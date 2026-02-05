package com.project.curve.spring.audit.validation;

import com.project.curve.spring.audit.annotation.PublishEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;

/**
 * Validator that checks the validity of SpEL expressions in @PublishEvent annotations at application startup.
 * <p>
 * Outputs warning logs for invalid SpEL expressions so developers can be notified.
 */
@Slf4j
@Component
public class SpelExpressionValidator implements ApplicationListener<ContextRefreshedEvent>, ApplicationContextAware {

    private ApplicationContext applicationContext;
    private final SpelExpressionParser spelParser = new SpelExpressionParser();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        validateSpelExpressions();
    }

    private void validateSpelExpressions() {
        String[] beanNames = applicationContext.getBeanDefinitionNames();

        for (String beanName : beanNames) {
            try {
                Object bean = applicationContext.getBean(beanName);
                Class<?> targetClass = AopUtils.getTargetClass(bean);
                
                // Could filter out Spring internal beans or proxies,
                // but check all beans as ReflectionUtils.doWithMethods handles safely
                ReflectionUtils.doWithMethods(targetClass, method -> validateMethod(targetClass, method));
            } catch (Exception e) {
                // Ignore and continue if specific bean loading fails
                log.trace("Failed to validate bean '{}': {}", beanName, e.getMessage());
            }
        }
    }

    private void validateMethod(Class<?> targetClass, Method method) {
        PublishEvent publishEvent = AnnotationUtils.findAnnotation(method, PublishEvent.class);
        if (publishEvent == null) {
            return;
        }

        validatePayloadExpression(targetClass, method, publishEvent);
        validateAggregateIdExpression(targetClass, method, publishEvent);
    }

    private void validatePayloadExpression(Class<?> targetClass, Method method, PublishEvent publishEvent) {
        String expression = publishEvent.payload();
        if (expression != null && !expression.isBlank()) {
            try {
                spelParser.parseExpression(expression);
            } catch (ParseException e) {
                log.error("Invalid SpEL expression in @PublishEvent(payload=\"{}\") on method {}.{}: {}",
                        expression, targetClass.getSimpleName(), method.getName(), e.getMessage());
            }
        }
    }

    private void validateAggregateIdExpression(Class<?> targetClass, Method method, PublishEvent publishEvent) {
        String expression = publishEvent.aggregateId();
        if (publishEvent.outbox() && expression != null && !expression.isBlank()) {
            try {
                spelParser.parseExpression(expression);
            } catch (ParseException e) {
                log.error("Invalid SpEL expression in @PublishEvent(aggregateId=\"{}\") on method {}.{}: {}",
                        expression, targetClass.getSimpleName(), method.getName(), e.getMessage());
            }
        }
    }
}
