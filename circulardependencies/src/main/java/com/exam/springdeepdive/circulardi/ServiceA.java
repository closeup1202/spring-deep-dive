package com.exam.springdeepdive.circulardi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ServiceA {

    @Autowired
    private ServiceB serviceB;

    public void callB() {
        System.out.println("ServiceA calls ServiceB");
        serviceB.doSomething();
    }

    public void doSomething() {
        System.out.println("ServiceA is doing something");
    }
}
