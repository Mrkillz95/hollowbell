package net.jj.hollowbell.entity;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.rig.BellRig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The Stinger thrown like a barbed harpoon, on a strand that stays tied to your hand, up to 36 blocks. What it sticks
 * in is hurt, pinned where it stands for three seconds and badly poisoned. Sneak as you throw and the strand reels
 * you to wherever it stuck: a creature, a wall, the ground, or him. The Stinger itself never leaves your hand; this
 * is the strand end that flies.
 */
public class StingerHook extends Projectile {
    public static final int FLYING = 0, REEL_ME = 2;
    public static final double RANGE = 36;
    /** how long the barb pins what it sticks in (ticks; half on a player, never a giant or a boss) */
    public static final int PIN = 60;
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(StingerHook.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HOOKED =
            SynchedEntityData.defineId(StingerHook.class, EntityDataSerializers.INT);

    private boolean meAlways;
    private float damage = 6f;
    private int flown, reeled;
    private @Nullable Vec3 anchor; // where it stuck, for a block or a spot on him
    private int anchorBell = -1; // the Hollowbell it stuck in (it rides along with him)
    private @Nullable Vector3f anchorRest;
    private int anchorBone = -1;

    public StingerHook(EntityType<? extends StingerHook> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public StingerHook(Level level, LivingEntity owner, boolean meAlways, float damage) {
        this(ModEntities.STINGER_HOOK, level);
        setOwner(owner);
        this.meAlways = meAlways;
        this.damage = damage;
        Vec3 eye = owner.getEyePosition();
        setPos(eye.x, eye.y - 0.15, eye.z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(DATA_STATE, FLYING);
        b.define(DATA_HOOKED, -1);
    }

    public int state() {
        return entityData.get(DATA_STATE);
    }

    public @Nullable Entity hooked() {
        int id = entityData.get(DATA_HOOKED);
        return id < 0 ? null : level().getEntity(id);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 128 * 128;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    } // a throw doesn't outlive a save

    @Override
    public void tick() {
        super.tick();
        Entity owner = getOwner();
        if (level().isClientSide) {
            if (state() == FLYING && tickCount % 2 == 0)
                level().addParticle(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 0, 0, 0);
            Vec3 v = getDeltaMovement();
            setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
            return;
        }
        if (!(owner instanceof LivingEntity me)
                || !owner.isAlive()
                || owner.level() != level()
                || owner.distanceTo(this) > RANGE + 8) {
            discard();
            return;
        }
        switch (state()) {
            case FLYING -> fly(me);
            case REEL_ME -> reelMe(me);
            default -> discard();
        }
    }

    private void fly(LivingEntity me) {
        Vec3 v = getDeltaMovement().add(0, -0.03, 0);
        setDeltaMovement(v);
        Vec3 from = position(), to = from.add(v);
        // him first: his real shape, not his small middle box
        for (HollowbellEntity h : level().getEntitiesOfClass(
                        HollowbellEntity.class, new AABB(from, to).inflate(260), x -> !x.isDeadOrDying())) {
            BellRig.Hit hit = h.raycast(from, v, v.length() + 0.5);
            if (hit == null) continue;
            Vector3f rest = new Vector3f(hit.vx() + 0.5f, hit.vy() + 0.5f, hit.vz() + 0.5f);
            Vec3 at = h.boneWorld(hit.bone(), rest);
            setPos(at.x, at.y, at.z);
            if (me instanceof Player p) h.applyDamage(damageSources().thrown(this, p), damage, hit.bone(), false);
            if (!meAlways) {
                barb();
                return;
            }
            anchorBell = h.getId();
            anchorBone = hit.bone();
            anchorRest = rest;
            anchor = at;
            stick(REEL_ME, h);
            return;
        }
        HitResult r = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (r.getType() == HitResult.Type.ENTITY) {
            Entity e = ((EntityHitResult) r).getEntity();
            LivingEntity le =
                    e.getTags().contains(Giants.TAG) ? Giants.ownerOf(e) : e instanceof LivingEntity l ? l : null;
            setPos(r.getLocation());
            if (le != null) {
                le.hurt(damageSources().thrown(this, me), damage);
                le.addEffect(new MobEffectInstance(MobEffects.POISON, PIN, 2), me);
                pin(le, me);
            }
            if (!meAlways) {
                barb();
                return;
            }
            anchor = null;
            stick(REEL_ME, le != null ? le : e);
            return;
        }
        if (r.getType() == HitResult.Type.BLOCK) {
            BlockHitResult b = (BlockHitResult) r;
            setPos(b.getLocation());
            if (!meAlways) {
                barb();
                return;
            }
            anchor = b.getLocation();
            stick(REEL_ME, null);
            return;
        }
        setPos(to.x, to.y, to.z);
        // too far and nothing caught: the strand pulls it back
        if (++flown > 24 || me.distanceTo(this) > RANGE) discard();
    }

    /** held where it stands: it can't move for a moment (not a giant or a boss, and a player only half as long) */
    public static void pin(LivingEntity e, LivingEntity by) {
        if (Giants.isGiant(e) || e.getType().is(ConventionalEntityTypeTags.BOSSES)) return;
        int ticks = e instanceof Player ? PIN / 2 : PIN;
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6), by);
        e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
        e.hurtMarked = true;
    }

    /** not reeling: the barb bites and the strand pulls the end back */
    private void barb() {
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1f, 1.5f);
        if (level() instanceof ServerLevel sl)
            sl.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 10, 0.2, 0.2, 0.2, 0.05);
        discard();
    }

    private void stick(int how, @Nullable Entity what) {
        setDeltaMovement(Vec3.ZERO);
        entityData.set(DATA_STATE, how);
        entityData.set(DATA_HOOKED, what == null ? -1 : what.getId());
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1f, 1.3f);
        level().playSound(
                        null,
                        getX(),
                        getY(),
                        getZ(),
                        SoundEvents.FISHING_BOBBER_RETRIEVE,
                        SoundSource.PLAYERS,
                        1f,
                        0.7f);
        if (level() instanceof ServerLevel sl)
            sl.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 10, 0.2, 0.2, 0.2, 0.05);
    }

    /** where the strand is tied, right now */
    private @Nullable Vec3 anchorNow() {
        if (anchorBell >= 0) {
            return level().getEntity(anchorBell) instanceof HollowbellEntity h && !h.isRemoved() && anchorRest != null
                    ? h.boneWorld(anchorBone, anchorRest)
                    : null;
        }
        if (anchor != null) return anchor;
        Entity e = hooked();
        return e != null && e.isAlive() ? e.position().add(0, e.getBbHeight() * 0.5, 0) : null;
    }

    /** you are pulled along the strand to where it stuck, and let go just short of it */
    private void reelMe(LivingEntity me) {
        Vec3 to = anchorNow();
        if (to == null || ++reeled > 40) {
            discard();
            return;
        }
        setPos(to.x, to.y, to.z);
        Vec3 d = to.subtract(me.position().add(0, me.getBbHeight() * 0.5, 0));
        double dist = d.length();
        me.resetFallDistance();
        if (dist < 2.2) {
            // arrive with a little hop, not a smack
            me.setDeltaMovement(me.getDeltaMovement().scale(0.25).add(0, 0.25, 0));
            me.hurtMarked = true;
            discard();
            return;
        }
        double speed = Math.min(1.6, 0.4 + dist * 0.1);
        me.setDeltaMovement(d.normalize().scale(speed).add(0, 0.04, 0));
        me.hurtMarked = true;
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        if (e == getOwner()
                || e instanceof Seat
                || e instanceof Shot
                || e instanceof StingerHook
                || e instanceof HollowbellEntity) return false;
        if (getOwner() != null && e.isPassengerOfSameVehicle(getOwner())) return false;
        return e.isPickable() || e.getTags().contains(Giants.TAG);
    }
}
