# 가계부 개발 프로젝트

Spring Boot 백엔드와 React 프론트엔드로 구성한 개인용 가계부 앱입니다.

## 구성

- `backend`: Spring Boot 3, JPA, MySQL
- `frontend`: React + Vite
- `frontend/src/components`: 공통 UI 컴포넌트
- `frontend/src/screens`: 화면 단위 컴포넌트
- `frontend/src/utils`: 날짜·금액 포맷과 CSV 유틸
- `docker-compose.yml`: MySQL, 백엔드, 프론트엔드, DB 백업 컨테이너 통합 실행
- `plan`: 기획 문서, 진행 기록, 참고 화면 이미지

## Docker 실행

```powershell
docker compose up --build
```

실행 후 접속:

- 프론트엔드: http://localhost:8081
- 백엔드 API: http://localhost:8080/api/bootstrap

영구 저장 경로:

- MySQL 데이터 파일/binlog: `data/db/mysql`
- MySQL SQL 백업: `data/db/backups/latest.sql`
- 업로드 사진: `data/app/uploads`
- 애플리케이션 로그: `data/app/logs`

서버 이전 시에는 실행을 중지한 뒤 `data` 폴더와 `.env`를 함께 옮기면 됩니다.

비밀번호 등 운영 설정은 `.env.example`을 복사해 `.env`로 만든 뒤 수정합니다.

영수증 OCR은 Docker 백엔드 이미지에 포함된 Tesseract를 사용합니다. 기본 언어는 `kor+eng`이며 `TESSERACT_COMMAND`, `TESSERACT_LANGUAGE` 환경 변수로 조정할 수 있습니다.

## CI/CD

GitHub Actions workflow는 `.github/workflows/deploy-oci.yml`에 있습니다.

동작:

- `main` 브랜치 push 또는 수동 실행(`workflow_dispatch`) 시 실행
- 백엔드 테스트
- 프론트엔드 빌드
- 백엔드/프론트엔드 Docker 이미지 빌드
- OCI 서버 SSH 접속 후 `git reset --hard origin/main`
- `docker compose up -d --build`
- 백엔드 `http://localhost:8080/api/bootstrap`, 프론트엔드 `http://localhost:8081` 확인

GitHub Repository Secrets:

| Secret | 값 |
| --- | --- |
| `OCI_HOST` | OCI 서버 IP 또는 도메인 |
| `OCI_USER` | SSH 사용자. Ubuntu 이미지 기준 `ubuntu` |
| `OCI_SSH_PRIVATE_KEY` | SSH private key 전체 내용 |
| `OCI_APP_DIR` | 서버 앱 경로. 예: `/opt/comfortable-ledger/app` |

OCI 서버 최초 준비 예시:

```bash
sudo apt-get update
sudo apt-get install -y git ca-certificates curl docker.io docker-compose-plugin
sudo usermod -aG docker ubuntu

sudo mkdir -p /opt/comfortable-ledger
sudo chown -R ubuntu:ubuntu /opt/comfortable-ledger
cd /opt/comfortable-ledger
git clone <REPOSITORY_URL> app
cd app
cp .env.example .env
```

서버의 `.env`와 `data/`는 배포 workflow가 삭제하지 않습니다.

## 로컬 개발 실행

백엔드:

```powershell
cd backend
gradle bootRun
```

프론트엔드:

```powershell
cd frontend
npm install
npm run dev
```

## 구현된 MVP

