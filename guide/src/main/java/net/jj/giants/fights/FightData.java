package net.jj.giants.fights;

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
import org.jetbrains.annotations.Nullable;

/**
 * (Part 10B) The giant fights' world data, data/jj_giants_fights.dat: each admin's fight script being edited, the
 * setups saved by name, the owners' giant wins, the betting board, who has said yes to owned giants fighting, wild
 * losers who follow their winner, and giants resting after a fight. One JSON text, so a field added later reads as its
 * default in an older world.
 */
public final class FightData extends SavedData {
    static final Gson GSON = new GsonBuilder().create();
    /** (the tests) */
    public static final Gson GSON_FOR_TESTS = GSON;

    public static final class State {
        /** each admin's script as they edit it (by player id) */
        public Map<String, Setup> drafts = new LinkedHashMap<>();
        /** setups saved by name (lower-case key) */
        public Map<String, Setup> saved = new LinkedHashMap<>();
        /** owners' giant wins: player id -> {name, wins} */
        public Map<String, Board> wins = new LinkedHashMap<>();
        /** the betting board: player id -> {name, right, wrong, won} */
        public Map<String, Board> bets = new LinkedHashMap<>();
        /** owners who said their giants may fight other owners' giants (Owned giants fight: both_ok) */
        public List<String> allow = new ArrayList<>();
        /** a wild loser who follows his winner: loser id -> winner id */
        public Map<String, String> follows = new LinkedHashMap<>();
        /** a giant resting after a fight: id -> game time he may fight again */
        public Map<String, Long> rest = new LinkedHashMap<>();
    }

    /** one line of a board */
    public static final class Board {
        public String name = "";
        public int wins, right, wrong;
        public long won;
    }

    /** one giant in a fight script: his side, a fresh one (kind, size) or one out there (id), and his own options */
    public static final class Fighter {
        public String side = "a", kind = "", id = "";
        public double size = 0.5;
        /** his moves (empty: all of them) and his finisher ("" = his own) */
        public List<String> moves = new ArrayList<>();
        public String finisher = "";
        /** the share of his health he starts with */
        public double health = 1;

        Fighter copy() {
            return GSON.fromJson(GSON.toJson(this), Fighter.class);
        }
    }

    /** a fight script (ADMIN_BOOK.md "Fight script for staged fights" 1-14) */
    public static final class Setup {
        public String name = "";
        public List<Fighter> fighters = new ArrayList<>();
        /** 1 winner: a, b, or fight (let them fight) */
        public String winner = "fight";
        /** 2 the loser's ending: death, escape, caught, bow */
        public String ending = "death";
        /** 3 the break point: the loser breaks at this share of his health */
        public double breakAt = 0.25;
        /** 4 length: quick, normal, epic */
        public String length = "normal";
        /** 7 arena damage: usual, off, some, little; grief heal afterwards */
        public String arena = "usual";
        public boolean healBlocks;
        /** 8 rounds: 1, 3, 5; rest between (seconds) and the share healed */
        public int rounds = 1;
        public int rest = 20;
        public double roundHeal = 0.25;
        /** 10 players: spectators safe or in danger; joining a side; bets */
        public String spectators = "safe";
        public boolean join;
        public boolean bets;
        /** 11 the show */
        public boolean music = true, countdown = true, announcer = true, slowmo = true, bars = true;
        /** 12 after: the winner goes home, stays or turns on players; the loser's remains; the winner grows */
        public String after = "home";
        public String remains = "rule";
        public boolean grow = true;
        /** where it was saved (a saved setup replays there unless started "here") */
        public String dim = "";
        public double x, y, z;
        public float yaw;

        public Setup copy() {
            return fill(GSON.fromJson(GSON.toJson(this), Setup.class));
        }

        public List<Fighter> side(String s) {
            List<Fighter> out = new ArrayList<>();
            for (Fighter f : fighters) if (f.side.equals(s)) out.add(f);
            return out;
        }
    }

    /** (Gson leaves a field missing from older text as null) */
    static Setup fill(Setup s) {
        if (s.fighters == null) s.fighters = new ArrayList<>();
        for (Fighter f : s.fighters) if (f.moves == null) f.moves = new ArrayList<>();
        if (s.name == null) s.name = "";
        if (s.dim == null) s.dim = "";
        return s;
    }

    public State s = new State();

    private static final Factory<FightData> FACTORY = new Factory<>(FightData::new, FightData::read, null);

    public static FightData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "jj_giants_fights");
    }

    private static FightData read(CompoundTag tag, HolderLookup.Provider p) {
        FightData d = new FightData();
        try {
            State st = GSON.fromJson(tag.getString("Json"), State.class);
            if (st != null) d.s = fillState(st);
        } catch (RuntimeException e) {
            GiantsGuideMod.LOG.warn("Couldn't read the giant fights' data (setups, boards): starting afresh", e);
        }
        return d;
    }

    private static State fillState(State st) {
        if (st.drafts == null) st.drafts = new LinkedHashMap<>();
        if (st.saved == null) st.saved = new LinkedHashMap<>();
        if (st.wins == null) st.wins = new LinkedHashMap<>();
        if (st.bets == null) st.bets = new LinkedHashMap<>();
        if (st.allow == null) st.allow = new ArrayList<>();
        if (st.follows == null) st.follows = new LinkedHashMap<>();
        if (st.rest == null) st.rest = new LinkedHashMap<>();
        st.drafts.values().forEach(FightData::fill);
        st.saved.values().forEach(FightData::fill);
        return st;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) {
        tag.putString("Json", GSON.toJson(s));
        tag.putInt("Version", 1);
        return tag;
    }

    /** this admin's script (a fresh one the first time) */
    public Setup draft(String player) {
        Setup d = s.drafts.get(player);
        if (d == null) {
            d = new Setup();
            s.drafts.put(player, d);
            setDirty();
        }
        return d;
    }

    public @Nullable Setup saved(String name) {
        Setup d = s.saved.get(name.trim().toLowerCase(java.util.Locale.ROOT));
        return d == null ? null : d.copy();
    }

    public Board board(Map<String, Board> m, String id, String name) {
        Board b = m.computeIfAbsent(id, k -> new Board());
        if (name != null && !name.isEmpty()) b.name = name;
        setDirty();
        return b;
    }
}
