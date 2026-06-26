# 편한가계부 후속 개발 진행 기록 v.0.0.54

작성일: 2026-06-26

## 1. 기준

- 이전 기록: `plan/plan-v.0.0.53.md`
- 이번 개발 범위: 거래 검색 결과 페이지네이션 및 정렬 선택 연결
- 검증 방식: Gradle 전체 테스트, Docker Compose 이미지 빌드, Playwright 브라우저 스모크 테스트

## 2. 이번 버전 개발 완료

### 백엔드 검색 페이지 응답

- `TransactionSearchSort`를 추가해 검색 정렬 값을 `DATE_DESC`, `DATE_ASC`, `AMOUNT_DESC`, `AMOUNT_ASC`로 제한했다.
- `TransactionSearchCriteria`에 `page`, `size`, `sort`를 추가했다.
- 페이지 기본값은 `page=0`, `size=50`, `sort=DATE_DESC`이며, `size`는 최대 200개로 제한한다.
- `size < 1` 입력은 `IllegalArgumentException`으로 거부하도록 검증을 추가했다.
- `TransactionQueryService.searchTransactions`가 `PageRequest`와 Spring Data `Sort`를 사용해 검색 결과를 페이지 단위로 조회한다.
- `ApiDtos.TransactionSearchResultDto`를 추가해 검색 응답에 `items`, `page`, `size`, `totalElements`, `totalPages`, `sort`를 포함했다.
- `LedgerController /transactions/search`가 `page`, `size`, `sort` 요청 파라미터를 받도록 확장했다.

### 프론트엔드 검색 상태 및 UI

- `useLedgerData`가 검색 배열만 보관하던 구조에서 `searchResult` 페이지 메타까지 함께 보관하도록 확장했다.
- 기존 화면 흐름과의 호환을 위해 `searchTransactions`는 계속 `searchResult.items` 배열로 제공한다.
- 검색 응답이 과거 배열 형태로 들어와도 화면이 깨지지 않도록 fallback 정규화를 추가했다.
- `emptyLedgerFilters`에 `page`, `size`, `sort` 기본값을 추가했다.
- 필터 조건이나 정렬이 바뀌면 검색 페이지가 0쪽으로 초기화되도록 처리했다.
- 거래 필터 영역에 정렬 선택, 페이지 크기 선택, 이전/다음 페이지 이동 UI를 추가했다.
- 검색 결과 수는 현재 페이지 개수가 아니라 서버의 `totalElements`를 표시하도록 바꿨다.
- 검색 페이지 UI에 필요한 `.search-options-row`, `.search-page-row` 스타일을 추가했다.

### 검증 스크립트 최신화

- `scripts/browser-smoke.mjs`가 `/transactions/search`의 새 페이지 응답과 기존 배열 응답을 모두 처리하도록 `transactionSearchItems` 헬퍼를 추가했다.
- 브라우저 스모크 테스트의 고급 검색 검증이 페이지 응답 구조에서도 같은 거래를 확인할 수 있게 했다.

## 3. v.0.0.53 대비 상태 변경

| 항목 | v.0.0.53 상태 | v.0.0.54 상태 | 메모 |
| --- | --- | --- | --- |
| 검색 결과 페이지네이션 | 미완료 | 완료 | 서버 Page 응답, 프론트 이전/다음 연결 |
| 검색 정렬 선택 | 미완료 | 완료 | 최신순, 오래된순, 금액 높은순, 금액 낮은순 |
| 검색 응답 DTO | 목록 배열 | 페이지 메타 포함 객체 | `TransactionSearchResultDto` |
| 검색 조건 객체 | 필터 조건만 포함 | 페이지/크기/정렬 포함 | `TransactionSearchCriteria` 확장 |
| 프론트 검색 hook | 배열 상태 중심 | `searchResult` + 배열 호환 | 기존 화면 소비 구조 유지 |
| 브라우저 스모크 | 배열 응답 기대 | 배열/페이지 응답 모두 허용 | 새 API 계약 반영 |

## 4. 아직 부분 완료인 항목

### 검색 CSV 범위

- 검색 화면의 `결과 CSV`는 현재 화면에 로드된 페이지 기준으로 동작한다.
- 전체 검색 조건에 해당하는 모든 페이지를 CSV로 받을지, “현재 페이지 CSV”로 명확히 표기할지 정책 결정이 필요하다.

### mutation UX

- mutation 실행 중 중복 제출을 막는 프론트엔드 pending 상태가 아직 통일되어 있지 않다.
- 일시적인 네트워크 오류에 대한 재시도 동작과 화면 상단 오류 배너가 아직 미완료다.

### 테스트 보강

- 검색 페이지네이션과 정렬 조합에 대한 백엔드 통합 테스트가 아직 충분하지 않다.
- 영수증 첨부 파일 정리 정책 고도화는 아직 미완료다.

## 5. 다음 개발 우선순위

### v.0.0.55 권장

1. 검색 결과 CSV 내보내기 범위 명확화
2. 영수증 첨부 파일 정리 정책 고도화
3. 검색 페이지네이션·정렬 백엔드 통합 테스트 확충
4. mutation pending 상태와 중복 제출 방지

### 이후 권장

1. API 재시도와 화면 상단 오류 배너
3. 대량 거래 검색 UX 개선

## 6. 검증

- `docker compose build frontend` 통과
- `docker run --rm -v "${PWD}\backend:/workspace" -w /workspace gradle:8.10.2-jdk21 gradle test --no-daemon` 통과
- `docker compose build backend` 통과
- `docker compose up -d frontend backend`로 최신 이미지 재생성 완료
- `powershell -ExecutionPolicy Bypass -File .\scripts\browser-smoke.ps1` 통과
- `git diff --check` 통과

## 7. 개발 메모

- 검색 페이지 상태는 필터 객체에 포함했지만, `hasLedgerFilters` 판단에는 포함하지 않아 “조건이 없으면 월 기본 목록” 동작을 유지했다.
- 서버 정렬에는 항상 `id` tie-breaker를 포함해 같은 날짜나 같은 금액의 거래도 안정적으로 정렬되게 했다.
- 기존 검색 배열 응답과의 호환 fallback을 프론트 hook과 스모크 스크립트에 둬서 전환 중에도 검증이 유연하게 동작한다.
