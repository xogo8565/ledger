# plan-v.0.3.27

작성일: 2026-07-01

## 작업 범위

- `P3. OCR 운영 성능/시간 초과 모니터링` 항목을 진행했다.
- OCR 요청의 처리 시간과 실패 상태를 운영 로그로 확인할 수 있도록 보강했다.

## 변경 내용

- `ReceiptOcrService`
  - OCR 요청 시작 시각을 기록하고 성공/실패/timeout/interrupted 상태를 로그로 남긴다.
  - 성공 로그에 전체 처리 시간, Tesseract 실행 시간, OCR 원문 길이, 경고 개수, 날짜/제목/금액 후보 개수를 포함한다.
  - 실패 로그에 상태, 처리 시간, 예외 타입, 메시지를 포함한다.
  - timeout 예외를 별도 타입으로 분리해 로그 status를 `timeout`으로 남긴다.
  - timeout 사용자 안내 메시지를 “30초 초과, 재촬영 또는 직접 입력” 기준으로 정리했다.

## 로그 포맷

성공:

```text
receipt_ocr status=success file="..." sizeBytes=... durationMs=... tesseractMs=... rawTextLength=... warnings=... dateCandidates=... titleCandidates=... amountCandidates=...
```

실패/timeout:

```text
receipt_ocr status=failed|timeout|interrupted file="..." sizeBytes=... durationMs=... errorType=... message="..."
```

OCR 원문 텍스트는 개인정보 가능성이 있어 로그에 남기지 않는다.

## 검증

```powershell
docker compose run --rm backend ./gradlew test
git diff --check
```

## 남은 작업

- 로그 수집 환경에서 `status=timeout`, `status=failed` 기준 집계 대시보드 또는 알림을 구성할지 결정한다.
- timeout 기준 30초를 환경 변수로 분리할지 검토한다.
