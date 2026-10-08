package net.jj.hollowbell.solid;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * The solid kit: what of a creature is solid, as it was built (rest model space), one frame per bone that moves on
 * its own. A frame is cells (one model block each, kept in 4x4x4 bricks so a long thin part costs little), or a
 * few boxes (for a smaller creature made of simple shapes), or both (a slab on a part made of blocks). Each frame is SOLID (you stand on it and bump into it) or
 * SOFT (strands, kelp, cloth: they only push you gently aside, never hold you).
 *
 * Built once per creature type, then only read: safe from both the server and the client thread.
 */
public final class SolidShape {
    public static final byte NONE = 0, SOLID = 1, SOFT = 2;

    /** per frame: the bone of the pose it moves with, and SOLID or SOFT */
    public final int[] bone;
    public final byte[] kind;
    /** per frame: its box as built (minx miny minz maxx maxy maxz, model blocks) */
    public final float[][] bounds;
    /** per frame: bricks (key → 64 cells), or null for a frame made of boxes */
    final Long2LongOpenHashMap[] bricks;
    /** per frame: its boxes (6 floats each), or null for a frame made of cells */
    final float[][] boxes;
    /** per frame made of cells: the same cells as one bit each over its box (quicker to look up), and the box's corner
     *  and size in cells; null if the box is too big */
    final long[][] dense;
    final int[][] denseBox;
    /** how many bytes the quick look-up takes (for the report) */
    public final long denseBytes;
    /** per bone: its frame, or -1 */
    public final int[] frameOfBone;
    /** how many cells there are in all (for the report) */
    public final int cells;

    private SolidShape(int bones, List<Frame> fs) {
        int n = fs.size();
        bone = new int[n]; kind = new byte[n]; bounds = new float[n][]; bricks = new Long2LongOpenHashMap[n]; boxes = new float[n][];
        dense = new long[n][]; denseBox = new int[n][];
        long db = 0;
        frameOfBone = new int[bones];
        java.util.Arrays.fill(frameOfBone, -1);
        int c = 0;
        for (int i = 0; i < n; i++) {
            Frame f = fs.get(i);
            bone[i] = f.bone; kind[i] = f.kind; bounds[i] = f.bounds; bricks[i] = f.bricks; boxes[i] = f.boxes;
            if (f.bricks != null) {
                int x0 = (int) Math.floor(f.bounds[0]), y0 = (int) Math.floor(f.bounds[1]), z0 = (int) Math.floor(f.bounds[2]);
                int nx = (int) Math.ceil(f.bounds[3]) - x0, ny = (int) Math.ceil(f.bounds[4]) - y0, nz = (int) Math.ceil(f.bounds[5]) - z0;
                long vol = (long) nx * ny * nz;
                if (vol <= 6_000_000L) {
                    long[] d = new long[(int) ((vol + 63) >> 6)];
                    final int fx = x0, fy = y0, fz = z0, fny = ny, fnz = nz;
                    Long2LongOpenHashMap br = f.bricks;
                    for (var en : br.long2LongEntrySet()) {
                        long k = en.getLongKey(), m = en.getLongValue();
                        int bx = (int) (k >>> 32) - 0x8000, by = (int) ((k >>> 16) & 0xFFFF) - 0x8000, bz = (int) (k & 0xFFFF) - 0x8000;
                        for (int j = 0; j < 64; j++) if ((m & (1L << j)) != 0) {
                            int x = bx * 4 + (j >> 4) - fx, y = by * 4 + ((j >> 2) & 3) - fy, z = bz * 4 + (j & 3) - fz;
                            long idx = ((long) x * fny + y) * fnz + z;
                            d[(int) (idx >> 6)] |= 1L << (idx & 63);
                        }
                    }
                    dense[i] = d; denseBox[i] = new int[]{x0, y0, z0, nx, ny, nz};
                    db += d.length * 8L;
                }
            }
            frameOfBone[f.bone] = i;
            c += f.cells;
        }
        cells = c;
        denseBytes = db;
    }

    public int frames() { return bone.length; }

    static long brickKey(int bx, int by, int bz) { return ((long) (bx + 0x8000) << 32) | ((long) (by + 0x8000) << 16) | (long) (bz + 0x8000); }

