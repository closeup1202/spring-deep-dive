# Flyway 실습 단계

각 단계는 **"뭘 해본다 → 뭘 관찰한다 → 왜 그런가"** 순서다.
단계마다 커밋을 남겨두면 나중에 되돌아가기 쉽다.

개념 정리와 에러 대응 인덱스는 [LEARN.md](LEARN.md) 에 있다. 이 문서는 손으로 굴리는 순서다.

---

## 0. 준비

`docker compose` 는 **이 모듈 디렉토리에서**, `gradlew` 는 **저장소 루트에서** 실행한다.

```bash
cd flyway-migration
docker compose up -d          # 호스트 5433 → 컨테이너 5432
cd ..
```

DB 접속:

```bash
docker exec -it flyway-migration-db psql -U flyway -d flyway_exam
```

앱 실행 (기본 프로필은 `local`):

```bash
./gradlew :flyway-migration:bootRun
curl localhost:8080/members
```

DB 를 처음부터 다시 만들고 싶을 때:

```bash
./gradlew :flyway-migration:flywayClean    # local 프로필에서만 허용 (clean-disabled: false)
```

---

## 1. 첫 마이그레이션 — 이력 테이블 들여다보기

**한다**: 그냥 앱을 띄운다.

**관찰한다**:

```sql
\d flyway_schema_history
select * from flyway_schema_history order by installed_rank;
```

**왜**: 이 테이블이 Flyway 의 전부다. 컬럼 하나씩 의미를 확인할 것.

| 컬럼 | 의미 |
|---|---|
| `installed_rank` | 실제 적용된 순서. **버전 순서와 다를 수 있다** (out-of-order) |
| `version` | `V1`, `V3_1` 의 버전 부분. Repeatable 은 `null` |
| `description` | 파일명의 `__` 뒤 부분. 언더스코어가 공백으로 바뀌어 저장된다 |
| `type` | `SQL` / `JDBC` / `BASELINE` |
| `checksum` | 파일 내용의 해시. **2단계의 주인공** |
| `success` | 실패한 마이그레이션도 행이 남는다 (6단계) |

로그에서 `spring.flyway.locations` 에 지정한 경로를 스캔하는 것,
그리고 `db/seed` 가 `local` 프로필에서만 잡히는 것도 함께 확인한다.

**콜백도 함께 확인**: 앱 로그(postgres 로그가 아니다)에 이런 줄이 있다.

```
o.f.c.i.c.SqlScriptCallbackFactory : Executing SQL callback: afterMigrate - report
o.f.c.i.s.DefaultSqlScriptExecutor : DB: [flyway-migration] afterMigrate: 6 applied, current version 4
```

`db/callback/afterMigrate__report.sql` 이 실행된 결과다. 파일명 앞부분(`afterMigrate`)이 곧 훅 이름이고,
`[flyway-migration]` 은 `spring.flyway.placeholders.app_name` 이 치환된 값이다.
`raise notice` 는 서버가 아니라 **클라이언트**로 가기 때문에 앱 로그에 `DB:` 접두어로 찍힌다.

---

## 2. checksum — 적용된 파일을 고치면 어떻게 되나

**한다**: 이미 적용된 `V2__create_member.sql` 에 주석 한 줄을 추가하고 재시작한다.

**관찰한다**: 부팅이 실패하고 `FlywayValidateException` 이 뜬다.

```
Migration checksum mismatch for migration version 2
-> Applied to database : 1234567890
-> Resolved locally    : 9876543210
```

**왜**: 이미 돌아간 마이그레이션은 다시 돌지 않는다. 그러니 파일을 고쳐봐야 DB 에 반영되지 않고,
반영되지 않은 채 "코드와 DB 가 다른" 상태가 된다. Flyway 는 그걸 checksum 으로 막는다.
**적용된 마이그레이션은 절대 수정하지 않는다** — 이게 Flyway 의 첫 번째 규칙이다.

**복구 방법 두 가지**:

1. 파일을 원래대로 되돌린다. (거의 항상 이게 정답)
2. 파일 변경이 의도된 것이었다면 → `./gradlew :flyway-migration:flywayRepair`
   → 이력 테이블의 checksum 을 현재 파일 기준으로 다시 계산해 덮어쓴다.
   **DB 스키마는 하나도 안 바뀐다.** repair 는 이력만 손보는 명령이다.

`spring.flyway.validate-on-migrate: false` 로 검사를 꺼볼 것.
부팅은 되지만 문제가 사라진 게 아니라 보이지 않게 된 것뿐이다.

---

## 3. ddl-auto=validate — 엔티티와 스키마 어긋내기

**한다**: `Member` 엔티티에 필드를 하나 추가한다. 마이그레이션은 쓰지 않는다.

```java
@Column(name = "phone", length = 20)
private String phone;
```

**관찰한다**:

```
Schema-validation: missing column [phone] in table [member]
```

**왜**: `ddl-auto: validate` 는 스키마를 만들지 않고 검증만 한다.
`update` 였다면 Hibernate 가 몰래 컬럼을 추가하고 넘어갔을 것이고,
그 변경은 어디에도 기록되지 않아 다른 환경에서 재현할 수 없다.
**Flyway 를 쓰기로 했으면 `ddl-auto` 는 `validate` 또는 `none` 이어야 한다.**

**해결**: `V5__add_member_phone.sql` 을 직접 작성한다.

```sql
alter table member add column phone varchar(20);
```

---

## 4. Repeatable 마이그레이션

**한다**: `R__member_summary_view.sql` 의 뷰에 컬럼을 하나 추가하고 재시작한다.

```sql
       min(m.created_at) as first_joined_at
```

**관찰한다**:
- 부팅이 실패하지 않는다. (versioned 였다면 2단계처럼 checksum 에러)
- `flyway_schema_history` 에 같은 description 의 행이 **하나 더** 쌓인다. `version` 은 `null`.

**왜**: Repeatable 은 checksum 이 바뀌면 그때마다 다시 실행된다.
그래서 `create or replace` 로 덮어쓸 수 있는 것 — 뷰, 함수, 프로시저, 시드 데이터 — 에만 쓴다.
`create table` 을 R__ 로 만들면 두 번째 실행에서 터진다.

**추가로 확인할 것**:
- Repeatable 은 **항상 versioned 가 전부 끝난 뒤** 실행된다.
- 여러 R__ 파일은 **파일명 알파벳 순**이다. 의존 관계가 있으면 `R__01_...`, `R__02_...` 처럼 접두어를 붙인다.
- `db/seed/R__dev_seed.sql` 이 왜 `on conflict do nothing` 인지 이제 이해될 것이다.
  이걸 빼고 재실행해서 실제로 중복이 나는 것도 한 번 보고 넘어갈 것.

---

## 5. out-of-order — 팀 협업에서 제일 자주 터지는 것

**한다**: 3단계에서 `V5` 를 만들어 적용한 상태에서, `lab/V4_1__hotfix_member_email_index.sql` 을
`src/main/resources/db/migration/` 으로 복사한다.

**관찰한다**: 부팅이 실패한다.

```
Detected resolved migration not applied to database: 4.1.
To ignore this migration, set -ignoreMigrationPatterns='*:ignored'.
To allow executing this migration, set -outOfOrder=true.
```

```bash
./gradlew :flyway-migration:flywayInfo
```

`4.1` 의 State 가 `Pending` 이 아니라 **`Ignored`** 다. 그리고 `Schema version` 은 여전히 `5` —
4.1 은 세어주지도 않는다.

표에 남아 있는 다른 State 도 같이 볼 것:
`Success` / `Ignored`(버전이 낮아 건너뜀) / `Superseded`(더 새 R__ 로 대체됨) / `Deleted`(repair 가 지움)

**왜**: Flyway 는 기본적으로 "현재 버전보다 낮은 번호"를 실행하지 않는다.
문제는 **환경마다 결과가 갈린다**는 것이다.

