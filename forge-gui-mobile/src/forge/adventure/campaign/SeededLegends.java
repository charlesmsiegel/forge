package forge.adventure.campaign;

import forge.StaticData;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.util.SaveFileContent;
import forge.adventure.util.SaveFileData;
import forge.deck.CardPool;
import forge.item.PaperCard;
import java.util.*;

/** Selected collectible landmarks are a saved manifest, never a fresh roll on visit/load. */
public final class SeededLegends implements SaveFileContent {
    public record Entry(String slot, String candidateId, String enemy, PaperCard card) { }
    private final Map<String, Entry> selected = new LinkedHashMap<>();
    private final Set<String> claimed = new HashSet<>();
    private boolean initialized;
    private long seed;

    public static SeededLegends capture(long seed, CampaignConfig.SeedRules rules, CardUniverse universe) {
        SeededLegends result = new SeededLegends(); result.initialize(seed, rules, universe); return result;
    }
    public void initialize(long worldSeed, CampaignConfig.SeedRules rules, CardUniverse universe) {
        if (initialized) return;
        if (rules == null || universe == null || rules.synergyChancePercent < 0 || rules.synergyChancePercent > 100)
            throw new IllegalArgumentException("Invalid seeded landmark rules or missing card universe");
        Set<String> ids = new HashSet<>();
        validate(rules.creatures, ids); validate(rules.locations, ids); validate(rules.synergy, ids);
        Random random = new Random(mixSeed(worldSeed));
        Map<String, Entry> staged = new LinkedHashMap<>();
        choose(staged, "creature", eligible(rules.creatures, universe, "creature"), random);
        choose(staged, "location", eligible(rules.locations, universe, "location"), random);
        List<CampaignConfig.SeedCandidate> packageCards = new ArrayList<>(Arrays.asList(rules.synergy));
        packageCards.sort(Comparator.comparing(candidate -> candidate.id));
        if (!packageCards.isEmpty() && random.nextInt(100) < rules.synergyChancePercent) {
            Map<String, Entry> packageEntries = new LinkedHashMap<>();
            for (CampaignConfig.SeedCandidate candidate : packageCards) {
                PaperCard card = printing(candidate, universe);
                if (card == null || !card.getRules().getType().isLand()) { packageEntries.clear(); break; }
                String slot = "synergy." + candidate.id;
                packageEntries.put(slot, new Entry(slot, candidate.id, candidate.enemy, card));
            }
            if (packageEntries.size() == packageCards.size()) staged.putAll(packageEntries);
        }
        selected.putAll(staged); seed = worldSeed; initialized = true;
    }
    private static long mixSeed(long seed) {
        long mixed = seed + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }
    private static void validate(CampaignConfig.SeedCandidate[] candidates, Set<String> ids) {
        if (candidates == null) throw new IllegalArgumentException("Missing seeded candidate pool");
        for (CampaignConfig.SeedCandidate candidate : candidates)
            if (candidate == null || candidate.id == null || candidate.id.isBlank() || !ids.add(candidate.id)
                    || candidate.card == null || candidate.card.isBlank() || candidate.enemy == null || candidate.enemy.isBlank())
                throw new IllegalArgumentException("Invalid or duplicate seeded landmark candidate");
    }
    private static PaperCard printing(CampaignConfig.SeedCandidate candidate, CardUniverse universe) {
        List<PaperCard> cards = new ArrayList<>(StaticData.instance().getCommonCards().getAllCardsNoAlt(candidate.card));
        cards.removeIf(card -> !(candidate.allowGenerationExcluded ? universe.allowsBespokePrinting(card) : universe.allowsPrinting(card))
                || card.getRules().isUnsupported());
        cards.sort(Comparator.comparing((PaperCard card) -> !card.getEdition().equals(candidate.preferredEdition))
                .thenComparing(Comparator.naturalOrder()));
        return cards.isEmpty() ? null : cards.get(0);
    }
    private static List<Entry> eligible(CampaignConfig.SeedCandidate[] pool, CardUniverse universe, String kind) {
        List<Entry> entries = new ArrayList<>();
        for (CampaignConfig.SeedCandidate candidate : pool) {
            PaperCard card = printing(candidate, universe);
            if (card == null || !card.getRules().getType().isLegendary()) continue;
            if (kind.equals("creature") ? !card.getRules().getType().isCreature() : !card.getRules().getType().isLand()) continue;
            entries.add(new Entry(kind, candidate.id, candidate.enemy, card));
        }
        entries.sort(Comparator.comparing(Entry::candidateId)); return entries;
    }
    private static void choose(Map<String, Entry> staged, String slot, List<Entry> options, Random random) {
        if (!options.isEmpty()) staged.put(slot, options.get(random.nextInt(options.size())));
    }
    public boolean isInitialized() { return initialized; }
    public long seed() { return seed; }
    public List<Entry> entries() { return List.copyOf(selected.values()); }
    public Entry entry(String slot) { return selected.get(slot); }
    public boolean isClaimed(String slot) { return claimed.contains(slot); }
    public PaperCard claim(String slot, AdventurePlayer player, WorldDeeds deeds) {
        Entry entry = selected.get(slot);
        if (entry == null) throw new IllegalArgumentException("No seeded landmark in slot " + slot);
        if (claimed.contains(slot)) return null;
        player.addCard(entry.card()); claimed.add(slot); deeds.grant("landmark." + slot);
        CampaignLog.event("legendary_landmark_claimed").with("slot", slot).with("candidate", entry.candidateId())
                .withCards("cards", List.of(entry.card())).write();
        return entry.card();
    }
    @Override public SaveFileData save() {
        SaveFileData data = new SaveFileData(); data.store("version", 1); data.store("initialized", initialized); data.store("seed", seed);
        data.storeObject("slots", selected.keySet().toArray(new String[0])); data.storeObject("claimed", claimed.toArray(new String[0]));
        for (Entry entry : selected.values()) {
            SaveFileData saved = new SaveFileData(); saved.store("candidate", entry.candidateId()); saved.store("enemy", entry.enemy());
            CardPool printing = new CardPool(); printing.add(entry.card()); saved.storeObject("printing", printing.toCardList("\n").split("\n"));
            data.store(entry.slot(), saved);
        }
        return data;
    }
    @Override public void load(SaveFileData data) {
        selected.clear(); claimed.clear(); initialized = false; seed = 0;
        if (data == null) return;
        initialized = data.readBool("initialized"); seed = data.readLong("seed");
        if (data.containsKey("claimed")) Collections.addAll(claimed, (String[])data.readObject("claimed"));
        if (data.containsKey("slots")) for (String slot : (String[])data.readObject("slots")) {
            SaveFileData saved = data.readSubData(slot);
            List<PaperCard> cards = CardPool.fromCardList(Arrays.asList((String[])saved.readObject("printing"))).toFlatList();
            if (cards.size() != 1) throw new IllegalArgumentException("Invalid saved legendary printing for " + slot);
            selected.put(slot, new Entry(slot, saved.readString("candidate"), saved.readString("enemy"), cards.get(0)));
        }
    }
}
