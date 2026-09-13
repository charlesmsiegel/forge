package forge.adventure.campaign;

import forge.deck.CardPool;
import forge.deck.Deck;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Campaign deck legality (MVP.md §31, §32): minimum size, per-name copy limits, restricted and
 * banned cards, and "every copy in the deck must be a copy the player can currently use".
 * Produces human-readable problems so the deck-repair screen can explain why a fight is blocked.
 */
public final class DeckValidator {
    private DeckValidator() {
    }

    public static boolean isValid(Deck deck, CardPool available, CampaignConfig config) {
        return problems(deck, available, config).isEmpty();
    }

    /**
     * @param deck      the deck to check (main + sideboard are checked together for copy limits)
     * @param available cards the player may currently use (whole collection at home, the
     *                  expedition pool while away); null skips the availability check
     */
    public static List<String> problems(Deck deck, CardPool available, CampaignConfig config) {
        List<String> problems = new ArrayList<>();
        if (deck == null) {
            problems.add("No deck selected.");
            return problems;
        }
        int size = deck.getMain().countAll();
        int min = config.player.minDeckSize;
        if (size < min)
            problems.add("Main deck has " + size + " cards; at least " + min + " are required.");

        CardPool all = deck.getAllCardsInASinglePool(true, true);

        Map<String, Integer> byName = new LinkedHashMap<>();
        for (Map.Entry<PaperCard, Integer> e : all) {
            byName.merge(e.getKey().getName(), e.getValue(), Integer::sum);
        }
        for (Map.Entry<String, Integer> e : byName.entrySet()) {
            String name = e.getKey();
            int count = e.getValue();
            PaperCard sample = firstByName(all, name);
            if (sample != null && AnteService.isOrdinaryBasicLand(sample))
                continue; // ordinary basic lands are unlimited
            if (config.cards.isBanned(name)) {
                problems.add(name + " is banned in this campaign.");
                continue;
            }
            int limit = config.cards.isRestricted(name) ? config.player.restrictedCopyLimit : config.player.ordinaryCopyLimit;
            if (count > limit)
                problems.add(name + ": " + count + " copies, limit " + limit + (config.cards.isRestricted(name) ? " (restricted)." : "."));
        }

        if (available != null) {
            for (Map.Entry<PaperCard, Integer> e : all) {
                PaperCard card = e.getKey();
                int owned = available.count(card);
                if (e.getValue() > owned)
                    problems.add(card.getName() + " (" + card.getEdition() + "): deck uses " + e.getValue()
                            + ", only " + owned + " available.");
            }
        }
        return problems;
    }

    private static PaperCard firstByName(CardPool pool, String name) {
        for (Map.Entry<PaperCard, Integer> e : pool) {
            if (e.getKey().getName().equals(name))
                return e.getKey();
        }
        return null;
    }
}
