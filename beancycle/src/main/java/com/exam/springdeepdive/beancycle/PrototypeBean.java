package com.exam.springdeepdive.beancycle;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
public class PrototypeBean {

    public PrototypeBean() {
        System.out.println("1. Constructor [PrototypeBean]");
    }

    @PostConstruct
    public void init() {
        System.out.println("2. @PostConstruct [PrototypeBean]");
    }

    @PreDestroy
    public void destroy() {
        System.out.println("3. @PreDestroy [PrototypeBean] - This will NOT be called by container");
    }
}
