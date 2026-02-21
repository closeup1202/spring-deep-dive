# Saga Compensation Pattern (보상 패턴)

## 개요

Saga 패턴은 **분산 시스템에서 여러 서비스에 걸친 트랜잭션을 관리하는 패턴**입니다. 각 서비스의 로컬 트랜잭션을 순차적으로 실행하고, 실패 시 **보상 트랜잭션(Compensation Transaction)**을 통해 이미 실행된 단계를 되돌립니다.

## 문제 상황

### 분산 시스템에서의 트랜잭션

마이크로서비스 아키텍처에서는 각 서비스가 독립적인 데이터베이스를 가집니다:

```
주문 서비스 (Order DB)
   ↓
결제 서비스 (Payment DB)
   ↓
재고 서비스 (Inventory DB)
```

**문제:**
- 전통적인 ACID 트랜잭션을 사용할 수 없음
- 2PC (Two-Phase Commit)는 성능과 가용성 문제
- 일부 단계는 성공하고 일부는 실패할 수 있음

### 실패 시나리오 예시

```
1. 주문 생성 ✓
2. 결제 처리 ✓
3. 재고 예약 ✗ (재고 부족)

문제: 이미 처리된 결제를 어떻게 취소할 것인가?
```

## Saga 패턴의 해결 방법

### 핵심 개념

1. **순방향 트랜잭션 (Forward Transaction)**
   - 비즈니스 로직을 수행하는 일반 트랜잭션
   - 예: 결제 처리, 재고 예약

2. **보상 트랜잭션 (Compensation Transaction)**
   - 순방향 트랜잭션을 되돌리는 트랜잭션
   - 예: 결제 취소, 재고 해제

### 동작 흐름

```
성공 케이스:
주문 생성 → 결제 처리 → 재고 예약 → 완료

실패 케이스:
주문 생성 → 결제 처리 → 재고 예약 (실패)
                ↓           ↓
              결제 취소    (보상 트랜잭션)
```

## Saga 패턴의 두 가지 방식

### 1. Choreography (안무 방식)

각 서비스가 이벤트를 발행하고 구독하여 자율적으로 동작:

```
┌──────────┐  OrderCreated   ┌──────────┐  PaymentCompleted  ┌───────────┐
│  Order   │ ──────────────> │ Payment  │ ─────────────────> │ Inventory │
│ Service  │                 │ Service  │                    │  Service  │
└──────────┘                 └──────────┘                    └───────────┘
                                  │                                │
                          PaymentFailed                    InventoryFailed
                                  ↓                                ↓
                             (보상 이벤트 발행)
```

**장점:**
- 서비스 간 느슨한 결합
- 확장성 좋음

**단점:**
- 전체 흐름 파악 어려움
- 순환 의존성 위험

### 2. Orchestration (오케스트레이션 방식)

중앙 조정자(Orchestrator)가 전체 흐름을 제어:

```
                    ┌─────────────────────┐
                    │  Saga Orchestrator  │
                    └─────────────────────┘
                       │     │        │
                  1.주문 2.결제   3.재고
                       ↓     ↓        ↓
              ┌──────────┐ ┌──────┐ ┌─────────┐
              │  Order   │ │Payment│ │Inventory│
              └──────────┘ └──────┘ └─────────┘
```

**장점:**
- 전체 흐름이 명확함
- 비즈니스 로직 중앙 집중
- 디버깅 및 모니터링 용이

**단점:**
- Orchestrator가 단일 장애점
- 서비스 간 결합도 증가

## 구현 설명 (Orchestration 방식)

### 1. 도메인 모델

#### Booking (예약)
```java
@Entity
public class Booking {
    @Id
    private Long id;
    private String userId;
    private String productId;
    private Integer quantity;
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private BookingState state;  // 현재 Saga의 상태

    private String failureReason;
}
```

