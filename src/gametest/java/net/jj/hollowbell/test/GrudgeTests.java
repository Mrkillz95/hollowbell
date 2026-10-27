package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.S;
import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.reload;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.entity.Grudges;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;

/** He keeps his grudges: only death, keeping well away for long, being told to forget or backing down end one. */
public class GrudgeTests implements FabricGameTest {

    private static Zombie zombie(GameTestHelper h, HollowbellEntity e, double out) {
        Zombie z = EntityType.ZOMBIE.create(h.getLevel());
        z.moveTo(e.getX() + out, e.groundAt(e.getX() + out, e.getZ()), e.getZ(), 0f, 0f);
        z.setNoAi(true);
        z.setInvulnerable(true);
        z.setPersistenceRequired();
        h.getLevel().addFreshEntity(z);
        return z;
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "grudge_mobs")
    public void everyoneWhoHurtsHimIsHeldNotJustPlayers(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 784);
        h.runAfterDelay(20, () -> {
            Zombie a = zombie(h, e, 6), b = zombie(h, e, 8);
            e.hurt(e.damageSources().mobAttack(a), 2f);
            e.hurt(e.damageSources().mobAttack(b), 2f);
            h.assertTrue(e.holdsGrudge(a), "the first creature to hurt him isn't held");
            h.assertTrue(e.holdsGrudge(b), "the second creature to hurt him isn't held");
            a.discard();
            b.discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "grudge_back")
    public void heGoesBackForWhatHurtHimAfterLosingIt(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 785);
        Zombie[] z = new Zombie[1];
        h.runAfterDelay(20, () -> {
            z[0] = zombie(h, e, 6);
            e.hurt(e.damageSources().mobAttack(z[0]), 2f);
            // (it hid for a moment, or something else took his eye, and his temper cooled)
            e.setTarget(null);
            e.clearHitList();
        });
        h.runAfterDelay(80, () -> {
            h.assertTrue(e.getTarget() == z[0], "he didn't go back for what hurt him: " + e.getTarget());
            z[0].discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "grudge_old")
    public void anOldGrudgeHoldsWhileItsTargetIsStillAbout(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 786);
        Zombie[] z = new Zombie[1];
        UUID gone = UUID.randomUUID(), brief = UUID.randomUUID();
        h.runAfterDelay(20, () -> {
            z[0] = zombie(h, e, 7);
            long now = h.getLevel().getGameTime();
            e.grudges().hold(z[0].getUUID(), now - Grudges.LET_GO * 3);
            e.grudges().hold(brief, now - 600);
            e.grudges().hold(gone, now - Grudges.LET_GO - 100);
        });
        h.runAfterDelay(80, () -> {
            h.assertTrue(e.holdsGrudge(z[0]), "an old grudge against something still near was dropped");
            h.assertTrue(e.grudges().has(brief), "something out of sight for half a minute was forgiven");
            h.assertFalse(e.grudges().has(gone), "something gone for more than ten minutes is still held");
            z[0].discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "grudge_end")
    public void deathOrForgivingEndsIt(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 787);
        h.runAfterDelay(20, () -> {
            Zombie a = zombie(h, e, 6), b = zombie(h, e, 8);
            e.hurt(e.damageSources().mobAttack(a), 2f);
            e.hurt(e.damageSources().mobAttack(b), 2f);
            a.setInvulnerable(false);
            a.kill();
            h.runAfterDelay(30, () -> {
                h.assertFalse(e.holdsGrudge(a), "the grudge outlived its target");
                h.assertTrue(e.holdsGrudge(b), "the other one was let go too");
                e.forgiveAll();
                h.assertFalse(e.holdsGrudge(b), "forgiving didn't clear it");
                b.discard();
                release(h, e);
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "grudge_save")
    public void aGrudgeOutlastsSavingAndLoading(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 788);
        h.runAfterDelay(20, () -> {
            Zombie a = zombie(h, e, 6);
            e.hurt(e.damageSources().mobAttack(a), 2f);
            HollowbellEntity back = reload(h, e);
            h.assertTrue(back.holdsGrudge(a), "the grudge was lost in the save");
            a.discard();
            release(h, back);
            h.succeed();
        });
    }
}
