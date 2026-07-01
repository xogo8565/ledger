# 편한가계부 반복 거래 기능 재노출 정책 개발 기록 v.0.3.22

작성일: 2026-07-01

## 1. 기준

- 이전 기록: `plan/plan-v.0.3.21.md`
- 이번 개발 범위: P2 `반복 거래 기능 노출 정책 결정`
- 기준 문서: `plan/new-development-items.md`

## 2. 정책 결정

- 반복 거래 기능은 제거하지 않고 유지한다.
- 기존 백엔드 API, 스케줄러, 프론트 내부 화면이 이미 유지되고 있으므로 더보기의 관리 메뉴로 재노출한다.
- 반복 거래는 일반 거래 입력의 핵심 흐름이 아니라 설정성 관리 기능으로 보고 더보기 화면에 둔다.

## 3. 구현 내용

### 더보기 메뉴 재노출

- 더보기 화면에 `반복 거래 관리` 메뉴를 추가했다.
- 클릭 시 기존 `RecurringManagerScreen`을 연다.
- 기존 반복 거래 관리 화면의 기능을 그대로 사용한다.
  - 반복 규칙 생성
  - 반복 규칙 수정
  - 반복 규칙 삭제
  - 오늘분 수동 생성

### 문서 갱신

- README의 구현된 MVP 항목에 더보기 반복 거래 관리를 명시했다.
- 신규 개발항목 정리 문서에서 반복 거래 노출 정책 상태를 완료로 갱신했다.

## 4. 변경 파일

- `frontend/src/main.jsx`
- `frontend/src/screens/MoreScreens.jsx`
- `README.md`
- `plan/new-development-items.md`

## 5. 검증

```text
docker run --rm -v "${PWD}:/workspace" -w /workspace/frontend node:22-alpine sh -c "npm ci && npm run build"
git diff --check
```

결과:

- 프론트엔드 Vite 프로덕션 빌드 통과
- `git diff --check` 오류 없음. 단, Windows CRLF 변환 경고만 출력됨
- 기존 `npm audit` 경고는 계속 출력됨: moderate 1건, high 1건

## 6. 남은 과제

1. 반복 거래 관리 화면의 실제 모바일 실기기 스크롤/입력 UX는 별도 화면 검증 항목에서 함께 확인한다.
2. 반복 거래 자동 생성 운영 로그나 실행 이력 화면은 별도 운영 보강 항목으로 분리할 수 있다.

