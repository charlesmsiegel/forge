package forge.adventure.campaign;

import com.google.common.collect.Multimap;
import forge.ai.LobbyPlayerAi;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

/**
 * Milestone 2, rules-engine side: when a RegisteredPlayer carries pre-selected ante cards the
 * engine antes exactly those (library copies when present, otherwise the card is added).
 */
public class PreselectedAnteTest extends AdventureTestBase {

    private static Game newGame(List<RegisteredPlayer> players) {
        GameRules rules = new GameRules(GameType.Adventure);
        rules.setPlayForAnte(true);
        Match match = new Match(rules, players, "Test");
        return new Game(players, rules, match);
    }

    private static Card addToLibrary(Game game, Player p, PaperCard pc) {
        Card c = Card.fromPaperCard(pc, p);
        c.setCollectible(true);
        c.setGameTimestamp(game.getNextTimestamp());
        p.getZone(ZoneType.Library).add(c);
        return c;
    }

    @Test
    public void preselectedCardsAreAntedInsteadOfRandomOnes() {
        PaperCard bolt = card("Lightning Bolt", "M10");
        PaperCard mountain = card("Mountain", "M10");
        PaperCard angel = card("Serra Angel", "7ED"); // sideboard card: not in the library
        PaperCard queen = card("Sliver Queen", "STH");

        RegisteredPlayer human = new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi("human", null));
        RegisteredPlayer ai = new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi("ai", null));
        List<RegisteredPlayer> players = new ArrayList<>(List.of(human, ai));
        Game game = newGame(players);
        Player pHuman = game.getPlayers().get(0);
        Player pAi = game.getPlayers().get(1);

        Card boltInLibrary = addToLibrary(game, pHuman, bolt);
        for (int i = 0; i < 5; i++) addToLibrary(game, pHuman, mountain);
        Card queenInLibrary = addToLibrary(game, pAi, queen);
        for (int i = 0; i < 5; i++) addToLibrary(game, pAi, card("Swamp", "STH"));

        human.setAnteCards(List.of(bolt, angel));
        ai.setAnteCards(List.of(queen));

        Multimap<Player, Card> anted = game.chooseCardsForAnte(true, false);

        List<Card> humanAnte = new ArrayList<>(anted.get(pHuman));
        assertEquals(humanAnte.size(), 2);
        assertTrue(humanAnte.contains(boltInLibrary), "the existing library copy is used");
        Card antedAngel = humanAnte.stream().filter(c -> c != boltInLibrary).findFirst().orElseThrow();
        assertEquals(antedAngel.getPaperCard(), angel, "card from outside the library is created");
        assertTrue(antedAngel.isCollectible());
        assertTrue(pHuman.getCardsIn(ZoneType.Library).contains(antedAngel), "and lives in the library until moved to ante");

        assertEquals(new ArrayList<>(anted.get(pAi)), List.of(queenInLibrary));
    }

    @Test
    public void twoCopiesOfOnePrintingResolveToTwoDifferentLibraryCards() {
        PaperCard bolt = card("Lightning Bolt", "M10");
        RegisteredPlayer human = new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi("human", null));
        RegisteredPlayer ai = new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi("ai", null));
        Game game = newGame(new ArrayList<>(List.of(human, ai)));
        Player pHuman = game.getPlayers().get(0);
        Card first = addToLibrary(game, pHuman, bolt);
        Card second = addToLibrary(game, pHuman, bolt);
        addToLibrary(game, game.getPlayers().get(1), card("Swamp", "STH"));

        human.setAnteCards(List.of(bolt, bolt));
        ai.setAnteCards(List.of());

        List<Card> anted = new ArrayList<>(game.chooseCardsForAnte(false, false).get(pHuman));
        assertEquals(anted.size(), 2);
        assertTrue(anted.contains(first) && anted.contains(second));
        assertEquals(pHuman.getCardsIn(ZoneType.Library).size(), 2, "nothing was created");
    }

    @Test
    public void playersWithoutPreselectionStillAnteRandomly() {
        RegisteredPlayer human = new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi("human", null));
        RegisteredPlayer ai = new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi("ai", null));
        Game game = newGame(new ArrayList<>(List.of(human, ai)));
        Player pHuman = game.getPlayers().get(0);
        Player pAi = game.getPlayers().get(1);
        Card bolt = addToLibrary(game, pHuman, card("Lightning Bolt", "M10"));
        addToLibrary(game, pAi, card("Swamp", "STH"));
        Card queen = addToLibrary(game, pAi, card("Sliver Queen", "STH"));

        human.setAnteCards(List.of(card("Lightning Bolt", "M10")));
        // ai: no pre-selection

        Multimap<Player, Card> anted = game.chooseCardsForAnte(false, false);
        assertEquals(new ArrayList<>(anted.get(pHuman)), List.of(bolt));
        assertEquals(new ArrayList<>(anted.get(pAi)), List.of(queen), "random pick excludes basic lands");
    }
}
