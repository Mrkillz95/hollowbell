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
import net.jj.hollowbell.rig.BellRig;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The strand lash hits what the whipping strand passes through, as it is drawn: what it is aimed at is struck, and
 * something on that side of him the strand never comes near is left alone.
 */
public class LashHitTests implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "lash_hits")
    public void theLashHitsWhereTheStrandWhips(GameTestHelper h) {
        float size = 0.3f;
        HollowbellEntity e = spawnAway(h, size, HollowbellEntity.CALM, 770);
        Pig[] pigs = new Pig[2];
        List<Vec3[]> lines = new ArrayList<>();
        int[] strand = {-1};
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            Vec3 out = e.toWorld(new Vector3f(1, 0, 0)).subtract(e.position()).normalize();
            double bell = e.bellRadius();
            // one where the strand nearest it hangs down to the ground, one further out on the same side
            Vec3 a = e.position().add(out.scale(bell * 0.95));
            Vec3 b = e.position().add(out.scale(bell * 1.3));
            pigs[0] = pig(h, new Vec3(a.x, e.groundAt(a.x, a.z), a.z));
            pigs[1] = pig(h, new Vec3(b.x, e.groundAt(b.x, b.z), b.z));
            e.setTarget(pigs[0]);
            h.assertTrue(e.forceMove(Moves.LASH, pigs[0]), "could not start the lash");
            strand[0] = e.moveArg();
        });
        h.onEachTick(() -> {
            // (the ticks the server sweeps the strand over for its hits)
            float t = e.moveT(0f);
            if (strand[0] >= 0 && e.moveNow() == Moves.LASH && t >= Moves.LASH_AT - 6 && t <= Moves.LASH_AT + 8)
                lines.add(line(e, strand[0]));
        });
        h.runAfterDelay(20 + Moves.length(Moves.LASH) + 4, () -> {
            double r = 3 * size + 1.5;
            for (int i = 0; i < 2; i++) {
                Pig p = pigs[i];
                boolean hit = !p.isAlive() || p.getHealth() < p.getMaxHealth();
                double d = nearest(lines, p);
                HollowbellMod.LOG.info("lash: pig {} nearest the strand came {} (reach {}), hit {}", i, d, r, hit);
                if (d < r - 0.3) h.assertTrue(hit, "the strand whipped through pig " + i + " but it wasn't hit");
                if (d > r + 1.5) h.assertTrue(!hit, "pig " + i + " was hit, the strand came no nearer than " + d);
                p.discard();
            }
            // (it whips through about where its target stands: within a block of its reach)
            h.assertTrue(nearest(lines, pigs[0]) < r + 1, "the lash never came near what it was aimed at");
            release(h, e);
            h.succeed();
        });
    }

    /** the strand from where it hangs to its end, as posed now */
    static Vec3[] line(HollowbellEntity e, int k) {
        BellRig.StrandDef sd = e.rig.strands[k];
        int[] b = sd.bones();
        Vec3[] out = new Vec3[b.length + 1];
        for (int i = 0; i < b.length; i++) out[i] = e.boneWorld(b[i], sd.joints()[i]);
        out[b.length] = e.boneWorld(b[b.length - 1], sd.bottom());
        return out;
    }

    /** how near the strand came to the pig's feet, middle or head, between the ticks too */
    static double nearest(List<Vec3[]> lines, Pig p) {
        double best = Double.MAX_VALUE;
        Vec3 c = p.position();
        Vec3[] pts = {c, c.add(0, p.getBbHeight() * 0.5, 0), c.add(0, p.getBbHeight(), 0)};
        for (int t = 1; t < lines.size(); t++) {
            Vec3[] was = lines.get(t - 1), now = lines.get(t);
            for (int k = 0; k <= 4; k++) {
                double f = k / 4.0;
                for (int i = 0; i + 1 < now.length; i++) {
                    Vec3 a = was[i].lerp(now[i], f), b = was[i + 1].lerp(now[i + 1], f);
                    for (Vec3 q : pts) best = Math.min(best, toSegment(q, a, b) - p.getBbWidth() * 0.5);
                }
            }
        }
        return best;
    }

    static double toSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double l2 = ab.lengthSqr();
        double u = l2 < 1e-9 ? 0 : Mth.clamp(p.subtract(a).dot(ab) / l2, 0, 1);
        return p.distanceTo(a.add(ab.scale(u)));
    }
}
