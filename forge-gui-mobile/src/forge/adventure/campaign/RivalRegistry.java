package forge.adventure.campaign;

import forge.adventure.data.EnemyData;
import forge.adventure.util.SaveFileContent;
import forge.adventure.util.SaveFileData;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * All persistent rivals of the current campaign, in promotion order.
 */
public class RivalRegistry implements SaveFileContent {
    private final List<Rival> rivals = new ArrayList<>();

    public List<Rival> all() {
        return Collections.unmodifiableList(rivals);
    }

    public int size() {
        return rivals.size();
    }

    /** A stable page of the camp roster, including rivals beyond the visible map slots. */
    public List<Rival> page(int page, int size) {
        if (page < 0 || size < 1)
            throw new IllegalArgumentException("Invalid rival page");
        long start = (long) page * size;
        if (start >= rivals.size())
            return List.of();
        return List.copyOf(rivals.subList((int) start, (int) Math.min(start + size, rivals.size())));
    }

    public Rival byId(String id) {
        if (id == null)
            return null;
        for (Rival r : rivals)
            if (r.id.equals(id))
                return r;
        return null;
    }

    /** The {@code slot}-th rival (0-based, promotion order) or null; used by rival camp maps. */
    public Rival atSlot(int slot) {
        return slot >= 0 && slot < rivals.size() ? rivals.get(slot) : null;
    }

    /**
     * Turns a generic enemy into a persistent rival with a generated, unique name.
     * The rival starts with no record and no cards; the caller records the match.
     */
    public Rival promote(EnemyData template, CampaignConfig.RivalRules rules, Random random) {
        String name = generateName(template, rules, random);
        Rival rival = new Rival(UUID.randomUUID().toString(), name, template.name);
        rivals.add(rival);
        return rival;
    }

    String generateName(EnemyData template, CampaignConfig.RivalRules rules, Random random) {
        Set<String> taken = new HashSet<>();
        for (Rival r : rivals)
            taken.add(r.name);
        String[] names = rules.names == null || rules.names.length == 0 ? new String[]{"Nameless"} : rules.names;
        String[] epithets = rules.epithets == null || rules.epithets.length == 0 ? new String[]{"the Thief"} : rules.epithets;
        for (int attempt = 0; attempt < 50; attempt++) {
            String candidate = names[random.nextInt(names.length)] + ", " + epithets[random.nextInt(epithets.length)];
            if (!taken.contains(candidate))
                return candidate;
        }
        return names[random.nextInt(names.length)] + " #" + (rivals.size() + 1);
    }

    /** Cards the player may challenge this rival for: exactly the copies it took from them. */
    public List<PaperCard> reclaimable(String rivalId) {
        Rival rival = byId(rivalId);
        return rival == null ? List.of() : rival.getCardsTakenFromPlayer();
    }

    @Override
    public void load(SaveFileData data) {
        rivals.clear();
        if (data == null || !data.containsKey("count"))
            return;
        int count = data.readInt("count");
        for (int i = 0; i < count; i++) {
            Rival rival = new Rival();
            rival.load(data.readSubData("rival_" + i));
            rivals.add(rival);
        }
    }

    @Override
    public SaveFileData save() {
        SaveFileData data = new SaveFileData();
        data.store("count", rivals.size());
        for (int i = 0; i < rivals.size(); i++)
            data.store("rival_" + i, rivals.get(i).save());
        return data;
    }
}
