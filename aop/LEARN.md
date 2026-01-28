# Spring AOP 동작 원리

스프링 AOP가 어떻게 동작하는지, 그리고 프록시(Proxy) 객체가 어떻게 생성되는지 학습합니다.

## 1. AOP와 프록시 패턴
스프링 AOP는 **프록시 패턴**을 기반으로 동작합니다.
*   우리가 `@Autowired`로 주입받는 객체는 실제 비즈니스 로직을 가진 객체(Target)가 아니라, 그 객체를 감싸고 있는 **프록시(대리자)**입니다.
*   프록시는 메서드 호출을 가로채서 부가 기능(Aspect)을 실행한 후, 실제 Target 객체의 메서드를 호출합니다.

## 2. 프록시의 종류

### A. JDK Dynamic Proxy
*   **대상**: 인터페이스가 있는 클래스
*   **원리**: Java의 리플렉션(Reflection) 기능을 이용하여 런타임에 인터페이스를 구현한 프록시 객체를 생성합니다.
*   **특징**: 인터페이스에 정의된 메서드만 프록시 적용이 가능합니다.

### B. CGLIB (Code Generation Library)
*   **대상**: 인터페이스가 없는 클래스 (또는 강제로 설정 시)
*   **원리**: 바이트코드를 조작하여 Target 클래스를 **상속(Extends)**받는 프록시 객체를 생성합니다.
*   **특징**:
    *   상속을 이용하므로 `final` 클래스나 `final` 메서드에는 적용할 수 없습니다.
    *   스프링 부트 2.0부터는 성능상의 이점 등으로 인해 **기본적으로 CGLIB를 선호**합니다.

## 3. 실습 결과 확인 포인트
`ProxyCheckRunner` 실행 결과를 통해 다음을 확인합니다.

1.  **클래스 이름**:
    *   JDK Proxy: `com.sun.proxy.$Proxy...` 형태
    *   CGLIB: `...$$EnhancerBySpringCGLIB$$...` 형태
2.  **AopUtils 확인**:
    *   `isAopProxy()`: true
    *   `isCglibProxy()` vs `isJdkDynamicProxy()` 결과 비교
3.  **Final 메서드 제약**:
    *   `noInterfaceService.doFinalAction()` 호출 시 `[AOP] Before method...` 로그가 출력되지 않습니다.
    *   이유: CGLIB는 메서드를 오버라이딩하여 AOP 로직을 심는데, `final` 메서드는 오버라이딩이 불가능하기 때문입니다.

## 4. 주의사항 (Self-Invocation)
프록시 객체를 통해 메서드를 호출할 때만 AOP가 적용됩니다.
*   **문제**: 빈 내부에서 자신의 다른 메서드를 호출(`this.method()`)하면 프록시를 거치지 않고 직접 호출하므로 **AOP가 적용되지 않습니다.**
