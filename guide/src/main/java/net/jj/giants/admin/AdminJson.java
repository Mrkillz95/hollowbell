package net.jj.giants.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.jj.giants.admin.AdminSetting.Type;
import org.jetbrains.annotations.Nullable;

/**
 * The admin bridge's words (ADMIN_BRIDGE.md): a giant's list of settings, one JSON object a line, and the values
 * sent back to it. Everything from a giant's mod is checked here; a line that can't be used is left out and the rest
 * still count.
 */
public final class AdminJson {
    private AdminJson() {}

    /** the protocol version this guide speaks; a giant that answers with a newer one is left out */
    public static final int VERSION = 1;
    /** most settings one giant may report */
    public static final int MOST_SETTINGS = 512;

    static final Pattern ID = Pattern.compile("[A-Za-z0-9_.\\-]{1,64}");

    /**
     * What one giant's mod said.
     *
     * @param giant    the guide's key for him
     * @param name     his plain name ("Wreckback")
     * @param uses     the all-giants multipliers his mod applies
     * @param all      the all-giants values he has now (as he was last sent them)
     * @param settings his own settings, in his order
     */
    public record Report(
            String giant,
            String name,
            int version,
            List<String> uses,
            Map<String, Double> all,
            List<AdminSetting> settings) {
        public @Nullable AdminSetting setting(String id) {
            for (AdminSetting s : settings) if (s.id().equals(id)) return s;
            return null;
        }
    }

    /** a giant's answer to "admin"; null when it isn't one this guide can read */
    public static @Nullable Report read(String giant, String fallbackName, @Nullable List<String> lines) {
        if (lines == null || lines.isEmpty()) return null;
        JsonObject head = object(lines.get(0));
        if (head == null || !head.has("admin")) return null;
        int v;
        try {
            v = head.get("admin").getAsInt();
        } catch (RuntimeException e) {
            return null;
        }
        if (v < 1 || v > VERSION) return null;
        String name = str(head, "name", 48);
        if (name.isEmpty()) name = fallbackName;
        List<String> uses = new ArrayList<>();
        if (head.get("uses") instanceof JsonArray a)
            for (JsonElement e : a)
                if (e.isJsonPrimitive() && ID.matcher(e.getAsString()).matches()) uses.add(e.getAsString());
        Map<String, Double> all = new LinkedHashMap<>();
        if (head.get("all") instanceof JsonObject o)
            for (var e : o.entrySet())
                try {
                    double d = e.getValue().getAsDouble();
                    if (Double.isFinite(d)) all.put(e.getKey(), d);
                } catch (RuntimeException bad) {
                    // left out
                }
        List<AdminSetting> out = new ArrayList<>();
        for (int i = 1; i < lines.size() && out.size() < MOST_SETTINGS; i++) {
            AdminSetting s = setting(giant, object(lines.get(i)));
            if (s != null && out.stream().noneMatch(o -> o.id().equals(s.id()))) out.add(s);
        }
        return new Report(giant, name, v, List.copyOf(uses), Map.copyOf(all), List.copyOf(out));
    }

