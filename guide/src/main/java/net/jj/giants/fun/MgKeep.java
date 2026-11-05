package net.jj.giants.fun;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * (Part 10A, Siege) The keep the players hold: a round stone wall with four open gates and a walkway on its inside
 * (ladders up to it), a trodden yard of dirt and gravel paths, and in the middle a square tower with a door, three
 * floors joined by a ladder, arrow slits, battlements on top and a flag and a fire bowl over it all. r round.
 */
final class MgKeep extends Plan {
    private final int wallH = 6, half = 4, towerH = 15;

    MgKeep(int r) {
        super(r, 0);
    }

    @Override
    public int reach() {
        return r + 4;
    }

    @Override
    int top(int dx, int dz) {
        return 0;
    }

    private boolean gateway(int dx, int dz) {
        return Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
    }

    @Override
    void ground(int dx, int dz, double d, int f, Builds.Col o) {
        if (d > r + 3.5) return;
        o.support = b(Blocks.STONE);
        double k = rnd(dx, dz, 301), n = noise(dx, dz, 6, 302);
        BlockState s;
        if (d > r + 1.5) s = k < 0.5 ? b(Blocks.COARSE_DIRT) : b(Blocks.GRASS_BLOCK);
        else if (gateway(dx, dz) && d > half + 1) s = k < 0.7 ? b(Blocks.GRAVEL) : b(Blocks.COBBLESTONE);
        else if (Math.abs(d - (half + 3)) < 1.2) s = k < 0.75 ? b(Blocks.COBBLESTONE) : b(Blocks.MOSSY_COBBLESTONE);
        else s = n < 0.35 ? b(Blocks.COARSE_DIRT) : n < 0.5 ? b(Blocks.PACKED_MUD) : k < 0.1 ? b(Blocks.GRAVEL)
                : b(Blocks.DIRT_PATH);
        // (gravel only on solid ground: it never falls)
        o.set(f, s);
        o.clearAbove(f + 1);
    }

    private BlockState brick(int x, int y, int z) {
        double k = rnd(x, y, z, 303);
        return k < 0.1 ? b(Blocks.CRACKED_STONE_BRICKS) : k < 0.2 ? b(Blocks.MOSSY_STONE_BRICKS) : b(Blocks.STONE_BRICKS);
    }

