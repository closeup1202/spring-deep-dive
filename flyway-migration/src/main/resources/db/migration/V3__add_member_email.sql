-- 컬럼 추가는 기존 행에 대해 null 을 채우므로 not null 을 바로 걸 수 없다.
-- not null 로 만들려면: 컬럼 추가 → 백필 → not null 부여 (마이그레이션 3개로 분리)
alter table member
    add column email varchar(255);

comment on column member.email is '이메일';
