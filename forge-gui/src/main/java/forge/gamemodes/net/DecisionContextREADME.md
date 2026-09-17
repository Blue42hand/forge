# DecisionContext network extension

`DecisionContext` is a host-to-client metadata envelope for external/headless pilots. It augments Forge's existing GUI/controller network protocol and does not replace it.

The envelope is versioned (`schemaVersion`), identifies the current native input (`inputType`, stable `inputToken`) and carries a monotonically increasing publication `sequence`. Optional typed subcontexts expose only facts owned by that native input: selection bounds/current selections, native mana-payment state, and priority actionability.

The host builds the context from `Input` and deciding-player state. It does not parse prompt strings and does not scan opponent hidden zones. Normal Forge clients consume and ignore the event; headless clients can read the latest context from `FGameClient#getLatestDecisionContext()` (or `HeadlessDecisionContext.latest`).

External pilots should treat `(seat, inputToken)` as the decision identity and `sequence` as the freshness marker. Responses against a no-longer-current token should be rejected by the pilot/controller layer rather than replayed against a newer Forge input.
