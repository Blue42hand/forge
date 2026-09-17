package forge.net;

import forge.deck.CardPool;
import forge.deck.Deck;
import forge.game.GameEntityView;
import forge.game.card.CardView;
import forge.game.player.DelayedReveal;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbilityView;
import forge.gamemodes.match.HostedMatch;
import forge.gamemodes.match.LobbySlot;
import forge.gamemodes.match.LobbySlotType;
import forge.gamemodes.net.server.FServerManager;
import forge.gamemodes.net.server.ServerGameLobby;
import forge.gui.interfaces.IGuiGame;
import forge.item.PaperCard;
import forge.localinstance.skin.FSkinProp;
import forge.util.FSerializableFunction;
import forge.util.ITriggerEvent;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Integration coverage for the production, policy-free headless network client. */
public class ProductionHeadlessClientIntegrationTest {
    private static boolean initialized;

    @BeforeClass
    public static void setUp() {
        if (!initialized) {
            TestUtils.ensureFModelInitialized();
            initialized = true;
        }
    }

    @Test(timeOut = 60000,
            description = "Production headless client reaches a real Forge prompt without auto-answering")
    public void testProductionClientCanRemainPendingAtPrompt() throws Exception {
        final FServerManager server = FServerManager.getInstance();
        final int port = PortAllocator.allocatePort();
        final PendingGui gui = new PendingGui();
        forge.gamemodes.net.client.HeadlessNetworkClient client = null;

        try {
            server.startServer(port);
            final ServerGameLobby lobby = new ServerGameLobby();
            server.setLobby(lobby);

            final Deck hostDeck = TestDeckLoader.createMinimalDeck("Mountain", 10);
            final Deck remoteDeck = TestDeckLoader.createMinimalDeck("Forest", 10);

            final LobbySlot host = lobby.getSlot(0);
            host.setType(LobbySlotType.AI);
            host.setName("Alice (Host AI)");
            host.setDeck(hostDeck);
            host.setIsReady(true);

            final LobbySlot remote = lobby.getSlot(1);
            remote.setType(LobbySlotType.OPEN);
            remote.setDeck(remoteDeck);
            remote.setIsReady(false);

            client = new forge.gamemodes.net.client.HeadlessNetworkClient(
                    "Bob (Remote)", "localhost", port, gui);
            Assert.assertTrue(client.connect(15000), "Production client should connect");
            Assert.assertTrue(client.getAssignedSlot() >= 0, "Production client should receive a lobby slot");

            client.setReady();
            final long readyDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!lobby.getSlot(client.getAssignedSlot()).isReady() && System.nanoTime() < readyDeadline) {
                Thread.sleep(10L);
            }
            Assert.assertTrue(lobby.getSlot(client.getAssignedSlot()).isReady(),
                    "Server lobby should observe the production client ready state");

            final Runnable start = lobby.startGame();
            Assert.assertNotNull(start, "Network game should pass validation and create a start runnable");
            start.run();

            Assert.assertTrue(gui.controllerInstalled.await(10, TimeUnit.SECONDS),
                    "Network client should receive a NetGameController");
            Assert.assertTrue(gui.decisionSurfaceReached.await(10, TimeUnit.SECONDS),
                    "Network client should reach an asynchronous player-decision surface");
            Assert.assertTrue(client.waitForGameView(10000),
                    "Network client should receive a replicated GameView");
            Assert.assertTrue(gui.decisionCallbackCount.get() > 0,
                    "At least one prompt/button/selectable callback should be surfaced");

            // PendingGui never invokes its installed controller. Give the engine a moment and verify
            // the match has not silently advanced to a terminal result via a default headless choice.
            Thread.sleep(250L);
            final HostedMatch match = HeadlessGuiDesktop.getLastMatch();
            Assert.assertNotNull(match, "Hosted match should exist");
            Assert.assertNotNull(match.getGame(), "Hosted game should exist");
            Assert.assertFalse(match.getGame().isGameOver(),
                    "Game must remain pending rather than auto-answering the remote decision");
        } finally {
            if (client != null) {
                client.close();
            }
            try {
                server.clearPlayerGuis();
            } catch (final Exception ignored) {
            }
            if (server.isHosting()) {
                server.stopServer();
            }
            HeadlessGuiDesktop.clearLastMatch();
        }
    }

    /**
     * Implements only the synchronous decision-returning methods required to instantiate the
     * production base. They fail loudly if the test reaches one before the expected async prompt.
     */
    private static final class PendingGui extends forge.gamemodes.net.client.HeadlessNetworkGuiGame {
        private final CountDownLatch controllerInstalled = new CountDownLatch(1);
        private final CountDownLatch decisionSurfaceReached = new CountDownLatch(1);
        private final AtomicInteger decisionCallbackCount = new AtomicInteger();

        private <T> T unexpectedSynchronousDecision(final String kind) {
            throw new AssertionError("Unexpected synchronous headless decision request: " + kind);
        }

        private void markDecisionSurface() {
            decisionCallbackCount.incrementAndGet();
            decisionSurfaceReached.countDown();
        }

        @Override
        protected void onGameControllerChanged(final PlayerView player,
                final forge.interfaces.IGameController controller, final boolean originalController) {
            if (controller != null) {
                controllerInstalled.countDown();
            }
        }

        @Override
        protected void onPromptMessage(final PlayerView player, final String message, final CardView card) {
            if (message != null && !message.isBlank()) {
                markDecisionSurface();
            }
        }

        @Override
        protected void onButtonsUpdated(final PlayerView owner, final String label1, final String label2,
                final boolean enable1, final boolean enable2, final boolean focus1) {
            if (enable1 || enable2) {
                markDecisionSurface();
            }
        }

        @Override
        protected void onSelectablesChanged(final List<CardView> cards, final int min, final int max) {
            if (!cards.isEmpty()) {
                markDecisionSurface();
            }
        }

        @Override
        public SpellAbilityView getAbilityToPlay(final CardView hostCard,
                final List<SpellAbilityView> abilities, final ITriggerEvent triggerEvent) {
            return unexpectedSynchronousDecision("ability");
        }

        @Override
        public Map<CardView, Integer> assignCombatDamage(final CardView attacker,
                final List<CardView> blockers, final int damage, final GameEntityView defender,
                final boolean overrideOrder, final boolean maySkip) {
            return unexpectedSynchronousDecision("combat damage");
        }

        @Override
        public Map<Object, Integer> assignGenericAmount(final CardView effectSource,
                final Map<Object, Integer> target, final int amount, final boolean atLeastOne,
                final String amountLabel) {
            return unexpectedSynchronousDecision("generic amount");
        }

        @Override
        public boolean showConfirmDialog(final String message, final String title,
                final String yesButtonText, final String noButtonText, final boolean defaultYes) {
            return unexpectedSynchronousDecision("confirm dialog");
        }

        @Override
        public int showOptionDialog(final String message, final String title, final FSkinProp icon,
                final List<String> options, final int defaultOption) {
            return unexpectedSynchronousDecision("option dialog");
        }

        @Override
        public String showInputDialog(final String message, final String title, final FSkinProp icon,
                final String initialInput, final List<String> inputOptions, final boolean isNumeric) {
            return unexpectedSynchronousDecision("input dialog");
        }

        @Override
        public boolean confirm(final CardView card, final String question,
                final boolean defaultIsYes, final List<String> options) {
            return unexpectedSynchronousDecision("card confirm");
        }

        @Override
        public <T> List<T> getChoices(final String message, final int min, final int max,
                final List<T> choices, final List<T> selected,
                final FSerializableFunction<T, String> display) {
            return unexpectedSynchronousDecision("choices");
        }

        @Override
        public <T> IGuiGame.OrderResult<T> order(final String title, final String top,
                final int remainingObjectsMin, final int remainingObjectsMax,
                final List<T> sourceChoices, final List<T> destChoices, final CardView referenceCard,
                final boolean sideboardingMode, final boolean showRememberCheckbox) {
            return unexpectedSynchronousDecision("order");
        }

        @Override
        public List<PaperCard> sideboard(final CardPool sideboard, final CardPool main,
                final String message) {
            return unexpectedSynchronousDecision("sideboard");
        }

        @Override
        public GameEntityView chooseSingleEntityForEffect(final String title,
                final List<? extends GameEntityView> optionList, final DelayedReveal delayedReveal,
                final boolean isOptional) {
            return unexpectedSynchronousDecision("single entity");
        }

        @Override
        public List<GameEntityView> chooseEntitiesForEffect(final String title,
                final List<? extends GameEntityView> optionList, final int min, final int max,
                final DelayedReveal delayedReveal) {
            return unexpectedSynchronousDecision("entities");
        }

        @Override
        public List<CardView> manipulateCardList(final String title, final Iterable<CardView> cards,
                final Iterable<CardView> manipulable, final boolean toTop, final boolean toBottom,
                final boolean toAnywhere) {
            return unexpectedSynchronousDecision("manipulate card list");
        }
    }
}
