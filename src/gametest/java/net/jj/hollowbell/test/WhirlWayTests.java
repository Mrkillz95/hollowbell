package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.pig;
import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.rig.BellAnim;
import net.jj.hollowbell.rig.BellRig;
import net.jj.hollowbell.rig.BellState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;

/**
 * The whirlpool turns one way: his bell spins the way it drags everything round him (and the dust drawn on the
 * ground goes round by the same Moves.WHIRL_WAY), seen from above.
 */
public class WhirlWayTests implements FabricGameTest {
    /** how far round something at c has gone about +y, seen from above (a turn about +y counts up) */
    static double round(Vec3 c, Vec3 p) {
        return Math.atan2(-(p.z - c.z), p.x - c.x);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600)
    public void hisSpinAndHisPullGoTheSameWayRound(GameTestHelper h) {
        // his bell's spin in his own space; turning him into the world never mirrors it
        BellAnim.In in = new BellAnim.In();
        BellState st = new BellState(BellRig.get());
        Moves.pose(BellRig.get(), st, in, Moves.WHIRLPOOL, 60, 0, 0f, 0f, 0f, -1f);
        float a = in.spin;
        Moves.pose(BellRig.get(), st, in, Moves.WHIRLPOOL, 80, 0, 0f, 0f, 0f, -1f);
        float spin = Math.signum(in.spin - a);
        h.assertTrue(spin == Math.signum(Moves.WHIRL_WAY), "his bell spins against WHIRL_WAY");

        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 985);
        Pig[] p = new Pig[1];
        double[] turned = {0}, last = {Double.NaN};
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            e.setCruise(0);
        });
        h.runAfterDelay(140, () -> {
            double r = e.bellRadius() * 1.4;
            Vec3 at = e.position().add(r, 0, 0);
            p[0] = pig(h, new Vec3(at.x, e.groundAt(at.x, at.z), at.z));
            // (a pig left without its own will is never moved by anything pushing it)
            p[0].setNoAi(false);
            p[0].getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);
            p[0].setHealth(p[0].getMaxHealth());
            e.setTarget(p[0]);
            h.assertTrue(e.forceMove(Moves.WHIRLPOOL, p[0]), "could not start the whirlpool");
        });
        h.onEachTick(() -> {
            if (p[0] == null || e.moveNow() != Moves.WHIRLPOOL) return;
            float t = e.moveT(0f);
            if (t <= Moves.WHIRL_UP || t >= Moves.WHIRL_CRUSH) return;
            double now = round(e.position(), p[0].position());
            if (!Double.isNaN(last[0])) {
                double d = now - last[0];
                if (d > Math.PI) d -= 2 * Math.PI;
                if (d < -Math.PI) d += 2 * Math.PI;
                turned[0] += d;
            }
            last[0] = now;
            p[0].setHealth(p[0].getMaxHealth());
        });
        h.runAfterDelay(140 + Moves.WHIRL_CRUSH + 10, () -> {
            HollowbellMod.LOG.info("whirl way: the pig went {} radians round, his spin {}", turned[0], spin);
            p[0].discard();
            release(h, e);
            h.assertTrue(
                    Math.signum(turned[0]) == spin && Math.abs(turned[0]) > 0.3,
                    "the pull took it " + turned[0] + " radians round, his bell spins " + spin);
            h.succeed();
        });
    }
}
