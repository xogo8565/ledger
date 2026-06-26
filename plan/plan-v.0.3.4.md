# 편한가계부 백엔드 MVC 패키지 세분화 및 후속 개선 개발 기록 v.0.3.4

작성일: 2026-06-26

## 1. 개발 목표

- 백엔드 프로젝트 폴더 구조를 MVC 흐름에 맞게 더 명확하게 세분화한다.
- 컨트롤러, DTO, 설정, 저장소, 서비스, 도메인, 공통 유틸의 책임 경계를 패키지 구조에서 바로 확인할 수 있도록 정리한다.
- 기존 API 동작은 유지하고 패키지명 변경에 따른 import 오류를 제거한다.
- 후속 개선 항목이었던 DTO 기능별 분리, 서비스 하위 패키지 분리, 반복 거래 노출 정책 정리, 프론트 브라우저 스모크 테스트 재실행을 완료한다.

## 2. 구현 내용

### 백엔드 MVC 패키지 구조 정리

기존 `web`, `repo` 중심 구조를 아래 기준으로 재정리했다.

| 패키지 | 역할 |
| --- | --- |
| `config` | Spring 설정, CORS 등 애플리케이션 설정 |
| `controller` | REST API 컨트롤러와 API 예외 처리 |
| `dto` | API 요청/응답 DTO |
| `repository` | Spring Data JPA Repository |
| `service` | 업무 로직과 트랜잭션 처리 |
| `domain` | JPA Entity, Enum, 도메인 모델 |
| `util` | 문자열, 숫자, 암호성 값 등 공통 유틸 |

### DTO 기능별 분리

단일 `ApiDtos` 파일에 모여 있던 DTO를 기능별 묶음 파일로 분리했다.

| DTO 파일 | 주요 범위 |
| --- | --- |
| `AssetDtos` | 자산, 카드 자산 저장 요청, 자산 요약 |
| `BudgetDtos` | 예산 설정, 카테고리 예산 |
| `CardPaymentDtos` | 카드 상세, 카드 결제 예정 |
| `CategoryDtos` | 카테고리 조회/저장 |
| `ImportDtos` | 문자 자동 입력 파싱 |
| `MemberDtos` | 구성원, 소비자 마이그레이션 |
| `ReceiptDtos` | 영수증 첨부, OCR 미리보기 |
| `RecurringDtos` | 반복 거래 규칙과 생성 결과 |
| `SummaryDtos` | 월간/연간/기간 통계, 부트스트랩 |
| `TransactionDtos` | 거래 조회/검색/생성 |

`com.comfortableledger.ledger.dto.ApiDtos` import는 제거하고, 컨트롤러/서비스/테스트 import를 신규 DTO 파일 기준으로 정리했다.

### 서비스 하위 패키지 분리

`service` 패키지를 도메인 성격별 하위 패키지로 나눴다.

| 서비스 패키지 | 포함 범위 |
| --- | --- |
| `service.asset` | 자산, 예산, 카드, 카드 결제 스케줄 |
| `service.importing` | 초기 데이터 적재, 문자 자동 입력 |
| `service.member` | 구성원 관리 |
| `service.receipt` | 영수증 첨부, OCR |
| `service.recurring` | 반복 거래 규칙과 스케줄러 |
| `service.statistics` | 통계 집계 |
| `service.transaction` | 거래 명령/조회/검색 조건 |

서비스 패키지 이동에 맞춰 컨트롤러와 테스트 import를 정리했고, package-private 테스트가 필요한 파일은 같은 하위 패키지로 이동했다.

### 프론트 및 운영 개선 반영

