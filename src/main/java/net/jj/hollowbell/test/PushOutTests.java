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
 * Nobody stays inside him: a player put inside him (with no game of their own to move them) is out within half a
 * second, unhurt, at any size and in the middle of a move; a creature at once; put straight back in, a player goes up
 * onto him. Standing on his dome is left alone, and the server lets you be carried far.
 */
public class PushOutTests implements FabricGameTest {
    private static void outOfHim(GameTestHelper h, int slot, float size, int move) {
        int chunks = chunksFor(size);
        BlockPos at = spot(h, slot, chunks);
        HollowbellEntity e = bell(h, at, size);
        after(h, 30, () -> {
            e.setStay(true);
            if (move != Moves.NONE) h.assertTrue(e.forceMove(move, null), "the move starts");
            after(h, 10, () -> {
                Vec3 in = inHim(e);
                h.assertTrue(in != null, "no spot inside him at size " + size);
                ServerPlayer p = player(h, in);
                // (his own blows and stings aren't what this is about: he spares whoever holds his book)
                p.getInventory().add(new net.minecraft.world.item.ItemStack(net.jj.hollowbell.ModItems.CODEX));
                h.assertTrue(Solid.inside(e, p, 0.08), "the player wasn't put inside him at size " + size + ": " + in + runs(e, in.x, in.z, in.y - 2, in.y + 4));
                float hp = p.getHealth();
                // out within half a second (and the tick he moves them in), and not back in deep: he breathes and
                // moves, so his parts can brush a test player who (with no game of their own) never steps away
                boolean[] out = {false};
                for (int t = 1; t <= 11; t++) after(h, t, () -> { if (!Solid.inside(e, p, 0.08)) out[0] = true; });
                // (mid-move his parts keep coming at a test player who never steps away: moved out again, they go up
                // onto him, so a little longer there)
                after(h, move == Moves.NONE ? 11 : 30, () -> {
                    double deep = Solid.depthIn(e, p.getBoundingBox(), 0.08, null, true);
                    h.assertTrue(out[0] && deep < 0.5, "still inside him after half a second at size " + size + ": " + in + " -> " + p.position()
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
    public void aPlayerInsideHimMidPulseWaveIsPushedOut(GameTestHelper h) { outOfHim(h, 24, 0.5f, Moves.PULSE); }

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

    /** a spot in the top of his dome, past a step in (a step in, he'd only lift them up), as he is now; or null */
    static Vec3 inHisDome(HollowbellEntity e) {
        Vec3 over = onDome(e, 30, 30);
        if (over == null) return null;
        Solid.Runs r = Solid.column(e, over.x, over.z, e.getY(), e.getY() + 260 * e.bellScale(), false, new Solid.Runs());
        double down = e.solidStep() + 0.3;
        int j = -1;
        for (int k = 0; k < r.n; k++) if (r.top[k] - r.bot[k] >= down + 0.5 && (j < 0 || r.top[k] > r.top[j])) j = k;
        return j < 0 ? null : new Vec3(over.x, r.top[j] - down, over.z);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "push_again")
    public void putBackInsideHimAtOnceTheyGoUpOntoHim(GameTestHelper h) {
        BlockPos at = spot(h, 26, chunksFor(1f));
        HollowbellEntity e = bell(h, at, 1f);
        ServerPlayer[] p = new ServerPlayer[1];
        boolean[] upOnHim = {false};
        after(h, 40, () -> {
            e.setStay(true);
            Vec3 in = inHisDome(e);
            h.assertTrue(in != null, "nothing thick of his dome there");
            p[0] = player(h, in);
            h.assertTrue(Solid.inside(e, p[0], 0.08), "the player wasn't put inside him: " + in + runs(e, in.x, in.z, in.y - 2, in.y + 4));
        });
        after(h, 51, () -> {
            h.assertTrue(!Solid.inside(e, p[0], 0.08), "not out the first time: " + p[0].position());
            Vec3 in = inHisDome(e);
            p[0].teleportTo(h.getLevel(), in.x, in.y, in.z, 0f, 0f);
        });
        // (watched each tick: he breathes, so the moment they're moved out is looked for, not guessed)
        h.onEachTick(() -> {
            if (p[0] != null && !upOnHim[0] && Solid.onTop(e, p[0], 0.9, 1.5) && !Solid.inside(e, p[0], 0.08)) upOnHim[0] = true;
            if (p[0] != null && Boolean.getBoolean("hollowbell.soliddbg"))
                net.jj.hollowbell.HollowbellMod.LOG.info("AGAINDBG {} at {} he {} inside {} onTop {} unstuck {}", h.getTick(), p[0].position(), e.position(),
                        Solid.inside(e, p[0], 0.08), Solid.onTop(e, p[0], 0.9, 1.5), Solid.unstuckServer);
        });
        after(h, 52, () -> upOnHim[0] = false);
        after(h, 52 + 40, () -> {
            Solid.Runs r = Solid.column(e, p[0].getX(), p[0].getZ(), p[0].getY() + 0.3, p[0].getY() + 1.8, false, new Solid.Runs());
            h.assertTrue(r.n == 0, "not out the second time: " + p[0].position());
            h.assertTrue(upOnHim[0], "the second time they didn't go up onto him: " + p[0].position());
            done(h, e, at, chunksFor(1f), p[0]);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "push_on_him")
    public void standingOnHisDomeIsLeftAlone(GameTestHelper h) {
        BlockPos at = spot(h, 27, chunksFor(1f));
        HollowbellEntity e = bell(h, at, 1f);
        after(h, 40, () -> {
            e.setStay(true);
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
                h.assertTrue(!Solid.inside(e, p, 0.3), "standing on him, they ended up inside him");
                done(h, e, at, chunksFor(1f), p);
            });
        });
    }
}
