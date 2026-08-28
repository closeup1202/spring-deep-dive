-- ── expand-contract 2/3 — backfill ─────────────────────────────────────
-- 【배포 2 — 이 파일】 기존 행을 채우고, 그제서야 not null 을 걸 수 있다.
--   읽기는 display_name 으로 전환, 쓰기는 아직 둘 다 유지.
--
-- 행이 수백만 건이면 이 update 하나가 테이블 전체를 잠근다.
-- 실무에서는 배치로 쪼개거나 별도 잡으로 돌리고, 마이그레이션에서는 not null 만 건다.
update member
set display_name = name
where display_name is null;

alter table member
    alter column display_name set not null;
