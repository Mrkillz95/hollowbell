package net.jj.giants.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.controls.ControlKey;
import net.jj.giants.controls.ControlsBridge;
import net.jj.giants.controls.ControlsCatalog;
import net.jj.giants.controls.ControlsJson;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;

/**
 * The Controls tab (CONTROLS_BRIDGE.md): whose each key is and how they're grouped, which keys clash (and which are
 * meant to share), reading giants_controls.json, and gestures' on/off through a giant's bridge.
 */
public class ControlsGameTests implements FabricGameTest {
    private static final Giant FAKE = new Giant("fakeperf", "fakeperf", "fakeperf", "net.jj.fakeperf.GiantsBridge",
            List.of(), Set.of());
    private static final Giant OLD = new Giant("fakeold", "fakeold", "fakeold", "net.jj.fakeold.GiantsBridge",
            List.of(), Set.of());

    private static ControlKey k(String name, String cat, String key) {
        return new ControlKey(name, cat, key, key);
    }

    private static ControlKey k(String name, String cat, String key, String def) {
        return new ControlKey(name, cat, key, def);
    }

    /** the keys the eight giants and the guide register today (their src/client), plus some of Minecraft's */
    private static List<ControlKey> today() {
        List<ControlKey> l = new ArrayList<>();
        l.add(k("key.jump", "key.categories.movement", "key.keyboard.space"));
        l.add(k("key.drop", "key.categories.inventory", "key.keyboard.q"));
        l.add(k("key.jj_giants.performance", "key.categories.jj_giants", ControlKey.NONE));
        l.add(k("key.mountain_breathes.armour_power", "key.categories.mountain_breathes", "key.keyboard.r"));
        l.add(k("key.mountain_breathes.attack_look", "key.categories.mountain_breathes", "key.keyboard.v"));
        l.add(k("key.furrowmaw.armour_power", "key.categories.furrowmaw", "key.keyboard.r"));
        l.add(k("key.fire_ice_cerberus.attack_look", "key.categories.fire_ice_cerberus", "key.keyboard.v"));
        l.add(k("key.fire_ice_cerberus.armour_power", "key.categories.fire_ice_cerberus", "key.keyboard.r"));
        l.add(k("key.hollowbell.armour_power", "key.categories.hollowbell", "key.keyboard.r"));
        l.add(k("key.hollowbell.attack_look", "key.categories.hollowbell", "key.keyboard.v"));
        l.add(k("key.lanternwillow.armour_power", "key.categories.lanternwillow", "key.keyboard.r"));
        l.add(k("key.lanternwillow.attack_look", "key.categories.lanternwillow", "key.keyboard.v"));
        l.add(k("key.wreckback.armour_power", "key.categories.wreckback", "key.keyboard.r"));
        l.add(k("key.wreckback.attack_look", "key.categories.wreckback", "key.keyboard.v"));
        l.add(k("key.swarmforge.build.walker", "key.categories.swarmforge_build", ControlKey.NONE));
        l.add(k("key.swarmforge.armour_power", "key.categories.swarmforge", "key.keyboard.r"));
        l.add(k("key.swarmforge.build.flyer", "key.categories.swarmforge_build", ControlKey.NONE));
        l.add(k("key.swarmforge.highlight", "key.categories.swarmforge", "key.keyboard.semicolon"));
        l.add(k("key.swarmforge.go_after_look", "key.categories.swarmforge", "key.keyboard.v"));
        l.add(k("key.gloamwick.armour_power", "key.categories.gloamwick", "key.keyboard.r"));
        l.add(k("key.gloamwick.attack_look", "key.categories.gloamwick", "key.keyboard.v"));
        l.add(k("key.othermod.thing", "key.categories.othermod", "key.keyboard.k"));
        return l;
    }

