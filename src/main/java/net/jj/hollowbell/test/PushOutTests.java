package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.BellKit.after;
import static net.jj.hollowbell.test.BellKit.chunksFor;
import static net.jj.hollowbell.test.BellKit.bell;
import static net.jj.hollowbell.test.BellKit.done;
import static net.jj.hollowbell.test.BellKit.inHim;
import static net.jj.hollowbell.test.BellKit.onDome;
import static net.jj.hollowbell.test.BellKit.player;
import static net.jj.hollowbell.test.BellKit.runs;
import static net.jj.hollowbell.test.BellKit.spot;
import static net.jj.hollowbell.test.BellKit.whenQuiet;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.solid.Solid;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;

/**
 * Nobody stays inside him: a player put inside him (with no game of their own to move them, so only the server's
 * fallback check moves them) is out within a second, unhurt, at any size and in the middle of a move; a creature at once; put straight back in, a player goes up
 * onto him. Standing on his dome is left alone, and the server lets you be carried far.
 */
public class PushOutTests implements FabricGameTest {
    private static void outOfHim(GameTestHelper h, int slot, float size, int move) {
        int chunks = chunksFor(size);
        BlockPos at = spot(h, slot, chunks);
        HollowbellEntity e = bell(h, at, size);
        after(h, 30, () -> {
            e.setStay(true);
            whenQuiet(h, e, () -> {
                if (move != Moves.NONE) h.assertTrue(e.forceMove(move, null), "the move starts");
                Vec3 in = inHim(e);
                h.assertTrue(in != null, "no spot inside him at size " + size);
                ServerPlayer p = player(h, in);
                // (his own blows and stings aren't what this is about: he spares whoever holds his book)
                p.getInventory().add(new net.minecraft.world.item.ItemStack(net.jj.hollowbell.ModItems.CODEX));
                h.assertTrue(Solid.inside(e, p, 0.08), "the player wasn't put inside him at size " + size + ": " + in + runs(e, in.x, in.z, in.y - 2, in.y + 4));
                float hp = p.getHealth();
                // out within a second. Mid-move his parts keep coming at a test
                // player who (with no game of their own) never steps away: moved out again, they go up onto him, so a
                // little longer there
                // (and at double size he can't fit under the top of the world, so he never stops sinking and rising)
                // (the server's own check is the fallback, their game moves them out at once: it counts half a
                // second of being well inside him, holding the count on a tick his own breathing explains, so a
                // test player is given a second)
                int within = move == Moves.NONE && size <= 1 ? 21 : 30;
                boolean[] out = {false};
                for (int t = 1; t <= within; t++) after(h, t, () -> { if (!Solid.inside(e, p, 0.08)) out[0] = true; });
                after(h, within, () -> {
                    double deep = Solid.depthIn(e, p.getBoundingBox(), 0.08, null, true);
                    // (moved out by him, or his part moved off them: either way not left in there. Mid-move only being
                    // out counts: the move can come at them again straight away)
                    h.assertTrue(out[0] && (deep < 0.5 || within > 11),
                            "still inside him after " + within + " ticks at size " + size + ": " + in + " -> " + p.position()
                            + " (" + deep + " in)" + runs(e, p.getX(), p.getZ(), p.getY() - 2, p.getY() + 4));
                    h.assertTrue(p.getHealth() >= hp, "hurt getting out: " + p.getHealth());
                    done(h, e, at, chunks, p);
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "push_small")
    public void aPlayerInsideHimAtATenthIsPushedOut(GameTestHelper h) { outOfHim(h, 20, 0.1f, Moves.NONE); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "push_tiny")
    public void aPlayerInsideHimAtHisSmallestIsPushedOut(GameTestHelper h) { outOfHim(h, 21, 0.03f, Moves.NONE); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "push_full")
    public void aPlayerInsideHimAtFullSizeIsPushedOut(GameTestHelper h) { outOfHim(h, 22, 1f, Moves.NONE); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "push_double")
    public void aPlayerInsideHimAtDoubleSizeIsPushedOut(GameTestHelper h) { outOfHim(h, 23, 2f, Moves.NONE); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "push_move")
    public void aPlayerInsideHimMidArmSlamIsPushedOut(GameTestHelper h) { outOfHim(h, 24, 1f, Moves.SLAM); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "push_mob")
    public void aCreatureInsideHimIsMovedOutAtOnce(GameTestHelper h) {
        BlockPos at = spot(h, 25, chunksFor(0.5f));
        HollowbellEntity e = bell(h, at, 0.5f);
        after(h, 30, () -> {
            e.setStay(true);
            Vec3 in = inHim(e);
            h.assertTrue(in != null, "no spot inside him");
            Pig p = EntityType.PIG.create(h.getLevel());
            p.moveTo(in.x, in.y, in.z, 0f, 0f);
            p.setNoAi(true);
            h.getLevel().addFreshEntity(p);
            h.assertTrue(Solid.inside(e, p, 0.08), "the pig wasn't put inside him: " + in);
            after(h, 3, () -> {
                h.assertTrue(!Solid.inside(e, p, 0.08), "the pig is still inside him: " + p.position() + runs(e, p.getX(), p.getZ(), p.getY() - 2, p.getY() + 3));
                h.assertTrue(p.isAlive(), "the pig died getting out");
                done(h, e, at, chunksFor(0.5f), p);
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "push_again")
    public void putBackInsideHimAtOnceTheyGoUpOntoHim(GameTestHelper h) {
        BlockPos at = spot(h, 26, chunksFor(1f));
        HollowbellEntity e = bell(h, at, 1f);
        ServerPlayer[] p = new ServerPlayer[1];
        // 0 not yet in, 1 in the first time, 2 put back in, 3 gone up onto him
        int[] phase = {0}, ticks = {0};
        double[] backAt = {0};
        after(h, 20, () -> e.setStay(true));
        after(h, 21, () -> whenQuiet(h, e, () -> {
            Vec3 in = inHim(e);
            h.assertTrue(in != null, "nothing thick of him");
            p[0] = player(h, in);
            p[0].getInventory().add(new net.minecraft.world.item.ItemStack(net.jj.hollowbell.ModItems.CODEX));
            h.assertTrue(Solid.inside(e, p[0], 0.08), "the player wasn't put inside him: " + in + runs(e, in.x, in.z, in.y - 2, in.y + 4));
            phase[0] = 1;
        }));
        // (watched each tick: he breathes, so the moments they're moved out are looked for, not guessed)
        h.onEachTick(() -> {
            if (phase[0] == 0 || phase[0] == 3) return;
            ticks[0]++;
            if (phase[0] == 1) {
                h.assertTrue(ticks[0] <= 25, "not out the first time: " + p[0].position());
                if (Solid.inside(e, p[0], 0.08)) return;
                // straight back in, where that part of him is now
                Vec3 in = inHim(e);
                p[0].teleportTo(h.getLevel(), in.x, in.y, in.z, 0f, 0f);
                backAt[0] = in.y;
                phase[0] = 2;
                ticks[0] = 0;
                return;
            }
            h.assertTrue(ticks[0] <= 30, "the second time they didn't go up onto him: " + p[0].position());
            if (Solid.onTop(e, p[0], 0.9, 1.5) && !Solid.inside(e, p[0], 0.08) && p[0].getY() > backAt[0] + 1.0) phase[0] = 3;
        });
        after(h, 21 + 130 + 80, () -> {
            h.assertTrue(phase[0] == 3, "they never went up onto him (" + phase[0] + "): " + p[0].position());
            done(h, e, at, chunksFor(1f), p[0]);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "push_on_him")
    public void standingOnHisDomeIsLeftAlone(GameTestHelper h) {
        BlockPos at = spot(h, 27, chunksFor(1f));
        HollowbellEntity e = bell(h, at, 1f);
        after(h, 20, () -> e.setStay(true));
        after(h, 21, () -> whenQuiet(h, e, () -> {
            Vec3 top = onDome(e, 25, -20);
            h.assertTrue(top != null, "nothing of his dome there");
            Vec3 on = top.add(0, 0.02, 0);
            ServerPlayer p = player(h, on);
            p.getInventory().add(new net.minecraft.world.item.ItemStack(net.jj.hollowbell.ModItems.CODEX));
            h.assertTrue(Solid.onTop(e, p, 0.9, 1.5), "the player isn't standing on him: " + p.position());
            h.assertTrue(Solid.carryAllowance(p) > 10, "the server wouldn't let him carry you far: " + Solid.carryAllowance(p));
            after(h, 20, () -> {
                // (a test player isn't carried, their own game does that: as his dome breathes up under them they're
                // lifted back onto it, never shoved off it sideways)
                h.assertTrue(Math.hypot(p.getX() - on.x, p.getZ() - on.z) < 1.0 && p.getY() > on.y - 0.5,
                        "standing on him, they were moved: " + on + " -> " + p.position());
                double deep = Solid.depthIn(e, p.getBoundingBox(), 0.08, null, true);
                h.assertTrue(deep < 0.6, "standing on him, they ended up inside him (" + deep + " in)");
                done(h, e, at, chunksFor(1f), p);
            });
        }));
    }
}
