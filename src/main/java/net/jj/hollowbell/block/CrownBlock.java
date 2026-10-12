package net.jj.hollowbell.block;

import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.world.WorldOne;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The Hollowbell's crown, taken off him when he dies: a small glowing cap to set down somewhere. */
public class CrownBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 10, 15);

    public CrownBlock(Properties p) { super(p); }

    @Override
    protected VoxelShape getShape(BlockState st, BlockGetter g, BlockPos p, CollisionContext c) { return SHAPE; }

    /** a right-click wakes it, the same as /hollowbell ward on next to it */
    @Override
    protected InteractionResult useWithoutItem(BlockState st, Level l, BlockPos p, Player who, BlockHitResult hit) {
        if (l instanceof ServerLevel sl) {
            Woken w = wake(sl, p);
            who.displayClientMessage(w.said(), false);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }

    /** whether it woke, and what to tell whoever woke it */
    public record Woken(boolean ok, Component said) {}

    /** wakes the crown at p: he is held off round it for a while, unless one is awake already or it is still resting */
    public static Woken wake(ServerLevel lvl, BlockPos crown) {
        if (HollowbellConfig.V.wardBlocks <= 0) return new Woken(false, Component.translatable("command.hollowbell.ward_no_blocks"));
        WorldOne w = WorldOne.get(lvl.getServer());
        ServerLevel over = lvl.getServer().overworld();
        if (w.warding(over)) {
            var awake = w.wardSpot();
            return new Woken(false, Component.translatable("command.hollowbell.ward_already", awake.getX(), awake.getZ(), w.wardLeft(over) / 20));
        }
        if (w.wardRestLeft(over) > 0) return new Woken(false, Component.translatable("command.hollowbell.ward_resting", w.wardRestLeft(over) / 20));
        w.startWard(lvl, crown, Math.max(20, HollowbellConfig.V.wardSeconds * 20), Math.max(0, HollowbellConfig.V.wardRestSeconds * 20));
        for (HollowbellEntity e : lvl.getEntities(ModEntities.HOLLOWBELL, x -> !x.isRemoved()))
            if (e.warded(e.getX(), e.getZ())) e.pushedBackByWard();
        return new Woken(true, Component.translatable("command.hollowbell.ward_wakes", crown.getX(), crown.getZ(), (int) WorldOne.wardRange()));
    }

    /** taken up or broken while it holds him off: the ward stops there and then */
    @Override
    protected void onRemove(BlockState st, Level l, BlockPos p, BlockState now, boolean moved) {
        if (!now.is(st.getBlock()) && l instanceof ServerLevel sl) WorldOne.get(sl.getServer()).crownTaken(sl, p);
        super.onRemove(st, l, p, now, moved);
    }

    @Override
    public void animateTick(BlockState st, Level l, BlockPos p, RandomSource r) {
        if (r.nextInt(3) == 0)
            l.addParticle(ParticleTypes.GLOW, p.getX() + 0.2 + r.nextDouble() * 0.6, p.getY() + 0.7, p.getZ() + 0.2 + r.nextDouble() * 0.6, 0, 0.02, 0);
    }
}
