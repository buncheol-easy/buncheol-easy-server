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

4. **Body 작성** — `.github/PULL_REQUEST_TEMPLATE.md` 형식 그대로 (단, `관련 티켓` 섹션은 Jira 미사용으로 항상 제거):

   ```markdown
   ## 주요 변경

   <이 PR 이 무엇을·왜 하는지 1~2 문단. title 보다 한 단계 구체적으로.>

   ## 변경 사항

   ### 추가 / 변경 API
   | 메서드 | 경로 | 응답 | 설명 |
   |---|---|---|---|
   | `GET` | `/v1/...` | `XxxResponse` | ... |

   ### 동작 변경
   - <기존 API/도메인 동작 before/after>

   ### 신규 에러코드
   | 코드 | HTTP | 메시지 |
   |---|---|---|
   | XXX-000 | 4xx | ... |

   ### 도메인 / 인프라 변경
   - <엔티티·리포지토리·스키마·인덱스·마이그레이션>

   ## 구현 메모
   - <비자명한 결정의 근거만. N+1 회피 전략, 인덱스 추가 이유, CAS / 동시성 처리, 트랜잭션 경계, 포트-어댑터 위임, 신규 도메인 메서드 시그니처 등>
   ```

   - **해당 없는 섹션은 통째로 삭제한다** (템플릿 헤더 주석에 명시).
   - 한국어 본문, 이모지 금지, AI 서명·Co-Authored-By 금지.
   - `code-quality-reviewer` 가 도출한 후속 작업(별도 PR 대상) 이 있다면 마지막에 `## 후속 작업` 으로 한 섹션 추가 가능.

5. **PR 생성**
   ```bash
   /opt/homebrew/bin/gh pr create \
     --base dev \
     --title "[FEAT] ..." \
     --body "$(cat <<'EOF'
   ## 주요 변경
   ...
   EOF
   )"
   ```
   - draft 가 필요하면 `--draft` 추가. 사용자가 명시하지 않으면 ready PR 로 만든다.

6. **결과 보고**
   - 생성된 PR URL 한 줄 + title + base/head + commits 개수 요약을 사용자에게 알린다.
   - 머지 / approve 같은 외부 액션은 사용자가 별도로 지시할 때만.

## 금지 사항

- AI 서명, Co-Authored-By, 이모지, "🤖 Generated with..." 등의 표시 일체.
- `main` 으로의 PR 생성·푸시.
- `--no-verify`, `--force-with-lease` 를 사용자 명시 요청 없이 사용.
- 한국어 브랜치명을 임의로 영문으로 변환 (claude-code-action 호환을 위해 한글 유지 — 메모리 기록).

## 실제 예시 (#22)

- Branch: `feat/KAN-77-BE-분철-목록-조회-API-구현` → `dev` (Jira 사용 당시 브랜치명 — 지금은 KAN 번호 없이 한글 작업명만)
- Title: `[FEAT] 분철 공개 목록 조회 API 구현`
- Body 골격: 주요 변경(1문단) / API 표 / 신규 에러코드 표 / 도메인·인프라 bullet / 구현 메모(N+1·커서·LIKE escape·인덱스 설계·트랜잭션)
