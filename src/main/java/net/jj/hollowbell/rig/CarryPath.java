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
        all = a;
    }
}
