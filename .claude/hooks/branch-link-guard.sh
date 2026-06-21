#!/usr/bin/env bash
# PreToolUse Bash hook — 무력화됨 (2026-06-03).
#
# 원래 목적: `gh pr create` 시 feat/<N> 브랜치가 이슈에 createLinkedBranch 로 연결됐는지 검증.
#
# 무력화 이유: createLinkedBranch 는 *새 브랜치 생성* 전용 mutation 이다. git checkout -b 로
#   만들어 push 한 브랜치는 이미 origin 에 존재하므로, 그 이름으로 createLinkedBranch 를 호출하면
#   errors 없이 linkedBranch:null 을 조용히 반환한다(= 실패). 즉 PR 생성 시점엔 브랜치가 항상
#   push 된 뒤라 linkedBranch 를 만들 방법이 원천적으로 없다. "PR 시 자동 link 백스톱"은 불가능.
#
# 이슈-PR 연결은 PR 본문의 close 키워드(`closed #N`)로 추적한다 — create-pr skill 이 자동 삽입하며,
#   머지 시 이슈 auto-close + 이슈 Development 섹션에 PR 이 표시된다. linkedBranch(브랜치 자체
#   연결)는 GitHub 가 사후 지원하지 않으므로 포기.
#
# 향후 진짜 linkedBranch 가 필요하면: git checkout -b 대신 이슈에서 createLinkedBranch 로
#   브랜치를 *생성*(origin first)한 뒤 fetch + checkout 하는 워크플로우로 전환해야 한다.
exit 0
