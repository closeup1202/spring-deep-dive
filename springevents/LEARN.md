# Spring Events 심화 학습

스프링 이벤트와 트랜잭션의 연동(`@TransactionalEventListener`)을 실무 관점에서 깊이 있게 학습합니다.

## 1. 핵심 개념: Transaction Phase
이벤트 리스너가 트랜잭션의 어느 시점에 실행될지를 결정하는 옵션입니다.

| Phase | 설명 | 실무 활용 예시 |
| :--- | :--- | :--- |
| **AFTER_COMMIT** (기본값) | 트랜잭션이 성공적으로 **커밋된 후** 실행 | 이메일/알림톡 발송, 타 시스템 API 호출 (가장 많이 사용) |
| **AFTER_ROLLBACK** | 트랜잭션이 **롤백된 후** 실행 | 실패 로그 기록, 관리자 알림, 임시 리소스 정리 |
| **BEFORE_COMMIT** | 트랜잭션 **커밋 직전** 실행 | 데이터 최종 검증 (거의 사용 안 함) |
| **AFTER_COMPLETION** | 커밋/롤백 상관없이 **종료 후** 실행 | 리소스 반납 (Finally 블록과 유사) |

## 2. 실무 패턴 (Best Practices)

### 패턴 1: 비즈니스 로직과 후처리 분리 (AFTER_COMMIT)
*   **상황**: 회원가입(Core) 후 가입 환영 이메일(Sub)을 보내야 함.
*   **문제**: 이메일 발송이 실패했다고 해서 회원가입까지 롤백되면 안 됨. 반대로, 회원가입이 DB 에러로 실패했는데 이메일이 발송되면 안 됨.
*   **해결**: `@TransactionalEventListener(phase = AFTER_COMMIT)` 사용.
    *   회원가입 트랜잭션이 커밋된 후에만 리스너가 실행됨.
    *   리스너에서 예외가 발생해도 이미 커밋된 회원가입은 안전함.

### 패턴 2: 비동기 처리 (Async + AFTER_COMMIT)
*   **상황**: 이메일 발송 서버가 느려서 응답이 3초 걸림.
*   **해결**: `@Async`와 함께 사용.
    *   메인 트랜잭션 스레드는 커밋 후 즉시 응답을 반환하고, 별도의 스레드에서 이메일을 발송함.
    *   사용자 경험(UX) 향상.

### 패턴 3: 실패 감지 (AFTER_ROLLBACK)
*   **상황**: 결제 트랜잭션이 실패했을 때 운영팀 슬랙으로 알림을 보내고 싶음.
*   **해결**: `phase = AFTER_ROLLBACK` 사용.

## 3. 실습 시나리오 (`EventRunner` 실행 결과 확인)

### Scenario 1: Transaction Success (Commit)
1.  `[Service]` 로직 수행 및 이벤트 발행
2.  `[Listener: Normal]` 즉시 실행 (동기)
3.  `[Listener: BEFORE_COMMIT]` 커밋 직전 실행
4.  **DB Commit 발생**
5.  `[Listener: AFTER_COMMIT]` 실행 (이메일 발송)
6.  `[Listener: Async + AFTER_COMMIT]` 별도 스레드에서 실행

### Scenario 2: Transaction Failure (Rollback)
1.  `[Service]` 로직 수행 및 이벤트 발행
2.  `[Listener: Normal]` 즉시 실행 (주의: 여기서 예외 터지면 롤백 원인이 될 수 있음)
3.  **Exception 발생 -> Rollback**
4.  `[Listener: AFTER_ROLLBACK]` 실행 (실패 알림)
5.  *`AFTER_COMMIT` 리스너들은 실행되지 않음!*