| | 4.1 적용 여부 |
|---|---|
| 4.1 을 만든 동료 로컬 (V4 상태였음) | 순서대로 적용됨 |
| 새로 세팅한 팀원 (빈 DB) | 1→2→3→4→**4.1**→5 로 전부 적용됨 |
| 이미 V5 까지 간 내 로컬 / 운영 | **Ignored** |

`validate-on-migrate: true`(기본값) 덕분에 부팅이 죽어서 드러나는 것이다.
validate 를 끄거나 `ignore-migration-patterns` 를 걸면 그때는 정말로 조용히 지나간다 — 그게 더 위험하다.

**해보기**: `spring.flyway.out-of-order: true` 로 바꾸고 재시작.
적용은 되지만 `version` 은 4.1 인데 `installed_rank` 는 **마지막**에 붙는다.
→ 새 팀원 DB 에서는 4.1 이 V5 앞에, 내 DB 에서는 V5 뒤에 적용됐다. **DB 마다 적용 순서가 다르다.**
지금은 인덱스 하나라 결과가 같지만, 4.1 이 컬럼을 추가하고 V5 가 그 컬럼을 참조했다면
새 팀원 DB 에서만 성공한다.

**그래서 실무에서는** `out-of-order` 를 끄고, 대신 팀 규칙으로 막는다:

1. **머지 전 버전 리베이스** — 4.1 을 6 으로 바꿔서 올린다 (제일 흔함)
2. **타임스탬프 버전** — `V20260826143000__hotfix.sql` 로 쓰면 번호가 뒤집힐 일이 거의 없다
3. **CI 체크** — "이 PR 의 최소 마이그레이션 버전 > main 의 최대 버전" 검사

`spring.flyway.ignore-migration-patterns` 도 찾아볼 것.
`*:ignored` 로 두면 부팅은 되지만 4.1 은 **영영 적용되지 않은 채** 지나간다 — 지금 상황에서 최악의 선택.

---

## 6. 실패하는 마이그레이션

**한다**: `lab/V21__failing_migration.sql` 을 `db/migration/` 으로 복사하고 앱을 띄운다.

V21 은 한 파일 안에 **성공할 문장(`create table lab_broken`)과 실패할 문장**을 같이 담고 있다.
앞 문장의 결과가 남는지가 관전 포인트다.

**관찰한다**:

```sql
\dt                          -- lab_broken 테이블이 생겼는가?
select installed_rank, version, script, success
from flyway_schema_history order by installed_rank desc limit 3;
```

PostgreSQL 에서는 **둘 다 아무것도 안 남는다.** `lab_broken` 도 없고,
이력에는 `success = false` 행조차 생기지 않는다. V21 을 실행한 적이 없는 것처럼 보인다.

**왜**: Flyway 는 마이그레이션 파일 하나를 트랜잭션 하나로 실행한다.
PostgreSQL 은 **DDL 도 트랜잭션 안에서 돌기 때문에** 파일이 통째로 롤백되고,
실패를 기록하는 insert 까지 같이 롤백된다.

| | 실패 시 앞 문장 | 실패 기록 | 복구 |
|---|---|---|---|
| **PostgreSQL** | 롤백됨 | 행이 안 남음 | SQL 고치고 재실행. 끝 |
| **MySQL / Oracle** | **그대로 남음** | `success=false` 행이 남음 | `repair` → 스키마 수동 정리 → 재실행 |

MySQL 이었다면 `lab_broken` 이 생긴 채로 남고 이력엔 실패 행이 박힌다.
재실행하면 `lab_broken already exists` 로 또 터진다 — 스키마가 반쯤 적용된 상태를 손으로 풀어야 한다.
→ **MySQL 을 쓴다면 마이그레이션 파일 하나에 DDL 하나**가 안전한 규칙이다.

**복구**: `V21__failing_migration.sql` 을 지우고 재시작하면 끝이다.
PostgreSQL 에서는 `flywayRepair` 가 **필요 없다** — 지울 실패 이력이 애초에 없다.
(repair 가 필요한 건 위 표의 오른쪽 열, 실패 행이 남는 DB 다)

