# 편한가계부 iPhone 클립보드 권한 대응 개발 기록 v.0.3.6

작성일: 2026-06-29

## 1. 개발 목표

- iPhone Safari/Chrome 등 모바일 브라우저에서 `navigator.clipboard.readText()`가 권한 제한으로 실패하는 문제를 회피한다.
- 서버 또는 웹앱이 클립보드를 자동으로 읽는 방식에만 의존하지 않고, 사용자가 직접 붙여넣을 수 있는 입력 경로를 제공한다.

## 2. 배경

- iOS 브라우저는 보안 정책상 웹앱이 사용자 클립보드를 자유롭게 읽지 못하게 막는 경우가 많다.
- 특히 홈 화면 앱/PWA, HTTP 환경, iframe, Safari/Chrome iOS WebKit 환경에서는 클립보드 읽기 권한 동작이 일관되지 않다.
- 따라서 모바일에서는 자동 읽기보다 `textarea`에 사용자가 직접 붙여넣는 UX가 안정적이다.

## 3. 구현 내용

### 수동 붙여넣기 화면 추가

- `ManualTextImportScreen`을 추가했다.
- 카드 승인 문자, 은행 입출금 문자, 날짜별 거래 목록을 직접 붙여넣은 뒤 기존 `/api/import/text/parse` API로 분석한다.
- 분석 성공 시 기존 거래 입력 화면으로 이동해 자동 채움한다.
- 여러 건이 분석된 경우 현재 입력 폼은 첫 번째 거래 기준으로 채운다.

### iOS fallback 처리

- iPhone/iPad 계열 브라우저를 감지하면 `navigator.clipboard.readText()`를 시도하지 않고 바로 수동 붙여넣기 화면을 연다.
- iOS Chrome(`CriOS`), iOS Firefox(`FxiOS`), iOS Edge(`EdgiOS`) user-agent도 제한 브라우저로 판단한다.
- Mobile Safari 계열 브라우저도 수동 붙여넣기 우선으로 처리한다.
- `navigator.clipboard.readText`가 없는 브라우저도 수동 붙여넣기 화면으로 이동한다.
- 클립보드 읽기 예외 또는 빈 클립보드도 alert 종료 대신 수동 붙여넣기 화면으로 이동한다.
- 클립보드 텍스트 분석 API가 실패해도 alert 종료 대신 수동 붙여넣기 화면으로 이동한다.
- 기존 데스크톱 브라우저의 자동 클립보드 읽기 흐름은 유지했다.

## 4. 적용 파일

- `frontend/src/main.jsx`
- `frontend/src/screens/TransactionScreens.jsx`
- `README.md`

## 5. 검증

```powershell
docker run --rm -v "${PWD}\frontend:/app" -w /app node:22-alpine sh -c "npm install && npm run build"
docker compose build frontend
```

결과:

- 프론트엔드 Vite 빌드 통과
- 프론트엔드 Docker 이미지 빌드 통과
- `npm audit` 기존 moderate/high 취약점 각 1건은 계속 출력됨

## 6. 후속 권장

1. 다건 파싱 `items` 결과를 프론트에서 목록으로 보여주고 선택/일괄 등록할 수 있게 확장한다.
2. 모바일에서 수동 붙여넣기 화면을 기본 경로로 둘지, 자동 읽기 버튼을 별도로 둘지 운영 UX를 정한다.
3. 현재 일부 프론트 한글 문자열이 깨져 있으므로, UTF-8 기준으로 화면 문구를 별도 정리한다.
