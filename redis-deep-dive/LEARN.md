# Redis Deep Dive: 분산 락과 랭킹 시스템

## 언제 사용하는가?

### ✅ Redis가 적합한 경우
1. **캐시 (Cache)**: DB 조회 결과를 메모리에 저장하여 응답 시간 단축
2. **세션 저장**: 분산 서버 환경에서 세션 공유 (Spring Session + Redis)
3. **분산 락**: 여러 서버 간 동시 접근 제어 (`RLock`)
4. **실시간 랭킹**: Sorted Set으로 O(log N) 랭킹 조회
5. **Rate Limiting**: 특정 시간 내 API 호출 수 제한

### ⚠️ 주의 사항
- **휘발성 데이터**: 기본적으로 재시작 시 데이터 소실 (AOF/RDB 설정으로 영속화 가능)
- **단일 스레드**: 명령어는 순차 실행 → 큰 컬렉션 연산 시 블로킹 발생
- **메모리 기반**: RAM 용량을 초과하면 eviction 정책 적용

---

## 1. Redis 자료구조

| 자료구조 | 내부 구조 | 주요 명령어 | 대표 사용 사례 |
|---------|----------|-----------|-------------|
| **String** | 단순 키-값 | `GET`, `SET`, `INCR` | 캐시, 카운터, 락 |
| **Hash** | 필드-값 맵 | `HGET`, `HSET`, `HGETALL` | 객체 저장 (User 정보 등) |
| **List** | 이중 연결 리스트 | `LPUSH`, `RPOP`, `LRANGE` | 메시지 큐, 최근 목록 |
| **Set** | 해시 테이블 | `SADD`, `SMEMBERS`, `SINTER` | 좋아요, 태그, 교집합 |
| **Sorted Set (ZSet)** | Skip List + Hash | `ZADD`, `ZREVRANGE`, `ZREVRANK` | 실시간 랭킹, 우선순위 큐 |
| **Bitmap** | String 비트 조작 | `SETBIT`, `GETBIT`, `BITCOUNT` | 출석 체크, DAU 집계 |
| **HyperLogLog** | 확률적 자료구조 | `PFADD`, `PFCOUNT` | 중복 제거 UV 집계 |

---

## 2. 분산 락 (Distributed Lock)

여러 서버가 동시에 실행되는 분산 환경에서 **"오직 하나의 프로세스만 특정 자원에 접근"**하도록 제어하는 기술입니다.

```
단일 서버: synchronized (JVM 내 동기화) ← 서버가 2대면 무용지물
분산 환경: Redis 분산 락 (서버 간 동기화) ✅
```

### SETNX 직접 구현 vs Redisson 비교

```java
// ❌ SETNX 직접 구현 - 스핀 락: Redis에 계속 물어봄 (CPU/Redis 부하)
while (true) {
    Boolean acquired = redisTemplate.opsForValue()
        .setIfAbsent("LOCK:key", "1", 3, TimeUnit.SECONDS);
    if (Boolean.TRUE.equals(acquired)) break;
    Thread.sleep(100); // 폴링 대기
}

// ✅ Redisson - Pub/Sub 방식: 락 해제 시 알림을 받아 대기 (효율적)
RLock lock = redissonClient.getLock("LOCK:key");
lock.tryLock(5, 3, TimeUnit.SECONDS); // waitTime, leaseTime
```

| 항목 | SETNX 직접 | Redisson |
|------|----------|---------|
| 대기 방식 | 스핀 락 (폴링) | Pub/Sub (이벤트 알림) |
| Redis 부하 | 높음 | 낮음 |
| 재진입성 | ❌ | ✅ ReentrantLock |
| watchdog (자동 연장) | ❌ | ✅ leaseTime=-1 시 활성화 |
| 구현 복잡도 | 높음 | 낮음 |

### 분산 락 동작 흐름 (본 모듈: @DistributedLock + AOP)

```
클라이언트 요청
    │
    ▼
@DistributedLock(key = "hot-deal-item-1") → AOP Intercept
    │
    ├─ 1. key 생성: "LOCK:hot-deal-item-1"
    ├─ 2. rLock.tryLock(waitTime=5s, leaseTime=3s)
    │       ├─ 성공: 락 획득, 메서드 실행
    │       └─ 실패 (5초 초과): return false (락 경합)
    │
    ├─ 3. joinPoint.proceed() → 비즈니스 로직 실행
    │
    └─ 4. finally: rLock.unlock() → Pub/Sub으로 대기 스레드 알림
```

