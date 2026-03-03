# JPA Fetch Join, Paging, DTO Projection, and OSIV - Complete Summary

------------------------------------------------------------------------

# 1. Fetch Join이란?

Fetch Join은 **JPQL 전용 문법**이며, 연관 엔티티를 한 번의 쿼리로 함께
조회하기 위한 기능이다.

``` java
SELECT t FROM Team t
JOIN FETCH t.members
```

-   SQL 문법이 아님
-   JPQL 문법임
-   연관 객체를 즉시 로딩(Eager Loading)하기 위한 JPA 기능

------------------------------------------------------------------------

# 2. 1:N 관계에서 Fetch Join 동작

## Team(1) : Member(N)

``` java
SELECT t FROM Team t
JOIN FETCH t.members
```

### 실제 SQL 결과

  team_id   member_id
  --------- -----------
  1         10
  1         11
  1         12

부모(Team)가 자식(Member) 수만큼 중복됨.

※ 카다시안 곱이 아니라, 1:N 조인의 자연스러운 결과

------------------------------------------------------------------------

# 3. DISTINCT Fetch Join

``` java
SELECT DISTINCT t
FROM Team t
JOIN FETCH t.members
```

-   DB row는 여전히 중복
-   JPA가 엔티티 중복 제거

## 하지만

❌ 컬렉션 fetch join + 페이징은 불가능\
(Hibernate는 전체 조회 후 메모리 페이징 수행)

------------------------------------------------------------------------

# 4. 안전한 페이징 패턴 (실무 표준)

## 1️⃣ ID 페이징 + IN Fetch Join (가장 추천)

### 1단계

``` java
SELECT t.id FROM Team t
```

### 2단계

``` java
SELECT DISTINCT t
FROM Team t
JOIN FETCH t.members
WHERE t.id IN :ids
```

✔ DB 페이징 정상 작동\
✔ 컬렉션 fetch join 가능\
✔ 가장 안전한 방식

------------------------------------------------------------------------

## 2️⃣ N쪽에서 조회 (N:1 Fetch Join)

``` java
SELECT m FROM Member m
JOIN FETCH m.team
```

✔ 중복 없음\
✔ 페이징 가능\
✔ 매우 안전

------------------------------------------------------------------------

## 3️⃣ BatchSize 사용

``` java
@BatchSize(size = 100)
```

또는

    hibernate.default_batch_fetch_size=100

N+1 → 1+1 구조로 개선

------------------------------------------------------------------------

# 5. DTO 프로젝션이 더 빠른 이유

## 1️⃣ 영속성 컨텍스트 미사용

-   엔티티 등록 없음
-   스냅샷 없음
-   Dirty Checking 없음

## 2️⃣ 필요한 컬럼만 조회

``` java
SELECT new TeamDto(t.id, t.name)
FROM Team t
```

네트워크/메모리 사용 감소

## 3️⃣ Fetch Join 제약 없음

-   1:N 페이징 문제 회피 가능

------------------------------------------------------------------------

# 6. DTO를 써야 하는 경우

✔ 조회 전용 API\
✔ 대량 데이터 조회\
✔ 통계/검색 화면\
✔ 엔티티 수정 필요 없음

------------------------------------------------------------------------

# 7. OSIV(Open Session In View)란?

Spring Boot 기본 설정:

    spring.jpa.open-in-view=true

의미:

HTTP 요청 시작 \~ 응답 완료까지\
영속성 컨텍스트 유지

## OSIV ON

-   Controller에서 Lazy 로딩 가능
-   N+1 위험
-   DB 커넥션 오래 점유

## OSIV OFF (실무 권장)

    spring.jpa.open-in-view=false

-   Service 계층에서 모든 데이터 준비
-   Lazy 로딩은 트랜잭션 안에서만
-   쿼리 추적 명확
-   성능 안정적

------------------------------------------------------------------------

# 8. 실무 철칙 정리

✔ 조회 API → DTO 사용\
✔ 수정/비즈니스 로직 → 엔티티 사용\
✔ 1:N 컬렉션 fetch join + 페이징은 사용하지 않음\
✔ OSIV는 끄고 설계하는 것이 안전

------------------------------------------------------------------------

# 최종 핵심 요약

-   Fetch Join은 JPQL 문법이다.
-   1:N에서 1쪽 fetch join 시 부모가 중복된다.
-   DISTINCT는 엔티티 중복만 제거한다.
-   컬렉션 fetch join + 페이징은 논리적으로 불가능하다.
-   실무에서는 ID 페이징 방식이 가장 안전하다.
-   조회 전용 API는 DTO 프로젝션이 성능상 유리하다.
-   OSIV는 끄고(Service 계층 중심 설계) 가는 것이 실무 표준이다.

------------------------------------------------------------------------

End of Summary
