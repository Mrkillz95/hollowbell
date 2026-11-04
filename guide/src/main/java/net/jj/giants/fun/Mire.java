package net.jj.giants.fun;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The Lantern Willow's den: a glowing bog. Mud, moss and grass with still pools (clay and mud in the bottom, lily
 * pads and glowing sea pickles in them, mangrove roots at their edges), mossy hummocks, ferns and dripleaves; round
 * the edge old willows stand up on arching mangrove roots with drooping crowns, curtains of glow-berry vines and
 * lanterns hanging from their branches like his lures; a spruce boardwalk on posts winds in from the south with
 * lanterns along it.
 */
final class Mire extends Plan {
    Mire(int r, double h) {
        super(r, h);
    }

    @Override
    public int reach() {
        return r + 2;
    }

    private double wet(int dx, int dz) {
        return noise2(dx, dz, Math.max(5, 7 * u), 140);
    }

    /** 2 deep pool, 1 shallow, 0 none */
    private int pool(int dx, int dz, double d) {
        if (d > r - 2.5) return 0;
        double w = wet(dx, dz) + (d / r) * 0.12;
        return w < 0.3 ? 2 : w < 0.42 ? 1 : 0;
    }

    private int hump(int dx, int dz, double d) {
        double w = wet(dx, dz);
        if (w < 0.68 || d > r - 1) return 0;
        return w > 0.78 && u > 0.7 ? 2 : 1;
    }

    @Override
    int top(int dx, int dz) {
        double d = Math.sqrt(dx * dx + dz * dz);
        return pool(dx, dz, d) > 0 ? -1 : hump(dx, dz, d);
    }

    @Override
    void ground(int dx, int dz, double d, int f, Builds.Col o) {
        if (d > r + 1) return;
        o.support = b(Blocks.MUD);
        int p = pool(dx, dz, d);
        double k = rnd(dx, dz, 141);
        if (p > 0) {
            o.set(f - p, k < 0.5 ? b(Blocks.CLAY) : b(Blocks.MUD));
            o.set(f - p - 1, b(Blocks.MUD));
            for (int y = -p + 1; y <= 0; y++) o.set(f + y, b(Blocks.WATER));
            o.clearAbove(f + 1);
            return;
        }
        int hh = hump(dx, dz, d);
        BlockState s = k < 0.32 ? b(Blocks.MUD) : k < 0.55 ? b(Blocks.MOSS_BLOCK) : k < 0.75 ? b(Blocks.GRASS_BLOCK)
                : k < 0.85 ? b(Blocks.PODZOL) : k < 0.93 ? b(Blocks.MUDDY_MANGROVE_ROOTS) : b(Blocks.ROOTED_DIRT);
        if (hh > 0) s = k < 0.7 ? b(Blocks.MOSS_BLOCK) : b(Blocks.GRASS_BLOCK);
        if (d > r) s = k < 0.5 ? b(Blocks.MUD) : b(Blocks.GRASS_BLOCK);
        for (int y = 0; y < hh; y++) o.set(f + y, b(Blocks.ROOTED_DIRT));
        o.set(f + hh, s);
        o.set(f - 1, b(Blocks.MUD));
        o.clearAbove(f + hh + 1);
    }

