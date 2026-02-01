package com.exam.jpa;

import com.exam.jpa.domain.Member;
import com.exam.jpa.repository.MemberRepository;
import com.exam.jpa.service.MemberService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class JpaDeepDiveTest {

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManager em;

    @BeforeEach
    void setUp() {
        memberService.createData();
        em.clear(); // 영속성 컨텍스트 초기화 (중요: 1차 캐시 비우기)
    }

    @Test
    @DisplayName("Dirty Checking: save() 호출 없이 엔티티 수정만으로 DB 업데이트가 발생해야 한다")
    void dirtyCheckingTest() {
        // Given
        Member member = memberRepository.findAll().get(0);
        Long memberId = member.getId();

        // When
        memberService.updateName(memberId, "UpdatedName");

        // Then
        Member updatedMember = memberRepository.findById(memberId).orElseThrow();
        assertThat(updatedMember.getName()).isEqualTo("UpdatedName");
    }

    @Test
    @DisplayName("N+1 문제 발생: findAll() 후 객체 탐색 시 추가 쿼리가 발생한다")
    @Transactional // 세션 유지를 위해 필요
    void nPlusOneProblem() {
        System.out.println("=== 1. Member 전체 조회 (쿼리 1번) ===");
        List<Member> members = memberRepository.findAll();

        System.out.println("=== 2. 각 Member의 Team 이름 조회 (여기서 N번 쿼리 발생) ===");
        for (Member member : members) {
            // 지연 로딩된 Team 프록시를 초기화하면서 쿼리가 나감
            System.out.println("Member: " + member.getName() + ", Team: " + member.getTeam().getName());
        }
        // 결과: 총 1 + N 번의 쿼리 실행 (Member 조회 1번 + Team 조회 N번)
    }

    @Test
    @DisplayName("Fetch Join: 한 방 쿼리로 N+1 문제를 해결한다")
    @Transactional
    void fetchJoinTest() {
        System.out.println("=== 1. Fetch Join으로 조회 (쿼리 1번) ===");
        List<Member> members = memberRepository.findAllWithTeam();

        System.out.println("=== 2. 객체 탐색 (이미 로딩되었으므로 쿼리 안 나감) ===");
        for (Member member : members) {
            System.out.println("Member: " + member.getName() + ", Team: " + member.getTeam().getName());
        }
        // 결과: 총 1번의 쿼리만 실행됨
    }
}
