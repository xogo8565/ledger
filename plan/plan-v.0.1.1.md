# 편한가계부 신규 개발 진행 기록 v.0.1.1

작성일: 2026-06-26

## 1. 기준

- 이전 계획: `plan/plan-v.0.1.0.md`
- 사용자 결정: OCR 엔진은 Tesseract로 확정
- 이번 개발 범위: Tesseract OCR API, 영수증 업로드 진입점, OCR 결과 거래 입력 초안 연결

## 2. 이번 버전 개발 완료

### Tesseract OCR 엔진 연동

- 백엔드 런타임 Docker 이미지에 `tesseract-ocr`, `tesseract-ocr-kor`, `tesseract-ocr-eng`를 설치하도록 변경했다.
- `app.ocr.tesseract-command`, `app.ocr.tesseract-language` 설정을 추가했다.
- 기본 OCR 언어는 `kor+eng`로 설정했다.
- 컨테이너 내부에서 `tesseract --list-langs`로 `eng`, `kor`, `osd` 언어 데이터가 보이는 것을 확인했다.

### 백엔드 OCR API

- `ReceiptOcrService`를 추가했다.
- 업로드된 이미지 파일을 임시 파일로 저장한 뒤 Tesseract CLI를 실행한다.
- OCR 실행 제한 시간은 30초로 두었다.
- OCR 원문을 기존 `ImportTextService.preview`에 전달해 거래 후보를 생성한다.
- `ReceiptOcrController`를 추가했다.
- 신규 API:
  - `POST /api/receipts/ocr`
  - multipart field: `file`
- `ApiDtos.ReceiptOcrPreview`를 추가했다.
  - 원본 파일명
  - OCR 원문 텍스트
  - 기존 문자 자동 입력 preview 결과
  - OCR 경고 목록

### 프론트엔드 OCR 업로드 UX

- 가계부 초기 화면에 `영수증 자동 입력` CTA를 추가했다.
- 더보기 메뉴의 `영수증 사진` 항목을 `영수증 업로드`로 교체하고 OCR 패널을 열도록 연결했다.
- `ReceiptOcrScreen`을 추가했다.
  - 이미지 파일 선택
  - OCR 분석 실행
  - 분석 결과 표시
  - OCR 원문 보기
  - 거래 입력 폼으로 반영
- OCR 결과를 거래 입력 폼 초안으로 채운다.
  - 유형
  - 날짜
  - 금액
  - 제목/가맹점
  - 메모/OCR 원문
  - 추천 카테고리
  - 기본 소비 명의
- OCR에 사용한 파일은 거래 저장 시 영수증 첨부 후보로 유지한다.

### 스모크 테스트 보강

- Playwright 브라우저 스모크에 더보기 메뉴의 영수증 업로드 패널 열림 확인을 추가했다.
- 가계부 초기 화면의 OCR CTA 렌더링 확인을 추가했다.

## 3. v.0.1.0 대비 상태 변경

| 항목 | v.0.1.0 상태 | v.0.1.1 상태 | 메모 |
| --- | --- | --- | --- |
| OCR 엔진 | 미정/검토 | Tesseract 확정 | Docker 이미지 포함 |
| OCR API | 계획 | 완료 | `POST /api/receipts/ocr` |
| OCR 결과 파싱 | 계획 | 기존 문자 파서 재사용 | `ImportTextService.preview` |
| 초기 화면 진입점 | 계획 | 완료 | 가계부 CTA |
| 햄버거/더보기 진입점 | 계획 | 완료 | 더보기 메뉴 연결 |
| 거래 입력 자동 채움 | 계획 | 완료 | OCR preview → entry form |
| 실제 OCR 회귀 이미지 | 미완료 | 미완료 | 다음 단계 |

## 4. 아직 부분 완료인 항목

### OCR 정확도 검증

- 실제 샘플 영수증 이미지 기반 회귀 테스트는 아직 없다.
- OCR 결과 정확도는 이미지 품질과 Tesseract 인식률에 따라 달라진다.

### OCR 파싱 고도화

- OCR 원문 파싱은 기존 문자 자동 입력 파서를 재사용한다.
- 영수증 특화 패턴인 합계, 승인번호, 사업자번호, 품목 라인 제거, 날짜 표기 다양화는 추가 보강이 필요하다.

### OCR 임시 파일 정책

- OCR 분석 중 생성되는 임시 파일은 요청 처리 후 삭제한다.
- 거래 저장 전 OCR 업로드 파일을 별도 임시 저장소에 보관하는 정책은 아직 없다.
- 현재는 프론트에서 OCR에 사용한 원본 `File` 객체를 거래 저장 시 첨부 후보로 유지한다.

## 5. 다음 개발 우선순위

### v.0.1.2 권장

1. 샘플 영수증 이미지 기반 OCR 회귀 테스트 추가
2. 영수증 특화 OCR 텍스트 파싱 고도화
3. OCR 실패/낮은 신뢰도 UX 보강
4. OCR 업로드 파일 임시 저장/정리 정책 설계

### 이후 권장

1. OCR 결과 후보 다중 선택 UI
2. 카테고리 추천 정확도 개선
3. OCR 성능/시간 초과 모니터링

## 6. 검증

- `docker run --rm -v "${PWD}\backend:/workspace" -w /workspace gradle:8.10.2-jdk21 gradle test --no-daemon` 통과
- `docker compose build frontend` 통과
- `docker compose build backend` 통과
- `docker compose up -d frontend backend`로 최신 이미지 재생성 완료
- `powershell -ExecutionPolicy Bypass -File .\scripts\browser-smoke.ps1` 통과
- `docker compose exec -T backend tesseract --list-langs`에서 `eng`, `kor`, `osd` 확인

## 7. 개발 메모

- Tesseract는 Java 라이브러리로 직접 묶지 않고 CLI 실행 방식으로 연동했다.
- 이 방식은 의존성 충돌이 적고 Docker 이미지에 OCR 엔진을 명확히 포함할 수 있다.
- OCR 원문은 민감정보일 수 있으므로 서버에 영구 저장하지 않고 응답으로만 전달한다.
- OCR 결과는 바로 거래로 저장하지 않고 사용자가 확인할 수 있는 거래 입력 초안으로만 반영한다.

