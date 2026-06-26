# 편한가계부 후속 개발 진행 기록 v.0.0.53

작성일: 2026-06-25

## 1. 기준

- 이전 기록: `plan/plan-v.0.0.52.md`
- 이번 개발 범위: 프론트엔드 도메인별 mutation hook과 공통 API 오류 처리 분리
- 검증 방식: Gradle 전체 테스트, Docker Compose 이미지 빌드, Playwright 브라우저 스모크 테스트

## 2. 이번 버전 개발 완료

### 공통 API 오류 계층

- `frontend/src/api/http.js`에 `ApiError`를 추가했다.
- JSON 요청과 상태 변경 요청이 실패 응답을 받으면 상태 코드와 서버 오류 본문을 포함한 예외를 발생시킨다.
- 응답 본문 없는 저장·삭제 요청을 위한 `requestOk`를 추가했다.
- `errorMessage`가 서버 오류, 도메인별 번역과 기본 안내 문구를 한 규칙으로 선택한다.

### 관리 도메인 mutation hook

- `frontend/src/hooks/useManagementMutations.js`를 추가했다.
- 자산·카테고리·명의·예산 저장·삭제와 성공 후 데이터 재조회를 이동했다.
- 기존 개인 지출 명의 마이그레이션과 전월 예산 복사 흐름도 관리 hook이 담당한다.
- 명의 중복, OWNER 삭제, 자산·개인 지출에서 사용 중인 명의 오류를 사용자 친화적 문구로 변환한다.

### 일정 도메인 mutation hook

- `frontend/src/hooks/useScheduleMutations.js`를 추가했다.
- 반복 거래 저장·삭제·만기 생성과 목록 갱신을 이동했다.
- 카드 결제 예약 저장·실행·재시도·재예약·취소와 상세 재조회를 이동했다.
- 일정 작업 실패 시 공통 오류 처리와 작업별 기본 메시지를 사용한다.

### 거래 도메인 mutation hook

- `frontend/src/hooks/useTransactionMutations.js`를 추가했다.
- 일반·할부 거래 저장·삭제, 영수증 일괄 첨부·삭제와 이체 수수료 거래 생성을 이동했다.
- 할부 영수증 대상 회차 선택과 거래 저장 후 폼 초기화·원장 재조회를 hook에서 조정한다.
- 거래·영수증·할부 실패도 공통 `ApiError` 처리 경로를 사용한다.

### API 모듈 mutation 처리 통일

- `ledgerApi`, `managementApi`, `scheduleApi`의 상태 변경 요청을 `requestOk` 또는 오류 검증이 포함된 `requestJson`으로 통일했다.
- 실패한 HTTP 응답이 성공 흐름을 계속 실행하지 않도록 했다.
- 조회 결과의 성공 여부를 직접 다뤄야 하는 명의 마이그레이션과 전월 예산 복사는 기존 `requestResult` 계약을 유지했다.

### App 책임과 파일 규모 변화

- `main.jsx`에서 관리·일정·거래 mutation 구현을 제거하고 hook 반환 함수를 화면에 전달한다.
- App은 폼 상태, 패널 선택, 조회 데이터와 hook 연결을 주로 담당한다.
- `main.jsx`는 v.0.0.52 기준 957줄에서 749줄로 감소했다.
- 관리 hook 187줄, 일정 hook 137줄, 거래 hook 158줄로 책임을 분리했다.
- 프론트엔드 프로덕션 빌드 모듈 수는 46개에서 49개로 증가했다.

## 3. v.0.0.52 대비 상태 변경

| 항목 | v.0.0.52 상태 | v.0.0.53 상태 | 메모 |
| --- | --- | --- | --- |
| 관리 mutation hook | 미완료 | 완료 | 자산·카테고리·명의·예산 |
| 일정 mutation hook | 미완료 | 완료 | 반복 거래·카드 결제 |
| 거래 mutation hook | 미완료 | 완료 | 거래·할부·영수증 |
| HTTP 실패 예외 처리 | 부분 완료 | 완료 | `ApiError`, `requestOk` |
| 사용자 오류 메시지 통일 | 미완료 | 완료 | fallback·서버 메시지·번역 |
| `main.jsx` mutation 책임 | 집중 | 분리 | 957줄에서 749줄 |
| 브라우저 스모크 테스트 | 완료 | 완료 | 거래·관리·일정 UI 흐름 통과 |
| Docker 이미지 빌드 | 완료 | 완료 | Vite 49개 모듈 변환 |
| Gradle 테스트 | 완료 | 완료 | 전체 테스트 통과 |

## 4. 아직 부분 완료인 항목

### 검색 기능

- 검색 결과는 전체 목록을 반환하며 페이지네이션과 정렬 선택을 지원하지 않는다.
- 백엔드 검색 조건 객체와 프론트엔드 검색 hook을 페이지 정보까지 확장할 필요가 있다.

### mutation UX

- mutation 실행 중 중복 제출을 막는 도메인별 pending 상태가 아직 없다.
- 일시적인 네트워크 오류에 대한 재시도 동작과 화면 내 오류 배너는 아직 미완료다.
- CSV 내보내기와 클립보드 권한 오류는 작업 특성상 App의 개별 안내를 유지한다.

### 테스트와 파일 정책

- 새 백엔드 Command·관리 서비스의 저장소 조합을 검증하는 통합 테스트를 확충할 수 있다.
- 영수증 첨부 파일 정리 정책 고도화는 아직 미완료다.

## 5. 다음 개발 우선순위

### v.0.0.54 권장

1. 검색 결과 페이지네이션과 정렬 API
2. 프론트엔드 검색 페이지 상태·UI 연결
3. mutation pending 상태와 중복 제출 방지
4. 백엔드 서비스별 통합 테스트 확충

### 이후 권장

1. 영수증 첨부 파일 정리 정책 고도화
2. 네트워크 재시도와 화면 내 오류 표시

## 6. 검증

- `docker run --rm -v "${PWD}\backend:/workspace" -w /workspace gradle:8.10.2-jdk21 gradle test --no-daemon` 통과
- `docker compose build frontend` 통과
- `docker compose build backend` 통과
- Docker 프론트엔드 빌드에서 Vite 49개 모듈 변환 통과
- `docker compose up -d frontend backend`로 최신 이미지 재생성 완료
- `powershell -ExecutionPolicy Bypass -File .\scripts\browser-smoke.ps1` 통과
- `git diff --check` 통과

## 7. 개발 메모

- hook은 화면 컴포넌트를 직접 알지 않고 상태 setter와 재조회 callback을 전달받아 기존 UI 구조를 유지한다.
- 실패 응답에서 패널 닫기나 폼 초기화를 실행하지 않아 사용자가 입력 내용을 잃지 않도록 했다.
- 다음 버전에서는 검색 Query 구조가 이미 분리되어 있으므로 페이지네이션·정렬을 백엔드와 프론트엔드에 함께 확장하기 좋다.
