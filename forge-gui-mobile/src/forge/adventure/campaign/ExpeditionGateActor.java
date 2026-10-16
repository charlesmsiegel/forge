package forge.adventure.campaign;

import forge.adventure.character.PortalActor;
import forge.adventure.scene.TileMapScene;
import forge.adventure.stage.MapStage;
import forge.adventure.util.Current;
import forge.adventure.util.Paths;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiled object type {@code expeditionGate}: a visible portal that starts an expedition
 * ({@code mode=enter}: confirm, restrict the collection to deck + sideboard, teleport into the
 * region's first map) or ends it ({@code mode=retreat}: confirm and leave to the world map;
 * {@link MapStage#exitDungeon} then ends the expedition and resets the region).
 */
public class ExpeditionGateActor extends PortalActor {
    private final String mode;
    private final String region;

    public ExpeditionGateActor(MapStage stage, int id, String targetMap, float x, float y, float w, float h,
                               String direction, String currentMap, int targetObject, String spritePath,
                               String mode, String region) {
        super(stage, id, targetMap, x, y, w, h, direction, currentMap, targetObject, spritePath);
        this.mode = mode == null ? "enter" : mode.toLowerCase();
        this.region = region == null ? "" : region;
        setAnimation("active");
    }

    @Override
    public void onPlayerCollide() {
        MapStage stage = getMapStage();
        CampaignState campaign = CampaignState.instance();
        if ("advance".equals(mode)) {
            continueRealm(stage, campaign);
            return;
        }
        if ("retreat".equals(mode)) {
            if (!campaign.expedition().isActive()) {
                stage.exitDungeon(false, false);
                return;
            }
            stage.showChoiceDialog("[RED]Retreat to Shandalar?[WHITE]\nEverything you own stays yours; "
                            + "local encounters reset to your latest chapter checkpoint and your whole collection becomes available again.",
                    "Retreat", () -> stage.exitDungeon(false, false),
                    "Stay", stage::resetPosition);
            return;
        }
        if (CampaignConfig.instance().hasRealm(region)) {
            showRealmEntry(stage, campaign);
            return;
        }
        if (campaign.expedition().isActive()) {
            if (region.equals(campaign.expedition().getRegionId())) teleport();
            else unavailable(stage, "Return to Shandalar before entering another expedition.");
            return;
        }
        String display = region.isEmpty() ? "the expedition" : Character.toUpperCase(region.charAt(0)) + region.substring(1);
        stage.showChoiceDialog("[GOLD]Enter " + display + "?[WHITE]\nOnly your current deck and sideboard ("
                        + Current.player().getSelectedDeck().getName() + ") come with you. Cards at home stay "
                        + "unavailable until you return. Cards you win or buy inside are yours to keep.",
                "Enter", () -> {
                    String rootId = TileMapScene.instance().rootPoint == null ? "" : TileMapScene.instance().rootPoint.getID();
                    campaign.enterExpedition(region, Current.player(), rootId);
                    teleport();
                },
                "Not now", stage::resetPosition);
    }

    private void showRealmEntry(MapStage stage, CampaignState campaign) {
        try {
            HistoricalRealm realm = CampaignConfig.instance().realm(region);
            if (campaign.expedition().isActive()) {
                unavailable(stage, "Return to Shandalar before choosing a new expedition route.");
                return;
            }
            RealmProgress progress = campaign.realmProgress(region);
            String checkpoint = realm.chapter(progress.checkpoint(realm)).name;
            List<String> labels = new ArrayList<>();
            List<Runnable> actions = new ArrayList<>();
            labels.add("Continue: " + checkpoint);
            actions.add(() -> enterRealm(stage, campaign, null));
            if (progress.isBlockComplete()) {
                labels.add("Replay the whole block");
                actions.add(() -> enterRealm(stage, campaign, "*"));
                for (HistoricalRealm.Chapter chapter : realm.chapters) {
                    labels.add("Replay " + chapter.name);
                    actions.add(() -> enterRealm(stage, campaign, chapter.id));
                }
            }
            labels.add("Stay in Shandalar");
            actions.add(stage::resetPosition);
            stage.showOptionsDialog("[GOLD]" + realm.name + "[WHITE]\nOnly your deck and sideboard come with you. "
                    + "Opponents and rewards follow each set; your cards remain unrestricted. "
                    + "Retreat keeps your haul and the latest set checkpoint.", labels, actions);
        } catch (IllegalArgumentException | IllegalStateException e) {
            unavailable(stage, e.getMessage());
        }
    }

    private void enterRealm(MapStage stage, CampaignState campaign, String selection) {
        try {
            String rootId = TileMapScene.instance().rootPoint == null ? "" : TileMapScene.instance().rootPoint.getID();
            HistoricalRealm.Chapter chapter = campaign.enterRealm(region, selection, Current.player(), rootId);
            teleport(chapter);
        } catch (IllegalArgumentException | IllegalStateException e) {
            unavailable(stage, "Cannot enter this expedition: " + e.getMessage());
        }
    }

    private void continueRealm(MapStage stage, CampaignState campaign) {
        if (!campaign.expedition().isActive() || !region.equals(campaign.expedition().getRegionId())) {
            unavailable(stage, "This passage belongs to another expedition.");
            return;
        }
        try {
            HistoricalRealm realm = CampaignConfig.instance().realm(region);
            RealmProgress progress = campaign.realmProgress(region);
            if (progress.isRunComplete()) {
                stage.showChoiceDialog("[GOLD]Expedition complete![WHITE]\nReturn with your cards, gold and unopened packs?",
                        "Return to Shandalar", () -> stage.exitDungeon(false, false), "Stay", stage::resetPosition);
            } else if (progress.canAdvance()) {
                HistoricalRealm.Chapter next = progress.nextChapter(realm);
                stage.showChoiceDialog("[GOLD]Checkpoint secured.[WHITE]\nContinue to " + next.name
                                + " with your current expedition cards and life?",
                        "Continue", () -> teleport(campaign.advanceRealm(region)), "Stay", stage::resetPosition);
            } else unavailable(stage, "Defeat this chapter's final opponent to open the next passage.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            unavailable(stage, e.getMessage());
        }
    }

    private static void unavailable(MapStage stage, String message) {
        stage.showChoiceDialog(message, "OK", stage::resetPosition, null, null);
    }

    private void teleport(HistoricalRealm.Chapter chapter) {
        TileMapScene.instance().loadNext(chapter.entryMap, chapter.entryObject);
        getMapStage().getPlayerSprite().playEffect(Paths.EFFECT_TELEPORT, 0.5f);
    }

    private void teleport() {
        MapStage stage = getMapStage();
        if (targetMap == null || targetMap.isEmpty()) {
            stage.exitDungeon(false, false);
            return;
        }
        TileMapScene.instance().loadNext(targetMap, entryTargetObject);
        stage.getPlayerSprite().playEffect(Paths.EFFECT_TELEPORT, 0.5f);
    }
}
