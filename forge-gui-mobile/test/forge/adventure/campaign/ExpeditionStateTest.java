package forge.adventure.campaign;

import forge.adventure.player.AdventurePlayer;
import forge.adventure.util.SaveFileData;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.*;

/**
 * Milestone 4: during an expedition only deck + sideboard and cards acquired on the way are
 * usable; losses disappear; returning home restores the full permanent collection.
 */
public class ExpeditionStateTest extends AdventureTestBase {
    private CampaignState state;

    @BeforeMethod
    public void freshState() {
        state = new CampaignState();
        CampaignState.setInstance(state);
    }

    @AfterMethod
    public void resetState() {
        CampaignState.setInstance(new CampaignState());
    }

    private AdventurePlayer playerWithHomeCollection() {
        PaperCard bolt = card("Lightning Bolt", "M10");
        PaperCard mountain = card("Mountain", "M10");
        PaperCard angel = card("Serra Angel", "7ED");
        PaperCard wrath = card("Wrath of God", "7ED");
        Deck deck = deckOf(bolt, 3, mountain, 37);
        deck.getOrCreate(DeckSection.Sideboard).add(angel, 1);
        AdventurePlayer player = newPlayer(deck);
        player.addCard(bolt, 1);   // 4th bolt stays at home
        player.addCard(wrath, 2);  // home-only cards
        return player;
    }

    @Test
    public void enteringHidesTheHomeCollectionButKeepsDeckAndSideboard() {
        AdventurePlayer player = playerWithHomeCollection();
        PaperCard bolt = card("Lightning Bolt", "M10");
        PaperCard angel = card("Serra Angel", "7ED");
        PaperCard wrath = card("Wrath of God", "7ED");

        state.enterExpedition("stronghold", player);
        CardPool available = state.availableCards(player);

        assertTrue(state.expedition().isActive());
        assertEquals(available.count(bolt), 3, "only the 3 carried bolts, not the 4th at home");
        assertEquals(available.count(angel), 1, "sideboard is carried");
        assertEquals(available.count(wrath), 0, "home collection hidden");
        assertEquals(player.getCards().count(wrath), 2, "...but still owned");
        assertTrue(DeckValidator.isValid(player.getSelectedDeck(), available, CampaignConfig.instance()));
    }

    @Test
    public void cardsAcquiredDuringTheExpeditionBecomeAvailable() {
        AdventurePlayer player = playerWithHomeCollection();
        PaperCard queen = card("Sliver Queen", "STH");
        state.enterExpedition("stronghold", player);

        player.addCard(queen, 1);

        assertEquals(state.availableCards(player).count(queen), 1);
        assertEquals(state.expedition().getAcquired().count(queen), 1);
    }

    @Test
    public void lostCardsDisappearFromTheExpeditionPool() {
        AdventurePlayer player = playerWithHomeCollection();
        PaperCard bolt = card("Lightning Bolt", "M10");
        state.enterExpedition("stronghold", player);

        assertTrue(CardOwnership.removeOne(player, bolt));

        assertEquals(state.availableCards(player).count(bolt), 2, "one carried copy gone");
        assertEquals(player.getCards().count(bolt), 3, "the 4th copy at home is untouched");
        List<String> problems = DeckValidator.problems(player.getSelectedDeck(), state.availableCards(player), CampaignConfig.instance());
        assertFalse(problems.isEmpty(), "deck is now short");
    }

    @Test
    public void acquiredCopiesAreSpentBeforeCarriedOnes() {
        AdventurePlayer player = playerWithHomeCollection();
        PaperCard bolt = card("Lightning Bolt", "M10");
        state.enterExpedition("stronghold", player);
        player.addCard(bolt, 1);
        assertEquals(state.availableCards(player).count(bolt), 4);

        CardOwnership.removeOne(player, bolt);

        assertEquals(state.expedition().getAcquired().count(bolt), 0);
        assertEquals(state.expedition().getCarried().count(bolt), 3);
        assertEquals(state.availableCards(player).count(bolt), 3);
    }

    @Test
    public void sellingDuringAnExpeditionOnlyOffersAvailableCopies() {
        AdventurePlayer player = playerWithHomeCollection();
        PaperCard wrath = card("Wrath of God", "7ED");
        state.enterExpedition("stronghold", player);

        assertEquals(player.getSellableCards().count(wrath), 0, "home-only cards cannot be sold while away");
        state.leaveExpedition("test");
        assertEquals(player.getSellableCards().count(wrath), 2);
    }

    @Test
    public void returningHomeRestoresEverythingThatSurvived() {
        AdventurePlayer player = playerWithHomeCollection();
        PaperCard bolt = card("Lightning Bolt", "M10");
        PaperCard wrath = card("Wrath of God", "7ED");
        PaperCard queen = card("Sliver Queen", "STH");
        state.enterExpedition("stronghold", player);
        player.addCard(queen, 1);
        CardOwnership.removeOne(player, bolt);

        state.leaveExpedition("retreat");
        CardPool available = state.availableCards(player);

        assertFalse(state.expedition().isActive());
        assertEquals(available.count(wrath), 2, "home cards back");
        assertEquals(available.count(queen), 1, "expedition gains kept");
        assertEquals(available.count(bolt), 3, "the lost copy stays lost");
    }

    @Test
    public void expeditionStateSurvivesSaveAndLoad() {
        AdventurePlayer player = playerWithHomeCollection();
        PaperCard queen = card("Sliver Queen", "STH").getFoiled();
        PaperCard wrath = card("Wrath of God", "7ED");
        state.enterExpedition("stronghold", player);
        player.addCard(queen, 1);

        SaveFileData data = state.save();
        CampaignState loaded = new CampaignState();
        loaded.load(data);

        assertTrue(loaded.expedition().isActive());
        assertEquals(loaded.expedition().getRegionId(), "stronghold");
        CardPool available = loaded.availableCards(player);
        assertEquals(available.count(queen), 1, "foil acquired card survives");
        assertEquals(available.count(wrath), 0, "home collection still hidden after load");
        assertEquals(available.count(card("Lightning Bolt", "M10")), 3);
    }
}
