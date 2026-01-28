package com.exam.springdeepdive.transaction;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class TxRunner implements ApplicationRunner {

    private final MemberService memberService;
    private final MemberRepository memberRepository;

    public TxRunner(MemberService memberService, MemberRepository memberRepository) {
        this.memberService = memberService;
        this.memberRepository = memberRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        System.out.println("==================================================");
        System.out.println("1. Unchecked Exception (RuntimeException) Test");
        try {
            memberService.saveAndThrowUncheckedException("Member1");
        } catch (RuntimeException e) {
            System.out.println("Caught: " + e.getMessage());
        }
        System.out.println("Members in DB: " + memberRepository.findAll()); // 기대: [] (롤백됨)

        System.out.println("==================================================");
        System.out.println("2. Checked Exception (Exception) Test");
        try {
            memberService.saveAndThrowCheckedException("Member2");
        } catch (Exception e) {
            System.out.println("Caught: " + e.getMessage());
        }
        System.out.println("Members in DB: " + memberRepository.findAll()); // 기대: [Member2] (커밋됨)
        memberRepository.deleteAll();

        System.out.println("==================================================");
        System.out.println("3. Checked Exception with rollbackFor Test");
        try {
            memberService.saveAndThrowCheckedExceptionWithRollbackOption("Member3");
        } catch (Exception e) {
            System.out.println("Caught: " + e.getMessage());
        }
        System.out.println("Members in DB: " + memberRepository.findAll()); // 기대: [] (롤백됨)
    }
}