    private static Function<ControlKey, String> owners(Map<String, String> extra) {
        return o -> ControlsCatalog.owner(o, Giants.ALL, extra);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void keysGroupedByGiant(GameTestHelper h) {
        Map<String, List<ControlKey>> g = ControlsCatalog.group(today(), Giants.ALL, Map.of());
        // the guide first, then the giants in the guide's order; Minecraft's and other mods' keys aren't listed
        List<String> want = new ArrayList<>();
        want.add(ControlsCatalog.GUIDE);
        for (Giant x : Giants.ALL) want.add(x.key());
        h.assertTrue(new ArrayList<>(g.keySet()).equals(want), "groups in order: " + g.keySet());
        h.assertTrue(g.get("pitchgut").size() == 2, "Pitchgut's two keys (his mod is mountain_breathes)");
        h.assertTrue(g.get("furrowmaw").size() == 1, "Furrowmaw's one key");
        h.assertTrue(g.get("cerberus").size() == 2, "the Cerberus's two keys (fire_ice_cerberus)");
        h.assertTrue(g.get(ControlsCatalog.GUIDE).size() == 1, "the guide's own key");
        // Swarmforge's build keys (their own category) join his group, and his categories stay together
        List<ControlKey> sw = g.get("swarmforge");
        h.assertTrue(sw.size() == 5, "Swarmforge's five keys here");
        h.assertTrue(sw.get(0).name().equals("key.swarmforge.build.walker")
                        && sw.get(1).name().equals("key.swarmforge.build.flyer")
                        && sw.get(2).name().equals("key.swarmforge.armour_power"),
                "categories kept together, in Minecraft's order: " + sw);
        int n = 0;
        for (List<ControlKey> l : g.values()) n += l.size();
        h.assertTrue(n == today().size() - 3, "every giant and guide key, and nothing else");
        // a key under some other category still counts when its name is his
        h.assertTrue("hollowbell".equals(ControlsCatalog.owner(
                k("key.hollowbell.sit_on_giant", "key.categories.gameplay", ControlKey.NONE), Giants.ALL, Map.of())),
                "his by its name");
        h.assertTrue(ControlsCatalog.owner(k("key.other", "key.categories.hollowbellish", "x"), Giants.ALL, Map.of()) == null,
                "a category that only starts with his id isn't his");
        // a category his giants_controls.json names
        Map<String, String> extra = Map.of("key.categories.wreck_extras", "wreckback");
        h.assertTrue("wreckback".equals(ControlsCatalog.owner(k("key.x.y", "key.categories.wreck_extras", "x"), Giants.ALL, extra)),
                "a category from his file");
        // only installed giants get groups
        Map<String, List<ControlKey>> one = ControlsCatalog.group(today(), List.of(Giants.byKey("gloamwick")), Map.of());
        h.assertTrue(one.keySet().equals(Set.of(ControlsCatalog.GUIDE, "gloamwick")), "just the guide and Gloamwick: " + one.keySet());
        h.assertTrue(ControlsCatalog.group(List.of(), Giants.ALL, Map.of()).isEmpty(), "no keys, no groups");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void clashesFound(GameTestHelper h) {
        List<ControlKey> all = today();
        Function<ControlKey, String> who = owners(Map.of());
        ControlKey hb = all.stream().filter(x -> x.name().equals("key.hollowbell.armour_power")).findFirst().orElseThrow();
        // as they come: every armour power on R and every "go after" on V share on purpose, and nothing clashes
        for (ControlKey x : all) {
            if (who.apply(x) == null) continue;
            ControlsCatalog.Clash c = ControlsCatalog.clash(x, all, who);
            h.assertFalse(c.any(), x.name() + " doesn't clash as it comes: " + c.clashes());
        }
        h.assertTrue(ControlsCatalog.clash(hb, all, who).shared().size() == 7, "Hollowbell's R is shared with the other 7");
        h.assertTrue(ControlsCatalog.role("key.swarmforge.go_after_look").equals("attack_look"), "Swarmforge's look key's job");
        ControlKey sv = all.stream().filter(x -> x.name().equals("key.swarmforge.go_after_look")).findFirst().orElseThrow();
        h.assertTrue(ControlsCatalog.clash(sv, all, who).shared().size() == 6, "his V is shared with the six other look keys");

        // moved onto Jump: a clash both ways
        List<ControlKey> moved = new ArrayList<>(all);
        ControlKey onSpace = k(hb.name(), hb.category(), "key.keyboard.space", "key.keyboard.r");
        moved.set(moved.indexOf(hb), onSpace);
        ControlsCatalog.Clash c = ControlsCatalog.clash(onSpace, moved, who);
        h.assertTrue(c.any() && c.clashes().size() == 1 && c.clashes().get(0).name().equals("key.jump"), "clashes with Jump");
        h.assertTrue(ControlsCatalog.clash(moved.get(0), moved, who).any(), "and Jump clashes with it");

        // a different job on another giant's key: a clash (his armour power on Gloamwick's V)
        List<ControlKey> v = new ArrayList<>(all);
        ControlKey onV = k(hb.name(), hb.category(), "key.keyboard.v", "key.keyboard.r");
        v.set(v.indexOf(hb), onV);
        ControlsCatalog.Clash cv = ControlsCatalog.clash(onV, v, who);
        h.assertTrue(cv.clashes().size() == 7, "on V it clashes with all seven look keys: " + cv.clashes().size());
        // his own two keys on one: a clash too
        h.assertTrue(cv.clashes().stream().anyMatch(x -> x.name().equals("key.hollowbell.attack_look")), "with his own look key");
        // the guide's key and another mod's are never "shared"
        ControlKey perf = k("key.jj_giants.performance", "key.categories.jj_giants", "key.keyboard.k", ControlKey.NONE);
        List<ControlKey> p = new ArrayList<>(all);
        p.set(2, perf);
        h.assertTrue(ControlsCatalog.clash(perf, p, who).clashes().size() == 1, "the guide's key on another mod's K clashes");
        // no key never clashes
        ControlKey none = k("key.jj_giants.performance", "key.categories.jj_giants", ControlKey.NONE);
        h.assertTrue(none.unbound(), "unknown is no key");
        h.assertFalse(ControlsCatalog.clash(none, all, who).any(), "no key, no clash");
        h.assertTrue(ControlsCatalog.clash(none, all, who).shared().isEmpty(), "no key, nothing shared");
        // the coming "sit on giant" keys: the same job on every giant, so they share too
        List<ControlKey> sit = new ArrayList<>(all);
        ControlKey s1 = k("key.hollowbell.sit_on_giant", "key.categories.hollowbell", "key.keyboard.x", ControlKey.NONE);
        ControlKey s2 = k("key.gloamwick.sit_on_giant", "key.categories.gloamwick", "key.keyboard.x", ControlKey.NONE);
        sit.add(s1);
        sit.add(s2);
        h.assertFalse(ControlsCatalog.clash(s1, sit, who).any(), "two giants' sit keys share");
        h.assertTrue(ControlsCatalog.clash(s1, sit, who).shared().size() == 1, "one other sit key");
        h.assertFalse(onSpace.isDefault(), "a moved key isn't its default");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void gestureJsonRead(GameTestHelper h) {
        String good = "{\"controls\":1,\"categories\":[\"key.categories.hollowbell_extra\",\"bad cat!\"],"
                + "\"keys\":{\"key.hollowbell.armour_power\":\"Great Toll.\",\"bad key!\":\"x\",\"key.hollowbell.empty\":\"\"},"
                + "\"gestures\":["
                + "{\"id\":\"triple_crouch_sit\",\"name\":\"Triple crouch to sit\",\"description\":\"Crouch three times.\","
                + "\"config\":\"tripleCrouchSit\",\"key\":\"key.hollowbell.sit_on_giant\",\"default\":true},"
                + "{\"id\":\"off_one\",\"name\":\"Off one\",\"what\":\"Older word for description.\",\"config\":\"offOne\",\"default\":false},"
                + "{\"id\":\"triple_crouch_sit\",\"name\":\"Twice\",\"config\":\"x\"},"
                + "{\"id\":\"Bad Id\",\"name\":\"x\",\"config\":\"x\"},"
                + "{\"id\":\"no_config\",\"name\":\"x\"},"
                + "{\"id\":\"no_name\",\"config\":\"x\"},"
                + "\"not an object\","
                + "{\"id\":\"long\",\"name\":\"" + "n".repeat(100) + "\",\"config\":\"long\",\"default\":\"yes\"}"
                + "]}";
        ControlsJson.Controls c = ControlsJson.read("hollowbell", good);
        h.assertTrue(c != null, "a good file reads");
        h.assertTrue(c.owner().equals("hollowbell"), "its owner");
        h.assertTrue(c.categories().equals(List.of("key.categories.hollowbell_extra")), "bad category left out: " + c.categories());
        h.assertTrue(c.keys().size() == 1 && c.keys().get("key.hollowbell.armour_power").equals("Great Toll."), "keys: " + c.keys());
        h.assertTrue(c.gestures().size() == 3, "three gestures kept: " + c.gestures());
        ControlsJson.Gesture g = c.gestures().get(0);
        h.assertTrue(g.id().equals("triple_crouch_sit") && g.name().equals("Triple crouch to sit")
                        && g.what().equals("Crouch three times.") && g.config().equals("tripleCrouchSit")
                        && g.key().equals("key.hollowbell.sit_on_giant") && g.def(),
                "the first gesture, all of it: " + g);
        h.assertTrue(c.gestureFor("key.hollowbell.sit_on_giant") == g, "found by its key");
        h.assertTrue(c.gestureFor("key.hollowbell.armour_power") == null, "no gesture by the armour key");
        ControlsJson.Gesture off = c.gestures().get(1);
        h.assertTrue(!off.def() && off.key().isEmpty() && off.what().equals("Older word for description."), "off, no key, 'what': " + off);
        ControlsJson.Gesture lng = c.gestures().get(2);
        h.assertTrue(lng.name().length() == ControlsJson.LONGEST_NAME && lng.def(), "a long name cut, a non-boolean default is on");
        // what isn't a file this guide reads
        h.assertTrue(ControlsJson.read("x", null) == null, "no text");
        h.assertTrue(ControlsJson.read("x", "not json {") == null, "not JSON");
        h.assertTrue(ControlsJson.read("x", "[1,2]") == null, "not an object");
        h.assertTrue(ControlsJson.read("x", "{\"controls\":2,\"gestures\":[]}") == null, "a newer version");
        h.assertTrue(ControlsJson.read("x", "{\"controls\":\"one\"}") == null, "a version that isn't a number");
        ControlsJson.Controls bare = ControlsJson.read("x", "{}");
        h.assertTrue(bare != null && bare.gestures().isEmpty() && bare.keys().isEmpty(), "an empty file is fine");
        ControlsJson.Controls many = ControlsJson.read("x", "{\"gestures\":["
                + String.join(",", java.util.Collections.nCopies(40, "{}")) + "]}");
        h.assertTrue(many != null && many.gestures().isEmpty(), "40 empty gestures: none");
        StringBuilder lots = new StringBuilder("{\"gestures\":[");
        for (int i = 0; i < 40; i++)
            lots.append(i > 0 ? "," : "").append("{\"id\":\"g").append(i).append("\",\"name\":\"G\",\"config\":\"c").append(i).append("\"}");
        ControlsJson.Controls capped = ControlsJson.read("x", lots.append("]}").toString());
        h.assertTrue(capped.gestures().size() == ControlsJson.MOST_GESTURES, "at most " + ControlsJson.MOST_GESTURES);
        // categories from files, for the catalog
        Map<String, String> extra = ControlsJson.extraCategories(Map.of("hollowbell", c));
        h.assertTrue("hollowbell".equals(extra.get("key.categories.hollowbell_extra")), "its category, his");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void gestureFilesInJars(GameTestHelper h) {
        // the test mod ships one the way a giant would, and the guide ships its own
        ControlsJson.Controls t = ControlsJson.forMod("fakeperf", "jj_giants-gametest");
        h.assertTrue(t != null, "the test mod's giants_controls.json is read");
        h.assertTrue(t.gestures().size() == 1 && t.gestures().get(0).config().equals("tripleCrouchSit"), "its gesture");
        h.assertTrue(t.categories().equals(List.of("key.categories.fakeperf_extra")), "its category");
        ControlsJson.Controls mine = ControlsJson.forMod(ControlsCatalog.GUIDE, "jj_giants");
        h.assertTrue(mine != null && mine.keys().containsKey("key.jj_giants.performance"), "the guide's own");
        String what = mine.keys().get("key.jj_giants.performance");
        h.assertTrue(Language.getInstance().has(what), "its words are a key in the guide's language file");
        h.assertTrue(ControlsJson.forMod("x", "no_such_mod") == null, "no mod, no file");
        h.assertTrue(ControlsJson.forMod("x", "minecraft") == null, "a mod without one");
        h.assertTrue(ControlsJson.all(Giants.installed()).containsKey(ControlsCatalog.GUIDE), "all() has the guide's");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void gestureThroughBridge(GameTestHelper h) {
        ControlsJson.Gesture g = ControlsJson.forMod("fakeperf", "jj_giants-gametest").gestures().get(0);
        net.jj.fakeperf.GiantsBridge.tripleCrouchSit = true;
        h.assertTrue(Boolean.TRUE.equals(ControlsBridge.get(FAKE, g)), "on to start");
        h.assertTrue(ControlsBridge.set(FAKE, g, false), "turned off");
        h.assertTrue(!net.jj.fakeperf.GiantsBridge.tripleCrouchSit, "his mod has it off");
        h.assertTrue(Boolean.FALSE.equals(ControlsBridge.get(FAKE, g)), "and says so");
        h.assertTrue(ControlsBridge.set(FAKE, g, true) && net.jj.fakeperf.GiantsBridge.tripleCrouchSit, "back on");
        JsonObject arg = JsonParser.parseString(ControlsBridge.setArg(g, false)).getAsJsonObject();
        h.assertTrue(arg.get("controls").getAsInt() == 1 && arg.get("config").getAsString().equals("tripleCrouchSit")
                && !arg.get("value").getAsBoolean(), "the set argument: " + arg);
        // an older mod (or one without that setting) says nothing: shown, but not changed from here
        h.assertTrue(ControlsBridge.get(OLD, g) == null, "an old bridge: unknown");
        h.assertFalse(ControlsBridge.set(OLD, g, false), "an old bridge doesn't take it");
        ControlsJson.Gesture other = new ControlsJson.Gesture("x", "X", "", "notHis", "", true);
        h.assertTrue(ControlsBridge.get(FAKE, other) == null, "a setting he doesn't have: unknown");
        h.succeed();
    }

    /**
     * The real giants (with -Pgiants): every Controls category and key name in each giant's own language file is
     * his by the guide's rule, so each of his keys lands in his group. And any giants_controls.json one ships reads.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void realGiantsKeysAreTheirs(GameTestHelper h) {
        int checked = 0;
        for (Giant g : Giants.installed()) {
            Optional<ModContainer> c = FabricLoader.getInstance().getModContainer(g.modId());
            h.assertTrue(c.isPresent(), g.key() + "'s mod");
            Optional<Path> lang = c.get().findPath("assets/" + g.modId() + "/lang/en_us.json");
            h.assertTrue(lang.isPresent(), g.key() + "'s language file");
            JsonObject o;
            try {
                o = JsonParser.parseString(Files.readString(lang.get(), StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                throw new AssertionError(g.key() + "'s language file reads", e);
            }
            int cats = 0;
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                String key = e.getKey();
                if (key.startsWith("key.categories.")) {
                    cats++;
                    h.assertTrue(g.key().equals(ControlsCatalog.owner(k("key.x", key, "x"), Giants.ALL, Map.of())),
                            key + " is " + g.key() + "'s");
                    checked++;
                } else if (key.startsWith("key.")) {
                    h.assertTrue(g.key().equals(ControlsCatalog.owner(k(key, "key.categories.misc", "x"), Giants.ALL, Map.of())),
                            key + " is " + g.key() + "'s by its name");
                    checked++;
                }
            }
            h.assertTrue(cats >= 1, g.key() + " has a Controls category");
            if (c.get().findPath("assets/" + g.modId() + "/" + ControlsJson.FILE).isPresent())
                h.assertTrue(ControlsJson.forMod(g.key(), g.modId()) != null, g.key() + "'s giants_controls.json reads");
        }
        if (!Giants.installed().isEmpty()) h.assertTrue(checked > 0, "some keys checked");
        h.succeed();
    }
}
