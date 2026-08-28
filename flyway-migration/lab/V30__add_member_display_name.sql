-- ── STEPS 9단계: 무중단 컬럼 rename (expand-contract) 1/3 — expand ──────
-- 목표: member.name → member.display_name 으로 이름 바꾸기
--
-- 절대 하면 안 되는 것:
--   alter table member rename column name to display_name;
--   → 마이그레이션이 도는 그 순간부터, 아직 배포되지 않은 구버전 인스턴스가 전부 죽는다.
--
-- expand-contract 는 이걸 3번의 배포로 나눈다.
--
-- 【배포 1 — 이 파일】 새 컬럼을 nullable 로 추가하기만 한다.
--   구버전 코드는 이 컬럼을 모르므로 아무 영향이 없다.
--   애플리케이션 코드는 이 시점부터 name 과 display_name 에 "둘 다 쓴다"(dual write).
alter table member
    add column display_name varchar(50);
