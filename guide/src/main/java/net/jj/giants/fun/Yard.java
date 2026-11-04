package net.jj.giants.fun;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;

/**
 * The Swarmforge's den: a scrap yard and forge. An iron-ruled work floor in the middle (kept clear for him), the yard
 * round it of gravel and cinders with oil stains and rust, the tracks of his treads curving through it and a railway
 * across it; round the edge brick forge halls with lit furnaces and anvils, four tall smokestacks smoking, heaps of
 * scrap (iron, rusting copper, chains, anvils, lightning rods sticking up), a fallen robot's head with glowing eyes and
 * a robot arm, a gantry crane with a load on its chain, a caged glowing core, and an iron-bar fence with a gate.
 */
final class Yard extends Plan {
    private final double work, track;

    Yard(int r, double h) {
        super(r, h);
        work = r * 0.36;
        track = r * 0.6;
    }

    @Override
    public int reach() {
        return r + 2;
    }

    @Override
    int top(int dx, int dz) {
        return 0;
    }

    private boolean tread(int dx, int dz, double d) {
        double a = Math.toDegrees(Math.atan2(dz, dx));
        if (a > 70 || a < -160) return false;
        double gauge = Math.max(2, 3.5 * u), w = Math.max(1, 1.4 * u);
        return Math.abs(d - (track - gauge)) < w / 2 + 0.3 || Math.abs(d - (track + gauge)) < w / 2 + 0.3;
    }

    @Override
    void ground(int dx, int dz, double d, int f, Builds.Col o) {
        if (d > r + 1) return;
        o.support = b(Blocks.STONE);
        double k = rnd(dx, dz, 190), n = noise(dx, dz, Math.max(3, 4 * u), 191);
        BlockState s;
        int grid = Math.max(4, (int) Math.round(6 * u));
        if (d <= work) {
            boolean line = Math.floorMod(dx, grid) == 0 || Math.floorMod(dz, grid) == 0;
            s = d < 2 ? b(Blocks.CHISELED_TUFF_BRICKS) : line ? b(Blocks.IRON_BLOCK)
                    : Math.abs(d - work + 0.8) < 0.7 ? b(Blocks.WAXED_CUT_COPPER)
                    : Math.floorMod(Math.floorDiv(dx, grid) + Math.floorDiv(dz, grid), 2) == 0 ? b(Blocks.SMOOTH_STONE)
                    : b(Blocks.POLISHED_ANDESITE);
            if (!line && k < 0.04) s = b(Blocks.BLACK_CONCRETE);
        } else if (tread(dx, dz, d)) {
            double a = Math.atan2(dz, dx) * d;
            s = Math.floorMod((int) Math.floor(a), 2) == 0 ? b(Blocks.DEEPSLATE_TILES) : b(Blocks.COBBLED_DEEPSLATE);
        } else if (n > 0.78 && k < 0.45) s = k < 0.2 ? b(Blocks.BLACK_CONCRETE) : k < 0.8 ? b(Blocks.COAL_BLOCK) : b(Blocks.GRAVEL);
        else if (n < 0.25) s = k < 0.3 ? b(Blocks.WAXED_EXPOSED_COPPER) : k < 0.55 ? b(Blocks.WAXED_WEATHERED_COPPER)
                : k < 0.7 ? b(Blocks.ORANGE_TERRACOTTA) : b(Blocks.BROWN_TERRACOTTA);
        else s = k < 0.3 ? b(Blocks.GRAVEL) : k < 0.5 ? b(Blocks.ANDESITE) : k < 0.65 ? b(Blocks.COARSE_DIRT)
                : k < 0.8 ? b(Blocks.COBBLESTONE) : k < 0.9 ? b(Blocks.STONE) : b(Blocks.TUFF);
        o.set(f, s);
        o.clearAbove(f + 1);
    }

