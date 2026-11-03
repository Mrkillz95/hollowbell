package net.jj.giants.systems;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jj.giants.GiantsGuideMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * (Part 8, ADMIN_BRIDGE.md §24.3) What the new systems keep with the world (data/jj_giants_systems.dat): each giant
 * they follow (his starting size, when he was first seen, his wins and last fight, food, loyalty, whether he's a
 * Titan, what he swallowed), the remains rotting, the season last told, and (as compact lists) the blocks waiting to
 * grow back. Read leniently: anything that can't be read starts empty.
 */
public final class SystemsData extends SavedData {

    /** one giant the systems follow, by his id */
    public static final class G {
        public String kind = "";
        /** his size when first seen (growth is from this) */
        public double start;
        /** the game time he was first seen */
        public long born;
        public int wins;
        /** the game time of his last fight (hurt, or hurting) */
        public long lastFight;
        /** the game time he was last seen standing (dropped a game day after) */
        public long seen;
        public double food = 100;
        /** -1: nobody owns him; else 0-100 for {@link #owner} */
        public double loyalty = -1;
        public String owner = "";
        public boolean titan, sulking, turned;
        /** the game time he turned on his owner (loyalty starts again a day later) */
        public long turnedAt;
        /** what the systems last wrote on him: health, damage, speed (1 = nothing) */
        public double tagHealth = 1, tagDamage = 1, tagSpeed = 1;
        /** the mood the systems last gave him ("" none) */
        public String moodSet = "";
        /** who last hurt him (a giant or a player) and when */
        public String lastBy = "";
        public long lastByAt;
        /** a player's things he swallowed (stacks as text), dropped when he falls */
        public List<String> belly = new ArrayList<>();
    }

    /** remains lying where a giant fell: the blocks the systems put there, by layer (0 bones, 1-3 flesh outside in) */
    public static final class Remains {
        public String kind = "", dim = "minecraft:overworld", rule = "skeleton";
        public int x, y, z;
        /** the game time he fell, and how long the corpse takes to rot away (ticks) */
        public long fell, rotTicks;
        /** how far it has rotted (0 fresh .. 4 gone) */
        public int stage;
        /** each block placed: packed position, its layer and the block (as text) */
        public List<Long> pos = new ArrayList<>();
        public List<Integer> layer = new ArrayList<>();
        public List<String> block = new ArrayList<>();
    }

    /** (Part 9) Mirror: what players have done to one kind lately, and what he has learned to counter */
    public static final class Learned {
        /** the damage players dealt him by each tactic (melee, arrows, magic, blasts), fading */
        public double[] score = new double[4];
        /** what each tactic does to him now (1 = all of it) */
        public double[] factor = {1, 1, 1, 1};
        /** each tactic the players near were told he is learning */
        public boolean[] told = new boolean[4];
    }

    /** (Part 9) Underworld: the one of a kind the Guide keeps in the Nether or the End */
    public static final class Under {
        /** his id ("" while none stands) */
        public String id = "";
        /** the game time he fell (0: not yet) and when the next may come */
        public long fell, due;
        /** he never comes back (Comes back after he falls is off) */
        public boolean never;
        /** the spot he was put on (under a roof he is put back there if he ends up on the roof) */
        public int sx, sy, sz;
    }

    public static final class State {
        public Map<String, G> giants = new LinkedHashMap<>();
        public List<Remains> remains = new ArrayList<>();
        /** the season last told (-1: none yet) */
        public int season = -1;
        /** (Part 9) Random chaos: the last game day the giants were shuffled (-1: never) and each kind's moves resting */
        public long chaosDay = -1;
        public Map<String, List<String>> rests = new LinkedHashMap<>();
        /** (Part 9) Mirror: each kind's learning */
        public Map<String, Learned> mirror = new LinkedHashMap<>();
        /** (Part 9) Underworld: "kind|dimension" -> the one kept there */
        public Map<String, Under> under = new LinkedHashMap<>();
        /** (Part 9) Villagers worship: the game day each giant was last left an offering */
        public Map<String, Long> offered = new LinkedHashMap<>();

        void fill() {
            if (giants == null) giants = new LinkedHashMap<>();
            if (remains == null) remains = new ArrayList<>();
            if (rests == null) rests = new LinkedHashMap<>();
            rests.values().removeIf(l -> l == null);
            if (mirror == null) mirror = new LinkedHashMap<>();
            mirror.values().removeIf(m -> m == null || m.score == null || m.score.length != 4 || m.factor == null
                    || m.factor.length != 4);
            for (Learned m : mirror.values()) if (m.told == null || m.told.length != 4) m.told = new boolean[4];
            if (under == null) under = new LinkedHashMap<>();
            under.values().removeIf(u -> u == null);
            for (Under u : under.values()) if (u.id == null) u.id = "";
            if (offered == null) offered = new LinkedHashMap<>();
            giants.values().removeIf(g -> g == null);
            for (G g : giants.values()) {
                if (g.belly == null) g.belly = new ArrayList<>();
                if (g.kind == null) g.kind = "";
                if (g.owner == null) g.owner = "";
                if (g.moodSet == null) g.moodSet = "";
                if (g.lastBy == null) g.lastBy = "";
            }
            remains.removeIf(r -> r == null || r.pos == null || r.layer == null || r.block == null
                    || r.pos.size() != r.layer.size() || r.pos.size() != r.block.size());
        }
    }

    public State s = new State();
    /** the grief queue, as last saved (Grief reads it in at start) */
    public ListTag grief = new ListTag();

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Factory<SystemsData> FACTORY = new Factory<>(SystemsData::new, SystemsData::read, null);

    public static SystemsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "jj_giants_systems");
    }

    static SystemsData read(CompoundTag tag, HolderLookup.Provider p) {
        SystemsData d = new SystemsData();
        try {
            State st = GSON.fromJson(tag.getString("Json"), State.class);
            if (st != null) d.s = st;
        } catch (RuntimeException e) {
            GiantsGuideMod.LOG.warn("Giants systems: couldn't read this world's systems data, starting empty", e);
        }
        d.s.fill();
        if (tag.contains("Grief", Tag.TAG_LIST)) d.grief = tag.getList("Grief", Tag.TAG_COMPOUND);
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) {
        tag.putString("Json", GSON.toJson(s));
        tag.put("Grief", Grief.save());
        tag.putInt("Version", 1);
        return tag;
    }

    public G giant(String id, String kind) {
        G g = s.giants.get(id);
        if (g == null) {
            g = new G();
            g.kind = kind;
            s.giants.put(id, g);
            setDirty();
        }
        return g;
    }
}
