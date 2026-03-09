# Spring Expression Language (SpEL)

Spring Expression Language(SpEL)는 런타임에 객체 그래프를 조회하고 조작할 수 있는 강력한 표현 언어입니다.

## EvaluationContext

SpEL 표현식을 평가할 때 `EvaluationContext` 인터페이스를 사용하여 프로퍼티, 메서드, 필드 등을 해석하고 타입 변환을 수행합니다.
Spring은 주로 두 가지 구현체를 제공합니다.

### 1. StandardEvaluationContext

- SpEL의 모든 기능을 제공합니다.
- 리플렉션을 사용하여 임의의 클래스, 메서드, 필드에 접근할 수 있습니다.
- **보안 이슈**: 사용자 입력을 그대로 표현식으로 사용할 경우, 악의적인 코드가 실행될 수 있는 위험이 있습니다 (예: `Runtime.getRuntime().exec(...)`).
- 따라서 신뢰할 수 없는 소스에서 온 표현식을 평가할 때는 사용을 지양해야 합니다.

```java
StandardEvaluationContext context = new StandardEvaluationContext();
// 모든 기능 사용 가능 (객체 생성, 정적 메서드 호출 등)
```

### 2. SimpleEvaluationContext (권장)

- SpEL 언어 문법의 일부만 지원하도록 제한된 컨텍스트입니다.
- Java 타입 참조, 생성자, 빈 참조 등을 제외하고, 주로 데이터 바인딩 용도로 사용됩니다.
- **보안**: 사용자 정의 표현식을 안전하게 평가해야 할 때 권장됩니다.
- `forReadOnlyDataBinding()` 또는 `forReadWriteDataBinding()` 팩토리 메서드를 통해 생성합니다.

```java
SimpleEvaluationContext context = SimpleEvaluationContext
    .forReadOnlyDataBinding()
    .withInstanceMethods() // 필요한 경우 메서드 호출 허용 설정
    .build();
```

## 주요 차이점 요약

| 특징 | StandardEvaluationContext | SimpleEvaluationContext |
|------|---------------------------|-------------------------|
| **기능** | 전체 SpEL 기능 지원 | 제한된 기능 (주로 데이터 바인딩) |
| **보안** | 취약할 수 있음 (임의 코드 실행 가능) | 안전함 (제한된 기능만 허용) |
| **사용 사례** | 프레임워크 내부, 신뢰할 수 있는 표현식 | 사용자 입력 필터링, 데이터 바인딩 |

## 예제 코드

`src/test/java/com/exam/spel/SpelTest.java`에서 두 컨텍스트의 동작 차이를 확인할 수 있습니다.
- `StandardEvaluationContext`에서는 `new String(...)`과 같은 객체 생성이 가능합니다.
- `SimpleEvaluationContext`에서는 객체 생성 시도시 예외가 발생하여 보안 위험을 방지합니다.

---

## 커스텀 어노테이션 + AOP + SpEL

SpEL의 가장 강력한 실무 활용 패턴입니다. 어노테이션에 SpEL 표현식을 선언하고, AOP Aspect가 이를 런타임에 평가합니다.

### 핵심 아이디어

```
@RequireRole("#user.role == 'ADMIN'")  ← 어노테이션에 SpEL 표현식 선언
public void deleteOrder(User user, Long orderId) { ... }
                  ↓
AOP Aspect가 메서드 파라미터(user, orderId)를 SpEL 컨텍스트 변수로 등록
                  ↓
런타임에 #user.role == 'ADMIN' 평가 → false면 예외 발생
```

### 메서드 파라미터를 SpEL 컨텍스트에 등록하는 방법

```java
MethodSignature signature = (MethodSignature) joinPoint.getSignature();
String[] paramNames = signature.getParameterNames(); // ["user", "orderId"]
Object[] args = joinPoint.getArgs();                 // [User("홍길동","USER"), 1L]

StandardEvaluationContext context = new StandardEvaluationContext();
for (int i = 0; i < paramNames.length; i++) {
    context.setVariable(paramNames[i], args[i]); // #user, #orderId 로 참조 가능
}
```

```
주의: getParameterNames()가 동작하려면 컴파일 시 파라미터 이름이 보존되어야 함
     Spring Boot 3.x는 기본적으로 -parameters 플래그가 적용되어 있어 동작함
```

### @RequireRole — 접근 제어

```java
// 어노테이션 선언
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    String value();                          // SpEL 표현식
    String message() default "접근 권한이 없습니다.";
}

// 사용
@RequireRole("#user.role == 'ADMIN'")
public void deleteOrder(User user, Long orderId) { ... }

// 복합 조건 (or 연산자)
@RequireRole("#requester.role == 'ADMIN' or #requester.name == #targetName")
public Order getOrder(User requester, String targetName, Long orderId) { ... }
```

```java
// Aspect
@Aspect
@Component
public class RequireRoleAspect {

    @Before("@annotation(requireRole)")  // 어노테이션 타입으로 바인딩
    public void checkRole(JoinPoint joinPoint, RequireRole requireRole) {
        StandardEvaluationContext context = buildContext(joinPoint, null);

        Boolean allowed = parser.parseExpression(requireRole.value())
                                .getValue(context, Boolean.class);

        if (!Boolean.TRUE.equals(allowed)) {
            throw new SecurityException(requireRole.message());
        }
    }
}
```

### @AuditLog — 동적 로그 메시지

