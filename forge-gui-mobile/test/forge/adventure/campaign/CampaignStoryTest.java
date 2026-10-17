package forge.adventure.campaign;

import forge.adventure.data.ConfigData;
import forge.adventure.util.SaveFileData;
import forge.deck.Deck;
import org.testng.annotations.Test;
import java.util.List;
import static org.testng.Assert.*;

public class CampaignStoryTest extends AdventureTestBase {
    private CampaignConfig config() { return CampaignConfig.load(PLANE_DIR + "campaign.json"); }
    private CardUniverse universe(CampaignConfig config) { return CardUniverse.capture(config, new ConfigData()); }
    @Test public void guardiansSealStatusAndReplayAreDeedDriven() {
        CampaignConfig config = config(); WorldDeeds deeds = new WorldDeeds(); CardUniverse snapshot = universe(config);
        var player = newPlayer(deckOf(card("Forest", "TMP"), 40));
        assertEquals(CampaignStory.title(deeds), "Mage");
        assertFalse(CampaignStory.canChallenge(config, "seal", deeds));
        assertThrows(IllegalStateException.class, () -> CampaignStory.resolve(config, "seal", true, player, deeds, snapshot));
        for (String color : List.of("white", "blue", "black", "red", "green")) {
            CampaignStory.resolve(config, "guardian-" + color, false, player, deeds, snapshot);
            assertFalse(deeds.has("guardian." + color));
            CampaignStory.resolve(config, "guardian-" + color, true, player, deeds, snapshot);
        }
        assertEquals(CampaignStory.title(deeds), "Champion of Shandalar");
        assertTrue(CampaignStory.canChallenge(config, "seal", deeds));
        CampaignStory.resolve(config, "seal", true, player, deeds, snapshot);
        assertEquals(CampaignStory.title(deeds), "Planeswalker");
        assertTrue(deeds.has("campaign.completed")); assertTrue(deeds.has("seal.broken"));
        assertTrue(CampaignStory.resolve(config, "seal", true, player, deeds, snapshot).isEmpty());
    }
    @Test public void audiencePursuitConquestAndPhysicalRewardSurviveSaves() {
        CampaignConfig config = config(); WorldDeeds deeds = new WorldDeeds(); CardUniverse snapshot = universe(config);
        var player = newPlayer(deckOf(card("Island", "TMP"), 40));
        assertFalse(CampaignStory.canChallenge(config, "urza-challenge", deeds));
        assertFalse(CampaignStory.canChallenge(config, "urza-conquest", deeds));
        assertTrue(CampaignStory.audience(config, "bolas-challenge", deeds).contains("seal"));
        deeds.grant("seal.broken");
        CampaignStory.resolve(config, "urza-challenge", true, player, deeds, snapshot);
        assertTrue(CampaignStory.canChallenge(config, "urza-conquest", deeds));
        int before = player.getCards().countAll();
        assertEquals(CampaignStory.resolve(config, "urza-conquest", true, player, deeds, snapshot).size(), 1);
        assertEquals(player.getCards().countAll(), before + 1);
        WorldDeeds restored = new WorldDeeds(); restored.load(deeds.save());
        assertTrue(restored.has("legend.urza.conquered"));
        assertTrue(CampaignStory.resolve(config, "urza-conquest", true, player, restored, snapshot).isEmpty());
        assertTrue(CampaignStory.audience(config, "bolas-challenge", restored).contains("Urza"));
        assertThrows(IllegalArgumentException.class, () -> CampaignStory.canChallenge(config, "missing", restored));
    }
    @Test public void bossDecksUseSupportedFrozenPrintingsAndBolasPrivilege() {
        CampaignConfig config = config(); CardUniverse all = universe(config);
        for (String id : List.of("urza-challenge", "urza-conquest", "bolas-challenge", "bolas-conquest")) {
            Deck deck = CampaignStory.deck(config, id, all);
            assertTrue(deck.getMain().countAll() >= 60);
            for (var printing : deck.getMain()) {
                assertTrue(all.isLegal(printing.getKey())); assertFalse(printing.getKey().getRules().isUnsupported());
            }
        }
        assertTrue(CampaignStory.deck(config, "urza-challenge", all).getMain().countByName("Tolarian Academy") > 0);
        assertTrue(CampaignStory.deck(config, "bolas-conquest", all).getMain().countByName("Nicol Bolas") > 4);
        config.cards.bannedCards = new String[]{"Tolarian Academy", "Black Lotus", "Nicol Bolas"};
        CardUniverse banned = universe(config);
        Deck legal = CampaignStory.deck(config, "urza-conquest", banned);
        assertEquals(legal.getMain().countByName("Tolarian Academy"), 0);
        assertEquals(legal.getMain().countByName("Black Lotus"), 0);
        assertTrue(legal.getMain().countAll() >= 60);
    }
    @Test public void illegalUniqueRewardsDoNotEnterCollectionOrRepeat() {
        CampaignConfig config = config(); config.cards.bannedCards = new String[]{"Tolarian Academy"};
        WorldDeeds deeds = new WorldDeeds(); deeds.grant("legend.urza.challenged"); CardUniverse snapshot = universe(config);
        var player = newPlayer(deckOf(card("Island", "TMP"), 40));
        int before = player.getCards().countAll();
        assertTrue(CampaignStory.resolve(config, "urza-conquest", true, player, deeds, snapshot).isEmpty());
        assertTrue(deeds.has("legend.urza.conquered"));
        assertEquals(player.getCards().countAll(), before);
    }
    @Test public void storyMapsContainActualBossesAndExplorationOpponents() {
        CampaignConfig config = config();
        CardUniverse snapshot = universe(config);
        var points = new com.badlogic.gdx.utils.Json().fromJson(forge.adventure.data.PointOfInterestData[].class,
                new com.badlogic.gdx.files.FileHandle(PLANE_DIR + "world/points_of_interest.json"));
        for (var encounter : config.story.encounters) {
            assertNotNull(forge.adventure.data.WorldData.getEnemy(encounter.enemy));
            for (var recipe : encounter.cards) assertNotNull(forge.StaticData.instance().getCommonCards().getCard(recipe.name), "Unknown recipe card " + recipe.name);
            assertEquals(CampaignStory.deck(config, encounter.id, snapshot).getMain().countAll(), 60);
            var file = new com.badlogic.gdx.files.FileHandle(PLANE_DIR + encounter.map);
            assertTrue(file.exists(), "Missing story map " + encounter.map);
            var map = new com.badlogic.gdx.utils.XmlReader().parse(file); boolean boss = false; int enemies = 0;
            for (var property : map.getChildrenByNameRecursively("property")) {
                if (property.getAttribute("name").equals("storyEncounter") && property.getAttribute("value").equals(encounter.id)) boss = true;
                if (property.getAttribute("name").equals("enemy")) enemies++;
            }
            assertTrue(boss, "Missing boss " + encounter.id);
            if (encounter.id.startsWith("urza") || encounter.id.startsWith("bolas")) assertTrue(enemies >= 2, "No exploration opponent");
            if (!encounter.poi.isBlank()) assertTrue(java.util.Arrays.stream(points).anyMatch(point -> point.name.equals(encounter.poi) && point.map.endsWith(encounter.map)));
            if (!encounter.pursuit.isBlank()) assertNotNull(CampaignStory.encounter(config, encounter.pursuit));
        }
    }

    @Test public void narrowUniverseUsesLegalBasicsAndFallbacks() {
        CampaignConfig config = config(); config.universe.allowedEditions = new String[]{"STH"};
        CardUniverse small = universe(config);
        for (var encounter : config.story.encounters) {
            var deck = CampaignStory.deck(config, encounter.id, small);
            assertEquals(deck.getMain().countAll(), 60);
            assertTrue(deck.getMain().toFlatList().stream().anyMatch(card -> card.getRules().getType().isCreature()), "Narrow-universe opponent needs a threat");
            for (var printing : deck.getMain()) assertTrue(small.allowsBespokePrinting(printing.getKey())
                    || (printing.getKey().getRules().getType().isBasicLand() && small.allowsPrinting(printing.getKey())));
        }
    }

}
