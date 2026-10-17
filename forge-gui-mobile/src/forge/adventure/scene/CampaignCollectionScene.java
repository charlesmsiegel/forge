package forge.adventure.scene;

import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.github.tommyettinger.textra.TextraButton;
import com.github.tommyettinger.textra.TextraLabel;
import forge.Forge;
import forge.adventure.campaign.*;
import forge.adventure.util.Controls;
import forge.adventure.util.Current;
import forge.adventure.util.Reward;
import forge.adventure.util.RewardActor;
import forge.card.CardRarity;
import java.util.*;
import java.util.List;

/** Read-only physical binder. Card previews never enter the reward acquisition/sale pipeline. */
public final class CampaignCollectionScene extends UIScene {
    private static final int PAGE_SIZE=12;
    private final Scene previous;
    private final Table root,filters=new Table(),rows=new Table();
    private final ScrollPane scroller;
    private final TextField search=Controls.newTextField(""),edition=Controls.newTextField("");
    private final TextraLabel summary=Controls.newTextraLabel("");
    private final TextraButton finish,rarity,availability,protection,reset;
    private final List<OwnedCard> owned;
    private final List<CampaignCollection.Entry> entries;
    private List<CampaignCollection.Entry> visible=List.of();
    private final Map<String,CampaignCollection.Completion> completions=new HashMap<>();
    private Boolean foil;
    private int rarityIndex,page,detail=-1;
    private boolean availableOnly,protectedOnly,updating;
    private RewardActor preview;
    private final String[] rarityNames={"","Common","Uncommon","Rare","MythicRare","Special","BasicLand"};

