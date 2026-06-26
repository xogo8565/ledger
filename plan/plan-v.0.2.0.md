# 편한가계부 CI/CD 신설 계획 v.0.2.0

작성일: 2026-06-26

## 1. 기준

- 이전 개발 라인: `plan/plan-v.0.1.6.md`
- 신규 개발 주제: Oracle Cloud Infrastructure 인스턴스 대상 CI/CD 파이프라인 신설
- 참조 접속 정보:
  - SSH 키 경로: `C:\ssh\oci.key`
  - SSH 사용자: `ubuntu`
  - 대상 서버: `217.142.229.9`
  - 접속 예시: `ssh -i "C:\ssh\oci.key" ubuntu@217.142.229.9`
- 전제:
  - 대상 OCI 인스턴스 OS는 Ubuntu로 본다.
  - 현재 앱은 `docker-compose.yml` 하나로 MySQL, 백엔드, 프론트엔드, DB 백업 컨테이너를 실행한다.
  - 현재 저장소에는 `.github/workflows`가 없다.

## 2. 목표

OCI 인스턴스에 다음 흐름의 CI/CD를 구성한다.

1. GitHub main 브랜치 push 또는 수동 실행
2. 백엔드 테스트 및 프론트엔드 빌드 검증
3. OCI 서버 SSH 접속
4. 서버에서 최신 코드 반영
5. Docker Compose 기반 재빌드/재기동
6. 배포 후 smoke test 실행
7. 실패 시 로그 확인 및 이전 컨테이너/데이터 보존

초기 목표는 단순하고 복구 가능한 배포다. 이미지 레지스트리 기반 blue/green 배포는 2단계로 둔다.

## 3. 배포 전략

### 1단계: SSH + git pull + docker compose build/up

초기 구현은 다음 방식으로 한다.

- GitHub Actions가 OCI 인스턴스에 SSH 접속한다.
- 서버의 애플리케이션 디렉터리에서 `git fetch`, `git reset --hard origin/main`을 수행한다.
- `.env`는 서버 로컬 파일로 유지하고 GitHub에 저장하지 않는다.
- `docker compose up -d --build`로 백엔드/프론트엔드 이미지를 서버에서 빌드한다.
- MySQL 데이터는 기존 `./data/db/mysql` 볼륨을 유지한다.

장점:

- 현재 `docker-compose.yml` 구조를 거의 그대로 쓴다.
- 별도 컨테이너 레지스트리 설정 없이 시작할 수 있다.
- 서버의 `data` 폴더와 `.env`를 그대로 보존한다.

단점:

- 서버에서 매번 빌드하므로 배포 시간이 길다.
- 빌드 실패와 배포 실패가 서버 리소스 상태에 영향을 받는다.

### 2단계: GitHub Actions build + GHCR/OCIR push + 서버 pull

1단계 안정화 후 전환한다.

- Actions에서 backend/frontend 이미지를 빌드한다.
- GHCR 또는 OCIR에 이미지를 push한다.
- 서버는 이미지를 pull한 뒤 `docker compose up -d`만 수행한다.
- compose 파일은 운영용 override 또는 이미지 태그 기반 파일로 분리한다.

장점:

- 서버 부하가 줄어든다.
- 빌드 산출물과 배포 산출물을 명확히 분리할 수 있다.
- 롤백 태그 관리가 가능하다.

## 4. OCI 서버 준비 계획

### SSH 키 권한

Windows 로컬에서 private key 권한은 아래 기준으로 정리한다.

```bat
icacls "C:\ssh\oci.key" /inheritance:r
icacls "C:\ssh\oci.key" /remove:g Everyone
icacls "C:\ssh\oci.key" /remove:g Users
icacls "C:\ssh\oci.key" /remove:g "Authenticated Users"
icacls "C:\ssh\oci.key" /grant:r %USERNAME%:R
icacls "C:\ssh\oci.key"
```

확인 기준:

- 현재 사용자만 `R` 권한을 가진다.
- `Everyone`, `Users`, `Authenticated Users` 권한은 없어야 한다.

