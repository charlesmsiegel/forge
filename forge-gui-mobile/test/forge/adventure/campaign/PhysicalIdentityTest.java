package forge.adventure.campaign;

import forge.adventure.player.AdventurePlayer;
import forge.adventure.util.SaveFileData;
import forge.deck.Deck;
import forge.item.PaperCard;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.*;
import java.util.List;

import static org.testng.Assert.*;

public class PhysicalIdentityTest extends AdventureTestBase {
    @BeforeMethod
    public void resetCampaign() { CampaignState.setInstance(new CampaignState()); }
    @AfterMethod public void clearCampaign() { CampaignState.setInstance(new CampaignState()); }

    @Test
    public void identicalCopiesHaveStableDistinctIdsAndSaleRetiresOnlyOne() {
        PaperCard bolt = card("Lightning Bolt", "M10");
        AdventurePlayer player = newPlayer(deckOf(bolt, 2));
        CampaignState state = CampaignState.instance();
        List<OwnedCard> original = state.ownedCopies(player);
        assertEquals(original.size(), 2);
        assertNotEquals(original.get(0).id, original.get(1).id);
        assertTrue(CardOwnership.removeCopy(player, original.get(0)));
        assertEquals(state.ownedCopies(player), List.of(original.get(1)));
        player.addCard(bolt);
        assertEquals(state.ownedCopies(player).size(), 2);
        assertFalse(state.ownedCopies(player).stream().anyMatch(c -> c.id.equals(original.get(0).id)));
    }

    @Test
    public void losingCarriedCopyCannotMakeIdenticalHomeCopyAvailable() {
        PaperCard bolt = card("Lightning Bolt", "M10");
        AdventurePlayer player = newPlayer(deckOf(bolt, 1));
        player.addCard(bolt);
        CampaignState state = CampaignState.instance();
        state.enterExpedition("stronghold", player);
        OwnedCard carried = state.availableCopies(player).get(0);
        assertTrue(CardOwnership.removeCopy(player, carried));
        assertEquals(player.getCards().count(bolt), 1);
        assertTrue(state.availableCopies(player).isEmpty());
        assertEquals(state.availableCards(player).count(bolt), 0);
        assertFalse(CardOwnership.removeCopy(player, state.ownedCopies(player).get(0)));
    }

    @Test
    public void serializedCampaignPreservesIdsAndConfiguration() throws Exception {
        PaperCard angel = card("Serra Angel", "7ED").getFoiled();
        AdventurePlayer player = newPlayer(deckOf(angel, 2));
        CampaignState state = CampaignState.instance();
        state.enterExpedition("stronghold", player);
        List<OwnedCard> original = state.ownedCopies(player);
        CampaignState loaded = new CampaignState();
        loaded.load(roundTrip(state.save()));
        loaded.initializeOwnership(player);
        assertEquals(loaded.ownedCopies(player), original);
        assertEquals(loaded.availableCopies(player), original);
        assertEquals(loaded.save().readString("campaignId"), CampaignConfig.instance().campaignId);
        assertEquals(loaded.ownedCopies(player).get(0).card, angel);
    }

    @Test
    public void legacyMigrationMintsIdsOnceIncludingExpeditionAndRivalCopies() throws Exception {
        PaperCard bolt = card("Lightning Bolt", "M10");
        AdventurePlayer player = newPlayer(deckOf(bolt, 1));
        player.addCard(bolt);
        ExpeditionState oldExpedition = new ExpeditionState();
        oldExpedition.enter("stronghold", deckOf(bolt, 1));
        SaveFileData legacy = new SaveFileData();
        legacy.store("version", 1);
        legacy.store("expedition", oldExpedition.save());
        CampaignState migrated = new CampaignState();
        migrated.load(roundTrip(legacy));
        migrated.initializeOwnership(player);
        List<OwnedCard> ids = migrated.ownedCopies(player);
        assertEquals(ids.size(), 2);
        assertEquals(migrated.availableCopies(player).size(), 1);
        CampaignState reloaded = new CampaignState();
        reloaded.load(roundTrip(migrated.save()));
        reloaded.initializeOwnership(player);
        assertEquals(reloaded.ownedCopies(player), ids);
        assertEquals(reloaded.availableCopies(player), migrated.availableCopies(player));

        Rival oldRival = new Rival("legacy-rival", "Krag", "Goblin");
        oldRival.addTaken(bolt);
        SaveFileData oldRivalData = oldRival.save();
        oldRivalData.remove("takenCopies");
        Rival migratedRival = new Rival(); migratedRival.load(roundTrip(oldRivalData));
        Rival reloadedRival = new Rival(); reloadedRival.load(roundTrip(migratedRival.save()));
        assertEquals(reloadedRival.getTakenCopies(), migratedRival.getTakenCopies());
        assertNotEquals(migratedRival.getTakenCopies().get(0).id, ids.get(0).id);
    }

    @Test
    public void configurationVersionRemainsPinnedWhenInstalledConfigurationChanges() throws Exception {
        CampaignConfig config = CampaignConfig.instance();
        int originalVersion = config.version;
        try {
            config.version = 7;
            AdventurePlayer player = newPlayer(deckOf(card("Lightning Bolt", "M10"), 1));
            SaveFileData saved = roundTrip(CampaignState.instance().save());
            config.version = 8;
            CampaignState restored = new CampaignState(); restored.load(saved);
            restored.initializeOwnership(player);
            assertEquals(restored.save().readInt("configVersion"), 7);
        } finally { config.version = originalVersion; }
    }

    @Test
    public void sellingAvailableCopyRetiresItsIdAndReportsActualCountLeavingHomeCopy() {
        PaperCard bolt = card("Lightning Bolt", "M10");
        AdventurePlayer player = newPlayer(deckOf(bolt, 1));
        player.addCard(bolt);
        CampaignState state = CampaignState.instance();
        state.enterExpedition("stronghold", player);
        OwnedCard carried = state.availableCopies(player).get(0);
        OwnedCard home = state.ownedCopies(player).stream().filter(c -> !c.id.equals(carried.id)).findFirst().orElseThrow();
        assertEquals(player.sellCard(bolt, 2), 1);
        assertEquals(state.ownedCopies(player), List.of(home));
        assertTrue(state.availableCopies(player).isEmpty());
        assertEquals(player.sellCard(bolt, 1), 0);
    }

    static SaveFileData roundTrip(SaveFileData data) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) { out.writeObject(data); }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (SaveFileData) in.readObject();
        }
    }
}
