# 인덱스

## 복합 인덱스
## 핵심 규칙

```
1순위: 등치(=) 조건을 범위 조건보다 앞에
2순위: 등치 조건이 여러 개면 카디널리티 높은 것 앞에
```

카디널리티는 보조 기준이다. 등치/범위 구분이 더 중요

---

## 예시 쿼리

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

---

## 왜 범위 조건 이후 컬럼은 인덱스 필터링에 사용 불가한가

### B+Tree 리프 노드 구조

B+Tree 인덱스는 키 조합 전체를 사전순으로 정렬해서 저장한다.
리프 노드는 이중 연결 리스트 형태.

#### `(contract_id, due_date, status)` 인덱스일 때

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

#### 범위 조건 후 MySQL이 실제로 하는 일

```
1. contract_id = 1 시작점 탐색     → 인덱스로 점프 (Access)
2. due_date = '2024-01-01' 시작점  → 인덱스로 점프 (Access)
3. due_date = '2024-12-31' 끝까지  → 범위 스캔

   [2024-01-01 | OVERDUE]  ← status 값 비교 (Index Filter)
   [2024-01-01 |   PAID ]  ← status 값 비교 (Index Filter)
   [2024-01-01 | UNPAID ]  ← 히트
   [2024-02-01 | OVERDUE]  ← status 값 비교 (Index Filter)
   [2024-02-01 |   PAID ]  ← status 값 비교 (Index Filter)
   [2024-02-01 | UNPAID ]  ← 히트
   ...범위 안 전체 행을 순차 스캔하며 status를 하나씩 비교
```

### `(contract_id, status, due_date)` 인덱스일 때

```
┌──────────────┬─────────┬─────────────┐
│ contract_id  │ status  │  due_date   │
├──────────────┼─────────┼─────────────┤
│      1       │OVERDUE  │ 2024-01-01  │
│      1       │OVERDUE  │ 2024-02-01  │
│      1       │OVERDUE  │ 2024-04-01  │
│      1       │  PAID   │ 2024-01-01  │
│      1       │  PAID   │ 2024-02-01  │
│      1       │  PAID   │ 2024-03-01  │
│      1       │ UNPAID  │ 2024-01-01  │  ← 여기로 점프
│      1       │ UNPAID  │ 2024-02-01  │  ← 연속
│      1       │ UNPAID  │ 2024-03-01  │  ← 연속
│      1       │ UNPAID  │ 2024-12-01  │  ← 여기까지만 읽고 종료
│      2       │  PAID   │ 2024-01-01  │
└──────────────┴─────────┴─────────────┘
```

`contract_id = 1` AND `status = 'UNPAID'` 구간 안에서 due_date가 연속으로 정렬됨.
→ BETWEEN 범위를 인덱스로 바로 탐색 + 종료 가능.

### 핵심 원리 한 줄 요약

```
B+Tree에서 다음 컬럼의 정렬은
이전 컬럼의 값이 고정(=)일 때만 유효하다.
범위 조건이 오면 그 안에서 다음 컬럼은 뒤섞인 상태가 된다.
```

---

## 인덱스 효과 비교 (EXPLAIN 기준)

| 인덱스 | Access | Index Filter | Table Filter |
|--------|--------|--------------|--------------|
| `(contract_id, due_date, status)` | contract_id + due_date 범위 | status 전체 스캔 | - |
| `(contract_id, status, due_date)` | contract_id + status + due_date 범위 | 없음 | - |

> EXPLAIN 실행 시 `Extra` 컬럼에 `Using index condition` 이 보이면 Index Filter 발생 중.
> 이상적으로는 `Using index` (Covering Index) 또는 아무것도 없는 것이 좋다.

---

## Covering Index

SELECT 컬럼이 인덱스에 모두 포함되면 테이블 접근 없이 인덱스만으로 결과 반환.

```sql
-- 이 쿼리는 인덱스 (contract_id, status, due_date) 만으로 처리 가능
SELECT due_date, status
FROM rent_bill
WHERE contract_id = 1
AND   status = 'UNPAID'
AND   due_date BETWEEN '2024-01-01' AND '2024-12-31';
```

```sql
-- SELECT * 이면 테이블 접근 필요 (인덱스에 없는 컬럼 존재)
SELECT *  -- ← amount, paid_at 등 인덱스 밖의 컬럼 때문에 테이블 접근 발생
FROM rent_bill ...
```

----

## 인덱스 단점:
- INSERT 느려짐
- UPDATE 느려짐
- 저장공간 증가

