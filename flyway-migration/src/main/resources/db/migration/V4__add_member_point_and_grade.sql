-- point 는 default 가 있으므로 곧바로 not null 가능
alter table member
    add column point integer not null default 0;

-- grade 는 일부러 null 로 둔다. 20단계 Java 마이그레이션이 이 값을 백필한다.
alter table member
    add column grade varchar(20);

comment on column member.point is '적립 포인트';
comment on column member.grade is '등급 (V20 Java 마이그레이션이 백필)';
