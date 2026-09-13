package forge.adventure.campaign;

import forge.adventure.player.AdventurePlayer;
import forge.adventure.util.SaveFileData;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Milestone 7: life carries between duels inside a persistent-life dungeon, using the existing
 * Adventure life total (no second HP system).
 */
public class DungeonLifeTest extends AdventureTestBase {

    private static AdventurePlayer player() {
        AdventurePlayer player = newPlayer(deckOf(card("Lightning Bolt", "M10"), 3, card("Mountain", "M10"), 37));
        assertEquals(player.getMaxLife(), 12);
        return player;
    }

    @Test
    public void enteringTheDungeonStartsAtMaximumLife() {
        AdventurePlayer player = player();
        player.setLife(4);
        DungeonLife.onEnter(player);
        assertEquals(player.getLife(), 12);
    }

    @Test
    public void winningCarriesTheEndingLifeIntoTheNextDuel() {
        AdventurePlayer player = player();
        DungeonLife.onEnter(player);
        DungeonLife.onDuelEnd(player, true, 5);
        assertEquals(player.getLife(), 5, "next duel starts at the life the last one ended with");
        DungeonLife.onDuelEnd(player, true, 2);
        assertEquals(player.getLife(), 2);
    }

    @Test
    public void lifeGainAboveTheMaximumIsKept() {
        AdventurePlayer player = player();
        DungeonLife.onEnter(player);
        DungeonLife.onDuelEnd(player, true, 25);
        assertEquals(player.getLife(), 25, "Stream of Life style gains matter across the dungeon");
    }

    @Test
    public void losingLeavesTheDefeatHandlingToForge() {
        AdventurePlayer player = player();
        DungeonLife.onEnter(player);
        DungeonLife.onDuelEnd(player, false, 0);
        assertEquals(player.getLife(), 12, "a loss does not write the (zero) ending life back");
        assertFalse(player.defeated(), "Forge's defeat handling then applies the configured life loss");
        assertTrue(player.getLife() < 12);
    }

    @Test
    public void carriedLifeSurvivesSaveAndLoad() {
        AdventurePlayer player = player();
        DungeonLife.onDuelEnd(player, true, 7);
        SaveFileData data = player.save();
        AdventurePlayer loaded = new AdventurePlayer();
        loaded.load(data);
        assertEquals(loaded.getLife(), 7);
        assertEquals(loaded.getMaxLife(), 12);
    }
}
