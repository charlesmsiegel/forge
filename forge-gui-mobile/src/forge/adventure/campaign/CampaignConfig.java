package forge.adventure.campaign;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
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
    public AnteRules ante = new AnteRules();
    public EconomyRules economy = new EconomyRules();
    public StrongholdRules stronghold = new StrongholdRules();
    public DungeonRules dungeon = new DungeonRules();
    public RivalRules rivals = new RivalRules();

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

    public static class AnteRules implements Serializable {
        public boolean enabled = false;
        public boolean excludeBasicLands = true;
        public int playerAnteCount = 1;
        public int opponentAnteCount = 1;
        public int reclamationRiskCount = 3;
    }

    public static class EconomyRules implements Serializable {
        public float cardSellMultiplier = 0.5f;
        public float shopPriceMultiplier = 1.0f;
        public float boosterPriceMultiplier = 1.0f;
    }

    public static class StrongholdRules implements Serializable {
        public String[] editions = new String[]{"STH"};
        public String boosterEdition = "STH";
        public int firstClearBoxBoosterCount = 36;
        public int repeatClearBoosterCount = 6;
    }

    public static class RivalRules implements Serializable {
        /** Given names and epithets combined into "Name, Epithet" for promoted rivals. */
        public String[] names = new String[]{"Krag", "Vesna", "Torvald", "Ilsa", "Mogrin", "Selene", "Bram", "Nadira"};
        public String[] epithets = new String[]{"Ember-Sneak", "the Cardsharp", "Ante-Taker", "the Vulture", "Deckbreaker", "the Grifter"};
    }

    public static class DungeonRules implements Serializable {
        /** Tiled map property that marks a map as a persistent-life dungeon. */
        public String persistentLifeMapKeyword = "persistentLife";
    }

    public boolean isActive() {
        return campaignId != null && !campaignId.isEmpty();
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
            return json.fromJson(CampaignConfig.class, file);
        } catch (Exception e) {
            System.err.println("Failed to read campaign config " + path + ": " + e);
            return new CampaignConfig();
        }
    }
}
