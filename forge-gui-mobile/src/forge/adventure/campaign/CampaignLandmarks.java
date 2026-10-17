package forge.adventure.campaign;

import forge.adventure.data.PointOfInterestData;
import forge.deck.Deck;
import forge.item.PaperCard;
import java.util.Map;

/** Resolve world templates without mutating the shared POI definitions. */
public final class CampaignLandmarks {
    private CampaignLandmarks() { }
    public static PointOfInterestData pointData(PointOfInterestData template) {
        if (!CampaignConfig.instance().isActive() || template.seedSlot == null || template.seedSlot.isBlank()) return template;
        PointOfInterestData result = new PointOfInterestData(template);
        SeededLegends.Entry selected = CampaignState.instance().seededLegends().entry(template.seedSlot);
        result.active = selected != null;
        if (selected == null) result.count = 0;
        else result.displayName = selected.card().getName();
        return result;
    }
    /** The named encounter actually plays its selected card; shared templates stay untouched. */
    public static Deck encounterDeck(Deck source, SeededLegends.Entry entry) {
        if (source == null || entry == null) throw new IllegalArgumentException("Missing legendary encounter deck");
        Deck deck = new Deck(source);
        if (deck.getMain().count(entry.card()) > 0) return deck;
        PaperCard remove = null;
        boolean land = entry.card().getRules().getType().isLand();
        for (Map.Entry<PaperCard, Integer> printing : deck.getMain()) {
            if (remove == null) remove = printing.getKey();
            if (printing.getKey().getRules().getType().isLand() == land) { remove = printing.getKey(); break; }
        }
        if (remove != null) deck.getMain().remove(remove, 1);
        deck.getMain().add(entry.card());
        return deck;
    }

}
