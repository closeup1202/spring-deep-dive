package com.exam.oauth2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OAuth2 Authorization Server 학습 모듈
 *
 * 실무 프로젝트(epki-auth)를 분석해 핵심 패턴을 추출한 학습용 구현체.
 *
 * 주요 학습 포인트:
 * 1. Spring Authorization Server의 두 개 FilterChain 구조
 * 2. RegisteredClientRepository 커스텀 구현 (클라이언트 유효성 검증 포함)
 * 3. OAuth2AuthorizationService 커스텀 구현 (JPA 기반 토큰 저장)
 * 4. 커스텀 인증 필터 + 프로바이더 패턴 (EPKI 인증서 인증 시뮬레이션)
 * 5. JWT 토큰 커스터마이징 (사용자 정보 클레임 추가)
 * 6. OIDC UserInfo 엔드포인트 매핑
 *
 * 실행 후 테스트:
 * - H2 Console: http://localhost:9090/h2-console
 * - JWKS: http://localhost:9090/oauth2/jwks
 * - OpenID Config: http://localhost:9090/.well-known/openid-configuration
 * - 커스텀 로그인: POST http://localhost:9090/oauth2/login?authCode=test-code-001
 */
@SpringBootApplication
public class OAuth2AuthorizationApplication {

    public static void main(String[] args) {
        SpringApplication.run(OAuth2AuthorizationApplication.class, args);
    }
}
