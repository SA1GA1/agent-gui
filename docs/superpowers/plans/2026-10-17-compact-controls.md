# Compact chat controls

User explicitly requests direct implementation, without a mockup or approval pause.

1. Expire all notifications after 180 seconds with one next-expiry timer; preserve threshold suppression. Integer percentages throughout; hide stale text.
2. Native layout: content-sized rounded approval rows/command, no cwd line, separator before custom option; tighter message spacing; common system font. Adaptive smaller tabs with gaps, pinned right plus, circular close and hover-only scrolling. Composer placeholder instead of tooltip, rounded left paperclip, centered compact model picker and straight send arrow in one toolbar. Consistent plain footer controls.
3. Apply equivalent optional webview changes and test time-based/draft/layout behavior.
4. Full tests, package/compatibility verification, review, native visual QA, focused commits and authorized push. November 9 already has 4 commits; at most 4 additional commits on that date.

Ruling: use the OS regular UI font (macOS system font, with platform fallback), rather than bundling a new font. Keep existing checkout and use a feature branch; retain drafts and permission semantics.

## Completion ledger

- Notice expiry is owned by the existing service worker: one next-deadline task, no polling loop, with 180-second deadlines preserved through redraws. Web-only notices use cleaned-up one-shot timers.
- Native controls, adaptive tabs, rounded permission choices and compact spacing implemented; optional webview matched. Common regular UI font replaces mixed default type styles.
- Review found a web range-input keyboard/assistive-tech persistence gap; all non-pointer changes now commit through onChange while pointer drags retain live preview.
- Native launch exposed a close-button accessibility initialization error and an omitted renderer label assignment. Both reproduced in tests before fixing. The popup's option preferred heights were made consistent with their layout heights, and placeholder drawing moved to the platform's native empty-text component.
- Final verification and live expiry evidence are recorded in docs/VALIDATION.md.
