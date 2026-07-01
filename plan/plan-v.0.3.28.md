# plan-v.0.3.28

작성일: 2026-07-01

## 작업 범위

- `P3. 영수증 품목별 상세 저장 모델 검토` 항목을 진행했다.
- 현재 OCR 품목 처리 방식과 영수증 첨부 도메인을 확인하고, 품목별 상세 저장 모델의 권장 설계안을 정리했다.

## 현재 구조

- `ReceiptAttachment`
  - 거래(`TransactionRecord`) 1건에 여러 영수증 이미지 파일을 연결한다.
  - 파일명, 저장 경로, content type, 크기, 생성 시각만 저장한다.
- `ImportTextService`
  - OCR 원문에서 품목표를 감지한다.
  - 내부 임시 모델 `ReceiptLineItem(name, amount)`로 품목명과 금액만 추출한다.
  - 품목 상세는 별도 저장하지 않고 거래 메모에 `품목: ...` 요약으로 남긴다.

## 권장 저장 모델

신규 테이블: `receipt_items`

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| `id` | bigint | PK |
| `transaction_id` | bigint | 거래 FK, 필수 |
| `receipt_attachment_id` | bigint nullable | 원본 영수증 이미지 FK, OCR 원본 추적용 |
| `line_order` | int | 영수증 내 표시 순서 |
| `name` | varchar | 품목명 |
| `quantity` | decimal nullable | 수량 |
| `unit_price` | decimal nullable | 단가 |
| `amount` | decimal | 금액 |
| `source` | varchar | `OCR`, `MANUAL` |
| `raw_line` | text nullable | OCR 원문 라인 |
| `created_at` | datetime | 생성 시각 |
| `updated_at` | datetime | 수정 시각 |

## 관계 설계

- 우선 권장: `TransactionRecord 1:N ReceiptItem`
  - 거래 상세 화면에서 품목 목록을 바로 조회하기 쉽다.
  - 영수증 파일이 여러 장이어도 같은 거래의 품목으로 합산해 다룰 수 있다.
  - 영수증 이미지 삭제 시 품목까지 삭제할지 여부를 별도 정책으로 둘 수 있다.
- 보조 관계: `ReceiptAttachment 1:N ReceiptItem`
  - OCR 원본 이미지를 추적하기 위해 nullable FK로 둔다.
  - 수동 입력 품목이나 기존 거래에 나중에 추가한 품목은 attachment 없이 저장할 수 있다.

## API 설계안

- `GET /api/transactions/{transactionId}/receipt-items`
  - 거래별 품목 목록 조회
- `PUT /api/transactions/{transactionId}/receipt-items`
  - 품목 목록 전체 교체 저장
  - OCR 후보 검토 화면에서 수정 후 확정하는 흐름에 적합
- OCR preview 응답 확장
  - 최초에는 저장하지 않고 `itemCandidates`로 품목 후보를 내려준다.
  - 사용자가 거래 저장 또는 OCR 검토 완료 시 확정 품목으로 저장한다.

## UI 영향

- 기존 거래 메모 요약은 유지한다.
  - 과거 데이터와 CSV/검색 흐름이 깨지지 않는다.
  - 품목 저장이 실패해도 최소한 기존 메모 기반 사용성은 유지된다.
- 거래 상세 화면에 `영수증 품목` 섹션을 추가한다.
  - 품목명, 수량, 단가, 금액 수정 가능
  - 총 품목 금액과 거래 금액 차이를 표시한다.
- OCR 결과 화면에는 품목 후보 목록을 검토/삭제/수정할 수 있는 UI가 필요하다.

## 구현 순서 제안

1. `ReceiptItem` 엔티티, repository, DTO 추가
2. OCR 품목 파서 결과를 `ReceiptOcrPreview.itemCandidates`로 노출
3. 거래 저장/수정 시 품목 후보를 함께 저장하는 API 확장
4. 거래 상세 화면에 품목 조회/수정 UI 추가
5. CSV 내보내기 또는 통계에 품목을 포함할지 별도 정책 결정

## 결정 사항

- 저장 모델은 거래 기준 1:N을 우선으로 설계한다.
- `receipt_attachment_id`는 OCR 추적을 위한 nullable 보조 FK로 둔다.
- 기존 메모 요약 방식은 당장 제거하지 않는다.

## 남은 작업

- 실제 `ReceiptItem` 엔티티와 API 구현
- OCR 품목 후보 DTO 추가
- 품목 검토/수정 UI 구현
