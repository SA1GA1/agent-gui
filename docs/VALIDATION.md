# Verification evidence — 2026-10-17

## Passed

- `npm test --prefix webview`: 19 UI/logic tests. Keyboard handling, duplicate Enter, slash selection, context warning, text drops, two quota windows and unknown quota display.
- `./gradlew test`: 28 Kotlin tests. JSONL chunking, out-of-order results, error/closure, approval IDs; context thresholds and last usage; Git branch changes/detached/no-Git; safe Markdown; notification thresholds; idle/reset reservation and competing projects; UTF-8 attachment snapshots, deleted source, binary/size/path rejection and bounded thumbnail dimensions.
- `./gradlew buildPlugin`: installable ZIP at `build/distributions/agent-gui-0.1.0.zip`.
- `./gradlew verifyPlugin`: compatible with AI-261.25134.95.2612.15822958. Four deprecated interface usages and six experimental platform usages remain; no compatibility errors. The scheduled-for-removal JCEF query overload was replaced.
- `npm audit`: no known vulnerabilities in the installed dependency tree after updating the build/test tools.
- Read-only real Codex handshake: initialize/initialized, model/list returned seven models, existing account/read succeeded.
- `scripts/live-smoke.py --image`: generated red PNG reached the model via localImage with a space-containing path and was identified as red; real turn/start, agent message streaming, turn/completed, token usage notification; second turn interrupted and reported interrupted.
- Native /compact completed: Stop disabled again, state returned to idle, context fell from 5.6% to 4.1%. Completion was observed after server events, not inferred from the initial RPC result.
- Latest native quota dialog manually verified: both 5-hour and weekly remaining percentages and actual reset timestamps appeared in a small window; no dates were invented.
- IDE restart manually verified: the existing real thread resumed and GUI_OK remained in history without resending the prompt.
- Native panel in an isolated Android Studio project: loaded without JCEF, connected to real Codex, populated models/reasoning/skills, displayed actual Git branch and actual five-hour/weekly quota values, sent a short message and rendered `GUI_OK` with a nonzero context percentage.

## Runtime findings and fixes

The installed Android Studio JBR does not include JCEF. The initial browser panel correctly refused to load; native Swing is now the default, optional webview remains available for a suitable runtime. The fallback text suggesting bundled JBR alone would fix this was removed.

One repeated test IDE launch overlapped shutdown and produced DirectoryLock/Start Failed. Both test processes were closed; the failed launch dialog was dismissed using Close, without resetting settings/plugins. Later tests used a separate system directory. This was a test-launch collision, not a defect in the user's project.

Independent review found and fixed: automatic compaction releasing a live turn; overriding ordinary chat security policies; manual compaction bypassing the two-operation budget; dropped text files ignored; @ attaching stale disk contents; stale activity after warmup errors; quick-switch draft loss; quota-pool mixing; a race between idle reservation and hidden inference. Additional fixes bound attachment previews and only publish active-chat messages.

## Pending — do not present as completed

- Observe an actual five-hour quota rollover followed by the silent request and a real new backend reset timestamp. Policy/coordinator tests cover timing and gating, not billing-server semantics.
- Complete long-session tests with two simultaneously active chats, process crashes, project close/reopen and a large history.
- Exercise every native attachment route, IME, approval flow and history pagination end to end in the installed IDE. Backend attachment tests do not prove the model saw an image.
- Quantitatively measure incremental memory/CPU with panel closed, idle, one and two active chats. The observed process RSS during an IDE startup is not an isolated plugin-memory benchmark; no resource budget is claimed yet.
- JCEF runtime/manual QA, Windows/Linux and other Android Studio build lines.
- Marketplace signing, public release screenshots and publication.

## Current scope

This is a functional alpha, not completion of every item in the seven-stage source plan. General plugins/MCP installation UI, arbitrary structured server forms, continuous transcript pagination and final resource audit remain outside this alpha. The approved chat UI is now implemented in native Swing and the optional webview: outlined tabs, top notifications, adaptive composer, model/reasoning row, prompt-history and usage popovers. Native animations run only during transitions or visible activity; idle state publication is event driven.

## Approved chat UI pass

- Strict context colors: >50% warning, >75% critical; compact actions appear in threshold notices at 50/75.
- Prompt-history navigation includes paginated server history and retains a bounded live tail after revealing an old turn. Superseded scans cancel between pages; UI search filters cached prompts.
- Installed/enabled plugins are discovered from the actual CLI and validated before mention submission.
- 28 Kotlin tests plus 19 webview tests cover the new navigation, safe rendering/copy links, button initialization, thresholds, stale response guards and separate conversation resumption.
- Read-only protocol smoke also exercised plugin/list and thread/items/list (30 installed/enabled plugins and a persisted 3-item thread in the test account).
- Close the sandbox before any Gradle rebuild: sandbox artifacts may share loaded JAR files with build outputs. Replacing those files while the IDE is running can produce transient ZIP/class-loading errors, independent of the packaged plugin.

- Final native visual review confirmed: compact triangle warning with far-right dismiss, rounded message cards, one model/reasoning picker, live usage meters and reset times. Independent read-only review found no additional significant issues in bounded transcript caching, history reveal, tab callbacks or animation lifecycle.
- Subsequent browser-reference refinements add a filled active chat tab, thin inactive separators, an active-tab close action, blue user bubbles limited to 72% width, consistent message padding and a round reasoning-slider thumb. Approval redesign remains a proposal, not completed functionality.
