package net.jj.giants.perf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.jj.giants.perf.PerfSetting.Cost;
import net.jj.giants.perf.PerfSetting.Kind;
import org.jetbrains.annotations.Nullable;

/**
 * The bridge's words, both ways (PERFORMANCE_BRIDGE.md): a giant's list of settings, one JSON object a line, and the
 * values sent back to it, one JSON object. Everything from a giant's mod is checked here, and anything strange is
 * left out rather than trusted.
 */
public final class PerfJson {
    private PerfJson() {}

    /** the protocol version this guide speaks; a giant that answers with a newer one is left out */
    public static final int VERSION = 1;
    /** most settings one giant may report */
    public static final int MOST_SETTINGS = 64;

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_.\\-]{1,64}");

    /** what one giant's mod said: its protocol version, the guide settings it reads, and its own settings */
    public record Report(String giant, int version, List<String> uses, List<PerfSetting> settings) {}

    /**
     * A giant's answer to "perf": the header line first, then one line a setting. Null when it isn't a perf answer
     * this guide can read (an older mod's, a newer protocol's, or nonsense).
     */
    public static @Nullable Report read(String giant, @Nullable List<String> lines) {
        if (lines == null || lines.isEmpty()) return null;
        JsonObject head = object(lines.get(0));
        if (head == null || !head.has("perf")) return null;
        int v;
        try {
            v = head.get("perf").getAsInt();
        } catch (RuntimeException e) {
            return null;
        }
        if (v < 1 || v > VERSION) return null;
        List<String> uses = new ArrayList<>();
        if (head.get("uses") instanceof JsonArray a)
            for (JsonElement e : a)
                if (e.isJsonPrimitive() && ID.matcher(e.getAsString()).matches()) uses.add(e.getAsString());
        List<PerfSetting> out = new ArrayList<>();
        for (int i = 1; i < lines.size() && out.size() < MOST_SETTINGS; i++) {
            PerfSetting s = setting(giant, object(lines.get(i)));
            if (s != null && out.stream().noneMatch(o -> o.id().equals(s.id()))) out.add(s);
        }
        return new Report(giant, v, List.copyOf(uses), List.copyOf(out));
    }

    private static @Nullable JsonObject object(@Nullable String line) {
        if (line == null) return null;
        try {
            JsonElement e = JsonParser.parseString(line);
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** one setting line from a giant's mod, made safe; null if it can't be used */
    public static @Nullable PerfSetting setting(String giant, @Nullable JsonObject o) {
        if (o == null) return null;
        String id = str(o, "id", 64);
        Kind kind = Kind.of(str(o, "kind", 16));
        if (id.isEmpty() || kind == null) return null;
        if (!id.startsWith(giant + ".")) id = giant + "." + id;
        if (!ID.matcher(id).matches()) return null;
        String section = switch (str(o, "section", 16)) {
            case PerfSetting.EFFECTS -> PerfSetting.EFFECTS;
            case PerfSetting.DETAIL -> PerfSetting.DETAIL;
            case PerfSetting.FEATURES -> PerfSetting.FEATURES;
            default -> PerfSetting.GIANT;
        };
        boolean server = str(o, "side", 16).equals("server");
        List<String> options = new ArrayList<>();
        if (o.get("options") instanceof JsonArray a)
            for (JsonElement e : a) if (e.isJsonPrimitive() && options.size() < 8) options.add(cut(e.getAsString(), 24));
        double min, max, step;
        switch (kind) {
            case TOGGLE -> {
                min = 0;
                max = 1;
                step = 1;
            }
            case CHOICE -> {
                if (options.size() < 2) return null;
                min = 0;
                max = options.size() - 1;
                step = 1;
            }
            default -> {
                min = num(o, "min", 0);
                max = num(o, "max", 1);
                step = Math.max(0, num(o, "step", 0));
                if (!(min <= max)) return null;
            }
        }
        PerfSetting bare = new PerfSetting(
                id, section, kind, min, max, step, 0, null, Cost.LOW, server, "", "", "", options, "", giant, false);
        double def = bare.clamp(num(o, "default", kind == Kind.SLIDER ? max : kind == Kind.TOGGLE ? 1 : 0));
        double[] lv = new double[5];
        JsonArray given = o.get("levels") instanceof JsonArray a ? a : new JsonArray();
        for (int i = 0; i < 5; i++) {
            double v = def;
            if (i > 0 && given.size() > 0) {
                JsonElement e = given.get(Math.min(i, given.size() - 1));
                v = e.isJsonPrimitive() ? number(e.getAsJsonPrimitive(), def) : def;
            }
            lv[i] = bare.clamp(v);
        }
        // Highest changes nothing: its value is the default, whatever the giant said
        lv[0] = def;
        String name = cut(str(o, "name", 200), 48);
        return new PerfSetting(
                id,
                section,
                kind,
                min,
                max,
                step,
                def,
                lv,
                Cost.of(str(o, "cost", 8)),
                server,
                name.isEmpty() ? id : name,
                cut(str(o, "what", 400), 160),
                cut(str(o, "loses", 400), 160),
                List.copyOf(options),
                cut(str(o, "unit", 16), 12),
                giant,
                false);
    }

    private static String str(JsonObject o, String k, int most) {
        try {
            JsonElement e = o.get(k);
            if (e == null || !e.isJsonPrimitive()) return "";
            String s = e.getAsString().strip();
            return s.length() > most ? s.substring(0, most) : s;
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String cut(String s, int most) {
        s = s.replaceAll("[\\p{Cntrl}§]", "");
        return s.length() > most ? s.substring(0, most - 1) + "…" : s;
    }

    private static double num(JsonObject o, String k, double or) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? number(e.getAsJsonPrimitive(), or) : or;
    }

    private static double number(JsonPrimitive p, double or) {
        try {
            if (p.isBoolean()) return p.getAsBoolean() ? 1 : 0;
            double d = p.getAsDouble();
            return Double.isFinite(d) ? d : or;
        } catch (RuntimeException e) {
            return or;
        }
    }

    // a giant's report, written out again for the network (the server's settings, sent to each player)

    public static JsonObject write(PerfSetting s) {
        JsonObject o = new JsonObject();
        o.addProperty("id", s.id());
        o.addProperty("section", s.section());
        o.addProperty("kind", s.kind().word());
        o.addProperty("min", s.min());
        o.addProperty("max", s.max());
        o.addProperty("step", s.step());
        o.addProperty("default", s.def());
        JsonArray lv = new JsonArray();
        for (int i = 0; i < 5; i++) lv.add(s.level(i));
        o.add("levels", lv);
        o.addProperty("cost", s.cost().word());
        o.addProperty("side", s.server() ? "server" : "client");
        o.addProperty("ops", s.ops());
        o.addProperty("name", s.name());
        o.addProperty("what", s.what());
        o.addProperty("loses", s.loses());
        JsonArray op = new JsonArray();
        for (String x : s.options()) op.add(x);
        o.add("options", op);
        o.addProperty("unit", s.unit());
        o.addProperty("owner", s.owner());
        o.addProperty("perGiant", s.perGiant());
        return o;
    }

    /** settings as sent from the server to a player (already checked once, on the server) */
    public static String writeAll(Collection<PerfSetting> list) {
        JsonArray a = new JsonArray();
        for (PerfSetting s : list) a.add(write(s));
        return a.toString();
    }

    public static List<PerfSetting> readAll(String json) {
        List<PerfSetting> out = new ArrayList<>();
        try {
            for (JsonElement e : JsonParser.parseString(json).getAsJsonArray()) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                String owner = str(o, "owner", 64);
                boolean perGiant = o.has("perGiant") && o.get("perGiant").getAsBoolean();
                if (owner.isEmpty() || perGiant) {
                    // the guide's own: rebuilt from this guide's catalog (an older or newer server's are matched by id)
                    String id = str(o, "id", 80);
                    PerfSetting mine = perGiant
                            ? PerfCatalog.renderDistance(owner)
                            : PerfCatalog.common().stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
                    if (mine != null && mine.id().equals(id)) out.add(mine);
                    continue;
                }
                PerfSetting s = setting(owner, o);
                if (s != null) out.add(s);
            }
        } catch (RuntimeException e) {
            // a broken list: what was read so far
        }
        return out;
    }

    // values

    /** values as JSON: toggles as true/false, the rest as numbers (only the ids given) */
    public static JsonObject values(Map<String, Double> values, Collection<PerfSetting> settings) {
        JsonObject o = new JsonObject();
        for (PerfSetting s : settings) {
            Double v = values.get(s.id());
            if (v == null) continue;
            if (s.kind() == Kind.TOGGLE) o.addProperty(s.id(), v >= 0.5);
            else o.addProperty(s.id(), s.clamp(v));
        }
        return o;
    }

    /** values read back: numbers and true/false, unknown ids kept (they're dropped where settings are known) */
    public static Map<String, Double> values(@Nullable String json) {
        Map<String, Double> out = new LinkedHashMap<>();
        JsonObject o = object(json);
        if (o == null) return out;
        for (var e : o.entrySet()) {
            if (out.size() >= 1024) break;
            if (!ID.matcher(e.getKey()).matches() || !e.getValue().isJsonPrimitive()) continue;
            double d = number(e.getValue().getAsJsonPrimitive(), Double.NaN);
            if (Double.isFinite(d)) out.put(e.getKey(), d);
        }
        return out;
    }
}
