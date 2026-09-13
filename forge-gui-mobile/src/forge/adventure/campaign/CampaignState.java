package forge.adventure.campaign;

import forge.adventure.data.EnemyData;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.util.SaveFileContent;
import forge.adventure.util.SaveFileData;
import forge.deck.CardPool;
import forge.item.PaperCard;
import forge.util.MyRandom;

import forge.adventure.pointofintrest.PointOfInterestChanges;
import forge.adventure.world.WorldSave;
import forge.deck.Deck;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Persistent Shandalar Reborn campaign state stored as its own sub-blob of the world save
 * (rivals, expedition, Stronghold runs). Grows milestone by milestone; every section is
 * optional on load so older saves keep working.
 */
public class CampaignState implements SaveFileContent {
    public static final int SAVE_VERSION = 2;

    private static CampaignState current = new CampaignState();

    private final RivalRegistry rivals = new RivalRegistry();
    private final ExpeditionState expedition = new ExpeditionState();
    private final Map<String, Integer> completions = new HashMap<>();
    private final CardIdentityLedger ownership = new CardIdentityLedger();
    private String campaignId = "";
    private int configVersion;
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
        expedition.leave();
        completions.clear();
        ownership.clear();
        campaignId = "";
        configVersion = 0;
    }

    /** Deterministic randomness for tests. */
    public void setRandom(Random random) {
        this.random = random;
    }

    public RivalRegistry rivals() {
        return rivals;
    }

    public ExpeditionState expedition() {
        return expedition;
    }

    /** The cards the player may currently use for deck building: the whole collection at home,
     *  only carried + acquired copies during an expedition (MVP.md 12). */
    public CardPool availableCards(AdventurePlayer player) {
        if (!CampaignConfig.instance().isActive()) return expedition.available(player.getCards());
        if (!expedition.isActive()) {
            initializeOwnership(player);
            return player.getCards();
        }
        CardPool result = new CardPool();
        for (OwnedCard copy : availableCopies(player)) result.add(copy.card);
        return result;
    }

    public void initializeOwnership(AdventurePlayer player) {
        if (!CampaignConfig.instance().isActive()) return;
        if (campaignId.isEmpty()) campaignId = CampaignConfig.instance().campaignId;
        if (configVersion == 0) configVersion = CampaignConfig.instance().version;
        ownership.reconcile(player.getCards());
        expedition.bindCopies(ownership.copies());
    }

    public List<OwnedCard> ownedCopies(AdventurePlayer player) {
        initializeOwnership(player);
        return ownership.copies();
    }

    public List<OwnedCard> availableCopies(AdventurePlayer player) {
        return expedition.availableCopies(ownedCopies(player));
    }

    void receiveCopy(OwnedCard copy) {
        ownership.add(copy);
        expedition.onAcquired(copy);
    }

    void loseCopy(OwnedCard copy) {
        if (ownership.remove(copy)) expedition.onLost(copy);
    }

    /** Leave home with the active deck + sideboard as the only usable cards. */
    public void enterExpedition(String regionId, AdventurePlayer player) {
        enterExpedition(regionId, player, "");
    }

    public void enterExpedition(String regionId, AdventurePlayer player, String rootPoiId) {
        initializeOwnership(player);
        expedition.enter(regionId, player.getSelectedDeck());
        expedition.bindCopies(ownership.copies());
        expedition.setRootPoiId(rootPoiId);
        CampaignLog.event("expedition_enter").with("region", regionId)
                .with("carried", expedition.getCarried().countAll()).write();
    }

    /** Return home: the full surviving permanent collection becomes usable again. */
    public void leaveExpedition(String reason) {
        if (!expedition.isActive())
            return;
        CampaignLog.event("expedition_leave").with("region", expedition.getRegionId()).with("reason", reason)
                .with("elapsedMillis", expedition.elapsedMillis())
                .with("acquired", expedition.getAcquired().countAll()).write();
        resetRegionMaps(expedition.getRegionId(), expedition.getRootPoiId());
        expedition.leave();
    }

    /** Any exit from a map to the world ends an active expedition (retreat, defeat, HUD exit). */
    public void onExitToWorld(String reason) {
        leaveExpedition(reason);
    }

    /** Forgets defeated enemies / map flags of the region's maps so the next run starts fresh. */
    public void resetRegionMaps(String regionId, String rootPoiId) {
        if (regionId == null || regionId.isEmpty() || rootPoiId == null || rootPoiId.isEmpty())
            return;
        try {
            for (String map : regionMaps(regionId)) {
                PointOfInterestChanges changes = WorldSave.getCurrentSave().getPointOfInterestChanges(rootPoiId + map);
                changes.clearDeletedObjects();
                changes.getMapFlags().clear();
            }
        } catch (Throwable t) {
            System.err.println("Could not reset region maps for " + regionId + ": " + t);
        }
    }

    private static String[] regionMaps(String regionId) {
        CampaignConfig config = CampaignConfig.instance();
        if (regionId.equals(config.stronghold.region))
            return config.stronghold.maps;
        return new String[0];
    }

    public int completions(String regionId) {
        return completions.getOrDefault(regionId, 0);
    }

    /**
     * The region's final encounter was won: count the completion and hand out sealed product
     * (first clear: a booster box; later clears: loose boosters). Packs go to the player's
     * unopened-booster inventory to be opened one at a time.
     *
     * @return the packs granted
     */
    public List<Deck> onRegionCompleted(String regionId, AdventurePlayer player) {
        int count = completions(regionId) + 1;
        completions.put(regionId, count);
        CampaignConfig config = CampaignConfig.instance();
        List<Deck> packs = new ArrayList<>();
        if (regionId.equals(config.stronghold.region)) {
            boolean first = count == 1;
            int packCount = first ? config.stronghold.firstClearBoxBoosterCount : config.stronghold.repeatClearBoosterCount;
            packs = RegionCompletion.createBoosters(config.stronghold.boosterEdition, packCount, first ? "Box" : null);
        }
        for (Deck pack : packs)
            player.addBooster(pack);
        CampaignLog.event("region_completed").with("region", regionId).with("completion", count)
                .with("elapsedMillis", expedition.elapsedMillis())
                .with("packs", packs.size()).write();
        return packs;
    }

    /** Called whenever copies enter the permanent collection (rewards, packs, purchases, ante wins). */
    public void onCardsAcquired(PaperCard card, int amount) {
        if (!CampaignConfig.instance().isActive()) return;
        for (int i = 0; i < amount; i++) receiveCopy(OwnedCard.mint(card));
    }

    /** Called whenever copies leave the permanent collection (ante losses, sales). */
    public void onCardsLost(PaperCard card, int amount) {
        if (!CampaignConfig.instance().isActive()) return;
        for (OwnedCard copy : expedition.availableCopies(ownership.copies())) {
            if (amount == 0) break;
            if (copy.card.equals(card)) { loseCopy(copy); amount--; }
        }
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
            // Template copies are not the stolen copies, even when their printings match.
            if (stake != null && stake.kind == AnteStake.Kind.RECLAMATION) {
                List<PaperCard> remainingWon = new ArrayList<>(cardsWon);
                for (PaperCard card : stake.opponentCards) {
                    if (remainingWon.remove(card) && rival.removeTaken(card))
                        CampaignLog.event("card_reclaimed").with("rival", rival.name).with("card", card).write();
                }
            }
        } else {
            rival.playerLosses++;
        }
        if (stake != null && stake.kind == AnteStake.Kind.RECLAMATION)
            CampaignLog.event("reclamation_result").with("rival", rival.name).with("won", playerWon)
                    .withCards("target", stake.opponentCards).withCards("risked", stake.playerCards).write();
        return rival.id;
    }

    /** Physical effects may transfer a copy even when its former owner wins the duel. */
    public String onPhysicalMatchEnd(String rivalId, EnemyData enemy, boolean playerWon,
                                     AnteService.PhysicalResult result, AnteStake stake) {
        Rival rival = rivals.byId(rivalId);
        boolean promoted = false;
        if (rival == null && !result.lost.isEmpty()) {
            rival = rivals.promote(enemy, CampaignConfig.instance().rivals, random);
            promoted = true;
        }
        if (rival == null) return null;
        if (playerWon) rival.playerWins++;
        else rival.playerLosses++;
        for (OwnedCard copy : result.lost) rival.addTaken(copy);
        // Only actual IDs that came back are reclaimed, regardless of match victory.
        for (OwnedCard copy : result.won)
            if (rival.removeTaken(copy))
                CampaignLog.event("card_reclaimed").with("rival", rival.name)
                        .with("instanceId", copy.id).with("card", copy.card).write();
        if (!result.lost.isEmpty())
            CampaignLog.event(promoted ? "rival_created" : "rival_took_cards")
                    .with("rival", rival.name).with("rivalId", rival.id).with("record", rival.record()).write();
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
        if (data.containsKey("campaignId")) campaignId = data.readString("campaignId");
        if (data.containsKey("configVersion")) configVersion = data.readInt("configVersion");
        if (data.containsKey("ownership")) ownership.load(data.readSubData("ownership"));
        if (data.containsKey("rivals"))
            rivals.load(data.readSubData("rivals"));
        if (data.containsKey("expedition"))
            expedition.load(data.readSubData("expedition"));
        if (data.containsKey("completionKeys")) {
            String[] keys = (String[]) data.readObject("completionKeys");
            Integer[] values = (Integer[]) data.readObject("completionValues");
            for (int i = 0; i < keys.length && i < values.length; i++)
                completions.put(keys[i], values[i]);
        }
    }

    @Override
    public SaveFileData save() {
        SaveFileData data = new SaveFileData();
        data.store("version", SAVE_VERSION);
        data.store("campaignId", campaignId);
        data.store("configVersion", configVersion);
        data.store("ownership", ownership.save());
        data.store("rivals", rivals.save());
        data.store("expedition", expedition.save());
        data.storeObject("completionKeys", completions.keySet().toArray(new String[0]));
        data.storeObject("completionValues", completions.values().toArray(new Integer[0]));
        return data;
    }
}
