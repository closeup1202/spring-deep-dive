package com.exam.oauth2.user;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.function.Function;

/**
 * OIDC UserInfo 엔드포인트 매핑 설정
 *
 * epki-auth의 UserMapperConfig 참고.
 *
 * GET /userinfo 요청 처리 흐름:
 * 1. 클라이언트가 Bearer {access_token}으로 /userinfo 요청
 * 2. Spring AS가 JWT를 검증 → JwtAuthenticationToken 생성
 * 3. OidcUserInfoAuthenticationContext를 userInfoMapper에 전달
 * 4. userInfoMapper가 JWT 클레임에서 OidcUserInfo 구성 후 반환
 * 5. JSON 응답
 *
 * 핵심 포인트:
 *   JWT access_token에 담긴 클레임을 그대로 UserInfo로 반환.
 *   따라서 CustomTokenCustomizer에서 access_token에 클레임을 잘 넣어야 함.
 *
 * 실무 패턴:
 *   DB 조회 없이 JWT 클레임만으로 UserInfo 구성 → 성능 최적화.
 *   민감한 정보(주민번호 등)는 UserInfo에 포함하지 않도록 주의.
 */
@Configuration
public class UserInfoMapperConfig {

    /**
     * userInfoMapper Bean
     *
     * SecurityConfig에서 주입받아 OIDC 설정에 등록:
     * http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
     *     .oidc(oidc -> oidc.userInfoEndpoint(ui -> ui.userInfoMapper(userInfoMapper)))
     */
    @Bean
    public Function<OidcUserInfoAuthenticationContext, OidcUserInfo> userInfoMapper() {
        return context -> {
            // UserInfo 요청의 인증 정보 (Bearer access_token)
            OidcUserInfoAuthenticationToken authentication = context.getAuthentication();

            // access_token이 JWT이므로 JwtAuthenticationToken으로 캐스팅
            JwtAuthenticationToken principal = (JwtAuthenticationToken) authentication.getPrincipal();

            // JWT 클레임을 그대로 OidcUserInfo로 반환
            // CustomTokenCustomizer에서 넣은 user_id, username, role 등이 포함됨
            return new OidcUserInfo(principal.getToken().getClaims());
        };
    }
}