**곁다리 — `CREATE INDEX CONCURRENTLY` 는 Flyway 로 돌리기 까다롭다**

트랜잭션 안에서 실행할 수 없어서 `V22__x.sql.conf` 에 `executeInTransaction=false` 를 줘야 하는데,
그래도 걸린다. Flyway 가 락 용도로 잡아둔 커넥션이 `idle in transaction` 으로 남아 있어
`CONCURRENTLY` 가 무한정 기다리기 때문이다.

```
 36943 | idle in transaction | SELECT COUNT(*) FROM pg_namespace WHERE nspname=$1
 36944 | active              | create index concurrently idx_member_phone on member (phone)
```

운영 테이블에 무중단으로 인덱스를 걸어야 하는 바로 그 상황에서 막힌다는 뜻이다.
실무에서는 그런 인덱스를 마이그레이션 밖에서 따로 돌리거나 전용 도구를 쓴다.

---

## 7. Java 마이그레이션

**한다**: **`application-local.yml`** 의 `locations` 에 Java 마이그레이션 패키지를 추가하고 재시작한다.

```yaml
locations: classpath:db/migration,classpath:db/callback,classpath:db/seed,classpath:com/exam/flyway/migration/lab
```

> `application.yml` 이 아니라 `application-local.yml` 이다.
> 기본 프로필이 `local` 이고 프로필 파일이 `locations` 를 **통째로 덮어쓰기** 때문에
> `application.yml` 쪽을 고치면 아무 일도 일어나지 않는다.
>
> Java 마이그레이션의 location 은 **패키지 경로**다. 파일 경로가 아니다.
> `com.exam.flyway.migration.lab` → `classpath:com/exam/flyway/migration/lab`

`src/main/java/com/exam/flyway/migration/lab/V20__BackfillMemberGrade.java` 가 실행된다.

**관찰한다**:

```sql
select id, name, point, grade from member order by id;
select version, description, type, checksum from flyway_schema_history where type = 'JDBC';
```

**왜**:
- 등급 정책이 `MemberGrade` enum 에 있다. SQL 로 `case when` 을 다시 짜면 정책이 두 곳에 중복된다.
  암호화/해싱, 파싱, 외부 시스템 조회 같은 백필도 마찬가지 이유로 Java 를 쓴다.
- `type` 이 `SQL` 이 아니라 `JDBC` 로 기록된다.
- **`checksum` 을 직접 관리해야 한다.** Java 마이그레이션은 기본 checksum 이 `null` 이라
  클래스 내용을 고쳐도 Flyway 가 알아채지 못한다. `getChecksum()` 을 오버라이드한 이유다.
- Java 마이그레이션은 **Spring 빈이 아니다.** `@Autowired` 로 리포지토리를 받을 수 없고,
  `context.getConnection()` 을 그대로 써야 한다. (직접 close 하면 안 된다)

- description 은 파일명이 아니라 **클래스명**에서 뽑는다 (`V20__BackfillMemberGrade` → `BackfillMemberGrade`).

**해볼 것**: `getChecksum()` 반환값을 `2` 로 바꾸고 재시작 → 2단계와 같은 checksum mismatch.
확인했으면 `1` 로 되돌린다.

**트레이드오프**: Java 마이그레이션은 `./gradlew :flyway-migration:flywayInfo` 같은 CLI 태스크에 **안 보인다.**
컴파일된 클래스가 CLI classpath 에 없기 때문이다.
DBA 가 SQL 파일만 보고 리뷰하는 조직이라면 이것도 고려 대상이다.
→ **SQL 로 표현할 수 있으면 SQL 로 하는 게 기본이고, Java 는 정말 필요할 때만 쓴다.**

---

## 8. baseline — 이미 돌아가는 DB 에 Flyway 붙이기

**상황**: 운영 DB 는 이미 몇 년째 수동 DDL 로 굴러가고 있다. 여기에 Flyway 를 도입한다.

