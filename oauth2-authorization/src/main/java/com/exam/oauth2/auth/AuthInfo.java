package com.exam.oauth2.auth;

/**
 * 인증 코드 저장소(AuthCodeStore)에서 조회되는 사용자 정보
 *
 * epki-auth의 AuthInfo record 참고.
 *
 * 실무(EPKI 흐름):
 *   1. 외부 EPKI 인증 시스템이 인증서 검증 완료
 *   2. 인증 결과(AuthInfo)를 Redis에 저장 → authCode 발급
 *   3. 브라우저가 authCode를 AS로 전달
 *   4. AS가 Redis에서 authCode로 AuthInfo 조회 → 사용자 인증 완료
 *
 * 학습 모듈에서는 Redis 대신 ConcurrentHashMap(AuthCodeStore) 사용.
 */
public record AuthInfo(
        String userId,
        String username,
        String role
) {
    public static AuthInfo of(String userId, String username, String role) {
        return new AuthInfo(userId, username, role);
    }
}
