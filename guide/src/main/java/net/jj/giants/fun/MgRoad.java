package net.jj.giants.fun;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * (Part 10A, Escort) An old road across a valley, west to east, gently winding: a cobbled and trodden track, grassy
 * hills rising either side with trees and boulders, broken walls by the road to hide behind, lantern posts along it, a
 * camp with an archway where it starts (west) and a gatehouse with banners where it ends (east).
 */
final class MgRoad extends Plan {
    private final int half, width, valley;

    MgRoad(int half, int width) {
        super(half, 0);
        this.half = half;
        this.width = width;
        this.valley = Math.max(28, width * 4);
    }

    @Override
    public int reach() {
        return half + 10;
    }

    /** where the middle of the road is at x */
    static double roadZ(int half, double x) {
        return Math.sin(x / (half * 0.42)) * half * 0.08;
    }

    private double roadZ(double x) {
        return roadZ(half, x);
    }

    /** how high the bank rises off the road here */
    private int bank(int x, int z) {
        double off = Math.abs(z - roadZ(x)) - width / 2.0 - 3;
        if (off <= 0 || Math.abs(x) > half + 6) return 0;
        double rise = Math.min(1, off / (valley - width / 2.0)) * (6 + 6 * noise2(x, z, 24, 401));
        double edgeFade = Math.min(1, (half + 6 - Math.abs(x)) / 10.0);
        return (int) Math.round(rise * edgeFade + (noise(x, z, 5, 402) - 0.5) * 1.2 * Math.min(1, off / 4));
    }

    @Override
    int top(int dx, int dz) {
        return Math.max(0, bank(dx, dz));
    }

    @Override
    void ground(int dx, int dz, double d, int f, Builds.Col o) {
        if (Math.abs(dx) > half + 9 || Math.abs(dz - roadZ(dx)) > valley + 4) return;
        o.support = b(Blocks.STONE);
        double off = Math.abs(dz - roadZ(dx));
        double k = rnd(dx, dz, 403);
        if (off <= width / 2.0 && Math.abs(dx) <= half + 6) {
            BlockState s = off < width / 2.0 - 1.2 ? (k < 0.55 ? b(Blocks.DIRT_PATH) : k < 0.8 ? b(Blocks.GRAVEL)
                    : b(Blocks.COBBLESTONE)) : (k < 0.5 ? b(Blocks.COARSE_DIRT) : b(Blocks.DIRT_PATH));
            o.set(f, s);
            o.clearAbove(f + 1);
            return;
        }
        int h = Math.max(0, bank(dx, dz));
        for (int y = 1; y <= h; y++) {
            BlockState s = y == h ? (k < 0.08 ? b(Blocks.COARSE_DIRT) : b(Blocks.GRASS_BLOCK))
                    : y >= h - 2 ? b(Blocks.DIRT) : rnd(dx, y, dz, 404) < 0.2 ? b(Blocks.ANDESITE) : b(Blocks.STONE);
            o.set(f + y, s);
        }
        o.clearAbove(f + h + 1);
    }

    @Override
    void plan() {
        // lantern posts along the road, either side by turns
        int n = 0;
        for (int x = -half + 8; x <= half - 8; x += 14, n++) {
            int side = (n & 1) == 0 ? 1 : -1;
            int z = (int) Math.round(roadZ(x) + side * (width / 2.0 + 1));
            for (int y = 1; y <= 3; y++) put(x, y, z, b(Blocks.SPRUCE_FENCE));
            put(x, 4, z, b(Blocks.SPRUCE_PLANKS));
            put(x, 3, z - side, with(b(Blocks.LANTERN), BlockStateProperties.HANGING, true));
            put(x, 4, z - side, b(Blocks.SPRUCE_FENCE));
        }
        // broken walls to hide behind, by the road
        for (double[] w : List.of(new double[] {-0.6, 1}, new double[] {-0.3, -1}, new double[] {0.05, 1},
                new double[] {0.35, -1}, new double[] {0.62, 1})) {
            int x0 = (int) Math.round(w[0] * half);
            int side = (int) w[1];
            int len = 7 + (int) (rnd(x0, side, 405) * 5);
            for (int i = 0; i < len; i++) {
                int x = x0 + i;
                int z = (int) Math.round(roadZ(x) + side * (width / 2.0 + 3));
                int hh = 1 + (int) Math.round(3 * Math.abs(Math.sin(i * 0.9 + x0)) * (1 - Math.abs(i - len / 2.0) / len));
                for (int y = top(x, z) + 1; y <= top(x, z) + hh; y++) put(x, y, z, rnd(x, y, z, 406) < 0.3
                        ? b(Blocks.MOSSY_COBBLESTONE) : rnd(x, y, z, 407) < 0.5 ? b(Blocks.COBBLESTONE)
                        : b(Blocks.CRACKED_STONE_BRICKS));
            }
        }
        // trees and boulders on the banks
        for (int i = 0; i < half / 3; i++) {
            int x = (int) Math.round((rnd(i, 0, 408) * 2 - 1) * (half - 6));
            int side = rnd(i, 1, 409) < 0.5 ? -1 : 1;
            double off = width / 2.0 + 8 + rnd(i, 2, 410) * (valley - width / 2.0 - 10);
            int z = (int) Math.round(roadZ(x) + side * off);
            int base = top(x, z);
            if (rnd(i, 3, 411) < 0.7) tree(x, base, z, rnd(i, 4, 412) < 0.35);
            else ball(x, base + 1, z, 1.6, 1.2, 1.6, (a, b2, c) -> rnd(a, b2, c, 413) < 0.3 ? b(Blocks.MOSSY_COBBLESTONE)
                    : b(Blocks.STONE));
        }
        camp(-half);
        gatehouse(half);
    }

