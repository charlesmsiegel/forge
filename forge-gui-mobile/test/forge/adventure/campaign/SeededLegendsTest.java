package forge.adventure.campaign;

import forge.adventure.data.ConfigData;
import forge.adventure.player.AdventurePlayer;
import org.testng.annotations.Test;
import java.util.HashSet;
import static org.testng.Assert.*;

public class SeededLegendsTest extends AdventureTestBase {
    private CampaignConfig.SeedCandidate candidate(String id, String name, String set) {
        CampaignConfig.SeedCandidate candidate = new CampaignConfig.SeedCandidate();
        candidate.id = id; candidate.card = name; candidate.preferredEdition = set; candidate.enemy = "Bandit";
        return candidate;
    }
    private CampaignConfig config() {
        CampaignConfig config = new CampaignConfig(); config.campaignId = "seed-test";
        config.seeds.creatures = new CampaignConfig.SeedCandidate[]{candidate("mirri", "Mirri, Cat Warrior", "EXO"), candidate("crovax", "Crovax the Cursed", "STH")};
        config.seeds.locations = new CampaignConfig.SeedCandidate[]{candidate("pendelhaven", "Pendelhaven", "LEG"), candidate("karakas", "Karakas", "LEG")};
        config.seeds.synergy = new CampaignConfig.SeedCandidate[]{candidate("mine", "Urza's Mine", "ATQ"), candidate("tower", "Urza's Tower", "ATQ"), candidate("plant", "Urza's Power Plant", "ATQ")};
        config.seeds.synergyChancePercent = 100;
        return config;
    }
    private CardUniverse universe(CampaignConfig config) { return CardUniverse.capture(config, new ConfigData()); }
    @Test public void seedAndDataOrderResolveToTheSameExplicitManifest() {
        CampaignConfig config = config(); CardUniverse universe = universe(config);
        SeededLegends first = SeededLegends.capture(12345, config.seeds, universe);
        var swap = config.seeds.creatures[0]; config.seeds.creatures[0] = config.seeds.creatures[1]; config.seeds.creatures[1] = swap;
        SeededLegends second = SeededLegends.capture(12345, config.seeds, universe);
        assertEquals(second.entries(), first.entries());
        assertEquals(first.entries().size(), 5);
        HashSet<String> choices = new HashSet<>();
        for (int seed = 0; seed < 40; seed++) choices.add(SeededLegends.capture(seed, config.seeds, universe).entry("creature").candidateId());
        assertEquals(choices.size(), 2, "Different seeds vary the chosen legendary creature");
    }
    @Test public void cardUniverseFilteringSkipsIneligibleLegendsAndKeepsSynergyAtomic() {
        CampaignConfig config = config(); config.universe.allowedEditions = new String[]{"STH", "EXO"};
        SeededLegends manifest = SeededLegends.capture(20, config.seeds, universe(config));
        assertNotNull(manifest.entry("creature")); assertNull(manifest.entry("location"));
        assertEquals(manifest.entries().size(), 1, "Tron cannot be partly selected");
        config = config(); config.cards.bannedCards = new String[]{"Urza's Tower"};
        manifest = SeededLegends.capture(20, config.seeds, universe(config));
        assertEquals(manifest.entries().size(), 2, "A banned package member excludes the whole package");
    }
    @Test public void saveLoadFreezesExactSelectedIdentitiesAndClaims() {
        CampaignConfig config = config(); SeededLegends manifest = SeededLegends.capture(2026, config.seeds, universe(config));
        SeededLegends loaded = new SeededLegends(); loaded.load(manifest.save());
        assertTrue(loaded.isInitialized()); assertEquals(loaded.seed(), 2026L);
        assertEquals(loaded.entries(), manifest.entries());
        config.seeds.creatures = new CampaignConfig.SeedCandidate[0];
        loaded.initialize(888, config.seeds, universe(config));
        assertEquals(loaded.entries(), manifest.entries(), "Existing manifest cannot reroll");
    }
    @Test public void onlyFirstVictoryMintsTheSelectedPhysicalCardAndDeed() {
        CampaignConfig config = config(); CampaignState previous = CampaignState.instance(); CampaignConfig old = CampaignConfig.instance();
        try {
            CampaignConfig.setInstance(config); CampaignState state = new CampaignState(); CampaignState.setInstance(state);
            state.initializeUniverse(config, new ConfigData()); state.initializeWorld(5678, config);
            AdventurePlayer player = newPlayer(deckOf(card("Island", "TMP"), 40));
            var selected = state.seededLegends().entry("creature");
            assertNull(state.onLandmarkResult("creature", false, player));
            assertEquals(state.onLandmarkResult("creature", true, player), selected.card());
            assertEquals(player.getCards().count(selected.card()), 1);
            assertTrue(state.deeds().has("landmark.creature"));
            assertNull(state.onLandmarkResult("creature", true, player));
            CampaignState restored = new CampaignState(); restored.load(state.save()); CampaignState.setInstance(restored);
            assertTrue(restored.deeds().has("landmark.creature"));
            assertNull(restored.onLandmarkResult("creature", true, player));
            assertEquals(restored.ownedCopies(player).size(), 41);
        } finally { CampaignState.setInstance(previous); CampaignConfig.setInstance(old); }
    }
    @Test public void deedFlagsAreExplicitIdempotentAndLegacySafe() {
        WorldDeeds deeds = new WorldDeeds(); assertFalse(deeds.has("seal.broken"));
        assertTrue(deeds.grant("seal.broken")); assertFalse(deeds.grant("seal.broken"));
        WorldDeeds loaded = new WorldDeeds(); loaded.load(deeds.save()); assertTrue(loaded.has("seal.broken"));
        loaded.load(null); assertFalse(loaded.has("seal.broken"));
        assertThrows(IllegalArgumentException.class, () -> loaded.grant(""));
    }
    @Test public void worldPoiUsesSelectedNameWithoutMutatingSharedTemplate() {
        CampaignConfig config = config(); CampaignState previous = CampaignState.instance(); CampaignConfig old = CampaignConfig.instance();
        try {
            CampaignConfig.setInstance(config); CampaignState state = new CampaignState(); CampaignState.setInstance(state);
            state.initializeUniverse(config, new ConfigData()); state.initializeWorld(22, config);
            forge.adventure.data.PointOfInterestData template = new forge.adventure.data.PointOfInterestData();
            template.name = "SeededChampion"; template.displayName = "A legendary creature"; template.count = 1; template.seedSlot = "creature";
            var resolved = CampaignLandmarks.pointData(template);
            assertNotSame(resolved, template);
            assertEquals(resolved.getDisplayName(), state.seededLegends().entry("creature").card().getName());
            assertEquals(template.displayName, "A legendary creature");
            template.seedSlot = "not-selected";
            assertEquals(CampaignLandmarks.pointData(template).count, 0);
            assertEquals(template.count, 1);
        } finally { CampaignState.setInstance(previous); CampaignConfig.setInstance(old); }
    }

