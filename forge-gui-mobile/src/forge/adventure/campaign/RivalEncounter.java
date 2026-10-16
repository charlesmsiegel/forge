package forge.adventure.campaign;

import forge.adventure.character.EnemySprite;
import forge.adventure.scene.DuelScene;
import forge.adventure.stage.MapStage;
import forge.adventure.util.Current;
import forge.adventure.util.Config;
import forge.deck.Deck;
import forge.adventure.data.EnemyData;
import forge.adventure.data.WorldData;
import forge.item.PaperCard;
import forge.util.MyRandom;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialog flow when the player bumps into a persistent rival on a map (MVP.md §16, §17):
 * ordinary ante duel, reclamation ante for a card this rival took, or walk away.
 */
public final class RivalEncounter {
    private RivalEncounter() {
    }

    public static void start(MapStage stage, EnemySprite mob) {
        Rival rival = CampaignState.instance().rivals().byId(mob.campaignRivalId);
        if (rival == null) {
            stage.beginDuel(mob);
            return;
        }
        List<PaperCard> taken = rival.getCardsTakenFromPlayer();
        StringBuilder message = new StringBuilder();
        message.append("[GOLD]").append(rival.name).append("[WHITE]\n");
        message.append("Record vs you: ").append(rival.record()).append('\n');
        if (taken.isEmpty())
            message.append("Holds none of your cards.");
        else
            message.append("Holds your: ").append(DuelScene.describeAll(taken));

        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add("Duel (normal ante)");
        actions.add(() -> {
            DuelScene.instance().setCampaignStake(null, mob);
            stage.beginDuel(mob);
        });
        if (!CampaignState.instance().knowledge().known(rival.id).isEmpty()) {
            Deck deck = deckForTargeting(mob);
            if (!CampaignState.instance().knowledge().targets(rival.id, deck).isEmpty()) {
                labels.add("Raise the ante (2 cards)...");
                actions.add(() -> chooseTarget(stage, mob, rival, 0));
            }
        }
        if (!taken.isEmpty()) {
            labels.add("Reclaim a card...");
            actions.add(() -> chooseCard(stage, mob, rival, 0));
        }
        if (CampaignState.instance().rivals().size() > 1) {
            labels.add("Find another rival...");
            actions.add(() -> chooseRival(stage, mob, 0));
        }
        labels.add("Walk away");
        actions.add(() -> walkAway(stage, mob));
        stage.showOptionsDialog(message.toString(), labels, actions);
    }

    private static void chooseRival(MapStage stage, EnemySprite host, int page) {
        RivalRegistry roster = CampaignState.instance().rivals();
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (Rival rival : roster.page(page, 4)) {
            labels.add(rival.name);
            actions.add(() -> {
                EnemyData base = WorldData.getEnemy(rival.baseEnemyName);
                if (base == null) {
                    stage.showOptionsDialog("This rival's encounter is unavailable.", List.of("Back"),
                            List.of(() -> chooseRival(stage, host, page)));
                    return;
                }
                host.campaignEncounter().clear();
                EnemySprite chosen = new EnemySprite(host.getId(), rival.encounterData(base));
                chosen.campaignRivalId = rival.id;
                chosen.nameOverride = rival.name;
                chosen.setPosition(host.getX(), host.getY());
                start(stage, stage.selectCampRival(host, chosen));
            });
        }
        if (page > 0) {
            labels.add("Previous");
            actions.add(() -> chooseRival(stage, host, page - 1));
        }
        if (!roster.page(page + 1, 4).isEmpty()) {
            labels.add("Next");
            actions.add(() -> chooseRival(stage, host, page + 1));
        }
        labels.add("Back");
        actions.add(() -> start(stage, host));
        stage.showOptionsDialog("Rivals' Camp — choose a rival", labels, actions);
    }

    private static void walkAway(MapStage stage, EnemySprite mob) {
        mob.campaignEncounter().clear();
        DuelScene.instance().setCampaignStake(null, mob);
        mob.freezeMovement();
        stage.resetPosition();
    }

    private static Deck deckForTargeting(EnemySprite mob) {
        return mob.campaignEncounter().deck(() -> {
            boolean genetic = Config.instance().getConfigData().enableGeneticAI
                    && (Current.player().isUsingCustomDeck() || Current.player().isHardorInsaneDifficulty());
            return mob.getData().copyPlayerDeck ? Current.player().getSelectedDeck()
                    : mob.getData().generateDeck(Current.player().isFantasyMode(), genetic);
        });
    }

