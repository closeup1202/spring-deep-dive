# Kafka Core 학습 정리

> 기반 버전: **Kafka 3.7 (KRaft 모드)** — ZooKeeper 없음

---

## 목차

1. [Kafka 아키텍처 개요](#1-kafka-아키텍처-개요)
2. [KRaft 모드 — ZooKeeper 제거](#2-kraft-모드--zookeeper-제거)
3. [Producer 핵심 설정](#3-producer-핵심-설정)
4. [Consumer 핵심 설정](#4-consumer-핵심-설정)
5. [ISR (In-Sync Replicas) & 복제](#5-isr-in-sync-replicas--복제)
6. [Log & Segment 구조](#6-log--segment-구조)
7. [Spring Kafka 실전 설정](#7-spring-kafka-실전-설정)
8. [@RetryableTopic — 논블로킹 재시도](#8-retryabletopic--논블로킹-재시도)
9. [실무 체크리스트](#9-실무-체크리스트)
10. [kcat 명령어 모음](#10-kcat-명령어-모음)

---

## 1. Kafka 아키텍처 개요

### 핵심 구성요소

```
Producer ──→ [Broker Cluster] ──→ Consumer Group
                   │
             Topic / Partition
```

| 구성요소 | 설명 |
|----------|------|
| **Topic** | 메시지를 분류하는 논리적 채널 |
| **Partition** | 토픽을 구성하는 물리적 단위, 파티션 내 순서 보장 |
| **Offset** | 파티션 내 메시지의 위치 (단조증가, 불변) |
| **Producer** | 브로커에 메시지를 발행 |
| **Consumer** | 브로커에서 메시지를 소비 (풀 방식) |
| **Consumer Group** | 동일한 group.id를 가진 컨슈머 집합, 파티션을 분담 |
| **Broker** | 메시지를 저장하고 클라이언트 요청을 처리하는 서버 |
| **Controller** | 파티션 리더 선출, 브로커 장애 감지 (KRaft에서는 Kafka 내부 처리) |

### 파티션 소비 규칙

```
파티션 3, 컨슈머 그룹 내 컨슈머 수에 따른 할당:

컨슈머 1개: [P0, P1, P2] 모두 담당
컨슈머 2개: C1=[P0,P1], C2=[P2]
컨슈머 3개: C1=[P0], C2=[P1], C3=[P2]   ← 이상적
컨슈머 4개: C1=[P0], C2=[P1], C3=[P2], C4=[] ← C4 유휴 (파티션 초과)

→ 최대 병렬 소비자 수 = 파티션 수
→ 처리량 늘리려면 파티션 수 먼저 늘려야 함 (나중에 줄이기 어려움, 늘리는 건 가능)
```

---

## 2. KRaft 모드 — ZooKeeper 제거

### ZooKeeper 방식의 문제

```
[기존 구조]
ZooKeeper Cluster (3노드) ←── 메타데이터 관리
        ↓
Kafka Broker Cluster (N노드)

문제:
- ZooKeeper와 Kafka 두 클러스터를 별도 운영
- ZooKeeper 병목: 파티션 수 증가 시 메타데이터 처리 한계 (~20만 파티션)
- ZooKeeper 장애 = 컨트롤러 선출 불가 = Kafka 클러스터 마비
- 운영 복잡성: 모니터링 대상이 두 개
```

### KRaft 방식 (Kafka 3.3+ 안정화)

```
[KRaft 구조]
Kafka Broker Cluster
  ├─ Broker 노드: 메시지 저장/처리
  └─ Controller 노드: 메타데이터 관리 (Raft 합의)
     (단일 노드가 양쪽 역할 모두 수행 가능)

장점:
- ZooKeeper 제거 → 운영 단순화
- 메타데이터를 Kafka 자체 로그에 저장 → 일관성 향상
- 파티션 수 수백만 개까지 확장 가능
- 컨트롤러 장애 복구 시간: 수십초 → 수초
```

| 구분 | ZooKeeper 기반 | KRaft 모드 |
|------|---------------|------------|
| 메타데이터 관리 | ZooKeeper | Kafka 내부 (Raft 로그) |
| 컨트롤러 선출 | ZooKeeper | Kafka 브로커 중 하나 |
| 최대 파티션 수 | ~200,000 | 수백만 |
| 컨트롤러 장애복구 | 수십 초 | 수 초 |
| 운영 복잡성 | 높음 (2개 클러스터) | 낮음 (1개 클러스터) |

### 버전별 KRaft 상태

```
Kafka 2.8.0 (2021): KRaft 얼리 액세스 도입
Kafka 3.3.0 (2022): KRaft 프로덕션 안정화
Kafka 3.5.0 (2023): ZooKeeper 제거 공식 권장
Kafka 3.7.0 (2024): 현재 최신, KRaft 기본 권장
Kafka 4.0 (예정):   ZooKeeper 완전 제거
```

### docker-compose.yml (KRaft 단일 노드)

```yaml
# 핵심 KRaft 환경변수
KAFKA_NODE_ID: 1
KAFKA_PROCESS_ROLES: broker,controller          # 단일 노드가 두 역할
KAFKA_CONTROLLER_QUORUM_VOTERS: '1@kafka:9093'  # nodeId@host:port

KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT

CLUSTER_ID: 'MkU3OEVBNTcwNTJENDM2Qg'  # base64 UUID, 최초 포맷 시 사용
```

---

## 3. Producer 핵심 설정

### Acks — 내구성 vs 처리량 트레이드오프

| acks | 동작 | 데이터 안전성 | 처리량 |
|------|------|--------------|--------|
| `0` | 응답 안 기다림 | 가장 낮음 (유실 가능) | 가장 높음 |
| `1` | 리더만 확인 | 중간 (리더 장애 시 유실) | 중간 |
| `all` | ISR 전체 확인 | 가장 높음 (min.insync.replicas) | 낮음 |

```
acks=all 동작:
  Producer → Leader 브로커
                └─ Follower 브로커들이 복제 완료 → ISR 확인
                   → min.insync.replicas 이상 복제됨 → ACK 응답
```

### Idempotence (멱등성) — Kafka 3.0+ 기본값

```
문제: acks + retries 조합 시 중복 메시지 발생 가능
      Producer: 메시지 전송 → 네트워크 오류 → 재전송
      Broker: 첫 번째도 성공, 재전송도 저장 → 중복!

해결: enable.idempotence=true
  Producer에 PID(Producer ID) 부여 + 각 메시지에 시퀀스 번호 부여
  → 브로커가 중복 시퀀스 감지 → 드롭 (저장하지 않음)
  → 정확히 1번 저장 보장

필수 동반 설정:
  acks=all
  retries > 0 (Integer.MAX_VALUE 권장)
  max.in.flight.requests.per.connection ≤ 5

성능 영향: 최대 20% 처리량 감소, 실무에서는 기본 적용 권장
```

### Record Accumulator — 배치 전송

```
Producer.send() 호출
    ↓
Record Accumulator (메모리 버퍼)
  ├─ 토픽 A, 파티션 0 → [msg1, msg2, msg3] (batch.size 채울 때까지 대기)
  ├─ 토픽 A, 파티션 1 → [msg4]
  └─ 토픽 B, 파티션 0 → [msg5, msg6]
    ↓
Sender Thread
  → linger.ms 경과 또는 batch.size 초과 시 브로커로 전송
```

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `batch.size` | 16,384 (16KB) | 배치 최대 크기, 채우면 즉시 전송 |
| `linger.ms` | 0 | 배치 대기 시간, 0이면 즉시 전송 |
| `buffer.memory` | 33,554,432 (32MB) | Record Accumulator 전체 메모리 |
| `compression.type` | none | snappy/lz4/gzip/zstd |

```
linger.ms 설정 가이드:
- 0ms: 지연 최소화, 배치 효율 낮음 (소량 트래픽 환경)
- 5~20ms: 배치 효율 높음, 약간의 지연 허용 (일반적)
- 20ms 초과: 높은 처리량 필요 환경
```

### max.in.flight.requests.per.connection

```
브로커 응답 없이 동시에 전송할 수 있는 배치 수 (기본값: 5)

멱등성 활성화 시: 최대 5까지 순서 보장 (Kafka가 내부적으로 시퀀스 재정렬)
멱등성 비활성화 + retries > 0: 값 > 1이면 순서 역전 가능
  → 배치 1 전송 실패, 배치 2 성공, 배치 1 재전송 → 순서 역전
  → 순서 보장 필요하면 1로 설정 (처리량 감소)
```

### 파티션 선택 전략

```
Key 없음:
  Sticky Partitioning (기본, Kafka 2.4+)
  → 현재 배치가 채워질 때까지 같은 파티션 사용
  → 배치 효율 극대화 (라운드로빈보다 배치 크기 큼)

Key 있음:
  hash(key) % 파티션 수 → 같은 key = 같은 파티션 = 순서 보장
  → 주문 이벤트: orderId를 key로 → CREATED→PAID→SHIPPED 순서 보장
  → 사용자 이벤트: userId를 key로 → 사용자별 이벤트 순서 보장
```

### 전송 전략 선택

| 전략 | 설정 | 특성 |
|------|------|------|
| 최대 1번(at-most-once) | acks=0 | 유실 가능, 중복 없음, 최고 처리량 |
| 최소 1번(at-least-once) | acks=all, retries>0 | 중복 가능, 유실 없음 |
| 정확히 1번(exactly-once) | idempotence=true + acks=all | 중복 없음, 유실 없음, 성능 약간 감소 |

---

## 4. Consumer 핵심 설정

### Offset 커밋 전략

```
auto.commit (enable.auto.commit=true, 기본값):
  poll() 호출 시 auto.commit.interval.ms(5초)마다 자동 커밋
  문제: 처리 중 장애 → 이미 커밋된 오프셋은 재처리 불가 → 유실

Manual Commit (enable.auto.commit=false):
  처리 성공 후 ack.acknowledge() 명시적 호출
  → 처리 보장 (at-least-once)
  → 중복 처리 가능성 있음 (처리 완료 후 커밋 전 장애)
  → 멱등성 있는 처리 로직 필요
```

### auto.offset.reset

| 값 | 동작 | 사용 시점 |
|----|------|-----------|
| `earliest` | 파티션 첫 오프셋부터 | 새 컨슈머 그룹이 과거 메시지도 처리해야 할 때 |
| `latest` | 구독 시점 이후 메시지만 | 새 컨슈머 그룹이 이후 메시지만 처리 (기본값) |
| `none` | 저장된 오프셋 없으면 예외 | 오프셋 초기화를 명시적으로 제어할 때 |

### Consumer Rebalancing

리밸런싱 발생 조건:
- 컨슈머 그룹에 새 컨슈머 추가/제거
- 토픽에 파티션 추가
- `session.timeout.ms` 내 heartbeat 없음
- `max.poll.interval.ms` 내 poll() 호출 없음

**Eager 모드 (기본: RangeAssignor)**
```
리밸런싱 시작
  → 모든 컨슈머가 모든 파티션 반환 (Stop The World)
  → Group Coordinator가 새로 파티션 분배
  → 모든 컨슈머 재할당 완료 후 소비 재개

문제: 리밸런싱 동안 전체 컨슈머 그룹 처리 중단
      파티션 수가 많고 컨슈머 수가 많을수록 중단 시간 길어짐
```

**Cooperative 모드 (CooperativeStickyAssignor, Kafka 2.4+)**
```
리밸런싱 시작
  → Group Coordinator가 이동이 필요한 파티션만 파악
  → 해당 파티션만 반환 요청 (나머지는 계속 소비 중)
  → 이동 파티션만 재할당 → 해당 컨슈머들만 잠시 중단

장점: 대부분의 컨슈머는 리밸런싱 중에도 계속 소비
      처리 지연 최소화

설정: PARTITION_ASSIGNMENT_STRATEGY_CONFIG = CooperativeStickyAssignor.class.getName()
```

### Static Group Membership

```
문제: 유지보수로 컨슈머 재시작 → 리밸런싱 → 재시작 완료 후 또 리밸런싱

해결: group.instance.id 설정
  → 같은 instance.id로 재접속하면 리밸런싱 없이 기존 파티션 유지
  → session.timeout.ms 내에 재접속 시 그룹 멤버십 유지

적합 환경: 파티션 수가 많고, 재시작이 잦은 배치 컨슈머
```

### HeartBeat Thread

```
HeartBeat Thread: poll() 호출과 독립적인 별도 스레드
  → poll() 처리가 느려도 heartbeat는 계속 전송 가능
  → session.timeout.ms 내 heartbeat 미도착 → 컨슈머 장애로 판단 → 리밸런싱

설정 관계:
  heartbeat.interval.ms < session.timeout.ms / 3 (권장)
  session.timeout.ms < max.poll.interval.ms

실무 권장값:
  session.timeout.ms = 45,000 (45초)
  heartbeat.interval.ms = 15,000 (15초)
  max.poll.interval.ms = 300,000 (5분, 처리 무거우면 늘림)
```

### max.poll.records와 처리 시간

```
처리 시간이 예측 가능한 경우:
  max.poll.records × 건당 처리 시간 < max.poll.interval.ms

예시:
  max.poll.records = 500
  건당 처리 시간 = 200ms
  최대 소요 = 500 × 200ms = 100초
  max.poll.interval.ms = 300,000 (5분) → 여유 있음

외부 API 호출이 포함된 경우:
  max.poll.records를 줄이거나 max.poll.interval.ms를 늘린다
  또는 비동기 처리로 poll() 주기 유지
```

---

## 5. ISR (In-Sync Replicas) & 복제

### 구조

```
Leader 파티션 (Broker 1)
  ├─ 메시지 수신 및 저장
  └─ Follower로부터 Fetch 요청 처리

Follower 파티션 (Broker 2, 3)
  → Leader에게 Fetch 요청 (pull 방식)
  → 가져온 오프셋을 Leader에 보고

ISR(In-Sync Replicas): Leader + Follower 중 동기화 상태인 복제본 집합
  - Follower가 replica.lag.time.max.ms(기본 10초) 내에 Leader를 따라잡으면 ISR 포함
  - 뒤처지면 ISR에서 제거 → OSR(Out-of-Sync Replica)
```

### acks=all + min.insync.replicas

```
min.insync.replicas = 2 의미:
  ISR에 최소 2개 복제본이 있어야 쓰기 성공

replication.factor = 3, min.insync.replicas = 2:
  정상: ISR=[리더+팔로워2개] → 쓰기 성공
  1개 브로커 장애: ISR=[리더+팔로워1개] → min=2 충족 → 쓰기 성공
  2개 브로커 장애: ISR=[리더만] → min=2 불충족 → NotEnoughReplicasException

실무 권장:
  replication.factor = 3 (최소)
  min.insync.replicas = 2 (RF의 과반수)
  acks = all
```

### Unclean Leader Election

```
unclean.leader.election.enable = false (기본값, 권장):
  ISR 외 Follower가 리더가 되는 것을 허용하지 않음
  → 데이터 유실 없음, 가용성 일시 감소

unclean.leader.election.enable = true:
  ISR 없어도 아무 Follower를 리더로 선출
  → 가용성 향상, 데이터 유실 가능
  → 금융 서비스 등 데이터 정합성 중요 환경에서는 false 필수
```

---

## 6. Log & Segment 구조

### 파티션 디렉토리 구조

```
/var/lib/kafka/data/
└── order-events-0/           ← 토픽명-파티션번호
    ├── 00000000000000000000.log        ← 실제 메시지 데이터 (Segment)
    ├── 00000000000000000000.index      ← offset → 파일 내 byte 위치
    ├── 00000000000000000000.timeindex  ← timestamp → byte 위치
    ├── 00000000001073741824.log        ← 다음 Segment (이전 롤링 후)
    └── ...
```

### 각 파일의 역할

```
.log 파일 (Segment):
  실제 메시지 데이터가 순차적으로 append
  오프셋 번호가 파일명의 시작 오프셋

.index 파일:
  offset → .log 파일 내 byte 위치 매핑
  특정 오프셋의 메시지를 O(log n)으로 탐색 가능
  (sparse index: 모든 오프셋이 아닌 일정 간격으로 저장)

.timeindex 파일:
  timestamp → byte 위치 매핑
  time-based offset reset(--to-datetime) 또는 타임스탬프 기반 조회에 사용
```

### Segment Rolling

새 Segment 파일이 생성되는 조건 (먼저 도달하는 것):

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `log.segment.bytes` | 1GB | Segment 크기 초과 시 롤링 |
| `log.roll.ms` | 7일 | 지정 시간 경과 시 롤링 |
| `log.roll.hours` | 168 | log.roll.ms의 시간 단위 표현 |

### 보존 정책

```
삭제 정책 (log.cleanup.policy=delete, 기본값):
  retention.ms 또는 retention.bytes 초과 시 오래된 Segment 삭제
  → 파일 단위로 삭제 (Segment 내 개별 메시지 삭제 불가)

압축 정책 (log.cleanup.policy=compact):
  같은 key의 메시지 중 최신 값만 유지
  → 이벤트 소싱, CDC(Change Data Capture), 설정 저장 등에 사용
  → key=null 메시지는 압축 불가 → key 필수

혼합 정책 (delete,compact):
  일정 기간 유지 + 키 기반 압축 동시 적용
```

---

## 7. Spring Kafka 실전 설정

### KafkaProducerConfig 핵심 포인트

```java
// Idempotence 3종 세트 (같이 설정해야 의미 있음)
config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
config.put(ProducerConfig.ACKS_CONFIG, "all");
config.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

// 배치 효율
config.put(ProducerConfig.BATCH_SIZE_CONFIG, 16_384);   // 16KB
config.put(ProducerConfig.LINGER_MS_CONFIG, 5);         // 5ms 대기
config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy"); // 네트워크/디스크 절감

// 타임아웃
config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30_000);
config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000); // retries 포함 총 타임아웃
```

### KafkaConsumerConfig 핵심 포인트

```java
// 수동 커밋 (처리 보장)
config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

// Cooperative 리밸런싱 (stop-the-world 방지)
config.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
    CooperativeStickyAssignor.class.getName());

// 트랜잭셔널 프로듀서 메시지만 소비
config.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

// 컨테이너 설정
factory.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);
factory.setConcurrency(3); // 파티션 수 이하로
```

### KafkaTemplate 동기 vs 비동기

```java
// 비동기 (throughput 우선)
kafkaTemplate.send(topic, key, value)
    .whenComplete((result, ex) -> { /* callback */ });

// 동기 (전송 보장 필요)
SendResult<String, Object> result =
    kafkaTemplate.send(topic, key, value)
        .get(10, TimeUnit.SECONDS); // 타임아웃 필수!
```

> `get()` 타임아웃 없이 무한 대기하면 브로커 장애 시 스레드 영구 블로킹.
> 반드시 타임아웃을 지정하고, 타임아웃 시 Outbox 패턴으로 재발행 보장.

### TopicBuilder로 토픽 생성

```java
@Bean
public NewTopic orderEventsTopic() {
    return TopicBuilder.name("order-events")
        .partitions(3)
        .replicas(1)
        .config(TopicConfig.RETENTION_MS_CONFIG, "604800000") // 7일
        .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "1")
        .build();
}
```

> `spring.kafka.admin.auto-create=true` (기본값) 환경에서 애플리케이션 시작 시 자동 생성.
> 이미 존재하는 토픽은 설정이 변경되지 않음 (AdminClient로 별도 수정 필요).

---

## 8. @RetryableTopic — 논블로킹 재시도

### 블로킹 재시도 vs 논블로킹 재시도

```
블로킹 재시도 (DefaultErrorHandler):
  처리 실패 → 컨슈머 스레드 내에서 backoff 대기 → 재시도
  → 대기 중 해당 파티션의 다른 메시지 처리 불가
  → max.poll.interval.ms 초과 위험 (대기 시간 > 폴링 간격)
  → 수초 이내 짧은 재시도에 적합

논블로킹 재시도 (@RetryableTopic):
  처리 실패 → 메시지를 재시도 토픽으로 발행 → 원본 파티션 계속 소비
  → 재시도 토픽의 별도 컨슈머가 지연 후 재처리
  → 원본 소비 지연 없음
  → 수초~수분의 긴 재시도 간격에 적합
```

### @RetryableTopic이 만드는 토픽 구조

```
원본 토픽: order-events-retryable
    │ 처리 실패
    ▼
재시도 토픽 1: order-events-retryable-retry-0  (2초 후 소비)
    │ 또 실패
    ▼
재시도 토픽 2: order-events-retryable-retry-1  (4초 후 소비)
    │ 또 실패
    ▼
재시도 토픽 3: order-events-retryable-retry-2  (8초 후 소비)
    │ 또 실패
    ▼
DLT: order-events-retryable.DLT               (최종 실패, 수동 처리)

→ 총 4회 시도 (최초 1 + 재시도 3)
→ 원본 토픽 소비는 중단 없이 계속됨
```

### 지수 백오프 계산

```java
@Backoff(value = 2_000L, multiplier = 2.0, maxDelay = 10_000L)

1차 재시도: 2,000ms (2초)
2차 재시도: 2,000 × 2.0 = 4,000ms (4초)
3차 재시도: 4,000 × 2.0 = 8,000ms (8초)
4차 재시도: 8,000 × 2.0 = 16,000ms → maxDelay=10,000ms 적용 → 10초

장점: thundering herd 방지
  고정 대기(2초,2초,2초): 모든 실패 메시지가 동시에 재시도 → 브로커 과부하
  지수 백오프: 재시도 시점이 분산 → 브로커 부하 분산
```

### @DltHandler — 최종 실패 처리

```java
@DltHandler
public void handleDlt(ConsumerRecord<String, OrderEvent> record) {
    // DLT 메시지 헤더에 포함된 원본 정보:
    //   KafkaHeaders.DLT_ORIGINAL_TOPIC
    //   KafkaHeaders.DLT_ORIGINAL_PARTITION
    //   KafkaHeaders.DLT_ORIGINAL_OFFSET
    //   KafkaHeaders.DLT_EXCEPTION_MESSAGE
    //   KafkaHeaders.DLT_EXCEPTION_STACKTRACE

    // 실무 대응:
    // 1. DB에 실패 이벤트 저장 (수동 처리 대기열)
    // 2. Slack/PagerDuty 알림
    // 3. 관리자 대시보드 노출
    // 4. 재처리 필요 시 원본 토픽으로 republish
}
```

### 재시도하지 않을 예외 지정

```java
@RetryableTopic(
    attempts = "4",
    backoff = @Backoff(value = 2_000L, multiplier = 2.0),
    exclude = {
        IllegalArgumentException.class,  // 입력 오류: 재시도 의미 없음
        IllegalStateException.class      // 비즈니스 오류: 재시도 의미 없음
    }
)
```

> 비즈니스 오류(잔액 부족, 유효하지 않은 주문)는 재시도해도 항상 실패.
> 재시도 대상은 일시적 오류(네트워크, DB 연결, 외부 API 타임아웃)만.

---

## 9. 실무 체크리스트

### Producer

| 체크 항목 | 이유 |
|-----------|------|
| `enable.idempotence=true` 설정 | 네트워크 오류 재전송 시 중복 메시지 방지 |
| `acks=all` 설정 | 리더 장애 시 데이터 유실 방지 |
| `compression.type=snappy` 설정 | 네트워크/디스크 비용 절감 |
| 동기 전송에 타임아웃 지정 | 브로커 장애 시 스레드 무한 블로킹 방지 |
| 비동기 전송에 실패 콜백 등록 | 전송 실패 메시지 Outbox에 기록하여 재발행 보장 |
| `orderId`를 key로 사용 | 같은 주문 이벤트의 순서 보장 |

### Consumer

| 체크 항목 | 이유 |
|-----------|------|
| `enable.auto.commit=false` | 처리 실패 시 오프셋 커밋 방지 → 재처리 보장 |
| `CooperativeStickyAssignor` 설정 | 리밸런싱 중 처리 중단 최소화 |
| `max.poll.interval.ms` > 최대 처리 시간 | 무거운 처리 중 리밸런싱 방지 |
| 처리 로직 멱등성 확보 | at-least-once 보장 환경에서 중복 처리 방지 |
| `isolation.level=read_committed` | 트랜잭셔널 프로듀서 롤백 메시지 소비 방지 |
| DLT 모니터링 설정 | 최종 실패 메시지 즉시 감지 |

### 브로커/토픽 설계

| 체크 항목 | 이유 |
|-----------|------|
| `replication.factor ≥ 3` | 브로커 1개 장애 허용 |
| `min.insync.replicas = RF/2 + 1` | acks=all의 최소 복제 보장 |
| 파티션 수 = 예상 최대 소비자 수 | 소비자 추가 시 즉시 병렬화 가능 |
| DLT 보존 기간 ≥ 30일 | 수동 재처리 여유 시간 확보 |
| `unclean.leader.election.enable=false` | 데이터 유실 방지 |
| KRaft 모드 사용 (Kafka 3.3+) | ZooKeeper 운영 복잡성 제거 |

### Lag 모니터링

```bash
# 컨슈머 그룹의 현재 Lag 확인
kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group order-service-group \
  --describe

# Lag 알림 기준 (실무):
# 일반 이벤트: Lag > 10,000
# 결제 이벤트: Lag > 100 (더 민감하게)
```

---

## 10. kcat 명령어 모음

```bash
# ─── 브로커/토픽 정보 ──────────────────────────────────────────────

# 전체 브로커 및 토픽 목록
kcat -b localhost:9092 -L

# 토픽 이름만 추출
kcat -b localhost:9092 -L | grep 'topic "'

# 특정 토픽 상세 (파티션, 리더, ISR 정보)
kcat -b localhost:9092 -L -t order-events

# ─── 메시지 소비 ──────────────────────────────────────────────────

# 처음부터 모든 메시지 읽기
kcat -b localhost:9092 -t order-events -C -o beginning

# 최신 메시지만 읽기 (실시간 모니터링)
kcat -b localhost:9092 -t order-events -C

# 처음부터 읽고 마지막 메시지 후 종료 (-e)
kcat -b localhost:9092 -t order-events -C -o beginning -e

# key와 value 함께 출력
kcat -b localhost:9092 -t order-events -C -f '%k => %s\n'

# JSON 형식으로 pretty print
kcat -b localhost:9092 \
  -t order-events \
  -C -o beginning \
  -e -q -f '%s\n' \
| jq .

# 특정 파티션, 특정 오프셋부터 읽기
kcat -b localhost:9092 -t order-events -C -p 0 -o 100

# ─── 메시지 발행 ──────────────────────────────────────────────────

# 단순 메시지 발행
echo "test-message" | kcat -b localhost:9092 -t order-events -P

# key 포함 발행 (-K: 는 key/value 구분자가 ":" 라는 의미)
echo "order-001:{"orderId":"order-001","status":"CREATED"}" \
  | kcat -b localhost:9092 -t order-events -P -K:

# 특정 파티션으로 발행
echo "test" | kcat -b localhost:9092 -t order-events -P -p 0

# ─── DLT 확인 ─────────────────────────────────────────────────────

# DLT 메시지 확인 (key와 원본 예외 정보 포함)
kcat -b localhost:9092 \
  -t order-events.DLT \
  -C -o beginning \
  -e -q \
  -f 'key=%k, value=%s\n'

# ─── 컨슈머 그룹 Lag 확인 (kafka-consumer-groups 사용) ───────────

# kafka CLI (docker exec)
docker exec kafka-kraft \
  kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group order-service-group \
  --describe
```

---

## 참고: 실습 코드 구조

```
kafka-core/
├── docker-compose.yml                ← KRaft 단일 브로커 + Kafka UI
├── LEARN.md
└── src/
    ├── main/java/com/exam/kafka/
    │   ├── config/
    │   │   ├── KafkaProducerConfig.java    ← Idempotent, 배치, 압축 설정
    │   │   ├── KafkaConsumerConfig.java    ← Manual ack, Cooperative, ErrorHandler
    │   │   └── KafkaTopicConfig.java       ← TopicBuilder, 보존 정책
    │   ├── domain/
    │   │   └── OrderEvent.java             ← 이벤트 도메인 (record)
    │   ├── producer/
    │   │   └── OrderEventProducer.java     ← 비동기/동기 전송
    │   └── consumer/
    │       ├── OrderEventConsumer.java     ← Manual commit
    │       └── RetryableOrderConsumer.java ← @RetryableTopic + @DltHandler
    └── test/java/com/exam/kafka/
        └── KafkaCoreTest.java              ← @EmbeddedKafka 테스트
```

| 테스트 | 검증 내용 |
|--------|-----------|
| `asyncSend_thenConsumed` | 비동기 전송 → 컨슈머 수신 확인 |
| `syncSend_returnsRecordMetadata` | 동기 전송 → 파티션/오프셋 메타데이터 |
| `sameKey_goesToSamePartition` | 동일 key → 동일 파티션 확인 |
| `directDltSend_messageArrives` | DLT 토픽 직접 전송 |
