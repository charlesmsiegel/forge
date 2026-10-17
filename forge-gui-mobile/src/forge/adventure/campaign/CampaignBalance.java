package forge.adventure.campaign;

import java.util.Set;

/** Independent campaign controls; never alter Magic rules or historical/legendary recipes. */
public final class CampaignBalance {
    private CampaignBalance() { }
    public static void validate(CampaignConfig c) {
        if(c.difficulty==null||c.rivals==null||c.economy==null||c.player==null||c.ante==null)
            throw new IllegalArgumentException("Missing campaign balance rules");
        if(c.player.startingLife<1||c.player.startingLife>1000
                ||!Float.isFinite(c.difficulty.enemyLifeMultiplier)||c.difficulty.enemyLifeMultiplier<=0||c.difficulty.enemyLifeMultiplier>10
                ||c.difficulty.ordinaryTierFloor<0||c.difficulty.ordinaryTierFloor>2
                ||c.difficulty.aiProfile==null||!Set.of("","Default","Reckless","Cautious","Experimental").contains(c.difficulty.aiProfile)
                ||c.rivals.winsPerTier<1||c.rivals.winsPerTier>1000
                ||c.rivals.championTier<0||c.rivals.championTier>2||c.rivals.sealTier<c.rivals.championTier||c.rivals.sealTier>2
                ||c.rivals.rarityUpgradeSlotsPerTier<0||c.rivals.rarityUpgradeSlotsPerTier>20)
            throw new IllegalArgumentException("Invalid independent campaign difficulty controls");
        CampaignPrices.validate(c.economy);
        if(c.ante.enabled&&(c.ante.playerAnteCount<1||c.ante.opponentAnteCount<1||c.ante.reclamationRiskCount<1))
            throw new IllegalArgumentException("Campaign ante counts must be positive");
    }
    public static int enemyLife(int base, float adventureFactor, CampaignConfig config) {
        if(!config.isActive())return Math.max(1,Math.round(base*adventureFactor));
        config.validateBalance();
        return Math.max(1,Math.round(base*config.difficulty.enemyLifeMultiplier));
    }
}
