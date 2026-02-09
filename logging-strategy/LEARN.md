# Logging Strategy: MDC & Structured Logging

프로덕션 환경에서 로그를 효과적으로 추적하고 분석하기 위한 로깅 전략을 학습합니다.

## 📌 언제 사용하는가?

### ✅ 반드시 사용해야 하는 경우
1. **마이크로서비스 환경**: 여러 서비스를 거치는 요청을 추적할 때
2. **멀티스레드 환경**: 동시에 처리되는 요청들을 구분해야 할 때
3. **프로덕션 장애 대응**: 특정 사용자/요청의 로그만 필터링해야 할 때
4. **ELK Stack 사용**: Elasticsearch에 로그를 수집하여 분석할 때
5. **SLA 모니터링**: 요청별 처리 시간과 성공/실패를 추적할 때

### ⚠️ 주의가 필요한 경우
- **메모리 누수 방지**: MDC.clear()를 반드시 호출해야 함 (ThreadPool 재사용 시)
- **비동기 처리**: @Async, CompletableFuture 사용 시 MDC가 자동 전파되지 않음
- **민감 정보**: 개인정보(주민번호, 카드번호)를 로그에 남기면 안 됨

---

## 1. MDC (Mapped Diagnostic Context)란?

MDC는 **스레드 로컬(ThreadLocal)** 기반으로 동작하는 맵 구조로, 로그에 컨텍스트 정보를 자동으로 추가할 수 있습니다.

### 동작 원리
```java
// 1. MDC에 값 설정
MDC.put("traceId", "abc-123");
MDC.put("userId", "user-999");

// 2. 로그 출력 시 자동으로 포함됨
log.info("Processing order");
// 출력: [abc-123] [user-999] INFO - Processing order

// 3. 요청 완료 후 반드시 정리 (메모리 누수 방지)
MDC.clear();
```

### 핵심 장점
- **코드 중복 제거**: 모든 로그에 일일이 traceId를 파라미터로 전달할 필요 없음
- **요청 추적**: 분산 환경에서 하나의 요청이 여러 서비스를 거칠 때 추적 가능
- **로그 필터링**: Kibana 등에서 특정 traceId로 필터링하여 요청 전체 흐름 확인

---

## 2. MDC 구현 패턴

### A. Filter를 사용한 자동 설정 (권장)
`MDCFilter.java` - 모든 HTTP 요청에 자동으로 traceId 부여

```java
@Component
@Order(1) // 가장 먼저 실행
public class MDCFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(...) {
        try {
            // 1. 헤더에서 traceId 추출 또는 생성
            String traceId = request.getHeader("X-Trace-Id");
            if (traceId == null) {
                traceId = UUID.randomUUID().toString().substring(0, 8);
            }
            MDC.put("traceId", traceId);
            MDC.put("userId", extractUserId(request));

            // 2. 다음 필터로 전달
            filterChain.doFilter(request, response);
        } finally {
            // 3. 요청 완료 후 반드시 정리
            MDC.clear();
        }
    }
}
```

**핵심**: `finally` 블록에서 `MDC.clear()`를 반드시 호출해야 합니다.
- WAS는 스레드풀을 재사용하므로, 정리하지 않으면 다음 요청에 이전 값이 남아있음!

### B. AOP를 사용한 메서드 레벨 추적
`LoggingAspect.java` - 컨트롤러 메서드 호출 시 자동으로 실행 시간 로깅

```java
@Aspect
@Component
public class LoggingAspect {
    @Around("execution(* com.example.logging.controller..*(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        log.info("▶ Method started: {}", joinPoint.getSignature().toShortString());

        try {
            return joinPoint.proceed();
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("◀ Method completed in {}ms", executionTime);
        }
    }
}
```

---

## 3. Structured Logging (구조화된 로깅)

로그를 나중에 파싱하기 쉽게 **JSON 형식**으로 출력합니다.

### 왜 필요한가?
- **일반 텍스트 로그**: 사람이 읽기 쉽지만, 기계가 파싱하기 어려움
- **JSON 로그**: ELK Stack(Elasticsearch)에서 검색/분석/시각화에 최적

### logback-spring.xml 설정
```xml
<appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <!-- MDC 필드를 JSON에 자동 포함 -->
        <includeMdcKeyName>traceId</includeMdcKeyName>
        <includeMdcKeyName>userId</includeMdcKeyName>

        <!-- 커스텀 필드 추가 -->
        <customFields>{"application":"my-app","environment":"prod"}</customFields>
    </encoder>
</appender>
```

### 출력 예시
**일반 로그**:
```
2025-02-09 14:30:15.123 [http-nio-8080-exec-1] [abc-123] [user-999] INFO  - Processing order
```

**JSON 로그** (ELK Stack 전송용):
```json
{
  "timestamp": "2025-02-09T14:30:15.123+09:00",
  "level": "INFO",
  "thread": "http-nio-8080-exec-1",
  "logger": "com.example.logging.service.OrderService",
  "message": "Processing order",
  "traceId": "abc-123",
  "userId": "user-999",
  "application": "my-app",
  "environment": "prod"
}
```

---

## 4. 실습 시나리오

