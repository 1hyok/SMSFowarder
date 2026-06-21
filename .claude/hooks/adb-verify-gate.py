#!/usr/bin/env python3
"""ADB(mobile MCP) 시각검증 게이트 — Claude Code Stop 훅.

이번 턴에 Compose UI(.kt, app/src/main 아래 @Composable 포함 파일)를 편집했는데
mobile MCP 의 screen/ui 등 호출이 0회면 턴 종료를 차단(decision: block)하여
ADB 시각검증을 강제한다. feedback_use_adb_mcp_proactively 정책의 hard 강제 계층.

단일 :app 모듈 레이아웃에 맞춰 경로(presentation//core/ui/) 매처 대신 내용 기반
(@Composable 포함 여부)으로 Compose UI 편집을 판정한다.
※ 전제: mobile/adb MCP(mcp__mobile__*)가 연결돼 있어야 의미가 있다. 미연결 환경에선
   Compose 편집 턴마다 1회 막혔다가 재종료로 통과되므로(불필요한 잔소리), 안 쓸 거면
   settings.json 의 Stop 훅에서 이 스크립트 라인을 빼라.

설계 메모:
- 메모리/스킬(soft) 은 "무엇을 어떻게" 검증할지를 담고, 이 훅은 "언제 멈출지"만 강제한다.
- 한 번 막은 뒤(stop_hook_active) 에는 통과시켜 무한 루프를 방지한다.
- 입력/transcript 파싱 실패 시에는 통과시켜 정상 작업을 막지 않는다.
"""
import sys
import json

# 편집으로 간주할 도구
EDIT_TOOLS = {"Edit", "Write", "MultiEdit", "NotebookEdit"}


def is_compose_ui(fp):
    """Compose UI 편집 판정 (단일 모듈 레이아웃).

    app/src/main 아래 .kt 중 @Composable 을 포함한 파일이면 True.
    경로 패키지 구조에 의존하지 않으므로 ui/·화면 루트 어디에 있든 잡힌다.
    """
    if not fp.endswith(".kt"):
        return False
    if "/src/main/" not in fp or "/test/" in fp or "/androidTest/" in fp:
        return False
    try:
        with open(fp, encoding="utf-8") as f:
            return "@Composable" in f.read()
    except Exception:
        # 못 읽으면 경로 휴리스틱 fallback: ui/ 패키지 또는 Screen/Activity 파일.
        return "/ui/" in fp or fp.endswith("Screen.kt") or fp.endswith("Activity.kt")


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

    # 직전에 이미 한 번 막았으면 통과 (무한 루프 방지)
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

    compose_edits = 0
    mobile_calls = 0
    for ev in turn:
        if ev.get("type") != "assistant":
            continue
        for b in ev.get("message", {}).get("content", []):
            if not isinstance(b, dict) or b.get("type") != "tool_use":
                continue
            name = b.get("name", "")
            if name in EDIT_TOOLS:
                fp = (b.get("input") or {}).get("file_path", "") or ""
                if is_compose_ui(fp):
                    compose_edits += 1
            elif name.startswith("mcp__mobile__"):
                mobile_calls += 1

    if compose_edits > 0 and mobile_calls == 0:
        reason = (
            "이번 턴에 Compose UI(.kt) 를 편집했는데 ADB(mobile MCP) 시각검증이 0회입니다. "
            "mcp__mobile__ui(action='tree', compact=true) 로 최소 1회 검증하세요 "
            "(에뮬레이터 미실행이면 자동 부팅 후 진행). "
            "build.gradle·도메인·테스트만 바뀌었거나 시각 영향이 없다면 "
            "ADB 없이 그대로 다시 종료하면 통과됩니다."
        )
        print(json.dumps({"decision": "block", "reason": reason}, ensure_ascii=False))

    sys.exit(0)


if __name__ == "__main__":
    main()
