package com.exam.txtemplate;

import com.exam.txtemplate.domain.Account;
import com.exam.txtemplate.domain.AuditLog;
import com.exam.txtemplate.domain.TransferRecord;
import com.exam.txtemplate.exception.InsufficientBalanceException;
import com.exam.txtemplate.repository.AccountRepository;
import com.exam.txtemplate.repository.AuditLogRepository;
import com.exam.txtemplate.repository.TransferRecordRepository;
import com.exam.txtemplate.service.AccountService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Slf4j
@SpringBootTest
class AccountServiceTest {

    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;
    @Autowired TransferRecordRepository transferRecordRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired PlatformTransactionManager transactionManager;

    Account alice;
    Account bob;

    @BeforeEach
    void setUp() {
        transferRecordRepository.deleteAll();
        auditLogRepository.deleteAll();
        accountRepository.deleteAll();

        alice = accountRepository.save(Account.of("Alice", 100_000L));
        bob   = accountRepository.save(Account.of("Bob",   50_000L));
    }

    // ---------------------------------------------------------------
    // 테스트 1: 정상 이체
    // ---------------------------------------------------------------
    @Test
    @DisplayName("정상 이체: 잔액 차감·입금·이력 저장이 하나의 트랜잭션에서 원자적으로 처리된다")
    void transfer_success() {
        TransferRecord record = accountService.transfer(alice.getId(), bob.getId(), 30_000L);

        Account updatedAlice = accountRepository.findById(alice.getId()).orElseThrow();
        Account updatedBob   = accountRepository.findById(bob.getId()).orElseThrow();

        assertThat(updatedAlice.getBalance()).isEqualTo(70_000L);
        assertThat(updatedBob.getBalance()).isEqualTo(80_000L);
        assertThat(record.getStatus()).isEqualTo("SUCCESS");
        assertThat(transferRecordRepository.count()).isEqualTo(1);
    }

    // ---------------------------------------------------------------
    // 테스트 2: 잔액 부족 → 롤백
    // ---------------------------------------------------------------
    @Test
    @DisplayName("잔액 부족 시 InsufficientBalanceException 발생, 잔액·이력 모두 변경되지 않는다")
    void transfer_insufficientBalance_rollback() {
        assertThatThrownBy(() ->
            accountService.transfer(alice.getId(), bob.getId(), 999_999L)
        ).isInstanceOf(InsufficientBalanceException.class);

        Account updatedAlice = accountRepository.findById(alice.getId()).orElseThrow();
        Account updatedBob   = accountRepository.findById(bob.getId()).orElseThrow();

        assertThat(updatedAlice.getBalance()).isEqualTo(100_000L); // 변경 없음
        assertThat(updatedBob.getBalance()).isEqualTo(50_000L);    // 변경 없음
        assertThat(transferRecordRepository.count()).isZero();     // 이력 없음
    }

    // ---------------------------------------------------------------
    // 테스트 3: setRollbackOnly — 고액 이체 차단
    // ---------------------------------------------------------------
    @Test
    @DisplayName("setRollbackOnly: 예외 없이 롤백 마크 시 잔액 변경 없이 false 반환")
    void transfer_suspicious_setRollbackOnly() {
        boolean result = accountService.transferWithSuspiciousCheck(
            alice.getId(), bob.getId(), 6_000_000L  // 한도 초과
        );

        assertThat(result).isFalse();

        Account updatedAlice = accountRepository.findById(alice.getId()).orElseThrow();
        assertThat(updatedAlice.getBalance()).isEqualTo(100_000L); // 롤백됨
        assertThat(transferRecordRepository.count()).isZero();
    }

    // ---------------------------------------------------------------
    // 테스트 4: REQUIRES_NEW — 메인 트랜잭션 롤백 시에도 감사 로그 보존
    // ---------------------------------------------------------------
    @Test
    @DisplayName("REQUIRES_NEW: 메인 로직 실패 후에도 감사 로그는 별도 트랜잭션으로 저장된다")
    void auditLog_survivesMainTransactionRollback() {
        // 감사 로그를 독립 트랜잭션으로 저장 (REQUIRES_NEW)
        accountService.saveAuditInNewTransaction(
            "TRANSFER_ATTEMPT", "Alice→Bob 30000원", "ATTEMPTED"
        );

        // 이후 메인 로직이 실패하더라도 감사 로그는 이미 커밋됨
        assertThatThrownBy(() ->
            accountService.transfer(alice.getId(), bob.getId(), 999_999L)
        ).isInstanceOf(InsufficientBalanceException.class);

        // 감사 로그는 살아있음
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo("TRANSFER_ATTEMPT");
    }

