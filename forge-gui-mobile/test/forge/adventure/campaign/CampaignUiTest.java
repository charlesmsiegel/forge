package forge.adventure.campaign;

import forge.adventure.data.ConfigData;
import forge.card.ColorSet;
import forge.card.MagicColor;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class CampaignUiTest extends AdventureTestBase {
    @Test public void journalUsesReadableNamesAndDoesNotCreateRealmProgress() {
        CampaignConfig config = CampaignConfig.load(PLANE_DIR + "campaign.json");
        CampaignState state = new CampaignState();
        state.initializeUniverse(config, new ConfigData()); state.initializeWorld(17, config);
        var before = state.save();
        var sections = CampaignJournal.sections(config, state);
        assertTrue(sections.stream().anyMatch(section -> section.title().equals("Shandalar")));
        String text = sections.toString();
        assertTrue(text.contains("Mage")); assertTrue(text.contains("Tempest")); assertTrue(text.contains("White Castle Guardian"));
        assertFalse(text.contains("guardian.white")); assertFalse(text.contains("legend.urza.conquered"));
        assertEquals(state.save().keySet(), before.keySet());
        assertEquals(state.save().readSubData("deeds").readObject("flags"), before.readSubData("deeds").readObject("flags"));
        state.deeds().grant("seal.broken"); state.deeds().grant("legend.urza.conquered");
        assertTrue(CampaignJournal.sections(config, state).toString().contains("Planeswalker"));
    }
    @Test public void creationPresetDoesNotMutatePlaneConfigAndStarterIsLegal() {
        CampaignConfig config = CampaignConfig.load(PLANE_DIR + "campaign.json");
        String original = config.universe.preset;
        var selected = CampaignSetup.withPreset(config, "Modern");
        assertEquals(config.universe.preset, original); assertEquals(selected.universe.preset, "Modern");
        CampaignState state = new CampaignState(); CampaignState.setInstance(state);
        state.initializeUniverse(selected, new ConfigData());
        var universe = state.universeFor(selected);
        var source = deckOf(card("Black Lotus", "LEA"), 4, card("Lightning Bolt", "M10"), 8, card("Mountain", "TMP"), 28);
        var starter = CampaignSetup.starter(source, ColorSet.fromNames("R"), selected, universe);
        assertEquals(starter.getMain().countAll(), 40);
        assertEquals(starter.getMain().countByName("Black Lotus"), 0);
        assertTrue(starter.getMain().countByName("Lightning Bolt") <= 3);
        assertTrue(DeckValidator.isValid(starter, starter.getMain(), selected));
        assertEquals(source.getMain().countByName("Black Lotus"), 4);
        int creatures = 0; for (var entry : starter.getMain()) if (entry.getKey().getRules().getType().isCreature()) creatures += entry.getValue();
        assertTrue(creatures > 0, "A legal starter must have a way to win");
    }
    @Test public void setupPreservesAnAlreadyLegalStarterAndExistingSnapshot() {
        CampaignConfig config = CampaignConfig.load(PLANE_DIR + "campaign.json");
        CampaignState state = new CampaignState(); CampaignState.setInstance(state); state.initializeUniverse(config, new ConfigData());
        var source = deckOf(card("Grizzly Bears", "LEA"), 3, card("Forest", "TMP"), 37);
        var result = CampaignSetup.starter(source, ColorSet.fromNames("G"), config, state.universeFor(config));
        assertEquals(result.getMain(), source.getMain());
        var frozen = state.universeFor(config).save();
        CampaignSetup.withPreset(config, "Modern");
        assertEquals(state.universeFor(config).save().readObject("names"), frozen.readObject("names"));
    }
    @Test public void repairingBannedSpellsDoesNotLeaveAnAllLandStarter() {
        CampaignConfig config = CampaignConfig.load(PLANE_DIR + "campaign.json");
        var state = new CampaignState(); CampaignState.setInstance(state); state.initializeUniverse(CampaignSetup.withPreset(config, "Modern"), new ConfigData());
        var starter = CampaignSetup.starter(deckOf(card("Mountain", "TMP"), 40, card("Black Lotus", "LEA"), 1),
                ColorSet.fromNames("R"), config, state.universeFor(config));
        assertEquals(starter.getMain().countAll(), 40);
        assertTrue(starter.getMain().toFlatList().stream().anyMatch(card -> card.getRules().getType().isCreature()));
    }
    @Test public void anIllegalCommanderStarterIsRefusedInsteadOfLosingItsCommander() {
        CampaignConfig config = CampaignConfig.load(PLANE_DIR + "campaign.json");
        var state = new CampaignState(); CampaignState.setInstance(state); state.initializeUniverse(config, new ConfigData());
        var deck = deckOf(card("Forest", "TMP"), 99, card("Grizzly Bears", "LEA"), 8);
        deck.getOrCreate(forge.deck.DeckSection.Commander).add(card("Mirri, Cat Warrior", "EXO"));
        assertThrows(IllegalArgumentException.class, () -> CampaignSetup.starter(deck, ColorSet.fromNames("G"), config, state.universeFor(config)));
        assertEquals(deck.getCommanders().size(), 1);
    }

    @Test public void spellOnlyInvalidStarterGetsManaAndAllBasicsGetsAWinCondition() {
        CampaignConfig config = CampaignConfig.load(PLANE_DIR + "campaign.json");
        var state = new CampaignState(); CampaignState.setInstance(state); state.initializeUniverse(CampaignSetup.withPreset(config, "Modern"), new ConfigData());
        var repaired = CampaignSetup.starter(deckOf(card("Black Lotus", "LEA"), 40), ColorSet.fromNames("R"), config, state.universeFor(config));
        assertEquals(repaired.getMain().countAll(), 40);
        assertTrue(repaired.getMain().countByName("Mountain") >= 16);
        assertTrue(repaired.getMain().toFlatList().stream().anyMatch(card -> card.getRules().getType().isCreature()));
        var basics = CampaignSetup.starter(deckOf(card("Mountain", "TMP"), 40), ColorSet.fromNames("R"), config, state.universeFor(config));
        assertTrue(basics.getMain().toFlatList().stream().anyMatch(card -> card.getRules().getType().isCreature()));
    }

    @Test public void repairDoesNotKeepUncastableOffColorCreatures() {
        CampaignConfig config = CampaignConfig.load(PLANE_DIR + "campaign.json");
        var state = new CampaignState(); CampaignState.setInstance(state); state.initializeUniverse(config, new ConfigData());
        var blue = deckOf(card("Air Elemental", "LEA"), 20, card("Sage Owl", "EXO"), 20);
        var result = CampaignSetup.starter(blue, ColorSet.fromNames("G"), config, state.universeFor(config));
        assertTrue(result.getMain().countByName("Forest") >= 16);
        for (var printing : result.getMain()) assertEquals(printing.getKey().getRules().getManaCost().getColorProfile() & ~MagicColor.GREEN, 0);
        assertTrue(result.getMain().toFlatList().stream().anyMatch(card -> card.getRules().getType().isCreature()));
    }

    @Test public void validCommanderStarterKeepsItsCommandZone() {
        CampaignConfig config = CampaignConfig.load(PLANE_DIR + "campaign.json");
        var state = new CampaignState(); CampaignState.setInstance(state); state.initializeUniverse(config, new ConfigData());
        var source = deckOf(card("Forest", "TMP"), 99);
        source.getOrCreate(forge.deck.DeckSection.Commander).add(card("Mirri, Cat Warrior", "EXO"));
        var result = CampaignSetup.starter(source, ColorSet.fromNames("G"), config, state.universeFor(config));
        assertEquals(result.getMain(), source.getMain()); assertEquals(result.getCommanders(), source.getCommanders());
    }

}
