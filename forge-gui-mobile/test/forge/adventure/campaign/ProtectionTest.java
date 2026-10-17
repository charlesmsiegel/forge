package forge.adventure.campaign;

import forge.adventure.player.AdventurePlayer;
import forge.ai.LobbyPlayerAi;
import forge.deck.Deck;
import forge.game.GameOutcome;
import forge.game.player.RegisteredPlayer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import java.util.List;
import java.util.Random;
import static org.testng.Assert.*;

public class ProtectionTest extends AdventureTestBase {
    private CampaignState old;
    private CampaignState state;
    private AdventurePlayer player;

    @BeforeMethod
    public void setup() {
        old = CampaignState.instance();
        state = new CampaignState(); CampaignState.setInstance(state);
        player = newPlayer(deckOf(card("Lightning Bolt", "M10"), 1, card("Mountain", "M10"), 39));
        state.progression().onFullClear("rath", player, CampaignProgressTest.rules());
    }

    @AfterMethod
    public void cleanup() { CampaignState.setInstance(old); }

    @Test
    public void identicalUnprotectedCopyIsSelectedAndBoundInsteadOfProtectedOne() {
        OwnedCard protectedCopy = state.ownedCopies(player).stream().filter(c -> c.card.getName().equals("Lightning Bolt")).findFirst().orElseThrow();
        state.progression().protect(protectedCopy, state.availableCopies(player));
        player.addCard(protectedCopy.card);
        Deck enemy = deckOf(card("Muscle Sliver", "TMP"), 3);
        AnteStake stake = AnteService.normalStake(player, enemy, CampaignConfig.instance().ante, new Random(2));
        assertEquals(stake.playerIds.size(), 1);
        assertFalse(stake.playerIds.contains(protectedCopy.id));
        var human = new RegisteredPlayer(player.getSelectedDeck()).setPlayer(new LobbyPlayerAi("human", null));
        var opponent = new RegisteredPlayer(enemy).setPlayer(new LobbyPlayerAi("enemy", null));
        AnteStake bound = AnteService.preparePhysicalAnte(player, human, opponent, stake, new Random(3));
        assertEquals(bound.playerIds, stake.playerIds, "binding cannot replace the previewed ID with an identical protected copy");
        assertFalse(bound.playerIds.contains(protectedCopy.id));
    }

    @Test
    public void multiCardRiskUsesEveryUnprotectedCopyBeforeFallingBack() {
        player.addCard(card("Lightning Bolt", "M10"), 3);
        player.getSelectedDeck().getMain().add(card("Lightning Bolt", "M10"), 3);
        state.progression().onFullClear("rath", player, CampaignProgressTest.rules());
        var bolts = state.availableCopies(player).stream().filter(c -> c.card.getName().equals("Lightning Bolt")).toList();
        state.progression().protect(bolts.get(0), state.availableCopies(player));
        state.progression().protect(bolts.get(1), state.availableCopies(player));
        var rules = new CampaignConfig.AnteRules(); rules.playerAnteCount = 3;
        var stake = AnteService.normalStake(player, deckOf(card("Muscle Sliver", "TMP"), 1), rules, new Random(1));
        assertEquals(stake.playerIds.size(), 3);
        assertEquals(stake.playerIds.stream().filter(state.progression()::isProtected).count(), 1L);
        assertEquals(new java.util.HashSet<>(stake.playerIds).size(), 3);
    }

    @Test
    public void protectionFallsBackWhenItIsTheOnlyEligibleCardAndOfficialLossFreesTheSlot() {
        OwnedCard copy = state.ownedCopies(player).stream().filter(c -> c.card.getName().equals("Lightning Bolt")).findFirst().orElseThrow();
        state.progression().protect(copy, state.availableCopies(player));
        assertEquals(AnteService.normalStake(player, deckOf(card("Muscle Sliver", "TMP"), 1), CampaignConfig.instance().ante, new Random(1)).playerIds, List.of(copy.id));
        var result = new GameOutcome.AnteResult();
        result.addLostPhysical(List.of(new GameOutcome.AnteCard(copy.id, copy.card)));
        AnteService.applyResult(player, result);
        assertFalse(state.progression().isProtected(copy.id));
        assertTrue(state.progression().protectedIds().isEmpty());
    }

    @Test
    public void protectionSurvivesSerializationAndRejectsUnavailableOrExcessCopies() throws Exception {
        var copies = state.availableCopies(player);
        state.progression().protect(copies.get(0), copies);
        assertThrows(IllegalArgumentException.class, () -> state.progression().protect(copies.get(1), copies));
        var loaded = new CampaignState(); loaded.load(PhysicalIdentityTest.roundTrip(state.save()));
        assertTrue(loaded.progression().isProtected(copies.get(0).id));
        state.progression().unprotect(copies.get(0).id);
        assertThrows(IllegalArgumentException.class, () -> state.progression().protect(OwnedCard.mint(card("Muscle Sliver", "TMP")), copies));
    }

    @Test
    public void voluntaryPrintingSalesUseUnprotectedCopiesAndDetectWhenConfirmationIsNeeded() {
        OwnedCard copy = state.ownedCopies(player).stream().filter(c -> c.card.getName().equals("Lightning Bolt")).findFirst().orElseThrow();
        state.progression().protect(copy, state.availableCopies(player));
        player.addCard(copy.card);
        assertFalse(state.saleTouchesProtection(player, copy.card, 1));
        assertTrue(state.saleTouchesProtection(player, copy.card, 2));
        assertEquals(player.sellCard(copy.card, 1), 1);
        assertTrue(state.ownedCopies(player).contains(copy));
        assertTrue(state.saleTouchesProtection(player, copy.card, 1));
    }
}
