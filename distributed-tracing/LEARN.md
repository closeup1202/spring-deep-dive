# Distributed Tracing: 분산 추적

마이크로서비스 환경에서 하나의 요청이 여러 서비스를 거쳐가는 전체 플로우를 추적하는 방법을 학습합니다.
Micrometer Tracing, Zipkin, Spring Cloud Sleuth의 후속 기술을 사용합니다.

## 📌 언제 사용하는가?

### ✅ 반드시 사용해야 하는 경우
1. **마이크로서비스 아키텍처**: 여러 서비스 간 통신이 있는 경우 (필수)
2. **장애 추적**: 어느 서비스에서 에러가 발생했는지 빠르게 파악
3. **성능 병목 지점 파악**: 어떤 서비스/DB 쿼리가 느린지 시각화
4. **서비스 의존성 분석**: 서비스 간 호출 관계를 자동으로 도식화
5. **SLA 준수**: 요청별 전체 응답 시간 추적

### 🎯 분산 추적 효과
- **빠른 장애 원인 파악**: 전체 플로우 중 어디서 실패했는지 즉시 확인
- **성능 최적화**: 각 서비스/DB 호출의 소요 시간을 상세하게 분석
- **서비스 의존성 시각화**: 실제 트래픽 기반 서비스 맵 자동 생성
- **운영 효율화**: 로그를 여러 서비스에서 뒤질 필요 없이 한 곳에서 확인

### ⚠️ 주의사항
- **성능 오버헤드**: 샘플링 비율 조정 필요 (프로덕션 10~20% 권장)
- **민감 정보**: 태그나 로그에 개인정보/API 키 포함하지 않도록 주의
- **Zipkin 메모리**: 프로덕션에서는 Elasticsearch 등 영구 저장소 사용

---

## 1. Distributed Tracing이란?

### A. 개념
마이크로서비스 환경에서 하나의 사용자 요청이 여러 서비스를 거쳐가는 전체 과정을 **하나의 traceId**로 추적하는 기술입니다.

```
[클라이언트]
    ↓ traceId: abc-123
[Service A - 주문]
    ↓ traceId: abc-123 (전파)
[Service B - 재고 확인] → DB 조회
    ↓ traceId: abc-123 (전파)
[Service B - 결제 처리] → 외부 PG 호출
```

### B. 핵심 용어

| 용어 | 설명 | 예시 |
|------|------|------|
| **Trace** | 하나의 요청이 시스템을 통과하는 전체 여정 | 주문 생성 요청의 전체 플로우 |
| **Span** | Trace 내의 개별 작업 단위 | DB 조회, HTTP 호출, 메서드 실행 |
| **TraceId** | 전체 Trace를 식별하는 ID (모든 Span에 동일) | `abc-123-def-456` |
| **SpanId** | 개별 Span을 식별하는 ID (Span마다 다름) | `span-001`, `span-002` |
| **Parent SpanId** | 현재 Span을 호출한 부모 Span의 ID | Service A span → Service B span |

### C. Zipkin UI에서 보이는 모습

```
Trace ID: abc-123-def-456  Duration: 250ms

Service A - POST /api/orders          [████████████████] 250ms
  ├─ check-stock                      [██] 50ms
  │   └─ Service B - GET /inventory   [██] 50ms
  │       └─ db-query-stock           [█] 20ms
  └─ payment-processing               [██████] 150ms
      └─ Service B - POST /payment    [██████] 150ms
          ├─ payment-validation       [█] 30ms
          ├─ external-pg-call         [███] 100ms
          └─ db-insert-payment        [█] 50ms
```

---

## 2. Micrometer Tracing (Spring Boot 3.x)

### A. Spring Cloud Sleuth → Micrometer Tracing
Spring Boot 3.0부터는 **Micrometer Tracing**을 사용합니다. (Sleuth는 deprecated)

### B. 의존성
```gradle
// Micrometer Tracing with Brave (Zipkin 호환)
implementation 'io.micrometer:micrometer-tracing-bridge-brave'

// Zipkin Reporter
implementation 'io.zipkin.reporter2:zipkin-reporter-brave'
```

### C. 자동 설정
Spring Boot 3.x는 아래 항목을 **자동으로** 처리합니다:

