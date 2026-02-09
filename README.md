# 🚀 Spring Deep Dive

스프링 프레임워크와 백엔드 개발의 핵심 개념을 실무 중심으로 학습하는 프로젝트입니다.

## 📚 학습 로드맵

각 모듈은 독립적으로 학습할 수 있으며, 각 디렉토리의 `LEARN.md` 파일에서 상세한 학습 가이드를 확인할 수 있습니다.

---

## 🎯 Phase 1: 스프링 코어 & 기본 개념

### 빈 생명주기 & 의존성 관리
- **beancycle** - 스프링 빈의 생명주기와 초기화/소멸 콜백
- **circulardependencies** - 순환 참조 문제와 해결 방법
- **bean-post-processor** - BeanPostProcessor를 사용한 빈 커스터마이징

### AOP & 횡단 관심사
- **aop** - AOP 개념과 프록시 패턴, @Aspect 사용법
- **spel** - Spring Expression Language 활용

### 트랜잭션 & 이벤트
- **transaction** - 트랜잭션 전파 속성과 격리 수준
- **springevents** - 스프링 이벤트를 활용한 느슨한 결합

---

## ⚡ Phase 2: 동시성 & 비동기 처리

### 멀티스레딩
- **async** - @Async와 커스텀 스레드 풀 설정
- **threadpool** - 스레드 풀 설정과 작업 큐 관리
- **concurrency** - 동시성 제어와 스레드 안전성
- **virtual-threads** - Java 21 Virtual Threads 활용

### 분산 락 & 동시성 제어
- **lock** - 분산 환경에서의 락 전략 (Redis, DB)

---

## 🌐 Phase 3: 웹 & API 개발

### Spring MVC
- **basic-web** - 스프링 MVC 기본 구조
- **mvc-internals** - DispatcherServlet, HandlerMapping 내부 동작

### HTTP & 통신
- **http-client** - RestTemplate, WebClient 사용법
- **serialization-practice** - JSON 직렬화/역직렬화 전략

### 보안
- **security-jwt** - JWT 기반 인증/인가

---

## 💾 Phase 4: 데이터 액세스 & 캐싱

### JPA & 영속성
- **jpa-deep-dive** - 영속성 컨텍스트, N+1 문제, QueryDSL
- **jpa-locking** - 낙관적 락, 비관적 락

### 캐싱 전략
- **cache-practice** - @Cacheable, Cache Eviction 전략
- **redis-deep-dive** - Redis 데이터 구조와 활용

---

## 🔥 Phase 5: Production Ready

### 로깅 & 모니터링
- **logging-strategy** - MDC, Structured Logging (JSON)
- **actuator-deep-dive** - 헬스체크, 메트릭, Prometheus 연동
- **distributed-tracing** - Micrometer Tracing, Zipkin 분산 추적

### 안정성 & 장애 대응
- **circuit-breaker-pattern** - Circuit Breaker로 장애 전파 방지
- **graceful-shutdown** - 무중단 배포와 우아한 종료

---

## 🧪 Phase 6: 테스트 & 품질

### 테스트 전략
- **test-practice** - 단위/통합 테스트 작성법
- **testcontainers-practice** - Testcontainers로 실제 환경 테스트

---

## 🔮 Phase 7: 고급 패턴 & 메시징

### 메시징 & 이벤트 드리븐
- **curve/kafka** - Kafka 프로듀서/컨슈머, 파티션 전략
- **curve/spring** - Spring Integration, 메시징 패턴

---

## 🗺️ 추천 학습 순서

### 🟢 초급: 스프링 기본 다지기 (1-2개월)
```
1. beancycle → circulardependencies → bean-post-processor
2. aop → transaction
3. basic-web → mvc-internals
4. jpa-deep-dive
```

### 🟡 중급: 실무 필수 기술 (2-3개월)
```
5. async → threadpool → concurrency
6. cache-practice → redis-deep-dive
7. lock → jpa-locking
8. security-jwt
9. http-client
10. test-practice → testcontainers-practice
```

### 🔴 고급: 프로덕션 환경 대비 (3-4개월)
```
11. logging-strategy ⭐ (최우선)
12. actuator-deep-dive ⭐ (최우선)
13. graceful-shutdown
14. circuit-breaker-pattern
15. virtual-threads
16. curve/kafka → curve/spring
```

---

## 📖 각 모듈 학습 방법

각 모듈은 다음 구조로 구성되어 있습니다:

```
module-name/
├── LEARN.md              # 학습 가이드 (필독!)
│   ├── 📌 언제 사용하는가?
│   ├── 핵심 개념
│   ├── 실습 시나리오
│   └── Best Practices
├── src/main/java/        # 실습 코드
└── src/test/java/        # 테스트 코드
```

**학습 단계**:
1. `LEARN.md` 읽기 (개념 이해)
2. 코드 실행 및 디버깅 (동작 확인)
3. 테스트 코드 작성 (이해도 검증)
4. 실무 적용 사례 고민

---

## 🚀 시작하기

### 프로젝트 빌드
```bash
# 전체 프로젝트 빌드
./gradlew build

# 특정 모듈만 빌드
./gradlew :logging-strategy:build
```

### 특정 모듈 실행
```bash
# 애플리케이션 실행
./gradlew :logging-strategy:bootRun

# 테스트 실행
./gradlew :jpa-deep-dive:test
```

---

## 🎓 학습 목표

이 프로젝트를 완료하면 다음을 할 수 있습니다:

- ✅ 스프링의 내부 동작 원리를 깊이 있게 이해
- ✅ 프로덕션 환경에서 발생하는 문제 해결 능력
- ✅ 성능 최적화와 동시성 제어 전략 수립
- ✅ 마이크로서비스 아키텍처 설계 및 구현
- ✅ 안정적인 백엔드 시스템 구축

---

## 📌 다음 단계로 나아가기

### 현재 프로젝트에 없는 영역 (향후 추가 예정)

#### 🔵 Observability (관측 가능성)
- [x] `actuator-deep-dive` - 헬스체크, 메트릭 엔드포인트 ✅ 완료
- [x] `distributed-tracing` - Micrometer Tracing, Zipkin 분산 추적 ✅ 완료

#### 🔵 Cloud Native
- [ ] `spring-cloud-config` - 중앙 설정 관리
- [ ] `service-discovery` - Eureka/Consul 서비스 디스커버리
- [ ] `api-gateway` - Spring Cloud Gateway
- [ ] `resilience4j-advanced` - Rate Limiter, Bulkhead

#### 🔵 Advanced Data Patterns
- [ ] `event-sourcing` - 이벤트 소싱 패턴
- [ ] `cqrs-pattern` - 읽기/쓰기 분리
- [ ] `saga-pattern` - 분산 트랜잭션 관리
- [ ] `outbox-pattern` - 메시지 발행 신뢰성

#### 🔵 Performance Engineering
- [ ] `connection-pool-tuning` - HikariCP 최적화
- [ ] `query-optimization` - 쿼리 성능 튜닝
- [ ] `spring-batch` - 대용량 배치 처리
- [ ] `reactive-webflux` - 리액티브 프로그래밍

#### 🔵 Testing Excellence
- [ ] `archunit-practice` - 아키텍처 테스트
- [ ] `contract-testing` - Pact, Spring Cloud Contract
- [ ] `performance-testing` - Gatling/K6 성능 테스트
- [ ] `chaos-engineering` - 카오스 엔지니어링


