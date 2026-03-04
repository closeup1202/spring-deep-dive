# TransactionTemplate과 RetryTemplate을 함께 사용하는 이유

## 핵심 질문
> 왜 `@Transactional` 대신 `TransactionTemplate`을 사용하는가?

## 답변
**재시도마다 새로운 트랜잭션을 생성하기 위해서입니다.**

---

## 문제 상황 비교

### ❌ `@Transactional`을 사용하면?

```java
@Transactional  // 트랜잭션이 pay() 메서드 시작 시 한 번만 생성됨
public void pay(Long rentBillId) {
    retryTemplate.execute(context -> {
        // 재시도해도 같은 트랜잭션 재사용
        RentBill rentBill = rentBillRepository.findById(rentBillId)
                .orElseThrow(RentBillNotFoundException::new);

        rentBill.pay(); // ObjectOptimisticLockingFailureException 발생
        return null;
    });
}
```

#### 문제점
1. `pay()` 메서드 진입 시 **트랜잭션이 1개 생성**됨
2. RetryTemplate이 재시도해도 **같은 트랜잭션 내에서 반복**
3. Optimistic Lock 실패 시 **이미 롤백된 트랜잭션을 재사용**하려고 시도
4. **재시도가 의미 없어짐** (같은 실패한 트랜잭션 컨텍스트)

---

### ✅ `TransactionTemplate`을 사용하면?

```java
public void pay(Long rentBillId) {
    retryTemplate.execute(context -> {
        // 재시도마다 새로운 트랜잭션 생성!
        return transactionTemplate.execute(status -> {
            RentBill rentBill = rentBillRepository.findById(rentBillId)
                    .orElseThrow(RentBillNotFoundException::new);

            rentBill.pay();
            return null;
        });
    });
}
```

#### 동작 흐름
1. **1차 시도**: TransactionTemplate → 새 트랜잭션 생성 → 실패 → 롤백
2. **2차 시도**: TransactionTemplate → **새 트랜잭션 생성** → 실패 → 롤백
3. **3차 시도**: TransactionTemplate → **새 트랜잭션 생성** → 성공 → 커밋

#### 장점
- 매번 **깨끗한 트랜잭션 상태**에서 재시도
- Optimistic Lock 재시도가 **정상 동작**
- 트랜잭션 경계를 **코드로 명시적 제어**

---

## 실제 시나리오: Optimistic Lock 동시성 상황

```
시간 | Thread A                    | Thread B
-----|----------------------------|---------------------------
T1   | SELECT (version=1)         |
T2   |                            | SELECT (version=1)
T3   | UPDATE (version=2) ✅      |
T4   |                            | UPDATE (version=2) ❌ 실패!
T5   |                            | [재시도] SELECT (version=2)
T6   |                            | UPDATE (version=3) ✅ 성공!
```

- **TransactionTemplate**: T5에서 새 트랜잭션으로 최신 데이터(version=2) 조회 가능
- **@Transactional**: T4에서 실패한 트랜잭션이 롤백되어 재시도 불가능

---

## 추가 이점

### 1. 세밀한 트랜잭션 제어

```java
public void pay(Long rentBillId) {
    retryTemplate.execute(context -> {
        // 재시도 로직
        log.info("재시도 횟수: {}", context.getRetryCount());

        return transactionTemplate.execute(status -> {
            // 트랜잭션 로직
            if (someCondition) {
                status.setRollbackOnly(); // 수동 롤백 가능
            }
            return null;
        });
    });
}
```

### 2. 트랜잭션 격리 레벨 동적 조정

```java
transactionTemplate.setIsolationLevel(
    TransactionDefinition.ISOLATION_SERIALIZABLE
);
```

### 3. 디버깅 용이성

- 코드로 명시되어 있어 트랜잭션 경계 파악 쉬움
- 로그 추가로 각 재시도의 트랜잭션 추적 가능

---

## 결론

### `@Transactional`을 사용하지 않은 이유

> 재시도마다 **새로운 트랜잭션**을 생성해야 Optimistic Lock 재시도가 제대로 동작하기 때문입니다.

만약 `@Transactional`을 사용하면 외부에 하나의 트랜잭션만 생성되어, 재시도 로직이 의미가 없어집니다.

`TransactionTemplate`을 `RetryTemplate` 내부에서 호출해야 **재시도마다 독립적인 트랜잭션**을 보장할 수 있습니다.

---

## 실제 구현 예시

### RetryConfig.java

```java
@Configuration
public class RetryConfig {

    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // 재시도 정책 설정
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(ObjectOptimisticLockingFailureException.class, true);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions);
        retryTemplate.setRetryPolicy(retryPolicy);

        // 백오프 정책 설정 (재시도 간격)
        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(100); // 100ms 대기
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }
}
```

### RentBillService.java

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class RentBillService {

    private final RentBillRepository rentBillRepository;
    private final RetryTemplate retryTemplate;
    private final TransactionTemplate transactionTemplate;

    public void pay(Long rentBillId) {
        retryTemplate.execute(context -> {
            log.info("청구서 ID: {}, 재시도 횟수: {}", rentBillId, context.getRetryCount());

            return transactionTemplate.execute(status -> {
                RentBill rentBill = rentBillRepository.findById(rentBillId)
                        .orElseThrow(RentBillNotFoundException::new);

                if (rentBill.getStatus() == RentBillStatus.PAID) {
                    throw new AlreadyPaidException();
                }

                rentBill.pay();
                return null;
            });
        });
    }
}
```

---

## 관련 참고 자료

- [Spring Retry Documentation](https://github.com/spring-projects/spring-retry)
- [Spring Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)
- [Optimistic Locking in JPA](https://www.baeldung.com/jpa-optimistic-locking)
