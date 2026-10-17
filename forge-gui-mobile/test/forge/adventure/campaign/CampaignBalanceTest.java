package forge.adventure.campaign;

import forge.adventure.util.CardUtil;
import forge.adventure.util.Reward;
import forge.deck.Deck;
import org.testng.annotations.*;
import java.util.*;
import static org.testng.Assert.*;

public class CampaignBalanceTest extends AdventureTestBase {
    private CampaignConfig previous;
    @BeforeMethod public void reset(){previous=CampaignConfig.instance();CampaignConfig.setInstance(CampaignConfig.load(PLANE_DIR+"campaign.json"));CampaignState.setInstance(new CampaignState());}
    @AfterMethod public void restore(){CampaignConfig.setInstance(previous);CampaignState.setInstance(new CampaignState());}
    @Test public void discountedCardAndPackPurchaseCannotProduceImmediateResaleProfit() {
        var rules=CampaignConfig.instance().economy;var cards=List.of(card("Lightning Bolt","M10"),card("Black Lotus","LEA"),card("Island","M10").getFoiled());
        int samples=0;
        for(float sell:new float[]{0,0.25f,0.5f,2})for(float shop:new float[]{0.1f,1,3})for(float discount:new float[]{0.01f,0.5f,1,2}) {
            rules.cardSellMultiplier=sell;rules.shopPriceMultiplier=shop;rules.boosterPriceMultiplier=shop;
            Deck pack=new Deck("Price sweep");
            long resale=0;
            for(var card:cards){pack.getMain().add(card);int base=CardUtil.getCardPrice(card);
                int ceiling=CampaignPrices.saleCeiling(base,card.isFoil(),rules);resale+=ceiling;
                assertTrue(CampaignPrices.purchase(new Reward(card),discount,rules)>ceiling);
                for(float town:new float[]{0,0.5f,1,2,3})assertTrue(CampaignPrices.sale(base,card.isFoil(),town,rules)<=ceiling);
                samples++;
            }
            assertTrue(CampaignPrices.purchase(new Reward(pack),discount,rules)>resale);
        }
        assertEquals(samples,144);
        System.out.println("BALANCE_SWEEP cards=144 packs=48 town_sales=720 immediate_profit=0");
    }
    @Test public void invalidDifficultyAndEconomyFailBeforeChangingState() {
        var config=CampaignConfig.instance();config.difficulty.enemyLifeMultiplier=Float.NaN;
        assertThrows(IllegalArgumentException.class,config::validateBalance);
        config.difficulty.enemyLifeMultiplier=1;config.rivals.winsPerTier=0;
        assertThrows(IllegalArgumentException.class,config::validateBalance);
        config.rivals.winsPerTier=3;config.economy.cardSellMultiplier=-1;
        assertThrows(IllegalArgumentException.class,config::validateBalance);
    }
    @Test public void initialLifeAndOpponentAxesRemainIndependentAndEarnedLifeSurvivesDifficultyChange() {
        var config=CampaignConfig.instance();config.player.startingLife=17;config.difficulty.enemyLifeMultiplier=1.5f;
        config.difficulty.aiProfile="Cautious";config.validateBalance();
        var player=newPlayer(deckOf(card("Island","M10"),40));assertEquals(player.getMaxLife(),17);
        player.addMaxLife(4);player.updateDifficulty(player.getDifficulty());assertEquals(player.getMaxLife(),21);
        assertEquals(config.economy.cardSellMultiplier,0.5f);assertEquals(config.ante.playerAnteCount,1);
        assertEquals(CampaignBalance.enemyLife(20,2,config),30,"Campaign life axis does not inherit a bundled Adventure difficulty multiplier");
    }
    @Test public void rivalThresholdChangesNeverDowngradeEarnedTierOrRerollSavedRecipe() {
        var config=CampaignConfig.instance();config.rivals.winsPerTier=2;
        var universe=CardUniverse.capture(config,new forge.adventure.data.ConfigData());
        var rival=new Rival("balance-rival","Saved","Adept Blue");rival.playerWins=2;
        assertEquals(rival.upgradeTier(),1);
        var template=deckOf(card("Island","M10"),24,card("Air Elemental","M10"),4,card("Cancel","M10"),4,card("Divination","M10"),4,card("Unsummon","M10"),4);
        var recipe=rival.opponentDeck(template,universe,template.getMain().toFlatList());
        var saved=rival.save();config.rivals.winsPerTier=20;config.rivals.rarityUpgradeSlotsPerTier=0;
        var loaded=new Rival();loaded.load(saved);assertEquals(loaded.upgradeTier(),1);
        assertEquals(loaded.opponentDeck(template,universe,template.getMain().toFlatList()).getMain(),recipe.getMain());
        assertEquals(loaded.deckSeed(),rival.deckSeed());
    }
    @Test public void singleInputConversionsAreRejectedBeforePhysicalRemoval() {
        var recipe=CampaignConfig.instance().trades[0];recipe.requiredCount=1;
        assertThrows(IllegalArgumentException.class,()->CampaignTrades.validate(recipe));
    }
    @Test public void exchangedPhysicalRewardsStayNonSaleableAcrossSaveAndCannotFeedAnotherConversion() {
        var player=newPlayer(deckOf(card("Muscle Sliver","TMP"),40));
        var recipe=new CampaignConfig.TradeRecipe();recipe.id="finite";recipe.requiredCount=20;
        recipe.inputEditions=new String[]{"TMP"};recipe.outputEditions=new String[]{"TMP"};
        var state=CampaignState.instance();
        var first=CampaignTrades.execute(recipe,CampaignTrades.preview(recipe,player),player,false,new Random(701));
        var second=CampaignTrades.execute(recipe,CampaignTrades.preview(recipe,player),player,false,new Random(702));
        assertTrue(first.hasNoSellValue());assertTrue(second.hasNoSellValue());assertEquals(player.cardSellPrice(first),0);
        assertEquals(state.ownedCopies(player).size(),2);
        var ids=state.ownedCopies(player);var loaded=new CampaignState();loaded.load(state.save());CampaignState.setInstance(loaded);
        assertEquals(loaded.ownedCopies(player),ids);assertTrue(loaded.ownedCopies(player).stream().allMatch(c->c.card.hasNoSellValue()));
        recipe.requiredCount=2;recipe.inputRarity="Rare";
        assertThrows(IllegalArgumentException.class,()->CampaignTrades.preview(recipe,player));
        assertEquals(loaded.ownedCopies(player),ids,"Rejected conversion cannot sacrifice outputs");
    }

