package forge.adventure.campaign;

import forge.StaticData;
import forge.adventure.data.ConfigData;
import forge.adventure.util.SaveFileContent;
import forge.adventure.util.SaveFileData;
import forge.card.CardEdition;
import forge.game.GameFormat;
import forge.item.PaperCard;
import forge.model.FModel;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Fixed campaign rules. No reference to a rotating format survives capture. */
public final class CardUniverse implements SaveFileContent {
    private static final Set<String> ANTE = Set.of("Amulet of Quoz", "Bronze Tablet", "Contract from Below",
            "Darkpact", "Demonic Attorney", "Jeweled Bird", "Rebirth", "Tempest Efreet", "Timmerian Fiends");
    private String campaignId = "", preset = "", capturedOn = "";
    private final Set<String> editions = new TreeSet<>(), names = new TreeSet<>(), banned = new TreeSet<>(),
            restricted = new TreeSet<>(), generationBans = new TreeSet<>(), generationExcludedEditions = new TreeSet<>(), infrastructureEditions = new TreeSet<>();
    private transient ConfigData source, effective;

    public static CardUniverse capture(CampaignConfig config, ConfigData plane) {
        String preset = config.universe.preset;
        GameFormat format = null;
        if (preset != null && !preset.isBlank() && !preset.equals("All Magic") && !preset.equals("Custom")) {
            format = FModel.getFormats().getFormat(preset);
            if (format == null) throw new IllegalArgumentException("Unknown campaign format preset " + preset);
        }
        return capture(config, plane, format, LocalDate.now());
    }

    public static CardUniverse capture(CampaignConfig config, ConfigData plane, GameFormat format, LocalDate today) {
        CardUniverse result = new CardUniverse();
        result.campaignId = config.campaignId;
        result.preset = config.universe.preset;
        result.capturedOn = today.toString();
        Set<String> requested = new TreeSet<>();
        add(requested, config.universe.allowedEditions);
        if (requested.isEmpty() && format != null) requested.addAll(format.getAllowedSetCodes());
        if (requested.isEmpty()) add(requested, plane.allowedEditions);
        add(result.generationExcludedEditions, plane.restrictedEditions);
        boolean explicitSets = config.universe.allowedEditions != null && config.universe.allowedEditions.length > 0;
        for (String code : requested)
            if (StaticData.instance().getCardEdition(code) == null) throw new IllegalArgumentException("Unknown campaign edition " + code);
        for (CardEdition edition : StaticData.instance().getEditions()) {
            String code = edition.getCode();
            LocalDate release = edition.getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            if (config.universe.includeUnreleased || !release.isAfter(today)) result.infrastructureEditions.add(code);
            if (!requested.isEmpty() && !requested.contains(code)) continue;
            if (!config.universe.includeUnreleased && release.isAfter(today)) {
                if (explicitSets && requested.contains(code)) throw new IllegalArgumentException("Campaign edition has not released: " + code);
                continue;
            }
            result.editions.add(code);
            for (CardEdition.EditionEntry entry : edition.getObtainableCards())
                if (format == null || format.getAllowedRarities().isEmpty() || format.getAllowedRarities().contains(entry.rarity()))
                    result.names.add(entry.name());
        }
        if (result.editions.isEmpty() || result.names.isEmpty()) throw new IllegalArgumentException("Campaign card universe is empty");
        if (format != null) {
            result.banned.addAll(format.getBannedCardNames());
            result.restricted.addAll(format.getRestrictedCards());
            result.names.addAll(format.getAdditionalCards());
        }
        if (config.universe.restoreAnteCards) result.banned.removeAll(ANTE);
        result.banned.removeAll(Arrays.asList(config.universe.unbannedCards));
        result.restricted.removeAll(Arrays.asList(config.universe.unrestrictedCards));
        add(result.names, config.universe.additionalCards);
        add(result.banned, config.cards.bannedCards);
        add(result.restricted, config.cards.restrictedCards);
        result.generationBans.addAll(result.banned);
        add(result.generationBans, plane.restrictedCards);
        return result;
    }

    private static void add(Set<String> set, String[] values) { if (values != null) Collections.addAll(set, values); }
    public boolean belongsTo(CampaignConfig config) { return config.isActive() && campaignId.equals(config.campaignId); }
    public boolean allowsEdition(String code) { return editions.contains(code) && !generationExcludedEditions.contains(code); }
    public boolean allowsGeneratedName(String name) { return names.contains(name) && !generationBans.contains(name); }
    public boolean isBanned(String name) { return banned.contains(name); }
    public Set<String> restrictedNames() { return Collections.unmodifiableSet(restricted); }
    public boolean isRestricted(String name) { return restricted.contains(name); }
    public boolean isLegal(PaperCard card) {
        return card != null && !banned.contains(card.getName())
                && (card.getRules().getType().isBasicLand() || names.contains(card.getName()));
    }
    public boolean allowsBespokePrinting(PaperCard card) { return isLegal(card) && editions.contains(card.getEdition()); }
    public boolean allowsPrinting(PaperCard card) {
        return isLegal(card) && !generationBans.contains(card.getName())
                && (allowsEdition(card.getEdition()) || (card.getRules().getType().isBasicLand()
                && infrastructureEditions.contains(card.getEdition())));
    }
    public void requireEdition(String edition) {
        if (!allowsEdition(edition)) throw new IllegalArgumentException("Edition " + edition + " is outside this campaign's card universe");
    }
    public ConfigData generationConfig(ConfigData original) {
        if (source == original && effective != null) return effective;
        ConfigData copy = new ConfigData();
        try {
            for (java.lang.reflect.Field field : ConfigData.class.getFields()) field.set(copy, field.get(original));
        } catch (IllegalAccessException e) { throw new IllegalStateException("Cannot copy Adventure configuration", e); }
        copy.allowedEditions = editions.toArray(new String[0]);
        copy.restrictedEditions = generationExcludedEditions.toArray(new String[0]);
        copy.restrictedCards = generationBans.toArray(new String[0]);
        source = original; effective = copy;
        return copy;
    }
    @Override public SaveFileData save() {
        SaveFileData data = new SaveFileData();
        data.store("version", 1); data.store("campaignId", campaignId); data.store("preset", preset); data.store("capturedOn", capturedOn);
        store(data, "editions", editions); store(data, "names", names); store(data, "banned", banned);
        store(data, "restricted", restricted); store(data, "generationBans", generationBans);
        store(data, "generationExcludedEditions", generationExcludedEditions);
        store(data, "infrastructureEditions", infrastructureEditions);
        return data;
    }
    private static void store(SaveFileData data, String key, Set<String> values) { data.storeObject(key, values.toArray(new String[0])); }
    private static void read(SaveFileData data, String key, Set<String> values) {
        values.clear(); if (data != null && data.containsKey(key)) add(values, (String[]) data.readObject(key));
    }
    @Override public void load(SaveFileData data) {
        campaignId = data == null ? "" : data.readString("campaignId");
        preset = data == null ? "" : data.readString("preset"); capturedOn = data == null ? "" : data.readString("capturedOn");
        read(data, "editions", editions); read(data, "names", names); read(data, "banned", banned);
        read(data, "restricted", restricted); read(data, "generationBans", generationBans);
        read(data, "generationExcludedEditions", generationExcludedEditions);
        read(data, "infrastructureEditions", infrastructureEditions); source = null; effective = null;
    }
}
