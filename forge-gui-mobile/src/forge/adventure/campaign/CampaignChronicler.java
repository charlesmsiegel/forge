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
                List.of("Legendary landmarks", "Recorded deeds", "Leave"),
                List.of(() -> landmarks(stage, 0), () -> deeds(stage, 0), stage::resetPosition));
    }
    private static void landmarks(MapStage stage, int page) {
        var manifest = CampaignState.instance().seededLegends();
        List<SeededLegends.Entry> entries = manifest.entries();
        int current = Math.max(0, Math.min(page, Math.max(0, (entries.size() - 1) / 3)));
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        for (SeededLegends.Entry entry : entries.subList(current * 3, Math.min(entries.size(), current * 3 + 3))) {
            labels.add(entry.card().getName() + (manifest.isClaimed(entry.slot()) ? " — mastered" : ""));
            actions.add(() -> travel(stage, entry));
        }
        if (current > 0) { labels.add("Previous"); actions.add(() -> landmarks(stage, current - 1)); }
        if ((current + 1) * 3 < entries.size()) { labels.add("Next"); actions.add(() -> landmarks(stage, current + 1)); }
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
        List<String> flags = new ArrayList<>(CampaignState.instance().deeds().flags());
        int current = Math.max(0, Math.min(page, Math.max(0, (flags.size() - 1) / 4)));
        String text = flags.isEmpty() ? "Your great deeds are still ahead of you." : String.join("\n", flags.subList(current * 4, Math.min(flags.size(), current * 4 + 4)));
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        if (current > 0) { labels.add("Previous"); actions.add(() -> deeds(stage, current - 1)); }
        if ((current + 1) * 4 < flags.size()) { labels.add("Next"); actions.add(() -> deeds(stage, current + 1)); }
        labels.add("Back"); actions.add(() -> show(stage));
        stage.showOptionsDialog("[GOLD]Recorded deeds[WHITE]\n" + text, labels, actions);
    }
}
