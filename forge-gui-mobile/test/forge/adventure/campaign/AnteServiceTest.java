package forge.adventure.campaign;

import forge.adventure.player.AdventurePlayer;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Random;

import static org.testng.Assert.*;

/**
 * Milestone 2: normal ante eligibility, random selection and ownership transfer.
 */
public class AnteServiceTest extends AdventureTestBase {

    private static CampaignConfig.AnteRules rules() {
        CampaignConfig.AnteRules rules = new CampaignConfig.AnteRules();
        rules.enabled = true;
        rules.excludeBasicLands = true;
        rules.playerAnteCount = 1;
        rules.opponentAnteCount = 1;
        rules.reclamationRiskCount = 3;
        return rules;
    }

    @Test
    public void ordinaryBasicLandsAreNotEligibleButFoilBasicsAre() {
        PaperCard plains = card("Plains", "7ED");
        PaperCard foilPlains = plains.getFoiled();
        PaperCard bolt = card("Lightning Bolt", "M10");
        Deck deck = deckOf(plains, 10, foilPlains, 1, bolt, 2);

        List<PaperCard> eligible = AnteService.eligiblePlayerCards(deck, rules());

        assertEquals(eligible.size(), 3, "2 bolts + 1 foil plains");
        assertFalse(eligible.contains(plains));
        assertTrue(eligible.contains(foilPlains));
    }

    @Test
    public void playerAnteDrawsFromMainDeckAndSideboardOnly() {
        PaperCard bolt = card("Lightning Bolt", "M10");
        PaperCard angel = card("Serra Angel", "7ED");
        PaperCard wrath = card("Wrath of God", "7ED");
        Deck deck = deckOf(bolt, 4);
        deck.getOrCreate(DeckSection.Sideboard).add(angel, 1);
        AdventurePlayer player = newPlayer(deck);
        player.addCard(wrath, 3); // in the collection but not carried in the deck

        Random random = new Random(42);
        for (int i = 0; i < 50; i++) {
            List<PaperCard> picked = AnteService.pickRandom(AnteService.eligiblePlayerCards(player.getSelectedDeck(), rules()), 1, random);
            assertEquals(picked.size(), 1);
            assertNotEquals(picked.get(0), wrath, "collection-only cards are never at stake");
        }
        List<PaperCard> all = AnteService.eligiblePlayerCards(player.getSelectedDeck(), rules());
        assertEquals(all.size(), 5, "4 bolts + 1 sideboard angel");
        assertTrue(all.contains(angel), "sideboard is at risk");
    }

    @Test
    public void pickRandomTakesDistinctCopiesWithoutReplacement() {
        PaperCard bolt = card("Lightning Bolt", "M10");
        PaperCard angel = card("Serra Angel", "7ED");
        List<PaperCard> eligible = List.of(bolt, bolt, angel);

        List<PaperCard> three = AnteService.pickRandom(eligible, 3, new Random(1));
        assertEquals(three.size(), 3);
        assertEquals(three.stream().filter(bolt::equals).count(), 2);
        assertEquals(AnteService.pickRandom(eligible, 5, new Random(1)).size(), 3, "cannot pick more than exist");
    }

    @Test
    public void normalStakeIsOneCardEachFromTheRightDecks() {
        PaperCard bolt = card("Lightning Bolt", "M10");
        PaperCard sliver = card("Sliver Queen", "STH");
        PaperCard swamp = card("Swamp", "STH");
        Deck mine = deckOf(bolt, 4);
        Deck theirs = deckOf(sliver, 1, swamp, 20);

        AnteStake stake = AnteService.normalStake(mine, theirs, rules(), new Random(7));

        assertEquals(stake.kind, AnteStake.Kind.NORMAL);
        assertEquals(stake.playerCards, List.of(bolt));
        assertEquals(stake.opponentCards, List.of(sliver), "opponent basics are excluded too");
    }

    @Test
    public void applyingALossRemovesExactlyOneCopyAndInvalidatesTheDeck() {
        PaperCard bolt = card("Lightning Bolt", "M10");
        PaperCard mountain = card("Mountain", "M10");
        Deck deck = deckOf(bolt, 3, mountain, 37); // exactly the 40-card minimum
        AdventurePlayer player = newPlayer(deck);
        CampaignConfig config = CampaignConfig.instance();
        assertTrue(DeckValidator.isValid(player.getSelectedDeck(), player.getCards(), config), "starts legal");

        List<PaperCard> removed = AnteService.applyResult(player, List.of(), List.of(bolt));

        assertEquals(removed, List.of(bolt));
        assertEquals(player.getCards().count(bolt), 2);
        assertEquals(player.getSelectedDeck().getMain().count(bolt), 2, "deck lost its extra copy");
        List<String> problems = DeckValidator.problems(player.getSelectedDeck(), player.getCards(), config);
        assertEquals(problems.size(), 1, problems.toString());
        assertTrue(problems.get(0).contains("39"), "deck is now one card short: " + problems.get(0));
    }

    @Test
    public void applyingAWinAddsTheExactPrintingWon() {
        PaperCard bolt = card("Lightning Bolt", "M10");
        PaperCard foilQueen = card("Sliver Queen", "STH").getFoiled();
        AdventurePlayer player = newPlayer(deckOf(bolt, 4));

        AnteService.applyResult(player, List.of(foilQueen), List.of());

        assertEquals(player.getCards().count(foilQueen), 1);
        assertEquals(player.getCards().count(foilQueen.getUnFoiled()), 0, "printing identity kept");
        assertEquals(player.getCards().count(bolt), 4, "nothing else changed");
    }

    @Test
    public void reclamationStakeRisksThreeCardsAgainstTheChosenOne() {
        PaperCard bolt = card("Lightning Bolt", "M10");
        PaperCard angel = card("Serra Angel", "7ED");
        Deck deck = deckOf(bolt, 4, card("Mountain", "M10"), 20);

        AnteStake stake = AnteService.reclamationStake(deck, angel, rules(), new Random(3));

        assertEquals(stake.kind, AnteStake.Kind.RECLAMATION);
        assertEquals(stake.playerCards.size(), 3);
        assertTrue(stake.playerCards.stream().allMatch(bolt::equals), "only non-basic deck cards are risked");
        assertEquals(stake.opponentCards, List.of(angel));
    }
}
