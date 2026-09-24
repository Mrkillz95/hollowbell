package net.jj.mountain.rig;

/**
 * Where each of his feet is in the world. A planted foot stays exactly where it landed while his body moves over
 * it; when its turn in the wave comes (or it gets stretched too far) it lifts, swings along an arc and lands a
 * little ahead of where the body will be, so nothing slides. Tentacles are dragged instead: their ground end
 * trails behind on a spring and the tip follows it like a rope.
 * The server and the client each run one of these from the same walk phase, so they agree to within a block.
 */
public final class Gait {
    /** height of the top of the ground at x, z (or NaN when that spot isn't loaded) */
    public interface Ground { double at(double x, double z); }
    /** a foot has just come down */
    public interface Landing { void land(int leg, double x, double y, double z); }

    private final MountainRig rig;
    private final int n;
    static final boolean DEBUG = System.getProperty("fabric-api.gametest") != null;
    /** the end each leg reaches for (the foot, or where a tentacle meets the ground): now and one tick ago */
    public final double[] x, y, z, ox, oy, oz;
    /** tentacle tips: now and one tick ago */
    public final double[] tx, ty, tz, otx, oty, otz;
    private final double[] sx, sy, sz, ex, ey, ez;       // swing start and landing spot
    private final double[] hx, hz, vx, vz;               // home last tick, and how fast home is moving
    private final double[] dirX, dirZ;                   // tentacles: which way the tip trails
    private final float[] t, lastP;
    private final boolean[] swing;
    /** false while a foot stands where the ground wasn't loaded yet (it gets put down properly once it is) */
    private final boolean[] known;
    /** how far a landing spot was moved to find flatter footing (a foot doesn't come down on the lip of a cliff) */
    private final double[] nx, nz;
    private final int[][] neighbours;
    private boolean ready;
    // one tentacle can be taken over for a whip: which one, how far through (0..1), and where it strikes
    private int whipLeg = -1;
    private float whipT;
    private double whipX, whipZ;

    public void setWhip(int leg, float t, double wx, double wz) { whipLeg = leg; whipT = t; whipX = wx; whipZ = wz; whipLift = 55f; whipOut = 0.45f; sweep = false; }
    /** the leg sweep: a walking leg swung out to one side and dragged across the ground through the spot */
    public void setSweep(int leg, float t, double wx, double wz) { whipLeg = leg; whipT = t; whipX = wx; whipZ = wz; sweep = true; }
    private boolean sweep;
    /** one bit per leg: a broken leg stops stepping and is dragged along */
    private long broken;
    public void setBroken(long mask) { broken = mask; }
    public boolean legBroken(int k) { return k >= 0 && k < 64 && (broken >>> k & 1L) != 0L; }
    /** the stomp: a walking leg lifted high over the spot and stamped down on it */
    public void setStomp(int leg, float t, double wx, double wz) { whipLeg = leg; whipT = t; whipX = wx; whipZ = wz; whipLift = 34f; whipOut = 0.8f; sweep = false; }
    private float whipLift = 55f, whipOut = 0.45f;
    private double lastX, lastZ;
    private float sprawl;

    public Gait(MountainRig rig) {
        this.rig = rig;
        n = rig.legs.length;
        x = new double[n]; y = new double[n]; z = new double[n]; ox = new double[n]; oy = new double[n]; oz = new double[n];
        tx = new double[n]; ty = new double[n]; tz = new double[n]; otx = new double[n]; oty = new double[n]; otz = new double[n];
        sx = new double[n]; sy = new double[n]; sz = new double[n]; ex = new double[n]; ey = new double[n]; ez = new double[n];
        hx = new double[n]; hz = new double[n]; vx = new double[n]; vz = new double[n];
        dirX = new double[n]; dirZ = new double[n];
        t = new float[n]; lastP = new float[n]; swing = new boolean[n]; known = new boolean[n]; nx = new double[n]; nz = new double[n];
        // the walking legs next to each one on the same side, so two neighbours never both lift out of turn
        neighbours = new int[n][];
        for (MountainRig.LegDef L : rig.legs) {
            int before = -1, after = -1; float ub = -9f, ua = 9f;
            for (MountainRig.LegDef M : rig.legs) {
                if (M == L || M.kind == 2 || M.side != L.side) continue;
                if (M.u < L.u && M.u > ub) { ub = M.u; before = M.k; }
                if (M.u > L.u && M.u < ua) { ua = M.u; after = M.k; }
            }
            neighbours[L.k] = new int[]{before, after};
        }
    }

