package forge.adventure.campaign;

import forge.adventure.scene.DuelScene;
import forge.adventure.stage.MapStage;
import forge.adventure.util.Current;
import forge.item.PaperCard;
import forge.util.MyRandom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Functional collector dialogs. Collection management and exchange remain in campaign services. */
public final class CampaignServices {
    private CampaignServices() { }

    public static void show(MapStage stage) {
        CampaignProgress progress = CampaignState.instance().progression();
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add("Protect or unprotect a card"); actions.add(() -> protection(stage, 0));
        if (progress.restrictedChoiceTokens() > 0) {
            labels.add("Choose an extra restricted copy"); actions.add(() -> restricted(stage, 0));
        }
        labels.add("Trade surplus cards"); actions.add(() -> recipes(stage));
        labels.add("Leave"); actions.add(stage::resetPosition);
        stage.showOptionsDialog("[GOLD]The Collector[WHITE]\n"
                + "Protection slots: " + progress.protectedIds().size() + "/" + progress.protectionSlots()
                + ". Ordinary copy limit: " + progress.ordinaryLimit(CampaignConfig.instance().player.ordinaryCopyLimit) + ".\n"
                + (progress.isMasterCollector() ? "Master Collector: ordinary limits apply per printing.\n" : "")
                + "Full block clears earn permanent abilities. Protected cards are skipped by random ante while unprotected cards remain.", labels, actions);
    }

    private static String shortCopy(OwnedCard copy) {
        String name = copy.card.getName();
        if (name.length() > 26) name = name.substring(0, 23) + "...";
        return name + " (" + copy.card.getEdition() + " #" + copy.card.getCollectorNumber()
                + (copy.card.isFoil() ? ", foil" : "") + ") copy " + copy.id.substring(Math.max(0, copy.id.length() - 8));
    }

    private static void protection(MapStage stage, int page) {
        CampaignState state = CampaignState.instance();
        List<OwnedCard> copies = new ArrayList<>(state.availableCopies(Current.player()));
        // Current deck copies are offered first; home reserve remains manageable outside expeditions.
        copies.sort(Comparator.comparing((OwnedCard copy) -> Current.player().getSelectedDeck().count(copy.card) == 0)
                .thenComparing(copy -> copy.card.getName()).thenComparing(copy -> copy.card.getEdition()));
        int selected = Math.max(0, Math.min(page, Math.max(0, (copies.size() - 1) / 4)));
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        for (OwnedCard copy : copies.subList(selected * 4, Math.min(copies.size(), selected * 4 + 4))) {
            labels.add((state.progression().isProtected(copy.id) ? "Protected: " : "") + shortCopy(copy));
            actions.add(() -> protectionChoice(stage, copy, selected));
        }
        pages(labels, actions, selected, copies.size(), p -> protection(stage, p));
        labels.add("Back"); actions.add(() -> show(stage));
        stage.showOptionsDialog("Protection: " + state.progression().protectedIds().size() + "/" + state.progression().protectionSlots()
                + " slots. Select a physical copy. If every eligible card is protected, random ante can still risk one.", labels, actions);
    }

    private static void protectionChoice(MapStage stage, OwnedCard copy, int page) {
        CampaignState state = CampaignState.instance();
        boolean protectedCopy = state.progression().isProtected(copy.id);
        stage.showChoiceDialog(DuelScene.describeAll(List.of(copy.card)) + "\nCopy: " + copy.id + "\n"
                        + (protectedCopy ? "Remove this copy's protection?" : "Use one protection slot for this copy?"),
                protectedCopy ? "Unprotect" : "Protect", () -> {
                    try {
                        if (protectedCopy) state.progression().unprotect(copy.id);
                        else state.progression().protect(copy, state.availableCopies(Current.player()));
                        protection(stage, page);
                    } catch (IllegalArgumentException e) { message(stage, e.getMessage(), () -> protection(stage, page)); }
                }, "Cancel", () -> protection(stage, page));
    }

