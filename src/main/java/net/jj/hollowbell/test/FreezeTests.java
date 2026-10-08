package net.jj.hollowbell.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.world.NoWait;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import static net.jj.hollowbell.test.HollowbellGameTests.*;

/**
 * The server never stops to wait for land to be made. Asking the height of land that was asked for but was still
 * being made (the game says it "has" that chunk) made a real server wait for it, up to 30 seconds. These ask about
 * such land and check the answer comes at once; a tp to it goes once it's ready.
 */
public class FreezeTests implements FabricGameTest {

    /** land nobody has made yet, far out */
    private static BlockPos unmade(GameTestHelper h, int slot) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        return new BlockPos(o.getX() + 900000 + slot * 4000, 64, o.getZ() - 700000);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200, batch = "freeze_ground")
    public void hisGroundNeverWaitsForLand(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.1f, HollowbellEntity.CALM, 391);
        ServerLevel l = h.getLevel();
        BlockPos far = unmade(h, 0);
        h.runAfterDelay(5, () -> {
            NoWait.ask(l, far.getX(), far.getZ(), 2);
            h.assertTrue(!NoWait.loaded(l, far.getX(), far.getZ()), "that land was loaded already");
            double under = e.groundAt(e.getX(), e.getZ());                 // (loaded: the last ground he saw)
            long t0 = System.nanoTime();
            double g = e.groundAt(far.getX() + 0.5, far.getZ() + 0.5);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            h.assertTrue(Math.abs(g - under) < 1e-6, "the ground of land not made yet should be taken as the last he saw (" + under + "), not " + g);
            h.assertTrue(ms < 50, "asking the ground of land not made yet took " + ms + " ms");
        });
        boolean[] done = {false};
        for (int t = 20; t < 1150; t += 10) {
            h.runAfterDelay(t, () -> {
                if (done[0]) return;
                NoWait.ask(l, far.getX(), far.getZ(), 2);
                if (!NoWait.loaded(l, far.getX(), far.getZ())) return;
                done[0] = true;
                double g = e.groundAt(far.getX() + 0.5, far.getZ() + 0.5);
                int real = l.getHeight(Heightmap.Types.MOTION_BLOCKING, far.getX(), far.getZ());
                h.assertTrue(Math.abs(g - real) < 1e-6, "the ground of loaded land reads " + g + ", not " + real);
                release(h, e);
                h.succeed();
            });
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100000, batch = "freeze_trip")
    public void aTripToLandNotMadeYetGoesOnceItIsReady(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos far = unmade(h, 1);
        ServerPlayer[] p = new ServerPlayer[1];
        h.runAfterDelay(5, () -> {
            p[0] = player(h, Vec3.atCenterOf(h.absolutePos(BlockPos.ZERO)).add(0, 3, 0));
            Vec3 was = p[0].position();
            long t0 = System.nanoTime();
            NoWait.go(p[0], l, far.getX(), far.getZ(), q -> {
                BlockPos safe = net.jj.hollowbell.world.TakeMe.safeNear(l, far.getX(), far.getZ(), 8);
                q.teleportTo(l, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, 0f, 0f);
                return Component.literal("There.");
            });
            long ms = (System.nanoTime() - t0) / 1_000_000;
            h.assertTrue(ms < 50, "starting the trip took " + ms + " ms");
            h.assertTrue(NoWait.onTheWay(p[0]), "not on the way");
            h.assertTrue(p[0].position().distanceTo(was) < 0.01, "went before the land was there");
        });
        boolean[] done = {false};
        // (land is made in real time while the test server ticks as fast as it can: wait by the clock)
        long[] until = {0};
        h.onEachTick(() -> {
            if (done[0] || h.getTick() < 20) return;
            if (until[0] == 0) until[0] = System.currentTimeMillis() + 90_000;
            if ((NoWait.onTheWay(p[0])) && System.currentTimeMillis() < until[0]) return;
            done[0] = true;
            try {
                double d = Math.hypot(p[0].getX() - (far.getX() + 0.5), p[0].getZ() - (far.getZ() + 0.5));
                h.assertTrue(d < 10, "landed " + d + " blocks off");
                int g = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p[0].getBlockX(), p[0].getBlockZ());
                h.assertTrue(Math.abs(p[0].getY() - g) < 1.01, "landed " + (p[0].getY() - g) + " over the ground");
            } finally {
                drop(p[0]);
            }
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1400, batch = "freeze_away")
    public void outOfTheWorldHeComesBackOnlyOnLoadedLand(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.1f, HollowbellEntity.CALM, 392);
        ServerLevel l = h.getLevel();
        BlockPos far = unmade(h, 2);
        net.jj.hollowbell.world.Away a = net.jj.hollowbell.world.Away.get(l.getServer());
        java.util.UUID[] id = {null};
        // (kept here too: other tests may clear the shared list of those out of the world while this one waits)
        Object[] rec = {null};
        boolean[] done = {false};
        h.runAfterDelay(20, () -> {
            id[0] = e.getUUID();
            h.assertTrue(e.stepAside(), "out of the world he goes");
            net.jj.hollowbell.world.Away.Rec r = a.get(id[0]);
            h.assertTrue(r != null, "and is written down");
            rec[0] = r;
            // (his sum moved out to land nobody has made yet)
            r.going = false; r.fromX = far.getX() + 0.5; r.fromZ = far.getZ() + 0.5;
            NoWait.ask(l, far.getX(), far.getZ(), 1);
            h.assertTrue(!NoWait.loaded(l, far.getX(), far.getZ()), "that land was loaded already");
            long t0 = System.nanoTime();
            HollowbellEntity back = a.bringBackWhenLoaded(l, r);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            h.assertTrue(back == null, "he came back on land that isn't loaded");
            h.assertTrue(a.get(id[0]) != null, "and his sum was lost");
            h.assertTrue(ms < 50, "asking took " + ms + " ms");
        });
        for (int t = 40; t < 1350; t += 10) {
            final int at = t;
            h.runAfterDelay(t, () -> {
                if (done[0] || rec[0] == null) return;
                // (a busy test server can leave that land waiting a long time behind the rest: after a while it's
                // forced, as somebody standing there would; what's tested is that he comes back once it's loaded)
                if (at == 400) l.setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
                net.jj.hollowbell.world.Away.Rec r = (net.jj.hollowbell.world.Away.Rec) rec[0];
                NoWait.ask(l, far.getX(), far.getZ(), 1);
                HollowbellEntity back = a.bringBackWhenLoaded(l, r);
                if (back == null) return;
                done[0] = true;
                h.assertTrue(Math.hypot(back.getX() - (far.getX() + 0.5), back.getZ() - (far.getZ() + 0.5)) < 2, "he came back somewhere else");
                back.discard();
                l.setChunkForced(far.getX() >> 4, far.getZ() >> 4, false);
                release(h, e);
                h.succeed();
            });
        }
    }
}
