# 편한가계부 개발 프로젝트

Spring Boot 백엔드와 React 프론트엔드로 구성한 개인용 가계부 앱입니다.

## 구성

- `backend`: Spring Boot 3, JPA, MySQL
- `frontend`: React + Vite
- `docker-compose.yml`: MySQL, 백엔드, 프론트엔드, DB 백업 컨테이너 통합 실행
- `plan`: 화면 기획서, PPT/PDF 산출물

## Docker 실행

```powershell
docker compose up --build
```

실행 후 접속:

- 프론트엔드: http://localhost:8081
- 백엔드 API: http://localhost:8080/api/bootstrap

영구 저장 경로:

- MySQL 실시간 데이터 파일/binlog: `data/db/mysql`
- MySQL SQL 백업: `data/db/backups/latest.sql`
- 업로드 사진: `data/app/uploads`
- 애플리케이션 로그: `data/app/logs`

서버 이전 시에는 앱을 중지한 뒤 `data` 폴더와 `.env`를 함께 옮기면 됩니다.

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
- 수입/지출/이체 등록
- 자산 잔액 반영
- 카테고리 기본 데이터
- 월별 수입/지출/예산 요약
- 카테고리별 지출 통계
- 영수증 사진 첨부 API
- 문자 텍스트 기반 자동 입력 후보 추출
- 가족/공동 가계부를 위한 Household/Member 도메인 기반

## 다음 작업

- 거래 수정/삭제
- 자산/카테고리 관리 화면
- 카드 결제 예정 금액 청구 기간 계산 고도화
- 카테고리별 예산 입력 화면
- 공동 가계부 초대/권한 UI
