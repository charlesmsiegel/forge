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
    public static final int SAVE_VERSION = 3;

    private static CampaignState current = new CampaignState();

    private CardUniverse universe;
    private final CampaignProgress progression = new CampaignProgress();
    private final OpponentKnowledge knowledge = new OpponentKnowledge();
    private final RivalRegistry rivals = new RivalRegistry();
    private final ExpeditionState expedition = new ExpeditionState();
    private final Map<String, Integer> completions = new HashMap<>();
    private final Map<String, RealmProgress> realms = new HashMap<>();
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
        forge.adventure.data.RewardData.invalidateCardPool();
    }

    public void clear() {
        universe = null;
        forge.adventure.data.RewardData.invalidateCardPool();
        rivals.load(null);
        knowledge.load(null);
        progression.load(null);
        expedition.leave();
        completions.clear();
        realms.clear();
        ownership.clear();
        campaignId = "";
        configVersion = 0;
    }

    /** Deterministic randomness for tests. */
    public void setRandom(Random random) {
        this.random = random;
    }

    public CardUniverse universeFor(CampaignConfig config) {
        return universe != null && universe.belongsTo(config) ? universe : null;
    }

    public void initializeUniverse(CampaignConfig config, forge.adventure.data.ConfigData plane) {
        if (!config.isActive() || universeFor(config) != null) return;
        universe = CardUniverse.capture(config, plane);
        forge.adventure.data.RewardData.invalidateCardPool();
    }

    public void requireCampaignEdition(String edition) {
        CardUniverse snapshot = universeFor(CampaignConfig.instance());
        if (snapshot != null) snapshot.requireEdition(edition);
    }

    /** Ordinary generated products obey the frozen universe; authored bespoke rewards are separate. */
    public void filterGeneratedPack(Deck pack) {
        CardUniverse snapshot = universeFor(CampaignConfig.instance());
        if (snapshot == null) return;
        List<Map.Entry<PaperCard, Integer>> remove = new ArrayList<>();
        int allowed = 0;
        for (Map.Entry<PaperCard, Integer> entry : pack.getMain())
            if (!snapshot.allowsPrinting(entry.getKey())) remove.add(Map.entry(entry.getKey(), entry.getValue()));
            else allowed += entry.getValue();
        if (allowed == 0) throw new IllegalArgumentException("No campaign-legal cards in generated product");
        for (Map.Entry<PaperCard, Integer> entry : remove) pack.getMain().remove(entry.getKey(), entry.getValue());
    }

    public CampaignProgress progression() { return progression; }

    public OpponentKnowledge knowledge() { return knowledge; }

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

    public boolean saleTouchesProtection(AdventurePlayer player, PaperCard printing, int count) {
        int total = 0, unprotected = 0;
        for (OwnedCard copy : availableCopies(player)) if (copy.card.equals(printing)) {
            total++;
            if (!progression.isProtected(copy.id)) unprotected++;
        }
        return Math.min(count, total) > unprotected;
    }

    public boolean saleTouchesProtection(AdventurePlayer player, forge.util.ItemPool<PaperCard> cards) {
        for (Map.Entry<PaperCard, Integer> entry : cards)
            if (saleTouchesProtection(player, entry.getKey(), entry.getValue())) return true;
        return false;
    }

    /** Printing-based voluntary removal spends unprotected copies before protected copies. */
    public List<OwnedCard> voluntaryRemovalCopies(AdventurePlayer player) {
        List<OwnedCard> copies = new ArrayList<>(availableCopies(player));
        copies.sort(java.util.Comparator.comparing(copy -> progression.isProtected(copy.id)));
        return copies;
    }

    void receiveCopy(OwnedCard copy) {
        ownership.add(copy);
        expedition.onAcquired(copy);
    }

    void loseCopy(OwnedCard copy) {
        if (ownership.remove(copy)) {
            expedition.onLost(copy);
            progression.unprotect(copy.id);
        }
    }

    /** Leave home with the active deck + sideboard as the only usable cards. */
    public void enterExpedition(String regionId, AdventurePlayer player) {
        enterExpedition(regionId, player, "");
    }

    public void enterExpedition(String regionId, AdventurePlayer player, String rootPoiId) {
        if (regionId.equals(CampaignConfig.instance().stronghold.region))
            for (String edition : CampaignConfig.instance().stronghold.editions) requireCampaignEdition(edition);
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
        RealmProgress progress = realms.get(expedition.getRegionId());
        if (progress != null) progress.leave();
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
        if (config.hasRealm(regionId)) {
            List<String> maps = new ArrayList<>();
            for (HistoricalRealm.Chapter chapter : config.realm(regionId).chapters)
                java.util.Collections.addAll(maps, chapter.maps);
            return maps.toArray(new String[0]);
        }
        if (regionId.equals(config.stronghold.region))
            return config.stronghold.maps;
        return new String[0];
    }

    public RealmProgress realmProgress(String realmId) {
        CampaignConfig.instance().realm(realmId);
        return realms.computeIfAbsent(realmId, key -> new RealmProgress());
    }

    /** Entry never replaces an active carried pool. Product definitions are checked before mutation. */
    public HistoricalRealm.Chapter enterRealm(String realmId, String selection, AdventurePlayer player, String rootPoiId) {
        if (expedition.isActive()) throw new IllegalStateException("Return home before starting another expedition");
        HistoricalRealm realm = CampaignConfig.instance().realm(realmId);
        CampaignConfig.instance().validateRealmContent(realm);
        CampaignProgress.validateRules(CampaignConfig.instance().progression);
        for (HistoricalRealm.Chapter chapter : realm.chapters) {
            for (String edition : chapter.editions) {
                requireCampaignEdition(edition);
                if (forge.StaticData.instance().getCardEdition(edition) == null)
                    throw new IllegalArgumentException("Unknown historical edition " + edition);
            }
            RegionCompletion.boosterTemplate(chapter.boosterEdition);
        }
        RealmProgress progress = realmProgress(realmId);
        progress.start(realm, selection);
        resetRegionMaps(realmId, rootPoiId);
        enterExpedition(realmId, player, rootPoiId);
        DungeonLife.onEnter(player);
        CampaignLog.event("realm_enter").with("realm", realmId)
                .with("chapter", progress.currentChapter(realm).id).with("singleChapter", progress.isSingleChapter()).write();
        return progress.currentChapter(realm);
    }

    public HistoricalRealm.Chapter activeRealmChapter() {
        if (!expedition.isActive()) return null;
        String id = expedition.getRegionId();
        RealmProgress progress = realms.get(id);
        if (progress == null || !progress.isActive()) return null; // Legacy standalone expedition.
        return progress.currentChapter(CampaignConfig.instance().realm(id));
    }

    public HistoricalRealm.Chapter advanceRealm(String realmId) {
        if (!expedition.isActive() || !realmId.equals(expedition.getRegionId()))
            throw new IllegalStateException("This realm is not the active expedition");
        HistoricalRealm realm = CampaignConfig.instance().realm(realmId);
        RealmProgress progress = realmProgress(realmId);
        progress.advance(realm);
        CampaignLog.event("realm_checkpoint").with("realm", realmId)
                .with("chapter", progress.currentChapter(realm).id).write();
        return progress.currentChapter(realm);
    }

    /** Legacy region id shares one lifetime jackpot with its block chapter. */
    public String chapterRewardKey(String realmId, String chapterId) {
        HistoricalRealm.Chapter chapter = CampaignConfig.instance().realm(realmId).chapter(chapterId);
        return chapter.legacyRegion == null || chapter.legacyRegion.isEmpty()
                ? realmId + ":" + chapterId : chapter.legacyRegion;
    }

    public List<Deck> onChapterCompleted(String realmId, String chapterId, AdventurePlayer player) {
        if (!expedition.isActive() || !realmId.equals(expedition.getRegionId())) return new ArrayList<>();
        HistoricalRealm realm = CampaignConfig.instance().realm(realmId);
        RealmProgress progress = realmProgress(realmId);
        if (!progress.canComplete(realm, chapterId)) return new ArrayList<>();
        HistoricalRealm.Chapter chapter = realm.chapter(chapterId);
        String key = chapterRewardKey(realmId, chapterId);
        int count = completions(key) + 1;
        // Resolve both configuration and product failures before any chapter guard or milestone changes.
        CampaignProgress.validateRules(CampaignConfig.instance().progression);
        List<Deck> packs = RegionCompletion.createBoosters(chapter.boosterEdition,
                count == 1 ? chapter.firstClearBoxBoosterCount : chapter.repeatClearBoosterCount, count == 1 ? "Box" : null);
        progress.complete(realm, chapterId);
        if (progress.isRunComplete() && !progress.isSingleChapter())
            progression.onFullClear(realmId, player, CampaignConfig.instance().progression);
        completions.put(key, count);
        for (Deck pack : packs) player.addBooster(pack);
        CampaignLog.event("chapter_completed").with("realm", realmId).with("chapter", chapterId)
                .with("completion", count).with("packs", packs.size()).with("elapsedMillis", expedition.elapsedMillis()).write();
        return packs;
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
        HistoricalRealm.Chapter activeChapter = activeRealmChapter();
        if (activeChapter != null && regionId.equals(activeChapter.legacyRegion))
            return onChapterCompleted(expedition.getRegionId(), activeChapter.id, player);
        int count = completions(regionId) + 1;
        CampaignConfig config = CampaignConfig.instance();
        List<Deck> packs = new ArrayList<>();
        if (regionId.equals(config.stronghold.region)) {
            boolean first = count == 1;
            int packCount = first ? config.stronghold.firstClearBoxBoosterCount : config.stronghold.repeatClearBoosterCount;
            packs = RegionCompletion.createBoosters(config.stronghold.boosterEdition, packCount, first ? "Box" : null);
        }
        completions.put(regionId, count);
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
        List<OwnedCard> removable = expedition.availableCopies(ownership.copies());
        removable.sort(java.util.Comparator.comparing(copy -> progression.isProtected(copy.id)));
        for (OwnedCard copy : removable) {
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
            for (PaperCard card : cardsLost) {
                rival.addTaken(card);
                knowledge.observe(rival.id, card);
            }
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
        for (OwnedCard copy : result.lost) {
            rival.addTaken(copy);
            knowledge.observe(rival.id, copy.card);
        }
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
        if (data.containsKey("universe")) { universe = new CardUniverse(); universe.load(data.readSubData("universe")); }
        if (data.containsKey("campaignId")) campaignId = data.readString("campaignId");
        if (data.containsKey("configVersion")) configVersion = data.readInt("configVersion");
        if (data.containsKey("ownership")) ownership.load(data.readSubData("ownership"));
        if (data.containsKey("rivals"))
            rivals.load(data.readSubData("rivals"));
        if (data.containsKey("knowledge")) knowledge.load(data.readSubData("knowledge"));
        if (data.containsKey("progression")) progression.load(data.readSubData("progression"));
        if (data.containsKey("expedition"))
            expedition.load(data.readSubData("expedition"));
        if (data.containsKey("realms")) {
            SaveFileData savedRealms = data.readSubData("realms");
            for (String id : savedRealms.keySet()) {
                RealmProgress progress = new RealmProgress();
                progress.load(savedRealms.readSubData(id));
                realms.put(id, progress); // Keep unknown ids: never reinterpret another realm's progress.
            }
        }
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
        if (universe != null) data.store("universe", universe.save());
        data.store("campaignId", campaignId);
        data.store("configVersion", configVersion);
        data.store("ownership", ownership.save());
        data.store("rivals", rivals.save());
        data.store("knowledge", knowledge.save());
        data.store("progression", progression.save());
        data.store("expedition", expedition.save());
        SaveFileData savedRealms = new SaveFileData();
        for (Map.Entry<String, RealmProgress> entry : realms.entrySet())
            savedRealms.store(entry.getKey(), entry.getValue().save());
        data.store("realms", savedRealms);
        data.storeObject("completionKeys", completions.keySet().toArray(new String[0]));
        data.storeObject("completionValues", completions.values().toArray(new Integer[0]));
        return data;
    }
}
