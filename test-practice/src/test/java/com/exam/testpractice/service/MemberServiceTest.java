package com.exam.testpractice.service;

import com.exam.testpractice.domain.Member;
import com.exam.testpractice.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Mockito 활성화
class MemberServiceTest {

    @Mock // 가짜 객체 생성
    private MemberRepository memberRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks // 가짜 객체를 주입받을 대상
    private MemberService memberService;

    // ============================================
    // 1. 기본 Stubbing & Verify
    // ============================================

    @Test
    @DisplayName("회원가입 성공 - 이메일 발송 포함")
    void signupSuccess() {
        // given
        String email = "test@example.com";
        String name = "Tester";

        given(memberRepository.findByEmail(email)).willReturn(Optional.empty());
        given(memberRepository.save(any(Member.class))).willReturn(new Member(1L, email, name));

        // when
        Member savedMember = memberService.signup(email, name);

        // then
        assertThat(savedMember.getId()).isEqualTo(1L);
        assertThat(savedMember.getEmail()).isEqualTo(email);

        // Verify: save 메서드가 정확히 1번 호출되었는지 검증
        verify(memberRepository, times(1)).save(any(Member.class));

        // Verify: 이메일 발송이 정확히 1번 호출되었는지 검증
        verify(emailService, times(1)).sendWelcomeEmail(email, name);
    }

    @Test
    @DisplayName("중복 이메일이면 예외가 발생해야 한다")
    void signupFailDuplicateEmail() {
        // given
        String email = "duplicate@example.com";
        given(memberRepository.findByEmail(email)).willReturn(Optional.of(new Member(email, "Old User")));

        // when & then
        assertThatThrownBy(() -> memberService.signup(email, "New User"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 존재하는 이메일입니다.");

        // Verify: save가 호출되지 않았는지 검증 (중복이므로)
        verify(memberRepository, never()).save(any(Member.class));

        // Verify: 이메일도 발송되지 않았는지 검증
        verify(emailService, never()).sendWelcomeEmail(anyString(), anyString());
    }

    // ============================================
    // 2. Verify 패턴 - times(), never(), atLeast(), atMost()
    // ============================================

    @Test
    @DisplayName("Verify: 정확히 N번 호출 검증 (times)")
    void verifyTimes() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(new Member(1L, "test@test.com", "Test")));

        // when
        memberService.getMember(1L);
        memberService.getMember(1L);

        // then
        verify(memberRepository, times(2)).findById(1L); // 정확히 2번 호출
    }

    @Test
    @DisplayName("Verify: 한 번도 호출되지 않음 검증 (never)")
    void verifyNever() {
        // given
        given(memberRepository.findByEmail("test@test.com")).willReturn(Optional.of(new Member("test@test.com", "Test")));

        // when
        memberService.requestPasswordReset("test@test.com");

        // then
        verify(memberRepository, never()).save(any()); // save는 호출되지 않음
        verify(emailService, times(1)).sendPasswordResetEmail("test@test.com"); // 이메일은 1번 호출
    }

    @Test
    @DisplayName("Verify: 최소 N번 호출 검증 (atLeastOnce, atLeast)")
    void verifyAtLeast() {
        // given
        given(memberRepository.findById(anyLong())).willReturn(Optional.of(new Member(1L, "test@test.com", "Test")));

        // when
        memberService.getMember(1L);
        memberService.getMember(1L);
        memberService.getMember(1L);

        // then
        verify(memberRepository, atLeastOnce()).findById(anyLong()); // 최소 1번
        verify(memberRepository, atLeast(2)).findById(anyLong()); // 최소 2번
    }

    @Test
    @DisplayName("Verify: 최대 N번 호출 검증 (atMost)")
    void verifyAtMost() {
        // given
        given(memberRepository.count()).willReturn(100L);

        // when
        memberService.getTotalMemberCount();

        // then
        verify(memberRepository, atMost(1)).count(); // 최대 1번
    }

