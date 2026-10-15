package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.*;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Flying, moving smoothly, and every size. */
public class FlightTests implements FabricGameTest {
    /** the biggest change in his speed from one tick to the next, and whether he ever went into the ground */
    private static final class Watch {
        Vec3 lastV;
        double maxDv, worstDip = 1e9;
        int ticks;

        void see(HollowbellEntity e) {
            Vec3 v = e.velocity();
            if (lastV != null) maxDv = Math.max(maxDv, v.subtract(lastV).length());
            lastV = v;
            // his rim above the ground under his middle
            worstDip =
                    Math.min(worstDip, e.getY() + (e.rig.rimY - 10) * e.bellScale() - e.groundAt(e.getX(), e.getZ()));
            ticks++;
        }
    }

    private static void watch(GameTestHelper h, HollowbellEntity e, Watch w, int from, int to) {
        for (int t = from; t < to; t++) h.runAfterDelay(t, () -> w.see(e));
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1100, batch = "fly")
    public void heFliesUpToWhatIsUpHighAndSinksBackDown(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.HUNTER, 80);
        Pig[] p = new Pig[1];
        double[] y0 = new double[1];
        Watch w = new Watch();
        h.runAfterDelay(20, () -> {
            y0[0] = e.getY();
            // a pig high up in the air, right over him
            p[0] = pig(h, e.position().add(0, 45, 0));
            p[0].setNoGravity(true);
            p[0].setInvulnerable(true);
            e.sendAfter(p[0]);
        });
        watch(h, e, w, 21, 1000);
        h.runAfterDelay(460, () -> {
            h.assertTrue(
                    e.getY() > y0[0] + 30,
                    "he didn't fly up to the pig: from " + y0[0] + " to " + e.getY() + " (pig at " + p[0].getY() + ")");
            // down it comes, to the ground
            p[0].teleportTo(p[0].getX(), e.groundAt(p[0].getX(), p[0].getZ()), p[0].getZ());
        });
        h.runAfterDelay(1000, () -> {
            h.assertTrue(
                    e.getY() < y0[0] + 8,
                    "he didn't come back down after the pig: at " + e.getY() + " ground "
                            + e.groundAt(e.getX(), e.getZ()));
            // smooth: no sudden change of speed, and never into the ground
            h.assertTrue(w.maxDv < 0.06, "his speed jumped by " + w.maxDv + " in one tick");
            h.assertTrue(w.worstDip > 0, "his rim went into the ground by " + (-w.worstDip));
            p[0].discard();
            release(h, e);
            h.succeed();
        });
    }

    /**
     * Watches every arm and strand joint and his crown, tick by tick. A part may move fast (a slam is fast), but
     * nothing may jump: the change in its speed from one tick to the next (its acceleration) stays small, and it
     * never moves far further than he does.
     */
    private static final class PoseWatch {
        float[][] last, last2;
        Vector3f lastCrown, lastCrown2;
        float worstJerk, worstStep;
        /** the drop landing: it's meant to stop him dead, as a knock does */
        boolean landing;

        String where = "", whereStep = "";
        final List<float[][]> hist = new ArrayList<>();
        int wc = -1, wi, wt, tick;
        final List<String> body = new ArrayList<>();

        String trail() {
            if (wc < 0) return "";
            StringBuilder b = new StringBuilder(" path:");
            for (int t = Math.max(0, wt - 5); t < Math.min(hist.size(), wt + 3); t++) {
                float[] q = hist.get(t)[wc];
                b.append(String.format(" [%.1f %.1f %.1f | %s]", q[wi], q[wi + 1], q[wi + 2], body.get(t)));
            }
            return b.toString();
        }

        void see(HollowbellEntity e, String when) {
            e.ensurePose();
            var st = e.state;
            Vector3f crown = e.rig.at(e.pose, e.rig.crownBone, new Vector3f(0, e.rig.crownY, 0));
            if (last2 != null) {
                // his own acceleration and step, taken off
                Vector3f ca = new Vector3f(crown).sub(lastCrown).sub(new Vector3f(lastCrown).sub(lastCrown2));
                float cstep = crown.distance(lastCrown);
                for (int c = 0; c < st.chain.length; c++) {
                    float[] a = last2[c], b = last[c], n = st.chain[c];
                    for (int i = 0; i < n.length; i += 3) {
                        float jx = n[i] - 2 * b[i] + a[i] - ca.x,
                                jy = n[i + 1] - 2 * b[i + 1] + a[i + 1] - ca.y,
                                jz = n[i + 2] - 2 * b[i + 2] + a[i + 2] - ca.z;
                        float j = (float) Math.sqrt(jx * jx + jy * jy + jz * jz);
                        // (hitting the ground, or his bell coming down on it, stops a part short: a knock, not a jump)
                        if (e.chainKnocked(c)) continue;
                        if (!landing && j > worstJerk) {
                            worstJerk = j;
                            wc = c;
                            wi = i;
                            wt = tick;
                            where = when + " chain " + c + " (" + (e.rig.chains[c].arm ? "arm" : "strand") + ") point "
                                    + i / 3;
                        }
                        float sx = n[i] - b[i], sy = n[i + 1] - b[i + 1], sz = n[i + 2] - b[i + 2];
                        float step = (float) Math.sqrt(sx * sx + sy * sy + sz * sz) - 2 * cstep;
                        if (step > worstStep) {
                            worstStep = step;
                            whereStep = when + " chain " + c + " point " + i / 3;
                        }
                    }
                }
            }
            float[][] snap = new float[st.chain.length][];
            for (int c = 0; c < st.chain.length; c++) snap[c] = st.chain[c].clone();
            hist.add(snap);
            body.add(String.format(
                    "low %.1f sq %.2f tilt %.2f,%.2f y %.2f vy %.3f",
                    st.lower, st.squeeze, st.tiltX, st.tiltZ, e.getY(), e.velocity().y));
            tick++;
            last2 = last;
            lastCrown2 = lastCrown;
            last = new float[st.chain.length][];
            for (int c = 0; c < st.chain.length; c++) last[c] = st.chain[c].clone();
            lastCrown = crown;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "no_snap")
    public void movesBlendInAndOutEvenCutOffHalfway(GameTestHelper h) {
        blendCheck(h, 81);
    }

    static void blendCheck(GameTestHelper h, int slot) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, slot);
        Pig[] p = new Pig[1];
        PoseWatch w = new PoseWatch();
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            p[0] = pig(h, under(e).add(3, 0, 0));
            p[0].setInvulnerable(true);
            h.assertTrue(e.forceMove(Moves.SLAM, p[0]), "no slam");
        });
        // cut the slam off at the top of its swing with a sweep, then the sweep off with a drop, then the drop off
        h.runAfterDelay(52, () -> h.assertTrue(e.forceMove(Moves.SWEEP, p[0]), "no sweep"));
        h.runAfterDelay(80, () -> h.assertTrue(e.forceMove(Moves.DROP, p[0]), "no drop"));
        h.runAfterDelay(140, () -> e.forceMove(Moves.CURTAIN, p[0]));
        int land = 80 + Moves.DROP_WIND + Moves.DROP_FALL;
        h.runAfterDelay(170, () -> e.moves().stopNow());
        for (int t = 21; t < 400; t++) {
            int tt = t;
            h.runAfterDelay(t, () -> {
                w.landing = tt >= land - 1 && tt <= land + 1;
                w.see(e, "tick " + tt);
            });
        }
        h.runAfterDelay(400, () -> {
            HollowbellMod.LOG.info("blend check: worst jerk {} ({}), worst step {}", w.worstJerk, w.where, w.worstStep);
            h.assertTrue(
                    w.worstJerk < 7f,
                    "a part of him lurched: its speed changed by " + w.worstJerk + " model blocks a tick in one tick ("
                            + w.where + ")" + w.trail());
            h.assertTrue(
                    w.worstStep < 30f,
                    "a part of him jumped " + w.worstStep + " model blocks in one tick (" + w.whereStep + ")");
            p[0].discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "full_size")
    public void aFullSizeOneFliesSteadily(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 1f, HollowbellEntity.CALM, 90);
        StringBuilder path = new StringBuilder();
        for (int t = 5; t < 300; t += 15) {
            int tt = t;
            h.runAfterDelay(
                    t, () -> path.append(String.format(" %d:(%.1f %.1f %.1f)", tt, e.getX(), e.getY(), e.getZ())));
        }
        h.runAfterDelay(300, () -> {
            boolean ok = Double.isFinite(e.getX())
                    && Double.isFinite(e.getY())
                    && !e.isRemoved()
                    && e.position().distanceTo(e.home()) < 200;
            h.assertTrue(ok, "he went wrong:" + path);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 700, batch = "dive_unhurt")
    public void aFullSizeSkyDiveDoesntHurtHim(GameTestHelper h) {
        // turned over at full size, the point he's measured from goes deep under the ground: that mustn't hurt him
        HollowbellEntity e = spawnAway(h, 1f, HollowbellEntity.CALM, 114);
        Pig[] p = new Pig[1];
        float[] hp = new float[1];
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            p[0] = pig(h, under(e));
            p[0].setInvulnerable(true);
            hp[0] = e.healthNow();
            h.assertTrue(e.forceMove(Moves.SKY_DIVE, p[0]), "no sky dive");
        });
        h.runAfterDelay(20 + Moves.length(Moves.SKY_DIVE) + 20, () -> {
            h.assertTrue(e.moveNow() != Moves.SKY_DIVE, "the sky dive never finished");
            h.assertTrue(
                    e.healthNow() >= hp[0] - 0.01f, "his own sky dive hurt him: " + hp[0] + " -> " + e.healthNow());
            p[0].discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "sizes")
    public void theSmallestAndTheBiggestWork(GameTestHelper h) {
        HollowbellEntity tiny = spawnAway(h, HollowbellEntity.MIN_SCALE, HollowbellEntity.HUNTER, 70);
        HollowbellEntity big = spawnAway(h, HollowbellEntity.MAX_SCALE, HollowbellEntity.HUNTER, 72);
        Pig[] p = new Pig[1];
        h.runAfterDelay(20, () -> {
            tiny.setStay(true);
            p[0] = pig(h, under(tiny));
            p[0].setInvulnerable(true);
            h.assertTrue(tiny.forceMove(Moves.GRAB, p[0]), "the tiny one can't grab");
            h.assertTrue(big.forceMove(Moves.PULSE, null), "the big one can't pulse");
        });
        h.runAfterDelay(20 + Moves.length(Moves.GRAB) + 10, () -> {
            h.assertTrue(tiny.isAlive() && big.isAlive(), "one of them died");
            // too small to have room inside: the pig is squeezed and let go
            h.assertTrue(!tiny.moves().isInside(p[0]) && !p[0].isPassenger(), "the tiny one took a pig inside");
            h.assertTrue(tiny.healthMax() >= 40f, "the tiny one has almost no health: " + tiny.healthMax());
            h.assertTrue(
                    Math.abs(big.healthMax() - BellConfig.V.health * 2f) < 1f,
                    "the big one's health: " + big.healthMax());
            p[0].discard();
            release(h, tiny);
            release(h, big);
            h.succeed();
        });
    }
}
