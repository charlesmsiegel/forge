package forge.adventure.campaign;

import com.google.common.eventbus.Subscribe;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.event.GameEventCardChangeZone;
import forge.game.event.GameEventCardsRevealed;
import forge.game.event.GameEventPlayerPriority;
import forge.game.event.GameEventZone;
import forge.game.player.Player;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Match-local observations. Engine visibility permissions are checked before recording identities. */
public final class PublicCardObserver {
    private final Game game;
    private final Player viewer;
    private final Set<PaperCard> seen = new LinkedHashSet<>();

    public PublicCardObserver(Game game, Player viewer) {
        this.game = game;
        this.viewer = viewer;
        game.subscribeToEvents(this);
        snapshot();
    }

    @Subscribe
    public void onZone(GameEventZone event) {
        observe(game.findByView(event.card()));
    }

    @Subscribe
    public void onZoneChange(GameEventCardChangeZone event) {
        observe(game.findByView(event.card()));
    }

    @Subscribe
    public void onReveal(GameEventCardsRevealed event) {
        if (!viewer.getView().equals(event.viewer())) return;
        for (var view : event.cards()) {
            Card card = game.findByView(view);
            if (card != null && card.getOwner() != viewer && card.isCollectible() && !card.isToken()
                    && card.getPaperCard() instanceof PaperCard printing)
                synchronized (seen) { seen.add(printing); }
        }
    }

    @Subscribe
    public void onPriority(GameEventPlayerPriority event) { snapshot(); }

    public void snapshot() {
        for (Player player : game.getPlayers()) for (Card card : player.getAllCards()) observe(card);
    }

    private void observe(Card card) {
        if (card == null || card.getOwner() == viewer || card.isToken() || !card.isCollectible()
                || card.getPaperCard() == null || !card.getView().canBeShownTo(viewer.getView())) return;
        // Public zones can contain morphs: canBeShownTo means an object is visible, not its identity.
        if (card.isFaceDown() && !card.mayPlayerLook(viewer)) return;
        if (card.getPaperCard() instanceof PaperCard printing)
            synchronized (seen) { seen.add(printing); }
    }

    public List<PaperCard> observations() {
        synchronized (seen) { return new ArrayList<>(seen); }
    }
}
