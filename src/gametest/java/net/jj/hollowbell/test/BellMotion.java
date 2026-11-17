package net.jj.hollowbell.test;

import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.rig.BellAnim;
import net.jj.hollowbell.rig.BellModel;
import net.jj.hollowbell.rig.BellPieces;
import net.jj.hollowbell.rig.BellRig;
import net.jj.hollowbell.rig.BellState;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * One of him stepped along tick by tick off the game, as the entity steps him (his swim beat, drifting over ground,
 * the move of the moment posed onto his body and swinging chains), measuring how far any corner of any part of him
 * goes in a tick (also between ticks, as the client draws him) and how much that step changes from one tick to the
 * next (the jolt). Model blocks throughout.
 */
final class BellMotion {
    static final BellRig RIG = BellRig.get();
    /** the eight corners of each part as built, and its middle */
    private static float[][] corners;

    static float[][] corners() {
        if (corners != null) return corners;
        BellModel m = BellModel.get();
        float[][] c = new float[RIG.boneCount()][];
        for (int b = 0; b < c.length; b++) {
            float[] bb = b < m.bounds.length ? m.bounds[b] : null;
            if (bb == null) {
                Vector3f p = RIG.pivot[b];
                c[b] = new float[] {p.x, p.y, p.z};
                continue;
            }
            float[] o = new float[27];
            int n = 0;
            for (int i = 0; i < 8; i++) {
                o[n++] = (i & 1) == 0 ? bb[0] : bb[3];
                o[n++] = (i & 2) == 0 ? bb[1] : bb[4];
                o[n++] = (i & 4) == 0 ? bb[2] : bb[5];
            }
            o[n++] = (bb[0] + bb[3]) / 2;
            o[n++] = (bb[1] + bb[4]) / 2;
            o[n] = (bb[2] + bb[5]) / 2;
            c[b] = o;
        }
        return corners = c;
    }

    /** the land under him: its height at a world x z (model blocks, his base at 0) */
    interface Land {
        float at(float x, float z);
    }

    /** level ground this far under the ends of his strands */
    static Land flat(float under) {
        return (x, z) -> -under;
    }

    /**
     * uneven ground: rolling hills in whole blocks (a block is 1/size of his blocks), so a strand dragged over it
     * goes up and down steps
     */
    static Land rough(float under, float size) {
        float blk = 1f / size;
        return (x, z) -> {
            float wx = x * size, wz = z * size;
            float hgt = 2.5f * Mth.sin(wx / 9f) + 2f * Mth.cos(wz / 7f) + 1.2f * Mth.sin((wx + wz) / 4f);
            return -under + Mth.floor(hgt) * blk;
        };
    }

    final BellAnim anim = new BellAnim(RIG);
    final BellAnim.In in = new BellAnim.In();
    final BellState mid = new BellState(RIG), was = new BellState(RIG);
    final Matrix4f[] pose = RIG.newPose(), before = RIG.newPose();
    private final Matrix4f[] hang = BellPieces.get().newHang(), slices = BellPieces.get().newPose();
    Land land = flat(14f);
    /** where he is over the land (model blocks) and how he drifts this tick */
    float wx, wy, wz, vx, vz, vy;

    int tick;
    /** the move of the moment, its tick, and what it aims at */
    int move = Moves.NONE, arg;

    float moveT, phase = -1f;
    float tx = 55f, ty = 4f, tz = 25f;

    /** the most a corner moved this tick (and the most between ticks, times four), and its jolt */
    float jump, jolt;

    String bone = "", joltBone = "";
    private float[] pts, ptsWas, stepWas;
    private int seen;

    /** a tick of him; measures how far every corner of him went (from the last tick, and between) */
    void step() {
        in.time = 1000 + tick++;
        in.speedRef = 0.3f;
        float ox = wx, oy = wy, oz = wz;
        in.groundUnder = land.at(wx, wz) - wy;
        in.ground = (mx, mz) -> land.at(ox + mx, oz + mz) - oy;
        in.pulse = Moves.pulseCurve(tick % 60, 1f);
        in.vx = vx;
        in.vy = vy;
        in.vz = vz;
        in.shiftX = vx;
        in.shiftY = vy;
        in.shiftZ = vz;
        wx += vx;
        wy += vy;
        wz += vz;
        Moves.pose(RIG, anim.now(), in, move, moveT, arg, tx, ty, tz, phase);
        anim.step(in);
        // what lies on the ground is fitted to lie on its top every fourth tick, as he does it in the game
        if (tick % 4 == 0 && anim.anyResting()) {
            anim.fill(mid, 1f);
            RIG.computePose(mid, pose, hang);
            BellPieces.get().pose(mid, pose, hang, slices);
            anim.fitSoles(slices, BellPieces.get(), BellModel.get());
        }
        measure();
    }

