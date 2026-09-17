package forge.gamemodes.net;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Authoritative, non-strategic metadata describing the native Forge input currently
 * awaiting a remote player's response. This augments the existing GUI/controller
 * protocol; it does not replace prompts or perform any legality decisions client-side.
 */
public final class DecisionContext implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final int SCHEMA_VERSION = 2;

    private final int schemaVersion;
    private final int seat;
    private final long sequence;
    private final String inputToken;
    private final String inputType;
    private final SelectionContext selection;
    private final PaymentContext payment;
    private final PriorityContext priority;

    public DecisionContext(final int seat, final long sequence, final String inputToken,
            final String inputType, final SelectionContext selection,
            final PaymentContext payment, final PriorityContext priority) {
        this.schemaVersion = SCHEMA_VERSION;
        this.seat = seat;
        this.sequence = sequence;
        this.inputToken = inputToken;
        this.inputType = inputType;
        this.selection = selection;
        this.payment = payment;
        this.priority = priority;
    }

    public int getSchemaVersion() { return schemaVersion; }
    public int getSeat() { return seat; }
    public long getSequence() { return sequence; }
    public String getInputToken() { return inputToken; }
    public String getInputType() { return inputType; }
    public SelectionContext getSelection() { return selection; }
    public PaymentContext getPayment() { return payment; }
    public PriorityContext getPriority() { return priority; }

    public static final class SelectionContext implements Serializable {
        private static final long serialVersionUID = 1L;
        private final int minimum;
        private final int maximum;
        private final List<Integer> legalEntityIds;
        private final List<Integer> selectedEntityIds;

        public SelectionContext(final int minimum, final int maximum,
                final List<Integer> legalEntityIds, final List<Integer> selectedEntityIds) {
            this.minimum = minimum;
            this.maximum = maximum;
            this.legalEntityIds = legalEntityIds == null ? List.of() : List.copyOf(legalEntityIds);
            this.selectedEntityIds = selectedEntityIds == null ? List.of() : List.copyOf(selectedEntityIds);
        }

        public int getMinimum() { return minimum; }
        public int getMaximum() { return maximum; }
        public List<Integer> getLegalEntityIds() { return legalEntityIds; }
        public List<Integer> getSelectedEntityIds() { return selectedEntityIds; }
    }

    public static final class PaymentContext implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String remainingManaCost;
        private final int paidForAbilityId;
        private final int paidForCardId;
        private final int floatingManaTotal;
        private final Map<String, Integer> floatingManaByColor;
        private final int payerLife;
        private final int pendingLifePayment;
        private final List<Integer> nativeAutoSourceCardIds;
        private final List<Integer> directManaSourceCardIds;
        private final boolean autoPaymentAvailable;

        public PaymentContext(final String remainingManaCost, final int paidForAbilityId,
                final int paidForCardId, final int floatingManaTotal,
                final Map<String, Integer> floatingManaByColor, final int payerLife,
                final int pendingLifePayment, final List<Integer> nativeAutoSourceCardIds,
                final List<Integer> directManaSourceCardIds, final boolean autoPaymentAvailable) {
            this.remainingManaCost = remainingManaCost;
            this.paidForAbilityId = paidForAbilityId;
            this.paidForCardId = paidForCardId;
            this.floatingManaTotal = floatingManaTotal;
            this.floatingManaByColor = floatingManaByColor == null
                    ? Map.of() : Collections.unmodifiableMap(floatingManaByColor);
            this.payerLife = payerLife;
            this.pendingLifePayment = pendingLifePayment;
            this.nativeAutoSourceCardIds = nativeAutoSourceCardIds == null
                    ? List.of() : List.copyOf(nativeAutoSourceCardIds);
            this.directManaSourceCardIds = directManaSourceCardIds == null
                    ? List.of() : List.copyOf(directManaSourceCardIds);
            this.autoPaymentAvailable = autoPaymentAvailable;
        }

        public String getRemainingManaCost() { return remainingManaCost; }
        public int getPaidForAbilityId() { return paidForAbilityId; }
        public int getPaidForCardId() { return paidForCardId; }
        public int getFloatingManaTotal() { return floatingManaTotal; }
        public Map<String, Integer> getFloatingManaByColor() { return floatingManaByColor; }
        public int getPayerLife() { return payerLife; }
        public int getPendingLifePayment() { return pendingLifePayment; }
        public List<Integer> getNativeAutoSourceCardIds() { return nativeAutoSourceCardIds; }
        public List<Integer> getDirectManaSourceCardIds() { return directManaSourceCardIds; }
        public boolean isAutoPaymentAvailable() { return autoPaymentAvailable; }
    }

    public static final class PriorityContext implements Serializable {
        private static final long serialVersionUID = 1L;
        private final boolean hasActionablePlay;
        private final List<Integer> actionableCardIds;

        public PriorityContext(final boolean hasActionablePlay, final List<Integer> actionableCardIds) {
            this.hasActionablePlay = hasActionablePlay;
            this.actionableCardIds = actionableCardIds == null ? List.of() : List.copyOf(actionableCardIds);
        }

        public boolean hasActionablePlay() { return hasActionablePlay; }
        public List<Integer> getActionableCardIds() { return actionableCardIds; }
    }
}
