package forge.adventure.campaign;

import forge.adventure.player.AdventurePlayer;
import forge.adventure.util.SaveFileData;
import forge.deck.Deck;
import forge.item.PaperCard;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

/**
 * Milestone 6: first Stronghold clear grants a booster box, later clears six boosters; packs are
 * real unopened boosters that enter the collection only when opened, one at a time.
 */
public class RegionCompletionTest extends AdventureTestBase {
    private CampaignState state;

    @BeforeMethod
    public void freshState() {
        state = new CampaignState();
        CampaignState.setInstance(state);
    }

    @AfterMethod
    public void resetState() {
        CampaignState.setInstance(new CampaignState());
    }

    private static AdventurePlayer player() {
        return newPlayer(deckOf(card("Lightning Bolt", "M10"), 3, card("Mountain", "M10"), 37));
    }

    @Test
    public void firstCompletionGrantsABoosterBoxAndLaterOnesSixBoosters() {
        AdventurePlayer player = player();
        CampaignConfig config = CampaignConfig.instance();

        List<Deck> box = state.onRegionCompleted("stronghold", player);
        assertEquals(box.size(), config.stronghold.firstClearBoxBoosterCount);
        assertEquals(player.getBoostersOwned().size, config.stronghold.firstClearBoxBoosterCount);
        assertEquals(state.completions("stronghold"), 1);
        String boxTag = box.get(0).getTags().stream().filter(t -> t.startsWith("box:")).findFirst().orElseThrow();
        assertTrue(box.stream().allMatch(p -> p.getTags().contains(boxTag)), "all packs belong to the same box");

        List<Deck> repeat = state.onRegionCompleted("stronghold", player);
        assertEquals(repeat.size(), config.stronghold.repeatClearBoosterCount);
        assertEquals(player.getBoostersOwned().size, config.stronghold.firstClearBoxBoosterCount + config.stronghold.repeatClearBoosterCount);
        assertEquals(state.completions("stronghold"), 2);
        assertTrue(repeat.stream().noneMatch(p -> p.getTags().contains(boxTag)), "loose boosters are not part of the box");
    }

    @Test
    public void packsAreStrongholdBoostersThatOnlyEnterTheCollectionWhenOpened() {
        AdventurePlayer player = player();
        int before = player.getCards().countAll();

        List<Deck> packs = state.onRegionCompleted("stronghold", player);

        assertEquals(player.getCards().countAll(), before, "unopened packs add nothing to the collection");
        for (Deck pack : packs) {
            assertEquals(pack.getComment(), "STH");
            assertEquals(pack.getMain().countAll(), 15, "Stronghold booster: 11 commons, 3 uncommons, 1 rare");
            for (Map.Entry<PaperCard, Integer> e : pack.getMain())
                assertEquals(e.getKey().getEdition(), "STH");
        }
        // Opening one pack (what InventoryScene -> RewardScene does): its cards become physical copies.
        Deck first = packs.get(0);
        player.addCards(first.getMain());
        player.removeBooster(first);
        assertEquals(player.getCards().countAll(), before + 15);
        assertEquals(player.getBoostersOwned().size, packs.size() - 1, "the rest of the box stays unopened");
    }

    @Test
    public void completionCountSurvivesSaveAndLoad() {
        AdventurePlayer player = player();
        state.onRegionCompleted("stronghold", player);
        state.onRegionCompleted("stronghold", player);

        SaveFileData data = state.save();
        CampaignState loaded = new CampaignState();
        loaded.load(data);

        assertEquals(loaded.completions("stronghold"), 2);
        assertEquals(loaded.completions("elsewhere"), 0);
    }
}
