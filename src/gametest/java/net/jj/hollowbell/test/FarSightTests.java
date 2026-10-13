package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.*;

import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.Detail;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.net.FarSightPayload;
import net.jj.hollowbell.world.Away;
import net.jj.hollowbell.world.FarSight;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Seeing him from far off. */
public class FarSightTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "detail")
    public void detailOnOnlySimplifiesFarAway(GameTestHelper h) {
        boolean was = Detail.on();
        try {
            Detail.set(true);
            h.assertTrue(Detail.lod(20, 1f) == Detail.FULL, "detail on: not full close up");
            h.assertTrue(Detail.lod(400, 1f) != Detail.FULL, "detail on: not simpler far away");
            h.assertTrue(Detail.lod(3000, 1f) == Detail.TINY, "detail on: not simplest very far away");
            Detail.set(false);
            for (double d : new double[] {5, 200, 800, 5000})
                for (float s : new float[] {0.03f, 0.2f, 1f, 2f})
                    h.assertTrue(Detail.lod(d, s) == Detail.FULL, "detail off but simplified at " + d + " size " + s);
            // it's kept in the settings file
            BellConfig.load();
            h.assertTrue(!Detail.on(), "detail off wasn't saved");
        } finally {
            Detail.set(was);
        }
        h.succeed();
    }

    private static FarSightPayload.Far farFor(List<FarSightPayload.Far> all, UUID id) {
        for (var f : all) if (f.id().equals(id)) return f;
        return null;
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "far_sight")
    public void farOffHeIsSentToBeDrawn(GameTestHelper h) {
        HollowbellEntity near = spawnAway(h, S, HollowbellEntity.CALM, 130);
        HollowbellEntity gone = spawnAway(h, S, HollowbellEntity.HUNTER, 131);
        var a = Away.get(h.getLevel().getServer());
        int keep = BellConfig.V.farSightBlocks;
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
                BellConfig.V.farSightBlocks = 1024;
                h.assertTrue(!FarSight.tracked(p, near), "six hundred blocks off, he shouldn't be sent as a creature");
                var list = FarSight.listFor(p);
                // through the wire and back, the way the player's game gets it
                var buf = new RegistryFriendlyByteBuf(
                        io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
                FarSightPayload.CODEC.encode(buf, new FarSightPayload(list));
                list = FarSightPayload.CODEC.decode(buf).all();
                var f1 = farFor(list, near.getUUID());
                h.assertTrue(f1 != null, "the one in the world isn't listed");
                h.assertTrue(
                        Math.abs(f1.x() - near.getX()) < 0.01
                                && Math.abs(f1.z() - near.getZ()) < 0.01
                                && Math.abs(f1.y() - near.getY()) < 0.01,
                        "the one in the world is listed in the wrong place");
                h.assertTrue(
                        Math.abs(f1.scale() - S) < 1e-4 && f1.variant() == HollowbellEntity.CALM,
                        "the one in the world has the wrong size or mood");
                var f2 = farFor(list, goneId);
                h.assertTrue(f2 != null, "the one out of the world isn't listed");
                Vec3 now = rec.spot(h.getLevel().getGameTime());
                h.assertTrue(
                        Math.abs(f2.x() - now.x) < 0.01 && Math.abs(f2.z() - now.z) < 0.01,
                        "the one out of the world is listed in the wrong place");
                double wantY = FarSight.groundUnder(h.getLevel(), now.x, now.z) + rec.lift;
                h.assertTrue(
                        Math.abs(f2.y() - wantY) < 0.01,
                        "the one out of the world is listed at the wrong height: " + f2.y() + " want " + wantY);
                h.assertTrue(f2.variant() == HollowbellEntity.HUNTER, "the one out of the world has the wrong mood");
                // far sight off: nobody is listed
                BellConfig.V.farSightBlocks = 0;
                h.assertTrue(FarSight.listFor(p).isEmpty(), "with far sight off, somebody is still listed");
                // and nothing past the reach
                BellConfig.V.farSightBlocks = 300;
                h.assertTrue(FarSight.listFor(p).isEmpty(), "somebody past the reach is listed");
                BellConfig.V.farSightBlocks = keep;
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
        int keep = BellConfig.V.farSightBlocks;
        ServerPlayer p = player(h, new Vec3(e.getX() + 20, e.getY() + 2, e.getZ()));
        h.runAfterDelay(40, () -> {
            BellConfig.V.farSightBlocks = 1024;
            h.assertTrue(FarSight.tracked(p, e), "twenty blocks off, the player should be sent him as a creature");
            h.assertTrue(
                    farFor(FarSight.listFor(p), e.getUUID()) == null,
                    "one the player already sees is listed for far sight too");
            BellConfig.V.farSightBlocks = keep;
            drop(p);
            release(h, e);
            h.succeed();
        });
    }
}
