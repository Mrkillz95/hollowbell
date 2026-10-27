package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.pig;
import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The strand sweep hits what the swinging strands pass through, as they are drawn: pigs all round him, near and
 * far, are struck only where a strand came through them.
 */
public class SweepHitTests implements FabricGameTest {

    private static final int START = 140;

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "sweep_hits")
    public void theSweepHitsWhereTheStrandsSwing(GameTestHelper h) {
        float size = 0.3f;
        HollowbellEntity e = spawnAway(h, size, HollowbellEntity.CALM, 780);
        List<Pig> pigs = new ArrayList<>();
        List<Double> nearest = new ArrayList<>();
        List<Vec3> home = new ArrayList<>();
        List<Float> hurtAt = new ArrayList<>();
        Vec3[][][] last = {null};
        // (down low as in a fight, his strand ends on the ground)
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            e.setCruise(0);
        });
        h.runAfterDelay(START, () -> {
            HollowbellMod.LOG.info("sweep: strand ends {} over the ground", e.getY() - e.groundAt(e.getX(), e.getZ()));
            Vec3 x = e.toWorld(new Vector3f(1, 0, 0)).subtract(e.position()).normalize();
            Vec3 z = new Vec3(-x.z, 0, x.x);
            double bell = e.bellRadius();
            // rings of pigs, from under his rim out past where his strands can reach
            for (double f : new double[] {0.7, 1.0, 1.25, 1.5})
                for (int a = 0; a < 8; a++) {
                    double ang = a * Math.PI / 4;
                    Vec3 p = e.position().add(x.scale(Math.cos(ang) * bell * f)).add(z.scale(Math.sin(ang) * bell * f));
                    pigs.add(pig(h, new Vec3(p.x, e.groundAt(p.x, p.z), p.z)));
                    nearest.add(Double.MAX_VALUE);
                    hurtAt.add(-1f);
                    home.add(pigs.getLast().position());
                }
            // what he sweeps at stands near under him, as it does in a fight (he comes over it)
            Vec3 p = e.position().add(x.scale(-bell * 0.3));
            pigs.add(pig(h, new Vec3(p.x, e.groundAt(p.x, p.z), p.z)));
            nearest.add(Double.MAX_VALUE);
            hurtAt.add(-1f);
            home.add(pigs.getLast().position());
            e.setTarget(pigs.getLast());
            h.assertTrue(e.forceMove(Moves.SWEEP, pigs.getLast()), "could not start the sweep");
        });
        h.onEachTick(() -> {
            // (the ticks the server sweeps the strands over for its hits)
            float t = e.moveT(0f);
            for (int i = 0; i < pigs.size(); i++) {
                Pig p = pigs.get(i);
                if (hurtAt.get(i) < 0 && (!p.isAlive() || p.getHealth() < p.getMaxHealth())) hurtAt.set(i, t);
            }
            if (e.moveNow() != Moves.SWEEP || t < 20 || t > 42) {
                last[0] = null;
                return;
            }
            Vec3[][] now = new Vec3[e.rig.strands.length][];
            for (int k = 0; k < now.length; k++) now[k] = LashHitTests.line(e, k);
            if (last[0] != null)
                for (int i = 0; i < pigs.size(); i++) {
                    // (from where it stood: a pig struck is flung off)
                    double d = nearest.get(i);
                    for (int k = 0; k < now.length; k++) d = Math.min(d, nearest(last[0][k], now[k], home.get(i)));
                    nearest.set(i, d);
                }
            last[0] = now;
        });
        h.runAfterDelay(START + Moves.length(Moves.SWEEP) + 4, () -> {
            double r = 3 * size + 1.5;
            int struck = 0, spared = 0;
            String wrong = null;
            for (int i = 0; i < pigs.size(); i++) {
                Pig p = pigs.get(i);
                // (once it's over, a pig the strands drag over is stung by them as by any strand it touches)
                boolean hit = hurtAt.get(i) >= 0 && hurtAt.get(i) <= 44;
                double d = nearest.get(i);
                HollowbellMod.LOG.info(
                        "sweep: pig {} nearest a strand came {} (reach {}), hit {} at {}", i, d, r, hit, hurtAt.get(i));
                if (d < r - 0.3 && !hit) wrong = "the strands swept through pig " + i + " but it wasn't hit";
                if (d > r + 1.5 && hit) wrong = "pig " + i + " was hit, no strand came nearer than " + d;
                if (hit) struck++;
                else spared++;
                p.discard();
            }
            h.assertTrue(wrong == null, wrong);
            h.assertTrue(hurtAt.getLast() >= 0, "the sweep missed what it was swung at");
            h.assertTrue(struck > 0, "the sweep hit nothing");
            h.assertTrue(spared > 0, "the sweep hit every pig round him, even where no strand swung");
            release(h, e);
            h.succeed();
        });
    }

    /** how near a strand moving from was to now came to a pig standing at c (its feet, middle or head) */
    static double nearest(Vec3[] was, Vec3[] now, Vec3 c) {
        double best = Double.MAX_VALUE;
        Vec3[] pts = {c, c.add(0, 0.45, 0), c.add(0, 0.9, 0)};
        for (int k = 0; k <= 4; k++) {
            double f = k / 4.0;
            for (int i = 0; i + 1 < now.length; i++) {
                Vec3 a = was[i].lerp(now[i], f), b = was[i + 1].lerp(now[i + 1], f);
                for (Vec3 q : pts) best = Math.min(best, LashHitTests.toSegment(q, a, b) - 0.45);
            }
        }
        return best;
    }
}
