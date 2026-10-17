package forge.adventure.campaign;

import forge.StaticData;
import forge.adventure.character.EnemySprite;
import forge.deck.Deck;
import forge.item.PaperCard;
import java.util.*;
import java.util.function.Supplier;

/** Shared selection path for the actual duel and targeted-ante preview. */
public final class CampaignOpponentDecks {
    private CampaignOpponentDecks() { }
    private static final Map<CardUniverse, List<PaperCard>> pools = new WeakHashMap<>();

    public static boolean prepare(forge.adventure.stage.GameStage stage, EnemySprite enemy) {
        if (!CampaignConfig.instance().isActive()) return true;
        try {
            enemy.campaignEncounter().deck(() -> {
                boolean genetic = forge.adventure.util.Config.instance().getConfigData().enableGeneticAI
                        && (forge.adventure.util.Current.player().isUsingCustomDeck()
                        || forge.adventure.util.Current.player().isHardorInsaneDifficulty());
                return generate(enemy, () -> enemy.getData().copyPlayerDeck
                        ? forge.adventure.util.Current.player().getSelectedDeck()
                        : enemy.getData().generateDeck(forge.adventure.util.Current.player().isFantasyMode(), genetic));
            });
            return true;
        } catch (IllegalArgumentException error) {
            enemy.campaignEncounter().clear();
            enemy.freezeMovement();
            if (stage instanceof forge.adventure.stage.MapStage map) map.resetPosition();
            stage.showOptionsDialog("[RED]Opponent unavailable.[WHITE]\n" + error.getMessage(),
                    List.of("Back"), List.of(() -> { }));
            return false;
        }
    }

    public static Deck generate(EnemySprite enemy, Supplier<Deck> selectTemplate) {
        Deck template = selectTemplate.get();
        CampaignConfig config = CampaignConfig.instance();
        CardUniverse universe = CampaignState.instance().universeFor(config);
        if (template == null && config.isActive())
            throw new IllegalArgumentException("Missing opponent deck for " + enemy.getData().getName() + ". Repair its configured deck path before fighting.");
        if (universe == null) return template;
        if (enemy.campaignStoryEncounter != null && CampaignStory.encounter(config, enemy.campaignStoryEncounter).cards.length > 0)
            return CampaignStory.deck(config, enemy.campaignStoryEncounter, universe);
        if (enemy.campaignLandmarkSlot != null)
            return CampaignLandmarks.encounterDeck(template, CampaignState.instance().seededLegends().entry(enemy.campaignLandmarkSlot));
        if (enemy.getData().copyPlayerDeck || enemy.campaignStoryEncounter != null
                || Arrays.asList(enemy.getData().questTags).contains("Historical")) return template;
        Rival rival = CampaignState.instance().rivals().byId(enemy.campaignRivalId);
        WorldDeeds deeds = CampaignState.instance().deeds();
        if (rival != null) rival.advanceForWorld(deeds);
        int tier = rival == null ? (deeds.has("seal.broken") ? 2 : deeds.has("shandalar.champion") ? 1 : 0) : rival.upgradeTier();
        long seed = rival == null ? enemy.getData().name.hashCode() : rival.deckSeed();
        Collection<PaperCard> pool = tier == 0 && template.getMain().toFlatList().stream().allMatch(c -> universe.allowsPrinting(c) && !c.getRules().isUnsupported())
                ? template.getMain().toFlatList() : pool(universe);
        return rival == null ? EnemyDeckPolicy.adapt(template, universe, pool, seed, tier)
                : rival.opponentDeck(template, universe, pool);
    }

    private static synchronized List<PaperCard> pool(CardUniverse universe) {
        return pools.computeIfAbsent(universe, key -> {
            List<PaperCard> cards = new ArrayList<>();
            for (String name : key.legalNames())
                for (PaperCard printing : StaticData.instance().getCommonCards().getAllCardsNoAlt(name))
                    if (key.allowsPrinting(printing)) { cards.add(printing); break; }
            // Expansions without basics still use the saved universe's infrastructure exception.
            for (String name : List.of("Plains", "Island", "Swamp", "Mountain", "Forest", "Wastes"))
                for (PaperCard printing : StaticData.instance().getCommonCards().getAllCardsNoAlt(name))
                    if (key.allowsPrinting(printing)) { cards.add(printing); break; }
            return cards;
        });
    }
}
