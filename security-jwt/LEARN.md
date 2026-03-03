# Spring Security Deep Dive

## 1. Security Filter Chain의 이해
Spring Security는 거대한 **필터 체인(Filter Chain)**입니다.
요청이 들어오면 약 15개 이상의 필터를 순차적으로 통과하며 인증(Authentication)과 인가(Authorization)를 수행합니다.

### 핵심 필터 순서
1. `SecurityContextHolderFilter`: 세션 등에서 저장된 인증 정보를 복구합니다.
2. `UsernamePasswordAuthenticationFilter`: 폼 로그인을 처리합니다. (우리는 JWT 필터를 이 앞에 배치)
3. `ExceptionTranslationFilter`: 필터 체인에서 발생한 예외(`AuthenticationException`, `AccessDeniedException`)를 잡아서 처리합니다.
4. `AuthorizationFilter`: 최종적으로 이 요청이 권한이 있는지 검사합니다.

## 2. 필터 종류와 상속 가이드 (Best Practice)

### (1) `OncePerRequestFilter` (가장 많이 사용)
- **용도:** 모든 요청에 대해 **단 한 번** 실행되어야 하는 로직 (JWT 검증, 로깅, CORS 등)
- **특징:** `GenericFilterBean`을 상속받아, 서블릿 포워딩 시 중복 실행되는 문제를 해결함.
- **예시:** `JwtAuthenticationFilter`, `ChainLoggingFilter`

### (2) `AbstractAuthenticationProcessingFilter` (로그인 전용)
- **용도:** **"로그인 시도"** (ID/PW 검증)만을 위한 필터.
- **특징:** 
    - 특정 URL(예: `/login`)로 들어온 요청만 가로챔 (`RequestMatcher`).
    - `AuthenticationManager`를 호출하여 인증을 시도함 (`attemptAuthentication`).
    - 성공 시 `successHandler`, 실패 시 `failureHandler`를 자동으로 호출하는 템플릿 메서드 패턴이 적용됨.
- **예시:** `JsonLoginFilter` (우리가 만든 것), `UsernamePasswordAuthenticationFilter` (기본 폼 로그인)

### (3) `GenericFilterBean`
- **용도:** Spring Bean 기능을 사용하는 일반 필터.
- **주의:** `OncePerRequestFilter`가 더 안전하므로 잘 안 씀.

## 3. JWT 구조와 동작 원리

JWT(JSON Web Token)는 `Header.Payload.Signature` 세 부분을 `.`으로 연결한 자가 포함(Self-Contained) 토큰입니다.

```
eyJhbGciOiJIUzI1NiJ9  .  eyJzdWIiOiJhZG1pbiIsImF1dGgiOiJST0xFX0FETUlOIiwiaWF0IjoxNzA4...  .  [Signature]
└──── Header (Base64) ─┘  └──────────────────── Payload (Base64) ──────────────────────────┘  └── HMAC ──┘
```

| 부분 | 내용 | 비고 |
|------|------|------|
| Header | `{"alg": "HS256", "typ": "JWT"}` | 알고리즘, 토큰 타입 |
| Payload | `{"sub": "admin", "auth": "ROLE_ADMIN", "iat": ..., "exp": ...}` | Claims (서명 대상) |
| Signature | `HMAC256(Base64(Header) + "." + Base64(Payload), secretKey)` | 무결성 검증 |

> **중요:** Payload는 Base64 인코딩일 뿐 암호화가 아닙니다. 비밀번호, 카드번호 같은 민감 정보를 절대 포함하지 마세요.

### 인증 흐름 다이어그램

```
클라이언트                                  서버
    │                                        │
    │─── POST /auth/login ──────────────────►│
    │    { "username": "admin", "password" } │ AuthController
    │                                        │   → JwtTokenProvider.createToken()
    │◄── 200 OK { "token": "eyJ..." } ──────│
    │                                        │
    │─── GET /api/admin ────────────────────►│
    │    Authorization: Bearer eyJ...        │ JwtAuthenticationFilter
    │                                        │   1. resolveToken()  → "eyJ..." 추출
    │                                        │   2. validateToken() → 서명/만료 검증
    │                                        │   3. getAuthentication() → 권한 복원
    │                                        │   4. SecurityContextHolder.set()
    │                                        │
    │                                        │ @PreAuthorize("hasRole('ADMIN')")
    │◄── 200 OK "Admin Content" ────────────│
    │                                        │
    │─── GET /api/admin (토큰 만료) ─────────►│
    │                                        │ JwtException 발생
    │                                        │ CustomAuthenticationEntryPoint
    │◄── 401 { "error": "Unauthorized" } ───│
```

