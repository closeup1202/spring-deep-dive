# Actuator Deep Dive: 프로덕션 준비 기능

Spring Boot Actuator를 사용하여 애플리케이션의 상태를 모니터링하고, 메트릭을 수집하며, 운영에 필요한 정보를 노출하는 방법을 학습합니다.

## 📌 언제 사용하는가?

### ✅ 반드시 사용해야 하는 경우
1. **프로덕션 배포**: 모든 프로덕션 환경에서 필수
2. **Kubernetes 환경**: Liveness/Readiness Probe 설정
3. **모니터링 연동**: Prometheus, Datadog, CloudWatch 등과 통합
4. **장애 대응**: 헬스체크, 로그 레벨 동적 변경
5. **성능 분석**: 메트릭 수집 및 대시보드 구축

### 🎯 Actuator 활용 효과
- **빠른 장애 감지**: 헬스체크로 서비스 상태 실시간 파악
- **운영 효율화**: 로그 레벨 변경, 환경 변수 확인 등 재배포 없이 처리
- **데이터 기반 의사결정**: 메트릭 수집으로 성능 병목 지점 파악
- **SLA 준수**: 응답 시간, 에러율 등 SLO 모니터링

### ⚠️ 보안 주의사항
- **민감 정보 노출 위험**: 환경 변수, 설정 정보에 비밀번호/API 키 포함 가능
- **인증/인가 필수**: 프로덕션에서는 반드시 보안 설정 적용
- **최소 권한 원칙**: 필요한 엔드포인트만 노출

---

## 1. Actuator란?

Spring Boot Actuator는 **프로덕션 환경에서 애플리케이션을 모니터링하고 관리**하기 위한 기능을 제공합니다.

### 주요 엔드포인트

| 엔드포인트 | 설명 | 프로덕션 노출 |
|-----------|------|--------------|
| `/actuator/health` | 애플리케이션 상태 (DB, Redis 등) | ✅ Public |
| `/actuator/info` | 애플리케이션 정보 (버전, 팀 등) | ✅ Public |
| `/actuator/metrics` | 메트릭 수집 (CPU, 메모리, HTTP 요청 등) | ⚠️ 인증 필요 |
| `/actuator/prometheus` | Prometheus 형식 메트릭 | ⚠️ 인증 필요 |
| `/actuator/env` | 환경 변수 조회 | ❌ 매우 주의 |
| `/actuator/loggers` | 로그 레벨 동적 변경 | ⚠️ 인증 필요 |
| `/actuator/beans` | 스프링 빈 목록 | ⚠️ 인증 필요 |
| `/actuator/threaddump` | 스레드 덤프 | ⚠️ 인증 필요 |
| `/actuator/heapdump` | 힙 덤프 (메모리 분석) | ❌ 매우 주의 |

---

## 2. Health Indicator (헬스 체크)

### A. 기본 제공 Health Indicator
Spring Boot는 자동으로 여러 시스템의 상태를 체크합니다:

- **DataSourceHealthIndicator**: 데이터베이스 연결
- **RedisHealthIndicator**: Redis 연결
- **DiskSpaceHealthIndicator**: 디스크 공간
- **PingHealthIndicator**: 기본 응답

### B. 커스텀 Health Indicator 구현

**CustomHealthIndicator.java** - 외부 시스템 상태 체크
```java
@Component
public class CustomHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        boolean isHealthy = checkExternalSystem();

        if (isHealthy) {
            return Health.up()
                    .withDetail("service", "CustomService")
                    .withDetail("status", "All systems operational")
                    .build();
        } else {
            return Health.down()
                    .withDetail("error", "External system unavailable")
                    .build();
        }
    }
}
```

### C. Health 상태
- **UP**: 정상 (HTTP 200)
- **DOWN**: 장애 (HTTP 503 Service Unavailable)
- **OUT_OF_SERVICE**: 점검 중
- **UNKNOWN**: 알 수 없음

### D. Kubernetes Liveness/Readiness Probe
```yaml
# application.yml
management:
  endpoint:
    health:
      probes:
        enabled: true
```

**Kubernetes 설정**:
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 5
  periodSeconds: 5
```

---

## 3. Metrics (메트릭)

### A. Micrometer란?
메트릭 수집을 위한 **파사드(Facade)** 라이브러리로, 다양한 모니터링 시스템과 통합 가능:
- Prometheus
- Datadog
- CloudWatch
- InfluxDB
- Graphite

### B. 메트릭 타입

#### 1️⃣ Counter (누적 값)
```java
Counter orderCounter = Counter.builder("orders.created")
        .description("Total orders created")
        .tag("type", "total")
        .register(meterRegistry);

orderCounter.increment();
```
**사용 사례**: 주문 수, API 호출 수, 에러 수

#### 2️⃣ Gauge (현재 상태 값)
```java
AtomicInteger activeOrders = meterRegistry.gauge(
    "orders.active",
    new AtomicInteger(0)
);

