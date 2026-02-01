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

## 3. 실습 내용

### 커스텀 로깅 필터 (`ChainLoggingFilter`)
필터 체인의 맨 앞(`Filter-Start`)과 맨 뒤(`Filter-End`)에 필터를 배치하여, 요청이 어떻게 흘러가는지 로그로 확인합니다.

### JSON 로그인 필터 (`JsonLoginFilter`)
`AbstractAuthenticationProcessingFilter`를 상속받아 구현했습니다.
컨트롤러 없이 필터 레벨에서 `POST /api/login` 요청을 가로채서 인증을 수행합니다.

### 인증 실패 핸들링 (`CustomAuthenticationEntryPoint`)
REST API에 맞게 **JSON 형식의 401 응답**을 내려주도록 구현했습니다.

## 4. 실행 방법
`src/test/java/com/exam/securityjwt/SecurityChainTest.java`를 실행하세요.
