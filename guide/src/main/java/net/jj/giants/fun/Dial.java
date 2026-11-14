package net.jj.giants.fun;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Tickmoor's den: the Great Dial of his Stillfields. A clock face laid in the ground, old ivory worn through to moss in
 * places, with a copper rim, sixty minute marks, twelve dark hour bars (twelve doubled, to the north) and two black
 * hands stopped at ten to twelve over a copper boss. Round it twelve standing stones, one for each hour: new white
 * quartz on the east side, old mossy stone on the west, and the four at twelve, three, six and nine are arches; one old
 * one has fallen. Between them sundials, and great copper gears half sunk in the grass. Round the edge, trees of every
 * season side by side (pink cherry, birch in flower, amber oak, bare spruce in snow), and in one patch the rain hangs
 * still in the air.
 */
final class Dial extends Plan {
    /** the dial's radius, and the stones' ring */
    private final double dial, stones;
    /** the hands, clockwise from twelve (north): the minute hand at ten to, the hour hand nearly at twelve */
    private static final double MINUTE = Math.toRadians(300), HOUR = Math.toRadians(355);

    Dial(int r, double h) {
        super(r, h);
        dial = r * 0.56;
        stones = r * 0.68;
    }

    @Override
    public int reach() {
        // room for the trees' crowns past the edge
        return r + 2 + sc(4, 2);
    }

    @Override
    int top(int dx, int dz) {
        return 0;
    }

    /** along and across a hand pointing at angle a (clockwise from north, -z) */
    private static double along(int dx, int dz, double a) {
        return dx * Math.sin(a) - dz * Math.cos(a);
    }

    private static double across(int dx, int dz, double a) {
        return dx * Math.cos(a) + dz * Math.sin(a);
    }

    /** the angle of (dx, dz), clockwise from north, 0 to 2 pi */
    private static double angle(double dx, double dz) {
        return (Math.atan2(dx, -dz) + Math.PI * 2) % (Math.PI * 2);
    }

