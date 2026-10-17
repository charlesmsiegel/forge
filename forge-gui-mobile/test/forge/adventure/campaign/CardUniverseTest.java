package forge.adventure.campaign;

import forge.adventure.data.ConfigData;
import forge.game.GameFormat;
import forge.deck.Deck;
import org.testng.annotations.Test;
import java.time.LocalDate;
import java.util.List;
import static org.testng.Assert.*;

public class CardUniverseTest extends AdventureTestBase {
    private CampaignConfig config() {
        CampaignConfig config = new CampaignConfig();
        config.campaignId = "universe-test";
        config.universe.allowedEditions = new String[]{"TMP", "STH", "EXO"};
        return config;
    }
    private ConfigData plane() {
        ConfigData data = new ConfigData();
        data.restrictedCards = new String[]{"Sol Ring"};
        return data;
    }
    @Test public void snapshotDoesNotFollowMutablePresetOrConfig() {
        CampaignConfig config = config();
        GameFormat preset = new GameFormat("Example", List.of("TMP"), List.of("Counterspell"));
        CardUniverse universe = CardUniverse.capture(config, plane(), preset, LocalDate.of(2026, 10, 16));
        config.cards.bannedCards = new String[]{"Shock"};
        config.universe.allowedEditions[0] = "M21";
        CardUniverse restored = new CardUniverse();
        restored.load(universe.save());
        assertFalse(restored.isLegal(card("Counterspell", "TMP")));
        assertTrue(restored.isLegal(card("Shock", "STH")));
        assertTrue(restored.isLegal(card("Shock", "M21")), "Legality follows the name, not the printing");
        assertFalse(restored.allowsPrinting(card("Shock", "M21")));
        assertFalse(restored.isLegal(card("Lightning Bolt", "LEA")));
        assertTrue(restored.isLegal(card("Island", "M21")));
        assertTrue(restored.allowsEdition("TMP"));
        assertFalse(restored.allowsEdition("M21"));
    }
    @Test public void anteRestorationAndExplicitBanPrecedence() {
        CampaignConfig config = config();
        config.universe.allowedEditions = new String[]{"LEA"};
        GameFormat preset = new GameFormat("Ante ban", List.of("LEA"), List.of("Contract from Below", "Black Lotus"));
        CardUniverse restoredAnte = CardUniverse.capture(config, plane(), preset, LocalDate.now());
        assertTrue(restoredAnte.isLegal(card("Contract from Below", "LEA")));
        assertFalse(restoredAnte.isLegal(card("Black Lotus", "LEA")));
        config.cards.bannedCards = new String[]{"Contract from Below"};
        assertFalse(CardUniverse.capture(config, plane(), preset, LocalDate.now()).isLegal(card("Contract from Below", "LEA")));
    }
    @Test public void effectiveGenerationConfigIsIsolatedAndFrozen() {
        CampaignConfig config = config();
        config.cards.restrictedCards = new String[]{"Shock"};
        ConfigData source = plane();
        CardUniverse snapshot = CardUniverse.capture(config, source, null, LocalDate.now());
        ConfigData effective = snapshot.generationConfig(source);
        assertNotSame(source, effective);
        assertNull(source.allowedEditions);
        assertEquals(effective.allowedEditions.length, 3);
        assertTrue(snapshot.isRestricted("Shock"));
        config.cards.restrictedCards = new String[0];
        CardUniverse loaded = new CardUniverse(); loaded.load(snapshot.save());
        assertTrue(loaded.isRestricted("Shock"));
        assertTrue(List.of(effective.restrictedCards).contains("Sol Ring"));
        assertFalse(List.of(effective.restrictedCards).contains("Shock"));
    }
    @Test public void invalidUniverseFailsBeforeUse() {
        CampaignConfig config = config();
        config.universe.allowedEditions = new String[]{"NOT_A_SET"};
        assertThrows(IllegalArgumentException.class, () -> CardUniverse.capture(config, plane(), null, LocalDate.now()));
        config.universe.allowedEditions = new String[]{"STH"};
        assertThrows(IllegalArgumentException.class, () -> CardUniverse.capture(config, plane(), null, LocalDate.of(1997,1,1)));
    }
    @Test public void stateAndDeckValidatorUseSavedRules() {
        CampaignState previous = CampaignState.instance();
        try {
            CampaignConfig config = config();
            config.player.minDeckSize = 1;
            CampaignState state = new CampaignState(); CampaignState.setInstance(state);
            state.initializeUniverse(config, plane());
            CampaignState loaded = new CampaignState(); loaded.load(state.save());
            CampaignState.setInstance(loaded);
            config.cards.bannedCards = new String[]{"Shock"};
            assertTrue(DeckValidator.problems(deckOf(card("Shock", "M21"), 1), null, config).isEmpty());
            assertFalse(DeckValidator.problems(deckOf(card("Lightning Bolt", "LEA"), 1), null, config).isEmpty());
        } finally { CampaignState.setInstance(previous); }
    }
    @Test public void generatedPacksCannotIntroduceForbiddenPrintings() {
        CampaignState previous = CampaignState.instance();
        CampaignConfig oldConfig = CampaignConfig.instance();
        try {
            CampaignConfig config = config(); config.cards.bannedCards = new String[]{"Counterspell"};
            CampaignConfig.setInstance(config);
            CampaignState state = new CampaignState(); CampaignState.setInstance(state);
            state.initializeUniverse(config, plane());
            Deck pack = deckOf(card("Shock", "STH"), 1, card("Counterspell", "TMP"), 1, card("Shock", "M21"), 1);
            state.filterGeneratedPack(pack);
            assertEquals(pack.getMain().countAll(), 1);
            assertEquals(pack.getMain().count(card("Shock", "STH")), 1);
            assertThrows(IllegalArgumentException.class, () -> RegionCompletion.createBoosters("M21", 1, null));
            assertFalse(RegionCompletion.createBoosters("STH", 1, null).isEmpty());
        } finally { CampaignState.setInstance(previous); CampaignConfig.setInstance(oldConfig); }
    }

