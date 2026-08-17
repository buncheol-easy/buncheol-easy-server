---
name: start-task
description: 작업 설명을 받아 컨벤션에 맞는 한글 브랜치를 dev 기반으로 체크아웃한 뒤 작업 계획을 제시한다. 수동 트리거 전용.
disable-model-invocation: true
---

# start-task

새 작업의 시작점. 한 호출로 (작업 분류 → 브랜치 체크아웃 → 작업 계획) 을 묶어 처리한다.

## 사전 컨텍스트 (동적 주입)

- 현재 브랜치:
  !`git rev-parse --abbrev-ref HEAD`
- 작업 트리 상태:
  !`git status --short`
- dev 와의 차이:
  !`git log --oneline easyTeam/dev..HEAD 2>/dev/null | head -10`
- 최근 다른 브랜치 (네이밍 참고):
  !`git for-each-ref --sort=-committerdate --format='%(refname:short)' refs/heads | head -8`

## 환경 메모 (이 저장소 고유)

- Remote: **`easyTeam`** (origin 아님)
- Base branch: **`dev`** (easyTeam/dev 기준 분기)
- 브랜치 prefix: `feat` | `fix` | `refactor` | `chore` | `docs` | `ci`
- 브랜치명 패턴: **`<prefix>/<한글-요약>`** (예: `feat/신규-참여-슬랙-운영자-알림`)
- **한국어 브랜치명 유지** (claude-code-action 호환 — 임의로 영문 변환 금지)
- Jira 는 2026-07 부로 사용하지 않는다. 티켓 생성·연결 없이 브랜치와 계획만 만든다.

## 절차

### 1. 작업 설명 수령 + 분류

사용자가 자연어로 무엇을 할지 설명한다. 다음을 추출:

- **작업 종류**: feat / fix / refactor / chore / docs / ci 중 하나
- **summary (한국어, 50자 이내)**: PR title 의 한국어 부분과 동일한 톤
- **브랜치 요약**: summary 에서 조사·서술어 제거한 짧은 한글 (예: `분철-목록-조회-API-구현`)

분류가 모호하면 **사용자에게 묻고 진행한다**. 절대 추측으로 진행하지 않는다.

### 2. 브랜치 초안 제시 + 승인 대기

다음 형식으로 사용자에게 보여주고 승인 후 진행한다:

```
[브랜치 초안]
브랜치명: feat/분철-목록-조회-API-구현
기준:    easyTeam/dev (최신)

이대로 진행할까요? (yes / 수정 사항 / 취소)
```

### 3. 브랜치 생성 + 체크아웃

승인 받았으면 즉시:

```bash
git fetch easyTeam dev
git switch -c <prefix>/<한글-요약> easyTeam/dev
```

예: `git switch -c feat/결제-주문-생성-API-구현 easyTeam/dev`

- 현재 브랜치가 dirty (uncommitted) 면 먼저 사용자에게 알리고 진행 여부 확인.
- 이미 동일 이름의 브랜치가 있으면 사용자에게 알리고 어떻게 처리할지 묻기 (덮어쓰기 절대 금지).
- 브랜치명에 한글이 포함되어도 그대로 유지.

### 4. 작업 계획 제시

체크아웃 완료 후 다음을 한 메시지로 사용자에게 보고:

```
[브랜치] feat/...  (easyTeam/dev 기준)

[작업 계획]
1. <단계 1 — 어느 도메인/파일을 손댈지>
2. <단계 2>
3. <테스트 전략 — 도메인 단위 / 서비스 단위 / 어댑터 통합>
4. <문서·스키마 변경 필요 여부>
5. <(있다면) 신규 ErrorCode prefix·번호 후보>
```

작업이 복잡(여러 모듈, 동시성, 상태 머신, 새 인덱스 등) 하다면 **Plan 서브 에이전트를 호출**해 단계별 구현 전략을 받아오는 것을 권장.

## 안전 가드

- 현재 작업 트리에 uncommitted 변경이 있으면 새 브랜치로 옮기기 전에 사용자에게 알린다.
- `git switch -c` 외의 명령 (`git reset --hard`, `git checkout .`, 강제 force 등) 은 사용하지 않는다.

## 예시 호출

> 사용자: "결제 주문 생성 API 만들고 싶어. POST /v1/payments 로 분철 ID 받아서 toss 결제 주문 만드는거"

1. 분류: feat, summary "결제 주문 생성 API 구현"
2. 초안 제시 (브랜치 `feat/결제-주문-생성-API-구현`)
3. 사용자 "yes"
4. `git fetch easyTeam dev && git switch -c feat/결제-주문-생성-API-구현 easyTeam/dev`
5. 작업 계획 제시 (도메인 PaymentOrder, 어댑터 추가, ErrorCode PAY-XXX, 통합 테스트 전략 등)
