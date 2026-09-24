package net.jj.mountain.entity.inside;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A fat leech that crawls out of the walls of his gut (or is pumped out of his heart). It inches toward you and,
 * once it bites, latches on and drinks until you kill it.
 */
public class GutLeech extends Monster implements InsideMob {
    private int latched;

    public GutLeech(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 8.0).add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 2.0).add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true));
        this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.8));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, InsideMob::fairGame));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 20, true, false, InsideMob::fairGame));
    }

    public boolean isLatched() { return getVehicle() instanceof LivingEntity; }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity le && !isPassenger() && le.getPassengers().isEmpty() && InsideMob.fairGame(le)) {
            startRiding(le, true);
            latched = 0;
            playSound(SoundEvents.HONEY_BLOCK_STEP, net.jj.mountain.ModSounds.vol(1.2f), 0.6f);
            if (le instanceof ServerPlayer p) {
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(p));
                p.displayClientMessage(Component.translatable("message.mountain_breathes.leech"), true);
            }
        }
        return hit;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (getVehicle() instanceof LivingEntity host) {
            latched++;
            if (!host.isAlive() || !InsideMob.fairGame(host) || latched > 400) { stopRiding(); return; }
            if (latched % 25 == 0) {
                if (host.hurt(damageSources().mobAttack(this), 1.5f)) heal(1.5f);
                ((ServerLevel) level()).sendParticles(ParticleTypes.DAMAGE_INDICATOR, getX(), getY() + 0.3, getZ(), 2, 0.2, 0.1, 0.2, 0.05);
                playSound(SoundEvents.GENERIC_DRINK, net.jj.mountain.ModSounds.vol(0.9f), 0.5f);
            }
        }
    }

    @Override
    public void stopRiding() {
        Entity v = getVehicle();
        super.stopRiding();
        if (v instanceof ServerPlayer p) p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(p));
    }

    @Override
    protected void removePassenger(Entity e) { super.removePassenger(e); }

    @Override public Vec3 getVehicleAttachmentPoint(Entity vehicle) { return new Vec3(0, 0.5, 0); }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.SLIME_SQUISH_SMALL; }
    @Override protected SoundEvent getHurtSound(DamageSource src) { return SoundEvents.SLIME_HURT_SMALL; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.SLIME_DEATH_SMALL; }
    @Override public float getVoicePitch() { return 0.7f + random.nextFloat() * 0.2f; }
    @Override public boolean canChangeDimensions(Level a, Level b) { return false; }
}
