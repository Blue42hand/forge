package forge.gamemodes.net.client;

import forge.gamemodes.net.DecisionContext;

/** Small convenience facade for callers that only need the latest authoritative decision metadata. */
public final class HeadlessDecisionContext {
    private HeadlessDecisionContext() {}

    public static DecisionContext latest(final HeadlessNetworkClient client) {
        if (client == null || client.getClient() == null) {
            return null;
        }
        return client.getClient().getLatestDecisionContext();
    }
}
