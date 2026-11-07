# Implementation ledger

Source: docs/superpowers/plans/2026-10-17-codex-studio.md (approved in the linked conversation).

- Preflight: empty working directory; remote repository has no refs. No existing code or history to preserve.
- Ruling: use this user-selected directory and a feature branch instead of another checkout — fresh repository; cost: no separate checkout for integration.
- Ruling: pin first compatibility target to installed Android Studio 261.25134.95.2612.15822958 / JBR 21 and Codex 0.163.0 — measured locally; cost: other IDE/CLI versions require validation.
- Backend receives JSONL over stdio, webview receives only allowlisted UI actions. No credentials in webview.
- Baseline tasks: scaffold/build, RPC/process, JCEF bridge/chat, attachments/commands, sessions/history, context/Git, release validation.
- Resource policy: one lazy App Server and JCEF instance per project; no model inference in plugin, no project indexing at startup; two active turns maximum. JCEF has an unavoidable memory overhead over CLI.
- Runtime/manual gates stay pending until actually observed; packaging does not imply Marketplace readiness.
