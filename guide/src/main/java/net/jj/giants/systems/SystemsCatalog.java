package net.jj.giants.systems;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.admin.AdminPresets;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.admin.AdminSetting.Type;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 8, ADMIN_BRIDGE.md §24.2; Part 9, §25.2) The new systems' settings: all-giants settings of the category "systems", each off
 * (or "as always") in the jar. Unlike the multipliers they have a server default, kept in
 * config/jj_giants_admin/systems.json (only what differs from the jar), under the world's own value.
 */
public final class SystemsCatalog {
    private SystemsCatalog() {}

    public static final String CAT = "systems";

    private static AdminSetting flag(String id, boolean on, String name, String what) {
        JsonPrimitive v = new JsonPrimitive(on);
        return new AdminSetting("all", id, CAT, Type.BOOL, 0, 1, 1, List.of(), v, v, v, false, AdminSetting.LIVE,
                name, what, "admin.jj_giants." + id, "");
    }

    private static AdminSetting num(String id, Type t, double def, double min, double max, double step, String name,
            String what) {
        JsonPrimitive v = t == Type.INT ? new JsonPrimitive((long) def) : new JsonPrimitive(def);
        return new AdminSetting("all", id, CAT, t, min, max, step, List.of(), v, v, v, false, AdminSetting.LIVE, name,
                what, "admin.jj_giants." + id, "");
    }

    private static AdminSetting choice(String id, List<String> options, String name, String what) {
        JsonPrimitive v = new JsonPrimitive(options.get(0));
        return new AdminSetting("all", id, CAT, Type.CHOICE, 0, options.size() - 1, 1, options, v, v, v, false,
                AdminSetting.LIVE, name, what, "admin.jj_giants." + id, "");
    }

