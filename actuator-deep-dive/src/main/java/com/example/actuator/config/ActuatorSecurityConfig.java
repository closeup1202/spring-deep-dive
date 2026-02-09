package com.example.actuator.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Actuator 엔드포인트 보안 설정
 *
 * 프로덕션 환경에서 Actuator 엔드포인트는 민감한 정보를 노출하므로
 * 반드시 인증/인가를 설정해야 합니다.
 *
 * 보안 전략:
 * 1. /actuator/health, /actuator/info - 인증 없이 접근 가능 (public)
 * 2. 나머지 엔드포인트 - ADMIN 권한 필요
 * 3. 프로덕션에서는 IP 화이트리스트 추가 권장
 */
@Configuration
@EnableWebSecurity
public class ActuatorSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        // 일반 API는 인증 없이 접근 가능
                        .requestMatchers("/api/**").permitAll()

                        // health와 info는 public (Kubernetes liveness/readiness probe)
                        .requestMatchers(
                                EndpointRequest.to("health", "info")
                        ).permitAll()

                        // Prometheus 메트릭은 별도 인증 (실제로는 네트워크 레벨에서 제한)
                        .requestMatchers(
                                EndpointRequest.to("prometheus")
                        ).permitAll()

                        // 나머지 actuator 엔드포인트는 ADMIN 권한 필요
                        .requestMatchers(
                                EndpointRequest.toAnyEndpoint()
                        ).hasRole("ADMIN")

                        .anyRequest().authenticated()
                )
                .httpBasic(httpBasic -> {}) // HTTP Basic 인증 사용
                .csrf(csrf -> csrf.disable()); // 테스트 편의를 위해 CSRF 비활성화

        return http.build();
    }

    /**
     * 테스트용 인메모리 사용자
     * 프로덕션에서는 OAuth2, LDAP 등을 사용
     */
    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails admin = User.builder()
                .username("admin")
                .password(passwordEncoder().encode("admin123"))
                .roles("ADMIN")
                .build();

        UserDetails user = User.builder()
                .username("user")
                .password(passwordEncoder().encode("user123"))
                .roles("USER")
                .build();

        return new InMemoryUserDetailsManager(admin, user);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