    public boolean ready() { return ready; }
    public boolean swinging(int k) { return swing[k]; }
    public void reset() { ready = false; }

    private static float phaseOffset(MountainRig.LegDef L) { return L.u * 2.3f + (L.side > 0 ? 0f : 0.5f); }

    /**
     * One tick. ex/ey/ez and yaw are where the entity is now, s its size, phase the walk clock and dPhase how far
     * the clock moved this tick. While dying no new steps start.
     */
    public void tick(double px, double py, double pz, float yawDeg, float s, float phase, float dPhase, float time,
                     boolean dying, Ground g, Landing land) {
        tick(px, py, pz, yawDeg, s, phase, dPhase, time, dying, 0f, g, land);
    }

    /** as above; sprawl (0..1) moves every foot's resting spot out from his body as he lies down to sleep */
    public void tick(double px, double py, double pz, float yawDeg, float s, float phase, float dPhase, float time,
                     boolean dying, float sprawl, Ground g, Landing land) {
        this.sprawl = sprawl;
        s = Math.max(0.02f, s);
        double th = Math.toRadians(180f - yawDeg), c = Math.cos(th), sn = Math.sin(th);
        double jump = Math.hypot(px - lastX, pz - lastZ);
        if (!ready || jump > 12 * s + 6) {
            for (MountainRig.LegDef L : rig.legs) {
                int k = L.k;
                Home h = home(L, L.endAt(sprawl), px, pz, c, sn, s);
                double fh = L.kind == 2 ? g.at(h.x, h.z) : footing(g, h.x, h.z, s, L.pad);
                known[k] = !Double.isNaN(fh);
                x[k] = h.x; z[k] = h.z; y[k] = (known[k] ? fh : py) + L.endY * s;
                hx[k] = h.x; hz[k] = h.z; vx[k] = 0; vz[k] = 0; swing[k] = false; t[k] = 0;
                lastP[k] = frac(phase + phaseOffset(L));
                if (L.kind == 2) {
                    Home tp = home(L, L.tipAt(sprawl), px, pz, c, sn, s);
                    tx[k] = tp.x; tz[k] = tp.z; ty[k] = ground(g, tp.x, tp.z, py) + L.joints[3].y * s;
                    double dx = tp.x - h.x, dz = tp.z - h.z, l = Math.max(1e-6, Math.hypot(dx, dz));
                    dirX[k] = dx / l; dirZ[k] = dz / l;
                }
            }
            System.arraycopy(x, 0, ox, 0, n); System.arraycopy(y, 0, oy, 0, n); System.arraycopy(z, 0, oz, 0, n);
            System.arraycopy(tx, 0, otx, 0, n); System.arraycopy(ty, 0, oty, 0, n); System.arraycopy(tz, 0, otz, 0, n);
            ready = true;
        } else {
            System.arraycopy(x, 0, ox, 0, n); System.arraycopy(y, 0, oy, 0, n); System.arraycopy(z, 0, oz, 0, n);
            System.arraycopy(tx, 0, otx, 0, n); System.arraycopy(ty, 0, oty, 0, n); System.arraycopy(tz, 0, otz, 0, n);
        }
        lastX = px; lastZ = pz;

        // Dying, everything stops where it is. His legs are all breaking at once as he comes down, and dragging
        // broken feet about while the legs are still being solved tore his back end apart on screen.
        if (dying) return;

        float stride = MountainRig.STRIDE * s;
        int walking = 0, lifted = 0;
        for (MountainRig.LegDef L : rig.legs) if (L.kind != 2) { walking++; if (swing[L.k]) lifted++; }
        int cap = Math.max(2, (int) (walking * 0.42f));
        float minRate = 1f / (10f + 26f * s);
        float rate = Math.max(dPhase / (1f - MountainRig.DUTY), minRate);

        for (MountainRig.LegDef L : rig.legs) {
            int k = L.k;
            Home h = home(L, L.endAt(sprawl), px, pz, c, sn, s);
            double ivx = h.x - hx[k], ivz = h.z - hz[k];
            if (Math.hypot(ivx, ivz) > 4 * s + 2) { ivx = 0; ivz = 0; }
            vx[k] = vx[k] * 0.7 + ivx * 0.3; vz[k] = vz[k] * 0.7 + ivz * 0.3;
            hx[k] = h.x; hz[k] = h.z;

            if (L.kind == 2) { tentacle(L, h, g, py, s, c, sn, px, pz, time); continue; }
            if (legBroken(k)) { dragBroken(L, h, g, py, s); continue; }
            if (k == whipLeg && whip(L, h, g, py, s)) { swing[k] = false; known[k] = true; continue; }   // a stomp has this leg

            float p = frac(phase + phaseOffset(L));
            boolean crossed = dPhase > 1e-6f && lastP[k] < MountainRig.DUTY && p >= MountainRig.DUTY && p - lastP[k] < 0.5f;
            lastP[k] = p;

            if (swing[k]) {
                t[k] = Math.min(1f, t[k] + rate);
                if (t[k] < 0.8f) landing(L, h, rate, dPhase, g, py, s);
                float e = t[k] * t[k] * (3f - 2f * t[k]);
                double hd = Math.hypot(ex[k] - sx[k], ez[k] - sz[k]);
                double arc = (2.5 + 0.3 * hd / s) * s;
                double base = sy[k] + (ey[k] - sy[k]) * e;
                double top = Math.max(sy[k], ey[k]);
                double up = Math.sin(Math.PI * Math.pow(t[k], 0.85));
                x[k] = sx[k] + (ex[k] - sx[k]) * e;
                z[k] = sz[k] + (ez[k] - sz[k]) * e;
                y[k] = base + (arc + (top - base)) * up;
                if (t[k] >= 1f) {
                    swing[k] = false;
                    known[k] = !Double.isNaN(footing(g, ex[k], ez[k], s, L.pad));
                    x[k] = ex[k]; y[k] = ey[k]; z[k] = ez[k];
                    if (land != null) land.land(k, x[k], y[k] - L.endY * s, z[k]);
                }
                continue;
            }
            if (dying) continue;
            // planted: time to step?
            double leadX = 0, leadZ = 0;
            if (dPhase > 1e-5f) {
                double f = 0.5 * MountainRig.DUTY / dPhase;
                leadX = vx[k] * f; leadZ = vz[k] * f;
                double ll = Math.hypot(leadX, leadZ), cap2 = 0.6 * stride;
                if (ll > cap2) { leadX *= cap2 / ll; leadZ *= cap2 / ll; }
            }
            double off = Math.hypot(x[k] - (h.x + leadX), z[k] - (h.z + leadZ));
            double stretch = Math.hypot(x[k] - h.x, z[k] - h.z);
            // keep a planted foot on the ground under it: the ground may not have been loaded when it came down,
            // or a block under it may have been broken or placed since
            double fg = Double.NaN;
            if (!known[k] || ((int) time + k) % 6 == 0) {
                fg = footing(g, x[k], z[k], s, L.pad);
                if (!Double.isNaN(fg)) {
                    double want = fg + L.endY * s;
                    // not known yet, or way off (the ground only just loaded): put it straight down on the real ground
                    if (!known[k] || Math.abs(want - y[k]) > 6 * s + 4) y[k] = want;
                    else if (Math.abs(want - y[k]) > 0.15) y[k] += Math.max(-0.5 - 0.5 * s, Math.min(0.5 + 0.5 * s, want - y[k]));
                    known[k] = true;
                }
            }
            boolean free = true;
            for (int nb : neighbours[k]) if (nb >= 0 && swing[nb]) free = false;
            boolean go = (crossed && off > 0.1 * stride)
                    || (stretch > 0.85 * stride && free && lifted < cap)
                    || stretch > 1.25 * stride                                    // never leave a foot behind, whatever the others are doing
                    || (!Double.isNaN(fg) && Math.abs(y[k] - (fg + L.endY * s)) > 3 * s + 1.5 && free && lifted < cap);
            if (go) {
                swing[k] = true; t[k] = 0f; lifted++;
                sx[k] = x[k]; sy[k] = y[k]; sz[k] = z[k];
                landing(L, h, rate, dPhase, g, py, s);
            }
        }
    }

