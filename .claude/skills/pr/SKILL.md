---
name: pr
description: 현재 브랜치를 easyTeam remote 에 푸시하고 dev 를 base 로 PULL_REQUEST_TEMPLATE 형식의 한국어 PR 을 생성한다. 수동 트리거 전용.
disable-model-invocation: true
---

# pr

현재 브랜치에 쌓인 커밋들을 묶어 BuncheolEasy 컨벤션의 PR 로 만든다.

## 사전 컨텍스트 (동적 주입)

- 현재 브랜치:
  !`git rev-parse --abbrev-ref HEAD`
- upstream 추적 여부:
  !`git rev-parse --abbrev-ref --symbolic-full-name @{u} 2>&1 || echo "no upstream"`
- dev 와의 커밋 차이:
  !`git log --oneline easyTeam/dev..HEAD 2>/dev/null || echo "easyTeam/dev 와 비교 불가 - fetch 필요"`
- dev 와의 diff 통계:
  !`git diff --stat easyTeam/dev...HEAD 2>/dev/null | tail -30`
- 작업 트리 상태:
  !`git status --short`
- PR 템플릿:
  !`cat .github/PULL_REQUEST_TEMPLATE.md 2>/dev/null || echo "템플릿 없음"`

## 환경 메모 (이 저장소 고유)

- Remote 이름: **`easyTeam`** (관용적인 `origin` 아님)
- PR base: **`dev`** 고정. `main` 으로 만들지 말 것 (`main` 은 별도 동기화).
- gh CLI: `/opt/homebrew/bin/gh` 로 풀패스 호출 (PATH 에 없음).
- Jira 는 2026-07 부로 사용하지 않는다. 티켓 추출·연결·전이를 하지 않고, PR 템플릿에 `관련 티켓` 섹션이 남아 있어도 항상 통째로 삭제한다.

## 절차

1. **사전 검증**
   - 현재 브랜치가 `dev` 또는 `main` 이면 즉시 중단 (feature 브랜치에서 PR 을 만든다).
   - 작업 트리에 uncommitted 변경이 있으면 사용자에게 알리고 진행 여부 묻기.
   - `git fetch easyTeam dev` 로 base 최신화. (필요 시)

2. **푸시**
   - upstream 미설정이면: `git push -u easyTeam <branch>`
   - 이미 설정돼 있으면: `git push`
   - **`main` 또는 `dev` 로 푸시 금지**. `--force` / `--force-with-lease` 는 사용자 명시 요청에만.

3. **Title 작성**
   - 형식: `[TAG] 한국어 요약`, 70자 이내.
   - tag: `FEAT` | `FIX` | `REFACTOR` | `CHORE` | `CI` | `DOCS` | `TEST`
   - 보통 브랜치 prefix (`feat/`, `fix/`, ...) 와 1:1 매칭됨. 다중 type 이 섞였으면 main type 으로 고르고 본문에서 부수 변경을 설명.

4. **Body 작성** — 위에 주입된 `.github/PULL_REQUEST_TEMPLATE.md`의 칸과 순서를 그대로 따른다 (단, `관련 티켓` 섹션은 Jira 미사용으로 항상 제거):

   - 소제목은 바꾸지 않는다. **해당 없는 칸은 통째로 삭제한다.** HTML 주석(`<!-- -->`)은 지운다.
   - 내용은 단답으로, 한 칸은 3줄 안팎. 판단 과정처럼 길어지는 내용은 커밋 본문에 둔다.
   - 「왜 지금 해야 하나」와 「한계와 트레이드오프」는 비워 두지 않는다. 감수한 게 없으면 "없음"이라고 쓴다.
   - 하지 않은 일·후속 작업은 「한계와 트레이드오프」에 쓴다.
   - 「리뷰 포인트」의 위험도는 🔴🟡🟢로 표시한다. 이모지는 이 위험도 표시에만 쓴다.
   - 「자기검증」은 실제로 한 것만 체크하고, 확인 방법을 괄호에 적는다.
   - 한국어 본문, AI 서명·Co-Authored-By 금지.

5. **PR 생성**
   ```bash
   /opt/homebrew/bin/gh pr create \
     --base dev \
     --title "[FEAT] ..." \
     --body "$(cat <<'EOF'
   ## 해결하려는 문제
   ...
   EOF
   )"
   ```
   - draft 가 필요하면 `--draft` 추가. 사용자가 명시하지 않으면 ready PR 로 만든다.

6. **결과 보고**
   - 생성된 PR URL 한 줄 + title + base/head + commits 개수 요약을 사용자에게 알린다.
   - 머지 / approve 같은 외부 액션은 사용자가 별도로 지시할 때만.

## 금지 사항

- AI 서명, Co-Authored-By, "🤖 Generated with..." 등의 표시 일체. 이모지는 리뷰 포인트 위험도 표시(🔴🟡🟢) 밖에서 쓰지 않는다.
- `main` 으로의 PR 생성·푸시.
- `--no-verify`, `--force-with-lease` 를 사용자 명시 요청 없이 사용.
- 한국어 브랜치명을 임의로 영문으로 변환 (claude-code-action 호환을 위해 한글 유지 — 메모리 기록).

## 실제 예시 (#22)

- Branch: `feat/KAN-77-BE-분철-목록-조회-API-구현` → `dev` (Jira 사용 당시 브랜치명 — 지금은 KAN 번호 없이 한글 작업명만)
- Title: `[FEAT] 분철 공개 목록 조회 API 구현`
- Body 골격: 해결하려는 문제 / 왜 지금 해야 하나 / 해결 방법(API 표·에러코드 표·스키마) / 한계와 트레이드오프 / 기존 기능 영향 / 리뷰 포인트 / 배포 체크리스트 / 자기검증
