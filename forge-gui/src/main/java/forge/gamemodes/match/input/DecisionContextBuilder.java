package forge.gamemodes.match.input;

import forge.ai.AvailableActions;
import forge.ai.ComputerUtilMana;
import forge.card.MagicColor;
import forge.card.mana.ManaAtom;
import forge.game.GameEntity;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardView;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.gamemodes.net.DecisionContext;
import forge.player.PlayerControllerHuman;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Converts host-side Forge input objects into a deliberately small, serializable
 * metadata snapshot for external pilots. The builder only reads the deciding
 * player's native input and player state; it never scans opponent hidden zones.
 */
public final class DecisionContextBuilder {
    private static final Map<InputQueue, TokenState> TOKENS = new WeakHashMap<>();

    private DecisionContextBuilder() {}

    public static DecisionContext build(final int seat, final Input input,
            final PlayerControllerHuman controller) {
        final Token token = tokenFor(controller.getInputQueue(), input);
        final String inputType = input == null ? "None" : input.getClass().getSimpleName();

        DecisionContext.SelectionContext selection = null;
        DecisionContext.PaymentContext payment = null;
        DecisionContext.PriorityContext priority = null;

        if (input instanceof InputSelectEntitiesFromList<?> select) {
            selection = buildSelection(select);
        }
        if (input instanceof InputPayMana payMana) {
            payment = buildPayment(payMana);
        }
        if (input instanceof InputPassPriority) {
            priority = buildPriority(controller.getPlayer());
        }

        return new DecisionContext(seat, token.sequence, token.inputToken,
                inputType, selection, payment, priority);
    }

    private static DecisionContext.SelectionContext buildSelection(
            final InputSelectEntitiesFromList<?> input) {
        final List<Integer> legal = new ArrayList<>();
        for (final Object item : input.getValidChoices()) {
            if (item instanceof GameEntity entity) {
                legal.add(entity.getId());
            }
        }
        final List<Integer> selected = new ArrayList<>();
        for (final Object item : input.getSelected()) {
            if (item instanceof GameEntity entity) {
                selected.add(entity.getId());
            }
        }
        return new DecisionContext.SelectionContext(input.min, input.max, legal, selected);
    }

    private static DecisionContext.PaymentContext buildPayment(final InputPayMana input) {
        final Player payer = input.player;
        final SpellAbility paidFor = input.saPaidFor;
        final ManaCostBeingPaid manaCost = input.manaCost;

        final Map<String, Integer> floating = new LinkedHashMap<>();
        for (final byte color : ManaAtom.MANATYPES) {
            final int amount = payer.getManaPool().getAmountOfColor(color);
            if (amount > 0) {
                floating.put(MagicColor.toShortString(color), amount);
            }
        }

        final List<Integer> autoSources = new ArrayList<>();
        boolean autoAvailable = false;
        try {
            final CardCollection sources = ComputerUtilMana.getManaSourcesToPayCost(
                    new ManaCostBeingPaid(manaCost), paidFor, payer, input.effect);
            if (sources != null) {
                autoAvailable = true;
                for (final Card card : sources) {
                    autoSources.add(card.getId());
                }
            }
        } catch (final RuntimeException ignored) {
            // A failed preview must not affect legality or invent a positive certificate.
            autoAvailable = false;
            autoSources.clear();
        }

        final Card paidForCard = paidFor == null ? null : paidFor.getHostCard();
        final String remaining = manaCost == null ? "" : manaCost.toString(false, payer.getManaPool());
        return new DecisionContext.PaymentContext(
                remaining,
                paidFor == null ? -1 : paidFor.getId(),
                paidForCard == null ? -1 : paidForCard.getId(),
                payer.getManaPool().totalMana(),
                floating,
                payer.getLife(),
                input.phyLifeToLose,
                autoSources,
                autoAvailable);
    }

    private static DecisionContext.PriorityContext buildPriority(final Player player) {
        if (player == null) {
            return new DecisionContext.PriorityContext(false, List.of());
        }
        final long timeoutMs = Long.getLong("forge.net.decisionContextActionabilityTimeoutMs", 50L);
        final List<Integer> actionableIds = new ArrayList<>();
        for (final CardView card : AvailableActions.collectActionable(player, timeoutMs)) {
            actionableIds.add(card.getId());
        }
        return new DecisionContext.PriorityContext(!actionableIds.isEmpty(), actionableIds);
    }

    private static synchronized Token tokenFor(final InputQueue queue, final Input input) {
        final TokenState state = TOKENS.computeIfAbsent(queue, q -> new TokenState());
        state.sequence++;
        Long token = state.byInput.get(input);
        if (token == null) {
            token = ++state.nextInputToken;
            state.byInput.put(input, token);
        }
        return new Token(state.sequence, "input-" + token);
    }

    private static final class TokenState {
        private final IdentityHashMap<Input, Long> byInput = new IdentityHashMap<>();
        private long nextInputToken;
        private long sequence;
    }

    private record Token(long sequence, String inputToken) {}
}
