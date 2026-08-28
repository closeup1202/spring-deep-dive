# Flyway 마이그레이션 실무 가이드

DB 스키마를 코드처럼 버전 관리하는 방법을 학습합니다.

이 모듈은 개념 설명보다 **직접 터뜨려 보는 것**에 무게를 둡니다.
[STEPS.md](STEPS.md) 의 11단계를 따라가면 부팅이 죽고, 이력이 꼬이고, 테스트가 실패합니다.
이 문서는 그 과정에서 알게 되는 것들을 정리한 참조 문서입니다.

**환경**: Spring Boot 3.4.1 · Flyway 10.20.1 · PostgreSQL 16 · Testcontainers 1.21.4

---

## 1. 언제 Flyway를 쓰는가?

### 1.1 `ddl-auto`로는 안 되는 이유

JPA만 쓰면 `spring.jpa.hibernate.ddl-auto: update`로 스키마가 자동으로 따라옵니다. 편하지만 운영에서는 못 씁니다.

* **기록이 남지 않습니다.** 언제 어떤 컬럼이 왜 생겼는지 아무 데도 없습니다. 리뷰도, 롤백도 불가능합니다.
* **삭제와 타입 변경을 안 해줍니다.** `update`는 추가만 합니다. 시간이 지나면 환경마다 스키마가 조금씩 달라집니다.
* **배포 시점에 자동 실행됩니다.** 스키마 변경이 사람의 승인 없이 나갑니다.

Flyway는 스키마 변경을 **번호가 붙은 SQL 파일**로 만들고, 어떤 파일이 언제 적용됐는지를 DB 안의 이력 테이블에 기록합니다. 그래서 코드 리뷰가 되고, 모든 환경이 같은 순서로 같은 스키마에 도달합니다.

### 1.2 이 모듈의 핵심 장치

```yaml
spring.jpa.hibernate.ddl-auto: validate
```

Hibernate가 스키마를 **만들지 않고 검증만** 합니다.
엔티티를 고치고 마이그레이션을 안 쓰면 부팅이 실패합니다.

> **Flyway를 쓰기로 했으면 `ddl-auto`는 반드시 `validate` 또는 `none`이어야 합니다.**
> 둘을 같이 켜면 스키마 관리 주체가 둘이 되고, 그 순간 Flyway의 이력은 거짓말이 됩니다.

한 가지 한계는 알아두세요. `validate`는 **컬럼 존재와 타입만** 검사합니다.
not null·유니크·FK 같은 제약은 보지 않습니다.

### 1.3 대형 서비스에서는 어떻게 쓰나

JVM 진영에서 Flyway는 사실상 기본값에 가깝지만, 규모가 커지면 **쓰는 방식**이 달라집니다.

```yaml
spring.flyway.enabled: false   # 앱은 마이그레이션을 실행하지 않는다
```

대신 배포 파이프라인의 **별도 단계**로 돌립니다. CI 잡, Kubernetes Job/initContainer, 또는 배포 전 승인 단계.

* **인스턴스 수십 대가 동시에 뜹니다.** Flyway가 DB 락으로 직렬화하지만, 나머지는 락을 기다리다 헬스체크 타임아웃에 걸립니다.
* **마이그레이션이 길면 배포가 멈춥니다.** 5분짜리 백필이 있으면 첫 인스턴스가 5분간 안 뜨고 오토스케일링·롤백이 다 막힙니다.
* **앱에 DDL 권한을 주고 싶지 않습니다.** 런타임 계정은 DML만, 마이그레이션 계정은 DDL까지 분리해야 합니다.
* **블루/그린·카나리는 순서를 직접 통제해야 합니다.** 부팅 시 실행은 그 통제권을 뺏어갑니다.

무거운 DDL은 Flyway가 실행 도구로 적합하지 않습니다 (6단계에서 직접 겪습니다). 역할을 나눕니다.

| 역할 | 도구 |
|---|---|
| 버전 이력 관리 | Flyway, Liquibase |
| 무거운 DDL 실행 | MySQL `gh-ost`·`pt-online-schema-change` / PostgreSQL `pg_repack`·`pgroll` |

대안으로는 **Liquibase**(rollback 무료 지원, DB 비종속 changelog), **Atlas**(선언형, Terraform 스타일),
**Bytebase**(리뷰·승인 워크플로 포함)가 있습니다.

