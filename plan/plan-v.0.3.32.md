# plan-v.0.3.32

작성일: 2026-07-01

## 작업 범위

- 프론트 OCR 후보 카드에서 서버 `candidateDetails`를 활용하도록 보강했다.
- 후보별 점수, 선택 이유, 원문 라인을 표시해 사용자가 후보를 고르는 근거를 확인할 수 있게 했다.

## 변경 내용

- `frontend/src/screens/TransactionScreens.jsx`
  - `candidateDetails`를 field/value 기준 Map으로 변환한다.
  - 날짜/내용/금액 후보 칩에 상세 메타데이터를 연결한다.
  - 후보 칩 내부에 점수, 선택 이유, 원문 라인을 표시한다.
  - 서버 상세 DTO가 없으면 기존 후보 칩 표시로 fallback한다.
- `frontend/src/styles.css`
  - 후보 칩을 다중 라인 레이아웃으로 조정했다.
  - 원문 라인은 긴 경우 말줄임 처리한다.
  - active 후보에서도 상세 텍스트가 읽히도록 색상을 조정했다.
- `README.md`, `plan/new-development-items.md`
  - OCR 후보 상세 DTO 프론트 표시 상태를 최신화했다.

## 남은 작업

- `candidateDetails`를 기준으로 기존 프론트 후보 추출 fallback을 더 줄일지 결정한다.
- 후보 상세 표시가 모바일 실기기에서 과밀하지 않은지 브라우저 스모크 또는 수동 확인한다.
