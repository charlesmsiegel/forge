package forge.adventure.campaign;

import forge.item.PaperCard;
import java.util.*;

/** Read-only summaries shared by campaign presentation; no reward/ownership operations. */
public final class CampaignPresentation {
    private CampaignPresentation() { }
    public record PackSummary(int cards, int newNames, int newArts) { }
    private static String art(PaperCard card) {
        return card.getEdition()+"|"+card.getName()+"|"+card.getCollectorNumber()+"|"+card.getArtIndex();
    }
    public static PackSummary pack(List<PaperCard> cards, List<OwnedCard> owned) {
        Set<String> names=new HashSet<>(),arts=new HashSet<>();
        for(var copy:owned){names.add(copy.card.getName());arts.add(art(copy.card));}
        int newNames=0,newArts=0;
        for(var card:cards){if(names.add(card.getName()))newNames++;if(arts.add(art(card)))newArts++;}
        return new PackSummary(cards.size(),newNames,newArts);
    }
    public static String anteWarning(AnteStake stake, Set<String> protectedIds, boolean persistentLife) {
        String text="\nLosing transfers these physical cards permanently; winning takes the opposing stake.";
        if(stake.playerIds.stream().anyMatch(protectedIds::contains))
            text+="\nA protected copy is at risk: protection falls back when no unprotected eligible copy remains.";
        if(persistentLife)text+="\nDungeon life persists between fights. Retreat before another duel if needed.";
        return text;
    }
}
