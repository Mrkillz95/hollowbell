package net.jj.hollowbell.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The Hollowbell's crown, taken off him when he dies: a small glowing cap to set down somewhere. */
public class CrownBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 10, 15);

    public CrownBlock(Properties p) { super(p); }

    @Override
    protected VoxelShape getShape(BlockState st, BlockGetter g, BlockPos p, CollisionContext c) { return SHAPE; }

    @Override
    public void animateTick(BlockState st, Level l, BlockPos p, RandomSource r) {
        if (r.nextInt(3) == 0)
            l.addParticle(ParticleTypes.GLOW, p.getX() + 0.2 + r.nextDouble() * 0.6, p.getY() + 0.7, p.getZ() + 0.2 + r.nextDouble() * 0.6, 0, 0.02, 0);
    }
}
