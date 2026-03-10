package com.exam.oauth2.authorization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.exam.oauth2.auth.CustomAuthenticationToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 커스텀 OAuth2AuthorizationService 구현 (JPA 기반)
 *
 * epki-auth의 EpkiOAuth2AuthorizationService 참고.
 * epki-auth는 Redis 기반, 이 모듈은 H2 JPA 기반.
 *
 * OAuth2AuthorizationService 역할:
 *   OAuth2 인가 흐름에서 생성된 Authorization 객체를 영속화.
 *   인가 코드 → 액세스 토큰 교환 등 각 단계에서 저장/조회.
 *
 * 처리 흐름 (Authorization Code Grant):
 *   1. /oauth2/authorize 성공 → save(authorization with authCode)
 *   2. /oauth2/token (code exchange) → findByToken(code, CODE)
 *   3. 토큰 발급 완료 → save(authorization with accessToken, refreshToken)
 *   4. 리소스 접근 → findByToken(accessToken, ACCESS_TOKEN)
 *   5. 토큰 갱신 → findByToken(refreshToken, REFRESH_TOKEN)
 *   6. 토큰 폐기 → remove(authorization)
 *
 * Jackson 모듈 등록 이유:
 *   OAuth2Authorization의 attributes 필드에는 복잡한 Spring Security 객체들이
 *   Map<String, Object>으로 저장됨. 이를 JSON으로 직렬화하려면
 *   Spring Security / Spring AS 전용 Jackson 모듈 등록 필요.
 *
 * 핵심 패턴 - CustomAuthenticationToken Mixin:
 *   epki-auth에서 EpkiAuthenticationToken이 attributes에 저장되는데,
 *   기본 Jackson 설정으로 역직렬화 불가 → Mixin 등록으로 해결.
 *   이 모듈도 동일하게 CustomAuthenticationToken Mixin을 등록.
 */
