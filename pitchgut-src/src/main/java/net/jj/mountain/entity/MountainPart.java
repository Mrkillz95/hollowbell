package net.jj.mountain.entity;

import net.jj.mountain.ModEntities;
import net.jj.mountain.ModItems;
import net.jj.mountain.rig.MountainRig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** One hitbox laid along his body or head. Hits go to the Mountain; the solid ones along his back can be stood on. */
public class MountainPart extends Entity {
    private static final EntityDataAccessor<Integer> DATA_PARENT = SynchedEntityData.defineId(MountainPart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_INDEX = SynchedEntityData.defineId(MountainPart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(MountainPart.class, EntityDataSerializers.FLOAT);
    private int orphanTicks;

    public MountainPart(EntityType<? extends MountainPart> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public MountainPart(Level level, MountainEntity parent, int index) {
        this(ModEntities.MOUNTAIN_PART, level);
        this.entityData.set(DATA_PARENT, parent.getId());
        this.entityData.set(DATA_INDEX, index);
        this.entityData.set(DATA_SCALE, parent.mountainScale());
        this.refreshDimensions();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(DATA_PARENT, -1);
        b.define(DATA_INDEX, 0);
        b.define(DATA_SCALE, 1.0f);
    }

    public int index() { return this.entityData.get(DATA_INDEX); }

    public MountainRig.PartDef def() {
        var parts = MountainRig.get().parts;
        return parts.get(Math.max(0, Math.min(parts.size() - 1, index())));
    }

    void setOwnerScale(float s) { this.entityData.set(DATA_SCALE, s); this.refreshDimensions(); }
    /** how big the mountain this box belongs to is */
    public float ownerScale() { return this.entityData.get(DATA_SCALE); }

    public @Nullable MountainEntity owner() {
        Entity e = level().getEntity(this.entityData.get(DATA_PARENT));
        return e instanceof MountainEntity c && !c.isRemoved() ? c : null;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        MountainRig.PartDef d = def();
        float s = this.entityData.get(DATA_SCALE);
        return EntityDimensions.fixed(Math.max(0.4f, d.width() * s), Math.max(0.4f, d.height() * s));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_INDEX.equals(key) || DATA_SCALE.equals(key)) this.refreshDimensions();
    }

    /** Called by the Mountain every tick, server side. */
    void moveWith(double x, double y, double z) {
        this.xo = this.getX(); this.yo = this.getY(); this.zo = this.getZ();
        this.setPos(x, y, z);
    }

    @Override
    public void tick() {
        this.tickCount++;
        MountainEntity o = owner();
        if (level().isClientSide) {
            if (o != null && o.clientPoseReady()) {
                Vec3 p = o.partFeet(index());
                this.xo = this.getX(); this.yo = this.getY(); this.zo = this.getZ();
                this.setPos(p.x, p.y, p.z);
            }
            return;
        }
        if (o == null || !o.ownsPart(this)) {
            if (++orphanTicks > 20) this.discard();
        } else orphanTicks = 0;
    }

    public void noteAttacker(Player p) {}

    @Override
    public void lerpTo(double x, double y, double z, float yr, float xr, int steps) {
        if (owner() == null) super.lerpTo(x, y, z, yr, xr, steps);        // otherwise we place ourselves from his pose
    }

    @Override public boolean isPickable() {
        MountainEntity o = owner();
        return o != null && (!o.isDeadOrDying() || o.isCarcass());   // the body still has to be hittable to be cut up
    }
    // standing on him is handled by the shape of his back itself (see MountainCollision): these boxes are only for hits
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public void push(Entity e) {}
    @Override public boolean displayFireAnimation() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean is(Entity e) { return this == e || owner() == e; }
    @Override public ItemStack getPickResult() {
        MountainEntity o = owner();
        return new ItemStack(o != null && o.isHunter() ? ModItems.HUNTER_EGG : ModItems.CALM_EGG);
    }
    @Override public boolean isInvulnerableTo(DamageSource src) { return super.isInvulnerableTo(src) || MountainEntity.isImmuneTo(src); }

    @Override
    public net.minecraft.world.InteractionResult interact(Player player, net.minecraft.world.InteractionHand hand) {
        MountainEntity o = owner();
        if (o == null || level().isClientSide) return net.minecraft.world.InteractionResult.PASS;
        if (!player.getItemInHand(hand).is(ModItems.CODEX)) return net.minecraft.world.InteractionResult.PASS;
        if (o.ridden()) { o.dropRider(); return net.minecraft.world.InteractionResult.SUCCESS; }
        return o.possess(player) ? net.minecraft.world.InteractionResult.SUCCESS : net.minecraft.world.InteractionResult.FAIL;
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide) return false;
        if (this.isInvulnerableTo(src)) return false;
        MountainEntity o = owner();
        return o != null && o.hurtFromPart(this, src, amount);
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
