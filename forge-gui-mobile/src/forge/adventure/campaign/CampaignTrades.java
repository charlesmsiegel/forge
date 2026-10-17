package forge.adventure.campaign;

import forge.StaticData;
import forge.adventure.player.AdventurePlayer;
import forge.card.CardRarity;
import forge.card.MagicColor;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Specialist exchanges operate on exact available IDs; output is staged before any sacrifice. */
public final class CampaignTrades {
    private CampaignTrades() { }

    public static boolean available(CampaignConfig.TradeRecipe recipe, WorldDeeds deeds) {
        for (String flag : recipe.requires) if (!deeds.has(flag)) return false;
        return true;
    }
    private static void requireAvailable(CampaignConfig.TradeRecipe recipe) {
        if (!available(recipe, CampaignState.instance().deeds()))
            throw new IllegalArgumentException("This specialist exchange requires: " + String.join(", ", recipe.requires));
    }

    public static void validate(CampaignConfig.TradeRecipe recipe) {
        if (recipe == null || recipe.id == null || recipe.id.isBlank() || recipe.requiredCount < 2
                || recipe.inputEditions == null || recipe.inputEditions.length == 0
                || recipe.outputEditions == null || recipe.outputEditions.length == 0
                || recipe.requires == null || Arrays.stream(recipe.requires).anyMatch(flag -> flag == null || flag.isBlank())
                || CardRarity.smartValueOf(recipe.inputRarity) == CardRarity.Unknown
                || CardRarity.smartValueOf(recipe.outputRarity) == CardRarity.Unknown
                || (!recipe.inputColor.isEmpty() && MagicColor.fromName(recipe.inputColor) == 0)
                || (!recipe.outputColor.isEmpty() && MagicColor.fromName(recipe.outputColor) == 0))
            throw new IllegalArgumentException("Invalid specialist trade recipe");
        for (String edition : recipe.inputEditions)
            if (StaticData.instance().getCardEdition(edition) == null) throw new IllegalArgumentException("Unknown input edition " + edition);
        for (String edition : recipe.outputEditions)
            if (StaticData.instance().getCardEdition(edition) == null) throw new IllegalArgumentException("Unknown output edition " + edition);
    }

    private static boolean matches(PaperCard card, String[] editions, String rarity, String color) {
        return Arrays.asList(editions).contains(card.getEdition()) && card.getRarity() == CardRarity.smartValueOf(rarity)
                && (color.isEmpty() || card.getRules().getColor().hasAnyColor(MagicColor.fromName(color)));
    }

    private static boolean inputMatches(PaperCard card, CampaignConfig.TradeRecipe recipe) {
        return !card.hasNoSellValue() && !card.getRules().getType().isBasicLand()
                && matches(card, recipe.inputEditions, recipe.inputRarity, recipe.inputColor);
    }

    public static List<OwnedCard> preview(CampaignConfig.TradeRecipe recipe, AdventurePlayer player) {
        validate(recipe); requireAvailable(recipe);
        List<OwnedCard> inputs = new ArrayList<>();
        for (OwnedCard copy : CampaignState.instance().voluntaryRemovalCopies(player))
            if (inputMatches(copy.card, recipe)) {
                inputs.add(copy);
                if (inputs.size() == recipe.requiredCount) return inputs;
            }
        throw new IllegalArgumentException("This exchange needs " + recipe.requiredCount + " available nonbasic "
                + recipe.inputRarity + " cards from " + String.join(", ", recipe.inputEditions));
    }

    public static PaperCard execute(CampaignConfig.TradeRecipe recipe, List<OwnedCard> inputs,
                                     AdventurePlayer player, boolean allowProtected, Random random) {
        validate(recipe); requireAvailable(recipe);
        CampaignState state = CampaignState.instance();
        List<OwnedCard> available = state.availableCopies(player);
        if (inputs == null || inputs.size() != recipe.requiredCount) throw new IllegalArgumentException("Wrong number of trade copies");
        Set<String> ids = new HashSet<>();
        for (OwnedCard copy : inputs)
            if (copy == null || !ids.add(copy.id) || !available.contains(copy) || !inputMatches(copy.card, recipe)
                    || (!allowProtected && state.progression().isProtected(copy.id)))
                throw new IllegalArgumentException("Trade includes unavailable, duplicate, ineligible or unconfirmed protected copies");
        for (String edition : recipe.outputEditions) RegionCompletion.ensureEditionLoaded(edition);
        List<PaperCard> outputs = new ArrayList<>();
        for (PaperCard card : StaticData.instance().getCommonCards().getAllCards())
            if (matches(card, recipe.outputEditions, recipe.outputRarity, recipe.outputColor)
                    && (state.universeFor(CampaignConfig.instance()) == null || state.universeFor(CampaignConfig.instance()).allowsPrinting(card))
                    && !card.getRules().isUnsupported() && !CampaignConfig.instance().isCardBanned(card.getName())) outputs.add(card);
        if (outputs.isEmpty()) throw new IllegalArgumentException("No eligible output for this exchange");
        outputs.sort(java.util.Comparator.naturalOrder());
        PaperCard reward = outputs.get(random.nextInt(outputs.size())).getNoSellVersion();
        // All availability, filtering and product failures are resolved before removing a physical unit.
        for (OwnedCard copy : inputs)
            if (!CardOwnership.removeCopy(player, copy)) throw new IllegalStateException("Validated trade copy disappeared");
        player.addCard(reward);
        CampaignLog.event("specialist_trade").with("recipe", recipe.id).with("inputIds", new ArrayList<>(ids))
                .withCards("inputs", inputs.stream().map(c -> c.card).toList()).withCards("outputs", List.of(reward)).write();
        return reward;
    }
}
