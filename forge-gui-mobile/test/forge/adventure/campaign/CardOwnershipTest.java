package forge.adventure.campaign;

import forge.adventure.player.AdventurePlayer;
import forge.adventure.util.SaveFileData;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Milestone 1: physical copies are units of an exact printing; ownership changes move exactly
 * one unit and keep edition / collector number / foil identity.
 */
public class CardOwnershipTest extends AdventureTestBase {

    @Test
    public void identicalPrintingsAreCountedAsSeparateCopies() {
        PaperCard bolt = card("Lightning Bolt", "M10");
        AdventurePlayer player = newPlayer(deckOf(bolt, 2));

        assertEquals(player.getCards().count(bolt), 2);
        assertTrue(CardOwnership.removeOne(player, bolt));
        assertEquals(player.getCards().count(bolt), 1, "exactly one unit removed");
        assertTrue(CardOwnership.removeOne(player, bolt));
        assertEquals(player.getCards().count(bolt), 0);
        assertFalse(CardOwnership.removeOne(player, bolt), "nothing left to remove");
    }

    @Test
    public void differentPrintingsOfTheSameNameStayDistinct() {
        PaperCard boltM10 = card("Lightning Bolt", "M10");
        PaperCard bolt3ed = card("Lightning Bolt", "3ED");
        assertNotEquals(boltM10, bolt3ed);
        AdventurePlayer player = newPlayer(deckOf(boltM10, 1, bolt3ed, 1));

        assertEquals(player.getCards().countByName("Lightning Bolt"), 2, "same card for copy limits");
        CardOwnership.removeOne(player, boltM10);
        assertEquals(player.getCards().count(boltM10), 0);
        assertEquals(player.getCards().count(bolt3ed), 1, "the other printing is untouched");
    }

    @Test
    public void removingACopyTrimsDecksThatWouldExceedTheRemainingCopies() {
        PaperCard angel = card("Serra Angel", "7ED");
        PaperCard plains = card("Plains", "7ED");
        AdventurePlayer player = newPlayer(deckOf(angel, 2, plains, 10));
        Deck deck = player.getSelectedDeck();
        // Move one Angel to the sideboard so both sections are exercised.
        deck.getMain().remove(angel, 1);
        deck.getOrCreate(DeckSection.Sideboard).add(angel, 1);

        CardOwnership.removeOne(player, angel);

        assertEquals(player.getCards().count(angel), 1);
        assertEquals(deck.count(angel), 1, "deck may only reference the one remaining copy");
        assertEquals(deck.getMain().count(angel), 1, "sideboard copy is dropped before main deck");
        assertEquals(deck.getMain().count(plains), 10, "other cards untouched");
    }

    @Test
    public void transferBetweenPoolsPreservesTheExactPrinting() {
        PaperCard foilAngel = card("Serra Angel", "7ED").getFoiled();
        PaperCard plainAngel = card("Serra Angel", "7ED");
        assertTrue(foilAngel.isFoil());
        CardPool player = new CardPool();
        player.add(foilAngel, 1);
        player.add(plainAngel, 1);
        CardPool rival = new CardPool();

        assertTrue(CardOwnership.transferOne(player, rival, foilAngel));

        assertEquals(player.count(foilAngel), 0);
        assertEquals(player.count(plainAngel), 1, "the non-foil copy stays");
        assertEquals(rival.count(foilAngel), 1);
        PaperCard received = rival.toFlatList().get(0);
        assertEquals(received.getEdition(), "7ED");
        assertEquals(received.getCollectorNumber(), foilAngel.getCollectorNumber());
        assertTrue(received.isFoil(), "foil identity survives the transfer");
        assertFalse(CardOwnership.transferOne(player, rival, foilAngel), "no second foil copy to move");
    }

    @Test
    public void saveAndLoadRoundTripPreservesPrintings() {
        PaperCard boltM10 = card("Lightning Bolt", "M10");
        PaperCard bolt3ed = card("Lightning Bolt", "3ED");
        PaperCard foilAngel = card("Serra Angel", "7ED").getFoiled();
        AdventurePlayer player = newPlayer(deckOf(boltM10, 2, bolt3ed, 1, foilAngel, 1));
        CardOwnership.removeOne(player, boltM10);

        SaveFileData data = player.save();
        AdventurePlayer loaded = new AdventurePlayer();
        loaded.load(data);

        assertEquals(loaded.getCards().count(boltM10), 1);
        assertEquals(loaded.getCards().count(bolt3ed), 1);
        assertEquals(loaded.getCards().count(foilAngel), 1, "foil printing survives save/load");
        assertEquals(loaded.getCards().count(foilAngel.getUnFoiled()), 0);
        assertEquals(loaded.getSelectedDeck().getMain().count(boltM10), 1, "deck was trimmed before save");
    }
}
