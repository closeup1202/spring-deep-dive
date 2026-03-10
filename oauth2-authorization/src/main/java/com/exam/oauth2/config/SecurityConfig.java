package com.exam.oauth2.config;

import com.exam.oauth2.auth.CustomAuthenticationProvider;
import com.exam.oauth2.auth.CustomLoginFilter;
import com.exam.oauth2.logging.OAuth2RequestLoggingFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.util.function.Function;

/**
 * Spring Security + OAuth2 Authorization Server 핵심 설정
 *
 * epki-auth의 SecurityConfig 참고.
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │ 두 개의 SecurityFilterChain 구조                                  │
 * │                                                                 │
 * │  Chain 1 (@Order(1)) - OAuth2 Authorization Server              │
 * │    └─ 처리 경로: /oauth2/*, /connect/*, /userinfo, /.well-known │
 * │    └─ 미인증 시: /login 으로 redirect                             │
 * │    └─ JWT 리소스 서버로도 동작 (토큰 인트로스펙션)                  │
 * │                                                                 │
 * │  Chain 2 (@Order(2)) - Default Security                         │
 * │    └─ 처리 경로: 나머지 모든 경로                                  │
 * │    └─ /login, /oauth2/login, /h2-console/** 허용                │
 * │    └─ CustomLoginFilter: POST /oauth2/login 처리                │
 * │    └─ FormLogin: /login (테스트용 폼 로그인)                      │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │ 커스텀 인증 흐름 (epki-auth의 EPKI 인증서 인증과 동일 패턴)          │
 * │                                                                 │
 * │  1. GET /oauth2/authorize?client_id=web-client&...             │
 * │  2. 미인증 → Chain 1의 EntryPoint → redirect /login            │
 * │     (원래 요청을 HttpSession에 저장)                              │
 * │  3. GET /login → 로그인 페이지 (Spring Security 기본 제공)         │
 * │  4. POST /oauth2/login?authCode=test-code-001                  │
 * │     → CustomLoginFilter → CustomAuthenticationToken(미인증)     │
 * │     → ProviderManager → CustomAuthenticationProvider            │
 * │     → AuthCodeStore 조회 → CustomAuthenticationToken(인증완료)   │
 * │     → SecurityContext 저장                                      │
 * │     → SavedRequestAwareAuthenticationSuccessHandler             │
 * │       → 저장된 /oauth2/authorize로 redirect                     │
 * │  5. GET /oauth2/authorize (이번엔 인증됨)                         │
 * │     → Spring AS가 인가 코드(auth code) 발급                      │
 * │     → redirect_uri?code=... 로 redirect                        │
 * │  6. POST /oauth2/token (클라이언트가 코드를 토큰으로 교환)           │
 * └─────────────────────────────────────────────────────────────────┘
 */
