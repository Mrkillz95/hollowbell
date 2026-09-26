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
}
