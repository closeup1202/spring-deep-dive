# JPA Deep Dive: 영속성 컨텍스트와 N+1 문제

## 📌 언제 사용하는가?

### ✅ 반드시 알아야 하는 경우
1. **ORM 기반 개발**: JPA/Hibernate를 사용하는 모든 프로젝트 (필수)
2. **성능 문제 해결**: N+1 문제로 API 응답이 느릴 때
3. **복잡한 조회 쿼리**: 동적 쿼리나 DTO 직접 조회가 필요할 때 (QueryDSL)
4. **데이터 정합성**: 엔티티 간 생명주기를 함께 관리해야 할 때 (Cascade)
5. **대량 데이터 수정**: 벌크 연산으로 성능 최적화가 필요할 때

### ⚠️ 주의가 필요한 경우
- **Lazy Loading 함정**: 프록시 객체를 잘못 사용하면 N+1 발생
- **영속성 컨텍스트 불일치**: 벌크 연산 후 clearAutomatically 설정 필수
- **과도한 Cascade**: 의도치 않은 데이터 삭제 위험

---

## 1. 영속성 컨텍스트 (Persistence Context)
JPA의 핵심은 **"엔티티를 영구 저장하는 환경"**인 영속성 컨텍스트입니다.
`EntityManager`를 통해 엔티티를 저장하거나 조회하면 영속성 컨텍스트에 보관됩니다.

### Dirty Checking (변경 감지)
- **원리:** 트랜잭션이 커밋되는 시점에, 엔티티의 현재 상태와 최초 조회 시점의 상태(스냅샷)를 비교합니다.
- **동작:** 변경된 부분이 있으면 `UPDATE` 쿼리를 자동으로 생성하여 DB에 날립니다.
- **장점:** `repository.save()`를 매번 호출할 필요가 없어 비즈니스 로직이 깔끔해집니다.

## 2. N+1 문제 (The N+1 Problem)
JPA 사용 시 가장 빈번하게 발생하는 성능 문제입니다.
`1`번의 쿼리로 `N`개의 엔티티를 조회했는데, 연관된 엔티티를 가져오기 위해 `N`번의 추가 쿼리가 발생하는 현상입니다.

### 왜 LAZY가 기본값인데 N+1이 생길까?

`FetchType.LAZY`는 "연관 엔티티를 즉시 로딩하지 말고, 필요할 때만 불러와라"는 전략입니다.
JPA는 처음 조회 시 연관 객체 자리에 **프록시(가짜 객체)** 를 꽂아두고, 실제 필드에 접근하는 순간 그제야 DB에 쿼리를 날립니다.

```
Member (id=1)
  └─ team = Proxy$Team { id=10, name=??? }  ← 아직 DB 조회 안 함
                                   ↑
              team.getName() 호출 시 이 시점에 SELECT * FROM team WHERE id=10 실행
```

### N+1 발생 흐름 (다이어그램)

아래는 Member 3명이 서로 다른 Team에 속할 때의 쿼리 흐름입니다.

```
[ 코드 ]  memberRepository.findAll()
    │
    ▼
┌─────────────────────────────────────────┐
│ 쿼리 ①  SELECT * FROM member           │  ← 1번 실행
│   → Member(id=1, team=Proxy#10)        │
│   → Member(id=2, team=Proxy#20)        │
│   → Member(id=3, team=Proxy#30)        │
└─────────────────────────────────────────┘
    │
    ▼  for (Member m : members) { m.getTeam().getName(); }
    │
    ├─ 쿼리 ②  SELECT * FROM team WHERE id=10   ← member(1) 접근 시
    ├─ 쿼리 ③  SELECT * FROM team WHERE id=20   ← member(2) 접근 시
    └─ 쿼리 ④  SELECT * FROM team WHERE id=30   ← member(3) 접근 시

총 쿼리 수 = 1 + N(3) = 4번
Member가 100명이면? → 101번!
```

> **핵심:** LAZY 자체가 문제가 아닙니다.
> "조회한 엔티티를 반복하며 연관 객체에 접근"하는 코드 패턴이 N+1을 유발합니다.

---

### 해결 방법 1: Fetch Join (가장 일반적)

JPQL의 `join fetch`는 SQL의 `INNER JOIN`으로 변환되어, 연관 엔티티를 **단 1번의 쿼리**로 함께 가져옵니다.

```java
// MemberRepository.java
@Query("select m from Member m join fetch m.team")
List<Member> findAllWithTeam();
```

```
[ 코드 ]  memberRepository.findAllWithTeam()
    │
    ▼
┌──────────────────────────────────────────────────────────┐
│ 쿼리 ①  SELECT m.*, t.*                                 │  ← 단 1번!
│         FROM member m                                    │
│         INNER JOIN team t ON m.team_id = t.id           │
│   → Member(id=1, team=Team{id=10, name="TeamA"})        │
│   → Member(id=2, team=Team{id=20, name="TeamB"})        │
│   → Member(id=3, team=Team{id=30, name="TeamC"})        │
└──────────────────────────────────────────────────────────┘

총 쿼리 수 = 1번 (N명이어도 항상 1번!)
```

