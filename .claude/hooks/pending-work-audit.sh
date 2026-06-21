#!/usr/bin/env bash
# SessionStart hook: 매 세션 시작마다 "미완료 작업" 의 FRESH 실측 스냅샷을 컨텍스트에 주입.
#
# 배경(왜 hook 인가):
#   과거 세션에서 "할 일 없음 / 다 했다" 를 기억·summary 에서 단정 → 실제로는 열린 이슈·미머지 PR·
#   commit 0 짜리 stub 브랜치가 남아있던 사고가 반복됐다. "다음엔 확인하겠다" 는 말은 세션을 못 넘긴다
#   (다음 세션의 나는 그 다짐을 기억 못 함). 그래서 다짐 대신 hook 으로 박는다: 매 시작마다 gh·git
#   실측을 돌려, 그 출력에서만 답하도록 강제한다.
#
# 동작:
#   gh issue list(assignee=1hyok) + gh pr list(author=1hyok, mergeable/review) + 로컬 브랜치 ahead-count
#   를 모아 SessionStart 표준 출력(stdout JSON, hookSpecificOutput.additionalContext)으로 주입.
#
# 안전:
#   gh 미설치/미인증/네트워크 실패·python3 부재 시 조용히 exit 0 (세션을 절대 막지 않음, 가짜 "0건"
#   도 주입하지 않음). 모든 실패 경로는 non-blocking.
set -uo pipefail

PROJECT_ROOT="${CLAUDE_PROJECT_DIR:-$(pwd)}"
cd "$PROJECT_ROOT" 2>/dev/null || exit 0

command -v gh >/dev/null 2>&1 || exit 0
command -v python3 >/dev/null 2>&1 || exit 0

OWNER="1hyok"

# gh 호출이 실패(미인증/네트워크)하면 가짜 "0건" 대신 주입 자체를 건너뛴다.
issues_json="$(gh issue list --assignee "$OWNER" --state open --limit 50 \
  --json number,title,labels 2>/dev/null)" || exit 0
prs_json="$(gh pr list --author "$OWNER" --state open --limit 50 \
  --json number,title,headRefName,mergeable,reviewDecision 2>/dev/null)" || exit 0

# 로컬 작업 브랜치 ahead-count (전부 로컬 git, 네트워크 없음). origin/master → master 순 fallback.
base="origin/master"
git rev-parse --verify -q "$base" >/dev/null 2>&1 || base="master"
git rev-parse --verify -q "$base" >/dev/null 2>&1 || base=""
branch_lines=""
if [ -n "$base" ]; then
  while IFS= read -r b; do
    case "$b" in develop|main|master) continue ;; esac
    ahead="$(git rev-list --count "$base..$b" 2>/dev/null || echo "?")"
    branch_lines+="${b}"$'\t'"${ahead}"$'\n'
  done < <(git for-each-ref --format='%(refname:short)' refs/heads/ 2>/dev/null)
fi

ISSUES_JSON="$issues_json" PRS_JSON="$prs_json" BRANCHES="$branch_lines" BASE="$base" python3 <<'PY'
import json, os, datetime

def load(name):
    raw = os.environ.get(name, "").strip()
    if not raw:
        return []
    try:
        return json.loads(raw)
    except Exception:
        return []

issues = load("ISSUES_JSON")
prs = load("PRS_JSON")
base = os.environ.get("BASE") or "master"
branches_raw = os.environ.get("BRANCHES", "")

L = []
ts = datetime.datetime.now().strftime("%Y-%m-%d %H:%M")
L.append(f"[PENDING-WORK AUDIT — {ts} 세션 시작 실측]")
L.append("아래는 기억/summary 가 아니라 방금 돌린 gh·git 결과다.")
L.append("\"할 일 없음/다 했다\" 류 답변은 반드시 이런 fresh audit 으로 뒷받칠 것 — 단정 전 재실행. base=master.")
L.append("")

L.append(f"■ OPEN ISSUES (assignee=1hyok): {len(issues)}건")
if issues:
    for it in issues:
        labels = ", ".join(l.get("name", "") for l in it.get("labels", []))
        suffix = f"  [{labels}]" if labels else ""
        L.append(f"  #{it['number']} {it['title']}{suffix}")
else:
    L.append("  (없음)")
L.append("")

L.append(f"■ OPEN PRs (author=1hyok): {len(prs)}건  ← review 승인 없으면 self-merge 불가")
if prs:
    for pr in prs:
        L.append(f"  #{pr['number']} {pr['title']}")
        L.append(f"      <{pr.get('headRefName','?')}> mergeable={pr.get('mergeable','?')} review={pr.get('reviewDecision') or 'NONE'}")
else:
    L.append("  (없음)")
L.append("")

L.append(f"■ LOCAL 작업 브랜치 (base={base} 대비 ahead, +0=stub):")
rows = [x for x in branches_raw.split("\n") if x.strip()]
if rows:
    for row in rows:
        parts = row.split("\t")
        name = parts[0]
        ahead = parts[1] if len(parts) > 1 else "?"
        tag = " (stub/empty)" if ahead == "0" else ""
        L.append(f"  {name}  +{ahead}{tag}")
else:
    L.append("  (없음)")

ctx = "\n".join(L)
print(json.dumps({"hookSpecificOutput": {"hookEventName": "SessionStart", "additionalContext": ctx}}))
PY
exit 0
