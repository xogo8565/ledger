# plan-v.0.3.26

작성일: 2026-07-01

## 작업 범위

- `P3. 프론트 한글 문자열/인코딩 정리` 항목을 진행했다.
- 프론트 소스의 깨진 한글 패턴을 점검하고, 브라우저 스모크에서 주요 화면 텍스트의 인코딩 깨짐을 자동 감지하도록 보강했다.

## 변경 내용

- `scripts/browser-smoke.mjs`
  - `assertNoBrokenKoreanText` 검증 함수를 추가했다.
  - `�`, `Ã`, `Â`, `??`, 대표적인 mojibake CJK 문자 패턴을 화면 텍스트에서 감지한다.
  - 모바일 가계부/자산/더보기/통계 화면, 전체 화면 패널, 데스크톱 가계부 화면에 검증을 연결했다.
- `README.md`
  - Browser Smoke Test 설명에 주요 화면 한글 깨짐 패턴 검증을 추가했다.
- `plan/new-development-items.md`
  - 프론트 한글 문자열/인코딩 정리 항목을 부분 완료로 최신화했다.

## 점검 결과

- `frontend/src`의 JSX/JS/CSS 파일에서 깨진 한글 패턴을 검색했으며 실제 프론트 화면 소스에서는 깨진 문자열을 찾지 못했다.
- PowerShell 콘솔에서는 UTF-8 한글이 깨져 보일 수 있으나, 파일 자체는 UTF-8 기준으로 정상 인식되는 것을 확인했다.

## 검증

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace node:22-alpine node --check scripts/browser-smoke.mjs
git diff --check
```

## 남은 작업

- 사용자 표시 문구를 상수/리소스 구조로 분리할지 결정한다.
- 실제 실행 중인 Docker Compose 환경에서 전체 `scripts/browser-smoke.ps1`을 재실행해 화면 텍스트 검증까지 통과하는지 확인한다.
