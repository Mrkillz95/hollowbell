package net.jj.mountain.block;

import com.mojang.serialization.MapCodec;
import net.jj.mountain.MountainConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What is left of him once he has gone into the ground: one lump of him that never stopped beating. It has one
 * job and it tells you what it is. Knock on it and it goes off like a heart the size of a house — everything
 * that means you harm nearby is thrown off its feet, and for as long as it keeps beating the Mountain himself
 * will not come within five hundred blocks of it. He turns round and walks out if he is already inside.
 *
 * It is not a wall. It runs for five minutes and then sits dark for twenty while it gathers itself, and there is
 * only one of them going at a time, so it is somewhere to run to, not somewhere to live.
 */
public class HeartTrophyBlock extends Block {
    public static final MapCodec<HeartTrophyBlock> CODEC = simpleCodec(HeartTrophyBlock::new);

    /** 0 dark and gathering itself, 1 beating slowly and ready, 2 holding him off */
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, 2);
    public static final int DARK = 0, READY = 1, WARDING = 2;

    /** how far the knock itself throws things off their feet */
    private static final double SHOVE = 48.0;
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

    public HeartTrophyBlock(BlockBehaviour.Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(MODE, READY));
    }

    @Override protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(MODE); }

    @Override
    protected VoxelShape getShape(BlockState st, BlockGetter g, BlockPos pos, CollisionContext c) { return SHAPE; }

    public static int wardTicks() { return Math.max(100, MountainConfig.V.wardSeconds * 20); }
    public static int restTicks() { return Math.max(100, MountainConfig.V.wardRestSeconds * 20); }

    private static String mins(long ticks) {
        long secs = Math.max(1, ticks / 20);
        if (secs < 60) return secs + "s";
        return (secs / 60) + "m " + (secs % 60) + "s";
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState st, Level lvl, BlockPos pos, Player who, BlockHitResult hit) {
        if (!(lvl instanceof ServerLevel sl)) return InteractionResult.sidedSuccess(lvl.isClientSide);
        var w = net.jj.mountain.world.MountainWorld.get(sl.getServer().overworld());

        // already going: it tells you how long you have got, wherever in the world it is beating
        if (w.warding(sl)) {
            var at = w.wardSpot();
            boolean here = at != null && at.getX() == pos.getX() && at.getZ() == pos.getZ();
            say(who, here ? "ward_running" : "ward_elsewhere", mins(w.wardLeft(sl)));
            return InteractionResult.CONSUME;
        }
        // spent: it is dark until it has gathered itself again
        long rest = w.wardRestLeft(sl);
        if (rest > 0) {
            if (st.getValue(MODE) != DARK) sl.setBlock(pos, st.setValue(MODE, DARK), Block.UPDATE_ALL);
            say(who, "ward_dark", mins(rest));
            return InteractionResult.CONSUME;
        }

        wake(sl, pos, st, who, w);
        return InteractionResult.sidedSuccess(false);
    }

    private void wake(ServerLevel sl, BlockPos pos, BlockState st, Player who, net.jj.mountain.world.MountainWorld w) {
        boolean holds = MountainConfig.V.wardBlocks > 0;
        if (holds) w.startWard(sl, pos, wardTicks(), restTicks());
        sl.setBlock(pos, st.setValue(MODE, holds ? WARDING : DARK), Block.UPDATE_ALL);
        sl.scheduleTick(pos, this, holds ? wardTicks() : restTicks());
        shove(sl, pos);

        // everybody near is told, because this is the loudest thing a block in this mod does
        Component line = holds
                ? Component.translatable("message.mountain_breathes.ward_on",
                        (int) net.jj.mountain.world.MountainWorld.wardRange(), mins(wardTicks()))
                : Component.translatable("message.mountain_breathes.heart_roar");
        for (ServerPlayer p : sl.players())
            if (p.distanceToSqr(Vec3.atCenterOf(pos)) < 160 * 160) p.displayClientMessage(line, false);

        // and if he is standing inside it right now, he is told to his face
        if (holds) {
            for (var m : sl.getEntities(net.jj.mountain.ModEntities.MOUNTAIN, x -> !x.isRemoved())) {
                if (w.warded(sl, m.getX(), m.getZ())) m.pushedBackByHeart();
            }
        }
    }

    /** the knock itself: a heartbeat the size of a house, and everything that means you harm goes over */
    private void shove(ServerLevel sl, BlockPos pos) {
        Vec3 c = Vec3.atCenterOf(pos);
        sl.playSound(null, pos, net.jj.mountain.ModSounds.ROAR, SoundSource.BLOCKS, net.jj.mountain.ModSounds.vol(5f), 0.5f);
        sl.playSound(null, pos, net.jj.mountain.ModSounds.HEART, SoundSource.BLOCKS, net.jj.mountain.ModSounds.vol(4f), 0.45f);
        sl.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + 0.6, c.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y + 0.9, c.z, 160, 3.0, 1.5, 3.0, 0.3);
        // a ring going out along the ground, so you can see how far it reaches
        for (int step = 2; step <= 24; step += 2) {
            double r = step;
            for (int i = 0; i < 48; i++) {
                double a = i * Math.PI * 2 / 48;
                sl.sendParticles(ParticleTypes.SCULK_SOUL, c.x + Math.cos(a) * r, c.y + 0.4, c.z + Math.sin(a) * r, 1, 0, 0.02, 0, 0.01);
            }
        }
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(SHOVE),
                x -> x.isAlive() && (x instanceof Enemy) && !(x instanceof Player))) {
            Vec3 out = e.position().subtract(c);
            if (out.lengthSqr() < 1.0E-4) out = new Vec3(1, 0, 0);
            out = new Vec3(out.x, 0, out.z).normalize();
            e.setDeltaMovement(e.getDeltaMovement().add(out.scale(1.5)).add(0, 0.55, 0));
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 1));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 1));
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 400, 0));
            if (e instanceof Mob mob) { mob.setTarget(null); mob.setLastHurtByMob(null); }
        }
    }

    private static void say(Player p, String key, Object... args) {
        p.displayClientMessage(Component.translatable("message.mountain_breathes." + key, args), false);
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState st, Level lvl,
                                                                  BlockPos pos, Player who, InteractionHand hand, BlockHitResult hit) {
        // anything in your hand still knocks on it, rather than being placed against it
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** the ward running out, and then the long dark coming to an end */
    @Override
    protected void tick(BlockState st, ServerLevel sl, BlockPos pos, RandomSource rnd) {
        var w = net.jj.mountain.world.MountainWorld.get(sl.getServer().overworld());
        if (st.getValue(MODE) == WARDING) {
            sl.setBlock(pos, st.setValue(MODE, DARK), Block.UPDATE_ALL);
            sl.playSound(null, pos, net.jj.mountain.ModSounds.HEART, SoundSource.BLOCKS, net.jj.mountain.ModSounds.vol(2.5f), 0.35f);
            for (ServerPlayer p : sl.players())
                if (p.distanceToSqr(Vec3.atCenterOf(pos)) < 160 * 160)
                    p.displayClientMessage(Component.translatable("message.mountain_breathes.ward_off"), false);
            sl.scheduleTick(pos, this, Math.max(20, (int) w.wardRestLeft(sl)));
            return;
        }
        if (st.getValue(MODE) == DARK) {
            if (w.wardRestLeft(sl) > 0) { sl.scheduleTick(pos, this, Math.max(20, (int) w.wardRestLeft(sl))); return; }
            sl.setBlock(pos, st.setValue(MODE, READY), Block.UPDATE_ALL);
            sl.playSound(null, pos, net.jj.mountain.ModSounds.HEART, SoundSource.BLOCKS, net.jj.mountain.ModSounds.vol(1.4f), 0.8f);
        }
    }

    /** put down fresh it shows whatever the world says it is, so a dug-up one is not a way round the rest */
    @Override
    public void onPlace(BlockState st, Level lvl, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(st, lvl, pos, old, moved);
        if (!(lvl instanceof ServerLevel sl) || old.is(this)) return;
        var w = net.jj.mountain.world.MountainWorld.get(sl.getServer().overworld());
        long rest = w.wardRestLeft(sl);
        int want = w.warding(sl) ? DARK : rest > 0 ? DARK : READY;    // a second heart cannot double the ward
        if (st.getValue(MODE) != want) sl.setBlock(pos, st.setValue(MODE, want), Block.UPDATE_ALL);
        if (want == DARK) sl.scheduleTick(pos, this, Math.max(20, (int) Math.max(rest, w.wardLeft(sl))));
    }

    /** digging up the heart that is holding him off stops it holding him off */
    @Override
    public void onRemove(BlockState st, Level lvl, BlockPos pos, BlockState to, boolean moved) {
        if (!to.is(this) && lvl instanceof ServerLevel sl && st.getValue(MODE) == WARDING) {
            var w = net.jj.mountain.world.MountainWorld.get(sl.getServer().overworld());
            var at = w.wardSpot();
            if (at != null && at.getX() == pos.getX() && at.getZ() == pos.getZ()) {
                w.endWard();
                for (ServerPlayer p : sl.players())
                    if (p.distanceToSqr(Vec3.atCenterOf(pos)) < 160 * 160)
                        p.displayClientMessage(Component.translatable("message.mountain_breathes.ward_broken"), false);
            }
        }
        super.onRemove(st, lvl, pos, to, moved);
    }

    @Override
    public void animateTick(BlockState st, Level lvl, BlockPos pos, RandomSource rnd) {
        int mode = st.getValue(MODE);
        if (mode == DARK) return;
        boolean hard = mode == WARDING;
        // it beats: fast and hard while it is holding him off, slow and quiet while it waits
        long beat = lvl.getGameTime() % (hard ? 14 : 30);
        if (beat > 2) return;
        int n = hard ? 10 : 4;
        for (int i = 0; i < n; i++)
            lvl.addParticle(hard ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SQUID_INK,
                    pos.getX() + rnd.nextDouble(), pos.getY() + rnd.nextDouble(), pos.getZ() + rnd.nextDouble(),
                    0, hard ? 0.06 : 0.02, 0);
        if (!hard) return;
        // and a ring rolling out along the ground so you can see it is doing something
        double r = 3 + rnd.nextDouble() * 5;
        for (int i = 0; i < 12; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            lvl.addParticle(ParticleTypes.SCULK_SOUL, pos.getX() + 0.5 + Math.cos(a) * r, pos.getY() + 0.2, pos.getZ() + 0.5 + Math.sin(a) * r, 0, 0.01, 0);
        }
    }

    /** the light it gives off: dark when spent, warm when ready, blinding while it is holding him off */
    public static int lightOf(BlockState st) {
        return switch (st.getValue(MODE)) { case WARDING -> 15; case READY -> 9; default -> 2; };
    }

    public static int mode(BlockState st) { return st.getValue(MODE); }
    public static int clampMins(long t) { return Mth.clamp((int) (t / 1200), 0, 999); }
}
