package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.drop;
import static net.jj.hollowbell.test.HollowbellGameTests.onGround;
import static net.jj.hollowbell.test.HollowbellGameTests.player;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.entity.StingerHook;
import net.jj.hollowbell.item.GlassBell;
import net.jj.hollowbell.item.Powers;
import net.jj.hollowbell.item.StingerItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Who the bell glass powers and the Stinger hit: other players only with PvP on, never cows, pets or you. */
public class WhoPowersHitTests implements FabricGameTest {

    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, Vec3 at) {
        T m = type.create(h.getLevel());
        m.moveTo(at.x, at.y, at.z, 0, 0);
        m.setNoAi(true);
        h.getLevel().addFreshEntity(m);
        return m;
    }

    private static boolean whole(LivingEntity e) {
        return e.getHealth() >= e.getMaxHealth();
    }

    private static void heal(LivingEntity e) {
        e.setHealth(e.getMaxHealth());
        e.removeAllEffects();
        e.invulnerableTime = 0;
    }

    private static int slowFor(LivingEntity e) {
        MobEffectInstance s = e.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        return s == null ? 0 : s.getDuration();
    }

    /**
     * one step of a test: if it fails, or it's the last one, the server's PvP setting goes back to what it was and
     * the test's players and mobs are cleared away
     */
    private static void step(GameTestHelper h, boolean pvpWas, boolean last, Runnable check, Entity... clear) {
        boolean ok = false;
        try {
            check.run();
            ok = true;
        } finally {
            if (!ok || last) {
                h.getLevel().getServer().setPvpAllowed(pvpWas);
                for (Entity e : clear) {
                    if (e instanceof ServerPlayer sp) drop(sp);
                    else e.discard();
                }
            }
        }
    }

    private static void pvp(GameTestHelper h, boolean on) {
        h.getLevel().getServer().setPvpAllowed(on);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "who_powers_hit_toll")
    public void theTollAndTheLastRingHitPlayersOnlyWithPvpAndNeverCowsOrPets(GameTestHelper h) {
        Powers.clear();
        boolean was = h.getLevel().getServer().isPvpAllowed();
        Vec3 at = onGround(h, 1, 1);
        ServerPlayer p = player(h, at);
        ServerPlayer q = player(h, at.add(3, 0, 0));
        Cow cow = mob(h, EntityType.COW, at.add(-3, 0, 0));
        Wolf wolf = mob(h, EntityType.WOLF, at.add(0, 0, 2));
        Zombie z = mob(h, EntityType.ZOMBIE, at.add(0, 0, -3));
        Entity[] all = {p, q, cow, wolf, z};
        h.runAfterDelay(2, () -> step(h, was, true, () -> {
            wolf.tame(p);
            GearTests.wearBellGlass(p);
            pvp(h, true);
            Powers.tollRing(p, false);
            h.assertTrue(q.getHealth() < q.getMaxHealth(), "with PvP on the toll hurts another player");
            int stun = slowFor(q);
            h.assertTrue(stun > 0 && stun <= Powers.STUN / 2, "and stuns them half as long: " + stun);
            h.assertTrue(slowFor(z) > Powers.STUN / 2, "a zombie gets the full stun: " + slowFor(z));
            h.assertTrue(z.getHealth() < z.getMaxHealth(), "and is hurt");
            h.assertTrue(whole(cow) && slowFor(cow) == 0, "the cow is left alone");
            h.assertTrue(whole(wolf) && slowFor(wolf) == 0, "your own wolf is left alone");
            h.assertTrue(whole(p), "you are never hurt");
            heal(q);
            Powers.ring(p, 1f);
            h.assertTrue(q.getHealth() < q.getMaxHealth(), "the full ring hurts another player too");
            h.assertTrue(whole(cow) && whole(wolf) && whole(p), "but not the cow, your wolf or you");
            heal(q);
            pvp(h, false);
            Powers.tollRing(p, false);
            Powers.tollRing(p, true);
            Powers.ring(p, 1f);
            h.assertTrue(whole(q) && slowFor(q) == 0, "with PvP off nobody else is touched");
            h.assertTrue(whole(cow) && whole(wolf) && whole(p), "nor the cow, the wolf or you");
            h.succeed();
        }, all));
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "who_powers_hit_stinger")
    public void theHarpoonHitsAPlayerOnlyWithPvpAndPinsThemHalfAsLong(GameTestHelper h) {
        boolean was = h.getLevel().getServer().isPvpAllowed();
        Vec3 at = onGround(h, 1, 1);
        ServerPlayer p = player(h, at);
        ServerPlayer q = player(h, at.add(4, 0, 0));
        Wolf wolf = mob(h, EntityType.WOLF, at.add(-4, 0, 0));
        Zombie z = mob(h, EntityType.ZOMBIE, at.add(0, 0, 4));
        Entity[] all = {p, q, wolf, z};
        ItemStack stinger = new ItemStack(ModItems.STINGER);
        // yaw -90 looks along +x, at the other player; 90 back the other way, at the wolf
        Runnable throwAt = () -> {
            p.setXRot(0);
            StingerItem.throwIt(p, stinger, 1f, false);
        };
        h.runAfterDelay(2, () -> step(h, was, false, () -> {
            StingerHook.pin(q, p);
            StingerHook.pin(z, p);
            h.assertTrue(slowFor(q) == StingerHook.PIN / 2, "a player is pinned half as long: " + slowFor(q));
            h.assertTrue(slowFor(z) == StingerHook.PIN, "a zombie the whole time: " + slowFor(z));
            heal(q);
            wolf.tame(p);
            pvp(h, true);
            p.setYRot(-90);
            throwAt.run();
        }, all));
        h.runAfterDelay(10, () -> step(h, was, false, () -> {
            h.assertTrue(q.getHealth() < q.getMaxHealth(), "with PvP on the harpoon hurts another player");
            h.assertTrue(q.hasEffect(MobEffects.POISON), "and poisons them");
            h.assertTrue(whole(p), "not you");
            heal(q);
            pvp(h, false);
            throwAt.run();
        }, all));
        h.runAfterDelay(18, () -> step(h, was, false, () -> {
            h.assertTrue(whole(q) && !q.hasEffect(MobEffects.POISON), "with PvP off it doesn't touch them");
            pvp(h, true);
            p.setYRot(90);
            throwAt.run();
        }, all));
        h.runAfterDelay(26, () -> step(h, was, true, () -> {
            h.assertTrue(whole(wolf) && !wolf.hasEffect(MobEffects.POISON), "your own wolf is never harpooned");
            h.assertTrue(whole(p), "nor you");
            h.succeed();
        }, all));
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "who_powers_hit_glass_bell")
    public void theGlassBellKeepsOtherPlayersOutOnlyWithPvp(GameTestHelper h) {
        Powers.clear();
        boolean was = h.getLevel().getServer().isPvpAllowed();
        Vec3 at = onGround(h, 3, 3);
        ServerPlayer p = player(h, at);
        ServerPlayer q = player(h, at.add(1.5, 0, 0));
        Entity[] all = {p, q};
        h.runAfterDelay(2, () -> step(h, was, false, () -> {
            GearTests.wearBellGlass(p);
            pvp(h, false);
            h.assertTrue(GlassBell.ring(p), "the bell closes over you");
        }, all));
        h.runAfterDelay(6, () -> step(h, was, false, () -> {
            h.assertTrue(q.position().distanceTo(at) < GlassBell.RADIUS, "with PvP off another player may stay in");
            pvp(h, true);
        }, all));
        h.runAfterDelay(10, () -> step(h, was, true, () -> {
            GlassBell.stop(p);
            h.assertTrue(q.position().distanceTo(at) >= GlassBell.RADIUS, "with PvP on they're pushed out");
            h.assertTrue(whole(q), "and not hurt by it");
            h.succeed();
        }, all));
    }
}
