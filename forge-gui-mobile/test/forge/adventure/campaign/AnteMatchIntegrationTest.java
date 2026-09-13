package forge.adventure.campaign;

import forge.ai.LobbyPlayerAi;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameOutcome;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.player.RegisteredPlayer;
import forge.item.PaperCard;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

/**
 * Milestone 2, end to end through the rules engine: a whole AI-vs-AI game played for ante with
 * pre-selected stakes ends with the winner owning exactly the loser's stake.
 */
public class AnteMatchIntegrationTest extends AdventureTestBase {

    private static Deck burnDeck() {
        return deckOf(card("Lightning Bolt", "M10"), 4, card("Mountain", "M10"), 20, card("Shock", "M10"), 4,
                card("Raging Goblin", "M10"), 4, card("Goblin Piker", "M10"), 4);
    }

    @Test(timeOut = 300000)
    public void wholeGamePlayedForAnteTransfersExactlyThePreselectedStakes() {
        PaperCard myStake = card("Serra Angel", "7ED").getFoiled();    // from "sideboard": not in the deck
        PaperCard theirStake = card("Sliver Queen", "STH");

        RegisteredPlayer p1 = new RegisteredPlayer(burnDeck()).setPlayer(new LobbyPlayerAi("p1", null));
        RegisteredPlayer p2 = new RegisteredPlayer(burnDeck()).setPlayer(new LobbyPlayerAi("p2", null));
        p1.setAnteCards(List.of(myStake));
        p2.setAnteCards(List.of(theirStake));
        p1.setPhysicalCards(java.util.Map.of());
        p2.setPhysicalCards(java.util.Map.of());
        p1.setAnteCardIds(List.of("my-stake-id"));
        p2.setAnteCardIds(List.of("their-stake-id"));
        List<RegisteredPlayer> players = new ArrayList<>(List.of(p1, p2));

        GameRules rules = new GameRules(GameType.Adventure);
        rules.setPlayForAnte(true);
        rules.setGamesPerMatch(1);
        rules.setManaBurn(false);
        Match match = new Match(rules, players, "Ante test");
        Game game = match.createGame();
        match.startGame(game);

        assertTrue(game.isGameOver());
        GameOutcome outcome = game.getOutcome();
        assertFalse(outcome.isDraw(), "burn mirror should not draw");
        RegisteredPlayer winner = outcome.getWinningPlayer();
        RegisteredPlayer loser = winner == p1 ? p2 : p1;
        PaperCard winnerStake = winner == p1 ? myStake : theirStake;
        PaperCard loserStake = winner == p1 ? theirStake : myStake;

        GameOutcome.AnteResult winnerResult = match.getAnteResult(winner);
        GameOutcome.AnteResult loserResult = match.getAnteResult(loser);
        assertEquals(winnerResult.wonCards, List.of(loserStake), "winner gains exactly the loser's stake");
        assertEquals(winnerResult.lostCards, List.of(), "winner keeps their own stake");
        assertEquals(loserResult.lostCards, List.of(loserStake));
        assertEquals(loserResult.wonCards, List.of());
        assertEquals(loserResult.lostCards.get(0).isFoil(), loserStake.isFoil(), "printing identity survives the game");
        assertNotEquals(winnerStake, loserStake);
        String lostId = loser == p1 ? "my-stake-id" : "their-stake-id";
        assertEquals(winnerResult.wonPhysicalCards.get(0).id, lostId);
        assertEquals(loserResult.lostPhysicalCards.get(0).id, lostId);
    }
}