activeOrders.incrementAndGet();
```
**사용 사례**: 활성 커넥션 수, 큐 크기, 메모리 사용량

#### 3️⃣ Timer (이벤트 빈도 + 소요 시간)
```java
Timer orderTimer = Timer.builder("orders.processing.time")
        .description("Order processing time")
        .register(meterRegistry);

orderTimer.record(() -> {
    // 주문 처리 로직
});
```
**사용 사례**: API 응답 시간, 쿼리 실행 시간

#### 4️⃣ Summary (분포 통계)
```java
meterRegistry.summary("orders.amount")
        .record(50000.0);
```
**사용 사례**: 주문 금액 분포, 요청 크기

### C. 기본 제공 메트릭
- **JVM**: `jvm.memory.used`, `jvm.gc.pause`
- **HTTP**: `http.server.requests` (응답 시간, 상태 코드별 count)
- **Thread Pool**: `executor.active`, `executor.queued`
- **Database**: `hikaricp.connections.active`

### D. 태그를 사용한 메트릭 세분화
```java
Counter.builder("orders.created")
        .tag("category", "electronics") // 카테고리별 구분
        .tag("region", "seoul")          // 지역별 구분
        .register(meterRegistry)
        .increment();
```

**Prometheus 쿼리**:
```promql
# 전자제품 카테고리 주문 수
orders_created_total{category="electronics"}

# 서울 지역 주문 증가율
rate(orders_created_total{region="seoul"}[5m])
```

---

## 4. Custom Endpoint (커스텀 엔드포인트)

### A. 기본 구조
```java
@Component
@Endpoint(id = "custom")
public class CustomEndpoint {

    @ReadOperation  // GET
    public CustomInfo getInfo() {
        return new CustomInfo();
    }

    @WriteOperation // POST
    public Map<String, String> updateConfig(String key, String value) {
        return Map.of("updated", key);
    }

    @DeleteOperation // DELETE
    public void clearCache() {
        // 캐시 삭제 로직
    }
}
```

### B. Selector를 사용한 동적 경로
```java
@ReadOperation
public CacheStats getCacheStats(@Selector String cacheName) {
    return getCacheStatsFor(cacheName);
}
```
접근: `GET /actuator/cache-stats/products`

---

## 5. 보안 설정

### A. 엔드포인트별 보안 전략

**ActuatorSecurityConfig.java**:
```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) {
    http.authorizeHttpRequests(authorize -> authorize
        // Public (Kubernetes probe)
        .requestMatchers(
            EndpointRequest.to("health", "info")
        ).permitAll()

        // Admin only
        .requestMatchers(
            EndpointRequest.toAnyEndpoint()
        ).hasRole("ADMIN")
    );
    return http.build();
}
```

### B. 민감 정보 마스킹
```yaml
# application.yml
management:
  endpoint:
    env:
      show-values: when-authorized  # 인증된 사용자만 값 표시
```

### C. IP 화이트리스트 (프로덕션 권장)
```java
.requestMatchers("/actuator/**")
    .access(new IpAddressMatcher("10.0.0.0/8"))
```

---

## 6. Prometheus 연동

### A. 의존성 추가
```gradle
implementation 'io.micrometer:micrometer-registry-prometheus'
```

### B. 설정
```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

### C. Prometheus 설정 (`prometheus.yml`)
```yaml
scrape_configs:
  - job_name: 'spring-actuator'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']
```

### D. Grafana 대시보드
1. Prometheus 데이터소스 추가
2. Spring Boot 2.1 System Dashboard 임포트
3. 커스텀 패널 생성 (주문 수, 응답 시간 등)

---

## 7. 실습 시나리오

### 1️⃣ 헬스체크 확인
```bash
# 전체 헬스 상태
curl http://localhost:8080/actuator/health

# 응답 예시
{
  "status": "UP",
  "components": {
    "customHealthIndicator": {
      "status": "UP",
      "details": {
        "service": "CustomService",
        "status": "All systems operational"
      }
    },
    "db": {
      "status": "UP",
      "details": {
        "database": "H2",
        "responseTime": "5ms"
      }
    }
  }
}
```

### 2️⃣ 메트릭 수집
```bash
# 주문 생성
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"category":"electronics","amount":50000}'

# 주문 메트릭 확인 (Basic Auth 필요)
curl -u admin:admin123 http://localhost:8080/actuator/metrics/orders.created

# 응답 예시
{
  "name": "orders.created",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 5.0
    }
  ],
  "availableTags": [
    {
      "tag": "type",
      "values": ["total"]
    }
  ]
}
```

### 3️⃣ 커스텀 엔드포인트 호출
```bash
# 커스텀 정보 조회
curl -u admin:admin123 http://localhost:8080/actuator/custom

# 캐시 통계 조회
curl -u admin:admin123 http://localhost:8080/actuator/cache-stats/products
```