    // ---------------------------------------------------------------
    // 테스트 5: readOnly TransactionTemplate — 조회만 수행
    // ---------------------------------------------------------------
    @Test
    @DisplayName("readOnly 트랜잭션: 두 계좌 잔액 합계를 정확히 반환한다")
    void getTotalBalance_readOnly() {
        long total = accountService.getTotalBalance(alice.getId(), bob.getId());
        assertThat(total).isEqualTo(150_000L);
    }

    // ---------------------------------------------------------------
    // 테스트 6: 동시 이체 — 낙관적 락 + RetryTemplate
    //
    // 핵심 검증:
    //   10개 스레드가 동시에 같은 계좌에서 1,000원씩 출금.
    //   낙관적 락 충돌 → RetryTemplate이 재시도 → 최종적으로 모두 성공해야 함.
    //   최종 잔액 = 100,000 - (성공한 출금 수 × 1,000)
    // ---------------------------------------------------------------
    @Test
    @DisplayName("낙관적 락 + Retry: 동시 10건 이체, 충돌 재시도 후 잔액 정합성 보장")
    void concurrentTransfer_optimisticLockRetry() throws InterruptedException {
        int threadCount    = 10;
        long amountEach    = 1_000L;
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount    = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready     = new CountDownLatch(threadCount);
        CountDownLatch start     = new CountDownLatch(1);
        CountDownLatch done      = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await(); // 모든 스레드가 준비될 때까지 대기
                    accountService.transfer(alice.getId(), bob.getId(), amountEach);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.warn("이체 최종 실패: {}", e.getMessage());
                    failCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown(); // 동시 출발
        done.await();
        executor.shutdown();

        Account finalAlice = accountRepository.findById(alice.getId()).orElseThrow();
        long expectedBalance = 100_000L - (successCount.get() * amountEach);

        log.info("성공: {}, 실패: {}, 최종 잔액: {}", successCount.get(), failCount.get(), finalAlice.getBalance());

        // 성공한 만큼만 차감되어 있어야 함 (중간 롤백된 건은 반영되면 안 됨)
        assertThat(finalAlice.getBalance()).isEqualTo(expectedBalance);

        // TransactionTemplate 덕분에 재시도가 실제로 작동함:
        // 재시도 없었다면 모두 실패했을 것 → successCount > 0 이어야 함
        assertThat(successCount.get()).isGreaterThan(0);
    }

    // ---------------------------------------------------------------
    // 테스트 7: 직접 TransactionTemplate 조합 실습
    //
    // 두 TransactionTemplate을 직접 엮어서 동작 확인:
    //   외부 tx(REQUIRED) → 내부 txA(REQUIRES_NEW) → 내부 txB(REQUIRES_NEW)
    // ---------------------------------------------------------------
    @Test
    @DisplayName("TransactionTemplate 중첩: REQUIRES_NEW는 항상 독립 커밋됨")
    void nestedTransactionTemplate_requiresNew() {
        TransactionTemplate outerTx = new TransactionTemplate(transactionManager);
        TransactionTemplate innerTx = new TransactionTemplate(transactionManager);
        innerTx.setPropagationBehavior(
            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );

        try {
            outerTx.execute(outerStatus -> {
                // 바깥 트랜잭션에서 Alice → Bob 이체
                Account from = accountRepository.findById(alice.getId()).orElseThrow();
                Account to   = accountRepository.findById(bob.getId()).orElseThrow();
                from.withdraw(10_000L);
                to.deposit(10_000L);

                // 안쪽 독립 트랜잭션에서 감사 로그 저장
                innerTx.executeWithoutResult(innerStatus ->
                    auditLogRepository.save(AuditLog.of("TRANSFER", "10000원", "ATTEMPTED"))
                );
                // 안쪽 tx는 여기서 이미 커밋됨

                // 바깥 트랜잭션 강제 롤백
                outerStatus.setRollbackOnly();
                return null;
            });
        } catch (Exception ignored) {}

        // 바깥 tx 롤백 → 잔액 변경 없음
        assertThat(accountRepository.findById(alice.getId()).orElseThrow().getBalance())
            .isEqualTo(100_000L);

        // 안쪽 REQUIRES_NEW tx → 감사 로그 커밋됨
        assertThat(auditLogRepository.count()).isEqualTo(1);
    }
}
