# DB Query & Index 학습 정리

---

## 목차

1. [기본 쿼리 문법](#1-기본-쿼리-문법)
   - JOIN / INNER JOIN / LEFT JOIN / RIGHT JOIN
   - GROUP BY
   - ORDER BY
   - LIMIT / OFFSET
   - HAVING
   - 서브쿼리 vs JOIN
2. [인덱스](#2-인덱스)
   - 복합 인덱스 핵심 규칙
   - 풀스캔이 인덱스보다 빠른 경우
   - LIKE + 인덱스 함정
   - OR 조건과 인덱스
   - NULL 컬럼과 인덱스
   - GROUP BY + ORDER BY COUNT DESC 에서 인덱스가 약한 이유
   - 인덱스 과다 설계 판단
3. [MySQL 실무 최적화 설정](#3-mysql-실무-최적화-설정)
4. [PostgreSQL 실무 최적화 설정](#4-postgresql-실무-최적화-설정)
5. [EXPLAIN 읽는 법](#5-explain-읽는-법)

---

## 1. 기본 쿼리 문법

### JOIN / INNER JOIN

두 테이블에서 **양쪽 모두 일치하는 행**만 반환한다.
`JOIN` 단독 키워드는 `INNER JOIN`과 동일하다.

```sql
SELECT o.id, u.name, o.total_price
FROM orders o
INNER JOIN users u ON o.user_id = u.id
WHERE o.status = 'PAID';
```

```
orders       users
┌──┬───────┐ ┌──┬──────┐
│id│user_id│ │id│ name │
├──┼───────┤ ├──┼──────┤
│ 1│   1   │ │ 1│Alice │
│ 2│   2   │ │ 2│ Bob  │
│ 3│   9   │ │ 3│Carol │  ← orders에 없음
└──┴───────┘ └──┴──────┘

결과: orders.user_id가 users.id와 일치하는 1, 2행만 반환
      order_id=3 (user_id=9)는 users에 없으므로 제외
```

---

### LEFT JOIN (LEFT OUTER JOIN)

왼쪽 테이블의 **모든 행**을 반환하고, 오른쪽에 일치하는 행이 없으면 NULL로 채운다.

```sql
SELECT u.id, u.name, o.id AS order_id, o.total_price
FROM users u
LEFT JOIN orders o ON u.id = o.user_id;
```

```
결과:
┌──────┬───────────┬──────────┬─────────────┐
│u.id  │  u.name   │ order_id │ total_price │
├──────┼───────────┼──────────┼─────────────┤
│  1   │  Alice    │    1     │   30000     │
│  2   │  Bob      │    2     │   15000     │
│  3   │  Carol    │   NULL   │    NULL     │  ← 주문 없는 유저도 포함
└──────┴───────────┴──────────┴─────────────┘
```

**주문이 없는 유저 찾기** (안티조인 패턴):

```sql
SELECT u.id, u.name
FROM users u
LEFT JOIN orders o ON u.id = o.user_id
WHERE o.id IS NULL;  -- 오른쪽이 NULL인 것 = 왼쪽에만 존재
```

---

### RIGHT JOIN

LEFT JOIN의 반대. 오른쪽 테이블의 **모든 행**을 반환.
실무에서는 LEFT JOIN으로 방향을 바꿔 쓰는 것이 일반적이다 (가독성).

```sql
-- 이 두 쿼리는 결과가 동일
SELECT * FROM orders o RIGHT JOIN users u ON o.user_id = u.id;
SELECT * FROM users  u LEFT JOIN orders o ON u.id = o.user_id;
```

---

### FULL OUTER JOIN

양쪽 테이블의 **모든 행**을 반환. 일치하지 않는 쪽은 NULL.
MySQL은 기본 지원 안 함 → UNION으로 구현.

```sql
-- PostgreSQL
SELECT u.name, o.total_price
FROM users u
FULL OUTER JOIN orders o ON u.id = o.user_id;

-- MySQL (UNION으로 대체)
SELECT u.name, o.total_price FROM users u LEFT  JOIN orders o ON u.id = o.user_id
UNION ALL
SELECT u.name, o.total_price FROM users u RIGHT JOIN orders o ON u.id = o.user_id
WHERE u.id IS NULL;
```

---

### GROUP BY

행을 특정 컬럼 기준으로 그룹화하고, 집계 함수(COUNT, SUM, AVG, MAX, MIN)를 적용한다.

```sql
-- 유저별 주문 수와 총 구매액
SELECT user_id,
       COUNT(*)          AS order_count,
       SUM(total_price)  AS total_spent,
       AVG(total_price)  AS avg_price,
       MAX(total_price)  AS max_price
FROM orders
GROUP BY user_id;
```

**GROUP BY + HAVING** (그룹 필터링):

```sql
-- 총 구매액이 100,000원 이상인 유저만
SELECT user_id, SUM(total_price) AS total_spent
FROM orders
GROUP BY user_id
HAVING SUM(total_price) >= 100000;
```

> WHERE는 그룹화 전 행 단위 필터, HAVING은 그룹화 후 집계 결과 필터.

```sql
-- WHERE + GROUP BY + HAVING 조합
SELECT user_id, SUM(total_price) AS total_spent
FROM orders
WHERE status = 'PAID'          -- 행 필터 (인덱스 활용 가능)
GROUP BY user_id
HAVING SUM(total_price) >= 100000;  -- 집계 후 필터
```

---

### ORDER BY

결과를 특정 컬럼 기준으로 정렬한다.

```sql
-- 기본: 최신 주문 먼저
SELECT * FROM orders ORDER BY created_at DESC;

-- 다중 정렬: 상태 오름차순, 같은 상태 내 금액 내림차순
SELECT * FROM orders ORDER BY status ASC, total_price DESC;

-- 집계 결과 기준 정렬
SELECT user_id, COUNT(*) AS cnt
FROM orders
GROUP BY user_id
ORDER BY cnt DESC;
```

**filesort 주의**: ORDER BY 기준 컬럼에 인덱스가 없으면 메모리/디스크 정렬(filesort) 발생.
인덱스의 정렬 순서와 ORDER BY 방향이 일치하면 filesort 없이 인덱스 순서 그대로 사용 가능.

---

### LIMIT / OFFSET

결과 행 수를 제한하거나 페이지네이션에 사용한다.

```sql
-- 최신 주문 10개
SELECT * FROM orders ORDER BY created_at DESC LIMIT 10;

-- 페이지네이션: 3페이지 (페이지당 10개)
SELECT * FROM orders ORDER BY created_at DESC LIMIT 10 OFFSET 20;

-- 단축 문법 (MySQL): LIMIT offset, count
SELECT * FROM orders ORDER BY created_at DESC LIMIT 20, 10;
```

**OFFSET 페이지네이션의 함정**:

```sql
-- OFFSET이 커질수록 느려진다
-- OFFSET 100000이면 100010개를 읽고 앞 100000개를 버림
SELECT * FROM orders ORDER BY id DESC LIMIT 10 OFFSET 100000;  -- 느림

-- 커서 기반 페이지네이션으로 개선
SELECT * FROM orders
WHERE id < :last_id   -- 이전 페이지의 마지막 id
ORDER BY id DESC
LIMIT 10;             -- 인덱스로 바로 탐색
```

---

### 서브쿼리 vs JOIN

```sql
-- 서브쿼리 방식
SELECT * FROM orders
WHERE user_id IN (
    SELECT id FROM users WHERE grade = 'VIP'
);

-- JOIN 방식 (보통 더 빠름)
SELECT o.*
FROM orders o
INNER JOIN users u ON o.user_id = u.id
WHERE u.grade = 'VIP';
```

- IN 서브쿼리는 결과가 큰 경우 EXISTS로 바꾸는 것이 유리할 수 있다.
- 상관 서브쿼리(Correlated Subquery)는 행마다 반복 실행 → 성능 주의.

```sql
-- 상관 서브쿼리 (각 주문마다 서브쿼리 실행)
SELECT o.id,
       (SELECT name FROM users WHERE id = o.user_id) AS user_name
FROM orders o;

-- JOIN으로 변환 (1번만 조인)
SELECT o.id, u.name AS user_name
FROM orders o
JOIN users u ON o.user_id = u.id;
```

---

## 2. 인덱스

### 복합 인덱스 핵심 규칙

```
1순위: 등치(=) 조건을 범위 조건보다 앞에
2순위: 등치 조건이 여러 개면 카디널리티 높은 것 앞에
```

카디널리티는 보조 기준이다. 등치/범위 구분이 더 중요.

#### 예시 쿼리

```sql
SELECT * FROM rent_bill
WHERE contract_id = 1
AND   status      = 'UNPAID'
AND   due_date BETWEEN '2024-01-01' AND '2024-12-31'
ORDER BY due_date DESC;
```

| 조건 | 타입 | 비고 |
|------|------|------|
| `contract_id = 1` | 등치(=) | 카디널리티 높음 |
| `status = 'UNPAID'` | 등치(=) | 카디널리티 낮음 (3가지) |
| `due_date BETWEEN ...` | 범위 | 마지막에 와야 함 |

```sql
-- 틀린 인덱스 (범위 조건이 중간에)
CREATE INDEX idx_wrong ON rent_bill (contract_id, due_date, status);

-- 올바른 인덱스 (범위 조건이 마지막)
CREATE INDEX idx_correct ON rent_bill (contract_id, status, due_date);
```

#### B+Tree 리프 노드 구조로 이해하기

B+Tree 인덱스는 키 조합 전체를 사전순으로 정렬해서 저장한다.
리프 노드는 이중 연결 리스트 형태.

`(contract_id, due_date, status)` 인덱스일 때:

```
┌──────────────┬─────────────┬─────────┐
│ contract_id  │  due_date   │ status  │
├──────────────┼─────────────┼─────────┤
│      1       │ 2024-01-01  │OVERDUE  │
│      1       │ 2024-01-01  │  PAID   │
│      1       │ 2024-01-01  │ UNPAID  │  ← 1월 끝
│      1       │ 2024-02-01  │OVERDUE  │  ← 2월 시작, status 리셋
│      1       │ 2024-02-01  │  PAID   │
│      1       │ 2024-02-01  │ UNPAID  │  ← 2월 끝
│      1       │ 2024-03-01  │  PAID   │  ← 3월 시작, status 리셋
│      1       │ 2024-03-01  │ UNPAID  │
│     ...      │     ...     │   ...   │
│      1       │ 2024-12-01  │OVERDUE  │
│      1       │ 2024-12-01  │  PAID   │
│      1       │ 2024-12-01  │ UNPAID  │  ← 범위 끝
│      2       │ 2024-01-01  │  PAID   │  ← contract_id 바뀜
└──────────────┴─────────────┴─────────┘
```

due_date 범위 안에서 status는 월마다 리셋되어 뒤섞인다.
→ UNPAID만 골라내려면 범위 내 전체를 순차 스캔해야 함.

`(contract_id, status, due_date)` 인덱스일 때:

```
┌──────────────┬─────────┬─────────────┐
│ contract_id  │ status  │  due_date   │
├──────────────┼─────────┼─────────────┤
│      1       │OVERDUE  │ 2024-01-01  │
│      1       │OVERDUE  │ 2024-02-01  │
│      1       │  PAID   │ 2024-01-01  │
│      1       │  PAID   │ 2024-02-01  │
│      1       │ UNPAID  │ 2024-01-01  │  ← 여기로 점프
│      1       │ UNPAID  │ 2024-02-01  │  ← 연속
│      1       │ UNPAID  │ 2024-12-01  │  ← 여기까지만 읽고 종료
│      2       │  PAID   │ 2024-01-01  │
└──────────────┴─────────┴─────────────┘
```

`contract_id = 1` AND `status = 'UNPAID'` 구간 안에서 due_date가 연속으로 정렬됨.
→ BETWEEN 범위를 인덱스로 바로 탐색 + 종료 가능.

#### 핵심 원리

```
B+Tree에서 다음 컬럼의 정렬은
이전 컬럼의 값이 고정(=)일 때만 유효하다.
범위 조건이 오면 그 안에서 다음 컬럼은 뒤섞인 상태가 된다.
```

#### 인덱스 효과 비교 (EXPLAIN 기준)

| 인덱스 | Access | Index Filter | Table Filter |
|--------|--------|--------------|--------------|
| `(contract_id, due_date, status)` | contract_id + due_date 범위 | status 전체 스캔 | - |
| `(contract_id, status, due_date)` | contract_id + status + due_date 범위 | 없음 | - |

> EXPLAIN 실행 시 `Extra` 컬럼에 `Using index condition` 이 보이면 Index Filter 발생 중.
> 이상적으로는 `Using index` (Covering Index) 또는 아무것도 없는 것이 좋다.

---

### Covering Index

SELECT 컬럼이 인덱스에 모두 포함되면 테이블 접근 없이 인덱스만으로 결과 반환.

```sql
-- 이 쿼리는 인덱스 (contract_id, status, due_date)만으로 처리 가능
SELECT due_date, status
FROM rent_bill
WHERE contract_id = 1
AND   status = 'UNPAID'
AND   due_date BETWEEN '2024-01-01' AND '2024-12-31';

-- SELECT * 이면 테이블 접근 필요 (인덱스에 없는 컬럼 존재)
SELECT *  -- amount, paid_at 등 인덱스 밖의 컬럼 때문에 테이블 접근 발생
FROM rent_bill ...
```

---

### 인덱스 단점

- INSERT 느려짐
- UPDATE 느려짐
- 저장공간 증가
- 이유: 인덱스도 같이 정렬 갱신해야 하기 때문

---

### 풀스캔이 인덱스보다 빠른 경우

핵심 이유: **랜덤 I/O vs 순차 I/O**

```
인덱스 사용: 인덱스 탐색(랜덤 I/O) → 테이블 행 접근(랜덤 I/O) × 결과 수
풀스캔:      테이블 처음부터 끝까지 순차 읽기(Sequential I/O) 1번

HDD 기준 랜덤 I/O는 순차 I/O보다 수십~수백 배 느리다.
```

| 상황 | 풀스캔이 유리한 이유 |
|------|------|
| 결과가 전체의 20~30% 이상 | 랜덤 I/O × 많은 행 > 순차 스캔 1번 |
| 테이블이 작음 | 인덱스 탐색 오버헤드가 더 큼 |
| 카디널리티 낮은 컬럼 단독 조회 | 선택성이 없어 랜덤 I/O만 늘어남 |
| SELECT * + 대량 조회 | Covering Index 불가 → 랜덤 I/O 폭발 |

> EXPLAIN 에서 `type: ALL` 이 나오면 풀스캔. 옵티마이저가 맞는 판단을 한 경우가 대부분이다.

MySQL 옵티마이저는 보통 **전체의 20~30% 이상** 읽어야 하면 풀스캔을 선택한다.

---

### LIKE + 인덱스 함정

```sql
CREATE INDEX idx_name ON products (name);
```

| 쿼리 | 인덱스 | 이유 |
|------|--------|------|
| `LIKE 'lap%'` | 사용 가능 (range) | 시작점 고정 → B+Tree 범위 탐색 가능 |
| `LIKE '%laptop%'` | **불가 (풀스캔)** | 시작점 알 수 없음 → 어디서 탐색해야 할지 모름 |

**앞에 `%` 가 붙는 순간 B+Tree 인덱스는 무력화된다.**

중간/후방 검색이 필요하면:

```sql
-- MySQL: Full-Text Index (역인덱스)
CREATE FULLTEXT INDEX idx_name_fulltext ON products (name);
SELECT * FROM products WHERE MATCH(name) AGAINST('laptop');

-- 한국어 포함 시 ngram parser 필요
CREATE FULLTEXT INDEX idx_name_fulltext ON products (name) WITH PARSER ngram;

-- 대규모 검색: Elasticsearch로 분리
```

---

### OR 조건과 인덱스

```sql
SELECT * FROM orders WHERE user_id = ? OR product_id = ?;
```

각각 인덱스가 있으면 MySQL이 **Index Merge** 를 시도하지만,
결과가 많으면 오버헤드(합집합 + 중복 제거) 때문에 오히려 느릴 수 있다.

더 나은 설계:

```sql
SELECT * FROM orders WHERE user_id = ?
UNION ALL
SELECT * FROM orders WHERE product_id = ?;
-- 각 쿼리가 독립적으로 인덱스를 활용
```

---

### NULL 컬럼과 인덱스

MySQL InnoDB는 **NULL도 인덱스에 저장**한다.
NULL은 B+Tree에서 가장 작은 값 → 인덱스 맨 앞에 저장.

```sql
-- user_id에 인덱스 있으면 IS NULL도 인덱스 탐색 가능
SELECT * FROM coupons WHERE user_id IS NULL;
-- EXPLAIN: type: ref
```

단, NULL로 비즈니스 상태를 표현하면 의도가 불분명해진다.

```sql
-- 더 나은 설계
ALTER TABLE coupons ADD COLUMN coupon_type ENUM('PUBLIC', 'USER_SPECIFIC');
CREATE INDEX idx_coupon_type ON coupons (coupon_type);
SELECT * FROM coupons WHERE coupon_type = 'PUBLIC';
```

---

### GROUP BY + ORDER BY COUNT DESC에서 인덱스가 약한 이유

```sql
SELECT contract_id, COUNT(*) AS cnt
FROM rent_bill
GROUP BY contract_id
ORDER BY cnt DESC
LIMIT 3;
```

```
실행 순서:
1. 전체 테이블 읽기          ← 인덱스로 줄일 수 없음 (COUNT 위해 다 읽어야)
2. contract_id 별로 그룹핑
3. 각 그룹의 COUNT 계산      ← 이 시점에야 COUNT 값을 앎
4. COUNT 기준으로 정렬       ← filesort (인덱스 순서와 무관한 새 값)
5. LIMIT 3 으로 자르기
```

LIMIT 3이 맨 마지막에 적용됨.
**3개만 필요하더라도 전체를 다 집계하고 정렬한 후에야 상위 3개를 자를 수 있다.**

이 쿼리가 느리다면 인덱스 튜닝보다 **집계 테이블 별도 관리나 캐싱**으로 접근하는 게 맞다.

---

### 인덱스 과다 설계 판단

```
orders: id(PK), user_id, product_id, status, total_price, created_at
```

주요 쿼리 패턴:
1. `WHERE user_id = ? ORDER BY created_at DESC`
2. `WHERE status = 'SHIPPING'`
3. `SELECT SUM(total_price) FROM orders`

| 인덱스 | 필요 여부 | 이유 |
|--------|-----------|------|
| `(user_id, created_at)` | **필요** | 등치 탐색 + ORDER BY filesort 방지 |
| `(status)` | **불필요** | 카디널리티 낮음, 풀스캔이 나을 수 있음 |
| `(total_price)` | **불필요** | WHERE 없는 전체 집계, 인덱스 효과 미미 |

```sql
-- 필요한 인덱스 1개
CREATE INDEX idx_orders_user_created ON orders (user_id, created_at);
```

인덱스는 많을수록 좋은 게 아니다.
쓸모없는 인덱스는 INSERT/UPDATE 때마다 정렬 갱신 비용만 발생시킨다.

---

### 복합 인덱스 + 정렬 + LIMIT 최적화

```sql
SELECT * FROM posts
WHERE author_id = ?
AND status = 'PUBLISHED'
ORDER BY created_at DESC
LIMIT 10;
```

최적 인덱스:

```sql
CREATE INDEX idx_posts ON posts (author_id, status, created_at);
```

```
author_id = ?          → 등치, 카디널리티 높음  → 첫 번째
status = 'PUBLISHED'   → 등치, 카디널리티 낮음  → 두 번째 (등치라 범위보다 앞)
created_at             → ORDER BY 대상          → 마지막
```

`author_id + status` 구간 안에서 `created_at`이 정렬 → filesort 없음 + **Early Termination** (LIMIT 10이면 10개만 읽고 종료).

---

## 3. MySQL 실무 최적화 설정

### InnoDB 버퍼 풀 (가장 중요)

```ini
# my.cnf / my.ini
[mysqld]
innodb_buffer_pool_size = 4G  # 서버 RAM의 70~80% (DB 전용 서버 기준)
innodb_buffer_pool_instances = 4  # buffer_pool_size / 1GB 단위 (병렬 접근 최적화)
```

- 테이블 데이터와 인덱스를 메모리에 캐싱
- 이 값이 작으면 디스크 I/O가 폭발적으로 증가
- `SHOW STATUS LIKE 'Innodb_buffer_pool_read_requests'` / `Innodb_buffer_pool_reads` 로 히트율 확인
  - 히트율 = (read_requests - reads) / read_requests × 100 → **99% 이상**이 목표

---

### 쿼리 캐시 (MySQL 8.0에서 제거됨)

MySQL 5.7 이하에서만 해당:

```ini
query_cache_type = 0   # 8.0에서 폐지됨, 5.7 이하에서도 쓰기 경합 시 비활성화 권장
query_cache_size = 0
```

MySQL 8.0+에서는 쿼리 캐시 자체가 없으므로 애플리케이션 레벨(Redis 등)에서 캐싱.

---

### 정렬 / 조인 버퍼

```ini
sort_buffer_size = 4M      # filesort 시 사용, 세션당 할당 → 너무 크게 하면 메모리 낭비
join_buffer_size = 4M      # 인덱스 없이 조인 시 사용 (Block Nested Loop)
read_rnd_buffer_size = 4M  # ORDER BY 후 테이블 접근 시 랜덤 I/O 최소화
```

- 이 값은 커넥션마다 독립적으로 할당되므로 커넥션 수 × 값 = 실제 메모리 사용
- `SHOW STATUS LIKE 'Sort_merge_passes'` 가 높으면 `sort_buffer_size` 증가 고려

---

### InnoDB 로그 / I/O 설정

```ini
innodb_log_file_size = 1G          # Redo 로그 크기 (크면 복구 느리지만 쓰기 성능 향상)
innodb_flush_log_at_trx_commit = 1 # 1=ACID 보장(기본), 2=성능 우선(crash 시 1초 데이터 손실 가능)
innodb_flush_method = O_DIRECT     # Linux에서 OS 캐시 이중화 방지 (SSD/NVMe 필수)
innodb_io_capacity = 2000          # HDD: 200, SSD: 2000~10000 (백그라운드 I/O 작업량)
innodb_io_capacity_max = 4000      # io_capacity의 2배
```

---

### 커넥션 / 스레드

```ini
max_connections = 500          # 동시 커넥션 수 (커넥션 풀 max_pool_size와 맞춤)
thread_cache_size = 100        # 스레드 재사용 (커넥션 생성 오버헤드 감소)
wait_timeout = 600             # 유휴 커넥션 유지 시간(초), 커넥션 풀 사용 시 낮게
interactive_timeout = 600
```

---

### 슬로우 쿼리 로그

```ini
slow_query_log = ON
slow_query_log_file = /var/log/mysql/slow.log
long_query_time = 1            # 1초 이상 쿼리 기록
log_queries_not_using_indexes = ON  # 인덱스 미사용 쿼리도 기록
```

분석 툴: `mysqldumpslow`, `pt-query-digest` (Percona Toolkit)

---

### 실무 EXPLAIN 활용

```sql
EXPLAIN SELECT * FROM orders WHERE user_id = 1 ORDER BY created_at DESC LIMIT 10;
EXPLAIN ANALYZE SELECT ...;  -- MySQL 8.0+: 실제 실행 시간 포함
```

| key 컬럼 | 의미 |
|----------|------|
| NULL | 인덱스 미사용 |
| idx_xxx | 해당 인덱스 사용 |

| type | 성능 순서 |
|------|-----------|
| system / const | 최상 |
| eq_ref | PK/Unique 조인 |
| ref | 인덱스 동등 탐색 |
| range | 범위 탐색 |
| index | 인덱스 풀스캔 |
| ALL | 테이블 풀스캔 (주의) |

---

### 통계 갱신

```sql
ANALYZE TABLE orders;  -- 인덱스 통계 갱신 (옵티마이저 힌트 재계산)
```

대규모 데이터 변경(bulk insert/delete) 후에는 통계가 오래되어 잘못된 실행 계획이 나올 수 있다.

---

### MySQL 주요 설정 요약

| 설정 | 권장값 | 설명 |
|------|--------|------|
| `innodb_buffer_pool_size` | RAM의 70~80% | 가장 중요한 설정 |
| `innodb_flush_log_at_trx_commit` | 1 (ACID) / 2 (성능) | 데이터 안전성 vs 쓰기 성능 |
| `innodb_flush_method` | O_DIRECT | Linux+SSD 조합에서 필수 |
| `innodb_io_capacity` | SSD: 2000~10000 | 백그라운드 I/O 속도 |
| `max_connections` | 커넥션 풀 크기에 맞춤 | 너무 크면 메모리 낭비 |
| `slow_query_log` | ON | 슬로우 쿼리 분석 기반 |

---

## 4. PostgreSQL 실무 최적화 설정

### 공유 버퍼 (Shared Buffers)

```conf
# postgresql.conf
shared_buffers = 4GB           # RAM의 25% (OS 캐시와 병용하므로 MySQL보다 낮게)
effective_cache_size = 12GB    # OS 캐시 포함 총 사용 가능 메모리 (플래너 힌트용)
                               # RAM의 75% 정도로 설정 (실제 할당 아님, 추정치)
```

- PostgreSQL은 OS의 페이지 캐시를 적극 활용 → shared_buffers는 RAM의 25%가 일반적
- `effective_cache_size`는 옵티마이저가 인덱스 사용 여부를 판단할 때 참고하는 추정값

---

### 쓰기 성능

```conf
wal_buffers = 64MB              # WAL 쓰기 버퍼 (shared_buffers의 약 3%, 최대 64MB)
checkpoint_completion_target = 0.9  # 체크포인트를 체크포인트 간격의 90%에 걸쳐 분산
max_wal_size = 4GB              # WAL 최대 크기 (크면 체크포인트 간격 늘어남)
min_wal_size = 1GB

synchronous_commit = on         # on=ACID 보장 / off=성능 우선 (crash 시 최대 wal_writer_delay만큼 손실)
```

---

### 쿼리 플래너 메모리

```conf
work_mem = 64MB                 # 정렬/해시 조인에 사용 (세션당 연산당 할당)
                                # 복잡한 쿼리는 여러 번 할당 → 총 메모리 = 커넥션 수 × work_mem × n
maintenance_work_mem = 512MB    # VACUUM, CREATE INDEX, ALTER TABLE 등에 사용
```

- `work_mem`이 너무 작으면 디스크 정렬 발생 (`EXPLAIN` 에서 "Sort Method: external merge Disk" 확인)
- `work_mem`이 너무 크면 많은 커넥션에서 OOM 위험

---

### 병렬 쿼리

```conf
max_parallel_workers_per_gather = 4   # 쿼리당 병렬 워커 수 (CPU 코어 수의 절반)
max_parallel_workers = 8              # 전체 병렬 워커 풀 크기
max_worker_processes = 16             # 전체 백그라운드 프로세스 수
parallel_tuple_cost = 0.1
parallel_setup_cost = 1000
```

---

### VACUUM / Autovacuum (PostgreSQL 특유)

PostgreSQL은 MVCC 방식으로 삭제/수정된 행을 즉시 제거하지 않음 → Dead Tuple 축적 → 테이블 팽창.

```conf
autovacuum = on                       # 반드시 ON
autovacuum_vacuum_threshold = 50      # dead tuple 50개 이상이면 vacuum 실행
autovacuum_vacuum_scale_factor = 0.1  # 테이블 크기의 10% 이상 dead tuple 시 vacuum
autovacuum_analyze_threshold = 50
autovacuum_analyze_scale_factor = 0.05
autovacuum_vacuum_cost_delay = 2ms    # 기본 20ms → 낮출수록 빠르지만 I/O 증가
autovacuum_max_workers = 5            # 기본 3 → 테이블 많으면 늘림
```

수동 vacuum:

```sql
VACUUM ANALYZE orders;          -- dead tuple 정리 + 통계 갱신
VACUUM FULL orders;             -- 테이블 재작성 (잠금 발생, 다운타임 필요)
REINDEX TABLE orders;           -- 인덱스 재구성
```

**Bloat 모니터링**:

```sql
SELECT relname, n_dead_tup, n_live_tup,
       round(n_dead_tup::numeric / nullif(n_live_tup + n_dead_tup, 0) * 100, 2) AS dead_ratio
FROM pg_stat_user_tables
ORDER BY n_dead_tup DESC;
```

---

### 커넥션 관리

PostgreSQL은 커넥션당 프로세스를 fork하므로 커넥션 오버헤드가 MySQL보다 크다.
→ 반드시 **PgBouncer** 같은 커넥션 풀러를 앞에 두는 것이 실무 표준.

```conf
max_connections = 200           # PgBouncer 사용 시 100~200이면 충분
```

PgBouncer 설정 (`pgbouncer.ini`):

```ini
[databases]
mydb = host=localhost dbname=mydb

[pgbouncer]
pool_mode = transaction         # transaction 모드 (세션 모드보다 효율적)
max_client_conn = 1000          # 클라이언트 최대 커넥션
default_pool_size = 50          # DB당 실제 커넥션 수
```

---

### 슬로우 쿼리 로그

```conf
log_min_duration_statement = 1000   # 1000ms 이상 쿼리 로그
log_line_prefix = '%t [%p]: [%l-1] user=%u,db=%d,app=%a,client=%h '
log_checkpoints = on
log_autovacuum_min_duration = 0     # 모든 autovacuum 로그 (0 = 항상)
```

---

### 실무 EXPLAIN ANALYZE

```sql
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT * FROM orders WHERE user_id = 1 ORDER BY created_at DESC LIMIT 10;
```

- `Seq Scan`: 풀스캔 → 인덱스 필요한지 검토
- `Index Scan`: 인덱스 탐색 + 테이블 접근
- `Index Only Scan`: Covering Index (이상적)
- `Buffers: shared hit=X read=Y`: hit은 캐시에서, read는 디스크에서 읽음
- `Sort Method: external merge Disk`: work_mem 부족 → 디스크 정렬 발생

---

### PostgreSQL 인덱스 유형

```sql
-- 기본 B-Tree (범위/등치 탐색)
CREATE INDEX idx_created ON orders (created_at);

-- Hash (등치만, 범위 불가)
CREATE INDEX idx_user_hash ON orders USING HASH (user_id);

-- GIN (배열, JSONB, Full-Text)
CREATE INDEX idx_tags ON products USING GIN (tags);

-- BRIN (시계열, 물리적 순서와 논리적 순서가 일치할 때)
-- 인덱스 크기 매우 작음, 삽입 순서대로 쌓이는 로그성 테이블에 유리
CREATE INDEX idx_log_time ON logs USING BRIN (created_at);

-- 부분 인덱스 (조건을 만족하는 행만 인덱싱)
CREATE INDEX idx_active_users ON users (email) WHERE status = 'ACTIVE';
-- 인덱스 크기 감소 + 탐색 효율 증가
```

---

### 통계 타겟

PostgreSQL 플래너의 실행 계획 품질은 통계에 의존.

```sql
-- 특정 컬럼의 통계 정밀도 높이기 (기본 100, 최대 10000)
ALTER TABLE orders ALTER COLUMN status SET STATISTICS 500;
ANALYZE orders;

-- 전체 기본값 변경
SET default_statistics_target = 200;
```

---

### PostgreSQL 주요 설정 요약

| 설정 | 권장값 | 설명 |
|------|--------|------|
| `shared_buffers` | RAM의 25% | 공유 버퍼 |
| `effective_cache_size` | RAM의 75% | 플래너용 추정치 |
| `work_mem` | 64MB~256MB | 정렬/해시 조인 메모리 |
| `maintenance_work_mem` | 512MB~2GB | VACUUM/인덱스 생성 |
| `autovacuum` | on | 반드시 활성화 |
| `max_connections` | 100~200 | PgBouncer 앞단에 두고 낮게 유지 |
| `checkpoint_completion_target` | 0.9 | 쓰기 I/O 분산 |
| `synchronous_commit` | on / off | 안전성 vs 성능 |

---

## 5. EXPLAIN 읽는 법

### MySQL

```sql
EXPLAIN SELECT * FROM orders WHERE user_id = 1 ORDER BY created_at DESC LIMIT 10;
```

| 컬럼 | 확인 포인트 |
|------|-------------|
| `type` | ALL(풀스캔) 경고, ref/range/const 목표 |
| `key` | NULL이면 인덱스 미사용 |
| `rows` | 예상 읽는 행 수 (실제와 다를 수 있음) |
| `Extra` | `Using filesort` → 정렬 최적화 필요, `Using index` → Covering Index |

```sql
-- MySQL 8.0+: 실제 실행 결과 포함
EXPLAIN ANALYZE SELECT ...;
```

### PostgreSQL

```sql
EXPLAIN (ANALYZE, BUFFERS) SELECT ...;
```

| 노드 타입 | 의미 |
|-----------|------|
| `Seq Scan` | 풀스캔 |
| `Index Scan` | 인덱스 + 테이블 접근 |
| `Index Only Scan` | Covering Index (최적) |
| `Hash Join` | 해시 기반 조인 |
| `Nested Loop` | 중첩 루프 조인 (소량 데이터에 유리) |
| `Merge Join` | 정렬 후 조인 (대량 + 정렬된 데이터) |

```
actual time=0.043..1.234   -- 시작~끝 실제 시간(ms)
rows=150 loops=1           -- 실제 반환 행 수 × 실행 횟수
Buffers: shared hit=42 read=8  -- 캐시 히트 vs 디스크 읽기
```

`read` 값이 크면 `shared_buffers` 증가 또는 인덱스 추가 검토.

---

> **정리**: 쿼리 최적화는 EXPLAIN으로 실행 계획을 확인하고, 인덱스 전략을 세운 뒤, DB 설정으로 메모리/I/O를 튜닝하는 순서로 접근한다. 인덱스 없는 설정 튜닝은 효과가 제한적이고, 설정 없는 인덱스는 메모리 부족으로 성능을 내지 못한다.
