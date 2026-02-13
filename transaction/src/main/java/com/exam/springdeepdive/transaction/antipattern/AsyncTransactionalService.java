package com.exam.springdeepdive.transaction.antipattern;

import com.exam.springdeepdive.transaction.Member;
import com.exam.springdeepdive.transaction.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Anti-Pattern 7: @Async and @Transactional Conflicts
 *
 * @Async와 @Transactional을 함께 사용할 때 발생하는 3가지 문제:
 *
 * 1. 같은 메서드에 동시 사용
 * 2. @Async 메서드에서 Lazy 로딩
 * 3. 같은 클래스 내 @Async 메서드 호출 (프록시 우회)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTransactionalService {

    private final MemberRepository memberRepository;

    /**
     * ❌ BAD: 같은 메서드에 @Async와 @Transactional 동시 사용
     *
     * 문제:
     * - @Async는 별도 스레드에서 실행
     * - 호출한 스레드의 트랜잭션과 완전히 분리됨
     * - 예상치 못한 트랜잭션 동작 발생
     */
    @Async
    @Transactional
    public void asyncAndTransactional() {
        log.info("=== @Async + @Transactional (BAD) ===");
        log.info("Thread: {}", Thread.currentThread().getName());

        Member member = new Member();
        member.setUsername("Async-Transactional");
        memberRepository.save(member);

        log.warn("트랜잭션이 비동기 스레드에서 실행됨 (호출 스레드와 무관)");
    }

    /**
     * ❌ BAD: @Async 메서드에서 Lazy 로딩 접근
     *
     * 문제:
     * - @Async 메서드는 별도 스레드에서 실행
     * - 호출한 쪽의 트랜잭션은 이미 종료됨
     * - Lazy 로딩 시 LazyInitializationException 발생
     */
    @Async
    public void asyncWithLazyLoading(Team team) {
        log.info("=== @Async with Lazy Loading (BAD) ===");
        log.info("Thread: {}", Thread.currentThread().getName());

        try {
            // ❌ 트랜잭션이 종료된 상태에서 Lazy 로딩 시도
            int memberCount = team.getMembers().size();
            log.info("Team: {}, Members: {}", team.getName(), memberCount);
        } catch (Exception e) {
            log.error("LazyInitializationException 발생!");
            log.error("원인: 트랜잭션이 이미 종료되어 Lazy 로딩 불가능");
        }
    }

    /**
     * ❌ BAD: 같은 클래스 내에서 @Async 메서드 호출
     *
     * 문제:
     * - 프록시 우회로 @Async가 작동하지 않음
     * - 동기적으로 실행됨 (비동기 실행 안 됨)
     */
    public void callAsyncMethodInSameClass() {
        log.info("=== Calling @Async in same class (BAD) ===");
        log.info("Caller thread: {}", Thread.currentThread().getName());

        asyncMethod();  // ❌ 프록시 우회로 동기 실행됨

        log.info("Returned immediately (should wait for async)");
    }

    @Async
    public void asyncMethod() {
        log.info("asyncMethod() executed");
        log.info("Thread: {}", Thread.currentThread().getName());
        log.warn("이 메서드는 동기적으로 실행됨 (프록시 우회)");
    }

    /**
     * ✅ GOOD: @Transactional 메서드에서 데이터 준비 후 @Async 호출
     *
     * 해결책:
     * 1. 트랜잭션 내에서 필요한 데이터를 미리 초기화 (Lazy 로딩 해소)
     * 2. 초기화된 데이터를 @Async 메서드에 전달
     * 3. @Async 메서드는 별도 Service로 분리
     */
    @Transactional
    public void prepareDataForAsync() {
        log.info("=== Correct approach: Prepare data in @Transactional ===");

        Member member = new Member();
        member.setUsername("Prepared-Data");
        memberRepository.save(member);

        // ✅ 트랜잭션 내에서 데이터 초기화 완료
        log.info("Data prepared: {}", member.getUsername());

        // ✅ 별도 Service의 @Async 메서드 호출 (외부 클래스)
        // asyncNotificationService.sendEmailAsync(member);
    }

    /**
     * 테스트 실행 메서드
     */
    public void demonstrateAsyncTransactionalProblems() throws InterruptedException {
        log.info("\n");
        log.info("========================================");
        log.info("@Async and @Transactional Conflicts");
        log.info("========================================");

        // 1. 같은 메서드에 동시 사용
        log.info("\n[1] Same method with @Async + @Transactional:");
        asyncAndTransactional();
        Thread.sleep(1000);  // 비동기 작업 대기

        // 2. 같은 클래스 내 @Async 호출
        log.info("\n[2] Calling @Async in same class:");
        callAsyncMethodInSameClass();
        Thread.sleep(1000);

        log.info("\n========================================");
        log.info("결론:");
        log.info("1. @Async와 @Transactional을 같은 메서드에 사용 금지");
        log.info("2. @Async 메서드는 별도 Service로 분리");
        log.info("3. Lazy 로딩은 트랜잭션 내에서 미리 초기화");
        log.info("========================================");
    }
}
