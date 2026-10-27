package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The arm wrap reaches down to what stands on the ground under his rim, takes hold of it, hoists it out from under
 * him where it can be seen (not hidden among his strands, not flung far off), squeezes it and lets it go.
 */
public class WrapTests implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "wrap")
    public void theWrapHoistsItOutAtHalfSize(GameTestHelper h) {
        wrapCase(h, 0.5f, 781);
    }

    private static void wrapCase(GameTestHelper h, float size, int slot) {
        HollowbellEntity e = spawnAway(h, size, HollowbellEntity.CALM, slot);
        double[] up = {Double.MAX_VALUE, -Double.MAX_VALUE};
        Ravager[] p = {null};
        double[] far = {Double.MAX_VALUE, 0};
        int[] samples = {0};
        boolean[] held = {false};
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            e.setCruise(0);
        });
        h.runAfterDelay(140, () -> {
            Vec3 x = e.toWorld(new Vector3f(1, 0, 0)).subtract(e.position()).normalize();
            Vec3 at = e.position().add(x.scale(e.bellRadius() * 0.5));
            // (something that lives through every squeeze, so it is held the whole time)
            p[0] = EntityType.RAVAGER.create(h.getLevel());
            p[0].moveTo(at.x, e.groundAt(at.x, at.z), at.z, 0f, 0f);
            p[0].setNoAi(true);
            p[0].setPersistenceRequired();
            p[0].getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);
            p[0].setHealth(2000);
            h.getLevel().addFreshEntity(p[0]);
            e.setTarget(p[0]);
            h.assertTrue(e.forceMove(Moves.WRAP, p[0]), "could not start the wrap");
        });
        h.onEachTick(() -> {
            if (p[0] == null || e.moveNow() != Moves.WRAP) return;
            float t = e.moveT(0f);
            if (e.moves().wrapped() == p[0]) held[0] = true;
            // (once it is hoisted: the last part of the hold)
            if (t >= Moves.WRAP_REACH + 50
                    && t < Moves.WRAP_REACH + Moves.WRAP_HOLD
                    && e.moves().wrapped() == p[0]) {
                samples[0]++;
                double d = e.horiz(p[0].position()) / e.bellRadius();
                far[0] = Math.min(far[0], d);
                far[1] = Math.max(far[1], d);
                double over = p[0].getY() - e.groundAt(p[0].getX(), p[0].getZ());
                up[0] = Math.min(up[0], over);
                up[1] = Math.max(up[1], over);
            }
        });
        h.runAfterDelay(140 + Moves.length(Moves.WRAP) + 4, () -> {
            HollowbellMod.LOG.info(
                    "wrap {}: held {}, out {} to {} bell radii, {} to {} over the ground (rim {}), hurt {}",
                    size,
                    held[0],
                    far[0],
                    far[1],
                    up[0],
                    up[1],
                    e.rig.rimY * size,
                    p[0].getHealth() < p[0].getMaxHealth());
            h.assertTrue(held[0], "the wrap never took hold of what stood under his rim");
            h.assertTrue(samples[0] > 20, "let go too soon");
            h.assertTrue(far[0] > 1.0, "held at " + far[0] + " bell radii: hidden in under him");
            h.assertTrue(far[1] < 2.2, "held out at " + far[1] + " bell radii: too far off");
            h.assertTrue(up[0] > 2, "held " + up[0] + " over the ground: dragged along it or in it");
            h.assertTrue(p[0].getVehicle() == null, "still held after the wrap");
            h.assertTrue(!p[0].isAlive() || p[0].getHealth() < p[0].getMaxHealth(), "the squeezes did nothing");
            p[0].discard();
            release(h, e);
            h.succeed();
        });
    }
}
