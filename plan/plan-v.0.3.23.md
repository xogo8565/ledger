# 편한가계부 초기 데이터 주입 이력 조회 API 개발 기록 v.0.3.23

작성일: 2026-07-01

## 1. 기준

- 이전 기록: `plan/plan-v.0.3.22.md`
- 이번 개발 범위: P2 `초기 데이터 운영 관리 개선` 중 주입 이력 조회 API
- 기준 문서: `plan/new-development-items.md`

## 2. 구현 내용

### 초기 데이터 주입 이력 DTO 추가

- 신규 DTO:
  - `InitialDataDtos.InitialDataImportDto`
- 응답 필드:
  - `id`
  - `resourceKind`
  - `resourceName`
  - `checksum`
  - `importedAt`
  - `rowCount`

### 주입 이력 조회 서비스 추가

- 신규 서비스:
  - `InitialDataImportService`
- `initial_data_imports` 테이블의 이력을 최신 주입 시각 순으로 반환한다.

### 관리 API 추가

- 신규 API:
  - `GET /api/initial-data/imports`
- 용도:
  - 서버에 새 `initial-data/*.xlsx`를 반영한 뒤 어떤 파일이 마지막으로 주입됐는지 확인한다.
  - checksum 변경 감지 결과와 row 수를 운영자가 확인한다.

### 문서 갱신

- README에 초기 데이터 주입 이력 조회 API 사용 예시를 추가했다.
- 신규 개발항목 정리 문서의 초기 데이터 운영 관리 상태를 갱신했다.

## 3. 변경 파일

- `backend/src/main/java/com/comfortableledger/ledger/domain/InitialDataImport.java`
- `backend/src/main/java/com/comfortableledger/ledger/dto/InitialDataDtos.java`
- `backend/src/main/java/com/comfortableledger/ledger/repository/InitialDataImportRepository.java`
- `backend/src/main/java/com/comfortableledger/ledger/service/importing/InitialDataImportService.java`
- `backend/src/main/java/com/comfortableledger/ledger/controller/LedgerController.java`
- `README.md`
- `plan/new-development-items.md`

## 4. 검증

```text
docker run --rm -v "${PWD}\backend:/workspace" -w /workspace gradle:8.10.2-jdk21 gradle test --no-daemon
git diff --check
```

결과:

- 백엔드 Gradle 테스트 통과
- `git diff --check` 오류 없음. 단, Windows CRLF 변환 경고만 출력됨
- Gradle deprecation warning은 기존과 동일하게 출력됨

## 5. 남은 과제

1. 운영 중 초기 데이터 파일을 외부 경로에서 읽는 설정을 추가할지 결정한다.
2. 수동 재주입 API가 필요한지 결정한다. 현재는 백엔드 재시작 시 checksum 변경 감지로 재주입한다.
3. 잘못 주입된 운영 DB 데이터를 되돌리는 절차를 별도 운영 문서로 정리한다.
4. skip row, upsert row, insert row를 더 세분화해 저장하려면 이력 테이블 확장 여부를 검토한다.

