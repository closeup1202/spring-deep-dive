package com.exam.springdeepdive.aop;

import org.springframework.stereotype.Service;

@Service
public class NoInterfaceService {
    
    public void doAction() {
        System.out.println("Doing action in NoInterfaceService");
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // CGLIB는 상속을 기반으로 하므로 final 메서드는 오버라이딩 불가 -> AOP 적용 불가
    public final void doFinalAction() {
        System.out.println("Doing FINAL action in NoInterfaceService");
    }
}
