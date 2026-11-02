package net.jj.giants.admin;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The world's admin settings, saved with the world (data/jj_giants_admin.dat): for each giant (and "all"), the
 * settings this world has its own value for, and the recent changes. A setting not here is the server's default
 * (the giant's config file). A giant whose mod is taken out keeps his values here, for when it comes back.
 */
public final class AdminWorld extends SavedData {
    /** how many changes are kept */
    public static final int MOST_LOG = 100;

    /**
     * One change.
     *
     * @param time  when (milliseconds since 1970)
     * @param who   who made it (a player's name, or "Server")
     * @param giant the giant's key, or "all"
     * @param id    the setting's id ("*" for a whole reset, a preset or an import)
     * @param name  the setting's name then, in plain words
     * @param old   the value before, as text ("-" for the server's default)
     * @param now   the value after, as text ("-" for the server's default)
     * @param undo  what puts it back: {"giant":{"id":value or null (null = back to the server default)}}, as JSON
     *              ("" if nothing to undo). For the book's undo (Part 2).
     */
    public record Change(long time, String who, String giant, String id, String name, String old, String now,
            String undo) {
        /** the values before, by giant and id (null value: it was the server default) */
        public static String undo(Map<String, Map<String, JsonElement>> before) {
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            for (var g : before.entrySet()) {
                com.google.gson.JsonObject v = new com.google.gson.JsonObject();
                for (var e : g.getValue().entrySet())
                    v.add(e.getKey(), e.getValue() == null ? com.google.gson.JsonNull.INSTANCE : e.getValue());
                o.add(g.getKey(), v);
            }
            return o.toString();
        }

        /** the same for one setting */
        public static String undo(String giant, String id, @org.jetbrains.annotations.Nullable JsonElement before) {
            Map<String, JsonElement> one = new java.util.HashMap<>();
            one.put(id, before);
            return undo(Map.of(giant, one));
        }
    }

    private final Map<String, Map<String, JsonElement>> giants = new LinkedHashMap<>();
    private final List<Change> log = new ArrayList<>();

    private static final Factory<AdminWorld> FACTORY = new Factory<>(AdminWorld::new, AdminWorld::read, null);

    public static AdminWorld get(MinecraftServer server) {
        return get(server.overworld());
    }

    /** the same, from the overworld itself (while it is being loaded, before the server knows it) */
    public static AdminWorld get(net.minecraft.server.level.ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(FACTORY, "jj_giants_admin");
    }

    public static AdminWorld read(CompoundTag tag, HolderLookup.Provider p) {
        AdminWorld w = new AdminWorld();
        CompoundTag g = tag.getCompound("Giants");
        for (String giant : g.getAllKeys()) {
            CompoundTag v = g.getCompound(giant);
            Map<String, JsonElement> m = new LinkedHashMap<>();
            for (String id : v.getAllKeys()) {
                try {
                    m.put(id, JsonParser.parseString(v.getString(id)));
                } catch (RuntimeException e) {
                    // a value that can't be read is left out: that setting is at the server's default
                }
            }
            if (!m.isEmpty()) w.giants.put(giant, m);
        }
        ListTag l = tag.getList("Log", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            w.log.add(new Change(
                    c.getLong("Time"),
                    c.getString("Who"),
                    c.getString("Giant"),
                    c.getString("Id"),
                    c.getString("Name"),
                    c.getString("Old"),
                    c.getString("Now"),
                    c.getString("Undo")));
        }
        while (w.log.size() > MOST_LOG) w.log.remove(0);
        return w;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) {
        CompoundTag g = new CompoundTag();
        for (var e : giants.entrySet()) {
            CompoundTag v = new CompoundTag();
            for (var s : e.getValue().entrySet()) v.putString(s.getKey(), s.getValue().toString());
            g.put(e.getKey(), v);
        }
        tag.put("Giants", g);
        ListTag l = new ListTag();
        for (Change c : log) {
            CompoundTag t = new CompoundTag();
            t.putLong("Time", c.time());
            t.putString("Who", c.who());
            t.putString("Giant", c.giant());
            t.putString("Id", c.id());
            t.putString("Name", c.name());
            t.putString("Old", c.old());
            t.putString("Now", c.now());
            t.putString("Undo", c.undo());
            l.add(t);
        }
        tag.put("Log", l);
        tag.putInt("Version", AdminJson.VERSION);
        return tag;
    }

    /** this world's own values for one giant (or "all"): id to value; empty if none */
    public Map<String, JsonElement> values(String giant) {
        return Map.copyOf(giants.getOrDefault(giant, Map.of()));
    }

    /** every giant (and "all") with values of its own */
    public List<String> giantsWithValues() {
        return List.copyOf(giants.keySet());
    }

    public void put(String giant, String id, JsonElement v) {
        giants.computeIfAbsent(giant, k -> new LinkedHashMap<>()).put(id, v);
        setDirty();
    }

    /** back to the server's default; true if it had a value */
    public boolean remove(String giant, String id) {
        Map<String, JsonElement> m = giants.get(giant);
        if (m == null || m.remove(id) == null) return false;
        if (m.isEmpty()) giants.remove(giant);
        setDirty();
        return true;
    }

    /** all of one giant's values gone; how many there were */
    public int clear(String giant) {
        Map<String, JsonElement> m = giants.remove(giant);
        if (m == null) return 0;
        setDirty();
        return m.size();
    }

    public void log(Change c) {
        log.add(c);
        while (log.size() > MOST_LOG) log.remove(0);
        setDirty();
    }

    /** the changes kept, oldest first */
    public List<Change> log() {
        return List.copyOf(log);
    }
}