    @Override
    void plan() {
        // the railway across the yard (stopping at the work floor), sleepers under it
        int rz = -(int) Math.round(r * 0.5);
        for (int x = -r; x <= r; x++) {
            double d = Math.hypot(x, rz);
            if (d > r - 1) continue;
            for (int z = rz - 1; z <= rz + 1; z++) put(x, 0, z, Math.floorMod(x, 2) == 0
                    ? log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z) : b(Blocks.GRAVEL));
            put(x, 1, rz, with(b(Blocks.RAIL), BlockStateProperties.RAIL_SHAPE, RailShape.EAST_WEST));
        }
        // the edge: forge halls, stacks, scrap, the robot, the crane, the core, by turns round the ring
        int slots = Mth.clamp((int) Math.round(2 * Math.PI * r * 0.82 / Math.max(10, 12 * u)), 6, 22);
        List<double[]> spots = ring(slots, r * 0.8, 0.3, 192);
        int stacks = 0;
        for (int i = 0; i < spots.size(); i++) {
            double[] s = spots.get(i);
            int x = (int) Math.round(s[0]), z = (int) Math.round(s[1]);
            // keep the gate (south) and the railway clear
            if (z > r * 0.55 && Math.abs(x) < Math.max(5, 6 * u)) continue;
            if (Math.abs(z - rz) < Math.max(4, 5 * u)) continue;
            switch (i % 6) {
                case 0 -> {
                    forge(x, z, s[2]);
                    stacks++;
                }
                case 1 -> scrap(x, z, 1.0);
                case 2 -> {
                    if (stacks < 4) {
                        stack(x, z);
                        stacks++;
                    } else scrap(x, z, 0.8);
                }
                case 3 -> robot(x, z, s[2]);
                case 4 -> crane(x, z, s[2]);
                default -> {
                    if (i == 5) core(x, z);
                    else scrap(x, z, 0.7);
                }
            }
        }
        // anvils, grindstones and smithing tables about the yard
        int tools = Mth.clamp((int) Math.round(4 * u), 3, 24);
        for (int i = 0; i < tools; i++) {
            double a = rnd(i, 1, 193) * Math.PI * 2, t = work + 2 + rnd(i, 2, 193) * (r * 0.75 - work - 2);
            int x = (int) Math.round(Math.cos(a) * t), z = (int) Math.round(Math.sin(a) * t);
            if (get(x, 1, z) != null || Math.abs(z - rz) < 2) continue;
            Direction dir = Direction.from2DDataValue((int) (rnd(i, 3, 193) * 4));
            put(x, 1, z, i % 3 == 0 ? facing(Blocks.GRINDSTONE, dir) : i % 3 == 1
                    ? facing(pick(rnd(i, 4, 193), Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL), dir)
                    : b(Blocks.SMITHING_TABLE));
        }
        // lamps on posts round the work floor
        for (var s : ring(Mth.clamp((int) Math.round(3 * u), 4, 12), work + 1.5, 0, 194)) {
            int x = (int) Math.round(s[0]), z = (int) Math.round(s[1]);
            int ph = sc(3, 2);
            for (int y = 1; y < ph; y++) put(x, y, z, b(Blocks.POLISHED_DEEPSLATE_WALL));
            put(x, ph, z, lit(Blocks.WAXED_COPPER_BULB));
        }
        // the iron-bar fence with posts, a gate to the south
        int posts = Math.max(4, (int) Math.round(3 * u));
        for (int x = -r - 1; x <= r + 1; x++)
            for (int z = -r - 1; z <= r + 1; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (Math.abs(d - (r + 0.3)) > 0.55) continue;
                if (z > 0 && Math.abs(x) <= Math.max(3, 4 * u)) continue;
                double along = (Math.atan2(z, x) + Math.PI) * r;
                boolean post = Math.floorMod((int) Math.round(along), posts * 2) == 0;
                int hh = sc(2.5, 2);
                for (int y = 1; y <= hh; y++) put(x, y, z, post ? b(Blocks.WAXED_WEATHERED_COPPER) : b(Blocks.IRON_BARS));
                if (post) put(x, hh + 1, z, b(Blocks.LIGHTNING_ROD));
            }
    }

    /** a brick forge hall facing the middle: furnaces along the back, anvils in front, a smokestack behind */
    private void forge(int x, int z, double a) {
        int w = sc(5, 3), dp = sc(3.5, 2), hh = sc(5, 3);
        double fx = -Math.cos(a), fz = -Math.sin(a);
        Direction in = Direction.getNearest(fx, 0, fz);
        double px = -fz, pz = fx;
        for (int i = -w; i <= w; i++)
            for (int j = -dp; j <= dp; j++) {
                int bx = (int) Math.round(x + px * i + fx * j), bz = (int) Math.round(z + pz * i + fz * j);
                boolean back = j == -dp, side = Math.abs(i) == w;
                put(bx, 0, bz, b(Blocks.POLISHED_DEEPSLATE));
                for (int y = 1; y <= hh; y++) {
                    BlockState wall = rnd(bx, y, bz, 195) < 0.75 ? b(Blocks.BRICKS) : b(Blocks.DEEPSLATE_BRICKS);
                    if (back) put(bx, y, bz, y <= 2 && Math.abs(i) < w && Math.floorMod(i, 2) == 0
                            ? lit(y == 1 && Math.floorMod(i, 4) == 0 ? Blocks.BLAST_FURNACE : Blocks.FURNACE)
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, in) : wall);
                    else if (side) put(bx, y, bz, wall);
                    else if (y == hh) put(bx, y, bz, rnd(bx, bz, 196) < 0.15 ? null
                            : slab(Blocks.DEEPSLATE_TILE_SLAB, false));
                    else put(bx, y, bz, b(Blocks.AIR));
                }
                if ((back || side) && Math.floorMod(i + j, 2) == 0) put(bx, hh + 1, bz, slab(Blocks.BRICK_SLAB, false));
            }
        // anvils and a cauldron by the open front
        for (int i = -w + 2; i <= w - 2; i += 3) {
            int bx = (int) Math.round(x + px * i + fx * (dp - 1)), bz = (int) Math.round(z + pz * i + fz * (dp - 1));
            put(bx, 1, bz, i % 2 == 0 ? facing(Blocks.ANVIL, in.getClockWise()) : b(Blocks.CAULDRON));
        }
        // its stack behind
        stackAt((int) Math.round(x + px * (w - 1) - fx * (dp + 2)), (int) Math.round(z + pz * (w - 1) - fz * (dp + 2)),
                sc(14, 8));
    }

    private void stack(int x, int z) {
        int base = sc(2.5, 2);
        for (int dx = -base; dx <= base; dx++)
            for (int dz = -base; dz <= base; dz++)
                for (int y = 1; y <= base; y++) put(x + dx, y, z + dz, Math.abs(dx) == base || Math.abs(dz) == base
                        ? b(Blocks.DEEPSLATE_BRICKS) : b(Blocks.BRICKS));
        stackAt(x, z, sc(18, 9));
    }

    /** a round brick smokestack, iron bands, smoking from the top */
    private void stackAt(int x, int z, int hh) {
        double rr = Math.max(1, 1.6 * u);
        for (int y = 1; y <= hh; y++) {
            double rad = rr * (1.15 - 0.25 * y / hh);
            boolean band = Math.floorMod(y, Math.max(3, (int) (4 * u))) == 0;
            for (int dx = (int) -Math.ceil(rad); dx <= Math.ceil(rad); dx++)
                for (int dz = (int) -Math.ceil(rad); dz <= Math.ceil(rad); dz++) {
                    double e = Math.hypot(dx, dz);
                    if (e > rad + 0.3) continue;
                    boolean inside = e < rad - 0.9;
                    put(x + dx, y, z + dz, inside ? (y == hh ? null : b(Blocks.AIR)) : band ? b(Blocks.WAXED_EXPOSED_CUT_COPPER)
                            : rnd(x + dx, y, z + dz, 197) < 0.8 ? b(Blocks.BRICKS) : b(Blocks.MUD_BRICKS));
                }
        }
        put(x, hh - 1, z, b(Blocks.HAY_BLOCK));
        put(x, hh, z, with(lit(Blocks.CAMPFIRE), BlockStateProperties.SIGNAL_FIRE, true));
    }

    /** a heap of scrap */
    private void scrap(int x, int z, double size) {
        double rr = Math.max(2, 3.5 * u * size), hh = Math.max(1.5, 2.5 * u * size);
        ball(x, 0, z, rr, hh, rr * 0.8, (bx, by, bz) -> {
            if (by < 1) return null;
            double k = rnd(bx, by, bz, 198);
            return k < 0.18 ? b(Blocks.IRON_BLOCK) : k < 0.3 ? b(Blocks.WAXED_WEATHERED_COPPER)
                    : k < 0.4 ? b(Blocks.WAXED_OXIDIZED_CUT_COPPER) : k < 0.5 ? b(Blocks.WAXED_EXPOSED_COPPER)
                    : k < 0.58 ? b(Blocks.ANVIL) : k < 0.66 ? b(Blocks.CAULDRON) : k < 0.72 ? b(Blocks.HOPPER)
                    : k < 0.8 ? b(Blocks.IRON_BARS) : k < 0.86 ? b(Blocks.CHAIN) : k < 0.92 ? b(Blocks.GRAVEL)
                    : b(Blocks.WAXED_COPPER_GRATE);
        });
        // lightning rods and chains sticking up out of it
        int spikes = Math.max(2, (int) Math.round(3 * u * size));
        for (int i = 0; i < spikes; i++) {
            int sx = x + (int) Math.round((rnd(i, x, 199) - 0.5) * rr), sz = z + (int) Math.round((rnd(i, z, 199) - 0.5) * rr);
            int y = 1;
            while (get(sx, y, sz) != null && !get(sx, y, sz).isAir() && y < 40) y++;
            int n = 1 + (int) (rnd(i, 5, 199) * 2);
            for (int k = 0; k < n; k++) put(sx, y + k, sz, i % 2 == 0 ? b(Blocks.LIGHTNING_ROD) : b(Blocks.CHAIN));
        }
    }

    /** a fallen robot's head (glowing eyes, a grille mouth, an aerial) and its arm, claw open */
    private void robot(int x, int z, double a) {
        int s = sc(2.5, 1);
        double fx = -Math.cos(a), fz = -Math.sin(a);
        Direction face = Direction.getNearest(fx, 0, fz);
        int hx = face.getStepX(), hz = face.getStepZ();
        for (int dx = -s; dx <= s; dx++)
            for (int dz = -s; dz <= s; dz++)
                for (int y = 1; y <= 2 * s + 1; y++) {
                    boolean front = dx * hx + dz * hz == s;
                    int across = hx != 0 ? dz : dx;
                    BlockState st = y == 2 * s + 1 || y == 1 ? b(Blocks.WAXED_WEATHERED_CUT_COPPER)
                            : (Math.abs(dx) == s && Math.abs(dz) == s) ? b(Blocks.POLISHED_DEEPSLATE)
                            : rnd(x + dx, y, z + dz, 200) < 0.3 ? b(Blocks.WAXED_EXPOSED_COPPER) : b(Blocks.IRON_BLOCK);
                    if (front && y == s + 1 + (s > 1 ? 1 : 0) && Math.abs(across) == Math.max(1, s / 2 + (s > 2 ? 1 : 0)))
                        st = lit(Blocks.WAXED_COPPER_BULB);
                    else if (front && y == Math.max(2, s / 2 + 1) && Math.abs(across) < s) st = b(Blocks.IRON_BARS);
                    put(x + dx, y, z + dz, st);
                }
        put(x, 2 * s + 2, z, b(Blocks.LIGHTNING_ROD));
        put(x, 2 * s + 3, z, facing(Blocks.END_ROD, Direction.UP));
        // the arm off to the side
        Direction side = face.getClockWise();
        int len = sc(7, 3);
        for (int i = s + 2; i <= s + 1 + len; i++) {
            int ax = x + side.getStepX() * i, az = z + side.getStepZ() * i;
            put(ax, 1, az, i == s + 1 + len / 2 ? b(Blocks.WAXED_COPPER_BLOCK) : b(Blocks.IRON_BLOCK));
        }
        int cx = x + side.getStepX() * (s + 2 + len), cz = z + side.getStepZ() * (s + 2 + len);
        put(cx, 1, cz, b(Blocks.IRON_BARS));
        put(cx + face.getStepX(), 1, cz + face.getStepZ(), b(Blocks.IRON_BARS));
        put(cx - face.getStepX(), 1, cz - face.getStepZ(), b(Blocks.IRON_BARS));
        put(cx + side.getStepX() + face.getStepX(), 1, cz + side.getStepZ() + face.getStepZ(), b(Blocks.IRON_BARS));
        put(cx + side.getStepX() - face.getStepX(), 1, cz + side.getStepZ() - face.getStepZ(), b(Blocks.IRON_BARS));
    }

    /** a gantry crane: two legs, a beam, a chain with a load on it */
    private void crane(int x, int z, double a) {
        int hh = sc(11, 6), span = sc(5, 3);
        double px = -Math.sin(a), pz = Math.cos(a);
        int[] ex = new int[2], ez = new int[2];
        for (int k = 0; k < 2; k++) {
            int side = k == 0 ? -1 : 1;
            ex[k] = (int) Math.round(x + px * span * side);
            ez[k] = (int) Math.round(z + pz * span * side);
            for (int y = 1; y <= hh; y++) {
                put(ex[k], y, ez[k], y % 4 == 0 ? b(Blocks.WAXED_CUT_COPPER) : b(Blocks.IRON_BLOCK));
                if (u >= 1.5) put(ex[k] + 1, y, ez[k], b(Blocks.IRON_BARS));
            }
        }
        line(ex[0], hh + 1, ez[0], ex[1], hh + 1, ez[1], 0, (bx, by, bz) -> b(Blocks.WAXED_WEATHERED_CUT_COPPER));
        int drop = hh - sc(4, 2);
        for (int y = hh; y > hh - drop; y--) put(x, y, z, b(Blocks.CHAIN));
        int ly = hh - drop;
        put(x, ly, z, b(Blocks.IRON_BLOCK));
        put(x, ly - 1, z, facing(Blocks.LIGHTNING_ROD, Direction.DOWN));
        scrap(x, z, 0.5);
    }

    /** the glowing core in an iron cage */
    private void core(int x, int z) {
        int s = sc(1.5, 1);
        for (int dx = -s - 1; dx <= s + 1; dx++)
            for (int dz = -s - 1; dz <= s + 1; dz++)
                for (int y = 1; y <= 2 * s + 3; y++) {
                    boolean shell = Math.abs(dx) == s + 1 || Math.abs(dz) == s + 1 || y == 1 || y == 2 * s + 3;
                    boolean in = Math.abs(dx) <= s - 0 && Math.abs(dz) <= s && y >= 2 && y <= 2 * s + 2
                            && Math.hypot(dx, y - s - 2) <= s + 0.4 && Math.hypot(dz, y - s - 2) <= s + 0.4;
                    if (shell) put(x + dx, y, z + dz, y == 1 || y == 2 * s + 3 ? b(Blocks.WAXED_OXIDIZED_CUT_COPPER)
                            : (Math.abs(dx) == s + 1 && Math.abs(dz) == s + 1) ? b(Blocks.IRON_BLOCK) : b(Blocks.IRON_BARS));
                    else if (in) put(x + dx, y, z + dz, rnd(dx, y, dz, 201) < 0.6 ? b(Blocks.OCHRE_FROGLIGHT)
                            : b(Blocks.SHROOMLIGHT));
                }
    }
}
