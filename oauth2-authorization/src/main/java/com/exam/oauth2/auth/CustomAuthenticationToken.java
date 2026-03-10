package com.exam.oauth2.auth;

import com.exam.oauth2.user.CustomUserPrincipal;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

/**
 * 커스텀 인증 토큰
 *
 * epki-auth의 EpkiAuthenticationToken 참고.
 *
 * Spring Security Authentication 인터페이스의 구현체.
 * 두 가지 상태를 가진다:
 *
 * [미인증 상태 - CustomLoginFilter에서 생성]
 *   - authCode: 외부 시스템에서 받은 일회성 코드
 *   - principal: null
 *   - authenticated: false
 *
 * [인증 완료 상태 - CustomAuthenticationProvider에서 생성]
 *   - authCode: null (사용 완료)
 *   - principal: CustomUserPrincipal (사용자 정보)
 *   - authenticated: true
 *
 * Spring Security 처리 흐름:
 *   Filter → (unauthenticated token) → AuthenticationManager
 *   → Provider → (authenticated token) → SecurityContext
 */
public class CustomAuthenticationToken extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String authCode;          // 미인증 시 사용
    private final CustomUserPrincipal principal; // 인증 완료 후 사용

    /**
     * 미인증 토큰 생성 (CustomLoginFilter에서 호출)
     */
    public static CustomAuthenticationToken unauthenticated(String authCode) {
        return new CustomAuthenticationToken(authCode, null, null);
    }

    /**
     * 인증 완료 토큰 생성 (CustomAuthenticationProvider에서 호출)
     */
    public static CustomAuthenticationToken authenticated(CustomUserPrincipal principal) {
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority(principal.role())
        );
        return new CustomAuthenticationToken(null, principal, authorities);
    }

    private CustomAuthenticationToken(
            String authCode,
            CustomUserPrincipal principal,
            Collection<? extends GrantedAuthority> authorities
    ) {
        super(authorities);
        this.authCode = authCode;
        this.principal = principal;
        setAuthenticated(authorities != null && !authorities.isEmpty());
    }

    /**
     * 자격증명 반환
     * - 미인증 시: authCode (검증에 사용)
     * - 인증 완료 시: null (보안상 제거)
     */
    @Override
    public Object getCredentials() {
        return authCode;
    }

    /**
     * 주체 반환
     * - 미인증 시: null
     * - 인증 완료 시: CustomUserPrincipal
     */
    @Override
    public Object getPrincipal() {
        return principal;
    }
}
