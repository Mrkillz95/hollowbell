package net.jj.mountain.entity.inside;

import net.jj.mountain.entity.GooGlob;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One of his eyes, torn loose, drifting about his gut on its dangling nerves. It keeps its distance, stares at you,
 * then spits a lump of goo.
 */
public class WatcherEye extends Monster implements InsideMob {
    private static final EntityDataAccessor<Integer> DATA_CHARGE = SynchedEntityData.defineId(WatcherEye.class, EntityDataSerializers.INT);
    private int shotIn = 60, charge, lifespan = -1;
    private @Nullable net.jj.mountain.entity.MountainEntity summoner;

    /** torn off him by his attack outside: it hunts for a while, then bursts */
    public void summonedBy(net.jj.mountain.entity.MountainEntity m, int ticks) { summonedBy(m, ticks, null, null); }
    /** thrown at someone: it flies after them from however far away, not just what's within sight of it */
    public void summonedBy(net.jj.mountain.entity.MountainEntity m, int ticks, @Nullable LivingEntity at, @Nullable Vec3 toward) { summoner = m; lifespan = ticks; hunt = at; goTo = toward; }
    private @Nullable LivingEntity hunt;
    private @Nullable Vec3 goTo;
    public @Nullable net.jj.mountain.entity.MountainEntity summoner() { return summoner; }
    private double orbit;
    private static final boolean DEBUG = Boolean.getBoolean("mountain.debug");

    public WatcherEye(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.xpReward = 5;
        this.orbit = random.nextDouble() * Math.PI * 2;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 14.0).add(Attributes.FLYING_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 40.0).add(Attributes.MOVEMENT_SPEED, 0.2);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) { super.defineSynchedData(b); b.define(DATA_CHARGE, 0); }

    /** 0..1 as it winds up to spit (the pupil widens and it glows) */
    public float charge() { return entityData.get(DATA_CHARGE) / 20f; }

    private @Nullable LivingEntity target() {
        if (hunt != null) {
            if (hunt.isAlive() && !hunt.isRemoved() && hunt.level() == level() && hunt.distanceToSqr(this) < 260 * 260 && InsideMob.fairGame(hunt, summoner)) return hunt;
            hunt = null;
        }
        LivingEntity best = null; double bd = 36 * 36;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(36), x -> InsideMob.fairGame(x, summoner))) {
            double d = e.distanceToSqr(this);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (lifespan > 0 && tickCount > lifespan) {
            ((ServerLevel) level()).sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1.5, getZ(), 20, 0.4, 0.4, 0.4, 0.1);
            playSound(SoundEvents.SLIME_BLOCK_BREAK, net.jj.mountain.ModSounds.vol(1.5f), 0.6f);
            discard();
            return;
        }
        LivingEntity tg = target();
        if (tg != null || (goTo != null && goTo.distanceToSqr(position()) < 8 * 8)) goTo = null;   // flown to where he threw it
        if (DEBUG && summoner != null && tickCount % 20 == 0)
            net.jj.mountain.MountainMod.LOG.info("thrown eye {} t={} at {} target {} goTo {} dist {} v {}", getId(), tickCount, position(), tg, goTo, tg == null ? -1 : distanceTo(tg), getDeltaMovement().length());
        Vec3 want;
        if (tg != null) {
            orbit += 0.012;
            double r = 8 + 2 * Math.sin(tickCount * 0.02 + getId());
            want = tg.position().add(Math.cos(orbit) * r, 4.5 + 1.5 * Math.sin(tickCount * 0.05 + getId()), Math.sin(orbit) * r);
            getLookControl().setLookAt(tg, 30f, 30f);
            double dx = tg.getX() - getX(), dz = tg.getZ() - getZ();
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            setYRot(Mth.approachDegrees(getYRot(), yaw, 12f)); setYBodyRot(getYRot()); setYHeadRot(getYRot());
            setXRot((float) -(Mth.atan2(tg.getEyeY() - getEyeY(), Math.hypot(dx, dz)) * Mth.RAD_TO_DEG));
            if (--shotIn <= 0 && hasLineOfSight(tg)) {
                charge++;
                if (charge == 1) playSound(SoundEvents.ELDER_GUARDIAN_AMBIENT, net.jj.mountain.ModSounds.vol(1.0f), 1.6f);
                if (charge >= 20) { spit(tg); charge = 0; shotIn = 55 + random.nextInt(45); }
            } else if (shotIn <= 0) charge = Math.max(0, charge - 1);
        } else if (goTo != null) {
            want = goTo.add(0, 4, 0);
            charge = 0;
        } else {
            want = position().add(Math.sin(tickCount * 0.03 + getId()) * 2, Math.sin(tickCount * 0.02) * 0.5, Math.cos(tickCount * 0.03 + getId()) * 2);
            charge = 0;
        }
        entityData.set(DATA_CHARGE, charge);
        Vec3 d = want.subtract(position());
        double len = d.length();
        // one he threw closes the distance fast, then slows down to circle you like the ones inside him
        boolean rush = summoner != null && (tg != null ? distanceToSqr(tg) > 18 * 18 : goTo != null);
        double acc = rush ? 0.09 : Math.min(0.03, 0.03 * len / 3);
        Vec3 v = getDeltaMovement().scale(0.9).add(len > 1e-3 ? d.scale(acc / len) : Vec3.ZERO);
        if (rush && horizontalCollision) v = v.add(0, 0.2, 0);                  // over hills, not into them
        double cap = rush ? 1.1 : summoner != null && tickCount < 25 ? 1.5 : 0.35;
        if (v.length() > cap) v = v.normalize().scale(cap);
        setDeltaMovement(v);
        move(net.minecraft.world.entity.MoverType.SELF, v);
    }

    private void spit(LivingEntity tg) {
        Vec3 from = getEyePosition().add(getLookAngle().scale(0.9));
        GooGlob g = new GooGlob(level(), (net.jj.mountain.entity.MountainEntity) null, from, 0.6f, false, 3.5f, 1.6f);
        g.setOwner(this);
        Vec3 aim = tg.getBoundingBox().getCenter().add(tg.getDeltaMovement().scale(8)).subtract(from);
        g.setDeltaMovement(aim.normalize().scale(0.9).add(0, aim.length() * 0.012, 0));
        level().addFreshEntity(g);
        playSound(SoundEvents.LLAMA_SPIT, net.jj.mountain.ModSounds.vol(1.4f), 0.6f);
        ((ServerLevel) level()).sendParticles(ParticleTypes.SQUID_INK, from.x, from.y, from.z, 6, 0.2, 0.2, 0.2, 0.05);
    }

    @Override public boolean causeFallDamage(float a, float b, DamageSource s) { return false; }
    @Override protected void checkFallDamage(double y, boolean g, net.minecraft.world.level.block.state.BlockState s, net.minecraft.core.BlockPos p) {}
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.GUARDIAN_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource src) { return SoundEvents.GUARDIAN_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.GUARDIAN_DEATH; }
    @Override public float getVoicePitch() { return 0.7f; }
    @Override public boolean canChangeDimensions(Level a, Level b) { return false; }
}
