package forge.adventure.campaign;

import forge.adventure.util.SaveFileContent;
import forge.adventure.util.SaveFileData;
import forge.deck.Deck;
import forge.deck.CardPool;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exact printings learned about a persistent opponent. Knowledge never implies current possession. */
public final class OpponentKnowledge implements SaveFileContent {
    private final Map<String, Set<PaperCard>> known = new LinkedHashMap<>();

    public void observe(String opponentId, PaperCard card) {
        if (opponentId == null || opponentId.isBlank() || card == null) return;
        known.computeIfAbsent(opponentId, key -> new LinkedHashSet<>()).add(card);
    }

    public List<PaperCard> known(String opponentId) {
        return new ArrayList<>(known.getOrDefault(opponentId, Set.of()));
    }

    public List<PaperCard> targets(String opponentId, Deck activeDeck) {
        List<PaperCard> targets = new ArrayList<>();
        if (activeDeck == null) return targets;
        for (PaperCard card : known(opponentId))
            if (activeDeck.getMain().count(card) > 0) targets.add(card);
        return targets;
    }

    @Override
    public SaveFileData save() {
        SaveFileData data = new SaveFileData();
        data.store("version", 1);
        data.storeObject("ids", known.keySet().toArray(new String[0]));
        int i = 0;
        for (Set<PaperCard> cards : known.values()) {
            CardPool pool = new CardPool();
            for (PaperCard card : cards) pool.add(card);
            data.storeObject("cards_" + i++, pool.toCardList("\n").split("\n"));
        }
        return data;
    }

    @Override
    public void load(SaveFileData data) {
        known.clear();
        if (data == null || !data.containsKey("ids")) return;
        String[] ids = (String[]) data.readObject("ids");
        for (int i = 0; i < ids.length; i++)
            if (data.containsKey("cards_" + i))
                for (PaperCard card : CardPool.fromCardList(java.util.Arrays.asList((String[]) data.readObject("cards_" + i))).toFlatList())
                    observe(ids[i], card);
    }
}
