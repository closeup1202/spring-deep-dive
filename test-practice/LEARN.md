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
*   **`@MockitoBean`**: Spring Context에 가짜 Bean을 등록하여 Controller가 의존하는 Service를 대체합니다. (Spring Boot 3.4+ 권장)
*   **`mockMvc.perform(...)`**: 가상의 HTTP 요청을 보냅니다.
*   **`andExpect(...)`**: 응답 상태 코드(`status()`)와 JSON 본문(`jsonPath`)을 검증합니다.

## 3. 테스트 피라미터 (Test Pyramid)
1.  **Unit Test (단위 테스트)**: 가장 많아야 함. 빠르고 격리됨. (Mockito 활용)
2.  **Integration Test (통합 테스트)**: DB, Redis 등 실제 인프라와 연동하여 검증. (Testcontainers 활용)
3.  **E2E Test (인수 테스트)**: 사용자 관점에서 전체 시나리오 검증. (RestAssured 등 활용)

이 모듈에서는 **1번(Mockito)**과 **Controller 테스트(MockMvc)**에 집중했습니다.

---

## 부록 1: Mockito vs @MockitoBean vs MockMvc 완벽 정리

이름이 비슷해서 헷갈리기 쉽지만, **출신과 역할**이 완전히 다릅니다.

| 이름 | 소속 | 역할 | 주로 쓰는 곳 |
| :--- | :--- | :--- | :--- |
| **Mockito** | Mockito 라이브러리 | 자바 세상에서 가짜 객체(Mock)를 만들고 조종하는 도구 | 모든 테스트 |
| **`@Mock`** | Mockito | 순수 자바 객체 Mock 생성 | **단위 테스트** (Service 등) |
| **`@MockitoBean`** | Spring Boot (3.4+) | **스프링 Bean**을 Mock으로 교체하여 컨텍스트에 등록 (최신 표준) | **통합/컨트롤러 테스트** |
| **`@MockBean`** | Spring Boot (구버전) | `@MockitoBean`의 옛날 이름 (Deprecated 예정) | **통합/컨트롤러 테스트** |
| **MockMvc** | Spring Framework | **가짜 HTTP 요청**을 보내는 브라우저 역할 | **컨트롤러 테스트** |

### 요약
*   **`@Mock`**: "나는 스프링 몰라. 그냥 자바 객체 가짜로 만들래." (가볍고 빠름)
*   **`@MockitoBean`**: "나는 스프링 컨텍스트에 있는 진짜 Bean을 쫓아내고 그 자리에 앉을래." (무겁고 느림, `@MockBean`의 최신 버전)
*   **`MockMvc`**: "나는 서버 안 띄우고 컨트롤러한테 요청 보낼래."

---

## 부록 2: 실무 Stubbing 패턴 BEST 5 (BDD 스타일)

**1. 기본 값 반환**
```java
// findById 호출 시 member 반환
given(repository.findById(1L)).willReturn(Optional.of(member));
```

**2. 예외 발생 (에러 테스트)**
```java
// save 호출 시 예외 발생
given(repository.save(any())).willThrow(new RuntimeException("Error"));
```

**3. Void 메서드 예외 발생 (★주의★)**
*   `void` 메서드는 `given()` 안에 넣을 수 없으므로 순서를 뒤집어야 합니다.
```java
// send 호출 시 예외 발생
willThrow(new RuntimeException("Fail")).given(emailService).send(any());
```

**4. 아무 값이나 허용 (Argument Matchers)**
```java
// 어떤 Long 값이 들어오든 empty 반환
given(repository.findById(anyLong())).willReturn(Optional.empty());
```

**5. 인자를 그대로 반환 (Answer)**
*   JPA `save()`처럼 넣은 객체를 그대로 리턴받고 싶을 때 사용합니다.
```java
// 첫 번째 인자(Argument[0])를 그대로 리턴
given(repository.save(any()))
    .willAnswer(invocation -> invocation.getArgument(0));
```

---

## 부록 3: 실무 Verify 패턴 완벽 가이드

Mockito의 `verify`는 단순히 "호출되었는지"만 확인하는 게 아닙니다. **몇 번 호출되었는지, 어떤 순서로 호출되었는지, 실제로 어떤 값이 전달되었는지**까지 검증할 수 있습니다.

