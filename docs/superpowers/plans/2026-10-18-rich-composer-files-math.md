# Rich composer, attachments and formulas implementation plan

> Execution: superpowers:executing-plans, inline in this chat; direct user-approved UI requirements, no mockup.

**Goal:** Ten unrestricted-format attachments, readable local formulas, atomic blue inline mentions, seven composer rows and centered model picker.
**Architecture:** Native Swing remains the default. A styled composer holds metadata-backed mention ranges, persisted alongside each draft; the optional web editor uses noneditable inline spans. Attachments retain immutable snapshots and bounded text previews; binary originals are provided as local file references to the CLI, whose installed input schema has no generic file input. Math is rendered locally with JLaTeXMath and KaTeX, with safe malformed-formula fallback and no remote assets.

- [x] AttachmentService/SessionService/UiBridge and web droppedFiles: count10, accept any file extension, preserve binary snapshots, bounded text extraction and full local path, consistent upload limits. Test unchanged snapshots, binary routing, oversized inputs and traversal.
- [x] SafeMarkdown/NativeTranscript + web MessageList: protect math delimiters from Markdown; local formula rendering, bounded cache, code exclusion, invalid fallback. Test supplied fractions and inline math/code boundaries.
- [x] Native styled editor + web rich editor: blue atomic /skill and @plugin/@file inline tokens, preserve surrounding text/caret, delete as units, multiple identities, no stale metadata on deletion, draft switching and custom approval restoration. Tests for edits, offsets and exact submitted identities.
- [x] Seven visual rows then scroll, center model popover with screen bounds. Test sizing and native visual review.
- [x] Run full tests, package/verifier and focused review; inspect in IDE. Commit medium increments chronologically, November10 currently4commits so at most4more before moving toNovember11. Push authorized main updates and reopen ordinary runtime project without changing user drafts.

Final rulings: arbitrary files use immutable local snapshot references because the installed CLI input schema has no generic document input. Each file remains capped at10MB and text previews at128KB. Document cards narrowed to132px with extensions only below names; viewport painting now intersects its inherited clip. Final verification:61Kotlin+41web tests, compatible plugin verifier, native fixture QA. Four commits complete November10 at8 total commits; any later work moves toNovember11.
