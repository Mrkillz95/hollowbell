package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.Belling;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * He only picks moves that can land from where the target is: nothing along the ground at a flyer, nothing short at
 * something far off, no stingers through a wall, no more Bellings while plenty are about, and still something to do
 * at one right under him.
 */
public class MoveChoiceTests implements FabricGameTest {
    private static final float S = 0.3f;
    private static final int[] GROUND = {
        Moves.SWEEP,
        Moves.SLAM,
        Moves.DROP,
        Moves.WHIRLPOOL,
        Moves.SKY_DIVE,
        Moves.ARM_STORM,
        Moves.SUN_LANCES,
        Moves.UNDERTOW,
        Moves.EGG_RAIN
    };
    private static final int[] SHORT = {
        Moves.GRAB,
        Moves.LASH,
        Moves.CURTAIN,
        Moves.SWEEP,
        Moves.SLAM,
        Moves.WRAP,
        Moves.POD_BURST,
        Moves.DROP,
        Moves.WHIRLPOOL,
        Moves.ARM_STORM,
        Moves.UNDERTOW,
        Moves.FLASH
    };

    /** a still zombie out from his middle along his x, that far over the ground */
    private static Zombie zombie(GameTestHelper h, HollowbellEntity e, double out, double up) {
        Vec3 x = e.toWorld(new Vector3f(1, 0, 0)).subtract(e.position()).normalize();
        Vec3 at = e.position().add(x.scale(out));
        Zombie z = EntityType.ZOMBIE.create(h.getLevel());
        z.moveTo(at.x, e.groundAt(at.x, at.z) + up, at.z, 0f, 0f);
        z.setNoAi(true);
        z.setNoGravity(true);
        z.setInvulnerable(true);
        h.getLevel().addFreshEntity(z);
        return z;
    }

    private static HollowbellEntity him(GameTestHelper h, int slot) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.HUNTER, slot);
        h.runAfterDelay(10, () -> {
            e.setStay(true);
            e.setCruise(0);
        });
        return e;
    }

    private static String named(int[] w, int... moves) {
        StringBuilder b = new StringBuilder();
        for (int a : moves) if (w[a] > 0) b.append(' ').append(Moves.NAMES[a]);
        return b.toString();
    }

    private static int[] weights(HollowbellEntity e, Zombie z) {
        // (under half health his heavy moves are his whenever their wait is over)
        if (e.healthNow() > e.healthMax() * 0.45f)
            e.hurt(e.damageSources().generic(), e.healthNow() - e.healthMax() * 0.45f);
        e.moves().clearCooldowns();
        return e.moves().weights(z, null);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "choice_air")
    public void nothingAlongTheGroundAtSomethingInTheAir(GameTestHelper h) {
        HollowbellEntity e = him(h, 790);
        h.runAfterDelay(100, () -> {
            Zombie z = zombie(h, e, e.bellRadius() * 0.6, 8 + 20 * S + 6);
            String wrong = named(weights(e, z), GROUND);
            h.assertTrue(wrong.isEmpty(), "ground moves at something in the air:" + wrong);
            z.discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "choice_far")
    public void nothingShortAtSomethingFarOff(GameTestHelper h) {
        HollowbellEntity e = him(h, 791);
        h.runAfterDelay(100, () -> {
            Zombie z = zombie(h, e, e.bellRadius() * 3.6, 0);
            String wrong = named(weights(e, z), SHORT);
            h.assertTrue(wrong.isEmpty(), "moves that can't reach that far:" + wrong);
            h.assertTrue(weights(e, z)[Moves.SKY_DIVE] > 0, "nothing to close the gap with");
            z.discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "choice_wall")
    public void noStingersThroughAWall(GameTestHelper h) {
        HollowbellEntity e = him(h, 792);
        h.runAfterDelay(100, () -> {
            Zombie z = zombie(h, e, e.bellRadius() * 2, 0);
            z.setNoGravity(false);
            BlockPos c = z.blockPosition();
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    for (int dy = -1; dy <= 3; dy++)
                        if (Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == -1 || dy == 3)
                            h.getLevel().setBlockAndUpdate(c.offset(dx, dy, dz), Blocks.STONE.defaultBlockState());
            h.assertTrue(weights(e, z)[Moves.VOLLEY] == 0, "stingers at something walled in");
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    for (int dy = -1; dy <= 3; dy++)
                        h.getLevel().setBlockAndUpdate(c.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
            h.assertTrue(weights(e, z)[Moves.VOLLEY] > 0, "no stingers with the wall gone");
            z.discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "choice_bellings")
    public void noMoreBellingsWhilePlentyAreAbout(GameTestHelper h) {
        HollowbellEntity e = him(h, 793);
        h.runAfterDelay(100, () -> {
            Zombie z = zombie(h, e, e.bellRadius() * 1.2, 0);
            z.setNoGravity(false);
            Belling[] b = new Belling[8];
            for (int i = 0; i < b.length; i++) {
                b[i] = ModEntities.BELLING.create(h.getLevel());
                b[i].moveTo(e.getX() + i, e.groundAt(e.getX(), e.getZ()) + 1, e.getZ(), 0f, 0f);
                b[i].setNoAi(true);
                h.getLevel().addFreshEntity(b[i]);
            }
            String wrong = named(weights(e, z), Moves.SHED, Moves.EGG_RAIN);
            h.assertTrue(wrong.isEmpty(), "more Bellings with eight about:" + wrong);
            for (Belling x : b) x.discard();
            z.discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "choice_near")
    public void somethingUnderHimGetsHisStrandsAndArms(GameTestHelper h) {
        HollowbellEntity e = him(h, 794);
        h.runAfterDelay(100, () -> {
            Zombie z = zombie(h, e, e.bellRadius() * 0.5, 0);
            z.setNoGravity(false);
            int[] w = weights(e, z);
            String close = named(w, Moves.LASH, Moves.SWEEP, Moves.SLAM, Moves.WRAP, Moves.CURTAIN, Moves.GRAB);
            h.assertTrue(close.split(" ").length > 4, "few strand and arm moves at something right under him:" + close);
            z.discard();
            release(h, e);
            h.succeed();
        });
    }
}
