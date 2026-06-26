# 편한가계부 후속 개발 진행 기록 v.0.0.49

작성일: 2026-06-25

## 1. 기준

- 이전 기록: `plan/plan-v.0.0.48.md`
- 이번 개발 범위: 백엔드 `LedgerService`의 명의 관리와 예산 설정 책임 분리
- 검증 방식: Gradle 전체 테스트, Docker Compose 이미지 빌드, Playwright 브라우저 스모크 테스트

## 2. 이번 버전 개발 완료

### 명의 관리 서비스 분리

- `backend/src/main/java/com/comfortableledger/ledger/service/MemberService.java`를 추가했다.
- 명의 목록, 생성, 수정, 삭제와 기존 개인 지출 명의 마이그레이션을 `LedgerService`에서 이동했다.
- 명의 수정 시 자산 소유자명 동기화, OWNER 삭제 방지, 자산·개인 지출 사용 여부 검증을 함께 이동했다.
- 명의 정규화와 기본 가구·OWNER 조회를 명의 도메인 내부 책임으로 정리했다.

### 예산 설정 서비스 분리

- `backend/src/main/java/com/comfortableledger/ledger/service/BudgetService.java`를 추가했다.
- 월별 예산 설정 조회, 저장, 전월 예산 복사를 `LedgerService`에서 이동했다.
- 전체·카테고리 예산 음수 검증, 월 예산 생성, 카테고리 예산 갱신·복사 로직을 예산 도메인에 모았다.
- 월간·연간 예산 사용 통계는 거래 통계와 결합되어 있어 이번 단계에서는 `LedgerService`에 유지했다.

### 컨트롤러 의존성 분리

- `LedgerController`가 `LedgerService`, `MemberService`, `BudgetService`, `CardService`를 역할별로 주입받도록 변경했다.
- `/members/**` 요청은 `MemberService`로, `/budgets/settings/**` 요청은 `BudgetService`로 전달한다.
- 외부 API 경로와 응답 DTO는 변경하지 않아 프론트엔드 호환성을 유지했다.

### 테스트 소유권 정리

- 소비 명의 마이그레이션 테스트를 `MemberServiceConsumerMigrationTest`로 이전했다.
- 테스트가 새 서비스의 저장소 의존성만 구성하도록 단순화했다.
- 기존 정적 통계와 거래·예산 요약 테스트는 `LedgerService`에 유지했다.

### 파일 규모 변화

- `LedgerService.java`는 1,334줄에서 1,143줄로 감소했다.
- `MemberService.java` 144줄과 `BudgetService.java` 152줄을 독립 서비스로 구성했다.
- 명의·예산 설정 관련 약 190줄의 책임을 원장 서비스에서 제거했다.

## 3. v.0.0.48 대비 상태 변경

| 항목 | v.0.0.48 상태 | v.0.0.49 상태 | 메모 |
| --- | --- | --- | --- |
| 명의 관리 서비스 분리 | 미완료 | 완료 | CRUD·자산 명의 동기화·마이그레이션 |
| 예산 설정 서비스 분리 | 미완료 | 완료 | 조회·저장·전월 복사 |
| 컨트롤러 도메인 위임 | 부분 완료 | 부분 완료 | 명의·예산 분리, 거래·통계 후속 |
| `LedgerService` 책임 분리 | 미완료 | 부분 완료 | 1,334줄에서 1,143줄로 감소 |
| 소비 명의 테스트 소유권 | LedgerService | MemberService | 새 서비스 직접 검증 |
| 브라우저 스모크 테스트 | 완료 | 완료 | 자산·카테고리·예산 UI 포함 통과 |
| Docker 이미지 빌드 | 완료 | 완료 | 프론트엔드·백엔드 통과 |
| Gradle 테스트 | 완료 | 완료 | 전체 테스트 통과 |

## 4. 아직 부분 완료인 항목

### 백엔드 서비스 구조

- `LedgerService`가 거래 CRUD, 할부, 검색, CSV, 자산·카테고리, 월간·연간·기간 통계를 함께 담당한다.
- 거래 변경과 자산 잔액 반영을 전담하는 거래 서비스 분리가 필요하다.
- 검색 조건과 JPA Specification 생성 책임을 별도 검색 객체 또는 서비스로 이동할 필요가 있다.
- 통계 집계 helper와 예산 사용 통계를 조회 전용 서비스로 분리할 수 있다.

### 프론트엔드 상태 구조

- 거래, 자산, 명의, 예산과 일정 mutation orchestration은 여전히 App에 남아 있다.
- 공통 HTTP 오류를 사용자 메시지로 변환하는 정책이 아직 통일되지 않았다.

### 기타 후속 항목

- 검색 결과 페이지네이션과 정렬 선택은 아직 미완료다.
- 영수증 첨부 파일 정리 정책 고도화는 아직 미완료다.

## 5. 다음 개발 우선순위

### v.0.0.50 권장

1. 백엔드 거래·할부 서비스 분리
2. 검색 조건 객체와 검색 서비스 분리
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

- 서비스 분리 후에도 기존 `/api` 계약을 유지해 프론트엔드 변경 없이 백엔드 구조만 개선했다.
- 통계에 필요한 예산 저장소 의존성은 `LedgerService`에 남아 있으므로, 다음 분리에서는 조회 전용 통계 서비스를 먼저 설계하는 편이 안전하다.
- 명의 마이그레이션 테스트를 새 서비스로 옮겨 책임 이동이 테스트 구조에도 반영되도록 했다.
