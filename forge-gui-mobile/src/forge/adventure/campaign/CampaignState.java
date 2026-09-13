package forge.adventure.campaign;

import forge.adventure.player.AdventurePlayer;
import forge.adventure.util.SaveFileContent;
import forge.adventure.util.SaveFileData;
import forge.deck.CardPool;

/**
 * Persistent Shandalar Reborn campaign state stored as its own sub-blob of the world save
 * (rivals, expedition, Stronghold runs). Grows milestone by milestone; every section is
 * optional on load so older saves keep working.
 */
public class CampaignState implements SaveFileContent {
    public static final int SAVE_VERSION = 1;

    private static CampaignState current = new CampaignState();

    public static CampaignState instance() {
        return current;
    }

    /** Replace the live state (new game, load, tests). */
    public static void setInstance(CampaignState state) {
        current = state == null ? new CampaignState() : state;
    }

    public void clear() {
    }

    /** The cards the player may currently use for deck building. Filtered during expeditions (M4). */
    public CardPool availableCards(AdventurePlayer player) {
        return player.getCards();
    }

    @Override
    public void load(SaveFileData data) {
        clear();
        if (data == null)
            return;
    }

    @Override
    public SaveFileData save() {
        SaveFileData data = new SaveFileData();
        data.store("version", SAVE_VERSION);
        return data;
    }
}
