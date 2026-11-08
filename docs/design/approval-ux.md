# Approval interaction proposal — awaiting visual approval

The user requested a CLI-like vertical choice list, keyboard control, readable long options, a custom answer and a decision about chat access. This proposal is separate from the already approved chat styling and is not implemented yet.

## Interaction

- Keep transcript scrolling and chat tabs available. Block only a new task in the same chat while the agent waits; preserve its draft.
- Show only choices actually supported by the request, as vertically stacked cards. Allow once is distinct from session-scoped permission. No automatic approval or timeout.
- Arrow keys move the focused option. Enter commits only an explicitly focused choice; focus movement alone never invokes it. Tab/Shift+Tab leave the option group normally.
- Do not steal focus while the user is typing. Announce the request and provide a route to focus it. Resolve navigation only inside the focused choice group, not globally in the IDE.
- Wrap complete choice labels and permission scope. A longer explanation or command/diff can expand in a bounded details area with a subtle scrollbar; never elide critical permission meaning.
- Escape returns focus without silently approving or rejecting a pending request. In custom text mode Enter adds a newline; Cmd/Ctrl+Enter or the labelled send action submits it.
- Choosing the custom branch reuses the composer in a clearly labelled request-answer mode, preserving the ordinary draft separately. Back to options preserves the custom text. No second competing textarea.
- For a permission request, custom text is not approval. Explicitly decline/cancel the requested action, then submit the instruction only when the protocol permits a new turn. For a structured agent question, return the supported free-text answer directly instead.
- Disappearance, reconnection or expiry invalidates the request, disables its actions and restores appropriate focus; never replay a consent decision automatically.

## Evidence and boundaries

[W3C keyboard conventions](https://www.w3.org/WAI/ARIA/apg/practices/keyboard-interface/) distinguish navigating a composite from activating a selection. [W3C alert guidance](https://www.w3.org/WAI/ARIA/apg/patterns/alert/) advises against moving keyboard focus for notifications. [W3C modal-dialog guidance](https://www.w3.org/WAI/ARIA/apg/patterns/dialog-modal/) explains that a modal makes the rest of the interface inert; this proposal deliberately uses a non-modal request card. [Apple alerts](https://developer.apple.com/design/human-interface-guidelines/alerts) supports concise information and clearly labelled actions.

The installed CLI schema was checked locally: command approvals support accept, acceptForSession, decline and cancel plus policy amendment objects; a structured request_user_input response instead uses answers keyed by question ID. Permission and free-text question responses must remain separate.
