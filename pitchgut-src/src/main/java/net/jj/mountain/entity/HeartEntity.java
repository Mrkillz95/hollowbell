package net.jj.mountain.entity;

import net.jj.mountain.innards.Innards;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** His heart, hanging in the room inside him. It never dies itself: every hit goes to him. */
public class HeartEntity extends Mob {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_BURST =
            net.minecraft.network.syncher.SynchedEntityData.defineId(HeartEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private @Nullable UUID mountainId;
    private int room = -1;

    public HeartEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.setPersistenceRequired();
        this.setNoAi(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1024.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder b) { super.defineSynchedData(b); b.define(DATA_BURST, 0); }

    /** ticks into the big squeeze before a blood burst (0 = not squeezing) */
    public int burst() { return entityData.get(DATA_BURST); }
    public void setBurst(int t) { entityData.set(DATA_BURST, t); }

    public void link(UUID mountain, int room) { this.mountainId = mountain; this.room = room; }
    public @Nullable UUID mountainId() { return mountainId; }
    public int room() { return room; }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide || amount <= 0) return false;
        if (src.is(DamageTypes.GENERIC_KILL)) return super.hurt(src, amount);
        Entity att = src.getEntity();
        if (!(att instanceof Player) && !(src.getDirectEntity() instanceof Projectile)) return false;
        if (!Innards.heartHit(this, src, amount)) return false;
        this.level().broadcastDamageEvent(this, src);
        this.playHurtSound(src);
        return true;
    }

    @Override public void tick() { super.tick(); this.setDeltaMovement(Vec3.ZERO); if (getHealth() < getMaxHealth()) setHealth(getMaxHealth()); }
    @Override public boolean isPushable() { return false; }
    @Override public void push(Entity e) {}
    @Override protected void pushEntities() {}
    @Override public void knockback(double d, double e, double f) {}
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean canChangeDimensions(Level a, Level b) { return false; }
    @Override protected SoundEvent getHurtSound(DamageSource src) { return SoundEvents.SLIME_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.SLIME_DEATH; }
    @Override protected float getSoundVolume() { return 2.5f; }
    @Override public float getVoicePitch() { return 0.4f; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (mountainId != null) tag.putUUID("Mountain", mountainId);
        tag.putInt("Room", room);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Mountain")) mountainId = tag.getUUID("Mountain");
        room = tag.getInt("Room");
    }
}
