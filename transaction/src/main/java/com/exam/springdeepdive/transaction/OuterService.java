package com.exam.springdeepdive.transaction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OuterService {

    private final MemberService memberService;

    public OuterService(MemberService memberService) {
        this.memberService = memberService;
    }

    @Transactional
    public void outerTransactionWithRequiresNew() {
        System.out.println("=== Outer Transaction Start ===");
        
        // 1. 이 로직은 Outer 트랜잭션에 묶임
        System.out.println("Outer Logic Executed");

        try {
            // 2. REQUIRES_NEW로 실행되는 Inner 트랜잭션
            // Inner에서 예외가 발생해도 Outer는 영향을 받지 않도록 try-catch 처리
            memberService.saveWithRequiresNew("InnerMember");
        } catch (Exception e) {
            System.out.println("Caught exception from inner: " + e.getMessage());
        }

        // 3. Outer 트랜잭션 계속 진행
        System.out.println("=== Outer Transaction End ===");
    }
}
