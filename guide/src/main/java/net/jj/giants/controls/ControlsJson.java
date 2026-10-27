package net.jj.giants.controls;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.jj.giants.Giant;
import net.jj.giants.GiantsGuideMod;
import org.jetbrains.annotations.Nullable;

/**
 * A giant's giants_controls.json (CONTROLS_BRIDGE.md): shipped in his jar at
 * {@code assets/<his mod id>/giants_controls.json}, it tells the Controls tab what his keys do in plain words, any
 * extra Controls categories of his, and his gestures (things done without a key, like crouching three times to sit
 * on him) with the client setting that turns each on or off. Everything in it is checked; what can't be used is
 * left out, and a file that can't be read at all just means "nothing extra".
 */
public final class ControlsJson {
    private ControlsJson() {}

    /** the file's format version this guide reads; a newer file is skipped */
    public static final int VERSION = 1;

    public static final String FILE = "giants_controls.json";
    public static final int MOST_GESTURES = 16, MOST_KEYS = 128, MOST_CATEGORIES = 8;
    public static final int LONGEST_NAME = 48, LONGEST_WHAT = 240;

    private static final Pattern ID = Pattern.compile("[a-z0-9_.\\-]{1,64}");
    private static final Pattern CONFIG = Pattern.compile("[A-Za-z0-9_.\\-]{1,64}");
    private static final Pattern LANG_KEY = Pattern.compile("[A-Za-z0-9_.\\-]{1,128}");

    /**
     * One gesture.
     *
     * @param id     its own id, e.g. "triple_crouch_sit"
     * @param name   its name on the toggle (plain words, or a language key)
     * @param what   one line on what it does (plain words, or a language key)
     * @param config the client setting that turns it on or off, read and set through his GiantsBridge
     * @param key    the key mapping it stands next to (its language key), or "" for a row of its own
     * @param def    on or off to start with
     */
    public record Gesture(String id, String name, String what, String config, String key, boolean def) {}

    /** what one mod's file says */
    public record Controls(
            String owner, Map<String, String> keys, List<String> categories, List<Gesture> gestures) {
        public static Controls empty(String owner) {
            return new Controls(owner, Map.of(), List.of(), List.of());
        }

        /** the gesture that stands next to this key, if any */
        public @Nullable Gesture gestureFor(String keyName) {
            for (Gesture g : gestures) if (g.key().equals(keyName)) return g;
            return null;
        }
    }

    /** reads a file's text; null if it isn't one this guide can use (not JSON, or a newer version) */
    public static @Nullable Controls read(String owner, @Nullable String text) {
        if (text == null) return null;
        JsonObject o;
        try {
            JsonElement e = JsonParser.parseString(text);
            if (!e.isJsonObject()) return null;
            o = e.getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
        int v = 1;
        if (o.has("controls")) {
            try {
                v = o.get("controls").getAsInt();
            } catch (RuntimeException e) {
                return null;
            }
        }
        if (v < 1 || v > VERSION) return null;

        Map<String, String> keys = new LinkedHashMap<>();
        if (o.get("keys") instanceof JsonObject ko)
            for (Map.Entry<String, JsonElement> e : ko.entrySet()) {
                if (keys.size() >= MOST_KEYS) break;
                String what = string(e.getValue(), LONGEST_WHAT);
                if (LANG_KEY.matcher(e.getKey()).matches() && !what.isEmpty()) keys.put(e.getKey(), what);
            }

        List<String> cats = new ArrayList<>();
        if (o.get("categories") instanceof JsonArray ca)
            for (JsonElement e : ca) {
                if (cats.size() >= MOST_CATEGORIES) break;
                String c = string(e, 128);
                if (LANG_KEY.matcher(c).matches() && !cats.contains(c)) cats.add(c);
            }

        List<Gesture> gestures = new ArrayList<>();
        if (o.get("gestures") instanceof JsonArray ga)
            for (JsonElement e : ga) {
                if (gestures.size() >= MOST_GESTURES) break;
                Gesture g = gesture(e);
                if (g != null && gestures.stream().noneMatch(x -> x.id().equals(g.id()))) gestures.add(g);
            }
        return new Controls(owner, Map.copyOf(keys), List.copyOf(cats), List.copyOf(gestures));
    }

    private static @Nullable Gesture gesture(JsonElement e) {
        if (!(e instanceof JsonObject g)) return null;
        String id = string(g.get("id"), 64);
        String config = string(g.get("config"), 64);
        String name = string(g.get("name"), LONGEST_NAME);
        if (!ID.matcher(id).matches() || !CONFIG.matcher(config).matches() || name.isEmpty()) return null;
        String what = string(g.has("description") ? g.get("description") : g.get("what"), LONGEST_WHAT);
        String key = string(g.get("key"), 128);
        if (!key.isEmpty() && !LANG_KEY.matcher(key).matches()) key = "";
        boolean def = true;
        if (g.get("default") instanceof JsonElement d && d.isJsonPrimitive() && d.getAsJsonPrimitive().isBoolean())
            def = d.getAsBoolean();
        return new Gesture(id, name, what, config, key, def);
    }

    /** a JSON string, trimmed and cut to a length; "" for anything else */
    private static String string(@Nullable JsonElement e, int most) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) return "";
        String s = e.getAsString().strip();
        return s.length() > most ? s.substring(0, most) : s;
    }

    /** the file in one mod's jar, read; null when the mod or the file isn't there or can't be used */
    public static @Nullable Controls forMod(String owner, String modId) {
        try {
            Optional<ModContainer> c = FabricLoader.getInstance().getModContainer(modId);
            if (c.isEmpty()) return null;
            Optional<Path> p = c.get().findPath("assets/" + modId + "/" + FILE);
            if (p.isEmpty() || !Files.isRegularFile(p.get())) return null;
            return read(owner, Files.readString(p.get(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            GiantsGuideMod.LOG.warn("Couldn't read {} from {}: {}", FILE, modId, e.toString());
            return null;
        }
    }

    /** the guide's own file and each giant's (only those that have one), by owner */
    public static Map<String, Controls> all(List<Giant> giants) {
        Map<String, Controls> out = new LinkedHashMap<>();
        Controls mine = forMod(ControlsCatalog.GUIDE, GiantsGuideMod.ID);
        if (mine != null) out.put(ControlsCatalog.GUIDE, mine);
        for (Giant g : giants) {
            Controls c = forMod(g.key(), g.modId());
            if (c != null) out.put(g.key(), c);
        }
        return out;
    }

    /** the extra categories every file names, category to owner (for {@link ControlsCatalog#owner}) */
    public static Map<String, String> extraCategories(Map<String, Controls> files) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Controls c : files.values()) for (String cat : c.categories()) out.putIfAbsent(cat, c.owner());
        return out;
    }
}
