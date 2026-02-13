package com.exam.springdeepdive.transaction.antipattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Anti-Pattern 4: N+1 Query Detection
 *
 * Lazy 로딩 관계를 반복문에서 접근할 때 N+1 쿼리가 발생합니다.
 *
 * 시나리오:
 * 1. Team 목록 조회 (1개 쿼리)
 * 2. 각 Team의 members 접근 (N개 쿼리) <- N+1 문제!
 *
 * 해결책:
 * - Fetch Join 사용
 * - @EntityGraph 사용
 * - @BatchSize 사용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NPlusOneService {

    private final TeamRepository teamRepository;

    /**
     * 테스트 데이터 초기화
     */
    @Transactional
    public void setupTestData() {
        log.info("=== Setting up N+1 test data ===");

        // 기존 데이터 삭제
        teamRepository.deleteAll();

        // 3개 팀 생성, 각 팀마다 2명의 멤버
        for (int i = 1; i <= 3; i++) {
            Team team = new Team("Team-" + i);
            teamRepository.save(team);

            for (int j = 1; j <= 2; j++) {
                TeamMember member = new TeamMember("Member-" + i + "-" + j, team);
                team.getMembers().add(member);
            }
            teamRepository.save(team);
        }

        log.info("Created 3 teams with 2 members each");
    }

    /**
     * ❌ BAD: N+1 Query Problem
     *
     * 실행되는 쿼리:
     * 1. SELECT * FROM team; (1개)
     * 2. SELECT * FROM team_member WHERE team_id = 1; (Team 개수만큼)
     * 3. SELECT * FROM team_member WHERE team_id = 2;
     * 4. SELECT * FROM team_member WHERE team_id = 3;
     *
     * 총 1 + N개의 쿼리 실행! (N = Team 개수)
     */
    @Transactional
    public void demonstrateNPlusOneProblem() {
        log.info("=== Demonstrating N+1 Query Problem ===");

        // 1개 쿼리: Team 목록 조회
        List<Team> teams = teamRepository.findAll();
        log.info("Loaded {} teams", teams.size());

        // N개 쿼리: 각 Team의 members 접근 시마다 쿼리 실행
        for (Team team : teams) {
            int memberCount = team.getMembers().size();  // 여기서 추가 쿼리 발생!
            log.info("Team: {}, Members: {}", team.getName(), memberCount);
        }

        log.info("Total queries: 1 (findAll) + {} (lazy loading) = {} queries",
                 teams.size(), teams.size() + 1);
    }

    /**
     * ✅ GOOD: Fetch Join으로 해결
     *
     * 실행되는 쿼리:
     * 1. SELECT t.*, m.* FROM team t JOIN team_member m ON t.id = m.team_id; (1개만!)
     *
     * 한 번의 쿼리로 Team과 Members를 모두 조회
     */
    @Transactional
    public void demonstrateFetchJoinSolution() {
        log.info("=== Demonstrating Fetch Join Solution ===");

        // 1개 쿼리: Team과 Members를 함께 조회
        List<Team> teams = teamRepository.findAllWithMembers();
        log.info("Loaded {} teams with Fetch Join", teams.size());

        // 추가 쿼리 없음: 이미 메모리에 로드됨
        for (Team team : teams) {
            int memberCount = team.getMembers().size();  // 쿼리 발생 안 함!
            log.info("Team: {}, Members: {}", team.getName(), memberCount);
        }

        log.info("Total queries: 1 (fetch join only)");
    }

    /**
     * 비교 실행 메서드
     */
    @Transactional
    public void comparePerformance() {
        log.info("\n");
        log.info("========================================");
        log.info("N+1 Query Problem vs Fetch Join");
        log.info("========================================");

        log.info("\n[1] N+1 Problem (Bad):");
        demonstrateNPlusOneProblem();

        log.info("\n[2] Fetch Join (Good):");
        demonstrateFetchJoinSolution();

        log.info("\n========================================");
        log.info("결론: Fetch Join은 1개 쿼리, N+1은 {}개 쿼리",
                 teamRepository.count() + 1);
        log.info("========================================");
    }
}