---

## 2. 핵심 개념

### 2.1 `flyway_schema_history`

Flyway의 전부입니다. 이 테이블이 있으면 Flyway가 관리하는 DB이고, 없으면 아닙니다.
문제가 생겼을 때 제일 먼저 여는 곳입니다.

| 컬럼 | 의미 | 함정 |
|---|---|---|
| `installed_rank` | 실제 적용된 순서 | **버전 순서가 아닙니다.** out-of-order를 켜면 어긋납니다 |
| `version` | 파일명의 버전 부분 | Repeatable은 `NULL`. varchar라서 문자열로 비교됩니다 |
| `description` | 파일명 `__` 뒤 | 언더스코어가 공백으로 바뀌어 저장됩니다. Java는 클래스명에서 뽑습니다 |
| `type` | 종류 | `SQL` / `JDBC`(Java) / `BASELINE` / `DELETE`(repair가 남김) |
| `checksum` | 파일 내용의 해시 | Java 마이그레이션은 기본 `null`. 직접 관리해야 합니다 |
| `success` | 성공 여부 | PostgreSQL에서는 실패해도 **행 자체가 안 남습니다** |

**이력은 덮어쓰지 않고 쌓입니다.** repair로 지우고 다시 적용하면 `DELETE` 행과 재적용 행이 둘 다 남습니다.
Repeatable을 세 번 고치면 세 줄이 쌓입니다.

`flywayInfo`가 보여주는 State는 다섯 가지입니다.

| State | 뜻 | 대응 |
|---|---|---|
| `Success` | 정상 적용 | — |
| `Pending` | 적용 대기 | 다음 마이그레이션에서 실행됨 |
| `Ignored` | 버전이 낮아 건너뜀 | 버전 리베이스 또는 out-of-order |
| `Superseded` | 더 새 R__로 대체됨 | 정상. Repeatable 재실행의 흔적 |
| `Deleted` | repair가 지움 | 다음 실행에서 재적용됨 |

### 2.2 파일 규칙

```
V1__create_team.sql        versioned — 한 번만 실행, 수정 금지
V1_1__hotfix.sql           1.1 과 동일 (점 대신 언더스코어)
R__member_summary.sql      repeatable — 내용 바뀌면 재실행, 항상 마지막
afterMigrate__report.sql   콜백 — 파일명 앞부분이 훅 이름
```

* 구분자는 언더스코어 **두 개**입니다. 하나면(`V6_add_x.sql`) Flyway가 **아무 에러 없이 무시**합니다.
* 콜백 이름 오타(`afterMigration__x.sql`)도 마찬가지로 조용히 무시됩니다.
* 버전 번호 형식은 자유롭습니다. `1`, `1.1`, `20260828143000`.
  팀 규칙으로 **타임스탬프**를 쓰면 병합 시 번호가 겹치거나 뒤집히는 문제가 거의 사라집니다.
* Java 마이그레이션의 location은 **패키지 경로**입니다. 파일 경로가 아닙니다.
  `com.exam.flyway.migration.lab` → `classpath:com/exam/flyway/migration/lab`

"조용히 무시된다"가 제일 위험합니다. 그래서 `MigrationFileConventionTest`로 CI에서 잡습니다.

### 2.3 Versioned vs Repeatable

규칙이 정반대입니다.

| | Versioned `V1__` | Repeatable `R__` |
|---|---|---|
| 파일 수정 | **금지** — checksum mismatch로 부팅 실패 | **권장** — 고치면 다시 적용됨 |
| 실행 횟수 | 딱 한 번 | 내용이 바뀔 때마다 |
| 실행 시점 | 버전 순서 | versioned가 전부 끝난 뒤 |
| 여러 개일 때 | 버전 번호 순 | **파일명 알파벳 순** |
| `version` 컬럼 | 있음 | `NULL` |
| 쓰는 곳 | 테이블·컬럼·인덱스·백필 | 뷰·함수·프로시저·시드 |

> **Repeatable의 재실행 조건은 "새 versioned가 생겼을 때"가 아니라 "자기 checksum이 바뀌었을 때"입니다.**
> 순서상 versioned 뒤에 오는 것과, 매번 다시 도는 것은 별개입니다.

