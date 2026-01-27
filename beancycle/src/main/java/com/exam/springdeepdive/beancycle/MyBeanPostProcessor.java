package com.exam.springdeepdive.beancycle;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

@Component
public class MyBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String name) {
        if (name.equals("lifecycleBean")) {
            System.out.println("2. before init: " + name);
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String name) {
        if (name.equals("lifecycleBean")) {
            System.out.println("4. after init: " + name);
        }
        return bean;
    }
}
