# 편한가계부 부채 금리 및 이자 자동 차감 개발 기록 v.0.3.16

## 개발 목표

- 부채 자산 설정 시 금리를 저장할 수 있게 한다.
- 부채별 출금계좌, 차감일, 자동 차감 여부를 설정한다.
- 자동 차감 대상 부채는 매월 지정일에 이자 지출 거래를 자동 생성하고 출금계좌 잔액에서 차감한다.

## 정책

- 금리는 연이율 `%`로 입력한다.
- 월 이자 계산식:

```text
abs(부채 잔액) * 연이율 / 100 / 12
```

- 계산 금액은 원 단위 반올림한다.
- 자동 차감은 이자 지출만 처리한다.
- 원금 상환은 자동으로 줄이지 않는다. 원금 상환은 기존 거래/이체 입력으로 처리한다.
- 같은 월에는 같은 부채의 자동 차감을 1회만 실행한다.

## 백엔드 변경

### 부채 프로필 추가

- 신규 도메인:
  - `DebtProfile`
- 신규 테이블:
  - `debt_profiles`
- 저장 필드:
  - 부채 자산
  - 출금계좌
  - 연 금리
  - 차감일
  - 자동 차감 여부
  - 마지막 차감 월

### 자산 API 확장

- `SaveAssetRequest`에 부채 설정 필드를 추가했다.
  - `debtPaymentAccountId`
  - `annualInterestRate`
  - `debtPaymentDay`
  - `debtAutoDeduct`
- `AssetDto`에 `debt` 응답을 추가했다.

### 자동 차감 스케줄러 추가

- 신규 서비스:
  - `DebtAutoDeductionService`
- 신규 스케줄러:
  - `DebtAutoDeductionScheduler`
- 기본 실행 설정:

```yaml
app:
  debt-auto-deduction:
    enabled: true
    cron: 0 30 4 * * *
    zone: Asia/Seoul
```

- 실행 결과:
  - `대출이자` 지출 카테고리가 없으면 생성
  - 거래명: `{부채명} 이자 자동 차감`
  - 출금계좌 잔액에서 이자 금액 차감
  - 부채 원금 잔액은 변경하지 않음

## 프론트엔드 변경

- 자산 등록/수정 화면에서 `부채` 선택 시 부채 자동 차감 설정 영역을 표시한다.
- 입력 항목:
  - 출금계좌
  - 연 금리(%)
  - 차감일
  - 이자 자동 차감 여부

## 변경 파일

- `backend/src/main/java/com/comfortableledger/ledger/domain/Asset.java`
- `backend/src/main/java/com/comfortableledger/ledger/domain/DebtProfile.java`
- `backend/src/main/java/com/comfortableledger/ledger/dto/AssetDtos.java`
- `backend/src/main/java/com/comfortableledger/ledger/repository/DebtProfileRepository.java`
- `backend/src/main/java/com/comfortableledger/ledger/service/asset/AssetManagementService.java`
- `backend/src/main/java/com/comfortableledger/ledger/service/asset/DebtAutoDeductionService.java`
- `backend/src/main/java/com/comfortableledger/ledger/service/asset/DebtAutoDeductionScheduler.java`
- `backend/src/main/resources/application.yml`
- `frontend/src/main.jsx`
- `frontend/src/hooks/useManagementMutations.js`
- `frontend/src/screens/AssetsScreen.jsx`
- `README.md`

## 검증

```text
docker run --rm -v "${PWD}\backend:/workspace" -w /workspace gradle:8.10.2-jdk21 gradle test --no-daemon
BUILD SUCCESSFUL
```

```text
docker run --rm -v "${PWD}\frontend:/workspace" -w /workspace node:22-alpine sh -c "npm ci && npm run build"
✓ built
```

## 운영 참고

- 서버 반영 후 백엔드 재시작 시 `debt_profiles` 테이블은 JPA `ddl-auto=update`에 의해 생성된다.
- 자동 차감 비활성화가 필요하면 환경 변수로 제어한다.

```bash
DEBT_AUTO_DEDUCTION_ENABLED=false
```
