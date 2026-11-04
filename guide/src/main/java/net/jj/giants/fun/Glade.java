package net.jj.giants.fun;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The Gloamwick's den: a dark glade. Podzol, moss and soul soil under a low mist (pale carpets lying in drifts); in
 * the middle a round inlay half light and half dark, for his light and darkness; round it a circle of carved standing
 * stones (mossy, cracked, a band of runes on their inner faces, some joined by lintels, one fallen), soul lanterns and
 * soul fires at their feet, candles in the grass; to the north a stepped altar lit by soul fires and candles, with
 * antlers of old bone behind it tipped with blue flames like his; and round the edge dead, twisted dark trees with
 * soul lanterns hanging from their branches.
 */
final class Glade extends Plan {
    private final double circle;

    Glade(int r, double h) {
        super(r, h);
        circle = r * 0.58;
    }

    @Override
    public int reach() {
        // room for the trees' limbs past the edge
        return r + 3 + sc(9, 4);
    }

    @Override
    int top(int dx, int dz) {
        return 0;
    }

    @Override
    void ground(int dx, int dz, double d, int f, Builds.Col o) {
        if (d > r + 2) return;
        o.support = b(Blocks.DIRT);
        double k = rnd(dx, dz, 210), n = noise(dx, dz, Math.max(3, 4.5 * u), 211);
        BlockState s;
        double moon = Math.max(2.5, r * 0.14);
        if (d <= moon) {
            // half light, half dark, a thin line between
            double side = dx + Math.sin(dz / Math.max(1.0, moon * 0.5)) * moon * 0.35;
            s = d > moon - 1 ? b(Blocks.POLISHED_DEEPSLATE) : Math.abs(side) < 0.6 ? b(Blocks.SOUL_SOIL)
                    : side > 0 ? (k < 0.85 ? b(Blocks.CALCITE) : b(Blocks.WHITE_CONCRETE))
                    : (k < 0.85 ? b(Blocks.BLACKSTONE) : b(Blocks.POLISHED_BLACKSTONE));
        } else if (Math.abs(d - circle) < Math.max(1.0, 1.4 * u)) s = k < 0.5 ? b(Blocks.MOSSY_COBBLESTONE)
                : k < 0.8 ? b(Blocks.MOSS_BLOCK) : b(Blocks.COBBLESTONE);
        else if (n > 0.66) s = k < 0.7 ? b(Blocks.SOUL_SOIL) : b(Blocks.PODZOL);
        else if (n < 0.3) s = k < 0.6 ? b(Blocks.MOSS_BLOCK) : b(Blocks.PODZOL);
        else s = k < 0.5 ? b(Blocks.PODZOL) : k < 0.7 ? b(Blocks.COARSE_DIRT) : k < 0.85 ? b(Blocks.MOSS_BLOCK)
                : b(Blocks.ROOTED_DIRT);
        if (d > r) s = k < 0.5 ? b(Blocks.PODZOL) : b(Blocks.COARSE_DIRT);
        o.set(f, s);
        o.clearAbove(f + 1);
    }

