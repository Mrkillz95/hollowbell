package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * He floats by design, but at a height he takes from the real ground, never from himself: sent round and round in
 * tight circles for over a minute, his strands trailing in under his bell, he keeps the same height over the ground
 * as when he started, and never ratchets up and up (the Furrowmaw's "he can fly by walking on himself").
 */
public class SelfStandTests implements FabricGameTest {

    static void turning(GameTestHelper h, float scale, int slot) {
        HollowbellEntity e = spawnAway(h, scale, HollowbellEntity.CALM, slot);
        e.setInvulnerable(true);
        int start = 100, settled = 500, end = 1700;
        double[] early = {-Double.MAX_VALUE}, late = {-Double.MAX_VALUE, Double.MAX_VALUE};
        int[] when = new int[1];
        Vec3[] mid = new Vec3[1];
        double[] went = {0};
        Vec3[] prev = new Vec3[1];
        h.runAfterDelay(start - 10, () -> mid[0] = e.position());
        // keep him going round and round a tight circle (his bell has no front to turn: the goal runs round the
        // circle a little ahead of him), so his strands keep trailing in under him
        double rad = 8 + 20 * scale;
        for (int t = start; t < end; t += 10)
            h.runAfterDelay(t, () -> {
                if (mid[0] == null) return;
                double a = Math.atan2(e.getZ() - mid[0].z, e.getX() - mid[0].x) + 0.9;
                e.setGoal(new Vec3(mid[0].x + Math.cos(a) * rad, e.getY(), mid[0].z + Math.sin(a) * rad));
            });
        for (int t = start; t <= end; t++) {
            int tt = t;
            h.runAfterDelay(t, () -> {
                if (prev[0] != null) went[0] += Math.hypot(e.getX() - prev[0].x, e.getZ() - prev[0].z);
                prev[0] = e.position();
                double over = e.getY() - e.groundAt(e.getX(), e.getZ());
                if (tt < settled) early[0] = Math.max(early[0], over);
                else {
                    if (over > late[0]) {
                        late[0] = over;
                        when[0] = tt;
                    }
                    late[1] = Math.min(late[1], over);
                }
                if (tt % 200 == 0)
                    HollowbellMod.LOG.info(String.format(
                            "selfstand %.2f: t %d %.2f over the ground, went %.1f", scale, tt, over, went[0]));
            });
        }
        h.runAfterDelay(end + 2, () -> {
            String msg = String.format(
                    "scale %.2f: at most %.2f over the ground in the first %d ticks; after, between %.2f and %.2f"
                            + " (highest at t %d); went %.1f round a circle of radius %.1f",
                    scale, early[0], settled - start, late[1], late[0], when[0], went[0], rad);
            HollowbellMod.LOG.info("selfstand: " + msg);
            release(h, e);
            h.assertTrue(went[0] > 2 * Math.PI * rad, "he never went round: " + msg);
            h.assertTrue(
                    late[0] < early[0] + 2.0 + 4.0 * scale, "turning round he rose up and up: " + msg);
            h.assertTrue(late[0] < 110 * scale + 40, "he went up into the sky: " + msg);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1900, batch = "selfstand_small")
    public void turningRoundHeNeverRisesOnHimselfSmall(GameTestHelper h) {
        turning(h, 0.1f, 961);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1900, batch = "selfstand_big")
    public void turningRoundHeNeverRisesOnHimself(GameTestHelper h) {
        turning(h, 0.4f, 963);
    }
}