    private static void restricted(MapStage stage, int page) {
        List<String> names = CampaignState.instance().ownedCopies(Current.player()).stream().map(copy -> copy.card.getName())
                .filter(CampaignConfig.instance()::isCardRestricted).distinct().sorted().toList();
        int selected = Math.max(0, Math.min(page, Math.max(0, (names.size() - 1) / 4)));
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        for (String name : names.subList(selected * 4, Math.min(names.size(), selected * 4 + 4))) {
            labels.add(name); actions.add(() -> stage.showChoiceDialog("Spend one earned choice to allow one additional copy of " + name + "?",
                    "Choose", () -> {
                        try {
                            CampaignState.instance().progression().chooseRestrictedCopy(name, CampaignConfig.instance(), Current.player().getCards());
                            show(stage);
                        } catch (IllegalArgumentException e) { message(stage, e.getMessage(), () -> show(stage)); }
                    }, "Cancel", () -> restricted(stage, selected)));
        }
        pages(labels, actions, selected, names.size(), p -> restricted(stage, p));
        labels.add("Back"); actions.add(() -> show(stage));
        stage.showOptionsDialog("Choose an owned restricted card. Its allowance remains by name, including under Master Collector.", labels, actions);
    }

    private static void recipes(MapStage stage) {
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        for (CampaignConfig.TradeRecipe recipe : CampaignConfig.instance().trades) {
            labels.add(recipe.name); actions.add(() -> {
                try { tradeInputs(stage, recipe, CampaignTrades.preview(recipe, Current.player()), 0); }
                catch (IllegalArgumentException e) { message(stage, e.getMessage(), () -> recipes(stage)); }
            });
        }
        labels.add("Back"); actions.add(() -> show(stage));
        stage.showOptionsDialog("Specialist exchanges consume the physical copies you approve. Ordinary basic lands and virtual cards are not accepted.", labels, actions);
    }

    private static void tradeInputs(MapStage stage, CampaignConfig.TradeRecipe recipe, List<OwnedCard> inputs, int page) {
        CampaignProgress progress = CampaignState.instance().progression();
        boolean touchesProtection = inputs.stream().anyMatch(copy -> progress.isProtected(copy.id));
        StringBuilder message = new StringBuilder(recipe.name).append("\nTrade ").append(inputs.size()).append(" cards for one random ")
                .append(recipe.outputRarity).append(" from ").append(String.join(", ", recipe.outputEditions)).append(".\nInputs ")
                .append(page * 4 + 1).append("–").append(Math.min(inputs.size(), page * 4 + 4)).append(" of ").append(inputs.size()).append(":\n");
        for (OwnedCard copy : inputs.subList(page * 4, Math.min(inputs.size(), page * 4 + 4)))
            message.append(progress.isProtected(copy.id) ? "[RED]Protected: [WHITE]" : "").append(shortCopy(copy)).append('\n');
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        if ((page + 1) * 4 >= inputs.size()) {
            message.append("This may require deck repair.");
            if (touchesProtection) message.append(" Protected inputs will lose their protection and free their slots.");
            labels.add("Confirm exchange"); actions.add(() -> {
                try {
                    PaperCard reward = CampaignTrades.execute(recipe, inputs, Current.player(), touchesProtection, MyRandom.getRandom());
                    message(stage, "Received: " + DuelScene.describeAll(List.of(reward)), () -> show(stage));
                } catch (IllegalArgumentException e) { message(stage, e.getMessage(), () -> recipes(stage)); }
            });
        }
        pages(labels, actions, page, inputs.size(), p -> tradeInputs(stage, recipe, inputs, p));
        labels.add("Cancel"); actions.add(() -> recipes(stage));
        stage.showOptionsDialog(message.toString(), labels, actions);
    }

    private static void pages(List<String> labels, List<Runnable> actions, int page, int size, java.util.function.IntConsumer select) {
        if (page > 0) { labels.add("Previous"); actions.add(() -> select.accept(page - 1)); }
        if ((page + 1) * 4 < size) { labels.add("Next"); actions.add(() -> select.accept(page + 1)); }
    }

    private static void message(MapStage stage, String message, Runnable back) {
        stage.showOptionsDialog(message, List.of("Back"), List.of(back));
    }
}