    @Override
    void plan() {
        // the small things on the ground and in the water
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > r) continue;
                double k = rnd(x, z, 142);
                int p = pool(x, z, d);
                if (p > 0) {
                    boolean shore = pool(x + 1, z, d) == 0 || pool(x - 1, z, d) == 0 || pool(x, z + 1, d) == 0
                            || pool(x, z - 1, d) == 0;
                    if (shore && k < 0.25) put(x, 0, z, with(b(Blocks.MANGROVE_ROOTS), BlockStateProperties.WATERLOGGED,
                            true));
                    else if (k < 0.14) put(x, 1, z, b(Blocks.LILY_PAD));
                    else if (k < 0.17 && p == 2) put(x, -1, z, with(with(b(Blocks.SEA_PICKLE), BlockStateProperties.PICKLES,
                            1 + (int) (rnd(x, z, 143) * 4)), BlockStateProperties.WATERLOGGED, true));
                    else if (k < 0.19 && shore) {
                        put(x, 0, z, with(b(Blocks.BIG_DRIPLEAF_STEM), BlockStateProperties.WATERLOGGED, true));
                        put(x, 1, z, facing(Blocks.BIG_DRIPLEAF, inward(x, z)));
                    }
                    continue;
                }
                int y = top(x, z) + 1;
                if (k < 0.12) put(x, y, z, b(Blocks.MOSS_CARPET));
                else if (k < 0.2) put(x, y, z, b(Blocks.SHORT_GRASS));
                else if (k < 0.24) put(x, y, z, b(Blocks.FERN));
                else if (k < 0.255) {
                    put(x, y, z, with(b(Blocks.LARGE_FERN), BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
                    put(x, y + 1, z, with(b(Blocks.LARGE_FERN), BlockStateProperties.DOUBLE_BLOCK_HALF,
                            DoubleBlockHalf.UPPER));
                } else if (k < 0.262) put(x, y, z, b(Blocks.BLUE_ORCHID));
            }
        // the boardwalk in from the south, on posts, lanterns along it
        double wob = rnd(r, 2, 144) * 6;
        int last = -99;
        for (double t = r + 1; t > r * 0.3; t -= 0.5) {
            double cx = Math.sin(t / Math.max(4, 5 * u) + wob) * Math.max(1.5, 2.5 * u), cz = t;
            int w = u >= 1.5 ? 2 : 1;
            for (int x = (int) Math.round(cx) - w; x <= (int) Math.round(cx) + w; x++) {
                int z = (int) Math.round(cz);
                boolean edge = Math.abs(x - Math.round(cx)) == w;
                put(x, 1, z, edge ? log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z)
                        : with(b(Blocks.SPRUCE_SLAB), BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
                if (edge && Math.floorMod(z, 3) == 0) put(x, 0, z, log(Blocks.MANGROVE_LOG, Direction.Axis.Y));
            }
            int z = (int) Math.round(cz);
            if (z != last && Math.floorMod(z, Math.max(5, (int) Math.round(6 * u))) == 0) {
                int x = (int) Math.round(cx) + w + (u >= 1.5 ? 0 : 1);
                int post = sc(2.5, 2);
                if (u < 1.5) put(x, 1, z, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
                for (int y = 2; y <= post + 1; y++) put(x, y, z, b(Blocks.SPRUCE_FENCE));
                put(x, post + 2, z, b(Blocks.LANTERN));
            }
            last = z;
        }
        // lantern posts here and there in the mire
        int posts = Mth.clamp((int) Math.round(2 * u * u), 2, 24);
        for (int i = 0; i < posts; i++) {
            double a = rnd(i, 1, 155) * Math.PI * 2, t = r * (0.15 + rnd(i, 2, 155) * 0.5);
            int x = (int) Math.round(Math.cos(a) * t), z = (int) Math.round(Math.sin(a) * t);
            int y = top(x, z);
            if (y < 0) put(x, 0, z, log(Blocks.MANGROVE_LOG, Direction.Axis.Y));
            int ph = sc(2.5, 2) + (int) (rnd(i, 3, 155) * 2);
            for (int k = 1; k <= ph; k++) put(x, Math.max(y, 0) + k, z, b(Blocks.SPRUCE_FENCE));
            put(x, Math.max(y, 0) + ph + 1, z, b(Blocks.LANTERN));
        }
        // the willows round the edge
        int trees = Mth.clamp((int) Math.round(2 * Math.PI * r * 0.85 / Math.max(11, 15 * u)), 4, 14);
        for (var s : ring(trees, r * 0.82, 0.6, 145)) {
            int x = (int) Math.round(s[0]), z = (int) Math.round(s[1]);
            if (z > r * 0.5 && Math.abs(x) < Math.max(4, 5 * u)) continue;
            willow(x, z, (int) (s[2] * 1000));
        }
        // one big old willow off to the side, if there's room
        if (u >= 0.6) {
            int x = (int) Math.round(-r * 0.45), z = (int) Math.round(-r * 0.4);
            willow(x, z, 7);
        }
    }

    /** a willow standing up on arching roots, a drooping crown, glow-berry curtains, lanterns on its branches */
    private void willow(int x, int z, int salt) {
        double big = 0.85 + rnd(salt, 1, 146) * 0.4;
        int lift = sc(2.5 * big, 2);
        int trunk = sc(8 * big, 5);
        boolean thick = u >= 1.6;
        // the roots: arches from the trunk's foot out and down
        int roots = 5 + (int) (rnd(salt, 2, 146) * 3);
        double spread = Math.max(2.5, 3.5 * u * big);
        for (int i = 0; i < roots; i++) {
            double a = i * Math.PI * 2 / roots + rnd(salt, i, 147) * 0.6;
            double ex = x + Math.cos(a) * spread, ez = z + Math.sin(a) * spread;
            double px = x, py = lift + 0.5, pz = z;
            for (int k = 1; k <= 8; k++) {
                double t = k / 8.0;
                double nx = Mth.lerp(t, x, ex), nz = Mth.lerp(t, z, ez);
                double ny = lift * Math.cos(t * Math.PI / 2) + Math.sin(t * Math.PI) * lift * 0.5 + 0.2;
                line(px, py, pz, nx, ny, nz, thick ? 0.6 : 0, (bx, by, bz) -> rnd(bx, by, bz, 148) < 0.75
                        ? b(Blocks.MANGROVE_ROOTS) : log(Blocks.MANGROVE_LOG, Direction.Axis.Y));
                px = nx;
                py = ny;
                pz = nz;
            }
        }
        // the trunk, a little bent
        double bx = (rnd(salt, 3, 146) - 0.5) * 2, bz = (rnd(salt, 4, 146) - 0.5) * 2;
        int topY = lift + trunk;
        for (int y = lift; y <= topY; y++) {
            double t = (double) (y - lift) / trunk;
            int tx = x + (int) Math.round(bx * t * t), tz = z + (int) Math.round(bz * t * t);
            put(tx, y, tz, log(Blocks.MANGROVE_LOG, Direction.Axis.Y));
            if (thick) {
                put(tx + 1, y, tz, log(Blocks.MANGROVE_LOG, Direction.Axis.Y));
                put(tx, y, tz + 1, log(Blocks.MANGROVE_LOG, Direction.Axis.Y));
                put(tx + 1, y, tz + 1, log(Blocks.MANGROVE_LOG, Direction.Axis.Y));
            }
        }
        int cx = x + (int) Math.round(bx), cz = z + (int) Math.round(bz);
        // branches with lanterns (his lures) hanging off their ends
        double cr = Math.max(3, 3.6 * u * big);
        int arms = 3 + (int) (rnd(salt, 5, 146) * 3);
        for (int i = 0; i < arms; i++) {
            double a = i * Math.PI * 2 / arms + rnd(salt, i + 10, 146);
            double len = cr * 0.95;
            double ex = cx + Math.cos(a) * len, ez = cz + Math.sin(a) * len;
            double sy = topY - trunk * 0.3, ey = topY - 1 + rnd(salt, i + 20, 146) * 2;
            Direction.Axis ax = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? Direction.Axis.X : Direction.Axis.Z;
            line(cx, sy, cz, ex, ey, ez, 0, (px, py, pz) -> log(Blocks.MANGROVE_LOG, ax));
            int lx = (int) Math.round(ex), lz = (int) Math.round(ez), ly = (int) Math.round(ey);
            int drop = 1 + (int) (rnd(salt, i + 30, 146) * sc(3, 1));
            if (i % 3 == 0) hangLantern(lx, ly, lz, drop, false);
            else {
                // a glowing lure on a strand, like his
                for (int k = 1; k <= drop; k++) put(lx, ly - k, lz, b(Blocks.CHAIN));
                double lr = Math.max(0.5, 0.55 * u);
                int ly2 = ly - drop - 1 - (int) Math.floor(lr);
                BlockState glow = i % 3 == 1 ? b(Blocks.OCHRE_FROGLIGHT) : b(Blocks.PEARLESCENT_FROGLIGHT);
                if (lr < 1) put(lx, ly2, lz, glow);
                else ball(lx, ly2, lz, lr, lr, lr, (px, py, pz) -> glow);
            }
        }
        // the crown: a low, wide dome
        double crownY = topY + cr * 0.15;
        // a few lobes, not one dome
        for (int lobe = 0; lobe < 3; lobe++) {
            double la = rnd(salt, lobe, 156) * Math.PI * 2, lo = lobe == 0 ? 0 : cr * 0.45;
            double lx = cx + Math.cos(la) * lo, lz = cz + Math.sin(la) * lo, lr = cr * (lobe == 0 ? 0.9 : 0.6);
            double ly = crownY + (lobe == 0 ? 0 : -cr * 0.12);
            ball(lx, ly, lz, lr, lr * 0.45, lr, (px, py, pz) -> {
                double e = Math.hypot(px - lx, pz - lz) / lr;
                if (rnd(px, py, pz, 149) < 0.18 + e * 0.3) return null;
                return rnd(px, py, pz, 150) < 0.75 ? leaves(Blocks.MANGROVE_LEAVES) : leaves(Blocks.AZALEA_LEAVES);
            });
        }
        put(cx, (int) Math.round(crownY), cz, log(Blocks.MANGROVE_LOG, Direction.Axis.Y));
        // curtains of vines, glow berries on some, hanging from the crown's underside toward its edge
        int under = (int) Math.floor(crownY - cr * 0.42 * 0.7);
        for (int px = (int) Math.floor(cx - cr * 1.3); px <= Math.ceil(cx + cr * 1.3); px++)
            for (int pz = (int) Math.floor(cz - cr * 1.3); pz <= Math.ceil(cz + cr * 1.3); pz++) {
                double e = Math.hypot(px - cx, pz - cz) / cr;
                if (e < 0.35 || e > 1.15 || rnd(px, pz, 151) > 0.38) continue;
                int from = -1;
                for (int py = under - 2; py <= (int) Math.ceil(crownY); py++) {
                    BlockState s = get(px, py, pz);
                    if (s != null && s.is(Blocks.MANGROVE_LEAVES) || s != null && s.is(Blocks.AZALEA_LEAVES)) {
                        from = py;
                        break;
                    }
                }
                if (from < 0) continue;
                int len = 1 + (int) (Math.pow(rnd(px, pz, 152), 1.6) * Math.max(2, (from - 1) * 0.95));
                boolean berries = rnd(px, pz, 153) < 0.6;
                for (int k = 1; k <= len; k++) {
                    if (from - k <= 0) break;
                    boolean end = k == len || from - k - 1 <= 0;
                    BlockState v = end ? with(b(Blocks.CAVE_VINES), BlockStateProperties.AGE_25, 25)
                            : b(Blocks.CAVE_VINES_PLANT);
                    v = with(v, BlockStateProperties.BERRIES, berries && rnd(px, k, pz, 154) < 0.6);
                    soft(px, from - k, pz, v);
                    if (end) break;
                }
            }
    }
}
