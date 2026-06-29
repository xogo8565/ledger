# 편한가계부 initial-data 변경 감지 재주입 개발 기록 v.0.3.14

## 개발 목표

- 서버 업로드/배포 후 `initial-data` 엑셀 파일 내용이 변경되면 자동으로 감지해 주입한다.
- 기존처럼 빈 DB에서만 1회 실행되는 구조를 제거한다.
- 재주입 시 기존 운영 데이터가 무분별하게 중복되지 않도록 한다.

## 변경 사항

### 파일 변경 감지 이력 테이블 추가

- 신규 도메인/테이블:
  - `InitialDataImport`
  - DB 테이블명: `initial_data_imports`
- 신규 Repository:
  - `InitialDataImportRepository`
- 관리 값:
  - 리소스 종류: `ASSET`, `TRANSACTION`
  - 파일명
  - SHA-256 checksum
  - 주입 시각
  - 읽은 row 수

### 실행 조건 변경

기존:

- `households`, `assets`, `categories`, `transactions`가 모두 비어 있을 때만 실행

변경:

- 앱 시작 시 항상 `initial-data/assets_*.xlsx`, `initial-data/transactions_*.xlsx` 파일을 검색
- 각 파일의 SHA-256 checksum을 계산
- 이전 이력과 checksum이 다르면 해당 파일만 다시 주입
- checksum이 같으면 skip

### 자산 재주입 정책

- 자산은 `householdId + name` 기준으로 upsert한다.
- 같은 이름의 자산이 있으면 다음 값을 갱신한다.
  - 자산 타입
  - 자산명
  - 잔액
  - 그룹명
  - 명의
- 같은 이름의 자산이 없으면 신규 생성한다.

### 거래 재주입 정책

- 거래는 다음 주요 필드가 동일하면 중복으로 보고 skip한다.
  - household
  - 거래일
  - 거래 유형
  - 금액
  - 자산
  - 제목
- 변경된 거래 파일을 다시 업로드해도 기존 동일 거래가 중복 생성되지 않는다.
- 신규 거래 행만 추가된다.

### 기존 DB 대응

- 기존 운영 DB에 `initial_data_imports` 이력이 없어도 앱 시작 시 파일을 한 번 평가한다.
- 자산은 upsert로 반영된다.
- 거래는 중복 skip 후 신규분만 주입된다.

## 변경 파일

- `backend/src/main/java/com/comfortableledger/ledger/domain/InitialDataImport.java`
- `backend/src/main/java/com/comfortableledger/ledger/repository/InitialDataImportRepository.java`
- `backend/src/main/java/com/comfortableledger/ledger/repository/AssetRepository.java`
- `backend/src/main/java/com/comfortableledger/ledger/repository/TransactionRepository.java`
- `backend/src/main/java/com/comfortableledger/ledger/service/importing/DemoDataInitializer.java`
- `README.md`

## 검증

```text
docker run --rm -v "${PWD}\backend:/workspace" -w /workspace gradle:8.10.2-jdk21 gradle test --no-daemon
BUILD SUCCESSFUL
```

## 운영 참고

- 서버에 새 `initial-data/*.xlsx`를 배포한 뒤 백엔드 컨테이너를 재시작하면 변경 감지가 실행된다.
- Docker Compose 기준:

```bash
docker compose up -d --build backend
docker compose logs backend --tail=100
```

- 파일명은 같고 내용만 바뀌어도 checksum이 달라지면 재주입된다.
- 파일명만 바뀌고 내용이 같아도 새 파일로 간주되어 평가된다. 이 경우 거래는 중복 skip 정책이 적용된다.
