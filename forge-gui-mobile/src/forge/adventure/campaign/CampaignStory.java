package forge.adventure.campaign;

import forge.StaticData;
import forge.adventure.player.AdventurePlayer;
import forge.deck.Deck;
import forge.item.PaperCard;
import java.util.*;

/** Authored encounters use ordinary Magic; deeds gate audiences and once-only rewards. */
public final class CampaignStory {
    private CampaignStory() { }
    public static CampaignConfig.StoryEncounter encounter(CampaignConfig config, String id) {
        CampaignConfig.StoryEncounter found = null; Set<String> ids = new HashSet<>();
        for (var entry : config.story.encounters) {
            if (entry.id == null || entry.id.isBlank() || !ids.add(entry.id)) throw new IllegalArgumentException("Invalid or duplicate story encounter");
            if (entry.id.equals(id)) found = entry;
        }
        if (found == null) throw new IllegalArgumentException("Unknown story encounter " + id);
        return found;
    }
    public static boolean canChallenge(CampaignConfig config, String id, WorldDeeds deeds) {
        for (String prerequisite : encounter(config, id).requires) if (!deeds.has(prerequisite)) return false;
        return true;
    }
    public static String title(WorldDeeds deeds) {
        if (deeds.has("seal.broken")) return "Planeswalker";
        if (deeds.has("shandalar.champion")) return "Champion of Shandalar";
        return "Mage";
    }
    public static String audience(CampaignConfig config, String id, WorldDeeds deeds) {
        var entry = encounter(config, id);
        String text = entry.audience;
        if (id.startsWith("bolas") && deeds.has("legend.urza.conquered")) text += "\nYou defeated Urza? A tolerable warm-up for meeting my magnificence.";
        if (id.startsWith("urza") && deeds.has("legend.bolas.conquered")) text += "\nBolas has fallen to you. Let us see whether that was more than good fortune.";
        if (id.startsWith("bolas") && deeds.has("legend.bolas.conquered")) text += "\nYour supposed victory proves nothing. I was conducting an experiment. You merely followed my plan.";
        if (!canChallenge(config, id, deeds)) text += "\n" + entry.refusal;
        return text;
    }
    public static List<PaperCard> resolve(CampaignConfig config, String id, boolean won, AdventurePlayer player, WorldDeeds deeds, CardUniverse universe) {
        var entry = encounter(config, id);
        if (!won) return List.of();
        if (!canChallenge(config, id, deeds)) throw new IllegalStateException("This challenge is not unlocked");
        for (String flag : entry.grants) if (flag == null || flag.isBlank()) throw new IllegalArgumentException("Invalid encounter deed");
        String completed = "encounter." + id;
        if (deeds.has(completed)) return List.of();
        List<PaperCard> rewards = new ArrayList<>();
        if (entry.reward != null && !entry.reward.isBlank()) {
            PaperCard reward = printing(entry.reward, universe);
            if (reward != null) { player.addCard(reward); rewards.add(reward); }
        }
        deeds.grant(completed);
        for (String flag : entry.grants) deeds.grant(flag);
        boolean champion = true;
        for (String guardian : config.story.guardianFlags) if (!deeds.has(guardian)) champion = false;
        if (champion && config.story.guardianFlags.length > 0) deeds.grant("shandalar.champion");
        CampaignLog.event("story_encounter_completed").with("encounter", id).with("title", title(deeds)).write();
        return List.copyOf(rewards);
    }
    private static PaperCard printing(String name, CardUniverse universe) {
        if (universe == null) throw new IllegalStateException("Missing campaign card universe");
        return StaticData.instance().getCommonCards().getAllCardsNoAlt(name).stream()
                .filter(card -> !card.getRules().isUnsupported() && (universe.allowsBespokePrinting(card)
                        || (card.getRules().getType().isBasicLand() && universe.allowsPrinting(card))))
                .sorted().findFirst().orElse(null);
    }
    public static Deck deck(CampaignConfig config, String id, CardUniverse universe) {
        var entry = encounter(config, id);
        Deck deck = new Deck(entry.name);
        int missingSpells = 0;
        for (var recipe : entry.cards) {
            if (recipe.count <= 0 || recipe.count > 60) throw new IllegalArgumentException("Invalid authored deck count");
            PaperCard card = printing(recipe.name, universe);
            if (card == null) { missingSpells += recipe.count; continue; }
            if (recipe.count > 4 && !card.getRules().getType().isBasicLand()
                    && !(entry.bolasDuplicates && recipe.name.contains("Nicol Bolas")))
                throw new IllegalArgumentException("Only Bolas receives an explicit authored duplication privilege");
            deck.getMain().add(card, recipe.count);
        }
        List<PaperCard> spells = new ArrayList<>();
        for (String name : entry.fallbackSpells) {
            PaperCard card = printing(name, universe);
            if (card != null && !card.getRules().getType().isLand()) spells.add(card);
        }
        for (PaperCard card : spells) {
            int count = Math.min(missingSpells, Math.max(0, 4 - deck.getMain().countByName(card.getName())));
            deck.getMain().add(card, count); missingSpells -= count;
        }
        List<PaperCard> basics = new ArrayList<>();
        for (String name : entry.basicLands) { PaperCard card = printing(name, universe); if (card != null) basics.add(card); }
        if (basics.isEmpty()) throw new IllegalStateException("No legal basic lands for " + id);
        int fill = Math.max(0, 60 - deck.getMain().countAll());
        for (int i = 0; i < fill; i++) deck.getMain().add(basics.get(i % basics.size()));
        return deck;
    }
}
