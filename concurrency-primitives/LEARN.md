# Concurrency Primitives: volatile & Atomic

Java 동시성 프로그래밍의 핵심 원시 타입인 `volatile`과 `Atomic` 클래스들을 깊이 있게 학습하는 모듈입니다.

## 목차

1. [개요](#개요)
2. [Java Memory Model (JMM)](#java-memory-model-jmm)
3. [volatile 키워드](#volatile-키워드)
4. [Atomic 클래스](#atomic-클래스)
5. [synchronized vs volatile vs Atomic](#synchronized-vs-volatile-vs-atomic)
6. [실전 사용 가이드](#실전-사용-가이드)
7. [성능 고려사항](#성능-고려사항)

---

## 개요

멀티스레드 환경에서 데이터 일관성과 가시성을 보장하는 것은 매우 중요합니다. Java는 이를 위해 여러 메커니즘을 제공합니다:

- **synchronized**: 가장 전통적이고 강력한 동기화 (무거움)
- **volatile**: 가시성만 보장하는 가벼운 메커니즘
- **Atomic 클래스**: Lock-free 알고리즘 기반의 원자적 연산

이 모듈에서는 `volatile`과 `Atomic`에 집중합니다.

---

## Java Memory Model (JMM)

### 문제: CPU 캐시와 메모리 가시성

```
[Thread 1]          [Thread 2]
   CPU1               CPU2
    ↓                  ↓
  Cache1            Cache2
    ↓                  ↓
       Main Memory
```

- 각 CPU는 자체 캐시를 가짐
- 한 스레드가 변수를 변경해도 다른 스레드는 캐시된 값을 읽을 수 있음
- 컴파일러/CPU는 성능을 위해 명령어를 재배치할 수 있음

### 세 가지 핵심 개념

1. **원자성 (Atomicity)**
   - 연산이 중단 없이 완전히 수행되거나 전혀 수행되지 않음
   - 예: `i++`는 원자적이지 않음 (읽기 → 증가 → 쓰기)

2. **가시성 (Visibility)**
   - 한 스레드의 변경사항이 다른 스레드에게 보이는지
   - CPU 캐시로 인해 최신 값을 못 볼 수 있음

3. **순서성 (Ordering)**
   - 명령어가 프로그램 순서대로 실행되는지
   - 컴파일러/CPU 최적화로 재배치될 수 있음

### Happens-Before 관계

JMM은 "happens-before" 관계를 정의하여 메모리 가시성을 보장합니다:

- **volatile 변수 쓰기** happens-before **해당 변수 읽기**
- **모니터 unlock** happens-before **해당 모니터 lock**
- **스레드 start()** happens-before **해당 스레드의 모든 작업**
- **스레드의 모든 작업** happens-before **해당 스레드 join() 완료**

---

## volatile 키워드

### 핵심 특징

```java
private volatile boolean flag = false;
private volatile int counter = 0;
```

#### 1. 가시성 보장

```java
// 스레드 1
flag = true;  // 메인 메모리에 즉시 쓰기

// 스레드 2
if (flag) {   // 메인 메모리에서 읽기 (항상 최신 값)
    // ...
}
```

- CPU 캐시를 거치지 않고 메인 메모리에서 직접 읽기/쓰기
- 한 스레드의 변경사항이 다른 스레드에게 즉시 보임

#### 2. 재배치 방지

```java
// volatile이 메모리 배리어 역할
int a = 1;
int b = 2;
volatile boolean ready = false;

// 컴파일러는 ready = false를 a, b 할당 위로 이동시킬 수 없음
```

#### 3. 원자성 보장 안함 (복합 연산)

```java
private volatile int counter = 0;

// 스레드 안전하지 않음!
public void increment() {
    counter++;  // 읽기 → 증가 → 쓰기 (3단계)
}
```

- **단순 읽기/쓰기**: 원자적 ✅
- **복합 연산** (++, --, +=): 원자적 아님 ❌

### volatile 사용 시나리오

#### ✅ 적합한 경우

##### 1. 상태 플래그

```java
private volatile boolean running = true;

public void run() {
    while (running) {
        // 작업 수행
    }
}

public void stop() {
    running = false;  // 다른 스레드가 즉시 볼 수 있음
}
```

##### 2. 읽기가 훨씬 많은 경우

```java
private volatile Configuration config;

// 여러 스레드가 읽기
public Configuration getConfig() {
    return config;
}

// 한 스레드만 쓰기
public void updateConfig(Configuration newConfig) {
    config = newConfig;
}
```

##### 3. Double-Checked Locking

```java
private static volatile Singleton instance;

public static Singleton getInstance() {
    if (instance == null) {
        synchronized (Singleton.class) {
            if (instance == null) {
                instance = new Singleton();
            }
        }
    }
    return instance;
}
```

##### 4. long/double의 원자적 읽기/쓰기

```java
// 64비트 변수는 volatile 없이는 2번의 32비트 연산으로 나뉠 수 있음
private volatile long timestamp;
private volatile double price;
```

#### ❌ 부적합한 경우

##### 1. 복합 연산

```java
private volatile int counter = 0;

// 스레드 안전하지 않음!
public void increment() {
    counter++;  // 여러 단계로 나뉨
}
```

**해결**: `AtomicInteger` 사용

##### 2. 여러 변수의 일관성

```java
private volatile int balance;
private volatile int transactions;

public void deposit(int amount) {
    balance += amount;      // 1단계
    transactions++;         // 2단계
    // 1단계와 2단계 사이에 다른 스레드가 읽을 수 있음!
}
```

**해결**: `synchronized` 사용

### volatile의 happens-before 효과

```java
class Example {
    private int normalVar = 0;
    private volatile boolean ready = false;

    // 스레드 1
    public void writer() {
        normalVar = 42;    // 1
        ready = true;      // 2 (volatile 쓰기)
    }

    // 스레드 2
    public int reader() {
        if (ready) {       // 3 (volatile 읽기)
            return normalVar;  // 4 - 항상 42를 봄!
        }
        return -1;
    }
}
```

**happens-before 체인**:
- (1) → (2): 프로그램 순서
- (2) → (3): volatile 쓰기 happens-before 읽기
- (3) → (4): 프로그램 순서

따라서 (1) → (4)가 보장되어, `normalVar`의 변경사항이 보임!

---

## Atomic 클래스

### 핵심 특징

Atomic 클래스는 **CAS (Compare-And-Swap)** 연산을 기반으로 **Lock-free** 알고리즘을 제공합니다.

```java
// CAS 의사 코드
boolean compareAndSet(expectedValue, newValue) {
    if (currentValue == expectedValue) {
        currentValue = newValue;
        return true;
    }
    return false;
}
```

#### CAS의 특징
- **원자적**: CPU 레벨에서 단일 명령어로 수행
- **Lock-free**: 락을 획득하지 않음
- **재시도 기반**: 실패 시 다시 시도 (스핀)

### 주요 Atomic 클래스

#### 1. AtomicInteger / AtomicLong

```java
AtomicInteger counter = new AtomicInteger(0);

// 기본 연산
counter.incrementAndGet();  // ++i
counter.getAndIncrement();  // i++
counter.decrementAndGet();  // --i
counter.addAndGet(5);       // i += 5
counter.getAndSet(10);      // 기존 값 반환 후 설정

// CAS 연산
counter.compareAndSet(10, 20);  // 10이면 20으로 변경

// 함수형 업데이트 (Java 8+)
counter.updateAndGet(x -> x * 2);  // 배로 증가
counter.accumulateAndGet(5, (x, y) -> x + y);  // 5 더하기
```

**사용 시나리오**:
- 카운터, 시퀀스 생성기
- 통계 수집 (요청 수, 에러 수 등)
- ID 생성

#### 2. AtomicBoolean

```java
AtomicBoolean initialized = new AtomicBoolean(false);

// 한 번만 실행되는 초기화
if (initialized.compareAndSet(false, true)) {
    // 초기화 로직 (딱 한 번만 실행됨)
}
```

**사용 시나리오**:
- 초기화 플래그
- 토글 스위치
- 작업 완료 표시

#### 3. AtomicReference

```java
AtomicReference<User> currentUser = new AtomicReference<>(new User("Unknown"));

// 객체 교체
currentUser.set(new User("Alice"));

// CAS
User expected = currentUser.get();
User newUser = new User("Bob");
boolean success = currentUser.compareAndSet(expected, newUser);

// 함수형 업데이트
currentUser.updateAndGet(user -> new User(user.name, user.age + 1));
```

**사용 시나리오**:
- 불변 객체의 원자적 교체
- 공유 상태 관리
- Lock-free 자료구조

#### 4. AtomicStampedReference - ABA 문제 해결

**ABA 문제**: 값이 A → B → A로 변경되면 CAS는 이를 감지 못함

```java
AtomicStampedReference<Account> accountRef =
    new AtomicStampedReference<>(account, 0);  // 초기 stamp = 0

int[] stampHolder = new int[1];
Account current = accountRef.get(stampHolder);
int currentStamp = stampHolder[0];

// stamp를 증가시키며 업데이트
boolean success = accountRef.compareAndSet(
    current, newAccount,
    currentStamp, currentStamp + 1
);
```

**사용 시나리오**:
- ABA 문제가 중요한 경우
- 버전 관리가 필요한 경우

#### 5. AtomicMarkableReference

```java
AtomicMarkableReference<Task> taskRef =
    new AtomicMarkableReference<>(null, false);

// 작업 할당
taskRef.compareAndSet(null, task, false, false);

// 작업 완료 표시
taskRef.compareAndSet(task, task, false, true);  // mark를 true로

// 완료 여부 확인
boolean completed = taskRef.isMarked();
```

**사용 시나리오**:
- boolean 플래그와 함께 관리할 때
- 완료/삭제 표시

#### 6. AtomicIntegerArray / AtomicLongArray / AtomicReferenceArray

```java
AtomicIntegerArray counters = new AtomicIntegerArray(10);

// 특정 인덱스 증가
counters.incrementAndGet(5);

// CAS
counters.compareAndSet(5, 10, 20);
```

**사용 시나리오**:
- 분산 카운터 배열
- 버킷별 통계
- 인덱스 기반 동시 처리

#### 7. LongAdder / DoubleAdder - 고성능 누산기

```java
LongAdder adder = new LongAdder();

// 여러 스레드에서 동시에 증가
adder.increment();
adder.add(5);

// 전체 합계 (모든 내부 셀의 합)
long sum = adder.sum();
```

**AtomicLong vs LongAdder**:

| 항목 | AtomicLong | LongAdder |
|------|-----------|-----------|
| 내부 구조 | 단일 값 | 여러 셀로 분산 |
| 낮은 경합 | 빠름 | 비슷 |
| 높은 경합 | CAS 재시도 많음 | 매우 빠름 |
| 메모리 | 적음 | 많음 (여러 셀) |
| get() | O(1) | O(셀 개수) |

**사용 시나리오**:
- 높은 경합이 예상되는 카운터
- 실시간 메트릭 수집
- 처리량 측정

#### 8. LongAccumulator / DoubleAccumulator - 일반화된 누산기

```java
// 최댓값 추적
LongAccumulator maxTracker = new LongAccumulator(Long::max, Long.MIN_VALUE);
maxTracker.accumulate(10);
maxTracker.accumulate(25);
maxTracker.accumulate(15);
long max = maxTracker.get();  // 25

// 최솟값 추적
LongAccumulator minTracker = new LongAccumulator(Long::min, Long.MAX_VALUE);

// 곱셈 누적
LongAccumulator product = new LongAccumulator((x, y) -> x * y, 1);
```

**사용 시나리오**:
- 최댓값/최솟값 추적
- 커스텀 누적 연산 (곱셈, 비트 연산 등)
- 통계 수집

### Atomic 클래스의 내부 동작

#### CAS 루프 예시

```java
public final int incrementAndGet() {
    for (;;) {
        int current = get();
        int next = current + 1;
        if (compareAndSet(current, next)) {
            return next;
        }
        // 실패 시 재시도 (다른 스레드가 값을 변경함)
    }
}
```

#### 하드웨어 지원

- x86: `CMPXCHG` 명령어
- ARM: `LDREX/STREX` 명령어
- CPU 레벨에서 원자성 보장

---

## synchronized vs volatile vs Atomic

### 기능 비교

| 항목 | synchronized | volatile | Atomic |
|------|-------------|----------|--------|
| **원자성** | ✅ 모든 연산 | ❌ 단순 읽기/쓰기만 | ✅ CAS 기반 연산 |
| **가시성** | ✅ | ✅ | ✅ |
| **순서성** | ✅ | ✅ (배리어) | ✅ |
| **락** | 필요 (모니터 락) | 불필요 | 불필요 (Lock-free) |
| **블로킹** | 다른 스레드 블로킹 | 없음 | 없음 (스핀) |
| **성능** | 낮음~중간 | 높음 | 중간~높음 |
| **복잡한 임계 영역** | ✅ | ❌ | ❌ (단일 변수만) |
| **여러 변수 일관성** | ✅ | ❌ | ❌ |
| **wait/notify** | ✅ | ❌ | ❌ |

### 시나리오별 선택 가이드

#### 1. 단순 플래그 (boolean)

```java
// ✅ 최선: volatile (가장 가볍고 충분함)
private volatile boolean running = true;

// ⚠️ 과도함: synchronized
private boolean running = true;
public synchronized void setRunning(boolean value) { running = value; }

// ⚠️ CAS 필요 없으면 과도: AtomicBoolean
private AtomicBoolean running = new AtomicBoolean(true);
```

**선택**: **volatile**

---

#### 2. 카운터 (증가/감소)

```java
// ❌ 불안전: volatile
private volatile int counter = 0;
public void increment() { counter++; }  // 스레드 안전하지 않음!

// ✅ 안전하지만 느림: synchronized
private int counter = 0;
public synchronized void increment() { counter++; }

// ✅ 최선: Atomic (안전하고 빠름)
private AtomicInteger counter = new AtomicInteger(0);
public void increment() { counter.incrementAndGet(); }
```

**선택**: **Atomic**

---

#### 3. 여러 변수의 일관성

```java
// ❌ 일관성 보장 안됨: Atomic
private AtomicInteger balance = new AtomicInteger(0);
private AtomicInteger transactions = new AtomicInteger(0);

public void deposit(int amount) {
    balance.addAndGet(amount);
    // ⚠️ 다른 스레드가 여기서 읽으면 일관성 깨짐!
    transactions.incrementAndGet();
}

// ✅ 최선: synchronized (일관성 보장)
private int balance = 0;
private int transactions = 0;

public synchronized void deposit(int amount) {
    balance += amount;
    transactions++;
    // 두 변수가 원자적으로 업데이트됨
}
```

**선택**: **synchronized**

---

#### 4. 읽기 >> 쓰기 (설정 값)

```java
// ✅ 최선: volatile (읽기 성능 최고)
private volatile Configuration config;

public Configuration getConfig() {
    return config;  // 여러 스레드가 자주 읽음
}

public void updateConfig(Configuration newConfig) {
    config = newConfig;  // 가끔 업데이트
}
```

**선택**: **volatile**

---

#### 5. 높은 경합의 카운터

```java
// ⚠️ 높은 경합 시 CAS 재시도 많음: AtomicLong
private AtomicLong counter = new AtomicLong(0);

// ✅ 최선: LongAdder (내부적으로 분산)
private LongAdder counter = new LongAdder();

public void increment() {
    counter.increment();  // 여러 셀에 분산
}

public long getTotal() {
    return counter.sum();  // 모든 셀의 합
}
```

**선택**: **LongAdder**

---

#### 6. CAS 기반 로직 (Lock-free)

```java
// ✅ Atomic의 CAS 활용
private AtomicInteger connectionCount = new AtomicInteger(0);
private static final int MAX_CONNECTIONS = 10;

public boolean tryAcquireConnection() {
    int current = connectionCount.get();
    while (current < MAX_CONNECTIONS) {
        if (connectionCount.compareAndSet(current, current + 1)) {
            return true;  // 획득 성공
        }
        current = connectionCount.get();  // 재시도
    }
    return false;  // 최대 연결 수 도달
}
```

**선택**: **Atomic**

---

### 성능 비교 (상대적)

#### 낮은 경합 (2-4 스레드)

```
volatile (읽기) >> Atomic > synchronized
```

#### 높은 경합 (10+ 스레드)

```
LongAdder > Atomic > synchronized
```

#### 복잡한 임계 영역

```
synchronized (단순하고 안전함)
```

---

## 실전 사용 가이드

### 1. 상태 플래그 - volatile

```java
@Service
public class BackgroundWorker {
    private volatile boolean running = false;

    public void start() {
        if (running) return;

        running = true;
        new Thread(() -> {
            while (running) {
                // 작업 수행
                processTask();
            }
        }).start();
    }

    public void stop() {
        running = false;  // Worker 스레드가 즉시 인지
    }
}
```

---

### 2. 요청 카운터 - AtomicLong

```java
@Component
public class RequestMetrics {
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);

    public void recordRequest(boolean success) {
        totalRequests.incrementAndGet();
        if (!success) {
            failedRequests.incrementAndGet();
        }
    }

    public double getFailureRate() {
        long total = totalRequests.get();
        if (total == 0) return 0.0;
        return (double) failedRequests.get() / total * 100;
    }
}
```

---

### 3. 캐시 초기화 - AtomicBoolean

```java
@Service
public class CacheService {
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private volatile Map<String, Object> cache;

    public void ensureInitialized() {
        if (initialized.compareAndSet(false, true)) {
            // 딱 한 번만 실행됨
            cache = loadCacheFromDatabase();
            log.info("Cache initialized");
        }
    }

    public Object get(String key) {
        ensureInitialized();
        return cache.get(key);
    }
}
```

---

### 4. 연결 풀 관리 - AtomicInteger

```java
public class ConnectionPool {
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final int maxConnections;

    public Connection acquire() {
        int current = activeConnections.get();
        while (current < maxConnections) {
            if (activeConnections.compareAndSet(current, current + 1)) {
                return createConnection();
            }
            current = activeConnections.get();
        }
        throw new ConnectionPoolExhaustedException();
    }

    public void release(Connection conn) {
        conn.close();
        activeConnections.decrementAndGet();
    }
}
```

---

### 5. 고성능 메트릭 - LongAdder

```java
@Component
public class HighThroughputMetrics {
    private final LongAdder requestCount = new LongAdder();
    private final LongAdder errorCount = new LongAdder();

    // 여러 스레드에서 동시 호출 (높은 경합)
    public void recordRequest() {
        requestCount.increment();
    }

    public void recordError() {
        errorCount.increment();
    }

    @Scheduled(fixedRate = 1000)
    public void reportMetrics() {
        long requests = requestCount.sumThenReset();
        long errors = errorCount.sumThenReset();
        log.info("Requests: {}, Errors: {}", requests, errors);
    }
}
```

---

### 6. 설정 관리 - volatile

```java
@Component
public class ConfigurationManager {
    private volatile Configuration config;

    @PostConstruct
    public void init() {
        config = loadConfiguration();
    }

    // 여러 스레드가 자주 읽음 (읽기 성능 최고)
    public Configuration getConfig() {
        return config;
    }

    // 관리자만 가끔 업데이트
    public void updateConfig(Configuration newConfig) {
        config = newConfig;  // volatile 쓰기 (모든 스레드가 즉시 봄)
    }
}
```

---

### 7. 통계 수집 - LongAccumulator

```java
@Component
public class ResponseTimeTracker {
    private final LongAccumulator maxResponseTime =
        new LongAccumulator(Long::max, 0);
    private final LongAccumulator minResponseTime =
        new LongAccumulator(Long::min, Long.MAX_VALUE);

    public void recordResponseTime(long timeMs) {
        maxResponseTime.accumulate(timeMs);
        minResponseTime.accumulate(timeMs);
    }

    public long getMaxResponseTime() {
        return maxResponseTime.get();
    }

    public long getMinResponseTime() {
        long min = minResponseTime.get();
        return min == Long.MAX_VALUE ? 0 : min;
    }
}
```

---

## 성능 고려사항

### 1. 경합 (Contention)

**낮은 경합** (2-4 스레드):
- `Atomic` > `synchronized`
- CAS 재시도가 적음

**높은 경합** (10+ 스레드):
- `LongAdder` > `Atomic` > `synchronized`
- Atomic은 CAS 재시도 증가로 성능 저하
- synchronized는 락 대기로 성능 저하

### 2. 메모리 오버헤드

```
volatile (0) < Atomic (객체) < LongAdder (여러 셀)
```

- **volatile**: 추가 오버헤드 없음
- **Atomic**: 객체 생성 비용
- **LongAdder**: 여러 내부 셀 생성 (메모리 많이 사용)

### 3. False Sharing 문제

```java
// ❌ False Sharing 발생 가능
class Counters {
    private AtomicLong counter1 = new AtomicLong();
    private AtomicLong counter2 = new AtomicLong();  // 같은 캐시 라인에 있을 수 있음
}

// ✅ 패딩으로 분리
class Counters {
    private AtomicLong counter1 = new AtomicLong();
    private long p1, p2, p3, p4, p5, p6, p7, p8;  // 패딩
    private AtomicLong counter2 = new AtomicLong();
}

// ✅ Java 8+: @Contended 사용
class Counters {
    @sun.misc.Contended
    private AtomicLong counter1 = new AtomicLong();

    @sun.misc.Contended
    private AtomicLong counter2 = new AtomicLong();
}
```

### 4. 스핀 vs 블로킹

**Atomic (스핀)**:
- CAS 실패 시 즉시 재시도
- CPU 사용량 증가
- 짧은 대기에 유리

**synchronized (블로킹)**:
- 락 획득 실패 시 대기 상태로 전환
- CPU 사용량 감소
- 긴 대기에 유리

---

## 일반적인 실수와 해결

### 실수 1: volatile로 복합 연산

```java
// ❌ 잘못된 코드
private volatile int counter = 0;
public void increment() {
    counter++;  // NOT thread-safe!
}

// ✅ 올바른 코드
private AtomicInteger counter = new AtomicInteger(0);
public void increment() {
    counter.incrementAndGet();
}
```

---

### 실수 2: Atomic으로 여러 변수 일관성

```java
// ❌ 잘못된 코드
private AtomicInteger x = new AtomicInteger(0);
private AtomicInteger y = new AtomicInteger(0);

public void update() {
    x.incrementAndGet();
    // 다른 스레드가 여기서 읽으면 일관성 깨짐!
    y.incrementAndGet();
}

// ✅ 올바른 코드 1: synchronized
private int x = 0, y = 0;
public synchronized void update() {
    x++;
    y++;
}

// ✅ 올바른 코드 2: 불변 객체 + AtomicReference
record Point(int x, int y) {}
private AtomicReference<Point> point = new AtomicReference<>(new Point(0, 0));
public void update() {
    point.updateAndGet(p -> new Point(p.x + 1, p.y + 1));
}
```

---

### 실수 3: volatile 없는 DCL

```java
// ❌ 잘못된 코드 (부분 초기화 문제)
private static Singleton instance;
public static Singleton getInstance() {
    if (instance == null) {
        synchronized (Singleton.class) {
            if (instance == null) {
                instance = new Singleton();  // 재배치 가능!
            }
        }
    }
    return instance;
}

// ✅ 올바른 코드
private static volatile Singleton instance;  // volatile 필수!
```

---

### 실수 4: get() 후 비교 후 set()

```java
// ❌ 잘못된 코드 (경합 조건)
AtomicInteger counter = new AtomicInteger(0);
if (counter.get() < 10) {
    counter.incrementAndGet();  // 다른 스레드가 이미 증가시켰을 수 있음
}

// ✅ 올바른 코드 (CAS 루프)
int current;
do {
    current = counter.get();
    if (current >= 10) break;
} while (!counter.compareAndSet(current, current + 1));
```

---

## 학습 체크리스트

- [ ] JMM의 3가지 핵심 개념 (원자성, 가시성, 순서성) 이해
- [ ] happens-before 관계 이해
- [ ] volatile의 특징과 한계 이해
- [ ] volatile 사용 시나리오 파악
- [ ] CAS 알고리즘 동작 원리 이해
- [ ] AtomicInteger/Long/Boolean 사용법 숙지
- [ ] AtomicReference 활용법 이해
- [ ] LongAdder vs AtomicLong 차이점 이해
- [ ] synchronized vs volatile vs Atomic 비교
- [ ] 시나리오별 적절한 메커니즘 선택 가능

---

## 참고 자료

- **Java Concurrency in Practice** (Brian Goetz)
- [Java Memory Model (JSR-133)](https://www.cs.umd.edu/~pugh/java/memoryModel/)
- [Doug Lea's Home Page](http://gee.cs.oswego.edu/) (java.util.concurrent 창시자)
- [The JSR-133 Cookbook](http://gee.cs.oswego.edu/dl/jmm/cookbook.html)
- [OpenJDK Atomic 구현](https://github.com/openjdk/jdk/tree/master/src/java.base/share/classes/java/util/concurrent/atomic)

---

## 테스트 실행

```bash
# 전체 테스트
./gradlew :concurrency-primitives:test

# volatile 테스트만
./gradlew :concurrency-primitives:test --tests VolatileExampleTest

# Atomic 테스트만
./gradlew :concurrency-primitives:test --tests AtomicExampleTest

# 비교 테스트
./gradlew :concurrency-primitives:test --tests ComparisonExampleTest

# 성능 비교 테스트
./gradlew :concurrency-primitives:test --tests "*performance*"
```

---

## 요약

### volatile
- **목적**: 가시성 보장
- **사용**: 상태 플래그, 읽기 위주 변수
- **장점**: 가장 가볍고 빠름
- **단점**: 복합 연산 불가

### Atomic
- **목적**: Lock-free 원자 연산
- **사용**: 카운터, CAS 로직
- **장점**: 안전하고 빠름 (중저 경합)
- **단점**: 높은 경합 시 CAS 재시도

### LongAdder
- **목적**: 고성능 누산
- **사용**: 높은 경합의 카운터
- **장점**: 높은 경합에서도 빠름
- **단점**: 메모리 오버헤드

### synchronized
- **목적**: 범용 동기화
- **사용**: 복잡한 임계 영역, 여러 변수
- **장점**: 모든 시나리오 지원
- **단점**: 무겁고 블로킹

**기억하세요**: "적절한 도구를 적절한 곳에!" 🎯

---

## 초보자를 위한 쉬운 설명

### Q1: i++는 왜 원자적이지 않나요?

`i++`는 겉으로 보기엔 한 줄이지만, CPU는 **3단계**로 나누어 실행합니다:

```java
int i = 0;
i++;  // 한 줄처럼 보이지만...

// 실제 CPU는 이렇게 3단계로 실행:
1. READ:   temp = i;      // 메모리에서 값 읽기 (0)
2. ADD:    temp = temp + 1; // 1 증가 (1)
3. WRITE:  i = temp;      // 메모리에 쓰기 (1)
```

**멀티스레드 환경에서의 문제**:

```
시간 →
Thread A: READ(0) → ADD(1) → WRITE(1)
Thread B:      READ(0) → ADD(1) → WRITE(1)
                  ↑
            A가 아직 WRITE 안 했는데 B가 읽어버림!

결과: i = 1 (기대값: 2)
```

**왜 이런가?**
- Thread A가 READ → ADD 하는 사이에
- Thread B가 끼어들어서 READ를 함
- 둘 다 0을 읽어서 1을 씀
- **2번 증가했는데 결과는 1!** (Lost Update)

**해결책**:
```java
// ❌ 스레드 안전하지 않음
private volatile int counter = 0;
public void increment() {
    counter++;  // 3단계로 나뉘므로 위험!
}

// ✅ 스레드 안전함 (CAS로 3단계를 원자적으로)
private AtomicInteger counter = new AtomicInteger(0);
public void increment() {
    counter.incrementAndGet();  // CPU 레벨에서 원자적!
}
```

---

### Q2: Happens-Before가 정확히 뭔가요?

**Happens-Before = "이 작업이 끝나면 그 결과를 다른 스레드가 볼 수 있다"는 보장**

#### 비유: 카톡 메시지

```
너: "치킨 주문했어" (메시지 전송)
      ↓ happens-before
친구: "오 좋아!" (메시지 읽음)
```

- 친구가 읽을 때는 **반드시 네가 보낸 메시지가 보임**
- 이게 happens-before 보장!

#### 프로그래밍에서는?

**문제가 있는 경우 (happens-before 없음)**:

```java
class Example {
    private int data = 0;
    private boolean ready = false;

    // 스레드 1
    public void writer() {
        data = 42;       // 1
        ready = true;    // 2
    }

    // 스레드 2
    public int reader() {
        if (ready) {     // 3
            return data; // 4 - 뭐가 나올까?
        }
        return -1;
    }
}
```

**예상**: `data`는 42가 나와야 함
**실제**: 0이 나올 수 있음! 😱

**왜?**
- CPU/컴파일러가 명령어 순서를 바꿀 수 있음
- 스레드 2가 `ready = true`는 봤는데, `data = 42`는 안 보일 수 있음
- CPU 캐시 때문에 스레드 2가 예전 값(0)을 읽을 수 있음

---

**해결책: volatile로 happens-before 만들기**

```java
class Example {
    private int data = 0;
    private volatile boolean ready = false;  // volatile!

    // 스레드 1
    public void writer() {
        data = 42;       // 1
        ready = true;    // 2 (volatile 쓰기)
    }

    // 스레드 2
    public int reader() {
        if (ready) {     // 3 (volatile 읽기)
            return data; // 4 - 항상 42!
        }
        return -1;
    }
}
```

**happens-before 체인**:
```
1. data = 42
     ↓ (프로그램 순서)
2. ready = true (volatile 쓰기)
     ↓ (volatile happens-before 규칙)
3. if (ready) (volatile 읽기)
     ↓ (프로그램 순서)
4. return data
```

**결과**:
- (1) happens-before (4)가 성립!
- 스레드 2가 `ready = true`를 보면
- 반드시 `data = 42`도 봄!

---

#### 실생활 비유

**happens-before 없음**:
```
카페 직원: "커피 만들었어요" (메모만 씀)
손님: (메모를 못 봐서) "아직 안 나왔는데요?"
```

**happens-before 있음 (volatile)**:
```
카페 직원: "커피 만들었어요" (진동벨 울림 📳)
손님: (진동벨 울려서) "아! 나왔네요!"
```

- volatile은 **진동벨** 같은 것
- 한 스레드가 쓰면 → 다른 스레드가 **반드시** 봄!

---

#### 핵심 정리

**Happens-Before를 한 문장으로**:
> "A happens-before B" = "A의 결과를 B가 볼 수 있다는 보장"

**예시**:
- `volatile 쓰기` happens-before `volatile 읽기`
  - 쓴 값을 읽을 때 반드시 봄
- `synchronized unlock` happens-before `synchronized lock`
  - 락 풀면 다음 락 잡는 쪽이 봄
- `Thread.start()` happens-before `새 스레드의 모든 작업`
  - start() 전 작업을 새 스레드가 봄

**왜 중요한가?**
- happens-before가 없으면 → 스레드가 옛날 값을 봄 (캐시 때문)
- happens-before가 있으면 → 최신 값을 보장

**기억하기**:
```
happens-before = "확실히 보인다" 보장
              = 메모리 가시성 보장
              = CPU 캐시 무효화 + 명령어 재배치 금지
```
