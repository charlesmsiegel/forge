package forge.adventure.campaign;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class RealmProgressTest {
    static HistoricalRealm realm() {
        HistoricalRealm realm = new HistoricalRealm();
        realm.id = "rath";
        realm.name = "Tempest Block";
        realm.chapters = new HistoricalRealm.Chapter[3];
        String[] ids = {"tempest", "stronghold", "exodus"};
        String[] sets = {"TMP", "STH", "EXO"};
        for (int i = 0; i < ids.length; i++) {
            HistoricalRealm.Chapter chapter = new HistoricalRealm.Chapter();
            chapter.id = ids[i];
            chapter.name = ids[i];
            chapter.editions = new String[]{sets[i]};
            chapter.boosterEdition = sets[i];
            chapter.entryMap = "maps/map/" + ids[i] + ".tmx";
            chapter.maps = new String[]{chapter.entryMap};
            realm.chapters[i] = chapter;
        }
        return realm;
    }

    @Test
    public void checkpointsAdvanceAtVictoryAndSurviveRetreat() {
        HistoricalRealm realm = realm();
        RealmProgress progress = new RealmProgress();
        progress.start(realm, null);
        assertEquals(progress.currentChapter(realm).id, "tempest");
        assertFalse(progress.canComplete(realm, "stronghold"));
        progress.complete(realm, "tempest");
        assertFalse(progress.canComplete(realm, "tempest"), "duplicate victory is not another clear");
        progress.leave();
        progress.start(realm, null);
        assertEquals(progress.currentChapter(realm).id, "stronghold");
        progress.leave();
        progress.start(realm, null);
        assertEquals(progress.currentChapter(realm).id, "stronghold");
    }

    @Test
    public void advancingRequiresVictoryAndFullReplayRetainsItsCheckpoint() {
        HistoricalRealm realm = realm();
        RealmProgress progress = new RealmProgress();
        progress.start(realm, null);
        assertThrows(IllegalStateException.class, () -> progress.advance(realm));
        for (String id : new String[]{"tempest", "stronghold", "exodus"}) {
            progress.complete(realm, id);
            if (!id.equals("exodus")) progress.advance(realm);
        }
        assertTrue(progress.isBlockComplete());
        assertTrue(progress.isRunComplete());
        progress.leave();
        progress.start(realm, "*");
        assertEquals(progress.currentChapter(realm).id, "tempest");
        progress.complete(realm, "tempest");
        progress.advance(realm);
        progress.leave();
        RealmProgress loaded = new RealmProgress();
        loaded.load(progress.save());
        loaded.start(realm, null);
        assertEquals(loaded.currentChapter(realm).id, "stronghold");
    }

    @Test
    public void singleChapterReplayDoesNotAdvanceOrEraseBlockAchievements() {
        HistoricalRealm realm = realm();
        RealmProgress progress = clearedBlock(realm);
        progress.start(realm, "stronghold");
        progress.complete(realm, "stronghold");
        assertTrue(progress.isRunComplete());
        assertThrows(IllegalStateException.class, () -> progress.advance(realm));
        progress.leave();
        progress.start(realm, null);
        assertEquals(progress.currentChapter(realm).id, "stronghold");
        assertTrue(progress.isBlockComplete());
        assertTrue(progress.isSingleChapter());
    }

    @Test
    public void serializedBossVictoryRetainsCompletionGuardAndNextCheckpoint() {
        HistoricalRealm realm = realm();
        RealmProgress progress = new RealmProgress();
        progress.start(realm, null);
        progress.complete(realm, "tempest");
        RealmProgress loaded = new RealmProgress();
        loaded.load(progress.save());
        assertFalse(loaded.canComplete(realm, "tempest"));
        assertEquals(loaded.currentChapter(realm).id, "tempest");
        loaded.advance(realm);
        assertEquals(loaded.currentChapter(realm).id, "stronghold");
    }

    @Test
    public void lockedUnknownAndConcurrentSelectionsDoNotMutateProgress() {
        HistoricalRealm realm = realm();
        RealmProgress progress = new RealmProgress();
        assertThrows(IllegalArgumentException.class, () -> progress.start(realm, "stronghold"));
        assertThrows(IllegalArgumentException.class, () -> progress.start(realm, "*"));
        progress.start(realm, null);
        assertThrows(IllegalStateException.class, () -> progress.start(realm, null));
        assertThrows(IllegalStateException.class, () -> progress.complete(realm, "missing"));
        assertEquals(progress.currentChapter(realm).id, "tempest");
        HistoricalRealm changed = realm();
        changed.chapters[0].id = "renamed";
        assertThrows(IllegalArgumentException.class, () -> progress.currentChapter(changed));
    }

    @Test
    public void malformedDefinitionsRejectDuplicateIdsAndSharedMaps() {
        HistoricalRealm realm = realm();
        realm.validate();
        realm.chapters[1].id = "tempest";
        assertThrows(IllegalArgumentException.class, realm::validate);
        realm = realm();
        realm.chapters[1].maps = new String[]{realm.chapters[0].entryMap};
        HistoricalRealm invalid = realm;
        assertThrows(IllegalArgumentException.class, invalid::validate);
    }

    private static RealmProgress clearedBlock(HistoricalRealm realm) {
        RealmProgress progress = new RealmProgress();
        progress.start(realm, null);
        for (int i = 0; i < realm.chapters.length; i++) {
            progress.complete(realm, realm.chapters[i].id);
            if (i + 1 < realm.chapters.length) progress.advance(realm);
        }
        progress.leave();
        return progress;
    }
}
