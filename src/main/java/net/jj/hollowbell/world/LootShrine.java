package net.jj.hollowbell.world;

import net.jj.hollowbell.ModBlocks;
import net.jj.hollowbell.block.LootCacheBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * Where he fell: a small pale pedestal of his own blocks, 7 across, with glowing posts at the corners and his loot
 * cache in the middle holding everything he dropped. The beam stands up out of the cache until it's emptied; the
 * pedestal stays as a trophy.
 */
public final class LootShrine {
    private LootShrine() {}

    /**
     * Builds it on the ground under where he fell (y) and puts the loot in; returns the cache's spot. With griefing
     * off it only fills open space and clears nothing.
     */
    public static BlockPos build(ServerLevel l, double x, double y, double z, List<ItemStack> loot, boolean clear) {
        int cx = Mth.floor(x), cz = Mth.floor(z);
        l.getChunk(cx >> 4, cz >> 4);
        int g = floorAt(l, cx, cz, y);           // first air over the ground
        g = Mth.clamp(g, l.getMinBuildHeight() + 2, l.getMaxBuildHeight() - 8);
        BlockState floor = ModBlocks.BELL_CALCITE.defaultBlockState(), edge = Blocks.POLISHED_DIORITE.defaultBlockState();
        BlockState post = Blocks.BONE_BLOCK.defaultBlockState(), light = Blocks.VERDANT_FROGLIGHT.defaultBlockState();
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            boolean rim = Math.abs(dx) == 3 || Math.abs(dz) == 3;
            // a solid footing under the floor (over water or a gap it stands on its own)
            put(l, new BlockPos(cx + dx, g - 2, cz + dz), edge, false);
            put(l, new BlockPos(cx + dx, g - 1, cz + dz), rim ? edge : floor, clear);
            if (clear) for (int ry = 0; ry <= 5; ry++) put(l, new BlockPos(cx + dx, g + ry, cz + dz), Blocks.AIR.defaultBlockState(), true);
            if (Math.abs(dx) == 3 && Math.abs(dz) == 3) {
                put(l, new BlockPos(cx + dx, g, cz + dz), post, clear);
                put(l, new BlockPos(cx + dx, g + 1, cz + dz), post, clear);
                put(l, new BlockPos(cx + dx, g + 2, cz + dz), light, clear);
            }
        }
        // the middle: a raised step of tendril glass and the cache on it
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            put(l, new BlockPos(cx + dx, g, cz + dz), dx == 0 && dz == 0 ? post : ModBlocks.TENDRIL_GLASS.defaultBlockState(), clear);
        BlockPos at = new BlockPos(cx, g + 1, cz);
        // (never over a chest or the like; with griefing off, on the first open block above whatever is there)
        while (at.getY() < g + 8 && (l.getBlockState(at).hasBlockEntity() || !clear && !l.getBlockState(at).canBeReplaced())) at = at.above();
        l.setBlock(at, ModBlocks.LOOT_CACHE.defaultBlockState(), Block.UPDATE_ALL);
        if (l.getBlockEntity(at) instanceof LootCacheBlockEntity be) {
            for (ItemStack st : loot) {
                ItemStack left = be.add(st);
                if (!left.isEmpty()) l.addFreshEntity(new ItemEntity(l, at.getX() + 0.5, at.getY() + 1.2, at.getZ() + 0.5, left));
            }
        } else for (ItemStack st : loot) l.addFreshEntity(new ItemEntity(l, x, g + 1, z, st));
        return at;
    }

    /**
     * The ground under where he fell: the first solid block or water down from a little over him. The top of the
     * land would be the roof in the nether.
     */
    static int floorAt(ServerLevel l, int x, int z, double fromY) {
        int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (!l.dimensionType().hasCeiling()) return top;
        int from = Math.min(top, Mth.floor(fromY) + 8);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, from, z);
        // (started inside rock over a cave: down through it to the open space first)
        boolean open = !isFloor(l.getBlockState(m));
        for (int y = from - 1; y > l.getMinBuildHeight(); y--) {
            boolean floor = isFloor(l.getBlockState(m.setY(y)));
            if (!floor) open = true;
            else if (open) return y + 1;
        }
        return top;
    }

    private static boolean isFloor(BlockState st) { return st.blocksMotion() || !st.getFluidState().isEmpty(); }

    /** sets a block, but never bedrock or anything holding things (chests stay); solid-only footings keep ground */
    private static void put(ServerLevel l, BlockPos p, BlockState st, boolean always) {
        BlockState was = l.getBlockState(p);
        if (was.is(Blocks.BEDROCK) || was.hasBlockEntity()) return;
        if (!always && !was.isAir() && was.getFluidState().isEmpty()) return;
        l.setBlock(p, st, Block.UPDATE_ALL);
    }
}
