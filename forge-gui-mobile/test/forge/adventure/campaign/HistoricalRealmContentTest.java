package forge.adventure.campaign;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import forge.deck.Deck;
import forge.deck.io.DeckSerializer;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.*;

public class HistoricalRealmContentTest extends AdventureTestBase {
    private CampaignConfig config() { return CampaignConfig.load(PLANE_DIR + "campaign.json"); }

    private static Document map(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new File(PLANE_DIR + path));
    }

    private static Map<String, String> props(Element object) {
        Map<String, String> properties = new HashMap<>();
        NodeList list = object.getElementsByTagName("property");
        for (int i = 0; i < list.getLength(); i++) {
            Element property = (Element) list.item(i);
            properties.put(property.getAttribute("name"), property.getAttribute("value"));
        }
        return properties;
    }

    private static JsonValue json(String path) throws Exception {
        return new JsonReader().parse(Files.readString(new File(PLANE_DIR + path).toPath()));
    }

    @Test
    public void rathContainsThreeReachableChaptersAndLegacyStrongholdRewardIdentity() throws Exception {
        HistoricalRealm realm = config().realm("rath");
        assertEquals(Arrays.stream(realm.chapters).map(c -> c.id).toArray(), new String[]{"tempest", "stronghold", "exodus"});
        assertEquals(realm.chapters[1].legacyRegion, "stronghold");
        for (HistoricalRealm.Chapter chapter : realm.chapters) {
            assertTrue(new File(PLANE_DIR + chapter.entryMap).isFile(), chapter.entryMap);
            for (String path : chapter.maps) assertTrue(new File(PLANE_DIR + path).isFile(), path);
            RegionCompletion.boosterTemplate(chapter.boosterEdition);
        }
        // Existing Stronghold POIs in old worlds expose the new block portal too.
        NodeList objects = map("maps/map/stronghold_gate.tmx").getElementsByTagName("object");
        boolean entrance = false;
        for (int i = 0; i < objects.getLength(); i++) {
            Element object = (Element) objects.item(i);
            Map<String, String> p = props(object);
            entrance |= "expeditionGate".equals(object.getAttribute("type")) && "rath".equals(p.get("region"))
                    && "enter".equals(p.get("mode"));
        }
        assertTrue(entrance, "existing saves need a reachable block entrance");
    }

    @Test
    public void everyChapterHasDomainCorrectEnemiesLootTraderAndGuardedExit() throws Exception {
        Map<String, JsonValue> enemies = new HashMap<>();
        for (JsonValue enemy : json("world/enemies_extra.json")) enemies.put(enemy.getString("name"), enemy);
        Map<String, JsonValue> shops = new HashMap<>();
        for (JsonValue shop : json("world/shops.json")) shops.put(shop.getString("name"), shop);
        for (HistoricalRealm realm : config().realms) for (HistoricalRealm.Chapter chapter : realm.chapters) {
            int encounters = 0;
            int bosses = 0;
            int exits = 0;
            int traders = 0;
            for (String path : chapter.maps) {
                NodeList objects = map(path).getElementsByTagName("object");
                for (int i = 0; i < objects.getLength(); i++) {
                    Element object = (Element) objects.item(i);
                    Map<String, String> p = props(object);
                    String enemyName = p.get("enemy");
                    if (enemyName != null) {
                        encounters++;
                        JsonValue enemy = enemies.get(enemyName);
                        assertNotNull(enemy, enemyName);
                        for (JsonValue deckPath : enemy.get("deck")) {
                            File file = new File(PLANE_DIR + deckPath.asString());
                            assertTrue(file.isFile(), file.toString());
                            Deck deck = DeckSerializer.fromFile(file);
                            assertTrue(deck.getMain().countAll() >= 40, file.toString());
                            for (var entry : deck.getMain()) {
                                var card = entry.getKey();
                                assertFalse(card.getRules().isUnsupported(), card.getName());
                                assertTrue(AnteService.isOrdinaryBasicLand(card) || Arrays.asList(chapter.editions).contains(card.getEdition()),
                                        file + ": " + card);
                            }
                        }
                        assertRewardDomain(enemy.get("rewards"), chapter);
                        if (chapter.id.equals(p.get("completesChapter")) || (!chapter.legacyRegion.isEmpty()
                                && chapter.legacyRegion.equals(p.get("completesRegion")))) bosses++;
                    }
                    if (p.containsKey("reward")) assertRewardDomain(new JsonReader().parse(p.get("reward")), chapter);
                    if (p.containsKey("shopList")) {
                        traders++;
                        assertNotNull(shops.get(p.get("shopList")));
                        assertRewardDomain(shops.get(p.get("shopList")).get("rewards"), chapter);
                    }
                    if ("advance".equals(p.get("mode")) && realm.id.equals(p.get("region"))) exits++;
                    if (p.containsKey("teleport") && !p.get("teleport").isEmpty())
                        assertTrue(new File(PLANE_DIR + p.get("teleport")).isFile(), p.get("teleport"));
                }
            }
            assertTrue(encounters >= 3, chapter.id + " needs encounter variety");
            assertEquals(bosses, 1, chapter.id + " needs one completion encounter");
            assertTrue(exits > 0, chapter.id + " needs a guarded continuation/completion exit");
            assertTrue(traders > 0, chapter.id + " needs historical stock");
        }
    }

    private static void assertRewardDomain(JsonValue rewards, HistoricalRealm.Chapter chapter) {
        if (rewards == null) return;
        for (JsonValue reward : rewards) {
            String type = reward.getString("type", "card");
            if (type.equals("gold") || type.equals("deckCard")) continue;
            assertNotNull(reward.get("editions"), chapter.id + ": unscoped card reward");
            for (JsonValue edition : reward.get("editions"))
                assertTrue(Arrays.asList(chapter.editions).contains(edition.asString()), chapter.id + ": " + edition.asString());
        }
    }
}
