package com.exam.oauth2.auth;

import com.exam.oauth2.exception.ErrorCode;
import com.exam.oauth2.user.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/**
 * 커스텀 인증 프로바이더
 *
 * epki-auth의 EpkiAuthenticationProvider 참고.
 *
 * AuthenticationProvider 역할:
 *   - AuthenticationManager(ProviderManager)가 인증 처리를 위임하는 객체
 *   - supports()로 처리 가능한 토큰 타입을 선언
 *   - authenticate()에서 실제 검증 로직 수행
 *
 * 처리 흐름:
 *   CustomLoginFilter
 *     → unauthenticated CustomAuthenticationToken (authCode 포함)
 *     → ProviderManager
 *     → CustomAuthenticationProvider.authenticate()
 *       → AuthCodeStore에서 authCode로 AuthInfo 조회
 *       → CustomUserPrincipal 생성
 *       → authenticated CustomAuthenticationToken 반환
 *     → SecurityContext에 저장
 *
 * 실무(epki-auth):
 *   EpkiAuthenticationProvider는 AuthCodeValidator(Redis)에서 이미
 *   검증된 AuthInfo를 받아 EpkiPrincipal만 생성하면 됨.
 *   실제 검증은 Filter 레벨에서 AuthCodeValidator가 담당.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthenticationProvider implements AuthenticationProvider {

    private final AuthCodeStore authCodeStore;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        CustomAuthenticationToken token = (CustomAuthenticationToken) authentication;
        String authCode = (String) token.getCredentials();

        log.debug("Authenticating with authCode: {}", authCode);

        // AuthCodeStore (= Redis)에서 인증 코드 조회 + 삭제
        AuthInfo authInfo = authCodeStore.consumeAndGet(authCode)
                .orElseThrow(() -> {
                    log.warn("Authentication failed - invalid authCode: {}", authCode);
                    return new BadCredentialsException(ErrorCode.INVALID_AUTH_CODE.getMessage());
                });

        // 인증 완료 - 사용자 주체 생성
        CustomUserPrincipal principal = CustomUserPrincipal.of(
                authInfo.userId(),
                authInfo.username(),
                authInfo.role()
        );

        log.info("Authentication success - user: {}, role: {}", principal.username(), principal.role());

        return CustomAuthenticationToken.authenticated(principal);
    }

    /**
     * 이 프로바이더가 처리할 수 있는 토큰 타입 선언
     * ProviderManager가 적절한 프로바이더를 찾을 때 사용
     */
    @Override
    public boolean supports(Class<?> authentication) {
        return CustomAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
