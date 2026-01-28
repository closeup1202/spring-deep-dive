package com.exam.springdeepdive.aop;

import org.springframework.stereotype.Service;

@Service
public class MyServiceImpl implements MyService {
    @Override
    public void doSomething() {
        System.out.println("Doing something in MyServiceImpl");
    }

    @Override
    public String hello(String name) {
        System.out.println("Hello " + name + " in MyServiceImpl");
        return "Hello " + name;
    }

    @Override
    public void boom() {
        System.out.println("Boom in MyServiceImpl");
        throw new RuntimeException("Boom!");
    }
}