접속 확인:

```powershell
ssh -i "C:\ssh\oci.key" ubuntu@217.142.229.9
```

### 서버 패키지

OCI Ubuntu 서버에 설치할 항목:

```bash
sudo apt-get update
sudo apt-get install -y git ca-certificates curl
```

Docker 설치:

```bash
sudo apt-get install -y docker.io docker-compose-plugin
sudo usermod -aG docker ubuntu
```

적용 후 재접속:

```bash
exit
ssh -i "C:\ssh\oci.key" ubuntu@217.142.229.9
docker version
docker compose version
```

### 서버 디렉터리

권장 경로:

```bash
sudo mkdir -p /opt/comfortable-ledger
sudo chown -R ubuntu:ubuntu /opt/comfortable-ledger
```

저장소 배치:

```bash
cd /opt/comfortable-ledger
git clone <REPOSITORY_URL> app
cd app
cp .env.example .env
```

운영 `.env`는 서버에서만 관리한다.

필수 조정:

- `MYSQL_ROOT_PASSWORD`
- `MYSQL_USER`
- `MYSQL_PASSWORD`
- `MYSQL_BACKUP_INTERVAL_SECONDS`

### OCI 네트워크

OCI VCN 보안 목록 또는 NSG에서 인바운드 허용:

| 목적 | 포트 | 권장 범위 |
| --- | --- | --- |
| SSH | 22 | 관리자 IP만 |
| 프론트엔드 HTTP | 8081 또는 80 | 필요 범위 |
| 백엔드 API | 8080 | 원칙적으로 외부 미공개 권장 |
| HTTPS | 443 | 리버스 프록시 도입 시 |

초기에는 `8081`만 공개하고, `8080`은 운영상 필요할 때만 제한적으로 공개한다.

서버 UFW 사용 시:

```bash
sudo ufw allow OpenSSH
sudo ufw allow 8081/tcp
sudo ufw enable
```

## 5. GitHub Actions 설계

### Repository Secrets

GitHub 저장소에 다음 secrets를 등록한다.

| Secret | 값 |
| --- | --- |
| `OCI_HOST` | `217.142.229.9` |
| `OCI_USER` | `ubuntu` |
| `OCI_SSH_PRIVATE_KEY` | `C:\ssh\oci.key` 파일 내용 전체 |
| `OCI_APP_DIR` | `/opt/comfortable-ledger/app` |

주의:

- private key 파일 내용은 저장소에 커밋하지 않는다.
- `OCI_SSH_PRIVATE_KEY`에는 `-----BEGIN ... PRIVATE KEY-----`부터 `-----END ... PRIVATE KEY-----`까지 전체를 넣는다.

### CI 단계

백엔드:

```bash
docker run --rm -v "${PWD}/backend:/workspace" -w /workspace gradle:8.10.2-jdk21 gradle test --no-daemon
```

프론트엔드:

```bash
docker compose build frontend
```

운영 배포 전 최소 검증:

- `git diff --check`
- 백엔드 테스트
- 백엔드 이미지 빌드
- 프론트엔드 이미지 빌드

### CD 단계

OCI 서버에서 실행할 배포 명령:

```bash
set -euo pipefail
cd "$OCI_APP_DIR"
git fetch origin main
git reset --hard origin/main
docker compose pull || true
docker compose up -d --build
docker compose ps
```

배포 후 확인:

```bash
curl -fsS http://localhost:8080/api/bootstrap >/dev/null
curl -fsS http://localhost:8081 >/dev/null
```

## 6. GitHub Actions workflow 초안

파일 경로:

```text
.github/workflows/deploy-oci.yml
```

초안:

