# 편한가계부 후속 개발 진행 기록 v.0.0.56

작성일: 2026-06-26

## 1. 기준

- 이전 기록: `plan/plan-v.0.0.55.md`
- 이번 개발 범위: 영수증 첨부 파일 정리 정책 고도화
- 검증 방식: Gradle 전체 테스트, Docker Compose 이미지 빌드, Playwright 브라우저 스모크 테스트

## 2. 이번 버전 개발 완료

### 영수증 파일 정리 전담 서비스

- `ReceiptFileStorage`를 추가해 영수증 물리 파일 삭제 책임을 분리했다.
- DB 트랜잭션이 활성화된 경우 파일 삭제를 `afterCommit` 이후에 수행하도록 했다.
- 트랜잭션이 없는 컨텍스트에서는 즉시 삭제하되, 파일 삭제 실패는 로그로 남기고 사용자 요청 자체를 실패시키지 않도록 했다.
- 업로드 도중 오류가 발생하면 이미 저장된 파일을 즉시 best-effort 방식으로 정리하도록 유지했다.

### 개별 영수증 삭제 정책 개선

- `ReceiptService.delete`가 영수증 메타데이터를 삭제한 뒤 파일 삭제를 `ReceiptFileStorage.deleteAfterCommit`에 위임하도록 변경했다.
- 파일 삭제 실패로 인해 DB 삭제가 실패처럼 보이던 흐름을 제거했다.
- `ReceiptController.delete`의 불필요한 `IOException` 전파를 제거했다.

### 거래 삭제 시 영수증 파일 정리

- `TransactionCommandService.deleteTransaction`이 거래 삭제 전에 연결된 영수증 메타데이터를 삭제하고, 물리 파일 삭제를 커밋 이후 정리하도록 했다.
- `deleteInstallmentTransactions`도 할부 그룹 전체 삭제 시 연결된 영수증 메타데이터와 파일을 정리하도록 했다.
- `ReceiptAttachmentRepository.findByTransactionIdIn`을 추가해 여러 거래의 영수증을 한 번에 조회할 수 있게 했다.
- 할부 개월 수 축소 시 기존처럼 제거되는 회차의 영수증은 마지막 남는 회차로 재배정하며, 이 경우 파일은 삭제하지 않는다.

### 테스트 보강

- `ReceiptServiceTest` 생성자 구성을 새 `ReceiptFileStorage` 의존성에 맞게 갱신했다.
- 개별 영수증 삭제 시 DB 메타데이터 삭제와 저장 파일 삭제가 함께 수행되는 테스트를 추가했다.

## 3. v.0.0.55 대비 상태 변경

| 항목 | v.0.0.55 상태 | v.0.0.56 상태 | 메모 |
| --- | --- | --- | --- |
| 개별 영수증 삭제 | 파일 삭제 직접 수행 | 파일 정리 서비스 위임 | after-commit/best-effort |
| 거래 삭제 시 영수증 파일 | 누수 가능 | 삭제 대상 파일 정리 | 단일 거래/할부 그룹 삭제 |
| 파일 삭제 실패 처리 | 요청 실패처럼 보일 수 있음 | 로그 기록 후 DB 삭제 유지 | 사용자 흐름 안정화 |
| 업로드 실패 파일 정리 | 부분 정리 | 유지 및 전담 서비스 사용 | 저장된 파일 즉시 정리 |
| 테스트 | 업로드 검증 중심 | 삭제 파일 정리 테스트 추가 | `ReceiptServiceTest` |

## 4. 아직 부분 완료인 항목

### 저장소-DB 불일치 복구

- 이미 과거에 누적된 orphan 파일을 스캔하고 정리하는 운영성 도구는 아직 없다.
- DB에는 있으나 파일이 없는 영수증을 진단하는 관리 API 또는 배치 작업도 아직 미구현이다.

### 검색 조건 전체 CSV

- 현재 검색 CSV는 현재 페이지 기준이다.
- 검색 조건 전체 결과 CSV는 별도 백엔드 export API와 최대 행 제한 정책이 필요하다.

### mutation UX

- mutation 실행 중 중복 제출 방지와 pending 표시가 아직 화면별로 통일되어 있지 않다.
- API 재시도와 화면 상단 오류 배너도 아직 미완료다.

## 5. 다음 개발 우선순위

### v.0.0.57 권장

1. 검색 조건 전체 결과 CSV 내보내기 API 검토
2. 백엔드 서비스별 통합 테스트 확충
3. mutation pending 상태와 중복 제출 방지
4. 영수증 저장소-DB 불일치 진단/정리 도구 설계

### 이후 권장

1. API 재시도와 화면 상단 오류 배너
2. 대량 거래 검색 UX 개선
3. 운영성 점검 스크립트 확대

## 6. 검증

- `docker run --rm -v "${PWD}\backend:/workspace" -w /workspace gradle:8.10.2-jdk21 gradle test --no-daemon` 통과
- `docker compose build backend` 통과
- `docker compose build frontend` 통과
- `docker compose up -d frontend backend`로 최신 이미지 재생성 완료
- `powershell -ExecutionPolicy Bypass -File .\scripts\browser-smoke.ps1` 통과
- `git diff --check` 통과

## 7. 개발 메모

- 파일 시스템 작업은 DB 트랜잭션과 원자적으로 묶을 수 없으므로, DB를 기준 상태로 삼고 파일 삭제는 커밋 이후 best-effort로 수행하는 정책을 택했다.
- 파일 삭제 실패 로그가 쌓이면 운영자가 해당 경로를 수동 정리하거나, 이후 orphan 파일 정리 도구가 처리할 수 있다.
- 거래 삭제 시 연결 영수증 메타데이터를 먼저 삭제해 FK 제약과 파일 누수를 함께 피하도록 했다.
