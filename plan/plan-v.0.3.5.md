# 편한가계부 다건 거래 텍스트 파싱 개발 기록 v.0.3.5

작성일: 2026-06-29

## 1. 개발 목표

- 사용자가 월/일 날짜 헤더 아래에 여러 거래를 붙여넣는 형식을 파싱할 수 있도록 한다.
- 예시 형식:

```text
6월 28일 일요일
-25,950원 | 주식회사 원플러스마트서부 | 삼성카드 taptap O
+21원 | 예금이자 → 내 NH농협계좌
```

- 기존 단건 문자/OCR 자동 입력 API 응답은 유지하면서, 다건 거래 후보 목록을 함께 내려준다.

## 2. 구현 내용

### DTO 확장

- `TextImportPreview`에 `items` 필드를 추가했다.
- 기존 생성자는 유지해 기존 OCR/문자 파싱 테스트와 응답 필드 호환성을 보존했다.
- 다건 후보용 `TextImportItem` DTO를 추가했다.

`TextImportItem` 주요 필드:

- 원문 라인
- 거래 유형
- 거래일
- 금액
- 가맹점/제목
- 자산명
- 메모
- 추천 카테고리 정보

### 다건 파싱 규칙

- `6월 28일 일요일` 같은 날짜 헤더를 인식한다.
- 날짜 헤더 이후의 거래 라인에는 해당 날짜를 적용한다.
- 거래 라인은 `|` 기준으로 다음 순서로 해석한다.
  - 금액
  - 가맹점/제목
  - 자산명
  - 나머지 컬럼은 메모
- `+금액`은 수입으로 처리한다.
- `-금액`은 기본 지출로 처리한다.
- `취소`, `환불`, `승인취소`가 포함된 라인은 수입 후보로 처리한다.
- `→`가 포함된 내부 계좌 이동성 문구는 이체 후보로 처리한다.
- 해외 결제 등 추가 컬럼은 메모에 보존한다.

### 기존 단건 파싱 보정

- 시간 문자열 `12:34`의 `12`가 금액으로 오인되지 않도록 단건 금액 추출에서 시간 패턴을 먼저 제거한다.
- 단건 금액 패턴은 3자리 이상 금액 중심으로 유지하고, `+21원` 같은 소액은 다건 라인의 명시적 금액 패턴에서 처리한다.

## 3. 적용 파일

- `backend/src/main/java/com/comfortableledger/ledger/dto/ImportDtos.java`
- `backend/src/main/java/com/comfortableledger/ledger/service/importing/ImportTextService.java`
- `backend/src/test/java/com/comfortableledger/ledger/service/importing/ImportTextServiceBasicTest.java`
- `README.md`

## 4. 검증

```powershell
docker run --rm -v "${PWD}\backend:/workspace" -w /workspace gradle:8.10.2-jdk21 gradle test --no-daemon
docker compose build backend
```

결과:

- 백엔드 Gradle 테스트 통과
- 백엔드 Docker 이미지 빌드 통과
- 날짜 헤더 기반 4건 거래 파싱 테스트 추가 및 통과

## 5. 남은 과제

1. 프론트엔드에서 `items` 목록을 확인하고 여러 거래를 일괄 등록하는 화면을 추가한다.
2. 자산명 문자열을 실제 자산 ID로 매핑하는 백엔드/프론트 정책을 정한다.
3. 카드 결제 출금, 보험료, 내부 계좌 이체 등 `→` 문구의 거래 유형 판정 규칙을 실제 자산 데이터와 연결해 고도화한다.
