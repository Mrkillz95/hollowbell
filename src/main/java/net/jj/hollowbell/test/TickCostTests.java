package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.BellKit.after;
import static net.jj.hollowbell.test.BellKit.bell;
import static net.jj.hollowbell.test.BellKit.done;
import static net.jj.hollowbell.test.BellKit.player;
import static net.jj.hollowbell.test.BellKit.spot;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.solid.Solid;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a fight costs the server each tick: him at full size, hunting a player under him, another player standing
 * on his dome, and the same moves at the same times each run. The time spent on him (wall and CPU) and in the solid
 * kit is logged (the numbers to compare when making him cheaper) and must stay well inside a tick. Also how long his
 * pose takes to work out (the client does it every frame he's drawn). Run on its own: -Ponly=TickCostTests.
 */
public class TickCostTests implements FabricGameTest {
    static final int TICKS = Integer.getInteger("hollowbell.tickCostTicks", 400);

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 4000, batch = "tick_cost")
    public void aFullSizeFightStaysCheapOnTheServer(GameTestHelper h) {
        boolean grief = HollowbellConfig.V.griefing;
        HollowbellConfig.V.griefing = false;
        BlockPos at = spot(h, 90, BellKit.chunksFor(1f));
        HollowbellEntity e = bell(h, at, 1f);
        e.setVariant(HollowbellEntity.HUNTER);
        List<ServerPlayer> ps = new ArrayList<>();
        long[] mark = new long[3];
        after(h, 60, () -> {
            Vec3 under = new Vec3(e.getX() + 20, e.groundAt(e.getX() + 20, e.getZ()), e.getZ() + 5);
            ServerPlayer front = player(h, under);
            front.getAbilities().invulnerable = true;
            front.setInvulnerable(true);
            ps.add(front);
            Vec3 top = BellKit.onDome(e, 30, 10);
            if (top != null) {
                ServerPlayer up = player(h, top.add(0, 0.02, 0));
                up.getAbilities().invulnerable = true;
                up.setInvulnerable(true);
                ps.add(up);
            }
            e.sendAfter(front);
            HollowbellEntity.tickNanos = 0;
            HollowbellEntity.tickCpuNanos = 0;
            HollowbellEntity.ticksTimed = 0;
            HollowbellEntity.posesWorked = 0;
            HollowbellEntity.timing = true;
            Solid.nanosServer = 0;
            Solid.queries = 0;
            mark[0] = System.nanoTime();
            int[] moves = {Moves.SLAM, Moves.PULSE, Moves.SWEEP, Moves.LASH};
            for (int i = 0; 10 + i * 95 < TICKS - 20; i++) {
                int mv = moves[i % moves.length];
                after(h, 10 + i * 95, () -> {
                    e.moves().restNow();
                    e.forceMove(mv, front);
                });
            }
            // (the kept pose is checked against one worked out afresh every few ticks: only cheaper, never different)
            int[] stale = {0, 0};
            for (int t = 3; t < TICKS; t += 7) after(h, t, () -> { stale[1]++; if (!e.keptPoseIsFresh()) stale[0]++; });
            after(h, TICKS, () -> {
                HollowbellEntity.timing = false;
                int ticks = Math.max(1, HollowbellEntity.ticksTimed);
                double him = HollowbellEntity.tickNanos / 1e6 / ticks;
                double himCpu = HollowbellEntity.tickCpuNanos / 1e6 / ticks;
                double solid = Solid.nanosServer / 1e6 / TICKS;
                double wall = (System.nanoTime() - mark[0]) / 1e6 / TICKS;
                double poses = HollowbellEntity.posesWorked / (double) ticks;
                // his pose, as the client works it out to draw him each frame
                Matrix4f[] pose = e.rig.newPose();
                e.fillState(0.5f);
                for (int i = 0; i < 300; i++) e.rig.computePose(e.state, pose);
                long t0 = System.nanoTime();
                for (int i = 0; i < 1000; i++) e.rig.computePose(e.state, pose);
                double poseUs = (System.nanoTime() - t0) / 1e3 / 1000;
                // (what the solid kit works out each frame you stand on him: only his solid parts)
                Matrix4f[] part = e.rig.newPose();
                for (int i = 0; i < 300; i++) e.solidPoseAt(0.5f, part);
                t0 = System.nanoTime();
                for (int i = 0; i < 1000; i++) e.solidPoseAt(0.5f, part);
                double solidPoseUs = (System.nanoTime() - t0) / 1e3 / 1000;
                t0 = System.nanoTime();
                for (int i = 0; i < 1000; i++) e.fillState(0.5f);
                double fillUs = (System.nanoTime() - t0) / 1e3 / 1000;
                HollowbellMod.LOG.info(String.format(Locale.ROOT,
                        "TICK COST: him %.3f ms a tick (CPU %.3f) over %d ticks, solid kit %.3f ms a tick (%d columns looked up),"
                                + " his pose worked out %.2f times a tick, the whole test server %.1f ms a tick by the clock; %d players."
                                + " One pose: %.1f us, filling it in %.1f us, his solid parts only %.1f us",
                        him, himCpu, ticks, solid, Solid.queries, poses, wall, ps.size(), poseUs, fillUs, solidPoseUs));
                HollowbellConfig.V.griefing = grief;
                try {
                    h.assertTrue(!e.isRemoved(), "he's still there");
                    h.assertTrue(him < 25, "he takes well under a tick: " + him + " ms");
                    h.assertTrue(stale[0] == 0, "his kept pose differed from a fresh one " + stale[0] + " of " + stale[1]);
                } finally {
                    done(h, e, at, BellKit.chunksFor(1f), ps.toArray(new ServerPlayer[0]));
                }
            });
        });
    }
}
