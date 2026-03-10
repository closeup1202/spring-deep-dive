package com.exam.oauth2.authorization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * OAuth2Authorization JPA 레포지토리
 *
 * epki-auth의 AuthorizationRepository(Redis CrudRepository) 참고.
 * 학습 모듈에서는 JPA로 대체.
 *
 * findByToken() 패턴:
 *   OAuth2AuthorizationService.findByToken()이 토큰 타입에 따라
 *   적절한 컬럼을 조회하도록 각 토큰 타입별 메서드 분리.
 *
 * 실무(epki-auth Redis 레포지토리):
 *   @Indexed 어노테이션으로 각 토큰 값에 인덱스 생성 →
 *   findByState(), findByAuthorizationCodeValue() 등을 지원.
 *   Redis는 보조 인덱스를 Set으로 관리.
 *
 * JPA 동일 효과:
 *   Spring Data JPA가 메서드명으로 WHERE 절을 자동 생성.
 */
@Repository
public interface AuthorizationJpaRepository extends JpaRepository<AuthorizationEntity, String> {

    // state 파라미터로 조회 (Authorization Code 흐름의 CSRF 상태값)
    Optional<AuthorizationEntity> findByState(String state);

    // 인가 코드로 조회 (클라이언트가 토큰 교환 시 사용)
    Optional<AuthorizationEntity> findByAuthorizationCodeValue(String authorizationCode);

    // Access Token으로 조회 (토큰 인트로스펙션, 리소스 서버 검증)
    Optional<AuthorizationEntity> findByAccessTokenValue(String accessToken);

    // Refresh Token으로 조회 (토큰 갱신 시)
    Optional<AuthorizationEntity> findByRefreshTokenValue(String refreshToken);

    // OIDC ID Token으로 조회
    Optional<AuthorizationEntity> findByOidcIdTokenValue(String idToken);
}
