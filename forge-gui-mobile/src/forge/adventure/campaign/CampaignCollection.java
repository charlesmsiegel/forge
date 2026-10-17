package forge.adventure.campaign;

import forge.deck.CardPool;
import forge.item.PaperCard;
import java.util.*;

/** Read-only binder projection over physical IDs; finishes never collapse ownership. */
public final class CampaignCollection {
    private CampaignCollection() { }
    public record Entry(OwnedCard copy, boolean available, boolean protectedCopy, int deckCopies) { }
    public record Query(String text, String edition, String rarity, Boolean foil, boolean availableOnly, boolean protectedOnly) { }
    public record Completion(int namesOwned, int namesTotal, int printingsOwned, int printingsTotal) { }

    public static List<Entry> entries(List<OwnedCard> copies, Set<String> available, Set<String> protectedIds, CardPool deck) {
        return copies.stream().map(c -> new Entry(c,available.contains(c.id),protectedIds.contains(c.id),deck.count(c.card)))
                .sorted(Comparator.comparing((Entry e) -> e.copy.card).thenComparing(e -> e.copy.id)).toList();
    }
    public static List<Entry> filter(List<Entry> entries, Query query) {
        String edition=normalized(query.edition),rarity=normalized(query.rarity);
        String[] words=normalized(query.text).split("\\s+");
        return entries.stream().filter(entry -> {
            PaperCard card=entry.copy.card;
            if (!edition.isEmpty() && !edition.equals(normalized(card.getEdition()))) return false;
            if (!rarity.isEmpty() && forge.card.CardRarity.smartValueOf(query.rarity)!=card.getRarity()) return false;
            if (query.foil!=null && query.foil!=card.isFoil()) return false;
            if (query.availableOnly && !entry.available || query.protectedOnly && !entry.protectedCopy) return false;
            String haystack=normalized(card.getName()+" "+card.getEdition()+" "+card.getCollectorNumber()+" "
                    +card.getArtist()+" "+entry.copy.id+" art:"+card.getArtIndex()+" "+(card.isFoil()?"foil":"regular"));
            for (String word:words) if (!haystack.contains(word)) return false;
            return true;
        }).toList();
    }
    private static String normalized(String text) { return text==null ? "" : text.trim().toLowerCase(Locale.ROOT); }
    private static String printing(PaperCard card) {
        return card.getEdition()+"\u0000"+card.getName()+"\u0000"+card.getCollectorNumber()+"\u0000"+card.getArtIndex();
    }
    /** Foil satisfies its base art slot; denominators use eligible campaign printings, not generation exclusions. */
    public static Completion completion(String edition, CardUniverse universe, Collection<PaperCard> catalog, List<OwnedCard> owned) {
        if (universe==null) throw new IllegalArgumentException("Set completion needs the saved campaign universe");
        Map<String,String> eligible=new HashMap<>();
        for (PaperCard card:catalog) if (card.getEdition().equalsIgnoreCase(edition) && universe.allowsBespokePrinting(card))
            eligible.put(printing(card),card.getName());
        Set<String> names=new HashSet<>(eligible.values()),foundNames=new HashSet<>(),foundPrintings=new HashSet<>();
        for (OwnedCard copy:owned) {
            String key=printing(copy.card);
            if (eligible.containsKey(key)) {foundPrintings.add(key);foundNames.add(copy.card.getName());}
        }
        return new Completion(foundNames.size(),names.size(),foundPrintings.size(),eligible.size());
    }
    /** Materialize a selected edition before reading Forge's lazy catalog. */
    public static Completion completionForEdition(String edition, CardUniverse universe, List<OwnedCard> owned) {
        RegionCompletion.ensureEditionLoaded(edition);
        return completion(edition,universe,forge.StaticData.instance().getCommonCards().getAllCards(),owned);
    }
    public static String printingLabel(PaperCard card) {
        return card.getEdition()+" #"+card.getCollectorNumber()+" • Art "+card.getArtIndex()+(card.isFoil()?" • foil":" • regular");
    }
}
