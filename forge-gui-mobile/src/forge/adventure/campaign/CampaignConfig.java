package forge.adventure.campaign;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.XmlReader;
import forge.adventure.util.Config;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Shandalar Reborn campaign configuration, read from {@code <plane>/campaign.json}.
 * All balance values that need iteration live here rather than in Java constants.
 * A plane without a campaign.json gets the defaults below (campaign features disabled).
 */
public class CampaignConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    public int version = 1;
    public String campaignId = "";
    public PlayerRules player = new PlayerRules();
    public CardRules cards = new CardRules();
    public UniverseRules universe = new UniverseRules();
    public AnteRules ante = new AnteRules();
    public EconomyRules economy = new EconomyRules();
    public StrongholdRules stronghold = new StrongholdRules();
    public WorldRules worldRules = new WorldRules();
    public SeedRules seeds = new SeedRules();
    public StoryRules story = new StoryRules();
    public LimitedOrganizer[] limitedOrganizers = new LimitedOrganizer[0];
    public TradeRecipe[] trades = new TradeRecipe[0];
    public TrainingPath[] training = new TrainingPath[0];

    public static class TrainingPath implements Serializable {
        public String id = "", name = "", color = "", map = "";
        public TrainingLesson[] lessons = new TrainingLesson[0];
    }
    public static class TrainingLesson implements Serializable {
        public String id = "", name = "", enemy = "", guidance = "";
        public String[] rewards = new String[0];
    }
    public ProgressionRules progression = new ProgressionRules();
    public HistoricalRealm[] realms = new HistoricalRealm[0];
    public DungeonRules dungeon = new DungeonRules();
    public RivalRules rivals = new RivalRules();
    public DifficultyRules difficulty = new DifficultyRules();
    public static class DifficultyRules implements Serializable {
        public float enemyLifeMultiplier = 1f;
        public String aiProfile = "";
        public int ordinaryTierFloor;
    }
    public void validateBalance() { CampaignBalance.validate(this); }

    public static class StoryRules implements Serializable {
        public String[] guardianFlags = new String[0];
        public StoryEncounter[] encounters = new StoryEncounter[0];
    }
    public static class StoryEncounter implements Serializable {
        public String id = "", name = "", enemy = "", map = "", poi = "";
        public String audience = "", refusal = "", reward = "", pursuit = "";
        public String[] requires = new String[0], grants = new String[0];
        public DeckCard[] cards = new DeckCard[0];
        public String[] basicLands = {"Island"}, fallbackSpells = new String[0];
        public boolean bolasDuplicates;
    }
    public static class DeckCard implements Serializable {
        public String name = "";
        public int count;
    }

    public static class PlayerRules implements Serializable {
        public int startingLife = 12;
        public int ordinaryCopyLimit = 3;
        public int restrictedCopyLimit = 1;
        public int minDeckSize = 40;
    }

    public static class CardRules implements Serializable {
        public String[] restrictedCards = new String[0];
        public String[] bannedCards = new String[0];
        private transient Set<String> restrictedSet, bannedSet;

        public boolean isRestricted(String cardName) {
            if (restrictedSet == null) restrictedSet = new HashSet<>(Arrays.asList(restrictedCards));
            return restrictedSet.contains(cardName);
        }
        public boolean isBanned(String cardName) {
            if (bannedSet == null) bannedSet = new HashSet<>(Arrays.asList(bannedCards));
            return bannedSet.contains(cardName);
        }
    }

    public static class UniverseRules implements Serializable {
        public String preset = "All Magic";
        public String[] allowedEditions = new String[0];
        public String[] additionalCards = new String[0];
        public String[] unbannedCards = new String[0];
        public String[] unrestrictedCards = new String[0];
        public boolean restoreAnteCards = true;
        public boolean includeUnreleased;
    }

    public boolean isCardBanned(String name) {
        CardUniverse snapshot = CampaignState.instance().universeFor(this);
        return snapshot == null ? cards.isBanned(name) : snapshot.isBanned(name);
    }
    public boolean isCardRestricted(String name) {
        CardUniverse snapshot = CampaignState.instance().universeFor(this);
        return snapshot == null ? cards.isRestricted(name) : snapshot.isRestricted(name);
    }

    public static class AnteRules implements Serializable {
        public boolean enabled = false;
        public boolean excludeBasicLands = true;
        public int playerAnteCount = 1;
        public int opponentAnteCount = 1;
        public int reclamationRiskCount = 3;
    }

    public static class EconomyRules implements Serializable {
        public float goldRewardMultiplier = 1.0f;
        public float cardSellMultiplier = 0.5f;
        public float shopPriceMultiplier = 1.0f;
        public float boosterPriceMultiplier = 1.0f;
    }

    public static class StrongholdRules implements Serializable {
        /** Region id used by expedition gates and completesRegion tags. */
        public String region = "stronghold";
        /** Plane-relative maps whose per-map state is reset when the expedition ends. */
        public String[] maps = new String[0];
        public String[] editions = new String[]{"STH"};
        public String boosterEdition = "STH";
        public int firstClearBoxBoosterCount = 36;
        public int repeatClearBoosterCount = 6;
    }

    public static class WorldRules implements Serializable {
        /** Existing Shandalar Reborn saves predate saved biome counts and have two biomes. */
        public int legacyBiomeCount;
    }
    public static class LandmarkRoute implements Serializable {
        public String slot = "";
        public String poi = "";
        public String map = "";
    }

    public static class SeedRules implements Serializable {
        public SeedCandidate[] creatures = new SeedCandidate[0];
        public SeedCandidate[] locations = new SeedCandidate[0];
        public SeedCandidate[] synergy = new SeedCandidate[0];
        public int synergyChancePercent = 50;
        public LandmarkRoute[] routes = new LandmarkRoute[0];
    }
    public static class SeedCandidate implements Serializable {
        public String id = "";
        public String card = "";
        public String preferredEdition = "";
        public String enemy = "";
        public boolean allowGenerationExcluded;
    }

    public static class LimitedOrganizer implements Serializable {
        public String id = "";
        public String name = "";
        public String block = "";
        public boolean phantom;
    }

    public LimitedOrganizer limitedOrganizer(String id) {
        LimitedOrganizer selected = null;
        Set<String> ids = new HashSet<>();
        for (LimitedOrganizer organizer : limitedOrganizers) {
            if (organizer.id == null || organizer.id.isBlank() || !ids.add(organizer.id))
                throw new IllegalArgumentException("Invalid or duplicate Limited organizer");
            if (organizer.id.equals(id)) selected = organizer;
        }
        if (selected == null) throw new IllegalArgumentException("Unknown Limited organizer " + id);
        return selected;
    }

    public static class TradeRecipe implements Serializable {
        public String[] requires = new String[0];
        public String id = "";
        public String name = "";
        public int requiredCount = 20;
        public String[] inputEditions = new String[0];
        public String inputRarity = "Common";
        public String inputColor = "";
        public String[] outputEditions = new String[0];
        public String outputRarity = "Rare";
        public String outputColor = "";
    }

    public static class ProgressionRules implements Serializable {
        public Milestone[] milestones = new Milestone[0];
    }

    public static class Milestone implements Serializable {
        public String id = "";
        public String realm = "";
        public int fullClears = 1;
        public int ordinaryCopyLimit;
        public int maxLifeBonus;
        public int protectionSlots;
        public int restrictedChoiceTokens;
        public boolean masterCollector;
        public String exemptCard = "";
        public int exemptCopyLimit;
    }

    public static class RivalRules implements Serializable {
        public int winsPerTier = 3;
        public int championTier = 1, sealTier = 2;
        public int rarityUpgradeSlotsPerTier = 2;
        /** Given names and epithets combined into "Name, Epithet" for promoted rivals. */
        public String[] names = new String[]{"Krag", "Vesna", "Torvald", "Ilsa", "Mogrin", "Selene", "Bram", "Nadira"};
        public String[] epithets = new String[]{"Ember-Sneak", "the Cardsharp", "Ante-Taker", "the Vulture", "Deckbreaker", "the Grifter"};
    }

    public static class DungeonRules implements Serializable {
        /** Tiled map property that marks a map as a persistent-life dungeon. */
        public String persistentLifeMapKeyword = "persistentLife";
    }

    /** Resolve a stable realm id and reject ambiguous/invalid routing before entry. */
    public HistoricalRealm realm(String id) {
        Set<String> ids = new HashSet<>();
        Set<String> maps = new HashSet<>();
        HistoricalRealm result = null;
        for (HistoricalRealm realm : realms) {
            realm.validate();
            if (!ids.add(realm.id)) throw new IllegalArgumentException("Duplicate realm " + realm.id);
            for (HistoricalRealm.Chapter chapter : realm.chapters)
                for (String map : chapter.maps)
                    if (!maps.add(map)) throw new IllegalArgumentException("Map belongs to multiple realms: " + map);
            if (realm.id.equals(id)) result = realm;
        }
        if (result == null) throw new IllegalArgumentException("Unknown historical realm '" + id + "'");
        return result;
    }

    /** Reject broken content before narrowing inventory or changing a checkpoint. */
    public void validateRealmContent(HistoricalRealm realm) {
        for (HistoricalRealm.Chapter chapter : realm.chapters) {
            for (String map : chapter.maps)
                if (!contentFile(map).exists()) throw new IllegalArgumentException("Missing historical map " + map);
            if (chapter.entryObject > 0) {
                try {
                    XmlReader.Element root = new XmlReader().parse(contentFile(chapter.entryMap));
                    boolean found = false;
                    for (XmlReader.Element object : root.getChildrenByNameRecursively("object"))
                        if (object.getIntAttribute("id", 0) == chapter.entryObject) {
                            String type = object.getAttribute("type", "");
                            String template = object.getAttribute("template", "");
                            found = type.equals("entry") || type.equals("portal") || type.equals("expeditionGate")
                                    || template.endsWith("/entry_up.tx") || template.endsWith("/portal.tx");
                        }
                    if (!found) throw new IllegalArgumentException("Missing chapter entry object " + chapter.entryObject + " in " + chapter.entryMap);
                } catch (com.badlogic.gdx.utils.SerializationException e) {
                    throw new IllegalArgumentException("Cannot read historical map " + chapter.entryMap, e);
                }
            }
        }
    }

    private FileHandle contentFile(String path) {
        return planeDirectory == null ? new FileHandle(Config.instance().getFilePath(path)) : new FileHandle(planeDirectory).child(path);
    }

    public boolean hasRealm(String id) {
        for (HistoricalRealm realm : realms) if (realm.id.equals(id)) return true;
        return false;
    }

    public boolean isActive() {
        return campaignId != null && !campaignId.isEmpty();
    }

    private transient String planeDirectory;

    /** Independent settings copy that retains the source plane's content location. */
    public CampaignConfig copy() {
        Json json = new Json();
        CampaignConfig result = json.fromJson(CampaignConfig.class, json.toJson(this));
        result.planeDirectory = planeDirectory;
        return result;
    }

    private static CampaignConfig current;

    /** Configuration of the currently selected plane (cached). */
    public static CampaignConfig instance() {
        if (current == null)
            current = load(Config.instance().getFilePath("campaign.json"));
        return current;
    }

    /** Replace the cached configuration (tests, plane switch). */
    public static void setInstance(CampaignConfig config) {
        current = config;
    }

    public static CampaignConfig load(String path) {
        FileHandle file = new FileHandle(path);
        if (!file.exists())
            return new CampaignConfig();
        try {
            Json json = new Json();
            json.setIgnoreUnknownFields(true);
            CampaignConfig config = json.fromJson(CampaignConfig.class, file);
            config.planeDirectory = file.parent().path();
            return config;
        } catch (Exception e) {
            System.err.println("Failed to read campaign config " + path + ": " + e);
            return new CampaignConfig();
        }
    }
}
