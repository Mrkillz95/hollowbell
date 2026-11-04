package net.jj.giants.systems.remains;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * (remains) One giant's remains: how he lies (his own bones turned), what each block of his model does as he rots
 * (flesh, hard parts that stay, eyes), the bones drawn into him, and his own touches. The base gives the common
 * pieces: lights going out, rot colours, a bone's tones, legs, a skull, ribs.
 */
public abstract class Design {
    public final String kind, mod, modelPath, rigPath;

    protected Design(String kind, String mod, String modelPath, String rigPath) {
        this.kind = kind;
        this.mod = mod;
        this.modelPath = modelPath;
        this.rigPath = rigPath;
    }

    /** turns his bones as he lies (on the pose), before it is solved */
    abstract void pose(GiantModel m, Pose p);

    /** posed model space -> lying (y 0 the ground), once the pose is solved */
    abstract double[] lie(GiantModel m, Pose p);

    /** what this block of this bone does as he rots */
    abstract byte role(GiantModel m, int bone, String block);

    /** the bones (and his own touches) drawn into him */
    abstract void bones(GiantModel m, Pose p, Carcass c);

    /** his flesh freshly dead: lights out */
    String dead(String block) {
        return lightsOut(block);
    }

    /** his flesh rotting (stage 1-3), r 0-1 */
    String rotten(String block, double r, int stage) {
        if (r < 0.3) return "minecraft:brown_terracotta";
        if (r < 0.5) return "minecraft:packed_mud";
        if (r < 0.65) return "minecraft:mud";
        if (r < 0.8) return stage >= 2 ? "minecraft:rooted_dirt" : "minecraft:brown_mushroom_block";
        return "minecraft:mycelium";
    }

    /** a hard part in this stage (0 fresh .. 3, 4 the skeleton left for good); null: gone (the bone layer shows) */
    String hard(String block, int stage, double n, double fine, int x, int y, int z) {
        return stage == 0 ? lightsOut(block) : lightsOut(block);
    }

    /** a block of his model that is part of his frame (never broken out) */
    boolean frame(GiantModel m, int bone, int x, int y, int z) {
        return false;
    }

    /** a bone token ("@bone", "@bone:x"...) at a tone t 0-1 */
    String token(String token, double t) {
        String axis = token.contains(":") ? token.substring(token.indexOf(':') + 1) : "y";
        if (t < 0.5) return "minecraft:calcite";
        if (t < 0.86) return "minecraft:bone_block[axis=" + axis + "]";
        return "minecraft:white_terracotta";
    }

    /** last touches on the stages (index 0-4) */
    void finish(Carcass c, int[][] st) {}

    // ---------------------------------------------------------------- common pieces

    static String lightsOut(String block) {
        String b = Blocks.base(block);
        return switch (b) {
            case "minecraft:sea_lantern" -> "minecraft:light_gray_concrete";
            case "minecraft:glowstone", "minecraft:ochre_froglight" -> "minecraft:yellow_terracotta";
            case "minecraft:shroomlight" -> "minecraft:orange_terracotta";
            case "minecraft:verdant_froglight" -> "minecraft:lime_terracotta";
            case "minecraft:pearlescent_froglight" -> "minecraft:pink_terracotta";
            case "minecraft:magma_block" -> "minecraft:blackstone";
            case "minecraft:crying_obsidian" -> "minecraft:obsidian";
            case "minecraft:redstone_lamp" -> "minecraft:redstone_lamp[lit=false]";
            case "minecraft:jack_o_lantern" -> "minecraft:carved_pumpkin";
            case "minecraft:end_rod", "minecraft:torch", "minecraft:wall_torch", "minecraft:ladder" -> "minecraft:air";
            default -> block;
        };
    }

    static boolean light(String block) {
        String b = Blocks.base(block);
        return b.endsWith("sea_lantern") || b.endsWith("glowstone") || b.endsWith("froglight")
                || b.endsWith("shroomlight") || b.endsWith("magma_block") || b.endsWith("redstone_lamp");
    }

    static int[] bones(GiantModel m, Predicate<String> which) {
        List<Integer> o = new ArrayList<>();
        for (int b = 0; b < m.names.length; b++) if (which.test(m.names[b])) o.add(b);
        return o.stream().mapToInt(Integer::intValue).toArray();
    }

    static IntPredicate of(GiantModel m, Predicate<String> which) {
        boolean[] in = new boolean[m.names.length];
        for (int b = 0; b < m.names.length; b++) in[b] = which.test(m.names[b]);
        return b -> b >= 0 && b < in.length && in[b];
    }

