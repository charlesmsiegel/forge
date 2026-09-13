package forge.adventure.campaign;

import forge.Forge;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.scene.DeckEditScene;
import forge.adventure.stage.GameStage;
import forge.adventure.util.Current;
import forge.deck.CardPool;

import java.util.List;

/**
 * Blocks ordinary fights while the active deck is illegal under the campaign rules and sends the
 * player to the deck editor instead (MVP.md §31). Nothing is ever filled in silently.
 */
public final class DeckRepairGate {
    private DeckRepairGate() {
    }

    /** Cards the player may build with right now (collection, or the expedition pool while away). */
    public static CardPool availableCards(AdventurePlayer player) {
        return CampaignState.instance().availableCards(player);
    }

    public static List<String> currentProblems() {
        AdventurePlayer player = Current.player();
        return DeckValidator.problems(player.getSelectedDeck(), availableCards(player), CampaignConfig.instance());
    }

    /**
     * @return true when the fight may start. Otherwise a dialog explaining the problems is shown
     *         with an option to open the deck editor, and false is returned.
     */
    public static boolean ensureDeckLegal(GameStage stage) {
        if (!CampaignConfig.instance().isActive())
            return true;
        List<String> problems = currentProblems();
        if (problems.isEmpty())
            return true;
        StringBuilder message = new StringBuilder("[RED]Your deck is not legal.[WHITE] Repair it before fighting:\n");
        for (String p : problems)
            message.append("- ").append(p).append('\n');
        CampaignLog.event("deck_invalid").with("problems", problems).write();
        stage.showChoiceDialog(message.toString(), "Edit deck", DeckRepairGate::openDeckEditor, "Later", null);
        return false;
    }

    public static void openDeckEditor() {
        DeckEditScene editScene = DeckEditScene.getInstance(null);
        editScene.loadEvent(null);
        Forge.switchScene(editScene);
    }
}
