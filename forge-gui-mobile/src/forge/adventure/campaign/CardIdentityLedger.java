package forge.adventure.campaign;

import forge.adventure.util.SaveFileData;
import forge.deck.CardPool;
import forge.item.PaperCard;
import java.util.*;

/**
 * Counts remain in Forge. Creation/load reconcile metadata after counts exist; acquisition
 * initializes before changing counts then mints; removal retires a selected available ID.
 * Transfers bypass minting and move that same ID. Save stores IDs and Forge printing strings.
 */
final class CardIdentityLedger {
    private final Map<String, OwnedCard> copies = new LinkedHashMap<>();

    List<OwnedCard> copies() { return new ArrayList<>(copies.values()); }
    void clear() { copies.clear(); }
    void add(OwnedCard copy) {
        if (copies.putIfAbsent(copy.id, copy) != null)
            throw new IllegalArgumentException("Duplicate physical card ID: " + copy.id);
    }
    boolean remove(OwnedCard copy) { return copies.remove(copy.id, copy); }

    void reconcile(CardPool pool) {
        CardPool represented = new CardPool();
        Iterator<OwnedCard> it = copies.values().iterator();
        while (it.hasNext()) {
            OwnedCard copy = it.next();
            if (represented.count(copy.card) >= pool.count(copy.card)) it.remove();
            else represented.add(copy.card);
        }
        for (Map.Entry<PaperCard, Integer> entry : pool)
            for (int i = represented.count(entry.getKey()); i < entry.getValue(); i++)
                add(OwnedCard.mint(entry.getKey()));
    }

    SaveFileData save() {
        SaveFileData data = new SaveFileData();
        data.storeObject("ids", copies.keySet().toArray(new String[0]));
        String[] printings = copies.values().stream().map(c -> {
            CardPool one = new CardPool();
            one.add(c.card);
            return one.toCardList("\n");
        }).toArray(String[]::new);
        data.storeObject("printings", printings);
        return data;
    }

    void load(SaveFileData data) {
        clear();
        if (data == null) return;
        String[] ids = (String[]) data.readObject("ids");
        String[] printings = (String[]) data.readObject("printings");
        if (ids == null || printings == null || ids.length != printings.length)
            throw new IllegalArgumentException("Invalid physical card ledger");
        for (int i = 0; i < ids.length; i++) {
            List<PaperCard> parsed = CardPool.fromCardList(List.of(printings[i])).toFlatList();
            if (parsed.size() != 1) throw new IllegalArgumentException("Invalid physical card printing");
            add(new OwnedCard(ids[i], parsed.get(0)));
        }
    }
}