    void measure() {
        float[][] c = corners();
        Vector3f a = new Vector3f(), b = new Vector3f();
        float most = 0f;
        anim.fill(was, 0f);
        RIG.computePose(was, before);
        for (int q = 1; q <= 4; q++) {
            anim.fill(mid, q / 4f);
            RIG.computePose(mid, pose);
            for (int bn = 0; bn < c.length; bn++) {
                float[] o = c[bn];
                for (int i = 0; i + 2 < o.length; i += 3) {
                    pose[bn].transformPosition(o[i], o[i + 1], o[i + 2], a);
                    before[bn].transformPosition(o[i], o[i + 1], o[i + 2], b);
                    float d = a.distance(b) * 4f;
                    if (d > most) {
                        most = d;
                        bone = RIG.boneNames[bn];
                    }
                }
            }
            for (int bn = 0; bn < c.length; bn++) before[bn].set(pose[bn]);
        }
        if (pts == null) {
            int n = 0;
            for (float[] o : c) n += o.length;
            pts = new float[n];
            ptsWas = new float[n];
            stepWas = new float[n];
        }
        int n = 0;
        for (int bn = 0; bn < c.length; bn++)
            for (int i = 0; i + 2 < c[bn].length; i += 3) {
                pose[bn].transformPosition(c[bn][i], c[bn][i + 1], c[bn][i + 2], a);
                pts[n++] = a.x;
                pts[n++] = a.y;
                pts[n++] = a.z;
            }
        float worst = 0f;
        int jb = 0;
        for (int i = 0; i + 2 < n; i += 3) {
            float sx = pts[i] - ptsWas[i], sy = pts[i + 1] - ptsWas[i + 1], sz = pts[i + 2] - ptsWas[i + 2];
            if (seen >= 2) {
                float jx = sx - stepWas[i], jy = sy - stepWas[i + 1], jz = sz - stepWas[i + 2];
                float j2 = jx * jx + jy * jy + jz * jz;
                if (j2 > worst) {
                    worst = j2;
                    jb = i;
                }
            }
            stepWas[i] = sx;
            stepWas[i + 1] = sy;
            stepWas[i + 2] = sz;
        }
        System.arraycopy(pts, 0, ptsWas, 0, n);
        seen++;
        jolt = (float) Math.sqrt(worst);
        for (int bn = 0, at = 0; bn < c.length; at += c[bn].length, bn++)
            if (jb < at + c[bn].length) {
                joltBone = RIG.boneNames[bn];
                break;
            }
        jump = most;
    }

    void run(int ticks) {
        for (int i = 0; i < ticks; i++) step();
    }

    /** the server's count for the moves that need one (the dive's turn over), and his drift in them */
    void serverSide(int m, float t) {
        phase = -1f;
        vx = vy = vz = 0f;
        if (m == Moves.SKY_DIVE) {
            int turn = Moves.DIVE_CLIMB, fall = turn + Moves.DIVE_TURN, back = fall + Moves.DIVE_FALL;
            if (t < turn) phase = 0f;
            else if (t < fall) phase = smooth((t - turn) / Moves.DIVE_TURN);
            else if (t < back + 8) phase = 1f;
            else phase = 1f - smooth((t - back - 8) / (Moves.DIVE_BACK - 8));
            if (t < turn) vy = 0.2f;
            else if (t >= fall && t < back) {
                vx = 0.1f;
                vy = -0.4f;
                vz = 0.05f;
            }
        }
        if (m == Moves.DROP && t > Moves.DROP_WIND && t < Moves.DROP_WIND + Moves.DROP_FALL) vy = -0.5f;
    }

    /** which arm or strand a move works with */
    static int argFor(int m) {
        return switch (m) {
            case Moves.SLAM, Moves.WRAP -> 1;
            case Moves.GRAB, Moves.HARVEST, Moves.LASH -> 30;
            default -> 0;
        };
    }

    static float smooth(float k) {
        k = Mth.clamp(k, 0f, 1f);
        return k * k * (3 - 2 * k);
    }
}
