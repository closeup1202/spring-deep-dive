package com.exam.springdeepdive.circulardi;

import org.springframework.stereotype.Component;

@Component
public class ComponentB {

    private final ComponentA componentA;

    // 생성자 주입: B를 만들려면 A가 필요함 -> 순환 참조 발생!
    public ComponentB(ComponentA componentA) {
        this.componentA = componentA;
    }
}