- 왜? → 인덱스도 같이 정렬 갱신해야 함.

---

## 풀스캔이 인덱스보다 빠른 경우

핵심 이유: **랜덤 I/O vs 순차 I/O**

```
인덱스 사용: 인덱스 탐색(랜덤 I/O) → 테이블 행 접근(랜덤 I/O) × 결과 수
풀스캔:      테이블 처음부터 끝까지 순차 읽기(Sequential I/O) 1번

HDD 기준 랜덤 I/O는 순차 I/O보다 수십~수백 배 느리다.
```

### 1. 조회 비율이 높을 때 (가장 흔한 케이스)

```sql
-- rent_bill 전체 중 UNPAID가 70%라면?
SELECT * FROM rent_bill WHERE status = 'UNPAID';
```

```
인덱스 사용 시:
  인덱스에서 UNPAID 행 70% 찾기
  → 각 행마다 테이블로 랜덤 I/O × 70%
  → 오히려 더 느림

풀스캔:
  테이블 처음부터 끝까지 순차 읽기 1번
  → 더 빠름
```

MySQL 옵티마이저는 보통 **전체의 20~30% 이상** 읽어야 하면 풀스캔을 선택한다.

### 2. 테이블 자체가 작을 때

```
행이 수백~수천 개 수준이면
인덱스 탐색 오버헤드 > 그냥 다 읽는 비용
옵티마이저가 자동으로 풀스캔 선택
```

### 3. 카디널리티가 낮은 단일 컬럼 인덱스

```sql
-- status 단독 인덱스
CREATE INDEX idx_status ON rent_bill (status);

SELECT * FROM rent_bill WHERE status = 'PAID';
-- PAID가 전체의 33%면 → 풀스캔이 나을 수 있음
```

이래서 카디널리티 낮은 컬럼은 단독 인덱스를 잘 안 만들고, 복합 인덱스의 중간에 끼워 넣는다.

### 4. SELECT * 로 많은 행 조회 시 (Covering Index 불가)

```sql
-- 인덱스: (contract_id, status, due_date)
SELECT * FROM rent_bill    -- amount, paid_at 등 인덱스 밖 컬럼 있음
WHERE contract_id = 1;     -- contract_id=1 인 행이 500개라면?

-- 인덱스로 500개 찾기 → 500번 랜덤 I/O로 테이블 접근
-- vs 풀스캔 1번
-- 테이블이 크지 않으면 풀스캔이 유리할 수도
```

### 정리

| 상황 | 풀스캔이 유리한 이유 |
|------|------|
| 결과가 전체의 20~30% 이상 | 랜덤 I/O × 많은 행 > 순차 스캔 1번 |
| 테이블이 작음 | 인덱스 탐색 오버헤드가 더 큼 |
| 카디널리티 낮은 컬럼 단독 조회 | 선택성이 없어 랜덤 I/O만 늘어남 |
| SELECT * + 대량 조회 | Covering Index 불가 → 랜덤 I/O 폭발 |

> EXPLAIN 에서 `type: ALL` 이 나오면 풀스캔. 옵티마이저가 맞는 판단을 한 경우가 대부분이다.

---

## GROUP BY + ORDER BY COUNT DESC 에서 인덱스가 약한 이유

### 인덱스가 잘 먹히는 구조

```
인덱스의 역할:
1. 어디서 읽기 시작할지 (탐색)
2. 어디서 읽기 끝낼지 (범위)
3. 이미 정렬된 순서 활용 (ORDER BY)
```

이 세 가지 모두 **읽기 전에 답을 알고 있을 때** 작동한다.

### COUNT는 읽기 전에 답을 모른다

```sql
SELECT contract_id, COUNT(*) AS cnt
FROM rent_bill
GROUP BY contract_id
ORDER BY cnt DESC
LIMIT 3;
```

```
문제 1: GROUP BY → 어차피 전체를 다 읽어야 COUNT가 가능
         contract_id=1 이 몇 개인지 → 다 세봐야 앎
         contract_id=2 이 몇 개인지 → 다 세봐야 앎
         ...전체 스캔 불가피

문제 2: ORDER BY COUNT(*) DESC → 집계 후에야 나오는 값으로 정렬
         인덱스는 컬럼 값 기준으로 정렬되어 있음
         COUNT 결과는 인덱스에 없음 → 메모리에서 filesort 발생
         집계 전에는 COUNT가 얼마인지 모르니까
```

