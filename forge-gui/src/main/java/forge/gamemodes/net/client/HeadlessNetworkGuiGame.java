package forge.gamemodes.net.client;

import forge.LobbyPlayer;
import forge.game.GameState;
import forge.game.card.CardView;
import forge.game.event.GameEvent;
import forge.game.phase.PhaseType;
import forge.game.player.IHasIcon;
import forge.game.player.PlayerView;
import forge.game.zone.ZoneType;
import forge.gamemodes.net.DeltaPacket;
import forge.gamemodes.net.NetworkGuiGame;
import forge.interfaces.IGameController;
import forge.player.PlayerZoneUpdate;
import forge.player.PlayerZoneUpdates;
import forge.trackable.TrackableCollection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Production headless base for Forge network clients.
 *
 * <p>This class owns only network-state/view plumbing and non-strategic display behavior.
 * It deliberately does <strong>not</strong> implement decision-returning GUI methods such as
 * ability choice, target/entity choice, ordering, confirmation, or combat-damage assignment.
 * Callers must subclass this type and provide those decisions explicitly. This keeps the
 * production headless path fail-closed instead of inheriting the test harness' historical
 * "pick the first option / click OK" policy.</p>
 *
 * <p>Asynchronous GUI/controller callbacks are exposed through protected hooks so an external
 * adapter can observe a prompt and intentionally leave it pending until it has a decision.
 * The replicated {@link forge.game.GameView} remains managed by {@link NetworkGuiGame}.</p>
 */
public abstract class HeadlessNetworkGuiGame extends NetworkGuiGame {
    private final AtomicInteger setGameViewCount = new AtomicInteger();
    private volatile boolean openViewCalled;
    private volatile long lastGameViewSequence = -1L;

    public final int getSetGameViewCount() {
        return setGameViewCount.get();
    }

    public final boolean isOpenViewCalled() {
        return openViewCalled;
    }

    public final long getLastGameViewSequence() {
        return lastGameViewSequence;
    }

    @Override
    protected void updateCurrentPlayer(final PlayerView player) {
        onCurrentPlayerChanged(player);
    }

    @Override
    public boolean isUiSetToSkipPhase(final PlayerView playerTurn, final PhaseType phase) {
        return false;
    }

    @Override
    public PlayerZoneUpdates openZones(final PlayerView controller, final Collection<ZoneType> zones,
            final Map<PlayerView, Object> players, final boolean backupLastZones) {
        return null;
    }

    @Override
    public void restoreOldZones(final PlayerView playerView, final PlayerZoneUpdates playerZoneUpdates) {
    }

    @Override
    public void openView(final TrackableCollection<PlayerView> myPlayers) {
        openViewCalled = true;
        onOpenView(myPlayers);
    }

    @Override
    public void setGameView(final forge.game.GameView gameView) {
        super.setGameView(gameView);
        setGameViewCount.incrementAndGet();
        lastGameViewSequence = -1L;
        onGameViewUpdated(getGameView(), -1L);
    }

    @Override
    public void setGameView(final forge.game.GameView gameView, final long sequenceNumber) {
        // Call the concrete AbstractGuiGame implementation directly so the hook is emitted once.
        super.setGameView(gameView);
        setGameViewCount.incrementAndGet();
        lastGameViewSequence = sequenceNumber;
        onGameViewUpdated(getGameView(), sequenceNumber);
    }

    @Override
    public void applyDelta(final DeltaPacket packet) {
        super.applyDelta(packet);
        onDeltaApplied(packet);
    }

    @Override
    public void handleGameEvents(final List<GameEvent> events) {
        super.handleGameEvents(events);
        onGameEvents(events);
    }

    @Override
    public void setOriginalGameController(final PlayerView view, final IGameController controller) {
        super.setOriginalGameController(view, controller);
        onGameControllerChanged(view, controller, true);
    }

    @Override
    public void setGameController(final PlayerView player, final IGameController controller) {
        super.setGameController(player, controller);
        onGameControllerChanged(player, controller, false);
    }

    @Override
    public void showPromptMessage(final PlayerView playerView, final String message, final CardView card) {
        onPromptMessage(playerView, message, card);
    }

    @Override
    public void updateButtons(final PlayerView owner, final String label1, final String label2,
            final boolean enable1, final boolean enable2, final boolean focus1) {
        onButtonsUpdated(owner, label1, label2, enable1, enable2, focus1);
    }

