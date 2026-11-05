package net.jj.giants.admin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import net.jj.giants.Giant;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Bundles of setting values (ADMIN_BRIDGE.md, "Bundles"): a preset, or a world's settings exported as text and
 * imported into another. Applying one first shows what it would change (a plan); the change only happens on
 * /giants admin confirm.
 */
public final class AdminTransfer {
    private AdminTransfer() {}

    /** the start of an exported string: the format's version */
    public static final String PREFIX = "GA1-";

    /**
     * A bundle: a name, whether it replaces everything (settings it doesn't name go back to the server default) or
     * only changes what it names, and the values: "all" and each giant's key to id to value.
     */
    public record Bundle(String id, String name, String about, boolean replace,
            Map<String, Map<String, JsonElement>> values, List<String> keep) {
        public Bundle(String id, String name, String about, boolean replace,
                Map<String, Map<String, JsonElement>> values) {
            this(id, name, about, replace, values, List.of());
        }
    }

    /**
     * What applying a bundle would do.
     *
     * @param changes     each setting that would change: giant key ("all" too), id, old value (null: the default),
     *                    new value (null: back to the default)
     * @param lines       the preview, in plain words
     * @param creationOnly the changed settings that only take full effect in a new world or on new land
     */
    public record Plan(Bundle bundle, List<Step> changes, List<String> lines, List<String> creationOnly,
            List<String> skipped, List<String> locked) {}

    public record Step(String giant, String id, String name, @Nullable JsonElement old, @Nullable JsonElement now,
            String oldText, String nowText) {}

    // the built-in presets (Part 3 adds Close Quarters and Far Frontier; Part 4 the rest: see ADMIN_BRIDGE.md)
    private static final Map<String, String> BUILT_IN = new LinkedHashMap<>();

    /** the giants a built-in preset names (every one the Guide knows, installed or not) */
    private static final String[] EVERY_GIANT = {"pitchgut", "furrowmaw", "cerberus", "hollowbell", "lanternwillow",
        "wreckback", "swarmforge", "gloamwick"};

    /** a preset that sets the same values for every giant ("replace": false: it changes only what it names) */
    private static String forEveryGiant(String id, String name, String about, String values) {
        StringBuilder g = new StringBuilder();
        for (String k : EVERY_GIANT) g.append(g.length() == 0 ? "" : ",").append('"').append(k).append("\":")
                .append(values);
        return "{\"admin\":1,\"preset\":\"" + id + "\",\"name\":\"" + name + "\",\"about\":\"" + about
                + "\",\"replace\":false,\"giants\":{" + g + "}}";
    }

    /** (Part 4) the Suggested group (ADMIN_BOOK.md, the preset picker's layout); My Giant comes in Part 5 */
    public static final List<String> SUGGESTED = List.of("my_giant", "pocket_giants", "titans", "gentle_giants", "kaiju_war",
            "night_terrors", "wild_sizes");

    /** a Part 4 re-tune preset: a replace that keeps where the grounds go (ADMIN_BRIDGE.md 19.8) */
    private static String retune(String id, String name, String about, String all, String every,
            Map<String, String> own) {
        StringBuilder g = new StringBuilder();
        for (String k : EVERY_GIANT) {
            StringBuilder v = new StringBuilder(every);
            String extra = own.get(k);
            if (extra != null && !extra.isEmpty()) v.append(v.length() == 0 ? "" : ",").append(extra);
            g.append(g.length() == 0 ? "" : ",").append('"').append(k).append("\":{").append(v).append('}');
        }
        return "{\"admin\":1,\"preset\":\"" + id + "\",\"name\":\"" + name + "\",\"about\":\"" + about
                + "\",\"replace\":true,\"keep\":[\"worldgen\",\"players\",\"server\"],\"all\":{" + myGiant(id, all)
                + "},\"giants\":{" + g + "}}";
    }

