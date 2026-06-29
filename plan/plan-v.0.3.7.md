# 편한가계부 OCR 정책 고도화 개발 기록 v.0.3.7

작성일: 2026-06-29

## 1. 개발 목표

- 영수증 OCR 결과를 단순 텍스트 파싱에만 의존하지 않고, 후보 우선순위와 검토 정책을 명확히 한다.
- 금액 후보에서 사업자번호, 전화번호, 카드번호 같은 노이즈 숫자가 거래 금액으로 섞이는 문제를 줄인다.
- 프론트가 OCR 결과의 신뢰도를 판단할 수 있도록 정책 메타데이터를 응답에 포함한다.

## 2. 구현 내용

### OCR 응답 정책 메타데이터 추가

`ReceiptOcrPreview` 응답에 `policy`를 추가했다.

제공 필드:

- `confidenceScore`: 0~100 범위의 OCR 후보 신뢰도 점수
- `needsReview`: 사용자가 후보를 확인해야 하는지 여부
- `recognizedFields`: 인식된 필드 목록
  - `date`
  - `title`
  - `amount`
- `reviewReasons`: 검토 필요 사유 목록

기존 생성자는 유지해 기존 호출 호환성은 보존했다.

### Tesseract 실행 정책 보강

- Tesseract 실행 옵션을 명시했다.
  - `--psm 6`
  - `--oem 1`
  - `preserve_interword_spaces=1`
- 영수증처럼 줄 단위 텍스트가 많은 이미지에서 후보 추출 안정성을 높이는 방향이다.

### 금액 후보 우선순위 개선

금액 후보 추출 순서를 아래처럼 정리했다.

1. `합계`, `총액`, `결제금액`, `받을금액`, `매출금액`, `승인금액`, `total` 라벨의 금액
2. 기존 파서가 선택한 거래 금액
3. `품명/단가/수량/금액` 품목표의 행별 마지막 금액
4. 기타 일반 금액 후보

### 숫자 노이즈 제외

금액 후보 추출 전에 아래 숫자 패턴을 제거한다.

- 사업자번호
- 전화번호
- 카드 마스킹 번호
- 날짜
- 시간

### 제목/품명 후보 개선

- 기존 파서가 선택한 가맹점/품명을 우선 포함한다.
- `품명/단가/수량/금액` 표의 품목명을 후보로 포함한다.
- 사업자번호, 전화번호, 날짜, 금액, 합계, 승인, 카드, 부가세 등 노이즈 라인을 제외한다.

### 품질 경고 개선

아래 상황에 사용자 검토 경고를 반환한다.

- OCR 텍스트가 비어 있음
- OCR 텍스트가 지나치게 짧음
- 금액 후보 없음
- 가맹점/품명 후보 불확실
- 금액 후보가 과다함
- 합계/결제금액 라벨 없이 여러 금액이 존재함

## 3. 적용 파일

- `backend/src/main/java/com/comfortableledger/ledger/dto/ReceiptDtos.java`
- `backend/src/main/java/com/comfortableledger/ledger/service/receipt/ReceiptOcrService.java`
- `backend/src/test/java/com/comfortableledger/ledger/service/receipt/ReceiptOcrServiceTest.java`
- `README.md`

## 4. 검증

```powershell
docker run --rm -v "${PWD}\backend:/workspace" -w /workspace gradle:8.10.2-jdk21 gradle test --no-daemon
docker compose build backend
```

결과:

- 백엔드 Gradle 테스트 통과
- 백엔드 Docker 이미지 빌드 통과
- OCR 후보 정책 테스트 통과
  - 합계 금액 우선
  - 품목명 후보 추출
  - 품목별 금액 후보 추출
  - 사업자번호/전화번호 금액 후보 제외
  - 정책 메타데이터 생성

## 5. 후속 권장

1. 프론트 OCR 결과 화면에 `confidenceScore`, `needsReview`, `recognizedFields`를 표시한다.
2. 실제 영수증 샘플 이미지를 fixture로 추가해 Tesseract 결과 회귀 테스트를 안정화한다.
3. 품목표 헤더가 OCR에서 깨지는 경우를 대비해 `품목`, `상품명`, `단가`, `수량`, `금액` 유사어 사전을 확장한다.
