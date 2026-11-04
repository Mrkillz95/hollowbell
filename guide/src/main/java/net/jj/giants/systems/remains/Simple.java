package net.jj.giants.systems.remains;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * (remains) The plain body plan of Part 8, kept for a giant whose own model can't be read (a stand-in in the tests,
 * or a giant mod changed past what this reads): a spine, ribs arching to the ground, legs, neck and skull in bone,
 * flesh in two layers over the middle. Stages: fresh, outer flesh rotten, outer gone, inner gone (bones), and the
 * bones for the skeleton rule.
 */
public final class Simple {
    private Simple() {}

    static final int BONE = 0, INNER = 1, OUTER = 2;

    record Plan(double ribH, double ribW, int legs, double legLen, boolean ring, int heads, double neck, double skull,
            double tail, String flesh, String flesh2, String inner, String rotten) {}

    static Plan plan(String kind) {
        return switch (kind) {
            case "pitchgut" -> new Plan(0.24, 0.2, 4, 0.22, false, 1, 0.12, 0.09, 0.15, "pink_terracotta",
                    "pink_terracotta", "nether_wart_block", "mud");
            case "furrowmaw" -> new Plan(0.05, 0.05, 0, 0, true, 1, 0.0, 0.06, 0.05, "nether_bricks", "nether_bricks",
                    "nether_wart_block", "rooted_dirt");
            case "cerberus" -> new Plan(0.16, 0.13, 4, 0.25, false, 3, 0.16, 0.06, 0.25, "netherrack", "packed_ice",
                    "blackstone", "cobblestone");
            case "hollowbell" -> new Plan(0.12, 0.1, 2, 0.2, false, 1, 0.22, 0.05, 0.12, "oxidized_copper",
                    "oxidized_copper", "lime_stained_glass", "mud");
            case "lanternwillow" -> new Plan(0.14, 0.12, 0, 0, false, 1, 0.1, 0.06, 0.0, "dark_oak_leaves",
                    "dark_oak_leaves", "dark_oak_log", "mud");
            case "wreckback" -> new Plan(0.14, 0.2, 8, 0.2, false, 1, 0.02, 0.06, 0.05, "red_terracotta",
                    "red_terracotta", "dark_oak_planks", "mud");
            case "swarmforge" -> new Plan(0.18, 0.16, 4, 0.22, false, 1, 0.06, 0.07, 0.0, "weathered_copper",
                    "weathered_copper", "polished_blackstone", "oxidized_copper");
            case "gloamwick" -> new Plan(0.16, 0.12, 4, 0.3, false, 1, 0.16, 0.07, 0.04, "blackstone", "blackstone",
                    "soul_soil", "soul_sand");
            default -> new Plan(0.15, 0.12, 4, 0.2, false, 1, 0.1, 0.06, 0.1, "brown_terracotta",
                    "brown_terracotta", "nether_wart_block", "mud");
        };
    }

    private static final class Body {
        final Map<Long, String> state = new LinkedHashMap<>();
        final Map<Long, Integer> layer = new HashMap<>();
        final double fx, fz, rx, rz;

        Body(float yaw) {
            double r = Math.toRadians(yaw);
            fx = -Math.sin(r);
            fz = Math.cos(r);
            rx = Math.cos(r);
            rz = Math.sin(r);
        }

        void put(double a, double b, double y, String s, int lay) {
            if (y < 0) return;
            long k = Carcass.key(Carcass.floor(fx * a + rx * b), Carcass.floor(y), Carcass.floor(fz * a + rz * b));
            Integer was = layer.get(k);
            if (was != null && was <= lay) return;
            state.put(k, s);
            layer.put(k, lay);
        }

        void line(double a1, double b1, double y1, double a2, double b2, double y2, double thick, String s, int lay) {
            double da = a2 - a1, db = b2 - b1, dy = y2 - y1;
            double len = Math.sqrt(da * da + db * db + dy * dy);
            int steps = Math.max(1, (int) Math.ceil(len * 2));
            int r = (int) Math.floor(thick / 2);
            for (int i = 0; i <= steps; i++) {
                double f = i / (double) steps;
                for (int x = -r; x <= r; x++)
                    for (int z = -r; z <= r; z++)
                        for (int h = -r; h <= r; h++)
                            if (x * x + z * z + h * h <= r * r + 0.5)
                                put(a1 + da * f + x, b1 + db * f + z, y1 + dy * f + h, s, lay);
            }
        }

        void ball(double a, double b, double y, double r, String s, int lay) {
            int R = (int) Math.ceil(r);
            for (int x = -R; x <= R; x++)
                for (int z = -R; z <= R; z++)
                    for (int h = -R; h <= R; h++) if (x * x + z * z + h * h <= r * r) put(a + x, b + z, y + h, s, lay);
        }
    }