### 1. 기본 Verify: 호출 횟수 검증

#### times(N) - 정확히 N번 호출
```java
verify(repository, times(1)).save(any()); // 정확히 1번
verify(repository, times(2)).findById(1L); // 정확히 2번
```
**실무 팁**: `times(1)`은 생략 가능합니다. `verify(repository).save(any())`만 써도 1번 호출을 검증합니다.

#### never() - 한 번도 호출되지 않음
```java
verify(repository, never()).delete(any()); // 절대 호출되면 안 됨
```
**실무 예시**: 회원가입 실패 시 이메일이 발송되지 않았는지 검증할 때 사용

#### atLeastOnce() / atLeast(N) - 최소 N번
```java
verify(repository, atLeastOnce()).findById(anyLong()); // 최소 1번
verify(repository, atLeast(2)).save(any()); // 최소 2번
```
**실무 예시**: 재시도 로직이 있을 때 "최소한 몇 번은 호출되어야 한다" 검증

#### atMost(N) - 최대 N번
```java
verify(emailService, atMost(3)).sendNotification(anyString()); // 최대 3번
```
**실무 예시**: 스팸 방지 로직에서 "최대 N번까지만 호출되어야 한다" 검증

---

### 2. InOrder - 호출 순서 검증 (실무 필수!)

**왜 필요한가?**
실무에서는 **순서가 중요한 로직**이 많습니다.
- 회원가입: 중복 체크 → 저장 → 이메일 발송
- 결제: 재고 차감 → 결제 진행 → 알림 발송
- 트랜잭션: 조회 → 수정 → 저장

```java
InOrder inOrder = inOrder(repository, emailService);
inOrder.verify(repository).findByEmail(email);    // 1. 중복 체크
inOrder.verify(repository).save(any());           // 2. 저장
inOrder.verify(emailService).sendWelcomeEmail(email, name); // 3. 이메일 발송
```

**실무 팁**: 순서가 뒤바뀌면 테스트가 실패합니다. 비즈니스 로직의 순서를 명확히 검증할 수 있습니다.

---

### 3. ArgumentCaptor - 실제 전달된 값 검증 (실무 핵심!)

**왜 필요한가?**
`any()`, `anyString()` 같은 Argument Matcher는 편하지만, **실제로 어떤 값이 전달되었는지는 검증하지 못합니다.**

ArgumentCaptor를 사용하면 **실제 전달된 객체를 캡처하여 세밀하게 검증**할 수 있습니다.

#### 기본 사용법
```java
ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
verify(repository).save(captor.capture());

Member capturedMember = captor.getValue();
assertThat(capturedMember.getEmail()).isEqualTo("test@test.com");
assertThat(capturedMember.getName()).isEqualTo("Tester");
```

#### 여러 번 호출 시 모든 값 캡처
```java
ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
verify(repository, times(3)).save(captor.capture());

List<Member> allCaptured = captor.getAllValues();
assertThat(allCaptured).hasSize(3);
assertThat(allCaptured.get(0).getName()).isEqualTo("User1");
assertThat(allCaptured.get(1).getName()).isEqualTo("User2");
```

**실무 예시**:
- 이메일 발송 시 실제 제목과 내용이 제대로 전달되었는지 검증
- 결제 API 호출 시 실제 금액과 상품명이 맞는지 검증
- 이벤트 발행 시 실제 이벤트 데이터가 올바른지 검증

---

### 4. verifyNoMoreInteractions - 추가 호출 검증

명시적으로 검증한 것 외에 **다른 호출이 없는지** 확인합니다.

```java
verify(repository).count();
verifyNoMoreInteractions(repository); // count 외에 다른 호출이 있으면 실패
```

**주의**: 너무 엄격해서 실무에서는 자주 쓰이지 않습니다. 특정 상황에서만 사용하세요.

---

## 부록 4: 고급 Stubbing 패턴 (실무 활용도 높음)

### 1. Answer - 동적으로 반환값 생성

`willReturn()`은 고정된 값을 반환하지만, `willAnswer()`는 **호출 시점에 동적으로 값을 생성**할 수 있습니다.

