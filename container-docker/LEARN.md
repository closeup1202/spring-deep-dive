# Docker Complete Guide

Docker 명령어와 실전 사용법을 학습합니다.

## 목차

1. [Docker 기본 명령어](#docker-기본-명령어)
2. [Dockerfile 작성](#dockerfile-작성)
3. [이미지 관리](#이미지-관리)
4. [컨테이너 관리](#컨테이너-관리)
5. [네트워크](#네트워크)
6. [볼륨과 스토리지](#볼륨과-스토리지)
7. [Docker Compose](#docker-compose)
8. [멀티 스테이지 빌드](#멀티-스테이지-빌드)
9. [최적화 기법](#최적화-기법)
10. [실전 예제](#실전-예제)

---

## Docker 기본 명령어

### 컨테이너 실행

```bash
# 기본 실행
docker run nginx

# 백그라운드 실행 (-d: detached)
docker run -d nginx

# 이름 지정
docker run -d --name my-nginx nginx

# 포트 매핑 (-p host:container)
docker run -d -p 8080:80 nginx

# 환경 변수 설정
docker run -d -e DB_HOST=localhost -e DB_PORT=5432 my-app

# 볼륨 마운트
docker run -d -v my-data:/data nginx

# 자동 삭제 (종료 시)
docker run --rm nginx

# 대화형 모드 (-it: interactive + tty)
docker run -it ubuntu bash

# 리소스 제한
docker run -d --cpus="1.5" --memory="512m" nginx

# 모든 옵션 조합
docker run -d \
  --name web \
  -p 8080:80 \
  -e ENV=production \
  -v app-data:/var/www \
  --cpus="2" \
  --memory="1g" \
  --restart unless-stopped \
  nginx:latest
```

---

### 컨테이너 관리

```bash
# 실행 중인 컨테이너 목록
docker ps

# 모든 컨테이너 (중지된 것 포함)
docker ps -a

# 마지막 실행한 컨테이너
docker ps -l

# 컨테이너 중지
docker stop <container-id 또는 name>

# 강제 종료
docker kill <container-id>

# 컨테이너 시작
docker start <container-id>

# 컨테이너 재시작
docker restart <container-id>

# 컨테이너 일시 중지
docker pause <container-id>
docker unpause <container-id>

# 컨테이너 삭제
docker rm <container-id>

# 강제 삭제 (실행 중이어도)
docker rm -f <container-id>

# 모든 중지된 컨테이너 삭제
docker container prune

# 모든 컨테이너 삭제 (위험!)
docker rm -f $(docker ps -aq)
```

---

### 컨테이너 정보 조회

```bash
# 컨테이너 로그
docker logs <container-id>

# 로그 실시간 추적 (-f: follow)
docker logs -f <container-id>

# 마지막 N줄만
docker logs --tail 100 <container-id>

# 타임스탬프 포함
docker logs -t <container-id>

# 컨테이너 상세 정보 (JSON)
docker inspect <container-id>

# 특정 필드만 추출
docker inspect --format='{{.NetworkSettings.IPAddress}}' <container-id>

# 컨테이너 리소스 사용량 실시간
docker stats

# 특정 컨테이너만
docker stats <container-id>

# 실행 중인 프로세스
docker top <container-id>
```

---

### 컨테이너 접속 및 명령 실행

```bash
# 컨테이너 내부 쉘 실행
docker exec -it <container-id> bash
# 또는
docker exec -it <container-id> sh

# 단일 명령 실행
docker exec <container-id> ls /app

# 루트 사용자로 실행
docker exec -it --user root <container-id> bash

# 실행 중인 컨테이너에 attach (Ctrl+P, Ctrl+Q로 detach)
docker attach <container-id>

# 컨테이너와 호스트 간 파일 복사
docker cp <container-id>:/path/in/container /host/path
docker cp /host/path <container-id>:/path/in/container
```

---

## Dockerfile 작성

### 기본 구조

```dockerfile
# 베이스 이미지 지정
FROM openjdk:17-jdk-slim

# 메타데이터
LABEL maintainer="your@email.com"
LABEL version="1.0"
LABEL description="Spring Boot Application"

# 작업 디렉토리 설정
WORKDIR /app

# 파일 복사
COPY target/app.jar app.jar

# 환경 변수 설정
ENV JAVA_OPTS="-Xmx512m"
ENV SPRING_PROFILES_ACTIVE=production

# 포트 노출 (문서화 목적)
EXPOSE 8080

# 사용자 변경 (보안)
RUN useradd -m appuser
USER appuser

# 컨테이너 시작 명령
CMD ["java", "-jar", "app.jar"]
```

---

### 주요 Dockerfile 명령어

#### FROM - 베이스 이미지

```dockerfile
# 공식 이미지 사용
FROM node:18

# 특정 버전
FROM python:3.11-slim

# 멀티 스테이지 (이름 지정)
FROM maven:3.8-openjdk-17 AS builder

# 다이제스트로 고정 (불변)
FROM nginx@sha256:abc123...
```

---

#### RUN - 이미지 빌드 시 명령 실행

```dockerfile
# 쉘 형식 (/bin/sh -c로 실행)
RUN apt-get update && apt-get install -y curl

# exec 형식 (권장, 쉘 없이 직접 실행)
RUN ["apt-get", "update"]

# 여러 명령을 &&로 연결 (레이어 최소화)
RUN apt-get update && \
    apt-get install -y \
        curl \
        vim \
        git && \
    rm -rf /var/lib/apt/lists/*  # 캐시 정리
```

---

#### COPY vs ADD

```dockerfile
# COPY (권장): 단순 파일 복사
COPY app.jar /app/app.jar
COPY src/ /app/src/

# 권한 설정
COPY --chown=appuser:appuser app.jar /app/

# ADD: 자동 압축 해제 + URL 다운로드
ADD https://example.com/file.tar.gz /tmp/
ADD archive.tar.gz /app/  # 자동으로 압축 해제됨

# 결론: 특별한 이유 없으면 COPY 사용
```

---

#### CMD vs ENTRYPOINT

```dockerfile
# CMD: 기본 명령 (override 가능)
CMD ["python", "app.py"]
# docker run my-image bash  ← 이렇게 덮어쓰기 가능

# ENTRYPOINT: 반드시 실행되는 명령
ENTRYPOINT ["python"]
# docker run my-image app.py  ← 인자만 추가

# 조합 사용 (권장)
ENTRYPOINT ["java", "-jar"]
CMD ["app.jar"]
# docker run my-image custom.jar  ← custom.jar로 변경 가능
```

---

#### ARG vs ENV

```dockerfile
# ARG: 빌드 시에만 사용
ARG VERSION=1.0
RUN echo "Building version ${VERSION}"
# 컨테이너 실행 시에는 없음!

# ENV: 빌드 + 런타임 모두 사용
ENV APP_ENV=production
# 컨테이너 실행 시에도 환경 변수로 존재

# 조합 사용
ARG VERSION
ENV APP_VERSION=${VERSION}

# 빌드 시 ARG 전달
# docker build --build-arg VERSION=2.0 .
```

---

#### WORKDIR

```dockerfile
# 작업 디렉토리 설정
WORKDIR /app

# 이후 명령은 /app에서 실행됨
COPY . .  # /app으로 복사
RUN ls    # /app 디렉토리 목록

# 경로 변경
WORKDIR /data
RUN pwd  # /data
```

---

#### VOLUME

```dockerfile
# 볼륨 마운트 포인트 선언
VOLUME ["/data", "/logs"]

# 또는
VOLUME /data

# 주의: Dockerfile에서 VOLUME을 선언해도
# 실행 시 -v 옵션으로 마운트 필요
```

---

#### USER

```dockerfile
# 사용자 생성 및 전환
RUN useradd -m -u 1000 appuser
USER appuser

# 이후 명령은 appuser로 실행
RUN whoami  # appuser

# root로 다시 전환 (필요 시)
USER root
```

---

#### HEALTHCHECK

```dockerfile
# 컨테이너 헬스 체크
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# 옵션:
# --interval: 체크 간격
# --timeout: 타임아웃
# --start-period: 초기 유예 시간
# --retries: 실패 허용 횟수
```

---

### 완전한 Spring Boot Dockerfile 예시

```dockerfile
# 멀티 스테이지 빌드
FROM maven:3.8-openjdk-17 AS builder

WORKDIR /build

# 의존성 캐싱 최적화
COPY pom.xml .
RUN mvn dependency:go-offline

# 소스 복사 및 빌드
COPY src ./src
RUN mvn clean package -DskipTests

# 실행 이미지
FROM openjdk:17-jdk-slim

LABEL maintainer="dev@example.com"
LABEL version="1.0"

# 비root 사용자 생성
RUN useradd -m -u 1000 appuser

WORKDIR /app

# builder 스테이지에서 jar 복사
COPY --from=builder --chown=appuser:appuser /build/target/*.jar app.jar

# 사용자 전환
USER appuser

# 환경 변수
ENV JAVA_OPTS="-Xms256m -Xmx512m" \
    SPRING_PROFILES_ACTIVE=production

# 포트
EXPOSE 8080

# 헬스 체크
HEALTHCHECK --interval=30s --timeout=3s \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 실행
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

---

## 이미지 관리

### 이미지 조회

```bash
# 로컬 이미지 목록
docker images

# 특정 이미지만
docker images nginx

# 중간 레이어 포함
docker images -a

# 이미지 ID만
docker images -q

# 디스크 사용량
docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}"
```

---

### 이미지 빌드

```bash
# 현재 디렉토리의 Dockerfile로 빌드
docker build -t my-app:1.0 .

# 태그 여러 개
docker build -t my-app:1.0 -t my-app:latest .

# 특정 Dockerfile 지정
docker build -f Dockerfile.dev -t my-app:dev .

# 빌드 인자 전달
docker build --build-arg VERSION=2.0 -t my-app:2.0 .

# 캐시 사용 안 함
docker build --no-cache -t my-app:latest .

# 특정 스테이지까지만 빌드
docker build --target builder -t my-app:builder .

# 빌드 컨텍스트 제외 (.dockerignore 사용)
# .dockerignore 파일:
node_modules
.git
*.log
```

---

### 이미지 푸시/풀

```bash
# Docker Hub 로그인
docker login

# 이미지 태그 재지정
docker tag my-app:latest username/my-app:latest

# 이미지 푸시
docker push username/my-app:latest

# 이미지 풀
docker pull username/my-app:latest

# 프라이빗 레지스트리
docker login registry.example.com
docker tag my-app:latest registry.example.com/my-app:latest
docker push registry.example.com/my-app:latest
```

---

### 이미지 관리

```bash
# 이미지 상세 정보
docker image inspect my-app:latest

# 이미지 히스토리 (레이어 확인)
docker history my-app:latest

# 이미지 삭제
docker rmi my-app:latest

# 강제 삭제
docker rmi -f my-app:latest

# 사용하지 않는 이미지 삭제
docker image prune

# dangling 이미지 삭제 (<none>:<none>)
docker image prune -a

# 이미지 저장 (tar 파일)
docker save -o my-app.tar my-app:latest

# 이미지 로드
docker load -i my-app.tar

# 이미지 내보내기 (컨테이너 기반)
docker export <container-id> > container.tar

# 이미지 가져오기
docker import container.tar my-app:imported
```

---

## 컨테이너 관리

### 자동 재시작 정책

```bash
# 항상 재시작
docker run -d --restart always nginx

# 실패 시에만 재시작
docker run -d --restart on-failure nginx

# 최대 3번까지만 재시작
docker run -d --restart on-failure:3 nginx

# 수동 중지 전까지 재시작
docker run -d --restart unless-stopped nginx

# 재시작 정책 변경
docker update --restart always <container-id>
```

---

### 리소스 제한

```bash
# CPU 제한
docker run -d --cpus="1.5" nginx  # 1.5 코어

# CPU 우선순위 (상대적, 기본 1024)
docker run -d --cpu-shares=512 nginx

# 메모리 제한
docker run -d --memory="512m" nginx
docker run -d --memory="2g" nginx

# 메모리 스왑 제한
docker run -d --memory="512m" --memory-swap="1g" nginx

# Disk I/O 제한
docker run -d --device-write-bps /dev/sda:1mb nginx

# 조합
docker run -d \
  --cpus="2" \
  --memory="1g" \
  --memory-swap="2g" \
  nginx
```

---

## 네트워크

### 네트워크 생성 및 관리

```bash
# 네트워크 목록
docker network ls

# 네트워크 생성
docker network create my-network

# 브리지 네트워크 (기본)
docker network create --driver bridge my-bridge

# 네트워크 상세 정보
docker network inspect my-network

# 네트워크 삭제
docker network rm my-network

# 사용하지 않는 네트워크 삭제
docker network prune
```

---

### 컨테이너 네트워크 연결

```bash
# 네트워크 지정하여 실행
docker run -d --name web --network my-network nginx

# 실행 중인 컨테이너에 네트워크 연결
docker network connect my-network <container-id>

# 네트워크 연결 해제
docker network disconnect my-network <container-id>

# 컨테이너 간 통신 (같은 네트워크)
docker run -d --name db --network my-network postgres
docker run -d --name app --network my-network \
  -e DB_HOST=db \  # ← 컨테이너 이름으로 접근!
  my-app
```

---

### 네트워크 모드

```bash
# Bridge (기본)
docker run -d --network bridge nginx

# Host (호스트 네트워크 직접 사용)
docker run -d --network host nginx

# None (네트워크 없음)
docker run -d --network none nginx

# 다른 컨테이너의 네트워크 공유
docker run -d --name web nginx
docker run -d --network container:web my-app
```

---

## 볼륨과 스토리지

### Named Volume

```bash
# 볼륨 생성
docker volume create my-data

# 볼륨 목록
docker volume ls

# 볼륨 상세 정보
docker volume inspect my-data

# 볼륨 사용
docker run -d -v my-data:/data nginx

# 볼륨 삭제
docker volume rm my-data

# 사용하지 않는 볼륨 삭제
docker volume prune

# 모든 볼륨 삭제 (위험!)
docker volume prune -a
```

---

### Bind Mount

```bash
# 절대 경로
docker run -d -v /host/path:/container/path nginx

# 상대 경로 (현재 디렉토리)
docker run -d -v $(pwd):/app my-app

# 읽기 전용
docker run -d -v /host/path:/container/path:ro nginx

# Windows (PowerShell)
docker run -d -v ${PWD}:/app my-app
```

---

### tmpfs (메모리)

```bash
# 메모리에 마운트 (휘발성)
docker run -d --tmpfs /tmp my-app

# 크기 제한
docker run -d --tmpfs /tmp:size=100m my-app
```

---

## Docker Compose

### docker-compose.yml 기본 구조

```yaml
version: '3.8'

services:
  # 웹 애플리케이션
  web:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - DB_HOST=db
    depends_on:
      - db
    networks:
      - app-network
    volumes:
      - app-data:/app/data
    restart: unless-stopped

  # 데이터베이스
  db:
    image: postgres:15
    environment:
      POSTGRES_DB: mydb
      POSTGRES_USER: user
      POSTGRES_PASSWORD: password
    volumes:
      - db-data:/var/lib/postgresql/data
    networks:
      - app-network

  # Redis
  redis:
    image: redis:7-alpine
    networks:
      - app-network

networks:
  app-network:
    driver: bridge

volumes:
  app-data:
  db-data:
```

---

### Docker Compose 명령어

```bash
# 서비스 시작 (백그라운드)
docker-compose up -d

# 특정 서비스만
docker-compose up -d web

# 빌드 포함
docker-compose up -d --build

# 로그 확인
docker-compose logs

# 특정 서비스 로그
docker-compose logs web

# 실시간 로그
docker-compose logs -f web

# 서비스 중지
docker-compose stop

# 서비스 중지 및 삭제
docker-compose down

# 볼륨도 삭제
docker-compose down -v

# 이미지도 삭제
docker-compose down --rmi all

# 실행 중인 서비스 목록
docker-compose ps

# 특정 서비스에서 명령 실행
docker-compose exec web bash

# 서비스 재시작
docker-compose restart web

# 서비스 확장 (스케일링)
docker-compose up -d --scale web=3
```

---

### 실전 예시: Spring Boot + PostgreSQL + Redis

```yaml
version: '3.8'

services:
  app:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: spring-app
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/mydb
      - SPRING_DATASOURCE_USERNAME=postgres
      - SPRING_DATASOURCE_PASSWORD=password
      - SPRING_REDIS_HOST=redis
      - SPRING_REDIS_PORT=6379
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_started
    networks:
      - backend
    restart: unless-stopped

  db:
    image: postgres:15-alpine
    container_name: postgres-db
    environment:
      POSTGRES_DB: mydb
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: password
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql
    ports:
      - "5432:5432"
    networks:
      - backend
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: redis-cache
    ports:
      - "6379:6379"
    networks:
      - backend
    volumes:
      - redis-data:/data

  nginx:
    image: nginx:alpine
    container_name: nginx-proxy
    ports:
      - "80:80"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
    depends_on:
      - app
    networks:
      - backend

networks:
  backend:
    driver: bridge

volumes:
  postgres-data:
  redis-data:
```

---

## 멀티 스테이지 빌드

### 왜 필요한가?

```
단일 스테이지:
- 빌드 도구 포함 (Maven, npm 등)
- 소스 코드 포함
- 최종 이미지 크기: 500MB+

멀티 스테이지:
- 빌드는 첫 스테이지에서
- 실행 파일만 최종 이미지로
- 최종 이미지 크기: 100MB
```

---

### Spring Boot 멀티 스테이지

```dockerfile
# Stage 1: Build
FROM maven:3.8-openjdk-17 AS builder

WORKDIR /build

# 의존성 캐싱
COPY pom.xml .
RUN mvn dependency:go-offline

# 빌드
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM openjdk:17-jdk-slim

WORKDIR /app

# builder 스테이지에서 jar만 복사
COPY --from=builder /build/target/*.jar app.jar

RUN useradd -m appuser
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**결과**:
```
이전: 800MB (Maven + JDK + 소스 포함)
이후: 250MB (JRE + JAR만)
```

---

### Node.js 멀티 스테이지

```dockerfile
# Stage 1: Build
FROM node:18 AS builder

WORKDIR /build

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# Stage 2: Runtime
FROM node:18-slim

WORKDIR /app

# 프로덕션 의존성만
COPY package*.json ./
RUN npm ci --only=production

# 빌드 결과물만 복사
COPY --from=builder /build/dist ./dist

USER node

EXPOSE 3000

CMD ["node", "dist/index.js"]
```

---

## 최적화 기법

### 1. .dockerignore 사용

```bash
# .dockerignore
node_modules
npm-debug.log
.git
.idea
*.md
.env
.DS_Store
target/
build/
*.log
```

---

### 2. 레이어 캐싱 활용

```dockerfile
# ❌ 비효율적 (코드 변경 시 npm install 재실행)
FROM node:18
WORKDIR /app
COPY . .
RUN npm install
CMD ["node", "index.js"]

# ✅ 효율적 (package.json 변경 시에만 npm install)
FROM node:18
WORKDIR /app
COPY package*.json ./
RUN npm install  # ← 캐시됨!
COPY . .
CMD ["node", "index.js"]
```

---

### 3. 작은 베이스 이미지 사용

```dockerfile
# 크기 비교
FROM ubuntu:22.04        # 77MB
FROM debian:bullseye     # 124MB
FROM alpine:3.18         # 7MB  ← 가장 작음

FROM openjdk:17          # 470MB
FROM openjdk:17-slim     # 220MB
FROM openjdk:17-alpine   # 170MB  ← 권장
```

---

### 4. 멀티 스테이지 빌드

```dockerfile
# 빌드 도구는 최종 이미지에 포함 안 됨
FROM maven:3.8 AS builder
# ... 빌드 ...

FROM openjdk:17-slim
COPY --from=builder /build/target/*.jar app.jar
```

---

### 5. 불필요한 파일 제거

```dockerfile
RUN apt-get update && \
    apt-get install -y curl && \
    rm -rf /var/lib/apt/lists/*  # ← 캐시 제거!
```

---

### 6. 레이어 합치기

```dockerfile
# ❌ 3개 레이어
RUN apt-get update
RUN apt-get install -y curl
RUN rm -rf /var/lib/apt/lists/*

# ✅ 1개 레이어 (더 작음)
RUN apt-get update && \
    apt-get install -y curl && \
    rm -rf /var/lib/apt/lists/*
```

---

## 실전 예제

### 1. Spring Boot 애플리케이션

```dockerfile
FROM maven:3.8-openjdk-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
RUN useradd -m appuser
USER appuser
EXPOSE 8080
HEALTHCHECK --interval=30s CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

### 2. React 애플리케이션

```dockerfile
# Build
FROM node:18 AS builder
WORKDIR /build
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Serve with Nginx
FROM nginx:alpine
COPY --from=builder /build/build /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

**nginx.conf**:
```nginx
server {
    listen 80;
    location / {
        root /usr/share/nginx/html;
        try_files $uri /index.html;
    }
}
```

---

### 3. Python Flask 애플리케이션

```dockerfile
FROM python:3.11-slim

WORKDIR /app

# 의존성 설치
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 앱 복사
COPY . .

# 비root 사용자
RUN useradd -m appuser
USER appuser

EXPOSE 5000

# Gunicorn으로 실행
CMD ["gunicorn", "--bind", "0.0.0.0:5000", "app:app"]
```

---

## 보안 모범 사례

### 1. 비root 사용자 실행

```dockerfile
RUN useradd -m -u 1000 appuser
USER appuser
```

---

### 2. 읽기 전용 파일시스템

```bash
docker run --read-only --tmpfs /tmp my-app
```

---

### 3. 비밀 정보 관리

```bash
# ❌ Dockerfile에 하드코딩
ENV DB_PASSWORD=secret123

# ✅ 환경 변수로 주입
docker run -e DB_PASSWORD=$DB_PASSWORD my-app

# ✅ Docker Secrets (Swarm)
echo "password123" | docker secret create db_password -
```

---

### 4. 이미지 스캐닝

```bash
# Trivy로 취약점 스캔
docker run aquasec/trivy image my-app:latest

# Snyk
snyk container test my-app:latest
```

---

### 5. 최소 권한

```bash
# 불필요한 Capability 제거
docker run --cap-drop ALL --cap-add NET_BIND_SERVICE my-app
```

---

## 요약

### 자주 쓰는 명령어 TOP 20

```bash
1.  docker run -d -p 8080:80 nginx     # 컨테이너 실행
2.  docker ps                          # 실행 중인 컨테이너
3.  docker ps -a                       # 모든 컨테이너
4.  docker logs -f <id>                # 로그 실시간
5.  docker exec -it <id> bash          # 컨테이너 접속
6.  docker stop <id>                   # 컨테이너 중지
7.  docker rm <id>                     # 컨테이너 삭제
8.  docker images                      # 이미지 목록
9.  docker build -t my-app .           # 이미지 빌드
10. docker push my-app                 # 이미지 푸시
11. docker pull nginx                  # 이미지 풀
12. docker rmi <id>                    # 이미지 삭제
13. docker-compose up -d               # Compose 시작
14. docker-compose down                # Compose 중지
15. docker network create my-net       # 네트워크 생성
16. docker volume create my-vol        # 볼륨 생성
17. docker inspect <id>                # 상세 정보
18. docker stats                       # 리소스 사용량
19. docker system prune                # 정리
20. docker login                       # 레지스트리 로그인
```

---

## 학습 체크리스트

- [ ] Docker 기본 명령어 숙지
- [ ] Dockerfile 작성 가능
- [ ] 멀티 스테이지 빌드 이해
- [ ] Docker Compose 사용 가능
- [ ] 네트워크와 볼륨 관리
- [ ] 이미지 최적화 기법 적용
- [ ] 보안 모범 사례 숙지
- [ ] 실전 프로젝트 컨테이너화

---

## 참고 자료

- [Docker 공식 문서](https://docs.docker.com/)
- [Dockerfile Best Practices](https://docs.docker.com/develop/dev-best-practices/)
- [Docker Compose 문서](https://docs.docker.com/compose/)
- [Play with Docker](https://labs.play-with-docker.com/) (무료 실습)

---

**"Dockerfile을 잘 작성하는 것이 절반이다."** 🐳