- 영수증 자동 입력 진입점은 더보기 메뉴에서 제거하고, 초기 화면 `+` 버튼의 입력 선택 시트로 이동했다.
- 반복 거래 등록 진입점은 더보기 메뉴에서 숨겼다.
- 반복 거래 백엔드, API, 내부 컴포넌트는 유지한다. 완전 제거 여부는 별도 기능 정책으로 결정한다.
- 거래 검색 필터는 기본 숨김 상태로 두고, 필터 행의 화살표 버튼을 눌렀을 때만 상세 필터가 보이도록 정리했다.
- 거래 검색 날짜 직접 지정 UI를 제거하고, 상단 선택 월 기준으로만 검색 기간을 전달하도록 정리했다.
- 거래 입력 화면의 금액 직접 입력 칸과 미동작 보조 버튼을 제거하고, 금액은 계산기 입력과 금액 표시 영역으로만 처리하도록 정리했다.
- 자산 금액, 카드 결제 예약 금액, 예산 금액, 검색 금액 필터, 이체 수수료 등 프론트 금액 입력은 입력 중 자동으로 천 단위 콤마가 표시되도록 공통 `MoneyInput`으로 정리했다.
- 거래 입력 계산기 금액 표시도 숫자 덩어리에 천 단위 콤마가 표시되도록 정리했다.
- 하단 탭과 `+` 버튼은 앱 화면 내부에서 sticky 배치로 유지해 화면 깨짐과 겹침을 줄였다.
- `127.0.0.1` 로컬 개발 주소에서 자산 삭제가 CORS 403으로 실패하던 문제를 해결했다.
- MySQL 데이터 디렉터리와 `mysql:8.4` 이미지를 초기화한 뒤 Docker Compose를 재실행해 기본 데이터 세팅을 확인했다.

## 3. 현재 백엔드 구조

```text
backend/src/main/java/com/comfortableledger/ledger
├── config
├── controller
├── domain
├── dto
│   ├── AssetDtos.java
│   ├── BudgetDtos.java
│   ├── CardPaymentDtos.java
│   ├── CategoryDtos.java
│   ├── ImportDtos.java
│   ├── MemberDtos.java
│   ├── ReceiptDtos.java
│   ├── RecurringDtos.java
│   ├── SummaryDtos.java
│   └── TransactionDtos.java
├── repository
├── service
│   ├── asset
│   ├── importing
│   ├── member
│   ├── receipt
│   ├── recurring
│   ├── statistics
│   └── transaction
└── util
```

## 4. 검증

```powershell
docker run --rm -v "${PWD}\backend:/workspace" -w /workspace gradle:8.10.2-jdk21 gradle test --no-daemon
docker compose up -d --build backend frontend
docker run --rm --add-host=host.docker.internal:host-gateway -v "${PWD}:/work" -w /tmp/smoke -e PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 -e PLAYWRIGHT_BROWSERS_PATH=/ms-playwright -e FRONTEND_URL=http://host.docker.internal:8081 -e BACKEND_URL=http://host.docker.internal:8080 -e SCREENSHOT_PATH=/work/browser-smoke-home.png mcr.microsoft.com/playwright:v1.49.0-noble sh -lc "npm init -y >/dev/null && npm install playwright@1.49.0 >/dev/null && cp /work/scripts/browser-smoke.mjs ./browser-smoke.mjs && node browser-smoke.mjs"
git diff --check
```

결과:

- 백엔드 Gradle 테스트 통과
- 백엔드/프론트 Docker 이미지 빌드 및 컨테이너 재기동 통과
- 프론트 Vite 프로덕션 빌드 통과
- Docker Compose 재기동 후 기본 데이터 확인: 자산 12개, 카테고리 13개, 거래 123개
- Playwright 브라우저 스모크 테스트 통과
- 금액 입력 콤마 표시 브라우저 확인 통과: 자산 금액 `1,234,567`, 검색 최소 금액 `9,876,543`, 거래 계산기 `1,234`
- 스모크 테스트 확인 범위: 월 거래 화면, 자산 화면, 더보기 관리 화면, 영수증 자동 입력 진입, 거래 직접 입력, 검색 필터 접기/펼치기, 예산/통계 주요 화면
- `git diff --check` 오류 없음. 단, `plan/plan-v.0.3.4.md`의 Windows CRLF 변환 경고만 출력됨
- 기존 `com.comfortableledger.ledger.dto.ApiDtos` import 잔여 없음

## 5. 후속 권장

1. 반복 거래 기능은 현재 사용자 노출만 숨긴 상태이므로, 완전 제거 또는 재노출 여부를 별도 정책으로 결정한다.
2. OCR/영수증 기능은 실제 샘플 이미지 기반 회귀 테스트를 추가하면 브라우저 스모크의 신뢰도를 더 높일 수 있다.
3. DTO 파일은 기능별 묶음으로 분리했지만, 특정 DTO 묶음이 더 커지면 public record 단위 파일 분리까지 진행한다.
