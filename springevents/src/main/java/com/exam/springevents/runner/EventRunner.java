package com.exam.springevents.runner;

import com.exam.springevents.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventRunner implements ApplicationRunner {

    private final MemberService memberService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("==================================================");
        log.info("Scenario 1: Transaction Success (Commit)");
        log.info("==================================================");
        memberService.join("Alice", "alice@example.com");

        Thread.sleep(1000); // 로그 섞임 방지

        log.info("==================================================");
        log.info("Scenario 2: Transaction Failure (Rollback)");
        log.info("==================================================");
        try {
            memberService.joinAndFail("Bob", "bob@example.com");
        } catch (Exception e) {
            log.error("Caught expected exception: {}", e.getMessage());
        }
    }
}