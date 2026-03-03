# Java Concurrency 심화

멀티스레드 환경에서 필수적인 `ThreadLocal`과 `CountDownLatch`의 동작 원리와 실무 활용법을 학습합니다.

## 1. ThreadLocal
스레드별로 독립적인 변수를 저장할 수 있는 공간입니다.
*   **원리**: `Thread` 클래스 내부에 `ThreadLocalMap`이라는 맵이 있고, 여기에 데이터를 저장합니다.
*   **활용**:
    *   Spring Security: `SecurityContextHolder` (인증 정보 저장)
    *   Spring MVC: `RequestContextHolder` (요청 정보 저장)
    *   Transaction: `TransactionSynchronizationManager` (커넥션 동기화)
*   **주의사항 (Memory Leak)**:
    *   스레드 풀을 사용하는 환경(Tomcat 등)에서는 스레드가 재사용됩니다.
    *   따라서 작업이 끝난 후 `ThreadLocal.remove()`를 호출하여 데이터를 지워주지 않으면, **다음 요청에서 이전 사용자의 데이터가 남아있는 심각한 보안 문제**가 발생할 수 있습니다.

## 2. ThreadLocal의 종류 (Variants)
1.  **`ThreadLocal` (기본)**
    *   현재 스레드에서만 접근 가능. 자식 스레드도 접근 불가.
2.  **`InheritableThreadLocal`**
    *   부모 스레드가 생성한 값을 자식 스레드(`new Thread()`)가 상속받음.
    *   **한계**: 스레드 풀처럼 스레드를 재사용하는 환경에서는, 부모의 값이 바뀌어도 자식(재사용된 스레드)은 **처음 생성될 때 복사받은 옛날 값**을 그대로 가지고 있음. (데이터 불일치 발생)
3.  **`TransmittableThreadLocal` (TTL)**
    *   Alibaba 오픈소스 라이브러리.
    *   스레드 풀 환경에서도 부모의 값을 자식에게 안전하게 전달(데코레이팅)해줌. 실무에서 비동기 추적(Tracing) 시 필수.

## 3. CountDownLatch
여러 스레드가 작업을 마칠 때까지 대기하는 동기화 도구입니다.
*   **동작**:
    1.  `new CountDownLatch(N)`으로 카운트 설정.
    2.  각 스레드가 작업 완료 후 `latch.countDown()` 호출.
    3.  대기하는 스레드(Main)는 `latch.await()`에서 멈춰 있다가, 카운트가 0이 되면 깨어남.
*   **활용**:
    *   병렬 데이터 처리 후 취합 (Scatter-Gather 패턴)
    *   서버 시작 시 여러 초기화 작업이 모두 끝날 때까지 대기

## 4. 레이스 컨디션과 동기화

### 레이스 컨디션 (Race Condition)

여러 스레드가 공유 자원에 동시 접근할 때, 실행 순서에 따라 결과가 달라지는 문제입니다.

```java
// ❌ 비원자적 연산: read → increment → write 3단계로 분리됨
private int count = 0;
public void increment() { count++; }
```

```
Thread-A: read(0) ─────────────────── increment → write(1)
Thread-B:          read(0) → increment → write(1)
기대값: 2,  실제값: 1  ← 갱신 손실(Lost Update) 발생
```

### synchronized vs ReentrantLock

```java
// 방식 1: synchronized - 간단하지만 타임아웃/인터럽트 제어 불가
public synchronized void increment() { count++; }

// 방식 2: ReentrantLock - 타임아웃, 인터럽트, 공정성 지원
private final ReentrantLock lock = new ReentrantLock();

public void increment() {
    if (lock.tryLock(100, TimeUnit.MILLISECONDS)) {
        try {
            count++;
        } finally {
            lock.unlock(); // 반드시 unlock (try-finally 필수)
        }
    }
}
```

| 항목 | synchronized | ReentrantLock |
|------|-------------|---------------|
| 타임아웃 | ❌ | ✅ `tryLock(timeout)` |
| 인터럽트 지원 | ❌ | ✅ `lockInterruptibly()` |
| 공정성(Fairness) | ❌ | ✅ `new ReentrantLock(true)` |
| 조건 변수 | ❌ | ✅ `Condition` 객체 |
| 자동 해제 | ✅ | ❌ unlock 수동 필수 |

> **실무 팁:** 단순 상호 배제는 `synchronized`, 타임아웃/다중 조건이 필요하면 `ReentrantLock`

### volatile과 AtomicInteger

```java
// volatile: 가시성(Visibility) 보장 → CPU 캐시 대신 메인 메모리 직접 읽기
// 단, 원자성은 보장되지 않음 → 단순 플래그에만 적합
private volatile boolean running = true;

// AtomicInteger: 가시성 + 원자성 모두 보장 (CAS 기반)
private AtomicInteger count = new AtomicInteger(0);
count.incrementAndGet(); // read-modify-write를 하나의 원자적 연산으로
```

### 동기화 도구 비교

| 도구 | 재사용 | 주요 용도 |
|------|--------|----------|
| `CountDownLatch` | ❌ 1회용 | N개 작업 완료 후 다음 단계 진행 |
| `CyclicBarrier` | ✅ | N개 스레드가 특정 지점에서 동시 출발 |
| `Semaphore` | ✅ | 동시 접근 수 제한 (DB 커넥션 풀 모사) |
| `Phaser` | ✅ | 여러 단계(Phase)를 가진 병렬 작업 |
| `ReentrantLock` | ✅ | synchronized 대안, 고급 제어 |

```java
// Semaphore 예시: 동시에 3개 스레드만 허용
Semaphore semaphore = new Semaphore(3);
semaphore.acquire(); // 허가 획득 (없으면 대기)
try {
    doWork();
} finally {
    semaphore.release(); // 반드시 반환
}
```

---

## 5. 실무 패턴 및 안티패턴

### ✅ ThreadLocal 올바른 패턴: try-finally로 항상 정리

```java
// UserContextHolder.java (본 모듈 구현)
public void handleRequest(String userId) {
    try {
        UserContextHolder.set(userId);
        doBusinessLogic();
    } finally {
        UserContextHolder.remove(); // 스레드 풀 재사용 환경에서 필수
    }
}
```

### ❌ 스레드 풀 환경에서 ThreadLocal 미정리 → 보안 취약점

```
요청 1 (userA) → Thread-1: set("userA") → 작업 → [정리 없음]
요청 2 (userB) → Thread-1 재사용: get() → "userA"  ← 이전 유저 데이터 노출!
```

Spring의 `SecurityContextHolder`, `RequestContextHolder`가 모두 `try-finally`로 정리하는 이유가 이 때문입니다.

### ThreadLocal 변종 선택 기준

| 상황 | 권장 도구 |
|------|----------|
| 단순 스레드별 격리 | `ThreadLocal` |
| `new Thread()` 자식 스레드에 값 전달 | `InheritableThreadLocal` |
| 스레드 풀(@Async, ExecutorService) 환경에서 전파 | `TransmittableThreadLocal` (Alibaba TTL) |
| 분산 추적 MDC 전파 | TTL 필수 |

---

## 6. 실습 포인트
### ConcurrencyRunner
*   `ThreadLocal`의 격리성과 `CountDownLatch`를 이용한 동기화를 확인합니다.

### ThreadLocalVariantRunner
*   `InheritableThreadLocal`이 `new Thread()`에서는 잘 동작하지만, **스레드 풀(`Pool-2`)에서는 부모의 변경된 값을 반영하지 못하는 문제**를 확인합니다.
