package net.jj.hollowbell.entity;

import net.jj.hollowbell.ModEntities;
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

/**
 * Something he is holding rides this while he carries it: at the end of a strand, inside his dome, round an arm,
 * or on his crown. He moves the seat every tick; when he lets go the seat goes.
 */
public class Seat extends Entity {
    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(Seat.class, EntityDataSerializers.INT);
    /** what the seat follows (see HollowbellEntity.seatSpot): 0 nothing (the server moves it), then which */
    private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(Seat.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_INDEX = SynchedEntityData.defineId(Seat.class, EntityDataSerializers.INT);
    public static final int FREE = 0, STRAND_TIP = 1, ARM_TIP = 2, INSIDE = 3, CROWN = 4;
    private int empty;
    private int ownerless;

    public Seat(EntityType<? extends Seat> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvisible(true);
    }

    public Seat(Level level, HollowbellEntity owner) {
        this(ModEntities.SEAT, level);
        this.entityData.set(DATA_OWNER, owner.getId());
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { b.define(DATA_OWNER, -1); b.define(DATA_MODE, FREE); b.define(DATA_INDEX, 0); }

    public int ownerId() { return entityData.get(DATA_OWNER); }

    /** the seat follows a point of him: both sides work it out from his own pose, so it never lags behind */
    public void follow(int mode, int index) { entityData.set(DATA_MODE, mode); entityData.set(DATA_INDEX, index); }
    public int mode() { return entityData.get(DATA_MODE); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (mode() != FREE && level().getEntity(ownerId()) instanceof HollowbellEntity h && !h.isRemoved()) {
                Entity rider = getFirstPassenger();
                Vec3 at = h.seatSpot(mode(), entityData.get(DATA_INDEX), rider);
                setPos(at.x, at.y, at.z);
            }
            return;
        }
        if (getPassengers().isEmpty()) { if (++empty > 10) discard(); } else empty = 0;
        Entity o = level().getEntity(ownerId());
        boolean gone = !(o instanceof HollowbellEntity h) || h.isRemoved() || !h.usesSeat(this);
        if (gone) { if (++ownerless > 2) { ejectPassengers(); discard(); } } else ownerless = 0;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        // a following seat is placed by the client itself
        if (mode() == FREE) super.lerpTo(x, y, z, yRot, xRot, steps);
    }

    @Override protected Vec3 getPassengerAttachmentPoint(Entity e, EntityDimensions d, float f) { return Vec3.ZERO; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean hurt(DamageSource src, float amount) { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected boolean canAddPassenger(Entity e) { return getPassengers().isEmpty(); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
