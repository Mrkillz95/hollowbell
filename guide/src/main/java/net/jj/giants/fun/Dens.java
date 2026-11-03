package net.jj.giants.fun;

import static net.jj.giants.fun.Builds.b;
import static net.jj.giants.fun.Builds.hash;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 7, ADMIN_BRIDGE.md 23.3) A den for each giant, sized to him (r round, his height h at that size): each a few
 * rings and patterns of plain blocks in his own colours, built column by column like the arena (Builds).
 */
final class Dens {
    private Dens() {}

    static @Nullable Builds.Shape shape(String kind, int r, double h) {
        return switch (kind) {
            case "pitchgut" -> make(r + 5, (dx, dz, d, f, o) -> crater(r, dx, dz, d, f, o));
            case "furrowmaw" -> make(r + 14, (dx, dz, d, f, o) -> pit(r, dx, dz, d, f, o));
            case "cerberus" -> make(r + 3, (dx, dz, d, f, o) -> split(r, dx, dz, d, f, o));
            case "hollowbell" -> make(r + 2, (dx, dz, d, f, o) -> perch(r, h, dx, dz, d, f, o));
            case "lanternwillow" -> make(r + 2, (dx, dz, d, f, o) -> mire(r, dx, dz, d, f, o));
            case "wreckback" -> make(r + 6, (dx, dz, d, f, o) -> cove(r, dx, dz, d, f, o));
            case "swarmforge" -> make(r + 2, (dx, dz, d, f, o) -> yard(r, dx, dz, d, f, o));
            case "gloamwick" -> make(r + 4, (dx, dz, d, f, o) -> glade(r, dx, dz, d, f, o));
            default -> null;
        };
    }

    interface ColumnFn {
        void column(int dx, int dz, double d, int f, Builds.Col out);
    }

    private static Builds.Shape make(int reach, ColumnFn fn) {
        return new Builds.Shape() {
            @Override
            public void column(int dx, int dz, double d, int f, Builds.Col out) {
                fn.column(dx, dz, d, f, out);
            }

            @Override
            public int reach() {
                return reach;
            }
        };
    }

    /** Pitchgut: a black crater, magma in its sides and a tar pool in the middle, a raised basalt rim */
    private static void crater(int r, int dx, int dz, double d, int f, Builds.Col o) {
        int deep = Math.max(3, Math.min(10, r / 4));
        if (d <= r) {
            int depth = (int) Math.round((1 - (d / r) * (d / r)) * deep);
            int y = f - depth;
            BlockState s = d < r * 0.28 ? b(Blocks.BLACK_CONCRETE)
                    : hash(dx, dz, 3) < 0.1 ? b(Blocks.MAGMA_BLOCK)
                    : hash(dx, dz, 4) < 0.3 ? b(Blocks.SMOOTH_BASALT) : b(Blocks.BLACKSTONE);
            o.set(y, s);
            o.set(y - 1, b(Blocks.BASALT));
            o.clearAbove(y + 1);
            o.support = b(Blocks.BASALT);
        } else if (d <= r + 4) {
            int rim = (int) Math.round((1 - (d - r) / 4) * 3);
            for (int y = f; y <= f + rim; y++) o.set(y, hash(dx, dz + y, 5) < 0.3 ? b(Blocks.BLACKSTONE) : b(Blocks.BASALT));
            o.clearAbove(f + rim + 1);
            o.support = b(Blocks.BASALT);
        }
    }

