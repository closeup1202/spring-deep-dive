package com.exam.springdeepdive.transaction.antipattern;

import com.exam.springdeepdive.transaction.Member;
import com.exam.springdeepdive.transaction.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Anti-Pattern 3: Transaction Propagation Conflict Detection
 *
 * 트랜잭션 전파 속성 충돌로 인한 런타임 에러 발생 시나리오
 *
 * 1. MANDATORY: 트랜잭션 없이 호출 시 예외 발생
 * 2. NEVER: 트랜잭션 내에서 호출 시 예외 발생
 * 3. REQUIRES_NEW: 데이터 불일치 위험
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropagationConflictService {

    private final MemberRepository memberRepository;

    /**
     * ❌ BAD: MANDATORY - 트랜잭션 없이 호출하면 예외 발생
     *
     * MANDATORY는 "반드시 기존 트랜잭션이 있어야 한다"는 의미
     * 트랜잭션 없이 호출하면 IllegalTransactionStateException 발생
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void mandatoryMethod() {
        log.info("mandatoryMethod() - MANDATORY propagation");
        Member member = new Member();
        member.setUsername("Mandatory-Member");
        memberRepository.save(member);
    }

    /**
     * 테스트: MANDATORY를 트랜잭션 없이 호출
     */
    public void testMandatoryWithoutTransaction() {
        log.info("=== Testing MANDATORY without transaction ===");
        try {
            mandatoryMethod();  // IllegalTransactionStateException 발생!
        } catch (Exception e) {
            log.error("Expected exception: {}", e.getClass().getSimpleName());
            log.error("Message: {}", e.getMessage());
        }
    }

    /**
     * ✅ GOOD: MANDATORY를 트랜잭션 내에서 호출
     */
    @Transactional
    public void testMandatoryWithTransaction() {
        log.info("=== Testing MANDATORY with transaction ===");
        mandatoryMethod();  // 정상 작동
        log.info("MANDATORY method succeeded!");
    }

    /**
     * ❌ BAD: NEVER - 트랜잭션 내에서 호출하면 예외 발생
     *
     * NEVER는 "절대 트랜잭션이 있으면 안 된다"는 의미
     * 트랜잭션 내에서 호출하면 IllegalTransactionStateException 발생
     */
    @Transactional(propagation = Propagation.NEVER)
    public void neverMethod() {
        log.info("neverMethod() - NEVER propagation");
        Member member = new Member();
        member.setUsername("Never-Member");
        memberRepository.save(member);
    }

    /**
     * 테스트: NEVER를 트랜잭션 내에서 호출
     */
    @Transactional
    public void testNeverWithTransaction() {
        log.info("=== Testing NEVER with transaction ===");
        try {
            neverMethod();  // IllegalTransactionStateException 발생!
        } catch (Exception e) {
            log.error("Expected exception: {}", e.getClass().getSimpleName());
            log.error("Message: {}", e.getMessage());
        }
    }

    /**
     * ✅ GOOD: NEVER를 트랜잭션 없이 호출
     */
    public void testNeverWithoutTransaction() {
        log.info("=== Testing NEVER without transaction ===");
        neverMethod();  // 정상 작동
        log.info("NEVER method succeeded!");
    }

    /**
     * ⚠️ WARNING: REQUIRES_NEW - 데이터 불일치 위험
     *
     * REQUIRES_NEW는 항상 새로운 트랜잭션을 생성합니다.
     * 외부 트랜잭션과 독립적이므로, 외부만 롤백되면 데이터 불일치 발생 가능
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requiresNewMethod() {
        log.info("requiresNewMethod() - REQUIRES_NEW propagation");
        Member member = new Member();
        member.setUsername("RequiresNew-Member");
        memberRepository.save(member);
        log.info("Inner transaction committed (REQUIRES_NEW)");
    }

    /**
     * 데이터 불일치 시나리오 재현
     */
    @Transactional
    public void testDataInconsistency() {
        log.info("=== Testing REQUIRES_NEW data inconsistency ===");

        // 외부 트랜잭션에서 데이터 저장
        Member outerMember = new Member();
        outerMember.setUsername("Outer-Member");
        memberRepository.save(outerMember);
        log.info("Outer member saved: {}", outerMember.getUsername());

        // 내부 트랜잭션 (REQUIRES_NEW) 실행 및 커밋
        requiresNewMethod();

        // 외부 트랜잭션에서 예외 발생 -> 외부만 롤백
        log.info("Throwing exception in outer transaction...");
        throw new RuntimeException("Outer transaction rollback!");

        // 결과:
        // - Outer-Member: 롤백됨 (DB에 없음)
        // - RequiresNew-Member: 커밋됨 (DB에 남음) <- 데이터 불일치!
    }
}
