package com.exam.jpa.repository;

import com.exam.jpa.domain.Member;
import com.exam.jpa.domain.Team;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest // JPA 관련 빈만 로드 (가볍고 빠름)
// @DataJpaTest는 기본적으로 Auditing을 켜지 않으므로 별도 설정 필요할 수 있음
// 하지만 메인 애플리케이션에 @EnableJpaAuditing이 있으면 작동함.
class RepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TeamRepository teamRepository;
    
    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("Auditing: 등록일/수정일이 자동으로 저장된다")
    void auditingTest() {
        // Given
        Team team = teamRepository.save(new Team("TeamA"));
        Member member = new Member("Member1", team);
        
        // When
        Member savedMember = memberRepository.save(member);

        // Then
        assertThat(savedMember.getCreatedDate()).isNotNull();
        assertThat(savedMember.getLastModifiedDate()).isNotNull();
        
        System.out.println("CreatedDate: " + savedMember.getCreatedDate());
    }

    @Test
    @DisplayName("벌크 연산: @Modifying(clearAutomatically=true)가 없으면 영속성 컨텍스트 불일치 발생")
    void bulkUpdateTest() {
        // Given
        Team team = teamRepository.save(new Team("TeamA"));
        memberRepository.save(new Member("OldName1", 20, team));
        memberRepository.save(new Member("OldName2", 30, team));
        memberRepository.save(new Member("OldName3", 40, team));

        // When
        // 20살 이상인 회원의 이름을 "NewName"으로 변경 (DB에는 반영됨)
        int resultCount = memberRepository.bulkUpdateName("NewName", 20);
        assertThat(resultCount).isEqualTo(3);

        // Then
        // 만약 clearAutomatically=true가 없다면?
        // 영속성 컨텍스트(1차 캐시)에는 여전히 "OldName"으로 남아있어서 테스트 실패함.
        // 옵션을 켰으므로, 다시 조회할 때 DB에서 가져와서 "NewName"이 되어야 함.
        
        List<Member> members = memberRepository.findAll();
        for (Member member : members) {
            System.out.println("Member Name: " + member.getName());
            assertThat(member.getName()).isEqualTo("NewName");
        }
    }
}
