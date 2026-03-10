package com.exam.oauth2.user;

import java.io.Serial;
import java.io.Serializable;

/**
 * 커스텀 인증 주체(Principal) 정의
 *
 * epki-auth의 EpkiPrincipal을 참고.
 * Spring Security에서 Authentication.getPrincipal()이 반환하는 객체.
 *
 * 실무(EPKI):
 *   - cn:       인증서 CN (주민등록번호 기반 식별자)
 *   - name:     사용자 실명
 *   - instCode: 소속 기관 코드
 *
 * 학습 모듈에서는 일반적인 사용자 정보로 대체:
 *   - userId:   사용자 고유 ID
 *   - username: 사용자명
 *   - role:     역할 (ROLE_USER, ROLE_ADMIN 등)
 *
 * Serializable을 구현하는 이유:
 *   Spring Security가 SecurityContext를 세션에 직렬화해 저장하기 때문.
 *   세션 기반 인증에서 Principal은 반드시 직렬화 가능해야 한다.
 */
public record CustomUserPrincipal(
        String userId,
        String username,
        String role
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static CustomUserPrincipal of(String userId, String username, String role) {
        return new CustomUserPrincipal(userId, username, role);
    }

    // Spring Security getName() 호환을 위해 username 반환
    public String getName() {
        return username;
    }
}
