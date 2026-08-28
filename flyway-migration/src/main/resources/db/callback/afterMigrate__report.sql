-- SQL 콜백. 파일명이 곧 훅 이름이다: afterMigrate__설명.sql
-- 주요 훅: beforeMigrate / beforeEachMigrate / afterEachMigrate / afterMigrate
--          afterMigrateError / beforeClean / afterClean ...
-- 콜백 파일도 spring.flyway.locations 에 포함된 경로에서 찾는다.
--
-- ${app_name} 은 spring.flyway.placeholders 로 치환된다.
-- 치환이 안 되면 부팅이 실패하므로, placeholder 동작을 확인하기 좋은 자리다.
--
-- raise notice 는 서버 로그가 아니라 "클라이언트"로 전달된다.
-- 즉 postgres 컨테이너 로그가 아니라 애플리케이션 로그에 `DB: ...` 형태로 찍힌다.
-- (메시지를 ASCII 로 쓴 이유: Windows 콘솔에서 한글이 깨져 보이기 때문)
do
$$
    declare
        v_count   integer;
        v_version text;
    begin
        select count(*) into v_count from flyway_schema_history where success = true;

        -- version 은 varchar 라서 max(version) 을 쓰면 '3' > '20' 이 된다.
        -- 최신 버전은 installed_rank 순서로 찾아야 하고, repeatable 은 version 이 null 이라 제외한다.
        select version
        into v_version
        from flyway_schema_history
        where success = true
          and version is not null
        order by installed_rank desc
        limit 1;

        raise notice '[${app_name}] afterMigrate: % applied, current version %',
            v_count, coalesce(v_version, '(none)');
    end
$$;
