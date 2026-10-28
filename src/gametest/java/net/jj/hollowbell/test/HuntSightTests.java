package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.S;
import static net.jj.hollowbell.test.HollowbellGameTests.drop;
import static net.jj.hollowbell.test.HollowbellGameTests.player;
import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * In the hunting mood he goes after a player who is about, not safe, and in his sight: a wall hides you, one block
 * doesn't; a player he sees outranks a creature he's after; the safe, creative players and Peaceful are left be.
 */
public class HuntSightTests implements FabricGameTest {
    static final int OUT = 26;

    /** the middle of his rim: where he looks out from, level with where the test puts the player's eyes */
    static Vec3 rim(HollowbellEntity e) {
        return new Vec3(e.getX(), e.getY() + e.rig.rimY * e.bellScale(), e.getZ());
    }

    /** a spot OUT blocks off along x, its feet so the player's eyes are level with his rim, open between */
    static Vec3 spot(GameTestHelper h, HollowbellEntity e) {
        Vec3 r = rim(e);
        Vec3 feet = new Vec3(Math.floor(r.x + OUT) + 0.5, Math.floor(r.y - 1.62), Math.floor(r.z) + 0.5);
        open(h, r, feet.add(0, 1.62, 0));
        BlockPos f = BlockPos.containing(feet);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                h.getLevel().setBlockAndUpdate(f.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
                for (int dy = 0; dy < 3; dy++) h.getLevel().setBlockAndUpdate(f.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
            }
        return feet;
    }

    /** nothing solid within a block of the line from a to b (the land in between can't hide the player) */
    static void open(GameTestHelper h, Vec3 a, Vec3 b) {
        double len = a.distanceTo(b);
        for (double s = 0; s <= len; s += 0.5) {
            BlockPos c = BlockPos.containing(a.lerp(b, s / len));
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos q = c.offset(dx, dy, dz);
                        if (!h.getLevel().getBlockState(q).isAir())
                            h.getLevel().setBlockAndUpdate(q, Blocks.AIR.defaultBlockState());
                    }
        }
    }

    /** a player who is creative (the shared one never is) */
    static ServerPlayer creative(GameTestHelper h, Vec3 at) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "hb-c" + HollowbellGameTests.PLAYERS.incrementAndGet()), false);
        ServerPlayer p = new ServerPlayer(
                h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), ClientInformation.createDefault());
        Connection c = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(c);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(c, p, cookie);
        p.teleportTo(h.getLevel(), at.x, at.y, at.z, 0f, 0f);
        p.setGameMode(GameType.CREATIVE);
        return p;
    }

    static Zombie zombie(GameTestHelper h, HollowbellEntity e) {
        Zombie z = EntityType.ZOMBIE.create(h.getLevel());
        double x = e.getX() - 12, zz = e.getZ();
        z.moveTo(x, e.groundAt(x, zz), zz, 0f, 0f);
        z.setNoAi(true);
        z.setInvulnerable(true);
        z.setPersistenceRequired();
        h.getLevel().addFreshEntity(z);
        return z;
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "hunt_sight_go")
    public void heGoesForAPlayerHeSees(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.HUNTER, 1101);
        h.runAfterDelay(20, () -> {
            ServerPlayer p = player(h, spot(h, e));
            h.runAfterDelay(50, () -> {
                h.assertTrue(e.getTarget() == p, "a player in plain sight wasn't picked: " + e.getTarget());
                double d0 = e.horiz(p.position());
                h.runAfterDelay(120, () -> {
                    double d1 = e.horiz(p.position());
                    h.assertTrue(e.getTarget() == p, "he let the player go: " + e.getTarget());
                    h.assertTrue(d1 < d0 - 2, "he didn't come for the player: " + d0 + " then " + d1);
                    drop(p);
                    release(h, e);
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "hunt_sight_wall")
    public void aWallHidesYouOneBlockDoesNot(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.HUNTER, 1102);
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            Vec3 feet = spot(h, e);
            // one player walled in all round, a few blocks out; a second one beside with one block in front of
            // their eyes
            BlockPos f = BlockPos.containing(feet);
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++)
                    for (int dy = -1; dy <= 4; dy++)
                        if (Math.max(Math.abs(dx), Math.abs(dz)) == 3 || dy == -1 || dy == 4)
                            h.getLevel().setBlockAndUpdate(f.offset(dx, dy, dz), Blocks.STONE.defaultBlockState());
            ServerPlayer hid = player(h, feet);
            h.runAfterDelay(50, () -> {
                h.assertTrue(e.getTarget() != hid, "a player walled in all round was picked");
                Vec3 f2 = new Vec3(feet.x, feet.y, feet.z + 7);
                open(h, rim(e), f2.add(0, 1.62, 0));
                BlockPos b = BlockPos.containing(f2);
                h.getLevel().setBlockAndUpdate(b.below(), Blocks.STONE.defaultBlockState());
                h.getLevel().setBlockAndUpdate(b.offset(-1, 1, 0), Blocks.STONE.defaultBlockState());
                ServerPlayer seen = player(h, f2);
                h.runAfterDelay(50, () -> {
                    h.assertTrue(e.getTarget() == seen, "one block in front of a player hid them: " + e.getTarget());
                    drop(hid);
                    drop(seen);
                    release(h, e);
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "hunt_sight_mob")
    public void aPlayerHeSeesComesBeforeACreature(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.HUNTER, 1103);
        h.runAfterDelay(20, () -> {
            Zombie z = zombie(h, e);
            e.setTarget(z);
            ServerPlayer p = player(h, spot(h, e));
            h.runAfterDelay(50, () -> {
                h.assertTrue(e.getTarget() == p, "he stayed on the creature: " + e.getTarget());
                z.discard();
                drop(p);
                release(h, e);
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "hunt_sight_safe")
    public void creativePlayersAndPeacefulAreLeftBe(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.HUNTER, 1104);
        Difficulty was = h.getLevel().getDifficulty();
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            Vec3 feet = spot(h, e);
            ServerPlayer c = creative(h, feet);
            h.runAfterDelay(50, () -> {
                h.assertTrue(e.getTarget() != c, "a creative player was picked");
                drop(c);
                h.getLevel().getServer().setDifficulty(Difficulty.PEACEFUL, true);
                ServerPlayer p = player(h, feet);
                h.runAfterDelay(50, () -> {
                    boolean picked = e.getTarget() == p;
                    h.getLevel().getServer().setDifficulty(was, true);
                    h.assertTrue(!picked, "a player was picked on Peaceful");
                    drop(p);
                    release(h, e);
                    h.succeed();
                });
            });
        });
    }
}
