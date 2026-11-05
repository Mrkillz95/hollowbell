package net.jj.giants.minigames;

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
import net.minecraft.world.level.saveddata.SavedData;

/**
 * (Part 10A, ADMIN_BRIDGE.md §26.9) What the minigames keep with the world (data/jj_giants_minigames.dat): the last
 * results, each player's wins and games by mode, and the best times. One JSON string, read leniently.
 */
public final class MgData extends SavedData {

    /** one game's result */
    public static final class Result {
        public String mode = "", name = "", by = "", line = "";
        public long time;
        public int players, rounds;
        public List<String> winners = new ArrayList<>();
    }

    /** one player's record */
    public static final class Player {
        public String name = "";
        public int wins, games;
        public Map<String, Integer> modeWins = new LinkedHashMap<>();
        /** (Speedrun) the fewest ticks a giant took them */
        public long best;
    }

    public static final class State {
        public List<Result> results = new ArrayList<>();
        public Map<String, Player> players = new LinkedHashMap<>();
        public int games;

        void fill() {
            if (results == null) results = new ArrayList<>();
            if (players == null) players = new LinkedHashMap<>();
            results.removeIf(r -> r == null || r.mode == null);
            for (Result r : results) if (r.winners == null) r.winners = new ArrayList<>();
            for (Player p : players.values()) if (p != null && p.modeWins == null) p.modeWins = new LinkedHashMap<>();
            players.values().removeIf(p -> p == null || p.name == null);
        }
    }

    public State s = new State();
    public static final int MOST_RESULTS = 30;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Factory<MgData> FACTORY = new Factory<>(MgData::new, MgData::read, null);

    public static MgData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "jj_giants_minigames");
    }

    static MgData read(CompoundTag tag, HolderLookup.Provider p) {
        MgData d = new MgData();
        try {
            State st = GSON.fromJson(tag.getString("Json"), State.class);
            if (st != null) d.s = st;
        } catch (RuntimeException e) {
            GiantsGuideMod.LOG.warn("Giants minigames: couldn't read this world's minigame data, starting empty", e);
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

    public Player player(String id, String name) {
        Player p = s.players.computeIfAbsent(id, k -> new Player());
        p.name = name;
        setDirty();
        return p;
    }

    public void result(Result r) {
        s.results.add(r);
        while (s.results.size() > MOST_RESULTS) s.results.remove(0);
        s.games++;
        setDirty();
    }
}