#### 인자를 그대로 반환 (JPA save 패턴)
```java
given(repository.save(any(Member.class)))
    .willAnswer(invocation -> {
        Member member = invocation.getArgument(0);
        return new Member(99L, member.getEmail(), member.getName());
    });
```

#### 호출 횟수에 따라 다른 값 반환
```java
given(repository.count())
    .willReturn(100L)  // 첫 번째 호출
    .willReturn(200L)  // 두 번째 호출
    .willReturn(300L); // 세 번째 호출
```

**실무 예시**:
- 첫 번째 API 호출은 실패하고, 재시도 시 성공하는 시나리오
- 페이징 테스트에서 페이지마다 다른 데이터 반환

---

### 2. doReturn vs given - 차이점 이해하기

#### given (권장)
```java
given(repository.findById(1L)).willReturn(Optional.of(member));
```
- BDD 스타일로 읽기 쉽고 직관적
- **단점**: Mock 객체가 아닌 Spy 객체나 실제 객체에서는 메서드를 한 번 호출함

#### doReturn (특수 케이스)
```java
doReturn(Optional.of(member)).when(repository).findById(1L);
```
- 실제 메서드를 호출하지 않고 바로 값을 반환
- **언제 사용?**: Spy 객체이거나, 실제 메서드 호출이 부작용을 일으킬 때

**실무 팁**: 일반적으로는 `given`을 사용하고, Spy 객체를 다룰 때만 `doReturn`을 사용하세요.

---

### 3. Void 메서드 Stubbing

#### 정상 동작 (willDoNothing)
```java
willDoNothing().given(emailService).sendEmail(anyString());
```
기본적으로 void 메서드는 아무것도 하지 않지만, 명시적으로 표현할 수 있습니다.

#### 예외 발생 (willThrow) - 실무 필수!
```java
willThrow(new RuntimeException("Email service is down"))
    .given(emailService).sendWelcomeEmail(anyString(), anyString());
```

**실무 예시**:
- 외부 API 호출 실패 시나리오 (이메일, SMS, 결제 등)
- 파일 저장 실패, 네트워크 에러 등

**주의**: void 메서드는 `given()` 안에 넣을 수 없습니다! `willThrow().given()` 순서를 지켜야 합니다.

---

## 부록 5: 실무에서 자주 쓰는 패턴 TOP 7

### 1. 회원가입 + 이메일 발송 (외부 API 호출)
```java
@Test
void signupWithEmailSending() {
    // given
    given(repository.findByEmail(email)).willReturn(Optional.empty());
    given(repository.save(any())).willReturn(savedMember);

    // when
    service.signup(email, name);

    // then
    verify(emailService, times(1)).sendWelcomeEmail(email, name);
}
```

### 2. 중복 체크 실패 시 save 호출되지 않음
```java
@Test
void duplicateEmailShouldNotSave() {
    given(repository.findByEmail(email)).willReturn(Optional.of(existingMember));

    assertThatThrownBy(() -> service.signup(email, name))
        .isInstanceOf(IllegalArgumentException.class);

    verify(repository, never()).save(any());
}
```

### 3. 외부 API 실패 시 예외 처리
```java
@Test
void emailServiceFailure() {
    given(repository.save(any())).willReturn(savedMember);
    willThrow(new RuntimeException("Email service down"))
        .given(emailService).sendWelcomeEmail(anyString(), anyString());

    assertThatThrownBy(() -> service.signup(email, name))
        .isInstanceOf(RuntimeException.class);
}
```

### 4. 여러 번 호출 시 다른 결과 반환 (재시도 로직)
```java
@Test
void retryLogic() {
    given(externalApi.call())
        .willThrow(new RuntimeException("Fail"))  // 1차 실패
        .willReturn("Success");                   // 2차 성공

    String result = service.callWithRetry();
    assertThat(result).isEqualTo("Success");
}
```

### 5. 순서 검증 (결제 프로세스)
```java
@Test
void paymentProcessOrder() {
    InOrder inOrder = inOrder(inventoryService, paymentService, notificationService);

    service.processPayment(orderId);

    inOrder.verify(inventoryService).decreaseStock(productId);
    inOrder.verify(paymentService).charge(amount);
    inOrder.verify(notificationService).sendConfirmation(email);
}
```

