package forge.adventure.campaign;

import java.util.ArrayList;
import java.util.List;

/** Read-only presentation projection; browsing does not create campaign progress. */
public final class CampaignJournal {
    public record Section(String title, String text) { }
    private CampaignJournal() { }
    public static List<String> accomplishments(CampaignConfig config, CampaignState state) {
        java.util.Set<String> names = new java.util.TreeSet<>(); var deeds = state.deeds();
        for (var encounter : config.story.encounters) if (deeds.has("encounter." + encounter.id)) names.add("Defeated " + encounter.name);
        for (var realm : config.realms) if (deeds.has("realm." + realm.id + ".completed")) names.add("Completed " + realm.name);
        for (var entry : state.seededLegends().entries()) if (state.seededLegends().isClaimed(entry.slot())) names.add("Mastered " + entry.card().getName());
        if (deeds.has("shandalar.champion")) names.add("Champion of Shandalar");
        if (deeds.has("seal.broken")) names.add("Broke Shandalar's seal — Planeswalker");
        for (var path : config.training) if (deeds.has("training.completed." + path.id)) names.add("Completed " + path.name);
        if (deeds.has("commission.academy.completed")) names.add("Delivered the Chronicler’s Academy letter");
        return List.copyOf(names);
    }
    public static List<Section> sections(CampaignConfig config, CampaignState state) {
        List<Section> result = new ArrayList<>(); var deeds = state.deeds();
        StringBuilder story = new StringBuilder(CampaignStory.title(deeds));
        story.append("\n\n").append(deeds.has("seal.broken") ? "The seal is broken. Seek the legendary figures and follow their challenges home."
                : deeds.has("shandalar.champion") ? "The five guardians have fallen. Confront the seal warden."
                : "Defeat the five color guardians, then confront the keeper of Shandalar's seal.");
        for (var encounter : config.story.encounters) story.append("\n\n").append(encounter.name).append("\n")
                .append((deeds.has("encounter." + encounter.id) ? "Complete" : CampaignStory.canChallenge(config, encounter.id, deeds) ? "Ready to challenge" : encounter.refusal));
        result.add(new Section("Shandalar", story.toString()));
        StringBuilder realms = new StringBuilder("Historical realms offer set-specific cards, saved checkpoints and full-block mastery.");
        for (var realm : config.realms) {
            RealmProgress progress = state.realmProgressSnapshot(realm.id);
            realms.append("\n\n").append(realm.name).append("\nCheckpoint: ").append(realm.chapter(progress.checkpoint(realm)).name)
                    .append("\nFull clears: ").append(state.progression().fullClears(realm.id));
            var snapshot = state.universeFor(config);
            boolean available = snapshot == null || java.util.Arrays.stream(realm.chapters)
                    .flatMap(chapter -> java.util.Arrays.stream(chapter.editions)).allMatch(snapshot::allowsEdition);
            if (!available) realms.append("\nUnavailable in this campaign's card universe.");
        }
        if (state.expedition().isActive()) realms.append("\n\nExpedition underway. Only carried cards are available. Return home to bank your acquisitions.");
        else realms.append("\n\nAt home. Your full collection is available.");
        result.add(new Section("Historical realms", realms.toString()));
        StringBuilder landmarks = new StringBuilder("These landmarks were chosen for this world. Ask the town Chronicler for directions.");
        for (var entry : state.seededLegends().entries()) landmarks.append("\n\n").append(entry.card().getName()).append(" • ")
                .append(entry.card().getEdition()).append("\n").append(state.seededLegends().isClaimed(entry.slot()) ? "Mastered — named reward received" : "Unmastered — named reward awaits");
        result.add(new Section("Legendary landmarks", landmarks.toString()));
        StringBuilder training = new StringBuilder("Optional Wayfarer training is available through the town Chronicler. Skip or resume at any time; normal ante and life rules apply.");
        for (var path : config.training) training.append("\n\n").append(path.name).append(": ").append(CampaignTraining.completed(path,deeds)).append("/3 lessons complete");
        result.add(new Section("Optional training", training.toString()));
        var progress = state.progression();
        result.add(new Section("Your abilities", "Ordinary copies per card: " + progress.ordinaryLimit(config.player.ordinaryCopyLimit)
                + "\nRestricted copies: " + config.player.restrictedCopyLimit + " (earned exceptions apply)"
                + "\nProtection slots: " + progress.protectionSlots() + "\nRestricted-copy choices available: " + progress.restrictedChoiceTokens()
                + "\nMaster Collector: " + (progress.isMasterCollector() ? "Unlocked — ordinary limits apply per printing" : "Not yet unlocked")
                + "\n\nComplete full historical blocks to earn new abilities. Visit the town Collector to assign protection and restricted choices."));
        var universe = state.universeFor(config);
        result.add(new Section("Card universe", universe == null ? "No campaign snapshot available." : universe.summary()
                + "\n\nThis universe is frozen for this campaign. Later format rotation and configuration changes do not replace it. Owned reprints follow card-name legality. Authored rewards may bypass ordinary generation exclusions, but never actual bans."));
        result.add(new Section("Campaign balance", "Initial life: " + config.player.startingLife
                + " (existing earned/saved life is retained)\nOpponent life multiplier: " + config.difficulty.enemyLifeMultiplier
                + "\nAI profile: " + (config.difficulty.aiProfile.isEmpty() ? "Opponent/default profile" : config.difficulty.aiProfile)
                + "\nOrdinary deck tier floor: " + config.difficulty.ordinaryTierFloor
                + "\nRival wins per tier: " + config.rivals.winsPerTier + " • upgrade slots/tier: " + config.rivals.rarityUpgradeSlotsPerTier
                + "\nGold loot multiplier: " + config.economy.goldRewardMultiplier
                + "\nSale / card shop / pack multipliers: " + config.economy.cardSellMultiplier + " / " + config.economy.shopPriceMultiplier + " / " + config.economy.boosterPriceMultiplier
                + "\nAnte: " + (config.ante.enabled ? config.ante.playerAnteCount + " vs " + config.ante.opponentAnteCount + "; reclamation risks " + config.ante.reclamationRiskCount : "Disabled")
                + "\n\nPlane balance controls are independent. Historical and legendary recipes keep their identities. Purchases cost more than their immediate resale value after discounts. Exchange rewards cannot be sold or exchanged again, but remain usable in decks and ante."));
        List<String> deedsList = accomplishments(config, state);
        result.add(new Section("Remembered deeds", deedsList.isEmpty() ? "Your great deeds are still ahead of you." : String.join("\n\n", deedsList)));
        return List.copyOf(result);
    }
}
