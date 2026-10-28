package net.jj.giants.duels;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Giant-vs-giant duels for balance runs (handoff\DUELS.md). For each pair of the eight giants, N runs, the start side
 * swapped every run: both spawn at full size 150 blocks apart (inside the 160-block meet range, where giants notice
 * each other in the world; further apart some let go of the target at once) on one wide arena of deep flat ground (a lake to one side for Wreckback),
 * a meeting fight is started through each mod's own GiantsBridge "meet" (as /giants meet does), and every tick the
 * harness reads both giants' real health. A run ends when one backs down (jj_giant_yield), dies, or time runs out.
 * One row per run in &lt;out&gt;.csv and the moves that landed in &lt;out&gt;-moves.csv (run folder build/duels).
 *
 * <p>Compiles against none of the giant mods (reflection only), so it runs with whatever released jars are in
 * run-mods/. Only in the -Pduels run (its own source set), never in the guide's normal game tests.
 *
 * <p>Gradle properties (passed on as jj.duel.*): -Pduel_runs=2 -Pduel_seconds=240 -Pduel_size=1 -Pduel_pairs=P-G,C-H
 * -Pduel_sea=true (the whole arena 8 deep) -Pduel_lake=false -Pduel_sprint=false -Pduel_out=name -Pduel_tag=label
 * -Pduel_tune="P.giantArmor=0.6;C.mobDamage=2.2;net.jj.cerberus.entity.CerberusEntity.snapGiantGap=30"
 * (a kind's code before the dot means that mod's config values; numbers patched at run time, no repo touched), or
 * -Pduel_tune=@C:/some/tune.txt (the same, one per line; # starts a comment). -Pduel_escape=true: the backing-down
 * check (see ESCAPE).
 */
public class GiantDuelTests implements FabricGameTest {
    /** short code, mod id, entity id, its GiantsBridge, its config class, size setter and getter, its move names */
    record Kind(String code, String mod, String id, String bridge, String config, String setSize, String getSize,
            String moveNames) {}

    static final Kind[] KINDS = {
        new Kind("P", "mountain_breathes", "mountain_breathes:mountain", "net.jj.mountain.GiantsBridge",
                "net.jj.mountain.MountainConfig", "setMountainScale", "mountainScale", "net.jj.mountain.entity.MountainMoves"),
        new Kind("F", "furrowmaw", "furrowmaw:furrowmaw", "net.jj.furrowmaw.GiantsBridge",
                "net.jj.furrowmaw.FurrowConfig", "setScale", "scale", null),
        new Kind("C", "fire_ice_cerberus", "fire_ice_cerberus:cerberus", "net.jj.cerberus.GiantsBridge",
                "net.jj.cerberus.CerberusConfig", "setScale", "scale", null),
        new Kind("H", "hollowbell", "hollowbell:hollowbell", "net.jj.hollowbell.GiantsBridge",
                "net.jj.hollowbell.BellConfig", "setBellScale", "bellScale", "net.jj.hollowbell.entity.Moves"),
        new Kind("W", "lanternwillow", "lanternwillow:lanternwillow", "net.jj.lanternwillow.GiantsBridge",
                "net.jj.lanternwillow.WillowConfig", "setSize", "size", "net.jj.lanternwillow.entity.Moves"),
        new Kind("WB", "wreckback", "wreckback:wreckback", "net.jj.wreckback.GiantsBridge",
                "net.jj.wreckback.WreckConfig", "setSize", "size", "net.jj.wreckback.entity.Moves"),
        new Kind("S", "swarmforge", "swarmforge:swarmforge", "net.jj.swarmforge.GiantsBridge",
                "net.jj.swarmforge.SwarmConfig", "setSize", "size", "net.jj.swarmforge.entity.Moves"),
        new Kind("G", "gloamwick", "gloamwick:gloamwick", "net.jj.gloamwick.GiantsBridge",
                "net.jj.gloamwick.GloamConfig", "setSize", "size", "net.jj.gloamwick.entity.Moves")
    };

    static final String YIELD = "jj_giant_yield";
    static final int RUNS = Integer.getInteger("jj.duel.runs", 2);
    static final float SIZE = Float.parseFloat(System.getProperty("jj.duel.size", "1.0"));
    static final int LIMIT = 20 * Integer.getInteger("jj.duel.seconds", 240);
    static final String ONLY = System.getProperty("jj.duel.pairs", "");
    static final boolean SEA = Boolean.getBoolean("jj.duel.sea");
    static final boolean LAKE = !"false".equals(System.getProperty("jj.duel.lake"));
    static final boolean SPRINT = !"false".equals(System.getProperty("jj.duel.sprint"));
    static final String OUT = System.getProperty("jj.duel.out", "giant-duels");
    static final String TAG = System.getProperty("jj.duel.tag", "base");
    /**
     * escape: the backing-down check. 45 s into each duel one side (a on even runs, b on odd) is put at 27 % health,
     * the other finishes it to the quarter where it backs down, and the harness watches the minute after: does the
     * loser get away (how far, how high, how much it still takes), does the winner let it go, does anyone stay stuck.
     */
    static final boolean ESCAPE = Boolean.getBoolean("jj.duel.escape");
    /** also watch the minute after a natural backing down in a normal duel (off: the duel ends as it backs down) */
    static final boolean AFTER_ALWAYS = Boolean.getBoolean("jj.duel.after");
    static final int DROP_AT = 45 * 20, AFTER = 1200, NO_YIELD = 2400;
    static final String TUNE = tuneText(System.getProperty("jj.duel.tune", ""));
    static final Path CSV = Path.of(OUT + ".csv"), MOVES = Path.of(OUT + "-moves.csv");

    /** the arena: far out, deep flat ground (so burrowers have room under it), surface at y TOP */
    static final int CX = 120_000, CZ = 120_000, ARENA_CHUNKS = 40, TOP = 10, HALF_GAP = 75;
    static final int ARENA_R = ARENA_CHUNKS * 16;
    /** a lake to the north of the fight line, deep enough for Wreckback's sea moves (6 at full size) */
    static final int LAKE_X = CX, LAKE_Z = CZ - 240, LAKE_R = 90, LAKE_DEPTH = 10, SEA_DEPTH = 8;
    /** no damage either way this long: the meet is given again (and counted) */
    static final int QUIET = 1200;

    static boolean ready;
    static final TicketType<ChunkPos> ARENA = TicketType.create("jj_duel_arena", Comparator.comparingLong(ChunkPos::toLong));

    @GameTestGenerator
    public Collection<TestFunction> duels() {
        List<TestFunction> out = new ArrayList<>();
        int n = 0;
        // run-major order, so a cut-short sweep still has every pair from one side
        for (int run = 0; run < RUNS; run++)
            for (int a = 0; a < KINDS.length; a++)
                for (int b = a + 1; b < KINDS.length; b++) {
                    Kind ka = KINDS[a], kb = KINDS[b];
                    if (!wanted(ka, kb)) continue;
                    Kind west = run % 2 == 0 ? ka : kb, east = run % 2 == 0 ? kb : ka;
                    int r = run;
                    String name = String.format(Locale.ROOT, "duel_%03d_%s_%s_%d", n++, ka.code, kb.code, run);
                    // each its own batch: batches run one after another, so only one duel is ever on the arena
                    out.add(new TestFunction(name, name, EMPTY_STRUCTURE, LIMIT + 3000, 0, false,
                            h -> duel(h, ka, kb, west, east, r)));
                }
        return out;
    }

    static boolean wanted(Kind a, Kind b) {
        if (ONLY.isBlank()) return true;
        for (String p : ONLY.split(",")) {
            String[] w = p.trim().split("-");
            if (w.length == 2 && ((w[0].equals(a.code) && w[1].equals(b.code)) || (w[0].equals(b.code) && w[1].equals(a.code))))
                return true;
        }
        return false;
    }

    static void duel(GameTestHelper h, Kind ka, Kind kb, Kind west, Kind east, int run) {
        ServerLevel l = h.getLevel();
        MinecraftServer server = l.getServer();
        String first = "";
        if (!ready) {
            long t0 = System.nanoTime();
            setUp(server);
            build(l);
            ready = true;
            first = String.format(Locale.ROOT, "arena built in %.0f s ", (System.nanoTime() - t0) / 1e9);
        }
        tune();
        clear(l);
        if (SPRINT) server.tickRateManager().requestGameToSprint(LIMIT + 400);
        LivingEntity w = spawn(l, west, new Vec3(CX - HALF_GAP, TOP, CZ), -90f);
        LivingEntity e = spawn(l, east, new Vec3(CX + HALF_GAP, TOP, CZ), 90f);
        Duel d = new Duel(new Side(ka, ka == west ? w : e), new Side(kb, kb == west ? w : e), west, run, server, l);
        d.notes.append(first);
        h.runAfterDelay(40, d::start);
        h.onEachTick(() -> {
            if (d.written) return;
            if (!d.done)
                try {
                    d.tick();
                } catch (RuntimeException x) {
                    d.notes.append("error ").append(x).append(' ');
                    d.end(null, "error");
                }
            if (d.done) {
                d.written = true;
                d.write();
                clear(l);
                h.succeed();
            }
        });
    }

    // ---- one side of a duel

    static final class Side {
        final Kind k;
        final LivingEntity e;
        float max, last, lost, healed;
        int firstHitOnMe = -1, lastDealt = -100000;
        String move = "none", lastMove = "none";
        int lastMoveTick = -100000;
        final Map<String, Integer> started = new TreeMap<>();
        /** what landed on the other one: label -> {hits, damage} */
        final Map<String, float[]> landed = new TreeMap<>();
        int samples, targeting, wakes, stuck, summonMax, deep;
        double height;
        final Vec3[] trail = new Vec3[30];

        Side(Kind k, LivingEntity e) {
            this.k = k;
            this.e = e;
        }
    }

    static final class Duel {
        final Side a, b;
        final Kind west;
        final int run;
        final MinecraftServer server;
        final ServerLevel l;
        int ticks, quiet, restarts, samples;
        /** the backing-down check (ESCAPE): who was put low, when, and what happened after it backed down */
        Side low;
        int dropTick = -1, yieldTick = -1, chase, afterSamples;
        Side yielder;
        Vec3 yieldPos;
        double distAtYield = -1, dist30 = -1, dist60 = -1, moved = 0, rise = 0, lostAtYield, healAtYield;
        double dist, farthest;
        boolean started, done, written;
        String winner = "", how = "";
        long wall0;
        final StringBuilder timeline = new StringBuilder(), notes = new StringBuilder();

        Duel(Side a, Side b, Kind west, int run, MinecraftServer server, ServerLevel l) {
            this.a = a;
            this.b = b;
            this.west = west;
            this.run = run;
            this.server = server;
            this.l = l;
        }

        void start() {
            for (Side s : new Side[] {a, b})
                if (!s.e.isAlive()) {
                    notes.append(s.k.code).append(" gone before the start ");
                    end(null, "error");
                    return;
                }
            for (Side s : new Side[] {a, b}) {
                wake(s);
                s.max = hp(s.e, "healthMax");
                s.last = hp(s.e, "healthNow");
                notes.append(s.k.code).append(" size ").append(num(s.e, s.k.getSize)).append(' ');
            }
            notes.append("ground ").append(l.getHeight(Heightmap.Types.MOTION_BLOCKING, CX, CZ)).append(' ');
            meet(true);
            started = true;
            wall0 = System.nanoTime();
        }

        /** both sides, as /giants meet does: each mod is told "otherUUID selfUUID"; the west one asks first */
        void meet(boolean first) {
            Side w = a.k == west ? a : b, o = w == a ? b : a;
            for (Side s : new Side[] {w, o}) {
                Side other = s == a ? b : a;
                List<String> said = bridge(server, s.k, "meet", other.e.getUUID() + " " + s.e.getUUID());
                if (first && said != null) notes.append(s.k.code).append(": ").append(String.join(" ", said)).append(' ');
                // (/giants meet makes it a player's fight, which nobody backs down from: mark it wild, as a meeting
                // out in the world is, so backing down and fleeing are what get measured)
                if (!wild(s) && first) notes.append(s.k.code).append(" not marked wild ");
            }
        }

        void tick() {
            if (!started) return;
            ticks++;
            moves(a);
            moves(b);
            boolean hit = hurt(b, a) | hurt(a, b);
            quiet = hit ? 0 : quiet + 1;
            if (quiet >= QUIET) {
                quiet = 0;
                restarts++;
                meet(false);
            }
            cull(l, a.e, b.e);
            if (ticks % 20 == 0) sample();
            if (ticks % 600 == 0)
                timeline.append(String.format(Locale.ROOT, "%d:%.0f/%.0f;", ticks / 20, b.lost, a.lost));

            if (ESCAPE && dropTick < 0 && ticks >= DROP_AT) {
                low = run % 2 == 0 ? a : b;
                setHp(low, low.max * 0.27f);
                low.last = hp(low.e, "healthNow");
                dropTick = ticks;
            }
            boolean yielded = a.e.getTags().contains(YIELD) || b.e.getTags().contains(YIELD);
            if (yielded && yieldTick < 0) {
                yieldTick = ticks;
                yielder = a.e.getTags().contains(YIELD) ? a : b;
                yieldPos = yielder.e.position();
                distAtYield = horiz(a.e, b.e);
                lostAtYield = yielder.lost;
                healAtYield = yielder.healed;
            }
            if (yieldTick >= 0 && yielder.e.isAlive()) afterYield();
            if (!a.e.isAlive() || !b.e.isAlive()) end(!a.e.isAlive() ? b.k : a.k, "death");
            else if (yieldTick >= 0 && (!ESCAPE && !AFTER_ALWAYS || ticks - yieldTick >= AFTER))
                end(yielder == a ? b.k : a.k, "yield");
            else if (ESCAPE && dropTick >= 0 && yieldTick < 0 && ticks - dropTick >= NO_YIELD) end(null, "no-yield");
            else if (ticks >= LIMIT) {
                // on time: whoever lost the smaller share of max HP, a draw within 5 %
                float fa = a.lost / Math.max(1f, a.max), fb = b.lost / Math.max(1f, b.max);
                end(Math.abs(fa - fb) < 0.05f ? null : fa < fb ? a.k : b.k, "time");
            }
        }

        /** the minute after backing down: how far the loser gets, how high, what it still takes, who follows */
        void afterYield() {
            int t = ticks - yieldTick;
            Side w = yielder == a ? b : a;
            if (t % 20 == 0) {
                afterSamples++;
                if (w.e instanceof Mob m && m.getTarget() == yielder.e) chase++;
                moved = Math.max(moved, Math.hypot(yielder.e.getX() - yieldPos.x, yielder.e.getZ() - yieldPos.z));
                rise = Math.max(rise, yielder.e.getY() - yieldPos.y);
            }
            if (t == 600) dist30 = horiz(a.e, b.e);
            if (t == AFTER - 1) dist60 = horiz(a.e, b.e);
        }

        /** the move each one is in now; a new one counts as used */
        void moves(Side s) {
            String m = moveName(s);
            if (!m.equals("none")) {
                if (!m.equals(s.move)) s.started.merge(m, 1, Integer::sum);
                s.lastMove = m;
                s.lastMoveTick = ticks;
            }
            s.move = m;
        }

        /** what def lost this tick, put down to att: its move now, else the move it just ended (~), else passive */
        boolean hurt(Side def, Side att) {
            float now = def.e.isAlive() ? hp(def.e, "healthNow") : 0f;
            boolean hit = false;
            if (now < def.last) {
                float dmg = def.last - now;
                def.lost += dmg;
                if (def.firstHitOnMe < 0) def.firstHitOnMe = ticks;
                att.lastDealt = ticks;
                String label = !att.move.equals("none") ? att.move
                        : ticks - att.lastMoveTick <= 60 ? att.lastMove + "~" : "passive";
                float[] v = att.landed.computeIfAbsent(label, x -> new float[2]);
                v[0]++;
                v[1] += dmg;
                hit = true;
            } else def.healed += now - def.last;
            def.last = now;
            return hit;
        }

        void sample() {
            samples++;
            Vec3 pa = a.e.position(), pb = b.e.position();
            dist += Math.hypot(pa.x - pb.x, pa.z - pb.z);
            for (Side s : new Side[] {a, b}) {
                Side o = s == a ? b : a;
                Vec3 p = s.e.position();
                farthest = Math.max(farthest, Math.hypot(p.x - CX, p.z - CZ));
                s.samples++;
                if (s.e instanceof Mob m && m.getTarget() == o.e) s.targeting++;
                s.height += p.y - TOP;
                if (asleep(s)) {
                    s.wakes++;
                    wake(s);
                }
                // stuck: has hardly moved in 30 s and landed nothing in that time
                int i = samples % s.trail.length;
                Vec3 then = s.trail[i];
                if (then != null && then.distanceTo(p) < 3 && ticks - s.lastDealt > 600) s.stuck++;
                s.trail[i] = p;
                s.summonMax = Math.max(s.summonMax, summons(l, s));
                if (s.k.code.equals("WB") && Boolean.TRUE.equals(call(s.e, "deepSea"))) s.deep++;
            }
        }

        void end(Kind w, String why) {
            winner = w == null ? "draw" : w.code;
            how = why;
            done = true;
        }

        void write() {
            if (!RUNAWAY.isEmpty()) notes.append("runaway shots ").append(RUNAWAY).append(' ');
            RUNAWAY.clear();
            float sec = Math.max(1, ticks) / 20f;
            double wall = (System.nanoTime() - wall0) / 1e9;
            String head = "tag,run,a,b,west,size,sea,seconds,wall_s,how,winner,dmg_a_to_b,dmg_b_to_a,dps_a_to_b,dps_b_to_a,"
                    + "hp_a_left,hp_b_left,max_a,max_b,heal_a,heal_b,first_hit_on_a_s,first_hit_on_b_s,restarts,"
                    + "target_a_pct,target_b_pct,mean_dist,height_a,height_b,stuck_a_s,stuck_b_s,wakes_a,wakes_b,"
                    + "summons_a,summons_b,wb_deep_pct,farthest,low,yielder,drop_to_yield_s,dist_at_yield,dist_30s,dist_60s,"
                    + "loser_moved,loser_rise,dmg_after_yield,winner_chase_pct,heal_after_yield,notes,timeline\n";
            String row = String.format(Locale.ROOT,
                    "%s,%d,%s,%s,%s,%.2f,%b,%.1f,%.0f,%s,%s,%.0f,%.0f,%.2f,%.2f,%.3f,%.3f,%.0f,%.0f,%.0f,%.0f,%.1f,%.1f,%d,"
                            + "%.0f,%.0f,%.0f,%.0f,%.0f,%d,%d,%d,%d,%d,%d,%.0f,%.0f,%s,%s,%.1f,%.0f,%.0f,%.0f,%.0f,%.0f,%.0f,%.0f,"
                            + "%.0f,\"%s\",%s%n",
                    TAG, run, a.k.code, b.k.code, west.code, SIZE, SEA, sec, wall, how, winner, b.lost, a.lost,
                    b.lost / sec, a.lost / sec, a.last / Math.max(1f, a.max), b.last / Math.max(1f, b.max), a.max,
                    b.max, a.healed, b.healed, a.firstHitOnMe / 20f, b.firstHitOnMe / 20f, restarts,
                    pct(a.targeting, a.samples), pct(b.targeting, b.samples), dist / Math.max(1, samples),
                    a.height / Math.max(1, a.samples), b.height / Math.max(1, b.samples), a.stuck, b.stuck, a.wakes,
                    b.wakes, a.summonMax, b.summonMax,
                    a.k.code.equals("WB") ? pct(a.deep, a.samples) : b.k.code.equals("WB") ? pct(b.deep, b.samples) : -1.0,
                    farthest, low == null ? "-" : low.k.code, yielder == null ? "-" : yielder.k.code,
                    yieldTick < 0 || dropTick < 0 ? -1.0 : (yieldTick - dropTick) / 20.0, distAtYield, dist30, dist60, moved,
                    rise, yielder == null ? -1.0 : yielder.lost - lostAtYield, pct(chase, afterSamples),
                    yielder == null ? -1.0 : yielder.healed - healAtYield, notes.toString().replace('"', '\'').replace('\n', ' '), timeline);
            StringBuilder mv = new StringBuilder();
            for (Side s : new Side[] {a, b}) {
                Side o = s == a ? b : a;
                java.util.Set<String> names = new java.util.TreeSet<>(s.started.keySet());
                names.addAll(s.landed.keySet());
                for (String m : names) {
                    float[] v = s.landed.getOrDefault(m, new float[2]);
                    mv.append(String.format(Locale.ROOT, "%s,%d,%s,%s,%s,%s,%s,%d,%.0f,%.0f%n", TAG, run, a.k.code, b.k.code,
                            s.k.code, o.k.code, m, s.started.getOrDefault(m, 0), v[0], v[1]));
                }
            }
            try {
                if (!Files.exists(CSV)) Files.writeString(CSV, head, StandardCharsets.UTF_8);
                Files.writeString(CSV, row, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
                if (!Files.exists(MOVES))
                    Files.writeString(MOVES, "tag,run,a,b,attacker,defender,move,started,hits,damage\n", StandardCharsets.UTF_8);
                Files.writeString(MOVES, mv.toString(), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            } catch (IOException x) {
                throw new IllegalStateException("can't write " + CSV.toAbsolutePath(), x);
            }
        }
    }

    /**
     * The meeting fight made a wild one: each mod keeps "a player started this" as a private boolean `ordered` on its
     * meeting (the Hollowbell on its flee state); set back to false. True if found.
     */
    static boolean wild(Side s) {
        for (String holder : new String[] {"meeting", "flee"}) {
            Object m = call(s.e, holder);
            if (m == null) continue;
            for (Class<?> c = m.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField("ordered");
                    f.setAccessible(true);
                    f.setBoolean(m, false);
                    return true;
                } catch (NoSuchFieldException x) {
                    // up the hierarchy
                } catch (ReflectiveOperationException | RuntimeException x) {
                    return false;
                }
            }
        }
        return false;
    }

    static double horiz(Entity a, Entity b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    /** a giant's health set straight (each mod's own setter), for the backing-down check */
    static void setHp(Side s, float v) {
        for (String m : new String[] {"setHealthTo", "setHealthNow"}) {
            Optional<Method> f = method(s.e.getClass(), m, float.class);
            if (f.isPresent()) {
                try {
                    f.get().invoke(s.e, v);
                    return;
                } catch (ReflectiveOperationException x) {
                    throw new IllegalStateException(m, x);
                }
            }
        }
        Object h = call(s.e, "health");
        Optional<Method> f = h == null ? Optional.empty() : method(h.getClass(), "setHealthTo", float.class);
        if (f.isEmpty()) throw new IllegalStateException(s.k.id + ": no way to set its health");
        try {
            f.get().invoke(h, v);
        } catch (ReflectiveOperationException x) {
            throw new IllegalStateException("setHealthTo", x);
        }
    }

    static double pct(int n, int of) {
        return of == 0 ? 0 : 100.0 * n / of;
    }

    // ---- the world

    /** every mod: no natural giants, no cap, none left over, nobody stepping out of the world, no block breaking (one arena for all runs) */
    static void setUp(MinecraftServer server) {
        for (Kind k : KINDS) {
            bridge(server, k, "natural", "off");
            // (no cap on how many: a world's own one parked out of the world would make a new one go)
            bridge(server, k, "limit", "0");
            bridge(server, k, "remove", "");
            bridge(server, k, "away", "off");
            bridge(server, k, "griefing", "off");
            bridge(server, k, "meetings", "on");
            bridge(server, k, "fight", "on");
        }
    }

    /**
     * Deep flat ground over the whole arena (stone, dirt, grass on top at y TOP - 1), written straight into the chunk
     * sections (millions of blocks), with a lake to one side, or the whole arena under SEA_DEPTH of water.
     */
    static void build(ServerLevel l) {
        int ox = CX >> 4, oz = CZ >> 4, r = ARENA_CHUNKS;
        BlockState stone = Blocks.STONE.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState(),
                grass = Blocks.GRASS_BLOCK.defaultBlockState(), sand = Blocks.SAND.defaultBlockState(),
                water = Blocks.WATER.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        int lo = l.getMinBuildHeight() + 1;
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                // (a ticket of our own: some giants let go of forced chunks round them when they leave)
                ChunkPos cp = new ChunkPos(ox + x, oz + z);
                l.getChunkSource().addRegionTicket(ARENA, cp, 2, cp);
                LevelChunk c = l.getChunk(ox + x, oz + z);
                for (int bx = 0; bx < 16; bx++)
                    for (int bz = 0; bz < 16; bz++) {
                        int wx = ((ox + x) << 4) + bx, wz = ((oz + z) << 4) + bz;
                        boolean lake = LAKE && !SEA && sq(wx - LAKE_X) + sq(wz - LAKE_Z) < sq(LAKE_R);
                        for (int y = lo; y < TOP + 40; y++) {
                            BlockState s;
                            if (SEA) s = y < TOP - 3 ? stone : y < TOP ? sand : y < TOP + SEA_DEPTH ? water : air;
                            else if (lake) s = y < TOP - LAKE_DEPTH - 3 ? stone : y < TOP - LAKE_DEPTH ? sand : y < TOP ? water : air;
                            else s = y < TOP - 4 ? stone : y < TOP - 1 ? dirt : y < TOP ? grass : air;
                            LevelChunkSection sec = c.getSection(c.getSectionIndex(y));
                            sec.setBlockState(bx, y & 15, bz, s, false);
                        }
                    }
                Heightmap.primeHeightmaps(c, EnumSet.allOf(Heightmap.Types.class));
                c.setUnsaved(true);
            }
    }

    static int sq(int v) {
        return v * v;
    }

    /** everything but players off the arena, and any giant anywhere that isn't in a duel */
    static void clear(ServerLevel l) {
        List<Entity> gone = new ArrayList<>();
        for (Entity e : l.getAllEntities()) {
            if (e instanceof Player) continue;
            String ns = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace();
            boolean ours = false;
            for (Kind k : KINDS) ours |= k.mod.equals(ns);
            if (ours || Math.abs(e.getX() - CX) < ARENA_R + 200 && Math.abs(e.getZ() - CZ) < ARENA_R + 200) gone.add(e);
        }
        for (Entity e : gone) e.discard();
    }

    /**
     * Shots and helpers that leave the arena are taken away: a giant's shot flying on past the loaded ground loads
     * (and makes) a new chunk every tick, which slowed the server to a crawl and ran it out of memory.
     */
    static final Map<String, Integer> RUNAWAY = new ConcurrentHashMap<>();

    static void cull(ServerLevel l, Entity a, Entity b) {
        int edge = ARENA_R - 48;
        List<Entity> gone = new ArrayList<>();
        for (Entity e : l.getAllEntities()) {
            if (e == a || e == b || e instanceof Player) continue;
            // (a runaway shot: over 40 blocks a tick. A Wreckback shot did this twice; its next move-check ray then
            // crossed thousands of chunks in one tick and hung the server. Counted as a finding.)
            if (e.getDeltaMovement().lengthSqr() > 40 * 40 && !(e instanceof LivingEntity)) {
                RUNAWAY.merge(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString(), 1, Integer::sum);
                gone.add(e);
                continue;
            }
            double dx = Math.abs(e.getX() - CX), dz = Math.abs(e.getZ() - CZ);
            if (dx < edge && dz < edge && e.getY() < 700) continue;
            // (only what flew off the arena, and anything whose position went NaN)
            if (dx > ARENA_R + 3000 || dz > ARENA_R + 3000) continue;
            String path = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
            // (the giants' own body parts go where the giant goes)
            if (path.endsWith("_part") || path.contains("seat") || e.getType() == a.getType() || e.getType() == b.getType()) continue;
            gone.add(e);
        }
        for (Entity e : gone) e.discard();
    }

    static LivingEntity spawn(ServerLevel l, Kind k, Vec3 at, float yaw) {
        EntityType<?> type = EntityType.byString(k.id).orElseThrow(() -> new IllegalStateException(k.id + " isn't loaded"));
        Entity e = type.create(l);
        if (!(e instanceof LivingEntity le)) throw new IllegalStateException(k.id + " isn't a creature");
        e.moveTo(at.x, at.y, at.z, yaw, 0f);
        set(e, k.setSize, float.class, SIZE);
        l.addFreshEntity(e);
        return le;
    }

    /** its own summons and robots near the arena (living, not a part of a giant, not the giant) */
    static int summons(ServerLevel l, Side s) {
        AABB box = new AABB(CX - ARENA_R, -64, CZ - ARENA_R, CX + ARENA_R, 400, CZ + ARENA_R);
        return l.getEntities((Entity) null, box, e -> e != s.e && e instanceof LivingEntity && e.isAlive()
                && BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals(s.k.mod)
                && !BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().endsWith("_part")
                && !BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).equals(BuiltInRegistries.ENTITY_TYPE.getKey(s.e.getType())))
                .size();
    }

    // ---- reflection into the giant mods

    static final Map<String, Optional<Method>> METHODS = new ConcurrentHashMap<>();

    static Optional<Method> method(Class<?> c, String name, Class<?>... args) {
        return METHODS.computeIfAbsent(c.getName() + "#" + name + args.length, x -> {
            try {
                return Optional.of(c.getMethod(name, args));
            } catch (NoSuchMethodException e) {
                return Optional.empty();
            }
        });
    }

    static Object call(Object o, String name) {
        Optional<Method> m = method(o.getClass(), name);
        if (m.isEmpty()) return null;
        try {
            return m.get().invoke(o);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    static String num(Entity e, String getter) {
        Object v = call(e, getter);
        return v instanceof Number n ? String.format(Locale.ROOT, "%.2f", n.floatValue()) : "?";
    }

    static void set(Entity e, String name, Class<?> arg, Object v) {
        try {
            e.getClass().getMethod(name, arg).invoke(e, v);
        } catch (ReflectiveOperationException x) {
            throw new IllegalStateException(e.getType() + " has no " + name, x);
        }
    }

    static float hp(Entity e, String getter) {
        Object v = call(e, getter);
        if (v instanceof Number n) return n.floatValue();
        return getter.equals("healthMax") ? ((LivingEntity) e).getMaxHealth() : ((LivingEntity) e).getHealth();
    }

    static final Map<String, String[]> NAMES = new ConcurrentHashMap<>();

    /** the move it is in now ("none" when none): an int moveNow() named by its mod's Moves.NAMES, or an enum move() */
    static String moveName(Side s) {
        if (s.k.moveNames == null) {
            Object m = call(s.e, "move");
            return m == null ? "none" : m.toString().toLowerCase(Locale.ROOT);
        }
        Object m = call(s.e, "moveNow");
        int id = m instanceof Number n ? n.intValue() : 0;
        if (id == 0) return "none";
        String[] names = NAMES.computeIfAbsent(s.k.moveNames, c -> {
            try {
                return (String[]) Class.forName(c).getField("NAMES").get(null);
            } catch (ReflectiveOperationException e) {
                return new String[0];
            }
        });
        return id < names.length ? names[id] : "move" + id;
    }

    static boolean asleep(Side s) {
        for (String g : new String[] {"asleep", "sleeping"}) if (Boolean.TRUE.equals(call(s.e, g))) return true;
        return false;
    }

    /** awake: each mod's own way */
    static void wake(Side s) {
        Entity e = s.e;
        switch (s.k.code) {
            case "P" -> call(e, "wakeNowForTests");
            case "F", "H" -> call(e, "wakeUp");
            case "G" -> {
                Object sl = call(e, "sleep");
                if (sl != null) call(sl, "wake");
            }
            default -> call(e, "wake");
        }
    }

    @SuppressWarnings("unchecked")
    static List<String> bridge(MinecraftServer server, Kind k, String action, String arg) {
        try {
            Method m = Class.forName(k.bridge).getMethod("giants", MinecraftServer.class, String.class, String.class);
            return (List<String>) m.invoke(null, server, action, arg);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("no GiantsBridge for " + k.id, e);
        }
    }

    /**
     * jj.duel.tune: "X.field=value;..." where X is a kind's code (that mod's config values, its static V) or a class
     * name (a static field on it, or on its static V). Applied before every duel, so a mod saving its config back
     * changes nothing.
     */
    static String tuneText(String v) {
        if (!v.startsWith("@")) return v;
        try {
            StringBuilder b = new StringBuilder();
            for (String line : Files.readAllLines(Path.of(v.substring(1)), StandardCharsets.UTF_8)) {
                int hash = line.indexOf('#');
                b.append(hash < 0 ? line : line.substring(0, hash)).append(';');
            }
            return b.toString();
        } catch (IOException x) {
            throw new IllegalStateException("can't read " + v, x);
        }
    }

    static void tune() {
        if (TUNE.isBlank()) return;
        for (String part : TUNE.split(";")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            int eq = p.indexOf('='), dot = p.lastIndexOf('.', eq);
            String owner = p.substring(0, dot), field = p.substring(dot + 1, eq), value = p.substring(eq + 1).trim();
            for (Kind k : KINDS) if (k.code.equals(owner)) owner = k.config;
            try {
                Class<?> c = Class.forName(owner);
                Object target = null;
                Field f;
                try {
                    f = c.getField(field);
                } catch (NoSuchFieldException x) {
                    target = c.getField("V").get(null);
                    f = target.getClass().getField(field);
                }
                if (!Modifier.isStatic(f.getModifiers()) && target == null) target = c.getField("V").get(null);
                Class<?> t = f.getType();
                Object v = t == float.class ? Float.parseFloat(value) : t == double.class ? Double.parseDouble(value)
                        : t == int.class ? Integer.parseInt(value) : t == boolean.class ? Boolean.parseBoolean(value) : value;
                f.set(target, v);
            } catch (ReflectiveOperationException | RuntimeException x) {
                throw new IllegalStateException("can't tune " + p, x);
            }
        }
    }
}
