package net.jj.hollowbell;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.world.Away;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * The admin settings (the Giants Guide's admin book and /giants admin, ADMIN_BRIDGE.md in the handoff folder). The
 * guide asks GiantsBridge "admin" for the list below, keeps the world's choices with the world, and sends "adminset"
 * with them: every setting the world has changed, and the all-giants multipliers.
 *
 * His config file stays the server's defaults. What the world changes goes on top of it in BellConfig.V
 * (BellConfig.compose), and is never written into the file. Without the guide, or in a world where nobody changed
 * anything, nothing here is set and he is exactly as he was.
 */
public final class GiantsAdmin {
    private GiantsAdmin() {}

    /** the protocol version this mod speaks */
    public static final int VERSION = 1;

    public static final String GIANT = HollowbellMod.MOD_ID, NAME = "Hollowbell";

    /** the guide's all-giants multipliers this mod applies on top of his own (see ADMIN_BRIDGE.md) */
    public static final String[] USES = {
        "all.health", "all.damage", "all.speed", "all.size", "all.cooldowns", "all.healing", "all.windCost"
    };

    // when a change takes effect
    public static final String LIVE = "live", NEW_SPAWNS = "newSpawns", NEW_LAND = "newLand",
            WORLD_CREATION = "worldCreation", RESTART = "restart";

    /** one of his settings: a BellConfig field (field != null) or one of his moves' own */
    public record Row(
            String id,
            String cat,
            String type,
            double min,
            double max,
            double step,
            String when,
            String name,
            String what,
            @Nullable Field field,
            Object jar) {}

    private static final List<Row> ROWS = new ArrayList<>();
    private static final Map<String, Row> BY_ID = new LinkedHashMap<>();

    /**
     * The config fields that are not admin settings: how he looks and sounds on each player's own game (those are
     * the Performance screen's, and a world can't set them for a player; his ambient hum is played by each player's
     * own game from its own file), the file's own version, and maxInWorld's old name (only read from old files).
     */
    public static final Set<String> NOT_ADMIN = Set.of(
            "configVersion",
            "maxHollowbells",
            "soundVolume",
            "fightMusic",
            "ambientSounds",
            "screenShake",
            "bigEffects",
            "renderDistance",
            "simpleFarAway",
            "simpleFarAwayAt",
            "farSightBlocks");

    /** a move's name the way his book says it (from his language file), or its id */
    private static String moveName(int m) {
        return LANG.getOrDefault("move.hollowbell." + Moves.NAMES[m], Moves.NAMES[m]);
    }

    private static final Map<String, String> LANG = new HashMap<>();

    static {
        try (InputStream in = GiantsAdmin.class.getResourceAsStream("/assets/hollowbell/lang/en_us.json")) {
            if (in != null) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                for (var e : o.entrySet())
                    if (e.getKey().startsWith("move.hollowbell.")) LANG.put(e.getKey(), e.getValue().getAsString());
            }
        } catch (Exception e) {
            HollowbellMod.LOG.debug("No move names for the admin settings: {}", e.toString());
        }
    }

    private static void cfg(String id, String cat, double min, double max, double step, String when, String name,
            String what) {
        try {
            Field f = BellConfig.Values.class.getField(id);
            Class<?> t = f.getType();
            String type = t == boolean.class ? "bool" : t == int.class ? "int" : "float";
            add(new Row(id, cat, type, min, max, step, when, name, what, f, f.get(new BellConfig.Values())));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("no config field " + id, e);
        }
    }

    private static void cfg(String id, String cat, String when, String name, String what) {
        cfg(id, cat, 0, 1, 1, when, name, what);
    }

    private static void add(Row r) {
        ROWS.add(r);
        BY_ID.put(r.id(), r);
    }

    static {
        // spawning
        cfg("oneInTheWorld", "spawning", LIVE, "Comes on his own",
                "One of him rises on his own somewhere out in the world.");
        cfg("maxInWorld", "spawning", 0, 20, 1, NEW_SPAWNS, "Most at once",
                "Most of him in the world at once; past it the oldest goes when another comes. 0 = no limit.");
        cfg("worldScale", "spawning", 0.03, 2, 0.01, NEW_SPAWNS, "Size of the world's own",
                "How big the one that rises on his own is (1 = full size).");
        cfg("spawnEggScale", "spawning", 0.03, 2, 0.01, NEW_SPAWNS, "Spawn egg size",
                "His size from a spawn egg and a plain summon (1 = full size).");
        cfg("smallEggScale", "spawning", 0.03, 2, 0.01, NEW_SPAWNS, "Small spawn egg size",
                "His size from the small spawn egg.");
        cfg("worldRespawnDays", "spawning", 0, 1000, 1, NEW_SPAWNS, "Days before he comes back",
                "Days after the world's own one dies before the next one rises.");
        cfg("ownedRespawnDays", "spawning", 0, 1000, 1, NEW_SPAWNS, "Days before an owned one comes back",
                "The same when the one killed was a player's (somebody held his book).");
        cfg("respawnBlocks", "spawning", 0, 100000, 100, NEW_SPAWNS, "How far off he comes back",
                "The furthest from where the last one died that the next one rises, in blocks.");
        // world gen
        cfg("homeRadius", "worldgen", 200, 2000, 50, NEW_LAND, "Size of his ground",
                "How far the Bell Hollows reach from their middle, in blocks. Used the next time his ground is"
                        + " chosen (a new world, or /hollowbell ground new); land already made stays.");
        cfg("groundNearest", "worldgen", 0, 100000, 100, WORLD_CREATION, "Nearest to spawn",
                "How close to the world's spawn his ground may begin (its nearest edge), in blocks. Used when his first"
                        + " ground and any extra copies are chosen.");
        cfg("groundFarthest", "worldgen", 0, 100000, 100, WORLD_CREATION, "Farthest from spawn",
                "How far from the world's spawn his ground may begin at most (its nearest edge), in blocks.");
        cfg("groundGap", "worldgen", 0, 20000, 50, NEW_LAND, "Room from other grounds",
                "Blocks kept between his ground and every other giant's (edge to edge) whenever one of his grounds is"
                        + " chosen. 0 = no rule while the world is new (after he falls his next ground still keeps 200"
                        + " off).");
        cfg("groundCopies", "worldgen", 1, 5, 1, NEW_LAND, "Copies of his ground",
                "How many grounds of his biome the world has. More than 1: the others are chosen by the same rules when"
                        + " the world next starts, on land not made yet, and made as the world makes that land.");
        // stats
        cfg("health", "stats", 10, 1000000, 10, LIVE, "Health", "His health at full size; a smaller one has less.");
        cfg("healthMultiplier", "stats", 0.1, 10, 0.05, LIVE, "Health multiplier",
                "His health times this (the same as /giants health).");
        cfg("damageMultiplier", "stats", 0.1, 10, 0.05, LIVE, "Damage multiplier",
                "Every hit he lands times this (the same as /giants damage).");
        cfg("mobDamage", "stats", 0, 20, 0.1, LIVE, "Damage to creatures",
                "His hits on anything that isn't a player are times this.");
        cfg("explosionArmor", "stats", 0, 10, 0.01, LIVE, "Blasts reaching him",
                "How much of a blast reaches him (TNT, creepers, anything that explodes).");
        cfg("giantArmor", "stats", 0, 10, 0.01, LIVE, "Giants' blows reaching him",
                "How much of a blow from another giant reaches him.");
        cfg("scaleToPlayers", "stats", LIVE, "Stronger against groups",
                "Each player after the first in a fight gives him 30% more health, up to three times as much.");
        cfg("podPopShare", "stats", 0, 1, 0.005, LIVE, "Health a pod takes",
                "How much of his health a popped pod takes with it (0.01 = 1%).");
        cfg("podsToSink", "stats", 0, 1, 0.01, LIVE, "Pods to sink him",
                "The share of his pods that has to be popped before he loses his lift and sinks (0.34 = a third).");
        cfg("sunkSeconds", "stats", 0, 600, 5, LIVE, "Stays sunk",
                "Once he sinks, he stays down at least this long, in seconds.");
        cfg("podRegrowSeconds", "stats", 0, 3600, 5, LIVE, "Pod regrowth wait",
                "Seconds before a popped pod starts growing back (it then takes about 20 more to grow).");
        // behaviour
        cfg("fightGiants", "behaviour", LIVE, "Fights other giants",
                "He picks fights with the other giants when they come near.");
        cfg("meetings", "behaviour", LIVE, "Giant meetings",
                "When he wanders near another giant he fights it or keeps away from it.");
        cfg("meetRange", "behaviour", 16, 1000, 8, LIVE, "Notices giants from",
                "How far off (in blocks, at full size) he notices another giant.");
        cfg("meetCooldown", "behaviour", 0, 1440, 1, LIVE, "Minutes between meetings",
                "How long before the same two giants can meet to fight again.");
        cfg("yieldAt", "behaviour", 0, 0.9, 0.05, LIVE, "Backs down at",
                "In a fight with another giant, he backs down under this share of his health. 0 = to the death.");
        cfg("fleeInWildFights", "behaviour", LIVE, "Backs down from wild fights",
                "He backs down from a wild fight he's losing (the same as /giants flee).");
        cfg("grudgeRate", "behaviour", 0, 5, 0.1, LIVE, "Grudges",
                "How fast his grudges grow: 1 normal, 0 never, 2 twice as fast.");
        cfg("griefing", "behaviour", LIVE, "Breaks blocks",
                "He pulls trees up, flattens plants with his strands and cracks the ground with his arm (also needs"
                        + " the mobGriefing rule).");
        cfg("harvest", "behaviour", LIVE, "Harvests",
                "He pulls cows, villagers and trees up into his dome.");
        cfg("insideDome", "behaviour", LIVE, "Keeps them in his dome",
                "What he pulls all the way up ends up inside his dome. Off: he squeezes it and lets go.");
        // moves
        cfg("heavyDelaySeconds", "moves", 0, 600, 5, LIVE, "Heavy moves wait",
                "Over half health he keeps his heavy moves until this many seconds into a fight.");
        cfg("shedCount", "moves", 0, 20, 1, LIVE, "Egg clumps shed",
                "How many egg clumps he sheds at a time. 0 = he never sheds.");
        // healing
        cfg("passiveHeal", "healing", LIVE, "Heals on his own",
                "He heals while he sleeps, and once he's been out of a fight for a while.");
        cfg("healHomePerMinute", "healing", 0, 100, 0.5, LIVE, "Healing in his ground",
                "Percent of his most health a minute over the Bell Hollows.");
        cfg("healAwayPerMinute", "healing", 0, 100, 0.5, LIVE, "Healing elsewhere",
                "Percent of his most health a minute anywhere else.");
        cfg("healPauseMinutes", "healing", 0, 60, 0.5, LIVE, "Minutes before healing",
                "Minutes out of any fight before he heals (asleep, he heals anyway).");
        // taming and the book
        cfg("bookCosts", "taming", LIVE, "Orders cost wind",
                "Orders from his book cost him wind, which comes back slowly.");
        cfg("windSeconds", "taming", 10, 3600, 5, LIVE, "Wind refill",
                "Seconds to get all his wind back from nothing.");
        cfg("freeHits", "taming", 0, 100, 1, LIVE, "Free hits",
                "Hits a book holder may land before he stops listening to the book. 0 = never.");
        cfg("bookRange", "taming", 16, 4096, 16, LIVE, "Book range",
                "How far the book reaches him, in blocks.");
        cfg("burnOwnersBook", "taming", LIVE, "Owner's book burns",
                "When a player's Hollowbell is killed their book burns, and passes to the next player carrying a"
                        + " copy.");
        cfg("wardBlocks", "taming", 0, 20000, 50, LIVE, "Crown keeps him away",
                "How far his crown, woken against him, holds him off, in blocks.");
        cfg("wardSeconds", "taming", 0, 86400, 60, LIVE, "Crown lasts",
                "How long the crown's ward lasts once it's started, in seconds.");
        cfg("wardRestSeconds", "taming", 0, 86400, 60, LIVE, "Crown rests",
                "How long the crown sits dark after a ward, in seconds.");
        // effects (the ones the server sends; how things look is each player's own, on the Performance screen)
        cfg("bossBar", "effects", LIVE, "Boss bars", "Shows his health and pods bars.");
        cfg("bossBarRange", "effects", 0, 100000, 10, LIVE, "Boss bar range",
                "How far away his boss bars still show, in blocks. 0 = worked out from his size.");
        // server load
        cfg("chunkLoading", "load", LIVE, "Keeps his land loaded",
                "Keeps the ground under him loaded while a player can see him, so he never freezes in the air.");
        cfg("offscreenTravel", "load", LIVE, "Steps out of the world",
                "With nobody near he steps out of the world and keeps going as a sum.");
        cfg("awayBlocks", "load", 0, 100000, 16, LIVE, "Steps out past",
                "How far the nearest player has to be before he steps out. 0 = worked out for you.");
        // each fighting move: on or off, its wait and its wind (the book's cost)
        for (int m : Moves.ORDER) {
            String n = Moves.NAMES[m], shown = moveName(m);
            add(new Row("move." + n + ".on", "moves", "bool", 0, 1, 1, LIVE, shown + ": on",
                    "Off: he never uses " + shown + ", and neither his book nor his rider can ask for it.",
                    null, true));
            add(new Row("move." + n + ".cooldown", "moves", "float", 0.25, 4, 0.05, LIVE, shown + ": wait",
                    "The wait after " + shown + " before his next move, and the book's wait for it, times this.",
                    null, 1.0));
            add(new Row("move." + n + ".wind", "moves", "float", 0, 4, 0.05, LIVE, shown + ": wind",
                    "What " + shown + " costs his wind when his book or his rider asks for it, times this.",
                    null, 1.0));
        }
    }

    public static List<Row> rows() {
        return List.copyOf(ROWS);
    }

    public static @Nullable Row row(String id) {
        return BY_ID.get(id);
    }

    // what the world has set (from the guide), swapped whole so any thread reads a whole one

    private static volatile Map<String, JsonPrimitive> world = Map.of();
    private static volatile Map<String, Double> all = Map.of();
    private static volatile boolean[] moveOn = filled(true);
    private static volatile float[] moveCool = filledF(), moveWind = filledF();

    private static boolean[] filled(boolean v) {
        boolean[] a = new boolean[Moves.NAMES.length];
        java.util.Arrays.fill(a, v);
        return a;
    }

    private static float[] filledF() {
        float[] a = new float[Moves.NAMES.length];
        java.util.Arrays.fill(a, 1f);
        return a;
    }

    /** true when this world has its own value for that id */
    public static boolean overridden(String id) {
        return world.containsKey(id);
    }

    /** the world's own values, as the guide sent them (after checking) */
    public static Map<String, JsonPrimitive> world() {
        return world;
    }

    // the bridge

    /** GiantsBridge "admin": the header, then one line per setting with its value now */
    public static List<String> describe() {
        JsonObject head = new JsonObject();
        head.addProperty("admin", VERSION);
        head.addProperty("giant", GIANT);
        head.addProperty("name", NAME);
        JsonArray uses = new JsonArray();
        for (String u : USES) uses.add(u);
        head.add("uses", uses);
        JsonObject a = new JsonObject();
        for (var e : all.entrySet()) a.addProperty(e.getKey(), e.getValue());
        head.add("all", a);
        List<String> out = new ArrayList<>();
        out.add(head.toString());
        for (Row r : ROWS) out.add(line(r).toString());
        return out;
    }

    private static JsonObject line(Row r) {
        JsonObject o = new JsonObject();
        o.addProperty("id", r.id());
        o.addProperty("cat", r.cat());
        o.addProperty("type", r.type());
        if (!r.type().equals("bool")) {
            o.addProperty("min", r.min());
            o.addProperty("max", r.max());
            o.addProperty("step", r.step());
        }
        o.add("default", prim(serverDefault(r)));
        o.add("value", prim(current(r)));
        o.add("jar", prim(r.jar()));
        o.addProperty("set", world.containsKey(r.id()));
        o.addProperty("when", r.when());
        o.addProperty("name", r.name());
        o.addProperty("what", r.what());
        o.addProperty("key", "admin." + GIANT + "." + r.id());
        if (r.id().startsWith("move.")) {
            o.addProperty("group", r.id().substring(5, r.id().lastIndexOf('.')));
        }
        return o;
    }

    private static JsonPrimitive prim(Object v) {
        if (v instanceof Boolean b) return new JsonPrimitive(b);
        if (v instanceof Integer i) return new JsonPrimitive(i);
        if (v instanceof Float f) return new JsonPrimitive(Double.parseDouble(Float.toString(f)));
        if (v instanceof Number n) return new JsonPrimitive(n.doubleValue());
        return new JsonPrimitive(String.valueOf(v));
    }

    /** the server's default: what his config file says (a move's: on, x1, x1) */
    public static Object serverDefault(Row r) {
        return r.field() == null ? r.jar() : BellConfig.fileValue(r.field());
    }

    /** the value in use now */
    public static Object current(Row r) {
        if (r.field() != null) {
            try {
                return r.field().get(BellConfig.V);
            } catch (IllegalAccessException e) {
                return r.jar();
            }
        }
        JsonPrimitive p = world.get(r.id());
        if (p == null) return r.jar();
        return r.type().equals("bool") ? (Object) p.getAsBoolean() : (Object) p.getAsDouble();
    }

    /**
     * GiantsBridge "adminset": {"admin":1,"values":{id:value,...},"all":{"all.health":2,...}}. Everything the world
     * has set; anything left out goes back to the server's default. Returns "ok", then a line for each value it
     * couldn't take; null if it can't be read at all.
     */
    public static @Nullable List<String> receive(@Nullable MinecraftServer server, String arg) {
        Map<String, JsonPrimitive> w = new LinkedHashMap<>();
        Map<String, Double> a = new HashMap<>();
        List<String> out = new ArrayList<>();
        out.add("ok");
        try {
            JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
            if (o.get("admin").getAsInt() > VERSION) return null;
            JsonObject values = o.has("values") ? o.getAsJsonObject("values") : new JsonObject();
            for (Map.Entry<String, JsonElement> e : values.entrySet()) {
                Row r = BY_ID.get(e.getKey());
                JsonPrimitive v = r == null ? null : check(r, e.getValue());
                if (v == null) out.add(rejected(e.getKey(), r == null ? "unknown" : "bad value"));
                else w.put(r.id(), v);
            }
            JsonObject al = o.has("all") ? o.getAsJsonObject("all") : new JsonObject();
            for (Map.Entry<String, JsonElement> e : al.entrySet()) {
                if (!List.of(USES).contains(e.getKey())) continue; // (one he doesn't apply)
                try {
                    double d = e.getValue().getAsDouble();
                    if (Double.isFinite(d)) a.put(e.getKey(), Math.max(0, Math.min(100, d)));
                } catch (RuntimeException bad) {
                    out.add(rejected(e.getKey(), "bad value"));
                }
            }
        } catch (RuntimeException e) {
            return null;
        }
        float before = fullHealth();
        put(w, a);
        if (server != null) applyLive(server, before);
        return out;
    }

    private static String rejected(String id, String why) {
        JsonObject o = new JsonObject();
        o.addProperty("rejected", id);
        o.addProperty("why", why);
        return o.toString();
    }

    /** a value pulled into its row's range and onto its step; null if it isn't one of that type */
    static @Nullable JsonPrimitive check(Row r, JsonElement e) {
        if (e == null || !e.isJsonPrimitive()) return null;
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (r.type().equals("bool")) return p.isBoolean() ? p : null;
        if (!p.isNumber()) return null;
        double d = p.getAsDouble();
        if (!Double.isFinite(d)) return null;
        d = Math.max(r.min(), Math.min(r.max(), d));
        if (r.type().equals("int")) return new JsonPrimitive((int) Math.round(d));
        return new JsonPrimitive(d);
    }

    /** the world's values in, his config put together again with them on top */
    static void put(Map<String, JsonPrimitive> w, Map<String, Double> a) {
        boolean[] on = filled(true);
        float[] cool = filledF(), wind = filledF();
        for (var e : w.entrySet()) {
            String id = e.getKey();
            if (!id.startsWith("move.")) continue;
            int m = Moves.byName(id.substring(5, id.lastIndexOf('.')));
            if (m <= 0) continue;
            if (id.endsWith(".on")) on[m] = e.getValue().getAsBoolean();
            else if (id.endsWith(".cooldown")) cool[m] = e.getValue().getAsFloat();
            else if (id.endsWith(".wind")) wind[m] = e.getValue().getAsFloat();
        }
        world = Map.copyOf(w);
        all = Map.copyOf(a);
        moveOn = on;
        moveCool = cool;
        moveWind = wind;
        BellConfig.compose();
    }

    /** a world's settings, back to none (when the server stops, so the next world starts from the file) */
    public static void clear() {
        put(Map.of(), Map.of());
    }

    /** his full-size health as it is in use now: the setting times his own multiplier and the all-giants one */
    static float fullHealth() {
        return BellConfig.V.health * HollowbellEntity.healthTimes();
    }

    /**
     * What changes at once for the ones standing: his most health (keeping the share of it he has now), and the same
     * for the ones out of the world (as /giants health does)
     */
    static void applyLive(MinecraftServer server, float fullBefore) {
        for (ServerLevel l : server.getAllLevels())
            for (HollowbellEntity h : l.getEntities(ModEntities.HOLLOWBELL, e -> !e.isRemoved() && !e.isDeadOrDying()))
                h.setBellScale(h.bellScale());
        float k = fullBefore > 0f ? fullHealth() / fullBefore : 1f;
        if (k == 1f || !Float.isFinite(k)) return;
        Away away = Away.get(server);
        for (Away.Rec r : away.all()) {
            r.hp *= k;
            r.hpMax *= k;
        }
        away.setDirty();
    }

    /** puts the world's own values on top of the file's (BellConfig.compose calls it) */
    static void applyTo(BellConfig.Values v) {
        for (var e : world.entrySet()) {
            Row r = BY_ID.get(e.getKey());
            if (r == null || r.field() == null) continue;
            try {
                switch (r.type()) {
                    case "bool" -> r.field().setBoolean(v, e.getValue().getAsBoolean());
                    case "int" -> r.field().setInt(v, e.getValue().getAsInt());
                    default -> r.field().setFloat(v, e.getValue().getAsFloat());
                }
            } catch (IllegalAccessException | RuntimeException ex) {
                HollowbellMod.LOG.warn("Admin setting {} not taken: {}", r.id(), ex.toString());
            }
        }
    }

    /**
     * For the old commands (/giants health, /hollowbell config...): they change the server's default, as they always
     * did; if this world keeps its own value for that setting, they say so.
     */
    public static String note(String id) {
        Row r = BY_ID.get(id);
        if (r == null || !world.containsKey(id)) return "";
        return " This world keeps its own: " + plain(current(r)) + " (/giants admin reset " + GIANT + " " + id + ").";
    }

    private static String plain(Object v) {
        if (v instanceof Boolean b) return b ? "on" : "off";
        if (v instanceof Float f) return new java.math.BigDecimal(Float.toString(f)).stripTrailingZeros()
                .toPlainString();
        return String.valueOf(v);
    }

    // reading them

    /** one of the all-giants multipliers (1 when nobody set it) */
    public static float all(String id) {
        return all.getOrDefault(id, 1.0).floatValue();
    }

    /** a size he is given when he comes (the world's own, an egg, a plain summon) times the all-giants size */
    public static float sized(float size) {
        float times = all("all.size");
        if (times == 1f) return size;
        return Math.max(HollowbellEntity.MIN_SCALE, Math.min(HollowbellEntity.MAX_SCALE, size * times));
    }

    /** whether he may use this move (his own choice, his book, his rider) */
    public static boolean moveOn(int move) {
        return move < 0 || move >= moveOn.length || moveOn[move];
    }

    /** the wait after this move (and the book's wait for it) times this: his own and the all-giants one */
    public static float moveCooldown(int move) {
        float own = move < 0 || move >= moveCool.length ? 1f : moveCool[move];
        return own * all("all.cooldowns");
    }

    /** what this move costs his wind (the book, his rider) times this */
    public static float moveWind(int move) {
        return move < 0 || move >= moveWind.length ? 1f : moveWind[move];
    }
}
