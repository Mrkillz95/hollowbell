package net.jj.hollowbell.entity;

import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.rig.BellModel;
import net.jj.hollowbell.rig.BellRig;
import net.jj.hollowbell.solid.GiantHull;
import net.jj.hollowbell.solid.SolidShape;

/**
 * What of Hollowbell is solid, for the solid kit (net.jj.hollowbell.solid): his bell (the copper ribs and the glass
 * between them: glass is a solid block, so you can stand on the dome), the rim, the crown, the four glowing balls and
 * the vase inside the dome, and his eight arms. They're filled in inside, since the model only keeps the blocks that
 * show a face; the dome itself is open underneath, so its inside stays hollow (things he holds hang in there).
 *
 * The strands, the pods and the egg clumps are not solid. They're thin and swing about, they hang right down to the
 * ground where you walk in among them to hit the pods, and a strand you touch already stings: if they pushed you
 * about you couldn't get at the pods, and if they were walls you'd be caught in the bundle. You walk through them.
 */
public final class BellSolid {
    private BellSolid() {}

    private static volatile SolidShape shape;

    public static SolidShape shape() {
        SolidShape s = shape;
        if (s != null) return s;
        synchronized (BellSolid.class) {
            if (shape == null) shape = build(BellRig.get(), BellModel.get());
            return shape;
        }
    }

    public static boolean ready() {
        return shape != null;
    }

    /** what kind of solid a bone of his is */
    public static byte kindOf(BellRig.Kind k) {
        return switch (k) {
            case BELL, RIM, CROWN, SPOT, ARM -> SolidShape.SOLID;
            case STRAND, POD, EGG -> SolidShape.NONE;
        };
    }

    public static SolidShape build(BellRig rig, BellModel m) {
        long t0 = System.currentTimeMillis();
        int nb = rig.boneCount();
        SolidShape.Builder b = new SolidShape.Builder(nb);
        boolean[] body = new boolean[nb];
        boolean[][] arm = new boolean[rig.arms.length][nb];
        for (int bone = 0; bone < nb; bone++) {
            byte k = kindOf(rig.kind[bone]);
            if (k == SolidShape.NONE) continue;
            for (int i = 0; i < m.count(bone); i++) b.cell(bone, k, m.x[bone][i], m.y[bone][i], m.z[bone][i]);
            if (rig.kind[bone] == BellRig.Kind.ARM) arm[rig.part[bone]][bone] = true;
            else body[bone] = true;
        }
        // (the bell, rim, crown and what's inside the dome filled as one; each arm on its own, as it swings apart)
        b.fillInsides(body);
        for (boolean[] a : arm) b.fillInsides(a);
        SolidShape s = b.build();
        if (HollowbellMod.IN_TESTS) {
            // (how much the filling in added, part by part: the model keeps only the blocks that show)
            java.util.Map<BellRig.Kind, int[]> n = new java.util.EnumMap<>(BellRig.Kind.class);
            for (int f = 0; f < s.frames(); f++) {
                int[] c = n.computeIfAbsent(rig.kind[s.bone[f]], k -> new int[2]);
                int[] cc = {0};
                s.cells(f, (x, y, z) -> cc[0]++);
                c[0] += cc[0];
                c[1] += m.count(s.bone[f]);
            }
            for (var en : n.entrySet()) HollowbellMod.LOG.info("Hollowbell solid {}: {} cells from {} blocks", en.getKey(), en.getValue()[0], en.getValue()[1]);
        }
        HollowbellMod.LOG.info("Hollowbell: solid shape ready ({} frames, {} cells, {} ms)", s.frames(), s.cells,
                System.currentTimeMillis() - t0);
        return s;
    }

    private static volatile boolean[] hullUse;
    private static volatile double[] hullKinds;

    /** his hull for the other giants: the bell, rim and crown as his body, the arms as limbs */
    public static boolean[] hullUse(SolidShape sh) {
        if (hullUse == null) hullParts(sh);
        return hullUse;
    }

    public static double[] hullKinds(SolidShape sh) {
        if (hullKinds == null) hullParts(sh);
        return hullKinds;
    }

    private static synchronized void hullParts(SolidShape sh) {
        if (hullUse != null && hullKinds != null) return;
        BellRig rig = BellRig.get();
        boolean[] use = new boolean[sh.frames()];
        double[] kind = new double[sh.frames()];
        for (int f = 0; f < sh.frames(); f++) {
            if (sh.kind[f] != SolidShape.SOLID) continue;
            switch (rig.kind[sh.bone[f]]) {
                case BELL, RIM, CROWN -> { use[f] = true; kind[f] = GiantHull.BODY; }
                case ARM -> { use[f] = true; kind[f] = GiantHull.LIMB; }
                default -> {}
            }
        }
        hullKinds = kind;
        hullUse = use;
    }

    /** at start, off the main thread, so the first one seen doesn't stall the server */
    public static void preload() {
        Thread t = new Thread(() -> {
            try {
                SolidShape sh = shape();
                GiantHull.balls(sh, hullUse(sh));
            } catch (RuntimeException e) {
                HollowbellMod.LOG.error("Hollowbell solid shape failed", e);
            }
        }, "Hollowbell solid shape");
        t.setDaemon(true);
        t.start();
    }
}