1. **TraceId/SpanId 생성**: 모든 HTTP 요청에 자동으로 부여
2. **HTTP 헤더 전파**: `traceparent` 헤더로 traceId를 다음 서비스로 전달
3. **로그 연동**: MDC에 traceId/spanId 자동 추가 (`%X{traceId}`)
4. **Zipkin 전송**: 설정만 하면 자동으로 Span 정보를 Zipkin에 전송

### D. 설정 (application.yml)
```yaml
management:
  tracing:
    sampling:
      probability: 1.0  # 100% 샘플링 (개발 환경)
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
```

**샘플링 비율**:
- **1.0 (100%)**: 개발 환경, 모든 요청 추적
- **0.1 (10%)**: 프로덕션 권장, 성능 오버헤드 최소화
- **0.01 (1%)**: 대규모 트래픽 환경

---

## 3. Trace Propagation (추적 정보 전파)

### A. HTTP 헤더를 통한 전파

Service A → Service B로 HTTP 요청 시, Spring Boot는 자동으로 HTTP 헤더에 traceId를 포함합니다.

**W3C Trace Context 표준 (Spring Boot 3.x 기본)**:
```http
GET /api/inventory/check HTTP/1.1
Host: localhost:8081
traceparent: 00-abc123def456-span001-01
```

### B. WebClient 자동 전파

Spring Boot 3.x의 `WebClient`는 자동으로 traceId를 전파합니다:

```java
@Bean
public WebClient.Builder webClientBuilder() {
    return WebClient.builder();
}

// 사용 시 - 자동으로 traceparent 헤더 추가됨
webClient.get()
    .uri("http://service-b:8081/api/inventory/check")
    .retrieve()
    .bodyToMono(Map.class)
    .block();
```

**내부 동작**:
1. Service A의 현재 traceId를 가져옴
2. HTTP 요청 헤더에 `traceparent: 00-traceId-spanId-01` 추가
3. Service B는 헤더를 읽어 동일한 traceId 사용

---

## 4. Custom Span (커스텀 스팬)

### A. 왜 필요한가?
- HTTP 호출, DB 쿼리는 자동으로 Span이 생성되지만, **비즈니스 로직**은 수동으로 Span을 만들어야 합니다.
- 예: 복잡한 계산, 파일 처리, 외부 API 호출 등

### B. 방법 1: Tracer API 사용

```java
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final Tracer tracer;

    public void processPayment(String orderId) {
        // 커스텀 Span 생성
        Span customSpan = tracer.nextSpan().name("payment-processing");

        try (Tracer.SpanInScope ws = tracer.withSpan(customSpan.start())) {
            // 태그 추가 (Zipkin에서 검색/필터링 가능)
            customSpan.tag("order.id", orderId);
            customSpan.tag("payment.type", "credit-card");

            // 비즈니스 로직
            log.info("Processing payment for order: {}", orderId);
            Thread.sleep(100);

        } catch (Exception e) {
            customSpan.error(e);  // 에러 정보 기록
            throw e;
        } finally {
            customSpan.end();  // 반드시 종료
        }
    }
}
```

### C. 방법 2: Observation API 사용 (권장)

Spring Boot 3.x부터는 `Observation`을 권장합니다:

```java
@Service
@RequiredArgsConstructor
public class InventoryService {
    private final ObservationRegistry observationRegistry;

    public Map<String, Object> checkStock(String productId) {
        return Observation.createNotStarted("check-stock", observationRegistry)
                .lowCardinalityKeyValue("product.id", productId)
                .observe(() -> {
                    // 비즈니스 로직
                    return queryDatabase(productId);
                });
    }
}
```

**장점**:
- Tracing + Metrics 동시 수집
- try-finally 보일러플레이트 불필요
- 함수형 스타일로 깔끔

---

## 5. Zipkin UI 사용법

### A. Zipkin 실행

```bash
# Docker Compose로 실행
cd distributed-tracing
docker-compose up -d

# Zipkin UI 접속
http://localhost:9411
```

### B. 주요 기능

#### 1️⃣ **Trace 검색**
- **Service Name**: Service A, Service B
- **Span Name**: `GET /api/orders`, `payment-processing`
- **Tags**: `order.id=ORDER-123`, `error=true`
- **시간 범위**: 최근 1시간, 24시간 등

