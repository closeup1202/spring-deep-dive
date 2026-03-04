package com.exam.txtemplate.service;

import com.exam.txtemplate.domain.Account;
import com.exam.txtemplate.domain.AuditLog;
import com.exam.txtemplate.domain.TransferRecord;
import com.exam.txtemplate.exception.InsufficientBalanceException;
import com.exam.txtemplate.repository.AccountRepository;
import com.exam.txtemplate.repository.AuditLogRepository;
import com.exam.txtemplate.repository.TransferRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * TransactionTemplate 실전 패턴 모음.
 *
 * 핵심 원칙:
 *   @Transactional  → 메서드 전체에 트랜잭션 경계가 고정됨 (AOP 프록시)
 *   TransactionTemplate → 코드에서 직접 트랜잭션 범위를 지정 (programmatic)
 *
 * TransactionTemplate을 선택하는 3가지 실전 이유:
 *   1. 재시도(Retry)마다 새 트랜잭션이 필요할 때
 *   2. 메서드 일부만 트랜잭션으로 묶어야 할 때 (외부 I/O + DB 쓰기 혼재)
 *   3. 같은 클래스 내에서 self-invocation 문제를 피해야 할 때
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository       accountRepository;
    private final TransferRecordRepository transferRecordRepository;
    private final AuditLogRepository      auditLogRepository;
    private final RetryTemplate           optimisticLockRetryTemplate;
    private final PlatformTransactionManager transactionManager;

    // ----------------------------------------------------------------
    // 패턴 1: RetryTemplate + TransactionTemplate
    //
    // 왜 @Transactional이 안 되는가?
    //   @Transactional은 메서드 진입 시 트랜잭션을 1개 생성한다.
    //   RetryTemplate이 재시도해도 같은 트랜잭션을 재사용하므로
    //   이미 롤백 마크된 트랜잭션으로 재시도 → 의미 없음.
    //
    //   TransactionTemplate은 execute() 호출마다 새 트랜잭션을 시작한다.
    //   재시도마다 DB에서 최신 버전을 다시 SELECT → 정상 재시도 가능.
    // ----------------------------------------------------------------
    public TransferRecord transfer(Long fromId, Long toId, long amount) {
        return optimisticLockRetryTemplate.execute(context -> {
            int attempt = context.getRetryCount() + 1;
            log.info("[transfer] 시도 #{}: fromId={}, toId={}, amount={}", attempt, fromId, toId, amount);

            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

            return txTemplate.execute(status -> {
                Account from = accountRepository.findById(fromId)
                    .orElseThrow(() -> new IllegalArgumentException("출금 계좌 없음: " + fromId));
                Account to = accountRepository.findById(toId)
                    .orElseThrow(() -> new IllegalArgumentException("입금 계좌 없음: " + toId));

                from.withdraw(amount); // 잔액 부족 시 InsufficientBalanceException
                to.deposit(amount);

                TransferRecord record = TransferRecord.success(fromId, toId, amount, context.getRetryCount());
                transferRecordRepository.save(record);

                // 커밋 후 슬랙/카카오 알림 전송 (패턴 4 참고)
                registerPostCommitHook(record);

                return record;
            });
        }, context -> {
            // 재시도 소진 후 최종 실패 콜백 (recover)
            Throwable lastEx = context.getLastThrowable();
            log.error("[transfer] 최종 실패: fromId={}, toId={}, 원인={}", fromId, toId, lastEx.getMessage());

            if (lastEx instanceof ObjectOptimisticLockingFailureException) {
                throw new IllegalStateException("동시 수정 충돌로 이체 실패. 잠시 후 재시도 해주세요.", lastEx);
            }
            throw new RuntimeException(lastEx);
        });
    }

    // ----------------------------------------------------------------
    // 패턴 2: 트랜잭션 범위 최소화 (외부 I/O + DB 쓰기 혼재)
    //
    // 외부 결제 API 호출(latency: 200~2000ms)이 포함된 로직에서
    // @Transactional을 메서드 전체에 걸면:
    //   → 외부 API 대기 시간 내내 DB 커넥션을 점유
    //   → 커넥션 풀 고갈 → 전체 서비스 장애
    //
    // 해결: TransactionTemplate으로 실제 DB 쓰기 구간만 트랜잭션으로 감싼다.
    // ----------------------------------------------------------------
    public TransferRecord transferWithExternalApiCall(Long fromId, Long toId, long amount) {
        // 1단계: 트랜잭션 없이 사전 검증 (SELECT only, 커넥션 점유 X)
        Account from = accountRepository.findById(fromId)
            .orElseThrow(() -> new IllegalArgumentException("출금 계좌 없음: " + fromId));

        if (from.getBalance() < amount) {
            throw new InsufficientBalanceException(
                "잔액 부족: 현재=%d, 요청=%d".formatted(from.getBalance(), amount));
        }

        // 2단계: 외부 PG사 API 호출 (트랜잭션 밖, DB 커넥션 점유 없음)
        String pgToken = callExternalPaymentGateway(fromId, toId, amount);

        // 3단계: 외부 API 성공 후에만 트랜잭션 시작 → DB 쓰기만 트랜잭션으로
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        return txTemplate.execute(status -> {
            // 최신 상태 재조회 (외부 API 호출 사이에 잔액이 바뀔 수 있음)
            Account freshFrom = accountRepository.findById(fromId).orElseThrow();
            Account freshTo   = accountRepository.findById(toId).orElseThrow();

            freshFrom.withdraw(amount);
            freshTo.deposit(amount);

            return transferRecordRepository.save(
                TransferRecord.success(fromId, toId, amount, 0)
            );
        });
    }

    // ----------------------------------------------------------------
    // 패턴 3: setRollbackOnly — 예외 없이 롤백 결정
    //
    // 비즈니스 규칙 위반 시 예외를 던지지 않고 롤백만 마크한 뒤
    // 호출자에게 false 등 결과값을 반환하고 싶을 때 사용.
    // (예외를 던지면 호출 스택 전체에 영향 → 선택적으로 처리 불가)
    // ----------------------------------------------------------------
    public boolean transferWithSuspiciousCheck(Long fromId, Long toId, long amount) {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        Boolean committed = txTemplate.execute(status -> {
            Account from = accountRepository.findById(fromId).orElseThrow();
            Account to   = accountRepository.findById(toId).orElseThrow();

            // 의심 거래 감지 (FDS 로직 가정): 1회 이체 한도 초과
            if (amount > 5_000_000L) {
                log.warn("[FDS] 고액 이체 감지: fromId={}, amount={} → 롤백", fromId, amount);
                status.setRollbackOnly(); // 예외 없이 롤백 마크
                return false;            // 호출자에게 실패 여부 반환 가능
            }

            from.withdraw(amount);
            to.deposit(amount);
            transferRecordRepository.save(TransferRecord.success(fromId, toId, amount, 0));
            return true;
        });

        return Boolean.TRUE.equals(committed);
    }

    // ----------------------------------------------------------------
    // 패턴 4: TransactionSynchronization — 커밋 후 훅 등록
    //
    // "DB 커밋이 완료된 후에" 외부 시스템(알림, 이벤트 발행)을 호출해야 할 때.
    //   → 커밋 전에 호출하면 DB 롤백 시 이미 보낸 알림은 회수 불가.
    //   → @TransactionalEventListener(phase = AFTER_COMMIT) 과 같은 목적이지만
    //     이벤트 클래스 없이 인라인으로 처리할 수 있다.
    // ----------------------------------------------------------------
    private void registerPostCommitHook(TransferRecord record) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 트랜잭션 커밋 완료 후 실행 — DB 롤백 시 이 블록은 호출되지 않음
                log.info("[afterCommit] 이체 완료 알림 전송: recordId={}", record.getId());
                // notificationService.sendTransferAlert(record);
            }
        });
    }

    // ----------------------------------------------------------------
    // 패턴 5: REQUIRES_NEW 상당 — 독립 트랜잭션으로 감사 로그 저장
    //
    // 메인 트랜잭션이 롤백되더라도 감사 로그는 반드시 저장해야 하는 경우.
    // @Transactional(propagation = REQUIRES_NEW)는 같은 클래스 내
    // self-invocation 시 AOP 프록시를 우회하므로 동작하지 않는다.
    // TransactionTemplate에 PROPAGATION_REQUIRES_NEW를 설정하면
    // self-invocation 없이 동일 클래스 내에서도 독립 트랜잭션 보장.
    // ----------------------------------------------------------------
    public void saveAuditInNewTransaction(String action, String detail, String result) {
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        requiresNew.executeWithoutResult(status ->
            auditLogRepository.save(AuditLog.of(action, detail, result))
        );
    }

    // ----------------------------------------------------------------
    // 패턴 6: readOnly TransactionTemplate — 조회 성능 최적화
    //
    // readOnly = true 효과:
    //   1. Hibernate: dirty checking(변경 감지) 비활성화 → 스냅샷 저장 생략 → 메모리/CPU 절감
    //   2. MySQL InnoDB: 읽기 전용 트랜잭션으로 처리 → 잠금 오버헤드 감소
    //   3. 일부 DB 드라이버: read replica로 라우팅 가능
    // ----------------------------------------------------------------
    public long getTotalBalance(Long fromId, Long toId) {
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);

        return readOnly.execute(status -> {
            Account from = accountRepository.findById(fromId).orElseThrow();
            Account to   = accountRepository.findById(toId).orElseThrow();
            return from.getBalance() + to.getBalance();
        });
    }

    /** 외부 PG사 API 호출 시뮬레이션 */
    private String callExternalPaymentGateway(Long fromId, Long toId, long amount) {
        log.info("[PG] 외부 결제 API 호출 중... fromId={}, amount={}", fromId, amount);
        // 실제로는 RestClient / WebClient 등으로 외부 호출
        return "pg-token-" + System.currentTimeMillis();
    }
}
