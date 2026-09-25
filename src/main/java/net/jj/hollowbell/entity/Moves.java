package net.jj.hollowbell.entity;

import net.jj.hollowbell.rig.BellAnim;
import net.jj.hollowbell.rig.BellRig;
import net.jj.hollowbell.rig.BellState;
import net.minecraft.util.Mth;

/**
 * His moves: their names, how strong each is (light, medium, heavy), how long each takes, and how each one moves
 * his body at a given moment. This part is shared by both sides so the client can draw a move from nothing more
 * than which move, when it started and at what. What each move actually does to the world is in {@link BellMoves}.
 */
public final class Moves {
    private Moves() {}

    public static final int NONE = 0, GRAB = 1, HARVEST = 2, CURTAIN = 3, SWEEP = 4, SLAM = 5, WRAP = 6, PULSE = 7, DROP = 8, SHED = 9,
            VOLLEY = 10, LASH = 11, FLASH = 12, SPORES = 13, POD_BURST = 14, EGG_RAIN = 15,
            WHIRLPOOL = 16, SKY_DIVE = 17, DEEP_TOLL = 18, ARM_STORM = 19, STINGER_STORM = 20, SUN_LANCES = 21, UNDERTOW = 22;
    /** names used by the commands, the book and the language file */
    public static final String[] NAMES = {"none", "grab", "harvest", "curtain", "sweep", "arm_slam", "arm_wrap", "pulse_wave", "drop", "shed",
            "sting_volley", "strand_lash", "glow_flash", "spore_cloud", "pod_burst", "egg_rain",
            "whirlpool", "sky_dive", "deep_toll", "arm_storm", "stinger_storm", "sun_lances", "undertow"};

    public static final int LIGHT = 0, MEDIUM = 1, HEAVY = 2;
    public static final String[] TIER_NAMES = {"light", "medium", "heavy"};

    /** light: quick and cheap, used often. Medium: a clear warning, then a real hit. Heavy: rare, a long wind-up, huge */
    public static int tier(int move) {
        return switch (move) {
            case GRAB, HARVEST, VOLLEY, LASH, FLASH -> LIGHT;
            case DROP, WHIRLPOOL, SKY_DIVE, DEEP_TOLL, ARM_STORM, STINGER_STORM, SUN_LANCES, UNDERTOW -> HEAVY;
            default -> MEDIUM;
        };
    }

    /** every move, light ones first, the order the book and the being-him keys list them in */
    public static final int[] ORDER = {GRAB, HARVEST, VOLLEY, LASH, FLASH,
            CURTAIN, SWEEP, SLAM, WRAP, PULSE, SHED, SPORES, POD_BURST, EGG_RAIN,
            DROP, WHIRLPOOL, SKY_DIVE, DEEP_TOLL, ARM_STORM, STINGER_STORM, SUN_LANCES, UNDERTOW};

    /** how long a move lasts, in ticks (grab and harvest can end early, when the strand is hit or lets go; the dive when he lands) */
    public static int length(int move) {
        return switch (move) {
            case GRAB, HARVEST -> REACH + LIFT + INTO;
            case CURTAIN -> 130;
            case SWEEP -> 70;
            case SLAM -> 76;
            case WRAP -> 130;
            case PULSE -> 44;
            case DROP -> DROP_WIND + DROP_FALL + DROP_DOWN + DROP_RISE;
            case SHED -> 46;
            case VOLLEY -> 44;
            case LASH -> 36;
            case FLASH -> 40;
            case SPORES -> 64;
            case POD_BURST -> 54;
            case EGG_RAIN -> 72;
            case WHIRLPOOL -> 170;
            case SKY_DIVE -> DIVE_CLIMB + DIVE_TURN + DIVE_FALL + DIVE_BACK;
            case DEEP_TOLL -> 150;
            case ARM_STORM -> 150;
            case STINGER_STORM -> 140;
            case SUN_LANCES -> 150;
            case UNDERTOW -> 120;
            default -> 0;
        };
    }

