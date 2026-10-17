package forge.adventure.campaign;

import forge.adventure.data.ConfigData;
import forge.adventure.data.EnemyData;
import org.testng.annotations.Test;
import java.time.LocalDate;
import static org.testng.Assert.*;

public class CampaignTrainingTest extends AdventureTestBase {
    private CampaignConfig config() { return CampaignConfig.load(PLANE_DIR + "campaign.json"); }
    @Test public void skippingAndReloadingNeverLocksAnyColorPath() {
        var config = config(); WorldDeeds deeds = new WorldDeeds();
        assertEquals(config.training.length, 5);
        CampaignTraining.skip(deeds);
        WorldDeeds loaded = new WorldDeeds(); loaded.load(deeds.save());
        assertTrue(loaded.has("training.skipped"));
        for (var path : config.training) {
            CampaignTraining.start(path, loaded);
            assertEquals(CampaignTraining.completed(path, loaded), 0);
            assertTrue(loaded.has("training.started." + path.id));
        }
        assertFalse(loaded.has("seal.broken"));
    }
    @Test public void failedAndRepeatedLessonsDoNotDuplicatePhysicalRewards() {
        CampaignState previous = CampaignState.instance();
        try {
            CampaignState.setInstance(new CampaignState());
            var config = config(); var player = newPlayer(deckOf(card("Mountain", "TMP"),40));
            var universe = CardUniverse.capture(config, new ConfigData(), null, LocalDate.of(2026,10,16));
            WorldDeeds deeds = new WorldDeeds(); var path = config.training[0]; var lesson = path.lessons[0];
            int before = player.getCards().countAll();
            assertNull(CampaignTraining.complete(config, lesson.id, false, player, deeds, universe));
            assertEquals(player.getCards().countAll(), before);
            var reward = CampaignTraining.complete(config, lesson.id, true, player, deeds, universe);
            assertNotNull(reward); assertTrue(universe.allowsPrinting(reward));
            assertEquals(player.getCards().countAll(), before+1);
            WorldDeeds loaded = new WorldDeeds(); loaded.load(deeds.save());
            assertNull(CampaignTraining.complete(config, lesson.id, true, player, loaded, universe));
            for (var remaining : path.lessons) CampaignTraining.complete(config, remaining.id, true, player, loaded, universe);
            assertEquals(CampaignTraining.completed(path, loaded),3);
            assertEquals(player.getCards().countAll(),before+3);
            assertTrue(loaded.has("training.completed." + path.id));
        } finally { CampaignState.setInstance(previous); }
    }
    @Test public void narrowUniverseRewardsAndTrainerReferencesUseRealContent() {
        var config = config(); CampaignTraining.validate(config);
        config.universe.allowedEditions = new String[]{"TMP", "STH", "EXO"};
        var universe = CardUniverse.capture(config, new ConfigData(), null, LocalDate.of(2026,10,16));
        for (var path : config.training) for (var lesson : path.lessons) {
            var reward = CampaignTraining.reward(path,lesson,universe);
            assertNotNull(reward); assertTrue(universe.allowsPrinting(reward));
            assertFalse(reward.getRules().getType().isLand());
            assertTrue(new com.badlogic.gdx.files.FileHandle(PLANE_DIR+path.map).exists());
            var all = new com.badlogic.gdx.utils.Json().fromJson(EnemyData[].class,
                    new com.badlogic.gdx.files.FileHandle(PLANE_DIR+"world/enemies_extra.json"));
            var trainer = java.util.Arrays.stream(all).filter(e -> e.name.equals(lesson.enemy)).findFirst().orElseThrow();
            assertEquals(CampaignTraining.lessonFor(config,trainer).id,lesson.id);
            assertTrue(new com.badlogic.gdx.files.FileHandle(assetsDir()+"res/adventure/common/"+trainer.sprite).exists(),trainer.sprite);
            var deck = forge.deck.io.DeckSerializer.fromFile(new java.io.File(PLANE_DIR+trainer.deck[0]));
            assertEquals(deck.getMain().countAll(),40,trainer.name);
            for (String line : new com.badlogic.gdx.files.FileHandle(PLANE_DIR+trainer.deck[0]).readString().split("\n")) {
                if (!line.matches("[0-9]+ .*\\|.*")) continue;
                String[] fields=line.substring(line.indexOf(' ')+1).split("\\|");
                assertEquals(card(fields[0],fields[1]).getEdition(),fields[1],"Missing exact tutor printing: "+line);
            }
            for (var printing:deck.getMain()) assertFalse(printing.getKey().getRules().isUnsupported());
            var xml = new com.badlogic.gdx.utils.XmlReader().parse(new com.badlogic.gdx.files.FileHandle(PLANE_DIR+path.map));
            boolean found=false;
            for(var prop:xml.getChildrenByNameRecursively("property"))
                if(prop.getAttribute("name").equals("trainingLesson") && prop.getAttribute("value").equals(lesson.id))found=true;
            assertTrue(found,"Trainer actor missing for "+lesson.id);
        }
    }
}
