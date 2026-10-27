package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.S;
import static net.jj.hollowbell.test.HollowbellGameTests.giantOfKind;
import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.reload;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.Belling;
import net.jj.hollowbell.entity.Giants;
import net.jj.hollowbell.entity.Grudges;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Shot;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Snowball;

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

    /** what he sends out (his Bellings, his stingers and eggs, his spore clouds) carries jj_giant_of:his id */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "grudge_helper_tag")
    public void hisHelpersCarryHisMark(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.HUNTER, 917);
        h.runAfterDelay(20, () -> {
            String mark = "jj_giant_of:" + e.getUUID();
            Belling b = new Belling(ModEntities.BELLING, h.getLevel());
            b.setOwner(e);
            h.assertTrue(b.getTags().contains(mark), "a Belling of his isn't marked: " + b.getTags());
            h.assertTrue(
                    new Shot(h.getLevel(), e, Shot.STINGER, 7f, 1f).getTags().contains(mark), "a stinger isn't marked");
            h.assertTrue(new Shot(h.getLevel(), e, Shot.EGG, 7f, 3f).getTags().contains(mark), "an egg isn't marked");
            // (an old Belling, saved before the mark: it gets it back when loaded)
            CompoundTag tag = new CompoundTag();
            b.saveWithoutId(tag);
            tag.remove("Tags");
            Belling old = new Belling(ModEntities.BELLING, h.getLevel());
            old.load(tag);
            h.assertTrue(old.getTags().contains(mark), "an old Belling isn't marked when loaded");
            release(h, e);
            h.succeed();
        });
    }

    /**
     * Hurt by another giant's helper (anything marked jj_giant_of:that giant), he holds it against the giant; in a
     * giants' meeting fight the giant is what he goes for, not the helper. A mark that finds nobody is ignored.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "grudge_helper")
    public void aHelpersBlowIsHeldAgainstItsGiant(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.HUNTER, 919);
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            var g = giantOfKind(h, e.position().add(60, 0, 0), "swarmforge");
            // a robot of its, struck straight
            Zombie robot = zombie(h, e, 6);
            robot.addTag(Giants.KIN);
            robot.addTag(Giants.OF + g.getUUID());
            e.hurt(e.damageSources().mobAttack(robot), 2f);
            h.assertTrue(e.holdsGrudge(g), "the robot's blow isn't held against its giant");
            h.assertFalse(e.holdsGrudge(robot), "the robot itself is held");
            // a shot the robot fired (the mark is on the shooter)
            e.forgiveAll();
            Zombie shooter = zombie(h, e, 7);
            shooter.addTag(Giants.KIN);
            shooter.addTag(Giants.OF + g.getUUID());
            Snowball ball = new Snowball(h.getLevel(), shooter);
            e.hurt(e.damageSources().thrown(ball, shooter), 2f);
            h.assertTrue(e.holdsGrudge(g), "the robot's shot isn't held against its giant");
            // (or on the shot itself)
            e.forgiveAll();
            Snowball marked = new Snowball(EntityType.SNOWBALL, h.getLevel());
            marked.addTag(Giants.OF + g.getUUID());
            e.hurt(e.damageSources().thrown(marked, null), 2f);
            h.assertTrue(e.holdsGrudge(g), "a marked shot isn't held against its giant");
            // in a meeting fight with the giant, the robot's blow turns him on the giant
            e.forgiveAll();
            h.assertTrue(e.startMeeting(g), "no meeting");
            Zombie other = zombie(h, e, 9);
            other.addTag(Giants.KIN);
            other.addTag(Giants.OF + g.getUUID());
            e.setTarget(other);
            e.hurt(e.damageSources().mobAttack(other), 2f);
            h.assertTrue(e.getTarget() == g, "in the meeting fight he goes for " + e.getTarget() + ", not the giant");
            // a mark that finds nobody (or isn't an id) is ignored: the one that struck is held
            e.forgiveAll();
            Zombie lost = zombie(h, e, 8), bad = zombie(h, e, 10);
            lost.addTag(Giants.OF + UUID.randomUUID());
            bad.addTag(Giants.OF + "not-an-id");
            e.hurt(e.damageSources().mobAttack(lost), 2f);
            e.hurt(e.damageSources().mobAttack(bad), 2f);
            h.assertTrue(e.holdsGrudge(lost) && e.holdsGrudge(bad), "a mark that finds nobody wasn't ignored");
            robot.discard();
            shooter.discard();
            other.discard();
            lost.discard();
            bad.discard();
            g.discard();
            release(h, e);
            h.succeed();
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
