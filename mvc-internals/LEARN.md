# Spring MVC Internals: HandlerMethodArgumentResolver

## 1. 개요
컨트롤러 메서드의 파라미터는 누가 채워줄까요?
`@RequestParam`, `@RequestBody`, `@PathVariable` 등은 모두 Spring MVC 내부의 **ArgumentResolver**들이 동작한 결과입니다.

이 기능을 직접 확장하면, 반복되는 코드(로그인 사용자 조회, 공통 헤더 파싱 등)를 획기적으로 줄일 수 있습니다.

## 2. 핵심 인터페이스
`HandlerMethodArgumentResolver` 인터페이스를 구현해야 합니다.

1. `supportsParameter(MethodParameter parameter)`:
   - 현재 파라미터를 이 Resolver가 처리할 수 있는지 확인합니다.
   - 보통 어노테이션(`@LoginUser`) 존재 여부와 타입(`UserSession`)을 체크합니다.

2. `resolveArgument(...)`:
   - 실제 파라미터에 들어갈 객체를 생성해서 반환합니다.
   - `NativeWebRequest`를 통해 `HttpServletRequest`에 접근할 수 있습니다.

## 3. 실습 내용

### 목표
`@LoginUser` 어노테이션이 붙은 파라미터에, HTTP 헤더(`X-User-Id`)를 기반으로 생성된 `UserSession` 객체를 자동으로 주입합니다.

### 코드 흐름
1. 클라이언트 요청: `GET /me` (Header: `X-User-Id: 100`)
2. Spring MVC: `UserController.getMe(@LoginUser UserSession user)` 호출 시도
3. `LoginUserArgumentResolver`: 
   - `@LoginUser` 확인 -> `supportsParameter` 통과
   - 헤더에서 "100" 추출 -> `UserSession(100, ...)` 생성 -> 반환
4. 컨트롤러: 주입된 `user` 객체 사용

## 4. 실행 방법
`src/test/java/com/exam/mvc/ArgumentResolverTest.java`를 실행하여 검증하세요.

## 5. 활용 팁
- **JWT 인증:** `Authorization` 헤더의 토큰을 파싱하여 유저 정보를 주입할 때 가장 많이 사용됩니다.
- **페이징/검색 조건:** 복잡한 쿼리 파라미터를 하나의 객체로 묶어서 받을 때도 유용합니다. (`@PageableDefault`가 이런 원리입니다)
