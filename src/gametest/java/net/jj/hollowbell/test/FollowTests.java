package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.drop;
import static net.jj.hollowbell.test.HollowbellGameTests.player;
import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

import java.util.function.Supplier;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.net.CodexOrders;
import net.jj.hollowbell.net.CodexPayload;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * "Follow me" from his own book (ADMIN_BRIDGE.md 21.2), through the real order path: the server handler the book's
 * FOLLOW press reaches (CodexOrders.handle, what the network receiver calls), from an owner holding the book. The owner
 * starts at his follow distance and walks 60 blocks straight away at about 4.3 blocks a second; within 30 seconds of
 * the walk ending he has come at least 30 blocks after them and is back within where he sets off (+4).
 */
public class FollowTests implements FabricGameTest {

    /** a walk of 60 blocks at 4.3 blocks a second, a little each tick */
    static final double WALK = 60, STEP = 4.3 / 20;

    static final int WALK_TICKS = (int) Math.ceil(WALK / STEP);

    /** how near he comes while following before he stops (BellFlight.followSpot, FollowGap: just past his body) */
    static double keep(HollowbellEntity e) {
        return e.followStop();
    }
    /**
     * how far the nearest edge of his body (his hull's columns, flat) is from a spot, counting only the parts down at
     * the height of someone standing there (a canopy or a head high over them isn't on them): below 0, it's on them
     */
    static double clearance(double[] hl, net.minecraft.world.phys.Vec3 at) {
        double c = Double.MAX_VALUE;
        for (int i = 0; i + 5 < hl.length; i += 6)
            if (hl[i + 3] < at.y + 1.8 && hl[i + 4] > at.y)
                c = Math.min(c, Math.hypot(hl[i] - at.x, hl[i + 1] - at.z) - hl[i + 2]);
        return c;
    }

    /**
     * how far his hull reaches out from (x, z) facing yaw, counting only the parts that come down to within 3 blocks
     * of his feet (y), what could tread on somebody: ahead, out to either side, and behind (the furthest edge of its
     * columns each way)
     */
    static double[] hullReaches(double[] hl, double x, double y, double z, float yaw) {
        float a = yaw * net.minecraft.util.Mth.DEG_TO_RAD;
        double fx = -net.minecraft.util.Mth.sin(a), fz = net.minecraft.util.Mth.cos(a);
        double front = 0, side = 0, back = 0;
        for (int i = 0; i + 5 < hl.length; i += 6) {
            if (hl[i + 3] > y + 3) continue;
            double dx = hl[i] - x, dz = hl[i + 1] - z, r = hl[i + 2];
            double fw = dx * fx + dz * fz, sd = Math.abs(dx * fz - dz * fx);
            front = Math.max(front, fw + r);
            back = Math.max(back, -fw + r);
            side = Math.max(side, sd + r);
        }
        return new double[] {front, side, back};
    }

    /**
     * sizes 0.1, 1 and 2 (his hull measured at size 1, grown with his size): the follow distances, checked. r1: his
     * parts down at the ground (he stops clear of them), all: the whole of him (he sets off before you're far past it)
     */
    static String checkGaps(GameTestHelper h, String name, double[] r1, double[] all,
            java.util.function.Consumer<Float> size, java.util.function.Supplier<double[]> stopStart) {
        double whole1 = Math.max(all[0], Math.max(all[1], all[2]));
        StringBuilder said = new StringBuilder(String.format(
                " hull at size 1: at the ground front %.1f side %.1f back %.1f, all of him %.1f;", r1[0], r1[1], r1[2],
                whole1));
        h.assertTrue(Math.max(r1[0], Math.max(r1[1], r1[2])) > 1, "his hull is there:" + said);
        double lastStop = 0;
        for (float s : new float[] {0.1f, 1f, 2f}) {
            size.accept(s);
            // (his own facing turns slowly at first: the furthest of his body any way round counts, not only ahead)
            double front = Math.max(r1[0], Math.max(r1[1], r1[2])) * s, wide = whole1 * s;
            double[] ss = stopStart.get();
            double stop = ss[0], start = ss[1];
            said.append(String.format(" size %.1f: stop %.1f start %.1f;", s, stop, start));
            h.assertTrue(stop >= front + 2, "size " + s + ": he stops clear of his body:" + said);
            h.assertTrue(start >= stop + 3, "size " + s + ": he sets off a bit past where he stops:" + said);
            h.assertTrue(start <= wide * 1.5 + 20, "size " + s + ": he sets off close:" + said);
            h.assertTrue(stop > lastStop, "bigger, further:" + said);
            lastStop = stop;
        }
        return "FOLLOWGAP " + name + said;
    }


    /** the land along the walk (+z from him), forced and running, r chunks each side; what to give unpath */
    static int[] path(GameTestHelper h, HollowbellEntity e, double length, int r) {
        int x = e.getBlockX(), z0 = e.getBlockZ(), z1 = (int) (z0 + length);
        int[] a = {x, z0, z1, r};
        forced(h, a, true);
        for (int z = z0; z <= z1; z += 16) HollowbellGameTests.runsEntities(h, x, z, r);
        return a;
    }