    /** a broken leg: it stops taking steps and is dragged along the ground behind its hip */
    private void dragBroken(MountainRig.LegDef L, Home h, Ground g, double py, float s) {
        int k = L.k;
        swing[k] = false; t[k] = 0f;
        double tx = h.x - vx[k] * 7, tz = h.z - vz[k] * 7;
        double dx = tx - x[k], dz = tz - z[k];
        double d = Math.hypot(dx, dz), cap = 1.6 * s + 0.4;
        if (d > 1e-6) { double f = Math.min(1.0, cap / d) * 0.25; x[k] += dx * f; z[k] += dz * f; }
        double gy = ground(g, x[k], z[k], py);
        known[k] = !Double.isNaN(g.at(x[k], z[k]));
        y[k] = ease(y[k], gy + L.endY * s, s);
    }

    /** where a swinging foot will come down: ahead of where its home will be when it lands */
    private void landing(MountainRig.LegDef L, Home h, float rate, float dPhase, Ground g, double py, float s) {
        int k = L.k;
        double left = (1f - t[k]) / rate;
        double lx = 0, lz = 0;
        if (dPhase > 1e-5f) {
            double f = 0.5 * MountainRig.DUTY / dPhase;
            lx = vx[k] * f; lz = vz[k] * f;
            double ll = Math.hypot(lx, lz), cap = 0.6 * MountainRig.STRIDE * s;
            if (ll > cap) { lx *= cap / ll; lz *= cap / ll; }
        }
        double gx = h.x + vx[k] * left + lx, gz = h.z + vz[k] * left + lz;
        if (t[k] < 0.05f) { nx[k] = 0; nz[k] = 0; }
        if (t[k] < 0.05f || (t[k] < 0.6f && (int) (t[k] * 40) % 6 == 0)) pickFooting(L, gx, gz, g, s);
        gx += nx[k]; gz += nz[k];
        ex[k] = gx; ez[k] = gz;
        if (t[k] < 0.05f || (int) (t[k] * 40) % 3 == 0 || Double.isNaN(ey[k])) {
            double fh = footing(g, gx, gz, s, L.pad);
            ey[k] = (Double.isNaN(fh) ? py : fh) + L.endY * s;
        }
        if (DEBUG && ey[k] - ground(g, gx, gz, py) > 3 * s + 2) System.out.println("GAIT high landing leg " + k + " ey " + ey[k] + " centre ground " + ground(g, gx, gz, py) + " py " + py + " at " + gx + "," + gz);
    }

