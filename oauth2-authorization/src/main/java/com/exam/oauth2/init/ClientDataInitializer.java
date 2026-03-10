package com.exam.oauth2.init;

import com.exam.oauth2.client.CustomRegisteredClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 테스트용 클라이언트 데이터 초기화
 *
 * 애플리케이션 시작 시 H2에 테스트 클라이언트 등록.
 * 실무에서는 DB에 사전 등록된 데이터를 사용 (이 클래스 불필요).
 *
 * 등록되는 클라이언트:
 *   1. web-client: Authorization Code + Refresh Token (사용자 인증)
 *   2. service-client: Client Credentials (서비스 간 통신)
 *   3. inactive-client: 비활성 클라이언트 (검증 테스트용)
 *
 * 테스트 시나리오:
 *   1. Authorization Code 흐름:
 *      → http://localhost:9090/oauth2/authorize?client_id=web-client&response_type=code&scope=openid&redirect_uri=http://localhost:8080/authorized
 *
 *   2. Client Credentials 흐름:
 *      → POST /oauth2/token -u service-client:service-secret -d "grant_type=client_credentials&scope=read"
 *
 *   3. 비활성 클라이언트 (에러 확인):
 *      → POST /oauth2/token -u inactive-client:inactive-secret ...
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientDataInitializer implements CommandLineRunner {

    private final CustomRegisteredClientRepository clientRepository;

    @Override
    public void run(String... args) {
        registerWebClient();
        registerServiceClient();
        registerInactiveClient();
        log.info("=== 클라이언트 초기화 완료 ===");
        log.info("  web-client     : Authorization Code + Refresh Token (사용자 인증용)");
        log.info("  service-client : Client Credentials (서비스 간 통신용)");
        log.info("  inactive-client: 비활성 클라이언트 (에러 테스트용)");
    }

    /**
     * Authorization Code 클라이언트
     * → 사용자가 직접 로그인하는 웹 애플리케이션
     */
    private void registerWebClient() {
        RegisteredClient webClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("web-client")
                .clientSecret("{noop}web-secret")   // 실무: {bcrypt}$2a$...
                .clientIdIssuedAt(Instant.now())
                .clientName("Web Application Client")
                // 클라이언트 인증 방법: Basic Auth 또는 POST body
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                // 지원 그랜트 타입
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                // 허용된 redirect URI (CSRF 방어 핵심 - 등록된 URI만 허용)
                .redirectUri("http://localhost:8080/login/oauth2/code/web-client")
                .redirectUri("http://localhost:8080/authorized")
                .redirectUri("http://127.0.0.1:8080/authorized")  // 로컬 테스트용
                // 지원 스코프
                .scope(OidcScopes.OPENID)    // OIDC 지원 (userinfo, id_token)
                .scope(OidcScopes.PROFILE)
                .scope("read")
                .scope("write")
                // 클라이언트 설정
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(true)  // 동의 화면 표시
                        .requireProofKey(false)              // PKCE 선택 (true 권장)
                        .build())
                // 토큰 설정
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(1))
                        .refreshTokenTimeToLive(Duration.ofDays(1))
                        .reuseRefreshTokens(false)  // 보안: 매번 새 Refresh Token 발급
                        .build())
                .build();

        clientRepository.save(webClient);
    }

    /**
     * Client Credentials 클라이언트
     * → 사용자 없이 서비스 간 통신에 사용
     */
    private void registerServiceClient() {
        RegisteredClient serviceClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("service-client")
                .clientSecret("{noop}service-secret")
                .clientIdIssuedAt(Instant.now())
                .clientName("Backend Service Client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("read")
                .scope("write")
                .scope("service")
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false) // 동의 화면 없음 (서비스 간)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(30))
                        .build())
                .build();

        clientRepository.save(serviceClient);
    }

    /**
     * 비활성 클라이언트 (CustomRegisteredClientRepository.validate() 테스트용)
     */
    private void registerInactiveClient() {
        RegisteredClient inactiveClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("inactive-client")
                .clientSecret("{noop}inactive-secret")
                .clientIdIssuedAt(Instant.now())
                .clientName("Inactive Client (테스트용)")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("read")
                .clientSettings(ClientSettings.builder().build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(30))
                        .build())
                .build();

        // 먼저 저장
        clientRepository.save(inactiveClient);

        // DB에서 직접 active = false 설정 (save()의 기본값은 true)
        // → validate()에서 CLIENT_INACTIVE 예외 발생 확인용
        // 실제 테스트는 ClientDataInitializerTest 참고
    }
}
