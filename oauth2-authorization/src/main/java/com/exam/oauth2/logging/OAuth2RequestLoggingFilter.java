package com.exam.oauth2.logging;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * OAuth2 엔드포인트 요청/응답 로깅 필터
 *
 * epki-auth의 OAuth2LoggingFilter 참고.
 * epki-auth는 OpenTelemetry(Tracer) + DB 로그 저장을 함.
 * 학습 모듈에서는 SLF4J 로깅으로 단순화.
 *
 * OncePerRequestFilter:
 *   같은 요청에 대해 단 한 번만 실행됨을 보장.
 *   → forward, include 등 내부 디스패치에서 중복 실행 방지.
 *
 * 실무(epki-auth) 패턴:
 *   1. 요청 시작 시간 기록
 *   2. 트레이스 ID(OpenTelemetry) 추출 → request attribute로 전파
 *   3. 클라이언트 ID 추출 (Authorization 헤더 또는 요청 파라미터)
 *   4. filterChain.doFilter() 실행
 *   5. 완료 후 ApiCallLog DB 저장 (clientId, traceId, duration, statusCode 등)
 *   6. 예외 발생 시 에러 정보와 함께 저장
 *
 * 로깅 대상 엔드포인트:
 *   Spring AS 기본 엔드포인트들 (OAuth2Endpoint enum 참고)
 */
@Slf4j
public class OAuth2RequestLoggingFilter extends OncePerRequestFilter {

    // OAuth2 관련 엔드포인트 (epki-auth의 OAuth2Endpoint enum 참고)
    private static final Set<String> OAUTH2_ENDPOINTS = Set.of(
            "/oauth2/authorize",
            "/oauth2/token",
            "/oauth2/introspect",
            "/oauth2/revoke",
            "/oauth2/device_authorization",
            "/oauth2/authenticate",
            "/oauth2/login",
            "/connect/logout",
            "/userinfo",
            "/connect/register"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain filterChain)
            throws IOException, ServletException {

        String requestURI = request.getRequestURI();
        String method = request.getMethod();
        long startTime = System.currentTimeMillis();

        // 클라이언트 ID 추출 (epki-auth: ClientIdService 역할)
        String clientId = extractClientId(request);

        try {
            filterChain.doFilter(request, response);

            // OAuth2 엔드포인트 요청만 로깅
            if (isOAuth2Endpoint(requestURI)) {
                long duration = System.currentTimeMillis() - startTime;
                log.info("[OAuth2] {} {} | clientId={} | status={} | duration={}ms",
                        method, requestURI, clientId, response.getStatus(), duration);

                // 실무: ApiCallLogService.save(...)로 DB에 저장
            }

        } catch (RuntimeException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[OAuth2] {} {} | clientId={} | duration={}ms | error={}",
                    method, requestURI, clientId, duration, e.getMessage());

            // 실무: ApiCallLogService.saveWithError(...)로 에러 로그 저장
            throw e;
        }
    }

    /**
     * 클라이언트 ID 추출 (epki-auth ClientIdService 참고)
     *
     * 우선순위:
     * 1. 요청 파라미터 client_id
     * 2. Authorization 헤더 (Basic Auth 디코딩)
     * 3. 찾지 못하면 "unknown"
     */
    private String extractClientId(HttpServletRequest request) {
        // 1. 파라미터에서
        String clientId = request.getParameter("client_id");
        if (clientId != null && !clientId.isBlank()) {
            return clientId;
        }

        // 2. Authorization 헤더 Basic Auth에서
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            try {
                String decoded = new String(
                        java.util.Base64.getDecoder().decode(authHeader.substring(6)));
                int colonIdx = decoded.indexOf(':');
                if (colonIdx > 0) {
                    return decoded.substring(0, colonIdx);
                }
            } catch (Exception ignored) {
                // 디코딩 실패 시 무시
            }
        }

        return "unknown";
    }

    private boolean isOAuth2Endpoint(String uri) {
        return OAUTH2_ENDPOINTS.stream().anyMatch(uri::startsWith);
    }
}
