# Implementation ledger

Source: docs/superpowers/plans/2026-10-17-codex-studio.md (approved in the linked conversation).

- Preflight: empty working directory; remote repository has no refs. No existing code or history to preserve.
- Ruling: use this user-selected directory and a feature branch instead of another checkout — fresh repository; cost: no separate checkout for integration.
- Ruling: pin first compatibility target to installed Android Studio 261.25134.95.2612.15822958 / JBR 21 and Codex 0.163.0 — measured locally; cost: other IDE/CLI versions require validation.
- Backend receives JSONL over stdio, webview receives only allowlisted UI actions. No credentials in webview.
- Baseline tasks: scaffold/build, RPC/process, JCEF bridge/chat, attachments/commands, sessions/history, context/Git, release validation.
- Resource policy: one lazy App Server and JCEF instance per project; no model inference in plugin, no project indexing at startup; two active turns maximum. JCEF has an unavoidable memory overhead over CLI.
- Runtime/manual gates stay pending until actually observed; packaging does not imply Marketplace readiness.

## Accepted updates during implementation

- User prioritizes low resource use and wants functionality/optimization before UI/UX.
- Native Swing is now the default after runtime verification found JCEF absent. Optional React view remains packaged. Ruling: adapt the initial JCEF architecture rather than replace the user's runtime — cost: maintain two UI adapters and less initial polish in native view.
- Add context notifications at 50/75/90%, quota remaining notices at 50/30/10%, and a five-hour quota button with a five-hour/weekly reset dialog.
- No manual subscription expiration date: backend schema has no authoritative expiry field; no fabricated expiry notices.
- User explicitly requests a silent minimal request after a five-hour reset, idle-only. It defaults enabled, can be disabled, uses a separate read-only ephemeral thread and reserves each known quota cycle across IDE projects. It cannot see work in external applications.
- User explicitly authorizes periodic pushes. This initial alpha is prepared for the previously empty GitHub repository.

## Progress

- Bootstrap, protocol client and process lifecycle implemented; selected CLI schema committed.
- Chat/session backend, stable attachments, commands, model selection, skills and Git/context implemented.
- Native panel and optional safe web bridge implemented. Current native panel tested against real Codex through an isolated IDE project.
- Quota notices and silent window-warmup policy implemented; real five-hour transition remains pending.
- Source and web tests, buildPlugin and Plugin Verifier pass; exact evidence and remaining manual checks are in VALIDATION.md.
- No claim of full seven-stage plan completion: see VALIDATION.md pending section.

## Review notes

Fixed important findings are listed in VALIDATION.md. Deferred minors: retention cleanup for already-sent attachment copies (needed by history), full history-message pagination, native code-copy button, richer action output streaming, context-window recalculation for a selected model before fresh usage arrives (shown stale meanwhile), and quantitative memory profiling. Closed-chat serialization was reduced; native rendering is bounded to a tail by default; warmup threads unsubscribe after completion.

## Commit schedule

The user clarified before the first push that commits must span multiple future days. The unpublished local metadata was redistributed, preserving the exact source tree: four commits on 2026-11-07 and four on 2026-11-08, author and committer timestamps both +03:00. A local backup ref retains the earlier unpublished dates; it is not pushed. No published history was rewritten.
