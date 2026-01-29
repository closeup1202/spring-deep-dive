# Testcontainers 실무 가이드

로컬 환경이나 CI/CD 파이프라인에서 외부 의존성(DB, Redis, Kafka 등)을 Docker 컨테이너로 띄워 테스트하는 방법을 학습합니다.

## 1. 왜 Testcontainers인가?
*   **환경 격리**: 개발자마다 로컬에 설치된 DB 버전이 다르거나, 데이터가 오염되어 테스트가 실패하는 문제를 해결합니다.
*   **일회성**: 테스트가 시작될 때 깨끗한 환경을 만들고, 끝나면 싹 지워버립니다.
*   **실제와 동일한 환경**: H2 같은 인메모리 DB는 실제 운영 DB(MySQL, PostgreSQL)와 문법이나 동작이 미묘하게 다를 수 있습니다. Testcontainers를 쓰면 실제 운영 환경과 똑같은 DB로 테스트할 수 있습니다.

## 2. 기본 사용법 (`BasicRedisTest.java`)
*   **`@Testcontainers`**: JUnit 5 확장 기능을 활성화합니다.
*   **`@Container`**: 컨테이너 객체를 관리합니다.
    *   `static`: 테스트 클래스 내의 모든 메서드가 하나의 컨테이너를 공유합니다. (추천)
    *   `instance`: 각 테스트 메서드(`@Test`)마다 컨테이너를 껐다 켭니다. (매우 느림)
*   **`@DynamicPropertySource`**: 컨테이너가 실행되면서 할당받은 랜덤 포트나 IP를 Spring Boot 설정(`application.yml`)에 동적으로 주입합니다.

## 3. 실무 최적화: Singleton Pattern (`AbstractContainerBaseTest.java`)
테스트 클래스가 100개라면 컨테이너도 100번 떴다 꺼질까요? 그러면 전체 테스트 시간이 엄청나게 길어집니다.

이를 해결하기 위해 **Singleton Container 패턴**을 사용합니다.
1.  추상 클래스(`AbstractContainerBaseTest`)에 `static` 필드로 컨테이너를 선언하고 `static` 블록에서 수동으로 `start()` 합니다.
2.  모든 통합 테스트 클래스가 이 추상 클래스를 상속받습니다.
3.  이렇게 하면 전체 테스트 스위트가 실행되는 동안 **컨테이너는 딱 한 번만 실행**되고 계속 재사용됩니다.

## 4. 실행 방법
*   **필수 조건**: 로컬에 **Docker Desktop** (또는 Docker Engine)이 실행 중이어야 합니다.
*   `BasicRedisTest` 또는 `SingletonRedisTest`를 JUnit으로 실행하면, 자동으로 Docker 이미지를 다운로드(최초 1회)하고 컨테이너를 띄워 테스트를 수행합니다.

## 5. 트러블슈팅 (Could not find a valid Docker environment)
이 에러는 Testcontainers가 실행 중인 Docker를 찾지 못할 때 발생합니다.

1.  **Docker Desktop 실행 확인**: 작업 표시줄 트레이 아이콘 확인 및 `docker ps` 명령어 실행.
2.  **Expose daemon 설정 (Windows)**:
    *   Docker Desktop 설정 -> General -> **"Expose daemon on tcp://localhost:2375 without TLS"** 체크.
3.  **WSL2 사용 시**:
    *   Docker Desktop 설정 -> Resources -> WSL Integration -> 사용 중인 배포판(Ubuntu 등) 체크.
