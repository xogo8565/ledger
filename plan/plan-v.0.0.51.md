# 편한가계부 후속 개발 진행 기록 v.0.0.51

작성일: 2026-06-25

## 1. 기준

- 이전 기록: `plan/plan-v.0.0.50.md`
- 이번 개발 범위: 백엔드 거래·할부 변경 서비스 분리와 카드·반복 거래 의존성 이전
- 검증 방식: Gradle 전체 테스트, Docker Compose 이미지 빌드, Playwright 브라우저 스모크 테스트

## 2. 이번 버전 개발 완료

### 거래 명령 서비스 분리

- `backend/src/main/java/com/comfortableledger/ledger/service/TransactionCommandService.java`를 추가했다.
- 일반 거래 생성·수정·삭제와 할부 그룹 생성·수정·삭제를 `LedgerService`에서 이동했다.
- 거래 생성에 필요한 가구·작성자·소비 명의·카테고리·자산 조회를 명령 서비스 내부로 모았다.
- 거래 유형별 자산 잔액 적용과 기존 거래 변경 시 잔액 원상복구를 명령 서비스가 전담한다.

### 할부 변경 책임 정리

- 할부 금액 균등 분할과 마지막 회차 잔액 보정 로직을 유지했다.
- 할부 개월 수 증가 시 새 회차를 생성하고 감소 시 초과 회차를 삭제한다.
- 할부 축소 시 삭제되는 회차의 영수증을 마지막 유지 회차로 재연결하는 정책을 보존했다.
- 할부 그룹 존재 여부, 지출 유형과 최소 2개월 검증을 새 서비스로 이동했다.

### 카드 결제·반복 거래 의존성 이전

- `RecurringTransactionService`가 더 이상 `LedgerService`를 의존하지 않고 `TransactionCommandService`로 거래를 생성한다.
- `CardService`도 카드 자동 결제 거래를 `TransactionCommandService`로 생성한다.
- `CardPaymentScheduler`와 `CardController`에서 `LedgerService` 전달 인자를 제거했다.
- 거래 생성 경로가 수동 입력, 반복 거래, 카드 자동 결제 모두 하나의 명령 서비스로 통합됐다.

### 컨트롤러 의존성 정리

- `LedgerController`의 거래·할부 POST·PUT·DELETE 엔드포인트를 `TransactionCommandService`로 연결했다.
- 거래 조회는 `TransactionQueryService`, 변경은 `TransactionCommandService`가 담당해 CQRS 형태의 읽기·쓰기 경계가 만들어졌다.
- 외부 API 경로와 DTO 계약은 변경하지 않았다.

### `LedgerService` 의존성 축소

- 거래 변경 책임 제거로 `ReceiptAttachmentRepository` 의존성을 제거했다.
- 거래 DTO 요청, 할부 UUID, 자산 잔액 변경 helper를 원장 서비스에서 제거했다.
- 관련 생성자 테스트 구성을 새 의존성 수에 맞게 정리했다.

### 파일 규모 변화

- `LedgerService.java`는 v.0.0.50의 968줄에서 689줄로 감소했다.
- `TransactionCommandService.java` 297줄을 독립 서비스로 구성했다.
- `LedgerService`에서 거래·할부 변경 관련 약 279줄을 제거했다.

## 3. v.0.0.50 대비 상태 변경

| 항목 | v.0.0.50 상태 | v.0.0.51 상태 | 메모 |
| --- | --- | --- | --- |
| 일반 거래 변경 서비스 분리 | 미완료 | 완료 | 생성·수정·삭제·잔액 반영 |
| 할부 변경 서비스 분리 | 미완료 | 완료 | 생성·회차 증감·삭제·영수증 보존 |
| 반복 거래 생성 의존성 | LedgerService | TransactionCommandService | 직접 명령 서비스 사용 |
| 카드 자동 결제 의존성 | LedgerService | TransactionCommandService | 컨트롤러 전달 인자 제거 |
| 거래 조회·변경 경계 | 부분 완료 | 완료 | Query·Command 서비스 분리 |
| `LedgerService` 책임 분리 | 부분 완료 | 부분 완료 | 968줄에서 689줄로 감소 |
| 브라우저 스모크 테스트 | 완료 | 완료 | UI 거래 생성·수정 흐름 통과 |
| Docker 이미지 빌드 | 완료 | 완료 | 프론트엔드·백엔드 통과 |
| Gradle 테스트 | 완료 | 완료 | 전체 테스트 통과 |

## 4. 아직 부분 완료인 항목

### 백엔드 서비스 구조

- `LedgerService`가 자산·카테고리 관리와 월간·연간·기간 통계를 함께 담당한다.
- 통계 집계 helper와 예산 사용 통계를 조회 전용 통계 서비스로 분리할 필요가 있다.
- 자산 요약과 자산·카테고리 CRUD도 관리 서비스로 이동할 수 있다.

### 프론트엔드 상태 구조

- 거래, 자산, 명의, 예산과 일정 mutation orchestration은 여전히 App에 남아 있다.
- 공통 HTTP 오류를 사용자 메시지로 변환하는 정책이 아직 통일되지 않았다.

### 기타 후속 항목

- 검색 결과 페이지네이션과 정렬 선택은 아직 미완료다.
- 영수증 첨부 파일 정리 정책 고도화는 아직 미완료다.

## 5. 다음 개발 우선순위

### v.0.0.52 권장

1. 백엔드 통계 조회 서비스 분리
2. 자산·카테고리 관리 서비스 분리
3. 프론트엔드 도메인별 mutation hook 분리
4. API 오류 응답·사용자 메시지 처리 통일

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

- 거래 조회와 변경 서비스가 분리되어 검색·페이지네이션 작업과 거래 mutation 작업을 독립적으로 확장할 수 있다.
- 카드 결제와 반복 거래도 동일한 명령 서비스를 사용하므로 자산 잔액 반영 규칙이 한 경로로 수렴했다.
- 다음 버전에서는 남은 통계와 자산·카테고리 책임을 분리하면 `LedgerService` 해체 작업을 마무리할 수 있다.
