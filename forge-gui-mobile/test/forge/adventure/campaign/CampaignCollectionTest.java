package forge.adventure.campaign;

import forge.StaticData;
import forge.adventure.data.ConfigData;
import forge.deck.CardPool;
import org.testng.annotations.Test;
import java.util.*;
import static org.testng.Assert.*;

public class CampaignCollectionTest extends AdventureTestBase {
    private forge.item.PaperCard art(int index) { return StaticData.instance().getCommonCards().getCard("Island","M10",index); }
    @Test public void basicArtAndFoilRemainSeparatePhysicalObjectsAcrossSave() {
        var a=art(1);var b=art(2);assertNotEquals(a,b);
        CardIdentityLedger ledger=new CardIdentityLedger();
        ledger.add(new OwnedCard("island-art-one",a));ledger.add(new OwnedCard("island-art-two",b));
        ledger.add(new OwnedCard("island-art-one-foil",a.getFoiled()));
        CardIdentityLedger loaded=new CardIdentityLedger();loaded.load(ledger.save());
        assertEquals(loaded.copies(),ledger.copies());
        assertEquals(loaded.copies().get(1).card.getArtIndex(),2);
        assertTrue(loaded.copies().get(2).card.isFoil());
    }
    @Test public void searchesAndPhysicalStatusDoNotMergeOrMutateCopies() {
        List<OwnedCard> copies=List.of(new OwnedCard("one",art(1)),new OwnedCard("two",art(2).getFoiled()),new OwnedCard("old-bolt",card("Lightning Bolt","LEA")));
        CardPool deck=new CardPool();deck.add(art(1),2);
        var entries=CampaignCollection.entries(copies,Set.of("one","old-bolt"),Set.of("two"),deck);
        assertEquals(entries.size(),3);assertEquals(copies.size(),3);
        assertEquals(CampaignCollection.filter(entries,new CampaignCollection.Query("","","Common",null,false,false)).size(),1);
        assertEquals(CampaignCollection.filter(entries,new CampaignCollection.Query("","","Basic Land",null,false,false)).size(),2);
        assertEquals(CampaignCollection.filter(entries,new CampaignCollection.Query("Island M10","","",null,false,false)).size(),2);
        var foil=CampaignCollection.filter(entries,new CampaignCollection.Query("","M10","",true,false,true));
        assertEquals(foil.size(),1);assertEquals(foil.get(0).copy().id,"two");assertFalse(foil.get(0).available());
        var available=CampaignCollection.filter(entries,new CampaignCollection.Query("","","",null,true,false));
        assertEquals(available.size(),2);
        assertEquals(entries.stream().filter(e->e.copy().id.equals("one")).findFirst().orElseThrow().deckCopies(),2);
        assertEquals(CampaignCollection.filter(entries,new CampaignCollection.Query("old-bolt","","",null,false,false)).size(),1);
    }
    @Test public void completionCountsNamesAndArtSlotsWithoutRequiringBothFoilFinishes() {
        CampaignConfig config=new CampaignConfig();config.campaignId="binder";config.universe.allowedEditions=new String[]{"M10"};
        CardUniverse universe=CardUniverse.capture(config,new ConfigData());
        var catalog=List.of(art(1),art(2),art(1).getFoiled(),card("Lightning Bolt","LEA"));
        var owned=List.of(new OwnedCard("foil",art(1).getFoiled()),new OwnedCard("other-art",art(2)),new OwnedCard("legacy",card("Lightning Bolt","LEA")));
        var completion=CampaignCollection.completion("M10",universe,catalog,owned);
        assertEquals(completion.namesOwned(),1);assertEquals(completion.namesTotal(),1);
        assertEquals(completion.printingsOwned(),2);assertEquals(completion.printingsTotal(),2);
        var legacy=CampaignCollection.completion("LEA",universe,catalog,owned);
        assertEquals(legacy.namesTotal(),0);assertEquals(legacy.printingsOwned(),0);
        var partial=CampaignCollection.completion("M10",universe,catalog,List.of(owned.get(0)));
        assertEquals(partial.namesOwned(),1);assertEquals(partial.printingsOwned(),1);
    }
    @Test public void completionUsesActualBansButKeepsGenerationExcludedCollectibleSlots() {
        CampaignConfig config=new CampaignConfig();config.campaignId="completion-scope";
        config.universe.allowedEditions=new String[]{"M10"};config.cards.bannedCards=new String[]{"Lightning Bolt"};
        var bolt=card("Lightning Bolt","M10");var catalog=List.of(art(1),bolt);
        var owned=List.of(new OwnedCard("land",art(1)),new OwnedCard("bolt",bolt));
        var banned=CardUniverse.capture(config,new ConfigData());
        assertEquals(CampaignCollection.completion("M10",banned,catalog,owned).namesTotal(),1);
        assertEquals(CampaignCollection.entries(owned,Set.of("land","bolt"),Set.of(),new CardPool()).size(),2);
        config.cards.bannedCards=new String[0];ConfigData plane=new ConfigData();plane.restrictedEditions=new String[]{"M10"};
        var excluded=CardUniverse.capture(config,plane);
        assertFalse(excluded.allowsPrinting(bolt));assertTrue(excluded.allowsBespokePrinting(bolt));
        assertEquals(CampaignCollection.completion("M10",excluded,catalog,owned).namesOwned(),2);
    }
    @Test public void unvisitedSetCompletionLoadsItsWholeCatalogBeforeCounting() {
        CampaignConfig config=new CampaignConfig();config.campaignId="unvisited-binder";config.universe.allowedEditions=new String[]{"FEM"};
        var universe=CardUniverse.capture(config,new ConfigData());
        var completion=CampaignCollection.completionForEdition("FEM",universe,List.of());
        long names=StaticData.instance().getCardEdition("FEM").getObtainableCards().stream().map(forge.card.CardEdition.EditionEntry::name).distinct().count();
        assertEquals(completion.namesTotal(),names);
        assertEquals(completion.printingsTotal(),StaticData.instance().getCardEdition("FEM").getObtainableCards().size());
        assertEquals(completion.namesOwned(),0);
        assertEquals(CampaignCollection.completionForEdition("FEM",universe,List.of()),completion);
    }
}
