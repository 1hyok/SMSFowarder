#!/usr/bin/env bash
# PreToolUse(WebFetch|WebSearch) — 공식 문서 소스 라우팅 가이드를 모델 컨텍스트에 주입.
# 항상 로드되는 CLAUDE.md 부담 없이, 실제 web 조회 직전에만 라우팅 표를 리마인드한다.
# stdin(툴 입력 JSON)은 소비하지 않고 무시; 가이드 본문을 additionalContext 로 emit.
set -uo pipefail

HOOK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GUIDE="$HOOK_DIR/doc-source-guide.md"

# jq 와 가이드 파일이 있을 때만 주입. 없으면 조용히 통과(툴 차단 안 함).
if [ -f "$GUIDE" ] && command -v jq >/dev/null 2>&1; then
  jq -Rs '{hookSpecificOutput: {hookEventName: "PreToolUse", additionalContext: .}}' "$GUIDE"
fi

exit 0