---

### 해결 방법 2: @EntityGraph

JPQL을 직접 작성하지 않고, 어노테이션으로 Fetch Join 효과를 낼 수 있습니다.
기존 `findAll()` 같은 Spring Data JPA 메서드에도 쉽게 적용할 수 있습니다.

```java
@EntityGraph(attributePaths = {"team"})
@Query("select m from Member m")
List<Member> findAllWithTeamGraph();
```

내부적으로 `LEFT OUTER JOIN`을 사용합니다. (Fetch Join은 INNER JOIN)

---

### ⚠️ 컬렉션 Fetch Join의 함정: 데이터 뻥튀기

`@OneToMany` 관계(컬렉션)에 Fetch Join을 적용하면 SQL의 특성상 **결과 행이 증가**합니다.

```
[ DB 상황 ]
Team(id=10) ── Member(id=1)
           └── Member(id=2)

[ SELECT t.*, m.* FROM team t JOIN member m ON t.id = m.team_id ]

결과 행:
  행① → Team(id=10) | Member(id=1)   ← Team이 2번 나옴!
  행② → Team(id=10) | Member(id=2)   ← Team이 2번 나옴!
```

이 때문에 JPQL에서 `distinct`를 추가해야 합니다.

```java
// Team 기준으로 Members를 Fetch Join할 때
@Query("select distinct t from Team t join fetch t.members")
List<Team> findAllWithMembers();
```

`distinct`는 두 가지 역할을 합니다.
1. SQL에 `DISTINCT` 추가 (DB 레벨 중복 제거)
2. JPA가 결과 리스트에서 **같은 엔티티 인스턴스**를 중복 제거

---

### 해결 방법 비교

```
┌──────────────────┬───────────────────┬────────────────────────────────┐
│      방법        │    JOIN 종류      │           특징                 │
├──────────────────┼───────────────────┼────────────────────────────────┤
│ Fetch Join       │ INNER JOIN        │ 연관 없는 데이터 제외됨        │
│ @EntityGraph     │ LEFT OUTER JOIN   │ 연관 없어도 결과에 포함됨      │
│ Batch Size       │ WHERE id IN (...)  │ 쿼리 수를 1+1로 줄임 (대안)   │
└──────────────────┴───────────────────┴────────────────────────────────┘
```

> **실무 팁:**
> - `@ManyToOne` (단건) → Fetch Join 또는 @EntityGraph
> - `@OneToMany` (컬렉션) → `@BatchSize` 또는 별도 쿼리 분리를 권장
>   (컬렉션 Fetch Join은 페이징과 함께 사용 불가 — Hibernate가 메모리에서 페이징 처리하여 OOM 위험)

## 3. 벌크 연산의 함정 (@Modifying)
`UPDATE`나 `DELETE` 쿼리를 JPQL로 직접 날릴 때 주의해야 합니다.
벌크 연산은 영속성 컨텍스트를 무시하고 **DB에 직접 쿼리**를 날립니다.
따라서 영속성 컨텍스트에 있는 엔티티와 DB의 데이터가 달라지는 **데이터 불일치**가 발생합니다.

- **해결:** `@Modifying(clearAutomatically = true)`를 사용하여 쿼리 실행 후 영속성 컨텍스트를 비워줘야 합니다.

## 4. JPA Auditing
`@CreatedDate`, `@LastModifiedDate`를 사용하여 생성일/수정일을 자동 관리합니다.
- `BaseTimeEntity`를 상속받아 중복 코드를 제거하는 패턴이 표준입니다.
- 메인 클래스에 `@EnableJpaAuditing` 필수.

## 5. QueryDSL & 연관관계 심화
### QueryDSL
- **동적 쿼리:** `BooleanExpression`을 사용하여 `where` 조건을 레고 조립하듯 만들 수 있습니다. (`null`이면 조건 무시)
- **DTO 조회:** 엔티티 전체를 조회하지 않고, `Projections`를 사용하여 필요한 필드만 조회하여 성능을 최적화합니다.

### Cascade & OrphanRemoval
- **Aggregate Root:** 부모 엔티티(`Order`)가 자식 엔티티(`OrderItem`)의 생명주기를 관리합니다.
- `CascadeType.ALL`: 부모 저장/삭제 시 자식도 같이 저장/삭제.
- `orphanRemoval = true`: 부모의 리스트에서 자식을 제거하면 DB에서도 삭제됨.

## 6. 실습 내용
1. `src/test/java/com/exam/jpa/repository/RepositoryTest.java`: JPA 기본 기능 테스트
2. `src/test/java/com/exam/jpa/repository/OrderRepositoryTest.java`: QueryDSL 및 Cascade 테스트
