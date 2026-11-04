package net.jj.giants.fun;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The Cerberus's den: a walled yard in three parts, one for each head. Facing out of the gate (south) from his
 * throne, the fire part is on his left (blackstone, basalt columns, magma cracks, burning netherrack), the ice part on
 * his right (packed and blue ice, snow, ice spikes) and the stone part behind him (cobbles, boulders, broken pillars);
 * where two meet the ground turns to obsidian, basalt or calcite. In the middle a stepped rock throne with three
 * spires behind it (fire, stone, ice), chains running out from it along the ground to iron anchors, gnawed bones and
 * skulls round it, and a broken wall with a gate between two towers.
 */
final class Kennel extends Plan {
    private static final int FIRE = 0, ICE = 1, STONE = 2;
    /** the middle of each part's arc (fire to the east, ice to the west, stone to the north) */
    private static final double[] MID = {Math.toRadians(30), Math.toRadians(150), Math.toRadians(-90)};
    private final double dais;
    private final int wallH;

    Kennel(int r, double h) {
        super(r, h);
        dais = Math.max(3, r * 0.2);
        wallH = sc(3, 2);
    }

    @Override
    public int reach() {
        return r + 4;
    }

    private static double gap(double a, double b) {
        return Math.abs(Mth.wrapDegrees(Math.toDegrees(a - b)));
    }

    /** which part, and how near (in blocks along the arc) to the next one */
    private int part(int dx, int dz) {
        double a = Math.atan2(dz, dx) + (noise(dx, dz, Math.max(3, 4 * u), 90) - 0.5) * 0.5;
        int best = 0;
        for (int k = 1; k < 3; k++) if (gap(a, MID[k]) < gap(a, MID[best])) best = k;
        return best;
    }

    private double edge(int dx, int dz) {
        double a = Math.atan2(dz, dx) + (noise(dx, dz, Math.max(3, 4 * u), 90) - 0.5) * 0.5;
        double best = 999;
        for (int k = 0; k < 3; k++) {
            double bound = MID[k] + Math.toRadians(60);
            best = Math.min(best, Math.toRadians(gap(a, bound)) * Math.sqrt(dx * dx + dz * dz));
        }
        return best;
    }

    private int other(int dx, int dz) {
        double a = Math.atan2(dz, dx) + (noise(dx, dz, Math.max(3, 4 * u), 90) - 0.5) * 0.5;
        int p = part(dx, dz), next = -1;
        for (int k = 0; k < 3; k++) if (k != p && (next < 0 || gap(a, MID[k]) < gap(a, MID[next]))) next = k;
        return next;
    }

    /** the dais: steps up to the throne */
    private int step(double d) {
        if (d > dais) return 0;
        int tiers = Mth.clamp((int) Math.round(u * 1.2), 1, 4);
        return Math.min(tiers, 1 + (int) ((dais - d) / Math.max(1.2, dais * 0.22)));
    }

    @Override
    int top(int dx, int dz) {
        return step(Math.sqrt(dx * dx + dz * dz));
    }

    private BlockState floor(int p, int dx, int dz) {
        double k = rnd(dx, dz, 91), n = noise(dx, dz, Math.max(3, 3.5 * u), 92);
        if (p == FIRE) {
            if (Math.abs(noise(dx, dz, Math.max(4, 6 * u), 93) - 0.5) < 0.012) return b(Blocks.MAGMA_BLOCK);
            if (n > 0.86) return k < 0.75 ? b(Blocks.NETHERRACK) : b(Blocks.MAGMA_BLOCK);
            return k < 0.4 ? b(Blocks.BLACKSTONE) : k < 0.6 ? b(Blocks.POLISHED_BLACKSTONE) : k < 0.72
                    ? b(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS) : k < 0.88 ? log(Blocks.BASALT, Direction.Axis.Y)
                    : k < 0.97 ? b(Blocks.SMOOTH_BASALT) : b(Blocks.GILDED_BLACKSTONE);
        }
        if (p == ICE) {
            if (n > 0.62) return k < 0.75 ? b(Blocks.SNOW_BLOCK) : b(Blocks.WHITE_CONCRETE);
            return k < 0.5 ? b(Blocks.PACKED_ICE) : k < 0.68 ? b(Blocks.BLUE_ICE) : k < 0.85 ? b(Blocks.SNOW_BLOCK)
                    : b(Blocks.CALCITE);
        }
        if (n > 0.7) return k < 0.5 ? b(Blocks.GRAVEL) : b(Blocks.COBBLESTONE);
        return k < 0.3 ? b(Blocks.STONE) : k < 0.48 ? b(Blocks.COBBLESTONE) : k < 0.62 ? b(Blocks.ANDESITE)
                : k < 0.74 ? b(Blocks.STONE_BRICKS) : k < 0.84 ? b(Blocks.CRACKED_STONE_BRICKS)
                : k < 0.92 ? b(Blocks.TUFF) : b(Blocks.MOSSY_COBBLESTONE);
    }

