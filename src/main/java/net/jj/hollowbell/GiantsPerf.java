package net.jj.hollowbell;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Giants Performance levels (the Giants Guide's screen, PERFORMANCE_BRIDGE.md in the guide). The guide asks
 * GiantsBridge "perf" for the list below and sends "perfset" with the values; the fx code reads them here.
 *
 * Every level is a share or a switch on top of this mod's own config: 1 / true / 0 (the default) changes nothing,
 * so without the guide, or at its Highest preset, he is exactly as he was.
 */
public final class GiantsPerf {
    private GiantsPerf() {}

    /** the protocol version this mod speaks */
    public static final int VERSION = 1;

    /** the guide's shared settings this mod reads (only these are listed as "read by Hollowbell") */
    private static final String[] USES = {
        "fx.particles",
        "fx.bigImpacts",
        "fx.smokeTrails",
        "fx.flashes",
        "fx.screenShake",
        "fx.screenEffects",
        "fx.ambient",
        "detail.model",
        "detail.animation",
        "detail.farSight",
        "detail.farSightDistance",
        "detail.lootBeams",
        "detail.sounds",
        "detail.music",
        "detail.renderDistance"
    };

    /** the shared settings that are choices: 0 (the first, best) by default */
    private static final String[] CHOICES = {"detail.animation", "detail.lootBeams"};

    /** his own settings, one JSON object each (see the guide's PERFORMANCE_BRIDGE.md for the fields) */
    private static final String[] OWN = {
        own("hollowbell.soundRings", "giant", "med", "client")
                .slider(1, new double[] {1, 1, 0.75, 0.5, 0.25})
                .words(
                        "Sound rings",
                        "The rings of sound that run out from his bell when it tolls or slams.",
                        "Fainter rings, none at 0%; it is harder to see how far a toll reaches.")
                .json(),
        own("hollowbell.rimShiver", "giant", "low", "client")
                .slider(1, new double[] {1, 1, 1, 0.5, 0})
                .words(
                        "Rim shiver",
                        "His rim shivering on after a toll, like a struck bell.",
                        "The rim settles sooner; at 0% it doesn't shiver after a toll.")
                .json(),
        own("hollowbell.venomClouds", "giant", "med", "client")
                .slider(1, new double[] {1, 1, 0.75, 0.5, 0.25})
                .words(
                        "Venom clouds",
                        "The green clouds where a stinger, a barb or his spores hang in the air.",
                        "Thinner clouds, none at 0%. The poison is still there.")
                .json(),
        own("hollowbell.stingerStreaks", "giant", "low", "client")
                .toggle(true, new boolean[] {true, true, true, true, false})
                .words(
                        "Stinger streaks",
                        "The green streak of poison behind each stinger in flight.",
                        "Stingers are drawn as a small glowing head with no streak behind it.")
                .json(),
        own("hollowbell.deepTollFx", "giant", "med", "client")
                .toggle(true, new boolean[] {true, true, true, true, true})
                .words(
                        "Deep Toll: rings and glints",
                        "The three tolls rippling out and the glass glinting in his Deep Toll.",
                        "The Deep Toll shows only its one big ring along the ground.")
                .json(),
        own("hollowbell.sunLancesFx", "giant", "med", "client")
                .toggle(true, new boolean[] {true, true, true, true, true})
                .words(
                        "Sun Lances: gathering light and burns",
                        "Light gathering into his spots and the burn marks the Sun Lances leave.",
                        "Only the beams themselves show.")
                .json(),
        own("hollowbell.bellings", "features", "high", "server")
                .slider(1, new double[] {1, 1, 1, 0.6, 0.3})
                .ops()
                .words(
                        "Belling count",
                        "How many bellings come out of his egg clumps and eggs.",
                        "Fewer bellings: he sheds fewer egg clumps at a time (at least one) and fewer eggs hatch.")
                .json()
    };

    /** each id's default: what it is when the guide hasn't said (Highest) */
    private static final Map<String, Double> DEFAULTS = new HashMap<>();

    static {
        for (String id : USES) DEFAULTS.put(id, 1.0);
        for (String id : CHOICES) DEFAULTS.put(id, 0.0);
        for (String s : OWN) {
            JsonObject o = JsonParser.parseString(s).getAsJsonObject();
            var d = o.get("default").getAsJsonPrimitive();
            DEFAULTS.put(o.get("id").getAsString(), d.isBoolean() ? (d.getAsBoolean() ? 1.0 : 0.0) : d.getAsDouble());
        }
    }

    // what the guide said, for this player's own game and for the world (read on any thread: swapped whole)
    private static volatile Map<String, Double> client = Map.of(), server = Map.of();

    /** GiantsBridge "perf": the header, then his own settings */
    public static List<String> describe() {
        JsonObject head = new JsonObject();
        head.addProperty("perf", VERSION);
        head.addProperty("giant", HollowbellMod.MOD_ID);
        JsonArray uses = new JsonArray();
        for (String u : USES) uses.add(u);
        head.add("uses", uses);
        List<String> out = new ArrayList<>();
        out.add(head.toString());
        for (String s : OWN) out.add(s);
        return out;
    }

    /** GiantsBridge "perfset": {"perf":1,"side":"client"|"server","values":{...}}; null if it can't be read */
    public static List<String> receive(String arg) {
        try {
            JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
            if (o.get("perf").getAsInt() > VERSION) return null;
            Map<String, Double> v = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("values").entrySet()) {
                if (!e.getValue().isJsonPrimitive()) continue;
                var p = e.getValue().getAsJsonPrimitive();
                double d = p.isBoolean() ? (p.getAsBoolean() ? 1 : 0) : p.getAsDouble();
                if (Double.isFinite(d)) v.put(e.getKey(), Math.max(0, Math.min(8, d)));
            }
            if ("server".equals(o.get("side").getAsString())) server = Map.copyOf(v);
            else client = Map.copyOf(v);
            return new ArrayList<>(List.of("Hollowbell: performance levels taken."));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** back to what the guide never touched (the tests) */
    public static void reset() {
        client = Map.of();
        server = Map.of();
    }

    // reading them

    private static double raw(Map<String, Double> m, String id) {
        return m.getOrDefault(id, DEFAULTS.getOrDefault(id, 1.0));
    }

    /** a share for this player's own game, 0..1 (1 when the guide hasn't said) */
    public static float f(String id) {
        return (float) Math.max(0, Math.min(1, raw(client, id)));
    }

    /** a switch for this player's own game (on when the guide hasn't said) */
    public static boolean on(String id) {
        return raw(client, id) >= 0.5;
    }

    /** a choice for this player's own game (0, the first, when the guide hasn't said) */
    public static int choice(String id) {
        return (int) Math.max(0, Math.min(7, Math.round(raw(client, id))));
    }

    /** a share for the whole world (the server's), 0..1 */
    public static float world(String id) {
        return (float) Math.max(0, Math.min(1, raw(server, id)));
    }

    /** a switch for the whole world */
    public static boolean worldOn(String id) {
        return raw(server, id) >= 0.5;
    }

    /** n things at this share: rounded, never fewer than one while n and the share are over 0 */
    public static int count(int n, float share) {
        if (n <= 0 || share <= 0f) return 0;
        if (share >= 1f) return n;
        return Math.max(1, Math.round(n * share));
    }

    /**
     * true about share of the time (always at 1, never at 0): for thinning out things made one at a time. At 1 no
     * random number is drawn, so nothing else changes.
     */
    public static boolean keep(float share) {
        return share >= 1f || (share > 0f && java.util.concurrent.ThreadLocalRandom.current().nextFloat() < share);
    }

    // writing his own setting lines

    private static Line own(String id, String section, String cost, String side) {
        Line l = new Line();
        l.o.addProperty("id", id);
        l.o.addProperty("section", section);
        l.o.addProperty("cost", cost);
        l.o.addProperty("side", side);
        return l;
    }

    private static final class Line {
        final JsonObject o = new JsonObject();

        Line slider(double def, double[] levels) {
            o.addProperty("kind", "slider");
            o.addProperty("min", 0);
            o.addProperty("max", 1);
            o.addProperty("step", 0.05);
            o.addProperty("unit", "%");
            o.addProperty("default", def);
            JsonArray a = new JsonArray();
            for (double d : levels) a.add(d);
            o.add("levels", a);
            return this;
        }

        Line toggle(boolean def, boolean[] levels) {
            o.addProperty("kind", "toggle");
            o.addProperty("default", def);
            JsonArray a = new JsonArray();
            for (boolean b : levels) a.add(b);
            o.add("levels", a);
            return this;
        }

        Line ops() {
            o.addProperty("ops", true);
            return this;
        }

        Line words(String name, String what, String loses) {
            o.addProperty("name", name);
            o.addProperty("what", what);
            o.addProperty("loses", loses);
            return this;
        }

        String json() {
            return o.toString();
        }
    }
}
