# Spring Transaction Management

스프링의 선언적 트랜잭션(`@Transactional`) 동작 원리와 롤백 규칙을 학습합니다.

## 1. 트랜잭션 롤백 규칙 (Rollback Rules)
스프링은 기본적으로 **Unchecked Exception**에 대해서만 롤백을 수행합니다.

*   **Unchecked Exception (RuntimeException, Error)**:
    *   예: `NullPointerException`, `IllegalArgumentException`
    *   **동작**: 자동 **Rollback**
*   **Checked Exception (Exception)**:
    *   예: `IOException`, `SQLException` (JDBC 레벨이 아닌 비즈니스 로직상)
    *   **동작**: 자동 **Commit** (롤백되지 않음)

### 왜 Checked Exception은 롤백하지 않을까?
스프링은 Checked Exception을 "비즈니스적으로 의미 있는 예외(복구 가능한 예외)"로 간주합니다. 예를 들어, '잔액 부족' 예외가 발생했을 때 트랜잭션을 롤백하기보다는, 실패 이력을 남기고 사용자에게 알림을 보내는 등의 후처리를 커밋해야 할 수도 있기 때문입니다.

### 강제 롤백 설정
Checked Exception 발생 시에도 롤백하고 싶다면 `rollbackFor` 옵션을 사용합니다.
```java
@Transactional(rollbackFor = Exception.class)
```

## 2. 트랜잭션 전파 (Propagation)
트랜잭션이 이미 진행 중일 때, 새로운 트랜잭션을 어떻게 처리할지 결정합니다.

*   **REQUIRED (기본값)**:
    *   이미 트랜잭션이 있으면 참여하고, 없으면 새로 만듭니다.
    *   하나라도 실패하면 전체가 롤백됩니다.
*   **REQUIRES_NEW**:
    *   항상 새로운 트랜잭션을 만듭니다.
    *   기존 트랜잭션은 잠시 중단(Suspend)됩니다.
    *   내부 트랜잭션이 롤백되어도 외부 트랜잭션에는 영향을 주지 않습니다 (단, 예외 처리를 잘 해야 함).

## 3. 트랜잭션 격리 수준 (Isolation Level)

여러 트랜잭션이 동시에 실행될 때 데이터를 어느 수준까지 격리할지 정의합니다.

### 격리 수준별 문제 발생 여부

| 격리 수준 | Dirty Read | Non-Repeatable Read | Phantom Read | 비고 |
|---------|-----------|-------------------|-------------|------|
| `READ_UNCOMMITTED` | ❌ 발생 | ❌ 발생 | ❌ 발생 | 사실상 미사용 |
| `READ_COMMITTED` | ✅ 방지 | ❌ 발생 | ❌ 발생 | Oracle/PostgreSQL 기본값 |
| `REPEATABLE_READ` | ✅ 방지 | ✅ 방지 | ❌ 발생 | **MySQL 기본값** |
| `SERIALIZABLE` | ✅ 방지 | ✅ 방지 | ✅ 방지 | 성능 최저, 완전 직렬화 |

### 이상 현상 정의

```
Dirty Read        : 아직 커밋 안 된 데이터를 읽음
Non-Repeatable Read: 같은 트랜잭션에서 같은 행을 두 번 읽었는데 값이 다름
Phantom Read      : 같은 조건 조회 시 처음엔 없던 행이 두 번째 조회에서 나타남
```

### Spring에서 설정

```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public void readData() { ... }

// 기본값: Isolation.DEFAULT → DB의 기본 격리 수준 사용
// 실무에서는 거의 기본값 사용, 필요 시 명시
```

---

## 3-1. 이상 현상 상세 — 다이어그램으로 이해하기

### Dirty Read

**정의**: 아직 커밋되지 않은 다른 트랜잭션의 변경 데이터를 읽는 현상.

