package forge.adventure.campaign;

import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The cards each side puts up before a campaign duel. Exact printings; order is irrelevant.
 */
public class AnteStake {
    public enum Kind { NORMAL, RECLAMATION }

    public final Kind kind;
    public final List<PaperCard> playerCards;
    public final List<PaperCard> opponentCards;
    public final List<String> playerIds;
    public final List<String> opponentIds;

    public AnteStake(Kind kind, List<PaperCard> playerCards, List<PaperCard> opponentCards) {
        this(kind, playerCards, opponentCards, List.of(), List.of());
    }

    public AnteStake(Kind kind, List<PaperCard> playerCards, List<PaperCard> opponentCards,
                     List<String> playerIds, List<String> opponentIds) {
        this.kind = kind;
        this.playerCards = Collections.unmodifiableList(new ArrayList<>(playerCards));
        this.opponentCards = Collections.unmodifiableList(new ArrayList<>(opponentCards));
        this.playerIds = List.copyOf(playerIds);
        this.opponentIds = List.copyOf(opponentIds);
    }

    public boolean isEmpty() {
        return playerCards.isEmpty() && opponentCards.isEmpty();
    }

    @Override
    public String toString() {
        return kind + " player=" + playerCards + " opponent=" + opponentCards;
    }
}
