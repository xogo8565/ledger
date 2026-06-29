# 편한가계부 프론트엔드 캐시 정책 개발 기록 v.0.3.10

작성일: 2026-06-29

## 1. 개발 목표

- 프론트엔드 변경사항 배포 후 사용자가 브라우저 캐시를 직접 비우지 않아도 최신 화면이 반영되도록 한다.
- Vite의 해시 기반 번들 파일은 장기 캐시하고, 새 번들 경로를 담는 HTML은 캐시하지 않도록 분리한다.

## 2. 구현 내용

### Nginx 캐시 정책

- `/` 및 `/index.html`
  - `Cache-Control: no-store, no-cache, must-revalidate, proxy-revalidate, max-age=0`
  - `Pragma: no-cache`
  - `Expires: 0`
- `/assets/*`
  - `Cache-Control: public, max-age=31536000, immutable`

Vite는 JS/CSS 파일명에 content hash를 붙인다. 따라서 assets는 장기 캐시해도 변경 시 새 파일명으로 배포된다. 반대로 `index.html`은 최신 assets 경로를 들고 있으므로 캐시하지 않는다.

### HTML 메타 보강

- `frontend/index.html`에 캐시 방지 meta를 추가했다.
- 깨져 있던 HTML title을 `가계부`로 정상화했다.

## 3. 적용 파일

- `frontend/nginx.conf`
- `frontend/index.html`
- `README.md`

## 4. 검증

```powershell
docker run --rm -v "${PWD}\frontend:/app" -w /app node:22-alpine sh -c "npm install && npm run build"
docker compose build frontend
```

추가로 테스트용 Docker 네트워크에서 프론트 컨테이너를 띄워 응답 헤더를 확인했다.

확인 결과:

- `/`
  - `Cache-Control: no-store, no-cache, must-revalidate, proxy-revalidate, max-age=0`
- `/index.html`
  - `Cache-Control: no-store, no-cache, must-revalidate, proxy-revalidate, max-age=0`
- `/assets/index-*.js`
  - `Cache-Control: public, max-age=31536000, immutable`

## 5. 운영 참고

- 이번 변경 이후부터는 새 배포 시 HTML이 매번 재검증되므로 최신 JS/CSS 해시 파일을 자동으로 받는다.
- 이미 이전 응답이 브라우저에 강하게 캐시된 사용자는 첫 1회는 탭 종료/재접속이 필요할 수 있다. 이후 배포부터는 직접 캐시 삭제 없이 반영된다.
- 외부 reverse proxy나 CDN을 추가하면 동일하게 HTML no-store, assets immutable 정책을 유지해야 한다.
