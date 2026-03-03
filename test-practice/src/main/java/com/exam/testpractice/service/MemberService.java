package com.exam.testpractice.service;

import com.exam.testpractice.domain.Member;
import com.exam.testpractice.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final EmailService emailService;

    /**
     * 회원가입 + 환영 이메일 발송
     */
    @Transactional
    public Member signup(String email, String name) {
        if (memberRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }
        Member savedMember = memberRepository.save(new Member(email, name));

        // 외부 API 호출 (이메일 발송)
        emailService.sendWelcomeEmail(email, name);

        return savedMember;
    }

    /**
     * 회원 조회
     */
    @Transactional(readOnly = true)
    public Member getMember(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }

    /**
     * 회원 삭제
     */
    @Transactional
    public void deleteMember(Long id) {
        Member member = getMember(id);
        memberRepository.delete(member);
    }

    /**
     * 회원 이름 변경
     */
    @Transactional
    public Member updateName(Long id, String newName) {
        Member member = getMember(id);
        // 실제로는 Member에 updateName 메서드가 있어야 하지만 예시용
        memberRepository.save(new Member(member.getId(), member.getEmail(), newName));
        return memberRepository.findById(id).orElseThrow();
    }

    /**
     * 비밀번호 재설정 이메일 발송
     */
    @Transactional(readOnly = true)
    public void requestPasswordReset(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        emailService.sendPasswordResetEmail(member.getEmail());
    }

    /**
     * 여러 회원 조회
     */
    @Transactional(readOnly = true)
    public List<Member> getMembers(List<Long> ids) {
        return memberRepository.findAllById(ids);
    }

    /**
     * 전체 회원 수 조회
     */
    @Transactional(readOnly = true)
    public long getTotalMemberCount() {
        return memberRepository.count();
    }
}