    /** is the cell x y z (model blocks, as built) of frame f solid */
    public boolean has(int f, int x, int y, int z) {
        if (hasCell(f, x, y, z)) return true;
        float[] bx = boxes[f];
        if (bx == null) return false;
        for (int i = 0; i < bx.length; i += 6)
            if (x + 0.5f > bx[i] && x + 0.5f < bx[i + 3] && y + 0.5f > bx[i + 1] && y + 0.5f < bx[i + 4] && z + 0.5f > bx[i + 2] && z + 0.5f < bx[i + 5]) return true;
        return false;
    }

    private boolean hasCell(int f, int x, int y, int z) {
        long[] d = dense[f];
        if (d != null) {
            int[] q = denseBox[f];
            x -= q[0]; y -= q[1]; z -= q[2];
            if (x < 0 || y < 0 || z < 0 || x >= q[3] || y >= q[4] || z >= q[5]) return false;
            long idx = ((long) x * q[4] + y) * q[5] + z;
            return (d[(int) (idx >> 6)] & (1L << (idx & 63))) != 0;
        }
        Long2LongOpenHashMap b = bricks[f];
        if (b == null) return false;
        long m = b.get(brickKey(x >> 2, y >> 2, z >> 2));
        return m != 0 && (m & (1L << (((x & 3) << 4) | ((y & 3) << 2) | (z & 3)))) != 0;
    }

    public interface CellVisitor { void cell(int x, int y, int z); }

    /** every cell of frame f (and of its boxes: the cells whose middles are in them) */
    public void cells(int f, CellVisitor v) {
        if (bricks[f] == null || boxes[f] != null) {
            float[] b = bounds[f];
            for (int x = (int) Math.floor(b[0]); x < b[3]; x++) for (int y = (int) Math.floor(b[1]); y < b[4]; y++) for (int z = (int) Math.floor(b[2]); z < b[5]; z++)
                if (has(f, x, y, z)) v.cell(x, y, z);
            return;
        }
        for (var en : bricks[f].long2LongEntrySet()) {
            long k = en.getLongKey(), m = en.getLongValue();
            int bx = (int) (k >>> 32) - 0x8000, by = (int) ((k >>> 16) & 0xFFFF) - 0x8000, bz = (int) (k & 0xFFFF) - 0x8000;
            for (int i = 0; i < 64; i++) if ((m & (1L << i)) != 0) v.cell(bx * 4 + (i >> 4), by * 4 + ((i >> 2) & 3), bz * 4 + (i & 3));
        }
    }

    /**
     * Where a straight line through frame f's cells is solid: the line is p(t) = a + t·d for t from 0 to len; each
     * solid stretch is added to out as {t in, t out}. (A 3D walk from cell to cell along the line.)
     */
    public void march(int f, float ax, float ay, float az, float dx, float dy, float dz, float len, FloatList out) {
        float[] bb = bounds[f];
        // clip the line to the frame's box first
        float t0 = 0f, t1 = len;
        float[] o = {ax, ay, az}, d = {dx, dy, dz};
        for (int k = 0; k < 3; k++) {
            float lo = bb[k] - 1e-3f, hi = bb[k + 3] + 1e-3f;
            if (Math.abs(d[k]) < 1e-9f) { if (o[k] < lo || o[k] > hi) return; continue; }
            float ta = (lo - o[k]) / d[k], tb = (hi - o[k]) / d[k];
            if (ta > tb) { float s = ta; ta = tb; tb = s; }
            t0 = Math.max(t0, ta); t1 = Math.min(t1, tb);
            if (t0 > t1) return;
        }
        if (bricks[f] == null) { marchBoxes(boxes[f], o, d, t0, t1, out); return; }
        int start = out.size();
        Long2LongOpenHashMap br = bricks[f];
        // walk the cells from t0 to t1
        float px = ax + dx * t0, py = ay + dy * t0, pz = az + dz * t0;
        int x = (int) Math.floor(px), y = (int) Math.floor(py), z = (int) Math.floor(pz);
        int sx = dx > 0 ? 1 : dx < 0 ? -1 : 0, sy = dy > 0 ? 1 : dy < 0 ? -1 : 0, sz = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        float tdx = sx != 0 ? Math.abs(1f / dx) : Float.MAX_VALUE, tdy = sy != 0 ? Math.abs(1f / dy) : Float.MAX_VALUE, tdz = sz != 0 ? Math.abs(1f / dz) : Float.MAX_VALUE;
        float tmx = sx > 0 ? t0 + (x + 1 - px) * tdx : sx < 0 ? t0 + (px - x) * tdx : Float.MAX_VALUE;
        float tmy = sy > 0 ? t0 + (y + 1 - py) * tdy : sy < 0 ? t0 + (py - y) * tdy : Float.MAX_VALUE;
        float tmz = sz > 0 ? t0 + (z + 1 - pz) * tdz : sz < 0 ? t0 + (pz - z) * tdz : Float.MAX_VALUE;
        float t = t0, inAt = Float.NaN;
        long lastKey = Long.MIN_VALUE, lastMask = 0;
        for (int guard = 0; guard < 4096; guard++) {
            boolean solid;
            if (dense[f] != null) solid = hasCell(f, x, y, z);
            else {
                long key = brickKey(x >> 2, y >> 2, z >> 2);
                if (key != lastKey) { lastKey = key; lastMask = br.get(key); }
                solid = lastMask != 0 && (lastMask & (1L << (((x & 3) << 4) | ((y & 3) << 2) | (z & 3)))) != 0;
            }
            if (solid && Float.isNaN(inAt)) inAt = t;
            else if (!solid && !Float.isNaN(inAt)) { out.add(inAt, t); inAt = Float.NaN; }
            float next = Math.min(tmx, Math.min(tmy, tmz));
            if (next >= t1) break;
            t = next;
            if (tmx == next) { x += sx; tmx += tdx; }
            else if (tmy == next) { y += sy; tmy += tdy; }
            else { z += sz; tmz += tdz; }
        }
        if (!Float.isNaN(inAt)) out.add(inAt, t1);
        // (a frame of cells and boxes both: a slab or a rail on a block part, say)
        if (boxes[f] != null) { marchBoxes(boxes[f], o, d, t0, t1, out); out.mergeFrom(start); }
    }

