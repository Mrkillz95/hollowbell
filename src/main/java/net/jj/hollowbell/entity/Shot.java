package net.jj.hollowbell.entity;

import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.ModSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Something he throws: a stinger flicked off a strand end (sting volley, stinger storm), or an egg clump dropped
 * from his strands (egg rain) that bursts where it lands and may hatch a Belling.
 */
public class Shot extends ThrowableItemProjectile {
    public static final int STINGER = 0, EGG = 1;
    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(Shot.class, EntityDataSerializers.INT);
    private float damage = 4f, radius = 2f;
    private int life = 200;

    public Shot(EntityType<? extends Shot> type, Level level) { super(type, level); }

    public Shot(Level level, HollowbellEntity owner, int kind, float damage, float radius) {
        super(ModEntities.SHOT, owner, level);
        entityData.set(DATA_KIND, kind);
        setItem(new ItemStack(kind == EGG ? Items.GRAY_CONCRETE : ModItems.STINGER));
        this.damage = damage;
        this.radius = radius;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { super.defineSynchedData(b); b.define(DATA_KIND, STINGER); }
    public int kind() { return entityData.get(DATA_KIND); }
    @Override protected Item getDefaultItem() { return ModItems.STINGER; }
    @Override protected double getDefaultGravity() { return kind() == EGG ? 0.05 : 0.02; }

    private @Nullable HollowbellEntity bell() { return getOwner() instanceof HollowbellEntity h ? h : null; }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && --life <= 0) discard();
        if (level().isClientSide && kind() == STINGER && tickCount % 2 == 0)
            level().addParticle(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 0, 0, 0);
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        if (e instanceof HollowbellEntity || e instanceof Belling || e instanceof Seat || e instanceof Shot) return false;
        HollowbellEntity h = bell();
        if (h != null && (h.spares(e) || h.moves().caught(e))) return false;
        return super.canHitEntity(e);
    }

    @Override
    protected void onHitEntity(EntityHitResult r) {
        super.onHitEntity(r);
        if (level().isClientSide || !(r.getEntity() instanceof LivingEntity le)) return;
        if (kind() == STINGER) {
            sting(le);
            playSound(ModSounds.STING, 1f, 0.8f + random.nextFloat() * 0.3f);
        }
    }

    private void sting(LivingEntity le) {
        HollowbellEntity h = bell();
        float d = h != null ? h.dmg(damage, le) : damage;
        le.invulnerableTime = 0;
        le.hurt(damageSources().mobProjectile(this, h), d);
        le.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
        le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
    }

    @Override
    protected void onHit(HitResult r) {
        super.onHit(r);
        if (level().isClientSide) return;
        if (kind() == EGG) burst();
        else if (level() instanceof ServerLevel sl)
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), getX(), getY(), getZ(), 6, 0.1, 0.1, 0.1, 0.05);
        discard();
    }

    /** an egg clump lands: it bursts, hurting what's round it, and sometimes a Belling hatches */
    private void burst() {
        if (!(level() instanceof ServerLevel sl)) return;
        HollowbellEntity h = bell();
        Vec3 c = position();
        sl.playSound(null, c.x, c.y, c.z, ModSounds.EGG_BURST, SoundSource.HOSTILE, 1.5f * HollowbellConfig.V.soundVolume, 0.8f + random.nextFloat() * 0.3f);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAY_CONCRETE.defaultBlockState()), c.x, c.y + 0.3, c.z, 40, radius * 0.4, 0.3, radius * 0.4, 0.15);
        sl.sendParticles(ParticleTypes.ITEM_SLIME, c.x, c.y + 0.3, c.z, 20, radius * 0.4, 0.3, radius * 0.4, 0.1);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius), e -> canHitEntity(e) && e.isAlive())) {
            if (e.position().distanceTo(c) > radius) continue;
            e.invulnerableTime = 0;
            e.hurt(damageSources().mobProjectile(this, h), h != null ? h.dmg(damage, e) : damage);
            e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
        }
        if (h != null && random.nextInt(2) == 0 && sl.getEntitiesOfClass(Belling.class, new AABB(c, c).inflate(48)).size() < 10) {
            Belling b = new Belling(ModEntities.BELLING, sl);
            b.setPos(c.x, c.y + 0.5, c.z);
            b.setOwner(h);
            if (h.getTarget() != null) b.setTarget(h.getTarget());
            sl.addFreshEntity(b);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Kind", kind()); tag.putFloat("Damage", damage); tag.putFloat("Radius", radius); tag.putInt("Life", life);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_KIND, tag.getInt("Kind")); damage = tag.getFloat("Damage"); radius = tag.getFloat("Radius"); life = tag.getInt("Life");
    }

    @Override public boolean isPickable() { return false; }
}