```
시간  Transaction A                      Transaction B
────  ─────────────────────────────     ──────────────────────────────
T1                                       BEGIN
T2                                       UPDATE account SET balance = 9000
                                         WHERE id = 1
                                         -- 아직 COMMIT 안 함 (잠정 변경)
T3    BEGIN
T4    SELECT balance FROM account
      WHERE id = 1
      → 9000  ◀── Dirty Read!
              커밋되지 않은 값을 읽음
T5    IF balance >= 5000 THEN
        출금 처리...  ← 잘못된 판단 근거
T6                                       ROLLBACK
                                         -- balance는 다시 1000으로
T7    이미 출금 처리됨 → 데이터 불일치!
```

**발생 격리 수준**: `READ_UNCOMMITTED` 에서만 발생

**방지 격리 수준**: `READ_COMMITTED` 이상

**방지 원리**:
```
Lock-based:
  B가 행을 수정할 때 Exclusive Lock 획득
  A가 SELECT 시도 → Shared Lock 대기
  → B가 COMMIT 또는 ROLLBACK 후 Lock 해제 시에만 A가 읽을 수 있음
  → 커밋된 데이터만 읽게 됨

MVCC(MySQL InnoDB, PostgreSQL):
  B의 변경은 새 버전(uncommitted)으로 저장
  A의 SELECT는 B의 트랜잭션 시작 전 마지막 커밋된 버전을 읽음
  → Lock 없이도 커밋된 데이터만 읽음 (읽기 성능 향상)
```

---

### Non-Repeatable Read

**정의**: 같은 트랜잭션 안에서 같은 행을 두 번 읽었을 때 값이 달라지는 현상.
다른 트랜잭션이 그 사이에 해당 행을 수정 후 커밋했기 때문에 발생.

```
시간  Transaction A                      Transaction B
────  ─────────────────────────────     ──────────────────────────────
T1    BEGIN
T2    SELECT balance FROM account
      WHERE id = 1
      → 1000  (첫 번째 읽기)
T3                                       BEGIN
T4                                       UPDATE account SET balance = 9000
                                         WHERE id = 1
T5                                       COMMIT
T6    SELECT balance FROM account
      WHERE id = 1
      → 9000  (두 번째 읽기) ◀── Non-Repeatable Read!
              같은 쿼리인데 다른 값
T7    첫 읽기(1000)와 두 번째(9000) 기준으로
      다른 로직 실행 → 불일치
```

**발생 격리 수준**: `READ_UNCOMMITTED`, `READ_COMMITTED`

**방지 격리 수준**: `REPEATABLE_READ` 이상

**방지 원리**:
```
Lock-based (전통적 방식):
  T2에서 SELECT 시 Shared Lock 획득, 트랜잭션 종료까지 유지
  T4에서 B가 UPDATE 시도 → Exclusive Lock 대기 (A의 Shared Lock과 충돌)
  → A가 커밋/롤백할 때까지 B가 블로킹됨
  → A의 두 번째 SELECT도 같은 값(1000)을 읽음

MVCC (MySQL InnoDB REPEATABLE_READ):
  A의 BEGIN 시점에 스냅샷(Read View) 생성
  이후 B가 아무리 커밋해도 A는 스냅샷 기준으로 읽음
  → T6의 SELECT도 스냅샷 기준 1000을 읽음
  → Lock 없이도 반복 읽기 일관성 보장
```

---

### Phantom Read

**정의**: 같은 트랜잭션 안에서 동일 범위 조건으로 조회했을 때, 처음엔 없던 행이
두 번째 조회에서 나타나거나 사라지는 현상. `WHERE` 범위 조건 + `INSERT`/`DELETE` 조합.

```
시간  Transaction A                      Transaction B
────  ─────────────────────────────     ──────────────────────────────
T1    BEGIN
T2    SELECT * FROM orders
      WHERE amount > 5000
      → 3건  (첫 번째 범위 조회)
T3                                       BEGIN
T4                                       INSERT INTO orders
                                         VALUES (amount=8000, ...)
T5                                       COMMIT
T6    SELECT * FROM orders
      WHERE amount > 5000
      → 4건  (두 번째 범위 조회) ◀── Phantom Read!
              幽靈(유령) 행이 나타남
T7    첫 조회 결과 기준으로 처리 중
      → 갑자기 행 수가 달라져 로직 오류
```

