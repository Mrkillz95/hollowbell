package net.jj.hollowbell.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.solid.Solid;
import net.jj.hollowbell.solid.SolidCarry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static net.jj.hollowbell.test.HollowbellGameTests.drop;
import static net.jj.hollowbell.test.HollowbellGameTests.player;
import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;
import static net.jj.hollowbell.test.SolidTests.slice;

/** His long death: whoever stands on him floats down, and the one who brought him down still gets the XP. */
public class DeathTests implements FabricGameTest {
    private static final float S = 0.3f;

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "death_credit")
    public void whoeverKilledHimGetsTheXpAndWhoeverStoodOnHimFloatsDown(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 614);
        ServerPlayer[] pl = new ServerPlayer[2];
        Vec3[] at = new Vec3[1];
        h.runAfterDelay(60, () -> {
            e.setStay(true);
            pl[0] = player(h, e.position().add(100 * S + 20, 0, 0));
            Vec3 top = e.topOfSlice(slice(e.rig, "crown", 0));
            h.assertTrue(top != null, "no spot to stand on his crown");
            pl[1] = player(h, top.add(0, 0.01, 0));
            SolidCarry c = new SolidCarry();
            c.step(pl[1], Solid.bodies(false));
            h.assertTrue(c.riding(), "the player put on his crown doesn't stand on him");
            at[0] = e.position();
            double over = pl[1].getY() - e.groundAt(pl[1].getX(), pl[1].getZ());
            boolean was = HollowbellConfig.V.oneInTheWorld;
            HollowbellConfig.V.oneInTheWorld = false;
            try {
                e.applyDamage(e.damageSources().playerAttack(pl[0]), e.healthMax() * 100f, -1, false);
            } finally {
                HollowbellConfig.V.oneInTheWorld = was;
            }
            h.assertTrue(e.isDeadOrDying(), "he didn't die");
            MobEffectInstance slow = pl[1].getEffect(MobEffects.SLOW_FALLING);
            h.assertTrue(slow != null, "the player on his crown got no slow falling as he died");
            h.assertTrue(slow.getDuration() * 0.45 > over, "slow falling for " + slow.getDuration() + " ticks from " + over + " blocks up");
        });
        h.runAfterDelay(60 + HollowbellEntity.DEATH_LENGTH + 10, () -> {
            var orbs = h.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(at[0], at[0]).inflate(60));
            int xp = orbs.stream().mapToInt(ExperienceOrb::getValue).sum();
            orbs.forEach(o -> o.discard());
            drop(pl[0]);
            drop(pl[1]);
            release(h, e);
            h.assertTrue(e.isRemoved(), "he should be gone after his death");
            h.assertTrue(xp > 0, "killed by a player, he dropped no XP when his death was over");
            h.succeed();
        });
    }
}
