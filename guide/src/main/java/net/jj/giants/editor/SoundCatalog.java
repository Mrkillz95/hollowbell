package net.jj.giants.editor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.admin.AdminSetting.Type;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 12, ADMIN_BRIDGE.md §29) The sound and music picker: four all-giants settings for each giant (his fight music,
 * his ground's sounds on or off and how loud, his voice's pitch), kept like the systems' settings (category "systems":
 * a world value, the server default in config/jj_giants_admin/systems.json, scopes, the change log, presets, export
 * and share codes), shown on the book's Sound page. Every jar value is today's sound. Applied on each player's game by
 * the Guide (EditorClient): no giant mod changes.
 */
public final class SoundCatalog {
    private SoundCatalog() {}

    static final String CAT = "systems";

    /** each giant's sound files' own namespace */
    public static final Map<String, String> NAMESPACE = Map.of("pitchgut", "mountain_breathes", "furrowmaw",
            "furrowmaw", "cerberus", "fire_ice_cerberus", "hollowbell", "hollowbell", "lanternwillow",
            "lanternwillow", "wreckback", "wreckback", "swarmforge", "swarmforge", "gloamwick", "gloamwick");

    /** each giant's own fight tracks past the three every one has (fight, fight_dark, fight_last) */
    public static final Map<String, String> SPECIAL = Map.of("furrowmaw", "music.fight_under", "lanternwillow",
            "music.fight_drink", "wreckback", "music.fight_sea", "swarmforge", "music.fight_core", "gloamwick",
            "music.fight_eclipse", "hollowbell", "music.fight_sunk");

    /** his voice: the sounds his pitch setting changes (his own body's, not his moves' blows) */
    public static final Map<String, List<String>> VOICE = Map.of(
            "pitchgut", List.of("breath_in", "breath_out", "roar", "scream", "hurt", "death", "rage", "weak"),
            "furrowmaw", List.of("bellow", "roar", "shriek", "growl", "death", "death_groan", "hurt", "gag", "sniff",
                    "inhale"),
            "cerberus", List.of("growl", "pant", "howl", "roar", "bellow", "hurt", "death", "throat"),
            "lanternwillow", List.of("groan", "growl", "hurt", "hurt_heavy", "death", "tired", "inhale"),
            "wreckback", List.of("groan", "roar", "hurt", "hurt_heavy", "death", "tired"),
            "swarmforge", List.of("hurt", "death", "tired", "horn", "alarm", "wave_alarm", "power_down", "power_up"),
            "gloamwick", List.of("bellow", "groan", "hurt", "death", "breath", "whisper", "wail", "toll"),
            "hollowbell", List.of("hum", "hurt", "hurt_heavy", "death", "tired", "toll", "toll_big"));

    /** the music choice: his own, none, or another giant's tracks */
    public static final List<String> MUSIC;

    static {
        List<String> m = new ArrayList<>(List.of("own", "off"));
        m.addAll(AdminCatalog.KEYS);
        MUSIC = List.copyOf(m);
    }

    public static String music(String kind) {
        return "all.music." + kind;
    }

    public static String ambient(String kind) {
        return "all.ambient." + kind;
    }

    public static String ambientVolume(String kind) {
        return "all.ambientVolume." + kind;
    }

    public static String voicePitch(String kind) {
        return "all.voicePitch." + kind;
    }

    private static String nameOf(String kind) {
        return kind.equals("lanternwillow") ? "Lantern Willow"
                : Character.toUpperCase(kind.charAt(0)) + kind.substring(1);
    }

    private static AdminSetting row(String id, Type t, JsonPrimitive v, double min, double max, double step,
            List<String> options, String name, String what) {
        return new AdminSetting("all", id, CAT, t, min, max, step, options, v, v, v, false, AdminSetting.LIVE, name,
                what, "admin.jj_giants." + id, "");
    }

    public static final List<AdminSetting> SETTINGS;

