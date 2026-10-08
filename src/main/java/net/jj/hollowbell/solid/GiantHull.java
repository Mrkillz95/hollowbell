package net.jj.hollowbell.solid;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giant against giant: two giants never pass through each other. Each giant shows the others its rough body, its
 * "hull": a handful of upright round columns over its body and big limbs, in the world, as plain numbers, so the
 * giants of JJ's different mods read each other's with no code shared between them:
 *
 * <pre>
 *   public double[] jjHull()      on the giant entity: 6 numbers a column,
 *                                 x, z, radius, bottom y, top y, kind (0 = body, 1 = limb)
 * </pre>
 *
 * A giant without that method (an older version) is read from its own box and its part boxes. Each giant moves only
 * itself: where hulls overlap it steps out along the shortest way, its share by weight (all of it if the other can't
 * move itself out); a limb touching a limb doesn't count, a limb or body against a body does. Walking stops when the
 * hulls are about to touch, and blows aim at the nearest point of the other's hull, not its middle. See
 * scratchpad solid_spec.md, "Giant against giant".
 */
public final class GiantHull {
    private GiantHull() {}

    public static final int STRIDE = 6;
    public static final double BODY = 0, LIMB = 1;

    // ------------------------------------------------------------------ one's own, from a solid shape

    /** rest balls (x y z r, model blocks) for each frame, worked out once per shape and choice of frames */
    private static final Map<SolidShape, float[][]> BALLS = new ConcurrentHashMap<>();

    /**
     * The balls over a shape's frames: each frame's cells cut into cubes (about 22 to the shape's longest side), and a
     * ball over each well-filled cube. use[f] false leaves a frame out (a small or soft part).
     */
    public static float[][] balls(SolidShape sh, boolean[] use) {
        return BALLS.computeIfAbsent(sh, s -> {
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (int f = 0; f < s.frames(); f++) if (use[f]) for (int a = 0; a < 3; a++) { lo = Math.min(lo, s.bounds[f][a]); hi = Math.max(hi, s.bounds[f][a + 3]); }
            int c = Math.max(3, (int) Math.ceil((hi - lo) / 22f));
            float[][] out = new float[s.frames()][];
            for (int f = 0; f < s.frames(); f++) {
                if (!use[f]) continue;
                final int C = c;
                it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<float[]> cubes = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
                s.cells(f, (x, y, z) -> {
                    long k = (((long) Math.floorDiv(x, C) + 0x10000) << 40) | (((long) Math.floorDiv(y, C) + 0x10000) << 20) | ((long) Math.floorDiv(z, C) + 0x10000);
                    float[] a = cubes.computeIfAbsent(k, q -> new float[4]);
                    a[0] += x + 0.5f; a[1] += y + 0.5f; a[2] += z + 0.5f; a[3]++;
                });
                List<float[]> keep = new ArrayList<>();
                int least = Math.max(2, (int) (C * C * C * 0.12f));
                for (float[] a : cubes.values()) {
                    if (a[3] < least) continue;
                    float n = a[3];
                    // (a ball over the cube's filled share of it, reaching its neighbours' across the corners)
                    float r = Math.min(C * 0.87f, 0.72f * C * (float) Math.cbrt(n / (C * C * C)) + 0.3f);
                    keep.add(new float[]{a[0] / n, a[1] / n, a[2] / n, r});
                }
                float[] flat = new float[keep.size() * 4];
                for (int i = 0; i < keep.size(); i++) System.arraycopy(keep.get(i), 0, flat, i * 4, 4);
                out[f] = flat;
            }
            return out;
        });
    }

