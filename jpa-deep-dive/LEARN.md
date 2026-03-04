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

---

## 2. JPA 연관관계 설계

### DB vs Java 객체 시점

DB에는 FK가 한 쪽 테이블에만 존재한다.
Java 객체에서는 양방향 참조가 가능하다.

```
DB:      rent_bill.contract_id  ← FK 컬럼 하나만 존재
Java:    rentBill.getContract() ← RentBill → Contract 참조
         contract.getRentBills() ← Contract → RentBill 참조 (DB에는 없는 개념)
```

---

### FK 주인 규칙

```
FK 컬럼이 있는 테이블의 Entity = FK 주인
FK 주인 쪽에 @ManyToOne + @JoinColumn 선언
반대편에 @OneToMany(mappedBy = ...) 선언
```

```java
// FK 주인: RentBill (rent_bill 테이블에 contract_id 컬럼 존재)
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "contract_id")   // "나 FK 관리한다"
private Contract contract;

// 거울: Contract (contract 테이블에 FK 컬럼 없음)
@OneToMany(mappedBy = "contract")   // "RentBill.contract 필드가 FK 관리자야"
private List<RentBill> rentBills = new ArrayList<>();
```

`mappedBy` 값은 **FK를 가진 쪽 Entity의 필드명**이다.

---

### @JoinColumn vs mappedBy

| | @JoinColumn | mappedBy |
|--|--|--|
| DB 컬럼 | 실제 FK 컬럼 있음 | 컬럼 없음 |
| INSERT/UPDATE | DB에 반영됨 | **반영 안 됨** |
| 역할 | FK 주인 | 읽기 전용 거울 |

```java
// ❌ DB에 반영 안 됨 (mappedBy는 읽기 전용)
contract.getRentBills().add(rentBill);

// ✅ DB에 반영됨 (FK 주인 쪽을 세팅)
rentBill.setContract(contract);
```

---

### fetch = FetchType.LAZY 필수

```java
// EAGER (기본값) → 쓰면 안 됨
// Contract 조회 시 Unit, Tenant 즉시 조회 쿼리 자동 실행
// 목록 조회 시 N+1 폭발

// LAZY → 실제 접근할 때만 쿼리 실행
@ManyToOne(fetch = FetchType.LAZY)
```

---

### 단방향 vs 양방향 선택 기준

```
부모에서 자식을 cascade로 한 번에 저장/삭제해야 한다  → 양방향
부모 객체에서 자식 목록을 자주 순회해야 한다          → 양방향
자식 목록 조회는 별도 API / Repository 쿼리로 충분하다 → 단방향
```

불필요한 양방향은 의도치 않은 쿼리 발생, JSON 직렬화 순환 참조 문제를 일으킨다.

---

### cascade = CascadeType.ALL 의미

```java
@OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
private List<RentBill> rentBills = new ArrayList<>();
```

- `cascade = CascadeType.ALL` → contract 저장/삭제 시 rentBill도 같이 저장/삭제
- `orphanRemoval = true` → `contract.getRentBills()`에서 제거하면 DB에서도 삭제

---

### 단방향일 때 부모-자식 동시 저장 패턴

Building → Unit이 단방향이면 cascade가 없으므로 Service에서 직접 처리한다.

```java
@Transactional
public void registerBuilding(BuildingCreateRequest request) {

    // 1. 최소 호실 검증
    if (request.getUnits() == null || request.getUnits().isEmpty()) {
        throw new IllegalArgumentException("호실은 최소 1개 이상이어야 합니다.");
    }

    // 2. 건물 먼저 저장 (Unit이 building_id FK를 참조하므로 선행 필수)
    Building building = buildingRepository.save(newBuilding);

    // 3. 호실 저장
    List<Unit> units = request.getUnits().stream()
            .map(u -> Unit.builder()
                    .building(building)  // FK 세팅
                    ...build())
            .toList();
    unitRepository.saveAll(units);
}
```

양방향 cascade였다면 `buildingRepository.save()` 한 번으로 끝나지만,
단방향은 저장 순서가 코드에 명시적으로 드러나서 읽기 쉽고 의도치 않은 삭제를 방지한다.

---

## 3. N+1 문제 (The N+1 Problem)
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

---

### 안전한 페이징 패턴 (실무 표준)

컬렉션 Fetch Join + 페이징을 동시에 사용하면 Hibernate가 메모리에서 전체 데이터를 올린 후 페이징하므로 OOM 위험이 있습니다. 실무에서 권장하는 대안은 3가지입니다.

**패턴 1: ID 페이징 + IN Fetch Join (가장 추천)**

```java
// 1단계: ID만 페이징 조회 (DB 레벨 페이징 정상 작동)
SELECT t.id FROM Team t  → pageable 적용

// 2단계: 조회된 ID로 컬렉션 Fetch Join
SELECT DISTINCT t FROM Team t JOIN FETCH t.members WHERE t.id IN :ids
```

✔ DB 페이징 정상 작동 | ✔ 컬렉션 Fetch Join 가능 | ✔ 가장 안전

