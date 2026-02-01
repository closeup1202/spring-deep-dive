# Spring Events와 Transaction 심층 탐구

## 1. 개요
Spring Event를 사용하면 서비스 간의 강한 결합을 끊을 수 있습니다.
하지만 트랜잭션과 함께 사용할 때는 **"이벤트 리스너의 실행 시점"**과 **"예외 발생 시 롤백 여부"**를 정확히 제어해야 합니다.

## 2. 핵심 개념

### `@EventListener` (기본)
- **실행 시점:** 이벤트를 발행(`publishEvent`)한 그 즉시 실행됩니다. (동기)
- **트랜잭션:** 발행한 쪽의 트랜잭션에 참여합니다.
- **위험성:** 리스너에서 예외가 발생하면, **발행한 쪽의 트랜잭션까지 롤백**됩니다.
    - 예: 회원가입 완료 -> 가입 축하 메일 발송(리스너) 실패 -> **회원가입 롤백** (???)

### `@TransactionalEventListener`
- **실행 시점:** 트랜잭션의 특정 단계에 실행을 예약합니다.
- **옵션 (`phase`):**
    - `AFTER_COMMIT` (기본값): 트랜잭션이 **성공적으로 커밋된 후**에 실행됩니다.
    - `AFTER_ROLLBACK`: 트랜잭션이 롤백된 후에 실행됩니다.
    - `BEFORE_COMMIT`: 커밋 직전에 실행됩니다.
- **안전성:** `AFTER_COMMIT` 단계에서 예외가 발생해도, 원본 트랜잭션은 이미 커밋되었으므로 **롤백되지 않습니다.**

## 3. 실습 내용

### 시나리오 1: 일반 리스너의 위험성
1. `MemberService`가 회원을 저장하고 이벤트를 발행합니다.
2. `@EventListener`가 이벤트를 받아서 예외를 던집니다.
3. 결과: `MemberService`의 트랜잭션이 롤백되어 회원 정보가 사라집니다.

### 시나리오 2: 트랜잭션 리스너의 안전성
1. `MemberService`가 회원을 저장하고 이벤트를 발행합니다.
2. 트랜잭션이 커밋됩니다. (DB 저장 확정)
3. `@TransactionalEventListener(phase = AFTER_COMMIT)`가 실행되어 예외를 던집니다.
4. 결과: 예외는 발생하지만, 회원 정보는 DB에 안전하게 남아있습니다.

## 4. 실행 방법
`src/test/java/com/exam/springevents/service/EventTransactionTest.java`를 실행하여 두 가지 케이스를 검증하세요.

## 5. 심화: 리스너에서 DB 작업을 해야 한다면?
`AFTER_COMMIT` 단계에서는 이미 트랜잭션이 끝났거나, 커밋된 상태라 쓰기 작업이 불가능할 수 있습니다.
이때는 리스너 메서드에 `@Transactional(propagation = Propagation.REQUIRES_NEW)`를 붙여서 **새로운 트랜잭션**을 시작해야 합니다.
