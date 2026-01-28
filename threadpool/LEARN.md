# Java Thread Pool 심화

JDK `ExecutorService`의 종류와 `ThreadPoolExecutor`의 내부 동작 원리를 깊이 있게 학습합니다.

## 1. Executors 팩토리 메서드의 함정
실무에서는 `Executors.newCachedThreadPool()`이나 `newFixedThreadPool()`을 직접 쓰는 것을 권장하지 않습니다. (대신 `ThreadPoolExecutor`를 직접 생성하거나 Spring의 `ThreadPoolTaskExecutor` 사용)

### A. CachedThreadPool
*   **특징**: 필요할 때마다 스레드를 생성합니다. (제한 없음)
*   **위험성**: 요청이 폭주하면 스레드가 수천 개 생성되어 **CPU/메모리 고갈로 서버가 다운**됩니다.
*   **실습 결과**: 작업 100개를 넣으면 스레드가 100개 생성되는 것을 확인할 수 있습니다.

### B. FixedThreadPool
*   **특징**: 스레드 개수를 고정합니다.
*   **위험성**: 스레드 개수는 안전하지만, **대기열(Queue)이 무제한(`LinkedBlockingQueue`)**입니다. 요청이 계속 쌓이면 **OOM(Out Of Memory)**이 발생할 수 있습니다.
*   **실습 결과**: 작업 100개를 넣어도 스레드는 5개만 유지되고, 나머지는 큐에 쌓입니다.

## 2. ThreadPoolExecutor 동작 순서 (중요)
스레드 풀은 다음 순서로 작업을 처리합니다.

1.  **Core Pool Size**: 스레드 수가 Core보다 적으면 **새 스레드**를 만듭니다.
2.  **Queue Capacity**: Core가 꽉 차면 **큐에 넣습니다**. (Max까지 바로 늘어나지 않음!)
3.  **Max Pool Size**: 큐도 꽉 차면 그제서야 **Max까지 스레드를 추가**로 만듭니다.
4.  **Reject**: Max도 꽉 차고 큐도 꽉 차면 **요청을 거절(Reject)**합니다.

### 실습 포인트 (`CustomPoolService`)
*   Core(2), Queue(10), Max(5) 설정에서 작업 16개를 넣었을 때:
    *   1~2번: Core 스레드 생성
    *   3~12번: 큐에 적재 (스레드 안 늘어남)
    *   13~15번: Max 스레드 생성 (총 5개)
    *   16번: **RejectedExecutionException 발생**
