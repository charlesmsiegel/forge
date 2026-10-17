package forge.adventure.campaign;

import forge.Forge;
import forge.adventure.data.AdventureEventData;
import forge.adventure.scene.EventScene;
import forge.adventure.scene.TileMapScene;
import forge.adventure.stage.MapStage;
import forge.adventure.util.AdventureEventController.EventFormat;
import forge.adventure.util.AdventureEventController.EventStatus;
import forge.adventure.util.Current;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** In-world organizers reuse Adventure's complete Limited event presentation. */
public final class CampaignLimitedOrganizer {
    private CampaignLimitedOrganizer() { }

    public static void show(MapStage stage, String organizerId, String map, int objectId) {
        try {
            CampaignConfig.LimitedOrganizer organizer = CampaignConfig.instance().limitedOrganizer(organizerId);
            String point = TileMapScene.instance().rootPoint == null ? "world" : TileMapScene.instance().rootPoint.getID();
            String source = point + "|" + map + "|" + organizer.id;
            AdventureEventData existing = CampaignLimited.localEvent(Current.player(), source, objectId);
            List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
            if (existing != null) {
                labels.add("Continue " + existing.format); actions.add(() -> open(stage, existing));
                if (existing.eventStatus != EventStatus.Completed) {
                    labels.add(existing.eventStatus == EventStatus.Available ? "Cancel this offer" : "Withdraw from this event");
                    actions.add(() -> stage.showChoiceDialog("Withdraw? Performance prizes are forfeited. "
                                    + (existing.campaignPhantom ? "This is a phantom event; cards are not kept." : "Keep only cards already picked or opened; event-added basic lands are not kept."),
                            "Withdraw", () -> {
                                if (existing.eventStatus != EventStatus.Available) {
                                    existing.eventStatus = EventStatus.Abandoned;
                                    CampaignLimited.claimPool(existing, Current.player());
                                }
                                Current.player().removeEvent(existing);
                                stage.resetPosition();
                            }, "Continue event", () -> show(stage, organizerId, map, objectId)));
                }
            } else {
                for (EventFormat format : new EventFormat[]{EventFormat.Draft, EventFormat.Sealed}) {
                    labels.add(organizer.block + " " + format); actions.add(() -> {
                        try {
                            AdventureEventData event = CampaignLimited.createOrResume(Current.player(), organizer, source, objectId, format, LocalDate.now().toEpochDay());
                            open(stage, event);
                        } catch (IllegalArgumentException | IllegalStateException e) { unavailable(stage, e.getMessage()); }
                    });
                }
            }
            labels.add("Leave"); actions.add(stage::resetPosition);
            stage.showOptionsDialog("[GOLD]" + organizer.name + "[WHITE]\n"
                    + (organizer.phantom ? "Phantom Limited: cards are not kept." : "Keep drafted or opened cards regardless of your record.")
                    + "\nLimited uses its own deck rules and 20 starting life. Entry prices and event details follow.", labels, actions);
        } catch (IllegalArgumentException | IllegalStateException e) { unavailable(stage, e.getMessage()); }
    }

    private static void open(MapStage stage, AdventureEventData event) {
        if (event.participants == null) event.generateParticipants();
        Forge.switchScene(EventScene.instance(TileMapScene.instance(), event, stage.getChanges()), true);
    }

    private static void unavailable(MapStage stage, String message) {
        stage.showChoiceDialog(message, "Back", stage::resetPosition, null, null);
    }
}
