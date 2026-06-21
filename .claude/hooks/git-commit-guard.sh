#!/usr/bin/env bash
# PreToolUse Bash hook: block all `git commit` invocations.
#
# 사용자가 Android Studio 커밋 탭에서 직접 검토·커밋하는 워크플로우. Claude의 자율 커밋은 절대 금지.
set -uo pipefail

input="$(cat)"
cmd="$(echo "$input" | jq -r '.tool_input.command // empty')"
[ -z "$cmd" ] && exit 0

# `git commit` 가 단어 경계 단위로 나타나면 차단 (`git commit-tree` 같은 다른 서브커맨드는 영향 없음)
if [[ "$cmd" =~ (^|[[:space:]]|\;|\&|\|)git[[:space:]]+commit([[:space:]]|$) ]]; then
    # 예외: `git commit --amend -m "..."` 또는 `--amend --message=...` 는 *메시지만 수정* 용도로 허용.
    # 주의: staged changes 가 있으면 amend 시 함께 커밋됨. 호출 전에 staging 이 비어있는지 확인할 책임은 호출자.
    if [[ "$cmd" =~ --amend ]] && [[ "$cmd" =~ (-m[[:space:]]|--message) ]]; then
        exit 0
    fi
    jq -nc --arg reason "자율 커밋 금지. 사용자가 Android Studio 커밋 탭에서 직접 검토·커밋합니다. 정말 커밋이 필요하면 사용자에게 직접 실행을 요청하세요. (예외: \`git commit --amend -m\` 으로 마지막 커밋 메시지만 수정하는 케이스는 허용.)" \
      '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: $reason}}'
fi

exit 0