    /** Furrowmaw: a sunken pit of coarse and rooted dirt, with a tunnel mouth leading off to the east */
    private static void pit(int r, int dx, int dz, double d, int f, Builds.Col o) {
        int floor = f - 6;
        if (d <= r) {
            BlockState s = hash(dx, dz, 6) < 0.12 ? b(Blocks.MUD)
                    : hash(dx, dz, 7) < 0.4 ? b(Blocks.ROOTED_DIRT) : b(Blocks.COARSE_DIRT);
            o.set(floor, s);
            o.set(floor - 1, b(Blocks.DIRT));
            o.clearAbove(floor + 1);
            o.support = b(Blocks.DIRT);
        } else if (d <= r + 2) {
            for (int y = floor; y <= f; y++) o.set(y, hash(dx, dz + y, 8) < 0.5 ? b(Blocks.ROOTED_DIRT) : b(Blocks.COARSE_DIRT));
            o.clearAbove(f + 1);
            o.support = b(Blocks.DIRT);
        } else if (dx > 0 && Math.abs(dz) <= 3 && d <= r + 14) {
            // the tunnel mouth: dug under the ground going east, roof left on
            o.set(floor, b(Blocks.COARSE_DIRT));
            for (int y = floor + 1; y <= floor + 5; y++) o.set(y, b(Blocks.AIR));
            if (Math.abs(dz) == 3) for (int y = floor + 1; y <= floor + 5; y++) o.set(y, b(Blocks.ROOTED_DIRT));
            o.set(floor + 6, b(Blocks.ROOTED_DIRT));
        }
    }

    /** Cerberus: a ring split down the middle, fire (blackstone and magma) to the west and ice to the east, a gate */
    private static void split(int r, int dx, int dz, double d, int f, Builds.Col o) {
        boolean fire = dx < 0;
        if (d <= r) {
            BlockState s = Math.abs(dx) <= 1 ? b(Blocks.CHISELED_POLISHED_BLACKSTONE)
                    : fire ? (hash(dx, dz, 9) < 0.12 ? b(Blocks.MAGMA_BLOCK)
                            : hash(dx, dz, 10) < 0.3 ? b(Blocks.POLISHED_BLACKSTONE) : b(Blocks.BLACKSTONE))
                    : (hash(dx, dz, 11) < 0.15 ? b(Blocks.BLUE_ICE)
                            : hash(dx, dz, 12) < 0.3 ? b(Blocks.SNOW_BLOCK) : b(Blocks.PACKED_ICE));
            o.set(f, s);
            o.clearAbove(f + 1);
            o.support = fire ? b(Blocks.BLACKSTONE) : b(Blocks.PACKED_ICE);
        } else if (d <= r + 2) {
            BlockState wall = fire ? b(Blocks.POLISHED_BLACKSTONE_BRICKS) : b(Blocks.PACKED_ICE);
            o.set(f, wall);
            o.support = wall;
            boolean gate = dz > 0 && Math.abs(dx) <= 3;
            if (gate) {
                // the gate: an iron bar overhead, chains hanging from it
                o.set(f + 4, b(Blocks.IRON_BARS));
                if ((dx & 1) == 0) o.set(f + 3, b(Blocks.CHAIN));
                o.clearAbove(f + 1);
                for (int y = f + 1; y <= f + 2; y++) o.set(y, b(Blocks.AIR));
                return;
            }
            boolean post = dz > 0 && Math.abs(dx) <= 5;
            int top = post ? f + 5 : f + 3;
            for (int y = f + 1; y <= top; y++) o.set(y, post ? b(Blocks.POLISHED_BLACKSTONE) : wall);
            if (post) o.set(top + 1, fire ? b(Blocks.LANTERN) : b(Blocks.SOUL_LANTERN));
            o.clearAbove(top + 2);
        }
    }

    /** Hollowbell: a high stone perch, a pillar holding a round landing ring, lanterns round its edge */
    private static void perch(int r, double h, int dx, int dz, double d, int f, Builds.Col o) {
        int up = Mth.clamp((int) Math.round(h * 0.25), 12, 48);
        double ring = Math.max(6, r * 0.6);
        if (d <= 3.2) {
            for (int y = f; y < f + up; y++) o.set(y, hash(dx, dz + y, 13) < 0.15 ? b(Blocks.CRACKED_STONE_BRICKS)
                    : b(Blocks.STONE_BRICKS));
            o.support = b(Blocks.STONE_BRICKS);
        }
        if (d <= ring) {
            o.set(f + up, d > ring - 1.2 ? b(Blocks.POLISHED_ANDESITE) : b(Blocks.SMOOTH_STONE));
            if (d > ring - 1.2) {
                double a = Math.toDegrees(Math.atan2(dz, dx)) + 180;
                if (((int) Math.round(a / 30)) * 30 - a < 1.5 && ((int) Math.round(a / 30)) * 30 - a > -1.5)
                    o.set(f + up + 1, b(Blocks.LANTERN));
            }
            if (d > 3.2) for (int y = f + 1; y < f + up; y++) o.set(y, b(Blocks.AIR));
            o.clearAbove(f + up + 2);
        } else if (d <= r) {
            o.clearAbove(f + 1);
        }
    }

