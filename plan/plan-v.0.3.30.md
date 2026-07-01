# plan-v.0.3.30

작성일: 2026-07-01

## 작업 범위

- `P3. OCR 후보 DTO 추가 확장` 항목을 진행했다.
- 기존 OCR 후보 응답 호환을 유지하면서 후보별 상세 메타데이터를 추가했다.

## 변경 내용

- `ReceiptDtos`
  - `ReceiptOcrCandidateDetail` record를 추가했다.
  - `ReceiptOcrCandidates`에 `candidateDetails` 필드를 추가했다.
  - 기존 `dateCandidates`, `titleCandidates`, `amountCandidates` 배열은 유지했다.
  - 3개 인자 생성자를 유지해 기존 서버 코드/테스트 호환성을 보장했다.
- `ReceiptOcrService`
  - 후보별 상세 DTO를 생성한다.
  - 상세 DTO에는 `field`, `value`, `score`, `sourceLine`, `reason`을 담는다.
  - 금액 후보는 거래 초안, 합계/결제금액 라벨, 품목표, 일반 원문 후보 순으로 점수를 부여한다.
  - 제목 후보는 거래 초안, 품목표 품명, 원문 가맹점 후보 순으로 점수를 부여한다.
  - 날짜 후보는 거래 초안 날짜와 OCR 원문 날짜 후보를 구분한다.
- `ReceiptOcrServiceTest`
  - 후보 상세 DTO의 금액/제목 메타데이터 검증을 추가했다.
- `README.md`, `plan/new-development-items.md`
  - OCR 후보 상세 DTO 제공 상태를 최신화했다.

## 응답 호환성

- 기존 클라이언트는 계속 `dateCandidates`, `titleCandidates`, `amountCandidates`를 사용할 수 있다.
- 신규 클라이언트는 `candidateDetails`를 사용해 후보 점수, 원문 라인, 선택 이유를 표시할 수 있다.

## 남은 작업

- OCR 원문 재분석 API도 후보 DTO와 정책 DTO를 반환하도록 확장할지 결정한다.
- 프론트 OCR 후보 카드가 `candidateDetails`를 직접 활용하도록 전환할지 검토한다.
- 프론트 fallback 후보 추출 로직을 축소할 수 있는지 확인한다.
