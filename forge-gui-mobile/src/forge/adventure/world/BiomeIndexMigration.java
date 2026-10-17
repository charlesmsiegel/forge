package forge.adventure.world;

/** Biomes keep their old indices when appended; the road bit moves after the new last biome. */
public final class BiomeIndexMigration {
    private BiomeIndexMigration() { }
    public static void remapRoadBit(long[][] map, int oldCount, int newCount) {
        if (oldCount < 1 || newCount < oldCount || newCount > 62)
            throw new IllegalArgumentException("Biome migration only supports appending stable biome indices");
        if (oldCount == newCount) return;
        long oldRoad = 1L << oldCount, newRoad = 1L << newCount;
        for (long[] column : map) for (int y = 0; y < column.length; y++)
            if ((column[y] & oldRoad) != 0) column[y] = (column[y] & ~oldRoad) | newRoad;
    }
}
