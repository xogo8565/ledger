# 편한가계부 공통 유틸 분리 개발 기록 v.0.3.2

작성일: 2026-06-26

## 1. 개발 목표

- 문자열, 숫자, 암호/토큰성 값처럼 여러 계층에서 반복될 수 있는 utility 성 처리를 `util/*` 클래스로 분리한다.
- 서비스 내부 private helper에 흩어진 공백 정규화, 금액 파싱, 민감값 마스킹 기준을 공통화한다.

## 2. 구현 내용

- `com.comfortableledger.ledger.util.StringValues` 추가
  - `emptyIfNull`
  - `trimToEmpty`
  - `normalizeWhitespace`
  - `normalizeSearchKey`
  - `firstNonBlank`
  - `containsAny`
  - `sortedByLengthDesc`
- `com.comfortableledger.ledger.util.NumberValues` 추가
  - `zeroIfNull`
  - `isPositive`
  - `parseDecimal`
  - `parseWonAmount`
  - `percent`
- `com.comfortableledger.ledger.util.SecretValues` 추가
  - `mask`
  - `maskAll`
- 적용 대상:
  - 초기 데이터 시딩 금액 파싱/first non blank 처리
  - 명의/소유자명 공백 정규화
  - 문자 자동입력/OCR 금액 파싱

## 3. 테스트

- `StringValuesTest`
- `NumberValuesTest`
- `SecretValuesTest`

## 4. 후속 권장

1. OCR/문자 파싱 내 `containsAny`, 검색 키 정규화 로직도 단계적으로 `StringValues`로 이전한다.
2. 통계/예산 계산의 비율 계산 로직은 `NumberValues.percent`로 추가 전환한다.
3. 운영 로그에 토큰/키/비밀번호가 추가되는 지점은 `SecretValues`를 강제 사용하도록 리뷰 기준을 둔다.