### 1️⃣ 기본 MDC 동작 확인
```bash
# 1. 애플리케이션 실행 (일반 로그)
./gradlew :logging-strategy:bootRun

# 2. API 호출
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user-123" \
  -d '{"orderId":"ORDER-001","amount":10000}'

# 3. 로그 확인
# [abc-123] [user-123] INFO - Creating order - orderId: ORDER-001
# [abc-123] [user-123] INFO - Processing order in service layer
# [abc-123] [user-123] DEBUG - Validating order
# [abc-123] [user-123] INFO - Order created successfully
```

**관찰 포인트**:
- 모든 로그에 동일한 `traceId`가 자동으로 포함됨
- Controller → Service → Repository 모든 계층에서 MDC 값이 유지됨

### 2️⃣ JSON 로깅 (ELK Stack 연동)
```bash
# JSON 프로파일로 실행
./gradlew :logging-strategy:bootRun --args='--spring.profiles.active=json'

# 동일한 API 호출 시 JSON 형식으로 출력됨
```

### 3️⃣ 분산 추적 시뮬레이션 (MSA)
```bash
# 서비스 A에서 생성한 traceId를 서비스 B로 전달
curl -X POST http://localhost:8080/api/orders \
  -H "X-Trace-Id: external-trace-999" \
  -H "X-User-Id: user-456" \
  -d '{"orderId":"ORDER-002","amount":20000}'

# 로그 확인: [external-trace-999] 가 출력됨
# 실제 MSA에서는 OpenTelemetry, Zipkin 등을 사용하여 자동 전파
```

### 4️⃣ 에러 로깅 전략
```bash
curl -X POST http://localhost:8080/api/orders/error

# 로그 출력:
# [xyz-789] ERROR - Order processing failed
# java.lang.IllegalArgumentException: Invalid order data
#   at com.example.logging.service.OrderService.processOrderWithError(...)
```

---

## 5. 프로덕션 환경 Best Practices

### ✅ 필수 적용 사항
1. **MDC.clear() 호출**: Filter의 `finally` 블록에서 반드시 정리
2. **민감 정보 제외**: 비밀번호, 카드번호, 주민번호 등은 로그에서 제외
3. **로그 레벨 관리**:
   - 개발(dev): DEBUG
   - 스테이징(staging): INFO
   - 프로덕션(prod): WARN (필요 시 INFO)
4. **로그 로테이션**: 디스크 용량 관리를 위해 일별/주별 로테이션
5. **성능 고려**: 로그 출력이 많으면 I/O 부하 증가 → 비동기 로깅 사용

### ⚠️ 비동기 환경에서의 MDC 전파
`@Async`, `CompletableFuture` 사용 시 MDC가 자동 전파되지 않습니다.

**해결 방법**: `TaskDecorator` 사용
```java
@Configuration
public class AsyncConfig implements AsyncConfigurer {
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(new MDCTaskDecorator()); // MDC 복사
        executor.initialize();
        return executor;
    }
}

public class MDCTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable task) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                task.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
```

---

## 6. 연관 모듈

- `async`: 비동기 환경에서 MDC 전파 (TaskDecorator)
- `mvc-internals`: Filter 동작 원리 이해
- `aop`: AOP를 사용한 로깅 전략
- `graceful-shutdown`: 애플리케이션 종료 시 로그 안전하게 처리

---

## 7. 주요 참고 사항

### MDC의 한계
- **ThreadLocal 기반**: 스레드가 바뀌면 값이 전파되지 않음
- **비동기 처리**: 별도의 TaskDecorator 구현 필요
- **Reactive Stack**: WebFlux에서는 Reactor Context 사용 (MDC 사용 불가)

### 로그 보안
```java
// ❌ 나쁜 예
log.info("User password: {}", user.getPassword());

// ✅ 좋은 예
log.info("User login successful - userId: {}", user.getId());
```

### 로그 성능
- **Lazy Evaluation**: `log.debug(() -> expensiveOperation())` 사용
- **Async Appender**: 로그를 별도 스레드에서 비동기로 처리

---

## 8. 테스트 실행

```bash
# 1. 단위 테스트 실행
./gradlew :logging-strategy:test

# 2. 애플리케이션 실행 (dev 프로파일)
./gradlew :logging-strategy:bootRun

# 3. JSON 로깅 테스트 (json 프로파일)
./gradlew :logging-strategy:bootRun --args='--spring.profiles.active=json'
```

---

## 9. 실무 적용 체크리스트

- [ ] MDCFilter를 모든 HTTP 요청에 적용
- [ ] MDC.clear()를 finally 블록에서 호출
- [ ] 민감 정보 로깅 제외 (마스킹 처리)
- [ ] 프로파일별 로그 레벨 설정 (dev/prod)
- [ ] ELK Stack 연동 시 JSON 로깅 사용
- [ ] 비동기 환경에서 TaskDecorator 구현
- [ ] 로그 로테이션 정책 설정
- [ ] 에러 발생 시 traceId를 클라이언트에 반환 (고객 문의 시 추적)

---

**핵심 요약**:
MDC를 사용하면 모든 로그에 traceId를 자동으로 포함시켜 분산 환경에서 요청을 추적할 수 있습니다.
Structured Logging(JSON)을 사용하면 로그를 기계가 파싱하기 쉽게 만들어 ELK Stack 등에서 분석할 수 있습니다.
