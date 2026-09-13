package forge.adventure.campaign;

import forge.StaticData;
import forge.adventure.data.DifficultyData;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.util.AdventureModes;
import forge.deck.Deck;
import forge.gui.GuiBase;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import org.testng.annotations.BeforeClass;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Boots the real card database once per JVM (lazy card scripts) with a headless GUI stub so
 * Adventure campaign logic can be tested without libGDX.
 */
public abstract class AdventureTestBase {
    private static boolean initialized = false;

    /** Path of the ShandalarReborn plane relative to the forge-gui-mobile module dir. */
    public static final String PLANE_DIR = assetsDir() + "res/adventure/ShandalarReborn/";

    static String assetsDir() {
        return Files.exists(Paths.get("./res")) ? "./" : "../forge-gui/";
    }

    @BeforeClass
    public synchronized void initializeModel() {
        if (initialized)
            return;
        GuiBase.setInterface(new HeadlessGui(assetsDir()));
        FModel.initialize(null, preferences -> {
            preferences.setPref(FPref.LOAD_CARD_SCRIPTS_LAZILY, true);
            preferences.setPref(FPref.UI_LANGUAGE, "en-US");
            return null;
        });
        CampaignConfig.setInstance(CampaignConfig.load(PLANE_DIR + "campaign.json"));
        initialized = true;
    }

    protected static PaperCard card(String name, String edition) {
        PaperCard pc = StaticData.instance().getCommonCards().getCard(name, edition);
        if (pc == null)
            throw new IllegalArgumentException("No such printing in card db: " + name + "|" + edition);
        return pc;
    }

    protected static PaperCard card(String name) {
        PaperCard pc = StaticData.instance().getCommonCards().getCard(name);
        if (pc == null)
            throw new IllegalArgumentException("No such card in card db: " + name);
        return pc;
    }

    /** A fully created player (as after "New Game") whose collection equals the given deck. */
    protected static AdventurePlayer newPlayer(Deck startingDeck) {
        AdventurePlayer player = new AdventurePlayer();
        DifficultyData difficulty = new DifficultyData();
        difficulty.name = "Normal";
        difficulty.startingLife = 12;
        difficulty.startingMoney = 100;
        player.create("Tester", startingDeck, true, 0, 0, false, false, difficulty, AdventureModes.Standard);
        return player;
    }

    protected static Deck deckOf(Object... cardsAndCounts) {
        Deck deck = new Deck("Test deck");
        for (int i = 0; i < cardsAndCounts.length; i += 2) {
            deck.getMain().add((PaperCard) cardsAndCounts[i], (Integer) cardsAndCounts[i + 1]);
        }
        return deck;
    }
}
