package forge.adventure.campaign;

import forge.adventure.data.AdventureEventData;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.util.AdventureEventController.EventFormat;
import forge.adventure.util.AdventureEventController.EventStatus;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.model.CardBlock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import java.io.*;
import static org.testng.Assert.*;

public class CampaignLimitedTest extends AdventureTestBase {
    @BeforeMethod public void resetState() { CampaignState.setInstance(new CampaignState()); }
    @AfterMethod public void clearState() { CampaignState.setInstance(new CampaignState()); }
    private AdventureEventData event(EventFormat format) {
        AdventureEventData event = new AdventureEventData(123L, format, (CardBlock)null);
        event.eventStatus = EventStatus.Completed;
        event.matchesWon = 0; event.matchesLost = 1;
        event.rewardDeck = deckOf(card("Muscle Sliver", "TMP"), 2, card("Island", "TMP"), 1);
        event.rewardDeck.getOrCreate(DeckSection.Sideboard).add(card("Shock", "STH"), 3);
        event.registeredDeck = deckOf(card("Island", "TMP"), 40);
        return event;
    }
    private AdventurePlayer player() { return newPlayer(deckOf(card("Island", "TMP"), 40)); }
    @Test public void losingParticipantsKeepTheActualPoolAsPhysicalCards() {
        AdventurePlayer player = player(); AdventureEventData event = event(EventFormat.Sealed);
        assertEquals(CampaignLimited.claimPool(event, player), 6);
        assertEquals(player.getCards().count(card("Muscle Sliver", "TMP")), 2);
        assertEquals(player.getCards().count(card("Shock", "STH")), 3);
        assertEquals(player.getCards().count(card("Island", "TMP")), 41, "Only the opened basic is kept; virtual deck lands are excluded");
        assertEquals(CampaignState.instance().ownedCopies(player).size(), 46);
        assertEquals(CampaignLimited.claimPool(event, player), 0);
    }
    @Test public void claimedGuardSurvivesEventSerialization() throws Exception {
        AdventurePlayer player = player(); AdventureEventData event = event(EventFormat.Draft);
        assertEquals(CampaignLimited.claimPool(event, player), 6);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) { out.writeObject(event); }
        AdventureEventData loaded;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            loaded = (AdventureEventData)in.readObject();
        }
        assertEquals(CampaignLimited.claimPool(loaded, player), 0);
        assertEquals(player.getCards().countAll(), 46);
    }
    @Test public void eventCardsJoinCarriedAvailabilityDuringExpeditions() {
        AdventurePlayer player = player(); CampaignState state = CampaignState.instance();
        state.enterExpedition("limited-test", player);
        assertEquals(CampaignLimited.claimPool(event(EventFormat.Draft), player), 6);
        assertEquals(state.expedition().getAcquired().countAll(), 6);
        assertEquals(state.availableCards(player).countAll(), 46);
    }
    @Test public void withdrawalRetainsOnlyActualPicksAndPhantomRetainsNothing() {
        AdventurePlayer player = player(); AdventureEventData event = event(EventFormat.Draft);
        event.eventStatus = EventStatus.Abandoned; event.rewardDeck = null;
        event.registeredDeck = deckOf(card("Shock", "STH"), 2, card("Island", "TMP").getNoSellVersion(), 20);
        assertEquals(CampaignLimited.claimPool(event, player), 2);
        AdventureEventData phantom = event(EventFormat.Sealed); phantom.campaignPhantom = true;
        assertEquals(CampaignLimited.claimPool(phantom, player), 0);
        AdventureEventData unpaid = event(EventFormat.Sealed); unpaid.eventStatus = EventStatus.Available;
        assertThrows(IllegalStateException.class, () -> CampaignLimited.claimPool(unpaid, player));
    }
    @Test public void organizerResumesPaidEventsAndValidatesHistoricalSetsBeforeRegistration() {
        AdventurePlayer player = player(); CampaignConfig config = CampaignConfig.instance();
        CampaignConfig.LimitedOrganizer organizer = new CampaignConfig.LimitedOrganizer();
        organizer.id = "rath-limited"; organizer.name = "Rath Organizers"; organizer.block = "Tempest";
        AdventureEventData created = CampaignLimited.createOrResume(player, organizer, "world-1|tempest", 901, EventFormat.Sealed, 20000);
        assertEquals(created.packConfiguration.length, 6);
        assertNotEquals(created.eventSeed, 0L, "The chosen event seed must be saved, including negative seeds");
        assertEquals(player.getEvents().size(), 1);
        created.eventStatus = EventStatus.Entered;
        assertSame(CampaignLimited.createOrResume(player, organizer, "world-1|tempest", 901, EventFormat.Draft, 20001), created);
        assertEquals(player.getEvents().size(), 1);
        CampaignConfig subset = new CampaignConfig(); subset.campaignId = config.campaignId;
        subset.universe.allowedEditions = new String[]{"STH"};
        CampaignState.instance().initializeUniverse(subset, new forge.adventure.data.ConfigData());
        assertThrows(IllegalArgumentException.class, () -> CampaignLimited.createOrResume(player, organizer, "world-2|tempest", 901, EventFormat.Draft, 20000));
        assertEquals(player.getEvents().size(), 1, "Rejected product must not register an event");
    }

}
