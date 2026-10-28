package net.jj.hollowbell.solid;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The spot a player sits on him (sit anywhere): fixed to one of his frames, as built. Both sides work it out from his
 * own pose every tick, so it goes exactly where that part of him goes: walking, turning, every move. The server
 * decides who sits and when they get up (Sitting); the seat only follows. Never saved: without him it sets its sitter
 * down and goes.
 */
public class SitSeat extends Entity {
    private static final EntityDataAccessor<Integer> DATA_OWNER =
            SynchedEntityData.defineId(SitSeat.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FRAME =
            SynchedEntityData.defineId(SitSeat.class, EntityDataSerializers.INT);
    /** the spot in the frame as built (small numbers, so it keeps its precision far out in the world) */
    private static final EntityDataAccessor<Vector3f> DATA_REST =
            SynchedEntityData.defineId(SitSeat.class, EntityDataSerializers.VECTOR3);

    /**
     * how far over the spot the sitter's riding point is: a player rides 0.6 over their feet, so this puts their hips
     * on the spot (feet 0.65 under it, legs out in front, as on a minecart)
     */
    public static final double SIT_OVER = -0.05;

    int empty, checked;
    float lastYaw = Float.NaN;

    public SitSeat(EntityType<? extends SitSeat> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvisible(true);
    }

    void setup(Entity owner, int frame, Vector3f rest) {
        entityData.set(DATA_OWNER, owner.getId());
        entityData.set(DATA_FRAME, frame);
        entityData.set(DATA_REST, new Vector3f(rest));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(DATA_OWNER, -1);
        b.define(DATA_FRAME, -1);
        b.define(DATA_REST, new Vector3f());
    }

    public int ownerId() {
        return entityData.get(DATA_OWNER);
    }

    public int frame() {
        return entityData.get(DATA_FRAME);
    }

    public Vector3f rest() {
        return new Vector3f(entityData.get(DATA_REST));
    }

    /** the giant this seat is on, if he's there (on this side) */
    public @Nullable SitHost host() {
        Entity o = level().getEntity(ownerId());
        return o instanceof SitHost h && !o.isRemoved() ? h : null;
    }

    /** the spot is there now: his frame is (not broken off, not hidden) and he can be stood on */
    public boolean spotThere(SitHost h) {
        int f = frame();
        if (f < 0 || !h.solidReady()) return false;
        SolidCache c = Solid.frames(h);
        return f < c.on.length && c.on[f];
    }

    /** the spot in the world now (from his pose this tick) */
    public Vec3 spotNow(SitHost h) {
        return Solid.toWorld(Solid.frames(h), frame(), entityData.get(DATA_REST));
    }

    /** moves it to where the spot is now; how far his frame turned since the last time (degrees) */
    public float follow(SitHost h) {
        if (!spotThere(h)) return 0f;
        SolidCache c = Solid.frames(h);
        Vec3 at = Solid.toWorld(c, frame(), entityData.get(DATA_REST));
        setPos(at.x, at.y, at.z);
        setDeltaMovement(Vec3.ZERO);
        float yaw = Solid.yawOf(c.toWorld[frame()]);
        float turned = Float.isNaN(lastYaw) ? 0f : Mth.wrapDegrees(yaw - lastYaw);
        lastYaw = yaw;
        return turned;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            SitHost h = host();
            if (h != null) {
                float turned = follow(h);
                // (whoever sits turns with the part; your own view is turned every frame by SolidClient)
                Entity p = getFirstPassenger();
                if (p != null && Math.abs(turned) < 45f && !Sitting.isLocal(p)) {
                    p.setYRot(p.getYRot() + turned);
                    if (p instanceof LivingEntity le) le.setYBodyRot(le.yBodyRot + turned);
                }
            }
            return;
        }
        Sitting.seatTick(this);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        // (his own game works out where it is from his pose, every tick: the server's word only without him)
        if (host() == null) super.lerpTo(x, y, z, yRot, xRot, steps);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity e, EntityDimensions d, float f) {
        return new Vec3(0, SIT_OVER, 0);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity e) {
        return getPassengers().isEmpty();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
