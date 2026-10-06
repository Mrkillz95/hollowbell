package net.jj.hollowbell.rig;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * His arms and strands drawn so they bend smoothly. Each piece of an arm or strand (a bone) is a straight, stiff
 * length between two joints of its chain, and drawn like that it would bend only at the joints, opening a gap on the
 * outside of every bend and folding in on the inside. So for drawing, each piece is cut across into a few slices
 * along its length, and each slice is laid along one smooth curve through the chain's joints (see
 * {@link BellRig#frame}): the bend is spread over the slices, so it reads as one smooth curve with no gaps, and the
 * root comes smoothly out of what it hangs from.
 *
 * The blocks are the same as the bone's (see {@link BellModel}); the slices only say which of them go together.
 */
public final class BellPieces {
    private static BellPieces instance;

    public static synchronized BellPieces get() {
        if (instance == null) instance = new BellPieces(BellRig.get(), BellModel.get());
        return instance;
    }

    /** slices per piece of an arm and of a strand */
    public static final int ARM_SLICES = Integer.getInteger("hollowbell.armSlices", 8);
    public static final int STRAND_SLICES = Integer.getInteger("hollowbell.strandSlices", 3);

    public final int count;
    /** per slice: its bone, its chain (-1 none), and how far along its piece its middle is (0 top joint, 1 bottom) */
    public final int[] bone, chain;
    public final float[] u;
    /** per bone: its first slice and how many; per bone, per block: which slice the block is in */
    public final int[] first, num;
    public final int[][] sliceOf;
    /** per slice: its box at rest (minx miny minz maxx maxy maxz), or null if empty */
    public final float[][] bounds;
    /** per bone cut into slices: which slice each of its blocks is in, by place (see {@link BellModel#key}) */
    private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap[] where;

    private BellPieces(BellRig rig, BellModel model) {
        int nb = rig.boneCount();
        first = new int[nb];
        num = new int[nb];
        sliceOf = new int[nb][];
        int total = 0;
        for (int b = 0; b < nb; b++) {
            first[b] = total;
            num[b] = rig.chainOf[b] < 0 ? 1 : rig.kind[b] == BellRig.Kind.ARM ? ARM_SLICES : STRAND_SLICES;
            total += num[b];
        }
        count = total;
        bone = new int[total];
        chain = new int[total];
        u = new float[total];
        bounds = new float[total][];
        where = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap[nb];
        for (int b = 0; b < nb; b++) {
            int P = num[b];
            for (int k = 0; k < P; k++) {
                int s = first[b] + k;
                bone[s] = b;
                chain[s] = rig.chainOf[b];
                u[s] = (k + 0.5f) / P;
            }
            int n = model.count(b);
            int[] of = new int[n];
            if (P > 1) {
                BellRig.Chain ch = rig.chains[rig.chainOf[b]];
                int i = rig.seg[b];
                float ax = ch.joints[i].x, ay = ch.joints[i].y, az = ch.joints[i].z;
                float dx = ch.joints[i + 1].x - ax, dy = ch.joints[i + 1].y - ay, dz = ch.joints[i + 1].z - az;
                float l2 = Math.max(1e-4f, dx * dx + dy * dy + dz * dz);
                for (int v = 0; v < n; v++) {
                    float t = ((model.x[b][v] + 0.5f - ax) * dx + (model.y[b][v] + 0.5f - ay) * dy + (model.z[b][v] + 0.5f - az) * dz) / l2;
                    of[v] = first[b] + Math.max(0, Math.min(P - 1, (int) Math.floor(t * P)));
                }
            } else java.util.Arrays.fill(of, first[b]);
            sliceOf[b] = of;
            if (P > 1) {
                var w = where[b] = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap(n);
                for (int v = 0; v < n; v++) w.put(BellModel.key(model.x[b][v], model.y[b][v], model.z[b][v]), of[v]);
            }
            for (int v = 0; v < n; v++) {
                float[] bb = bounds[of[v]];
                if (bb == null) bb = bounds[of[v]] = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
                int x = model.x[b][v], y = model.y[b][v], z = model.z[b][v];
                bb[0] = Math.min(bb[0], x); bb[1] = Math.min(bb[1], y); bb[2] = Math.min(bb[2], z);
                bb[3] = Math.max(bb[3], x + 1); bb[4] = Math.max(bb[4], y + 1); bb[5] = Math.max(bb[5], z + 1);
            }
        }
    }

    /** is the block of bone b at (x, y, z) in slice s (a bone not cut into slices is all one) */
    public boolean in(int s, int b, int x, int y, int z) {
        return where[b] == null || where[b].getOrDefault(BellModel.key(x, y, z), -1) == s;
    }

    public Matrix4f[] newPose() { Matrix4f[] m = new Matrix4f[count]; for (int i = 0; i < count; i++) m[i] = new Matrix4f(); return m; }

    public Matrix4f[] newHang() { Matrix4f[] m = new Matrix4f[BellRig.get().chains.length]; for (int i = 0; i < m.length; i++) m[i] = new Matrix4f(); return m; }

    /** each slice's transform, from the bones' and what each chain hangs from (both from {@link BellRig#computePose}) */
    public void pose(BellState st, Matrix4f[] bones, Matrix4f[] hang, Matrix4f[] out) {
        BellRig rig = BellRig.get();
        Quaternionf brot = rig.bodyRotation(st, new Quaternionf());
        for (int s = 0; s < count; s++) {
            int b = bone[s];
            if (num[b] == 1) { out[s].set(bones[b]); continue; }
            int c = chain[s];
            rig.frame(st, b, u[s], rig.seg[b] == 0 && rig.chains[c].parentBone >= 0 ? hang[c] : null, brot, out[s]);
        }
    }
}
