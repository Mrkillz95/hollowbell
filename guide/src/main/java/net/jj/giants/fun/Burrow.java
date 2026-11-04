package net.jj.giants.fun;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Furrowmaw's den: a deep burrow pit cut in terraces, with a ramp spiralling down round it so he (or anyone) can
 * walk in and out. Its banded walls are ribbed like his own rings (dirt, mud, his orange and copper), the bottom is
 * ploughed in furrows, tunnel mouths gape from the walls ringed with dripstone teeth (some half fallen in, rubble
 * spilling out), the big burrow he lives in leads off the bottom, his five hook mandibles curl over the rim, and the
 * ground round it is ploughed in furrows running out from the pit.
 */
final class Burrow extends Plan {
    private final int deep, steps, out;
    private final double inner, tw, spin;

    Burrow(int r, double h) {
        super(r, h);
        deep = Mth.clamp((int) Math.round(r * 0.3), 5, 36);
        steps = Mth.clamp((int) Math.round(deep / (3 + 1.5 * u)), 2, 10);
        inner = r * 0.42;
        tw = (r - inner) / steps;
        spin = rnd(r, 1, 70) * Math.PI * 2;
        out = sc(8, 4);
    }

    @Override
    public int reach() {
        return r + out + 1;
    }

    private int level(int k) {
        if (k < 0) return 0;
        return -Math.min(deep, (int) Math.round((k + 1) * (double) deep / steps));
    }

