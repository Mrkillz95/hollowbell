package net.jj.giants.systems.remains;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * (remains) A fallen giant as blocks, in the world's own grid round the spot he fell (x z from where he stood, y 0 the
 * first free block over the ground there). Three layers per block: his own model's block (with what it does as he
 * rots: flesh, hard parts, eyes), the bones drawn into him, and extras a design adds. Then each stage of the rot is
 * worked out per block: 0 fresh, 1 rotting, 2 falling away, 3 bare bones (a corpse's last), and 4 the skeleton left
 * for good (the "skeleton" rule). Plain Java: block states are text, turned into the game's own by the caller.
 */
public final class Carcass {
    /** what a block of his model does as he rots */
    public static final byte SKIP = 0, FLESH = 1, HARD = 2, EYE = 3;
    public static final int STAGES = 5, SKELETON = 4;
    static final String AIR = "minecraft:air";

    final GiantModel m;
    final Pose pose;
    final Design d;
    /** his size: model blocks to world blocks */
    public final double s;
    /** the lie: posed model space -> lying model space (y 0 the ground) */
    final double[] lie;
    private final double cos, sin;
    final long seed;

    // the blocks
    private long[] keys = new long[1 << 16];
    private int[] slots = new int[1 << 17];
    int n;
    int[] cx = new int[1 << 16], cy = new int[1 << 16], cz = new int[1 << 16];
    int[] model = new int[1 << 16], bone = new int[1 << 16];
    byte[] role = new byte[1 << 16];
    short[] owner = new short[1 << 16];
    /** a design's own extras: a block's material per stage (null: none) */
    final Map<Integer, int[]> extra = new HashMap<>();

    // materials
    final List<String> mats = new ArrayList<>();
    private final Map<String, Integer> matId = new HashMap<>();

    Carcass(GiantModel m, Pose pose, Design d, double s, double[] lie, float yaw, long seed) {
        this.m = m;
        this.pose = pose;
        this.d = d;
        this.s = s;
        this.lie = lie;
        double r = Math.toRadians(yaw);
        cos = Math.cos(r);
        sin = Math.sin(r);
        this.seed = seed;
        Arrays.fill(slots, -1);
        mat(AIR);
    }

    int mat(String s) {
        Integer i = matId.get(s);
        if (i != null) return i;
        mats.add(s);
        matId.put(s, mats.size() - 1);
        return mats.size() - 1;
    }

    String name(int id) {
        return mats.get(id);
    }

    // ---------------------------------------------------------------- the grid

    static long key(int x, int y, int z) {
        return ((long) (x + (1 << 20)) << 42) | ((long) (y + (1 << 20)) << 21) | (long) (z + (1 << 20));
    }

    private static int hash(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        return (int) k;
    }

    /** the block at x y z, or -1 */
    int find(int x, int y, int z) {
        long k = key(x, y, z);
        int mask = slots.length - 1;
        for (int h = hash(k) & mask; ; h = (h + 1) & mask) {
            int i = slots[h];
            if (i < 0) return -1;
            if (keys[i] == k) return i;
        }
    }

    /** the block at x y z, made if new */
    int cell(int x, int y, int z) {
        long k = key(x, y, z);
        int mask = slots.length - 1;
        int h = hash(k) & mask;
        for (; ; h = (h + 1) & mask) {
            int i = slots[h];
            if (i < 0) break;
            if (keys[i] == k) return i;
        }
        if (n == keys.length) grow();
        int i = n++;
        keys[i] = k;
        cx[i] = x;
        cy[i] = y;
        cz[i] = z;
        owner[i] = -1;
        if (n * 2 > slots.length) rehash();
        else slots[h] = i;
        return i;
    }

    private void grow() {
        int c = keys.length * 2;
        keys = Arrays.copyOf(keys, c);
        cx = Arrays.copyOf(cx, c);
        cy = Arrays.copyOf(cy, c);
        cz = Arrays.copyOf(cz, c);
        model = Arrays.copyOf(model, c);
        bone = Arrays.copyOf(bone, c);
        role = Arrays.copyOf(role, c);
        owner = Arrays.copyOf(owner, c);
    }

    private void rehash() {
        slots = new int[slots.length * 2];
        Arrays.fill(slots, -1);
        int mask = slots.length - 1;
        for (int i = 0; i < n; i++) {
            int h = hash(keys[i]) & mask;
            while (slots[h] >= 0) h = (h + 1) & mask;
            slots[h] = i;
        }
    }

    // ---------------------------------------------------------------- places

    /** a lying point (model blocks) -> the world's grid round the spot (continuous) */
    double[] world(double[] q) {
        double x = q[0] * s, y = q[1] * s, z = q[2] * s;
        return new double[] {x * cos - z * sin, y, x * sin + z * cos};
    }

    /** a posed point -> lying */
    double[] lying(double[] p) {
        return Pose.apply(lie, p[0], p[1], p[2]);
    }

    /** a posed point -> the world grid */
    public double[] w(double[] p) {
        return world(lying(p));
    }

    /** a posed point of this bone -> the world grid, sagging as that bone's blocks do */
    public double[] wb(int bone, double[] p) {
        double[] q = lying(p);
        double k = bone >= 0 ? pose.drape[bone] : 0;
        if (k > 0 && q[1] > 0) q[1] *= 1 - k;
        return world(q);
    }

    /** a line between points of two bones (each sagging as its bone does), radius in model blocks */
    public void lineB(int ba, double[] a, int bb, double[] b, double ra, double rb, String mat) {
        lineW(wb(ba, a), wb(bb, b), Math.max(0.5, ra * s), Math.max(0.5, rb * s), mat);
    }

    public void ballB(int bone, double[] c, double r, String mat) {
        double[] p = wb(bone, c);
        fill(p[0], p[1], p[2], Math.max(0.5, r * s), mat);
    }

    /** a posed direction -> the world grid (unit) */
    public double[] wdir(double[] v) {
        double[] q = Pose.dir(lie, v[0], v[1], v[2]);
        double x = q[0], z = q[2];
        double[] o = {x * cos - z * sin, q[1], x * sin + z * cos};
        return unit(o);
    }

    static double[] unit(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return l < 1e-9 ? new double[] {0, 1, 0} : new double[] {v[0] / l, v[1] / l, v[2] / l};
    }

    /** the whole turn a model block of this bone has had, ending in the world grid */
    double[] turnToWorld(double[] boneTurn) {
        double[] t = Pose.then(boneTurn, lie);
        double[] yawM = {cos, 0, -sin, 0, 1, 0, sin, 0, cos, 0, 0, 0};
        return Pose.then(t, yawM);
    }

    // ---------------------------------------------------------------- his own blocks

    /** puts his model down, each block by what the design makes of it */
    void body() {
        int k = s >= 1.01 ? (int) Math.ceil(s * 2) : s >= 0.45 ? 2 : 1;
        double step = 1.0 / k;
        Map<Long, Integer> cache = new HashMap<>();
        for (int b = 0; b < m.names.length; b++) {
            int nv = m.x[b].length;
            if (nv == 0) continue;
            double drape = pose.drape[b];
            double[] turn = turnToWorld(pose.abs[b]);
            for (int i = 0; i < nv; i++) {
                int pi = m.pal[b][i];
                long ck = ((long) b << 20) | pi;
                Integer packed = cache.get(ck);
                if (packed == null) {
                    String block = m.palette[pi];
                    byte r = d.role(m, b, block);
                    int id = r == SKIP ? 0 : mat(Blocks.orient(block, turn));
                    packed = (id << 3) | r;
                    cache.put(ck, packed);
                }
                byte r = (byte) (packed & 7);
                if (r == SKIP) continue;
                int id = packed >>> 3;
                // a block of his frame (a machine's ribs): never a hole
                if (r == HARD && d.frame(m, b, m.x[b][i], m.y[b][i], m.z[b][i])) r = (byte) (r | 8);
                for (int a = 0; a < k; a++)
                    for (int bb = 0; bb < k; bb++)
                        for (int c = 0; c < k; c++) {
                            double[] p = pose.voxel(b, m.x[b][i] + (a + 0.5) * step, m.y[b][i] + (bb + 0.5) * step,
                                    m.z[b][i] + (c + 0.5) * step);
                            double[] q = lying(p);
                            if (drape > 0 && q[1] > 0) q[1] = q[1] * (1 - drape);
                            double[] wp = world(q);
                            int x = floor(wp[0]), y = floor(wp[1]), z = floor(wp[2]);
                            if (y < -3) continue;
                            int ci = cell(x, y, z);
                            byte was = role[ci];
                            if (was == 0 || prio(r) > prio(was) || (r == (HARD | 8) && was == HARD)) {
                                role[ci] = r;
                                model[ci] = id;
                                owner[ci] = (short) b;
                            }
                        }
            }
        }
    }

    private static int prio(byte r) {
        r = (byte) (r & 7);
        return r == EYE ? 3 : r == HARD ? 2 : r == FLESH ? 1 : 0;
    }

    static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    // ---------------------------------------------------------------- drawing bones

    /** a bone block (or anything) at a spot of the world grid, in the bone layer */
    public void put(int x, int y, int z, String mat) {
        if (y < -3) return;
        int c = cell(x, y, z);
        bone[c] = mat(mat);
    }

    /** takes the bone layer away here (sockets, gaps) */
    public void clear(int x, int y, int z) {
        int c = find(x, y, z);
        if (c >= 0) bone[c] = 0;
    }

    /** a thick line in the world grid, radius from ra to rb; "{axis}" in the block becomes the line's way */
    public void lineW(double[] a, double[] b, double ra, double rb, String mat) {
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        String m2 = mat;
        if (mat.contains("{axis}")) {
            double ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
            m2 = mat.replace("{axis}", ay >= ax && ay >= az ? "y" : ax >= az ? "x" : "z");
        }
        int steps = Math.max(1, (int) Math.ceil(len * 2.5));
        for (int i = 0; i <= steps; i++) {
            double f = i / (double) steps;
            double x = a[0] + dx * f, y = a[1] + dy * f, z = a[2] + dz * f;
            double r = ra + (rb - ra) * f;
            fill(x, y, z, r, m2);
        }
    }

    private void fill(double x, double y, double z, double r, String mat) {
        if (mat.contains("{axis}")) mat = mat.replace("{axis}", "y");
        if (r < 0.7) {
            put(floor(x), floor(y), floor(z), mat);
            return;
        }
        int R = (int) Math.ceil(r);
        int bx = floor(x), by = floor(y), bz = floor(z);
        for (int i = -R; i <= R; i++)
            for (int j = -R; j <= R; j++)
                for (int k = -R; k <= R; k++) {
                    double ex = bx + i + 0.5 - x, ey = by + j + 0.5 - y, ez = bz + k + 0.5 - z;
                    if (ex * ex + ey * ey + ez * ez <= r * r) put(bx + i, by + j, bz + k, mat);
                }
    }

    /** a line between two posed points, its radius in model blocks (never under one block) */
    public void line(double[] a, double[] b, double ra, double rb, String mat) {
        lineW(w(a), w(b), Math.max(0.5, ra * s), Math.max(0.5, rb * s), mat);
    }

    public void ball(double[] c, double r, String mat) {
        double[] p = w(c);
        fill(p[0], p[1], p[2], Math.max(0.5, r * s), mat);
    }

    /** a world-grid ball */
    public void ballW(double[] p, double r, String mat) {
        fill(p[0], p[1], p[2], r, mat);
    }

    /** is the block here one of his model's, of these bones */
    boolean of(int x, int y, int z, IntPredicate bones) {
        int c = find(x, y, z);
        return c >= 0 && owner[c] >= 0 && bones.test(owner[c]);
    }

    /**
     * Ribs: at stations along the spine (posed points), each rib follows his body's own inside (cast out from the
     * spine until his flesh of these bones is met, then drawn a little inside it), over this arc each side of his
     * back (degrees from the back, 180 = all round). The spine itself: vertebrae with a spike up each.
     */
    public void ribs(double[][] spine, double[] up, IntPredicate torso, double gap, double inset, double arc,
            double thick, double vert, double spike, String ribMat, String spineMat) {
        List<double[]> pts = new ArrayList<>();
        for (double[] p : spine) pts.add(w(p));
        double[] U0 = wdir(up);
        double total = 0;
        for (int i = 1; i < pts.size(); i++) total += dist(pts.get(i - 1), pts.get(i));
        double gw = Math.max(2.6, gap * s), vw = Math.max(0.5, vert * s);
        double maxR = 0;
        for (int b = 0; b < m.names.length; b++) {
            if (!torso.test(b)) continue;
            float[] bx = m.box(b);
            if (bx != null) maxR = Math.max(maxR, Math.max(bx[3] - bx[0], Math.max(bx[4] - bx[1], bx[5] - bx[2])));
        }
        double rmax = maxR * s + 4;
        // the spine
        for (int i = 1; i < pts.size(); i++) lineW(pts.get(i - 1), pts.get(i), vw * 0.75, vw * 0.75, spineMat);
        int ribN = 0;
        for (double t = gw * 0.5; t < total - gw * 0.3; t += gw) {
            double[] P = along(pts, t), T = unit(sub(along(pts, Math.min(total, t + 1)), along(pts, Math.max(0, t - 1))));
            double[] U = unit(sub(U0, mul(T, dot(U0, T))));
            double[] V = cross(T, U);
            // a vertebra and its spike
            ballW(P, vw, spineMat);
            if (spike > 0) lineW(P, add(P, mul(U, Math.max(1.5, spike * s))), Math.max(0.5, vw * 0.4), 0.5,
                    spineMat);
            ribN++;
            for (int side = -1; side <= 1; side += 2) {
                double[] last = null;
                double stepDeg = Math.max(2, Math.min(12, 40 / Math.max(1, rmax * 0.25)));
                for (double th = 8; th <= arc; th += stepDeg) {
                    double a = Math.toRadians(th);
                    double[] dir = add(mul(U, Math.cos(a)), mul(V, side * Math.sin(a)));
                    // out from the spine to where his flesh this way ends (its outside: a small giant's body is
                    // a few blocks of solid flesh, a big one's a shell); the rib runs just inside that
                    double hit = -1;
                    int empty = 0;
                    for (double r = Math.max(1, vw); r < rmax; r += 0.5) {
                        double[] q = add(P, mul(dir, r));
                        if (of(floor(q[0]), floor(q[1]), floor(q[2]), torso)) {
                            hit = r;
                            empty = 0;
                        } else if (hit > 0 && ++empty >= 3) break;
                    }
                    if (hit < 0) {
                        last = null;
                        continue;
                    }
                    double r = Math.max(vw + 0.5, hit - Math.max(1, inset * s));
                    double[] q = add(P, mul(dir, r));
                    if (q[1] < 0) break;
                    if (last != null) lineW(last, q, Math.max(0.5, thick * s), Math.max(0.5, thick * s), ribMat);
                    else lineW(P, q, Math.max(0.5, thick * s), Math.max(0.5, thick * s), ribMat);
                    last = q;
                }
            }
        }
    }

    /**
     * A skull from his head's own shape: the blocks of these bones drawn in bone, pulled in toward their middle (at
     * rest, by these amounts across, up and along, so a skull is narrower than the head it was), a block the design
     * gives null for left open.
     */
    public void skull(int[] bones, double shrink, java.util.function.Function<String, String> bonemat) {
        skull(bones, shrink, shrink, shrink, bonemat);
    }

    public void skull(int[] bones, double sx, double sy, double sz,
            java.util.function.Function<String, String> bonemat) {
        double[] c = new double[3];
        int cnt = 0;
        for (int b : bones) {
            if (b < 0) continue;
            for (int i = 0; i < m.x[b].length; i++) {
                c[0] += m.x[b][i] + 0.5;
                c[1] += m.y[b][i] + 0.5;
                c[2] += m.z[b][i] + 0.5;
                cnt++;
            }
        }
        if (cnt == 0) return;
        c = new double[] {c[0] / cnt, c[1] / cnt, c[2] / cnt};
        Map<Integer, String> mm = new HashMap<>();
        int k = s >= 1.01 ? 2 : 1;
        for (int b : bones) {
            if (b < 0) continue;
            for (int i = 0; i < m.x[b].length; i++) {
                int pal = m.pal[b][i];
                String mt = mm.computeIfAbsent(pal, q -> bonemat.apply(m.palette[q]));
                if (mt == null) continue;
                for (int a = 0; a < k; a++) {
                    double o = k == 1 ? 0.5 : 0.25 + a * 0.5;
                    double x = c[0] + (m.x[b][i] + o - c[0]) * sx, y = c[1] + (m.y[b][i] + o - c[1]) * sy,
                            z = c[2] + (m.z[b][i] + o - c[2]) * sz;
                    double[] wp = w(pose.voxel(b, x, y, z));
                    put(floor(wp[0]), floor(wp[1]), floor(wp[2]), mt);
                }
            }
        }
    }

    /** opens a socket: the bone layer taken away in a ball round a posed point */
    public void socket(double[] at, double r) {
        double[] p = w(at);
        double R = Math.max(0.8, r * s);
        int Ri = (int) Math.ceil(R);
        for (int i = -Ri; i <= Ri; i++)
            for (int j = -Ri; j <= Ri; j++)
                for (int k = -Ri; k <= Ri; k++)
                    if (i * i + j * j + k * k <= R * R + 0.3)
                        clear(floor(p[0]) + i, floor(p[1]) + j, floor(p[2]) + k);
    }

    /** a design's extra at a world spot: its block in each stage (index 0-4; null = nothing then) */
    public void extra(int x, int y, int z, String... perStage) {
        if (y < -3) return;
        int c = cell(x, y, z);
        int[] e = new int[STAGES];
        for (int i = 0; i < STAGES; i++) e[i] = i < perStage.length && perStage[i] != null ? mat(perStage[i]) : 0;
        extra.put(c, e);
    }

    // ---------------------------------------------------------------- small vector things

    static double dist(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static double[] sub(double[] a, double[] b) {
        return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    static double[] add(double[] a, double[] b) {
        return new double[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    static double[] mul(double[] a, double k) {
        return new double[] {a[0] * k, a[1] * k, a[2] * k};
    }

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double[] lerp(double[] a, double[] b, double f) {
        return new double[] {a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
    }

    /** the point this far along a polyline */
    static double[] along(List<double[]> pts, double t) {
        for (int i = 1; i < pts.size(); i++) {
            double l = dist(pts.get(i - 1), pts.get(i));
            if (t <= l || i == pts.size() - 1) return lerp(pts.get(i - 1), pts.get(i), l < 1e-9 ? 0 : Math.min(1, t / l));
            t -= l;
        }
        return pts.get(0);
    }

    // ---------------------------------------------------------------- the stages

    /** what goes down: each block's place (world grid round the spot) and its block in each stage (0 = none) */
    public static final class Out {
        public final int n;
        public final int[] x, y, z;
        public final String[] mats;
        public final int[][] st;

        public Out(int n, int[] x, int[] y, int[] z, String[] mats, int[][] st) {
            this.n = n;
            this.x = x;
            this.y = y;
            this.z = z;
            this.mats = mats;
            this.st = st;
        }

        /** how many blocks show in this stage */
        public int count(int stage) {
            int c = 0;
            for (int i = 0; i < n; i++) if (st[stage][i] != 0) c++;
            return c;
        }
    }

    /** smooth noise 0-1 round a world spot, this many blocks across */
    double noise(int x, int y, int z, double size, int salt) {
        return Noise.at(x / size, y / size, z / size, seed * 31 + salt);
    }

    Out compose() {
        int[][] st = new int[STAGES][n];
        int air = 0;
        Map<Long, Integer> tokens = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int bm = bone[i];
            int x = cx[i], y = cy[i], z = cz[i];
            double big = noise(x, y, z, Math.max(2, 7 * Math.min(1, s * 1.6)), 1);
            double fine = Noise.hash(x, y, z, seed + 5);
            if (bm != 0 && mats.get(bm).startsWith("@")) {
                // a bone's own block, chosen here (a few tones of bone in patches)
                double tn = noise(x, y, z, Math.max(1.5, 4 * Math.min(1, s * 1.6)), 9) * 0.8 + fine * 0.2;
                int q = (int) (tn * 16);
                long tk = ((long) bm << 8) | q;
                Integer res = tokens.get(tk);
                if (res == null) {
                    String t = d.token(mats.get(bm), (q + 0.5) / 16.0);
                    res = t == null ? 0 : mat(t);
                    tokens.put(tk, res);
                }
                bm = res;
                bone[i] = bm;
            }
            double n2 = big * 0.75 + fine * 0.25;
            boolean framed = (role[i] & 8) != 0;
            byte r = (byte) (role[i] & 7);
            String block = model[i] == 0 ? null : name(model[i]);
            for (int k = 0; k < STAGES; k++) {
                int v;
                if (r == FLESH) {
                    v = flesh(block, k, n2, fine, y, bm);
                } else if (r == EYE) {
                    v = k == 0 ? mat(d.dead(block)) : bm;
                } else if (r == HARD) {
                    String h = d.hard(block, k, framed ? 1.0 : n2, fine, x, y, z);
                    int hv = h == null ? 0 : mat(h);
                    v = hv == 0 ? bm : hv;
                } else v = bm;
                st[k][i] = v;
            }
            int[] e = extra.get(i);
            if (e != null) for (int k = 0; k < STAGES; k++) st[k][i] = e[k];
        }
        // a corpse only ever loses or changes blocks: nothing comes back where one went (but a design's own extras
        // may come later: a fallen crown, shards on the ground; they go only into free spots)
        for (int k = 1; k < SKELETON; k++)
            for (int i = 0; i < n; i++) if (st[k - 1][i] == air && !extra.containsKey(i)) st[k][i] = air;
        d.finish(this, st);
        for (int k = 0; k < STAGES; k++) {
            Blocks.smooth(this, st[k]);
            Blocks.connect(this, st[k]);
        }
        // bottom up, so it rises from the ground as it goes down
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> cy[a] != cy[b] ? Integer.compare(cy[a], cy[b])
                : cx[a] != cx[b] ? Integer.compare(cx[a], cx[b]) : Integer.compare(cz[a], cz[b]));
        int[] ox = new int[n], oy = new int[n], oz = new int[n];
        int[][] os = new int[STAGES][n];
        int w = 0;
        for (int j = 0; j < n; j++) {
            int i = order[j];
            boolean any = false;
            for (int k = 0; k < STAGES; k++) any |= st[k][i] != 0;
            if (!any) continue;
            ox[w] = cx[i];
            oy[w] = cy[i];
            oz[w] = cz[i];
            for (int k = 0; k < STAGES; k++) os[k][w] = st[k][i];
            w++;
        }
        int[][] fin = new int[STAGES][];
        for (int k = 0; k < STAGES; k++) fin[k] = Arrays.copyOf(os[k], w);
        return new Out(w, Arrays.copyOf(ox, w), Arrays.copyOf(oy, w), Arrays.copyOf(oz, w),
                mats.toArray(new String[0]), fin);
    }

    /** a flesh block through the rot: fresh, then patches rotting and holes, then falling away to the bones */
    private int flesh(String block, int k, double n2, double fine, int y, int bm) {
        double low = Math.max(1, 2.2 * s);
        switch (k) {
            case 0:
                return mat(d.dead(block));
            case 1:
                if (n2 < 0.2) return bm;
                if (n2 < 0.55) return mat(d.rotten(block, fine, 1));
                return mat(d.dead(block));
            case 2:
                if (y < low && n2 < 0.75) return mat(d.rotten(block, fine, 2));
                if (n2 < 0.13) return mat(d.rotten(block, fine, 2));
                return bm;
            case 3:
                if (y < low * 0.6 && n2 < 0.3) return mat(d.rotten(block, fine, 3));
                return bm;
            default:
                return bm;
        }
    }
}
