package net.jj.mountain.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * What stands up when he looks at you.
 *
 * Black goo comes up out of the ground in your shape, takes your health and whatever you are holding, and comes
 * at you. It is not on a timer: it stands for exactly as long as he can see you, and the second you put solid
 * world between his eyes and your back it falls apart where it is. He goes on doing everything else in the
 * meantime — the stare costs him nothing to hold.
 */
public class ShadowOfYou extends Monster {
    private @Nullable UUID ownerId;
    private int mountainId = -1;
    /** ticks since it was last allowed to exist, so it does not flicker out on one unlucky line of sight */
    private int unseen;
    private int life;
    /** it never comes back up in the same breath: one per player at a time is the whole point */
    public static final int MAX_LIFE = 20 * 60 * 3;

    public ShadowOfYou(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 5;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.4)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
    }

    /** made out of whoever it is a shadow of: their health, their reach, and what they were holding */
    public void becomeShadowOf(Player who, MountainEntity of) {
        this.ownerId = who.getUUID();
        this.mountainId = of.getId();
        float hp = Math.max(6f, who.getMaxHealth());
        var max = getAttribute(Attributes.MAX_HEALTH);
        if (max != null) max.setBaseValue(hp);
        setHealth(hp);
        ItemStack hand = who.getMainHandItem().copy();
        if (!hand.isEmpty()) setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, hand);
        setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0f);
        // it hits about as hard as they do, so somebody in full gear is fighting somebody in full gear
        var atk = getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(Math.max(3.0, damageOf(who)));
        setTarget(who);
        setCustomName(Component.translatable("entity.mountain_breathes.shadow_of", who.getName()));
    }

    private static double damageOf(Player who) {
        double base = who.getAttributeValue(Attributes.ATTACK_DAMAGE);
        return Math.min(18.0, base * 0.8 + 1.0);
    }

    public @Nullable Player owner() {
        return ownerId == null ? null : level().getPlayerByUUID(ownerId);
    }

    public @Nullable UUID ownerId() { return ownerId; }

    private @Nullable MountainEntity mountain() {
        if (mountainId < 0) return null;
        return level().getEntity(mountainId) instanceof MountainEntity m ? m : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(2) == 0)
                level().addParticle(ParticleTypes.SQUID_INK,
                        getX() + (random.nextDouble() - 0.5) * 0.7, getY() + random.nextDouble() * 1.8, getZ() + (random.nextDouble() - 0.5) * 0.7,
                        0, -0.06, 0);
            return;
        }
        life++;
        if (life > MAX_LIFE) { crumble(); return; }

        Player who = owner();
        MountainEntity m = mountain();
        // no owner, no Mountain, or his eyes are off you: it has nothing holding it up any more
        boolean held = who != null && who.isAlive() && !who.isSpectator()
                && m != null && m.isAlive() && !m.isRemoved() && m.level() == level()
                && m.canSee(who);
        if (held) { unseen = 0; if (getTarget() != who) setTarget(who); }
        else if (++unseen > 20) { crumble(); return; }

        if (tickCount % 3 == 0 && level() instanceof ServerLevel sl)
            sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 0.9, getZ(), 3, 0.28, 0.7, 0.28, 0.01);
    }

    /** it goes back into the ground the way it came out of it */
    public void crumble() {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 0.9, getZ(), 80, 0.4, 0.8, 0.4, 0.12);
            sl.playSound(null, blockPosition(), SoundEvents.HONEY_BLOCK_BREAK, net.minecraft.sounds.SoundSource.HOSTILE,
                    net.jj.mountain.ModSounds.vol(1.1f), 0.5f);
            Player who = owner();
            if (who instanceof ServerPlayer sp)
                sp.displayClientMessage(Component.translatable("message.mountain_breathes.shadow_gone"), true);
        }
        discard();
    }

    /** the goo it is made of comes out of the ground with it */
    public void rise(ServerLevel sl) {
        sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 0.2, getZ(), 140, 0.5, 0.35, 0.5, 0.18);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.4, getZ(), 30, 0.4, 0.5, 0.4, 0.02);
        sl.playSound(null, blockPosition(), SoundEvents.HONEY_BLOCK_PLACE, net.minecraft.sounds.SoundSource.HOSTILE,
                net.jj.mountain.ModSounds.vol(1.4f), 0.45f);
    }

    /** whoever it is a shadow of is the only one it wants, and nothing else should pick a fight with it */
    @Override
    public boolean canAttack(LivingEntity e) {
        return e instanceof Player p && p.getUUID().equals(ownerId);
    }

    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean isPersistenceRequired() { return true; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (src.getEntity() instanceof MountainEntity) return false;    // he does not fight his own shadow
        return super.hurt(src, amount);
    }

    @Override
    public void die(DamageSource src) {
        super.die(src);
        if (level() instanceof ServerLevel sl)
            sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 0.9, getZ(), 100, 0.4, 0.8, 0.4, 0.15);
    }

    @Override protected net.minecraft.sounds.SoundEvent getAmbientSound() { return SoundEvents.HONEY_BLOCK_STEP; }
    @Override protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource s) { return SoundEvents.HONEY_BLOCK_BREAK; }
    @Override protected net.minecraft.sounds.SoundEvent getDeathSound() { return SoundEvents.HONEY_BLOCK_BREAK; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("ShadowOf", ownerId);
        tag.putInt("OfMountain", mountainId);
        tag.putInt("ShadowLife", life);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("ShadowOf")) ownerId = tag.getUUID("ShadowOf");
        mountainId = tag.contains("OfMountain") ? tag.getInt("OfMountain") : -1;
        life = tag.getInt("ShadowLife");
    }

    /** where one should come up: on the ground right behind whoever it is a shadow of */
    public static Vec3 spotFor(Player who) {
        Vec3 back = who.getLookAngle().scale(-1.6);
        return new Vec3(who.getX() + back.x, who.getY(), who.getZ() + back.z);
    }
}
