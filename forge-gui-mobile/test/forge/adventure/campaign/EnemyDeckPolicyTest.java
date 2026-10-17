package forge.adventure.campaign;

import forge.adventure.data.ConfigData;
import forge.deck.Deck;
import org.testng.annotations.Test;
import java.time.LocalDate;
import java.util.List;
import static org.testng.Assert.*;

public class EnemyDeckPolicyTest extends AdventureTestBase {
    private CardUniverse universe() {
        CampaignConfig config = new CampaignConfig(); config.campaignId = "opponents";
        config.universe.allowedEditions = new String[]{"STH"};
        config.cards.restrictedCards = new String[]{"Shock"};
        return CardUniverse.capture(config, new ConfigData(), null, LocalDate.of(2026,10,16));
    }
    @Test public void substitutionsPreserveCurveColorAndFrozenPrintingRules() {
        var universe = universe();
        Deck skeleton = deckOf(card("Lightning Bolt", "LEA"), 4, card("Mogg Fanatic", "TMP"), 4,
                card("Mountain", "TMP"), 12);
        var pool = List.of(card("Shock", "STH"), card("Fling", "STH"), card("Mogg Flunkies", "STH"),
                card("Mountain", "TMP"), card("Counterspell", "TMP"));
        Deck result = EnemyDeckPolicy.adapt(skeleton, universe, pool, 77, 0);
        assertEquals(result.getMain().countAll(), 20);
        assertEquals(result.getMain().toFlatList().stream().filter(c -> c.getRules().getType().isLand()).count(), 12L);
        assertTrue(result.getMain().toFlatList().stream().allMatch(universe::allowsPrinting));
        assertTrue(result.getMain().toFlatList().stream().filter(c -> c.getName().equals("Shock")).count() <= 1);
        assertEquals(result.getMain().toCardList("\n"), EnemyDeckPolicy.adapt(skeleton, universe, pool, 77, 0).getMain().toCardList("\n"));
        assertEquals(skeleton.getMain().count(card("Lightning Bolt", "LEA")), 4, "do not mutate curated skeleton");
    }
    @Test public void scarcePoolsFailExplicitlyInsteadOfUsingForbiddenCards() {
        assertThrows(IllegalArgumentException.class, () -> EnemyDeckPolicy.adapt(new Deck("missing"), universe(), List.of(), 1, 0));
        assertThrows(IllegalArgumentException.class, () -> EnemyDeckPolicy.adapt(
                deckOf(card("Mogg Fanatic", "TMP"), 4), universe(), List.of(card("Counterspell", "TMP")), 1, 0));
    }
    @Test public void rivalUpgradeMilestonesAreBoundedAndSaved() {
        Rival rival = new Rival("r1", "Test", "Adept Red");
        assertEquals(rival.upgradeTier(), 0);
        rival.playerWins = 3; assertEquals(rival.upgradeTier(), 1);
        rival.playerWins = 6; assertEquals(rival.upgradeTier(), 2);
        Rival loaded = new Rival(); loaded.load(rival.save());
        assertEquals(loaded.upgradeTier(), 2);
        assertEquals(loaded.deckSeed(), rival.deckSeed());
        loaded.playerWins = 100; assertEquals(loaded.upgradeTier(), 2);
    }
    @Test public void rivalDeckRecipeSurvivesReloadAndPoolChanges() {
        var universe = universe();
        Rival rival = new Rival("recipe", "Recipe", "Adept Red");
        Deck skeleton = deckOf(card("Lightning Bolt", "LEA"), 4, card("Mountain", "TMP"), 12);
        var pool = List.of(card("Shock", "STH"), card("Fling", "STH"), card("Mountain", "TMP"));
        Deck chosen = rival.opponentDeck(skeleton, universe, pool);
        Rival loaded = new Rival(); loaded.load(rival.save());
        assertEquals(loaded.opponentDeck(skeleton, universe, List.of()).getMain().toCardList("\n"), chosen.getMain().toCardList("\n"));
        chosen.getMain().clear();
        assertEquals(loaded.opponentDeck(skeleton, universe, List.of()).getMain().countAll(), 16);
    }
    @Test public void upgradesAndAcquiredCardsStayWithinTheTemplateManaPlan() {
        var universe = universe();
        Deck template = deckOf(card("Mogg Flunkies", "STH"), 4, card("Fling", "STH"), 4, card("Mountain", "TMP"), 12);
        var pool = List.of(card("Mogg Flunkies", "STH"), card("Mogg Maniac", "STH"), card("Fling", "STH"), card("Shock", "STH"));
        Deck upgraded = EnemyDeckPolicy.adapt(template, universe, pool, 19, 1);
        assertEquals(upgraded.getMain().count(card("Mogg Maniac", "STH")), 2);
        Deck acquired = EnemyDeckPolicy.withAcquired(upgraded, universe, List.of(card("Shock", "STH"), card("Counterspell", "TMP")));
        assertEquals(acquired.getMain().countAll(), 20);
        assertEquals(acquired.getMain().count(card("Shock", "STH")), 1);
        assertEquals(acquired.getMain().count(card("Counterspell", "TMP")), 0);
        assertEquals(upgraded.getMain().count(card("Shock", "STH")), 0);
    }
    @Test public void worldMilestonesRaiseRivalBudgetsWithoutResettingTheirHistory() {
        Rival rival = new Rival("world", "World", "Adept Blue"); WorldDeeds deeds = new WorldDeeds();
        rival.advanceForWorld(deeds); assertEquals(rival.upgradeTier(), 0);
        deeds.grant("shandalar.champion"); rival.advanceForWorld(deeds); assertEquals(rival.upgradeTier(), 1);
        deeds.grant("seal.broken"); rival.advanceForWorld(deeds); assertEquals(rival.upgradeTier(), 2);
        Rival loaded = new Rival(); loaded.load(rival.save()); loaded.advanceForWorld(new WorldDeeds());
        assertEquals(loaded.upgradeTier(), 2);
    }
    @Test public void modernSnapshotReprintsHistoricalSkeletonWithoutCrossingItsBoundary() {
        CampaignConfig config = new CampaignConfig(); config.campaignId = "modern-opponents";
        config.universe.preset = "Modern";
        CardUniverse modern = CardUniverse.capture(config, new ConfigData(), forge.model.FModel.getFormats().getModern(), LocalDate.of(2026,10,16));
        Deck template = deckOf(card("Lightning Bolt", "LEA"), 4, card("Mogg Fanatic", "TMP"), 4, card("Mountain", "M10"), 12);
        Deck adapted = EnemyDeckPolicy.adapt(template, modern, List.of(card("Lightning Bolt", "M10"), card("Mogg Fanatic", "10E"), card("Mountain", "M10")), 9, 0);
        assertTrue(adapted.getMain().toFlatList().stream().allMatch(modern::allowsPrinting));
        assertEquals(adapted.getMain().countAll(), 20);
        assertEquals(adapted.getMain().count(card("Lightning Bolt", "LEA")), 0);
    }
}
