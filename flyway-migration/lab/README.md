# lab — 실습용 준비 파일

여기 있는 SQL 파일들은 **의도적으로 `src/main/resources/db/migration` 밖에** 두었다.
Flyway 는 `spring.flyway.locations` 에 지정된 경로만 스캔하므로 이 파일들은 아직 실행되지 않는다.

[STEPS.md](../STEPS.md) 의 해당 단계에서 `src/main/resources/db/migration/` 으로
**복사해 넣으면서** 실습한다.

| 파일 | 단계 | 목적 |
|---|---|---|
| `V4_1__hotfix_member_email_index.sql` | 5 | out-of-order — 뒤늦게 머지된 낮은 버전 |
| `V21__failing_migration.sql` | 6 | 실패한 마이그레이션과 트랜잭션 롤백 |
| `V30__add_member_display_name.sql` | 9 | expand-contract 1/3 |
| `V31__backfill_member_display_name.sql` | 9 | expand-contract 2/3 |
| `V32__drop_member_name.sql` | 9 | expand-contract 3/3 |

Java 마이그레이션은 컴파일이 필요해서 여기가 아니라
`src/main/java/com/exam/flyway/migration/lab/` 에 있다.
같은 원리로 `locations` 에 없는 패키지라 실행되지 않는다 (7단계).

## 시작 상태

`db/migration` 에는 **V1~V4 + R__** 만 들어 있다.
V5 는 3단계에서 직접 작성하고, V33 은 10단계에서 직접 작성한다.

## 실습 중 되돌리기

```bash
cd flyway-migration && docker compose down -v && docker compose up -d && cd ..
# 또는
./gradlew :flyway-migration:flywayClean          # local 프로필에서만 가능
```
