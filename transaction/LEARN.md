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

## 3. 실습 결과 확인 포인트
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
