#!/usr/bin/env bash
# PreToolUse Bash hook: block dangerous git state changes without explicit user instruction.
#
# 차단 대상:
#   - git push (force push 만)
#   - git reset --hard
#   - git rebase
#   - git clean -f
#   - git restore <file>      (작업 파일 폐기)
#
# 비차단: git checkout (브랜치 생성/전환/파일 복원 모두) — 안전 작업이라 막지 않는다.
#   브랜치명 규약·이슈 메타데이터는 git-branch-guard.sh 가 별도로 검사한다.
set -uo pipefail

input="$(cat)"
cmd="$(echo "$input" | jq -r '.tool_input.command // empty')"
[ -z "$cmd" ] && exit 0

deny() {
    jq -nc --arg reason "git state 변경은 사용자 명시 지시 필요. '$1' 자율 실행 금지." \
      '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: $reason}}'
    exit 0
}

# git push — force push 만 차단. 일반 push 와 --delete 는 통과.
# /create-pr 같은 명시 워크플로우에서 일반 push 는 안전한 작업 (기존 commit 추가만, 손실 없음).
# Force push (-f, --force, --force-with-lease, +refspec) 는 commit 손실 위험 → 차단 유지.
if [[ "$cmd" =~ (^|[[:space:]]|\;|\&|\|)git[[:space:]]+push([[:space:]]|$) ]]; then
    if [[ "$cmd" =~ git[[:space:]]+push[[:space:]].*(-f([[:space:]]|$)|--force([[:space:]]|=|$)|--force-with-lease) ]] \
        || [[ "$cmd" =~ git[[:space:]]+push[[:space:]]+[^[:space:]]+[[:space:]]+\+ ]]; then
        deny "git push --force"
    fi
fi

# git reset --hard
if [[ "$cmd" =~ git[[:space:]]+reset[[:space:]].*--hard ]]; then
    deny "git reset --hard"
fi

# git rebase
if [[ "$cmd" =~ (^|[[:space:]]|\;|\&|\|)git[[:space:]]+rebase([[:space:]]|$) ]]; then
    deny "git rebase"
fi

# git branch -d / -D (로컬 브랜치 삭제) — 차단 안 함.
# memory feedback_cleanup_merged_branches.md — 로컬/원격 브랜치 정리 영구 허가.
# 원격 삭제(git push --delete)도 위 push 블록에서 통과.

# git clean -f / -fd / -ffd 등
if [[ "$cmd" =~ git[[:space:]]+clean[[:space:]].*-[a-zA-Z]*f ]]; then
    deny "git clean -f"
fi

# git restore <file> (작업 파일 폐기)
if [[ "$cmd" =~ (^|[[:space:]]|\;|\&|\|)git[[:space:]]+restore([[:space:]]|$) ]]; then
    deny "git restore"
fi

exit 0
