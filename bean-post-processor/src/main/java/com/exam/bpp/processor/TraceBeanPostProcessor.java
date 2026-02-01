package com.exam.bpp.processor;

import com.exam.bpp.annotation.Trace;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

@Slf4j
@Component
public class TraceBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 1. 빈의 클래스 정보를 가져옴
        Class<?> beanClass = bean.getClass();

        // 2. @Trace 어노테이션이 붙은 메서드가 하나라도 있는지 확인
        boolean hasTraceAnnotation = Arrays.stream(beanClass.getMethods())
                .anyMatch(method -> method.isAnnotationPresent(Trace.class));

        // 3. @Trace가 없다면 원본 빈 그대로 반환
        if (!hasTraceAnnotation) {
            return bean;
        }

        log.info("TraceBeanPostProcessor: '{}' 빈에 @Trace 발견! 프록시 적용 중...", beanName);

        // 4. 프록시 팩토리 생성 (Spring AOP 기능 활용)
        ProxyFactory proxyFactory = new ProxyFactory(bean);
        
        // 5. 어드바이스(부가 기능) 추가
        proxyFactory.addAdvice((MethodInterceptor) invocation -> {
            Method method = invocation.getMethod();

            // @Trace가 붙은 메서드만 시간 측정
            if (method.isAnnotationPresent(Trace.class)) {
                long startTime = System.currentTimeMillis();
                try {
                    return invocation.proceed(); // 실제 메서드 실행
                } finally {
                    long endTime = System.currentTimeMillis();
                    log.info("--> [Trace] {} 실행 시간: {}ms", method.getName(), (endTime - startTime));
                }
            } else {
                return invocation.proceed(); // @Trace 없으면 그냥 실행
            }
        });

        // 6. 프록시 객체 반환 (이제 컨테이너에는 원본 대신 이 프록시가 등록됨)
        return proxyFactory.getProxy();
    }
}
