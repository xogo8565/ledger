# 편한가계부 백엔드 MVC 패키지 세분화 개발 기록 v.0.3.4

작성일: 2026-06-26

## 1. 개발 목표

- 백엔드 프로젝트 폴더 구조를 MVC 흐름에 맞게 더 명확하게 세분화한다.
- 컨트롤러, DTO, 설정, 저장소, 서비스, 도메인, 공통 유틸의 책임 경계를 패키지 구조에서 바로 확인할 수 있도록 정리한다.
- 기존 API 동작은 유지하고 패키지명 변경에 따른 import 오류를 제거한다.

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
git diff --check
```

결과:

- 백엔드 Gradle 테스트 통과
- 백엔드 Docker 이미지 빌드 통과
- `git diff --check` 오류 없음. 단, Windows CRLF 경고만 출력됨
- 기존 `com.comfortableledger.ledger.web`, `com.comfortableledger.ledger.repo` import 잔여 없음

## 5. 후속 권장

1. 현재 `ApiDtos`는 단일 파일에 DTO가 계속 모여 있으므로, 기능별 DTO 파일 분리를 다음 구조 개선 항목으로 진행한다.
2. `service` 패키지도 거래, 자산, 통계, OCR, 초기 데이터 등 도메인별 하위 패키지로 나눌 수 있다.
3. 삭제·대체한 과거 테스트 범위 중 필요한 시나리오는 최신 구조 기준의 테스트로 추가 복원한다.
4. 프론트 개선 사항 : header 에 영수증 자동 입력 section 삭제 / 더보기에서 삭제 / 초기 화면 + 버튼 내 햄버거 내용에 추가
5. 프론트 개선 사항 : 검색 시 필터에 날짜 지정 삭제, 상단에 위치한 xxxx년 mm월로만 필터링되게 수정
6. 프론트 개선 사항 : 지출 / 수입 입력 페이지에서 숫자 입력 칸 삭제 / 반복, 할부 버튼 작동 안함 / 더보기에서 반복 거래 등록 삭제 처리
7. 프론트 개선 사항 : 초기 화면에서 + 버튼은 항시 화면 화단에서 고정 노출되도록 수정