#### BookingState (상태)
```java
public enum BookingState {
    INITIATED,           // 시작됨
    PAYMENT_PENDING,     // 결제 대기
    PAYMENT_COMPLETED,   // 결제 완료
    INVENTORY_RESERVED,  // 재고 예약
    COMPLETED,           // 완료
    FAILED,              // 실패
    COMPENSATING,        // 보상 중
    COMPENSATED          // 보상 완료
}
```

### 2. Saga Orchestrator

```java
@Service
public class BookingSagaOrchestrator {
    @Transactional
    public Booking executeBookingSaga(...) {
        // 1. 예약 생성
        Booking booking = createBooking(...);

        try {
            // 2. 결제 처리
            processPaymentStep(booking);

            // 3. 재고 예약
            reserveInventoryStep(booking);

            // 4. 성공
            booking.updateState(BookingState.COMPLETED);
            return booking;

        } catch (Exception e) {
            // 5. 실패 시 보상 트랜잭션 실행
            compensate(booking, e.getMessage());
            throw new RuntimeException("예약 실패", e);
        }
    }
}
```

### 3. 각 단계의 구현

#### 결제 단계
```java
private void processPaymentStep(Booking booking) {
    booking.updateState(BookingState.PAYMENT_PENDING);
    bookingRepository.save(booking);

    // 실제 결제 처리
    paymentService.processPayment(
        booking.getId(),
        booking.getUserId(),
        booking.getAmount()
    );

    booking.updateState(BookingState.PAYMENT_COMPLETED);
    bookingRepository.save(booking);
}
```

#### 재고 예약 단계
```java
private void reserveInventoryStep(Booking booking) {
    // 재고 예약 시도
    inventoryService.reserveInventory(
        booking.getProductId(),
        booking.getQuantity()
    );

    booking.updateState(BookingState.INVENTORY_RESERVED);
    bookingRepository.save(booking);
}
```

### 4. 보상 트랜잭션

```java
private void compensate(Booking booking, String reason) {
    booking.updateState(BookingState.COMPENSATING);

    // 역순으로 보상 실행
    switch (booking.getState()) {
        case INVENTORY_RESERVED:
            // 재고 예약 취소
            inventoryService.releaseInventory(
                booking.getProductId(),
                booking.getQuantity()
            );
            // fall through

        case PAYMENT_COMPLETED:
        case PAYMENT_PENDING:
            // 결제 취소
            paymentService.refundPayment(booking.getId());
            break;
    }

    booking.markAsFailed(reason);
    booking.updateState(BookingState.COMPENSATED);
    bookingRepository.save(booking);
}
```

**중요 포인트:**
1. **역순 실행**: 가장 마지막 단계부터 보상
2. **상태 기반**: 현재 상태에 따라 어디까지 보상할지 결정
3. **Fall-through**: switch문에서 break 없이 이전 단계들도 보상

### 5. 서비스 계층

#### PaymentService
```java
@Service
public class PaymentService {
    // 순방향: 결제 처리
    public Payment processPayment(Long bookingId, String userId, BigDecimal amount) {
        Payment payment = new Payment(bookingId, userId, amount);
        return paymentRepository.save(payment);
    }

    // 보상: 결제 취소
    public void refundPayment(Long bookingId) {
        paymentRepository.findByBookingId(bookingId)
            .ifPresent(payment -> {
                payment.refund();
                paymentRepository.save(payment);
            });
    }
}
```

#### InventoryService
```java
@Service
public class InventoryService {
    // 순방향: 재고 예약
    public void reserveInventory(String productId, Integer quantity) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품 없음"));

        if (!inventory.canReserve(quantity)) {
            throw new IllegalStateException("재고 부족");
        }

        inventory.reserve(quantity);
        inventoryRepository.save(inventory);
    }

    // 보상: 재고 해제
    public void releaseInventory(String productId, Integer quantity) {
        inventoryRepository.findByProductId(productId)
            .ifPresent(inventory -> {
                inventory.releaseReservation(quantity);
                inventoryRepository.save(inventory);
            });
    }
}
```