    private BlockState seam(int a, int b, int dx, int dz) {
        boolean k = rnd(dx, dz, 94) < 0.7;
        if ((a == FIRE && b == ICE) || (a == ICE && b == FIRE)) return k ? b(Blocks.OBSIDIAN) : b(Blocks.CRYING_OBSIDIAN);
        if (a == STONE ? b == FIRE : a == FIRE) return k ? log(Blocks.BASALT, Direction.Axis.Y) : b(Blocks.TUFF);
        return k ? b(Blocks.CALCITE) : b(Blocks.DIORITE);
    }

    private BlockState tile(int p) {
        return p == FIRE ? b(Blocks.POLISHED_BLACKSTONE_BRICKS) : p == ICE ? b(Blocks.PACKED_ICE)
                : b(Blocks.STONE_BRICKS);
    }

    @Override
    void ground(int dx, int dz, double d, int f, Builds.Col o) {
        if (d > r + 3) return;
        int p = part(dx, dz);
        o.support = p == FIRE ? b(Blocks.BLACKSTONE) : p == ICE ? b(Blocks.PACKED_ICE) : b(Blocks.STONE);
        BlockState s = edge(dx, dz) < Math.max(0.8, 0.9 * u) && d > dais + 1 ? seam(p, other(dx, dz), dx, dz)
                : floor(p, dx, dz);
        if (d > r) s = tile(p);
        o.set(f, s);
        o.set(f - 1, o.support);
        int st = step(d);
        if (st > 0) {
            // the dais in the three stones, steps facing out
            for (int y = 1; y <= st; y++) o.set(f + y, tile(p));
            boolean rim = step(d + 1.0) < st;
            if (rim) o.set(f + st, stair(p == FIRE ? Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS : p == ICE
                    ? Blocks.QUARTZ_STAIRS : Blocks.STONE_BRICK_STAIRS, inward(dx, dz), false));
            if (d < 1.5) o.set(f + st, p == FIRE ? b(Blocks.CHISELED_POLISHED_BLACKSTONE) : b(Blocks.CHISELED_STONE_BRICKS));
        }
        o.clearAbove(f + st + 1);
    }

