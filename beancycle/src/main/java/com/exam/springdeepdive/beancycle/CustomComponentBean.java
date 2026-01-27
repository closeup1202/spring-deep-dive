package com.exam.springdeepdive.beancycle;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class CustomComponentBean {

    public CustomComponentBean() {
        System.out.println("1. Constructor [CustomComponentBean]");
    }

    @PostConstruct
    public void postConstruct() {
        System.out.println("3. @PostConstruct [CustomComponentBean]");
    }
}
