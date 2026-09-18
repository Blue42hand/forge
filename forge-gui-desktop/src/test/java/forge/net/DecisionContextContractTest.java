package forge.net;

import forge.gamemodes.net.DecisionContext;
import forge.gamemodes.net.event.DecisionContextEvent;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;

/** Contract coverage for the external-pilot metadata envelope. */
public class DecisionContextContractTest {

    @Test
    public void testVersionedContextRoundTripsWithoutPromptOrOpponentPayload() throws Exception {
        final DecisionContext original = new DecisionContext(
                2,
                17L,
                "input-4",
                "InputPassPriority",
                null,
                null,
                new DecisionContext.PriorityContext(false, List.of()));

        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(new DecisionContextEvent(original));
        }

        final DecisionContextEvent decoded;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            decoded = (DecisionContextEvent) in.readObject();
        }

        final DecisionContext context = decoded.getContext();
        Assert.assertEquals(context.getSchemaVersion(), DecisionContext.SCHEMA_VERSION);
        Assert.assertEquals(context.getSeat(), 2);
        Assert.assertEquals(context.getSequence(), 17L);
        Assert.assertEquals(context.getInputToken(), "input-4");
        Assert.assertEquals(context.getInputType(), "InputPassPriority");
        Assert.assertNull(context.getSelection());
        Assert.assertNull(context.getPayment());
        Assert.assertNotNull(context.getPriority());
        Assert.assertFalse(context.getPriority().hasActionablePlay());
        Assert.assertTrue(context.getPriority().getActionableCardIds().isEmpty());

        // The schema intentionally has no prompt text, card names, opponent-zone snapshot,
        // deck list, or arbitrary object payload. Hidden information must enter only through
        // an explicitly typed field populated from the deciding seat's native input/state.
        final List<String> fieldNames = java.util.Arrays.stream(DecisionContext.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .toList();
        Assert.assertFalse(fieldNames.contains("prompt"));
        Assert.assertFalse(fieldNames.contains("message"));
        Assert.assertFalse(fieldNames.contains("gameView"));
        Assert.assertFalse(fieldNames.contains("opponents"));
    }

    @Test
    public void testPaymentContextCarriesOnlyTypedSafeFacts() {
        final DecisionContext.PaymentContext payment = new DecisionContext.PaymentContext(
                "{2}{G}", 101, 55, 2, Map.of("G", 1, "C", 1), 34, 0,
                List.of(11, 12), List.of(21, 22), true);
        Assert.assertEquals(payment.getRemainingManaCost(), "{2}{G}");
        Assert.assertEquals(payment.getPaidForAbilityId(), 101);
        Assert.assertEquals(payment.getPaidForCardId(), 55);
        Assert.assertEquals(payment.getFloatingManaTotal(), 2);
        Assert.assertEquals(payment.getFloatingManaByColor().get("G"), Integer.valueOf(1));
        Assert.assertEquals(payment.getNativeAutoSourceCardIds(), List.of(11, 12));
        Assert.assertEquals(payment.getDirectManaSourceCardIds(), List.of(21, 22));
        Assert.assertTrue(payment.isAutoPaymentAvailable());
    }
}
