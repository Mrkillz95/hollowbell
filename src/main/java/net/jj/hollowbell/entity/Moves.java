package net.jj.hollowbell.entity;

import net.jj.hollowbell.rig.BellRig;
import net.jj.hollowbell.rig.BellState;
import net.minecraft.util.Mth;

/**
 * His moves: their names, how long each takes, and how each one moves his body at a given moment. This part is
 * shared by both sides so the client can draw a move from nothing more than which move, when it started and at what.
 * What each move actually does to the world is in {@link BellMoves}.
 */
public final class Moves {
    private Moves() {}

    public static final int NONE = 0, GRAB = 1, HARVEST = 2, CURTAIN = 3, SWEEP = 4, SLAM = 5, WRAP = 6, PULSE = 7, DROP = 8, SHED = 9;
    /** names used by the commands, the book and the language file */
    public static final String[] NAMES = {"none", "grab", "harvest", "curtain", "sweep", "arm_slam", "arm_wrap", "pulse_wave", "drop", "shed"};

    /** how long a move lasts, in ticks (grab and harvest can end early, when the strand is hit or lets go) */
    public static int length(int move) {
        return switch (move) {
            case GRAB, HARVEST -> REACH + LIFT + INTO;
            case CURTAIN -> 130;
            case SWEEP -> 70;
            case SLAM -> 76;
            case WRAP -> 130;
            case PULSE -> 44;
            case DROP -> DROP_FALL + DROP_DOWN + DROP_RISE;
            case SHED -> 46;
            default -> 0;
        };
    }

    public static final int REACH = 30, LIFT = 190, INTO = 24;
    public static final int DROP_FALL = 24, DROP_DOWN = 130, DROP_RISE = 150;
    /** when in the pulse wave the shock goes out */
    public static final int PULSE_AT = 12;
    /** when the egg clumps come off */
    public static final int SHED_AT = 22;
    public static final int WRAP_REACH = 20, WRAP_HOLD = 90;
    public static final int CURTAIN_CLOSE = 34, CURTAIN_HOLD = 70;

    public static int byName(String n) {
        for (int i = 1; i < NAMES.length; i++) if (NAMES[i].equals(n)) return i;
        return -1;
    }

    private static float smooth(float k) { k = Mth.clamp(k, 0f, 1f); return k * k * (3 - 2 * k); }

    /**
     * Puts the move's shape into the pose state. t = ticks into the move, arg = which arm or strand,
     * (tx, ty, tz) = what it is aimed at, in model space. grabLift is the server's own count for grabbing
     * (it can stop and start), or -1 to work it out from t.
     */
    public static void pose(BellRig rig, BellState st, int move, float t, int arg, float tx, float ty, float tz, float liftOverride) {
        st.slamArm = -1; st.wrapArm = -1; st.grabStrand = -1; st.curtain = 0f; st.sweep = 0f;
        switch (move) {
            case GRAB, HARVEST -> {
                if (arg < 0 || arg >= rig.strands.length) return;
                st.grabStrand = arg;
                st.grabReach = smooth(t / REACH);
                float lift = liftOverride >= 0f ? liftOverride : Mth.clamp((t - REACH) / LIFT, 0f, 1f);
                st.grabLift = lift;
                st.grabReach *= 1f - 0.7f * lift;          // it comes back to hanging straight as it pulls up
                st.grabX = tx; st.grabY = ty; st.grabZ = tz;
            }
            case CURTAIN -> {
                float k = t < CURTAIN_CLOSE ? smooth(t / CURTAIN_CLOSE) : t < CURTAIN_CLOSE + CURTAIN_HOLD ? 1f
                        : 1f - smooth((t - CURTAIN_CLOSE - CURTAIN_HOLD) / (length(CURTAIN) - CURTAIN_CLOSE - CURTAIN_HOLD));
                // pulled tight in the middle of the hold
                if (t > CURTAIN_CLOSE && t < CURTAIN_CLOSE + CURTAIN_HOLD) k += 0.15f * (float) Math.sin((t - CURTAIN_CLOSE) * 0.3f);
                st.curtain = k; st.curtainX = tx; st.curtainZ = tz;
            }
            case SWEEP -> {
                float k = t / length(SWEEP);
                // wound back, then swung right across, then settling
                float s = k < 0.3f ? -smooth(k / 0.3f) : k < 0.6f ? Mth.lerp(smooth((k - 0.3f) / 0.3f), -1f, 1.2f) : 1.2f * (1f - smooth((k - 0.6f) / 0.4f));
                st.sweep = s;
                st.sweepDir = (float) Math.atan2(tz, tx);
            }
            case SLAM -> { st.slamArm = arg; st.slamT = Mth.clamp(t / length(SLAM), 0f, 1f); }
            case WRAP -> {
                st.wrapArm = arg;
                st.wrapAmt = t < WRAP_REACH ? smooth(t / WRAP_REACH) : t < WRAP_REACH + WRAP_HOLD ? 1f
                        : 1f - smooth((t - WRAP_REACH - WRAP_HOLD) / (length(WRAP) - WRAP_REACH - WRAP_HOLD));
                st.wrapX = tx; st.wrapY = ty; st.wrapZ = tz;
            }
            default -> {}
        }
    }

    /** how far down the bell is in the drop, 0-1 */
    public static float drop(int move, float t) {
        if (move != DROP) return 0f;
        if (t < DROP_FALL) { float k = t / DROP_FALL; return k * k; }
        if (t < DROP_FALL + DROP_DOWN) return 1f;
        return 1f - smooth((t - DROP_FALL - DROP_DOWN) / DROP_RISE);
    }

    /** the bell's squeeze for a pulse that started age ticks ago, at strength power */
    public static float pulseCurve(float age, float power) {
        if (age < 0f || age > 34f) return 0f;
        float k = age < 7f ? smooth(age / 7f) : 1f - smooth((age - 7f) / 27f);
        return k * power;
    }
}