    @Override
    void plan() {
        // the wall, its battlements and the walkway inside
        for (int x = -r - 2; x <= r + 2; x++)
            for (int z = -r - 2; z <= r + 2; z++) {
                double d = Math.hypot(x, z);
                if (d >= r && d < r + 1.8) {
                    if (gateway(x, z)) continue;
                    for (int y = 1; y <= wallH; y++)
                        put(x, y, z, y == 1 ? b(Blocks.POLISHED_ANDESITE) : brick(x, y, z));
                    double ang = Math.atan2(z, x);
                    int along = (int) Math.floor((ang + Math.PI) * (r + 1));
                    if (d > r + 0.8 && (along & 1) == 0) put(x, wallH + 1, z, b(Blocks.STONE_BRICK_WALL));
                } else if (d >= r - 2.2 && d < r && !gateway(x, z)) {
                    put(x, wallH - 2, z, slab(Blocks.SPRUCE_SLAB, true));
                }
            }
        // ladders up to the walkway, between the gates
        for (int q = 0; q < 4; q++) {
            double a = Math.toRadians(45 + 90 * q);
            int x = (int) Math.round(Math.cos(a) * (r - 2.6)), z = (int) Math.round(Math.sin(a) * (r - 2.6));
            Direction in = inward(x, z);
            for (int y = 1; y <= wallH - 2; y++) {
                put(x, y, z, b(Blocks.SPRUCE_PLANKS));
                put(x + in.getStepX(), y, z + in.getStepZ(), facing(Blocks.LADDER, in));
            }
        }
        // the gate posts: a torch either side, a banner over each gate
        for (Direction d : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            Direction across = d.getClockWise();
            for (int side = -1; side <= 1; side += 2) {
                int x = d.getStepX() * r + across.getStepX() * side * 2, z = d.getStepZ() * r + across.getStepZ() * side * 2;
                for (int y = 1; y <= wallH + 2; y++) put(x, y, z, y == wallH + 2 ? b(Blocks.CHISELED_STONE_BRICKS)
                        : b(Blocks.POLISHED_DEEPSLATE));
                put(x, wallH + 3, z, b(Blocks.LANTERN));
            }
        }
        tower();
        // the yard: hay, barrels, a well, a cart of logs
        int w = half + 6;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                boolean rim = dx != 0 || dz != 0;
                put(w + dx, 1, w + dz, rim ? b(Blocks.COBBLESTONE_WALL) : b(Blocks.WATER_CAULDRON));
            }
        put(-w, 1, w, log(Blocks.HAY_BLOCK, Direction.Axis.Y));
        put(-w - 1, 1, w, log(Blocks.HAY_BLOCK, Direction.Axis.Y));
        put(-w, 2, w, log(Blocks.HAY_BLOCK, Direction.Axis.Y));
        put(w, 1, -w, b(Blocks.BARREL));
        put(w + 1, 1, -w, b(Blocks.BARREL));
        put(w, 2, -w, b(Blocks.BARREL));
        for (int i = -1; i <= 1; i++) put(-w, 1, -w + i, log(Blocks.SPRUCE_LOG, Direction.Axis.Z));
        put(-w, 2, -w, log(Blocks.SPRUCE_LOG, Direction.Axis.Z));
    }

    /** the keep's tower: hollow, a door on the south, three floors and a ladder, slits, battlements, flag and fire */
    private void tower() {
        int[] floors = {0, 5, 10, towerH};
        for (int x = -half; x <= half; x++)
            for (int z = -half; z <= half; z++) {
                boolean edge = Math.abs(x) == half || Math.abs(z) == half;
                boolean corner = Math.abs(x) == half && Math.abs(z) == half;
                for (int y = 1; y <= towerH; y++) {
                    if (edge) {
                        boolean door = z == half && Math.abs(x) <= 1 && y <= 3;
                        boolean slit = !corner && (y % 5 == 3) && (Math.abs(x) == 2 || Math.abs(z) == 2);
                        put(x, y, z, door || slit ? b(Blocks.AIR) : corner ? b(Blocks.POLISHED_DEEPSLATE)
                                : brick(x, y, z));
                    } else {
                        boolean floor = false;
                        for (int fl : floors) if (y == fl) floor = true;
                        // the ladder's hole in each floor (north-west inside corner)
                        boolean hole = x == -half + 1 && z == -half + 1;
                        put(x, y, z, floor && !hole ? b(Blocks.SPRUCE_PLANKS) : b(Blocks.AIR));
                    }
                }
                // battlements
                if (edge && ((x + z) & 1) == 0) put(x, towerH + 1, z, brick(x, towerH + 1, z));
            }
        // the ladder up the inside of the north wall, from the ground to the roof
        for (int y = 1; y <= towerH; y++)
            put(-half + 1, y, -half + 1, facing(Blocks.LADDER, Direction.SOUTH));
        // lanterns inside, a table and chests of nothing on the first floor
        put(half - 1, 4, half - 1, with(b(Blocks.LANTERN), BlockStateProperties.HANGING, true));
        put(half - 1, 9, -half + 1, with(b(Blocks.LANTERN), BlockStateProperties.HANGING, true));
        put(1, 1, -half + 1, b(Blocks.CRAFTING_TABLE));
        put(2, 1, -half + 1, b(Blocks.BARREL));
        // over the door: a banner each side, a torch
        put(-2, 4, half + 1, facing(Blocks.RED_WALL_BANNER, Direction.SOUTH));
        put(2, 4, half + 1, facing(Blocks.RED_WALL_BANNER, Direction.SOUTH));
        put(0, 4, half + 1, facing(Blocks.WALL_TORCH, Direction.SOUTH));
        // the flag pole and the fire bowl on the roof
        for (int y = towerH + 1; y <= towerH + 5; y++) put(half - 1, y, -half + 1, b(Blocks.SPRUCE_FENCE));
        put(half - 1, towerH + 6, -half + 1, with(b(Blocks.RED_BANNER), BlockStateProperties.ROTATION_16, 8));
        put(-half + 2, towerH + 1, half - 2, b(Blocks.POLISHED_BLACKSTONE));
        put(-half + 2, towerH + 2, half - 2, lit(Blocks.CAMPFIRE));
    }
}
