package com.exam.testpractice.service;

import com.exam.testpractice.domain.Member;
import com.exam.testpractice.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class) // Mockito 활성화
class MemberServiceTest {

    @Mock // 가짜 객체 생성
    private MemberRepository memberRepository;

    @InjectMocks // 가짜 객체를 주입받을 대상
    private MemberService memberService;

    @Test
    @DisplayName("회원가입 성공 테스트")
    void signupSuccess() {
        // given
        String email = "test@example.com";
        String name = "Tester";
        
        // Stubbing: findByEmail 호출 시 빈 Optional 반환 (중복 없음)
        given(memberRepository.findByEmail(email)).willReturn(Optional.empty());
        
        // Stubbing: save 호출 시 저장된 Member 반환
        given(memberRepository.save(any(Member.class))).willReturn(new Member(1L, email, name));

        // when
        Member savedMember = memberService.signup(email, name);

        // then
        assertThat(savedMember.getId()).isEqualTo(1L);
        assertThat(savedMember.getEmail()).isEqualTo(email);
        
        // Verify: save 메서드가 정확히 1번 호출되었는지 검증
        verify(memberRepository).save(any(Member.class));
    }

    @Test
    @DisplayName("중복 이메일이면 예외가 발생해야 한다")
    void signupFailDuplicateEmail() {
        // given
        String email = "duplicate@example.com";
        
        // Stubbing: 이미 존재하는 회원 반환
        given(memberRepository.findByEmail(email)).willReturn(Optional.of(new Member(email, "Old User")));

        // when & then
        assertThatThrownBy(() -> memberService.signup(email, "New User"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 존재하는 이메일입니다.");
    }
}