```java
// 어노테이션 선언
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    String value(); // SpEL 표현식
}

// 사용: #result 로 메서드 반환값 참조
@AuditLog("'주문 생성 - 사용자: ' + #user.name + ', 금액: ' + #result.amount + '원'")
public Order createOrder(User user, int amount) { ... }
```

```java
// Aspect: @AfterReturning → returning 으로 반환값 받아서 #result 로 등록
@Aspect
@Component
public class AuditLogAspect {

    @AfterReturning(pointcut = "@annotation(auditLog)", returning = "returnValue")
    public void logAfterReturning(JoinPoint joinPoint, AuditLog auditLog, Object returnValue) {
        StandardEvaluationContext context = buildContext(joinPoint, returnValue); // #result = returnValue

        String message = parser.parseExpression(auditLog.value())
                               .getValue(context, String.class);

        log.info("[AUDIT] {}", message);
        // 출력: [AUDIT] 주문 생성 - 사용자: 홍길동, 금액: 50000원
    }
}
```

### EvaluationContext 선택 기준 — "누가 표현식을 작성하느냐"

**SimpleEvaluationContext를 권장한다 = 사용자 입력 표현식에 한정**

```
표현식 작성자
    ├── 개발자 (소스코드, 어노테이션에 하드코딩)
    │       → StandardEvaluationContext
    │       → 코드 리뷰/빌드를 거친 신뢰할 수 있는 표현식
    │       → T(클래스), new 등 고급 기능이 필요할 수도 있음
    │
    └── 외부 (DB, API, 관리자 UI, 사용자 입력)
            → SimpleEvaluationContext
            → 런타임에 동적으로 주입되는 신뢰할 수 없는 표현식
            → 객체 생성, 정적 메서드 호출 등을 반드시 차단해야 함
```

**[위험] Standard로 외부 표현식을 평가하면**
```java
// DB나 외부 API에서 이런 표현식이 들어온 경우
String expression = "T(java.lang.Runtime).getRuntime().exec('rm -rf /')";

StandardEvaluationContext context = new StandardEvaluationContext();
parser.parseExpression(expression).getValue(context); // 실행됨 → 위험!
```

**[안전] Simple로 외부 표현식을 평가하면**
```java
SimpleEvaluationContext context = SimpleEvaluationContext
        .forReadOnlyDataBinding()
        .withInstanceMethods()
        .build();

// 객체 생성 차단
parser.parseExpression("new String('공격')").getValue(context);       // SpelEvaluationException
// 정적 메서드 차단
parser.parseExpression("T(Runtime).getRuntime()").getValue(context);  // SpelEvaluationException
// 프로퍼티 접근은 허용
parser.parseExpression("#user.role").getValue(context, user);         // "ADMIN" ← 정상 동작
```

**왜 AOP Aspect에서는 Standard를 쓰는가**
```java
// @RequireRole 어노테이션의 표현식은 개발자가 소스코드에 직접 작성
@RequireRole("#user.role == 'ADMIN'")  // ← 개발자 작성, 코드 리뷰 대상
public void deleteOrder(User user, Long orderId) { ... }

// 이 표현식은 컴파일 타임에 이미 확정됨 → Standard 사용이 올바름
```

**실무 패턴: DB에서 동적 규칙을 불러올 때**
```java
// DB에 저장된 할인 규칙 (관리자가 UI에서 등록)
// "#user.role == 'VIP'"  ← 단순해 보이지만 외부 출처이므로 Simple 사용

public boolean evaluateRule(String ruleExpression, User user) {
    try {
        SimpleEvaluationContext context = SimpleEvaluationContext
                .forReadOnlyDataBinding()
                .withInstanceMethods()
                .build();
        context.setVariable("user", user);
        return Boolean.TRUE.equals(
            parser.parseExpression(ruleExpression).getValue(context, Boolean.class)
        );
    } catch (SpelEvaluationException e) {
        return false; // 잘못된 표현식은 false 처리
    }
}
```

### SpEL 표현식 문법 요약

| 표현식 | 설명 | 예시 |
|--------|------|------|
| `#변수명` | 컨텍스트 변수 참조 | `#user.role` |
| `#result` | 메서드 반환값 (@AfterReturning) | `#result.amount` |
| `+` | 문자열 연결 | `'안녕' + #user.name` |
| `==`, `!=` | 비교 | `#user.role == 'ADMIN'` |
| `and`, `or`, `!` | 논리 연산 | `#a == 'X' or #b == 'Y'` |
| `T(클래스)` | 정적 메서드/상수 접근 | `T(Math).abs(#value)` |

### 실습 코드 위치

```
src/main/java/com/exam/spel/
  ├── annotation/
  │   ├── RequireRole.java     ← 접근 제어 어노테이션
  │   └── AuditLog.java        ← 동적 로그 어노테이션
  ├── aspect/
  │   ├── SpelAspectSupport.java  ← 파라미터 → 컨텍스트 변환 공통 로직
  │   ├── RequireRoleAspect.java  ← @Before: 조건 불충족 시 예외
  │   └── AuditLogAspect.java     ← @AfterReturning: 반환값 포함 로그
  ├── service/
  │   ├── OrderService.java        ← 어노테이션 활용 예시 (Standard)
  │   └── DynamicRuleService.java  ← 외부 표현식 평가 (Simple)
  └── domain/
      ├── User.java
      └── Order.java

src/test/java/com/exam/spel/
  ├── SpelAopTest.java           ← 허용/차단/복합조건 테스트
  └── EvaluationContextTest.java ← Standard 위험 vs Simple 안전 비교 테스트
```
