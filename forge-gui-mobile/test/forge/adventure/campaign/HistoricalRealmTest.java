package forge.adventure.campaign;

import forge.adventure.player.AdventurePlayer;
import forge.adventure.util.SaveFileData;
import forge.deck.Deck;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.testng.Assert.*;

public class HistoricalRealmTest extends AdventureTestBase {
    private CampaignConfig original;
    private CampaignConfig config;
    private CampaignState state;
    private AdventurePlayer player;
    private HistoricalRealm realm;

    @BeforeMethod
    public void setupRealm() {
        original = CampaignConfig.instance();
        config = CampaignConfig.load(PLANE_DIR + "campaign.json");
        realm = RealmProgressTest.realm();
        realm.chapters[1].legacyRegion = "stronghold";
        realm.chapters[1].entryMap = "maps/map/stronghold_1.tmx";
        realm.chapters[1].maps = new String[]{realm.chapters[1].entryMap};
        for (HistoricalRealm.Chapter chapter : realm.chapters) chapter.entryObject = 38;
        config.realms = new HistoricalRealm[]{realm};
        CampaignConfig.setInstance(config);
        state = new CampaignState();
        CampaignState.setInstance(state);
        player = newPlayer(deckOf(card("Lightning Bolt", "M10"), 3, card("Mountain", "M10"), 37));
    }

    @AfterMethod
    public void cleanupRealm() {
        CampaignConfig.setInstance(original);
        CampaignState.setInstance(new CampaignState());
    }

    @Test
    public void chapterProductsAreGuardedAndAdvancePreservesPhysicalInventoryAndLife() {
        state.enterRealm("rath", null, player, "");
        List<String> ids = state.availableCopies(player).stream().map(c -> c.id).collect(Collectors.toList());
        player.setLife(5);
        List<Deck> products = state.onChapterCompleted("rath", "tempest", player);
        assertEquals(products.size(), 36);
        assertTrue(products.stream().allMatch(pack -> pack.getComment().equals("TMP")));
        assertTrue(state.onChapterCompleted("rath", "tempest", player).isEmpty());
        assertEquals(player.getBoostersOwned().size, 36);
        state.advanceRealm("rath");
        assertEquals(state.activeRealmChapter().id, "stronghold");
        assertEquals(player.getLife(), 5);
        assertEquals(state.availableCopies(player).stream().map(c -> c.id).collect(Collectors.toList()), ids);
        state.leaveExpedition("retreat");
        state.enterRealm("rath", null, player, "");
        assertEquals(state.activeRealmChapter().id, "stronghold");
    }

    @Test
    public void standaloneStrongholdAndBlockShareLifetimeRewardsBothDirections() {
        assertEquals(state.onRegionCompleted("stronghold", player).size(), 36);
        state.enterRealm("rath", null, player, "");
        state.onChapterCompleted("rath", "tempest", player);
        state.advanceRealm("rath");
        assertEquals(state.onChapterCompleted("rath", "stronghold", player).size(), 6);
        assertEquals(state.completions("stronghold"), 2);
        state.leaveExpedition("retreat");
        assertEquals(state.onRegionCompleted("stronghold", player).size(), 6);
    }

    @Test
    public void failedProductGenerationLeavesCheckpointAndRewardCountUntouched() {
        state.enterRealm("rath", null, player, "");
        realm.chapters[0].editions = new String[]{"TMP", "NOT-A-SET"};
        realm.chapters[0].boosterEdition = "NOT-A-SET";
        assertThrows(IllegalArgumentException.class, () -> state.onChapterCompleted("rath", "tempest", player));
        assertEquals(state.completions("rath:tempest"), 0);
        assertTrue(state.realmProgress("rath").canComplete(realm, "tempest"));
        assertEquals(player.getBoostersOwned().size, 0);
        realm.chapters[0].boosterEdition = "TMP";
        assertEquals(state.onChapterCompleted("rath", "tempest", player).size(), 36);
    }

    @Test
    public void realmSaveRoundTripAfterVictoryDoesNotRewardAgain() {
        state.enterRealm("rath", null, player, "");
        state.onChapterCompleted("rath", "tempest", player);
        CampaignState loaded = new CampaignState();
        loaded.load(state.save());
        CampaignState.setInstance(loaded);
        assertEquals(loaded.activeRealmChapter().id, "tempest");
        assertTrue(loaded.onChapterCompleted("rath", "tempest", player).isEmpty());
        loaded.advanceRealm("rath");
        assertEquals(loaded.activeRealmChapter().id, "stronghold");
    }

    @Test
    public void unknownAndForeignTransitionsCannotMutateExpedition() {
        assertThrows(IllegalArgumentException.class, () -> state.enterRealm("missing", null, player, ""));
        assertFalse(state.expedition().isActive());
        state.enterRealm("rath", null, player, "");
        assertTrue(state.onChapterCompleted("missing", "tempest", player).isEmpty());
        assertTrue(state.onChapterCompleted("rath", "exodus", player).isEmpty());
        assertThrows(IllegalStateException.class, () -> state.enterRealm("rath", "stronghold", player, ""));
        assertEquals(state.activeRealmChapter().id, "tempest");
        assertEquals(state.completions("rath:exodus"), 0);
    }