    /**
     * (Part 5, ADMIN_BRIDGE.md 20.1) presets keep the player settings, and name My Giant: on for My Giant, off for
     * every other one
     */
    private static String myGiant(String id, String all) {
        String mine = "\"all.myGiant\":" + id.equals("my_giant");
        return all.isEmpty() ? mine : all + "," + mine;
    }

    /** each giant's size for about two blocks tall (his height at size 1: ADMIN_BRIDGE.md 19.2) */
    private static final Map<String, String> POCKET_SIZE = Map.of(
            "pitchgut", "\"worldScale\":0.012", "furrowmaw", "\"worldScale\":0.03",
            "cerberus", "\"worldScale\":0.011", "hollowbell", "\"worldScale\":0.011",
            "lanternwillow", "\"worldScale\":0.011", "wreckback", "\"worldScale\":0.011",
            "swarmforge", "\"worldScale\":0.015", "gloamwick", "\"worldScale\":0.011");

    /** Lite: ambient sounds off where the server decides them (the Hollowbell's are each player's own) */
    private static final Map<String, String> LITE_OWN = Map.of(
            "pitchgut", "\"ambientSounds\":false", "furrowmaw", "\"ambientSounds\":false",
            "cerberus", "\"ambientSounds\":false", "lanternwillow", "\"ambientSounds\":false",
            "wreckback", "\"ambientSounds\":false", "gloamwick", "\"ambientSounds\":false",
            "swarmforge", "\"ambientSounds\":false,\"robotLimit\":120,\"armyPoints\":150");

    private static final String TITAN_ALL = "\"all.health\":2.5,\"all.damage\":3,\"all.speed\":1.25,"
            + "\"all.size\":2,\"all.cooldowns\":0.5";
    private static final String TITAN_EACH = "\"temper\":\"hunter\",\"fleeInWildFights\":false,\"yieldAt\":0,"
            + "\"griefing\":true";