    @Override
    void plan() {
        double moon = Math.max(2.5, r * 0.14);
        // mist lying in drifts, and the small things in the grass
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > r || d <= moon + 0.5) continue;
                double mist = noise(x, z, Math.max(4, 6 * u), 212), k = rnd(x, z, 213);
                // thick in the middle of a drift, thinning to wisps at its edge
                if (mist > 0.64 && k < (mist - 0.64) * 3.2) put(x, 1, z, rnd(x, z, 226) < 0.3
                        ? b(Blocks.WHITE_CARPET) : b(Blocks.LIGHT_GRAY_CARPET));
                else if (k < 0.07) put(x, 1, z, b(Blocks.MOSS_CARPET));
                else if (k < 0.11) put(x, 1, z, b(Blocks.FERN));
                else if (k < 0.13) put(x, 1, z, b(Blocks.SHORT_GRASS));
                else if (k < 0.135) put(x, 1, z, with(with(b(pick(rnd(x, z, 214), Blocks.WHITE_CANDLE,
                        Blocks.LIGHT_GRAY_CANDLE, Blocks.CANDLE)), BlockStateProperties.CANDLES,
                        1 + (int) (rnd(x, z, 215) * 4)), BlockStateProperties.LIT, true));
                else if (k < 0.137 && d > circle + 2) {
                    put(x, 0, z, b(Blocks.SOUL_SOIL));
                    put(x, 1, z, b(Blocks.SOUL_FIRE));
                }
            }
        // the standing stones
        int n = Mth.clamp((int) Math.round(2 * Math.PI * circle / Math.max(5, 6 * u)), 7, 22);
        var stones = ring(n, circle, 0.15, 216);
        int fallen = (int) (rnd(n, 1, 217) * n);
        int[] tops = new int[n];
        for (int i = 0; i < n; i++) {
            double[] s = stones.get(i);
            if (s[1] < -circle * 0.75 && Math.abs(s[0]) < circle * 0.45) {
                tops[i] = -1;
                continue;
            }
            tops[i] = i == fallen ? -1 : stone(s[0], s[1], s[2], i);
            if (i == fallen) lying(s[0], s[1], s[2], i);
        }
        // lintels across some neighbouring pairs
        for (int i = 0; i + 1 < n; i += 3) {
            if (tops[i] < 0 || tops[i + 1] < 0) continue;
            double[] a = stones.get(i), c = stones.get(i + 1);
            int y = Math.min(tops[i], tops[i + 1]) + 1;
            line(a[0], y, a[1], c[0], y, c[1], u >= 1.5 ? 0.5 : 0, (x, yy, z) -> rnd(x, yy, z, 218) < 0.7
                    ? b(Blocks.STONE_BRICKS) : b(Blocks.MOSSY_STONE_BRICKS));
        }
        // the altar to the north, antlers behind it
        altar(0, -(int) Math.round(circle * 0.62));
        // the dead trees round the edge
        int trees = Mth.clamp((int) Math.round(2 * Math.PI * r * 0.86 / Math.max(15, 12 * u)), 5, 18);
        for (var s : ring(trees, r * 0.86, 0.5, 219)) {
            int x = (int) Math.round(s[0]), z = (int) Math.round(s[1]);
            if (z > r * 0.6 && Math.abs(x) < Math.max(4, 5 * u)) continue;
            deadTree(x, z, (int) (s[2] * 1000));
        }
    }

    /** a carved standing stone; its top height */
    private int stone(double sx, double sz, double a, int i) {
        int hh = sc(6, 3) + (int) (rnd(i, 2, 220) * sc(3, 1));
        int w = Math.max(1, sc(1.2, 1)), t = u >= 1.8 ? 1 : 0;
        double tx = -Math.sin(a), tz = Math.cos(a), nx = Math.cos(a), nz = Math.sin(a);
        double lean = (rnd(i, 3, 220) - 0.5) * 0.25;
        int top = 0;
        for (int y = 1; y <= hh; y++) {
            double off = lean * y;
            int width = y > hh - 1 && hh > 3 ? Math.max(0, w - 1) : w;
            for (int k = -width; k <= width; k++)
                for (int j = -t; j <= t; j++) {
                    int x = (int) Math.round(sx + tx * k + nx * (j + off)), z = (int) Math.round(sz + tz * k + nz * (j + off));
                    boolean inner = j == -t;
                    double q = rnd(x, y, z, 221);
                    BlockState st = q < 0.45 ? b(Blocks.STONE_BRICKS) : q < 0.7 ? b(Blocks.MOSSY_STONE_BRICKS)
                            : q < 0.85 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.ANDESITE);
                    // a band of runes on the inner face
                    if (inner && Math.abs(y - hh * 0.55) < Math.max(1, hh * 0.18))
                        st = (k + y) % 2 == 0 ? b(Blocks.CHISELED_DEEPSLATE) : b(Blocks.CHISELED_STONE_BRICKS);
                    if (y <= 1 && q < 0.5) st = b(Blocks.MOSSY_COBBLESTONE);
                    put(x, y, z, st);
                    top = Math.max(top, y);
                }
        }
        // moss on its top, a soul lantern or fire at its inner foot
        int fx = (int) Math.round(sx - nx * (t + 1.5)), fz = (int) Math.round(sz - nz * (t + 1.5));
        if (i % 2 == 0) put(fx, 1, fz, b(Blocks.SOUL_LANTERN));
        else if (i % 4 == 1) {
            put(fx, 0, fz, b(Blocks.SOUL_SOIL));
            put(fx, 1, fz, lit(Blocks.SOUL_CAMPFIRE));
        } else put(fx, 1, fz, with(with(b(Blocks.WHITE_CANDLE), BlockStateProperties.CANDLES, 3),
                BlockStateProperties.LIT, true));
        put((int) Math.round(sx + nx * lean * hh), top + 1, (int) Math.round(sz + nz * lean * hh), b(Blocks.MOSS_CARPET));
        return top;
    }

    private void lying(double sx, double sz, double a, int i) {
        int len = sc(6, 3);
        double tx = -Math.sin(a), tz = Math.cos(a);
        for (int k = 0; k < len; k++)
            for (int w = 0; w <= (u >= 1.2 ? 1 : 0); w++) {
                int x = (int) Math.round(sx + tx * k + Math.cos(a) * w), z = (int) Math.round(sz + tz * k + Math.sin(a) * w);
                put(x, 1, z, rnd(x, k, z, 222) < 0.5 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.CRACKED_STONE_BRICKS));
                if (rnd(x, z, 223) < 0.4) put(x, 2, z, b(Blocks.MOSS_CARPET));
            }
    }

    /** the stepped altar: soul fires either side, candles on it, his antlers behind it with blue flames on the tines */
    private void altar(int x, int z) {
        int w = sc(3, 2), steps = Mth.clamp(sc(1.5, 1), 1, 3);
        for (int s = 0; s < steps; s++) {
            int ww = w + steps - s;
            for (int dx = -ww; dx <= ww; dx++)
                for (int dz = -ww + 1; dz <= ww - 1; dz++) {
                    boolean edge = Math.abs(dx) == ww || Math.abs(dz) == ww - 1;
                    Direction in = Math.abs(dx) == ww ? (dx > 0 ? Direction.WEST : Direction.EAST)
                            : dz > 0 ? Direction.NORTH : Direction.SOUTH;
                    put(x + dx, s + 1, z + dz, edge && s == steps - 1 ? stair(Blocks.POLISHED_DEEPSLATE_STAIRS, in, false)
                            : edge ? stair(Blocks.DEEPSLATE_TILE_STAIRS, in, false) : b(Blocks.POLISHED_DEEPSLATE));
                }
        }
        int ty = steps + 1;
        // the table: a block of chiseled stone with slabs either side
        for (int dx = -w + 1; dx <= w - 1; dx++)
            put(x + dx, ty, z, Math.abs(dx) == w - 1 ? b(Blocks.POLISHED_BLACKSTONE_BRICKS) : b(Blocks.CHISELED_POLISHED_BLACKSTONE));
        for (int dx = -w + 1; dx <= w - 1; dx++)
            put(x + dx, ty + 1, z, dx == 0 ? with(b(Blocks.SKELETON_SKULL), BlockStateProperties.ROTATION_16, 0)
                    : with(with(b(Math.floorMod(dx, 2) == 0 ? Blocks.WHITE_CANDLE : Blocks.CANDLE),
                    BlockStateProperties.CANDLES, 1 + Math.floorMod(dx * 7, 4)), BlockStateProperties.LIT, true));
        // soul fires in bowls either side
        for (int side = -1; side <= 1; side += 2) {
            int bx = x + side * (w + steps + 1), bz = z;
            put(bx, 1, bz, b(Blocks.POLISHED_BLACKSTONE_WALL));
            put(bx, 2, bz, b(Blocks.SOUL_SOIL));
            put(bx, 3, bz, lit(Blocks.SOUL_CAMPFIRE));
        }
        // the antlers: two broad palms of bone branching up and out behind it
        int az = z - w - steps;
        int hh = sc(9, 5);
        for (int side = -1; side <= 1; side += 2) {
            double bx = x + side * 0.8;
            double ex = x + side * Math.max(3, 4.5 * u), ey = ty + hh * 0.7;
            line(bx, ty, az, ex, ey, az, u >= 1.6 ? 0.6 : 0, (px, py, pz) -> b(Blocks.BONE_BLOCK));
            // the palm: a fan of tines up from the beam's end
            int tines = Mth.clamp((int) Math.round(3 + u), 3, 7);
            for (int k = 0; k < tines; k++) {
                double fa = Math.toRadians(20 + 120.0 * k / (tines - 1)) * side;
                double tlen = Math.max(2, hh * 0.45 * (0.7 + 0.3 * Math.sin(Math.PI * k / (tines - 1))));
                double tx = ex + Math.sin(fa) * tlen, tyy = ey + Math.cos(Math.toRadians(20 + 120.0 * k / (tines - 1))
                        * 0.7) * tlen;
                line(ex, ey, az, tx, tyy, az, 0, (px, py, pz) -> rnd(px, py, pz, 224) < 0.8 ? b(Blocks.BONE_BLOCK)
                        : b(Blocks.CALCITE));
                put((int) Math.round(tx), (int) Math.round(tyy) + 1, az, k % 2 == 0 ? b(Blocks.SOUL_TORCH)
                        : with(with(b(Blocks.WHITE_CANDLE), BlockStateProperties.CANDLES, 2), BlockStateProperties.LIT, true));
            }
        }
    }

    /** a dead, twisted dark oak: bent trunk, roots, branching bare limbs, soul lanterns hanging off a few */
    private void deadTree(int x, int z, int salt) {
        int hh = sc(10, 6) + (int) (rnd(salt, 1, 225) * sc(4, 2));
        boolean thick = u >= 1.6;
        double wx = (rnd(salt, 2, 225) - 0.5) * 2.5 * Math.max(1, u), wz = (rnd(salt, 3, 225) - 0.5) * 2.5 * Math.max(1, u);
        double px = x, pz = z;
        for (int y = 1; y <= hh; y++) {
            double t = (double) y / hh;
            px = x + wx * Math.sin(t * Math.PI * 0.8);
            pz = z + wz * Math.sin(t * Math.PI * 0.8);
            int bx = (int) Math.round(px), bz = (int) Math.round(pz);
            Block k = rnd(bx, y, bz, 226) < 0.2 ? Blocks.STRIPPED_DARK_OAK_LOG : Blocks.DARK_OAK_LOG;
            put(bx, y, bz, log(k, Direction.Axis.Y));
            if (thick && y < hh * 0.7) {
                put(bx + 1, y, bz, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
                put(bx, y, bz + 1, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
                put(bx + 1, y, bz + 1, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
            }
        }
        // roots
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + rnd(salt, i, 227);
            double l = Math.max(2, 2.5 * u);
            Direction.Axis ax = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? Direction.Axis.X : Direction.Axis.Z;
            line(x, 1.4, z, x + Math.cos(a) * l, 0.6, z + Math.sin(a) * l, 0, (bx, by, bz) -> log(Blocks.DARK_OAK_LOG, ax));
        }
        // limbs: a few, each splitting once
        int limbs = 3 + (int) (rnd(salt, 4, 225) * 3);
        for (int i = 0; i < limbs; i++) {
            double a = i * Math.PI * 2 / limbs + rnd(salt, i + 10, 225);
            double sy = Math.round(hh * (0.55 + 0.45 * i / limbs));
            double st = sy / hh;
            double px0 = x + wx * Math.sin(st * Math.PI * 0.8), pz0 = z + wz * Math.sin(st * Math.PI * 0.8);
            double len = Math.max(3, 4.5 * u) * (0.7 + rnd(salt, i + 20, 225) * 0.5);
            double ex = px0 + Math.cos(a) * len, ez = pz0 + Math.sin(a) * len, ey = sy + len * (0.3 + rnd(salt, i, 228) * 0.5);
            limb(px0, sy, pz0, ex, ey, ez);
            for (int k = 0; k < 2; k++) {
                double a2 = a + (k == 0 ? -0.7 : 0.7);
                double l2 = len * 0.55;
                double fx = ex + Math.cos(a2) * l2, fz = ez + Math.sin(a2) * l2, fy = ey + l2 * (k == 0 ? 0.6 : -0.1);
                limb(ex, ey, ez, fx, fy, fz);
                if (k == 1 && i % 2 == 0) hangLantern((int) Math.round(fx), (int) Math.round(fy), (int) Math.round(fz),
                        1 + (int) (rnd(salt, i + 30, 225) * sc(2, 1)), true);
            }
        }
    }

    private void limb(double x0, double y0, double z0, double x1, double y1, double z1) {
        double dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), dz = Math.abs(z1 - z0);
        Direction.Axis ax = dy > Math.max(dx, dz) ? Direction.Axis.Y : dx > dz ? Direction.Axis.X : Direction.Axis.Z;
        line(x0, y0, z0, x1, y1, z1, 0, (bx, by, bz) -> log(Blocks.DARK_OAK_LOG, ax));
    }
}
