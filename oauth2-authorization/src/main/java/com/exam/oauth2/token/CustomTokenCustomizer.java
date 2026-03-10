package com.exam.oauth2.token;

import com.exam.oauth2.auth.CustomAuthenticationToken;
import com.exam.oauth2.user.CustomUserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

/**
 * JWT Access Token 커스터마이저
 *
 * epki-auth의 JwtTokenCustomizerService 참고.
 *
 * 역할:
 *   Spring AS가 JWT를 생성하기 직전에 호출되어 클레임을 추가/수정.
 *   OAuth2TokenCustomizer<JwtEncodingContext>를 구현 → Spring AS가 자동 감지.
 *
 * 처리 흐름:
 *   TokenEndpointFilter
 *     → OAuth2AccessTokenGenerator
 *       → CustomTokenCustomizer.customize(context) ← 여기서 클레임 추가
 *         → JwtEncoder → JWT 생성
 *
 * JwtEncodingContext에서 접근 가능한 정보:
 *   - context.getTokenType(): ACCESS_TOKEN, REFRESH_TOKEN, ID_TOKEN
 *   - context.getPrincipal(): 인증된 사용자 Authentication
 *   - context.getAuthorizationGrantType(): 그랜트 타입
 *   - context.getAuthorizedScopes(): 인가된 스코프
 *   - context.getRegisteredClient(): 클라이언트 정보
 *   - context.getClaims(): 현재까지 쌓인 클레임 빌더
 *
 * 그랜트 타입별 principal 타입:
 *   - Authorization Code: CustomAuthenticationToken (사용자 로그인)
 *   - Client Credentials: OAuth2ClientAuthenticationToken (클라이언트 자체)
 *   - Refresh Token: 원래 인증 토큰 래핑
 *
 * 기본 클레임 (Spring AS가 자동 추가):
 *   iss, sub, aud, exp, iat, jti, scope
 *
 * 커스텀 클레임 (이 클래스에서 추가):
 *   user_id, username, role (Authorization Code 흐름에서만)
 */
@Slf4j
@Component
public class CustomTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    @Override
    public void customize(JwtEncodingContext context) {
        // Access Token에만 커스텀 클레임 추가
        // ID Token에는 UserInfoMapper가 별도로 처리
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            return;
        }

        Authentication principal = context.getPrincipal();
        log.debug("Customizing token for principal type: {}", principal.getClass().getSimpleName());

        // Authorization Code Grant: 사용자 정보 클레임 추가
        if (principal instanceof CustomAuthenticationToken customToken
                && customToken.getPrincipal() instanceof CustomUserPrincipal user) {

            // epki-auth: name, cn, instCode 추가
            // 학습 모듈: user_id, username, role 추가
            context.getClaims().claims(claims -> {
                claims.put("user_id", user.userId());
                claims.put("username", user.username());
                claims.put("role", user.role());
            });

            log.debug("Custom claims added for user: {}, role: {}", user.username(), user.role());
        }

        // Client Credentials Grant: 사용자 정보 없음 (서비스 간 통신)
        // 이 경우 principal은 OAuth2ClientAuthenticationToken
        // → 클라이언트 ID 정도만 추가하거나 아무것도 추가 안 해도 됨
    }
}
