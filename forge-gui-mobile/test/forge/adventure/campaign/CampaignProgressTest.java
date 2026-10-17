package forge.adventure.campaign;

import forge.adventure.player.AdventurePlayer;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class CampaignProgressTest extends AdventureTestBase {
    static CampaignConfig.ProgressionRules rules() {
        var rules = new CampaignConfig.ProgressionRules();
        var first = new CampaignConfig.Milestone();
        first.id = "rath-first"; first.realm = "rath"; first.fullClears = 1;
        first.ordinaryCopyLimit = 4; first.maxLifeBonus = 2; first.protectionSlots = 1;
        var second = new CampaignConfig.Milestone();
        second.id = "rath-second"; second.realm = "rath"; second.fullClears = 2;
        second.protectionSlots = 2; second.restrictedChoiceTokens = 1;
        var third = new CampaignConfig.Milestone();
        third.id = "rath-third"; third.realm = "rath"; third.fullClears = 3;
        third.protectionSlots = 3; third.masterCollector = true;
        rules.milestones = new CampaignConfig.Milestone[]{first, second, third};
        return rules;
    }

    @Test
    public void milestonesAwardLifeAndAbilitiesOnceAcrossSaveAndDifferentRealms() throws Exception {
        CampaignProgress progress = new CampaignProgress();
        AdventurePlayer player = newPlayer(deckOf(card("Lightning Bolt", "M10"), 3));
        int life = player.getMaxLife();
        progress.onFullClear("other", player, rules());
        assertEquals(player.getMaxLife(), life);
        progress.onFullClear("rath", player, rules());
        assertEquals(player.getMaxLife(), life + 2);
        assertEquals(progress.ordinaryLimit(3), 4);
        assertEquals(progress.protectionSlots(), 1);
        CampaignProgress loaded = new CampaignProgress();
        loaded.load(PhysicalIdentityTest.roundTrip(progress.save()));
        loaded.onFullClear("rath", player, rules());
        assertEquals(player.getMaxLife(), life + 2, "first clear life is not re-awarded");
        assertEquals(loaded.protectionSlots(), 2);
        assertEquals(loaded.restrictedChoiceTokens(), 1);
        loaded.onFullClear("rath", player, rules());
        assertTrue(loaded.isMasterCollector());
        assertEquals(loaded.protectionSlots(), 3);
    }

    @Test
    public void masterCollectorGroupsOrdinaryPrintingsButRestrictedCardsStillCountByName() {
        CampaignState original = CampaignState.instance();
        CampaignConfig config = CampaignConfig.instance();
        try {
            CampaignState state = new CampaignState(); CampaignState.setInstance(state);
            var deck = deckOf(card("Lightning Bolt", "M10"), 3, card("Lightning Bolt", "3ED"), 3, card("Mountain", "M10"), 34);
            var player = newPlayer(deck);
            assertFalse(DeckValidator.isValid(deck, player.getCards(), config));
            for (int i = 0; i < 3; i++) state.progression().onFullClear("rath", player, rules());
            assertTrue(DeckValidator.isValid(deck, player.getCards(), config), "ordinary printings count separately");
            var restricted = deckOf(card("Sol Ring", "3ED"), 1, card("Sol Ring", "C14"), 1, card("Mountain", "M10"), 38);
            assertFalse(DeckValidator.isValid(restricted, restricted.getMain(), config));
            state.progression().chooseRestrictedCopy("Sol Ring", config, restricted.getMain());
            assertTrue(DeckValidator.isValid(restricted, restricted.getMain(), config));
            assertEquals(state.progression().restrictedChoiceTokens(), 0);
        } finally { CampaignState.setInstance(original); }
    }

    @Test
    public void namedExemptionsAndRestrictedChoiceAreExplicitAndPersisted() throws Exception {
        var progress = new CampaignProgress();
        var player = newPlayer(deckOf(card("Sol Ring", "3ED"), 1));
        assertThrows(IllegalArgumentException.class, () -> progress.chooseRestrictedCopy("Sol Ring", CampaignConfig.instance(), player.getCards()));
        progress.onFullClear("rath", player, rules());
        progress.onFullClear("rath", player, rules());
        assertThrows(IllegalArgumentException.class, () -> progress.chooseRestrictedCopy("Lightning Bolt", CampaignConfig.instance(), player.getCards()));
        progress.chooseRestrictedCopy("Sol Ring", CampaignConfig.instance(), player.getCards());
        progress.grantCopyExemption("Lightning Bolt", 5);
        var loaded = new CampaignProgress(); loaded.load(PhysicalIdentityTest.roundTrip(progress.save()));
        assertEquals(loaded.copyLimit("Sol Ring", 1), 2);
        assertEquals(loaded.copyLimit("Lightning Bolt", 3), 5);
        loaded.load(null);
        assertEquals(loaded.ordinaryLimit(3), 3);
        assertEquals(loaded.copyLimit("Sol Ring", 1), 1);
    }
}
