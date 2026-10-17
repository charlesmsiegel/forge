package forge.adventure.campaign;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Json;
import forge.adventure.data.BiomeData;
import forge.adventure.world.BiomeStructure;
import forge.adventure.world.ColorMap;
import org.testng.annotations.Test;
import javax.imageio.ImageIO;
import java.io.File;
import static org.testng.Assert.*;

public class BiomeStructureTest extends AdventureTestBase {
    private ColorMap image(String path) throws Exception {
        var image = ImageIO.read(new File(assetsDir() + "res/adventure/common/" + path));
        ColorMap map = new ColorMap(image.getWidth(), image.getHeight());
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int rgb = image.getRGB(x, y); map.setColor(x, y, new Color((rgb << 8) | (rgb >>> 24)));
        }
        return map;
    }
    @Test(timeOut = 15000) public void authoredStructuresHandlePartialAndRectangularChunks() throws Exception {
        for (String color : new String[]{"white", "blue", "black", "red", "green", "shandalar_reborn"}) {
            var biome = new Json().fromJson(BiomeData.class, new FileHandle(PLANE_DIR + "world/biomes/" + color + ".json"));
            if (biome.structures == null) continue;
            for (var data : biome.structures) {
                BiomeStructure structure = new BiomeStructure(data, 12345, 182, 173);
                structure.initialize(image(data.sourcePath), data.maskPath == null || data.maskPath.isBlank() ? null : image(data.maskPath));
                assertNotNull(structure.image);
                for (int x = 0; x < (int)(data.width * 182); x++)
                    for (int y = 0; y < (int)(data.height * 173); y++) assertTrue(structure.objectID(x, y) >= -1);
            }
        }
    }
}