### 실행 순서로 보면 더 명확

```
1. 전체 테이블 읽기          ← 인덱스로 줄일 수 없음 (COUNT 위해 다 읽어야)
2. contract_id 별로 그룹핑
3. 각 그룹의 COUNT 계산      ← 이 시점에야 COUNT 값을 앎
4. COUNT 기준으로 정렬       ← filesort (인덱스 순서와 무관한 새 값)
5. LIMIT 3 으로 자르기
```

LIMIT 3 이 맨 마지막에 적용되는 게 핵심.
**3개만 필요하더라도 전체를 다 집계하고 정렬한 후에야 상위 3개를 자를 수 있다.**

### WHERE 가 있으면 그나마 도움은 됨

```sql
-- WHERE 로 읽는 범위를 줄이면 인덱스가 부분적으로 기여
SELECT contract_id, COUNT(*) AS cnt
FROM rent_bill
WHERE due_date >= '2024-01-01'   -- 여기서는 인덱스 활용 가능
GROUP BY contract_id
ORDER BY cnt DESC
LIMIT 3;
```

이 쿼리가 느리다면 인덱스 튜닝보다 **집계 테이블 별도 관리나 캐싱** 쪽으로 접근하는 게 맞다.

---

## SELECT SUM() 에서 인덱스 효과

### WHERE 없는 전체 SUM

```sql
SELECT SUM(total_price) FROM orders;
```

기본적으로는 GROUP BY + COUNT와 마찬가지 - 전체를 다 읽어야 하므로 인덱스 탐색 이점 없음.

단, **Covering Index면 조금 다름.**

```
테이블 풀스캔:  모든 컬럼(id, user_id, total_price, status, ...) 전체 읽기
인덱스 스캔:   total_price 컬럼만 있는 인덱스 읽기 → 데이터 크기가 훨씬 작음
```

```sql
CREATE INDEX idx_total_price ON orders (total_price);

-- 이 경우 MySQL이 테이블 대신 인덱스만 스캔
-- 인덱스 크기 << 테이블 크기 → I/O 감소
-- EXPLAIN Extra: Using index
```

전체를 읽는 건 동일하지만 **읽는 데이터 양이 줄어드는 효과**는 있다.

### GROUP BY + ORDER BY COUNT DESC 와 비교

| | `SUM(전체)` | `GROUP BY + ORDER BY COUNT DESC` |
|--|--|--|
| 전체 읽기 | 불가피 | 불가피 |
| Covering Index 효과 | 있음 (I/O 감소) | 있음 (I/O 감소) |
| filesort | **없음** | **발생** |
| 결론 | Covering Index면 조금 도움 | 인덱스 효과 거의 없음 |

GROUP BY + ORDER BY COUNT DESC 가 더 나쁜 이유는 집계 후 **COUNT 값으로 정렬하는 filesort** 가 추가로 발생하기 때문.

### WHERE 있으면 얘기가 달라짐

```sql
-- 이건 인덱스 효과 큼
SELECT SUM(total_price)
FROM orders
WHERE user_id = 1
AND   created_at BETWEEN '2024-01-01' AND '2024-12-31';
-- user_id, created_at 인덱스로 범위 좁힌 후 SUM
```

집계 함수 자체가 문제가 아니라 **WHERE로 얼마나 범위를 줄일 수 있느냐**가 인덱스 효과를 결정한다.

---

## 인덱스 과다 설계 판단 (실전 문제)

### 테이블

```
orders
- id (PK)
- user_id
- product_id
- status
- total_price
- created_at
```

### 조회 패턴

```sql
-- 1. 유저 주문 목록
WHERE user_id = ?
ORDER BY created_at DESC

-- 2. 특정 상태 주문 수
WHERE status = 'SHIPPING'

-- 3. 전체 매출 합계
SELECT SUM(total_price) FROM orders;
```

### 필요한 인덱스: 1개

```sql
CREATE INDEX idx_orders_user_created ON orders (user_id, created_at);
```

쿼리 1 `WHERE user_id = ? ORDER BY created_at DESC` 을 위해.

```
user_id = ? → 등치, 앞에
created_at  → 같은 user_id 내에서 정렬되어 있음
             → ORDER BY 를 위한 filesort 없이 인덱스 순서 그대로 사용
```

`(user_id)` 단독 인덱스로는 부족. user_id로 찾은 후 created_at 정렬을 위해 filesort가 발생하기 때문.

### 필요 없는 인덱스: 2개

