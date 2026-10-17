package forge.adventure.campaign;

import forge.adventure.scene.TileMapScene;
import forge.adventure.stage.MapStage;
import forge.adventure.util.Config;
import java.util.ArrayList;
import java.util.List;

/** A functional in-world route to deed recognition and newly added landmarks in legacy worlds. */
public final class CampaignChronicler {
    private CampaignChronicler() { }
    public static void show(MapStage stage) {
        stage.showOptionsDialog("[GOLD]Chronicler of Shandalar[WHITE]\nYour deeds are remembered. Old paths lead to the legendary places bound to this world's seed.",
                List.of("Historical gateways", "Legendary landmarks", "Guardians and legends", "Training and town news", "Leave"),
                List.of(() -> crossroads(stage), () -> landmarks(stage, 0), () -> story(stage, 0), () -> townNews(stage), stage::resetPosition));
    }
    private static void townNews(MapStage stage) {
        stage.showOptionsDialog("[GOLD]Training and town news[WHITE]\nOptional lessons, rumors and the record of your deeds.",
                List.of("Optional training", "Rumors and commissions", "Recorded deeds", "Back"),
                List.of(() -> CampaignTrainingEncounter.paths(stage, 0), () -> rumors(stage), () -> deeds(stage, 0), () -> show(stage)));
    }
    private static void rumors(MapStage stage) {
        var deeds = CampaignState.instance().deeds();
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        if (CampaignRecognition.commissionAvailable(deeds)) {
            labels.add("Accept Academy letter"); actions.add(() -> {
                CampaignRecognition.acceptCommission(deeds); rumors(stage);
            });
        }
        labels.add("Back");actions.add(() -> show(stage));
        String commission = deeds.has("commission.academy.completed") ? "\n\nThe Academy received your letter."
                : deeds.has("commission.academy.accepted") ? "\n\nDeliver the Chronicler's letter by speaking to Urza at the Academy. You need not defeat him. A useful blue card awaits."
                : CampaignRecognition.commissionAvailable(deeds) ? "\n\nCommission: carry my letter to Urza's Academy. Speak to him for a useful blue card." : "";
        stage.showOptionsDialog("[GOLD]Word across Shandalar[WHITE]\n" + String.join("\n\n", CampaignRecognition.rumors(deeds)) + commission,labels,actions);
    }
    private static void crossroads(MapStage stage) {
        if (CampaignState.instance().expedition().isActive()) {
            stage.showChoiceDialog("Return home before choosing another expedition.", "Back", () -> show(stage), null, null); return;
        }
        stage.showChoiceDialog("Visit the Historical Crossroads? Choose Sarpadia, Rath or Mirrodin there. Each gateway checks this campaign's frozen card universe before entry.",
                "Visit crossroads", () -> TileMapScene.instance().loadNext("maps/map/realm_crossroads.tmx", 38), "Stay here", () -> show(stage));
    }
    private static void story(MapStage stage, int page) {
        var config = CampaignConfig.instance(); var deeds = CampaignState.instance().deeds();
        var entries = java.util.Arrays.stream(config.story.encounters)
                .filter(entry -> !entry.poi.isBlank() || CampaignStory.canChallenge(config, entry.id, deeds)).toList();
        int current = Math.max(0, Math.min(page, Math.max(0, (entries.size() - 1) / 2)));
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        for (var entry : entries.subList(current * 2, Math.min(entries.size(), current * 2 + 2))) {
            labels.add(entry.name + (deeds.has("encounter." + entry.id) ? " — defeated" : ""));
            actions.add(() -> CampaignStoryEncounter.travel(stage, entry.id));
        }
        if (current > 0) { labels.add("Previous"); actions.add(() -> story(stage, current - 1)); }
        if ((current + 1) * 2 < entries.size()) { labels.add("Next"); actions.add(() -> story(stage, current + 1)); }
        labels.add("Back"); actions.add(() -> show(stage));
        stage.showOptionsDialog("[GOLD]" + CampaignStory.title(deeds) + "[WHITE]\nFive guardians hold the paths to the seal. Legendary figures may grant an audience before accepting a challenge.", labels, actions);
    }
    private static void landmarks(MapStage stage, int page) {
        var manifest = CampaignState.instance().seededLegends();
        List<SeededLegends.Entry> entries = manifest.entries();
        int current = Math.max(0, Math.min(page, Math.max(0, (entries.size() - 1) / 2)));
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        for (SeededLegends.Entry entry : entries.subList(current * 2, Math.min(entries.size(), current * 2 + 2))) {
            labels.add(entry.card().getName() + (manifest.isClaimed(entry.slot()) ? " — mastered" : ""));
            actions.add(() -> travel(stage, entry));
        }
        if (current > 0) { labels.add("Previous"); actions.add(() -> landmarks(stage, current - 1)); }
        if ((current + 1) * 2 < entries.size()) { labels.add("Next"); actions.add(() -> landmarks(stage, current + 1)); }
        labels.add("Back"); actions.add(() -> show(stage));
        stage.showOptionsDialog(entries.isEmpty() ? "No eligible legendary landmarks were selected in this campaign's card universe."
                : "These are actual places and opponents. Defeat the guardian to receive its named physical card once. Linked Urza lands each have their own trial.", labels, actions);
    }
    private static void travel(MapStage stage, SeededLegends.Entry entry) {
        if (CampaignState.instance().expedition().isActive()) {
            stage.showChoiceDialog("Return home before exploring another landmark.", "Back", () -> show(stage), null, null); return;
        }
        CampaignConfig.LandmarkRoute selected = null;
        for (CampaignConfig.LandmarkRoute route : CampaignConfig.instance().seeds.routes)
            if (route.slot.equals(entry.slot())) {
                if (selected != null) throw new IllegalArgumentException("Duplicate landmark route " + entry.slot());
                selected = route;
            }
        if (selected == null || !new com.badlogic.gdx.files.FileHandle(Config.instance().getFilePath(selected.map)).exists()) {
            stage.showChoiceDialog("This landmark route is unavailable.", "Back", () -> show(stage), null, null); return;
        }
        String target = selected.map;
        stage.showChoiceDialog("Explore " + entry.card().getName() + "?\nNormal campaign ante and dungeon life apply. Retreat returns you to Shandalar.",
                "Explore", () -> TileMapScene.instance().loadNext(target, 38), "Stay here", () -> landmarks(stage, 0));
    }
    private static void deeds(MapStage stage, int page) {
        List<String> flags = CampaignJournal.accomplishments(CampaignConfig.instance(), CampaignState.instance());
        int current = Math.max(0, Math.min(page, Math.max(0, (flags.size() - 1) / 4)));
        String text = flags.isEmpty() ? "Your great deeds are still ahead of you." : String.join("\n", flags.subList(current * 4, Math.min(flags.size(), current * 4 + 4)));
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        if (current > 0) { labels.add("Previous"); actions.add(() -> deeds(stage, current - 1)); }
        if ((current + 1) * 4 < flags.size()) { labels.add("Next"); actions.add(() -> deeds(stage, current + 1)); }
        labels.add("Back"); actions.add(() -> show(stage));
        stage.showOptionsDialog("[GOLD]Recorded deeds[WHITE]\n" + text, labels, actions);
    }
}
