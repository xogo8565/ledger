# 편한가계부 신규 개발 진행 기록 v.0.1.7

작성일: 2026-06-26

## 1. 기준

- 이전 기록: `plan/plan-v.0.1.6.md`
- 이번 개발 범위: 영수증 OCR 임시 파일 정리 정책 보강
- 배경: OCR 요청 처리 중 생성되는 임시 파일은 정상 요청 흐름에서는 `finally`에서 삭제된다. 다만 서버 프로세스 강제 종료, 컨테이너 재시작, OS 레벨 오류가 겹치면 `receipt-ocr-*` 임시 파일이 남을 수 있다.

## 2. 이번 버전 개발 완료

### OCR 임시 파일 명명 정책

- OCR 업로드 임시 파일 prefix를 `receipt-ocr-` 상수로 분리했다.
- 기존 `Files.createTempFile(...)` 기반 임시 파일 생성 방식은 유지한다.
- 요청 처리 완료 시 `finally`에서 임시 파일을 삭제하는 기존 정책도 유지한다.

### 서버 시작 시 stale OCR 임시 파일 정리

- `ReceiptOcrService`에 서버 시작 시 OCR 임시 파일 정리 로직을 추가했다.
- Java temp directory에서 `receipt-ocr-*` prefix를 가진 일반 파일만 대상으로 한다.
- 마지막 수정 시간이 24시간보다 오래된 파일만 삭제한다.
- 정리 실패가 애플리케이션 기동을 막지 않도록 startup cleanup 예외는 무시한다.

### 테스트 추가

- `ReceiptOcrServiceTest`에 stale temp cleanup 테스트를 추가했다.
- 검증 항목:
  - 오래된 `receipt-ocr-*` 파일은 삭제한다.
  - 최근 `receipt-ocr-*` 파일은 유지한다.
  - prefix가 다른 오래된 파일은 삭제하지 않는다.

## 3. v.0.1.6 대비 상태 변경

| 항목 | v.0.1.6 상태 | v.0.1.7 상태 | 메모 |
| --- | --- | --- | --- |
| 요청 중 OCR 임시 파일 | finally 삭제 | 유지 | 정상 요청 흐름 동일 |
| 비정상 종료 잔여 파일 | 별도 정리 없음 | 서버 시작 시 24시간 초과 파일 정리 | `receipt-ocr-*` 대상 |
| 정리 실패 처리 | 해당 없음 | 앱 기동 방해 없음 | 예외 무시 |
| 테스트 | 품질 경고 중심 | 임시 파일 정리 테스트 추가 | prefix/mtime 기준 |

## 4. 아직 부분 완료인 항목

### 검증 상태

- Docker Desktop이 수동 일시정지 상태라 Docker 기반 테스트/빌드를 실행하지 못했다.
- 로컬에는 Gradle wrapper가 없고 `npm` 명령도 PATH에 없어 대체 빌드도 제한됐다.
- `git diff --check`는 통과했다.

Docker Desktop 재개 후 아래 검증이 필요하다.

```powershell
docker run --rm -v "${PWD}\backend:/workspace" -w /workspace gradle:8.10.2-jdk21 gradle test --no-daemon
docker compose build backend
docker compose build frontend
```

### OCR 후보 선택 UX

- OCR 결과 후보 다중 선택 UI는 아직 없다.
- 품명/금액/가맹점 후보가 여러 개일 때 사용자가 선택하는 흐름은 다음 단계다.

## 5. 다음 개발 우선순위

### v.0.1.8 권장

1. Docker 재개 후 v0.1.6~v0.1.7 테스트/빌드 검증
2. OCR 결과 후보 다중 선택 UI 설계 및 1차 구현
3. 실제 이미지 OCR fixture 안정화

### 이후 권장

1. OCR 성능/시간 초과 모니터링
2. 영수증 품목별 상세 저장 모델 검토
3. OCR 후보 선택 이력 기반 추천 개선

## 6. 검증

- `git diff --check` 통과
- Docker 기반 테스트/빌드: Docker Desktop 수동 일시정지로 미실행
- 로컬 Gradle/NPM 대체 검증: Gradle wrapper 없음, `npm` 명령 없음으로 미실행

## 7. 개발 메모

- OCR 임시 파일은 운영 데이터가 아니므로 서버 재시작 시 stale 파일을 안전하게 정리해도 된다.
- 다만 최근 파일은 진행 중 요청 또는 직전 실패 요청일 수 있어 24시간 보존 기준을 둔다.
- 업로드 원본 영수증 첨부 파일은 `data/app/uploads`에 저장되는 운영 데이터이므로 이번 cleanup 대상에 포함하지 않는다.