    /** the plain body, this long (blocks along the spine), turned to the yaw */
    public static Carcass.Out plan(String kind, double len, float yaw, boolean corpse) {
        Plan p = plan(kind);
        Body b = new Body(yaw);
        String bone = "minecraft:bone_block";
        double L = len, half = L / 2;
        double th = L >= 40 ? 2 : 1;
        double H = Math.max(2, p.ribH() * L), W = Math.max(1.5, p.ribW() * L);
        if (p.ring()) {
            double R = Math.max(1.5, Math.max(H, W));
            b.line(-half, 0, R, half, 0, R, th, bone, BONE);
            double gap = Math.max(3, L / 24);
            for (double a = -half + gap / 2; a < half - 1; a += gap)
                for (int i = 0; i < 24; i++) {
                    double t = i / 24.0 * Math.PI * 2;
                    b.put(a, Math.cos(t) * R, R + Math.sin(t) * R, bone, BONE);
                }
            if (corpse) shell(b, p, R, half, R * 1.2, R * 1.2);
            return out(b, corpse, p);
        }
        b.line(-half, 0, H, half, 0, H, th, bone, BONE);
        double gap = Math.max(2, L / 12);
        for (double a = -half * 0.6; a <= half * 0.5; a += gap) {
            int n = (int) Math.max(8, Math.PI * (H + W));
            double pb = W, py = 0;
            for (int i = 1; i <= n; i++) {
                double t = Math.PI * i / n;
                double nb = W * Math.cos(t), ny = H * Math.sin(t);
                b.line(a, pb, py, a, nb, ny, 1, bone, BONE);
                pb = nb;
                py = ny;
            }
        }
        if (p.legs() > 0) {
            double legL = Math.max(3, p.legLen() * L);
            int pairs = p.legs() / 2;
            for (int i = 0; i < pairs; i++) {
                double a = pairs == 1 ? half * 0.3 : -half * 0.55 + i * (half * 1.1 / (pairs - 1));
                for (int s : new int[] {-1, 1}) {
                    double kneeB = s * (W + legL * 0.5), footB = s * (W + legL);
                    b.line(a, s * W * 0.6, H * 0.4, a + legL * 0.1, kneeB, H * 0.5, th, bone, BONE);
                    b.line(a + legL * 0.1, kneeB, H * 0.5, a + legL * 0.3, footB, 0, 1, bone, BONE);
                }
            }
        }
        if (p.tail() > 0) {
            double tl = p.tail() * L;
            b.line(-half, 0, H, -half - tl * 0.5, 0, H * 0.4, th, bone, BONE);
            b.line(-half - tl * 0.5, 0, H * 0.4, -half - tl, 0, 0, 1, bone, BONE);
        }
        double nl = Math.max(1, p.neck() * L), sk = Math.max(1.2, p.skull() * L);
        for (int hIdx = 0; hIdx < p.heads(); hIdx++) {
            double spread = p.heads() == 1 ? 0 : (hIdx - 1) * W * 0.9;
            double na = half + nl, nb = spread, ny = H + nl * 0.4;
            b.line(half, 0, H, na, nb, ny, th, bone, BONE);
            b.ball(na + sk * 0.6, nb, ny, sk, bone, BONE);
            b.line(na + sk * 0.2, nb, ny - sk, na + sk * 2.2, nb, Math.max(0, ny - sk * 1.8), 1, bone, BONE);
        }
        if (corpse) shell(b, p, H, half, W * 1.15, H * 1.2);
        return out(b, corpse, p);
    }

    private static void shell(Body b, Plan p, double cy, double ra, double rb, double ry) {
        int A = (int) Math.ceil(ra), B = (int) Math.ceil(rb), Y = (int) Math.ceil(cy + ry);
        for (int a = -A; a <= A; a++)
            for (int s = -B; s <= B; s++)
                for (int y = 0; y <= Y; y++) {
                    double d = (a * a) / (ra * ra) + (s * s) / (rb * rb) + ((y - cy) * (y - cy)) / (ry * ry);
                    if (d > 1) continue;
                    double rad = Math.sqrt(d);
                    if (rad > 0.8) b.put(a, s, y, "minecraft:" + (s < 0 ? p.flesh2() : p.flesh()), OUTER);
                    else if (rad > 0.6) b.put(a, s, y, "minecraft:" + p.inner(), INNER);
                }
    }

    private static Carcass.Out out(Body b, boolean corpse, Plan p) {
        List<String> mats = new ArrayList<>();
        Map<String, Integer> id = new HashMap<>();
        mats.add(Carcass.AIR);
        id.put(Carcass.AIR, 0);
        int n = b.state.size();
        int[] x = new int[n], y = new int[n], z = new int[n];
        int[][] st = new int[Carcass.STAGES][n];
        String rotten = "minecraft:" + p.rotten();
        int i = 0;
        for (var e : b.state.entrySet()) {
            long k = e.getKey();
            x[i] = (int) ((k >>> 42) & ((1 << 21) - 1)) - (1 << 20);
            y[i] = (int) ((k >>> 21) & ((1 << 21) - 1)) - (1 << 20);
            z[i] = (int) (k & ((1 << 21) - 1)) - (1 << 20);
            int lay = b.layer.get(k);
            int m = id.computeIfAbsent(e.getValue(), q -> {
                mats.add(q);
                return mats.size() - 1;
            });
            int rot = id.computeIfAbsent(rotten, q -> {
                mats.add(q);
                return mats.size() - 1;
            });
            st[0][i] = m;
            st[1][i] = lay == OUTER ? rot : m;
            st[2][i] = lay == OUTER ? 0 : m;
            st[3][i] = lay == BONE ? m : 0;
            st[4][i] = lay == BONE ? m : 0;
            i++;
        }
        return new Carcass.Out(n, x, y, z, mats.toArray(new String[0]), st);
    }
}
