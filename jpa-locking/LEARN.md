# JPA Locking & Hexagonal Architecture

## 1. Hexagonal Architecture (Ports and Adapters)

이 모듈은 헥사고날 아키텍처를 적용하여 도메인 로직과 영속성 계층을 분리했습니다.

- **Domain (`com.exam.jpalocking.domain`)**: `OutboxEvent`는 순수 자바 객체(POJO)로, JPA 어노테이션이나 DB 기술에 의존하지 않습니다.
- **Port (`com.exam.jpalocking.port`)**: `OutboxEventRepository` 인터페이스는 도메인이 필요로 하는 저장소 기능을 정의합니다.
- **Adapter (`com.exam.jpalocking.adapter.jpa`)**: `JpaOutboxEventRepositoryAdapter`는 Port를 구현하고, 내부적으로 Spring Data JPA(`OutboxEventJpaRepository`)를 사용하여 실제 DB와 통신합니다.

이 구조의 장점은 도메인 로직이 특정 기술(JPA, MongoDB 등)에 종속되지 않아 테스트가 용이하고 기술 변경에 유연하다는 것입니다.

## 2. Pessimistic Locking & SKIP LOCKED

멀티 인스턴스 환경에서 스케줄러가 동시에 실행될 때, 동일한 이벤트를 중복 처리하는 것을 방지하기 위해 `PESSIMISTIC_WRITE` 락과 `SKIP LOCKED` 옵션을 사용했습니다.

### 동작 원리
1. **PESSIMISTIC_WRITE**: 트랜잭션이 끝날 때까지 해당 행(Row)에 배타적 락(Exclusive Lock)을 겁니다. 다른 트랜잭션은 이 행을 수정하거나 락을 걸 수 없습니다.
2. **SKIP LOCKED**: 락을 획득하려고 할 때, 이미 다른 트랜잭션에 의해 락이 걸려 있는 행은 **대기하지 않고 결과 집합에서 제외(Skip)**합니다.

### 코드 예시 (`OutboxEventJpaRepository`)
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")}) // -2는 SKIP LOCKED를 의미 (Hibernate/PostgreSQL 등)
@Query("SELECT e FROM OutboxEventEntity e WHERE e.status = 'PENDING' ORDER BY e.createdAt ASC")
List<OutboxEventEntity> findPendingEventsSkipLocked(Pageable pageable);
```

### 장점
- **중복 처리 방지**: 여러 서버가 동시에 폴링하더라도 서로 다른 이벤트를 가져가게 됩니다.
- **Non-blocking**: 락이 걸린 행을 기다리지 않으므로 처리량이 향상됩니다. 큐(Queue)와 유사한 동작을 DB로 구현할 때 매우 유용합니다.
