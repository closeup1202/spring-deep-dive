# Spring Security & JWT 심화

Spring Security의 필터 체인 구조를 이해하고, JWT(JSON Web Token)를 이용한 Stateless 인증 시스템을 직접 구현합니다.

## 1. 핵심 개념

### Security Filter Chain
Spring Security는 서블릿 필터(Servlet Filter) 기반으로 동작합니다. 수많은 필터들이 체인처럼 연결되어 요청을 처리하는데, 우리는 이 체인 중간에 **커스텀 인증 필터(`JwtAuthenticationFilter`)**를 끼워 넣습니다.

### JWT (JSON Web Token)
*   **Stateless**: 서버가 세션을 유지하지 않아도 되므로 확장성(Scale-out)에 유리합니다.
*   **구조**: Header.Payload.Signature
*   **검증**: 서버는 비밀키(Secret Key)를 사용하여 서명(Signature)을 검증함으로써 토큰의 위변조 여부를 확인합니다.

## 2. 구현 구조

1.  **`JwtTokenProvider`**: 토큰 생성, 검증, 정보 추출을 담당하는 유틸리티 클래스입니다.
    *   **Role 포함**: 토큰 생성 시 `auth` 클레임에 사용자 권한(ROLE_USER, ROLE_ADMIN)을 저장합니다.
2.  **`JwtAuthenticationFilter`**:
    *   모든 요청의 헤더(`Authorization`)를 검사합니다.
    *   유효한 JWT가 있으면 `Authentication` 객체를 만들어 `SecurityContextHolder`에 저장합니다.
3.  **`SecurityConfig`**:
    *   `@EnableMethodSecurity`: `@PreAuthorize` 어노테이션을 활성화합니다.
    *   `SessionCreationPolicy.STATELESS`: 세션을 사용하지 않도록 설정합니다.

## 3. 권한 제어 (@PreAuthorize)

메서드 단위로 정교한 권한 제어를 수행합니다.

*   `@PreAuthorize("hasRole('ADMIN')")`: 관리자 권한을 가진 사용자만 메서드를 실행할 수 있습니다.
*   `@PreAuthorize("hasAnyRole('USER', 'ADMIN')")`: 일반 사용자나 관리자 모두 실행 가능합니다.
*   SpEL(Spring Expression Language)을 사용하여 파라미터 값 검증 등 복잡한 조건도 처리할 수 있습니다.

## 4. 실행 및 테스트 방법

1.  **일반 사용자 로그인**
    *   `POST /auth/login` (Body: `{"username": "user1"}`)
    *   Role: `ROLE_USER` 발급됨

2.  **관리자 로그인**
    *   `POST /auth/login` (Body: `{"username": "admin"}`)
    *   Role: `ROLE_ADMIN` 발급됨

3.  **권한 테스트**
    *   **일반 사용자 토큰**으로 `/api/admin` 호출 -> **403 Forbidden** (접근 거부)
    *   **관리자 토큰**으로 `/api/admin` 호출 -> **200 OK** ("Admin Content")
    *   **일반 사용자 토큰**으로 `/api/user` 호출 -> **200 OK** ("User Content")
