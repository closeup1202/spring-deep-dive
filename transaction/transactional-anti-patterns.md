# Spring `@Transactional` 안티패턴 완전 정복

> 이 플러그인(**Spring Transaction Inspector**)이 감지하는 11가지 `@Transactional` 안티패턴을
> **기초 개념부터** 차근차근 설명하는 문서입니다.
> Spring을 처음 다루는 분도 이해할 수 있도록, "왜 문제가 되는지"를 중심으로 풀어 씁니다.

---

## 목차

- [Part 0. 먼저 알아야 할 기초 개념](#part-0-먼저-알아야-할-기초-개념)
  - [0-1. 트랜잭션이란?](#0-1-트랜잭션이란)
  - [0-2. `@Transactional`은 어떻게 동작할까? (AOP 프록시)](#0-2-transactional은-어떻게-동작할까-aop-프록시)
  - [0-3. propagation(전파)과 readOnly](#0-3-propagation전파과-readonly)
  - [0-4. 롤백 규칙: 언제 롤백되는가?](#0-4-롤백-규칙-언제-롤백되는가)
- [Part 1. 프록시가 가로채지 못하는 경우](#part-1-프록시가-가로채지-못하는-경우)
  - [① 같은 클래스 내부 호출 (Self-Invocation)](#-같은-클래스-내부-호출-self-invocation)
  - [② private / final / static 메서드](#-private--final--static-메서드)
- [Part 2. 예외와 롤백의 함정](#part-2-예외와-롤백의-함정)
  - [③ Checked 예외인데 `rollbackFor` 없음](#-checked-예외인데-rollbackfor-없음)
  - [④ 예외를 삼켜버림 (Swallowed Exception)](#-예외를-삼켜버림-swallowed-exception)
- [Part 3. 전파(propagation) 설정 충돌](#part-3-전파propagation-설정-충돌)
  - [⑤ MANDATORY / NEVER / REQUIRES_NEW 충돌](#-mandatory--never--requires_new-충돌)
  - [⑥ readOnly 트랜잭션이 쓰기 메서드를 호출](#-readonly-트랜잭션이-쓰기-메서드를-호출)
- [Part 4. readOnly 위반](#part-4-readonly-위반)
  - [⑦ readOnly 트랜잭션 안에서 직접 쓰기](#-readonly-트랜잭션-안에서-직접-쓰기)
- [Part 5. 성능과 리소스](#part-5-성능과-리소스)
  - [⑧ N+1 쿼리](#-n1-쿼리)
  - [⑨ 트랜잭션 안에서의 외부 호출](#-트랜잭션-안에서의-외부-호출)
- [Part 6. 비동기 / 재시도와의 충돌](#part-6-비동기--재시도와의-충돌)
  - [⑩ `@Async` + `@Transactional`](#-async--transactional)
  - [⑪ `@Retryable` + `@Transactional`](#-retryable--transactional)
- [한눈에 보는 요약표](#한눈에-보는-요약표)

---

## Part 0. 먼저 알아야 할 기초 개념

11가지 안티패턴 중 상당수는 **딱 하나의 원리**에서 출발합니다.
바로 "`@Transactional`은 마법이 아니라 **프록시**로 동작한다"는 사실이에요.
이 Part만 제대로 이해하면 나머지는 자연스럽게 이해됩니다.

### 0-1. 트랜잭션이란?

**트랜잭션(Transaction)** 은 "모두 성공하거나, 모두 실패해야 하는" 작업의 묶음입니다.

예를 들어 계좌 이체는 두 단계로 이루어집니다.

1. A 계좌에서 1만원 출금
2. B 계좌에 1만원 입금

만약 1번만 성공하고 2번에서 오류가 나면? 돈이 허공으로 사라집니다.
그래서 이 둘을 **하나의 트랜잭션**으로 묶어, 중간에 실패하면 **전부 되돌립니다(rollback)**.
끝까지 성공하면 **확정(commit)** 합니다.

이런 "전부 성공 or 전부 실패" 성질을 **원자성(Atomicity)** 이라고 부릅니다.

### 0-2. `@Transactional`은 어떻게 동작할까? (AOP 프록시)

Spring에서는 메서드에 `@Transactional`만 붙이면 트랜잭션이 적용됩니다.

```java
@Service
public class AccountService {
    @Transactional
    public void transfer(Long from, Long to, long amount) {
        withdraw(from, amount);
        deposit(to, amount);
    }
}
```

그런데 **어떻게** 이게 가능할까요? `transfer` 메서드 안에는 트랜잭션을 시작/커밋하는
코드가 한 줄도 없는데 말이죠.

비밀은 **프록시(Proxy)** 입니다. Spring은 `AccountService`를 그대로 쓰지 않고,
**똑같이 생긴 대역(代役) 객체**를 하나 만들어서 그걸 주입합니다.

```
[호출자] ──> [AccountService 프록시] ──> [진짜 AccountService]
                     │
                     └─ 여기서 "트랜잭션 시작 → 원본 메서드 실행 → 커밋/롤백"을 대신 처리
```

즉, 우리가 `accountService.transfer(...)`를 호출하면 실제로는 **프록시의 메서드**가 먼저 실행되어:

1. 트랜잭션을 **시작**하고
2. 진짜 `transfer()`를 호출하고
3. 정상 종료되면 **커밋**, 예외가 터지면 **롤백**

합니다.

> 🔑 **핵심 한 줄**
> `@Transactional`은 "**프록시가 메서드 호출을 가로채야**" 동작한다.
> 프록시가 가로채지 못하면 → 어노테이션은 **그냥 무시**된다. 에러도, 경고도 없이.

바로 이 "프록시가 가로채지 못하는 상황"들이 안티패턴 ①②⑩⑪의 뿌리입니다.

### 0-3. propagation(전파)과 readOnly

트랜잭션이 **이미 진행 중일 때**, 다른 트랜잭션 메서드를 호출하면 어떻게 될까요?
이걸 정하는 게 **전파 속성(propagation)** 입니다.

| propagation | 의미 |
|---|---|
| `REQUIRED` (기본값) | 진행 중인 트랜잭션이 있으면 **참여**, 없으면 **새로 시작** |
| `REQUIRES_NEW` | 항상 **새 트랜잭션**을 시작 (기존 건 잠시 중단) |
| `MANDATORY` | 진행 중인 트랜잭션이 **반드시 있어야** 함. 없으면 예외 |
| `NEVER` | 트랜잭션이 **있으면 안 됨**. 있으면 예외 |
| `SUPPORTS` | 있으면 참여, 없으면 트랜잭션 없이 진행 |
| `NOT_SUPPORTED` | 트랜잭션 없이 진행 (있으면 중단) |
| `NESTED` | 중첩 트랜잭션(savepoint) 생성 |

**readOnly**는 "이 메서드는 읽기만 한다"는 힌트입니다.

```java
@Transactional(readOnly = true)
public List<User> findUsers() { ... }
```

`readOnly = true`를 주면 Hibernate가 **변경 감지(dirty checking)** 를 생략해 성능이 좋아지고,
일부 환경에서는 **읽기 전용 DB(replica)** 로 라우팅되기도 합니다.

### 0-4. 롤백 규칙: 언제 롤백되는가?

가장 많이 오해하는 부분입니다.

> ⚠️ **Spring은 기본적으로 `RuntimeException`과 `Error`에 대해서만 롤백한다.**
> `IOException` 같은 **checked 예외는 기본적으로 롤백하지 않는다 (커밋된다!).**

이 규칙이 안티패턴 ③④의 핵심입니다.

```java
@Transactional
public void save() throws IOException {
    repository.save(entity);   // ① 저장
    throw new IOException();    // ② checked 예외 → 롤백 안 됨 → ①이 커밋됨!
}
```

---

## Part 1. 프록시가 가로채지 못하는 경우

> 인스펙션: `TransactionalMethodCallInspection`, `InvalidTransactionalMethodInspection`

[0-2](#0-2-transactional은-어떻게-동작할까-aop-프록시)에서 본 것처럼,
프록시가 호출을 가로채지 못하면 `@Transactional`은 **조용히 무시**됩니다.

### ① 같은 클래스 내부 호출 (Self-Invocation)

**개념.** 같은 클래스 안의 메서드를 호출하면 `this.method()` 직접 호출이 됩니다.
이건 프록시를 거치지 않으므로 트랜잭션이 적용되지 않습니다.

**❌ 나쁜 예시**
```java
@Service
public class OrderService {

    public void createOrder() {
        // this.saveOrder() 와 동일 → 프록시를 거치지 않음!
        saveOrder();   // ⚠️ @Transactional 무시됨
    }

    @Transactional
    public void saveOrder() {
        orderRepository.save(...);
    }
}
```

`createOrder()`는 외부에서 프록시를 통해 들어왔지만,
그 안에서 `saveOrder()`를 부르는 순간 **프록시를 우회**합니다.
→ `saveOrder()`의 `@Transactional`은 동작하지 않습니다.

**✅ 좋은 예시 — 별도 빈으로 분리**
```java
@Service
public class OrderService {
    private final OrderPersistenceService persistence;

    public void createOrder() {
        persistence.saveOrder();   // ✅ 다른 빈 → 프록시 경유 → 트랜잭션 적용
    }
}

@Service
public class OrderPersistenceService {
    @Transactional
    public void saveOrder() { ... }
}
```

> 💡 호출하는 쪽도 `@Transactional`이고 기본 전파(REQUIRED)라면 같은 트랜잭션에 합류하므로 큰 문제가 없습니다.
> 그래서 이 인스펙션은 상황에 따라 INFO(참고) 또는 WARNING으로 심각도를 구분합니다.

### ② private / final / static 메서드

**개념.** Spring AOP 프록시는 메서드를 **오버라이드**하는 방식으로 가로챕니다.
따라서 오버라이드할 수 없는 메서드에는 트랜잭션을 걸 수 없습니다.

- **private** → 외부에서 호출 불가, 프록시가 가로챌 수 없음
- **final** → 오버라이드 불가
- **static** → 인스턴스가 아닌 클래스에 속함, 프록시 대상 아님

**❌ 나쁜 예시**
```java
@Service
public class PaymentService {

    @Transactional
    private void charge() { ... }   // ❌ private → 무시됨

    @Transactional
    public final void refund() { ... }   // ❌ final → 무시됨

    @Transactional
    public static void report() { ... }  // ❌ static → 무시됨
}
```

**✅ 좋은 예시**
```java
@Service
public class PaymentService {
    @Transactional
    public void charge() { ... }   // ✅ public, non-final, non-static
}
```

> 💡 이 인스펙션은 "메서드 visibility 변경", "final 제거", "static 제거", "@Transactional 제거"
> 같은 빠른 수정(Quick Fix)을 제공합니다.

---

## Part 2. 예외와 롤백의 함정

> 인스펙션: `CheckedExceptionRollbackInspection`, `SwallowedExceptionInspection`

[0-4](#0-4-롤백-규칙-언제-롤백되는가)에서 본 "기본은 RuntimeException만 롤백" 규칙에서 비롯됩니다.

### ③ Checked 예외인데 `rollbackFor` 없음

**개념.** checked 예외(`Exception`을 상속, `RuntimeException`이 아닌 것)는
**기본적으로 롤백을 유발하지 않습니다.** 그래서 일부만 저장된 채 커밋되는 사고가 납니다.

**❌ 나쁜 예시**
```java
@Transactional
public void processPayment() throws PaymentException {  // checked 예외
    account.deduct(amount);          // 잔액 차감
    if (gatewayFails()) {
        throw new PaymentException(); // ⚠️ 롤백 안 됨 → 차감은 그대로 커밋!
    }
}
```
잔액은 빠져나갔는데 결제는 실패하는 최악의 상황.

**✅ 좋은 예시 — `rollbackFor` 지정**
```java
@Transactional(rollbackFor = Exception.class)
public void processPayment() throws PaymentException {
    account.deduct(amount);
    if (gatewayFails()) {
        throw new PaymentException();  // ✅ 이제 롤백됨
    }
}
```

> 💡 Jakarta/javax `@Transactional`을 쓴다면 같은 역할을 `rollbackOn` 속성이 합니다.
> 인스펙션은 두 경우를 모두 인식합니다.

### ④ 예외를 삼켜버림 (Swallowed Exception)

**개념.** 트랜잭션 메서드 안에서 예외를 `catch`로 잡고 **다시 던지지도, 롤백 표시도 하지 않으면**
프록시 입장에서는 "정상 종료"로 보입니다. → **커밋**됩니다.

**❌ 나쁜 예시**
```java
@Transactional
public void process() {
    try {
        repository.save(entity);
        riskyStep();              // 여기서 예외 발생
    } catch (Exception e) {
        log.error("실패", e);     // ⚠️ 잡고 끝 → 정상 종료로 간주 → 커밋!
    }
}
```
중간까지 진행된 변경이 **부분 커밋**되어 데이터가 깨집니다.

**✅ 좋은 예시 — 다시 던지거나 롤백 표시**
```java
@Transactional
public void process() {
    try {
        repository.save(entity);
        riskyStep();
    } catch (Exception e) {
        log.error("실패", e);
        throw e;   // ✅ 방법 1: 다시 던지기
        // 또는 방법 2: 롤백만 표시하고 정상 흐름 유지
        // TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    }
}
```

> 💡 **감지 범위:** 이 인스펙션은 **메서드에 직접** `@Transactional`이 붙은 경우만 검사합니다.
> 클래스 레벨 `@Transactional`은 (무관한 메서드까지 경고하는 걸 피하기 위해) 대상에서 제외합니다.

---

## Part 3. 전파(propagation) 설정 충돌

> 인스펙션: `TransactionalPropagationConflictInspection`, `ReadOnlyTransactionWriteCallInspection`

[0-3](#0-3-propagation전파과-readonly)의 전파 속성을 잘못 조합하면 런타임 예외나 데이터 불일치가 납니다.

### ⑤ MANDATORY / NEVER / REQUIRES_NEW 충돌

**개념.** 전파 속성은 "호출 시점에 트랜잭션이 있는지/없는지"에 대한 **요구 조건**입니다.
이 조건이 맞지 않으면 예외가 터집니다.

**MANDATORY — 트랜잭션이 반드시 있어야 함**
```java
public void update() {              // ❌ @Transactional 없음
    decreaseStock();                // → IllegalTransactionStateException!
}

@Transactional(propagation = Propagation.MANDATORY)
public void decreaseStock() { ... }
```

**NEVER — 트랜잭션이 있으면 안 됨**
```java
@Transactional
public void register() {
    sendStatistics();               // ❌ 트랜잭션 안에서 호출 → 예외!
}

@Transactional(propagation = Propagation.NEVER)
public void sendStatistics() { ... }
```

**REQUIRES_NEW — 독립 트랜잭션이라 데이터 불일치 위험**
```java
@Transactional
public void createOrder() {
    orderRepository.save(order);    // 트랜잭션 A
    logHistory();                   // 트랜잭션 B (독립!)
    throw new RuntimeException();    // A는 롤백되지만 B는 이미 커밋됨!
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void logHistory() { ... }
```

> 💡 MANDATORY/NEVER 위반은 **ERROR**, REQUIRES_NEW로 인한 불일치 위험은 **약한 경고**로 표시합니다.

### ⑥ readOnly 트랜잭션이 쓰기 메서드를 호출

**개념.** `readOnly = true` 메서드가 (기본 전파로) **쓰기 가능한** 트랜잭션 메서드를 호출하면,
호출된 메서드도 **읽기 전용 트랜잭션에 합류**합니다. 그 안에서 쓰기를 시도하면 실패합니다.

**❌ 나쁜 예시**
```java
@Transactional(readOnly = true)
public void viewDashboard() {
    User u = userRepository.findById(1L);
    updateLastSeen();   // ⚠️ readOnly 트랜잭션에 합류 → 쓰기 시 예외
}

@Transactional  // REQUIRED (기본)
public void updateLastSeen() {
    statsRepository.save(...);
    // InvalidDataAccessApiUsageException: ... read-only mode
}
```

**✅ 좋은 예시**
- 쓰기 메서드를 다른 빈으로 분리하고 `REQUIRES_NEW`로 독립 트랜잭션을 갖게 하거나,
- 애초에 호출하는 쪽의 `readOnly = true`를 빼기.

> 💡 같은 클래스 호출이면 `REQUIRES_NEW`도 [①](#-같은-클래스-내부-호출-self-invocation) 때문에 안 먹히므로,
> 이 경우는 **ERROR**(더 심각)로 표시합니다.

---

## Part 4. readOnly 위반

> 인스펙션: `ReadOnlyTransactionalInspection`

### ⑦ readOnly 트랜잭션 안에서 직접 쓰기

**개념.** `readOnly = true`라고 선언해 놓고 그 안에서 `save()`/`delete()` 같은
쓰기 연산이나 컬렉션 수정을 하면, 의도와 코드가 모순됩니다.

**❌ 나쁜 예시**
```java
@Transactional(readOnly = true)
public void process() {
    User user = userRepository.findById(1L);
    user.setName("수정");
    userRepository.save(user);   // ⚠️ readOnly인데 쓰기!
}
```

**✅ 좋은 예시**
```java
@Transactional  // 쓰기가 필요하면 readOnly를 빼기
public void process() {
    User user = userRepository.findById(1L);
    user.setName("수정");
    userRepository.save(user);
}
```

> 💡 [⑥](#-readonly-트랜잭션이-쓰기-메서드를-호출)이 "**다른 메서드를 호출**"하는 경우라면,
> ⑦은 "**같은 메서드 안에서 직접**" 쓰기를 하는 경우입니다.
> 오탐을 줄이기 위해 Repository/EntityManager 등 실제 쓰기 타입일 때만 감지합니다.

---

## Part 5. 성능과 리소스

> 인스펙션: `NPlusOneQueryInspection`, `ExternalCallInTransactionInspection`

### ⑧ N+1 쿼리

**개념.** 목록을 한 번 조회한 뒤, 반복문/스트림에서 각 요소의 **지연 로딩(LAZY) 연관관계**에
접근하면, 요소마다 추가 쿼리가 나갑니다. 1번 + N번 = **N+1 쿼리**.

**❌ 나쁜 예시**
```java
@Transactional
public void printAuthors() {
    List<Post> posts = postRepository.findAll();  // 쿼리 1번

    for (Post post : posts) {
        post.getAuthor().getName();  // ⚠️ post마다 쿼리 1번 더 → 총 N번
    }
}
```

**✅ 좋은 예시 — fetch join으로 한 번에**
```java
@Query("SELECT p FROM Post p JOIN FETCH p.author")
List<Post> findAllWithAuthor();   // ✅ 단일 쿼리
```

> 💡 `@OneToMany`/`@ManyToMany`(기본 LAZY)와 `@ManyToOne`/`@OneToOne`(fetch=LAZY 명시)을 모두 인식하며,
> for-each와 stream(`map`/`flatMap`/`forEach`/`filter`)을 검사합니다.
> OSIV 환경을 위해 "트랜잭션 밖도 검사" 옵션도 있습니다.

### ⑨ 트랜잭션 안에서의 외부 호출

**개념.** 트랜잭션 메서드는 **메서드 전체 구간 동안 DB 커넥션을 붙잡고** 있습니다.
그 안에서 HTTP 호출, 메일 전송, 파일 I/O, `Thread.sleep` 같은 **느린 외부 작업**을 하면,
커넥션을 그만큼 오래 점유합니다. 부하가 몰리면 **커넥션 풀이 고갈**되어 앱이 멈춥니다.

**❌ 나쁜 예시**
```java
@Transactional
public void placeOrder(Order order) {
    orderRepository.save(order);
    restTemplate.postForObject(url, order, Void.class);  // ⚠️ HTTP 동안 커넥션 점유!
}
```

**✅ 좋은 예시 — 외부 호출은 트랜잭션 밖으로**
```java
public void placeOrder(Order order) {
    saveOrder(order);                                    // ✅ 짧은 트랜잭션
    restTemplate.postForObject(url, order, Void.class);  // ✅ 트랜잭션 밖
}

@Transactional
public void saveOrder(Order order) {
    orderRepository.save(order);
}
```

> 💡 **감지 대상:** RestTemplate / RestClient / WebClient, HttpClient, OkHttp, Apache HttpClient,
> `@FeignClient` 인터페이스, JavaMailSender / MailSender, `java.nio.file.Files`, `Thread.sleep()`.
> ([④](#-예외를-삼켜버림-swallowed-exception)와 마찬가지로 **메서드 레벨** `@Transactional`만 검사합니다.)

---

## Part 6. 비동기 / 재시도와의 충돌

> 인스펙션: `AsyncTransactionalConflictInspection`, `RetryableTransactionalConflictInspection`

### ⑩ `@Async` + `@Transactional`

**개념.** `@Async`는 작업을 **다른 스레드**에서 실행합니다.
그런데 트랜잭션 컨텍스트(및 영속성 컨텍스트)는 스레드에 묶여 있어 **전파되지 않습니다.**

이 인스펙션은 세 가지 패턴을 감지합니다.

**패턴 1: 같은 메서드에 둘 다**
```java
@Async
@Transactional   // ⚠️ 트랜잭션이 async 스레드로 전파되지 않음
public void process() { ... }
```

**패턴 2: `@Async` 안에서 지연 로딩 접근**
```java
@Async
public void handle(User user) {
    user.getOrders().size();  // ⚠️ 세션이 닫혀 LazyInitializationException
}
```

**패턴 3: 같은 클래스에서 `@Async` 호출**
```java
public void caller() {
    doAsync();  // ⚠️ 프록시 우회 → 동기로 실행됨 (①과 같은 원리)
}
@Async
public void doAsync() { ... }
```

**✅ 좋은 예시:** 비동기 메서드를 별도 빈으로 분리하고, 필요한 데이터는 **미리 로딩**해서 넘기기.

### ⑪ `@Retryable` + `@Transactional`

**개념.** `@Retryable`(Spring Retry)은 예외 발생 시 메서드를 **재시도**합니다.
같은 메서드(빈)에 `@Transactional`이 함께 있으면 **트랜잭션이 재시도보다 안쪽**에 놓입니다.

순서를 그림으로 보면:

```
@Retryable (바깥) ──> @Transactional (안쪽) ──> 메서드 본문
```

예외가 나면 **안쪽 트랜잭션이 먼저 끝나고(커밋/롤백)**, 그 다음 바깥 재시도가 돕니다.
재시도는 **새 트랜잭션**으로 다시 시작되므로, 부수효과(예: 잔액 차감)가 **매 시도마다 반복**됩니다.
3번 재시도 = 3번 차감.

**❌ 나쁜 예시**
```java
@Retryable
@Transactional   // ⚠️ 재시도가 트랜잭션을 감쌈 → 부수효과 반복 위험
public void charge(Long accountId, long amount) {
    balanceRepository.deduct(accountId, amount);
    gateway.call();   // 실패하면 → 전체가 재시도됨
}
```

**✅ 좋은 예시 — 빈을 분리 (바깥=재시도, 안쪽=트랜잭션)**
```java
@Service
public class PaymentService {
    private final PaymentTxService tx;

    @Retryable   // ✅ 바깥 빈: 재시도 담당
    public void charge(Long accountId, long amount) {
        tx.charge(accountId, amount);
    }
}

@Service
public class PaymentTxService {
    @Transactional   // ✅ 안쪽 빈: 재시도마다 깨끗한 트랜잭션 경계
    public void charge(Long accountId, long amount) {
        balanceRepository.deduct(accountId, amount);
        gateway.call();
    }
}
```

> 💡 `@Retryable`은 `org.springframework.retry:spring-retry` 의존성이 필요합니다.

---

## 한눈에 보는 요약표

| # | 안티패턴 | 핵심 원인 | 인스펙션 클래스 | 설정 키 |
|---|---|---|---|---|
| ① | 같은 클래스 내부 호출 | 프록시 우회 | `TransactionalMethodCallInspection` | `enableSameClassCallDetection` |
| ② | private/final/static | 프록시가 오버라이드 불가 | `InvalidTransactionalMethodInspection` | `enablePrivate/Final/StaticMethodDetection` |
| ③ | checked 예외 + rollbackFor 없음 | 기본은 RuntimeException만 롤백 | `CheckedExceptionRollbackInspection` | `enableCheckedExceptionRollbackDetection` |
| ④ | 예외 삼킴 | 정상 종료로 간주 → 커밋 | `SwallowedExceptionInspection` | `enableSwallowedExceptionDetection` |
| ⑤ | 전파 충돌 (MANDATORY/NEVER/REQUIRES_NEW) | 전파 조건 불일치 | `TransactionalPropagationConflictInspection` | `enablePropagationConflictDetection` |
| ⑥ | readOnly가 쓰기 메서드 호출 | readOnly 트랜잭션에 합류 | `ReadOnlyTransactionWriteCallInspection` | `enableReadOnlyWriteCallDetection` |
| ⑦ | readOnly 안에서 직접 쓰기 | 선언과 코드 모순 | `ReadOnlyTransactionalInspection` | `enableReadOnlyTransactionalDetection` |
| ⑧ | N+1 쿼리 | 반복 중 지연 로딩 접근 | `NPlusOneQueryInspection` | `enableN1Detection` |
| ⑨ | 트랜잭션 내 외부 호출 | 커넥션 장기 점유 | `ExternalCallInTransactionInspection` | `enableExternalCallDetection` |
| ⑩ | `@Async` + `@Transactional` | 트랜잭션이 스레드 넘어 전파 안 됨 | `AsyncTransactionalConflictInspection` | `enableAsyncTransactionalDetection` |
| ⑪ | `@Retryable` + `@Transactional` | 재시도가 트랜잭션을 감쌈 | `RetryableTransactionalConflictInspection` | `enableRetryableTransactionalDetection` |

---

### 가장 중요한 한 문장

> **`@Transactional`은 "외부에서 프록시를 통해 호출된 public 메서드"에서만 제대로 동작한다.**
> 나머지는 대부분 여기서 파생되는 예외 상황이다.

설정은 **Settings → Tools → Spring Transaction Inspector** 에서 항목별로 켜고 끌 수 있습니다.
