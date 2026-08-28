-- ── expand-contract 3/3 — contract ─────────────────────────────────────
-- 【배포 3 — 이 파일】 name 을 참조하는 코드가 완전히 사라진 것을 확인한 뒤에만 실행한다.
--   보통 배포 2가 끝나고 며칠 뒤, 별도 배포로 나간다.
--
-- 이 단계에서 Member 엔티티의 name 필드도 함께 지워야 한다.
-- (ddl-auto=validate 라 안 지우면 다음 부팅이 실패하며 알려준다)
alter table member
    drop column name;
