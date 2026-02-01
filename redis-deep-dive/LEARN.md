# Redis Deep Dive: 분산 락과 랭킹 시스템

## 1. 분산 락 (Distributed Lock)
여러 서버가 동시에 실행되는 분산 환경에서, **"오직 하나의 프로세스만 특정 자원에 접근"**하도록 제어하는 기술입니다.
Java의 `synchronized`는 하나의 JVM 안에서만 동작하므로, 서버가 여러 대일 때는 무용지물입니다.

### Redisson 활용
- Redis의 `SETNX` 명령어를 직접 쓰지 않고, Redisson 라이브러리를 사용하면 `RLock` 인터페이스로 쉽게 락을 구현할 수 있습니다.
- **Pub/Sub 방식**을 사용하여 스핀 락(계속 Redis에 물어보는 방식)보다 부하가 적습니다.

### 구현 방식
- `@DistributedLock` 어노테이션을 정의하고 AOP로 감싸서, 비즈니스 로직 전후에 락 획득/해제를 자동화했습니다.

## 2. 실시간 랭킹 (Sorted Set)
Redis의 **Sorted Set (ZSet)** 자료구조는 `Key-Value`에 `Score`를 함께 저장하며, 입력과 동시에 정렬됩니다.
DB로 랭킹을 구현하면 `ORDER BY` 쿼리가 매우 느리지만, Redis ZSet은 `O(log N)`으로 매우 빠릅니다.

### 주요 명령어
- `ZADD key score member`: 점수 추가/갱신
- `ZREVRANGE key start end`: 점수 높은 순으로 범위 조회
- `ZREVRANK key member`: 특정 멤버의 등수 조회

## 3. 실습 내용
`src/test/java/com/exam/redis/RedisDeepDiveTest.java`를 실행하세요.
(주의: 로컬에 Redis가 6379 포트로 떠 있어야 합니다)

1. **`distributedLockTest`**: 5개의 스레드가 동시에 진입해도, 로그가 순차적으로 찍히는지 확인.
2. **`rankingTest`**: 점수에 따라 순위가 정확히 매겨지는지 확인.