    /** every system's settings, in the book's order (the group each starts is in {@link #GROUPS}) */
    static final List<AdminSetting> SETTINGS = List.of(
            flag("all.herd", false, "Herds",
                    "Giants of the same kind keep together: the rest follow the world's own one (or the biggest) and"
                            + " walk back to him when they stray. Needs How many come above 1 (the Herds preset sets"
                            + " it)."),
            num("all.herdRange", Type.INT, 48, 16, 256, 8, "Herd spread",
                    "How far one may stray before he walks back to the leader, in blocks (never less than the"
                            + " leader's own reach)."),
            flag("all.growth", false, "Growth", "Giants grow with each day they survive and each fight they win."),
            num("all.growthPerDay", Type.FLOAT, 0.05, 0, 0.5, 0.01, "Growth each day",
                    "His starting size times this, added for every game day he has lived."),
            num("all.growthPerWin", Type.FLOAT, 0.15, 0, 1, 0.05, "Growth each win",
                    "His starting size times this, added for every giant or player he brings down."),
            num("all.growthMost", Type.FLOAT, 2, 1, 8, 0.25, "Biggest growth",
                    "He never grows past his starting size times this (nor past his own biggest size)."),
            num("all.growthTitan", Type.INT, 0, 0, 100, 1, "Titan after",
                    "Days left alone (no fight) before he becomes a Titan: his biggest growth at once, with twice the"
                            + " health and damage. 0 = never."),
            flag("all.hunger", false, "Hunger",
                    "Giants must eat: creatures they kill, and food players throw to them. A hungry giant weakens and"
                            + " goes hunting; a full one is calmer."),
            num("all.hungerDays", Type.FLOAT, 3, 0.5, 20, 0.5, "Days to go hungry",
                    "Full to empty in this many game days (half as fast while he sleeps)."),
            num("all.hungerWeak", Type.FLOAT, 0.6, 0.2, 1, 0.05, "Starving strength",
                    "His damage and speed when he is empty, times this (it eases in below a quarter full)."),
            flag("all.loyalty", false, "Loyalty",
                    "How his owner treats him changes his mood: kept near, fed and spared he is devoted (hits harder"
                            + " beside them); hit or left alone he sulks and goes hunting."),
            flag("all.loyaltyTurns", true, "Can turn on you",
                    "With Loyalty on: at no loyalty left he turns on his owner: he stops listening to their book and"
                            + " hunts them for a day."),
            choice("all.griefHeal", List.of("off", "slow", "normal", "fast", "instant"), "Grief healing",
                    "Blocks the giants broke grow back: slow (10 minutes), normal (3 minutes), fast (30 seconds),"
                            + " instant (2 seconds)."),
            choice("all.deathRule", List.of("drops", "corpse", "skeleton"), "When a giant falls",
                    "drops: his loot, as always. corpse: also his body stays and rots away. skeleton: also his bones"
                            + " stay where he fell, a landmark you can mine."),
            num("all.corpseDays", Type.FLOAT, 3, 0.5, 30, 0.5, "Corpse lasts",
                    "Game days his body takes to rot away."),
            choice("all.playerDeath", List.of("normal", "keep", "swallow"), "Dying near a giant",
                    "A player a giant (or anything of his) kills, or who dies close to a giant who is fighting:"
                            + " normal (drops as always), keep (keeps their things and levels), swallow (their things"
                            + " go into him and drop when he falls)."),
            num("all.scalePlayers", Type.FLOAT, 0, 0, 1, 0.05, "Stronger with more players",
                    "Every giant's health and damage +this for each player online past the first."),
            num("all.scaleDays", Type.FLOAT, 0, 0, 1, 0.05, "Stronger with time",
                    "+this for every 10 days the world has run."),
            num("all.scaleArmour", Type.FLOAT, 0, 0, 2, 0.05, "Stronger against armour",
                    "+this times how good the best armour worn on the server is (none 0, full netherite 1)."),
            num("all.scaleMost", Type.FLOAT, 3, 1, 10, 0.25, "Most scaling",
                    "The scaling never makes health or damage more than this times."),
            flag("all.weakPoints", false, "Weak points",
                    "Each giant has weak points on his body (his eyes, a soft spot...). Hits there do more; a glint"
                            + " shows where while he fights."),
            num("all.weakHit", Type.FLOAT, 2.5, 1, 5, 0.25, "Weak point damage", "A hit on a weak point times this."),
            num("all.bodyHit", Type.FLOAT, 1, 0.1, 1, 0.05, "Damage elsewhere",
                    "The share of a hit that lands anywhere else (1 = all of it)."),
            choice("all.fear", List.of("off", "light", "strong"), "Fear",
                    "Players near a wild giant on the hunt: light (slowness), strong (slowness and darkness)."),
            flag("all.awe", false, "Awe", "Players beside a giant they own get strength and resistance."),
            num("all.moonFrenzy", Type.FLOAT, 1, 1, 3, 0.25, "Full moon frenzy",
                    "On full-moon nights every giant hits this much harder and moves faster (half as much). 1 = off."),
            flag("all.seasons", false, "Seasons",
                    "Spring (giants heal faster), summer (hit harder, move faster), autumn (as usual), winter"
                            + " (slower, tougher hide). Everyone is told when the season turns."),
            num("all.seasonDays", Type.INT, 7, 1, 60, 1, "Season length", "Game days each season lasts."),
            // (Part 9, ADMIN_BRIDGE.md 25.2) new systems B
            choice("all.villagers", List.of("off", "flee", "hide", "worship"), "Villagers",
                    "off: as always. flee: they run from a wild or hunting one. hide: they shut themselves in, as for"
                            + " a raid. worship: they ring a calm one, look up at him and leave him bread each day (from"
                            + " a hunting one they hide)."),
            flag("all.golems", false, "Golems fight wild giants",
                    "Iron golems go for a wild giant who hunts or fights near them, or who walks into their"
                            + " village."),
            flag("all.followers", false, "Followers",
                    "Wild giants gather a band of creatures that keep near them and go for whatever they fight: each"
                            + " giant his own kind (magma cubes, husks, wither skeletons, phantoms, slimes,"
                            + " drowned, vindicators, vexes)."),
            num("all.followerCount", Type.INT, 6, 1, 32, 1, "Followers each",
                    "How many creatures follow each wild giant, at most (up to an army)."),
            num("all.followerEvery", Type.INT, 60, 10, 600, 10, "A new follower every",
                    "Seconds between new followers joining him while he has fewer than he may (only with a player"
                            + " near)."),
            flag("all.weather", false, "Weather Gods",
                    "Each giant brings his own weather to the land round him: ash, dust, sleet and embers, storms,"
                            + " mist, smog or the gloaming. Only players in his area see it; the bigger he is, the"
                            + " bigger his area."),
            num("all.weatherArea", Type.FLOAT, 1, 0.5, 3, 0.25, "Weather area",
                    "How far his weather reaches, times this."),
            num("all.weatherStrength", Type.FLOAT, 1, 0.25, 1, 0.05, "Weather strength",
                    "How thick his weather is: fog, rain and what falls."),
            choice("all.underworld", List.of("off", "nether", "end", "both"), "Underworld",
                    "Giants live in the Nether and the End instead of the Overworld: none come in the Overworld (a"
                            + " wild world's own one there goes below), and one of each comes near players in the"
                            + " Nether, the End or both, sized to fit."),
            flag("all.sacred", false, "Sacred ground",
                    "Nobody may build or break blocks in a giant's ground while he lives (players in creative"
                            + " mode still may)."),
            flag("all.mirror", false, "Mirror",
                    "Giants learn how players fight them: the tactic players lean on (melee, arrows, magic or"
                            + " blasts) hurts each kind less and less. Pair it with Stronger against armour."),
            num("all.mirrorMost", Type.FLOAT, 0.5, 0.1, 0.9, 0.05, "Most they learn",
                    "The most a favourite tactic is cut by (0.5: it does half)."),
            flag("all.chaos", false, "Random chaos",
                    "Every game day each giant's size, mood and moves are shuffled: a new size, a new mood (wild"
                            + " ones), and only some of his moves that day."),
            num("all.chaosDays", Type.INT, 1, 1, 10, 1, "Shuffle every",
                    "Game days between shuffles."));

