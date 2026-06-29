# 편한가계부 카드 자산 등록 오류 수정 기록 v.0.3.12

작성일: 2026-06-29

## 1. 개발 목표

- 자산 화면에서 카드 등록 시 503/Internal Server Error로 보이는 문제를 수정한다.
- 결제계좌 또는 결제일 입력이 잘못된 경우 서버 내부 예외가 아니라 명확한 400 검증 오류를 반환하도록 한다.

## 2. 원인

- 카드 등록 서비스에서 `paymentAccountId`를 `orElseThrow()`로 직접 조회했다.
- 결제계좌가 없거나 잘못된 ID가 넘어오면 명시 메시지 없이 내부 예외가 발생할 수 있었다.
- `statementClosingDay`, `paymentDay`는 JPA Entity에는 `@Min/@Max`가 있었지만 API 요청 DTO에는 범위 검증이 없었다.
- 따라서 잘못된 날짜 값이 들어오면 저장 시점에 JPA/DB 계층 예외로 보일 수 있었다.

## 3. 구현 내용

### DTO 검증 추가

`SaveCardAssetRequest`에 아래 검증을 추가했다.

- `statementClosingDay`: 1~31
- `paymentDay`: 1~31

### 결제계좌 검증 추가

카드 결제계좌 조회를 전용 검증 함수로 분리했다.

검증 항목:

- 결제계좌 ID 필수
- 결제계좌 존재 여부
- 동일 가구 소속 여부
- 숨김 자산 여부
- 카드/부채 자산을 결제계좌로 선택하지 않았는지 여부

잘못된 경우 `IllegalArgumentException`을 발생시켜 기존 `ApiExceptionHandler`를 통해 HTTP 400으로 내려가도록 했다.

### 기타 보정

- 카드 수정 시 대상 자산이 없을 때도 `Asset not found` 400 오류로 처리한다.
- 자산 삭제 시 대상 자산이 없을 때도 `Asset not found` 400 오류로 처리한다.

## 4. 적용 파일

- `backend/src/main/java/com/comfortableledger/ledger/dto/AssetDtos.java`
- `backend/src/main/java/com/comfortableledger/ledger/service/asset/AssetManagementService.java`
- `README.md`

## 5. 검증

```powershell
docker run --rm -v "${PWD}\backend:/workspace" -w /workspace gradle:8.10.2-jdk21 gradle test --no-daemon
docker compose build backend
```

결과:

- 백엔드 Gradle 테스트 통과
- 백엔드 Docker 이미지 빌드 통과

## 6. 운영 확인

- 서버 반영 후 카드 등록을 다시 시도한다.
- 여전히 503이면 애플리케이션 예외가 아니라 백엔드 컨테이너 비정상 상태 또는 프론트가 구버전 백엔드를 보고 있을 가능성이 높으므로 `docker compose logs backend --tail=100`으로 확인한다.
