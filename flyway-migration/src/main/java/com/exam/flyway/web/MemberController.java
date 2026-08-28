package com.exam.flyway.web;

import com.exam.flyway.domain.Member;
import com.exam.flyway.repository.MemberRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 앱이 정상적으로 떴고 마이그레이션 결과가 엔티티와 맞는지 눈으로 확인하는 용도.
 * <pre>curl localhost:8080/members</pre>
 */
@RestController
public class MemberController {

    private final MemberRepository memberRepository;

    public MemberController(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @GetMapping("/members")
    public List<MemberResponse> members() {
        return memberRepository.findAll().stream()
                .map(MemberResponse::from)
                .toList();
    }

    public record MemberResponse(Long id, String name, String email, int point, String grade) {

        static MemberResponse from(Member member) {
            return new MemberResponse(
                    member.getId(),
                    member.getName(),
                    member.getEmail(),
                    member.getPoint(),
                    member.getGrade() == null ? null : member.getGrade().name());
        }
    }
}
