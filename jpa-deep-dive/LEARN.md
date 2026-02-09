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

### 발생 원인
- `FetchType.LAZY`로 설정된 연관관계(`@ManyToOne` 등)를 조회할 때, 처음에는 **프록시 객체**만 가져옵니다.
- 이후 `member.getTeam().getName()`처럼 실제 데이터를 사용할 때 DB 조회가 발생합니다.

### 해결 방법: Fetch Join
- JPQL에서 `join fetch`를 사용하면, 연관된 엔티티를 SQL의 `JOIN`을 이용해 **한 번에** 가져옵니다.
- `select m from Member m join fetch m.team`

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