    public CampaignCollectionScene(Scene previous) {
        super(Forge.isLandscapeMode()?"ui/campaign_journal.json":"ui/campaign_journal_portrait.json");
        this.previous=previous;
        var state=CampaignState.instance();owned=List.copyOf(state.ownedCopies(Current.player()));
        Set<String> available=new HashSet<>();for(var copy:state.availableCopies(Current.player()))available.add(copy.id);
        entries=CampaignCollection.entries(owned,available,state.progression().protectedIds(),Current.player().getSelectedDeck().getMain());
        root=ui.findActor("content");scroller=new ScrollPane(rows,Controls.getSkin());
        scroller.setScrollingDisabled(true,false);scroller.setFadeScrollBars(false);
        search.setMessageText("Search name or copy");edition.setMessageText("Set / all");
        search.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener(){public void changed(ChangeEvent event,com.badlogic.gdx.scenes.scene2d.Actor actor){if(!updating)refresh(true);}});
        edition.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener(){public void changed(ChangeEvent event,com.badlogic.gdx.scenes.scene2d.Actor actor){if(!updating)refresh(true);}});
        finish=Controls.newTextButton("Any finish",()->{foil=foil==null?Boolean.TRUE:foil?Boolean.FALSE:null;refresh(true);});
        rarity=Controls.newTextButton("Any rarity",()->{rarityIndex=(rarityIndex+1)%rarityNames.length;refresh(true);});
        availability=Controls.newTextButton("All owned",()->{availableOnly=!availableOnly;refresh(true);});
        protection=Controls.newTextButton("Any protection",()->{protectedOnly=!protectedOnly;refresh(true);});
        reset=Controls.newTextButton("Clear",()->{
            updating=true;search.setText("");edition.setText("");updating=false;
            foil=null;rarityIndex=0;availableOnly=false;protectedOnly=false;refresh(true);
        });
        ui.onButtonPress("return",()->{if(detail>=0){page=detail/PAGE_SIZE;detail=-1;showList();}else Forge.switchScene(previous);});
        ui.onButtonPress("previous",()->navigate(-1));ui.onButtonPress("next",()->navigate(1));
    }
    private void navigate(int delta) {
        if(detail>=0){if(!visible.isEmpty()){detail=Math.floorMod(detail+delta,visible.size());showDetail();}}
        else {page=Math.max(0,Math.min(page+delta,Math.max(0,(visible.size()-1)/PAGE_SIZE)));refresh(false);}
    }
    private void showList() {
        releasePreview();root.clear();filters.clear();
        float width=root.getWidth();
        Table input=new Table();input.add(search).width(width-124).height(22).padRight(4);
        input.add(edition).width(64).height(22).padRight(4);input.add(reset).width(48).height(22);
        filters.add(input).colspan(2).growX().padBottom(3);filters.row();
        filters.add(finish).width((width-4)/2).height(20).padRight(4);filters.add(rarity).width((width-4)/2).height(20);filters.row();
        filters.add(availability).height(20).fillX().padRight(4);filters.add(protection).height(20).fillX();
        summary.setWrap(true);
        root.top().add(filters).growX();root.row();root.add(summary).growX().height(36).padTop(3);root.row();
        root.add(scroller).grow();refresh(false);selectActor(getPossibleSelection().first());
    }
    private void refresh(boolean resetPage) {
        if(detail>=0)return;
        if(resetPage)page=0;
        visible=CampaignCollection.filter(entries,new CampaignCollection.Query(search.getText(),edition.getText(),rarityNames[rarityIndex],foil,availableOnly,protectedOnly));
        int pages=Math.max(1,(visible.size()+PAGE_SIZE-1)/PAGE_SIZE);page=Math.min(page,pages-1);
        TextraLabel heading=ui.findActor("heading");heading.setText("[BLACK]Physical collection  "+(page+1)+"/"+pages);
        finish.setText(foil==null?"Any finish":foil?"Foil only":"Regular only");
        rarity.setText(rarityIndex==0?"Any rarity":CardRarity.smartValueOf(rarityNames[rarityIndex]).getLongName());
        availability.setText(availableOnly?"Available only":"All owned");protection.setText(protectedOnly?"Protected only":"Any protection");
        String text=visible.size()+" matching copies / "+owned.size()+" owned";
        String set=edition.getText().trim().toUpperCase(Locale.ROOT);
        if(!set.isEmpty()) {
            var completion=completions.computeIfAbsent(set,key->CampaignCollection.completionForEdition(key,CampaignState.instance().universeFor(CampaignConfig.instance()),owned));
            text=set+" eligible: "+completion.namesOwned()+"/"+completion.namesTotal()+" names • "+completion.printingsOwned()+"/"+completion.printingsTotal()+" arts\n"+text;
        }
        summary.setText("[BLACK][%85]"+text);
        rows.clear();rows.top().left();clearSelectable();
        addToSelectable(search);addToSelectable(edition);addToSelectable(reset);addToSelectable(finish);addToSelectable(rarity);addToSelectable(availability);addToSelectable(protection);
        int start=page*PAGE_SIZE;
        for(int i=start;i<Math.min(start+PAGE_SIZE,visible.size());i++) {
            final int index=i;var entry=visible.get(i);var copy=entry.copy();
            String name=copy.card.getName();int limit=Forge.isLandscapeMode()?48:30;if(name.length()>limit)name=name.substring(0,limit-1)+"…";
            String id=copy.id.substring(Math.max(0,copy.id.length()-8));
            String label=name+"\n[%80]"+CampaignCollection.printingLabel(copy.card)+"\ncopy …"+id
                    +(entry.protectedCopy()?" • protected":"")+" • "+(entry.available()?"available":"home reserve");
            var button=Controls.newTextButton(label,()->{detail=index;showDetail();});
            rows.add(button).width(root.getWidth()-16).height(58).padBottom(4);rows.row();
            addToSelectable(new Selectable<>(button){@Override public void onSelect(UIScene scene){scroller.scrollTo(button.getX(),button.getY(),button.getWidth(),button.getHeight());}});
        }
        if(visible.isEmpty()){
            var empty=Controls.newTextraLabel("[WHITE]No physical copies match. Clear a filter or try another set.");empty.setWrap(true);
            rows.add(empty).width(root.getWidth()-20).pad(8);
        }
        footer(page>0,page+1<pages);scroller.setScrollY(0);stage.setScrollFocus(scroller);
    }
    private void footer(boolean before,boolean after) {
        Button previousButton=ui.findActor("previous"),nextButton=ui.findActor("next"),back=ui.findActor("return");
        previousButton.setDisabled(!before);nextButton.setDisabled(!after);
        if(before)addToSelectable(previousButton);if(after)addToSelectable(nextButton);addToSelectable(back);
    }
    private void showDetail() {
        releasePreview();root.clear();stage.setKeyboardFocus(null);
        var entry=visible.get(detail);var copy=entry.copy();
        TextraLabel heading=ui.findActor("heading");heading.setText("[BLACK]Printing and physical copy");
        preview=new RewardActor(new Reward(copy.card,true),false,RewardScene.Type.RewardChoice,false);
        preview.setTouchable(Touchable.disabled);
        String text=copy.card.getName()+"\n"+CampaignCollection.printingLabel(copy.card)+"\nArtist: "+copy.card.getArtist()
                +"\nRarity: "+copy.card.getRarity().getLongName()+"\n\nPhysical copy: "+copy.id
                +"\n"+(entry.protectedCopy()?"Protected":"Unprotected")+" • "+(entry.available()?"Available now":"At home during this expedition")
                +"\nActive deck uses "+entry.deckCopies()+" of this exact printing/finish."
                +"\n\n"+copy.card.getRules().getOracleText();
        var label=Controls.newTextraLabel("[WHITE][%85]"+text);label.setWrap(true);
        Table words=new Table();words.top().left().add(label).width(Forge.isLandscapeMode()?root.getWidth()-134:root.getWidth()-20).pad(5);
        ScrollPane reading=new ScrollPane(words,Controls.getSkin());reading.setScrollingDisabled(true,false);reading.setFadeScrollBars(false);
        if(Forge.isLandscapeMode()){root.add(preview).size(110,154).padRight(8);root.add(reading).grow();}
        else{root.top().add(preview).size(126,176);root.row();root.add(reading).grow().padTop(5);}
        clearSelectable();footer(visible.size()>1,visible.size()>1);stage.setScrollFocus(reading);selectActor(getPossibleSelection().first());
    }
    private void releasePreview(){if(preview!=null){preview.remove();preview.dispose();preview=null;}}
    @Override public void enter(){super.enter();if(detail>=0)showDetail();else showList();}
    @Override public boolean leave(){releasePreview();return super.leave();}
    @Override public void dispose(){releasePreview();super.dispose();}
}