```yaml
name: Deploy to OCI

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  test-and-build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Check diff whitespace
        run: git diff --check

      - name: Backend tests
        run: docker run --rm -v "${PWD}/backend:/workspace" -w /workspace gradle:8.10.2-jdk21 gradle test --no-daemon

      - name: Backend image build
        run: docker compose build backend

      - name: Frontend image build
        run: docker compose build frontend

  deploy:
    needs: test-and-build
    runs-on: ubuntu-latest
    environment: production
    steps:
      - name: Prepare SSH key
        run: |
          mkdir -p ~/.ssh
          printf '%s\n' "${{ secrets.OCI_SSH_PRIVATE_KEY }}" > ~/.ssh/oci.key
          chmod 600 ~/.ssh/oci.key
          ssh-keyscan -H "${{ secrets.OCI_HOST }}" >> ~/.ssh/known_hosts

      - name: Deploy on OCI
        run: |
          ssh -i ~/.ssh/oci.key "${{ secrets.OCI_USER }}@${{ secrets.OCI_HOST }}" << 'EOF'
            set -euo pipefail
            cd "${{ secrets.OCI_APP_DIR }}"
            git fetch origin main
            git reset --hard origin/main
            docker compose up -d --build
            docker compose ps
            curl -fsS http://localhost:8080/api/bootstrap >/dev/null
            curl -fsS http://localhost:8081 >/dev/null
          EOF
```

## 7. 운영 데이터 보존 정책

배포 명령에서 금지할 작업:

- `docker compose down -v`
- `rm -rf data`
- `.env` 덮어쓰기
- MySQL 볼륨 삭제

배포 전 백업 확인:

```bash
ls -lah /opt/comfortable-ledger/app/data/db/backups
```

수동 백업:

```bash
docker exec comfortable-ledger-mysql-backup sh -c 'ls -lah /backups && cp /backups/latest.sql /backups/pre-deploy-$(date +%Y%m%d-%H%M%S).sql'
```

## 8. 롤백 계획

초기 1단계 롤백:

```bash
cd /opt/comfortable-ledger/app
git log --oneline -n 10
git reset --hard <PREVIOUS_COMMIT>
docker compose up -d --build
```

주의:

- DB 스키마 변경이 포함된 경우 애플리케이션 롤백만으로 부족할 수 있다.
- DB migration 정책이 명확해지기 전에는 스키마 변경 배포 전 SQL 백업을 필수로 한다.

## 9. 다음 실행 작업

### v.0.2.1 권장

1. OCI 서버 SSH 접속 검증
2. Docker/Compose 설치 및 `ubuntu` 사용자 docker 권한 설정
3. `/opt/comfortable-ledger/app` 저장소 clone
4. 운영 `.env` 작성
5. 서버에서 수동 `docker compose up -d --build` 검증
6. GitHub Secrets 등록
7. `.github/workflows/deploy-oci.yml` 추가
8. `workflow_dispatch`로 수동 배포 검증

### v.0.2.2 권장

1. 8081 직접 공개 대신 Nginx/Caddy 리버스 프록시 도입
2. HTTPS 인증서 적용
3. GHCR 또는 OCIR 이미지 레지스트리 기반 배포 전환
4. 배포 성공/실패 알림 추가

## 10. 리스크와 결정 필요 사항

| 항목 | 리스크 | 결정 필요 |
| --- | --- | --- |
| 저장소 접근 | OCI 서버가 private repo를 clone하려면 인증 필요 | deploy key 또는 PAT 선택 |
| DB 스키마 | JPA 자동 변경 정책에 따라 롤백 어려움 | 운영 DB migration 정책 확정 |
| 포트 공개 | 8080 API 외부 공개 시 공격면 증가 | 8080 비공개, 프론트만 공개 권장 |
| 서버 빌드 | OCI free tier 사양이면 빌드가 느릴 수 있음 | 2단계에서 registry 배포로 전환 |
| SSH key | private key 유출 시 서버 접근 위험 | GitHub secret, 로컬 ACL, 키 주기적 교체 |

## 11. 결론

초기 CI/CD는 `GitHub Actions + SSH + docker compose up --build`로 시작한다. 현재 프로젝트 구조와 가장 잘 맞고, 별도 레지스트리 없이 빠르게 운영 배포를 자동화할 수 있다. 이후 배포 시간이 문제되거나 롤백 요구가 커지면 GHCR/OCIR 이미지 태그 기반 배포로 전환한다.