    /** the book's groups: a name key and the settings in it */
    public static final String[][] GROUPS = {
        {"herds", "all.herd", "all.herdRange"},
        {"growth", "all.growth", "all.growthPerDay", "all.growthPerWin", "all.growthMost", "all.growthTitan"},
        {"hunger", "all.hunger", "all.hungerDays", "all.hungerWeak"},
        {"loyalty", "all.loyalty", "all.loyaltyTurns"},
        {"grief", "all.griefHeal"},
        {"death", "all.deathRule", "all.corpseDays"},
        {"playerdeath", "all.playerDeath"},
        {"scaling", "all.scalePlayers", "all.scaleDays", "all.scaleArmour", "all.scaleMost"},
        {"weak", "all.weakPoints", "all.weakHit", "all.bodyHit"},
        {"fear", "all.fear", "all.awe"},
        {"seasons", "all.moonFrenzy", "all.seasons", "all.seasonDays"},
        // (Part 9) new systems B
        {"villages", "all.villagers", "all.golems"},
        {"followers", "all.followers", "all.followerCount", "all.followerEvery"},
        {"weather", "all.weather", "all.weatherArea", "all.weatherStrength"},
        {"underworld", "all.underworld"},
        {"sacred", "all.sacred"},
        {"mirror", "all.mirror", "all.mirrorMost"},
        {"chaos", "all.chaos", "all.chaosDays"}};

    /** (Part 9) the first group of new systems B (the book starts them under their own heading) */
    public static final String FIRST_B = "villages";

    /** the settings, each with the server default from the file in place of the jar's */
    public static List<AdminSetting> settings() {
        Map<String, JsonElement> d = defaults();
        if (d.isEmpty()) return SETTINGS;
        List<AdminSetting> out = new ArrayList<>();
        for (AdminSetting s : SETTINGS) {
            JsonElement v = d.get(s.id());
            out.add(v == null || !s.accepts(v) ? s : new AdminSetting(s.giant(), s.id(), s.cat(), s.type(), s.min(),
                    s.max(), s.step(), s.options(), v, v, s.jar(), false, s.when(), s.name(), s.what(), s.key(),
                    s.group()));
        }
        return out;
    }

    public static boolean has(String id) {
        for (AdminSetting s : SETTINGS) if (s.id().equals(id)) return true;
        return false;
    }

    // the server defaults (config/jj_giants_admin/systems.json)

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static @Nullable Map<String, JsonElement> cache;
    private static long cacheStamp = -1;

    private static Path file() {
        return AdminPresets.dir().resolve("systems.json");
    }

    /** what the server's file sets (read again when the file changes) */
    public static synchronized Map<String, JsonElement> defaults() {
        Path f = file();
        long stamp;
        try {
            stamp = Files.exists(f) ? Files.getLastModifiedTime(f).toMillis() ^ Files.size(f) : 0;
        } catch (IOException e) {
            stamp = 0;
        }
        if (cache != null && stamp == cacheStamp) return cache;
        Map<String, JsonElement> m = new LinkedHashMap<>();
        if (stamp != 0)
            try {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var e : o.entrySet()) if (has(e.getKey())) m.put(e.getKey(), e.getValue());
            } catch (IOException | RuntimeException e) {
                GiantsGuideMod.LOG.warn("Giants admin: {} can't be read, so the systems use their own defaults: {}",
                        f, e.toString());
            }
        cache = m;
        cacheStamp = stamp;
        return m;
    }

    /** a system's server default to this value (the jar's: taken out of the file); null when written, else why not */
    public static synchronized @Nullable String setDefault(AdminSetting s, JsonElement v) {
        Map<String, JsonElement> m = new LinkedHashMap<>(defaults());
        if (AdminSetting.same(v, s.jar())) m.remove(s.id());
        else m.put(s.id(), v);
        JsonObject o = new JsonObject();
        for (var e : m.entrySet()) o.add(e.getKey(), e.getValue());
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "The server's systems file couldn't be written: " + e.getMessage();
        }
        cache = null;
        return null;
    }
}