    @Test public void singleAndBulkSaleOverflowRejectBeforeConsumingCopiesOrSelection() {
        var bolt=card("Lightning Bolt","M10");var player=newPlayer(deckOf(bolt,3));
        var ids=CampaignState.instance().ownedCopies(player);
        player.giveGold(Integer.MAX_VALUE-player.getGold());
        assertThrows(IllegalArgumentException.class,()->player.sellCard(bolt,2));
        assertEquals(CampaignState.instance().ownedCopies(player),ids);assertEquals(player.getGold(),Integer.MAX_VALUE);
        var selection=new forge.util.ItemPool<forge.item.PaperCard>(forge.item.PaperCard.class);selection.add(bolt,3);
        assertThrows(IllegalArgumentException.class,()->player.doBulkSell(selection));
        assertEquals(selection.countAll(),3);assertEquals(CampaignState.instance().ownedCopies(player),ids);
        CampaignConfig.instance().economy.cardSellMultiplier=Float.NaN;
        assertThrows(IllegalArgumentException.class,()->player.sellCard(bolt,1));
        assertEquals(CampaignState.instance().ownedCopies(player),ids);
        assertThrows(IllegalArgumentException.class,()->player.giveGold(1));assertEquals(player.getGold(),Integer.MAX_VALUE);
    }

    @Test public void saleQuantityAndBulkTotalsCannotWrapIntegerProceeds() {
        var rare=card("Black Lotus","LEA");CampaignConfig.instance().economy.cardSellMultiplier=100;
        var probe=newPlayer(deckOf(rare,1));int unit=probe.cardSellPrice(rare);
        int count=(int)((long)Integer.MAX_VALUE/unit+1);
        CampaignState.setInstance(new CampaignState());
        var player=newPlayer(deckOf(rare,count));var ids=CampaignState.instance().ownedCopies(player);
        assertTrue((long)unit*count>Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class,()->player.sellCard(rare,count));
        assertEquals(CampaignState.instance().ownedCopies(player),ids);assertEquals(player.getCards().count(rare),count);
        var selection=new forge.util.ItemPool<forge.item.PaperCard>(forge.item.PaperCard.class);selection.add(rare,count);
        assertThrows(IllegalArgumentException.class,()->player.doBulkSell(selection));
        assertEquals(selection.count(rare),count);assertEquals(CampaignState.instance().ownedCopies(player),ids);
    }

    @Test public void goldRewardCapDoesNotInterruptMixedLootOrDiscardFollowingCards() {
        var island=card("Island","M10");var bolt=card("Lightning Bolt","M10");
        var player=newPlayer(deckOf(island,1));player.giveGold(Integer.MAX_VALUE-player.getGold()-5);
        for(var reward:List.of(new Reward(bolt),new Reward(50),new Reward(bolt)))player.addReward(reward);
        assertEquals(player.getGold(),Integer.MAX_VALUE);assertEquals(player.getCards().count(bolt),2);
        assertEquals(CampaignState.instance().ownedCopies(player).size(),3);
        player.addReward(new Reward(50));assertEquals(player.getGold(),Integer.MAX_VALUE);
        CampaignConfig.instance().economy.goldRewardMultiplier=3;
        player.takeGold(300);player.addReward(new Reward(50));assertEquals(player.getGold(),Integer.MAX_VALUE-150);
        assertTrue(CampaignPrices.purchase(new Reward(50),0.01f,CampaignConfig.instance().economy)>150,
                "A discounted gold reward cannot buy more gold than it costs, even with generous loot settings");
    }

}
