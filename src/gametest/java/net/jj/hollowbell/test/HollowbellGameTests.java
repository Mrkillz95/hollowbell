package net.jj.hollowbell.test;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.Giants;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Meeting;
import net.jj.hollowbell.rig.BellModel;
import net.jj.hollowbell.rig.BellRig;
import net.jj.hollowbell.world.Away;
import net.jj.hollowbell.world.HomeGround;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Server-side checks: run with ./gradlew runGametest. Each test has a batch of its own, far from the others. */
public class HollowbellGameTests implements FabricGameTest {
    static final float S = 0.1f;

    static HollowbellEntity spawnAway(GameTestHelper h, float scale, int variant, int slot) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        int x = o.getX() + 20000 + slot * 400, z = o.getZ() + 5000;
        // (the land all round him, as round a player: the bigger he is, the further; nothing he reads is still being
        // made)
        int rc = 3 + Math.round(3 * scale);
        for (int cx = (x >> 4) - rc; cx <= (x >> 4) + rc; cx++)
            for (int cz = (z >> 4) - rc; cz <= (z >> 4) + rc; cz++) {
                h.getLevel().setChunkForced(cx, cz, true);
                h.getLevel().getChunk(cx, cz);
            }
        runsEntities(h, x, z, rc - 2);
        int y = h.getLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        HollowbellEntity e = ModEntities.HOLLOWBELL.create(h.getLevel());
        e.moveTo(x + 0.5, y, z + 0.5, 0f, 0f);
        e.setBellScale(scale);
        e.setVariant(variant);
        e.setMoveCooldown(100000); // he only does what the test tells him
        e.skipArrival();
        h.getLevel().addFreshEntity(e);
        return e;
    }

    /**
     * Waits until the forced land round x z (r chunks each way) runs its entities, by looking at the land itself, for
     * at most half a minute of real time. A server that is behind finishes making forced land ready only in its
     * spare time between ticks, and a loaded machine may give it none for a long while: what a test puts there then
     * stands still, and a test that counts ticks from there fails for no fault of his. This pumps the same work the
     * game's own wait for a chunk does.
     */
    static void runsEntities(GameTestHelper h, int x, int z, int r) {
        var l = h.getLevel();
        long until = System.nanoTime() + 30_000_000_000L;
        for (int cx = (x >> 4) - Math.max(0, r); cx <= (x >> 4) + Math.max(0, r); cx++) {
            for (int cz = (z >> 4) - Math.max(0, r); cz <= (z >> 4) + Math.max(0, r); cz++) {
                BlockPos p = new BlockPos(cx << 4, 0, cz << 4);
                while (!l.isPositionEntityTicking(p) && System.nanoTime() < until) {
                    if (!l.getChunkSource().pollTask()) LockSupport.parkNanos(1_000_000L);
                }
            }
        }
    }

    static void release(GameTestHelper h, HollowbellEntity e) {
        int x = e.getBlockX(), z = e.getBlockZ();
        e.discard();
        int rc = 4 + Math.round(3 * e.bellScale());
        for (int cx = (x >> 4) - rc; cx <= (x >> 4) + rc; cx++)
            for (int cz = (z >> 4) - rc; cz <= (z >> 4) + rc; cz++) h.getLevel().setChunkForced(cx, cz, false);
    }

    static Pig pig(GameTestHelper h, Vec3 at) {
        Pig p = EntityType.PIG.create(h.getLevel());
        p.moveTo(at.x, at.y, at.z, 0f, 0f);
        p.setNoAi(true);
        p.setPersistenceRequired();
        h.getLevel().addFreshEntity(p);
        return p;
    }

    @SuppressWarnings("deprecation")
    static final AtomicInteger PLAYERS = new AtomicInteger();

    static ServerPlayer player(GameTestHelper h, Vec3 at) {
        // each one its own name: tests run side by side, and a command naming a player takes the first one online by
        // that name (with every test player called the same, "set target" in one test picked another test's player)
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "hb-test" + PLAYERS.incrementAndGet()), false);
        ServerPlayer p =
                new ServerPlayer(
                        h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
                    @Override
                    public boolean isSpectator() {
                        return false;
                    }

                    @Override
                    public boolean isCreative() {
                        return false;
                    }
                };
        Connection c = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(c);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(c, p, cookie);
        p.teleportTo(h.getLevel(), at.x, at.y, at.z, 0f, 0f);
        try {
            Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            f.setAccessible(true);
            f.setInt(p, 0);
        } catch (ReflectiveOperationException e) {
            HollowbellMod.LOG.warn("Test player keeps its spawn protection: {}", e.toString());
        }
        p.setGameMode(GameType.SURVIVAL);
        return p;
    }

    static void drop(ServerPlayer p) {
        p.getServer().getPlayerList().remove(p);
    }

    /** the ground right under his middle */
    static Vec3 under(HollowbellEntity e) {
        return new Vec3(e.getX() + 0.3, e.groundAt(e.getX(), e.getZ()), e.getZ() + 0.3);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "rig")
    public void theModelAndRigLoad(GameTestHelper h) {
        BellRig rig = BellRig.get();
        BellModel m = BellModel.get();
        h.assertTrue(
                rig.boneCount() == m.boneCount(), "rig has " + rig.boneCount() + " bones, model has " + m.boneCount());
        for (int b = 0; b < rig.boneCount(); b++)
            h.assertTrue(rig.boneNames[b].equals(m.boneNames[b]), "bone " + b + " named differently");
        h.assertTrue(rig.arms.length == 8, "arms: " + rig.arms.length);
        h.assertTrue(rig.spots.length == 5, "spots: " + rig.spots.length);
        for (String n : rig.boneNames) h.assertTrue(!n.startsWith("thread"), "a thread is still in the rig: " + n);
        h.assertTrue(rig.strands.length >= 50, "strands: " + rig.strands.length);
        h.assertTrue(rig.pods.length >= 10 && rig.pods.length <= 31, "pods: " + rig.pods.length);
        h.assertTrue(rig.eggs.length >= 10, "egg clumps: " + rig.eggs.length);
        // every block in him is a real Java block (nothing turned into stone)
        var lookup = BuiltInRegistries.BLOCK.asLookup();
        for (String p : m.palette) {
            try {
                BlockStateParser.parseForBlock(lookup, p, false);
            } catch (CommandSyntaxException ex) {
                h.fail("not a Java block: " + p);
            }
        }
        long voxels = 0;
        for (int b = 0; b < m.boneCount(); b++) voxels += m.count(b);
        h.assertTrue(voxels > 300000, "too few blocks drawn: " + voxels);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "summon")
    public void heSummonsAndFloats(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 1);
        h.runAfterDelay(40, () -> {
            h.assertTrue(e.isAlive(), "he isn't alive");
            float want = Math.max(40f, BellConfig.V.health * S);
            h.assertTrue(Math.abs(e.healthMax() - want) < 1f, "health " + e.healthMax() + " want " + want);
            h.assertTrue(e.podsLeft() == e.rig.pods.length, "pods popped already");
            double ground = e.groundAt(e.getX(), e.getZ());
            // drifting with nothing to do, he floats a little way over the ground, his strands near it
            h.assertTrue(
                    e.getY() > ground - 1 && e.getY() < ground + 60 * S + 5,
                    "he should float just over the ground: y " + e.getY() + " ground " + ground);
            // the pose puts his crown where it should be
            Vec3 c = e.crownWorld();
            h.assertTrue(
                    Math.abs(c.y - (e.getY() + (e.rig.crownY + 1) * S)) < 3,
                    "crown at " + c + " he is at " + e.position());
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "command")
    public void theSummonCommandWorks(GameTestHelper h) {
        var src = h.getLevel()
                .getServer()
                .createCommandSourceStack()
                .withPosition(Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 2, 2))))
                .withLevel(h.getLevel())
                .withPermission(4)
                .withSuppressedOutput();
        int before = h.getLevel().getEntities(ModEntities.HOLLOWBELL, x -> true).size();
        h.getLevel().getServer().getCommands().performPrefixedCommand(src, "hollowbell summon guardian 0.05");
        h.runAfterDelay(5, () -> {
            var all = h.getLevel()
                    .getEntities(ModEntities.HOLLOWBELL, x -> x.isGuardian() && Math.abs(x.bellScale() - 0.05f) < 1e-3);
            h.assertTrue(
                    h.getLevel().getEntities(ModEntities.HOLLOWBELL, x -> true).size() == before + 1 && !all.isEmpty(),
                    "the command made nothing");
            for (var x : all) x.discard();
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "command_rights")
    public void detailIsForEveryoneTheRestNeedsCheats(GameTestHelper h) {
        var d = h.getLevel().getServer().getCommands().getDispatcher();
        var base = h.getLevel()
                .getServer()
                .createCommandSourceStack()
                .withLevel(h.getLevel())
                .withSuppressedOutput();
        var player = base.withPermission(0);
        var op = base.withPermission(2);
        h.assertTrue(
                !d.parse("hollowbell detail on", player).getReader().canRead()
                        && d.parse("hollowbell detail on", player)
                                .getExceptions()
                                .isEmpty(),
                "a player without cheats can't use /hollowbell detail");
        h.assertTrue(
                d.parse("hollowbell summon", player).getReader().canRead(),
                "a player without cheats can use /hollowbell summon");
        for (String c : new String[] {
            "hollowbell summon calm 0.3",
            "hollowbell list",
            "hollowbell do sky_dive",
            "hollowbell height 20",
            "hollowbell goto 10 10",
            "hollowbell stay true",
            "hollowbell ride",
            "hollowbell detail",
            "hollowbell reload"
        }) {
            var p = d.parse(c, op);
            h.assertTrue(
                    !p.getReader().canRead() && p.getExceptions().isEmpty(), "/" + c + " doesn't parse with cheats on");
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
            h.assertTrue(
                    e.rig.kind[hit.bone()] == BellRig.Kind.CROWN,
                    "looking down on the crown hit " + e.rig.boneNames[hit.bone()]);
            // from the side at rim height: the rim or an arm
            Vec3 side = e.position().add(-(100 * S + 5), 134 * S, 0.2);
            var hit2 = e.raycast(side, new Vec3(1, 0, 0), 30);
            h.assertTrue(hit2 != null, "nothing hit from the side");
            // and a hit on a spot is worth far more than one on his copper
            h.assertTrue(
                    e.worth(e.rig.spots[0].bone(), false) > e.worth(e.rig.bellBone, false) * 10,
                    "spots aren't weak spots");
            h.assertTrue(
                    e.worth(e.rig.crownBone, true) > e.worth(e.rig.crownBone, false),
                    "hitting from inside isn't worth more");
            release(h, e);
            h.succeed();
        });
    }

    /** a stand-in for another giant boss: anything tagged as a giant counts as one */
    static Zombie fakeGiant(GameTestHelper h, Vec3 at) {
        Zombie z = EntityType.ZOMBIE.create(h.getLevel());
        z.moveTo(at.x, at.y, at.z, 0f, 0f);
        z.setNoAi(true);
        z.setPersistenceRequired();
        z.addTag(Giants.TAG);
        h.getLevel().addFreshEntity(z);
        return z;
    }

    /**
     * a part box of another giant: not a creature itself, it has an owner() (like Pitchgut's, the Furrowmaw's, the
     * Cerberus's)
     */
    public static class FakePart extends Interaction {
        final LivingEntity owner;

        public FakePart(Level l, LivingEntity owner) {
            super(EntityType.INTERACTION, l);
            this.owner = owner;
            addTag(Giants.TAG);
        }

        public LivingEntity owner() {
            return owner;
        }
    }

    /** the middle of something */
    static Vec3 mid(Entity e) {
        return e.getBoundingBox().getCenter();
    }

    static CommandSourceStack op(GameTestHelper h, Vec3 at) {
        return h.getLevel()
                .getServer()
                .createCommandSourceStack()
                .withPosition(at)
                .withLevel(h.getLevel())
                .withPermission(4)
                .withSuppressedOutput();
    }

    static void run(GameTestHelper h, Vec3 at, String cmd) {
        h.getLevel().getServer().getCommands().performPrefixedCommand(op(h, at), cmd);
    }

    /** a clean world for the tests that count him: nobody standing, nobody out of the world */
    static void clearAll(GameTestHelper h) {
        var server = h.getLevel().getServer();
        for (var l : server.getAllLevels())
            for (HollowbellEntity e : new ArrayList<>(l.getEntities(ModEntities.HOLLOWBELL, x -> !x.isRemoved())))
                e.discard();
        Away.get(server).forgetAll();
    }

    static void force(GameTestHelper h, int x, int z, int r, boolean on) {
        for (int cx = (x >> 4) - r; cx <= (x >> 4) + r; cx++)
            for (int cz = (z >> 4) - r; cz <= (z >> 4) + r; cz++) {
                h.getLevel().setChunkForced(cx, cz, on);
                if (on) h.getLevel().getChunk(cx, cz);
            }
    }

    static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    static Object[] args(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getArgs() : new Object[0];
    }

    static boolean parses(GameTestHelper h, String cmd, int perm) {
        var d = h.getLevel().getServer().getCommands().getDispatcher();
        var p = d.parse(
                cmd,
                h.getLevel()
                        .getServer()
                        .createCommandSourceStack()
                        .withLevel(h.getLevel())
                        .withPermission(perm)
                        .withSuppressedOutput());
        return !p.getReader().canRead() && p.getExceptions().isEmpty();
    }

    /**
     * A spot far off for the ground tests, its chunks loaded. The test world is a normal one with a new seed each
     * time, so the spot walks along until the generator says the land round it is dry (it never loads to look).
     */
    static BlockPos groundSpot(GameTestHelper h, int slot) {
        var l = h.getLevel();
        var src = l.getChunkSource();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        int bx = ((o.getX() + 20000 + slot * 400) >> 4 << 4) + 8, bz = ((o.getZ() + 5000) >> 4 << 4) + 8;
        int x = bx, z = bz;
        search:
        for (int tries = 0; tries < 80; tries++) {
            int tx = bx + (tries % 2 == 0 ? 0 : 160), tz = bz + (tries / 2) * 176;
            for (int dx = -40; dx <= 40; dx += 20)
                for (int dz = -40; dz <= 40; dz += 20) {
                    int g = src.getGenerator()
                            .getBaseHeight(tx + dx, tz + dz, Heightmap.Types.OCEAN_FLOOR_WG, l, src.randomState());
                    if (g <= l.getSeaLevel() + 3) continue search;
                }
            x = tx;
            z = tz;
            break;
        }
        force(h, x, z, 1, true);
        return new BlockPos(x, 0, z);
    }

    static int top(GameTestHelper h, int x, int z) {
        return h.getLevel().getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
    }

    static boolean hollowsAt(GameTestHelper h, int x, int y, int z) {
        var ch = h.getLevel().getChunkAt(new BlockPos(x, y, z));
        return ch.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z))
                .is(HomeGround.BELL_HOLLOWS);
    }

    static boolean ours(BlockState s) {
        return HomeGround.isOurs(s);
    }

    /** every block in these columns from a little under the ground to high over it */
    static int count(GameTestHelper h, List<BlockPos> at, Predicate<BlockState> what) {
        int n = 0;
        for (BlockPos p : at) if (what.test(h.getLevel().getBlockState(p))) n++;
        return n;
    }

    /** everything a command says back, caught */
    static List<Component> said(GameTestHelper h, Vec3 at, String cmd) {
        List<Component> out = new ArrayList<>();
        CommandSource catcher = new CommandSource() {
            @Override
            public void sendSystemMessage(Component c) {
                out.add(c);
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        var src = new CommandSourceStack(
                catcher,
                at,
                Vec2.ZERO,
                h.getLevel(),
                4,
                "test",
                Component.literal("test"),
                h.getLevel().getServer(),
                null);
        h.getLevel().getServer().getCommands().performPrefixedCommand(src, cmd);
        return out;
    }

    /** a spot on the flat ground of this test, dx/dz blocks from its corner, standing on the top block */
    static Vec3 onGround(GameTestHelper h, int dx, int dz) {
        BlockPos o = h.absolutePos(new BlockPos(dx, 0, dz));
        return new Vec3(o.getX() + 0.5, top(h, o.getX(), o.getZ()) + 1, o.getZ() + 0.5);
    }

    /** saves him, takes him out and puts a copy made from the save back in (a restart, as far as he can tell) */
    static HollowbellEntity reload(GameTestHelper h, HollowbellEntity e) {
        CompoundTag tag = new CompoundTag();
        e.saveWithoutId(tag);
        e.discard();
        HollowbellEntity b = ModEntities.HOLLOWBELL.create(h.getLevel());
        b.load(tag);
        b.setMoveCooldown(100000);
        h.getLevel().addFreshEntity(b);
        return b;
    }

    /** a stand-in for another giant, of that mod's kind */
    static Zombie giantOfKind(GameTestHelper h, Vec3 at, String kind) {
        var z = fakeGiant(h, at);
        z.addTag(Meeting.KIND + kind);
        z.setInvulnerable(true);
        return z;
    }

    static boolean done(ServerPlayer p, String name) {
        var adv = p.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("hollowbell", name));
        return adv != null && p.getAdvancements().getOrStartProgress(adv).isDone();
    }
}