    @Override
    void ground(int dx, int dz, double d, int f, Builds.Col o) {
        if (d > r + 2) return;
        o.support = b(Blocks.DIRT);
        double k = rnd(dx, dz, 310), n = noise(dx, dz, Math.max(3, 4 * u), 311);
        double rim = Math.max(1.0, dial * 0.05), boss = Math.max(1.2, dial * 0.07);
        BlockState s;
        if (d <= boss) s = d <= boss * 0.55 ? b(Blocks.WAXED_COPPER_BLOCK) : b(Blocks.WAXED_CUT_COPPER);
        else if (d <= dial) {
            double a = angle(dx, dz), al = along(dx, dz, MINUTE), ac = Math.abs(across(dx, dz, MINUTE));
            double hl = along(dx, dz, HOUR), hc = Math.abs(across(dx, dz, HOUR));
            double handW = Math.max(0.5, dial * 0.03);
            // the hour hand: a broad spade near its tip
            double spade = hl > dial * 0.38 && hl < dial * 0.52 ? Math.max(1.0, dial * 0.07) * (1 - Math.abs(hl
                    - dial * 0.45) / (dial * 0.07)) : 0;
            boolean minuteHand = al > 0 && al < dial * 0.84 && ac < handW;
            boolean hourHand = hl > 0 && hl < dial * 0.54 && hc < Math.max(handW * 1.4, spade);
            double hourStep = Math.PI / 6, toHour = Math.abs(a / hourStep - Math.round(a / hourStep)) * hourStep;
            boolean twelve = Math.round(a / hourStep) % 12 == 0;
            double barW = Math.max(0.55, dial * 0.035) * (twelve ? 2.6 : 1);
            boolean hourBar = d > dial * 0.74 && d < dial - rim - 0.5 && toHour * d < barW;
            double minStep = Math.PI / 30, toMin = Math.abs(a / minStep - Math.round(a / minStep)) * minStep;
            boolean minuteMark = d > dial - rim - Math.max(1.5, dial * 0.06) && d < dial - rim - 0.2
                    && toMin * d < Math.max(0.5, dial * 0.012) && dial >= 9;
            if (d > dial - rim) s = d > dial - 0.9 ? b(Blocks.WAXED_OXIDIZED_CUT_COPPER) : b(Blocks.SMOOTH_STONE);
            else if (minuteHand || hourHand) s = k < 0.85 ? b(Blocks.POLISHED_BLACKSTONE) : b(Blocks.BLACKSTONE);
            else if (hourBar) s = k < 0.8 ? b(Blocks.POLISHED_DEEPSLATE) : b(Blocks.DEEPSLATE_TILES);
            else if (minuteMark) s = b(Blocks.DEEPSLATE_TILES);
            // the old ivory face, worn through to moss and earth in patches
            else if (n > 0.72) s = k < 0.6 ? b(Blocks.MOSS_BLOCK) : b(Blocks.GRASS_BLOCK);
            else if (n > 0.64) s = k < 0.5 ? b(Blocks.DIORITE) : b(Blocks.MOSS_BLOCK);
            else s = k < 0.62 ? b(Blocks.SMOOTH_QUARTZ) : k < 0.88 ? b(Blocks.QUARTZ_BRICKS) : b(Blocks.CALCITE);
        } else {
            // the meadow: grass and moss; snow round the winter trees (to the west) and earth under the others
            double a = angle(dx, dz);
            boolean winter = a > Math.PI * 1.25 && a < Math.PI * 1.75 && d > stones;
            if (winter && n > 0.35) s = k < 0.7 ? b(Blocks.SNOW_BLOCK) : b(Blocks.GRASS_BLOCK);
            else if (n > 0.7) s = k < 0.6 ? b(Blocks.MOSS_BLOCK) : b(Blocks.GRASS_BLOCK);
            else if (n < 0.22) s = k < 0.5 ? b(Blocks.PODZOL) : b(Blocks.COARSE_DIRT);
            else s = k < 0.92 ? b(Blocks.GRASS_BLOCK) : b(Blocks.COARSE_DIRT);
            if (d > r) s = k < 0.6 ? b(Blocks.GRASS_BLOCK) : b(Blocks.COARSE_DIRT);
        }
        o.set(f, s);
        o.clearAbove(f + 1);
    }

