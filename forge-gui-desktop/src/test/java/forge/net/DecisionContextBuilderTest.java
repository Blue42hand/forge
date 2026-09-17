package forge.net;

import forge.gamemodes.net.DecisionContext;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/** Structural checks for exactly-once decision identity semantics. */
public class DecisionContextBuilderTest {
    @Test
    public void testInputTokenAndSequenceAreIndependent() {
        final DecisionContext first = new DecisionContext(1, 10L, "input-7",
                "InputSelectCardsFromList",
                new DecisionContext.SelectionContext(1, 2, List.of(3, 4), List.of()),
                null, null);
        final DecisionContext continuation = new DecisionContext(1, 11L, "input-7",
                "InputSelectCardsFromList",
                new DecisionContext.SelectionContext(1, 2, List.of(3, 4), List.of(3)),
                null, null);
        final DecisionContext next = new DecisionContext(1, 12L, "input-8",
                "InputPassPriority", null, null, null);

        Assert.assertEquals(first.getInputToken(), continuation.getInputToken(),
                "continuing updates for one native input keep their identity");
        Assert.assertTrue(continuation.getSequence() > first.getSequence(),
                "every published snapshot has a monotonic sequence");
        Assert.assertNotEquals(next.getInputToken(), continuation.getInputToken(),
                "a new native input has a new token");
    }
}