    static {
        BUILT_IN.put("classic", "{\"admin\":1,\"preset\":\"classic\",\"name\":\"Classic\",\"about\":\"Every giant as"
                + " the server's config files have him: every giant setting this world changed goes back (the"
                + " player settings stay).\","
                + "\"replace\":true,\"keep\":[\"players\",\"server\"],\"all\":{\"all.myGiant\":false}}");
        // Part 5: My Giant (ADMIN_BRIDGE.md 20.7)
        BUILT_IN.put("my_giant", retune("my_giant", "My Giant",
                "Each player picks a giant the first time they join: they are taken to his ground with their own book"
                        + " of him and his finder, and one of him comes to them as their own.",
                "", "", Map.of()));
        // Part 3: where the giants' grounds go (ADMIN_BRIDGE.md 18.9)
        BUILT_IN.put("close_quarters", forEveryGiant("close_quarters", "Close Quarters",
                "Every giant's ground begins 500 to 1,500 blocks from spawn, packed close together (smaller grounds,"
                        + " 50 blocks apart). Best for a new world: land already made stays as it is.",
                "{\"groundNearest\":500,\"groundFarthest\":1500,\"groundGap\":50,\"homeRadius\":500}"));
        BUILT_IN.put("far_frontier", forEveryGiant("far_frontier", "Far Frontier",
                "Every giant's ground is far out: it begins 5,000 to 20,000 blocks from spawn. Best for a new world:"
                        + " land already made stays as it is.",
                "{\"groundNearest\":5000,\"groundFarthest\":20000}"));
        // Part 4: the re-tune presets (ADMIN_BRIDGE.md 19.9). Suggested first, in the picker's order.
        BUILT_IN.put("pocket_giants", retune("pocket_giants", "Pocket Giants",
                "Player-sized giants, about two blocks tall, with health and hits to match. Four of each kind roam"
                        + " together, and they come back after a day.",
                "\"all.damage\":0.6",
                "\"spawnSize\":[0.85,1.25],\"worldCount\":4,\"maxInWorld\":6,\"worldRespawnDays\":1",
                POCKET_SIZE));
        BUILT_IN.put("titans", retune("titans", "Titans",
                "Twice the size, three times the damage, two and a half times the health and faster. Always hunting,"
                        + " never backing down, short waits between moves, breaking blocks, slow to heal.",
                TITAN_ALL + ",\"all.healing\":0.5", TITAN_EACH, Map.of()));
        BUILT_IN.put("gentle_giants", retune("gentle_giants", "Gentle Giants",
                "Giants never attack unless hit first, hold no grudges, forgive many hits and their orders cost no"
                        + " wind: for peaceful exploring and riding.",
                "", "\"temper\":\"peaceful\",\"grudgeRate\":0,\"freeHits\":20,\"bookCosts\":false", Map.of()));
        BUILT_IN.put("kaiju_war", retune("kaiju_war", "Kaiju War",
                "Giants seek each other out across the world and fight whenever they meet, breaking blocks as they"
                        + " go. Players are the survivors in between.",
                "", "\"seekGiants\":true,\"fightGiants\":true,\"meetings\":true,\"meetCooldown\":1,"
                        + "\"griefing\":true", Map.of()));
        BUILT_IN.put("night_terrors", retune("night_terrors", "Night Terrors",
                "Giants sleep all day and hunt all night, twice as strong after dark. The Gloamwick's Eclipse comes"
                        + " much more often.",
                "", "\"sleepSchedule\":\"day\",\"nightStrength\":2", Map.of("gloamwick", "\"eclipseOften\":true")));
        BUILT_IN.put("wild_sizes", retune("wild_sizes", "Wild Sizes",
                "Every giant that comes on his own is a random size, from tiny to twice as big, with health and hits"
                        + " to match, and may turn up in another giant's ground.",
                "\"all.spawnMix\":true", "\"spawnSize\":[0.1,2]", Map.of()));
        // All presets
        BUILT_IN.put("siege", retune("siege", "Siege",
                "Giants head for villages and players and attack, breaking blocks, and come back a day after they"
                        + " fall. Survive the assault.",
                "", "\"raidVillages\":true,\"griefing\":true,\"worldRespawnDays\":1", Map.of()));
        BUILT_IN.put("one_life", retune("one_life", "One Life",
                "Each giant comes once per world: he never comes back after he falls and never heals on his own. A"
                        + " kill is for good.",
                "", "\"respawn\":false,\"passiveHeal\":false,\"worldCount\":1", Map.of()));
        BUILT_IN.put("boss_rush", retune("boss_rush", "Boss Rush",
                "Short, hard fights: less health, harder hits, shorter waits. Giants come back a minute after they fall"
                        + " and drop three times the loot.",
                "\"all.health\":0.6,\"all.damage\":1.25,\"all.cooldowns\":0.75",
                "\"worldRespawnDays\":0,\"ownedRespawnDays\":0,\"lootMultiplier\":3", Map.of()));
        BUILT_IN.put("nomads", retune("nomads", "Nomads",
                "Giants never stay home: they wander the whole world, so one may turn up anywhere, even coming in"
                        + " another giant's ground.",
                "\"all.spawnMix\":true", "\"roam\":\"wander\"", Map.of()));
        BUILT_IN.put("hardcore", retune("hardcore", "Hardcore",
                "Titans' strength, no healing on their own, no free hits for book holders, and orders cost twice the"
                        + " wind.",
                TITAN_ALL + ",\"all.windCost\":2", TITAN_EACH + ",\"passiveHeal\":false,\"freeHits\":0", Map.of()));
        BUILT_IN.put("builder_showcase", retune("builder_showcase", "Builder / Showcase",
                "Calm giants that stay where they are: they never start a fight, their hits do next to nothing, they"
                        + " break no blocks and leave each other alone. For screenshots, maps and videos.",
                "\"all.damage\":0.1",
                "\"temper\":\"peaceful\",\"roam\":\"stay\",\"griefing\":false,\"fightGiants\":false,"
                        + "\"meetings\":false",
                Map.of()));
        BUILT_IN.put("public_server_safe", retune("public_server_safe", "Public Server Safe",
                "No block breaking, no land kept loaded, one of each at most, slightly softer hits and smaller"
                        + " Swarmforge armies.",
                "\"all.damage\":0.8", "\"griefing\":false,\"chunkLoading\":false,\"maxInWorld\":1",
                Map.of("swarmforge", "\"robotLimit\":120,\"armyPoints\":200")));
        BUILT_IN.put("lite", retune("lite", "Lite",
                "Fewer, smaller giants for weak computers and busy servers: half size, one of each, no ambient sounds"
                        + " and smaller Swarmforge armies.",
                "", "\"worldScale\":0.5,\"maxInWorld\":1", LITE_OWN));
        BUILT_IN.put("speedrun", retune("speedrun", "Speedrun",
                "Weaker giants for quick fights: half the health, softer hits, and orders cost half the wind. New"
                        + " players start with every giant's finder.",
                "\"all.health\":0.5,\"all.damage\":0.75,\"all.windCost\":0.5,\"all.kitFinders\":\"all\"", "",
                Map.of()));
        // Part 8: the new systems (ADMIN_BRIDGE.md 24.7)
        BUILT_IN.put("herds", retune("herds", "Herds",
                "Several smaller giants of each kind roam together in packs: four of each, smaller than usual, keeping"
                        + " close to their leader.",
                "\"all.herd\":true", "\"spawnSize\":[0.35,0.6],\"worldCount\":4,\"maxInWorld\":6", Map.of()));
        BUILT_IN.put("evolution", retune("evolution", "Evolution",
                "Giants start small and grow with each day they survive and each fight they win; one left alone for a"
                        + " week becomes a Titan.",
                "\"all.size\":0.4,\"all.growth\":true,\"all.growthPerDay\":0.1,\"all.growthPerWin\":0.25,"
                        + "\"all.growthMost\":5,\"all.growthTitan\":7", "", Map.of()));
        // Part 9: new systems B (ADMIN_BRIDGE.md 25.10)
        BUILT_IN.put("random_chaos", retune("random_chaos", "Random Chaos",
                "Every game day the giants are shuffled: new sizes, new moods, and each uses only some of his moves"
                        + " that day.",
                "\"all.chaos\":true", "", Map.of()));
        BUILT_IN.put("underworld", retune("underworld", "Underworld",
                "The giants live in the Nether and the End instead of the Overworld: one of each in both, coming near"
                        + " players there, sized to fit under the Nether's roof.",
                "\"all.underworld\":\"both\"", "", Map.of()));
        BUILT_IN.put("weather_gods", retune("weather_gods", "Weather Gods",
                "Each giant brings his own weather to the land round him: ash, dust, sleet and embers, storms, mist,"
                        + " smog or the gloaming. The bigger the giant, the further it reaches.",
                "\"all.weather\":true", "", Map.of()));
        BUILT_IN.put("sacred_ground", retune("sacred_ground", "Sacred Ground",
                "Nobody may build or break blocks in a giant's ground while he lives. Bring him down first.",
                "\"all.sacred\":true", "", Map.of()));
        BUILT_IN.put("mirror", retune("mirror", "Mirror",
                "Giants copy the players: the better the armour worn on the server, the stronger they are, and each"
                        + " kind learns to shrug off the tactic players lean on.",
                "\"all.mirror\":true,\"all.scaleArmour\":1", "", Map.of()));
        // (Part 3's two last, after every re-tune one)
        BUILT_IN.put("close_quarters", BUILT_IN.remove("close_quarters"));
        BUILT_IN.put("far_frontier", BUILT_IN.remove("far_frontier"));
    }