**발생 격리 수준**: `READ_UNCOMMITTED`, `READ_COMMITTED`, `REPEATABLE_READ` (lock-based 시스템)

**방지 격리 수준**: `SERIALIZABLE` (표준 기준)

**방지 원리**:
```
SERIALIZABLE — Next-Key Lock (MySQL InnoDB):
  T2에서 범위 조건(amount > 5000)에 Gap Lock 획득
  Gap Lock: 인덱스 레코드 사이의 '빈 공간'을 잠금
  T4에서 B의 INSERT(amount=8000) → Gap Lock 구간에 해당 → 대기
  → A 커밋 후에야 B가 INSERT 가능
  → A의 두 번째 조회도 3건

SERIALIZABLE — PostgreSQL SSI (Serializable Snapshot Isolation):
  트랜잭션 간 읽기/쓰기 의존성을 추적
  충돌 감지 시 나중에 커밋한 트랜잭션을 롤백 (abort)
  → Lock 없이 직렬화 달성 (높은 동시성 유지)
```

---

### MySQL InnoDB의 특수 사례 — REPEATABLE_READ에서 Phantom Read

MySQL InnoDB는 `REPEATABLE_READ`(기본값)에서 MVCC와 Gap Lock을 함께 사용하므로
**일반 SELECT에서는 Phantom Read가 발생하지 않는다.** 하지만 예외가 있다.

```
[Snapshot Read] vs [Current Read]

Snapshot Read (MVCC 스냅샷):
  일반 SELECT문
  → 트랜잭션 시작 시점의 스냅샷 기준으로 읽음
  → Phantom Read 없음

Current Read (최신 커밋 데이터 직접 읽기):
  SELECT ... FOR UPDATE
  SELECT ... LOCK IN SHARE MODE
  UPDATE / DELETE (내부적으로 current read)
  → 스냅샷 무시, 현재 커밋된 최신 데이터 읽음
  → Gap Lock 없으면 Phantom Row 보임
```

```
Phantom이 발생하는 패턴 (REPEATABLE_READ):

시간  Transaction A                      Transaction B
────  ─────────────────────────────     ──────────────────────────────
T1    BEGIN
T2    SELECT * FROM orders
      WHERE amount > 5000
      → 3건  (Snapshot Read, MVCC)
T3                                       INSERT INTO orders (amount=8000)
                                         COMMIT
T4    SELECT * FROM orders
      WHERE amount > 5000
      → 3건  (Snapshot Read, 아직 Phantom 없음)
T5    SELECT * FROM orders
      WHERE amount > 5000
      FOR UPDATE                ◀── Current Read!
      → 4건  (Phantom Read 발생!)
              B가 삽입한 행이 Gap Lock 없이 보임
```

**실무 대응**:
- 범위 조회 후 `FOR UPDATE`를 혼합하는 패턴 → `SERIALIZABLE` 사용 또는 애플리케이션 레벨 제어
- 정합성이 절대적으로 중요한 금융 연산 → `SERIALIZABLE` 또는 비관적 락(`SELECT ... FOR UPDATE`)

---

### 격리 수준 구현 메커니즘 비교

```
┌──────────────────────┬────────────────────────────────────────────────┐
│ 방식                 │ 특징                                            │
├──────────────────────┼────────────────────────────────────────────────┤
│ Lock 기반            │ 읽기 시 Shared Lock, 쓰기 시 Exclusive Lock     │
│ (전통 RDBMS)         │ 높은 격리 = 높은 잠금 경쟁 = 낮은 동시성        │
│                      │ Deadlock 위험                                  │
├──────────────────────┼────────────────────────────────────────────────┤
│ MVCC                 │ 각 트랜잭션에 스냅샷 제공                       │
│ (MySQL InnoDB,       │ 읽기 시 Lock 불필요 → 읽기/쓰기 충돌 없음      │
│  PostgreSQL)         │ Undo Log에 이전 버전 보관                       │
│                      │ 읽기 성능↑, 쓰기 충돌만 Lock으로 처리          │
└──────────────────────┴────────────────────────────────────────────────┘
```

