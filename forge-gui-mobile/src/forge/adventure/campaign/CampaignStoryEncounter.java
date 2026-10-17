package forge.adventure.campaign;

import forge.adventure.character.EnemySprite;
import forge.adventure.scene.DuelScene;
import forge.adventure.scene.TileMapScene;
import forge.adventure.stage.MapStage;
import forge.adventure.util.Config;
import java.util.ArrayList;
import java.util.List;

/** Audience, challenge and pursuit remain interactions with a figure in an actual location. */
public final class CampaignStoryEncounter {
    private CampaignStoryEncounter() { }
    public static void show(MapStage stage, EnemySprite mob) {
        var config = CampaignConfig.instance(); var deeds = CampaignState.instance().deeds();
        var entry = CampaignStory.encounter(config, mob.campaignStoryEncounter);
        String delivery = "";
        try {
            var reward = CampaignRecognition.deliver(entry.id, forge.adventure.util.Current.player(), deeds,
                    CampaignState.instance().universeFor(config));
            if (reward != null) delivery = "\n\nThe Academy accepts the Chronicler's letter. Received " + DuelScene.describeAll(List.of(reward)) + ".";
        } catch (IllegalArgumentException error) { delivery = "\n\nCommission reward unavailable: " + error.getMessage(); }
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        if (CampaignStory.canChallenge(config, entry.id, deeds)) {
            labels.add(deeds.has("encounter." + entry.id) ? "Duel again (normal ante)" : "Challenge (normal ante)");
            actions.add(() -> { DuelScene.instance().setCampaignStake(null, mob); stage.beginDuel(mob); });
        }
        if (!entry.pursuit.isBlank() && CampaignStory.canChallenge(config, entry.pursuit, deeds)) {
            labels.add("Pursue " + (entry.id.startsWith("urza") ? "Urza" : "Bolas") + " home");
            actions.add(() -> travel(stage, entry.pursuit));
        }
        labels.add("Leave"); actions.add(() -> { mob.freezeMovement(); stage.resetPosition(); });
        stage.showOptionsDialog("[GOLD]" + entry.name + "[WHITE]\n" + CampaignStory.audience(config, entry.id, deeds) + delivery, labels, actions);
    }
    public static void travel(MapStage stage, String id) {
        var config = CampaignConfig.instance(); var deeds = CampaignState.instance().deeds();
        var entry = CampaignStory.encounter(config, id);
        if (CampaignState.instance().expedition().isActive()) {
            stage.showChoiceDialog("Return home before following this path.", "Back", () -> CampaignChronicler.show(stage), null, null); return;
        }
        if (entry.poi.isBlank() && !CampaignStory.canChallenge(config, id, deeds)) {
            stage.showChoiceDialog(entry.refusal, "Back", () -> CampaignChronicler.show(stage), null, null); return;
        }
        if (!new com.badlogic.gdx.files.FileHandle(Config.instance().getFilePath(entry.map)).exists()) {
            stage.showChoiceDialog("This path is unavailable.", "Back", () -> CampaignChronicler.show(stage), null, null); return;
        }
        stage.showChoiceDialog("Explore " + entry.name + "?\nNormal campaign ante and persistent dungeon life apply.",
                "Explore", () -> TileMapScene.instance().loadNext(entry.map, 38), "Stay here", stage::resetPosition);
    }
}
