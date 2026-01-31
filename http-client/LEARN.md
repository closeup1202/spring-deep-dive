# Spring HTTP Client 완벽 가이드

외부 API와 통신하기 위한 Spring의 3가지 기술(RestTemplate, WebClient, Http Interface)을 학습합니다.

## 1. RestTemplate (Legacy)
*   **특징**: 전통적인 동기식(Blocking) 클라이언트입니다.
*   **장점**: 사용하기 쉽고 익숙합니다.
*   **단점**: 요청 하나당 스레드 하나를 점유하므로, 대량의 요청을 처리할 때 비효율적입니다. Spring 5부터는 유지보수 모드(Maintenance Mode)로 전환되었습니다.
*   **설정**: `RestTemplateBuilder`를 사용하여 타임아웃 등을 설정합니다.

## 2. WebClient (Modern)
*   **특징**: Spring 5(WebFlux)에서 도입된 비동기/Non-blocking 클라이언트입니다.
*   **장점**: 적은 수의 스레드로 많은 요청을 동시에 처리할 수 있어 성능이 뛰어납니다. 동기/비동기 모두 지원합니다.
*   **단점**: 리액티브 프로그래밍(Mono, Flux)에 대한 이해가 필요하여 러닝 커브가 있습니다.
*   **사용법**:
    *   `block()`: 결과를 받을 때까지 기다림 (동기식처럼 사용)
    *   `subscribe()`: 결과를 기다리지 않고 콜백으로 처리 (비동기)

## 3. HTTP Interface (Latest - Spring 6+)
*   **특징**: **선언적(Declarative) HTTP 클라이언트**입니다.
*   **장점**: 구현체를 직접 만들 필요 없이, **인터페이스에 어노테이션만 붙이면** Spring이 알아서 구현체를 만들어줍니다. (Retrofit, Feign과 유사)
*   **기반**: 내부적으로는 `WebClient`를 사용하여 통신합니다.
*   **설정**:
    1.  `WebClient` 빈 생성
    2.  `WebClientAdapter`로 어댑터 생성
    3.  `HttpServiceProxyFactory`로 프록시 생성
    4.  `factory.createClient(MyInterface.class)`로 빈 등록

## 4. 실습 가이드
`ApiServiceTest`를 실행하여 3가지 방식 모두 정상적으로 외부 API(JSONPlaceholder)를 호출하고 응답을 받아오는지 확인하세요.

### 추천 전략
*   **신규 프로젝트**: 무조건 **HTTP Interface**를 추천합니다. 코드가 가장 깔끔하고 생산성이 높습니다.
*   **기존 프로젝트**: `RestTemplate`을 쓰고 있다면 굳이 바꿀 필요는 없지만, 성능 이슈가 있거나 비동기 처리가 필요하다면 `WebClient` 도입을 고려하세요.