```
MVCC 스냅샷 동작 예시 (MySQL InnoDB):

                  Undo Log (버전 체인)
balance=1000 ──→ balance=5000 ──→ balance=9000
(TX 50 이전)     (TX 50 커밋)     (TX 80 커밋)

TX 60 (REPEATABLE_READ, 시작 시점=TX 55):
  → TX 55 이전 마지막 커밋 버전 = balance=1000 읽음

TX 90 (READ_COMMITTED):
  → 현재 최신 커밋 버전 = balance=9000 읽음
  → TX 60이 아직 살아있어도 TX 90은 최신 커밋 읽음
```

---

### 격리 수준 선택 가이드

```
READ_UNCOMMITTED
  └─ 사용처: 거의 없음. 실시간 통계/모니터링처럼 약간의 부정확함이 허용되고
             최대 처리량이 필요한 경우 (실무에서 사실상 미사용)

READ_COMMITTED
  └─ 사용처: Oracle/PostgreSQL 기본값
             대부분의 웹 애플리케이션 (Dirty Read 방지로 충분한 경우)
             OLTP 환경에서 높은 동시성 필요 시

REPEATABLE_READ (MySQL 기본값)
  └─ 사용처: 한 트랜잭션 내에서 같은 데이터를 여러 번 읽어야 하는 경우
             정합성 중요, MySQL InnoDB에서는 Phantom Read도 대부분 방지됨
             배치 처리, 재무 계산

SERIALIZABLE
  └─ 사용처: 완전한 데이터 정합성 필수 (금융 이체, 재고 차감)
             Phantom Read까지 100% 방지
             처리량보다 정합성이 절대 우선인 경우
             (성능 저하 크므로 꼭 필요한 연산에만 국소적으로 적용)
```

```java
// 실무 패턴: 기본은 DEFAULT, 특수 케이스만 명시

// 일반 조회 — DB 기본값 사용
@Transactional(readOnly = true)
public List<Order> findOrders() { ... }

// 같은 데이터를 트랜잭션 내에서 여러 번 읽는 중요 로직
@Transactional(isolation = Isolation.REPEATABLE_READ)
public void calculateMonthlyRevenue() { ... }

// 재고 차감처럼 Phantom Read까지 막아야 하는 금융 연산
@Transactional(isolation = Isolation.SERIALIZABLE)
public void deductStock(Long productId, int quantity) { ... }
```

> **주의**: `Isolation.SERIALIZABLE`은 Gap Lock(MySQL) 또는 SSI(PostgreSQL)를 사용하므로
> 동시 트랜잭션이 많을수록 처리량이 급격히 감소하고 Deadlock 위험이 증가한다.
> 격리 수준을 올리기 전에 비관적 락(`SELECT ... FOR UPDATE`)으로 대상을 좁히는 것이 대안이 될 수 있다.

---

### 이상 현상 × 격리 수준 — 전체 정리

```
                    ┌──────────────┬──────────────────┬──────────────┐
                    │  Dirty Read  │ Non-Repeatable   │ Phantom Read │
                    │              │      Read        │              │
┌───────────────────┼──────────────┼──────────────────┼──────────────┤
│ READ_UNCOMMITTED  │   발생 ❌    │    발생 ❌        │   발생 ❌    │
├───────────────────┼──────────────┼──────────────────┼──────────────┤
│ READ_COMMITTED    │   방지 ✅    │    발생 ❌        │   발생 ❌    │
├───────────────────┼──────────────┼──────────────────┼──────────────┤
│ REPEATABLE_READ   │   방지 ✅    │    방지 ✅        │ △ (DB마다)  │
│ (MySQL InnoDB)    │              │                  │ MVCC+GapLock │
├───────────────────┼──────────────┼──────────────────┼──────────────┤
│ SERIALIZABLE      │   방지 ✅    │    방지 ✅        │   방지 ✅    │
└───────────────────┴──────────────┴──────────────────┴──────────────┘

△ MySQL InnoDB REPEATABLE_READ: 일반 SELECT는 방지, FOR UPDATE 혼합 패턴은 주의
```

---

