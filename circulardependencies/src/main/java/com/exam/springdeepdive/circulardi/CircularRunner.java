package com.exam.springdeepdive.circulardi;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class CircularRunner implements ApplicationRunner {

    private final ServiceA serviceA;

    public CircularRunner(ServiceA serviceA) {
        this.serviceA = serviceA;
    }

    @Override
    public void run(ApplicationArguments args) {
        System.out.println("=== Circular Dependency Test (Field Injection) ===");
        serviceA.callB();
    }
}