    private static void marchBoxes(float[] bx, float[] o, float[] d, float c0, float c1, FloatList out) {
        int start = out.size();
        if (bx == null) return;
        for (int i = 0; i < bx.length; i += 6) {
            float t0 = c0, t1 = c1;
            boolean miss = false;
            for (int k = 0; k < 3 && !miss; k++) {
                float lo = bx[i + k], hi = bx[i + k + 3];
                if (Math.abs(d[k]) < 1e-9f) { if (o[k] <= lo || o[k] >= hi) miss = true; continue; }
                float ta = (lo - o[k]) / d[k], tb = (hi - o[k]) / d[k];
                if (ta > tb) { float s = ta; ta = tb; tb = s; }
                t0 = Math.max(t0, ta); t1 = Math.min(t1, tb);
                if (t0 >= t1) miss = true;
            }
            if (!miss) out.add(t0, t1);
        }
        out.mergeFrom(start);
    }

    /** a small list of float pairs, reused (no garbage per query) */
    public static final class FloatList {
        float[] a = new float[32];
        int n;
        public void clear() { n = 0; }
        public int size() { return n; }
        public float get(int i) { return a[i]; }
        void add(float x, float y) {
            if (n + 2 > a.length) a = java.util.Arrays.copyOf(a, a.length * 2);
            a[n++] = x; a[n++] = y;
        }
        /** sorts and joins the overlapping pairs from start on */
        void mergeFrom(int start) {
            int m = (n - start) / 2;
            if (m < 2) return;
            float[][] p = new float[m][];
            for (int i = 0; i < m; i++) p[i] = new float[]{a[start + 2 * i], a[start + 2 * i + 1]};
            java.util.Arrays.sort(p, (u, v) -> Float.compare(u[0], v[0]));
            n = start;
            float s = p[0][0], e = p[0][1];
            for (int i = 1; i < m; i++) {
                if (p[i][0] <= e) e = Math.max(e, p[i][1]);
                else { add(s, e); s = p[i][0]; e = p[i][1]; }
            }
            add(s, e);
        }
    }

    // ------------------------------------------------------------------ building one

    static final class Frame {
        int bone; byte kind; float[] bounds; Long2LongOpenHashMap bricks; float[] boxes; int cells;
    }

    /**
     * Builds a shape. For a frame of cells: add every cell (x y z, model blocks as built) of a bone with
     * {@link #cell}; for a frame of boxes: {@link #box}. Then {@link #fillInsides()} (optional) fills what is
     * walled in by SOLID cells, so nothing can ever be inside him, and {@link #build()}.
     */
    public static final class Builder {
        final int bones;
        final byte[] boneKind;
        final IntArrayList[] cellsOf;
        final List<float[]>[] boxesOf;

