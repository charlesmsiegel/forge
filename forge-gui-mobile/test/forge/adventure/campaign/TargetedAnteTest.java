package forge.adventure.campaign;

import forge.deck.Deck;
import forge.item.PaperCard;
import org.testng.annotations.Test;
import java.util.List;
import java.util.Random;
import static org.testng.Assert.*;

public class TargetedAnteTest extends AdventureTestBase {
    @Test
    public void knownExactPrintingCanBeTargetedForTwoDistinctEligibleCopies() {
        PaperCard target = card("Muscle Sliver", "TMP");
        Deck enemy = deckOf(target, 3, card("Forest", "TMP"), 37);
        Deck player = deckOf(card("Lightning Bolt", "M10"), 3, card("Mountain", "M10"), 37);
        OpponentKnowledge knowledge = new OpponentKnowledge();
        knowledge.observe("rival-1", target);
        AnteStake stake = AnteService.targetedStake(player, enemy, target, knowledge, "rival-1", CampaignConfig.instance().ante, new Random(1));
        assertEquals(stake.kind, AnteStake.Kind.TARGETED);
        assertEquals(stake.playerCards.size(), 2);
        assertEquals(stake.opponentCards, List.of(target));
        assertEquals(stake.playerCards.stream().filter(AnteService::isOrdinaryBasicLand).count(), 0L);
    }

    @Test
    public void unknownStaleOtherRivalAndDifferentPrintingTargetsAreRejected() {
        PaperCard target = card("Muscle Sliver", "TMP");
        Deck enemy = deckOf(target, 3);
        Deck player = deckOf(card("Lightning Bolt", "M10"), 3);
        OpponentKnowledge knowledge = new OpponentKnowledge();
        assertThrows(IllegalArgumentException.class, () -> AnteService.targetedStake(player, enemy, target, knowledge, "rival", CampaignConfig.instance().ante, new Random(1)));
        knowledge.observe("rival", target);
        assertThrows(IllegalArgumentException.class, () -> AnteService.targetedStake(player, enemy, target, knowledge, "other-rival", CampaignConfig.instance().ante, new Random(1)));
        assertThrows(IllegalArgumentException.class, () -> AnteService.targetedStake(player, new Deck(), target, knowledge, "rival", CampaignConfig.instance().ante, new Random(1)));
        PaperCard otherPrinting = card("Muscle Sliver", "PLC");
        assertThrows(IllegalArgumentException.class, () -> AnteService.targetedStake(player, deckOf(otherPrinting, 1), otherPrinting, knowledge, "rival", CampaignConfig.instance().ante, new Random(1)));
    }

    @Test
    public void insufficientRiskCardsCannotProduceDiscountedTargetedAnte() {
        PaperCard target = card("Muscle Sliver", "TMP");
        OpponentKnowledge knowledge = new OpponentKnowledge();
        knowledge.observe("rival", target);
        assertThrows(IllegalArgumentException.class, () -> AnteService.targetedStake(
                deckOf(card("Lightning Bolt", "M10"), 1, card("Mountain", "M10"), 39), deckOf(target, 1), target,
                knowledge, "rival", CampaignConfig.instance().ante, new Random(1)));
    }

    @Test
    public void knowledgePersistsButStaleTargetsAreExcludedAndLegacyDataIsEmpty() {
        OpponentKnowledge knowledge = new OpponentKnowledge();
        PaperCard a = card("Muscle Sliver", "TMP");
        PaperCard b = card("Lightning Bolt", "M10");
        knowledge.observe("rival", a);
        knowledge.observe("rival", a);
        knowledge.observe("rival", b);
        OpponentKnowledge loaded = new OpponentKnowledge();
        loaded.load(knowledge.save());
        assertEquals(loaded.known("rival").size(), 2);
        assertEquals(loaded.targets("rival", deckOf(a, 3)), List.of(a));
        assertTrue(loaded.known("other").isEmpty());
        loaded.load(null);
        assertTrue(loaded.known("rival").isEmpty());
    }
}
