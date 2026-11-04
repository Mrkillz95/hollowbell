package net.jj.giants.fun;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Wreckback's den: a sheltered cove. A lagoon deepening to the middle (sand, gravel and clay on its bed, seagrass,
 * kelp in the deep, a coral reef round the shallows, sea pickles glowing), a sand beach round it with dunes, dead
 * coral, driftwood and tide pools, rocky headlands at the back; a broken galleon lies heeled over in the shallows
 * (planked hull split open with its ribs showing, a standing mast with a torn sail and a black flag, the other mast
 * snapped off and fallen, barrels and empty chests spilled round it), the bow of a second wreck sticks up out of the
 * deep, and an anchor lies half buried on the beach, its chain running into the water.
 */
final class Cove extends Plan {
    private final int deep;
    private final double shore;

    Cove(int r, double h) {
        super(r, h);
        deep = Mth.clamp((int) Math.round(r / 6.0), 3, 16);
        shore = r * 0.7;
    }

    @Override
    public int reach() {
        return r + 4;
    }

    private double coast(int dx, int dz) {
        return shore + (noise(dx, dz, Math.max(5, 7 * u), 160) - 0.5) * r * 0.22;
    }

    private boolean water(int dx, int dz) {
        double d = Math.sqrt(dx * dx + dz * dz);
        return d < coast(dx, dz) && d < r - 3;
    }

    /** the bed under the water (the top of the sand) */
    private int bed(int dx, int dz) {
        double d = Math.sqrt(dx * dx + dz * dz);
        double t = Mth.clamp(d / coast(dx, dz), 0, 1);
        return Math.min(-1, -1 - (int) Math.round((deep - 1) * (1 - t * t) + (noise(dx, dz, 4, 161) - 0.5) * Math.min(2, u)));
    }

