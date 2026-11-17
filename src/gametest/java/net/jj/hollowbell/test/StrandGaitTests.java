package net.jj.hollowbell.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.rig.BellRig;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * His strands and arms are what he walks on: drifting and turning low over uneven ground, dragging their ends over it,
 * they go no jerkier than over level ground. Measured as the most any joint's step changes from one tick to the next
 * (the client draws straight between ticks, so this is what shows), with the ground looked at, and what lies on it
 * fitted to its top, as he does it in the game. Before, every few ticks the ground under a strand's end was looked at
 * again and the end jumped up a step at once, and each fitting dropped or lifted what lay on the ground by a block.
 */
public class StrandGaitTests implements FabricGameTest {
    /** the most any joint of any chain jerked, drifting and turning over this land */
    static float worstJerk(BellMotion.Land land) {
        BellMotion b = new BellMotion();
        b.land = land;
        BellRig rig = BellMotion.RIG;
        int nc = rig.chains.length;
        float[][] p1 = new float[nc][], p2 = new float[nc][];
        float worst = 0f;
        for (int i = 0; i < 520; i++) {
            float a = i < 220 ? 0f : (i - 220) * 0.02f, v = 0.3f * Math.min(1f, i / 60f);
            b.vx = v * (float) Math.cos(a);
            b.vz = v * (float) Math.sin(a);
            b.step();
            for (int c = 0; c < nc; c++) {
                float[] q = b.anim.now().chain[c];
                if (p2[c] != null && i > 160)
                    for (int k = 0; k < q.length; k += 3) {
                        float jx = q[k] - 2 * p1[c][k] + p2[c][k],
                                jy = q[k + 1] - 2 * p1[c][k + 1] + p2[c][k + 1],
                                jz = q[k + 2] - 2 * p1[c][k + 2] + p2[c][k + 2];
                        worst = Math.max(worst, (float) Math.sqrt(jx * jx + jy * jy + jz * jz));
                    }
                p2[c] = p1[c];
                p1[c] = q.clone();
            }
        }
        return worst;
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "strand_gait")
    public void strandsDraggedOverUnevenGroundGoNoJerkierThanOverLevel(GameTestHelper h) {
        StringBuilder bad = new StringBuilder();
        // (low enough that his strand ends drag on the ground, and lower still, lying along it)
        for (float under : new float[] {2f, -6f}) {
            float flat = worstJerk(BellMotion.flat(under)), rough = worstJerk(BellMotion.rough(under, 0.3f));
            HollowbellMod.LOG.info("strand gait, {} under: level {} uneven {}", under, flat, rough);
            if (rough > flat * 1.75f)
                bad.append(String.format("%.0f under: %.2f over uneven ground, %.2f over level; ", under, rough, flat));
        }
        h.assertTrue(bad.isEmpty(), bad.toString());
        h.succeed();
    }

    /**
     * the most any joint of any strand jerked through a whole move (and 10 ticks after), his strand ends this far over
     * the ground (lying on it when under is small)
     */
    static float moveJerk(int move, float under) {
        BellMotion b = new BellMotion();
        b.land = BellMotion.flat(under);
        b.tx = 40f;
        b.ty = -under;
        b.tz = 10f;
        b.run(80);
        b.move = move;
        b.arg = BellMotion.argFor(move);
        BellRig rig = BellMotion.RIG;
        int nc = rig.chains.length, len = Moves.length(move);
        float[][] p1 = new float[nc][], p2 = new float[nc][];
        float worst = 0f;
        for (int t = 0; t <= len + 10; t++) {
            b.moveT = t;
            if (t >= len) {
                b.move = Moves.NONE;
                b.moveT = 0;
            }
            b.step();
            for (int c = 0; c < nc; c++) {
                if (rig.chains[c].arm) continue;
                float[] q = b.anim.now().chain[c];
                if (p2[c] != null)
                    for (int k = 0; k < q.length; k += 3) {
                        float jx = q[k] - 2 * p1[c][k] + p2[c][k],
                                jy = q[k + 1] - 2 * p1[c][k + 1] + p2[c][k + 1],
                                jz = q[k + 2] - 2 * p1[c][k + 2] + p2[c][k + 2];
                        worst = Math.max(worst, (float) Math.sqrt(jx * jx + jy * jy + jz * jz));
                    }
                p2[c] = p1[c];
                p1[c] = q.clone();
            }
        }
        return worst;
    }

    /**
     * Strands lying along the ground through a move (the sweep dragging them across it, the drop coming down on
     * them, the arm storm) jerk little more than hanging free in the water. Before, a strand on the ground was bent
     * straight back to what its joints allow in one go, and its end flicked tens of blocks in a tick (tip jolt: the
     * sweep 57 and the drop 80 on the ground, now about 8 and 13, as much as the same moves hanging free).
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "strand_gait")
    public void strandsLyingOnTheGroundThroughAMoveDoNotFlick(GameTestHelper h) {
        StringBuilder bad = new StringBuilder(), log = new StringBuilder();
        for (int m = 1; m < Moves.NAMES.length; m++) {
            float ground = moveJerk(m, 14f), free = moveJerk(m, 400f);
            log.append(String.format(" %s %.1f/%.1f", Moves.NAMES[m], ground, free));
            if (ground > Math.max(GROUND_JERK_X * free, free + GROUND_JERK_PLUS))
                bad.append(String.format("%s: %.1f on the ground, %.1f free; ", Moves.NAMES[m], ground, free));
        }
        HollowbellMod.LOG.info("strand jerk in moves, on the ground/free:{}", log);
        h.assertTrue(bad.isEmpty(), bad.toString());
        h.succeed();
    }

    static final float GROUND_JERK_X = 3f, GROUND_JERK_PLUS = 6f;
}