#### 2️⃣ **Trace 상세 보기**
```
Trace ID: abc-123
Duration: 250ms
Services: 2
Spans: 8

[Service A] POST /api/orders     250ms
  [Service A] check-stock          50ms
    [Service B] GET /inventory     50ms
      [DB] SELECT FROM inventory   20ms
  [Service A] payment-processing  150ms
    [Service B] POST /payment     150ms
      [Validation] validate        30ms
      [PG] external-pg-call       100ms
      [DB] INSERT INTO payments    50ms
```

**각 Span 클릭 시 확인 가능 정보**:
- Span 이름, Duration
- Tags (order.id, product.id 등)
- Annotations (이벤트)
- 로그 메시지

#### 3️⃣ **Dependencies (서비스 의존성 그래프)**
실제 트래픽 기반으로 서비스 간 호출 관계를 자동 생성합니다.

```
[Service A] ---> [Service B]
              └─> [Database]
              └─> [External PG]
```

---

## 6. 실습 시나리오

### 1️⃣ 환경 준비

```bash
# 1. Zipkin 실행
cd distributed-tracing
docker-compose up -d

# 2. Service B 실행 (포트 8081)
cd service-b
../../gradlew bootRun

# 3. Service A 실행 (포트 8080)
cd service-a
../../gradlew bootRun
```

### 2️⃣ 주문 생성 요청

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "productId": "PROD-001",
    "quantity": 5
  }'
```

**응답**:
```json
{
  "success": true,
  "orderId": "ORDER-1707567890123",
  "traceId": "abc123def456",
  "details": {
    "stock": {
      "productId": "PROD-001",
      "available": true
    },
    "payment": {
      "orderId": "ORDER-1707567890123",
      "success": true
    }
  }
}
```

### 3️⃣ 로그 확인

**Service A 로그**:
```
23:45:01.123 [http-nio-8080-exec-1] [abc123def456/span001] INFO  - === [Service A] Order request received ===
23:45:01.124 [http-nio-8080-exec-1] [abc123def456/span002] INFO  - [Service A] Calling Service B - Check stock
23:45:01.200 [http-nio-8080-exec-1] [abc123def456/span003] INFO  - [Service A] Calling Service B - Process payment
```

**Service B 로그**:
```
23:45:01.125 [http-nio-8081-exec-1] [abc123def456/span004] INFO  - === [Service B] Stock check request received ===
23:45:01.150 [http-nio-8081-exec-1] [abc123def456/span005] INFO  - [Service B] Checking stock in database
23:45:01.201 [http-nio-8081-exec-2] [abc123def456/span006] INFO  - === [Service B] Payment request received ===
```

**관찰 포인트**:
- **traceId가 동일**: `abc123def456`
- **spanId는 다름**: `span001`, `span002`, ...
- 로그를 traceId로 검색하면 전체 플로우 추적 가능

### 4️⃣ Zipkin UI에서 확인

1. **http://localhost:9411** 접속
2. **Find Traces** 클릭
3. Service Name: `service-a` 선택
4. **Run Query**
5. 최근 Trace 클릭

**Zipkin UI 화면**:
```
Trace Timeline:
┌─ service-a: POST /api/orders (250ms)
│  ├─ service-a: check-stock (50ms)
│  │  └─ service-b: GET /api/inventory/check (50ms)
│  │     └─ db-query-stock (20ms)
│  └─ service-a: payment-processing (150ms)
│     └─ service-b: POST /api/payment/process (150ms)
│        ├─ payment-validation (30ms)
│        ├─ external-pg-call (100ms)
│        └─ db-insert-payment (50ms)
```

### 5️⃣ 태그로 검색

```bash
# 특정 주문 추적
Zipkin UI에서 Tags: order.id=ORDER-1707567890123

# 에러 발생한 요청만 검색
Tags: error=true
```

---

## 7. 프로덕션 Best Practices

### ✅ 필수 체크리스트

#### 1. 샘플링 설정
```yaml
management:
  tracing:
    sampling:
      probability: 0.1  # 10% 샘플링
```

**샘플링 전략**:
- **URL 기반**: `/api/health` 제외, `/api/orders` 포함
- **에러 요청 100% 샘플링**: 에러 발생 시 항상 추적
- **느린 요청 100% 샘플링**: 응답 시간 > 1초

#### 2. Zipkin 영구 저장소
```yaml
# Docker Compose - Elasticsearch 백엔드
services:
  zipkin:
    image: openzipkin/zipkin
    environment:
      - STORAGE_TYPE=elasticsearch
      - ES_HOSTS=elasticsearch:9200
