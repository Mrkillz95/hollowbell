package net.jj.hollowbell.entity;

import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.rig.BellModel;
import net.jj.hollowbell.rig.BellPieces;
import net.jj.hollowbell.rig.BellRig;
import net.jj.hollowbell.solid.SolidShape;

import java.util.BitSet;

/**
 * What of the Hollowbell is solid, for the solid kit (net.jj.hollowbell.solid). The kit's "bones" here are the slices
 * his arms and strands are drawn in (see BellPieces; every other part is one slice of its own), so what you bump into
 * bends with them just as they are drawn.
 *
 * Solid (you bump into it and stand on it): the dome and its rim, the crown, the glowing spots and the vase inside,
 * the pods and the egg clumps, and his arms where they leave the rim (their first two pieces). Soft (it only pushes you
 * aside, never holds you, never stood on): every strand, and the far half of each arm. The model file only keeps the
 * blocks that show a face, so whatever his blocks wall in is filled in too, each filled block going with the nearest
 * block of him; the inside of the dome is open underneath, so it stays a hollow you can be in.
 */
public final class HollowSolid {
    private HollowSolid() {}

    private static volatile SolidShape shape;
    /** how many blocks were filled in, by kind (for the lab) */
    public static volatile String report = "";

    public static SolidShape shape() {
        SolidShape s = shape;
        if (s != null) return s;
        synchronized (HollowSolid.class) {
            if (shape == null) shape = build();
            return shape;
        }
    }

    public static boolean ready() { return shape != null; }

    /** what kind of solid a bone of his is */
    public static byte kindOf(BellRig rig, int bone) {
        return switch (rig.kind[bone]) {
            case BELL, RIM, CROWN, SPOT, POD, EGG -> SolidShape.SOLID;
            case ARM -> rig.seg[bone] <= 1 ? SolidShape.SOLID : SolidShape.SOFT;
            case STRAND -> SolidShape.SOFT;
        };
    }

    static SolidShape build() {
        long t0 = System.currentTimeMillis();
        BellRig rig = BellRig.get();
        BellModel m = BellModel.get();
        BellPieces pc = BellPieces.get();
        int nb = rig.boneCount();
        // the box round all of him as built, a block bigger all round
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (int b = 0; b < nb; b++) for (int i = 0; i < m.count(b); i++) {
            x0 = Math.min(x0, m.x[b][i]); y0 = Math.min(y0, m.y[b][i]); z0 = Math.min(z0, m.z[b][i]);
            x1 = Math.max(x1, m.x[b][i]); y1 = Math.max(y1, m.y[b][i]); z1 = Math.max(z1, m.z[b][i]);
        }
        x0--; y0--; z0--; x1++; y1++; z1++;
        final int nx = x1 - x0 + 1, ny = y1 - y0 + 1, nz = z1 - z0 + 1, total = nx * ny * nz;
        // each cell: the slice of him there (+1), 0 for none
        short[] owner = new short[total];
        for (int b = 0; b < nb; b++) for (int i = 0; i < m.count(b); i++)
            owner[((m.x[b][i] - x0) * ny + (m.y[b][i] - y0)) * nz + (m.z[b][i] - z0)] = (short) (pc.sliceOf[b][i] + 1);
        // the outside: every empty cell reachable from the edge of the box
        BitSet out = new BitSet(total);
        int[] q = new int[total];
        int head = 0, tail = 0;
        for (int x = 0; x < nx; x++) for (int y = 0; y < ny; y++) for (int z = 0; z < nz; z++) {
            if (x != 0 && y != 0 && z != 0 && x != nx - 1 && y != ny - 1 && z != nz - 1) continue;
            int i = (x * ny + y) * nz + z;
            if (owner[i] != 0 || out.get(i)) continue;
            out.set(i);
            q[tail++] = i;
        }
        int sx = ny * nz;
        while (head < tail) {
            int i = q[head++];
            int x = i / sx, r = i - x * sx, y = r / nz, z = r - y * nz;
            for (int k = 0; k < 6; k++) {
                int xx = x + (k == 0 ? 1 : k == 1 ? -1 : 0), yy = y + (k == 2 ? 1 : k == 3 ? -1 : 0), zz = z + (k == 4 ? 1 : k == 5 ? -1 : 0);
                if (xx < 0 || yy < 0 || zz < 0 || xx >= nx || yy >= ny || zz >= nz) continue;
                int j = (xx * ny + yy) * nz + zz;
                if (owner[j] != 0 || out.get(j)) continue;
                out.set(j);
                q[tail++] = j;
            }
        }
        // the walled-in cells: each goes with the nearest block of him (spread out from his blocks a cell at a time)
        head = 0; tail = 0;
        for (int i = 0; i < total; i++) {
            if (owner[i] == 0) continue;
            int x = i / sx, r = i - x * sx, y = r / nz, z = r - y * nz;
            boolean edge = false;
            for (int k = 0; k < 6 && !edge; k++) {
                int xx = x + (k == 0 ? 1 : k == 1 ? -1 : 0), yy = y + (k == 2 ? 1 : k == 3 ? -1 : 0), zz = z + (k == 4 ? 1 : k == 5 ? -1 : 0);
                if (xx < 0 || yy < 0 || zz < 0 || xx >= nx || yy >= ny || zz >= nz) continue;
                int j = (xx * ny + yy) * nz + zz;
                if (owner[j] == 0 && !out.get(j)) edge = true;
            }
            if (edge) q[tail++] = i;
        }
        int filled = 0;
        int[] filledBy = new int[BellRig.Kind.values().length];
        while (head < tail) {
            int i = q[head++];
            int x = i / sx, r = i - x * sx, y = r / nz, z = r - y * nz;
            for (int k = 0; k < 6; k++) {
                int xx = x + (k == 0 ? 1 : k == 1 ? -1 : 0), yy = y + (k == 2 ? 1 : k == 3 ? -1 : 0), zz = z + (k == 4 ? 1 : k == 5 ? -1 : 0);
                if (xx < 0 || yy < 0 || zz < 0 || xx >= nx || yy >= ny || zz >= nz) continue;
                int j = (xx * ny + yy) * nz + zz;
                if (owner[j] != 0 || out.get(j)) continue;
                owner[j] = owner[i];
                filled++;
                filledBy[rig.kind[pc.bone[owner[i] - 1]].ordinal()]++;
                q[tail++] = j;
            }
        }
        q = null;
        SolidShape.Builder sb = new SolidShape.Builder(pc.count);
        byte[] kindOfSlice = new byte[pc.count];
        for (int s = 0; s < pc.count; s++) kindOfSlice[s] = kindOf(rig, pc.bone[s]);
        for (int i = 0; i < total; i++) {
            int o = owner[i];
            if (o == 0) continue;
            int x = i / sx, r = i - x * sx, y = r / nz, z = r - y * nz;
            sb.cell(o - 1, kindOfSlice[o - 1], x + x0, y + y0, z + z0);
        }
        SolidShape s = sb.build();
        StringBuilder rep = new StringBuilder();
        for (BellRig.Kind k : BellRig.Kind.values()) if (filledBy[k.ordinal()] > 0) rep.append(k.name().toLowerCase()).append(' ').append(filledBy[k.ordinal()]).append(", ");
        report = filled + " filled in (" + rep + ")";
        HollowbellMod.LOG.info("Hollowbell: solid shape ready ({} frames, {} cells, {}; {} ms)", s.frames(), s.cells, report, System.currentTimeMillis() - t0);
        return s;
    }

