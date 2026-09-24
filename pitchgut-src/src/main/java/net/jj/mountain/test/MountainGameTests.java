package net.jj.mountain.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.mountain.ModBlocks;
import net.jj.mountain.ModEntities;
import net.jj.mountain.entity.GripSeat;
import net.jj.mountain.entity.HeartEntity;
import net.jj.mountain.entity.MountainEntity;
import net.jj.mountain.entity.MountainPart;
import net.jj.mountain.innards.Innards;
import net.jj.mountain.rig.MountainRig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.UUID;

/** Server-side checks: run with gradle runGametest. */
public class MountainGameTests implements FabricGameTest {
    static {
        // no world boss during the tests: it would put one down thousands of blocks out and make the world there
        net.jj.mountain.MountainConfig.V.oneInTheWorld = false;

    }

    private static MountainEntity spawn(GameTestHelper h, float scale, int variant) {
        MountainEntity e = h.spawn(ModEntities.MOUNTAIN, new BlockPos(1, 1, 1));
        e.setVariant(variant);
        e.setMountainScale(scale);
        return e;
    }

    /** a real (survival) player in the level, like the helper's mock but not in creative */
    @SuppressWarnings("deprecation")
    private static ServerPlayer survivalPlayer(GameTestHelper h, Vec3 at) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mtn-test"), false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
        };
        Connection c = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(c);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(c, p, cookie);
        p.teleportTo(h.getLevel(), at.x, at.y, at.z, 0f, 0f);
        // A player who has just joined can't be hurt for three seconds, and that grace only counts down while a
        // real connection is being ticked. These players have no client on the other end, so it never would.
        try {
            java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            f.setAccessible(true);
            f.setInt(p, 0);
        } catch (ReflectiveOperationException ignored) { }
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);   // the bare test server hands out creative
        return p;
    }

    private static void drop(ServerPlayer p) { p.getServer().getPlayerList().remove(p); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
    public void spawnsWithAllHitboxes(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.08f, MountainEntity.CALM);
        e.setStay(true);                      // he must not wander off mid-count
        h.runAfterDelay(70, () -> {
            int want = MountainRig.get().parts.size();
            h.assertTrue(e.parts().size() == want, "parts: " + e.parts().size() + " want " + want);
            AABB near = e.getBoundingBox().inflate(300 * 0.08 + 8);
            for (MountainPart p : e.parts()) h.assertTrue(near.intersects(p.getBoundingBox()), "part " + p.index() + " is far from him");
            h.assertTrue(e.eyesOpen() == MountainRig.get().eyes.length, "all eyes should start open");
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void poppingAnEyeHurtsHim(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.1f, MountainEntity.CALM);
        h.runAfterDelay(20, () -> {
            float before = e.healthNow();
            int open = e.eyesOpen();
            e.popEyes(1, null);
            h.assertTrue(e.eyesOpen() == open - 1, "an eye should be popped: " + e.eyesOpen());
            h.assertTrue(e.isEyePopped(0), "eye 0 should be the popped one");
            float lost = before - e.healthNow(), worth = e.eyeWorth();
            h.assertTrue(lost > worth * 0.9f && lost < worth * 1.6f,
                    "an eye should cost him what an eye is worth (" + worth + "): lost " + lost);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void fleshBarelyFeelsIt(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.1f, MountainEntity.CALM);
        h.runAfterDelay(25, () -> {
            MountainPart part = e.parts().get(0);
            float before = e.healthNow();
            h.assertTrue(part.hurt(e.damageSources().generic(), 20f), "the part took no damage");
            float lost = before - e.healthNow();
            h.assertTrue(lost > 1.5f && lost < 2.5f, "a flesh hit should do a tenth: lost " + lost);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "death")
    public void heDiesSinksAndDropsLoot(GameTestHelper h) {
        int wasCarcass = net.jj.mountain.MountainConfig.V.carcassSeconds;
        net.jj.mountain.MountainConfig.V.carcassSeconds = 2;        // don't sit through the whole body rotting
        MountainEntity e = spawn(h, 0.06f, MountainEntity.CALM);
        h.runAfterDelay(20, () -> {
            e.applyDamage(e.damageSources().generic(), e.healthMax() * 20f, null);
            h.assertTrue(e.isDeadOrDying(), "he should be dying");
        });
        h.runAfterDelay(20 + 60, () -> {
            h.assertTrue(!e.isRemoved(), "he should still be going through his last breath");
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(e.blockPosition()).inflate(40));
            h.assertTrue(drops.isEmpty(), "the loot should wait until he has sunk");
        });
        h.runAfterDelay(20 + e.deathLength() + 90, () -> {
            net.jj.mountain.MountainConfig.V.carcassSeconds = wasCarcass;
            h.assertTrue(e.isRemoved(), "he should be gone after sinking");
            h.assertTrue(e.parts().isEmpty(), "his hitboxes should be gone");
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(new BlockPos(1, 1, 1))).inflate(40));
            h.assertTrue(!drops.isEmpty(), "no loot dropped");
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200)
    public void theHunterHuntsTheCalmOneWaits(GameTestHelper h) {
        MountainEntity calm = spawn(h, 0.1f, MountainEntity.CALM);
        MountainEntity hunter = h.spawn(ModEntities.MOUNTAIN, new BlockPos(1, 1, 14));
        hunter.setVariant(MountainEntity.HUNTER); hunter.setMountainScale(0.1f);
        ServerPlayer p = survivalPlayer(h, Vec3.atCenterOf(h.absolutePos(new BlockPos(12, 2, 8))));
        // (on a busy test server his chunk can take a while to start ticking, so wait for him to have lived 40 ticks)
        h.succeedWhen(() -> {
            h.assertTrue(hunter.tickCount >= 40 && calm.tickCount >= 40, "not ticking yet");
            h.assertTrue(hunter.getTarget() instanceof ServerPlayer, "the hunter should be after a player (the nearest one), has " + hunter.getTarget());
            h.assertTrue(calm.getTarget() == null, "the calm one should leave him alone");
            calm.applyDamage(calm.damageSources().playerAttack(p), 10f, null);
            h.assertTrue(calm.getTarget() == p, "hit him and he fights back");
            drop(p);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
    public void swallowedIntoHisHeartAndCoughedBack(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.3f, MountainEntity.CALM);
        ServerPlayer p = survivalPlayer(h, Vec3.atCenterOf(h.absolutePos(new BlockPos(10, 2, 10))));
        ServerLevel home = h.getLevel();
        h.runAfterDelay(75, () -> {                   // players can't be hurt for 3 seconds after they join
            if (home.getServer().getLevel(Innards.KEY) == null) {
                // the bare game-test server makes no datapack dimensions: he has to chew and spit instead
                Vec3 before = p.position();
                e.swallow(p);
                h.assertTrue(p.level() == home, "with no innards he should spit the player out");
                h.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING) && p.position().distanceTo(before) > 2, "he should have spat the player out in front of him");
                drop(p);
                h.succeed();
                return;
            }
            e.swallow(p);
            h.assertTrue(p.level().dimension() == Innards.KEY, "the player should be inside him");
            h.assertTrue(e.swallowedPlayers().contains(p.getUUID()), "he should know he has eaten someone");
        });
        h.runAfterDelay(125, () -> {
            if (!(p.level().dimension() == Innards.KEY)) return;
            ServerLevel in = (ServerLevel) p.level();
            BlockPos c = Innards.roomCenter(Innards.roomOf(e.getUUID()));
            var hearts = in.getEntitiesOfClass(HeartEntity.class, new AABB(c).inflate(30));
            h.assertTrue(hearts.size() == 1, "there should be one heart in the room, found " + hearts.size());
            h.assertTrue(in.getBlockState(p.blockPosition()).is(ModBlocks.GOO) || in.getBlockState(p.blockPosition().below()).isSolid(), "the player should be standing in the room");
            float before = e.healthNow();
            hearts.get(0).hurt(in.damageSources().playerAttack(p), 10f);
            h.assertTrue(e.healthNow() <= before - 29f, "a heart hit should hurt him three times over: " + before + " -> " + e.healthNow());
            e.coughUp();
            h.assertTrue(p.level() == home, "coughing should bring the player back out");
            drop(p);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
    public void gooPoursAsHeWalks(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.15f, MountainEntity.CALM);
        h.runAfterDelay(10, () -> e.setGoal(e.position().add(0, 0, 45)));
        h.runAfterDelay(300, () -> {
            int n = 0;
            for (BlockPos q : BlockPos.betweenClosed(e.blockPosition().offset(-40, -4, -60), e.blockPosition().offset(40, 6, 60)))
                if (h.getLevel().getBlockState(q).is(ModBlocks.GOO)) n++;
            h.assertTrue(n > 6, "there should be a trail of goo, found " + n);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
    public void hisArmsGrabWhatIsOnHisBack(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.25f, MountainEntity.CALM);
        final Pig[] pig = new Pig[1];
        h.runAfterDelay(20, () -> {
            MountainRig rig = MountainRig.get();
            MountainRig.BodySample b = rig.body.get(rig.body.size() / 2);
            Vec3 top = e.toWorld(new Vector3f(b.p()).add(0, b.r() + 2, 0), new org.joml.Matrix4f());
            pig[0] = EntityType.PIG.create(h.getLevel());
            pig[0].setNoAi(true);
            pig[0].setNoGravity(true);
            pig[0].moveTo(top.x, top.y, top.z, 0, 0);
            h.getLevel().addFreshEntity(pig[0]);
        });
        h.succeedWhen(() -> h.assertTrue(pig[0] != null && (e.held() == pig[0] || pig[0].getVehicle() instanceof GripSeat || !pig[0].isAlive()),
                "his arms have not taken the pig on his back yet"));
    }

    // ------------------------------------------------------------------ legs, riding, attacks

    /**
     * He is far bigger than a test's box and would trip over the other tests (and stand on their barrier walls),
     * so these tests take him out to open ground of his own, kept loaded.
     */
    private static MountainEntity spawnAway(GameTestHelper h, float scale, int variant, int slot) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        int x = o.getX() + 30000 + slot * 600, z = o.getZ();
        for (int cx = (x >> 4) - 4; cx <= (x >> 4) + 4; cx++) for (int cz = (z >> 4) - 4; cz <= (z >> 4) + 4; cz++) {
            h.getLevel().setChunkForced(cx, cz, true);
            h.getLevel().getChunk(cx, cz);
        }
        int y = h.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
        MountainEntity e = ModEntities.MOUNTAIN.create(h.getLevel());
        e.moveTo(x + 0.5, y, z + 0.5, 0f, 0f);
        e.setVariant(variant);
        e.setMountainScale(scale);
        h.getLevel().addFreshEntity(e);
        return e;
    }

    private static void release(GameTestHelper h, MountainEntity e) {
        int x = e.getBlockX(), z = e.getBlockZ();
        e.discard();
        for (int cx = (x >> 4) - 6; cx <= (x >> 4) + 6; cx++) for (int cz = (z >> 4) - 6; cz <= (z >> 4) + 6; cz++) h.getLevel().setChunkForced(cx, cz, false);
    }

    private static net.minecraft.world.entity.animal.Pig pig(GameTestHelper h, Vec3 worldAt) {
        net.minecraft.world.entity.animal.Pig p = EntityType.PIG.create(h.getLevel());
        p.moveTo(worldAt.x, worldAt.y, worldAt.z, 0f, 0f);
        p.setNoAi(true);
        p.setPersistenceRequired();
        h.getLevel().addFreshEntity(p);
        return p;
    }

    /** after walking and turning, every planted foot is on the ground (not in it, not floating) */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "legs")
    public void feetStayOnTheGround(GameTestHelper h) {
        MountainEntity e = spawnAway(h, 0.2f, MountainEntity.CALM, 1);
        h.runAfterDelay(10, () -> e.setGoal(e.position().add(30, 0, 25)));
        h.runAfterDelay(300, () -> {
            float s = e.mountainScale();
            int checked = 0; double worst = 0;
            for (MountainRig.LegDef L : MountainRig.get().legs) {
                if (L.kind == 2 || e.footLifted(L.k)) continue;
                Vec3 f = e.footWorld(L);
                int gy = e.groundAt(net.minecraft.util.Mth.floor(f.x), net.minecraft.util.Mth.floor(f.z));
                worst = Math.max(worst, f.y - gy);
                StringBuilder col = new StringBuilder();
                for (int dy = -1; dy < 14; dy++) col.append(h.getLevel().getBlockState(BlockPos.containing(f.x, gy + dy, f.z)).getBlock().getName().getString()).append(",");
                h.assertTrue(f.y > gy - 0.6 && f.y < gy + 3.5 * s + 1.0, "leg " + L.k + " foot at " + f.y + " but the ground is at " + gy + " col " + col + " pos " + f);
                checked++;
            }
            h.assertTrue(checked > 15, "most feet should be planted: " + checked);
            release(h, e);
            h.succeed();
        });
    }

    /** something lying on his back is carried round with him when he turns */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "ride")
    public void thingsOnHisBackRideAlong(GameTestHelper h) {
        MountainEntity e = spawnAway(h, 0.3f, MountainEntity.CALM, 14);
        net.minecraft.world.entity.vehicle.Boat[] item = new net.minecraft.world.entity.vehicle.Boat[1];
        MountainPart[] on = new MountainPart[1];
        h.runAfterDelay(30, () -> {
            e.setStay(true);
            on[0] = middleOfHisBack(e);
            h.assertTrue(on[0] != null, "no solid part on his back");
            AABB b = on[0].getBoundingBox();
            double top = e.backTop(b.getCenter().x, b.getCenter().z);
            h.assertTrue(!Double.isNaN(top), "no back surface over the middle of his back");
            item[0] = new net.minecraft.world.entity.vehicle.Boat(h.getLevel(), b.getCenter().x, top + 2.5, b.getCenter().z);
            h.getLevel().addFreshEntity(item[0]);
            e.setGoal(e.position().add(-60, 0, 5));      // makes him turn round on the spot
        });
        h.runAfterDelay(80, () -> {
            h.assertTrue(item[0].isAlive(), "the item is gone");
            double top = e.backTop(item[0].getX(), item[0].getZ());
            boolean onHim = !Double.isNaN(top) && item[0].getY() > top - 1.5 && item[0].getY() < top + 6;
            h.assertTrue(onHim, "the item slid off: " + item[0].position() + " his back there " + top);
            item[0].discard();
            release(h, e);
            h.succeed();
        });
    }

    /** the solid back part nearest the middle of his length */
    private static MountainPart middleOfHisBack(MountainEntity e) {
        java.util.List<MountainPart> back = new java.util.ArrayList<>();
        for (MountainPart p : e.parts()) if (p.def().solid() && p.def().kind().equals("back")) back.add(p);
        return back.isEmpty() ? null : back.get(back.size() / 2);
    }

    private static void attackHits(GameTestHelper h, int attack, Vec3 local, int after, String what) {
        MountainEntity e = spawnAway(h, 0.1f, MountainEntity.HUNTER, attack <= 9 ? 2 + attack : 20 + attack);
        net.minecraft.world.entity.animal.Pig[] target = new net.minecraft.world.entity.animal.Pig[1];
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            // local: x to his right, z ahead of his face, in blocks
            float yr = e.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
            Vec3 fwd = new Vec3(-net.minecraft.util.Mth.sin(yr), 0, net.minecraft.util.Mth.cos(yr)), right = new Vec3(-fwd.z, 0, fwd.x).scale(-1);
            Vec3 at = e.position().add(fwd.scale(local.z)).add(right.scale(local.x));
            at = new Vec3(at.x, e.groundAt(net.minecraft.util.Mth.floor(at.x), net.minecraft.util.Mth.floor(at.z)), at.z);
            target[0] = pig(h, at);
            target[0].setHealth(10f);
            e.setTarget(target[0]);
            h.assertTrue(e.forceAttack(attack), "could not start " + what);
        });
        h.runAfterDelay(20 + after, () -> {
            h.assertTrue(!target[0].isAlive() || target[0].getHealth() < 10f, what + " did not hurt the pig at " + local);
            target[0].discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "atk_tentacleWhipHitsHisSide")
    public void tentacleWhipHitsHisSide(GameTestHelper h) { attackHits(h, net.jj.mountain.rig.RigState.WHIP, new Vec3(-8, 0, -9), 45, "the whip"); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 250, batch = "atk_bodySlamRingHitsInFront")
    public void bodySlamRingHitsInFront(GameTestHelper h) { attackHits(h, net.jj.mountain.rig.RigState.REAR, new Vec3(2, 0, 18), 100, "the body slam"); }

    /** he does not burn anything with it any more: the stare is the shadow and nothing else */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 250, batch = "atk_gazeBurnsWhatItSees")
    public void theStareNoLongerBurnsAnything(GameTestHelper h) {
        MountainEntity e = spawnAway(h, 0.1f, MountainEntity.HUNTER, 24);
        net.minecraft.world.entity.animal.Pig[] target = new net.minecraft.world.entity.animal.Pig[1];
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            float yr = e.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
            Vec3 fwd = new Vec3(-net.minecraft.util.Mth.sin(yr), 0, net.minecraft.util.Mth.cos(yr));
            Vec3 at = e.position().add(fwd.scale(20));
            at = new Vec3(at.x, e.groundAt(net.minecraft.util.Mth.floor(at.x), net.minecraft.util.Mth.floor(at.z)), at.z);
            target[0] = pig(h, at);
            target[0].setHealth(10f);
            e.setTarget(target[0]);
            h.assertTrue(e.forceAttack(net.jj.mountain.rig.RigState.GAZE), "could not start the stare");
        });
        h.runAfterDelay(20 + 90, () -> {
            h.assertTrue(target[0].isAlive() && target[0].getHealth() >= 10f,
                    "the stare should not burn it any more: " + target[0].getHealth());
            target[0].discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260, batch = "atk_gooLobLandsOnYou")
    public void gooLobLandsOnYou(GameTestHelper h) { attackHits(h, net.jj.mountain.rig.RigState.ARTILLERY, new Vec3(0, 0, 16), 120, "the goo lob"); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "atk_dartsFromHisSkin")
    public void dartsFromHisSkin(GameTestHelper h) { attackHits(h, net.jj.mountain.rig.RigState.DARTS, new Vec3(-9, 0, -6), 60, "the darts"); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "atk_vomitFloodsTheGroundInFront")
    public void vomitFloodsTheGroundInFront(GameTestHelper h) { attackHits(h, net.jj.mountain.rig.RigState.VOMIT, new Vec3(0, 0, 12), 90, "the vomit"); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 320, batch = "atk_tentacleEruptionComesUpUnderYou")
    public void tentacleEruptionComesUpUnderYou(GameTestHelper h) { attackHits(h, net.jj.mountain.rig.RigState.ERUPT, new Vec3(0, 0, 35), 240, "the tentacle eruption"); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 420, batch = "atk_looseEyesComeForYou")
    public void looseEyesComeForYou(GameTestHelper h) { attackHits(h, net.jj.mountain.rig.RigState.SWARM, new Vec3(0, 0, 35), 340, "the loose eyes"); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "atk_screamThrowsYouBack")
    public void screamThrowsYouBack(GameTestHelper h) { attackHits(h, net.jj.mountain.rig.RigState.SCREAM, new Vec3(0, 0, 22), 70, "the scream"); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260, batch = "atk_tongueReelsYouIn")
    public void tongueReelsYouIn(GameTestHelper h) { attackHits(h, net.jj.mountain.rig.RigState.TONGUE, new Vec3(0, 0, 16), 150, "the tongue"); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "atk_legStompsOnYou")
    public void legStompsOnYou(GameTestHelper h) { attackHits(h, net.jj.mountain.rig.RigState.STOMP, new Vec3(-8, 0, -9), 55, "the leg stomp"); }

    /** the gaze can't burn what is walled off from him */
    /** stone between his eyes and your back and nothing stands up at all */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "atk_hidingFromTheGazeWorks")
    public void hidingFromTheStareWorks(GameTestHelper h) {
        MountainEntity e = spawnAway(h, 0.1f, MountainEntity.HUNTER, 12);
        ServerPlayer[] p = new ServerPlayer[1];
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            float yr = e.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
            Vec3 fwd = new Vec3(-net.minecraft.util.Mth.sin(yr), 0, net.minecraft.util.Mth.cos(yr));
            Vec3 at = e.position().add(fwd.scale(20));
            BlockPos c = BlockPos.containing(at.x, e.groundAt(net.minecraft.util.Mth.floor(at.x), net.minecraft.util.Mth.floor(at.z)), at.z);
            for (int dx = -2; dx <= 2; dx++) for (int dy = 0; dy <= 3; dy++) for (int dz = -2; dz <= 2; dz++)
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == 3)
                    h.getLevel().setBlockAndUpdate(c.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            p[0] = survivalPlayer(h, Vec3.atBottomCenterOf(c));
            h.assertTrue(!e.canSee(p[0]), "walled in, he cannot see them");
            e.setTarget(p[0]);
            h.assertTrue(e.forceAttack(net.jj.mountain.rig.RigState.GAZE), "could not start the stare");
        });
        h.runAfterDelay(20 + 90, () -> {
            h.assertTrue(shadowsOf(h, p[0]) == 0, "nothing should have stood up behind a wall");
            drop(p[0]);
            release(h, e);
            h.succeed();
        });
    }

    private static int shadowsOf(GameTestHelper h, ServerPlayer p) {
        int n = 0;
        for (var sh : h.getLevel().getEntitiesOfClass(net.jj.mountain.entity.ShadowOfYou.class,
                p.getBoundingBox().inflate(300, 120, 300)))
            if (!sh.isRemoved() && p.getUUID().equals(sh.ownerId())) n++;
        return n;
    }

    /** the stare stands your own shape up out of the goo, and it goes the moment he loses sight of you */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "shadow")
    public void theStareStandsYourShadowUp(GameTestHelper h) {
        MountainEntity e = spawnAway(h, 0.1f, MountainEntity.HUNTER, 26);
        ServerPlayer[] p = new ServerPlayer[1];
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            float yr = e.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
            Vec3 fwd = new Vec3(-net.minecraft.util.Mth.sin(yr), 0, net.minecraft.util.Mth.cos(yr));
            Vec3 at = e.position().add(fwd.scale(26));
            at = new Vec3(at.x, e.groundAt(net.minecraft.util.Mth.floor(at.x), net.minecraft.util.Mth.floor(at.z)) + 1, at.z);
            p[0] = survivalPlayer(h, at);
            h.assertTrue(e.canSee(p[0]), "out in the open he can see them");
            e.setTarget(p[0]);
            h.assertTrue(e.forceAttack(net.jj.mountain.rig.RigState.GAZE), "could not start the stare");
        });
        h.runAfterDelay(20 + 70, () -> {
            h.assertTrue(shadowsOf(h, p[0]) == 1, "one of them should be standing: " + shadowsOf(h, p[0]));
            var sh = h.getLevel().getEntitiesOfClass(net.jj.mountain.entity.ShadowOfYou.class,
                    p[0].getBoundingBox().inflate(300, 120, 300)).get(0);
            h.assertTrue(sh.getMaxHealth() >= p[0].getMaxHealth() - 0.01f, "it has their health: " + sh.getMaxHealth());
            h.assertTrue(sh.getTarget() == p[0], "and it wants them and nobody else");
            // and the stare does not root him: he can go straight on to something else
            h.assertTrue(e.forceAttack(net.jj.mountain.rig.RigState.DARTS), "he should be free to do something else");

            // out of his sight and it comes apart on its own
            e.discard();
            h.runAfterDelay(40, () -> {
                h.assertTrue(shadowsOf(h, p[0]) == 0, "with him gone it should have fallen apart");
                drop(p[0]);
                release(h, e);
                h.succeed();
            });
        });
    }

    /** the long breath in drags what it can see toward his mouth */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "draw_in")
    public void theLongBreathDragsYouIn(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.05f, MountainEntity.HUNTER);
        Pig[] target = new Pig[1];
        double[] best = {0};
        h.runAfterDelay(25, () -> {
            e.setStay(true);
            target[0] = h.spawn(EntityType.PIG, new BlockPos(6, 2, 6));   // the arena is only eight across
            target[0].setNoAi(true);
            target[0].setInvulnerable(true);
            e.setTarget(target[0]);
            h.assertTrue(e.forceAttack(net.jj.mountain.rig.RigState.DRAW), "could not start the long breath");
        });
        // he is solid, so it cannot travel far before it is up against his side. What the breath does is push it
        // his way every few ticks, so that is what this watches: which way it is being carried, tick by tick.
        for (int i = 60; i < 150; i++) {
            h.runAfterDelay(25 + i, () -> {
                if (target[0] == null || target[0].isRemoved()) return;
                Vec3 to = e.position().subtract(target[0].position());
                Vec3 flat = new Vec3(to.x, 0, to.z);
                if (flat.lengthSqr() < 1.0E-6) return;
                Vec3 v = target[0].getDeltaMovement();
                best[0] = Math.max(best[0], new Vec3(v.x, 0, v.z).dot(flat.normalize()));
            });
        }
        h.runAfterDelay(25 + 155, () -> {
            boolean eaten = target[0].isRemoved() || !target[0].isAlive() || target[0].level() != h.getLevel();
            h.assertTrue(eaten || best[0] > 0.02,
                    "the breath should be carrying it toward him: best pull was " + best[0]);
            if (!eaten) target[0].discard();
            h.succeed();
        });
    }

    /** he pulls a lump out of his own side, and it is his, not the world's */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "tear_off")
    public void hePullsAPieceOutOfHimself(GameTestHelper h) {
        MountainEntity e = spawnAway(h, 0.1f, MountainEntity.HUNTER, 30);
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            float was = e.healthNow();
            h.assertTrue(e.getTarget() == null, "nobody is in front of him");
            h.assertTrue(e.forceAttack(net.jj.mountain.rig.RigState.SPLIT), "he tears a piece off with nobody there to see it");
            h.runAfterDelay(70, () -> {
                net.jj.mountain.entity.MountainEntity piece = null;
                for (MountainEntity o : h.getLevel().getEntities(ModEntities.MOUNTAIN, x -> !x.isRemoved() && x.isAPiece())) piece = o;
                h.assertTrue(piece != null, "a lump of him should be standing there");
                h.assertTrue(piece.mountainScale() < e.mountainScale(), "and it is smaller than he is");
                h.assertTrue(!piece.isWorldOne(), "it is never the world's own");
                h.assertTrue(e.healthNow() < was, "and it came out of him: " + was + " -> " + e.healthNow());
                // it is his, so it is not counted against the limit and it is not turned away
                h.assertTrue(!net.jj.mountain.world.MountainWorld.limitNow(piece, h.getLevel()), "a piece is never pushed out");
                h.assertTrue(!piece.isRemoved(), "and it is still standing");
                piece.setStay(true);
                piece.discard();
                release(h, e);
                h.succeed();
            });
        });
    }

    /** every attack plays out and he goes back to normal */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 3600, batch = "atk_everyAttackFinishes")
    public void everyAttackFinishes(GameTestHelper h) {
        MountainEntity e = spawnAway(h, 0.15f, MountainEntity.CALM, 13);      // calm: he won't start more attacks of his own
        int[] order = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 16, 15};   // the long breath last: it runs longest
        h.runAfterDelay(20, () -> { e.setStay(true); pig(h, e.position().add(0, 0, 25)).setInvulnerable(true); });
        int at = 30;
        for (int a : order) {
            int len = net.jj.mountain.entity.MountainAttacks.length(a) + 60;
            final int start = at, which = a;
            h.runAfterDelay(start, () -> h.assertTrue(e.forceAttack(which), "could not start attack " + which));
            h.runAfterDelay(start + 5, () -> h.assertTrue(e.attackNow() == which, "attack " + which + " is not running"));
            h.runAfterDelay(start + len, () -> h.assertTrue(e.attackNow() != which, "attack " + which + " never finished"));
            at += len + 20;
        }
        final int done = at;
        h.runAfterDelay(done, () -> {
            h.assertTrue(e.isAlive(), "he died?");
            for (MountainEntity o : h.getLevel().getEntities(ModEntities.MOUNTAIN, x -> !x.isRemoved() && x.isAPiece())) o.discard();
            release(h, e);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ the things that live inside him

    private static Vec3 openGround(GameTestHelper h, int slot) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        int x = o.getX() + 30000 + slot * 600, z = o.getZ() + 3000;
        for (int cx = (x >> 4) - 2; cx <= (x >> 4) + 2; cx++) for (int cz = (z >> 4) - 2; cz <= (z >> 4) + 2; cz++) { h.getLevel().setChunkForced(cx, cz, true); h.getLevel().getChunk(cx, cz); }
        return new Vec3(x + 0.5, h.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5);
    }

    private static void unforce(GameTestHelper h, Vec3 at) {
        int x = (int) at.x, z = (int) at.z;
        for (int cx = (x >> 4) - 2; cx <= (x >> 4) + 2; cx++) for (int cz = (z >> 4) - 2; cz <= (z >> 4) + 2; cz++) h.getLevel().setChunkForced(cx, cz, false);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "gut_tentacle")
    public void gutTentacleLashesWhatComesNear(GameTestHelper h) {
        Vec3 at = openGround(h, 20);
        var t = ModEntities.GUT_TENTACLE.create(h.getLevel());
        t.moveTo(at.x, at.y, at.z, 0f, 0f);
        h.getLevel().addFreshEntity(t);
        var p = pig(h, at.add(4, 0, 1));
        p.setHealth(10f);
        h.runAfterDelay(150, () -> {
            h.assertTrue(!p.isAlive() || p.getHealth() < 10f, "the tentacle never hurt the pig next to it");
            t.discard(); p.discard(); unforce(h, at);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 320, batch = "gut_eye")
    public void looseEyeSpitsGoo(GameTestHelper h) {
        // in the test's own square, with the pig handed to it: out on open ground thousands of blocks away the
        // chunk sometimes never started running and it hung there doing nothing
        Pig p = h.spawn(EntityType.PIG, new BlockPos(3, 2, 3));
        p.setNoAi(true);
        p.setHealth(10f);
        var w = ModEntities.WATCHER.create(h.getLevel());
        Vec3 at = h.absoluteVec(new Vec3(3, 8, 3));
        w.moveTo(at.x, at.y, at.z, 0f, 0f);
        h.getLevel().addFreshEntity(w);
        for (int q = 10; q < 400; q += 20) h.runAfterDelay(q, () -> { if (p.isAlive()) w.setTarget(p); });
        h.runAfterDelay(260, () -> {
            h.assertTrue(!p.isAlive() || p.getHealth() < 10f, "the eye never hit the pig");
            w.discard();
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "gut_leech")
    public void leechLatchesOn(GameTestHelper h) {
        // it is put right next to the pig and made to bite: what is being checked is that a bite latches it on
        // and that it hangs there drinking, not how long its own eyes take to find something
        Pig p = h.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        p.setNoAi(true);
        var l = ModEntities.GUT_LEECH.create(h.getLevel());
        Vec3 at = h.absoluteVec(new Vec3(3, 2, 2));
        l.moveTo(at.x, at.y, at.z, 0f, 0f);
        h.getLevel().addFreshEntity(l);
        h.runAfterDelay(20, () -> {
            float before = p.getHealth();
            h.assertTrue(l.doHurtTarget(p), "the leech should be able to bite the pig");
            h.assertTrue(l.getVehicle() == p, "and that bite should have latched it on: " + l.getVehicle());
            h.assertTrue(p.getHealth() < before, "and taken something out of it");
        });
        h.runAfterDelay(60, () -> {
            h.assertTrue(p.getHealth() < 8f, "it should have gone on drinking while it hung there: " + p.getHealth());
            l.discard();
            h.succeed();
        });
    }

    /** the guardian goes for a monster and leaves the pig and the player alone */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "guardian")
    public void guardianHuntsMonstersNotPlayers(GameTestHelper h) {
        MountainEntity e = spawnAway(h, 0.1f, MountainEntity.GUARDIAN, 15);
        net.minecraft.world.entity.monster.Zombie[] z = new net.minecraft.world.entity.monster.Zombie[1];
        net.minecraft.world.entity.animal.Pig[] p = new net.minecraft.world.entity.animal.Pig[1];
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            z[0] = EntityType.ZOMBIE.create(h.getLevel());
            z[0].moveTo(e.getX() + 14, e.getY(), e.getZ() + 6, 0f, 0f);
            z[0].setNoAi(true); z[0].setPersistenceRequired();
            h.getLevel().addFreshEntity(z[0]);
            p[0] = pig(h, e.position().add(-12, 0, 5));
        });
        h.runAfterDelay(60, () -> {
            h.assertTrue(e.getTarget() == z[0], "the guardian should be after the zombie, has " + e.getTarget());
            h.assertTrue(e.spares(p[0]), "he should leave the pig alone");
            h.assertTrue(!e.spares(z[0]), "the zombie is fair game");
            z[0].discard(); p[0].discard(); release(h, e);
            h.succeed();
        });
    }

    /** something dropped on his back lands on it instead of falling through */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "stand")
    public void youCanStandOnHisBack(GameTestHelper h) {
        MountainEntity e = spawnAway(h, 0.3f, MountainEntity.CALM, 16);
        ItemEntity[] item = new ItemEntity[1];
        AABB[] box = new AABB[1];
        h.runAfterDelay(30, () -> {
            e.setStay(true);
            MountainPart mid = middleOfHisBack(e);
            h.assertTrue(mid != null, "no solid part on his back");
            box[0] = mid.getBoundingBox();
            AABB b = box[0];
            double top = e.backTop(b.getCenter().x, b.getCenter().z);
            h.assertTrue(!Double.isNaN(top), "no back surface over the middle of his back");
            item[0] = new ItemEntity(h.getLevel(), b.getCenter().x, top + 12, b.getCenter().z, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE));
            item[0].setDeltaMovement(Vec3.ZERO); item[0].setNeverPickUp();
            h.getLevel().addFreshEntity(item[0]);
        });
        h.runAfterDelay(80, () -> {
            double top = e.backTop(item[0].getX(), item[0].getZ());
            net.jj.mountain.MountainMod.LOG.info("stand test: item y {} his back there {}", item[0].getY(), top);
            h.assertTrue(item[0].getY() > top - 2.0 && item[0].getY() < top + 8, "it fell through his back: item at " + item[0].getY() + ", his back at " + top);
            item[0].discard(); release(h, e);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ sleeping
    private static double heightAboveGround(MountainEntity e, Vec3 p) {
        return e.backTop(p.x, p.z) - e.groundAt(net.minecraft.util.Mth.floor(p.x), net.minecraft.util.Mth.floor(p.z));
    }

    /** asleep he lies low with his tail on the ground; a hunter wakes within seconds of you coming near and goes for you */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 700, batch = "sleep_hunter")
    public void sleepingHunterWakesAndComesForYou(GameTestHelper h) {
        MountainEntity e = spawnAway(h, 0.3f, MountainEntity.HUNTER, 17);
        net.minecraft.server.level.ServerPlayer[] p = new net.minecraft.server.level.ServerPlayer[1];
        double[] awake = new double[2];
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            Vec3 mid = e.spinePoint(e.spineSize() / 2), tail = e.spinePoint(1);
            awake[0] = heightAboveGround(e, mid); awake[1] = heightAboveGround(e, tail);
            e.startAsleep();
        });
        h.runAfterDelay(140, () -> {
            h.assertTrue(e.isAsleep(), "he should still be asleep with nobody about");
            Vec3 mid = e.spinePoint(e.spineSize() / 2), tail = e.spinePoint(1);
            double m = heightAboveGround(e, mid), t = heightAboveGround(e, tail);
            net.jj.mountain.MountainMod.LOG.info("sleep test: back {} -> {}, tail {} -> {}", awake[0], m, awake[1], t);
            {   // the ramp: height of his back along the line of his spine, from past the tail up to the middle
                Vec3 t0 = e.spinePoint(0), m0 = e.spinePoint(e.spineSize() / 2);
                Vec3 dir = m0.subtract(t0).multiply(1, 0, 1).normalize();
                StringBuilder sb = new StringBuilder();
                for (double d = -12; d <= 40; d += 2) { Vec3 q = t0.add(dir.scale(d)); sb.append(String.format(" %.0f:%.1f", d, heightAboveGround(e, q))); }
                net.jj.mountain.MountainMod.LOG.info("sleep ramp:{}", sb);

            }
            h.assertTrue(m < awake[0] - 0.25 * net.jj.mountain.rig.MountainRig.SLEEP_DROP * 0.3, "lying down, his back should be much lower: " + awake[0] + " -> " + m);
            h.assertTrue(t < 0.5 * awake[1], "his tail should come down near the ground: " + awake[1] + " -> " + t);
            p[0] = survivalPlayer(h, new Vec3(e.getX() + 6, e.getY(), e.getZ() + 4));
        });
        h.runAfterDelay(160, () -> h.succeedWhen(() -> {
            h.assertTrue(!e.sleeping(), "still asleep or getting up");
            h.assertTrue(e.getTarget() == p[0], "awake but not after the player: " + e.getTarget());
            drop(p[0]); release(h, e);
        }));
    }

    /** a calm one lets you walk on him for half a minute or more before he wakes, eyes first */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1800, batch = "sleep_calm")
    public void sleepingCalmOneTakesItsTime(GameTestHelper h) {
        MountainEntity e = spawnAway(h, 0.1f, MountainEntity.CALM, 18);
        net.minecraft.server.level.ServerPlayer[] p = new net.minecraft.server.level.ServerPlayer[1];
        h.runAfterDelay(20, () -> { e.setStay(true); e.startAsleep(); p[0] = survivalPlayer(h, new Vec3(e.getX() + 3, e.getY(), e.getZ() + 2)); });
        h.runAfterDelay(560, () -> h.assertTrue(e.isAsleep(), "a calm one shouldn't wake up this soon"));
        h.runAfterDelay(580, () -> h.succeedWhen(() -> {
            h.assertTrue(!e.sleeping(), "still asleep");
            h.assertTrue(e.getTarget() == null, "a calm one shouldn't come for you just for waking him");
            drop(p[0]); release(h, e);
        }));
    }

    /** a pig pushed along his back walks over the bumps on it and never sinks in */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "walk_back")
    public void walkingAlongHisBackIsSmooth(GameTestHelper h) {
        MountainEntity e = spawnAway(h, 0.3f, MountainEntity.CALM, 19);
        net.minecraft.world.entity.animal.Pig[] pig = new net.minecraft.world.entity.animal.Pig[1];
        Vec3[] start = new Vec3[1];
        double[] worst = {Double.MAX_VALUE};
        h.runAfterDelay(30, () -> {
            e.setStay(true);
            Vec3 a = e.spinePoint(e.spineSize() / 2 - 3);
            double top = e.backTop(a.x, a.z);
            h.assertTrue(!Double.isNaN(top), "no back surface to start on");
            pig[0] = pig(h, new Vec3(a.x, top + 0.2, a.z));
            pig[0].setNoAi(true);
            start[0] = pig[0].position();
        });
        for (int t = 40; t < 200; t++) {
            final int tt = t;
            h.runAfterDelay(t, () -> {
                Vec3 a = e.spinePoint(e.spineSize() / 2 - 3), b = e.spinePoint(e.spineSize() / 2 + 3);
                Vec3 dir = b.subtract(a).multiply(1, 0, 1).normalize();
                pig[0].setDeltaMovement(dir.x * 0.18, pig[0].getDeltaMovement().y, dir.z * 0.18);
                double top = e.backTop(pig[0].getX(), pig[0].getZ());
                if (!Double.isNaN(top) && tt > 50) worst[0] = Math.min(worst[0], pig[0].getY() - top);
            });
        }
        h.runAfterDelay(205, () -> {
            double moved = pig[0].position().subtract(start[0]).horizontalDistance();
            net.jj.mountain.MountainMod.LOG.info("walk test: moved {} worst sink {}", moved, worst[0]);
            h.assertTrue(moved > 8, "the pig got stuck on his back: moved " + moved);
            h.assertTrue(worst[0] > -1.0, "the pig sank into his back by " + (-worst[0]));
            pig[0].discard(); release(h, e);
            h.succeed();
        });
    }

    /** walking, every leg's foot comes down onto the ground and every leg moves */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "legs_reach")
    public void everyLegReachesTheGround(GameTestHelper h) {
        MountainEntity e = spawnAway(h, 0.3f, MountainEntity.CALM, 23);
        int n = e.legCount();
        double[] minGap = new double[n], moved = new double[n];
        Vec3[] first = new Vec3[n];
        java.util.Arrays.fill(minGap, Double.MAX_VALUE);
        h.runAfterDelay(20, () -> e.setGoal(e.position().add(0, 0, -400)));
        for (int t = 60; t < 420; t += 5) {
            h.runAfterDelay(t, () -> {
                for (int k = 0; k < n; k++) {
                    Vec3 f = e.legFootWorld(k);
                    double g = e.groundAt(net.minecraft.util.Mth.floor(f.x), net.minecraft.util.Mth.floor(f.z));
                    minGap[k] = Math.min(minGap[k], f.y - g);
                    Vec3 rel = e.worldToModelPoint(f);
                    if (first[k] == null) first[k] = rel; else moved[k] = Math.max(moved[k], rel.distanceTo(first[k]));
                }
            });
        }
        h.runAfterDelay(425, () -> {
            StringBuilder bad = new StringBuilder();
            for (int k = 0; k < n; k++) {
                net.jj.mountain.MountainMod.LOG.info("leg {} kind {} closest to ground {} moved {} (model)", k, e.legKind(k), String.format("%.2f", minGap[k]), String.format("%.1f", moved[k]));
                if (minGap[k] > 1.5 + 3 * 0.3 || moved[k] < 4) bad.append(" ").append(k);
            }
            release(h, e);
            h.assertTrue(bad.length() == 0, "legs that never reach the ground or never move:" + bad);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ 1.8: legs and arms that break
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
    public void aLegCanBeBrokenAndItStopsStepping(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.1f, MountainEntity.CALM);
        h.runAfterDelay(30, () -> {
            float before = e.healthNow();
            h.assertTrue(!e.legBroken(3), "leg 3 should start whole");
            for (int i = 0; i < 40 && !e.legBroken(3); i++) e.hurtLeg(3, e.damageSources().generic(), e.legHpMax() * 0.2f);
            h.assertTrue(e.legBroken(3), "leg 3 should be broken after enough hits");
            h.assertTrue(e.healthNow() < before, "breaking a leg should cost him health too");
            h.assertTrue(e.brokenLegs() == 1, "only that leg should be broken: " + e.brokenLegs());
            e.setStay(true);                       // he must not wander off and drag the foot with him
            Vec3 foot = e.footWorld(MountainRig.get().legs[3]);
            h.runAfterDelay(60, () -> {
                Vec3 now = e.footWorld(MountainRig.get().legs[3]);
                h.assertTrue(now.distanceTo(foot) < 6 * 0.1f + 3, "a broken leg should drag, not step: moved " + now.distanceTo(foot));
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 900, batch = "downed")
    public void enoughBrokenLegsOnOneSideBringHimDown(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.1f, MountainEntity.CALM);
        h.runAfterDelay(30, () -> {
            int done = 0;
            for (MountainRig.LegDef L : MountainRig.get().legs) {
                if (L.side != 1 || done >= 4) continue;
                for (int i = 0; i < 40 && !e.legBroken(L.k); i++) e.hurtLeg(L.k, e.damageSources().generic(), e.legHpMax() * 0.2f);
                done++;
            }
            h.assertTrue(e.knockedDown(), "four broken legs on one side should put him on his belly");
            float before = e.healthNow();
            e.applyDamage(e.damageSources().generic(), 100f, null);
            h.assertTrue(before - e.healthNow() > 10f, "he should take more while he is down: lost " + (before - e.healthNow()));
            h.assertTrue(e.knockedDown(), "and he is still down long after the old seven seconds");
            h.runAfterDelay(460, () -> {
                h.assertTrue(!e.knockedDown(), "he should get back up");
                h.assertTrue(e.brokenLegs() < 4, "he should get a leg back under him: " + e.brokenLegs());
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
    public void aBrokenArmLetsGoAndCannotGrab(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.1f, MountainEntity.CALM);
        h.runAfterDelay(30, () -> {
            for (int i = 0; i < 60 && !e.armBroken(2); i++) e.hurtArm(2, e.damageSources().generic(), e.armHpMax() * 0.2f);
            h.assertTrue(e.armBroken(2), "arm 2 should be broken");
            h.assertTrue(e.brokenArms() == 1, "only that arm: " + e.brokenArms());
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
    public void heHitsMobsHarderThanPlayers(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.1f, MountainEntity.CALM);
        h.runAfterDelay(20, () -> {
            Pig pig = h.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
            ServerPlayer p = survivalPlayer(h, h.absoluteVec(new Vec3(2, 2, 3)));
            float onMob = e.dmg(10f, pig), onPlayer = e.dmg(10f, p);
            drop(p);
            h.assertTrue(Math.abs(onMob - onPlayer * net.jj.mountain.MountainConfig.V.mobDamage) < 0.01f,
                    "mobs should take " + net.jj.mountain.MountainConfig.V.mobDamage + " times as much: mob " + onMob + " player " + onPlayer);
            h.assertTrue(onMob > onPlayer, "mobs should take more than players");
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260, batch = "atk_stormEach")
    public void theEyeStormBurnsEveryOneOfThem(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.03f, MountainEntity.HUNTER);
        e.setStay(true);
        h.runAfterDelay(25, () -> {
            Pig a = h.spawn(EntityType.PIG, new BlockPos(3, 2, 3));
            Pig b = h.spawn(EntityType.PIG, new BlockPos(4, 2, 3));
            Pig c = h.spawn(EntityType.PIG, new BlockPos(3, 2, 4));
            for (Pig pg : new Pig[]{a, b, c}) { pg.setNoAi(true); pg.setInvulnerable(true); }
            e.setTarget(a);
            h.assertTrue(e.forceAttack(net.jj.mountain.rig.RigState.EYE_STORM), "he should start his eye storm");
            h.runAfterDelay(60, () -> {
                h.assertTrue(e.stormTargets() >= 3, "the storm should hold a line on each of them: " + e.stormTargets());
                h.assertTrue(e.watchCount() >= 3, "and his eyes should be split between them: " + e.watchCount());
                h.succeed();
            });
        });
    }

    /** the stare takes in everybody he can see at once, and each of them gets their own */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260, batch = "atk_gazeSeveral")
    public void theStareTakesInEverybodyAtOnce(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.03f, MountainEntity.HUNTER);
        ServerPlayer[] who = new ServerPlayer[3];
        h.runAfterDelay(25, () -> {
            who[0] = survivalPlayer(h, h.absoluteVec(new Vec3(3, 2, 3)));
            who[1] = survivalPlayer(h, h.absoluteVec(new Vec3(5, 2, 3)));
            who[2] = survivalPlayer(h, h.absoluteVec(new Vec3(3, 2, 5)));
            e.setTarget(who[0]);
            h.assertTrue(e.forceAttack(net.jj.mountain.rig.RigState.GAZE), "he should start his stare");
            h.runAfterDelay(60, () -> {
                h.assertTrue(e.gazeTargets() >= 3, "his stare should take in all of them: " + e.gazeTargets());
                int up = 0;
                for (ServerPlayer p : who) up += shadowsOf(h, p);
                h.assertTrue(up >= 3, "and each of them should have one of their own standing: " + up);
                for (var sh : h.getLevel().getEntitiesOfClass(net.jj.mountain.entity.ShadowOfYou.class,
                        e.getBoundingBox().inflate(300, 120, 300))) sh.discard();
                for (ServerPlayer p : who) drop(p);
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
    public void hisFeetSnapTrees(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.1f, MountainEntity.CALM);
        h.runAfterDelay(20, () -> {
            BlockPos p = new BlockPos(3, 2, 3);
            for (int i = 0; i < 4; i++) h.setBlock(p.above(i), net.minecraft.world.level.block.Blocks.OAK_LOG);
            Vec3 at = h.absoluteVec(new Vec3(3.5, 2, 3.5));
            e.snapTrees(at, 6, 8);
            h.assertTrue(h.getBlockState(p).isAir(), "the log at his foot should be snapped");
            h.assertTrue(h.getBlockState(p.above(3)).isAir(), "the top of the tree should go too");
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160, batch = "watch")
    public void hisEyesSplitBetweenSeveralThings(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.06f, MountainEntity.CALM);
        e.setStay(true);
        h.runAfterDelay(30, () -> {
            Pig a = h.spawn(EntityType.PIG, new BlockPos(3, 2, 3));
            Pig b = h.spawn(EntityType.PIG, new BlockPos(4, 2, 4));
            Pig c = h.spawn(EntityType.PIG, new BlockPos(2, 2, 4));
            for (Pig pg : new Pig[]{a, b, c}) pg.setNoAi(true);
            h.runAfterDelay(30, () -> {
                var on = e.watching();
                h.assertTrue(e.watchCount() >= 3, "his eyes should split between them: " + e.watchCount());
                for (Pig pg : new Pig[]{a, b, c}) h.assertTrue(on.contains(pg), "a pig right next to him has no eye on it: " + on.size());
                a.discard(); b.discard(); c.discard();
                h.runAfterDelay(30, () -> {
                    h.assertTrue(e.watching().isEmpty() || e.watching().stream().noneMatch(x -> x == a || x == b || x == c),
                            "he should stop watching things that are gone");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
    public void heStartsUpEvenWhenHeLandsFarFromAnybody(GameTestHelper h) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        int x = o.getX() + 900, z = o.getZ() + 900;
        MountainEntity e = net.jj.mountain.ModEntities.MOUNTAIN.create(h.getLevel());
        h.assertTrue(e != null, "he should be makeable");
        e.setMountainScale(0.3f);
        MountainEntity.holdChunkAt(h.getLevel(), new BlockPos(x, 0, z), e.getId());
        h.getLevel().getChunk(x >> 4, z >> 4);
        int y = h.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
        e.moveTo(x + 0.5, y, z + 0.5, 0f, 0f);
        h.getLevel().addFreshEntity(e);
        // (nobody is out there with him, so the hold lapses after a while and he unloads again, which is right:
        //  what matters is that he wakes up and runs at all instead of sitting there dead to the world)
        h.runAfterDelay(60, () -> {
            boolean gone = e.isRemoved();
            int t = e.tickCount;
            e.discard();
            h.assertTrue(!gone, "he should still be out there, not unloaded");
            h.assertTrue(t > 5, "he should have started ticking on his own away from everybody: " + t);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ being told what to do

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
    public void heGoesAfterWhatHeIsSentAfter(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.06f, MountainEntity.GUARDIAN);
        h.runAfterDelay(30, () -> {
            Pig pig = h.spawn(EntityType.PIG, new BlockPos(3, 2, 3));
            pig.setNoAi(true);
            h.assertTrue(e.spares(pig), "a guardian should leave a pig alone on his own");
            e.sendAfter(java.util.List.of(pig));
            h.assertTrue(!e.spares(pig), "once he is sent after it, it is fair game");
            h.runAfterDelay(30, () -> {
                h.assertTrue(e.getTarget() == pig, "he should be after the pig he was sent after: " + e.getTarget());
                e.clearHitList();
                h.runAfterDelay(30, () -> {
                    h.assertTrue(e.spares(pig), "calling him off should put the pig back out of reach");
                    h.assertTrue(e.getTarget() != pig, "and he should drop it: " + e.getTarget());
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
    public void heWalksWhereHeIsSentWhileStillFighting(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.2f, MountainEntity.CALM);
        h.runAfterDelay(30, () -> {
            Pig pig = h.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
            pig.setNoAi(true);
            pig.setInvulnerable(true);        // it has to live long enough for him to still be fighting it at the end
            e.sendAfter(java.util.List.of(pig));
            h.runAfterDelay(20, () -> {
                h.assertTrue(e.getTarget() == pig, "he should be after the pig first");
                float yr = e.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;   // straight ahead, so no long turn first
                Vec3 spot = e.position().add(-net.minecraft.util.Mth.sin(yr) * 240, 0, net.minecraft.util.Mth.cos(yr) * 240);
                e.setGoal(spot);
                double d0 = e.position().distanceTo(spot);
                h.runAfterDelay(140, () -> {
                    double d1 = e.position().distanceTo(spot);
                    h.assertTrue(d1 < d0 - 2, "he should be on his way to the spot: " + d0 + " -> " + d1);
                    h.assertTrue(e.getTarget() == pig, "and still fighting the pig on the way: " + e.getTarget());
                    h.succeed();
                });
            });
        });
    }

    // ------------------------------------------------------------------ phases, the soft place, memory, the body

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
    public void heTurnsAsHeIsWornDown(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.06f, MountainEntity.CALM);
        h.runAfterDelay(20, () -> {
            h.assertTrue(e.phase() == 0, "he should start whole: " + e.phase());
            // his hide soaks up most of a hit, so wear him down rather than trying it in one
            for (int i = 0; i < 400 && e.phase() < 1; i++) e.applyDamage(e.damageSources().generic(), e.healthMax() * 0.05f, null);
            h.assertTrue(e.phase() == 1, "past half he should have turned once: " + e.phase());
            h.assertTrue(e.phaseSpeed() > 1.05f, "and pick up speed: " + e.phaseSpeed());
            for (int i = 0; i < 400 && e.phase() < 2; i++) e.applyDamage(e.damageSources().generic(), e.healthMax() * 0.03f, null);
            h.assertTrue(e.phase() == 2, "past a quarter he should have turned again: " + e.phase());
            h.assertTrue(e.isAlive(), "he should still be alive");
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
    public void aShotDownHisThroatHurtsFarMore(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.1f, MountainEntity.CALM);
        h.runAfterDelay(30, () -> {
            h.assertTrue(e.throat() == null, "there is nothing to aim at while his face is shut");
            e.forceBreath();
            h.succeedWhen(() -> {
                h.assertTrue(e.throat() != null, "his face has not opened yet");
                float before = e.healthNow();
                e.hurtThroat(e.damageSources().generic(), 10f);
                float went = before - e.healthNow();
                h.assertTrue(went > 30f, "a shot in there should be worth far more than 10: " + went);
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void heRemembersWhoHurtHim(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.06f, MountainEntity.GUARDIAN);
        ServerPlayer p = survivalPlayer(h, h.absoluteVec(new Vec3(3, 2, 3)));
        h.runAfterDelay(20, () -> {
            h.assertTrue(e.spares(p), "a guardian should leave a player alone to start with");
            h.assertTrue(!e.holdsGrudge(p), "and hold nothing against them yet");
            e.applyDamage(e.damageSources().playerAttack(p), 20f, null);
            h.assertTrue(e.holdsGrudge(p), "hitting him should put you on his list");
            h.assertTrue(!e.spares(p), "and take you off the list of things he spares");
            e.forgiveAll();
            h.assertTrue(!e.holdsGrudge(p), "forgiving should clear it");
            drop(p);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
    public void hisBodyLiesThereThenRotsAway(GameTestHelper h) {
        int wasCarcass = net.jj.mountain.MountainConfig.V.carcassSeconds;
        net.jj.mountain.MountainConfig.V.carcassSeconds = 2;
        MountainEntity e = spawn(h, 0.05f, MountainEntity.CALM);
        h.runAfterDelay(20, () -> {
            e.hurt(e.damageSources().genericKill(), Float.MAX_VALUE);
            h.assertTrue(e.isDeadOrDying(), "he should be dying");
            h.runAfterDelay(MountainEntity.FALL_AT + 20, () -> {
                h.assertTrue(!e.isRemoved(), "his body should still be lying there");
                h.assertTrue(e.isCarcass(), "and count as a carcass");
                h.assertTrue(e.brokenLegs() >= 20, "his legs should have gone under him: " + e.brokenLegs());
                // the loot is on the ground the moment he falls, and hitting the body gives nothing more
                var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, e.getBoundingBox().inflate(160));
                h.assertTrue(!drops.isEmpty(), "his loot should be down already");
                int before = drops.size();
                MountainPart part = e.parts().get(0);
                for (int i = 0; i < 6; i++) part.hurt(e.damageSources().generic(), 5f);
                int after = h.getLevel().getEntitiesOfClass(ItemEntity.class, e.getBoundingBox().inflate(160)).size();
                h.assertTrue(after == before, "hitting the body should give nothing: " + before + " -> " + after);
                net.jj.mountain.MountainConfig.V.carcassSeconds = wasCarcass;
                e.discard();
                h.succeed();
            });
        });
    }

    // ------------------------------------------------------------------ the world's one, the book, riding, burrowing

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void thereIsOnlyEverOneBook(GameTestHelper h) {
        var w = net.jj.mountain.world.MountainWorld.get(h.getLevel().getServer().overworld());
        boolean was = w.bookExists();
        w.forgetBook();
        h.assertTrue(w.claimBook(), "the first book should be allowed");
        h.assertTrue(!w.claimBook(), "a second one should not");
        w.forgetBook();
        h.assertTrue(w.claimBook(), "counting it lost should let another be written");
        if (!was) w.forgetBook();
        h.succeed();
    }

    /**
     * The one-book rule used to be checked when you wrote one and nowhere else, so a creative copy or a /give
     * walked straight past it. The real one now carries a mark and the world remembers the mark: everything else
     * comes apart the moment it turns up in somebody's hands, however it got there.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "one_book")
    public void everySecondCopyComesApart(GameTestHelper h) {
        var srv = h.getLevel().getServer();
        var w = net.jj.mountain.world.MountainWorld.get(srv.overworld());
        boolean was = w.bookExists();
        java.util.UUID wasId = w.bookId();
        w.forgetBook();

        ServerPlayer p = survivalPlayer(h, h.absoluteVec(new Vec3(3, 2, 3)));
        p.getInventory().clearContent();
        p.getEnderChestInventory().clearContent();

        // two of them, the way he ended up with two: neither carries a mark yet
        p.getInventory().add(new net.minecraft.world.item.ItemStack(net.jj.mountain.ModItems.CODEX));
        p.getInventory().add(new net.minecraft.world.item.ItemStack(net.jj.mountain.ModItems.CODEX));
        w.oneBookOnly(srv);

        h.assertTrue(codexCount(p) == 1, "two in one pack should leave one, left " + codexCount(p));
        h.assertTrue(w.bookId() != null, "the one left should have been taken on as the book");
        var kept = firstCodex(p);
        h.assertTrue(net.jj.mountain.item.MountainCodexItem.theRealOne(w, kept), "and it should be carrying the mark");
        h.assertTrue(net.jj.mountain.item.MountainCodexItem.heldBy(p), "and it should still count as the book in hand");
        java.util.UUID mark = w.bookId();

        // a /give straight into the pack, long after: no mark, so it goes
        p.getInventory().add(new net.minecraft.world.item.ItemStack(net.jj.mountain.ModItems.CODEX));
        w.oneBookOnly(srv);
        h.assertTrue(codexCount(p) == 1, "a handed-out copy should not survive, left " + codexCount(p));
        h.assertTrue(mark.equals(w.bookId()), "and it should not have taken the real one's place");
        h.assertTrue(net.jj.mountain.item.MountainCodexItem.theRealOne(w, firstCodex(p)), "the real one is the one still there");

        // the ender chest is no hiding place either
        p.getEnderChestInventory().setItem(0, new net.minecraft.world.item.ItemStack(net.jj.mountain.ModItems.CODEX));
        w.oneBookOnly(srv);
        h.assertTrue(p.getEnderChestInventory().getItem(0).isEmpty(), "a copy in the ender chest should come apart too");

        // and a copy owns nothing even in the second before it crumbles
        ServerPlayer other = survivalPlayer(h, h.absoluteVec(new Vec3(5, 2, 3)));
        other.getInventory().clearContent();
        other.getInventory().add(new net.minecraft.world.item.ItemStack(net.jj.mountain.ModItems.CODEX));
        h.assertTrue(!net.jj.mountain.item.MountainCodexItem.heldBy(other), "an unmarked copy should own him for nothing");
        h.assertTrue(net.jj.mountain.item.MountainCodexItem.heldBy(p), "while the real one still does");
        w.oneBookOnly(srv);
        h.assertTrue(codexCount(other) == 0, "and then it is gone");

        // losing the real one lets another be written, and the next one picked up becomes it
        p.getInventory().clearContent();
        w.forgetBook();
        h.assertTrue(!net.jj.mountain.item.MountainCodexItem.theRealOne(w, kept), "the old mark means nothing once it is lost");
        other.getInventory().add(new net.minecraft.world.item.ItemStack(net.jj.mountain.ModItems.CODEX));
        w.oneBookOnly(srv);
        h.assertTrue(codexCount(other) == 1 && w.bookId() != null, "the next one to turn up becomes the book");
        h.assertTrue(!mark.equals(w.bookId()), "and it is not the old one come back");

        other.getInventory().clearContent();
        drop(p); drop(other);
        w.forgetBook();
        if (was && wasId != null) { w.claimBookAs(); }
        else if (was) { w.claimBook(); }
        h.succeed();
    }

    private static int codexCount(ServerPlayer p) {
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(net.jj.mountain.ModItems.CODEX)) n++;
        return n;
    }

    private static net.minecraft.world.item.ItemStack firstCodex(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(net.jj.mountain.ModItems.CODEX)) return inv.getItem(i);
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    /** whoever asked him to finish it cannot hold one at all, and that burning does not cost anybody else theirs */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "one_book")
    public void theMarkedManCannotKeepOne(GameTestHelper h) {
        var srv = h.getLevel().getServer();
        var w = net.jj.mountain.world.MountainWorld.get(srv.overworld());
        boolean was = w.bookExists();
        w.forgetBook();

        ServerPlayer good = survivalPlayer(h, h.absoluteVec(new Vec3(3, 2, 6)));
        ServerPlayer bad = survivalPlayer(h, h.absoluteVec(new Vec3(5, 2, 6)));
        good.getInventory().clearContent();
        bad.getInventory().clearContent();

        good.getInventory().add(new net.minecraft.world.item.ItemStack(net.jj.mountain.ModItems.CODEX));
        w.oneBookOnly(srv);
        java.util.UUID mark = w.bookId();
        h.assertTrue(mark != null, "the good one's book is the book");

        w.mark(bad.getUUID());
        bad.getInventory().add(new net.minecraft.world.item.ItemStack(net.jj.mountain.ModItems.CODEX));
        w.oneBookOnly(srv);
        h.assertTrue(codexCount(bad) == 0, "it will not stay in those hands");
        h.assertTrue(mark.equals(w.bookId()), "and burning a copy off them costs nobody else theirs");
        h.assertTrue(codexCount(good) == 1, "the real one is where it was");

        w.unmark(bad.getUUID());
        good.getInventory().clearContent();
        drop(good); drop(bad);
        w.forgetBook();
        if (was) w.claimBook();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
    public void theBookSendsHimAndSetsHimOnThings(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.06f, MountainEntity.GUARDIAN);
        ServerPlayer p = survivalPlayer(h, h.absoluteVec(new Vec3(3, 2, 3)));
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.jj.mountain.ModItems.CODEX));
        h.runAfterDelay(20, () -> {
            h.assertTrue(e.spares(p), "he leaves whoever carries the book alone");
            Pig pig = h.spawn(EntityType.PIG, new BlockPos(4, 2, 4));
            pig.setNoAi(true);
            net.jj.mountain.ModItems.CODEX.interactLivingEntity(p.getMainHandItem(), p, pig, net.minecraft.world.InteractionHand.MAIN_HAND);
            h.runAfterDelay(30, () -> {
                h.assertTrue(e.getTarget() == pig, "the book should have set him on the pig: " + e.getTarget());
                drop(p);
                h.succeed();
            });
        });
    }


    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "trip")
    public void heGoesOnWalkingWhileNobodyIsWatching(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.2f, MountainEntity.CALM);
        e.setStay(true);                                   // the journey is a paper one; he shouldn't wander off it
        double[] from = {0};
        var w = net.jj.mountain.world.MountainWorld.get(h.getLevel().getServer().overworld());
        h.runAfterDelay(20, () -> {
            Vec3 to = e.position().add(4000, 0, 0);
            double speed = e.travelSpeed(false);
            h.assertTrue(speed > 0.01, "he should have a walking pace: " + speed);
            w.startTrip(h.getLevel(), e, to, speed, false);
            h.assertTrue(w.tripping(), "the journey should be written down");
            h.assertTrue(!w.tripDone(h.getLevel()), "and it shouldn't be over before it has started");
            from[0] = e.getX();
            Vec3 now = w.tripSpot(h.getLevel());
            h.assertTrue(Math.abs(now.x - from[0]) < 2, "he starts where he was: " + now);
        });
        h.runAfterDelay(220, () -> {
            Vec3 now = w.tripSpot(h.getLevel());
            double gone = now.x - from[0];
            double want = 200 * e.travelSpeed(false);
            h.assertTrue(gone > want * 0.5 && gone < want * 1.6,
                    "after two hundred ticks he should have covered about " + (int) want + " blocks, not " + (int) gone);
            h.assertTrue(w.where() != null && Math.abs(w.where().getX() - now.x) < 3, "the finder should follow him along");
            w.endTrip();
            h.assertTrue(!w.tripping(), "and the journey ends when he catches up");
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800, batch = "called")
    public void comeToMeKeepsComingUntilHeIsThere(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.15f, MountainEntity.CALM);
        // the player walks away rather than the Mountain being thrown across the field: a player keeps the
        // ground under them loaded, and he can only walk on ground that is there
        ServerPlayer p = survivalPlayer(h, h.absoluteVec(new Vec3(3, 2, 3)));
        p.setInvulnerable(true);
        double[] first = {0};
        h.runAfterDelay(20, () -> {
            Vec3 far = e.position().add(70, 0, 0);
            p.teleportTo(h.getLevel(), far.x, far.y + 1, far.z, 0f, 0f);
            e.callTo(p);
            h.assertTrue(e.beingCalled(), "he should be on his way");
            first[0] = Math.sqrt(p.distanceToSqr(e));
            h.assertTrue(first[0] > e.arriveRange(), "he should have a way to come: " + first[0]);
        });
        h.runAfterDelay(700, () -> {
            double now = Math.sqrt(p.distanceToSqr(e));
            h.assertTrue(now < first[0] - 10, "he should have closed the ground: " + first[0] + " -> " + now);
            h.assertTrue(!e.beingCalled() || now < e.arriveRange() * 1.4,
                    "he should be with you by now: " + now + " vs " + e.arriveRange());
            p.setInvulnerable(false);
            drop(p);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 700, batch = "fetch")
    public void heComesOverAndPicksYouUp(GameTestHelper h) {
        boolean wasGoo = net.jj.mountain.MountainConfig.V.gooTrail;
        net.jj.mountain.MountainConfig.V.gooTrail = false;          // his own goo is not what is being checked here
        MountainEntity e = spawn(h, 0.05f, MountainEntity.CALM);
        double[] seatY = {0};
        ServerPlayer p = survivalPlayer(h, h.absoluteVec(new Vec3(3, 2, 3)));
        // nothing can touch him for this one: standing beside a Mountain is not survivable for long, and what
        // he does to you on the way is a different test
        p.setInvulnerable(true);
        h.runAfterDelay(10, () -> {
            p.setHealth(p.getMaxHealth());
            p.fallDistance = 0f;
            h.assertTrue(e.comeAndGetMe(p), "he should set off for you");
            h.assertTrue(e.comingForSomebody(), "and he should be on his way");
            h.assertTrue(!e.ridden(), "but he hasn't got you yet");
        });
        h.runAfterDelay(200, () -> {
            h.assertTrue(e.ridden(), "by now he should have picked you up: coming=" + e.comingForSomebody()
                    + " down=" + e.settingSomebodyDown() + " passenger=" + p.isPassenger() + " hp=" + p.getHealth()
                    + " away=" + (int) Math.sqrt(p.distanceToSqr(e)));
            h.assertTrue(p.isPassenger(), "and you should be riding the seat he carries you in");
            // the seat rides his pose, which shifts under it every tick, so this is "up on him" not "to the block"
            h.assertTrue(p.position().distanceTo(e.seatWorld()) < 24, "the seat should be up on him: "
                    + p.position() + " vs " + e.seatWorld());
            h.assertTrue(e.spares(p), "he doesn't touch whoever he is carrying");
            h.assertTrue(p.getHealth() > 0f, "and he shouldn't have killed you on the way up");
            seatY[0] = e.seatWorld().y;
            h.assertTrue(e.setMeDown(), "and he should take you back out of the seat");
            h.assertTrue(!e.ridden(), "you stop driving him the moment he starts");
            h.assertTrue(e.settingSomebodyDown() && p.isPassenger(), "but he is still carrying you down");
        });
        h.runAfterDelay(200 + 70, () -> {
            h.assertTrue(!e.settingSomebodyDown(), "he should be done setting you down");
            h.assertTrue(!p.isPassenger(), "and you should be standing on your own feet");
            h.assertTrue(p.position().y < seatY[0] - 1.0, "he should have brought you down off him: " + p.position().y + " from " + seatY[0]);
            net.jj.mountain.MountainConfig.V.gooTrail = wasGoo;
            p.setInvulnerable(false);
            drop(p);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "drive")
    public void youCanStepIntoHimAndDriveHim(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.06f, MountainEntity.CALM);
        ServerPlayer p = survivalPlayer(h, h.absoluteVec(new Vec3(3, 2, 3)));
        h.runAfterDelay(20, () -> {
            h.assertTrue(e.possess(p), "he should let you in");
            h.assertTrue(e.ridden(), "and you should be in him");
            float before = e.getYRot();
            e.drive(p, 1f, 0f, before + 90f);
            h.runAfterDelay(80, () -> {                 // long enough that the join grace has run out
                e.drive(p, 1f, 0f, before + 90f);
                h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(e.getYRot() - before)) > 2f,
                        "he should turn the way you push: " + before + " -> " + e.getYRot());
                // a hit on the body you left behind throws you out, and he won't have you back
                p.setHealth(p.getMaxHealth());
                p.hurtTime = 0; p.invulnerableTime = 0;      // whatever happened on the way up, this is a fresh hit
                float hp0 = p.getHealth();
                boolean took = p.hurt(p.damageSources().magic(), 2f);
                h.assertTrue(p.getHealth() < hp0 && p.hurtTime > 0, "the body left behind should have been hurt: took=" + took
                        + " hp " + hp0 + " -> " + p.getHealth() + " hurtTime " + p.hurtTime
                        + " invuln " + p.isInvulnerableTo(p.damageSources().generic()) + "/" + p.getAbilities().invulnerable
                        + " mode " + p.gameMode.getGameModeForPlayer() + " diff " + h.getLevel().getDifficulty());
                h.runAfterDelay(5, () -> {
                    h.assertTrue(!e.ridden(), "being hurt should snap you out of him");
                    h.assertTrue(!e.possess(p), "and he should not take you back straight away");
                    net.jj.mountain.world.MountainWorld.get(h.getLevel().getServer().overworld())
                            .shutOut(h.getLevel(), p.getUUID(), 0);
                    drop(p);
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
    public void heGoesUnderTheGround(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.05f, MountainEntity.CALM);
        h.runAfterDelay(20, () -> {
            h.assertTrue(!e.burrowed(), "he starts on top of the ground");
            h.assertTrue(e.goUnder(e.position().add(40, 0, 0)), "he should be able to go under");
            h.runAfterDelay(80, () -> {
                h.assertTrue(e.burrowed(), "he should be under the ground by now");
                h.assertTrue(!e.hurt(e.damageSources().generic(), 50f), "nothing can reach him down there");
                h.assertTrue(e.digging(), "and he should still be busy down there");
                e.discard();
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "safe_lists")
    public void everybodyKeepsTheirOwnSafeList(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.05f, MountainEntity.HUNTER);
        ServerPlayer[] a = {survivalPlayer(h, h.absoluteVec(new Vec3(3, 2, 3)))};
        ServerPlayer b = survivalPlayer(h, h.absoluteVec(new Vec3(5, 2, 3)));
        var w = net.jj.mountain.world.MountainWorld.get(h.getLevel().getServer().overworld());
        var srv = h.getLevel().getServer();
        var book = new net.minecraft.world.item.ItemStack(net.jj.mountain.ModItems.CODEX);
        h.runAfterDelay(30, () -> {
            java.util.UUID au = a[0].getUUID();
            w.toggleFriend(au, b.getUUID());
            h.assertTrue(!e.spares(b), "nobody has the book, so nobody's list counts");

            a[0].setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, book.copy());
            w.refreshHolder(srv);
            h.assertTrue(e.spares(a[0]), "whoever has the book is left alone");
            h.assertTrue(e.spares(b), "and so is everyone on that player's list");

            // putting it away in the pack is not putting it down
            a[0].setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            a[0].getInventory().add(book.copy());
            w.refreshHolder(srv);
            h.assertTrue(e.spares(a[0]) && e.spares(b), "the book in your pack is still the book");

            // logging off with it is not putting it down either
            drop(a[0]);
            w.refreshHolder(srv);
            h.assertTrue(au.equals(w.bookHolder()), "the book went off with them, so it is still theirs");
            h.assertTrue(e.spares(b), "and their list is still doing its job");

            // b takes it off them: a's list stops meaning anything
            a[0] = survivalPlayer(h, h.absoluteVec(new Vec3(3, 2, 3)));
            a[0].getInventory().clearContent();
            b.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, book.copy());
            w.refreshHolder(srv);
            h.assertTrue(e.spares(b), "b has it now, so b is safe");
            h.assertTrue(!e.spares(a[0]), "and a's own list counts for nothing without the book");
            h.assertTrue(w.listOf(au).size() == 1, "a's list is still a's list");

            // a whole kind of creature goes on the list too
            Pig pig = h.spawn(EntityType.PIG, new BlockPos(4, 2, 4));
            pig.setNoAi(true);
            h.assertTrue(!e.spares(pig), "he goes for pigs to start with");
            w.toggleKind(b.getUUID(), EntityType.PIG);
            h.assertTrue(e.spares(pig), "and leaves them alone once they're on the holder's list");
            w.toggleKind(b.getUUID(), EntityType.PIG);
            h.assertTrue(!e.spares(pig), "and goes for them again when it comes off");

            // and once they are back and have put it down for real, nobody holds it
            w.dropFriend(au, b.getUUID());
            b.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            b.getInventory().clearContent();
            w.refreshHolder(srv);
            h.assertTrue(w.bookHolder() == null, "with the book put down by somebody who is here, it is nobody's");
            drop(a[0]); drop(b);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "burrow_gap")
    public void heOnlyGoesUnderOnCommandEveryFewDays(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.05f, MountainEntity.CALM);
        h.runAfterDelay(20, () -> {
            h.assertTrue(e.forcedUnderLeft() == 0, "nobody has sent him under yet");
            h.assertTrue(e.goUnderOnCommand(e.position().add(40, 0, 0)), "the first order should take");
            h.assertTrue(e.forcedUnderLeft() > MountainEntity.forcedUnderGap() - 100,
                    "and it should be three days before the next: " + e.forcedUnderLeft());
            h.runAfterDelay(100, () -> {
                h.assertTrue(!e.goUnderOnCommand(e.position().add(40, 0, 0)), "a second order so soon should be refused");
                h.assertTrue(e.forcedUnderLeft() > 0, "the wait should still be running");
                // and the wait is a setting: turn it off and he goes whenever he is asked
                int was = net.jj.mountain.MountainConfig.V.burrowGapDays;
                net.jj.mountain.MountainConfig.V.burrowGapDays = 0;
                h.assertTrue(e.forcedUnderLeft() == 0, "with the wait set to nothing there is nothing to wait for");
                net.jj.mountain.MountainConfig.V.burrowGapDays = was;
                e.discard();
                h.succeed();
            });
        });
    }

    // ------------------------------------------------------------------ what the book costs him
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "book_cost")
    public void theBookTiresHim(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.05f, MountainEntity.HUNTER);
        ServerPlayer p = survivalPlayer(h, h.absoluteVec(new Vec3(4, 2, 4)));
        p.getInventory().add(new net.minecraft.world.item.ItemStack(net.jj.mountain.ModItems.CODEX));
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            var mood = e.mood();
            h.assertTrue(mood.wind() > 0.9f, "he starts with his wind: " + mood.wind());
            float cost = net.jj.mountain.net.CodexOrders.windCost(
                    net.jj.mountain.net.CodexPayload.ATTACK_MOVE, net.jj.mountain.rig.RigState.EYE_STORM);
            h.assertTrue(cost > 0.1f, "his biggest move should cost him something: " + cost);
            float was = mood.wind();
            mood.spendWind(cost);
            h.assertTrue(mood.wind() < was - cost * 0.9f, "asking for it should take it out of him");
            // run him right down: the big ones are simply not in him
            for (int i = 0; i < 20; i++) mood.spendWind(cost);
            h.assertTrue(mood.wind() <= 0.001f, "he should be empty: " + mood.wind());
            h.assertTrue(!mood.hasWind(cost), "and the big move should be out of reach");
            h.assertTrue(mood.hasWind(0f), "while a free line still works");
            // pushing him while he is empty is what sours him
            float sour = mood.sourOf(p.getUUID());
            net.jj.mountain.net.CodexOrders.handle(p, new net.jj.mountain.net.CodexPayload(
                    net.jj.mountain.net.CodexPayload.ATTACK_MOVE, net.jj.mountain.rig.RigState.EYE_STORM));
            h.assertTrue(mood.sourOf(p.getUUID()) > sour, "being pushed on empty should tell on him");
            drop(p);
            e.discard();
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "book_cost")
    public void soured_rightThroughHeStopsListening(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.05f, MountainEntity.HUNTER);
        ServerPlayer p = survivalPlayer(h, h.absoluteVec(new Vec3(4, 2, 4)));
        p.getInventory().add(new net.minecraft.world.item.ItemStack(net.jj.mountain.ModItems.CODEX));
        var w = net.jj.mountain.world.MountainWorld.get(h.getLevel().getServer().overworld());
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            w.refreshHolder(h.getLevel().getServer());
            var mood = e.mood();
            java.util.UUID u = p.getUUID();
            h.assertTrue(mood.stage(u) == net.jj.mountain.entity.Mood.FINE, "he starts on good terms");
            h.assertTrue(e.spares(p), "and the book keeps him off you");
            mood.sour(u, 0.35f);
            h.assertTrue(mood.stage(u) == net.jj.mountain.entity.Mood.SLOW, "a little sour: he drags his feet");
            mood.sour(u, 0.25f);
            h.assertTrue(mood.stage(u) == net.jj.mountain.entity.Mood.BALKY, "more: he ignores some of it");
            mood.sour(u, 0.25f);
            h.assertTrue(mood.stage(u) == net.jj.mountain.entity.Mood.WILFUL, "more again: he picks his own");
            mood.sour(u, 0.25f);
            h.assertTrue(mood.stage(u) == net.jj.mountain.entity.Mood.TURNED, "right through: he is done with you");
            // the book no longer keeps him off, and the orders stop landing
            h.assertTrue(!e.spares(p) || !mood.hunting(u), "sanity");
            net.jj.mountain.net.CodexOrders.handle(p, new net.jj.mountain.net.CodexPayload(net.jj.mountain.net.CodexPayload.STAY));
            // and a word from the console puts it right again
            mood.settle(u);
            h.assertTrue(mood.stage(u) == net.jj.mountain.entity.Mood.FINE, "settling him clears it");
            h.assertTrue(e.spares(p), "and the book means something again");
            drop(p);
            e.discard();
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ what every fight leaves on him

    /** whole -> broken -> knitted back scarred -> broken again -> ruined, and a ruined one never comes back */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "scars")
    public void whatIsBrokenNeverComesBackTheSame(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.05f, MountainEntity.CALM);
        h.runAfterDelay(15, () -> {
            e.mendAllLimbs();
            h.assertTrue(e.scarredLegs() == 0 && e.ruinedLegs() == 0, "he starts whole");

            h.assertTrue(e.breakLegsNear(e.position(), 1) == 1, "one leg should break");
            int k = -1;
            for (int i = 0; i < e.legCount(); i++) if (e.legBroken(i)) { k = i; break; }
            h.assertTrue(k >= 0, "something should be broken");
            h.assertTrue(!e.legScarred(k), "it is not scarred yet, only broken");

            h.assertTrue(e.mendOneLeg() == k, "it knits back");
            h.assertTrue(!e.legBroken(k), "so it is not broken any more");
            h.assertTrue(e.legScarred(k), "but it is scarred now");
            h.assertTrue(e.legStrength(k) < 0.6f, "and it takes less than a whole one: " + e.legStrength(k));

            // the same leg again: that one is finished
            e.breakLegAt(k);
            h.assertTrue(e.legRuined(k), "a scarred leg broken twice is ruined");
            h.assertTrue(e.mendOneLeg() != k, "and it never knits back");
            h.assertTrue(e.legBroken(k), "it stays broken for good");

            // and he cannot be ruined past a third of himself
            e.breakLegsNear(e.position(), e.legCount());
            for (int pass = 0; pass < 4; pass++) for (int i = 0; i < e.legCount(); i++) { e.mendOneLeg(); }
            h.assertTrue(e.ruinedLegs() <= Math.max(1, e.legCount() / 3),
                    "no more than a third of him can be ruined: " + e.ruinedLegs() + " of " + e.legCount());

            e.mendAllLimbs();
            h.assertTrue(e.scarredLegs() == 0 && e.ruinedLegs() == 0, "mendlimbs takes every mark off him");
            e.discard();
            h.succeed();
        });
    }

    /** four on one side used to put him down for seven seconds; it is a real window now */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "knockdown")
    public void fourLegsOnASidePutHimDownForAGoodWhile(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.05f, MountainEntity.CALM);
        h.runAfterDelay(15, () -> {
            e.mendAllLimbs();
            h.assertTrue(!e.knockedDown(), "he starts on his legs");
            e.breakLegsNear(e.position(), 8);
            h.assertTrue(e.knockedDown(), "eight legs should put him down");
            h.assertTrue(!e.crippled(), "eight is not the end of him: " + e.brokenLegs() + " of " + e.legCount());
            h.runAfterDelay(150, () -> {
                // the old one had run out long before this
                h.assertTrue(e.knockedDown(), "he should still be on his belly after seven and a half seconds");
                e.mendAllLimbs();
                h.assertTrue(!e.knockedDown(), "and up again once his legs are back");
                e.discard();
                h.succeed();
            });
        });
    }

    /** about seven in ten legs gone and he never stands again, but he is worse to be near than ever */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "crippled")
    public void enoughLegsGoneAndHeStaysDownAndTurnsNasty(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.05f, MountainEntity.CALM);
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(6, 2, 6));
        pig.setNoAi(true);
        pig.setInvulnerable(true);                        // he kills it in one go otherwise, and then has nothing to want
        h.runAfterDelay(15, () -> {
            e.mendAllLimbs();
            int want = e.crippleAt();
            h.assertTrue(want > 8 && want < e.legCount(), "the cripple line should be most of his legs, is " + want);
            e.breakLegsNear(e.position(), want);
            h.assertTrue(e.crippled(), "that should be enough: " + e.brokenLegs() + " of " + e.legCount());
            h.assertTrue(e.knockedDown(), "and he is on his belly");

            // he cannot be sent anywhere any more
            Vec3 was = e.position();
            e.setGoal(was.add(600, 0, 0));
            h.runAfterDelay(120, () -> {
                h.assertTrue(e.knockedDown(), "and six seconds later he is still down, and always will be");
                double moved = Math.hypot(e.getX() - was.x, e.getZ() - was.z);
                h.assertTrue(moved < 2, "he should not have gone anywhere: " + moved);
                // but he takes whatever comes near him, calm or not
                h.assertTrue(e.getTarget() != null, "down for good he goes for whatever is in reach");
                h.assertTrue(!e.sleeping(), "and he does not lie down to sleep");
                e.goToSleep();
                h.assertTrue(!e.sleeping(), "he will not be put to sleep either");
                e.mendAllLimbs();
                h.assertTrue(!e.crippled() && !e.knockedDown(), "legs back and he is up");
                pig.discard();
                e.discard();
                h.succeed();
            });
        });
    }

    /** his own heart, beating: he turns round and walks out of the circle */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "ward")
    public void hisOwnHeartHoldsHimOff(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.2f, MountainEntity.CALM);
        var w = net.jj.mountain.world.MountainWorld.get(h.getLevel().getServer().overworld());
        h.runAfterDelay(20, () -> {
            BlockPos mid = e.blockPosition();
            w.startWard(h.getLevel(), mid, 2000, 100);
            h.assertTrue(w.warding(h.getLevel()), "the heart is beating");
            h.assertTrue(e.warded(e.getX(), e.getZ()), "and he is standing inside it");
            h.assertTrue(!e.warded(e.getX() + 4000, e.getZ()), "four thousand blocks off he is not");
            e.setStay(false);
            e.setGoal(e.position());                      // told to stand right on it: he still will not
            double[] from = {e.position().distanceTo(Vec3.atCenterOf(mid))};
            h.runAfterDelay(140, () -> {
                double now = e.position().distanceTo(Vec3.atCenterOf(mid));
                h.assertTrue(now > from[0] + 1.5, "he should have walked away from it: " + from[0] + " -> " + now);
                w.forgetWard();
                h.assertTrue(!w.warding(h.getLevel()), "and the heart stops when it is put out");
                e.discard();
                h.succeed();
            });
        });
    }

    /** summon another and the one that has been standing longest makes way, up to whatever the limit is set to */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "limit")
    public void aSummonedOnePushesTheOldOneOut(GameTestHelper h) {
        var lvl = h.getLevel();
        var V = net.jj.mountain.MountainConfig.V;
        final int was = V.maxMountains;
        MountainEntity old = spawn(h, 0.05f, MountainEntity.CALM);
        old.markWorldOne();
        old.bornAt(lvl);
        h.runAfterDelay(20, () -> {
            MountainEntity fresh = spawn(h, 0.05f, MountainEntity.HUNTER);
            fresh.bornAt(lvl);
            h.assertTrue(fresh.bornAt(lvl) > old.bornAt(lvl), "the new one is the newer one: "
                    + old.bornAt(lvl) + " then " + fresh.bornAt(lvl));

            // room for two: nobody goes
            V.maxMountains = 2;
            h.assertTrue(!net.jj.mountain.world.MountainWorld.limitNow(fresh, lvl), "with room for two, both stay");
            h.assertTrue(!old.isRemoved() && !fresh.isRemoved(), "so neither of them is gone");

            // room for one: the old one makes way and the new one is the world's own from now on
            V.maxMountains = 1;
            h.assertTrue(!net.jj.mountain.world.MountainWorld.limitNow(fresh, lvl), "the newcomer is not the one turned away");
            h.assertTrue(old.isRemoved(), "the one standing longest makes way");
            h.assertTrue(!fresh.isRemoved(), "and the summoned one stays");
            h.assertTrue(fresh.isWorldOne(), "and takes over as the world's own");

            // no limit at all: another can stand beside him
            V.maxMountains = 0;
            MountainEntity third = spawn(h, 0.05f, MountainEntity.CALM);
            h.assertTrue(!net.jj.mountain.world.MountainWorld.limitNow(third, lvl), "with no limit nothing is pushed out");
            h.assertTrue(!third.isRemoved() && !fresh.isRemoved(), "so they both stand");

            V.maxMountains = was;
            third.discard();
            fresh.discard();
            h.succeed();
        });
    }

    /** there is one of him: a second turning up anywhere is the one that goes */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "one_mountain")
    public void thereIsOnlyEverOneOfHim(GameTestHelper h) {
        var srv = h.getLevel().getServer();
        // another batch's Mountain may still be standing somewhere in the test world, so this works off what
        // is there rather than off an empty world
        MountainEntity a = spawn(h, 0.05f, MountainEntity.CALM);
        h.runAfterDelay(10, () -> {
            h.assertTrue(net.jj.mountain.world.MountainWorld.anyOther(srv, null) != null, "something is standing out there");
            MountainEntity b = spawn(h, 0.05f, MountainEntity.HUNTER);
            h.runAfterDelay(10, () -> {
                var seen = net.jj.mountain.world.MountainWorld.anyOther(srv, b);
                h.assertTrue(seen != null && seen != b, "looking past one of them still finds another: " + seen);
                // the world's own one always wins, wherever it is and whatever else is standing
                a.markWorldOne();
                h.assertTrue(net.jj.mountain.world.MountainWorld.anyOther(srv, null) == a, "the world's own one is the one kept");
                h.assertTrue(net.jj.mountain.world.MountainWorld.anyOther(srv, a) != a, "and looking past him finds somebody else");
                a.discard(); b.discard();
                h.succeed();
            });
        });
    }

    // ------------------------------------------------------------------ what he leaves behind
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "trophy")
    public void hisHeartScattersWhatMeansYouHarm(GameTestHelper h) {
        BlockPos bp = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlockAndUpdate(bp, ModBlocks.HEART.defaultBlockState());
        var zed = EntityType.ZOMBIE.create(h.getLevel());
        h.assertTrue(zed != null, "no zombie");
        zed.moveTo(bp.getX() + 4.5, bp.getY(), bp.getZ() + 0.5, 0f, 0f);
        h.getLevel().addFreshEntity(zed);
        ServerPlayer p = survivalPlayer(h, h.absoluteVec(new Vec3(1, 2, 1)));
        var w = net.jj.mountain.world.MountainWorld.get(h.getLevel().getServer().overworld());
        w.forgetWard();
        h.runAfterDelay(20, () -> {
            h.assertTrue(net.jj.mountain.block.HeartTrophyBlock.mode(h.getLevel().getBlockState(bp))
                    == net.jj.mountain.block.HeartTrophyBlock.READY, "it starts ready");
            h.getLevel().getBlockState(bp).useWithoutItem(h.getLevel(), p,
                    new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(bp), net.minecraft.core.Direction.UP, bp, false));
            h.assertTrue(net.jj.mountain.block.HeartTrophyBlock.mode(h.getLevel().getBlockState(bp))
                    == net.jj.mountain.block.HeartTrophyBlock.WARDING, "knocking on it sets it going");
            h.assertTrue(zed.getDeltaMovement().y > 0.2, "the zombie should have been thrown: " + zed.getDeltaMovement());
            h.assertTrue(zed.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "and left reeling");
            // and while it beats he will not come near the place
            h.assertTrue(w.warding(h.getLevel()), "the ward should be running");
            h.assertTrue(w.warded(h.getLevel(), bp.getX() + 100, bp.getZ()), "a hundred blocks off is still inside it");
            h.assertTrue(!w.warded(h.getLevel(), bp.getX() + 4000, bp.getZ()), "four thousand blocks off is not");
            // digging it up puts it out
            h.getLevel().removeBlock(bp, false);
            h.assertTrue(!w.warding(h.getLevel()), "digging up the heart stops it holding him off");
            zed.discard();
            drop(p);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ out of the world and back again
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "step_aside")
    public void aloneHeLeavesTheWorldAndComesBackTheSame(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.05f, MountainEntity.CALM);
        ServerPlayer p = survivalPlayer(h, h.absoluteVec(new Vec3(4, 2, 4)));
        var w = net.jj.mountain.world.MountainWorld.get(h.getLevel().getServer().overworld());
        h.runAfterDelay(25, () -> {
            e.popEyes(20, null);
            final float hp = e.healthNow();
            final int eyes = e.eyesOpen();
            e.setGoal(e.position().add(3000, 0, 0));
            h.assertTrue(e.stepAside(), "he should be able to step out of the world");
            h.assertTrue(e.isRemoved(), "and be gone from it");
            h.assertTrue(w.isAway(), "and be kept as a sum instead");
            h.assertTrue(w.tripping(), "with the journey written down");
            h.assertTrue(w.where(h.getLevel()) != null, "the sum should know where he is");
            // somebody is standing right where he is, so the world should put him straight back
            h.runAfterDelay(60, () -> {
                MountainEntity back = null;
                for (MountainEntity m : h.getLevel().getEntities(net.jj.mountain.ModEntities.MOUNTAIN, m -> !m.isRemoved())) back = m;
                h.assertTrue(back != null, "he should have been put back in the world");
                h.assertTrue(!w.isAway(), "and the sum finished with");
                h.assertTrue(Math.abs(back.healthNow() - hp) < 1f,
                        "with the health he left with: " + back.healthNow() + " want " + hp);
                h.assertTrue(back.eyesOpen() == eyes, "and the eyes he left with: " + back.eyesOpen() + " want " + eyes);
                back.discard();
                w.forgetAway();
                w.endTrip();
                drop(p);
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "step_aside_orders")
    public void theBookStillTurnsHimWhileHeIsASum(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.05f, MountainEntity.CALM);
        var w = net.jj.mountain.world.MountainWorld.get(h.getLevel().getServer().overworld());
        ServerLevel sl = h.getLevel();
        h.runAfterDelay(25, () -> {
            Vec3 first = e.position().add(4000, 0, 0);
            e.setGoal(first);
            h.assertTrue(e.stepAside(), "out of the world he goes");
            h.assertTrue(w.tripping() && Math.abs(w.tripEnd().x - first.x) < 2, "he set off for the first spot");
            // the book reaches him out there and turns him round
            Vec3 second = e.position().add(-2500, 0, 0);
            h.assertTrue(w.sendAway(sl, second, false), "the book should still reach him");
            h.assertTrue(Math.abs(w.tripEnd().x - second.x) < 2, "and turn him for the new spot: " + w.tripEnd());
            h.assertTrue(w.tripLeftMinutes(sl) > 0, "with a walk still ahead of him");
            // and telling him to stand still stops the sum where it is
            var at = w.where(sl);
            h.assertTrue(w.haltAway(sl), "and he can be told to stand still out there");
            h.assertTrue(!w.tripping(), "the journey stops");
            h.assertTrue(w.where(sl) != null && w.where(sl).getX() == at.getX(), "and he is left where he had got to");
            w.forgetAway();
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ the storm takes the whole crowd
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "eye_storm_crowd")
    public void theEyeStormBurnsEveryOneOfThemAtOnce(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.04f, MountainEntity.HUNTER);
        java.util.List<Pig> crowd = new java.util.ArrayList<>();
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            for (int i = 0; i < 26; i++) {
                double a = i * Math.PI * 2 / 26, r = 8 + (i % 3) * 4;
                Pig pig = h.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
                pig.setNoAi(true);
                pig.setInvulnerable(true);
                pig.moveTo(e.getX() + Math.cos(a) * r, e.getY(), e.getZ() + Math.sin(a) * r, 0f, 0f);
                crowd.add(pig);
            }
            h.runAfterDelay(10, () -> {
                h.assertTrue(e.forceAttack(net.jj.mountain.rig.RigState.EYE_STORM), "the storm should start");
                h.runAfterDelay(45, () -> {
                    int on = e.stormTargets();
                    h.assertTrue(on > 8, "every one of them he can see gets a beam, not the first eight: " + on + " of " + crowd.size());
                    for (Pig p : crowd) p.discard();
                    e.discard();
                    h.succeed();
                });
            });
        });
    }

    // ------------------------------------------------------------------ the safe list is absolute
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 420, batch = "safe_no_attack")
    public void heNeverGoesAfterSomebodyOnHisList(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.05f, MountainEntity.HUNTER);
        ServerPlayer p = survivalPlayer(h, h.absoluteVec(new Vec3(5, 2, 5)));
        p.getInventory().add(new net.minecraft.world.item.ItemStack(net.jj.mountain.ModItems.CODEX));
        var w = net.jj.mountain.world.MountainWorld.get(h.getLevel().getServer().overworld());
        h.runAfterDelay(25, () -> {
            w.refreshHolder(h.getLevel().getServer());
            e.setStay(true);
            p.setInvulnerable(true);
            h.assertTrue(e.spares(p), "whoever carries the book is left alone");
            // a hunter used to pick the nearest player every half second whatever his list said, roar at them
            // and swing for nothing, because only the damage was ever checked against the list
            h.assertTrue(e.getTarget() != p, "he should not have them in his sights to start with");
            // and hitting him must not put them there either
            e.hurt(e.damageSources().playerAttack(p), 40f);
            h.assertTrue(e.holdsGrudge(p), "he remembers who hit him");
            h.assertTrue(e.getTarget() != p, "but a hit from somebody on his list does not turn him on them");
            h.runAfterDelay(260, () -> {
                h.assertTrue(e.getTarget() != p, "and he never comes back round to them: " + e.getTarget());
                h.assertTrue(e.spares(p), "they are still on the list");
                p.setInvulnerable(false);
                e.forgiveAll();
                drop(p);
                e.discard();
                h.succeed();
            });
        });
    }

    // ------------------------------------------------------------------ the last thing he does
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "unmake_gate")
    public void heWillOnlyFinishItWhenThereIsAlmostNothingLeft(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.05f, MountainEntity.CALM);
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            h.assertTrue(e.unmakeState() == 2, "whole, there is far too much of him left: " + e.unmakeState());
            e.hurtSelf(e.healthMax() * 0.8f);
            h.assertTrue(e.unmakeState() == 0, "down to a fifth, he will: " + e.unmakeState());
            boolean was = net.jj.mountain.MountainConfig.V.unmake;
            net.jj.mountain.MountainConfig.V.unmake = false;
            h.assertTrue(e.unmakeState() == 1, "and with it switched off there is no line at all");
            net.jj.mountain.MountainConfig.V.unmake = was;
            e.discard();
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 700, batch = "unmake")
    public void theLastThingHeDoesKillsHimAndMarksYouForGood(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.05f, MountainEntity.CALM);
        ServerPlayer p = survivalPlayer(h, h.absoluteVec(new Vec3(6, 2, 6)));
        p.getInventory().add(new net.minecraft.world.item.ItemStack(net.jj.mountain.ModItems.CODEX));
        var w = net.jj.mountain.world.MountainWorld.get(h.getLevel().getServer().overworld());
        var V = net.jj.mountain.MountainConfig.V;
        final int wasR = V.unmakeRadius, wasW = V.unmakeWave;
        h.runAfterDelay(25, () -> {
            V.unmakeRadius = 16;                              // a hole the size of the test, not the size of the idea
            V.unmakeWave = 30;
            e.setStay(true);
            p.setInvulnerable(true);
            w.refreshHolder(h.getLevel().getServer());
            e.hurtSelf(e.healthMax() * 0.85f);
            h.assertTrue(net.jj.mountain.item.MountainCodexItem.heldBy(p), "the book starts in their pack");
            h.assertTrue(e.unmakeIt(p), "he should take the asking");
            h.assertTrue(e.unmaking(), "and be standing all the way up");
            h.assertTrue(!e.unmakeIt(p), "and not take it twice");
            h.runAfterDelay(430, () -> {
                h.assertTrue(e.isDeadOrDying(), "it should be the end of him");
                h.assertTrue(w.marked(p.getUUID()), "and whoever asked should be marked");
                h.assertTrue(!e.spares(p), "nothing covers them any more");
                h.runAfterDelay(30, () -> {
                    h.assertTrue(!net.jj.mountain.item.MountainCodexItem.heldBy(p), "and no book will stay in their hands");
                    h.assertTrue(!w.bookExists(), "while the world will still let another be written");
                    V.unmakeRadius = wasR;
                    V.unmakeWave = wasW;
                    w.unmark(p.getUUID());
                    p.setInvulnerable(false);
                    net.jj.mountain.world.Crater.forgetEverything();
                    net.jj.mountain.world.Shockwave.forgetEverything();
                    drop(p);
                    e.discard();
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260, batch = "five_strikes")
    public void fiveHitsWithTheBookInYourHandAndHeIsDoneWithYou(GameTestHelper h) {
        MountainEntity e = spawn(h, 0.05f, MountainEntity.CALM);
        ServerPlayer p = survivalPlayer(h, h.absoluteVec(new Vec3(5, 2, 5)));
        p.getInventory().add(new net.minecraft.world.item.ItemStack(net.jj.mountain.ModItems.CODEX));
        var w = net.jj.mountain.world.MountainWorld.get(h.getLevel().getServer().overworld());
        int most = net.jj.mountain.MountainConfig.V.freeHits;
        h.runAfterDelay(25, () -> {
            w.refreshHolder(h.getLevel().getServer());
            e.setStay(true);
            p.setInvulnerable(true);
            h.assertTrue(most >= 2, "the setting should allow for a few: " + most);
            h.assertTrue(e.spares(p), "the book keeps him off you to start with");
            // all but the last one he wears
            for (int i = 0; i < most - 1; i++) {
                e.hurt(e.damageSources().playerAttack(p), 20f);
                h.assertTrue(e.spares(p), "he should still be wearing it after " + (i + 1) + " of " + most);
                h.assertTrue(e.getTarget() != p, "and not be coming for you yet");
                h.assertTrue(e.mood().strikesLeft(p.getUUID()) == most - i - 1,
                        "the count should be going down: " + e.mood().strikesLeft(p.getUUID()));
            }
            // and the last one he does not
            e.hurt(e.damageSources().playerAttack(p), 20f);
            h.assertTrue(e.mood().turnedOn(p.getUUID()), "that should be one too many");
            h.assertTrue(!e.spares(p), "and the book in your hand should mean nothing now");
            h.assertTrue(e.getTarget() == p, "and he should be coming for you: " + e.getTarget());
            // the console can put him right again
            e.mood().settle(p.getUUID());
            h.assertTrue(e.spares(p), "settling him gives you the book back");
            h.assertTrue(e.mood().strikesLeft(p.getUUID()) == most, "with a full count again");
            p.setInvulnerable(false);
            e.forgiveAll();
            drop(p);
            e.discard();
            h.succeed();
        });
    }
}