        @SuppressWarnings("unchecked")
        public Builder(int bones) {
            this.bones = bones;
            boneKind = new byte[bones];
            cellsOf = new IntArrayList[bones];
            boxesOf = new List[bones];
        }

        static int pack(int x, int y, int z) { return ((x + 1024) << 21) | ((y + 1024) << 10) | (z + 512); }
        static int ux(int p) { return (p >>> 21) - 1024; }
        static int uy(int p) { return ((p >>> 10) & 0x7FF) - 1024; }
        static int uz(int p) { return (p & 0x3FF) - 512; }

        /** one solid cell of a bone (model blocks; x and y within ±1000, z within ±500) */
        public Builder cell(int bone, byte kind, int x, int y, int z) {
            if (kind == NONE) return this;
            if (boneKind[bone] == NONE || kind == SOLID) boneKind[bone] = kind;
            if (cellsOf[bone] == null) cellsOf[bone] = new IntArrayList();
            cellsOf[bone].add(pack(x, y, z));
            return this;
        }

        /** one box of a bone (model blocks, as built) */
        public Builder box(int bone, byte kind, float x0, float y0, float z0, float x1, float y1, float z1) {
            if (kind == NONE) return this;
            if (boneKind[bone] == NONE || kind == SOLID) boneKind[bone] = kind;
            if (boxesOf[bone] == null) boxesOf[bone] = new ArrayList<>();
            boxesOf[bone].add(new float[]{Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1)});
            return this;
        }

        /**
         * Fills in whatever the SOLID cells wall in, as built (a model file often keeps only the blocks you can see):
         * everything not reachable from outside is solid too. Each filled cell goes with the bone of the nearest
         * solid cell straight above or below it. Costs about a second for a 300-block giant: do it off the main
         * thread at start (see the adapter).
         */
        public Builder fillInsides() {
            int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
            for (int b = 0; b < bones; b++) if (cellsOf[b] != null && boneKind[b] == SOLID) for (int i = 0; i < cellsOf[b].size(); i++) {
                int p = cellsOf[b].getInt(i);
                x0 = Math.min(x0, ux(p)); y0 = Math.min(y0, uy(p)); z0 = Math.min(z0, uz(p));
                x1 = Math.max(x1, ux(p)); y1 = Math.max(y1, uy(p)); z1 = Math.max(z1, uz(p));
            }
            if (x0 > x1) return this;
            x0--; y0--; z0--; x1++; y1++; z1++;
            final int nx = x1 - x0 + 1, ny = y1 - y0 + 1, nz = z1 - z0 + 1;
            long total = (long) nx * ny * nz;
            if (total > 400_000_000L) throw new IllegalStateException("solid kit: too big to fill " + nx + "x" + ny + "x" + nz);
            BitSet solid = new BitSet((int) total);
            for (int b = 0; b < bones; b++) if (cellsOf[b] != null && boneKind[b] == SOLID) for (int i = 0; i < cellsOf[b].size(); i++) {
                int p = cellsOf[b].getInt(i);
                solid.set(((ux(p) - x0) * ny + (uy(p) - y0)) * nz + (uz(p) - z0));
            }
            // the outside: everything reachable from the edge of the box
            BitSet out = new BitSet((int) total);
            int cap = 1 << 22;
            int[] q = new int[cap];
            int head = 0, tail = 0, size = 0;
            for (int x = 0; x < nx; x++) for (int y = 0; y < ny; y++) for (int z = 0; z < nz; z++) {
                if (x != 0 && y != 0 && z != 0 && x != nx - 1 && y != ny - 1 && z != nz - 1) continue;
                int i = (x * ny + y) * nz + z;
                if (solid.get(i) || out.get(i)) continue;
                out.set(i);
                if (size == cap) { q = grow(q, head, size); cap = q.length; head = 0; tail = size; }
                q[tail] = i; tail = (tail + 1) % cap; size++;
            }
            int sx = ny * nz;
            while (size > 0) {
                int i = q[head]; head = (head + 1) % cap; size--;
                int x = i / sx, r = i - x * sx, y = r / nz, z = r - y * nz;
                for (int k = 0; k < 6; k++) {
                    int xx = x + (k == 0 ? 1 : k == 1 ? -1 : 0), yy = y + (k == 2 ? 1 : k == 3 ? -1 : 0), zz = z + (k == 4 ? 1 : k == 5 ? -1 : 0);
                    if (xx < 0 || yy < 0 || zz < 0 || xx >= nx || yy >= ny || zz >= nz) continue;
                    int j = (xx * ny + yy) * nz + zz;
                    if (solid.get(j) || out.get(j)) continue;
                    out.set(j);
                    if (size == cap) { q = grow(q, head, size); cap = q.length; head = 0; tail = size; }
                    q[tail] = j; tail = (tail + 1) % cap; size++;
                }
            }
            // the owner of each solid cell, column by column
            it.unimi.dsi.fastutil.ints.Int2ShortOpenHashMap own = new it.unimi.dsi.fastutil.ints.Int2ShortOpenHashMap();
            own.defaultReturnValue((short) -1);
            for (int b = 0; b < bones; b++) if (cellsOf[b] != null && boneKind[b] == SOLID) for (int i = 0; i < cellsOf[b].size(); i++) {
                int p = cellsOf[b].getInt(i);
                own.put(((ux(p) - x0) * ny + (uy(p) - y0)) * nz + (uz(p) - z0), (short) b);
            }
            int[] below = new int[ny], belowAt = new int[ny];
            for (int x = 1; x < nx - 1; x++) for (int z = 1; z < nz - 1; z++) {
                int lastBone = -1, lastY = -1;
                for (int y = 0; y < ny; y++) {
                    int i = (x * ny + y) * nz + z;
                    if (solid.get(i)) { int o = own.get(i); if (o >= 0) lastBone = o; lastY = y; }
                    below[y] = lastBone; belowAt[y] = lastY;
                }
                int nextBone = -1, nextY = -1;
                for (int y = ny - 1; y >= 0; y--) {
                    int i = (x * ny + y) * nz + z;
                    if (solid.get(i)) { int o = own.get(i); if (o >= 0) nextBone = o; nextY = y; continue; }
                    if (out.get(i)) continue;
                    int b = below[y] >= 0 && (nextBone < 0 || y - belowAt[y] <= nextY - y) ? below[y] : nextBone;
                    if (b < 0) continue;
                    cellsOf[b].add(pack(x + x0, y + y0, z + z0));
                }
            }
            return this;
        }