```java
// 선언적 분산 락 (비즈니스 로직은 락을 몰라도 됨)
@DistributedLock(key = "hot-deal-item-1")
public void purchaseItem(String userId) {
    // 오직 1개 서버, 1개 스레드만 진입 보장
    log.info("Purchase logic - User: {}", userId);
}
```

---

## 3. 실시간 랭킹 (Sorted Set)

Redis의 **Sorted Set (ZSet)**은 `Score`로 자동 정렬되는 자료구조입니다.

### DB vs Redis 랭킹 성능 비교

| 구분 | DB (ORDER BY) | Redis ZSet |
|------|--------------|-----------|
| 조회 복잡도 | O(N log N) | O(log N) |
| 상위 N위 조회 | Full Scan 후 정렬 | `ZREVRANGE 0 N-1` |
| 점수 업데이트 | UPDATE + 재정렬 | `ZADD` (자동 정렬) |
| 10만 건 조회 | 수백 ms | 수 ms |

### 주요 명령어

```java
// 점수 추가/갱신 (O(log N))
redisTemplate.opsForZSet().add("game:leaderboard", userId, score);
// → ZADD game:leaderboard 300 userB

// 상위 N명 조회 (점수 높은 순, O(log N + M))
redisTemplate.opsForZSet().reverseRange("game:leaderboard", 0, limit - 1);
// → ZREVRANGE game:leaderboard 0 4

// 특정 유저 순위 조회 (0-based, O(log N))
redisTemplate.opsForZSet().reverseRank("game:leaderboard", userId);
// → ZREVRANK game:leaderboard userB → 0 (1위)
```

```
game:leaderboard (ZSet)
├─ userB → score: 300  (rank: 0, 1위)
├─ userC → score: 200  (rank: 1, 2위)
└─ userA → score: 100  (rank: 2, 3위)
```

---

## 4. 캐시 패턴

### Cache-Aside (Lazy Loading) — 가장 일반적

```
요청 → Redis 조회 → 있으면(Hit): 즉시 반환
                 → 없으면(Miss): DB 조회 → Redis 저장 → 반환
```

```java
public User getUser(Long id) {
    String key = "user:" + id;
    User cached = (User) redisTemplate.opsForValue().get(key);
    if (cached != null) return cached;                // Cache Hit

    User user = userRepository.findById(id).orElseThrow();
    redisTemplate.opsForValue().set(key, user, 30, TimeUnit.MINUTES); // TTL 설정
    return user;
}
```

### Write-Through — 쓰기 시 캐시도 동시에 갱신

```
쓰기 → DB 저장 + Redis 동시 저장 → 항상 최신 상태 유지
단점: 불필요한 데이터도 캐시됨 (읽히지 않는 데이터)
```

### Write-Behind (Write-Back) — 캐시 먼저 쓰고 DB는 나중에

```
쓰기 → Redis만 저장 → 비동기로 DB에 반영
장점: 쓰기 성능 극대화
단점: Redis 장애 시 데이터 유실 위험
```

---

## 5. Redis Lua Script — Atomic 감소

### Race Condition: DECR 방식의 문제

재고 감소를 단순 `DECR` + 음수 체크로 구현하면 두 명령 사이에 다른 요청이 끼어들 수 있습니다.

```java
// ❌ Race Condition 발생 가능
Long stock = redisTemplate.opsForValue().decrement(key, quantity);  // DECR
if (stock == null || stock < 0) {
    redisTemplate.opsForValue().increment(key, quantity);           // 복구 INCR
    return false;
}
```

```
재고: 1, 동시 요청: 2개

Thread-A: DECR → stock = 0  → (0 >= 0) 성공 판단, 주문 처리 시작
Thread-B: DECR → stock = -1 → INCR 복구 실행
          (이때 Thread-A는 이미 성공 반환 → Kafka 발행 완료)
          Thread-B INCR → 재고 0 → 1 복구

결과: 재고 1개인데 Thread-A 주문 성공 + 재고가 1로 복구 → 다음 요청도 성공 가능
     → 초과 판매 발생
```

DECR과 복구 INCR은 별도 명령 → **"검증"과 "차감"이 원자적이지 않음**이 핵심 문제입니다.

### Lua Script 적용

