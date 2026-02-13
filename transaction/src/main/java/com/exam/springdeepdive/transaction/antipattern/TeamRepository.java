package com.exam.springdeepdive.transaction.antipattern;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {

    /**
     * ✅ GOOD: Fetch Join을 사용하여 N+1 문제 해결
     * 한 번의 쿼리로 Team과 Members를 함께 조회
     */
    @Query("SELECT DISTINCT t FROM Team t JOIN FETCH t.members")
    List<Team> findAllWithMembers();
}