**한다**: 새 DB 를 하나 만들어 "기존 DB" 를 흉내 낸다.

```sql
create database legacy_db;
\c legacy_db
-- V1, V2 의 내용을 손으로 실행해서 이미 테이블이 있는 상태를 만든다
create table team (id bigserial primary key, name varchar(50) not null, created_at timestamp not null default now());
create table member (id bigserial primary key, team_id bigint references team(id), name varchar(50) not null, created_at timestamp not null default now());
```

`spring.datasource.url` 을 `legacy_db` 로 바꾸고 앱을 띄운다.

**관찰한다**: `Found non-empty schema(s) "public" but no schema history table` → 부팅 실패.

**해결**:

```yaml
spring:
  flyway:
    baseline-on-migrate: true
    baseline-version: 2          # "V2 까지는 이미 적용된 것으로 친다"
    baseline-description: legacy schema
```

**왜**: Flyway 는 빈 스키마에서 시작하는 걸 전제한다.
`baseline` 은 "여기부터가 시작점"이라고 선언하는 것이고,
`baseline-version` 이하의 마이그레이션은 **실행하지 않고 건너뛴다.**
따라서 이 값을 잘못 잡으면 V1, V2 가 영영 적용되지 않는다.
→ 실무에서는 현재 운영 스키마를 덤프해 `V1__baseline.sql` 로 만들어두고
새 환경에서는 그게 실행되게, 기존 환경에서는 baseline 으로 건너뛰게 맞추는 방식을 많이 쓴다.

---

## 9. 무중단 스키마 변경 (expand-contract)

**한다**: `lab/V30`, `V31`, `V32` 를 **한 번에 하나씩** 복사해 적용한다.

**왜 세 번인가**: `alter table member rename column name to display_name;` 을 한 번에 실행하면,
마이그레이션이 끝난 그 순간부터 **아직 배포되지 않은 구버전 인스턴스가 전부 죽는다.**
롤링 배포 중에는 신/구 버전이 동시에 떠 있다.

| 배포 | 마이그레이션 | 애플리케이션 코드 |
|---|---|---|
| 1 | V30: `display_name` nullable 추가 | 두 컬럼에 모두 쓰기(dual write) |
| 2 | V31: 백필 + not null | 읽기는 `display_name`, 쓰기는 아직 둘 다 |
| 3 | V32: `name` 삭제 | `name` 참조 완전 제거, 엔티티 필드도 삭제 |

**규칙**: 마이그레이션은 **항상 이전 버전 코드와 호환**되어야 한다.
컬럼 삭제/rename/타입 축소/not null 추가는 전부 이 패턴으로 쪼갠다.

V32 를 적용하면 `Member.name` 필드 때문에 부팅이 실패한다. 3단계의 반대 방향 — 이것도 `validate` 덕분이다.

---

## 10. 롤백은 어떻게 하나

**결론부터**: Flyway Community 에는 `undo` 가 없다 (Teams/Enterprise 유료 기능).

**실무의 답은 forward-fix** — 되돌리는 마이그레이션을 새로 쓴다.

```sql
-- V33__revert_display_name.sql
alter table member add column name varchar(50);
update member set name = display_name;
```

그래서 애초에 **되돌릴 일이 없게** 설계하는 게 중요하다:
- 데이터를 지우는 마이그레이션은 최대한 미룬다 (9단계의 contract 단계)
- `drop column` 전에 이름만 바꿔 유예 기간을 두기도 한다 (`name_deprecated_20260826`)
- 배포 전 반드시 스테이징에서 **운영 데이터 복제본**에 실행해본다

---

## 11. CI 안전장치

```bash
./gradlew :flyway-migration:test
```

- `MigrationSmokeTest` — Testcontainers 로 **완전히 빈 DB** 에 V1 부터 전부 재생한다.
  로컬 DB 에서는 이미 적용된 마이그레이션이 다시 안 돌기 때문에, 새로 짠 SQL 이 실제로 실행 가능한지는
  이 테스트만이 보장한다. 컨텍스트가 뜨는 것 자체가 `ddl-auto=validate` 통과를 의미한다.
