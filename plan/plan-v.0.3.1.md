# 편한가계부 초기 데이터 파일 목록 처리 개발 기록 v.0.3.1

작성일: 2026-06-26

## 1. 변경 목표

- 초기 데이터 파일을 단일 고정 파일명이 아니라 목록 패턴으로 읽어 처리한다.
- 자산 파일은 `assets_*.xlsx`로 지정한다.
- 거래 파일은 `transactions_*.xlsx`로 지정한다.

## 2. 구현 내용

- 초기 데이터 리소스 파일명을 다음 규칙으로 변경했다.
  - `backend/src/main/resources/initial-data/assets_자산설정_초기거래.xlsx`
  - `backend/src/main/resources/initial-data/transactions_6월_가계부.xlsx`
- `DemoDataInitializer`가 `ResourcePatternResolver`로 아래 패턴 전체를 읽도록 수정했다.
  - `classpath*:initial-data/assets_*.xlsx`
  - `classpath*:initial-data/transactions_*.xlsx`
- 여러 파일이 매칭되면 파일명 오름차순으로 순차 처리한다.
- 같은 자산명이 여러 자산 파일에 중복으로 나오면 최초 1회만 생성한다.

## 3. 동작 정책

- DB가 비어 있을 때만 초기 데이터가 주입된다.
- 자산 파일은 먼저 모두 처리하고, 이후 거래 파일을 처리한다.
- 거래 파일에 자산 파일에 없는 자산명이 나오면 0원 자산으로 자동 생성한다.
- 자산 잔액은 자산 파일의 값을 기준으로 유지하며, 초기 거래 저장 시 잔액을 추가 증감하지 않는다.

## 4. 검증 예정

- 백엔드 테스트
- 백엔드 Docker 이미지 빌드
