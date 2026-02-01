package com.exam.jpa.service;

import com.exam.jpa.domain.Member;
import com.exam.jpa.domain.Team;
import com.exam.jpa.repository.MemberRepository;
import com.exam.jpa.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final TeamRepository teamRepository;

    @Transactional
    public void createData() {
        Team teamA = teamRepository.save(new Team("TeamA"));
        Team teamB = teamRepository.save(new Team("TeamB"));

        memberRepository.save(new Member("Member1", teamA));
        memberRepository.save(new Member("Member2", teamA));
        memberRepository.save(new Member("Member3", teamB));
    }

    // Dirty Checking 테스트용
    @Transactional
    public void updateName(Long memberId, String newName) {
        Member member = memberRepository.findById(memberId).orElseThrow();
        member.changeName(newName);
        // memberRepository.save(member); // 호출 안 함! 그래도 업데이트 쿼리가 나가야 함.
    }
}