    /** Lantern Willow: a mire of mud, moss and grass with still pools, and lanterns on posts */
    private static void mire(int r, int dx, int dz, double d, int f, Builds.Col o) {
        if (d > r) return;
        boolean pool = hash(dx >> 3, dz >> 3, 14) < 0.22 && hash(dx, dz, 15) < 0.85;
        o.support = b(Blocks.MUD);
        if (pool) {
            o.set(f, b(Blocks.WATER));
            o.set(f - 1, b(Blocks.MUD));
            o.clearAbove(f + 1);
            return;
        }
        double k = hash(dx, dz, 16);
        o.set(f, k < 0.45 ? b(Blocks.MUD) : k < 0.75 ? b(Blocks.MOSS_BLOCK) : b(Blocks.GRASS_BLOCK));
        if (Math.floorMod(dx, 8) == 0 && Math.floorMod(dz, 8) == 0 && d < r - 2) {
            for (int y = f + 1; y <= f + 3; y++) o.set(y, b(Blocks.SPRUCE_FENCE));
            o.set(f + 4, b(Blocks.LANTERN));
            o.clearAbove(f + 5);
        } else o.clearAbove(f + 1);
    }

    /** Wreckback: a flooded cove with a sand bed, wreck timbers sticking out of it, a sand beach round it */
    private static void cove(int r, int dx, int dz, double d, int f, Builds.Col o) {
        if (d <= r) {
            int bed = f - 4;
            o.set(bed, b(Blocks.SAND));
            o.set(bed - 1, b(Blocks.SANDSTONE));
            for (int y = bed + 1; y <= f; y++) o.set(y, b(Blocks.WATER));
            double k = hash(dx, dz, 17);
            if (k < 0.035 && d < r - 2) {
                BlockState log = (k < 0.018 ? b(Blocks.DARK_OAK_LOG) : b(Blocks.STRIPPED_SPRUCE_LOG));
                int tall = 2 + (int) (hash(dx, dz, 18) * 4);
                for (int y = bed + 1; y <= Math.min(f + 2, bed + tall); y++) o.set(y, log);
            }
            o.clearAbove(f + 1);
            o.support = b(Blocks.SANDSTONE);
        } else if (d <= r + 5) {
            o.set(f, b(Blocks.SAND));
            o.set(f - 1, b(Blocks.SAND));
            if (hash(dx, dz, 19) < 0.02)
                o.set(f + 1, b(Blocks.STRIPPED_SPRUCE_LOG).setValue(RotatedPillarBlock.AXIS,
                        hash(dx, dz, 20) < 0.5 ? Direction.Axis.X : Direction.Axis.Z));
            o.clearAbove(f + 2);
            o.support = b(Blocks.SANDSTONE);
        }
    }

