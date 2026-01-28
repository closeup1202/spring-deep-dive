package com.exam.springdeepdive.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LoggingAspect {

    @Pointcut("execution(* com.exam.springdeepdive.aop.*Service*.*(..))")
    public void serviceMethods() {}

    @Before("serviceMethods()")
    public void logBefore(JoinPoint joinPoint) {
        System.out.println("[AOP] Before method: " + joinPoint.getSignature().getName());
    }

    @After("serviceMethods()")
    public void logAfter(JoinPoint joinPoint) {
        System.out.println("[AOP] After method: " + joinPoint.getSignature().getName());
    }

    @AfterReturning(pointcut = "serviceMethods()", returning = "result")
    public void logAfterReturning(JoinPoint joinPoint, Object result) {
        System.out.println("[AOP] AfterReturning method: " + joinPoint.getSignature().getName() + ", result: " + result);
    }

    @AfterThrowing(pointcut = "serviceMethods()", throwing = "error")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable error) {
        System.out.println("[AOP] AfterThrowing method: " + joinPoint.getSignature().getName() + ", error: " + error);
    }

    @Around("serviceMethods()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        System.out.println("[AOP] Around before method: " + joinPoint.getSignature().getName());
        try {
            Object result = joinPoint.proceed();
            System.out.println("[AOP] Around after method: " + joinPoint.getSignature().getName());
            return result;
        } catch (Throwable e) {
            System.out.println("[AOP] Around error method: " + joinPoint.getSignature().getName());
            throw e;
        }
    }
}
