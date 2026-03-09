package com.exam.spel.aspect;

import com.exam.spel.annotation.AuditLog;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class AuditLogAspect extends SpelAspectSupport {

    // returnValue: 메서드 반환값 → #result 로 SpEL에서 참조
    @AfterReturning(pointcut = "@annotation(auditLog)", returning = "returnValue")
    public void logAfterReturning(JoinPoint joinPoint, AuditLog auditLog, Object returnValue) {
        StandardEvaluationContext context = buildContext(joinPoint, returnValue);

        Expression expression = parser.parseExpression(auditLog.value());
        String message = expression.getValue(context, String.class);

        log.info("[AUDIT] {}", message);
    }
}