**패턴 2: N쪽에서 조회 (N:1 Fetch Join)**

```java
// Member(N) 기준으로 조회하면 중복 없이 페이징 가능
SELECT m FROM Member m JOIN FETCH m.team
```

✔ 중복 없음 | ✔ 페이징 가능 | ✔ 매우 안전 (단, 설계 방향이 뒤집힘)

**패턴 3: @BatchSize 사용**

```java
@BatchSize(size = 100)
private List<Member> members;

// 또는 글로벌 설정
// hibernate.default_batch_fetch_size=100
```

N+1 → 1+1 구조로 개선. 페이징과도 안전하게 함께 사용 가능.

---

## 4. 벌크 연산의 함정 (@Modifying)
`UPDATE`나 `DELETE` 쿼리를 JPQL로 직접 날릴 때 주의해야 합니다.
벌크 연산은 영속성 컨텍스트를 무시하고 **DB에 직접 쿼리**를 날립니다.
따라서 영속성 컨텍스트에 있는 엔티티와 DB의 데이터가 달라지는 **데이터 불일치**가 발생합니다.

- **해결:** `@Modifying(clearAutomatically = true)`를 사용하여 쿼리 실행 후 영속성 컨텍스트를 비워줘야 합니다.

## 5. JPA Auditing
`@CreatedDate`, `@LastModifiedDate`를 사용하여 생성일/수정일을 자동 관리합니다.
- `BaseTimeEntity`를 상속받아 중복 코드를 제거하는 패턴이 표준입니다.
- 메인 클래스에 `@EnableJpaAuditing` 필수.

## 6. QueryDSL & 연관관계 심화
### QueryDSL
- **동적 쿼리:** `BooleanExpression`을 사용하여 `where` 조건을 레고 조립하듯 만들 수 있습니다. (`null`이면 조건 무시)
- **DTO 조회:** 엔티티 전체를 조회하지 않고, `Projections`를 사용하여 필요한 필드만 조회하여 성능을 최적화합니다.

### DTO 프로젝션이 엔티티 조회보다 빠른 이유

| 항목 | 엔티티 조회 | DTO 프로젝션 |
|------|------------|-------------|
| 영속성 컨텍스트 등록 | O (스냅샷 저장) | X |
| Dirty Checking | O | X |
| 조회 컬럼 수 | 전체 | 필요한 것만 |
| 1:N 페이징 문제 | 있음 | 없음 (SQL 직접 제어) |

```java
// JPQL new 문법
SELECT new com.example.TeamDto(t.id, t.name) FROM Team t

// QueryDSL Projections
queryFactory.select(Projections.constructor(TeamDto.class, team.id, team.name))
```

**DTO를 써야 하는 경우**
- 조회 전용 API (수정 불필요)
- 대량 데이터 조회 / 통계·검색 화면
- 1:N 컬렉션 페이징이 필요한 경우

### Cascade & OrphanRemoval
- **Aggregate Root:** 부모 엔티티(`Order`)가 자식 엔티티(`OrderItem`)의 생명주기를 관리합니다.
- `CascadeType.ALL`: 부모 저장/삭제 시 자식도 같이 저장/삭제.
- `orphanRemoval = true`: 부모의 리스트에서 자식을 제거하면 DB에서도 삭제됨.

## 7. OSIV (Open Session In View)

Spring Boot 기본값: `spring.jpa.open-in-view=true`

HTTP 요청 시작부터 응답 완료까지 영속성 컨텍스트(DB 커넥션)를 유지하는 설정입니다.

```
┌─────────────────────────────────────────────────────────────┐
│  HTTP Request                                               │
│  ┌─────────┐   ┌─────────┐   ┌──────────┐   ┌──────────┐  │
│  │ Filter  │──▶│Controller│──▶│ Service  │──▶│   View   │  │
│  └─────────┘   └─────────┘   └──────────┘   └──────────┘  │
│  ←────────────── 영속성 컨텍스트 유지 (OSIV ON) ──────────▶  │
│                              ←── 트랜잭션 ──▶               │
└─────────────────────────────────────────────────────────────┘
```

| 구분 | OSIV ON (기본값) | OSIV OFF (실무 권장) |
|------|-----------------|---------------------|
| Lazy 로딩 | Controller에서도 가능 | 트랜잭션 안에서만 가능 |
| DB 커넥션 점유 | 응답 완료까지 (오래) | 트랜잭션 종료 시 반환 |
| N+1 위험 | 높음 | 낮음 (Service에서 명시적 처리) |
| 쿼리 추적 | 어려움 | 명확 |

```yaml
# application.yml
spring:
  jpa:
    open-in-view: false  # 실무 권장
```

OSIV를 끄면 모든 Lazy 로딩은 `@Transactional` 범위 안에서만 가능하므로, **Service 계층에서 필요한 데이터를 모두 준비하는 설계**가 강제됩니다. 이는 쿼리 위치를 예측 가능하게 만들어 성능 문제를 사전에 방지합니다.

---

## 8. Lombok 엔티티 사용 주의사항

