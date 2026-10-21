package net.jj.hollowbell.entity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.jj.hollowbell.Adv;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.ModSounds;
import net.jj.hollowbell.item.BellArmorItem;
import net.jj.hollowbell.rig.BellAnim;
import net.jj.hollowbell.rig.BellModel;
import net.jj.hollowbell.rig.BellPieces;
import net.jj.hollowbell.rig.BellRig;
import net.jj.hollowbell.rig.BellState;
import net.jj.hollowbell.solid.GiantHull;
import net.jj.hollowbell.solid.Solid;
import net.jj.hollowbell.solid.SolidBody;
import net.jj.hollowbell.solid.SolidCache;
import net.jj.hollowbell.solid.SolidShape;
import net.jj.hollowbell.world.NoWait;
import net.jj.hollowbell.world.WorldOne;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Hollowbell, my KillzAI build: a huge green jellyfish of glass, copper and bone that drifts over the land on his
 * hanging strands and pulls things up into his hollow dome.
 *
 * The body is drawn from the build block for block (see the client's BellRenderer). The server keeps the same pose
 * the client draws, so a hit lands on exactly the block you swung at: see {@link #hitBy}. His own health is kept
 * here rather than in the game's (which can't go past 1024).
 */
public class HollowbellEntity extends Monster implements SolidBody {
    public static final int CALM = 0, HUNTER = 1, GUARDIAN = 2;
    public static final float MIN_SCALE = 0.03f, MAX_SCALE = 2f;

    static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Float> DATA_HP =
            SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Float> DATA_HP_MAX =
            SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_MOVE =
            SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MOVE_ARG =
            SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_MOVE_START =
            SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Vector3f> DATA_AIM =
            SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> DATA_LIFT =
            SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.FLOAT);
    /**
     * carrying somebody up onto his crown: how far from him they are kept, how high over the crown their middle ends
     */
    private static final EntityDataAccessor<Vector3f> DATA_CARRY =
            SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.VECTOR3);

    static final EntityDataAccessor<Long> DATA_PULSE_START =
            SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.LONG);
    static final EntityDataAccessor<Float> DATA_PULSE_POWER =
            SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.FLOAT);
    /** lower, tilt x, tilt z: what his popped pods do to him */
    private static final EntityDataAccessor<Vector3f> DATA_HANG =
            SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.VECTOR3);

    static final EntityDataAccessor<Float> DATA_SUNK =
            SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<CompoundTag> DATA_PARTS =
            SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Integer> DATA_FLAGS =
            SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.INT);
    /** how he is moving, blocks per tick: the client carries him on with it between updates */
    static final EntityDataAccessor<Vector3f> DATA_VEL =
            SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.VECTOR3);
    /** how asleep he is, 0 awake to 1 fast asleep (eased on the server, so the client draws the same) */
    static final EntityDataAccessor<Float> DATA_SLEEP =
            SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.FLOAT);

    static final int F_STAY = 1, F_RIDDEN = 2, F_TIRED = 4, F_AWAKE = 8, F_FOLLOW = 16;

    public final BellRig rig = BellRig.get();
    /** the pose the server works with (and the client, for aiming and hits) */
    public final BellState state = new BellState(rig);

    public final Matrix4f[] pose = rig.newPose();
    /** his body and his swinging arms and strands, a tick at a time (both sides) */
    private final BellAnim anim = new BellAnim(rig);

    private final BellAnim.In animIn = new BellAnim.In();

    @Nullable
    Vec3 animAt;

    {
        // one set of his parts, seen by the pose and the animation alike
        state.podPopped = anim.now().podPopped;
        state.podGrowth = anim.now().podGrowth;
        state.eggGone = anim.now().eggGone;
    }

    private long poseTick = Long.MIN_VALUE;
    final Mood mood = new Mood(this);
    final BellMoves moves = new BellMoves(this);
    final BellFlight flight = new BellFlight(this);
    final BellSaving saving = new BellSaving(this);
    final BellRiding riding = new BellRiding(this);
    final BellTargets targets = new BellTargets(this);
    final BellHealth health = new BellHealth(this);

    // ---- his parts
    final float[] podHp = new float[rig.pods.length];
    /** gametime a popped pod starts growing back */
    final long[] podRegrowAt = new long[rig.pods.length];
    /** he stays down at least until this gametime once enough pods are popped */
    long sunkUntil;

    final int[] eggBack = new int[rig.eggs.length];
    boolean partsDirty = true;
    private int partsSentAt;

    // ---- his own health
    float hp = -1f, baseMax = -1f;
    final Map<UUID, Long> fighters = new HashMap<>();

    // ---- going places
    @Nullable
    Vec3 home, goal;
    /** blocks per tick, in the world */
    Vec3 vel = Vec3.ZERO;

    long nextPulse;
    /** how high over the ground he likes to drift right now, and the height he is making for */
    double cruise = -1, wantY = Double.NaN;
    /** the being-him keys: up (1), down (-1) */
    int driveUp;
    /** a fresh one comes down out of the sky; ticks left of that. settled: he has arrived (saved) */
    int arriving;

    boolean settled;

    /** a fresh one starts where it's put, not up in the sky */
    public void skipArrival() {
        settled = true;
    }

    public boolean arriving() {
        return arriving > 0;
    }

    private void arrive() {
        flight.arrive();
    }

    int wanderIn;
    boolean stay;
    int angerTicks;

    @Nullable
    UUID hunted;
    /** "go after what I look at": a player's order. It overrides his own picks, his wandering and his circle, and holds
     *  until the target dies, is lost for {@link #HUNT_LOST_TICKS}, or he is given a new order */
    boolean huntOrdered;
    /** where the one he's after was last seen, and since when it hasn't been (-1: it's here) */
    @Nullable
    Vec3 huntSeen;

    long huntLostAt = -1;
    WeakReference<LivingEntity> huntRef = new WeakReference<>(null);
    /** ticks he keeps looking for a target that went out of sight */
    public static final int HUNT_LOST_TICKS = 600;
    /** when he first turned up, kept through every save: it decides who is oldest when the world is full */
    long bornAt = -1;
    /** the one the world keeps (see WorldOne); saved with him */
    boolean worldOne;
    /** true when he was read back from a save (or from being away), not freshly made: the cap leaves him be */
    boolean loadedFromSave;
    /** bound to a circle he will not leave on his own (the book's "keep to here"); boundR <= 0 = free */
    double boundX, boundZ;

    int boundR = -1;

    // ---- being him
    @Nullable
    LivingEntity rider;

    @Nullable
    Seat riderSeat;

    float driveF, driveS, driveYaw;
    int driveIdle;
    /** who asked him to come and put them on his crown */
    @Nullable
    UUID fetching;

    // ---- bars
    private @Nullable BellBar barHp, barPods;

    // ---- client smoothing: where the server last said he is, and how he's carried on between
    private @Nullable Vec3 cTarget;
    private Vec3 cVel = Vec3.ZERO;
    private CompoundTag cParts;

    /** set by the client: a slam's thump for the screen shake (x y z, how hard) */
    public static QuadHook clientThump = (x, y, z, k) -> {};

    public interface QuadHook {
        void thump(double x, double y, double z, float k);
    }

    public HollowbellEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.xpReward = 500;
        this.noCulling = true;
        addTag(Giants.TAG);
        addTag(Meeting.KIND + Meeting.ME);
        for (int i = 0; i < podHp.length; i++) podHp[i] = 1f;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 256.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.ATTACK_DAMAGE, 10.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_SCALE, 1f);
        b.define(DATA_VARIANT, HUNTER);
        b.define(DATA_HP, 6000f);
        b.define(DATA_HP_MAX, 6000f);
        b.define(DATA_MOVE, 0);
        b.define(DATA_MOVE_ARG, -1);
        b.define(DATA_MOVE_START, 0L);
        b.define(DATA_AIM, new Vector3f());
        b.define(DATA_LIFT, -1f);
        b.define(DATA_CARRY, new Vector3f());
        b.define(DATA_PULSE_START, -1000L);
        b.define(DATA_PULSE_POWER, 1f);
        b.define(DATA_HANG, new Vector3f());
        b.define(DATA_SUNK, 0f);
        b.define(DATA_PARTS, new CompoundTag());
        b.define(DATA_FLAGS, 0);
        b.define(DATA_VEL, new Vector3f());
        b.define(DATA_SLEEP, 0f);
    }

    // sleeping

    /** how long he must be left alone before he drifts down and sleeps (the tests shorten it) */
    public static int quietBeforeSleep = 2400;

    boolean asleep;
    private int quiet;
    long hurtAt = -100000;

    // the operator's switches

    /** held where he is: no drifting, no picking targets, no moves of his own (his body still breathes) */
    boolean frozen;
    /** how fast he goes, times normal (0.1 to 5) */
    float speedMul = 1f;

    public boolean frozen() {
        return frozen;
    }

    public void setFrozen(boolean on) {
        frozen = on;
        if (on) {
            vel = Vec3.ZERO;
            goal = null;
            setTarget(null);
            moves.stopNow();
        }
    }

    public float speedMul() {
        return speedMul;
    }

    public void setSpeedMul(float k) {
        speedMul = Mth.clamp(k, 0.1f, 5f);
    }

    /** asleep, or still waking up */
    public boolean asleep() {
        return asleep;
    }
    /** 0 awake to 1 fast asleep, as he is drawn */
    public float sleepiness() {
        return entityData.get(DATA_SLEEP);
    }
    /** the far-off stand-in on the client is told straight out (the real one eases it on the server) */
    public void setSleepiness(float k) {
        if (ghost) entityData.set(DATA_SLEEP, Mth.clamp(k, 0f, 1f));
    }

    /** he drifts down and sleeps (the book, the command, or being left alone a couple of minutes) */
    public void goToSleep() {
        if (asleep || isDeadOrDying()) return;
        asleep = true;
        quiet = 0;
        flight.follow(null);
        goal = null;
        wanderTo = null;
        fetching = null;
        setTarget(null);
        moves.stopNow();
    }

    /** he wakes and gets up (about two seconds of it): a hit, an order, a rider, or a hunter smelling somebody */
    public void wakeUp() {
        if (!asleep) return;
        asleep = false;
        quiet = 0;
        wanderIn = Math.max(wanderIn, 60);
    }

    private void sleepTick() {
        long now = level().getGameTime();
        if (asleep) {
            if (rider != null) wakeUp();
            else if (isHunter()) {
                double r = 32 * bellScale() + 8;
                for (Player p : level().players())
                    if (targets.fairGame(p)
                            && horiz(p.position()) < r
                            && Math.abs(p.getY() - getY()) < r + 60 * bellScale()) {
                        wakeUp();
                        break;
                    }
            }
        } else {
            boolean busy = getTarget() != null
                    || rider != null
                    || goal != null
                    || comeTo != null
                    || fetching != null
                    || (moveNow() != Moves.NONE && moveNow() != Moves.HARVEST)
                    || hunted != null
                    || angerTicks > 0
                    || now - hurtAt < 600
                    || moves.holdsStill();
            // a hunter only sleeps at night; calm and guardian whenever they're left alone
            boolean may = !isHunter() || level().isNight();
            quiet = busy || !may ? 0 : quiet + 1;
            if (quiet >= quietBeforeSleep) goToSleep();
        }
        float k = entityData.get(DATA_SLEEP);
        float want = asleep ? 1f : 0f;
        // five seconds to settle, two to get up
        float nk = asleep ? Math.min(want, k + 1f / 100f) : Math.max(want, k - 1f / 40f);
        if (nk != k) entityData.set(DATA_SLEEP, nk);
    }

    // size, mood, health

    public float bellScale() {
        return entityData.get(DATA_SCALE);
    }

    public void setBellScale(float s) {
        s = Mth.clamp(s, MIN_SCALE, MAX_SCALE);
        float ratio = hp > 0 && healthMax() > 0 ? hp / healthMax() : 1f;
        entityData.set(DATA_SCALE, s);
        baseMax = Math.max(40f, BellConfig.V.health * s);
        setMax(baseMax * playerBoost(), ratio);
        refreshDimensions();
    }

    float playerBoost() {
        if (!BellConfig.V.scaleToPlayers) return 1f;
        int n = Math.max(1, fighters.size());
        return Math.min(3f, 1f + 0.3f * (n - 1));
    }

    void setMax(float max, float ratio) {
        entityData.set(DATA_HP_MAX, max);
        hp = Mth.clamp(max * ratio, 0f, max);
        entityData.set(DATA_HP, hp);
    }

    public int variant() {
        return entityData.get(DATA_VARIANT);
    }

    public void setVariant(int v) {
        entityData.set(DATA_VARIANT, Mth.clamp(v, 0, 2));
        if (v == GUARDIAN && home == null) home = position();
    }

    public boolean isHunter() {
        return variant() == HUNTER;
    }

    public boolean isGuardian() {
        return variant() == GUARDIAN;
    }

    public Mood mood() {
        return mood;
    }

    public float healthNow() {
        return entityData.get(DATA_HP);
    }

    public float healthMax() {
        return entityData.get(DATA_HP_MAX);
    }
    /** below half: the loops turn red and he pulses more */
    public boolean angry() {
        return healthNow() < healthMax() * 0.5f;
    }

    public boolean staying() {
        return (entityData.get(DATA_FLAGS) & F_STAY) != 0;
    }

    public void setStay(boolean on) {
        stay = on;
        setFlag(F_STAY, on);
        if (on) {
            flight.follow(null);
            goal = null;
            vel = Vec3.ZERO;
        }
    }

    void setFlag(int f, boolean on) {
        int v = entityData.get(DATA_FLAGS);
        entityData.set(DATA_FLAGS, on ? v | f : v & ~f);
    }

    public boolean ridden() {
        return (entityData.get(DATA_FLAGS) & F_RIDDEN) != 0;
    }
    /** he's up and about: not asleep and not held still by an operator (the client's fight music goes by this) */
    public boolean awake() {
        return (entityData.get(DATA_FLAGS) & F_AWAKE) != 0;
    }

    /** worn out after a heavy move: slower, and no moves for a few seconds */
    public boolean tired() {
        return (entityData.get(DATA_FLAGS) & F_TIRED) != 0;
    }

    void setTired(boolean on) {
        setFlag(F_TIRED, on);
    }

    public Vec3 velocity() {
        return level().isClientSide ? cVel : vel;
    }

    public int moveNow() {
        return entityData.get(DATA_MOVE);
    }

    public int moveArg() {
        return entityData.get(DATA_MOVE_ARG);
    }

    public float moveT(float partial) {
        return (float) (level().getGameTime() - entityData.get(DATA_MOVE_START)) + partial;
    }
    /** resting on the ground: sunk down from popped pods, or down in a drop */
    public boolean resting() {
        return entityData.get(DATA_SUNK) > 0.5f || moveNow() == Moves.DROP;
    }

    public boolean sunk() {
        return entityData.get(DATA_SUNK) > 0.5f;
    }

    void setMove(int move, int arg, Vector3f aim) {
        entityData.set(DATA_MOVE, move);
        entityData.set(DATA_MOVE_ARG, arg);
        entityData.set(DATA_MOVE_START, level().getGameTime());
        entityData.set(DATA_AIM, new Vector3f(aim));
        entityData.set(DATA_LIFT, -1f);
    }

    void setAim(Vector3f aim) {
        entityData.set(DATA_AIM, new Vector3f(aim));
    }
    /** jumps the move of the moment to t ticks in (the dive, once he's landed) */
    void rewindMove(int t) {
        entityData.set(DATA_MOVE_START, level().getGameTime() - t);
    }

    void setLift(float l) {
        entityData.set(DATA_LIFT, l);
    }

    void setCarry(float d, float end) {
        entityData.set(DATA_CARRY, new Vector3f(d, end, 0f));
    }

    Vector3f aim() {
        return entityData.get(DATA_AIM);
    }
    /** where the move of the moment is aimed, in the world (the tests) */
    public Vec3 aimWorld() {
        return toWorld(aim());
    }

    public int podsLeft() {
        int n = 0;
        for (int i = 0; i < rig.pods.length; i++) if (!state.podPopped[i]) n++;
        return n;
    }

    public int eggsLeft() {
        int n = 0;
        for (int i = 0; i < rig.eggs.length; i++) if (!state.eggGone[i]) n++;
        return n;
    }

    public boolean isPodPopped(int i) {
        return state.podPopped[i];
    }

    public float podGrowth(int i) {
        return state.podGrowth[i];
    }
    /** how many popped pods bring him down */
    public int podsToSink() {
        return Math.max(1, (int) Math.ceil(rig.pods.length * Mth.clamp(BellConfig.V.podsToSink, 0.05f, 1f)));
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose p) {
        float s = bellScale();
        return EntityDimensions.fixed(Math.max(0.6f, 8f * s), Math.max(0.6f, 8f * s));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SCALE.equals(key)) refreshDimensions();
    }

    /** the box round all of him, for drawing and for finding what is near him */
    public AABB bodyBox() {
        float s = bellScale();
        double r = 112 * s + 2;
        // brought down onto the ground or flipped in the dive, he can reach well below where he floats
        double below = Math.max(4, (state.lower + 12) * s + 4);
        // (a strand carrying somebody over his dome goes a way above the crown)
        return new AABB(
                getX() - r, getY() - below, getZ() - r, getX() + r, getY() + (rig.crownY + 40) * s + 2, getZ() + r);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return bodyBox();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        double r = Math.min(BellConfig.V.renderDistance, 400 * Math.max(0.1f, bellScale()) + 160);
        r = Math.max(r, Math.min(4096, BellConfig.V.farSightBlocks)); // never lost before far sight reaches
        return d < r * r;
    }

    // the stand-in drawn far off

    /** a stand-in a player's game draws far off (see FarSight): never in any world, hangs calm, ground well below */
    private boolean ghost;

    public void makeGhost() {
        ghost = true;
    }

    public boolean isGhost() {
        return ghost;
    }

    /** the stand-in's own tick: only its body and strands move, gently */
    public void ghostTick() {
        tickCount++;
        animTick();
    }

    // model space and the world

    /** yaw is fixed for his life: he is round and has no front */
    public Matrix4f modelToWorld(float partial) {
        double x = Mth.lerp(partial, xo, getX()), y = Mth.lerp(partial, yo, getY()), z = Mth.lerp(partial, zo, getZ());
        return new Matrix4f()
                .translate((float) x, (float) y, (float) z)
                .rotateY(-getYRot() * Mth.DEG_TO_RAD)
                .scale(bellScale());
    }

    public Vec3 toWorld(Vector3f model) {
        float s = bellScale();
        float a = -getYRot() * Mth.DEG_TO_RAD, c = Mth.cos(a), sn = Mth.sin(a);
        double x = model.x * c + model.z * sn, z = -model.x * sn + model.z * c;
        return new Vec3(getX() + x * s, getY() + model.y * s, getZ() + z * s);
    }

    public Vector3f toModel(Vec3 w) {
        float s = bellScale();
        double dx = (w.x - getX()) / s, dy = (w.y - getY()) / s, dz = (w.z - getZ()) / s;
        float a = getYRot() * Mth.DEG_TO_RAD, c = Mth.cos(a), sn = Mth.sin(a);
        return new Vector3f((float) (dx * c + dz * sn), (float) dy, (float) (-dx * sn + dz * c));
    }

    public Vector3f dirToModel(Vec3 d) {
        float s = bellScale();
        float a = getYRot() * Mth.DEG_TO_RAD, c = Mth.cos(a), sn = Mth.sin(a);
        return new Vector3f((float) ((d.x * c + d.z * sn) / s), (float) (d.y / s), (float) ((-d.x * sn + d.z * c) / s));
    }

    /**
     * The name of a part of him that has a block (any of it more than a sliver) inside this box in the
     * world, or null. skipStrand is a strand not to count (the one holding whatever the box is round).
     */
    public @Nullable String partIn(AABB box, int skipStrand) {
        ensurePose();
        BellModel m = BellModel.get();
        float s = bellScale();
        // a block of him counts if its middle is inside the box grown by just under half a block of his
        AABB big = box.inflate(0.45 * s);
        Vector3f lo = new Vector3f(Float.MAX_VALUE), hi = new Vector3f(-Float.MAX_VALUE);
        for (int i = 0; i < 8; i++) {
            Vector3f c = toModel(new Vec3(
                    (i & 1) == 0 ? big.minX : big.maxX,
                    (i & 2) == 0 ? big.minY : big.maxY,
                    (i & 4) == 0 ? big.minZ : big.maxZ));
            lo.min(c);
            hi.max(c);
        }
        Vector3f mid = new Vector3f(lo).add(hi).mul(0.5f);
        float half = new Vector3f(hi).sub(lo).length() * 0.5f;
        Matrix4f inv = new Matrix4f();
        Vector3f v = new Vector3f(), cc = new Vector3f();
        for (int b = 0; b < rig.boneCount(); b++) {
            float[] bb = m.bounds[b];
            if (bb == null || !rig.shown(state, b)) continue;
            if (skipStrand >= 0 && rig.kind[b] == BellRig.Kind.STRAND && rig.part[b] == skipStrand) continue;
            // quick: the bone's box as a ball where it is now, against the box as a ball
            cc.set((bb[0] + bb[3]) * 0.5f, (bb[1] + bb[4]) * 0.5f, (bb[2] + bb[5]) * 0.5f);
            float r = 0.5f
                            * (float) Math.sqrt((bb[3] - bb[0]) * (bb[3] - bb[0])
                                    + (bb[4] - bb[1]) * (bb[4] - bb[1])
                                    + (bb[5] - bb[2]) * (bb[5] - bb[2]))
                            * 1.4f
                    + 1f;
            pose[b].transformPosition(cc);
            if (cc.distance(mid) > r + half) continue;
            pose[b].invert(inv);
            Vector3f rl = new Vector3f(Float.MAX_VALUE), rh = new Vector3f(-Float.MAX_VALUE);
            for (int i = 0; i < 8; i++) {
                inv.transformPosition(
                        v.set((i & 1) == 0 ? lo.x : hi.x, (i & 2) == 0 ? lo.y : hi.y, (i & 4) == 0 ? lo.z : hi.z));
                rl.min(v);
                rh.max(v);
            }
            int x0 = (int) Math.floor(Math.max(rl.x, bb[0])), x1 = (int) Math.floor(Math.min(rh.x, bb[3]));
            int y0 = (int) Math.floor(Math.max(rl.y, bb[1])), y1 = (int) Math.floor(Math.min(rh.y, bb[4]));
            int z0 = (int) Math.floor(Math.max(rl.z, bb[2])), z1 = (int) Math.floor(Math.min(rh.z, bb[5]));
            for (int x = x0; x <= x1; x++)
                for (int y = y0; y <= y1; y++)
                    for (int z = z0; z <= z1; z++) {
                        if (!m.has(b, x, y, z)) continue;
                        pose[b].transformPosition(v.set(x + 0.5f, y + 0.5f, z + 0.5f));
                        if (big.contains(toWorld(v))) return rig.boneNames[b];
                    }
        }
        return null;
    }

    /** a model-space point (see toModel) in his rest space: as if he weren't leaning, lowered or turned right now */
    public Vector3f restOf(Vector3f model) {
        ensurePose();
        return rig.body(state, new Matrix4f()).invert().transformPosition(model, new Vector3f());
    }

    /** a rest-space point of a bone, where it is in the world right now (server pose) */
    public Vec3 boneWorld(int bone, Vector3f rest) {
        ensurePose();
        return toWorld(rig.at(pose, bone, rest));
    }

    public Vec3 strandTipWorld(int s) {
        ensurePose();
        return toWorld(rig.strandTip(pose, s));
    }

    public Vec3 armTipWorld(int a) {
        ensurePose();
        return toWorld(rig.armTip(pose, a));
    }

    public Vec3 podWorld(int p) {
        return boneWorld(rig.pods[p].bone(), rig.pods[p].centre());
    }

    public Vec3 eggWorld(int e) {
        return boneWorld(rig.eggs[e].bone(), rig.eggs[e].centre());
    }

    public Vec3 spotWorld(int k) {
        return boneWorld(rig.spots[k].bone(), rig.spots[k].centre());
    }

    public Vec3 crownWorld() {
        return boneWorld(rig.crownBone, new Vector3f(0, rig.crownY + 1, 0));
    }

    // places on him things are held

    /** places inside the dome to hang things: the first four right under the glowing balls, then round the vase */
    public static final int SLOTS = 14;

    public Vec3 slotWorld(int i) {
        return riding.slotWorld(i);
    }

    public Vec3 seatSpot(int mode, int index, @Nullable Entity who) {
        return riding.seatSpot(mode, index, who);
    }

    public double carryDrop(@Nullable Entity who) {
        return riding.carryDrop(who);
    }

    /** the pose as of this tick, worked out once a tick when something needs it */
    public void ensurePose() {
        long now = level().getGameTime();
        if (poseTick == now) return;
        poseTick = now;
        fillState(1f);
        rig.computePose(state, pose, poseHang);
    }

    /** what each arm and strand hangs from, as drawn (with the pose) */
    private final Matrix4f[] poseHang = BellPieces.get().newHang();
    /** his arms and strands in slices as they are drawn up close (see BellPieces), worked out when a hit needs them */
    private final Matrix4f[] slices = BellPieces.get().newPose();

    private long slicesTick = Long.MIN_VALUE;

    /** the slices as of this tick, so a hit lands on him just where he's drawn */
    public Matrix4f[] slicesNow() {
        ensurePose();
        if (slicesTick != poseTick) {
            slicesTick = poseTick;
            BellPieces.get().pose(state, pose, poseHang, slices);
        }
        return slices;
    }

    /** the pose state at this moment (partial: how far into the tick, for drawing) */
    public void fillState(float partial) {
        if (level().isClientSide) readParts();
        anim.fill(state, partial);
        state.red = angry();
    }

    /** one tick of his body and his arms and strands swinging: after he has moved, on both sides */
    private void animTick() {
        float s = bellScale();
        BellAnim.In in = animIn;
        long gt = level().getGameTime();
        in.time = (float) (gt % 240000L);
        Vec3 v = level().isClientSide ? cVel : vel;
        Vector3f mv = dirToModel(v);
        in.vx = mv.x;
        in.vy = mv.y;
        in.vz = mv.z;
        Vec3 at = position();
        Vector3f sh = animAt == null ? new Vector3f() : dirToModel(at.subtract(animAt));
        if (animAt == null) anim.reset();
        animAt = at;
        in.shiftX = sh.x;
        in.shiftY = sh.y;
        in.shiftZ = sh.z;
        in.speedRef = (float) (flight.maxSpeed() / s);
        in.pulse = Moves.pulseCurve((float) (gt - entityData.get(DATA_PULSE_START)), entityData.get(DATA_PULSE_POWER));
        Vector3f hang = entityData.get(DATA_HANG);
        in.hangLower = hang.x;
        in.hangTiltX = hang.y;
        in.hangTiltZ = hang.z;
        in.sunk = entityData.get(DATA_SUNK) > 0.5f ? 1f : 0f;
        in.sleep = entityData.get(DATA_SLEEP);
        in.dying = isDeadOrDying() ? deathTime : -1f;
        in.tired = tired();
        in.red = angry();
        double g = groundAt(getX(), getZ());
        in.groundUnder = (float) ((g - getY()) / s);
        in.ground = this::groundModel;
        if (level().isClientSide) readParts();
        Vector3f aim = aim();
        int move = moveNow();
        Vector3f carry = entityData.get(DATA_CARRY);
        Moves.pose(
                rig,
                anim.now(),
                in,
                move,
                moveT(0f),
                moveArg(),
                aim.x,
                aim.y,
                aim.z,
                entityData.get(DATA_LIFT),
                carry.x,
                carry.y);
        anim.step(in);
        poseTick = Long.MIN_VALUE;
        solidVer++;
        solidCache.touch();
    }

    /** the ground under a point of him, in his own model heights (NaN if that ground isn't loaded) */
    private float groundModel(float mx, float mz) {
        if (ghost) return Float.NaN;
        Vec3 w = toWorld(new Vector3f(mx, 0, mz));
        BlockPos p = BlockPos.containing(w.x, getY(), w.z);
        if (!NoWait.loaded(level(), p)) return Float.NaN;
        // (under a roof the top of the land is the roof)
        int y = level().dimensionType().hasCeiling()
                ? NoWait.standY(level(), p.getX(), p.getZ(), p.getY())
                : level().getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ());
        return (float) ((y - getY()) / bellScale());
    }

    /** the joints of one of his chains (an arm or strand) as they are this tick, model space */
    public float[] chainNow(int chain) {
        return anim.now().chain[chain];
    }

    /** was this arm or strand knocked by the ground or his bell just now */
    public boolean chainKnocked(int c) {
        return anim.knocked(c);
    }

    /** the client's pose, for its own drawing of hits and aiming (the renderer makes its own) */
    public boolean clientPoseReady() {
        return level().isClientSide && tickCount > 1;
    }

    // what the client is told about his parts

    private void syncParts() {
        CompoundTag t = new CompoundTag();
        int mask = 0;
        for (int i = 0; i < rig.pods.length && i < 31; i++) if (state.podPopped[i]) mask |= 1 << i;
        t.putInt("P", mask);
        byte[] e = new byte[rig.eggs.length];
        for (int i = 0; i < e.length; i++) e[i] = (byte) (state.eggGone[i] ? 1 : 0);
        t.putByteArray("E", e);
        byte[] pg = new byte[rig.pods.length];
        for (int i = 0; i < pg.length; i++) pg[i] = (byte) Math.round(Mth.clamp(state.podGrowth[i], 0f, 1f) * 255f);
        t.putByteArray("G", pg);
        entityData.set(DATA_PARTS, t);
    }

    private void readParts() {
        CompoundTag t = entityData.get(DATA_PARTS);
        if (t == cParts || !t.contains("G")) return;
        cParts = t;
        BellState st = anim.now();
        int mask = t.getInt("P");
        for (int i = 0; i < rig.pods.length && i < 31; i++) st.podPopped[i] = (mask & (1 << i)) != 0;
        byte[] e = t.getByteArray("E");
        for (int i = 0; i < Math.min(e.length, rig.eggs.length); i++) st.eggGone[i] = e[i] != 0;
        byte[] pg = t.getByteArray("G");
        for (int i = 0; i < Math.min(pg.length, rig.pods.length); i++) st.podGrowth[i] = (pg[i] & 0xff) / 255f;
    }

    // ticking

    @Override
    public void tick() {
        if (hp < 0 && !level().isClientSide) setBellScale(bellScale());
        super.tick();
        if (!isRemoved() && !ghost) Solid.track(this);
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (isDeadOrDying()) return;
        serverTick();
    }

    /** the client's last tick of him (see tickIfSkipped) */
    private long clientTickedAt = -1;

    /**
     * The game only moves a creature on your screen while the chunk at its very middle is near you. He's so big you
     * can be watching him with his middle further off than that, and he'd freeze. Called after the client's own
     * ticking: if the game skipped him this tick, he's ticked here instead.
     */
    public void tickIfSkipped() {
        if (!level().isClientSide || isRemoved() || isPassenger() || clientTickedAt == level().getGameTime()) return;
        setOldPosAndRot();
        tickCount++;
        tick();
    }

    private void clientTick() {
        clientTickedAt = level().getGameTime();
        // carried on at the speed the server says, and eased toward where it last said he was, so he glides
        // between its updates instead of stepping
        Vector3f sv = entityData.get(DATA_VEL);
        cVel = cVel.add(new Vec3(sv.x - cVel.x, sv.y - cVel.y, sv.z - cVel.z).scale(0.35));
        Vec3 p = position().add(cVel);
        if (cTarget != null) {
            Vec3 err = cTarget.subtract(p);
            if (err.lengthSqr() > Mth.square(24 * bellScale() + 12)) p = cTarget;
            else p = p.add(err.scale(0.14));
        }
        setPos(p.x, p.y, p.z);
        animTick();
        if (tickCount % 4 == 0) BellFx.ambient(this);
    }

    /** the server's word on where he is: kept as a target rather than jumped to */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (cTarget == null && tickCount < 2) setPos(x, y, z);
        cTarget = new Vec3(x, y, z);
        setYRot(yRot);
        setXRot(xRot);
    }

    private void serverTick() {
        if (bornAt < 0) bornAt = level().getGameTime();
        if (!settled) flight.arrive();
        if (home == null) home = position();
        // the world's own one keeps the world posted on where he is, for the finder
        if (worldOne && tickCount % 100 == 0 && level() instanceof ServerLevel sl)
            WorldOne.get(sl.getServer()).seen(this);
        mood.tick();
        if (angerTicks > 0) angerTicks--;
        long now = level().getGameTime();
        // the game's own health never moves: his is kept apart
        if (getHealth() < getMaxHealth()) setHealth(getMaxHealth());
        health.passiveHeal();

        partsTick(now);
        riding.riderTick();
        if (!frozen) sleepTick();
        if (!frozen) targets.meetTick(now);
        if (!asleep && !frozen) targets.pickTarget();
        if (comeTo != null && !frozen) flight.comeTick();
        flight.followTick();
        if (!frozen) moves.tick();
        if (frozen) {
            vel = Vec3.ZERO;
            entityData.set(DATA_VEL, new Vector3f());
        } else flight.fly(now);
        flight.giantsApart();
        animTick();
        ensurePose();
        moves.afterPose();
        Solid.serverTick(this);
        health.stingTick();
        health.arrowsTick();
        barsTick();
        boolean up = !asleep && !frozen;
        if (up != awake()) setFlag(F_AWAKE, up);
        if (partsDirty && tickCount - partsSentAt >= 4) {
            partsDirty = false;
            partsSentAt = tickCount;
            syncParts();
        }
        if (tickCount % 20 == 0) fighters.entrySet().removeIf(e -> now - e.getValue() > 6000);
    }

    // pods and eggs over time

    private void partsTick(long now) {
        boolean anyChange = false;
        for (int i = 0; i < rig.pods.length; i++) {
            float g = state.podGrowth[i];
            if (g >= 1f || now < podRegrowAt[i]) continue;
            // a popped pod grows back over about twenty seconds, then it can be popped again
            float ng = Math.min(1f, g + 1f / 400f);
            if ((int) (ng * 40) != (int) (g * 40) || ng >= 1f) anyChange = true;
            state.podGrowth[i] = ng;
            if (ng >= 1f) {
                state.podPopped[i] = false;
                podHp[i] = 1f;
            }
        }
        if (anyChange) partsDirty = true;
        // egg clumps come back slowly on the strands
        for (int i = 0; i < rig.eggs.length; i++) {
            if (!state.eggGone[i] || eggBack[i] <= 0) continue;
            if (--eggBack[i] == 0) {
                state.eggGone[i] = false;
                partsDirty = true;
            }
        }
        if (tickCount % 5 == 0) hangFromPods();
    }

    /**
     * The pods keep him up. Each popped one lets him hang a little lower and lean toward that side; pop a third of
     * them and he loses his lift and sinks right down, and stays down until enough have grown back.
     */
    void hangFromPods() {
        int n = rig.pods.length, popped = 0;
        float lx = 0f, lz = 0f;
        for (int i = 0; i < n; i++) {
            float missing = 1f - state.podGrowth[i];
            if (!state.podPopped[i]) continue;
            popped++;
            var P = rig.pods[i];
            float r = Math.max(10f, (float) Math.hypot(P.centre().x, P.centre().z));
            lx += missing * P.centre().x / r;
            lz += missing * P.centre().z / r;
        }
        int need = podsToSink();
        float lower = Math.min(1f, popped / (float) need) * 18f;
        // leaning over toward the side with the popped pods: its edge goes down
        float tiltZ = Mth.clamp(-lx / need * 0.12f, -0.18f, 0.18f), tiltX = Mth.clamp(lz / need * 0.12f, -0.18f, 0.18f);
        entityData.set(DATA_HANG, new Vector3f(lower, tiltX, tiltZ));
        float sunk = entityData.get(DATA_SUNK);
        long now = level().getGameTime();
        if (sunk < 0.5f && popped >= need) {
            entityData.set(DATA_SUNK, 1f);
            sunkUntil = now + Math.max(5, BellConfig.V.sunkSeconds) * 20L;
            sound(position(), SoundEvents.ANVIL_LAND, 3f, 0.4f);
            for (ServerPlayer p : level() instanceof ServerLevel sl ? sl.players() : List.<ServerPlayer>of())
                if (p.distanceToSqr(this) < Mth.square(120 * bellScale() + 60))
                    p.displayClientMessage(Component.translatable("message.hollowbell.sunk"), true);
        } else if (sunk > 0.5f && popped < need && now >= sunkUntil) entityData.set(DATA_SUNK, 0f);
    }

    // where he goes

    public void setGoal(@Nullable Vec3 g) {
        if (g != null && !Double.isFinite(g.x + g.y + g.z)) return;
        goal = g == null ? null : keptIn(g);
        comeTo = null;
        holdThere = false;
        comeLostAt = 0;
        if (g != null) {
            flight.follow(null);
            setStay(false);
            wakeUp();
        }
    }

    /** "Follow me": he keeps near this player until told otherwise (null stops it) */
    public void follow(@Nullable Player p) {
        flight.follow(p);
    }

    /** following somebody (the client knows too, for the book) */
    public boolean following() {
        return (entityData.get(DATA_FLAGS) & F_FOLLOW) != 0;
    }

    public @Nullable UUID followingWho() {
        return flight.follow;
    }

    /** who he is coming to (their spot is looked up again every ten seconds while he comes), or null */
    @Nullable
    UUID comeTo;
    /** a movement order: when he gets there he holds still until he's given something else */
    boolean holdThere;

    /** a movement order from the book or a command: go there (or come to this player), then hold still there */
    public void orderTo(Vec3 g, @Nullable Player follow) {
        // a new order ends one to go after something
        if (huntOrdered) {
            if (getTarget() != null
                    && hunted != null
                    && hunted.equals(getTarget().getUUID())) setTarget(null);
            targets.dropHunt();
        }
        setGoal(g);
        comeTo = follow == null ? null : follow.getUUID();
        holdThere = true;
    }

    /** when (real time, ms) the one he's coming to went missing (logged off, or in another world); 0 = they're here */
    long comeLostAt;
    /**
     * how long he waits for somebody who has gone before he gives up on them (20 real minutes; the tests shorten it)
     */
    public static long comeGiveUpMs = 20 * 60 * 1000L;

    /** waiting where he last saw the one he was coming to, for them to come back */
    public boolean waitingForSomebody() {
        return comeTo != null && comeLostAt != 0;
    }

    public @Nullable UUID comingTo() {
        return comeTo;
    }

    public boolean holdsThere() {
        return holdThere;
    }

    /** /hollowbell height: how high over the ground he drifts (his strand ends that far up), until he picks again */
    public void setCruise(double blocks) {
        cruise = Math.max(0, blocks);
        wanderIn = Math.max(wanderIn, 1200);
        if (stay) wantY = groundAt(getX(), getZ()) + cruise;
    }

    public @Nullable Vec3 goal() {
        return goal;
    }

    public void callTo(Player p) {
        setGoal(p.position());
    }

    public @Nullable Vec3 home() {
        return home;
    }

    public void setHome(Vec3 h) {
        home = h;
    }

    /** how far he can see things to hunt */
    public double senseRange() {
        return 110 * bellScale() + 40;
    }

    public double guardRange() {
        return 140 * bellScale() + 40;
    }
    /** his bell's radius in the world */
    public double bellRadius() {
        return 88 * bellScale();
    }

    public double maxSpeed() {
        return flight.maxSpeed();
    }

    @Nullable
    Vec3 wanderTo;

    public double horiz(Vec3 p) {
        double dx = p.x - getX(), dz = p.z - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** the last ground he saw that was loaded (for land that isn't yet) */
    private double groundSeen = Double.NaN;

    /** the top of the ground (or water, or treetops) at a spot */
    public double groundAt(double x, double z) {
        if (ghost) return getY() - 60 * bellScale();
        BlockPos p = BlockPos.containing(x, getY(), z);
        // (only land loaded right now: hasChunk also said yes for land still being made, and asking its height made the
        // whole server wait until it was made, up to 30 seconds on a real server)
        int y = level().dimensionType().hasCeiling()
                ? NoWait.standY(level(), p.getX(), p.getZ(), p.getY())
                : NoWait.height(level(), Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ());
        // (land not loaded yet: taken as level with the last ground he did see, not as high as he is, which lifted him
        // up with a jolt whenever he looked ahead over land still being made)
        if (y == NoWait.NOT_YET) return Double.isNaN(groundSeen) ? getY() - 30 * bellScale() : groundSeen;
        groundSeen = y;
        return y;
    }

    /** a pulse of the bell: it squeezes in, and a strong one is the pulse wave */
    /** when his last swim beat began (game time) */
    public long pulseStartedAt() {
        return entityData.get(DATA_PULSE_START);
    }

    public void pulse(float power) {
        entityData.set(DATA_PULSE_START, level().getGameTime());
        entityData.set(DATA_PULSE_POWER, power);
        Vec3 at = toWorld(new Vector3f(0, rig.rimY + 20, 0));
        sound(at, ModSounds.PULSE, 1.2f + power, 1.05f - 0.1f * power);
        if (power >= 1f) sound(at, ModSounds.PULSE_WATER, 0.8f + 0.6f * power, 1f);
        if (power >= 1.2f)
            sound(
                    boneWorld(rig.sectors[random.nextInt(rig.sectors.length)].bone(), new Vector3f(0, rig.rimY, 0)),
                    ModSounds.RIPPLE,
                    1.5f,
                    1f);
    }

    // who he goes after

    public @Nullable Player bookHolder() {
        return targets.bookHolder();
    }

    public boolean spares(Entity e) {
        return targets.spares(e);
    }

    static final boolean DEBUG =
            Boolean.getBoolean("hollowbell.debug") || System.getProperty("hollowbell.autotest") != null;

    /** entity types he never picks up (the other giant bosses and their parts); mods can add to it */
    public static final TagKey<EntityType<?>> NOT_CARRIED = TagKey.create(
            Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "not_carried"));
    /** small creatures from the other giant mods he may still pick up (Furrowmaw's furrowlings) */
    public static final TagKey<EntityType<?>> CARRIED_ANYWAY = TagKey.create(
            Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "carried_anyway"));

    /**
     * Whether his strands and arms can pick this up. Never the giants: Pitchgut, Cerberus, Furrowmaw, any boss, any
     * part of one, anything from the other giant mods (so future ones are covered too), or anything simply huge.
     */
    public static boolean canCarry(@Nullable Entity e) {
        // (nothing with somebody on it: a ridden horse would take its rider up with it)
        if (e == null || e.isVehicle() || Giants.carriesGiant(e)) return false;
        if (Giants.onGiantSeat(e, HollowbellMod.MOD_ID)) return false;
        EntityType<?> t = e.getType();
        if (t.is(NOT_CARRIED) || t.is(ConventionalEntityTypeTags.BOSSES)) return false;
        String cls = e.getClass().getName();
        if (cls.startsWith("net.jj.") && !cls.startsWith("net.jj.hollowbell.") && !t.is(CARRIED_ANYWAY)) return false;
        return e.getBbWidth() <= 6.0f && e.getBbHeight() <= 8.0f;
    }

    public boolean fairGame(@Nullable LivingEntity e) {
        return targets.fairGame(e);
    }

    public void sendAfter(LivingEntity e) {
        targets.sendAfter(e);
    }

    public @Nullable String huntRefusal(LivingEntity e) {
        return targets.huntRefusal(e);
    }

    public @Nullable UUID huntedByOrder() {
        return targets.huntedByOrder();
    }

    public void clearHitList() {
        targets.clearHitList();
    }

    public void endOrderedHunt() {
        targets.endOrderedHunt();
    }

    public void forgiveAll() {
        targets.forgiveAll();
    }

    // being hit

    /** set just before a hit is passed to the game so hurt() knows which block of him it landed on */
    int pendingBone = -1;

    boolean pendingInside;

    public static boolean isImmuneTo(DamageSource src) {
        return src.is(DamageTypes.IN_WALL)
                || src.is(DamageTypes.FALL)
                || src.is(DamageTypes.DROWN)
                || src.is(DamageTypes.CRAMMING)
                || src.is(DamageTypes.FLY_INTO_WALL)
                || src.is(DamageTypes.IN_FIRE)
                || src.is(DamageTypes.LAVA)
                || src.is(DamageTypes.ON_FIRE)
                || src.is(DamageTypes.HOT_FLOOR)
                || src.is(DamageTypes.SWEET_BERRY_BUSH)
                || src.is(DamageTypes.CACTUS);
    }

    public void hitBy(ServerPlayer p, int claimedBone) {
        health.hitBy(p, claimedBone);
    }

    static final int SWING_GAP = 4;
    final Map<UUID, Long> lastSwing = new HashMap<>();

    /** the first block of him along a line in the world, up to maxDist blocks */
    public @Nullable BellRig.Hit raycast(Vec3 from, Vec3 dir, double maxDist) {
        ensurePose();
        Vector3f o = toModel(from);
        Vec3 unit = dir.normalize();
        Vector3f d = dirToModel(unit);
        return rig.raycast(state, pose, slicesNow(), o, d, (float) maxDist);
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide || isDeadOrDying()) return false;
        if (src.is(DamageTypes.GENERIC_KILL)) {
            // /kill, /hollowbell kill, /giants kill: really dead (the game's own health too, or his death never runs)
            hp = 0;
            entityData.set(DATA_HP, 0f);
            setHealth(0f);
            die(src);
            return true;
        }
        if (isInvulnerableTo(src) || isImmuneTo(src)) return false;
        Entity att = src.getEntity();
        if (att == this
                || att instanceof Belling
                || (att instanceof LivingEntity le && moves.caught(le) && !(le instanceof Player))) return false;
        int bone = pendingBone;
        return health.applyDamage(src, amount, bone, pendingInside);
    }

    public float worth(int bone, boolean inside) {
        return health.worth(bone, inside);
    }

    public boolean applyDamage(DamageSource src, float amount, int bone, boolean inside) {
        return health.applyDamage(src, amount, bone, inside);
    }

    public void popPods(int n) {
        health.popPods(n);
    }

    public void mendPods() {
        health.mendPods();
    }

    void eggGone(int i) {
        health.eggGone(i);
    }

    public void heal() {
        health.heal();
    }

    public void setHealthTo(float v) {
        health.setHealthTo(v);
    }

    public void hurtBy(float amount) {
        health.hurtBy(amount);
    }

    // stings and arrows

    // bars

    public void clearBars() {
        if (barHp != null) barHp.removeAllPlayers();
        if (barPods != null) barPods.removeAllPlayers();
    }

    private void barsTick() {
        if (tickCount % 10 != 0) return;
        if (!BellConfig.V.bossBar) {
            clearBars();
            return;
        }
        if (barHp == null) {
            barHp = new BellBar(
                    BellBar.idFor(getUUID(), "hp"),
                    Component.translatable("bar.hollowbell.health"),
                    BossEvent.BossBarColor.GREEN,
                    BossEvent.BossBarOverlay.NOTCHED_10);
            barPods = new BellBar(
                    BellBar.idFor(getUUID(), "pods"),
                    Component.empty(),
                    BossEvent.BossBarColor.YELLOW,
                    BossEvent.BossBarOverlay.PROGRESS);
        }
        barHp.setName(getDisplayName()); // a name tag shows on his bar
        barHp.setProgress(Mth.clamp(healthNow() / Math.max(1f, healthMax()), 0f, 1f));
        barHp.setColor(angry() ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.GREEN);
        barPods.setProgress(podsLeft() / (float) Math.max(1, rig.pods.length));
        barPods.setName(Component.translatable(
                sunk() ? "bar.hollowbell.pods_sunk" : "bar.hollowbell.pods", podsLeft(), rig.pods.length));
        barPods.setColor(sunk() ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.YELLOW);
        double r = barRange(bellScale());
        List<ServerPlayer> want = new ArrayList<>();
        if (level() instanceof ServerLevel sl)
            for (ServerPlayer p : sl.players()) {
                if (p.distanceToSqr(getX(), p.getY(), getZ()) < r * r || moves.caught(p) || p == rider) want.add(p);
            }
        for (BellBar b : new BellBar[] {barHp, barPods}) {
            for (ServerPlayer p : new ArrayList<>(b.getPlayers())) if (!want.contains(p)) b.removePlayer(p);
            for (ServerPlayer p : want) b.addPlayer(p);
        }
    }

    @Override
    public void remove(RemovalReason why) {
        if (!level().isClientSide
                && !steppedOut
                && why != RemovalReason.UNLOADED_TO_CHUNK
                && why != RemovalReason.UNLOADED_WITH_PLAYER)
            HollowbellMod.LOG.info("Removed at {} ({})", blockPosition().toShortString(), why);
        clearBars();
        if (!level().isClientSide && (why == RemovalReason.KILLED || why == RemovalReason.DISCARDED))
            riding.lowerWhoeverStandsOnHim();
        Solid.untrack(this);
        if (!level().isClientSide) {
            riding.dropRider();
            moves.letGoOfEverything(why == RemovalReason.KILLED || why == RemovalReason.DISCARDED);
        }
        super.remove(why);
    }

    /** taken to another world (a /tp): whoever he held is let go here first, as when he's removed */
    public void leftThisWorld() {
        riding.dropRider();
        riding.stopFetch();
        moves.letGoOfEverything(true);
        clearBars();
    }

    /** how far off his boss bars still show */
    public static double barRange(float scale) {
        int set = BellConfig.V.bossBarRange;
        return set > 0 ? set : 220 * scale + 200;
    }

    // out of the world and back

    /** how far he drifts in a tick, for the sum that keeps him going while nobody is near */
    public double travelSpeed() {
        if (sunk()) return 0.01;
        float s = bellScale();
        return (0.10 + 0.16 * Math.sqrt(s)) * (angry() ? 1.15 : 1.0) * 0.8 * speedMul;
    }

    /** ticks with nobody anywhere near him */
    int aloneOut;
    /** just put back: don't step straight out again */
    int justBack;
    /** the world already has him written down: remove() must not do anything more with him */
    boolean steppedOut;

    /** back from away, still heading where he was going */
    public void backFromAway(@Nullable Vec3 to, boolean stayPut) {
        justBack = 200;
        settled = true;
        setStay(stayPut);
        goal = to == null ? null : keptIn(to);
    }

    public void stepAsideIfAlone(int every) {
        flight.stepAsideIfAlone(every);
    }

    public boolean stepAside() {
        return flight.stepAside();
    }

    public boolean steppedOut() {
        return steppedOut;
    }

    // riding on his crown: being him

    public @Nullable LivingEntity rider() {
        return rider;
    }

    public boolean carrying() {
        return rider != null;
    }
    /** somebody rides him (every giant has this, found by name: see Giants.ridden) */
    public boolean jjRidden() {
        return rider != null;
    }

    public boolean usesSeat(Seat s) {
        return s == riderSeat || moves.usesSeat(s);
    }

    public @Nullable LivingEntity fetchingNow() {
        return riding.fetchingNow();
    }

    public boolean comeAndGetMe(LivingEntity who) {
        return riding.comeAndGetMe(who);
    }

    public void stopFetch() {
        riding.stopFetch();
    }

    public boolean comingForSomebody() {
        return riding.comingForSomebody();
    }

    void seatOnCrown(LivingEntity who, @Nullable Seat keep) {
        riding.seatOnCrown(who, keep);
    }

    public boolean possess(LivingEntity who) {
        return riding.possess(who);
    }

    public static void putDownOnLeaving(ServerPlayer p) {
        BellRiding.putDownOnLeaving(p);
    }

    public void setMeDown(LivingEntity who) {
        riding.setMeDown(who);
    }

    void holdHeight() {
        riding.holdHeight();
    }

    @Nullable
    Seat handRiderToStrand() {
        return riding.handRiderToStrand();
    }

    public void dropRider() {
        riding.dropRider();
    }

    public void drive(LivingEntity who, float forward, float strafe, float yaw, int up) {
        riding.drive(who, forward, strafe, yaw, up);
    }

    /** a move pressed while riding: the book's clock applies */
    public boolean forceMove(int which) {
        wakeUp();
        return !moves.settingDown() && moves.force(which, getTarget());
    }

    public boolean forceMoveAt(int which, Vec3 spot) {
        wakeUp();
        return !moves.settingDown() && moves.forceAt(which, spot);
    }

    public boolean forceMove(int which, @Nullable LivingEntity at) {
        wakeUp();
        return !moves.settingDown() && moves.force(which, at != null ? at : getTarget());
    }

    // dying

    @Override
    public void die(DamageSource src) {
        // (whoever stands on him floats down: he won't hold them up once he's dying)
        if (!level().isClientSide && !dead) riding.lowerWhoeverStandsOnHim();
        if (!level().isClientSide && !dead && level() instanceof ServerLevel sl) {
            // the world's own one dying sets the next one going; another one only if the world has none and
            // isn't already counting down to one
            var w = WorldOne.get(sl.getServer());
            if (worldOne && (w.isTheOne(getUUID()) || w.oneId() == null)) {
                w.died(sl, this);
                worldOne = false; // saved mid-fold he is nobody's any more, so a reload can't bring him back
            } else if (!worldOne && BellConfig.V.oneInTheWorld && !w.aliveNow() && w.dueIn(sl) < 0) {
                w.died(sl, this); // no world one and nothing counting down: the count starts from here
            }
        }
        if (!level().isClientSide) {
            if (deathBy == null && (src.getEntity() instanceof Player || lastHurtByPlayer != null))
                deathBy = src.getEntity() instanceof Player k ? k.getUUID() : lastHurtByPlayer.getUUID();
            HollowbellMod.LOG.info("Died at {} ({})", blockPosition().toShortString(), src.getMsgId());
            // killed by somebody wearing his own glass
            Entity killer = src.getEntity() instanceof ServerPlayer ? src.getEntity() : getKillCredit();
            if (killer instanceof ServerPlayer sp && BellArmorItem.fullSet(sp)) Adv.grant(sp, "own_glass");
            // his chunk keeps going until his death is over, even if everybody walks off: never left half dead
            if (level() instanceof ServerLevel sl)
                sl.getChunkSource().addRegionTicket(DYING_TICKET, new ChunkPos(blockPosition()), 2, getId());
            riding.dropRider();
            riding.stopFetch();
            moves.letGoOfEverything(true);
            setMove(Moves.NONE, -1, new Vector3f());
            sound(position().add(0, rig.rimY * bellScale(), 0), ModSounds.DEATH, 4f, 1f);
        }
        super.die(src);
    }

    /**
     * The player who brought him down. The game forgets who hit him 100 ticks after the blow, and his death takes
     * longer, so it's told again every tick: the XP and the loot stay theirs.
     */
    @Nullable
    UUID deathBy;

    @Nullable
    DamageSource deathLoot;

    boolean lootDropped;
    public static final int DEATH_LENGTH = 320;
    private static final TicketType<Integer> DYING_TICKET =
            TicketType.create("hollowbell_death", Integer::compare, DEATH_LENGTH + 80);

    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource src) {
        deathLoot = src;
    }

    /** the game's own death loot, dropped now */
    void dropLootNow(ServerLevel level, DamageSource src) {
        super.dropAllDeathLoot(level, src);
    }

    @Override
    protected void tickDeath() {
        health.tickDeath();
    }

    public int deathLength() {
        return DEATH_LENGTH;
    }

    /** while his loot is being made: what he drops is kept here instead of thrown on the ground */
    @Nullable
    List<ItemStack> caught;

    @Override
    public @Nullable ItemEntity spawnAtLocation(ItemStack stack, float up) {
        if (caught != null) {
            if (!stack.isEmpty()) caught.add(stack.copy());
            return null;
        }
        return super.spawnAtLocation(stack, up);
    }

    @Override
    protected boolean shouldDropLoot() {
        return true;
    }

    @Override
    public void onClientRemoval() {
        super.onClientRemoval();
        Solid.untrack(this);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {}

    @Override
    public void push(Entity e) {}

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void checkInsideBlocks() {}

    @Override
    public boolean updateInWaterStateAndDoFluidPushing() {
        return false;
    }

    @Override
    public boolean isInWater() {
        return false;
    }

    @Override
    public void travel(Vec3 v) {}

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    /**
     * He's so big that turned over in the dive (or folded right down in a deep valley) the point he's measured from
     * can dip well under the ground, even under the bottom of the world. Only if he's really lost down there does
     * the void hurt him.
     */
    @Override
    protected void onBelowWorld() {
        if (getY() < level().getMinBuildHeight() - 64 - 420 * bellScale()) super.onBelowWorld();
    }

    @Override
    public boolean canBeAffected(MobEffectInstance e) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean skipAttackInteraction(Entity e) {
        return false;
    }

    @Override
    protected void registerGoals() {}

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public void knockback(double d, double x, double z) {}

    @Override
    protected SoundEvent getHurtSound(DamageSource s) {
        return lastHitBig ? ModSounds.HURT_HEAVY : ModSounds.HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 2.5f * BellConfig.V.soundVolume * Mth.clamp((float) Math.sqrt(bellScale()), 0.3f, 1.5f);
    }

    @Override
    public float getVoicePitch() {
        return Mth.clamp(1.1f - 0.3f * (float) Math.sqrt(bellScale()), 0.5f, 1.4f);
    }
    /** the last hit took a big piece of him: he cries out rather than rings */
    boolean lastHitBig;

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(
                isHunter() ? ModItems.HUNTING_EGG : isGuardian() ? ModItems.GUARDIAN_EGG : ModItems.CALM_EGG);
    }

    // little helpers

    /** a sound of his: a big one is louder (heard further off) and deeper, a small one quieter and higher */
    public void sound(Vec3 at, SoundEvent ev, float vol, float pitch) {
        float v = vol * BellConfig.V.soundVolume;
        if (v <= 0f) return;
        float s = bellScale();
        float size = Mth.clamp((float) Math.sqrt(s), 0.25f, 1.6f);
        float p = Mth.clamp(
                pitch * Mth.clamp(1.15f - 0.35f * (float) Math.log(Math.max(0.03f, s) * 2.2f) / 1.6f, 0.75f, 1.9f),
                0.5f,
                2f);
        level().playSound(null, at.x, at.y, at.z, ev, SoundSource.HOSTILE, v * (0.45f + 0.55f * size), p);
    }

    public void particles(ParticleOptions p, Vec3 at, int n, double spread, double speed) {
        if (level() instanceof ServerLevel sl)
            sl.sendParticles(p, at.x, at.y, at.z, n, spread, spread * 0.5, spread, speed);
    }

    /** how hard he hits: small ones hit less */
    public float dmg(float base, Entity to) {
        // the numbers in the moves are for full size; a small one hits a good deal less, a huge one more
        float size = Mth.clamp(0.2f + 0.8f * (float) Math.pow(bellScale(), 0.6), 0.25f, 1.5f);
        float f = base * BellConfig.V.damageMultiplier * size;
        if (!(to instanceof Player)) f *= BellConfig.V.mobDamage;
        return f;
    }

    public long bornAt() {
        return bornAt;
    }

    /** his birth tick, stamped now if he never got one (a fresh one joining before his first tick) */
    public long bornAtOr(Level l) {
        if (bornAt < 0) bornAt = l.getGameTime();
        return bornAt;
    }

    // the one the world keeps
    public void markWorldOne() {
        worldOne = true;
    }

    public void clearWorldOne() {
        worldOne = false;
    }

    public boolean isWorldOne() {
        return worldOne;
    }
    /** freshly made (summon, egg, the world's own), not read back from a save: only these count at the cap */
    public boolean freshSpawn() {
        return !loadedFromSave;
    }

    /** somebody summoned another and he is the one who has to go: gone for good, no loot, no sum */
    public void takenPlaceOf() {
        clearBars();
        if (level() instanceof ServerLevel sl) {
            Vec3 c = position();
            float s = bellScale();
            sl.sendParticles(
                    ParticleTypes.SQUID_INK, c.x, c.y + 60 * s + 2, c.z, 200, 50 * s + 4, 40 * s + 2, 50 * s + 4, 0.3);
            sound(c, ModSounds.DEATH, 4f, 0.6f);
            for (ServerPlayer p : sl.players())
                if (p.distanceToSqr(this) < 400 * 400)
                    p.displayClientMessage(Component.translatable("message.hollowbell.made_way"), false);
            HollowbellMod.LOG.info(
                    "A Hollowbell at {}, {} made way for a newer one", Mth.floor(getX()), Mth.floor(getZ()));
        }
        steppedOut = true; // remove() has nothing more to note about him
        discard();
    }

    // bound to a circle
    public boolean bound() {
        return boundR > 0;
    }

    public int boundRadius() {
        return boundR;
    }

    public Vec3 boundCentre() {
        return new Vec3(boundX, 0, boundZ);
    }

    public void bindTo(double x, double z, int r) {
        boundX = x;
        boundZ = z;
        boundR = Mth.clamp(r, 32, 100000);
    }

    public void unbind() {
        boundR = -1;
    }

    /** a spot pulled inside his circle, less his own reach, so all of him stays in; as given when he is free */
    public Vec3 keptIn(Vec3 want) {
        if (!bound() || want == null) return want;
        double keep = Math.max(8, boundR - bellRadius() - 12 * bellScale());
        double dx = want.x - boundX, dz = want.z - boundZ;
        double len = Math.hypot(dx, dz);
        if (len <= keep) return want;
        return new Vec3(boundX + dx / len * keep, want.y, boundZ + dz / len * keep);
    }

    // the crown holding him off
    /** is that spot inside the circle a woken crown keeps him out of? */
    public boolean warded(double wx, double wz) {
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) return false;
        return WorldOne.get(sl.getServer()).warded(level(), wx, wz);
    }

    public void pushedBackByWard() {
        flight.pushedBackByWard();
    }

    // meeting the other giants

    /** the giant he is having a meeting fight with (see Meeting), or null */
    @Nullable
    UUID meetFoe;
    /** the giant he is keeping out of the way of, and where it was last */
    @Nullable
    UUID meetAvoid;

    @Nullable
    Vec3 avoidAt;
    /** backed down: until when (game time); and running from where, until when */
    long yieldUntil, fleeUntil, yieldFor, fleeFor;

    @Nullable
    Vec3 fleeFrom;
    /** the giants he has met lately (by their id): not again until this game time */
    final Map<UUID, Long> metLately = new HashMap<>();
    /** the tests: how often he looks round for other giants (ticks) */
    public static int meetEvery = 40;

    public boolean yielding() {
        return getTags().contains(Meeting.YIELD);
    }

    public @Nullable UUID meetFoe() {
        return meetFoe;
    }

    public @Nullable UUID avoiding() {
        return meetAvoid;
    }

    public long meetCooldownLeft(UUID other) {
        return targets.meetCooldownLeft(other);
    }

    public boolean fightingGiant(@Nullable Entity e) {
        return targets.fightingGiant(e);
    }

    public boolean startMeeting(LivingEntity other) {
        return targets.startMeeting(other);
    }

    public void backDown(Entity from) {
        targets.backDown(from);
    }

    // saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        saving.writeSave(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        saving.readSave(tag);
    }

    boolean pendingWaits;

    @Override
    public void baseTick() {
        super.baseTick();
        if (pendingWaits && !level().isClientSide) {
            pendingWaits = false;
            long now = level().getGameTime();
            for (int i = 0; i < podRegrowAt.length; i++) podRegrowAt[i] += now;
            sunkUntil += now;
            yieldUntil = yieldFor > 0 ? now + yieldFor : 0;
            fleeUntil = fleeFor > 0 ? now + fleeFor : 0;
            if (yieldUntil == 0) removeTag(Meeting.YIELD);
            if (heavyIn >= 0) moves.resumeWaits(heavyIn, fightFor);
            for (var m : metLeft.entrySet()) metLately.put(m.getKey(), now + m.getValue());
            metLeft.clear();
        }
    }

    /** loaded, put back on his first tick: his heavy-move wait, how far into a fight, the giants he met lately */
    long heavyIn = -1, fightFor = -1;

    final Map<UUID, Long> metLeft = new HashMap<>();

    /** for tests and the commands */
    public void setMoveCooldown(int t) {
        moves.setCooldown(t);
    }

    public BellMoves moves() {
        return moves;
    }

    public static void logOnce(String s) {
        HollowbellMod.LOG.info(s);
    }

    /** ticks the slow heal waits after any hurt */
    public static final int HEAL_PAUSE = 300;

    // solid (the solid kit's adapter)

    /** solid kit frames, this tick */
    final SolidCache solidCache = new SolidCache();
    /** bumped each step of his animation: the pose the kit reads is worked out again */
    long solidVer, solidPoseVer = Long.MIN_VALUE;

    private BellState solidSt, solidDrawSt;
    private Matrix4f[] solidBones, solidHangs, solidSlices, solidDrawBones, solidDrawHangs;

    @Override
    public Entity solidSelf() {
        return this;
    }

    @Override
    public SolidShape solidShape() {
        return BellSolid.shape();
    }

    @Override
    public SolidCache solidCache() {
        return solidCache;
    }
    /** the solid kit off, to measure what it costs */
    public static boolean solidOff;

    @Override
    public boolean solidReady() {
        return !solidOff
                && !isRemoved()
                && !ghost
                && (!isDeadOrDying() || lowering)
                && solidVer > 0
                && bellScale() >= 0.02f
                && BellSolid.ready()
                && (!level().isClientSide || tickCount > 1);
    }

    @Override
    public AABB solidBox() {
        return bodyBox();
    }

    /** the pose as of this tick, in slices (his own copy: the renderer fills his state in between ticks) */
    @Override
    public Matrix4f[] solidPose() {
        // (the server's own pose of the tick, in slices, already worked out for hits; the client draws from its state
        // between ticks, so it keeps its own)
        if (!level().isClientSide) return slicesNow();
        if (solidSlices == null) {
            BellPieces pc = BellPieces.get();
            solidSt = new BellState(rig);
            solidBones = rig.newPose();
            solidHangs = pc.newHang();
            solidSlices = pc.newPose();
        }
        if (solidPoseVer != solidVer) {
            solidPoseVer = solidVer;
            if (level().isClientSide) readParts();
            anim.fill(solidSt, 1f);
            rig.computePose(solidSt, solidBones, solidHangs);
            BellPieces.get().pose(solidSt, solidBones, solidHangs, solidSlices);
        }
        return solidSlices;
    }

    /** the pose as drawn between ticks (the renderer's way: BellRenderer) */
    @Override
    public void solidPoseAt(float partial, Matrix4f[] out) {
        if (solidDrawSt == null) {
            solidDrawSt = new BellState(rig);
            solidDrawBones = rig.newPose();
            solidDrawHangs = BellPieces.get().newHang();
        }
        anim.fill(solidDrawSt, partial);
        rig.computePose(solidDrawSt, solidDrawBones, solidDrawHangs);
        BellPieces.get().pose(solidDrawSt, solidDrawBones, solidDrawHangs, out);
    }

    /** turned and sized just as he's drawn (modelToWorld; his yaw is fixed for his life) */
    @Override
    public Matrix4f solidRoot(float partial, Matrix4f out) {
        return out.identity().rotateY(-getYRot() * Mth.DEG_TO_RAD).scale(bellScale());
    }

    /** a popped pod (or one still growing back) and an egg clump that's gone aren't there to bump into */
    @Override
    public boolean solidBoneOn(int slice) {
        int b = BellPieces.get().bone[slice];
        BellState st = anim.now();
        return switch (rig.kind[b]) {
            case EGG -> !st.eggGone[rig.part[b]];
            case POD -> !st.podPopped[rig.part[b]] && st.podGrowth[rig.part[b]] >= 0.98f;
            default -> true;
        };
    }

    @Override
    public float solidScale() {
        return bellScale();
    }

    /** his seats, shots, hooks and Bellings, whoever he holds or is closing on, and the other giants (see jjHull) */
    @Override
    public boolean solidIgnores(Entity e) {
        return e instanceof Seat
                || e instanceof Shot
                || e instanceof StingerHook
                || e instanceof Belling
                || e == rider
                || Giants.isGiant(e)
                || (!level().isClientSide && (moves.caught(e) || moves.closingOn(e) || targets.huntedInTheAir(e)));
    }

    /**
     * A spot on the top of one of his slices: over where the middle of its blocks is
     * now, the highest top of it there with room to stand over it; or null.
     */
    public @Nullable Vec3 topOfSlice(int slice) {
        SolidShape sh = solidShape();
        int f = sh.frameOfBone[slice];
        if (f < 0) return null;
        // the cells of it with nothing of it over them, highest first, a few spread round it
        List<int[]> tops = new ArrayList<>();
        sh.cells(f, (x, y, z) -> {
            if (!sh.has(f, x, y + 1, z) && !sh.has(f, x, y + 2, z)) tops.add(new int[] {x, y, z});
        });
        if (tops.isEmpty()) return null;
        tops.sort((p, q) -> q[1] - p[1]);
        SolidCache c = Solid.frames(this);
        float s = bellScale();
        int step = Math.max(1, tops.size() / 40);
        for (int i = 0; i < tops.size(); i += step) {
            int[] t = tops.get(i);
            Vec3 w = Solid.toWorld(c, f, new Vector3f(t[0] + 0.5f, t[1] + 0.5f, t[2] + 0.5f));
            Solid.Runs r = Solid.column(this, w.x, w.z, w.y - 4 * s - 2, w.y + 120 * s + 4, false, new Solid.Runs());
            double best = Double.NaN;
            for (int j = 0; j < r.n; j++)
                if (r.frame[j] == f && (Double.isNaN(best) || r.top[j] > best)) best = r.top[j];
            if (Double.isNaN(best)) continue;
            boolean room = true;
            for (int j = 0; j < r.n; j++)
                if (r.bot[j] > best - 0.01 && r.bot[j] < best + 2.2 && r.frame[j] != f) room = false;
            for (int j = 0; j < r.n; j++)
                if (r.frame[j] != f && r.bot[j] <= best && r.top[j] > best + 0.05) room = false;
            if (room) return new Vec3(w.x, best, w.z);
        }
        return null;
    }

    boolean lowering;

    void floatDown(LivingEntity le) {
        riding.floatDown(le);
    }

    // giant against giant (see GiantHull)

    double[] hullNow;
    private long hullTick = Long.MIN_VALUE;
    final List<LivingEntity> giantsNear = new ArrayList<>();
    /** how far other giants' bodies have pushed him in all */
    public double giantPushed;

    /**
     * His rough body for the other giants (every giant has this, found by name): 6 numbers a column, x, z,
     * radius, bottom y, top y, kind (0 body, 1 limb), in the world. Worked out at most once a tick, when asked.
     */
    public double[] jjHull() {
        long now = level().getGameTime();
        if (hullNow != null && hullTick == now) return hullNow;
        if (!BellSolid.ready() || isRemoved() || ghost || solidVer == 0) return new double[0];
        hullNow = GiantHull.fromBody(this, BellSolid.hullBalls(BellSolid.hullUse()), BellSolid.hullKinds());
        hullTick = now;
        return hullNow;
    }

    /** how near another giant his body comes (the gap between their bodies): close enough for his arms and strands */
    public double contactGap() {
        return 1 + 2 * bellScale();
    }

    /** gap to that giant's hull (below 0: overlapping), or null if either has none */
    public @Nullable Double hullGapTo(Entity giant) {
        double[] me = jjHull(), them = GiantHull.of(giant, Giants.TAG);
        if (me.length == 0 || them.length == 0) return null;
        return GiantHull.gap(me, them);
    }

    /** the other giants near him (looked for every 5 ticks) */
    public List<LivingEntity> giantsNear() {
        return giantsNear;
    }

    /**
     * Going for another giant: where its body is, from his. Null when it isn't a giant. The height (world y) his body
     * must keep over it so his dome and arms stay out of it (his strands may hang over it), and whether he is already
     * in reach across (so he stops drifting in).
     */
    @Nullable
    double[] giantPlan;

    @Nullable
    LivingEntity giantPlanFor;
}