        private static int[] grow(int[] q, int head, int size) {
            int[] n = new int[q.length * 2];
            for (int i = 0; i < size; i++) n[i] = q[(head + i) % q.length];
            return n;
        }

        public SolidShape build() {
            List<Frame> fs = new ArrayList<>();
            for (int b = 0; b < bones; b++) {
                if (boneKind[b] == NONE) continue;
                Frame f = new Frame();
                f.bone = b; f.kind = boneKind[b];
                float[] bb = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
                if (cellsOf[b] != null) {
                    f.bricks = new Long2LongOpenHashMap();
                    for (int i = 0; i < cellsOf[b].size(); i++) {
                        int p = cellsOf[b].getInt(i);
                        int x = ux(p), y = uy(p), z = uz(p);
                        long key = brickKey(x >> 2, y >> 2, z >> 2);
                        long bit = 1L << (((x & 3) << 4) | ((y & 3) << 2) | (z & 3));
                        long was = f.bricks.get(key);
                        if ((was & bit) == 0) { f.bricks.put(key, was | bit); f.cells++; }
                        bb[0] = Math.min(bb[0], x); bb[1] = Math.min(bb[1], y); bb[2] = Math.min(bb[2], z);
                        bb[3] = Math.max(bb[3], x + 1); bb[4] = Math.max(bb[4], y + 1); bb[5] = Math.max(bb[5], z + 1);
                    }
                    f.bricks.trim();
                }
                if (boxesOf[b] != null) {
                    List<float[]> l = boxesOf[b];
                    f.boxes = new float[l.size() * 6];
                    for (int i = 0; i < l.size(); i++) {
                        float[] x = l.get(i);
                        System.arraycopy(x, 0, f.boxes, i * 6, 6);
                        for (int k = 0; k < 3; k++) { bb[k] = Math.min(bb[k], x[k]); bb[k + 3] = Math.max(bb[k + 3], x[k + 3]); }
                    }
                    f.cells += l.size();
                }
                f.bounds = bb;
                fs.add(f);
            }
            return new SolidShape(bones, fs);
        }
    }
}