- `MigrationFileConventionTest` — 파일명 규칙, 버전 중복, 콜백 이름 오타를 잡는다.
  (오타 난 콜백 파일은 **에러 없이 그냥 무시된다** — 그래서 테스트로 잡아야 한다)

### 실제로 이 테스트가 잡은 버그 (9~10단계를 하면 반드시 재현된다)

9단계에서 V31 이 `display_name` 에 not null 을 걸었는데,
`db/seed/R__dev_seed.sql` 의 insert 에는 그 컬럼이 없었다.

```
ERROR: null value in column "display_name" of relation "member" violates not-null constraint
```

**로컬 DB 에서는 절대 안 보이는 버그다.** 시드는 4단계 때 이미 적용됐고 그 뒤로 파일이
안 바뀌었으니 재실행되지 않기 때문이다. 앱을 몇 번을 재시작해도, `flywayInfo` 도 `flywayValidate` 도
전부 통과한다. **빈 DB 에 처음부터 재생해야만 드러난다.**

| | 결과 |
|---|---|
| 내 로컬 (시드 이미 적용됨) | 멀쩡 |
| 빈 DB — 신규 팀원 / CI / 운영 | V1~V33 → not null → **그다음** 시드 실행 → 실패 |

그리고 `MigrationSmokeTest`(prod, seed 없음)는 통과하고
`FlywayApplicationTests`(local, seed 포함)만 실패했다.
→ 운영 배포는 무사하고 **개발 환경 세팅만 깨지는** 버그였다.
프로필을 하나만 테스트했으면 새 팀원이 합류하는 날 발견됐을 것이다.

**CI 에 추가하면 좋은 것**:

```bash
./gradlew :flyway-migration:flywayValidate   # 운영 DB 를 향해 실행 → 배포 전에 checksum 불일치 감지
./gradlew :flyway-migration:flywayInfo       # 이번 배포에서 무엇이 적용될지 로그로 남기기
```

---

## 자주 쓰는 Gradle 태스크

| 명령 | 설명 |
|---|---|
| `./gradlew :flyway-migration:flywayInfo` | 적용/대기/무시 상태 표로 출력. **제일 자주 쓴다** |
| `./gradlew :flyway-migration:flywayValidate` | checksum·누락 검사만 (변경 없음) |
| `./gradlew :flyway-migration:flywayMigrate` | 앱 없이 마이그레이션만 실행 |
| `./gradlew :flyway-migration:flywayRepair` | 실패 이력 제거 + checksum 재계산 (**스키마는 안 건드림**) |
| `./gradlew :flyway-migration:flywayBaseline` | 현재 상태를 시작점으로 표시 |
| `./gradlew :flyway-migration:flywayClean` | 스키마 전체 삭제. **운영 금지** |

> Gradle 플러그인 설정(`build.gradle` 의 `flyway { }`)과 Spring 설정(`application.yml` 의 `spring.flyway`)은
> **완전히 별개**다. 두 곳의 `locations` 가 어긋나면 CLI 와 앱이 서로 다른 결과를 내놓는다.
> 실무에서는 한쪽만 쓰거나, 값을 한 곳에서 읽도록 묶는다.

---

## 파일 규칙 요약

```
V1__create_team.sql       versioned, 한 번만 실행, 수정 금지
V1_1__hotfix.sql          점 대신 언더스코어도 가능 (1.1 과 동일)
R__member_summary.sql     repeatable, 내용 바뀌면 재실행, 항상 마지막
afterMigrate__report.sql  콜백, 파일명 앞부분이 훅 이름
```

- 구분자는 언더스코어 **두 개**다. 하나면 그냥 무시된다.
- 버전 번호는 `1`, `1.1`, `20260826120000` 처럼 아무 형식이나 가능하다.
  팀 규칙으로 **타임스탬프**를 쓰면 5단계의 충돌이 거의 사라진다.
