package com.exam.springdeepdive.transaction.antipattern;

import com.exam.springdeepdive.transaction.Member;
import com.exam.springdeepdive.transaction.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Anti-Pattern 8: ReadOnly Transaction Calling Write Methods
 *
 * @Transactional(readOnly=true) 메서드가 쓰기 가능한 메서드를 호출할 때 발생하는 문제
 *
 * 시나리오 1: 같은 클래스 호출 (ERROR)
 * - 프록시 우회로 REQUIRES_NEW가 작동하지 않음
 * - readOnly 상태 유지되어 write 실패
 *
 * 시나리오 2: 다른 클래스 호출 (WARNING)
 * - REQUIRED 전파: readOnly 상속되어 write 실패 가능
 * - REQUIRES_NEW 전파: 새 트랜잭션 생성하여 해결 가능
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReadOnlyCallWriteService {

    private final MemberRepository memberRepository;
    private final WriteHelperService writeHelperService;

    /**
     * ❌ ERROR: 같은 클래스 내에서 write 메서드 호출
     *
     * 문제:
     * - readOnly=true 메서드에서 writeInNewTransaction() 호출
     * - 프록시 우회로 REQUIRES_NEW가 작동하지 않음
     * - readOnly 트랜잭션 상태가 유지됨
     */
    @Transactional(readOnly = true)
    public void readOnlyCallingSameClassWrite() {
        log.info("=== ReadOnly calling same class write (ERROR) ===");

        // 읽기 작업
        long count = memberRepository.count();
        log.info("Current member count: {}", count);

        try {
            // ❌ 같은 클래스의 @Transactional(REQUIRES_NEW) 메서드 호출
            writeInNewTransaction();  // 프록시 우회로 REQUIRES_NEW 무시됨!
        } catch (Exception e) {
            log.error("Write failed: {}", e.getMessage());
            log.error("원인: 프록시 우회로 readOnly 상태 유지됨");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeInNewTransaction() {
        log.info("writeInNewTransaction() called");
        log.warn("하지만 같은 클래스에서 호출되어 REQUIRES_NEW가 무시됨!");

        Member member = new Member();
        member.setUsername("Same-Class-Write");
        memberRepository.save(member);  // readOnly 트랜잭션에서 실행 시도
    }

    /**
     * ⚠️ WARNING: 다른 클래스의 write 메서드 호출 (REQUIRED 전파)
     *
     * 문제:
     * - readOnly=true 트랜잭션이 REQUIRED 전파로 상속됨
     * - 하위 메서드도 readOnly로 실행됨
     */
    @Transactional(readOnly = true)
    public void readOnlyCallingExternalWriteREQUIRED() {
        log.info("=== ReadOnly calling external write with REQUIRED (WARNING) ===");

        long count = memberRepository.count();
        log.info("Current member count: {}", count);

        try {
            // ⚠️ 다른 클래스의 REQUIRED 메서드 호출
            writeHelperService.writeWithRequired();  // readOnly가 상속됨!
        } catch (Exception e) {
            log.error("Write failed: {}", e.getMessage());
            log.error("원인: REQUIRED 전파로 readOnly 상속됨");
        }
    }

    /**
     * ✅ GOOD: 다른 클래스의 write 메서드 호출 (REQUIRES_NEW 전파)
     *
     * 해결책:
     * - REQUIRES_NEW 전파를 사용하여 새로운 쓰기 가능한 트랜잭션 생성
     * - readOnly 상태와 독립적으로 write 작업 수행
     */
    @Transactional(readOnly = true)
    public void readOnlyCallingExternalWriteREQUIRES_NEW() {
        log.info("=== ReadOnly calling external write with REQUIRES_NEW (GOOD) ===");

        long count = memberRepository.count();
        log.info("Current member count: {}", count);

        // ✅ 다른 클래스의 REQUIRES_NEW 메서드 호출
        writeHelperService.writeWithRequiresNew();  // 새 트랜잭션 생성하여 성공!

        log.info("Write succeeded with REQUIRES_NEW propagation");
    }

    /**
     * 테스트 실행 메서드
     */
    public void demonstrateReadOnlyCallWriteProblems() {
        log.info("\n");
        log.info("========================================");
        log.info("ReadOnly Calling Write Methods Problems");
        log.info("========================================");

        // 1. 같은 클래스 호출 (ERROR)
        log.info("\n[1] Same class call (ERROR):");
        try {
            readOnlyCallingSameClassWrite();
        } catch (Exception e) {
            log.error("Failed: {}", e.getMessage());
        }

        // 2. 다른 클래스 + REQUIRED (WARNING)
        log.info("\n[2] External class + REQUIRED (WARNING):");
        try {
            readOnlyCallingExternalWriteREQUIRED();
        } catch (Exception e) {
            log.error("Failed: {}", e.getMessage());
        }

        // 3. 다른 클래스 + REQUIRES_NEW (GOOD)
        log.info("\n[3] External class + REQUIRES_NEW (GOOD):");
        try {
            readOnlyCallingExternalWriteREQUIRES_NEW();
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage());
        }

        log.info("\n========================================");
        log.info("결론:");
        log.info("1. 같은 클래스: Service 분리 필수");
        log.info("2. 다른 클래스: REQUIRES_NEW 전파 사용");
        log.info("========================================");
    }
}
