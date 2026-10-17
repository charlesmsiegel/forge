package forge.adventure.scene;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.github.tommyettinger.textra.TextraLabel;
import forge.Forge;
import forge.adventure.campaign.CampaignConfig;
import forge.adventure.campaign.CampaignJournal;
import forge.adventure.campaign.CampaignState;
import forge.adventure.util.Controls;
import java.util.List;

/** Campaign information is browsed without changing the player's location or save. */
public final class CampaignJournalScene extends UIScene {
    private final Scene previous;
    private final Table content;
    private final ScrollPane scroller;
    private final List<CampaignJournal.Section> sections;
    private int page;
    public CampaignJournalScene(Scene previous) {
        super(Forge.isLandscapeMode() ? "ui/campaign_journal.json" : "ui/campaign_journal_portrait.json");
        this.previous = previous;
        sections = CampaignJournal.sections(CampaignConfig.instance(), CampaignState.instance());
        Table root = ui.findActor("content"); content = new Table();
        scroller = new ScrollPane(content, Controls.getSkin()); scroller.setScrollingDisabled(true, false);
        scroller.setFadeScrollBars(false); root.add(scroller).grow();
        ui.onButtonPress("return", () -> Forge.switchScene(previous));
        ui.onButtonPress("previous", () -> display((page + sections.size() - 1) % sections.size()));
        ui.onButtonPress("next", () -> display((page + 1) % sections.size()));
    }
    private void display(int selected) {
        page = selected;
        TextraLabel title = ui.findActor("heading");
        title.setText("[BLACK]" + sections.get(page).title() + "  " + (page + 1) + "/" + sections.size());
        content.clear();
        String body = sections.get(page).text();
        if (sections.get(page).title().equals("Your abilities")) body += "\n\nCurrent maximum life: " + forge.adventure.util.Current.player().getMaxLife();
        var text = Controls.newTypingLabel("[WHITE]" + body);
        text.skipToTheEnd(); text.setWrap(true); text.setColor(Color.WHITE);
        Table root = ui.findActor("content");
        content.top().left().add(text).width(root.getWidth() - 24).top().left().pad(8);
        scroller.setScrollY(0); stage.setScrollFocus(scroller);
    }
    @Override public void enter() { super.enter(); display(page); }
}