## 4. 트랜잭션 전파 전체 옵션

| 전파 속성 | 기존 트랜잭션 있을 때 | 기존 트랜잭션 없을 때 | 주요 용도 |
|---------|-----------------|-----------------|---------|
| `REQUIRED` (기본값) | 참여 | 새로 생성 | 일반적인 서비스 계층 |
| `REQUIRES_NEW` | 기존 일시 중단, 새로 생성 | 새로 생성 | 독립적인 로그 저장, 이메일 발송 |
| `SUPPORTS` | 참여 | 없이 실행 | 트랜잭션 선택적 참여 |
| `NOT_SUPPORTED` | 기존 일시 중단, 없이 실행 | 없이 실행 | 트랜잭션 없이 실행해야 할 때 |
| `MANDATORY` | 참여 | **예외 발생** | 반드시 트랜잭션 안에서 호출해야 하는 메서드 |
| `NEVER` | **예외 발생** | 없이 실행 | 트랜잭션 안에서 호출 금지 |
| `NESTED` | 중첩 트랜잭션(Savepoint) | 새로 생성 | 부분 롤백 허용 (JDBC만 지원) |

### REQUIRED vs REQUIRES_NEW 동작 비교

```
[ REQUIRED ]
Outer TX ─────────────────────────────► commit/rollback
              Inner TX (참여)
              └── 같은 TX → 내부 실패 = 전체 롤백

[ REQUIRES_NEW ]
Outer TX ──── (일시 중단) ────────────► commit/rollback
              Inner TX (새로 생성) ──► 독립적 commit/rollback
              └── 내부 커밋 후 외부 롤백 시 → 내부 결과는 DB에 남음 (주의!)
```

### NESTED (중첩 트랜잭션) — JPA에서는 주의

```java
@Transactional(propagation = Propagation.NESTED)
public void nestedMethod() {
    // Savepoint를 생성, 실패 시 이 지점으로만 롤백
    // JPA(Hibernate)는 Savepoint를 완전히 지원하지 않으므로 JDBC 직접 사용 시에만 권장
}
```

---

## 5. readOnly=true 최적화 원리

```java
@Transactional(readOnly = true)
public List<User> findAll() { ... }
```

| 항목 | readOnly=false (기본) | readOnly=true |
|------|---------------------|---------------|
| Dirty Checking | ✅ 수행 (스냅샷 저장) | ❌ 비활성화 |
| Flush Mode | AUTO | MANUAL (자동 flush 없음) |
| DB 최적화 | 없음 | 읽기 전용 힌트 전달 (DB에 따라 최적화) |
| 성능 | 낮음 | 높음 (스냅샷 생성 안 함) |
| 데이터 소스 분리 | 불가 | Read Replica로 라우팅 가능 |

> **실무:** CQRS 패턴에서 조회 메서드에 `readOnly=true`를 적용하면 Read Replica로 자동 라우팅하는 인프라 구성이 가능합니다.

---

## 6. 실습 결과 확인 포인트
`TxRunner` 실행 결과를 통해 다음을 확인합니다.

1.  **Member1 (Unchecked)**: DB에 저장되지 않음 (롤백됨).
2.  **Member2 (Checked)**: DB에 저장됨 (커밋됨).
3.  **Member3 (Checked + rollbackFor)**: DB에 저장되지 않음 (롤백됨).

## 4. @Transactional 안티패턴 (Anti-Patterns)

실무에서 자주 발생하는 트랜잭션 관련 실수와 안티패턴을 정리합니다.

### 4.1 AOP Proxy Bypass (Self-Invocation)

**문제**: 같은 클래스 내에서 `@Transactional` 메서드를 호출하면 트랜잭션이 적용되지 않습니다.

**원인**: Spring AOP는 프록시 기반으로 동작합니다. 같은 클래스 내부 호출은 프록시를 거치지 않고 `this`를 통해 직접 호출되므로 AOP가 적용되지 않습니다.

```java
@Service
public class UserService {
    public void registerUser() {
        saveUser();  // ❌ 프록시 우회! @Transactional 무시됨
    }

    @Transactional
    public void saveUser() {
        // 트랜잭션이 시작되지 않음
    }
}
```

