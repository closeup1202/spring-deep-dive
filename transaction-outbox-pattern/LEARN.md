# Transaction Outbox Pattern

## 개요

트랜잭션 아웃박스 패턴은 **분산 시스템에서 데이터베이스 트랜잭션과 메시지 발행의 원자성을 보장**하는 패턴입니다.

## 문제 상황

### 일반적인 접근 방식의 문제점

```java
@Transactional
public Order createOrder(OrderRequest request) {
    // 1. DB에 주문 저장
    Order order = orderRepository.save(new Order(request));

    // 2. Kafka에 이벤트 발행
    kafkaTemplate.send("OrderCreated", order); // 여기서 실패하면?

    return order;
}
```

**문제점:**
- DB 트랜잭션은 커밋되었지만 Kafka 발행이 실패하면 데이터 불일치 발생
- Kafka는 트랜잭션 범위에 포함되지 않음
- 네트워크 장애, Kafka 다운 등의 상황에서 이벤트 유실

## 트랜잭션 아웃박스 패턴

### 핵심 아이디어

1. **비즈니스 데이터와 이벤트를 같은 트랜잭션에 저장**
2. **별도의 프로세스가 아웃박스 테이블을 폴링하여 메시지 발행**

### 구조

```
┌─────────────────────────────────────────┐
│         Database Transaction            │
│                                         │
│  ┌──────────┐      ┌──────────────┐   │
│  │  Orders  │      │ Outbox Events│   │
│  │  Table   │      │    Table     │   │
│  └──────────┘      └──────────────┘   │
│                                         │
└─────────────────────────────────────────┘
              │
              ▼
      ┌───────────────┐
      │ Outbox        │
      │ Processor     │  (주기적 폴링)
      └───────────────┘
              │
              ▼
         ┌────────┐
         │ Kafka  │
         └────────┘
```

## 구현 설명

### 1. 도메인 모델

#### Order (비즈니스 엔티티)
```java
@Entity
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String customerId;
    private String productName;
    private BigDecimal price;
    private Integer quantity;
    private OrderStatus status;
}
```

#### OutboxEvent (아웃박스 이벤트)
```java
@Entity
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String aggregateId;      // 연관된 비즈니스 엔티티 ID
    private String aggregateType;    // 엔티티 타입 (예: "Order")
    private String eventType;        // 이벤트 타입 (예: "OrderCreated")
    private String payload;          // JSON 페이로드

    @Enumerated(EnumType.STRING)
    private EventStatus status;      // PENDING, PROCESSED, FAILED

    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
}
```

### 2. 트랜잭션 처리

```java
@Service
public class OrderService {
    @Transactional
    public Order createOrder(...) {
        // 1. 비즈니스 데이터 저장
        Order order = orderRepository.save(new Order(...));

        // 2. 아웃박스 이벤트 저장 (같은 트랜잭션!)
        OutboxEvent event = new OutboxEvent(
            order.getId().toString(),
            "Order",
            "OrderCreated",
            createPayload(order)
        );
        outboxEventRepository.save(event);

        // 3. 트랜잭션 커밋 시 Order와 OutboxEvent 모두 저장됨
        return order;
    }
}
```

**장점:**
- Order와 OutboxEvent가 하나의 트랜잭션에서 처리됨
- 둘 다 저장되거나, 둘 다 롤백됨 (원자성 보장)
- Kafka 장애와 무관하게 데이터 일관성 유지

### 3. 아웃박스 프로세서 (이벤트 발행)

```java
@Service
public class OutboxProcessor {
    @Scheduled(fixedDelay = 5000)  // 5초마다 실행
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> pendingEvents =
            outboxEventRepository.findByStatus(PENDING);

        for (OutboxEvent event : pendingEvents) {
            try {
                // Kafka로 발행
                kafkaTemplate.send(
                    event.getEventType(),
                    event.getAggregateId(),
                    event.getPayload()
                );

                // 성공 시 상태 업데이트
                event.markAsProcessed();
                outboxEventRepository.save(event);

            } catch (Exception e) {
                // 실패 시 상태 업데이트 (재시도 가능)
                event.markAsFailed();
                outboxEventRepository.save(event);
            }
        }
    }
}
```

**동작 방식:**
1. 주기적으로 PENDING 상태의 이벤트 조회
2. Kafka로 발행 시도
3. 성공 시 PROCESSED로 상태 변경
4. 실패 시 FAILED로 표시 (나중에 재시도 가능)

## 실행 방법

### 1. Kafka 실행 (Docker)

```bash
# docker-compose.yml
version: '3'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:latest
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  kafka:
    image: confluentinc/cp-kafka:latest
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
```

```bash
docker-compose up -d
```

### 2. 애플리케이션 실행

```bash
./gradlew :transaction-outbox-pattern:bootRun
```

### 3. API 테스트

```bash
# 주문 생성
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "customer-123",
    "productName": "Laptop",
    "price": 1500.00,
    "quantity": 1
  }'
```

### 4. 확인

```bash
# H2 Console 접속
http://localhost:8080/h2-console

# JDBC URL: jdbc:h2:mem:outboxdb
# 테이블 확인
SELECT * FROM ORDERS;
SELECT * FROM OUTBOX_EVENTS;
```

## 패턴의 장단점

### 장점

