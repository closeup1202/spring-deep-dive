# Git Commands Reference

Git 명령어 완벽 가이드 - 기초부터 고급까지

## 목차

1. [Git 기초](#git-기초)
2. [저장소 초기화 및 설정](#저장소-초기화-및-설정)
3. [기본 작업 흐름](#기본-작업-흐름)
4. [브랜치 관리](#브랜치-관리)
5. [원격 저장소 협업](#원격-저장소-협업)
6. [변경 사항 되돌리기](#변경-사항-되돌리기)
7. [히스토리 조회](#히스토리-조회)
8. [고급 명령어](#고급-명령어)
9. [Git 설정](#git-설정)
10. [유용한 팁과 트릭](#유용한-팁과-트릭)

---

## Git 기초

### Git의 3가지 영역

```
Working Directory  →  Staging Area  →  Local Repository  →  Remote Repository
   (작업 공간)         (준비 영역)        (로컬 저장소)          (원격 저장소)
        ↓                  ↓                  ↓                    ↓
   수정된 파일         git add           git commit            git push
```

### 파일의 상태

- **Untracked**: Git이 추적하지 않는 새 파일
- **Modified**: 수정되었지만 아직 Staged 안 됨
- **Staged**: 커밋할 준비가 된 파일
- **Committed**: 로컬 저장소에 저장됨

---

## 저장소 초기화 및 설정

### 새 저장소 만들기

```bash
# 현재 디렉토리를 Git 저장소로 초기화
git init

# 새 디렉토리를 만들고 Git 저장소 초기화
git init my-project
cd my-project
```

### 기존 저장소 복제

```bash
# 원격 저장소 복제
git clone https://github.com/user/repo.git

# 특정 브랜치만 복제
git clone -b develop https://github.com/user/repo.git

# 특정 디렉토리 이름으로 복제
git clone https://github.com/user/repo.git my-folder
```

---

## 기본 작업 흐름

### 1. 파일 상태 확인

```bash
# 현재 상태 확인 (수정, Staged, Untracked 파일)
git status

# 간결한 형식으로 상태 확인
git status -s
```

**출력 예시**:
```
M  README.md       (Modified, Staged)
 M src/Main.java   (Modified, not Staged)
?? newfile.txt     (Untracked)
```

---

### 2. 변경 사항 Staging

```bash
# 특정 파일 추가
git add file.txt

# 여러 파일 추가
git add file1.txt file2.txt

# 현재 디렉토리의 모든 변경 사항 추가
git add .

# 모든 변경 사항 추가 (삭제 포함)
git add -A

# 수정/삭제만 추가 (새 파일 제외)
git add -u

# 대화형 모드로 선택적 추가
git add -p
```

**`git add -p` 사용 예시**:
```bash
# 변경 사항을 청크 단위로 선택
y - 이 청크를 stage
n - 이 청크를 무시
s - 청크를 더 작게 분할
e - 수동으로 편집
```

---

### 3. 커밋 만들기

```bash
# Staged 파일을 커밋 (에디터 열림)
git commit

# 커밋 메시지와 함께 커밋
git commit -m "feat: add user authentication"

# Staging을 건너뛰고 커밋 (tracked 파일만)
git commit -am "fix: resolve login bug"

# 빈 커밋 (CI 재실행 등에 유용)
git commit --allow-empty -m "chore: trigger CI"

# 마지막 커밋 수정 (메시지 변경)
git commit --amend -m "new commit message"

# 마지막 커밋에 파일 추가 (메시지 유지)
git add forgotten-file.txt
git commit --amend --no-edit
```

**좋은 커밋 메시지 작성법**:
```
<type>: <subject>

<body>

<footer>
```

**타입**:
- `feat`: 새로운 기능
- `fix`: 버그 수정
- `docs`: 문서 수정
- `style`: 코드 포맷팅
- `refactor`: 리팩토링
- `test`: 테스트 추가
- `chore`: 빌드/설정 변경

**예시**:
```
feat: add user profile page

- Add user profile component
- Implement profile edit functionality
- Add avatar upload feature

Closes #123
```

---

### 4. 변경 사항 확인

```bash
# Working Directory vs Staging Area
git diff

# Staging Area vs Last Commit
git diff --staged
# 또는
git diff --cached

# 특정 파일의 차이
git diff file.txt

# 두 커밋 비교
git diff commit1 commit2

# 브랜치 간 비교
git diff main..feature

# 통계만 보기
git diff --stat
```

---

## 브랜치 관리

### 브랜치 생성 및 전환

```bash
# 브랜치 목록 보기
git branch

# 원격 브랜치 포함 모든 브랜치 보기
git branch -a

# 새 브랜치 생성
git branch feature/login

# 새 브랜치 생성 및 전환
git checkout -b feature/signup
# 또는 (Git 2.23+)
git switch -c feature/signup

# 브랜치 전환
git checkout main
# 또는
git switch main

# 원격 브랜치 기반으로 로컬 브랜치 생성
git checkout -b feature origin/feature
# 또는
git switch -c feature origin/feature

# 이전 브랜치로 돌아가기
git checkout -
# 또는
git switch -
```

---

### 브랜치 병합

```bash
# feature 브랜치를 현재 브랜치에 병합
git merge feature

# Fast-forward 금지 (병합 커밋 생성)
git merge --no-ff feature

# 충돌 발생 시 병합 중단
git merge --abort

# Squash 병합 (모든 커밋을 하나로)
git merge --squash feature
git commit -m "feat: add login feature"


*Fast-forward라고 나오는 건 master에서 따로 새 커밋을 만든 적이 없어서, Git이 그냥 포인터만 앞으로 옮긴 상황

```

**병합 전략**:

1. **Fast-forward** (기본):
```
main:    A---B
              \
feature:       C---D

merge 후:
main:    A---B---C---D
```

2. **3-way merge** (--no-ff):
```
main:    A---B-------M
              \     /
feature:       C---D

M = 병합 커밋
```

---

### 브랜치 Rebase

```bash
# 현재 브랜치를 main 위로 재배치
git rebase main

# 대화형 리베이스 (최근 3개 커밋)
git rebase -i HEAD~3

# 충돌 해결 후 계속
git rebase --continue

# 리베이스 중단
git rebase --abort

# 리베이스 건너뛰기
git rebase --skip
```

**대화형 리베이스 옵션**:
```
pick   = 커밋 유지
reword = 커밋 메시지 수정
edit   = 커밋 수정
squash = 이전 커밋과 합치기
fixup  = squash와 같지만 메시지 버림
drop   = 커밋 삭제
```

**예시**:
```bash
git rebase -i HEAD~3

# 에디터에서:
pick abc1234 feat: add login
squash def5678 fix: typo
reword ghi9012 feat: add signup
```

---

### 브랜치 삭제

```bash
# 병합된 브랜치 삭제
git branch -d feature

# 강제 삭제 (병합 안 되어도)
git branch -D feature

# 원격 브랜치 삭제
git push origin --delete feature
# 또는
git push origin :feature
```

---

## 원격 저장소 협업

### 원격 저장소 관리

```bash
# 원격 저장소 목록
git remote

# 원격 저장소 상세 정보
git remote -v

# 원격 저장소 추가
git remote add origin https://github.com/user/repo.git

# 원격 저장소 URL 변경
git remote set-url origin https://github.com/user/new-repo.git

# 원격 저장소 이름 변경
git remote rename origin upstream

# 원격 저장소 제거
git remote remove origin
```

---

### Push (업로드)

```bash
# 현재 브랜치를 origin으로 푸시
git push

# 처음 푸시 시 upstream 설정
git push -u origin main
# 이후부터는 git push만 해도 됨

# 특정 브랜치 푸시
git push origin feature

# 모든 브랜치 푸시
git push --all

# 태그 푸시
git push --tags

# 강제 푸시 (위험!)
git push --force
# 더 안전한 강제 푸시
git push --force-with-lease
```

---

### Fetch (다운로드만)

```bash
# 원격 저장소의 변경 사항 가져오기 (병합 안 함)
git fetch

# 특정 원격 저장소에서 가져오기
git fetch origin

# 모든 원격 저장소에서 가져오기
git fetch --all

# 삭제된 원격 브랜치 정리
git fetch --prune
# 또는
git fetch -p
```

---

### Pull (다운로드 + 병합)

```bash
# 원격 브랜치를 가져와서 현재 브랜치에 병합
git pull

# 특정 브랜치 pull
git pull origin main

# Rebase로 pull (병합 커밋 없이)
git pull --rebase

# Fast-forward만 허용
git pull --ff-only
```

**Pull vs Fetch + Merge**:
```bash
# 이 두 개는 같음:
git pull

git fetch
git merge origin/main
```

---

### Pull Request 워크플로우

```bash
# 1. main 브랜치에서 시작
git checkout main
git pull

# 2. 새 기능 브랜치 생성
git checkout -b feature/new-feature

# 3. 작업 및 커밋
git add .
git commit -m "feat: add new feature"

# 4. 원격에 푸시
git push -u origin feature/new-feature

# 5. GitHub에서 Pull Request 생성

# 6. 리뷰 후 수정 사항 반영
git add .
git commit -m "fix: address review comments"
git push

# 7. PR 병합 후 로컬 정리
git checkout main
git pull
git branch -d feature/new-feature
```

---

## 변경 사항 되돌리기

### Working Directory 되돌리기

```bash
# 특정 파일의 변경 사항 취소
git checkout -- file.txt
# 또는 (Git 2.23+)
git restore file.txt

# 모든 변경 사항 취소
git checkout -- .
# 또는
git restore .

# 특정 커밋의 파일로 되돌리기
git checkout abc1234 -- file.txt
```

---

### Staging Area 되돌리기

```bash
# 특정 파일 unstage
git reset HEAD file.txt
# 또는 (Git 2.23+)
git restore --staged file.txt

# 모든 파일 unstage
git reset HEAD
# 또는
git restore --staged .
```

---

### 커밋 되돌리기

```bash
# 마지막 커밋 취소 (변경 사항은 유지, Staged 상태)
git reset --soft HEAD~1

# 마지막 커밋 취소 (변경 사항은 유지, Unstaged 상태)
git reset HEAD~1
# 또는
git reset --mixed HEAD~1

# 마지막 커밋 취소 (변경 사항도 삭제, 위험!)
git reset --hard HEAD~1

# 특정 커밋으로 되돌리기
git reset --hard abc1234

# 특정 커밋을 되돌리는 새 커밋 생성 (안전)
git revert abc1234

# 여러 커밋 되돌리기
git revert abc1234..def5678
```

**reset vs revert**:

| 명령어 | 히스토리 변경 | 협업 | 사용 시기 |
|--------|--------------|------|-----------|
| `reset` | ✅ 변경됨 | ❌ 위험 | 로컬에서만 |
| `revert` | ❌ 유지됨 | ✅ 안전 | 공유된 커밋 |

---

### 실수 복구

```bash
# reflog로 실수 전 상태 찾기
git reflog

# 출력 예시:
# abc1234 HEAD@{0}: reset: moving to HEAD~1
# def5678 HEAD@{1}: commit: feat: add feature
# ghi9012 HEAD@{2}: commit: fix: bug fix

# 특정 상태로 복구
git reset --hard HEAD@{1}

# 삭제한 브랜치 복구
git reflog
git checkout -b recovered-branch abc1234
```

---

## 히스토리 조회

### 커밋 로그

```bash
# 기본 로그
git log

# 한 줄로 간결하게
git log --oneline

# 그래프로 보기
git log --graph --oneline --all

# 최근 N개 커밋
git log -5

# 특정 날짜 이후
git log --since="2024-01-01"
git log --since="2 weeks ago"

# 특정 작성자
git log --author="John"

# 커밋 메시지로 검색
git log --grep="fix"

# 파일 변경 이력
git log -- file.txt

# 파일 내용 변경 추적
git log -p file.txt

# 통계와 함께
git log --stat

# 커스텀 포맷
git log --pretty=format:"%h - %an, %ar : %s"
```

**유용한 로그 별칭**:
```bash
git config --global alias.lg "log --graph --pretty=format:'%Cred%h%Creset -%C(yellow)%d%Creset %s %Cgreen(%cr) %C(bold blue)<%an>%Creset' --abbrev-commit"

# 사용:
git lg
```

---

### 커밋 검색

```bash
# 특정 텍스트가 추가/삭제된 커밋 찾기
git log -S "function_name"

# 정규식으로 검색
git log -G "regex_pattern"

# 특정 함수 변경 이력
git log -L :function_name:file.c
```

---

### Blame (코드 작성자 찾기)

```bash
# 파일의 각 줄이 누가 언제 수정했는지
git blame file.txt

# 특정 줄만 보기
git blame -L 10,20 file.txt

# 이메일 대신 이름 표시
git blame -e file.txt
```

---

### 특정 커밋 조회

```bash
# 커밋 상세 정보
git show abc1234

# 특정 파일만
git show abc1234:path/to/file.txt

# 태그 정보
git show v1.0.0
```

---

## 고급 명령어

### Stash (임시 저장)

```bash
# 현재 변경 사항 임시 저장
git stash

# 메시지와 함께 저장
git stash save "WIP: working on feature"

# Untracked 파일도 포함
git stash -u

# Stash 목록 보기
git stash list

# Stash 적용 (유지)
git stash apply

# Stash 적용 (삭제)
git stash pop

# 특정 Stash 적용
git stash apply stash@{2}

# Stash 삭제
git stash drop stash@{0}

# 모든 Stash 삭제
git stash clear

# Stash를 브랜치로
git stash branch feature-branch
```

**Stash 사용 시나리오**:
```bash
# 1. 긴급 버그 수정 상황
git stash                    # 현재 작업 저장
git checkout main            # main으로 이동
git checkout -b hotfix       # 핫픽스 브랜치 생성
# ... 버그 수정 ...
git checkout feature         # 원래 브랜치로
git stash pop                # 작업 복구
```

---

### Cherry-pick (커밋 선택 가져오기)

```bash
# 특정 커밋을 현재 브랜치에 적용
git cherry-pick abc1234

# 여러 커밋 가져오기
git cherry-pick abc1234 def5678

# 범위로 가져오기
git cherry-pick abc1234..def5678

# 충돌 시 계속
git cherry-pick --continue

# Cherry-pick 중단
git cherry-pick --abort
```

---

### Tag (태그)

```bash
# 태그 목록
git tag

# 패턴으로 검색
git tag -l "v1.0.*"

# Lightweight 태그 (포인터만)
git tag v1.0.0

# Annotated 태그 (메타데이터 포함, 권장)
git tag -a v1.0.0 -m "Release version 1.0.0"

# 특정 커밋에 태그
git tag -a v1.0.0 abc1234 -m "Release 1.0.0"

# 태그 삭제
git tag -d v1.0.0

# 원격에 태그 푸시
git push origin v1.0.0

# 모든 태그 푸시
git push --tags

# 원격 태그 삭제
git push origin --delete v1.0.0
```

---

### Submodule (서브모듈)

```bash
# 서브모듈 추가
git submodule add https://github.com/user/lib.git libs/lib

# 서브모듈 초기화
git submodule init

# 서브모듈 업데이트
git submodule update

# 서브모듈 포함하여 클론
git clone --recursive https://github.com/user/repo.git

# 서브모듈 제거
git submodule deinit libs/lib
git rm libs/lib
```

---

### Worktree (작업 트리)

```bash
# 동일 저장소의 다른 브랜치를 별도 디렉토리에서 작업
git worktree add ../hotfix hotfix-branch

# Worktree 목록
git worktree list

# Worktree 제거
git worktree remove ../hotfix
```

---

### Bisect (이진 탐색으로 버그 찾기)

```bash
# Bisect 시작
git bisect start

# 현재 커밋이 나쁨
git bisect bad

# 특정 커밋이 좋음
git bisect good abc1234

# Git이 중간 커밋으로 이동
# 테스트 후:
git bisect good  # 또는
git bisect bad

# 반복하면 문제 커밋 찾아줌

# Bisect 종료
git bisect reset
```

---

### Clean (Untracked 파일 삭제)

```bash
# 삭제될 파일 미리 보기
git clean -n

# Untracked 파일 삭제
git clean -f

# 디렉토리도 삭제
git clean -fd

# .gitignore에 있는 파일도 삭제
git clean -fx

# 대화형 모드
git clean -i
```

---

## Git 설정

### 사용자 정보

```bash
# 전역 설정
git config --global user.name "Your Name"
git config --global user.email "your@email.com"

# 저장소별 설정
git config user.name "Work Name"
git config user.email "work@company.com"

# 설정 확인
git config user.name
git config --list

# 설정 파일 위치
# --global: ~/.gitconfig
# --local:  .git/config
```

---

### 에디터 설정

```bash
# 기본 에디터 설정
git config --global core.editor "vim"
git config --global core.editor "code --wait"  # VS Code
git config --global core.editor "nano"
```

---

### 별칭 (Alias)

```bash
# 자주 쓰는 명령어 별칭
git config --global alias.co checkout
git config --global alias.br branch
git config --global alias.ci commit
git config --global alias.st status
git config --global alias.unstage 'reset HEAD --'
git config --global alias.last 'log -1 HEAD'
git config --global alias.visual 'log --graph --oneline --all'

# 사용:
git co main      # git checkout main
git unstage file.txt
```

---

### 줄바꿈 설정

```bash
# Windows
git config --global core.autocrlf true

# Mac/Linux
git config --global core.autocrlf input

# 변환 안 함
git config --global core.autocrlf false
```

---

### 색상 설정

```bash
# 컬러 활성화
git config --global color.ui auto

# 비활성화
git config --global color.ui false
```

---

## 유용한 팁과 트릭

### 1. 마지막 커밋 메시지 빠르게 수정

```bash
git commit --amend -m "new message"
```

---

### 2. 모든 브랜치에서 검색

```bash
git grep "search_term" $(git rev-list --all)
```

---

### 3. 파일 삭제 히스토리 찾기

```bash
git log --all --full-history -- path/to/file
```

---

### 4. 특정 커밋의 파일 복구

```bash
git checkout abc1234 -- path/to/file
```

---

### 5. 빈 디렉토리 추적

```bash
# .gitkeep 파일 생성
touch empty-dir/.gitkeep
git add empty-dir/.gitkeep
```

---

### 6. 대소문자 변경 감지

```bash
# 대소문자 구분 활성화
git config core.ignorecase false

# 파일명 변경
git mv oldName.txt OldName.txt
```

---

### 7. 임시 커밋 (WIP)

```bash
# 작업 중 임시 저장
git add .
git commit -m "WIP"

# 나중에 이어서 작업 후
git add .
git commit --amend --no-edit
```

---

### 8. 특정 파일 무시 (로컬만)

```bash
# .git/info/exclude 파일에 추가
echo "local-config.json" >> .git/info/exclude
```

---

### 9. 커밋 템플릿

```bash
# 템플릿 파일 생성
cat > ~/.gitmessage << EOF
# <type>: <subject>
#
# <body>
#
# <footer>
EOF

# 템플릿 설정
git config --global commit.template ~/.gitmessage
```

---

### 10. 자동 줄바꿈 제거

```bash
git config --global core.whitespace trailing-space,space-before-tab
git diff --check
```

---

## .gitignore 패턴

```bash
# 특정 파일
secret.txt

# 특정 확장자
*.log
*.tmp

# 특정 디렉토리
node_modules/
build/

# 예외 (무시하지 않음)
!important.log

# 특정 경로만
/root-only.txt

# 와일드카드
**/logs
*.class

# 주석
# This is a comment
```

**일반적인 .gitignore 예시**:
```bash
# Java
*.class
*.jar
target/

# Node.js
node_modules/
npm-debug.log

# IDE
.idea/
.vscode/
*.iml

# OS
.DS_Store
Thumbs.db

# 환경 설정
.env
application-local.yml
```

---

## Git 워크플로우

### Git Flow

```
main (프로덕션)
  ↑
develop (개발)
  ↑
feature/* (기능 개발)
release/* (릴리스 준비)
hotfix/* (긴급 수정)
```

**명령어 예시**:
```bash
# Feature 시작
git checkout -b feature/login develop

# Feature 완료
git checkout develop
git merge --no-ff feature/login
git branch -d feature/login

# Release 시작
git checkout -b release/1.0.0 develop

# Release 완료
git checkout main
git merge --no-ff release/1.0.0
git tag -a 1.0.0
git checkout develop
git merge --no-ff release/1.0.0
git branch -d release/1.0.0
```

---

### GitHub Flow (간단)

```
main (항상 배포 가능)
  ↑
feature/* (기능 브랜치)
```

**워크플로우**:
```bash
# 1. 브랜치 생성
git checkout -b feature/new-feature

# 2. 커밋
git commit -m "feat: add feature"

# 3. 푸시
git push -u origin feature/new-feature

# 4. Pull Request 생성 (GitHub)

# 5. 리뷰 및 병합

# 6. 배포

# 7. 브랜치 삭제
git branch -d feature/new-feature
```

---

## 트러블슈팅

### 1. 충돌 해결

```bash
# 충돌 발생 시
git status  # 충돌 파일 확인

# 파일 편집 (충돌 마커 제거)
<<<<<<< HEAD
현재 브랜치 내용
=======
병합하려는 브랜치 내용
>>>>>>> feature

# 해결 후
git add conflicted-file.txt
git commit
```

---

### 2. Push 거부 (non-fast-forward)

```bash
# 원격에 새 커밋이 있을 때
git pull --rebase
git push
```

---

### 3. 잘못된 브랜치에 커밋

```bash
# 커밋을 올바른 브랜치로 이동
git checkout correct-branch
git cherry-pick abc1234

# 잘못된 브랜치에서 제거
git checkout wrong-branch
git reset --hard HEAD~1
```

---

### 4. 대용량 파일 실수로 커밋

```bash
# Git LFS 사용
git lfs install
git lfs track "*.psd"
git add .gitattributes

# 이미 커밋된 파일 제거
git filter-branch --tree-filter 'rm -f large-file.zip' HEAD
# 또는 (Git 2.38+)
git filter-repo --path large-file.zip --invert-paths
```

---

## 요약

### 자주 쓰는 명령어 TOP 20

```bash
1.  git status                    # 상태 확인
2.  git add .                     # 모든 변경사항 추가
3.  git commit -m "message"       # 커밋
4.  git push                      # 원격에 푸시
5.  git pull                      # 원격에서 가져오기
6.  git checkout -b branch        # 브랜치 생성 및 전환
7.  git merge branch              # 브랜치 병합
8.  git branch -d branch          # 브랜치 삭제
9.  git log --oneline             # 간결한 로그
10. git diff                      # 변경사항 확인
11. git stash                     # 임시 저장
12. git stash pop                 # 임시 저장 복구
13. git reset --soft HEAD~1       # 마지막 커밋 취소
14. git commit --amend            # 마지막 커밋 수정
15. git rebase -i HEAD~3          # 대화형 리베이스
16. git cherry-pick abc1234       # 특정 커밋 가져오기
17. git fetch --prune             # 원격 변경사항 가져오기
18. git clean -fd                 # Untracked 파일 삭제
19. git reflog                    # 참조 로그
20. git tag -a v1.0.0             # 태그 생성
```

---

## 학습 체크리스트

- [ ] Git의 3가지 영역 이해 (Working, Staging, Repository)
- [ ] 기본 워크플로우 숙지 (add, commit, push, pull)
- [ ] 브랜치 생성/병합/삭제 가능
- [ ] 충돌 해결 경험
- [ ] Reset vs Revert 차이 이해
- [ ] Stash 활용 가능
- [ ] Rebase 사용 가능
- [ ] Cherry-pick 활용 가능
- [ ] Git Flow 또는 GitHub Flow 이해
- [ ] .gitignore 작성 가능

---

## 참고 자료

- [Pro Git Book](https://git-scm.com/book/ko/v2) (무료, 한글)
- [Git 공식 문서](https://git-scm.com/docs)
- [GitHub Skills](https://skills.github.com/)
- [Learn Git Branching](https://learngitbranching.js.org/?locale=ko) (인터랙티브)
- [Oh My Git!](https://ohmygit.org/) (게임으로 배우기)

---

**"Git은 배우는 것이 아니라 익숙해지는 것이다."** 🚀