    private boolean tidePool(int dx, int dz) {
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < coast(dx, dz) + 2 || d > r - 3) return false;
        return noise(dx, dz, Math.max(2.5, 3 * u), 162) > 0.8;
    }

    private int rock(int dx, int dz) {
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < r * 0.88) return 0;
        double n = noise(dx, dz, Math.max(4, 6 * u), 163);
        if (n < 0.55) return 0;
        return (int) Math.round((n - 0.55) / 0.45 * sc(7, 3) * Math.min(1, (d - r * 0.88) / (r * 0.12 + 1)) + 0.5);
    }

    @Override
    int top(int dx, int dz) {
        if (water(dx, dz)) return bed(dx, dz);
        if (tidePool(dx, dz)) return -1;
        double d = Math.sqrt(dx * dx + dz * dz);
        int rk = rock(dx, dz);
        if (rk > 0) return rk;
        return dune(dx, dz, d);
    }

    private int dune(int dx, int dz, double d) {
        double t = (d - coast(dx, dz)) / Math.max(1, r - coast(dx, dz));
        return t > 0.5 && noise(dx, dz, Math.max(3, 4 * u), 164) > 0.6 ? 1 : 0;
    }

    @Override
    void ground(int dx, int dz, double d, int f, Builds.Col o) {
        if (d > r + 3) return;
        o.support = b(Blocks.SANDSTONE);
        double k = rnd(dx, dz, 165);
        if (water(dx, dz)) {
            int y = bed(dx, dz);
            double n = noise(dx, dz, Math.max(3, 4 * u), 166);
            o.set(f + y, n > 0.72 ? b(Blocks.GRAVEL) : n < 0.2 ? b(Blocks.CLAY) : k < 0.08 ? b(Blocks.GRAVEL)
                    : b(Blocks.SAND));
            o.set(f + y - 1, b(Blocks.SAND));
            o.set(f + y - 2, b(Blocks.SANDSTONE));
            for (int yy = y + 1; yy <= 0; yy++) o.set(f + yy, b(Blocks.WATER));
            o.clearAbove(f + 1);
            return;
        }
        if (tidePool(dx, dz)) {
            o.set(f - 1, k < 0.5 ? b(Blocks.SAND) : b(Blocks.GRAVEL));
            o.set(f - 2, b(Blocks.SANDSTONE));
            o.set(f, b(Blocks.WATER));
            o.clearAbove(f + 1);
            return;
        }
        int rk = rock(dx, dz);
        if (rk > 0) {
            for (int y = 0; y <= rk; y++) {
                double j = rnd(dx, y, dz, 167);
                o.set(f + y, y == rk && j < 0.3 ? b(Blocks.MOSSY_COBBLESTONE) : j < 0.4 ? b(Blocks.STONE)
                        : j < 0.65 ? b(Blocks.ANDESITE) : j < 0.8 ? b(Blocks.COBBLESTONE) : b(Blocks.TUFF));
            }
            o.clearAbove(f + rk + 1);
            return;
        }
        int y = dune(dx, dz, d);
        boolean wetSand = d < coast(dx, dz) + 2;
        BlockState s = wetSand ? (k < 0.15 ? b(Blocks.GRAVEL) : b(Blocks.SAND))
                : k < 0.07 ? b(Blocks.SANDSTONE) : b(Blocks.SAND);
        if (y > 0) o.set(f, b(Blocks.SAND));
        o.set(f + y, s);
        o.set(f - 1, b(Blocks.SAND));
        o.clearAbove(f + y + 1);
    }

    @Override
    void plan() {
        // life in the water and on the beach
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                double k = rnd(x, z, 168);
                if (water(x, z)) {
                    int y = bed(x, z), depth = -y;
                    double d = Math.sqrt(x * x + z * z);
                    boolean reef = d > coast(x, z) * 0.62 && d < coast(x, z) * 0.85
                            && noise(x, z, Math.max(3, 4 * u), 169) > 0.45;
                    if (reef && depth >= 2 && k < 0.6) {
                        Block[] blocks = {Blocks.TUBE_CORAL_BLOCK, Blocks.BRAIN_CORAL_BLOCK, Blocks.BUBBLE_CORAL_BLOCK,
                                Blocks.FIRE_CORAL_BLOCK, Blocks.HORN_CORAL_BLOCK};
                        Block[] fans = {Blocks.TUBE_CORAL_FAN, Blocks.BRAIN_CORAL_FAN, Blocks.BUBBLE_CORAL_FAN,
                                Blocks.FIRE_CORAL_FAN, Blocks.HORN_CORAL_FAN};
                        Block[] plants = {Blocks.TUBE_CORAL, Blocks.BRAIN_CORAL, Blocks.BUBBLE_CORAL, Blocks.FIRE_CORAL,
                                Blocks.HORN_CORAL};
                        int c = (int) (noise(x, z, 3, 170) * 5) % 5;
                        int hh = k < 0.3 && depth >= 3 ? 2 : 1;
                        for (int i = 1; i <= hh; i++) put(x, y + i, z, b(blocks[c]));
                        if (y + hh + 1 <= 0) put(x, y + hh + 1, z, with(b(rnd(x, z, 171) < 0.5 ? fans[c] : plants[c]),
                                BlockStateProperties.WATERLOGGED, true));
                    } else if (depth >= 4 && k < 0.18 + (depth > 6 ? 0.1 : 0)) {
                        int len = 1 + (int) (rnd(x, z, 172) * (depth - 2));
                        for (int i = 1; i <= len; i++) put(x, y + i, z, i == len
                                ? with(b(Blocks.KELP), BlockStateProperties.AGE_25, 25) : b(Blocks.KELP_PLANT));
                    } else if (k < 0.3 && depth >= 2) {
                        put(x, y + 1, z, with(b(Blocks.TALL_SEAGRASS), BlockStateProperties.DOUBLE_BLOCK_HALF,
                                DoubleBlockHalf.LOWER));
                        put(x, y + 2, z, with(b(Blocks.TALL_SEAGRASS), BlockStateProperties.DOUBLE_BLOCK_HALF,
                                DoubleBlockHalf.UPPER));
                    } else if (k < 0.48) put(x, y + 1, z, b(Blocks.SEAGRASS));
                    else if (k < 0.5) put(x, y + 1, z, with(with(b(Blocks.SEA_PICKLE), BlockStateProperties.PICKLES,
                            1 + (int) (rnd(x, z, 173) * 4)), BlockStateProperties.WATERLOGGED, true));
                } else if (tidePool(x, z)) {
                    if (k < 0.25) put(x, -1, z, with(with(b(Blocks.SEA_PICKLE), BlockStateProperties.PICKLES,
                            1 + (int) (rnd(x, z, 174) * 3)), BlockStateProperties.WATERLOGGED, true));
                    else if (k < 0.4) put(x, -1, z, with(b(Blocks.BRAIN_CORAL_FAN), BlockStateProperties.WATERLOGGED, true));
                    else if (k < 0.55) put(x, -1, z, b(Blocks.SEAGRASS));
                } else if (rock(x, z) == 0 && Math.hypot(x, z) <= r) {
                    int y = top(x, z) + 1;
                    if (k < 0.012) put(x, y - 1, z, pick(rnd(x, z, 175), b(Blocks.DEAD_BRAIN_CORAL_BLOCK),
                            b(Blocks.DEAD_TUBE_CORAL_BLOCK), b(Blocks.DEAD_HORN_CORAL_BLOCK)));
                    else if (k < 0.02) put(x, y, z, b(Blocks.DEAD_BUSH));
                    else if (k < 0.03) put(x, y, z, with(pick(rnd(x, z, 176), b(Blocks.DEAD_BRAIN_CORAL),
                            b(Blocks.DEAD_FIRE_CORAL_FAN), b(Blocks.DEAD_TUBE_CORAL)), BlockStateProperties.WATERLOGGED, false));
                    else if (k < 0.034 && Math.hypot(x, z) > coast(x, z) + 4) put(x, y, z, b(Blocks.SHORT_GRASS));
                }
            }
        // the galleon, heeled over in the shallows
        double a1 = rnd(r, 3, 177) * Math.PI * 2;
        double place = shore * 0.78;
        int len = sc(24, 11);
        hull(Math.cos(a1) * place, Math.sin(a1) * place, a1 + Math.PI / 2 + 0.3, len, sc(7.5, 4), sc(5.5, 3),
                0.35, 0, -2, true, 178);
        // a second wreck: just its bow, sticking up out of the deep
        if (u >= 0.55) {
            double a2 = a1 + Math.PI * (0.8 + rnd(r, 4, 177) * 0.4);
            hull(Math.cos(a2) * shore * 0.35, Math.sin(a2) * shore * 0.35, a2 + 0.9, sc(15, 7), sc(5.5, 3), sc(4, 2),
                    0.1, 0.55, bed((int) (Math.cos(a2) * shore * 0.35), (int) (Math.sin(a2) * shore * 0.35)) - 1,
                    false, 179);
        }
        // the anchor on the beach, its chain into the water
        double a3 = a1 + Math.PI * 0.6;
        anchor(Math.cos(a3) * (shore + (r - shore) * 0.55), Math.sin(a3) * (shore + (r - shore) * 0.55), a3);
        // driftwood
        int drift = Mth.clamp((int) Math.round(3 * u), 2, 14);
        for (var s : ring(drift, shore + (r - shore) * 0.45, 0.9, 180)) {
            int x = (int) Math.round(s[0]), z = (int) Math.round(s[1]);
            if (water(x, z) || rock(x, z) > 0) continue;
            boolean ax = rnd(x, z, 181) < 0.5;
            int l = sc(3, 2);
            for (int i = 0; i < l; i++) put(ax ? x + i : x, top(ax ? x + i : x, ax ? z : z + i) + 1, ax ? z : z + i,
                    log(rnd(x, z, 182) < 0.5 ? Blocks.STRIPPED_SPRUCE_LOG : Blocks.STRIPPED_OAK_LOG,
                            ax ? Direction.Axis.X : Direction.Axis.Z));
        }
    }

    /**
     * a ship's hull at (cx, cz) pointing along a: len long, wide across, high from keel to deck; heeled over by roll
     * and pitched up toward the bow; its keel at y0. whole = the full ship with masts and cargo, else just the bow
     */
    private void hull(double cx, double cz, double a, int len, int wide, int high, double roll, double pitch, int y0,
            boolean whole, int salt) {
        double ca = Math.cos(a), sa = Math.sin(a);
        double half = len / 2.0;
        double from = whole ? -half : 0;
        double breakAt = whole ? half * (rnd(salt, 1, 183) - 0.5) * 0.6 : 99, breakW = Math.max(1.5, len * 0.08);
        int box = (int) Math.ceil(half + wide + 2);
        int lo = y0 - 2, hi = y0 + high + (int) Math.ceil(half * pitch) + wide + 2;
        for (int x = (int) Math.floor(cx - box); x <= Math.ceil(cx + box); x++)
            for (int z = (int) Math.floor(cz - box); z <= Math.ceil(cz + box); z++) {
                double s = (x - cx) * ca + (z - cz) * sa, w = -(x - cx) * sa + (z - cz) * ca;
                if (s < from - 0.5 || s > half + 0.5) continue;
                double tt = s >= 0 ? s / half : -s / half;
                double hw = wide / 2.0 * Math.sqrt(Math.max(0, 1 - Math.pow(Math.min(1, tt), s >= 0 ? 2.2 : 5)));
                if (hw < 0.4) continue;
                boolean broken = Math.abs(s - breakAt) < breakW;
                for (int y = lo; y <= hi; y++) {
                    double v = y - y0 - w * roll - Math.max(0, s - from) * pitch;
                    if (v < 0 || v > high + (s > half * 0.7 && whole ? 1 : 0)) continue;
                    double hwv = hw * (0.4 + 0.6 * Math.sqrt(Math.min(1, v / high)));
                    double aw = Math.abs(w);
                    if (aw > hwv + 0.5) continue;
                    boolean shell = aw > hwv - 0.75 || v < 0.9;
                    boolean deck = v > high - 0.6;
                    // inside: the ground left as it is, water up to the sea's top, air over it
                    BlockState ground = y <= top(x, z) ? null : y <= 0 && water(x, z) ? b(Blocks.WATER) : b(Blocks.AIR);
                    if (broken) {
                        // split open: only ribs left
                        boolean rib = Math.floorMod((int) Math.round(s), 2) == 0 && shell && v < high * 0.8;
                        put(x, y, z, rib ? log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y) : ground);
                        continue;
                    }
                    if (shell) {
                        if (rnd(x, y, z, salt + 2) < 0.07 && v > 1) {
                            put(x, y, z, ground);
                            continue;
                        }
                        double k = rnd(x, y, z, salt + 3);
                        put(x, y, z, v < 0.9 ? log(Blocks.STRIPPED_DARK_OAK_LOG, Math.abs(ca) > Math.abs(sa)
                                ? Direction.Axis.X : Direction.Axis.Z)
                                : v > high - 1.5 ? (k < 0.8 ? b(Blocks.SPRUCE_PLANKS) : b(Blocks.STRIPPED_SPRUCE_WOOD))
                                : k < 0.7 ? b(Blocks.DARK_OAK_PLANKS) : k < 0.88 ? b(Blocks.SPRUCE_PLANKS)
                                : b(Blocks.STRIPPED_DARK_OAK_WOOD));
                    } else if (deck && whole && rnd(x, z, salt + 4) < 0.75)
                        put(x, y, z, with(b(Blocks.SPRUCE_SLAB), BlockStateProperties.SLAB_TYPE,
                                net.minecraft.world.level.block.state.properties.SlabType.TOP));
                    else put(x, y, z, ground);
                }
            }
        if (!whole) return;
        // the standing mast: a yard, a torn sail, a crow's nest, a black flag
        double mastS = half * 0.25;
        int mx = (int) Math.round(cx + ca * mastS), mz = (int) Math.round(cz + sa * mastS);
        int mastH = sc(17, 8);
        int baseY = y0 + 1;
        double lean = roll * 0.9;
        double px = -sa, pz = ca;
        int topX = 0, topY = 0, topZ = 0;
        for (int i = 0; i <= mastH; i++) {
            int x = (int) Math.round(mx + px * i * lean), z = (int) Math.round(mz + pz * i * lean);
            put(x, baseY + i, z, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
            topX = x;
            topY = baseY + i;
            topZ = z;
        }
        put(topX, topY + 1, topZ, with(b(Blocks.BLACK_BANNER), BlockStateProperties.ROTATION_16,
                rot16(0, 0, px, pz)));
        int yardY = baseY + (int) (mastH * 0.75);
        int yardW = sc(6, 3);
        int yx = (int) Math.round(mx + px * (yardY - baseY) * lean), yz = (int) Math.round(mz + pz * (yardY - baseY) * lean);
        Direction.Axis along = Math.abs(ca) > Math.abs(sa) ? Direction.Axis.X : Direction.Axis.Z;
        for (int k = -yardW; k <= yardW; k++)
            put((int) Math.round(yx + ca * k), yardY, (int) Math.round(yz + sa * k), log(Blocks.STRIPPED_SPRUCE_LOG, along));
        int sailH = (int) (mastH * 0.5);
        for (int k = -yardW + 1; k <= yardW - 1; k++)
            for (int j = 1; j <= sailH; j++) {
                double torn = rnd(k, j, salt + 5) + (j > sailH * 0.6 ? 0.25 : 0) + Math.abs(k) / (double) yardW * 0.2;
                if (torn > 0.92 || j > sailH - Math.abs(k) / 2) continue;
                int x = (int) Math.round(yx + ca * k + px * (0.8 + j * lean * 0)), z = (int) Math.round(yz + sa * k + pz
                        * 0.8);
                put(x, yardY - j, z, rnd(k, j, salt + 6) < 0.85 ? b(Blocks.WHITE_WOOL) : b(Blocks.LIGHT_GRAY_WOOL));
            }
        int nestY = baseY + mastH - 2;
        int nx = (int) Math.round(mx + px * (mastH - 2) * lean), nz = (int) Math.round(mz + pz * (mastH - 2) * lean);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if (dx != 0 || dz != 0) {
                    put(nx + dx, nestY, nz + dz, b(Blocks.SPRUCE_SLAB));
                    put(nx + dx, nestY + 1, nz + dz, b(Blocks.SPRUCE_FENCE));
                }
        // the snapped mast: a stump and the rest lying across the beach
        double foreS = half * 0.65;
        int fx = (int) Math.round(cx + ca * foreS), fz = (int) Math.round(cz + sa * foreS);
        for (int i = 0; i < sc(3, 2); i++) put(fx, baseY + i, fz, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
        double fa = Math.atan2(cz, cx);
        int flen = sc(13, 6);
        for (int i = 2; i <= flen; i++) {
            int x = (int) Math.round(fx + Math.cos(fa) * i + px * 1.5), z = (int) Math.round(fz + Math.sin(fa) * i + pz * 1.5);
            put(x, Math.max(top(x, z) + 1, baseY - 1), z, log(Blocks.SPRUCE_LOG, Math.abs(Math.cos(fa)) > Math.abs(
                    Math.sin(fa)) ? Direction.Axis.X : Direction.Axis.Z));
        }
        // cargo spilled round it: barrels and empty chests
        int cargo = Mth.clamp((int) Math.round(4 * u), 3, 20);
        for (int i = 0; i < cargo; i++) {
            double ang = rnd(i, salt, 184) * Math.PI * 2, t = wide * 0.6 + rnd(i, salt, 185) * len * 0.45;
            int x = (int) Math.round(cx + Math.cos(ang) * t), z = (int) Math.round(cz + Math.sin(ang) * t);
            if (water(x, z) && bed(x, z) < -3) continue;
            int y = Math.max(top(x, z) + 1, water(x, z) ? bed(x, z) + 1 : 0);
            if (get(x, y, z) != null && !get(x, y, z).isAir() && !get(x, y, z).is(Blocks.WATER)) continue;
            BlockState c = rnd(i, salt, 186) < 0.65 ? facing(Blocks.BARREL, rnd(i, salt, 187) < 0.6 ? Direction.UP
                    : Direction.from2DDataValue(i & 3)) : with(facing(Blocks.CHEST, Direction.from2DDataValue(i & 3)),
                    BlockStateProperties.WATERLOGGED, water(x, z));
            put(x, y, z, c);
            if (rnd(i, salt, 188) < 0.3 && !water(x, z)) put(x, y + 1, z, facing(Blocks.BARREL, Direction.UP));
        }
    }

    /** an anchor lying on the beach, half buried, its chain running off into the water */
    private void anchor(double x, double z, double a) {
        int len = sc(6, 3);
        double ax = Math.cos(a), az = Math.sin(a);
        int y = 1;
        // the shank lying along the ground, toward the land
        for (int i = 0; i <= len; i++) put((int) Math.round(x + ax * i), y, (int) Math.round(z + az * i),
                b(Blocks.IRON_BLOCK));
        // the stock across its far end, the arms curling up at the near end
        double px = -az, pz = ax;
        int sx = (int) Math.round(x + ax * len), sz = (int) Math.round(z + az * len);
        for (int k = -len / 2; k <= len / 2; k++) put((int) Math.round(sx + px * k), y, (int) Math.round(sz + pz * k),
                log(Blocks.STRIPPED_DARK_OAK_LOG, Math.abs(px) > Math.abs(pz) ? Direction.Axis.X : Direction.Axis.Z));
        int arm = Math.max(2, len / 2);
        for (int side = -1; side <= 1; side += 2)
            for (int k = 1; k <= arm; k++) {
                int bx = (int) Math.round(x + px * side * k - ax * (k * k / (double) arm) * 0.5);
                int bz = (int) Math.round(z + pz * side * k - az * (k * k / (double) arm) * 0.5);
                put(bx, y, bz, b(Blocks.IRON_BLOCK));
                if (k == arm) put(bx, y + 1, bz, facing(Blocks.ANVIL, Direction.getNearest(ax, 0, az)));
            }
        // the chain
        double cx = x + ax * len, cz = z + az * len;
        double back = Math.atan2(-cz, -cx);
        for (double t = 1; t < r; t += 0.5) {
            int bx = (int) Math.round(cx + Math.cos(back) * t), bz = (int) Math.round(cz + Math.sin(back) * t);
            if (water(bx, bz)) {
                put(bx, bed(bx, bz) + 1, bz, with(log(Blocks.CHAIN, Math.abs(Math.cos(back)) > Math.abs(Math.sin(back))
                        ? Direction.Axis.X : Direction.Axis.Z), BlockStateProperties.WATERLOGGED, true));
                if (bed(bx, bz) < -3) break;
                continue;
            }
            if (get(bx, top(bx, bz) + 1, bz) != null) continue;
            put(bx, top(bx, bz) + 1, bz, log(Blocks.CHAIN, Math.abs(Math.cos(back)) > Math.abs(Math.sin(back))
                    ? Direction.Axis.X : Direction.Axis.Z));
        }
    }
}
