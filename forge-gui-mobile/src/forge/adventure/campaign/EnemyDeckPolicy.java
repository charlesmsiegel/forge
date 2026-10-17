package forge.adventure.campaign;

import forge.card.CardRarity;
import forge.deck.Deck;
import forge.item.PaperCard;
import java.util.*;

/** Deterministic ordinary-opponent substitutions; authored historical/boss recipes opt out. */
public final class EnemyDeckPolicy {
    private EnemyDeckPolicy() { }

    public static Deck adapt(Deck skeleton, CardUniverse universe, Collection<PaperCard> pool, long seed, int tier) {
        return adapt(skeleton, universe, pool, seed, tier, 2);
    }
    public static Deck adapt(Deck skeleton, CardUniverse universe, Collection<PaperCard> pool, long seed, int tier, int slotsPerTier) {
        if (slotsPerTier < 0 || slotsPerTier > 20) throw new IllegalArgumentException("Invalid opponent rarity budget");
        if (skeleton == null || universe == null) throw new IllegalArgumentException("Opponent template and saved universe are required");
        if (skeleton.getMain().isEmpty()) throw new IllegalArgumentException("Opponent template has no cards; repair its configured deck before fighting");
        List<PaperCard> legal = pool.stream().filter(universe::allowsPrinting)
                .filter(c -> !c.getRules().isUnsupported()).sorted().toList();
        Deck result = new Deck(skeleton.getName());
        int upgrades = Math.max(0, Math.min(2, tier)) * slotsPerTier;
        for (PaperCard source : skeleton.getMain().toFlatList()) {
            PaperCard selected = null;
            boolean valid = universe.allowsPrinting(source) && !source.getRules().isUnsupported() && room(result, source, universe);
            if (valid && (upgrades == 0 || source.getRules().getType().isLand())) selected = source;
            if (selected == null) {
                List<PaperCard> candidates = new ArrayList<>();
                for (PaperCard candidate : legal) {
                    if (room(result, candidate, universe) && compatible(source, candidate)
                            && (candidate.getName().equals(source.getName()) || rarity(candidate) <= Math.max(rarity(source), Math.min(2, tier)))
                            && (!valid || rarity(candidate) > rarity(source))) candidates.add(candidate);
                }
                final int salt = result.getMain().countAll();
                candidates.sort(Comparator.comparingInt((PaperCard c) -> c.getName().equals(source.getName()) ? 0 : 1)
                        .thenComparingInt(c -> Math.abs(cmc(c) - cmc(source)))
                        .thenComparingInt(c -> Collections.disjoint(source.getRules().getType().getSubtypes(), c.getRules().getType().getSubtypes()) ? 1 : 0)
                        .thenComparingLong(c -> mix(seed ^ c.toString().hashCode() ^ salt))
                        .thenComparing(PaperCard::toString));
                if (!candidates.isEmpty()) {
                    selected = candidates.get(0);
                    if (valid) upgrades--;
                } else if (valid) selected = source;
                else throw new IllegalArgumentException("No legal opponent substitute for " + source.getName()
                        + "; this universe needs more cards of its color and role.");
            }
            result.getMain().add(selected);
        }
        return result;
    }

    /** Opponent use of stolen cards never mints or moves a physical ownership record. */
    public static Deck withAcquired(Deck template, CardUniverse universe, List<PaperCard> acquired) {
        Deck result = new Deck(template);
        for (PaperCard card : acquired) {
            if (!universe.allowsPrinting(card) || card.getRules().getType().isLand()) continue;
            for (PaperCard source : result.getMain().toFlatList()) {
                if (source.equals(card) || !compatible(source, card)) continue;
                result.getMain().remove(source, 1);
                if (room(result, card, universe)) { result.getMain().add(card); break; }
                result.getMain().add(source);
            }
        }
        return result;
    }

    private static boolean room(Deck deck, PaperCard card, CardUniverse universe) {
        return card.getRules().getType().isBasicLand()
                || deck.getMain().countByName(card.getName()) < (universe.isRestricted(card.getName()) ? 1 : 4);
    }
    private static int cmc(PaperCard card) { return card.getRules().getManaCost().getCMC(); }
    private static int rarity(PaperCard card) {
        if (card.getRarity() == CardRarity.MythicRare) return 3;
        if (card.getRarity() == CardRarity.Rare) return 2;
        if (card.getRarity() == CardRarity.Uncommon) return 1;
        return 0;
    }
    private static boolean compatible(PaperCard source, PaperCard candidate) {
        var from = source.getRules(); var to = candidate.getRules();
        if (from.getType().isBasicLand()) return source.getName().equals(candidate.getName());
        if (from.getType().isLand()) return to.getType().isLand() && from.getColorIdentity().equals(to.getColorIdentity());
        return !to.getType().isLand() && from.getColor().equals(to.getColor())
                && (to.getManaCost().getColorProfile() & ~from.getManaCost().getColorProfile()) == 0
                && role(source).equals(role(candidate)) && Math.abs(cmc(source) - cmc(candidate)) <= 2;
    }
    private static String role(PaperCard card) {
        if (card.getRules().getType().isCreature()) return "creature";
        String text = card.getRules().getOracleText().toLowerCase(Locale.ROOT);
        if (text.contains("damage") || text.contains("destroy target") || text.contains("exile target") || text.contains("counter target")) return "interaction";
        return "support";
    }
    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