**해결책**:
- 별도의 Service 클래스로 분리
- `@Autowired private UserService self` (Self Injection)
- `AopContext.currentProxy()` 사용 (비권장)

---

### 4.2 Invalid Method Modifiers

**문제**: `private`, `final`, `static` 메서드에 `@Transactional`을 붙여도 동작하지 않습니다.

**원인**: Spring AOP는 CGLIB 프록시를 사용하여 메서드를 오버라이드합니다. `private`/`final`/`static` 메서드는 오버라이드할 수 없으므로 프록시가 적용되지 않습니다.

```java
@Service
public class OrderService {
    @Transactional
    private void processOrder() {  // ❌ private 메서드
        // 트랜잭션 적용 안 됨
    }

    @Transactional
    public final void confirmOrder() {  // ❌ final 메서드
        // 트랜잭션 적용 안 됨
    }

    @Transactional
    public static void cancelOrder() {  // ❌ static 메서드
        // 트랜잭션 적용 안 됨
    }
}
```

**해결책**: 반드시 `public`, 인스턴스 메서드로 작성해야 합니다.

---

### 4.3 Transaction Propagation Conflict Detection

**문제**: 트랜잭션 전파 속성 충돌로 인한 런타임 에러 발생

**위험한 전파 속성**:

1. **MANDATORY**: 트랜잭션 없이 호출하면 예외 발생
```java
@Transactional(propagation = Propagation.MANDATORY)
public void mustHaveTransaction() {
    // IllegalTransactionStateException 발생
}
```

2. **NEVER**: 트랜잭션 내에서 호출하면 예외 발생
```java
@Transactional(propagation = Propagation.NEVER)
public void mustNotHaveTransaction() {
    // IllegalTransactionStateException 발생
}
```

3. **REQUIRES_NEW**: 데이터 불일치 위험
```java
@Transactional
public void outerMethod() {
    // 외부 트랜잭션
    innerService.createNew();  // 별도 트랜잭션 (외부와 독립)
    // 외부만 롤백되면 데이터 불일치 발생
}
```

**해결책**: 전파 속성을 신중하게 선택하고, 호출 체인을 문서화합니다.

---

### 4.4 N+1 Query Detection

**문제**: Lazy 로딩 관계를 반복문에서 접근할 때 쿼리가 N+1번 실행됩니다.

**원인**: JPA는 기본적으로 연관 관계를 Lazy 로딩합니다. 반복문 안에서 연관 엔티티에 접근하면 매번 쿼리가 실행됩니다.

```java
@Entity
public class Team {
    @OneToMany(mappedBy = "team")  // 기본 LAZY
    private List<Member> members;
}

@Transactional
public void printTeams() {
    List<Team> teams = teamRepository.findAll();  // 1개 쿼리
    for (Team team : teams) {
        team.getMembers().size();  // N개 쿼리 (팀 개수만큼)
    }
}
```

**해결책**:
- Fetch Join 사용: `@Query("SELECT t FROM Team t JOIN FETCH t.members")`
- `@EntityGraph` 사용
- Batch Size 설정: `@BatchSize(size = 10)`

---

### 4.5 ReadOnly Transaction Write Operations

**문제**: `@Transactional(readOnly=true)` 메서드에서 write 작업(save/update/delete)을 수행합니다.

**원인**: `readOnly=true`는 읽기 전용 최적화를 적용합니다. 하지만 JPA는 에러를 던지지 않고 무시하거나, DB에 따라 동작이 달라집니다.

```java
@Transactional(readOnly = true)
public void updateUser(Long id) {
    User user = userRepository.findById(id);
    user.setName("New Name");  // ❌ Dirty Checking 동작 안 할 수 있음
    userRepository.save(user);  // ❌ DB에 따라 에러 또는 무시
}
```

**해결책**: write 작업이 필요하면 `readOnly=false` (기본값) 사용

---

### 4.6 Checked Exception Rollback

**문제**: `rollbackFor` 설정 없이 Checked Exception을 던지면 롤백되지 않습니다.

