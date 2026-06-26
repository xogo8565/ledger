# 편한가계부 후속 개발 진행 기록 v.0.0.48

작성일: 2026-06-25

## 1. 기준

- 이전 기록: `plan/plan-v.0.0.47.md`
- 이번 개발 범위: 프론트엔드 API 요청 모듈과 대시보드·검색·통계 데이터 custom hook 분리
- 검증 방식: Gradle 전체 테스트, Docker Compose 이미지 빌드, Playwright 브라우저 스모크 테스트

## 2. 이번 버전 개발 완료

### 공통 HTTP 계층 분리

- `frontend/src/api/http.js`를 추가했다.
- `/api` 기준 경로, JSON 요청 옵션, JSON 응답 파싱, 성공 여부와 오류 응답을 함께 반환하는 공통 처리를 구성했다.
- 프론트엔드의 직접 `fetch` 호출을 공통 HTTP 계층 한 곳으로 제한했다.

### 도메인별 API 모듈 분리

- `frontend/src/api/ledgerApi.js`에 bootstrap, 검색, 통계, 거래, 영수증, CSV, 문자 분석 요청을 모았다.
- `frontend/src/api/managementApi.js`에 자산, 카테고리, 명의, 예산 요청을 모았다.
- `frontend/src/api/scheduleApi.js`에 할부, 반복 거래, 카드 결제 일정 요청을 모았다.
- URL과 HTTP 메서드 선택, 요청 본문 직렬화, 응답 파싱 책임을 App에서 API 모듈로 이동했다.

### 데이터 로딩 custom hook 분리

- `frontend/src/hooks/useLedgerData.js`를 추가했다.
- 월별 bootstrap, 자산 요약, 명의 목록, 연간 통계·예산, 기간 통계, 필터 검색 상태와 effect를 hook으로 이동했다.
- 월·검색 조건·통계 기간 변경에 따른 재조회와 검색 debounce를 hook이 담당한다.
- App에는 조회 결과와 `reload`, `reloadMembers` 인터페이스만 노출해 mutation 이후 갱신 흐름을 유지했다.

### App 책임 정리

- `main.jsx`에서 직접 `fetch`, URL 조합, JSON 응답 파싱을 제거했다.
- App은 입력값 검증, 사용자 확인·알림, 화면 상태 전환과 API 함수 조합에 집중한다.
- `main.jsx`는 v.0.0.47의 1,096줄에서 957줄로 감소했다.
- 프론트엔드 프로덕션 빌드 모듈 수는 41개에서 46개로 증가했다.

## 3. v.0.0.47 대비 상태 변경

| 항목 | v.0.0.47 상태 | v.0.0.48 상태 | 메모 |
| --- | --- | --- | --- |
| 공통 HTTP 처리 분리 | 미완료 | 완료 | 기준 경로·JSON·결과 처리 |
| 도메인별 API 요청 분리 | 미완료 | 완료 | 원장·관리·일정 API |
| 데이터 조회 custom hook | 미완료 | 완료 | bootstrap·검색·통계 상태/effect |
| 직접 `fetch` 호출 정리 | 미완료 | 완료 | `api/http.js` 한 곳으로 제한 |
| mutation 상태 hook | 미완료 | 미완료 | App의 저장·삭제 orchestration 후속 분리 |
| 브라우저 스모크 테스트 | 완료 | 완료 | 주요 화면·UI 변경 흐름 통과 |
| Docker 이미지 빌드 | 완료 | 완료 | Vite 46개 모듈 변환 |
| Gradle 테스트 | 완료 | 완료 | 전체 테스트 통과 |

## 4. 아직 부분 완료인 항목

### 프론트엔드 상태 구조

- 거래, 자산, 명의, 예산과 일정 저장·삭제 함수는 여전히 App에서 폼 상태와 화면 전환을 함께 조정한다.
- 도메인별 mutation hook으로 성공 후 갱신, 오류 표시와 패널 종료 정책을 나눌 수 있다.
- 공통 HTTP 계층의 오류 객체와 사용자 메시지 정책을 통일할 필요가 있다.

### 백엔드 구조

- `LedgerService`가 거래, 검색, 예산, 통계, 명의와 자산 관리 책임을 함께 담당한다.
- 도메인별 서비스 분리와 검색 조건 객체 분리가 필요하다.

### 기타 후속 항목

- 검색 결과 페이지네이션과 정렬 선택은 아직 미완료다.
- 영수증 첨부 파일 정리 정책 고도화는 아직 미완료다.

## 5. 다음 개발 우선순위

### v.0.0.49 권장

1. 백엔드 프로젝트 구조와 `LedgerService` 책임 분리
2. 프론트엔드 도메인별 mutation hook 분리
3. API 오류 응답·사용자 메시지 처리 통일
4. 검색 결과 페이지네이션·정렬 설계

### 이후 권장

1. 영수증 첨부 파일 정리 정책 고도화
2. API 재시도와 로딩 UX 통일

## 6. 검증

- `docker run --rm -v "${PWD}\backend:/workspace" -w /workspace gradle:8.10.2-jdk21 gradle test --no-daemon` 통과
- `docker compose build frontend` 통과
- `docker compose build backend` 통과
- Docker 프론트엔드 빌드에서 Vite 46개 모듈 변환 통과
- `docker compose up -d frontend backend`로 최신 이미지 재생성 완료
- `powershell -ExecutionPolicy Bypass -File .\scripts\browser-smoke.ps1` 통과
- `git diff --check` 통과

## 7. 개발 메모

- API 모듈은 화면 상태를 알지 못하고 요청과 응답 형태만 담당하도록 구성했다.
- 조회 hook은 데이터 조회와 effect만 소유하며, 확인창이나 패널 전환 같은 UI 정책은 App에 남겼다.
- 기존 API 실패 처리 방식은 유지하면서 `requestResult`를 통해 후속 오류 정책 통일을 위한 공통 반환 형태를 마련했다.