그래서 R__은 **몇 번을 실행해도 같은 결과**가 나와야 합니다.
`create or replace`, `on conflict do nothing`, `if not exists`.
`create table`을 R__로 만들면 두 번째 실행에서 터집니다.

R__ 파일끼리 의존 관계가 있으면 `R__01_xxx.sql`, `R__02_xxx.sql`처럼 접두어로 순서를 강제해야 합니다.

---

## 3. 설정 (`application.yml`)

실습에서 실제로 켜고 꺼 보는 것들입니다.

| 설정 | 기본 | 끄거나 잘못 잡으면 |
|---|---|---|
| `ddl-auto` | `validate` | `update`면 Hibernate가 몰래 스키마를 바꾸고 기록을 안 남깁니다 |
| `validate-on-migrate` | `true` | checksum 불일치를 못 잡습니다. 문제가 사라진 게 아니라 안 보이게 됩니다 |
| `out-of-order` | `false` | `true`면 DB마다 적용 순서가 달라집니다 |
| `baseline-on-migrate` | 도입 시 한 번만 | 켜둔 채로 이력 테이블을 잃으면 조용히 baseline을 다시 만들고 넘어갑니다 |
| `baseline-version` | — | 낮으면 `already exists`, 높으면 마이그레이션이 영영 건너뛰어집니다 |
| `clean-disabled` | `true` | 운영에서 `false`는 스키마 전체 삭제 버튼을 열어두는 것입니다 |
| `ignore-migration-patterns` | 비움 | `*:ignored`는 부팅은 시키지만 그 마이그레이션은 **영영 적용되지 않습니다** |
| `locations` | — | 프로필 파일이 **통째로 덮어씁니다** |

### 3.1 프로필 분리

| 프로필 | locations | clean | 용도 |
|---|---|---|---|
| `local` (기본) | migration + callback + **seed** | 허용 | 실습 |
| `prod` | migration + callback | 금지 | 운영 흉내 / 스모크 테스트 |

`application-local.yml`이 `locations`를 **통째로 덮어씁니다.**
그래서 7단계에서 Java 마이그레이션 경로를 추가할 때 `application.yml`을 고치면 아무 일도 일어나지 않습니다.

### 3.2 설정을 두 곳에 두지 마세요

`build.gradle`의 `flyway { }` 블록과 `application.yml`의 `spring.flyway`는 **완전히 별개**입니다.
`locations`가 어긋나면 같은 DB를 두고 CLI와 앱이 서로 다른 판단을 합니다.

```
Detected applied migration not resolved locally: dev seed.
→ flywayRepair 가 이력에 DELETE 를 박아버립니다
```

앱은 `db/seed`를 적용했는데 CLI는 그 경로를 몰랐기 때문입니다.
이 모듈의 `build.gradle`은 `-PflywayProfile=prod`로 양쪽을 맞출 수 있게 해뒀습니다.

---

## 4. 실습 시나리오

[STEPS.md](STEPS.md)의 11단계입니다. 각 단계가 무엇을 드러내는지만 정리합니다.

| 단계 | 해보는 것 | 알게 되는 것 |
|---|---|---|
| 1 | 앱 부팅 후 이력 테이블 관찰 | 스키마는 마이그레이션의 **합**이다 |
| 2 | 적용된 파일에 주석 추가 | checksum mismatch. `repair`는 **이력만** 고친다 |
| 3 | 엔티티에 필드만 추가 | `ddl-auto: validate`가 마이그레이션을 강제한다 |
| 4 | R__ 뷰 파일 수정 | Repeatable은 자기 checksum이 바뀔 때만 재실행된다 |
| 5 | V5 뒤에 V4_1 머지 | `Ignored`. 환경마다 스키마가 갈린다 |
| 6 | 실패하는 SQL 실행 | PostgreSQL은 DDL도 트랜잭션. 이력에 행이 안 남는다 |
| 7 | Java 마이그레이션 활성화 | checksum을 **손으로** 관리해야 한다 |
| 8 | 기존 DB에 Flyway 붙이기 | `baseline-version`이 진짜 일이다 |
| 9 | 컬럼 rename을 3번에 나눠 배포 | 마이그레이션은 이전 버전 코드와 호환돼야 한다 |
| 10 | `undo` 시도 | OSS에는 없다. forward-fix뿐이다 |
| 11 | `./gradlew :flyway-migration:test` | **로컬에서 안 보이는 버그**를 빈 DB 재생만이 잡는다 |