    private void tree(int x, int base, int z, boolean birch) {
        int h = 5 + (int) (rnd(x, z, 414) * 3);
        for (int y = 1; y <= h; y++) put(x, base + y, z, log(birch ? Blocks.BIRCH_LOG : Blocks.OAK_LOG,
                Direction.Axis.Y));
        ball(x, base + h, z, 2.6, 2.2, 2.6, (a, b2, c) -> (a == x && c == z && b2 <= base + h) ? null
                : leaves(birch ? Blocks.BIRCH_LEAVES : Blocks.OAK_LEAVES));
    }

    /** where the road starts: an archway of logs with a banner, a tent and a camp fire */
    private void camp(int x) {
        int z0 = (int) Math.round(roadZ(x));
        int w = width / 2 + 1;
        for (int side = -1; side <= 1; side += 2)
            for (int y = 1; y <= 6; y++) put(x, y, z0 + side * w, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
        for (int z = -w; z <= w; z++) put(x, 7, z0 + z, log(Blocks.SPRUCE_LOG, Direction.Axis.Z));
        put(x - 1, 6, z0, facing(Blocks.GREEN_WALL_BANNER, Direction.WEST));
        put(x + 1, 6, z0, facing(Blocks.GREEN_WALL_BANNER, Direction.EAST));
        // a tent of wool to one side
        int tz = z0 - w - 5;
        for (int i = -2; i <= 2; i++)
            for (int y = 1; y <= 3; y++) {
                int span = 3 - y;
                for (int s = -span; s <= span; s++)
                    if (Math.abs(s) == span) put(x + i, y, tz + s, b(Blocks.WHITE_WOOL));
            }
        put(x + 1, 1, z0 + w + 3, lit(Blocks.CAMPFIRE));
        for (int[] c : new int[][] {{0, 2}, {2, 2}, {1, 4}}) put(x + c[0], 1, z0 + w + 1 + c[1],
                log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
    }

    /** where the road ends: a stone gatehouse over the road, towers each side, banners and lanterns */
    private void gatehouse(int x) {
        int z0 = (int) Math.round(roadZ(x));
        int w = width / 2 + 1, h = 9;
        for (int side = -1; side <= 1; side += 2)
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = 0; dz <= 4; dz++) {
                    int z = z0 + side * (w + dz);
                    for (int y = 1; y <= h + 3; y++) put(x + dx, y, z, rnd(x + dx, y, z, 415) < 0.12
                            ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS));
                    if (((dx + dz) & 1) == 0) put(x + dx, h + 4, z, b(Blocks.STONE_BRICK_WALL));
                }
        for (int dx = -2; dx <= 2; dx++)
            for (int z = -w + 1; z <= w - 1; z++) {
                for (int y = h - 1; y <= h; y++) put(x + dx, y, z0 + z, b(Blocks.STONE_BRICKS));
                if (((dx + z) & 1) == 0) put(x + dx, h + 1, z0 + z, b(Blocks.STONE_BRICK_WALL));
            }
        for (int side = -1; side <= 1; side += 2) {
            put(x - 3, h - 2, z0 + side * (w + 2), facing(Blocks.YELLOW_WALL_BANNER, Direction.WEST));
            put(x - 3, 3, z0 + side * (w + 1), facing(Blocks.WALL_TORCH, Direction.WEST));
        }
        put(x - 3, h - 3, z0, facing(Blocks.YELLOW_WALL_BANNER, Direction.WEST));
        put(x - 1, h - 2, z0, with(b(Blocks.LANTERN), BlockStateProperties.HANGING, true));
    }
}
