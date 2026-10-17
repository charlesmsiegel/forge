package forge.adventure.campaign;

import forge.adventure.player.AdventurePlayer;
import forge.item.PaperCard;
import java.util.ArrayList;
import java.util.List;

/** Readable reactions and a small deed-gated commission; no reputation score. */
public final class CampaignRecognition {
    private CampaignRecognition() { }
    public static List<String> rumors(WorldDeeds deeds) {
        List<String> lines = new ArrayList<>();
        lines.add(deeds.has("shandalar.champion") ? "The five castles name you Champion. The Chronicler trusts you with an Academy commission."
                : "Five castle guardians hold the seal. Each victory is remembered; the Chronicler knows their roads.");
        lines.add(deeds.has("seal.broken") ? "The seal is broken. Legendary challengers recognize a planeswalker's deeds."
                : "The sealed chamber waits for a champion. You may seek an audience before a legend will fight you.");
        if (deeds.has("realm.fallen-empires.completed")) lines.add("Sarpadia's chroniclers remember your victory. The Collector has opened a Sarpadian archive exchange.");
        if (deeds.has("realm.mirrodin.completed")) lines.add("Word of your Mirrodin mastery travels through the metal lands.");
        if (deeds.has("legend.urza.conquered")) lines.add("Urza's home has fallen. Bolas has heard the news and dismisses it with unusual haste.");
        if (deeds.has("legend.bolas.conquered")) lines.add("Bolas insists that your victory was an experiment. The towns tell a different story.");
        return List.copyOf(lines);
    }
    public static boolean commissionAvailable(WorldDeeds deeds) {
        return deeds.has("shandalar.champion") && !deeds.has("commission.academy.accepted") && !deeds.has("commission.academy.completed");
    }
    public static void acceptCommission(WorldDeeds deeds) {
        if (!commissionAvailable(deeds)) throw new IllegalArgumentException("The Academy commission is not available");
        deeds.grant("commission.academy.accepted");
    }
    public static PaperCard deliver(String audienceId, AdventurePlayer player, WorldDeeds deeds, CardUniverse universe) {
        if (!audienceId.startsWith("urza-") || !deeds.has("commission.academy.accepted") || deeds.has("commission.academy.completed")) return null;
        PaperCard reward = CampaignTraining.chooseReward(new String[]{"Counterspell", "Mana Leak", "Divination"}, "Blue", universe);
        player.addCard(reward); deeds.grant("commission.academy.completed"); return reward;
    }
}