    /** a solid body's hull now: its balls carried into the world by its frames this tick. kind[f]: BODY or LIMB. */
    public static double[] fromBody(SolidBody b, float[][] balls, double[] kind) {
        SolidCache c = Solid.frames(b);
        float s = b.solidScale();
        int n = 0;
        for (int f = 0; f < balls.length; f++) if (balls[f] != null && c.on[f]) n += balls[f].length / 4;
        double[] out = new double[n * STRIDE];
        Vector3f v = new Vector3f();
        int k = 0;
        for (int f = 0; f < balls.length; f++) {
            float[] bl = balls[f];
            if (bl == null || !c.on[f]) continue;
            for (int i = 0; i < bl.length; i += 4) {
                c.toWorld[f].transformPosition(bl[i], bl[i + 1], bl[i + 2], v);
                double r = bl[i + 3] * s;
                out[k] = v.x + c.ox; out[k + 1] = v.z + c.oz; out[k + 2] = r;
                out[k + 3] = v.y + c.oy - r; out[k + 4] = v.y + c.oy + r; out[k + 5] = kind[f];
                k += STRIDE;
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ another's

    private static final Map<Class<?>, Optional<Method>> HULL = new ConcurrentHashMap<>();

    /** does this giant show its hull (and so move itself out of others, the same as this one does) */
    public static boolean shows(Entity giant) { return method(giant).isPresent(); }

    private static Optional<Method> method(Entity e) {
        return HULL.computeIfAbsent(e.getClass(), cl -> {
            try { Method m = cl.getMethod("jjHull"); return m.getReturnType() == double[].class ? Optional.of(m) : Optional.empty(); }
            catch (Exception x) { return Optional.empty(); }
        });
    }

    /**
     * Another giant's hull: its own jjHull, or, for one that doesn't show one, its box and its part boxes (each part
     * box of a giant made of them; anything tagged as a giant near it that belongs to it) as columns.
     */
    public static double[] of(Entity giant, String partTag) {
        var m = method(giant);
        if (m.isPresent()) {
            try {
                Object o = m.get().invoke(giant);
                if (o instanceof double[] d && d.length % STRIDE == 0) return d;
            } catch (Exception ignored) { }
        }
        List<AABB> boxes = new ArrayList<>();
        boxes.add(giant.getBoundingBox());
        AABB look = giant.getBoundingBox().inflate(Math.max(48, giant.getBbWidth() * 4));
        for (Entity p : giant.level().getEntities(giant, look, x -> !(x instanceof LivingEntity) && x.getTags().contains(partTag))) {
            if (ownerOf(p) == giant) boxes.add(p.getBoundingBox());
        }
        return fromBoxes(boxes);
    }

    private static final Map<Class<?>, Optional<Method>> OWNER = new ConcurrentHashMap<>();

    private static @Nullable Object ownerOf(Entity e) {
        var m = OWNER.computeIfAbsent(e.getClass(), c -> {
            try { return Optional.of(c.getMethod("owner")); } catch (Exception x) { return Optional.empty(); }
        });
        try { return m.isPresent() ? m.get().invoke(e) : null; } catch (Exception x) { return null; }
    }

    /** boxes as columns: each box cut along its longer side into squares, a column round each */
    public static double[] fromBoxes(List<AABB> boxes) {
        List<double[]> cols = new ArrayList<>();
        for (AABB b : boxes) {
            double w = b.getXsize(), d = b.getZsize();
            double side = Math.max(0.3, Math.min(w, d));
            int n = (int) Math.min(64, Math.max(1, Math.ceil(Math.max(w, d) / side)));
            boolean alongX = w >= d;
            for (int i = 0; i < n; i++) {
                double t = (i + 0.5) / n;
                double x = alongX ? b.minX + w * t : (b.minX + b.maxX) / 2, z = alongX ? (b.minZ + b.maxZ) / 2 : b.minZ + d * t;
                double r = 0.5 * Math.hypot(alongX ? w / n : w, alongX ? d : d / n);
                cols.add(new double[]{x, z, r, b.minY, b.maxY, BODY});
            }
        }
        double[] out = new double[cols.size() * STRIDE];
        for (int i = 0; i < cols.size(); i++) System.arraycopy(cols.get(i), 0, out, i * STRIDE, STRIDE);
        return out;
    }

    // ------------------------------------------------------------------ between two

    /** how much a hull weighs (its columns' volume, near enough) */
    public static double mass(double[] h) {
        double m = 0;
        for (int i = 0; i < h.length; i += STRIDE) m += h[i + 2] * h[i + 2] * Math.max(0.1, h[i + 4] - h[i + 3]);
        return m;
    }

    /** the box round a hull */
    public static AABB box(double[] h) {
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (int i = 0; i < h.length; i += STRIDE) {
            x0 = Math.min(x0, h[i] - h[i + 2]); x1 = Math.max(x1, h[i] + h[i + 2]);
            z0 = Math.min(z0, h[i + 1] - h[i + 2]); z1 = Math.max(z1, h[i + 1] + h[i + 2]);
            y0 = Math.min(y0, h[i + 3]); y1 = Math.max(y1, h[i + 4]);
        }
        return h.length == 0 ? new AABB(0, 0, 0, 0, 0, 0) : new AABB(x0, y0, z0, x1, y1, z1);
    }

    private static boolean near(double[] h, int i, AABB b, double pad) {
        return h[i] + h[i + 2] + pad > b.minX && h[i] - h[i + 2] - pad < b.maxX && h[i + 1] + h[i + 2] + pad > b.minZ && h[i + 1] - h[i + 2] - pad < b.maxZ
                && h[i + 4] > b.minY && h[i + 3] < b.maxY;
    }

    /**
     * The way out for a (the mover) from b: {dx, dz, depth}, the shortest way out of the deepest overlap of a body
     * (a limb against a limb doesn't count). depth 0: no overlap.
     */
    public static double[] pushOut(double[] a, double[] b) {
        double[] best = {0, 0, 0};
        AABB ba = box(a), bb = box(b);
        if (!ba.intersects(bb)) return best;
        for (int i = 0; i < a.length; i += STRIDE) {
            if (!near(a, i, bb, 0)) continue;
            for (int j = 0; j < b.length; j += STRIDE) {
                if (a[i + 5] == LIMB && b[j + 5] == LIMB) continue;
                if (a[i + 4] <= b[j + 3] || b[j + 4] <= a[i + 3]) continue;
                double dx = a[i] - b[j], dz = a[i + 1] - b[j + 1];
                double rr = a[i + 2] + b[j + 2];
                double d2 = dx * dx + dz * dz;
                if (d2 >= rr * rr) continue;
                double d = Math.sqrt(d2), depth = rr - d;
                if (depth <= best[2]) continue;
                if (d < 1e-4) {
                    // (right on top of each other: out the way from its middle to ours)
                    Vec3 ca = ba.getCenter(), cb = bb.getCenter();
                    dx = ca.x - cb.x; dz = ca.z - cb.z; d = Math.max(1e-4, Math.hypot(dx, dz));
                    if (d < 1e-3) { dx = 1; dz = 0; d = 1; }
                }
                best[0] = dx / d; best[1] = dz / d; best[2] = depth;
            }
        }
        return best;
    }

    /** the deepest any of a's body goes into b (limb on limb not counted): 0 when they don't overlap */
    public static double overlap(double[] a, double[] b) { return pushOut(a, b)[2]; }

    /** the least gap across between a's and b's columns at the same height (below 0: they overlap) */
    public static double gap(double[] a, double[] b) {
        double g = Double.MAX_VALUE;
        for (int i = 0; i < a.length; i += STRIDE) for (int j = 0; j < b.length; j += STRIDE) {
            if (a[i + 4] <= b[j + 3] || b[j + 4] <= a[i + 3]) continue;
            g = Math.min(g, Math.hypot(a[i] - b[j], a[i + 1] - b[j + 1]) - a[i + 2] - b[j + 2]);
        }
        return g;
    }

    /** the nearest point of a hull to a spot (on its surface; at the spot's height, kept within that column's) */
    public static @Nullable Vec3 nearest(double[] h, Vec3 from) {
        Vec3 best = null;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < h.length; i += STRIDE) {
            double dx = from.x - h[i], dz = from.z - h[i + 1], d = Math.hypot(dx, dz);
            double y = Mth.clamp(from.y, h[i + 3], h[i + 4]);
            double out = Math.max(0, d - h[i + 2]);
            double dist2 = out * out + (from.y - y) * (from.y - y);
            if (dist2 >= bd) continue;
            bd = dist2;
            double k = d < 1e-4 ? 0 : h[i + 2] / d;
            best = d <= h[i + 2] ? new Vec3(from.x, y, from.z) : new Vec3(h[i] + dx * k, y, h[i + 1] + dz * k);
        }
        return best;
    }

    /** is this spot inside the hull */
    public static boolean contains(double[] h, double x, double y, double z) {
        for (int i = 0; i < h.length; i += STRIDE)
            if (y >= h[i + 3] && y <= h[i + 4] && (x - h[i]) * (x - h[i]) + (z - h[i + 1]) * (z - h[i + 1]) < h[i + 2] * h[i + 2]) return true;
        return false;
    }
}
