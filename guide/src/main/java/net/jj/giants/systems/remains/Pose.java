package net.jj.giants.systems.remains;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * (remains) How a fallen giant lies: each bone turned about its own pivot (a child turns with its parent), as his own
 * rig would pose him, and a part of a bone may snap off and lie apart. Affine maps are 12 numbers: a 3x3 turn (row
 * by row) and then where it moves to (p' = R p + t). Model space throughout.
 */
public final class Pose {
    final GiantModel m;
    private final double[][] local;
    private final double[][] set;
    final double[][] abs;
    final List<List<Snap>> snaps = new ArrayList<>();
    /** bones that sag to the ground once placed (strands, hair, slack arms) and how much (0 none, 1 flat) */
    final double[] drape;

    /** a piece of a bone, past a plane, turned apart from the rest */
    record Snap(double nx, double ny, double nz, double d, double[] map) {
        boolean has(double x, double y, double z) {
            return nx * x + ny * y + nz * z > d;
        }
    }

    public Pose(GiantModel m) {
        this.m = m;
        int n = m.names.length;
        local = new double[n][];
        set = new double[n][];
        abs = new double[n][];
        drape = new double[n];
        for (int i = 0; i < n; i++) snaps.add(new ArrayList<>());
    }

    static double[] ident() {
        return new double[] {1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0};
    }

    /** the turn by deg about an axis (through 0) */
    static double[] rot(double ax, double ay, double az, double deg) {
        double l = Math.sqrt(ax * ax + ay * ay + az * az);
        if (l < 1e-9) return ident();
        ax /= l;
        ay /= l;
        az /= l;
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a), t = 1 - c;
        return new double[] {t * ax * ax + c, t * ax * ay - s * az, t * ax * az + s * ay, t * ax * ay + s * az,
            t * ay * ay + c, t * ay * az - s * ax, t * ax * az - s * ay, t * ay * az + s * ax, t * az * az + c, 0, 0,
            0};
    }

    /** that turn about a point */
    static double[] about(double[] r, double px, double py, double pz) {
        double[] o = r.clone();
        o[9] = px - (r[0] * px + r[1] * py + r[2] * pz);
        o[10] = py - (r[3] * px + r[4] * py + r[5] * pz);
        o[11] = pz - (r[6] * px + r[7] * py + r[8] * pz);
        return o;
    }

    /** a then b (b of a) */
    static double[] then(double[] a, double[] b) {
        double[] o = new double[12];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++)
                o[i * 3 + j] = b[i * 3] * a[j] + b[i * 3 + 1] * a[3 + j] + b[i * 3 + 2] * a[6 + j];
            o[9 + i] = b[i * 3] * a[9] + b[i * 3 + 1] * a[10] + b[i * 3 + 2] * a[11] + b[9 + i];
        }
        return o;
    }

    static double[] apply(double[] a, double x, double y, double z) {
        return new double[] {a[0] * x + a[1] * y + a[2] * z + a[9], a[3] * x + a[4] * y + a[5] * z + a[10],
            a[6] * x + a[7] * y + a[8] * z + a[11]};
    }

    static double[] dir(double[] a, double x, double y, double z) {
        return new double[] {a[0] * x + a[1] * y + a[2] * z, a[3] * x + a[4] * y + a[5] * z,
            a[6] * x + a[7] * y + a[8] * z};
    }

    /** turn this bone (and all on it) about its pivot, about an axis of model space at rest */
    public Pose turn(int b, double ax, double ay, double az, double deg) {
        if (b < 0) return this;
        double[] r = rot(ax, ay, az, deg);
        local[b] = local[b] == null ? r : then(local[b], r);
        return this;
    }

    public Pose turn(String name, double ax, double ay, double az, double deg) {
        return turn(m.bone(name), ax, ay, az, deg);
    }

    /** turn every bone whose name passes (each about its own pivot) */
    public Pose turnAll(Predicate<String> which, double ax, double ay, double az, double deg) {
        for (int b = 0; b < m.names.length; b++) if (which.test(m.names[b])) turn(b, ax, ay, az, deg);
        return this;
    }

    /** this bone is put just here (its own map from model space at rest; nothing of its parent's) */
    public Pose place(int b, double[] map) {
        if (b >= 0) set[b] = map;
        return this;
    }

    /** the part of this bone past the plane (n . p > d, model space at rest) turns apart about a point */
    public Pose snap(int b, double nx, double ny, double nz, double d, double px, double py, double pz, double ax,
            double ay, double az, double deg) {
        if (b < 0) return this;
        snaps.get(b).add(new Snap(nx, ny, nz, d, about(rot(ax, ay, az, deg), px, py, pz)));
        return this;
    }

    public Pose drape(Predicate<String> which, double k) {
        for (int b = 0; b < m.names.length; b++) if (which.test(m.names[b])) drape[b] = k;
        return this;
    }

    /** works out where each bone is now */
    public Pose solve() {
        for (int b = 0; b < abs.length; b++) solve(b, 0);
        return this;
    }

    private double[] solve(int b, int depth) {
        if (abs[b] != null) return abs[b];
        if (set[b] != null) return abs[b] = set[b];
        double[] mine = local[b] == null ? ident()
                : about(local[b], m.pivot[b][0], m.pivot[b][1], m.pivot[b][2]);
        int p = m.parent[b];
        if (p < 0 || depth > 200) return abs[b] = mine;
        return abs[b] = then(mine, solve(p, depth + 1));
    }

    /** a point of this bone (model space at rest) where it lies now */
    public double[] at(int b, double x, double y, double z) {
        return apply(abs[b], x, y, z);
    }

    public double[] at(int b, float[] p) {
        return apply(abs[b], p[0], p[1], p[2]);
    }

    /** a voxel's place, its snap (if any) first */
    double[] voxel(int b, double x, double y, double z) {
        for (Snap s : snaps.get(b))
            if (s.has(x, y, z)) {
                double[] q = apply(s.map, x, y, z);
                return apply(abs[b], q[0], q[1], q[2]);
            }
        return apply(abs[b], x, y, z);
    }

    /** the turn a voxel of this bone has had (for blocks that face a way) */
    double[] turnOf(int b, double x, double y, double z) {
        for (Snap s : snaps.get(b)) if (s.has(x, y, z)) return then(s.map, abs[b]);
        return abs[b];
    }
}
