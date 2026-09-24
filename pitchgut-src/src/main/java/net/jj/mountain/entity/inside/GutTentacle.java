package net.jj.mountain.entity.inside;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A tentacle that bursts up out of the floor of his gut. It sways, lashes down at whoever comes near, or wraps round
 * them and squeezes until they hack at it enough to be let go. It never moves from where it came up; after a while
 * it sinks back down.
 */
public class GutTentacle extends Monster implements InsideMob {
    public static final int EMERGE = 0, IDLE = 1, LASH = 2, GRAB = 3, HOLD = 4, SINK = 5;
    public static final float HEIGHT = 8f, REACH = 8.5f;
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(GutTentacle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_AIM = SynchedEntityData.defineId(GutTentacle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DIST = SynchedEntityData.defineId(GutTentacle.class, EntityDataSerializers.FLOAT);
    private int phase = EMERGE, t, cooldown = 30, life, heldHurt, lifespan = -1;
    private @Nullable net.jj.mountain.entity.MountainEntity summoner;

    /** one called up out of the ground by his attack outside: it goes back down sooner, and fights on his side */
    public void summonedBy(net.jj.mountain.entity.MountainEntity m, int ticks) { summoner = m; lifespan = ticks; }
    private @Nullable LivingEntity victim;

    public GutTentacle(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setPersistenceRequired();
        this.xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.ARMOR, 2.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_PHASE, 0); b.define(DATA_AIM, 0f); b.define(DATA_DIST, 4f);
    }

    public int phase() { return entityData.get(DATA_PHASE) >>> 16; }
    public int phaseTime() { return entityData.get(DATA_PHASE) & 0xffff; }
    /** which way it leans to strike (degrees), and how far away the thing it's after is */
    public float aim() { return entityData.get(DATA_AIM); }
    public float aimDist() { return entityData.get(DATA_DIST); }

    private void set(int p) { phase = p; t = 0; }

    @Override
    public void tick() {
        this.setDeltaMovement(Vec3.ZERO);
        super.tick();
        if (level().isClientSide) return;
        t++; life++;
        if (cooldown > 0) cooldown--;
        switch (phase) {
            case EMERGE -> emerge();
            case IDLE -> idle();
            case LASH -> lash();
            case GRAB -> grab();
            case HOLD -> hold();
            case SINK -> { if (t >= 30) discard(); else dust(4); }
            default -> {}
        }
        entityData.set(DATA_PHASE, (phase << 16) | Math.min(t, 0xffff));
    }

    private void dust(int n) {
        ((ServerLevel) level()).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PINK_TERRACOTTA.defaultBlockState()), getX(), getY() + 0.2, getZ(), n, 0.8, 0.1, 0.8, 0.1);
        ((ServerLevel) level()).sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 0.2, getZ(), n / 2 + 1, 0.8, 0.1, 0.8, 0.05);
    }

    private void emerge() {
        if (t == 1) playSound(SoundEvents.WARDEN_EMERGE, net.jj.mountain.ModSounds.vol(1.2f), 1.4f);
        dust(t < 30 ? 6 : 2);
        if (t >= 34) set(IDLE);
    }

    private @Nullable LivingEntity nearest() {
        LivingEntity best = null; double bd = REACH * REACH;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(REACH, 3, REACH), x -> InsideMob.fairGame(x, summoner))) {
            double d = e.distanceToSqr(getX(), e.getY(), getZ());
            if (d < bd && hasLineOfSight(e)) { bd = d; best = e; }
        }
        return best;
    }

    private void face(LivingEntity e) {
        entityData.set(DATA_AIM, (float) (Mth.atan2(e.getZ() - getZ(), e.getX() - getX()) * Mth.RAD_TO_DEG) - 90f);
        entityData.set(DATA_DIST, (float) Math.hypot(e.getX() - getX(), e.getZ() - getZ()));
    }

    private void idle() {
        if (life > (lifespan > 0 ? lifespan : 900 + getId() % 200)) { set(SINK); playSound(SoundEvents.HONEY_BLOCK_SLIDE, net.jj.mountain.ModSounds.vol(1.2f), 0.5f); return; }
        LivingEntity e = nearest();
        if (e == null || cooldown > 0) return;
        victim = e;
        face(e);
        double d = Math.hypot(e.getX() - getX(), e.getZ() - getZ());
        if (d < 4.2 && random.nextInt(3) == 0) set(GRAB); else set(LASH);
        playSound(SoundEvents.WARDEN_TENDRIL_CLICKS, net.jj.mountain.ModSounds.vol(1.5f), 0.6f);
    }

    /** rear back for 14 ticks, then whip down across where they stand */
    private void lash() {
        if (victim != null && t < 12 && victim.isAlive()) face(victim);
        if (t == 15) playSound(SoundEvents.PLAYER_ATTACK_SWEEP, net.jj.mountain.ModSounds.vol(1.4f), 0.5f);
        if (t == 17) {
            float a = (aim() + 90f) * Mth.DEG_TO_RAD;
            Vec3 dir = new Vec3(Mth.cos(a), 0, Mth.sin(a));
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(REACH, 2, REACH), x -> InsideMob.fairGame(x, summoner))) {
                Vec3 v = new Vec3(e.getX() - getX(), 0, e.getZ() - getZ());
                double along = v.dot(dir), side = v.subtract(dir.scale(along)).length();
                if (along < 0.5 || along > REACH + 0.5 || side > 1.6 + e.getBbWidth() / 2) continue;
                if (e.hurt(damageSources().mobAttack(this), 5f)) {
                    e.knockback(1.1, -dir.x, -dir.z);
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.35, 0));
                    e.hurtMarked = true;
                }
            }
            Vec3 hit = position().add(dir.scale(Math.min(aimDist(), REACH)));
            ((ServerLevel) level()).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PINK_TERRACOTTA.defaultBlockState()), hit.x, hit.y + 0.2, hit.z, 20, 0.8, 0.1, 0.8, 0.15);
            level().playSound(null, hit.x, hit.y, hit.z, SoundEvents.SLIME_BLOCK_FALL, SoundSource.HOSTILE, net.jj.mountain.ModSounds.vol(1.6f), 0.5f);
        }
        if (t >= 34) { set(IDLE); cooldown = 25 + random.nextInt(30); }
    }

    /** reach out and wrap round them */
    private void grab() {
        if (victim == null || !victim.isAlive()) { set(IDLE); return; }
        face(victim);
        if (t == 8) {
            double d = Math.hypot(victim.getX() - getX(), victim.getZ() - getZ());
            if (d < 5.0 && Math.abs(victim.getY() - getY()) < 4 && InsideMob.fairGame(victim)) {
                set(HOLD); heldHurt = 0;
                playSound(SoundEvents.SLIME_ATTACK, net.jj.mountain.ModSounds.vol(1.6f), 0.5f);
                if (victim instanceof ServerPlayer p) p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.mountain_breathes.tentacle_grab"), true);
            } else { set(IDLE); cooldown = 20; }
        }
    }

    /** squeezing: hit it enough and it lets go */
    private void hold() {
        LivingEntity v = victim;
        if (v == null || !v.isAlive() || !InsideMob.fairGame(v) || t > 90 || heldHurt >= 6) { release(); return; }
        float a = (aim() + 90f) * Mth.DEG_TO_RAD;
        Vec3 at = position().add(Mth.cos(a) * 1.3, 3.2 + 0.3 * Mth.sin(t * 0.3f), Mth.sin(a) * 1.3);
        if (v instanceof ServerPlayer p) p.connection.teleport(at.x, at.y, at.z, p.getYRot(), p.getXRot());
        else v.teleportTo(at.x, at.y, at.z);
        v.setDeltaMovement(Vec3.ZERO);
        v.fallDistance = 0;
        if (t % 20 == 10) {
            v.hurt(damageSources().mobAttack(this), 2.5f);
            playSound(SoundEvents.SLIME_SQUISH, net.jj.mountain.ModSounds.vol(1.2f), 0.5f);
        }
        v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2), this);
    }

    private void release() {
        LivingEntity v = victim;
        if (v != null && v.isAlive()) {
            float a = (aim() + 90f) * Mth.DEG_TO_RAD;
            v.setDeltaMovement(Mth.cos(a) * 0.9, 0.6, Mth.sin(a) * 0.9);
            v.hurtMarked = true;
        }
        victim = null;
        set(IDLE); cooldown = 50;
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        boolean r = super.hurt(src, amount);
        if (r && phase == HOLD) heldHurt += Math.max(1, (int) amount);
        return r;
    }

    @Override
    protected void tickDeath() {
        // it doesn't fall over, it slithers back into the floor
        ++this.deathTime;
        if (!level().isClientSide && deathTime % 3 == 0) dust(4);
        if (this.deathTime >= 24 && !level().isClientSide) this.remove(RemovalReason.KILLED);
    }

    @Override public boolean isPushable() { return false; }
    @Override public void push(Entity e) {}
    @Override protected void pushEntities() {}
    @Override public void knockback(double d, double e, double f) {}
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean canChangeDimensions(Level a, Level b) { return false; }
    @Override protected SoundEvent getHurtSound(DamageSource src) { return SoundEvents.SLIME_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.SLIME_DEATH; }
    @Override public float getVoicePitch() { return 0.6f; }
    @Override public boolean shouldDropExperience() { return true; }

    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putInt("Life", life); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); life = tag.getInt("Life"); set(IDLE); }
}