**원인**: Spring은 Checked Exception을 "복구 가능한 비즈니스 예외"로 간주하여 커밋합니다.

```java
@Transactional
public void transfer(Long from, Long to, int amount) throws InsufficientFundsException {
    accountRepository.withdraw(from, amount);
    if (balance < 0) {
        throw new InsufficientFundsException();  // ❌ 커밋됨! 데이터 불일치
    }
    accountRepository.deposit(to, amount);
}
```

**해결책**:
```java
@Transactional(rollbackFor = Exception.class)  // 모든 예외 롤백
// 또는
@Transactional(rollbackFor = InsufficientFundsException.class)  // 특정 예외만
```

---

### 4.7 @Async and @Transactional Conflicts

**문제**: `@Async`와 `@Transactional`을 함께 사용하면 예상치 못한 동작이 발생합니다.

**충돌 패턴 3가지**:

1. **같은 메서드에 동시 사용**
```java
@Async
@Transactional
public void asyncProcess() {
    // ❌ 비동기 스레드에서 트랜잭션이 분리됨
    // 호출한 스레드의 트랜잭션과 무관
}
```

2. **@Async 메서드에서 Lazy 로딩**
```java
@Async
public void sendEmail(User user) {
    user.getOrders().size();  // ❌ LazyInitializationException
    // 트랜잭션이 이미 종료됨
}
```

3. **같은 클래스 내 @Async 호출**
```java
@Service
public class NotificationService {
    public void notify() {
        sendAsync();  // ❌ 프록시 우회로 동기 실행됨
    }

    @Async
    public void sendAsync() {
        // 비동기로 실행되지 않음
    }
}
```

**해결책**:
- `@Async` 메서드는 별도 Service로 분리
- `@Transactional` 메서드 안에서 `@Async` 호출 금지
- Lazy 로딩은 트랜잭션 내에서 미리 초기화

---

### 4.8 ReadOnly Transaction Calling Write Methods

**문제**: `@Transactional(readOnly=true)` 메서드가 쓰기 가능한 메서드를 호출할 때 예상치 못한 동작 발생

**시나리오 1: 같은 클래스 호출 (ERROR)**
```java
@Service
public class ProductService {
    @Transactional(readOnly = true)
    public void process() {
        updateStock();  // ❌ 프록시 우회로 REQUIRES_NEW 무시됨
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStock() {
        // readOnly 트랜잭션 상태 유지 (새 트랜잭션 안 만들어짐)
    }
}
```

**시나리오 2: 다른 클래스 호출 (WARNING)**
```java
@Service
public class OrderService {
    @Autowired
    private InventoryService inventoryService;

    @Transactional(readOnly = true)
    public void viewOrder() {
        inventoryService.decreaseStock();  // ⚠️ REQUIRES_NEW로 해결 가능
    }
}

@Service
public class InventoryService {
    @Transactional  // REQUIRED이면 readOnly 상속됨
    public void decreaseStock() {
        // readOnly 트랜잭션으로 실행됨 (write 실패 가능)
    }
}
```

**해결책**:
- 다른 클래스 호출 시: `propagation = Propagation.REQUIRES_NEW` 사용
- 같은 클래스 호출: Service 분리 필수

---

## 5. 안티패턴 체크리스트

실무에서 코드 리뷰 시 확인할 항목:

- [ ] 같은 클래스 내에서 `@Transactional` 메서드 호출하지 않는가?
- [ ] `@Transactional`이 `public` 메서드에만 붙어 있는가?
- [ ] `MANDATORY`, `NEVER` 전파 속성을 신중하게 사용했는가?
- [ ] 반복문에서 Lazy 로딩 접근을 피하고 Fetch Join을 사용했는가?
- [ ] `readOnly=true` 메서드에서 write 작업이 없는가?
- [ ] Checked Exception 발생 시 `rollbackFor`를 명시했는가?
- [ ] `@Async`와 `@Transactional`을 분리했는가?
- [ ] `readOnly` 메서드가 write 메서드를 호출할 때 `REQUIRES_NEW`를 고려했는가?
