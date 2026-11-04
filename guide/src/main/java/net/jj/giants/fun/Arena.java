package net.jj.giants.fun;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * (Part 7, ADMIN_BRIDGE.md 23.3) The arena: a round, dead flat floor with nothing standing on it (a compass star in
 * the middle, eight spokes, wedges of two tilings, a ring of the four sides' colours, older and mossier toward the
 * wall), a stone wall round it with banners and torches on its inside, pillars with fire bowls, four open gates
 * between towers flying their side's flag, rows of stone seats with aisles outside the wall, flag poles and lanterns
 * along the back, and a scoreboard over the north stands. Each quarter has a colour: north red, east blue, south
 * yellow, west green.
 */
final class Arena extends Plan {
    private final int rows, wallH, gate, back;

    Arena(int r) {
        super(r, 0);
        rows = r >= 90 ? 6 : r >= 60 ? 5 : 4;
        wallH = r >= 90 ? 7 : r >= 60 ? 6 : 5;
        gate = r >= 90 ? 4 : r >= 60 ? 3 : 2;
        back = r + 2 + rows * 2;
    }

    @Override
    public int reach() {
        return back + 6;
    }

    @Override
    int top(int dx, int dz) {
        return 0;
    }

    /** 0 north, 1 east, 2 south, 3 west */
    static int side(double dx, double dz) {
        Direction d = Direction.getNearest(dx, 0, dz);
        return d == Direction.NORTH ? 0 : d == Direction.EAST ? 1 : d == Direction.SOUTH ? 2 : 3;
    }

    static Block colour(int side, String what) {
        return switch (what) {
            case "concrete" -> new Block[] {Blocks.RED_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.YELLOW_CONCRETE,
                    Blocks.GREEN_CONCRETE}[side];
            case "wall_banner" -> new Block[] {Blocks.RED_WALL_BANNER, Blocks.BLUE_WALL_BANNER,
                    Blocks.YELLOW_WALL_BANNER, Blocks.GREEN_WALL_BANNER}[side];
            case "banner" -> new Block[] {Blocks.RED_BANNER, Blocks.BLUE_BANNER, Blocks.YELLOW_BANNER,
                    Blocks.GREEN_BANNER}[side];
            default -> new Block[] {Blocks.RED_TERRACOTTA, Blocks.BLUE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA,
                    Blocks.GREEN_TERRACOTTA}[side];
        };
    }

    private boolean gateway(int dx, int dz) {
        return Math.abs(dx) <= gate || Math.abs(dz) <= gate;
    }

