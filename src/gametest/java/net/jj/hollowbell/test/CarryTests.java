package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.*;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.entity.Seat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Riding his crown, being carried up, and leaving the game while he has you. */
public class CarryTests implements FabricGameTest {
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
            h.assertTrue(
                    pl[0].position().distanceTo(e.crownWorld()) < 3,
                    "not on his crown: " + pl[0].position() + " crown " + e.crownWorld());
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

    /** after a player left the game while he had them: on the ground beside him, let go, falling gently */
    private static void checkPutDown(GameTestHelper h, HollowbellEntity e, ServerPlayer p, String how) {
        h.assertTrue(p.getVehicle() == null, how + ": still riding something");
        double over = p.getY() - e.groundAt(p.getX(), p.getZ());
        h.assertTrue(over > -0.2 && over < 1.0, how + ": saved " + over + " over the ground");
        h.assertTrue(
                e.horiz(p.position()) > e.bellRadius(),
                how + ": put down under him, " + e.horiz(p.position()) + " from his middle");
        h.assertTrue(
                h.getLevel().noCollision(p, p.getBoundingBox()) && !h.getLevel().containsAnyLiquid(p.getBoundingBox()),
                how + ": put down inside blocks or water");
        h.assertTrue(p.hasEffect(MobEffects.SLOW_FALLING), how + ": no slow falling");
        h.assertTrue(
                e.rider() != p
                        && !e.moves().caught(p)
                        && e.moves().grabbed() != p
                        && e.moves().wrapped() != p
                        && !e.moves().isInside(p),
                how + ": he still has them");
        HollowbellMod.LOG.info(
                "leaving while {}: saved {} over the ground, {} from his middle",
                how,
                String.format("%.2f", over),
                String.format("%.1f", e.horiz(p.position())));
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
            if (st[0] == 0
                    && kind.equals("wrapped by an arm")
                    && e.moveNow() == Moves.WRAP
                    && e.moves().t() == Moves.WRAP_REACH - 1)
                p.teleportTo(
                        h.getLevel(),
                        e.armTipWorld(e.moveArg()).x,
                        e.armTipWorld(e.moveArg()).y,
                        e.armTipWorld(e.moveArg()).z,
                        0f,
                        0f);
            if (st[0] == 0) {
                boolean has = switch (kind) {
                    case "riding" -> e.rider() == p && p.getY() > e.getY() + 50 * s;
                    case "carried up" -> e.moves().grabbed() == p && p.getY() > e.groundAt(p.getX(), p.getZ()) + 20 * s;
                    case "held by a strand" ->
                        e.moves().grabbed() == p && e.moves().t() > Moves.REACH + 20;
                    case "inside the dome" -> e.moves().isInside(p);
                    default -> e.moves().wrapped() == p && e.moves().t() > Moves.WRAP_REACH + 10;
                };
                h.assertTrue(h.getTick() < 20 + 800, "he never got hold of the player (" + kind + ")");
                if (!has) return;
                h.assertTrue(p.getVehicle() instanceof Seat, kind + ": not on one of his seats");
                drop(p);
                checkPutDown(h, e, p, kind);
                st[0] = 1;
                return;
            }
            // a strand that was carrying them up goes back down and hangs again
            if (++st[1] > 5 && (!kind.equals("carried up") || e.moveNow() == Moves.NONE)) {
                h.assertTrue(
                        e.moveNow() != Moves.GRAB || kind.equals("inside the dome"),
                        kind + ": the strand is still holding on");
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
    public void leavingWhileOnHisCrownPutsYouDown(GameTestHelper h) {
        leaveCase(h, "riding", 140);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200, batch = "leave_carried")
    public void leavingWhileBeingCarriedUpPutsYouDown(GameTestHelper h) {
        leaveCase(h, "carried up", 141);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "leave_grabbed")
    public void leavingWhileAStrandHoldsYouPutsYouDown(GameTestHelper h) {
        leaveCase(h, "held by a strand", 142);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "leave_wrapped")
    public void leavingWhileAnArmHoldsYouPutsYouDown(GameTestHelper h) {
        leaveCase(h, "wrapped by an arm", 143);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "leave_inside")
    public void leavingWhileInsideHisDomePutsYouDown(GameTestHelper h) {
        leaveCase(h, "inside the dome", 144);
    }

    /**
     * The body of a rider as it really is: a sitting rider's legs go out in front, but the game keeps their box
     * standing and sinks it below the seat (0.6 for a player), so the box's bottom is taken up to the seat.
     */
    private static AABB sitting(Entity e) {
        AABB b = e.getBoundingBox();
        if (e.getVehicle() != null)
            b = b.setMinY(Math.max(b.minY, e.getVehicle().getY()));
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
            if (out[i]) {
                len += d[i].length();
                rest += ch.restLen[i];
            } else in++;
        }
        for (int i = 0; i + 1 < d.length; i++)
            if (out[i] && out[i + 1]) worst = Math.max(worst, (float) Math.toDegrees(d[i].angle(d[i + 1])));
        return new float[] {worst, rest > 0f ? len / rest : 1f, in};
    }

    /** keeps the chunks round him loaded, n chunks each way, for a big one that moves about */
    private static void forceAround(GameTestHelper h, HollowbellEntity e, int n) {
        int x = e.getBlockX(), z = e.getBlockZ();
        for (int cx = (x >> 4) - n; cx <= (x >> 4) + n; cx++)
            for (int cz = (z >> 4) - n; cz <= (z >> 4) + n; cz++) {
                h.getLevel().setChunkForced(cx, cz, true);
                h.getLevel().getChunk(cx, cz);
            }
        runsEntities(h, x, z, n - 2);
    }

    private static void unforceAround(GameTestHelper h, int x, int z, int n) {
        for (int cx = (x >> 4) - n; cx <= (x >> 4) + n; cx++)
            for (int cz = (z >> 4) - n; cz <= (z >> 4) + n; cz++) h.getLevel().setChunkForced(cx, cz, false);
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
        int[] st = {0, 0, -1}; // 0 before the grab, 1 carried, 2 on the crown, 3 done; ticks carried; strand
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
                look[0] = Math.max(look[0], l[0]);
                look[1] = Math.max(look[1], l[1]);
                look[2] = Math.min(look[2], l[1]);
                look[3] = Math.max(look[3], l[2]);
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
            if (st[0] == 0) {
                st[0] = 1;
                st[2] = e.moveArg();
            }
            st[1]++;
            double step = now.distanceTo(prev[0]);
            worst[0] = Math.max(worst[0], step);
            h.assertTrue(
                    step <= limit,
                    String.format(
                            "tick %d of the carry: the player moved %.2f blocks in one tick (limit %.2f)",
                            st[1], step, limit));
            if (held) {
                double gap = e.strandTipWorld(st[2]).distanceTo(now);
                worst[1] = Math.max(worst[1], gap);
                h.assertTrue(
                        gap <= near,
                        String.format(
                                "tick %d of the carry: the strand's end is %.2f blocks from the player (limit %.2f)",
                                st[1], gap, near));
                h.assertTrue(p.getVehicle() instanceof Seat, "the player isn't held on a seat");
            } else h.assertTrue(onCrown, "the strand let go of the player " + st[1] + " ticks into the carry");
            // nothing of him inside them, now or anywhere on the way from last tick
            AABB box = sitting(p);
            Vec3 d = now.subtract(prev[0]);
            int k = Math.max(1, (int) Math.ceil(d.length() / (0.2 * Math.max(0.25, s))));
            for (int i = 0; i <= k; i++) {
                AABB b = box.move(d.scale(-(double) (k - i) / k));
                String part = e.partIn(b, st[2]);
                h.assertTrue(
                        part == null,
                        String.format(
                                "tick %d of the carry: the player is inside his %s at %s", st[1], part, b.getCenter()));
            }
            prev[0] = now;
            h.assertTrue(st[1] < 20 * 60, "the carry never finished");
            if (onCrown) {
                st[0] = 2;
                h.assertTrue(
                        e.ridden() && p.getVehicle() instanceof Seat seat && seat.mode() == Seat.CROWN,
                        "not sitting on the crown seat");
                double off = p.getVehicle().position().distanceTo(e.crownWorld());
                h.assertTrue(off < 0.05, "the crown seat isn't on the crown: " + off);
                HollowbellMod.LOG.info(
                        "carry at size {}: {} ticks, biggest step {} (limit {}), biggest gap to the strand's end {}"
                                + " (limit {}), nothing of him in the way",
                        s,
                        st[1],
                        String.format("%.3f", worst[0]),
                        String.format("%.2f", limit),
                        String.format("%.3f", worst[1]),
                        String.format("%.2f", near));
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
                HollowbellMod.LOG.info(
                        "carry at size {}: the strand's biggest bend between two pieces {} degrees, stretched {} to {}"
                                + " times its length (up to {} of its 6 pieces drawn in at a time)",
                        s,
                        String.format("%.0f", look[0]),
                        String.format("%.2f", look[2]),
                        String.format("%.2f", look[1]),
                        (int) look[3]);
                // it reads as one smooth rope: no sharp kink between two pieces, and not pulled out much longer than it
                // is
                h.assertTrue(look[0] <= 75f, "the strand bent " + look[0] + " degrees between two pieces");
                h.assertTrue(
                        look[1] <= 1.25f && look[2] >= 0.6f,
                        "the strand was stretched " + look[2] + " to " + look[1] + " times its length");
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
        int[] st = {0, 0, -1}; // 0 on the crown, 1 carried down, 2 on the ground, 3 done; ticks carried; strand
        h.runAfterDelay(20, () -> {
            pl[0] = player(h, e.position().add(40 * s + 5, 0, 0));
            h.assertTrue(e.possess(pl[0]), "couldn't get on him");
        });
        // up a way first, so he has to come down for it
        for (int i = 25; i < 85; i += 5) h.runAfterDelay(i, () -> e.drive(pl[0], 0f, 0f, 0f, 1));
        h.runAfterDelay(90, () -> {
            h.assertTrue(
                    e.getY() > e.groundAt(e.getX(), e.getZ()) + 3 * s + 1,
                    "he didn't go up: y " + e.getY() + " ground " + e.groundAt(e.getX(), e.getZ()) + " rider "
                            + (e.rider() == pl[0]) + " asleep " + e.asleep() + " sunk " + e.sunk() + " move "
                            + e.moveNow() + " setting down " + e.moves().settingDown());
            e.setMeDown(pl[0]);
            h.assertTrue(e.moves().settingDown(), "he isn't setting the player down");
        });
        h.onEachTick(() -> {
            ServerPlayer p = pl[0];
            if (p == null || h.getTick() < 90) return;
            if (st[2] >= 0 && e.moveNow() == Moves.GRAB) {
                float[] l = ropeLook(e, st[2]);
                look[0] = Math.max(look[0], l[0]);
                look[1] = Math.max(look[1], l[1]);
                look[2] = Math.min(look[2], l[1]);
                look[3] = Math.max(look[3], l[2]);
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
            h.assertTrue(
                    step <= limit,
                    String.format(
                            "tick %d of the set-down: the player moved %.2f blocks in one tick (limit %.2f)",
                            st[1], step, limit));
            if (held) {
                double gap = e.strandTipWorld(st[2]).distanceTo(now);
                worst[1] = Math.max(worst[1], gap);
                h.assertTrue(
                        gap <= near,
                        String.format(
                                "tick %d of the set-down: the strand's end is %.2f blocks from the player (limit %.2f)",
                                st[1], gap, near));
            }
            AABB box = held ? sitting(p) : p.getBoundingBox();
            Vec3 d = now.subtract(prev[0]);
            int k = Math.max(1, (int) Math.ceil(d.length() / (0.2 * Math.max(0.25, s))));
            for (int i = 0; i <= k; i++) {
                AABB b = box.move(d.scale(-(double) (k - i) / k));
                String part = e.partIn(b, st[2]);
                h.assertTrue(
                        part == null,
                        String.format(
                                "tick %d of the set-down: the player is inside his %s at %s",
                                st[1], part, b.getCenter()));
            }
            prev[0] = now;
            h.assertTrue(st[1] < 20 * 60, "the set-down never finished");
            if (!held) {
                // let go: standing on the ground beside him
                h.assertTrue(!p.isPassenger() && e.rider() == null, "not let go properly");
                double over = p.getY() - e.groundAt(p.getX(), p.getZ());
                h.assertTrue(over > -0.2 && over < 1.0, "set down " + over + " over the ground");
                st[0] = 2;
                HollowbellMod.LOG.info(
                        "set down at size {}: {} ticks, biggest step {} (limit {}), biggest gap to the strand's end {}"
                                + " (limit {}), nothing of him in the way, put down {} over the ground",
                        s,
                        st[1],
                        String.format("%.3f", worst[0]),
                        String.format("%.2f", limit),
                        String.format("%.3f", worst[1]),
                        String.format("%.2f", near),
                        String.format("%.2f", over));
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(st[0] >= 2, "not down yet");
            h.assertTrue(e.moveNow() == Moves.NONE, "the strand is still busy");
            if (st[0] == 2) {
                st[0] = 3;
                HollowbellMod.LOG.info(
                        "set down at size {}: the strand's biggest bend between two pieces {} degrees, stretched {} to"
                                + " {} times its length",
                        s,
                        String.format("%.0f", look[0]),
                        String.format("%.2f", look[2]),
                        String.format("%.2f", look[1]));
                h.assertTrue(look[0] <= 75f, "the strand bent " + look[0] + " degrees between two pieces");
                h.assertTrue(
                        look[1] <= 1.25f && look[2] >= 0.6f,
                        "the strand was stretched " + look[2] + " to " + look[1] + " times its length");
                drop(pl[0]);
                release(h, e);
                unforceAround(h, ox, oz, n);
            }
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1400, batch = "setdown_small")
    public void setDownFromTheCrownSmall(GameTestHelper h) {
        setDownCase(h, 0.1f, 145);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1800, batch = "setdown_mid")
    public void setDownFromTheCrownMid(GameTestHelper h) {
        setDownCase(h, 0.3f, 146);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 2600, batch = "setdown_full")
    public void setDownFromTheCrownFullSize(GameTestHelper h) {
        setDownCase(h, 1.0f, 147);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200, batch = "carry_small")
    public void carriedUpOntoTheCrownSmall(GameTestHelper h) {
        carriedUp(h, 0.1f, 130);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1400, batch = "carry_mid")
    public void carriedUpOntoTheCrownMid(GameTestHelper h) {
        carriedUp(h, 0.3f, 131);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 2400, batch = "carry_full")
    public void carriedUpOntoTheCrownFullSize(GameTestHelper h) {
        carriedUp(h, 1.0f, 133);
    }

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
                h.assertTrue(
                        !p.isPassenger() && e.moves().grabbed() == null && e.rider() == null, "it took hold anyway");
                h.assertTrue(e.comingForSomebody(), "he gave up on the player");
                st[0] = 2;
            } else if (st[0] == 2 && e.moves().carryingUp()) st[0] = 3; // and he tries again
        });
        h.succeedWhen(() -> {
            h.assertTrue(st[0] == 3, "he didn't try again");
            e.stopFetch();
            drop(pl[0]);
            release(h, e);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "carry_walk_off")
    public void theCarryMissesIfYouWalkOff(GameTestHelper h) {
        carryCalledOff(h, false, 134);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "carry_hurt")
    public void theCarryPullsBackIfYouAreHurt(GameTestHelper h) {
        carryCalledOff(h, true, 135);
    }
}