### @ToString 무한 루프

양방향 연관관계에서 `@ToString`을 양쪽 모두 사용하면 **StackOverflowError** 발생.

```java
@Entity
@ToString  // ⚠️ 위험!
public class Order {
    @ManyToOne
    private User user;
}

@Entity
@ToString  // ⚠️ 위험!
public class User {
    @OneToMany(mappedBy = "user")
    private List<Order> orders;
}
```

```
order.toString()
 → user.toString()
   → orders.toString()
     → order.toString()  ← 무한 반복 → StackOverflowError
```

---

### @ToString이 LAZY 로딩을 강제 초기화

```java
@Entity
@ToString  // LAZY 필드에 접근 → SELECT 쿼리 발생
public class Order {
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;
}
```

**발생 시나리오:**

```java
Order order = orderRepository.findById(1L);
System.out.println(order);   // user 테이블 SELECT 발생!
log.debug("Order: {}", order); // N+1 유발 가능
// IntelliJ 디버거에서 변수 hover만 해도 트리거됨
```

---

### 해결 방법

**방법 1: onlyExplicitlyIncluded (가장 권장)**

```java
@Entity
@ToString(onlyExplicitlyIncluded = true)
public class Order {

    @ToString.Include
    private Long id;

    @ToString.Include
    private String orderNumber;

    // 연관관계 필드는 자동 제외
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    @OneToMany(mappedBy = "order")
    private List<OrderItem> orderItems;
}
```

**방법 2: exclude 사용**

```java
@ToString(exclude = {"user", "orderItems"})
public class Order { ... }
```

연관관계 추가 시 누락 위험이 있어 onlyExplicitlyIncluded보다 불안전.

**방법 3: @ToString 자체를 사용하지 않음**

디버깅 불편함을 감수하는 대신 부작용이 없다.

---

### @EqualsAndHashCode도 동일한 문제

```java
// ❌ 연관관계 포함 시 무한 재귀 위험
@EqualsAndHashCode

// ✅ ID만 포함
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Order {
    @EqualsAndHashCode.Include
    private Long id;

    @OneToMany(mappedBy = "order")
    private List<OrderItem> orderItems;  // 자동 제외
}
```

---

### JSON 직렬화 무한 재귀

엔티티를 직접 Response Body로 반환하면 양방향 참조 때문에 무한 직렬화 발생.

```java
// ❌ 엔티티 직접 반환
@GetMapping("/orders/{id}")
public Order getOrder(@PathVariable Long id) {
    return orderService.findById(id);  // 무한 재귀!
}

// ✅ DTO 변환 (권장)
@GetMapping("/orders/{id}")
public OrderResponse getOrder(@PathVariable Long id) {
    return OrderResponse.from(orderService.findById(id));
}
```

Jackson 어노테이션으로 해결도 가능하지만, DTO 변환이 더 명확하고 유지보수하기 좋다.

```java
// Jackson 방식 (DTO 없이 해결하고 싶을 때)
@JsonManagedReference   // 직렬화 O
@OneToMany(mappedBy = "order")
private List<OrderItem> orderItems;

@JsonBackReference      // 직렬화 X (역방향 차단)
@ManyToOne
private Order order;
```

---

### 실무 엔티티 표준 패턴

```java
@Entity
@Table(name = "contracts")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Contract extends BaseEntity {

    @ToString.Include
    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ToString.Include
    private BigDecimal monthlyRent;

    @ToString.Include
    private LocalDate startDate;

    // 연관관계는 자동 제외
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RentBill> rentBills = new ArrayList<>();
}
```

**체크리스트:**
- ✅ `@ToString(onlyExplicitlyIncluded = true)` — 연관관계 자동 제외
- ✅ `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` — ID만 비교
- ✅ 연관관계 필드는 `@ToString.Include` / `@EqualsAndHashCode.Include` 절대 붙이지 않음
- ✅ JSON 응답은 DTO 변환
- ✅ `fetch = FetchType.LAZY` 기본 유지

---

## 9. 실무 철칙 정리

| 상황 | 권장 방식 |
|------|----------|
| 조회 전용 API | DTO 프로젝션 |
| 수정/비즈니스 로직 | 엔티티 |
| 1:N 컬렉션 + 페이징 | ID 페이징 또는 @BatchSize |
| 단건 연관 조회 | Fetch Join / @EntityGraph |
| OSIV 설정 | `open-in-view: false` + Service 계층 중심 설계 |
| 연관관계 방향 | 단방향 우선, cascade 필요 시 양방향 추가 |
| 엔티티 Lombok | `@ToString(onlyExplicitlyIncluded = true)` + 연관관계 제외 |
| JSON 반환 | 엔티티 직접 반환 금지, DTO 변환 |

---

## 10. 실습 내용
1. `src/test/java/com/exam/jpa/repository/RepositoryTest.java`: JPA 기본 기능 테스트
2. `src/test/java/com/exam/jpa/repository/OrderRepositoryTest.java`: QueryDSL 및 Cascade 테스트
