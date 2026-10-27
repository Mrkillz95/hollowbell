package net.jj.giants.perf;

import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.jj.giants.Giant;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.perf.PerfSetting.Kind;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * The performance half of the bridge (PERFORMANCE_BRIDGE.md). The guide calls each giant's GiantsBridge by name,
 * the same as /giants does, with two more actions:
 * <ul>
 *   <li>"perf", arg "1" (the guide's protocol version): his list of settings, as JSON lines;
 *   <li>"perfset", arg one JSON object {"perf":1,"side":"client"|"server","values":{...}}: the values to use.
 * </ul>
 * An older mod answers null to both (an action it doesn't know), and is simply left out. Whatever goes wrong inside
 * one giant's mod only leaves that giant out.
 */
public final class PerfBridges {
    private PerfBridges() {}

    public static final String LIST = "perf", SET = "perfset";

    /** one call to a giant's bridge; null if it isn't there, doesn't know the action, or fails */
    public static @Nullable List<String> call(@Nullable MinecraftServer server, Giant g, String action, String arg) {
        try {
            Class<?> c = Class.forName(g.bridge(), true, PerfBridges.class.getClassLoader());
            Method m = c.getMethod("giants", MinecraftServer.class, String.class, String.class);
            if (!Modifier.isStatic(m.getModifiers())) return null;
            Object r = m.invoke(null, server, action, arg);
            if (!(r instanceof List<?> l)) return null;
            List<String> out = new ArrayList<>();
            for (Object o : l) if (o != null && out.size() < PerfJson.MOST_SETTINGS + 2) out.add(o.toString());
            return out;
        } catch (Throwable t) {
            // a call into another giant's mod: an old one, a missing one, or a crash in it
            GiantsGuideMod.LOG.debug("No {} from {}: {}", action, g.key(), t.toString());
            return null;
        }
    }

    /** what this giant's mod says it has; null for a mod that doesn't speak the protocol */
    public static @Nullable PerfJson.Report ask(@Nullable MinecraftServer server, Giant g) {
        return PerfJson.read(g.key(), call(server, g, LIST, String.valueOf(PerfJson.VERSION)));
    }

    /** every giant here that speaks it, in the guide's order */
    public static List<PerfJson.Report> askAll(@Nullable MinecraftServer server, List<Giant> giants) {
        List<PerfJson.Report> out = new ArrayList<>();
        for (Giant g : giants) {
            PerfJson.Report r = ask(server, g);
            if (r != null) out.add(r);
        }
        return out;
    }

    /**
     * The values one giant is sent for one side: every guide setting of that side (his own copy of the per-giant
     * ones, under their plain id), and his own settings. Anything not set is sent as its default, so a giant never
     * has to guess; at Highest everything is its default and nothing changes.
     */
    public static JsonObject valuesFor(String giant, boolean server, List<PerfSetting> settings, Map<String, Double> v) {
        JsonObject o = new JsonObject();
        for (PerfSetting s : settings) {
            if (s.server() != server) continue;
            boolean his = s.owner().isEmpty() || s.owner().equals(giant);
            if (!his) continue;
            double x = s.clamp(v.getOrDefault(s.id(), s.def()));
            if (s.kind() == Kind.TOGGLE) o.addProperty(s.plainId(), x >= 0.5);
            else o.addProperty(s.plainId(), x);
        }
        return o;
    }

    /** the whole "perfset" argument */
    public static String setArg(String giant, boolean server, List<PerfSetting> settings, Map<String, Double> v) {
        JsonObject o = new JsonObject();
        o.addProperty("perf", PerfJson.VERSION);
        o.addProperty("side", server ? "server" : "client");
        o.add("values", valuesFor(giant, server, settings, v));
        return o.toString();
    }

    /** sends one side's values to every giant that speaks the protocol; how many took them */
    public static int push(
            @Nullable MinecraftServer server,
            List<PerfJson.Report> reports,
            List<Giant> giants,
            boolean serverSide,
            List<PerfSetting> settings,
            Map<String, Double> values) {
        int n = 0;
        for (PerfJson.Report r : reports) {
            Giant g = giants.stream().filter(x -> x.key().equals(r.giant())).findFirst().orElse(null);
            if (g == null) continue;
            if (call(server, g, SET, setArg(g.key(), serverSide, settings, values)) != null) n++;
        }
        return n;
    }

    /** all settings: the guide's (with a render distance for each giant that answered) and each giant's own */
    public static List<PerfSetting> settings(List<PerfJson.Report> reports, List<Giant> giants) {
        List<PerfSetting> out = new ArrayList<>(PerfCatalog.common());
        for (Giant g : giants)
            if (reports.stream().anyMatch(r -> r.giant().equals(g.key())))
                out.add(PerfCatalog.renderDistance(g.key()));
        for (PerfJson.Report r : reports)
            for (PerfSetting s : r.settings())
                if (out.stream().noneMatch(o -> o.id().equals(s.id()))) out.add(s);
        return out;
    }
}