    @Test
    public void legacyActiveStrongholdAndCountsStillLoadWithoutRealmData() {
        state.enterExpedition("stronghold", player);
        state.onRegionCompleted("stronghold", player);
        SaveFileData legacy = state.save();
        legacy.remove("realms");
        legacy.store("version", 2);
        CampaignState loaded = new CampaignState();
        loaded.load(legacy);
        assertTrue(loaded.expedition().isActive());
        assertNull(loaded.activeRealmChapter());
        assertEquals(loaded.completions("stronghold"), 1);
        loaded.leaveExpedition("retreat");
        loaded.enterRealm("rath", null, player, "");
        assertEquals(loaded.activeRealmChapter().id, "tempest", "legacy Stronghold does not unlock other chapters");
    }

    @Test
    public void fullBlockAndSingleReplayUseRepeatRewardsAfterSerializedCompletion() {
        state.enterRealm("rath", null, player, "");
        for (String id : new String[]{"tempest", "stronghold", "exodus"}) {
            assertEquals(state.onChapterCompleted("rath", id, player).size(), 36);
            if (!id.equals("exodus")) state.advanceRealm("rath");
        }
        assertTrue(state.deeds().has("realm.rath.completed"));
        state.leaveExpedition("exit");
        CampaignState loaded = new CampaignState();
        var legacy = state.save(); legacy.remove("deeds");
        loaded.load(legacy);
        assertTrue(loaded.deeds().has("realm.rath.completed"), "Migrate known prior block accomplishments without another clear");
        CampaignState.setInstance(loaded);
        loaded.enterRealm("rath", "stronghold", player, "");
        assertEquals(loaded.onChapterCompleted("rath", "stronghold", player).size(), 6);
        assertTrue(loaded.realmProgress("rath").isRunComplete());
        assertEquals(loaded.progression().fullClears("rath"), 1, "single chapter replay is not another full block clear");
        assertThrows(IllegalStateException.class, () -> loaded.advanceRealm("rath"));
        loaded.leaveExpedition("exit");
        loaded.enterRealm("rath", "*", player, "");
        assertEquals(loaded.activeRealmChapter().id, "tempest");
        assertEquals(loaded.onChapterCompleted("rath", "tempest", player).size(), 6);
    }

    @Test
    public void retreatResetsTransientEncountersAndFlagsButKeepsCheckpointAndHoldings() {
        String root = "realm-regression:";
        var changes = forge.adventure.world.WorldSave.getCurrentSave().getPointOfInterestChanges(root + realm.chapters[0].entryMap);
        state.enterRealm("rath", null, player, root);
        changes.deleteObject(58);
        changes.getMapFlags().put("door", (byte) 1);
        player.addCard(card("Muscle Sliver", "TMP"));
        int owned = player.getCards().countAll();
        state.onChapterCompleted("rath", "tempest", player);
        state.leaveExpedition("defeated");
        assertFalse(changes.isObjectDeleted(58));
        assertTrue(changes.getMapFlags().isEmpty());
        assertEquals(player.getCards().countAll(), owned);
        state.enterRealm("rath", null, player, root);
        assertEquals(state.activeRealmChapter().id, "stronghold");
    }

    @Test
    public void missingMapAndEntryObjectRejectBeforeActivatingAvailability() {
        realm.chapters[0].entryMap = "maps/map/missing-realm-map.tmx";
        realm.chapters[0].maps = new String[]{realm.chapters[0].entryMap};
        assertThrows(IllegalArgumentException.class, () -> state.enterRealm("rath", null, player, ""));
        assertFalse(state.expedition().isActive());
        assertFalse(state.realmProgress("rath").isActive());
        realm.chapters[0].entryMap = "maps/map/tempest.tmx";
        realm.chapters[0].maps = new String[]{realm.chapters[0].entryMap};
        realm.chapters[0].entryObject = 123456;
        assertThrows(IllegalArgumentException.class, () -> state.enterRealm("rath", null, player, ""));
        assertFalse(state.expedition().isActive());
    }

    @Test
    public void invalidMilestoneCannotConsumeFinalChapterVictory() {
        state.enterRealm("rath", null, player, "");
        state.onChapterCompleted("rath", "tempest", player); state.advanceRealm("rath");
        state.onChapterCompleted("rath", "stronghold", player); state.advanceRealm("rath");
        var broken = new CampaignConfig.Milestone();
        config.progression.milestones = new CampaignConfig.Milestone[]{broken};
        int packs = player.getBoostersOwned().size;
        assertThrows(IllegalArgumentException.class, () -> state.onChapterCompleted("rath", "exodus", player));
        assertTrue(state.realmProgress("rath").canComplete(realm, "exodus"));
        assertEquals(player.getBoostersOwned().size, packs);
        assertEquals(state.completions("rath:exodus"), 0);
    }

    @Test
    public void malformedRealmCannotStartOrReplaceCarriedCards() {
        realm.chapters[0].editions = new String[]{"NOT-A-SET"};
        assertThrows(IllegalArgumentException.class, () -> state.enterRealm("rath", null, player, ""));
        assertFalse(state.expedition().isActive());
    }
}
