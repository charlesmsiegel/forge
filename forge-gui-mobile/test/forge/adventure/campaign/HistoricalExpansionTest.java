package forge.adventure.campaign;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.XmlReader;
import forge.adventure.data.ConfigData;
import forge.adventure.data.WorldData;
import forge.deck.Deck;
import org.testng.annotations.Test;
import java.util.List;
import static org.testng.Assert.*;

public class HistoricalExpansionTest extends AdventureTestBase {
    @Test public void presetCopyKeepsTheLoadedPlanesContentRoot() throws java.io.IOException {
        var directory = java.nio.file.Files.createTempDirectory("campaign-content-root");
        var map = directory.resolve("maps/preset-root-only.tmx");
        var configFile = directory.resolve("campaign.json");
        try {
            java.nio.file.Files.createDirectories(map.getParent());
            java.nio.file.Files.writeString(map, "<map/>");
            java.nio.file.Files.writeString(configFile, "{}");
            var source = CampaignConfig.load(configFile.toString());
            var realm = new HistoricalRealm();
            var chapter = new HistoricalRealm.Chapter();
            chapter.maps = new String[]{"maps/preset-root-only.tmx"};
            realm.chapters = new HistoricalRealm.Chapter[]{chapter};
            String originalPreset = source.universe.preset;
            var copy = CampaignSetup.withPreset(source, "Modern");
            copy.validateRealmContent(realm);
            java.nio.file.Files.delete(map);
            assertThrows(IllegalArgumentException.class, () -> copy.validateRealmContent(realm));
            assertEquals(source.universe.preset, originalPreset);
        } finally {
            java.nio.file.Files.deleteIfExists(map);
            java.nio.file.Files.deleteIfExists(map.getParent());
            java.nio.file.Files.deleteIfExists(configFile);
            java.nio.file.Files.deleteIfExists(directory);
        }
    }
    @Test public void additionalRealmsHaveWorkingProductsMapsAndSetSpecificOpponents() {
        var config = CampaignConfig.load(PLANE_DIR + "campaign.json");
        assertEquals(config.realm("fallen-empires").chapters.length, 1);
        assertEquals(config.realm("mirrodin").chapters.length, 3);
        for (String id : List.of("fallen-empires", "mirrodin")) {
            var realm = config.realm(id); config.validateRealmContent(realm);
            for (var chapter : realm.chapters) {
                assertNotNull(RegionCompletion.boosterTemplate(chapter.boosterEdition));
                var map = new XmlReader().parse(new FileHandle(PLANE_DIR + chapter.entryMap)); int enemies = 0, bosses = 0;
                for (var object : map.getChildrenByNameRecursively("object")) {
                    String enemy = "", completes = "";
                    for (var property : object.getChildrenByNameRecursively("property")) {
                        if (property.getAttribute("name").equals("enemy")) enemy = property.getAttribute("value");
                        if (property.getAttribute("name").equals("completesChapter")) completes = property.getAttribute("value");
                    }
                    if (!enemy.isBlank()) {
                        var data = WorldData.getEnemy(enemy); assertNotNull(data, enemy); enemies++;
                        Deck deck = forge.deck.io.DeckSerializer.fromFile(new java.io.File(PLANE_DIR + data.deck[0]));
                        assertTrue(deck.getMain().countAll() >= 40);
                        for (var printing : deck.getMain()) assertTrue(printing.getKey().getRules().getType().isBasicLand()
                                || java.util.Arrays.asList(chapter.editions).contains(printing.getKey().getEdition()), enemy + " contains an out-of-set spell");
                    }
                    if (completes.equals(chapter.id)) bosses++;
                }
                assertEquals(enemies, 4); assertEquals(bosses, 1);
            }
        }
    }
    @Test public void newRealmCompletionSavesRewardsAndDoesNotInventOtherRealmProgress() {
        var original = CampaignConfig.instance(); var config = CampaignConfig.load(PLANE_DIR + "campaign.json");
        CampaignConfig.setInstance(config); var state = new CampaignState(); CampaignState.setInstance(state);
        try {
            state.initializeUniverse(config, new ConfigData());
            var player = newPlayer(deckOf(card("Forest", "TMP"), 40));
            state.enterRealm("fallen-empires", null, player, "");
            assertEquals(state.onChapterCompleted("fallen-empires", "fallen-empires", player).size(), 60);
            assertTrue(state.onChapterCompleted("fallen-empires", "fallen-empires", player).isEmpty());
            assertTrue(state.deeds().has("realm.fallen-empires.completed"));
            assertEquals(state.progression().fullClears("mirrodin"), 0);
            var restored = new CampaignState(); restored.load(state.save());
            assertTrue(restored.realmProgressSnapshot("fallen-empires").isBlockComplete());
            assertFalse(restored.realmProgressSnapshot("mirrodin").isBlockComplete());
        } finally { CampaignConfig.setInstance(original); CampaignState.setInstance(new CampaignState()); }
    }
    @Test public void modernCanEnterMirrodinButCannotReceiveFallenEmpiresProducts() {
        var original = CampaignConfig.instance(); var config = CampaignSetup.withPreset(CampaignConfig.load(PLANE_DIR + "campaign.json"), "Modern");
        CampaignConfig.setInstance(config); var state = new CampaignState(); CampaignState.setInstance(state);
        try {
            state.initializeUniverse(config, new ConfigData()); var player = newPlayer(deckOf(card("Forest", "M10"), 40));
            assertThrows(IllegalArgumentException.class, () -> state.enterRealm("fallen-empires", null, player, ""));
            assertFalse(state.expedition().isActive());
            assertEquals(state.enterRealm("mirrodin", null, player, "").id, "mirrodin");
        } finally { CampaignConfig.setInstance(original); CampaignState.setInstance(new CampaignState()); }
    }
    @Test public void mirrodinCheckpointsSurviveLoadAndGrantBlockMasteryOnlyAtTheEnd() {
        var original = CampaignConfig.instance(); var config = CampaignConfig.load(PLANE_DIR + "campaign.json");
        CampaignConfig.setInstance(config); var state = new CampaignState(); CampaignState.setInstance(state);
        try {
            state.initializeUniverse(config, new ConfigData()); var player = newPlayer(deckOf(card("Forest", "MRD"), 40));
            state.enterRealm("mirrodin", null, player, "");
            assertEquals(state.onChapterCompleted("mirrodin", "mirrodin", player).size(), 36);
            assertEquals(state.progression().fullClears("mirrodin"), 0);
            var restored = new CampaignState(); restored.load(state.save()); CampaignState.setInstance(restored);
            assertEquals(restored.advanceRealm("mirrodin").id, "darksteel");
            assertEquals(restored.onChapterCompleted("mirrodin", "darksteel", player).size(), 36);
            assertEquals(restored.progression().fullClears("mirrodin"), 0);
            assertEquals(restored.advanceRealm("mirrodin").id, "fifth-dawn");
            assertEquals(restored.onChapterCompleted("mirrodin", "fifth-dawn", player).size(), 36);
            assertEquals(restored.progression().fullClears("mirrodin"), 1);
            assertTrue(restored.deeds().has("realm.mirrodin.completed"));
            assertTrue(restored.progression().protectionSlots() >= 2);
            assertTrue(restored.onChapterCompleted("mirrodin", "fifth-dawn", player).isEmpty());
        } finally { CampaignConfig.setInstance(original); CampaignState.setInstance(new CampaignState()); }
    }
    @Test public void newLimitedLeaguesResolveToRealBlocksAndReuseLocalEntry() {
        var original = CampaignConfig.instance(); var config = CampaignConfig.load(PLANE_DIR + "campaign.json");
        CampaignConfig.setInstance(config); var state = new CampaignState(); CampaignState.setInstance(state);
        try {
            state.initializeUniverse(config, new ConfigData()); var player = newPlayer(deckOf(card("Forest", "MRD"), 40));
            for (String id : List.of("fallen-empires", "mirrodin")) {
                var organizer = config.limitedOrganizer(id + "-limited");
                var event = CampaignLimited.createOrResume(player, organizer, id + "|organizer", 901,
                        forge.adventure.util.AdventureEventController.EventFormat.Sealed, 20000);
                assertTrue(event.packConfiguration.length >= 6);
                assertSame(CampaignLimited.createOrResume(player, organizer, id + "|organizer", 901,
                        forge.adventure.util.AdventureEventController.EventFormat.Sealed, 20001), event);
            }
        } finally { CampaignConfig.setInstance(original); CampaignState.setInstance(new CampaignState()); }
    }

    @Test public void journalExplainsWhenARealmIsOutsideTheFrozenUniverse() {
        var config = CampaignSetup.withPreset(CampaignConfig.load(PLANE_DIR + "campaign.json"), "Modern");
        var state = new CampaignState(); state.initializeUniverse(config, new ConfigData());
        String text = CampaignJournal.sections(config, state).stream().filter(section -> section.title().equals("Historical realms")).findFirst().orElseThrow().text();
        assertTrue(text.contains("Unavailable in this campaign's card universe"));
        assertTrue(text.contains("Mirrodin"));
    }

}
