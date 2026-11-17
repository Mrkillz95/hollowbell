package net.jj.hollowbell.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.HollowbellMod;
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
}
