package net.jj.hollowbell.test;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.entity.Moves;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Every move eases back into his hovering and his drifting: he is stepped tick by tick through each move (with the
 * client's in-between frames too) and on after it, and as a move ends, or is cut short (a new move, stopped, falling
 * asleep, dying), no corner of him may move faster in a tick than he does drifting and swimming at his most, nor jolt
 * (change its step from one tick to the next) more than that, or than the move itself was doing just before.
 */
public class BlendOutTests implements FabricGameTest {
    /**
     * what one run measured, model blocks a tick: the most any corner moved in the move's last 30% (cut short: in
     * all of it), from its end on, the jolt from its end on, the jolt and speed in the move's own last few ticks, and
     * which part moved most when
     */
    record Jump(
            float move,
            float moveJolt,
            float after,
            float jolt,
            float ownJolt,
            float ownSpeed,
            String bone,
            int tick) {}

    /** the most he moves (and jolts) in a tick hovering, and drifting and turning, over a long while */
    static float[] baseline() {
        BellMotion b = new BellMotion();
        b.run(160);
        float idle = 0f, idleJolt = 0f;
        for (int i = 0; i < 240; i++) {
            b.step();
            idle = Math.max(idle, b.jump);
            idleJolt = Math.max(idleJolt, b.jolt);
        }
        BellMotion w = new BellMotion();
        float drift = 0f, driftJolt = 0f;
        for (int i = 0; i < 420; i++) {
            // up to his top speed, then round in a wide turn
            float a = i < 200 ? 0f : (i - 200) * 0.02f, v = 0.3f * Math.min(1f, i / 60f);
            w.vx = v * (float) Math.cos(a);
            w.vz = v * (float) Math.sin(a);
            w.step();
            if (i < 160) continue;
            drift = Math.max(drift, w.jump);
            driftJolt = Math.max(driftJolt, w.jolt);
        }
        // the lash (the new move a cut-short one gives way to) from hovering, up to its blow
        Jump lash = play(Moves.LASH, Moves.LASH_AT - 6, 1, () -> {}, new BellMotion());
        return new float[] {idle, drift, idleJolt, driftJolt, lash.move, lash.ownJolt};
    }

    /** a move from hovering at rest, played whole (or cut short at tick cut by what end does), then window ticks on */
    static Jump play(int move, int cut, int window, Runnable end, BellMotion b) {
        int len = Moves.length(move), stop = cut > 0 ? cut : len;
        b.run(160);
        b.move = move;
        b.arg = BellMotion.argFor(move);
        float inMove = 0f, inJolt = 0f, after = 0f, jolt = 0f, own = 0f, speed = 0f, all = 0f;
        String worst = "";
        int worstTick = 0;
        for (int t = 1; t <= stop; t++) {
            b.moveT = t;
            b.serverSide(move, t);
            if (t == stop) {
                // the move has ended (or been cut): the first pose without it
                b.move = Moves.NONE;
                b.moveT = 0;
                b.serverSide(Moves.NONE, 0);
                end.run();
            }
            b.step();
            if (t == stop) {
                jolt = b.jolt;
                after = b.jump;
                worst = b.bone;
            } else {
                all = Math.max(all, b.jump);
                if (t >= stop - 3) {
                    own = Math.max(own, b.jolt);
                    speed = Math.max(speed, b.jump);
                }
                if (cut <= 0 && t >= 0.7f * len) {
                    inMove = Math.max(inMove, b.jump);
                    inJolt = Math.max(inJolt, b.jolt);
                }
            }
        }
        for (int i = 1; i < window; i++) {
            if (b.in.dying >= 0f) b.in.dying++;
            if (b.in.sleep > 0f) b.in.sleep = Math.min(1f, b.in.sleep + 0.02f);
            if (b.move != Moves.NONE) b.moveT++;
            b.step();
            jolt = Math.max(jolt, b.jolt);
            if (b.jump > after) {
                after = b.jump;
                worst = b.bone;
                worstTick = i;
            }
        }
        return new Jump(cut > 0 ? all : inMove, inJolt, after, jolt, own, speed, worst, worstTick);
    }

    /** the moves to try (all his moves) */
    static List<Integer> moves() {
        List<Integer> l = new ArrayList<>();
        for (int m : Moves.ORDER) if (Moves.length(m) > 0) l.add(m);
        return l;
    }

    /**
     * the most any corner of him may move in a tick as a move ends: a little over his drifting and swimming (or what
     * the move itself was doing over its last 30%: a strand flung up in it is still coming down)
     */
    static float limit(float[] base) {
        return Math.max(base[0], base[1]) * 1.5f;
    }

    /** the most a corner's step may change from one tick to the next as a move lets go */
    static float joltLimit(float[] base) {
        return Math.max(base[2], base[3]) * 1.5f;
    }

    /**
     * cut short in the middle of a swing, he is never sent faster than the move had him going as it was cut, nor
     * jolted harder than it was jolting him; and a new move cutting in may move him as much as it does from rest
     */
    static float cutLimit(float[] base, Jump j, int move, boolean newMove) {
        float lim = Math.max(Math.max(limit(base) * 1.2f, j.ownSpeed * 1.25f), newMove ? base[4] * 1.25f : 0f);
        return whipped(move) || newMove ? Math.max(lim, limit(base) * WHIPPED) : lim;
    }

