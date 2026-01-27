package com.exam.springdeepdive.beancycle;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

@Component
public class MyBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String name) {
        if (name.equals("lifecycleBean")) {
            System.out.println("5. postProcessBeforeInitialization [MyBeanPostProcessor]");
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String name) {
        if (name.equals("lifecycleBean")) {
            System.out.println("8. postProcessAfterInitialization [MyBeanPostProcessor]");
        }
        return bean;
    }
}