### 4️⃣ 로그 레벨 동적 변경
```bash
# 현재 로그 레벨 확인
curl -u admin:admin123 http://localhost:8080/actuator/loggers/com.example.actuator

# 로그 레벨 변경 (재배포 불필요!)
curl -u admin:admin123 -X POST \
  http://localhost:8080/actuator/loggers/com.example.actuator \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel":"DEBUG"}'
```

### 5️⃣ Prometheus 메트릭 확인
```bash
curl http://localhost:8080/actuator/prometheus

# 응답 예시 (Prometheus 포맷)
# HELP orders_created_total Total number of orders created
# TYPE orders_created_total counter
orders_created_total{application="actuator-deep-dive",type="total"} 5.0

# HELP http_server_requests_seconds
# TYPE http_server_requests_seconds summary
http_server_requests_seconds_count{method="POST",uri="/api/orders",status="200"} 5.0
http_server_requests_seconds_sum{method="POST",uri="/api/orders",status="200"} 0.523
```

---

## 8. 프로덕션 Best Practices

### ✅ 필수 체크리스트

#### 1. 보안
- [ ] Actuator 엔드포인트에 인증/인가 설정
- [ ] `/actuator/health`, `/actuator/info`만 Public
- [ ] 민감 정보 마스킹 (`show-values: when-authorized`)
- [ ] IP 화이트리스트 설정 (AWS Security Group 등)

#### 2. 헬스체크
- [ ] 모든 외부 의존성에 대한 Health Indicator 구현
- [ ] Kubernetes Liveness/Readiness Probe 설정
- [ ] Health Indicator 타임아웃 설정 (느린 DB 체크 방지)

#### 3. 메트릭
- [ ] 비즈니스 메트릭 정의 (주문 수, 결제 성공률 등)
- [ ] 태그를 사용한 메트릭 세분화
- [ ] SLO 기반 알람 설정 (응답 시간 95% < 200ms)
- [ ] 메트릭 retention 정책 (Prometheus 15일 등)

#### 4. 모니터링
- [ ] Grafana 대시보드 구축
- [ ] 알람 룰 설정 (Slack, PagerDuty 연동)
- [ ] 정기적인 메트릭 리뷰 (주간/월간)

---

## 9. 트러블슈팅

### 문제 1: Health 상태가 DOWN으로 표시
**원인**: 특정 Health Indicator가 실패
**해결**:
```bash
curl http://localhost:8080/actuator/health | jq
```
어떤 컴포넌트가 DOWN인지 확인 후 해당 시스템(DB, Redis 등) 점검

### 문제 2: 메트릭이 수집되지 않음
**원인**: MeterRegistry Bean이 주입되지 않음
**해결**: `micrometer-core` 의존성 확인 및 Auto-configuration 로그 확인

### 문제 3: Prometheus 엔드포인트 404
**원인**: `micrometer-registry-prometheus` 의존성 누락
**해결**:
```gradle
implementation 'io.micrometer:micrometer-registry-prometheus'
```

---

## 10. 연관 모듈

- **logging-strategy**: MDC와 함께 사용하여 traceId 기반 로그 추적
- **graceful-shutdown**: Health Indicator와 함께 무중단 배포
- **circuit-breaker-pattern**: 외부 API Health Indicator에 Circuit Breaker 적용

---

## 11. 테스트 실행

```bash
# 1. 애플리케이션 실행
./gradlew :actuator-deep-dive:bootRun

# 2. 테스트 실행
./gradlew :actuator-deep-dive:test

# 3. Actuator 엔드포인트 탐색
# Public 엔드포인트 (인증 불필요)
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/info
curl http://localhost:8080/actuator/prometheus

# Admin 엔드포인트 (Basic Auth)
curl -u admin:admin123 http://localhost:8080/actuator/metrics
curl -u admin:admin123 http://localhost:8080/actuator/custom
curl -u admin:admin123 http://localhost:8080/actuator/env
```

---

## 12. 다음 단계

Actuator를 학습한 후에는:
1. **Prometheus + Grafana 실습**: 실제 모니터링 대시보드 구축
2. **Distributed Tracing**: Zipkin/Jaeger와 연동하여 분산 추적
3. **APM 연동**: Datadog, New Relic 등 상용 APM 도구 활용

---

**핵심 요약**:
Spring Boot Actuator는 프로덕션 환경에서 애플리케이션을 모니터링하고 관리하기 위한 필수 도구입니다.
헬스체크, 메트릭 수집, 커스텀 엔드포인트를 통해 시스템 상태를 실시간으로 파악하고, 장애에 빠르게 대응할 수 있습니다.
보안 설정을 반드시 적용하여 민감한 정보가 노출되지 않도록 주의해야 합니다.
