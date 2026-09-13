package forge.adventure.campaign;

import forge.adventure.player.AdventurePlayer;
import forge.deck.CardPool;
import forge.item.PaperCard;

import java.util.Map;

/**
 * Card-aware dialog conditions and actions (MVP.md 29 "card request" quests): a dialog may ask
 * whether the player carries a card by name and take exactly one copy of it.
 */
public final class CampaignDialogs {
    private CampaignDialogs() {
    }

    /** True if the player can currently use at least one copy of the named card (any printing). */
    public static boolean hasCard(AdventurePlayer player, String cardName) {
        return findCopy(player, cardName) != null;
    }

    /** The first usable printing of the named card, or null. */
    public static PaperCard findCopy(AdventurePlayer player, String cardName) {
        if (cardName == null || cardName.isEmpty())
            return null;
        CardPool available = CampaignState.instance().availableCards(player);
        for (Map.Entry<PaperCard, Integer> e : available) {
            if (e.getValue() > 0 && e.getKey().getName().equalsIgnoreCase(cardName))
                return e.getKey();
        }
        return null;
    }

    /**
     * Takes exactly one usable copy of the named card from the player (deck trimmed if needed).
     *
     * @return the printing that was taken, or null if the player had none
     */
    public static PaperCard removeCardByName(AdventurePlayer player, String cardName) {
        PaperCard copy = findCopy(player, cardName);
        if (copy == null)
            return null;
        if (!CardOwnership.removeOne(player, copy))
            return null;
        CampaignLog.event("card_given").with("card", copy).write();
        return copy;
    }
}