    private static void chooseTarget(MapStage stage, EnemySprite mob, Rival rival, int index) {
        Deck deck = deckForTargeting(mob);
        List<PaperCard> targets = CampaignState.instance().knowledge().targets(rival.id, deck);
        if (targets.isEmpty()) { start(stage, mob); return; }
        int selected = Math.floorMod(index, targets.size());
        PaperCard target = targets.get(selected);
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add("Risk two cards for it");
        actions.add(() -> confirmTarget(stage, mob, rival, deck, target));
        if (targets.size() > 1) {
            labels.add("Next known card");
            actions.add(() -> chooseTarget(stage, mob, rival, selected + 1));
        }
        labels.add("Back");
        actions.add(() -> start(stage, mob));
        stage.showOptionsDialog("[GOLD]Known card: " + DuelScene.describeAll(List.of(target))
                + "[WHITE]\n(" + (selected + 1) + " of " + targets.size() + ")\n"
                + "Risk two random eligible cards against this exact printing from the opponent's active deck.", labels, actions);
    }

    private static void confirmTarget(MapStage stage, EnemySprite mob, Rival rival, Deck deck, PaperCard target) {
        final AnteStake stake;
        try {
            stake = AnteService.targetedStake(Current.player().getSelectedDeck(), deck, target,
                    CampaignState.instance().knowledge(), rival.id, CampaignConfig.instance().ante, MyRandom.getRandom());
        } catch (IllegalArgumentException e) {
            stage.showOptionsDialog(e.getMessage(), List.of("Back"), List.of(() -> start(stage, mob)));
            return;
        }
        CampaignLog.event("targeted_ante_offered").with("rivalId", rival.id)
                .withCards("target", stake.opponentCards).withCards("risked", stake.playerCards).write();
        stage.showOptionsDialog("[RED]Raise the ante.[WHITE]\nYou would risk: " + DuelScene.describeAll(stake.playerCards)
                        + "\nagainst: " + DuelScene.describeAll(stake.opponentCards) + "\nFight?",
                List.of("Fight", "Cancel"), List.of(() -> {
                    DuelScene.instance().setCampaignStake(stake, mob);
                    stage.beginDuel(mob);
                }, () -> {
                    DuelScene.instance().setCampaignStake(null, mob);
                    start(stage, mob);
                }));
    }

    private static void chooseCard(MapStage stage, EnemySprite mob, Rival rival, int index) {
        List<OwnedCard> taken = rival.getTakenCopies();
        if (taken.isEmpty()) {
            start(stage, mob);
            return;
        }
        index = Math.floorMod(index, taken.size());
        OwnedCard card = taken.get(index);
        String message = "Reclaim [GOLD]" + DuelScene.describeAll(List.of(card.card)) + "[WHITE]?\n"
                + "(" + (index + 1) + " of " + taken.size() + ")\nYou will risk "
                + CampaignConfig.instance().ante.reclamationRiskCount + " random cards from your deck and sideboard.";
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add("Risk cards for it");
        actions.add(() -> confirmStake(stage, mob, rival, card));
        if (taken.size() > 1) {
            final int next = index + 1;
            labels.add("Next card");
            actions.add(() -> chooseCard(stage, mob, rival, next));
        }
        labels.add("Back");
        actions.add(() -> start(stage, mob));
        stage.showOptionsDialog(message, labels, actions);
    }

    private static void confirmStake(MapStage stage, EnemySprite mob, Rival rival, OwnedCard card) {
        CampaignConfig.AnteRules rules = CampaignConfig.instance().ante;
        AnteStake stake = AnteService.reclamationStake(Current.player().getSelectedDeck(), card, rules, MyRandom.getRandom());
        if (stake.playerCards.size() < rules.reclamationRiskCount) {
            stage.showOptionsDialog("You need at least " + rules.reclamationRiskCount
                            + " eligible (non-basic) cards in your deck and sideboard to challenge for a card.",
                    List.of("Back"), List.of(() -> start(stage, mob)));
            return;
        }
        String message = "[RED]Reclamation ante.[WHITE]\nYou would risk: " + DuelScene.describeAll(stake.playerCards)
                + "\nagainst: " + DuelScene.describeAll(stake.opponentCards) + "\nFight?";
        CampaignLog.event("reclamation_offered").with("rival", rival.name)
                .withCards("target", stake.opponentCards).withCards("risked", stake.playerCards).write();
        stage.showOptionsDialog(message, List.of("Fight", "Cancel"), List.of(
                () -> {
                    DuelScene.instance().setCampaignStake(stake, mob);
                    stage.beginDuel(mob);
                },
                () -> start(stage, mob)));
    }
}
