package net.jj.hollowbell.rig;

import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The way a strand carries somebody up onto his crown ("ride him"). Shared by the server (where the one carried
 * really is) and the client (drawing the strand), so both lay the strand out the same way.
 *
 * The one carried goes up his side just outside his arms, round the edge of the rim and over the dome to the top,
 * then is let down onto the crown. The way they go keeps a set distance d from every part of him (all the way
 * round, with room for the bell to swell a little), so nothing of him is ever in the way. The strand holding them
 * is a rope from where it hangs under the rim to them: straight while it can be, and once they are round the rim
 * it lies along the same way, over the rim and the dome.
 *
 * Everything here is in his rest space (as built, before he leans or turns), in the upright half-plane through the
 * strand's root: r out from his middle, y up.
 */
public final class CarryPath {
    private static final int Y0 = -4;
    /** per block row from Y0: how far out any part of him reaches, all the way round (the bell with room to swell) */
    private static float[] all;
    /** the same for the dome, the rim and the crown only (what the rope must not cut through) */
    private static float[] shell;
    private static int top;
    /** how far out the rim goes (as built, all the way round) */
    private static float rimEdge;
    private static int[] candidates;
    private static final Map<Long, CarryPath> CACHE = new ConcurrentHashMap<>();

    /** the strand, its angle round him, and how far from him the one carried is kept */
    public final int strand;
    public final float theta, d;
    /** the way, sampled every block: r and y, and the height at the very top (straight over the crown) */
    private final float[] tr, ty;
    private final float yTop;
    /** the lowest point on the way from which the rope from the root no longer has to go round the rim */
    private final float visibleTo;
    /** where along the way the part going straight up ends (after it, the way comes in over the top) */
    private final int sideEnd;

    private CarryPath(BellRig rig, int k, float d) {
        build(rig);
        this.strand = k;
        this.d = d;
        Vector3f root = rig.strands[k].joints()[0];
        this.theta = (float) Math.atan2(root.z, root.x);
        float lo = firstRow() - d + 0.5f;
        yTop = top + d;
        // sample the edge of everything within d of him, from low on his side to straight over his crown
        List<float[]> raw = new ArrayList<>();
        for (float y = lo; y <= yTop; y += 0.05f) raw.add(new float[]{Math.max(0f, offset(y, d)), y});
        raw.add(new float[]{0f, yTop});
        // the same, every block along it
        List<float[]> even = new ArrayList<>();
        even.add(raw.get(0));
        float carry = 0f;
        float[] prev = raw.get(0);
        for (int i = 1; i < raw.size(); i++) {
            float[] p = raw.get(i);
            float seg = (float) Math.hypot(p[0] - prev[0], p[1] - prev[1]);
            float pos = 1f - carry;
            while (pos <= seg) {
                float f = pos / seg;
                even.add(new float[]{prev[0] + (p[0] - prev[0]) * f, prev[1] + (p[1] - prev[1]) * f});
                pos += 1f;
            }
            carry = seg - (pos - 1f);
            prev = p;
        }
        float[] last = raw.get(raw.size() - 1), el = even.get(even.size() - 1);
        if (Math.hypot(last[0] - el[0], last[1] - el[1]) > 1e-3) even.add(last);
        tr = new float[even.size()];
        ty = new float[even.size()];
        int se = 0;
        for (int i = 0; i < tr.length; i++) {
            tr[i] = even.get(i)[0];
            ty[i] = even.get(i)[1];
            if (i > 0 && ty[i] > ty[se]) se = i;
        }
        sideEnd = se;
        // how far up the rope from the root can go straight to the one carried without cutting the rim
        float ra = (float) Math.hypot(root.x, root.z), ya = root.y;
        int vis = 0;
        for (int i = 0; i < tr.length; i++) {
            if (!clearOfShell(ra, ya, tr[i], ty[i])) break;
            vis = i;
        }
        visibleTo = vis;
    }