```java
// ✅ Lua Script — Atomic 재고 감소
private static final RedisScript<Long> DECREASE_STOCK_SCRIPT = RedisScript.of(
    """
    local stock = tonumber(redis.call('GET', KEYS[1]))
    if stock == nil then
        return -2
    end
    if stock < tonumber(ARGV[1]) then
        return -1
    end
    return redis.call('DECRBY', KEYS[1], ARGV[1])
    """,
    Long.class
);

public boolean decrease(Long productId, int quantity) {
    String key = "product:stock:" + productId;

    Long result = redisTemplate.execute(
        DECREASE_STOCK_SCRIPT,
        List.of(key),
        String.valueOf(quantity)
    );

    // -2: 키 없음, -1: 재고 부족, 0 이상: 감소 성공
    return result != null && result >= 0;
}
```

### Lua가 왜 Atomic인가

```
Redis 단일 스레드 모델:
  Redis는 명령을 하나씩 순차 처리
  → 일반 명령(GET, SET): 명령 사이에 다른 클라이언트 요청 끼어들기 가능

  Lua Script: 스크립트 전체가 하나의 명령으로 취급
  → 스크립트 실행 도중 다른 명령 처리 불가

비교:
  ❌ DECR 방식:  DECR ─── (다른 요청 끼어들 수 있음) ─── INCR 복구
  ✅ Lua Script: [GET → 재고 검증 → DECRBY] 전체가 하나의 원자 연산
```

| 구분 | DECR + INCR | Lua Script |
|------|------------|------------|
| 원자성 | ❌ 두 명령 사이 개입 가능 | ✅ 스크립트 전체가 단일 명령 |
| Race Condition | 발생 가능 | 발생 불가 |
| 재고 음수 가능성 | 있음 | 없음 |
| 추가 의존성 | 없음 | 없음 (Redis 내장) |
| 복잡도 | 낮음 | 약간 높음 (Lua 문법) |

### Lua Script vs 분산 락 선택 기준

```
Lua Script가 적합한 경우:
  - "조회 → 검증 → 쓰기"를 원자적으로 처리해야 할 때
  - 재고 감소, 쿠폰 차감처럼 단순한 카운터 연산
  - 외부 API 호출 등 느린 작업이 없는 경우
  - Redis 단일 노드 또는 클러스터의 단일 키 대상

분산 락(Redisson)이 적합한 경우:
  - 여러 Redis 키 또는 외부 자원(DB)을 함께 변경해야 할 때
  - 처리 시간이 긴 로직 (외부 API 호출, 복잡한 비즈니스 로직)
  - 트랜잭션 범위가 Lua로 표현하기 복잡한 경우
```

---

## 6. 실무 안티패턴

### ❌ 캐시 스탬피드 (Cache Stampede)

캐시가 만료된 순간, 동일한 데이터를 요청하는 트래픽이 한꺼번에 DB로 몰리는 현상입니다.

```
인기 상품 캐시 TTL 만료
    → 동시에 수천 개 요청이 DB로 직행
    → DB CPU 급증 → 커넥션 풀 고갈 → 응답 지연 → 재시도 폭증
    → 연쇄 장애 (cascading failure) → 전체 서비스 다운
```

**자주 발생하는 상황**: 인기 게시글, 메인 대시보드, 실시간 랭킹, AI 모델 응답 캐시, TTL 고정값으로 설정한 경우

#### 해결 전략 1: TTL 랜덤화 (가장 간단, 실무 최다 사용)

만료 시점을 분산시켜 한 번에 몰리지 않도록 합니다.

```java
// ❌ 고정 TTL → 동시 만료
redisTemplate.opsForValue().set(key, value, 600, TimeUnit.SECONDS);

// ✅ TTL 랜덤화 → 만료 시점 분산
int ttl = 600 + ThreadLocalRandom.current().nextInt(0, 60);
redisTemplate.opsForValue().set(key, value, ttl, TimeUnit.SECONDS);
```

#### 해결 전략 2: Mutex Lock (분산 락)

캐시 Miss 시 딱 한 명만 DB를 조회하도록 제한합니다.

```java
// Redis SETNX 기반 — 첫 번째 요청만 DB 조회, 나머지는 대기 후 캐시 사용
public Product getProduct(Long id) {
    String cacheKey = "product:" + id;
    Product cached = (Product) redisTemplate.opsForValue().get(cacheKey);
    if (cached != null) return cached;

    String lockKey = "LOCK:" + cacheKey;
    Boolean locked = redisTemplate.opsForValue()
        .setIfAbsent(lockKey, "1", 3, TimeUnit.SECONDS); // SETNX

    if (Boolean.TRUE.equals(locked)) {
        try {
            Product product = productRepository.findById(id).orElseThrow();
            redisTemplate.opsForValue().set(cacheKey, product, 600, TimeUnit.SECONDS);
            return product;
        } finally {
            redisTemplate.delete(lockKey);
        }
    } else {
        // 락 획득 실패 → 잠시 대기 후 캐시 재조회
        Thread.sleep(100);
        return (Product) redisTemplate.opsForValue().get(cacheKey);
    }
}
```

