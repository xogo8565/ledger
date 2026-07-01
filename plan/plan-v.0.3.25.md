# 편한가계부 모바일 전체 화면 패널 자동 검증 보강 기록 v.0.3.25

작성일: 2026-07-01

## 1. 기준

- 이전 기록: `plan/plan-v.0.3.24.md`
- 이번 개발 범위: P3 `모바일 전체 화면 패널 실기기 검증`
- 실제 iPhone Chrome/Safari 실기기 검증은 직접 수행하지 못하므로 Playwright 모바일 viewport 자동 검증으로 보강했다.

## 2. 구현 내용

### full-panel viewport 검증 추가

- `scripts/browser-smoke.mjs`에 `assertFullPanelFitsViewport` helper를 추가했다.
- 검증 기준:
  - `.full-panel`이 보이는 상태인지 확인
  - 패널의 x/y 시작점이 viewport 밖으로 벗어나지 않는지 확인
  - 패널 width가 viewport width를 초과하지 않는지 확인
  - 패널 height가 viewport height를 초과하지 않는지 확인
  - 문서 전체 horizontal overflow가 없는지 확인

### 검증 대상 패널 확대

모바일 viewport 390x844 기준으로 다음 화면을 확인한다.

- 자산 등록/수정 패널
- 카드 결제 관리 패널
- 카테고리 관리 패널
- 명의 관리 패널
- 반복 거래 관리 패널
- 영수증 OCR 패널
- 거래 입력 패널
- 예산 설정 패널

### 반복 거래 재노출 정책 반영

- 기존 브라우저 스모크는 더보기 메뉴에 반복 거래가 없어야 한다고 검증했다.
- v0.3.22에서 반복 거래 관리가 더보기 메뉴로 재노출되었으므로, 스모크 기준을 `반복 거래 관리` 메뉴가 표시되고 패널이 열리는지 확인하도록 변경했다.

## 3. 변경 파일

- `scripts/browser-smoke.mjs`
- `README.md`
- `plan/new-development-items.md`

## 4. 검증

```text
docker run --rm -v "${PWD}:/workspace" -w /workspace node:22-alpine node --check scripts/browser-smoke.mjs
git diff --check
```

결과:

- 브라우저 스모크 스크립트 Node 문법 검사 통과
- `git diff --check` 오류 없음. 단, Windows CRLF 변환 경고만 출력됨

## 5. 남은 과제

1. 실제 iPhone Chrome/Safari에서 주요 full-panel을 열어 하단 내비게이션/FAB 겹침과 내부 스크롤을 확인한다.
2. 실행 중인 Docker Compose 스택에서 `scripts/browser-smoke.ps1` 전체 브라우저 스모크를 수행한다.
3. 거래 상세 패널도 테스트 데이터 조건이 안정적인 경로로 별도 자동 검증에 포함할지 검토한다.

