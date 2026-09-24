package net.jj.mountain.entity;

import net.jj.mountain.ModEntities;
import net.jj.mountain.ModItems;
import net.jj.mountain.MountainConfig;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A lump of his goo. Big ones are lobbed from his mouth (artillery) and burst into a black puddle; small fast
 * ones are spat from the holes in his skin at anyone climbing him and poison what they hit.
 */
public class GooGlob extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(GooGlob.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_DART = SynchedEntityData.defineId(GooGlob.class, EntityDataSerializers.BOOLEAN);
    private float damage = 4f, radius = 2f;
    private int life;

    public GooGlob(EntityType<? extends GooGlob> type, Level level) { super(type, level); }

    public GooGlob(Level level, MountainEntity owner, Vec3 at, float size, boolean dart, float damage, float radius) {
        super(ModEntities.GOO_GLOB, at.x, at.y, at.z, level);
        setOwner(owner);
        entityData.set(DATA_SIZE, size);
        entityData.set(DATA_DART, dart);
        this.damage = damage; this.radius = radius;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_SIZE, 1f);
        b.define(DATA_DART, false);
    }

    public float size() { return entityData.get(DATA_SIZE); }
    public boolean dart() { return entityData.get(DATA_DART); }

    @Override protected Item getDefaultItem() { return ModItems.GOO; }
    @Override protected double getDefaultGravity() { return dart() ? 0.012 : 0.045; }

    @Override
    protected boolean canHitEntity(Entity e) {
        if (e instanceof MountainEntity || e instanceof MountainPart || e instanceof GripSeat || e instanceof HeartEntity || e instanceof GooGlob
                || e instanceof net.jj.mountain.entity.inside.InsideMob) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (getOwner() instanceof MountainEntity m && m.spares(e)) return false;
        if (getOwner() instanceof net.jj.mountain.entity.inside.WatcherEye) {
            // a loose eye's goo only hits what its Mountain would
            Entity o = getOwner();
            if (o instanceof net.jj.mountain.entity.inside.WatcherEye w && w.summoner() != null && w.summoner().spares(e)) return false;
        }
        return super.canHitEntity(e);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            float s = size();
            for (int i = 0; i < (dart() ? 1 : 3); i++)
                level().addParticle(ParticleTypes.SQUID_INK, getX() + (random.nextDouble() - 0.5) * s, getY() + (random.nextDouble() - 0.5) * s,
                        getZ() + (random.nextDouble() - 0.5) * s, 0, 0, 0);
            if (!dart() && random.nextInt(3) == 0)
                level().addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.BLACK_CONCRETE_POWDER.defaultBlockState()), getX(), getY() - s * 0.5, getZ(), 0, 0, 0);
        } else if (++life > 400) discard();
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (level().isClientSide || isRemoved()) return;
        if (MountainAttacks.DEBUG) net.jj.mountain.MountainMod.LOG.info("glob hit {} at {} after {} ticks", hit.getType(), hit.getLocation(), tickCount);
        splat(hit.getLocation());
        discard();
    }

    private void splat(Vec3 at) {
        ServerLevel sl = (ServerLevel) level();
        Entity owner = getOwner();
        MountainEntity m = owner instanceof MountainEntity me ? me : null;
        float s = size();
        if (dart()) {
            sl.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.05);
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.HOSTILE, net.jj.mountain.ModSounds.vol(1.0f), 0.7f);
        } else {
            sl.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 0.5, at.z, (int) (20 + 25 * s), radius * 0.5, 0.6, radius * 0.5, 0.15);
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLACK_CONCRETE_POWDER.defaultBlockState()), at.x, at.y + 0.3, at.z,
                    (int) (20 + 20 * s), radius * 0.4, 0.3, radius * 0.4, 0.2);
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_BLOCK_FALL, SoundSource.HOSTILE, net.jj.mountain.ModSounds.vol(2.5f), 0.5f);
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.HONEY_BLOCK_BREAK, SoundSource.HOSTILE, net.jj.mountain.ModSounds.vol(2.0f), 0.4f);
            if (m != null && MountainConfig.V.gooTrail) m.gooPatch(at.x, at.z, radius * 0.8, false);
        }
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius), x -> canHitEntity(x) && x.isAlive())) {
            double d = e.getBoundingBox().getCenter().distanceTo(at);
            if (d > radius + e.getBbWidth()) continue;
            float k = dart() ? 1f : (float) Mth.clamp(1.2 - d / (radius + 1), 0.4, 1.0);
            e.hurt(damageSources().thrown(this, owner), damage * k);
            if (dart()) e.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1), owner);
            else {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2), owner);
                e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0), owner);
                Vec3 push = e.position().subtract(at).multiply(1, 0, 1);
                if (push.lengthSqr() > 1e-4) e.setDeltaMovement(e.getDeltaMovement().add(push.normalize().scale(0.6 * k).add(0, 0.35 * k, 0)));
                e.hurtMarked = true;
            }
        }
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putFloat("Size", size()); tag.putBoolean("Dart", dart()); tag.putFloat("Damage", damage); tag.putFloat("Radius", radius); }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_SIZE, tag.getFloat("Size")); entityData.set(DATA_DART, tag.getBoolean("Dart"));
        damage = tag.getFloat("Damage"); radius = tag.getFloat("Radius");
    }
}
