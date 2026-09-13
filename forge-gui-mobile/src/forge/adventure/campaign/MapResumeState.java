package forge.adventure.campaign;

import com.badlogic.gdx.math.Vector2;
import forge.adventure.util.SaveFileContent;
import forge.adventure.util.SaveFileData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The transient part of an authored campaign map. WorldSave already owns player life,
 * inventory and permanent map changes; this snapshot restores location and surviving
 * encounters without treating load as another visit or a retreat.
 */
public class MapResumeState implements SaveFileContent {
    public String rootPoiId = "";
    public String mapPath = "";
    public float playerX;
    public float playerY;
    public final Map<Integer, Vector2> enemies = new LinkedHashMap<>();
    public final Map<Integer, String> rivalIds = new LinkedHashMap<>();

    public MapResumeState() { }

    public MapResumeState(String rootPoiId, String mapPath, float x, float y,
                          Map<Integer, Vector2> enemies) {
        this.rootPoiId = rootPoiId;
        this.mapPath = mapPath;
        playerX = x;
        playerY = y;
        enemies.forEach((id, position) -> this.enemies.put(id, position.cpy()));
    }

    public boolean isActive() {
        return !rootPoiId.isEmpty() && !mapPath.isEmpty();
    }

    @Override
    public void load(SaveFileData data) {
        rootPoiId = "";
        mapPath = "";
        enemies.clear();
        rivalIds.clear();
        if (data == null || !data.containsKey("rootPoiId"))
            return;
        rootPoiId = data.readString("rootPoiId");
        mapPath = data.readString("mapPath");
        playerX = data.readFloat("playerX");
        playerY = data.readFloat("playerY");
        int count = data.readInt("enemyCount");
        for (int i = 0; i < count; i++) {
            SaveFileData enemy = data.readSubData("enemy_" + i);
            enemies.put(enemy.readInt("id"), new Vector2(enemy.readFloat("x"), enemy.readFloat("y")));
            if (enemy.containsKey("rivalId"))
                rivalIds.put(enemy.readInt("id"), enemy.readString("rivalId"));
        }
    }

    @Override
    public SaveFileData save() {
        SaveFileData data = new SaveFileData();
        data.store("version", 1);
        data.store("rootPoiId", rootPoiId);
        data.store("mapPath", mapPath);
        data.store("playerX", playerX);
        data.store("playerY", playerY);
        data.store("enemyCount", enemies.size());
        int index = 0;
        for (Map.Entry<Integer, Vector2> entry : enemies.entrySet()) {
            SaveFileData enemy = new SaveFileData();
            enemy.store("id", entry.getKey());
            enemy.store("x", entry.getValue().x);
            enemy.store("y", entry.getValue().y);
            if (rivalIds.containsKey(entry.getKey()))
                enemy.store("rivalId", rivalIds.get(entry.getKey()));
            data.store("enemy_" + index++, enemy);
        }
        return data;
    }
}
