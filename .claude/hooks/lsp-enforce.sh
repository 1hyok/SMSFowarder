#!/usr/bin/env bash
# PreToolUse Bash hook: Kotlin (.kt / .kts) 대상 grep/rg/ag 호출을 strict block.
#
# 의도: Kotlin 심볼 작업(클래스·함수·변수 정의/참조 탐색) 은 LSP findReferences /
# goToDefinition / workspaceSymbol 우선. grep 은 rename 추적 불가·scope-blind·비용 큼.
#
# 차단 조건 (둘 다 hit):
#   1) command 안에 grep / rg / ag 단어 등장
#   2) command 안에 .kt 또는 .kts 등장
#
# 우회: command 를 `BYPASS_LSP=1 <cmd>` 로 prefix 하면 통과. 정당한 텍스트 검색
# (TODO 일괄·import 패턴 통계 등) 또는 build.gradle.kts 안의 dependency 라인 검색
# 처럼 LSP 로 못 찾는 케이스용.
set -uo pipefail

input="$(cat)"
cmd="$(echo "$input" | jq -r '.tool_input.command // empty')"
[ -z "$cmd" ] && exit 0

# 명시 우회: command 가 BYPASS_LSP=1 로 시작하면 통과.
if [[ "$cmd" =~ ^[[:space:]]*BYPASS_LSP=1[[:space:]] ]]; then
    exit 0
fi

# grep/rg/ag 호출 + .kt/.kts 대상 둘 다 hit 시 차단.
if [[ "$cmd" =~ (^|[[:space:]]|\;|\&|\|)(grep|rg|ag)([[:space:]]|$) ]] \
    && [[ "$cmd" =~ \.kts?([[:space:]]|\'|\"|/|$|\)) ]]; then
    jq -nc --arg cmd "$cmd" '
      {hookSpecificOutput: {
        hookEventName: "PreToolUse",
        permissionDecision: "deny",
        permissionDecisionReason: ("Kotlin 심볼 검색은 LSP findReferences / goToDefinition / workspaceSymbol 우선 사용. " +
          "정말 텍스트 검색이 필요하면 \"BYPASS_LSP=1 \" prefix 로 우회. (차단된 명령: " + $cmd + ")")
      }}'
    exit 0
fi

exit 0
