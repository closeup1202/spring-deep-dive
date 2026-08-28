-- ── STEPS 6단계: 실패하는 마이그레이션 실습 파일 ───────────────────────
-- db/migration 으로 복사하고 앱을 띄우면 부팅이 실패한다. 그 뒤를 관찰하는 게 목적이다.
--
-- 확인할 것:
--   1) 첫 번째 문장은 성공했는데 테이블이 남아 있는가?
--      → PostgreSQL 은 DDL 트랜잭션을 지원하므로 통째로 롤백된다.
--        (MySQL 이었다면 첫 문장만 적용된 채 남아 스키마가 깨진다 — DB 별 차이가 여기서 갈린다)
--   2) flyway_schema_history 에 success = false 행이 남았는가?
--      select * from flyway_schema_history order by installed_rank desc limit 3;
--   3) 실패 행이 남아 있으면 다음 부팅도 계속 막힌다.
--      해결: 파일의 SQL 을 고친 뒤 ./gradlew flywayRepair 로 실패 이력을 정리하고 다시 실행.
--      (PostgreSQL 은 실패 시 이력을 아예 안 남기기도 한다. 그 경우 repair 없이 재실행하면 된다)

create table lab_broken
(
    id bigserial not null,
    constraint pk_lab_broken primary key (id)
);

-- 존재하지 않는 테이블 → 실패
insert into no_such_table (id)
values (1);