    public static final int REACH = 30, LIFT = 190, INTO = 24;
    /** the drop: the warning (the bell lifts and glows), the fall, down, and back up */
    public static final int DROP_WIND = 30, DROP_FALL = 24, DROP_DOWN = 130, DROP_RISE = 150;
    /** when in the pulse wave the shock goes out */
    public static final int PULSE_AT = 12;
    /** when the egg clumps come off */
    public static final int SHED_AT = 22;
    public static final int WRAP_REACH = 20, WRAP_HOLD = 90;
    public static final int CURTAIN_CLOSE = 34, CURTAIN_HOLD = 70;
    /** the sting volley's three flicks */
    public static final int VOLLEY_AT = 14;
    /** the strand lash: when it hits */
    public static final int LASH_AT = 18;
    /** the glow flash */
    public static final int FLASH_AT = 18;
    public static final int SPORES_AT = 22, POD_AT = 18, EGG_AT = 20;
    /** the whirlpool: wind up, spinning, the crush at the end */
    public static final int WHIRL_UP = 40, WHIRL_CRUSH = 140;
    /** the sky dive: up, turning over, the fall (ends early when he lands), and turning back */
    public static final int DIVE_CLIMB = 90, DIVE_TURN = 24, DIVE_FALL = 80, DIVE_BACK = 46;
    /** the deep toll: three tolls, then the big one */
    public static final int TOLL_BIG = 110;
    /** the arm storm: all eight arms up, then down one after another */
    public static final int STORM_UP = 40, STORM_EVERY = 10;
    public static final int RAIN_FROM = 30, RAIN_TO = 100;
    public static final int LANCE_FROM = 35, LANCE_TO = 120;
    public static final int UNDERTOW_PULL = 30, UNDERTOW_SLAM = 60;

    public static int byName(String n) {
        for (int i = 1; i < NAMES.length; i++) if (NAMES[i].equals(n)) return i;
        return -1;
    }

    static float smooth(float k) { k = Mth.clamp(k, 0f, 1f); return k * k * (3 - 2 * k); }

    /** 0 to 1 over [a, b], held, then 1 to 0 over [c, d] */
    static float hump(float t, float a, float b, float c, float d) {
        if (t < b) return smooth((t - a) / Math.max(1f, b - a));
        if (t < c) return 1f;
        return 1f - smooth((t - c) / Math.max(1f, d - c));
    }

    /** in the arm storm, which place in the order arm k comes down (they go round the circle) */
    public static int stormRank(int arm, int first) { return Math.floorMod(arm - first, 8); }
    public static int stormHit(int arm, int first) { return STORM_UP + STORM_EVERY * stormRank(arm, first); }

