package com.example.logging.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * AOP를 사용하여 모든 컨트롤러 메서드 호출 시 자동으로 MDC에 traceId를 추가합니다.
 * 이를 통해 분산 환경에서 요청을 추적할 수 있습니다.
 */
@Slf4j
@Aspect
@Component
public class LoggingAspect {

    private static final String TRACE_ID = "traceId";
    private static final String USER_ID = "userId";

    @Around("execution(* com.example.logging.controller..*(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        // 1. traceId가 없으면 생성 (최상위 진입점)
        if (MDC.get(TRACE_ID) == null) {
            MDC.put(TRACE_ID, UUID.randomUUID().toString().substring(0, 8));
        }

        String methodName = joinPoint.getSignature().toShortString();

        long startTime = System.currentTimeMillis();
        log.info("▶ Method started: {}", methodName);

        try {
            Object result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("◀ Method completed: {} in {}ms", methodName, executionTime);
            return result;
        } catch (Exception e) {
            log.error("✘ Method failed: {} - Error: {}", methodName, e.getMessage(), e);
            throw e;
        } finally {
            // 2. 최상위 메서드가 끝나면 MDC 정리 (메모리 누수 방지)
            // 실제로는 Filter에서 처리하는 것이 더 안전함
            if (methodName.contains("Controller")) {
                MDC.clear();
            }
        }
    }
}
