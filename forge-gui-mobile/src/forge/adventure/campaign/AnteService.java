package forge.adventure.campaign;

import forge.adventure.player.AdventurePlayer;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Campaign ante rules (MVP.md §13, §17). Chooses the physical copies at stake before a duel and
 * applies the engine's ante result to the player's collection afterwards.
 * <p>
 * The duel itself is still played by the Forge rules engine with the stakes pre-selected on the
 * {@code RegisteredPlayer}s, so ante-interacting cards keep working.
 */
public final class AnteService {
    private AnteService() {
    }

    /** True for "ordinary basic lands": non-foil Plains/Island/Swamp/Mountain/Forest/Wastes. */
    public static boolean isOrdinaryBasicLand(PaperCard card) {
        return card.isVeryBasicLand() && !card.isFoil();
    }

    /**
     * Every physical copy the player could lose to random ante: main deck plus sideboard,
     * one list entry per copy, minus ordinary basic lands when the rules say so.
     */
    public static List<PaperCard> eligiblePlayerCards(Deck deck, CampaignConfig.AnteRules rules) {
        List<PaperCard> eligible = new ArrayList<>();
        addEligible(deck.getMain(), rules, eligible);
        if (deck.has(DeckSection.Sideboard))
            addEligible(deck.get(DeckSection.Sideboard), rules, eligible);
        return eligible;
    }

    /** Every physical copy an opponent could lose: its main deck minus ordinary basic lands. */
    public static List<PaperCard> eligibleOpponentCards(Deck deck, CampaignConfig.AnteRules rules) {
        List<PaperCard> eligible = new ArrayList<>();
        if (deck != null)
            addEligible(deck.getMain(), rules, eligible);
        return eligible;
    }

    private static void addEligible(CardPool pool, CampaignConfig.AnteRules rules, List<PaperCard> out) {
        if (pool == null)
            return;
        for (PaperCard card : pool.toFlatList()) {
            if (rules.excludeBasicLands && isOrdinaryBasicLand(card))
                continue;
            out.add(card);
        }
    }

    /** Picks {@code count} distinct copies (not distinct printings) without replacement. */
    public static List<PaperCard> pickRandom(List<PaperCard> eligible, int count, Random random) {
        List<PaperCard> pool = new ArrayList<>(eligible);
        List<PaperCard> picked = new ArrayList<>();
        while (picked.size() < count && !pool.isEmpty()) {
            picked.add(pool.remove(random.nextInt(pool.size())));
        }
        return picked;
    }

    /** Normal ante: one random eligible copy per side (counts from the campaign config). */
    public static AnteStake normalStake(Deck playerDeck, Deck opponentDeck, CampaignConfig.AnteRules rules, Random random) {
        List<PaperCard> player = pickRandom(eligiblePlayerCards(playerDeck, rules), rules.playerAnteCount, random);
        List<PaperCard> opponent = pickRandom(eligibleOpponentCards(opponentDeck, rules), rules.opponentAnteCount, random);
        return new AnteStake(AnteStake.Kind.NORMAL, player, opponent);
    }

    /**
     * Reclamation ante: the player risks {@code reclamationRiskCount} random eligible copies against
     * one specific card the opponent previously won from them.
     */
    public static AnteStake reclamationStake(Deck playerDeck, PaperCard reclaimed, CampaignConfig.AnteRules rules, Random random) {
        List<PaperCard> player = pickRandom(eligiblePlayerCards(playerDeck, rules), rules.reclamationRiskCount, random);
        return new AnteStake(AnteStake.Kind.RECLAMATION, player, List.of(reclaimed));
    }

    /**
     * Applies a finished duel's ante result to the player's permanent collection: won cards are
     * added, lost cards are removed one copy each (decks trimmed accordingly).
     *
     * @return the lost cards that were actually removed (a card the player did not own is ignored).
     */
    public static List<PaperCard> applyResult(AdventurePlayer player, List<PaperCard> won, List<PaperCard> lost) {
        for (PaperCard card : won)
            CardOwnership.addOne(player, card);
        List<PaperCard> removed = new ArrayList<>();
        for (PaperCard card : lost) {
            if (CardOwnership.removeOne(player, card))
                removed.add(card);
        }
        return removed;
    }
}
