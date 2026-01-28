# Java Concurrency 심화

멀티스레드 환경에서 필수적인 `ThreadLocal`과 `CountDownLatch`의 동작 원리와 실무 활용법을 학습합니다.

## 1. ThreadLocal
스레드별로 독립적인 변수를 저장할 수 있는 공간입니다.
*   **원리**: `Thread` 클래스 내부에 `ThreadLocalMap`이라는 맵이 있고, 여기에 데이터를 저장합니다.
*   **활용**:
    *   Spring Security: `SecurityContextHolder` (인증 정보 저장)
    *   Spring MVC: `RequestContextHolder` (요청 정보 저장)
    *   Transaction: `TransactionSynchronizationManager` (커넥션 동기화)
*   **주의사항 (Memory Leak)**:
    *   스레드 풀을 사용하는 환경(Tomcat 등)에서는 스레드가 재사용됩니다.
    *   따라서 작업이 끝난 후 `ThreadLocal.remove()`를 호출하여 데이터를 지워주지 않으면, **다음 요청에서 이전 사용자의 데이터가 남아있는 심각한 보안 문제**가 발생할 수 있습니다.

## 2. ThreadLocal의 종류 (Variants)
1.  **`ThreadLocal` (기본)**
    *   현재 스레드에서만 접근 가능. 자식 스레드도 접근 불가.
2.  **`InheritableThreadLocal`**
    *   부모 스레드가 생성한 값을 자식 스레드(`new Thread()`)가 상속받음.
    *   **한계**: 스레드 풀처럼 스레드를 재사용하는 환경에서는, 부모의 값이 바뀌어도 자식(재사용된 스레드)은 **처음 생성될 때 복사받은 옛날 값**을 그대로 가지고 있음. (데이터 불일치 발생)
3.  **`TransmittableThreadLocal` (TTL)**
    *   Alibaba 오픈소스 라이브러리.
    *   스레드 풀 환경에서도 부모의 값을 자식에게 안전하게 전달(데코레이팅)해줌. 실무에서 비동기 추적(Tracing) 시 필수.

## 3. CountDownLatch
여러 스레드가 작업을 마칠 때까지 대기하는 동기화 도구입니다.
*   **동작**:
    1.  `new CountDownLatch(N)`으로 카운트 설정.
    2.  각 스레드가 작업 완료 후 `latch.countDown()` 호출.
    3.  대기하는 스레드(Main)는 `latch.await()`에서 멈춰 있다가, 카운트가 0이 되면 깨어남.
*   **활용**:
    *   병렬 데이터 처리 후 취합 (Scatter-Gather 패턴)
    *   서버 시작 시 여러 초기화 작업이 모두 끝날 때까지 대기

## 4. 실습 포인트
### ConcurrencyRunner
*   `ThreadLocal`의 격리성과 `CountDownLatch`를 이용한 동기화를 확인합니다.

### ThreadLocalVariantRunner
*   `InheritableThreadLocal`이 `new Thread()`에서는 잘 동작하지만, **스레드 풀(`Pool-2`)에서는 부모의 변경된 값을 반영하지 못하는 문제**를 확인합니다.
