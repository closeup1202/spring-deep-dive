package com.exam.oauth2.client;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * OAuth2 클라이언트 JPA 엔티티
 *
 * epki-auth의 Client 엔티티 참고.
 *
 * RegisteredClient(Spring AS 도메인 객체) ↔ ClientEntity(DB 테이블) 변환은
 * ClientHelper가 담당.
 *
 * 실무(epki-auth) 추가 필드:
 *   - apiKeyStatusCode: "01"이어야 유효한 클라이언트 (NotAllowedStatusException)
 *   - testKeyYn: "Y"이면 프로덕션에서 차단 (NotAllowedKeyException)
 *   - clientSecretExpiresAt: 만료 + 1일 grace period 후 차단 (KeyExpiredException)
 *
 * 학습 모듈 단순화:
 *   - active: true이어야 유효 (apiKeyStatusCode 대체)
 *   - clientSecretExpiresAt: null이면 무기한, 설정 시 만료 검증
 */
@Entity
@Table(name = "oauth2_client")
@Getter
@Setter
@ToString
public class ClientEntity {

    @Id
    private String id;                    // UUID (RegisteredClient.getId())

    private String clientId;              // 클라이언트 식별자 (web-client, service-client)

    @Column(name = "client_id_issued_at")
    private Instant clientIdIssuedAt;

    @Column(name = "client_secret", length = 300)
    private String clientSecret;          // BCrypt 인코딩된 시크릿

    @Column(name = "client_secret_expires_at")
    private Instant clientSecretExpiresAt; // null이면 무기한

    @Column(name = "client_name")
    private String clientName;

    @Column(name = "client_authentication_methods", length = 1000)
    private String clientAuthenticationMethods; // 콤마 구분: "client_secret_basic,client_secret_post"

    @Column(name = "authorization_grant_types", length = 1000)
    private String authorizationGrantTypes; // 콤마 구분: "authorization_code,refresh_token"

    @Column(name = "redirect_uris", length = 1000)
    private String redirectUris;           // 콤마 구분

    @Column(name = "post_logout_redirect_uris", length = 1000)
    private String postLogoutRedirectUris; // 콤마 구분

    @Column(name = "scopes", length = 1000)
    private String scopes;                 // 콤마 구분: "openid,profile,read"

    @Column(name = "client_settings", length = 2000)
    private String clientSettings;         // JSON (requireAuthorizationConsent 등)

    @Column(name = "token_settings", length = 2000)
    private String tokenSettings;          // JSON (accessTokenTimeToLive 등)

    // 비즈니스 검증 필드
    private boolean active = true;         // 클라이언트 활성화 여부 (epki-auth: apiKeyStatusCode="01")
}
