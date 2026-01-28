package com.exam.springdeepdive.circulardi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ServiceB {

    @Autowired
    private ServiceA serviceA;

    public void callA() {
        System.out.println("ServiceB calls ServiceA");
        serviceA.doSomething();
    }

    public void doSomething() {
        System.out.println("ServiceB is doing something");
    }
}
