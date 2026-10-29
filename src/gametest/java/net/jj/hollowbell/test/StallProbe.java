package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.player;
import static net.jj.hollowbell.test.HollowbellGameTests.under;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.Belling;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.world.Painter;
import net.jj.hollowbell.world.TakeMe;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A measuring run, never in the usual run (-Ponly=StallProbe), writing how long each server tick took to
 * build/stall-probe-*.txt:
 * - him at full size fighting a player in a wood (a few hundred chunks held round him, trees all over), every move
 *   one after another;
 * - /giants paint (land and biome, and biome only) and the take-me-to, in the nether: the test overworld is flat and
 *   costs nothing to make, the nether is made like real land.
 * Bounded: each part has its own tick limit and a clock limit, and holds at most 289 chunks.
 */
public class StallProbe implements FabricGameTest {
    /** chunks held each way round him (17 x 17 = 289) */
    private static final int RC = 8;
    /** no part runs longer than this by the clock */
    private static final long WALL = 20L * 60 * 1_000_000_000L;

    /** the tick times the server wrote, from the tick before this one */
    private static long lastTick(MinecraftServer srv) {
        return srv.getTickTimesNanos()[(srv.getTickCount() + 99) % 100];
    }

    private static void write(String name, StringBuilder b) {
        try {
            Files.writeString(Path.of("..", "stall-probe-" + name + ".txt"), b.toString());
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    private static final class Stat {
        double sum;
        long worst;
        int n, over;

        void add(long ns) {
            sum += ns;
            worst = Math.max(worst, ns);
            n++;
            if (ns > 50_000_000L) over++;
        }

        String line(String name) {
            return String.format(
                    "%-14s avg %7.2f ms  worst %8.2f ms  over50 %3d  of %4d%n",
                    name, n == 0 ? 0 : sum / n / 1e6, worst / 1e6, over, n);
        }
    }

    private static int[] spot(GameTestHelper h, int slot) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        return new int[] {o.getX() + 20000 + slot * 400, o.getZ() + 5000};
    }

    /** the land rc chunks each way round x z held loaded and running */
    private static void hold(ServerLevel l, int x, int z, int rc) {
        for (int cx = (x >> 4) - rc; cx <= (x >> 4) + rc; cx++)
            for (int cz = (z >> 4) - rc; cz <= (z >> 4) + rc; cz++) {
                l.setChunkForced(cx, cz, true);
                l.getChunk(cx, cz);
            }
        long until = System.nanoTime() + 60_000_000_000L;
        for (int cx = (x >> 4) - rc; cx <= (x >> 4) + rc; cx++)
            for (int cz = (z >> 4) - rc; cz <= (z >> 4) + rc; cz++) {
                BlockPos p = new BlockPos(cx << 4, 0, cz << 4);
                while (!l.isPositionEntityTicking(p) && System.nanoTime() < until)
                    if (!l.getChunkSource().pollTask()) LockSupport.parkNanos(1_000_000L);
            }
    }

    private static void letGo(ServerLevel l, int x, int z, int rc) {
        for (int cx = (x >> 4) - rc; cx <= (x >> 4) + rc; cx++)
            for (int cz = (z >> 4) - rc; cz <= (z >> 4) + rc; cz++) l.setChunkForced(cx, cz, false);
    }

    /** a wood over the held land: an oak every 7 blocks or so, with its leaves */
    private static void wood(ServerLevel l, int x, int z, int r) {
        RandomSource rnd = RandomSource.create(x * 31L + z);
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int tx = x - r; tx <= x + r; tx += 7)
            for (int tz = z - r; tz <= z + r; tz += 7) {
                int bx = tx + rnd.nextInt(3), bz = tz + rnd.nextInt(3);
                if (Math.abs(bx - x) < 6 && Math.abs(bz - z) < 6) continue;
                int g = l.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
                int hgt = 4 + rnd.nextInt(3);
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++)
                        for (int dy = hgt - 2; dy <= hgt + 1; dy++) {
                            if (dy == hgt + 1 && (Math.abs(dx) > 1 || Math.abs(dz) > 1)) continue;
                            l.setBlock(new BlockPos(bx + dx, g + dy, bz + dz), leaf, Block.UPDATE_CLIENTS);
                        }
                for (int dy = 0; dy < hgt; dy++) l.setBlock(new BlockPos(bx, g + dy, bz), log, Block.UPDATE_CLIENTS);
            }
    }

    private static HollowbellEntity bell(ServerLevel l, int x, int z) {
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        HollowbellEntity e = ModEntities.HOLLOWBELL.create(l);
        e.moveTo(x + 0.5, y, z + 0.5, 0f, 0f);
        e.setBellScale(1f);
        e.setMoveCooldown(100000);
        e.skipArrival();
        l.addFreshEntity(e);
        return e;
    }

    private static ServerPlayer playerIn(GameTestHelper h, ServerLevel l, Vec3 at) {
        ServerPlayer p = player(h, at);
        p.teleportTo(l, at.x, at.y, at.z, 0f, 0f);
        return p;
    }

    /** every move, one after the other, against a player under him, in a wood */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 12000, batch = "stall_probe_moves")
    public void everyMoveCostsOnlyAFewMillisecondsATick(GameTestHelper h) {
        MinecraftServer srv = h.getLevel().getServer();
        ServerLevel l = h.getLevel();
        int[] at = spot(h, 40);
        hold(l, at[0], at[1], RC);
        wood(l, at[0], at[1], RC * 16);
        HollowbellEntity e = bell(l, at[0], at[1]);
        ServerPlayer[] p = {null};
        long wall0 = System.nanoTime();
        // state: which move (0 = settling), the tick it started, the tick count
        int[] st = {0, 0, 0};
        Stat[] stats = new Stat[Moves.NAMES.length];
        Stat idle = new Stat();
        List<String> notes = new ArrayList<>();
        StringBuilder log = new StringBuilder();
        h.onEachTick(() -> {
            int t = ++st[2];
            if (t < 3 || st[0] < 0) return;
            long ns = lastTick(srv);
            int m = st[0];
            log.append(t).append(' ').append(m).append(' ').append(ns / 1000).append(' ')
                    .append(java.time.LocalTime.now()).append('\n');
            if (p[0] == null || p[0].isRemoved() || p[0].isDeadOrDying()) {
                if (p[0] != null) {
                    notes.add("the player died at tick " + t);
                    HollowbellGameTests.drop(p[0]);
                }
                p[0] = player(h, under(e).add(6, 0, 0));
                // (a player that never dies: a new one made mid-fight costs a tick of its own)
                p[0].getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
            }
            p[0].setHealth(p[0].getMaxHealth());
            p[0].getFoodData().setFoodLevel(20);
            if (m == 0) {
                if (t > 60) idle.add(ns);
                if (t >= 160) next(h, e, p[0], st, notes);
                return;
            }
            if (stats[m] == null) stats[m] = new Stat();
            int since = t - st[1];
            if (since >= 1) stats[m].add(ns);
            boolean over = e.moveNow() != m && since > 4;
            if (over || since > Moves.length(m) + 120) {
                if (!over) notes.add(Moves.NAMES[m] + " still running after " + since);
                e.moves().letGoOfEverything(false);
                for (Belling b : l.getEntitiesOfClass(Belling.class, e.bodyBox().inflate(60))) b.discard();
                if (m + 1 >= Moves.NAMES.length || System.nanoTime() - wall0 > WALL) {
                    if (m + 1 < Moves.NAMES.length) notes.add("stopped by the clock after " + Moves.NAMES[m]);
                    StringBuilder b = new StringBuilder();
                    b.append(idle.line("idle"));
                    for (int k = 1; k < stats.length; k++) if (stats[k] != null) b.append(stats[k].line(Moves.NAMES[k]));
                    for (String s : notes) b.append("# ").append(s).append('\n');
                    b.append(log);
                    write("moves", b);
                    st[0] = -1;
                    HollowbellGameTests.drop(p[0]);
                    e.discard();
                    letGo(l, at[0], at[1], RC);
                    h.succeed();
                    return;
                }
                next(h, e, p[0], st, notes);
            }
        });
    }

    private static void next(GameTestHelper h, HollowbellEntity e, ServerPlayer p, int[] st, List<String> notes) {
        int m = st[0] + 1;
        st[0] = m;
        st[1] = st[2];
        e.setStay(true);
        Vec3 spot = m == Moves.SLAM || m == Moves.WRAP || m == Moves.SWEEP
                ? e.armTipWorld(0).multiply(1, 0, 1).add(0, e.groundAt(e.armTipWorld(0).x, e.armTipWorld(0).z), 0)
                : under(e).add(6, 0, 0);
        p.teleportTo(h.getLevel(), spot.x, spot.y, spot.z, 0f, 0f);
        e.setTarget(p);
        if (m == Moves.HARVEST) {
            // (something to harvest under him)
            Cow c = EntityType.COW.create(h.getLevel());
            Vec3 u = under(e).add(-4, 0, 3);
            c.moveTo(u.x, u.y, u.z, 0f, 0f);
            h.getLevel().addFreshEntity(c);
            if (!e.forceMove(m, c)) notes.add(Moves.NAMES[m] + " didn't start");
            return;
        }
        if (!e.forceMove(m, p)) notes.add(Moves.NAMES[m] + " didn't start");
    }

    /** /giants paint round a player at the edge of loaded land, in the nether: most of the circle isn't loaded */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400000, batch = "stall_probe_paint")
    public void paintingCostsOnlyAFewMillisecondsATick(GameTestHelper h) {
        paint(h, true, 41);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400000, batch = "stall_probe_paint_biome")
    public void biomePaintingCostsOnlyAFewMillisecondsATick(GameTestHelper h) {
        paint(h, false, 42);
    }

    private static void paint(GameTestHelper h, boolean full, int slot) {
        MinecraftServer srv = h.getLevel().getServer();
        ServerLevel l = srv.getLevel(Level.NETHER);
        int[] at = spot(h, slot);
        int rc = 4;
        hold(l, at[0], at[1], rc);
        ServerPlayer p = playerIn(h, l, new Vec3(at[0] + 0.5, 70, at[1] + 0.5));
        long wall0 = System.nanoTime();
        int[] st = {0, 0};
        Stat s = new Stat();
        StringBuilder log = new StringBuilder();
        String name = full ? "paint-full" : "paint-biome";
        int[] unloaded = {0};
        h.onEachTick(() -> {
            int t = ++st[0];
            if (st[1] < 0) return;
            if (t == 100) {
                st[1] = Painter.start(p, 256, full);
                // (how many of the chunks to paint aren't loaded when it starts)
                for (int cx = (at[0] - 256) >> 4; cx <= (at[0] + 256) >> 4; cx++)
                    for (int cz = (at[1] - 256) >> 4; cz <= (at[1] + 256) >> 4; cz++)
                        if (l.getChunkSource().getChunkNow(cx, cz) == null) unloaded[0]++;
            }
            if (t <= 101) return;
            long ns = lastTick(srv);
            s.add(ns);
            log.append(t).append(' ').append(ns / 1000).append('\n');
            boolean clock = System.nanoTime() - wall0 > WALL;
            if (!Painter.busy() || clock || t > 390000) {
                StringBuilder b = new StringBuilder();
                b.append(s.line(name));
                b.append("# chunks ").append(st[1]).append(", ticks ").append(t - 101)
                        .append(Painter.busy() ? ", not finished" : ", finished")
                        .append(", square round him not loaded at the start: ").append(unloaded[0])
                        .append(String.format(", %.1f s by the clock%n", (System.nanoTime() - wall0) / 1e9));
                b.append(log);
                write(name, b);
                st[1] = -1;
                Painter.forget();
                HollowbellGameTests.drop(p);
                letGo(l, at[0], at[1], rc);
                h.succeed();
            }
        });
    }

    /**
     * /hollowbell tp (and /giants take-me-to), in the nether: to one standing far off with only the land under him
     * loaded, so the landing spot has to be made first ("Getting the land ready")
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 3000, batch = "stall_probe_tp")
    public void takeMeToCostsOnlyAFewMillisecondsATick(GameTestHelper h) {
        MinecraftServer srv = h.getLevel().getServer();
        ServerLevel l = srv.getLevel(Level.NETHER);
        int[] at = spot(h, 43);
        hold(l, at[0], at[1], 2);
        HollowbellEntity e = bell(l, at[0], at[1]);
        // the player stands 3000 blocks off, on land of its own
        int px = at[0] + 3000, pz = at[1];
        hold(l, px, pz, 0);
        ServerPlayer p = playerIn(h, l, new Vec3(px + 0.5, 70, pz + 0.5));
        long wall0 = System.nanoTime();
        int[] st = {0, -1};
        Stat s = new Stat();
        StringBuilder log = new StringBuilder();
        String[] said = {""};
        h.onEachTick(() -> {
            int t = ++st[0];
            if (st[1] == -2) return;
            if (t == 40) said[0] = TakeMe.tp(p, 0).getString();
            if (t <= 41) return;
            long ns = lastTick(srv);
            s.add(ns);
            log.append(t).append(' ').append(ns / 1000).append('\n');
            if (st[1] < 0 && p.distanceToSqr(e) < 400 * 400) st[1] = t;
            boolean clock = System.nanoTime() - wall0 > WALL;
            if ((st[1] > 0 && t > st[1] + 300) || clock || t > 2800) {
                StringBuilder b = new StringBuilder();
                b.append(s.line("take-me-to"));
                b.append("# said: ").append(said[0]).append(", arrived at tick ").append(st[1]).append('\n');
                b.append(log);
                write("tp", b);
                st[1] = -2;
                HollowbellGameTests.drop(p);
                e.discard();
                letGo(l, px, pz, 0);
                letGo(l, at[0], at[1], 2);
                h.succeed();
            }
        });
    }
}
