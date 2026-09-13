package forge.adventure.campaign;

import forge.adventure.player.AdventurePlayer;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;

/**
 * Physical card ownership helpers for the Shandalar Reborn campaign.
 * <p>
 * A "physical copy" is one unit of count of an exact {@link PaperCard} printing (name, edition,
 * collector number, art index, foil, flags) inside a {@link CardPool}. Two copies of the same
 * printing are two units; different printings of the same card name are different keys.
 * This deliberately reuses Forge's collection model instead of introducing a parallel
 * per-instance card object (see docs/implementation-plan.md, decision 1).
 */
public final class CardOwnership {
    private CardOwnership() {
    }

    /**
     * Removes exactly one copy of the exact printing from the player's permanent collection.
     * Any deck that would then reference more copies than the player owns is trimmed
     * (sideboard and other extra sections first, then the main deck), mirroring what Forge
     * Adventure does for ante losses but without the basic-land exemption.
     *
     * @return false if the player owns no copy of that printing (nothing changes).
     */
    public static boolean removeOne(AdventurePlayer player, PaperCard card) {
        CardPool collection = player.getCards();
        int owned = collection.count(card);
        if (owned < 1)
            return false;
        int remaining = owned - 1;
        for (int i = 0; i < player.getDeckCount(); i++) {
            trimDeck(player.getDeck(i), card, remaining);
        }
        return collection.remove(card, 1);
    }

    /** Adds one copy of the exact printing to the player's permanent collection. */
    public static void addOne(AdventurePlayer player, PaperCard card) {
        player.addCard(card, 1);
    }

    /**
     * Moves exactly one copy of the exact printing from one pool to another.
     *
     * @return false if the source pool holds no copy (nothing changes).
     */
    public static boolean transferOne(CardPool from, CardPool to, PaperCard card) {
        if (from.count(card) < 1)
            return false;
        if (!from.remove(card, 1))
            return false;
        to.add(card, 1);
        return true;
    }

    /** Drops copies of {@code card} from the deck until it references at most {@code allowed}. */
    static void trimDeck(Deck deck, PaperCard card, int allowed) {
        int inDeck = deck.count(card);
        int excess = inDeck - allowed;
        if (excess <= 0)
            return;
        for (DeckSection section : DeckSection.values()) {
            if (section == DeckSection.Main || deck.get(section) == null)
                continue;
            int inSection = deck.get(section).count(card);
            int fromSection = Math.min(inSection, excess);
            if (fromSection > 0) {
                deck.get(section).remove(card, fromSection);
                excess -= fromSection;
                if (excess <= 0)
                    return;
            }
        }
        deck.getMain().remove(card, excess);
    }
}
