package com.exam.springdeepdive.transaction.antipattern;

import com.exam.springdeepdive.transaction.Member;
import com.exam.springdeepdive.transaction.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Anti-Pattern 2: Invalid Method Modifiers
 *
 * private, final, static 메서드에 @Transactional을 붙여도 동작하지 않습니다.
 * Spring AOP는 CGLIB 프록시로 메서드를 오버라이드하는데,
 * 이 메서드들은 오버라이드할 수 없기 때문입니다.
 *
 * 실행 결과:
 * - private/final/static 메서드: 트랜잭션 적용 안 됨 (롤백 안 됨)
 * - public 메서드: 트랜잭션 정상 작동 (롤백됨)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvalidModifierService {

    private final MemberRepository memberRepository;

    /**
     * ❌ BAD: private 메서드에 @Transactional
     *
     * CGLIB 프록시는 private 메서드를 오버라이드할 수 없으므로
     * @Transactional이 무시됩니다.
     */
    @Transactional  // 무시됨!
    private void privateTransactionalMethod() {
        log.info("privateTransactionalMethod() called");

        Member member = new Member();
        member.setUsername("Private-Method-No-Rollback");
        memberRepository.save(member);

        throw new RuntimeException("Exception in private method");
        // 트랜잭션이 없어서 롤백 안 됨 -> DB에 데이터 남음
    }

    /**
     * ❌ BAD: final 메서드에 @Transactional
     *
     * final 메서드는 오버라이드할 수 없으므로 프록시가 적용되지 않습니다.
     */
    @Transactional  // 무시됨!
    public final void finalTransactionalMethod() {
        log.info("finalTransactionalMethod() called");

        Member member = new Member();
        member.setUsername("Final-Method-No-Rollback");
        memberRepository.save(member);

        throw new RuntimeException("Exception in final method");
        // 트랜잭션이 없어서 롤백 안 됨 -> DB에 데이터 남음
    }

    /**
     * ❌ BAD: static 메서드에 @Transactional
     *
     * static 메서드는 인스턴스 메서드가 아니므로 프록시 적용 불가능합니다.
     */
    @Transactional  // 무시됨!
    public static void staticTransactionalMethod(MemberRepository repo) {
        log.info("staticTransactionalMethod() called");

        Member member = new Member();
        member.setUsername("Static-Method-No-Rollback");
        repo.save(member);

        throw new RuntimeException("Exception in static method");
        // 트랜잭션이 없어서 롤백 안 됨 -> DB에 데이터 남음
    }

    /**
     * ✅ GOOD: public 인스턴스 메서드
     *
     * 올바른 방법: public, non-final, non-static 메서드
     */
    @Transactional  // 정상 작동!
    public void correctTransactionalMethod() {
        log.info("correctTransactionalMethod() called");

        Member member = new Member();
        member.setUsername("Public-Method-Will-Rollback");
        memberRepository.save(member);

        throw new RuntimeException("Exception in public method");
        // 트랜잭션이 정상 작동하여 롤백됨 -> DB에 데이터 안 남음
    }

    // 테스트용 wrapper 메서드들
    public void testPrivateMethod() {
        try {
            privateTransactionalMethod();
        } catch (Exception e) {
            log.error("Private method exception: {}", e.getMessage());
        }
    }

    public void testFinalMethod() {
        try {
            finalTransactionalMethod();
        } catch (Exception e) {
            log.error("Final method exception: {}", e.getMessage());
        }
    }

    public void testStaticMethod() {
        try {
            staticTransactionalMethod(memberRepository);
        } catch (Exception e) {
            log.error("Static method exception: {}", e.getMessage());
        }
    }

    public void testPublicMethod() {
        try {
            correctTransactionalMethod();
        } catch (Exception e) {
            log.error("Public method exception: {}", e.getMessage());
        }
    }
}
