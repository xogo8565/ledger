# 편한가계부 후속 개발 진행 기록 v.0.0.52

작성일: 2026-06-25

## 1. 기준

- 이전 기록: `plan/plan-v.0.0.51.md`
- 이번 개발 범위: 백엔드 통계 조회와 자산·카테고리 관리 서비스 분리 및 `LedgerService` 제거
- 검증 방식: Gradle 전체 테스트, Docker Compose 이미지 빌드, Playwright 브라우저 스모크 테스트

## 2. 이번 버전 개발 완료

### 자산·카테고리 관리 서비스 분리

- `backend/src/main/java/com/comfortableledger/ledger/service/AssetManagementService.java`를 추가했다.
- 자산 목록·요약·생성·수정·숨김 처리와 카드 자산 프로필 관리를 이동했다.
- 카테고리 목록·생성·수정·비활성화도 같은 관리 서비스로 이동했다.
- 등록 명의 검증, 자산 그룹 기본값, 카테고리 아이콘·색상 기본값을 서비스 내부에 모았다.

### 통계 조회 서비스 분리

- `backend/src/main/java/com/comfortableledger/ledger/service/StatisticsService.java`를 추가했다.
- 월간·연간·기간 통계와 연간 예산 사용 통계를 이동했다.
- 카테고리·태그·공동/개인·명의별 지출과 주차별 합계 helper를 함께 이동했다.
- 월간 카테고리 예산 사용률과 초과 여부 계산도 통계 서비스가 담당한다.

### `LedgerService` 제거

- 남아 있던 모든 책임이 도메인 서비스로 이전되어 `LedgerService.java`를 삭제했다.
- `LedgerController`는 자산 관리, 통계, 명의, 예산, 거래 Query·Command 서비스를 역할별로 주입받는다.
- bootstrap 응답도 자산·카테고리, 거래 목록, 월간 통계를 각 전용 서비스에서 조합한다.
- 백엔드 메인·테스트 소스에서 `LedgerService` 참조가 더 이상 남지 않았다.

### 테스트 소유권 이전

- 자산 요약 테스트를 `AssetManagementServiceAssetSummaryTest`로 이전했다.
- 태그·소비 구분·명의·주차·기간·연간 예산 통계 테스트를 `StatisticsService*Test`로 이전했다.
- 테스트 이름과 직접 호출 대상이 실제 책임 서비스와 일치하도록 정리했다.

### 파일 규모 변화

- v.0.0.51에서 689줄이던 `LedgerService.java`를 완전히 제거했다.
- `AssetManagementService.java` 230줄과 `StatisticsService.java` 320줄로 책임을 분리했다.
- 이전 1,334줄 단일 서비스는 명의, 예산, 거래 Query·Command, 통계, 자산 관리 서비스로 해체됐다.

## 3. v.0.0.51 대비 상태 변경

| 항목 | v.0.0.51 상태 | v.0.0.52 상태 | 메모 |
| --- | --- | --- | --- |
| 자산 관리 서비스 분리 | 미완료 | 완료 | 목록·요약·CRUD·카드 프로필 |
| 카테고리 관리 서비스 분리 | 미완료 | 완료 | 목록·CRUD·기본값 |
| 통계 조회 서비스 분리 | 미완료 | 완료 | 월간·연간·기간·예산 사용 |
| 통계 helper 테스트 소유권 | LedgerService | StatisticsService | 파일·호출 대상 이전 |
| `LedgerService` 책임 분리 | 부분 완료 | 완료 | 클래스와 모든 참조 제거 |
| 브라우저 스모크 테스트 | 완료 | 완료 | 자산·카테고리·예산 UI 포함 통과 |
| Docker 이미지 빌드 | 완료 | 완료 | 프론트엔드·백엔드 통과 |
| Gradle 테스트 | 완료 | 완료 | 전체 테스트 통과 |

## 4. 현재 백엔드 서비스 구조

- `AssetManagementService`: 자산·카테고리 관리와 자산 요약
- `BudgetService`: 월별 예산 설정 조회·저장·전월 복사
- `MemberService`: 명의 관리와 기존 개인 지출 마이그레이션
- `TransactionQueryService`: 거래 조회·검색·CSV
- `TransactionCommandService`: 거래·할부 생성·수정·삭제와 잔액 반영
- `StatisticsService`: 월간·연간·기간 통계와 예산 사용 통계
- `CardService`, `RecurringTransactionService`, `ReceiptService`, `ImportTextService`: 기존 독립 도메인

## 5. 아직 부분 완료인 항목

### 프론트엔드 상태 구조

- 거래, 자산, 명의, 예산과 일정 mutation orchestration은 여전히 App에 남아 있다.
- 공통 HTTP 오류를 사용자 메시지로 변환하는 정책이 아직 통일되지 않았다.

### 검색 기능

- 검색 결과는 아직 전체 목록을 반환하며 페이지네이션과 정렬 선택을 지원하지 않는다.

### 테스트와 파일 정책

- 새 Command·관리 서비스의 저장소 조합을 검증하는 통합 테스트를 확충할 수 있다.
- 영수증 첨부 파일 정리 정책 고도화는 아직 미완료다.

## 6. 다음 개발 우선순위

### v.0.0.53 권장

1. 프론트엔드 도메인별 mutation hook 분리
2. API 오류 응답·사용자 메시지 처리 통일
3. 검색 결과 페이지네이션·정렬 설계
4. 백엔드 서비스별 통합 테스트 확충

### 이후 권장

1. 영수증 첨부 파일 정리 정책 고도화
2. API 재시도와 로딩 UX 통일

## 7. 검증

- `docker run --rm -v "${PWD}\backend:/workspace" -w /workspace gradle:8.10.2-jdk21 gradle test --no-daemon` 통과
- `docker compose build frontend` 통과
- `docker compose build backend` 통과
- `docker compose up -d frontend backend`로 최신 이미지 재생성 완료
- `powershell -ExecutionPolicy Bypass -File .\scripts\browser-smoke.ps1` 통과
- `git diff --check` 통과

## 8. 개발 메모

- 외부 API 계약을 유지한 채 단일 대형 서비스를 제거해 이후 도메인별 변경 범위가 훨씬 선명해졌다.
- 읽기·쓰기 분리된 거래 서비스와 독립 통계 서비스 덕분에 페이지네이션과 통계 기능을 서로 영향 없이 확장할 수 있다.
- 다음 버전부터는 백엔드 구조 분리보다 프론트엔드 mutation orchestration과 오류 UX 정리에 집중하는 편이 효율적이다.