    /** the height of the ground in the pit: terraces, the spiral ramp down them, ploughed ridges in the bottom */
    private int height(int dx, int dz) {
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > r) return d <= r + out && furrow(dx, dz, d) ? 1 : 0;
        if (d <= inner) return -deep + (ridge(d) ? 1 : 0);
        int k = Mth.clamp((int) ((r - d) / tw), 0, steps - 1);
        double t = ramp(dx, dz, d, k);
        int y = level(k);
        if (t < 1) y = (int) Math.round(Mth.lerp(t, level(k - 1), level(k)));
        return y;
    }

    /** how far along the ramp down terrace k a spot is (1 or more: not on it) */
    private double ramp(int dx, int dz, double d, int k) {
        double a = Math.atan2(dz, dx);
        double start = spin + k * 2.4;
        double mid = r - (k + 0.5) * tw;
        double span = Math.max(8, 10 * u + (level(k - 1) - level(k)) * 3) / mid;
        return Mth.positiveModulo(a - start, Math.PI * 2) / span;
    }

    private boolean ridge(double d) {
        return Math.floorMod((int) Math.floor(d / Math.max(2, 2.5 * u)), 2) == 0 && d > 2.5 * u;
    }

    private boolean furrow(int dx, int dz, double d) {
        double a = Math.atan2(dz, dx);
        int lines = Mth.clamp((int) Math.round(2 * Math.PI * r / Math.max(3, 4 * u)), 12, 180);
        return Math.cos(a * lines) > 0.55 && rnd(dx, dz, 71) < 0.9 - (d - r) / out * 0.6;
    }

    @Override
    int top(int dx, int dz) {
        return height(dx, dz);
    }

    /** the banded wall: his rings in the earth */
    private BlockState band(int dx, int y, int dz) {
        int bh = Math.max(1, (int) Math.round(u * 0.9));
        int k = Math.floorMod(Math.floorDiv(y + (int) (noise(dx, dz, 9, 72) * 2), bh), 7);
        double j = rnd(dx, y, dz, 73);
        return switch (k) {
            case 0 -> j < 0.7 ? b(Blocks.COARSE_DIRT) : b(Blocks.ROOTED_DIRT);
            case 1 -> j < 0.75 ? b(Blocks.PACKED_MUD) : b(Blocks.MUD_BRICKS);
            case 2 -> j < 0.8 ? b(Blocks.ORANGE_TERRACOTTA) : b(Blocks.TERRACOTTA);
            case 3 -> j < 0.7 ? b(Blocks.ROOTED_DIRT) : b(Blocks.DIRT);
            case 4 -> j < 0.6 ? b(Blocks.WAXED_EXPOSED_COPPER) : j < 0.85 ? b(Blocks.WAXED_WEATHERED_COPPER)
                    : b(Blocks.BROWN_TERRACOTTA);
            case 5 -> j < 0.7 ? b(Blocks.MUD) : b(Blocks.PACKED_MUD);
            default -> j < 0.6 ? b(Blocks.BROWN_TERRACOTTA) : b(Blocks.COARSE_DIRT);
        };
    }

    @Override
    void ground(int dx, int dz, double d, int f, Builds.Col o) {
        if (d > r + out) return;
        int y = height(dx, dz);
        o.support = b(Blocks.DIRT);
        if (d > r) {
            double k = rnd(dx, dz, 74);
            if (y > 0) {
                o.set(f + 1, k < 0.6 ? b(Blocks.ROOTED_DIRT) : b(Blocks.COARSE_DIRT));
                o.set(f, b(Blocks.DIRT));
                o.clearAbove(f + 2);
            } else {
                o.set(f, k < 0.5 ? b(Blocks.COARSE_DIRT) : k < 0.8 ? b(Blocks.DIRT) : b(Blocks.PODZOL));
                o.clearAbove(f + 1);
            }
            return;
        }
        // the face down to the next terrace shows the bands
        int lo = Math.max(-deep - 1, y - (int) Math.ceil((double) deep / steps) - 2);
        if (d <= inner) lo = y - 2;
        for (int yy = lo; yy < y; yy++) o.set(f + yy, band(dx, yy, dz));
        BlockState s;
        double k = rnd(dx, dz, 75);
        if (d <= inner) s = ridge(d) ? (k < 0.7 ? b(Blocks.ROOTED_DIRT) : b(Blocks.COARSE_DIRT))
                : k < 0.45 ? b(Blocks.MUD) : k < 0.8 ? b(Blocks.PACKED_MUD) : b(Blocks.COARSE_DIRT);
        else {
            // each terrace its own earth, the ramp paved in mud brick
            int step = Mth.clamp((int) ((r - d) / tw), 0, steps - 1);
            if (ramp(dx, dz, d, step) < 1) s = k < 0.75 ? b(Blocks.MUD_BRICKS) : b(Blocks.PACKED_MUD);
            else s = switch (step % 3) {
                case 0 -> k < 0.55 ? b(Blocks.COARSE_DIRT) : k < 0.85 ? b(Blocks.ROOTED_DIRT) : b(Blocks.TUFF);
                case 1 -> k < 0.6 ? b(Blocks.PACKED_MUD) : k < 0.85 ? b(Blocks.COARSE_DIRT) : b(Blocks.MUD);
                default -> k < 0.5 ? b(Blocks.ROOTED_DIRT) : k < 0.8 ? b(Blocks.DIRT) : b(Blocks.COARSE_DIRT);
            };
        }
        o.set(f + y, s);
        o.clearAbove(f + y + 1);
    }

    @Override
    void plan() {
        // the big burrow off the bottom, then smaller tunnel mouths in the walls
        double a0 = spin + Math.PI;
        int big = Mth.clamp(sc(3.2, 2), 2, Math.max(2, deep - 3));
        tunnel(a0, inner - 1, -deep + 1, big, Math.min((int) (reach() - inner) - big - 2, sc(14, 6)), 0.15, 0.55, 76);
        int n = Mth.clamp((int) Math.round(1.6 * u), 1, 8);
        for (int i = 0; i < n; i++) {
            int k = Mth.clamp((int) (rnd(i, 1, 77) * steps), 0, steps - 1);
            if (k == steps - 1 && steps > 1) k--;
            double a = a0 + Math.PI * 2 * (i + 1) / (n + 1) + (rnd(i, 2, 77) - 0.5) * 0.4;
            int tr = Mth.clamp(sc(1.8, 1), 1, Math.max(1, (level(k - 1) - level(k) - 1) / 2));
            double d = r - k * tw - 0.5;
            tunnel(a, d, level(k) + 1, tr, sc(6, 3), 0.45 + rnd(i, 3, 77) * 0.4, 0.3, 78 + i);
        }
        // teeth: dripstone standing up along the bottom edge and on the terraces
        int teeth = Mth.clamp((int) Math.round(10 * u), 6, 90);
        for (int i = 0; i < teeth; i++) {
            double a = rnd(i, 4, 79) * Math.PI * 2;
            double d = rnd(i, 5, 79) < 0.5 ? inner + 0.5 : r - (int) (rnd(i, 6, 79) * steps) * tw - tw + 0.7;
            int x = (int) Math.round(Math.cos(a) * d), z = (int) Math.round(Math.sin(a) * d);
            int y = top(x, z);
            if (get(x, y + 1, z) != null) continue;
            int len = 1 + (int) (rnd(i, 7, 79) * sc(2.5, 1));
            put(x, y, z, b(Blocks.DRIPSTONE_BLOCK));
            dripstone(x, y + 1, z, len, false);
        }
        // his five hook mandibles curling over the rim
        for (var s : ring(5, r + 1.5, 0.25, 80)) hook(s[2]);
        // spoil heaps of dug earth outside
        int heaps = Mth.clamp((int) Math.round(2 * u), 2, 10);
        for (var s : ring(heaps, r + out * 0.55, 0.8, 81)) {
            double hr = Math.max(1.5, 2.2 * u);
            ball(s[0], 0, s[1], hr, hr * 0.6, hr * 0.8, (x, y, z) -> y < 1 ? null
                    : pick(rnd(x, y, z, 82), b(Blocks.COARSE_DIRT), b(Blocks.TUFF), b(Blocks.ROOTED_DIRT),
                            b(Blocks.DIRT), b(Blocks.PACKED_MUD)));
        }
        // a few bones of what he ate
        for (int i = 0; i < Mth.clamp((int) (2 * u), 1, 12); i++) {
            double a = rnd(i, 8, 83) * Math.PI * 2, d = rnd(i, 9, 83) * inner * 0.8;
            int x = (int) Math.round(Math.cos(a) * d), z = (int) Math.round(Math.sin(a) * d);
            boolean ax = rnd(i, 10, 83) < 0.5;
            for (int k = 0; k < sc(2, 1) + 1; k++)
                put(ax ? x + k : x, top(ax ? x + k : x, ax ? z : z + k) + 1, ax ? z : z + k,
                        log(Blocks.BONE_BLOCK, ax ? Direction.Axis.X : Direction.Axis.Z));
        }
    }

    /**
     * a tunnel from the pit's wall at d out at angle a: dark inside, lined with mud and roots, teeth round its mouth,
     * part fallen in (rubble spilling out of it) by how much it caved
     */
    private void tunnel(double a, double d0, int y0, int tr, int len, double caved, double drop, int salt) {
        double ax = Math.cos(a), az = Math.sin(a);
        double cy0 = y0 + tr - 0.5;
        // lining first, the hollow over it
        for (int i = -1; i <= len; i++) {
            double cx = ax * (d0 + i), cz = az * (d0 + i);
            double cy = cy0 - Math.max(0, i) * drop;
            for (int x = (int) Math.floor(cx - tr - 2); x <= Math.ceil(cx + tr + 2); x++)
                for (int z = (int) Math.floor(cz - tr - 2); z <= Math.ceil(cz + tr + 2); z++)
                    for (int y = (int) Math.floor(cy - tr - 2); y <= Math.ceil(cy + tr + 2); y++) {
                        double along = (x - cx) * ax + (z - cz) * az;
                        if (Math.abs(along) > 0.6) continue;
                        double side = (x - cx) * -az + (z - cz) * ax;
                        double e = Math.hypot(side, (y - cy) * 1.1);
                        if (e <= tr + 1.2 && e > tr - 0.3 && y < top(x, z))
                            soft(x, y, z, rnd(x, y, z, salt) < 0.45 ? b(Blocks.ROOTED_DIRT)
                                    : rnd(x, y, z, salt + 1) < 0.5 ? b(Blocks.MUD) : b(Blocks.PACKED_MUD));
                    }
        }
        int fall = (int) Math.round(len * (1 - caved));
        for (int i = -1; i <= len; i++) {
            double cx = ax * (d0 + i), cz = az * (d0 + i);
            double cy = cy0 - Math.max(0, i) * drop;
            for (int x = (int) Math.floor(cx - tr - 1); x <= Math.ceil(cx + tr + 1); x++)
                for (int z = (int) Math.floor(cz - tr - 1); z <= Math.ceil(cz + tr + 1); z++)
                    for (int y = (int) Math.floor(cy - tr - 1); y <= Math.ceil(cy + tr + 1); y++) {
                        double along = (x - cx) * ax + (z - cz) * az;
                        if (Math.abs(along) > 0.6) continue;
                        double side = (x - cx) * -az + (z - cz) * ax;
                        double e = Math.hypot(side, (y - cy) * 1.1);
                        if (e > tr - 0.3 || y >= top(x, z)) continue;
                        boolean floor = y < cy - tr + 0.9;
                        if (i >= fall) put(x, y, z, pick(rnd(x, y, z, salt + 2), b(Blocks.TUFF), b(Blocks.COARSE_DIRT),
                                b(Blocks.COBBLESTONE), b(Blocks.ROOTED_DIRT)));
                        else put(x, y, z, floor ? (rnd(x, y, z, salt + 3) < 0.5 ? b(Blocks.COARSE_DIRT)
                                : b(Blocks.MUD)) : b(Blocks.AIR));
                    }
        }
        // the mouth's teeth (hanging from the top, standing on the floor) and hanging roots further in
        double px = -az, pz = ax;
        int teeth = Math.max(2, tr + 1);
        for (int k = 0; k < teeth; k++) {
            double s = (k - (teeth - 1) / 2.0) / Math.max(1, (teeth - 1) / 2.0) * (tr - 0.6);
            int x = (int) Math.round(ax * (d0 + 0.5) + px * s), z = (int) Math.round(az * (d0 + 0.5) + pz * s);
            double cy = cy0;
            int topY = (int) Math.floor(cy + Math.sqrt(Math.max(0, tr * tr - s * s)) - 0.4);
            int botY = (int) Math.ceil(cy - Math.sqrt(Math.max(0, tr * tr - s * s)) + 0.4);
            int long_ = Math.max(1, Math.min(tr - 1, 1 + (int) (rnd(k, salt, 84) * tr)));
            if (get(x, topY, z) != null && get(x, topY, z).isAir()) {
                put(x, topY + 1, z, b(Blocks.DRIPSTONE_BLOCK));
                dripstone(x, topY, z, long_, true);
            }
            if ((k & 1) == 0 && get(x, botY, z) != null && get(x, botY, z).isAir()) {
                put(x, botY - 1, z, b(Blocks.DRIPSTONE_BLOCK));
                dripstone(x, botY, z, Math.max(1, long_ - 1), false);
            }
        }
        for (int i = 2; i < fall; i += 2) {
            int x = (int) Math.round(ax * (d0 + i)), z = (int) Math.round(az * (d0 + i));
            int y = (int) Math.floor(cy0 - i * drop + tr - 0.8);
            if (get(x, y, z) != null && get(x, y, z).isAir() && get(x, y + 1, z) != null && !get(x, y + 1, z).isAir())
                put(x, y, z, b(Blocks.HANGING_ROOTS));
        }
        // rubble spilling out in front
        double hr = Math.max(1.2, tr * 0.9);
        double rx = ax * (d0 - hr * 0.6), rz = az * (d0 - hr * 0.6);
        ball(rx, y0 - 0.5, rz, hr, hr * caved * 1.2 + 0.4, hr, (x, y, z) -> y <= top(x, z) ? null
                : pick(rnd(x, y, z, salt + 4), b(Blocks.TUFF), b(Blocks.COARSE_DIRT), b(Blocks.COBBLESTONE),
                        b(Blocks.ROOTED_DIRT), b(Blocks.PACKED_MUD)));
    }

    /** one hook mandible: a stem up from the rim, then curling over the pit and down to a sharp tip */
    private void hook(double a) {
        double ax = Math.cos(a), az = Math.sin(a);
        int stemH = sc(7, 3);
        double rad = Math.max(2.5, 4 * u), thick = Math.max(0.6, 1.5 * u);
        double base = r + 1;
        int segs = 48;
        double lx = ax * base, ly = 1, lz = az * base;
        for (int i = 1; i <= segs; i++) {
            double t = (double) i / segs;
            double d, y;
            if (t < 0.35) {
                // the stem, leaning in a little
                double k = t / 0.35;
                d = base - k * rad * 0.15;
                y = 1 + k * stemH;
            } else {
                // the curl: most of a circle over the edge, ending pointing down into the pit
                double k = (t - 0.35) / 0.65, th = k * Math.PI * 1.05;
                d = base - rad * 0.15 - rad * (1 - Math.cos(th));
                y = 1 + stemH + rad * Math.sin(th);
            }
            double x = ax * d, z = az * d;
            double w = thick * (1 - t * 0.8);
            int band = (int) (t * 9);
            BlockState s = t > 0.88 ? b(Blocks.BONE_BLOCK) : t < 0.12 ? b(Blocks.PACKED_MUD)
                    : band % 2 == 0 ? b(Blocks.ORANGE_TERRACOTTA) : b(Blocks.WAXED_WEATHERED_CUT_COPPER);
            line(lx, ly, lz, x, y, z, w, (px, py, pz) -> s);
            lx = x;
            ly = y;
            lz = z;
        }
        dripstone((int) Math.round(lx), (int) Math.round(ly) - 1, (int) Math.round(lz), Math.max(1, sc(1.5, 1)), true);
    }
}
