package com.exam.spel.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Arrays;

/**
 * SpEL Aspect 공통 유틸 - 메서드 파라미터를 SpEL 컨텍스트 변수로 등록
 */
abstract class SpelAspectSupport {

    protected final ExpressionParser parser = new SpelExpressionParser();

    /**
     * JoinPoint의 파라미터를 #변수명 형태로 컨텍스트에 등록
     * result가 null이 아니면 #result 로도 참조 가능
     */
    protected StandardEvaluationContext buildContext(JoinPoint joinPoint, Object result) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames(); // 파라미터 이름 (-parameters 플래그 또는 Spring Boot 기본 설정으로 가능)
        Object[] args = joinPoint.getArgs();

        System.out.println("paramNames = " + Arrays.toString(paramNames)); // [user, orderId]
        System.out.println("args = " + Arrays.toString(args)); // [User[name=관리자, role=ADMIN], 1]

        StandardEvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }

        if (result != null) {
            context.setVariable("result", result);
        }

        return context;
    }
}
