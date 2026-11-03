package net.jj.giants.fun;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jj.giants.GiantsGuideMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Part 7's world data (ADMIN_BRIDGE.md 23.3), saved with the world as data/jj_giants_fun.dat: the giant tags, what
 * admins gave each giant (a name, a title, a tag, loot), who is watching a giant (so they come back where they were),
 * and the arenas and dens built. Kept as one JSON text, so a field added later reads as its default in an older world.
 */
public final class FunWorld extends SavedData {
    private static final Gson GSON = new GsonBuilder().create();

    /** what is saved */
    public static final class State {
        public Map<String, Tag> tags = new LinkedHashMap<>();
        public Map<String, Mark> marks = new LinkedHashMap<>();
        public Map<String, Watch> watching = new LinkedHashMap<>();
        public List<Build> builds = new ArrayList<>();
        public int nextBuild = 1;
        /** the built-in tag was made once (deleting it keeps it deleted) */
        public boolean madeEventBoss;
    }

    /** a giant tag: his own stats and looks, given to any giant */
    public static final class Tag {
        public String name = "";
        public double health = 1, damage = 1, speed = 1, size = 0;
        public String title = "", colour = "white";
        public boolean glow;
        public @Nullable Loot loot;

        Tag copy() {
            return GSON.fromJson(GSON.toJson(this), Tag.class);
        }

        /** (the tests) a copy to change */
        public Tag copyForTests() {
            return copy();
        }
    }

    /** a loot an admin gave: drops these items and one roll of the table when he falls; replace: his own don't */
    public static final class Loot {
        public String mode = "add";
        /** the stacks, each as its saved text (SNBT) */
        public List<String> items = new ArrayList<>();
        public String table = "";

        public boolean empty() {
            return (items == null || items.isEmpty()) && (table == null || table.isEmpty());
        }
    }

    /** what admins gave one giant (by his id) */
    public static final class Mark {
        public String kind = "";
        public String name = "", title = "", colour = "white";
        public boolean shown;
        public String tag = "";
        public @Nullable Loot loot;
        /** his name before the Guide named him (a name tag's), put back when cleared */
        public String oldName = "";
        /** glowing came from his tag (taken off with it) */
        public boolean tagGlow;

        boolean empty() {
            return name.isEmpty() && title.isEmpty() && tag.isEmpty() && (loot == null || loot.empty())
                    && "white".equals(colour) && !shown;
        }
    }

    /** a player watching a giant: what to put back */
    public static final class Watch {
        public String mode = "free", kind = "", giant = "";
        public String gameMode = "survival", dim = "minecraft:overworld";
        public double x, y, z;
        public float yaw, pitch;
    }

    /** an arena or a den */
    public static final class Build {
        public int n;
        public String kind = "arena", style = "medium", dim = "minecraft:overworld", who = "";
        public int x, y, z, r;
        public long time;
        /** building, built, taking down, gone, stopped */
        public String state = "building";
        /** its blocks before, kept to take it down (false: too big to keep) */
        public boolean undo = true;
        public int done, total;
        /** a den's giant (his biome painted under it) */
        public String giant = "";
    }

    public State s = new State();

    private static final Factory<FunWorld> FACTORY = new Factory<>(FunWorld::new, FunWorld::read, null);

    public static FunWorld get(MinecraftServer server) {
        return get(server.overworld());
    }

    public static FunWorld get(ServerLevel overworld) {
        FunWorld w = overworld.getDataStorage().computeIfAbsent(FACTORY, "jj_giants_fun");
        if (!w.s.madeEventBoss) {
            w.s.madeEventBoss = true;
            Tag t = new Tag();
            t.name = "Event boss";
            t.health = 3;
            t.damage = 1.5;
            t.speed = 1.1;
            t.title = "Event Boss";
            t.colour = "gold";
            t.glow = true;
            w.s.tags.putIfAbsent(t.name, t);
            w.setDirty();
        }
        return w;
    }

    private static FunWorld read(CompoundTag tag, HolderLookup.Provider p) {
        FunWorld d = new FunWorld();
        try {
            State st = GSON.fromJson(tag.getString("Json"), State.class);
            if (st != null) d.s = fill(st);
        } catch (RuntimeException e) {
            GiantsGuideMod.LOG.warn("Couldn't read the fun tools' data (tags, names, builds): starting afresh", e);
        }
        return d;
    }

    /** (Gson leaves a field missing from older text as null) */
    private static State fill(State st) {
        if (st.tags == null) st.tags = new LinkedHashMap<>();
        if (st.marks == null) st.marks = new LinkedHashMap<>();
        if (st.watching == null) st.watching = new LinkedHashMap<>();
        if (st.builds == null) st.builds = new ArrayList<>();
        return st;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) {
        tag.putString("Json", GSON.toJson(s));
        tag.putInt("Version", 1);
        return tag;
    }

    /** one giant's marks, made if there are none */
    public Mark mark(String id, String kind) {
        Mark m = s.marks.computeIfAbsent(id, k -> new Mark());
        if (!kind.isEmpty()) m.kind = kind;
        setDirty();
        return m;
    }

    public @Nullable Mark markOf(String id) {
        return s.marks.get(id);
    }

    /** a giant's marks dropped when nothing is left in them */
    public void tidy(String id) {
        Mark m = s.marks.get(id);
        if (m != null && m.empty()) s.marks.remove(id);
        setDirty();
    }

    public @Nullable Build build(int n) {
        for (Build b : s.builds) if (b.n == n) return b;
        return null;
    }

    /** a tag by name, any case */
    public @Nullable Tag tag(String name) {
        for (Tag t : s.tags.values()) if (t.name.equalsIgnoreCase(name.trim())) return t;
        return null;
    }
}