    /** try the spot and a few around it; keep the one where the ground under the pad is most even */
    private void pickFooting(MountainRig.LegDef L, double gx, double gz, Ground g, float s) {
        int k = L.k;
        double step = 0.22 * MountainRig.STRIDE * s, best = Double.MAX_VALUE, bx = nx[k], bz = nz[k];
        for (int i = -1; i < 8; i++) {
            double cx = i < 0 ? nx[k] : nx[k] + Math.cos(i * Math.PI / 4) * step, cz = i < 0 ? nz[k] : nz[k] + Math.sin(i * Math.PI / 4) * step;
            double len = Math.hypot(cx, cz), lim = 0.35 * MountainRig.STRIDE * s;
            if (len > lim) { cx *= lim / len; cz *= lim / len; }
            double r = roughness(g, gx + cx, gz + cz, s, L.pad);
            if (Double.isNaN(r)) continue;
            double score = r + 0.12 * Math.hypot(cx, cz) / Math.max(0.05, s);
            if (score < best - 0.01) { best = score; bx = cx; bz = cz; }
        }
        nx[k] = bx; nz[k] = bz;
    }

    /** how uneven the ground under a foot pad is (highest minus lowest), NaN if not loaded */
    private static double roughness(Ground g, double x, double z, float s, float pad) {
        double c = g.at(x, z);
        if (Double.isNaN(c)) return Double.NaN;
        double lo = c, hi = c, r = Math.max(0.7, 0.55 * pad * s);
        for (int i = 0; i < 8; i++) {
            double v = g.at(x + Math.cos(i * Math.PI / 4) * r, z + Math.sin(i * Math.PI / 4) * r);
            if (Double.isNaN(v)) continue;
            lo = Math.min(lo, v); hi = Math.max(hi, v);
        }
        return (hi - lo) / Math.max(0.05, s);
    }

