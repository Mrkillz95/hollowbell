package net.jj.giants.players;

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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Part 5's world data (ADMIN_BRIDGE.md 20.8), saved with the world as data/jj_giants_players.dat: picks, per-player
 * rules, leaderboard numbers, the open vote, the signs at spawn and the challenges' progress. Kept as one JSON text in
 * the file, so a field added later just reads as its default in an older world.
 */
public final class PlayerData extends SavedData {
    private static final Gson GSON = new GsonBuilder().create();

    /** what is saved */
    public static final class State {
        public Map<String, Pick> picks = new LinkedHashMap<>();
        public Map<String, Rule> rules = new LinkedHashMap<>();
        public Map<String, Stats> stats = new LinkedHashMap<>();
        public @Nullable Vote vote;
        public @Nullable Result last;
        public List<Sign> signs = new ArrayList<>();
        public Challenges challenges = new Challenges();
    }

    /** a player's My Giant pick: the giant's key, what came of it ("" while on the way), when (ms) */
    public static final class Pick {
        public String giant = "", outcome = "";
        public long time;
        /** one of him came for them (false: at the world's limit, or no loaded spot for him yet) */
        public boolean spawned;
    }

    /** an admin's rules for one player (ADMIN_BRIDGE.md 20.4) */
    public static final class Rule {
        public String name = "";
        public boolean own = true, ignored = false, noKit = false;

        public boolean any() {
            return !own || ignored || noKit;
        }
    }

    /** one player's leaderboard numbers (ticks are the overworld's game time) */
    public static final class Stats {
        public String name = "";
        public Map<String, Integer> kills = new LinkedHashMap<>();
        public Set<String> owned = new LinkedHashSet<>();
        public long streak, longest, last = -1, first = -1;

        public int killsAll() {
            int n = 0;
            for (int k : kills.values()) n += k;
            return n;
        }
    }

    /** the vote that is open */
    public static final class Vote {
        public List<String> ids = new ArrayList<>(), names = new ArrayList<>();
        public long endsAt;
        public int minutes;
        public boolean auto;
        public String by = "";
        public Map<String, Integer> votes = new LinkedHashMap<>();

        public int[] counts() {
            int[] c = new int[ids.size()];
            for (int v : votes.values()) if (v >= 0 && v < c.length) c[v]++;
            return c;
        }
    }

    /** how the last vote ended */
    public static final class Result {
        public String text = "", winner = "";
        public long time;
    }

    /** a leaderboard sign: where, and which board */
    public static final class Sign {
        public String dim = "minecraft:overworld", kind = "slayers";
        public int x, y, z;
    }

    /** the challenges' progress (ADMIN_BRIDGE.md 20.10) */
    public static final class Challenges {
        /** each player's giant kills in Boss Rush (game time of each, the last few) */
        public Map<String, List<Long>> rush = new LinkedHashMap<>();
        /** each player's giant kills in Pocket Giants */
        public Map<String, Integer> pocket = new LinkedHashMap<>();
        /** One Life: the kinds fallen in this run, and who slew one */
        public Set<String> oneLifeFallen = new LinkedHashSet<>();
        public Set<String> oneLifeSlayers = new LinkedHashSet<>();
    }

    public State s = new State();

    private static final Factory<PlayerData> FACTORY = new Factory<>(PlayerData::new, PlayerData::read, null);

    public static PlayerData get(MinecraftServer server) {
        return get(server.overworld());
    }

    public static PlayerData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(FACTORY, "jj_giants_players");
    }

    private static PlayerData read(CompoundTag tag, HolderLookup.Provider p) {
        PlayerData d = new PlayerData();
        try {
            State st = GSON.fromJson(tag.getString("Json"), State.class);
            if (st != null) d.s = fill(st);
        } catch (RuntimeException e) {
            GiantsGuideMod.LOG.warn("Couldn't read the players' data (leaderboards, picks, rules): starting afresh", e);
        }
        return d;
    }

    /** (Gson leaves a field missing from older text as null) */
    private static State fill(State st) {
        if (st.picks == null) st.picks = new LinkedHashMap<>();
        if (st.rules == null) st.rules = new LinkedHashMap<>();
        if (st.stats == null) st.stats = new LinkedHashMap<>();
        if (st.signs == null) st.signs = new ArrayList<>();
        if (st.challenges == null) st.challenges = new Challenges();
        for (Stats x : st.stats.values()) {
            if (x.kills == null) x.kills = new LinkedHashMap<>();
            if (x.owned == null) x.owned = new LinkedHashSet<>();
        }
        return st;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) {
        tag.putString("Json", GSON.toJson(s));
        tag.putInt("Version", 1);
        return tag;
    }

    /** (the tests) the same data, as it would be read back from the file */
    public PlayerData reread() {
        CompoundTag t = save(new CompoundTag(), null);
        return read(t, null);
    }

    public Stats stats(String id, String name) {
        Stats st = s.stats.computeIfAbsent(id, k -> new Stats());
        if (name != null && !name.isEmpty()) st.name = name;
        setDirty();
        return st;
    }

    public Rule rule(String id) {
        Rule r = s.rules.get(id);
        return r == null ? new Rule() : r;
    }
}
