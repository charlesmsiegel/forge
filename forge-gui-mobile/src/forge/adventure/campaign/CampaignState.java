package forge.adventure.campaign;

import forge.adventure.data.EnemyData;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.util.SaveFileContent;
import forge.adventure.util.SaveFileData;
import forge.deck.CardPool;
import forge.item.PaperCard;
import forge.util.MyRandom;

import java.util.List;
import java.util.Random;

/**
 * Persistent Shandalar Reborn campaign state stored as its own sub-blob of the world save
 * (rivals, expedition, Stronghold runs). Grows milestone by milestone; every section is
 * optional on load so older saves keep working.
 */
public class CampaignState implements SaveFileContent {
    public static final int SAVE_VERSION = 1;

    private static CampaignState current = new CampaignState();

    private final RivalRegistry rivals = new RivalRegistry();
    private Random random = MyRandom.getRandom();

    public static CampaignState instance() {
        return current;
    }

    /** Replace the live state (new game, load, tests). */
    public static void setInstance(CampaignState state) {
        current = state == null ? new CampaignState() : state;
    }

    public void clear() {
        rivals.load(null);
    }

    /** Deterministic randomness for tests. */
    public void setRandom(Random random) {
        this.random = random;
    }

    public RivalRegistry rivals() {
        return rivals;
    }

    /** The cards the player may currently use for deck building. Filtered during expeditions (M4). */
    public CardPool availableCards(AdventurePlayer player) {
        return player.getCards();
    }

    /**
     * Campaign bookkeeping after an ordinary duel (MVP.md §16, §17).
     *
     * @param rivalId    id of the rival that was fought, or null for a generic enemy
     * @param enemy      the enemy template that was fought
     * @param playerWon  match result
     * @param cardsWon   exact printings the player gained (already applied to the collection)
     * @param cardsLost  exact printings the player lost (already removed from the collection)
     * @param stake      the stake that was played for (may be null)
     * @return the id of the rival that now holds the player's lost cards (newly promoted or
     *         existing), or null when no rival is involved
     */
    public String onMatchEnd(String rivalId, EnemyData enemy, boolean playerWon,
                             List<PaperCard> cardsWon, List<PaperCard> cardsLost, AnteStake stake) {
        Rival rival = rivals.byId(rivalId);
        if (!playerWon && !cardsLost.isEmpty()) {
            boolean promoted = false;
            if (rival == null) {
                rival = rivals.promote(enemy, CampaignConfig.instance().rivals, random);
                promoted = true;
            }
            rival.playerLosses++;
            for (PaperCard card : cardsLost)
                rival.addTaken(card);
            CampaignLog.event(promoted ? "rival_created" : "rival_took_cards")
                    .with("rival", rival.name).with("rivalId", rival.id).with("base", rival.baseEnemyName)
                    .withCards("cards", cardsLost).with("record", rival.record()).write();
            return rival.id;
        }
        if (rival == null)
            return null;
        if (playerWon) {
            rival.playerWins++;
            for (PaperCard card : cardsWon) {
                if (rival.removeTaken(card))
                    CampaignLog.event("card_reclaimed").with("rival", rival.name).with("card", card).write();
            }
        } else {
            rival.playerLosses++;
        }
        if (stake != null && stake.kind == AnteStake.Kind.RECLAMATION)
            CampaignLog.event("reclamation_result").with("rival", rival.name).with("won", playerWon)
                    .withCards("target", stake.opponentCards).withCards("risked", stake.playerCards).write();
        return rival.id;
    }

    @Override
    public void load(SaveFileData data) {
        clear();
        if (data == null)
            return;
        if (data.containsKey("rivals"))
            rivals.load(data.readSubData("rivals"));
    }

    @Override
    public SaveFileData save() {
        SaveFileData data = new SaveFileData();
        data.store("version", SAVE_VERSION);
        data.store("rivals", rivals.save());
        return data;
    }
}
