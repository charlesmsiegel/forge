package forge.game.event;

import forge.game.card.CardView;
import forge.game.player.PlayerView;
import java.util.List;

/** A reveal actually presented to one recipient, including brief hidden-zone reveals. */
public record GameEventCardsRevealed(List<CardView> cards, PlayerView viewer) implements GameEvent {
    public GameEventCardsRevealed {
        cards = List.copyOf(cards);
    }

    @Override
    public <T> T visit(IGameEventVisitor<T> visitor) { return visitor.visit(this); }
}
