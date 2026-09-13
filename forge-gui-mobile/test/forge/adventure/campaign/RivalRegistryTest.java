package forge.adventure.campaign;

import forge.adventure.data.EnemyData;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.util.SaveFileData;
import forge.item.PaperCard;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Random;

import static org.testng.Assert.*;

/**
 * Milestone 3: generic opponents become persistent rivals only by taking a card; the exact
 * printing they took persists and is the only thing that can be reclaimed from them.
 */
public class RivalRegistryTest extends AdventureTestBase {

    private static EnemyData goblin() {
        EnemyData data = new EnemyData();
        data.name = "Goblin";
        data.sprite = "sprites/enemy/humanoid/goblin/goblin.atlas";
        data.deck = new String[]{"decks/standard/goblin.dck"};
        data.life = 10;
        return data;
    }

    private static CampaignState freshState() {
        CampaignState state = new CampaignState();
        state.setRandom(new Random(11));
        return state;
    }

    @Test
    public void aGenericEnemyThatLosesOrTakesNothingStaysGeneric() {
        CampaignState state = freshState();
        assertNull(state.onMatchEnd(null, goblin(), true, List.of(card("Lightning Bolt", "M10")), List.of(), null));
        assertNull(state.onMatchEnd(null, goblin(), false, List.of(), List.of(), null), "lost but no card taken");
        assertEquals(state.rivals().size(), 0);
    }

    @Test
    public void aGenericEnemyThatTakesACardBecomesAPersistentRival() {
        CampaignState state = freshState();
        PaperCard foilAngel = card("Serra Angel", "7ED").getFoiled();

        String id = state.onMatchEnd(null, goblin(), false, List.of(), List.of(foilAngel), null);

        assertNotNull(id);
        Rival rival = state.rivals().byId(id);
        assertNotNull(rival);
        assertFalse(rival.name.isEmpty(), "rival got a generated name");
        assertNotEquals(rival.name, "Goblin");
        assertEquals(rival.baseEnemyName, "Goblin");
        assertEquals(rival.record(), "0-1");
        assertEquals(rival.getCardsTakenFromPlayer(), List.of(foilAngel));
        assertTrue(rival.getCardsTakenFromPlayer().get(0).isFoil(), "exact printing retained");
        assertEquals(state.rivals().atSlot(0), rival);
    }

    @Test
    public void campPagesReachEveryRivalAfterSaveLoad() {
        CampaignState state = freshState();
        PaperCard bolt = card("Lightning Bolt", "M10");
        for (int i = 0; i < 9; i++)
            state.onMatchEnd(null, goblin(), false, List.of(), List.of(bolt), null);
        CampaignState loaded = freshState();
        loaded.load(state.save());
        assertEquals(loaded.rivals().page(0, 4), loaded.rivals().all().subList(0, 4));
        assertEquals(loaded.rivals().page(1, 4), loaded.rivals().all().subList(4, 8));
        assertEquals(loaded.rivals().page(2, 4), loaded.rivals().all().subList(8, 9));
        assertTrue(loaded.rivals().page(3, 4).isEmpty());
    }

    @Test
    public void bossRivalsBecomeRepeatableCampOpponentsWithoutChangingTheirTemplate() {
        EnemyData boss = goblin();
        boss.boss = true;
        boss.nextEnemy = goblin();
        Rival rival = new Rival("boss-rival", "Krag", boss.name);
        EnemyData encounter = rival.encounterData(boss);
        assertFalse(encounter.boss, "camp wins must not permanently delete a boss rival");
        assertNull(encounter.nextEnemy, "a rival must not turn into its boss's next phase");
        assertEquals(encounter.nameOverride, "Krag");
        assertEquals(encounter.deck, boss.deck);
        assertTrue(boss.boss);
        assertNotNull(boss.nextEnemy);
    }

    @Test
    public void rivalNamesAreUnique() {
        CampaignState state = freshState();
        PaperCard bolt = card("Lightning Bolt", "M10");
        for (int i = 0; i < 6; i++)
            state.onMatchEnd(null, goblin(), false, List.of(), List.of(bolt), null);
        long distinct = state.rivals().all().stream().map(r -> r.name).distinct().count();
        assertEquals(distinct, 6);
    }

    @Test
    public void stolenCardsSurviveSaveAndLoad() {
        CampaignState state = freshState();
        PaperCard foilAngel = card("Serra Angel", "7ED").getFoiled();
        PaperCard bolt = card("Lightning Bolt", "3ED");
        String id = state.onMatchEnd(null, goblin(), false, List.of(), List.of(foilAngel, bolt), null);
        state.onMatchEnd(id, goblin(), true, List.of(card("Grizzly Bears", "M10")), List.of(), null);

        SaveFileData data = state.save();
        CampaignState loaded = new CampaignState();
        loaded.load(data);

        Rival rival = loaded.rivals().byId(id);
        assertNotNull(rival);
        assertEquals(rival.name, state.rivals().byId(id).name);
        assertEquals(rival.record(), "1-1");
        assertEquals(rival.getTakenPool().count(foilAngel), 1);
        assertEquals(rival.getTakenPool().count(bolt), 1);
        assertEquals(rival.getTakenPool().count(card("Lightning Bolt", "M10")), 0, "other printing not confused");
    }

