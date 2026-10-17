package forge.adventure.campaign;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.XmlReader;
import forge.adventure.data.BiomeData;
import forge.adventure.data.PointOfInterestData;
import forge.adventure.data.WorldData;
import org.testng.annotations.Test;
import java.util.*;
import static org.testng.Assert.*;

public class WorldLegendContentTest extends AdventureTestBase {
    @Test public void authoredFiveColorWorldAndLegendaryRoutesResolve() {
        CampaignConfig config = CampaignConfig.load(PLANE_DIR + "campaign.json");
        assertEquals(config.worldRules.legacyBiomeCount, 2);
        assertTrue(config.seeds.creatures.length >= 2); assertTrue(config.seeds.locations.length >= 2);
        assertEquals(config.seeds.synergy.length, 3); assertEquals(config.seeds.routes.length, 5);
        String[] biomes = new JsonReader().parse(new FileHandle(PLANE_DIR + "world/world.json")).get("biomesNames").asStringArray();
        assertEquals(biomes.length, 7); assertEquals(biomes[1], "world/biomes/shandalar_reborn.json");
        Map<String, PointOfInterestData> points = new HashMap<>();
        for (PointOfInterestData point : new Json().fromJson(PointOfInterestData[].class, new FileHandle(PLANE_DIR + "world/points_of_interest.json"))) points.put(point.name, point);
        Set<String> names = new HashSet<>();
        for (String path : biomes) {
            FileHandle file = new FileHandle(PLANE_DIR + path);
            if (!file.exists()) file = new FileHandle(assetsDir() + "res/adventure/common/" + path);
            BiomeData biome = new Json().fromJson(BiomeData.class, file); names.add(biome.name);
            if (biome.pointsOfInterest != null) for (String point : biome.pointsOfInterest) assertTrue(points.containsKey(point), "Missing POI " + point);
        }
        assertTrue(names.containsAll(List.of("white", "blue", "black", "red", "green")));
        for (CampaignConfig.LandmarkRoute route : config.seeds.routes) {
            assertEquals(points.get(route.poi).seedSlot, route.slot);
            assertTrue(points.get(route.poi).map.endsWith(route.map));
            XmlReader.Element map = new XmlReader().parse(new FileHandle(PLANE_DIR + route.map));
            boolean found = false;
            for (XmlReader.Element property : map.getChildrenByNameRecursively("property"))
                if (property.getAttribute("name").equals("seedSlot") && property.getAttribute("value").equals(route.slot)) found = true;
            assertTrue(found, "Missing selected-card encounter in " + route.map);
        }
        for (CampaignConfig.SeedCandidate[] pool : List.of(config.seeds.creatures, config.seeds.locations, config.seeds.synergy))
            for (CampaignConfig.SeedCandidate candidate : pool) assertNotNull(WorldData.getEnemy(candidate.enemy), "Missing thematic enemy " + candidate.enemy);
    }
}
