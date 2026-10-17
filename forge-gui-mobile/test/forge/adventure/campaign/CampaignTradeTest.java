package forge.adventure.campaign;

import forge.adventure.player.AdventurePlayer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.testng.Assert.*;

public class CampaignTradeTest extends AdventureTestBase {
    private CampaignState old, state;
    private AdventurePlayer player;
    private CampaignConfig.TradeRecipe recipe;

    @BeforeMethod
    public void setup() {
        old = CampaignState.instance(); state = new CampaignState(); CampaignState.setInstance(state);
        player = newPlayer(deckOf(card("Lightning Bolt", "M10"), 3, card("Mountain", "M10"), 37));
        player.addCard(card("Muscle Sliver", "TMP"), 20);
        recipe = new CampaignConfig.TradeRecipe();
        recipe.id = "tempest-common-rare"; recipe.name = "Tempest Collector";
        recipe.inputEditions = new String[]{"TMP"}; recipe.outputEditions = new String[]{"TMP"};
        recipe.requiredCount = 20; recipe.inputRarity = "Common"; recipe.outputRarity = "Rare";
    }

    @AfterMethod
    public void cleanup() { CampaignState.setInstance(old); }

    @Test
    public void exactCopiesAreExchangedForOneRandomSameSetRareAfterConfirmationOnly() {
        int before = player.getCards().countAll();
        List<OwnedCard> preview = CampaignTrades.preview(recipe, player);
        assertEquals(preview.size(), 20);
        assertEquals(player.getCards().countAll(), before, "preview consumes nothing");
        var reward = CampaignTrades.execute(recipe, preview, player, false, new Random(1));
        assertEquals(reward.getEdition(), "TMP");
        assertEquals(reward.getRarity(), forge.card.CardRarity.Rare);
        assertEquals(player.getCards().countAll(), before - 19);
        assertTrue(state.ownedCopies(player).stream().noneMatch(c -> preview.contains(c)));
        assertEquals(state.ownedCopies(player).stream().filter(c -> c.card.equals(reward)).count(), 1L);
    }

    @Test
    public void duplicatesStaleCopiesAndMissingOutputDoNotMutateOwnership() {
        List<OwnedCard> preview = CampaignTrades.preview(recipe, player);
        int count = player.getCards().countAll();
        List<OwnedCard> duplicates = new ArrayList<>(preview); duplicates.set(1, duplicates.get(0));
        assertThrows(IllegalArgumentException.class, () -> CampaignTrades.execute(recipe, duplicates, player, false, new Random(1)));
        assertEquals(player.getCards().countAll(), count);
        recipe.outputEditions = new String[]{"NOT-A-SET"};
        assertThrows(IllegalArgumentException.class, () -> CampaignTrades.execute(recipe, preview, player, false, new Random(1)));
        assertEquals(player.getCards().countAll(), count);
        recipe.outputEditions = new String[]{"TMP"};
        CardOwnership.removeCopy(player, preview.get(0));
        assertThrows(IllegalArgumentException.class, () -> CampaignTrades.execute(recipe, preview, player, false, new Random(1)));
        assertEquals(player.getCards().countAll(), count - 1);
    }

    @Test
    public void expeditionCannotTradeTheHomeReserveAndVirtualBasicsAreNotCurrency() {
        List<OwnedCard> home = CampaignTrades.preview(recipe, player);
        state.enterExpedition("stronghold", player);
        assertThrows(IllegalArgumentException.class, () -> CampaignTrades.preview(recipe, player));
        assertThrows(IllegalArgumentException.class, () -> CampaignTrades.execute(recipe, home, player, false, new Random(1)));
        state.leaveExpedition("exit");
        recipe.inputEditions = new String[]{"M10"}; recipe.inputRarity = "Basic Land";
        assertThrows(IllegalArgumentException.class, () -> CampaignTrades.preview(recipe, player));
    }

    @Test
    public void protectedTradeRequiresAuthorizationAndFreesTheExactSlot() {
        state.progression().onFullClear("rath", player, CampaignProgressTest.rules());
        List<OwnedCard> inputs = CampaignTrades.preview(recipe, player);
        state.progression().protect(inputs.get(0), state.availableCopies(player));
        assertThrows(IllegalArgumentException.class, () -> CampaignTrades.execute(recipe, inputs, player, false, new Random(1)));
        assertEquals(player.getCards().countAll(), 60);
        CampaignTrades.execute(recipe, inputs, player, true, new Random(1));
        assertTrue(state.progression().protectedIds().isEmpty());
    }

    @Test
    public void colorFilterCannotConsumeUnrelatedCards() {
        recipe.inputColor = "B";
        assertThrows(IllegalArgumentException.class, () -> CampaignTrades.preview(recipe, player));
        assertEquals(player.getCards().countAll(), 60);
    }
    @Test
    public void collectorAndDefaultMilestonesArePresentInTownContent() throws Exception {
        CampaignConfig config = CampaignConfig.load(PLANE_DIR + "campaign.json");
        assertTrue(java.util.Arrays.stream(config.trades).map(recipe -> recipe.id).collect(java.util.stream.Collectors.toSet())
                .containsAll(java.util.Set.of("tempest-common-rare", "stronghold-common-rare", "exodus-common-rare")));
        for (var recipe : config.trades) CampaignTrades.validate(recipe);
        CampaignProgress.validateRules(config.progression);
        assertTrue(java.util.Arrays.stream(config.progression.milestones).map(milestone -> milestone.id).collect(java.util.stream.Collectors.toSet())
                .containsAll(java.util.Set.of("rath-first-clear", "rath-second-clear", "rath-master-collector")));
        String town = java.nio.file.Files.readString(java.nio.file.Path.of(PLANE_DIR + "maps/map/sr_town.tmx"));
        assertTrue(town.contains("campaignServices"), "existing towns need a reachable collector");
    }

}
