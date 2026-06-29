# 편한가계부 다건 거래 순차 입력 개발 기록 v.0.3.9

작성일: 2026-06-29

## 1. 개발 목표

- 날짜 헤더 아래 여러 거래가 붙어 있는 텍스트를 분석했을 때 첫 1건만 처리되는 문제를 개선한다.
- 다건 분석 결과를 한 번에 모두 저장하지 않고, 사용자가 각 거래를 확인하면서 1건씩 순차 저장할 수 있게 한다.

## 2. 구현 내용

### 프론트엔드 순차 입력 대기열

- 문자/거래 목록 분석 결과의 `items`가 여러 건이면 프론트에서 대기열을 만든다.
- 첫 번째 거래를 거래 입력 화면에 채운다.
- 사용자가 저장하면 다음 거래를 자동으로 입력 화면에 채운다.
- 마지막 거래 저장 후에는 기존처럼 입력 화면을 닫고 목록을 갱신한다.
- 중간에 화면을 닫으면 남은 대기열은 초기화한다.

### 진행 상태 표시

- 거래 입력 화면 상단에 순차 입력 진행 상태를 표시한다.

예:

```text
자동 입력 처리 중
3/24건 저장 후 다음 거래가 자동으로 표시됩니다.
```

### 자산명 자동 매칭

- 다건 파싱 결과에 `assetName`이 있으면 기존 자산명과 비교해 가능한 경우 `assetId`를 자동 선택한다.
- 이체 후보는 `→` 기준으로 출금/입금 자산명을 추정한다.
- 자산명이 정확히 맞지 않으면 빈 값으로 두고 사용자가 직접 선택하게 한다.

### 0원 거래 제외

- `0원 | 나이스_머니포인트` 같은 저장 불가 라인은 다건 후보에서 제외한다.
- 기존 거래 저장 검증은 금액 0을 저장하지 않으므로, 순차 입력이 0원 항목에서 멈추지 않도록 파싱 단계에서 제거했다.

## 3. 적용 파일

- `backend/src/main/java/com/comfortableledger/ledger/service/importing/ImportTextService.java`
- `backend/src/test/java/com/comfortableledger/ledger/service/importing/ImportTextServiceBasicTest.java`
- `frontend/src/main.jsx`
- `frontend/src/hooks/useTransactionMutations.js`
- `frontend/src/screens/TransactionScreens.jsx`
- `frontend/src/styles.css`
- `README.md`

## 4. 검증

```powershell
docker run --rm -v "${PWD}\backend:/workspace" -w /workspace gradle:8.10.2-jdk21 gradle test --no-daemon
docker run --rm -v "${PWD}\frontend:/app" -w /app node:22-alpine sh -c "npm install && npm run build"
docker compose build backend
docker compose build frontend
```

결과:

- 백엔드 Gradle 테스트 통과
- 프론트엔드 Vite 빌드 통과
- 백엔드 Docker 이미지 빌드 통과
- 프론트엔드 Docker 이미지 빌드 통과

## 5. 후속 권장

1. 순차 입력 중 `건너뛰기` 버튼을 추가한다.
2. 다건 분석 결과를 저장 전 목록으로 보여주고, 제외할 항목을 선택할 수 있게 한다.
3. 자산명 매칭 실패 항목은 별도 경고로 표시한다.
