package forge.adventure.campaign;

import forge.item.PaperCard;
import java.util.Objects;
import java.util.UUID;

/** Campaign metadata for one unit in Forge's authoritative CardPool. */
public final class OwnedCard {
    public final String id;
    public final PaperCard card;

    public OwnedCard(String id, PaperCard card) {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("Missing physical card ID");
        this.id = id;
        this.card = Objects.requireNonNull(card);
    }

    public static OwnedCard mint(PaperCard card) { return new OwnedCard(UUID.randomUUID().toString(), card); }

    @Override public boolean equals(Object other) {
        return other instanceof OwnedCard && id.equals(((OwnedCard) other).id) && card.equals(((OwnedCard) other).card);
    }
    @Override public int hashCode() { return Objects.hash(id, card); }
}
