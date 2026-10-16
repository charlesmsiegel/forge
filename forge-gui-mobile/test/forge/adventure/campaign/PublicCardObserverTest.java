package forge.adventure.campaign;

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
import org.testng.annotations.Test;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;

public class PublicCardObserverTest extends AdventureTestBase {
    private Game game() {
        var a = new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi("human", null));
        var b = new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi("opponent", null));
        var players = new ArrayList<>(List.of(a, b));
        var rules = new GameRules(GameType.Adventure);
        return new Match(rules, players, "knowledge").createGame();
    }

    private Card add(Game game, Player owner, String name, ZoneType zone, boolean faceDown) {
        Card card = Card.fromPaperCard(card(name, "TMP"), owner);
        card.setCollectible(true);
        card.setGameTimestamp(game.getNextTimestamp());
        if (faceDown) card.turnFaceDown(true);
        owner.getZone(zone).add(card);
        return card;
    }

    @Test
    public void hiddenCardsAndFacedownPublicObjectsNeverLeakTheirIdentity() {
        Game game = game();
        Player human = game.getPlayers().get(0), opponent = game.getPlayers().get(1);
        PublicCardObserver observer = new PublicCardObserver(game, human);
        add(game, opponent, "Muscle Sliver", ZoneType.Library, false);
        add(game, opponent, "Winged Sliver", ZoneType.Hand, false);
        add(game, opponent, "Horned Sliver", ZoneType.Battlefield, true);
        add(game, human, "Metallic Sliver", ZoneType.Battlefield, false);
        observer.snapshot();
        assertTrue(observer.observations().isEmpty());
    }

    @Test
    public void publicZoneEventsRememberExactPrintingsEvenAfterTheyReturnToHiddenZones() {
        Game game = game();
        Player human = game.getPlayers().get(0), opponent = game.getPlayers().get(1);
        PublicCardObserver observer = new PublicCardObserver(game, human);
        Card played = add(game, opponent, "Muscle Sliver", ZoneType.Battlefield, false);
        assertEquals(observer.observations(), List.of(card("Muscle Sliver", "TMP")));
        opponent.getZone(ZoneType.Battlefield).remove(played);
        opponent.getZone(ZoneType.Hand).add(played);
        observer.snapshot();
        assertEquals(observer.observations(), List.of(card("Muscle Sliver", "TMP")));
    }

    @Test
    public void explicitViewingPermissionCanTeachAHiddenCardWithoutRevealingItsNeighbors() {
        Game game = game();
        Player human = game.getPlayers().get(0), opponent = game.getPlayers().get(1);
        PublicCardObserver observer = new PublicCardObserver(game, human);
        Card shown = add(game, opponent, "Winged Sliver", ZoneType.Hand, false);
        add(game, opponent, "Muscle Sliver", ZoneType.Hand, false);
        shown.addMayLookTemp(human);
        observer.snapshot();
        assertEquals(observer.observations(), List.of(card("Winged Sliver", "TMP")));
    }
    @Test
    public void temporaryRevealsAreRememberedOnlyByTheirActualRecipient() {
        Game game = game();
        Player human = game.getPlayers().get(0), opponent = game.getPlayers().get(1);
        PublicCardObserver observer = new PublicCardObserver(game, human);
        Card shown = add(game, opponent, "Winged Sliver", ZoneType.Hand, false);
        Card privateCard = add(game, opponent, "Muscle Sliver", ZoneType.Hand, false);
        game.getAction().revealTo(privateCard, opponent);
        assertTrue(observer.observations().isEmpty(), "a reveal to another player is private");
        game.getAction().revealTo(shown, human);
        assertEquals(observer.observations(), List.of(card("Winged Sliver", "TMP")), "a brief reveal need not span a priority event");
    }

    @Test
    public void hostedGameCreationInstallsObserverBeforeAnySetupOrReveal() {
        class TestHost extends forge.gamemodes.match.HostedMatch {
            Game create(Match match) { return createGame(match); }
        }
        var a = new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi("human", null));
        var b = new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi("opponent", null));
        var match = new Match(new GameRules(GameType.Adventure), new ArrayList<>(List.of(a, b)), "early observer");
        var observer = new java.util.concurrent.atomic.AtomicReference<PublicCardObserver>();
        TestHost host = new TestHost();
        host.setGameCreatedHook(created -> observer.set(new PublicCardObserver(created, created.getPlayers().get(0))));
        Game game = host.create(match);
        assertNotNull(observer.get(), "subscription is synchronous with game creation");
        Card shown = add(game, game.getPlayers().get(1), "Winged Sliver", ZoneType.Hand, false);
        game.getAction().revealTo(shown, game.getPlayers().get(0));
        assertEquals(observer.get().observations(), List.of(card("Winged Sliver", "TMP")));
    }

}
