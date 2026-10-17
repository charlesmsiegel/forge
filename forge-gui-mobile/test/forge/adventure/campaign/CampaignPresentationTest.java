package forge.adventure.campaign;

import org.testng.annotations.Test;
import java.util.List;
import java.util.Set;
import static org.testng.Assert.*;

public class CampaignPresentationTest extends AdventureTestBase {
    @Test public void packSummaryCountsNewNamesAndArtWithoutMergingPhysicalRewards() {
        var island=card("Island","M10");
        var second=forge.StaticData.instance().getCommonCards().getCard("Island","M10",2);
        var bolt=card("Lightning Bolt","M10");
        var summary=CampaignPresentation.pack(List.of(island,island.getFoiled(),second,bolt,bolt),List.of(new OwnedCard("old",island)));
        assertEquals(summary.cards(),5);
        assertEquals(summary.newNames(),1);
        assertEquals(summary.newArts(),2);
    }
    @Test public void anteWarningExplainsPhysicalLossProtectionFallbackAndDungeonLife() {
        var stake=new AnteStake(AnteStake.Kind.RECLAMATION,List.of(card("Island","M10")),List.of(),List.of("protected"),List.of());
        var text=CampaignPresentation.anteWarning(stake,Set.of("protected"),true);
        assertTrue(text.contains("permanently"));assertTrue(text.contains("protected"));assertTrue(text.contains("life"));
        assertFalse(CampaignPresentation.anteWarning(stake,Set.of(),false).contains("protected"));
    }
}