    public static @Nullable JsonObject object(@Nullable String line) {
        if (line == null) return null;
        try {
            JsonElement e = JsonParser.parseString(line);
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** one setting line from a giant's mod, made safe; null if it can't be used */
    public static @Nullable AdminSetting setting(String giant, @Nullable JsonObject o) {
        return setting(giant, o, false);
    }

    /**
     * The same, also taking the all-giants ids when {@code shared} (the admin book's page of them, from the guide
     * itself: a giant's own list may never hold one).
     */
    public static @Nullable AdminSetting setting(String giant, @Nullable JsonObject o, boolean shared) {
        if (o == null) return null;
        String id = str(o, "id", 64);
        Type type = Type.of(str(o, "type", 16));
        if (!ID.matcher(id).matches() || type == null || id.startsWith("all.") != shared) return null;
        String cat = str(o, "cat", 16);
        // (coming back after he falls, as the Hearthstride's relighting rows say it, is spawning)
        if (cat.equals("respawn")) cat = "spawning";
        if (!AdminSetting.CATEGORIES.contains(cat)) cat = "behaviour";
        String when = str(o, "when", 16);
        if (!AdminSetting.WHENS.contains(when)) when = AdminSetting.LIVE;
        double min = num(o, "min", 0), max = num(o, "max", 1), step = num(o, "step", 0);
        List<String> options = new ArrayList<>();
        if (o.get("options") instanceof JsonArray a)
            for (JsonElement e : a)
                if (e.isJsonPrimitive() && options.size() < 32) {
                    String s = e.getAsString().trim();
                    if (!s.isEmpty() && s.length() <= 32 && !s.contains(" ")) options.add(s);
                }
        switch (type) {
            case BOOL -> {
                min = 0;
                max = 1;
                step = 1;
            }
            case CHOICE -> {
                if (options.size() < 2) return null;
            }
            default -> {
                if (!(max >= min) || !(step >= 0)) return null;
                if (type == Type.INT && step < 1) step = 1;
            }
        }
        String name = str(o, "name", 64);
        if (name.isEmpty()) name = id;
        AdminSetting probe = new AdminSetting(giant, id, cat, type, min, max, step, List.copyOf(options),
                JsonNull.INSTANCE, JsonNull.INSTANCE, JsonNull.INSTANCE, false, when, name, "", "", "");
        JsonElement def = fit(probe, o.get("default"));
        JsonElement value = fit(probe, o.get("value"));
        JsonElement jar = fit(probe, o.get("jar"));
        if (def == null) return null;
        if (value == null) value = def;
        if (jar == null) jar = def;
        boolean set = o.get("set") instanceof JsonPrimitive p && p.isBoolean() && p.getAsBoolean();
        // (Part 2C, 21.3) the book's section, when the giant names one; else it comes from the category
        String section = str(o, "section", 16);
        if (SECTIONS.contains(section)) SECTION_OF.put(giant + "/" + id, section);
        else SECTION_OF.remove(giant + "/" + id);
        return new AdminSetting(giant, id, cat, type, min, max, step, List.copyOf(options), def, value, jar, set,
                when, name, str(o, "what", 240), str(o, "key", 96), str(o, "group", 48));
    }

    /** (Part 2C, ADMIN_BRIDGE.md 21.3) the admin book's sections, in the order its tabs show them */
    public static final List<String> SECTIONS = List.of("stats", "moves", "spawning", "behaviour", "healing", "taming",
            "loot", "players", "effects", "worldgen", "misc");

    /** sections named by a giant's own line (giant/id), for the few that differ from their category */
    private static final java.util.Map<String, String> SECTION_OF = new java.util.concurrent.ConcurrentHashMap<>();

    /** the section a setting shows in: the one its giant named, else its category's (server load: misc) */
    public static String section(AdminSetting s) {
        String named = SECTION_OF.get(s.giant() + "/" + s.id());
        if (named != null) return named;
        return SECTIONS.contains(s.cat()) ? s.cat() : "misc";
    }

    /** a value as the giant reported it, in this setting's form; a number outside the range is kept (the file's) */
    private static @Nullable JsonElement fit(AdminSetting s, @Nullable JsonElement e) {
        if (e == null || e.isJsonNull()) return null;
        try {
            return switch (s.type()) {
                case BOOL -> e.getAsJsonPrimitive().isBoolean() ? e : null;
                case CHOICE -> s.options().contains(e.getAsString()) ? new JsonPrimitive(e.getAsString())
                        : e.getAsJsonPrimitive().isNumber() && e.getAsInt() >= 0 && e.getAsInt() < s.options().size()
                                ? new JsonPrimitive(s.options().get(e.getAsInt()))
                                : null;
                case RANGE -> e.isJsonArray() && e.getAsJsonArray().size() == 2 ? e : null;
                case INT -> new JsonPrimitive(Math.round(e.getAsDouble()));
                case FLOAT -> Double.isFinite(e.getAsDouble()) ? new JsonPrimitive(e.getAsDouble()) : null;
            };
        } catch (RuntimeException bad) {
            return null;
        }
    }

    public static String str(JsonObject o, String k, int most) {
        try {
            JsonElement e = o.get(k);
            if (e == null || !e.isJsonPrimitive()) return "";
            String s = e.getAsString().trim();
            return s.length() > most ? s.substring(0, most) : s;
        } catch (RuntimeException e) {
            return "";
        }
    }

    public static double num(JsonObject o, String k, double or) {
        try {
            double d = o.get(k).getAsDouble();
            return Double.isFinite(d) ? d : or;
        } catch (RuntimeException e) {
            return or;
        }
    }

    /** one setting as a line of the giant's list (the same words), for the admin book's pages */
    public static JsonObject write(AdminSetting s) {
        JsonObject o = new JsonObject();
        o.addProperty("id", s.id());
        o.addProperty("cat", s.cat());
        o.addProperty("type", s.type().word());
        if (s.type() != Type.BOOL) {
            o.addProperty("min", s.min());
            o.addProperty("max", s.max());
            o.addProperty("step", s.step());
        }
        if (s.type() == Type.CHOICE) {
            JsonArray a = new JsonArray();
            for (String x : s.options()) a.add(x);
            o.add("options", a);
        }
        o.add("default", s.def());
        o.add("value", s.value());
        o.add("jar", s.jar());
        o.addProperty("set", s.set());
        o.addProperty("when", s.when());
        o.addProperty("name", s.name());
        if (!s.what().isEmpty()) o.addProperty("what", s.what());
        if (!s.key().isEmpty()) o.addProperty("key", s.key());
        if (!s.group().isEmpty()) o.addProperty("group", s.group());
        String section = SECTION_OF.get(s.giant() + "/" + s.id());
        if (section != null) o.addProperty("section", section);
        return o;
    }

    /** the "adminset" argument: the world's values for one giant, and the all-giants ones */
    public static String setArg(Map<String, JsonElement> values, Map<String, JsonElement> all) {
        JsonObject o = new JsonObject();
        o.addProperty("admin", VERSION);
        JsonObject v = new JsonObject();
        for (var e : values.entrySet()) v.add(e.getKey(), e.getValue());
        o.add("values", v);
        JsonObject a = new JsonObject();
        for (var e : all.entrySet()) a.add(e.getKey(), e.getValue());
        o.add("all", a);
        return o.toString();
    }
}