    /** the way for strand k with the one carried kept d (model blocks) from him */
    public static CarryPath get(BellRig rig, int k, float d) {
        float dq = Math.max(1f, Math.round(d * 2f) / 2f);
        long key = ((long) k << 32) | Float.floatToIntBits(dq);
        return CACHE.computeIfAbsent(key, x -> new CarryPath(rig, k, dq));
    }

    /**
     * How far from him (in model blocks) the one carried is kept: half their size (their box, from its middle to a
     * corner) and a margin, scaled to him; never less than 10, so the strand has room to bend round the rim.
     */
    public static float clearance(float width, float height, float scale) {
        float rho = (float) Math.sqrt(2 * (width * 0.5f) * (width * 0.5f) + (height * 0.5f) * (height * 0.5f));
        return Math.max(rho / Math.max(0.01f, scale) + 3f, 10f);
    }

    /** the strands he carries people with: long ones hanging from the rim, nothing hanging from them, between two arms */
    public static int[] candidates(BellRig rig) {
        build(rig);
        return candidates;
    }

    /** how far out from his middle the way is at a height (on his side, below the dome's top) */
    public float radiusAt(float y) {
        if (y <= ty[0]) return tr[0];
        for (int i = 1; i <= sideEnd; i++) if (ty[i] >= y) {
            float f = (y - ty[i - 1]) / Math.max(1e-4f, ty[i] - ty[i - 1]);
            return tr[i - 1] + (tr[i] - tr[i - 1]) * f;
        }
        return tr[sideEnd];
    }

    // ------------------------------------------------------------------ one carry

    /**
     * One carry: from p0 (the middle of the one picked up, rest space) to yEnd straight over the crown (the middle
     * of them once they sit on it). Worked out afresh each tick; cheap.
     */
    public final class Run {
        final float l0, lTrack, lEnd, lBlend;
        final float dx, dy, dz;
        final float yEnd;

        public Run(Vector3f p0, float yEnd) {
            this.yEnd = Math.min(yEnd, yTop);
            l0 = arcAtY(p0.y);
            Vector3f t0 = track(l0, new Vector3f());
            dx = p0.x - t0.x; dy = p0.y - t0.y; dz = p0.z - t0.z;
            lTrack = tr.length - 1;
            lEnd = lTrack + (yTop - this.yEnd);
            float off = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            lBlend = Mth.clamp(1.5f * off + 10f, 10f, Math.max(10f, 0.4f * (lEnd - l0)));
        }

        /** how long a rope from the root to the one carried at progress u has to be, going round his rim and dome */
        public float need(Vector3f root, float u) {
            float[] pts = new float[3 * maxRopePoints()];
            int n = rope(root, u, pts);
            float l = 0f;
            for (int i = 1; i < n; i++) l += d3(pts, i - 1, pts, i);
            return l;
        }

        /** how long the whole way is, model blocks */
        public float length() { return lEnd - l0; }

        /** where the middle of the one carried is at progress u (0 picked up, 1 over the crown), rest space */
        public Vector3f at(float u, Vector3f out) {
            float l = l0 + Mth.clamp(u, 0f, 1f) * (lEnd - l0);
            track(l, out);
            float k = 1f - smooth((l - l0) / lBlend);
            return out.add(dx * k, dy * k, dz * k);
        }

        /**
         * The strand's shape at progress u: a rope from its root to the one carried, going round the rim and over
         * the dome the way they went. Filled into pts as x y z triples; returns how many points.
         */
        public int rope(Vector3f root, float u, float[] pts) {
            float l = l0 + Mth.clamp(u, 0f, 1f) * (lEnd - l0);
            int n = 0;
            pts[n++] = root.x; pts[n++] = root.y; pts[n++] = root.z;
            float lv = Math.max(visibleTo, l0);
            Vector3f v = new Vector3f();
            if (l > lv + 0.5f) {
                track(lv, v);
                pts[n++] = v.x; pts[n++] = v.y; pts[n++] = v.z;
                for (int i = (int) Math.floor(lv) + 1; i < l && n + 6 <= pts.length; i++) {
                    track(i, v);
                    pts[n++] = v.x; pts[n++] = v.y; pts[n++] = v.z;
                }
            }
            at(u, v);
            pts[n++] = v.x; pts[n++] = v.y; pts[n++] = v.z;
            return n / 3;
        }
    }

