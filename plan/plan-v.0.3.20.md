# 편한가계부 OCR 결과 신뢰도/검토 정책 UI 보강 개발 기록 v.0.3.20

작성일: 2026-07-01

## 1. 기준

- 이전 기록: `plan/plan-v.0.3.19.md`
- 이번 개발 범위: P1 `OCR 결과 신뢰도/검토 정책 UI 보강`
- 기준 문서: `plan/new-development-items.md`

## 2. 구현 내용

### OCR 정책 메타데이터 표시

- 영수증 OCR 결과 화면에 정책 카드를 추가했다.
- 표시 항목:
  - 신뢰도 점수
  - 검토 필요 여부
  - 인식된 필드 배지: 날짜, 내용/품명, 금액
  - 검토 사유 요약
- 신뢰도 점수에 따라 카드 색상과 progress bar 색상을 구분한다.

### 재분석 fallback 정책 표시

- 최초 OCR 분석은 서버의 `policy` DTO를 사용한다.
- 수정한 OCR 원문을 재분석하는 흐름은 기존 텍스트 파싱 API를 사용하므로 서버 `policy` DTO가 없다.
- 이 경우 프론트가 후보 목록과 warning을 기준으로 동일한 점수 계산 방식의 fallback 정책 요약을 표시한다.

### 품목표 헤더 유사어 확장

- 백엔드 OCR 품목표 헤더 인식 기준을 확장했다.
- 프론트 fallback 후보 추출의 품목표 헤더 인식 기준도 동일하게 확장했다.
- 추가 대응 키워드:
  - 품명, 품목, 상품, 상품명, 메뉴, 제품
  - 단가, 수량, 단위, qty, quantity
  - 금액, 합계, total, amount

## 3. 변경 파일

- `backend/src/main/java/com/comfortableledger/ledger/service/receipt/ReceiptOcrService.java`
- `frontend/src/screens/TransactionScreens.jsx`
- `frontend/src/styles.css`
- `plan/new-development-items.md`

## 4. 검증

```text
docker run --rm -v "${PWD}:/workspace" -w /workspace/frontend node:22-alpine sh -c "npm ci && npm run build"
docker run --rm -v "${PWD}\backend:/workspace" -w /workspace gradle:8.10.2-jdk21 gradle test --no-daemon
```

결과:

- 프론트엔드 Vite 프로덕션 빌드 통과
- 백엔드 Gradle 테스트 통과
- 기존 `npm audit` 경고는 계속 출력됨: moderate 1건, high 1건
- Gradle deprecation warning은 기존과 동일하게 출력됨

## 5. 남은 과제

1. OCR 재분석 API가 `ReceiptOcrCandidates`와 `ReceiptOcrPolicy`를 직접 반환하도록 서버 API를 통일할지 결정한다.
2. 정책 카드에 후보별 선택 이유나 원문 라인까지 표시하려면 OCR 후보 DTO 확장 항목과 함께 진행한다.

