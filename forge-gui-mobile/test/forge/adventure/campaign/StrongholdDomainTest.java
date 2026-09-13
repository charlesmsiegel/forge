package forge.adventure.campaign;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import forge.adventure.data.EnemyData;
import forge.deck.Deck;
import forge.deck.io.DeckSerializer;
import forge.item.PaperCard;
import org.testng.annotations.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

/**
 * Milestone 5: Stronghold opponents use Stronghold cards only (plus ordinary basic lands);
 * the player is not restricted (MVP.md §22).
 */
public class StrongholdDomainTest extends AdventureTestBase {
    private static final String DECK_DIR = PLANE_DIR + "decks/stronghold/";

    private static List<File> deckFiles() {
        File[] files = new File(DECK_DIR).listFiles((d, n) -> n.endsWith(".dck"));
        assertNotNull(files, "no Stronghold deck directory at " + DECK_DIR);
        return List.of(files);
    }

    @Test
    public void everyStrongholdOpponentDeckUsesOnlyStrongholdNonbasics() {
        List<File> files = deckFiles();
        assertTrue(files.size() >= 6, "expected a small library of Stronghold decks, found " + files.size());
        CampaignConfig config = CampaignConfig.instance();
        List<String> allowed = List.of(config.stronghold.editions);
        for (File file : files) {
            Deck deck = DeckSerializer.fromFile(file);
            assertNotNull(deck, "unreadable deck " + file);
            int size = deck.getMain().countAll();
            assertTrue(size >= 40, file.getName() + " has only " + size + " cards");
            for (Map.Entry<PaperCard, Integer> e : deck.getMain()) {
                PaperCard card = e.getKey();
                if (AnteService.isOrdinaryBasicLand(card))
                    continue;
                assertTrue(allowed.contains(card.getEdition()),
                        file.getName() + ": " + card.getName() + " is printed as " + card.getEdition());
                assertFalse(card.getRules().isUnsupported(), file.getName() + ": " + card.getName() + " unsupported");
            }
        }
    }

    @Test
    public void everyStrongholdEnemyReferencesExistingDecks() {
        FileHandle handle = new FileHandle(PLANE_DIR + "world/enemies_extra.json");
        assertTrue(handle.exists());
        Array<EnemyData> enemies = new Json().fromJson(Array.class, EnemyData.class, handle);
        assertTrue(enemies.size >= 5);
        boolean boss = false;
        for (EnemyData enemy : enemies) {
            assertNotNull(enemy.deck, enemy.name + " has no decks");
            for (String path : enemy.deck)
                assertTrue(new File(PLANE_DIR + path).isFile(), enemy.name + " references missing deck " + path);
            assertTrue(new File(PLANE_DIR + "../common/" + enemy.sprite).isFile(), enemy.name + " sprite missing: " + enemy.sprite);
            boss |= enemy.boss;
        }
        assertTrue(boss, "Stronghold needs a final encounter");
    }

    @Test
    public void sliverQueenIsObtainableButNotGuaranteed() {
        List<String> decksWithQueen = new ArrayList<>();
        for (File file : deckFiles()) {
            Deck deck = DeckSerializer.fromFile(file);
            if (deck.getMain().countByName("Sliver Queen") > 0)
                decksWithQueen.add(file.getName());
        }
        assertFalse(decksWithQueen.isEmpty(), "some Stronghold opponent must run Sliver Queen");
        assertTrue(decksWithQueen.size() < deckFiles().size(), "but not every opponent");
    }

    @Test
    public void thePlayerIsNotRestrictedToStrongholdCards() {
        Deck modern = deckOf(card("Lightning Bolt", "M10"), 3, card("Mountain", "M10"), 37);
        assertTrue(DeckValidator.isValid(modern, modern.getAllCardsInASinglePool(true, true), CampaignConfig.instance()));
    }
}
