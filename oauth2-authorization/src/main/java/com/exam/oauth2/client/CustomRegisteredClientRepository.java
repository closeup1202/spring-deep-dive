package com.exam.oauth2.client;

import com.exam.oauth2.exception.ApiException;
import com.exam.oauth2.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 커스텀 RegisteredClientRepository 구현
 *
 * epki-auth의 EpkiRegisteredClientRepository 참고.
 *
 * Spring AS 기본 구현체:
 *   - InMemoryRegisteredClientRepository: 메모리 저장 (테스트용)
 *   - JdbcRegisteredClientRepository: JDBC 저장 (운영용)
 *
 * 커스텀 구현 이유:
 *   비즈니스 검증 로직(클라이언트 활성 여부, 시크릿 만료 등)을
 *   DB 조회 후 즉시 적용하기 위함.
 *   → 표준 JDBC 구현체에 비즈니스 검증을 추가하는 데코레이터 패턴.
 *
 * epki-auth의 검증 항목:
 *   1. apiKeyStatusCode == "01"  → 이 모듈: active == true
 *   2. testKeyYn != "Y" (프로덕션) → 이 모듈: 생략 (학습 단순화)
 *   3. clientSecretExpiresAt + 1일 > now() → 동일하게 구현
 *
 * Spring AS 사용 시점:
 *   - findByClientId(): 토큰 요청 시 클라이언트 검증
 *   - findById(): 토큰 인트로스펙션, 세션 복원 등
 *   - save(): 클라이언트 등록/수정
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class CustomRegisteredClientRepository implements RegisteredClientRepository {

    private final ClientJpaRepository clientJpaRepository;
    private final ClientHelper clientHelper;

    @Override
    public void save(RegisteredClient registeredClient) {
        Assert.notNull(registeredClient, "registeredClient cannot be null");
        ClientEntity entity = clientHelper.toEntity(registeredClient);
        clientJpaRepository.save(entity);
        log.info("Client saved: {}", entity.getClientId());
    }

    @Override
    public RegisteredClient findById(String id) {
        Assert.hasText(id, "id cannot be empty");
        return clientJpaRepository.findById(id)
                .map(entity -> {
                    validate(entity);
                    return clientHelper.toRegisteredClient(entity);
                })
                .orElse(null);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        Assert.hasText(clientId, "clientId cannot be empty");

        ClientEntity entity = clientJpaRepository.findByClientId(clientId)
                .orElseThrow(() -> {
                    log.warn("Client not found: {}", clientId);
                    return new ApiException(ErrorCode.CLIENT_NOT_FOUND, clientId);
                });

        // 비즈니스 검증 수행
        validate(entity);

        return clientHelper.toRegisteredClient(entity);
    }

    /**
     * 클라이언트 유효성 검증
     *
     * epki-auth의 EpkiRegisteredClientRepository.validate() 참고.
     *
     * 순서:
     *   1. 활성 상태 확인 (status check)
     *   2. 시크릿 만료 확인 (expiry check with grace period)
     */
    private void validate(ClientEntity client) {
        // 1. 비활성 클라이언트 차단
        if (!client.isActive()) {
            log.warn("Inactive client blocked: {}", client.getClientId());
            throw new ApiException(ErrorCode.CLIENT_INACTIVE, client.getClientId());
        }

        // 2. 시크릿 만료 검증 (1일 grace period - epki-auth와 동일)
        // grace period: 만료 당일 갑작스러운 서비스 중단 방지
        if (client.getClientSecretExpiresAt() != null) {
            Instant expiredWithGrace = client.getClientSecretExpiresAt()
                    .plus(1, ChronoUnit.DAYS); // grace period 1일
            if (Instant.now().isAfter(expiredWithGrace)) {
                log.warn("Client secret expired (with 1d grace): {}", client.getClientId());
                throw new ApiException(ErrorCode.CLIENT_SECRET_EXPIRED, client.getClientId());
            }
        }

        log.debug("Client validated: {}", client.getClientId());
    }
}