    // ============================================
    // 3. 호출 순서 검증 (InOrder)
    // ============================================

    @Test
    @DisplayName("Verify: 메서드 호출 순서 검증 (InOrder) - 실무 핵심!")
    void verifyInOrder() {
        // given
        String email = "order@test.com";
        String name = "OrderTest";
        given(memberRepository.findByEmail(email)).willReturn(Optional.empty());
        given(memberRepository.save(any(Member.class))).willReturn(new Member(1L, email, name));

        // when
        memberService.signup(email, name);

        // then - 실무에서 중요! 회원가입 후 이메일 발송이 순서대로 실행되는지 검증
        InOrder inOrder = inOrder(memberRepository, emailService);
        inOrder.verify(memberRepository).findByEmail(email); // 1. 중복 체크
        inOrder.verify(memberRepository).save(any(Member.class)); // 2. 저장
        inOrder.verify(emailService).sendWelcomeEmail(email, name); // 3. 이메일 발송
    }

    // ============================================
    // 4. ArgumentCaptor - 실제 전달된 인자 검증
    // ============================================

    @Test
    @DisplayName("ArgumentCaptor: 실제 전달된 인자 값 캡처하여 검증 - 실무 필수!")
    void argumentCaptorTest() {
        // given
        String email = "captor@test.com";
        String name = "CaptorTest";
        given(memberRepository.findByEmail(email)).willReturn(Optional.empty());
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        memberService.signup(email, name);

        // then - 실제로 save에 전달된 Member 객체를 캡처하여 검증
        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(memberCaptor.capture());

        Member capturedMember = memberCaptor.getValue();
        assertThat(capturedMember.getEmail()).isEqualTo(email);
        assertThat(capturedMember.getName()).isEqualTo(name);
    }

    @Test
    @DisplayName("ArgumentCaptor: 여러 번 호출 시 모든 인자 캡처")
    void argumentCaptorMultiple() {
        // given
        given(memberRepository.findById(anyLong())).willReturn(Optional.of(new Member(1L, "test@test.com", "Test")));
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        memberService.updateName(1L, "NewName1");
        memberService.updateName(1L, "NewName2");

        // then - 모든 호출의 인자를 캡처
        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository, times(2)).save(captor.capture());

