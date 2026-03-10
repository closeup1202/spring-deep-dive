package com.exam.oauth2.authorization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * OAuth2Authorization JPA 엔티티
 *
 * epki-auth의 Authorization 엔티티(Redis 저장) 참고.
 * 학습 모듈에서는 H2 JPA로 대체.
 *
 * OAuth2Authorization(Spring AS 도메인 객체)을 DB에 저장하기 위한 플랫 구조.
 * 복잡한 중첩 객체(Token.metadata, attributes 등)는 JSON 문자열로 직렬화.
 *
 * 저장되는 토큰 종류:
 *   1. Authorization Code: 인가 코드 (Authorization Code Grant 흐름)
 *   2. Access Token: 리소스 접근 토큰
 *   3. Refresh Token: Access Token 갱신 토큰
 *   4. OIDC ID Token: 사용자 신원 토큰 (OIDC 흐름)
 *
 * 실무(epki-auth): Redis Hash로 저장
 *   @RedisHash("auth:oauth2:authorization")
 *   @TimeToLive: 설정 가능 TTL
 *   → Redis는 TTL이 내장되어 만료 토큰을 자동 삭제
 *   → H2(학습용)은 별도 배치 삭제가 필요
 */
@Entity
@Table(name = "oauth2_authorization")
@Getter
@Setter
public class AuthorizationEntity {

    @Id
    private String id;

    private String registeredClientId;
    private String principalName;
    private String authorizationGrantType;

    @Column(length = 4000)
    private String authorizedScopes;

    @Column(length = 4000)
    private String attributes;            // OAuth2Authorization.getAttributes() → JSON

    private String state;

    // ─── Authorization Code ───────────────────────────────

    @Column(length = 4000)
    private String authorizationCodeValue;
    private Instant authorizationCodeIssuedAt;
    private Instant authorizationCodeExpiresAt;

    @Column(length = 4000)
    private String authorizationCodeMetadata; // JSON (invalidated 등 메타데이터)

    // ─── Access Token ─────────────────────────────────────

    @Column(length = 4000)
    private String accessTokenValue;
    private Instant accessTokenIssuedAt;
    private Instant accessTokenExpiresAt;

    @Column(length = 4000)
    private String accessTokenMetadata;   // JSON

    @Column(length = 1000)
    private String accessTokenScopes;     // 콤마 구분

    // ─── Refresh Token ────────────────────────────────────

    @Column(length = 4000)
    private String refreshTokenValue;
    private Instant refreshTokenIssuedAt;
    private Instant refreshTokenExpiresAt;

    @Column(length = 4000)
    private String refreshTokenMetadata;  // JSON

    // ─── OIDC ID Token ────────────────────────────────────

    @Column(length = 4000)
    private String oidcIdTokenValue;
    private Instant oidcIdTokenIssuedAt;
    private Instant oidcIdTokenExpiresAt;

    @Column(length = 4000)
    private String oidcIdTokenMetadata;   // JSON

    @Column(length = 4000)
    private String oidcIdTokenClaims;     // JSON (ID Token의 클레임)
}
