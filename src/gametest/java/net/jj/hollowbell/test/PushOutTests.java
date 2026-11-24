package net.jj.hollowbell.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.solid.Solid;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Nobody stays inside him where they aren't meant to be: a player in a boat in him, one in and out of him over and
 * over, and one walled in by a hollow of him are all moved out.
 */
public class PushOutTests implements FabricGameTest {

    private static HollowbellEntity him(GameTestHelper h, float size, int slot) {
        return HollowbellGameTests.spawnAway(h, size, HollowbellEntity.CALM, slot);
    }

    private static ServerPlayer someone(GameTestHelper h, Vec3 at) {
        return HollowbellGameTests.player(h, at);
    }

    private static void gone(GameTestHelper h, HollowbellEntity e, ServerPlayer... ps) {
        for (ServerPlayer p : ps) HollowbellGameTests.drop(p);
        HollowbellGameTests.release(h, e);
    }

    /** how finely to look through him: about 60000 spots in all */
    private static double step(AABB t) {
        return Math.max(0.5, Math.cbrt(t.getXsize() * t.getYsize() * t.getZsize() / 60000));
    }

    /** a spot with a player's box well in him (half a block or more, no top of his at their feet), nearest his
     *  middle, or null */
    static Vec3 deepSpot(HollowbellEntity e, ServerPlayer p) {
        AABB t = e.solidTightBox();
        var dim = p.getDimensions(p.getPose());
        double st = step(t), bestD = Double.MAX_VALUE;
        Vec3 best = null;
        for (double y = t.minY; y < t.maxY; y += st)
            for (double x = t.minX; x < t.maxX; x += st)
                for (double z = t.minZ; z < t.maxZ; z += st) {
                    double d = Math.hypot(x - e.getX(), z - e.getZ());
                    if (d >= bestD) continue;
                    Vec3 at = new Vec3(x, y, z);
                    if (Solid.depthIn(e, dim.makeBoundingBox(at), 0.08, null, true) < 0.6) continue;
                    p.setPos(at);
                    if (Solid.anchorUnder(e, p, 0.9, 1.5) != null) continue;
                    best = at;
                    bestD = d;
                }
        return best;
    }

    /** near `at`, a spot still well in him (he drifts and pulses round a test player, who can't move by itself) */
    static Vec3 keptIn(HollowbellEntity e, ServerPlayer p, Vec3 at) {
        var dim = p.getDimensions(p.getPose());
        if (Solid.depthIn(e, dim.makeBoundingBox(at), 0.08, null, true) >= 0.6) return at;
        Vec3 best = at;
        double most = 0;
        for (double dx = -1.5; dx <= 1.5; dx += 0.5)
            for (double dy = -1.5; dy <= 1.5; dy += 0.5)
                for (double dz = -1.5; dz <= 1.5; dz += 0.5) {
                    Vec3 c = at.add(dx, dy, dz);
                    double d = Solid.depthIn(e, dim.makeBoundingBox(c), 0.08, null, true);
                    if (d > most) {
                        most = d;
                        best = c;
                    }
                }
        return best;
    }

    /** a spot standing in a hollow of him, walled in by him, or null */
    static Vec3 hollowOf(HollowbellEntity e, ServerPlayer p) {
        AABB t = e.solidTightBox();
        var dim = p.getDimensions(p.getPose());
        // (only on his tops: one walled in by a hollow of him stands on him there; about 40000 columns in all)
        double st = Math.max(0.5, Math.sqrt(t.getXsize() * t.getZsize() / 40000));
        Solid.Runs r = new Solid.Runs();
        for (double x = t.minX; x < t.maxX; x += st)
            for (double z = t.minZ; z < t.maxZ; z += st) {
                Solid.column(e, x, z, t.minY - 1, t.maxY + 1, false, r);
                for (int j = 0; j < r.n; j++) {
                    Vec3 at = new Vec3(x, r.top[j] + 0.01, z);
                    if (e.solidHollowHolds(at) || !Solid.clear(e, p, dim.makeBoundingBox(at))) continue;
                    if (Solid.walledAt(e, p, at)) return at;
                }
            }
        return null;
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "push_boat")
    public void aPlayerInABoatInsideHimIsGotOffAndMovedOut(GameTestHelper h) {
        HollowbellEntity e = him(h, 0.5f, 540);
        SolidTests.after(h, 60, () -> {
            h.assertTrue(e.solidReady(), "he isn't solid yet");
            e.setStay(true);
            // (held where he is: a test player has no game of its own to keep up with him as he drifts)
            e.setFrozen(true);
            ServerPlayer p = someone(h, e.position());
            Vec3 in = deepSpot(e, p);
            h.assertTrue(in != null, "nowhere deep in him");
            // (kept in him while they sit in it: a test player's game doesn't drive the boat)
            Vec3[] spot = {in}, was = {e.position()};
            Boat boat = EntityType.BOAT.create(h.getLevel());
            boat.setPos(in);
            h.getLevel().addFreshEntity(boat);
            p.teleportTo(h.getLevel(), in.x, in.y, in.z, 0f, 0f);
            h.assertTrue(p.startRiding(boat, true), "the player didn't get in the boat");
            // (out at some tick: he drifts, and may come back over them after; their own game moves them at once)
            boolean[] out = {false, false};
            for (int k = 1; k <= 19; k++)
                SolidTests.after(h, k, () -> {
                    if (p.isPassenger()) {
                        spot[0] = keptIn(e, p, spot[0].add(e.position().subtract(was[0])));
                        was[0] = e.position();
                        boat.setPos(spot[0]);
                        boat.setDeltaMovement(Vec3.ZERO);
                        return;
                    }
                    if (!Solid.inside(e, p, 0.08) && p.level().noCollision(p, p.getBoundingBox().deflate(0.05)))
                        out[0] = true;
                    if (!Solid.inside(e, boat, 0.08)) out[1] = true;
                });
            SolidTests.after(h, 20, () -> {
                try {
                    h.assertTrue(!p.isPassenger(), "still in the boat inside him");
                    h.assertTrue(out[0], "the player wasn't moved out of him onto a free spot: " + p.position());
                    h.assertTrue(out[1], "the boat wasn't moved out of him: " + boat.position());
                } finally {
                    boat.discard();
                    gone(h, e, p);
                }
                h.succeed();
            });
        });
    }