    static void unpath(GameTestHelper h, int[] a) {
        if (a != null) forced(h, a, false);
    }

    private static void forced(GameTestHelper h, int[] a, boolean on) {
        int x = a[0], z0 = a[1], z1 = a[2], r = a[3];
        for (int cx = (x >> 4) - r; cx <= (x >> 4) + r; cx++)
            for (int cz = (z0 >> 4) - r; cz <= (z1 >> 4) + r; cz++) {
                h.getLevel().setChunkForced(cx, cz, on);
                if (on) h.getLevel().getChunk(cx, cz);
            }
    }

    static void stand(GameTestHelper h, ServerPlayer p, double x, double z) {
        int y = h.getLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        p.teleportTo(x, y, z);
    }

    /** the owner, holding his book, at his follow distance from him */
    static ServerPlayer owner(GameTestHelper h, HollowbellEntity e) {
        ServerPlayer p = player(h, e.position());
        p.getInventory().add(new ItemStack(ModItems.CODEX));
        stand(h, p, e.getX(), e.getZ() + keep(e));
        return p;
    }

    static void press(ServerPlayer p, int action) {
        CodexOrders.handle(p, new CodexPayload(action));
    }

    /**
     * From tick at: the owner walks 60 blocks straight on (+z); the ticks it takes. (Everything is scheduled from the
     * test's start: scheduling from inside a scheduled step breaks the test runner's list while it goes through it.)
     */
    static int walk(GameTestHelper h, Supplier<ServerPlayer> p, int at) {
        double[] start = new double[2];
        h.runAfterDelay(at, () -> {
            start[0] = p.get().getX();
            start[1] = p.get().getZ();
        });
        for (int i = 1; i <= WALK_TICKS; i++) {
            double dz = Math.min(WALK, i * STEP);
            h.runAfterDelay(at + i, () -> stand(h, p.get(), start[0], start[1] + dz));
        }
        return WALK_TICKS;
    }

    /**
     * From tick at, the owner walks off; who: the one that should follow (looked up when the walk starts). He passes
     * once he has come 30 blocks after them and is within where he sets off (+4), checked every 10 ticks
     * for 30 seconds after the walk ends.
     */
    static void followsTheWalk(GameTestHelper h, Supplier<ServerPlayer> pl, Supplier<HollowbellEntity> who, int at,
            Runnable cleanup) {
        double[] from = new double[1];
        boolean[] done = new boolean[1];
        h.runAfterDelay(at, () -> from[0] = who.get().getZ());
        int end = at + walk(h, pl, at);
        for (int t = end; t <= end + 600; t += 10) {
            int tt = t;
            h.runAfterDelay(t, () -> {
                if (done[0]) return;
                HollowbellEntity e = who.get();
                ServerPlayer p = pl.get();
                double came = e.getZ() - from[0], d = e.horiz(p.position()), near = e.followStart() + 4;
                if (came >= 30 && d <= near) {
                    done[0] = true;
                    cleanup.run();
                    h.succeed();
                } else if (tt + 10 > end + 600) {
                    done[0] = true;
                    cleanup.run();
                    h.fail("he didn't keep up: came " + (int) came + " blocks, " + (int) d + " off (want <= "
                            + (int) near + "), following=" + e.following() + " goal=" + e.goal() + " staying="
                            + e.staying() + " asleep=" + e.asleep() + " target=" + e.getTarget());
                }
            });
        }
    }

    private static void followsAtSize(GameTestHelper h, float scale, int slot) {
        HollowbellEntity e = spawnAway(h, scale, HollowbellEntity.CALM, slot);
        ServerPlayer[] p = new ServerPlayer[1];
        int[][] land = new int[1][];
        h.runAfterDelay(10, () -> {
            land[0] = path(h, e, keep(e) + WALK + 40, 2 + Math.round(2 * scale));
            p[0] = owner(h, e);
            press(p[0], CodexPayload.FOLLOW);
            h.assertTrue(e.following() && p[0].getUUID().equals(e.followingWho()), "the book didn't make him follow");
        });
        followsTheWalk(h, () -> p[0], () -> e, 20, () -> {
            unpath(h, land[0]);
            drop(p[0]);
            release(h, e);
        });
    }