@Slf4j
@Component
public class CustomOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private final AuthorizationJpaRepository authorizationRepository;
    private final RegisteredClientRepository registeredClientRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CustomOAuth2AuthorizationService(
            AuthorizationJpaRepository authorizationRepository,
            RegisteredClientRepository registeredClientRepository) {
        this.authorizationRepository = authorizationRepository;
        this.registeredClientRepository = registeredClientRepository;

        // Spring Security 타입 직렬화 모듈
        ClassLoader classLoader = CustomOAuth2AuthorizationService.class.getClassLoader();
        List<Module> securityModules = SecurityJackson2Modules.getModules(classLoader);
        objectMapper.registerModules(securityModules);

        // Spring AS 타입 직렬화 모듈
        objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());

        // CustomAuthenticationToken 직렬화 지원
        // epki-auth: objectMapper.addMixIn(EpkiAuthenticationToken.class, EpkiAuthenticationTokenMixin.class)
        // 학습 모듈: addMixIn 대신 기본 직렬화 허용 (실습 단순화)
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL
        );
    }

    // ─── OAuth2AuthorizationService 인터페이스 구현 ──────────

    @Override
    public void save(OAuth2Authorization authorization) {
        Assert.notNull(authorization, "authorization cannot be null");
        AuthorizationEntity entity = toEntity(authorization);
        authorizationRepository.save(entity);
        log.debug("Authorization saved: id={}, grantType={}, principal={}",
                entity.getId(), entity.getAuthorizationGrantType(), entity.getPrincipalName());
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        Assert.notNull(authorization, "authorization cannot be null");
        authorizationRepository.deleteById(authorization.getId());
        log.debug("Authorization removed: id={}", authorization.getId());
    }

    @Override
    public OAuth2Authorization findById(String id) {
        Assert.hasText(id, "id cannot be empty");
        return authorizationRepository.findById(id)
                .map(this::toObject)
                .orElse(null);
    }

    /**
     * 토큰 값과 타입으로 Authorization 조회
     *
     * tokenType이 null이면 accessToken으로 조회 (리소스 서버 인트로스펙션).
     * 각 토큰 타입에 맞는 컬럼을 조회.
     */
    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        Assert.hasText(token, "token cannot be empty");

        Optional<AuthorizationEntity> result;

        if (tokenType == null) {
            result = authorizationRepository.findByAccessTokenValue(token);
        } else if (OAuth2ParameterNames.STATE.equals(tokenType.getValue())) {
            result = authorizationRepository.findByState(token);
        } else if (OAuth2ParameterNames.CODE.equals(tokenType.getValue())) {
            result = authorizationRepository.findByAuthorizationCodeValue(token);
        } else if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)) {
            result = authorizationRepository.findByAccessTokenValue(token);
        } else if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
            result = authorizationRepository.findByRefreshTokenValue(token);
        } else if (OidcParameterNames.ID_TOKEN.equals(tokenType.getValue())) {
            result = authorizationRepository.findByOidcIdTokenValue(token);
        } else {
            result = Optional.empty();
        }

        return result.map(this::toObject).orElse(null);
    }

    // ─── 변환 메서드 ─────────────────────────────────────────

    /**
     * AuthorizationEntity → OAuth2Authorization 변환
     */
    private OAuth2Authorization toObject(AuthorizationEntity entity) {
        // RegisteredClient 조회 (Authorization에 연결)
        RegisteredClient registeredClient =
                registeredClientRepository.findById(entity.getRegisteredClientId());
        if (registeredClient == null) {
            throw new DataRetrievalFailureException(
                    "RegisteredClient를 찾을 수 없음: id=" + entity.getRegisteredClientId());
        }

        OAuth2Authorization.Builder builder = OAuth2Authorization
                .withRegisteredClient(registeredClient)
                .id(entity.getId())
                .principalName(entity.getPrincipalName())
                .authorizationGrantType(resolveGrantType(entity.getAuthorizationGrantType()))
                .authorizedScopes(StringUtils.commaDelimitedListToSet(entity.getAuthorizedScopes()))
                .attributes(attrs -> attrs.putAll(parseMap(entity.getAttributes())));

        if (entity.getState() != null) {
            builder.attribute(OAuth2ParameterNames.STATE, entity.getState());
        }

        // 인가 코드 복원
        if (entity.getAuthorizationCodeValue() != null) {
            builder.token(
                    new OAuth2AuthorizationCode(
                            entity.getAuthorizationCodeValue(),
                            entity.getAuthorizationCodeIssuedAt(),
                            entity.getAuthorizationCodeExpiresAt()),
                    meta -> meta.putAll(parseMap(entity.getAuthorizationCodeMetadata()))
            );
        }

        // Access Token 복원
        if (entity.getAccessTokenValue() != null) {
            builder.token(
                    new OAuth2AccessToken(
                            OAuth2AccessToken.TokenType.BEARER,
                            entity.getAccessTokenValue(),
                            entity.getAccessTokenIssuedAt(),
                            entity.getAccessTokenExpiresAt(),
                            StringUtils.commaDelimitedListToSet(entity.getAccessTokenScopes())),
                    meta -> meta.putAll(parseMap(entity.getAccessTokenMetadata()))
            );
        }

        // Refresh Token 복원
        if (entity.getRefreshTokenValue() != null) {
            builder.token(
                    new OAuth2RefreshToken(
                            entity.getRefreshTokenValue(),
                            entity.getRefreshTokenIssuedAt(),
                            entity.getRefreshTokenExpiresAt()),
                    meta -> meta.putAll(parseMap(entity.getRefreshTokenMetadata()))
            );
        }

        // OIDC ID Token 복원
        if (entity.getOidcIdTokenValue() != null) {
            builder.token(
                    new OidcIdToken(
                            entity.getOidcIdTokenValue(),
                            entity.getOidcIdTokenIssuedAt(),
                            entity.getOidcIdTokenExpiresAt(),
                            parseMap(entity.getOidcIdTokenClaims())),
                    meta -> meta.putAll(parseMap(entity.getOidcIdTokenMetadata()))
            );
        }

        return builder.build();
    }

    /**
     * OAuth2Authorization → AuthorizationEntity 변환
     */
    private AuthorizationEntity toEntity(OAuth2Authorization authorization) {
        AuthorizationEntity entity = new AuthorizationEntity();
        entity.setId(authorization.getId());
        entity.setRegisteredClientId(authorization.getRegisteredClientId());
        entity.setPrincipalName(authorization.getPrincipalName());
        entity.setAuthorizationGrantType(authorization.getAuthorizationGrantType().getValue());
        entity.setAuthorizedScopes(
                StringUtils.collectionToCommaDelimitedString(authorization.getAuthorizedScopes()));
        entity.setAttributes(writeMap(authorization.getAttributes()));
        entity.setState(authorization.getAttribute(OAuth2ParameterNames.STATE));

        // 각 토큰 저장 (Consumer 패턴으로 공통화 - epki-auth와 동일)
        setTokenValues(
                authorization.getToken(OAuth2AuthorizationCode.class),
                entity::setAuthorizationCodeValue,
                entity::setAuthorizationCodeIssuedAt,
                entity::setAuthorizationCodeExpiresAt,
                entity::setAuthorizationCodeMetadata
        );

        var accessToken = authorization.getToken(OAuth2AccessToken.class);
        setTokenValues(
                accessToken,
                entity::setAccessTokenValue,
                entity::setAccessTokenIssuedAt,
                entity::setAccessTokenExpiresAt,
                entity::setAccessTokenMetadata
        );
        if (accessToken != null && accessToken.getToken().getScopes() != null) {
            entity.setAccessTokenScopes(
                    StringUtils.collectionToCommaDelimitedString(accessToken.getToken().getScopes()));
        }

        setTokenValues(
                authorization.getToken(OAuth2RefreshToken.class),
                entity::setRefreshTokenValue,
                entity::setRefreshTokenIssuedAt,
                entity::setRefreshTokenExpiresAt,
                entity::setRefreshTokenMetadata
        );

        var oidcIdToken = authorization.getToken(OidcIdToken.class);
        setTokenValues(
                oidcIdToken,
                entity::setOidcIdTokenValue,
                entity::setOidcIdTokenIssuedAt,
                entity::setOidcIdTokenExpiresAt,
                entity::setOidcIdTokenMetadata
        );
        if (oidcIdToken != null) {
            entity.setOidcIdTokenClaims(writeMap(oidcIdToken.getClaims()));
        }

        return entity;
    }

    /**
     * 토큰 값들을 엔티티 Setter에 일괄 적용 (Consumer 패턴)
     * epki-auth의 setTokenValues() 동일 패턴
     */
    private <T extends OAuth2Token> void setTokenValues(
            OAuth2Authorization.Token<T> token,
            Consumer<String> valueConsumer,
            Consumer<Instant> issuedAtConsumer,
            Consumer<Instant> expiresAtConsumer,
            Consumer<String> metadataConsumer) {

        if (token == null) return;

        OAuth2Token oAuth2Token = token.getToken();
        valueConsumer.accept(oAuth2Token.getTokenValue());
        issuedAtConsumer.accept(oAuth2Token.getIssuedAt());
        expiresAtConsumer.accept(oAuth2Token.getExpiresAt());
        metadataConsumer.accept(writeMap(token.getMetadata()));
    }

    // ─── 유틸 메서드 ─────────────────────────────────────────

    private Map<String, Object> parseMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("Authorization JSON 파싱 실패: {}", e.getMessage());
            throw new IllegalArgumentException("JSON 파싱 실패", e);
        }
    }

    private String writeMap(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.error("Authorization JSON 직렬화 실패: {}", e.getMessage());
            throw new IllegalArgumentException("JSON 직렬화 실패", e);
        }
    }

    private static AuthorizationGrantType resolveGrantType(String value) {
        return switch (value) {
            case "authorization_code" -> AuthorizationGrantType.AUTHORIZATION_CODE;
            case "client_credentials" -> AuthorizationGrantType.CLIENT_CREDENTIALS;
            case "refresh_token" -> AuthorizationGrantType.REFRESH_TOKEN;
            default -> new AuthorizationGrantType(value);
        };
    }
}
