package net.jj.hollowbell.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.entity.Belling;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.rig.BellModel;
import net.jj.hollowbell.rig.BellRig;
import net.jj.hollowbell.world.BellWorld;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.UUID;

/** Server-side checks: run with ./gradlew runGametest. Each test has a batch of its own, far from the others. */
public class HollowbellGameTests implements FabricGameTest {
    private static final float S = 0.1f;

    // ------------------------------------------------------------------ helpers

    private static HollowbellEntity spawnAway(GameTestHelper h, float scale, int variant, int slot) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        int x = o.getX() + 20000 + slot * 400, z = o.getZ() + 5000;
        for (int cx = (x >> 4) - 3; cx <= (x >> 4) + 3; cx++) for (int cz = (z >> 4) - 3; cz <= (z >> 4) + 3; cz++) {
            h.getLevel().setChunkForced(cx, cz, true);
            h.getLevel().getChunk(cx, cz);
        }
        int y = h.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
        HollowbellEntity e = ModEntities.HOLLOWBELL.create(h.getLevel());
        e.moveTo(x + 0.5, y, z + 0.5, 0f, 0f);
        e.setBellScale(scale);
        e.setVariant(variant);
        e.setMoveCooldown(100000);          // he only does what the test tells him
        h.getLevel().addFreshEntity(e);
        return e;
    }

    private static void release(GameTestHelper h, HollowbellEntity e) {
        int x = e.getBlockX(), z = e.getBlockZ();
        e.discard();
        for (int cx = (x >> 4) - 4; cx <= (x >> 4) + 4; cx++) for (int cz = (z >> 4) - 4; cz <= (z >> 4) + 4; cz++) h.getLevel().setChunkForced(cx, cz, false);
    }

    private static Pig pig(GameTestHelper h, Vec3 at) {
        Pig p = EntityType.PIG.create(h.getLevel());
        p.moveTo(at.x, at.y, at.z, 0f, 0f);
        p.setNoAi(true);
        p.setPersistenceRequired();
        h.getLevel().addFreshEntity(p);
        return p;
    }

    @SuppressWarnings("deprecation")
    private static ServerPlayer player(GameTestHelper h, Vec3 at) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "hb-test"), false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
        };
        Connection c = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(c);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(c, p, cookie);
        p.teleportTo(h.getLevel(), at.x, at.y, at.z, 0f, 0f);
        try {
            java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            f.setAccessible(true);
            f.setInt(p, 0);
        } catch (ReflectiveOperationException ignored) { }
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        return p;
    }

    private static void drop(ServerPlayer p) { p.getServer().getPlayerList().remove(p); }

    /** the ground right under his middle */
    private static Vec3 under(HollowbellEntity e) { return new Vec3(e.getX() + 0.3, e.groundAt(e.getX(), e.getZ()), e.getZ() + 0.3); }

    // ------------------------------------------------------------------ the model and the rig

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "rig")
    public void theModelAndRigLoad(GameTestHelper h) {
        BellRig rig = BellRig.get();
        BellModel m = BellModel.get();
        h.assertTrue(rig.boneCount() == m.boneCount(), "rig has " + rig.boneCount() + " bones, model has " + m.boneCount());
        for (int b = 0; b < rig.boneCount(); b++) h.assertTrue(rig.boneNames[b].equals(m.boneNames[b]), "bone " + b + " named differently");
        h.assertTrue(rig.arms.length == 8, "arms: " + rig.arms.length);
        h.assertTrue(rig.spots.length == 5, "spots: " + rig.spots.length);
        h.assertTrue(rig.threads.length == 128, "threads: " + rig.threads.length);
        h.assertTrue(rig.strands.length >= 50, "strands: " + rig.strands.length);
        h.assertTrue(rig.pods.length >= 10 && rig.pods.length <= 31, "pods: " + rig.pods.length);
        h.assertTrue(rig.eggs.length >= 10, "egg clumps: " + rig.eggs.length);
        // every block in him is a real Java block (nothing turned into stone)
        var lookup = BuiltInRegistries.BLOCK.asLookup();
        for (String p : m.palette) {
            try { BlockStateParser.parseForBlock(lookup, p, false); }
            catch (Exception ex) { h.fail("not a Java block: " + p); }
        }
        long voxels = 0; for (int b = 0; b < m.boneCount(); b++) voxels += m.count(b);
        h.assertTrue(voxels > 300000, "too few blocks drawn: " + voxels);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "summon")
    public void heSummonsAndFloats(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 1);
        h.runAfterDelay(40, () -> {
            h.assertTrue(e.isAlive(), "he isn't alive");
            float want = Math.max(40f, HollowbellConfig.V.health * S);
            h.assertTrue(Math.abs(e.healthMax() - want) < 1f, "health " + e.healthMax() + " want " + want);
            h.assertTrue(e.podsLeft() == e.rig.pods.length, "pods popped already");
            h.assertTrue(e.threadsHolding() == 128, "threads not all holding: " + e.threadsHolding());
            double ground = e.groundAt(e.getX(), e.getZ());
            h.assertTrue(Math.abs(e.getY() - ground) < 1.5, "he should hang with his strands on the ground: y " + e.getY() + " ground " + ground);
            // the pose puts his crown where it should be
            Vec3 c = e.crownWorld();
            h.assertTrue(Math.abs(c.y - (e.getY() + (e.rig.crownY + 1) * S)) < 3, "crown at " + c + " he is at " + e.position());
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "command")
    public void theSummonCommandWorks(GameTestHelper h) {
        var src = h.getLevel().getServer().createCommandSourceStack().withPosition(Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 2, 2))))
                .withLevel(h.getLevel()).withPermission(4).withSuppressedOutput();
        int before = h.getLevel().getEntities(ModEntities.HOLLOWBELL, x -> true).size();
        h.getLevel().getServer().getCommands().performPrefixedCommand(src, "hollowbell summon guardian 0.05");
        h.runAfterDelay(5, () -> {
            var all = h.getLevel().getEntities(ModEntities.HOLLOWBELL, x -> x.isGuardian() && Math.abs(x.bellScale() - 0.05f) < 1e-3);
            h.assertTrue(h.getLevel().getEntities(ModEntities.HOLLOWBELL, x -> true).size() == before + 1 && !all.isEmpty(), "the command made nothing");
            for (var x : all) x.discard();
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "ray")
    public void aSwingFindsTheRightBlock(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 2);
        h.runAfterDelay(10, () -> {
            e.setStay(true);
            e.ensurePose();
            // from above, straight down onto the crown
            Vec3 top = e.position().add(0, (e.rig.crownY + 20) * S, 0);
            var hit = e.raycast(top, new Vec3(0, -1, 0), 40);
            h.assertTrue(hit != null, "nothing hit looking down on him");
            h.assertTrue(e.rig.kind[hit.bone()] == BellRig.Kind.CROWN, "looking down on the crown hit " + e.rig.boneNames[hit.bone()]);
            // from the side at rim height: the rim or an arm
            Vec3 side = e.position().add(-(100 * S + 5), 134 * S, 0.2);
            var hit2 = e.raycast(side, new Vec3(1, 0, 0), 30);
            h.assertTrue(hit2 != null, "nothing hit from the side");
            // and a hit on a spot is worth far more than one on his copper
            h.assertTrue(e.worth(e.rig.spots[0].bone(), false) > e.worth(e.rig.bellBone, false) * 10, "spots aren't weak spots");
            h.assertTrue(e.worth(e.rig.crownBone, true) > e.worth(e.rig.crownBone, false), "hitting from inside isn't worth more");
            release(h, e);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ every move starts and ends

    private static void moveRuns(GameTestHelper h, int move, int slot) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, slot);
        Pig[] p = new Pig[1];
        int len = Moves.length(move);
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            p[0] = pig(h, move == Moves.SLAM || move == Moves.WRAP || move == Moves.SWEEP
                    ? e.armTipWorld(0).multiply(1, 0, 1).add(0, e.groundAt(e.armTipWorld(0).x, e.armTipWorld(0).z), 0) : under(e));
            p[0].setInvulnerable(true);
            e.setTarget(p[0]);
            h.assertTrue(e.forceMove(move, p[0]), "could not start " + Moves.NAMES[move]);
        });
        h.runAfterDelay(24, () -> h.assertTrue(e.moveNow() == move, Moves.NAMES[move] + " is not running (" + e.moveNow() + ")"));
        h.runAfterDelay(20 + len + 20, () -> {
            h.assertTrue(e.moveNow() != move, Moves.NAMES[move] + " never finished");
            h.assertTrue(e.isAlive(), "he died doing " + Moves.NAMES[move]);
            e.moves().letGoOfEverything(false);
            p[0].discard();
            for (Belling b : h.getLevel().getEntitiesOfClass(Belling.class, e.bodyBox().inflate(30))) b.discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "move_grab") public void moveGrab(GameTestHelper h) { moveRuns(h, Moves.GRAB, 10); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "move_harvest") public void moveHarvest(GameTestHelper h) { moveRuns(h, Moves.HARVEST, 11); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "move_curtain") public void moveCurtain(GameTestHelper h) { moveRuns(h, Moves.CURTAIN, 12); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "move_sweep") public void moveSweep(GameTestHelper h) { moveRuns(h, Moves.SWEEP, 13); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "move_slam") public void moveSlam(GameTestHelper h) { moveRuns(h, Moves.SLAM, 14); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "move_wrap") public void moveWrap(GameTestHelper h) { moveRuns(h, Moves.WRAP, 15); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "move_pulse") public void movePulse(GameTestHelper h) { moveRuns(h, Moves.PULSE, 16); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "move_drop") public void moveDrop(GameTestHelper h) { moveRuns(h, Moves.DROP, 17); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "move_shed") public void moveShed(GameTestHelper h) { moveRuns(h, Moves.SHED, 18); }

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
        h.runAfterDelay(20 + Moves.length(Moves.SLAM) + 5, () -> {
            h.assertTrue(!p[0].isAlive() || p[0].getHealth() < 10f, "the slam missed the pig under the arm");
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

    // ------------------------------------------------------------------ grab, let go, the inside of the dome

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "grab_letgo")
    public void hitTheStrandAndItLetsGo(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.HUNTER, 30);
        Pig[] p = new Pig[1];
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            p[0] = pig(h, under(e));
            p[0].setInvulnerable(true);
            h.assertTrue(e.forceMove(Moves.GRAB, p[0]), "no grab");
        });
        h.runAfterDelay(20 + Moves.REACH + 20, () -> {
            h.assertTrue(e.moves().grabbed() == p[0], "the strand didn't take hold of the pig");
            h.assertTrue(p[0].isPassenger(), "the pig isn't held");
            h.assertTrue(e.moves().lift() > 0f, "it isn't pulling it up");
            int strand = e.moveArg();
            int bone = e.rig.strands[strand].bones()[0];
            for (int i = 0; i < 4; i++) e.applyDamage(e.damageSources().generic(), 3f, bone, false);
            h.assertTrue(e.moves().grabbed() == null, "hit four times and the strand still holds on");
            h.assertTrue(e.moveNow() == Moves.NONE, "the grab didn't end");
        });
        h.runAfterDelay(20 + Moves.REACH + 30, () -> {
            h.assertTrue(!p[0].isPassenger(), "the pig is still riding something");
            p[0].discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "inside")
    public void pulledIntoTheDomeAndSpatOut(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.25f, HollowbellEntity.HUNTER, 31);
        ServerPlayer[] pl = new ServerPlayer[1];
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            pl[0] = player(h, under(e).add(0.5, 0, 0.5));
            pl[0].setInvulnerable(true);
            h.assertTrue(e.forceMove(Moves.GRAB, pl[0]), "no grab");
        });
        h.runAfterDelay(20 + Moves.length(Moves.GRAB) + 10, () -> {
            h.assertTrue(e.moves().isInside(pl[0]), "the player didn't end up inside the dome");
            // inside the dome: under the bell, above the rim
            double y = pl[0].getY() - e.getY();
            h.assertTrue(y > 100 * 0.25f && y < e.rig.crownY * 0.25f, "inside, but at height " + y);
            h.assertTrue(e.horiz(pl[0].position()) < 70 * 0.25f, "inside, but out at " + e.horiz(pl[0].position()));
            // a glowing spot is in reach from in there
            h.assertTrue(e.raycast(pl[0].getEyePosition(), new Vec3(0, 1, 0), 6) != null, "nothing of him in reach over your head");
            // hit him hard enough from inside and he lets you go
            for (int i = 0; i < 30 && e.moves().isInside(pl[0]); i++)
                e.applyDamage(e.damageSources().playerAttack(pl[0]), 20f, e.rig.spots[0].bone(), true);
            h.assertTrue(!e.moves().isInside(pl[0]), "hurting him from inside didn't get you out");
            drop(pl[0]);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "sting")
    public void theStrandsSting(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 32);
        Pig[] z = new Pig[1];
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            // right on one of the blocks of a strand's lowest piece
            var S = e.rig.strands[e.rig.strands.length / 2];
            int bone = S.bones()[S.bones().length - 1];
            BellModel m = BellModel.get();
            int k = m.count(bone) / 2;
            Vec3 tip = e.boneWorld(bone, new Vector3f(m.x[bone][k] + 0.5f, m.y[bone][k] + 0.5f, m.z[bone][k] + 0.5f));
            z[0] = EntityType.PIG.create(h.getLevel());
            z[0].moveTo(tip.x, tip.y - 0.9, tip.z, 0, 0);
            z[0].setNoGravity(true);
            z[0].setNoAi(true);
            z[0].setPersistenceRequired();
            h.getLevel().addFreshEntity(z[0]);
        });
        h.runAfterDelay(40, () -> {
            h.assertTrue(z[0].hasEffect(MobEffects.POISON) && z[0].hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "touching a strand didn't sting");
            z[0].discard();
            release(h, e);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ threads, pods, eggs

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 900, batch = "threads")
    public void threadsCutAndGrowBack(GameTestHelper h) {
        int was = HollowbellConfig.V.threadRegrowSeconds;
        HollowbellConfig.V.threadRegrowSeconds = 1;
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 40);
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            int bone = e.rig.threads[5].bones()[0];
            for (int i = 0; i < 20 && e.threadGrowth(5) > 0f; i++) e.applyDamage(e.damageSources().generic(), 6f, bone, false);
            h.assertTrue(e.threadGrowth(5) == 0f, "thread 5 wasn't cut");
            h.assertTrue(e.threadsHolding() == 127, "threads holding: " + e.threadsHolding());
        });
        h.runAfterDelay(60, () -> h.assertTrue(e.threadGrowth(5) > 0f && e.threadGrowth(5) < 1f, "thread 5 isn't growing back: " + e.threadGrowth(5)));
        h.runAfterDelay(20 + 20 + 420, () -> {
            HollowbellConfig.V.threadRegrowSeconds = was;
            h.assertTrue(e.threadGrowth(5) >= 1f, "thread 5 never grew back: " + e.threadGrowth(5));
            h.assertTrue(e.threadsHolding() == 128, "not all holding again");
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120, batch = "sunk")
    public void cutEnoughThreadsAndHeSinks(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 41);
        h.runAfterDelay(20, () -> { e.setStay(true); e.cutThreads(90); });
        h.runAfterDelay(35, () -> {
            h.assertTrue(e.threadsHolding() == 38, "holding: " + e.threadsHolding());
            h.assertTrue(e.sunk(), "90 threads cut and he hasn't sunk");
            h.assertTrue(!e.forceMove(Moves.DROP, null), "he can drop while he's already sunk");
            e.mendThreads();
        });
        h.runAfterDelay(50, () -> {
            h.assertTrue(!e.sunk(), "threads mended and he's still sunk");
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "pods")
    public void podsPopAndStayPopped(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 42);
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            float before = e.healthNow();
            int pods = e.podsLeft();
            int bone = e.rig.pods[2].bone();
            for (int i = 0; i < 40 && !e.isPodPopped(2); i++) e.applyDamage(e.damageSources().generic(), 5f, bone, false);
            h.assertTrue(e.isPodPopped(2), "pod 2 never popped");
            h.assertTrue(e.podsLeft() == pods - 1, "pods left " + e.podsLeft());
            h.assertTrue(e.healthNow() < before - e.healthMax() * 0.012f, "popping a pod should cost him");
            var items = h.getLevel().getEntitiesOfClass(ItemEntity.class, e.bodyBox(), it -> it.getItem().is(ModItems.POD));
            h.assertTrue(!items.isEmpty(), "a popped pod dropped nothing");
            for (var it : items) it.discard();
            // hitting it again does nothing more to the pod
            e.applyDamage(e.damageSources().generic(), 5f, bone, false);
            h.assertTrue(e.podsLeft() == pods - 1, "a popped pod came back");
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120, batch = "shed")
    public void sheddingHatchesBellings(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.HUNTER, 43);
        h.runAfterDelay(20, () -> { e.setStay(true); h.assertTrue(e.forceMove(Moves.SHED, null), "no shed"); });
        h.runAfterDelay(20 + Moves.SHED_AT + 3, () -> {
            int eggsGone = e.rig.eggs.length - e.eggsLeft();
            h.assertTrue(eggsGone == HollowbellConfig.V.shedCount, "egg clumps gone: " + eggsGone);
            var bs = h.getLevel().getEntitiesOfClass(Belling.class, e.bodyBox().inflate(10));
            h.assertTrue(bs.size() == eggsGone, "bellings: " + bs.size());
            for (Belling b : bs) b.discard();
            release(h, e);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ the book, riding, dying, saving

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "safe")
    public void theBookAndTheSafeList(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.HUNTER, 50);
        ServerPlayer[] pl = new ServerPlayer[2];
        h.runAfterDelay(20, () -> {
            pl[0] = player(h, e.position().add(3, 0, 3));
            pl[1] = player(h, e.position().add(-3, 0, 3));
            h.assertTrue(!e.spares(pl[0]) && !e.spares(pl[1]), "he spares players with no book about");
            pl[0].getInventory().add(new net.minecraft.world.item.ItemStack(ModItems.CODEX));
            h.assertTrue(e.spares(pl[0]), "he doesn't spare the one holding the book");
            h.assertTrue(!e.spares(pl[1]), "the other player is spared without being on the list");
            BellWorld.get(h.getLevel().getServer()).toggleFriend(pl[0].getUUID(), pl[1].getUUID());
            h.assertTrue(e.spares(pl[1]), "the safe list didn't protect the other player");
            pl[0].getInventory().clearContent();
            h.assertTrue(!e.spares(pl[1]), "the list still counts with the book put down");
            BellWorld.get(h.getLevel().getServer()).dropFriend(pl[0].getUUID(), pl[1].getUUID());
            drop(pl[0]); drop(pl[1]);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "ride")
    public void sittingOnHisCrown(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 51);
        ServerPlayer[] pl = new ServerPlayer[1];
        h.runAfterDelay(20, () -> {
            pl[0] = player(h, e.position().add(5, 0, 5));
            h.assertTrue(e.possess(pl[0]), "couldn't get on");
        });
        h.runAfterDelay(30, () -> {
            h.assertTrue(e.rider() == pl[0] && e.ridden(), "not riding him");
            h.assertTrue(pl[0].position().distanceTo(e.crownWorld()) < 3, "not on his crown: " + pl[0].position() + " crown " + e.crownWorld());
            e.drive(pl[0], 1f, 0f, 0f);
        });
        h.runAfterDelay(90, () -> {
            h.assertTrue(e.distanceToSqr(e.home()) > 0.5, "driving him forward didn't move him");
            e.dropRider();
            h.assertTrue(!pl[0].isPassenger() && e.rider() == null, "didn't get off");
            drop(pl[0]);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 520, batch = "death")
    public void heDiesSinksAndDropsLoot(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 60);
        Vec3[] at = new Vec3[1];
        h.runAfterDelay(20, () -> {
            at[0] = e.position();
            e.applyDamage(e.damageSources().generic(), e.healthMax() * 100f, -1, false);
            h.assertTrue(e.isDeadOrDying(), "he should be dying");
        });
        h.runAfterDelay(120, () -> {
            h.assertTrue(!e.isRemoved(), "he should still be folding down");
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at[0], at[0]).inflate(40)).isEmpty(), "loot before he has sunk");
        });
        h.runAfterDelay(20 + HollowbellEntity.DEATH_LENGTH + 20, () -> {
            h.assertTrue(e.isRemoved(), "he should be gone after sinking");
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at[0], at[0]).inflate(40));
            h.assertTrue(!drops.isEmpty(), "no loot dropped");
            h.assertTrue(drops.stream().anyMatch(d -> d.getItem().is(ModItems.CROWN)), "no crown in the loot");
            for (var d : drops) d.discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "save")
    public void everythingSurvivesSaveAndReload(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.2f, HollowbellEntity.GUARDIAN, 61);
        h.runAfterDelay(20, () -> {
            e.popPods(3);
            e.cutThreads(7);
            e.setStay(true);
            e.hurtBy(50f);
            CompoundTag tag = new CompoundTag();
            e.saveWithoutId(tag);
            HollowbellEntity b = ModEntities.HOLLOWBELL.create(h.getLevel());
            b.load(tag);
            h.assertTrue(Math.abs(b.bellScale() - 0.2f) < 1e-4, "size lost: " + b.bellScale());
            h.assertTrue(b.isGuardian(), "mood lost");
            h.assertTrue(b.podsLeft() == e.podsLeft(), "pods lost: " + b.podsLeft() + " vs " + e.podsLeft());
            h.assertTrue(b.threadsHolding() == e.threadsHolding(), "threads lost: " + b.threadsHolding() + " vs " + e.threadsHolding());
            h.assertTrue(Math.abs(b.healthNow() - e.healthNow()) < 0.5f, "health lost: " + b.healthNow() + " vs " + e.healthNow());
            h.assertTrue(b.healthMax() == e.healthMax(), "max health lost");
            h.assertTrue(b.staying(), "hold still lost");
            b.discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "egg")
    public void theSmallSpawnEggMakesASmallOne(GameTestHelper h) {
        CompoundTag t = new CompoundTag();
        t.putInt("HollowbellEgg", 3);
        HollowbellEntity b = ModEntities.HOLLOWBELL.create(h.getLevel());
        b.readAdditionalSaveData(t);
        h.assertTrue(Math.abs(b.bellScale() - HollowbellConfig.V.smallEggScale) < 1e-4 && b.isHunter(), "small egg made " + b.bellScale());
        t.putInt("HollowbellEgg", 0);
        HollowbellEntity c = ModEntities.HOLLOWBELL.create(h.getLevel());
        c.readAdditionalSaveData(t);
        h.assertTrue(c.variant() == HollowbellEntity.CALM, "calm egg made " + c.variant());
        b.discard(); c.discard();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "drift")
    public void heDriftsWhereHeIsSent(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 62);
        Vec3[] start = new Vec3[1];
        h.runAfterDelay(20, () -> { start[0] = e.position(); e.setGoal(e.position().add(25, 0, 0)); });
        h.runAfterDelay(360, () -> {
            double moved = e.getX() - start[0].x;
            h.assertTrue(moved > 8, "he only drifted " + moved + " toward where he was sent");
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "sizes")
    public void theSmallestAndTheBiggestWork(GameTestHelper h) {
        HollowbellEntity tiny = spawnAway(h, HollowbellEntity.MIN_SCALE, HollowbellEntity.HUNTER, 70);
        HollowbellEntity big = spawnAway(h, HollowbellEntity.MAX_SCALE, HollowbellEntity.HUNTER, 72);
        Pig[] p = new Pig[1];
        h.runAfterDelay(20, () -> {
            tiny.setStay(true);
            p[0] = pig(h, under(tiny));
            p[0].setInvulnerable(true);
            h.assertTrue(tiny.forceMove(Moves.GRAB, p[0]), "the tiny one can't grab");
            h.assertTrue(big.forceMove(Moves.PULSE, null), "the big one can't pulse");
        });
        h.runAfterDelay(20 + Moves.length(Moves.GRAB) + 10, () -> {
            h.assertTrue(tiny.isAlive() && big.isAlive(), "one of them died");
            // too small to have room inside: the pig is squeezed and let go
            h.assertTrue(!tiny.moves().isInside(p[0]) && !p[0].isPassenger(), "the tiny one took a pig inside");
            h.assertTrue(tiny.healthMax() >= 40f, "the tiny one has almost no health: " + tiny.healthMax());
            h.assertTrue(Math.abs(big.healthMax() - HollowbellConfig.V.health * 2f) < 1f, "the big one's health: " + big.healthMax());
            p[0].discard();
            release(h, tiny);
            release(h, big);
            h.succeed();
        });
    }
}
