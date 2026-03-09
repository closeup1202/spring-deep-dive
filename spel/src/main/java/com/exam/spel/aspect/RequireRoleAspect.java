package com.exam.spel.aspect;

import com.exam.spel.annotation.RequireRole;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RequireRoleAspect extends SpelAspectSupport {

    @Before("@annotation(requireRole)")
    public void checkRole(JoinPoint joinPoint, RequireRole requireRole) {
        StandardEvaluationContext context = buildContext(joinPoint, null);

        Expression expression = parser.parseExpression(requireRole.value());
        Boolean allowed = expression.getValue(context, Boolean.class);

        if (!Boolean.TRUE.equals(allowed)) {
            throw new SecurityException(requireRole.message());
        }
    }
}
