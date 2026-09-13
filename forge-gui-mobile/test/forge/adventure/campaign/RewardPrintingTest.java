package forge.adventure.campaign;

import com.badlogic.gdx.utils.Array;
import forge.adventure.data.RewardData;
import forge.adventure.util.Reward;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Set-themed card rewards (e.g. Stronghold loot) must hand out printings from the requested
 * edition, not the default printing of the same card name.
 */
public class RewardPrintingTest extends AdventureTestBase {

    @Test
    public void editionFilteredRewardsUsePrintingsFromThatEdition() {
        RewardData data = new RewardData();
        data.type = "card";
        data.count = 12;
        data.editions = new String[]{"STH"};
        data.rarity = new String[]{"Common", "Uncommon", "Rare"};

        Array<Reward> rewards = data.generate(false, true);

        assertEquals(rewards.size, 12);
        for (Reward reward : rewards) {
            assertNotNull(reward.getCard());
            assertEquals(reward.getCard().getEdition(), "STH", reward.getCard().getName() + " printed as " + reward.getCard().getEdition());
        }
    }
}
