package com.exam.springdeepdive.beancycle;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
public class InitBean implements InitializingBean {

    @Override
    public void afterPropertiesSet() {
        System.out.println("[InitBean(InitializingBean)]");
    }
}
