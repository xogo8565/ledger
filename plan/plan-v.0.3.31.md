# plan-v.0.3.31

작성일: 2026-07-01

## 작업 범위

- OCR 원문 재분석 흐름에도 후보 DTO와 정책 DTO를 반환하도록 확장했다.
- 기존 일반 문자 파싱 API와 OCR 재분석 API를 분리했다.

## 변경 내용

- `ReceiptOcrService`
  - OCR 원문 문자열을 받아 `ReceiptOcrPreview`를 생성하는 `reparse` 메서드를 추가했다.
  - 최초 이미지 OCR과 재분석이 같은 후보/정책 생성 로직을 사용하도록 `previewFromText`로 공통화했다.
  - 재분석 로그 `receipt_ocr_reparse`를 추가해 처리 시간, 원문 길이, 경고/후보 개수를 남긴다.
- `ReceiptOcrController`
  - `POST /api/receipts/ocr/reparse`를 추가했다.
  - 요청 본문은 기존 `TextImportRequest(rawText)`를 재사용한다.
- `frontend/src/api/ledgerApi.js`
  - `reparseReceiptOcr(rawText)`를 추가했다.
- `ReceiptOcrScreen`
  - 수정한 OCR 원문 재분석 시 일반 문자 파싱 API 대신 OCR 재분석 API를 호출한다.
  - 재분석 결과에서도 서버 `candidates`, `candidateDetails`, `policy`를 그대로 사용할 수 있다.
- `ReceiptOcrServiceTest`
  - 재분석 결과에 후보와 정책이 포함되는지 검증한다.

## 호환성

- `POST /api/import/text/parse`는 카드/은행 문자 및 거래 목록 붙여넣기용으로 유지한다.
- OCR 화면의 원문 재분석만 `POST /api/receipts/ocr/reparse`로 이동했다.
- 기존 OCR preview 응답 구조는 유지된다.

## 남은 작업

- 프론트 후보 카드에서 `candidateDetails.sourceLine`, `reason`, `score`를 직접 표시할지 결정한다.
- OCR 재분석 전체 브라우저 스모크 시나리오를 추가할지 검토한다.
