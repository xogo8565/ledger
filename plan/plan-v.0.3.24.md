# 편한가계부 부채 이자 자동 차감 운영 API 개발 기록 v.0.3.24

작성일: 2026-07-01

## 1. 기준

- 이전 기록: `plan/plan-v.0.3.23.md`
- 이번 개발 범위: P2 `부채 자동 이자 차감 운영 보강`
- 기준 문서: `plan/new-development-items.md`

## 2. 구현 내용

### 자동 차감 상태 조회 API 추가

- 신규 API:
  - `GET /api/debts/auto-deductions`
- 선택 파라미터:
  - `date`: 특정 기준일로 실행 가능 여부 계산
- 응답:
  - 기준일
  - 전체 부채 프로필 수
  - 실행 가능 프로필 수
  - 부채별 자동 차감 상태 목록

부채별 상태에는 다음 값을 포함한다.

- 부채 프로필 ID
- 부채 자산 ID/이름/잔액
- 출금계좌 ID/이름
- 연 금리
- 차감일
- 자동 차감 여부
- 마지막 차감 월
- 예상 월 이자
- 기준일상 도래 여부
- 실행 가능 여부
- 상태 코드

상태 코드 예시:

- `EXECUTABLE`
- `AUTO_DEDUCT_OFF`
- `ALREADY_DEDUCTED`
- `NOT_DUE`
- `DEBT_ASSET_HIDDEN`
- `PAYMENT_ACCOUNT_MISSING`
- `NO_INTEREST`

### 자동 차감 수동 실행 API 추가

- 신규 API:
  - `POST /api/debts/auto-deductions/execute`
- 선택 파라미터:
  - `date`: 특정 기준일로 실행
- 기존 스케줄러와 동일한 `DebtAutoDeductionService.executeDueDeductions`를 사용한다.
- 이미 해당 월 차감이 완료된 부채는 `lastDeductedMonth` 기준으로 중복 생성하지 않는다.

### 문서 갱신

- README에 부채 자동 차감 상태 조회/수동 실행 예시를 추가했다.
- 신규 개발항목 정리 문서의 부채 자동 이자 차감 운영 보강 상태를 갱신했다.

## 3. 변경 파일

- `backend/src/main/java/com/comfortableledger/ledger/domain/DebtProfile.java`
- `backend/src/main/java/com/comfortableledger/ledger/dto/DebtDtos.java`
- `backend/src/main/java/com/comfortableledger/ledger/repository/DebtProfileRepository.java`
- `backend/src/main/java/com/comfortableledger/ledger/service/asset/DebtAutoDeductionService.java`
- `backend/src/main/java/com/comfortableledger/ledger/controller/LedgerController.java`
- `README.md`
- `plan/new-development-items.md`

## 4. 검증

```text
docker run --rm -v "${PWD}\backend:/workspace" -w /workspace gradle:8.10.2-jdk21 gradle test --no-daemon
git diff --check
```

결과:

- 백엔드 Gradle 테스트 통과
- `git diff --check` 오류 없음. 단, Windows CRLF 변환 경고만 출력됨
- Gradle deprecation warning은 기존과 동일하게 출력됨

## 5. 남은 과제

1. 자동 차감 실행 이력을 별도 테이블로 저장할지 결정한다.
2. 프론트 자산/관리 화면에서 상태 조회 API를 보여줄지 결정한다.
3. 자동 차감 거래 삭제/수정 시 `lastDeductedMonth`를 되돌릴지, 수동 재실행 경로를 둘지 정책을 정한다.

