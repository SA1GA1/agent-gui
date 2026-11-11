# Clipboard and chat polish

Direct approved changes: screenshot Cmd+V, footer spacing, inactive-tab hover close, plugins under slash while at is files only, circular bounded activity ring and breathing room around code/prose.

1. Reproduce legacy TransferHandler paste with an image Transferable; share native clipboard/drop handling for both overloads and preserve copy/cut/plain text. Test local image clipboard without model requests.
2. Change plugin autocomplete and new plugin token labels to /, validate/persist those labels, retain backward-compatible old draft identities. Update web/native tests to ensure @ offers files only.
3. Inactive tabs show close on hover/focus; leave keyboard close accessible. Keep ring within circular button bounds and align footer top/bottom padding. Space code/prose boundaries in native and web.
4. Copy controls give temporary success feedback, never show an insertion caret, and retain selectable code.
5. Scope approval Up/Down/Enter to the visible chat, preserve custom replies and IME composition, and remove registration when the panel closes.
6. Run focused and full checks, inspect actual screenshot paste/UI in IDE, commit meaningful changes after current November11 first commit. Push existing authorization.

7. User follow-up: icon-only copy controls with a temporary checkmark; user message hover actions and HH:mm send times, persisted locally for new messages and taken from agent timestamps for imported messages. No fabricated historical times.

Validation: 69 native tests and 43 web tests passed; plugin packaged and verifier reported no compatibility problems. Native approval Up/Down was checked in Android Studio.