#### 해결 전략 3: Stale-While-Revalidate

TTL이 만료돼도 기존(stale) 데이터를 즉시 반환하면서 백그라운드에서 갱신합니다.

```java
// 논리적 TTL을 데이터 안에 포함 → 실제 TTL은 더 길게 설정
public Product getProduct(Long id) {
    CachedProduct cached = (CachedProduct) redisTemplate.opsForValue().get("product:" + id);

    if (cached != null && !cached.isExpired()) {
        return cached.getData(); // 아직 유효한 캐시 반환
    }
    if (cached != null && cached.isExpired()) {
        // 만료됐지만 기존 값 즉시 반환 + 백그라운드 갱신
        CompletableFuture.runAsync(() -> refreshCache(id));
        return cached.getData(); // stale 허용
    }
    return refreshCache(id); // 최초 캐시 미스
}
```

#### 해결 전략 4: Probabilistic Early Expiration (PER)

TTL 만료 전에 확률적으로 선제 갱신합니다. 트래픽이 많을수록 갱신 확률이 높아집니다.

```java
// 만료 1분 전, 10% 확률로 선제 갱신 → 만료 시점에 몰리지 않음
Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
if (ttl != null && ttl < 60 && Math.random() < 0.1) {
    refreshCache(key);
}
```

#### 전략 비교

| 방법 | 구현 난이도 | 안정성 | Stale 허용 | 실무 사용 빈도 |
|------|-----------|--------|-----------|-------------|
| TTL 랜덤화 | ★ | 중 | ❌ | ⭐⭐⭐⭐⭐ |
| Mutex Lock | ★★★ | 높음 | ❌ | ⭐⭐⭐⭐ |
| Stale-While-Revalidate | ★★★ | 매우 높음 | ✅ | ⭐⭐⭐⭐ |
| PER (선제 갱신) | ★★ | 높음 | ❌ | ⭐⭐⭐ |

> **실무 권장**: 일반적으로 TTL 랜덤화를 기본으로 적용하고, Hot Key + 고비용 조회(AI 응답, 복잡한 집계)에는 Mutex Lock 또는 Stale-While-Revalidate를 추가로 적용합니다.

### ❌ 큰 컬렉션 키 (Hot Key + Big Key)

```java
// ❌ 단일 키에 수백만 개 멤버 → 단일 스레드 Redis 블로킹
ZADD all:users <score> <member>  // 전체 유저를 하나의 ZSet에

// ✅ 분산 키로 샤딩
ZADD users:shard:0 <score> <member>  // userId % 10 으로 분산
```

### ❌ TTL 없는 캐시 → 메모리 무제한 증가

```java
// ❌ TTL 미설정 → 영구 저장
redisTemplate.opsForValue().set(key, value);

// ✅ 항상 TTL 설정
redisTemplate.opsForValue().set(key, value, 30, TimeUnit.MINUTES);
```

### ❌ 분산 락 finally 없는 unlock → 데드락

```java
// ❌ 예외 발생 시 unlock 누락 → 다른 스레드 영원히 대기
if (lock.tryLock()) {
    doWork(); // 예외 발생 시 unlock 안 됨!
    lock.unlock();
}

// ✅ finally로 항상 unlock
if (lock.tryLock(5, 3, TimeUnit.SECONDS)) {
    try {
        doWork();
    } finally {
        lock.unlock();
    }
}
```

---

## 7. 실습 내용

`src/test/java/com/exam/redis/RedisDeepDiveTest.java`를 실행하세요.
(주의: 로컬에 Redis가 6379 포트로 떠 있어야 합니다)

1. **`distributedLockTest`**: 5개 스레드가 동시 진입 → 로그가 순차적으로 출력되는지 확인.
2. **`rankingTest`**: 점수 추가 후 `ZREVRANGE`로 정렬 순서 검증.
3. **`luaScriptStockDecreaseTest`**: 재고 10개에 20개 스레드 동시 차감 → 정확히 10개 성공, 재고 0 검증.
