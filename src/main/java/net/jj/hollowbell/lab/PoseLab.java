package net.jj.hollowbell.lab;

import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.rig.BellAnim;
import net.jj.hollowbell.rig.BellModel;
import net.jj.hollowbell.rig.BellRig;
import net.jj.hollowbell.rig.BellState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pose lab: runs his animation offline (the real BellAnim, BellRig and Moves, no game), poses the real model in idle,
 * drifting, climbing, sinking, the pulse, every move's key moments, asleep and dying, and measures how whole he stays:
 *  - floating: blocks that end up cut off from the rest of him (a clump with nothing of him touching it);
 *  - gaps: blocks that touch at rest but come apart in the pose, sorted by what they are (a joint in an arm or a
 *    strand, a pod or egg clump on its strand, a strand on its root, the dome's bands...);
 *  - glow: the same for the glowing blocks only;
 *  - bends: the sharpest bend at any joint of an arm or strand.
 * Pictures of the worst moments go to build/poselab (or -Dlab.out). Development only, never in the jar:
 *   ./gradlew poseLab                 (every scene)
 *   ./gradlew poseLab -Plab="idle slam" -Pshots=1
 */
public final class PoseLab {
    static BellRig rig;
    static BellModel model;
    static int N;
    static int[] vb, vcol, vs;
    static net.jj.hollowbell.rig.BellPieces pieces;
    static boolean rigid = Boolean.getBoolean("lab.rigid");
    static Matrix4f[] labHang;
    static float[] rx, ry, rz;
    static boolean[] glow;
    static String[] palName;
    /** pairs of blocks of different bones that touch at rest */
    static int[] pa, pb;
    static float[] prd;
    static File out = new File(System.getProperty("lab.out", "build/poselab"));
    static boolean shots = !"0".equals(System.getProperty("lab.shots", "0"));
    static PrintWriter log;

    public static void main(String[] args) throws Exception {
        out.mkdirs();
        log = new PrintWriter(new File(out, "poselab.txt"));
        rig = BellRig.get();
        model = BellModel.get();
        prepare();
        List<String> want = Arrays.asList(args);
        boolean all = want.isEmpty() || want.contains("all");
        Map<String, Scene> scenes = scenes();
        Totals tot = new Totals();
        for (Scene s : scenes.values()) {
            if (!all && !want.contains(s.name) && want.stream().noneMatch(w -> s.name.startsWith(w))) continue;
            run(s, tot);
        }
        say(String.format("ALL: %d poses, floating blocks: worst %d (%s), total %d; break pairs over 1 block: worst %d (%s); glow gaps worst %d (%s); sharpest bend %.0f deg (%s)",
                tot.poses, tot.worstFloat, tot.worstFloatAt, tot.sumFloat, tot.worstGaps, tot.worstGapsAt, tot.worstGlow, tot.worstGlowAt, Math.toDegrees(tot.worstBend), tot.worstBendAt));
        log.close();
    }

    static final boolean JERK = Boolean.getBoolean("lab.jerk");
    static float[][] jl, jl2;
    static float worstJerk;

    /** like the blend test: the biggest change of speed of any joint in a tick (not counting knocks on the ground) */
    static void jerk(BellAnim anim, int t, String scene) {
        BellState st = anim.now();
        if (t == 0) { jl = null; jl2 = null; worstJerk = 0; }
        if (jl2 != null) for (int c = 0; c < st.chain.length; c++) {
            if (anim.knocked(c)) continue;
            float[] a = jl2[c], b = jl[c], n = st.chain[c];
            for (int i = 0; i < n.length; i += 3) {
                float jx = n[i] - 2 * b[i] + a[i], jy = n[i + 1] - 2 * b[i + 1] + a[i + 1], jz = n[i + 2] - 2 * b[i + 2] + a[i + 2];
                float j = (float) Math.sqrt(jx * jx + jy * jy + jz * jz);
                if (j > worstJerk) { worstJerk = j; say(String.format("    jerk %s t%d chain %d (%s %d) point %d: %.1f", scene, t - SETTLE, c, rig.chains[c].arm ? "arm" : "strand", rig.chains[c].index, i / 3, j)); }
            }
        }
        jl2 = jl;
        jl = new float[st.chain.length][];
        for (int c = 0; c < st.chain.length; c++) jl[c] = st.chain[c].clone();
    }

    static void say(String s) { System.out.println(s); log.println(s); log.flush(); }

    // ------------------------------------------------------------------ the model's blocks

    static void prepare() {
        int nb = model.boneCount();
        int n = 0;
        for (int b = 0; b < nb; b++) n += model.count(b);
        N = n;
        pieces = net.jj.hollowbell.rig.BellPieces.get();
        labHang = pieces.newHang();
        vs = new int[n];
        vb = new int[n]; vcol = new int[n]; rx = new float[n]; ry = new float[n]; rz = new float[n]; glow = new boolean[n];
        palName = model.palette;
        int[] palCol = new int[palName.length];
        boolean[] palGlow = new boolean[palName.length];
        for (int p = 0; p < palName.length; p++) {
            palCol[p] = colour(palName[p]);
            palGlow[p] = palName[p].matches("minecraft:(verdant_froglight|ochre_froglight|pearlescent_froglight|sea_lantern|shroomlight|glowstone)");
        }
        int i = 0;
        HashMap<Long, Integer> at = new HashMap<>(n * 2);
        for (int b = 0; b < nb; b++) for (int k = 0; k < model.count(b); k++, i++) {
            vb[i] = b; vs[i] = rigid ? b : pieces.sliceOf[b][k]; rx[i] = model.x[b][k]; ry[i] = model.y[b][k]; rz[i] = model.z[b][k];
            vcol[i] = palCol[model.pal[b][k]]; glow[i] = palGlow[model.pal[b][k]];
            at.put(BellModel.key(model.x[b][k], model.y[b][k], model.z[b][k]), i);
        }
        // every pair of blocks of two different bones that touch at rest (faces, edges or corners)
        int[] a = new int[1 << 16], c = new int[1 << 16];
        float[] d = new float[1 << 16];
        int m = 0;
        for (int j = 0; j < n; j++) {
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                // each pair once
                if (dx < 0 || (dx == 0 && dy < 0) || (dx == 0 && dy == 0 && dz < 0)) continue;
                Integer o = at.get(BellModel.key((int) rx[j] + dx, (int) ry[j] + dy, (int) rz[j] + dz));
                if (o == null || vb[o] == vb[j]) continue;
                if (m == a.length) { a = Arrays.copyOf(a, m * 2); c = Arrays.copyOf(c, m * 2); d = Arrays.copyOf(d, m * 2); }
                a[m] = j; c[m] = o; d[m] = (float) Math.sqrt(dx * dx + dy * dy + dz * dz); m++;
            }
        }
        pa = Arrays.copyOf(a, m); pb = Arrays.copyOf(c, m); prd = Arrays.copyOf(d, m);
        say(String.format("model: %d blocks in %d bones (%d glowing), %d touching pairs between bones", n, nb, count(glow), m));
    }

    static int count(boolean[] b) { int k = 0; for (boolean x : b) if (x) k++; return k; }

    static int colour(String java) {
        String n = java.replace("minecraft:", "");
        Map<String, int[]> col = Map.ofEntries(
                Map.entry("bone_block", new int[]{229, 225, 207}), Map.entry("oxidized_copper", new int[]{82, 162, 132}),
                Map.entry("weathered_copper", new int[]{108, 153, 110}), Map.entry("calcite", new int[]{223, 224, 220}),
                Map.entry("glass", new int[]{200, 225, 235}), Map.entry("verdant_froglight", new int[]{229, 244, 228}),
                Map.entry("glowstone", new int[]{255, 218, 116}), Map.entry("sea_lantern", new int[]{172, 200, 190}),
                Map.entry("oxidized_cut_copper", new int[]{80, 154, 126}), Map.entry("weathered_cut_copper", new int[]{109, 145, 107}),
                Map.entry("honeycomb_block", new int[]{229, 148, 29}), Map.entry("dripstone_block", new int[]{134, 107, 92}),
                Map.entry("tinted_glass", new int[]{44, 38, 46}), Map.entry("ochre_froglight", new int[]{251, 245, 207}),
                Map.entry("shroomlight", new int[]{240, 146, 70}), Map.entry("cyan_terracotta", new int[]{86, 91, 91}));
        Map<String, int[]> dye = Map.of("white", new int[]{233, 236, 236}, "lime", new int[]{112, 185, 25}, "yellow", new int[]{248, 197, 39},
                "light_gray", new int[]{142, 142, 134}, "gray", new int[]{62, 68, 71}, "cyan", new int[]{21, 137, 145}, "black", new int[]{20, 21, 25});
        int[] c = col.get(n);
        if (c == null && n.endsWith("_stained_glass")) c = dye.get(n.substring(0, n.length() - "_stained_glass".length()));
        if (c == null && n.endsWith("_concrete")) c = dye.get(n.substring(0, n.length() - "_concrete".length()));
        if (c == null) c = new int[]{255, 0, 255};
        return (c[0] << 16) | (c[1] << 8) | c[2];
    }

    // ------------------------------------------------------------------ scenes

    interface Driver { void tick(int t, BellAnim.In in, BellState st); }

    record Scene(String name, int ticks, int[] samples, Driver d) {}

    static float GROUND = Float.parseFloat(System.getProperty("lab.ground", "-14"));
    static final int SETTLE = 160;

    static Map<String, Scene> scenes() {
        Map<String, Scene> m = new LinkedHashMap<>();
        m.put("rest", new Scene("rest", 1, new int[]{0}, (t, in, st) -> in.pulse = 0f));
        m.put("idle", new Scene("idle", SETTLE + 140, every(SETTLE, SETTLE + 140, 14), (t, in, st) -> {}));
        m.put("drift", new Scene("drift", SETTLE + 200, every(SETTLE, SETTLE + 200, 20), (t, in, st) -> move(in, 0.3f * Math.min(1f, t / 60f), 0f, 0f)));
        m.put("turn", new Scene("turn", SETTLE + 200, every(SETTLE, SETTLE + 200, 20), (t, in, st) -> {
            float a = t < SETTLE ? 0f : (t - SETTLE) * 0.03f;
            move(in, 0.3f * (float) Math.cos(a), 0f, 0.3f * (float) Math.sin(a));
        }));
        m.put("stop", new Scene("stop", SETTLE + 140, every(SETTLE, SETTLE + 140, 14), (t, in, st) -> move(in, t < SETTLE ? 0.3f : 0f, 0f, 0f)));
        m.put("climb", new Scene("climb", SETTLE + 120, every(SETTLE, SETTLE + 120, 12), (t, in, st) -> {
            move(in, 0.05f, 0.25f, 0f);
            in.pulse = Moves.pulseCurve(t % 34, 1.6f);
        }));
        m.put("sink", new Scene("sink", SETTLE + 120, every(SETTLE, SETTLE + 120, 12), (t, in, st) -> move(in, 0f, -0.2f, 0f)));
        m.put("sleep", new Scene("sleep", SETTLE + 200, every(SETTLE, SETTLE + 200, 25), (t, in, st) -> in.sleep = Math.min(1f, Math.max(0f, (t - SETTLE + 100) / 100f))));
        m.put("sunk", new Scene("sunk", SETTLE + 200, every(SETTLE, SETTLE + 200, 25), (t, in, st) -> in.sunk = t > SETTLE - 60 ? 1f : 0f));
        m.put("dying", new Scene("dying", SETTLE + 320, every(SETTLE, SETTLE + 320, 32), (t, in, st) -> in.dying = t > SETTLE - 40 ? t - (SETTLE - 40) : -1f));
        m.put("tired", new Scene("tired", SETTLE + 100, every(SETTLE, SETTLE + 100, 20), (t, in, st) -> in.tired = true));
        for (int mv : Moves.ORDER) {
            int len = Moves.length(mv);
            String name = Moves.NAMES[mv];
            int[] at = keyTimes(mv, len);
            int[] samples = new int[at.length];
            for (int i = 0; i < at.length; i++) samples[i] = SETTLE + at[i];
            m.put(name, new Scene(name, SETTLE + len + 1, samples, (t, in, st) -> {
                if (t < SETTLE) return;
                moveTick(mv, t - SETTLE, len, in, st);
            }));
        }
        // the blend test: a slam cut off by a sweep, that by a drop, then a curtain, then stopped
        m.put("blend", new Scene("blend", SETTLE + 400, every(SETTLE, SETTLE + 400, 400), (t, in, st) -> {
            if (t < SETTLE) return;
            int k = t - SETTLE;
            int mv = k < 32 ? Moves.SLAM : k < 60 ? Moves.SWEEP : k < 120 ? Moves.DROP : k < 150 ? Moves.CURTAIN : Moves.NONE;
            int start = k < 32 ? 0 : k < 60 ? 32 : k < 120 ? 60 : k < 150 ? 120 : 150;
            if (mv != Moves.NONE) Moves.pose(rig, st, in, mv, k - start, 1, 30f, 1f, 10f, -1f);
            if (mv == Moves.DROP) move(in, 0f, 0f, 0f);
        }));
        // the grab carrying somebody up onto his crown
        m.put("carry", new Scene("carry", SETTLE + 200, every(SETTLE, SETTLE + 200, 20), (t, in, st) -> {
            if (t < SETTLE) return;
            int k = t - SETTLE;
            float phase = k < Moves.REACH ? Moves.CARRY_PHASE : Moves.CARRY_PHASE + Math.min(1f, (k - Moves.REACH) / 150f);
            Moves.pose(rig, st, in, Moves.GRAB, k, 30, 70f, 4f, 10f, phase, 8f, 2f);
        }));
        return m;
    }

    static int[] keyTimes(int mv, int len) {
        List<Integer> l = new ArrayList<>();
        int steps = 10;
        for (int i = 1; i <= steps; i++) l.add(Math.min(len, Math.round(len * i / (float) steps)));
        int[] extra = switch (mv) {
            case Moves.SLAM -> new int[]{(int) (len * 0.5f), (int) (len * BellRig.SLAM_HIT), (int) (len * BellRig.SLAM_HIT) + 2};
            case Moves.PULSE -> new int[]{Moves.PULSE_AT};
            case Moves.LASH -> new int[]{Moves.LASH_AT};
            case Moves.POD_BURST -> new int[]{Moves.POD_AT, Moves.POD_AT + 3};
            case Moves.EGG_RAIN -> new int[]{Moves.EGG_AT, Moves.EGG_AT + 5};
            case Moves.WHIRLPOOL -> new int[]{Moves.WHIRL_CRUSH};
            case Moves.DEEP_TOLL -> new int[]{Moves.TOLL_BIG};
            case Moves.ARM_STORM -> new int[]{Moves.STORM_UP, Moves.STORM_UP + 3 * Moves.STORM_EVERY};
            case Moves.SUN_LANCES -> new int[]{Moves.LANCE_FROM + 4};
            case Moves.UNDERTOW -> new int[]{Moves.UNDERTOW_SLAM};
            default -> new int[0];
        };
        for (int e : extra) l.add(e);
        return l.stream().distinct().sorted().mapToInt(Integer::intValue).toArray();
    }

    /** what the server would be doing with the move this tick (the target in front of him, low down) */
    static void moveTick(int mv, int t, int len, BellAnim.In in, BellState st) {
        float tx = 55f, ty = 4f, tz = 25f;
        int arg = switch (mv) {
            case Moves.SLAM, Moves.WRAP -> 1;
            case Moves.GRAB, Moves.HARVEST, Moves.LASH -> 30;
            default -> 0;
        };
        float phase = -1f;
        if (mv == Moves.SKY_DIVE) {
            int turn = Moves.DIVE_CLIMB, fall = turn + Moves.DIVE_TURN, back = fall + Moves.DIVE_FALL;
            if (t < turn) phase = 0f;
            else if (t < fall) phase = smooth((t - turn) / (float) Moves.DIVE_TURN);
            else if (t < back + 8) phase = 1f;
            else phase = 1f - smooth((t - back - 8) / (float) (Moves.DIVE_BACK - 8));
            if (t < turn) move(in, 0f, 0.2f, 0f);
            else if (t >= fall && t < back) move(in, 0.1f, -0.4f, 0.05f);
        }
        if (mv == Moves.DROP) move(in, 0f, t > Moves.DROP_WIND && t < Moves.DROP_WIND + Moves.DROP_FALL ? -0.5f : 0f, 0f);
        Moves.pose(rig, st, in, mv, t, arg, tx, ty, tz, phase);
    }

    static float smooth(float k) { k = Math.max(0f, Math.min(1f, k)); return k * k * (3 - 2 * k); }

    static void move(BellAnim.In in, float vx, float vy, float vz) {
        in.vx = vx; in.vy = vy; in.vz = vz;
        in.shiftX = vx; in.shiftY = vy; in.shiftZ = vz;
    }

    static int[] every(int from, int to, int step) {
        List<Integer> l = new ArrayList<>();
        for (int t = from; t <= to; t += step) l.add(t);
        return l.stream().mapToInt(Integer::intValue).toArray();
    }

    // ------------------------------------------------------------------ running one

    static final class Totals {
        int poses, worstFloat = -1, sumFloat, worstGaps = -1, worstGlow = -1;
        float worstBend = -1;
        String worstFloatAt = "", worstGapsAt = "", worstGlowAt = "", worstBendAt = "";
    }

    static void run(Scene s, Totals tot) throws Exception {
        BellAnim anim = new BellAnim(rig);
        BellAnim.In in = new BellAnim.In();
        BellState st = new BellState(rig);
        Matrix4f[] pose = rig.newPose();
        int si = 0;
        Result worst = null;
        int worstT = 0;
        int[] floats = new int[s.samples.length];
        for (int t = 0; t <= s.ticks && si < s.samples.length; t++) {
            in.time = 1000 + t;
            in.speedRef = 0.3f;
            in.groundUnder = GROUND;
            in.ground = (mx, mz) -> GROUND;
            in.pulse = Moves.pulseCurve(t % 60, 1f);
            in.sleep = 0f; in.sunk = 0f; in.dying = -1f; in.tired = false; in.red = false;
            move(in, 0f, 0f, 0f);
            st.clearMoves();
            in.clearAsks();
            BellState now = anim.now();
            s.d.tick(t, in, now);
            anim.step(in);
            if (JERK) jerk(anim, t, s.name);
            if (t == s.samples[si]) {
                anim.fill(st, 1f);
                rig.computePose(st, pose, labHang);
                curState = st;
                if (Integer.getInteger("lab.strand", -1) >= 0) {
                    int sc = rig.strandChain[Integer.getInteger("lab.strand")];
                    float[] cp = st.chain[sc];
                    StringBuilder sb = new StringBuilder("      chain");
                    for (int q = 0; q < cp.length / 3; q++) sb.append(String.format(" (%.1f %.1f %.1f)", cp[3 * q], cp[3 * q + 1], cp[3 * q + 2]));
                    Vector3f h0 = labHang[sc].transformPosition(new Vector3f(rig.chains[sc].joints[0]));
                    sb.append(String.format(" hang*J0 (%.1f %.1f %.1f)", h0.x, h0.y, h0.z));
                    say(sb.toString());
                }
                Result r = measure(pose);
                StringBuilder bw = new StringBuilder();
                r.bend = bend(st, bw);
                r.bendWhat = bw.toString();
                floats[si] = r.floating;
                String at = s.name + "@" + (t >= SETTLE && s.ticks > SETTLE ? "t" + (t - SETTLE) : "" + t);
                say(String.format("  %-16s floating %5d in %3d bits | breaks>1: %5d (worst %5.1f %s) | glow breaks %4d (worst %4.1f %s) | bend %3.0f %s | parts that only touch, apart: %d",
                        at, r.floating, r.bits, r.gapPairs, r.worstGap, r.worstGapWhat, r.glowGaps, r.worstGlowGap, r.worstGlowWhat, Math.toDegrees(r.bend), r.bendWhat, r.touchApart) + " | over 2: " + r.big2);
                if (!r.byCat.isEmpty()) say("      by kind: " + r.byCat);
                if (!r.bitList.isEmpty()) say("      bits: " + r.bitList);
                tot.poses++;
                tot.sumFloat += r.floating;
                if (r.floating > tot.worstFloat) { tot.worstFloat = r.floating; tot.worstFloatAt = at; }
                if (r.gapPairs > tot.worstGaps) { tot.worstGaps = r.gapPairs; tot.worstGapsAt = at; }
                if (r.glowGaps > tot.worstGlow) { tot.worstGlow = r.glowGaps; tot.worstGlowAt = at; }
                if (r.bend > tot.worstBend) { tot.worstBend = r.bend; tot.worstBendAt = at + " " + r.bendWhat; }
                if (worst == null || r.floating + r.gapPairs / 4 > worst.floating + worst.gapPairs / 4) { worst = r; worstT = t; }
                if (shots) picture(s.name + "_" + (t >= SETTLE && s.ticks > SETTLE ? "t" + (t - SETTLE) : "" + t), pose, r, st);
                si++;
            }
        }
        if (worst != null && !shots && Boolean.getBoolean("lab.worst")) {
            // the worst moment again, for a picture
            run1(s, worstT);
        }
    }

    /** runs a scene to tick t and draws it */
    static void run1(Scene s, int tt) throws Exception {
        BellAnim anim = new BellAnim(rig);
        BellAnim.In in = new BellAnim.In();
        BellState st = new BellState(rig);
        Matrix4f[] pose = rig.newPose();
        for (int t = 0; t <= tt; t++) {
            in.time = 1000 + t; in.speedRef = 0.3f; in.groundUnder = GROUND; in.ground = (mx, mz) -> GROUND;
            in.pulse = Moves.pulseCurve(t % 60, 1f);
            in.sleep = 0f; in.sunk = 0f; in.dying = -1f; in.tired = false;
            move(in, 0f, 0f, 0f);
            s.d.tick(t, in, anim.now());
            anim.step(in);
        }
        anim.fill(st, 1f);
        rig.computePose(st, pose, labHang);
        curState = st;
        picture(s.name + "_worst_" + (tt - SETTLE), pose, measure(pose), st);
    }

    // ------------------------------------------------------------------ measuring

    static final class Result {
        float[] px, py, pz, scale;
        boolean[] cut, gapped;
        int floating, bits, gapPairs, glowGaps, touchApart, big2;
        float worstGap, worstGlowGap, bend;
        String worstGapWhat = "", worstGlowWhat = "", bendWhat = "";
        Map<String, Integer> byCat = new java.util.TreeMap<>();
        List<String> bitList = new ArrayList<>();
    }

    static String cat(int a, int b) {
        BellRig.Kind ka = rig.kind[a], kb = rig.kind[b];
        if (ka.ordinal() > kb.ordinal()) { int t = a; a = b; b = t; BellRig.Kind k = ka; ka = kb; kb = k; }
        int ca = rig.chainOf[a], cb = rig.chainOf[b];
        if (ca >= 0 && ca == cb) return (ka == BellRig.Kind.ARM ? "arm" : "strand") + " joint";
        if (ca >= 0 && cb >= 0) {
            if ((rig.chains[cb].parentBone == a && rig.seg[b] == 0) || (rig.chains[ca].parentBone == b && rig.seg[a] == 0)) return "branch root";
            return ka == kb ? (ka == BellRig.Kind.ARM ? "arm-arm" : "strand-strand") : "arm-strand";
        }
        if (cb >= 0 && rig.chains[cb].parentBone == a && rig.seg[b] == 0) return kb.name().toLowerCase() + " root";
        if (cb >= 0 && ca < 0 && rig.chains[cb].parentBone != a && (ka == BellRig.Kind.BELL || ka == BellRig.Kind.RIM || ka == BellRig.Kind.SPOT || ka == BellRig.Kind.CROWN))
            return kb.name().toLowerCase() + "-" + ka.name().toLowerCase();
        if (kb == BellRig.Kind.POD || kb == BellRig.Kind.EGG) return kb.name().toLowerCase() + (rig.parent[b] == a ? " on its own" : "-" + ka.name().toLowerCase());
        return ka.name().toLowerCase() + "-" + kb.name().toLowerCase();
    }

    /** a break inside one of his parts, or where a part hangs from what it grows on (not two parts that only touch) */
    static boolean own(String c) {
        return c.endsWith("joint") || c.endsWith("root") || c.endsWith("on its own")
                || c.matches("(bell|rim|crown|spot)-(bell|rim|crown|spot)");
    }

    static BellState curState;
    static final boolean DEBUG = Boolean.getBoolean("lab.debug");
    static int dbg;

    static Result measure(Matrix4f[] pose) {
        Result r = new Result();
        Matrix4f[] sp = pose;
        if (!rigid) { sp = pieces.newPose(); pieces.pose(curState, pose, labHang, sp); }
        int nb = sp.length;
        r.scale = new float[nb];
        Vector3f v = new Vector3f();
        for (int b = 0; b < nb; b++) {
            Matrix4f m = sp[b];
            float c0 = (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01() + m.m02() * m.m02());
            float c1 = (float) Math.sqrt(m.m10() * m.m10() + m.m11() * m.m11() + m.m12() * m.m12());
            float c2 = (float) Math.sqrt(m.m20() * m.m20() + m.m21() * m.m21() + m.m22() * m.m22());
            r.scale[b] = Math.max(c0, Math.max(c1, c2));
        }
        float[] px = new float[N], py = new float[N], pz = new float[N];
        for (int i = 0; i < N; i++) {
            sp[vs[i]].transformPosition(v.set(rx[i] + 0.5f, ry[i] + 0.5f, rz[i] + 0.5f));
            px[i] = v.x; py[i] = v.y; pz[i] = v.z;
        }
        r.px = px; r.py = py; r.pz = pz;
        // gaps: blocks that touched at rest, now apart by more than a block
        r.gapped = new boolean[N];
        for (int k = 0; k < pa.length; k++) {
            int a = pa[k], b = pb[k];
            float dx = px[a] - px[b], dy = py[a] - py[b], dz = pz[a] - pz[b];
            float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float g = d - prd[k] * Math.max(r.scale[vs[a]], r.scale[vs[b]]);
            if (g <= 1f) continue;
            String c = cat(vb[a], vb[b]);
            if (!own(c)) { r.touchApart++; continue; }
            if (DEBUG && c.equals("strand joint") && dbg++ < 6)
                say(String.format("      dbg %s v%d(%s slice %d) %.0f,%.0f,%.0f -> %.1f,%.1f,%.1f | %s v%d(slice %d) %.0f,%.0f,%.0f -> %.1f,%.1f,%.1f gap %.1f",
                        rig.boneNames[vb[a]], a, "", vs[a], rx[a], ry[a], rz[a], px[a], py[a], pz[a], rig.boneNames[vb[b]], b, vs[b], rx[b], ry[b], rz[b], px[b], py[b], pz[b], g));
            r.gapPairs++;
            if (g > 2f) r.big2++;
            r.gapped[a] = true; r.gapped[b] = true;
            r.byCat.merge(c, 1, Integer::sum);
            if (g > r.worstGap) { r.worstGap = g; r.worstGapWhat = rig.boneNames[vb[a]] + "/" + rig.boneNames[vb[b]]; }
            if (glow[a] || glow[b]) {
                r.glowGaps++;
                if (g > r.worstGlowGap) { r.worstGlowGap = g; r.worstGlowWhat = rig.boneNames[vb[a]] + "/" + rig.boneNames[vb[b]] + String.format("@%.0f,%.0f,%.0f", rx[a], ry[a], rz[a]); }
            }
        }
        // floating: link blocks closer than their size (a touch, allowing for parts drawn bigger), the biggest piece is him
        int[] parent = new int[N];
        for (int i = 0; i < N; i++) parent[i] = i;
        float cell = 3f;
        HashMap<Long, int[]> grid = new HashMap<>(N / 4);
        int[] next = new int[N];
        Arrays.fill(next, -1);
        for (int i = 0; i < N; i++) {
            long key = cellKey((int) Math.floor(px[i] / cell), (int) Math.floor(py[i] / cell), (int) Math.floor(pz[i] / cell));
            int[] head = grid.get(key);
            if (head == null) grid.put(key, new int[]{i});
            else { next[i] = head[0]; head[0] = i; }
        }
        for (int i = 0; i < N; i++) {
            int cx = (int) Math.floor(px[i] / cell), cy = (int) Math.floor(py[i] / cell), cz = (int) Math.floor(pz[i] / cell);
            float si = r.scale[vs[i]];
            for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) for (int c = -1; c <= 1; c++) {
                int[] head = grid.get(cellKey(cx + a, cy + b, cz + c));
                if (head == null) continue;
                for (int j = head[0]; j >= 0; j = next[j]) {
                    if (j <= i) continue;
                    float dx = px[i] - px[j], dy = py[i] - py[j], dz = pz[i] - pz[j];
                    float lim = 1.8f * Math.max(si, r.scale[vs[j]]);
                    if (lim > 2.95f) lim = 2.95f;
                    if (dx * dx + dy * dy + dz * dz <= lim * lim) union(parent, i, j);
                }
            }
        }
        HashMap<Integer, Integer> size = new HashMap<>();
        for (int i = 0; i < N; i++) size.merge(find(parent, i), 1, Integer::sum);
        int big = -1, bs = -1;
        for (var e : size.entrySet()) if (e.getValue() > bs) { bs = e.getValue(); big = e.getKey(); }
        r.cut = new boolean[N];
        HashMap<Integer, Map<Integer, Integer>> bitBones = new HashMap<>();
        for (int i = 0; i < N; i++) {
            int root = find(parent, i);
            if (root == big) continue;
            r.cut[i] = true;
            r.floating++;
            bitBones.computeIfAbsent(root, k -> new HashMap<>()).merge(vb[i], 1, Integer::sum);
        }
        r.bits = size.size() - 1;
        if (DEBUG) {
            // for the biggest cut-off pieces: the links to the rest of him that broke, and by how much (closest pair)
            Map<String, Float> links = new HashMap<>();
            for (int k = 0; k < pa.length; k++) {
                int a = pa[k], b = pb[k];
                int ra = find(parent, a), rb = find(parent, b);
                if (ra == rb) continue;
                if (Math.min(size.get(ra), size.get(rb)) < 200) continue;
                float dx = px[a] - px[b], dy = py[a] - py[b], dz = pz[a] - pz[b];
                float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                String key = rig.boneNames[vb[a]] + "~" + rig.boneNames[vb[b]];
                links.merge(key, d, Math::min);
            }
            links.entrySet().stream().sorted(Map.Entry.comparingByValue()).limit(12)
                    .forEach(e -> say(String.format("      link %s closest %.1f", e.getKey(), e.getValue())));
        }
        bitBones.values().stream().sorted((x, y) -> sum(y) - sum(x)).limit(6).forEach(m -> {
            StringBuilder sb = new StringBuilder();
            m.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(4)
                    .forEach(e -> sb.append(sb.length() == 0 ? "" : "+").append(rig.boneNames[e.getKey()]).append(':').append(e.getValue()));
            r.bitList.add(sb.toString());
        });
        return r;
    }

    static int sum(Map<Integer, Integer> m) { int t = 0; for (int v : m.values()) t += v; return t; }

    static long cellKey(int x, int y, int z) { return ((long) (x + 100000) * 200003L + (y + 100000)) * 200003L + (z + 100000); }
    static int find(int[] p, int i) { while (p[i] != i) { p[i] = p[p[i]]; i = p[i]; } return i; }
    static void union(int[] p, int a, int b) { a = find(p, a); b = find(p, b); if (a != b) p[Math.max(a, b)] = Math.min(a, b); }

    /** the sharpest bend between two pieces of any chain, from the chain points */
    static float bend(BellState st, StringBuilder what) {
        float worst = 0f;
        for (int c = 0; c < rig.chains.length; c++) {
            float[] p = st.chain[c];
            int m = rig.chains[c].points() - 1;
            for (int i = 1; i < m; i++) {
                float ax = p[3 * i] - p[3 * i - 3], ay = p[3 * i + 1] - p[3 * i - 2], az = p[3 * i + 2] - p[3 * i - 1];
                float bx = p[3 * i + 3] - p[3 * i], by = p[3 * i + 4] - p[3 * i + 1], bz = p[3 * i + 5] - p[3 * i + 2];
                float la = (float) Math.sqrt(ax * ax + ay * ay + az * az), lb = (float) Math.sqrt(bx * bx + by * by + bz * bz);
                if (la < 1e-3f || lb < 1e-3f) continue;
                // how much more it bends than it was built bent
                Vector3f j0 = rig.chains[c].joints[i - 1], j1 = rig.chains[c].joints[i], j2 = rig.chains[c].joints[i + 1];
                float built = new Vector3f(j1).sub(j0).angle(new Vector3f(j2).sub(j1));
                float now = (float) Math.acos(Math.max(-1f, Math.min(1f, (ax * bx + ay * by + az * bz) / (la * lb))));
                if (now - built > worst) {
                    worst = now - built;
                    what.setLength(0);
                    what.append(rig.chains[c].arm ? "arm " : "strand ").append(rig.chains[c].index).append(" joint ").append(i);
                }
            }
        }
        return worst;
    }

    // ------------------------------------------------------------------ pictures

    /** front, side, top and from below; cut-off blocks in magenta, blocks that came apart in red */
    static void picture(String name, Matrix4f[] pose, Result r, BellState st) throws Exception {
        float[][] views = {{0f, 0.15f}, {(float) Math.PI / 2, 0.15f}, {0.6f, 1.2f}, {0.3f, -0.5f}};
        int S = 3, W = 300 * S, H = 300 * S;
        BufferedImage img = new BufferedImage(W * 2, H * 2, BufferedImage.TYPE_INT_RGB);
        for (int v = 0; v < views.length; v++) draw(img, (v % 2) * W, (v / 2) * H, W, H, S, views[v][0], views[v][1], r, 0, 110, 0, null, true);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.YELLOW);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        g.drawString(name + "   cut off: " + r.floating + "   apart: " + r.gapPairs, 10, 26);
        g.dispose();
        ImageIO.write(img, "png", new File(out, name + ".png"));
        // close-ups of the arms and strands, in their own colours (no marks), from the front and the side
        int cw = 900, chh = 900;
        BufferedImage close = new BufferedImage(cw * 2, chh, BufferedImage.TYPE_INT_RGB);
        draw(close, 0, 0, cw, chh, 6f, 0.4f, 0.1f, r, 0, 75 - st.lower, 0, null, false);
        draw(close, cw, 0, cw, chh, 6f, 0.4f + (float) Math.PI / 2, 0.1f, r, 0, 75 - st.lower, 0, null, false);
        g = close.createGraphics();
        g.setColor(Color.YELLOW);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        g.drawString(name + " (close)", 10, 26);
        g.dispose();
        ImageIO.write(close, "png", new File(out, name + "_close.png"));
    }

    /**
     * Draws the posed blocks into img (x0, y0, w, h) looking from yaw/pitch, centred on (cx, cy, cz), S pixels a block.
     * only: if not null, just these bones.
     */
    static void draw(BufferedImage img, int x0, int y0, int w, int h, float S, float yaw, float pitch, Result r, float cx, float cy, float cz, boolean[] only, boolean marks) {
        float cyaw = (float) Math.cos(yaw), syaw = (float) Math.sin(yaw), cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        float[] zb = new float[w * h];
        Arrays.fill(zb, Float.MAX_VALUE);
        int bg = 0x181a1e;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x0 + x, y0 + y, bg);
        for (int i = 0; i < N; i++) {
            if (only != null && !only[vb[i]]) continue;
            float x = r.px[i] - cx, y = r.py[i] - cy, z = r.pz[i] - cz;
            // turn about y, then tip
            float x1 = x * cyaw - z * syaw, z1 = x * syaw + z * cyaw;
            float y2 = y * cp - z1 * sp, z2 = y * sp + z1 * cp;
            float u = w / 2f + x1 * S, vv = h / 2f - y2 * S;
            float depth = z2;
            int sz = (int) Math.ceil(S * Math.max(1f, r.scale[vs[i]])) + 1;
            int col = marks && r.cut[i] ? 0xff00ff : marks && r.gapped[i] ? 0xff2020 : vcol[i];
            float shade = Math.max(0.45f, Math.min(1.1f, 0.8f - depth / 400f));
            if (glow[i] && (!marks || (!r.cut[i] && !r.gapped[i]))) shade = 1.1f;
            int cr = Math.min(255, (int) (((col >> 16) & 255) * shade)), cg = Math.min(255, (int) (((col >> 8) & 255) * shade)), cb = Math.min(255, (int) ((col & 255) * shade));
            int c = (cr << 16) | (cg << 8) | cb;
            int ui = (int) (u - sz / 2f), vi = (int) (vv - sz / 2f);
            for (int a = 0; a < sz; a++) for (int b = 0; b < sz; b++) {
                int px = ui + a, py = vi + b;
                if (px < 0 || py < 0 || px >= w || py >= h) continue;
                int o = py * w + px;
                if (depth < zb[o]) { zb[o] = depth; img.setRGB(x0 + px, y0 + py, c); }
            }
        }
    }
}