**쿼리 2** `WHERE status = 'SHIPPING'`

```sql
-- 불필요
CREATE INDEX idx_status ON orders (status);
```

- status 카디널리티 낮음 (PENDING / SHIPPING / DELIVERED / CANCELLED 등 몇 가지)
- SHIPPING이 전체의 20~30% 이상이면 옵티마이저가 어차피 풀스캔 선택
- 인덱스 만들어도 랜덤 I/O × 많은 행 → 오히려 손해

**쿼리 3** `SELECT SUM(total_price) FROM orders`

```sql
-- 불필요
CREATE INDEX idx_total_price ON orders (total_price);
```

- WHERE 없이 전체 SUM → 어차피 전체를 다 읽어야 함
- Covering Index로 I/O가 약간 줄긴 하지만 효과 미미
- 이 쿼리가 느리다면 인덱스가 아니라 **캐싱 또는 집계 테이블**로 해결하는 게 맞음

### 정리

| 인덱스 | 필요 여부 | 이유 |
|--------|-----------|------|
| `(user_id, created_at)` | **필요** | 등치 탐색 + filesort 방지 |
| `(status)` | **불필요** | 카디널리티 낮음, 풀스캔이 나을 수 있음 |
| `(total_price)` | **불필요** | WHERE 없는 전체 집계, 인덱스 효과 미미 |

인덱스는 많을수록 좋은 게 아니다. 쓸모없는 인덱스는 INSERT/UPDATE 때마다 정렬 갱신 비용만 발생시킨다.

---

## LIKE + 인덱스 함정

### 테이블

```
products
- id (PK)
- name (varchar)
- category_id
- price
- created_at
```

### name에 인덱스를 걸면

```sql
CREATE INDEX idx_name ON products (name);
```

**1번 `LIKE '%laptop%'` → 인덱스 무용, 풀스캔**

```
B+Tree는 왼쪽부터 정렬된 구조.
'%laptop%' 은 시작 문자를 알 수 없음
→ 인덱스 어디서 시작해야 할지 알 수 없음
→ 풀스캔 발생

EXPLAIN: type: ALL
```

**2번 `LIKE 'lap%'` → 인덱스 활용 가능**

```
'lap%' 는 시작점이 'lap'으로 고정됨
→ B+Tree에서 'lap' 이상, 'laq' 미만 구간으로 범위 탐색 가능

EXPLAIN: type: range
```

### 둘 다 성능 좋게 하려면

2번은 일반 B+Tree 인덱스로 해결. 끝.

1번은 B+Tree 인덱스로는 구조적으로 불가능. 대안은 두 가지.

**방법 1: Full-Text Index (MySQL 내장)**

```sql
CREATE FULLTEXT INDEX idx_name_fulltext ON products (name);

-- 기존 LIKE 대신
SELECT * FROM products
WHERE MATCH(name) AGAINST('laptop');

-- 부분 일치가 필요하면 boolean mode
SELECT * FROM products
WHERE MATCH(name) AGAINST('laptop*' IN BOOLEAN MODE);
```

```
내부적으로 역인덱스(Inverted Index) 사용.
'laptop' 이라는 단어가 어느 행에 있는지 미리 구축해둔 구조.
B+Tree와 달리 단어 단위로 검색 가능.
```

단점:
- 기본 최소 단어 길이 제한 (ft_min_word_len = 4)
- 한국어는 ngram parser 별도 설정 필요

```sql
-- 한국어 포함 시
CREATE FULLTEXT INDEX idx_name_fulltext ON products (name) WITH PARSER ngram;
```

**방법 2: Elasticsearch 등 외부 검색 엔진**

```
MySQL은 저장/트랜잭션에 집중
검색은 Elasticsearch에 위임

실무에서 상품 검색 기능이 복잡해지면 이쪽으로 감.
자동완성, 오타 교정, 형태소 분석 등 지원.
```

### 정리

| 쿼리 | 일반 인덱스 | Full-Text Index |
|------|------------|----------------|
| `LIKE 'lap%'` | 사용 가능 (range) | 가능하지만 오버스펙 |
| `LIKE '%laptop%'` | **불가 (풀스캔)** | 가능 |

핵심: **앞에 `%` 가 붙는 순간 B+Tree 인덱스는 무력화된다.**

---

## OR 조건과 인덱스

```sql
SELECT * FROM orders
WHERE user_id = ?
OR product_id = ?;
```

### 각각 인덱스 있으면?

