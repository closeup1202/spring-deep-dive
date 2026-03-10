package com.exam.oauth2.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CustomLoginFilter 단위 테스트
 *
 * 테스트 전략:
 *   - AuthenticationManager를 Mock으로 대체
 *   - MockHttpServletRequest로 HTTP 요청 시뮬레이션
 *   - 실제 Spring Security 컨텍스트 불필요 (순수 단위 테스트)
 *
 * epki-auth의 EpkiAuthenticationFilterTest 참고.
 */
class CustomLoginFilterTest {

    private static final String LOGIN_URL = "/oauth2/login";
    private static final String ALLOWED_REFERER = "http://localhost";

    private AuthenticationManager authenticationManager;
    private CustomLoginFilter filter;

    @BeforeEach
    void setUp() {
        authenticationManager = mock(AuthenticationManager.class);
        filter = new CustomLoginFilter(
                new AntPathRequestMatcher(LOGIN_URL, "POST"),
                authenticationManager,
                ALLOWED_REFERER
        );
    }

    @Nested
    @DisplayName("요청 메서드 검증")
    class MethodValidation {

        @Test
        @DisplayName("GET 요청 시 예외 발생")
        void shouldRejectGetMethod() {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", LOGIN_URL);
            request.addHeader("Referer", "http://localhost/login");

            assertThatThrownBy(() ->
                    filter.attemptAuthentication(request, new MockHttpServletResponse())
            )
                    .isInstanceOf(AuthenticationException.class)
                    .hasMessageContaining("지원하지 않는 HTTP 메서드");
        }

        @Test
        @DisplayName("POST 요청은 통과")
        void shouldAcceptPostMethod() {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", LOGIN_URL);
            request.addHeader("Referer", "http://localhost/login");
            request.setParameter("authCode", "test-code-001");

            // AuthenticationManager가 성공적으로 인증한 것처럼 Mock
            when(authenticationManager.authenticate(any()))
                    .thenReturn(CustomAuthenticationToken.authenticated(
                            new com.exam.oauth2.user.CustomUserPrincipal("user1", "홍길동", "ROLE_USER")
                    ));

            var result = filter.attemptAuthentication(request, new MockHttpServletResponse());

            assertThat(result).isNotNull();
            assertThat(result.isAuthenticated()).isTrue();
        }
    }

    @Nested
    @DisplayName("Referer 헤더 검증")
    class RefererValidation {

        @Test
        @DisplayName("Referer 헤더 없으면 예외 발생")
        void shouldRejectMissingReferer() {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", LOGIN_URL);
            // Referer 헤더 없음

            assertThatThrownBy(() ->
                    filter.attemptAuthentication(request, new MockHttpServletResponse())
            )
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessageContaining("Referer");
        }

        @Test
        @DisplayName("허용되지 않은 Referer 도메인이면 예외 발생")
        void shouldRejectInvalidReferer() {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", LOGIN_URL);
            request.addHeader("Referer", "https://malicious.com/login");

            assertThatThrownBy(() ->
                    filter.attemptAuthentication(request, new MockHttpServletResponse())
            )
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessageContaining("허용되지 않은 Referer");
        }

        @Test
        @DisplayName("허용된 Referer 도메인은 통과")
        void shouldAcceptValidReferer() {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", LOGIN_URL);
            request.addHeader("Referer", "http://localhost:9090/login");
            request.setParameter("authCode", "valid-code");

            when(authenticationManager.authenticate(any()))
                    .thenReturn(CustomAuthenticationToken.authenticated(
                            new com.exam.oauth2.user.CustomUserPrincipal("u1", "테스트", "ROLE_USER")
                    ));

            var result = filter.attemptAuthentication(request, new MockHttpServletResponse());
            assertThat(result.isAuthenticated()).isTrue();
        }
    }

    @Nested
    @DisplayName("authCode 파라미터 검증")
    class AuthCodeValidation {

        @Test
        @DisplayName("authCode 파라미터 없으면 예외 발생")
        void shouldRejectMissingAuthCode() {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", LOGIN_URL);
            request.addHeader("Referer", "http://localhost/login");
            // authCode 파라미터 없음

            assertThatThrownBy(() ->
                    filter.attemptAuthentication(request, new MockHttpServletResponse())
            )
                    .isInstanceOf(AuthenticationException.class)
                    .hasMessageContaining("authCode");
        }

        @Test
        @DisplayName("authCode가 비어 있으면 예외 발생")
        void shouldRejectEmptyAuthCode() {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", LOGIN_URL);
            request.addHeader("Referer", "http://localhost/login");
            request.setParameter("authCode", "  "); // 공백

            assertThatThrownBy(() ->
                    filter.attemptAuthentication(request, new MockHttpServletResponse())
            )
                    .isInstanceOf(AuthenticationException.class);
        }
    }

    @Test
    @DisplayName("Referer 검증 없이도 동작 - 빈 prefix 설정")
    void shouldSkipRefererValidationWhenPrefixIsEmpty() {
        // Referer 검증을 끈 필터 (allowedRefererPrefix = "")
        CustomLoginFilter noRefererFilter = new CustomLoginFilter(
                new AntPathRequestMatcher(LOGIN_URL, "POST"),
                authenticationManager,
                "" // 검증 비활성화
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", LOGIN_URL);
        // Referer 없음 → 검증 스킵
        request.setParameter("authCode", "any-code");

        when(authenticationManager.authenticate(any()))
                .thenReturn(CustomAuthenticationToken.authenticated(
                        new com.exam.oauth2.user.CustomUserPrincipal("u1", "테스트", "ROLE_USER")
                ));

        var result = noRefererFilter.attemptAuthentication(request, new MockHttpServletResponse());
        assertThat(result.isAuthenticated()).isTrue();
    }
}
