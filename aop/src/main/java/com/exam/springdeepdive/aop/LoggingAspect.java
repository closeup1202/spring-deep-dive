package com.exam.springdeepdive.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LoggingAspect {

    @Before("execution(* com.exam.springdeepdive.aop.*Service*.*(..))")
    public void logBefore(JoinPoint joinPoint) {
        System.out.println("[AOP] Before method: " + joinPoint.getSignature().getName());
    }
}
