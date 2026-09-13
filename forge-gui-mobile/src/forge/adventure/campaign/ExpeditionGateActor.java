package forge.adventure.campaign;

import forge.adventure.character.PortalActor;
import forge.adventure.scene.TileMapScene;
import forge.adventure.stage.MapStage;
import forge.adventure.util.Current;
import forge.adventure.util.Paths;

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
        if ("retreat".equals(mode)) {
            if (!campaign.expedition().isActive()) {
                stage.exitDungeon(false, false);
                return;
            }
            stage.showChoiceDialog("[RED]Retreat to Shandalar?[WHITE]\nEverything you own stays yours; "
                            + "your progress here resets and the whole collection becomes available again.",
                    "Retreat", () -> stage.exitDungeon(false, false),
                    "Stay", stage::resetPosition);
            return;
        }
        if (campaign.expedition().isActive()) {
            teleport();
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
