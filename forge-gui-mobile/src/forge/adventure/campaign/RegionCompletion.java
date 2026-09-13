package forge.adventure.campaign;

import forge.StaticData;
import forge.deck.Deck;
import forge.item.PaperCard;
import forge.item.SealedTemplate;
import forge.item.generation.BoosterGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sealed-product rewards for completing a historical region (MVP.md §25, §27). A booster box is
 * simply N unopened boosters sharing a box id in their tags; each is opened individually from the
 * inventory like any other Adventure booster, so a box is never dumped as one card list.
 */
public final class RegionCompletion {
    private RegionCompletion() {
    }

    public static SealedTemplate boosterTemplate(String edition) {
        SealedTemplate template = StaticData.instance().getBoosters().get(edition);
        if (template == null)
            throw new IllegalArgumentException("No booster template for edition " + edition);
        ensureEditionLoaded(edition);
        return template;
    }

    /**
     * With lazy card-script loading the booster print sheets only see cards that have already
     * been materialised; touching every card of the edition first makes collation complete.
     */
    static void ensureEditionLoaded(String edition) {
        forge.card.CardEdition ed = StaticData.instance().getCardEdition(edition);
        if (ed == null)
            return;
        for (forge.card.CardEdition.EditionEntry card : ed.getObtainableCards())
            StaticData.instance().getCommonCards().getCard(card.name(), edition);
    }

    /**
     * Creates {@code count} unopened boosters of {@code edition}. Contents are generated with
     * Forge's collation now (like every Adventure booster) but only enter the collection when the
     * player opens the pack.
     *
     * @param label shown in the pack name, e.g. "Stronghold Booster Box"; null for loose boosters
     */
    public static List<Deck> createBoosters(String edition, int count, String label) {
        SealedTemplate template = boosterTemplate(edition);
        String editionName = StaticData.instance().getCardEdition(edition) == null
                ? edition : StaticData.instance().getCardEdition(edition).getName();
        String boxId = "box:" + UUID.randomUUID();
        List<Deck> packs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            List<PaperCard> cards = BoosterGenerator.getBoosterPack(template);
            Deck pack = new Deck();
            pack.getMain().add(cards);
            pack.setName(label == null ? editionName + " Booster"
                    : editionName + " Booster (" + label + " " + (i + 1) + "/" + count + ")");
            pack.setComment(edition);
            pack.getTags().add("edition:" + edition);
            if (label != null && count > 1)
                pack.getTags().add(boxId);
            packs.add(pack);
        }
        return packs;
    }
}
