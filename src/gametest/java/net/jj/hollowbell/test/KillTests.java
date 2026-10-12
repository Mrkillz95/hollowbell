package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.*;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.rig.BellRig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** His moves really kill and hurt things. */
public class KillTests implements FabricGameTest {
    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, Vec3 at) {
        T m = type.create(h.getLevel());
        m.moveTo(at.x, at.y, at.z, 0f, 0f);
        m.setNoAi(true);
        m.setPersistenceRequired();
        // a helmet so the sun can't be what kills it
        m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        h.getLevel().addFreshEntity(m);
        return m;
    }

    /** starts a move at full size with the creature right where it lands, then checks it's dead a little after */
    private static void killsWith(GameTestHelper h, int move, EntityType<? extends Mob> type, int slot, int within) {
        HollowbellEntity e = spawnAway(h, 1f, HollowbellEntity.HUNTER, slot);
        Mob[] m = new Mob[1];
        float[] hp = new float[1];
        h.runAfterDelay(30, () -> {
            e.setStay(true);
            Vec3 at = under(e);
            if (move == Moves.SLAM) {
                Vec3 tip = e.armTipWorld(0);
                at = new Vec3(tip.x, e.groundAt(tip.x, tip.z), tip.z);
            }
            if (move == Moves.SWEEP) at = under(e).add(e.bellRadius() * 0.8, 0, 0);
            m[0] = mob(h, type, at);
            hp[0] = m[0].getHealth();
            e.moves().restNow();
            h.assertTrue(e.forceMove(move, m[0]), "couldn't start " + Moves.NAMES[move]);
        });
        h.runAfterDelay(30 + within, () -> {
            h.assertTrue(
                    !m[0].isAlive(),
                    "the " + type.getDescription().getString() + " (" + hp[0] + " health) lived through "
                            + Moves.NAMES[move] + " with " + m[0].getHealth() + " left");
            m[0].discard();
            e.moves().letGoOfEverything(false);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "kill_zombie")
    public void theSweepKillsAZombie(GameTestHelper h) {
        killsWith(h, Moves.SWEEP, EntityType.ZOMBIE, 100, Moves.length(Moves.SWEEP));
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "kill_skeleton")
    public void theArmSlamKillsASkeleton(GameTestHelper h) {
        killsWith(h, Moves.SLAM, EntityType.SKELETON, 102, Moves.length(Moves.SLAM));
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "kill_golem")
    public void theDropKillsAnIronGolem(GameTestHelper h) {
        killsWith(h, Moves.DROP, EntityType.IRON_GOLEM, 104, Moves.DROP_WIND + Moves.DROP_FALL + 20);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "kill_golem2")
    public void theDeepTollKillsAnIronGolem(GameTestHelper h) {
        killsWith(h, Moves.DEEP_TOLL, EntityType.IRON_GOLEM, 106, Moves.TOLL_BIG + 30);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 700, batch = "kill_cow")
    public void aCowTakenIntoTheDomeDies(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.25f, HollowbellEntity.HUNTER, 108);
        Cow[] c = new Cow[1];
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            c[0] = mob(h, EntityType.COW, under(e));
            h.assertTrue(e.forceMove(Moves.HARVEST, c[0]), "no harvest");
        });
        // it's hurt on the way up but lives to be taken in, so you see it go into the dome
        h.runAfterDelay(
                20 + Moves.REACH + Moves.LIFT - 5,
                () -> h.assertTrue(c[0].isAlive(), "the cow died on the way up, before it got to the dome"));
        h.runAfterDelay(20 + Moves.length(Moves.HARVEST) + 60, () -> {
            h.assertTrue(
                    !c[0].isAlive(),
                    "the cow is still alive " + (e.moves().isInside(c[0]) ? "inside the dome" : "outside") + " with "
                            + c[0].getHealth());
            c[0].discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1500, batch = "guardian_clears")
    public void aGuardianClearsOutHostileMobs(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.5f, HollowbellEntity.GUARDIAN, 110);
        List<Mob> zs = new ArrayList<>();
        h.runAfterDelay(20, () -> {
            e.setHome(e.position());
            e.setMoveCooldown(0);
            e.moves().restNow();
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2;
                Vec3 at = e.position().add(Math.cos(a) * 25, 0, Math.sin(a) * 25);
                Vec3 where = new Vec3(at.x, e.groundAt(at.x, at.z), at.z);
                zs.add(i % 2 == 0 ? mob(h, EntityType.ZOMBIE, where) : mob(h, EntityType.SKELETON, where));
            }
        });
        h.runAfterDelay(1400, () -> {
            long alive = zs.stream().filter(LivingEntity::isAlive).count();
            h.assertTrue(alive == 0, alive + " of 4 hostile mobs are still alive round a guardian after a minute");
            for (var z : zs) z.discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "netherite")
    public void aHeavyMoveBadlyHurtsAPlayerInNetherite(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 1f, HollowbellEntity.HUNTER, 112);
        ServerPlayer[] pl = new ServerPlayer[1];
        h.runAfterDelay(30, () -> {
            e.setStay(true);
            pl[0] = player(h, under(e).add(8, 0, 0));
            pl[0].setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
            pl[0].setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
            pl[0].setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
            pl[0].setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
            e.moves().restNow();
            h.assertTrue(e.forceMove(Moves.DROP, pl[0]), "no drop");
        });
        h.runAfterDelay(30 + Moves.DROP_WIND + Moves.DROP_FALL + 10, () -> {
            float lost = 20f - pl[0].getHealth();
            h.assertTrue(
                    !pl[0].isAlive() || lost >= 10f, "a player in netherite only lost " + lost + " health to the drop");
            drop(pl[0]);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 900, batch = "arrive")
    public void aNewOneComesDownOutOfTheSky(GameTestHelper h) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        int x = o.getX() + 20000 + 95 * 400, z = o.getZ() + 5000;
        for (int cx = (x >> 4) - 3; cx <= (x >> 4) + 3; cx++)
            for (int cz = (z >> 4) - 3; cz <= (z >> 4) + 3; cz++) {
                h.getLevel().setChunkForced(cx, cz, true);
                h.getLevel().getChunk(cx, cz);
            }
        runsEntities(h, x, z, 1);
        int y = h.getLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        HollowbellEntity e = ModEntities.HOLLOWBELL.create(h.getLevel());
        e.moveTo(x + 0.5, y, z + 0.5, 0f, 0f);
        e.setBellScale(S);
        e.setVariant(HollowbellEntity.CALM);
        h.getLevel().addFreshEntity(e);
        double[] top = new double[1];
        h.runAfterDelay(5, () -> {
            top[0] = e.getY();
            h.assertTrue(top[0] > y + 20, "he didn't start up in the sky: " + top[0] + " over ground " + y);
        });
        h.runAfterDelay(850, () -> {
            h.assertTrue(
                    e.getY() < top[0] - 20 && e.getY() < y + 60 * S + 8,
                    "he didn't come down: at " + e.getY() + " (started " + top[0] + ", ground " + y + ")");
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "slam_hits")
    public void theArmSlamHurtsWhatItLandsOn(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.HUNTER, 19);
        Pig[] p = new Pig[1];
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            Vec3 tip = e.armTipWorld(3);
            p[0] = pig(h, new Vec3(tip.x, e.groundAt(tip.x, tip.z), tip.z));
            p[0].setHealth(10f);
            h.assertTrue(e.forceMove(Moves.SLAM, p[0]), "no slam");
        });
        int[] arm = new int[1];
        Vec3[] tipAt = new Vec3[1];
        h.runAfterDelay(20 + Math.round(BellRig.SLAM_HIT * Moves.length(Moves.SLAM)), () -> {
            arm[0] = e.moveArg();
            tipAt[0] = e.armTipWorld(Math.max(0, e.moveArg()));
        });
        h.runAfterDelay(20 + Moves.length(Moves.SLAM) + 5, () -> {
            h.assertTrue(
                    !p[0].isAlive() || p[0].getHealth() < 10f,
                    "the slam missed the pig under the arm: arm " + arm[0] + " tip " + tipAt[0] + " pig "
                            + p[0].position() + " he " + e.position());
            p[0].discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "pulse_flyers")
    public void thePulseWaveKnocksFlyersDown(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.HUNTER, 20);
        ServerPlayer[] pl = new ServerPlayer[1];
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            pl[0] = player(h, e.position().add(0, 150 * S, 12 * S + 5));
            pl[0].getAbilities().mayfly = true;
            pl[0].getAbilities().flying = true;
            h.assertTrue(e.forceMove(Moves.PULSE, pl[0]), "no pulse");
        });
        h.runAfterDelay(20 + Moves.PULSE_AT + 26, () -> {
            h.assertTrue(!pl[0].getAbilities().flying, "still flying after the pulse wave");
            h.assertTrue(pl[0].hasEffect(MobEffects.CONFUSION), "no nausea from the pulse wave");
            drop(pl[0]);
            release(h, e);
            h.succeed();
        });
    }
}
