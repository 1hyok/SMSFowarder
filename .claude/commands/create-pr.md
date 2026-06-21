---
description: PR 생성 — 팀 양식(유니코드 헤더 4섹션) + 제목 규칙 + 빌드 검증
---

현재 브랜치 변경으로 PR 을 생성한다. 이 command 호출 자체가 명시 지시이므로 진행하되, 아래 게이트·양식을 지킨다.

## 절차
1. `git fetch` 후 현재 브랜치가 origin 에 push 됐는지 확인 (`git log origin/<branch>..HEAD` 0건). unpushed 있으면 멈추고 사용자에게 push 요청 (자율 push 금지).
2. 연결 이슈 번호 확인. 변경 모듈 빌드 검증 `./gradlew :<module>:compileDebugKotlin` (+필요시 ktlint) — 결과를 💬 To Reviewers 에 기재.
3. `gh pr create --base master --title "<제목>" --body "<본문>"`.
4. **연결 이슈 라벨을 PR 로 복사** — `gh pr edit <PR> --add-label "$(gh issue view <N> --json labels --jq '.labels | map(.name) | join(",")')"`. `closes` 가 복수면 각 이슈 라벨의 합집합. 이슈에 라벨이 없으면 skip. (PR 라벨은 연결 이슈 라벨을 그대로 따른다.)
5. 채팅엔 **PR URL + 한 줄 요약만**. 본문 텍스트를 채팅에 재출력하지 말 것 (GitHub 에서 보이므로 중복).

## 제목
Conventional commit 스타일 + 끝에 이슈 번호를 **`#` 없이** `(closes NNN)`:
- OK: `fix(home): 수신자 홈 Hero senderName 매핑 (closes 164)`
- X: `... (#164)` — `#` 접두는 PR/이슈 번호 혼동이라 금지
- X: 이슈 번호 생략 — issue-first 라 거의 모든 PR 이 이슈 매핑 (무관 hotfix 만 예외)
- 복수: `(closes 220, 221)`

## 본문 (정확히 이대로 — `## ` H2 + 이모지 + 유니코드 italic 글자)

```
## 📌𝘐𝘴𝘴𝘶𝘦𝘴

Closes #<이슈번호> — <이슈 제목 그대로>

## 📎𝘞𝘰𝘳𝘬 𝘋𝘦𝘴𝘤𝘳𝘪𝘱𝘵𝘪𝘰𝘯

- 한 줄 변경 요약 (커밋 해시 동봉 가능)
- ...

## 📷𝘚𝘤𝘳𝘦𝘦𝘯𝘴𝘩𝘰𝘵

스크린샷 또는 "UI 변경 없음 / Compose Preview 검증" 사유

## 💬𝘛𝘰 𝘙𝘦𝘷𝘪𝘦𝘸𝘦𝘳𝘴

- 알려둘 점 (의도된 차이 / 서버 협의 / 후속 작업 / stack base 의존)
- 빌드 검증: `./gradlew :<module>:compileDebugKotlin` BUILD SUCCESSFUL
```

## 주의
- `## ` H2 필수 — GitHub 가 H2 아래 섹션 경계선(underline)을 그림. 이모지+유니코드 글자 셋 다 한 묶음. 과거 `## ` 빼서 경계선 사라진 회귀 있었음.
- 본문은 **평문** — 줄을 `_..._` italic 으로 감싸지 말 것. 강조는 정말 필요한 단어만 `**bold**`. (헤더가 italic 처럼 보이는 건 유니코드 글자 형태일 뿐.)
- 본문 📌 첫 줄은 **`Closes #<번호>`** 로 시작 — close 키워드와 `#번호`가 **인접**해야 머지 시 auto-close 발동한다. `closed <제목> #번호`처럼 사이에 제목이 끼면 GitHub 가 못 잡는다(2026-06-03 #387/#389 실측 실패, 같은 양식의 #388만 우연히 잡힘). 제목은 ` — ` 뒤에 붙인다. (제목의 `closes NNN`(# 없음, 괄호 안)은 미발동이라 본문 키워드 필수.)
- 📷 섹션 비우지 말 것 — 없으면 "UI 변경 없음" 등 사유.
- **라벨 = 연결 이슈 라벨 복사** (절차 4) — PR 라벨은 `closes` 대상 이슈의 라벨을 그대로 물려받는다. 이슈에 없는 라벨을 추가로 붙이는 건 사용자가 "X 라벨 붙여" 명시할 때만. PR 에 라벨이 비면 Stop hook 이 차단한다.