### 6. ArgumentCaptor로 이벤트 발행 검증
```java
@Test
void eventPublishing() {
    service.createOrder(orderRequest);

    ArgumentCaptor<OrderCreatedEvent> captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
    verify(eventPublisher).publish(captor.capture());

    OrderCreatedEvent event = captor.getValue();
    assertThat(event.getOrderId()).isEqualTo(orderId);
    assertThat(event.getAmount()).isEqualTo(10000);
}
```

### 7. 리스트 조회 + 필터링 검증
```java
@Test
void findActiveMembers() {
    List<Member> members = Arrays.asList(
        new Member(1L, "active@test.com", "Active"),
        new Member(2L, "inactive@test.com", "Inactive")
    );
    given(repository.findAll()).willReturn(members);

    List<Member> result = service.getActiveMembers();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getEmail()).isEqualTo("active@test.com");
}
```

---

## 부록 6: 실무 테스트 체크리스트

테스트 작성 시 아래 항목을 체크하세요:

- [ ] **정상 케이스**: 모든 조건이 만족될 때 올바르게 동작하는가?
- [ ] **예외 케이스**: 중복, null, 빈 값 등 예외 상황을 처리하는가?
- [ ] **외부 의존성**: 이메일, 결제 등 외부 API 호출이 제대로 이루어지는가?
- [ ] **호출 횟수**: 의도한 만큼만 호출되었는가? (너무 많거나 적지 않은가?)
- [ ] **호출 순서**: 비즈니스 로직의 순서가 올바른가?
- [ ] **전달 값**: 실제로 올바른 값이 전달되었는가? (ArgumentCaptor)
- [ ] **실패 시나리오**: 외부 API 실패, 네트워크 에러 등을 처리하는가?

---

## 부록 7: Argument Matchers 완전 정리

Mockito에서 자주 사용하는 Argument Matcher 목록입니다.

| Matcher | 설명 | 예시 |
|---------|------|------|
| `any()` | 모든 객체 허용 | `verify(repo).save(any())` |
| `any(Class)` | 특정 타입의 모든 객체 | `verify(repo).save(any(Member.class))` |
| `anyString()` | 모든 String | `verify(email).send(anyString())` |
| `anyLong()` | 모든 Long | `verify(repo).findById(anyLong())` |
| `anyInt()` | 모든 int | `verify(service).process(anyInt())` |
| `anyList()` | 모든 List | `verify(repo).saveAll(anyList())` |
| `eq(value)` | 특정 값과 일치 | `verify(repo).findById(eq(1L))` |
| `isNull()` | null 값 | `verify(service).handle(isNull())` |
| `isNotNull()` | null이 아닌 값 | `verify(service).handle(isNotNull())` |
| `contains(str)` | 문자열 포함 | `verify(logger).log(contains("error"))` |

**주의**: Matcher를 사용할 때는 **모든 인자에 Matcher를 사용**해야 합니다.

```java
// ❌ 잘못된 사용
verify(service).process(1L, any());

// ✅ 올바른 사용
verify(service).process(eq(1L), any());
```

---

## 부록 8: 실무 팁 & 안티패턴

### ✅ 권장 사항

1. **BDD 스타일 사용**: `given`, `when`, `then` 주석으로 구조 명확히
2. **DisplayName 활용**: 한글로 명확하게 "무엇을 테스트하는지" 작성
3. **verify는 핵심만**: 모든 호출을 검증하지 말고, 중요한 것만 검증
4. **ArgumentCaptor 적극 활용**: 실제 전달된 값 검증이 중요

### ❌ 안티패턴

1. **과도한 verify**: 모든 메서드 호출을 검증하면 유지보수가 어려움
2. **테스트 간 의존성**: 테스트는 독립적으로 실행 가능해야 함
3. **실제 DB 사용**: 단위 테스트에서는 Mock 사용 (통합 테스트에서 실제 DB)
4. **너무 긴 테스트**: 하나의 테스트는 하나의 시나리오만 검증

---

## 부록 9: 참고 자료

- **Mockito 공식 문서**: https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html
- **AssertJ 문서**: https://assertj.github.io/doc/
- **Spring Boot Testing**: https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing
