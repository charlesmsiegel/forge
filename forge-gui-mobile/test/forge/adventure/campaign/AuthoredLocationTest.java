package forge.adventure.campaign;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.XmlReader;
import org.testng.annotations.Test;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import static org.testng.Assert.*;

public class AuthoredLocationTest extends AdventureTestBase {
    @Test public void authoredPlacesHaveDistinctTerrainAndPortableSources() throws Exception {
        var manifest = new JsonReader().parse(new FileHandle(PLANE_DIR + "world/authored_locations.json"));
        assertEquals(manifest.size, 14); Set<String> terrain = new HashSet<>();
        for (var entry : manifest) {
            assertFalse(entry.getString("description").isBlank());
            assertTrue(new FileHandle(assetsDir() + "res/adventure/common/maps/map/" + entry.getString("source")).exists());
            var file = new FileHandle(PLANE_DIR + entry.getString("map")); var map = new XmlReader().parse(file);
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            for (var tileset : map.getChildrenByName("tileset")) {
                assertTrue(file.parent().child(tileset.getAttribute("source")).exists());
                hash.update(tileset.getAttribute("source").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            for (var layer : map.getChildrenByNameRecursively("layer")) hash.update(layer.getChildByName("data").getText().replaceAll("\\s", "").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertTrue(terrain.add(java.util.HexFormat.of().formatHex(hash.digest())), "Repeated terrain for " + entry.getString("map"));
        }
    }
    @Test public void storyPlacesSupportExplorationAndLoreWithoutImportedBattleCheats() {
        var config = CampaignConfig.load(PLANE_DIR + "campaign.json");
        for (var encounter : config.story.encounters) {
            var map = new XmlReader().parse(new FileHandle(PLANE_DIR + encounter.map)); int enemies = 0, lore = 0, bosses = 0;
            for (var property : map.getChildrenByNameRecursively("property")) {
                String name = property.getAttribute("name");
                assertFalse(name.equals("dungeonEffect"), "Source battle effect leaked into " + encounter.map);
                if (name.equals("enemy")) enemies++;
                if (name.equals("campaignLore")) lore++;
                if (name.equals("storyEncounter") && property.getAttribute("value").equals(encounter.id)) bosses++;
            }
            assertTrue(enemies >= 3, encounter.id + " needs ordinary exploration encounters");
            assertEquals(lore, 1, encounter.id + " needs one visible lore interaction"); assertEquals(bosses, 1);
        }
    }
}
