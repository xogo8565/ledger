# 편한가계부 더보기 버전 표시 개발 기록 v.0.3.8

작성일: 2026-06-29

## 1. 개발 목표

- 사용자가 모바일 화면에서 현재 실행 중인 앱 버전을 확인할 수 있도록 더보기 화면에 버전을 표시한다.
- iOS 브라우저 캐시나 서버 배포본 확인 시 현재 프론트 번들 버전을 식별할 수 있게 한다.

## 2. 구현 내용

- 더보기 화면 하단에 `앱 버전` 카드를 추가했다.
- 기본 버전은 `frontend/package.json`의 `version` 값을 사용한다.
- 빌드/배포 환경에서 `VITE_APP_VERSION`을 지정하면 해당 값을 우선 표시한다.

표시 예:

```text
앱 버전  v0.3.7
```

## 3. 적용 파일

- `frontend/src/screens/MoreScreens.jsx`
- `frontend/src/styles.css`
- `README.md`

## 4. 검증

```powershell
docker run --rm -v "${PWD}\frontend:/app" -w /app node:22-alpine sh -c "npm install && npm run build"
docker compose build frontend
```

결과:

- 프론트엔드 Vite 빌드 통과
- 프론트엔드 Docker 이미지 빌드 통과
- 현재 `frontend/package.json` 버전 기준 `v0.3.7` 표시

## 5. 운영 참고

- 서버 반영 후 iPhone Chrome/Safari에서 더보기 화면의 버전이 갱신됐는지 확인하면 최신 프론트 컨테이너 적용 여부를 판단할 수 있다.
- 모바일 캐시가 남아 있으면 이전 번들이 실행될 수 있으므로, 버전이 그대로면 탭 종료 후 재접속 또는 프론트 컨테이너 재기동을 확인한다.