    @Override
    void plan() {
        // the broken wall round it, each part in its own stone
        int gate = Math.max(2, sc(3, 2));
        for (int x = -r - 3; x <= r + 3; x++)
            for (int z = -r - 3; z <= r + 3; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= r + 0.5 || d > r + 2.5) continue;
                if (z > 0 && Math.abs(x) <= gate) continue;
                int p = part(x, z);
                double broke = noise(x, z, Math.max(3, 3 * u), 95);
                int hh = (int) Math.round(wallH * (0.35 + broke * 0.9));
                for (int y = 1; y <= hh; y++) {
                    double k = rnd(x, y, z, 96);
                    put(x, y, z, p == FIRE ? (k < 0.5 ? b(Blocks.POLISHED_BLACKSTONE_BRICKS) : k < 0.8
                            ? b(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS) : b(Blocks.BLACKSTONE))
                            : p == ICE ? (k < 0.6 ? b(Blocks.PACKED_ICE) : k < 0.85 ? b(Blocks.SNOW_BLOCK)
                            : b(Blocks.BLUE_ICE))
                            : (k < 0.45 ? b(Blocks.STONE_BRICKS) : k < 0.7 ? b(Blocks.CRACKED_STONE_BRICKS)
                            : k < 0.85 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.COBBLESTONE)));
                }
                if (hh >= wallH && Math.floorMod(x + z, 2) == 0 && d > r + 1.5)
                    put(x, hh + 1, z, p == FIRE ? b(Blocks.POLISHED_BLACKSTONE_BRICK_WALL)
                            : p == ICE ? b(Blocks.SNOW_BLOCK) : b(Blocks.STONE_BRICK_WALL));
            }
        // the gate towers: fire on the east side, ice on the west, fire bowls on top
        int th = wallH + sc(4, 2);
        for (int side = -1; side <= 1; side += 2) {
            int cx = side * (gate + 2), cz = (int) Math.round(Math.sqrt(Math.max(0, (r + 1.5) * (r + 1.5) - cx * cx)));
            boolean fire = side > 0;
            int w = u >= 1.5 ? 2 : 1;
            for (int x = cx - w; x <= cx + w; x++)
                for (int z = cz - w; z <= cz + w; z++)
                    for (int y = 1; y <= th; y++)
                        put(x, y, z, y == th ? (fire ? b(Blocks.CHISELED_POLISHED_BLACKSTONE) : b(Blocks.CHISELED_QUARTZ_BLOCK))
                                : fire ? (rnd(x, y, z, 97) < 0.8 ? b(Blocks.POLISHED_BLACKSTONE_BRICKS)
                                : b(Blocks.GILDED_BLACKSTONE)) : (rnd(x, y, z, 97) < 0.7 ? b(Blocks.PACKED_ICE)
                                : b(Blocks.BLUE_ICE)));
            put(cx, th + 1, cz, fire ? lit(Blocks.CAMPFIRE) : lit(Blocks.SOUL_CAMPFIRE));
            put(cx, th, cz, fire ? b(Blocks.MAGMA_BLOCK) : b(Blocks.SOUL_SOIL));
            // a banner on the side facing the gate, and chains hanging off the front
            put(cx - side * (w + 1), th - 1, cz, facing(fire ? Blocks.RED_WALL_BANNER : Blocks.LIGHT_BLUE_WALL_BANNER,
                    side > 0 ? Direction.WEST : Direction.EAST));
            for (int y = th - 1; y >= Math.max(1, th - sc(3, 2)); y--) put(cx, y, cz + w + 1, b(Blocks.CHAIN));
        }
        // the throne: three spires behind the dais (fire east, stone middle, ice west)
        int baseZ = -(int) Math.round(dais * 0.8);
        int st = step(0);
        int[] tall = {sc(14, 6), sc(18, 8), sc(14, 6)};
        for (int i = -1; i <= 1; i++) {
            int p = i == 1 ? FIRE : i == -1 ? ICE : STONE;
            double sx = i * Math.max(2.5, dais * 0.5), sz = baseZ - (i == 0 ? 1 : 0);
            double rad = Math.max(1.2, 1.9 * u) * (i == 0 ? 1.15 : 1);
            int hh = tall[i + 1];
            for (int y = 0; y <= hh; y++) {
                double rr = rad * (1 - (double) y / (hh + 1)) + 0.35;
                double lean = (double) y / hh * i * rad * 0.4;
                for (int x = (int) Math.floor(sx - rr - 1); x <= Math.ceil(sx + rr + 1); x++)
                    for (int z = (int) Math.floor(sz - rr - 1); z <= Math.ceil(sz + rr + 1); z++) {
                        double e = Math.hypot(x - sx - lean, z - sz) + (rnd(x, y, z, 98) - 0.5) * 0.6;
                        if (e > rr) continue;
                        double k = rnd(x, y, z, 99);
                        boolean tip = y > hh * 0.75;
                        put(x, st + y, z, p == FIRE ? (tip ? (k < 0.5 ? b(Blocks.MAGMA_BLOCK) : b(Blocks.SHROOMLIGHT))
                                : k < 0.15 ? b(Blocks.MAGMA_BLOCK) : k < 0.6 ? b(Blocks.BLACKSTONE)
                                : log(Blocks.BASALT, Direction.Axis.Y))
                                : p == ICE ? (tip ? b(Blocks.BLUE_ICE) : k < 0.65 ? b(Blocks.PACKED_ICE)
                                : k < 0.85 ? b(Blocks.BLUE_ICE) : b(Blocks.SNOW_BLOCK))
                                : (k < 0.35 ? b(Blocks.STONE) : k < 0.6 ? b(Blocks.ANDESITE) : k < 0.8
                                ? b(Blocks.COBBLESTONE) : b(Blocks.TUFF)));
                    }
            }
        }
        // the seat back joining them, and arm rests
        int back = sc(5, 3);
        double bw = Math.max(2.5, dais * 0.75);
        for (int x = (int) -Math.ceil(bw); x <= Math.ceil(bw); x++)
            for (int y = 1; y <= back; y++) {
                int p = x > 1 ? FIRE : x < -1 ? ICE : STONE;
                put(x, st + y, baseZ + 1, y == back ? (p == FIRE ? b(Blocks.CHISELED_POLISHED_BLACKSTONE) : p == ICE
                        ? b(Blocks.CHISELED_QUARTZ_BLOCK) : b(Blocks.CHISELED_STONE_BRICKS)) : tile(p));
            }
        for (int side = -1; side <= 1; side += 2)
            for (int z = baseZ + 2; z <= baseZ + 2 + sc(3, 1); z++) {
                int x = side * (int) Math.ceil(bw);
                put(x, st + 1, z, tile(side > 0 ? FIRE : ICE));
                put(x, st + 2, z, stair(side > 0 ? Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS : Blocks.QUARTZ_STAIRS,
                        side > 0 ? Direction.EAST : Direction.WEST, false));
            }
        // three chains from iron anchors on the dais out along the ground to posts at the wall
        for (int i = 0; i < 3; i++) {
            double a = MID[i] + (i == STONE ? Math.toRadians(25) : 0);
            double ax = Math.cos(a), az = Math.sin(a);
            int x0 = (int) Math.round(ax * (dais - 1)), z0 = (int) Math.round(az * (dais - 1));
            put(x0, step(Math.hypot(x0, z0)) + 1, z0, b(Blocks.IRON_BLOCK));
            double end = r - Math.max(2, 2 * u);
            boolean alongX = Math.abs(ax) > Math.abs(az);
            for (double t = dais; t < end; t += 0.5) {
                int x = (int) Math.round(ax * t), z = (int) Math.round(az * t);
                put(x, 1, z, log(Blocks.CHAIN, alongX ? Direction.Axis.X : Direction.Axis.Z));
            }
            int px = (int) Math.round(ax * end), pz = (int) Math.round(az * end);
            for (int y = 1; y <= sc(3, 2); y++) put(px, y, pz, y == sc(3, 2) ? b(Blocks.IRON_BLOCK)
                    : b(Blocks.POLISHED_BLACKSTONE));
            put(px, sc(3, 2) + 1, pz, i == ICE ? b(Blocks.SOUL_LANTERN) : b(Blocks.LANTERN));
        }
        // each part's own things
        int n = Mth.clamp((int) Math.round(3 * u * u), 2, 40);
        for (int i = 0; i < n * 3; i++) {
            double a = rnd(i, 1, 100) * Math.PI * 2, t = dais + 2 + rnd(i, 2, 100) * (r - dais - 4);
            int x = (int) Math.round(Math.cos(a) * t), z = (int) Math.round(Math.sin(a) * t);
            if (edge(x, z) < 2 || Math.abs(x) <= gate + 1 && z > 0) continue;
            int p = part(x, z);
            if (p == FIRE) {
                if (i % 3 == 0) basalt(x, z);
                else if (i % 3 == 1) {
                    put(x, 0, z, b(Blocks.NETHERRACK));
                    put(x, 1, z, b(Blocks.FIRE));
                } else {
                    put(x, 0, z, b(Blocks.MAGMA_BLOCK));
                    if (u > 1) put(x, 1, z, lit(Blocks.CAMPFIRE));
                }
            } else if (p == ICE) {
                if (i % 2 == 0) spike(x, z);
                else for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++)
                        if (rnd(x + dx, z + dz, 101) < 0.6)
                            soft(x + dx, 1, z + dz, with(b(Blocks.SNOW), BlockStateProperties.LAYERS,
                                    1 + (int) (rnd(x + dx, z + dz, 102) * 3)));
            } else {
                if (i % 2 == 0) {
                    double br = Math.max(1, 1.6 * u * (0.6 + rnd(i, 3, 100) * 0.6));
                    ball(x, 0.3, z, br, br * 0.8, br, (bx, by, bz) -> by < 1 ? null
                            : pick(rnd(bx, by, bz, 103), b(Blocks.STONE), b(Blocks.ANDESITE), b(Blocks.COBBLESTONE),
                                    b(Blocks.MOSSY_COBBLESTONE), b(Blocks.TUFF)));
                } else {
                    int ph = sc(4, 2) - (int) (rnd(i, 4, 100) * sc(2, 1));
                    for (int y = 1; y <= ph; y++) put(x, y, z, y == ph && rnd(i, 5, 100) < 0.5
                            ? b(Blocks.STONE_BRICK_STAIRS) : rnd(x, y, z, 104) < 0.3 ? b(Blocks.CRACKED_STONE_BRICKS)
                            : b(Blocks.STONE_BRICKS));
                }
            }
        }
        // gnawed bones and skulls round the throne
        int bones = Mth.clamp((int) Math.round(3 * u), 2, 16);
        for (int i = 0; i < bones; i++) {
            double a = rnd(i, 6, 105) * Math.PI * 2, t = dais + 1.5 + rnd(i, 7, 105) * r * 0.35;
            int x = (int) Math.round(Math.cos(a) * t), z = (int) Math.round(Math.sin(a) * t);
            if (z > 0 && Math.abs(x) <= gate) continue;
            femur(x, z, rnd(i, 8, 105) < 0.5, sc(4, 2));
        }
        int skulls = Mth.clamp((int) Math.round(4 * u), 3, 24);
        for (int i = 0; i < skulls; i++) {
            double a = rnd(i, 9, 106) * Math.PI * 2, t = dais + 1 + rnd(i, 10, 106) * r * 0.6;
            int x = (int) Math.round(Math.cos(a) * t), z = (int) Math.round(Math.sin(a) * t);
            soft(x, 1, z, with(b(Blocks.SKELETON_SKULL), BlockStateProperties.ROTATION_16,
                    (int) (rnd(i, 11, 106) * 16)));
        }
    }

    private void basalt(int x, int z) {
        int n = 3 + (int) (rnd(x, z, 107) * 5 * Math.max(1, u * 0.6));
        for (int k = 0; k < n; k++) {
            int bx = x + (int) Math.round((rnd(k, x, 108) - 0.5) * 3 * Math.max(1, u * 0.6));
            int bz = z + (int) Math.round((rnd(k, z, 109) - 0.5) * 3 * Math.max(1, u * 0.6));
            int hh = 1 + (int) (rnd(bx, bz, 110) * sc(8, 3));
            for (int y = 1; y <= hh; y++) put(bx, y, bz, log(Blocks.BASALT, Direction.Axis.Y));
            if (rnd(bx, bz, 111) < 0.3) put(bx, hh + 1, bz, b(Blocks.MAGMA_BLOCK));
        }
    }

    private void spike(int x, int z) {
        int hh = sc(6, 3) + (int) (rnd(x, z, 112) * sc(4, 1));
        double rad = Math.max(0.6, 1.1 * u);
        for (int y = 1; y <= hh; y++) {
            double rr = rad * (1 - (double) y / (hh + 1)) + 0.3;
            for (int dx = (int) -Math.ceil(rr); dx <= Math.ceil(rr); dx++)
                for (int dz = (int) -Math.ceil(rr); dz <= Math.ceil(rr); dz++)
                    if (Math.hypot(dx, dz) <= rr) put(x + dx, y, z + dz, Math.hypot(dx, dz) < rr - 1 || y > hh - 2
                            ? b(Blocks.BLUE_ICE) : b(Blocks.PACKED_ICE));
        }
    }

    /** a big gnawed bone lying down: knobs at both ends, bitten through in places */
    private void femur(int x, int z, boolean alongX, int len) {
        Direction.Axis ax = alongX ? Direction.Axis.X : Direction.Axis.Z;
        for (int k = 0; k <= len; k++) {
            int bx = alongX ? x + k : x, bz = alongX ? z : z + k;
            if (k > 0 && k < len && rnd(bx, bz, 113) < 0.2) continue;
            put(bx, 1, bz, log(Blocks.BONE_BLOCK, ax));
            if (k == 0 || k == len) {
                put(alongX ? bx : bx + 1, 1, alongX ? bz + 1 : bz, log(Blocks.BONE_BLOCK, ax));
                if (len >= 4) put(bx, 2, bz, b(Blocks.BONE_BLOCK));
            }
        }
    }
}