@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${auth.login-endpoint}")
    private String loginEndpoint;

    @Value("${auth.allowed-referer-prefix}")
    private String allowedRefererPrefix;

    // ─── Chain 1: OAuth2 Authorization Server ─────────────────────

    /**
     * OAuth2 Authorization Server FilterChain
     *
     * applyDefaultSecurity()가 설정하는 것:
     *   - AuthorizationEndpoint: /oauth2/authorize
     *   - TokenEndpoint: /oauth2/token
     *   - TokenIntrospectionEndpoint: /oauth2/introspect
     *   - TokenRevocationEndpoint: /oauth2/revoke
     *   - JwkSetEndpoint: /oauth2/jwks
     *   - OidcEndpoints: /userinfo, /connect/logout, /.well-known/openid-configuration
     *
     * securityMatcher: 위 엔드포인트에 대해서만 이 체인이 적용됨.
     * 나머지 요청은 Chain 2로 넘어감.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            Function<OidcUserInfoAuthenticationContext, OidcUserInfo> userInfoMapper,
            OAuth2RequestLoggingFilter loggingFilter
    ) throws Exception {

        // Spring AS 기본 보안 설정 (모든 OAuth2 엔드포인트 등록)
        OAuth2AuthorizationServerConfigurer configurer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();

        http
            .securityMatcher(configurer.getEndpointsMatcher())
            .with(configurer, authServer -> authServer
                .oidc(oidc -> oidc
                    // OIDC UserInfo 엔드포인트 활성화 + 커스텀 매퍼 등록
                    .userInfoEndpoint(ui -> ui.userInfoMapper(userInfoMapper))
                )
            )
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            // 미인증 시 /login으로 redirect (HTML 요청에 한해)
            .exceptionHandling(e -> e
                .defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                )
            )
            // 이 체인도 리소스 서버로 동작 (JWT 검증 → /oauth2/introspect 지원)
            .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))
            // 요청 로깅 필터 추가 (epki-auth의 OAuth2LoggingFilter)
            .addFilterAfter(loggingFilter, org.springframework.security.web.context.SecurityContextHolderFilter.class);

        return http.build();
    }

    // ─── Chain 2: Default Security ────────────────────────────────

    /**
     * 기본 보안 FilterChain
     *
     * 로그인 페이지, 커스텀 인증 엔드포인트, H2 콘솔 등을 처리.
     * CustomLoginFilter를 UsernamePasswordAuthenticationFilter 앞에 추가.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(
            HttpSecurity http,
            CustomAuthenticationProvider customProvider,
            UserDetailsService userDetailsService
    ) throws Exception {

        // AuthenticationManager 구성
        // - CustomAuthenticationProvider: authCode 기반 인증 (POST /oauth2/login)
        // - DaoAuthenticationProvider: 폼 로그인 (POST /login)
        AuthenticationManager authManager = buildAuthenticationManager(customProvider, userDetailsService);

        // 커스텀 로그인 필터 생성 (POST /oauth2/login)
        CustomLoginFilter customLoginFilter = buildCustomLoginFilter(authManager);

        http
            .authenticationManager(authManager)
            .authorizeHttpRequests(auth -> auth
                // 커스텀 인증 엔드포인트, 로그인 페이지, H2 콘솔 허용
                .requestMatchers(loginEndpoint, "/login", "/error", "/h2-console/**").permitAll()
                .anyRequest().authenticated()
            )
            // Spring Security 기본 폼 로그인 (테스트용 - admin/admin123)
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")   // POST /login → DaoAuthenticationProvider
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
            )
            // CSRF 예외: 커스텀 인증 엔드포인트, H2 콘솔
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(loginEndpoint, "/h2-console/**")
            )
            // H2 Console을 iframe으로 표시
            .headers(h -> h.frameOptions(f -> f.disable()))
            // 커스텀 로그인 필터 등록 (폼 로그인 필터보다 앞에)
            .addFilterBefore(customLoginFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ─── 빈 정의 ──────────────────────────────────────────────────

    /**
     * 요청 로깅 필터 빈
     * epki-auth에서는 SecurityConfig가 직접 생성 (@Bean 메서드)
     */
    @Bean
    public OAuth2RequestLoggingFilter oAuth2RequestLoggingFilter() {
        return new OAuth2RequestLoggingFilter();
    }

    /**
     * 테스트용 in-memory 사용자 (폼 로그인 fallback)
     * 실무에서는 DB 기반 UserDetailsService 사용
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager(
                User.withUsername("admin")
                        .password("{noop}admin123")
                        .roles("ADMIN")
                        .build()
        );
    }

    // ─── 내부 헬퍼 ────────────────────────────────────────────────

    private AuthenticationManager buildAuthenticationManager(
            CustomAuthenticationProvider customProvider,
            UserDetailsService userDetailsService) {

        // 폼 로그인용 Provider
        DaoAuthenticationProvider daoProvider = new DaoAuthenticationProvider();
        daoProvider.setUserDetailsService(userDetailsService);

        // ProviderManager: 등록 순서대로 supports()를 확인해 처리 위임
        // - customProvider: CustomAuthenticationToken 처리
        // - daoProvider: UsernamePasswordAuthenticationToken 처리
        return new ProviderManager(customProvider, daoProvider);
    }

    private CustomLoginFilter buildCustomLoginFilter(AuthenticationManager authManager) {
        CustomLoginFilter filter = new CustomLoginFilter(
                new AntPathRequestMatcher(loginEndpoint, "POST"),
                authManager,
                allowedRefererPrefix
        );

        // 인증 성공 → 원래 요청(=/oauth2/authorize)으로 redirect
        // HttpSession에 저장된 SavedRequest를 꺼내 redirect
        SavedRequestAwareAuthenticationSuccessHandler successHandler =
                new SavedRequestAwareAuthenticationSuccessHandler();
        successHandler.setDefaultTargetUrl("/");
        filter.setAuthenticationSuccessHandler(successHandler);

        // 인증 실패 → /login?error 로 redirect
        filter.setAuthenticationFailureHandler(
                new SimpleUrlAuthenticationFailureHandler("/login?error"));

        // SecurityContext를 세션에 저장 (다음 요청에서 인증 유지)
        filter.setSecurityContextRepository(new HttpSessionSecurityContextRepository());

        return filter;
    }
}
