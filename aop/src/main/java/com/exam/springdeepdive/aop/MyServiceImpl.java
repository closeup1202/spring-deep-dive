package com.exam.springdeepdive.aop;

import org.springframework.stereotype.Service;

@Service
public class MyServiceImpl implements MyService {
    @Override
    public void doSomething() {
        System.out.println("Doing something in MyServiceImpl");
    }
}
