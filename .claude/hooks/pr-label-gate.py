#!/usr/bin/env python3
"""PR 라벨 누락 게이트 — Claude Code Stop 훅.

이번 턴에 `gh pr create` 로 PR 을 생성했는데 같은 턴에 라벨 적용(`--add-label`/`--label`)
이 0회면 턴 종료를 차단(decision: block)하여, "PR 라벨 = 연결 이슈 라벨 복사" 정책
(create-pr command 절차 4)의 누락을 막는다.

설계 메모:
- issue-branch-link-gate 와 동일한 '턴 기반' 구조 — 매 Stop 마다 author OPEN PR 전수를 gh 로
  조회하지 않고, PR 을 *새로 만든 턴* 에만 라벨 적용 여부를 검사한다(네트워크 부담·라벨 없는
  이슈의 false positive 회피). 기존 PR 의 일회성 라벨 보정은 사용자 지시로 따로 처리.
- 연결 이슈에 라벨이 없어 복사할 게 없는 경우엔, 한 번 막힌 뒤 재종료(stop_hook_active)하면
  통과시켜 무한 루프를 방지한다. (그 경우 이슈부터 라벨을 부여하는 게 근본 해결.)
- 입력/transcript 파싱 실패 시에는 통과시켜 정상 작업을 막지 않는다.
- 메모리/커맨드(soft) 는 "무엇을 어떻게"를, 이 훅은 "언제 멈출지"만 강제한다.
"""
import sys
import json
import re

# 이번 턴 Bash command 문자열에서 탐지
PR_CREATE_RE = re.compile(r"gh\s+pr\s+create")
LABEL_RE = re.compile(r"--add-label|--label\b")


def is_real_user_message(ev):
    """tool_result 응답이 아닌, 실제 사용자 입력 메시지인지."""
    if ev.get("type") != "user":
        return False
    content = ev.get("message", {}).get("content")
    if isinstance(content, str):
        return True
    if isinstance(content, list):
        return not any(
            isinstance(b, dict) and b.get("type") == "tool_result" for b in content
        )
    return False


def main():
    try:
        data = json.load(sys.stdin)
    except Exception:
        sys.exit(0)  # 입력 파싱 실패 시 통과

    # 직전에 이미 한 번 막았으면 통과 (무한 루프 방지 — 라벨 없는 이슈 등)
    if data.get("stop_hook_active"):
        sys.exit(0)

    tpath = data.get("transcript_path")
    if not tpath:
        sys.exit(0)

    try:
        with open(tpath, encoding="utf-8") as f:
            lines = [json.loads(l) for l in f if l.strip()]
    except Exception:
        sys.exit(0)

    # 이번 턴 경계 = 마지막 '실제 사용자 메시지' 이후
    last_user = -1
    for i, ev in enumerate(lines):
        if is_real_user_message(ev):
            last_user = i
    turn = lines[last_user + 1:]

    pr_create = 0
    label = 0
    for ev in turn:
        if ev.get("type") != "assistant":
            continue
        for b in ev.get("message", {}).get("content", []):
            if not isinstance(b, dict) or b.get("type") != "tool_use":
                continue
            if b.get("name") != "Bash":
                continue
            cmd = (b.get("input") or {}).get("command", "") or ""
            if PR_CREATE_RE.search(cmd):
                pr_create += 1
            if LABEL_RE.search(cmd):
                label += 1

    if pr_create > 0 and label == 0:
        reason = (
            "이번 턴에 `gh pr create` 로 PR 을 생성했는데 라벨을 적용하지 않았습니다. "
            "PR 라벨은 연결 이슈(`closes #N`)의 라벨을 그대로 복사해야 합니다 (create-pr 절차 4): "
            "`gh pr edit <PR> --add-label \"$(gh issue view <N> --json labels "
            "--jq '.labels|map(.name)|join(\",\")')\"`. "
            "연결 이슈에 라벨이 없으면 이슈부터 라벨을 부여하세요. "
            "라벨 적용 후(또는 라벨 없는 이슈라 적용 불가면 그대로) 다시 종료하면 통과됩니다."
        )
        print(json.dumps({"decision": "block", "reason": reason}, ensure_ascii=False))

    sys.exit(0)


if __name__ == "__main__":
    main()