        List<Member> capturedMembers = captor.getAllValues();
        assertThat(capturedMembers).hasSize(2);
        assertThat(capturedMembers.get(0).getName()).isEqualTo("NewName1");
        assertThat(capturedMembers.get(1).getName()).isEqualTo("NewName2");
    }

    // ============================================
    // 5. 고급 Stubbing - Answer (실무 활용도 높음)
    // ============================================

    @Test
    @DisplayName("Answer: 입력값을 그대로 반환 (JPA save 패턴)")
    void answerReturnArgument() {
        // given
        String email = "answer@test.com";
        String name = "AnswerTest";
        given(memberRepository.findByEmail(email)).willReturn(Optional.empty());

        // Answer: save 호출 시 첫 번째 인자를 그대로 반환 (실무에서 자주 사용)
        given(memberRepository.save(any(Member.class)))
                .willAnswer(invocation -> {
                    Member member = invocation.getArgument(0);
                    return new Member(99L, member.getEmail(), member.getName());
                });

        // when
        Member savedMember = memberService.signup(email, name);

        // then
        assertThat(savedMember.getId()).isEqualTo(99L);
        assertThat(savedMember.getEmail()).isEqualTo(email);
    }

    @Test
    @DisplayName("Answer: 호출 횟수에 따라 다른 값 반환")
    void answerDifferentValues() {
        // given - 첫 번째 호출에는 100, 두 번째 호출에는 200 반환
        given(memberRepository.count())
                .willReturn(100L)
                .willReturn(200L);

        // when & then
        assertThat(memberService.getTotalMemberCount()).isEqualTo(100L);
        assertThat(memberService.getTotalMemberCount()).isEqualTo(200L);
    }

    // ============================================
    // 6. Void 메서드 Stubbing (willDoNothing, willThrow)
    // ============================================

    @Test
    @DisplayName("Void 메서드: 정상 동작 (willDoNothing)")
    void voidMethodDoNothing() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.of(new Member(1L, "test@test.com", "Test")));

        // void 메서드는 기본적으로 아무것도 하지 않지만 명시적으로 표현 가능
        willDoNothing().given(emailService).sendWelcomeEmail(anyString(), anyString());

        // when
        memberService.getMember(1L);

        // then
        verify(memberRepository).findById(1L);
    }

    @Test
    @DisplayName("Void 메서드: 예외 발생 (willThrow) - 실무 필수!")
    void voidMethodThrowException() {
        // given
        String email = "exception@test.com";
        String name = "ExceptionTest";
        given(memberRepository.findByEmail(email)).willReturn(Optional.empty());
        given(memberRepository.save(any(Member.class))).willReturn(new Member(1L, email, name));

        // void 메서드에서 예외 발생 시키기 (이메일 발송 실패 시나리오)
        willThrow(new RuntimeException("Email service is down"))
                .given(emailService).sendWelcomeEmail(anyString(), anyString());

        // when & then
        assertThatThrownBy(() -> memberService.signup(email, name))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Email service is down");
    }

    // ============================================
    // 7. doReturn vs given (특수 케이스)
    // ============================================

    @Test
    @DisplayName("doReturn: Spy 객체나 실제 메서드를 호출하지 않고 싶을 때 사용")
    void doReturnPattern() {
        // given
        // doReturn은 실제 메서드를 호출하지 않고 바로 값을 반환
        // given은 내부적으로 실제 메서드를 한 번 호출함
        doReturn(Optional.empty()).when(memberRepository).findByEmail("test@test.com");

        // when
        Optional<Member> result = memberRepository.findByEmail("test@test.com");

        // then
        assertThat(result).isEmpty();
    }

    // ============================================
    // 8. verifyNoMoreInteractions (추가 호출 검증)
    // ============================================

    @Test
    @DisplayName("verifyNoMoreInteractions: 명시한 것 외에 다른 호출이 없는지 검증")
    void verifyNoMoreInteractionsTest() {
        // given
        given(memberRepository.count()).willReturn(100L);

        // when
        memberService.getTotalMemberCount();

        // then
        verify(memberRepository).count();
        verifyNoMoreInteractions(memberRepository); // count 외에 다른 호출이 없어야 함
    }

    // ============================================
    // 9. 실무 시나리오: 삭제 + Verify
    // ============================================

    @Test
    @DisplayName("실무 시나리오: 회원 삭제 성공")
    void deleteMemberSuccess() {
        // given
        Long memberId = 1L;
        Member member = new Member(memberId, "delete@test.com", "DeleteTest");
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));
        willDoNothing().given(memberRepository).delete(any(Member.class));

        // when
        memberService.deleteMember(memberId);

        // then
        verify(memberRepository).findById(memberId);
        verify(memberRepository).delete(member);
    }

    // ============================================
    // 10. 실무 시나리오: 리스트 조회
    // ============================================

    @Test
    @DisplayName("실무 시나리오: 여러 회원 조회")
    void getMembersSuccess() {
        // given
        List<Long> ids = Arrays.asList(1L, 2L, 3L);
        List<Member> members = Arrays.asList(
                new Member(1L, "user1@test.com", "User1"),
                new Member(2L, "user2@test.com", "User2"),
                new Member(3L, "user3@test.com", "User3")
        );
        given(memberRepository.findAllById(ids)).willReturn(members);

        // when
        List<Member> result = memberService.getMembers(ids);

        // then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getName()).isEqualTo("User1");
        verify(memberRepository).findAllById(ids);
    }
}
