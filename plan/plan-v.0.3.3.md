# 편한가계부 프론트엔드 공통 유틸 분리 개발 기록 v.0.3.3

작성일: 2026-06-26

## 1. 개발 목표

- 백엔드와 동일하게 프론트엔드에서도 문자열, 숫자, 암호/토큰성 값 utility 처리를 `utils/*`로 분리한다.
- 화면/훅에 흩어진 숫자 변환, 원화 파싱, 공백 정규화, 후보 중복 제거 기준을 공통화한다.

## 2. 구현 내용

- `frontend/src/utils/stringValues.js` 추가
  - `emptyIfNull`
  - `trimToEmpty`
  - `normalizeWhitespace`
  - `normalizeSearchKey`
  - `firstNonBlank`
  - `containsAny`
  - `uniqueNonBlank`
  - `sortedByLengthDesc`
- `frontend/src/utils/numberValues.js` 추가
  - `toNumber`
  - `toPositiveNumber`
  - `parseDecimal`
  - `parseWonAmount`
  - `formatNumber`
  - `formatWon`
  - `percent`
- `frontend/src/utils/secretValues.js` 추가
  - `maskSecret`
  - `maskAll`
- `frontend/package-lock.json` 생성
  - CI/Docker 빌드 의존성 재현성 확보
- `frontend/.dockerignore` 추가
  - `node_modules`, `dist`가 Docker build context에 포함되지 않도록 제외

## 3. 적용 범위

- `format.js`
  - 금액/숫자 포맷을 `numberValues` 기반으로 전환
- `TransactionScreens.jsx`
  - OCR 후보 금액 파싱
  - OCR 원문 blank 검사
  - 후보 목록 중복 제거
  - 공백 정규화
- `useManagementMutations.js`
  - 자산/예산 저장 숫자 변환
  - 명의명 공백 정규화
- `useLedgerData.js`
  - 검색 금액 비교
  - 검색어 trim
  - 월 기반 연/월 숫자 변환
- `useScheduleMutations.js`
  - 반복 거래/카드 결제 숫자 변환
- `useTransactionMutations.js`
  - 거래 저장/이체 수수료/영수증 대상 회차 숫자 변환

## 4. 후속 권장

1. `main.jsx`, `LedgerScreen.jsx`, `StatsScreen.jsx`의 남은 `Number(...)` 계산도 화면별로 단계적 전환한다.
2. 민감값이 UI/로그에 노출되는 신규 기능은 `secretValues`를 사용하도록 리뷰 기준을 둔다.
3. 프론트엔드 테스트 환경이 필요하면 Vitest 추가 후 util 단위 테스트를 작성한다.

## 5. 검증

```powershell
docker run --rm -v "${PWD}\frontend:/app" -w /app node:22-alpine sh -c "npm install && npm run build"
docker compose build frontend
git diff --check
```

결과:

- 프론트엔드 Vite 빌드 성공
- 프론트엔드 Docker 이미지 빌드 성공
- `git diff --check` 오류 없음. 단, Windows CRLF 경고만 출력됨
- `npm audit`에서 기존 의존성 기준 moderate/high 경고가 각 1건 출력됨
