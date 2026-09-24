package net.jj.mountain.block;

import com.mojang.serialization.MapCodec;
import net.jj.mountain.innards.Innards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The black goo that pours out of his mouth. A thin, sticky layer: it slows you right down and darkens your eyes
 * while you stand in it, and dries up a few minutes after he has gone. Inside him it never dries, and it burns.
 */
public class GooBlock extends Block {
    public static final MapCodec<GooBlock> CODEC = simpleCodec(GooBlock::new);
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);
    /** the goo that landed here ate the block it landed on (so a stream only ever digs one block deep) */
    public static final BooleanProperty EATEN = BooleanProperty.create("eaten");
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 3, 16);
    private static final VoxelShape COLLIDE = Block.box(0, 0, 0, 16, 1, 16);

    public GooBlock(BlockBehaviour.Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0).setValue(EATEN, false));
    }

    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(AGE, EATEN); }

    @Override protected VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) { return SHAPE; }
    @Override protected VoxelShape getCollisionShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) { return COLLIDE; }
    @Override protected boolean isPathfindable(BlockState s, PathComputationType t) { return t == PathComputationType.LAND; }

    @Override
    protected boolean canSurvive(BlockState s, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState s, Direction d, BlockState n, LevelAccessor level, BlockPos pos, BlockPos np) {
        return !s.canSurvive(level, pos) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : super.updateShape(s, d, n, level, pos, np);
    }

    @Override
    protected void entityInside(BlockState s, Level level, BlockPos pos, Entity e) {
        if (level.isClientSide || !(e instanceof LivingEntity le)) return;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        if (e instanceof net.jj.mountain.entity.inside.InsideMob) return;           // they live in it
        boolean inside = Innards.isInnards(level);
        if (!inside) {                                                     // out in the world it blinds you; inside him you need to see
            MobEffectInstance cur = le.getEffect(MobEffects.DARKNESS);
            if (cur == null || cur.getDuration() < 30) le.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 70, 0, false, false, true));
        }
        MobEffectInstance slow = le.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (slow == null || slow.getDuration() < 10) le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1, false, false, true));
        if (inside && e.tickCount % 40 == 0 && !(e instanceof net.jj.mountain.entity.inside.InsideMob))    // stomach acid: stand on the mounds to get out of it
            le.hurt(level.damageSources().magic(), 1.0f);
    }

    @Override
    protected void randomTick(BlockState s, ServerLevel level, BlockPos pos, RandomSource r) {
        if (Innards.isInnards(level)) return;
        int age = s.getValue(AGE);
        if (age >= 3) level.removeBlock(pos, false);
        else level.setBlock(pos, s.setValue(AGE, age + 1), 2);
    }

    @Override
    public void animateTick(BlockState s, Level level, BlockPos pos, RandomSource r) {
        if (r.nextInt(12) == 0)
            level.addParticle(ParticleTypes.SQUID_INK, pos.getX() + r.nextDouble(), pos.getY() + 0.2, pos.getZ() + r.nextDouble(), 0, 0.01, 0);
        if (r.nextInt(40) == 0)
            level.addParticle(ParticleTypes.DRIPPING_OBSIDIAN_TEAR, pos.getX() + r.nextDouble(), pos.getY() + 0.2, pos.getZ() + r.nextDouble(), 0, 0, 0);
    }
}
