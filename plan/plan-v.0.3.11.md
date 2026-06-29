# 편한가계부 프론트엔드 패널 겹침 수정 기록 v.0.3.11

작성일: 2026-06-29

## 1. 개발 목표

- 거래 상세, 거래 등록, 자산/카테고리/명의 관리 등 전체 화면 패널을 열 때 기존 화면, 하단 네비게이션, floating 버튼과 겹쳐 보이는 문제를 수정한다.

## 2. 원인

- 기존 `.full-panel`과 sheet backdrop이 `.phone-shell` 내부 `position: absolute` 레이어였다.
- `.phone-shell`은 자체 스크롤 컨테이너이므로, 기존 화면 스크롤 위치와 패널 높이가 엇갈릴 때 아래 UI가 같이 보일 수 있었다.
- 전체 화면 패널 컨테이너에 `min-height: 760px`가 있어 작은 모바일 화면에서 viewport 기준 레이어 계산이 불안정했다.

## 3. 구현 내용

### 전체 화면 패널 고정 레이어화

- `.full-panel`을 `position: fixed`로 변경했다.
- 모바일 앱 프레임 폭인 `min(100vw, 430px)`에 맞춰 가운데 정렬했다.
- `height: 100vh`와 `height: 100dvh`를 함께 지정해 모바일 브라우저 주소창 변화에도 대응했다.
- z-index를 `1000`으로 올려 하단 네비/FAB보다 항상 위에 표시되도록 했다.

### 선택 시트 고정 레이어화

- `.sheet-backdrop`도 `position: fixed`로 변경했다.
- entry choice sheet는 z-index `1100`으로 올려 전체 패널보다도 위에 표시되도록 했다.

### 패널 내부 높이 안정화

- 전체 화면 패널 내부 컨테이너의 `min-height: 760px`를 제거하고 `min-height: 0`으로 변경했다.
- 내부 목록/본문 영역만 스크롤되도록 기존 `overflow-y: auto` 구조를 유지했다.

### stacking context 분리

- `.phone-shell`에 `isolation: isolate`를 추가해 내부 z-index 충돌 가능성을 줄였다.

## 4. 적용 파일

- `frontend/src/styles.css`
- `README.md`

## 5. 검증

```powershell
docker run --rm -v "${PWD}\frontend:/app" -w /app node:22-alpine sh -c "npm install && npm run build"
docker compose build frontend
```

결과:

- 프론트엔드 Vite 빌드 통과
- 프론트엔드 Docker 이미지 빌드 통과

## 6. 후속 권장

1. 실제 iPhone Chrome/Safari에서 거래 상세, 거래 등록, 더보기 관리 화면을 각각 열어 하단 네비와 겹치지 않는지 확인한다.
2. 화면별로 내부 스크롤이 필요한 영역이 충분한지 확인한다.