    @Test
    public void onlyCardsTakenByThatRivalAreReclaimable() {
        CampaignState state = freshState();
        PaperCard angel = card("Serra Angel", "7ED");
        PaperCard bolt = card("Lightning Bolt", "M10");
        String first = state.onMatchEnd(null, goblin(), false, List.of(), List.of(angel), null);
        String second = state.onMatchEnd(null, goblin(), false, List.of(), List.of(bolt), null);

        assertEquals(state.rivals().reclaimable(first), List.of(angel));
        assertEquals(state.rivals().reclaimable(second), List.of(bolt));
        assertEquals(state.rivals().reclaimable("no-such-rival"), List.of());
    }

    @Test
    public void winningATemplateCopyDoesNotReclaimAnIdenticalStolenCard() {
        CampaignState state = freshState();
        PaperCard bolt = card("Lightning Bolt", "M10");
        String id = state.onMatchEnd(null, goblin(), false, List.of(), List.of(bolt), null);
        AnteStake normal = new AnteStake(AnteStake.Kind.NORMAL, List.of(), List.of(bolt));
        state.onMatchEnd(id, goblin(), true, List.of(bolt), List.of(), normal);
        assertEquals(state.rivals().reclaimable(id), List.of(bolt));
    }

    @Test
    public void reclamationRemovesOnlyThePledgedStolenCopy() {
        CampaignState state = freshState();
        PaperCard bolt = card("Lightning Bolt", "M10");
        String id = state.onMatchEnd(null, goblin(), false, List.of(), List.of(bolt, bolt), null);
        AnteStake reclamation = new AnteStake(AnteStake.Kind.RECLAMATION, List.of(), List.of(bolt));
        // An ante card added another, identical template copy to the opponent's ante.
        state.onMatchEnd(id, goblin(), true, List.of(bolt, bolt), List.of(), reclamation);
        assertEquals(state.rivals().reclaimable(id), List.of(bolt));
    }

    @Test
    public void winningTheReclamationDuelReturnsTheCardAndRemovesItFromTheRival() {
        CampaignState state = freshState();
        PaperCard angel = card("Serra Angel", "7ED");
        AdventurePlayer player = newPlayer(deckOf(card("Lightning Bolt", "M10"), 4, card("Mountain", "M10"), 20));
        String id = state.onMatchEnd(null, goblin(), false, List.of(), List.of(angel), null);
        AnteStake stake = AnteService.reclamationStake(player.getSelectedDeck(), angel, CampaignConfig.instance().ante, new Random(5));
        assertEquals(stake.playerCards.size(), 3, "exactly three cards risked");

        // The engine's result for a won reclamation: the target is won, nothing lost.
        AnteService.applyResult(player, stake.opponentCards, List.of());
        state.onMatchEnd(id, goblin(), true, stake.opponentCards, List.of(), stake);

        assertEquals(player.getCards().count(angel), 1, "player owns the card again");
        assertEquals(state.rivals().reclaimable(id), List.of(), "rival no longer holds it");
        assertEquals(state.rivals().byId(id).record(), "1-1");
    }

    @Test
    public void losingTheReclamationDuelHandsTheRiskedCardsToTheRival() {
        CampaignState state = freshState();
        PaperCard angel = card("Serra Angel", "7ED");
        PaperCard bolt = card("Lightning Bolt", "M10");
        AdventurePlayer player = newPlayer(deckOf(bolt, 4, card("Mountain", "M10"), 20));
        String id = state.onMatchEnd(null, goblin(), false, List.of(), List.of(angel), null);
        AnteStake stake = AnteService.reclamationStake(player.getSelectedDeck(), angel, CampaignConfig.instance().ante, new Random(5));

        List<PaperCard> removed = AnteService.applyResult(player, List.of(), stake.playerCards);
        state.onMatchEnd(id, goblin(), false, List.of(), removed, stake);

        assertEquals(removed.size(), 3);
        assertEquals(player.getCards().count(bolt), 1);
        Rival rival = state.rivals().byId(id);
        assertEquals(rival.getTakenPool().count(bolt), 3, "the three risked copies are now reclaimable too");
        assertEquals(rival.getTakenPool().count(angel), 1);
        assertEquals(rival.record(), "0-2");
        assertEquals(state.rivals().size(), 1, "no second rival was created");
    }
}