- 월별 거래 목록 조회
- 월 내 거래 검색/필터
- 사용자 지정 기간 거래 조회/필터 및 필터 결과 수입·지출·합계 재계산
- 서버 기반 거래 고급 검색: 기간·유형·카테고리·명의·소비 구분·자산·금액 범위·키워드 조합
- 검색 결과 페이지네이션 및 최신순/오래된순/금액순 정렬
- 거래 추가 시 직접 입력/문자 자동 입력 선택 및 수입/지출/이체 등록
- 거래 상세 확인, 수정, 삭제 UI
- 자산 잔액 반영
- 자산 생성/수정/삭제
- 등록 명의 목록 기반 자산 명의 선택·저장 및 명의별 자산·부채·순자산 집계
- 더보기 명의 관리: 목록·추가·수정·삭제와 기본 명의 보호
- 더보기 화면 앱 버전 표시
- 기존 명의 미지정 개인 지출 대상 확인 및 기본 OWNER 명의 일괄 연결
- 카드 자산 생성/수정 시 결제 계좌/확정일/결제일/자동 결제 설정
- 카드 자산 등록/수정 시 결제계좌 및 결제일 검증
- 부채 자산 금리/출금계좌/차감일/이자 자동 차감 설정 및 매월 이자 지출 자동 생성
- 카테고리 기본 데이터 및 카테고리 관리
- 월별 수입/지출/예산 요약
- 카테고리별 예산 사용률과 초과 표시
- 전월 예산 복사
- 월별 주간 지출 통계와 연간 예산·지출 사용 현황
- 월별/연별 수입·지출 통계와 카테고리별·소비 태그별·공동/개인 지출 통계
- 통계 카테고리/소비 태그/공동·개인 항목 선택 후 거래 목록 이동
- 지출 거래 공동/개인 소비 구분과 개인 소비 명의 지정, 목록·상세·검색·CSV 반영
- 카드 결제 예약 관리, 청구 기간 기반 예정 금액, 주말/공휴일 결제일 보정, 중복 실행 방지, 실패 사유 표시, 실패 결제 재시도/재예약
- 할부 거래 생성, 일정 조회, 그룹 단위 수정/삭제, 회차 수 변경
- 반복 거래 생성/수정/삭제 및 만기 생성
- 영수증 사진 신규/기존 거래 다중 첨부, 조회, 미리보기, 삭제 및 거래 삭제 시 파일 정리
- 영수증 업로드 Tesseract OCR 분석 및 거래 입력 초안 자동 채움
- OCR 원문 편집 후 재분석 및 수정된 거래 후보 반영
- OCR 품목표 여러 행 메모 요약
- OCR 실패/낮은 신뢰도 경고 및 직접 입력 전환
- OCR 임시 파일 서버 시작 시 정리
- OCR 결과 제목/금액 후보 선택
- OCR 결과 날짜 후보 선택
- 백엔드 OCR 후보 DTO 기반 날짜/제목/금액 후보 제공
- OCR 정책 메타데이터 제공: 신뢰도 점수, 검토 필요 여부, 인식 필드, 검토 사유
- OCR 금액 후보 정책 고도화: 합계/결제금액 우선, 사업자번호·전화번호·카드번호 제외, 품목표 후보 보강
- 클립보드 카드/은행 문자 자동 분석, 과거 이력·가맹점 키워드 카테고리 추천 및 거래 입력 확정
- iPhone Safari/Chrome 클립보드 권한 제한 대응을 위한 수동 붙여넣기 문자 자동 입력
- 날짜 헤더와 `|` 구분 거래 목록 텍스트 다건 파싱 후보 제공
- 다건 문자/거래 목록 자동 입력 시 1건 저장 후 다음 거래를 순차 표시
- 지출 거래 소비 태그 입력, 상세 표시, 검색, CSV 내보내기
- 월별 거래 및 화면 필터/현재 검색 페이지 결과 CSV 내보내기
- 가족/공동 가계부를 위한 Household/Member 도메인 기반
- DB 첫 실행 시 기본 명의 `석수`, 보조 명의 `유진` 자동 생성
- `initial-data/assets_*.xlsx`, `initial-data/transactions_*.xlsx` 엑셀 목록 기반 초기 자산·거래 데이터 주입
- `initial-data` 파일 SHA-256 변경 감지 기반 재주입: 배포/업로드 후 변경된 파일만 자산 upsert, 거래 중복 skip 처리
- `plan/*.png` 참고 자산을 `initial-data/assets_plan_20260629.xlsx`로 반영해 기본 자산 주입
- `assets_plan_20260629.xlsx` 카드 자산은 금액 0원, 엑셀 확정일/결제일 컬럼 기준으로 주입
- 문자열/숫자/암호성 값 공통 유틸 분리
- 프론트엔드 문자열/숫자/암호성 값 공통 유틸 분리
- 백엔드 MVC 기준 패키지 세분화: `controller`, `dto`, `config`, `repository`, `service`, `domain`, `util`
- 프론트엔드 배포 캐시 정책: HTML no-store, 해시 assets immutable로 새 버전 자동 반영
- 상세/등록/관리 화면 고정 레이어 처리로 기존 UI와 겹침 방지

## 다음 작업

- OCI 서버 최초 패키지 설치, 앱 clone, GitHub Secrets 등록 후 `workflow_dispatch` 배포 검증

## Smoke Test

Docker Compose로 스택을 실행한 뒤:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1
```

확인 항목:

- 프론트엔드 HTTP 200
- 백엔드 bootstrap API
- 카드 결제 예약 생성/취소
- 반복 거래 규칙 생성/삭제
- 할부 거래 생성/일정 조회/삭제
- 주요 API 음수/무효 입력 검증

## Browser Smoke Test

Docker Compose로 스택을 실행한 뒤:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\browser-smoke.ps1
```

Playwright를 Docker에서 실행해 가계부, 자산, 카드 결제, 반복 거래, 거래 입력 화면이 브라우저 오류 없이 열리는지 확인합니다. 또한 빈 폼 방지, UI 생성/수정 흐름, 자산/카테고리/예산 관리 흐름, 반응형 스크린샷, 테스트 데이터 자동 정리를 검증합니다.
