package forge.adventure.campaign;

import forge.StaticData;
import forge.adventure.data.EnemyData;
import forge.adventure.player.AdventurePlayer;
import forge.card.MagicColor;
import forge.item.PaperCard;
import java.util.*;

/** Optional training progresses through actual Magic victories, with deed-backed one-time rewards. */
public final class CampaignTraining {
    private CampaignTraining() { }
    public static void skip(WorldDeeds deeds) { deeds.grant("training.skipped"); }
    public static void start(CampaignConfig.TrainingPath path, WorldDeeds deeds) { deeds.grant("training.started." + path.id); }
    public static int completed(CampaignConfig.TrainingPath path, WorldDeeds deeds) {
        return (int) Arrays.stream(path.lessons).filter(l -> deeds.has("training.lesson." + l.id)).count();
    }
    public static void validate(CampaignConfig config) {
        Set<String> paths = new HashSet<>(), lessons = new HashSet<>(), enemies = new HashSet<>();
        for (var path : config.training) {
            if (path.id.isBlank() || !paths.add(path.id) || path.name.isBlank() || MagicColor.fromName(path.color) == 0
                    || !path.map.startsWith("maps/") || path.map.contains("..") || path.lessons.length != 3)
                throw new IllegalArgumentException("Invalid training path " + path.id);
            for (var lesson : path.lessons)
                if (lesson.id.isBlank() || !lessons.add(lesson.id) || lesson.name.isBlank() || lesson.enemy.isBlank()
                        || !enemies.add(lesson.enemy) || lesson.guidance.isBlank() || lesson.rewards.length == 0)
                    throw new IllegalArgumentException("Invalid training lesson " + lesson.id);
        }
    }
    public static CampaignConfig.TrainingLesson lessonFor(CampaignConfig config, EnemyData enemy) {
        for (String tag : enemy.questTags) if (tag.startsWith("Training:")) {
            String id = tag.substring("Training:".length());
            for (var path : config.training) for (var lesson : path.lessons) if (lesson.id.equals(id)) return lesson;
            throw new IllegalArgumentException("Unknown training lesson " + id);
        }
        return null;
    }
    public static PaperCard reward(CampaignConfig.TrainingPath path, CampaignConfig.TrainingLesson lesson, CardUniverse universe) {
        return chooseReward(lesson.rewards, path.color, universe);
    }
    static PaperCard chooseReward(String[] preferred, String color, CardUniverse universe) {
        if (universe == null) throw new IllegalArgumentException("Training needs the saved campaign card universe");
        LinkedHashSet<String> names = new LinkedHashSet<>(List.of(preferred)); names.addAll(universe.legalNames());
        for (String name : names) for (PaperCard card : StaticData.instance().getCommonCards().getAllCardsNoAlt(name)) {
            var rules = card.getRules();
            if (universe.allowsPrinting(card) && !rules.isUnsupported() && !rules.getType().isLand()
                    && rules.getColor().hasAnyColor(MagicColor.fromName(color)) && rules.getManaCost().getCMC() <= 4)
                return card;
        }
        throw new IllegalArgumentException("This universe has no suitable " + color + " lesson reward");
    }
    public static PaperCard complete(CampaignConfig config, String id, boolean won, AdventurePlayer player,
                                     WorldDeeds deeds, CardUniverse universe) {
        if (!won || deeds.has("training.lesson." + id)) return null;
        for (var path : config.training) for (var lesson : path.lessons) if (lesson.id.equals(id)) {
            PaperCard reward = reward(path, lesson, universe); // Resolve before recording any completion.
            player.addCard(reward); start(path, deeds); deeds.grant("training.lesson." + id);
            if (completed(path, deeds) == path.lessons.length) deeds.grant("training.completed." + path.id);
            CampaignLog.event("training_lesson_completed").with("lesson", id).with("card", reward).write();
            return reward;
        }
        throw new IllegalArgumentException("Unknown training lesson " + id);
    }
}
