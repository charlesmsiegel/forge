package forge.adventure.campaign;

import forge.adventure.data.AdventureEventData;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.util.AdventureEventController.EventFormat;
import forge.adventure.util.AdventureEventController.EventStatus;
import forge.deck.CardPool;
import forge.card.CardEdition;
import forge.model.CardBlock;
import forge.model.FModel;
import forge.deck.Deck;
import forge.item.PaperCard;
import java.util.Map;

/** Retaining a paid Limited pool is independent from performance prizes. */
public final class CampaignLimited {
    private CampaignLimited() { }

    public static boolean isCampaignLimited(AdventureEventData event) {
        return CampaignConfig.instance().isActive() && event != null
                && (event.format == EventFormat.Draft || event.format == EventFormat.Sealed);
    }

    public static boolean isEditionAvailable(CardEdition edition) {
        CardUniverse universe = CampaignState.instance().universeFor(CampaignConfig.instance());
        if (universe == null) return true;
        if (!universe.allowsEdition(edition.getCode())) return false;
        for (CardEdition.EditionEntry entry : edition.getObtainableCards())
            if (!universe.allowsGeneratedName(entry.name())) return false;
        return true;
    }

    public static AdventureEventData localEvent(AdventurePlayer player, String sourceID, int objectId) {
        for (AdventureEventData event : player.getEvents())
            if (sourceID.equals(event.sourceID) && event.eventOrigin == objectId
                    && event.eventStatus != EventStatus.Awarded && event.eventStatus != EventStatus.Abandoned) return event;
        return null;
    }

    public static AdventureEventData createOrResume(AdventurePlayer player, CampaignConfig.LimitedOrganizer organizer,
                                                    String sourceID, int objectId, EventFormat format, long epochDay) {
        AdventureEventData existing = localEvent(player, sourceID, objectId);
        if (existing != null) return existing;
        if (organizer == null || organizer.id == null || organizer.id.isBlank() || organizer.name == null
                || organizer.name.isBlank() || (format != EventFormat.Draft && format != EventFormat.Sealed))
            throw new IllegalArgumentException("Invalid historical Limited organizer or format");
        CardBlock block = FModel.getBlocks().get(organizer.block);
        if (block == null || block.getSets().isEmpty()) throw new IllegalArgumentException("Unknown historical Limited block " + organizer.block);
        for (CardEdition edition : block.getSets()) {
            if (!isEditionAvailable(edition)) throw new IllegalArgumentException("Limited set " + edition.getCode() + " is unavailable in this campaign");
            RegionCompletion.boosterTemplate(edition.getCode());
        }
        long seed = epochDay * 31 + ((long) sourceID.hashCode() << 32) + objectId;
        AdventureEventData event = new AdventureEventData(seed, format, block);
        event.sourceID = sourceID; event.eventOrigin = objectId; event.campaignPhantom = organizer.phantom;
        event.description = organizer.name + (organizer.phantom ? " — phantom: cards are not kept" : " — keep the opened/drafted cards regardless of record");
        event.eventRules = new AdventureEventData.AdventureEventRules(format,
                event.style == forge.adventure.util.AdventureEventController.EventStyle.RoundRobin
                        ? AdventureEventData.PairingStyle.RoundRobin : AdventureEventData.PairingStyle.SingleElimination, 1f);
        player.addEvent(event);
        return event;
    }

    public static int claimPool(AdventureEventData event, AdventurePlayer player) {
        if (!isCampaignLimited(event) || event.campaignPoolClaimed || event.campaignPhantom) return 0;
        if (event.eventStatus != EventStatus.Completed && event.eventStatus != EventStatus.Abandoned)
            throw new IllegalStateException("Finish or withdraw from the event before keeping its pool");
        Deck source = event.rewardDeck;
        // An unfinished draft has actual picks but no added virtual basics yet.
        if (source == null && event.format == EventFormat.Draft && !event.isDraftComplete
                && event.eventStatus == EventStatus.Abandoned) source = event.registeredDeck;
        if (source == null) return 0;
        CardPool retained = new CardPool();
        for (Map.Entry<PaperCard, Integer> entry : source.getAllCardsInASinglePool(true, true))
            if (!entry.getKey().hasNoSellValue()) retained.add(entry.getKey(), entry.getValue());
        player.addCards(retained);
        event.campaignPoolClaimed = true;
        CampaignLog.event("limited_pool_retained").with("source", event.sourceID).with("format", event.format.name())
                .with("count", retained.countAll()).withCards("cards", retained.toFlatList()).write();
        return retained.countAll();
    }
}
