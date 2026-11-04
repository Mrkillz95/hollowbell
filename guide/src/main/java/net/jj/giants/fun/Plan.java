package net.jj.giants.fun;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.WallSide;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 7, ADMIN_BRIDGE.md 23.3) A build planned in two layers, for the dens and the arena: the ground, worked out a
 * column at a time as it is built (Builds), and the pieces on it (trees, stones, wrecks, bones...), worked out once
 * when the build starts and kept by column. Heights here are from the floor (0 = the block the admin stood on).
 * Sizes go by {@link #u}, the build's radius over 32, so a den for a small giant is a small den with fewer, smaller
 * pieces, and one for a big giant a big one. Fences, walls and bars are joined to their neighbours here, since the
 * builder puts blocks without updating them.
 */
abstract class Plan implements Builds.Shape {
    /** the radius, his height at his size, and the scale (radius / 32) */
    final int r;
    final double h, u;
    private final Map<Long, Map<Integer, BlockState>> pieces = new HashMap<>();
    private int count;

    Plan(int r, double h) {
        this.r = r;
        this.h = h;
        this.u = r / 32.0;
    }

    /** the ground of one column (f = the floor's height) */
    abstract void ground(int dx, int dz, double d, int f, Builds.Col o);

    /** the top block of the ground in a column, from the floor */
    abstract int top(int dx, int dz);

    /** the pieces */
    abstract void plan();

    final Plan made() {
        plan();
        join();
        return this;
    }

    /** how many piece blocks there are (the tests) */
    final int pieceCount() {
        return count;
    }

    @Override
    public final void column(int dx, int dz, double d, int f, Builds.Col o) {
        ground(dx, dz, d, f, o);
        Map<Integer, BlockState> m = pieces.get(key(dx, dz));
        if (m != null) for (var e : m.entrySet()) o.set(f + e.getKey(), e.getValue());
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    // putting pieces

    final boolean inside(int x, int z) {
        int k = reach();
        return Math.abs(x) <= k && Math.abs(z) <= k;
    }

    final void put(int x, int y, int z, @Nullable BlockState s) {
        if (s == null || !inside(x, z)) return;
        Map<Integer, BlockState> m = pieces.computeIfAbsent(key(x, z), k -> new HashMap<>(8));
        if (m.put(y, s) == null) count++;
    }

    /** only where no piece is yet */
    final void soft(int x, int y, int z, @Nullable BlockState s) {
        if (get(x, y, z) == null) put(x, y, z, s);
    }

    final @Nullable BlockState get(int x, int y, int z) {
        Map<Integer, BlockState> m = pieces.get(key(x, z));
        return m == null ? null : m.get(y);
    }

    /** a solid piece there (not air, not a plant) */
    final boolean solid(int x, int y, int z) {
        BlockState s = get(x, y, z);
        return s != null && s.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    interface Paint {
        @Nullable BlockState at(int x, int y, int z);
    }

    final void box(int x0, int y0, int z0, int x1, int y1, int z1, Paint p) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++)
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) put(x, y, z, p.at(x, y, z));
    }

    /** an ellipsoid round (cx, cy, cz) */
    final void ball(double cx, double cy, double cz, double rx, double ry, double rz, Paint p) {
        for (int x = (int) Math.floor(cx - rx); x <= Math.ceil(cx + rx); x++)
            for (int z = (int) Math.floor(cz - rz); z <= Math.ceil(cz + rz); z++)
                for (int y = (int) Math.floor(cy - ry); y <= Math.ceil(cy + ry); y++) {
                    double a = (x - cx) / rx, b = (y - cy) / ry, c = (z - cz) / rz;
                    if (a * a + b * b + c * c <= 1) put(x, y, z, p.at(x, y, z));
                }
    }

    /** a line of blocks, thick round it (0 = one block) */
    final void line(double x0, double y0, double z0, double x1, double y1, double z1, double thick, Paint p) {
        double len = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
        int n = Math.max(1, (int) Math.ceil(len * 2));
        int t = (int) Math.ceil(thick);
        int lx = Integer.MIN_VALUE, ly = 0, lz = 0;
        for (int i = 0; i <= n; i++) {
            double k = (double) i / n;
            double x = x0 + (x1 - x0) * k, y = y0 + (y1 - y0) * k, z = z0 + (z1 - z0) * k;
            if (t == 0) {
                // one block thick, but every block touching the last face to face (no gaps seen from afar)
                int cx = (int) Math.round(x), cy = (int) Math.round(y), cz = (int) Math.round(z);
                if (lx != Integer.MIN_VALUE) {
                    if (cx != lx && (cy != ly || cz != lz)) put(cx, ly, lz, p.at(cx, ly, lz));
                    if (cz != lz && cy != ly) put(cx, ly, cz, p.at(cx, ly, cz));
                }
                put(cx, cy, cz, p.at(cx, cy, cz));
                lx = cx;
                ly = cy;
                lz = cz;
            } else for (int a = -t; a <= t; a++)
                for (int b = -t; b <= t; b++)
                    for (int c = -t; c <= t; c++)
                        if (a * a + b * b + c * c <= thick * thick + 0.3) {
                            int px = (int) Math.round(x) + a, py = (int) Math.round(y) + b, pz = (int) Math.round(z) + c;
                            put(px, py, pz, p.at(px, py, pz));
                        }
        }
    }

    /** a hanging dripstone (down) or a standing one (up), n long, its root at y */
    final void dripstone(int x, int y, int z, int n, boolean down) {
        Direction dir = down ? Direction.DOWN : Direction.UP;
        for (int i = 0; i < n; i++) {
            int left = n - 1 - i;
            DripstoneThickness t = left == 0 ? DripstoneThickness.TIP : left == 1 ? DripstoneThickness.FRUSTUM
                    : i == 0 ? DripstoneThickness.BASE : DripstoneThickness.MIDDLE;
            put(x, down ? y - i : y + i, z, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                    .setValue(BlockStateProperties.VERTICAL_DIRECTION, dir)
                    .setValue(BlockStateProperties.DRIPSTONE_THICKNESS, t));
        }
    }

    /** a lantern hanging on a chain n long under (x, y, z) */
    final void hangLantern(int x, int y, int z, int n, boolean soul) {
        for (int i = 1; i <= n; i++) put(x, y - i, z, b(Blocks.CHAIN));
        put(x, y - n - 1, z, (soul ? b(Blocks.SOUL_LANTERN) : b(Blocks.LANTERN)).setValue(BlockStateProperties.HANGING,
                true));
    }

    // blocks

    static BlockState b(Block k) {
        return k.defaultBlockState();
    }

    static <T extends Comparable<T>, V extends T> BlockState with(BlockState s, Property<T> p, V v) {
        return s.trySetValue(p, v);
    }

    static BlockState log(Block k, Direction.Axis a) {
        return with(b(k), BlockStateProperties.AXIS, a);
    }

    static BlockState leaves(Block k) {
        return with(b(k), LeavesBlock.PERSISTENT, true);
    }

    static BlockState stair(Block k, Direction facing, boolean top) {
        return with(with(b(k), BlockStateProperties.HORIZONTAL_FACING, facing), BlockStateProperties.HALF,
                top ? Half.TOP : Half.BOTTOM);
    }

    static BlockState slab(Block k, boolean top) {
        return with(b(k), BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    static BlockState facing(Block k, Direction d) {
        BlockState s = b(k);
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && d.getAxis().isHorizontal())
            return s.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
        return with(s, BlockStateProperties.FACING, d);
    }

    static BlockState lit(Block k) {
        return with(b(k), BlockStateProperties.LIT, true);
    }

    /** a 16-way rotation (banners, skulls) facing from (x, z) toward (tx, tz) */
    static int rot16(double x, double z, double tx, double tz) {
        double a = Math.toDegrees(Math.atan2(-(tx - x), tz - z));
        return Math.floorMod((int) Math.round(a / 22.5), 16);
    }

    /** the side facing from (x, z) toward the middle */
    static Direction inward(int x, int z) {
        return Direction.getNearest(-x, 0, -z);
    }

    // chance and noise

    static double rnd(int x, int z, int salt) {
        return Builds.hash(x, z, salt);
    }

    static double rnd(int x, int y, int z, int salt) {
        return Builds.hash(x, z * 31 + y * 1013, salt);
    }

    /** smooth value noise from 0 to 1, cells this wide */
    static double noise(double x, double z, double cell, int salt) {
        double fx = x / cell, fz = z / cell;
        int ix = Mth.floor(fx), iz = Mth.floor(fz);
        double tx = fx - ix, tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = rnd(ix, iz, salt), b = rnd(ix + 1, iz, salt), c = rnd(ix, iz + 1, salt), d = rnd(ix + 1, iz + 1, salt);
        return Mth.lerp(tz, Mth.lerp(tx, a, b), Mth.lerp(tx, c, d));
    }

    /** two octaves */
    static double noise2(double x, double z, double cell, int salt) {
        return noise(x, z, cell, salt) * 0.65 + noise(x, z, cell / 2.7, salt + 7) * 0.35;
    }

    /** one of these, by a number from 0 to 1 */
    @SafeVarargs
    static <T> T pick(double k, T... from) {
        return from[Mth.clamp((int) (k * from.length), 0, from.length - 1)];
    }

    /** scaled to the den: at least lo */
    final int sc(double v, int lo) {
        return Math.max(lo, (int) Math.round(v * u));
    }

    /** n spots spread round a ring (a little jitter), as {x, z, angle} */
    final List<double[]> ring(int n, double rad, double jitter, int salt) {
        List<double[]> out = new ArrayList<>();
        double off = rnd(n, (int) rad, salt) * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double a = off + i * Math.PI * 2 / n + (rnd(i, salt, salt + 1) - 0.5) * jitter * Math.PI * 2 / n;
            double rr = rad * (1 + (rnd(i, salt, salt + 2) - 0.5) * jitter * 0.3);
            out.add(new double[] {Math.cos(a) * rr, Math.sin(a) * rr, a});
        }
        return out;
    }

    // joining fences, walls and bars to their neighbours

    private void join() {
        List<Object[]> changes = new ArrayList<>();
        for (var c : pieces.entrySet()) {
            int x = (int) (c.getKey() >> 32), z = (int) (long) c.getKey();
            for (var e : c.getValue().entrySet()) {
                BlockState s = e.getValue();
                Block k = s.getBlock();
                if (!(k instanceof CrossCollisionBlock) && !(k instanceof WallBlock)) continue;
                int y = e.getKey();
                boolean n = joins(s, get(x, y, z - 1)), so = joins(s, get(x, y, z + 1)),
                        w = joins(s, get(x - 1, y, z)), ea = joins(s, get(x + 1, y, z));
                BlockState t;
                if (k instanceof WallBlock) {
                    boolean straight = (n && so && !w && !ea) || (w && ea && !n && !so);
                    BlockState above = get(x, y + 1, z);
                    t = s.setValue(WallBlock.NORTH_WALL, n ? WallSide.LOW : WallSide.NONE)
                            .setValue(WallBlock.SOUTH_WALL, so ? WallSide.LOW : WallSide.NONE)
                            .setValue(WallBlock.WEST_WALL, w ? WallSide.LOW : WallSide.NONE)
                            .setValue(WallBlock.EAST_WALL, ea ? WallSide.LOW : WallSide.NONE)
                            .setValue(WallBlock.UP, !straight || (above != null && !above.isAir()));
                } else t = s.setValue(CrossCollisionBlock.NORTH, n).setValue(CrossCollisionBlock.SOUTH, so)
                        .setValue(CrossCollisionBlock.WEST, w).setValue(CrossCollisionBlock.EAST, ea);
                if (t != s) changes.add(new Object[] {x, y, z, t});
            }
        }
        for (Object[] c : changes) pieces.get(key((int) c[0], (int) c[2])).put((int) c[1], (BlockState) c[3]);
    }

    private static boolean joins(BlockState s, @Nullable BlockState n) {
        if (n == null || n.isAir()) return false;
        Block a = s.getBlock(), b = n.getBlock();
        if (a instanceof FenceBlock) return b instanceof FenceBlock || n.isSolidRender(EmptyBlockGetter.INSTANCE,
                BlockPos.ZERO);
        if (a instanceof IronBarsBlock) return b instanceof IronBarsBlock || b instanceof WallBlock
                || n.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (a instanceof WallBlock) return b instanceof WallBlock || b instanceof IronBarsBlock
                || n.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        return false;
    }
}