## 실행 방법

### 1. 애플리케이션 실행

```bash
./gradlew :saga-compensation-pattern:bootRun
```

### 2. 성공 케이스 테스트

```bash
# 재고가 충분한 경우
curl -X POST http://localhost:8081/api/bookings \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "productId": "PRODUCT-001",
    "quantity": 5,
    "amount": 100.00
  }'

# 응답: state = "COMPLETED"
```

### 3. 실패 케이스 테스트 (재고 부족)

```bash
# 재고보다 많은 수량 요청
curl -X POST http://localhost:8081/api/bookings \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-456",
    "productId": "PRODUCT-003",
    "quantity": 100,
    "amount": 1000.00
  }'

# 응답: state = "FAILED", error = "재고 부족..."
```

### 4. 데이터 확인

```bash
# H2 Console 접속
http://localhost:8081/h2-console

# JDBC URL: jdbc:h2:mem:sagadb

# 예약 상태 확인
SELECT * FROM BOOKINGS;

# 결제 상태 확인 (REFUNDED 확인)
SELECT * FROM PAYMENTS;

# 재고 확인 (롤백 확인)
SELECT * FROM INVENTORY;
```

## 패턴의 특징

### 장점

1. **ACID 없이도 일관성 보장**
   - 분산 환경에서 데이터 일관성 유지
   - 보상 트랜잭션으로 원자성 구현

2. **높은 가용성**
   - 각 서비스가 독립적으로 동작
   - 일부 서비스 장애에도 전체 시스템 가동

3. **확장성**
   - 서비스별 독립적 확장 가능
   - 단계 추가/제거 용이

4. **비즈니스 로직 명확화**
   - 전체 프로세스를 코드로 표현
   - 실패 시나리오 명시적 처리

### 단점

1. **복잡도 증가**
   - 각 단계마다 보상 로직 필요
   - 상태 관리 복잡

2. **격리성 부족**
   - 중간 상태가 다른 트랜잭션에 노출될 수 있음
   - Dirty Read 가능

3. **보상 트랜잭션의 한계**
   - 일부 작업은 되돌릴 수 없음 (예: 이메일 발송)
   - 완벽한 롤백 보장 불가

4. **디버깅 어려움**
   - 분산 환경에서 추적 어려움
   - 실패 지점 파악 복잡

## 보상 트랜잭션 설계 원칙

### 1. 멱등성 (Idempotency)

보상 트랜잭션은 여러 번 실행되어도 결과가 같아야 합니다:

```java
public void refundPayment(Long bookingId) {
    paymentRepository.findByBookingId(bookingId)
        .ifPresent(payment -> {
            // 이미 환불된 경우 무시
            if (payment.getStatus() == PaymentStatus.REFUNDED) {
                return;
            }
            payment.refund();
            paymentRepository.save(payment);
        });
}
```

### 2. 재시도 가능성

보상 트랜잭션 실패 시 재시도 메커니즘:

```java
@Retryable(
    value = {Exception.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000)
)
public void refundPayment(Long bookingId) {
    // 재시도 가능한 환불 로직
}
```

### 3. 상태 추적

각 단계의 상태를 명확히 기록:

```java
@Entity
public class SagaState {
    private Long sagaId;
    private String currentStep;
    private String status;
    private LocalDateTime lastUpdated;
    private String compensationStep;
}
```

## 고급 패턴

### 1. Semantic Lock (의미적 잠금)

보상이 필요한 리소스를 "진행 중" 상태로 표시:

```java
@Entity
public class Inventory {
    private Integer availableQuantity;
    private Integer reservedQuantity;  // Semantic lock

    public void reserve(Integer quantity) {
        this.availableQuantity -= quantity;
        this.reservedQuantity += quantity;  // 잠금
    }

    public void confirm(Integer quantity) {
        this.reservedQuantity -= quantity;  // 잠금 해제
    }

    public void release(Integer quantity) {
        this.availableQuantity += quantity;
        this.reservedQuantity -= quantity;  // 보상
    }
}
```