1. **데이터 일관성 보장**
   - 비즈니스 데이터와 이벤트가 하나의 트랜잭션에서 처리
   - 부분 실패 방지

2. **메시지 전달 보장 (At-least-once)**
   - 실패한 이벤트는 재시도 가능
   - 이벤트 유실 방지

3. **외부 시스템 장애 격리**
   - Kafka 다운되어도 비즈니스 로직은 정상 동작
   - 복구 후 자동으로 이벤트 발행

4. **감사 추적**
   - 모든 이벤트가 데이터베이스에 기록됨
   - 디버깅 및 모니터링 용이

### 단점

1. **추가 테이블 관리**
   - 아웃박스 테이블 필요
   - 오래된 이벤트 정리 필요

2. **폴링 오버헤드**
   - 주기적으로 데이터베이스 조회
   - 지연 시간 발생 가능 (폴링 주기에 따라)

3. **중복 발행 가능성**
   - 프로세서가 중복 실행될 수 있음
   - 컨슈머에서 멱등성 처리 필요

4. **복잡도 증가**
   - 추가 컴포넌트 (프로세서) 필요
   - 운영 복잡도 상승

## 최적화 전략

### 1. CDC (Change Data Capture) 사용

폴링 대신 데이터베이스 변경 로그를 모니터링:

```
┌──────────┐     ┌─────────┐     ┌────────┐
│ Database │ --> │ Debezium│ --> │ Kafka  │
│          │     │  (CDC)  │     │        │
└──────────┘     └─────────┘     └────────┘
```

**장점:**
- 실시간에 가까운 이벤트 발행
- 폴링 오버헤드 제거
- 확장성 향상

**도구:**
- Debezium
- Maxwell
- AWS DMS

### 2. 배치 처리

한 번에 여러 이벤트를 처리:

```java
@Scheduled(fixedDelay = 5000)
public void processOutboxEvents() {
    List<OutboxEvent> events = outboxEventRepository
        .findTop100ByStatusOrderByCreatedAtAsc(PENDING); // 배치 크기 제한

    // 배치 전송
    events.forEach(this::publishToKafka);
}
```

### 3. 파티셔닝

대량 데이터 처리를 위한 테이블 파티셔닝:

```sql
CREATE TABLE outbox_events (
    ...
) PARTITION BY RANGE (created_at) (
    PARTITION p0 VALUES LESS THAN ('2024-01-01'),
    PARTITION p1 VALUES LESS THAN ('2024-02-01'),
    ...
);
```

## 실무 고려사항

### 1. 멱등성

컨슈머는 중복 메시지를 처리할 수 있어야 함:

```java
@KafkaListener(topics = "OrderCreated")
public void handleOrderCreated(String message) {
    String orderId = extractOrderId(message);

    // 이미 처리했는지 확인
    if (processedEventRepository.exists(orderId)) {
        return; // 중복 처리 방지
    }

    // 비즈니스 로직 수행
    processOrder(message);

    // 처리 완료 기록
    processedEventRepository.save(orderId);
}
```

### 2. 재시도 전략

실패한 이벤트의 재시도:

```java
@Scheduled(fixedDelay = 60000) // 1분마다
public void retryFailedEvents() {
    List<OutboxEvent> failedEvents =
        outboxEventRepository.findByStatus(FAILED);

    for (OutboxEvent event : failedEvents) {
        if (event.getRetryCount() < MAX_RETRIES) {
            retryPublish(event);
        } else {
            // Dead Letter Queue로 이동
            moveToDeadLetterQueue(event);
        }
    }
}
```

### 3. 모니터링

```java
@Scheduled(fixedDelay = 30000)
public void monitorOutbox() {
    long pendingCount = outboxEventRepository.countByStatus(PENDING);
    long failedCount = outboxEventRepository.countByStatus(FAILED);

    // 메트릭 수집
    meterRegistry.gauge("outbox.pending", pendingCount);
    meterRegistry.gauge("outbox.failed", failedCount);

    // 알림
    if (pendingCount > THRESHOLD) {
        alertService.sendAlert("Too many pending events!");
    }
}
```

### 4. 정리 작업

```java
@Scheduled(cron = "0 0 2 * * ?") // 매일 새벽 2시
public void cleanupProcessedEvents() {
    LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7);
    outboxEventRepository.deleteByStatusAndProcessedAtBefore(
        PROCESSED,
        cutoffDate
    );
}
```

## 다른 패턴과의 비교

### vs 2PC (Two-Phase Commit)

| 특성 | Outbox Pattern | 2PC |
|------|---------------|-----|
| 복잡도 | 낮음 | 높음 |
| 성능 | 좋음 | 나쁨 (블로킹) |
| 가용성 | 높음 | 낮음 |
| 일관성 | Eventual | Strong |

### vs Saga Pattern

- **Outbox**: 단일 서비스 내에서 DB와 메시징의 일관성
- **Saga**: 여러 서비스 간의 분산 트랜잭션 조정

두 패턴을 함께 사용할 수 있습니다!

## 참고 자료

- [Microservices Pattern: Transactional Outbox](https://microservices.io/patterns/data/transactional-outbox.html)
- [Debezium Tutorial](https://debezium.io/documentation/)
- [Event-Driven Architecture](https://martinfowler.com/articles/201701-event-driven.html)