    public static Set<String> presets() {
        return BUILT_IN.keySet();
    }

    /** (Part 4) where a built-in preset is listed: "default" (Classic), "suggested" or "all" */
    public static String group(String id) {
        return id.equals("classic") ? "default" : SUGGESTED.contains(id) ? "suggested" : "all";
    }

    /** a built-in preset by its id or its name ("Far Frontier"); null if there's none */
    public static @Nullable String builtInId(String idOrName) {
        String t = idOrName.trim().toLowerCase(java.util.Locale.ROOT);
        if (BUILT_IN.containsKey(t)) return t;
        String asId = t.replace(' ', '_');
        return BUILT_IN.containsKey(asId) ? asId : null;
    }

    /** a built-in preset by its id, or a preset saved on this server by its name; null if there's none */
    public static @Nullable Bundle preset(String id) {
        String b = builtInId(id);
        String json = b == null ? null : BUILT_IN.get(b);

        if (json != null) return read(json);
        AdminPresets.Saved s = AdminPresets.find(id);
        return s == null ? null : s.bundle();
    }

    /** a bundle from its JSON; null if it isn't one */
    public static @Nullable Bundle read(String json) {
        JsonObject o = AdminJson.object(json);
        if (o == null || !o.has("admin")) return null;
        try {
            if (o.get("admin").getAsInt() > AdminJson.VERSION) return null;
        } catch (RuntimeException e) {
            return null;
        }
        Map<String, Map<String, JsonElement>> values = new LinkedHashMap<>();
        if (o.get("all") instanceof JsonObject a) values.put(AdminCatalog.ALL, map(a));
        if (o.get("giants") instanceof JsonObject g)
            for (var e : g.entrySet())
                if (e.getValue() instanceof JsonObject v && AdminJson.ID.matcher(e.getKey()).matches())
                    values.put(e.getKey(), map(v));
        boolean replace = !(o.get("replace") instanceof com.google.gson.JsonPrimitive p) || !p.isBoolean()
                || p.getAsBoolean();
        String id = AdminJson.str(o, "preset", 32);
        // (Part 4) the categories a replace leaves as they are ("keep":["worldgen"])
        List<String> keep = new ArrayList<>();
        if (o.get("keep") instanceof com.google.gson.JsonArray ka)
            for (JsonElement k : ka)
                if (k.isJsonPrimitive() && AdminSetting.CATEGORIES.contains(k.getAsString())) keep.add(k.getAsString());
        return new Bundle(id.isEmpty() ? "import" : id, AdminJson.str(o, "name", 48), AdminJson.str(o, "about", 400),
                replace, values, List.copyOf(keep));
    }

