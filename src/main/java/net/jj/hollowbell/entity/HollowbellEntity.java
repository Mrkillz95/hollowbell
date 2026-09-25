package net.jj.hollowbell.entity;

import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.item.CodexItem;
import net.jj.hollowbell.rig.BellModel;
import net.jj.hollowbell.rig.BellRig;
import net.jj.hollowbell.rig.BellState;
import net.jj.hollowbell.world.BellWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Hollowbell: JJ's KillzAI build brought to life. A huge green jellyfish of glass, copper and bone that drifts over
 * the land on its hanging strands and pulls things up into its hollow dome.
 *
 * The body is drawn from the build block for block (see the client's BellRenderer). The server keeps the same pose
 * the client draws, so a hit lands on exactly the block you swung at: see {@link #hitBy}. His own health is kept
 * here rather than in the game's (which can't go past 1024).
 */
public class HollowbellEntity extends Monster {
    public static final int CALM = 0, HUNTER = 1, GUARDIAN = 2;
    public static final float MIN_SCALE = 0.03f, MAX_SCALE = 2f;

    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_HP = SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HP_MAX = SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_MOVE = SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MOVE_ARG = SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_MOVE_START = SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Vector3f> DATA_AIM = SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> DATA_LIFT = SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> DATA_PULSE_START = SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> DATA_PULSE_POWER = SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.FLOAT);
    /** lower, tilt x, tilt z: what his popped pods do to him */
    private static final EntityDataAccessor<Vector3f> DATA_HANG = SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> DATA_SUNK = SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<CompoundTag> DATA_PARTS = SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Integer> DATA_FLAGS = SynchedEntityData.defineId(HollowbellEntity.class, EntityDataSerializers.INT);
    private static final int F_STAY = 1, F_RIDDEN = 2;

    public final BellRig rig = BellRig.get();
    /** the pose the server works with (and the client, for aiming and hits) */
    public final BellState state = new BellState(rig);
    public final Matrix4f[] pose = rig.newPose();
    private long poseTick = Long.MIN_VALUE;
    private final Mood mood = new Mood(this);
    final BellMoves moves = new BellMoves(this);

    // ---- his parts
    private final float[] podHp = new float[rig.pods.length];
    /** gametime a popped pod starts growing back */
    private final long[] podRegrowAt = new long[rig.pods.length];
    /** he stays down at least until this gametime once enough pods are popped */
    private long sunkUntil;
    private final int[] eggBack = new int[rig.eggs.length];
    private boolean partsDirty = true;
    private int partsSentAt;

    // ---- his own health
    private float hp = -1f, baseMax = -1f;
    private final java.util.Map<UUID, Long> fighters = new java.util.HashMap<>();

    // ---- going places
    private @Nullable Vec3 home, goal;
    private Vec3 vel = Vec3.ZERO;
    private long nextPulse;
    private int wanderIn;
    private boolean stay;
    private int angerTicks;
    private @Nullable UUID hunted;
    private long bornAt = -1;

    // ---- being him
    private @Nullable LivingEntity rider;
    private @Nullable Seat riderSeat;
    private float driveF, driveS, driveYaw;
    private int driveIdle;
    /** who asked him to come and put them on his crown */
    private @Nullable UUID fetching;

    // ---- bars
    private @Nullable BellBar barHp, barPods;

    // ---- client smoothing
    private float cLower, cTiltX, cTiltZ, cSunk, cDriftX, cDriftZ;
    private CompoundTag cParts;

    /** set by the client: a slam's thump for the screen shake (x y z, how hard) */
    public static QuadHook clientThump = (x, y, z, k) -> {};
    public interface QuadHook { void thump(double x, double y, double z, float k); }

    public HollowbellEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.xpReward = 500;
        this.noCulling = true;
        for (int i = 0; i < podHp.length; i++) podHp[i] = 1f;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 1000.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 256.0).add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.ATTACK_DAMAGE, 10.0);
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
        b.define(DATA_PULSE_START, -1000L);
        b.define(DATA_PULSE_POWER, 1f);
        b.define(DATA_HANG, new Vector3f());
        b.define(DATA_SUNK, 0f);
        b.define(DATA_PARTS, new CompoundTag());
        b.define(DATA_FLAGS, 0);
    }

    // ------------------------------------------------------------------ size, mood, health

    public float bellScale() { return entityData.get(DATA_SCALE); }

    public void setBellScale(float s) {
        s = Mth.clamp(s, MIN_SCALE, MAX_SCALE);
        float ratio = hp > 0 && healthMax() > 0 ? hp / healthMax() : 1f;
        entityData.set(DATA_SCALE, s);
        baseMax = Math.max(40f, HollowbellConfig.V.health * s);
        setMax(baseMax * playerBoost(), ratio);
        refreshDimensions();
    }

    private float playerBoost() {
        if (!HollowbellConfig.V.scaleToPlayers) return 1f;
        int n = Math.max(1, fighters.size());
        return Math.min(3f, 1f + 0.3f * (n - 1));
    }

    private void setMax(float max, float ratio) {
        entityData.set(DATA_HP_MAX, max);
        hp = Mth.clamp(max * ratio, 0f, max);
        entityData.set(DATA_HP, hp);
    }

    public int variant() { return entityData.get(DATA_VARIANT); }
    public void setVariant(int v) { entityData.set(DATA_VARIANT, Mth.clamp(v, 0, 2)); if (v == GUARDIAN && home == null) home = position(); }
    public boolean isHunter() { return variant() == HUNTER; }
    public boolean isGuardian() { return variant() == GUARDIAN; }
    public Mood mood() { return mood; }

    public float healthNow() { return entityData.get(DATA_HP); }
    public float healthMax() { return entityData.get(DATA_HP_MAX); }
    /** below half: the loops turn red and he pulses more */
    public boolean angry() { return healthNow() < healthMax() * 0.5f; }

    public boolean staying() { return (entityData.get(DATA_FLAGS) & F_STAY) != 0; }
    public void setStay(boolean on) { stay = on; setFlag(F_STAY, on); if (on) { goal = null; vel = Vec3.ZERO; } }
    private void setFlag(int f, boolean on) { int v = entityData.get(DATA_FLAGS); entityData.set(DATA_FLAGS, on ? v | f : v & ~f); }
    public boolean ridden() { return (entityData.get(DATA_FLAGS) & F_RIDDEN) != 0; }

    public int moveNow() { return entityData.get(DATA_MOVE); }
    public int moveArg() { return entityData.get(DATA_MOVE_ARG); }
    public float moveT(float partial) { return (float) (level().getGameTime() - entityData.get(DATA_MOVE_START)) + partial; }
    /** resting on the ground: sunk down from popped pods, or down in a drop */
    public boolean resting() { return entityData.get(DATA_SUNK) > 0.5f || moveNow() == Moves.DROP; }
    public boolean sunk() { return entityData.get(DATA_SUNK) > 0.5f; }

    void setMove(int move, int arg, Vector3f aim) {
        entityData.set(DATA_MOVE, move);
        entityData.set(DATA_MOVE_ARG, arg);
        entityData.set(DATA_MOVE_START, level().getGameTime());
        entityData.set(DATA_AIM, new Vector3f(aim));
        entityData.set(DATA_LIFT, -1f);
    }
    void setAim(Vector3f aim) { entityData.set(DATA_AIM, new Vector3f(aim)); }
    void setLift(float l) { entityData.set(DATA_LIFT, l); }
    Vector3f aim() { return entityData.get(DATA_AIM); }

    public int podsLeft() { int n = 0; for (int i = 0; i < rig.pods.length; i++) if (!state.podPopped[i]) n++; return n; }
    public int eggsLeft() { int n = 0; for (int i = 0; i < rig.eggs.length; i++) if (!state.eggGone[i]) n++; return n; }
    public boolean isPodPopped(int i) { return state.podPopped[i]; }
    public float podGrowth(int i) { return state.podGrowth[i]; }
    /** how many popped pods bring him down */
    public int podsToSink() { return Math.max(1, (int) Math.ceil(rig.pods.length * Mth.clamp(HollowbellConfig.V.podsToSink, 0.05f, 1f))); }

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
        double r = 108 * s + 2;
        return new AABB(getX() - r, getY() - 4, getZ() - r, getX() + r, getY() + (rig.crownY + 8) * s, getZ() + r);
    }

    @Override public AABB getBoundingBoxForCulling() { return bodyBox(); }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        double r = Math.min(HollowbellConfig.V.renderDistance, 400 * Math.max(0.1f, bellScale()) + 160);
        return d < r * r;
    }

    // ------------------------------------------------------------------ model space and the world

    /** yaw is fixed for his life: he is round and has no front */
    public Matrix4f modelToWorld(float partial) {
        double x = Mth.lerp(partial, xo, getX()), y = Mth.lerp(partial, yo, getY()), z = Mth.lerp(partial, zo, getZ());
        return new Matrix4f().translate((float) x, (float) y, (float) z).rotateY(-getYRot() * Mth.DEG_TO_RAD).scale(bellScale());
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

    /** a rest-space point of a bone, where it is in the world right now (server pose) */
    public Vec3 boneWorld(int bone, Vector3f rest) { ensurePose(); return toWorld(rig.at(pose, bone, rest)); }
    public Vec3 strandTipWorld(int s) { ensurePose(); return toWorld(rig.strandTip(pose, s)); }
    public Vec3 armTipWorld(int a) { ensurePose(); return toWorld(rig.armTip(pose, a)); }
    public Vec3 podWorld(int p) { return boneWorld(rig.pods[p].bone(), rig.pods[p].centre()); }
    public Vec3 eggWorld(int e) { return boneWorld(rig.eggs[e].bone(), rig.eggs[e].centre()); }
    public Vec3 spotWorld(int k) { return boneWorld(rig.spots[k].bone(), rig.spots[k].centre()); }
    public Vec3 crownWorld() { return boneWorld(rig.crownBone, new Vector3f(0, rig.crownY + 1, 0)); }

    /** the server's pose, worked out once a tick when something needs it */
    public void ensurePose() {
        long now = level().getGameTime();
        if (poseTick == now) return;
        poseTick = now;
        fillState(0f);
        rig.computePose(state, pose);
    }

    /** the pose state as of this moment. On the client this smooths what the server sends. */
    public void fillState(float partial) {
        BellState st = state;
        boolean client = level().isClientSide;
        long gt = level().getGameTime();
        st.time = tickCount + partial;
        st.pulse = Moves.pulseCurve((float) (gt - entityData.get(DATA_PULSE_START)) + partial, entityData.get(DATA_PULSE_POWER));
        Vector3f hang = entityData.get(DATA_HANG);
        float sunkNow = entityData.get(DATA_SUNK);
        if (client) {
            st.lower = cLower; st.tiltX = cTiltX; st.tiltZ = cTiltZ;
            st.driftX = cDriftX; st.driftZ = cDriftZ;
        } else {
            st.lower = hang.x; st.tiltX = hang.y; st.tiltZ = hang.z;
            Vector3f v = dirToModel(vel);
            st.driftX = v.x; st.driftZ = v.z;
        }
        int move = moveNow();
        float t = moveT(partial);
        st.drop = Math.max(Moves.drop(move, t), client ? cSunk : sunkNow);
        Vector3f aim = aim();
        Moves.pose(rig, st, move, t, moveArg(), aim.x, aim.y, aim.z, entityData.get(DATA_LIFT));
        st.red = angry();
        if (isDeadOrDying()) {
            float dt = deathTime + partial;
            st.death = Mth.clamp(dt / 140f, 0f, 1f);
            st.sink = Mth.clamp((dt - 190f) / 120f, 0f, 1f);
        } else { st.death = 0f; st.sink = 0f; }
        if (client) readParts();
    }

    /** the client's pose, for its own drawing of hits and aiming (the renderer makes its own) */
    public boolean clientPoseReady() { return level().isClientSide && tickCount > 1; }

    // ------------------------------------------------------------------ what the client is told about his parts

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
        int mask = t.getInt("P");
        for (int i = 0; i < rig.pods.length && i < 31; i++) state.podPopped[i] = (mask & (1 << i)) != 0;
        byte[] e = t.getByteArray("E");
        for (int i = 0; i < Math.min(e.length, rig.eggs.length); i++) state.eggGone[i] = e[i] != 0;
        byte[] pg = t.getByteArray("G");
        for (int i = 0; i < Math.min(pg.length, rig.pods.length); i++) state.podGrowth[i] = (pg[i] & 0xff) / 255f;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        if (hp < 0 && !level().isClientSide) setBellScale(bellScale());
        super.tick();
        if (level().isClientSide) { clientTick(); return; }
        if (isDeadOrDying()) return;
        serverTick();
    }

    private void clientTick() {
        Vector3f hang = entityData.get(DATA_HANG);
        cLower += (hang.x - cLower) * 0.08f;
        cTiltX += (hang.y - cTiltX) * 0.08f;
        cTiltZ += (hang.z - cTiltZ) * 0.08f;
        cSunk += (entityData.get(DATA_SUNK) - cSunk) * 0.06f;
        Vector3f v = dirToModel(new Vec3(getX() - xo, 0, getZ() - zo));
        cDriftX += (v.x - cDriftX) * 0.1f;
        cDriftZ += (v.z - cDriftZ) * 0.1f;
        if (tickCount % 4 == 0) BellFx.ambient(this);
    }

    private void serverTick() {
        if (bornAt < 0) bornAt = level().getGameTime();
        if (home == null) home = position();
        mood.tick();
        if (angerTicks > 0) angerTicks--;
        long now = level().getGameTime();
        // the game's own health never moves: his is kept apart
        if (getHealth() < getMaxHealth()) setHealth(getMaxHealth());

        partsTick(now);
        riderTick();
        pickTarget();
        moves.tick();
        drift(now);
        ensurePose();
        moves.afterPose();
        stingTick();
        arrowsTick();
        barsTick();
        if (partsDirty && tickCount - partsSentAt >= 4) { partsDirty = false; partsSentAt = tickCount; syncParts(); }
        if (tickCount % 20 == 0) fighters.entrySet().removeIf(e -> now - e.getValue() > 6000);
    }

    // ------------------------------------------------------------------ pods and eggs over time

    private void partsTick(long now) {
        boolean anyChange = false;
        for (int i = 0; i < rig.pods.length; i++) {
            float g = state.podGrowth[i];
            if (g >= 1f || now < podRegrowAt[i]) continue;
            // a popped pod grows back over about twenty seconds, then it can be popped again
            float ng = Math.min(1f, g + 1f / 400f);
            if ((int) (ng * 40) != (int) (g * 40) || ng >= 1f) anyChange = true;
            state.podGrowth[i] = ng;
            if (ng >= 1f) { state.podPopped[i] = false; podHp[i] = 1f; }
        }
        if (anyChange) partsDirty = true;
        // egg clumps come back slowly on the strands
        for (int i = 0; i < rig.eggs.length; i++) {
            if (!state.eggGone[i] || eggBack[i] <= 0) continue;
            if (--eggBack[i] == 0) { state.eggGone[i] = false; partsDirty = true; }
        }
        if (tickCount % 5 == 0) hangFromPods();
    }

    /**
     * The pods keep him up. Each popped one lets him hang a little lower and lean toward that side; pop a third of
     * them and he loses his lift and sinks right down, and stays down until enough have grown back.
     */
    private void hangFromPods() {
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
            sunkUntil = now + Math.max(5, HollowbellConfig.V.sunkSeconds) * 20L;
            sound(position(), net.minecraft.sounds.SoundEvents.ANVIL_LAND, 3f, 0.4f);
            for (ServerPlayer p : level() instanceof ServerLevel sl ? sl.players() : List.<ServerPlayer>of())
                if (p.distanceToSqr(this) < Mth.square(120 * bellScale() + 60)) p.displayClientMessage(Component.translatable("message.hollowbell.sunk"), true);
        } else if (sunk > 0.5f && popped < need && now >= sunkUntil) entityData.set(DATA_SUNK, 0f);
    }

    // ------------------------------------------------------------------ where he goes

    public void setGoal(@Nullable Vec3 g) { goal = g; if (g != null) setStay(false); }
    public @Nullable Vec3 goal() { return goal; }
    public void callTo(Player p) { setGoal(p.position()); }
    public @Nullable Vec3 home() { return home; }
    public void setHome(Vec3 h) { home = h; }

    /** how far he can see things to hunt */
    public double senseRange() { return 110 * bellScale() + 40; }
    public double guardRange() { return 140 * bellScale() + 40; }
    /** his bell's radius in the world */
    public double bellRadius() { return 88 * bellScale(); }

    private void drift(long now) {
        float s = bellScale();
        boolean down = resting() || isDeadOrDying();
        Vec3 want = null;
        if (rider != null && (driveF != 0f || driveS != 0f)) {
            float yr = driveYaw * Mth.DEG_TO_RAD;
            Vec3 fw = new Vec3(-Mth.sin(yr), 0, Mth.cos(yr)), rt = new Vec3(Mth.cos(yr), 0, Mth.sin(yr));
            want = position().add(fw.scale(driveF * 60).add(rt.scale(driveS * 60)));
        } else if (!stay && !moves.holdsStill()) {
            LivingEntity t = getTarget();
            if (fetchingNow() != null) want = fetchingNow().position();
            else if (goal != null) {
                want = goal;
                if (horiz(goal) < 6 + 10 * s) { goal = null; want = null; }
            } else if (t != null) {
                // hunting: he hangs right over you, so the strands can reach
                if (horiz(t.position()) > 12 * s + 2) want = t.position();
            } else {
                if (--wanderIn <= 0 || (home != null && horiz(home) > wanderRange() * 1.3)) {
                    wanderIn = 300 + random.nextInt(500);
                    Vec3 c = isGuardian() && home != null ? home : position();
                    double a = random.nextDouble() * Math.PI * 2, d = random.nextDouble() * wanderRange();
                    goal = null;
                    wanderTo = c.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                }
                if (wanderTo != null && horiz(wanderTo) > 8 * s + 3) want = wanderTo;
            }
        }
        // each pulse pushes him along
        int period = angry() ? 46 : 70;
        if (rider != null && want != null) period = 36;
        if (now >= nextPulse && !down) {
            pulse(1f);
            nextPulse = now + period + random.nextInt(12);
            if (want != null) {
                Vec3 d = want.subtract(position()).multiply(1, 0, 1);
                double len = d.length();
                if (len > 0.01) {
                    double speed = (0.10 + 0.16 * Math.sqrt(s)) * (rider != null ? 1.5 : 1.0);
                    // slowing down as he gets there, so he doesn't overshoot
                    double k = Math.min(1.0, len / (30 * s + 10));
                    vel = vel.add(d.scale(1 / len).scale(speed * 2.2 * k));
                }
            }
        }
        if (down) vel = vel.scale(0.8);
        vel = vel.scale(0.965);
        double cap = 0.6 + 0.4 * s;
        if (vel.lengthSqr() > cap * cap) vel = vel.normalize().scale(cap);
        double nx = getX() + vel.x, nz = getZ() + vel.z;
        // he floats along with the strand ends just touching the ground under his middle
        double ground = groundAt(nx, nz);
        double ny = getY() + Mth.clamp(ground - getY(), -0.6 - s, 0.6 + s) * 0.25;
        if (Math.abs(ground - getY()) > 60 * s + 20) ny = ground;        // came off a cliff or up out of the sea
        setPos(nx, ny, nz);
        setDeltaMovement(Vec3.ZERO);
        if (tickCount % 10 == 0 && HollowbellConfig.V.griefing && vel.lengthSqr() > 0.001) moves.flattenUnderStrands();
    }

    private @Nullable Vec3 wanderTo;
    private double wanderRange() { return (isGuardian() ? 80 : 150) * bellScale() + 30; }
    public double horiz(Vec3 p) { double dx = p.x - getX(), dz = p.z - getZ(); return Math.sqrt(dx * dx + dz * dz); }

    /** the top of the ground (or water) at a spot */
    public double groundAt(double x, double z) {
        BlockPos p = BlockPos.containing(x, getY(), z);
        if (!level().hasChunkAt(p)) return getY();
        return level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
    }

    /** a pulse of the bell: it squeezes in, and a strong one is the pulse wave */
    public void pulse(float power) {
        entityData.set(DATA_PULSE_START, level().getGameTime());
        entityData.set(DATA_PULSE_POWER, power);
        sound(boneWorld(rig.rimBone, new Vector3f(0, rig.rimY + 20, 0)), net.minecraft.sounds.SoundEvents.CONDUIT_AMBIENT_SHORT, 1.4f + power, 0.5f);
    }

    // ------------------------------------------------------------------ who he goes after

    /** the player holding the book for him (the nearest one carrying it, within reach of the book) */
    public @Nullable Player bookHolder() {
        Player best = null; double bd = Double.MAX_VALUE;
        double r = HollowbellConfig.V.bookRange;
        for (Player p : level().players()) {
            if (!CodexItem.carriedBy(p)) continue;
            double d = p.distanceToSqr(this);
            if (d < r * r && d < bd) { bd = d; best = p; }
        }
        return best;
    }

    /** he leaves this one alone: the book, the safe list, creative players */
    public boolean spares(Entity e) {
        if (e instanceof Player p) {
            if (p.isCreative() || p.isSpectator()) return true;
            if (mood.turnedOn(p.getUUID()) || mood.hunting(p.getUUID())) return false;
            Player holder = bookHolder();
            if (holder == null) return false;
            if (holder == p) return true;
            if (level() instanceof ServerLevel sl) return BellWorld.get(sl.getServer()).onList(holder.getUUID(), p.getUUID());
            return false;
        }
        if (e instanceof HollowbellEntity || e instanceof Belling) return true;
        Player holder = bookHolder();
        if (holder != null && level() instanceof ServerLevel sl && e instanceof LivingEntity le)
            return BellWorld.get(sl.getServer()).kindOnList(holder.getUUID(), le.getType());
        return false;
    }

    public boolean fairGame(@Nullable LivingEntity e) {
        return e != null && e.isAlive() && !e.isRemoved() && e.level() == level() && !spares(e)
                && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand) && e != rider && !moves.caught(e);
    }

    private void pickTarget() {
        LivingEntity t = getTarget();
        if (t != null && (!fairGame(t) || horiz(t.position()) > senseRange() * 1.6 || (isGuardian() && home != null
                && t.position().distanceTo(home) > guardRange() * 1.3 && angerTicks <= 0))) { setTarget(null); t = null; }
        if (hunted != null && level() instanceof ServerLevel sl) {
            Entity h = sl.getEntity(hunted);
            if (h instanceof LivingEntity le && fairGame(le)) { setTarget(le); return; }
            if (h == null || !h.isAlive()) hunted = null;
        }
        if (tickCount % 20 != 0 || t != null) return;
        if (variant() == CALM && angerTicks <= 0) return;
        double r = senseRange();
        Player best = null; double bd = Double.MAX_VALUE;
        for (Player p : level().players()) {
            if (!fairGame(p)) continue;
            double d = horiz(p.position());
            if (d > r) continue;
            if (isGuardian() && home != null && p.position().distanceTo(home) > guardRange()) continue;
            if (d < bd) { bd = d; best = p; }
        }
        if (best != null) setTarget(best);
    }

    /** the book: go and get these */
    public void sendAfter(LivingEntity e) { hunted = e.getUUID(); setTarget(e); angerTicks = 1200; setStay(false); }
    public void clearHitList() { hunted = null; setTarget(null); angerTicks = 0; }
    public void forgiveAll() { mood.settle(null); clearHitList(); }

    // ------------------------------------------------------------------ being hit

    /** set just before a hit is passed to the game so hurt() knows which block of him it landed on */
    private int pendingBone = -1;
    private boolean pendingInside;

    public static boolean isImmuneTo(DamageSource src) {
        return src.is(DamageTypes.IN_WALL) || src.is(DamageTypes.FALL) || src.is(DamageTypes.DROWN) || src.is(DamageTypes.CRAMMING)
                || src.is(DamageTypes.FLY_INTO_WALL) || src.is(DamageTypes.IN_FIRE) || src.is(DamageTypes.LAVA) || src.is(DamageTypes.ON_FIRE)
                || src.is(DamageTypes.HOT_FLOOR) || src.is(DamageTypes.SWEET_BERRY_BUSH) || src.is(DamageTypes.CACTUS);
    }

    /**
     * A player's swing that the client says landed on him. The server looks for itself along the player's own
     * line of sight (with a little give, since the two sides see him a tick apart) and passes the hit through the
     * game's own attack so weapons, enchantments and crits all count.
     */
    public void hitBy(ServerPlayer p, int claimedBone) {
        if (isDeadOrDying() && !isRemoved()) return;
        ensurePose();
        double reach = p.entityInteractionRange() + 1.5;
        Vec3 eye = p.getEyePosition(), look = p.getViewVector(1f);
        BellRig.Hit h = raycast(eye, look, reach);
        int bone = h != null ? h.bone() : -1;
        if (bone < 0 && claimedBone >= 0 && claimedBone < rig.boneCount()) {
            // the client saw it a moment ago: take its word if that bone is right there in front of the player
            Vec3 c = boneCentreWorld(claimedBone);
            if (c != null && nearestOnBone(claimedBone, eye, reach + 2.5 + 4 * bellScale())) bone = claimedBone;
        }
        if (bone < 0) return;
        pendingBone = bone;
        pendingInside = moves.caught(p);
        try { p.attack(this); }
        finally { pendingBone = -1; pendingInside = false; }
    }

    private @Nullable Vec3 boneCentreWorld(int b) {
        float[] bb = BellModel.get().bounds[b];
        if (bb == null) return null;
        return boneWorld(b, new Vector3f((bb[0] + bb[3]) / 2, (bb[1] + bb[4]) / 2, (bb[2] + bb[5]) / 2));
    }

    private boolean nearestOnBone(int b, Vec3 eye, double within) {
        Vector3f m = toModel(eye);
        Matrix4f inv = new Matrix4f();
        int pad = (int) Math.ceil(within / bellScale());
        if (pad > 12) pad = 12;
        return rig.touches(pose, b, m, pad, inv);
    }

    /** the first block of him along a line in the world, up to maxDist blocks */
    public @Nullable BellRig.Hit raycast(Vec3 from, Vec3 dir, double maxDist) {
        Vector3f o = toModel(from);
        Vec3 unit = dir.normalize();
        Vector3f d = dirToModel(unit);
        return rig.raycast(state, pose, o, d, (float) maxDist);
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide || isDeadOrDying()) return false;
        if (src.is(DamageTypes.GENERIC_KILL)) { hp = 0; die(src); return true; }
        if (isInvulnerableTo(src) || isImmuneTo(src)) return false;
        Entity att = src.getEntity();
        if (att == this || att instanceof Belling || (att instanceof LivingEntity le && moves.caught(le) && !(le instanceof Player))) return false;
        int bone = pendingBone;
        return applyDamage(src, amount, bone, pendingInside);
    }

    /** how much of a hit reaches him, by the block it landed on */
    public float worth(int bone, boolean inside) {
        if (bone < 0) return 0.1f;
        return switch (rig.kind[bone]) {
            case CROWN -> inside ? 4f : 3f;
            case SPOT -> inside ? 3.5f : 2.5f;
            case POD -> 1f;
            case EGG -> 0.3f;
            default -> 0.1f;           // the copper and bone take very little
        };
    }

    public boolean applyDamage(DamageSource src, float amount, int bone, boolean inside) {
        if (level().isClientSide || isDeadOrDying()) return false;
        Entity att = src.getEntity();
        float dealt = amount * worth(bone, inside);
        if (resting()) dealt *= 1.3f;                       // down on the ground he can really be hurt
        if (bone >= 0) {
            BellRig.Kind k = rig.kind[bone];
            int part = rig.part[bone];
            Vec3 at = boneCentreWorld(bone);
            if (k == BellRig.Kind.POD && !state.podPopped[part]) {
                podHp[part] -= amount / Math.max(4f, healthMax() * 0.012f);
                if (at != null) particles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.WHITE_STAINED_GLASS.defaultBlockState()), at, 16, rig.pods[part].radius() * bellScale() * 0.5, 0.3);
                if (podHp[part] <= 0f) popPod(part, att);
            } else if (k == BellRig.Kind.STRAND) {
                moves.strandHit(part, amount, att);
            } else if (k == BellRig.Kind.ARM) {
                moves.armHit(part, amount, att);
            }
            if ((k == BellRig.Kind.SPOT || k == BellRig.Kind.CROWN) && at != null)
                particles(net.minecraft.core.particles.ParticleTypes.GLOW, at, 20, 4 * bellScale() + 0.5, 0.1);
        }
        if (att instanceof LivingEntity le && moves.caught(le)) moves.hurtFromInside(dealt);
        return takeDamage(src, dealt);
    }

    private boolean takeDamage(DamageSource src, float dealt) {
        Entity att = src.getEntity();
        hp = Math.max(0f, healthNow() - dealt);
        entityData.set(DATA_HP, hp);
        level().broadcastDamageEvent(this, src);
        if (att instanceof Player p && !p.isCreative()) {
            boolean had = fighters.containsKey(p.getUUID());
            fighters.put(p.getUUID(), level().getGameTime());
            if (!had && fighters.size() > 1) setMax(baseMax * playerBoost(), healthNow() / Math.max(1f, healthMax()));
            setLastHurtByPlayer(p);
            if (spares(p) && !p.isSpectator()) {
                if (mood.struck(p.getUUID())) {
                    setTarget(p); angerTicks = 2400;
                    p.displayClientMessage(Component.translatable("message.hollowbell.had_enough"), false);
                } else {
                    int left = mood.strikesLeft(p.getUUID());
                    if (left < Integer.MAX_VALUE) p.displayClientMessage(Component.translatable("message.hollowbell.lets_it_go", left), true);
                }
            } else if (!moves.caught(p)) { setTarget(p); angerTicks = 1200; }
        } else if (att instanceof LivingEntity le && !spares(le)) {
            setLastHurtByMob(le);
            if (getTarget() == null) { setTarget(le); angerTicks = Math.max(angerTicks, 600); }
        }
        if (hp <= 0f) { setHealth(0f); die(src); }
        return true;
    }

    public void popPod(int i, @Nullable Entity by) {
        if (state.podPopped[i]) return;
        state.podPopped[i] = true;
        state.podGrowth[i] = 0f;
        podHp[i] = 0f;
        podRegrowAt[i] = level().getGameTime() + Math.max(1, HollowbellConfig.V.podRegrowSeconds) * 20L;
        partsDirty = true;
        Vec3 c = podWorld(i);
        sound(c, net.minecraft.sounds.SoundEvents.GLASS_BREAK, 3f, 0.5f);
        sound(c, net.minecraft.sounds.SoundEvents.SLIME_DEATH, 2f, 0.4f);
        particles(net.minecraft.core.particles.ParticleTypes.SQUID_INK, c, 40, rig.pods[i].radius() * bellScale() * 0.6, 0.2);
        // a pod out of his twenty-three is worth a good piece of him
        float worth = healthMax() * 0.015f;
        hp = Math.max(0f, healthNow() - worth);
        entityData.set(DATA_HP, hp);
        if (by instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("message.hollowbell.pod_popped", podsLeft()), true);
        if (level() instanceof ServerLevel sl) {
            ItemStack drop = new ItemStack(ModItems.POD);
            sl.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(sl, c.x, c.y, c.z, drop));
        }
        if (hp <= 0f) { setHealth(0f); die(by instanceof Player pl ? damageSources().playerAttack(pl) : damageSources().generic()); }
        else hangFromPods();
    }

    /** /hollowbell popped n: pops the first n pods */
    public void popPods(int n) {
        for (int i = 0; i < rig.pods.length && n > 0 && !isDeadOrDying(); i++) if (!state.podPopped[i]) { popPod(i, null); n--; }
    }

    /** grows every pod back straight away */
    public void mendPods() {
        for (int i = 0; i < rig.pods.length; i++) { state.podPopped[i] = false; state.podGrowth[i] = 1f; podHp[i] = 1f; podRegrowAt[i] = 0; }
        sunkUntil = 0;
        partsDirty = true;
        hangFromPods();
    }

    void eggGone(int i) { state.eggGone[i] = true; eggBack[i] = 20 * 60 * 5; partsDirty = true; }

    public void heal() { hp = healthMax(); entityData.set(DATA_HP, hp); }
    public void setHealthTo(float v) { hp = Mth.clamp(v, 1f, healthMax()); entityData.set(DATA_HP, hp); }
    public void hurtBy(float amount) { applyDamage(damageSources().generic(), amount / 0.1f, -1, false); }

    // ------------------------------------------------------------------ stings and arrows

    /** touching a strand stings: poison and slowness */
    private void stingTick() {
        if (tickCount % 5 != 0) return;
        float s = bellScale();
        AABB box = new AABB(getX() - 100 * s, getY() - 2, getZ() - 100 * s, getX() + 100 * s, getY() + rig.rimY * s, getZ() + 100 * s);
        List<LivingEntity> near = level().getEntitiesOfClass(LivingEntity.class, box, e -> fairGame(e) && !(e instanceof HollowbellEntity));
        if (near.isEmpty()) return;
        Matrix4f inv = new Matrix4f();
        for (LivingEntity e : near) {
            Vector3f m = toModel(e.position().add(0, e.getBbHeight() * 0.5, 0));
            int pad = Math.max(1, Math.round(0.7f / s));
            if (pad > 6) pad = 6;
            boolean hit = false;
            for (var S : rig.strands) {
                // cheap first: is it anywhere near this strand's line
                Vector3f top = S.joints()[0];
                if (Math.abs(m.x - top.x) > 40 || Math.abs(m.z - top.z) > 40) continue;
                for (int b : S.bones()) if (rig.touches(pose, b, m, pad, inv)) { hit = true; break; }
                if (hit) break;
            }
            if (hit) moves.sting(e);
        }
    }

    /** arrows and other things thrown at him: the game can't find him for them, so he looks for them himself */
    private void arrowsTick() {
        List<net.minecraft.world.entity.projectile.Projectile> ps = level().getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class, bodyBox(),
                p -> p.getDeltaMovement().lengthSqr() > 0.04 && !(p.getOwner() instanceof HollowbellEntity));
        for (var p : ps) {
            if (p instanceof net.minecraft.world.entity.projectile.AbstractArrow a && a.isNoGravity() && a.getDeltaMovement().lengthSqr() < 0.05) continue;
            Vec3 v = p.getDeltaMovement();
            BellRig.Hit h = raycast(p.position(), v, v.length() + 0.5);
            if (h == null) continue;
            float dmg;
            if (p instanceof net.minecraft.world.entity.projectile.AbstractArrow a) dmg = (float) (a.getBaseDamage() * v.length());
            else dmg = 2f;
            DamageSource src = p.getOwner() instanceof LivingEntity le ? damageSources().mobProjectile(p, le) : damageSources().generic();
            applyDamage(src, Math.max(1f, dmg), h.bone(), false);
            Vec3 at = p.position().add(v.normalize().scale(h.t()));
            sound(at, net.minecraft.sounds.SoundEvents.ARROW_HIT, 1f, 0.8f);
            if (p instanceof net.minecraft.world.entity.projectile.AbstractArrow a && a.pickup == net.minecraft.world.entity.projectile.AbstractArrow.Pickup.ALLOWED
                    && level() instanceof ServerLevel sl) {
                sl.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(sl, at.x, at.y, at.z, new ItemStack(net.minecraft.world.item.Items.ARROW)));
            }
            p.discard();
        }
    }

    // ------------------------------------------------------------------ bars

    public void clearBars() {
        if (barHp != null) barHp.removeAllPlayers();
        if (barPods != null) barPods.removeAllPlayers();
    }

    private void barsTick() {
        if (tickCount % 10 != 0) return;
        if (!HollowbellConfig.V.bossBar) { clearBars(); return; }
        if (barHp == null) {
            barHp = new BellBar(BellBar.idFor(getUUID(), "hp"), Component.translatable("bar.hollowbell.health"), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_10);
            barPods = new BellBar(BellBar.idFor(getUUID(), "pods"), Component.empty(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
        }
        barHp.setProgress(Mth.clamp(healthNow() / Math.max(1f, healthMax()), 0f, 1f));
        barHp.setColor(angry() ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.GREEN);
        barPods.setProgress(podsLeft() / (float) Math.max(1, rig.pods.length));
        barPods.setName(Component.translatable(sunk() ? "bar.hollowbell.pods_sunk" : "bar.hollowbell.pods", podsLeft(), rig.pods.length));
        barPods.setColor(sunk() ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.YELLOW);
        double r = 90 * bellScale() + 90;
        List<ServerPlayer> want = new ArrayList<>();
        if (level() instanceof ServerLevel sl) for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(getX(), p.getY(), getZ()) < r * r || moves.caught(p) || p == rider) want.add(p);
        }
        for (BellBar b : new BellBar[]{barHp, barPods}) {
            for (ServerPlayer p : new ArrayList<>(b.getPlayers())) if (!want.contains(p)) b.removePlayer(p);
            for (ServerPlayer p : want) b.addPlayer(p);
        }
    }

    @Override
    public void remove(RemovalReason why) {
        if (!level().isClientSide && why != RemovalReason.UNLOADED_TO_CHUNK && why != RemovalReason.UNLOADED_WITH_PLAYER)
            HollowbellMod.LOG.info("Hollowbell removed at {} ({})", position(), why);
        clearBars();
        if (!level().isClientSide) { dropRider(); moves.letGoOfEverything(why == RemovalReason.KILLED || why == RemovalReason.DISCARDED); }
        super.remove(why);
    }

    // ------------------------------------------------------------------ riding on his crown: being him

    public @Nullable LivingEntity rider() { return rider; }
    public boolean carrying() { return rider != null; }

    public boolean usesSeat(Seat s) { return s == riderSeat || moves.usesSeat(s); }

    public @Nullable Player fetchingNow() {
        if (fetching == null || !(level() instanceof ServerLevel sl)) return null;
        return sl.getServer().getPlayerList().getPlayer(fetching);
    }

    /** the book's "sit on his crown": he comes to you, a strand picks you up and puts you on the crown */
    public boolean comeAndGetMe(Player p) {
        if (rider != null || isDeadOrDying() || resting()) return false;
        fetching = p.getUUID();
        setStay(false);
        return true;
    }
    public void stopFetch() { fetching = null; }
    public boolean comingForSomebody() { return fetching != null; }

    /** called by the moves once a strand has lifted the one who asked up to the crown */
    void seatOnCrown(LivingEntity who) {
        fetching = null;
        if (riderSeat != null) riderSeat.discard();
        riderSeat = new Seat(level(), this);
        Vec3 c = crownWorld();
        riderSeat.setPos(c.x, c.y, c.z);
        level().addFreshEntity(riderSeat);
        who.stopRiding();
        who.startRiding(riderSeat, true);
        rider = who;
        setFlag(F_RIDDEN, true);
        driveF = driveS = 0f; driveYaw = who.getYRot();
        clearHitList();
        if (who instanceof ServerPlayer sp) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp, new net.jj.hollowbell.net.BeingHimPayload(getId(), true));
            sp.displayClientMessage(Component.translatable("message.hollowbell.on_crown"), true);
        }
        sound(c, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 2f, 0.5f);
    }

    /** straight on to the crown, no strand: the command and the tests */
    public boolean possess(LivingEntity who) {
        if (rider != null || isDeadOrDying()) return false;
        seatOnCrown(who);
        return true;
    }

    public void dropRider() {
        if (rider instanceof ServerPlayer sp)
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp, new net.jj.hollowbell.net.BeingHimPayload(getId(), false));
        LivingEntity who = rider;
        rider = null;
        setFlag(F_RIDDEN, false);
        if (riderSeat != null) { riderSeat.ejectPassengers(); riderSeat.discard(); riderSeat = null; }
        if (who != null && who.isAlive()) {
            // set down on the ground beside him, gently
            double a = random.nextDouble() * Math.PI * 2, r = bellRadius() + 3;
            double x = getX() + Math.cos(a) * r, z = getZ() + Math.sin(a) * r;
            double y = groundAt(x, z);
            who.teleportTo(x, y + 0.5, z);
            who.fallDistance = 0;
            who.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 80, 0, false, false));
        }
        driveF = driveS = 0f;
    }

    public void drive(LivingEntity who, float forward, float strafe, float yaw) {
        if (rider != who) return;
        driveF = Mth.clamp(forward, -1f, 1f);
        driveS = Mth.clamp(strafe, -1f, 1f);
        driveYaw = yaw;
        driveIdle = 0;
    }

    private void riderTick() {
        if (rider == null) return;
        if (!rider.isAlive() || rider.isRemoved() || rider.level() != level()) { dropRider(); return; }
        if (riderSeat == null || riderSeat.isRemoved()) { riderSeat = new Seat(level(), this); level().addFreshEntity(riderSeat); }
        ensurePose();
        Vec3 c = crownWorld();
        riderSeat.moveTo(c.x, c.y, c.z);
        if (rider.getVehicle() != riderSeat) rider.startRiding(riderSeat, true);
        rider.fallDistance = 0;
        if (++driveIdle > 60) { driveF = 0; driveS = 0; }
    }

    /** a move pressed while riding: the book's clock applies */
    public boolean forceMove(int which) { return moves.force(which, getTarget()); }
    public boolean forceMove(int which, @Nullable LivingEntity at) { return moves.force(which, at != null ? at : getTarget()); }

    // ------------------------------------------------------------------ dying

    @Override
    public void die(DamageSource src) {
        if (!level().isClientSide) {
            HollowbellMod.LOG.info("Hollowbell died at {} ({})", position(), src.getMsgId());
            dropRider();
            stopFetch();
            moves.letGoOfEverything(true);
            setMove(Moves.NONE, -1, new Vector3f());
            sound(position().add(0, rig.rimY * bellScale(), 0), net.minecraft.sounds.SoundEvents.WARDEN_DEATH, 4f, 0.5f);
        }
        super.die(src);
    }

    private @Nullable DamageSource deathLoot;
    private boolean lootDropped;
    public static final int DEATH_LENGTH = 320;

    @Override protected void dropAllDeathLoot(ServerLevel level, DamageSource src) { deathLoot = src; }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (level().isClientSide) return;
        float s = bellScale();
        ensurePose();
        if (deathTime == 60 || deathTime == 100 || deathTime == 130) {
            for (int i = 0; i < 6; i++) {
                var S = rig.strands[random.nextInt(rig.strands.length)];
                Vec3 c = strandTipWorld(S.k());
                sound(c, net.minecraft.sounds.SoundEvents.BONE_BLOCK_BREAK, 2f, 0.5f + random.nextFloat() * 0.3f);
            }
        }
        if (deathTime == 140) {
            Vec3 c = position();
            sound(c, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.4f);
            particles(net.minecraft.core.particles.ParticleTypes.CLOUD, c.add(0, 2, 0), 120, 80 * s, 0.1);
            clientThump.thump(c.x, c.y, c.z, 1f);
        }
        if (deathTime > 190 && deathTime % 8 == 0)
            particles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                    net.minecraft.world.level.block.Blocks.OXIDIZED_COPPER.defaultBlockState()), position().add(0, 1, 0), 60, 70 * s, 0.2);
        if (deathTime >= DEATH_LENGTH && !isRemoved()) {
            if (!lootDropped && level() instanceof ServerLevel sl)
                super.dropAllDeathLoot(sl, deathLoot != null ? deathLoot : damageSources().generic());
            lootDropped = true;
            particles(net.minecraft.core.particles.ParticleTypes.POOF, position().add(0, 1, 0), 120, 60 * s, 0.05);
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    public int deathLength() { return DEATH_LENGTH; }

    @Override protected boolean shouldDropLoot() { return true; }

    // ------------------------------------------------------------------ the game's own habits, none of which fit him

    @Override public boolean isPushable() { return false; }
    @Override protected void pushEntities() {}
    @Override public void push(Entity e) {}
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override protected void checkInsideBlocks() {}
    @Override public boolean updateInWaterStateAndDoFluidPushing() { return false; }
    @Override public boolean isInWater() { return false; }
    @Override public void travel(Vec3 v) {}
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public boolean fireImmune() { return true; }
    @Override public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance e) { return false; }
    @Override public boolean isAttackable() { return true; }
    @Override public boolean skipAttackInteraction(Entity e) { return false; }
    @Override protected void registerGoals() {}
    @Override public boolean displayFireAnimation() { return false; }
    @Override public void knockback(double d, double x, double z) {}
    @Override protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource s) { return net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_HIT; }
    @Override protected net.minecraft.sounds.SoundEvent getDeathSound() { return net.minecraft.sounds.SoundEvents.WARDEN_DEATH; }
    @Override protected float getSoundVolume() { return 2.5f * HollowbellConfig.V.soundVolume; }
    @Override public float getVoicePitch() { return 0.5f; }
    @Override public ItemStack getPickResult() { return new ItemStack(isHunter() ? ModItems.HUNTING_EGG : isGuardian() ? ModItems.GUARDIAN_EGG : ModItems.CALM_EGG); }

    // ------------------------------------------------------------------ little helpers

    public void sound(Vec3 at, SoundEvent ev, float vol, float pitch) {
        float v = vol * HollowbellConfig.V.soundVolume;
        if (v <= 0f) return;
        level().playSound(null, at.x, at.y, at.z, ev, SoundSource.HOSTILE, v * (0.6f + 0.4f * Math.min(1f, bellScale() * 2f)), pitch);
    }

    public void particles(ParticleOptions p, Vec3 at, int n, double spread, double speed) {
        if (level() instanceof ServerLevel sl) sl.sendParticles(p, at.x, at.y, at.z, n, spread, spread * 0.5, spread, speed);
    }

    /** how hard he hits: small ones hit less */
    public float dmg(float base, Entity to) {
        float f = base * HollowbellConfig.V.damageMultiplier * Mth.clamp(0.25f + 0.75f * (float) Math.sqrt(bellScale()), 0.3f, 1.4f);
        if (!(to instanceof Player)) f *= HollowbellConfig.V.mobDamage;
        return f;
    }

    public long bornAt() { return bornAt; }

    // ------------------------------------------------------------------ saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("BellScale", bellScale());
        tag.putInt("BellVariant", variant());
        tag.putFloat("BellHp", healthNow());
        tag.putFloat("BellHpMax", healthMax());
        tag.putFloat("BellBaseMax", baseMax);
        int mask = 0;
        for (int i = 0; i < rig.pods.length && i < 31; i++) if (state.podPopped[i]) mask |= 1 << i;
        tag.putInt("Pods", mask);
        ListTag ph = new ListTag();
        for (float v : podHp) ph.add(net.minecraft.nbt.FloatTag.valueOf(v));
        tag.put("PodHp", ph);
        byte[] e = new byte[rig.eggs.length];
        for (int i = 0; i < e.length; i++) e[i] = (byte) (state.eggGone[i] ? 1 : 0);
        tag.putByteArray("Eggs", e);
        ListTag pg = new ListTag();
        long now = level().getGameTime();
        for (int i = 0; i < rig.pods.length; i++) {
            CompoundTag o = new CompoundTag();
            o.putFloat("G", state.podGrowth[i]);
            o.putLong("Wait", Math.max(0, podRegrowAt[i] - now));
            pg.add(o);
        }
        tag.put("PodGrowth", pg);
        tag.putFloat("Sunk", entityData.get(DATA_SUNK));
        tag.putLong("SunkFor", Math.max(0, sunkUntil - now));
        if (home != null) { tag.putDouble("HomeX", home.x); tag.putDouble("HomeY", home.y); tag.putDouble("HomeZ", home.z); }
        tag.putBoolean("Stay", stay);
        if (hunted != null) tag.putUUID("Hunted", hunted);
        mood.save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("BellScale")) entityData.set(DATA_SCALE, Mth.clamp(tag.getFloat("BellScale"), MIN_SCALE, MAX_SCALE));
        if (tag.contains("BellVariant")) entityData.set(DATA_VARIANT, Mth.clamp(tag.getInt("BellVariant"), 0, 2));
        // a spawn egg's own tag: 0 calm, 1 hunting, 2 guardian, 3 small
        if (tag.contains("HollowbellEgg")) {
            int v = tag.getInt("HollowbellEgg");
            entityData.set(DATA_VARIANT, v == 3 ? HUNTER : v);
            entityData.set(DATA_SCALE, v == 3 ? HollowbellConfig.V.smallEggScale : HollowbellConfig.V.spawnEggScale);
        }
        if (tag.contains("BellHpMax")) {
            baseMax = tag.contains("BellBaseMax") ? tag.getFloat("BellBaseMax") : tag.getFloat("BellHpMax");
            entityData.set(DATA_HP_MAX, tag.getFloat("BellHpMax"));
            hp = tag.getFloat("BellHp");
            entityData.set(DATA_HP, hp);
        } else hp = -1f;
        if (tag.contains("Pods")) {
            int mask = tag.getInt("Pods");
            for (int i = 0; i < rig.pods.length && i < 31; i++) state.podPopped[i] = (mask & (1 << i)) != 0;
        }
        if (tag.contains("PodHp", Tag.TAG_LIST)) {
            ListTag ph = tag.getList("PodHp", Tag.TAG_FLOAT);
            for (int i = 0; i < Math.min(ph.size(), podHp.length); i++) podHp[i] = ph.getFloat(i);
        }
        if (tag.contains("Eggs")) {
            byte[] e = tag.getByteArray("Eggs");
            for (int i = 0; i < Math.min(e.length, rig.eggs.length); i++) { state.eggGone[i] = e[i] != 0; if (state.eggGone[i]) eggBack[i] = 20 * 60 * 5; }
        }
        if (tag.contains("PodGrowth", Tag.TAG_LIST)) {
            ListTag pg = tag.getList("PodGrowth", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(pg.size(), rig.pods.length); i++) {
                CompoundTag o = pg.getCompound(i);
                state.podGrowth[i] = o.getFloat("G");
                podRegrowAt[i] = o.getLong("Wait");      // made relative to now on the first tick
            }
        } else for (int i = 0; i < rig.pods.length; i++) state.podGrowth[i] = state.podPopped[i] ? 0f : 1f;
        entityData.set(DATA_SUNK, tag.getFloat("Sunk"));
        sunkUntil = tag.getLong("SunkFor");
        pendingWaits = true;
        if (tag.contains("HomeX")) home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
        setStay(tag.getBoolean("Stay"));
        hunted = tag.hasUUID("Hunted") ? tag.getUUID("Hunted") : null;
        mood.load(tag);
        partsDirty = true;
        refreshDimensions();
    }

    private boolean pendingWaits;

    @Override
    public void baseTick() {
        super.baseTick();
        if (pendingWaits && !level().isClientSide) {
            pendingWaits = false;
            long now = level().getGameTime();
            for (int i = 0; i < podRegrowAt.length; i++) podRegrowAt[i] += now;
            sunkUntil += now;
        }
    }

    /** for tests and the commands */
    public void setMoveCooldown(int t) { moves.setCooldown(t); }
    public BellMoves moves() { return moves; }

    public static void logOnce(String s) { HollowbellMod.LOG.info(s); }
}
