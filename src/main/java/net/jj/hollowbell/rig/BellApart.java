package net.jj.hollowbell.rig;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Arrays;

/**
 * Keeps his arms, strands and pods from passing through each other (1.10). After his chains have swung for the tick
 * (see BellAnim), each piece of an arm or strand is taken as a rounded rod as thick as its blocks, each pod as a ball,
 * and the vase inside the dome as a fixed rod. Where two of them are deep in each other they are eased apart sideways,
 * half the way a tick: the strands give way most, an arm less, and nothing a move is swinging (a slam landing where it's
 * aimed, the strand carrying somebody up) is moved at all. Moved with its speed kept (both the joint and where it was a
 * tick ago), so nothing is flung, and only sideways, so nothing is pushed into the ground or up into his bell.
 * Cheap: the rods are put in a grid across x z and only those whose boxes meet are looked at.
 */
final class BellApart {
    private final BellRig rig;
    /** per chain, per piece: how thick it is (model blocks, across from its line) */
    private final float[][] rad;
    /** per pod: its middle as built, how big it is, the bone it hangs from, how far along it (0-1), its chain and piece */
    private final float[] podR;
    private final int[] podChain, podSeg, podBone;
    private final float[] podU;
    /** the rods: chain, piece (pod: -1 - pod index; the vase: -1000) and their boxes, reused */
    private int n;
    private int[] rc, rs;
    private float[] bx0, bx1, by0, by1, bz0, bz1;
    private final float[][] podAt;
    /** the pods, then the egg clumps */
    private final BellRig.BlobDef[] blobs;
    private final Matrix4f m = new Matrix4f(), inv = new Matrix4f();
    private final Quaternionf q = new Quaternionf();
    private final Vector3f v = new Vector3f(), w = new Vector3f();
    /** the most this tick moved anything (for the lab) */
    float moved;

