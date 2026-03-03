# Session & Cookie Deep Dive

## 목차
1. [Session 기본](#1-session-기본)
2. [Cookie 기본](#2-cookie-기본)
3. [Session vs Cookie 비교](#3-session-vs-cookie-비교)
4. [Session 고급: Redis 기반 분산 세션](#4-session-고급-redis-기반-분산-세션)
5. [Cookie 보안: 서명과 암호화](#5-cookie-보안-서명과-암호화)
6. [실무 패턴](#6-실무-패턴)

---

## 1. Session 기본

### 세션이란?
세션(Session)은 **서버 측에 저장되는 사용자별 데이터 저장소**입니다. 클라이언트는 세션 ID만 쿠키로 받고, 실제 데이터는 서버 메모리(또는 Redis)에 저장됩니다.

```
클라이언트                     서버
   │                           │
   │─── 로그인 요청 ────────────►│
   │                           │ 세션 생성 (메모리 또는 Redis)
   │◄─ Set-Cookie: JSESSIONID=abc123 ──│
   │                           │
   │─── 요청 (Cookie: JSESSIONID=abc123) ──►│
   │                           │ 세션 조회 → 사용자 식별
   │◄─── 응답 ─────────────────│
```

### HttpSession 기본 사용법

```java
// 1. 세션 생성
HttpSession session = request.getSession(true);  // true: 없으면 생성
session.setAttribute("username", "user123");

// 2. 세션 조회
String username = (String) session.getAttribute("username");

// 3. 세션 삭제 (로그아웃)
session.invalidate();
```

### 세션 생명주기

| 메서드 | 설명 |
|--------|------|
| `getCreationTime()` | 세션 생성 시간 (ms) |
| `getLastAccessedTime()` | 마지막 접근 시간 |
| `getMaxInactiveInterval()` | 타임아웃 (초) |
| `setMaxInactiveInterval(int seconds)` | 타임아웃 설정 |
| `isNew()` | 새로 생성된 세션인지 확인 |

### Spring의 편리한 어노테이션

```java
// @SessionAttribute: 메서드 파라미터로 세션 값 읽기
@GetMapping("/user")
public String getUser(@SessionAttribute(value = "username", required = false) String username) {
    return "현재 사용자: " + username;
}
```

### Session Scoped Bean

```java
@Component
@SessionScope
public class UserSessionData {
    private String username;
    private int accessCount;
}

// 컨트롤러에서 자동 주입
@RestController
public class MyController {
    private final UserSessionData userSessionData;

    public MyController(UserSessionData userSessionData) {
        this.userSessionData = userSessionData;
    }
}
```

- **세션마다 별도의 빈 인스턴스**가 생성됨
- 세션이 유지되는 동안 상태 보관 가능

---

## 2. Cookie 기본

### 쿠키란?
쿠키(Cookie)는 **클라이언트(브라우저)에 저장되는 작은 데이터 조각**입니다. 서버가 `Set-Cookie` 헤더로 전송하면, 브라우저는 이후 모든 요청에 자동으로 쿠키를 포함합니다.

```
클라이언트                     서버
   │                           │
   │─── 요청 ───────────────────►│
   │                           │
   │◄─ Set-Cookie: username=john; Max-Age=3600 ──│
   │                           │
   │─── 요청 (Cookie: username=john) ──►│
   │                           │
```

### Cookie 기본 생성

```java
Cookie cookie = new Cookie("username", "john_doe");
cookie.setMaxAge(3600);    // 1시간 (초 단위)
cookie.setPath("/");        // 모든 경로에서 접근 가능
response.addCookie(cookie);
```

### Cookie 속성

| 속성 | 설명 | 보안 효과 |
|------|------|----------|
| `MaxAge` | 쿠키 수명 (초). -1이면 세션 쿠키 | - |
| `Path` | 쿠키를 전송할 경로 | 경로 제한 |
| `Domain` | 쿠키를 전송할 도메인 | 도메인 제한 |
| `HttpOnly` | JavaScript에서 접근 불가 | **XSS 방어** |
| `Secure` | HTTPS에서만 전송 | **중간자 공격 방어** |
| `SameSite` | 교차 사이트 요청 시 쿠키 전송 제한 | **CSRF 방어** |

### SameSite 속성 상세

```java
// Strict: 동일한 사이트에서만 전송 (가장 안전)
Set-Cookie: token=abc; SameSite=Strict

// Lax: 안전한 메서드(GET)로 이동 시 전송
Set-Cookie: token=abc; SameSite=Lax

// None: 모든 요청에 전송 (Secure 필수)
Set-Cookie: token=abc; SameSite=None; Secure
```

### 쿠키 읽기

```java
// 1. 모든 쿠키 조회
Cookie[] cookies = request.getCookies();
for (Cookie cookie : cookies) {
    System.out.println(cookie.getName() + "=" + cookie.getValue());
}

// 2. @CookieValue 어노테이션
@GetMapping("/user")
public String getUser(@CookieValue(value = "username", required = false) String username) {
    return "User: " + username;
}
```

### 쿠키 삭제

```java
Cookie cookie = new Cookie("username", null);
cookie.setMaxAge(0);      // 즉시 만료
cookie.setPath("/");       // 원본과 동일한 Path 필요
response.addCookie(cookie);
```

---

## 3. Session vs Cookie 비교

| 구분 | Session | Cookie |
|------|---------|--------|
| **저장 위치** | 서버 (메모리/Redis) | 클라이언트 (브라우저) |
| **용량** | 제한 없음 (메모리 허용 범위) | 4KB 제한 |
| **보안** | 높음 (서버 내부) | 낮음 (클라이언트 노출) |
| **속도** | 느림 (서버 조회 필요) | 빠름 (브라우저에서 바로 전송) |
| **서버 부하** | 높음 (메모리 사용) | 낮음 |
| **수명** | 타임아웃 또는 명시적 삭제 | MaxAge 또는 브라우저 종료 |
| **클러스터 환경** | Redis 등 외부 저장소 필요 | 자동 전달 (추가 설정 불필요) |

### 선택 가이드

```
세션 사용:
- 민감한 정보 (사용자 권한, 개인정보)
- 대용량 데이터
- 보안이 중요한 경우

쿠키 사용:
- 비민감한 설정 (테마, 언어)
- 로그인 상태 유지 (Remember-Me)
- 서버 부하를 줄이고 싶을 때
```

---

## 4. Session 고급: Redis 기반 분산 세션

### 문제: 단일 서버 세션의 한계

```
[서버 A] - 메모리 세션
[서버 B] - 메모리 세션

로드밸런서
     ↓
클라이언트 요청 1 → 서버 A (세션 생성)
클라이언트 요청 2 → 서버 B (세션 없음!) ❌
```

### 해결: Spring Session + Redis

```
[서버 A] ─┐
           └─► [Redis] ◄── 모든 서버가 공유
[서버 B] ─┘

클라이언트 요청 1 → 서버 A → Redis에 세션 저장
클라이언트 요청 2 → 서버 B → Redis에서 세션 조회 ✅
```

### 설정

```java
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800)
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}
```

```yaml
# application.yml
spring:
  session:
    store-type: redis
  data:
    redis:
      host: localhost
      port: 6379
```

### 세션 검색 및 관리

```java
@RestController
@RequiredArgsConstructor
public class SessionManagementController {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    // 특정 사용자의 모든 세션 조회
    @GetMapping("/sessions/{username}")
    public Map<String, ? extends Session> getUserSessions(@PathVariable String username) {
        return sessionRepository.findByPrincipalName(username);
    }

    // 특정 사용자의 모든 세션 강제 종료 (보안)
    @DeleteMapping("/sessions/{username}")
    public void revokeAllSessions(@PathVariable String username) {
        Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(username);
        sessions.keySet().forEach(sessionRepository::deleteById);
    }
}
```

### 동시 로그인 제한 (Concurrent Session Control)

```java
@PostMapping("/login")
public String login(@RequestParam String username) {
    Map<String, ? extends Session> existingSessions = sessionRepository.findByPrincipalName(username);

    int MAX_SESSIONS = 2;
    if (existingSessions.size() >= MAX_SESSIONS) {
        // 가장 오래된 세션 제거
        String oldestSessionId = existingSessions.entrySet().stream()
            .min(Comparator.comparing(e -> e.getValue().getLastAccessedTime()))
            .map(Map.Entry::getKey)
            .orElse(null);

        if (oldestSessionId != null) {
            sessionRepository.deleteById(oldestSessionId);
        }
    }

    // 새 세션 생성
    HttpSession session = request.getSession(true);
    session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, username);

    return "로그인 성공";
}
```

### 세션 고정 공격 방어 (Session Fixation Protection)

```java
@PostMapping("/login-secure")
public String loginSecure(@RequestParam String username) {
    // 기존 세션 무효화
    HttpSession oldSession = request.getSession(false);
    if (oldSession != null) {
        oldSession.invalidate();
    }

    // 새 세션 생성 (새로운 세션 ID)
    HttpSession newSession = request.getSession(true);
    newSession.setAttribute("username", username);

    return "새로운 세션 ID로 로그인 완료";
}
```

### 세션 하이재킹 탐지

```java
@PostMapping("/create-secure-session")
public String createSecureSession(@RequestParam String username) {
    HttpSession session = request.getSession(true);

    // 보안 정보 저장
    session.setAttribute("username", username);
    session.setAttribute("userAgent", request.getHeader("User-Agent"));
    session.setAttribute("ipAddress", request.getRemoteAddr());

    return "보안 세션 생성 완료";
}

@GetMapping("/validate-session")
public String validateSession() {
    HttpSession session = request.getSession(false);

    String storedUserAgent = (String) session.getAttribute("userAgent");
    String currentUserAgent = request.getHeader("User-Agent");

    if (!storedUserAgent.equals(currentUserAgent)) {
        session.invalidate();
        throw new SecurityException("세션 하이재킹 의심");
    }

    return "세션 유효";
}
```

---

## 5. Cookie 보안: 서명과 암호화

### 쿠키 보안 체크리스트

| 위협 | 설명 | 방어 방법 |
|------|------|----------|
| **XSS** | JavaScript로 쿠키 탈취 | `HttpOnly` |
| **중간자 공격** | HTTP 스니핑 | `Secure` (HTTPS) |
| **CSRF** | 다른 사이트에서 쿠키 자동 전송 | `SameSite=Strict` |
| **쿠키 변조** | 클라이언트가 값 수정 | **서명** 또는 **암호화** |

### 쿠키 서명 (Cookie Signing)

**목적:** 쿠키가 변조되었는지 검증

```java
// 1. 서명 생성 (HMAC-SHA256)
String signature = generateHmacSignature(value);
String signedValue = value + "." + signature;

Cookie cookie = new Cookie("signedCookie", URLEncoder.encode(signedValue, UTF_8));
cookie.setHttpOnly(true);
response.addCookie(cookie);

// 2. 서명 검증
String decodedValue = URLDecoder.decode(cookie.getValue(), UTF_8);
String[] parts = decodedValue.split("\\.");
String originalValue = parts[0];
String providedSignature = parts[1];

String expectedSignature = generateHmacSignature(originalValue);
if (!expectedSignature.equals(providedSignature)) {
    throw new SecurityException("쿠키가 변조되었습니다!");
}

// HMAC 생성
private String generateHmacSignature(String value) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    SecretKeySpec key = new SecretKeySpec(SECRET_KEY.getBytes(), "HmacSHA256");
    mac.init(key);
    byte[] hmacBytes = mac.doFinal(value.getBytes());
    return Base64.getEncoder().encodeToString(hmacBytes);
}
```

### 쿠키 암호화 (Cookie Encryption)

**목적:** 쿠키 내용을 읽을 수 없게 암호화

```java
// AES 암호화
private String encrypt(String value) throws Exception {
    SecretKeySpec key = new SecretKeySpec(SECRET_KEY.getBytes(), "AES");
    Cipher cipher = Cipher.getInstance("AES");
    cipher.init(Cipher.ENCRYPT_MODE, key);
    byte[] encryptedBytes = cipher.doFinal(value.getBytes());
    return Base64.getEncoder().encodeToString(encryptedBytes);
}

// AES 복호화
private String decrypt(String encryptedValue) throws Exception {
    SecretKeySpec key = new SecretKeySpec(SECRET_KEY.getBytes(), "AES");
    Cipher cipher = Cipher.getInstance("AES");
    cipher.init(Cipher.DECRYPT_MODE, key);
    byte[] decodedBytes = Base64.getDecoder().decode(encryptedValue);
    byte[] decryptedBytes = cipher.doFinal(decodedBytes);
    return new String(decryptedBytes);
}

// 사용
String encryptedValue = encrypt("sensitive-data");
Cookie cookie = new Cookie("encryptedCookie", URLEncoder.encode(encryptedValue, UTF_8));
response.addCookie(cookie);
```

### 서명 vs 암호화

| 비교 | 서명 (Signing) | 암호화 (Encryption) |
|------|----------------|---------------------|
| 목적 | 변조 방지 | 내용 숨김 + 변조 방지 |
| 내용 가독성 | 볼 수 있음 (Base64) | 볼 수 없음 |
| 성능 | 빠름 | 느림 |
| 사용 사례 | 사용자 ID, 비민감 데이터 | 비밀번호, 카드번호 등 |

### 쿠키 Prefix (__Secure-, __Host-)

```java
// __Secure- Prefix: Secure 속성 강제
Set-Cookie: __Secure-token=abc123; Secure; HttpOnly; SameSite=Strict

// __Host- Prefix: 가장 강력한 보안
// - Secure 필수
// - Domain 설정 불가 (현재 호스트만)
// - Path는 반드시 /
Set-Cookie: __Host-token=abc123; Path=/; Secure; HttpOnly; SameSite=Strict
```

### CSRF 토큰 쿠키

```java
// CSRF 토큰 생성
String csrfToken = UUID.randomUUID().toString();

// JavaScript에서 읽을 수 있어야 함 (HttpOnly X)
Cookie csrfCookie = new Cookie("XSRF-TOKEN", csrfToken);
csrfCookie.setPath("/");
csrfCookie.setSecure(true);
response.addCookie(csrfCookie);

// 검증
@PostMapping("/api/submit")
public String submit(
    @CookieValue("XSRF-TOKEN") String csrfCookie,
    @RequestHeader("X-XSRF-TOKEN") String csrfHeader) {

    if (!csrfCookie.equals(csrfHeader)) {
        throw new SecurityException("CSRF 토큰 불일치");
    }

    return "성공";
}
```

---

## 6. 실무 패턴

### 패턴 1: 로그인 상태 유지 (Remember-Me)

```java
@PostMapping("/login")
public String login(@RequestParam boolean rememberMe) {
    String token = UUID.randomUUID().toString();
    tokenStore.put(token, username);

    Cookie authCookie = new Cookie("authToken", token);
    authCookie.setPath("/");
    authCookie.setHttpOnly(true);
    authCookie.setSecure(true);

    if (rememberMe) {
        authCookie.setMaxAge(30 * 24 * 60 * 60); // 30일
    } else {
        authCookie.setMaxAge(-1); // 세션 쿠키
    }

    response.addCookie(authCookie);
    return "로그인 성공";
}
```

### 패턴 2: 세션 + 쿠키 하이브리드

```
세션: 인증 정보 (서버 측)
쿠키: 사용자 설정 (클라이언트 측)

// 서버 측 (세션)
session.setAttribute("userId", 12345);
session.setAttribute("role", "ADMIN");

// 클라이언트 측 (쿠키)
Cookie themeCookie = new Cookie("theme", "dark");
Cookie langCookie = new Cookie("language", "ko");
```

### 패턴 3: JWT vs Session

| 비교 | Session | JWT |
|------|---------|-----|
| 저장 위치 | 서버 | 클라이언트 |
| Stateful | ✅ | ❌ (Stateless) |
| 확장성 | Redis 필요 | 수평 확장 쉬움 |
| 즉시 무효화 | 가능 | 어려움 (블랙리스트 필요) |
| 크기 | 작음 (ID만) | 큼 (모든 정보 포함) |

```java
// Session 방식
HttpSession session = request.getSession();
session.setAttribute("userId", 123);
session.setAttribute("role", "ADMIN");

// JWT 방식
String jwt = Jwts.builder()
    .setSubject("user123")
    .claim("role", "ADMIN")
    .signWith(key)
    .compact();

Cookie jwtCookie = new Cookie("jwt", jwt);
jwtCookie.setHttpOnly(true);
jwtCookie.setSecure(true);
response.addCookie(jwtCookie);
```

### 패턴 4: 비밀번호 변경 시 모든 세션 종료

```java
@PostMapping("/change-password")
public String changePassword(@RequestParam String username, @RequestParam String newPassword) {
    // 비밀번호 변경 로직
    userService.changePassword(username, newPassword);

    // 모든 디바이스에서 로그아웃
    Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(username);
    sessions.keySet().forEach(sessionRepository::deleteById);

    return "비밀번호가 변경되었습니다. 모든 디바이스에서 로그아웃되었습니다.";
}
```

### 패턴 5: 세션 타임아웃 슬라이딩 윈도우

```java
@GetMapping("/api/protected")
public String protectedResource(HttpServletRequest request) {
    HttpSession session = request.getSession(false);

    if (session != null) {
        // 마지막 활동 시간 갱신
        session.setAttribute("lastActivity", System.currentTimeMillis());

        // 타임아웃 연장 (슬라이딩 윈도우)
        session.setMaxInactiveInterval(1800); // 30분
    }

    return "보호된 리소스";
}
```

---

## 7. 실습 가이드

### 실습 1: 기본 세션 CRUD
```bash
# 세션 생성
curl -X POST http://localhost:8080/api/session/basic/create

# 세션 조회
curl -b cookies.txt http://localhost:8080/api/session/basic/info

# 세션 무효화
curl -X POST -b cookies.txt http://localhost:8080/api/session/basic/invalidate
```

### 실습 2: 기본 쿠키 CRUD
```bash
# 쿠키 생성
curl -X POST http://localhost:8080/api/cookie/basic/create

# 쿠키 조회
curl -b cookies.txt http://localhost:8080/api/cookie/basic/all

# 쿠키 삭제
curl -X DELETE http://localhost:8080/api/cookie/basic/delete/username
```

### 실습 3: Redis 세션 (분산 환경)
```bash
# Redis 실행
docker run -d -p 6379:6379 redis

# Redis 세션 생성
curl -X POST http://localhost:8080/api/session/redis/create

# 사용자명으로 세션 검색
curl http://localhost:8080/api/session/redis/find-by-username/redis-user
```

### 실습 4: 쿠키 서명 및 검증
```bash
# 서명된 쿠키 생성
curl -X POST "http://localhost:8080/api/cookie/signing/create-signed?name=userToken&value=user123"

# 서명 검증
curl -b cookies.txt http://localhost:8080/api/cookie/signing/verify/userToken
```

### 실습 5: 보안 쿠키
```bash
# 보안 쿠키 생성
curl -X POST http://localhost:8080/api/cookie/security/create-secure

# 쿠키 보안 체크
curl -b cookies.txt http://localhost:8080/api/cookie/security/security-checklist/username
```

---

## 8. 트러블슈팅

### 문제 1: 세션이 유지되지 않음

```
원인: 쿠키의 Domain 또는 Path 불일치
해결:
Cookie cookie = new Cookie("JSESSIONID", sessionId);
cookie.setPath("/");  // 모든 경로에서 접근 가능
cookie.setDomain(".example.com");  // 서브도메인 포함
```

### 문제 2: Redis 세션이 저장되지 않음

```
원인: @EnableRedisHttpSession 누락
해결:
@Configuration
@EnableRedisHttpSession
public class RedisConfig { ... }
```

### 문제 3: HTTPS에서 쿠키가 전송되지 않음

```
원인: Secure 속성 미설정
해결:
cookie.setSecure(true);
```

### 문제 4: CSRF 토큰 불일치

```
원인: SameSite=Strict 설정으로 인한 교차 사이트 요청 차단
해결: SameSite=Lax 또는 None 사용
```

---

## 9. 성능 최적화

### Redis 세션 최적화

```yaml
spring:
  session:
    redis:
      flush-mode: on-save  # 즉시 저장 (기본값)
      # flush-mode: immediate  # 즉시 저장 + 동기화
```

### 세션 직렬화 최적화

```java
// GenericJackson2JsonRedisSerializer 대신 JdkSerializationRedisSerializer 사용
@Bean
public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
    return new JdkSerializationRedisSerializer();
}
```

### 쿠키 크기 최적화

```java
// ❌ 나쁜 예: 모든 정보를 쿠키에 저장
Cookie bigCookie = new Cookie("userInfo", "id=123;name=John;email=john@example.com;...");

// ✅ 좋은 예: ID만 쿠키에 저장, 나머지는 서버 세션
Cookie smallCookie = new Cookie("userId", "123");
```

---

## 10. 참고 자료

- Spring Session 공식 문서: https://spring.io/projects/spring-session
- MDN Cookie 가이드: https://developer.mozilla.org/ko/docs/Web/HTTP/Cookies
- OWASP Session Management: https://owasp.org/www-community/Session_Management_Cheat_Sheet
- Redis 공식 문서: https://redis.io/docs/
