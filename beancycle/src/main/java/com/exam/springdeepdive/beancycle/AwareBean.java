package com.exam.springdeepdive.beancycle;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.stereotype.Component;

@Component
public class AwareBean implements BeanNameAware {

    @Override
    public void setBeanName(String name) {
        System.out.println("[AwareBean(BeanNameAware)] = " + name);
    }
}