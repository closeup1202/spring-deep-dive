package com.exam.springevents.service;

import com.exam.springevents.event.MemberJoinedEvent;
import com.exam.springevents.publisher.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final EventPublisher eventPublisher;

    @Transactional
    public void join(String name, String email) {
        log.info("=== [Service] Start join logic for {} ===", name);
        
        // 1. DB 저장 로직 (생략 - 실제로는 Repository.save 호출)
        log.info("[Service] Saving member to DB...");

        // 2. 이벤트 발행
        eventPublisher.publish(new MemberJoinedEvent(name, email));
        
        log.info("=== [Service] End join logic ===");
    }

    @Transactional
    public void joinAndFail(String name, String email) {
        log.info("=== [Service] Start join logic (Will Fail) for {} ===", name);

        // 1. 이벤트 발행 (트랜잭션 안에서 발행됨)
        eventPublisher.publish(new MemberJoinedEvent(name, email));

        // 2. 예외 발생 -> 트랜잭션 롤백
        log.info("[Service] Exception occurred! Rolling back...");
        throw new RuntimeException("DB Error occurred!");
    }
}