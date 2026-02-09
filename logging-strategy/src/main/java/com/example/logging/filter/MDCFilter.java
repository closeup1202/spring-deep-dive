package com.example.logging.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 모든 HTTP 요청에 대해 MDC에 traceId와 userId를 자동으로 설정하는 필터입니다.
 *
 * MDC(Mapped Diagnostic Context):
 * - 스레드 로컬 변수를 사용하여 로그에 컨텍스트 정보를 자동으로 추가
 * - 멀티스레드 환경에서도 각 요청의 로그를 추적 가능
 */
@Slf4j
@Component
@Order(1) // 가장 먼저 실행되도록 설정
public class MDCFilter extends OncePerRequestFilter {

    private static final String TRACE_ID = "traceId";
    private static final String USER_ID = "userId";
    private static final String REQUEST_URI = "requestUri";
    private static final String REQUEST_METHOD = "requestMethod";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // 1. X-Trace-Id 헤더가 있으면 사용, 없으면 새로 생성 (MSA 환경 대응)
            String traceId = request.getHeader("X-Trace-Id");
            if (traceId == null || traceId.isEmpty()) {
                traceId = UUID.randomUUID().toString().substring(0, 8);
            }
            MDC.put(TRACE_ID, traceId);

            // 2. 사용자 ID (실제로는 JWT에서 추출하거나 세션에서 가져옴)
            String userId = request.getHeader("X-User-Id");
            MDC.put(USER_ID, userId != null ? userId : "anonymous");

            // 3. 요청 정보
            MDC.put(REQUEST_URI, request.getRequestURI());
            MDC.put(REQUEST_METHOD, request.getMethod());

            // 4. 응답 헤더에도 traceId 추가 (클라이언트에서 추적 가능)
            response.setHeader("X-Trace-Id", traceId);

            log.info("🚀 Request started: {} {}", request.getMethod(), request.getRequestURI());

            filterChain.doFilter(request, response);

            log.info("✅ Request completed: {} - Status: {}", request.getRequestURI(), response.getStatus());

        } finally {
            // 5. 요청 처리 완료 후 MDC 정리 (메모리 누수 방지 - 매우 중요!)
            MDC.clear();
        }
    }
}