    BellApart(BellRig rig) {
        this.rig = rig;
        BellModel model = BellModel.get();
        rad = new float[rig.chains.length][];
        for (BellRig.Chain ch : rig.chains) {
            rad[ch.id] = new float[ch.bones.length];
            for (int i = 0; i < ch.bones.length; i++) {
                int b = ch.bones[i];
                Vector3f a = ch.joints[i], c = ch.joints[i + 1];
                float dx = c.x - a.x, dy = c.y - a.y, dz = c.z - a.z, l2 = Math.max(1e-4f, dx * dx + dy * dy + dz * dz);
                int cnt = model.count(b);
                float[] d = new float[Math.max(1, cnt)];
                for (int k = 0; k < cnt; k++) {
                    float px = model.x[b][k] + 0.5f - a.x, py = model.y[b][k] + 0.5f - a.y, pz = model.z[b][k] + 0.5f - a.z;
                    float t = Math.max(0f, Math.min(1f, (px * dx + py * dy + pz * dz) / l2));
                    float ex = px - dx * t, ey = py - dy * t, ez = pz - dz * t;
                    d[k] = (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
                }
                Arrays.sort(d);
                rad[ch.id][i] = Math.max(0.8f, d[(int) (d.length * 0.7f)]);
            }
        }
        int np = rig.pods.length + rig.eggs.length;
        podR = new float[np]; podChain = new int[np]; podSeg = new int[np]; podBone = new int[np]; podU = new float[np];
        podAt = new float[np][3];
        blobs = new BellRig.BlobDef[np];
        for (int k = 0; k < np; k++) blobs[k] = k < rig.pods.length ? rig.pods[k] : rig.eggs[k - rig.pods.length];
        for (int k = 0; k < np; k++) {
            BellRig.BlobDef P = blobs[k];
            int b = P.bone();
            float[] d = new float[Math.max(1, model.count(b))];
            for (int j = 0; j < model.count(b); j++) {
                float px = model.x[b][j] + 0.5f - P.centre().x, py = model.y[b][j] + 0.5f - P.centre().y, pz = model.z[b][j] + 0.5f - P.centre().z;
                d[j] = (float) Math.sqrt(px * px + py * py + pz * pz);
            }
            Arrays.sort(d);
            podR[k] = Math.max(1f, d[(int) (d.length * 0.6f)]);
            int pb = rig.parent[b];
            podBone[k] = pb;
            podChain[k] = pb >= 0 ? rig.chainOf[pb] : -1;
            podSeg[k] = pb >= 0 ? rig.seg[pb] : -1;
            podU[k] = Float.isNaN(rig.blobU[b]) ? 0.5f : rig.blobU[b];
        }
        int cap = 8;
        for (BellRig.Chain ch : rig.chains) cap += ch.bones.length;
        cap += np + 1;
        rc = new int[cap]; rs = new int[cap];
        bx0 = new float[cap]; bx1 = new float[cap]; by0 = new float[cap]; by1 = new float[cap]; bz0 = new float[cap]; bz1 = new float[cap];
    }

    /** how freely each chain gives way this tick (0: not at all) */
    private float[] give;
    /** per chain, per joint: the push asked for this tick, and the one given last tick (eased from one to the next, so
     *  nothing lurches) */
    private float[][] want, given;

    /**
     * One pass over the chains as they are after this tick's swing: things deep in each other eased apart. p: the
     * chains' joints now, q: a tick ago (moved together, so their speed is kept). body: his body's frame (for the vase).
     */
    void apply(BellState st, float[][] p, float[][] qq, float[] held, Matrix4f body) {
        int nc = rig.chains.length;
        if (give == null) {
            give = new float[nc];
            want = new float[nc][]; given = new float[nc][];
            for (int c = 0; c < nc; c++) { want[c] = new float[p[c].length]; given[c] = new float[p[c].length]; }
        }
        // (what's in what is looked for every other tick; between, the last ask holds)
        boolean look = (++ticks & 1) == 0 || ticks < 3;
        if (look) for (int c = 0; c < nc; c++) Arrays.fill(want[c], 0f);
        for (int c = 0; c < nc; c++) {
            BellRig.Chain ch = rig.chains[c];
            float g = (ch.arm ? 0.25f : 1f) * (1f - Math.min(1f, held[c] * 1.4f));
            if (!ch.arm && (ch.index == st.carryStrand || ch.index == st.grabStrand)) g = 0f;
            give[c] = Math.max(0f, g);
        }
        moved = 0f;
        if (!look) { give(p, qq); return; }
        // the rods: every piece of every chain
        n = 0;
        for (int c = 0; c < nc; c++) {
            float[] pt = p[c];
            for (int i = 0; i < rad[c].length; i++) {
                float r = rad[c][i];
                int a = 3 * i, b = 3 * i + 3;
                box(c, i, Math.min(pt[a], pt[b]) - r, Math.max(pt[a], pt[b]) + r, Math.min(pt[a + 1], pt[b + 1]) - r, Math.max(pt[a + 1], pt[b + 1]) + r,
                        Math.min(pt[a + 2], pt[b + 2]) - r, Math.max(pt[a + 2], pt[b + 2]) + r);
            }
        }
        // the pods (where they hang now: on their piece, as drawn there)
        Quaternionf brot = rig.bodyRotation(st, q);
        for (int k = 0; k < blobs.length; k++) {
            if (gone(st, k) || podChain[k] < 0) { podAt[k][0] = Float.NaN; continue; }
            rig.frame(st, podBone[k], podU[k], null, brot, m);
            m.transformPosition(blobs[k].centre(), v);
            float r = blobR(st, k);
            podAt[k][0] = v.x; podAt[k][1] = v.y; podAt[k][2] = v.z;
            box(-1, -1 - k, v.x - r, v.x + r, v.y - r, v.y + r, v.z - r, v.z + r);
        }
        // the vase hanging in the dome: never moved
        body.transformPosition(v.set(0, 124, 0));
        body.transformPosition(w.set(0, 180, 0));
        float vr = VASE_R;
        box(-1, -1000, Math.min(v.x, w.x) - vr, Math.max(v.x, w.x) + vr, Math.min(v.y, w.y) - vr, Math.max(v.y, w.y) + vr, Math.min(v.z, w.z) - vr, Math.max(v.z, w.z) + vr);
        float vx0 = v.x, vy0 = v.y, vz0 = v.z, vx1 = w.x, vy1 = w.y, vz1 = w.z;

        // his arms never through his dome (turned right over in the dive, they trail behind into it): each joint of
        // an arm over the rim, inside the dome as it is shaped now, is eased out to the outside of it
        body.invert(inv);
        for (int c = 0; c < nc; c++) {
            BellRig.Chain ch = rig.chains[c];
            if (!ch.arm || give[c] <= 0f) continue;
            float[] pt = p[c];
            for (int i = 1; i < ch.points(); i++) {
                int o = 3 * i;
                inv.transformPosition(v.set(pt[o], pt[o + 1], pt[o + 2]));
                float r = rad[c][Math.min(i - 1, rad[c].length - 1)];
                if (v.y < rig.rimY - r || v.y > rig.crownY + r) continue;
                float out = rig.domeOuter(Math.min(v.y, rig.crownY - 1)) * rig.squeezeAt(st, v.y) + r * LEAVE;
                float h = (float) Math.sqrt(v.x * v.x + v.z * v.z);
                if (h >= out) continue;
                float k = h < 1e-3f ? 0f : (out - h) * RATE / h;
                if (h < 1e-3f) { v.x += out; } else { v.x += v.x * k; v.z += v.z * k; }
                body.transformPosition(w.set(v));
                float[] wa = want[c];
                wa[o] += w.x - pt[o]; wa[o + 1] += w.y - pt[o + 1]; wa[o + 2] += w.z - pt[o + 2];
            }
        }

        // (only those whose boxes meet: each box put in the squares of a grid across x z it reaches over, and a pair looked
        // at only in the square where both their boxes start)
        for (int g = 0; g < G * G; g++) cellN[g] = 0;
        for (int i = 0; i < n; i++) {
            int x0 = cell(bx0[i]), x1 = cell(bx1[i]), z0 = cell(bz0[i]), z1 = cell(bz1[i]);
            for (int gx = x0; gx <= x1; gx++) for (int gz = z0; gz <= z1; gz++) {
                int g = gx * G + gz;
                if (cellN[g] == cells[g].length) cells[g] = Arrays.copyOf(cells[g], cells[g].length * 2);
                cells[g][cellN[g]++] = i;
            }
        }
        for (int g = 0; g < G * G; g++) {
            int[] l = cells[g];
            int m = cellN[g], gx = g / G, gz = g % G;
            for (int ii = 0; ii < m; ii++) {
                int a = l[ii];
                for (int jj = ii + 1; jj < m; jj++) {
                    int b = l[jj];
                    if (by0[b] > by1[a] || by1[b] < by0[a] || bx0[b] > bx1[a] || bx1[b] < bx0[a] || bz0[b] > bz1[a] || bz1[b] < bz0[a]) continue;
                    if (cell(Math.max(bx0[a], bx0[b])) != gx || cell(Math.max(bz0[a], bz0[b])) != gz) continue;
                    pair(st, p, qq, a, b, vx0, vy0, vz0, vx1, vy1, vz1);
                }
            }
        }
        give(p, qq);
    }

    private long ticks;

    /**
     * What was asked, eased from last tick's push (each joint's push changes by at most EASE a tick), and given to
     * joint and its place a tick ago alike.
     */
    private void give(float[][] p, float[][] qq) {
        int nc = rig.chains.length;
        for (int c = 0; c < nc; c++) {
            float[] wa = want[c], gv = given[c], pt = p[c], pq = qq[c];
            for (int o = 3; o < wa.length; o += 3) {
                // (never more than MAX_STEP a tick in all)
                float wl = (float) Math.sqrt(wa[o] * wa[o] + wa[o + 1] * wa[o + 1] + wa[o + 2] * wa[o + 2]);
                if (wl > MAX_STEP) { float f = MAX_STEP / wl; wa[o] *= f; wa[o + 1] *= f; wa[o + 2] *= f; }
                float dx = wa[o] - gv[o], dy = wa[o + 1] - gv[o + 1], dz = wa[o + 2] - gv[o + 2];
                float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (l > EASE) { float f = EASE / l; dx *= f; dy *= f; dz *= f; }
                gv[o] += dx; gv[o + 1] += dy; gv[o + 2] += dz;
                if (gv[o] == 0f && gv[o + 1] == 0f && gv[o + 2] == 0f) continue;
                pt[o] += gv[o]; pt[o + 1] += gv[o + 1]; pt[o + 2] += gv[o + 2];
                pq[o] += gv[o]; pq[o + 1] += gv[o + 1]; pq[o + 2] += gv[o + 2];
                moved = Math.max(moved, (float) Math.sqrt(gv[o] * gv[o] + gv[o + 1] * gv[o + 1] + gv[o + 2] * gv[o + 2]));
            }
        }
    }

    /** how much a joint's push may change from one tick to the next (model blocks a tick) */
    static final float EASE = Float.parseFloat(System.getProperty("hollowbell.apartEase", "0.5"));

    static final float VASE_R = 19f;
    /** the grid the boxes are sorted into: G squares of CELL model blocks a side, about his middle */
    static final int G = 24;
    static final float CELL = 20f;
    private final int[][] cells = new int[G * G][];
    private final int[] cellN = new int[G * G];
    { for (int g = 0; g < G * G; g++) cells[g] = new int[16]; }

    private static int cell(float v) { int c = (int) Math.floor(v / CELL) + G / 2; return c < 0 ? 0 : c >= G ? G - 1 : c; }

    private boolean gone(BellState st, int k) {
        int np = rig.pods.length;
        return k < np ? st.podPopped[k] : st.eggGone[k - np];
    }

    /** how big a pod or egg clump is now (a pod growing back is smaller) */
    private float blobR(BellState st, int k) {
        return k < rig.pods.length ? podR[k] * (0.3f + 0.7f * st.podGrowth[k]) : podR[k];
    }
    /** how far into each other two things may go before they're eased apart (of the two thicknesses) */
    static final float LEAVE = 0.8f;
    static final float RATE = Float.parseFloat(System.getProperty("hollowbell.apartRate", "1.0"));
    /** the most one pair is eased apart in a tick (model blocks), so nothing lurches */
    static final float MAX_STEP = Float.parseFloat(System.getProperty("hollowbell.apartStep", "1.2"));

    private void box(int c, int s, float x0, float x1, float y0, float y1, float z0, float z1) {
        rc[n] = c; rs[n] = s; bx0[n] = x0; bx1[n] = x1; by0[n] = y0; by1[n] = y1; bz0[n] = z0; bz1[n] = z1; n++;
    }

    /** a and b: a rod (chain, piece), a pod (-1 - pod), or the vase */
    private void pair(BellState st, float[][] p, float[][] qq, int a, int b, float vx0, float vy0, float vz0, float vx1, float vy1, float vz1) {
        int ca = rc[a], sa = rs[a], cb = rc[b], sb = rs[b];
        boolean podA = ca < 0 && sa > -1000, podB = cb < 0 && sb > -1000, vaseA = sa == -1000, vaseB = sb == -1000;
        if ((vaseA || podA) && (vaseB || podB) && (vaseA || vaseB)) {
            // a pod against the vase
            int k = vaseA ? -1 - sb : -1 - sa;
            podVsRod(st, p, qq, k, -1, -1, vx0, vy0, vz0, vx1, vy1, vz1, VASE_R);
            return;
        }
        if (podA && podB) { podVsPod(st, p, qq, -1 - sa, -1 - sb); return; }
        if (podA || podB) {
            int k = podA ? -1 - sa : -1 - sb, c = podA ? cb : ca, s = podA ? sb : sa;
            if (vaseA || vaseB) return;
            // (not the piece it hangs on, nor one hanging from it)
            if (c == podChain[k] && Math.abs(s - podSeg[k]) <= 1) return;
            BellRig.Chain ch = rig.chains[c];
            if (ch.parentBone == blobs[k].bone() && s <= 1) return;
            float[] pt = p[c];
            podVsRod(st, p, qq, k, c, s, pt[3 * s], pt[3 * s + 1], pt[3 * s + 2], pt[3 * s + 3], pt[3 * s + 4], pt[3 * s + 5], rad[c][s]);
            return;
        }
        if (vaseA || vaseB) {
            int c = vaseA ? cb : ca, s = vaseA ? sb : sa;
            if (rig.chains[c].parentBone >= 0 && rig.kind[rig.chains[c].parentBone] == BellRig.Kind.SPOT && s <= 1) return;
            float[] pt = p[c];
            rods(p, qq, c, s, pt[3 * s], pt[3 * s + 1], pt[3 * s + 2], pt[3 * s + 3], pt[3 * s + 4], pt[3 * s + 5], rad[c][s], -1, -1, vx0, vy0, vz0, vx1, vy1, vz1, VASE_R);
            return;
        }
        if (ca == cb) return;
        if (related(ca, sa, cb, sb) || related(cb, sb, ca, sa)) return;
        float[] pa = p[ca], pb = p[cb];
        rods(p, qq, ca, sa, pa[3 * sa], pa[3 * sa + 1], pa[3 * sa + 2], pa[3 * sa + 3], pa[3 * sa + 4], pa[3 * sa + 5], rad[ca][sa],
                cb, sb, pb[3 * sb], pb[3 * sb + 1], pb[3 * sb + 2], pb[3 * sb + 3], pb[3 * sb + 4], pb[3 * sb + 5], rad[cb][sb]);
    }

    /** chain c (piece s) hangs from chain o right there (its first pieces, by the piece of o it grows from) */
    private boolean related(int c, int s, int o, int so) {
        BellRig.Chain ch = rig.chains[c];
        if (ch.parentChain == o && s <= 1 && Math.abs(so - ch.parentSeg) <= 1) return true;
        // (two chains growing from the same pod or from the same piece, near their roots)
        BellRig.Chain oc = rig.chains[o];
        return s == 0 && so == 0 && ch.parentBone >= 0 && ch.parentBone == oc.parentBone;
    }

    private final float[] cp = new float[8];

    /** the nearest points of two pieces: fills cp with s, t, and the two points */
    private void closest(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
        float ux = bx - ax, uy = by - ay, uz = bz - az, vx = dx - cx, vy = dy - cy, vz = dz - cz, wx = ax - cx, wy = ay - cy, wz = az - cz;
        float A = ux * ux + uy * uy + uz * uz, B = ux * vx + uy * vy + uz * vz, C = vx * vx + vy * vy + vz * vz, D = ux * wx + uy * wy + uz * wz, E = vx * wx + vy * wy + vz * wz;
        float den = A * C - B * B, s, t;
        if (A < 1e-6f && C < 1e-6f) { s = 0; t = 0; }
        else if (A < 1e-6f) { s = 0; t = clamp(E / C); }
        else if (C < 1e-6f) { t = 0; s = clamp(-D / A); }
        else {
            s = den > 1e-6f ? clamp((B * E - C * D) / den) : 0f;
            t = (B * s + E) / C;
            if (t < 0f) { t = 0f; s = clamp(-D / A); }
            else if (t > 1f) { t = 1f; s = clamp((B - D) / A); }
        }
        cp[0] = s; cp[1] = t;
        cp[2] = ax + ux * s; cp[3] = ay + uy * s; cp[4] = az + uz * s;
        cp[5] = cx + vx * t; cp[6] = cy + vy * t; cp[7] = cz + vz * t;
    }

    private static float clamp(float k) { return k < 0f ? 0f : k > 1f ? 1f : k; }

    /** two rods (chain -1: fixed) eased apart */
    private void rods(float[][] p, float[][] qq, int ca, int sa, float ax, float ay, float az, float bx, float by, float bz, float ra,
                      int cb, int sb, float cx, float cy, float cz, float dx, float dy, float dz, float rb) {
        closest(ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz);
        float s = cp[0], t = cp[1];
        float nx = cp[2] - cp[5], nz = cp[4] - cp[7], ny = cp[3] - cp[6];
        float d = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        float want = (ra + rb) * LEAVE;
        if (d >= want) return;
        float ga = ca >= 0 ? give[ca] : 0f, gb = cb >= 0 ? give[cb] : 0f;
        if (ga + gb < 1e-4f) return;
        float hl = (float) Math.sqrt(nx * nx + nz * nz);
        if (hl < 0.05f) {
            // (one right over the other: apart the way their roots are from each other)
            float[] ra0 = ca >= 0 ? p[ca] : null, rb0 = cb >= 0 ? p[cb] : null;
            nx = (ra0 != null ? ra0[0] : 0f) - (rb0 != null ? rb0[0] : 0f);
            nz = (ra0 != null ? ra0[2] : 0f) - (rb0 != null ? rb0[2] : 0f);
            hl = (float) Math.sqrt(nx * nx + nz * nz);
            if (hl < 0.05f) { nx = 1f; nz = 0f; hl = 1f; }
        }
        nx /= hl; nz /= hl;
        float ov = (want - d) * RATE;
        float ma = ov * ga / (ga + gb), mb = ov * gb / (ga + gb);
        if (ca >= 0) push(p, qq, ca, sa, s, nx * ma, nz * ma);
        if (cb >= 0) push(p, qq, cb, sb, t, -nx * mb, -nz * mb);
    }

    /** a pod against a rod (chain c piece s; c -1: the vase, fixed) */
    private void podVsRod(BellState st, float[][] p, float[][] qq, int k, int c, int s, float ax, float ay, float az, float bx, float by, float bz, float r) {
        float[] pc = podAt[k];
        if (Float.isNaN(pc[0])) return;
        closest(pc[0], pc[1], pc[2], pc[0], pc[1], pc[2], ax, ay, az, bx, by, bz);
        float nx = pc[0] - cp[5], nz = pc[2] - cp[7], ny = pc[1] - cp[6];
        float d = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        float rp = blobR(st, k);
        float want = (rp + r) * LEAVE;
        if (d >= want) return;
        float gp = give[podChain[k]], gr = c >= 0 ? give[c] : 0f;
        if (gp + gr < 1e-4f) return;
        float hl = (float) Math.sqrt(nx * nx + nz * nz);
        if (hl < 0.05f) { nx = pc[0]; nz = pc[2]; hl = (float) Math.sqrt(nx * nx + nz * nz); if (hl < 0.05f) { nx = 1f; nz = 0f; hl = 1f; } }
        nx /= hl; nz /= hl;
        float ov = (want - d) * RATE;
        float mp = ov * gp / (gp + gr), mr = ov * gr / (gp + gr);
        push(p, qq, podChain[k], podSeg[k], podU[k], nx * mp, nz * mp);
        if (c >= 0) push(p, qq, c, s, cp[1], -nx * mr, -nz * mr);
    }

    private void podVsPod(BellState st, float[][] p, float[][] qq, int i, int j) {
        float[] a = podAt[i], b = podAt[j];
        if (Float.isNaN(a[0]) || Float.isNaN(b[0])) return;
        if (podChain[i] == podChain[j] && Math.abs(podSeg[i] - podSeg[j]) <= 0) return;
        float nx = a[0] - b[0], ny = a[1] - b[1], nz = a[2] - b[2];
        float d = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        float want = (blobR(st, i) + blobR(st, j)) * LEAVE;
        if (d >= want) return;
        float ga = give[podChain[i]], gb = give[podChain[j]];
        if (ga + gb < 1e-4f) return;
        float hl = (float) Math.sqrt(nx * nx + nz * nz);
        if (hl < 0.05f) { nx = 1f; nz = 0f; hl = 1f; }
        nx /= hl; nz /= hl;
        float ov = (want - d) * RATE;
        push(p, qq, podChain[i], podSeg[i], podU[i], nx * ov * ga / (ga + gb), nz * ov * ga / (ga + gb));
        push(p, qq, podChain[j], podSeg[j], podU[j], -nx * ov * gb / (ga + gb), -nz * ov * gb / (ga + gb));
    }

    /** moves piece s of chain c sideways by (mx, mz) at u along it: its two joints shared out (never its root) */
    private void push(float[][] p, float[][] qq, int c, int s, float u, float mx, float mz) {
        float a = s == 0 ? 0f : 1f - u, b = s == 0 ? 1f : u;
        // (the whole move on the joint that can move, if the other is the root)
        float sum = a + b;
        if (sum < 1e-4f) return;
        a /= sum; b /= sum;
        float[] wa = want[c];
        if (s > 0) { int o = 3 * s; wa[o] += mx * a; wa[o + 2] += mz * a; }
        int o = 3 * s + 3;
        wa[o] += mx * b; wa[o + 2] += mz * b;
    }
}
