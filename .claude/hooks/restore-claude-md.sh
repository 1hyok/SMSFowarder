#!/usr/bin/env bash
# SessionStart hook: 프로젝트 루트 CLAUDE.md 가 사라졌으면 자동 복원.
#
# CLAUDE.md 는 .gitignore 등록 파일이라 git 추적되지 않음 — 로컬 실수 삭제(IDE/rm/clean) 에 취약하다.
# 본 hook 은 우선순위 fallback 으로 복원:
#   1) `.claude/backup/CLAUDE.md` (가장 최신 알려진 백업)
#   2) git history (commit ef92980b 직전 = `.gitignore` 등록 시점 직전 풀 내용)
#
# 복원이 실패해도 hook 은 절대 차단하지 않는다 (항상 exit 0).
set -uo pipefail

PROJECT_ROOT="${CLAUDE_PROJECT_DIR:-$(pwd)}"
TARGET="$PROJECT_ROOT/CLAUDE.md"
BACKUP="$PROJECT_ROOT/.claude/backup/CLAUDE.md"

if [ -f "$TARGET" ]; then
    exit 0
fi

if [ -f "$BACKUP" ]; then
    cp "$BACKUP" "$TARGET"
    echo "CLAUDE.md restored from .claude/backup/" >&2
    exit 0
fi

if (cd "$PROJECT_ROOT" 2>/dev/null && git show ef92980b^:claude.md > "$TARGET" 2>/dev/null); then
    echo "CLAUDE.md restored from git history (ef92980b^)" >&2
    exit 0
fi

echo "WARN: CLAUDE.md missing and no recovery source found" >&2
exit 0
