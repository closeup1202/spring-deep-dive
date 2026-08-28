-- local 프로필에서만 읽히는 개발용 시드 데이터.
-- Repeatable 로 만들되 반드시 멱등(idempotent)하게 작성해야 한다.
-- Repeatable 은 파일이 바뀔 때마다 재실행되므로, insert 만 있으면 중복이 쌓인다.
--
-- ※ 이 파일은 STEPS 11단계에서 "실제로 터지는" 파일이다.
--    9단계에서 display_name 에 not null 을 걸고 나면 빈 DB 재생 시 이 insert 가 실패한다.
--    로컬에서는 시드가 이미 적용돼 재실행되지 않으므로 절대 보이지 않는다.
--    미리 고치지 말 것 — 스모크 테스트가 잡아내는 걸 직접 보는 게 11단계의 목적이다.

insert into team (id, name)
values (1, '플랫폼'),
       (2, '결제'),
       (3, '데이터')
on conflict (id) do nothing;

insert into member (id, team_id, name, email, point)
values (1, 1, '김건홍', 'gunhong@example.com', 12000),
       (2, 1, '이서준', 'seojun@EXAMPLE.com', 300),
       (3, 2, '박지우', 'jiwoo@example.com', 5400),
       (4, 2, '최민서', 'minseo@example.com', 0),
       (5, 3, '정하윤', 'hayun@example.com', 98000),
       (6, null, '무소속', 'nobody@example.com', 700)
on conflict (id) do nothing;

-- bigserial 시퀀스를 수동 insert 한 id 뒤로 밀어준다.
-- (이걸 빼먹으면 JPA 로 저장할 때 duplicate key 가 난다 — 시드 데이터의 흔한 함정)
select setval(pg_get_serial_sequence('team', 'id'), coalesce((select max(id) from team), 1));
select setval(pg_get_serial_sequence('member', 'id'), coalesce((select max(id) from member), 1));
