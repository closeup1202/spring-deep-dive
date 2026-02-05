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
