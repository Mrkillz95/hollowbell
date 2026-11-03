package net.jj.giants.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jj.giants.GiantsGuideMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 6, ADMIN_BRIDGE.md §22) What the server tools keep with the world (data/jj_giants_tools.dat): the areas drawn
 * with the book, the preset schedules and the one running, the event running, the stats, the alerts, which giants
 * the pause froze, and what the profile and the modpack last put in. Kept as one JSON string, read leniently: anything
 * that can't be read starts empty.
 */
public final class ToolsData extends SavedData {

    /** an area drawn with the book: a circle in one dimension */
    public static final class Area {
        public String id = "", name = "", kind = Areas.KEEP_OUT, dim = "minecraft:overworld", by = "";
        public int x, z, r = 64;
        public long time;
    }

    /** a preset that goes on by itself at certain times, and back off after */
    public static final class Schedule {
        public String id = "", name = "", preset = "", kind = Schedules.DAYS, date = "", by = "";
        /** days of the week, 1 Monday .. 7 Sunday */
        public List<Integer> days = new ArrayList<>();
        public boolean on = true;
    }

    /** one setting a schedule or an event changed: to put it back when it ends (now = what it set) */
    public static final class Put {
        public String giant = "", id = "";
        public @Nullable String old, now;
    }

    /** what is running for a while: an event, or a schedule's preset */
    public static final class Running {
        public String id = "", name = "", by = "";
        /** ticks left (an event; counted while the server runs) */
        public long left;
        public long started;
        public List<Put> puts = new ArrayList<>();
    }

    /** one kind of giant's numbers */
    public static final class Stat {
        public int deaths, slain, killed;
        public double damage;
        public long blocks;
    }

    public static final class Alert {
        public long time;
        public String kind = "", text = "", giant = "", id = "", dim = "";
        public int x, z;
    }

    /** everything kept (one object, so Gson never sees the saved data's own fields) */
    public static final class State {
        public List<Area> areas = new ArrayList<>();
        public List<Schedule> schedules = new ArrayList<>();
        public @Nullable Running schedule, event;
        public Map<String, Stat> stats = new LinkedHashMap<>();
        public long statsSince;
        public List<Alert> alerts = new ArrayList<>();
        /** giant ids the pause froze that haven't been seen standing since it ended */
        public Set<String> frozen = new LinkedHashSet<>();
        public boolean pausedNow;
        public String profileHash = "", packHash = "";
        public long profileAt, packAt;
        public int nextId = 1;

        /** (Gson leaves out what the file doesn't have) */
        void fill() {
            if (areas == null) areas = new ArrayList<>();
            if (schedules == null) schedules = new ArrayList<>();
            if (stats == null) stats = new LinkedHashMap<>();
            if (alerts == null) alerts = new ArrayList<>();
            if (frozen == null) frozen = new LinkedHashSet<>();
            if (profileHash == null) profileHash = "";
            if (packHash == null) packHash = "";
            areas.removeIf(a -> a == null || a.id == null);
            schedules.removeIf(x -> x == null || x.id == null);
            for (Schedule x : schedules) if (x.days == null) x.days = new ArrayList<>();
            for (Running r : new Running[] {schedule, event}) if (r != null && r.puts == null) r.puts = new ArrayList<>();
        }
    }

    public State s = new State();

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Factory<ToolsData> FACTORY = new Factory<>(ToolsData::new, ToolsData::read, null);
    /** most alerts kept */
    public static final int MOST_ALERTS = 50;

    public static ToolsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "jj_giants_tools");
    }

    static ToolsData read(CompoundTag tag, HolderLookup.Provider p) {
        ToolsData d = new ToolsData();
        try {
            State st = GSON.fromJson(tag.getString("Json"), State.class);
            if (st != null) d.s = st;
        } catch (RuntimeException e) {
            GiantsGuideMod.LOG.warn("Giants server tools: couldn't read this world's tools data, starting empty", e);
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

    public String newId(String prefix) {
        setDirty();
        return prefix + (s.nextId++);
    }

    public Stat stat(String kind) {
        return s.stats.computeIfAbsent(kind, k -> new Stat());
    }

    public void alert(Alert a) {
        s.alerts.add(a);
        while (s.alerts.size() > MOST_ALERTS) s.alerts.remove(0);
        setDirty();
    }
}