### 토큰 검증 내부 구조 (JwtTokenProvider)

```java
// 생성: HS256 + 1시간 유효
return Jwts.builder()
    .setSubject(username)
    .claim("auth", role)   // "ROLE_ADMIN" 또는 "ROLE_USER"
    .setIssuedAt(new Date())
    .setExpiration(new Date(now + 3600000))
    .signWith(key)         // Keys.secretKeyFor(HS256)
    .compact();

// 검증: 서명 불일치 또는 만료 시 JwtException
Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);

// 권한 복원: Claims → GrantedAuthority → SecurityContext
Claims claims = parser.parseClaimsJws(token).getBody();
String auth = claims.get("auth").toString(); // "ROLE_ADMIN"
```

---

## 4. 토큰 갱신 전략 (Refresh Token Pattern)

현재 구현은 Access Token(1시간)만 사용합니다. 실무에서는 보안과 사용자 경험의 균형을 위해 Refresh Token 패턴을 함께 사용합니다.

```
Access Token  (짧은 유효기간: 15분 ~ 1시간) → 실제 API 요청에 사용
Refresh Token (긴 유효기간: 7일 ~ 30일)    → Access Token 재발급 전용
```

### 갱신 흐름

```
1. 로그인 → Access Token(15분) + Refresh Token(7일) 발급
2. API 요청 → Access Token 헤더에 포함
3. Access Token 만료(401) → POST /auth/refresh + Refresh Token 전송
4. 서버: Refresh Token 검증 → 새 Access Token 발급
5. Refresh Token도 만료 → 재로그인 요구
```

### 토큰 저장 위치 비교

| 위치 | XSS 취약 | CSRF 취약 | 비고 |
|------|---------|----------|------|
| `localStorage` | ❌ 취약 | ✅ 안전 | JS로 직접 접근 가능 |
| `sessionStorage` | ❌ 취약 | ✅ 안전 | 탭 종료 시 삭제 |
| `HttpOnly Cookie` | ✅ 안전 | ❌ 취약 | CSRF Token으로 대응 가능 |
| Memory (JS 변수) | ✅ 안전 | ✅ 안전 | 새로고침 시 소실 |

> **실무 권장:** Access Token → 메모리(JS 변수), Refresh Token → HttpOnly Cookie

---

## 5. 보안 주의사항 및 안티패턴

### ❌ 안티패턴

```java
// 1. 비밀 키를 코드에 하드코딩 → 유출 시 모든 토큰 위조 가능
private final Key key = Keys.hmacShaKeyFor("my-secret-key-1234".getBytes()); // ❌

// 2. 만료 시간을 너무 길게 → 탈취 시 오래 사용 가능
private final long validity = 30L * 24 * 60 * 60 * 1000; // ❌ 30일

// 3. Payload에 민감 정보 포함 → Base64는 암호화가 아님
.claim("password", user.getPassword())  // ❌
.claim("ssn", user.getSocialSecurityNumber()) // ❌
```

### ✅ 실무 패턴

```java
// 1. 비밀 키를 환경변수 또는 Vault에서 로드
@Value("${jwt.secret}") private String secretKey;
Key key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));

// 2. 짧은 Access Token + Refresh Token 전략
// access: 15분, refresh: 7일 (Redis에 저장하여 강제 만료 가능)

// 3. 로그아웃 처리: 블랙리스트 방식
// Redis에 만료된 Access Token 저장 → 요청 시 블랙리스트 확인
```

### @PreAuthorize 활용 패턴 (본 모듈 구현)

```java
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")         // USER 또는 ADMIN
@PreAuthorize("hasRole('ADMIN')")                     // ADMIN만
@PreAuthorize("hasRole('ADMIN') and #username == authentication.name") // 역할 + 파라미터 조건
```

---

## 6. 실습 내용

### 커스텀 로깅 필터 (`ChainLoggingFilter`)
필터 체인의 맨 앞(`Filter-Start`)과 맨 뒤(`Filter-End`)에 필터를 배치하여, 요청이 어떻게 흘러가는지 로그로 확인합니다.

### JSON 로그인 필터 (`JsonLoginFilter`)
`AbstractAuthenticationProcessingFilter`를 상속받아 구현했습니다.
컨트롤러 없이 필터 레벨에서 `POST /api/login` 요청을 가로채서 인증을 수행합니다.

### 인증 실패 핸들링 (`CustomAuthenticationEntryPoint`)
REST API에 맞게 **JSON 형식의 401 응답**을 내려주도록 구현했습니다.

## 4. 실행 방법
`src/test/java/com/exam/securityjwt/SecurityChainTest.java`를 실행하세요.
