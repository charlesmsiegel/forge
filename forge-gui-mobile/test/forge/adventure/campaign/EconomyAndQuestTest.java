package forge.adventure.campaign;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import forge.adventure.data.AdventureQuestData;
import forge.adventure.data.AdventureQuestStage;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.util.CardUtil;
import forge.adventure.util.Reward;
import forge.item.PaperCard;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.testng.Assert.*;

/**
 * Milestone 8: configurable economy multipliers and the card-request / delivery quest content.
 */
public class EconomyAndQuestTest extends AdventureTestBase {
    private CampaignConfig saved;

    @BeforeMethod
    public void keepConfig() {
        saved = CampaignConfig.instance();
        CampaignState.setInstance(new CampaignState());
    }

    @AfterMethod
    public void restoreConfig() {
        CampaignConfig.setInstance(saved);
        CampaignState.setInstance(new CampaignState());
    }

    private static CampaignConfig configWith(float sell, float shop, float booster) {
        CampaignConfig config = CampaignConfig.load(PLANE_DIR + "campaign.json");
        config.economy.cardSellMultiplier = sell;
        config.economy.shopPriceMultiplier = shop;
        config.economy.boosterPriceMultiplier = booster;
        return config;
    }

    @Test
    public void sellAndShopPricesFollowTheCampaignMultipliers() {
        PaperCard bolt = card("Lightning Bolt", "M10");
        int base = CardUtil.getCardPrice(bolt);
        assertTrue(base > 0);
        AdventurePlayer player = newPlayer(deckOf(bolt, 3, card("Mountain", "M10"), 37));

        CampaignConfig.setInstance(configWith(0.5f, 1.0f, 1.0f));
        assertEquals(player.cardSellPrice(bolt), (int) (base * 0.5f));
        assertEquals(CardUtil.getRewardPrice(new Reward(bolt)), base);

        CampaignConfig.setInstance(configWith(0.25f, 2.0f, 1.0f));
        assertEquals(player.cardSellPrice(bolt), (int) (base * 0.25f));
        assertEquals(CardUtil.getRewardPrice(new Reward(bolt)), base * 2);
    }

    @Test
    public void sellingCardsGivesGoldAndRemovesExactlyTheSoldCopies() {
        PaperCard bolt = card("Lightning Bolt", "M10");
        AdventurePlayer player = newPlayer(deckOf(bolt, 3, card("Mountain", "M10"), 37));
        player.addCard(bolt, 2); // 5 owned, 3 in deck -> 2 sellable
        int gold = player.getGold();
        assertEquals(player.getSellableCards().count(bolt), 2);

        int sold = player.sellCard(bolt, 2);

        assertEquals(sold, 2);
        assertEquals(player.getCards().count(bolt), 3);
        assertEquals(player.getGold(), gold + 2 * player.cardSellPrice(bolt));
    }

    @Test
    public void cardRequestDialogHelpersTakeExactlyOneUsableCopy() {
        PaperCard giantM10 = card("Hill Giant", "M10");
        AdventurePlayer player = newPlayer(deckOf(card("Lightning Bolt", "M10"), 3, card("Mountain", "M10"), 37));
        assertFalse(CampaignDialogs.hasCard(player, "Hill Giant"));
        player.addCard(giantM10, 2);
        assertTrue(CampaignDialogs.hasCard(player, "hill giant"), "name match is case-insensitive");

        PaperCard taken = CampaignDialogs.removeCardByName(player, "Hill Giant");

        assertEquals(taken, giantM10);
        assertEquals(player.getCards().count(giantM10), 1);
        assertNotNull(CampaignDialogs.removeCardByName(player, "Hill Giant"));
        assertNull(CampaignDialogs.removeCardByName(player, "Hill Giant"), "nothing left to give");
    }

    @Test
    public void cardsAtHomeCannotBeHandedOverDuringAnExpedition() {
        PaperCard giant = card("Hill Giant", "M10");
        AdventurePlayer player = newPlayer(deckOf(card("Lightning Bolt", "M10"), 3, card("Mountain", "M10"), 37));
        player.addCard(giant, 1);
        CampaignState.instance().enterExpedition("stronghold", player);
        assertFalse(CampaignDialogs.hasCard(player, "Hill Giant"), "the copy stayed at home");
        CampaignState.instance().leaveExpedition("test");
        assertTrue(CampaignDialogs.hasCard(player, "Hill Giant"));
    }

    @Test
    public void planeQuestContentIsWellFormed() throws Exception {
        FileHandle handle = new FileHandle(PLANE_DIR + "world/quests.json");
        Array<AdventureQuestData> quests = new Json().fromJson(Array.class, AdventureQuestData.class, handle);
        assertTrue(quests.size >= 1, "delivery quest template present");
        AdventureQuestData delivery = quests.get(0);
        assertTrue(delivery.isTemplate);
        assertEquals(delivery.reward.type, "gold");
        assertTrue(delivery.stages.length >= 2);
        for (AdventureQuestStage stage : delivery.stages)
            assertNotNull(stage.objective, "every stage names a valid objective");

        String town = Files.readString(Paths.get(PLANE_DIR + "maps/map/sr_town.tmx"));
        assertTrue(town.contains("hasCard"), "the town has a card-request NPC dialog");
        assertTrue(town.contains("removeCard"));
        assertTrue(town.contains("questtype"), "the job board still offers side quests");
    }
}
