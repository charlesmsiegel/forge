package forge.adventure.campaign;

import forge.adventure.data.ConfigData;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class CampaignRecognitionTest extends AdventureTestBase {
    @Test public void deedsChangeRumorsAndUnlockOffersWithoutChangingLegacyAccess() {
        var config = CampaignConfig.load(PLANE_DIR+"campaign.json"); WorldDeeds deeds = new WorldDeeds();
        String early = String.join("\n",CampaignRecognition.rumors(deeds));
        assertFalse(CampaignRecognition.commissionAvailable(deeds));
        var original = config.trades[0]; assertTrue(CampaignTrades.available(original,deeds));
        var gated = java.util.Arrays.stream(config.trades).filter(r -> r.requires.length>0).findFirst().orElseThrow();
        assertFalse(CampaignTrades.available(gated,deeds));
        for(String flag:gated.requires) deeds.grant(flag);
        assertTrue(CampaignTrades.available(gated,deeds));
        deeds.grant("shandalar.champion"); deeds.grant("seal.broken");
        assertTrue(CampaignRecognition.commissionAvailable(deeds));
        assertNotEquals(String.join("\n",CampaignRecognition.rumors(deeds)),early);
    }
    @Test public void messengerCommissionRequiresAcceptanceAndAwardsOnlyOnceAcrossReload() {
        CampaignState previous = CampaignState.instance();
        try {
            CampaignState.setInstance(new CampaignState());
            var config = CampaignConfig.load(PLANE_DIR+"campaign.json"); var universe = CardUniverse.capture(config,new ConfigData());
            WorldDeeds deeds = new WorldDeeds();
            var player = newPlayer(deckOf(card("Island","TMP"),40)); int before=player.getCards().countAll();
            assertNull(CampaignRecognition.deliver("urza-challenge",player,deeds,universe));
            assertThrows(IllegalArgumentException.class,()->CampaignRecognition.acceptCommission(deeds));
            deeds.grant("shandalar.champion"); CampaignRecognition.acceptCommission(deeds);
            assertNull(CampaignRecognition.deliver("bolas-challenge",player,deeds,universe));
            assertNotNull(CampaignRecognition.deliver("urza-challenge",player,deeds,universe));
            WorldDeeds loaded=new WorldDeeds();loaded.load(deeds.save());
            assertNull(CampaignRecognition.deliver("urza-conquest",player,loaded,universe));
            assertEquals(player.getCards().countAll(),before+1);
            assertFalse(CampaignRecognition.commissionAvailable(loaded));
        } finally { CampaignState.setInstance(previous); }
    }
    @Test public void lockedExchangeCannotConsumeCardsThroughDirectDomainCalls() {
        CampaignState previous=CampaignState.instance();
        try {
            CampaignState state=new CampaignState(); CampaignState.setInstance(state);
            var config=CampaignConfig.load(PLANE_DIR+"campaign.json");
            var recipe=java.util.Arrays.stream(config.trades).filter(r->r.id.equals("champion-reserve")).findFirst().orElseThrow();
            var player=newPlayer(deckOf(card("Mogg Fanatic","TMP"),15,card("Mountain","TMP"),25));
            var copies=state.ownedCopies(player).stream().filter(c->c.card.getName().equals("Mogg Fanatic")).toList();
            assertThrows(IllegalArgumentException.class,()->CampaignTrades.preview(recipe,player));
            assertThrows(IllegalArgumentException.class,()->CampaignTrades.execute(recipe,copies,player,false,new java.util.Random(4)));
            assertEquals(player.getCards().countAll(),40);
            assertEquals(state.ownedCopies(player).stream().filter(c->c.card.getName().equals("Mogg Fanatic")).count(),15L);
        } finally {CampaignState.setInstance(previous);}
    }
}
