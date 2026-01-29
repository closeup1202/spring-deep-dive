# Spring Boot 테스트 실무 가이드 (Mockito & MockMvc)

단위 테스트(Unit Test)와 슬라이스 테스트(Slice Test)를 통해 견고한 애플리케이션을 만드는 방법을 학습합니다.

## 1. Mockito를 이용한 Service 단위 테스트 (`MemberServiceTest`)
*   **목적**: DB나 외부 의존성 없이 **순수 비즈니스 로직**만 빠르게 검증합니다.
*   **`@ExtendWith(MockitoExtension.class)`**: JUnit 5에서 Mockito를 사용하기 위한 필수 설정입니다.
*   **`@Mock`**: 가짜 객체(Repository)를 만듭니다. 실제 DB에 연결되지 않습니다.
*   **`@InjectMocks`**: `@Mock`으로 만든 가짜 객체들을 테스트 대상 객체(Service)에 주입해줍니다.
*   **BDD 스타일**:
    *   `given(...)`: 시나리오 설정 (Stubbing)
    *   `when(...)`: 테스트 대상 실행
    *   `then(...)`: 결과 검증 (`assertThat`, `verify`)

## 2. MockMvc를 이용한 Controller 테스트 (`MemberControllerTest`)
*   **목적**: 서버를 띄우지 않고 **HTTP 요청/응답** 처리 로직(파라미터 바인딩, JSON 변환, 상태 코드)을 검증합니다.
*   **`@WebMvcTest`**: 웹 계층(Controller, Filter 등)만 로드하므로 `@SpringBootTest`보다 훨씬 빠릅니다.
*   **`@MockBean`**: Spring Context에 가짜 Bean을 등록하여 Controller가 의존하는 Service를 대체합니다.
*   **`mockMvc.perform(...)`**: 가상의 HTTP 요청을 보냅니다.
*   **`andExpect(...)`**: 응답 상태 코드(`status()`)와 JSON 본문(`jsonPath`)을 검증합니다.

## 3. 테스트 피라미터 (Test Pyramid)
1.  **Unit Test (단위 테스트)**: 가장 많아야 함. 빠르고 격리됨. (Mockito 활용)
2.  **Integration Test (통합 테스트)**: DB, Redis 등 실제 인프라와 연동하여 검증. (Testcontainers 활용)
3.  **E2E Test (인수 테스트)**: 사용자 관점에서 전체 시나리오 검증. (RestAssured 등 활용)

이 모듈에서는 **1번(Mockito)**과 **Controller 테스트(MockMvc)**에 집중했습니다.
