package forge.adventure.campaign;

import com.google.common.collect.Lists;
import forge.adventure.util.SaveFileContent;
import forge.adventure.util.SaveFileData;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Arrays;

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
    private final Set<String> carriedIds = new LinkedHashSet<>();
    private final Set<String> acquiredIds = new LinkedHashSet<>();
    private boolean hasIdentity;

    void bindCopies(List<OwnedCard> owned) {
        if (!active || hasIdentity) return;
        CardPool remainingCarried = new CardPool();
        remainingCarried.addAll(carried);
        CardPool remainingAcquired = new CardPool();
        remainingAcquired.addAll(acquired);
        for (OwnedCard copy : owned) {
            if (remainingCarried.count(copy.card) > 0) {
                remainingCarried.remove(copy.card);
                carriedIds.add(copy.id);
            } else if (remainingAcquired.count(copy.card) > 0) {
                remainingAcquired.remove(copy.card);
                acquiredIds.add(copy.id);
            }
        }
        hasIdentity = true;
    }

    List<OwnedCard> availableCopies(List<OwnedCard> owned) {
        if (!active) return owned;
        List<OwnedCard> result = new ArrayList<>();
        for (OwnedCard copy : owned)
            if (acquiredIds.contains(copy.id)) result.add(copy);
        for (OwnedCard copy : owned)
            if (carriedIds.contains(copy.id)) result.add(copy);
        return result;
    }

    void onAcquired(OwnedCard copy) {
        onAcquired(copy.card, 1);
        if (active) acquiredIds.add(copy.id);
    }

    void onLost(OwnedCard copy) {
        if (acquiredIds.remove(copy.id)) acquired.remove(copy.card);
        else if (carriedIds.remove(copy.id)) carried.remove(copy.card);
    }

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
        carriedIds.clear();
        acquiredIds.clear();
        hasIdentity = false;
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
        carriedIds.clear();
        acquiredIds.clear();
        hasIdentity = false;
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
     * still contains. Basic lands follow the same carried-inventory rule as other cards.
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
        hasIdentity = data.containsKey("carriedIds");
        if (hasIdentity) {
            carriedIds.addAll(Arrays.asList((String[]) data.readObject("carriedIds")));
            acquiredIds.addAll(Arrays.asList((String[]) data.readObject("acquiredIds")));
        }
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
        if (hasIdentity) {
            data.storeObject("carriedIds", carriedIds.toArray(new String[0]));
            data.storeObject("acquiredIds", acquiredIds.toArray(new String[0]));
        }
        return data;
    }
}
