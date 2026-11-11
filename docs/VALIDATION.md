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
- Subsequent browser-reference refinements add a filled active chat tab, thin inactive separators, an active-tab close action, blue user bubbles limited to 72% width, consistent message padding and a round reasoning-slider thumb. The permission approval redesign is implemented and validated below.

## Approved permission composer — 2026-10-17

- 33 Kotlin and 22 webview tests pass. New cases cover session decisions, stale thread/turn rejection, custom text requiring cancellation, exactly-once deferred reply release, independent drafts, explicit keyboard activation, bounded reasons, visible choices and A → B → A custom-draft restoration.
- Clean test/buildPlugin/verifyPlugin/prepareSandbox succeeded; the verifier remains compatible with the installed Android Studio build (the same four deprecated and six experimental API usages).
- Native IDE end-to-end test used an isolated local JSONL fixture, without real command execution or model inference. The visible menu stayed attached to one composer, all three options remained visible during custom editing, Escape restored the ordinary draft, and returning restored the custom answer. Sending produced exactly one `cancel` response followed by one text-only `turn/start`; the ordinary draft returned after submission.
- An independent review identified and verified fixes for web chat-switch draft overwrite, native custom-mode autocomplete and unbounded approval reasons. Follow-up review found no additional actionable issues.
- A first aggregate run found duplicate generated `ApprovalFlowTest 2.class` files in build outputs. Clean regeneration removed those artifacts; the full clean build and subsequent final test run passed. No source file was removed to suppress a test.
- This implements command/file permission approvals. Arbitrary structured question forms and JCEF manual testing remain pending. The local fixture verifies protocol routing, not every production backend approval variation.

## Compact controls — 2026-10-18

- 37 Kotlin tests and 25 webview tests cover the current pass. Added three-minute deadline, adaptive tab widths, regular font, selected-tab close construction, actual approval label rendering, toolbar placement/placeholder, transient web notices, integer context and non-pointer reasoning changes.
- Native IDE visual QA used eight fixture chats: tabs shrink to their minimum then scroll, the plus remains pinned at the right, and the horizontal thumb appears over the tab strip. The approval command and selected option are rounded; the cwd row is absent and a fine separator precedes the custom option. The composer contains attachment, model and send in one row; the footer controls use a common size without a quota border or arrow.
- Actual native notification expiry was observed: a quota warning present after connection disappeared automatically after 180 seconds, without pressing dismiss or starting another chat task. Expiry uses one next-deadline task on the existing worker. Permission requests are not notifications and do not expire or approve themselves.
- The native reasoning increment changed Medium to High and persisted it. Web tests cover range changes without pointer/key events so PageUp/PageDown and assistive input do not lose a selection.
- Visual QA found and fixed an anonymous close-button accessibility initialization error and omitted text in the rounded approval renderer. Each failure was reproduced by a test before correction.
- The native model menu uses matching preferred/layout row heights; native empty-text rendering replaces manual placeholder painting. The system UI font is used with regular weight, with no additional font download.
- Optional webview tests/build passed. JCEF manual QA and quantitative resource measurements remain pending; no resource budget is claimed.

## Interaction polish and attachment cards — 2026-10-18

