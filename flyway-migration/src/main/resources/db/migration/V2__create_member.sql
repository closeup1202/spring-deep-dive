create table member
(
    id         bigserial   not null,
    team_id    bigint,
    name       varchar(50) not null,
    created_at timestamp   not null default now(),
    constraint pk_member primary key (id),
    constraint fk_member_team foreign key (team_id) references team (id)
);

create index idx_member_team_id on member (team_id);

comment on table member is '회원';
