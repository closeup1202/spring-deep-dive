# TransactionTemplate 실전 학습

---

## 목차

1. [@Transactional vs TransactionTemplate — 언제 무엇을 쓰는가](#1-transactional-vs-transactiontemplate--언제-무엇을-쓰는가)
2. [프로그래밍 방식 트랜잭션 기본](#2-프로그래밍-방식-트랜잭션-기본)
3. [패턴 1: RetryTemplate + TransactionTemplate (낙관적 락 재시도)](#3-패턴-1-retrytemplate--transactiontemplate-낙관적-락-재시도)
4. [패턴 2: 트랜잭션 범위 최소화 (외부 I/O + DB 쓰기 혼재)](#4-패턴-2-트랜잭션-범위-최소화-외부-io--db-쓰기-혼재)
5. [패턴 3: setRollbackOnly — 예외 없이 롤백](#5-패턴-3-setrollbackonly--예외-없이-롤백)
6. [패턴 4: TransactionSynchronization — 커밋 후 훅](#6-패턴-4-transactionsynchronization--커밋-후-훅)
7. [패턴 5: REQUIRES_NEW와 self-invocation 문제](#7-패턴-5-requires_new와-self-invocation-문제)
8. [패턴 6: readOnly TransactionTemplate](#8-패턴-6-readonly-transactiontemplate)
9. [RetryTemplate 설정 — BackOff 전략](#9-retrytemplate-설정--backoff-전략)
10. [실무 판단 기준 정리](#10-실무-판단-기준-정리)

---

## 1. @Transactional vs TransactionTemplate — 언제 무엇을 쓰는가

### @Transactional의 동작 원리

```
호출자 → [AOP 프록시] → 실제 Service 메서드
           ↑
      트랜잭션 begin/commit/rollback을 프록시가 감싼다
```

`@Transactional`은 스프링 AOP 프록시가 메서드 **진입 시 트랜잭션을 begin**, **반환 시 commit**, **예외 시 rollback**한다.
제약은 명확하다: 트랜잭션 경계가 메서드 단위로 고정되고, 프록시 기반이라 **같은 클래스 내 self-invocation** 시 동작하지 않는다.

### TransactionTemplate의 동작 원리

```java
transactionTemplate.execute(status -> {
    // 이 람다 시작 시 트랜잭션 begin
    // 람다 종료 시 commit
    // 예외 발생 시 rollback
    return result;
});
```

`TransactionTemplate`은 `PlatformTransactionManager`를 직접 호출하는 **프로그래밍 방식**이다.
AOP 프록시와 무관하게 동작하므로:
- 메서드 일부만 트랜잭션으로 묶을 수 있다
- 같은 클래스 내에서도 독립 트랜잭션을 만들 수 있다
- 재시도 루프 안에서 호출 시 **호출마다 새 트랜잭션**을 시작한다

### 선택 기준

| 상황 | 선택 |
|------|------|
| 단순 CRUD, 트랜잭션 범위 = 메서드 전체 | `@Transactional` |
| 재시도(Retry)마다 새 트랜잭션이 필요 | `TransactionTemplate` |
| 메서드 일부만 트랜잭션으로 묶어야 함 | `TransactionTemplate` |
| self-invocation으로 `@Transactional`이 무시됨 | `TransactionTemplate` |
| 커밋 후 동작(알림, 이벤트)을 인라인으로 등록 | `TransactionTemplate` + Synchronization |
| 격리 레벨을 동적으로 변경해야 함 | `TransactionTemplate` |

---

## 2. 프로그래밍 방식 트랜잭션 기본

### TransactionTemplate 빈 등록

`PlatformTransactionManager`는 Spring Boot가 자동 등록하므로 주입만 하면 된다.

```java
@Configuration
public class TxConfig {

    // TransactionTemplate은 thread-safe → 빈으로 등록해서 공유 가능
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager tm) {
        return new TransactionTemplate(tm);
    }

    // 속성이 다르면 별도 빈으로 만든다
    @Bean
    public TransactionTemplate readOnlyTransactionTemplate(PlatformTransactionManager tm) {
        TransactionTemplate template = new TransactionTemplate(tm);
        template.setReadOnly(true);
        return template;
    }

    @Bean
    public TransactionTemplate requiresNewTransactionTemplate(PlatformTransactionManager tm) {
        TransactionTemplate template = new TransactionTemplate(tm);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
```

> 또는 `PlatformTransactionManager`만 주입받고 서비스 코드에서 `new TransactionTemplate(tm)`으로 직접 생성해도 된다.
> `TransactionTemplate` 생성 비용은 거의 없다.

### 반환값이 없을 때

```java
// execute() → T 반환
transactionTemplate.execute(status -> {
    repo.save(entity);
    return null; // void처럼 쓸 때 null 반환
});

// executeWithoutResult() → void, null 반환 코드가 없어서 더 깔끔
transactionTemplate.executeWithoutResult(status ->
    repo.save(entity)
);
```

### 설정 가능한 속성

```java
TransactionTemplate template = new TransactionTemplate(transactionManager);

template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);      // 기본값
template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);  // 항상 새 트랜잭션
template.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED); // 트랜잭션 없이 실행

template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);   // 기본값 (DB마다 다름)
template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);  // MySQL InnoDB 기본값
template.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);     // 가장 강한 격리

template.setTimeout(30);    // 30초 타임아웃 (DB 잠금 대기 포함)
template.setReadOnly(true); // 읽기 전용 최적화
```

---

## 3. 패턴 1: RetryTemplate + TransactionTemplate (낙관적 락 재시도)

### 왜 @Transactional이 동작하지 않는가

```java
// ❌ 이 방식은 재시도가 의미 없다
@Transactional
public void pay(Long id) {
    retryTemplate.execute(context -> {
        // retryTemplate이 재시도해도 이미 같은 트랜잭션 안
        // 1차 시도에서 ObjectOptimisticLockingFailureException 발생
        // → 트랜잭션이 rollback-only 마크됨
        // → 2차 시도: 롤백된 트랜잭션으로 SELECT 시도 → TransactionException 발생
        entity.pay();
        return null;
    });
}
```

```
@Transactional 사용 시 흐름:
┌──────────────────────────────────────────┐
│  트랜잭션 #1 begin                        │
│  ├─ 1차 시도: SELECT(version=1) → 실패   │
│  │   → rollback-only 마크               │
│  ├─ 2차 시도: 이미 죽은 트랜잭션으로 재시도 │
│  │   → org.springframework.transaction   │
│  │      .UnexpectedRollbackException     │
│  └─ 재시도 불가                           │
│  트랜잭션 #1 rollback                     │
└──────────────────────────────────────────┘
```

### TransactionTemplate으로 해결

```java
// ✅ 재시도마다 새 트랜잭션
public void pay(Long id) {
    retryTemplate.execute(context -> {
        return transactionTemplate.execute(status -> {
            // execute() 호출 시마다 새 트랜잭션 begin
            // DB에서 최신 version 재조회 → 정상 재시도
            entity.pay();
            return null;
        });
        // 실패 시 rollback → 다음 재시도에서 transactionTemplate.execute() 재호출
    });
}
```

```
TransactionTemplate 사용 시 흐름:
┌──────────────────┐
│ 1차 시도          │
│ 트랜잭션 #1 begin │ ← transactionTemplate.execute()
│ SELECT(version=1) │
│ UPDATE 충돌 → 실패 │
│ 트랜잭션 #1 rollback│
└──────────────────┘
       ↓ 재시도 (100ms 대기)
┌──────────────────┐
│ 2차 시도          │
│ 트랜잭션 #2 begin │ ← transactionTemplate.execute() 재호출
│ SELECT(version=2) │ ← 최신 버전 재조회
│ UPDATE 성공       │
│ 트랜잭션 #2 commit│
└──────────────────┘
```

### 실제 코드

```java
public TransferRecord transfer(Long fromId, Long toId, long amount) {
    return optimisticLockRetryTemplate.execute(context -> {
        log.info("[transfer] 시도 #{}", context.getRetryCount() + 1);

        // execute() 호출마다 새 트랜잭션 → 항상 최신 DB 상태에서 시작
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        return txTemplate.execute(status -> {
            Account from = accountRepository.findById(fromId).orElseThrow();
            Account to   = accountRepository.findById(toId).orElseThrow();

            from.withdraw(amount);
            to.deposit(amount);

            return transferRecordRepository.save(
                TransferRecord.success(fromId, toId, amount, context.getRetryCount())
            );
        });

    }, context -> {
        // 재시도 횟수 소진 후 최종 실패 recover
        throw new IllegalStateException("동시 충돌 재시도 실패", context.getLastThrowable());
    });
}
```

### 낙관적 락 동시 시나리오

```
시간  Thread A                    Thread B
────  ────────────────────────    ────────────────────────
T1    SELECT (version=1)
T2                                SELECT (version=1)
T3    UPDATE SET version=2 ✅
T4                                UPDATE SET version=2 ❌
                                  ObjectOptimisticLockingFailureException
T5                                [RetryTemplate: 100ms 대기 후 재시도]
T6                                트랜잭션 #2 begin
T7                                SELECT (version=2) ← 최신 버전 재조회
T8                                UPDATE SET version=3 ✅
```

---

## 4. 패턴 2: 트랜잭션 범위 최소화 (외부 I/O + DB 쓰기 혼재)

### 문제: @Transactional이 외부 API 호출을 감싸면

```java
@Transactional  // 트랜잭션 시작 = DB 커넥션 점유 시작
public void processPayment(Long id) {
    Account account = accountRepository.findById(id).orElseThrow();

    // 외부 PG사 API 호출 (200ms ~ 2000ms) ← 이 구간 내내 커넥션 점유!
    String pgToken = pgClient.call(account);

    account.markPaid(pgToken); // DB 쓰기
} // 여기서 커넥션 반환
```

```
커넥션 풀 크기: 10
동시 요청: 10개 × 외부 API 대기 2000ms = 20초간 커넥션 전부 점유
11번째 요청: 커넥션 대기 → 타임아웃 → 장애
```

### 해결: 외부 I/O는 트랜잭션 밖, DB 쓰기만 TransactionTemplate으로

```java
public void processPayment(Long id) {
    // 1단계: 사전 조회 (커넥션 즉시 반환, 트랜잭션 없음)
    Account account = findAccountWithoutTx(id);

    // 2단계: 외부 API 호출 (트랜잭션 없음 = 커넥션 점유 없음)
    String pgToken = pgClient.call(account); // 2000ms 대기해도 커넥션 블로킹 없음

    // 3단계: DB 쓰기만 트랜잭션으로 (수십ms)
    transactionTemplate.executeWithoutResult(status -> {
        Account fresh = accountRepository.findById(id).orElseThrow(); // 최신 상태 재조회
        fresh.markPaid(pgToken);
    });
}
```

### 커넥션 점유 시간 비교

```
@Transactional 사용:
  [커넥션 점유]────────────────────────────[반환]
  ← 사전조회 ←── 외부 API 2000ms ──→ ← DB쓰기 →

TransactionTemplate 사용:
  [커넥션]─[반환] ← 외부 API 2000ms → [커넥션]─[반환]
  ↑ 사전조회만                        ↑ DB쓰기만
  (수ms)                             (수ms)
```

> 실무에서 결제, 외부 인증, 파일 업로드, 이메일 발송이 포함된 서비스 메서드에는
> `@Transactional`을 메서드 전체에 걸지 않는 것이 원칙이다.

---

## 5. 패턴 3: setRollbackOnly — 예외 없이 롤백

### 왜 필요한가

예외를 던지면 호출 스택 전체로 전파된다.
비즈니스 규칙 위반 시 DB만 롤백하고 **호출자에게는 결과값(false 등)을 반환**하고 싶을 때 사용한다.

```java
public boolean transfer(Long fromId, Long toId, long amount) {
    Boolean result = transactionTemplate.execute(status -> {

        if (isSuspiciousTransaction(fromId, amount)) {
            log.warn("의심 거래 감지 → 롤백");
            status.setRollbackOnly(); // 예외 발생 없이 롤백 마크
            return false;            // 호출자에게 실패 여부 반환
        }

        // 정상 로직
        account.withdraw(amount);
        return true;
    });

    return Boolean.TRUE.equals(result);
}
```

```java
// 호출자 코드
boolean success = transferService.transfer(from, to, amount);
if (!success) {
    // 예외 없이 정상 흐름에서 실패 처리 가능
    log.warn("이체 차단됨");
}
```

### setRollbackOnly vs 예외 던지기

| | `setRollbackOnly()` | 예외 던지기 |
|--|--|--|
| 호출 스택 전파 | 없음 | 있음 |
| 롤백 | 보장 | 보장 (RuntimeException 기준) |
| 반환값 | 가능 | 불가 |
| 사용처 | FDS·검증 실패 시 조용히 롤백 | 복구 불가 오류 |

---

## 6. 패턴 4: TransactionSynchronization — 커밋 후 훅

### 왜 커밋 후 실행이 중요한가

```
문제 시나리오:
1. 이체 성공 → Kafka 이벤트 발행 (트랜잭션 안)
2. DB commit 전에 발행 → 이벤트 소비자가 DB 조회 → 아직 커밋 안 됨
3. 또는: Kafka 발행 후 DB rollback → 이벤트는 이미 발행됨

해결: 반드시 DB commit 완료 후에 외부 시스템을 호출해야 한다.
```

```java
transactionTemplate.executeWithoutResult(status -> {
    account.withdraw(amount);
    transferRecordRepository.save(record);

    // 커밋 완료 후 실행될 콜백 등록
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 이 블록은 DB commit 완료 후에만 실행됨
                // rollback 시에는 호출되지 않음
                kafkaProducer.send("transfer.completed", record.getId());
                slackNotifier.send("이체 완료: " + record.getId());
            }
        }
    );
});
```

> `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`와 동일한 목적이지만
> 이벤트 클래스 없이 인라인으로 처리할 수 있다.

---

## 7. 패턴 5: REQUIRES_NEW와 self-invocation 문제

### @Transactional(propagation = REQUIRES_NEW)의 함정

```java
@Service
public class OrderService {

    @Transactional
    public void placeOrder(Order order) {
        orderRepository.save(order);
        saveAuditLog("ORDER_PLACED"); // ← self-invocation!
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW) // 무시됨!
    public void saveAuditLog(String action) {
        auditLogRepository.save(AuditLog.of(action));
        // 같은 클래스 내 호출 → AOP 프록시를 거치지 않음
        // → REQUIRES_NEW 설정이 완전히 무시됨
        // → placeOrder의 트랜잭션 안에서 실행됨
    }
}
```

### TransactionTemplate으로 해결

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final PlatformTransactionManager transactionManager;

    @Transactional
    public void placeOrder(Order order) {
        orderRepository.save(order);
        saveAuditLog("ORDER_PLACED"); // 프록시 없이 직접 호출해도 동작함
    }

    public void saveAuditLog(String action) {
        // REQUIRES_NEW 속성을 직접 설정 → 프록시 불필요
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        requiresNew.executeWithoutResult(status ->
            auditLogRepository.save(AuditLog.of(action))
        );
        // 이 시점에 감사 로그 트랜잭션은 이미 커밋됨
        // placeOrder가 롤백되어도 감사 로그는 보존됨
    }
}
```

### REQUIRES_NEW 전파 흐름

```
placeOrder 트랜잭션 #1 begin
  │
  ├─ orderRepository.save(order)
  │
  ├─ saveAuditLog() 호출
  │   │
  │   └─ 트랜잭션 #2 begin (REQUIRES_NEW)
  │       auditLogRepository.save(...)
  │       트랜잭션 #2 commit  ← 독립적으로 커밋
  │
  ├─ (여기서 예외 발생)
  │
  트랜잭션 #1 rollback  ← order 저장 취소
                         ← 하지만 감사 로그 #2는 이미 커밋됨
```

> **주의**: REQUIRES_NEW는 기존 트랜잭션을 일시 중단(Suspend)하고 새 커넥션을 가져온다.
> 즉, 동시에 커넥션 2개를 사용한다. 커넥션 풀 크기를 고려해야 한다.

---

## 8. 패턴 6: readOnly TransactionTemplate

```java
TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
readOnly.setReadOnly(true);

List<Account> accounts = readOnly.execute(status ->
    accountRepository.findAll()
);
```

### readOnly = true 효과

| 영역 | 효과 |
|------|------|
| Hibernate | dirty checking 비활성화 → 스냅샷 저장 생략 → 메모리 절감 |
| Hibernate | 플러시 모드 `MANUAL` → 자동 flush 차단 → INSERT/UPDATE 방지 |
| MySQL InnoDB | 읽기 전용 트랜잭션으로 처리 → 언두 로그 최소화 |
| Spring DataSource Router | read replica 라우팅 가능 (별도 설정 필요) |

### @Transactional(readOnly = true)와 동일한 효과

```java
// 이 둘은 동일한 동작
@Transactional(readOnly = true)
public List<Account> findAll() { ... }

// ↕ 동일

public List<Account> findAll() {
    TransactionTemplate readOnly = new TransactionTemplate(tm);
    readOnly.setReadOnly(true);
    return readOnly.execute(status -> accountRepository.findAll());
}
```

> **실무 팁**: 배치성 대량 조회나 통계 쿼리에는 반드시 readOnly 트랜잭션을 사용한다.
> dirty checking이 비활성화되므로 조회 행 수에 비례하는 메모리 낭비가 없다.

---

## 9. RetryTemplate 설정 — BackOff 전략

### FixedBackOff vs ExponentialBackOff

```java
// ❌ FixedBackOff: 모든 스레드가 같은 시간 후 동시에 재시도
FixedBackOffPolicy fixed = new FixedBackOffPolicy();
fixed.setBackOffPeriod(100); // 항상 100ms 후 재시도
// 10개 스레드가 동시에 실패 → 100ms 후 10개 동시 재시도 → 또 충돌 → 반복
```

```java
// ✅ ExponentialBackOff: 재시도 간격이 점점 늘어남 → thundering herd 방지
ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
backOff.setInitialInterval(100);   // 1차 재시도: 100ms 후
backOff.setMultiplier(2.0);        // 2차: 200ms, 3차: 400ms
backOff.setMaxInterval(1_000);     // 최대 1000ms를 넘지 않음

// 10개 스레드가 동시에 실패해도 재시도 타이밍이 분산됨
```

### 재시도 대상 예외 선별

```java
Map<Class<? extends Throwable>, Boolean> retryable = new HashMap<>();
retryable.put(ObjectOptimisticLockingFailureException.class, true); // 재시도
retryable.put(InsufficientBalanceException.class, false);           // 재시도 안 함 (비즈니스 오류)
retryable.put(IllegalArgumentException.class, false);               // 재시도 안 함 (입력 오류)

SimpleRetryPolicy policy = new SimpleRetryPolicy(3, retryable);
```

> 재시도 대상은 **일시적 경쟁 조건**으로 인한 예외만 포함해야 한다.
> 잔액 부족, 유효성 오류 같은 비즈니스 예외를 재시도하면 무한 실패 반복이다.

### Recover 콜백 — 재시도 소진 후 처리

```java
retryTemplate.execute(context -> {
    return transactionTemplate.execute(status -> {
        // 메인 로직
    });
}, context -> {
    // 재시도 횟수 소진 후 실행되는 recover 메서드
    Throwable cause = context.getLastThrowable();
    log.error("최종 실패: {}", cause.getMessage());

    // 실패 이력 기록, 보상 트랜잭션 실행, 알림 전송 등
    return fallbackResult;
});
```

---

## 10. 실무 판단 기준 정리

### 언제 TransactionTemplate을 쓰는가

```
1. Retry 안에 트랜잭션이 있어야 할 때 (가장 대표적)
   → @Transactional + Retry = 재시도 무의미
   → TransactionTemplate + Retry = 재시도마다 새 트랜잭션

2. 메서드 중간에 외부 I/O(HTTP, 파일, 메시지큐)가 있을 때
   → @Transactional 전체에 걸면 커넥션 점유 폭발
   → TransactionTemplate으로 DB 쓰기 구간만 감쌈

3. 같은 클래스에서 REQUIRES_NEW 트랜잭션이 필요할 때
   → self-invocation → @Transactional 무시
   → TransactionTemplate에 setPropagationBehavior()

4. 커밋 성공 후 외부 시스템 연동 (알림, 이벤트 발행)
   → TransactionSynchronization.afterCommit()

5. 격리 레벨을 요청마다 동적으로 바꿔야 할 때
   → setIsolationLevel()로 런타임에 결정
```

### 안티패턴

```java
// ❌ @Transactional과 함께 RetryTemplate 사용
@Transactional
public void pay(Long id) {
    retryTemplate.execute(context -> {
        entity.pay(); // 재시도해도 같은 죽은 트랜잭션
        return null;
    });
}

// ❌ @Transactional이 외부 I/O를 감쌈
@Transactional
public void sendEmail(Long userId) {
    User user = userRepository.findById(userId).orElseThrow();
    emailClient.send(user.getEmail()); // 외부 API 수초 대기 → 커넥션 점유
    user.markEmailSent();
}

// ❌ self-invocation으로 REQUIRES_NEW 무시
@Transactional
public void placeOrder(Order order) {
    orderRepository.save(order);
    this.saveAuditLog(); // 프록시 우회 → REQUIRES_NEW 무시됨
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void saveAuditLog() { ... }
```

### 체크리스트

| 체크 항목 | 이유 |
|-----------|------|
| Retry 루프 내부에 `@Transactional` 메서드를 호출하고 있지 않은가? | 재시도 불가 |
| `@Transactional` 메서드 안에서 외부 API·파일·메시지큐를 호출하고 있지 않은가? | 커넥션 점유 |
| 같은 클래스에서 `REQUIRES_NEW` 메서드를 직접 호출하고 있지 않은가? | 프록시 우회 |
| 커밋 전에 Kafka/Slack 등 외부 시스템을 호출하고 있지 않은가? | 롤백 시 메시지 회수 불가 |
| 재시도 대상 예외가 비즈니스 오류를 포함하고 있지 않은가? | 무한 실패 반복 |

---

## 참고: 실습 코드 구조

```
transactiontemplate/
├── src/main/java/com/exam/txtemplate/
│   ├── config/
│   │   └── RetryConfig.java         ← ExponentialBackOff 설정
│   ├── domain/
│   │   ├── Account.java             ← @Version 낙관적 락
│   │   ├── TransferRecord.java      ← 이체 이력
│   │   └── AuditLog.java            ← 감사 로그 (REQUIRES_NEW 대상)
│   ├── exception/
│   │   └── InsufficientBalanceException.java
│   ├── repository/
│   │   ├── AccountRepository.java
│   │   ├── TransferRecordRepository.java
│   │   └── AuditLogRepository.java
│   └── service/
│       └── AccountService.java      ← 6가지 패턴 구현
└── src/test/java/com/exam/txtemplate/
    └── AccountServiceTest.java      ← 동시성 테스트 포함
```

| 테스트 | 검증 내용 |
|--------|-----------|
| `transfer_success` | 정상 이체, 잔액·이력 원자적 처리 |
| `transfer_insufficientBalance_rollback` | 예외 시 완전 롤백 |
| `transfer_suspicious_setRollbackOnly` | setRollbackOnly, 예외 없이 롤백 |
| `auditLog_survivesMainTransactionRollback` | REQUIRES_NEW 독립 커밋 |
| `getTotalBalance_readOnly` | readOnly 트랜잭션 조회 |
| `concurrentTransfer_optimisticLockRetry` | 10개 동시 요청, 재시도 후 잔액 정합성 |
| `nestedTransactionTemplate_requiresNew` | 중첩 트랜잭션, 바깥 롤백 시 안쪽 보존 |