시작 상태는 `db/migration`에 **V1~V4 + R__**만 들어 있습니다.
V5는 3단계에서, V33은 10단계에서 직접 작성합니다.
나머지 실습 파일은 [lab/](lab/)에 있고, 해당 단계에서 복사해 넣습니다.

---

## 5. 자주 밟는 함정

11단계에서 실제로 만나는 것들만 추렸습니다.

### 5.1 적용된 파일을 수정했다

```
Migration checksum mismatch for migration version 2
-> Applied to database : -522029646
-> Resolved locally    : -1066329717
```

이미 적용된 마이그레이션은 다시 실행되지 않습니다. 파일을 고쳐도 DB는 안 바뀌고,
**코드와 DB가 조용히 어긋납니다.** 99%는 파일을 되돌리는 게 정답입니다.

`repair`는 **이력만** 고칩니다. 스키마는 하나도 안 바뀝니다.
`varchar(50)`을 `varchar(100)`으로 고치고 repair하면
*컬럼은 50인데 이력만 100인 척* 하는 최악의 상태가 됩니다.

### 5.2 out-of-order — 팀 협업에서 제일 자주 터집니다

`main`이 V5로 나간 사이 동료가 V4 브랜치에서 V4_1을 만들어 머지하면:

| | 4.1 적용 여부 |
|---|---|
| 4.1을 만든 동료 로컬 (V4 상태였음) | 순서대로 적용됨 |
| 새로 세팅한 팀원 (빈 DB) | 1→2→3→4→**4.1**→5로 전부 적용됨 |
| 이미 V5까지 간 내 로컬 / 운영 | **Ignored** |

`validate-on-migrate: true` 덕분에 부팅이 죽어서 드러납니다.
validate를 끄거나 `ignore-migration-patterns`를 걸면 정말로 조용히 지나갑니다. 그게 더 위험합니다.

막는 방법은 설정이 아니라 팀 규칙입니다. 머지 전 버전 리베이스, 타임스탬프 버전,
CI에서 "PR 최소 버전 > main 최대 버전" 검사.

### 5.3 baseline은 반쪽만 켜면 실패합니다

```
Found non-empty schema(s) "public" but no schema history table.

# baseline-on-migrate 만 켰더니
ERROR: relation "team" already exists
```

`baseline-version`이 `0`이라 V1부터 실행하려 든 것입니다.
낮으면 `already exists`, 높으면 마이그레이션이 영영 건너뛰어집니다.

그리고 baseline을 쓰면 기존 DB는 V1·V2를 실행한 적이 없고 새 DB는 실행합니다.
두 스키마가 같다는 보장이 없습니다.
→ 운영 스키마를 `pg_dump --schema-only`로 떠서 `V1__baseline.sql`로 만들면 양쪽이 수렴합니다.

### 5.4 Java 마이그레이션은 checksum이 `null`입니다

`getChecksum()`을 오버라이드하지 않으면 **클래스 로직을 고쳐도 Flyway가 눈치채지 못합니다.**
2단계의 보호 장치가 통째로 사라집니다.

Spring 빈도 아닙니다. Flyway는 컨텍스트가 뜨기 전에 실행되므로 `@Autowired`를 못 받고,
`context.getConnection()`을 써야 하며 **직접 close하면 안 됩니다.**

그리고 `flywayInfo` 같은 CLI 태스크에 **안 보입니다.**
→ SQL로 표현할 수 있으면 SQL로 하는 게 기본이고, Java는 정말 필요할 때만 씁니다.

### 5.5 `CREATE INDEX CONCURRENTLY`는 Flyway로 돌리기 까다롭습니다

트랜잭션 안에서 실행할 수 없어서 `V22__x.sql.conf`에 `executeInTransaction=false`를 줘야 하는데,
그래도 걸립니다. Flyway가 락 용도로 잡아둔 커넥션이 `idle in transaction`으로 남아 무한정 기다립니다.

