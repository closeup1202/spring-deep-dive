package com.exam.springdeepdive.transaction.antipattern;

import com.exam.springdeepdive.transaction.Member;
import com.exam.springdeepdive.transaction.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Anti-Pattern 6: Checked Exception Rollback
 *
 * rollbackFor 설정 없이 Checked Exception을 던지면 롤백되지 않습니다.
 *
 * Spring의 롤백 규칙:
 * - RuntimeException (Unchecked): 자동 롤백
 * - Exception (Checked): 자동 커밋 (롤백 안 됨!)
 *
 * 이유: Spring은 Checked Exception을 "복구 가능한 비즈니스 예외"로 간주
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckedExceptionService {

    private final MemberRepository memberRepository;

    /**
     * ❌ BAD: Checked Exception 발생 시 롤백 안 됨
     *
     * InsufficientFundsException은 Checked Exception (extends Exception)이므로
     * 롤백되지 않고 커밋됩니다.
     *
     * 결과: 데이터 불일치 발생 가능
     */
    @Transactional
    public void transferWithCheckedExceptionNOROLLBACK(String from, String to, int amount)
            throws InsufficientFundsException {
        log.info("=== Transfer with Checked Exception (NO rollbackFor) ===");

        // 1. from 계정에서 출금 기록
        Member fromMember = new Member();
        fromMember.setUsername(from + "-withdrew-" + amount);
        memberRepository.save(fromMember);
        log.info("Withdrawal recorded: {}", fromMember.getUsername());

        // 2. 잔액 부족 체크 (비즈니스 로직 예외)
        int balance = 100;  // 가정
        if (amount > balance) {
            log.error("Insufficient funds! Required: {}, Available: {}", amount, balance);
            throw new InsufficientFundsException("잔액이 부족합니다");
            // ❌ Checked Exception -> 롤백 안 됨!
            // 결과: 출금 기록만 남고 입금 기록은 없음 (데이터 불일치!)
        }

        // 3. to 계정에 입금 기록 (도달하지 않음)
        Member toMember = new Member();
        toMember.setUsername(to + "-deposited-" + amount);
        memberRepository.save(toMember);
        log.info("Deposit recorded: {}", toMember.getUsername());
    }

    /**
     * ✅ GOOD: rollbackFor로 Checked Exception도 롤백 설정
     *
     * rollbackFor = Exception.class 설정으로
     * Checked Exception 발생 시에도 롤백됩니다.
     */
    @Transactional(rollbackFor = Exception.class)  // 모든 예외에 대해 롤백
    public void transferWithRollbackFor(String from, String to, int amount)
            throws InsufficientFundsException {
        log.info("=== Transfer with rollbackFor = Exception.class ===");

        // 1. from 계정에서 출금 기록
        Member fromMember = new Member();
        fromMember.setUsername(from + "-withdrew-" + amount);
        memberRepository.save(fromMember);
        log.info("Withdrawal recorded: {}", fromMember.getUsername());

        // 2. 잔액 부족 체크
        int balance = 100;
        if (amount > balance) {
            log.error("Insufficient funds! Required: {}, Available: {}", amount, balance);
            throw new InsufficientFundsException("잔액이 부족합니다");
            // ✅ rollbackFor 설정으로 롤백됨!
            // 결과: 출금 기록도 롤백되어 데이터 일관성 유지
        }

        // 3. to 계정에 입금 기록
        Member toMember = new Member();
        toMember.setUsername(to + "-deposited-" + amount);
        memberRepository.save(toMember);
        log.info("Deposit recorded: {}", toMember.getUsername());
    }

    /**
     * ✅ GOOD: 특정 Checked Exception만 롤백
     */
    @Transactional(rollbackFor = InsufficientFundsException.class)
    public void transferWithSpecificRollback(String from, String to, int amount)
            throws InsufficientFundsException {
        log.info("=== Transfer with rollbackFor = InsufficientFundsException.class ===");

        Member fromMember = new Member();
        fromMember.setUsername(from + "-withdrew-" + amount);
        memberRepository.save(fromMember);

        int balance = 100;
        if (amount > balance) {
            throw new InsufficientFundsException("잔액이 부족합니다");
            // ✅ InsufficientFundsException에 대해 롤백
        }

        Member toMember = new Member();
        toMember.setUsername(to + "-deposited-" + amount);
        memberRepository.save(toMember);
    }

    /**
     * 비교: Unchecked Exception은 자동 롤백됨
     */
    @Transactional
    public void transferWithUncheckedExceptionAUTOROLLBACK(String from, String to, int amount) {
        log.info("=== Transfer with Unchecked Exception (auto rollback) ===");

        Member fromMember = new Member();
        fromMember.setUsername(from + "-withdrew-" + amount);
        memberRepository.save(fromMember);

        int balance = 100;
        if (amount > balance) {
            throw new RuntimeException("잔액이 부족합니다");  // RuntimeException
            // ✅ Unchecked Exception -> 자동 롤백!
        }

        Member toMember = new Member();
        toMember.setUsername(to + "-deposited-" + amount);
        memberRepository.save(toMember);
    }

    /**
     * 테스트 실행 메서드
     */
    public void demonstrateCheckedExceptionProblem() {
        log.info("\n");
        log.info("========================================");
        log.info("Checked Exception Rollback Problem");
        log.info("========================================");

        // 1. Checked Exception without rollbackFor (커밋됨!)
        log.info("\n[1] Checked Exception WITHOUT rollbackFor:");
        try {
            transferWithCheckedExceptionNOROLLBACK("Alice", "Bob", 500);
        } catch (InsufficientFundsException e) {
            log.error("Exception caught: {}", e.getMessage());
            log.error("❌ Transaction was COMMITTED (data inconsistency!)");
        }

        // 2. Checked Exception with rollbackFor (롤백됨)
        log.info("\n[2] Checked Exception WITH rollbackFor:");
        try {
            transferWithRollbackFor("Charlie", "David", 500);
        } catch (InsufficientFundsException e) {
            log.error("Exception caught: {}", e.getMessage());
            log.info("✅ Transaction was ROLLED BACK (data consistency maintained)");
        }

        // 3. Unchecked Exception (자동 롤백)
        log.info("\n[3] Unchecked Exception (auto rollback):");
        try {
            transferWithUncheckedExceptionAUTOROLLBACK("Eve", "Frank", 500);
        } catch (RuntimeException e) {
            log.error("Exception caught: {}", e.getMessage());
            log.info("✅ Transaction was ROLLED BACK automatically");
        }

        log.info("\n========================================");
        log.info("결론: Checked Exception 사용 시 반드시 rollbackFor 설정!");
        log.info("========================================");
    }
}
