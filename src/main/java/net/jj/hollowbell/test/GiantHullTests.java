package net.jj.hollowbell.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.entity.Giants;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.solid.GiantHull;
import net.jj.hollowbell.solid.Solid;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

/**
 * Giant against giant (1.10): two giants never pass through each other. Two of him drifted into each other for a
 * minute, and him going for a giant from "another mod" (a stand-in that shows no hull: its box is its body): bodies
 * stay out of each other, he comes up to it (over it: his strands hang down on it), and his blows aim at its body,
 * not its feet.
 */
public class GiantHullTests implements FabricGameTest {
    static final float SZ = 0.12f;

    static void after(GameTestHelper h, int t, Runnable r) { h.runAfterDelay(t, r); }

    /** how many of a's body balls (the middles of its well-filled bits) are inside b's real solid body */
    static int bodyIn(HollowbellEntity a, HollowbellEntity b) {
        double[] hh = a.jjHull();
        Solid.Runs r = new Solid.Runs();
        int n = 0;
        for (int i = 0; i < hh.length; i += GiantHull.STRIDE) {
            if (hh[i + 5] != GiantHull.BODY) continue;
            double y = (hh[i + 3] + hh[i + 4]) / 2;
            Solid.column(b, hh[i], hh[i + 1], y - 0.05, y + 0.05, false, r);
            if (r.n > 0) n++;
        }
        return n;
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1600, batch = "giant_hull")
    public void twoOfHimDriftedIntoEachOtherNeverGoInside(GameTestHelper h) {
        HollowbellEntity a = spawnAway(h, SZ, HollowbellEntity.CALM, 510);
        HollowbellEntity b = spawnAway(h, SZ, HollowbellEntity.CALM, 511);
        double[] worst = {0, 0}, least = {Double.MAX_VALUE};
        int[] in = {0, 0};
        boolean[] on = {false};
        boolean meet = HollowbellConfig.V.meetings;
        after(h, 5, () -> {
            HollowbellConfig.V.meetings = false;
            // the other one brought over beside him, at his height
            b.moveTo(a.getX() + 45, a.getY(), a.getZ(), b.getYRot(), 0f);
            a.setStay(true);
            b.setStay(true);
        });
        after(h, 60, () -> {
            double[] ha = a.jjHull(), hb = b.jjHull();
            h.assertTrue(ha.length > 0 && hb.length > 0, "no hull");
            h.assertTrue(GiantHull.gap(hb, ha) > 2, "they started in each other: gap " + GiantHull.gap(hb, ha));
            // he drifts straight through where the other hangs, and on
            b.setStay(false);
            b.setGoal(a.position().add(-50, 0, 0));
            on[0] = true;
        });
        // (watched each tick from the start: a tick watcher can't be added from inside another)
        h.onEachTick(() -> {
            if (!on[0] || a.isRemoved() || b.isRemoved()) return;
            double[] ha = a.jjHull(), hb = b.jjHull();
            worst[0] = Math.max(worst[0], GiantHull.overlap(hb, ha));
            worst[1] = Math.max(worst[1], GiantHull.overlap(ha, hb));
            least[0] = Math.min(least[0], GiantHull.gap(hb, ha));
        });
        for (int k = 1; k <= 30; k++) after(h, 60 + k * 40, () -> { in[0] += bodyIn(b, a); in[1] += bodyIn(a, b); });
        after(h, 60 + 1220, () -> {
            String what = String.format("deepest overlap %.2f / %.2f blocks, body bits inside the other %d / %d, nearest gap %.2f, pushed %.1f / %.1f; %d / %d columns",
                    worst[0], worst[1], in[0], in[1], least[0], a.giantPushed, b.giantPushed, a.jjHull().length / 6, b.jjHull().length / 6);
            HollowbellMod.LOG.info("Giant against giant (two of him): {}", what);
            HollowbellConfig.V.meetings = meet;
            h.assertTrue(worst[0] < 1.0 && worst[1] < 1.0, "they went into each other: " + what);
            h.assertTrue(in[0] + in[1] <= 2, "bits of one were inside the other's real body: " + what);
            h.assertTrue(least[0] < 3, "he never got up to the other one: " + what);
            h.assertTrue(a.giantPushed + b.giantPushed > 0.5, "nobody was pushed: " + what);
            release(h, b);
            release(h, a);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1600, batch = "giant_hull_standin")
    public void heFightsAnotherModsGiantByItsBodyNotItsFeet(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, SZ, HollowbellEntity.CALM, 512);
        boolean fight = HollowbellConfig.V.fightGiants;
        Giant[] g = new Giant[1];
        double[] worst = {0}, nearest = {Double.MAX_VALUE};
        boolean[] on = {false};
        after(h, 20, () -> {
            HollowbellConfig.V.fightGiants = true;
            // a stand-in for another mod's giant without a hull of its own: a giant zombie, tagged as a giant
            g[0] = EntityType.GIANT.create(h.getLevel());
            Vec3 gp = new Vec3(e.getX() + 45, e.groundAt(e.getX() + 45, e.getZ()), e.getZ());
            g[0].moveTo(gp.x, gp.y, gp.z, 90f, 0f);
            g[0].setNoAi(true);
            g[0].setPersistenceRequired();
            g[0].addTag(Giants.TAG);
            g[0].getAttribute(Attributes.MAX_HEALTH).setBaseValue(100000);
            g[0].setHealth(100000);
            h.getLevel().addFreshEntity(g[0]);
        });
        after(h, 40, () -> {
            h.assertTrue(e.fairGame(g[0]), "the stand-in giant isn't fair game");
            e.sendAfter(g[0]);
            on[0] = true;
        });
        h.onEachTick(() -> {
            if (!on[0] || e.isRemoved() || g[0] == null || g[0].isRemoved()) return;
            double[] me = e.jjHull(), it = GiantHull.of(g[0], Giants.TAG);
            worst[0] = Math.max(worst[0], GiantHull.overlap(me, it));
            nearest[0] = Math.min(nearest[0], Math.hypot(e.getX() - g[0].getX(), e.getZ() - g[0].getZ()));
        });
        after(h, 40 + 1000, () -> {
            Giant gg = g[0];
            String what = String.format("deepest into it %.2f, nearest across %.2f (his bell %.2f wide), pushed %.1f, him %.1f over its top", worst[0], nearest[0], e.bellRadius() * 2,
                    e.giantPushed, e.getY() - gg.getBoundingBox().maxY);
            h.assertTrue(nearest[0] < e.bellRadius() + gg.getBbWidth(), "he never came up to it: " + what);
            h.assertTrue(worst[0] < 1.0, "he went into it: " + what);
            // a blow: aimed at its body, not at its feet
            e.moves().restNow();
            h.assertTrue(e.forceMove(Moves.SLAM, gg), "he wouldn't slam at it");
            Vec3 aim = e.aimWorld();
            AABB box = gg.getBoundingBox();
            double cx = Math.max(box.minX - aim.x, Math.max(0, aim.x - box.maxX)), cy = Math.max(box.minY - aim.y, Math.max(0, aim.y - box.maxY)), cz = Math.max(box.minZ - aim.z, Math.max(0, aim.z - box.maxZ));
            double off = Math.sqrt(Math.max(0, cx) * Math.max(0, cx) + Math.max(0, cy) * Math.max(0, cy) + Math.max(0, cz) * Math.max(0, cz));
            double fromFeet = aim.distanceTo(gg.position());
            HollowbellMod.LOG.info("Giant against giant (stand-in): {}; aim {} blocks off its body, {} from its feet", what, off, fromFeet);
            h.assertTrue(off < 1.0 && fromFeet > 2.0, String.format("his blow aims %.2f off its body and %.2f from its feet: not at its body", off, fromFeet));
            HollowbellConfig.V.fightGiants = fight;
            gg.discard();
            release(h, e);
            h.succeed();
        });
    }
}
