package forge.adventure.campaign;

import forge.adventure.character.EnemySprite;
import forge.adventure.scene.DuelScene;
import forge.adventure.scene.TileMapScene;
import forge.adventure.stage.MapStage;
import forge.adventure.util.Current;
import java.util.ArrayList;
import java.util.List;

/** Optional lessons stay in the world and use the ordinary campaign encounter pipeline. */
public final class CampaignTrainingEncounter {
    private CampaignTrainingEncounter() { }
    public static void paths(MapStage stage, int page) {
        var config = CampaignConfig.instance(); var deeds = CampaignState.instance().deeds();
        int start = Math.max(0, Math.min(page * 2, Math.max(0, config.training.length - 1)));
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        for (int i = start; i < Math.min(start+2, config.training.length); i++) {
            var path = config.training[i];
            labels.add(path.color + " training — " + CampaignTraining.completed(path,deeds) + "/3");
            actions.add(() -> path(stage,path));
        }
        if (page>0) { labels.add("Previous");actions.add(() -> paths(stage,page-1)); }
        if (start+2<config.training.length) { labels.add("Next");actions.add(() -> paths(stage,page+1)); }
        if (page == 0) { labels.add("Skip training");actions.add(() -> { CampaignTraining.skip(deeds); CampaignChronicler.show(stage); }); }
        labels.add("Back");actions.add(() -> CampaignChronicler.show(stage));
        stage.showOptionsDialog("[GOLD]Wayfarer training[WHITE]\nFive optional color paths. Learn through real Magic games; skip or return at any time.",labels,actions);
    }
    private static void path(MapStage stage, CampaignConfig.TrainingPath path) {
        var state = CampaignState.instance();
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        for (var lesson:path.lessons) {
            labels.add(lesson.name + (state.deeds().has("training.lesson."+lesson.id) ? " — complete" : ""));
            actions.add(() -> stage.showChoiceDialog(lesson.guidance + "\n\nEach tutor waits in the " + path.color + " hall. Normal ante and persistent life apply. First victories grant one useful legal card each.",
                    "Visit training hall",() -> travel(stage,path),"Back",() -> path(stage,path)));
        }
        labels.add("Back");actions.add(() -> paths(stage,0));
        stage.showOptionsDialog("[GOLD]"+path.name+"[WHITE]\nCompleted lessons remain saved. Practice again without repeating their rewards.",labels,actions);
    }
    private static void travel(MapStage stage, CampaignConfig.TrainingPath path) {
        var state=CampaignState.instance();
        if (state.expedition().isActive()) {
            stage.showChoiceDialog("Return home before choosing a training route.","Back",()->path(stage,path),null,null);return;
        }
        try {
            for (var lesson:path.lessons) CampaignTraining.reward(path,lesson,state.universeFor(CampaignConfig.instance()));
            CampaignTraining.start(path,state.deeds()); TileMapScene.instance().loadNext(path.map,38);
        } catch (IllegalArgumentException error) {
            stage.showChoiceDialog(error.getMessage(),"Back",()->path(stage,path),null,null);
        }
    }
    public static void show(MapStage stage, EnemySprite mob) {
        var config=CampaignConfig.instance(); var state=CampaignState.instance();
        var lesson=CampaignTraining.lessonFor(config,mob.getData());
        List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();
        labels.add("Train (normal ante)");actions.add(()->{DuelScene.instance().setCampaignStake(null,mob);stage.beginDuel(mob);});
        if(mob.campaignRivalId!=null){labels.add("Rival options");actions.add(()->RivalEncounter.start(stage,mob));}
        labels.add("Leave");actions.add(()->{mob.freezeMovement();stage.resetPosition();});
        stage.showOptionsDialog("[GOLD]"+lesson.name+"[WHITE]\n"+lesson.guidance+"\n\nNormal ante and life rules apply. "
                +(state.deeds().has("training.lesson."+lesson.id)?"Already completed: this is practice, without another lesson reward.":"First victory earns one legal physical card."),labels,actions);
    }
}