    @Override
    void ground(int dx, int dz, double d, int f, Builds.Col o) {
        double ang = Math.atan2(dz, dx);
        o.support = b(Blocks.STONE_BRICKS);
        if (d <= r) {
            o.set(f, floor(dx, dz, d, ang));
            o.clearAbove(f + 1);
        } else if (d <= r + 2) {
            o.set(f, b(Blocks.STONE_BRICKS));
            if (gateway(dx, dz)) {
                o.set(f, b(Blocks.POLISHED_ANDESITE));
                o.clearAbove(f + 1);
                return;
            }
            for (int y = 1; y <= wallH; y++) {
                double k = rnd(dx, y, dz, 230);
                o.set(f + y, y == 1 ? b(Blocks.POLISHED_ANDESITE) : y == wallH ? b(Blocks.SMOOTH_STONE)
                        : k < 0.08 ? b(Blocks.CRACKED_STONE_BRICKS) : k < 0.14 ? b(Blocks.MOSSY_STONE_BRICKS)
                        : b(Blocks.STONE_BRICKS));
            }
            // battlements along the outer edge
            int along = (int) Math.floor((ang + Math.PI) * (r + 2));
            if (d > r + 1 && (along & 1) == 0) o.set(f + wallH + 1, b(Blocks.STONE_BRICK_WALL));
            o.clearAbove(f + wallH + 2);
        } else if (d <= back) {
            if (gateway(dx, dz)) {
                o.set(f, b(Blocks.POLISHED_ANDESITE));
                o.clearAbove(f + 1);
                return;
            }
            int row = Math.min(rows - 1, (int) ((d - (r + 2)) / 2));
            boolean inner = (d - (r + 2)) - row * 2 < 1;
            int hh = row + 1;
            double aDeg = Math.toDegrees(ang) + 180;
            boolean aisle = Math.abs(Mth.wrapDegrees(aDeg - 15 - Math.round((aDeg - 15) / 30) * 30)) * Math.PI / 180 * d < 1.2;
            Direction out = inward(dx, dz).getOpposite();
            for (int y = 0; y < hh; y++) o.set(f + y, b(Blocks.STONE_BRICKS));
            if (aisle) o.set(f + hh, stair(Blocks.POLISHED_ANDESITE_STAIRS, out, false));
            else if (inner) o.set(f + hh, stair(Blocks.STONE_BRICK_STAIRS, out, false));
            else o.set(f + hh, rnd(dx, dz, 231) < 0.1 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.STONE_BRICKS));
            if (aisle && inner) o.set(f + hh, b(Blocks.POLISHED_ANDESITE));
            if (d > back - 1 && !aisle) o.set(f + hh + 1, b(Blocks.STONE_BRICK_WALL));
            o.clearAbove(f + hh + (d > back - 1 ? 2 : 1));
        }
    }

    private BlockState floor(int dx, int dz, double d, double ang) {
        double star = Math.max(4, r * 0.12);
        if (d < 1.5) return b(Blocks.CHISELED_STONE_BRICKS);
        if (d < star + 1) {
            double c = Math.cos(4 * ang);
            double rs = star * (0.3 + 0.7 * Math.pow(Math.abs(c), 6));
            if (d < rs) return c > 0 ? b(Blocks.POLISHED_DEEPSLATE) : b(Blocks.POLISHED_ANDESITE);
            if (d > star) return b(Blocks.POLISHED_BLACKSTONE_BRICKS);
            return b(Blocks.SMOOTH_STONE);
        }
        // the ring of the four colours
        if (Math.abs(d - r * 0.62) < 0.8) return b(colour(side(dx, dz), "terracotta"));
        if (Math.abs(d - r * 0.62) < 1.8 || Math.abs(d - (r - 1.5)) < 0.7 || Math.abs(d - r * 0.33) < 0.7)
            return b(Blocks.POLISHED_ANDESITE);
        // eight spokes
        double sp = Math.abs(Mth.wrapDegrees(Math.toDegrees(ang) - Math.round(Math.toDegrees(ang) / 45) * 45));
        if (sp * Math.PI / 180 * d < 0.6) return b(Blocks.POLISHED_ANDESITE);
        // wedges: two tilings by turns, wearing toward the wall
        int wedge = Math.floorMod((int) Math.floor(Math.toDegrees(ang) / 45), 2);
        double k = rnd(dx, dz, 232), wear = Math.max(0, (d / r - 0.6)) * 0.4;
        if (k < 0.05 + wear * 0.5) return b(Blocks.CRACKED_STONE_BRICKS);
        if (k < 0.08 + wear) return b(Blocks.MOSSY_STONE_BRICKS);
        if (wedge == 0) return b(Blocks.STONE_BRICKS);
        return Math.floorMod(Math.floorDiv(dx, 2) + Math.floorDiv(dz, 2), 2) == 0 ? b(Blocks.SMOOTH_STONE)
                : b(Blocks.POLISHED_ANDESITE);
    }

    @Override
    void plan() {
        // pillars round the wall (between the gates), fire bowls or lanterns on top
        for (int k = 0; k < 12; k++) {
            double a = Math.toRadians(15 + 30 * k);
            int px = (int) Math.round(Math.cos(a) * (r + 1.6)), pz = (int) Math.round(Math.sin(a) * (r + 1.6));
            int top = wallH + 3;
            for (int dx = -1; dx <= 0; dx++)
                for (int dz = -1; dz <= 0; dz++)
                    for (int y = 1; y <= top; y++)
                        if (Math.hypot(px + dx, pz + dz) > r + 0.5) put(px + dx, y, pz + dz, y == 1 ? b(Blocks.POLISHED_DEEPSLATE)
                                : y == wallH - 1 ? b(Blocks.CHISELED_DEEPSLATE)
                                : y == top ? b(Blocks.POLISHED_DEEPSLATE) : b(Blocks.DEEPSLATE_BRICKS));
            put(px, top + 1, pz, r >= 60 ? lit(Blocks.CAMPFIRE) : b(Blocks.LANTERN));
            if (r >= 60) {
                put(px - 1, top + 1, pz, b(Blocks.LANTERN));
                put(px, top + 1, pz - 1, b(Blocks.LANTERN));
            }
        }
        // banners and torches on the inside of the wall
        int every = r >= 60 ? 6 : 5;
        for (int i = 0; i < (int) (2 * Math.PI * r / every); i++) {
            double a = i * every / (double) r;
            int x = (int) Math.round(Math.cos(a) * (r + 0.5)), z = (int) Math.round(Math.sin(a) * (r + 0.5));
            int wx = (int) Math.round(Math.cos(a) * (r + 1.2)), wz = (int) Math.round(Math.sin(a) * (r + 1.2));
            if (gateway(x, z) || Math.hypot(x, z) > r + 0.5 || get(wx, 3, wz) != null) continue;
            Direction in = inward(x, z);
            int s = side(x, z);
            if ((i & 1) == 0) put(x, wallH - 1, z, facing(colour(s, "wall_banner"), in));
            else put(x, 3, z, facing(Blocks.WALL_TORCH, in));
        }
        // the gate towers, flags flying
        for (int s = 0; s < 4; s++) {
            Direction d = new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}[s];
            Direction across = d.getClockWise();
            for (int side = -1; side <= 1; side += 2) {
                int cx = d.getStepX() * (r + 2) + across.getStepX() * side * (gate + 2);
                int cz = d.getStepZ() * (r + 2) + across.getStepZ() * side * (gate + 2);
                int hh = wallH + 4;
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int y = 1; y <= hh; y++)
                            if (Math.hypot(cx + dx, cz + dz) > r + 0.5) put(cx + dx, y, cz + dz, y == 1 ? b(Blocks.POLISHED_DEEPSLATE) : y == hh
                                    ? b(Blocks.POLISHED_DEEPSLATE) : rnd(cx + dx, y, cz + dz, 233) < 0.15
                                    ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : b(Blocks.DEEPSLATE_BRICKS));
                        if ((dx + dz & 1) == 0 && (dx != 0 || dz != 0)) put(cx + dx, hh + 1, cz + dz,
                                b(Blocks.POLISHED_BLACKSTONE_BRICK_WALL));
                    }
                put(cx, hh + 1, cz, b(Blocks.OAK_FENCE));
                put(cx, hh + 2, cz, b(Blocks.OAK_FENCE));
                put(cx, hh + 3, cz, with(b(colour(s, "banner")), BlockStateProperties.ROTATION_16, rot16(cx, cz, 0, 0)));
                // a torch on each tower's face toward the gateway
                Direction toGate = side > 0 ? across.getOpposite() : across;
                put(cx + toGate.getStepX() * 2, 3, cz + toGate.getStepZ() * 2, facing(Blocks.WALL_TORCH, toGate));
                put(cx + toGate.getStepX() * 2, wallH + 1, cz + toGate.getStepZ() * 2, facing(Blocks.WALL_TORCH, toGate));
            }
        }
        // flag poles and lanterns along the back of the stands
        for (int k = 0; k < 24; k++) {
            double a = Math.toRadians(7.5 + 15 * k);
            int x = (int) Math.round(Math.cos(a) * (back - 0.5)), z = (int) Math.round(Math.sin(a) * (back - 0.5));
            if (gateway(x, z)) continue;
            int y = rows + 1;
            if (k % 2 == 0) {
                for (int i = 1; i <= 4; i++) put(x, y + i, z, b(Blocks.SPRUCE_FENCE));
                put(x, y + 5, z, with(b(colour(side(x, z), "banner")), BlockStateProperties.ROTATION_16,
                        rot16(x, z, 0, 0)));
            } else {
                put(x, y + 1, z, b(Blocks.SPRUCE_FENCE));
                put(x, y + 2, z, b(Blocks.LANTERN));
            }
        }
        scoreboard();
    }

    /** a scoreboard over the north stands facing in: a framed black board, two lit scores either side of a "VS" */
    private void scoreboard() {
        String[] digit = {"111", "101", "101", "101", "111"};
        String[] v = {"101", "101", "101", "101", "010"}, s = {"111", "100", "111", "001", "111"};
        int z = -(back + 2);
        int w = 27, hh = 9, y0 = rows + 4;
        int x0 = -w / 2;
        for (int x = x0 - 1; x <= x0 + w; x++)
            for (int y = y0 - 1; y <= y0 + hh; y++) {
                boolean frame = x == x0 - 1 || x == x0 + w || y == y0 - 1 || y == y0 + hh;
                put(x, y, z, frame ? b(Blocks.POLISHED_BLACKSTONE_BRICKS) : b(Blocks.BLACK_CONCRETE));
                put(x, y, z - 1, b(Blocks.POLISHED_BLACKSTONE));
            }
        // legs
        for (int side = -1; side <= 1; side += 2)
            for (int y = 1; y < y0 - 1; y++) {
                put(side * (w / 2 - 2), y, z - 1, b(Blocks.POLISHED_BLACKSTONE_BRICKS));
                put(side * (w / 2 - 2), y, z, b(Blocks.POLISHED_BLACKSTONE_BRICK_WALL));
            }
        // the left (red) score "00", "VS", the right (blue) score "00", on the inner face
        int top = y0 + hh - 2;
        int[] at = {x0 + 2, x0 + 6, x0 + 11, x0 + 14, x0 + 19, x0 + 23};
        String[][] glyph = {digit, digit, v, s, digit, digit};
        BlockState[] lights = {b(Blocks.SHROOMLIGHT), b(Blocks.SHROOMLIGHT), b(Blocks.WHITE_CONCRETE),
                b(Blocks.WHITE_CONCRETE), b(Blocks.SEA_LANTERN), b(Blocks.SEA_LANTERN)};
        for (int g = 0; g < 6; g++)
            for (int row = 0; row < 5; row++)
                for (int c = 0; c < 3; c++)
                    if (glyph[g][row].charAt(c) == '1') put(at[g] + c, top - row - 1, z, lights[g]);
        // the sides' colours along the bottom, a banner either end
        for (int x = x0; x < x0 + w; x++) put(x, y0, z, b(x < 0 ? Blocks.RED_CONCRETE : x > 0 ? Blocks.BLUE_CONCRETE
                : Blocks.WHITE_CONCRETE));
        put(x0 - 1, y0 + hh + 1, z, with(b(Blocks.RED_BANNER), BlockStateProperties.ROTATION_16, 0));
        put(x0 + w, y0 + hh + 1, z, with(b(Blocks.BLUE_BANNER), BlockStateProperties.ROTATION_16, 0));
    }
}
