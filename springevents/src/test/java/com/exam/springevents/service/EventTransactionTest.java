package com.exam.springevents.service;

import com.exam.springevents.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class EventTransactionTest {

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        memberRepository.clear();
    }

    @Test
    @DisplayName("일반 @EventListener에서 예외가 발생하면 트랜잭션이 롤백된다")
    void normalListenerExceptionRollback() {
        // When: 리스너에서 예외를 던지는 이름으로 가입 시도
        // MemberEventListener.handleNormal()에서 "FailNormal" 체크함
        assertThrows(RuntimeException.class, () -> {
            memberService.join("FailNormal", "test@example.com");
        });

        // Then: 트랜잭션이 롤백되어 DB에 저장되지 않아야 함
        assertThat(memberRepository.exists("FailNormal")).isFalse();
    }

    @Test
    @DisplayName("@TransactionalEventListener(AFTER_COMMIT)에서 예외가 발생해도 트랜잭션은 커밋된다")
    void afterCommitListenerExceptionCommit() {
        // When: AFTER_COMMIT 리스너에서 예외를 던지는 이름으로 가입 시도
        // MemberEventListener.handleAfterCommit()에서 "FailAfterCommit" 체크함
        // 주의: 리스너 예외가 메인 스레드로 전파되지 않을 수 있음 (Spring 구현에 따라 다름)
        // 하지만 여기서는 동기 호출이므로 예외가 전파될 가능성이 높음.
        // -> TransactionalEventListener는 기본적으로 예외를 삼키지 않지만,
        //    커밋 후 실행되므로 호출자에게 예외가 전달되더라도 이미 DB는 커밋된 상태임.
        
        try {
            memberService.join("FailAfterCommit", "test@example.com");
        } catch (Exception e) {
            System.out.println("예외 발생 (예상됨): " + e.getMessage());
        }

        // Then: 트랜잭션은 이미 커밋되었으므로 DB에 저장되어 있어야 함
        assertThat(memberRepository.exists("FailAfterCommit")).isTrue();
    }
}