package com.exam.jpa.repository;

import com.exam.jpa.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MemberRepository extends JpaRepository<Member, Long> {

    @Query("select m from Member m join fetch m.team")
    List<Member> findAllWithTeam();

    // 벌크 연산: 특정 나이 이상의 회원 이름을 일괄 변경
    // clearAutomatically = true: 쿼리 실행 후 영속성 컨텍스트를 비워줌 (필수!)
    @Modifying(clearAutomatically = true)
    @Query("update Member m set m.name = :name where m.age >= :age")
    int bulkUpdateName(@Param("name") String name, @Param("age") int age);
}
