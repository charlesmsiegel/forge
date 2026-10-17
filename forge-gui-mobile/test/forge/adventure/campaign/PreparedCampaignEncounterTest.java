package forge.adventure.campaign;

import forge.deck.Deck;
import org.testng.annotations.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.testng.Assert.*;

public class PreparedCampaignEncounterTest extends AdventureTestBase {
    @Test
    public void randomDeckIsChosenOnceUntilConsumedAndCanceledDeckIsDiscarded() {
        PreparedCampaignEncounter encounter = new PreparedCampaignEncounter();
        AtomicInteger rolls = new AtomicInteger();
        var supplier = (java.util.function.Supplier<Deck>) () -> {
            rolls.incrementAndGet();
            return deckOf(card("Muscle Sliver", "TMP"), 3);
        };
        Deck first = encounter.deck(supplier);
        assertSame(encounter.deck(supplier), first);
        assertEquals(rolls.get(), 1);
        assertSame(encounter.take(), first);
        assertNull(encounter.take(), "a prepared deck applies to one encounter only");
        encounter.deck(supplier);
        encounter.clear();
        assertNull(encounter.take(), "cancel cannot leave a stale deck for the next fight");
    }

    @Test
    public void cachedDeckDoesNotMutateTheTemplateAndTargetValidationRejectsStaleStakes() {
        PreparedCampaignEncounter encounter = new PreparedCampaignEncounter();
        Deck template = deckOf(card("Muscle Sliver", "TMP"), 3);
        Deck selected = encounter.deck(() -> template);
        selected.getMain().remove(card("Muscle Sliver", "TMP"));
        assertEquals(template.getMain().countAll(), 3);
        var knowledge = new OpponentKnowledge();
        knowledge.observe("rival", card("Muscle Sliver", "TMP"));
        var stake = AnteService.targetedStake(deckOf(card("Lightning Bolt", "M10"), 3), template,
                card("Muscle Sliver", "TMP"), knowledge, "rival", CampaignConfig.instance().ante, new java.util.Random(1));
        AnteService.validateTargetedStake(stake, template, knowledge, "rival");
        assertThrows(IllegalArgumentException.class, () -> AnteService.validateTargetedStake(stake, new Deck(), knowledge, "rival"));
    }

    @Test
    public void observedKnowledgeAndPreviousOwnershipSurviveCampaignSaveAndPromotion() throws Exception {
        CampaignState old = CampaignState.instance();
        try {
            CampaignState state = new CampaignState();
            CampaignState.setInstance(state);
            var enemy = new forge.adventure.data.EnemyData();
            enemy.name = "Goblin Wizard";
            var lost = card("Lightning Bolt", "M10");
            String rival = state.onMatchEnd(null, enemy, false, java.util.List.of(), java.util.List.of(lost), null);
            state.knowledge().observe(rival, card("Muscle Sliver", "TMP"));
            assertTrue(state.knowledge().known(rival).contains(lost), "previous ownership is knowledge");
            CampaignState loaded = new CampaignState();
            loaded.load(PhysicalIdentityTest.roundTrip(state.save()));
            assertEquals(new java.util.HashSet<>(loaded.knowledge().known(rival)), new java.util.HashSet<>(state.knowledge().known(rival)));
            loaded.load(null);
            assertTrue(loaded.knowledge().known(rival).isEmpty());
        } finally { CampaignState.setInstance(old); }
    }
}