### 2. Saga Log

각 단계의 실행과 보상을 로그로 기록:

```java
@Entity
public class SagaLog {
    @Id
    private Long id;
    private Long sagaId;
    private String step;
    private String action;  // EXECUTE or COMPENSATE
    private LocalDateTime timestamp;
    private String result;
}
```

### 3. Dead Letter Queue

보상 실패 시 별도 큐로 이동하여 수동 처리:

```java
public void compensate(Booking booking) {
    try {
        executeCompensation(booking);
    } catch (Exception e) {
        // Dead Letter Queue로 이동
        deadLetterQueueService.add(booking, e);
        // 알림 발송
        alertService.notifyCompensationFailure(booking);
    }
}
```

## 실무 고려사항

### 1. 타임아웃 처리

각 단계에 타임아웃 설정:

```java
@Transactional(timeout = 30)
public void processPaymentStep(Booking booking) {
    // 30초 내에 완료되어야 함
}
```

### 2. 모니터링

Saga 실행 상태 모니터링:

```java
@Scheduled(fixedDelay = 60000)
public void monitorStuckSagas() {
    LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);
    List<Booking> stuckBookings = bookingRepository
        .findByStateNotInAndUpdatedAtBefore(
            List.of(COMPLETED, FAILED, COMPENSATED),
            threshold
        );

    if (!stuckBookings.isEmpty()) {
        alertService.notifyStuckSagas(stuckBookings);
    }
}
```

### 3. 순서 보장

일부 단계는 순서가 중요할 수 있습니다:

```java
public Booking executeBookingSaga(...) {
    // 반드시 이 순서로 실행
    createBooking();
    processPayment();     // 결제 먼저
    reserveInventory();   // 재고는 나중에
}
```

**이유:**
- 결제가 실패하면 재고 예약 불필요
- 재고 예약은 비용이 낮은 작업
- 실패 가능성 높은 작업을 먼저 수행

### 4. 분산 추적

전체 Saga를 추적할 수 있는 ID 사용:

```java
@Slf4j
public class BookingSagaOrchestrator {
    public Booking executeBookingSaga(...) {
        String sagaId = UUID.randomUUID().toString();
        MDC.put("sagaId", sagaId);

        try {
            log.info("Starting saga: {}", sagaId);
            // Saga 실행
        } finally {
            MDC.remove("sagaId");
        }
    }
}
```

## Saga vs 다른 패턴

### vs Two-Phase Commit (2PC)

| 특성 | Saga | 2PC |
|------|------|-----|
| 일관성 | Eventual | Strong |
| 가용성 | 높음 | 낮음 |
| 성능 | 좋음 | 나쁨 |
| 복잡도 | 높음 | 중간 |
| 격리성 | 없음 | 있음 |

### vs Outbox Pattern

- **Outbox**: 단일 서비스 내 DB-메시징 일관성
- **Saga**: 여러 서비스 간 분산 트랜잭션 조정

함께 사용 가능! Saga의 각 단계에서 Outbox 패턴 적용

### vs Event Sourcing

- **Saga**: 상태 기반, 보상 트랜잭션
- **Event Sourcing**: 이벤트 기반, 이벤트 재생

## 참고 자료

- [Microservices Pattern: Saga](https://microservices.io/patterns/data/saga.html)
- [Chris Richardson - Pattern: Saga](https://www.chrisrichardson.net/post/antipatterns/2019/07/09/developing-sagas-part-1.html)
- [Martin Fowler - Saga Pattern](https://martinfowler.com/articles/patterns-of-distributed-systems/saga.html)
- [AWS - Saga Pattern](https://docs.aws.amazon.com/prescriptive-guidance/latest/modernization-data-persistence/saga-pattern.html)
