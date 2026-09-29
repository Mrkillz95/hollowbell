package net.jj.hollowbell.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * His loot cache: where his drops are kept when he dies, on his pedestal, so nothing despawns or falls in lava.
 * Open it like a chest. While anything is left in it, a pale green beam of light stands up out of it.
 */
public class LootCacheBlock extends BaseEntityBlock {
    public static final MapCodec<LootCacheBlock> CODEC = simpleCodec(LootCacheBlock::new);
    /** something is still in it: the beam stands and it glows */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public LootCacheBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(LIT); }
    @Override protected RenderShape getRenderShape(BlockState st) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState st, BlockGetter g, BlockPos p, CollisionContext c) { return SHAPE; }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new LootCacheBlockEntity(pos, st); }

    @Override
    protected InteractionResult useWithoutItem(BlockState st, Level l, BlockPos pos, Player p, BlockHitResult hit) {
        if (l.isClientSide) return InteractionResult.SUCCESS;
        if (l.getBlockEntity(pos) instanceof LootCacheBlockEntity be) {
            p.openMenu(be);
            if (p instanceof net.minecraft.server.level.ServerPlayer sp) net.jj.hollowbell.HollowbellMod.award(sp, "loot_cache");
        }
        return InteractionResult.CONSUME;
    }

    /** broken: whatever is still in it drops out */
    @Override
    protected void onRemove(BlockState st, Level l, BlockPos pos, BlockState now, boolean moved) {
        if (!st.is(now.getBlock()) && l.getBlockEntity(pos) instanceof LootCacheBlockEntity be) Containers.dropContents(l, pos, be);
        super.onRemove(st, l, pos, now, moved);
    }
}
