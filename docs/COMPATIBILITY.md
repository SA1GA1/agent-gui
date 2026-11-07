# Compatibility baseline

Observed on 2026-10-17, macOS ARM64.

| Component | Baseline |
|---|---|
| Android Studio | AI-261.25134.95.2612.15822958 |
| IDE runtime | bundled JBR 21.0.10; JCEF present in runtime |
| Codex CLI | 0.163.0 |
| Gradle | 9.5.0 wrapper |
| Kotlin | 2.2.20; Java bytecode target 21 |

Protocol source: `codex app-server generate-json-schema --out <temporary-directory>`.
Selected schema snapshots are committed beside this document in `protocol/`.

Stable methods used: initialize + initialized, account/read, account/login/start,
model/list, thread/start, thread/list, thread/resume, thread/name/set,
thread/archive, turn/start, turn/interrupt, thread/compact/start, skills/list,
mcpServerStatus/list. Unsupported server requests are explicitly rejected.
No terminal output parsing, private token exchange or direct API key storage.

Usage calculation: `tokenUsage.last.totalTokens / modelContextWindow * 100`.
`total` is cumulative billing usage and MUST NOT be used as occupancy.

Sources:
- https://developers.openai.com/codex/app-server/
- https://plugins.jetbrains.com/docs/intellij/embedded-browser-jcef.html
- https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html

Packaging, runtime smoke tests, permissions, image input, compaction and resource
measurements have separate evidence in IMPLEMENTATION.md; schema presence alone
is not runtime validation. Other platforms and IDE build lines are unverified.
