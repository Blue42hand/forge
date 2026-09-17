package forge.gamemodes.net.client;

import forge.gamemodes.match.GameLobby.GameLobbyData;
import forge.gamemodes.net.ChatMessage;
import forge.gamemodes.net.event.UpdateLobbyPlayerEvent;
import forge.interfaces.ILobbyListener;
import forge.util.IHasForgeLog;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reusable production headless Forge network client shell.
 *
 * <p>The shell owns TCP client/lobby lifecycle and a caller-provided
 * {@link HeadlessNetworkGuiGame}. It contains no game-play policy and never clicks buttons,
 * selects cards, chooses players, or otherwise answers a Forge prompt on its own.</p>
 *
 * <p>A caller can observe the replicated state and prompt hooks through its GUI subclass,
 * then execute an approved action through the {@code IGameController} installed on that GUI
 * by {@link FGameClient}. This is the supported production counterpart to the historical
 * auto-playing network test client.</p>
 */
public final class HeadlessNetworkClient implements AutoCloseable, IHasForgeLog {
    private final String username;
    private final String hostname;
    private final int port;
    private final HeadlessNetworkGuiGame guiGame;

    private final AtomicBoolean connected = new AtomicBoolean();
    private final AtomicInteger assignedSlot = new AtomicInteger(-1);
    private volatile CountDownLatch connectedLatch = new CountDownLatch(1);

    private volatile FGameClient client;
    private volatile ClientGameLobby lobby;

    public HeadlessNetworkClient(final String username, final String hostname, final int port,
            final HeadlessNetworkGuiGame guiGame) {
        this.username = Objects.requireNonNull(username, "username");
        this.hostname = Objects.requireNonNull(hostname, "hostname");
        this.port = port;
        this.guiGame = Objects.requireNonNull(guiGame, "guiGame");
    }

    public synchronized boolean connect(final long timeoutMs) {
        if (connected.get()) {
            return true;
        }
        if (client != null) {
            throw new IllegalStateException("Client has already been started; close it before reconnecting");
        }

        connectedLatch = new CountDownLatch(1);
        assignedSlot.set(-1);
        lobby = new ClientGameLobby();
        guiGame.setClientLobby(lobby);

        client = new FGameClient(username, guiGame, hostname, port);
        client.addLobbyListener(new ClientLobbyListener());
        client.connect();

        try {
            final boolean success = connectedLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!success) {
                netLog.error("Headless client '{}' timed out connecting to {}:{} after {}ms",
                        username, hostname, port, timeoutMs);
            }
            return success;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Recreate the underlying TCP client after a clean close. Forge will perform its normal
     * lobby/full-state synchronization; no local state is guessed or replayed by this wrapper.
     */
    public synchronized boolean reconnect(final long timeoutMs) {
        closeClient();
        client = null;
        connected.set(false);
        assignedSlot.set(-1);
        return connect(timeoutMs);
    }

    public boolean isConnected() {
        return connected.get();
    }

    public int getAssignedSlot() {
        return assignedSlot.get();
    }

    public String getUsername() {
        return username;
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    public HeadlessNetworkGuiGame getGuiGame() {
        return guiGame;
    }

    public ClientGameLobby getLobby() {
        return lobby;
    }

    public FGameClient getClient() {
        return client;
    }

    /**
     * Mark this client's assigned lobby slot ready. This is lobby mechanics only; it does not
     * answer any in-game prompt.
     */
    public void setReady() {
        final FGameClient currentClient = client;
        final ClientGameLobby currentLobby = lobby;
        final int slot = assignedSlot.get();
        if (currentClient == null || currentLobby == null || !connected.get() || slot < 0) {
            throw new IllegalStateException("Cannot set ready before the client has a lobby slot");
        }

        final UpdateLobbyPlayerEvent event = UpdateLobbyPlayerEvent.isReadyUpdate(true);
        currentLobby.applyToSlot(slot, event);
        currentClient.send(event);
    }

    /**
     * Wait until a full replicated game view has arrived. This method never advances the game.
     */
    public boolean waitForGameView(final long timeoutMs) {
        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (guiGame.getGameView() != null && guiGame.getSetGameViewCount() > 0) {
                return true;
            }
            try {
                Thread.sleep(10L);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return guiGame.getGameView() != null && guiGame.getSetGameViewCount() > 0;
    }

    /** Request Forge's existing authoritative full-state resynchronization through each controller. */
    public void requestResync() {
        if (guiGame.getOriginalGameControllers().isEmpty()) {
            throw new IllegalStateException("Cannot request resync before a game controller is installed");
        }
        guiGame.getOriginalGameControllers().forEach(controller -> controller.requestResync());
    }

    @Override
    public synchronized void close() {
        closeClient();
        client = null;
        connected.set(false);
        assignedSlot.set(-1);
    }

    private void closeClient() {
        final FGameClient currentClient = client;
        if (currentClient != null) {
            try {
                currentClient.close();
            } catch (final RuntimeException e) {
                netLog.warn("Error closing headless client '{}': {}", username, e.getMessage());
            }
        }
    }

    private final class ClientLobbyListener implements ILobbyListener {
        @Override
        public void update(final GameLobbyData state, final int slot) {
            final ClientGameLobby currentLobby = lobby;
            if (currentLobby == null) {
                return;
            }
            currentLobby.setData(state);
            if (slot < 0) {
                return;
            }

            final int previous = assignedSlot.getAndSet(slot);
            currentLobby.setLocalPlayer(slot);
            if (previous < 0) {
                connected.set(true);
                connectedLatch.countDown();
                netLog.info("Headless client '{}' connected to {}:{} in lobby slot {}",
                        username, hostname, port, slot);
            }
        }

        @Override
        public void message(final String source, final String message, final ChatMessage.MessageType type) {
            netLog.info("Headless client '{}' chat from {}: {}", username, source, message);
        }

        @Override
        public void close() {
            connected.set(false);
            netLog.info("Headless client '{}' connection closed", username);
        }

        @Override
        public ClientGameLobby getLobby() {
            return lobby;
        }
    }
}
