package forge.adventure.campaign;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Data-only historical route. Chapters constrain content, never the player's deck. */
public class HistoricalRealm implements Serializable {
    private static final long serialVersionUID = 1L;
    public String id = "";
    public String name = "";
    public Chapter[] chapters = new Chapter[0];

    public static class Chapter implements Serializable {
        private static final long serialVersionUID = 1L;
        public String id = "";
        public String name = "";
        public String[] editions = new String[0];
        public String entryMap = "";
        public int entryObject = 0;
        public String[] maps = new String[0];
        public String boosterEdition = "";
        /** Optional legacy region counter shared with a standalone expedition. */
        public String legacyRegion = "";
        public int firstClearBoxBoosterCount = 36;
        public int repeatClearBoosterCount = 6;
    }

    public Chapter chapter(String chapterId) {
        return chapters[index(chapterId)];
    }

    public int index(String chapterId) {
        for (int i = 0; i < chapters.length; i++)
            if (chapters[i].id.equals(chapterId)) return i;
        throw new IllegalArgumentException("Unknown chapter '" + chapterId + "' in realm '" + id + "'");
    }

    public void validate() {
        if (id == null || id.isBlank() || name == null || name.isBlank() || chapters == null || chapters.length == 0)
            throw new IllegalArgumentException("Historical realm needs an id, name and chapters");
        Set<String> ids = new HashSet<>();
        Set<String> maps = new HashSet<>();
        for (Chapter chapter : chapters) {
            if (chapter == null || chapter.id == null || chapter.id.isBlank() || !ids.add(chapter.id))
                throw new IllegalArgumentException("Missing or duplicate chapter id in " + id);
            if (chapter.name == null || chapter.name.isBlank() || chapter.maps == null || chapter.maps.length == 0
                    || chapter.entryMap == null || !Arrays.asList(chapter.maps).contains(chapter.entryMap)
                    || chapter.editions == null || chapter.editions.length == 0
                    || chapter.boosterEdition == null || !Arrays.asList(chapter.editions).contains(chapter.boosterEdition)
                    || chapter.entryObject < 0 || chapter.firstClearBoxBoosterCount < 1 || chapter.repeatClearBoosterCount < 0)
                throw new IllegalArgumentException("Invalid chapter definition " + id + "/" + chapter.id);
            for (String edition : chapter.editions)
                if (edition == null || edition.isBlank()) throw new IllegalArgumentException("Empty historical edition");
            for (String map : chapter.maps)
                if (map == null || !map.startsWith("maps/") || map.contains("..") || !maps.add(map))
                    throw new IllegalArgumentException("Invalid or shared chapter map in " + id + ": " + map);
        }
    }
}
