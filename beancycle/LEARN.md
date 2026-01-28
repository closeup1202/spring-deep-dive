# Spring Bean Lifecycle (빈 생명주기)

스프링 컨테이너가 빈을 관리하는 전체 생명주기와 스코프별 동작 차이를 학습합니다.

## 1. 빈 생명주기 전체 흐름
스프링 빈은 객체 생성 -> 의존성 주입 -> 초기화 -> 사용 -> 소멸의 과정을 거칩니다.

1.  **Bean Definition Load**: 빈 설정 정보 로드 (`BeanFactoryPostProcessor`)
2.  **Instantiation**: 빈 객체 생성 (Constructor 호출)
3.  **Populate Properties**: 의존성 주입 (DI)
4.  **Aware Interfaces**: 스프링 컨테이너 자원 주입
    *   `BeanNameAware`: 빈 이름 주입
    *   `BeanFactoryAware`: 빈 팩토리 주입
    *   `ApplicationContextAware`: 애플리케이션 컨텍스트 주입
5.  **BeanPostProcessor (Before)**: 초기화 전 처리 (`postProcessBeforeInitialization`)
6.  **Initialization**: 초기화 단계
    *   `@PostConstruct`
    *   `InitializingBean.afterPropertiesSet()`
7.  **BeanPostProcessor (After)**: 초기화 후 처리 (`postProcessAfterInitialization`)
    *   **중요**: AOP 프록시 객체가 주로 이 단계에서 생성되어 원본 빈을 대체합니다.
8.  **Bean Ready**: 빈 사용 가능
9.  **Destruction**: 소멸 단계 (컨테이너 종료 시)
    *   `@PreDestroy`
    *   `DisposableBean.destroy()`

## 2. Bean Scope (빈 스코프)
*   **Singleton (기본값)**:
    *   컨테이너 시작 시 생성되고 종료 시 소멸됩니다.
    *   스프링이 전체 라이프사이클을 관리합니다.
*   **Prototype**:
    *   요청할 때마다 새로운 객체가 생성됩니다.
    *   **중요**: 스프링은 **생성과 초기화까지만 관여**합니다. 소멸 메서드(`@PreDestroy`)는 호출되지 않으므로 클라이언트가 직접 자원을 해제해야 합니다.

## 3. 실습 파일 설명
*   `LifecycleBean.java`: 모든 라이프사이클 인터페이스를 구현하여 호출 순서 확인
*   `MyBeanPostProcessor.java`: 빈 초기화 전후에 로그를 출력하여 개입 시점 확인
*   `PrototypeBean.java`: 프로토타입 스코프 빈의 소멸 메서드가 호출되지 않음을 확인
