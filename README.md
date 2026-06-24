# 편한가계부 개발 프로젝트

Spring Boot 백엔드와 React 프론트엔드로 구성한 개인용 가계부 앱입니다.

## 구성

- `backend`: Spring Boot 3, JPA, MySQL
- `frontend`: React + Vite
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
- 사용자 지정 기간 거래 조회/필터
- 수입/지출/이체 등록
- 거래 상세 확인, 수정, 삭제 UI
- 자산 잔액 반영
- 자산 생성/수정/삭제
- 자산 명의 표시 및 저장
- 카드 자산 생성/수정 시 결제 계좌/확정일/결제일/자동 결제 설정
- 카테고리 기본 데이터 및 카테고리 관리
- 월별 수입/지출/예산 요약
- 카테고리별 예산 사용률과 초과 표시
- 전월 예산 복사
- 월별/연별 수입·지출 통계와 카테고리별·소비 태그별 지출 통계
- 통계 카테고리/소비 태그 선택 후 거래 목록 이동
- 카드 결제 예약 관리, 청구 기간 기반 예정 금액, 주말/공휴일 결제일 보정, 중복 실행 방지, 실패 사유 표시, 실패 결제 재시도/재예약
- 할부 거래 생성, 일정 조회, 그룹 단위 수정/삭제, 회차 수 변경
- 반복 거래 생성/수정/삭제 및 만기 생성
- 영수증 사진 신규/기존 거래 다중 첨부, 조회, 미리보기, 삭제
- 카드/은행 문자 텍스트 기반 자동 입력, 과거 이력·가맹점 키워드 카테고리 추천 및 거래 입력 확정
- 지출 거래 소비 태그 입력, 상세 표시, 검색, CSV 내보내기
- 월별 거래 및 화면 필터 결과 CSV 내보내기
- 가족/공동 가계부를 위한 Household/Member 도메인 기반

## 다음 작업

- 기간 필터 기준 요약 금액 재계산
- 거래 추가 시 직접 입력/문자 자동 입력 선택 메뉴
- 명의별 자산 집계
- 할부 그룹 수정 중 영수증 첨부
- 더보기의 명의 관리 메뉴

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
