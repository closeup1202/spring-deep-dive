# Spring Web 기초: Hello, World!

Spring MVC를 사용하여 가장 기본적인 웹 API 서버를 만드는 방법을 학습합니다.

## 1. Spring MVC의 핵심 동작 원리 (DispatcherServlet)

Spring Web 애플리케이션은 **DispatcherServlet**이라는 중앙 서블릿을 통해 모든 웹 요청을 받습니다.

1.  **요청 접수**: 클라이언트로부터 HTTP 요청이 들어오면, `DispatcherServlet`이 가장 먼저 요청을 받습니다.
2.  **핸들러 매핑(Handler Mapping)**: `DispatcherServlet`은 요청 URL, HTTP 메서드 등을 보고 이 요청을 처리할 컨트롤러 메서드(`Handler`)를 찾습니다. (예: `/hello` 요청을 `HelloController.hello()` 메서드에 매핑)
3.  **컨트롤러 실행**: 찾은 컨트롤러의 메서드를 실행합니다.
4.  **View 또는 ResponseBody 처리**:
    *   `@RestController`가 붙은 컨트롤러는 메서드의 반환값을 HTTP 응답 본문(Response Body)에 직접 씁니다. (주로 JSON, TEXT 등)
    *   `@Controller`가 붙은 컨트롤러는 보통 `View`의 이름을 반환하고, `ViewResolver`가 해당 View(예: HTML 템플릿)를 렌더링하여 응답합니다.

## 2. 주요 어노테이션

### @SpringBootApplication
*   이 어노테이션 하나로 다음 세 가지 기능을 모두 활성화합니다.
    1.  `@SpringBootConfiguration`: 스프링 부트 설정을 나타냅니다.
    2.  `@EnableAutoConfiguration`: 클래스패스에 있는 라이브러리들을 기반으로 필요한 설정을 **자동으로** 구성합니다. (예: `spring-boot-starter-web`이 있으면 Tomcat, DispatcherServlet 등을 자동으로 설정)
    3.  `@ComponentScan`: 이 클래스가 있는 패키지 및 하위 패키지의 `@Component`, `@Service`, `@Repository`, `@Controller` 등을 스캔하여 Bean으로 등록합니다.

### @RestController
*   `@Controller`와 `@ResponseBody`를 합친 어노테이션입니다.
*   이 어노테이션이 붙은 클래스의 모든 메서드는 반환값을 View가 아닌 **HTTP 응답 본문**에 직접 쓰게 됩니다. API 서버를 만들 때 주로 사용합니다.

### @GetMapping("/hello")
*   HTTP GET 요청을 특정 URL 경로(`/hello`)와 매핑하는 역할을 합니다.
*   `@PostMapping`, `@PutMapping`, `@DeleteMapping` 등 다양한 HTTP 메서드에 맞는 어노테이션이 존재합니다.

### @RequestParam
*   URL의 쿼리 파라미터(Query Parameter)를 메서드의 파라미터로 바인딩합니다.
*   예시: `/hello?name=Spring` 요청이 오면, `name` 파라미터에 "Spring"이라는 값이 담깁니다.
*   `defaultValue` 속성을 사용하여 파라미터가 없을 때의 기본값을 지정할 수 있습니다.

## 3. 실행 및 확인
1.  `BasicWebApplication`을 실행합니다.
2.  웹 브라우저나 `curl` 같은 도구를 사용하여 다음 URL에 접속합니다.
    *   `http://localhost:8080/hello` -> "Hello, World!" 출력
    *   `http://localhost:8080/hello?name=John` -> "Hello, John!" 출력
