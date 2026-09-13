package forge.adventure.campaign;

import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.*;

/**
 * Milestone 2: campaign deck legality used by the deck-repair gate.
 */
public class DeckValidatorTest extends AdventureTestBase {

    private static CampaignConfig config() {
        CampaignConfig config = new CampaignConfig();
        config.campaignId = "test";
        config.player.minDeckSize = 40;
        config.player.ordinaryCopyLimit = 3;
        config.player.restrictedCopyLimit = 1;
        config.cards.restrictedCards = new String[]{"Demonic Tutor"};
        config.cards.bannedCards = new String[]{"Chaos Orb"};
        return config;
    }

    private static Deck legalDeck() {
        return deckOf(card("Lightning Bolt", "M10"), 3, card("Mountain", "M10"), 37);
    }

    @Test
    public void aLegalDeckHasNoProblems() {
        Deck deck = legalDeck();
        assertEquals(DeckValidator.problems(deck, deck.getAllCardsInASinglePool(true, true), config()), List.of());
    }

    @Test
    public void tooFewCardsIsReported() {
        Deck deck = deckOf(card("Lightning Bolt", "M10"), 3, card("Mountain", "M10"), 36);
        List<String> problems = DeckValidator.problems(deck, null, config());
        assertEquals(problems.size(), 1);
        assertTrue(problems.get(0).contains("39") && problems.get(0).contains("40"), problems.get(0));
    }

    @Test
    public void copyLimitCountsAcrossPrintingsAndSideboard() {
        Deck deck = deckOf(card("Lightning Bolt", "M10"), 2, card("Lightning Bolt", "3ED"), 1, card("Mountain", "M10"), 37);
        deck.getOrCreate(DeckSection.Sideboard).add(card("Lightning Bolt", "M10"), 1);
        List<String> problems = DeckValidator.problems(deck, null, config());
        assertEquals(problems.size(), 1, problems.toString());
        assertTrue(problems.get(0).startsWith("Lightning Bolt: 4 copies, limit 3"), problems.get(0));
    }

    @Test
    public void ordinaryBasicLandsAreUnlimited() {
        Deck deck = deckOf(card("Mountain", "M10"), 40);
        assertTrue(DeckValidator.isValid(deck, null, config()));
    }

    @Test
    public void restrictedAndBannedCardsAreEnforced() {
        Deck deck = deckOf(card("Demonic Tutor", "3ED"), 2, card("Chaos Orb", "3ED"), 1, card("Swamp", "M10"), 37);
        List<String> problems = DeckValidator.problems(deck, null, config());
        assertEquals(problems.size(), 2, problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("Demonic Tutor") && p.contains("limit 1")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("Chaos Orb") && p.contains("banned")), problems.toString());
    }

    @Test
    public void cardsNotAvailableToThePlayerAreReported() {
        Deck deck = legalDeck();
        CardPool available = new CardPool();
        PaperCard bolt = card("Lightning Bolt", "M10");
        available.add(bolt, 2); // one copy short
        available.add(card("Mountain", "M10"), 37);
        List<String> problems = DeckValidator.problems(deck, available, config());
        assertEquals(problems.size(), 1, problems.toString());
        assertTrue(problems.get(0).contains("Lightning Bolt") && problems.get(0).contains("only 2 available"), problems.get(0));
    }
}
