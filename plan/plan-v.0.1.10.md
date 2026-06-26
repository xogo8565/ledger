# 편한가계부 신규 개발 진행 기록 v.0.1.10

작성일: 2026-06-26

## 1. 기준

- 이전 기록: `plan/plan-v.0.1.9.md`
- 이번 개발 범위: v0.1.10 권장 내용까지만 진행하고 v0.1 잔여 계획 파기
- 사용자 요청: `plan-v.0.1.* 후속 개발 진행( v.0.1.10 권장 내용까지만 진행하고 남은 계획 파기)`

## 2. 이번 버전 개발 완료

### 백엔드 OCR 후보 DTO 추가

- `ReceiptOcrPreview` 응답에 `ReceiptOcrCandidates` DTO를 추가했다.
- 후보 DTO 구성:
  - `dateCandidates`
  - `titleCandidates`
  - `amountCandidates`
- OCR 최초 분석 응답에서 날짜/제목/금액 후보를 서버가 함께 내려준다.

### 후보 추출 로직 서버 이전

- 날짜 후보:
  - `YYYY-MM-DD`
  - `YYYY.MM.DD`
  - `YYYY/MM/DD`
  - `MM/DD`
  - `MM월 DD일`
  - 연도 없는 날짜는 현재 연도 기준으로 정규화한다.
- 금액 후보:
  - 파싱된 금액을 우선 후보로 포함한다.
  - OCR 원문에서 금액 형태를 추가 추출한다.
  - 날짜 문자열에서 연도 숫자가 금액 후보로 잡히지 않도록 날짜 영역을 제외한다.
- 제목/품명 후보:
  - 파싱된 가맹점/품명을 우선 후보로 포함한다.
  - `품명/단가/수량/금액` 표의 품목명을 후보로 포함한다.
  - 날짜, 금액, 합계, 사업자, 전화번호 등 노이즈 라인을 제외한다.

### 프론트 후보 선택 UI 연동 방식 정리

- OCR 결과 화면은 서버가 내려준 `candidates`를 우선 사용한다.
- 서버 후보가 없는 경우에만 기존 프론트 추출 로직을 fallback으로 사용한다.
- OCR 원문 재분석은 기존 텍스트 파싱 API를 사용하므로 후보 DTO가 없다. 이 경우 stale 후보를 쓰지 않도록 `candidates`를 비우고 fallback 후보를 사용한다.

### 실제 이미지 OCR fixture 판단

- 현재 조건부 실제 이미지 OCR 테스트(`ReceiptOcrServiceImageTest`)는 유지한다.
- Tesseract 설치 여부와 폰트/렌더링 환경에 따라 결과가 달라질 수 있으므로, 고정 fixture 안정화는 v0.1 범위에서 추가 진행하지 않는다.
- v0.1 라인은 OCR 자동입력 핵심 흐름과 후보 선택 UI까지 완료한 것으로 종결한다.

## 3. v.0.1.9 대비 상태 변경

| 항목 | v.0.1.9 상태 | v.0.1.10 상태 | 메모 |
| --- | --- | --- | --- |
| OCR 후보 DTO | 없음 | 추가 | `ReceiptOcrCandidates` |
| 후보 추출 위치 | 프론트 임시 로직 | 백엔드 우선, 프론트 fallback | 최초 OCR 분석 기준 |
| 날짜 후보 | 프론트 추출 | 백엔드 DTO 우선 | 재분석은 fallback |
| 금액 후보 | 프론트 추출 | 백엔드 DTO 우선 | 날짜 숫자 오인 방지 |
| 제목/품명 후보 | 프론트 추출 | 백엔드 DTO 우선 | 품목표 기반 |
| 실제 이미지 fixture | 조건부 테스트 있음 | 유지, 추가 안정화 중단 | v0.1 종료 |

## 4. 파기하는 v0.1 잔여 계획

아래 항목은 v0.1 라인에서 더 이상 진행하지 않는다.

- 실제 이미지 OCR fixture 추가 안정화
- OCR 성능/시간 초과 모니터링
- 영수증 품목별 상세 저장 모델 검토
- OCR 후보 선택 이력 기반 추천 개선
- OCR 후보 DTO의 추가 확장

필요 시 별도 신규 버전 라인에서 다시 계획한다.

## 5. v0.1 라인 종료 기준

v0.1 라인은 다음 기능을 완료한 상태로 종결한다.

- Tesseract OCR 연동
- 영수증 업로드 진입점
- OCR 결과 거래 입력 초안 반영
- OCR 원문 수정 후 재분석
- `품명/단가/수량/금액` 품목표 우선 파싱
- 여러 품목 메모 요약
- OCR 실패/낮은 신뢰도 안내
- OCR 임시 파일 정리
- 날짜/제목/금액 후보 선택 UI
- 백엔드 OCR 후보 DTO

## 6. 검증

- `docker run --rm -v "${PWD}\backend:/workspace" -w /workspace gradle:8.10.2-jdk21 gradle test --no-daemon` 통과
- `docker compose build backend` 통과
- `docker compose build frontend` 통과
- `git diff --check` 통과
- `powershell -ExecutionPolicy Bypass -File .\scripts\browser-smoke.ps1` 통과

## 7. 개발 메모

- 후보 DTO를 추가했지만 기존 프론트 fallback을 유지했다. 이는 재분석 API가 아직 후보 DTO를 반환하지 않기 때문이다.
- v0.1 이후 OCR 고도화는 신규 라인에서 서버 후보 구조를 더 명확하게 확장하는 방식이 적절하다.