    @Test public void generationEditionExclusionsDoNotChangeOwnedCardLegality() {
        CampaignConfig config = config(); config.universe.allowedEditions = new String[]{"TMP", "STH"};
        ConfigData source = plane(); source.restrictedEditions = new String[]{"TMP"};
        CardUniverse snapshot = CardUniverse.capture(config, source, null, LocalDate.now());
        assertTrue(snapshot.isLegal(card("Muscle Sliver", "TMP")));
        assertFalse(snapshot.allowsPrinting(card("Muscle Sliver", "TMP")));
        assertTrue(snapshot.allowsPrinting(card("Shock", "STH")));
        CardUniverse loaded = new CardUniverse(); loaded.load(snapshot.save());
        assertTrue(loaded.isLegal(card("Muscle Sliver", "TMP")));
        assertFalse(loaded.allowsPrinting(card("Muscle Sliver", "TMP")));
        config.universe.allowedEditions = new String[]{"TMP"};
        assertTrue(CardUniverse.capture(config, source, null, LocalDate.now()).isLegal(card("Muscle Sliver", "TMP")));
    }

    @Test public void configuredModernPresetResolvesForgeRules() {
        CampaignConfig config = config(); config.universe.allowedEditions = new String[0];
        config.universe.preset = "Modern";
        CardUniverse snapshot = CardUniverse.capture(config, plane());
        assertTrue(snapshot.isLegal(card("Shock", "STH")));
        assertFalse(snapshot.isLegal(card("Dark Ritual", "LEA")));
        assertTrue(snapshot.isBanned("Skullclamp"));
        config.universe.preset = "Missing format";
        assertThrows(IllegalArgumentException.class, () -> CardUniverse.capture(config, plane()));
    }

    @Test public void setsWithoutBasicsStillGenerateInfrastructureLands() {
        CampaignState previous = CampaignState.instance(); CampaignConfig oldConfig = CampaignConfig.instance();
        forge.adventure.util.Config adventure = forge.adventure.util.Config.instance();
        boolean variants = adventure.getSettingData().useAllCardVariants;
        try {
            CampaignConfig config = config(); config.universe.allowedEditions = new String[]{"STH"};
            CampaignConfig.setInstance(config);
            CampaignState state = new CampaignState(); CampaignState.setInstance(state);
            state.initializeUniverse(config, plane()); adventure.getSettingData().useAllCardVariants = true;
            forge.item.PaperCard plains = forge.adventure.util.CardUtil.getCardByName("Plains");
            assertNotNull(plains, "Small expansions do not remove infrastructure basic lands");
            assertTrue(plains.getRules().getType().isBasicLand());
            assertTrue(state.universeFor(config).isLegal(plains));
            assertNotNull(forge.adventure.util.CardUtil.getCardByNameAndEdition("Plains", plains.getEdition()));
        } finally {
            adventure.getSettingData().useAllCardVariants = variants;
            CampaignState.setInstance(previous); CampaignConfig.setInstance(oldConfig);
        }
    }

}
