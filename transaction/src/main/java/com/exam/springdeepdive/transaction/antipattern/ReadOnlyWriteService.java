package com.exam.springdeepdive.transaction.antipattern;

import com.exam.springdeepdive.transaction.Member;
import com.exam.springdeepdive.transaction.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Anti-Pattern 5: ReadOnly Transaction Write Operations
 *
 * @Transactional(readOnly=true) 메서드에서 write 작업을 수행하는 문제
 *
 * readOnly=true의 동작:
 * - JPA: Flush 모드를 MANUAL로 설정 (Dirty Checking 비활성화)
 * - JDBC: Connection을 읽기 전용으로 설정
 * - DB: 읽기 최적화 (락 획득 안 함, 스냅샷 생성 안 함)
 *
 * 문제점:
 * - save(), update(), delete() 호출 시 동작이 DB마다 다름
 * - 일부 DB는 에러, 일부는 무시, 일부는 실행됨 (예측 불가능)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReadOnlyWriteService {

    private final MemberRepository memberRepository;

    /**
     * ❌ BAD: readOnly=true 메서드에서 save() 호출
     *
     * 예상되는 동작:
     * - H2 (테스트): 에러 안 나고 무시될 수 있음
     * - PostgreSQL: 에러 발생 가능
     * - MySQL: Dirty Checking은 안 되지만 명시적 save()는 실행될 수 있음
     */
    @Transactional(readOnly = true)
    public void readOnlyButSave() {
        log.info("=== ReadOnly Transaction with save() ===");

        Member member = new Member();
        member.setUsername("ReadOnly-Save");
        memberRepository.save(member);  // ❌ readOnly 트랜잭션에서 write 시도

        log.warn("save() called in readOnly transaction - behavior is unpredictable!");
    }

    /**
     * ❌ BAD: readOnly=true 메서드에서 Dirty Checking 시도
     *
     * readOnly=true는 Flush 모드를 MANUAL로 설정하므로
     * Dirty Checking이 작동하지 않습니다.
     */
    @Transactional(readOnly = true)
    public void readOnlyButUpdate(Long memberId) {
        log.info("=== ReadOnly Transaction with update (Dirty Checking) ===");

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        log.info("Before update: {}", member.getUsername());
        member.setUsername("Updated-In-ReadOnly");  // ❌ Dirty Checking 작동 안 함
        log.info("After update: {}", member.getUsername());

        // readOnly=true이므로 flush가 호출되지 않아 DB에 반영 안 됨
        log.warn("Entity changed but NOT persisted (Dirty Checking disabled)");
    }

    /**
     * ❌ BAD: readOnly=true 메서드에서 delete() 호출
     */
    @Transactional(readOnly = true)
    public void readOnlyButDelete(Long memberId) {
        log.info("=== ReadOnly Transaction with delete() ===");

        memberRepository.deleteById(memberId);  // ❌ readOnly 트랜잭션에서 delete 시도

        log.warn("delete() called in readOnly transaction - may fail or be ignored!");
    }

    /**
     * ✅ GOOD: 읽기 작업만 수행 (올바른 readOnly 사용)
     */
    @Transactional(readOnly = true)
    public void correctReadOnlyUsage() {
        log.info("=== Correct ReadOnly Transaction ===");

        long count = memberRepository.count();
        log.info("Total members: {}", count);

        memberRepository.findAll().forEach(member ->
                log.info("Member: {}", member.getUsername())
        );

        log.info("ReadOnly transaction used correctly for read operations only");
    }

    /**
     * ✅ GOOD: 쓰기 작업이 필요하면 readOnly=false (기본값)
     */
    @Transactional  // readOnly=false (기본값)
    public void correctWriteUsage() {
        log.info("=== Correct Write Transaction ===");

        Member member = new Member();
        member.setUsername("Correct-Write");
        memberRepository.save(member);

        log.info("Member saved successfully: {}", member.getUsername());
    }

    /**
     * 테스트 실행 메서드
     */
    public void demonstrateReadOnlyProblems() {
        log.info("\n");
        log.info("========================================");
        log.info("ReadOnly Transaction Write Problems");
        log.info("========================================");

        // 1. save() 시도
        try {
            readOnlyButSave();
        } catch (Exception e) {
            log.error("Error in readOnlyButSave: {}", e.getMessage());
        }

        // 2. 테스트 데이터 생성
        Member testMember = new Member();
        testMember.setUsername("Test-Member");
        memberRepository.save(testMember);

        // 3. update 시도 (Dirty Checking)
        try {
            readOnlyButUpdate(testMember.getId());
        } catch (Exception e) {
            log.error("Error in readOnlyButUpdate: {}", e.getMessage());
        }

        // 4. delete 시도
        try {
            readOnlyButDelete(testMember.getId());
        } catch (Exception e) {
            log.error("Error in readOnlyButDelete: {}", e.getMessage());
        }

        log.info("\n========================================");
        log.info("결론: readOnly=true에서 write 작업 금지!");
        log.info("========================================");
    }
}
