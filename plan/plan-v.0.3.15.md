# 편한가계부 plan 카드 자산 주입 정책 개발 기록 v.0.3.15

## 개발 목표

- 전체 카드 등록 기본값은 변경하지 않는다.
- `assets_plan_20260629.xlsx`로 등록되는 카드 자산에만 다음 정책을 적용한다.
  - 카드 자산 금액: `0`
  - 확정일/결제일: 엑셀 컬럼 값

## 변경 사항

### plan 자산 파일 한정 정책

- 대상 파일:
  - `backend/src/main/resources/initial-data/assets_plan_20260629.xlsx`
- 해당 파일에서 `AssetType.CARD`로 판정되는 자산에만 금액을 `0`으로 저장한다.
- 다른 `assets_*.xlsx` 파일이나 화면에서 수동 등록하는 카드 자산에는 적용하지 않는다.

### 카드 프로필 생성/갱신

- `assets_plan_20260629.xlsx` 카드 자산 주입 시 `CardProfile`을 생성한다.
- 이미 카드 프로필이 있으면 엑셀 컬럼의 확정일/결제일 값으로 갱신한다.
- 결제계좌는 이미지/엑셀에서 특정할 수 없으므로 `null`로 둔다.
- 자동결제 값은 신규 생성 시 `false`, 기존 프로필은 기존 값을 유지한다.

### 엑셀 원본 정리

- `assets_plan_20260629.xlsx` 안의 카드 행 금액/잔액도 `0`으로 수정했다.
- 코드에서도 해당 파일의 카드 자산은 금액 `0`으로 강제하므로, 파일 값이 달라져도 동일 정책이 유지된다.

## 검증

- `InitialDataWorkbookReaderTest`에 plan 카드 행 금액 `0` 검증을 추가했다.
- 백엔드 전체 테스트 통과.

```text
docker run --rm -v "${PWD}\backend:/workspace" -w /workspace gradle:8.10.2-jdk21 gradle test --no-daemon
BUILD SUCCESSFUL
```

## 운영 참고

- 이전 변경 감지 정책에 따라 `assets_plan_20260629.xlsx` checksum이 바뀌었으므로 서버 배포 후 백엔드 재시작 시 재주입 대상이 된다.
- 기존 DB에 같은 이름의 카드 자산이 있으면 금액 0, 엑셀 컬럼 기준 확정일/결제일로 갱신된다.