    private static Map<String, JsonElement> map(JsonObject o) {
        Map<String, JsonElement> m = new LinkedHashMap<>();
        for (var e : o.entrySet()) if (AdminJson.ID.matcher(e.getKey()).matches()) m.put(e.getKey(), e.getValue());
        return m;
    }

    /** a bundle's JSON */
    public static String write(Bundle b) {
        JsonObject o = new JsonObject();
        o.addProperty("admin", AdminJson.VERSION);
        if (!b.id().isEmpty() && !b.id().equals("export") && !b.id().equals("import"))
            o.addProperty("preset", b.id());
        if (!b.name().isEmpty()) o.addProperty("name", b.name());
        if (!b.about().isEmpty()) o.addProperty("about", b.about());
        o.addProperty("replace", b.replace());
        if (!b.keep().isEmpty()) {
            com.google.gson.JsonArray k = new com.google.gson.JsonArray();
            for (String c : b.keep()) k.add(c);
            o.add("keep", k);
        }
        JsonObject giants = new JsonObject();
        for (var e : b.values().entrySet()) {
            JsonObject v = new JsonObject();
            for (var s : e.getValue().entrySet()) v.add(s.getKey(), s.getValue());
            if (e.getKey().equals(AdminCatalog.ALL)) o.add("all", v);
            else giants.add(e.getKey(), v);
        }
        o.add("giants", giants);
        return o.toString();
    }

    /** this world's settings as one bundle (every giant's, installed or not, and the all-giants ones) */
    public static Bundle current(MinecraftServer server) {
        AdminWorld w = AdminWorld.get(server);
        Map<String, Map<String, JsonElement>> v = new LinkedHashMap<>();
        for (String g : w.giantsWithValues()) v.put(g, w.values(g));
        return new Bundle("export", "", "", true, v);
    }

