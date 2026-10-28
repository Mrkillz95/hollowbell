package net.jj.hollowbell.solid;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.HashMap;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * One creature's frames for the tick (kept on the entity, one per side): for each solid frame, model as built to the
 * world and back, and its box in the world. Worked out only when something near him asks, once per pose. The pose of
 * the tick before is kept too, so how fast a part moves at a spot is known (to shove, not trap).
 */
public final class SolidCache {
    long stamp = Long.MIN_VALUE, tick = Long.MIN_VALUE;
    /** bumped by the adapter whenever the pose is worked out again */
    long poseVersion;
    /** frames are worked out about his own spot (far out in the world a float can't hold a block's place) */
    double ox, oy, oz;
    /** his facing and size the frames were worked out with */
    float yaw = Float.NaN, scale = Float.NaN;

    Matrix4f[] toWorld, toRest;
    /** per frame: its box in the world, about (ox oy oz): minx miny minz maxx maxy maxz */
    float[][] box;

    boolean[] on;
    /** last tick's frames (for how fast a spot moves) */
    double pox, poy, poz;

    Matrix4f[] prevWorld;
    boolean havePrev;
    /** server: what stands on him (by entity id), and players found well inside him (checks running) */
    final Int2ObjectOpenHashMap<Solid.Carried> riding = new Int2ObjectOpenHashMap<>();

    final HashMap<Integer, Integer> stuck = new HashMap<>();
    /** per player moved out of him: when, and how many times running (a body that keeps landing on them) */
    final HashMap<Integer, long[]> movedOut = new HashMap<>();

    /**
     * which frames' boxes reach over each patch of the world (GRID blocks a side), for each pose. First each frame's
     * patches (span: x0 x1 z0 z1, or x0 > x1 for none), and the frames over a patch found by going through them (he has
     * a few dozen frames, and a few columns are looked up a tick). Only once a pose has been asked about many times is
     * a grid made of it: patch k's frames are gridFrames[gridFrom[k]] up to gridFrames[gridFrom[k + 1]], in frame
     * order, over a flat grid round all his boxes (gx0 gz0, gw by gd), or for boxes spread too far for that, found by
     * key in sparse. Either way the same frames, in the same order. (A map of lists made new each pose was most of
     * what he cost a tick.)
     */
    int[] gridFrom = new int[1], gridFrames = new int[0];
    /** framesAt's answer: the frames over the patch, in frame order */
    int[] found = new int[0];

    long gridStamp = Long.MIN_VALUE, spanStamp = Long.MIN_VALUE, builds;
    /** for the perf lab: grids made, and patches looked up */
    public static long gridBuilds, lookups;
    static final double GRID = 8.0;
    /** the most patches a flat grid may have */
    static final int FLAT_MAX = 1 << 20;
    /** how many looks at one pose before a grid of it is made */
    static final int GRID_AFTER = 96;

    private long gx0, gz0;
    private int gw, gd, asked, total;
    private boolean flat;
    private long ax0, ax1, az0, az1;
    private final Long2IntOpenHashMap sparse = new Long2IntOpenHashMap();
    private long[] span = new long[0];
    /** where the next frame of each patch goes, while building */
    private int[] fill = new int[0];

    {
        sparse.defaultReturnValue(-1);
    }

    /** the frames whose boxes reach over the patch of the world column x z (about his own spot), into found: how many */
    int framesAt(double lx, double lz) {
        if (spanStamp != builds) spans();
        lookups++;
        long gx = Math.floorDiv((long) Math.floor(lx), (long) GRID), gz = Math.floorDiv((long) Math.floor(lz), (long) GRID);
        int n = 0;
        if (++asked > GRID_AFTER) {
            if (gridStamp != builds) buildGrid();
            int k = cellAt(gx, gz);
            if (k < 0) return 0;
            for (int q = gridFrom[k], e = gridFrom[k + 1]; q < e; q++) found[n++] = gridFrames[q];
            return n;
        }
        for (int f = 0, nf = box.length; f < nf; f++) {
            int b = f * 4;
            if (span[b] <= gx && gx <= span[b + 1] && span[b + 2] <= gz && gz <= span[b + 3]) found[n++] = f;
        }
        return n;
    }

    private int cellAt(long gx, long gz) {
        if (!flat) return sparse.get(key(gx, gz));
        if (gx < gx0 || gz < gz0 || gx >= gx0 + gw || gz >= gz0 + gd) return -1;
        return (int) ((gx - gx0) * gd + (gz - gz0));
    }

    private static long key(long gx, long gz) {
        return (gx << 32) ^ (gz & 0xffffffffL);
    }

