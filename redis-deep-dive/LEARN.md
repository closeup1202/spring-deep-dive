# Redis Deep Dive: 분산 락과 랭킹 시스템

## 📌 언제 사용하는가?

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

## 5. 실무 안티패턴

### ❌ 캐시 스탬피드 (Cache Stampede)

```
캐시 만료 → 동시에 수천 개 요청이 DB로 몰림 → DB 과부하
```

**해결책**: 뮤텍스 락 또는 확률적 갱신(Probabilistic Early Expiration)

```java
// 캐시 만료 전에 미리 갱신 (PER 방식)
if (ttl < 60 && Math.random() < 0.1) { // 만료 1분 전, 10% 확률로 선제 갱신
    refreshCache(key);
}
```

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

## 6. 실습 내용

`src/test/java/com/exam/redis/RedisDeepDiveTest.java`를 실행하세요.
(주의: 로컬에 Redis가 6379 포트로 떠 있어야 합니다)

1. **`distributedLockTest`**: 5개 스레드가 동시 진입 → 로그가 순차적으로 출력되는지 확인.
2. **`rankingTest`**: 점수 추가 후 `ZREVRANGE`로 정렬 순서 검증.