```
36943 | idle in transaction | SELECT COUNT(*) FROM pg_namespace WHERE nspname=$1
36944 | active              | create index concurrently idx_member_phone on member (phone)
```

운영 테이블에 무중단으로 인덱스를 걸어야 하는 바로 그 상황에서 막힙니다.

---

## 6. DB별 트랜잭션 차이

6단계가 다른 DB였다면 완전히 다르게 끝납니다.

| | PostgreSQL | MySQL · Oracle |
|---|---|---|
| DDL 트랜잭션 | 지원 | 미지원 |
| 실패 시 앞 문장 | 롤백됨 | **그대로 남음** |
| 이력 기록 | 행이 안 남음 | `success = false` 행이 남음 |
| 복구 | SQL 고치고 재실행. 끝 | repair → 스키마 수동 정리 → 재실행 |
| 권장 규칙 | — | **파일 하나에 DDL 하나** |

MySQL에서는 실패한 마이그레이션이 스키마를 반쯤 적용된 상태로 남깁니다.
재실행하면 `already exists`로 또 터지고, 그 상태를 손으로 풀어야 합니다.

---

## 7. 무중단 스키마 변경 (expand-contract)

`alter table member rename column name to display_name;`을 한 번에 배포하면,
마이그레이션이 끝난 그 순간부터 **아직 배포되지 않은 구버전 인스턴스가 전부 죽습니다.**
롤링 배포 중에는 신·구 버전이 반드시 동시에 떠 있고,
마이그레이션은 그중 **첫 번째 신버전 인스턴스**가 실행합니다.

> **원칙: 모든 마이그레이션은 이전 버전 코드와 호환되어야 합니다.**

| 배포 | 마이그레이션 | 애플리케이션 | 롤백 |
|---|---|---|---|
| **1** expand | `display_name` nullable 추가 | 두 컬럼에 모두 쓰기 | 코드만 되돌리면 끝 |
| **2** migrate | 백필 → not null | 읽기 전환, 쓰기는 둘 다 | 코드만 되돌리면 끝 |
| **3** contract | `name` 삭제 | `name` 참조 완전 제거 | **불가 — forward-fix만** |

배포 1·2는 DB를 건드리지 않고 코드만 되돌리면 됩니다. 배포 3만 위험합니다.
그래서 실무에서 contract를 **며칠~몇 주 미룹니다.**

### 같은 패턴이 적용되는 변경들

| 변경 | 왜 위험한가 | 어떻게 쪼개나 |
|---|---|---|
| 컬럼 rename | 구버전이 옛 이름을 찾음 | 추가 → 백필 → 삭제 |
| 컬럼 삭제 | 구버전이 그 컬럼에 씀 | 코드에서 제거 → 유예 → 삭제 |
| `not null` 추가 | 구버전 insert가 값을 안 넣음 | 컬럼 추가 → dual write → 백필 → 제약 |
| 타입 축소 | 기존 데이터가 안 들어감 | 새 컬럼 → 검증·백필 → 교체 |
| 테이블 분리 | 구버전이 옛 테이블을 읽음 | 새 테이블 + 동기화 → 전환 → 제거 |

### 함정 둘

* **큰 테이블의 백필은 마이그레이션 밖으로 뺍니다.** `update` 한 줄이 테이블 전체를 잠그고,
  그동안 배포도 쓰기도 멈춥니다. 배치로 나눠 돌리고 마이그레이션에는 제약만 남깁니다.
* **`drop column`은 의존하는 뷰가 있으면 실패합니다.** R__은 versioned 뒤에 실행되므로
  "뷰를 먼저 고치고 컬럼을 지우는" 순서를 R__으로는 만들 수 없습니다.
  같은 versioned 안에서 `drop view` → `drop column`을 하고 R__이 다시 만들게 합니다.

---

## 8. 롤백 전략

```
Flyway Redgate Edition Required: undo is not supported by OSS Edition
```

Community에는 `undo`가 없습니다. 유료판을 써도 `U32__...sql`을 손으로 써둬야 하고, 자동 역산은 없습니다.

실무의 답은 **forward-fix** — 되돌리는 마이그레이션을 새 버전으로 추가합니다.