- 41 Kotlin and 28 webview tests pass. Actual native HTML glyph family, size and weight match the composer; the assistant message has no gray card. Picker tests cover selection refresh, changing effort ranges and committing after drag release. Web coverage includes keeping the model menu open, independent blue mention-chip removal, document extension labels and image previews.
- Clean test/buildPlugin/verifyPlugin/prepareSandbox succeeded; a subsequent complete build after the popup lifecycle fix passed. The verifier remains compatible with the installed Android Studio build, with the same four deprecated and six experimental API usages.
- Native IDE QA confirmed that selecting GPT-6 Astra keeps the menu open with its checkmark updated. Dragging reasoning from Medium to Max repainted the full track cleanly. Endpoint markers are inset, tab gaps are halved and separators appear beside the active tab too.
- Native history search has no focus glow, with a rounded selected prompt. A seven-line draft displays five visual lines with vertical scrolling. Footer history/quota controls match the context text and occupy a compact row.
- Document cards show a file icon, truncated name and uppercase extension; images use 72-pixel square cropped thumbnails. Six fixture attachments stayed in one row and scrolled horizontally. Existing cached 192-pixel previews are reused; no full-image reload or background animation loop was introduced. This changes attachment presentation, not the set of supported file formats (UTF-8 text and images).
- Closing the persistent model menu initially exposed a disposed-popup content access on the next state update. Native QA reproduced it; visible/disposed guards and identity-safe onClosed cleanup fixed it. Repeated close, history selection and subsequent draft updates produced no new popup exception.
- Independent focused review found no further important issues in picker synchronization, scroll timer lifecycle, attachment sizing/removal or popup cleanup. Skill/plugin chip selection and removal passed web tests; native automation did not successfully exercise that selection in this pass. Optional JCEF manual QA and resource profiling remain pending.

## Rich composer, arbitrary attachments and formulas — 2026-10-18

- 61 Kotlin and 41 webview tests pass. Full test/buildPlugin/verifyPlugin/prepareSandbox succeeds; verifier reports compatibility with the installed Android Studio build, with the same four deprecated and six experimental API usages.
- Attachments accept any regular-file format, up to ten files and 10 MB per file. Immutable snapshots retain original bytes. Recognized UTF-8 text includes a bounded 128 KB preview and an explicit truncation note; PDF/Office/archive/other binary formats are supplied as local snapshot references for CLI tools, not native document-model inputs. Images retain native image routing. Local uploads validate actual bytes rather than trusting extensions/MIME.
- Native IDE fixture QA selected ten files together (PDF, DOCX, XLSX, Markdown and six binary files). All ten rendered and were sent as ten distinct snapshot references, with binary bytes excluded from inline text. No actual model inference or file execution occurred in this fixture.
- The native composer now uses styled atomic inline references; metadata moves with edits and compound undo. Both editors persist multiple selected identities alongside draft text, validate complete label ranges before sending, and resolve identities against enabled catalogs. Native QA selected /review and @plugin in one prompt; the outgoing turn contained exactly one skill and one plugin mention with their correct paths. Backspace removed one complete mention and undo restored its identity.
- Both composers grow to seven visual lines before scrolling; the native picker centers above its model control and clamps to the containing window. Web tests cover cross-chat undo isolation, approval draft restoration, canonical multiline caret offsets, atomic deletion and acknowledgement-based clearing after an immediately completed turn.
- Native JLaTeXMath and optional web KaTeX render inline/display formulas locally, preserving code and currency text. The provided transfer function rendered as stacked fractions and powers in the native IDE. Unsupported native TeX remains readable source. Math rendering rejects file-loading/unsafe macros, bounds formula size/depth and caches up to128 icons. Packaged web fonts are embedded data resources allowed explicitly by CSP.
- Native accessibility inspection initially exposed an ImageView cast mismatch; a regression reproduced it and the math view now inherits ImageView with remote URL loading disabled. Native accessibility subsequently read formula descriptions without exceptions. Review also removed quadratic remaining-response copies from the Markdown scanner.
- Attachment cards are132 px wide (images72 px square), with extension only below the base name. A regression reproduced drawing outside a scrolled viewport; intersecting the inherited graphics clip fixes the first-card edge. Native visual QA confirmed clean card boundaries and antialiased placeholder text.
- Final independent review found no further important issues after correcting cross-chat undo, multiline caret and scanner allocation findings. Optional JCEF manual interaction testing and quantitative resource profiling remain pending; binary references require the agent's available local file tools to interpret their contents.
- Follow-up attachment affordance: reduced document cards to120px so the tested440px tool window shows three full cards plus the start of a fourth. Native visual QA confirmed the next-card edge remains inside the viewport. Production web/native packaging passed; this width-only follow-up does not change attachment routing or the102 passing behavior tests above.
