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
        e.skipArrival();
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
        for (String n : rig.boneNames) h.assertTrue(!n.startsWith("thread"), "a thread is still in the rig: " + n);
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
            double ground = e.groundAt(e.getX(), e.getZ());
            // drifting with nothing to do, he floats a little way over the ground, his strands near it
            h.assertTrue(e.getY() > ground - 1 && e.getY() < ground + 60 * S + 5, "he should float just over the ground: y " + e.getY() + " ground " + ground);
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

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "command_rights")
    public void detailIsForEveryoneTheRestNeedsCheats(GameTestHelper h) {
        var d = h.getLevel().getServer().getCommands().getDispatcher();
        var base = h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()).withSuppressedOutput();
        var player = base.withPermission(0);
        var op = base.withPermission(2);
        h.assertTrue(!d.parse("hollowbell detail on", player).getReader().canRead() && d.parse("hollowbell detail on", player).getExceptions().isEmpty(),
                "a player without cheats can't use /hollowbell detail");
        h.assertTrue(d.parse("hollowbell summon", player).getReader().canRead(), "a player without cheats can use /hollowbell summon");
        for (String c : new String[]{"hollowbell summon calm 0.3", "hollowbell list", "hollowbell do sky_dive", "hollowbell height 20",
                "hollowbell goto 10 10", "hollowbell stay true", "hollowbell ride", "hollowbell detail", "hollowbell reload"}) {
            var p = d.parse(c, op);
            h.assertTrue(!p.getReader().canRead() && p.getExceptions().isEmpty(), "/" + c + " doesn't parse with cheats on");
        }
        h.succeed();
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

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "move_volley") public void moveVolley(GameTestHelper h) { moveRuns(h, Moves.VOLLEY, 21); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "move_lash") public void moveLash(GameTestHelper h) { moveRuns(h, Moves.LASH, 22); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "move_flash") public void moveFlash(GameTestHelper h) { moveRuns(h, Moves.FLASH, 23); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "move_spores") public void moveSpores(GameTestHelper h) { moveRuns(h, Moves.SPORES, 24); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "move_podburst") public void movePodBurst(GameTestHelper h) { moveRuns(h, Moves.POD_BURST, 25); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "move_eggrain") public void moveEggRain(GameTestHelper h) { moveRuns(h, Moves.EGG_RAIN, 26); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "move_whirlpool") public void moveWhirlpool(GameTestHelper h) { moveRuns(h, Moves.WHIRLPOOL, 27); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "move_skydive") public void moveSkyDive(GameTestHelper h) { moveRuns(h, Moves.SKY_DIVE, 28); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "move_deeptoll") public void moveDeepToll(GameTestHelper h) { moveRuns(h, Moves.DEEP_TOLL, 29); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "move_armstorm") public void moveArmStorm(GameTestHelper h) { moveRuns(h, Moves.ARM_STORM, 33); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "move_stingerstorm") public void moveStingerStorm(GameTestHelper h) { moveRuns(h, Moves.STINGER_STORM, 34); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "move_sunlances") public void moveSunLances(GameTestHelper h) { moveRuns(h, Moves.SUN_LANCES, 35); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "move_undertow") public void moveUndertow(GameTestHelper h) { moveRuns(h, Moves.UNDERTOW, 36); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "tiers")
    public void everyMoveHasAStrength(GameTestHelper h) {
        int[] n = new int[3];
        for (int m : Moves.ORDER) n[Moves.tier(m)]++;
        h.assertTrue(Moves.ORDER.length == Moves.NAMES.length - 1, "the move order misses some: " + Moves.ORDER.length);
        h.assertTrue(Moves.NAMES.length - 1 >= 16, "fewer than 16 moves: " + (Moves.NAMES.length - 1));
        h.assertTrue(n[Moves.LIGHT] >= 4 && n[Moves.MEDIUM] >= 5 && n[Moves.HEAVY] >= 6, "light/medium/heavy: " + n[0] + "/" + n[1] + "/" + n[2]);
        for (int m = 1; m < Moves.NAMES.length; m++) {
            h.assertTrue(net.jj.hollowbell.net.CodexOrders.windCost(net.jj.hollowbell.net.CodexPayload.ATTACK_MOVE, m) > 0f, Moves.NAMES[m] + " costs no wind");
            if (Moves.tier(m) == Moves.HEAVY) h.assertTrue(net.jj.hollowbell.net.CodexOrders.windCost(net.jj.hollowbell.net.CodexPayload.ATTACK_MOVE, m) >= 0.4f, Moves.NAMES[m] + " is heavy but cheap");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "tired")
    public void aHeavyMoveWearsHimOut(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.HUNTER, 37);
        h.runAfterDelay(20, () -> { e.setStay(true); h.assertTrue(e.forceMove(Moves.DEEP_TOLL, null), "no deep toll"); });
        h.runAfterDelay(20 + Moves.length(Moves.DEEP_TOLL) + 5, () -> {
            h.assertTrue(e.tired() && e.moves().isTired(), "not worn out after a heavy move");
            h.assertTrue(e.maxSpeed() < 0.5 * (0.10 + 0.16 * Math.sqrt(S)), "worn out but not slower: " + e.maxSpeed());
        });
        h.runAfterDelay(20 + Moves.length(Moves.DEEP_TOLL) + 130, () -> {
            h.assertTrue(!e.tired(), "still worn out long after");
            release(h, e);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ he really kills things

    private static <T extends net.minecraft.world.entity.Mob> T mob(GameTestHelper h, EntityType<T> type, Vec3 at) {
        T m = type.create(h.getLevel());
        m.moveTo(at.x, at.y, at.z, 0f, 0f);
        m.setNoAi(true);
        m.setPersistenceRequired();
        // a helmet so the sun can't be what kills it
        m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));
        h.getLevel().addFreshEntity(m);
        return m;
    }

    /** starts a move at full size with the creature right where it lands, then checks it's dead a little after */
    private static void killsWith(GameTestHelper h, int move, EntityType<? extends net.minecraft.world.entity.Mob> type, int slot, int within) {
        HollowbellEntity e = spawnAway(h, 1f, HollowbellEntity.HUNTER, slot);
        net.minecraft.world.entity.Mob[] m = new net.minecraft.world.entity.Mob[1];
        float[] hp = new float[1];
        h.runAfterDelay(30, () -> {
            e.setStay(true);
            Vec3 at = under(e);
            if (move == Moves.SLAM) { Vec3 tip = e.armTipWorld(0); at = new Vec3(tip.x, e.groundAt(tip.x, tip.z), tip.z); }
            if (move == Moves.SWEEP) at = under(e).add(e.bellRadius() * 0.8, 0, 0);
            m[0] = mob(h, type, at);
            hp[0] = m[0].getHealth();
            e.moves().restNow();
            h.assertTrue(e.forceMove(move, m[0]), "couldn't start " + Moves.NAMES[move]);
        });
        h.runAfterDelay(30 + within, () -> {
            h.assertTrue(!m[0].isAlive(), "the " + type.getDescription().getString() + " (" + hp[0] + " health) lived through " + Moves.NAMES[move]
                    + " with " + m[0].getHealth() + " left");
            m[0].discard();
            e.moves().letGoOfEverything(false);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "kill_zombie")
    public void theSweepKillsAZombie(GameTestHelper h) { killsWith(h, Moves.SWEEP, EntityType.ZOMBIE, 100, Moves.length(Moves.SWEEP)); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "kill_skeleton")
    public void theArmSlamKillsASkeleton(GameTestHelper h) { killsWith(h, Moves.SLAM, EntityType.SKELETON, 102, Moves.length(Moves.SLAM)); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "kill_golem")
    public void theDropKillsAnIronGolem(GameTestHelper h) { killsWith(h, Moves.DROP, EntityType.IRON_GOLEM, 104, Moves.DROP_WIND + Moves.DROP_FALL + 20); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "kill_golem2")
    public void theDeepTollKillsAnIronGolem(GameTestHelper h) { killsWith(h, Moves.DEEP_TOLL, EntityType.IRON_GOLEM, 106, Moves.TOLL_BIG + 30); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 700, batch = "kill_cow")
    public void aCowTakenIntoTheDomeDies(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.25f, HollowbellEntity.HUNTER, 108);
        net.minecraft.world.entity.animal.Cow[] c = new net.minecraft.world.entity.animal.Cow[1];
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            c[0] = mob(h, EntityType.COW, under(e));
            h.assertTrue(e.forceMove(Moves.HARVEST, c[0]), "no harvest");
        });
        // it's hurt on the way up but lives to be taken in, so you see it go into the dome
        h.runAfterDelay(20 + Moves.REACH + Moves.LIFT - 5, () ->
                h.assertTrue(c[0].isAlive(), "the cow died on the way up, before it got to the dome"));
        h.runAfterDelay(20 + Moves.length(Moves.HARVEST) + 60, () -> {
            h.assertTrue(!c[0].isAlive(), "the cow is still alive " + (e.moves().isInside(c[0]) ? "inside the dome" : "outside") + " with " + c[0].getHealth());
            c[0].discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1500, batch = "guardian_clears")
    public void aGuardianClearsOutHostileMobs(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.5f, HollowbellEntity.GUARDIAN, 110);
        java.util.List<net.minecraft.world.entity.Mob> zs = new java.util.ArrayList<>();
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
            long alive = zs.stream().filter(net.minecraft.world.entity.LivingEntity::isAlive).count();
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
            pl[0].setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_HELMET));
            pl[0].setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_CHESTPLATE));
            pl[0].setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_LEGGINGS));
            pl[0].setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_BOOTS));
            e.moves().restNow();
            h.assertTrue(e.forceMove(Moves.DROP, pl[0]), "no drop");
        });
        h.runAfterDelay(30 + Moves.DROP_WIND + Moves.DROP_FALL + 10, () -> {
            float lost = 20f - pl[0].getHealth();
            h.assertTrue(!pl[0].isAlive() || lost >= 10f, "a player in netherite only lost " + lost + " health to the drop");
            drop(pl[0]);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 900, batch = "arrive")
    public void aNewOneComesDownOutOfTheSky(GameTestHelper h) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        int x = o.getX() + 20000 + 95 * 400, z = o.getZ() + 5000;
        for (int cx = (x >> 4) - 3; cx <= (x >> 4) + 3; cx++) for (int cz = (z >> 4) - 3; cz <= (z >> 4) + 3; cz++) { h.getLevel().setChunkForced(cx, cz, true); h.getLevel().getChunk(cx, cz); }
        int y = h.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
        HollowbellEntity e = ModEntities.HOLLOWBELL.create(h.getLevel());
        e.moveTo(x + 0.5, y, z + 0.5, 0f, 0f);
        e.setBellScale(S);
        e.setVariant(HollowbellEntity.CALM);
        h.getLevel().addFreshEntity(e);
        double[] top = new double[1];
        h.runAfterDelay(5, () -> { top[0] = e.getY(); h.assertTrue(top[0] > y + 20, "he didn't start up in the sky: " + top[0] + " over ground " + y); });
        h.runAfterDelay(850, () -> {
            h.assertTrue(e.getY() < top[0] - 20 && e.getY() < y + 60 * S + 8, "he didn't come down: at " + e.getY() + " (started " + top[0] + ", ground " + y + ")");
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "detail")
    public void detailOnOnlySimplifiesFarAway(GameTestHelper h) {
        boolean was = net.jj.hollowbell.Detail.on();
        try {
            net.jj.hollowbell.Detail.set(true);
            h.assertTrue(net.jj.hollowbell.Detail.lod(20, 1f) == net.jj.hollowbell.Detail.FULL, "detail on: not full close up");
            h.assertTrue(net.jj.hollowbell.Detail.lod(400, 1f) != net.jj.hollowbell.Detail.FULL, "detail on: not simpler far away");
            h.assertTrue(net.jj.hollowbell.Detail.lod(3000, 1f) == net.jj.hollowbell.Detail.TINY, "detail on: not simplest very far away");
            net.jj.hollowbell.Detail.set(false);
            for (double d : new double[]{5, 200, 800, 5000})
                for (float s : new float[]{0.03f, 0.2f, 1f, 2f})
                    h.assertTrue(net.jj.hollowbell.Detail.lod(d, s) == net.jj.hollowbell.Detail.FULL, "detail off but simplified at " + d + " size " + s);
            // it's kept in the settings file
            HollowbellConfig.load();
            h.assertTrue(!net.jj.hollowbell.Detail.on(), "detail off wasn't saved");
        } finally {
            net.jj.hollowbell.Detail.set(was);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "sounds")
    public void hisSoundsAreAllThere(GameTestHelper h) {
        var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(
                HollowbellGameTests.class.getResourceAsStream("/assets/hollowbell/sounds.json"), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        var lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(
                HollowbellGameTests.class.getResourceAsStream("/assets/hollowbell/lang/en_us.json"), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        for (var ev : net.jj.hollowbell.ModSounds.ALL) {
            var id = ev.getLocation();
            h.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(id), "not registered: " + id);
            h.assertTrue(json.has(id.getPath()), "no sounds.json entry for " + id);
            var o = json.getAsJsonObject(id.getPath());
            h.assertTrue(o.getAsJsonArray("sounds").size() > 0, "no sounds for " + id);
            h.assertTrue(lang.has(o.get("subtitle").getAsString()), "no subtitle text for " + id);
            for (var snd : o.getAsJsonArray("sounds")) {
                String name = snd.getAsJsonObject().get("name").getAsString();
                if (name.startsWith("hollowbell:"))
                    h.assertTrue(HollowbellGameTests.class.getResource("/assets/hollowbell/sounds/" + name.substring(11) + ".ogg") != null, "missing file for " + name);
            }
        }
        h.succeed();
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
        h.runAfterDelay(20 + Math.round(BellRig.SLAM_HIT * Moves.length(Moves.SLAM)), () -> { arm[0] = e.moveArg(); tipAt[0] = e.armTipWorld(Math.max(0, e.moveArg())); });
        h.runAfterDelay(20 + Moves.length(Moves.SLAM) + 5, () -> {
            h.assertTrue(!p[0].isAlive() || p[0].getHealth() < 10f, "the slam missed the pig under the arm: arm " + arm[0] + " tip " + tipAt[0] + " pig " + p[0].position() + " he " + e.position());
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
        // while it's held, the pig stays on the end of the strand holding it
        for (int k = 8; k <= 18; k += 5) {
            int at = k;
            h.runAfterDelay(20 + Moves.REACH + at, () -> {
                if (!p[0].isPassenger()) return;
                Vec3 tip = e.strandTipWorld(e.moveArg());
                double d = p[0].position().add(0, p[0].getBbHeight() * 0.5, 0).distanceTo(tip);
                h.assertTrue(d < 3.0, "the held pig is " + String.format("%.1f", d) + " blocks from the strand tip " + at + " ticks in");
            });
        }
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

    // ------------------------------------------------------------------ pods and eggs

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 900, batch = "pod_regrow")
    public void podsGrowBack(GameTestHelper h) {
        int was = HollowbellConfig.V.podRegrowSeconds;
        HollowbellConfig.V.podRegrowSeconds = 1;
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 40);
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            e.popPods(1);
            h.assertTrue(e.isPodPopped(0) && e.podGrowth(0) == 0f, "pod 0 wasn't popped");
        });
        h.runAfterDelay(60, () -> h.assertTrue(e.podGrowth(0) > 0f && e.podGrowth(0) < 1f, "pod 0 isn't growing back: " + e.podGrowth(0)));
        h.runAfterDelay(20 + 20 + 420, () -> {
            HollowbellConfig.V.podRegrowSeconds = was;
            h.assertTrue(e.podGrowth(0) >= 1f && !e.isPodPopped(0), "pod 0 never grew back: " + e.podGrowth(0));
            h.assertTrue(e.podsLeft() == e.rig.pods.length, "not all pods back");
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120, batch = "sunk")
    public void popAThirdOfThePodsAndHeSinks(GameTestHelper h) {
        int was = HollowbellConfig.V.sunkSeconds;
        HollowbellConfig.V.sunkSeconds = 1;
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 41);
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            e.popPods(e.podsToSink() - 1);
            h.assertTrue(!e.sunk(), "he sank before a third of his pods were popped");
            e.popPods(1);
            h.assertTrue(e.podsToSink() >= e.rig.pods.length / 4 && e.podsToSink() <= e.rig.pods.length / 2, "pods to sink: " + e.podsToSink());
        });
        h.runAfterDelay(35, () -> {
            h.assertTrue(e.sunk(), "a third of his pods popped and he hasn't sunk");
            h.assertTrue(!e.forceMove(Moves.DROP, null), "he can drop while he's already sunk");
            e.mendPods();
        });
        h.runAfterDelay(80, () -> {
            HollowbellConfig.V.sunkSeconds = was;
            h.assertTrue(!e.sunk(), "pods grown back and he's still sunk");
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
            e.drive(pl[0], 1f, 0f, 0f, 0);
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
            e.setStay(true);
            e.hurtBy(50f);
            CompoundTag tag = new CompoundTag();
            e.saveWithoutId(tag);
            HollowbellEntity b = ModEntities.HOLLOWBELL.create(h.getLevel());
            b.load(tag);
            h.assertTrue(Math.abs(b.bellScale() - 0.2f) < 1e-4, "size lost: " + b.bellScale());
            h.assertTrue(b.isGuardian(), "mood lost");
            h.assertTrue(b.podsLeft() == e.podsLeft(), "pods lost: " + b.podsLeft() + " vs " + e.podsLeft());
            h.assertTrue(b.podGrowth(0) == e.podGrowth(0), "pod growth lost");
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

    // ------------------------------------------------------------------ flying, and moving smoothly

    /** the biggest change in his speed from one tick to the next, and whether he ever went into the ground */
    private static final class Watch {
        Vec3 lastV; double maxDv, worstDip = 1e9; int ticks;
        void see(HollowbellEntity e) {
            Vec3 v = e.velocity();
            if (lastV != null) maxDv = Math.max(maxDv, v.subtract(lastV).length());
            lastV = v;
            // his rim above the ground under his middle
            worstDip = Math.min(worstDip, e.getY() + (e.rig.rimY - 10) * e.bellScale() - e.groundAt(e.getX(), e.getZ()));
            ticks++;
        }
    }

    private static void watch(GameTestHelper h, HollowbellEntity e, Watch w, int from, int to) {
        for (int t = from; t < to; t++) h.runAfterDelay(t, () -> w.see(e));
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1100, batch = "fly")
    public void heFliesUpToWhatIsUpHighAndSinksBackDown(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.HUNTER, 80);
        Pig[] p = new Pig[1];
        double[] y0 = new double[1];
        Watch w = new Watch();
        h.runAfterDelay(20, () -> {
            y0[0] = e.getY();
            // a pig high up in the air, right over him
            p[0] = pig(h, e.position().add(0, 45, 0));
            p[0].setNoGravity(true);
            p[0].setInvulnerable(true);
            e.sendAfter(p[0]);
        });
        watch(h, e, w, 21, 1000);
        h.runAfterDelay(460, () -> {
            h.assertTrue(e.getY() > y0[0] + 30, "he didn't fly up to the pig: from " + y0[0] + " to " + e.getY() + " (pig at " + p[0].getY() + ")");
            // down it comes, to the ground
            p[0].teleportTo(p[0].getX(), e.groundAt(p[0].getX(), p[0].getZ()), p[0].getZ());
        });
        h.runAfterDelay(1000, () -> {
            h.assertTrue(e.getY() < y0[0] + 8, "he didn't come back down after the pig: at " + e.getY() + " ground " + e.groundAt(e.getX(), e.getZ()));
            // smooth: no sudden change of speed, and never into the ground
            h.assertTrue(w.maxDv < 0.06, "his speed jumped by " + w.maxDv + " in one tick");
            h.assertTrue(w.worstDip > 0, "his rim went into the ground by " + (-w.worstDip));
            p[0].discard();
            release(h, e);
            h.succeed();
        });
    }

    /**
     * Watches every arm and strand joint and his crown, tick by tick. A part may move fast (a slam is fast), but
     * nothing may jump: the change in its speed from one tick to the next (its acceleration) stays small, and it
     * never moves far further than he does.
     */
    private static final class PoseWatch {
        float[][] last, last2; Vector3f lastCrown, lastCrown2; float worstJerk, worstStep; String where = "", whereStep = "";
        final java.util.List<float[][]> hist = new java.util.ArrayList<>(); int wc = -1, wi, wt, tick;
        final java.util.List<String> body = new java.util.ArrayList<>();
        String trail() {
            if (wc < 0) return "";
            StringBuilder b = new StringBuilder(" path:");
            for (int t = Math.max(0, wt - 5); t < Math.min(hist.size(), wt + 3); t++) { float[] q = hist.get(t)[wc]; b.append(String.format(" [%.1f %.1f %.1f | %s]", q[wi], q[wi + 1], q[wi + 2], body.get(t))); }
            return b.toString();
        }
        void see(HollowbellEntity e, String when) {
            e.ensurePose();
            var st = e.state;
            Vector3f crown = e.rig.at(e.pose, e.rig.crownBone, new Vector3f(0, e.rig.crownY, 0));
            if (last2 != null) {
                // his own acceleration and step, taken off
                Vector3f ca = new Vector3f(crown).sub(lastCrown).sub(new Vector3f(lastCrown).sub(lastCrown2));
                float cstep = crown.distance(lastCrown);
                for (int c = 0; c < st.chain.length; c++) {
                    float[] a = last2[c], b = last[c], n = st.chain[c];
                    for (int i = 0; i < n.length; i += 3) {
                        float jx = n[i] - 2 * b[i] + a[i] - ca.x, jy = n[i + 1] - 2 * b[i + 1] + a[i + 1] - ca.y, jz = n[i + 2] - 2 * b[i + 2] + a[i + 2] - ca.z;
                        float j = (float) Math.sqrt(jx * jx + jy * jy + jz * jz);
                        // (hitting the ground, or his bell coming down on it, stops a part short: a knock, not a jump)
                        if (e.chainKnocked(c)) continue;
                        if (j > worstJerk) { worstJerk = j; wc = c; wi = i; wt = tick; where = when + " chain " + c + " (" + (e.rig.chains[c].arm ? "arm" : "strand") + ") point " + i / 3; }
                        float sx = n[i] - b[i], sy = n[i + 1] - b[i + 1], sz = n[i + 2] - b[i + 2];
                        float step = (float) Math.sqrt(sx * sx + sy * sy + sz * sz) - 2 * cstep;
                        if (step > worstStep) { worstStep = step; whereStep = when + " chain " + c + " point " + i / 3; }
                    }
                }
            }
            float[][] snap = new float[st.chain.length][];
            for (int c = 0; c < st.chain.length; c++) snap[c] = st.chain[c].clone();
            hist.add(snap);
            body.add(String.format("low %.1f sq %.2f tilt %.2f,%.2f y %.2f vy %.3f", st.lower, st.squeeze, st.tiltX, st.tiltZ, e.getY(), e.velocity().y));
            tick++;
            last2 = last; lastCrown2 = lastCrown;
            last = new float[st.chain.length][];
            for (int c = 0; c < st.chain.length; c++) last[c] = st.chain[c].clone();
            lastCrown = crown;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "no_snap")
    public void movesBlendInAndOutEvenCutOffHalfway(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 81);
        Pig[] p = new Pig[1];
        PoseWatch w = new PoseWatch();
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            p[0] = pig(h, under(e).add(3, 0, 0));
            p[0].setInvulnerable(true);
            h.assertTrue(e.forceMove(Moves.SLAM, p[0]), "no slam");
        });
        // cut the slam off at the top of its swing with a sweep, then the sweep off with a drop, then the drop off
        h.runAfterDelay(52, () -> h.assertTrue(e.forceMove(Moves.SWEEP, p[0]), "no sweep"));
        h.runAfterDelay(80, () -> h.assertTrue(e.forceMove(Moves.DROP, p[0]), "no drop"));
        h.runAfterDelay(140, () -> e.forceMove(Moves.CURTAIN, p[0]));
        h.runAfterDelay(170, () -> e.moves().stopNow());
        for (int t = 21; t < 400; t++) { int tt = t; h.runAfterDelay(t, () -> w.see(e, "tick " + tt)); }
        h.runAfterDelay(400, () -> {
            h.assertTrue(w.worstJerk < 7f, "a part of him lurched: its speed changed by " + w.worstJerk + " model blocks a tick in one tick (" + w.where + ")" + w.trail());
            h.assertTrue(w.worstStep < 30f, "a part of him jumped " + w.worstStep + " model blocks in one tick (" + w.whereStep + ")");
            p[0].discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "full_size")
    public void aFullSizeOneFliesSteadily(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 1f, HollowbellEntity.CALM, 90);
        StringBuilder path = new StringBuilder();
        for (int t = 5; t < 300; t += 15) { int tt = t; h.runAfterDelay(t, () -> path.append(String.format(" %d:(%.1f %.1f %.1f)", tt, e.getX(), e.getY(), e.getZ()))); }
        h.runAfterDelay(300, () -> {
            boolean ok = Double.isFinite(e.getX()) && Double.isFinite(e.getY()) && !e.isRemoved() && e.position().distanceTo(e.home()) < 200;
            h.assertTrue(ok, "he went wrong:" + path);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 700, batch = "dive_unhurt")
    public void aFullSizeSkyDiveDoesntHurtHim(GameTestHelper h) {
        // turned over at full size, the point he's measured from goes deep under the ground: that mustn't hurt him
        HollowbellEntity e = spawnAway(h, 1f, HollowbellEntity.CALM, 114);
        Pig[] p = new Pig[1];
        float[] hp = new float[1];
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            p[0] = pig(h, under(e));
            p[0].setInvulnerable(true);
            hp[0] = e.healthNow();
            h.assertTrue(e.forceMove(Moves.SKY_DIVE, p[0]), "no sky dive");
        });
        h.runAfterDelay(20 + Moves.length(Moves.SKY_DIVE) + 20, () -> {
            h.assertTrue(e.moveNow() != Moves.SKY_DIVE, "the sky dive never finished");
            h.assertTrue(e.healthNow() >= hp[0] - 0.01f, "his own sky dive hurt him: " + hp[0] + " -> " + e.healthNow());
            p[0].discard();
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
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "nocarry")
    public void heNeverPicksUpTheGiants(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.2f, HollowbellEntity.HUNTER, 70);
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            Vec3 at = under(e);
            net.minecraft.world.entity.monster.Zombie z = net.minecraft.world.entity.EntityType.ZOMBIE.create(h.getLevel());
            z.moveTo(at.x, at.y, at.z, 0f, 0f);
            z.setNoAi(true);
            h.getLevel().addFreshEntity(z);
            net.minecraft.world.entity.monster.Giant giant = net.minecraft.world.entity.EntityType.GIANT.create(h.getLevel());
            giant.moveTo(at.x + 3, at.y, at.z, 0f, 0f);
            giant.setNoAi(true);
            h.getLevel().addFreshEntity(giant);
            net.minecraft.world.entity.boss.wither.WitherBoss wither = net.minecraft.world.entity.EntityType.WITHER.create(h.getLevel());
            h.assertTrue(HollowbellEntity.canCarry(z), "a zombie can be picked up");
            h.assertFalse(HollowbellEntity.canCarry(giant), "a giant is too big to pick up");
            h.assertFalse(HollowbellEntity.canCarry(wither), "a boss can't be picked up");
            h.assertFalse(e.forceMove(Moves.GRAB, giant), "he doesn't try to grab the giant");
            h.assertFalse(e.forceMove(Moves.WRAP, giant), "or wrap it");
            z.discard();
            giant.discard();
            release(h, e);
            h.succeed();
        });
    }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "bosshits")
    public void anotherGiantsBlowLandsInFull(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 71);
        h.runAfterDelay(20, () -> {
            net.minecraft.world.entity.monster.Zombie z = net.minecraft.world.entity.EntityType.ZOMBIE.create(h.getLevel());
            z.moveTo(e.getX(), e.getY(), e.getZ(), 0f, 0f);
            float before = e.healthNow();
            e.hurt(h.getLevel().damageSources().mobAttack(z), 50f);
            h.assertTrue(before - e.healthNow() > 49f, "a creature's blow should land in full: " + (before - e.healthNow()));
            float before2 = e.healthNow();
            e.hurt(h.getLevel().damageSources().mobAttack(z), 1.0E6f);
            h.assertTrue(e.healthNow() <= 0f || e.isDeadOrDying(), "the Unmake's killing blow should kill him: " + e.healthNow() + " of " + before2);
            release(h, e);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ out of the world and back again

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "away")
    public void aloneHeStepsOutAndComesBackTheSame(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 116);
        var a = net.jj.hollowbell.world.Away.get(h.getLevel().getServer());
        h.runAfterDelay(30, () -> {
            e.popPods(2);
            e.hurt(e.damageSources().generic(), 40f);
            final float hp = e.healthNow();
            final int pods = e.podsLeft();
            final java.util.UUID id = e.getUUID();
            final Vec3 was = e.position();
            e.setGoal(was.add(3000, 0, 0));
            h.assertTrue(e.stepAside(), "he should step out of the world");
            h.assertTrue(e.isRemoved(), "and be gone from it");
            var r = a.get(id);
            h.assertTrue(r != null, "and be written down instead");
            h.assertTrue(r.going && Math.abs(r.toX - (was.x + 3000)) < 2, "still going where he was going");
            h.runAfterDelay(40, () -> {
                Vec3 s = r.spot(h.getLevel().getGameTime());
                h.assertTrue(s.x > was.x + 1, "the sum should move him along: " + s.x + " from " + was.x);
                // somebody walks up to where the sum says he is: he comes back
                ServerPlayer p = player(h, new Vec3(s.x, e.groundAt(s.x, s.z) + 1, s.z));
                h.runAfterDelay(45, () -> {
                    HollowbellEntity back = null;
                    for (HollowbellEntity m : h.getLevel().getEntities(ModEntities.HOLLOWBELL, m -> !m.isRemoved() && m.getUUID().equals(id))) back = m;
                    h.assertTrue(back != null, "he should be back in the world");
                    h.assertTrue(a.get(id) == null, "and the sum finished with");
                    h.assertTrue(Math.abs(back.healthNow() - hp) < 1f, "with the health he left with: " + back.healthNow() + " want " + hp);
                    h.assertTrue(back.podsLeft() == pods, "and the pods he left with: " + back.podsLeft() + " want " + pods);
                    h.assertTrue(back.getX() > was.x + 1, "further along than he left: " + back.getX());
                    h.assertTrue(back.goal() != null && Math.abs(back.goal().x - (was.x + 3000)) < 2, "and still going there");
                    h.assertTrue(back.getY() > back.groundAt(back.getX(), back.getZ()), "floating, not in the ground");
                    drop(p);
                    release(h, back);
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "away_book")
    public void theBookStillReachesHimOutThere(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 118);
        var a = net.jj.hollowbell.world.Away.get(h.getLevel().getServer());
        net.minecraft.server.level.ServerLevel sl = h.getLevel();
        h.runAfterDelay(30, () -> {
            final java.util.UUID id = e.getUUID();
            Vec3 was = e.position();
            e.setGoal(was.add(4000, 0, 0));
            h.assertTrue(e.stepAside(), "out of the world he goes");
            var r = a.get(id);
            Vec3 second = was.add(-2500, 0, 0);
            h.assertTrue(a.send(sl, id, second), "the book should still reach him");
            h.assertTrue(Math.abs(r.toX - second.x) < 2, "and turn him for the new spot");
            h.assertTrue(r.minutesLeft(sl.getGameTime()) > 0, "with a way still to go");
            h.assertTrue(a.stay(sl, id, true), "he can be told to hold still out there");
            h.assertTrue(!r.going && r.stay, "and the sum stops");
            a.forget(id);
            h.assertTrue(a.get(id) == null, "and forgotten");
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "bar_range")
    public void hisBarsShowFromFarOff(GameTestHelper h) {
        h.assertTrue(HollowbellEntity.barRange(1f) >= 500, "full size: " + HollowbellEntity.barRange(1f));
        h.assertTrue(HollowbellEntity.barRange(0.1f) >= 250, "small: " + HollowbellEntity.barRange(0.1f));
        h.assertTrue(net.jj.hollowbell.world.Away.awayRange(h.getLevel().getServer(), 1f) > HollowbellEntity.barRange(1f),
                "he never steps out while you can still see his bars");
        h.succeed();
    }

    // ------------------------------------------------------------------ the other giants (JJ's other bosses)

    /** a stand-in for another of JJ's bosses: anything tagged as a giant counts as one */
    private static net.minecraft.world.entity.monster.Zombie fakeGiant(GameTestHelper h, Vec3 at) {
        net.minecraft.world.entity.monster.Zombie z = EntityType.ZOMBIE.create(h.getLevel());
        z.moveTo(at.x, at.y, at.z, 0f, 0f);
        z.setNoAi(true);
        z.setPersistenceRequired();
        z.addTag(net.jj.hollowbell.entity.Giants.TAG);
        h.getLevel().addFreshEntity(z);
        return z;
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "giants_armour")
    public void aGiantsBlowLandsByHisArmourAgainstGiants(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 120);
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            h.assertTrue(e.getTags().contains(net.jj.hollowbell.entity.Giants.TAG), "he isn't tagged as a giant");
            var g = fakeGiant(h, under(e));
            float before = e.healthNow();
            e.hurt(e.damageSources().mobAttack(g), 100f);
            float took = before - e.healthNow();
            float want = 100f * HollowbellConfig.V.giantArmor * net.jj.hollowbell.entity.Giants.fightPace(e.bellScale(), 1f);
            h.assertTrue(Math.abs(took - want) < 0.5f, "a giant's blow of 100 took " + took + ", not " + want);
            // a blast that isn't a giant's: a bit over a third
            before = e.healthNow();
            e.hurt(e.damageSources().explosion(null, null), 100f);
            took = before - e.healthNow();
            h.assertTrue(Math.abs(took - 35f) < 0.5f, "a blast of 100 took " + took + ", not 35");
            // an ordinary zombie's blow still lands in full
            var z = EntityType.ZOMBIE.create(h.getLevel());
            z.moveTo(e.getX(), e.getY(), e.getZ(), 0f, 0f);
            before = e.healthNow();
            e.hurt(e.damageSources().mobAttack(z), 100f);
            took = before - e.healthNow();
            h.assertTrue(Math.abs(took - 100f) < 0.5f, "a zombie's blow of 100 took " + took);
            // a giant's small helper (a belling, a mudling, ...) counts as its giant
            var kin = EntityType.ZOMBIE.create(h.getLevel());
            kin.moveTo(e.getX(), e.getY(), e.getZ(), 0f, 0f);
            kin.addTag(net.jj.hollowbell.entity.Giants.KIN);
            float b3 = e.healthNow();
            e.hurt(e.damageSources().mobAttack(kin), 100f);
            float kinTook = b3 - e.healthNow();
            float kinWant = 100f * HollowbellConfig.V.giantArmor * net.jj.hollowbell.entity.Giants.fightPace(e.bellScale(), 1f);
            h.assertTrue(Math.abs(kinTook - kinWant) < 0.5f, "a giant's helper's blow of 100 took " + kinTook + ", not " + kinWant);
            g.discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "giants_nocarry")
    public void heNeverCarriesAGiant(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.2f, HollowbellEntity.HUNTER, 121);
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            var g = fakeGiant(h, under(e));
            h.assertFalse(HollowbellEntity.canCarry(g), "a giant (even a small one) can't be picked up");
            h.assertFalse(e.forceMove(Moves.GRAB, g), "he doesn't try to grab it");
            h.assertFalse(e.forceMove(Moves.WRAP, g), "or wrap it");
            h.assertTrue(g.getVehicle() == null, "it got picked up anyway");
            g.discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "giants_off")
    public void withGiantsOffHeLeavesThemAlone(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.2f, HollowbellEntity.HUNTER, 122);
        h.runAfterDelay(20, () -> {
            var g = fakeGiant(h, under(e));
            boolean was = HollowbellConfig.V.fightGiants;
            try {
                HollowbellConfig.V.fightGiants = true;
                h.assertTrue(e.fairGame(g), "with giants on, another giant is fair game");
                HollowbellConfig.V.fightGiants = false;
                h.assertFalse(e.fairGame(g), "with giants off, he leaves it alone");
            } finally { HollowbellConfig.V.fightGiants = was; }
            g.discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "pod_worth")
    public void aPoppedPodTakesOnePercent(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 123);
        h.runAfterDelay(20, () -> {
            h.assertTrue(new HollowbellConfig.Values().podRegrowSeconds == 150, "pods should take 150 s to start growing back");
            float before = e.healthNow();
            e.popPods(1);
            float took = before - e.healthNow();
            h.assertTrue(Math.abs(took - e.healthMax() * 0.01f) < 0.05f, "a pod took " + took + " of " + e.healthMax());
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "sweep_reach")
    public void hisSweepReachesAllTheWayOut(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.HUNTER, 124);
        net.minecraft.world.entity.monster.Husk[] z = new net.minecraft.world.entity.monster.Husk[1];
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            // out past the box round his body (112 at full size), still inside the sweep (88 x 1.35 + 4)
            double d = e.bellRadius() * 1.33 + 2;
            double dx = e.getX() + d, dz = e.getZ();
            h.assertTrue(d > 112 * e.bellScale() + 2, "the test spot is inside his box anyway: " + d);
            z[0] = EntityType.HUSK.create(h.getLevel());
            z[0].moveTo(dx, e.groundAt(dx, dz), dz, 0f, 0f);
            z[0].setNoAi(true);
            z[0].setPersistenceRequired();
            h.getLevel().addFreshEntity(z[0]);
            e.setTarget(z[0]);
            h.assertTrue(e.forceMove(Moves.SWEEP, z[0]), "could not start the sweep");
        });
        h.runAfterDelay(20 + Moves.length(Moves.SWEEP) + 10, () -> {
            h.assertTrue(z[0].getLastHurtByMob() == e || z[0].isDeadOrDying(), "the sweep never reached the husk " + (int) e.horiz(z[0].position()) + " blocks out");
            z[0].discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "giants_once")
    public void aGiantOfManyPartsHitsOnceATick(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 136);
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            var g = fakeGiant(h, under(e));
            float armour = HollowbellConfig.V.giantArmor * net.jj.hollowbell.entity.Giants.fightPace(e.bellScale(), 1f);
            float before = e.healthNow();
            // four parts of one giant land at once
            for (int i = 0; i < 4; i++) e.hurt(e.damageSources().mobAttack(g), 100f);
            float took = before - e.healthNow();
            h.assertTrue(Math.abs(took - 100f * armour) < 0.5f, "four parts' blows of 100 in one tick took " + took + ", not " + 100f * armour);
            // a bigger one the same tick: only what it's over the first
            before = e.healthNow();
            e.hurt(e.damageSources().mobAttack(g), 150f);
            took = before - e.healthNow();
            h.assertTrue(Math.abs(took - 50f * armour) < 0.5f, "a bigger blow the same tick took " + took + ", not " + 50f * armour);
            float[] next = {e.healthNow()};
            h.runAfterDelay(1, () -> {
                e.hurt(e.damageSources().mobAttack(g), 100f);
                float t2 = next[0] - e.healthNow();
                h.assertTrue(Math.abs(t2 - 100f * armour) < 0.5f, "the next tick's blow took " + t2);
                g.discard();
                release(h, e);
                h.succeed();
            });
        });
    }

    /** a part box of another giant: not a creature itself, it has an owner() (like Pitchgut's, the Furrowmaw's, the Cerberus's) */
    public static class FakePart extends net.minecraft.world.entity.Interaction {
        final net.minecraft.world.entity.LivingEntity owner;
        public FakePart(net.minecraft.world.level.Level l, net.minecraft.world.entity.LivingEntity owner) {
            super(EntityType.INTERACTION, l);
            this.owner = owner;
            addTag(net.jj.hollowbell.entity.Giants.TAG);
        }
        public net.minecraft.world.entity.LivingEntity owner() { return owner; }
    }

    /**
     * Another giant made of part boxes (its middle far off, one part right beside him) is struck through the part: his
     * pulse wave reaches the part and the giant it belongs to is hurt. A part deep under the ground is out of reach.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "giant_parts")
    public void heStrikesAnotherGiantThroughItsParts(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 148);
        net.minecraft.world.entity.monster.Zombie[] g = new net.minecraft.world.entity.monster.Zombie[2];
        FakePart[] part = new FakePart[2];
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            double far = e.bellRadius() * 3 + 40;
            // one giant with a part on the ground beside him, one with its only part deep under the ground
            for (int i = 0; i < 2; i++) {
                g[i] = fakeGiant(h, e.position().add(i == 0 ? far : -far, 0, 0));
                g[i].setHealth(20f);
                part[i] = new FakePart(h.getLevel(), g[i]);
                double x = e.getX() + (i == 0 ? 1 : -1) * e.bellRadius() * 1.1, z = e.getZ();
                part[i].moveTo(x, e.groundAt(x, z) - (i == 0 ? 0 : 12), z, 0f, 0f);
                h.getLevel().addFreshEntity(part[i]);
                h.assertTrue(net.jj.hollowbell.entity.Giants.ownerOf(part[i]) == g[i], "the part's giant wasn't found");
            }
            h.assertTrue(e.forceMove(Moves.PULSE, g[0]), "no pulse wave");
        });
        h.runAfterDelay(20 + Moves.length(Moves.PULSE) + 10, () -> {
            h.assertTrue(g[0].getHealth() < 20f || g[0].isDeadOrDying(), "the pulse wave never struck the giant through its part");
            h.assertTrue(g[1].getHealth() >= 20f, "a part deep under the ground was struck");
            for (int i = 0; i < 2; i++) { part[i].discard(); g[i].discard(); }
            release(h, e);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ leaving the game while he has you

    /** after a player left the game while he had them: on the ground beside him, let go, falling gently */
    private static void checkPutDown(GameTestHelper h, HollowbellEntity e, ServerPlayer p, String how) {
        h.assertTrue(p.getVehicle() == null, how + ": still riding something");
        double over = p.getY() - e.groundAt(p.getX(), p.getZ());
        h.assertTrue(over > -0.2 && over < 1.0, how + ": saved " + over + " over the ground");
        h.assertTrue(e.horiz(p.position()) > e.bellRadius(), how + ": put down under him, " + e.horiz(p.position()) + " from his middle");
        h.assertTrue(h.getLevel().noCollision(p, p.getBoundingBox()) && !h.getLevel().containsAnyLiquid(p.getBoundingBox()), how + ": put down inside blocks or water");
        h.assertTrue(p.hasEffect(MobEffects.SLOW_FALLING), how + ": no slow falling");
        h.assertTrue(e.rider() != p && !e.moves().caught(p) && e.moves().grabbed() != p && e.moves().wrapped() != p && !e.moves().isInside(p), how + ": he still has them");
        net.jj.hollowbell.HollowbellMod.LOG.info("leaving while {}: saved {} over the ground, {} from his middle", how,
                String.format("%.2f", over), String.format("%.1f", e.horiz(p.position())));
    }

    /**
     * A player leaves the game while he has them one way or another (kind: riding his crown, being carried up onto
     * it, held by a strand, wrapped by an arm, inside his dome): they're let go and saved on the ground beside him.
     */
    private static void leaveCase(GameTestHelper h, String kind, int slot) {
        float s = 0.3f;
        HollowbellEntity e = spawnAway(h, s, HollowbellEntity.CALM, slot);
        forceAround(h, e, 5);
        int ox = e.getBlockX(), oz = e.getBlockZ();
        ServerPlayer[] pl = new ServerPlayer[1];
        int[] st = {0, 0};
        h.runAfterDelay(20, () -> {
            e.ensurePose();
            switch (kind) {
                case "riding" -> {
                    e.setStay(true);
                    pl[0] = player(h, e.position().add(40 * s + 5, 0, 0));
                    h.assertTrue(e.possess(pl[0]), "couldn't get on him");
                }
                case "carried up" -> {
                    double x = e.getX() + 75 * s + 3, z = e.getZ() + 30 * s + 1;
                    pl[0] = player(h, new Vec3(x, e.groundAt(x, z), z));
                    h.assertTrue(e.comeAndGetMe(pl[0]), "he won't come for the player");
                }
                case "held by a strand", "inside the dome" -> {
                    e.setStay(true);
                    pl[0] = player(h, under(e));
                    pl[0].setInvulnerable(true);
                    h.assertTrue(e.forceMove(Moves.GRAB, pl[0]), "no grab");
                }
                case "wrapped by an arm" -> {
                    e.setStay(true);
                    pl[0] = player(h, e.armTipWorld(0));
                    pl[0].setInvulnerable(true);
                    h.assertTrue(e.forceMove(Moves.WRAP, pl[0]), "no wrap");
                }
                default -> throw new IllegalArgumentException(kind);
            }
        });
        h.onEachTick(() -> {
            ServerPlayer p = pl[0];
            if (p == null || st[0] == 2) return;
            // (the arm closes where its end is: the player is put right there as it does)
            if (st[0] == 0 && kind.equals("wrapped by an arm") && e.moveNow() == Moves.WRAP && e.moves().t() == Moves.WRAP_REACH - 1)
                p.teleportTo(h.getLevel(), e.armTipWorld(e.moveArg()).x, e.armTipWorld(e.moveArg()).y, e.armTipWorld(e.moveArg()).z, 0f, 0f);
            if (st[0] == 0) {
                boolean has = switch (kind) {
                    case "riding" -> e.rider() == p && p.getY() > e.getY() + 50 * s;
                    case "carried up" -> e.moves().grabbed() == p && p.getY() > e.groundAt(p.getX(), p.getZ()) + 20 * s;
                    case "held by a strand" -> e.moves().grabbed() == p && e.moves().t() > Moves.REACH + 20;
                    case "inside the dome" -> e.moves().isInside(p);
                    default -> e.moves().wrapped() == p && e.moves().t() > Moves.WRAP_REACH + 10;
                };
                h.assertTrue(h.getTick() < 20 + 800, "he never got hold of the player (" + kind + ")");
                if (!has) return;
                h.assertTrue(p.getVehicle() instanceof net.jj.hollowbell.entity.Seat, kind + ": not on one of his seats");
                drop(p);
                checkPutDown(h, e, p, kind);
                st[0] = 1;
                return;
            }
            // a strand that was carrying them up goes back down and hangs again
            if (++st[1] > 5 && (!kind.equals("carried up") || e.moveNow() == Moves.NONE)) {
                h.assertTrue(e.moveNow() != Moves.GRAB || kind.equals("inside the dome"), kind + ": the strand is still holding on");
                if (kind.equals("carried up")) {
                    int strand = e.rig.strands.length > 0 ? e.moves().lastStrand() : 0;
                    float tipY = e.toModel(e.strandTipWorld(strand)).y;
                    h.assertTrue(tipY < e.rig.rimY, "the strand didn't go back down: " + tipY);
                }
                checkPutDown(h, e, p, kind + " (a few ticks on)");
                st[0] = 2;
                release(h, e);
                unforceAround(h, ox, oz, 5);
                h.succeed();
            }
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "leave_riding")
    public void leavingWhileOnHisCrownPutsYouDown(GameTestHelper h) { leaveCase(h, "riding", 140); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200, batch = "leave_carried")
    public void leavingWhileBeingCarriedUpPutsYouDown(GameTestHelper h) { leaveCase(h, "carried up", 141); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "leave_grabbed")
    public void leavingWhileAStrandHoldsYouPutsYouDown(GameTestHelper h) { leaveCase(h, "held by a strand", 142); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "leave_wrapped")
    public void leavingWhileAnArmHoldsYouPutsYouDown(GameTestHelper h) { leaveCase(h, "wrapped by an arm", 143); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "leave_inside")
    public void leavingWhileInsideHisDomePutsYouDown(GameTestHelper h) { leaveCase(h, "inside the dome", 144); }

    // ------------------------------------------------------------------ "ride him": carried up onto his crown

    /** the middle of something */
    private static Vec3 mid(net.minecraft.world.entity.Entity e) { return e.getBoundingBox().getCenter(); }

    /**
     * The body of a rider as it really is: a sitting rider's legs go out in front, but the game keeps their box
     * standing and sinks it below the seat (0.6 for a player), so the box's bottom is taken up to the seat.
     */
    private static AABB sitting(net.minecraft.world.entity.Entity e) {
        AABB b = e.getBoundingBox();
        if (e.getVehicle() != null) b = b.setMinY(Math.max(b.minY, e.getVehicle().getY()));
        return b;
    }

    /**
     * How the strand carrying somebody looks right now: the biggest angle between two neighbouring pieces of it
     * (degrees), and how stretched it is (its length now over its length as built), both over the pieces that are
     * paid out (the ones still drawn in at its root, short stubs, don't count), and how many are drawn in.
     */
    private static float[] ropeLook(HollowbellEntity e, int strand) {
        var ch = e.rig.chains[e.rig.strandChain[strand]];
        float[] p = e.chainNow(ch.id);
        float len = 0f, rest = 0f, worst = 0f, in = 0f;
        Vector3f[] d = new Vector3f[ch.points() - 1];
        boolean[] out = new boolean[d.length];
        for (int i = 0; i < d.length; i++) {
            d[i] = new Vector3f(p[3 * i + 3] - p[3 * i], p[3 * i + 4] - p[3 * i + 1], p[3 * i + 5] - p[3 * i + 2]);
            out[i] = d[i].length() >= 0.5f * ch.restLen[i];
            if (out[i]) { len += d[i].length(); rest += ch.restLen[i]; } else in++;
        }
        for (int i = 0; i + 1 < d.length; i++)
            if (out[i] && out[i + 1]) worst = Math.max(worst, (float) Math.toDegrees(d[i].angle(d[i + 1])));
        return new float[]{worst, rest > 0f ? len / rest : 1f, in};
    }

    /** keeps the chunks round him loaded, n chunks each way, for a big one that moves about */
    private static void forceAround(GameTestHelper h, HollowbellEntity e, int n) {
        int x = e.getBlockX(), z = e.getBlockZ();
        for (int cx = (x >> 4) - n; cx <= (x >> 4) + n; cx++) for (int cz = (z >> 4) - n; cz <= (z >> 4) + n; cz++) {
            h.getLevel().setChunkForced(cx, cz, true);
            h.getLevel().getChunk(cx, cz);
        }
    }

    private static void unforceAround(GameTestHelper h, int x, int z, int n) {
        for (int cx = (x >> 4) - n; cx <= (x >> 4) + n; cx++) for (int cz = (z >> 4) - n; cz <= (z >> 4) + n; cz++) h.getLevel().setChunkForced(cx, cz, false);
    }

    /**
     * The whole pick-up, the way the book's "ride him" does it: he comes alongside, a strand takes the player round
     * the middle and carries them up his side, round the rim and over the dome, and sets them on his crown.
     * Checked every tick from the moment the strand closes round them until they sit on the crown:
     *  - they never move more than 0.4 + 1.6 x size blocks in a tick. The way up is paced at about 0.14 + 1.2 x
     *    size at its fastest (about a block a tick at his full size, like his own top speed with a strand's pull
     *    on top); the rest is room for his body bobbing as he swims. A jump onto the crown from the strand's end
     *    would be tens of blocks at any size.
     *  - the end of the strand holding them (worked out on the server's own pose) is never more than 2 + 3 x size
     *    blocks from their middle;
     *  - no block of him (other than the strand holding them) is ever inside them, at each tick or anywhere on the
     *    straight line between one tick and the next (so they can't go through the glass, the copper or the rim);
     *  - they end on the crown seat, riding him.
     */
    private static void carriedUp(GameTestHelper h, float s, int slot) {
        HollowbellEntity e = spawnAway(h, s, HollowbellEntity.CALM, slot);
        int n = (int) Math.ceil((260 * s + 40) / 16.0);
        forceAround(h, e, n);
        int ox = e.getBlockX(), oz = e.getBlockZ();
        ServerPlayer[] pl = new ServerPlayer[1];
        double limit = 0.4 + 1.6 * s, near = 2 + 3 * s;
        double[] worst = new double[2];
        // how the strand looks: biggest bend between two pieces, most and least stretched
        float[] look = {0f, 0f, 99f, 0f};
        Vec3[] prev = new Vec3[1];
        int[] st = {0, 0, -1};           // 0 before the grab, 1 carried, 2 on the crown, 3 done; ticks carried; strand
        long[] t0 = {0};
        h.runAfterDelay(20, () -> {
            // somewhere off to one side of him, on the ground
            double x = e.getX() + 75 * s + 3, z = e.getZ() + 30 * s + 1;
            pl[0] = player(h, new Vec3(x, e.groundAt(x, z), z));
            h.assertTrue(e.comeAndGetMe(pl[0]), "he won't come for the player");
            t0[0] = h.getTick();
        });
        h.onEachTick(() -> {
            ServerPlayer p = pl[0];
            if (p == null) return;
            if (st[2] >= 0 && e.moveNow() == Moves.GRAB) {
                float[] l = ropeLook(e, st[2]);
                look[0] = Math.max(look[0], l[0]); look[1] = Math.max(look[1], l[1]); look[2] = Math.min(look[2], l[1]); look[3] = Math.max(look[3], l[2]);
            }
            if (st[0] >= 2) return;
            boolean held = e.moves().grabbed() == p;
            boolean onCrown = e.rider() == p;
            Vec3 now = mid(p);
            if (st[0] == 0 && !held && !onCrown) {
                prev[0] = now;
                h.assertTrue(h.getTick() - t0[0] < 1400, "he never picked the player up");
                return;
            }
            if (st[0] == 0) { st[0] = 1; st[2] = e.moveArg(); }
            st[1]++;
            double step = now.distanceTo(prev[0]);
            worst[0] = Math.max(worst[0], step);
            h.assertTrue(step <= limit, String.format("tick %d of the carry: the player moved %.2f blocks in one tick (limit %.2f)", st[1], step, limit));
            if (held) {
                double gap = e.strandTipWorld(st[2]).distanceTo(now);
                worst[1] = Math.max(worst[1], gap);
                h.assertTrue(gap <= near, String.format("tick %d of the carry: the strand's end is %.2f blocks from the player (limit %.2f)", st[1], gap, near));
                h.assertTrue(p.getVehicle() instanceof net.jj.hollowbell.entity.Seat, "the player isn't held on a seat");
            } else h.assertTrue(onCrown, "the strand let go of the player " + st[1] + " ticks into the carry");
            // nothing of him inside them, now or anywhere on the way from last tick
            AABB box = sitting(p);
            Vec3 d = now.subtract(prev[0]);
            int k = Math.max(1, (int) Math.ceil(d.length() / (0.2 * Math.max(0.25, s))));
            for (int i = 0; i <= k; i++) {
                AABB b = box.move(d.scale(-(double) (k - i) / k));
                String part = e.partIn(b, st[2]);
                h.assertTrue(part == null, String.format("tick %d of the carry: the player is inside his %s at %s", st[1], part, b.getCenter()));
            }
            prev[0] = now;
            h.assertTrue(st[1] < 20 * 60, "the carry never finished");
            if (onCrown) {
                st[0] = 2;
                h.assertTrue(e.ridden() && p.getVehicle() instanceof net.jj.hollowbell.entity.Seat seat && seat.mode() == net.jj.hollowbell.entity.Seat.CROWN,
                        "not sitting on the crown seat");
                double off = p.getVehicle().position().distanceTo(e.crownWorld());
                h.assertTrue(off < 0.05, "the crown seat isn't on the crown: " + off);
                net.jj.hollowbell.HollowbellMod.LOG.info("carry at size {}: {} ticks, biggest step {} (limit {}), biggest gap to the strand's end {} (limit {}), nothing of him in the way",
                        s, st[1], String.format("%.3f", worst[0]), String.format("%.2f", limit), String.format("%.3f", worst[1]), String.format("%.2f", near));
            }
        });
        // once on, the strand goes back down and hangs again, and then he's theirs to drive
        h.succeedWhen(() -> {
            h.assertTrue(st[0] >= 2, "not on the crown yet");
            h.assertTrue(e.moveNow() == Moves.NONE, "the strand is still on its way back");
            h.assertTrue(e.rider() == pl[0], "fell off");
            float tipY = e.toModel(e.strandTipWorld(st[2])).y;
            h.assertTrue(tipY < e.rig.rimY, "the strand is still up over his rim: " + tipY);
            if (st[0] == 2) {
                st[0] = 3;
                net.jj.hollowbell.HollowbellMod.LOG.info("carry at size {}: the strand's biggest bend between two pieces {} degrees, stretched {} to {} times its length (up to {} of its 6 pieces drawn in at a time)",
                        s, String.format("%.0f", look[0]), String.format("%.2f", look[2]), String.format("%.2f", look[1]), (int) look[3]);
                // it reads as one smooth rope: no sharp kink between two pieces, and not pulled out much longer than it is
                h.assertTrue(look[0] <= 75f, "the strand bent " + look[0] + " degrees between two pieces");
                h.assertTrue(look[1] <= 1.25f && look[2] >= 0.6f, "the strand was stretched " + look[2] + " to " + look[1] + " times its length");
                e.dropRider();
                drop(pl[0]);
                release(h, e);
                unforceAround(h, ox, oz, n);
            }
        });
    }

    /**
     * Getting off (G): he sinks to the ground, a strand comes up over the dome, takes the rider off the crown and
     * carries them back down his side, and sets them on their feet beside him. The same checks as the pick-up,
     * every tick from the moment the strand has them to when they stand on the ground: steps no bigger than
     * 0.4 + 1.6 x size, the strand's end no more than 2 + 3 x size from their middle, nothing of him in them.
     */
    private static void setDownCase(GameTestHelper h, float s, int slot) {
        HollowbellEntity e = spawnAway(h, s, HollowbellEntity.CALM, slot);
        int n = (int) Math.ceil((260 * s + 40) / 16.0);
        forceAround(h, e, n);
        int ox = e.getBlockX(), oz = e.getBlockZ();
        ServerPlayer[] pl = new ServerPlayer[1];
        double limit = 0.4 + 1.6 * s, near = 2 + 3 * s;
        double[] worst = new double[2];
        float[] look = {0f, 0f, 99f, 0f};
        Vec3[] prev = new Vec3[1];
        int[] st = {0, 0, -1};            // 0 on the crown, 1 carried down, 2 on the ground, 3 done; ticks carried; strand
        h.runAfterDelay(20, () -> {
            pl[0] = player(h, e.position().add(40 * s + 5, 0, 0));
            h.assertTrue(e.possess(pl[0]), "couldn't get on him");
        });
        // up a way first, so he has to come down for it
        for (int i = 25; i < 85; i += 5) h.runAfterDelay(i, () -> e.drive(pl[0], 0f, 0f, 0f, 1));
        h.runAfterDelay(90, () -> {
            h.assertTrue(e.getY() > e.groundAt(e.getX(), e.getZ()) + 3 * s + 1, "he didn't go up");
            e.setMeDown(pl[0]);
            h.assertTrue(e.moves().settingDown(), "he isn't setting the player down");
        });
        h.onEachTick(() -> {
            ServerPlayer p = pl[0];
            if (p == null || h.getTick() < 90) return;
            if (st[2] >= 0 && e.moveNow() == Moves.GRAB) {
                float[] l = ropeLook(e, st[2]);
                look[0] = Math.max(look[0], l[0]); look[1] = Math.max(look[1], l[1]); look[2] = Math.min(look[2], l[1]); look[3] = Math.max(look[3], l[2]);
            }
            if (st[0] >= 2) return;
            boolean held = e.moves().grabbed() == p;
            Vec3 now = mid(p);
            if (st[0] == 0 && !held) {
                h.assertTrue(e.rider() == p, "fell off before the strand came");
                prev[0] = now;
                if (e.moveNow() == Moves.GRAB) st[2] = e.moveArg();
                h.assertTrue(h.getTick() < 90 + 1600, "the strand never came for the player");
                return;
            }
            if (st[0] == 0) st[0] = 1;
            st[1]++;
            double step = now.distanceTo(prev[0]);
            worst[0] = Math.max(worst[0], step);
            h.assertTrue(step <= limit, String.format("tick %d of the set-down: the player moved %.2f blocks in one tick (limit %.2f)", st[1], step, limit));
            if (held) {
                double gap = e.strandTipWorld(st[2]).distanceTo(now);
                worst[1] = Math.max(worst[1], gap);
                h.assertTrue(gap <= near, String.format("tick %d of the set-down: the strand's end is %.2f blocks from the player (limit %.2f)", st[1], gap, near));
            }
            AABB box = held ? sitting(p) : p.getBoundingBox();
            Vec3 d = now.subtract(prev[0]);
            int k = Math.max(1, (int) Math.ceil(d.length() / (0.2 * Math.max(0.25, s))));
            for (int i = 0; i <= k; i++) {
                AABB b = box.move(d.scale(-(double) (k - i) / k));
                String part = e.partIn(b, st[2]);
                h.assertTrue(part == null, String.format("tick %d of the set-down: the player is inside his %s at %s", st[1], part, b.getCenter()));
            }
            prev[0] = now;
            h.assertTrue(st[1] < 20 * 60, "the set-down never finished");
            if (!held) {
                // let go: standing on the ground beside him
                h.assertTrue(!p.isPassenger() && e.rider() == null, "not let go properly");
                double over = p.getY() - e.groundAt(p.getX(), p.getZ());
                h.assertTrue(over > -0.2 && over < 1.0, "set down " + over + " over the ground");
                st[0] = 2;
                net.jj.hollowbell.HollowbellMod.LOG.info("set down at size {}: {} ticks, biggest step {} (limit {}), biggest gap to the strand's end {} (limit {}), nothing of him in the way, put down {} over the ground",
                        s, st[1], String.format("%.3f", worst[0]), String.format("%.2f", limit), String.format("%.3f", worst[1]), String.format("%.2f", near), String.format("%.2f", over));
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(st[0] >= 2, "not down yet");
            h.assertTrue(e.moveNow() == Moves.NONE, "the strand is still busy");
            if (st[0] == 2) {
                st[0] = 3;
                net.jj.hollowbell.HollowbellMod.LOG.info("set down at size {}: the strand's biggest bend between two pieces {} degrees, stretched {} to {} times its length",
                        s, String.format("%.0f", look[0]), String.format("%.2f", look[2]), String.format("%.2f", look[1]));
                h.assertTrue(look[0] <= 75f, "the strand bent " + look[0] + " degrees between two pieces");
                h.assertTrue(look[1] <= 1.25f && look[2] >= 0.6f, "the strand was stretched " + look[2] + " to " + look[1] + " times its length");
                drop(pl[0]);
                release(h, e);
                unforceAround(h, ox, oz, n);
            }
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1400, batch = "setdown_small")
    public void setDownFromTheCrownSmall(GameTestHelper h) { setDownCase(h, 0.1f, 145); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1800, batch = "setdown_mid")
    public void setDownFromTheCrownMid(GameTestHelper h) { setDownCase(h, 0.3f, 146); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 2600, batch = "setdown_full")
    public void setDownFromTheCrownFullSize(GameTestHelper h) { setDownCase(h, 1.0f, 147); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200, batch = "carry_small")
    public void carriedUpOntoTheCrownSmall(GameTestHelper h) { carriedUp(h, 0.1f, 130); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1400, batch = "carry_mid")
    public void carriedUpOntoTheCrownMid(GameTestHelper h) { carriedUp(h, 0.3f, 131); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 2400, batch = "carry_full")
    public void carriedUpOntoTheCrownFullSize(GameTestHelper h) { carriedUp(h, 1.0f, 133); }

    /** the strand reaching out for you misses if you walk off, and pulls back if you're hurt; he tries again */
    private static void carryCalledOff(GameTestHelper h, boolean hurt, int slot) {
        float s = 0.1f;
        HollowbellEntity e = spawnAway(h, s, HollowbellEntity.CALM, slot);
        ServerPlayer[] pl = new ServerPlayer[1];
        int[] st = {0};
        h.runAfterDelay(20, () -> {
            double x = e.getX() + 6, z = e.getZ() + 3;
            pl[0] = player(h, new Vec3(x, e.groundAt(x, z), z));
            h.assertTrue(e.comeAndGetMe(pl[0]), "he won't come for the player");
        });
        h.onEachTick(() -> {
            ServerPlayer p = pl[0];
            if (p == null) return;
            if (st[0] == 0 && e.moves().carryingUp() && e.moves().t() == 12) {
                h.assertTrue(e.moves().grabbed() == null && !p.isPassenger(), "held before the strand got there");
                if (hurt) p.hurt(p.damageSources().generic(), 1f);
                else p.teleportTo(p.getX() + 6, p.getY(), p.getZ() + 2);
                st[0] = 1;
            } else if (st[0] == 1) {
                h.assertTrue(e.moveNow() != Moves.GRAB, "the strand kept reaching");
                h.assertTrue(!p.isPassenger() && e.moves().grabbed() == null && e.rider() == null, "it took hold anyway");
                h.assertTrue(e.comingForSomebody(), "he gave up on the player");
                st[0] = 2;
            } else if (st[0] == 2 && e.moves().carryingUp()) st[0] = 3;       // and he tries again
        });
        h.succeedWhen(() -> {
            h.assertTrue(st[0] == 3, "he didn't try again");
            e.stopFetch();
            drop(pl[0]);
            release(h, e);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "carry_walk_off")
    public void theCarryMissesIfYouWalkOff(GameTestHelper h) { carryCalledOff(h, false, 134); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "carry_hurt")
    public void theCarryPullsBackIfYouAreHurt(GameTestHelper h) { carryCalledOff(h, true, 135); }

    // ================================================================== the world features: his own one, his ground,
    // the cap, the finder, the armor, the ward, keeping to a circle, /giants and the recipes

    private static net.minecraft.commands.CommandSourceStack op(GameTestHelper h, Vec3 at) {
        return h.getLevel().getServer().createCommandSourceStack().withPosition(at).withLevel(h.getLevel()).withPermission(4).withSuppressedOutput();
    }

    private static void run(GameTestHelper h, Vec3 at, String cmd) {
        h.getLevel().getServer().getCommands().performPrefixedCommand(op(h, at), cmd);
    }

    /** a clean world for the tests that count him: nobody standing, nobody out of the world */
    private static void clearAll(GameTestHelper h) {
        var server = h.getLevel().getServer();
        for (var l : server.getAllLevels())
            for (HollowbellEntity e : new java.util.ArrayList<>(l.getEntities(ModEntities.HOLLOWBELL, x -> !x.isRemoved()))) e.discard();
        net.jj.hollowbell.world.Away.get(server).forgetAll();
    }

    private static void force(GameTestHelper h, int x, int z, int r, boolean on) {
        for (int cx = (x >> 4) - r; cx <= (x >> 4) + r; cx++) for (int cz = (z >> 4) - r; cz <= (z >> 4) + r; cz++) {
            h.getLevel().setChunkForced(cx, cz, on);
            if (on) h.getLevel().getChunk(cx, cz);
        }
    }

    private static String key(net.minecraft.network.chat.Component c) {
        return c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey() : c.getString();
    }

    private static Object[] args(net.minecraft.network.chat.Component c) {
        return c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getArgs() : new Object[0];
    }

    private static boolean parses(GameTestHelper h, String cmd, int perm) {
        var d = h.getLevel().getServer().getCommands().getDispatcher();
        var p = d.parse(cmd, h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()).withPermission(perm).withSuppressedOutput());
        return !p.getReader().canRead() && p.getExceptions().isEmpty();
    }

    // ------------------------------------------------------------------ the world's own one

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "natural_cmd")
    public void theNaturalCommandTurnsHimOnAndOff(GameTestHelper h) {
        boolean was = HollowbellConfig.V.oneInTheWorld;
        Vec3 at = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1)));
        run(h, at, "hollowbell natural off");
        h.assertTrue(!HollowbellConfig.V.oneInTheWorld, "natural off didn't turn it off");
        run(h, at, "hollowbell natural");
        run(h, at, "hollowbell natural on");
        h.assertTrue(HollowbellConfig.V.oneInTheWorld, "natural on didn't turn it on");
        for (String c : new String[]{"hollowbell natural", "hollowbell natural off", "hollowbell limit", "hollowbell limit 3", "hollowbell ward",
                "hollowbell ward on", "hollowbell ward off", "hollowbell ward minutes 5", "hollowbell ward rest 0", "hollowbell ward blocks 300",
                "hollowbell area", "hollowbell area off", "hollowbell area 10 20 64"}) {
            h.assertTrue(parses(h, c, 2), "/" + c + " doesn't parse with cheats on");
            h.assertTrue(!parses(h, c, 0), "/" + c + " works without cheats");
        }
        HollowbellConfig.V.oneInTheWorld = was;
        HollowbellConfig.save();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "natural_death")
    public void whenTheWorldsOwnOneDiesTheNextIsDue(GameTestHelper h) {
        clearAll(h);
        var w = net.jj.hollowbell.world.WorldOne.get(h.getLevel().getServer());
        w.clearForTests();
        boolean was = HollowbellConfig.V.oneInTheWorld;
        HollowbellConfig.V.oneInTheWorld = true;
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 200);
        h.runAfterDelay(20, () -> {
            e.markWorldOne();
            w.seen(e);
            h.assertTrue(w.aliveNow() && w.where() != null, "the world didn't note him");
            Vec3 fell = e.position();
            e.applyDamage(e.damageSources().generic(), e.healthMax() * 100f, -1, false);
            h.assertTrue(e.isDeadOrDying(), "he should be dying");
            h.assertTrue(!w.aliveNow(), "the world still thinks he is out there");
            BlockPos next = w.where();
            double d = Math.hypot(next.getX() - fell.x, next.getZ() - fell.z);
            h.assertTrue(d > 1000 && d < 40000, "the next one comes down " + (int) d + " blocks from where he fell");
            int days = w.daysLeft(h.getLevel());
            h.assertTrue(days == HollowbellConfig.V.worldRespawnDays, "the next one is due in " + days + " days");
            // and the finder says when, and which way
            ServerPlayer p = player(h, fell.add(0, 2, 0));
            var said = net.jj.hollowbell.item.FinderItem.answer(h.getLevel(), p);
            h.assertTrue(key(said).equals("message.hollowbell.finder_wait"), "the finder said " + key(said));
            h.assertTrue(((Number) args(said)[0]).intValue() == days, "the finder counts " + args(said)[0] + " days");
            drop(p);
            release(h, e);
            w.clearForTests();
            HollowbellConfig.V.oneInTheWorld = was;
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "natural_removed")
    public void removingHimStartsTheCountToTheNext(GameTestHelper h) {
        clearAll(h);
        var w = net.jj.hollowbell.world.WorldOne.get(h.getLevel().getServer());
        w.clearForTests();
        // a remove that can't reach him (asleep far off) changes nothing
        HollowbellEntity far = ModEntities.HOLLOWBELL.create(h.getLevel());
        far.moveTo(h.absolutePos(BlockPos.ZERO).getX() + 9000, 0, 0);
        w.adopt(far);
        BlockPos o = h.absolutePos(new BlockPos(1, 2, 1));
        run(h, Vec3.atCenterOf(o), "hollowbell remove");
        h.assertTrue(w.aliveNow() && w.isTheOne(far.getUUID()), "a remove that didn't reach him ended the world's own one");
        far.discard();
        // one that does reach him starts the count
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 214);
        h.runAfterDelay(20, () -> {
            w.clearForTests();
            w.adopt(e);
            run(h, Vec3.atCenterOf(o), "hollowbell remove");
            h.assertTrue(!w.aliveNow(), "removed, the world still thinks he is out there");
            h.assertTrue(w.daysLeft(h.getLevel()) == HollowbellConfig.V.worldRespawnDays, "the next one is due in " + w.daysLeft(h.getLevel()) + " days");
            w.clearForTests();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80, batch = "natural_adopts_away")
    public void theSpawnerTakesOnOneAlreadyOutThere(GameTestHelper h) {
        clearAll(h);
        var server = h.getLevel().getServer();
        var w = net.jj.hollowbell.world.WorldOne.get(server);
        w.clearForTests();
        boolean was = HollowbellConfig.V.oneInTheWorld;
        int wasMax = HollowbellConfig.V.maxHollowbells;
        HollowbellConfig.V.oneInTheWorld = true;
        HollowbellConfig.V.maxHollowbells = 1;
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.HUNTER, 215);
        h.runAfterDelay(20, () -> {
            var away = net.jj.hollowbell.world.Away.get(server);
            java.util.UUID id = e.getUUID();
            Vec3 at = e.position();
            h.assertTrue(e.stepAside(), "he should step out of the world");
            away.get(id).body.remove("BornAt");                    // one from before ages were kept
            ServerPlayer p = player(h, Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1))));
            w.tick(h.getLevel());
            h.assertTrue(away.count() == 1 && away.get(id) != null, "the one out of the world was forgotten");
            h.assertTrue(away.get(id).body.getBoolean("WorldOne") && w.isTheOne(id) && w.aliveNow(), "he wasn't taken on as the world's own");
            int standing = 0;
            for (var l : server.getAllLevels()) standing += l.getEntities(ModEntities.HOLLOWBELL, x -> !x.isRemoved()).size();
            h.assertTrue(standing == 0, "the spawner put a new one down anyway (" + standing + ")");
            // another one dying now (not the world's own) doesn't start the count
            HollowbellEntity other = spawnAway(h, S, HollowbellEntity.CALM, 216);
            other.applyDamage(other.damageSources().generic(), other.healthMax() * 100f, -1, false);
            h.assertTrue(w.aliveNow() && w.isTheOne(id), "another one dying ended the world's own one, out of the world");
            release(h, other);
            away.forget(id);
            drop(p);
            force(h, (int) at.x, (int) at.z, 4, false);
            w.clearForTests();
            HollowbellConfig.V.oneInTheWorld = was;
            HollowbellConfig.V.maxHollowbells = wasMax;
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "natural_copies")
    public void anOldCopyOrADyingOneIsNotTheWorldsOwn(GameTestHelper h) {
        clearAll(h);
        var server = h.getLevel().getServer();
        var w = net.jj.hollowbell.world.WorldOne.get(server);
        w.clearForTests();
        int wasMax = HollowbellConfig.V.maxHollowbells;
        HollowbellConfig.V.maxHollowbells = 1;
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 217);
        h.runAfterDelay(20, () -> {
            w.adopt(e);
            // an old copy of him, still marked, loads: it loses the mark, and nobody is pushed out
            CompoundTag tag = new CompoundTag();
            e.saveWithoutId(tag);
            HollowbellEntity copy = ModEntities.HOLLOWBELL.create(h.getLevel());
            copy.load(tag);
            copy.setUUID(java.util.UUID.randomUUID());
            copy.moveTo(e.getX() + 20, e.getY(), e.getZ());
            h.getLevel().addFreshEntity(copy);
            net.jj.hollowbell.world.WorldOne.joinedNow(copy, h.getLevel());
            h.assertTrue(!copy.isWorldOne() && w.isTheOne(e.getUUID()), "the old copy kept the mark");
            h.assertTrue(!e.isRemoved() && !copy.isRemoved(), "a copy loading pushed somebody out");
            copy.discard();
            // seen from the nether he writes nothing down about the overworld
            HollowbellEntity nether = ModEntities.HOLLOWBELL.create(server.getLevel(net.minecraft.world.level.Level.NETHER));
            nether.moveTo(e.getX() + 5000, 64, e.getZ() + 5000);
            BlockPos before = w.where();
            net.jj.hollowbell.world.WorldOne.get(server).clearForTests();
            w.noteSpot(before.getX(), before.getZ(), true, -1);
            w.adopt(nether);
            h.assertTrue(w.where().getX() == before.getX() && w.where().getZ() == before.getZ(), "nether spot written as the overworld spot: " + w.where());
            nether.discard();
            // he dies and is saved mid-fold: loading him again doesn't bring the world's own one back
            w.clearForTests();
            w.adopt(e);
            e.applyDamage(e.damageSources().generic(), e.healthMax() * 100f, -1, false);
            h.assertTrue(!w.aliveNow(), "his death didn't count");
            CompoundTag dying = new CompoundTag();
            e.saveWithoutId(dying);
            HollowbellEntity back = ModEntities.HOLLOWBELL.create(h.getLevel());
            back.load(dying);
            h.assertTrue(!back.isWorldOne(), "saved mid-fold he is still the world's own");
            net.jj.hollowbell.world.WorldOne.joinedNow(back, h.getLevel());
            w.seen(e);
            h.assertTrue(!w.aliveNow(), "reloading him mid-fold brought him back");
            back.discard();
            w.clearForTests();
            HollowbellConfig.V.maxHollowbells = wasMax;
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "upgrade")
    public void upgradingNeverThrowsAnybodyAway(GameTestHelper h) {
        clearAll(h);
        var server = h.getLevel().getServer();
        // the settings: an old file with no limit gets the new limit of 1; a new file set to 0 keeps 0
        var old = new HollowbellConfig.Values();
        old.configVersion = 4; old.maxHollowbells = 0;
        HollowbellConfig.migrate(old);
        h.assertTrue(old.maxHollowbells == 1, "an old file's limit became " + old.maxHollowbells);
        var mine = new HollowbellConfig.Values();
        mine.configVersion = 5; mine.maxHollowbells = 0;
        HollowbellConfig.migrate(mine);
        h.assertTrue(mine.maxHollowbells == 0, "a player's own 0 was changed to " + mine.maxHollowbells);
        int wasMax = HollowbellConfig.V.maxHollowbells;
        HollowbellConfig.V.maxHollowbells = 1;
        HollowbellEntity a = spawnAway(h, S, HollowbellEntity.CALM, 218);
        HollowbellEntity b = spawnAway(h, S, HollowbellEntity.HUNTER, 219);
        HollowbellEntity c = spawnAway(h, S, HollowbellEntity.GUARDIAN, 220);
        h.runAfterDelay(20, () -> {
            var away = net.jj.hollowbell.world.Away.get(server);
            h.assertTrue(c.stepAside(), "he should step out of the world");
            for (var r : away.all()) r.body.remove("BornAt");
            // three from an older version, all loading back with the limit now at 1
            java.util.List<HollowbellEntity> back = new java.util.ArrayList<>();
            for (HollowbellEntity x : new HollowbellEntity[]{a, b}) {
                CompoundTag tag = new CompoundTag();
                x.saveWithoutId(tag);
                tag.remove("BornAt");
                HollowbellEntity y = ModEntities.HOLLOWBELL.create(h.getLevel());
                y.load(tag);
                h.assertTrue(!y.freshSpawn() && y.bornAt() == 0, "an old one reads as fresh, born " + y.bornAt());
                y.setUUID(java.util.UUID.randomUUID());
                y.moveTo(x.getX() + 30, x.getY(), x.getZ());
                h.getLevel().addFreshEntity(y);
                net.jj.hollowbell.world.WorldOne.joinedNow(y, h.getLevel());
                back.add(y);
            }
            h.assertTrue(back.stream().noneMatch(HollowbellEntity::isRemoved) && !a.isRemoved() && !b.isRemoved(), "an old one loading pushed somebody out");
            h.assertTrue(away.count() == 1, "the one out of the world was thrown away on upgrade");
            for (var y : back) y.discard();
            for (var r : away.all()) away.forget(r.id);
            HollowbellConfig.V.maxHollowbells = wasMax;
            release(h, a);
            release(h, b);
            force(h, (int) c.getX(), (int) c.getZ(), 4, false);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80, batch = "cap_egg")
    public void aSpawnEggMeetsTheLimit(GameTestHelper h) {
        clearAll(h);
        int wasMax = HollowbellConfig.V.maxHollowbells;
        HollowbellConfig.V.maxHollowbells = 1;
        HollowbellEntity a = spawnAway(h, S, HollowbellEntity.CALM, 221);
        h.runAfterDelay(20, () -> {
            BlockPos at = BlockPos.containing(a.getX() + 30, a.getY(), a.getZ());
            // the real egg path: the egg's tag is merged into a fresh save of him
            HollowbellEntity egg = ModEntities.HOLLOWBELL.spawn(h.getLevel(), new net.minecraft.world.item.ItemStack(ModItems.SMALL_EGG), null, at,
                    net.minecraft.world.entity.MobSpawnType.SPAWN_EGG, true, false);
            h.assertTrue(egg != null, "the egg made nothing");
            h.assertTrue(egg.freshSpawn(), "an egg one reads as loaded from a save, so it slips past the limit");
            net.jj.hollowbell.world.WorldOne.joinedNow(egg, h.getLevel());
            h.assertTrue(a.isRemoved() && !egg.isRemoved(), "the egg one didn't push the older one out");
            egg.discard();
            HollowbellConfig.V.maxHollowbells = wasMax;
            release(h, a);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ the cap

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120, batch = "cap_fresh")
    public void pastTheLimitTheOldestMakesWay(GameTestHelper h) {
        clearAll(h);
        int was = HollowbellConfig.V.maxHollowbells;
        HollowbellConfig.V.maxHollowbells = 1;
        HollowbellEntity a = spawnAway(h, S, HollowbellEntity.CALM, 202);
        HollowbellEntity[] b = new HollowbellEntity[1];
        Vec3[] aAt = new Vec3[1];
        h.runAfterDelay(20, () -> {
            aAt[0] = a.position();
            b[0] = spawnAway(h, S, HollowbellEntity.HUNTER, 203);
            h.assertTrue(!net.jj.hollowbell.world.WorldOne.limitNow(b[0], h.getLevel()), "the new one was turned away");
            h.assertTrue(a.isRemoved(), "the old one is still there past the limit");
            h.assertTrue(!b[0].isRemoved(), "the new one went instead");
            // at the limit is fine: nobody more goes
            h.assertTrue(!net.jj.hollowbell.world.WorldOne.limitNow(null, h.getLevel()) && !b[0].isRemoved(), "at the limit, somebody still went");
        });
        h.runAfterDelay(40, () -> {
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(aAt[0], aAt[0]).inflate(40)).isEmpty(), "the one who made way dropped loot");
            HollowbellConfig.V.maxHollowbells = was;
            release(h, b[0]);
            force(h, (int) aAt[0].x, (int) aAt[0].z, 4, false);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120, batch = "cap_reload")
    public void aSavedOneLoadingIsNotANewArrival(GameTestHelper h) {
        clearAll(h);
        var server = h.getLevel().getServer();
        var w = net.jj.hollowbell.world.WorldOne.get(server);
        w.clearForTests();
        int was = HollowbellConfig.V.maxHollowbells;
        HollowbellConfig.V.maxHollowbells = 2;
        HollowbellEntity a = spawnAway(h, S, HollowbellEntity.CALM, 204);
        h.runAfterDelay(20, () -> {
            a.markWorldOne();
            a.bindTo(a.getX(), a.getZ(), 90);
            long born = a.bornAt();
            h.assertTrue(born >= 0 && a.freshSpawn(), "a fresh one: born " + born);
            CompoundTag tag = new CompoundTag();
            a.saveWithoutId(tag);
            HollowbellEntity b = ModEntities.HOLLOWBELL.create(h.getLevel());
            b.load(tag);
            h.assertTrue(!b.freshSpawn(), "read back from a save, he counts as a new arrival");
            h.assertTrue(b.bornAt() == born, "born " + b.bornAt() + ", want " + born);
            h.assertTrue(b.isWorldOne(), "being the world's own one was lost");
            h.assertTrue(b.bound() && b.boundRadius() == 90, "his circle was lost");
            b.discard();
            Vec3 aAt = a.position();
            // one out of the world and one standing, the limit at two: nobody goes
            h.assertTrue(a.stepAside(), "he should step out of the world");
            var away = net.jj.hollowbell.world.Away.get(server);
            HollowbellEntity c = spawnAway(h, S, HollowbellEntity.HUNTER, 205);
            h.assertTrue(!net.jj.hollowbell.world.WorldOne.limitNow(c, h.getLevel()), "turned away at the limit");
            h.assertTrue(away.count() == 1, "the one out of the world was thrown away at exactly the limit");
            // a third, past it: the oldest (the one out there) goes, and the newest takes over as the world's own
            HollowbellEntity d = spawnAway(h, S, HollowbellEntity.HUNTER, 206);
            net.jj.hollowbell.world.WorldOne.limitNow(d, h.getLevel());
            h.assertTrue(away.count() == 0, "the oldest, out of the world, should have gone");
            h.assertTrue(!c.isRemoved() && !d.isRemoved(), "the wrong one went");
            h.assertTrue(d.isWorldOne(), "the world's own one wasn't passed on to the newest");
            HollowbellConfig.V.maxHollowbells = was;
            w.clearForTests();
            release(h, c);
            release(h, d);
            force(h, (int) aAt.x, (int) aAt.z, 4, false);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "limit_cmd")
    public void theLimitCommandSetsIt(GameTestHelper h) {
        clearAll(h);
        int was = HollowbellConfig.V.maxHollowbells;
        Vec3 at = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1)));
        run(h, at, "hollowbell limit 3");
        h.assertTrue(HollowbellConfig.V.maxHollowbells == 3, "limit is " + HollowbellConfig.V.maxHollowbells);
        run(h, at, "hollowbell limit");
        run(h, at, "hollowbell limit 0");
        h.assertTrue(HollowbellConfig.V.maxHollowbells == 0, "limit is " + HollowbellConfig.V.maxHollowbells);
        HollowbellConfig.V.maxHollowbells = was;
        HollowbellConfig.save();
        h.succeed();
    }

    // ------------------------------------------------------------------ the finder

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "finder_here")
    public void theFinderPointsAtHim(GameTestHelper h) {
        clearAll(h);
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 207);
        ServerPlayer[] pl = new ServerPlayer[1];
        net.minecraft.world.item.ItemStack st = new net.minecraft.world.item.ItemStack(ModItems.FINDER);
        h.runAfterDelay(20, () -> {
            pl[0] = player(h, e.position().add(-200, 2, 0));
            var said = net.jj.hollowbell.item.FinderItem.answer(h.getLevel(), pl[0]);
            h.assertTrue(key(said).equals("message.hollowbell.finder"), "far off the finder said " + key(said));
            h.assertTrue("east".equals(args(said)[1]), "he is east, the finder says " + args(said)[1]);
            h.assertTrue(Math.abs(((Number) args(said)[0]).intValue() - 200) < 3, "200 blocks, the finder says " + args(said)[0]);
            pl[0].teleportTo(e.getX() + 10, e.getY(), e.getZ() - 40);
            said = net.jj.hollowbell.item.FinderItem.answer(h.getLevel(), pl[0]);
            h.assertTrue(key(said).equals("message.hollowbell.finder_close"), "close by the finder said " + key(said));
            h.assertTrue("south".equals(args(said)[1]), "he is south, the finder says " + args(said)[1]);
        });
        for (int t = 21; t <= 45; t++) h.runAfterDelay(t, () -> st.inventoryTick(h.getLevel(), pl[0], 0, false));
        h.runAfterDelay(50, () -> {
            h.assertTrue(st.hasFoil(), "the finder doesn't shine with him close");
            drop(pl[0]);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "finder_stored")
    public void theFinderKnowsWhereTheWorldKeepsHim(GameTestHelper h) {
        clearAll(h);
        var w = net.jj.hollowbell.world.WorldOne.get(h.getLevel().getServer());
        w.clearForTests();
        boolean was = HollowbellConfig.V.oneInTheWorld;
        HollowbellConfig.V.oneInTheWorld = true;
        BlockPos o = h.absolutePos(new BlockPos(1, 2, 1));
        ServerPlayer p = player(h, Vec3.atCenterOf(o));
        var said = net.jj.hollowbell.item.FinderItem.answer(h.getLevel(), p);
        h.assertTrue(key(said).equals("message.hollowbell.finder_none"), "with nothing picked the finder said " + key(said));
        // none out there, the next due in three days, 5000 blocks east
        w.noteSpot(o.getX() + 5000, o.getZ(), false, h.getLevel().getGameTime() + 3 * 24000L);
        said = net.jj.hollowbell.item.FinderItem.answer(h.getLevel(), p);
        h.assertTrue(key(said).equals("message.hollowbell.finder_wait"), "waiting, the finder said " + key(said));
        h.assertTrue(((Number) args(said)[0]).intValue() == 3, "3 days, the finder says " + args(said)[0]);
        h.assertTrue("far to the east".equals(args(said)[1]), "far to the east, the finder says " + args(said)[1]);
        // he is out there (not loaded): the world's note of him
        w.noteSpot(o.getX(), o.getZ() - 3000, true, -1);
        said = net.jj.hollowbell.item.FinderItem.answer(h.getLevel(), p);
        h.assertTrue(key(said).equals("message.hollowbell.finder_far"), "with him out there the finder said " + key(said));
        h.assertTrue("north".equals(args(said)[1]), "he is north, the finder says " + args(said)[1]);
        drop(p);
        w.clearForTests();
        HollowbellConfig.V.oneInTheWorld = was;
        h.succeed();
    }

    // ------------------------------------------------------------------ bell glass armor

    private static void wearBellGlass(ServerPlayer p) {
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(ModItems.BELL_HELMET));
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(ModItems.BELL_CHESTPLATE));
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new net.minecraft.world.item.ItemStack(ModItems.BELL_LEGGINGS));
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(ModItems.BELL_BOOTS));
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "armor_poison")
    public void bellGlassKeepsPoisonOff(GameTestHelper h) {
        ServerPlayer p = player(h, Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1))));
        wearBellGlass(p);
        h.assertTrue(net.jj.hollowbell.item.BellArmorItem.fullSet(p), "four pieces on and it isn't a full set");
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.POISON, 200, 1));
        float hp = p.getHealth();
        boolean bit = p.hurt(p.damageSources().magic(), 1f);           // poison's own bite: one point a tick
        h.assertTrue(!bit && p.getHealth() == hp, "poison's bite got through the glass");
        h.assertTrue(!p.hasEffect(MobEffects.POISON), "the poison stayed on");
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.POISON, 200, 1));
        net.jj.hollowbell.item.BellArmorItem.abilities(p);
        h.assertTrue(!p.hasEffect(MobEffects.POISON), "the set didn't take the poison off");
        // other magic still lands: a potion of harming hurts through the glass
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.POISON, 200, 1));
        float hp2 = p.getHealth();
        p.invulnerableTime = 0;
        h.assertTrue(p.hurt(p.damageSources().magic(), 6f) && p.getHealth() < hp2, "harming didn't hurt through the glass");
        p.removeEffect(MobEffects.POISON);
        // three pieces is not the set
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.POISON, 200, 1));
        net.jj.hollowbell.item.BellArmorItem.abilities(p);
        h.assertTrue(p.hasEffect(MobEffects.POISON), "three pieces kept poison off");
        drop(p);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "armor_fall")
    public void bellGlassCatchesAFastFall(GameTestHelper h) {
        Vec3 at = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1)));
        ServerPlayer p = player(h, at);
        ServerPlayer q = player(h, at.add(1, 0, 0));
        wearBellGlass(p);
        p.fallDistance = 12f;
        net.jj.hollowbell.item.BellArmorItem.abilities(p);
        h.assertTrue(p.hasEffect(MobEffects.SLOW_FALLING), "falling fast in the full set, no slow falling");
        h.assertTrue(p.fallDistance < 1f, "the fall so far still counts: " + p.fallDistance);
        q.fallDistance = 12f;
        net.jj.hollowbell.item.BellArmorItem.abilities(q);
        h.assertTrue(!q.hasEffect(MobEffects.SLOW_FALLING), "no armor, and slow falling anyway");
        // each piece says what the set does, in gold
        java.util.List<net.minecraft.network.chat.Component> tip = new java.util.ArrayList<>();
        for (var item : new net.minecraft.world.item.Item[]{ModItems.BELL_HELMET, ModItems.BELL_CHESTPLATE, ModItems.BELL_LEGGINGS, ModItems.BELL_BOOTS}) {
            tip.clear();
            item.appendHoverText(new net.minecraft.world.item.ItemStack(item), net.minecraft.world.item.Item.TooltipContext.EMPTY, tip,
                    net.minecraft.world.item.TooltipFlag.NORMAL);
            h.assertTrue(tip.size() >= 2 && key(tip.get(0)).equals("item.hollowbell.bell_glass.tip")
                    && tip.get(0).getStyle().getColor() == net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.GOLD),
                    "no gold set line on " + item);
        }
        drop(p);
        drop(q);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "lang_keys")
    public void everyNewLineHasItsWords(GameTestHelper h) {
        try (var in = HollowbellGameTests.class.getResourceAsStream("/assets/hollowbell/lang/en_us.json")) {
            h.assertTrue(in != null, "no lang file");
            var lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (String k : new String[]{"item.hollowbell.hollowbell_finder", "item.hollowbell.bell_glass.tip", "item.hollowbell.bell_glass.tip2",
                    "biome.hollowbell.bell_hollows", "message.hollowbell.finder", "message.hollowbell.finder_far", "message.hollowbell.finder_close",
                    "message.hollowbell.finder_none", "message.hollowbell.finder_wrong_world", "message.hollowbell.finder_wait",
                    "message.hollowbell.finder_soon", "message.hollowbell.made_way", "message.hollowbell.ward_refused", "message.hollowbell.ward_broken",
                    "message.hollowbell.codex_bound", "message.hollowbell.codex_roam", "codex.hollowbell.keep_here", "codex.hollowbell.keep_here_tip",
                    "codex.hollowbell.let_roam"})
                h.assertTrue(lang.has(k), "no words for " + k);
        } catch (java.io.IOException ex) {
            h.fail("couldn't read the lang file: " + ex);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ the crown holding him off

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "ward")
    public void aWokenCrownHoldsHimOff(GameTestHelper h) {
        clearAll(h);
        var l = h.getLevel();
        var w = net.jj.hollowbell.world.WorldOne.get(l.getServer());
        w.clearForTests();
        int wasBlocks = HollowbellConfig.V.wardBlocks;
        HollowbellConfig.V.wardBlocks = 48;
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 208);
        ServerPlayer[] pl = new ServerPlayer[1];
        BlockPos[] crown = new BlockPos[2];
        double[] d0 = new double[1];
        h.runAfterDelay(20, () -> {
            Vec3 at = e.position();
            int cx = (int) Math.floor(at.x) + 100, cz = (int) Math.floor(at.z);
            force(h, cx, cz, 2, true);
            int gy = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, cx, cz);
            crown[0] = new BlockPos(cx, gy, cz);
            l.setBlockAndUpdate(crown[0], net.jj.hollowbell.ModBlocks.CROWN.defaultBlockState());
            run(h, Vec3.atCenterOf(crown[0].above()), "hollowbell ward on");
            h.assertTrue(w.warding(l), "the crown didn't wake");
            h.assertTrue(e.warded(cx, cz) && !e.warded(at.x, at.z), "the circle is in the wrong place");
            // told to go right to it, he won't
            e.setGoal(new Vec3(cx + 0.5, gy, cz + 0.5));
            // a pig by the crown is out of his reach
            Pig pg = pig(h, new Vec3(cx + 2.5, gy, cz + 0.5));
            h.assertTrue(!e.fairGame(pg), "he can still go after something by the crown");
            pg.discard();
            pl[0] = player(h, at.add(5, 0, 5));
            pl[0].getInventory().add(new net.minecraft.world.item.ItemStack(ModItems.CODEX));
        });
        h.runAfterDelay(23, () -> {
            h.assertTrue(e.goal() == null, "he still means to go into the circle: " + e.goal());
            // the book can't send him in there either
            net.jj.hollowbell.net.CodexOrders.handle(pl[0], new net.jj.hollowbell.net.CodexPayload(
                    net.jj.hollowbell.net.CodexPayload.GO_TO_XZ, 0, crown[0].getX() + 0.5, crown[0].getZ() + 0.5));
            h.assertTrue(e.goal() == null, "the book sent him into the circle");
            // wake a crown right beside him instead: he makes for the edge
            run(h, e.position(), "hollowbell ward off");
            BlockPos near = BlockPos.containing(e.getX() + 12, e.groundAt(e.getX() + 12, e.getZ()), e.getZ());
            crown[1] = near;
            l.setBlockAndUpdate(near, net.jj.hollowbell.ModBlocks.CROWN.defaultBlockState());
            run(h, Vec3.atCenterOf(near.above()), "hollowbell ward on");
            h.assertTrue(w.warding(l) && e.warded(e.getX(), e.getZ()), "the second crown didn't wake round him");
            d0[0] = Math.hypot(e.getX() - near.getX(), e.getZ() - near.getZ());
        });
        h.runAfterDelay(300, () -> {
            double d1 = Math.hypot(e.getX() - crown[1].getX(), e.getZ() - crown[1].getZ());
            h.assertTrue(d1 > d0[0] + 1, "inside the circle he didn't make for the edge: " + d0[0] + " -> " + d1);
            // taking the crown up ends it
            l.setBlockAndUpdate(crown[1], net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            h.assertTrue(!w.warding(l), "the ward kept going with the crown taken up");
            l.setBlockAndUpdate(crown[0], net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            force(h, crown[0].getX(), crown[0].getZ(), 2, false);
            HollowbellConfig.V.wardBlocks = wasBlocks;
            w.clearForTests();
            drop(pl[0]);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "ward_zero")
    public void wardBlocksZeroIsNoWard(GameTestHelper h) {
        var l = h.getLevel();
        var w = net.jj.hollowbell.world.WorldOne.get(l.getServer());
        w.clearForTests();
        int was = HollowbellConfig.V.wardBlocks;
        BlockPos o = h.absolutePos(new BlockPos(1, 2, 1));
        w.startWard(l, o, 2000, 0);
        HollowbellConfig.V.wardBlocks = 100;
        h.assertTrue(w.warded(l, o.getX() + 10, o.getZ()), "the ward isn't holding");
        HollowbellConfig.V.wardBlocks = 0;
        h.assertTrue(!w.warded(l, o.getX() + 10, o.getZ()), "ward blocks 0 still holds him off");
        HollowbellConfig.V.wardBlocks = was;
        w.clearForTests();
        h.succeed();
    }

    // ------------------------------------------------------------------ kept to a circle

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800, batch = "area")
    public void keptToACircleHeStaysInIt(GameTestHelper h) {
        clearAll(h);
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 209);
        Vec3[] c = new Vec3[1];
        double[] d0 = new double[1];
        h.runAfterDelay(20, () -> {
            // the circle's middle 60 blocks off, 32 across: he is outside it, and walks back in
            c[0] = e.position().add(-60, 0, 0);
            e.bindTo(c[0].x, c[0].z, 32);
            h.assertTrue(e.bound() && e.boundRadius() == 32, "not bound");
            d0[0] = Math.hypot(e.getX() - c[0].x, e.getZ() - c[0].z);
            // told to go way past the edge, the place he aims for is inside
            e.setGoal(e.position().add(300, 0, 0));
            h.assertTrue(e.goal() != null && Math.hypot(e.goal().x - c[0].x, e.goal().z - c[0].z) <= 32, "sent past the edge: " + e.goal());
            e.setGoal(null);
        });
        h.runAfterDelay(320, () -> {
            double d1 = Math.hypot(e.getX() - c[0].x, e.getZ() - c[0].z);
            h.assertTrue(d1 < d0[0] - 1, "outside his circle he didn't head back in: " + d0[0] + " -> " + d1);
            // freed, he goes where he is sent
            e.unbind();
            Vec3 far = e.position().add(300, 0, 0);
            e.setGoal(far);
            h.assertTrue(e.goal() != null && Math.abs(e.goal().x - far.x) < 1, "freed, he's still held to the circle");
            // the command binds the nearest one, and saving keeps it
            run(h, e.position(), "hollowbell area " + (int) e.getX() + " " + (int) e.getZ() + " 64");
            h.assertTrue(e.bound() && e.boundRadius() == 64, "/hollowbell area didn't bind him");
            CompoundTag tag = new CompoundTag();
            e.saveWithoutId(tag);
            HollowbellEntity b = ModEntities.HOLLOWBELL.create(h.getLevel());
            b.load(tag);
            h.assertTrue(b.bound() && b.boundRadius() == 64, "his circle didn't survive a save");
            b.discard();
            run(h, e.position(), "hollowbell area off");
            h.assertTrue(!e.bound(), "/hollowbell area off didn't free him");
            // the book: keep to here, then let him roam
            ServerPlayer p = player(h, e.position().add(4, 0, 4));
            p.getInventory().add(new net.minecraft.world.item.ItemStack(ModItems.CODEX));
            net.jj.hollowbell.net.CodexOrders.handle(p, new net.jj.hollowbell.net.CodexPayload(net.jj.hollowbell.net.CodexPayload.BIND_HERE, 75));
            h.assertTrue(e.bound() && e.boundRadius() == 75 && Math.hypot(e.boundCentre().x - p.getX(), e.boundCentre().z - p.getZ()) < 1,
                    "the book didn't keep him to the player's spot");
            net.jj.hollowbell.net.CodexOrders.handle(p, new net.jj.hollowbell.net.CodexPayload(net.jj.hollowbell.net.CodexPayload.FREE_ROAM));
            h.assertTrue(!e.bound(), "the book didn't let him roam");
            // out of the world, a bound one's trip keeps to his circle too
            e.bindTo(e.getX(), e.getZ(), 100);
            java.util.UUID id = e.getUUID();
            Vec3 mid = e.position();
            h.assertTrue(e.stepAside(), "he should step out of the world");
            var a = net.jj.hollowbell.world.Away.get(h.getLevel().getServer());
            a.send(h.getLevel(), id, mid.add(5000, 0, 0));
            var r = a.get(id);
            h.assertTrue(r != null && Math.hypot(r.toX - mid.x, r.toZ - mid.z) <= 100.5, "out of the world he's sent past his circle");
            a.forget(id);
            drop(p);
            release(h, e);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ /giants

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "giants_cmd")
    public void theGiantsCommandReachesHim(GameTestHelper h) {
        clearAll(h);
        var server = h.getLevel().getServer();
        h.assertTrue(net.jj.hollowbell.command.GiantsCommand.elected(), "on his own, Hollowbell should run /giants");
        int was = HollowbellConfig.V.maxHollowbells;
        run(h, Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1))), "giants limit 2");
        h.assertTrue(HollowbellConfig.V.maxHollowbells == 2, "/giants limit 2 left the limit at " + HollowbellConfig.V.maxHollowbells);
        var lines = net.jj.hollowbell.command.GiantsCommand.ask(server, "status", "");
        h.assertTrue(lines.stream().anyMatch(s -> s.startsWith("Hollowbell: ")), "no answer from Hollowbell: " + lines);
        h.assertTrue(net.jj.hollowbell.GiantsBridge.giants(server, "nonsense", "") == null, "an unknown action should give null");
        h.assertTrue(parses(h, "giants where", 0), "/giants where needs cheats");
        h.assertTrue(!parses(h, "giants limit 2", 0), "/giants limit works without cheats");
        h.assertTrue(parses(h, "giants natural off", 2) && parses(h, "giants volume 1", 2), "/giants doesn't parse with cheats on");
        HollowbellConfig.V.maxHollowbells = was;
        HollowbellConfig.save();
        h.succeed();
    }

    // ------------------------------------------------------------------ every recipe the mod ships

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "recipes")
    public void everyRecipeIsThere(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var rm = server.getRecipeManager();
        String[] ids = {"hollowbell_codex", "bell_glass_helmet", "bell_glass_chestplate", "bell_glass_leggings", "bell_glass_boots",
                "hollowbell_finder", "pod_poison"};
        for (String id : ids) {
            var r = rm.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("hollowbell", id));
            h.assertTrue(r.isPresent(), "the recipe " + id + " is missing (a broken file?)");
        }
        long ours = rm.getRecipes().stream().filter(r -> r.id().getNamespace().equals("hollowbell")).count();
        h.assertTrue(ours == ids.length, "the mod ships " + ours + " recipes, the test knows " + ids.length);
        var finder = rm.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("hollowbell", "hollowbell_finder")).get();
        h.assertTrue(finder.value().getResultItem(server.registryAccess()).is(ModItems.FINDER), "the finder recipe makes something else");
        var codex = rm.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("hollowbell", "hollowbell_codex")).get();
        h.assertTrue(codex.value().getResultItem(server.registryAccess()).is(ModItems.CODEX), "the codex recipe makes something else");
        h.succeed();
    }

    // ------------------------------------------------------------------ his ground, the Bell Hollows

    /**
     * A spot far off for the ground tests, its chunks loaded. The test world is a normal one with a new seed each
     * time, so the spot walks along until the generator says the land round it is dry (it never loads to look).
     */
    private static BlockPos groundSpot(GameTestHelper h, int slot) {
        var l = h.getLevel();
        var src = l.getChunkSource();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        int bx = ((o.getX() + 20000 + slot * 400) >> 4 << 4) + 8, bz = ((o.getZ() + 5000) >> 4 << 4) + 8;
        int x = bx, z = bz;
        search:
        for (int tries = 0; tries < 80; tries++) {
            int tx = bx + (tries % 2 == 0 ? 0 : 160), tz = bz + (tries / 2) * 176;
            for (int dx = -40; dx <= 40; dx += 20) for (int dz = -40; dz <= 40; dz += 20) {
                int g = src.getGenerator().getBaseHeight(tx + dx, tz + dz, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, l, src.randomState());
                if (g <= l.getSeaLevel() + 3) continue search;
            }
            x = tx; z = tz;
            break;
        }
        force(h, x, z, 1, true);
        return new BlockPos(x, 0, z);
    }

    private static int top(GameTestHelper h, int x, int z) {
        return h.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1;
    }

    private static boolean hollowsAt(GameTestHelper h, int x, int y, int z) {
        var ch = h.getLevel().getChunkAt(new BlockPos(x, y, z));
        return ch.getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(x), net.minecraft.core.QuartPos.fromBlock(y),
                net.minecraft.core.QuartPos.fromBlock(z)).is(net.jj.hollowbell.world.HomeGround.BELL_HOLLOWS);
    }

    /** the test world has villages: the ground tests want a chunk with no building in it */
    private static net.minecraft.world.level.chunk.LevelChunk plainChunk(GameTestHelper h, BlockPos at) {
        var ch = h.getLevel().getChunkAt(at);
        ch.setAllReferences(new java.util.HashMap<>());
        ch.setInhabitedTime(0);
        return ch;
    }

    private static boolean ours(net.minecraft.world.level.block.state.BlockState s) {
        return net.jj.hollowbell.world.HomeGround.isOurs(s);
    }

    /** his ground turned, but not grass: plainly his */
    private static boolean plainlyOurs(net.minecraft.world.level.block.state.BlockState s) {
        return ours(s) && !s.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK) && s.getFluidState().isEmpty();
    }

    /** claims the ground 100 blocks west of the spot: the spot's chunk lies in the pale core, outside the den */
    private static void claimNear(GameTestHelper h, net.jj.hollowbell.world.WorldOne w, BlockPos c) {
        w.claimHome(h.getLevel(), c.getX() - 100, c.getZ());
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "ground_trees")
    public void hisGroundTakesTreesButNotCabins(GameTestHelper h) {
        var l = h.getLevel();
        var w = net.jj.hollowbell.world.WorldOne.get(l.getServer());
        w.clearForTests();
        BlockPos c = groundSpot(h, 222);
        claimNear(h, w, c);
        var chunk = plainChunk(h, c);
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        int g = top(h, x0 + 5, z0 + 5);                                  // the ground
        var LOG = net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState();
        var PLANK = net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState();
        var LEAF = net.minecraft.world.level.block.Blocks.OAK_LEAVES.defaultBlockState();
        int floorY = top(h, x0 + 4, z0 + 4);
        var floor = l.getBlockState(new BlockPos(x0 + 4, floorY, z0 + 4));
        java.util.List<BlockPos> cabin = new java.util.ArrayList<>();
        // a log cabin: log walls 3 high round a 5x5, a plank roof over it
        for (int dx = 2; dx <= 6; dx++) for (int dz = 2; dz <= 6; dz++) {
            boolean wall = dx == 2 || dx == 6 || dz == 2 || dz == 6;
            if (wall) for (int y = 1; y <= 3; y++) { BlockPos p = new BlockPos(x0 + dx, g + y, z0 + dz); l.setBlock(p, LOG, 2); cabin.add(p); }
            BlockPos r = new BlockPos(x0 + dx, g + 4, z0 + dz);
            l.setBlock(r, PLANK, 2);
            cabin.add(r);
        }
        // a tree: a trunk five high, a leaf cap three across on top
        int tx = x0 + 11, tz = z0 + 11;
        int tg = top(h, tx, tz);
        for (int y = 1; y <= 5; y++) l.setBlock(new BlockPos(tx, tg + y, tz), LOG, 2);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) l.setBlock(new BlockPos(tx + dx, tg + 6, tz + dz), LEAF, 2);
        // placed leaves on a post are somebody's: they stay
        BlockPos post = new BlockPos(x0 + 13, top(h, x0 + 13, z0 + 3) + 1, z0 + 3);
        l.setBlock(post, net.minecraft.world.level.block.Blocks.OAK_FENCE.defaultBlockState(), 2);
        l.setBlock(post.above(), LEAF.setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), 2);
        net.jj.hollowbell.world.HomeGround.paint(l, w, chunk);
        for (BlockPos p : cabin) h.assertTrue(!l.getBlockState(p).isAir(), "the cabin lost a block at " + p);
        h.assertTrue(l.getBlockState(new BlockPos(x0 + 4, floorY, z0 + 4)) == floor, "the floor under the cabin roof was turned");
        for (int y = 1; y <= 6; y++) {
            var st = l.getBlockState(new BlockPos(tx, tg + y, tz));
            h.assertTrue(!st.is(net.minecraft.tags.BlockTags.LOGS) && !st.is(net.minecraft.tags.BlockTags.LEAVES), "the tree is still there at +" + y);
        }
        h.assertTrue(!l.getBlockState(new BlockPos(tx + 1, tg + 6, tz)).is(net.minecraft.tags.BlockTags.LEAVES), "the tree's leaves are still there");
        h.assertTrue(ours(l.getBlockState(new BlockPos(tx, top(h, tx, tz), tz))), "the ground under the tree wasn't turned");
        h.assertTrue(l.getBlockState(post.above()).is(net.minecraft.tags.BlockTags.LEAVES), "placed leaves were cleared");
        for (BlockPos p : cabin) l.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        l.setBlock(post, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        l.setBlock(post.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        w.clearForTests();
        force(h, c.getX(), c.getZ(), 1, false);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "ground_lived")
    public void groundPeopleLiveOnIsLeftAlone(GameTestHelper h) {
        var l = h.getLevel();
        var w = net.jj.hollowbell.world.WorldOne.get(l.getServer());
        w.clearForTests();
        BlockPos c = groundSpot(h, 223);
        claimNear(h, w, c);
        var chunk = plainChunk(h, c);
        chunk.setInhabitedTime(5000);                                   // players have spent a few minutes here
        int ty = top(h, c.getX(), c.getZ());
        var was = l.getBlockState(new BlockPos(c.getX(), ty, c.getZ()));
        net.jj.hollowbell.world.HomeGround.paint(l, w, chunk);
        h.assertTrue(l.getBlockState(new BlockPos(c.getX(), ty, c.getZ())) == was, "a chunk people live in was turned");
        h.assertTrue(top(h, c.getX(), c.getZ()) == ty, "a chunk people live in was reshaped");
        h.assertTrue(!hollowsAt(h, c.getX(), ty, c.getZ()), "a chunk people live in became the Bell Hollows");
        h.assertTrue(w.paintedAlready(chunk.getPos().toLong()), "it will be looked at again and again");
        w.clearForTests();
        force(h, c.getX(), c.getZ(), 1, false);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "ground_paint")
    public void hisGroundTurnsAChunk(GameTestHelper h) {
        var l = h.getLevel();
        var w = net.jj.hollowbell.world.WorldOne.get(l.getServer());
        w.clearForTests();
        BlockPos c = groundSpot(h, 210);
        h.assertTrue(!hollowsAt(h, c.getX(), top(h, c.getX(), c.getZ()), c.getZ()), "the Bell Hollows before anything was claimed");
        claimNear(h, w, c);
        h.assertTrue(w.homeRadius() == net.jj.hollowbell.world.WorldOne.configRadius() && w.homeRadius() >= 900, "a new ground is " + w.homeRadius() + " across");
        var chunk = plainChunk(h, c);
        net.jj.hollowbell.world.HomeGround.paint(l, w, chunk);
        h.assertTrue(w.paintedAlready(chunk.getPos().toLong()), "the chunk isn't noted as done");
        int ours = 0, plain = 0;
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int x = chunk.getPos().getMinBlockX() + dx, z = chunk.getPos().getMinBlockZ() + dz;
            var st = l.getBlockState(new BlockPos(x, top(h, x, z), z));
            if (ours(st)) ours++;
            if (plainlyOurs(st)) plain++;
        }
        h.assertTrue(ours >= 230, "only " + ours + " of 256 columns are his ground on top");
        h.assertTrue(plain >= 100, "only " + plain + " of 256 columns are plainly his (not grass)");
        int ty = top(h, c.getX(), c.getZ());
        h.assertTrue(hollowsAt(h, c.getX(), ty, c.getZ()), "the biome didn't change to the Bell Hollows");
        h.assertTrue(hollowsAt(h, c.getX(), ty + 40, c.getZ()), "the air over his ground isn't the Bell Hollows");
        w.clearForTests();
        force(h, c.getX(), c.getZ(), 1, false);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "ground_again")
    public void turningAChunkTwiceDoesNothing(GameTestHelper h) {
        var l = h.getLevel();
        var w = net.jj.hollowbell.world.WorldOne.get(l.getServer());
        w.clearForTests();
        BlockPos c = groundSpot(h, 211);
        claimNear(h, w, c);
        var chunk = plainChunk(h, c);
        net.jj.hollowbell.world.HomeGround.paint(l, w, chunk);
        BlockPos t = new BlockPos(c.getX(), top(h, c.getX(), c.getZ()), c.getZ());
        var was = l.getBlockState(t);
        l.setBlockAndUpdate(t, net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState());
        net.jj.hollowbell.world.HomeGround.paint(l, w, chunk);
        h.assertTrue(l.getBlockState(t).is(net.minecraft.world.level.block.Blocks.OAK_PLANKS), "the chunk was turned a second time");
        l.setBlockAndUpdate(t, was);
        w.clearForTests();
        force(h, c.getX(), c.getZ(), 1, false);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "ground_outside")
    public void groundOutsideHisIsLeftAlone(GameTestHelper h) {
        var l = h.getLevel();
        var w = net.jj.hollowbell.world.WorldOne.get(l.getServer());
        w.clearForTests();
        BlockPos c = groundSpot(h, 212);
        w.claimHome(l, c.getX(), c.getZ());
        BlockPos far = new BlockPos(c.getX(), 0, c.getZ() + w.homeRadius() + 40);
        force(h, far.getX(), far.getZ(), 1, true);
        var chunk = plainChunk(h, far);
        java.util.List<net.minecraft.world.level.block.state.BlockState> before = new java.util.ArrayList<>();
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int x = chunk.getPos().getMinBlockX() + dx, z = chunk.getPos().getMinBlockZ() + dz;
            before.add(l.getBlockState(new BlockPos(x, top(h, x, z), z)));
        }
        net.jj.hollowbell.world.HomeGround.paint(l, w, chunk);
        int i = 0;
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int x = chunk.getPos().getMinBlockX() + dx, z = chunk.getPos().getMinBlockZ() + dz;
            h.assertTrue(l.getBlockState(new BlockPos(x, top(h, x, z), z)) == before.get(i++), "a block outside his ground was changed");
        }
        h.assertTrue(!hollowsAt(h, far.getX(), top(h, far.getX(), far.getZ()), far.getZ()), "the biome outside his ground changed");
        w.clearForTests();
        force(h, c.getX(), c.getZ(), 1, false);
        force(h, far.getX(), far.getZ(), 1, false);
        h.succeed();
    }

    /** every block of two chunks side by side, from a little under the ground to well over it */
    private static java.util.List<net.minecraft.world.level.block.state.BlockState> grab(GameTestHelper h, int x0, int z0, int lo, int hi) {
        var out = new java.util.ArrayList<net.minecraft.world.level.block.state.BlockState>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = x0; x < x0 + 32; x++) for (int z = z0; z < z0 + 16; z++) for (int y = lo; y <= hi; y++)
            out.add(h.getLevel().getBlockState(m.set(x, y, z)));
        return out;
    }

    private static void putBack(GameTestHelper h, int x0, int z0, int lo, int hi, java.util.List<net.minecraft.world.level.block.state.BlockState> was) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int k = 0;
        for (int x = x0; x < x0 + 32; x++) for (int z = z0; z < z0 + 16; z++) for (int y = lo; y <= hi; y++)
            h.getLevel().setBlock(m.set(x, y, z), was.get(k++), 2 | 16);
    }

    /** the glass of the bell and the froglight of its crown */
    private static boolean bell(net.minecraft.world.level.block.state.BlockState s) {
        return s.is(net.minecraft.world.level.block.Blocks.LIME_STAINED_GLASS) || s.is(net.minecraft.world.level.block.Blocks.WHITE_STAINED_GLASS)
                || s.is(net.minecraft.world.level.block.Blocks.GREEN_STAINED_GLASS) || s.is(net.minecraft.world.level.block.Blocks.LIGHT_GRAY_STAINED_GLASS)
                || s.is(net.minecraft.world.level.block.Blocks.PEARLESCENT_FROGLIGHT);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "ground_border")
    public void bigFeaturesComeOutWholeInAnyOrder(GameTestHelper h) {
        var l = h.getLevel();
        var w = net.jj.hollowbell.world.WorldOne.get(l.getServer());
        w.clearForTests();
        BlockPos c = groundSpot(h, 224);
        long seed = 0x5EED0001L;
        // the den's glass bell is fifty across: it straddles the border between this chunk and the next one east
        w.claimHome(l, c.getX() + 8, c.getZ(), seed);
        var a = plainChunk(h, c);
        var b = plainChunk(h, c.offset(16, 0, 0));
        int x0 = a.getPos().getMinBlockX(), z0 = a.getPos().getMinBlockZ();
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int x = x0; x < x0 + 32; x++) for (int z = z0; z < z0 + 16; z++) { lo = Math.min(lo, top(h, x, z)); hi = Math.max(hi, top(h, x, z)); }
        lo -= 12; hi += 40;
        var before = grab(h, x0, z0, lo, hi);
        net.jj.hollowbell.world.HomeGround.paint(l, w, a);
        net.jj.hollowbell.world.HomeGround.paint(l, w, b);
        var first = grab(h, x0, z0, lo, hi);
        // the bell really does cross the border: glass or calcite over the ground on both sides of it
        int east = 0, west = 0;
        for (int z = z0; z < z0 + 16; z++) for (int y = lo; y <= hi; y++) {
            if (bell(l.getBlockState(new BlockPos(x0 + 15, y, z)))) west++;
            if (bell(l.getBlockState(new BlockPos(x0 + 16, y, z)))) east++;
        }
        if (west <= 3 || east <= 3) {
            var o = net.jj.hollowbell.world.HomeGround.lastOut;
            int pc = 0, an = 0, maxY = Integer.MIN_VALUE;
            if (o != null) for (int i = 0; i < 256; i++) { if (o.paint[i]) pc++; an += o.an[i]; for (int k = 0; k < o.an[i]; k++) maxY = Math.max(maxY, o.ay[i][k]); }
            h.fail("the bell doesn't cross the chunk border (" + west + " / " + east + "); last chunk: " + pc + " painted, " + an
                    + " set above, highest " + maxY + ", looked from " + lo + " to " + hi + ", den at " + net.jj.hollowbell.world.HomeGround.plan(l, w).denY());
        }
        // the same ground again, the chunks turned the other way round
        putBack(h, x0, z0, lo, hi, before);
        w.clearForTests();
        w.claimHome(l, c.getX() + 8, c.getZ(), seed);
        plainChunk(h, c); plainChunk(h, c.offset(16, 0, 0));
        net.jj.hollowbell.world.HomeGround.paint(l, w, b);
        net.jj.hollowbell.world.HomeGround.paint(l, w, a);
        var second = grab(h, x0, z0, lo, hi);
        int diff = 0;
        for (int i = 0; i < first.size(); i++) if (first.get(i) != second.get(i)) diff++;
        h.assertTrue(diff == 0, diff + " blocks came out different when the chunks were turned the other way round");
        putBack(h, x0, z0, lo, hi, before);
        w.clearForTests();
        force(h, c.getX(), c.getZ(), 1, false);
        force(h, c.getX() + 16, c.getZ(), 1, false);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "ground_raised")
    public void raisedGroundIsNeverHollow(GameTestHelper h) {
        var l = h.getLevel();
        var w = net.jj.hollowbell.world.WorldOne.get(l.getServer());
        w.clearForTests();
        BlockPos c = groundSpot(h, 225);
        var chunk = plainChunk(h, c);
        // a claim that puts this chunk out in the hills, where the plan raises plenty of it
        int best = -1, bestAt = 0;
        int[][] offs = {{-300, 0}, {0, -300}, {300, 0}, {0, 300}, {-420, 0}, {0, -420}, {-220, -220}, {220, 220}};
        for (int k = 0; k < offs.length; k++) {
            w.clearForTests();
            w.claimHome(l, c.getX() + offs[k][0], c.getZ() + offs[k][1], 0x5EED0100L + k);
            int[] y0 = new int[256];
            var o = net.jj.hollowbell.world.HomeGround.preview(l, w, chunk, y0);
            int up = 0;
            for (int i = 0; i < 256; i++) if (o.paint[i] && o.top[i] > y0[i]) up++;
            if (up > best) { best = up; bestAt = k; }
        }
        w.clearForTests();
        w.claimHome(l, c.getX() + offs[bestAt][0], c.getZ() + offs[bestAt][1], 0x5EED0100L + bestAt);
        plainChunk(h, c);
        net.jj.hollowbell.world.HomeGround.paint(l, w, chunk);
        var o = net.jj.hollowbell.world.HomeGround.lastOut;
        int[] y0 = net.jj.hollowbell.world.HomeGround.lastY0;
        h.assertTrue(o != null, "nothing was worked out");
        int raised = 0;
        for (int i = 0; i < 256; i++) {
            if (!o.paint[i] || o.top[i] <= y0[i]) continue;
            int x = chunk.getPos().getMinBlockX() + (i & 15), z = chunk.getPos().getMinBlockZ() + (i >> 4);
            for (int y = y0[i] + 1; y <= o.top[i]; y++) {
                var st = l.getBlockState(new BlockPos(x, y, z));
                h.assertTrue(!st.isAir() && st.getFluidState().isEmpty(), "raised ground is hollow at " + x + ", " + y + ", " + z);
            }
            raised++;
        }
        h.assertTrue(raised >= 10, "only " + raised + " columns were raised; the test proves little");
        w.clearForTests();
        force(h, c.getX(), c.getZ(), 1, false);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "ground_upgrade")
    public void anOldGroundGrowsAndIsTurnedAgain(GameTestHelper h) {
        var l = h.getLevel();
        var w = net.jj.hollowbell.world.WorldOne.get(l.getServer());
        w.clearForTests();
        BlockPos c = groundSpot(h, 226);
        // the first, small Hollows: two chunks turned (smooth stone on top, which the new core never uses), one since lived in
        w.claimOldHome(c.getX() - 100, c.getZ(), 0x5EED0200L);
        var fresh = plainChunk(h, c);
        var lived = plainChunk(h, c.offset(0, 0, 16));
        for (var ch : new net.minecraft.world.level.chunk.LevelChunk[]{fresh, lived}) {
            for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
                int x = ch.getPos().getMinBlockX() + dx, z = ch.getPos().getMinBlockZ() + dz;
                BlockPos t = new BlockPos(x, top(h, x, z), z);
                if (l.getBlockState(t).getFluidState().isEmpty()) l.setBlock(t, net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            }
            w.notePainted(ch.getPos().toLong());
        }
        lived.setInhabitedTime(5000);
        java.util.List<net.minecraft.world.level.block.state.BlockState> livedBefore = new java.util.ArrayList<>();
        java.util.List<Integer> livedTops = new java.util.ArrayList<>();
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int x = lived.getPos().getMinBlockX() + dx, z = lived.getPos().getMinBlockZ() + dz;
            livedTops.add(top(h, x, z));
            livedBefore.add(l.getBlockState(new BlockPos(x, top(h, x, z), z)));
        }
        h.assertTrue(w.groundVersion() == 1 && w.homeRadius() == 320, "the old ground isn't old");
        h.assertTrue(w.upgradeGround(), "the old ground wasn't brought up to date");
        h.assertTrue(w.groundVersion() == net.jj.hollowbell.world.WorldOne.GROUND_VERSION && w.homeRadius() >= 900, "the ground didn't grow: " + w.homeRadius());
        h.assertTrue(!w.paintedAlready(fresh.getPos().toLong()), "the old chunk isn't waiting to be turned again");
        net.jj.hollowbell.world.HomeGround.paint(l, w, fresh);
        net.jj.hollowbell.world.HomeGround.paint(l, w, lived);
        int changed = 0;
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int x = fresh.getPos().getMinBlockX() + dx, z = fresh.getPos().getMinBlockZ() + dz;
            if (!l.getBlockState(new BlockPos(x, top(h, x, z), z)).is(net.minecraft.world.level.block.Blocks.SMOOTH_STONE)) changed++;
        }
        h.assertTrue(changed >= 150, "only " + changed + " columns of the unvisited chunk were turned again");
        h.assertTrue(w.paintedAlready(fresh.getPos().toLong()) && w.paintedAlready(lived.getPos().toLong()), "the chunks aren't noted as done");
        int k = 0;
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int x = lived.getPos().getMinBlockX() + dx, z = lived.getPos().getMinBlockZ() + dz;
            h.assertTrue(top(h, x, z) == livedTops.get(k) && l.getBlockState(new BlockPos(x, top(h, x, z), z)) == livedBefore.get(k),
                    "the chunk people lived in was changed at " + x + ", " + z);
            k++;
        }
        // /hollowbell ground renew: the one people lived in is turned the new way after all
        h.assertTrue(w.oldKept() == 1, "the lived-in old chunk isn't noted as kept: " + w.oldKept());
        h.assertTrue(w.renewOld() == 1 && w.oldKept() == 0, "renew didn't let the kept chunk go");
        h.assertTrue(!w.paintedAlready(lived.getPos().toLong()), "the renewed chunk isn't waiting to be turned");
        net.jj.hollowbell.world.HomeGround.paint(l, w, lived);
        int renewed = 0;
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int x = lived.getPos().getMinBlockX() + dx, z = lived.getPos().getMinBlockZ() + dz;
            if (!l.getBlockState(new BlockPos(x, top(h, x, z), z)).is(net.minecraft.world.level.block.Blocks.SMOOTH_STONE)) renewed++;
        }
        h.assertTrue(renewed >= 180, "only " + renewed + " columns of the renewed chunk were turned");
        w.clearForTests();
        force(h, c.getX(), c.getZ(), 1, false);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "ground_den")
    public void theDenStandsInTheMiddle(GameTestHelper h) {
        var l = h.getLevel();
        var w = net.jj.hollowbell.world.WorldOne.get(l.getServer());
        w.clearForTests();
        BlockPos c = groundSpot(h, 227);
        w.claimHome(l, c.getX(), c.getZ(), 0x5EED0300L);
        var chunk = plainChunk(h, c);
        net.jj.hollowbell.world.HomeGround.paint(l, w, chunk);
        int t = top(h, c.getX(), c.getZ());
        var crown = l.getBlockState(new BlockPos(c.getX(), t, c.getZ()));
        h.assertTrue(crown.is(net.minecraft.world.level.block.Blocks.PEARLESCENT_FROGLIGHT), "the bell has no crown on top, it has " + crown);
        int air = 0;
        for (int y = t - 1; y > t - 30; y--) if (l.getBlockState(new BlockPos(c.getX(), y, c.getZ())).isAir()) air++;
        h.assertTrue(air >= 10, "the bell isn't hollow under its crown (" + air + " air)");
        int glass = 0, lights = 0;
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) for (int y = t - 30; y <= t; y++) {
            var st = l.getBlockState(new BlockPos(chunk.getPos().getMinBlockX() + dx, y, chunk.getPos().getMinBlockZ() + dz));
            if (st.is(net.minecraft.world.level.block.Blocks.LIME_STAINED_GLASS) || st.is(net.minecraft.world.level.block.Blocks.WHITE_STAINED_GLASS)) glass++;
            if (st.is(net.minecraft.world.level.block.Blocks.CHAIN)) lights++;
        }
        h.assertTrue(glass >= 60, "only " + glass + " glass in the bell over the middle chunk");
        h.assertTrue(lights >= 1, "no lights hang inside the bell");
        w.clearForTests();
        force(h, c.getX(), c.getZ(), 1, false);
        h.succeed();
    }

    // ------------------------------------------------------------------ seeing him from far off

    private static net.jj.hollowbell.net.FarSightPayload.Far farFor(java.util.List<net.jj.hollowbell.net.FarSightPayload.Far> all, UUID id) {
        for (var f : all) if (f.id().equals(id)) return f;
        return null;
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "far_sight")
    public void farOffHeIsSentToBeDrawn(GameTestHelper h) {
        HollowbellEntity near = spawnAway(h, S, HollowbellEntity.CALM, 130);
        HollowbellEntity gone = spawnAway(h, S, HollowbellEntity.HUNTER, 131);
        var a = net.jj.hollowbell.world.Away.get(h.getLevel().getServer());
        int keep = HollowbellConfig.V.farSightBlocks;
        h.runAfterDelay(30, () -> {
            UUID goneId = gone.getUUID();
            h.assertTrue(gone.stepAside(), "he should step out of the world");
            var rec = a.get(goneId);
            h.assertTrue(rec != null, "and be written down");
            // a player six hundred blocks from each: the one in the world, and where the sum says the other is
            Vec3 s = rec.spot(h.getLevel().getGameTime());
            double mx = (near.getX() + s.x) / 2, mz = (near.getZ() + s.z) / 2;
            double half = Math.hypot(s.x - near.getX(), s.z - near.getZ()) / 2;
            double off = Math.sqrt(Math.max(0, 600 * 600 - half * half));
            ServerPlayer p = player(h, new Vec3(mx, near.getY(), mz + off));
            h.runAfterDelay(5, () -> {
                HollowbellConfig.V.farSightBlocks = 1024;
                h.assertTrue(!net.jj.hollowbell.world.FarSight.tracked(p, near), "six hundred blocks off, he shouldn't be sent as a creature");
                var list = net.jj.hollowbell.world.FarSight.listFor(p);
                // through the wire and back, the way the player's game gets it
                var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
                net.jj.hollowbell.net.FarSightPayload.CODEC.encode(buf, new net.jj.hollowbell.net.FarSightPayload(list));
                list = net.jj.hollowbell.net.FarSightPayload.CODEC.decode(buf).all();
                var f1 = farFor(list, near.getUUID());
                h.assertTrue(f1 != null, "the one in the world isn't listed");
                h.assertTrue(Math.abs(f1.x() - near.getX()) < 0.01 && Math.abs(f1.z() - near.getZ()) < 0.01 && Math.abs(f1.y() - near.getY()) < 0.01,
                        "the one in the world is listed in the wrong place");
                h.assertTrue(Math.abs(f1.scale() - S) < 1e-4 && f1.variant() == HollowbellEntity.CALM, "the one in the world has the wrong size or mood");
                var f2 = farFor(list, goneId);
                h.assertTrue(f2 != null, "the one out of the world isn't listed");
                Vec3 now = rec.spot(h.getLevel().getGameTime());
                h.assertTrue(Math.abs(f2.x() - now.x) < 0.01 && Math.abs(f2.z() - now.z) < 0.01, "the one out of the world is listed in the wrong place");
                double wantY = net.jj.hollowbell.world.FarSight.groundUnder(h.getLevel(), now.x, now.z) + rec.lift;
                h.assertTrue(Math.abs(f2.y() - wantY) < 0.01, "the one out of the world is listed at the wrong height: " + f2.y() + " want " + wantY);
                h.assertTrue(f2.variant() == HollowbellEntity.HUNTER, "the one out of the world has the wrong mood");
                // far sight off: nobody is listed
                HollowbellConfig.V.farSightBlocks = 0;
                h.assertTrue(net.jj.hollowbell.world.FarSight.listFor(p).isEmpty(), "with far sight off, somebody is still listed");
                // and nothing past the reach
                HollowbellConfig.V.farSightBlocks = 300;
                h.assertTrue(net.jj.hollowbell.world.FarSight.listFor(p).isEmpty(), "somebody past the reach is listed");
                HollowbellConfig.V.farSightBlocks = keep;
                a.forget(goneId);
                drop(p);
                release(h, near);
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "far_sight_near")
    public void oneYouAlreadySeeIsNotSentTwice(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 132);
        int keep = HollowbellConfig.V.farSightBlocks;
        ServerPlayer p = player(h, new Vec3(e.getX() + 20, e.getY() + 2, e.getZ()));
        h.runAfterDelay(40, () -> {
            HollowbellConfig.V.farSightBlocks = 1024;
            h.assertTrue(net.jj.hollowbell.world.FarSight.tracked(p, e), "twenty blocks off, the player should be sent him as a creature");
            h.assertTrue(farFor(net.jj.hollowbell.world.FarSight.listFor(p), e.getUUID()) == null, "one the player already sees is listed for far sight too");
            HollowbellConfig.V.farSightBlocks = keep;
            drop(p);
            release(h, e);
            h.succeed();
        });
    }
}