    private void tentacle(MountainRig.LegDef L, Home h, Ground g, double py, float s, double c, double sn, double px, double pz, float time) {
        int k = L.k;
        if (k == whipLeg && whip(L, h, g, py, s)) { tip(L, h, g, py, s, c, sn, px, pz, time); return; }
        // the ground end is dragged along on a spring, so it trails behind him and slides round when he turns
        double k1 = 0.07;
        x[k] += (h.x - x[k]) * k1;
        z[k] += (h.z - z[k]) * k1;
        double lag = Math.hypot(x[k] - h.x, z[k] - h.z), maxLag = 0.55 * MountainRig.STRIDE * s;
        if (lag > maxLag) { x[k] = h.x + (x[k] - h.x) * maxLag / lag; z[k] = h.z + (z[k] - h.z) * maxLag / lag; }
        y[k] = ease(y[k], ground(g, x[k], z[k], py) + L.endY * s, s);
        tip(L, h, g, py, s, c, sn, px, pz, time);
    }

    /** The whip: rear the tentacle up, crack it down onto the target, leave it there a moment, drag it back. */
    private boolean whip(MountainRig.LegDef L, Home h, Ground g, double py, float s) {
        int k = L.k;
        float t = whipT;
        if (t <= 0f || t >= 1f) return false;
        double gy = ground(g, whipX, whipZ, py) + L.endY * s;
        double hy = ground(g, h.x, h.z, py) + L.endY * s;
        if (sweep) {
            // an arc round where the foot stands, through the target: out to one side, across, and back home
            double dx = whipX - h.x, dz = whipZ - h.z, R = Math.max(3 * s + 1, Math.hypot(dx, dz)), a0 = Math.atan2(dz, dx), open = 1.1;
            double ang, rad, lift;
            if (t < 0.3f) { double e = smooth(t / 0.3f); ang = a0 + open; rad = R * e; lift = 12 * s * Math.sin(Math.PI * 0.5 * e) + 2 * s; }
            else if (t < 0.62f) { double e = (t - 0.3f) / 0.32f; ang = a0 + open - 2 * open * e; rad = R; lift = 2 * s + 1.5 * s * Math.sin(Math.PI * e); }
            else { double e = smooth((t - 0.62f) / 0.38f); ang = a0 - open; rad = R * (1 - e); lift = 2 * s + 8 * s * Math.sin(Math.PI * e); }
            double nx2 = h.x + Math.cos(ang) * rad, nz2 = h.z + Math.sin(ang) * rad;
            x[k] = nx2; z[k] = nz2; y[k] = ground(g, nx2, nz2, py) + L.endY * s + lift;
            return true;
        }
        double rx = h.x + (whipX - h.x) * whipOut, rz = h.z + (whipZ - h.z) * whipOut, ry = hy + whipLift * s;
        double nx, ny, nz;
        if (t < 0.4f) {
            double e = smooth(t / 0.4f);
            nx = h.x + (rx - h.x) * e; nz = h.z + (rz - h.z) * e; ny = hy + (ry - hy) * e;
        } else if (t < 0.58f) {
            double e = (t - 0.4f) / 0.18f; e = e * e;
            nx = rx + (whipX - rx) * e; nz = rz + (whipZ - rz) * e; ny = ry + (gy - ry) * e;
        } else if (t < 0.78f) {
            nx = whipX; nz = whipZ; ny = gy;
        } else {
            double e = smooth((t - 0.78f) / 0.22f);
            nx = whipX + (h.x - whipX) * e; nz = whipZ + (h.z - whipZ) * e; ny = gy + (hy - gy) * e + 8 * s * Math.sin(Math.PI * e);
        }
        x[k] = nx; y[k] = ny; z[k] = nz;
        return true;
    }

    private static double smooth(double t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }

    private void tip(MountainRig.LegDef L, Home h, Ground g, double py, float s, double c, double sn, double px, double pz, float time) {
        int k = L.k;
        // the tip follows like a rope, drifting back to its natural direction, and writhes
        Home rest = home(L, L.tipAt(sprawl), px, pz, c, sn, s);
        double rx = rest.x - h.x, rz = rest.z - h.z, rl = Math.max(1e-6, Math.hypot(rx, rz));
        double len = rl;
        double fx = tx[k] - x[k], fz = tz[k] - z[k], fl = Math.hypot(fx, fz);
        if (fl > 1e-6) { fx /= fl; fz /= fl; } else { fx = rx / rl; fz = rz / rl; }
        double bx = fx * 0.9 + (rx / rl) * 0.1, bz = fz * 0.9 + (rz / rl) * 0.1, bl = Math.max(1e-6, Math.hypot(bx, bz));
        dirX[k] = bx / bl; dirZ[k] = bz / bl;
        double w = 0.32 * Math.sin(time * 0.045 + k * 1.7) + 0.14 * Math.sin(time * 0.11 + k * 0.6);
        double cw = Math.cos(w), sw = Math.sin(w);
        double wx = dirX[k] * cw - dirZ[k] * sw, wz = dirX[k] * sw + dirZ[k] * cw;
        tx[k] = x[k] + wx * len; tz[k] = z[k] + wz * len;
        // the last piece is straight, so lift the tip enough that it clears any bump between it and the ground end
        double want = ground(g, tx[k], tz[k], py) + L.joints[3].y * s;
        for (double f : new double[]{0.33, 0.66}) {
            double gm = ground(g, x[k] + (tx[k] - x[k]) * f, z[k] + (tz[k] - z[k]) * f, py) + (L.endY + (L.joints[3].y - L.endY) * f) * s;
            want = Math.max(want, (gm - y[k] * (1 - f)) / f);
        }
        ty[k] = ease(ty[k], want, s);
    }

