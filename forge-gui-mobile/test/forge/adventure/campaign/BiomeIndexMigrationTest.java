package forge.adventure.campaign;

import forge.adventure.world.BiomeIndexMigration;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class BiomeIndexMigrationTest {
    @Test public void oldRoadFlagDoesNotBecomeTheFirstNewColorBiome() {
        long[][] saved = {{1, 2, 6, 4}, {0, 3, 7, 2}};
        BiomeIndexMigration.remapRoadBit(saved, 2, 7);
        assertEquals(saved[0], new long[]{1, 2, 130, 128});
        assertEquals(saved[1], new long[]{0, 3, 131, 2});
        BiomeIndexMigration.remapRoadBit(saved, 7, 7);
        assertEquals(saved[0][2], 130L, "Saved updated count prevents applying the migration twice");
    }
    @Test public void unsafeBiomeReorderingIsRejectedBeforeMutation() {
        long[][] saved = {{6}};
        assertThrows(IllegalArgumentException.class, () -> BiomeIndexMigration.remapRoadBit(saved, 7, 2));
        assertEquals(saved[0][0], 6L);
    }
}
