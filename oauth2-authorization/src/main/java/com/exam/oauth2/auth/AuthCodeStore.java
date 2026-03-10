package com.exam.oauth2.auth;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 인증 코드 저장소 (Redis 시뮬레이션)
 *
 * epki-auth의 AuthCodeValidator가 사용하는 Redis 저장소를 학습용으로 대체.
 *
 * 실무 흐름 (epki-auth):
 *   ┌──────────────────────────────────────────────────────────┐
 *   │ EPKI 인증 시스템                                           │
 *   │  1. 인증서 검증 완료                                        │
 *   │  2. AuthInfo(cn, name, instCode)를 Redis에 저장             │
 *   │     Key: "epki-cert-auth:{authCode}"                      │
 *   │     TTL: 60초 (일회성)                                     │
 *   │  3. authCode를 브라우저 redirect URL에 포함                  │
 *   └──────────────────────────────────────────────────────────┘
 *          ↓ (브라우저 redirect)
 *   ┌──────────────────────────────────────────────────────────┐
 *   │ AS (Authorization Server = epki-auth)                    │
 *   │  4. POST /oauth2/authenticate?authCode={code} 수신        │
 *   │  5. Redis에서 authCode로 AuthInfo 조회 + 삭제 (단 1회 사용) │
 *   │  6. EpkiAuthenticationToken 생성 → 인증 완료               │
 *   └──────────────────────────────────────────────────────────┘
 *
 * 학습 모듈에서는:
 *   - ConcurrentHashMap으로 Redis 대체
 *   - @PostConstruct로 테스트용 코드 사전 등록
 *   - consumeAndGet()으로 조회 후 즉시 삭제 (단 1회 사용 보장)
 *
 * Redis 전환 시:
 *   StringRedisTemplate를 주입받아 opsForValue().get(key)로 대체.
 *   Key: "auth-code:{authCode}", TTL: 60초 설정.
 */
@Slf4j
@Component
public class AuthCodeStore {

    // Redis를 대체하는 in-memory 저장소
    // 실무: StringRedisTemplate (Key: "epki-cert-auth:{code}", TTL: 60s)
    private final ConcurrentHashMap<String, AuthInfo> store = new ConcurrentHashMap<>();

    /**
     * 인증 코드 저장
     * 실무에서는 Redis SET with TTL:
     *   redisTemplate.opsForValue().set("auth-code:" + code, json, 60, TimeUnit.SECONDS);
     */
    public void store(String code, AuthInfo authInfo) {
        store.put(code, authInfo);
        log.debug("AuthCode stored: {}", code);
    }

    /**
     * 인증 코드로 사용자 정보 조회 + 즉시 삭제 (단 1회 사용)
     *
     * 실무에서는 Lua 스크립트나 GETDEL 명령으로 원자적 조회+삭제:
     *   String json = redisTemplate.opsForValue().getAndDelete("epki-cert-auth:" + code);
     */
    public Optional<AuthInfo> consumeAndGet(String code) {
        AuthInfo authInfo = store.remove(code); // 조회 + 삭제 (원자적)
        if (authInfo == null) {
            log.warn("Invalid or expired authCode: {}", code);
            return Optional.empty();
        }
        log.debug("AuthCode consumed: {} → user: {}", code, authInfo.username());
        return Optional.of(authInfo);
    }

    /**
     * 테스트용 인증 코드 사전 등록
     *
     * 실무에서는 이 메서드가 없고, 외부 EPKI 시스템이 Redis에 직접 저장.
     * 학습 모듈에서는 curl 테스트를 위해 미리 등록.
     *
     * 사용법:
     *   curl -X POST "http://localhost:9090/oauth2/login?authCode=test-code-001" \
     *     -H "Referer: http://localhost:9090" \
     *     -c cookies.txt -b cookies.txt
     */
    @PostConstruct
    public void initTestCodes() {
        store("test-code-001", AuthInfo.of("user001", "홍길동", "ROLE_USER"));
        store("test-code-002", AuthInfo.of("user002", "김철수", "ROLE_USER"));
        store("test-code-admin", AuthInfo.of("admin001", "관리자", "ROLE_ADMIN"));
        log.info("Test auth codes initialized: test-code-001, test-code-002, test-code-admin");
    }
}
