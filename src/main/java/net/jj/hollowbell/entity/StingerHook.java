package net.jj.hollowbell.entity;

import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.rig.BellRig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

/**
 * The Stinger thrown like a harpoon, on a strand that stays tied to your hand. What it sticks in gets reeled:
 * something your size or smaller is dragged to you; a wall, the ground, something big, or him, and you are the one
 * pulled in. Sneak as you throw and you always go to it. The Stinger itself never leaves your hand; this is the
 * strand end that flies.
 */
public class StingerHook extends Projectile {
    public static final int FLYING = 0, REEL_THEM = 1, REEL_ME = 2;
    public static final double RANGE = 36;
    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(StingerHook.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HOOKED = SynchedEntityData.defineId(StingerHook.class, EntityDataSerializers.INT);

    private boolean meAlways;
    private float damage = 6f;
    private int flown, reeled;
    private @Nullable Vec3 anchor;           // where it stuck, for a block or a spot on him
    private int anchorBell = -1;             // the Hollowbell it stuck in (it rides along with him)
    private @Nullable Vector3f anchorRest; private int anchorBone = -1;

    public StingerHook(EntityType<? extends StingerHook> type, Level level) { super(type, level); noPhysics = true; }

    public StingerHook(Level level, LivingEntity owner, boolean meAlways, float damage) {
        this(ModEntities.STINGER_HOOK, level);
        setOwner(owner);
        this.meAlways = meAlways;
        this.damage = damage;
        Vec3 eye = owner.getEyePosition();
        setPos(eye.x, eye.y - 0.15, eye.z);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(DATA_STATE, FLYING); b.define(DATA_HOOKED, -1); }
    public int state() { return entityData.get(DATA_STATE); }
    public @Nullable Entity hooked() { int id = entityData.get(DATA_HOOKED); return id < 0 ? null : level().getEntity(id); }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 128 * 128; }
    @Override protected void addAdditionalSaveData(CompoundTag t) {}
    @Override protected void readAdditionalSaveData(CompoundTag t) {}
    @Override public boolean shouldBeSaved() { return false; }          // a throw doesn't outlive a save

    @Override
    public void tick() {
        super.tick();
        Entity owner = getOwner();
        if (level().isClientSide) {
            if (state() == FLYING && tickCount % 2 == 0) level().addParticle(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 0, 0, 0);
            Vec3 v = getDeltaMovement();
            setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
            return;
        }
        if (!(owner instanceof LivingEntity me) || !owner.isAlive() || owner.level() != level() || owner.distanceTo(this) > RANGE + 8) { discard(); return; }
        switch (state()) {
            case FLYING -> fly(me);
            case REEL_THEM -> reelThem(me);
            case REEL_ME -> reelMe(me);
            default -> discard();
        }
    }

    private void fly(LivingEntity me) {
        Vec3 v = getDeltaMovement().add(0, -0.03, 0);
        setDeltaMovement(v);
        Vec3 from = position(), to = from.add(v);
        // him first: his real shape, not his small middle box
        for (HollowbellEntity h : level().getEntitiesOfClass(HollowbellEntity.class, new AABB(from, to).inflate(260), x -> !x.isDeadOrDying())) {
            BellRig.Hit hit = h.raycast(from, v, v.length() + 0.5);
            if (hit == null) continue;
            Vector3f rest = new Vector3f(hit.vx() + 0.5f, hit.vy() + 0.5f, hit.vz() + 0.5f);
            Vec3 at = h.boneWorld(hit.bone(), rest);
            setPos(at.x, at.y, at.z);
            if (me instanceof Player p) h.applyDamage(damageSources().thrown(this, p), damage, hit.bone(), false);
            anchorBell = h.getId(); anchorBone = hit.bone(); anchorRest = rest; anchor = at;
            stick(REEL_ME, h);
            return;
        }
        HitResult r = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (r.getType() == HitResult.Type.ENTITY) {
            Entity e = ((EntityHitResult) r).getEntity();
            LivingEntity le = e.getTags().contains(Giants.TAG) ? Giants.ownerOf(e) : e instanceof LivingEntity l ? l : null;
            setPos(r.getLocation());
            if (le != null) {
                le.hurt(damageSources().thrown(this, me), damage);
                le.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), me);
            }
            Entity pull = le != null ? le : e;
            boolean big = meAlways || Giants.isGiant(pull) || pull.getBbWidth() > 1.6f || pull.getBbHeight() > 2.8f || pull.getTags().contains(Giants.TAG);
            if (big) anchor = null;
            stick(big ? REEL_ME : REEL_THEM, pull);
            return;
        }
        if (r.getType() == HitResult.Type.BLOCK) {
            BlockHitResult b = (BlockHitResult) r;
            setPos(b.getLocation());
            anchor = b.getLocation();
            stick(REEL_ME, null);
            return;
        }
        setPos(to.x, to.y, to.z);
        // too far and nothing caught: the strand pulls it back
        if (++flown > 24 || me.distanceTo(this) > RANGE) discard();
    }

    private void stick(int how, @Nullable Entity what) {
        setDeltaMovement(Vec3.ZERO);
        entityData.set(DATA_STATE, how);
        entityData.set(DATA_HOOKED, what == null ? -1 : what.getId());
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1f, 1.3f);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 1f, 0.7f);
        if (level() instanceof ServerLevel sl)
            sl.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 10, 0.2, 0.2, 0.2, 0.05);
    }

    /** where the strand is tied, right now */
    private @Nullable Vec3 anchorNow() {
        if (anchorBell >= 0) {
            return level().getEntity(anchorBell) instanceof HollowbellEntity h && !h.isRemoved() && anchorRest != null
                    ? h.boneWorld(anchorBone, anchorRest) : null;
        }
        if (anchor != null) return anchor;
        Entity e = hooked();
        return e != null && e.isAlive() ? e.position().add(0, e.getBbHeight() * 0.5, 0) : null;
    }

    /** a small one is dragged to your feet, a bit up off the ground so it doesn't snag */
    private void reelThem(LivingEntity me) {
        Entity e = hooked();
        if (e == null || !e.isAlive() || ++reeled > 30) { discard(); return; }
        Vec3 to = me.position().add(me.getLookAngle().multiply(1.2, 0, 1.2));
        Vec3 d = to.subtract(e.position());
        double dist = d.length();
        setPos(e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ());
        if (dist < 1.8) { e.setDeltaMovement(e.getDeltaMovement().scale(0.2)); e.hurtMarked = true; discard(); return; }
        double speed = Math.min(1.25, 0.35 + dist * 0.12);
        e.setDeltaMovement(d.normalize().scale(speed).add(0, reeled < 3 ? 0.35 : 0.06, 0));
        e.hurtMarked = true;
        e.resetFallDistance();
    }

    /** you are pulled along the strand to where it stuck, and let go just short of it */
    private void reelMe(LivingEntity me) {
        Vec3 to = anchorNow();
        if (to == null || ++reeled > 40) { discard(); return; }
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
        if (e == getOwner() || e instanceof Seat || e instanceof Shot || e instanceof StingerHook || e instanceof HollowbellEntity) return false;
        if (getOwner() != null && e.isPassengerOfSameVehicle(getOwner())) return false;
        return e.isPickable() || e.getTags().contains(Giants.TAG);
    }
}
