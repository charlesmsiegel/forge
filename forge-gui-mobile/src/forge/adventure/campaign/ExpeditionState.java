package forge.adventure.campaign;

import com.google.common.collect.Lists;
import forge.adventure.util.SaveFileContent;
import forge.adventure.util.SaveFileData;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;

import java.util.Map;

/**
 * Availability filter for expeditions (MVP.md §12): while away from home only the carried copies
 * (deck + sideboard at departure) plus copies acquired during the expedition may be used.
 * Cards are never moved between collections; the permanent collection stays the single source
 * of ownership and this state only narrows what is usable.
 */
public class ExpeditionState implements SaveFileContent {
    private boolean active;
    private String regionId = "";
    private String rootPoiId = "";
    private final CardPool carried = new CardPool();
    private final CardPool acquired = new CardPool();

    public boolean isActive() {
        return active;
    }

    public String getRegionId() {
        return regionId;
    }

    /** Id of the point of interest whose sub-maps form the region (for resetting them). */
    public String getRootPoiId() {
        return rootPoiId;
    }

    public void setRootPoiId(String rootPoiId) {
        this.rootPoiId = rootPoiId == null ? "" : rootPoiId;
    }

    public CardPool getCarried() {
        return carried;
    }

    public CardPool getAcquired() {
        return acquired;
    }

    /** Starts an expedition: the active deck's main deck and sideboard are the carried pool. */
    public void enter(String regionId, Deck activeDeck) {
        this.active = true;
        this.regionId = regionId == null ? "" : regionId;
        carried.clear();
        acquired.clear();
        carried.addAll(activeDeck.getMain());
        if (activeDeck.has(DeckSection.Sideboard))
            carried.addAll(activeDeck.get(DeckSection.Sideboard));
    }

    /** Ends the expedition: every surviving card is simply part of the permanent collection again. */
    public void leave() {
        active = false;
        regionId = "";
        rootPoiId = "";
        carried.clear();
        acquired.clear();
    }

    public void onAcquired(PaperCard card, int amount) {
        if (active && amount > 0)
            acquired.add(card, amount);
    }

    /** A copy left the collection (ante loss, sale): acquired copies are spent before carried ones. */
    public void onLost(PaperCard card, int amount) {
        if (!active)
            return;
        int fromAcquired = Math.min(acquired.count(card), amount);
        if (fromAcquired > 0)
            acquired.remove(card, fromAcquired);
        int rest = amount - fromAcquired;
        int fromCarried = Math.min(carried.count(card), rest);
        if (fromCarried > 0)
            carried.remove(card, fromCarried);
    }

    /**
     * The copies usable right now: (carried + acquired) clamped to what the collection actually
     * still contains. Ordinary basic lands are always available (Forge treats them as unlimited).
     */
    public CardPool available(CardPool collection) {
        if (!active)
            return collection;
        CardPool result = new CardPool();
        CardPool pool = new CardPool();
        pool.addAll(carried);
        pool.addAll(acquired);
        for (Map.Entry<PaperCard, Integer> e : pool) {
            int owned = collection.count(e.getKey());
            int usable = Math.min(e.getValue(), owned);
            if (usable > 0)
                result.add(e.getKey(), usable);
        }
        for (Map.Entry<PaperCard, Integer> e : collection) {
            if (AnteService.isOrdinaryBasicLand(e.getKey()) && result.count(e.getKey()) < e.getValue())
                result.add(e.getKey(), e.getValue() - result.count(e.getKey()));
        }
        return result;
    }

    @Override
    public void load(SaveFileData data) {
        leave();
        if (data == null || !data.containsKey("active"))
            return;
        active = data.readBool("active");
        regionId = data.containsKey("regionId") ? data.readString("regionId") : "";
        if (regionId == null)
            regionId = "";
        rootPoiId = data.containsKey("rootPoiId") ? data.readString("rootPoiId") : "";
        if (rootPoiId == null)
            rootPoiId = "";
        carried.addAll(readPool(data, "carried"));
        acquired.addAll(readPool(data, "acquired"));
    }

    private static CardPool readPool(SaveFileData data, String key) {
        if (!data.containsKey(key))
            return new CardPool();
        String[] lines = (String[]) data.readObject(key);
        return lines == null ? new CardPool() : CardPool.fromCardList(Lists.newArrayList(lines));
    }

    private static String[] writePool(CardPool pool) {
        String list = pool.toCardList("\n");
        return list.isEmpty() ? new String[0] : list.split("\n");
    }

    @Override
    public SaveFileData save() {
        SaveFileData data = new SaveFileData();
        data.store("active", active);
        data.store("regionId", regionId);
        data.store("rootPoiId", rootPoiId);
        data.storeObject("carried", writePool(carried));
        data.storeObject("acquired", writePool(acquired));
        return data;
    }
}
