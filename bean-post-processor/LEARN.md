# BeanPostProcessor 심층 탐구

## 1. 개요
Spring Framework의 마법 같은 기능들(`@Transactional`, `@Cacheable`, `@Async` 등)은 어떻게 동작할까요?
개발자가 작성한 코드에 어노테이션만 붙였을 뿐인데, 트랜잭션이 시작되고 캐시가 적용됩니다.
이 모든 마법의 중심에는 **BeanPostProcessor(빈 후처리기)**가 있습니다.

이 모듈에서는 직접 `BeanPostProcessor`를 구현하여 나만의 `@Trace` 어노테이션을 만들어봅니다.
이를 통해 Spring AOP와 프록시(Proxy)가 어떻게 생성되고 적용되는지 깊이 이해할 수 있습니다.

## 2. 핵심 개념

### BeanPostProcessor란?
Spring 컨테이너가 빈(Bean)을 생성하고 초기화하는 과정에 개입할 수 있는 훅(Hook) 인터페이스입니다.
두 가지 주요 메서드를 제공합니다:
1. `postProcessBeforeInitialization`: 빈 초기화(`@PostConstruct`) **전**에 호출
2. `postProcessAfterInitialization`: 빈 초기화 **후**에 호출 (주로 여기서 프록시 적용)

### 프록시(Proxy) 패턴
원본 객체를 감싸서 대리자(Proxy) 역할을 하는 객체입니다.
클라이언트가 프록시를 호출하면, 프록시는 부가 기능(시간 측정, 트랜잭션 등)을 수행한 뒤 원본 객체를 호출합니다.

## 3. 실습 내용

### 목표
메서드 실행 시간을 측정하여 로그로 남겨주는 `@Trace` 어노테이션을 만듭니다.

### 구현 단계
1. **`@Trace` 어노테이션 정의**: 메서드에 붙일 마커 어노테이션 생성
2. **`TraceService` 구현**: `@Trace`를 사용하는 비즈니스 로직 작성
3. **`TraceBeanPostProcessor` 구현**:
    - 빈 생성 후(`postProcessAfterInitialization`) 가로채기
    - `@Trace`가 붙은 빈을 찾아서 **프록시 객체로 교체**
    - 프록시 내부에서 실행 시간 측정 로직 주입 (`MethodInterceptor`)
4. **테스트**: 실제 빈이 프록시로 바뀌었는지, 로그가 잘 찍히는지 확인

## 4. 실행 방법

### 테스트 실행
`src/test/java/com/exam/bpp/BeanPostProcessorTest.java`를 실행하세요.

### 예상 결과
1. `traceService`의 클래스 이름에 `$$SpringCGLIB`가 포함되어야 합니다. (프록시 적용 확인)
2. `slowMethod` 실행 시 `--> [Trace] slowMethod 실행 시간: 10xxms` 로그가 출력됩니다.
3. `fastMethod` 실행 시에는 시간 측정 로그가 출력되지 않습니다.

## 5. 심화 학습 (Self-Invocation 문제)
만약 `TraceService` 내부에서 `this.slowMethod()`를 호출하면 어떻게 될까요?
프록시를 거치지 않고 원본 객체의 메서드를 직접 호출하기 때문에 `@Trace` 기능이 동작하지 않습니다.
이것이 바로 Spring AOP의 유명한 **"내부 호출(Self-Invocation) 문제"**입니다.
