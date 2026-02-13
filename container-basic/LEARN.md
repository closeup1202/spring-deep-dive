# Container Technology Basics

컨테이너 기술의 기초 개념과 원리를 학습합니다.

## 목차

1. [컨테이너란?](#컨테이너란)
2. [가상화 vs 컨테이너](#가상화-vs-컨테이너)
3. [컨테이너의 핵심 기술](#컨테이너의-핵심-기술)
4. [컨테이너의 장단점](#컨테이너의-장단점)
5. [컨테이너 런타임](#컨테이너-런타임)
6. [컨테이너 이미지](#컨테이너-이미지)
7. [컨테이너 네트워킹](#컨테이너-네트워킹)
8. [컨테이너 스토리지](#컨테이너-스토리지)
9. [컨테이너 보안](#컨테이너-보안)
10. [실습 환경 구성](#실습-환경-구성)

---

## 컨테이너란?

### 정의

**컨테이너**는 애플리케이션과 그 실행에 필요한 모든 것(라이브러리, 설정 파일, 종속성)을 하나의 패키지로 묶은 **격리된 실행 환경**입니다.

### 비유: 컨테이너는 "표준 운송 컨테이너"

```
실제 운송 컨테이너:
- 다양한 화물을 표준화된 상자에 담음
- 배, 트럭, 기차 어디서나 동일하게 운송
- 내용물과 무관하게 처리

소프트웨어 컨테이너:
- 애플리케이션을 표준화된 형식으로 패키징
- 개발, 테스트, 운영 환경에서 동일하게 실행
- 내부 구현과 무관하게 배포
```

---

### 컨테이너의 핵심 개념

```
[컨테이너]
    │
    ├─ 애플리케이션 코드
    ├─ 런타임 (Node.js, Java 등)
    ├─ 시스템 라이브러리
    ├─ 설정 파일
    └─ 환경 변수

모두 하나로 패키징!
```

---

## 가상화 vs 컨테이너

### 전통적인 가상화 (Virtual Machine)

```
┌─────────────────────────────────────────┐
│          Application 1    Application 2  │
│          ─────────────    ─────────────  │
│          Binaries/Libs    Binaries/Libs  │
│          Guest OS 1       Guest OS 2     │
├─────────────────────────────────────────┤
│            Hypervisor (VMware, VirtualBox)│
├─────────────────────────────────────────┤
│            Host OS (Windows, Linux)      │
├─────────────────────────────────────────┤
│            Hardware (CPU, Memory, Disk)  │
└─────────────────────────────────────────┘
```

**특징**:
- 각 VM은 독립적인 **전체 OS** 포함
- 무겁고 부팅이 느림 (수십 초 ~ 수 분)
- 리소스 오버헤드 큼 (GB 단위 메모리)

---

### 컨테이너 기반 가상화

```
┌─────────────────────────────────────────┐
│     App 1      App 2      App 3          │
│   ─────────  ─────────  ─────────        │
│   Bins/Libs  Bins/Libs  Bins/Libs        │
├─────────────────────────────────────────┤
│        Container Runtime (Docker)        │
├─────────────────────────────────────────┤
│            Host OS (Linux)               │
├─────────────────────────────────────────┤
│            Hardware                      │
└─────────────────────────────────────────┘
```

**특징**:
- **Host OS의 커널 공유**
- 가볍고 빠름 (초 단위 시작)
- 리소스 효율적 (MB 단위 메모리)

---

### 비교표

| 항목 | Virtual Machine | Container |
|------|----------------|-----------|
| **격리 수준** | 강함 (하드웨어 수준) | 중간 (프로세스 수준) |
| **시작 시간** | 수십 초 ~ 수 분 | 수 밀리초 ~ 수 초 |
| **메모리 사용** | GB 단위 | MB 단위 |
| **디스크 사용** | 수십 GB | 수백 MB |
| **이식성** | 낮음 (OS 포함) | 높음 (표준화) |
| **성능 오버헤드** | 높음 (5~10%) | 거의 없음 (<2%) |
| **밀도** | 낮음 (수십 개) | 높음 (수백~수천 개) |
| **보안** | 강함 | 중간 (커널 공유) |

---

### 언제 무엇을 사용할까?

**Virtual Machine을 사용해야 할 때**:
- 완전히 다른 OS 필요 (Windows에서 Linux)
- 강력한 격리 필요 (멀티 테넌트)
- 레거시 애플리케이션

**컨테이너를 사용해야 할 때**:
- 마이크로서비스 아키텍처
- CI/CD 파이프라인
- 수평 확장이 필요한 애플리케이션
- 개발/테스트 환경 통일

**둘 다 사용 (하이브리드)**:
- VM 위에서 컨테이너 실행 (일반적)
- 보안과 효율성 모두 확보

---

## 컨테이너의 핵심 기술

컨테이너는 **Linux 커널 기능**을 활용하여 구현됩니다.

### 1. Namespaces (격리)

프로세스에게 "독립된 세상"을 보여주는 기술

| Namespace | 격리 대상 | 설명 |
|-----------|---------|------|
| **PID** | Process ID | 각 컨테이너는 자신만의 PID 1 가짐 |
| **NET** | Network | 독립적인 네트워크 인터페이스 |
| **MNT** | Mount | 독립적인 파일시스템 |
| **UTS** | Hostname | 각자 다른 hostname |
| **IPC** | Inter-Process Communication | 프로세스 간 통신 격리 |
| **USER** | User/Group ID | UID/GID 매핑 |

**예시**:
```bash
# 호스트에서
ps aux | wc -l
# 200개 프로세스

# 컨테이너 내부에서
ps aux | wc -l
# 5개 프로세스 (자신의 프로세스만 보임!)
```

---

### 2. Cgroups (리소스 제한)

컨테이너가 사용할 수 있는 **리소스를 제한**하는 기술

**제한 가능한 리소스**:
- **CPU**: CPU 사용률 제한 (예: 50%)
- **Memory**: 메모리 사용량 제한 (예: 512MB)
- **Disk I/O**: 디스크 읽기/쓰기 속도 제한
- **Network**: 네트워크 대역폭 제한

**예시**:
```bash
# Docker에서 리소스 제한
docker run --cpus="0.5" --memory="512m" my-app

# 실제 cgroup 설정 확인
cat /sys/fs/cgroup/cpu/docker/<container-id>/cpu.cfs_quota_us
```

**왜 필요한가?**
- 한 컨테이너가 모든 리소스 독점 방지
- 멀티 테넌트 환경에서 공정성 보장
- 예측 가능한 성능

---

### 3. Union File System (레이어)

여러 파일시스템을 **하나로 합쳐서** 보여주는 기술

**레이어 구조**:
```
[읽기/쓰기 레이어]  ← 컨테이너 실행 중 변경사항
      ↓
[레이어 3: 앱 코드]  ← COPY app.jar
      ↓
[레이어 2: Java]    ← RUN apt install openjdk
      ↓
[레이어 1: Base OS] ← FROM ubuntu:22.04
      ↓
    [커널]           ← 호스트 공유
```

**장점**:
- **공간 효율**: 동일한 레이어 공유 (중복 제거)
- **빠른 배포**: 변경된 레이어만 다운로드
- **버전 관리**: 각 레이어는 불변 (Immutable)

**예시**:
```bash
# 같은 Ubuntu 베이스 이미지를 사용하는 10개 컨테이너
# → Ubuntu 레이어는 1번만 저장됨!

docker images
# ubuntu:22.04 - 77MB (1번만 저장)
# my-app-1     - 78MB (앱 코드만 추가: 1MB)
# my-app-2     - 78MB (앱 코드만 추가: 1MB)
```

---

### 4. chroot (루트 디렉토리 격리)

프로세스의 **루트 디렉토리를 변경**하여 파일시스템 격리

```bash
# 호스트
ls /
# bin dev home lib usr var ...

# 컨테이너 (chroot된 환경)
ls /
# app bin lib ... (컨테이너만의 파일시스템)
```

---

## 컨테이너의 장단점

### 장점

#### 1. 일관성 (Consistency)
```
개발자 노트북: ✅ 작동
테스트 서버:   ✅ 작동
운영 서버:     ✅ 작동

"내 컴퓨터에서는 되는데..." → 사라짐!
```

#### 2. 이식성 (Portability)
```bash
# 어디서나 동일하게 실행
docker run my-app

# AWS, GCP, Azure, 온프레미스 모두 가능
```

#### 3. 빠른 배포
```
VM:        부팅 30초 ~ 2분
Container: 시작 0.1초 ~ 2초

배포 속도 10~100배 향상!
```

#### 4. 자원 효율성
```
서버 1대에:
- VM:        10~20개
- Container: 100~1000개

밀도 10~100배 향상!
```

#### 5. 마이크로서비스 친화적
```
각 서비스를 독립적으로:
- 배포
- 확장
- 롤백
```

#### 6. DevOps 가속화
```
개발 → 빌드 → 테스트 → 배포
모든 단계에서 동일한 환경 사용
```

---

### 단점

#### 1. 보안 취약점
```
모든 컨테이너가 Host OS 커널 공유
→ 커널 취약점 공격 시 모두 영향
```

**완화 방법**:
- 최소 권한 원칙
- SELinux, AppArmor 사용
- 컨테이너 스캐닝 (Trivy, Clair)

#### 2. 영속 데이터 관리
```
컨테이너는 일시적(Ephemeral)
삭제 시 데이터도 삭제됨
```

**해결책**:
- Volume 사용
- 외부 스토리지 연결

#### 3. 네트워킹 복잡성
```
수백 개 컨테이너 간 통신
서비스 디스커버리 필요
```

**해결책**:
- Kubernetes, Docker Swarm
- Service Mesh (Istio, Linkerd)

#### 4. 모니터링 어려움
```
수명이 짧은 컨테이너
→ 로그/메트릭 수집 복잡
```

**해결책**:
- 중앙 집중식 로깅 (ELK)
- 분산 추적 (Jaeger, Zipkin)

---

## 컨테이너 런타임

### 런타임 계층 구조

```
┌─────────────────────────────────────┐
│   High-Level Runtime (Docker)       │  ← 사용자 인터페이스
├─────────────────────────────────────┤
│   Low-Level Runtime (containerd)    │  ← 컨테이너 생명주기 관리
├─────────────────────────────────────┤
│   OCI Runtime (runc)                │  ← 실제 컨테이너 실행
├─────────────────────────────────────┤
│   Linux Kernel                      │
└─────────────────────────────────────┘
```

---

### 주요 런타임

#### 1. Docker
- **가장 인기 있는** 컨테이너 플랫폼
- 이미지 빌드, 레지스트리, 네트워킹 등 모두 포함
- 개발자 친화적 (docker CLI)

#### 2. containerd
- **CNCF 프로젝트**
- Docker에서 분리된 핵심 런타임
- Kubernetes 기본 런타임

#### 3. CRI-O
- Kubernetes 전용 경량 런타임
- OCI 표준 준수

#### 4. Podman
- **Daemonless** (Docker는 daemon 필요)
- Rootless 실행 가능 (보안 향상)
- Docker 명령어와 호환

---

### OCI (Open Container Initiative)

컨테이너 표준화 조직

**정의한 표준**:
1. **Image Spec**: 이미지 포맷
2. **Runtime Spec**: 컨테이너 실행 방법
3. **Distribution Spec**: 이미지 배포 방법

**왜 중요한가?**
- 벤더 종속 방지
- 상호 운용성 보장
- Docker 이미지를 다른 런타임에서 실행 가능

---

## 컨테이너 이미지

### 이미지란?

**컨테이너를 실행하기 위한 템플릿** (클래스와 인스턴스 관계)

```
이미지 (Image)         컨테이너 (Container)
    └──────────────►   (실행 중인 인스턴스)
```

---

### 이미지 레이어

```
Dockerfile:
FROM ubuntu:22.04        → Layer 1: Ubuntu (77MB)
RUN apt install java     → Layer 2: Java (200MB)
COPY app.jar /app/       → Layer 3: App (50MB)
CMD ["java", "-jar", ...]

최종 이미지 크기: 327MB
```

**레이어 캐싱**:
```bash
# 첫 빌드: 5분
docker build -t my-app .

# app.jar만 변경 후 재빌드: 5초!
# Layer 1, 2는 캐시 사용
```

---

### 이미지 저장소 (Registry)

```
┌───────────────┐
│  Developer    │
└───────┬───────┘
        │ docker push
        ↓
┌───────────────┐
│ Docker Hub    │  ← 공개 레지스트리
│ (Registry)    │
└───────┬───────┘
        │ docker pull
        ↓
┌───────────────┐
│  Server       │
└───────────────┘
```

**주요 레지스트리**:
- **Docker Hub**: 공개 이미지 저장소
- **Amazon ECR**: AWS 프라이빗 레지스트리
- **Google GCR**: GCP 레지스트리
- **Harbor**: 오픈소스 프라이빗 레지스트리

---

### 이미지 태그

```bash
# 이미지 이름 구조
[registry]/[namespace]/[repository]:[tag]

예시:
docker.io/library/ubuntu:22.04
   ↑        ↑       ↑      ↑
   │        │       │      └─ 버전
   │        │       └──────── 이미지 이름
   │        └──────────────── 네임스페이스
   └───────────────────────── 레지스트리
```

**태그 규칙**:
- `latest`: 기본 태그 (권장 안 함!)
- `1.0.0`: Semantic Versioning
- `sha256:abc...`: 해시 (불변)

---

## 컨테이너 네트워킹

### 네트워크 모드

#### 1. Bridge (기본)
```
[컨테이너 A]     [컨테이너 B]
      │               │
      └───────┬───────┘
           [Bridge]
              │
         [Host NIC]
```

- 가장 일반적
- 컨테이너끼리 통신 가능
- 외부 접근은 포트 매핑 필요

#### 2. Host
```
[컨테이너] → [Host NIC] 직접 사용
```

- 네트워크 성능 최고
- 포트 충돌 가능

#### 3. None
```
[컨테이너] → (네트워크 없음)
```

- 완전 격리
- 보안 테스트용

---

### 포트 매핑

```bash
docker run -p 8080:80 nginx

호스트:8080 → 컨테이너:80

[브라우저] → localhost:8080
                 │
           [Host:8080]
                 │
          [Container:80]
                 │
              [Nginx]
```

---

### 컨테이너 간 통신

```bash
# 1. 같은 네트워크에 연결
docker network create my-network

# 2. 컨테이너 실행
docker run --network my-network --name db postgres
docker run --network my-network --name app my-app

# 3. app 컨테이너에서 db 접근
# 호스트명으로 접근 가능!
jdbc:postgresql://db:5432/mydb
```

---

## 컨테이너 스토리지

### 문제점

```bash
# 컨테이너에서 파일 생성
docker run ubuntu touch /data/file.txt

# 컨테이너 삭제
docker rm <container>

# 파일 사라짐! 💥
```

---

### 해결책: Volume

#### 1. Named Volume (권장)
```bash
# Volume 생성
docker volume create my-data

# 컨테이너에 마운트
docker run -v my-data:/data ubuntu

# 데이터 영속성 보장
docker rm <container>  # 컨테이너 삭제
docker run -v my-data:/data ubuntu
ls /data  # 파일 여전히 존재!
```

#### 2. Bind Mount
```bash
# 호스트 디렉토리 직접 마운트
docker run -v /host/path:/container/path ubuntu

# 개발 시 코드 동기화에 유용
docker run -v $(pwd):/app my-app
```

#### 3. tmpfs (메모리)
```bash
# 메모리에만 저장 (휘발성, 빠름)
docker run --tmpfs /tmp my-app
```

---

### 비교

| 타입 | 위치 | 관리 | 사용 사례 |
|------|------|------|----------|
| **Named Volume** | Docker 관리 | Docker | 프로덕션 데이터 |
| **Bind Mount** | 호스트 경로 | 사용자 | 개발 환경 |
| **tmpfs** | 메모리 | Docker | 임시 데이터 |

---

## 컨테이너 보안

### 보안 위협

#### 1. 권한 있는 컨테이너
```bash
# ❌ 위험: root 권한으로 실행
docker run --privileged my-app

# ✅ 안전: 일반 사용자로 실행
docker run --user 1000:1000 my-app
```

#### 2. 이미지 취약점
```bash
# 오래된 베이스 이미지
FROM ubuntu:18.04  # ❌ EOL (End of Life)

# 최신 보안 패치 적용
FROM ubuntu:22.04  # ✅
```

#### 3. 비밀 정보 노출
```bash
# ❌ Dockerfile에 하드코딩
ENV DB_PASSWORD=secret123

# ✅ 환경 변수로 주입
docker run -e DB_PASSWORD=$DB_PASSWORD my-app
```

---

### 보안 모범 사례

#### 1. 최소 권한 원칙
```dockerfile
# Rootless 실행
FROM ubuntu:22.04
RUN useradd -m appuser
USER appuser
```

#### 2. 이미지 스캐닝
```bash
# Trivy로 취약점 스캔
trivy image my-app:latest

# 결과:
# CVE-2023-1234 (HIGH)
# CVE-2023-5678 (MEDIUM)
```

#### 3. 읽기 전용 파일시스템
```bash
docker run --read-only my-app

# 쓰기 필요한 디렉토리만 tmpfs
docker run --read-only --tmpfs /tmp my-app
```

#### 4. Capability 제한
```bash
# 불필요한 권한 제거
docker run --cap-drop ALL --cap-add NET_BIND_SERVICE my-app
```

---

## 실습 환경 구성

### 1. Docker 설치 (Ubuntu)

```bash
# 기존 설치 제거
sudo apt remove docker docker-engine docker.io containerd runc

# 저장소 설정
sudo apt update
sudo apt install ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# Docker 설치
sudo apt update
sudo apt install docker-ce docker-ce-cli containerd.io

# 설치 확인
docker --version
sudo docker run hello-world
```

---

### 2. Docker 설치 (Windows/Mac)

```bash
# Docker Desktop 다운로드
https://www.docker.com/products/docker-desktop/

# 설치 후 확인
docker --version
docker run hello-world
```

---

### 3. 사용자 권한 설정 (Linux)

```bash
# docker 그룹에 사용자 추가
sudo usermod -aG docker $USER

# 재로그인 후
docker run hello-world  # sudo 없이 실행
```

---

### 4. 첫 컨테이너 실행

```bash
# 1. Nginx 웹 서버 실행
docker run -d -p 8080:80 --name my-nginx nginx

# 2. 브라우저에서 접속
# http://localhost:8080

# 3. 로그 확인
docker logs my-nginx

# 4. 컨테이너 내부 접속
docker exec -it my-nginx bash

# 5. 정리
docker stop my-nginx
docker rm my-nginx
```

---

## 요약

### 핵심 개념

1. **컨테이너**: 애플리케이션과 종속성을 패키징한 격리 환경
2. **VM vs Container**: 컨테이너는 OS 커널 공유로 가볍고 빠름
3. **핵심 기술**: Namespaces (격리) + Cgroups (제한) + Union FS (레이어)
4. **이미지**: 컨테이너의 템플릿 (레이어 구조)
5. **네트워킹**: Bridge, Host, None 모드
6. **스토리지**: Volume으로 영속성 보장
7. **보안**: 최소 권한, 이미지 스캐닝, Rootless

---

### 왜 컨테이너를 사용하는가?

```
문제: "내 컴퓨터에서는 되는데..."

해결: 컨테이너로 환경 통일
     → 개발/테스트/운영 모두 동일

결과: 빠른 배포, 자원 효율, 마이크로서비스
```

---

### 다음 단계

- **container-docker**: Docker 실전 사용법
- **kubernetes**: 컨테이너 오케스트레이션

---

## 학습 체크리스트

- [ ] 컨테이너와 VM의 차이 설명 가능
- [ ] Namespaces와 Cgroups 이해
- [ ] 컨테이너 이미지 레이어 구조 이해
- [ ] 컨테이너 네트워킹 모드 구분
- [ ] Volume으로 데이터 영속성 보장
- [ ] 컨테이너 보안 모범 사례 숙지
- [ ] Docker 설치 및 기본 명령어 실행

---

## 참고 자료

- [컨테이너란? - Red Hat](https://www.redhat.com/ko/topics/containers)
- [Linux Namespaces](https://man7.org/linux/man-pages/man7/namespaces.7.html)
- [Cgroups Documentation](https://www.kernel.org/doc/Documentation/cgroup-v2.txt)
- [OCI Specifications](https://github.com/opencontainers/runtime-spec)
- [Docker Documentation](https://docs.docker.com/)

---

**"컨테이너는 애플리케이션 배포의 표준이 되었다."** 🐳
