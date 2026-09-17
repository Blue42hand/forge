package forge.gamemodes.net.event;

import forge.gamemodes.net.DecisionContext;

/** Host-to-client metadata for the authoritative native input awaiting a response. */
public final class DecisionContextEvent implements NetEvent {
    private static final long serialVersionUID = 1L;

    private final DecisionContext context;

    public DecisionContextEvent(final DecisionContext context) {
        this.context = context;
    }

    public DecisionContext getContext() {
        return context;
    }
}
