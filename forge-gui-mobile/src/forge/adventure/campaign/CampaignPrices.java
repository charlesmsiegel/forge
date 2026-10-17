package forge.adventure.campaign;

import forge.adventure.util.CardUtil;
import forge.adventure.util.Reward;

/** Price floors apply after discounts, including known pack contents; free earned rewards stay free. */
public final class CampaignPrices {
    private CampaignPrices() { }
    public static void validate(CampaignConfig.EconomyRules rules) {
        if(rules==null||!valid(rules.goldRewardMultiplier,true)||!valid(rules.cardSellMultiplier,true)||!valid(rules.shopPriceMultiplier,false)||!valid(rules.boosterPriceMultiplier,false))
            throw new IllegalArgumentException("Invalid campaign economy multipliers");
    }
    private static boolean valid(float value,boolean zero){return Float.isFinite(value)&&value>=(zero?0:0.001f)&&value<=100;}
    private static int price(double value) {
        if(!Double.isFinite(value)||value<0||value>=Integer.MAX_VALUE-1)
            throw new IllegalArgumentException("Campaign price exceeds supported gold range");
        return (int)Math.floor(value);
    }
    public static long rewardGold(int count, CampaignConfig.EconomyRules rules) {
        validate(rules);
        return Math.round(Math.max(0,count)*(double)rules.goldRewardMultiplier);
    }
    public static int saleCeiling(int base, boolean foil, CampaignConfig.EconomyRules rules) {
        validate(rules);
        return price(Math.max(0,base)*(double)rules.cardSellMultiplier*(foil?1.2:1)*2);
    }
    public static int sale(int base, boolean foil, float townModifier, CampaignConfig.EconomyRules rules) {
        int ceiling=saleCeiling(base,foil,rules);
        if(!Float.isFinite(townModifier))throw new IllegalArgumentException("Invalid town price modifier");
        return Math.min(ceiling,price(Math.max(0,base)*(double)rules.cardSellMultiplier*(foil?1.2:1)*Math.max(0,Math.min(2,2-townModifier))));
    }
    public static int purchase(Reward reward,float discount,CampaignConfig.EconomyRules rules) {
        validate(rules);
        if(!Float.isFinite(discount)||discount<0)throw new IllegalArgumentException("Invalid purchase discount");
        long floor=0;
        double base=reward.getCard()!=null ? CardUtil.getCardPrice(reward.getCard())*(double)rules.shopPriceMultiplier
                : reward.getDeck()!=null ? CardUtil.getBoosterPrice(reward.getDeck())*(double)rules.boosterPriceMultiplier : CardUtil.getRewardPrice(reward);
        double nominal=base*discount;
        if(reward.getCard()!=null&&!reward.getCard().hasNoSellValue())
            floor=saleCeiling(CardUtil.getCardPrice(reward.getCard()),reward.getCard().isFoil(),rules);
        else if(reward.getType()==Reward.Type.Gold) floor=rewardGold(reward.getCount(),rules);
        else if(reward.getDeck()!=null&&!reward.getDeck().getTags().contains("noSell"))
            for(var card:reward.getDeck().getAllCardsInASinglePool(true,true).toFlatList())
                if(!card.hasNoSellValue())floor+=saleCeiling(CardUtil.getCardPrice(card),card.isFoil(),rules);
        return Math.max(1,price(Math.max(nominal,floor+1d)));
    }
}