    /** a bundle as one line of text to copy: GA1- and the JSON, squeezed and in URL-safe Base64 */
    public static String encode(Bundle b) {
        byte[] raw = write(b).getBytes(StandardCharsets.UTF_8);
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        d.setInput(raw);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (!d.finished()) out.write(buf, 0, d.deflate(buf));
        d.end();
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
    }

    /** an exported string (or the bundle's JSON itself) back to a bundle; null if it isn't one */
    public static @Nullable Bundle decode(String text) {
        String t = text == null ? "" : text.trim();
        if (t.startsWith("{")) return read(t);
        // (Part 12) a share code
        if (ShareCodes.looksLike(t)) return ShareCodes.read(t).bundle();
        if (!t.startsWith(PREFIX)) return null;
        try {
            byte[] packed = Base64.getUrlDecoder().decode(t.substring(PREFIX.length()));
            Inflater i = new Inflater();
            i.setInput(packed);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            while (!i.finished()) {
                int n = i.inflate(buf);
                if (n == 0 && (i.needsInput() || i.needsDictionary())) break;
                out.write(buf, 0, n);
                if (out.size() > 1 << 20) break; // (nothing real is this big)
            }
            i.end();
            return read(out.toString(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | DataFormatException e) {
            return null;
        }
    }

    /** what a bundle would change in this world, without changing anything */
    public static Plan plan(MinecraftServer server, Bundle b) {
        return plan(AdminView.of(server), b);
    }

    /** the same for any view: a running world, or the Create World screen's draft (Part 3) */
    public static Plan plan(AdminView view, Bundle b) {
        AdminWorld w = view.world();
        List<Step> steps = new ArrayList<>();
        List<String> lines = new ArrayList<>(), skipped = new ArrayList<>(), creation = new ArrayList<>(),
                locked = new ArrayList<>();
        // the all-giants ones
        Map<String, JsonElement> wantAll = b.values().getOrDefault(AdminCatalog.ALL, Map.of());
        for (AdminSetting s : AdminServer.shared(w))
            consider(w, AdminCatalog.ALL, "All giants", s, wantAll, b.replace() && !b.keep().contains(s.cat()), steps,
                    creation, locked);
        for (String id : wantAll.keySet())
            if (AdminCatalog.shared(id) == null) skipped.add("all giants " + id);
        // each giant here that speaks the protocol
        Set<String> seen = new LinkedHashSet<>();
        for (Giant g : AdminServer.giants()) {
            AdminJson.Report r = view.report(g);
            if (r == null) continue;
            seen.add(g.key());
            Map<String, JsonElement> want = b.values().getOrDefault(g.key(), Map.of());
            for (AdminSetting s : r.settings())
                consider(w, g.key(), r.name(), s, want, b.replace() && !b.keep().contains(s.cat()), steps, creation,
                        locked);
            for (String id : want.keySet()) if (r.setting(id) == null) skipped.add(r.name() + " " + id);
        }
        for (String g : b.values().keySet())
            if (!g.equals(AdminCatalog.ALL) && !seen.contains(g)) skipped.add(g + " (not here)");
        for (Step s : steps)
            lines.add("  " + (s.giant().equals(AdminCatalog.ALL) ? "All giants" : nameFor(s.giant())) + " "
                    + s.name() + ": " + s.oldText() + " → " + s.nowText());
        String title = title(b);
        if (steps.isEmpty()) lines.add(0, title + " changes nothing in this world.");
        else lines.add(0, title + " would change " + steps.size() + (steps.size() == 1 ? " setting:" : " settings:"));
        if (!creation.isEmpty())
            lines.add("World creation: these only take full effect in a new world or on land not made yet: "
                    + String.join(", ", creation) + ".");
        if (!locked.isEmpty()) lines.add("Locked, so left as they are: " + String.join(", ", locked) + ".");
        if (!skipped.isEmpty()) lines.add("Left out (not here or not known): " + String.join(", ", skipped) + ".");
        if (!steps.isEmpty()) lines.add("/giants admin confirm to apply it (within five minutes).");
        return new Plan(b, steps, lines, creation, skipped, locked);
    }

    /** what a bundle is called in a preview */
    public static String title(Bundle b) {
        if (!b.name().isEmpty()) return b.name();
        return switch (b.id()) {
            case "import", "export" -> "The import";
            case "snapshot" -> "The snapshot";
            default -> b.id();
        };
    }

    private static String nameFor(String key) {
        Giant g = AdminServer.giant(key);
        return g == null ? key : AdminServer.nameOf(g);
    }

    private static void consider(AdminWorld w, String giant, String giantName, AdminSetting s,
            Map<String, JsonElement> want, boolean replace, List<Step> steps, List<String> creation,
            List<String> locked) {
        JsonElement old = s.set() ? s.value() : null;
        AdminWorld.Lock lock = w.lock(giant, s.id());
        if (lock != null) {
            // (a locked one is never changed by a bundle; it is named only if the bundle would have changed it)
            boolean wants = want.containsKey(s.id()) ? !AdminSetting.same(want.get(s.id()), s.value())
                    : replace && old != null;
            if (wants) locked.add(giantName + " " + s.name() + " (" + lock.who() + ")");
            return;
        }
        JsonElement now;
        if (want.containsKey(s.id())) {
            JsonElement v = want.get(s.id());
            if (!s.accepts(v)) return; // (one it can't take is left as it is)
            try {
                now = s.parse(AdminSetting.text(v));
            } catch (AdminSetting.BadValue e) {
                return;
            }
        } else if (replace) now = null;
        else return;
        // (Part 5) a player setting a preset names at its default (My Giant off) is the default, not a value of
        // the world's own: nothing to change when the world has none
        if (now != null && s.giant().equals(AdminCatalog.ALL) && !AdminCatalog.multiplier(s)
                && AdminSetting.same(now, s.def())) now = null;
        if (old == null && now == null) return;
        if (old != null && now != null && AdminSetting.same(old, now)) return;
        String oldText = AdminServer.shown(s, old == null ? s.def() : old) + (old == null ? " (default)" : "");
        String nowText = AdminServer.shown(s, now == null ? s.def() : now) + (now == null ? " (default)" : "");
        steps.add(new Step(giant, s.id(), s.name(), old, now, oldText, nowText));
        if (s.creationOnly()) creation.add(giantName + " " + s.name());
    }

    /** does a plan: every change kept for the world, logged as one, and sent to the giants */
    public static int apply(MinecraftServer server, String who, Plan p) {
        AdminWorld w = AdminWorld.get(server);
        // (locked since the preview: still left alone)
        List<Step> steps = p.changes().stream().filter(s -> w.lock(s.giant(), s.id()) == null).toList();
        if (steps.isEmpty()) return 0;
        String id = p.bundle().id();
        boolean imported = id.equals("import") || id.equals("export"), snap = id.equals("snapshot");
        // a snapshot first (not before putting a snapshot back: that one is undone from the change log)
        if (!snap) w.snapshot(who, imported ? "Before imported settings" : "Before the preset " + title(p.bundle()));
        Map<String, Map<String, JsonElement>> before = new LinkedHashMap<>();
        for (Step s : steps) before.computeIfAbsent(s.giant(), k -> new HashMap<>()).put(s.id(), s.old());
        for (Step s : steps) {
            if (s.now() == null) w.remove(s.giant(), s.id());
            else w.put(s.giant(), s.id(), s.now());
        }
        String what = imported ? "import" : snap ? "snapshot" : "preset:" + id;
        String name = snap ? p.bundle().name()
                : p.bundle().name().isEmpty() ? (imported ? "imported settings" : id)
                : "preset " + p.bundle().name();
        w.log(new AdminWorld.Change(System.currentTimeMillis(), who, "everything", what, name, "",
                steps.size() + (steps.size() == 1 ? " change" : " changes"), AdminWorld.Change.undo(before)));
        AdminServer.push(server, w);
        return steps.size();
    }

    /** a world's values as a bundle that puts exactly them back (a snapshot) */
    public static Bundle snapshotBundle(AdminWorld.Snapshot s) {
        String when = new java.text.SimpleDateFormat("MMM d HH:mm", java.util.Locale.ROOT)
                .format(new java.util.Date(s.time()));
        return new Bundle("snapshot", "snapshot from " + when, s.why(), true, s.values());
    }

    // the danger rating if a plan were applied (the Presets page's preview)

    /** the rating now, and as it would be with the plan applied */
    public static AdminInsight.Danger[] dangerBeforeAfter(MinecraftServer server, Plan p) {
        return dangerBeforeAfter(AdminView.of(server), p);
    }

    public static AdminInsight.Danger[] dangerBeforeAfter(AdminView view, Plan p) {
        AdminWorld w = view.world();
        Map<String, Map<String, JsonElement>> after = new HashMap<>();
        for (Step s : p.changes())
            after.computeIfAbsent(s.giant(), k -> new HashMap<>()).put(s.id(), s.now());
        List<AdminInsight.View> now = new ArrayList<>(), then = new ArrayList<>();
        for (Giant g : AdminServer.giants()) {
            AdminJson.Report r = view.report(g);
            if (r == null) continue;
            now.add(AdminInsight.view(r));
            Map<String, JsonElement> ch = after.getOrDefault(g.key(), Map.of());
            List<AdminSetting> list = new ArrayList<>();
            for (AdminSetting s : r.settings()) {
                if (!ch.containsKey(s.id())) {
                    list.add(s);
                    continue;
                }
                JsonElement v = ch.get(s.id());
                list.add(new AdminSetting(s.giant(), s.id(), s.cat(), s.type(), s.min(), s.max(), s.step(),
                        s.options(), s.def(), v == null ? s.def() : v, s.jar(), v != null, s.when(), s.name(),
                        s.what(), s.key(), s.group()));
            }
            then.add(AdminInsight.view(new AdminJson.Report(r.giant(), r.name(), r.version(), r.uses(), r.all(),
                    list)));
        }
        Map<String, Double> allNow = new HashMap<>(), allThen = new HashMap<>();
        Map<String, JsonElement> chAll = after.getOrDefault(AdminCatalog.ALL, Map.of());
        for (AdminSetting s : AdminServer.shared(w)) {
            // (Part 5: the player settings are no multipliers; Part 8: the systems' switches and numbers count)
            if (AdminCatalog.player(s)) continue;
            if (AdminCatalog.systems(s) && s.type() == AdminSetting.Type.CHOICE) continue;
            allNow.put(s.id(), number(s.value()));
            if (chAll.containsKey(s.id())) {
                JsonElement v = chAll.get(s.id());
                allThen.put(s.id(), number(v == null ? s.def() : v));
            } else allThen.put(s.id(), number(s.value()));
        }
        return new AdminInsight.Danger[] {AdminInsight.danger(now, allNow), AdminInsight.danger(then, allThen)};
    }

    /** a value as the rating reads it: on/off as 1/0 */
    private static double number(JsonElement v) {
        if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) return v.getAsBoolean() ? 1 : 0;
        return v.getAsDouble();
    }

    // waiting for /giants admin confirm: one plan per person, for five minutes

    private record Pending(Plan plan, long until) {}

    private static final Map<String, Pending> PENDING = new HashMap<>();

    public static void hold(String who, Plan p) {
        PENDING.put(who, new Pending(p, System.currentTimeMillis() + 5 * 60_000L));
    }

    public static @Nullable Plan take(String who) {
        Pending p = PENDING.remove(who);
        return p == null || p.until() < System.currentTimeMillis() ? null : p.plan();
    }

    public static void forget() {
        PENDING.clear();
    }
}