MySQL이 **Index Merge** 최적화를 시도한다.

```
INDEX(user_id)    → user_id = ? 로 행 집합 A
INDEX(product_id) → product_id = ? 로 행 집합 B
A ∪ B (합집합 + 중복 제거)

EXPLAIN Extra: Using union(idx_user_id, idx_product_id); Using where
```

두 인덱스 각각 탐색 + 결과 합치기 + 중복 제거 오버헤드가 있어서,
결과가 많으면 옵티마이저가 그냥 풀스캔을 선택하기도 한다.

### 더 나은 설계: UNION으로 분리

```sql
SELECT * FROM orders WHERE user_id = ?
UNION ALL
SELECT * FROM orders WHERE product_id = ?;
```

각 쿼리가 독립적으로 인덱스를 타므로 Index Merge 오버헤드 없이 최적으로 동작한다.

---

## 복합 인덱스 + 정렬 + LIMIT

```sql
-- posts: id, author_id, status(PUBLISHED/DRAFT), view_count, created_at
SELECT * FROM posts
WHERE author_id = ?
AND status = 'PUBLISHED'
ORDER BY created_at DESC
LIMIT 10;
```

### 최적 인덱스

```sql
CREATE INDEX idx_posts ON posts (author_id, status, created_at);
```

### 순서와 이유

```
author_id = ?          → 등치, 카디널리티 높음  → 첫 번째
status = 'PUBLISHED'   → 등치, 카디널리티 낮음  → 두 번째 (등치라 범위보다 앞)
created_at             → ORDER BY 대상          → 마지막
```

`author_id + status` 구간 안에서 `created_at`이 정렬되어 있으므로:
- `ORDER BY created_at DESC` filesort 없음
- `LIMIT 10` 이면 앞에서 10개만 읽고 즉시 종료 **(Early Termination)**

만약 `(author_id, created_at, status)` 순서라면:
- ORDER BY는 도움이 되지만
- status 필터가 created_at 범위 스캔 도중 적용
- LIMIT 10을 채우려면 status 불일치 행을 건너뛰면서 더 많이 읽어야 할 수 있음

---

## 인덱스 있는데도 느린 경우

```sql
-- INDEX(user_id) 만 있는 상태
SELECT * FROM payments
WHERE user_id = ?
AND paid_at BETWEEN ? AND ?;
```

### 왜 느린가

```
user_id = ? 로 인덱스 탐색
→ user_id에 해당하는 모든 행의 rowid 획득
→ 행마다 테이블로 랜덤 I/O (paid_at 필터링)

user_id = ? 인 데이터가 많을수록
랜덤 I/O × 많은 행 → 느려짐
```

### 바꿔야 할 인덱스

```sql
CREATE INDEX idx_user_paid ON payments (user_id, paid_at);
```

```
user_id = ?              → 등치, 앞에
paid_at BETWEEN ? AND ?  → 범위, 뒤에

→ paid_at 범위까지 인덱스로 처리
→ Table Filter 없이 정확한 행만 테이블 접근
```

---

## NULL 컬럼과 인덱스

```sql
-- coupons: id, user_id(nullable), code, expired_at
SELECT * FROM coupons WHERE user_id IS NULL;
```

### user_id에 인덱스 있으면 효과 있는가

있다. MySQL InnoDB는 **NULL도 인덱스에 저장**한다.

```
NULL은 B+Tree에서 가장 작은 값으로 취급 → 인덱스 맨 앞에 저장
→ WHERE user_id IS NULL 도 인덱스 탐색 가능

EXPLAIN: type: ref
```

단, NULL 비율이 높으면 풀스캔이 더 나을 수 있다는 건 동일하다.

### 이런 구조가 맞는 설계인가

좋지 않다.

```
user_id IS NULL 의 의미가 불명확
→ 미할당? 공개 쿠폰? 삭제된 사용자?
NULL은 "값 없음"이지 "공개"의 의미가 아니다.
```

더 나은 설계:

```sql
-- 명시적 타입 컬럼 추가
ALTER TABLE coupons ADD COLUMN coupon_type ENUM('PUBLIC', 'USER_SPECIFIC');
CREATE INDEX idx_coupon_type ON coupons (coupon_type);

-- 이제 쿼리가 의도를 드러냄
SELECT * FROM coupons WHERE coupon_type = 'PUBLIC';
```

NULL로 비즈니스 상태를 표현하면 의도가 숨겨지고 인덱스 설계도 어색해진다.
