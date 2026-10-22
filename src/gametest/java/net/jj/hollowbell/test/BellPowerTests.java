package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.drop;
import static net.jj.hollowbell.test.HollowbellGameTests.onGround;
import static net.jj.hollowbell.test.HollowbellGameTests.player;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.item.GlassBell;
import net.jj.hollowbell.item.Powers;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** The bell glass set's two powers: the Great Toll (R) and the Glass Bell (Sneak + R). */
public class BellPowerTests implements FabricGameTest {

    private static Zombie zombie(GameTestHelper h, Vec3 at) {
        Zombie z = EntityType.ZOMBIE.create(h.getLevel());
        z.moveTo(at.x, at.y, at.z, 0, 0);
        z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        z.setNoAi(true);
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        z.setHealth(100);
        h.getLevel().addFreshEntity(z);
        return z;
    }

    private static Wolf wolf(GameTestHelper h, Vec3 at) {
        Wolf w = EntityType.WOLF.create(h.getLevel());
        w.moveTo(at.x, at.y, at.z, 0, 0);
        w.setNoAi(true);
        h.getLevel().addFreshEntity(w);
        return w;
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160, batch = "armour_great_toll")
    public void theGreatTollStunsTenBlocksOutThenASecondRingPushesBack(GameTestHelper h) {
        Powers.clear();
        Vec3 at = onGround(h, 1, 1);
        ServerPlayer p = player(h, at);
        Zombie near = zombie(h, at.add(3, 0, 0));
        Zombie far = zombie(h, onGround(h, 7, 7));
        Wolf mine = wolf(h, at.add(0, 0, 2));
        Runnable clear = () -> {
            near.discard();
            far.discard();
            mine.discard();
            drop(p);
        };
        float[] after1 = new float[1];
        double[] d1 = new double[1];
        h.runAfterDelay(2, () -> {
            mine.tame(p);
            GearTests.wearBellGlass(p);
            p.setOnGround(true);
            h.assertTrue(Powers.use(p) == Powers.Kind.TOLL, "on the ground: the toll");
            h.assertTrue(p.getCooldowns().isOnCooldown(ModItems.BELL_CHESTPLATE), "R's wait is on the chestplate");
            h.assertTrue(!p.getCooldowns().isOnCooldown(ModItems.BELL_LEGGINGS), "Sneak + R's is its own");
        });
        int first = 2 + Powers.RISE + Powers.HANG + 6;
        h.runAfterDelay(first, () -> {
            try {
                p.setOnGround(true);
                float took = 100 - near.getHealth();
                h.assertTrue(took >= 6 && took <= 14, "the first ring hurts 8 to 14: " + took);
                h.assertTrue(far.getHealth() < 100, "and reaches 8 blocks out (10 now)");
                MobEffectInstance slow = near.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
                h.assertTrue(slow != null && slow.getAmplifier() >= 6, "stunned: can't move " + slow);
                MobEffectInstance weak = near.getEffect(MobEffects.WEAKNESS);
                h.assertTrue(weak != null && weak.getDuration() > 40, "nor hit, for three seconds: " + weak);
                h.assertTrue(mine.getHealth() == mine.getMaxHealth(), "your own wolf is left alone");
                h.assertTrue(Powers.busy(p), "the second ring is still to come");
                after1[0] = near.getHealth();
                d1[0] = near.position().distanceTo(at);
            } catch (RuntimeException ex) {
                clear.run();
                throw ex;
            }
        });
        h.runAfterDelay(first + Powers.SECOND_RING + 3, () -> {
            try {
                float took = after1[0] - near.getHealth();
                h.assertTrue(took >= 3 && took <= 8, "the softer ring does about half: " + took);
                double d2 = near.position().distanceTo(at);
                h.assertTrue(
                        d2 > d1[0] + 0.3 || near.getDeltaMovement().horizontalDistance() > 0.3,
                        "and pushes them back out: " + d1[0] + " -> " + d2);
                h.assertTrue(!Powers.busy(p), "then the toll is done");
                h.assertTrue(mine.getHealth() == mine.getMaxHealth(), "your wolf still unhurt");
            } finally {
                clear.run();
            }
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 460, batch = "armour_glass_bell")
    public void theGlassBellKeepsThingsOutAndLetsYouBreathe(GameTestHelper h) {
        Powers.clear();
        Vec3 at = onGround(h, 3, 3);
        ServerPlayer p = player(h, at);
        Zombie in = zombie(h, at.add(2, 0, 0));
        Wolf mine = wolf(h, at.add(0, 0, 1.5));
        Runnable clear = () -> {
            in.discard();
            mine.discard();
            GlassBell.stop(p);
            drop(p);
        };
        h.runAfterDelay(2, () -> {
            try {
                mine.tame(p);
                GearTests.wearBellGlass(p);
                p.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                h.assertTrue(!GlassBell.ring(p), "nothing without the whole set");
                GearTests.wearBellGlass(p);
                h.assertTrue(GlassBell.ring(p), "the bell closes over you");
                h.assertTrue(GlassBell.inside(p), "you're inside it");
                h.assertTrue(p.getCooldowns().isOnCooldown(ModItems.BELL_LEGGINGS), "the wait is on the leggings");
                h.assertTrue(!p.getCooldowns().isOnCooldown(ModItems.BELL_CHESTPLATE), "R's is its own");
                h.assertTrue(!GlassBell.ring(p), "not again for two minutes");
                p.setAirSupply(0);
            } catch (RuntimeException ex) {
                clear.run();
                throw ex;
            }
        });
        Arrow[] shot = new Arrow[1];
        h.runAfterDelay(5, () -> {
            try {
                h.assertTrue(in.position().distanceTo(at) >= GlassBell.RADIUS, "the zombie is pushed out of the bell");
                h.assertTrue(mine.position().distanceTo(at) < GlassBell.RADIUS, "your own wolf may stay in");
                h.assertTrue(p.getAirSupply() == p.getMaxAirSupply(), "and you breathe in it");
                // an arrow from outside, flying in at you
                shot[0] = new Arrow(EntityType.ARROW, h.getLevel());
                shot[0].setPos(at.x, at.y + 1, at.z - 7);
                shot[0].setDeltaMovement(0, 0, 1.5);
                h.getLevel().addFreshEntity(shot[0]);
            } catch (RuntimeException ex) {
                clear.run();
                throw ex;
            }
        });
        h.runAfterDelay(12, () -> {
            try {
                h.assertTrue(shot[0].isRemoved(), "the arrow doesn't get through the glass");
                h.assertTrue(p.getHealth() == p.getMaxHealth(), "and didn't hit you");
            } catch (RuntimeException ex) {
                clear.run();
                throw ex;
            }
        });
        h.runAfterDelay(2 + GlassBell.TICKS + 5, () -> {
            try {
                h.assertTrue(!GlassBell.inside(p), "after 20 seconds the bell is gone");
            } finally {
                clear.run();
            }
            h.succeed();
        });
    }
}