    @Test public void landmarkOpponentUsesItsSelectedCardWithoutChangingTheSharedDeck() {
        CampaignConfig config = config(); SeededLegends manifest = SeededLegends.capture(7, config.seeds, universe(config));
        var entry = manifest.entry("creature");
        forge.deck.Deck source = deckOf(card("Swamp", "TMP"), 18, card("Dauthi Slayer", "TMP"), 22);
        forge.deck.Deck prepared = CampaignLandmarks.encounterDeck(source, entry);
        assertEquals(prepared.getMain().countAll(), 40);
        assertTrue(prepared.getMain().count(entry.card()) > 0);
        assertEquals(source.getMain().count(entry.card()), 0);
        assertEquals(source.getMain().countAll(), 40);
        forge.deck.Deck land = CampaignLandmarks.encounterDeck(source, manifest.entry("location"));
        assertEquals(land.getMain().countAll(), 40);
        assertTrue(land.getMain().count(manifest.entry("location").card()) > 0);
    }

    @Test public void curatedLandmarkMayIntroduceGenerationExcludedCardsButNotExplicitBans() {
        CampaignConfig config = config();
        var cradle = candidate("cradle", "Gaea's Cradle", "USG");
        config.seeds.locations = new CampaignConfig.SeedCandidate[]{cradle};
        ConfigData plane = new ConfigData(); plane.restrictedCards = new String[]{"Gaea's Cradle"};
        CardUniverse universe = CardUniverse.capture(config, plane);
        assertNull(SeededLegends.capture(1, config.seeds, universe).entry("location"));
        cradle.allowGenerationExcluded = true;
        assertNotNull(SeededLegends.capture(1, config.seeds, universe).entry("location"));
        config.cards.bannedCards = new String[]{"Gaea's Cradle"};
        assertNull(SeededLegends.capture(1, config.seeds, CardUniverse.capture(config, plane)).entry("location"));
    }

}
