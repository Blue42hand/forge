package forge.net;

import forge.deck.CardPool;
import forge.deck.Deck;
import forge.game.GameEntityView;
import forge.game.card.CardView;
import forge.game.player.DelayedReveal;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbilityView;
import forge.gamemodes.match.LobbySlot;
import forge.gamemodes.match.LobbySlotType;
import forge.gamemodes.net.DecisionContext;
import forge.gamemodes.net.client.HeadlessNetworkClient;
import forge.gamemodes.net.client.HeadlessNetworkGuiGame;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** End-to-end coverage for authoritative host -> headless client decision metadata. */
public class DecisionContextLiveIntegrationTest {
    private static boolean initialized;

    @BeforeClass
    public static void setUp() {
        if (!initialized) {
            TestUtils.ensureFModelInitialized();
            initialized = true;
        }
    }

    @Test(timeOut = 60000)
    public void testNativeInputIdentityArrivesWithoutPromptParsing() throws Exception {
        final FServerManager server = FServerManager.getInstance();
        final int port = PortAllocator.allocatePort();
        final PassiveGui gui = new PassiveGui();
        HeadlessNetworkClient client = null;
        try {
            server.startServer(port);
            final ServerGameLobby lobby = new ServerGameLobby();
            server.setLobby(lobby);

            final LobbySlot host = lobby.getSlot(0);
            host.setType(LobbySlotType.AI);
            host.setName("Host AI");
            host.setDeck(TestDeckLoader.createMinimalDeck("Mountain", 10));
            host.setIsReady(true);

            final LobbySlot remote = lobby.getSlot(1);
            remote.setType(LobbySlotType.OPEN);
            remote.setDeck(TestDeckLoader.createMinimalDeck("Forest", 10));
            remote.setIsReady(false);

            client = new HeadlessNetworkClient("Remote Pilot", "localhost", port, gui);
            Assert.assertTrue(client.connect(15000));
            client.setReady();

            final long readyDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!lobby.getSlot(client.getAssignedSlot()).isReady() && System.nanoTime() < readyDeadline) {
                Thread.sleep(10L);
            }
            final Runnable start = lobby.startGame();
            Assert.assertNotNull(start);
            start.run();

            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            DecisionContext context = null;
            while (System.nanoTime() < deadline) {
                context = client.getClient().getLatestDecisionContext();
                if (context != null && !"None".equals(context.getInputType())) {
                    break;
                }
                Thread.sleep(10L);
            }

            Assert.assertNotNull(context, "headless client should receive native decision metadata");
            Assert.assertEquals(context.getSchemaVersion(), DecisionContext.SCHEMA_VERSION);
            Assert.assertEquals(context.getSeat(), client.getAssignedSlot());
            Assert.assertTrue(context.getSequence() > 0);
            Assert.assertNotNull(context.getInputToken());
            Assert.assertFalse(context.getInputToken().isBlank());
            Assert.assertNotEquals(context.getInputType(), "None");

            // Initial mulligan/starting-hand decisions need identity, but no opponent-zone payload.
            // Rich typed subcontexts are present only when that native input owns those facts.
            if (context.getInputType().contains("Mulligan") || context.getInputType().contains("StartingHand")) {
                Assert.assertNull(context.getSelection());
                Assert.assertNull(context.getPayment());
                Assert.assertNull(context.getPriority());
            }
        } finally {
            if (client != null) client.close();
            try { server.clearPlayerGuis(); } catch (final Exception ignored) {}
            if (server.isHosting()) server.stopServer();
            HeadlessGuiDesktop.clearLastMatch();
        }
    }

    private static final class PassiveGui extends HeadlessNetworkGuiGame {
        private <T> T fail(final String kind) { throw new AssertionError("Unexpected synchronous decision: " + kind); }
        @Override public SpellAbilityView getAbilityToPlay(CardView hostCard, List<SpellAbilityView> abilities, ITriggerEvent triggerEvent) { return fail("ability"); }
        @Override public Map<CardView, Integer> assignCombatDamage(CardView attacker, List<CardView> blockers, int damage, GameEntityView defender, boolean overrideOrder, boolean maySkip) { return fail("combat"); }
        @Override public Map<Object, Integer> assignGenericAmount(CardView effectSource, Map<Object, Integer> target, int amount, boolean atLeastOne, String amountLabel) { return fail("amount"); }
        @Override public boolean showConfirmDialog(String message, String title, String yesButtonText, String noButtonText, boolean defaultYes) { return fail("confirm"); }
        @Override public int showOptionDialog(String message, String title, FSkinProp icon, List<String> options, int defaultOption) { return fail("option"); }
        @Override public String showInputDialog(String message, String title, FSkinProp icon, String initialInput, List<String> inputOptions, boolean isNumeric) { return fail("input"); }
        @Override public boolean confirm(CardView card, String question, boolean defaultIsYes, List<String> options) { return fail("card confirm"); }
        @Override public <T> List<T> getChoices(String message, int min, int max, List<T> choices, List<T> selected, FSerializableFunction<T, String> display) { return fail("choices"); }
        @Override public <T> IGuiGame.OrderResult<T> order(String title, String top, int remainingObjectsMin, int remainingObjectsMax, List<T> sourceChoices, List<T> destChoices, CardView referenceCard, boolean sideboardingMode, boolean showRememberCheckbox) { return fail("order"); }
        @Override public List<PaperCard> sideboard(CardPool sideboard, CardPool main, String message) { return fail("sideboard"); }
        @Override public GameEntityView chooseSingleEntityForEffect(String title, List<? extends GameEntityView> optionList, DelayedReveal delayedReveal, boolean isOptional) { return fail("single entity"); }
        @Override public List<GameEntityView> chooseEntitiesForEffect(String title, List<? extends GameEntityView> optionList, int min, int max, DelayedReveal delayedReveal) { return fail("entities"); }
        @Override public List<CardView> manipulateCardList(String title, Iterable<CardView> cards, Iterable<CardView> manipulable, boolean toTop, boolean toBottom, boolean toAnywhere) { return fail("manipulate"); }
    }
}