    /**
     * What the move asks of him at a given moment: the arms' and strands' shape goes into st (the chains swing to
     * it with weight), and what it asks of his body (the bell brought down, the glow, the spin...) into in (his body
     * eases toward it). t = ticks into the move, arg = which arm or strand, (tx, ty, tz) = what it is aimed at, in
     * model space. phase is the server's own count for the grab (how far up it's pulled) and the dive (how far
     * over he's turned), or -1.
     */
    public static void pose(BellRig rig, BellState st, BellAnim.In in, int move, float t, int arg, float tx, float ty, float tz, float phase) {
        st.clearMoves();
        in.clearAsks();
        int len = length(move);
        switch (move) {
            case GRAB, HARVEST -> {
                if (arg < 0 || arg >= rig.strands.length) return;
                st.grabStrand = arg;
                st.grabReach = smooth(t / REACH);
                float lift = phase >= 0f ? phase : Mth.clamp((t - REACH) / LIFT, 0f, 1f);
                st.grabLift = lift;
                st.grabReach *= 1f - 0.7f * lift;          // it comes back to hanging straight as it pulls up
                st.grabX = tx; st.grabY = ty; st.grabZ = tz;
            }
            case CURTAIN -> {
                float k = t < CURTAIN_CLOSE ? smooth(t / CURTAIN_CLOSE) : t < CURTAIN_CLOSE + CURTAIN_HOLD ? 1f
                        : 1f - smooth((t - CURTAIN_CLOSE - CURTAIN_HOLD) / (len - CURTAIN_CLOSE - CURTAIN_HOLD));
                // pulled tight in the middle of the hold
                if (t > CURTAIN_CLOSE && t < CURTAIN_CLOSE + CURTAIN_HOLD) k += 0.15f * (float) Math.sin((t - CURTAIN_CLOSE) * 0.3f);
                st.curtain = k; st.curtainX = tx; st.curtainZ = tz;
            }
            case SWEEP -> {
                float k = t / len;
                // wound back, then swung right across, then settling
                float s = k < 0.3f ? -smooth(k / 0.3f) : k < 0.6f ? Mth.lerp(smooth((k - 0.3f) / 0.3f), -1f, 1.2f) : 1.2f * (1f - smooth((k - 0.6f) / 0.4f));
                st.sweep = s;
                st.sweepDir = (float) Math.atan2(tz, tx);
            }
            case SLAM -> { st.slamArm = arg; st.slamT = Mth.clamp(t / len, 0f, 1f); st.slamX = tx; st.slamY = ty; st.slamZ = tz; }
            case WRAP -> {
                st.wrapArm = arg;
                st.wrapAmt = t < WRAP_REACH ? smooth(t / WRAP_REACH) : t < WRAP_REACH + WRAP_HOLD ? 1f
                        : 1f - smooth((t - WRAP_REACH - WRAP_HOLD) / (len - WRAP_REACH - WRAP_HOLD));
                st.wrapX = tx; st.wrapY = ty; st.wrapZ = tz;
            }
            case PULSE -> in.glow = t < PULSE_AT ? smooth(t / PULSE_AT) : 1f - smooth((t - PULSE_AT) / 20f);
            case DROP -> {
                in.drop = drop(move, t);
                // the warning: he lifts a little, glowing, then down he comes
                in.lowerAdd = -6f * hump(t, 0, DROP_WIND * 0.7f, DROP_WIND * 0.8f, DROP_WIND + 4);
                in.glow = 0.9f * hump(t, 0, DROP_WIND, DROP_WIND + 2, DROP_WIND + DROP_FALL);
                st.spread = 0.5f * hump(t, 0, DROP_WIND, DROP_WIND, DROP_WIND + 10);
            }
            case SHED -> in.eggShake = t < SHED_AT ? smooth(t / SHED_AT) : 1f - smooth((t - SHED_AT) / 16f);
            case VOLLEY -> st.flick = hump(t, 0, VOLLEY_AT, VOLLEY_AT + 18, len) * (t > VOLLEY_AT && t < VOLLEY_AT + 18 ? 0.8f + 0.2f * (float) Math.cos((t - VOLLEY_AT) * 0.8f) : 1f);
            case LASH -> {
                if (arg < 0 || arg >= rig.strands.length) return;
                st.lashStrand = arg;
                st.lashT = Mth.clamp(t / len, 0f, 1f);
                st.lashDir = (float) Math.atan2(tz, tx);
            }
            case FLASH -> { in.glow = 1.8f * hump(t, 0, FLASH_AT, FLASH_AT, len); in.squeezeAdd = 0.4f * hump(t, FLASH_AT - 4, FLASH_AT, FLASH_AT + 2, FLASH_AT + 10); }
            case SPORES -> { in.eggShake = hump(t, 0, SPORES_AT, SPORES_AT, SPORES_AT + 20); in.squeezeAdd = 0.3f * hump(t, SPORES_AT - 3, SPORES_AT, SPORES_AT + 2, SPORES_AT + 12); }
            case POD_BURST -> { in.podSwell = hump(t, 0, POD_AT, POD_AT + 16, len); in.glow = 0.3f * in.podSwell; }
            case EGG_RAIN -> in.eggShake = hump(t, 0, EGG_AT, EGG_AT + 24, len);
            case WHIRLPOOL -> {
                st.spread = hump(t, 0, WHIRL_UP, WHIRL_CRUSH - 20, WHIRL_CRUSH);
                st.swirl = hump(t, 10, WHIRL_UP, WHIRL_CRUSH, len);
                in.spinning = true;
                // two whole turns, winding up and easing off, so he ends square
                in.spin = (float) (4 * Math.PI) * smoother(Mth.clamp((t - 20f) / (WHIRL_CRUSH - 5f), 0f, 1f));
                in.glow = 0.7f * hump(t, 0, WHIRL_UP, WHIRL_UP, WHIRL_UP + 20);
                if (t > WHIRL_CRUSH - 10 && t < WHIRL_CRUSH + 20) st.curtain = hump(t, WHIRL_CRUSH - 10, WHIRL_CRUSH, WHIRL_CRUSH + 4, WHIRL_CRUSH + 20);
            }
            case SKY_DIVE -> {
                // phase: how far over he has turned (the server says; it knows when he lands)
                float over = phase >= 0f ? phase : 0f;
                float a = 2.9f * over;
                float dx = tx, dz = tz, d = (float) Math.sqrt(dx * dx + dz * dz);
                if (d < 1e-3f) { dx = 1f; dz = 0f; d = 1f; }
                in.flipX = dz / d * a;
                in.flipZ = -dx / d * a;
                in.glow = 1.2f * hump(t, DIVE_CLIMB - 20, DIVE_CLIMB, DIVE_CLIMB + DIVE_TURN, DIVE_CLIMB + DIVE_TURN + 10);
                in.squeezeAdd = 0.6f * over;
                st.spread = 0.4f * hump(t, 0, 20, DIVE_CLIMB - 10, DIVE_CLIMB);
            }
            case DEEP_TOLL -> {
                in.glow = 1.6f * Mth.clamp(t / TOLL_BIG, 0f, 1f) * (t < TOLL_BIG + 4 ? 1f : 1f - smooth((t - TOLL_BIG - 4) / 20f));
                st.spread = 0.5f * hump(t, 60, TOLL_BIG, TOLL_BIG + 6, TOLL_BIG + 36);
            }
            case ARM_STORM -> {
                int first = Math.max(0, arg);
                for (int k = 0; k < 8 && k < st.armRaise.length; k++) {
                    float hit = stormHit(k, first);
                    float r;
                    if (t < STORM_UP) r = smooth(t / STORM_UP);
                    else if (t < hit - 6) r = 1f;
                    else if (t < hit) r = Mth.lerp(smooth((t - hit + 6) / 6f), 1f, -0.15f);
                    else r = -0.15f * (1f - smooth((t - hit) / 20f)) * (t < len - 10 ? 1f : 1f - smooth((t - len + 10) / 10f));
                    st.armRaise[k] = r;
                }
                in.glow = 0.5f * hump(t, 0, STORM_UP, STORM_UP, STORM_UP + 20);
            }
            case STINGER_STORM -> {
                st.flick = hump(t, 0, RAIN_FROM, RAIN_TO, len);
                st.spread = 0.35f * st.flick;
                in.glow = 0.8f * hump(t, 0, RAIN_FROM, RAIN_TO, len);
            }
            case SUN_LANCES -> {
                in.glow = 2f * hump(t, 0, LANCE_FROM, LANCE_TO, len);
                st.spread = 0.5f * hump(t, 0, LANCE_FROM, LANCE_TO, len);
                in.squeezeAdd = -0.4f * hump(t, 0, LANCE_FROM, LANCE_TO, len);
            }
            case UNDERTOW -> {
                st.spread = hump(t, 0, UNDERTOW_PULL, UNDERTOW_PULL, UNDERTOW_SLAM - 10);
                st.curtain = hump(t, UNDERTOW_PULL, UNDERTOW_SLAM, UNDERTOW_SLAM + 8, UNDERTOW_SLAM + 40);
                st.curtainX = 0f; st.curtainZ = 0f;
                st.swirl = 0.4f * hump(t, UNDERTOW_PULL, UNDERTOW_SLAM - 10, UNDERTOW_SLAM, UNDERTOW_SLAM + 20);
                in.squeezeAdd = 0.8f * hump(t, UNDERTOW_SLAM - 6, UNDERTOW_SLAM, UNDERTOW_SLAM + 2, UNDERTOW_SLAM + 16);
                in.glow = 0.6f * hump(t, 0, UNDERTOW_PULL, UNDERTOW_PULL, UNDERTOW_SLAM);
            }
            default -> {}
        }
    }

    private static float smoother(float k) { return k * k * k * (k * (k * 6 - 15) + 10); }

    /** how far down the bell is in the drop, 0-1 */
    public static float drop(int move, float t) {
        if (move != DROP) return 0f;
        t -= DROP_WIND;
        if (t < 0) return 0f;
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