    /** the most points a rope can have */
    public int maxRopePoints() { return tr.length + 8; }

    /** a point on the way, l blocks along it (past the end: straight down onto the crown) */
    private Vector3f track(float l, Vector3f out) {
        float r, y;
        int n = tr.length - 1;
        if (l >= n) { r = 0f; y = yTop - (l - n); }
        else if (l <= 0f) { r = tr[0]; y = ty[0]; }
        else {
            int i = (int) Math.floor(l);
            float f = l - i;
            r = tr[i] + (tr[i + 1] - tr[i]) * f;
            y = ty[i] + (ty[i + 1] - ty[i]) * f;
        }
        return out.set(r * (float) Math.cos(theta), y, r * (float) Math.sin(theta));
    }

    /** how far along the way its side first reaches a height */
    private float arcAtY(float y) {
        if (y <= ty[0]) return 0f;
        for (int i = 1; i <= sideEnd; i++) if (ty[i] >= y) {
            float f = (y - ty[i - 1]) / Math.max(1e-4f, ty[i] - ty[i - 1]);
            return i - 1 + f;
        }
        return sideEnd;
    }

    static float smooth(float k) { k = Mth.clamp(k, 0f, 1f); return k * k * (3 - 2 * k); }

    // ------------------------------------------------------------------ laying the strand out as a rope

    /** how far the rope keeps off his rim and dome (model blocks): half a strand's width and a little */
    public static final float ROPE_CLEAR = 5f;

