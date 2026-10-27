package net.jj.giants.perf;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/**
 * One player's performance settings (config/jj_giants_performance.json on their own game): the preset they picked,
 * their own values for the per-player settings, and the presets they saved. The world's Features are not kept here
 * (the server keeps those), but a saved preset remembers them too, for an operator to apply.
 */
public final class PerfProfile {
    /** what the preset button says: a built-in, one of theirs, or Custom once anything has been moved */
    private String preset = PerfPresets.BUILT_IN.get(PerfPresets.HIGHEST);
    /** the preset last picked, for "reset to preset" */
    private String base = preset;
    private final Map<String, Double> values = new LinkedHashMap<>();
    private final Map<String, Map<String, Double>> mine = new LinkedHashMap<>();
    /** an operator's choice: picking a preset sets the world's Features too */
    public boolean withFeatures;

    public static final int MOST_PRESETS = 32, LONGEST_NAME = 24;

    public String preset() {
        return preset;
    }

    public String base() {
        return base;
    }

    public boolean custom() {
        return preset.equals(PerfPresets.CUSTOM);
    }

    /** the names of the presets they saved, in the order saved */
    public List<String> mine() {
        return List.copyOf(mine.keySet());
    }

    /** every preset name to cycle through: the built-ins, then theirs */
    public List<String> all() {
        List<String> out = new ArrayList<>(PerfPresets.BUILT_IN);
        out.addAll(mine.keySet());
        return out;
    }

    public double get(PerfSetting s) {
        return s.clamp(values.getOrDefault(s.id(), s.def()));
    }

    /** the raw per-player values (ids not here are their default) */
    public Map<String, Double> values() {
        return values;
    }

    /** a setting moved by hand: the label goes to Custom */
    public void set(PerfSetting s, double v) {
        values.put(s.id(), s.clamp(v));
        preset = PerfPresets.CUSTOM;
    }

    /** something else was moved by hand (one of the world's settings): the label goes to Custom */
    public void markCustom() {
        preset = PerfPresets.CUSTOM;
    }

    /** every setting's value in a preset (built-in or theirs; null for a name that is neither) */
    public @Nullable Map<String, Double> presetValues(String name, List<PerfSetting> settings) {
        int b = PerfPresets.builtIn(name);
        if (b >= 0) return PerfPresets.values(b, settings);
        Map<String, Double> saved = mine.get(name);
        if (saved == null) return null;
        Map<String, Double> out = new LinkedHashMap<>();
        for (PerfSetting s : settings) out.put(s.id(), s.clamp(saved.getOrDefault(s.id(), s.def())));
        return out;
    }

    /**
     * Picks a preset: the per-player settings take its values. Returns all its values (the world's ones too, for an
     * operator to send on), or null for an unknown name.
     */
    public @Nullable Map<String, Double> apply(String name, List<PerfSetting> settings) {
        Map<String, Double> v = presetValues(name, settings);
        if (v == null) return null;
        for (PerfSetting s : settings) if (!s.server()) values.put(s.id(), v.get(s.id()));
        preset = base = PerfPresets.builtIn(name) >= 0 ? PerfPresets.BUILT_IN.get(PerfPresets.builtIn(name)) : name;
        return v;
    }

    /**
     * Puts the settings that match back to the last picked preset's values ("reset to preset" on one section).
     * Returns those values (world ones too) for the caller to send on. The label goes back to the preset if nothing
     * else is moved.
     */
    public Map<String, Double> reset(Predicate<PerfSetting> which, List<PerfSetting> settings) {
        Map<String, Double> v = presetValues(base, settings);
        if (v == null) {
            base = PerfPresets.BUILT_IN.get(PerfPresets.HIGHEST);
            v = PerfPresets.values(PerfPresets.HIGHEST, settings);
        }
        Map<String, Double> out = new LinkedHashMap<>();
        for (PerfSetting s : settings)
            if (which.test(s)) {
                out.put(s.id(), v.get(s.id()));
                if (!s.server()) values.put(s.id(), v.get(s.id()));
            }
        boolean same = true;
        for (PerfSetting s : settings)
            if (!s.server() && Math.abs(get(s) - v.get(s.id())) > 1e-9) same = false;
        if (same) preset = base;
        return out;
    }

