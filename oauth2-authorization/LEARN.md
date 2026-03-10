# OAuth2 Authorization Server 심층 학습

> 실무 프로젝트(epki-auth)를 분석해 Spring Authorization Server의 핵심 패턴을 정리한 학습 모듈.

---

## 목차

1. [OAuth2 개념 정리](#1-oauth2-개념-정리)
2. [Spring Authorization Server 아키텍처](#2-spring-authorization-server-아키텍처)
3. [두 개의 SecurityFilterChain 구조](#3-두-개의-securityfilterchain-구조)
4. [RegisteredClientRepository - 클라이언트 관리](#4-registeredclientrepository---클라이언트-관리)
5. [OAuth2AuthorizationService - 토큰 저장소](#5-oauth2authorizationservice---토큰-저장소)
6. [커스텀 인증 필터 + 프로바이더 패턴](#6-커스텀-인증-필터--프로바이더-패턴)
7. [JWT 토큰 커스터마이징](#7-jwt-토큰-커스터마이징)
8. [OIDC UserInfo 엔드포인트](#8-oidc-userinfo-엔드포인트)
9. [실무 적용 패턴 (epki-auth)](#9-실무-적용-패턴-epki-auth)
10. [테스트 가이드](#10-테스트-가이드)

---

## 1. OAuth2 개념 정리

### OAuth2의 4가지 역할

```
┌──────────────┐    ┌──────────────────────────┐
│ Resource     │    │ Authorization Server (AS)  │
│ Owner        │───▶│  = 이 모듈                 │
│ (사용자)      │    │  - 사용자 인증              │
└──────────────┘    │  - 토큰 발급               │
                    │  - 클라이언트 검증           │
┌──────────────┐    └──────────────────────────┘
│ Client       │              │
│ (웹앱/서비스) │◀─────────────┘ (토큰)
└──────────────┘
        │ (Bearer 토큰)
        ▼
┌──────────────┐
│ Resource     │
│ Server       │
│ (API 서버)   │
└──────────────┘
```

### Grant Type(인가 방식) 비교

| Grant Type | 사용 시나리오 | 흐름 |
|-----------|-------------|------|
| **Authorization Code** | 사용자가 직접 로그인하는 웹/모바일 앱 | 사용자 → 로그인 → 인가코드 → 토큰 |
| **Client Credentials** | 서비스 간 통신 (사용자 없음) | 클라이언트 인증 → 토큰 |
| **Refresh Token** | 만료된 Access Token 갱신 | Refresh Token → 새 Access Token |

### Authorization Code Grant 흐름 (상세)

```
Client                    AS                        User
  │                        │                          │
  │─── GET /oauth2/authorize?client_id=...&scope=... ─▶│
  │                        │◀── 로그인 페이지 ─────────│
  │                        │    (미인증 시 redirect)    │
  │                        │                          │
  │                        │─── POST /oauth2/login ──▶│ (커스텀 인증)
  │                        │    authCode=xxx           │
  │                        │◀── 인증 완료 ─────────────│
  │                        │                          │
  │                        │─── (동의 화면) ──────────▶│
  │                        │◀── 동의 ─────────────────│
  │                        │                          │
  │◀── redirect redirect_uri?code=AUTH_CODE ─────────│
  │                        │                          │
  │─── POST /oauth2/token ─▶│
  │    code=AUTH_CODE       │
  │    client_id=...        │
  │    client_secret=...    │
  │◀── { access_token,      │
  │      refresh_token,      │
  │      id_token }         │
```

---

## 2. Spring Authorization Server 아키텍처

### 의존성 하나로 가능한 것들

```gradle
implementation 'org.springframework.boot:spring-boot-starter-oauth2-authorization-server'
```

이 단일 의존성이 제공하는 것:
- **JWT 발급/검증** (nimbus-jose-jwt)
- **OAuth2 엔드포인트** (/oauth2/authorize, /oauth2/token, /oauth2/jwks 등)
- **OIDC 지원** (/userinfo, /.well-known/openid-configuration)
- **토큰 인트로스펙션/폐기** (/oauth2/introspect, /oauth2/revoke)

### 핵심 인터페이스

```
RegisteredClientRepository   ← 클라이언트(앱) 정보 저장
OAuth2AuthorizationService   ← 인가 정보(토큰 등) 저장
OAuth2TokenCustomizer        ← JWT 클레임 커스터마이징
AuthorizationServerSettings  ← issuer, 엔드포인트 경로 설정
JWKSource                    ← JWT 서명 키 제공
```

### 제공되는 기본 구현체

| 인터페이스 | InMemory | JDBC | 커스텀 |
|-----------|---------|------|-------|
| `RegisteredClientRepository` | O | O | 이 모듈 |
| `OAuth2AuthorizationService` | O | O | 이 모듈 |
| `OAuth2AuthorizationConsentService` | O | O | - |

---

## 3. 두 개의 SecurityFilterChain 구조

### 왜 두 개가 필요한가?

Spring AS의 OAuth2 엔드포인트(`/oauth2/*`)와 일반 로그인 페이지(`/login`)는
**서로 다른 보안 규칙**이 적용되어야 한다.

```java
// Chain 1 @Order(1): OAuth2 Authorization Server 전용
// 처리 경로: /oauth2/authorize, /oauth2/token, /userinfo 등
// 특징:
//   - 미인증 시 /login으로 redirect
//   - JWT 리소스 서버로도 동작 (토큰 검증)
//   - OAuth2 프로토콜 오류는 JSON으로 응답

@Bean
@Order(1)
public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
    OAuth2AuthorizationServerConfigurer configurer = OAuth2AuthorizationServerConfigurer.authorizationServer();

    http.securityMatcher(configurer.getEndpointsMatcher())   // OAuth2 엔드포인트만 처리
        .with(configurer, authServer -> authServer
            .oidc(oidc -> oidc.userInfoEndpoint(ui -> ui.userInfoMapper(userInfoMapper)))
        )
        .exceptionHandling(e -> e
            .defaultAuthenticationEntryPointFor(
                new LoginUrlAuthenticationEntryPoint("/login"),
                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
            )
        )
        .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()));

    return http.build();
}

// Chain 2 @Order(2): 기본 보안 (로그인 페이지, 커스텀 인증 등)
@Bean
@Order(2)
public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/oauth2/login", "/login", "/error").permitAll()
            .anyRequest().authenticated()
        )
        .formLogin(Customizer.withDefaults())
        .addFilterBefore(customLoginFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
}
```

### FilterChain 동작 원리

```
HTTP 요청
    │
    ▼
DispatcherServlet
    │
    ├─ GET /oauth2/authorize ──▶ Chain 1 (securityMatcher 매칭)
    ├─ POST /oauth2/token    ──▶ Chain 1
    ├─ GET /userinfo         ──▶ Chain 1
    │
    ├─ POST /oauth2/login    ──▶ Chain 2 (Chain 1 미매칭)
    ├─ GET /login            ──▶ Chain 2
    └─ 기타                  ──▶ Chain 2
```

### @Order의 중요성

```java
// @Order 숫자가 작을수록 먼저 적용
// Chain 1 (@Order(1)) → Chain 2 (@Order(2))
// securityMatcher()가 없으면 모든 요청을 처리하려 함 → 충돌!

// 주의: applyDefaultSecurity()는 내부적으로 securityMatcher를 설정하므로
// OAuth2 엔드포인트 이외의 요청은 Chain 2로 자동으로 넘어감
```

---

## 4. RegisteredClientRepository - 클라이언트 관리

### RegisteredClient 구조

```java
RegisteredClient.withId(UUID.randomUUID().toString())
    .clientId("web-client")                              // 클라이언트 식별자
    .clientSecret("{bcrypt}$2a$...")                     // 시크릿 (BCrypt 인코딩)
    .clientAuthenticationMethod(CLIENT_SECRET_BASIC)     // 인증 방법
    .authorizationGrantType(AUTHORIZATION_CODE)          // 지원 그랜트 타입
    .redirectUri("https://app.example.com/callback")     // 허용 redirect URI
    .scope(OidcScopes.OPENID)                            // 지원 스코프
    .clientSettings(ClientSettings.builder()
        .requireAuthorizationConsent(true)               // 동의 화면 표시
        .requireProofKey(true)                           // PKCE 필수 (보안 강화)
        .build())
    .tokenSettings(TokenSettings.builder()
        .accessTokenTimeToLive(Duration.ofHours(1))
        .refreshTokenTimeToLive(Duration.ofDays(1))
        .reuseRefreshTokens(false)                       // 보안: 매번 새 RT 발급
        .build())
    .build();
```

### epki-auth의 커스텀 RegisteredClientRepository 패턴

```java
// EpkiRegisteredClientRepository implements RegisteredClientRepository
// → DB에서 클라이언트 조회 후 비즈니스 검증 추가

public RegisteredClient findByClientId(String clientId) {
    Client entity = repository.findByClientId(clientId)
            .orElseThrow(() -> new NoSuchClientIdException(clientId));

    validate(entity);  // ← 비즈니스 검증

    return clientHelper.toClient(entity, repository);
}

private void validate(Client client) {
    // 1. apiKeyStatusCode가 "01"이 아니면 차단 (비활성 클라이언트)
    if (!"01".equals(client.getApiKeyStatusCode())) {
        throw new NotAllowedStatusException(client.getClientId());
    }

    // 2. 프로덕션에서 테스트 키 차단
    if ("Y".equals(client.getTestKeyYn()) && profile.equals("prd")) {
        throw new NotAllowedKeyException(client.getClientId());
    }

    // 3. 만료 + 1일 grace period 이후 차단
    Instant expiredDate = client.getClientSecretExpiresAt().plus(1, ChronoUnit.DAYS);
    if (Instant.now().isAfter(expiredDate)) {
        throw new KeyExpiredException(client.getClientId());
    }
}
```

### ClientEntity ↔ RegisteredClient 변환

```
RegisteredClient (Spring AS 도메인 객체)
    ↕ ClientHelper
ClientEntity (JPA 엔티티 = DB 테이블)

변환이 복잡한 이유:
  - ClientSettings, TokenSettings: Map<String, Object> → JSON 문자열
  - ClientAuthenticationMethods: Set → 콤마 구분 문자열
  - AuthorizationGrantTypes: Set → 콤마 구분 문자열
  - Jackson 특수 모듈 필요 (OAuth2AuthorizationServerJackson2Module)
```

---

## 5. OAuth2AuthorizationService - 토큰 저장소

### OAuth2Authorization 생명주기

```
[Authorization Code 흐름]

1. GET /oauth2/authorize (사용자 인증 성공)
   → save(Authorization { state=xxx, authCode=yyy })

2. POST /oauth2/token?code=yyy (코드 교환)
   → findByToken(yyy, CODE)
   → save(Authorization { accessToken=aaa, refreshToken=bbb })

3. GET /resource (API 호출)
   → findByToken(aaa, ACCESS_TOKEN)  ← 리소스 서버 검증

4. POST /oauth2/token?refresh_token=bbb (토큰 갱신)
   → findByToken(bbb, REFRESH_TOKEN)
   → save(Authorization { accessToken=ccc, refreshToken=ddd }) ← reuseRefreshTokens=false

5. POST /oauth2/revoke (토큰 폐기)
   → remove(Authorization)
```

### epki-auth의 Redis 기반 구현 vs 이 모듈의 JPA 기반 구현

```java
// epki-auth: Redis 저장소
@Repository
public interface AuthorizationRepository extends CrudRepository<Authorization, String> {
    Optional<Authorization> findByState(String state);
    Optional<Authorization> findByAuthorizationCodeValue(String code);
    Optional<Authorization> findByAccessTokenValue(String token);
    Optional<Authorization> findByRefreshTokenValue(String token);
    Optional<Authorization> findByOidcIdTokenValue(String token);
}
// Authorization 엔티티: @RedisHash + @Indexed + @TimeToLive

// 이 모듈: JPA(H2) 저장소
@Repository
public interface AuthorizationJpaRepository extends JpaRepository<AuthorizationEntity, String> {
    Optional<AuthorizationEntity> findByState(String state);
    // ...동일 메서드
}
```

### Jackson 모듈 등록이 필요한 이유

```java
// OAuth2Authorization.attributes에는 복잡한 Spring Security 객체들이 저장됨:
// - EpkiAuthenticationToken (사용자 인증 정보)
// - Map<String, Object> (추가 속성)

// 기본 Jackson으로는 역직렬화 불가 → 전용 모듈 등록 필요
objectMapper.registerModules(SecurityJackson2Modules.getModules(classLoader));
objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());

// epki-auth: 커스텀 토큰도 Mixin으로 등록
objectMapper.addMixIn(EpkiAuthenticationToken.class, EpkiAuthenticationTokenMixin.class);
```

### Redis가 더 적합한 이유 (실무 관점)

| 항목 | Redis | JPA(DB) |
|------|-------|---------|
| TTL 자동 삭제 | ✅ 내장 | ❌ 배치 필요 |
| 성능 | ✅ 인메모리 | ⚠️ 네트워크 I/O |
| 수평 확장 | ✅ 클러스터 | ⚠️ 복잡 |
| 영속성 | ⚠️ 선택적 | ✅ 기본 제공 |

---

## 6. 커스텀 인증 필터 + 프로바이더 패턴

### epki-auth의 EPKI 인증 흐름

```
외부 EPKI 인증 시스템
    │ 인증서 검증 완료
    │ authInfo(cn, name, instCode) → Redis에 저장
    │ authCode 발급
    │
    ▼ (브라우저 redirect)
POST /oauth2/authenticate?authCode=xxx
    │
    ▼
EpkiAuthenticationFilter (AbstractAuthenticationProcessingFilter 상속)
    │ validateRequest() - POST만 허용, Referer 도메인 검증
    │ authCode 파라미터 추출
    │ AuthCodeValidator → Redis에서 authCode로 AuthInfo 조회+삭제
    │ EpkiAuthenticationToken(cn, name, instCode) 생성 (미인증)
    │
    ▼
ProviderManager
    │ supports(EpkiAuthenticationToken.class) == true
    │
    ▼
EpkiAuthenticationProvider
    │ EpkiPrincipal(cn, name, instCode) 생성
    │ EpkiAuthenticationToken(principal) 반환 (인증완료)
    │
    ▼
SecurityContext에 저장
    │
    ▼
SavedRequestAwareAuthenticationSuccessHandler
    → 원래 /oauth2/authorize로 redirect
```

### AbstractAuthenticationProcessingFilter가 제공하는 것

```java
// 직접 구현 필요: attemptAuthentication()
public Authentication attemptAuthentication(request, response) throws AuthenticationException {
    // 1. 요청 검증
    // 2. 파라미터 추출
    // 3. 미인증 토큰 생성
    // 4. AuthenticationManager.authenticate() 호출
    return getAuthenticationManager().authenticate(token);
}

// 자동으로 처리됨:
// - requiresAuthentication(): URL 매처로 이 필터가 처리할지 결정
// - successfulAuthentication(): SecurityContext 저장 + successHandler 호출
// - unsuccessfulAuthentication(): SecurityContext 초기화 + failureHandler 호출
```

### AuthenticationProvider.supports() 의 중요성

```java
// ProviderManager는 등록된 모든 Provider를 순서대로 확인
// → supports(authentication.getClass()) == true인 Provider에만 위임

public boolean supports(Class<?> authentication) {
    // CustomAuthenticationToken과 그 서브클래스만 처리
    return CustomAuthenticationToken.class.isAssignableFrom(authentication);
}

// 결과:
// - CustomAuthenticationToken → CustomAuthenticationProvider 처리
// - UsernamePasswordAuthenticationToken → DaoAuthenticationProvider 처리
// - 해당 Provider 없음 → ProviderNotFoundException
```

---

## 7. JWT 토큰 커스터마이징

### 기본 JWT 클레임 (Spring AS 자동 추가)

```json
{
  "iss": "http://localhost:9090",
  "sub": "user001",          // principalName
  "aud": ["web-client"],
  "exp": 1700000000,
  "iat": 1699996400,
  "jti": "uuid-...",
  "scope": "openid read"
}
```

### 커스텀 클레임 추가 (OAuth2TokenCustomizer)

```java
@Component
public class CustomTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    @Override
    public void customize(JwtEncodingContext context) {
        // Access Token에만 커스텀 클레임 추가
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) return;

        Authentication principal = context.getPrincipal();

        // Authorization Code Grant: 사용자 정보 추가
        if (principal instanceof CustomAuthenticationToken token
                && token.getPrincipal() instanceof CustomUserPrincipal user) {

            context.getClaims().claims(claims -> {
                claims.put("user_id", user.userId());
                claims.put("username", user.username());
                claims.put("role", user.role());
            });
        }
        // Client Credentials: 추가 클레임 없음 (서비스 간 통신)
    }
}
```

### epki-auth의 JWT 커스터마이징

```java
// JwtTokenCustomizerService - EPKI 인증서 정보를 JWT에 추가
public void customize(JwtEncodingContext context) {
    if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
        context.getClaims().claims((claims) -> {
            Authentication authentication = context.getPrincipal();
            if (authentication.getPrincipal() instanceof EpkiPrincipal epkiPrincipal) {
                claims.put("name", epkiPrincipal.name());        // 사용자 실명
                claims.put("cn", epkiPrincipal.cn());           // 인증서 CN (식별자)
                claims.put("instCode", epkiPrincipal.instCode()); // 기관 코드
            }
        });
    }
}
```

### 결과 JWT

```json
{
  "iss": "http://localhost:9090",
  "sub": "홍길동",
  "exp": 1700000000,
  "user_id": "user001",
  "username": "홍길동",
  "role": "ROLE_USER",
  "scope": "openid read"
}
```

---

## 8. OIDC UserInfo 엔드포인트

### 흐름

```
클라이언트
  │
  │ GET /userinfo
  │ Authorization: Bearer {access_token}
  │
  ▼
Chain 1 (oauth2ResourceServer.jwt())
  │ access_token 검증 (JWKSource로 서명 검증)
  │ JwtAuthenticationToken 생성
  │
  ▼
OidcUserInfoEndpointFilter
  │ OidcUserInfoAuthenticationToken 생성
  │
  ▼
userInfoMapper.apply(context)
  │ context.getAuthentication() → OidcUserInfoAuthenticationToken
  │ authentication.getPrincipal() → JwtAuthenticationToken
  │ principal.getToken().getClaims() → Map<String, Object>
  │
  ▼
OidcUserInfo(claims) 반환
  │
  ▼
JSON 응답:
{
  "sub": "user001",
  "username": "홍길동",
  "role": "ROLE_USER",
  ...
}
```

### UserInfoMapper 구현

```java
@Bean
public Function<OidcUserInfoAuthenticationContext, OidcUserInfo> userInfoMapper() {
    return context -> {
        OidcUserInfoAuthenticationToken authentication = context.getAuthentication();
        JwtAuthenticationToken principal = (JwtAuthenticationToken) authentication.getPrincipal();

        // JWT access_token의 클레임을 그대로 UserInfo로 반환
        // → CustomTokenCustomizer에서 넣은 user_id, username, role이 포함됨
        return new OidcUserInfo(principal.getToken().getClaims());
    };
}
```

### SecurityConfig에 등록

```java
http.with(configurer, authServer -> authServer
    .oidc(oidc -> oidc
        .userInfoEndpoint(ui -> ui.userInfoMapper(userInfoMapper))
    )
);
```

---

## 9. 실무 적용 패턴 (epki-auth)

### 전체 아키텍처 다이어그램

```
┌─────────────────────────────────────────────────────────────────┐
│                      epki-auth (AS)                             │
│                                                                 │
│  ┌────────────────┐    ┌─────────────────┐    ┌─────────────┐   │
│  │ LoginAccessFilter│  │ EpkiAuth Filter  │  │OAuth2Logging│   │
│  │ (login 접근 검증)│  │ (EPKI 인증 처리)  │  │Filter (로깅)│   │
│  └────────────────┘    └─────────────────┘    └─────────────┘   │
│                               │                                  │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │ SecurityFilterChain (Chain 1 - OAuth2 AS)                 │   │
│  │   /oauth2/authorize  /oauth2/token  /oauth2/introspect    │   │
│  │   /userinfo  /connect/logout  /.well-known/*              │   │
│  └───────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │ SecurityFilterChain (Chain 2 - Default)                   │   │
│  │   /oauth2/authenticate  /login.html  /error               │   │
│  └───────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────┐    ┌──────────────────┐                    │
│  │ PostgreSQL       │    │ Redis            │                    │
│  │ (tb_epls_client) │    │ (Authorization,  │                    │
│  │ (API Call Logs)  │    │  Session,        │                    │
│  │                  │    │  AuthCode)       │                    │
│  └─────────────────┘    └──────────────────┘                    │
└─────────────────────────────────────────────────────────────────┘
```

### epki-auth 보안 검증 체계

```
요청 수신
    │
    ├─ Chain 1 (OAuth2 엔드포인트)
    │     │
    │     └─ OAuth2LoginAuthenticationEntryPoint
    │          - 미인증 시 /login 페이지로 redirect
    │          - HMAC-SHA256 서명 토큰 생성 (LoginAccessTokenGenerator)
    │          - 서명 토큰을 redirect URL에 포함
    │
    ├─ Chain 2 (로그인 페이지 등)
    │     │
    │     ├─ LoginAccessFilter
    │     │    - HttpSession에 SavedRequest 존재 확인
    │     │    - /oauth2/authorize를 통해서만 로그인 페이지 접근 허용
    │     │    (직접 접근 차단)
    │     │
    │     └─ EpkiAuthenticationFilter
    │          - POST 메서드 검증
    │          - Referer 헤더 도메인 검증
    │          - authCode 파라미터 → Redis 조회+삭제
    │          - EpkiAuthenticationToken 생성
    │
    └─ EpkiRegisteredClientRepository (클라이언트 검증)
          - apiKeyStatusCode == "01"
          - testKeyYn != "Y" (프로덕션)
          - secretExpiresAt + 1일 > now()
```

### OAuth2 로깅 패턴

```java
// epki-auth: OpenTelemetry 트레이스 + DB 로그
@NewSpan  // 새 OpenTelemetry span 시작
protected void doFilterInternal(request, response, filterChain) {
    String traceId = tracer.currentSpan().context().spanId();
    String clientId = clientIdService.extract(request);
    long start = System.currentTimeMillis();

    filterChain.doFilter(request, response);

    if (OAuth2Endpoint.match(requestURI)) {
        long duration = System.currentTimeMillis() - start;
        ApiCallLogModel model = ApiCallLogModel.onSuccess(request, response, clientId, duration);
        logService.save(model);  // DB 저장
    }
}

// 저장 정보:
// - linkServiceId (client ID)
// - traceId (OpenTelemetry span ID)
// - httpMethod, path, statusCode
// - elapseHour (요청 처리 시간 ms)
// - requestAt
```

### 환경별 설정 (application-{profile}.yml)

```yaml
# application-dev.yml
epki:
  token-secret: epki-authenticate-secret-saas
  auth:
    oauth2:
      authorization-ttl: 43200      # 12시간 (Redis TTL)
      refresh-token-timeout: 43200   # 12시간
      access-token-timeout: 120      # 2분 (개발 시 짧게)
      jwk:
        file-path: /saas/auth/jwk/epki-jwk.json  # 고정 JWK 파일

# application-prd.yml
epki:
  auth:
    oauth2:
      access-token-timeout: 3600    # 1시간 (프로덕션)
```

---

## 10. 테스트 가이드

### 서버 실행

```bash
# spring-deep-dive 루트에서
./gradlew :oauth2-authorization:bootRun

# 또는 IntelliJ에서 OAuth2AuthorizationApplication 실행
```

### 엔드포인트 확인

```bash
# OIDC Discovery 문서 (모든 엔드포인트 URL 포함)
curl http://localhost:9090/.well-known/openid-configuration

# JWK Set (공개키)
curl http://localhost:9090/oauth2/jwks

# H2 Console (DB 직접 조회)
# http://localhost:9090/h2-console
# JDBC URL: jdbc:h2:mem:oauth2db
```

### Client Credentials 흐름 테스트

```bash
# 1. Access Token 발급
curl -X POST http://localhost:9090/oauth2/token \
  -u service-client:service-secret \
  -d "grant_type=client_credentials&scope=read"

# 응답:
# {
#   "access_token": "eyJ...",
#   "token_type": "Bearer",
#   "expires_in": 1800,
#   "scope": "read"
# }

# 2. 토큰 디코딩 (jwt.io에서 확인)
# Header: {"alg":"RS256","kid":"..."}
# Payload: {"iss":"http://localhost:9090","sub":"service-client",...}
```

### Authorization Code 흐름 테스트

```bash
# 1. 브라우저에서 Authorization 요청
# http://localhost:9090/oauth2/authorize?
#   client_id=web-client&
#   response_type=code&
#   scope=openid+read&
#   redirect_uri=http://localhost:8080/authorized&
#   state=random-state-value

# 2. /login 페이지로 redirect (Spring Security 기본 폼)
#    → admin / admin123 으로 로그인 (폼 로그인)
#    또는 커스텀 authCode 로그인:

# 커스텀 authCode 로그인 (브라우저 세션 유지 필요)
curl -X POST "http://localhost:9090/oauth2/login?authCode=test-code-001" \
  -H "Referer: http://localhost:9090" \
  -c cookies.txt -b cookies.txt \
  -v

# 3. 동의 화면 후 redirect_uri로 code 수신
# http://localhost:8080/authorized?code=AUTH_CODE&state=...

# 4. 토큰 교환
curl -X POST http://localhost:9090/oauth2/token \
  -u web-client:web-secret \
  -d "grant_type=authorization_code" \
  -d "code=AUTH_CODE" \
  -d "redirect_uri=http://localhost:8080/authorized"

# 5. Refresh Token으로 갱신
curl -X POST http://localhost:9090/oauth2/token \
  -u web-client:web-secret \
  -d "grant_type=refresh_token" \
  -d "refresh_token=REFRESH_TOKEN"

# 6. UserInfo 조회 (openid 스코프 포함 시)
curl http://localhost:9090/userinfo \
  -H "Authorization: Bearer ACCESS_TOKEN"

# 7. 토큰 인트로스펙션
curl -X POST http://localhost:9090/oauth2/introspect \
  -u web-client:web-secret \
  -d "token=ACCESS_TOKEN"

# 8. 토큰 폐기
curl -X POST http://localhost:9090/oauth2/revoke \
  -u web-client:web-secret \
  -d "token=REFRESH_TOKEN"
```

### 에러 케이스 테스트

```bash
# 비활성 클라이언트 (CLIENT_INACTIVE)
# → ClientDataInitializer에서 inactive-client를 직접 H2 Console에서 active=false로 수정 후
curl -X POST http://localhost:9090/oauth2/token \
  -u inactive-client:inactive-secret \
  -d "grant_type=client_credentials"

# 잘못된 authCode (INVALID_AUTH_CODE)
curl -X POST "http://localhost:9090/oauth2/login?authCode=wrong-code" \
  -H "Referer: http://localhost:9090" \
  -c cookies.txt -b cookies.txt

# Referer 없는 요청 (INVALID_REFERER)
curl -X POST "http://localhost:9090/oauth2/login?authCode=test-code-001"
# → Referer 헤더 없어서 BadCredentialsException
```

### H2 Console 활용

```sql
-- 저장된 클라이언트 확인
SELECT client_id, client_name, active, client_secret_expires_at
FROM oauth2_client;

-- 발급된 Authorization 확인
SELECT id, registered_client_id, principal_name, authorization_grant_type
FROM oauth2_authorization;

-- 비활성 클라이언트 테스트 (검증 로직 확인)
UPDATE oauth2_client SET active = false WHERE client_id = 'inactive-client';
```

---

## 핵심 정리

### OAuth2 Authorization Server 3대 구성 요소

```
1. RegisteredClientRepository
   "어떤 클라이언트(앱)이 이 AS를 사용할 수 있는가?"
   → 클라이언트 등록, 검증, 설정 관리

2. OAuth2AuthorizationService
   "현재 진행 중인 인가 흐름과 발급된 토큰을 어디에 저장하는가?"
   → 인가 코드, 액세스 토큰, 리프레시 토큰 저장/조회

3. JWKSource
   "JWT를 어떤 키로 서명하는가?"
   → RSA 키 쌍 관리, /oauth2/jwks 엔드포인트 제공
```

### 실무에서 커스텀이 필요한 지점

```
표준 구현 (InMemory/JDBC)     커스텀이 필요한 이유
────────────────────────────────────────────────────
RegisteredClientRepository  → 비즈니스 검증 (만료, 상태, 환경별 제한)
OAuth2AuthorizationService  → Redis TTL 자동 삭제, 성능 최적화
Authentication (Filter+Provider) → 비밀번호 이외의 인증 방식 (EPKI 인증서, MFA 등)
OAuth2TokenCustomizer       → 도메인별 JWT 클레임 (userId, role, instCode 등)
UserInfoMapper              → OIDC UserInfo를 JWT 클레임에서 매핑
```
