package forge.adventure.campaign;

import forge.adventure.player.AdventurePlayer;
import forge.adventure.util.SaveFileContent;
import forge.adventure.util.SaveFileData;
import forge.deck.CardPool;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** A small set of earned campaign exceptions, separate from Forge's card rules. */
public final class CampaignProgress implements SaveFileContent {
    private final Map<String, Integer> fullClears = new HashMap<>();
    private final Set<String> awarded = new HashSet<>();
    private final Set<String> protectedIds = new HashSet<>();
    private final Set<String> restrictedChoices = new HashSet<>();
    private final Map<String, Integer> exemptions = new HashMap<>();
    private int ordinaryCopyLimit;
    private int protectionSlots;
    private int restrictedChoiceTokens;
    private boolean masterCollector;

    public static void validateRules(CampaignConfig.ProgressionRules rules) {
        if (rules == null || rules.milestones == null) throw new IllegalArgumentException("Missing campaign progression rules");
        Set<String> ids = new HashSet<>();
        for (CampaignConfig.Milestone milestone : rules.milestones)
            if (milestone == null || milestone.id == null || milestone.id.isBlank() || !ids.add(milestone.id) || milestone.fullClears < 1
                    || milestone.realm == null || milestone.realm.isBlank() || milestone.ordinaryCopyLimit < 0
                    || milestone.maxLifeBonus < 0 || milestone.protectionSlots < 0 || milestone.restrictedChoiceTokens < 0
                    || (milestone.exemptCard != null && !milestone.exemptCard.isBlank() && milestone.exemptCopyLimit < 1))
                throw new IllegalArgumentException("Invalid campaign milestone");
    }

    public void onFullClear(String realmId, AdventurePlayer player, CampaignConfig.ProgressionRules rules) {
        validateRules(rules);
        int count = fullClears.merge(realmId, 1, Integer::sum);
        for (CampaignConfig.Milestone milestone : rules.milestones) {
            if (!realmId.equals(milestone.realm) || count < milestone.fullClears || !awarded.add(milestone.id)) continue;
            ordinaryCopyLimit = Math.max(ordinaryCopyLimit, milestone.ordinaryCopyLimit);
            protectionSlots = Math.max(protectionSlots, milestone.protectionSlots);
            restrictedChoiceTokens += milestone.restrictedChoiceTokens;
            masterCollector |= milestone.masterCollector;
            if (milestone.maxLifeBonus > 0) player.addMaxLife(milestone.maxLifeBonus);
            if (milestone.exemptCard != null && !milestone.exemptCard.isBlank() && milestone.exemptCopyLimit > 0)
                grantCopyExemption(milestone.exemptCard, milestone.exemptCopyLimit);
            CampaignLog.event("campaign_milestone").with("id", milestone.id).with("realm", realmId).with("fullClears", count).write();
        }
    }

    public int fullClears(String realmId) { return fullClears.getOrDefault(realmId, 0); }
    public int ordinaryLimit(int base) { return Math.max(base, ordinaryCopyLimit); }
    public int protectionSlots() { return protectionSlots; }
    public int restrictedChoiceTokens() { return restrictedChoiceTokens; }
    public boolean isMasterCollector() { return masterCollector; }

    public void protect(OwnedCard copy, java.util.List<OwnedCard> available) {
        if (copy == null || !available.contains(copy)) throw new IllegalArgumentException("That physical copy is unavailable");
        if (protectedIds.contains(copy.id)) return;
        if (protectedIds.size() >= protectionSlots) throw new IllegalArgumentException("No free protection slot");
        protectedIds.add(copy.id);
    }

    public void unprotect(String id) { protectedIds.remove(id); }
    public boolean isProtected(String id) { return protectedIds.contains(id); }
    public Set<String> protectedIds() { return Set.copyOf(protectedIds); }

    public int copyLimit(String cardName, int base) {
        return Math.max(exemptions.getOrDefault(cardName, 0), base + (restrictedChoices.contains(cardName) ? 1 : 0));
    }

    public void grantCopyExemption(String cardName, int limit) {
        if (cardName == null || cardName.isBlank() || limit < 1) throw new IllegalArgumentException("Invalid copy exemption");
        exemptions.merge(cardName, limit, Math::max);
    }

    public void chooseRestrictedCopy(String cardName, CampaignConfig config, CardPool owned) {
        if (restrictedChoiceTokens < 1 || !config.cards.isRestricted(cardName) || owned.countByName(cardName) < 1
                || restrictedChoices.contains(cardName))
            throw new IllegalArgumentException("Choose an owned restricted card using an earned choice token");
        restrictedChoices.add(cardName);
        restrictedChoiceTokens--;
    }

    @Override
    public SaveFileData save() {
        SaveFileData data = new SaveFileData();
        data.store("version", 1);
        data.store("ordinaryLimit", ordinaryCopyLimit);
        data.store("protectionSlots", protectionSlots);
        data.store("restrictedTokens", restrictedChoiceTokens);
        data.store("masterCollector", masterCollector);
        data.storeObject("protectedIds", protectedIds.toArray(new String[0]));
        data.storeObject("awarded", awarded.toArray(new String[0]));
        data.storeObject("restrictedChoices", restrictedChoices.toArray(new String[0]));
        storeCounts(data, "fullClears", fullClears);
        storeCounts(data, "exemptions", exemptions);
        return data;
    }

    @Override
    public void load(SaveFileData data) {
        ordinaryCopyLimit = data == null ? 0 : data.readInt("ordinaryLimit");
        protectionSlots = data == null ? 0 : data.readInt("protectionSlots");
        restrictedChoiceTokens = data == null ? 0 : data.readInt("restrictedTokens");
        masterCollector = data != null && data.readBool("masterCollector");
        protectedIds.clear(); awarded.clear(); restrictedChoices.clear(); fullClears.clear(); exemptions.clear();
        if (data == null) return;
        if (data.containsKey("protectedIds")) protectedIds.addAll(Arrays.asList((String[]) data.readObject("protectedIds")));
        if (data.containsKey("awarded")) awarded.addAll(Arrays.asList((String[]) data.readObject("awarded")));
        if (data.containsKey("restrictedChoices")) restrictedChoices.addAll(Arrays.asList((String[]) data.readObject("restrictedChoices")));
        loadCounts(data, "fullClears", fullClears);
        loadCounts(data, "exemptions", exemptions);
    }

    private static void storeCounts(SaveFileData data, String prefix, Map<String, Integer> counts) {
        data.storeObject(prefix + "Keys", counts.keySet().toArray(new String[0]));
        data.storeObject(prefix + "Values", counts.values().toArray(new Integer[0]));
    }

    private static void loadCounts(SaveFileData data, String prefix, Map<String, Integer> counts) {
        if (!data.containsKey(prefix + "Keys")) return;
        String[] keys = (String[]) data.readObject(prefix + "Keys");
        Integer[] values = (Integer[]) data.readObject(prefix + "Values");
        for (int i = 0; i < keys.length && i < values.length; i++) counts.put(keys[i], values[i]);
    }
}
