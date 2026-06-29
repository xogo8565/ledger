# 편한가계부 백엔드 API 표준 응답 래핑 개발 기록 v.0.3.18

## 개발 목표

- 백엔드 Controller의 JSON 응답을 `ApiResponse<T>`로 표준화한다.
- Controller 응답은 `ResponseEntity<ApiResponse<T>>`로 감싸 반환한다.
- 프론트엔드는 표준 응답을 자동 unwrap해 기존 화면 로직을 유지한다.

## 응답 구조

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

오류 응답:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "BAD_REQUEST",
    "message": "..."
  }
}
```

## 백엔드 변경

- 신규 DTO 추가:
  - `ApiResponse<T>`
- 신규 공통 응답 유틸 추가:
  - `controller.support.ApiResponses`
  - `ok(data)`, `ok()`, `error(status, code, message)` 제공
- 예외 처리 변경:
  - `ApiExceptionHandler`가 `ResponseEntity<ApiResponse<Void>>` 반환
  - `BAD_REQUEST`, `VALIDATION_ERROR` 코드 제공
- JSON API Controller 응답을 `ResponseEntity<ApiResponse<T>>`로 변경
  - `LedgerController`
  - `CardController`
  - `ImportController`
  - `ReceiptController`
  - `ReceiptOcrController`
  - `RecurringTransactionController`

## 예외

- 파일 다운로드/스트리밍 endpoint는 기존 `ResponseEntity<byte[]>`를 유지했다.
  - 거래 CSV 다운로드
  - 영수증 원본 파일 다운로드

이 endpoint까지 JSON으로 래핑하면 브라우저 다운로드와 이미지 미리보기가 깨지므로, media type이 `text/csv` 또는 binary인 응답은 원본 ResponseEntity를 유지한다.

## 프론트엔드 변경

- `frontend/src/api/http.js`
  - `requestJson`에서 `ApiResponse` 자동 unwrap
  - `requestResult`에서 성공 응답만 unwrap
  - 오류 메시지는 `error.message` 우선 사용
- 기존 화면/훅은 대부분 그대로 유지한다.

## 후속 정리

- Controller별로 중복 보유하던 `ResponseEntity.ok(ApiResponse...)` helper를 제거했다.
- 모든 JSON Controller는 `ApiResponses` 공통 메서드를 통해 응답을 생성한다.

## 검증

```text
docker run --rm -v "${PWD}\backend:/workspace" -w /workspace gradle:8.10.2-jdk21 gradle test --no-daemon
BUILD SUCCESSFUL
```

```text
docker run --rm -v "${PWD}\frontend:/workspace" -w /workspace node:22-alpine sh -c "npm ci && npm run build"
✓ built
```
