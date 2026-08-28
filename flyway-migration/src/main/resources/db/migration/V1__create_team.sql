-- 최초 스키마. 여기서부터 flyway_schema_history 가 만들어진다.
create table team
(
    id         bigserial    not null,
    name       varchar(50)  not null,
    created_at timestamp    not null default now(),
    constraint pk_team primary key (id)
);

comment on table team is '팀';
