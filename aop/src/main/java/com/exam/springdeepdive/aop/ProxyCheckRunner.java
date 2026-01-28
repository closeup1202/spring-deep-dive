package com.exam.springdeepdive.aop;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ProxyCheckRunner implements ApplicationRunner {

    private final MyService myService;
    private final NoInterfaceService noInterfaceService;

    public ProxyCheckRunner(MyService myService, NoInterfaceService noInterfaceService) {
        this.myService = myService;
        this.noInterfaceService = noInterfaceService;
    }

    @Override
    public void run(ApplicationArguments args) {
        System.out.println("=== AOP Proxy Check ===");

        // 1. Interface based service
        System.out.println("--------------------------------------------------");
        System.out.println("1. MyService Class: " + myService.getClass().getName());
        System.out.println(" ");
        System.out.println("   Is Proxy? " + org.springframework.aop.support.AopUtils.isAopProxy(myService));
        System.out.println("   Is CGLIB? " + org.springframework.aop.support.AopUtils.isCglibProxy(myService));
        System.out.println("   Is JDK Dynamic? " + org.springframework.aop.support.AopUtils.isJdkDynamicProxy(myService));
        myService.doSomething();

        System.out.println("--------------------------------------------------");

        // 2. Class based service (No Interface)
        System.out.println("2. NoInterfaceService Class: " + noInterfaceService.getClass().getName());
        System.out.println(" ");
        System.out.println("   Is Proxy? " + org.springframework.aop.support.AopUtils.isAopProxy(noInterfaceService));
        System.out.println("   Is CGLIB? " + org.springframework.aop.support.AopUtils.isCglibProxy(noInterfaceService));
        System.out.println("   Is JDK Dynamic? " + org.springframework.aop.support.AopUtils.isJdkDynamicProxy(noInterfaceService));
        System.out.println("\n[Calling Normal Method]");
        noInterfaceService.doAction(); // AOP 로그 O
        System.out.println("--------------------------------------------------");
        System.out.println("\n[Calling Final Method]");
        noInterfaceService.doFinalAction(); // AOP 로그 X (예상)
        System.out.println("--------------------------------------------------");
    }
}