다만 이건 진짜 롤백이 아닙니다. **스키마 변경은 되돌릴 수 있어도 데이터 삭제는 되돌릴 수 없습니다.**
사본 없는 컬럼을 지웠다면 값은 영영 못 돌아옵니다.

그래서 되돌릴 일이 없게 설계하는 쪽이 전략입니다.

* **`drop` 대신 rename으로 유예 기간을 둡니다.** `zz_deprecated_name_20260828`처럼.
  컬럼이 남아 있으니 rename 한 번으로 되돌아가고, 접두어 덕분에 "곧 지울 것"이 눈에 보입니다.
* **`drop`이 포함된 배포 직전에 PITR 복원 지점을 잡습니다.**
* **스테이징에서 운영 데이터 복제본으로 리허설합니다.** 마이그레이션 실패는 대부분 데이터 때문입니다.
  중복 때문에 유니크 인덱스가 안 걸리고, 예상 못 한 null 때문에 not null이 안 붙습니다.
* **삭제를 미루는 게 기본입니다.** 저장 비용은 싸고 복구 불가능한 실수는 비쌉니다.

---

## 9. CI 안전장치

11단계에서 실제로 버그가 잡힙니다. 앞선 열 단계를 모두 정상 통과하고,
앱도 잘 뜨고 `flywayValidate`도 통과하는 상태에서요.

```
ERROR: null value in column "display_name" of relation "member" violates not-null constraint
```

시드 파일의 insert에 `display_name`이 없는데 V31이 not null을 걸었기 때문입니다.
**로컬에서는 절대 안 보입니다.** 시드는 이미 적용됐고 파일이 안 바뀌었으니 재실행되지 않으니까요.

### 테스트 두 종류

* **`MigrationSmokeTest`** — Testcontainers로 빈 DB에 V1부터 전부 재생합니다.
  새 SQL이 실제로 실행 가능한지, 누적 결과가 엔티티와 맞는지, 순서 의존성이 깨지지 않았는지를 봅니다.
  로컬 DB에서는 이미 적용된 것이 다시 안 돌기 때문에 **이 테스트만이 보장합니다.**
* **`MigrationFileConventionTest`** — DB 없이 도는 순수 단위 테스트.
  언더스코어 하나, 버전 중복, 콜백 이름 오타를 잡습니다. 전부 "조용히 무시되는" 부류입니다.

`local`과 `prod` 프로필을 **둘 다** 돌려야 합니다.
11단계 버그는 `prod`는 통과하고 `local`만 실패했습니다 —
운영은 무사하고 **개발 환경 세팅만 깨지는** 버그였습니다.

### 파이프라인

```bash
./gradlew :flyway-migration:test            # 빈 DB 재생 + 파일명 규칙
./gradlew :flyway-migration:flywayValidate  # 운영 DB 대상 — 배포 전 checksum 불일치 감지
./gradlew :flyway-migration:flywayInfo      # 이번 배포에 무엇이 나가는지 로그로 남기기
```

> **한계**: 스모크 테스트는 **빈 DB**에서 돕니다. 운영 데이터로는 안 돕니다.
> 데이터 때문에 나는 실패는 스테이징 리허설이 메웁니다. 두 개가 다른 층을 지킵니다.

---

## 10. 에러 → 대응 인덱스

| 메시지 | 원인 | 대응 |
|---|---|---|
| `Migration checksum mismatch` | 적용된 파일을 수정 | 파일 되돌리기. 의도된 변경이면 `flywayRepair` |
| `Schema-validation: missing column [x]` | 엔티티에 있는데 DB에 없음 | 마이그레이션 작성 |
| `Detected resolved migration not applied` | 이미 지나간 버전 번호 | 버전 리베이스. 임시로는 `out-of-order: true` |
| `Detected applied migration not resolved locally` | CLI와 앱의 `locations` 불일치 | 양쪽 설정 일치. **repair 먼저 돌리지 마세요** |
| `Found non-empty schema but no schema history` | 기존 DB에 Flyway 첫 도입 | `baseline-on-migrate` + **`baseline-version`까지** |
| `relation "x" already exists` `42P07` | `baseline-version`이 너무 낮음 | 실제 스키마 상태에 맞는 버전으로 |
| `null value in column ... not-null` `23502` | 백필 없이 not null, 또는 시드 누락 | 컬럼 추가 → 백필 → 제약 순서로 분리 |
| `No Flyway database plugin found` | Gradle 플러그인 classpath에 드라이버 없음 | `buildscript`에 드라이버 + `flyway-database-*` |
| `undo is not supported by OSS Edition` | Community에는 undo가 없음 | forward-fix로 새 마이그레이션 작성 |
| 마이그레이션이 **무응답** | `CONCURRENTLY` + Flyway 락 커넥션 | 마이그레이션 밖에서 실행 |
| 파일이 **아무 반응 없이 무시됨** | 언더스코어 하나 / 콜백 이름 오타 | 파일명 규칙 테스트로 CI에서 차단 |

