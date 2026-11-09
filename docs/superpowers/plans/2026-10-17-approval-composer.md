# Approved approval composer implementation

Spec: docs/design/approval-ux.md and the user's final approved mockup corrections.

1. Add protocol decision validation and custom-reply lifecycle. Only supported session permission choices; custom replies cancel the pending action and start a text-only turn after completion, preserving draft/attachments. Reject stale IDs and never replay after reconnect.
2. Native approval rows attached to the composer. Wrapped labels, color selection only, local arrows/Enter, Esc returns from custom mode, keep rows visible, compact centered chevron and bounded command details. Brighter warnings. Preserve per-request custom text without overwriting ordinary drafts.
3. Optional webview uses equivalent behavior and appearance. Test keyboard selection, request invalidation and ordinary draft restoration.
4. Run Kotlin/web tests, build and compatibility check, review, commit in focused increments and reopen the sandbox.

Ruling: implement in the existing checkout on a feature branch so the current sandbox paths remain stable. User already authorized commits/pushes. New commits start on November 9 because November 8 has reached eight commits.

## Completion ledger

- Task 1 complete: validated permission decisions; deferred custom text released once after the matching turn completion. Tests first failed on missing implementation, then passed.
- Task 2 complete: attached native menu, borderless wrapped choices, local keyboard navigation, flat chevron, brighter triangular warning and separately stored custom drafts.
- Task 3 complete: equivalent optional webview. A chat-switch regression failed with `ordinary A` replacing `custom A`, then passed after coordinating restoration with the saved request draft.
- Task 4 complete: 55 tests green, clean package and compatibility verification passed. Isolated native JSONL fixture confirmed cancellation, one text-only follow-up and restored ordinary draft. Independent review's three issues fixed and re-reviewed.
- Integration follows the user's existing authorization to commit and periodically push this repository. No new approval step is needed for the already approved design.
