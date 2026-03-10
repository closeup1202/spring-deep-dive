package com.exam.oauth2.auth;

import com.exam.oauth2.user.CustomUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * CustomAuthenticationProvider 단위 테스트
 *
 * AuthCodeStore를 Mock으로 대체해 Provider 로직만 테스트.
 * epki-auth의 EpkiAuthenticationProviderTest 참고.
 */
class CustomAuthenticationProviderTest {

    private AuthCodeStore authCodeStore;
    private CustomAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        authCodeStore = mock(AuthCodeStore.class);
        provider = new CustomAuthenticationProvider(authCodeStore);
    }

    @Test
    @DisplayName("유효한 authCode - 인증 성공")
    void shouldAuthenticateWithValidCode() {
        // given
        String authCode = "valid-code-001";
        AuthInfo authInfo = AuthInfo.of("user001", "홍길동", "ROLE_USER");

        when(authCodeStore.consumeAndGet(authCode))
                .thenReturn(java.util.Optional.of(authInfo));

        CustomAuthenticationToken unauthenticated = CustomAuthenticationToken.unauthenticated(authCode);

        // when
        Authentication result = provider.authenticate(unauthenticated);

        // then
        assertThat(result).isNotNull();
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getPrincipal()).isInstanceOf(CustomUserPrincipal.class);

        CustomUserPrincipal principal = (CustomUserPrincipal) result.getPrincipal();
        assertThat(principal.userId()).isEqualTo("user001");
        assertThat(principal.username()).isEqualTo("홍길동");
        assertThat(principal.role()).isEqualTo("ROLE_USER");

        // authCode는 단 1회만 소비되어야 함
        verify(authCodeStore, times(1)).consumeAndGet(authCode);
    }

    @Test
    @DisplayName("유효하지 않은 authCode - BadCredentialsException 발생")
    void shouldThrowExceptionForInvalidCode() {
        // given
        String invalidCode = "invalid-code";
        when(authCodeStore.consumeAndGet(invalidCode))
                .thenReturn(java.util.Optional.empty());

        CustomAuthenticationToken unauthenticated = CustomAuthenticationToken.unauthenticated(invalidCode);

        // when & then
        assertThatThrownBy(() -> provider.authenticate(unauthenticated))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("유효하지 않거나 만료된");
    }

    @Test
    @DisplayName("만료된 authCode - BadCredentialsException 발생")
    void shouldThrowExceptionForExpiredCode() {
        // given - 코드가 이미 소비되어 없음
        String expiredCode = "already-used-code";
        when(authCodeStore.consumeAndGet(expiredCode))
                .thenReturn(java.util.Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                provider.authenticate(CustomAuthenticationToken.unauthenticated(expiredCode))
        ).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("supports() - CustomAuthenticationToken 지원")
    void shouldSupportCustomAuthenticationToken() {
        assertThat(provider.supports(CustomAuthenticationToken.class)).isTrue();
    }

    @Test
    @DisplayName("supports() - UsernamePasswordAuthenticationToken 미지원")
    void shouldNotSupportUsernamePasswordToken() {
        assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isFalse();
    }

    @Test
    @DisplayName("인증 완료 토큰의 권한 검증")
    void shouldSetCorrectAuthoritiesOnAuthentication() {
        // given
        AuthInfo authInfo = AuthInfo.of("admin001", "관리자", "ROLE_ADMIN");
        when(authCodeStore.consumeAndGet("admin-code"))
                .thenReturn(java.util.Optional.of(authInfo));

        // when
        Authentication result = provider.authenticate(
                CustomAuthenticationToken.unauthenticated("admin-code")
        );

        // then
        assertThat(result.getAuthorities()).hasSize(1);
        assertThat(result.getAuthorities().iterator().next().getAuthority())
                .isEqualTo("ROLE_ADMIN");
    }
}