    @Override
    void plan() {
        // the meadow's grass and flowers, by season; snow lying in the winter quarter
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > r || d <= dial + 0.5) continue;
                double k = rnd(x, z, 312), a = angle(x, z);
                int season = (int) (a / (Math.PI / 2)) % 4;
                boolean winter = a > Math.PI * 1.25 && a < Math.PI * 1.75 && d > stones;
                if (winter) {
                    if (noise(x, z, Math.max(3, 4 * u), 311) > 0.35 || k < 0.3)
                        put(x, 1, z, with(b(Blocks.SNOW), BlockStateProperties.LAYERS, 1 + (int) (rnd(x, z, 313) * 2)));
                    continue;
                }
                if (k < 0.16) put(x, 1, z, b(Blocks.SHORT_GRASS));
                else if (k < 0.2) put(x, 1, z, b(Blocks.FERN));
                else if (k < 0.23) put(x, 1, z, switch (season) {
                    case 0 -> with(with(b(Blocks.PINK_PETALS), BlockStateProperties.FLOWER_AMOUNT, 1 + (int) (rnd(x, z,
                            314) * 4)), BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue((x + z) & 3));
                    case 1 -> b(pick(rnd(x, z, 315), Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY));
                    case 2 -> b(pick(rnd(x, z, 315), Blocks.POPPY, Blocks.ORANGE_TULIP, Blocks.DANDELION));
                    default -> b(Blocks.SHORT_GRASS);
                });
                else if (k < 0.235) put(x, 1, z, b(Blocks.MOSS_CARPET));
            }
        // the twelve hour stones (the gate arches at twelve, three, six and nine)
        int fallen = 7 + (int) (rnd(r, 2, 316) * 3);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            double sx = Math.sin(a) * stones, sz = -Math.cos(a) * stones;
            boolean fresh = Math.sin(a) > 0.01 || (i == 0 && rnd(r, 3, 316) < 0.5);
            if (i % 3 == 0) arch(sx, sz, a, fresh, i);
            else if (i == fallen) lying(sx, sz, a, i);
            else stone(sx, sz, a, fresh, i);
        }
        // sundials and half-sunk gears between the stones and the trees
        double mid = (stones + r * 0.86) / 2;
        int sundials = Mth.clamp((int) Math.round(1 + u), 2, 5);
        for (var s : ring(sundials, mid, 0.4, 317)) sundial((int) Math.round(s[0]), (int) Math.round(s[1]), s[2]);
        int gears = Mth.clamp((int) Math.round(1.5 + u * 1.2), 2, 7);
        for (var s : ring(gears, mid * 1.05, 0.6, 318)) gear(s[0], s[1], s[2] + Math.PI / 2, (int) (s[2] * 1000));
        // the trees of every season round the edge
        int trees = Mth.clamp((int) Math.round(2 * Math.PI * r * 0.86 / Math.max(10, 9 * u)), 6, 24);
        int k = 0;
        for (var s : ring(trees, r * 0.87, 0.4, 319)) {
            int x = (int) Math.round(s[0]), z = (int) Math.round(s[1]);
            double a = angle(x, z);
            // (the gate at six stays open)
            if (Math.abs(a - Math.PI) < 0.2) continue;
            boolean winter = a > Math.PI * 1.25 && a < Math.PI * 1.75;
            int season = winter ? 3 : (int) (a / (Math.PI / 2)) % 3;
            tree(x, z, season, (int) (s[2] * 1000) + k++);
        }
        // a patch where the rain hangs still in the air: long drops, still as glass rods
        double ra = Math.toRadians(150);
        int px = (int) Math.round(Math.sin(ra) * r * 0.78), pz = (int) Math.round(-Math.cos(ra) * r * 0.78);
        int pr = sc(6, 3);
        for (int x = px - pr; x <= px + pr; x++)
            for (int z = pz - pr; z <= pz + pr; z++) {
                if ((x - px) * (x - px) + (z - pz) * (z - pz) > pr * pr || rnd(x, z, 320) > 0.11) continue;
                if (get(x, 1, z) != null && solid(x, 1, z)) continue;
                int y = 3 + (int) (rnd(x, z, 321) * sc(9, 4));
                put(x, y, z, with(b(Blocks.END_ROD), BlockStateProperties.FACING, Direction.UP));
            }
    }

    /** a standing stone for one hour: new quartz, or old stone with moss; a lantern at its foot now and then */
    private void stone(double sx, double sz, double a, boolean fresh, int i) {
        int hh = sc(5, 3) + (int) (rnd(i, 4, 322) * sc(2, 1));
        int w = u >= 1.4 ? 1 : 0;
        double tx = Math.cos(a), tz = Math.sin(a);
        for (int y = 1; y <= hh; y++)
            for (int j = -w; j <= w; j++) {
                int x = (int) Math.round(sx + tx * j), z = (int) Math.round(sz + tz * j);
                double q = rnd(x, y, z, 323);
                BlockState st = fresh ? (y == hh ? b(Blocks.CHISELED_QUARTZ_BLOCK)
                        : with(b(Blocks.QUARTZ_PILLAR), BlockStateProperties.AXIS, Direction.Axis.Y))
                        : q < 0.4 ? b(Blocks.MOSSY_STONE_BRICKS) : q < 0.7 ? b(Blocks.CRACKED_STONE_BRICKS)
                        : q < 0.85 ? b(Blocks.STONE_BRICKS) : b(Blocks.MOSSY_COBBLESTONE);
                // the hour's mark, cut into its inner face
                if (y == hh - 1 && j == 0 && hh > 3) st = fresh ? b(Blocks.CHISELED_QUARTZ_BLOCK)
                        : b(Blocks.CHISELED_STONE_BRICKS);
                put(x, y, z, st);
            }
        if (!fresh) put((int) Math.round(sx), hh + 1, (int) Math.round(sz), b(Blocks.MOSS_CARPET));
        if (i % 2 == 1) {
            double ix = sx - Math.sin(a) * (w + 1.6), iz = sz + Math.cos(a) * (w + 1.6);
            put((int) Math.round(ix), 1, (int) Math.round(iz), b(Blocks.LANTERN));
        }
    }

    /** a gate arch over the hour's line: two pillars and a lintel across */
    private void arch(double sx, double sz, double a, boolean fresh, int i) {
        int hh = sc(7, 4), half = Math.max(1, sc(2.5, 1));
        double tx = Math.cos(a), tz = Math.sin(a);
        for (int side = -1; side <= 1; side += 2) {
            double px = sx + tx * half * side, pz = sz + tz * half * side;
            for (int y = 1; y <= hh; y++) {
                int x = (int) Math.round(px), z = (int) Math.round(pz);
                double q = rnd(x, y, z, 324);
                // the ancient side crumbles: an old pillar may stop short
                if (!fresh && side > 0 && y > hh - 1 && q < 0.5) continue;
                put(x, y, z, fresh ? with(b(Blocks.QUARTZ_PILLAR), BlockStateProperties.AXIS, Direction.Axis.Y)
                        : q < 0.5 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.CRACKED_STONE_BRICKS));
            }
        }
        int y = hh + 1;
        line(sx - tx * (half + 1), y, sz - tz * (half + 1), sx + tx * (half + 1), y, sz + tz * (half + 1), 0,
                (x, yy, z) -> fresh ? (rnd(x, z, 325) < 0.5 ? b(Blocks.QUARTZ_BRICKS) : b(Blocks.SMOOTH_QUARTZ))
                        : rnd(x, z, 325) < 0.6 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.STONE_BRICKS));
        // a clock in the lintel's middle, facing in, and a lantern hanging under it
        int cx = (int) Math.round(sx), cz = (int) Math.round(sz);
        put(cx, y + 1, cz, fresh ? b(Blocks.CHISELED_QUARTZ_BLOCK) : b(Blocks.CHISELED_STONE_BRICKS));
        if (hh > 4) hangLantern(cx, y, cz, 1, false);
    }

    private void lying(double sx, double sz, double a, int i) {
        int len = sc(5, 3);
        double tx = -Math.sin(a), tz = Math.cos(a);
        for (int kk = 0; kk < len; kk++) {
            int x = (int) Math.round(sx + tx * kk), z = (int) Math.round(sz + tz * kk);
            put(x, 1, z, rnd(x, kk, z, 326) < 0.5 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.CRACKED_STONE_BRICKS));
            if (rnd(x, z, 327) < 0.5) put(x, 2, z, b(Blocks.MOSS_CARPET));
        }
    }

    /** a sundial: a carved plinth, a stone top and a copper pointer */
    private void sundial(int x, int z, double a) {
        int hh = Math.max(1, sc(1.5, 1));
        for (int y = 1; y <= hh; y++) put(x, y, z, b(Blocks.CHISELED_STONE_BRICKS));
        if (u >= 1.2) for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if (dx != 0 || dz != 0) put(x + dx, hh, z + dz, slab(Blocks.SMOOTH_STONE_SLAB, true));
        put(x, hh + 1, z, b(Blocks.LIGHTNING_ROD));
        put(x, hh + 2, z, b(Blocks.LIGHTNING_ROD));
    }

    /** a great copper gear standing on its edge, half sunk in the grass, teeth round it, a hub in the middle */
    private void gear(double gx, double gz, double face, int salt) {
        int rad = sc(3.5, 2);
        double tx = Math.cos(face), tz = Math.sin(face);
        double sink = rad * (0.25 + rnd(salt, 1, 328) * 0.3);
        BlockState rim = b(pick(rnd(salt, 2, 328), Blocks.WAXED_EXPOSED_CUT_COPPER, Blocks.WAXED_WEATHERED_CUT_COPPER,
                Blocks.WAXED_OXIDIZED_CUT_COPPER));
        int teeth = Math.max(6, rad * 3);
        for (int i = 0; i < teeth * 4; i++) {
            double t = i * Math.PI * 2 / (teeth * 4);
            boolean tooth = (i / 2) % 2 == 0;
            double rr = rad + (tooth ? 1 : 0);
            double x = gx + tx * Math.cos(t) * rr, y = Math.sin(t) * rr - sink, z = gz + tz * Math.cos(t) * rr;
            if (y < 0.5) continue;
            put((int) Math.round(x), (int) Math.round(y), (int) Math.round(z), rim);
        }
        // spokes and the hub
        for (int sp = 0; sp < 4; sp++) {
            double t = sp * Math.PI / 2 + 0.4;
            line(gx, -sink, gz, gx + tx * Math.cos(t) * rad, Math.sin(t) * rad - sink, gz + tz * Math.cos(t) * rad, 0,
                    (x, y, z) -> y >= 1 ? b(Blocks.WAXED_CUT_COPPER) : null);
        }
        if (sink < 0.5) put((int) Math.round(gx), 1, (int) Math.round(gz), b(Blocks.WAXED_COPPER_BLOCK));
        put((int) Math.round(gx + tx * rad), 1, (int) Math.round(gz + tz * rad), b(Blocks.MOSS_CARPET));
    }

    /** a tree of one season: 0 pink cherry, 1 birch in flower, 2 amber oak, 3 bare spruce in snow */
    private void tree(int x, int z, int season, int salt) {
        int hh = sc(7, 4) + (int) (rnd(salt, 1, 329) * sc(3, 1));
        Block log = switch (season) {
            case 0 -> Blocks.CHERRY_LOG;
            case 1 -> Blocks.BIRCH_LOG;
            case 2 -> Blocks.OAK_LOG;
            default -> Blocks.SPRUCE_LOG;
        };
        for (int y = 1; y <= hh; y++) put(x, y, z, log(log, Direction.Axis.Y));
        if (season == 3) {
            // bare: short limbs out and up, snow on their tops
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + rnd(salt, i, 330);
                int y0 = hh / 2 + i * Math.max(1, hh / 8);
                double l = Math.max(2, 2.5 * u);
                int ex = (int) Math.round(x + Math.cos(a) * l), ez = (int) Math.round(z + Math.sin(a) * l);
                Direction.Axis ax = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? Direction.Axis.X : Direction.Axis.Z;
                line(x, y0, z, ex, y0 + l * 0.5, ez, 0, (bx, by, bz) -> log(Blocks.SPRUCE_LOG, ax));
                put(ex, (int) Math.round(y0 + l * 0.5) + 1, ez, with(b(Blocks.SNOW), BlockStateProperties.LAYERS, 2));
            }
            put(x, hh + 1, z, with(b(Blocks.SNOW), BlockStateProperties.LAYERS, 3));
            return;
        }
        // a round crown
        double cr = Math.max(2, 3 * u) * (0.85 + rnd(salt, 2, 329) * 0.3);
        double cy = hh + cr * 0.35;
        ball(x, cy, z, cr, cr * 0.75, cr, (bx, by, bz) -> {
            double q = rnd(bx, by, bz, 331);
            if (q < 0.12) return null;
            return switch (season) {
                case 0 -> leaves(Blocks.CHERRY_LEAVES);
                case 1 -> q < 0.55 ? leaves(Blocks.FLOWERING_AZALEA_LEAVES) : leaves(Blocks.BIRCH_LEAVES);
                // amber: glowing amber in his autumn oak's leaves
                default -> q < 0.22 ? b(Blocks.SHROOMLIGHT) : leaves(Blocks.OAK_LEAVES);
            };
        });
        // fallen blossom under the cherries
        if (season == 0) for (int i = 0; i < 6; i++) {
            int fx = x + (int) Math.round((rnd(salt, i, 332) - 0.5) * cr * 2.4);
            int fz = z + (int) Math.round((rnd(salt, i + 9, 332) - 0.5) * cr * 2.4);
            soft(fx, 1, fz, with(b(Blocks.PINK_PETALS), BlockStateProperties.FLOWER_AMOUNT, 4));
        }
    }
}
