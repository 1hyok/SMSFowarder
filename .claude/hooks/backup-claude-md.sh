#!/usr/bin/env bash
# SessionStart hook: 루트 CLAUDE.md 가 존재하면 .claude/backup/ 으로 스냅샷 갱신.
#
# restore-claude-md.sh 의 짝 — 복원 안전망(.claude/backup/CLAUDE.md)을 항상 최신으로 유지한다.
# CLAUDE.md 는 .gitignore 등록 파일이라 git 추적되지 않음 → 로컬 백업이 유일한 최신 복구원.
# 매 세션 시작 시 직전 세션까지의 최신 내용을 백업에 반영해, 실수 삭제 시 restore 가
# 구버전이 아닌 최신본으로 살려내도록 한다.
#
# 백업이 실패해도 hook 은 절대 차단하지 않는다 (항상 exit 0).
set -uo pipefail

PROJECT_ROOT="${CLAUDE_PROJECT_DIR:-$(pwd)}"
SOURCE="$PROJECT_ROOT/CLAUDE.md"
BACKUP_DIR="$PROJECT_ROOT/.claude/backup"
BACKUP="$BACKUP_DIR/CLAUDE.md"

# 원본이 없으면 백업할 게 없다 (소실 복원은 restore-claude-md.sh 담당).
[ -f "$SOURCE" ] || exit 0

mkdir -p "$BACKUP_DIR"

# 내용이 이미 같으면 복사 skip — 불필요한 mtime 변경 방지.
if [ -f "$BACKUP" ] && cmp -s "$SOURCE" "$BACKUP"; then
    exit 0
fi

if cp "$SOURCE" "$BACKUP"; then
    echo "CLAUDE.md backed up to .claude/backup/" >&2
fi
exit 0
