# Spring AOP 동작 원리

스프링 AOP가 어떻게 동작하는지, 그리고 프록시(Proxy) 객체가 어떻게 생성되는지 학습합니다.

## 1. AOP와 프록시 패턴
스프링 AOP는 **프록시 패턴**을 기반으로 동작합니다.
*   우리가 `@Autowired`로 주입받는 객체는 실제 비즈니스 로직을 가진 객체(Target)가 아니라, 그 객체를 감싸고 있는 **프록시(대리자)**입니다.
*   프록시는 메서드 호출을 가로채서 부가 기능(Aspect)을 실행한 후, 실제 Target 객체의 메서드를 호출합니다.

## 2. 프록시의 종류

### A. JDK Dynamic Proxy
*   **대상**: 인터페이스가 있는 클래스
*   **원리**: Java의 리플렉션(Reflection) 기능을 이용하여 런타임에 인터페이스를 구현한 프록시 객체를 생성합니다.
*   **특징**: 인터페이스에 정의된 메서드만 프록시 적용이 가능합니다.

### B. CGLIB (Code Generation Library)
*   **대상**: 인터페이스가 없는 클래스 (또는 강제로 설정 시)
*   **원리**: 바이트코드를 조작하여 Target 클래스를 **상속(Extends)**받는 프록시 객체를 생성합니다.
*   **특징**:
    *   상속을 이용하므로 `final` 클래스나 `final` 메서드에는 적용할 수 없습니다.
    *   스프링 부트 2.0부터는 성능상의 이점 등으로 인해 **기본적으로 CGLIB를 선호**합니다.

## 3. 실습 결과 확인 포인트
`ProxyCheckRunner` 실행 결과를 통해 다음을 확인합니다.

1.  **클래스 이름**:
    *   JDK Proxy: `com.sun.proxy.$Proxy...` 형태
    *   CGLIB: `...$$EnhancerBySpringCGLIB$$...` 형태
2.  **AopUtils 확인**:
    *   `isAopProxy()`: true
    *   `isCglibProxy()` vs `isJdkDynamicProxy()` 결과 비교
3.  **Final 메서드 제약**:
    *   `noInterfaceService.doFinalAction()` 호출 시 `[AOP] Before method...` 로그가 출력되지 않습니다.
    *   이유: CGLIB는 메서드를 오버라이딩하여 AOP 로직을 심는데, `final` 메서드는 오버라이딩이 불가능하기 때문입니다.

## 4. 주의사항 (Self-Invocation)
프록시 객체를 통해 메서드를 호출할 때만 AOP가 적용됩니다.
*   **문제**: 빈 내부에서 자신의 다른 메서드를 호출(`this.method()`)하면 프록시를 거치지 않고 직접 호출하므로 **AOP가 적용되지 않습니다.**

## 5. 다양한 Advice 종류
`LoggingAspect`에 추가된 다양한 Advice 어노테이션을 통해 AOP 실행 시점을 제어할 수 있습니다.

*   **@Before**: 타겟 메서드가 실행되기 **전**에 실행됩니다.
*   **@After**: 타겟 메서드의 실행 결과(성공, 예외)와 상관없이 **종료 후**에 무조건 실행됩니다. (finally 블록과 유사)
*   **@AfterReturning**: 타겟 메서드가 **정상적으로 종료**된 후에 실행됩니다. `returning` 속성을 통해 리턴값을 받아올 수 있습니다.
*   **@AfterThrowing**: 타겟 메서드 실행 중 **예외가 발생**했을 때 실행됩니다. `throwing` 속성을 통해 예외 객체를 받아올 수 있습니다.
*   **@Around**: 타겟 메서드 실행 **전과 후**를 모두 제어할 수 있는 가장 강력한 Advice입니다. `ProceedingJoinPoint.proceed()`를 호출하여 타겟 메서드를 실행해야 하며, 실행 여부나 리턴값 조작, 예외 처리 등을 직접 제어할 수 있습니다.

---

## 6. Advice 실행 순서 다이어그램

정상 실행 시:
```
요청
 │
 ▼
@Around (before proceed)
 │
 ▼
@Before
 │
 ▼
[ 실제 메서드 실행 ]
 │
 ▼
@Around (after proceed)
 │
 ▼
@AfterReturning
 │
 ▼
@After (finally)
```

예외 발생 시:
```
요청
 │
 ▼
@Around (before proceed)
 │
 ▼
@Before
 │
 ▼
[ 실제 메서드 → 예외 발생 ]
 │
 ▼
@Around (catch block → re-throw)
 │
 ▼
@AfterThrowing
 │
 ▼
@After (finally)
 │
 ▼
예외 전파
```

