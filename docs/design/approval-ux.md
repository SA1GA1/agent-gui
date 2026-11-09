# Approved approval interaction

The user requested a CLI-like vertical choice list, keyboard control, readable long options, a custom answer and a decision about chat access. The final mockup is approved for implementation, including keeping options visible while typing a custom reply.

## Interaction

- Keep transcript scrolling and chat tabs available. Block only a new task in the same chat while the agent waits; preserve its draft.
- Show only choices actually supported by the request, as vertically stacked borderless rows. Allow once is distinct from session-scoped permission. No automatic approval or timeout.
- Arrow keys move the focused option. Enter commits only an explicitly focused choice; focus movement alone never invokes it. Tab/Shift+Tab leave the option group normally.
- Do not steal focus while the user is typing. Announce the request and provide a route to focus it. Resolve navigation only inside the focused choice group, not globally in the IDE.
- Wrap complete choice labels and permission scope. A longer explanation or command/diff can expand in a bounded details area with a subtle scrollbar; never elide critical permission meaning.
- Escape returns focus without silently approving or rejecting a pending request. The normal composer keeps Enter to send and Shift+Enter for a newline, including IME safeguards.
- Choosing the custom branch reuses the composer attached directly beneath the still-visible request choices, preserving the ordinary draft separately. Escape returns to options and preserves the custom text; there is no Back button. No second competing textarea.
- For a permission request, custom text is not approval. Explicitly decline/cancel the requested action, then submit the instruction only when the protocol permits a new turn. Structured agent question forms use a separate protocol and remain outside this permission UI implementation.
- Disappearance, reconnection or expiry invalidates the request, disables its actions and restores appropriate focus; never replay a consent decision automatically.

## Evidence and boundaries

[W3C keyboard conventions](https://www.w3.org/WAI/ARIA/apg/practices/keyboard-interface/) distinguish navigating a composite from activating a selection. [W3C alert guidance](https://www.w3.org/WAI/ARIA/apg/patterns/alert/) advises against moving keyboard focus for notifications. [W3C modal-dialog guidance](https://www.w3.org/WAI/ARIA/apg/patterns/dialog-modal/) explains that a modal makes the rest of the interface inert; this proposal deliberately uses a non-modal request card. [Apple alerts](https://developer.apple.com/design/human-interface-guidelines/alerts) supports concise information and clearly labelled actions.

The installed CLI schema was checked locally: command approvals support accept, acceptForSession, decline and cancel plus policy amendment objects; a structured request_user_input response instead uses answers keyed by question ID. Permission and free-text question responses must remain separate.

## Final visual corrections

- Keep all choices visible while composing a custom reply. Use only selection color, without a leading marker or an enclosing rectangle around the list.
- The request menu is attached to the single ordinary chat composer. No second input or focus-color highlight.
- Use a short, flat, centered down-chevron for command expansion, no Details label. Bound long reasons, commands and options with subtle scrolling.
- Keep the transcript and tabs accessible, avoid helper prose that is absent from the CLI.
- Use brighter amber notices and a yellow triangular warning icon.
