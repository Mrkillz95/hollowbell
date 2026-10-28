package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.BellKit.after;
import static net.jj.hollowbell.test.BellKit.chunksFor;
import static net.jj.hollowbell.test.BellKit.bell;
import static net.jj.hollowbell.test.BellKit.done;
import static net.jj.hollowbell.test.BellKit.spot;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.entity.Giants;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.solid.GiantHull;
import net.jj.hollowbell.solid.Solid;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.phys.Vec3;

/**
 * Giant against giant: two giants never pass through each other. His hull (what the other giants read of him: his
 * bell, rim and crown as his body, his arms as limbs) is where his body is; two of him drifted into each other stay
 * out of each other; a giant from "another mod" (a stand-in with no hull of its own) put in his way is never inside
 * his body for long.
 */
public class GiantHullTests implements FabricGameTest {
    static final float SZ = 0.15f;

    /** how many of a's body balls (the middles of its well-filled bits) are inside b's real solid body */
    static int bodyIn(HollowbellEntity a, HollowbellEntity b) {
        double[] h = a.jjHull();
        Solid.Runs r = new Solid.Runs();
        int n = 0;
        for (int i = 0; i < h.length; i += GiantHull.STRIDE) {
            if (h[i + 5] != GiantHull.BODY) continue;
            double y = (h[i + 3] + h[i + 4]) / 2;
            Solid.column(b, h[i], h[i + 1], y - 0.05, y + 0.05, false, r);
            if (r.n > 0) n++;
        }
        return n;
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "giant_hull_shape")
    public void hisHullIsWhereHisBellAndArmsAre(GameTestHelper h) {
        BlockPos at = spot(h, 60, chunksFor(0.5f));
        HollowbellEntity e = bell(h, at, 0.5f);
        after(h, 20, () -> {
            e.setStay(true);
            double[] hull = e.jjHull();
            h.assertTrue(hull.length > 0 && hull.length % GiantHull.STRIDE == 0, "no hull");
            int body = 0, limb = 0, off = 0, offBody = 0;
            float s = e.bellScale();
            for (int i = 0; i < hull.length; i += GiantHull.STRIDE) {
                double mid = (hull[i + 3] + hull[i + 4]) / 2;
                if (hull[i + 5] == GiantHull.BODY) {
                    body++;
                    // (the bell, rim and crown: over his rim's height, give or take a ball)
                    if (mid < e.getY() + (e.rig.rimY - 15) * s) off++;
                } else limb++;
                // every ball is on him: some of him right there
                Solid.Runs r = Solid.column(e, hull[i], hull[i + 1], hull[i + 3] - 1, hull[i + 4] + 1, false, new Solid.Runs());
                if (r.n == 0) { off++; if (hull[i + 5] == GiantHull.BODY) offBody++; }
            }
            HollowbellMod.LOG.info("Hollowbell hull: {} body columns, {} limb columns, {} off him ({} of them body)", body, limb, off, offBody);
            h.assertTrue(body > 20 && limb > 20, "body " + body + ", limbs " + limb);
            // (a ball over a curved bit of his dome or a lobe of an arm can sit just off the blocks under its middle)
            h.assertTrue(off <= (body + limb) / 10, off + " of his hull's columns aren't on him");
            h.assertTrue(GiantHull.shows(e), "the other giants can read his hull");
            done(h, e, at, chunksFor(0.5f));
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1500, batch = "giant_hull")
    public void twoOfHimDriftedIntoEachOtherNeverGoInside(GameTestHelper h) {
        BlockPos at = spot(h, 61, chunksFor(SZ) + 4);
        HollowbellEntity a = bell(h, at.offset(-25, 0, 0), SZ);
        HollowbellEntity b = bell(h, at.offset(25, 0, 0), SZ);
        double[] worst = {0, 0};
        int[] in = {0, 0};
        double[] gap0 = {0};
        boolean[] on = {false};
        after(h, 20, () -> {
            double[] ha = a.jjHull(), hb = b.jjHull();
            h.assertTrue(ha.length > 0 && hb.length > 0, "no hull");
            gap0[0] = GiantHull.gap(hb, ha);
            h.assertTrue(gap0[0] > 2, "they started in each other: gap " + gap0[0]);
            a.setStay(true);
            // he drifts straight through where the other hangs, and on
            b.setGoal(Vec3.atBottomCenterOf(at.offset(-70, 0, 0)));
            on[0] = true;
        });
        h.onEachTick(() -> {
            if (!on[0] || a.isRemoved() || b.isRemoved()) return;
            worst[0] = Math.max(worst[0], GiantHull.overlap(b.jjHull(), a.jjHull()));
            worst[1] = Math.max(worst[1], GiantHull.overlap(a.jjHull(), b.jjHull()));
            // (kept going his way: reaching where he was sent, he'd stop)
            if (b.goal() == null) b.setGoal(Vec3.atBottomCenterOf(at.offset(-70, 0, 0)));
        });
        for (int k = 1; k <= 30; k++)
            after(h, 20 + k * 40, () -> {
                in[0] += bodyIn(b, a);
                in[1] += bodyIn(a, b);
            });
        after(h, 20 + 1220, () -> {
            double gap = GiantHull.gap(b.jjHull(), a.jjHull());
            String what = String.format("deepest overlap %.2f / %.2f blocks, body bits inside the other %d / %d, gap at the end %.2f (from %.1f),"
                    + " pushed %.1f / %.1f blocks", worst[0], worst[1], in[0], in[1], gap, gap0[0], a.giantPushed, b.giantPushed);
            HollowbellMod.LOG.info("Giant against giant (two of him): {}", what);
            h.assertTrue(worst[0] < 1.0 && worst[1] < 1.0, "they went into each other: " + what);
            h.assertTrue(in[0] + in[1] <= 8, "bits of one were inside the other's real body: " + what);
            h.assertTrue(gap < 3, "he never got up to the other one: " + what);
            done(h, a, at, chunksFor(SZ) + 4, b);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "giant_hull_other")
    public void anotherModsGiantInHisWayIsntLeftInsideHim(GameTestHelper h) {
        BlockPos at = spot(h, 62, chunksFor(0.3f));
        HollowbellEntity e = bell(h, at, 0.3f);
        Giant[] g = new Giant[1];
        double[] worst = {0};
        boolean[] on = {false};
        after(h, 20, () -> {
            e.setStay(true);
            // a stand-in for another mod's giant without a hull of its own: a giant zombie, tagged as a giant, put
            // right where one of his arms hangs
            double[] hull = e.jjHull();
            int low = -1;
            for (int i = 0; i < hull.length; i += GiantHull.STRIDE)
                if (hull[i + 5] == GiantHull.LIMB && (low < 0 || hull[i + 3] < hull[low + 3])) low = i;
            h.assertTrue(low >= 0, "no arms in his hull");
            Vec3 arm = new Vec3(hull[low], hull[low + 3], hull[low + 1]);
            g[0] = EntityType.GIANT.create(h.getLevel());
            g[0].moveTo(arm.x, e.groundAt(arm.x, arm.z), arm.z, 0f, 0f);
            g[0].setNoAi(true);
            g[0].setPersistenceRequired();
            g[0].addTag(Giants.TAG);
            g[0].getAttribute(Attributes.MAX_HEALTH).setBaseValue(100000);
            g[0].setHealth(100000);
            h.getLevel().addFreshEntity(g[0]);
            on[0] = true;
        });
        after(h, 40, () -> h.assertTrue(e.giantPushed > 0, "he didn't move out of the other giant at all"));
        after(h, 300, () -> {
            double now = GiantHull.overlap(e.jjHull(), GiantHull.of(g[0], Giants.TAG));
            HollowbellMod.LOG.info("Giant against giant (stand-in): moved {} blocks, overlap now {}", e.giantPushed, now);
            h.assertTrue(now < 0.5, "his body is still in the other giant by " + now);
            done(h, e, at, chunksFor(0.3f), g[0]);
        });
    }
}