    private static volatile float[][] hullBalls;

    /**
     * The balls his hull is made of (see GiantHull.balls; the same format, x y z r per ball, by frame): each used frame's
     * cells cut into cubes about 9 to his longest side, a ball over each cube well filled for the part of the cube
     * the frame reaches into. (GiantHull.balls asks each cube to be well filled as a whole: his arms are cut into thin
     * slices, so most of their cubes were left out, and his hull had holes where his arms are.)
     */
    public static float[][] hullBalls(boolean[] use) {
        float[][] b = hullBalls;
        if (b != null) return b;
        SolidShape sh = shape();
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (int f = 0; f < sh.frames(); f++) if (use[f]) for (int a = 0; a < 3; a++) { lo = Math.min(lo, sh.bounds[f][a]); hi = Math.max(hi, sh.bounds[f][a + 3]); }
        final int C = Math.max(3, (int) Math.ceil((hi - lo) / 9f));
        float[][] out = new float[sh.frames()][];
        for (int f = 0; f < sh.frames(); f++) {
            if (!use[f]) continue;
            float[] fb = sh.bounds[f];
            it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<float[]> cubes = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
            sh.cells(f, (x, y, z) -> {
                long k = (((long) Math.floorDiv(x, C) + 0x10000) << 40) | (((long) Math.floorDiv(y, C) + 0x10000) << 20) | ((long) Math.floorDiv(z, C) + 0x10000);
                float[] a = cubes.computeIfAbsent(k, q -> new float[]{0, 0, 0, 0, Math.floorDiv(x, C), Math.floorDiv(y, C), Math.floorDiv(z, C)});
                a[0] += x + 0.5f; a[1] += y + 0.5f; a[2] += z + 0.5f; a[3]++;
            });
            java.util.List<float[]> keep = new java.util.ArrayList<>();
            for (float[] a : cubes.values()) {
                // (the part of this cube inside the frame's own box)
                float vol = 1f;
                for (int k = 0; k < 3; k++) {
                    float c0 = a[4 + k] * C, c1 = c0 + C;
                    vol *= Math.max(0f, Math.min(c1, fb[k + 3]) - Math.max(c0, fb[k]));
                }
                float n = a[3];
                if (n < Math.max(2f, vol * 0.12f)) continue;
                float r = Math.min(C * 0.87f, 0.72f * C * (float) Math.cbrt(n / (C * C * C)) + 0.3f + 0.25f * C);
                keep.add(new float[]{a[0] / n, a[1] / n, a[2] / n, r});
            }
            float[] flat = new float[keep.size() * 4];
            for (int i = 0; i < keep.size(); i++) System.arraycopy(keep.get(i), 0, flat, i * 4, 4);
            out[f] = flat;
        }
        return hullBalls = out;
    }

    /** works it out off the main thread at start, so the first one seen doesn't stall the server */
    public static void preload() {
        Thread t = new Thread(() -> {
            try { shape(); } catch (Throwable e) { HollowbellMod.LOG.error("Hollowbell solid shape failed", e); }
        }, "Hollowbell solid shape");
        t.setDaemon(true);
        t.start();
    }
}