    /** each frame's patches, for this pose */
    private void spans() {
        int n = box.length;
        if (span.length < n * 4) span = new long[n * 4];
        if (found.length < n) found = new int[n];
        ax0 = Long.MAX_VALUE;
        ax1 = Long.MIN_VALUE;
        az0 = Long.MAX_VALUE;
        az1 = Long.MIN_VALUE;
        total = 0;
        for (int f = 0; f < n; f++) {
            span[f * 4] = 1;
            span[f * 4 + 1] = 0;
            if (!on[f]) continue;
            float[] b = box[f];
            long x0 = Math.floorDiv((long) Math.floor(b[0]), (long) GRID),
                    x1 = Math.floorDiv((long) Math.floor(b[3]), (long) GRID);
            long z0 = Math.floorDiv((long) Math.floor(b[2]), (long) GRID),
                    z1 = Math.floorDiv((long) Math.floor(b[5]), (long) GRID);
            if ((x1 - x0 + 1) * (z1 - z0 + 1) > 40000) continue;
            span[f * 4] = x0;
            span[f * 4 + 1] = x1;
            span[f * 4 + 2] = z0;
            span[f * 4 + 3] = z1;
            total += (int) ((x1 - x0 + 1) * (z1 - z0 + 1));
            ax0 = Math.min(ax0, x0);
            ax1 = Math.max(ax1, x1);
            az0 = Math.min(az0, z0);
            az1 = Math.max(az1, z1);
        }
        asked = 0;
        spanStamp = builds;
    }

    private void buildGrid() {
        int n = box.length;
        sparse.clear();
        flat = true;
        gridBuilds++;
        gridStamp = builds;
        if (total == 0) {
            gw = gd = 0;
            return;
        }
        long cells = (ax1 - ax0 + 1) * (az1 - az0 + 1);
        int nc;
        if (cells <= FLAT_MAX) {
            gx0 = ax0;
            gz0 = az0;
            gw = (int) (ax1 - ax0 + 1);
            gd = (int) (az1 - az0 + 1);
            nc = gw * gd;
        } else {
            // spread too far for a flat grid: the patches anything reaches over, numbered as first met
            flat = false;
            nc = 0;
            for (int f = 0; f < n; f++)
                for (long x = span[f * 4]; x <= span[f * 4 + 1]; x++)
                    for (long z = span[f * 4 + 2]; z <= span[f * 4 + 3]; z++)
                        if (sparse.putIfAbsent(key(x, z), nc) == -1) nc++;
        }
        if (gridFrom.length < nc + 1) gridFrom = new int[nc + 1];
        java.util.Arrays.fill(gridFrom, 0, nc + 1, 0);
        if (gridFrames.length < total) gridFrames = new int[total];
        // how many in each patch, then where each patch's run starts, then the frames into their runs
        for (int f = 0; f < n; f++)
            for (long x = span[f * 4]; x <= span[f * 4 + 1]; x++)
                for (long z = span[f * 4 + 2]; z <= span[f * 4 + 3]; z++) gridFrom[cellAt(x, z) + 1]++;
        for (int k = 0; k < nc; k++) gridFrom[k + 1] += gridFrom[k];
        if (fill.length < nc) fill = new int[nc];
        System.arraycopy(gridFrom, 0, fill, 0, nc);
        for (int f = 0; f < n; f++)
            for (long x = span[f * 4]; x <= span[f * 4 + 1]; x++)
                for (long z = span[f * 4 + 2]; z <= span[f * 4 + 3]; z++) gridFrames[fill[cellAt(x, z)]++] = f;
    }

    /** frame f's box in the world now */
    public float[] boxOf(int f) {
        float[] b = box[f];
        return new float[] {
            (float) (b[0] + ox),
            (float) (b[1] + oy),
            (float) (b[2] + oz),
            (float) (b[3] + ox),
            (float) (b[4] + oy),
            (float) (b[5] + oz)
        };
    }

    /** the adapter calls this whenever his pose is worked out again */
    public void touch() {
        poseVersion++;
    }

    void ensure(int n) {
        if (toWorld != null && toWorld.length == n) return;
        toWorld = new Matrix4f[n];
        toRest = new Matrix4f[n];
        prevWorld = new Matrix4f[n];
        box = new float[n][6];
        on = new boolean[n];
        for (int i = 0; i < n; i++) {
            toWorld[i] = new Matrix4f();
            toRest[i] = new Matrix4f();
            prevWorld[i] = new Matrix4f();
        }
        havePrev = false;
    }

    /** world box of a rest box through m (about the origin) */
    static void worldBox(Matrix4f m, float[] b, float[] out) {
        Vector3f v = new Vector3f();
        float x0 = Float.MAX_VALUE,
                y0 = Float.MAX_VALUE,
                z0 = Float.MAX_VALUE,
                x1 = -Float.MAX_VALUE,
                y1 = -Float.MAX_VALUE,
                z1 = -Float.MAX_VALUE;
        for (int c = 0; c < 8; c++) {
            m.transformPosition((c & 1) == 0 ? b[0] : b[3], (c & 2) == 0 ? b[1] : b[4], (c & 4) == 0 ? b[2] : b[5], v);
            x0 = Math.min(x0, v.x);
            y0 = Math.min(y0, v.y);
            z0 = Math.min(z0, v.z);
            x1 = Math.max(x1, v.x);
            y1 = Math.max(y1, v.y);
            z1 = Math.max(z1, v.z);
        }
        out[0] = x0;
        out[1] = y0;
        out[2] = z0;
        out[3] = x1;
        out[4] = y1;
        out[5] = z1;
    }
}