    /** a bone's pivot, where it lies now */
    static double[] piv(GiantModel m, Pose p, String bone) {
        int b = m.bone(bone);
        return b < 0 ? new double[3] : p.at(b, m.pivot[b]);
    }

    /** a point of a bone (model space at rest) where it lies now */
    static double[] at(GiantModel m, Pose p, String bone, double x, double y, double z) {
        int b = m.bone(bone);
        return b < 0 ? new double[] {x, y, z} : p.at(b, x, y, z);
    }

    /** the far end of a bone: its block furthest from its pivot (model space at rest) */
    static float[] tip(GiantModel m, int b) {
        float[] pv = m.pivot[b];
        float[] best = pv;
        double bd = -1;
        for (int i = 0; i < m.x[b].length; i++) {
            double dx = m.x[b][i] + 0.5 - pv[0], dy = m.y[b][i] + 0.5 - pv[1], dz = m.z[b][i] + 0.5 - pv[2];
            double d = dx * dx + dy * dy + dz * dz;
            if (d > bd) {
                bd = d;
                best = new float[] {m.x[b][i] + 0.5f, m.y[b][i] + 0.5f, m.z[b][i] + 0.5f};
            }
        }
        return best;
    }

    /**
     * The lie: turned by this map (about 0), then moved down so the lowest block of these bones sits at the ground
     * (less sink, into it).
     */
    static double[] settle(GiantModel m, Pose p, double[] turn, IntPredicate support, double sink) {
        double low = Double.MAX_VALUE;
        for (int b = 0; b < m.names.length; b++) {
            if (!support.test(b)) continue;
            int step = Math.max(1, m.x[b].length / 4000);
            for (int i = 0; i < m.x[b].length; i += step) {
                double[] v = p.voxel(b, m.x[b][i] + 0.5, m.y[b][i] + 0.5, m.z[b][i] + 0.5);
                double[] q = Pose.apply(turn, v[0], v[1], v[2]);
                low = Math.min(low, q[1]);
            }
        }
        if (low == Double.MAX_VALUE) low = 0;
        double[] o = turn.clone();
        o[10] -= low + sink;
        return o;
    }

    /**
     * A leg's bones: a shaft between each joint (thick to thin), a knob at each joint, and the last bone's own
     * blocks (a hoof, a claw) left as they are. Joints are posed points.
     */
    static void limb(Carcass c, double[][] joints, double[] radii, String mat) {
        for (int i = 1; i < joints.length; i++) {
            double r0 = radii[Math.min(radii.length - 1, i - 1)], r1 = radii[Math.min(radii.length - 1, i)];
            c.line(joints[i - 1], joints[i], r0 * 0.8, r1 * 0.8, mat.contains("{") ? mat : mat);
            c.ball(joints[i - 1], r0 * 1.15, "@bone");
        }
        c.ball(joints[joints.length - 1], radii[radii.length - 1], "@bone");
    }

    /**
     * His back line from his own blocks: at stations along z (model space at rest), the top of these bones near the
     * middle (|x| under half), less inset. Points at rest, from z0 to z1.
     */
    static double[][] backLine(GiantModel m, int[] bones, double z0, double z1, int n, double half, double inset) {
        double[] top = new double[n];
        java.util.Arrays.fill(top, -1e9);
        for (int b : bones)
            for (int i = 0; i < m.x[b].length; i++) {
                if (Math.abs(m.x[b][i] + 0.5) > half) continue;
                double f = (m.z[b][i] + 0.5 - z0) / (z1 - z0);
                int k = (int) Math.round(f * (n - 1));
                if (k < 0 || k >= n) continue;
                top[k] = Math.max(top[k], m.y[b][i] + 1);
            }
        List<double[]> o = new ArrayList<>();
        for (int k = 0; k < n; k++)
            if (top[k] > -1e8) o.add(new double[] {0, top[k] - inset, z0 + (z1 - z0) * k / (n - 1.0)});
        return o.toArray(new double[0][]);
    }

    /** a point along a line */
    static double[] lerp(double[] a, double[] b, double f) {
        return Carcass.lerp(a, b, f);
    }

    /** turn about the y axis by deg, about 0 (for a giant who faces -z: 180) */
    static double[] yaw(double deg) {
        return Pose.rot(0, 1, 0, deg);
    }

    /** a turn about a point */
    static double[] turnAbout(double ax, double ay, double az, double deg, double px, double py, double pz) {
        return Pose.about(Pose.rot(ax, ay, az, deg), px, py, pz);
    }
}