    /** Swarmforge: a forge yard of smooth stone ruled with iron, a copper ring, furnaces round the edge, iron bars */
    private static void yard(int r, int dx, int dz, double d, int f, Builds.Col o) {
        if (d <= r) {
            BlockState s = Math.floorMod(dx, 8) == 0 || Math.floorMod(dz, 8) == 0 ? b(Blocks.IRON_BLOCK)
                    : Math.abs(d - r * 0.6) < 0.7 ? b(Blocks.WAXED_CUT_COPPER) : b(Blocks.SMOOTH_STONE);
            o.set(f, s);
            o.support = b(Blocks.STONE);
            if (Math.abs(d - (r - 2)) < 0.6) {
                double a = Math.atan2(dz, dx);
                int along = (int) Math.floor((a + Math.PI) * (r - 2));
                if (Math.floorMod(along, 6) == 0) {
                    Direction in = Direction.fromYRot(Math.toDegrees(Math.atan2(-dz, -dx)) - 90);
                    o.set(f + 1, (Math.floorMod(along, 12) == 0 ? b(Blocks.BLAST_FURNACE) : b(Blocks.FURNACE))
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, in));
                    o.clearAbove(f + 2);
                    return;
                }
            }
            o.clearAbove(f + 1);
        } else if (d <= r + 1.5) {
            o.set(f, b(Blocks.SMOOTH_STONE));
            o.support = b(Blocks.STONE);
            boolean gap = Math.abs(dx) <= 2 && dz > 0;
            if (!gap) {
                o.set(f + 1, b(Blocks.IRON_BARS));
                o.set(f + 2, b(Blocks.IRON_BARS));
            }
            o.clearAbove(f + 3);
        }
    }

    /** Gloamwick: a glade of moss and podzol ringed with dark oaks, a stone circle, soul lanterns and candles */
    private static void glade(int r, int dx, int dz, double d, int f, Builds.Col o) {
        if (d > r + 4) return;
        double a = Math.toDegrees(Math.atan2(dz, dx)) + 180;
        // the dark oaks round the edge: trunks every 20 degrees, a crown of leaves round each
        double nearTree = 99;
        for (int k = 0; k < 18; k++) {
            double ta = Math.toRadians(k * 20);
            double tx = Math.cos(ta) * (r + 1), tz = Math.sin(ta) * (r + 1);
            nearTree = Math.min(nearTree, Math.sqrt((dx - tx) * (dx - tx) + (dz - tz) * (dz - tz)));
        }
        double k = hash(dx, dz, 21);
        o.set(f, k < 0.4 ? b(Blocks.MOSS_BLOCK) : k < 0.7 ? b(Blocks.PODZOL) : b(Blocks.GRASS_BLOCK));
        o.support = b(Blocks.DIRT);
        BlockState leaves = b(Blocks.DARK_OAK_LEAVES).setValue(LeavesBlock.PERSISTENT, true);
        if (nearTree < 0.8) {
            for (int y = f + 1; y <= f + 6; y++) o.set(y, b(Blocks.DARK_OAK_LOG));
            o.set(f + 7, leaves);
            o.clearAbove(f + 8);
            return;
        }
        if (nearTree < 2.6) {
            o.set(f + 5, leaves);
            o.set(f + 6, leaves);
            if (nearTree < 1.8) o.set(f + 7, leaves);
            o.clearAbove(f + 8);
            for (int y = f + 1; y <= f + 4; y++) o.set(y, b(Blocks.AIR));
            return;
        }
        if (d > r) {
            o.clearAbove(f + 1);
            return;
        }
        // the stone circle
        double ring = r * 0.4;
        if (Math.abs(d - ring) < 1.0) {
            int step = (int) Math.round(a / 30) * 30;
            if (Math.abs(step - a) * Math.PI / 180 * ring < 1.0) {
                for (int y = f + 1; y <= f + 3; y++) o.set(y, hash(dx, dz + y, 22) < 0.5
                        ? b(Blocks.MOSSY_COBBLESTONE) : b(Blocks.COBBLESTONE));
                o.clearAbove(f + 4);
                return;
            }
        }
        // soul lanterns on posts
        double lr = r * 0.7;
        if (Math.abs(d - lr) < 0.7) {
            int step = (int) Math.round(a / 45) * 45;
            if (Math.abs(step - a) * Math.PI / 180 * lr < 0.7) {
                o.set(f + 1, b(Blocks.DARK_OAK_FENCE));
                o.set(f + 2, b(Blocks.DARK_OAK_FENCE));
                o.set(f + 3, b(Blocks.SOUL_LANTERN));
                o.clearAbove(f + 4);
                return;
            }
        }
        // candles here and there
        if (hash(dx, dz, 23) < 0.025) {
            int n = 1 + (int) (hash(dx, dz, 24) * 4);
            o.set(f + 1, b(Blocks.CANDLE).setValue(CandleBlock.CANDLES, Mth.clamp(n, 1, 4))
                    .setValue(CandleBlock.LIT, true));
            o.clearAbove(f + 2);
            return;
        }
        o.clearAbove(f + 1);
    }
}
