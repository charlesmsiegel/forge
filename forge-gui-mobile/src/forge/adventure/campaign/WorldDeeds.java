package forge.adventure.campaign;

import forge.adventure.util.SaveFileContent;
import forge.adventure.util.SaveFileData;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/** Accomplishments are explicit deeds, not a generic reputation grind. */
public final class WorldDeeds implements SaveFileContent {
    private final Set<String> flags = new TreeSet<>();
    public boolean has(String flag) { return flags.contains(flag); }
    public Set<String> flags() { return Collections.unmodifiableSet(flags); }
    public boolean grant(String flag) {
        if (flag == null || flag.isBlank()) throw new IllegalArgumentException("Missing world deed");
        return flags.add(flag);
    }
    @Override public SaveFileData save() {
        SaveFileData data = new SaveFileData(); data.store("version", 1);
        data.storeObject("flags", flags.toArray(new String[0])); return data;
    }
    @Override public void load(SaveFileData data) {
        flags.clear(); if (data != null && data.containsKey("flags")) flags.addAll(Arrays.asList((String[])data.readObject("flags")));
    }
}
