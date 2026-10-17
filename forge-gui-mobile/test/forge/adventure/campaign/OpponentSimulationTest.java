package forge.adventure.campaign;

import forge.adventure.data.ConfigData;
import forge.ai.LobbyPlayerAi;
import forge.deck.Deck;
import forge.game.*;
import forge.game.player.RegisteredPlayer;
import forge.util.MyRandom;
import org.testng.annotations.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import static org.testng.Assert.*;

/** Small reproducible smoke sample, not a win-rate or optimal-pilot claim. */
public class OpponentSimulationTest extends AdventureTestBase {
    @Test(timeOut = 300000) public void adaptedAggroDecksFinishOrdinaryMagicGames() {
        CampaignConfig config = new CampaignConfig(); config.campaignId = "simulation";
        config.universe.allowedEditions = new String[]{"M10"};
        CardUniverse universe = CardUniverse.capture(config, new ConfigData(), null, LocalDate.of(2026,10,16));
        Deck skeleton = deckOf(card("Lightning Bolt", "LEA"), 4, card("Shock", "STH"), 4,
                card("Mogg Fanatic", "TMP"), 4, card("Goblin Piker", "M10"), 4, card("Mountain", "M10"), 20);
        var pool = List.of(card("Lightning Bolt", "M10"), card("Seismic Strike", "M10"), card("Raging Goblin", "M10"),
                card("Goblin Piker", "M10"), card("Mountain", "M10"));
        Random previous = MyRandom.getRandom();
        try {
            for (int seed : new int[]{1703, 1704}) {
                MyRandom.setRandom(new Random(seed));
                Deck adapted = EnemyDeckPolicy.adapt(skeleton, universe, pool, seed, 0);
                assertTrue(adapted.getMain().toFlatList().stream().allMatch(universe::allowsPrinting));
                var first = new RegisteredPlayer(adapted).setPlayer(new LobbyPlayerAi("adapted", null));
                var second = new RegisteredPlayer(adapted).setPlayer(new LobbyPlayerAi("control", null));
                first.setStartingLife(12); second.setStartingLife(12);
                GameRules rules = new GameRules(GameType.Adventure); rules.setGamesPerMatch(1); rules.setPlayForAnte(false);
                Match match = new Match(rules, List.of(first, second), "Opponent sample " + seed);
                Game game = match.createGame(); match.startGame(game);
                assertTrue(game.isGameOver()); assertNotNull(game.getOutcome());
                System.out.println("OPPONENT_SAMPLE seed=" + seed + " draw=" + game.getOutcome().isDraw()
                        + " turns=" + game.getPhaseHandler().getTurn()
                        + " winner=" + (game.getOutcome().getWinningPlayer() == first ? "adapted" : "control"));
            }
        } finally { MyRandom.setRandom(previous); }
    }
    @Test(timeOut = 300000) public void authoredLegendaryRecipesRunWithTheNormalForgePilot() {
        CampaignConfig config = CampaignConfig.load(PLANE_DIR + "campaign.json");
        CardUniverse universe = CardUniverse.capture(config, new ConfigData(), null, LocalDate.of(2026,10,16));
        Random previous = MyRandom.getRandom();
        try {
            int seed = 2300;
            for (String id : List.of("urza-challenge", "urza-conquest", "bolas-challenge", "bolas-conquest")) {
                MyRandom.setRandom(new Random(++seed));
                Deck recipe = CampaignStory.deck(config, id, universe);
                if (id.startsWith("urza")) assertTrue(recipe.getMain().toFlatList().stream().filter(c -> c.getRules().getType().isCreature()).count() >= 8, "Academy pilot needs independent artifact threats alongside its combo engine");
                Deck control = deckOf(card("Lightning Bolt", "M10"), 4, card("Seismic Strike", "M10"), 4,
                        card("Raging Goblin", "M10"), 4, card("Goblin Piker", "M10"), 4, card("Mountain", "M10"), 20);
                var boss = new RegisteredPlayer(recipe).setPlayer(new LobbyPlayerAi(id, null));
                var challenger = new RegisteredPlayer(control).setPlayer(new LobbyPlayerAi("aggro control", null));
                boss.setStartingLife(20); challenger.setStartingLife(12);
                GameRules rules = new GameRules(GameType.Adventure); rules.setGamesPerMatch(1); rules.setPlayForAnte(false);
                Match match = new Match(rules, List.of(boss, challenger), "Legendary pilot " + id);
                Game game = match.createGame(); match.startGame(game);
                assertTrue(game.isGameOver()); assertNotNull(game.getOutcome());
                System.out.println("LEGENDARY_SAMPLE id=" + id + " seed=" + seed + " draw=" + game.getOutcome().isDraw()
                        + " turns=" + game.getPhaseHandler().getTurn()
                        + " winner=" + (game.getOutcome().getWinningPlayer() == boss ? id : "aggro control"));
            }
        } finally { MyRandom.setRandom(previous); }
    }
}
