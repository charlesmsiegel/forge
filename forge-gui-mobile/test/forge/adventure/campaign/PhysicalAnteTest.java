package forge.adventure.campaign;

import forge.adventure.data.EnemyData;
import forge.adventure.player.AdventurePlayer;
import forge.ai.LobbyPlayerAi;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import java.util.*;
import static org.testng.Assert.*;

public class PhysicalAnteTest extends AdventureTestBase {
    @BeforeMethod public void reset() { CampaignState.setInstance(new CampaignState()); }
    @AfterMethod public void clear() { CampaignState.setInstance(new CampaignState()); }

    @Test
    public void engineKeepsDifferentIdenticalCopiesExchangedByOwnershipEffects() throws Exception {
        PaperCard bolt = card("Lightning Bolt", "M10");
        RegisteredPlayer a = new RegisteredPlayer(deckOf(bolt, 1)).setPlayer(new LobbyPlayerAi("a", null));
        RegisteredPlayer b = new RegisteredPlayer(deckOf(bolt, 1)).setPlayer(new LobbyPlayerAi("b", null));
        a.setPhysicalCards(Map.of("original-a", bolt));
        b.setPhysicalCards(Map.of("original-b", bolt));
        GameRules rules = new GameRules(GameType.Adventure);
        rules.setPlayForAnte(true);
        Match match = new Match(rules, new ArrayList<>(List.of(a, b)), "ownership exchange");
        Game game = match.createGame();
        var prepare = Match.class.getDeclaredMethod("prepareAllZones", Game.class);
        prepare.setAccessible(true);
        prepare.invoke(match, game);
        Player pa = game.getPlayers().get(0), pb = game.getPlayers().get(1);
        Card ca = pa.getCardsIn(ZoneType.Library).get(0), cb = pb.getCardsIn(ZoneType.Library).get(0);
        pb.changeOwnership(ca);
        pa.changeOwnership(cb);
        game.setGameOver(GameEndReason.Draw);
        var execute = Match.class.getDeclaredMethod("executeOwnershipChanges", Game.class);
        execute.setAccessible(true);
        execute.invoke(match, game);
        GameOutcome.AnteResult result = game.getOutcome().getAnteResult(a);
        assertTrue(result.wonCards.isEmpty(), "legacy printing-count cancellation stays compatible");
        assertTrue(result.lostCards.isEmpty());
        assertEquals(result.wonPhysicalCards.get(0).id, "original-b");
        assertEquals(result.lostPhysicalCards.get(0).id, "original-a");
    }

    @Test
    public void reclamationUsesSeparateStolenCopyEvenWhenTemplateHasIdenticalPrinting() {
        PaperCard bolt = card("Lightning Bolt", "M10");
        RegisteredPlayer a = new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi("a", null));
        RegisteredPlayer b = new RegisteredPlayer(new Deck()).setPlayer(new LobbyPlayerAi("b", null));
        a.setPhysicalCards(Map.of());
        b.setPhysicalCards(Map.of("template", bolt));
        a.setAnteCards(List.of());
        b.setAnteCards(List.of(bolt));
        b.setAnteCardIds(List.of("stolen"));
        GameRules rules = new GameRules(GameType.Adventure);
        Match match = new Match(rules, new ArrayList<>(List.of(a, b)), "reclamation");
        Game game = match.createGame();
        Player pb = game.getPlayers().get(1);
        Card template = Card.fromPaperCard(bolt, pb);
        template.setCollectible(true);
        pb.getZone(ZoneType.Library).add(template);
        game.registerPhysicalCard(template, null);
        Card selected = game.chooseCardsForAnte(false, false).get(pb).iterator().next();
        assertNotEquals(selected.getId(), template.getId());
        assertEquals(game.getPhysicalCard(selected).id, "stolen");
        assertEquals(game.getPhysicalCard(template).id, "template");
    }

    @Test
    public void lostIdSurvivesPromotionSerializationAndReclamation() throws Exception {
        PaperCard bolt = card("Lightning Bolt", "M10");
        AdventurePlayer player = newPlayer(deckOf(bolt, 1));
        CampaignState state = CampaignState.instance();
        OwnedCard original = state.ownedCopies(player).get(0);
        GameOutcome.AnteResult loss = new GameOutcome.AnteResult();
        loss.addLostPhysical(List.of(new GameOutcome.AnteCard(original.id, bolt)));
        AnteService.PhysicalResult applied = AnteService.applyResult(player, loss);
        EnemyData enemy = new EnemyData(); enemy.name = "Goblin Wizard";
        String rivalId = state.onPhysicalMatchEnd(null, enemy, false, applied, null);
        Rival rival = state.rivals().byId(rivalId);
        assertEquals(rival.getTakenCopies(), List.of(original));
        Rival loaded = new Rival(); loaded.load(PhysicalIdentityTest.roundTrip(rival.save()));
        assertEquals(loaded.getTakenCopies(), List.of(original));
        AnteStake stake = AnteService.reclamationStake(player.getSelectedDeck(), original,
                CampaignConfig.instance().ante, new Random(1));
        GameOutcome.AnteResult win = new GameOutcome.AnteResult();
        win.addWonPhysical(List.of(new GameOutcome.AnteCard(original.id, bolt)));
        AnteService.PhysicalResult gained = AnteService.applyResult(player, win);
        state.onPhysicalMatchEnd(rivalId, enemy, true, gained, stake);
        assertEquals(state.ownedCopies(player), List.of(original));
        assertTrue(rival.getTakenCopies().isEmpty());
    }

    @Test
    public void samePrintingExchangeReplacesTheLostIdAndPromotesItsHolderEvenAfterAWin() {
        PaperCard bolt = card("Lightning Bolt", "M10");
        AdventurePlayer player = newPlayer(deckOf(bolt, 1));
        CampaignState state = CampaignState.instance();
        OwnedCard original = state.ownedCopies(player).get(0);
        GameOutcome.AnteResult exchange = new GameOutcome.AnteResult();
        exchange.addLostPhysical(List.of(new GameOutcome.AnteCard(original.id, bolt)));
        exchange.addWonPhysical(List.of(new GameOutcome.AnteCard("opponent-copy", bolt)));
        AnteService.PhysicalResult applied = AnteService.applyResult(player, exchange);
        EnemyData enemy = new EnemyData(); enemy.name = "Goblin Wizard";
        String rivalId = state.onPhysicalMatchEnd(null, enemy, true, applied, null);
        assertEquals(player.getCards().count(bolt), 1);
        assertEquals(state.ownedCopies(player).get(0).id, "opponent-copy");
        assertEquals(state.rivals().byId(rivalId).getTakenCopies(), List.of(original));
        assertEquals(state.rivals().byId(rivalId).playerWins, 1);
    }
}
