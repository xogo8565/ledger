# 편한가계부 OCI 배포 준비 점검 자동화 개발 기록 v.0.3.21

작성일: 2026-07-01

## 1. 기준

- 이전 기록: `plan/plan-v.0.3.20.md`
- 이번 개발 범위: P1 `OCI 배포 최초 검증 완료` 중 배포 전 준비 상태 점검 자동화
- 실제 운영 서버 변경과 `workflow_dispatch` 실행은 수행하지 않았다.

## 2. 구현 내용

### OCI 배포 준비 점검 스크립트 추가

- 신규 스크립트:
  - `scripts/oci-deploy-readiness.ps1`
- 로컬 정적 점검 항목:
  - `.github/workflows/deploy-oci.yml` 존재
  - `docker-compose.yml` 존재
  - `.env.example` 존재
  - `data` 디렉터리 존재
  - backend/frontend GHCR 이미지 설정
  - workflow 수동 실행, secret 검증, `docker compose pull`, health check 포함 여부
- SSH 인자를 제공하면 서버 전제 조건도 읽기 전용으로 점검한다.
  - SSH 접속
  - git 설치
  - Docker 설치
  - Docker Compose 설치
  - 앱 디렉터리 존재
  - `.env` 존재
  - `data` 디렉터리 존재
  - 앱 디렉터리 git repository 여부

### README CI/CD 설명 갱신

- 실제 workflow에 맞춰 설명을 수정했다.
- 기존 설명의 `docker compose up -d --build`를 GHCR 기반 `docker compose pull`, `docker compose up -d` 흐름으로 정리했다.
- 배포 전 readiness 스크립트 실행 예시를 추가했다.

## 3. 변경 파일

- `scripts/oci-deploy-readiness.ps1`
- `README.md`
- `plan/new-development-items.md`

## 4. 검증

```text
powershell -ExecutionPolicy Bypass -File .\scripts\oci-deploy-readiness.ps1 -SkipSsh
docker run --rm -v "${PWD}:/repo" -w /repo rhysd/actionlint:latest
docker compose config --quiet
git diff --check
```

결과:

- OCI readiness 로컬 정적 점검 통과
- GitHub Actions workflow 정적 검사 통과
- Docker Compose config 검증 통과
- `git diff --check` 오류 없음. 단, Windows CRLF 변환 경고만 출력됨

## 5. 남은 과제

1. GitHub Repository Secrets를 실제 저장소에 등록한다.
2. `workflow_dispatch`로 수동 배포를 실행한다.
3. workflow의 backend/frontend health check 결과를 확인한다.
4. 필요하면 SSH 서버 점검 모드로 `scripts/oci-deploy-readiness.ps1`을 실행해 서버 전제 조건을 검증한다.

