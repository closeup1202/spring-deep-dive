package com.exam.springdeepdive.circulardi;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class ComponentA {

    private final ComponentB componentB;

    // 생성자 주입: A를 만들려면 B가 필요함
    public ComponentA(ComponentB componentB) {
        this.componentB = componentB;
    }
}
