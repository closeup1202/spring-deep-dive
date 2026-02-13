# Kubernetes Complete Guide

Kubernetes(K8s) 컨테이너 오케스트레이션 플랫폼을 학습합니다.

## 목차

1. [Kubernetes란?](#kubernetes란)
2. [핵심 개념](#핵심-개념)
3. [아키텍처](#아키텍처)
4. [주요 리소스](#주요-리소스)
5. [kubectl 명령어](#kubectl-명령어)
6. [배포 전략](#배포-전략)
7. [서비스와 네트워킹](#서비스와-네트워킹)
8. [스토리지](#스토리지)
9. [설정 관리](#설정-관리)
10. [실전 예제](#실전-예제)

---

## Kubernetes란?

### 정의

**Kubernetes**(K8s)는 컨테이너화된 애플리케이션의 배포, 확장, 관리를 **자동화**하는 오픈소스 플랫폼입니다.

### 왜 필요한가?

```
Docker만 사용:
- 수동 배포
- 수동 스케일링
- 수동 복구
- 서비스 디스커버리 없음

Kubernetes 사용:
- 자동 배포 및 롤백
- 자동 스케일링 (HPA, VPA)
- 자동 복구 (Self-healing)
- 자동 서비스 디스커버리
- 로드 밸런싱
```

---

### Kubernetes가 해결하는 문제

#### 1. 서비스 디스커버리와 로드 밸런싱
```
문제: 컨테이너 IP는 동적으로 변경됨
해결: Service 리소스로 안정적인 엔드포인트 제공
```

#### 2. 자동 복구
```
문제: 컨테이너 죽으면 수동 재시작
해결: Pod 자동 재시작, 노드 장애 시 다른 노드로 이동
```

#### 3. 자동 스케일링
```
문제: 트래픽 증가 시 수동으로 인스턴스 추가
해결: HPA(Horizontal Pod Autoscaler)로 자동 확장
```

#### 4. 롤링 업데이트
```
문제: 무중단 배포 복잡
해결: Deployment의 Rolling Update 전략
```

#### 5. 시크릿과 설정 관리
```
문제: 환경별 설정 관리 복잡
해결: ConfigMap, Secret으로 중앙 관리
```

---

## 핵심 개념

### 1. Desired State (원하는 상태)

```
선언적 관리 (Declarative):

사용자: "Pod 3개를 실행하고 싶어요"
K8s:   "알겠습니다. 항상 3개를 유지하겠습니다"

Pod 1개 죽음 → K8s가 자동으로 새 Pod 시작
```

**YAML로 선언**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-app
spec:
  replicas: 3  # 원하는 상태: Pod 3개
```

---

### 2. Controller (컨트롤러)

```
┌──────────────────────────┐
│   Desired State (3 Pods) │
└────────────┬─────────────┘
             │
             ↓
      ┌─────────────┐
      │ Controller  │  ← 실제 상태를 체크하고 조정
      └─────────────┘
             │
             ↓
┌──────────────────────────┐
│   Current State (2 Pods) │  ← 1개 부족!
└────────────┬─────────────┘
             │
             ↓ Controller가 Pod 1개 추가
┌──────────────────────────┐
│   Current State (3 Pods) │  ← 원하는 상태 달성!
└──────────────────────────┘
```

---

### 3. Namespace (네임스페이스)

가상 클러스터 - 리소스를 논리적으로 분리

```
┌─────────────────────────────────────┐
│           Kubernetes Cluster        │
│                                     │
│  ┌────────────┐  ┌────────────┐   │
│  │   dev      │  │   prod     │   │
│  │  Namespace │  │  Namespace │   │
│  └────────────┘  └────────────┘   │
│                                     │
│  ┌────────────┐  ┌────────────┐   │
│  │   test     │  │   staging  │   │
│  │  Namespace │  │  Namespace │   │
│  └────────────┘  └────────────┘   │
└─────────────────────────────────────┘
```

**기본 Namespace**:
- `default`: 기본 네임스페이스
- `kube-system`: K8s 시스템 컴포넌트
- `kube-public`: 모든 사용자가 읽을 수 있는 리소스
- `kube-node-lease`: 노드 하트비트

---

## 아키텍처

### Kubernetes 클러스터 구조

```
┌────────────────────────────────────────────────────┐
│                 Control Plane (Master)             │
│  ┌──────────────┐  ┌──────────────┐               │
│  │ API Server   │  │  Scheduler   │               │
│  └──────────────┘  └──────────────┘               │
│  ┌──────────────┐  ┌──────────────┐               │
│  │ Controller   │  │    etcd      │               │
│  │  Manager     │  │ (Key-Value)  │               │
│  └──────────────┘  └──────────────┘               │
└────────────────────────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┐
        │               │               │
┌───────▼──────┐ ┌──────▼──────┐ ┌─────▼───────┐
│   Worker 1   │ │   Worker 2  │ │   Worker 3  │
│ ┌──────────┐ │ │ ┌──────────┐│ │ ┌──────────┐│
│ │  Kubelet │ │ │ │  Kubelet ││ │ │  Kubelet ││
│ └──────────┘ │ │ └──────────┘│ │ └──────────┘│
│ ┌──────────┐ │ │ ┌──────────┐│ │ ┌──────────┐│
│ │ kube-    │ │ │ │ kube-    ││ │ │ kube-    ││
│ │ proxy    │ │ │ │ proxy    ││ │ │ proxy    ││
│ └──────────┘ │ │ └──────────┘│ │ └──────────┘│
│ ┌──────────┐ │ │ ┌──────────┐│ │ ┌──────────┐│
│ │Container │ │ │ │Container ││ │ │Container ││
│ │ Runtime  │ │ │ │ Runtime  ││ │ │ Runtime  ││
│ └──────────┘ │ │ └──────────┘│ │ └──────────┘│
└──────────────┘ └─────────────┘ └─────────────┘
```

---

### Control Plane 컴포넌트

#### 1. API Server
- **역할**: 모든 요청의 진입점
- **기능**: 인증, 인가, Validation
- kubectl과 통신하는 REST API

#### 2. etcd
- **역할**: 클러스터의 모든 데이터 저장
- **특징**: 분산 Key-Value 저장소
- 클러스터 상태의 단일 진실 공급원 (Single Source of Truth)

#### 3. Scheduler
- **역할**: Pod를 어느 노드에 배치할지 결정
- **고려 사항**: 리소스, 친화성, 반친화성, Taints/Tolerations

#### 4. Controller Manager
- **역할**: 다양한 컨트롤러 실행
- **예시**: ReplicaSet Controller, Deployment Controller, Node Controller

---

### Worker Node 컴포넌트

#### 1. Kubelet
- **역할**: Pod 생명주기 관리
- API Server와 통신하여 Pod Spec 받음
- 컨테이너 런타임에 명령 전달

#### 2. kube-proxy
- **역할**: 네트워크 프록시
- Service 엔드포인트로 트래픽 라우팅
- iptables 또는 IPVS 사용

#### 3. Container Runtime
- **예시**: containerd, CRI-O, Docker (deprecated)
- 실제 컨테이너 실행

---

## 주요 리소스

### 1. Pod

**가장 작은 배포 단위** - 하나 이상의 컨테이너 그룹

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: my-pod
  labels:
    app: nginx
spec:
  containers:
  - name: nginx
    image: nginx:1.25
    ports:
    - containerPort: 80
```

**특징**:
- 같은 Pod의 컨테이너는 네트워크 공유 (localhost)
- 같은 노드에 스케줄링됨
- 일시적(Ephemeral) - IP는 변경될 수 있음

---

### 2. ReplicaSet

**원하는 수의 Pod 복제본을 유지**

```yaml
apiVersion: apps/v1
kind: ReplicaSet
metadata:
  name: my-replicaset
spec:
  replicas: 3  # Pod 3개 유지
  selector:
    matchLabels:
      app: nginx
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
      - name: nginx
        image: nginx:1.25
```

**동작**:
- Pod 3개 중 1개 죽으면 → 자동으로 새 Pod 시작
- 수동으로 replicas 변경 가능

---

### 3. Deployment (권장!)

**ReplicaSet + 롤링 업데이트**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-app
spec:
  replicas: 3
  selector:
    matchLabels:
      app: nginx
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
      - name: nginx
        image: nginx:1.25
        ports:
        - containerPort: 80
```

**기능**:
- Rolling Update (무중단 배포)
- Rollback (이전 버전으로 되돌리기)
- 버전 히스토리 관리

---

### 4. Service

**Pod에 안정적인 엔드포인트 제공**

#### ClusterIP (기본)
```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-service
spec:
  type: ClusterIP
  selector:
    app: nginx
  ports:
  - port: 80
    targetPort: 80
```

**특징**:
- 클러스터 내부에서만 접근
- 고정 IP 제공

---

#### NodePort
```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-service
spec:
  type: NodePort
  selector:
    app: nginx
  ports:
  - port: 80
    targetPort: 80
    nodePort: 30080  # 30000-32767 범위
```

**특징**:
- 모든 노드의 특정 포트로 접근 가능
- `<NodeIP>:30080`으로 접근

---

#### LoadBalancer
```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-service
spec:
  type: LoadBalancer
  selector:
    app: nginx
  ports:
  - port: 80
    targetPort: 80
```

**특징**:
- 클라우드 제공자의 로드 밸런서 생성 (AWS ELB, GCP LB)
- 외부 IP 할당

---

### 5. Ingress

**HTTP/HTTPS 라우팅**

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: my-ingress
spec:
  rules:
  - host: example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: my-service
            port:
              number: 80
```

**기능**:
- 도메인 기반 라우팅
- Path 기반 라우팅
- TLS/SSL 종료

---

### 6. ConfigMap

**비밀하지 않은 설정 데이터**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
data:
  database.url: "jdbc:postgresql://db:5432/mydb"
  app.env: "production"
```

**사용**:
```yaml
spec:
  containers:
  - name: app
    env:
    - name: DB_URL
      valueFrom:
        configMapKeyRef:
          name: app-config
          key: database.url
```

---

### 7. Secret

**비밀 데이터 (Base64 인코딩)**

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: db-secret
type: Opaque
data:
  username: YWRtaW4=  # admin (base64)
  password: cGFzc3dvcmQxMjM=  # password123 (base64)
```

**사용**:
```yaml
spec:
  containers:
  - name: app
    env:
    - name: DB_PASSWORD
      valueFrom:
        secretKeyRef:
          name: db-secret
          key: password
```

---

### 8. PersistentVolume & PersistentVolumeClaim

**영속 스토리지**

```yaml
# PersistentVolume (관리자 생성)
apiVersion: v1
kind: PersistentVolume
metadata:
  name: pv-data
spec:
  capacity:
    storage: 10Gi
  accessModes:
    - ReadWriteOnce
  hostPath:
    path: /data

---
# PersistentVolumeClaim (사용자 요청)
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: pvc-data
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 5Gi

---
# Pod에서 사용
spec:
  containers:
  - name: app
    volumeMounts:
    - name: data
      mountPath: /app/data
  volumes:
  - name: data
    persistentVolumeClaim:
      claimName: pvc-data
```

---

## kubectl 명령어

### 기본 명령어

```bash
# 클러스터 정보
kubectl cluster-info
kubectl version

# 노드 목록
kubectl get nodes

# 모든 리소스
kubectl get all

# 특정 리소스
kubectl get pods
kubectl get deployments
kubectl get services
kubectl get ingress
```

---

### Pod 관리

```bash
# Pod 목록
kubectl get pods

# 다른 네임스페이스
kubectl get pods -n kube-system

# 모든 네임스페이스
kubectl get pods --all-namespaces
# 또는
kubectl get pods -A

# 상세 정보
kubectl describe pod <pod-name>

# Pod 생성
kubectl run nginx --image=nginx

# Pod 삭제
kubectl delete pod <pod-name>

# Pod 로그
kubectl logs <pod-name>

# 실시간 로그
kubectl logs -f <pod-name>

# 여러 컨테이너 중 특정 컨테이너
kubectl logs <pod-name> -c <container-name>

# Pod 내부 쉘 실행
kubectl exec -it <pod-name> -- bash

# 포트 포워딩
kubectl port-forward <pod-name> 8080:80
```

---

### Deployment 관리

```bash
# Deployment 생성
kubectl create deployment nginx --image=nginx

# Deployment 목록
kubectl get deployments

# 스케일링
kubectl scale deployment nginx --replicas=5

# 이미지 업데이트
kubectl set image deployment/nginx nginx=nginx:1.26

# 롤아웃 상태 확인
kubectl rollout status deployment/nginx

# 롤아웃 히스토리
kubectl rollout history deployment/nginx

# 롤백 (이전 버전으로)
kubectl rollout undo deployment/nginx

# 특정 리비전으로 롤백
kubectl rollout undo deployment/nginx --to-revision=2

# Deployment 삭제
kubectl delete deployment nginx
```

---

### Service 관리

```bash
# Service 생성
kubectl expose deployment nginx --port=80 --type=LoadBalancer

# Service 목록
kubectl get services
# 또는
kubectl get svc

# Service 상세 정보
kubectl describe service nginx

# Service 삭제
kubectl delete service nginx
```

---

### 리소스 관리 (YAML 기반)

```bash
# YAML 파일로 생성
kubectl apply -f deployment.yaml

# 여러 파일
kubectl apply -f deployment.yaml -f service.yaml

# 디렉토리의 모든 YAML
kubectl apply -f ./configs/

# 리소스 삭제
kubectl delete -f deployment.yaml

# 실행 중인 리소스 YAML 가져오기
kubectl get deployment nginx -o yaml > nginx.yaml

# JSON 형식
kubectl get deployment nginx -o json
```

---

### 네임스페이스 관리

```bash
# 네임스페이스 목록
kubectl get namespaces
# 또는
kubectl get ns

# 네임스페이스 생성
kubectl create namespace dev

# 네임스페이스 삭제
kubectl delete namespace dev

# 기본 네임스페이스 변경
kubectl config set-context --current --namespace=dev

# 특정 네임스페이스의 리소스
kubectl get pods -n dev
```

---

### 디버깅 및 트러블슈팅

```bash
# 이벤트 확인
kubectl get events

# 특정 리소스 이벤트
kubectl describe pod <pod-name>

# 노드 상태 확인
kubectl describe node <node-name>

# 리소스 사용량
kubectl top nodes
kubectl top pods

# Pod가 Pending인 이유 확인
kubectl describe pod <pod-name> | grep -A 5 Events

# YAML 검증
kubectl apply --dry-run=client -f deployment.yaml

# 서버 측 검증
kubectl apply --dry-run=server -f deployment.yaml
```

---

### Context 관리

```bash
# Context 목록
kubectl config get-contexts

# 현재 Context
kubectl config current-context

# Context 전환
kubectl config use-context minikube

# Context 생성
kubectl config set-context dev --namespace=dev --cluster=minikube --user=minikube
```

---

## 배포 전략

### 1. Rolling Update (기본)

```
기존 Pod를 하나씩 교체

v1: ▓▓▓▓▓
    ↓↓↓↓↓
v2: ▓░░░░ → ▓▓░░░ → ▓▓▓░░ → ▓▓▓▓░ → ▓▓▓▓▓

무중단 배포 ✅
느린 배포 ⚠️
```

```yaml
spec:
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1        # 최대 1개 초과 생성
      maxUnavailable: 1  # 최대 1개 중지 가능
```

---

### 2. Recreate

```
모든 Pod 삭제 후 새로 생성

v1: ▓▓▓▓▓
    ↓
    ░░░░░ (다운타임!)
    ↓
v2: ▓▓▓▓▓

빠른 배포 ✅
다운타임 ❌
```

```yaml
spec:
  strategy:
    type: Recreate
```

---

### 3. Blue-Green (수동)

```
새 버전(Green) 준비 후 한 번에 전환

Blue:  ▓▓▓▓▓ (Service ← 여기)
Green: ▓▓▓▓▓ (준비 완료)
         ↓
Blue:  ▓▓▓▓▓
Green: ▓▓▓▓▓ (Service ← 전환!)

빠른 롤백 ✅
리소스 2배 ❌
```

```bash
# 1. Green 배포
kubectl apply -f deployment-green.yaml

# 2. Service를 Green으로 전환
kubectl patch service my-app -p '{"spec":{"selector":{"version":"green"}}}'

# 3. Blue 삭제
kubectl delete deployment my-app-blue
```

---

### 4. Canary (점진적)

```
일부 트래픽만 새 버전으로 보냄

v1: ▓▓▓▓▓ (90% 트래픽)
v2: ▓     (10% 트래픽)
    ↓
v1: ▓▓▓   (50% 트래픽)
v2: ▓▓▓   (50% 트래픽)
    ↓
v1: ░░░░░ (삭제)
v2: ▓▓▓▓▓ (100% 트래픽)

위험 최소화 ✅
복잡함 ⚠️
```

```yaml
# v1: 9개 replicas
---
# v2: 1개 replica (10% 트래픽)
spec:
  replicas: 1
```

---

## 서비스와 네트워킹

### Service Type 비교

| Type | 접근 범위 | 사용 사례 |
|------|---------|----------|
| **ClusterIP** | 클러스터 내부 | 내부 마이크로서비스 |
| **NodePort** | 외부 (노드 포트) | 개발/테스트 |
| **LoadBalancer** | 외부 (LB) | 프로덕션 |
| **ExternalName** | 외부 DNS | 외부 서비스 연동 |

---

### DNS 기반 서비스 디스커버리

```
Pod에서 다른 Service 접근:

<service-name>.<namespace>.svc.cluster.local

예시:
- my-service (같은 네임스페이스)
- my-service.default
- my-service.default.svc.cluster.local
```

---

## 스토리지

### Volume 종류

#### 1. emptyDir (임시)
```yaml
volumes:
- name: cache
  emptyDir: {}
```

- Pod와 생명주기 같음
- Pod 삭제 시 데이터 사라짐

---

#### 2. hostPath (노드 로컬)
```yaml
volumes:
- name: data
  hostPath:
    path: /data
    type: Directory
```

- 노드의 파일시스템 마운트
- Pod 재시작해도 유지
- 노드 변경 시 데이터 없음

---

#### 3. PersistentVolume (영속)
```yaml
volumes:
- name: data
  persistentVolumeClaim:
    claimName: my-pvc
```

- 노드와 무관하게 유지
- 클라우드 스토리지 (EBS, GCE PD)

---

## 설정 관리

### ConfigMap 사용 방법

#### 1. 환경 변수로
```yaml
env:
- name: DB_URL
  valueFrom:
    configMapKeyRef:
      name: app-config
      key: database.url
```

#### 2. 파일로 마운트
```yaml
volumeMounts:
- name: config
  mountPath: /config
volumes:
- name: config
  configMap:
    name: app-config
```

---

### Secret 생성

```bash
# 명령어로 생성
kubectl create secret generic db-secret \
  --from-literal=username=admin \
  --from-literal=password=password123

# 파일에서 생성
kubectl create secret generic tls-secret \
  --from-file=tls.crt \
  --from-file=tls.key

# YAML로 (Base64 인코딩 필요)
echo -n 'admin' | base64  # YWRtaW4=
```

---

## 실전 예제

### Spring Boot 애플리케이션 배포

#### 1. Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spring-app
spec:
  replicas: 3
  selector:
    matchLabels:
      app: spring-app
  template:
    metadata:
      labels:
        app: spring-app
    spec:
      containers:
      - name: app
        image: myregistry/spring-app:1.0
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production"
        - name: DB_HOST
          valueFrom:
            configMapKeyRef:
              name: app-config
              key: db.host
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: password
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
```

---

#### 2. Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: spring-app-service
spec:
  type: LoadBalancer
  selector:
    app: spring-app
  ports:
  - port: 80
    targetPort: 8080
```

---

#### 3. ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
data:
  db.host: "postgres-service"
  db.port: "5432"
  db.name: "mydb"
```

---

#### 4. Secret

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: db-secret
type: Opaque
data:
  password: cGFzc3dvcmQxMjM=  # password123
```

---

#### 5. Ingress

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: spring-app-ingress
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  rules:
  - host: myapp.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: spring-app-service
            port:
              number: 80
```

---

#### 배포 명령어

```bash
# ConfigMap 생성
kubectl apply -f configmap.yaml

# Secret 생성
kubectl apply -f secret.yaml

# Deployment 배포
kubectl apply -f deployment.yaml

# Service 생성
kubectl apply -f service.yaml

# Ingress 생성
kubectl apply -f ingress.yaml

# 상태 확인
kubectl get all
kubectl get pods
kubectl logs -f <pod-name>

# 스케일링
kubectl scale deployment spring-app --replicas=5

# 롤링 업데이트
kubectl set image deployment/spring-app app=myregistry/spring-app:2.0

# 롤백
kubectl rollout undo deployment/spring-app
```

---

## 모범 사례

### 1. 리소스 제한 설정

```yaml
resources:
  requests:  # 최소 보장
    memory: "256Mi"
    cpu: "250m"
  limits:    # 최대 허용
    memory: "512Mi"
    cpu: "500m"
```

---

### 2. Health Check 설정

```yaml
livenessProbe:   # 살아있는지 (재시작 기준)
  httpGet:
    path: /health
    port: 8080
  initialDelaySeconds: 30

readinessProbe:  # 준비됐는지 (트래픽 기준)
  httpGet:
    path: /health
    port: 8080
  initialDelaySeconds: 10
```

---

### 3. Label과 Selector 활용

```yaml
metadata:
  labels:
    app: myapp
    version: v1
    environment: production
```

---

### 4. Namespace로 환경 분리

```bash
kubectl create namespace dev
kubectl create namespace staging
kubectl create namespace production
```

---

## 학습 체크리스트

- [ ] Kubernetes 기본 개념 이해
- [ ] Pod, Deployment, Service 차이 구분
- [ ] kubectl 기본 명령어 숙지
- [ ] YAML 파일 작성 가능
- [ ] ConfigMap, Secret 사용
- [ ] Rolling Update 이해
- [ ] Ingress로 외부 노출
- [ ] 실전 애플리케이션 배포 경험

---

## 참고 자료

- [Kubernetes 공식 문서](https://kubernetes.io/docs/)
- [Kubernetes The Hard Way](https://github.com/kelseyhightower/kubernetes-the-hard-way)
- [Play with Kubernetes](https://labs.play-with-k8s.com/)
- [K8s by Example](https://k8s-examples.container-solutions.com/)

---

**"Kubernetes는 컨테이너 오케스트레이션의 표준이다."** ☸️