    /** slides a dragged height towards the ground so it doesn't jump a whole block at each edge */
    private static double ease(double cur, double want, float s) {
        return Math.abs(want - cur) > 6 * s + 3 ? want : cur + (want - cur) * 0.35;
    }

    /** true while some planted foot is far enough from where it belongs under him that it should be moved */
    public boolean restless(float s) {
        if (!ready) return false;
        double lim = 0.25 * MountainRig.STRIDE * Math.max(0.02f, s);
        for (MountainRig.LegDef L : rig.legs) {
            int k = L.k;
            if (L.kind == 2 || swing[k]) continue;
            if (Math.hypot(x[k] - hx[k], z[k] - hz[k]) > lim) return true;
        }
        return false;
    }

    private record Home(double x, double z) {}

    private static Home home(MountainRig.LegDef L, org.joml.Vector3f p, double px, double pz, double c, double sn, float s) {
        return new Home(px + (p.x * c + p.z * sn) * s, pz + (-p.x * sn + p.z * c) * s);
    }

    /**
     * Where a foot pad rests: on the higher part of the ground under it (so it sits on a slope or a bump instead of
     * sinking in), but never propped up on one tall block at its edge. NaN when the ground there isn't loaded.
     */
    static double footing(Ground g, double x, double z, float s, float pad) {
        double c = g.at(x, z);
        if (Double.isNaN(c)) return Double.NaN;
        double r = Math.max(0.7, 0.5 * pad * s);
        double[] hs = new double[9];
        hs[0] = c;
        int n = 1;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            double v = g.at(x + Math.cos(a) * r, z + Math.sin(a) * r);
            if (!Double.isNaN(v)) hs[n++] = v;
        }
        java.util.Arrays.sort(hs, 0, n);
        double v = hs[(int) Math.floor((n - 1) * 0.7)];
        return Math.min(v, c + 1.0 + 1.5 * s);
    }

    private static double ground(Ground g, double x, double z, double fallback) {
        double v = g.at(x, z);
        return Double.isNaN(v) ? fallback : v;
    }

    private static float frac(float v) { return v - (float) Math.floor(v); }

    /**
     * Writes where each leg's end and each tentacle tip should be, in his model space, blended between the last
     * tick and this one. The entity position and yaw passed in must be the same blend.
     */
    public void toModel(float partial, double px, double py, double pz, float yawDeg, float s, float[] feet, float[] tips) {
        s = Math.max(0.02f, s);
        double th = Math.toRadians(180f - yawDeg), c = Math.cos(th), sn = Math.sin(th);
        for (int k = 0; k < n; k++) {
            put(feet, k, ox[k] + (x[k] - ox[k]) * partial - px, oy[k] + (y[k] - oy[k]) * partial - py, oz[k] + (z[k] - oz[k]) * partial - pz, c, sn, s);
            put(tips, k, otx[k] + (tx[k] - otx[k]) * partial - px, oty[k] + (ty[k] - oty[k]) * partial - py, otz[k] + (tz[k] - otz[k]) * partial - pz, c, sn, s);
        }
    }

    private static void put(float[] a, int k, double dx, double dy, double dz, double c, double sn, float s) {
        // inverse of rotateY(th): (x, z) -> (x c - z sn, x sn + z c)
        a[k * 3] = (float) ((dx * c - dz * sn) / s);
        a[k * 3 + 1] = (float) (dy / s);
        a[k * 3 + 2] = (float) ((dx * sn + dz * c) / s);
    }
}
