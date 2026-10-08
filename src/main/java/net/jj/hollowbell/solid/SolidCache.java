package net.jj.hollowbell.solid;

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
    final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<Solid.Carried> riding = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
    final java.util.HashMap<Integer, Integer> stuck = new java.util.HashMap<>();

    /** which frames' boxes reach over each patch of the world (GRID blocks a side), worked out when first asked each pose */
    final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<int[]> grid = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    long gridStamp = Long.MIN_VALUE, builds;
    static final double GRID = 8.0;

    /** the frames whose boxes reach over the world column x z (about his own spot), or an empty list */
    int[] framesAt(double lx, double lz) {
        if (gridStamp != builds) buildGrid();
        int[] l = grid.get(key(Math.floorDiv((long) Math.floor(lx), (long) GRID), Math.floorDiv((long) Math.floor(lz), (long) GRID)));
        return l == null ? NONE : l;
    }
    private static final int[] NONE = new int[0];
    private static long key(long gx, long gz) { return (gx << 32) ^ (gz & 0xffffffffL); }

    private void buildGrid() {
        grid.clear();
        it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<it.unimi.dsi.fastutil.ints.IntArrayList> g = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        for (int f = 0; f < box.length; f++) {
            if (!on[f]) continue;
            float[] b = box[f];
            long x0 = Math.floorDiv((long) Math.floor(b[0]), (long) GRID), x1 = Math.floorDiv((long) Math.floor(b[3]), (long) GRID);
            long z0 = Math.floorDiv((long) Math.floor(b[2]), (long) GRID), z1 = Math.floorDiv((long) Math.floor(b[5]), (long) GRID);
            if ((x1 - x0 + 1) * (z1 - z0 + 1) > 40000) continue;
            for (long x = x0; x <= x1; x++) for (long z = z0; z <= z1; z++) g.computeIfAbsent(key(x, z), q -> new it.unimi.dsi.fastutil.ints.IntArrayList()).add(f);
        }
        for (var en : g.long2ObjectEntrySet()) grid.put(en.getLongKey(), en.getValue().toIntArray());
        gridStamp = builds;
    }

    /** frame f's box in the world now (for the tests) */
    public float[] boxOf(int f) { float[] b = box[f]; return new float[]{(float) (b[0] + ox), (float) (b[1] + oy), (float) (b[2] + oz), (float) (b[3] + ox), (float) (b[4] + oy), (float) (b[5] + oz)}; }

    /** the adapter calls this whenever his pose is worked out again */
    public void touch() { poseVersion++; }

    void ensure(int n) {
        if (toWorld != null && toWorld.length == n) return;
        toWorld = new Matrix4f[n]; toRest = new Matrix4f[n]; prevWorld = new Matrix4f[n]; box = new float[n][6]; on = new boolean[n];
        for (int i = 0; i < n; i++) { toWorld[i] = new Matrix4f(); toRest[i] = new Matrix4f(); prevWorld[i] = new Matrix4f(); }
        havePrev = false;
    }

    /** world box of a rest box through m (about the origin) */
    static void worldBox(Matrix4f m, float[] b, float[] out) {
        Vector3f v = new Vector3f();
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, z0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
        for (int c = 0; c < 8; c++) {
            m.transformPosition((c & 1) == 0 ? b[0] : b[3], (c & 2) == 0 ? b[1] : b[4], (c & 4) == 0 ? b[2] : b[5], v);
            x0 = Math.min(x0, v.x); y0 = Math.min(y0, v.y); z0 = Math.min(z0, v.z);
            x1 = Math.max(x1, v.x); y1 = Math.max(y1, v.y); z1 = Math.max(z1, v.z);
        }
        out[0] = x0; out[1] = y0; out[2] = z0; out[3] = x1; out[4] = y1; out[5] = z1;
    }
}