    /** why a name can't be used for a preset, or null if it can */
    public @Nullable String badName(String name, @Nullable String keeping) {
        String n = name == null ? "" : name.strip();
        if (n.isEmpty()) return "Give it a name.";
        if (n.length() > LONGEST_NAME) return "That name is too long (" + LONGEST_NAME + " letters at most).";
        if (PerfPresets.builtIn(n) >= 0 || n.equalsIgnoreCase(PerfPresets.CUSTOM))
            return "\"" + n + "\" is a built-in preset's name.";
        for (String k : mine.keySet())
            if (k.equalsIgnoreCase(n) && !k.equals(keeping)) return "You already have a preset called " + k + ".";
        if (!mine.containsKey(n) && mine.size() >= MOST_PRESETS && keeping == null)
            return "That's " + MOST_PRESETS + " presets: delete one first.";
        return null;
    }

    /** saves these values (every setting's, the world's too) as a preset of theirs; null, or why not */
    public @Nullable String saveAs(String name, Map<String, Double> all) {
        String bad = badName(name, null);
        if (bad != null) return bad;
        String n = name.strip();
        mine.put(n, new LinkedHashMap<>(all));
        preset = base = n;
        return null;
    }

    public @Nullable String rename(String from, String to) {
        if (!mine.containsKey(from)) return "There's no preset of yours called " + from + ".";
        String bad = badName(to, from);
        if (bad != null) return bad;
        String n = to.strip();
        Map<String, Map<String, Double>> keep = new LinkedHashMap<>(mine);
        mine.clear();
        for (var e : keep.entrySet()) mine.put(e.getKey().equals(from) ? n : e.getKey(), e.getValue());
        if (preset.equals(from)) preset = n;
        if (base.equals(from)) base = n;
        return null;
    }

    public @Nullable String delete(String name) {
        if (mine.remove(name) == null) return "There's no preset of yours called " + name + ".";
        if (preset.equals(name)) preset = PerfPresets.CUSTOM;
        if (base.equals(name)) base = PerfPresets.BUILT_IN.get(PerfPresets.HIGHEST);
        return null;
    }

    // the file

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("version", 1);
        o.addProperty("preset", preset);
        o.addProperty("base", base);
        o.addProperty("withFeatures", withFeatures);
        o.add("values", map(values));
        JsonObject m = new JsonObject();
        for (var e : mine.entrySet()) m.add(e.getKey(), map(e.getValue()));
        o.add("presets", m);
        return o;
    }

    private static JsonObject map(Map<String, Double> v) {
        JsonObject o = new JsonObject();
        for (var e : v.entrySet()) o.addProperty(e.getKey(), e.getValue());
        return o;
    }

    public static PerfProfile fromJson(@Nullable JsonObject o) {
        PerfProfile p = new PerfProfile();
        if (o == null) return p;
        try {
            p.values.putAll(PerfJson.values(o.get("values") instanceof JsonObject v ? v.toString() : null));
            if (o.get("presets") instanceof JsonObject m)
                for (var e : m.entrySet()) {
                    String n = e.getKey().strip();
                    if (p.badName(n, null) == null && e.getValue().isJsonObject())
                        p.mine.put(n, new LinkedHashMap<>(PerfJson.values(e.getValue().toString())));
                }
            String was = str(o, "preset");
            p.preset = known(p, was, was.isEmpty() ? PerfPresets.BUILT_IN.get(0) : PerfPresets.CUSTOM);
            p.base = known(p, str(o, "base"), PerfPresets.BUILT_IN.get(0));
            if (p.base.equals(PerfPresets.CUSTOM)) p.base = PerfPresets.BUILT_IN.get(0);
            p.withFeatures = o.has("withFeatures") && o.get("withFeatures").getAsBoolean();
        } catch (RuntimeException e) {
            // a hand-edited file with something odd in: what was read so far
        }
        return p;
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    private static String known(PerfProfile p, String name, String or) {
        int b = PerfPresets.builtIn(name);
        if (b >= 0) return PerfPresets.BUILT_IN.get(b);
        if (p.mine.containsKey(name) || name.equals(PerfPresets.CUSTOM)) return name;
        return or;
    }

    public static PerfProfile load(Path file) {
        try {
            if (Files.exists(file)) {
                JsonElement e = JsonParser.parseString(Files.readString(file));
                return fromJson(e.isJsonObject() ? e.getAsJsonObject() : null);
            }
        } catch (Exception e) {
            net.jj.giants.GiantsGuideMod.LOG.warn("Could not read {}, starting from Highest", file, e);
        }
        return new PerfProfile();
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, new GsonBuilder().setPrettyPrinting().create().toJson(toJson()));
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            net.jj.giants.GiantsGuideMod.LOG.warn("Could not write {}", file, e);
        }
    }
}
