package com.exam.oauth2.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ClientEntity ↔ RegisteredClient 변환 헬퍼
 *
 * epki-auth의 ClientHelper 참고.
 *
 * 변환이 필요한 이유:
 *   Spring AS는 RegisteredClient(도메인 객체)를 사용하지만,
 *   JPA는 단순한 문자열/숫자 타입을 저장.
 *   → 복잡한 객체(ClientSettings, TokenSettings)를 JSON으로 직렬화해 저장.
 *
 * Jackson 모듈 등록이 필요한 이유:
 *   Spring Security/OAuth2 AS의 타입들이 기본 Jackson 설정으로
 *   직렬화되지 않아 전용 Jackson 모듈을 등록해야 함.
 *
 * 핵심 메서드:
 *   - toEntity(): RegisteredClient → ClientEntity (DB 저장용)
 *   - toRegisteredClient(): ClientEntity → RegisteredClient (Spring AS 사용용)
 */
@Slf4j
@Component
public class ClientHelper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ClientHelper() {
        // Spring Security Jackson 모듈 등록 (Authentication, GrantedAuthority 등)
        ClassLoader classLoader = ClientHelper.class.getClassLoader();
        List<Module> securityModules = SecurityJackson2Modules.getModules(classLoader);
        objectMapper.registerModules(securityModules);

        // Spring AS Jackson 모듈 등록 (ClientSettings, TokenSettings 등)
        objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
    }

    /**
     * RegisteredClient → ClientEntity 변환
     * save() 시 사용
     */
    public ClientEntity toEntity(RegisteredClient client) {
        ClientEntity entity = new ClientEntity();
        entity.setId(client.getId());
        entity.setClientId(client.getClientId());
        entity.setClientIdIssuedAt(client.getClientIdIssuedAt());
        entity.setClientSecret(client.getClientSecret());
        entity.setClientSecretExpiresAt(client.getClientSecretExpiresAt());
        entity.setClientName(client.getClientName() != null ? client.getClientName() : client.getClientId());

        // 컬렉션 → 콤마 구분 문자열
        entity.setClientAuthenticationMethods(
                toCommaDelimited(client.getClientAuthenticationMethods(), ClientAuthenticationMethod::getValue));
        entity.setAuthorizationGrantTypes(
                toCommaDelimited(client.getAuthorizationGrantTypes(), AuthorizationGrantType::getValue));
        entity.setRedirectUris(StringUtils.collectionToCommaDelimitedString(client.getRedirectUris()));
        entity.setPostLogoutRedirectUris(
                StringUtils.collectionToCommaDelimitedString(client.getPostLogoutRedirectUris()));
        entity.setScopes(StringUtils.collectionToCommaDelimitedString(client.getScopes()));

        // 복잡한 설정 객체 → JSON 문자열
        entity.setClientSettings(writeMap(client.getClientSettings().getSettings()));
        entity.setTokenSettings(writeMap(client.getTokenSettings().getSettings()));

        return entity;
    }

    /**
     * ClientEntity → RegisteredClient 변환
     * findById(), findByClientId() 반환 시 사용
     */
    public RegisteredClient toRegisteredClient(ClientEntity entity) {
        Map<String, Object> clientSettingsMap = parseMap(entity.getClientSettings());
        Map<String, Object> tokenSettingsMap = parseMap(entity.getTokenSettings());

        return RegisteredClient.withId(entity.getId())
                .clientId(entity.getClientId())
                .clientIdIssuedAt(entity.getClientIdIssuedAt())
                .clientSecret(entity.getClientSecret())
                .clientSecretExpiresAt(entity.getClientSecretExpiresAt())
                .clientName(entity.getClientName())
                // 콤마 구분 문자열 → Set<T>
                .clientAuthenticationMethods(addTypedElements(
                        entity.getClientAuthenticationMethods(), ClientAuthenticationMethod::new))
                .authorizationGrantTypes(addTypedElements(
                        entity.getAuthorizationGrantTypes(), AuthorizationGrantType::new))
                .redirectUris(addStrings(entity.getRedirectUris()))
                .postLogoutRedirectUris(addStrings(entity.getPostLogoutRedirectUris()))
                .scopes(addStrings(entity.getScopes()))
                // JSON → Settings 객체 복원
                .clientSettings(ClientSettings.withSettings(clientSettingsMap).build())
                .tokenSettings(TokenSettings.withSettings(tokenSettingsMap).build())
                .build();
    }

    // ─── 유틸 메서드 ───────────────────────────────────────

    private <T> String toCommaDelimited(Set<T> set, Function<T, String> mapper) {
        return set.stream().map(mapper).collect(Collectors.joining(","));
    }

    private <T> Consumer<Set<T>> addTypedElements(String values, Function<String, T> constructor) {
        return elements -> {
            Set<T> result = StringUtils.commaDelimitedListToSet(values)
                    .stream()
                    .map(constructor)
                    .collect(Collectors.toUnmodifiableSet());
            elements.addAll(result);
        };
    }

    private Consumer<Set<String>> addStrings(String values) {
        return s -> {
            if (StringUtils.hasText(values)) {
                s.addAll(StringUtils.commaDelimitedListToSet(values));
            }
        };
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("ClientHelper - JSON 파싱 실패: {}", e.getMessage());
            throw new IllegalArgumentException("JSON 파싱 실패: " + e.getMessage(), e);
        }
    }

    private String writeMap(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.error("ClientHelper - JSON 직렬화 실패: {}", e.getMessage());
            throw new IllegalArgumentException("JSON 직렬화 실패: " + e.getMessage(), e);
        }
    }
}
