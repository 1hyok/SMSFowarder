[공식 문서 소스 라우팅 리마인더 — 지금 WebFetch/WebSearch 중이다. 소스는 평면적 우선순위가 아니라 **역할**로 구분 — "무엇을 찾느냐"로 분기해 질문 유형에 맞는 권위 소스를 쓸 것. 핵심 문서는 원문 확인하고, 답변·커밋 메시지에 출처 URL을 명시한다.]

### A. 라이브러리(AndroidX): 사용법·API·버전
1. **사용법·API·동작·마이그레이션** → developer.android.com (Architecture Guide, Library 문서, release notes). 단 release notes *상단 요약 박스*(Latest / Stable / RC / Beta / Alpha)는 stable 승격을 늦게 반영하는 경우가 있으므로 **"최신 버전 숫자"의 근거로는 쓰지 말 것**.
2. **최신 Stable 좌표 (버전 권위)** → Google Maven. 브라우징은 `maven.google.com`, 좌표 확정은 아티팩트별 메타데이터 `dl.google.com/dl/android/maven2/<group을 슬래시로>/<artifact>/maven-metadata.xml`(가볍고 기계 판독. 예: `…/maven2/androidx/navigation3/navigation3-runtime/maven-metadata.xml`). 핀 박기 전 stable 존재 여부는 여기서 확정.
    - 보조 — 빠른 신선도 스캔: AndroidX Atom 피드 `developer.android.com/feeds/androidx-release-notes.xml`. 발행 로그라 요약 박스보다 빠를 때가 많지만, 피드 자체도 지연될 수 있어 단독 권위는 아님. **※ 라이브러리 전용 — AGP/Gradle 릴리스는 여기 안 뜸 (→ C).**
    - fallback 미러(3rd-party, 캐시·지연 주의): androidx.tech 또는 mvnrepository.com 중 하나. Google Maven과 중복이므로 1차 접근 실패 시에만.
3. **API 시그니처·소스·커밋 단위** → android.googlesource.com / AndroidX GitHub. 버전 체크용이 아니며 필요 시에만.

### B. 언어(Kotlin) → kotlinlang.org
- 별도 벤더(JetBrains)·릴리스 주기·버전 권위. 문법·표준 라이브러리·coroutines/Flow는 여기가 1차 (developer.android.com 아님).
- 버전 좌표는 A-2 트릭 안 먹힘 → `kotlinlang.org/docs` 또는 GitHub `JetBrains/kotlin` releases에서 확정.

### C. 빌드 툴체인(AGP / Gradle / Studio / JDK / Build Tools / API level) → developer.android.com/build/releases
- AGP "좌표"는 A-2 트릭 먹힘: `dl.google.com/.../com/android/tools/build/gradle/maven-metadata.xml`.
- 단, 핵심인 `AGP ↔ Gradle ↔ Studio ↔ JDK ↔ compileSdk` 호환 **행렬**은 메타데이터에 없음 → `…/build/releases/about-agp`(API level별 Studio/AGP 최소 버전 표) + 버전별 release notes가 권위.
- Gradle 쪽 행렬: `docs.gradle.org/current/userguide/compatibility.html`.
- A의 "stable 핀 박기"로는 안 잡히는 "내 빌드가 이걸 빌드할 수 있나"가 여기.

### D. 디자인(Material 3) → m3.material.io
- 가이드라인·디자인 토큰·컴포넌트 권위 (API 문서와 별개 축). 현재 M3 Expressive.
- I/O 2026부터 "Material Android = Compose-first". MDC-Android(Views)는 유지보수 모드 → 새 작업은 Compose Material3 기준.

### E. 알려진 버그 → issuetracker.google.com (공개 Google Issue Tracker)
- 핀 박은 버전 오동작 시 "알려진 버그냐 / 픽스 예정이냐"의 답은 release notes 아니라 여기. A의 버전관리와 짝.

### F. (선택) 정준 패턴·샘플 → github.com/android
- 버전·API 아니라 "지금 권장되는 구조"용. Now in Android(`android/nowinandroid`), `architecture-samples`, `compose-samples`. Architecture Guide의 코드화 버전.

### 횡단 주의 — Compose ↔ Kotlin (A-1·A-2에 적용)
- Kotlin 2.0+: Compose 컴파일러 버전 = Kotlin 버전. Compose Compiler Gradle plugin(`org.jetbrains.kotlin.plugin.compose`, version = Kotlin 버전)으로 적용.
- 따라서 "Compose to Kotlin Compatibility Map"(`…/jetpack/androidx/releases/compose-kotlin`)은 **1.9 이하 전용 legacy** — 2.0+에선 볼 필요 없음. `kotlinCompilerExtensionVersion` 따로 핀 박지 말 것.
