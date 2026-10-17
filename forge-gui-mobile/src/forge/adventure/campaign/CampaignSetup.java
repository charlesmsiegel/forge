package forge.adventure.campaign;

import com.badlogic.gdx.utils.Json;
import forge.StaticData;
import forge.card.ColorSet;
import forge.deck.Deck;
import forge.item.PaperCard;
import java.util.*;

/** New-game choices never mutate the plane configuration or an existing save. */
public final class CampaignSetup {
    private CampaignSetup() { }
    public static CampaignConfig withPreset(CampaignConfig source, String preset) {
        Json json = new Json(); CampaignConfig copy = json.fromJson(CampaignConfig.class, json.toJson(source));
        if (preset != null && !preset.isBlank()) { copy.universe.preset = preset; copy.universe.allowedEditions = new String[0]; }
        return copy;
    }
    private static int limit(PaperCard card, CampaignConfig config, CardUniverse universe) {
        return card.getRules().getType().isBasicLand() ? Integer.MAX_VALUE
                : universe.isRestricted(card.getName()) ? config.player.restrictedCopyLimit : config.player.ordinaryCopyLimit;
    }
    public static Deck starter(Deck source, ColorSet colors, CampaignConfig config, CardUniverse universe) {
        if (source == null) throw new IllegalArgumentException("No starting deck is available for this selection.");
        if (!config.isActive() || universe == null) return source;
        boolean hasLand = false, hasSpell = false;
        int manaColors = 0;
        String[] landNames = {"Plains", "Island", "Swamp", "Mountain", "Forest"};
        for (var entry : source.getMain()) {
            if (entry.getKey().getRules().getType().isLand()) hasLand = true;
            for (int i = 0; i < landNames.length; i++) if (entry.getKey().getName().equals(landNames[i])) manaColors |= 1 << i;
        }
        if (manaColors == 0) manaColors = colors.getColor();
        for (var entry : source.getAllCardsInASinglePool(true, true)) {
            if (!entry.getKey().getRules().getType().isLand()
                    && (entry.getKey().getRules().getManaCost().getColorProfile() & ~manaColors) == 0) hasSpell = true;
        }
        boolean valid = source.getMain().countAll() >= config.player.minDeckSize && hasLand && hasSpell;
        for (var entry : source.getAllCardsInASinglePool(true, true))
            if (!universe.isLegal(entry.getKey()) || entry.getKey().getRules().isUnsupported()
                    || source.getAllCardsInASinglePool(true, true).countByName(entry.getKey().getName()) > limit(entry.getKey(), config, universe)) valid = false;
        if (source.has(forge.deck.DeckSection.Commander)
                && forge.deck.DeckFormat.Commander.getDeckConformanceProblem(source) != null) valid = false;
        if (valid) return new Deck(source);
        if (source.has(forge.deck.DeckSection.Commander)) throw new IllegalArgumentException(
                "This Commander starter is incompatible with the selected campaign universe or copy rules. Choose another universe or deck mode.");
        Deck result = new Deck(source.getName());
        for (var entry : source.getMain()) if (universe.isLegal(entry.getKey()) && !entry.getKey().getRules().isUnsupported()
                && (entry.getKey().getRules().getType().isLand()
                || (entry.getKey().getRules().getManaCost().getColorProfile() & ~colors.getColor()) == 0)) {
            int count = Math.min(entry.getValue(), Math.max(0, limit(entry.getKey(), config, universe) - result.getMain().countByName(entry.getKey().getName())));
            if (count > 0) result.getMain().add(entry.getKey(), count);
        }
        int targetSize = Math.max(config.player.minDeckSize, result.getMain().countAll());
        int basicTarget = (int)Math.ceil(targetSize * 0.4);
        List<PaperCard> basics = new ArrayList<>();
        String[] basicNames = {"Plains", "Island", "Swamp", "Mountain", "Forest"};
        for (int i = 0; i < basicNames.length; i++) {
            if ((colors.getColor() & (1 << i)) == 0) continue;
            PaperCard basic = basicPrinting(basicNames[i], universe);
            if (basic == null) throw new IllegalArgumentException("No legal basic land for the selected colors.");
            basics.add(basic);
        }
        if (basics.isEmpty()) {
            PaperCard basic = basicPrinting("Wastes", universe);
            if (basic == null) throw new IllegalArgumentException("No legal colorless basic land for this universe.");
            basics.add(basic);
        }
        int matchingBasics = 0;
        for (var entry : result.getMain()) if (basics.stream().anyMatch(basic -> basic.getName().equals(entry.getKey().getName()))) matchingBasics += entry.getValue();
        for (int i = matchingBasics; i < basicTarget; i++) {
            if (result.getMain().countAll() >= targetSize) {
                PaperCard remove = null;
                for (var printing : result.getMain()) if (!printing.getKey().getRules().getType().isBasicLand()) remove = printing.getKey();
                if (remove == null) throw new IllegalArgumentException("Cannot supply the selected starter's mana colors.");
                result.getMain().remove(remove, 1);
            }
            result.getMain().add(basics.get(i % basics.size()));
        }
        Set<String> candidates = new LinkedHashSet<>(List.of("Grizzly Bears", "Llanowar Elves", "Runeclaw Bear", "Elvish Visionary",
                "Goblin Arsonist", "Mogg Fanatic", "Raging Goblin", "Goblin Piker", "Borderland Marauder",
                "Walking Corpse", "Foul Imp", "Vampire Nighthawk", "Child of Night", "Wind Drake", "Welkin Tern", "Sage Owl",
                "Savannah Lions", "Silvercoat Lion", "Elite Vanguard", "Youthful Knight", "Silver Myr", "Iron Myr", "Myr Sire"));
        candidates.addAll(universe.legalNames());
        int creatureCount = 0;
        for (var entry : result.getMain()) if (entry.getKey().getRules().getType().isCreature()) creatureCount += entry.getValue();
        for (String name : candidates) {
            if (result.getMain().countAll() >= targetSize && creatureCount > 0) break;
            PaperCard selected = null;
            for (PaperCard card : StaticData.instance().getCommonCards().getAllCardsNoAlt(name)) {
                if (card.getRules().getType().isCreature() && !card.getRules().isUnsupported()
                        && card.getRules().getColor().hasNoColorsExcept(colors)
                        && (card.getRules().getManaCost().getColorProfile() & ~colors.getColor()) == 0
                        && !card.getRules().hasKeyword("Defender") && card.getRules().getManaCost().getCMC() >= 1
                        && card.getRules().getManaCost().getCMC() <= 4 && universe.allowsPrinting(card)) { selected = card; break; }
            }
            if (selected == null) continue;
            int room = targetSize - result.getMain().countAll();
            if (room <= 0 && creatureCount == 0) {
                for (var printing : result.getMain().toFlatList()) if (printing.getRules().getType().isBasicLand()) {
                    result.getMain().remove(printing, 1); room++; if (room >= 3) break;
                }
            }
            int count = Math.min(room, Math.max(0, limit(selected, config, universe) - result.getMain().countByName(name)));
            if (count > 0) { result.getMain().add(selected, count); creatureCount += count; }
        }
        if (result.getMain().countAll() < targetSize || creatureCount == 0)
            throw new IllegalArgumentException("This card universe cannot supply a playable starter in the selected colors. Choose another universe or color.");
        return result;
    }
    private static PaperCard basicPrinting(String name, CardUniverse universe) {
        return StaticData.instance().getCommonCards().getAllCardsNoAlt(name).stream()
                .filter(universe::allowsPrinting).sorted().findFirst().orElse(null);
    }

}
