package com.exam.oauth2.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/**
 * JWT 토큰 설정
 *
 * epki-auth의 TokenConfig 참고.
 * epki-auth는 RSA 키를 파일에서 로드 (TokenRSAKeyFactory.loadFromFile).
 * 학습 모듈에서는 시작 시마다 새 RSA 키 쌍 생성 (재시작 시 기존 토큰 무효화).
 *
 * JWK(JSON Web Key):
 *   JWT 서명 검증에 사용되는 공개키를 공개하는 표준.
 *   GET /oauth2/jwks 엔드포인트를 통해 리소스 서버가 공개키를 가져감.
 *
 * 실무 시 주의사항:
 *   - RSA 키를 매번 생성하면 재시작 시 기존 액세스 토큰이 검증 불가.
 *   - 운영환경에서는 파일/KMS/Vault에서 고정 키를 로드해야 함.
 *   - 키 교체(rotation) 시 grace period 동안 이전 키도 JWK에 포함해야 함.
 *
 * 관련 엔드포인트:
 *   - GET /oauth2/jwks           : JWK Set 조회
 *   - GET /.well-known/openid-configuration : OIDC Discovery 문서
 */
@Slf4j
@Configuration
public class TokenConfig {

    @Value("${oauth2.issuer-uri}")
    private String issuerUri;

    /**
     * JWK Source 빈
     *
     * JWT 서명/검증에 사용할 RSA 키 쌍을 JWKSource로 래핑.
     * OAuth2AuthorizationServerConfigurer가 이 빈을 자동 감지.
     *
     * 실무(epki-auth): TokenRSAKeyFactory.loadFromFile(jwkFilePath)
     * 학습 모듈: 시작 시 새 RSA-2048 키 생성
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        RSAKey rsaKey = generateRsaKey();
        log.info("RSA JWK 생성 완료 (keyId: {})", rsaKey.getKeyID());
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    private RSAKey generateRsaKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048); // 최소 2048bit (실무: 4096bit 권장)
            KeyPair keyPair = generator.generateKeyPair();

            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID(UUID.randomUUID().toString()) // 키 식별자
                    .build();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA 알고리즘을 찾을 수 없음", e);
        }
    }

    /**
     * JWT Decoder 빈
     *
     * 리소스 서버(oauth2ResourceServer.jwt())가 사용하는 JWT 검증기.
     * JWKSource를 기반으로 서명 검증.
     */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /**
     * Authorization Server 기본 설정
     *
     * 엔드포인트 경로, issuer URI 등 설정.
     * 기본값으로도 충분하지만 issuer를 명시적으로 설정.
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer(issuerUri) // JWT의 iss 클레임, OIDC Discovery의 issuer
                .build();
    }
}