> `@After`는 항상 마지막에 실행됩니다 (`try-finally`의 `finally`와 동일한 의미).

---

## 7. Pointcut 표현식 문법

```java
// 본 모듈의 Pointcut
@Pointcut("execution(* com.exam.springdeepdive.aop.*Service*.*(..))")

// 문법 구조
execution( [수식어] [반환타입] [패키지.클래스.메서드]([파라미터]) )
//           생략가능   *      com.example.*Service*.*    (..)
//  *  = 모든 반환 타입
//  .. = 0개 이상의 파라미터 (어떤 파라미터도 매칭)
```

### 자주 사용하는 Pointcut 패턴

```java
// 1. 특정 패키지 하위 모든 메서드
@Pointcut("execution(* com.example.service..*(..))")

// 2. 특정 어노테이션이 붙은 메서드
@Pointcut("@annotation(com.example.Transactional)")

// 3. 특정 어노테이션이 붙은 클래스의 모든 메서드
@Pointcut("@within(org.springframework.stereotype.Service)")

// 4. 특정 타입의 메서드 (상속 포함)
@Pointcut("within(com.example.service.*)")

// 5. 조합 (AND / OR / NOT)
@Pointcut("execution(* com.example..*Service.*(..)) && !execution(* *.get*(..))")
```

---

## 8. 다중 Aspect 실행 순서 (@Order)

같은 메서드에 여러 Aspect가 적용될 때, 실행 순서는 `@Order`로 제어합니다.

```java
@Aspect
@Order(1)  // 숫자가 낮을수록 먼저 실행 (바깥쪽 래핑)
@Component
public class SecurityAspect { ... }

@Aspect
@Order(2)
@Component
public class LoggingAspect { ... }

@Aspect
@Order(3)  // 가장 안쪽
@Component
public class TransactionAspect { ... }
```

```
요청 진입:   SecurityAspect → LoggingAspect → TransactionAspect → [메서드]
응답 반환:   [메서드] → TransactionAspect → LoggingAspect → SecurityAspect
```

---

## 9. 실무 활용 패턴

### 패턴 1: 성능 측정 (@Around)

```java
@Around("execution(* com.example.service.*.*(..))")
public Object measureTime(ProceedingJoinPoint pjp) throws Throwable {
    long start = System.currentTimeMillis();
    try {
        return pjp.proceed();
    } finally {
        long duration = System.currentTimeMillis() - start;
        log.info("[PERF] {}.{}() → {}ms",
            pjp.getTarget().getClass().getSimpleName(),
            pjp.getSignature().getName(),
            duration);
    }
}
```

### 패턴 2: 커스텀 어노테이션 + AOP (본 모듈의 redis 분산 락 방식)

```java
// 1. 어노테이션 정의
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Retry { int times() default 3; }

// 2. AOP로 처리
@Around("@annotation(retry)")
public Object handleRetry(ProceedingJoinPoint pjp, Retry retry) throws Throwable {
    for (int i = 0; i < retry.times(); i++) {
        try {
            return pjp.proceed();
        } catch (Exception e) {
            if (i == retry.times() - 1) throw e;
            log.warn("Retry {}/{}", i + 1, retry.times());
        }
    }
    throw new IllegalStateException("Unreachable");
}
```

### 패턴 3: @AfterThrowing으로 공통 예외 로깅

```java
@AfterThrowing(pointcut = "execution(* com.example..*(..))", throwing = "ex")
public void logException(JoinPoint jp, Exception ex) {
    log.error("[ERROR] {}.{}() → {}",
        jp.getTarget().getClass().getSimpleName(),
        jp.getSignature().getName(),
        ex.getMessage());
    // 슬랙 알림, 모니터링 연동 등
}
```

### AOP 실무 체크리스트

- [ ] Self-Invocation(같은 클래스 내 호출)은 AOP가 적용되지 않는다.
- [ ] `final` 메서드, `private` 메서드는 CGLIB가 오버라이드할 수 없어 AOP 무효.
- [ ] `@Around`에서 `proceed()`를 호출하지 않으면 실제 메서드가 실행되지 않는다.
- [ ] 여러 Aspect가 같은 메서드에 적용될 때 `@Order`로 순서를 명시한다.
- [ ] Pointcut 표현식이 의도한 범위만 매칭하는지 반드시 테스트로 검증한다.