```

#### 3. 민감 정보 제외
```java
// ❌ 나쁜 예
customSpan.tag("user.password", password);
customSpan.tag("credit.card", cardNumber);

// ✅ 좋은 예
customSpan.tag("user.id", userId);
customSpan.tag("payment.masked", "****-****-****-1234");
```

#### 4. 태그 네이밍 규칙
```java
// 표준 태그 사용 (Zipkin UI에서 자동 인식)
span.tag("http.method", "POST");
span.tag("http.status_code", "200");
span.tag("db.type", "mysql");
span.tag("db.statement", "SELECT * FROM users");

// 커스텀 태그
span.tag("order.id", orderId);
span.tag("product.category", "electronics");
```

---

## 8. 트러블슈팅

### 문제 1: traceId가 전파되지 않음

**원인**: RestTemplate 사용 시 자동 전파 안 됨
**해결**: WebClient 사용 또는 RestTemplate에 Interceptor 추가

```java
@Bean
public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
            .interceptors((request, body, execution) -> {
                // traceId를 수동으로 헤더에 추가
                return execution.execute(request, body);
            })
            .build();
}
```

### 문제 2: Zipkin에 Span이 보이지 않음

**원인 1**: 샘플링에서 제외됨
**해결**: `probability: 1.0`으로 변경 (개발 환경)

**원인 2**: Zipkin 연결 실패
**해결**:
```bash
# Zipkin 로그 확인
docker logs zipkin

# Service 로그에서 Zipkin 전송 에러 확인
ERROR - Failed to send span to Zipkin
```

### 문제 3: 커스텀 Span이 생성되지 않음

**원인**: `Tracer` Bean 주입 실패
**해결**: `micrometer-tracing-bridge-brave` 의존성 확인

---

## 9. 연관 모듈

- **logging-strategy**: MDC와 traceId를 함께 사용하여 로그 추적
- **actuator-deep-dive**: `/actuator/metrics`에서 trace 관련 메트릭 확인
- **circuit-breaker-pattern**: Circuit Breaker와 함께 사용하여 장애 추적

---

## 10. 고급 주제

### A. Baggage (컨텍스트 전파)

traceId 외에 추가 정보를 전파하고 싶을 때:

```java
// Service A에서 설정
BaggageField userId = BaggageField.create("userId");
userId.updateValue(traceContext, "user-123");

// Service B에서 읽기
String userId = userId.getValue(traceContext);
```

### B. OpenTelemetry 마이그레이션

Zipkin → OpenTelemetry로 마이그레이션:

```gradle
// Brave 대신 OpenTelemetry
implementation 'io.micrometer:micrometer-tracing-bridge-otel'
implementation 'io.opentelemetry:opentelemetry-exporter-zipkin'
```

### C. Grafana Tempo 연동

Zipkin 대신 Grafana Tempo 사용:

```yaml
management:
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
```

---

## 11. 테스트 실행

```bash
# 1. Zipkin 실행
cd distributed-tracing
docker-compose up -d

# 2. Service B 실행
./gradlew :distributed-tracing:service-b:bootRun

# 3. Service A 실행 (다른 터미널)
./gradlew :distributed-tracing:service-a:bootRun

# 4. 주문 생성
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":"PROD-001","quantity":5}'

# 5. Zipkin UI 확인
open http://localhost:9411
```

---

## 12. 다음 단계

Distributed Tracing을 학습한 후:
1. **Grafana + Tempo 실습**: Zipkin 대신 Grafana Tempo 사용
2. **OpenTelemetry**: 표준 분산 추적으로 마이그레이션
3. **APM 도구**: Datadog, New Relic 등 상용 APM 활용

---

**핵심 요약**:
Distributed Tracing은 마이크로서비스 환경에서 필수적인 기술입니다.
하나의 traceId로 여러 서비스를 거치는 요청의 전체 플로우를 추적하여, 장애 원인을 빠르게 파악하고 성능 병목 지점을 시각화할 수 있습니다.
Spring Boot 3.x는 Micrometer Tracing으로 자동화된 분산 추적을 제공하며, Zipkin UI를 통해 직관적으로 확인할 수 있습니다.
