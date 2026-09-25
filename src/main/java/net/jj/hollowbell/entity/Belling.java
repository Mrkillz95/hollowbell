package net.jj.hollowbell.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A Belling: an egg clump that broke off him and hatched. A small jelly, drawn as a tiny copy of him, that drifts
 * after you and stings. It comes apart after a few minutes.
 */
public class Belling extends Monster {
    private @Nullable UUID owner;
    private int life = 20 * 180, stingIn;

    public Belling(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 8.0).add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 2.0).add(Attributes.FOLLOW_RANGE, 32.0).add(Attributes.FLYING_SPEED, 0.3);
    }

    public void setOwner(HollowbellEntity h) { owner = h.getUUID(); }

    private @Nullable HollowbellEntity owner() {
        if (owner == null || !(level() instanceof ServerLevel sl)) return null;
        return sl.getEntity(owner) instanceof HollowbellEntity h ? h : null;
    }

    @Override protected void registerGoals() {}

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (--life <= 0) { discard(); return; }
        LivingEntity t = getTarget();
        HollowbellEntity h = owner();
        if (t != null && (!t.isAlive() || t.distanceToSqr(this) > 48 * 48 || (h != null && h.spares(t)) || (t instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            setTarget(null); t = null;
        }
        if (t == null && tickCount % 20 == 0) {
            Player best = null; double bd = 24 * 24;
            for (Player p : level().players()) {
                if (p.isCreative() || p.isSpectator() || (h != null && h.spares(p))) continue;
                double d = p.distanceToSqr(this);
                if (d < bd) { bd = d; best = p; }
            }
            if (best != null) setTarget(best);
        }
        // it drifts in little pulses, like him
        float pulse = Mth.sin(tickCount * 0.25f) * 0.5f + 0.5f;
        Vec3 v = getDeltaMovement().scale(0.9);
        if (t != null) {
            Vec3 to = t.position().add(0, t.getBbHeight() * 0.6, 0).subtract(position());
            if (to.lengthSqr() > 0.01) v = v.add(to.normalize().scale(0.035 * (0.4 + pulse)));
        } else v = v.add(0, (Mth.sin(tickCount * 0.05f) * 0.01), 0);
        if (getY() - level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, getBlockX(), getBlockZ()) < 1.2) v = v.add(0, 0.02, 0);
        setDeltaMovement(v);
        if (t != null && --stingIn <= 0 && getBoundingBox().inflate(0.4).intersects(t.getBoundingBox())) {
            stingIn = 20;
            t.hurt(damageSources().mobAttack(this), 2f);
            t.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
            playSound(SoundEvents.BEE_STING, 0.8f, 1.2f);
        }
    }

    @Override public void travel(Vec3 in) { move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement()); }
    @Override protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState st, net.minecraft.core.BlockPos pos) {}
    @Override public boolean causeFallDamage(float d, float m, DamageSource s) { return false; }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.AMETHYST_BLOCK_CHIME; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.SLIME_HURT_SMALL; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.SLIME_DEATH_SMALL; }
    @Override public boolean removeWhenFarAway(double d) { return true; }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        Entity a = src.getEntity();
        if (a instanceof HollowbellEntity || a instanceof Belling) return false;
        return super.hurt(src, amount);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Life", life);
        if (owner != null) tag.putUUID("Bell", owner);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Life")) life = tag.getInt("Life");
        owner = tag.hasUUID("Bell") ? tag.getUUID("Bell") : null;
    }
}
