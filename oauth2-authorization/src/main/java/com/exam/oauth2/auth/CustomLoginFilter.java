package com.exam.oauth2.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.util.StringUtils;

/**
 * 커스텀 로그인 필터
 *
 * epki-auth의 EpkiAuthenticationFilter 참고.
 *
 * AbstractAuthenticationProcessingFilter를 상속해
 * /oauth2/login (POST) 요청을 처리.
 *
 * 처리 흐름:
 *   1. POST /oauth2/login?authCode={code} 요청 수신
 *   2. validateRequest() - HTTP 메서드, Referer 헤더 검증
 *   3. authCode 파라미터 추출
 *   4. unauthenticated CustomAuthenticationToken 생성
 *   5. AuthenticationManager.authenticate() 호출 (→ CustomAuthenticationProvider)
 *   6. 인증 성공 → SavedRequestAwareAuthenticationSuccessHandler
 *      → 원래 요청(/oauth2/authorize)으로 redirect
 *   7. 인증 실패 → SimpleUrlAuthenticationFailureHandler
 *      → /login?error 로 redirect
 *
 * AbstractAuthenticationProcessingFilter가 제공하는 것:
 *   - successfulAuthentication(): SecurityContext 저장 + successHandler 호출
 *   - unsuccessfulAuthentication(): SecurityContext 초기화 + failureHandler 호출
 *   - requiresAuthentication(): URL 매처로 처리할 요청 판단
 *
 * Referer 검증의 목적:
 *   CSRF 방어의 일환. 허용된 도메인에서만 인증 요청을 받음.
 *   (epki-auth는 인증서 인증이라 CSRF 토큰 대신 Referer로 검증)
 */
@Slf4j
public class CustomLoginFilter extends AbstractAuthenticationProcessingFilter {

    private static final String AUTH_CODE_PARAMETER = "authCode";
    private final String allowedRefererPrefix;

    public CustomLoginFilter(AntPathRequestMatcher matcher,
                             AuthenticationManager authManager,
                             String allowedRefererPrefix) {
        super(matcher);
        setAuthenticationManager(authManager);
        this.allowedRefererPrefix = allowedRefererPrefix;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request,
                                                HttpServletResponse response)
            throws AuthenticationException {

        // Step 1: HTTP 메서드 및 Referer 검증
        validateRequest(request);

        // Step 2: authCode 파라미터 추출
        String authCode = request.getParameter(AUTH_CODE_PARAMETER);
        if (!StringUtils.hasText(authCode)) {
            log.warn("Missing authCode parameter");
            throw new AuthenticationServiceException(
                    "필수 파라미터가 없습니다: " + AUTH_CODE_PARAMETER
            );
        }

        log.debug("Attempting authentication with authCode: {}", authCode);

        // Step 3: 미인증 토큰 생성 → AuthenticationManager에 위임
        // AuthenticationManager(ProviderManager)가 CustomAuthenticationProvider를 찾아 처리
        return getAuthenticationManager().authenticate(
                CustomAuthenticationToken.unauthenticated(authCode)
        );
    }

    /**
     * 요청 유효성 검증
     *
     * epki-auth의 EpkiAuthenticationFilter.validateRequest() 참고.
     */
    private void validateRequest(HttpServletRequest request) {
        // 1. POST 메서드만 허용
        if (!HttpMethod.POST.name().equals(request.getMethod())) {
            throw new AuthenticationServiceException(
                    "지원하지 않는 HTTP 메서드: " + request.getMethod()
            );
        }

        // 2. Referer 헤더 검증 (허용된 도메인에서만 인증 요청 허용)
        if (StringUtils.hasText(allowedRefererPrefix)) {
            String referer = request.getHeader("Referer");

            if (!StringUtils.hasText(referer)) {
                log.warn("Missing Referer header");
                throw new BadCredentialsException("Referer 헤더가 없습니다.");
            }

            if (!referer.startsWith(allowedRefererPrefix)) {
                log.warn("Invalid Referer: {}", referer);
                throw new BadCredentialsException("허용되지 않은 Referer: " + referer);
            }
        }
    }
}