    /** in him two ticks, out one, over and over (half in a part of him as it swings): still moved out */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "push_in_and_out")
    public void inAndOutOfHimStillAddsUpToBeingMovedOut(GameTestHelper h) {
        HollowbellEntity e = him(h, 0.5f, 541);
        ServerPlayer[] p = {null};
        Vec3[] in = {null};
        int[] was = {0};
        SolidTests.after(h, 60, () -> {
            h.assertTrue(e.solidReady(), "he isn't solid yet");
            e.setStay(true);
            // (held where he is: a test player has no game of its own to keep up with him as he drifts)
            e.setFrozen(true);
            p[0] = someone(h, e.position());
            in[0] = deepSpot(e, p[0]);
            h.assertTrue(in[0] != null, "nowhere deep in him");
            Vec3[] him = {e.position()};
            was[0] = Solid.unstuckServer;
            for (int k = 0; k <= 36; k++) {
                int kk = k;
                SolidTests.after(h, k, () -> {
                    // (kept in him as he drifts; out over the top of him)
                    in[0] = keptIn(e, p[0], in[0].add(e.position().subtract(him[0])));
                    him[0] = e.position();
                    Vec3 to = kk % 3 == 2 ? new Vec3(in[0].x, e.solidTightBox().maxY + 6, in[0].z) : in[0];
                    p[0].teleportTo(h.getLevel(), to.x, to.y, to.z, 0f, 0f);
                    p[0].setDeltaMovement(Vec3.ZERO);
                });
            }
            SolidTests.after(h, 38, () -> {
                try {
                    h.assertTrue(Solid.unstuckServer > was[0], "never moved out of him");
                } finally {
                    gone(h, e, p[0]);
                }
                h.succeed();
            });
        });
    }

    /** standing in a hollow of him with no way out (not in a part of him): moved out to open ground, unhurt */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "push_walled_kit")
    public void walledInByHimIsMovedOutToOpenGround(GameTestHelper h) {
        HollowbellEntity e = him(h, 0.5f, 542);
        SolidTests.after(h, 60, () -> {
            h.assertTrue(e.solidReady(), "he isn't solid yet");
            e.setStay(true);
            // (held where he is: a test player has no game of its own to keep up with him as he drifts)
            e.setFrozen(true);
            ServerPlayer p = someone(h, e.position().add(0, e.solidTightBox().getYsize() + 4, 0));
            h.assertTrue(!Solid.walledIn(e, p), "over him in the open counts as walled in");
            Vec3 at = hollowOf(e, p);
            if (at == null) {
                // (no hollow of him walls anyone in, as he stands now)
                gone(h, e, p);
                h.succeed();
                return;
            }
            p.teleportTo(h.getLevel(), at.x, at.y, at.z, 0f, 0f);
            float hp = p.getHealth();
            int moved = Solid.unstuckServer;
            // (out of the hollow at some tick, moved or with him moved off them; he may come back over them after,
            // and a test player has no game of its own to lift them as he does)
            boolean[] out = {false};
            for (int k = 1; k <= 25; k++)
                SolidTests.after(h, k, () -> {
                    if (!Solid.walledIn(e, p) && !Solid.inside(e, p, 0.08))
                        out[0] = true;
                });
            SolidTests.after(h, 26, () -> {
                try {
                    h.assertTrue(
                            out[0] || Solid.unstuckServer > moved,
                            "still walled in by him: " + at + " -> " + p.position() + " walled " + Solid.walledIn(e, p)
                                    + " depth " + Solid.depthIn(e, p.getBoundingBox(), 0.08, null, true));
                    // (his own moves may hit them; being moved out never hurts)
                    var by = p.getLastDamageSource();
                    h.assertTrue(
                            p.getHealth() >= hp - 0.01f || by != null && by.getEntity() == e,
                            "hurt being moved out: " + p.getHealth() + " by " + p.getLastDamageSource() + " at "
                                    + p.position());
                } finally {
                    gone(h, e, p);
                }
                h.succeed();
            });
        });
    }
}
