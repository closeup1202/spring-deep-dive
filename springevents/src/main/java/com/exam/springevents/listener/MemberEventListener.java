package com.exam.springevents.listener;

import com.exam.springevents.event.MemberJoinedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class MemberEventListener {

    // 1. [기본] 트랜잭션과 상관없이 항상 실행 (동기)
    // 주의: 여기서 예외가 터지면 원본 트랜잭션도 같이 롤백될 수 있음!
    @EventListener
    public void handleNormal(MemberJoinedEvent event) {
        log.info("[Listener: Normal] Event received. (Thread: {})", Thread.currentThread().getName());
        
        if (event.name().contains("FailNormal")) {
            log.error("[Listener: Normal] Exception occurred! This should rollback the transaction.");
            throw new RuntimeException("Fail in Normal Listener");
        }
    }

    // 2. [AFTER_COMMIT] 트랜잭션이 성공적으로 커밋된 후에만 실행 (실무 사용 빈도 1위)
    // 용도: 이메일 발송, 알림톡 전송, 타 시스템 데이터 동기화
    // 특징: 여기서 실패해도 원본 트랜잭션은 이미 커밋되었으므로 롤백되지 않음.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAfterCommit(MemberJoinedEvent event) {
        log.info("[Listener: AFTER_COMMIT] Transaction Committed! Sending Welcome Email to {}. (Thread: {})", event.email(), Thread.currentThread().getName());
        
        if (event.name().contains("FailAfterCommit")) {
            log.error("[Listener: AFTER_COMMIT] Exception occurred! But transaction should be already committed.");
            throw new RuntimeException("Fail in AfterCommit Listener");
        }
    }

    // 3. [AFTER_ROLLBACK] 트랜잭션이 롤백되었을 때 실행
    // 용도: 실패 로그 기록, 관리자 알림, 임시 파일 삭제
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleAfterRollback(MemberJoinedEvent event) {
        log.warn("[Listener: AFTER_ROLLBACK] Transaction Rolled back! Cleanup for {}. (Thread: {})", event.name(), Thread.currentThread().getName());
    }

    // 4. [BEFORE_COMMIT] 트랜잭션 커밋 직전에 실행
    // 용도: 커밋 전 마지막 데이터 검증이나 추가 작업 (잘 안 쓰임)
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleBeforeCommit(MemberJoinedEvent event) {
        log.info("[Listener: BEFORE_COMMIT] About to commit. Final check for {}. (Thread: {})", event.name(), Thread.currentThread().getName());
    }
    
    // 5. [Async + AFTER_COMMIT] 비동기로 실행 (실무 권장 패턴)
    // 용도: 이메일 발송 등 시간이 걸리는 작업은 메인 스레드를 잡지 않도록 비동기로 처리
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAsyncAfterCommit(MemberJoinedEvent event) {
        log.info("[Listener: Async + AFTER_COMMIT] Sending Push Notification asynchronously...{} (Thread: {})", event.name(), Thread.currentThread().getName());
    }
}