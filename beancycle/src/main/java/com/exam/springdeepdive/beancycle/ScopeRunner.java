package com.exam.springdeepdive.beancycle;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class ScopeRunner implements ApplicationRunner {

    private final ApplicationContext applicationContext;

    public ScopeRunner(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        System.out.println("=== Requesting Prototype Bean 1 ===");
        PrototypeBean bean1 = applicationContext.getBean(PrototypeBean.class);
        
        System.out.println("=== Requesting Prototype Bean 2 ===");
        PrototypeBean bean2 = applicationContext.getBean(PrototypeBean.class);

        System.out.println("Are beans same? " + (bean1 == bean2));
    }
}