---

## 11. 명령어

| 명령 | 하는 일 | 주의 |
|---|---|---|
| `flywayInfo` | 적용·대기·무시 상태 표 | 제일 자주 씁니다. Java 마이그레이션은 안 보입니다 |
| `flywayValidate` | checksum·누락 검사만 | 변경 없음. CI에 넣기 좋습니다 |
| `flywayMigrate` | 앱 없이 마이그레이션 실행 | — |
| `flywayRepair` | 실패 이력 제거 + checksum 재계산 | **스키마는 안 건드립니다.** locations가 어긋난 상태로 돌리면 `DELETE`를 박습니다 |
| `flywayBaseline` | 현재 상태를 시작점으로 표시 | 도입 시 한 번 |
| `flywayClean` | 스키마 전체 삭제 | **운영 금지** |

전부 `./gradlew :flyway-migration:` 접두어가 붙습니다.
`-PflywayProfile=prod`를 주면 `db/seed`를 제외하고 실행합니다.

### 진단용 SQL

```sql
-- 전체 이력
select installed_rank, version, description, type, checksum, success
from flyway_schema_history order by installed_rank;

-- 실패한 것만 (PostgreSQL 아닌 DB 에서 유용)
select * from flyway_schema_history where success = false;

-- 현재 버전 (version 이 varchar 라 max() 를 쓰면 안 된다)
select version from flyway_schema_history
where success and version is not null
order by installed_rank desc limit 1;
```

---

## 12. 실행 방법

```bash
# 1. DB 기동 (모듈 디렉토리에서)
cd flyway-migration && docker compose up -d && cd ..

# 2. 앱 실행
./gradlew :flyway-migration:bootRun
curl localhost:8080/members

# 3. 테스트 (Docker 필요)
./gradlew :flyway-migration:test
```

DB는 호스트 **5433** 포트를 씁니다. 로컬에 다른 PostgreSQL이 떠 있어도 충돌하지 않습니다.

---

## 13. 트러블슈팅

### Could not find a valid Docker environment

Testcontainers가 Docker와 붙지 못할 때 납니다. 로그를 보면 원인이 갈립니다.

**(a) `BadRequestException (Status 400)`이 같이 나오는 경우** — 파이프에 연결은 됐는데
`/info`가 400을 돌려주는 상황입니다. Docker Engine 29(API 1.53)가 구버전 API를 거부하는 것으로,
**Testcontainers 버전이 낮아서** 생깁니다.

이 모듈은 `build.gradle`에서 버전을 올려 해결했습니다.

```groovy
ext['testcontainers.version'] = '1.21.4'
```

Boot 3.4.1이 관리하는 1.20.4는 이 환경에서 동작하지 않습니다.
`io.spring.dependency-management`가 BOM 버전을 강제하므로,
의존성에 버전을 직접 박는 것으로는 안 되고 이 프로퍼티로 올려야 합니다.

**(b) 400 없이 전략 탐색만 실패하는 경우** — Docker Desktop이 꺼져 있거나
WSL2 통합이 꺼져 있을 가능성이 높습니다. `docker ps`부터 확인하세요.

### No Flyway database plugin found to handle jdbc:postgresql://...

Gradle 플러그인은 애플리케이션 classpath를 보지 않습니다.
`buildscript` 블록에 JDBC 드라이버와 `flyway-database-postgresql`을 따로 올려야 합니다.

### 포트 5433이 이미 사용 중

`docker-compose.yml`의 포트 매핑과 `application.yml`·`build.gradle`의 URL 세 곳을 함께 바꾸세요.
