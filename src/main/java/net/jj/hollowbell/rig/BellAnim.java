package net.jj.hollowbell.rig;

import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * How Hollowbell moves, with weight. Run once a tick on both sides (the server for hits and for where the things he
 * holds are, the client for drawing), and blended between the last two ticks when a frame is drawn.
 *
 * Two things are kept:
 *  - his body (how low he hangs, how he leans, the bell's squeeze, the glow...) as springs, each easing toward what
 *    is asked of it from wherever it is now, so a move started, finished or cut off halfway never makes him jump;
 *  - his arms and strands as chains of joints in water: each joint keeps its speed from tick to tick (and loses a
 *    little to the water), is pulled toward the shape the moment asks for, and is held at its length from the joint
 *    above. When he moves, the water holds them back, so they trail behind him, then swing back past and settle
 *    when he stops. They never go into the ground or up through his bell.
 */
public final class BellAnim {
    private final BellRig rig;
    /** the pose as of the end of the last tick (chains and body), and the one before, for blending frames */
    private final BellState now;
    private final float[][] prev, last;
    private final float[][] target, eased;
    private final float[] groundAt;
    /** per chain: how much a move has hold of it right now, eased in quickly and let go slowly */
    private final float[] held;
    /** per chain: the last step one of its joints was pushed by the ground or his bell (a knock, not a swing) */
    private final long[] knockedAt;
    private boolean started;
    private long steps;

    // body springs: value, speed, and the value a tick ago
    private static final int LOWER = 0, TX = 1, TZ = 2, SQUEEZE = 3, RIPPLE = 4, DEATH = 5, DROOP = 6, GLOW = 7, SWELL = 8, SHAKE = 9,
            SPIN = 10, DX = 11, DY = 12, DZ = 13, CLIMB = 14, SINK = 15, FOLD = 16, N = 17;
    private final float[] x = new float[N], v = new float[N], xl = new float[N];
    private float timeNow;

    /** what the entity tells the animation each tick */
    public static final class In {
        public float time;
        /** his speed and how far he moved this tick, both in model blocks */
        public float vx, vy, vz, shiftX, shiftY, shiftZ;
        /** his top speed, model blocks per tick */
        public float speedRef = 0.3f;
        /** the bell's pulse right now (0 relaxed, 1 a normal pulse, up to 1.7) */
        public float pulse;
        /** from his pods: hanging lower and leaning */
        public float hangLower, hangTiltX, hangTiltZ;
        /** sunk (lost his lift): 0 or 1 */
        public float sunk;
        /** ticks since he died, or -1 */
        public float dying = -1f;
        public boolean tired, red;
        /** the ground under his middle, in model blocks above his base (NaN if not known) */
        public float groundUnder = Float.NaN;
        /** the ground at a model x z, in model y (NaN if not known) */
        public Ground ground = (mx, mz) -> Float.NaN;
        /** filled by the move: how far the bell is brought down (0-1), the flip (a turn, x z), the spin, the glow... */
        public float drop, flipX, flipZ, spin, glow, podSwell, eggShake, squeezeAdd, lowerAdd;
        public boolean spinning;
        public void clearAsks() { drop = 0; flipX = 0; flipZ = 0; spin = 0; glow = 0; podSwell = 0; eggShake = 0; squeezeAdd = 0; lowerAdd = 0; spinning = false; }
    }

    public interface Ground { float at(float mx, float mz); }

    public BellAnim(BellRig rig) {
        this.rig = rig;
        this.now = new BellState(rig);
        int nc = rig.chains.length;
        prev = new float[nc][];
        last = new float[nc][];
        target = new float[nc][];
        eased = new float[nc][];
        held = new float[nc];
        knockedAt = new long[nc];
        java.util.Arrays.fill(knockedAt, -100);
        groundAt = new float[nc];
        java.util.Arrays.fill(groundAt, Float.NaN);
        for (int c = 0; c < nc; c++) {
            prev[c] = now.chain[c].clone();
            last[c] = now.chain[c].clone();
            target[c] = now.chain[c].clone();
            eased[c] = now.chain[c].clone();
        }
    }

    /** the state as of the last tick, for the moves to read and fill in */
    public BellState now() { return now; }

    /** forget the swinging: everything goes straight to where it is asked to be on the next tick */
    public void reset() { started = false; }

    // ------------------------------------------------------------------ one tick

    public void step(In in) {
        BellState st = now;
        st.time = in.time;
        timeNow = in.time;
        System.arraycopy(x, 0, xl, 0, N);
        for (int c = 0; c < rig.chains.length; c++) System.arraycopy(st.chain[c], 0, last[c], 0, st.chain[c].length);
        boolean first = !started;
        float shift = (float) Math.sqrt(in.shiftX * in.shiftX + in.shiftY * in.shiftY + in.shiftZ * in.shiftZ);
        if (shift > 40f) first = true;

        // how he's moving, smoothed a little
        spring(DX, in.vx, 0.12f, 1f, first);
        spring(DY, in.vy, 0.12f, 1f, first);
        spring(DZ, in.vz, 0.12f, 1f, first);
        float ref = Math.max(0.02f, in.speedRef);
        spring(CLIMB, Mth.clamp(x[DY] / (0.45f * ref), 0f, 1f), 0.06f, 1f, first);
        spring(SINK, Mth.clamp(-x[DY] / (0.35f * ref), 0f, 1f), 0.05f, 1f, first);

        // folded down: the drop, sunk, dying. The rim is brought right down onto the ground wherever that is
        float deathK = in.dying < 0 ? 0f : Mth.clamp(in.dying / 140f, 0f, 1f);
        float fold = Math.max(Math.max(in.drop, in.sunk), deathK);
        spring(FOLD, fold, 0.09f, 0.9f, first);
        // (the rest of the way down he flies himself)
        float ground = Float.isNaN(in.groundUnder) ? 0f : Mth.clamp(in.groundUnder, -40f, 20f);
        float sinkIn = in.dying < 0 ? 0f : Mth.clamp((in.dying - 190f) / 120f, 0f, 1f);
        float lowerT = in.hangLower + x[FOLD] * (rig.rimY - 12f - ground) + sinkIn * (rig.crownY + 10f) + in.lowerAdd + (in.tired ? 4f : 0f);
        spring(LOWER, lowerT, 0.06f, 0.8f, first);

        // leaning into the way he's going, heavily
        float hx = x[DX], hz = x[DZ];
        float h = (float) Math.sqrt(hx * hx + hz * hz);
        float lean = 0.2f * Math.min(1f, h / ref);
        float tx = in.hangTiltX + in.flipX, tz = in.hangTiltZ + in.flipZ + 0.22f * deathK;
        if (h > 1e-4f) { tx += hz / h * lean; tz += -hx / h * lean; }
        boolean flipping = in.flipX * in.flipX + in.flipZ * in.flipZ > 0.01f;
        spring(TX, tx, flipping ? 0.05f : 0.03f, 0.6f, first);
        spring(TZ, tz, flipping ? 0.05f : 0.03f, 0.6f, first);

        // the bell: the pulse squeezes it, climbing keeps it narrow, sinking opens it wide like a parachute
        float sq = in.pulse + 0.35f * x[CLIMB] - 1.0f * x[SINK] - 0.45f * (in.tired ? 1f : 0f) + in.squeezeAdd;
        spring(SQUEEZE, sq * (1f - deathK), 0.35f, 0.75f, first);
        spring(RIPPLE, 0.35f + 0.8f * Math.abs(in.pulse) + 0.5f * x[SINK], 0.1f, 1f, first);
        spring(DEATH, deathK, 0.08f, 1f, first);
        spring(DROOP, in.tired ? 1f : 0f, 0.05f, 1f, first);
        spring(GLOW, in.glow, 0.22f, 1f, first);
        spring(SWELL, in.podSwell, 0.15f, 0.9f, first);
        spring(SHAKE, in.eggShake, 0.2f, 1f, first);
        // turning: the whirlpool winds him round and he settles back square afterwards
        float spinT = in.spinning ? in.spin : (float) (Math.round(x[SPIN] / (2 * Math.PI)) * 2 * Math.PI);
        spring(SPIN, spinT, in.spinning ? 0.12f : 0.03f, 0.95f, first);

        st.lower = x[LOWER]; st.tiltX = x[TX]; st.tiltZ = x[TZ]; st.squeeze = x[SQUEEZE]; st.ripple = x[RIPPLE];
        st.death = x[DEATH]; st.droop = x[DROOP]; st.glow = x[GLOW]; st.podSwell = x[SWELL]; st.eggShake = x[SHAKE]; st.spin = x[SPIN];
        st.driftX = x[DX]; st.driftY = x[DY]; st.driftZ = x[DZ]; st.climb = x[CLIMB]; st.sink = x[SINK]; st.fold = x[FOLD];
        st.pulse = in.pulse; st.red = in.red;

        chains(in, first);
        started = true;
        steps++;
    }

    /** a spring toward want: k how stiff (per tick squared), z how damped (1 = no overshoot) */
    private void spring(int i, float want, float k, float z, boolean snap) {
        if (snap) { x[i] = want; v[i] = 0f; xl[i] = want; return; }
        float c = 2f * z * (float) Math.sqrt(k);
        v[i] += k * (want - x[i]) - c * v[i];
        x[i] += v[i];
    }

    // ------------------------------------------------------------------ the chains

    private final Matrix4f mBody = new Matrix4f(), mAnchor = new Matrix4f(), mInv = new Matrix4f();
    private final Quaternionf qRot = new Quaternionf(), qBody = new Quaternionf(), qLocal = new Quaternionf(), qTmp = new Quaternionf();
    private final Vector3f tv = new Vector3f(), tv2 = new Vector3f();
    private final float[] velScratch = new float[3 * 64];

    private void chains(In in, boolean first) {
        BellState st = now;
        rig.body(st, mBody);
        rig.bodyRotation(st, qBody);
        mBody.invert(mInv);
        int nc = rig.chains.length;
        long tick = steps;
        for (int c = 0; c < nc; c++) {
            BellRig.Chain ch = rig.chains[c];
            float[] p = st.chain[c], q = prev[c], raw = target[c], tg = eased[c];
            int m = ch.points() - 1;
            rig.anchor(st, ch, mBody, mAnchor);
            targets(ch, st, mAnchor, raw);
            if (first) {
                System.arraycopy(raw, 0, tg, 0, raw.length);
                System.arraycopy(raw, 0, p, 0, raw.length);
                System.arraycopy(raw, 0, q, 0, raw.length);
                System.arraycopy(raw, 0, last[c], 0, raw.length);
                groundAt[c] = in.ground.at(raw[3 * m], raw[3 * m + 2]);
                held[c] = 0f;
                continue;
            }
            // a move takes hold of a chain quickly and lets go of it slowly, and the shape it asks for is eased
            // toward the same way: so a move started, ended or cut off halfway never yanks an arm or a strand
            boolean scripted = scripted(ch, st);
            held[c] += ((scripted ? 1f : 0f) - held[c]) * (scripted ? 0.3f : 0.06f);
            float ease = scripted ? 0.35f : 0.07f + 0.05f * (1f - held[c]);
            tg[0] = raw[0]; tg[1] = raw[1]; tg[2] = raw[2];
            for (int i = 3; i < raw.length; i++) {
                // eased in the frame of the joint above, so the ease never drags a chain away from where it hangs
                int j = i - 3;
                float relRaw = raw[i] - raw[j], relNow = tg[i] - tg[j];
                tg[i] = tg[j] + relNow + (relRaw - relNow) * ease;
            }
            // the ground under this one, looked at now and then (and kept level with the world as he rises and sinks)
            if ((tick + c) % 4 == 0) groundAt[c] = in.ground.at(p[3 * m], p[3 * m + 2]);
            else groundAt[c] -= in.shiftY;
            float hk = held[c];
            float k0, damp, abs;
            if (ch.arm) { k0 = Mth.lerp(hk, 0.12f, 0.5f); damp = Mth.lerp(hk, 0.88f, 0.82f); abs = Mth.lerp(hk, 0.3f, 0.85f); }
            else { k0 = Mth.lerp(hk, 0.05f, 0.3f); damp = Mth.lerp(hk, 0.93f, 0.82f); abs = Mth.lerp(hk, 0.1f, 0.7f); }
            // the top joint goes where it hangs from
            p[0] = tg[0]; p[1] = tg[1]; p[2] = tg[2];
            q[0] = tg[0]; q[1] = tg[1]; q[2] = tg[2];
            for (int i = 1; i <= m; i++) {
                int o = 3 * i;
                // he moved: the water holds the joint where it was
                p[o] -= in.shiftX; p[o + 1] -= in.shiftY; p[o + 2] -= in.shiftZ;
                q[o] -= in.shiftX; q[o + 1] -= in.shiftY; q[o + 2] -= in.shiftZ;
                float k = k0 * (1f - 0.45f * i / m);
                // where it wants to be: its piece of the asked-for shape hung off the joint above, mixed with where
                // the shape puts it outright
                float wx = (p[o - 3] + tg[o] - tg[o - 3]) * (1f - abs) + tg[o] * abs;
                float wy = (p[o - 2] + tg[o + 1] - tg[o - 2]) * (1f - abs) + tg[o + 1] * abs;
                float wz = (p[o - 1] + tg[o + 2] - tg[o - 1]) * (1f - abs) + tg[o + 2] * abs;
                float ux = p[o] - q[o], uy = p[o + 1] - q[o + 1], uz = p[o + 2] - q[o + 2];
                // water holds back a fast swing much harder than a slow one
                float sp = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
                float dmp = damp / (1f + 0.035f * sp);
                float vx = ux * dmp, vy = uy * dmp, vz = uz * dmp;
                q[o] = p[o]; q[o + 1] = p[o + 1]; q[o + 2] = p[o + 2];
                float mx = vx + k * (wx - p[o]), my = vy + k * (wy - p[o + 1]), mz = vz + k * (wz - p[o + 2]);
                // however hard it's swung, nothing moves further than this in a tick
                float ml = mx * mx + my * my + mz * mz, cap = ch.arm ? 14f : 10f;
                if (ml > cap * cap) { float f = cap / (float) Math.sqrt(ml); mx *= f; my *= f; mz *= f; }
                p[o] += mx; p[o + 1] += my; p[o + 2] += mz;
            }
            float gy = groundAt[c];
            for (int i = 1; i <= m; i++) {
                int o = 3 * i;
                // held at its length from the joint above (the length the moment asks for)
                float lx = tg[o] - tg[o - 3], ly = tg[o + 1] - tg[o - 2], lz = tg[o + 2] - tg[o - 1];
                float len = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
                float dx = p[o] - p[o - 3], dy = p[o + 1] - p[o - 2], dz = p[o + 2] - p[o - 1];
                float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d < 1e-5f) { dx = lx; dy = ly; dz = lz; d = Math.max(1e-5f, len); }
                // a strand is like wet rope: pushed together it bunches up a little rather than kicking out
                // sideways, pulled it gives a little rather than yanking tight, and either way it eases back to
                // its length over a few ticks. An arm keeps its length
                float want = len;
                if (!ch.arm) want = Mth.clamp(len + (d - len) * (d < len ? 0.9f : 0.55f), 0.6f * len, 1.12f * len);
                p[o] = p[o - 3] + dx / d * want;
                p[o + 1] = p[o - 2] + dy / d * want;
                p[o + 2] = p[o - 1] + dz / d * want;
                // never up through his own bell: kept under it, in his own body's frame
                if (!ch.arm) {
                    tv.set(p[o], p[o + 1], p[o + 2]);
                    mInv.transformPosition(tv);
                    float top = rig.rimY - 2f;
                    if (tv.y > top && tv.x * tv.x + tv.z * tv.z < 95f * 95f) {
                        tv.y = top;
                        knockedAt[c] = steps;
                        mBody.transformPosition(tv);
                        p[o] = tv.x; p[o + 1] = tv.y; p[o + 2] = tv.z;
                    }
                }
                // never into the ground: it rests on it and drags
                if (!Float.isNaN(gy)) {
                    float floor = gy + (ch.arm ? 1.2f : 0.5f);
                    if (p[o + 1] < floor) {
                        p[o + 1] = floor;
                        knockedAt[c] = steps;
                        if (q[o + 1] < floor) q[o + 1] = floor;
                        q[o] += (p[o] - q[o]) * 0.35f;
                        q[o + 2] += (p[o + 2] - q[o + 2]) * 0.35f;
                    }
                }
            }
            // the strand's own stiffness: each joint's swing is blended a little with its neighbours', which takes out
            // any quick wobble between joints and leaves the slow, heavy swing
            float[] vel = velScratch;
            for (int i = 1; i <= m; i++) { vel[3 * i] = p[3 * i] - q[3 * i]; vel[3 * i + 1] = p[3 * i + 1] - q[3 * i + 1]; vel[3 * i + 2] = p[3 * i + 2] - q[3 * i + 2]; }
            vel[0] = 0; vel[1] = 0; vel[2] = 0;
            for (int i = 1; i <= m; i++) {
                int o = 3 * i, up = o - 3, dn = i < m ? o + 3 : o;
                for (int a = 0; a < 3; a++) {
                    float avg = (vel[up + a] + vel[dn + a]) * 0.5f;
                    float nv = vel[o + a] * 0.72f + avg * 0.28f;
                    q[o + a] = p[o + a] - nv;
                }
            }
        }
    }

    /** was this chain knocked by the ground or his bell in the last few ticks (for the tests) */
    public boolean knocked(int c) {
        for (int k = c; k >= 0; k = rig.chains[k].parentChain) if (steps - 1 - knockedAt[k] <= 3) return true;
        return false;
    }

    private static boolean scripted(BellRig.Chain ch, BellState st) {
        if (ch.arm) return ch.index == st.slamArm || ch.index == st.wrapArm || (ch.index < st.armRaise.length && Math.abs(st.armRaise[ch.index]) > 0.03f);
        return ch.index == st.grabStrand || ch.index == st.lashStrand;
    }

    // ------------------------------------------------------------------ the shape asked for

    /** the joints of a chain in the shape the moment asks for, hung from its anchor */
    private void targets(BellRig.Chain ch, BellState st, Matrix4f anchor, float[] out) {
        anchor.transformPosition(tv.set(ch.joints[0]));
        out[0] = tv.x; out[1] = tv.y; out[2] = tv.z;
        qRot.set(qBody);
        int m = ch.points() - 1;
        for (int i = 0; i < m; i++) {
            qLocal.identity();
            float stretch = 1f;
            if (ch.arm) armLocal(ch, i, m, st, qLocal);
            else {
                strandLocal(ch, i, m, st, qLocal);
                if (ch.index == st.grabStrand) stretch = 1f - 0.8f * st.grabLift;
            }
            qRot.mul(qLocal);
            tv2.set(ch.joints[i + 1]).sub(ch.joints[i]).mul(stretch);
            qRot.transform(tv2);
            out[3 * i + 3] = out[3 * i] + tv2.x;
            out[3 * i + 4] = out[3 * i + 1] + tv2.y;
            out[3 * i + 5] = out[3 * i + 2] + tv2.z;
        }
    }

    private static final Vector3f UP = new Vector3f(0, 1, 0);

    /** a turn that swings whatever hangs below the pivot toward direction (dx, dz) by angle radians */
    static Quaternionf swing(Quaternionf out, float dx, float dz, float angle) {
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-6f || Math.abs(angle) < 1e-6f) return out.identity();
        return out.identity().rotateAxis(angle, -dz / len, 0f, dx / len);
    }

    private void armLocal(BellRig.Chain ch, int sg, int m, BellState st, Quaternionf local) {
        BellRig.ArmDef A = rig.arms[ch.index];
        float dx = (float) Math.cos(A.angle()), dz = (float) Math.sin(A.angle());
        float t = st.time, ph = ch.index * 1.3f;
        // swimming: each pulse swings them out a little, the lower segments later and more
        float out = st.armSwing * (0.05f + 0.03f * sg) * (st.pulse - 0.35f);
        // a slow curl in and out while he idles
        if (sg > 0) out -= 0.05f * (1f + (float) Math.sin(t * 0.03f + ph)) * (0.6f + 0.4f * sg / (float) m);
        // climbing: folded in under him; sinking: spread out and up
        float open = Math.max(st.sink, st.spread);
        if (sg == 0) out += -0.28f * st.climb + 0.45f * open;
        else out += 0.06f * st.climb + 0.14f * open;
        // folded out when the bell comes down
        if (sg == 0) out += st.fold * 0.85f; else out -= st.fold * 0.2f;
        int segK = Math.min(sg, 2);
        if (A.k() == st.slamArm) {
            out += BellRig.slam(st.slamT, segK);
            // coming down, the arm reaches for what it's slamming
            if (sg == 0 && st.slamT > 0.45f) {
                Vector3f j0 = A.joints()[0];
                float wx = st.slamX - j0.x, wz = st.slamZ - j0.z;
                float d = (float) Math.sqrt(wx * wx + wz * wz), hgt = Math.max(20f, j0.y - st.slamY);
                float reach = BellRig.smooth((st.slamT - 0.45f) / 0.15f) * (1f - BellRig.smooth((st.slamT - 0.8f) / 0.2f));
                // take off the arm's own lean inward, so it points at the spot
                float built = (float) Math.atan2(Math.hypot(A.tip().x - j0.x, A.tip().z - j0.z), j0.y - A.tip().y);
                local.mul(swing(qTmp, wx, wz, reach * Math.max(0f, (float) Math.atan2(d, hgt) - 0.3f * built)));
            }
        }
        // the arm storm: every arm up, then down one after another
        float r = A.k() < st.armRaise.length ? st.armRaise[A.k()] : 0f;
        if (r > 0f) out += r * (segK == 0 ? 1.2f : segK == 1 ? 0.35f : 0.2f);
        else if (r < 0f) out -= r * (segK == 0 ? -0.5f : -1.6f);
        // the whirlpool: swung round the way he turns
        if (st.swirl > 0f) local.mul(swing(qTmp, -dz, dx, 0.3f * st.swirl * (sg == 0 ? 1f : 0.4f)));
        if (A.k() == st.wrapArm) {
            // reach out toward it, then the lower segments curl in round it
            float wx = st.wrapX - A.joints()[0].x, wz = st.wrapZ - A.joints()[0].z;
            float w = st.wrapAmt;
            if (sg == 0) local.mul(swing(qTmp, wx, wz, 0.45f * w));
            else out -= 0.8f * w;
        }
        local.mul(swing(qTmp, dx, dz, out));
        // trailing behind as he goes (the water does the rest)
        float sp = (float) Math.sqrt(st.driftX * st.driftX + st.driftZ * st.driftZ);
        if (sp > 1e-4f) local.premul(swing(qTmp, -st.driftX, -st.driftZ, Math.min(0.3f, sp * 0.8f) / (m + 1) * (sg == 0 ? 0.5f : 1f)));
    }

    private void strandLocal(BellRig.Chain ch, int sg, int m, BellState st, Quaternionf local) {
        BellRig.StrandDef S = rig.strands[ch.index];
        float t = st.time;
        float ph = S.k() * 2.39f;
        // sway, a little more at the bottom
        float amp = 0.018f + 0.006f * sg;
        local.rotateX(amp * (float) Math.sin(t * 0.045f + ph + sg * 0.7f)).rotateZ(amp * (float) Math.cos(t * 0.039f + ph * 1.3f + sg * 0.6f));
        Vector3f top = S.joints()[0];
        float ox = top.x, oz = top.z;
        float r = (float) Math.sqrt(ox * ox + oz * oz);
        if (r < 1f) { ox = (float) Math.cos(ph); oz = (float) Math.sin(ph); r = 1f; }
        // trailing behind as he drifts
        float sp = (float) Math.sqrt(st.driftX * st.driftX + st.driftZ * st.driftZ);
        if (sp > 1e-4f) local.premul(swing(qTmp, -st.driftX, -st.driftZ, Math.min(0.5f, sp * 1.2f) / (m + 1) * (sg == 0 ? 0.6f : 1f)));
        // climbing: drawn in to a bundle under him. Sinking: floating up and out
        if (sg == 0 && st.climb > 0f) local.premul(swing(qTmp, -ox, -oz, 0.1f * st.climb * Math.min(1f, r / 50f)));
        float open = Math.max(st.sink, st.spread);
        if (open > 0f) local.premul(swing(qTmp, ox, oz, open * (sg == 0 ? 0.3f : 0.12f)));
        // buckling under him when the bell comes down: zig zag, out
        if (st.fold > 0f) {
            float z = (sg % 2 == 0 ? 1f : -1.6f) * st.fold * 1.05f * 4f / (m + 1);
            if (sg == 0) z = st.fold * 0.7f;
            local.premul(swing(qTmp, ox, oz, z));
        }
        // the sweep: everything swung one way
        if (st.sweep != 0f) {
            float dx = (float) Math.cos(st.sweepDir), dz = (float) Math.sin(st.sweepDir);
            local.premul(swing(qTmp, dx, dz, st.sweep * (sg == 0 ? 0.45f : 0.18f)));
        }
        // the ends flicked up and out: the stingers let fly
        if (st.flick > 0f && sg >= m - 2) local.premul(swing(qTmp, ox, oz, 0.55f * st.flick));
        // the whirlpool: swung round and out
        if (st.swirl > 0f) local.premul(swing(qTmp, -oz, ox, st.swirl * (sg == 0 ? 0.4f : 0.12f))).premul(swing(qTmp, ox, oz, 0.15f * st.swirl));
        // the lash: one strand wound back, then whipped across
        if (S.k() == st.lashStrand) {
            float k = st.lashT;
            float a = k < 0.4f ? -0.6f * BellRig.smooth(k / 0.4f) : k < 0.6f ? Mth.lerp(BellRig.smooth((k - 0.4f) / 0.2f), -0.6f, 1.1f) : 1.1f * (1f - BellRig.smooth((k - 0.6f) / 0.4f));
            local.premul(swing(qTmp, (float) Math.cos(st.lashDir), (float) Math.sin(st.lashDir), a * (sg == 0 ? 0.6f : 0.25f)));
        }
        // the curtain: the bottoms drawn in round the one inside
        if (st.curtain > 0f && sg == 0) {
            float tx = st.curtainX - top.x, tz = st.curtainZ - top.z;
            float d = (float) Math.sqrt(tx * tx + tz * tz);
            float h = Math.max(20f, top.y);
            float want = (float) Math.atan2(Math.max(0f, d - 6f), h);
            local.premul(swing(qTmp, tx, tz, want * st.curtain));
        }
        // grabbing: the strand reaches for it
        if (S.k() == st.grabStrand && sg == 0) {
            float tx = st.grabX - top.x, tz = st.grabZ - top.z;
            float d = (float) Math.sqrt(tx * tx + tz * tz);
            float h = Math.max(10f, top.y - st.grabY);
            local.premul(swing(qTmp, tx, tz, (float) Math.atan2(d, h) * st.grabReach));
        }
    }

    // ------------------------------------------------------------------ a frame

    /** the pose between the last tick and this one, for drawing */
    public void fill(BellState out, float partial) {
        float k = Mth.clamp(partial, 0f, 1f);
        BellState st = now;
        out.time = timeNow + k;
        out.lower = Mth.lerp(k, xl[LOWER], x[LOWER]);
        out.tiltX = Mth.lerp(k, xl[TX], x[TX]);
        out.tiltZ = Mth.lerp(k, xl[TZ], x[TZ]);
        out.squeeze = Mth.lerp(k, xl[SQUEEZE], x[SQUEEZE]);
        out.ripple = Mth.lerp(k, xl[RIPPLE], x[RIPPLE]);
        out.death = Mth.lerp(k, xl[DEATH], x[DEATH]);
        out.droop = Mth.lerp(k, xl[DROOP], x[DROOP]);
        out.glow = Mth.lerp(k, xl[GLOW], x[GLOW]);
        out.podSwell = Mth.lerp(k, xl[SWELL], x[SWELL]);
        out.eggShake = Mth.lerp(k, xl[SHAKE], x[SHAKE]);
        out.spin = Mth.lerp(k, xl[SPIN], x[SPIN]);
        out.driftX = st.driftX; out.driftY = st.driftY; out.driftZ = st.driftZ;
        out.climb = st.climb; out.sink = st.sink; out.fold = st.fold; out.pulse = st.pulse; out.red = st.red;
        for (int c = 0; c < st.chain.length; c++) {
            float[] a = last[c], b = st.chain[c], o = out.chain[c];
            if (k >= 1f) System.arraycopy(b, 0, o, 0, b.length);
            else for (int i = 0; i < o.length; i++) o[i] = a[i] + (b[i] - a[i]) * k;
        }
        if (out != st) {
            System.arraycopy(st.podPopped, 0, out.podPopped, 0, st.podPopped.length);
            System.arraycopy(st.podGrowth, 0, out.podGrowth, 0, st.podGrowth.length);
            System.arraycopy(st.eggGone, 0, out.eggGone, 0, st.eggGone.length);
            out.grabStrand = st.grabStrand; out.grabLift = st.grabLift;
        }
    }
}
