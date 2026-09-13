package forge.adventure.campaign;

import forge.adventure.player.AdventurePlayer;

/**
 * Persistent-life dungeons (MVP.md 30): the player enters at maximum life, the life total at the
 * end of each won duel carries into the next one (so life gain matters across a dungeon), and
 * losing ejects the player through Forge Adventure's normal defeat path. No second HP system:
 * this only writes the match result back into AdventurePlayer.life.
 */
public final class DungeonLife {
    private DungeonLife() {
    }

    /** Entering a persistent-life dungeon from the world map: start at full life. */
    public static void onEnter(AdventurePlayer player) {
        player.resetToMaxLife();
        CampaignLog.event("dungeon_enter").with("life", player.getLife()).with("maxLife", player.getMaxLife()).write();
    }

    /**
     * A duel inside a persistent-life dungeon ended. On a win the ending life (which may exceed
     * the maximum through life gain) becomes the player's current life. Losses are left to
     * Forge's defeat handling (life loss + ejection).
     */
    public static void onDuelEnd(AdventurePlayer player, boolean playerWon, int endingLife) {
        if (!playerWon)
            return;
        int life = Math.max(1, endingLife);
        player.setLife(life);
        CampaignLog.event("dungeon_duel_life").with("life", life).with("maxLife", player.getMaxLife()).write();
    }
}
