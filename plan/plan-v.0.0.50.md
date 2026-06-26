# 편한가계부 후속 개발 진행 기록 v.0.0.50

작성일: 2026-06-25

## 1. 기준

- 이전 기록: `plan/plan-v.0.0.49.md`
- 이번 개발 범위: 백엔드 거래 조회·검색·CSV 서비스와 검색 조건 객체 분리
- 검증 방식: Gradle 전체 테스트, Docker Compose 이미지 빌드, Playwright 브라우저 스모크 테스트

## 2. 이번 버전 개발 완료

### 거래 조회 서비스 분리

- `backend/src/main/java/com/comfortableledger/ledger/service/TransactionQueryService.java`를 추가했다.
- 월별·기간별·일별 거래 조회, 단건 조회와 할부 그룹 조회를 `LedgerService`에서 이동했다.
- 거래 CSV 내보내기와 CSV 셀 escaping도 조회 서비스가 담당하도록 정리했다.
- 조회 서비스는 `HouseholdRepository`, `TransactionRepository`만 의존하도록 구성했다.

### 검색 조건 객체 분리

- `backend/src/main/java/com/comfortableledger/ledger/service/TransactionSearchCriteria.java`를 추가했다.
- 시작일·종료일, 거래 유형, 카테고리, 소비 구분·명의, 자산, 최소·최대 금액과 검색어를 하나의 record로 묶었다.
- 날짜 역전, 음수 금액, 최소·최대 금액 역전 검증을 record 생성 시점에 수행한다.
- 검색어 trim 처리를 조건 객체가 담당해 검색 서비스의 입력 전처리를 단순화했다.

### JPA 검색 책임 이동

- 거래 검색용 JPA `Specification`과 조건별 Predicate 생성을 `TransactionQueryService`로 이동했다.
- 자산·출금 자산·입금 자산 통합 필터와 제목·메모·태그·카테고리·자산·명의 키워드 검색을 유지했다.
- 거래일과 ID 내림차순 정렬을 기존과 동일하게 적용했다.
- 자산 조건과 키워드 조건을 별도 helper로 나눠 Specification 본문의 복잡도를 낮췄다.

### 컨트롤러와 테스트 정리

- `LedgerController`가 조회 엔드포인트와 bootstrap 거래 목록을 `TransactionQueryService`에 위임한다.
- 외부 `/api/transactions/**`, `/api/export/transactions.csv` 경로와 응답 형식은 변경하지 않았다.
- 기존 `LedgerServiceTransactionSearchTest`를 `TransactionSearchCriteriaTest`로 이전했다.
- 검색 유효성 검증 테스트가 저장소 mock 없이 조건 객체만 직접 검증하도록 단순화됐다.

### 파일 규모 변화

- `LedgerService.java`는 v.0.0.49의 1,143줄에서 968줄로 감소했다.
- `TransactionQueryService.java` 203줄과 `TransactionSearchCriteria.java` 32줄을 추가했다.
- `LedgerService`에서 거래 조회·검색·CSV 관련 약 175줄을 제거했다.

## 3. v.0.0.49 대비 상태 변경

| 항목 | v.0.0.49 상태 | v.0.0.50 상태 | 메모 |
| --- | --- | --- | --- |
| 거래 조회 서비스 분리 | 미완료 | 완료 | 월·기간·일·단건·할부 그룹 |
| 검색 조건 객체 분리 | 미완료 | 완료 | record 생성 시 유효성 검증 |
| JPA Specification 분리 | 미완료 | 완료 | 검색 조건·정렬 전담 |
| 거래 CSV 책임 분리 | 미완료 | 완료 | 조회 서비스로 이동 |
| 거래·할부 변경 서비스 | 미완료 | 미완료 | 생성·수정·삭제 후속 분리 |
| `LedgerService` 책임 분리 | 부분 완료 | 부분 완료 | 1,143줄에서 968줄로 감소 |
| 브라우저 스모크 테스트 | 완료 | 완료 | 원장·검색 연계 화면 통과 |
| Docker 이미지 빌드 | 완료 | 완료 | 프론트엔드·백엔드 통과 |
| Gradle 테스트 | 완료 | 완료 | 전체 테스트 통과 |

## 4. 아직 부분 완료인 항목

### 백엔드 서비스 구조

- `LedgerService`가 거래·할부 생성·수정·삭제와 자산 잔액 반영을 함께 담당한다.
- 자산·카테고리 관리와 월간·연간·기간 통계 집계도 아직 같은 서비스에 남아 있다.
- 거래 변경 서비스 분리 시 반복 거래 서비스의 `LedgerService` 의존성도 새 서비스로 이동해야 한다.
- 통계 helper와 예산 사용 통계를 조회 전용 통계 서비스로 분리할 필요가 있다.

### 검색 기능

- 검색 결과는 아직 전체 목록을 반환하며 페이지네이션과 정렬 선택을 지원하지 않는다.
- 후속 페이지네이션 도입 시 `TransactionSearchCriteria`에 페이지·정렬 값을 확장할 수 있다.

### 프론트엔드 상태 구조

- 거래, 자산, 명의, 예산과 일정 mutation orchestration은 여전히 App에 남아 있다.
- 공통 HTTP 오류를 사용자 메시지로 변환하는 정책이 아직 통일되지 않았다.

## 5. 다음 개발 우선순위

### v.0.0.51 권장

1. 백엔드 거래·할부 변경 서비스 분리
2. 반복 거래 서비스의 거래 생성 의존성 이전
3. 통계 조회 서비스 분리
4. 프론트엔드 도메인별 mutation hook과 오류 처리 통일

### 이후 권장

1. 검색 결과 페이지네이션·정렬
2. 영수증 첨부 파일 정리 정책 고도화

## 6. 검증

- `docker run --rm -v "${PWD}\backend:/workspace" -w /workspace gradle:8.10.2-jdk21 gradle test --no-daemon` 통과
- `docker compose build frontend` 통과
- `docker compose build backend` 통과
- `docker compose up -d frontend backend`로 최신 이미지 재생성 완료
- `powershell -ExecutionPolicy Bypass -File .\scripts\browser-smoke.ps1` 통과
- `git diff --check` 통과

## 7. 개발 메모

- 조회와 변경 책임을 먼저 나눠 다음 버전의 거래 mutation 분리 범위를 선명하게 만들었다.
- 검색 조건 검증을 record로 이동해 컨트롤러와 서비스가 동일한 유효성 규칙을 사용한다.
- 외부 API 계약을 유지했으므로 프론트엔드 수정 없이 백엔드 내부 구조만 개선했다.
