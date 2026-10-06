package net.jj.hollowbell.rig;

import org.joml.Matrix4f;

/**
 * His arms and strands drawn so they bend smoothly. Each piece of an arm or strand (a bone) is a straight, stiff
 * length between two joints of its chain, and on its own it would bend only at the joints, opening a gap on the
 * outside of every bend and folding in on the inside. So for drawing, each piece is cut across into a few slices
 * along its length. The middle slice goes exactly with its piece; the slices toward a joint go more and more with the
 * piece on the other side of that joint (and the first slices of a strand or arm with what it hangs from: its rim
 * sector, the vase, a pod, the arm or strand it grows from). The bend is shared out over all the slices, so it reads as
 * one smooth curve with no gap, and the root stays on what it hangs from.
 *
 * The blocks are the same as the bone's (see {@link BellModel}); hits still use the bones (the slices never move more
 * than a block or two off them).
 */
public final class BellPieces {
    private static BellPieces instance;

    public static synchronized BellPieces get() {
        if (instance == null) instance = new BellPieces(BellRig.get(), BellModel.get());
        return instance;
    }

    /** slices per piece of an arm and of a strand */
    public static final int ARM_SLICES = Integer.getInteger("hollowbell.armSlices", 6);
    public static final int STRAND_SLICES = Integer.getInteger("hollowbell.strandSlices", 3);

    public final int count;
    /**
     * per slice: its bone, the bones it leans toward (-1 none; for the first piece of a chain, prevBone is -2: what
     * the chain hangs from) and how much, and its chain (-1 none)
     */
    public final int[] bone, prevBone, nextBone, chain;
    public final float[] wPrev, wNext;
    /** per bone: its first slice and how many; per bone, per block: which slice the block is in */
    public final int[] first, num;
    public final int[][] sliceOf;
    /** per slice: its box at rest (minx miny minz maxx maxy maxz), or null if empty */
    public final float[][] bounds;

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
        bone = new int[total]; prevBone = new int[total]; nextBone = new int[total]; chain = new int[total];
        wPrev = new float[total]; wNext = new float[total];
        bounds = new float[total][];
        java.util.Arrays.fill(prevBone, -1);
        java.util.Arrays.fill(nextBone, -1);
        java.util.Arrays.fill(chain, -1);
        for (int b = 0; b < nb; b++) {
            int P = num[b];
            int c = rig.chainOf[b];
            int prev = -1, next = -1;
            boolean root = false;
            if (c >= 0) {
                BellRig.Chain ch = rig.chains[c];
                int i = rig.seg[b];
                root = i == 0;
                prev = i > 0 ? ch.bones[i - 1] : ch.parentBone >= 0 ? -2 : -1;
                next = i + 1 < ch.bones.length ? ch.bones[i + 1] : -1;
            }
            for (int k = 0; k < P; k++) {
                int s = first[b] + k;
                bone[s] = b;
                chain[s] = c;
                if (P == 1) continue;
                float u = (k + 0.5f) / P;
                if (prev != -1) { prevBone[s] = prev; wPrev[s] = BellRig.wPrev(u, root); }
                if (next >= 0) { nextBone[s] = next; wNext[s] = BellRig.wNext(u); }
            }
            int n = model.count(b);
            int[] of = new int[n];
            if (P > 1) {
                BellRig.Chain ch = rig.chains[c];
                int i = rig.seg[b];
                float ax = ch.joints[i].x, ay = ch.joints[i].y, az = ch.joints[i].z;
                float dx = ch.joints[i + 1].x - ax, dy = ch.joints[i + 1].y - ay, dz = ch.joints[i + 1].z - az;
                float l2 = Math.max(1e-4f, dx * dx + dy * dy + dz * dz);
                for (int v = 0; v < n; v++) {
                    float u = ((model.x[b][v] + 0.5f - ax) * dx + (model.y[b][v] + 0.5f - ay) * dy + (model.z[b][v] + 0.5f - az) * dz) / l2;
                    of[v] = first[b] + Math.max(0, Math.min(P - 1, (int) Math.floor(u * P)));
                }
            } else java.util.Arrays.fill(of, first[b]);
            sliceOf[b] = of;
            for (int v = 0; v < n; v++) {
                float[] bb = bounds[of[v]];
                if (bb == null) bb = bounds[of[v]] = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
                int x = model.x[b][v], y = model.y[b][v], z = model.z[b][v];
                bb[0] = Math.min(bb[0], x); bb[1] = Math.min(bb[1], y); bb[2] = Math.min(bb[2], z);
                bb[3] = Math.max(bb[3], x + 1); bb[4] = Math.max(bb[4], y + 1); bb[5] = Math.max(bb[5], z + 1);
            }
        }
    }

    public Matrix4f[] newPose() { Matrix4f[] m = new Matrix4f[count]; for (int i = 0; i < count; i++) m[i] = new Matrix4f(); return m; }

    /** each slice's transform, from the bones' and what each chain hangs from (see {@link BellRig#computePose}) */
    public void pose(Matrix4f[] bones, Matrix4f[] hang, Matrix4f[] out) {
        for (int s = 0; s < count; s++) {
            Matrix4f prev = prevBone[s] == -2 ? hang[chain[s]] : prevBone[s] >= 0 ? bones[prevBone[s]] : null;
            Matrix4f next = nextBone[s] >= 0 ? bones[nextBone[s]] : null;
            // every piece on either side of a joint maps that joint to the same spot, so the mix keeps it there too
            BellRig.mix(bones[bone[s]], prev, wPrev[s], next, wNext[s], out[s]);
        }
    }

    public Matrix4f[] newHang() { Matrix4f[] m = new Matrix4f[BellRig.get().chains.length]; for (int i = 0; i < m.length; i++) m[i] = new Matrix4f(); return m; }
}
