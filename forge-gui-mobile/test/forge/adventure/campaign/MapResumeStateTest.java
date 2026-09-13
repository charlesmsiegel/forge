package forge.adventure.campaign;

import com.badlogic.gdx.math.Vector2;
import forge.adventure.util.SaveFileData;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.testng.Assert.*;

public class MapResumeStateTest {
    @Test
    public void savedMapRetainsPositionAndOnlySurvivingEncounters() {
        Map<Integer, Vector2> enemies = new LinkedHashMap<>();
        enemies.put(201, new Vector2(16.5f, 42));
        MapResumeState snapshot = new MapResumeState("stronghold-poi", "maps/stronghold_keep.tmx",
                123.5f, 456, enemies);
        snapshot.rivalIds.put(201, "rival-five");
        enemies.get(201).set(0, 0);
        enemies.put(202, new Vector2(99, 99));

        MapResumeState loaded = new MapResumeState();
        loaded.load(snapshot.save());
        assertTrue(loaded.isActive());
        assertEquals(loaded.rootPoiId, "stronghold-poi");
        assertEquals(loaded.mapPath, "maps/stronghold_keep.tmx");
        assertEquals(loaded.playerX, 123.5f);
        assertEquals(loaded.playerY, 456f);
        assertEquals(loaded.enemies.size(), 1);
        assertEquals(loaded.enemies.get(201), new Vector2(16.5f, 42));
        assertEquals(loaded.rivalIds.get(201), "rival-five", "roster selections survive reload");
        assertFalse(loaded.enemies.containsKey(202), "defeated encounters must not reappear");
    }

    @Test
    public void legacyWorldMapSavesHaveNoMapToResume() {
        MapResumeState loaded = new MapResumeState();
        loaded.load(null);
        assertFalse(loaded.isActive());
        loaded.load(new SaveFileData());
        assertFalse(loaded.isActive());
    }
}
