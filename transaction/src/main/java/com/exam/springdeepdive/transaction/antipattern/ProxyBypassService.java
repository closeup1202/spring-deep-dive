package com.exam.springdeepdive.transaction.antipattern;

import com.exam.springdeepdive.transaction.Member;
import com.exam.springdeepdive.transaction.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Anti-Pattern 1: AOP Proxy Bypass (Self-Invocation)
 *
 * 같은 클래스 내에서 @Transactional 메서드를 호출하면 트랜잭션이 적용되지 않습니다.
 *
 * 실행 방법:
 * - testProxyBypass() 호출 시: 트랜잭션 적용 안 됨 (롤백 안 됨)
 * - 외부에서 saveWithTransaction() 직접 호출 시: 트랜잭션 정상 작동
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProxyBypassService {

    private final MemberRepository memberRepository;

    /**
     * ❌ BAD: 같은 클래스 내부에서 @Transactional 메서드 호출
     *
     * this.saveWithTransaction()은 프록시를 거치지 않고 직접 호출되므로
     * @Transactional이 무시됩니다.
     */
    public void testProxyBypass() {
        log.info("=== Proxy Bypass Test Started ===");

        try {
            // 프록시 우회로 트랜잭션이 시작되지 않음
            saveWithTransaction();
        } catch (Exception e) {
            log.error("Exception occurred: {}", e.getMessage());
        }

        log.info("=== Proxy Bypass Test Ended ===");
        // 예외가 발생했지만 트랜잭션이 없어서 롤백되지 않음
        // DB에 "ProxyBypass-Before-Exception" 데이터가 남아있음
    }

    /**
     * @Transactional이 붙어있지만, 같은 클래스 내부에서 호출되면 무시됨
     */
    @Transactional
    public void saveWithTransaction() {
        log.info("saveWithTransaction() called");

        Member member = new Member();
        member.setUsername("ProxyBypass-Before-Exception");
        memberRepository.save(member);
        log.info("Member saved: {}", member.getUsername());

        // 예외 발생 (정상적이면 롤백되어야 함)
        throw new RuntimeException("Intentional exception for testing");
    }

    /**
     * ✅ GOOD: 외부에서 직접 호출하면 프록시를 거쳐서 트랜잭션 정상 작동
     *
     * 다른 클래스에서 이 메서드를 호출하면 Spring AOP 프록시가 작동하여
     * 트랜잭션이 정상적으로 시작되고, 예외 발생 시 롤백됩니다.
     */
    @Transactional
    public void externalCallWorks() {
        log.info("=== External Call Test Started ===");

        Member member = new Member();
        member.setUsername("ExternalCall-Will-Be-Rollback");
        memberRepository.save(member);
        log.info("Member saved: {}", member.getUsername());

        throw new RuntimeException("This will trigger rollback!");
        // 이 경우 롤백됨 (외부에서 호출했으므로 프록시 작동)
    }
}
