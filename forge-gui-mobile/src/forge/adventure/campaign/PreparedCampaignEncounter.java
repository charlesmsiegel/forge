package forge.adventure.campaign;

import forge.deck.Deck;
import java.util.function.Supplier;

/** One actual template selection for ante preview and duel; cancellation discards it. */
public final class PreparedCampaignEncounter {
    private Deck deck;

    public Deck deck(Supplier<Deck> generate) {
        if (deck == null) {
            Deck selected = generate.get();
            if (selected != null) deck = new Deck(selected);
        }
        return deck;
    }

    public Deck take() {
        Deck selected = deck;
        clear();
        return selected;
    }

    public void clear() { deck = null; }
}
