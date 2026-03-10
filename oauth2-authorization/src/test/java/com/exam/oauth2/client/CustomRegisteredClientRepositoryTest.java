package com.exam.oauth2.client;

import com.exam.oauth2.exception.ApiException;
import com.exam.oauth2.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * CustomRegisteredClientRepository 단위 테스트
 *
 * ClientJpaRepository, ClientHelper를 Mock으로 대체해
 * 비즈니스 검증 로직만 테스트.
 *
 * epki-auth의 EpkiRegisteredClientRepository.validate() 로직 검증:
 *   1. 비활성 클라이언트 차단
 *   2. 만료된 시크릿 + 1일 grace period 이후 차단
 */
class CustomRegisteredClientRepositoryTest {

    private ClientJpaRepository clientJpaRepository;
    private ClientHelper clientHelper;
    private CustomRegisteredClientRepository repository;

    @BeforeEach
    void setUp() {
        clientJpaRepository = mock(ClientJpaRepository.class);
        clientHelper = mock(ClientHelper.class);
        repository = new CustomRegisteredClientRepository(clientJpaRepository, clientHelper);
    }

    @Nested
    @DisplayName("findByClientId() - 정상 케이스")
    class FindByClientIdSuccess {

        @Test
        @DisplayName("활성 클라이언트 조회 성공")
        void shouldReturnActiveClient() {
            // given
            ClientEntity entity = createActiveClientEntity("web-client");
            RegisteredClient registeredClient = createRegisteredClient("web-client");

            when(clientJpaRepository.findByClientId("web-client"))
                    .thenReturn(Optional.of(entity));
            when(clientHelper.toRegisteredClient(entity))
                    .thenReturn(registeredClient);

            // when
            RegisteredClient result = repository.findByClientId("web-client");

            // then
            assertThat(result).isNotNull();
            assertThat(result.getClientId()).isEqualTo("web-client");
        }

        @Test
        @DisplayName("시크릿 만료 전 클라이언트 조회 성공")
        void shouldReturnClientWithFutureExpiry() {
            // given - 만료일이 미래
            ClientEntity entity = createActiveClientEntity("client-with-expiry");
            entity.setClientSecretExpiresAt(Instant.now().plus(Duration.ofDays(30)));
            RegisteredClient registeredClient = createRegisteredClient("client-with-expiry");

            when(clientJpaRepository.findByClientId("client-with-expiry"))
                    .thenReturn(Optional.of(entity));
            when(clientHelper.toRegisteredClient(entity))
                    .thenReturn(registeredClient);

            // when
            RegisteredClient result = repository.findByClientId("client-with-expiry");

            // then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("1일 grace period 이내 만료 - 여전히 허용")
        void shouldAllowClientWithinGracePeriod() {
            // given - 23시간 전에 만료됐지만 grace period(1일) 이내
            ClientEntity entity = createActiveClientEntity("expiring-client");
            entity.setClientSecretExpiresAt(Instant.now().minus(Duration.ofHours(23)));
            RegisteredClient registeredClient = createRegisteredClient("expiring-client");

            when(clientJpaRepository.findByClientId("expiring-client"))
                    .thenReturn(Optional.of(entity));
            when(clientHelper.toRegisteredClient(entity))
                    .thenReturn(registeredClient);

            // when & then - grace period 이내이므로 예외 없음
            RegisteredClient result = repository.findByClientId("expiring-client");
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("findByClientId() - 검증 실패 케이스")
    class FindByClientIdFailure {

        @Test
        @DisplayName("존재하지 않는 클라이언트 - CLIENT_NOT_FOUND")
        void shouldThrowNotFoundForUnknownClient() {
            when(clientJpaRepository.findByClientId("unknown"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> repository.findByClientId("unknown"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> {
                        ApiException apiEx = (ApiException) e;
                        assertThat(apiEx.getErrorCode()).isEqualTo(ErrorCode.CLIENT_NOT_FOUND);
                    });
        }

        @Test
        @DisplayName("비활성 클라이언트 - CLIENT_INACTIVE")
        void shouldThrowInactiveForDisabledClient() {
            // given
            ClientEntity entity = createActiveClientEntity("inactive-client");
            entity.setActive(false); // 비활성화

            when(clientJpaRepository.findByClientId("inactive-client"))
                    .thenReturn(Optional.of(entity));

            // when & then
            assertThatThrownBy(() -> repository.findByClientId("inactive-client"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> {
                        assertThat(((ApiException) e).getErrorCode())
                                .isEqualTo(ErrorCode.CLIENT_INACTIVE);
                    });
        }

        @Test
        @DisplayName("Grace period 지난 만료 클라이언트 - CLIENT_SECRET_EXPIRED")
        void shouldThrowExpiredAfterGracePeriod() {
            // given - 25시간 전에 만료 (grace period 1일 초과)
            ClientEntity entity = createActiveClientEntity("expired-client");
            entity.setClientSecretExpiresAt(Instant.now().minus(Duration.ofHours(25)));

            when(clientJpaRepository.findByClientId("expired-client"))
                    .thenReturn(Optional.of(entity));

            // when & then
            assertThatThrownBy(() -> repository.findByClientId("expired-client"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> {
                        assertThat(((ApiException) e).getErrorCode())
                                .isEqualTo(ErrorCode.CLIENT_SECRET_EXPIRED);
                    });
        }

        @Test
        @DisplayName("정확히 grace period 경계(24시간+1초) 초과 - 만료 처리")
        void shouldExpireJustAfterGracePeriod() {
            ClientEntity entity = createActiveClientEntity("boundary-client");
            entity.setClientSecretExpiresAt(
                    Instant.now().minus(Duration.ofHours(24)).minusSeconds(1));

            when(clientJpaRepository.findByClientId("boundary-client"))
                    .thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> repository.findByClientId("boundary-client"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e ->
                            assertThat(((ApiException) e).getErrorCode())
                                    .isEqualTo(ErrorCode.CLIENT_SECRET_EXPIRED)
                    );
        }
    }

    @Test
    @DisplayName("save() - 엔티티 변환 후 저장")
    void shouldSaveRegisteredClient() {
        // given
        RegisteredClient client = createRegisteredClient("new-client");
        ClientEntity entity = createActiveClientEntity("new-client");

        when(clientHelper.toEntity(client)).thenReturn(entity);

        // when
        repository.save(client);

        // then
        verify(clientJpaRepository, times(1)).save(entity);
    }

    // ─── 헬퍼 메서드 ─────────────────────────────────────────────

    private ClientEntity createActiveClientEntity(String clientId) {
        ClientEntity entity = new ClientEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setClientId(clientId);
        entity.setActive(true);
        entity.setClientSecretExpiresAt(null); // 무기한
        return entity;
    }

    private RegisteredClient createRegisteredClient(String clientId) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret("{noop}secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("read")
                .clientSettings(ClientSettings.builder().build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(1))
                        .build())
                .build();
    }
}