    @Override
    public void setSelectables(final Iterable<CardView> cards, final int min, final int max) {
        super.setSelectables(cards, min, max);
        final List<CardView> snapshot = new ArrayList<>();
        if (cards != null) {
            for (final CardView card : cards) {
                snapshot.add(card);
            }
        }
        onSelectablesChanged(snapshot, min, max);
    }

    @Override
    public void clearSelectables() {
        super.clearSelectables();
        onSelectablesChanged(List.of(), 0, 0);
    }

    @Override
    public void showCombat() {
    }

    @Override
    public void finishGame() {
        onFinishGame();
    }

    @Override
    public void flashIncorrectAction() {
        onIncorrectAction();
    }

    @Override
    public void alertUser() {
        onAlertUser();
    }

    @Override
    public void updatePhase(final boolean saveState) {
    }

    @Override
    public void updateTurn(final PlayerView player) {
    }

    @Override
    public void updatePlayerControl() {
    }

    @Override
    public void enableOverlay() {
    }

    @Override
    public void disableOverlay() {
    }

    @Override
    public void showManaPool(final PlayerView player) {
    }

    @Override
    public void hideManaPool(final PlayerView player) {
    }

    @Override
    public void updateStack() {
    }

    @Override
    public Iterable<PlayerZoneUpdate> tempShowZones(final PlayerView controller,
            final Iterable<PlayerZoneUpdate> zonesToUpdate) {
        return zonesToUpdate;
    }

    @Override
    public void hideZones(final PlayerView controller, final Iterable<PlayerZoneUpdate> zonesToUpdate) {
    }

    @Override
    public void updateZones(final Iterable<PlayerZoneUpdate> zonesToUpdate) {
    }

    @Override
    public void updateCards(final Iterable<CardView> cards) {
    }

    @Override
    public GameState getGamestate() {
        return null;
    }

    @Override
    public void updateManaPool(final Iterable<PlayerView> manaPoolUpdate) {
    }

    @Override
    public void updateLives(final Iterable<PlayerView> livesUpdate) {
    }

    @Override
    public void updateShards(final Iterable<PlayerView> shardsUpdate) {
    }

    @Override
    public void setPanelSelection(final CardView hostCard) {
    }

    @Override
    public void message(final String message, final String title) {
        onMessage(message, title);
    }

    @Override
    public void showErrorDialog(final String message, final String title) {
        onError(message, title);
    }

    @Override
    public void setCard(final CardView card) {
    }

    @Override
    public void setPlayerAvatar(final LobbyPlayer player, final IHasIcon ihi) {
    }

    @Override
    public void afterGameEnd() {
        super.afterGameEnd();
        onGameEnded();
    }

    /** Called after the current controlled player changes. */
    protected void onCurrentPlayerChanged(final PlayerView player) {
    }

    /** Called when the client receives its initial/open-view player set. */
    protected void onOpenView(final TrackableCollection<PlayerView> myPlayers) {
    }

    /** Called after a full GameView has been installed/merged. */
    protected void onGameViewUpdated(final forge.game.GameView gameView, final long sequenceNumber) {
    }

    /** Called after a delta has been applied to the replicated GameView. */
    protected void onDeltaApplied(final DeltaPacket packet) {
    }

    /** Called after a received batch of game events has been dispatched. */
    protected void onGameEvents(final List<GameEvent> events) {
    }

    /** Called whenever Forge installs or changes a controller for a controlled player. */
    protected void onGameControllerChanged(final PlayerView player, final IGameController controller,
            final boolean originalController) {
    }

    /**
     * Asynchronous prompt notification. Subclasses may deliberately do nothing here to leave
     * the corresponding native Forge input pending until an external pilot responds.
     */
    protected void onPromptMessage(final PlayerView player, final String message, final CardView card) {
    }

    /** Asynchronous OK/Cancel (or labelled button) state notification. */
    protected void onButtonsUpdated(final PlayerView owner, final String label1, final String label2,
            final boolean enable1, final boolean enable2, final boolean focus1) {
    }

    /** Snapshot of the current selectable-card surface. */
    protected void onSelectablesChanged(final List<CardView> cards, final int min, final int max) {
    }

    protected void onIncorrectAction() {
    }

    protected void onAlertUser() {
    }

    protected void onMessage(final String message, final String title) {
    }

    protected void onError(final String message, final String title) {
    }

    protected void onFinishGame() {
    }

    protected void onGameEnded() {
    }
}
