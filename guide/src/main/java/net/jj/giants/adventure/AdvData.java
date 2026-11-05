package net.jj.giants.adventure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.jj.giants.GiantsGuideMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 11, ADMIN_BRIDGE.md §28) What the adventure tools keep with the world (data/jj_giants_adventure.dat): paths,
 * areas, scripts, which giant runs which script (and how far he got), the command hooks, and the saved adventure maps
 * (their blocks are in files of their own: AdvMaps). One JSON string, read leniently.
 */
public final class AdvData extends SavedData {

    /** a path: points one after another in one dimension */
    public static final class Path {
        public String dim = "minecraft:overworld";
        public List<double[]> pts = new ArrayList<>();
    }

    /** an area: a box of blocks (both corners in it) in one dimension */
    public static final class Area {
        public String dim = "minecraft:overworld";
        public int x1, y1, z1, x2, y2, z2;

        public boolean has(String d, double x, double y, double z) {
            return dim.equals(d) && x >= Math.min(x1, x2) && x < Math.max(x1, x2) + 1 && z >= Math.min(z1, z2)
                    && z < Math.max(z1, z2) + 1 && y >= Math.min(y1, y2) - 2 && y < Math.max(y1, y2) + 3;
        }

        public int sx() {
            return Math.abs(x2 - x1) + 1;
        }

        public int sy() {
            return Math.abs(y2 - y1) + 1;
        }

        public int sz() {
            return Math.abs(z2 - z1) + 1;
        }
    }

    /** a script: its steps, one line each */
    public static final class Script {
        public List<String> steps = new ArrayList<>();
    }

    /** a giant running a script: which, the step he is on, who gave it */
    public static final class Run {
        public String script = "", kind = "", by = "";
        public int step;
        public boolean done;
    }

    /** a command hook: when a giant this target matches has this event, the command runs */
    public static final class Hook {
        public int id;
        public String target = "", event = "", command = "", by = "";
        /** the permission level of whoever added it: the command runs with it */
        public int level = 2;
        public int fired;
        public long last;
    }

    /** a saved adventure map's description (its blocks are in jj_giants_adventures/<name>.nbt) */
    public static final class MapInfo {
        public String name = "", by = "", goal = "", dim = "";
        public long time;
        public int sx, sy, sz, giants, blocks;
        /** time up: "lose" (the players didn't make it) or "win" (they held out) */
        public String timeUp = "lose";
        /** players win as soon as one of them reaches this area of the map ("" none) */
        public String winArea = "";
    }

    public static final class State {
        public Map<String, Path> paths = new LinkedHashMap<>();
        public Map<String, Area> areas = new LinkedHashMap<>();
        public Map<String, Script> scripts = new LinkedHashMap<>();
        public Map<String, Run> runs = new LinkedHashMap<>();
        public List<Hook> hooks = new ArrayList<>();
        public Map<String, MapInfo> maps = new LinkedHashMap<>();
        public boolean scores;
        public int nextHook = 1;

        void fill() {
            if (paths == null) paths = new LinkedHashMap<>();
            if (areas == null) areas = new LinkedHashMap<>();
            if (scripts == null) scripts = new LinkedHashMap<>();
            if (runs == null) runs = new LinkedHashMap<>();
            if (hooks == null) hooks = new ArrayList<>();
            if (maps == null) maps = new LinkedHashMap<>();
            paths.values().removeIf(p -> p == null);
            for (Path p : paths.values()) if (p.pts == null) p.pts = new ArrayList<>();
            areas.values().removeIf(a -> a == null || a.dim == null);
            scripts.values().removeIf(s -> s == null);
            for (Script s : scripts.values()) if (s.steps == null) s.steps = new ArrayList<>();
            runs.values().removeIf(r -> r == null || r.script == null);
            hooks.removeIf(h -> h == null || h.event == null || h.command == null || h.target == null);
            maps.values().removeIf(m -> m == null || m.name == null);
            for (Hook h : hooks) nextHook = Math.max(nextHook, h.id + 1);
        }
    }

    public State s = new State();
    public static final int MOST_HOOKS = 200, MOST_NAMED = 100, MOST_POINTS = 64, MOST_STEPS = 40;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Factory<AdvData> FACTORY = new Factory<>(AdvData::new, AdvData::read, null);

    public static AdvData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "jj_giants_adventure");
    }

    static AdvData read(CompoundTag tag, HolderLookup.Provider p) {
        AdvData d = new AdvData();
        try {
            State st = GSON.fromJson(tag.getString("Json"), State.class);
            if (st != null) d.s = st;
        } catch (RuntimeException e) {
            GiantsGuideMod.LOG.warn("Giants adventure: couldn't read this world's adventure data, starting empty", e);
        }
        d.s.fill();
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) {
        tag.putString("Json", GSON.toJson(s));
        tag.putInt("Version", 1);
        return tag;
    }

    /** names: letters, digits, _ and -, 1 to 24, kept in lower case */
    public static @Nullable String name(String n) {
        if (n == null) return null;
        String k = n.strip().toLowerCase(Locale.ROOT);
        return k.matches("[a-z0-9_\\-]{1,24}") ? k : null;
    }

    public @Nullable Hook hook(int id) {
        for (Hook h : s.hooks) if (h.id == id) return h;
        return null;
    }

    static String json(Object o) {
        return GSON.toJson(o);
    }

    static <T> T copy(T o, Class<T> c) {
        return GSON.fromJson(GSON.toJson(o), c);
    }
}
