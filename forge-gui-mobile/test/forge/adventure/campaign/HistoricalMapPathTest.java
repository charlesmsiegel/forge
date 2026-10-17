package forge.adventure.campaign;

import org.testng.annotations.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.InflaterInputStream;
import static org.testng.Assert.*;

/** Check authored routing against real tileset collision geometry, not merely filenames. */
public class HistoricalMapPathTest extends AdventureTestBase {
    private static Element xml(File file) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(file).getDocumentElement();
    }

    @Test
    public void expeditionPortalsAreReachableFromMapEntrance() throws Exception {
        CampaignConfig config = CampaignConfig.load(PLANE_DIR + "campaign.json");
        Set<String> paths = new HashSet<>();
        paths.add("maps/map/stronghold_gate.tmx");
        paths.add("maps/map/sr_town.tmx");
        paths.add("maps/map/realm_crossroads.tmx");
        for (HistoricalRealm realm : config.realms)
            for (HistoricalRealm.Chapter chapter : realm.chapters) java.util.Collections.addAll(paths, chapter.maps);
        for (CampaignConfig.LandmarkRoute route : config.seeds.routes) paths.add(route.map);
        for (var encounter : config.story.encounters) paths.add(encounter.map);
        for (var path : config.training) paths.add(path.map);
        for (String path : paths) checkMap(new File(PLANE_DIR + path));
    }

    private static void checkMap(File file) throws Exception {
        Element root = xml(file);
        int width = Integer.parseInt(root.getAttribute("width"));
        int height = Integer.parseInt(root.getAttribute("height"));
        TreeMap<Integer, Element> tilesets = new TreeMap<>();
        NodeList sets = root.getElementsByTagName("tileset");
        for (int i = 0; i < sets.getLength(); i++) {
            Element set = (Element) sets.item(i);
            tilesets.put(Integer.parseInt(set.getAttribute("firstgid")), xml(new File(file.getParentFile(), set.getAttribute("source"))));
        }
        Map<Integer, Boolean> blockedTiles = new HashMap<>();
        boolean[] blocked = new boolean[width * height];
        NodeList layers = root.getElementsByTagName("layer");
        for (int l = 0; l < layers.getLength(); l++) {
            Element data = (Element) ((Element) layers.item(l)).getElementsByTagName("data").item(0);
            try (var stream = new InflaterInputStream(new ByteArrayInputStream(Base64.getMimeDecoder().decode(data.getTextContent())))) {
                ByteBuffer ids = ByteBuffer.wrap(stream.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < blocked.length; i++) {
                    int gid = ids.getInt() & 0x1fffffff;
                    if (gid == 0) continue;
                    Boolean collision = blockedTiles.get(gid);
                    if (collision == null) {
                        collision = false;
                        var set = tilesets.floorEntry(gid);
                        NodeList tiles = set.getValue().getElementsByTagName("tile");
                        for (int t = 0; t < tiles.getLength(); t++) {
                            Element tile = (Element) tiles.item(t);
                            if (Integer.parseInt(tile.getAttribute("id")) != gid - set.getKey()) continue;
                            NodeList objects = tile.getElementsByTagName("object");
                            for (int o = 0; o < objects.getLength(); o++) {
                                Element object = (Element) objects.item(o);
                                float x = number(object, "x"), y = number(object, "y");
                                if (x <= 8 && x + number(object, "width") > 8 && y <= 8 && y + number(object, "height") > 8) collision = true;
                            }
                        }
                        blockedTiles.put(gid, collision);
                    }
                    blocked[i] |= collision;
                }
            }
        }
        NodeList objects = root.getElementsByTagName("object");
        for (int i = 0; i < objects.getLength(); i++) {
            Element object = (Element) objects.item(i); boolean collision = object.getAttribute("type").equals("collision");
            NodeList properties = object.getElementsByTagName("property");
            for (int p = 0; p < properties.getLength(); p++) {
                Element property = (Element)properties.item(p);
                collision |= property.getAttribute("name").equals("type") && property.getAttribute("value").equals("collision");
            }
            if (collision) for (int cell = 0; cell < blocked.length; cell++) {
                float x = (cell % width) * 16 + 8, y = (cell / width) * 16 + 8;
                if (x >= number(object, "x") && x < number(object, "x") + number(object, "width")
                        && y >= number(object, "y") && y < number(object, "y") + number(object, "height")) blocked[cell] = true;
            }
        }
        int start = -1;
        Set<Integer> gates = new HashSet<>();
        for (int i = 0; i < objects.getLength(); i++) {
            Element object = (Element) objects.item(i);
            int cell = (int) ((number(object, "y") - 8) / 16) * width + (int) ((number(object, "x") + 8) / 16);
            NodeList props = object.getElementsByTagName("property");
            for (int p = 0; p < props.getLength(); p++)
                if (((Element)props.item(p)).getAttribute("name").equals("seedSlot") || ((Element)props.item(p)).getAttribute("name").equals("storyEncounter") || ((Element)props.item(p)).getAttribute("name").equals("completesChapter") || ((Element)props.item(p)).getAttribute("name").equals("campaignLore") || ((Element)props.item(p)).getAttribute("name").equals("trainingLesson")) gates.add(cell);
            if (object.getAttribute("id").equals("38") || (start < 0 && object.getAttribute("template").endsWith("/portal.tx"))) start = cell;
            if (object.getAttribute("type").equals("expeditionGate") || object.getAttribute("type").equals("campaignServices") || object.getAttribute("type").equals("campaignLimited") || object.getAttribute("type").equals("campaignChronicler")) gates.add(cell);
        }
        assertTrue(start >= 0 && start < blocked.length, file + " has no entrance");
        Set<Integer> reached = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(start); reached.add(start);
        while (!queue.isEmpty()) {
            int cell = queue.remove();
            for (int delta : new int[]{-1, 1, -width, width}) {
                int next = cell + delta;
                if (next < 0 || next >= blocked.length || Math.abs(next % width - cell % width) > 1 || blocked[next]) continue;
                if (reached.add(next)) queue.add(next);
            }
        }
        for (int gate : gates) assertTrue(reached.contains(gate), file.getName() + " has an unreachable expedition portal at tile " + gate % width + "," + gate / width);
    }

    @Test
    public void authoredEntrancesClearActualHeroCollider() throws Exception {
        var config=CampaignConfig.load(PLANE_DIR+"campaign.json");
        Set<String> maps=new HashSet<>();
        for(var encounter:config.story.encounters)maps.add(encounter.map);
        for(var realm:config.realms)for(var chapter:realm.chapters)java.util.Collections.addAll(maps,chapter.maps);
        for(String path:maps) {
            File file=new File(PLANE_DIR+path);Element root=xml(file);
            boolean authored=false;var props=root.getElementsByTagName("property");
            for(int i=0;i<props.getLength();i++)if(((Element)props.item(i)).getAttribute("name").equals("campaignLayoutRevision"))authored=true;
            if(!authored)continue;
            Element entry=null;var objects=root.getElementsByTagName("object");
            for(int i=0;i<objects.getLength();i++)if(((Element)objects.item(i)).getAttribute("id").equals("38"))entry=(Element)objects.item(i);
            assertNotNull(entry,path);
            // EntryActor.up and CharacterSprite's 16px hero collider, expressed in Tiled coordinates.
            float px=number(entry,"x")+4,py=number(entry,"y")-16-6.4f;
            int width=Integer.parseInt(root.getAttribute("width"));
            TreeMap<Integer,Element> sets=new TreeMap<>();var nodes=root.getElementsByTagName("tileset");
            for(int i=0;i<nodes.getLength();i++){var node=(Element)nodes.item(i);sets.put(Integer.parseInt(node.getAttribute("firstgid")),xml(new File(file.getParentFile(),node.getAttribute("source"))));}
            var layers=root.getElementsByTagName("layer");
            for(int l=0;l<layers.getLength();l++){
                var data=(Element)((Element)layers.item(l)).getElementsByTagName("data").item(0);
                try(var in=new InflaterInputStream(new ByteArrayInputStream(Base64.getMimeDecoder().decode(data.getTextContent())))){
                    var ids=ByteBuffer.wrap(in.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
                    for(int cell=0;ids.hasRemaining();cell++){
                        int gid=ids.getInt()&0x1fffffff;float tx=(cell%width)*16,ty=(cell/width)*16;
                        if(gid==0||tx+16<=px||tx>=px+10||ty+16<=py||ty>=py+6.4f)continue;
                        var set=sets.floorEntry(gid);var tiles=set.getValue().getElementsByTagName("tile");
                        for(int t=0;t<tiles.getLength();t++){
                            var tile=(Element)tiles.item(t);if(Integer.parseInt(tile.getAttribute("id"))!=gid-set.getKey())continue;
                            var collisions=tile.getElementsByTagName("object");
                            for(int c=0;c<collisions.getLength();c++){
                                var box=(Element)collisions.item(c);float x=tx+number(box,"x"),y=ty+number(box,"y");
                                assertFalse(px<x+number(box,"width")&&px+10>x&&py<y+number(box,"height")&&py+6.4f>y,
                                        path+" spawns hero inside tile collision at "+tx+","+ty);
                            }
                        }
                    }
                }
            }
        }
    }

    private static float number(Element element, String attribute) {
        return element.hasAttribute(attribute) ? Float.parseFloat(element.getAttribute(attribute)) : 0;
    }
}
