# Circular Dependencies (순환 참조)

A가 B를 참조하고, B가 다시 A를 참조하는 상황에서 스프링의 동작 방식을 학습합니다.

## 1. 순환 참조란?
두 개 이상의 빈이 서로를 의존성으로 가지고 있어, 어느 하나를 먼저 생성할 수 없는 딜레마 상황입니다.
*   A -> B -> A

## 2. Spring Boot 2.6+ 정책 변경 (중요)
스프링 부트 2.6 버전부터는 **순환 참조가 기본적으로 금지(Prohibited)**됩니다.
*   이전에는 필드 주입 시 경고만 뜨고 허용되었으나, 이제는 앱 실행 시 에러가 발생합니다.
*   **해결책**: `application.properties`에 다음 설정을 추가해야 합니다.
    ```properties
    spring.main.allow-circular-references=true
    ```
    이 설정을 켜면 스프링의 3단계 캐시 메커니즘이 작동하여 필드 주입 순환 참조를 해결해줍니다.

## 3. 주입 방식에 따른 차이

### A. 필드/Setter 주입 (Field/Setter Injection)
*   **특징**: `allow-circular-references=true` 설정 시 **해결 가능**.
*   **동작 원리**:
    1.  A 객체를 먼저 생성합니다 (아직 B는 주입되지 않음).
    2.  A를 스프링의 **3단계 캐시(Singleton Factories)**에 미리 노출합니다.
    3.  B를 생성하려고 시도합니다. B는 A가 필요합니다.
    4.  B는 캐시에 있는 (아직 덜 완성된) A를 가져와 주입받습니다.
    5.  B 생성이 완료됩니다.
    6.  A에 B를 주입하고 A 생성을 완료합니다.

### B. 생성자 주입 (Constructor Injection)
*   **특징**: `allow-circular-references=true` 설정을 켜도 **해결 불가**.
*   **이유**: 객체를 생성하는 시점(Constructor 호출)에 서로가 필요하기 때문에, 객체 생성 자체를 시작할 수 없습니다.
*   **결과**: `BeanCurrentlyInCreationException` 발생하며 앱 실행 실패.

## 4. 해결 방법 (@Lazy)
생성자 주입을 꼭 써야 하는데 순환 참조가 발생한다면 `@Lazy`를 사용합니다.
*   **사용법**: `public ComponentA(@Lazy ComponentB b) { ... }`
*   **원리**:
    *   스프링은 B 대신 **프록시(가짜 객체)**를 즉시 주입하여 A 생성을 성공시킵니다.
    *   나중에 A가 실제로 B의 메서드를 호출할 때, 그때서야 진짜 B를 찾거나 생성합니다.

## 5. 실습 파일 설명
*   `ServiceA`, `ServiceB`: 필드 주입을 사용하여 순환 참조가 발생하는 케이스 (설정 필요)
*   `ComponentA`, `ComponentB`: 생성자 주입을 사용하여 순환 참조 에러가 발생하는 케이스 (`@Lazy`로 해결)
