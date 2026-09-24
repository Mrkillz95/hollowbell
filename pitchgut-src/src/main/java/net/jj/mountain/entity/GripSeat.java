package net.jj.mountain.entity;

import net.jj.mountain.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** The inside of one of his hands: whatever he has grabbed rides this while it is passed along his back. */
public class GripSeat extends Entity {
    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(GripSeat.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_RIDE = SynchedEntityData.defineId(GripSeat.class, EntityDataSerializers.BOOLEAN);
    private int empty;

    /** true when this is the saddle on top of his head rather than the inside of a hand */
    public boolean isRide() { return this.entityData.get(DATA_RIDE); }
    public void markRide() { this.entityData.set(DATA_RIDE, true); }

    public GripSeat(EntityType<? extends GripSeat> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvisible(true);
    }

    public GripSeat(Level level, MountainEntity owner) {
        this(ModEntities.GRIP, level);
        this.entityData.set(DATA_OWNER, owner.getId());
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(DATA_OWNER, -1); b.define(DATA_RIDE, false); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (getPassengers().isEmpty()) { if (++empty > 10) discard(); } else empty = 0;
        Entity o = level().getEntity(entityData.get(DATA_OWNER));
        // a hand seat lasts as long as he is holding something; the seat he carries a driver in lasts as long
        // as somebody is driving him or being lifted up to it
        boolean gone = !(o instanceof MountainEntity m) || m.isRemoved()
                || (isRide() ? !m.carryingSomebody() : m.held() == null);
        if (gone) { ejectPassengers(); discard(); }
    }

    @Override protected Vec3 getPassengerAttachmentPoint(Entity e, EntityDimensions d, float f) { return new Vec3(0, 0, 0); }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean hurt(DamageSource src, float amount) { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected boolean canAddPassenger(Entity e) { return getPassengers().isEmpty(); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
