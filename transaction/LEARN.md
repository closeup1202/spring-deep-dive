# Spring Transaction Management

스프링의 선언적 트랜잭션(`@Transactional`) 동작 원리와 롤백 규칙을 학습합니다.

## 1. 트랜잭션 롤백 규칙 (Rollback Rules)
스프링은 기본적으로 **Unchecked Exception**에 대해서만 롤백을 수행합니다.

*   **Unchecked Exception (RuntimeException, Error)**:
    *   예: `NullPointerException`, `IllegalArgumentException`
    *   **동작**: 자동 **Rollback**
*   **Checked Exception (Exception)**:
    *   예: `IOException`, `SQLException` (JDBC 레벨이 아닌 비즈니스 로직상)
    *   **동작**: 자동 **Commit** (롤백되지 않음)

### 왜 Checked Exception은 롤백하지 않을까?
스프링은 Checked Exception을 "비즈니스적으로 의미 있는 예외(복구 가능한 예외)"로 간주합니다. 예를 들어, '잔액 부족' 예외가 발생했을 때 트랜잭션을 롤백하기보다는, 실패 이력을 남기고 사용자에게 알림을 보내는 등의 후처리를 커밋해야 할 수도 있기 때문입니다.

### 강제 롤백 설정
Checked Exception 발생 시에도 롤백하고 싶다면 `rollbackFor` 옵션을 사용합니다.
```java
@Transactional(rollbackFor = Exception.class)
```

## 2. 트랜잭션 전파 (Propagation)
트랜잭션이 이미 진행 중일 때, 새로운 트랜잭션을 어떻게 처리할지 결정합니다.

*   **REQUIRED (기본값)**:
    *   이미 트랜잭션이 있으면 참여하고, 없으면 새로 만듭니다.
    *   하나라도 실패하면 전체가 롤백됩니다.
*   **REQUIRES_NEW**:
    *   항상 새로운 트랜잭션을 만듭니다.
    *   기존 트랜잭션은 잠시 중단(Suspend)됩니다.
    *   내부 트랜잭션이 롤백되어도 외부 트랜잭션에는 영향을 주지 않습니다 (단, 예외 처리를 잘 해야 함).

## 3. 실습 결과 확인 포인트
`TxRunner` 실행 결과를 통해 다음을 확인합니다.

1.  **Member1 (Unchecked)**: DB에 저장되지 않음 (롤백됨).
2.  **Member2 (Checked)**: DB에 저장됨 (커밋됨).
3.  **Member3 (Checked + rollbackFor)**: DB에 저장되지 않음 (롤백됨).
