# 편한가계부 CI/CD 개발 기록 v.0.2.1

작성일: 2026-06-26

## 1. 개발 목표

- `plan-v.0.2.0`의 OCI 1단계 배포 전략을 실제 GitHub Actions workflow로 구현한다.
- 초기 목표는 서버에서 직접 Docker Compose build/up을 수행하는 단순 SSH 배포로 한다.
- 서버의 `.env`, `data/` 영구 데이터를 배포 과정에서 삭제하지 않는다.

## 2. 구현 내용

- `.github/workflows/deploy-oci.yml` 추가
- 실행 조건:
  - `main` 브랜치 push
  - 수동 실행 `workflow_dispatch`
- CI 단계:
  - `git diff --check`
  - 백엔드 Gradle 테스트
  - Node 22 설정
  - 프론트엔드 `npm install && npm run build`
  - `docker compose build backend`
  - `docker compose build frontend`
- CD 단계:
  - GitHub Secrets 기반 SSH 접속
  - OCI 앱 디렉터리에서 `git fetch origin main`
  - `git reset --hard origin/main`
  - `docker compose up -d --build`
  - `docker compose ps`
  - 백엔드/프론트엔드 로컬 health check

## 3. 필요한 GitHub Secrets

| Secret | 설명 |
| --- | --- |
| `OCI_HOST` | OCI 서버 IP 또는 도메인 |
| `OCI_USER` | SSH 사용자. Ubuntu 이미지 기준 `ubuntu` |
| `OCI_SSH_PRIVATE_KEY` | SSH private key 전체 내용 |
| `OCI_APP_DIR` | 서버 앱 경로. 예: `/opt/comfortable-ledger/app` |

## 4. 서버 전제 조건

- Ubuntu 서버에 `git`, `curl`, `docker.io`, `docker-compose-plugin` 설치
- 배포 사용자가 Docker 실행 권한 보유
- `OCI_APP_DIR`에 repository clone 완료
- 서버 로컬 `.env` 작성 완료
- `data/` 디렉터리는 서버 로컬 영구 데이터로 유지

## 5. 배포 검증

workflow 배포 후 서버 내부에서 다음 URL을 확인한다.

```bash
curl -fsS http://localhost:8080/api/bootstrap >/dev/null
curl -fsS http://localhost:8081 >/dev/null
```

## 6. 로컬 검증 결과

```powershell
git diff --check
docker run --rm -v "${PWD}\backend:/workspace" -w /workspace gradle:8.10.2-jdk21 gradle test --no-daemon
docker compose build frontend
docker compose build backend
docker run --rm -v "${PWD}:/repo" -w /repo rhysd/actionlint:latest
```

결과:

- 백엔드 테스트 성공
- 프론트엔드 Docker 이미지 빌드 성공
- 백엔드 Docker 이미지 빌드 성공
- GitHub Actions workflow 정적 검사 성공
- `git diff --check` 오류 없음. 단, Windows CRLF 경고만 출력됨

## 7. 남은 작업

1. OCI 서버에서 최초 패키지 설치 및 앱 clone
2. 운영 `.env` 작성
3. GitHub Repository Secrets 등록
4. GitHub Actions `workflow_dispatch`로 수동 배포 검증
5. 필요 시 2단계로 GHCR/OCIR 이미지 registry 기반 배포 전환
