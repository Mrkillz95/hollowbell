package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.*;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.mixin.ChunkMapAccess;
import net.jj.hollowbell.test.mixin.ChunkCacheAccess;
import net.jj.hollowbell.world.Away;
import net.jj.hollowbell.world.FarOrders;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Saving, restarting and quitting. */
public class RestartTests implements FabricGameTest {
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
            h.assertTrue(
                    Math.abs(b.healthNow() - e.healthNow()) < 0.5f,
                    "health lost: " + b.healthNow() + " vs " + e.healthNow());
            h.assertTrue(b.healthMax() == e.healthMax(), "max health lost");
            h.assertTrue(b.staying(), "hold still lost");
            b.discard();
            release(h, e);
            h.succeed();
        });
    }

    private static final TicketType<Integer> QUIT_TEST = TicketType.create("hollowbell_quit_test", Integer::compare);

    /**
     * The hang on "Saving worlds": a chunk let go and then taken back before its unload job ran, while land next to
     * it is still being made. Quitting runs the unload queue until it's empty; the old job must end, not go round
     * forever.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "quit_unload")
    public void quittingNeverHangsOnAChunkTakenBackBeforeItWasUnloaded(GameTestHelper h) {
        var lv = h.getLevel();
        var cs = lv.getChunkSource();
        var cm = (ChunkMapAccess) cs.chunkMap;
        var upd = (ChunkCacheAccess) cs;
        var cp = new ChunkPos(h.absolutePos(BlockPos.ZERO).offset(20000 + 330 * 400, 0, 15000));
        long key = cp.toLong();
        cs.addRegionTicket(QUIT_TEST, cp, 0, 1);
        lv.getChunk(cp.x, cp.z);
        h.runAfterDelay(10, () -> {
            var holder = cm.hollowbell$holder(key);
            h.assertTrue(holder != null, "the test chunk didn't load");
            // let it go: the game queues the job that saves and drops it, and (no time left this tick) doesn't run it
            // yet
            cs.removeRegionTicket(QUIT_TEST, cp, 0, 1);
            upd.hollowbell$update();
            for (int i = 0; i < 20 && cm.hollowbell$holder(key) != null; i++) cm.hollowbell$tick(() -> false);
            h.assertTrue(cm.hollowbell$holder(key) == null, "the test chunk was never queued to be dropped");
            // taken back before the job ran, and land next to it is being made (it's held for that)
            cs.addRegionTicket(QUIT_TEST, cp, 0, 1);
            upd.hollowbell$update();
            h.assertTrue(cm.hollowbell$holder(key) == holder, "the test chunk wasn't taken back");
            holder.increaseGenerationRefCount();
            // a moment later the chunk is fully back, and the old job wakes up to find it "not ready"
            h.runAfterDelay(10, () -> {
                // quitting: the queue is run with all the time in the world (cut off here so a failure can't hang the
                // test)
                int[] n = {0};
                try {
                    cm.hollowbell$tick(() -> ++n[0] < 200_000);
                } finally {
                    holder.decreaseGenerationRefCount();
                    cs.removeRegionTicket(QUIT_TEST, cp, 0, 1);
                }
                h.assertTrue(n[0] < 200_000, "quitting would hang: the old unload job went round " + n[0] + " times");
                h.succeed();
            });
        });
    }

    /** a fake player with a set id (so the same one can "come back") */
    @SuppressWarnings("deprecation")
    static ServerPlayer playerWithId(GameTestHelper h, Vec3 at, UUID id) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(id, "hb-t" + id.toString().substring(0, 6)), false);
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
        p.setGameMode(GameType.SURVIVAL);
        return p;
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "keep_goto")
    public void aGoThereOrderSurvivesARestart(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 320);
        h.runAfterDelay(20, () -> {
            Vec3 to = e.position().add(300, 0, 0);
            e.orderTo(to, null);
            HollowbellEntity b = reload(h, e);
            h.assertTrue(
                    b.goal() != null && Math.abs(b.goal().x - to.x) < 2,
                    "the spot he was sent to is lost: " + b.goal());
            h.assertTrue(b.holdsThere(), "he forgot to hold still once there");
            double x0 = b.getX();
            h.runAfterDelay(80, () -> {
                h.assertTrue(b.getX() > x0 + 1, "he isn't going there: " + x0 + " -> " + b.getX());
                release(h, b);
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "keep_stay")
    public void stayAndAreaSurviveARestart(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 321);
        h.runAfterDelay(20, () -> {
            e.sendAfter(pig(h, under(e).add(3, 0, 0)));
            e.setStay(true);
            e.bindTo(e.getX(), e.getZ(), 64);
            HollowbellEntity b = reload(h, e);
            h.assertTrue(b.staying(), "he stopped staying");
            h.assertTrue(b.bound() && b.boundRadius() == 64, "his area is lost");
            release(h, b);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "keep_come")
    public void comeToMeWaitsForSomebodyWhoLeftThenCarriesOn(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 322);
        UUID id = UUID.randomUUID();
        h.runAfterDelay(20, () -> {
            ServerPlayer p = playerWithId(h, e.position().add(40, 0, 0), id);
            e.orderTo(p.position(), p);
            drop(p); // they leave the game
            HollowbellEntity b = reload(h, e);
            h.assertTrue(id.equals(b.comingTo()), "who he's coming to is lost");
            h.runAfterDelay(30, () -> {
                h.assertTrue(b.waitingForSomebody(), "he should know they've gone and wait for them");
                h.assertTrue(id.equals(b.comingTo()), "he gave up at once");
                // they come back somewhere else: he carries on to them
                ServerPlayer back = playerWithId(h, b.position().add(-40, 0, 10), id);
                h.runAfterDelay(30, () -> {
                    h.assertTrue(!b.waitingForSomebody(), "he didn't notice they're back");
                    h.assertTrue(
                            b.goal() != null && b.goal().distanceTo(back.position()) < 5,
                            "he isn't coming to where they are now: " + b.goal());
                    // they go again, and this time he gives up (the 20 minutes cut short)
                    drop(back);
                    long was = HollowbellEntity.comeGiveUpMs;
                    HollowbellEntity.comeGiveUpMs = 1;
                    h.runAfterDelay(50, () -> {
                        HollowbellEntity.comeGiveUpMs = was;
                        h.assertTrue(b.comingTo() == null && !b.holdsThere(), "he never gave up waiting");
                        release(h, b);
                        h.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "keep_away")
    public void anAwayTripAndItsOrderSurviveARestart(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 323);
        var server = h.getLevel().getServer();
        var a = Away.get(server);
        h.runAfterDelay(20, () -> {
            UUID id = e.getUUID();
            UUID who = UUID.randomUUID();
            Vec3 was = e.position();
            h.assertTrue(e.stepAside(), "he should step out of the world");
            h.assertTrue(a.order(h.getLevel(), id, was.add(2000, 0, 0), who), "the order didn't reach him out there");
            CompoundTag saved = a.save(new CompoundTag(), h.getLevel().registryAccess());
            var a2 = Away.load(saved, h.getLevel().registryAccess());
            var r = a2.get(id);
            h.assertTrue(r != null, "the record is lost");
            h.assertTrue(r.going && Math.abs(r.toX - (was.x + 2000)) < 2, "the trip is lost");
            h.assertTrue(r.hold && who.equals(r.follow), "the order is lost");
            h.assertTrue(r.spot(h.getLevel().getGameTime() + 200).x > was.x + 1, "he isn't going anywhere");
            a.forget(id);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "keep_fetch")
    public void anOrderToOneInUnloadedLandSurvivesARestart(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 324);
        var server = h.getLevel().getServer();
        var a = Away.get(server);
        h.runAfterDelay(20, () -> {
            UUID id = e.getUUID();
            Vec3 at = e.position();
            release(h, e);
            a.noteParked(e); // (left in land nobody has loaded)
            Vec3 to = at.add(500, 0, 0);
            var t = new FarOrders.Target(FarOrders.Kind.PARKED, id, at.x, at.z, null);
            h.assertTrue(FarOrders.order(h.getLevel(), t, to, null, 0f) != null, "the order went nowhere");
            CompoundTag saved = a.save(new CompoundTag(), h.getLevel().registryAccess());
            FarOrders.forget(); // the server stops
            Away.load(saved, h.getLevel().registryAccess()); // and starts again
            Object[] f = FarOrders.firstFetch();
            h.assertTrue(
                    f != null && id.equals(f[0]) && Math.abs(((Vec3) f[1]).x - to.x) < 1, "the order to him is lost");
            FarOrders.forget();
            a.unpark(id);
            h.succeed();
        });
    }
}
