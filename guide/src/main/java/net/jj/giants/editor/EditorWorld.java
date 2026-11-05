package net.jj.giants.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import net.jj.giants.GiantsGuideMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * (Part 12, ADMIN_BRIDGE.md §29) The giant editor's world data, data/jj_giants_editor.dat: the custom material sets
 * and which look each kind, each giant tag and each one giant wears. Kept as one JSON text (fields added later read as
 * their defaults in an older world). Nothing here: every giant is drawn as always.
 */
public final class EditorWorld extends SavedData {
    private static final Gson GSON = new GsonBuilder().create();

    public static final class State {
        /** custom sets by name */
        public Map<String, Custom> custom = new LinkedHashMap<>();
        /** kind -> look ("frost", "custom:Ice palace") */
        public Map<String, String> kinds = new LinkedHashMap<>();
        /** giant tag name -> look */
        public Map<String, String> tags = new LinkedHashMap<>();
        /** one giant (his id) -> his kind and look */
        public Map<String, One> giants = new LinkedHashMap<>();
    }

    /** a custom set: a built-in one to start from (or none) and the admin's own blocks, by the block replaced */
    public static final class Custom {
        public String name = "";
        public String base = "";
        public Map<String, String> blocks = new LinkedHashMap<>();
        public String by = "";
        public long time;
    }

    public static final class One {
        public String kind = "";
        public String look = "";
    }

    public State s = new State();

    private static final Factory<EditorWorld> FACTORY = new Factory<>(EditorWorld::new, EditorWorld::read, null);

    public static EditorWorld get(MinecraftServer server) {
        return get(server.overworld());
    }

    public static EditorWorld get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(FACTORY, "jj_giants_editor");
    }

    private static EditorWorld read(CompoundTag tag, HolderLookup.Provider p) {
        EditorWorld d = new EditorWorld();
        try {
            State st = GSON.fromJson(tag.getString("Json"), State.class);
            if (st != null) {
                if (st.custom == null) st.custom = new LinkedHashMap<>();
                if (st.kinds == null) st.kinds = new LinkedHashMap<>();
                if (st.tags == null) st.tags = new LinkedHashMap<>();
                if (st.giants == null) st.giants = new LinkedHashMap<>();
                d.s = st;
            }
        } catch (RuntimeException e) {
            GiantsGuideMod.LOG.warn("Couldn't read the giant editor's data (looks, custom sets): starting afresh", e);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) {
        tag.putString("Json", GSON.toJson(s));
        tag.putInt("Version", 1);
        return tag;
    }

    /** a custom set by name, any case */
    public Custom custom(String name) {
        for (Custom c : s.custom.values()) if (c.name.equalsIgnoreCase(name.trim())) return c;
        return null;
    }
}