    /**
     * One tick of the strand that is carrying somebody, as a rope: pos holds its joints now and prev a tick ago (rest
     * space, x y z each). Its root is held at (rx ry rz) where it hangs from, and its end at (tx ty tz), right on the
     * one it carries; in between it swings with its weight and the water's drag. Each piece keeps its own length
     * where it can: with length to spare the rope hangs in a curve; pulled tighter it lies over the rim and dome as
     * close as it can and only stretches as much as it has to. Its stiffness shares a bend out along it rather than
     * letting one joint take it all, and nothing of it goes into his rim or dome, or under the ground (floorY).
     */
    public static void step(float[] pos, float[] prev, float[] restLen, float rx, float ry, float rz, float tx, float ty, float tz, float floorY) {
        build(null);
        int n = restLen.length + 1, e = 3 * (n - 1);
        float[] g = new float[2];
        // how much rope the way needs: the rope as it is, pulled tight between its two ends round his rim and dome
        float[] tight = pos.clone();
        tight[0] = rx; tight[1] = ry; tight[2] = rz;
        tight[e] = tx; tight[e + 1] = ty; tight[e + 2] = tz;
        for (int it = 0; it < 16; it++) {
            for (int i = 1; i < n - 1; i++) {
                int o = 3 * i;
                for (int k = 0; k < 3; k++) tight[o + k] = 0.5f * (tight[o - 3 + k] + tight[o + 3 + k]);
            }
            keepOff(tight, n, g);
        }
        float need = 0f;
        for (int i = 0; i < n - 1; i++) need += d3(tight, i, tight, i + 1);
        // it pays out from its root only as much as the way needs (and a little over, so it hangs in a gentle curve):
        // the pieces nearest the end come out first, the ones not needed stay drawn in at the root as a short stub
        float[] tl = new float[restLen.length];
        float want = need * PAY_SLACK, after = 0f;
        for (int i = restLen.length - 1; i >= 0; i--) {
            tl[i] = Mth.clamp(want - after, restLen[i] * STUB, restLen[i]);
            after += restLen[i];
        }
        float bendCos = (float) Math.cos(Math.toRadians(MAX_BEND) * 0.5);
        // moving on: what speed it had (the water takes some), and its weight
        for (int i = 1; i < n - 1; i++) {
            int o = 3 * i;
            float vx = (pos[o] - prev[o]) * DRAG, vy = (pos[o + 1] - prev[o + 1]) * DRAG - WEIGHT, vz = (pos[o + 2] - prev[o + 2]) * DRAG;
            float v = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (v > SPEED) { vx *= SPEED / v; vy *= SPEED / v; vz *= SPEED / v; }
            prev[o] = pos[o]; prev[o + 1] = pos[o + 1]; prev[o + 2] = pos[o + 2];
            pos[o] += vx; pos[o + 1] += vy; pos[o + 2] += vz;
        }
        // how fast its end is going this tick (the joints may go a few times that, and no more)
        float endMove = (float) Math.sqrt((tx - pos[e]) * (tx - pos[e]) + (ty - pos[e + 1]) * (ty - pos[e + 1]) + (tz - pos[e + 2]) * (tz - pos[e + 2]));
        float cap = Math.max(JOINT_MOVE, END_X * endMove);
        prev[0] = pos[0]; prev[1] = pos[1]; prev[2] = pos[2];
        prev[e] = pos[e]; prev[e + 1] = pos[e + 1]; prev[e + 2] = pos[e + 2];
        pos[0] = rx; pos[1] = ry; pos[2] = rz;
        pos[e] = tx; pos[e + 1] = ty; pos[e + 2] = tz;
        for (int it = 0; it < ITER; it++) {
            // stiffness: each joint drawn a little toward the line between its neighbours
            for (int i = 1; i < n - 1; i++) {
                int o = 3 * i;
                for (int k = 0; k < 3; k++) pos[o + k] += STIFF * (0.5f * (pos[o - 3 + k] + pos[o + 3 + k]) - pos[o + k]);
            }
            // each piece its own length, both ways along, the two ends held
            for (int pass = 0; pass < 2; pass++) for (int j = 0; j < n - 1; j++) {
                int i = pass == 0 ? j : n - 2 - j;
                int a = 3 * i, b = a + 3;
                float dx = pos[b] - pos[a], dy = pos[b + 1] - pos[a + 1], dz = pos[b + 2] - pos[a + 2];
                float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dl < 1e-5f) continue;
                float diff = (dl - tl[i]) / dl;
                boolean fa = i == 0, fb = i + 1 == n - 1;
                float wa = fa ? 0f : fb ? 1f : 0.5f, wb = fb ? 0f : fa ? 1f : 0.5f;
                pos[a] += dx * diff * wa; pos[a + 1] += dy * diff * wa; pos[a + 2] += dz * diff * wa;
                pos[b] -= dx * diff * wb; pos[b + 1] -= dy * diff * wb; pos[b + 2] -= dz * diff * wb;
            }
            // it bends, but never sharply at any one joint: two joints apart are kept far enough apart
            for (int i = 1; i < n - 1; i++) {
                int a = 3 * (i - 1), b = 3 * (i + 1);
                float dx = pos[b] - pos[a], dy = pos[b + 1] - pos[a + 1], dz = pos[b + 2] - pos[a + 2];
                float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                // (a piece still drawn in at the root doesn't count)
                if (tl[i - 1] < restLen[i - 1] * 0.5f) continue;
                float min = (tl[i - 1] + tl[i]) * bendCos;
                if (dl >= min || dl < 1e-5f) continue;
                float diff = (dl - min) / dl;
                boolean fa = i - 1 == 0, fb = i + 1 == n - 1;
                if (fa && fb) continue;
                float wa = fa ? 0f : fb ? 1f : 0.5f, wb = fb ? 0f : fa ? 1f : 0.5f;
                pos[a] += dx * diff * wa; pos[a + 1] += dy * diff * wa; pos[a + 2] += dz * diff * wa;
                pos[b] -= dx * diff * wb; pos[b + 1] -= dy * diff * wb; pos[b + 2] -= dz * diff * wb;
                // and the joint between them out to the side it bends to, so they don't just slide
            }
            // never into his rim or dome: the joints, and each piece along its length, kept off them
            keepOff(pos, n, g);
            for (int i = 1; i < n - 1; i++) if (pos[3 * i + 1] < floorY) pos[3 * i + 1] = floorY;
            pos[0] = rx; pos[1] = ry; pos[2] = rz;
            pos[e] = tx; pos[e + 1] = ty; pos[e + 2] = tz;
        }
        // however it's pulled about, no joint goes much faster than its end (the rope catches up over the next few ticks)
        for (int i = 1; i < n - 1; i++) {
            int o = 3 * i;
            float dx = pos[o] - prev[o], dy = pos[o + 1] - prev[o + 1], dz = pos[o + 2] - prev[o + 2];
            float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > cap) {
                float f = cap / d;
                pos[o] = prev[o] + dx * f; pos[o + 1] = prev[o + 1] + dy * f; pos[o + 2] = prev[o + 2] + dz * f;
            }
        }
    }

    public static float DRAG = 0.8f, WEIGHT = 0.3f, SPEED = 4f, STIFF = 0.25f, PAY_SLACK = 1.05f, STUB = 0.12f, MAX_BEND = 45f, OUT_PAST = 4f, JOINT_MOVE = 3f, END_X = 2f;
    public static int ITER = 30;

    /**
     * Where the strand comes out from under the rim right now (rest space). While the one it carries is down his side
     * it hangs from its own root; as they go up past the rim, its top pays out along the underside of the rim to the
     * rim's edge (and back in again on the way down), so it comes out over the edge instead of bending hard round it,
     * and needs less stretch to get over the dome.
     */
    public static Vector3f outFrom(Vector3f root, float endY, int rimY, Vector3f out) {
        build(null);
        float k = smooth((endY - (rimY - 25f)) / 30f);
        float r = (float) Math.sqrt(root.x * root.x + root.z * root.z);
        float to = Math.max(r, rimEdge + OUT_PAST);
        float f = r > 1e-3f ? (r + (to - r) * k) / r : 1f;
        return out.set(root.x * f, root.y, root.z * f);
    }

    /** a rope's joints (not its two ends), and each piece along its length, kept ROPE_CLEAR off his rim and dome */
    private static void keepOff(float[] pos, int n, float[] g) {
        for (int i = 1; i < n - 1; i++) pushOut(pos, 3 * i, 1f, g);
        for (int i = 1; i < n - 1; i++) {
            int a = 3 * i, b = a + 3;
            boolean fb = i + 1 == n - 1;
            for (int q = 1; q <= 3; q++) {
                float f = q / 4f;
                float mx = pos[a] + (pos[b] - pos[a]) * f, my = pos[a + 1] + (pos[b + 1] - pos[a + 1]) * f, mz = pos[a + 2] + (pos[b + 2] - pos[a + 2]) * f;
                float mr = (float) Math.sqrt(mx * mx + mz * mz);
                float dd = shellDist(mr, my);
                if (dd >= ROPE_CLEAR) continue;
                shellGrad(mr, my, g);
                float push = ROPE_CLEAR - dd;
                moveR(pos, a, g, push * (fb ? 1f : 1f - f));
                if (!fb) moveR(pos, b, g, push * f);
            }
        }
    }

    /** a joint kept ROPE_CLEAR off his rim and dome */
    private static void pushOut(float[] p, int o, float k, float[] g) {
        float r = (float) Math.sqrt(p[o] * p[o] + p[o + 2] * p[o + 2]);
        float dd = shellDist(r, p[o + 1]);
        if (dd >= ROPE_CLEAR) return;
        shellGrad(r, p[o + 1], g);
        moveR(p, o, g, (ROPE_CLEAR - dd) * k);
    }

    /** moves a point by amt along (g[0] outward from his middle, g[1] up) */
    private static void moveR(float[] p, int o, float[] g, float amt) {
        float r = (float) Math.sqrt(p[o] * p[o] + p[o + 2] * p[o + 2]);
        float nr = Math.max(0f, r + g[0] * amt);
        if (r > 1e-4f) { p[o] *= nr / r; p[o + 2] *= nr / r; }
        p[o + 1] += g[1] * amt;
    }

    private static float d3(float[] a, int i, float[] b, int j) {
        float dx = b[3 * j] - a[3 * i], dy = b[3 * j + 1] - a[3 * i + 1], dz = b[3 * j + 2] - a[3 * i + 2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ---- how far a point (r out, y up) is from his rim, dome and crown: below 0 inside them

    private static final int SR = 150, SY0 = -40, SY1 = 260;
    private static float[] sdf;

    public static float shellDist(float r, float y) {
        float fr = Mth.clamp(r, 0f, SR - 1.001f), fy = Mth.clamp(y - SY0, 0f, SY1 - SY0 - 1.001f);
        int i = (int) fr, j = (int) fy;
        float a = fr - i, b = fy - j;
        int w = SY1 - SY0 + 1;
        float v00 = sdf[i * w + j], v01 = sdf[i * w + j + 1], v10 = sdf[(i + 1) * w + j], v11 = sdf[(i + 1) * w + j + 1];
        float v = (v00 * (1 - a) + v10 * a) * (1 - b) + (v01 * (1 - a) + v11 * a) * b;
        // past the edge of the table: add on how far past
        return v + Math.max(0f, r - (SR - 1)) + Math.max(0f, Math.max(SY0 - y, y - SY1));
    }

    private static void shellGrad(float r, float y, float[] g) {
        float gx = shellDist(r + 0.5f, y) - shellDist(Math.max(0f, r - 0.5f), y);
        float gy = shellDist(r, y + 0.5f) - shellDist(r, y - 0.5f);
        float l = (float) Math.sqrt(gx * gx + gy * gy);
        if (l < 1e-5f) { g[0] = 0f; g[1] = 1f; return; }
        g[0] = gx / l; g[1] = gy / l;
    }

    private static boolean inShellAt(float r, float y) {
        int row = (int) Math.floor(y) - Y0;
        return row >= 0 && row < shell.length && shell[row] > 0f && r < shell[row];
    }

    private static void buildSdf() {
        int w = SY1 - SY0 + 1;
        float[] f = new float[SR * w];
        List<int[]> edge = new ArrayList<>();
        for (int i = 0; i < SR; i++) for (int j = 0; j < w; j++) {
            float r = i, y = j + SY0;
            boolean in = inShellAt(r, y);
            // a point of the edge: in, with a neighbour out (or out, with a neighbour in)
            boolean edgeP = false;
            for (int k = 0; k < 4 && !edgeP; k++) {
                float nr = r + (k == 0 ? 1 : k == 1 ? -1 : 0), ny = y + (k == 2 ? 1 : k == 3 ? -1 : 0);
                if (nr >= 0 && inShellAt(nr, ny) != in) edgeP = true;
            }
            if (edgeP && in) edge.add(new int[]{i, j});
        }
        for (int i = 0; i < SR; i++) for (int j = 0; j < w; j++) {
            float best = Float.MAX_VALUE;
            for (int[] p : edge) {
                float dx = p[0] - i, dy = p[1] - j;
                float d2 = dx * dx + dy * dy;
                if (d2 < best) best = d2;
            }
            float d = (float) Math.sqrt(best);
            f[i * w + j] = inShellAt(i, j + SY0) ? -d : d;
        }
        sdf = f;
    }

    // ------------------------------------------------------------------ his outline

    private static int firstRow() {
        for (int i = 0; i < all.length; i++) if (all[i] > 0f) return i + Y0;
        return 0;
    }

    /** the edge of everything within d of him, at height y: how far out it is (-1 if nothing is that close) */
    private static float offset(float y, float d) {
        float best = -1f;
        int a = (int) Math.floor(y - d) - Y0 - 1, b = (int) Math.ceil(y + d) - Y0 + 1;
        for (int i = Math.max(0, a); i <= Math.min(all.length - 1, b); i++) {
            if (all[i] <= 0f) continue;
            float row = i + Y0;
            float dy = y < row ? row - y : y > row + 1 ? y - row - 1 : 0f;
            if (dy > d) continue;
            best = Math.max(best, all[i] + (float) Math.sqrt(d * d - dy * dy));
        }
        return best;
    }

    /** a straight rope from the root (ra, ya) to (r, y): does it stay clear of the rim and dome (by a strand's width) */
    private static boolean clearOfShell(float ra, float ya, float r, float y) {
        float len = (float) Math.hypot(r - ra, y - ya);
        int n = Math.max(2, (int) (len * 2));
        for (int i = 0; i <= n; i++) {
            float f = i / (float) n;
            float pr = ra + (r - ra) * f, py = ya + (y - ya) * f;
            // right where it hangs from, it is part of the rim: only look further out
            if (pr < ra + 4f) continue;
            if (inShell(pr, py, 3f)) return false;
        }
        return true;
    }

    private static boolean inShell(float r, float y, float m) {
        int a = (int) Math.floor(y - m) - Y0, b = (int) Math.floor(y + m) - Y0;
        for (int i = Math.max(0, a); i <= Math.min(shell.length - 1, b); i++) if (shell[i] > 0f && r < shell[i] + m) return true;
        return false;
    }

    private static synchronized void build(BellRig rig) {
        if (all != null) return;
        if (rig == null) rig = BellRig.get();
        BellModel m = BellModel.get();
        int rows = rig.crownY + 8 - Y0;
        float[] a = new float[rows], sh = new float[rows];
        int hi = 0;
        for (int b = 0; b < m.boneCount(); b++) {
            BellRig.Kind k = rig.kind[b];
            boolean body = k == BellRig.Kind.BELL || k == BellRig.Kind.RIM || k == BellRig.Kind.CROWN || k == BellRig.Kind.SPOT;
            short[] xs = m.x[b], ys = m.y[b], zs = m.z[b];
            for (int i = 0; i < xs.length; i++) {
                int row = ys[i] - Y0;
                if (row < 0 || row >= rows) continue;
                float cx = Math.abs(xs[i] + 0.5f) + 0.5f, cz = Math.abs(zs[i] + 0.5f) + 0.5f;
                float r = (float) Math.sqrt(cx * cx + cz * cz);
                // the bell swells a little when he's worn out or sinking: leave room for it
                if (k == BellRig.Kind.RIM) rimEdge = Math.max(rimEdge, r);
                if (body) { r = r * 1.07f + 1f; sh[row] = Math.max(sh[row], r); }
                a[row] = Math.max(a[row], r);
                hi = Math.max(hi, ys[i] + 1);
            }
        }
        top = hi;
        shell = sh;
        // the strands that can do the carrying
        List<Integer> c = new ArrayList<>();
        boolean[] hasBranch = new boolean[rig.strands.length];
        for (BellRig.Chain ch : rig.chains) if (!ch.arm && ch.parentChain >= 0 && !rig.chains[ch.parentChain].arm) hasBranch[rig.chains[ch.parentChain].index] = true;
        for (BellRig.StrandDef S : rig.strands) {
            int pb = rig.parent[S.bones()[0]];
            if (pb < 0 || rig.kind[pb] != BellRig.Kind.RIM || hasBranch[S.k()]) continue;
            BellRig.Chain ch = rig.chains[rig.strandChain[S.k()]];
            float len = 0f;
            for (float l : ch.restLen) len += l;
            if (len < 110f) continue;
            Vector3f j0 = S.joints()[0];
            double ang = Math.atan2(j0.z, j0.x);
            double gap = Math.PI;
            for (BellRig.ArmDef A : rig.arms) {
                double dd = Math.abs(Mth.wrapDegrees(Math.toDegrees(ang - A.angle())));
                gap = Math.min(gap, Math.toRadians(dd));
            }
            if (gap < 0.28) continue;
            c.add(S.k());
        }
        candidates = c.stream().mapToInt(Integer::intValue).toArray();
        buildSdf();
        all = a;
    }
}