    static float cutJoltLimit(float[] base, Jump j, int move, boolean newMove) {
        float lim = Math.max(Math.max(joltLimit(base) * 1.2f, j.ownJolt * 1.5f), newMove ? base[5] * 1.5f : 0f);
        return whipped(move) || newMove ? Math.max(lim, joltLimit(base) * WHIPPED * 2.5f) : lim;
    }

    /**
     * Cut off in the middle of the lash's whip, the whirlpool's swirl or the dive's turn over, a strand flung right up
     * falls back and the pods on it turn over quickly as it does; and a new move cutting in swings its own strand out
     * at once: allowed this many times his drifting (they were up to twice that before the strands were let go of
     * smoothly)
     */
    static final float WHIPPED = 4.5f;

    static boolean whipped(int move) {
        return move == Moves.LASH || move == Moves.WHIRLPOOL || move == Moves.SKY_DIVE;
    }

    /** the ticks after a move's end that are its letting go */
    static final int WINDOW = 20;

    /** the cuts tried: a move, and the tick it is cut short at (in the middle of its biggest pose or swing) */
    static final int[][] CUTS = {
        {Moves.SWEEP, 30}, {Moves.SLAM, 40}, {Moves.SLAM, 49}, {Moves.LASH, 18}, {Moves.PULSE, 9},
        {Moves.DROP, 60}, {Moves.CURTAIN, 60}, {Moves.WRAP, 60}, {Moves.WHIRLPOOL, 90}, {Moves.WHIRLPOOL, 141},
        {Moves.DEEP_TOLL, 105}, {Moves.ARM_STORM, 60}, {Moves.SUN_LANCES, 60}, {Moves.UNDERTOW, 58},
        {Moves.EGG_RAIN, 50}, {Moves.POD_BURST, 20}, {Moves.STINGER_STORM, 60}, {Moves.SKY_DIVE, 100},
        {Moves.GRAB, 120}, {Moves.FLASH, 19}, {Moves.VOLLEY, 24}
    };

    static final String[] HOW = {"new move", "stopped", "asleep", "dying"};

    static List<String> report(List<String> fails, float[] base) {
        float lim = limit(base), joltLim = joltLimit(base);
        List<String> rows = new ArrayList<>();
        rows.add(String.format(
                "baseline: hovering %.2f b/t jolt %.2f; drifting %.2f b/t jolt %.2f; a lash from rest %.2f jolt %.2f",
                base[0], base[2], base[1], base[3], base[4], base[5]));
        rows.add(String.format("limits: %.2f b/t, jolt %.2f", lim, joltLim));
        rows.add("move\tlen\tin_move_b/t\tafter_b/t\tjolt\tits_own_jolt\tworst_bone\tat_tick");
        for (int m : moves()) {
            Jump j = play(m, 0, WINDOW, () -> {}, new BellMotion());
            rows.add(String.format(
                    "%s\t%d\t%.2f\t%.2f\t%.2f\t%.2f\t%s\t%d", Moves.NAMES[m], Moves.length(m), j.move, j.after,
                    j.jolt, j.ownJolt, j.bone, j.tick));
            if (j.after > Math.max(lim, j.move * 1.2f))
                fails.add(Moves.NAMES[m] + " moves " + j.after + " a tick as it ends");
            if (j.jolt > Math.max(Math.max(joltLim, j.ownJolt * 1.5f), j.moveJolt * 1.25f))
                fails.add(Moves.NAMES[m] + " jolts " + j.jolt + " as it ends");
        }
        rows.add("cut short\tits_speed\tafter_b/t\tjolt\tits_own_jolt\tworst_bone\tat_tick");
        for (int[] c : CUTS) {
            for (int k = 0; k < HOW.length; k++) {
                // (he never starts a move until the last has finished or been stopped: a new one cutting straight into
                // a whirlpool can't happen)
                if (k == 0 && c[0] == Moves.WHIRLPOOL) continue;
                BellMotion b = new BellMotion();
                final int kk = k;
                // (a new move: only until its own blow comes in, which is meant to be quick)
                int window = k == 0 ? Moves.LASH_AT - 6 : WINDOW;
                Jump j = play(c[0], c[1], window, () -> {
                    switch (kk) {
                        case 0 -> {
                            b.move = Moves.LASH;
                            b.arg = 30;
                            b.moveT = 1;
                        }
                        case 2 -> b.in.sleep = 0.02f;
                        case 3 -> b.in.dying = 0f;
                        default -> {}
                    }
                }, b);
                rows.add(String.format(
                        "%s at %d (%s)\t%.2f\t%.2f\t%.2f\t%.2f\t%s\t%d", Moves.NAMES[c[0]], c[1], HOW[k], j.ownSpeed,
                        j.after, j.jolt, j.ownJolt, j.bone, j.tick));
                String what = Moves.NAMES[c[0]] + " cut short at " + c[1] + " (" + HOW[k] + ")";
                if (j.after > cutLimit(base, j, c[0], k == 0)) fails.add(what + " moves " + j.after + " a tick");
                if (j.jolt > cutJoltLimit(base, j, c[0], k == 0)) fails.add(what + " jolts " + j.jolt);
            }
        }
        return rows;
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "blend_out")
    public void everyMoveEasesBackIntoHisHoverAndDrift(GameTestHelper h) {
        List<String> fails = new ArrayList<>();
        List<String> rows = report(fails, baseline());
        for (String r : rows) HollowbellMod.LOG.info("blend out: {}", r);
        h.assertTrue(
                fails.isEmpty(),
                fails.size() + " too sudden: " + String.join("; ", fails.subList(0, Math.min(4, fails.size()))));
        h.succeed();
    }
}