    /**
     * JJ (Nov 3): the follow distances grow with his body, and stay close. At sizes 0.1, 1 and 2 (his hull measured
     * at size 1, grown with his size) he stops clear of the front of his body, sets off a little further out, and
     * never waits till you're far past his body.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "follow_gaps")
    public void followGapsGrowWithHisBody(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 1f, HollowbellEntity.CALM, 1516);
        h.runAfterDelay(40, () -> {
            // (he flies: all of him counts, his strands too)
            double[] r1 = hullReaches(e.jjHull(), e.getX(), Double.MAX_VALUE / 2, e.getZ(), e.getYRot());
            double[] all = hullReaches(e.jjHull(), e.getX(), Double.MAX_VALUE / 2, e.getZ(), e.getYRot());
            String said = checkGaps(h, "Hollowbell", r1, all, e::setBellScale,
                    () -> new double[] {e.followStop(), e.followStart()});
            net.jj.hollowbell.HollowbellMod.LOG.info(said);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200, batch = "follow_small")
    public void followsAndKeepsUpAtTheSmallEggSize(GameTestHelper h) {
        followsAtSize(h, 0.2f, 1504);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200, batch = "follow_big")
    public void followsAndKeepsUpAtTheWorldsOwnSize(GameTestHelper h) {
        followsAtSize(h, 1f, 1506);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800, batch = "follow_unfollow")
    public void pressedAgainHeStopsFollowing(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.2f, HollowbellEntity.CALM, 1508);
        ServerPlayer[] p = new ServerPlayer[1];
        int[][] land = new int[1][];
        double[] from = new double[2];
        h.runAfterDelay(10, () -> {
            land[0] = path(h, e, keep(e) + WALK + 40, 3);
            p[0] = owner(h, e);
            press(p[0], CodexPayload.FOLLOW);
            h.assertTrue(e.following(), "the book didn't make him follow");
            press(p[0], CodexPayload.FOLLOW);
            h.assertTrue(!e.following() && e.followingWho() == null, "pressed again, he still follows");
            // (so his own wandering can't move him: roam stay never stops him following, the follow comes first)
            h.assertTrue(java.util.List.of("ok").equals(net.jj.hollowbell.GiantsBridge.giants(h.getLevel().getServer(),
                    "adminset", "{\"admin\":1,\"values\":{\"roam\":\"stay\"}}")), "roam stay");
            from[0] = e.getX();
            from[1] = e.getZ();
        });
        int end = 20 + walk(h, () -> p[0], 20);
        h.runAfterDelay(end + 200, () -> {
            double came = Math.hypot(e.getX() - from[0], e.getZ() - from[1]);
            boolean following = e.following();
            net.jj.hollowbell.GiantsAdmin.clear();
            unpath(h, land[0]);
            drop(p[0]);
            release(h, e);
            h.assertFalse(following, "he took up following again");
            h.assertTrue(came < 8, "he moved anyway: " + (int) came + " blocks");
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200, batch = "follow_saved")
    public void heStillFollowsAfterASaveAndLoad(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.2f, HollowbellEntity.CALM, 1510);
        ServerPlayer[] p = new ServerPlayer[1];
        int[][] land = new int[1][];
        HollowbellEntity[] b = new HollowbellEntity[1];
        h.runAfterDelay(10, () -> {
            land[0] = path(h, e, keep(e) + WALK + 40, 3);
            p[0] = owner(h, e);
            press(p[0], CodexPayload.FOLLOW);
            h.assertTrue(e.following(), "the book didn't make him follow");
            // his own save, into a fresh one in the level, the old one gone
            CompoundTag tag = new CompoundTag();
            e.saveWithoutId(tag);
            e.discard();
            b[0] = ModEntities.HOLLOWBELL.create(h.getLevel());
            b[0].load(tag);
            h.getLevel().addFreshEntity(b[0]);
            h.assertTrue(b[0].following() && p[0].getUUID().equals(b[0].followingWho()), "following was lost");
        });
        followsTheWalk(h, () -> p[0], () -> b[0], 20, () -> {
            unpath(h, land[0]);
            drop(p[0]);
            release(h, b[0]);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200, batch = "follow_after_stay")
    public void afterHoldStillFollowMeStillWorks(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.2f, HollowbellEntity.CALM, 1512);
        ServerPlayer[] p = new ServerPlayer[1];
        int[][] land = new int[1][];
        h.runAfterDelay(10, () -> {
            land[0] = path(h, e, keep(e) + WALK + 40, 3);
            p[0] = owner(h, e);
            press(p[0], CodexPayload.STAY);
            h.assertTrue(e.staying(), "Hold still didn't hold him");
            press(p[0], CodexPayload.FOLLOW);
            h.assertTrue(e.following(), "after Hold still, the book didn't make him follow");
            h.assertFalse(e.staying(), "Hold still is still on");
        });
        followsTheWalk(h, () -> p[0], () -> e, 20, () -> {
            unpath(h, land[0]);
            drop(p[0]);
            release(h, e);
        });
    }

    /** following is an order: left alone a long while on it, he doesn't doze off and drop it */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "follow_awake")
    public void heDoesntDozeOffWhileFollowing(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.2f, HollowbellEntity.CALM, 1514);
        ServerPlayer[] p = new ServerPlayer[1];
        int was = HollowbellEntity.quietBeforeSleep;
        h.runAfterDelay(10, () -> {
            p[0] = owner(h, e);
            press(p[0], CodexPayload.FOLLOW);
            h.assertTrue(e.following(), "the book didn't make him follow");
            HollowbellEntity.quietBeforeSleep = 40;
        });
        h.runAfterDelay(150, () -> {
            HollowbellEntity.quietBeforeSleep = was;
            boolean following = e.following(), asleep = e.asleep();
            drop(p[0]);
            release(h, e);
            h.assertFalse(asleep, "he dozed off while following");
            h.assertTrue(following, "he stopped following");
            h.succeed();
        });
    }
}
