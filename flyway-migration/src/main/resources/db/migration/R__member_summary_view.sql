-- Repeatable 마이그레이션 (버전 번호 없음, R__ 로 시작).
--  * 항상 versioned 마이그레이션이 전부 끝난 뒤에 실행된다.
--  * 파일 내용(checksum)이 바뀌면 그때마다 다시 실행된다.
--  * 그래서 뷰/함수/프로시저처럼 "덮어쓰기 가능한" 객체에만 쓴다.
--  * 여러 R__ 파일이 있으면 파일명 알파벳 순으로 실행된다.
create or replace view member_summary as
select t.id                        as team_id,
       t.name                      as team_name,
       count(m.id)                 as member_count,
       coalesce(sum(m.point), 0)   as total_point,
       min(m.created_at)           as first_joined_at
from team t
         left join member m on m.team_id = t.id
group by t.id, t.name;