    static {
        List<AdminSetting> l = new ArrayList<>();
        for (String k : AdminCatalog.KEYS) {
            String n = nameOf(k);
            l.add(row(music(k), Type.CHOICE, new JsonPrimitive("own"), 0, MUSIC.size() - 1, 1, MUSIC,
                    n + ": fight music",
                    "The music players hear when " + n + " fights them. own: his own tracks. off: none. A giant's"
                            + " name: that giant's tracks (each part of the fight to the same part of his)."));
            l.add(row(ambient(k), Type.BOOL, new JsonPrimitive(true), 0, 1, 1, List.of(), n + ": ground sounds",
                    "The sounds of " + n + "'s own ground and of him at rest (wind, hums, creaks). Off: silent."));
            l.add(row(ambientVolume(k), Type.FLOAT, new JsonPrimitive(1.0), 0, 2, 0.05, List.of(),
                    n + ": ground sounds' volume", "How loud those sounds are, times this (each player's own"
                            + " volume still counts)."));
            l.add(row(voicePitch(k), Type.FLOAT, new JsonPrimitive(1.0), 0.5, 2, 0.05, List.of(),
                    n + ": voice pitch", "His voice (roars, groans, hurt and death) higher or deeper: 2 is an octave"
                            + " up, 0.5 an octave down."));
        }
        SETTINGS = List.copyOf(l);
    }

    public static List<AdminSetting> after(List<AdminSetting> before) {
        List<AdminSetting> out = new ArrayList<>(before);
        out.addAll(SETTINGS);
        return List.copyOf(out);
    }

    public static boolean ours(String id) {
        for (AdminSetting s : SETTINGS) if (s.id().equals(id)) return true;
        return false;
    }

    /**
     * What every player's game is sent: only the giants whose sound differs from as shipped,
     * {"giants":{"wreckback":{"music":"gloamwick","ambient":false,"ambientVolume":0.5,"voicePitch":0.8}}}.
     */
    public static JsonObject sync(MinecraftServer server) {
        JsonObject o = new JsonObject(), gs = new JsonObject();
        for (String k : AdminCatalog.KEYS) {
            JsonObject g = new JsonObject();
            String m = net.jj.giants.systems.Systems.option(server, music(k));
            if (!m.equals("own") && MUSIC.contains(m)) g.addProperty("music", m);
            if (!net.jj.giants.systems.Systems.on(server, ambient(k))) g.addProperty("ambient", false);
            double v = net.jj.giants.systems.Systems.num(server, ambientVolume(k));
            if (v != 1) g.addProperty("ambientVolume", v);
            double p = net.jj.giants.systems.Systems.num(server, voicePitch(k));
            if (p != 1 && p > 0) g.addProperty("voicePitch", p);
            if (!g.isEmpty()) gs.add(k, g);
        }
        o.add("giants", gs);
        return o;
    }

    // what a sound is (the players' games, and the tests)

    /** which giant's sounds these are (by their namespace), or null */
    public static @Nullable String kindOf(String namespace) {
        for (var e : NAMESPACE.entrySet()) if (e.getValue().equals(namespace)) return e.getKey();
        return null;
    }

    /** one of his fight tracks */
    public static boolean fightMusic(String path) {
        return path.startsWith("music.fight");
    }

    /** his ground's sounds and his sounds at rest */
    public static boolean ambient(String kind, String path) {
        return path.startsWith("ambient") || kind.equals("cerberus") && (path.startsWith("mood.")
                || path.startsWith("additions."));
    }

    public static boolean voice(String kind, String path) {
        List<String> v = VOICE.get(kind);
        return v != null && v.contains(path);
    }

    /** his fight track's part in another giant's tracks: the same part (his own extra part: theirs, or their dark) */
    public static String trackFor(String fromKind, String path, String toKind) {
        if (path.equals("music.fight") || path.equals("music.fight_dark") || path.equals("music.fight_last"))
            return path;
        String s = SPECIAL.get(toKind);
        return s != null ? s : "music.fight_dark";
    }

    /** a value of the sync for one giant: (music, ambient, volume, pitch) */
    public static @Nullable JsonElement of(JsonObject sync, String kind, String field) {
        if (!(sync.get("giants") instanceof JsonObject g) || !(g.get(kind) instanceof JsonObject k)) return null;
        return k.get(field);
    }
}
