# 편한가계부 백엔드 MVC 패키지 세분화 및 프론트 후속 개선 개발 기록 v.0.3.4

작성일: 2026-06-26

## 1. 개발 목표

- 백엔드 프로젝트 폴더 구조를 MVC 흐름에 맞게 더 명확하게 세분화한다.
- 컨트롤러, DTO, 설정, 저장소, 서비스, 도메인, 공통 유틸의 책임 경계를 패키지 구조에서 바로 확인할 수 있도록 정리한다.
- 기존 API 동작은 유지하고 패키지명 변경에 따른 import 오류를 제거한다.
- 프론트 후속 개선 항목 중 입력 진입점, 검색 월 기준 필터, 거래 입력 금액 UI, 하단 고정 버튼을 정리한다.

## 2. 구현 내용

### 패키지 구조 정리

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

### 주요 변경

- `web` 패키지의 컨트롤러 클래스를 `controller` 패키지로 이동했다.
- `web.ApiDtos`를 `dto.ApiDtos`로 이동했다.
- `web.WebConfig`를 `config.WebConfig`로 이동했다.
- `repo` 패키지를 `repository` 패키지로 변경했다.
- 서비스, 테스트, 설정 코드의 import를 신규 패키지 기준으로 정리했다.
- 패키지 이동 과정에서 깨진 테스트 문자열을 정리하고, 초기 데이터/자동 입력/자산 요약 회귀 테스트를 최신 구조 기준으로 재작성했다.

### 프론트 후속 개선

- 가계부 본문 상단의 영수증 자동 입력 CTA 잔여 코드와 스타일을 제거했다.
- `더보기` 메뉴에서 영수증 업로드와 반복 거래 등록 진입점을 제거했다.
- 초기 화면 `+` 버튼의 입력 선택 시트에 `영수증 자동 입력` 항목을 추가했다.
- 거래 검색 필터에서 날짜 직접 지정 UI를 제거하고, 상단 월 선택값의 월 시작일~월 말일만 검색 API에 전달하도록 정리했다.
- 검색 결과 CSV 파일명과 표시 기간도 상단 월 기준으로 정리했다.
- 거래 입력 화면에서 금액 직접 입력 칸과 작동하지 않던 `반복/할부` 보조 버튼을 제거했다.
- 거래 금액은 상단 금액 표시 영역과 하단 계산기 버튼으로만 입력하도록 정리했다.
- 계산기 헤더의 작동하지 않던 장식 버튼을 제거했다.
- 하단 탭과 `+` 버튼을 화면 하단 기준 고정 배치로 변경해 스크롤 중에도 초기 화면에서 계속 노출되도록 했다.
- 화면 깨짐을 줄이기 위해 하단 탭과 `+` 버튼을 전체 viewport 기준이 아니라 앱 폰 쉘 내부 sticky 배치로 조정했다.
- 거래 검색 필터는 기본 접힘 상태로 변경하고, `필터` 행의 화살표 버튼을 눌렀을 때만 검색/유형/금액/정렬 컨트롤이 나타나도록 했다.
- `127.0.0.1` 로컬 개발 주소에서 자산 삭제 시 CORS 검증이 403으로 실패하던 문제를 수정했다.
- 브라우저 스모크 시나리오를 새 메뉴 구조와 계산기 금액 입력 방식에 맞게 갱신했다.

## 3. 현재 백엔드 구조

```text
backend/src/main/java/com/comfortableledger/ledger
├── config
├── controller
├── domain
├── dto
├── repository
├── service
└── util
```

## 4. 검증

```powershell
docker run --rm -v "${PWD}\backend:/workspace" -w /workspace gradle:8.10.2-jdk21 gradle test --no-daemon
docker compose build backend
npm.cmd ci
npm.cmd run build
node --check scripts\browser-smoke.mjs
git diff --check
```

결과:

- 백엔드 Gradle 테스트 통과
- 백엔드 Docker 이미지 빌드 통과
- 프론트 Vite 프로덕션 빌드 통과
- `scripts/browser-smoke.mjs` 문법 검사 통과
- Playwright 좌표 확인으로 필터 기본 접힘, 펼침 후 컨트롤 노출, 하단 탭과 `+` 버튼 비겹침 확인
- `http://127.0.0.1:5173` 브라우저 origin에서 임시 자산 생성 후 삭제 API 200 응답 확인
- `git diff --check` 오류 없음. 단, Windows CRLF 경고만 출력됨
- 기존 `com.comfortableledger.ledger.web`, `com.comfortableledger.ledger.repo` import 잔여 없음
- `npm.cmd ci` 실행 중 audit 알림 2건(중간 1, 높음 1)이 출력됨. 이번 UI 변경 범위에서는 미조치

## 5. 후속 권장

1. 현재 `ApiDtos`는 단일 파일에 DTO가 계속 모여 있으므로, 기능별 DTO 파일 분리를 다음 구조 개선 항목으로 진행한다.
2. `service` 패키지도 거래, 자산, 통계, OCR, 초기 데이터 등 도메인별 하위 패키지로 나눌 수 있다.
3. 삭제·대체한 과거 테스트 범위 중 필요한 시나리오는 최신 구조 기준의 테스트로 추가 복원한다.
4. 프론트 브라우저 스모크 테스트는 백엔드/프론트 서버를 함께 띄운 상태에서 새 시나리오 기준으로 재실행한다.
5. 반복 거래 기능 자체는 백엔드와 내부 컴포넌트에 남아 있으므로, 향후 다시 노출할지 또는 완전히 제거할지 별도 결정한다.
6. `npm audit`에서 보고된 프론트 의존성 취약점은 별도 보안 업데이트 범위에서 검토한다.
