package forge.adventure.campaign;

import forge.adventure.util.SaveFileContent;
import forge.adventure.util.SaveFileData;

/** Ordered checkpoints are permanent; clearing a chapter is guarded for the active visit. */
public final class RealmProgress implements SaveFileContent {
    private String checkpoint = "";
    private String currentChapter = "";
    private boolean active;
    private boolean chapterCleared;
    private boolean singleChapter;
    private boolean runComplete;
    private boolean blockComplete;

    /** null continues a checkpoint, '*' starts a full replay, a chapter id starts a single replay. */
    public void start(HistoricalRealm realm, String selection) {
        realm.validate();
        if (active) throw new IllegalStateException("A realm run is already active");
        String target = checkpoint.isEmpty() ? realm.chapters[0].id : checkpoint;
        boolean single = singleChapter;
        if (selection != null) {
            if (!blockComplete) throw new IllegalArgumentException("Finish the block before selecting a replay");
            target = "*".equals(selection) ? realm.chapters[0].id : realm.chapter(selection).id;
            single = !"*".equals(selection);
        }
        realm.chapter(target); // Unknown saved identifiers must fail before any mutation.
        checkpoint = target;
        currentChapter = target;
        singleChapter = single;
        active = true;
        chapterCleared = false;
        runComplete = false;
    }

    public HistoricalRealm.Chapter currentChapter(HistoricalRealm realm) {
        return realm.chapter(currentChapter.isEmpty() ? realm.chapters[0].id : currentChapter);
    }

    public String checkpoint(HistoricalRealm realm) {
        String id = checkpoint.isEmpty() ? realm.chapters[0].id : checkpoint;
        return realm.chapter(id).id;
    }

    public boolean canComplete(HistoricalRealm realm, String chapterId) {
        return active && !chapterCleared && !runComplete && currentChapter(realm).id.equals(chapterId);
    }

    public void complete(HistoricalRealm realm, String chapterId) {
        if (!canComplete(realm, chapterId)) throw new IllegalStateException("Chapter is not eligible for completion: " + chapterId);
        int next = realm.index(chapterId) + 1;
        chapterCleared = true;
        runComplete = singleChapter || next == realm.chapters.length;
        if (!singleChapter) {
            if (next == realm.chapters.length) {
                blockComplete = true;
                checkpoint = realm.chapters[0].id;
            } else checkpoint = realm.chapters[next].id;
        }
    }

    public boolean canAdvance() { return active && chapterCleared && !runComplete; }

    public HistoricalRealm.Chapter nextChapter(HistoricalRealm realm) {
        if (!canAdvance()) throw new IllegalStateException("Defeat the chapter's final opponent before advancing");
        return realm.chapter(checkpoint);
    }

    public void advance(HistoricalRealm realm) {
        currentChapter = nextChapter(realm).id;
        chapterCleared = false;
    }

    public void leave() { active = false; }
    public boolean isActive() { return active; }
    public boolean isSingleChapter() { return singleChapter; }
    public boolean isRunComplete() { return runComplete; }
    public boolean isBlockComplete() { return blockComplete; }

    @Override
    public SaveFileData save() {
        SaveFileData data = new SaveFileData();
        data.store("version", 1);
        data.store("checkpoint", checkpoint);
        data.store("chapter", currentChapter);
        data.store("active", active);
        data.store("cleared", chapterCleared);
        data.store("single", singleChapter);
        data.store("finished", runComplete);
        data.store("blockComplete", blockComplete);
        return data;
    }

    @Override
    public void load(SaveFileData data) {
        checkpoint = data == null ? "" : data.readString("checkpoint");
        currentChapter = data == null ? "" : data.readString("chapter");
        active = data != null && data.readBool("active");
        chapterCleared = data != null && data.readBool("cleared");
        singleChapter = data != null && data.readBool("single");
        runComplete = data != null && data.readBool("finished");
        blockComplete = data != null && data.readBool("blockComplete");
    }
}
