package net.jj.mountain.entity;

import net.jj.mountain.MountainConfig;
import net.jj.mountain.ModBlocks;
import net.jj.mountain.ModEntities;
import net.jj.mountain.block.GooBlock;
import net.jj.mountain.innards.Innards;
import net.jj.mountain.rig.Gait;
import net.jj.mountain.rig.MountainRig;
import net.jj.mountain.rig.RigState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The Mountain That Breathes. The entity itself is a small anchor on the ground under the middle of his belly;
 * what you can hit is a set of {@link MountainPart} boxes laid along his body and head, and what you see is the
 * real voxel build, drawn bone by bone.
 *
 * He breathes all the time: in (his mouth drags things toward it and swallows what reaches it), hold, then out
 * (a blast that throws you back). Black goo pours out of his mouth wherever he walks. His ~200 eyes are his weak
 * spots; his flesh barely feels a hit. Anything on his back gets passed hand to hand to his mouth. Swallowed players
 * drop into a room inside him where his heart hangs - hurt it enough and he coughs you back up.
 *
 * Two kinds: calm (wanders, only fights once hurt or when you stand in front of his mouth) and hunting (comes for you).
 */
public class MountainEntity extends Monster {
    public static final int CALM = 0, HUNTER = 1, GUARDIAN = 2;

    // ------------------------------------------------------------------ synced state
    private static EntityDataAccessor<Float> f() { return SynchedEntityData.defineId(MountainEntity.class, EntityDataSerializers.FLOAT); }
    private static EntityDataAccessor<Integer> i() { return SynchedEntityData.defineId(MountainEntity.class, EntityDataSerializers.INT); }
    private static EntityDataAccessor<Long> l() { return SynchedEntityData.defineId(MountainEntity.class, EntityDataSerializers.LONG); }
    /** the bends of the tail, back, shoulder and neck pieces (x, y, z, w) as he follows the ground */
    private static final EntityDataAccessor<org.joml.Quaternionf> DATA_BEND = SynchedEntityData.defineId(MountainEntity.class, EntityDataSerializers.QUATERNION);
    /** one bit per broken leg / arm, and how far he is down on his belly */
    private static final EntityDataAccessor<Long> DATA_LEGS_BROKEN = l(), DATA_ARMS_BROKEN0 = l(), DATA_ARMS_BROKEN1 = l();
    private static final EntityDataAccessor<Long> DATA_LEGS_SCARRED = l();
    private static final EntityDataAccessor<Float> DATA_DOWN = f();
    /** the piece that was just hit: bone in the low bits, ticks of jerk left in the high ones */
    private static final EntityDataAccessor<Integer> DATA_FLINCH = i();
    /** the other things his eyes are on, and how many of them */
    private static final EntityDataAccessor<org.joml.Vector3f> DATA_LOOK2 = SynchedEntityData.defineId(MountainEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<org.joml.Vector3f> DATA_LOOK3 = SynchedEntityData.defineId(MountainEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<org.joml.Vector3f> DATA_LOOK4 = SynchedEntityData.defineId(MountainEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> DATA_LOOK_N = i();
    private static final EntityDataAccessor<Float> DATA_SCALE = f(), DATA_HP = f(), DATA_HP_MAX = f();
    private static final EntityDataAccessor<Integer> DATA_VARIANT = i(), DATA_ACT = i(), DATA_HOLD = i(), DATA_EYES_OPEN = i();
    private static final EntityDataAccessor<Float> DATA_WALK = f(), DATA_WALK_AMT = f(), DATA_TURN = f(), DATA_PITCH = f(), DATA_ROLL = f(), DATA_LIFT = f(),
            DATA_BREATH = f(), DATA_MOUTH = f(), DATA_GRIND = f(), DATA_HYAW = f(), DATA_HPITCH = f(),
            DATA_LX = f(), DATA_LY = f(), DATA_LZ = f(), DATA_LAMT = f(), DATA_RX = f(), DATA_RY = f(), DATA_RZ = f(), DATA_RAMT = f(), DATA_HOLD_T = f();
    private static final EntityDataAccessor<Integer> DATA_ATK = i(), DATA_ATK_ARG = i();
    private static final EntityDataAccessor<Float> DATA_AX = f(), DATA_AY = f(), DATA_AZ = f();
    @SuppressWarnings("unchecked")
    private static final EntityDataAccessor<Long>[] DATA_POP = new EntityDataAccessor[]{l(), l(), l(), l()};
    /** how far he has lain down (0..1), how open his eyes are (0..1), and settings the client needs (bit 0: goo on) */
    private static final EntityDataAccessor<Float> DATA_SLEEP = f(), DATA_LIDS = f();
    private static final EntityDataAccessor<Integer> DATA_FLAGS = i();

    /** Set by the client mod: ambient particles and sounds. Never touched on a dedicated server. */
    public static Consumer<MountainEntity> clientTickHook = e -> {};

    /** Set by the client mod: a foot of his coming down, so the ground can shake under you. */
    public static net.jj.mountain.rig.Gait.Landing clientFootHook = null;

    private static final TicketType<Integer> TICKET = TicketType.create("mountain_breathes", Integer::compareTo, 80);
    public static final int DEATH_TICKS = 240;
    /** how long the legs take to go before he comes down */
    public static final int FALL_AT = 150;
    /** breath stages, synced for the effects */
    public static final int REST = 0, INHALE = 1, HOLD = 2, EXHALE = 3, DYING = 4;

    final MountainRig rig = MountainRig.get();
    // server
    final RigState state = new RigState();
    final Matrix4f[] pose = rig.newPoseArray();
    /** his attacks besides breathing and grabbing */
    final MountainAttacks attacks = new MountainAttacks(this);
    private float hp = -1f;
    private final float[] eyeHp = new float[rig.eyes.length];
    private final MountainPart[] parts = new MountainPart[rig.parts.size()];
    private MountainBar bar, eyeBar;
    private float walkPhase, walkAmount, turnAmt, bodyPitch, bodyRoll;
    private final float[] segBend = new float[5];
    // ---- what each leg and arm can take before it breaks
    private final float[] legHp = new float[MountainRig.get().legs.length];
    private final float[] armHp = new float[MountainRig.get().arms.length];
    private long legsBroken, armsBroken0, armsBroken1;
    /**
     * What he keeps of every fight. A limb that has been broken once and knitted back is scarred: it works, but
     * it never takes what it used to, so it goes again sooner. Break a scarred one a second time and it is
     * ruined — that one never comes back at all. Capped, so he cannot scar himself into a heap over a month.
     */
    private long legsScarred, legsRuined, armsScarred0, armsScarred1, armsRuined0, armsRuined1;
    /** true while he is down on his belly for good, so the message only goes out once */
    private boolean wasCrippled;
    /** knocked off his legs: ticks left on the ground, and how far down he is (0..1) */
    private int downTicks;
    private float downAmt;
    /** ticks since a leg or arm was last hurt, for growing them back */
    private int limbCalm;
    /** the piece he is still jerking from, and who hit him last (his head snaps round at them) */
    private int flinchBone = -1, flinchT;
    /** the other things he is watching right now (his eyes share themselves out between them) */
    private final Vector3f[] looks = {new Vector3f(), new Vector3f(), new Vector3f()};
    private int lookCount = 1;
    private final java.util.List<LivingEntity> watchList = new java.util.ArrayList<>();
    private @Nullable LivingEntity hurtBy;
    private int hurtByT;
    public static final int FLINCH_TICKS = 14;
    // how much his trunk follows the ground (dev: -Dmountain.bend="root,max,gain,mid")
    private static final double[] BEND_CFG = java.util.Arrays.stream(System.getProperty("mountain.bend", "0.25,0.15,0.5,0").split(",")).mapToDouble(Double::parseDouble).toArray();
    private static final double BEND_ROOT = BEND_CFG[0], BEND_MAX = BEND_CFG[1], BEND_GAIN = BEND_CFG[2];
    private static final boolean HEIGHT_MID = BEND_CFG[3] > 0.5;
    /** how fast he is turning, degrees a tick; it speeds up and slows down instead of snapping */
    private float yawVel;
    private double groundY = Double.NaN;
    /** where every foot is (server copy; the client runs its own from the same walk clock) */
    private final Gait gait = new Gait(rig);
    private final float[] feetS = new float[rig.legs.length * 3], tipsS = new float[rig.legs.length * 3];
    private float dPhaseS;
    int breathTick, breathPeriod = 230, stage = REST, stageT;
    private boolean strongBreath;
    float breath, mouth, grind, headYaw, headPitch;
    final Vector3f look = new Vector3f(), reach = new Vector3f();
    float lookAmt, reachAmt;
    private int angerTicks, wanderPause = 60;
    private boolean stay;
    private @Nullable Vec3 wanderGoal, goal;
    // held and passed along
    private int holdArm = -1, holdNext = -1;
    private float holdT;
    private @Nullable Entity held;
    private @Nullable GripSeat seat;
    private final Map<Integer, Integer> biteCooldown = new HashMap<>();
    private final Set<UUID> swallowedPlayers = new HashSet<>();
    private float heartDamageThisVisit;
    // client
    private final RigState prevC = new RigState(), curC = new RigState();
    private final Matrix4f[] clientPose = rig.newPoseArray();
    private boolean clientPoseReady;

    public MountainEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.xpReward = 2500;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1024.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 512.0)
                .add(Attributes.ATTACK_DAMAGE, 10.0).add(Attributes.ARMOR, 10.0).add(Attributes.MOVEMENT_SPEED, 0.2);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_SCALE, 1.0f); b.define(DATA_HP, 1f); b.define(DATA_HP_MAX, 1f);
        b.define(DATA_VARIANT, CALM); b.define(DATA_ACT, 0); b.define(DATA_HOLD, 0); b.define(DATA_EYES_OPEN, MountainRig.get().eyes.length);
        for (EntityDataAccessor<Float> a : List.of(DATA_WALK, DATA_WALK_AMT, DATA_TURN, DATA_PITCH, DATA_ROLL, DATA_LIFT, DATA_BREATH, DATA_MOUTH, DATA_GRIND,
                DATA_HYAW, DATA_HPITCH, DATA_LX, DATA_LY, DATA_LZ, DATA_LAMT, DATA_RX, DATA_RY, DATA_RZ, DATA_RAMT, DATA_HOLD_T)) b.define(a, 0f);
        for (EntityDataAccessor<Long> a : DATA_POP) b.define(a, 0L);
        b.define(DATA_ATK, 0); b.define(DATA_ATK_ARG, -1); b.define(DATA_AX, 0f); b.define(DATA_AY, 0f); b.define(DATA_AZ, 0f);
        b.define(DATA_SLEEP, 0f); b.define(DATA_LIDS, 1f); b.define(DATA_FLAGS, 1);
        b.define(DATA_BEND, new org.joml.Quaternionf(0f, 0f, 0f, 0f));
        b.define(DATA_LEGS_BROKEN, 0L); b.define(DATA_ARMS_BROKEN0, 0L); b.define(DATA_ARMS_BROKEN1, 0L); b.define(DATA_DOWN, 0f);
        b.define(DATA_LEGS_SCARRED, 0L);
        b.define(DATA_FLINCH, 0xffff);
        b.define(DATA_LOOK2, new org.joml.Vector3f()); b.define(DATA_LOOK3, new org.joml.Vector3f());
        b.define(DATA_LOOK4, new org.joml.Vector3f()); b.define(DATA_LOOK_N, 1);
    }

    /** goo is switched on (the client uses this to leave out the dripping and the streams too) */
    public boolean gooOn() { return (entityData.get(DATA_FLAGS) & 1) != 0; }

    // ------------------------------------------------------------------ sleeping
    // Asleep he lies flat: belly on the ground, legs sprawled, tail sagging to the ground like a ramp, arms limp,
    // eyes shut, breathing slow. Someone on him or close to him wakes him: a hunter within a few seconds, the calm
    // and guardian ones only after 30 to 60 seconds. Waking, his eyes open first and find you; then he gets up.
    private boolean asleep, sleepMode;
    private float sleepAmt, lids = 1f;
    private int disturbed, wakeAfter, waking = -1, aloneTicks;
    private boolean rudeWake;
    private @Nullable UUID waker;

    /** lying down or getting up (his normal life is on hold) */
    public boolean sleeping() { return asleep || waking >= 0 || sleepAmt > 0f; }
    /** asleep and staying that way for now */
    public boolean isAsleep() { return asleep; }
    /** 0..1, synced: how far down he is */
    public float sleepAmount() { return entityData.get(DATA_SLEEP); }

    /** /mountain sleep: lie down now (and lie down again whenever he's left alone) */
    public void goToSleep() {
        if (isDeadOrDying() || crippled()) return;       // he is not resting, he is waiting for you
        asleep = true; sleepMode = true; waking = -1; disturbed = 0; aloneTicks = 0;
        attacks.stop(); setTarget(null); angerTicks = 0; playerAnger = 0; goal = null; wanderGoal = null;
        calledBy = null;
        if (held != null) releaseHeld(false);
        if (rider != null) setMeDown();                  // he doesn't lie down on top of whoever is riding him
        else stopFetch();
    }

    /** summoned asleep: already lying there with his eyes shut */
    public void startAsleep() { goToSleep(); sleepAmt = 1f; lids = 0f; lookAmt = 0f; }

    /** /mountain sleep again: get up now and stay up */
    public void wakeUp(@Nullable Player by) { sleepMode = false; if (asleep || sleepAmt > 0f) startWaking(by, false); }

    private void startWaking(@Nullable Player by, boolean rude) {
        if (!asleep && waking >= 0) { rudeWake |= rude; return; }
        asleep = false; waking = 0; disturbed = 0; rudeWake = rude; aloneTicks = 0;
        waker = by == null ? null : by.getUUID();
        sound(toWorld(rig.headCenter, pose[rig.head]), net.jj.mountain.ModSounds.AMBIENT, 2.0f, 0.8f);
    }

    /** a player on his back or right next to him (creative and spectators don't count) */
    private @Nullable Player disturber() {
        float s = mountainScale();
        AABB bb = backBounds();
        if (bb == null) return null;
        AABB near = bb.inflate(8 + 18 * s, 4 + 10 * s, 8 + 18 * s);
        Player best = null; double bd = Double.MAX_VALUE;
        for (Player p : level().players()) {
            if (p.isCreative() || p.isSpectator() || !p.isAlive() || Innards.isInside(p) || !near.contains(p.position())) continue;
            double d = p.distanceToSqr(this);
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    /** one tick asleep, or lying down, or getting up */
    private void sleepStep() {
        float s = mountainScale();
        attacks.stop();
        if (held != null) releaseHeld(false);
        reachAmt = Math.max(0f, reachAmt - 0.05f);
        walkAmount = Math.max(0f, walkAmount - 0.03f); yawVel *= 0.8f; turnAmt *= 0.9f;
        // the walk clock only runs while some foot still has to be put down in its new place
        float idle = 1f / (40f + 60f * s);
        dPhaseS = gait.restless(s) ? idle : 0f;
        walkPhase += dPhaseS;
        if (walkPhase > 1000f) walkPhase -= 1000f;
        // slow, deep, quiet breaths with his mouth shut
        stage = REST; stageT = 0; strongBreath = false; breathTick = 0;
        breath += (0.35f + 0.3f * Mth.sin(tickCount * (Mth.TWO_PI / 170f)) - breath) * 0.1f;
        mouth += (0f - mouth) * 0.1f; grind *= 0.7f;
        Player near = disturber();
        if (asleep) {
            sleepAmt = Math.min(1f, sleepAmt + 1f / 110f);
            lids = Math.max(0f, lids - 1f / 40f);
            lookAmt = Math.max(0f, lookAmt - 0.03f);
            headYaw *= 0.97f; headPitch *= 0.97f;
            if (sleepAmt >= 1f && tickCount % 170 == 40) sound(mouthWorld(), net.jj.mountain.ModSounds.BREATH_IN, 0.7f, 0.55f);
            if (near != null && sleepAmt > 0.6f) {
                if (disturbed == 0) wakeAfter = isHunter() ? 50 + random.nextInt(50) : 600 + random.nextInt(601);
                if (++disturbed >= wakeAfter) startWaking(near, false);
            } else if (disturbed > 0) disturbed = Math.max(0, disturbed - 2);
            return;
        }
        // getting up: first the eyes open and find you, then he rises
        waking++;
        Player p = waker != null && level().getPlayerByUUID(waker) instanceof Player w && w.isAlive() && !w.isSpectator() ? w : near;
        boolean fast = isHunter() || rudeWake;
        int openT = fast ? 24 : 55;
        lids = Math.min(1f, waking / (float) openT);
        if (p != null) {
            Vec3 mp = worldToModelPoint(p.getEyePosition());
            Vector3f to = new Vector3f((float) mp.x, (float) mp.y, (float) mp.z);
            if (lookAmt < 0.02f) look.set(to);
            look.lerp(to, 0.3f);
            lookAmt = Math.min(1f, lookAmt + 0.06f);
            if (waking > openT) {
                Vector3f base = rig.pivot[rig.head];
                float wantYaw = Mth.clamp((float) Math.atan2(-(mp.x - base.x), -(mp.z - base.z)), -0.45f, 0.45f);
                headYaw += Mth.clamp(wantYaw - headYaw, -0.02f, 0.02f);
            }
        }
        if (waking == openT) sound(toWorld(rig.headCenter, pose[rig.head]), net.jj.mountain.ModSounds.ROAR, 2.5f, isHunter() ? 0.85f : 0.65f);
        if (waking > openT + 10) sleepAmt = Math.max(0f, sleepAmt - 1f / (fast ? 45f : 90f));
        if (sleepAmt <= 0f && waking > openT + 10) {
            waking = -1; lids = 1f; aloneTicks = 0;
            if (p != null && (isHunter() || rudeWake) && !spares(p)) { setTarget(p); angerTicks = Math.max(angerTicks, 600); }
            rudeWake = false; waker = null;
        }
    }

    /** awake in sleep mode: after a couple of minutes with nobody about and nothing to do, he lies back down */
    private void maybeDoze() {
        if (!sleepMode || isDeadOrDying()) return;
        boolean idle = getTarget() == null && angerTicks <= 0 && playerAnger <= 0 && attacks.id == RigState.NONE && held == null && disturber() == null;
        aloneTicks = idle ? aloneTicks + 1 : 0;
        // he is a night thing: in daylight he beds down in minutes, in the dark he stays up for a long time
        boolean day = MountainConfig.V.sleepsByDay && level().isDay() && !stormy() && level().canSeeSky(blockPosition());
        int wait = day ? 600 : 6000;
        if (aloneTicks > wait) { asleep = true; aloneTicks = 0; disturbed = 0; }
        // and night gets him up again of his own accord
        if (asleep && !day && waking < 0 && level().random.nextInt(400) == 0 && !level().isDay()) startWaking(null, false);
    }

    // ------------------------------------------------------------------ size, kind and health
    public float mountainScale() { return this.entityData.get(DATA_SCALE); }
    public int variant() { return this.entityData.get(DATA_VARIANT); }
    public boolean isHunter() { return variant() == HUNTER; }
    /** the guardian goes after monsters and leaves players (and their animals) alone until a player hurts him */
    public boolean isGuardian() { return variant() == GUARDIAN; }
    public void setVariant(int v) {
        this.entityData.set(DATA_VARIANT, v == HUNTER ? HUNTER : v == GUARDIAN ? GUARDIAN : CALM);
        if (!level().isClientSide) { playerAnger = 0; if (v != HUNTER) { angerTicks = 0; setTarget(null); } }
    }

    /** ticks left of being angry at players (only the guardian keeps this apart from his other anger) */
    private int playerAnger;

    // ------------------------------------------------------------------ how far gone he is
    /** 0 while he is over half health, 1 once he is under half, 2 once he is under a quarter */
    public int phase() {
        float f = healthNow() / Math.max(1f, healthMax());
        return f <= 0.25f ? 2 : f <= 0.5f ? 1 : 0;
    }
    /** how much faster and harder he goes in each phase */
    public float phaseSpeed() {
        float f = phase() == 2 ? 1.34f : phase() == 1 ? 1.16f : 1f;
        return stormy() ? f * 1.12f : f;
    }
    private int phaseSeen;
    /** ticks left of the roar he lets out when he turns */
    private int phaseRoar;
    public int phaseRoarTicks() { return phaseRoar; }

    /** he crosses half, then a quarter: he stops, roars, and comes back worse */
    private void enterPhase(int p) {
        phaseSeen = p;
        phaseRoar = 50;
        angerTicks = Math.max(angerTicks, 2400);
        if (isGuardian()) playerAnger = Math.max(playerAnger, 2400);
        attacks.stop();
        Vec3 head = toWorld(rig.headCenter, pose[rig.head]);
        sound(head, net.jj.mountain.ModSounds.RAGE, 4f, p == 2 ? 0.95f : 0.8f);
        sendParticles(net.minecraft.core.particles.ParticleTypes.SQUID_INK, head, 120, 8 * mountainScale() + 2, 8 * mountainScale() + 2, 0.4);
        Component msg = Component.translatable(p == 2 ? "message.mountain_breathes.phase2" : "message.mountain_breathes.phase1");
        for (Player pl : level().players())
            if (pl.distanceToSqr(this) < sq(400 * mountainScale() + 120)) pl.displayClientMessage(msg, true);
    }

    /** true when this thing should come to no harm from him: a guardian spares everything but monsters until provoked */
    private net.jj.mountain.world.@Nullable MountainWorld friendCache;
    private net.jj.mountain.world.@Nullable MountainWorld friendList() {   // always the overworld's copy: that is where the lists live
        if (friendCache == null && level() instanceof ServerLevel sl)
            friendCache = net.jj.mountain.world.MountainWorld.get(sl.getServer().overworld());
        return friendCache;
    }

    /**
     * He does not go for his own kind. Two of them standing close enough to tread on each other would shove, one
     * would take the shove as a hit, and from then on the pair of them were locked on each other and no use to
     * anybody. Nothing sets another Mountain as his target, wherever it comes from.
     */
    @Override
    public void setTarget(@Nullable LivingEntity t) {
        if (t instanceof MountainEntity) return;
        super.setTarget(t);
    }

    public boolean spares(@Nullable Entity e) {
        // whoever asked one of them to finish it. Every Mountain knows them and nothing covers them.
        if (e instanceof Player && friendList() != null && friendList().marked(e.getUUID())) return false;
        if (e instanceof Player && mood.hunting(e.getUUID())) return false;
        if (e instanceof Player && mood.turnedOn(e.getUUID())) return false; // he has stopped reading your book
        if (e != null && (e == rider || e == fetch)) return true;  // never the one he is carrying or coming for
        if (e != null && onHitList(e)) return false;              // /mountain attack overrules everything
        if (net.jj.mountain.item.MountainCodexItem.heldBy(e)) return true;   // whoever carries the book owns him
        if (e != null && friendList() != null) {
            if (e instanceof Player pf ? friendList().friendly(pf.getUUID()) : friendList().friendlyKind(e.getType())) return true;
        }
        if (holdsGrudge(e)) return false;                          // and he never spares someone who has hurt him before
        if (!isGuardian() || playerAnger > 0 || e == null) return false;
        if (e instanceof net.jj.mountain.entity.inside.InsideMob) return true;
        return !(e instanceof net.minecraft.world.entity.monster.Enemy) || e instanceof Player;
    }
    public float healthNow() { return this.entityData.get(DATA_HP); }
    public float healthMax() { return this.entityData.get(DATA_HP_MAX); }
    public int eyesOpen() { return this.entityData.get(DATA_EYES_OPEN); }

    private static float sizeFactor(float s) { return (float) Math.pow(Math.max(0.03f, s), 0.8); }

    public void setMountainScale(float s) {
        s = Mth.clamp(s, 0.02f, 2.0f);
        this.entityData.set(DATA_SCALE, s);
        float max = Math.max(60f, Math.round(MountainConfig.V.health * sizeFactor(s)));
        this.entityData.set(DATA_HP_MAX, max);
        hp = max;
        for (int e = 0; e < eyeHp.length; e++) eyeHp[e] = Math.max(3f, rig.eyes[e].radius * 11f * sizeFactor(s));
        legsBroken = armsBroken0 = armsBroken1 = 0L;
        legsScarred = legsRuined = armsScarred0 = armsScarred1 = armsRuined0 = armsRuined1 = 0L;
        wasCrippled = false;
        for (int k = 0; k < legHp.length; k++) legHp[k] = legHpMax(k);
        for (int k = 0; k < armHp.length; k++) armHp[k] = armHpMax(k);
        syncLimbs();
        for (int e = 0; e < rig.eyes.length; e++) state.setPopped(e, false);
        syncHealth();
        this.refreshDimensions();
        for (MountainPart p : parts) if (p != null) p.setOwnerScale(s);
    }

    private void syncHealth() {
        float max = healthMax();
        this.entityData.set(DATA_HP, Math.max(0f, hp));
        int open = 0;
        for (int e = 0; e < rig.eyes.length; e++) if (!state.isPopped(e)) open++;
        this.entityData.set(DATA_EYES_OPEN, open);
        if (!this.isDeadOrDying()) this.setHealth(Math.max(1f, this.getMaxHealth() * Math.max(0f, hp) / max));
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        float s = mountainScale();
        return EntityDimensions.fixed(Math.max(1.0f, 3f * s), Math.max(1.0f, 3f * s));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SCALE.equals(key)) this.refreshDimensions();
    }

    // ------------------------------------------------------------------ vanilla behaviour he does not have
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public void push(Entity e) {}
    @Override protected void pushEntities() {}
    @Override public void knockback(double d, double e, double f) {}
    @Override public boolean canBeLeashed() { return false; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean canChangeDimensions(Level a, Level b) { return false; }
    @Override public boolean canFreeze() { return false; }
    @Override public boolean isAffectedByPotions() { return false; }
    @Override protected boolean isAlwaysExperienceDropper() { return true; }
    @Override public boolean isInvulnerableTo(DamageSource src) { return super.isInvulnerableTo(src) || isImmuneTo(src); }
    static boolean isImmuneTo(DamageSource src) {
        return src.is(DamageTypeTags.IS_FALL) || src.is(DamageTypeTags.IS_DROWNING) || src.is(DamageTypeTags.IS_FREEZING)
                || src.is(DamageTypes.IN_WALL) || src.is(DamageTypes.CRAMMING) || src.is(DamageTypes.SWEET_BERRY_BUSH) || src.is(DamageTypes.CACTUS)
                || src.is(DamageTypes.FLY_INTO_WALL) || src.is(DamageTypes.IN_FIRE) || src.is(DamageTypes.LAVA) || src.is(DamageTypes.ON_FIRE)
                || src.is(DamageTypes.HOT_FLOOR) || src.is(DamageTypes.MAGIC);
    }

    @Override public AABB getBoundingBoxForCulling() {
        float s = mountainScale();
        Vector3f lo = rig.extentMin, hi = rig.extentMax;
        double reach = Math.sqrt(Math.max(lo.x * lo.x, hi.x * hi.x) + Math.max(lo.z * lo.z, hi.z * hi.z));
        double r = (reach + 12) * s + 6, top = (hi.y + 20) * s + 6;
        return new AABB(getX() - r, getY() - 220 * s - 2, getZ() - r, getX() + r, getY() + top, getZ() + r);
    }
    @Override public boolean shouldRenderAtSqrDistance(double d) {
        double r = MountainConfig.V.renderDistance * Math.max(0.3, Math.min(1.0, mountainScale() * 1.5)) * getViewScale();
        return d < r * r;
    }

    // ------------------------------------------------------------------ sounds
    @Override protected SoundEvent getAmbientSound() { return net.jj.mountain.ModSounds.AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource src) { return net.jj.mountain.ModSounds.HURT; }
    @Override protected SoundEvent getDeathSound() { return net.jj.mountain.ModSounds.DEATH; }
    @Override public int getAmbientSoundInterval() { return 320; }
    @Override protected float getSoundVolume() { return 2.0f + 6.0f * mountainScale(); }
    @Override public float getVoicePitch() { return 0.3f + 0.5f * (1f - Math.min(1f, mountainScale())) + (this.random.nextFloat() - 0.5f) * 0.06f; }
    public void sound(Vec3 at, SoundEvent ev, float vol, float pitch) {
        if (net.jj.mountain.ModSounds.silent()) return;
        level().playSound(null, at.x, at.y, at.z, ev, SoundSource.HOSTILE,
                net.jj.mountain.ModSounds.vol(vol) * (1.0f + 5.0f * mountainScale()), pitch);
    }

    /** the noises the game itself plays for him (ambient, hurt, death) are turned down the same way */
    @Override public void playSound(SoundEvent ev, float vol, float pitch) {
        if (net.jj.mountain.ModSounds.silent()) return;
        super.playSound(ev, net.jj.mountain.ModSounds.vol(vol), pitch);
    }

    // ------------------------------------------------------------------ spawn / save
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (!grudges.isEmpty()) {
            ListTag g = new ListTag();
            for (UUID u : grudges) g.add(net.minecraft.nbt.NbtUtils.createUUID(u));
            tag.put("Grudges", g);
        }
        if (worldOne) tag.putBoolean("WorldOne", true);
        if (pieceLeft >= 0) tag.putInt("PieceLeft", pieceLeft);
        if (bornAt >= 0) tag.putLong("BornAt", bornAt);
        tag.putLong("ForcedUnder", forcedUnderAt);
        tag.putFloat("MountainScale", mountainScale());
        tag.putInt("MountainVariant", variant());
        tag.putFloat("MountainHealth", hp);
        tag.putLong("LegsBroken", legsBroken); tag.putLong("ArmsBroken0", armsBroken0); tag.putLong("ArmsBroken1", armsBroken1);
        tag.putLong("LegsScarred", legsScarred); tag.putLong("LegsRuined", legsRuined);
        tag.putLong("ArmsScarred0", armsScarred0); tag.putLong("ArmsScarred1", armsScarred1);
        tag.putLong("ArmsRuined0", armsRuined0); tag.putLong("ArmsRuined1", armsRuined1);
        ListTag eh = new ListTag();
        for (float v : eyeHp) eh.add(FloatTag.valueOf(v));
        tag.put("EyeHealth", eh);
        tag.putLongArray("EyesPopped", state.popped);
        tag.putBoolean("Stay", fetchState >= 2 ? stayWas : stay);
        if (goal != null) { tag.putDouble("GoalX", goal.x); tag.putDouble("GoalY", goal.y); tag.putDouble("GoalZ", goal.z); }
        if (digState != 0) {
            tag.putInt("DigState", digState);
            tag.putInt("DigT", digT);
            tag.putFloat("DigAmt", digAmt);
            if (digTo != null) { tag.putDouble("DigToX", digTo.x); tag.putDouble("DigToY", digTo.y); tag.putDouble("DigToZ", digTo.z); }
        }
        tag.putBoolean("Asleep", asleep || (waking >= 0 && sleepAmt > 0.5f));
        tag.putBoolean("SleepMode", sleepMode);
        if (unmaking()) {                                  // he must not be left standing up with nothing to come down on
            tag.putInt("RiseT", attacks.t);
            if (breaker != null) tag.put("Breaker", net.minecraft.nbt.NbtUtils.createUUID(breaker));
        }
        mood.save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("MountainVariant")) setVariant(tag.getInt("MountainVariant"));
        setMountainScale(tag.contains("MountainScale") ? tag.getFloat("MountainScale") : MountainConfig.V.spawnEggScale);
        if (tag.contains("MountainHealth")) hp = tag.getFloat("MountainHealth");
        if (tag.contains("GoalX")) goal = new Vec3(tag.getDouble("GoalX"), tag.getDouble("GoalY"), tag.getDouble("GoalZ"));
        if (tag.contains("DigState")) {                       // a run under the ground carries on where it left off
            digState = tag.getInt("DigState");
            digT = tag.getInt("DigT");
            digAmt = tag.getFloat("DigAmt");
            if (tag.contains("DigToX")) digTo = new Vec3(tag.getDouble("DigToX"), tag.getDouble("DigToY"), tag.getDouble("DigToZ"));
        }
        legsBroken = tag.getLong("LegsBroken"); armsBroken0 = tag.getLong("ArmsBroken0"); armsBroken1 = tag.getLong("ArmsBroken1");
        legsScarred = tag.getLong("LegsScarred"); legsRuined = tag.getLong("LegsRuined");
        armsScarred0 = tag.getLong("ArmsScarred0"); armsScarred1 = tag.getLong("ArmsScarred1");
        armsRuined0 = tag.getLong("ArmsRuined0"); armsRuined1 = tag.getLong("ArmsRuined1");
        legsBroken |= legsRuined; armsBroken0 |= armsRuined0; armsBroken1 |= armsRuined1;
        for (int k = 0; k < legHp.length; k++) legHp[k] = legBroken(k) ? 0f : Math.min(legHp[k] <= 0f ? legHpMax(k) : legHp[k], legHpMax(k));
        for (int k = 0; k < armHp.length; k++) armHp[k] = armBroken(k) ? 0f : Math.min(armHp[k] <= 0f ? armHpMax(k) : armHp[k], armHpMax(k));
        wasCrippled = crippled();
        syncLimbs();
        if (tag.contains("EyeHealth", Tag.TAG_LIST)) {
            ListTag eh = tag.getList("EyeHealth", Tag.TAG_FLOAT);
            for (int e = 0; e < eyeHp.length && e < eh.size(); e++) eyeHp[e] = eh.getFloat(e);
        }
        stay = tag.getBoolean("Stay");
        sleepMode = tag.getBoolean("SleepMode");
        if (tag.getBoolean("Asleep")) { asleep = true; sleepAmt = 1f; lids = 0f; waking = -1; }
        if (tag.contains("EyesPopped")) {
            long[] p = tag.getLongArray("EyesPopped");
            for (int q = 0; q < 4 && q < p.length; q++) state.popped[q] = p[q];
        }
        worldOne = tag.getBoolean("WorldOne");
        pieceLeft = tag.contains("PieceLeft") ? tag.getInt("PieceLeft") : -1;
        bornAt = tag.contains("BornAt") ? tag.getLong("BornAt") : -1;
        if (tag.contains("ForcedUnder")) forcedUnderAt = tag.getLong("ForcedUnder");
        grudges.clear();
        if (tag.contains("Grudges", Tag.TAG_LIST)) {
            ListTag g = tag.getList("Grudges", Tag.TAG_INT_ARRAY);
            for (int i = 0; i < g.size(); i++) grudges.add(net.minecraft.nbt.NbtUtils.loadUUID(g.get(i)));
        }
        mood.load(tag);
        if (tag.contains("RiseT")) {
            riseBack = Math.max(0, tag.getInt("RiseT"));
            if (tag.contains("Breaker")) breaker = net.minecraft.nbt.NbtUtils.loadUUID(tag.get("Breaker"));
        }
        syncHealth();
        resetPhaseMark();
    }

    private void ensureInit() {
        if (!level().isClientSide && hp < 0) setMountainScale(mountainScale());
    }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor lvl, net.minecraft.world.DifficultyInstance diff,
                                                                   net.minecraft.world.entity.MobSpawnType why, @Nullable net.minecraft.world.entity.SpawnGroupData data) {
        if (hp < 0 || why == net.minecraft.world.entity.MobSpawnType.SPAWN_EGG) setMountainScale(why == net.minecraft.world.entity.MobSpawnType.SPAWN_EGG ? MountainConfig.V.spawnEggScale : mountainScale());
        return super.finalizeSpawn(lvl, diff, why, data);
    }

    // ------------------------------------------------------------------ main loop
    @Override
    public void tick() {
        ensureInit();
        this.setDeltaMovement(Vec3.ZERO);
        super.tick();
        if (this.isRemoved()) return;
        this.setYBodyRot(this.getYRot());
        this.setYHeadRot(this.getYRot());
        if (level().isClientSide) { clientTick(); return; }
        tripCatchUp();
        resumeRise();
        updateServerState();
        rig.computePose(state, pose, null);
        ensureParts();
        placeParts();
        updateBack();
        carryRiders();
        carryHeld();
        // these run here rather than with the rest of his thinking: asleep, knocked down or dying he stops
        // thinking, and somebody he is carrying must still be able to get off
        riderTick();
        fetchTick();
        calledTick();
        carryRider();
        writeSyncedState();
        updateBars();
        keepChunksLoaded();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        entityData.set(DATA_FLAGS, MountainConfig.V.gooTrail ? 1 : 0);
        mood.tick();
        if (sleeping()) {
            sleepStep();
            limbTick();
            footsteps();
            return;
        }
        maybeDoze();
        limbTick();
        pieceTick();
        reportIn();
        digTick();
        shedLumps();
        scatterTheWorld();
        scaleToTheFight();
        if (phaseRoar > 0) phaseRoar--;
        if (noGrab > 0) noGrab--;
        hunt();
        if (held instanceof ServerPlayer sp && tickCount % 20 == 0 && struggle < struggleNeeded())
            sp.displayClientMessage(Component.translatable("message.mountain_breathes.struggle_hint"), true);
        LivingEntity target = updateTarget();
        if (!attacks.usesMouth()) breathe(target);
        else { stage = REST; stageT = 0; breath += (0f - breath) * 0.12f; grind *= 0.7f; }
        move(target);
        lookAround(target);
        arms();
        attacks.tick(target);
        if (MountainConfig.V.gooTrail && this.tickCount % 2 == 0) pourGoo();
        footsteps();
        if (this.tickCount % 40 == 0 && !level().players().isEmpty()) {
            Vec3 c = toWorld(rig.headCenter, pose[rig.head]);
            for (Player p : level().players()) if (p.distanceToSqr(c) < sq(70 * mountainScale() + 24)) {
                sound(c, net.jj.mountain.ModSounds.HEART, 1.6f, 0.8f);
                break;
            }
        }
    }

    private static double sq(double v) { return v * v; }

    // ------------------------------------------------------------------ who he is after
    public boolean isAngry() { return isHunter() || angerTicks > 0 || (isGuardian() && getTarget() != null); }

    private @Nullable LivingEntity updateTarget() {
        float s = mountainScale();
        if (angerTicks > 0) angerTicks--;
        if (playerAnger > 0) playerAnger--;
        double range = 60 + 220 * s;
        LivingEntity t = getTarget();
        boolean sent = onHitList(t);
        if (t != null && (!t.isAlive() || t.isRemoved() || (t instanceof Player p && (p.isCreative() || p.isSpectator()))
                || t.level() != level() || (!sent && (t.distanceToSqr(this) > sq(range * 1.6) || !isAngry()))
                || Innards.isInside(t) || spares(t))) {
            t = null; setTarget(null);
        }
        if (!hitList.isEmpty() && (t == null || !sent || this.tickCount % 20 == 0)) {
            LivingEntity sendTo = fromHitList();
            if (sendTo != null) {
                angerTicks = Math.max(angerTicks, 200);
                if (sendTo instanceof Player) playerAnger = Math.max(playerAnger, 200);
                if (t == null || !sent) { setTarget(sendTo); t = sendTo; }
            }
        }
        if (t == null && isGuardian() && this.tickCount % 10 == 5) {
            // the nearest monster in reach (not the things he calls up himself)
            LivingEntity best = null; double bd = range * range;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range, range * 0.5, range),
                    x -> x instanceof net.minecraft.world.entity.monster.Enemy && x.isAlive() && !(x instanceof MountainEntity)
                            && !(x instanceof net.jj.mountain.entity.inside.InsideMob) && !Innards.isInside(x) && !spares(x))) {
                double d = e.distanceToSqr(this);
                if (d < bd) { bd = d; best = e; }
            }
            if (best != null) setTarget(best);
        }
        // the marked: calm, guardian or hunter, he goes for them the moment he can see them
        if (t == null && this.tickCount % 10 == 3 && friendList() != null && friendList().markedCount() > 0) {
            Player best = null; double bd = range * range;
            for (Player p : level().players()) {
                if (p.isCreative() || p.isSpectator() || !p.isAlive() || Innards.isInside(p)) continue;
                if (!friendList().marked(p.getUUID())) continue;
                double d = p.distanceToSqr(this);
                if (d < bd) { bd = d; best = p; }
            }
            if (best != null) {
                if (greeted.add(best.getUUID()))
                    sound(toWorld(rig.headCenter, pose[rig.head]), net.jj.mountain.ModSounds.RAGE, 4f, 0.7f);
                setTarget(best);
                angerTicks = Math.max(angerTicks, 2400);
                playerAnger = Math.max(playerAnger, 2400);
                t = best;
            }
        }
        // down for good: he cannot come to you, so anything that comes to him will do, whatever he is
        if (t == null && crippled() && this.tickCount % 5 == 0) {
            LivingEntity best = null; double bd = range * range;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range, range * 0.6, range),
                    x -> x.isAlive() && !(x instanceof MountainEntity) && !(x instanceof net.jj.mountain.entity.inside.InsideMob)
                            && !Innards.isInside(x) && !spares(x))) {
                if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
                double d = e.distanceToSqr(this);
                if (d < bd) { bd = d; best = e; }
            }
            if (best != null) {
                setTarget(best); t = best;
                angerTicks = Math.max(angerTicks, 400);
                if (best instanceof Player) playerAnger = Math.max(playerAnger, 400);
            }
        }
        if (t == null && isHunter() && this.tickCount % 10 == 0) {
            Player best = null; double bd = range * range;
            for (Player p : level().players()) {
                if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
                if (spares(p)) continue;                  // on his list: he does not go after them at all
                double d = p.distanceToSqr(this);
                if (d < bd) { bd = d; best = p; }
            }
            if (best != null) { setTarget(best); sound(position(), net.jj.mountain.ModSounds.ROAR, 2.0f, 0.9f); }
        }
        return getTarget();
    }

    // ------------------------------------------------------------------ pieces of him getting up
    private int lumpCool = 400;
    private int lumpsOut;

    /**
     * Torn open and past caring, he starts shedding. Lumps of his own flesh drop off him, pick themselves up and
     * go after whatever he is after. He only does it once there is not much of him left.
     */
    private void shedLumps() {
        if (level().isClientSide || isDeadOrDying() || sleeping() || burrowed()) return;
        if (!MountainConfig.V.shedsLumps || phase() < 2 || !isAngry() || getTarget() == null) return;
        if (--lumpCool > 0) return;
        lumpCool = 160 + random.nextInt(200);
        if (lumpsOut >= 6) { lumpsOut = countLumps(); if (lumpsOut >= 6) return; }
        float s = mountainScale();
        int n = 1 + random.nextInt(3);
        for (int i = 0; i < n; i++) {
            MountainRig.BodySample b = rig.body.get(random.nextInt(rig.body.size()));
            Vec3 from = toWorld(b.p(), pose[b.bone()]).add((random.nextDouble() - 0.5) * 6 * s, 0, (random.nextDouble() - 0.5) * 6 * s);
            net.jj.mountain.entity.inside.GutLeech lump = net.jj.mountain.ModEntities.GUT_LEECH.create(level());
            if (lump == null) continue;
            lump.moveTo(from.x, from.y, from.z, random.nextFloat() * 360f, 0f);
            lump.setTarget(getTarget());
            level().addFreshEntity(lump);
            lumpsOut++;
            sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PINK_TERRACOTTA.defaultBlockState()), from, 30, 2 * s + 0.8, 1.2, 0.2);
        }
        sound(toWorld(rig.headCenter, pose[rig.head]), net.jj.mountain.ModSounds.GOO, 2.5f, 0.6f);
        sound(position(), net.jj.mountain.ModSounds.ROAR, 2.5f, 1.15f);
    }

    private int countLumps() {
        double r = 160 * mountainScale() + 60;
        return level().getEntitiesOfClass(net.jj.mountain.entity.inside.GutLeech.class, getBoundingBoxForCulling().inflate(r)).size();
    }

    // ------------------------------------------------------------------ going under
    /** 0 nothing, 1 sinking, 2 travelling under, 3 coming up */
    private int digState;
    private int digT, digCool = 600;
    private float digAmt;
    private @Nullable Vec3 digTo;

    public boolean burrowed() { return level().isClientSide ? curC.burrow > 0.55f : digAmt > 0.55f; }
    public float burrowAmount() { return level().isClientSide ? curC.burrow : digAmt; }
    public boolean digging() { return digState != 0; }

    /** send him under, to come up at a spot */
    /** the last time somebody made him go under, so it can't be asked for again for three days */
    private long forcedUnderAt = Long.MIN_VALUE / 4;
    /** how long he puts up with being sent under, in ticks (settings: burrowGapDays) */
    public static long forcedUnderGap() { return Math.max(0, MountainConfig.V.burrowGapDays) * 24000L; }

    /** ticks before he can be sent under again, or 0 */
    public long forcedUnderLeft() {
        long left = forcedUnderGap() - (level().getGameTime() - forcedUnderAt);
        return Math.max(0, left);
    }

    /** somebody telling him to go under, which he only stands for every few days */
    public boolean goUnderOnCommand(Vec3 to) {
        if (!MountainConfig.V.canBurrow || forcedUnderLeft() > 0) return false;
        if (!goUnder(to)) return false;
        forcedUnderAt = level().getGameTime();
        return true;
    }

    public boolean goUnder(Vec3 to) {
        if (level().isClientSide || isDeadOrDying() || sleeping() || downTicks > 0 || digState != 0 || ridden()) return false;
        digTo = to; digState = 1; digT = 0;
        attacks.stop();
        releaseHeld(true);
        Vec3 c = position();
        sound(c, net.jj.mountain.ModSounds.ROAR, 4f, 0.6f);
        sound(c, SoundEvents.WARDEN_DIG, 4f, 0.4f);
        return true;
    }

    /**
     * He gets tired of walking. He folds down into the ground, runs under it as a ridge of broken earth, and
     * comes up wherever he was going. Nothing can reach him while he is under and he can't reach anything either.
     */
    private void digTick() {
        if (digCool > 0) digCool--;
        // he never takes himself under the ground any more: the book is the only thing that puts him under
        if (digState == 0) return;
        digT++;
        float s = mountainScale();
        Vec3 c = position();
        if (digState == 1) {                                        // going down
            digAmt = Math.min(1f, digAmt + 0.018f);
            if (digT % 3 == 0) {
                sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level().getBlockState(blockPosition().below()).isAir()
                        ? Blocks.DIRT.defaultBlockState() : level().getBlockState(blockPosition().below())), c, 120, 40 * s + 6, 2, 0.3);
            }
            if (digT % 16 == 0) sound(c, SoundEvents.WARDEN_DIG, 3f, 0.45f);
            if (digAmt >= 1f) { digState = 2; digT = 0; }
            return;
        }
        if (digState == 2) {                                        // running under the ground
            if (digTo == null) { digState = 3; digT = 0; return; }
            double dx = digTo.x - getX(), dz = digTo.z - getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            double step = (0.9 + 2.2 * s) * 3.0;
            if (d <= step || digT > 1200) { digState = 3; digT = 0; return; }
            double nx = getX() + dx / d * step, nz = getZ() + dz / d * step;
            setYRot((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f);
            setYBodyRot(getYRot()); setYHeadRot(getYRot());
            // never force new ground into being just to run under it: where the world isn't there yet, hold his height
            BlockPos np = BlockPos.containing(nx, getY(), nz);
            double gy = level().hasChunkAt(np) ? groundAt(np.getX(), np.getZ()) : getY();
            setPos(nx, gy, nz);
            Vec3 top = new Vec3(nx, gy + 1, nz);
            if (digT % 2 == 0)
                sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()), top, 40, 12 * s + 3, 1.0, 0.35);
            if (digT % 8 == 0) sound(top, SoundEvents.ROOTED_DIRT_BREAK, 3f, 0.4f);
            if (digT % 4 == 0) {
                double rr = 10 + 22 * s;
                for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(top, top).inflate(rr))) {
                    if (e == this || e instanceof MountainEntity || e instanceof HeartEntity || spares(e)) continue;
                    if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
                    e.hurt(damageSources().mobAttack(this), dmg(2 + 4 * s, e));
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.45 + 0.3 * s, 0));
                    e.hurtMarked = true;
                }
                if (canGrief() && level().hasChunkAt(np)) trample(top, 5 + 9 * s, (int) (3 + 5 * s));
            }
            return;
        }
        // coming up
        digAmt = Math.max(0f, digAmt - 0.022f);
        if (digT == 1) {
            sound(c, net.jj.mountain.ModSounds.ROAR, 5f, 0.55f);
            sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()), c, 400, 50 * s + 8, 4, 0.5);
            double rr = 60 * s + 14;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBoxForCulling().inflate(rr))) {
                if (e == this || e instanceof MountainEntity || e instanceof HeartEntity || spares(e)) continue;
                if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
                e.hurt(damageSources().mobAttack(this), dmg(6 + 11 * s, e));
                Vec3 away = e.position().subtract(c).normalize().scale(0.8 + 0.8 * s).add(0, 0.9, 0);
                e.setDeltaMovement(e.getDeltaMovement().add(away));
                e.hurtMarked = true;
            }
        }
        if (digAmt <= 0f) {
            digState = 0; digT = 0; digTo = null; digCool = 1200 + random.nextInt(1200);
            cameUpAt();
        }
    }

    /** how far he covers in a tick: what the world uses to work out where he has got to while nobody is near */
    public double travelSpeed(boolean under) {
        float s = mountainScale();
        if (under) return (0.9 + 2.2 * s) * 3.0;
        // his top walking pace, knocked back a little for the turning and the ground he has to go round
        return (0.22 * s + 0.04) * (isHunter() ? 1.35 : 1.0) * 0.8;
    }

    /**
     * The first thing he does when the world starts running him again: if he was left part way somewhere, he is
     * put where he ought to have got to by now and carries on from there.
     */
    private boolean caughtUp;
    private void tripCatchUp() {
        if (caughtUp || level().isClientSide || !(level() instanceof ServerLevel sl)) return;
        caughtUp = true;
        var w = net.jj.mountain.world.MountainWorld.get(sl.getServer().overworld());
        if (!w.tripping() || !getUUID().equals(w.tripWho())) return;
        Vec3 at = w.tripSpot(sl);
        double dx = at.x - getX(), dz = at.z - getZ();
        if (dx * dx + dz * dz > 4) {
            BlockPos bp = new BlockPos(Mth.floor(at.x), 64, Mth.floor(at.z));
            holdChunkAt(sl, bp, getId());
            sl.getChunk(bp.getX() >> 4, bp.getZ() >> 4);
            int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bp.getX(), bp.getZ());
            moveTo(at.x, Math.max(y, sl.getMinBuildHeight() + 1), at.z, getYRot(), 0f);
            setYBodyRot(getYRot()); setYHeadRot(getYRot());
        }
        if (w.tripUnder()) {
            if (w.tripDone(sl)) { digState = 3; digT = 0; }        // at the end of the run he comes back up
            else { digState = 2; digAmt = 1f; digTo = w.tripEnd(); }
        } else if (!w.tripDone(sl)) {
            goal = w.tripEnd();
        } else {
            goal = null;
        }
        justBack = 200;                        // he has just been put back: he does not step straight out again
        net.jj.mountain.MountainMod.LOG.info("The Mountain has caught up to {}, {}", getBlockX(), getBlockZ());
        w.endTrip();
    }

    /** where he came up, written down, and whoever sent him under is told */
    private void cameUpAt() {
        if (!(level() instanceof ServerLevel sl)) return;
        if (worldOne) net.jj.mountain.world.MountainWorld.get(sl.getServer().overworld()).seen(this);
        if (sentUnderBy != null) {
            ServerPlayer who = sl.getServer().getPlayerList().getPlayer(sentUnderBy);
            if (who != null) who.displayClientMessage(Component.translatable("message.mountain_breathes.came_up",
                    getBlockX(), getBlockY(), getBlockZ()), false);
            sentUnderBy = null;
        }
    }
    private @Nullable UUID sentUnderBy;
    public void sentUnderBy(@Nullable UUID who) { sentUnderBy = who; }

    // ------------------------------------------------------------------ what everything else does about him
    /**
     * Nothing living wants to be near him. Animals break and run, villagers make for their doors, and anything
     * else with legs gets out of the way. They only bolt while he is up and moving, not while he sleeps.
     */
    private void scatterTheWorld() {
        if (level().isClientSide || sleeping() || isDeadOrDying() || tickCount % 30 != 7) return;
        float s = mountainScale();
        double r = 40 + 150 * s;
        int done = 0;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBoxForCulling().inflate(r * 0.5))) {
            if (done >= 24) break;
            if (e == this || e instanceof MountainEntity || e instanceof HeartEntity || e instanceof Player) continue;
            if (e instanceof net.jj.mountain.entity.inside.InsideMob || Innards.isInside(e)) continue;
            if (!(e instanceof Mob mob) || !mob.isAlive() || mob.isNoAi() || mob.isPassenger()) continue;
            double d = Math.sqrt(e.distanceToSqr(this));
            if (d > r) continue;
            done++;
            // a monster he is hunting stands its ground; everything else gets away from him
            if (e instanceof net.minecraft.world.entity.monster.Enemy && !spares(e) && d > r * 0.45) continue;
            Vec3 away = e.position().subtract(position());
            if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
            away = away.normalize().scale(24 + 40 * s);
            Vec3 to = e.position().add(away.x, 0, away.z);
            int gy = groundAt(Mth.floor(to.x), Mth.floor(to.z));
            if (mob.getNavigation().moveTo(to.x, gy, to.z, 1.6)) {
                mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 60, 1, false, false));
            } else {
                mob.setDeltaMovement(mob.getDeltaMovement().add(away.normalize().scale(0.22 + 0.2 * s)));
                mob.hurtMarked = true;
            }
        }
    }

    /** a storm brings him up: he moves and swings faster while it thunders */
    public boolean stormy() { return level().isThundering(); }

    // ------------------------------------------------------------------ more of you, more of him
    private float baseMax = -1f;
    private void scaleToTheFight() {
        if (level().isClientSide || !MountainConfig.V.scaleToPlayers || tickCount % 100 != 13) return;
        if (baseMax < 0f) baseMax = Math.max(60f, Math.round(MountainConfig.V.health * sizeFactor(mountainScale())));
        double r = 200 + 400 * mountainScale();
        int n = 0;
        for (Player p : level().players())
            if (!p.isSpectator() && !p.isCreative() && p.isAlive() && p.distanceToSqr(this) < r * r) n++;
        n = Mth.clamp(n, 1, 6);
        float want = baseMax * (1f + 0.45f * (n - 1));
        if (Math.abs(want - healthMax()) < 1f) return;
        float frac = healthMax() > 0 ? Mth.clamp(hp / healthMax(), 0f, 1f) : 1f;
        entityData.set(DATA_HP_MAX, want);
        hp = want * frac;
        syncHealth();
    }

    // ------------------------------------------------------------------ being him
    private @Nullable LivingEntity rider;
    private int driveIdle, carriedT;
    private float driveF, driveS, driveYaw;
    /** the seat he carries a driver in, cradled by his arms */
    private @Nullable GripSeat rideSeat;
    /** somebody he has been asked to come and pick up */
    private @Nullable LivingEntity fetch;
    /** 1 = walking over to you, 2 = the hand taking you up, 3 = setting you back down */
    private int fetchState, fetchT, fetchArm = -1;
    private @Nullable Vec3 liftFrom, liftTo;
    private boolean stayWas;
    private static final int LIFT_DOWN = 24, LIFT_UP = 26, SET_DOWN = 56;

    public @Nullable LivingEntity rider() { return rider; }
    /** somebody is in him, working him from the inside */
    public boolean ridden() { return rider != null && rider.isAlive() && !rider.isRemoved() && rider.level() == level(); }

    /**
     * Where the view sits when you are him: clear above the crest of his back behind his neck, so you look out
     * over the top of his own head.
     */
    public Vec3 saddleWorld() {
        if (topReach == null || rig.body.isEmpty()) return toWorld(rig.headCenter, pose[rig.head]).add(0, 34 * mountainScale(), 0);
        float s = mountainScale();
        float zlo = Float.MAX_VALUE, zhi = -Float.MAX_VALUE;
        for (MountainRig.BodySample b : rig.body) { zlo = Math.min(zlo, b.p().z); zhi = Math.max(zhi, b.p().z); }
        float cut = zlo + (zhi - zlo) * 0.34f;
        int seat = -1;
        double high = -Double.MAX_VALUE, crest = 0;
        for (int i = 0; i < rig.body.size(); i++) {
            if (rig.body.get(i).p().z > cut) continue;
            crest = Math.max(crest, topReach[i][0]);
            if (topReach[i][0] > high) { high = topReach[i][0]; seat = i; }
        }
        if (seat < 0) { seat = 0; crest = topReach[0][0]; }
        MountainRig.BodySample b = rig.body.get(seat);
        Vec3 w = toWorld(b.p(), pose[b.bone()]);
        return w.add(0, crest * s + 7 * s + 1.4, 0);
    }

    /**
     * Where he carries you: down in the hollow of his back rather than perched on the crest of it, so the arms
     * that curl in around you stand over your head.
     */
    public Vec3 seatWorld() { return saddleWorld().subtract(0, 7.0 * mountainScale() + 0.2, 0); }

    /** true while he is carrying a driver, lifting one up to the seat, or setting one back down */
    public boolean carryingSomebody() {
        return rider != null || (fetch != null && ((fetchState == 2 && fetchT >= LIFT_DOWN) || fetchState == 3));
    }

    /** he is coming to pick you up: the book asks for this, and he has to walk over and reach down for you */
    public boolean comeAndGetMe(LivingEntity who) {
        if (level().isClientSide || isDeadOrDying() || rider != null || fetch != null) return false;
        if (shutOutNow(who)) return false;
        if (sleeping()) wakeUp(who instanceof Player p ? p : null);
        fetch = who; fetchState = 1; fetchT = 0; fetchArm = -1;
        stayWas = stay;                                    // put back if he never gets to you
        stay = false;
        goal = who.position();
        sound(position(), net.jj.mountain.ModSounds.ROAR, 3f, 0.8f);
        if (who instanceof ServerPlayer sp)
            sp.displayClientMessage(Component.translatable("message.mountain_breathes.coming_for_you", (int) Math.sqrt(distanceToSqr(who))), true);
        return true;
    }

    /** true (and says so) when he has shut this one out after throwing them off */
    private boolean shutOutNow(LivingEntity who) {
        if (!(who instanceof ServerPlayer sp) || !(level() instanceof ServerLevel sl)) return false;
        long out = net.jj.mountain.world.MountainWorld.get(sl.getServer().overworld()).shutOutFor(sl, sp.getUUID());
        if (out <= 0) return false;
        sp.displayClientMessage(Component.translatable("message.mountain_breathes.shut_out", (int) Math.ceil(out / 1200.0)), true);
        return true;
    }

    /** one word for what he is doing, for the book's "where is he" line */
    public String doingNow() {
        if (burrowed() || digState != 0) return "under";
        if (sleeping()) return "asleep";
        if (knockedDown()) return "down";
        if (ridden()) return "ridden";
        if (goal != null || calledBy != null) return "walking";
        if (stay) return "standing";
        return walkAmount > 0.15f ? "walking" : "standing";
    }

    public boolean comingForSomebody() { return fetch != null && fetchState <= 2; }
    /** true while a hand is carrying somebody back down to the ground */
    public boolean settingSomebodyDown() { return fetch != null && fetchState == 3; }

    public void stopFetch() {
        if (fetchState >= 1) stay = stayWas;
        // whoever was half way up comes down gently rather than being dropped from his shoulder
        if (fetch != null && rideSeat != null && !rideSeat.isRemoved() && rideSeat.getPassengers().contains(fetch)) {
            fetch.stopRiding();
            fetch.fallDistance = 0;
            fetch.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 100, 0, false, false));
            noGrab = 60;
        }
        if (rider == null && rideSeat != null) { rideSeat.discard(); rideSeat = null; }
        fetch = null; fetchState = 0; fetchT = 0; fetchArm = -1; liftFrom = liftTo = null;
    }

    /**
     * Asking to get off: he doesn't shrug you into the air, he takes you out of the seat with a hand and carries
     * you down to the ground beside his foot.
     */
    public boolean setMeDown() {
        if (rider == null || rideSeat == null || rideSeat.isRemoved()) return false;
        LivingEntity who = rider;
        if (who instanceof ServerPlayer sp)
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp, new net.jj.mountain.net.InsideHimPayload(getId(), false));
        rider = null; driveF = driveS = 0f;
        fetch = who; fetchState = 3; fetchT = 0;
        stayWas = stay; stay = true;                            // he stands still while he lowers you
        liftFrom = seatWorld();
        fetchArm = nearestHand(liftFrom);
        Vec3 hw = fetchArm >= 0 ? handWorld(fetchArm) : position().add(20 * mountainScale(), 0, 0);
        liftTo = new Vec3(hw.x, groundAt(Mth.floor(hw.x), Mth.floor(hw.z)) + 0.2, hw.z);
        sound(liftFrom, SoundEvents.SLIME_SQUISH, 2f, 0.6f);
        if (who instanceof ServerPlayer sp2) sp2.displayClientMessage(Component.translatable("message.mountain_breathes.setting_down"), true);
        return true;
    }

    /**
     * Walking over to whoever asked for him, then a hand comes down, closes round them and lifts them up to the
     * seat. Only when they are in it do they get him.
     */
    private void fetchTick() {
        if (fetch == null) return;
        // a player in creative is fine to carry: they asked for it. A spectator has nothing to pick up.
        if (!fetch.isAlive() || fetch.isRemoved() || fetch.level() != level() || isDeadOrDying() || rider != null
                || (fetch instanceof Player fp && fp.isSpectator())) { stopFetch(); return; }
        float s = mountainScale();
        Vec3 c = fetch.getBoundingBox().getCenter();
        if (fetchState == 1) {
            if (++fetchT > 20 * 60 * 4) {                      // he lost you
                if (fetch instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("message.mountain_breathes.lost_you"), true);
                stopFetch(); return;
            }
            // he stops short of you rather than walking over the top of you
            if (fetchT % 20 == 0 && !burrowed()) {
                Vec3 back = position().subtract(c);
                back = back.horizontalDistanceSqr() < 1 ? new Vec3(1, 0, 0) : back.multiply(1, 0, 1).normalize();
                goal = c.add(back.scale(40 * s + 9));
            }
            if (burrowed() || knockedDown()) return;
            int arm = nearestHand(c);
            if (arm < 0) return;
            // he has arrived once you are standing under him. His hands are up on his back and can't come all
            // the way down to the ground, so the reach is judged by how far his own body spreads, not by where
            // a hand happens to be at that moment.
            Vector3f lo = rig.extentMin, hi = rig.extentMax;
            double spread = Math.sqrt(Math.max(lo.x * lo.x, hi.x * hi.x) + Math.max(lo.z * lo.z, hi.z * hi.z));
            double r = spread * s + 10;
            double dx = c.x - getX(), dz = c.z - getZ();
            if (dx * dx + dz * dz > r * r) return;
            fetchArm = arm;
            fetchState = 2; fetchT = 0; goal = null;
            stay = true;                                        // he stands over you while he picks you up
            sound(handWorld(arm), SoundEvents.SLIME_ATTACK, 2.5f, 0.5f);
            if (fetch instanceof ServerPlayer sp4) sp4.displayClientMessage(Component.translatable("message.mountain_breathes.reaching_down"), true);
            return;
        }
        if (fetchState == 3) {                                  // carried back down to the ground
            fetchT++;
            float k = Mth.clamp(fetchT / (float) SET_DOWN, 0f, 1f);
            Vec3 from = liftFrom == null ? seatWorld() : liftFrom, to = liftTo == null ? position() : liftTo;
            // out past his side first, then down: straight from the seat to the ground would drag you through
            // the middle of him
            Vec3 out = new Vec3(to.x, from.y, to.z);
            Vec3 at = k < 0.45f ? from.lerp(out, ease(k / 0.45f)) : out.lerp(to, ease((k - 0.45f) / 0.55f));
            if (rideSeat != null) rideSeat.moveTo(at.x, at.y - fetch.getBbHeight() * 0.5, at.z);
            Vec3 mpd = worldToModelPoint(at);
            reach.set((float) mpd.x, (float) mpd.y, (float) mpd.z);
            reachAmt = 1f;
            if (k >= 1f) {
                LivingEntity who = fetch;
                who.stopRiding();
                who.fallDistance = 0;
                who.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 60, 0, false, false));
                if (rideSeat != null) { rideSeat.discard(); rideSeat = null; }
                sound(at, SoundEvents.SLIME_SQUISH, 1.6f, 0.9f);
                noGrab = 100;                                  // he has just put you down; he doesn't snatch you back
                stopFetch();
                if (who instanceof ServerPlayer sp3) sp3.displayClientMessage(Component.translatable("message.mountain_breathes.set_down"), true);
            }
            return;
        }
        fetchT++;
        if (fetchT <= LIFT_DOWN) {                              // the hand comes down to you
            int arm = nearestHand(c);
            if (arm >= 0) fetchArm = arm;
            Vec3 mp = worldToModelPoint(c);
            reach.set((float) mp.x, (float) mp.y, (float) mp.z);
            reachAmt = Math.min(1f, fetchT / (float) (LIFT_DOWN - 4));
            if (fetchT == LIFT_DOWN) {
                liftFrom = fetch.position();                    // he takes you from where you stand
                seatIn(fetch, liftFrom);
                sound(handWorld(fetchArm), SoundEvents.SLIME_SQUISH, 2f, 0.5f);
            }
            return;
        }
        float k = Mth.clamp((fetchT - LIFT_DOWN) / (float) LIFT_UP, 0f, 1f);
        // up off the ground into the hand first, then the hand carries you the rest of the way to the seat
        Vec3 hand = handWorld(fetchArm), to = seatWorld();
        Vec3 at;
        if (k < 0.4f) at = (liftFrom == null ? hand : liftFrom).lerp(hand, ease(k / 0.4f));
        else at = hand.lerp(to, ease((k - 0.4f) / 0.6f));
        if (rideSeat != null) rideSeat.moveTo(at.x, at.y - fetch.getBbHeight() * 0.5, at.z);
        Vec3 mp = worldToModelPoint(at);
        reach.set((float) mp.x, (float) mp.y, (float) mp.z);
        reachAmt = 1f;
        if (k >= 1f) { LivingEntity who = fetch; stopFetch(); takeTheReins(who); }
    }

    private static float ease(float k) { k = Mth.clamp(k, 0f, 1f); return k * k * (3 - 2 * k); }

    /** the unbroken hand nearest a spot */
    private int nearestHand(Vec3 p) {
        int best = -1; double bd = Double.MAX_VALUE;
        for (MountainRig.ArmDef A : rig.arms) {
            if (armBroken(A.k)) continue;
            double d = toWorld(A.handPoint, pose[A.hand]).distanceToSqr(p);
            if (d < bd) { bd = d; best = A.k; }
        }
        return best;
    }

    private void seatIn(LivingEntity who, Vec3 at) {
        if (rideSeat != null) { rideSeat.discard(); rideSeat = null; }
        rideSeat = new GripSeat(level(), this);
        rideSeat.markRide();
        rideSeat.setPos(at.x, at.y - who.getBbHeight() * 0.5, at.z);
        level().addFreshEntity(rideSeat);
        who.stopRiding();
        who.startRiding(rideSeat, true);
    }

    /** he has you: you work him from up there, held in among his arms */
    public boolean possess(LivingEntity who) {
        if (shutOutNow(who)) return false;
        return takeTheReins(who);
    }

    /** the same, without asking again whether he'll have you: by now he has you in his hand */
    private boolean takeTheReins(LivingEntity who) {
        if (level().isClientSide || isDeadOrDying() || rider != null) return false;
        if (fetch != null && fetch != who) return false;                       // he is in the middle of somebody else
        if (rideSeat != null && !rideSeat.isRemoved() && !rideSeat.getPassengers().isEmpty()
                && !rideSeat.getPassengers().contains(who)) return false;
        if (held != null) releaseHeld(true);                                   // he needs the hand
        if (sleeping()) wakeUp(who instanceof Player p ? p : null);
        if (rideSeat == null || rideSeat.isRemoved()) seatIn(who, seatWorld());
        rider = who;
        driveIdle = 0; carriedT = 0; driveF = driveS = 0f; driveYaw = getYRot();
        stay = false;
        sound(position(), net.jj.mountain.ModSounds.ROAR, 3f, 0.8f);
        if (who instanceof ServerPlayer sp) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp, new net.jj.mountain.net.InsideHimPayload(getId(), true));
            sp.displayClientMessage(Component.translatable("message.mountain_breathes.inside_him"), true);
        }
        return true;
    }

    public void dropRider() {
        if (rider instanceof ServerPlayer sp)
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp, new net.jj.mountain.net.InsideHimPayload(getId(), false));
        if (rider != null) { rider.stopRiding(); rider.fallDistance = 0; }     // set down on his back, which you can walk on
        if (rideSeat != null) { rideSeat.discard(); rideSeat = null; }
        rider = null;
        driveF = driveS = 0f;
        reachAmt = Math.min(reachAmt, 0.4f);
    }

    /** keeps whoever is driving in the seat, and the arms folded in over them (runs after the pose) */
    private void carryRider() {
        if (rider == null || rideSeat == null || rideSeat.isRemoved()) return;
        Vec3 at = seatWorld();
        rideSeat.moveTo(at.x, at.y - rider.getBbHeight() * 0.5, at.z);
        if (!rider.isPassenger() || rider.getVehicle() != rideSeat) {
            if (rider.isAlive() && !rider.isRemoved()) rider.startRiding(rideSeat, true);
        }
        rider.fallDistance = 0;
        Vec3 mp = worldToModelPoint(at);
        reach.set((float) mp.x, (float) mp.y, (float) mp.z);
        reachAmt = Math.min(1f, reachAmt + 0.05f);
    }

    /** what the player at the other end is pushing on */
    public void drive(LivingEntity who, float forward, float strafe, float yaw) {
        if (rider != who) return;
        driveF = Mth.clamp(forward, -1f, 1f);
        driveS = Mth.clamp(strafe, -1f, 1f);
        driveYaw = yaw;
        driveIdle = 0;
    }

    /** he is nobody's again if the body dies, leaves, is hurt, or the player stops sending */
    private void riderTick() {
        if (rider == null) return;
        if (!rider.isAlive() || rider.isRemoved() || rider.level() != level() || isDeadOrDying()) { dropRider(); return; }
        // anything that gets a hit in on you up there throws you off, and he won't have you back for a long while.
        // His own doing doesn't count: his goo, his own hands, the drop into the seat. Something else has to land it.
        if (rider.hurtTime > 0) {
            DamageSource last = rider.getLastDamageSource();
            if (!myOwnDoing(last)) { thrownOut(); return; }
        }
        driveIdle++;
        if (driveIdle > 60) { driveF = 0f; driveS = 0f; }     // nobody pushing: he stands, but he still carries you
        if (++carriedT > 1200 && carriedT % 20 == 0) mood.sour(rider.getUUID(), 0.0005f);   // an hour on his back tells
    }

    /** everything he does to you himself while he is carrying you: none of it counts as being knocked off */
    private boolean myOwnDoing(@Nullable DamageSource src) {
        if (src == null) return true;
        if (src.is(net.minecraft.tags.DamageTypeTags.IS_FALL) || src.is(DamageTypes.IN_WALL)
                || src.is(DamageTypes.FLY_INTO_WALL) || src.is(DamageTypes.CRAMMING)) return true;
        Entity a = src.getEntity(), b = src.getDirectEntity();
        return mine(a) || mine(b);
    }

    private boolean mine(@Nullable Entity e) {
        if (e == null) return false;
        if (e == this) return true;
        if (e instanceof MountainPart mp) return mp.owner() == this;
        if (e instanceof net.jj.mountain.entity.GooGlob) return true;
        return e instanceof net.jj.mountain.entity.inside.InsideMob;
    }

    /** something got a hit in on you up there: off you come, and he won't have you again for a long while */
    private void thrownOut() {
        LivingEntity who = rider;
        if (who instanceof ServerPlayer sp && level() instanceof ServerLevel sl) {
            net.jj.mountain.world.MountainWorld.get(sl.getServer().overworld())
                    .shutOut(sl, sp.getUUID(), Math.max(200, MountainConfig.V.shutOutTicks));
            sp.displayClientMessage(Component.translatable("message.mountain_breathes.thrown_out"), true);
        }
        sound(position(), net.jj.mountain.ModSounds.SCREAM, 3f, 1.3f);
        if (who != null) mood.sour(who.getUUID(), 0.15f);
        dropRider();
    }

    // ------------------------------------------------------------------ the one the world keeps
    private boolean worldOne;
    public void markWorldOne() { worldOne = true; }

    /**
     * A lump he tore out of his own side. It is him at a fraction of the size, it fights beside him for a minute
     * and then it comes apart. It is not the world's one, it is never counted against the limit, and it leaves
     * nothing behind but goo.
     */
    private int pieceLeft = -1;
    public boolean isAPiece() { return pieceLeft >= 0; }

    /** is anybody carrying the book right now? While somebody is, the lump is theirs to call and not his */
    public boolean someoneHasTheBook() { return friendList() != null && friendList().bookHolder() != null; }
    public void becomeAPieceOf(MountainEntity parent) {
        pieceLeft = 20 * 60;
        worldOne = false;
        bornAt = level().getGameTime();
        setStay(false);
    }

    private void pieceTick() {
        if (pieceLeft < 0 || level().isClientSide) return;
        if (--pieceLeft > 0) return;
        if (level() instanceof ServerLevel sl) {
            sendParticles(net.minecraft.core.particles.ParticleTypes.SQUID_INK, position().add(0, 10 * mountainScale() + 2, 0),
                    260, 14 * mountainScale() + 4, 10 * mountainScale() + 2, 0.35);
            sound(position(), net.jj.mountain.ModSounds.DEATH, 4f, 0.8f);
        }
        parked = true;                                    // it is not written down and it is not coming back
        clearBars();
        discard();
    }
    public boolean isWorldOne() { return worldOne; }

    /**
     * While he is loaded he keeps the world posted on where he is. The one the world keeps is written down as
     * himself; any other is written down as the last place a Mountain was seen, so the book and the finder are
     * never pointing at somewhere he left ten minutes ago.
     */
    private void reportIn() {
        if (level().isClientSide || isDeadOrDying()) return;
        if (tickCount % 40 != 0 || !(level() instanceof ServerLevel sl)) return;
        var w = net.jj.mountain.world.MountainWorld.get(sl.getServer().overworld());
        if (worldOne) w.seen(this); else w.noted(this);
    }

    // ------------------------------------------------------------------ who he has not forgotten
    /** everyone who has ever drawn his blood, kept for good */
    private final java.util.LinkedHashSet<UUID> grudges = new java.util.LinkedHashSet<>();
    /** what the book costs him: his wind, and what he holds against whoever is holding it */
    private final Mood mood = new Mood(this);
    public Mood mood() { return mood; }
    /** he does not forget this one either */
    public void holdItAgainst(Player p) { remember(p); }
    /** who he has already roared at this time round, so he only does it once per meeting */
    private final Set<UUID> greeted = new HashSet<>();
    private int huntTick;

    public boolean holdsGrudge(@Nullable Entity e) { return e != null && !grudges.isEmpty() && grudges.contains(e.getUUID()); }
    public int grudgeCount() { return grudges.size(); }
    public void forgiveAll() { grudges.clear(); greeted.clear(); }

    private void remember(Entity e) {
        if (!(e instanceof Player) || level().isClientSide) return;
        if (grudges.add(e.getUUID()) && grudges.size() > 12) {
            java.util.Iterator<UUID> it = grudges.iterator();
            it.next(); it.remove();
        }
    }

    /**
     * Run from him and he does not let it go. Every so often he works out where the nearest person on his list is
     * and starts walking, and when he gets there he is already angry.
     */
    private void hunt() {
        boolean anyMarked = friendList() != null && friendList().markedCount() > 0;
        if ((grudges.isEmpty() && !mood.huntingAnyone() && !anyMarked) || sleeping() || isDeadOrDying()) return;
        if (stay && !mood.huntingAnyone()) return;               // told to stay put, unless he is after somebody
        if (getTarget() != null) return;
        if (++huntTick % 200 != 0) return;
        Player best = null; double bd = Double.MAX_VALUE;
        for (Player p : level().players()) {
            boolean wanted = holdsGrudge(p) || mood.hunting(p.getUUID())
                    || (friendList() != null && friendList().marked(p.getUUID()));
            if (!wanted || spares(p)) continue;            // the safe list beats anything he remembers
            if (p.isCreative() || p.isSpectator() || !p.isAlive() || Innards.isInside(p)) continue;
            double d = p.distanceToSqr(this);
            if (d < bd) { bd = d; best = p; }
        }
        if (best == null) { greeted.clear(); return; }
        double close = 60 + 220 * mountainScale();
        if (bd > close * close) {
            if (goal == null) goal = best.position();                 // still a long way off: start walking
            else if (goal.distanceToSqr(best.position()) > 400) goal = best.position();
            return;
        }
        if (greeted.add(best.getUUID())) {
            sound(toWorld(rig.headCenter, pose[rig.head]), net.jj.mountain.ModSounds.ROAR, 3.5f, 0.75f);
            best.displayClientMessage(Component.translatable("message.mountain_breathes.remembered"), true);
        }
        angerTicks = Math.max(angerTicks, 1200);
        playerAnger = Math.max(playerAnger, 1200);
        setTarget(best);
        goal = null;
    }

    // ------------------------------------------------------------------ things he has been told to go after
    /** /mountain attack: things he has been sent after, in order. He works down the list as they die. */
    private final List<UUID> hitList = new ArrayList<>();

    /** sends him after these, nearest first; he keeps at them until they are dead or the list is cleared */
    public void sendAfter(List<? extends Entity> list) {
        hitList.clear();
        for (Entity e : list) if (e instanceof LivingEntity && e != this && !(e instanceof MountainEntity)) hitList.add(e.getUUID());
        setTarget(null);
        angerTicks = Math.max(angerTicks, 600);
        for (Entity e : list) if (e instanceof Player) { playerAnger = Math.max(playerAnger, 600); break; }
        if (sleeping()) wakeUp(null);
    }

    public void clearHitList() { hitList.clear(); setTarget(null); }
    public int hitListSize() { return hitList.size(); }
    public boolean onHitList(@Nullable Entity e) { return e != null && !hitList.isEmpty() && hitList.contains(e.getUUID()); }

    /** drops the ones that are gone and gives back the nearest one still standing */
    private @Nullable LivingEntity fromHitList() {
        if (hitList.isEmpty() || !(level() instanceof ServerLevel sl)) return null;
        LivingEntity best = null; double bd = Double.MAX_VALUE;
        for (Iterator<UUID> it = hitList.iterator(); it.hasNext(); ) {
            Entity e = sl.getEntity(it.next());
            if (!(e instanceof LivingEntity le) || !le.isAlive() || le.isRemoved()
                    || (le instanceof Player p && (p.isCreative() || p.isSpectator()))) { it.remove(); continue; }
            double d = le.distanceToSqr(this);
            if (d < bd) { bd = d; best = le; }
        }
        return best;
    }

    /** /mountain goto: walk to a spot and stop there. */
    public void setGoal(@Nullable Vec3 g) { this.goal = g; this.stay = false; calledBy = null; }

    // ------------------------------------------------------------------ called over
    /** who told him to come, if anybody: he keeps walking until he is standing with them */
    private @Nullable UUID calledBy;
    private int calledT;

    public boolean beingCalled() { return calledBy != null; }

    /** "Come to me": not a spot to walk to but a person to walk to, kept up until he is there */
    public void callTo(Player who) {
        calledBy = who.getUUID();
        calledT = 0;
        stay = false;
        goal = who.position();
    }

    /** how close he counts as having arrived: his own body reaching you, not the middle of him */
    public double arriveRange() {
        Vector3f lo = rig.extentMin, hi = rig.extentMax;
        double spread = Math.sqrt(Math.max(lo.x * lo.x, hi.x * hi.x) + Math.max(lo.z * lo.z, hi.z * hi.z));
        return spread * mountainScale() * 0.55 + 12;
    }

    private void calledTick() {
        if (calledBy == null || level().isClientSide) return;
        if (!(level() instanceof ServerLevel sl)) return;
        Player who = sl.getPlayerByUUID(calledBy);
        if (who == null || !who.isAlive() || who.level() != level() || ++calledT > 20 * 60 * 6) { calledBy = null; return; }
        double dx = who.getX() - getX(), dz = who.getZ() - getZ();
        if (dx * dx + dz * dz < arriveRange() * arriveRange()) {
            calledBy = null;
            goal = null;
            if (who instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("message.mountain_breathes.arrived"), true);
            sound(position(), net.jj.mountain.ModSounds.ROAR, 3f, 0.7f);
            return;
        }
        // kept up as you move, and never let go of while he is still on his way
        if (calledT % 20 == 0 && !burrowed() && !stay) goal = who.position();
        if (calledT % 100 == 0 && who instanceof ServerPlayer sp2)
            sp2.displayClientMessage(Component.translatable("message.mountain_breathes.on_his_way", (int) Math.sqrt(dx * dx + dz * dz)), true);
    }
    /** /mountain stay: stand where he is (he still turns, breathes, grabs and swallows). */
    public void setStay(boolean v) { this.stay = v; this.goal = null; this.wanderGoal = null; this.calledBy = null; }
    public boolean staying() { return stay; }

    // ------------------------------------------------------------------ breathing
    public int stage() { return entityData.get(DATA_ACT) >>> 16 & 0xff; }
    public int stageTime() { return entityData.get(DATA_ACT) & 0xffff; }
    public boolean strongBreathSynced() { return (entityData.get(DATA_ACT) >>> 24 & 1) != 0; }

    private boolean someoneAtHisMouth() {
        float s = mountainScale();
        Vec3 m = mouthWorld(), dir = mouthDir();
        for (Player p : level().players()) {
            if (p.isCreative() || p.isSpectator() || spares(p)) continue;
            Vec3 v = p.position().subtract(m);
            double d = v.length();
            if (d < 45 * s + 8 && v.normalize().dot(dir) > 0.4) return true;
        }
        return false;
    }

    private boolean forceStrong;

    /** /mountain breathe: a full, hard breath starting now (in, hold, then the blast) */
    public void forceBreath() { breathTick = 0; forceStrong = true; }

    private void breathe(@Nullable LivingEntity target) {
        float s = mountainScale();
        float frac = hp / Math.max(1f, healthMax());
        boolean angry = isAngry() && target != null;
        // he comes back for another pull sooner than he used to, and holds the pull for longer each time
        int period = (int) ((angry ? 110 : 185) * (frac < 0.25f ? 0.62f : frac < 0.5f ? 0.8f : 1f));
        if (breathTick == 0) { breathPeriod = period; strongBreath = angry || forceStrong || someoneAtHisMouth(); forceStrong = false; }
        float f = breathTick / (float) breathPeriod;
        int newStage = f < 0.50f ? INHALE : f < 0.58f ? HOLD : f < 0.80f ? EXHALE : REST;
        if (newStage != stage) { stage = newStage; stageT = 0; } else stageT++;
        float mouthWant, breathWant;
        switch (stage) {
            case INHALE -> {
                float t = f / 0.50f;
                breathWant = smooth(t);
                mouthWant = strongBreath ? 0.30f + 0.45f * smooth(t * 1.6f) : 0.10f + 0.14f * smooth(t);
                if (stageT == 0) sound(mouthWorld(), net.jj.mountain.ModSounds.BREATH_IN, strongBreath ? 2.6f : 1.3f, strongBreath ? 0.8f : 1.0f);
                if (t > 0.08f) suck(strongBreath ? 1f : 0.22f, true);
            }
            case HOLD -> { breathWant = 1f; mouthWant = 0.03f; grind = 0.9f * Mth.sin(tickCount * 0.9f); }
            case EXHALE -> {
                float t = (f - 0.58f) / 0.22f;
                breathWant = 1f - smooth(t);
                mouthWant = (strongBreath ? 1.0f : 0.40f) * (1f - 0.6f * smooth(t));
                if (stageT == 1) blast(strongBreath);
            }
            default -> { breathWant = 0f; mouthWant = 0.04f + 0.03f * Mth.sin(tickCount * 0.05f); }
        }
        if (stage != HOLD) grind *= 0.7f;
        breath += (breathWant - breath) * 0.35f;
        mouth += (mouthWant - mouth) * 0.25f;
        breathTick++;
        if (breathTick >= breathPeriod) breathTick = 0;
    }

    /**
     * The inhale: pull things toward his mouth. Whatever reaches it is swallowed. Far out it is a tug you can
     * walk out of; the closer you get the harder it takes hold, and inside the last stretch it picks you up off
     * your feet and there is no walking anywhere.
     */
    private void suck(float strength, boolean canSwallow) {
        float s = mountainScale();
        Vec3 m = mouthWorld(), dir = mouthDir();
        double range = 95 * s + 12;
        boolean hard = strength > 0.5f && canSwallow;           // a full breath in, not the one he takes all day
        double grab = 34 * s + 8;                               // inside this he has you
        for (Entity e : level().getEntities(this, AABB.ofSize(m.add(dir.scale(range * 0.45)), range * 1.3, range * 1.3, range * 1.3), this::canBeSucked)) {
            Vec3 c = e.getBoundingBox().getCenter();
            Vec3 v = m.subtract(c);
            double d = v.length();
            if (d > range) continue;
            double along = c.subtract(m).dot(dir);
            if (along < -(20 * s + 4) || (d > 22 * s + 5 && along / Math.max(d, 1e-3) < 0.35)) continue;
            if (canSwallow && d < 12 * s + 4) { swallow(e); continue; }
            double near = Math.max(0, 1 - d / range);
            double pull = strength * (0.05 + 0.42 * near * near) * (e instanceof ItemEntity || e instanceof ExperienceOrb ? 1.8 : 1.0);
            Vec3 add = v.normalize().scale(pull);
            double lift = e.onGround() ? strength * (0.06 + 0.26 * near) : 0.0;
            e.setDeltaMovement(e.getDeltaMovement().add(add.x, add.y + lift, add.z));
            if (hard && d < grab) {
                // off your feet and straight down it: the ground stops being anything to hold on to
                Vec3 to = v.normalize().scale(Math.min(d, 0.75 + 1.2 * s));
                e.setDeltaMovement(to.x, Math.max(to.y, 0.14), to.z);
            }
            e.hurtMarked = true;
            if (e instanceof LivingEntity le) le.fallDistance = 0;
        }
        if (tickCount % 3 == 0) {
            for (int k = 0; k < 5; k++) {
                Vec3 p = m.add(dir.scale(range * (0.25 + 0.75 * random.nextDouble()))).add((random.nextDouble() - 0.5) * range * 0.5, (random.nextDouble() - 0.5) * range * 0.3, (random.nextDouble() - 0.5) * range * 0.5);
                Vec3 v = m.subtract(p).normalize().scale(1.2 + 2.0 * s);
                sendParticle(ParticleTypes.CLOUD, p, v);
            }
        }
    }

    private boolean canBeSucked(Entity e) {
        if (spares(e)) return false;
        if (e instanceof MountainPart || e instanceof MountainEntity || e instanceof GripSeat || e instanceof HeartEntity || !e.isAlive()) return false;
        if (e == held || e.isPassenger()) return false;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        return e instanceof LivingEntity || e instanceof ItemEntity || e instanceof ExperienceOrb || e instanceof Projectile;
    }

    /** The exhale: a blast straight out of his mouth. */
    private void blast(boolean strong) {
        float s = mountainScale();
        Vec3 m = mouthWorld(), dir = mouthDir();
        double range = (strong ? 115 : 50) * s + (strong ? 14 : 6);
        double cone = Math.cos(Math.toRadians(30));
        sound(m, net.jj.mountain.ModSounds.BREATH_OUT, strong ? 3.0f : 1.3f, strong ? 0.85f : 1.05f);
        if (strong) sound(m, net.jj.mountain.ModSounds.ROAR, 2.0f, 0.8f);
        for (Entity e : level().getEntities(this, AABB.ofSize(m.add(dir.scale(range * 0.5)), range * 1.3, range * 1.3, range * 1.3), this::canBeSucked)) {
            Vec3 v = e.getBoundingBox().getCenter().subtract(m);
            double d = v.length();
            if (d > range || v.normalize().dot(dir) < cone) continue;
            // a wall between you and his mouth shelters you from the blast
            if (level().clip(new net.minecraft.world.level.ClipContext(m, e.getBoundingBox().getCenter(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, this)).getType() != net.minecraft.world.phys.HitResult.Type.MISS) continue;
            double k = (strong ? 2.4 + 2.2 * s : 0.7 + 0.5 * s) * (1 - 0.55 * d / range);
            Vec3 push = dir.scale(k).add(0, strong ? 0.55 : 0.25, 0);
            e.setDeltaMovement(e.getDeltaMovement().add(push));
            e.hurtMarked = true;
            if (strong && e instanceof LivingEntity le) {
                le.hurt(damageSources().mobAttack(this), dmg(3 + 5 * s, le));
                le.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0), this);
            }
        }
        for (int k = 0; k < (strong ? 14 : 5); k++) {
            Vec3 p = m.add(dir.scale(range * random.nextDouble() * 0.8));
            sendParticles(strong ? ParticleTypes.GUST_EMITTER_LARGE : ParticleTypes.GUST, p, 1, 3 * s, 2 * s, 0.0);
        }
        sendParticles(ParticleTypes.SQUID_INK, m, (int) (40 + 120 * s), 6 * s, 6 * s, 0.6);
        // goo sprays out with it and lands in front of him
        if (strong && MountainConfig.V.gooTrail) {
            for (int k = 0; k < 10 + (int) (18 * s); k++) {
                double dd = range * (0.2 + 0.7 * random.nextDouble());
                Vec3 p = m.add(dir.scale(dd)).add((random.nextDouble() - 0.5) * dd * 0.5, 0, (random.nextDouble() - 0.5) * dd * 0.5);
                gooPatch(p.x, p.z, 0.8 + 1.6 * s * random.nextDouble(), false);
            }
        }
    }

    // ------------------------------------------------------------------ swallowing
    public void swallow(Entity e) {
        if (isDeadOrDying() || e.isRemoved()) return;
        float s = mountainScale();
        Vec3 m = mouthWorld();
        if (e == held) releaseHeld(false);
        sound(m, SoundEvents.GENERIC_EAT, 3.0f, 0.3f);
        sound(m, SoundEvents.HONEY_BLOCK_SLIDE, 2.0f, 0.4f);
        sendParticles(ParticleTypes.SQUID_INK, m, 40, 3 * s, 3 * s, 0.3);
        if (e instanceof ItemEntity || e instanceof ExperienceOrb || e instanceof Projectile) { e.discard(); return; }
        if (e instanceof ServerPlayer p && MountainConfig.V.fightInside && s >= 0.12f && Innards.swallow(p, this)) {
            swallowedPlayers.add(p.getUUID());
            heartDamageThisVisit = 0;
            p.displayClientMessage(Component.translatable("message.mountain_breathes.swallowed"), true);
            return;
        }
        if (e instanceof LivingEntity le) {
            le.hurt(damageSources().mobAttack(this), dmg(e instanceof Player ? 6 + 10 * s : 20 + 30 * s, le));
            if (!(le instanceof Player) && !le.isAlive()) mood.fed();     // something to eat takes the edge off
            if (le.isAlive()) spitOut(le);
        }
    }

    /** Throw something out of his mouth, down onto the ground in front of him. */
    public void spitOut(Entity e) {
        float s = mountainScale();
        Vec3 m = mouthWorld(), dir = mouthDir();
        Vec3 flat = new Vec3(dir.x, 0, dir.z).normalize();
        Vec3 land = m.add(flat.scale(18 * s + 4));
        level().getChunk(Mth.floor(land.x) >> 4, Mth.floor(land.z) >> 4);       // make sure the ground there is loaded
        int gy = groundAt(Mth.floor(land.x), Mth.floor(land.z));
        if (e instanceof ServerPlayer sp && sp.level() != level()) {
            sp.teleportTo((ServerLevel) level(), land.x, gy + 1, land.z, getYRot() + 180f, 10f);
        } else e.teleportTo(land.x, gy + 1, land.z);
        e.setDeltaMovement(flat.scale(0.8 + 0.6 * s).add(0, 0.5, 0));
        e.hurtMarked = true;
        e.fallDistance = 0;
        if (e instanceof LivingEntity le) {
            le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0), this);
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), this);
        }
        sound(m, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 2.5f, 0.4f);
        sendParticles(ParticleTypes.SQUID_INK, new Vec3(land.x, gy + 1, land.z), 60, 2 + 3 * s, 1, 0.2);
        if (MountainConfig.V.gooTrail) gooPatch(land.x, land.z, 1.5 + 2 * s, false);
    }

    /** Heart damage from inside: hurts him badly, and enough of it in one visit makes him cough everyone up. */
    public void hurtFromHeart(DamageSource src, float amount) {
        if (isDeadOrDying()) return;
        float dealt = amount * 3f;
        applyDamage(src, dealt, null);
        heartDamageThisVisit += dealt;
        if (heartDamageThisVisit >= healthMax() * 0.08f && !isDeadOrDying()) coughUp();
    }

    public void coughUp() {
        heartDamageThisVisit = 0;
        sound(mouthWorld(), net.jj.mountain.ModSounds.ROAR, 3f, 0.75f);
        Innards.ejectAll(this);
        swallowedPlayers.clear();
    }

    public Set<UUID> swallowedPlayers() { return swallowedPlayers; }

    // ------------------------------------------------------------------ walking
    /** How far (at full size) from the turning point his feet are on average; sets how fast they step in a turn. */
    private static final double TURN_LEVER = 95.0;

    private float maxTurn(float s) { return Mth.clamp(0.55f / (float) Math.sqrt(Math.max(s, 0.05f)), 0.4f, 4f); }

    private void move(@Nullable LivingEntity target) {
        float s = mountainScale();
        // somebody on his head steers him: he goes where they look, as fast as they ask
        if (ridden()) {
            // where you are pushing, relative to where you are looking
            float want = driveYaw;
            if (Math.abs(driveS) > 0.05f && Math.abs(driveF) < 0.05f) want += driveS > 0 ? -90f : 90f;
            else if (Math.abs(driveS) > 0.05f) want += (driveS > 0 ? -35f : 35f);
            if (driveF < -0.05f) want += 180f;
            float diff = Mth.wrapDegrees(want - getYRot());
            float turn = Math.abs(diff) > 2f ? maxTurn(s) * Mth.clamp(diff / 26f, -1f, 1f) : 0f;
            boolean pushing = Math.abs(driveF) > 0.05f || Math.abs(driveS) > 0.05f;
            float go = pushing ? Mth.clamp(1.15f - Math.abs(diff) / 70f, 0.15f, 1f) : 0f;
            if (downTicks > 0 || phaseRoar > 0 || digState != 0) go = 0f;
            steer(s, go, turn);
            return;
        }

        // his own heart, beating in somebody's hands, is the one place he will not go
        Vec3 wardOut = wardEscape();
        if (wardOut == null && goal != null && warded(goal.x, goal.z)) goal = null;
        boolean sentTo = goal != null || wardOut != null;
        Vec3 aim = wardOut != null ? wardOut : goal != null ? goal : target != null ? target.position() : wanderGoal;
        if (target == null && goal == null && !stay) wander();
        float wantMove = 0f, wantVel = 0f;
        float maxTurn = maxTurn(s);
        if (stage == HOLD || (stage == EXHALE && strongBreath) || attacks.holdsStill()) maxTurn *= 0.3f;
        if (aim != null) {
            double dx = aim.x - getX(), dz = aim.z - getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            float diff = Mth.wrapDegrees(want - getYRot());
            boolean chasing = !sentTo && target != null;
            double stopAt = chasing ? 70 * s + 8 : calledBy != null ? arriveRange() * 0.9 : 10 * s + 4;
            if (!chasing && dist < stopAt) {
                if (sentTo) goal = null; else { wanderGoal = null; wanderPause = 100 + random.nextInt(300); }
            } else {
                // ease into the turn and out of it: full speed when far off, slowing as he comes round
                if (Math.abs(diff) > 2f && !attacks.holdsStill()) wantVel = maxTurn * Mth.clamp(diff / 35f, -1f, 1f);
                // he never backs up: a sharp turn is made walking slowly forward or turning on the spot
                if (dist > stopAt) wantMove = Mth.clamp(1.15f - Math.abs(diff) / 70f, 0.12f, 1f);
                if (stay || attacks.holdsStill() || downTicks > 0 || phaseRoar > 0) wantMove = 0f;
            }
        }
        steer(s, wantMove, wantVel);
    }

    /** turns him and carries him along: the same walk whether he chose where to go or you did */
    private void steer(float s, float wantMove, float wantVel) {
        float acc = maxTurn(s) / 28f;
        yawVel += Mth.clamp(wantVel - yawVel, -acc, acc);
        walkAmount += Mth.clamp(wantMove - walkAmount, -0.025f, 0.015f);
        turnAmt += (yawVel / maxTurn(s) - turnAmt) * 0.08f;
        double speed = (0.22 * s + 0.04) * (isHunter() ? 1.35 : 1.0) * phaseSpeed() * walkAmount;
        speed *= Math.max(0.45, 1.0 - 0.07 * brokenLegs());        // dragging broken legs slows him down

        // turn about the middle of his body (further forward the faster he walks), so the head swings one way
        // and the tail the other instead of the whole mountain pivoting on his neck
        float y0 = getYRot(), y1 = y0 + yawVel;
        // flat on his belly he turns about his own middle, not about a point out ahead of him: pivoting round
        // that far-forward point was carrying a knocked-down Mountain several blocks across the ground
        double pz = downTicks > 0 ? 0.0 : Mth.lerp(walkAmount, 75.0, 40.0) * s;
        double a0 = (180f - y0) * Mth.DEG_TO_RAD, a1 = (180f - y1) * Mth.DEG_TO_RAD;
        double cx = pz * (Math.sin(a0) - Math.sin(a1)), cz = pz * (Math.cos(a0) - Math.cos(a1));
        setYRot(y1);
        float yr = y1 * Mth.DEG_TO_RAD;
        double nx = getX() + cx - Mth.sin(yr) * speed, nz = getZ() + cz + Mth.cos(yr) * speed;

        // the walk clock runs with how far his feet are carried, walking or turning; standing, it only runs while
        // some foot still has to be put back under him
        float dPhase = (float) ((speed + Math.abs(yawVel) * Mth.DEG_TO_RAD * TURN_LEVER * s) / (MountainRig.CYCLE_LENGTH * Math.max(s, 0.02f)));
        float idle = 1f / (40f + 60f * s);
        if (dPhase < idle && gait.restless(s)) dPhase = idle;
        if (isDeadOrDying()) dPhase = 0f;
        walkPhase += dPhase;
        if (walkPhase > 1000f) walkPhase -= 1000f;
        dPhaseS = dPhase;
        if (this.tickCount % 2 == 0 || Double.isNaN(groundY)) sampleGround(nx, nz);
        double ny = Double.isNaN(groundY) ? getY() : getY() + Mth.clamp(groundY - getY(), -0.3 - 0.6 * s, 0.3 + 0.6 * s);
        setPos(nx, ny, nz);
    }

    private void wander() {
        float s = mountainScale();
        if (wanderGoal != null) return;
        if (wanderPause > 0) { wanderPause--; return; }
        double a = random.nextDouble() * Math.PI * 2, d = 60 + (60 + 160 * s) * random.nextDouble();
        Vec3 g = position().add(Math.cos(a) * d, 0, Math.sin(a) * d);
        if (level().hasChunkAt(BlockPos.containing(g))) wanderGoal = g;
        else wanderPause = 40;
    }

    /**
     * How high he stands and how his body follows the ground. The ground under the feet of each of the five trunk
     * pieces (tail to neck) gives a height along his length; his middle sits at that height, and each piece leans
     * or bends to follow it, so over a ridge or down into a dip every leg can still reach the ground under it.
     * Across his width one sideways lean is fitted.
     */
    private void sampleGround(double cx, double cz) {
        if (MountainRig.OLD_BODY) { sampleGroundOld(cx, cz); return; }
        float s = mountainScale();
        float yr = (180f - getYRot()) * Mth.DEG_TO_RAD;
        float c = Mth.cos(yr), sn = Mth.sin(yr);
        double[] segZ = new double[5], segH = new double[5]; int[] segN = new int[5];
        double n = 0, sx = 0, sh = 0, sxx = 0, sxh = 0;
        for (MountainRig.LegDef L : rig.legs) {
            if (L.kind == 2) continue;
            Vector3f p = L.foot();
            double wx = cx + (p.x * c + p.z * sn) * s, wz = cz + (-p.x * sn + p.z * c) * s;
            BlockPos bp = BlockPos.containing(wx, getY(), wz);
            if (!level().hasChunkAt(bp)) continue;
            double h = groundAt(bp.getX(), bp.getZ());
            int sg = rig.legSegment(L);
            if (sg >= 0) { segZ[sg] += p.z * s; segH[sg] += h; segN[sg]++; }
            double x = p.x * s;
            n++; sx += x; sh += h; sxx += x * x; sxh += x * h;
        }
        if (n < 4) return;
        // height along his length: through the middle of each piece's feet
        int m = 0; double[] hz = new double[5], hh = new double[5];
        for (int i = 0; i < 5; i++) if (segN[i] > 0) { hz[m] = segZ[i] / segN[i]; hh[m] = segH[i] / segN[i]; m++; }
        if (m == 0) return;
        // (sorted by z: the pieces go tail (big z) to neck (small z))
        for (int i = 0; i < m; i++) for (int j = i + 1; j < m; j++) if (hz[j] < hz[i]) { double t = hz[i]; hz[i] = hz[j]; hz[j] = t; t = hh[i]; hh[i] = hh[j]; hh[j] = t; }
        final int mm = m;
        java.util.function.DoubleUnaryOperator H = z -> {
            if (mm == 1 || z <= hz[0]) return hh[0];
            if (z >= hz[mm - 1]) return hh[mm - 1];
            int i = 0; while (i + 1 < mm && hz[i + 1] < z) i++;
            double f = (z - hz[i]) / Math.max(1e-6, hz[i + 1] - hz[i]);
            return hh[i] + (hh[i + 1] - hh[i]) * f;
        };
        double[] pz = new double[5];
        for (int i = 0; i < 5; i++) pz[i] = rig.segmentPivot(i).z * s;
        // each piece's lean: the slope of the ground under it (front up is positive)
        double[][] span = {{pz[0], pz[0] + 60 * s}, {pz[1], pz[0]}, {pz[3], pz[1]}, {pz[4], pz[3]}, {pz[4] - 50 * s, pz[4]}};
        double[] abs = new double[5];
        for (int i = 0; i < 5; i++) {
            double a = span[i][0], b = span[i][1];
            abs[i] = Math.atan(-(H.applyAsDouble(b) - H.applyAsDouble(a)) / Math.max(1e-3, b - a));
        }
        abs[2] = Mth.clamp(abs[2], -BEND_ROOT, BEND_ROOT);
        double[] rel = new double[5];
        rel[3] = abs[3] - abs[2]; rel[4] = abs[4] - abs[3]; rel[1] = abs[1] - abs[2]; rel[0] = abs[0] - abs[1];
        for (int i : new int[]{0, 1, 3, 4}) {
            float t = (float) Mth.clamp(rel[i] * BEND_GAIN, -BEND_MAX, BEND_MAX);
            segBend[i] += (t - segBend[i]) * 0.15f;
        }
        // how high: the average ground under all his feet (sitting his middle on a hilltop would leave the feet
        // at both ends hanging in the air)
        groundY = (HEIGHT_MID ? H.applyAsDouble(pz[2]) : sh / n) - 0.25 * s;
        bodyPitch += ((float) abs[2] - bodyPitch) * 0.15f;
        // across his width: one sideways lean (his right side higher tips his right side up)
        double mx = sx / n, mh = sh / n, vxx = sxx / n - mx * mx, vxh = sxh / n - mx * mh;
        double bx = vxx > 1e-6 ? vxh / vxx : 0;
        float tr = (float) Mth.clamp(Math.atan(bx), -0.3, 0.3);
        bodyRoll += (tr - bodyRoll) * 0.15f;
    }

    /** dev only: the way it was before 1.7, to compare */
    private void sampleGroundOld(double cx, double cz) {
        float s = mountainScale();
        float yr = (180f - getYRot()) * Mth.DEG_TO_RAD;
        float c = Mth.cos(yr), sn = Mth.sin(yr);
        double front = 0, back = 0, right = 0, left = 0; int nf = 0, nb = 0, nr = 0, nl = 0, ok = 0; double sum = 0;
        for (MountainRig.LegDef L : rig.legs) {
            if (L.kind == 2 || (L.k % 3) != 0) continue;
            Vector3f p = L.foot();
            double wx = cx + (p.x * c + p.z * sn) * s, wz = cz + (-p.x * sn + p.z * c) * s;
            BlockPos bp = BlockPos.containing(wx, getY(), wz);
            if (!level().hasChunkAt(bp)) continue;
            double h = groundAt(bp.getX(), bp.getZ());
            sum += h; ok++;
            if (L.u > 0.5f) { front += h; nf++; } else { back += h; nb++; }
            if (L.side > 0) { right += h; nr++; } else { left += h; nl++; }
        }
        if (ok == 0) return;
        groundY = sum / ok - 0.25 * s;
        if (nf > 0 && nb > 0) {
            float tp = (float) Mth.clamp(Math.atan2(front / nf - back / nb, 120 * Math.max(s, 0.02)), -0.22, 0.22);
            bodyPitch += (tp - bodyPitch) * 0.15f;
        }
        if (nr > 0 && nl > 0) {
            float tr = (float) Mth.clamp(Math.atan2(right / nr - left / nl, 110 * Math.max(s, 0.02)), -0.18, 0.18);
            bodyRoll += (-tr - bodyRoll) * 0.15f;
        }
    }

    /**
     * Top of the real ground: through water, lava, leaves, logs, plants and his own goo.
     * The game's height maps can't be trusted on the client: there they can read as the bottom of the world even in
     * chunks you can see, and legs drawn from that reached for bedrock anywhere the ground wasn't flat. So when the
     * height map looks wrong (nothing solid at the top it gives), the ground is found from the blocks themselves,
     * coming down from high above him. Columns found that way are kept for a couple of seconds.
     */
    public int groundAt(int x, int z) {
        Level lv = level();
        int min = lv.getMinBuildHeight();
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        if (lv.isClientSide) {
            if (tickCount - groundCacheTick > 40) { groundCache.clear(); groundCacheTick = tickCount; }
            int c = groundCache.getOrDefault(key, Integer.MIN_VALUE);
            if (c != Integer.MIN_VALUE) return c;
        }
        // asking the height of a column in a chunk that isn't there makes the game build that chunk, so a stray
        // faraway coordinate would generate terrain mid-tick. Out there, his own footing is the best guess.
        if (!lv.hasChunk(x >> 4, z >> 4) && (Math.abs(x - getX()) > 256 || Math.abs(z - getZ()) > 256)) return Mth.floor(getY());
        int top = lv.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, top - 1, z);
        if (top <= min + 1 || lv.getBlockState(m).isAir()) {
            int start = Math.min(lv.getMaxBuildHeight() - 1, Mth.floor(getY()) + (int) (200 * mountainScale()) + 40);
            m.setY(start);
            while (m.getY() > min && lv.getBlockState(m).isAir()) m.move(0, -1, 0);
        }
        for (int i = 0; i < 64 && m.getY() > min; i++) {
            BlockState st = lv.getBlockState(m);
            if (st.getFluidState().isEmpty() && !st.is(BlockTags.LOGS) && !st.is(BlockTags.LEAVES) && !st.canBeReplaced() && st.blocksMotion()) break;
            m.move(0, -1, 0);
        }
        int g = m.getY() + 1;
        if (lv.isClientSide) groundCache.put(key, g);
        return g;
    }
    private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap groundCache = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
    private int groundCacheTick;

    // ------------------------------------------------------------------ head and eyes
    private void lookAround(@Nullable LivingEntity target) {
        float s = mountainScale();
        // the head follows what he is after (or idly sways)
        float wantYaw, wantPitch;
        LivingEntity at = hurtByT > 0 && hurtBy != null && hurtBy.isAlive() ? hurtBy : target;
        if (at != null) target = at;
        if (target != null) {
            Vec3 mp = worldToModelPoint(target.getEyePosition());
            Vector3f base = rig.pivot[rig.head];
            double dx = mp.x - base.x, dy = mp.y - base.y, dz = mp.z - base.z;
            wantYaw = (float) Math.atan2(-dx, -dz);
            wantPitch = (float) (Math.atan2(dy, Math.hypot(dx, dz)) - 0.15);
        } else {
            wantYaw = 0.22f * Mth.sin(tickCount * 0.009f);
            wantPitch = 0.06f * Mth.sin(tickCount * 0.013f);
        }
        wantYaw = Mth.clamp(wantYaw, -0.45f, 0.45f);
        wantPitch = Mth.clamp(wantPitch, -0.32f, 0.22f);
        float r = hurtByT > 40 ? 0.075f : 0.012f;            // a hit snaps his head round, then he goes back to watching
        headYaw += Mth.clamp(wantYaw - headYaw, -r, r);
        headPitch += Mth.clamp(wantPitch - headPitch, -r, r);
        // every eye follows the nearest player, calm or not
        Player best = null; double bd = sq(160 * s + 40);
        for (Player p : level().players()) {
            if (p.isSpectator() || !p.isAlive() || Innards.isInside(p)) continue;
            double d = p.distanceToSqr(this);
            if (d < bd) { bd = d; best = p; }
        }
        Entity watch = target != null ? target : best;
        if (watch != null) {
            Vec3 mp = worldToModelPoint(watch.getEyePosition());
            if (lookAmt < 0.02f) look.set((float) mp.x, (float) mp.y, (float) mp.z);
            look.lerp(new Vector3f((float) mp.x, (float) mp.y, (float) mp.z), 0.3f);
            lookAmt = Math.min(1f, lookAmt + 0.04f);
        } else lookAmt = Math.max(0f, lookAmt - 0.02f);

        // everything else worth an eye: his eyes divide themselves between them, so different eyes follow different
        // things at the same time instead of all of them staring at one
        if (tickCount % 10 == 0 && forceWatch <= 0) {
            watchList.clear();
            double watchR = 150 * s + 50;
            List<LivingEntity> near = level().getEntitiesOfClass(LivingEntity.class, getBoundingBoxForCulling().inflate(watchR),
                    e -> e != this && e.isAlive() && !(e instanceof MountainEntity) && !(e instanceof HeartEntity)
                            && !Innards.isInside(e) && !(e instanceof Player p2 && p2.isSpectator()));
            near.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(this)));
            for (LivingEntity e : near) {
                if (e == watch || spares(e)) continue;
                watchList.add(e);
                if (watchList.size() >= 3) break;
            }
        }
        watchList.removeIf(e -> !e.isAlive());
        if (forceWatch > 0) forceWatch--;
        lookCount = 1 + watchList.size();
        for (int i = 0; i < looks.length; i++) {
            LivingEntity e = i < watchList.size() ? watchList.get(i) : null;
            if (e == null) { looks[i].set(look); continue; }
            Vec3 mp = worldToModelPoint(e.getEyePosition());
            Vector3f want = new Vector3f((float) mp.x, (float) mp.y, (float) mp.z);
            if (looks[i].lengthSquared() < 1e-6f) looks[i].set(want); else looks[i].lerp(want, 0.3f);
        }
    }

    /**
     * Put a whole set of things in front of his eyes at once, for the eye storm: every one of them gets a line
     * held on it rather than the crowd sharing one look.
     */
    private int forceWatch;
    void watchAll(java.util.List<LivingEntity> them) {
        if (them.isEmpty()) return;
        watchList.clear();
        for (LivingEntity e : them) {
            if (e == null || !e.isAlive()) continue;
            watchList.add(e);
            if (watchList.size() >= 3) break;
        }
        forceWatch = 12;
    }

    // ------------------------------------------------------------------ the forest of arms
    private void arms() {
        float s = mountainScale();
        // while he is carrying a driver, or reaching down for one, the arms are already busy: the seat sets
        // where they fold in to, and nothing on his back is something to pick up
        boolean cradling = this.rider != null || (fetch != null && fetchState >= 2);
        // something on his back?
        LivingEntity rider = null;
        if (held == null && tickCount % 4 == 0) {
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBoxForCulling(), this::canBeGrabbed)) {
                if (onHisBack(e)) { rider = e; break; }
            }
            riderCache = rider;
        } else rider = held == null ? riderCache : null;
        if (rider != null && (!rider.isAlive() || !onHisBack(rider))) { rider = null; riderCache = null; }
        if (attacks.controlsArms()) rider = null;
        if (held != null && !tongueHold) advanceHold();          // whatever is in his fist keeps moving along it
        if (cradling) { /* the seat has the arms */ }
        else if (held != null) { reachAmt = Math.max(0f, reachAmt - 0.05f); }
        else if (rider != null) {
            Vec3 mp = worldToModelPoint(rider.getBoundingBox().getCenter());
            reach.lerp(new Vector3f((float) mp.x, (float) mp.y, (float) mp.z), reachAmt < 0.05f ? 1f : 0.25f);
            reachAmt = Math.min(1f, reachAmt + 0.04f);
            if (reachAmt > 0.5f) {
                Vec3 c = rider.getBoundingBox().getCenter();
                for (MountainRig.ArmDef A : rig.arms) {
                    if (armBroken(A.k)) continue;
                    Vec3 hw = toWorld(A.handPoint, pose[A.hand]);
                    if (hw.distanceTo(c) < 5.5 * s + 2.2) { grab(rider, A.k); break; }
                }
            }
        } else if (!attacks.controlsArms()) reachAmt = Math.max(0f, reachAmt - 0.03f);
        // mouths in the palms bite whatever comes near
        if (tickCount % 5 == 0) {
            for (MountainRig.ArmDef A : rig.arms) {
                if (A.extra != 1 || armBroken(A.k)) continue;
                Vec3 hw = toWorld(A.handPoint, pose[A.hand]);
                for (LivingEntity e : victims(hw, 4.5 * s + 2)) {
                    if (e == held) continue;
                    Integer cd = biteCooldown.get(e.getId());
                    if (cd != null && tickCount - cd < 20) continue;
                    biteCooldown.put(e.getId(), tickCount);
                    if (e.hurt(damageSources().mobAttack(this), dmg(3 + 4 * s, e))) sound(hw, SoundEvents.EVOKER_FANGS_ATTACK, 1.2f, 0.6f);
                }
            }
            if (biteCooldown.size() > 64) biteCooldown.clear();
        }
    }
    private @Nullable LivingEntity riderCache;

    private boolean canBeGrabbed(LivingEntity e) {
        if (spares(e) || sleeping() || noGrab > 0) return false;
        if (e == rider) return false;                      // whoever is steering him is not something to pick up
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator() && e.isAlive();
        return e.isAlive() && !(e instanceof MountainEntity) && !(e instanceof HeartEntity) && e.getBbWidth() < 4f;
    }

    /** Standing on him, climbing him, or flying low over his back. */
    public boolean onHisBack(Entity e) {
        float s = Math.max(mountainScale(), 0.02f);
        Vec3 mp = worldToModelPoint(e.position());
        for (MountainRig.BodySample b : rig.body) {
            if (Math.abs(mp.z - b.p().z) > 12) continue;
            double side = Math.abs(mp.x - b.p().x);
            double up = mp.y - (b.p().y + state.bodyLift);
            if (side < b.r() * 1.1 && up > -b.r() * 0.15 && up < b.r() + 30) return true;
        }
        Vec3 hp = worldToModelPoint(e.position());
        Vector3f hc = rig.headCenter;
        return Math.hypot(Math.hypot(hp.x - hc.x, hp.z - hc.z), hp.y - hc.y) < 58 && hp.y > hc.y + 10;
    }

    private void grab(LivingEntity e, int arm) {
        struggle = 0f;
        if (held != null || isDeadOrDying()) return;
        held = e;
        holdArm = arm;
        holdNext = nextArm(arm);
        holdT = 0f;
        seat = new GripSeat(level(), this);
        Vec3 hw = handWorld(arm);
        seat.setPos(hw.x, hw.y - e.getBbHeight() * 0.5, hw.z);
        level().addFreshEntity(seat);
        e.stopRiding();
        e.startRiding(seat, true);
        sound(hw, SoundEvents.SLIME_ATTACK, 2.0f, 0.4f);
        if (e instanceof Player p) p.displayClientMessage(Component.translatable("message.mountain_breathes.grabbed"), true);
    }

    private int nextArm(int from) {
        MountainRig.ArmDef A = rig.arms[from];
        int best = -1; double bd = 75 * 75;
        for (MountainRig.ArmDef B : rig.arms) {
            if (B.u < A.u + 0.03f) continue;
            double d = A.handPoint.distanceSquared(B.handPoint) * (1 + (B.u - A.u) * 0.5);
            if (d < bd) { bd = d; best = B.k; }
        }
        return best;
    }

    private void advanceHold() {
        if (held == null) return;
        if (!held.isAlive() || held.isRemoved() || (held instanceof Player p && (p.isCreative() || p.isSpectator())) || held.level() != level()) { releaseHeld(true); return; }
        holdT += 1f / 22f;
        if (holdT >= 1f) {
            if (holdNext < 0) { Entity e = held; releaseHeld(false); swallow(e); return; }
            holdArm = holdNext; holdNext = nextArm(holdArm); holdT = 0f;
            held.hurt(damageSources().mobAttack(this), 1.0f);
            sound(handWorld(holdArm), SoundEvents.SLIME_SQUISH, 1.5f, 0.5f);
        }
    }

    /** Keep the held thing in the holding hand (runs every tick, after the pose). */
    private void carryHeld() {
        if (held == null || seat == null || holdArm < 0 || tongueHold) return;
        Vec3 hw = handWorld(holdArm);
        seat.moveTo(hw.x, hw.y - held.getBbHeight() * 0.5 - 0.3, hw.z);
        if (!held.isPassenger() || held.getVehicle() != seat) {
            if (held.isAlive() && !held.isRemoved()) held.startRiding(seat, true);
        }
        held.fallDistance = 0;
    }

    private void releaseHeld(boolean drop) {
        tongueHold = false;
        if (held != null) { held.stopRiding(); held.fallDistance = 0; }
        if (seat != null) { seat.discard(); seat = null; }
        held = null; holdArm = holdNext = -1; holdT = 0;
    }

    public @Nullable Entity held() { return held; }

    // ------------------------------------------------------------------ the tongue's catch
    /** the tongue has hold of something (not a hand) */
    // ------------------------------------------------------------------ fighting your way out of his hand
    private float struggle;
    /** ticks after someone tears free in which he can't grab again */
    private int noGrab;

    /** how hard you have to fight to get out of this one's hand */
    public float struggleNeeded() { return 14f + 26f * mountainScale(); }
    public float struggleSoFar() { return struggle; }

    /** the player being held has hit the key: a few more of those and the fingers come apart */
    public void struggleOut(LivingEntity who, int presses) {
        if (held != who || presses <= 0) return;
        struggle += Math.min(presses, 6);
        Vec3 hw = holdArm >= 0 ? handWorld(holdArm) : who.position();
        sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PINK_TERRACOTTA.defaultBlockState()), hw, 8, 1.2, 0.6, 0.1);
        if (struggle < struggleNeeded()) {
            if (who instanceof ServerPlayer sp && who.tickCount % 4 == 0)
                sp.displayClientMessage(Component.translatable("message.mountain_breathes.struggle",
                        Math.round(100 * Math.min(1f, struggle / struggleNeeded()))), true);
            return;
        }
        // free: the hand snaps open and throws you clear
        Vec3 away = who.position().subtract(position()).normalize().add(0, 0.55, 0).normalize();
        releaseHeld(true);
        noGrab = 60;
        who.setDeltaMovement(away.scale(0.5 + 0.5 * mountainScale()));
        who.hurtMarked = true;
        who.fallDistance = 0f;
        sound(hw, net.jj.mountain.ModSounds.HURT, 2f, 1.3f);
        if (who instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("message.mountain_breathes.broke_free"), true);
    }

    private boolean tongueHold;
    boolean tongueHolding() { return tongueHold && held != null; }
    boolean canBeTongued(LivingEntity e) { return held == null && canBeGrabbed(e) && !Innards.isInside(e); }

    void tongueGrab(LivingEntity e) {
        if (held != null || isDeadOrDying()) return;
        held = e; tongueHold = true; holdArm = holdNext = -1; holdT = 0f;
        seat = new GripSeat(level(), this);
        seat.setPos(e.getX(), e.getY(), e.getZ());
        level().addFreshEntity(seat);
        e.stopRiding();
        e.startRiding(seat, true);
        if (e instanceof Player p) p.displayClientMessage(Component.translatable("message.mountain_breathes.tongue"), true);
    }

    void moveTongueCatch(Vec3 tip) {
        if (!tongueHolding() || seat == null) return;
        if (!held.isAlive() || held.isRemoved() || held.level() != level()) { letGoOfTongue(); return; }
        seat.moveTo(tip.x, tip.y - held.getBbHeight() * 0.5, tip.z);
        if (!held.isPassenger() || held.getVehicle() != seat) held.startRiding(seat, true);
        held.fallDistance = 0;
    }

    void swallowFromTongue() {
        if (!tongueHolding()) { tongueHold = false; return; }
        Entity e = held;
        tongueHold = false;
        releaseHeld(false);
        swallow(e);
    }

    void letGoOfTongue() { if (tongueHold) { tongueHold = false; releaseHeld(true); } }

    // ------------------------------------------------------------------ goo
    private void pourGoo() {
        float s = mountainScale();
        if (isDeadOrDying() && deathTime > 150) return;
        int n = s < 0.3f ? 1 : 2;
        for (int k = 0; k < n; k++) {
            MountainRig.GooDef g = rig.goo[random.nextInt(rig.goo.length)];
            Vec3 tip = toWorld(g.tip(), pose[g.bone()]);
            gooPatch(tip.x + (random.nextDouble() - 0.5) * 3 * s, tip.z + (random.nextDouble() - 0.5) * 3 * s, 0.6 + 2.4 * s * random.nextDouble(), true);
        }
    }

    /** A splash of goo on the ground: a disc of goo blocks, and at the middle the ground itself gets eaten a block deep. */
    public void gooPatch(double x, double z, double r, boolean eat) {
        if (!MountainConfig.V.gooTrail) return;
        int cx = Mth.floor(x), cz = Mth.floor(z);
        if (!level().hasChunkAt(new BlockPos(cx, 0, cz))) return;
        int gy = groundAt(cx, cz);
        BlockState goo = ModBlocks.GOO.defaultBlockState();
        int ri = Mth.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) for (int dz = -ri; dz <= ri; dz++) {
            if (dx * dx + dz * dz > r * r + 0.5) continue;
            int x2 = cx + dx, z2 = cz + dz;
            int y2 = groundAt(x2, z2);
            if (Math.abs(y2 - gy) > 3) continue;
            BlockPos p = new BlockPos(x2, y2, z2);
            BlockState at = level().getBlockState(p);
            boolean center = dx == 0 && dz == 0;
            if (at.is(ModBlocks.GOO)) {
                if (center && eat && canGrief() && !at.getValue(GooBlock.EATEN)) eatDown(p, at);
                else if (at.getValue(GooBlock.AGE) > 0) level().setBlock(p, at.setValue(GooBlock.AGE, 0), 2);
                continue;
            }
            if (!at.isAir() && !(at.canBeReplaced() && at.getFluidState().isEmpty())) continue;
            if (!goo.canSurvive(level(), p)) continue;
            level().setBlock(p, goo, 3);
            if (center && eat && canGrief()) eatDown(p, goo);
        }
        if (random.nextInt(4) == 0) {
            Vec3 at = new Vec3(x, gy + 0.3, z);
            sendParticles(ParticleTypes.SQUID_INK, at, 6, r * 0.5, 0.2, 0.05);
            if (random.nextInt(3) == 0) sound(at, net.jj.mountain.ModSounds.GOO, 0.5f, 0.85f);
        }
    }

    /** The goo eats the block under it (soft ground only), one block deep. */
    private void eatDown(BlockPos gooPos, BlockState gooState) {
        BlockPos below = gooPos.below();
        BlockState b = level().getBlockState(below);
        float hard = b.getDestroySpeed(level(), below);
        if (hard < 0 || hard > 3.0f || b.hasBlockEntity() || b.is(Blocks.BEDROCK) || b.is(Blocks.OBSIDIAN) || b.is(ModBlocks.GOO)) return;
        BlockPos below2 = below.below();
        if (!level().getBlockState(below2).isFaceSturdy(level(), below2, net.minecraft.core.Direction.UP)) return;
        level().setBlock(gooPos, Blocks.AIR.defaultBlockState(), 2);
        level().setBlock(below, gooState.setValue(GooBlock.EATEN, true).setValue(GooBlock.AGE, 0), 3);
    }

    // ------------------------------------------------------------------ feet
    private void footsteps() {
        if (attacks.id == RigState.WHIP) gait.setSweep(attacks.arg, attacks.t / (float) MountainAttacks.length(RigState.WHIP), attacks.aim.x, attacks.aim.z);
        else if (attacks.id == RigState.STOMP) gait.setStomp(attacks.arg, attacks.t / (float) MountainAttacks.length(RigState.STOMP), attacks.aim.x, attacks.aim.z);
        else gait.setWhip(-1, 0f, 0, 0);
        gait.setBroken(legsBroken);
        gait.tick(getX(), getY(), getZ(), getYRot(), mountainScale(), walkPhase, dPhaseS, tickCount, isDeadOrDying(), Math.max(sleepAmt, downAmt),
                this::groundOrNaN, (k, x, y, z) -> footstep(rig.legs[k], new Vec3(x, y, z)));
    }

    /** ground height for the feet, or NaN where the world isn't loaded */
    public double groundOrNaN(double x, double z) {
        BlockPos bp = BlockPos.containing(x, getY(), z);
        if (!level().hasChunkAt(bp)) return Double.NaN;
        int g = groundAt(bp.getX(), bp.getZ());
        // nothing but air down to the bottom of the world: on the client that means the chunk's blocks haven't
        // arrived yet, not that the ground is down there
        if (level().isClientSide && g <= level().getMinBuildHeight() + 1) return Double.NaN;
        return g;
    }

    /** where leg L's foot is right now */
    public Vec3 footWorld(MountainRig.LegDef L) {
        return gait.ready() ? new Vec3(gait.x[L.k], gait.y[L.k] - L.endY * mountainScale(), gait.z[L.k]) : toWorld(L.foot(), pose[L.bones[2]]);
    }

    public boolean footLifted(int k) { return gait.swinging(k); }

    private void footstep(MountainRig.LegDef L, Vec3 at) {
        float s = mountainScale();
        int gy = groundAt(Mth.floor(at.x), Mth.floor(at.z));
        at = new Vec3(at.x, gy, at.z);
        BlockState ground = level().getBlockState(BlockPos.containing(at.x, gy - 1, at.z));
        if (ground.isAir()) ground = Blocks.DIRT.defaultBlockState();
        sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at, (int) (8 + 30 * s), 3 * s + 0.5, 0.6, 0.2);
        sound(at, net.jj.mountain.ModSounds.STEP, 1.4f, 1.25f - 0.55f * Math.min(1f, s) + random.nextFloat() * 0.06f);
        double r = 3 + 6 * s;
        for (LivingEntity e : victims(at.add(0, r * 0.3, 0), r)) {
            if (e.getY() > at.y + 2 + 4 * s || e == held) continue;
            if (e.hurt(damageSources().mobAttack(this), dmg(4 + 7 * s, e))) {
                e.knockback(0.5 + 0.8 * s, at.x - e.getX(), at.z - e.getZ());
                e.hurtMarked = true;
            }
        }
        if (canGrief()) trample(at, 2 + 5 * s, (int) (4 + 10 * s));
        snapTrees(at, 3 + 7 * s, (int) (10 + 22 * s));
        splash(at, 6 + 14 * s);
    }

    /** a foot coming down in a wood: the trees under it snap */
    public void snapTrees(Vec3 at, double r, int height) {
        if (!canGrief()) return;
        float s = mountainScale();
        int ri = Mth.ceil(r), budget = 400, broke = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState wood = null;
        for (int dx = -ri; dx <= ri && budget > 0; dx++) for (int dz = -ri; dz <= ri && budget > 0; dz++) {
            if (dx * dx + dz * dz > r * r) continue;
            for (int dy = height; dy >= -1 && budget > 0; dy--) {
                m.set(at.x + dx, at.y + dy, at.z + dz);
                BlockState st = level().getBlockState(m);
                if (st.isAir() || st.is(ModBlocks.GOO)) continue;
                if (st.is(BlockTags.LOGS) || st.is(BlockTags.LEAVES) || st.is(Blocks.MUSHROOM_STEM)
                        || st.is(Blocks.BROWN_MUSHROOM_BLOCK) || st.is(Blocks.RED_MUSHROOM_BLOCK) || st.is(BlockTags.WART_BLOCKS)) {
                    if (st.is(BlockTags.LOGS) && wood == null) wood = st;
                    level().destroyBlock(m.immutable(), false, this);
                    budget--; broke++;
                }
            }
        }
        if (broke > 6) {
            sound(at, SoundEvents.WOOD_BREAK, 3.0f, 0.45f);
            if (wood != null) sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, wood), at.add(0, 3 * s, 0), (int) (20 + 40 * s), r * 0.5, 2.0 * s + 0.5, 0.25);
        }
    }

    /** a foot coming down in water: it throws the water up and shoves anything swimming in it */
    public void splash(Vec3 at, double r) {
        float s = mountainScale();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z));
        int top = Integer.MIN_VALUE;
        for (int i = 0; i < 64; i++) {
            m.setY(Mth.floor(at.y) + i);
            if (level().getFluidState(m).is(net.minecraft.tags.FluidTags.WATER)) top = m.getY();
            else if (top != Integer.MIN_VALUE) break;
        }
        if (top == Integer.MIN_VALUE) return;
        Vec3 sp = new Vec3(at.x, top + 1, at.z);
        sendParticles(ParticleTypes.SPLASH, sp, (int) (50 + 150 * s), r * 0.5, 0.8, 0.35);
        sendParticles(ParticleTypes.BUBBLE, sp, (int) (20 + 60 * s), r * 0.45, 0.6, 0.2);
        sound(sp, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 3.0f, 0.5f);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(sp, sp).inflate(r), e -> e != this && e.isAlive() && e.isInWater())) {
            if (spares(e)) continue;
            e.knockback(0.5 + 0.7 * s, sp.x - e.getX(), sp.z - e.getZ());
            e.setDeltaMovement(e.getDeltaMovement().add(0, 0.35 + 0.3 * s, 0));
            e.hurtMarked = true;
        }
    }

    /** the same flattening, for anything that isn't him: leaves, flowers, snow, cane, whatever is loose */
    public static void flattenAt(ServerLevel lvl, double x, double z, double r, int height) {
        int ri = Mth.ceil(r), budget = 90;
        int y0 = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -ri; dx <= ri && budget > 0; dx++) for (int dz = -ri; dz <= ri && budget > 0; dz++) {
            if (dx * dx + dz * dz > r * r) continue;
            for (int dy = height; dy >= -2 && budget > 0; dy--) {
                m.set(x + dx, y0 + dy, z + dz);
                if (!lvl.hasChunkAt(m)) break;
                BlockState st = lvl.getBlockState(m);
                if (st.isAir() || st.is(ModBlocks.GOO)) continue;
                if (st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS) || st.is(BlockTags.FLOWERS) || st.is(BlockTags.SNOW)
                        || st.is(BlockTags.SAPLINGS) || (st.canBeReplaced() && st.getFluidState().isEmpty())
                        || st.is(Blocks.SUGAR_CANE) || st.is(Blocks.BAMBOO)) {
                    lvl.destroyBlock(m.immutable(), false);
                    budget--;
                }
            }
        }
    }

    void trample(Vec3 at, double r, int height) {
        int ri = Mth.ceil(r), budget = 300;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -ri; dx <= ri && budget > 0; dx++) for (int dz = -ri; dz <= ri && budget > 0; dz++) {
            if (dx * dx + dz * dz > r * r) continue;
            for (int dy = height; dy >= -1 && budget > 0; dy--) {
                m.set(at.x + dx, at.y + dy, at.z + dz);
                BlockState st = level().getBlockState(m);
                if (st.isAir() || st.is(ModBlocks.GOO)) continue;
                if (st.is(BlockTags.LEAVES) || st.is(BlockTags.FLOWERS) || st.is(BlockTags.SNOW) || st.is(BlockTags.SAPLINGS)
                        || (st.canBeReplaced() && st.getFluidState().isEmpty()) || st.is(Blocks.SUGAR_CANE) || st.is(Blocks.BAMBOO)) {
                    level().destroyBlock(m.immutable(), false, this);
                    budget--;
                }
            }
        }
    }

    // ------------------------------------------------------------------ down his throat
    /** true on both sides: he is flat on his belly */
    public boolean downNow() { return level().isClientSide ? curC.down > 0.25f : downTicks > 0; }

    /** how far open his face is, on either side */
    public float mouthOpen() { return level().isClientSide ? curC.mouthOpen : mouth; }

    /** the point down his throat that a shot has to reach, or null while his face is shut */
    public @Nullable Vec3 throat() {
        if (isDeadOrDying() || sleeping() || mouthOpen() < 0.45f) return null;
        return mouthWorld().add(mouthDir().scale(-7 * mountainScale()));
    }

    /** how wide a target the inside of his mouth is */
    public double throatRadius() { return 2.2 + 7.5 * mountainScale(); }

    /** did this go in his mouth rather than bouncing off his hide? */
    private boolean downTheThroat(DamageSource src) {
        Vec3 w = throat();
        if (w == null) return false;
        double r = throatRadius();
        Entity direct = src.getDirectEntity();
        // an arrow or anything else thrown has to actually be in there
        if (direct instanceof Projectile) return direct.position().distanceToSqr(w) < r * r;
        // swinging into his open face counts too, if you are looking down it
        if (direct instanceof Player pl) {
            Vec3 from = pl.getEyePosition();
            Vec3 to = from.add(pl.getViewVector(1f).scale(pl.entityInteractionRange() + 3.0 + 26 * mountainScale()));
            return distToSegment(w, from, to) < r;
        }
        return false;
    }

    private static double distToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len = ab.lengthSqr();
        double t = len < 1e-9 ? 0 : Mth.clamp(p.subtract(a).dot(ab) / len, 0, 1);
        return p.distanceTo(a.add(ab.scale(t)));
    }

    /** a hit that went straight in past his teeth */
    public boolean hurtThroat(DamageSource src, float amount) {
        Vec3 w = throat();
        if (w == null) return takeDamage(src, amount);
        float s = mountainScale();
        sendParticles(ParticleTypes.CRIT, w, 40, 1.5 + 3 * s, 1.5 + 3 * s, 0.35);
        sendParticles(ParticleTypes.DAMAGE_INDICATOR, w, 20, 1.5 + 3 * s, 1.5 + 3 * s, 0.25);
        sound(w, net.jj.mountain.ModSounds.HURT, 3f, 0.8f);
        return takeDamage(src, amount * THROAT_MULT);
    }

    /** how much worse a hit down his throat is than one on his hide */
    public static final float THROAT_MULT = 6f;

    // ------------------------------------------------------------------ taking damage
    public boolean hurtFromPart(MountainPart part, DamageSource src, float amount) {
        if (level().isClientSide || amount <= 0) return false;
        if (isDeadOrDying() || isInvulnerableTo(src)) return false;
        Entity att = src.getEntity(), direct = src.getDirectEntity();
        if (att == this || att instanceof MountainPart || att instanceof MountainEntity) return false;
        if (downTheThroat(src)) return hurtThroat(src, amount);
        MountainRig.PartDef pd = part.def();
        if (pd.leg() >= 0 && pd.leg() < legHp.length) return hurtLeg(pd.leg(), src, amount);
        flinch(pd.bone());
        int arm = armHitBy(src);
        if (arm >= 0) return hurtArm(arm, src, amount);
        int eye = -1;
        if (direct instanceof Player pl) eye = eyeOnRay(pl.getEyePosition(), pl.getViewVector(1f), pl.entityInteractionRange() + 2.5);
        else if (direct instanceof Projectile pr) eye = eyeNear(pr.position(), 2.0);
        else if (direct instanceof LivingEntity le) eye = eyeNear(le.getEyePosition(), 3.0);
        return applyDamage(src, amount, eye >= 0 ? eye : null);
    }

    // ------------------------------------------------------------------ legs and arms that can be broken
    public float legHpMax() { return Math.max(20f, healthMax() * 0.015f); }
    public float armHpMax() { return Math.max(12f, healthMax() * 0.008f); }
    public boolean legBroken(int k) { return k >= 0 && k < 64 && (legsBroken >>> k & 1L) != 0L; }
    public boolean legScarred(int k) { return k >= 0 && k < 64 && (legsScarred >>> k & 1L) != 0L; }
    public boolean legRuined(int k) { return k >= 0 && k < 64 && (legsRuined >>> k & 1L) != 0L; }
    public boolean armScarred(int k) { return k < 0 ? false : k < 64 ? (armsScarred0 >>> k & 1L) != 0L : (armsScarred1 >>> (k - 64) & 1L) != 0L; }
    public boolean armRuined(int k) { return k < 0 ? false : k < 64 ? (armsRuined0 >>> k & 1L) != 0L : (armsRuined1 >>> (k - 64) & 1L) != 0L; }
    public int scarredLegs() { return Long.bitCount(legsScarred); }
    public int ruinedLegs() { return Long.bitCount(legsRuined); }
    public int scarredArms() { return Long.bitCount(armsScarred0) + Long.bitCount(armsScarred1); }
    public int ruinedArms() { return Long.bitCount(armsRuined0) + Long.bitCount(armsRuined1); }
    /** he can never be more than a third ruined: past that a scarred limb just breaks and knits again */
    private int ruinCapLegs() { return Math.max(1, rig.legs.length / 3); }
    private int ruinCapArms() { return Math.max(1, rig.arms.length / 3); }
    /** what a scarred limb can take: it knitted, but not the way it was */
    public float legHpMax(int k) { return legHpMax() * (legScarred(k) ? 0.45f : 1f); }
    public float armHpMax(int k) { return armHpMax() * (armScarred(k) ? 0.45f : 1f); }

    // ------------------------------------------------------------------ down for good
    /** how many legs have to be gone before he stays on his belly */
    public int crippleAt() { return Math.max(2, Math.round(rig.legs.length * Mth.clamp(MountainConfig.V.crippleAt, 0.2f, 1f))); }
    /** enough of his legs are gone that he will not get up again, and he knows it */
    public boolean crippled() { return brokenLegs() >= crippleAt(); }
    public boolean armBroken(int k) { return k < 0 ? false : k < 64 ? (armsBroken0 >>> k & 1L) != 0L : (armsBroken1 >>> (k - 64) & 1L) != 0L; }
    public int brokenLegs() { return Long.bitCount(legsBroken); }
    public int brokenArms() { return Long.bitCount(armsBroken0) + Long.bitCount(armsBroken1); }
    public boolean knockedDown() { return downTicks > 0; }

    // ------------------------------------------------------------------ what his eyes can reach
    /**
     * Can any of his open eyes, or his head, see that thing from here? His own goo does not count: it lies all
     * around him and would blind him. This is the one thing the shadow stands on, so it lives out here where
     * anything can ask.
     */
    public boolean canSee(@Nullable Entity e) {
        if (e == null || e.level() != level()) return false;
        Vec3 to = e.getEyePosition();
        for (int k = 0; k < rig.eyes.length; k += 23) {
            if (isEyePopped(k)) continue;
            if (clearTo(eyeWorld(k), to)) return true;
        }
        return clearTo(toWorld(rig.headCenter, pose[rig.head]), to);
    }

    private boolean clearTo(Vec3 from, Vec3 to) {
        Vec3 f = from;
        for (int i = 0; i < 6; i++) {
            net.minecraft.world.phys.HitResult h = level().clip(new net.minecraft.world.level.ClipContext(
                    f, to, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
            if (h.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return true;
            if (!(h instanceof net.minecraft.world.phys.BlockHitResult bh)
                    || !level().getBlockState(bh.getBlockPos()).is(net.jj.mountain.ModBlocks.GOO)) return false;
            Vec3 d = to.subtract(f);
            double len = d.length();
            if (len < 1.5) return true;
            f = h.getLocation().add(d.scale(1.05 / len));
        }
        return false;
    }

    // ------------------------------------------------------------------ his own heart holding him off
    /** is that spot inside the circle his heart is keeping him out of? */
    public boolean warded(double wx, double wz) {
        if (level().isClientSide || !(level() instanceof ServerLevel sl) || sl.getServer() == null) return false;
        return net.jj.mountain.world.MountainWorld.get(sl.getServer().overworld()).warded(level(), wx, wz);
    }

    /** if he is standing inside it, a spot outside the edge to make for; otherwise nothing */
    private @Nullable Vec3 wardEscape() {
        if (!warded(getX(), getZ())) return null;
        if (!(level() instanceof ServerLevel sl)) return null;
        var at = net.jj.mountain.world.MountainWorld.get(sl.getServer().overworld()).wardSpot();
        if (at == null) return null;
        double dx = getX() - (at.getX() + 0.5), dz = getZ() - (at.getZ() + 0.5);
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4) { dx = 1; dz = 0; len = 1; }
        double r = net.jj.mountain.world.MountainWorld.wardRange() + 64;
        return new Vec3(at.getX() + 0.5 + dx / len * r, getY(), at.getZ() + 0.5 + dz / len * r);
    }

    /** the heart has woken under him: whatever he was doing here, he is not doing it any more */
    public void pushedBackByHeart() {
        if (level().isClientSide) return;
        setTarget(null); goal = null; calledBy = null; stay = false;
        clearHitList();
        attacks.stop();
        stopFetch();
        angerTicks = 0; playerAnger = 0;
        sound(position(), net.jj.mountain.ModSounds.HURT, 4.5f, 0.45f);
    }

    /** that piece of him jerks for a moment */
    public void flinch(int bone) {
        if (bone < 0 || level().isClientSide) return;
        flinchBone = bone; flinchT = FLINCH_TICKS;
        entityData.set(DATA_FLINCH, (bone & 0xffff) | (flinchT << 16));
    }

    private void syncLimbs() {
        entityData.set(DATA_LEGS_BROKEN, legsBroken);
        entityData.set(DATA_ARMS_BROKEN0, armsBroken0);
        entityData.set(DATA_ARMS_BROKEN1, armsBroken1);
        entityData.set(DATA_LEGS_SCARRED, legsScarred);
    }

    /** a hit that landed on one leg */
    public boolean hurtLeg(int k, DamageSource src, float amount) {
        limbCalm = 0;
        if (!legBroken(k)) {
            legHp[k] -= amount;
            Vec3 c = legKneeWorld(k);
            sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PINK_TERRACOTTA.defaultBlockState()), c, 10, 2.5 * mountainScale() + 0.6, 0.5, 0.15);
            if (legHp[k] <= 0) breakLeg(k, src.getEntity());
            flinch(rig.legs[k].bones[0]);
        }
        return takeDamage(src, amount * 0.35f);
    }

    /** a hit that landed on one arm */
    public boolean hurtArm(int k, DamageSource src, float amount) {
        limbCalm = 0;
        if (!armBroken(k)) {
            armHp[k] -= amount;
            Vec3 c = handWorld(k);
            sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PINK_WOOL.defaultBlockState()), c, 10, 2.0 * mountainScale() + 0.6, 0.5, 0.15);
            if (armHp[k] <= 0) breakArm(k, src.getEntity());
            flinch(rig.arms[k].upper);
        }
        return takeDamage(src, amount * 0.35f);
    }

    private void breakLeg(int k, @Nullable Entity by) {
        legsBroken |= 1L << k;
        // a leg he has already had broken once does not get another chance
        if (MountainConfig.V.scars && legScarred(k) && !legRuined(k) && ruinedLegs() < ruinCapLegs()) legsRuined |= 1L << k;
        syncLimbs();
        float s = mountainScale();
        Vec3 c = legKneeWorld(k);
        sound(c, SoundEvents.BONE_BLOCK_BREAK, 3.0f, 0.5f);
        sound(c, net.jj.mountain.ModSounds.HURT, 2.4f, 0.9f);
        sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PINK_TERRACOTTA.defaultBlockState()), c, 70, 4.0 * s + 1, 4.0 * s + 1, 0.35);
        sendParticles(ParticleTypes.SQUID_INK, c, 30, 3.0 * s + 1, 2.0 * s + 1, 0.2);
        if (by instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("message.mountain_breathes.leg_broken", brokenLegs()), true);
        if (crippled()) { comeDownForGood(); return; }
        // enough legs gone on one side and he comes down on his belly
        int side = rig.legs[k].side, sideCount = 0;
        for (MountainRig.LegDef L : rig.legs) if (L.side == side && legBroken(L.k)) sideCount++;
        if (downTicks <= 0 && (sideCount >= 4 || brokenLegs() >= 8)) goDown();
    }

    private void breakArm(int k, @Nullable Entity by) {
        if (k < 64) armsBroken0 |= 1L << k; else armsBroken1 |= 1L << (k - 64);
        if (MountainConfig.V.scars && armScarred(k) && !armRuined(k) && ruinedArms() < ruinCapArms()) {
            if (k < 64) armsRuined0 |= 1L << k; else armsRuined1 |= 1L << (k - 64);
        }
        syncLimbs();
        Vec3 c = handWorld(k);
        sound(c, SoundEvents.BONE_BLOCK_BREAK, 2.4f, 0.7f);
        sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PINK_WOOL.defaultBlockState()), c, 45, 3.0 * mountainScale() + 1, 3.0 * mountainScale() + 1, 0.3);
        if (by instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("message.mountain_breathes.arm_broken"), true);
        if (held != null && holdArm == k) { if (tongueHold) letGoOfTongue(); else releaseHeld(true); }
    }

    /** too many legs gone: he comes down on his belly for a few seconds, wide open to being hurt */
    /** /mountain breakleg: break the legs nearest a spot */
    public int breakLegsNear(Vec3 at, int count) {
        java.util.List<Integer> order = new java.util.ArrayList<>();
        for (MountainRig.LegDef L : rig.legs) if (!legBroken(L.k)) order.add(L.k);
        order.sort((a, b) -> Double.compare(legFootWorld(a).distanceToSqr(at), legFootWorld(b).distanceToSqr(at)));
        int n = 0;
        for (int k : order) {
            if (n >= count) break;
            legHp[k] = 0f; breakLeg(k, null); n++;
        }
        return n;
    }

    /** /mountain mendlimbs: every leg and arm whole again */
    public void mendAllLimbs() {
        legsBroken = 0L; armsBroken0 = 0L; armsBroken1 = 0L;
        legsScarred = legsRuined = armsScarred0 = armsScarred1 = armsRuined0 = armsRuined1 = 0L;
        java.util.Arrays.fill(legHp, legHpMax());
        java.util.Arrays.fill(armHp, armHpMax());
        downTicks = 0; wasCrippled = false;
        syncLimbs();
    }

    // ------------------------------------------------------------------ whoever asked him to finish it
    /** whoever asked, so the mark lands on the right name when the ground goes */
    private @Nullable UUID breaker;

    /** he was half way up when the world was put away: he goes back to where he was and finishes it */
    private int riseBack = -1;
    private void resumeRise() {
        if (riseBack < 0) return;
        int t = riseBack;
        riseBack = -1;
        if (isDeadOrDying() || !attacks.forceUnmake()) { breaker = null; return; }
        attacks.t = t;
    }

    /** a wound nobody dealt him: the price of a thing he was asked to do */
    public void hurtSelf(float amount) {
        if (level().isClientSide || isDeadOrDying() || amount <= 0) return;
        takeDamage(damageSources().generic(), amount);
    }

    // ------------------------------------------------------------------ the last thing he does
    /** 0 = he will, 1 = it is switched off, 2 = there is still too much of him left, 3 = not this moment */
    public int unmakeState() {
        if (!MountainConfig.V.unmake) return 1;
        if (isDeadOrDying() || !isAlive()) return 3;
        if (healthNow() > healthMax() * 0.25f) return 2;
        if (attacks.id == net.jj.mountain.rig.RigState.UNMAKE || digState != 0 || carryingSomebody()) return 3;
        return 0;
    }

    public boolean unmaking() { return attacks.id == net.jj.mountain.rig.RigState.UNMAKE; }

    /** the ask: only of a Mountain with a quarter of himself left, and it is the end of him */
    public boolean unmakeIt(ServerPlayer who) {
        if (level().isClientSide || unmakeState() != 0) return false;
        if (!(level() instanceof ServerLevel sl)) return false;
        breaker = who.getUUID();
        if (sleeping()) wakeUp(who);
        stay = false;
        goal = null;
        clearHitList();
        if (!attacks.forceUnmake()) { breaker = null; return false; }
        // the sky goes over the whole world, and everybody is told, near him or not
        sl.setWeatherParameters(0, 12000, true, true);
        for (ServerPlayer sp : sl.getServer().getPlayerList().getPlayers())
            sp.displayClientMessage(Component.translatable("message.mountain_breathes.unmake_coming"), false);
        return true;
    }

    /** twenty seconds of it: every eye he has left burning everything alive for a long way out */
    void unmakeBurn() {
        if (!(level() instanceof ServerLevel sl)) return;
        float s = mountainScale();
        double r = 200 + 300 * s;
        java.util.List<LivingEntity> all = new java.util.ArrayList<>();
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBoxForCulling().inflate(r),
                x -> x.isAlive() && !(x instanceof MountainEntity) && !(x instanceof HeartEntity))) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            all.add(e);
            if (all.size() >= net.jj.mountain.net.StormPayload.MOST) break;
        }
        attacks.burnAll(all);
    }

    /** the moment he comes down: the ground goes, the wave goes out, and he goes with it */
    void unmakeNow() {
        if (!(level() instanceof ServerLevel sl)) return;
        float s = mountainScale();
        Vec3 c = position();
        int gy = Mth.floor(c.y);
        BlockPos mid = new BlockPos(Mth.floor(c.x), gy, Mth.floor(c.z));
        int r = Math.max(24, Math.round(Math.max(24, MountainConfig.V.unmakeRadius) * (float) Math.sqrt(Math.max(0.05f, s))));

        sound(c, SoundEvents.GENERIC_EXPLODE.value(), 10f, 0.2f);
        sound(c, net.jj.mountain.ModSounds.DEATH, 6f, 0.4f);
        sound(c, net.jj.mountain.ModSounds.RAGE, 6f, 0.55f);
        sendParticles(ParticleTypes.EXPLOSION_EMITTER, new Vec3(mid.getX(), mid.getY() + 3, mid.getZ()), 60, r * 0.5, 6, 0.0);

        // nothing living anywhere near him comes out of it, bar the one who asked
        double kill = r * 1.1;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(mid).inflate(kill, kill, kill),
                x -> x.isAlive() && x != this && !(x instanceof MountainEntity))) {
            if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) continue;
            if (breaker != null && breaker.equals(e.getUUID())) continue;
            e.hurt(damageSources().mobAttack(this), 1.0E6f);
        }

        net.jj.mountain.world.Crater.start(sl, mid, r);
        net.jj.mountain.world.Shockwave.start(sl, new Vec3(mid.getX() + 0.5, mid.getY(), mid.getZ() + 0.5),
                Math.max(r * 2.0, MountainConfig.V.unmakeWave), dmg(40f), breaker);
        heartOnASpike(sl, mid, r);

        // and whoever asked is known to every one of them from here on
        ServerPlayer who = breaker == null ? null : sl.getServer().getPlayerList().getPlayer(breaker);
        if (breaker != null) {
            var bw = net.jj.mountain.world.MountainWorld.get(sl.getServer().overworld());
            bw.mark(breaker);
            bw.oneBookOnly(sl.getServer());   // and it goes out of their hands this second, not half a second later
            if (who != null) who.displayClientMessage(Component.translatable("message.mountain_breathes.unmake_marked"), false);
        }
        for (ServerPlayer sp : sl.getServer().getPlayerList().getPlayers())
            sp.displayClientMessage(Component.translatable("message.mountain_breathes.unmake_done", r * 2), false);
        breaker = null;

        // and that is the end of him
        hp = 0;
        syncHealth();
        this.setHealth(0f);
        this.die(damageSources().generic());
    }

    /** his heart set on a spike in the middle of it, so the hole does not swallow the one thing worth having */
    private void heartOnASpike(ServerLevel sl, BlockPos mid, int r) {
        int deep = (int) Math.round(r * 0.5);
        int base = Math.max(sl.getMinBuildHeight() + 1, mid.getY() - deep - 1);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = base; y <= base + 3; y++) {
            p.set(mid.getX(), y, mid.getZ());
            sl.setBlock(p, Blocks.BEDROCK.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
        p.set(mid.getX(), base + 4, mid.getZ());
        sl.setBlock(p, ModBlocks.HEART.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
    }

    public void goDown() {
        // four legs gone on one side used to put him down for seven seconds, which was barely worth the trouble
        // of breaking them. Now it is a real window, and it gets longer the more of him is already gone.
        downTicks = Math.min(900, 420 + 25 * Math.max(0, brokenLegs() - 4));
        Vec3 c = position();
        sound(c, SoundEvents.GENERIC_EXPLODE.value(), 5f, 0.4f);
        sound(c, net.jj.mountain.ModSounds.DEATH, 3f, 1.0f);
        for (ServerPlayer p : ((ServerLevel) level()).players()) {
            if (p.distanceToSqr(this) < sq(300 * mountainScale() + 80)) p.displayClientMessage(Component.translatable("message.mountain_breathes.knocked_down"), true);
        }
    }

    /**
     * Enough of his legs are gone that he will not stand again. He is not finished, though — he is the worst he
     * has ever been to be near. He cannot follow you, so everything he has left goes into reaching you anyway.
     */
    private void comeDownForGood() {
        if (wasCrippled) return;
        wasCrippled = true;
        downTicks = Math.max(downTicks, 200);
        stay = false; goal = null; wanderGoal = null;
        stopFetch();
        if (sleeping()) wakeUp(null);
        angerTicks = Math.max(angerTicks, 2400);
        playerAnger = Math.max(playerAnger, 2400);
        Vec3 c = position();
        sound(c, SoundEvents.GENERIC_EXPLODE.value(), 6f, 0.3f);
        sound(c, net.jj.mountain.ModSounds.RAGE, 5f, 0.6f);
        if (level() instanceof ServerLevel sl) {
            for (ServerPlayer p : sl.players())
                if (p.distanceToSqr(this) < sq(400 * mountainScale() + 120))
                    p.displayClientMessage(Component.translatable("message.mountain_breathes.down_for_good"), false);
        }
    }

    /** every tick: coming down, getting back up, and growing broken legs and arms back when he is left alone */
    private void limbTick() {
        if (flinchT > 0) {
            flinchT--;
            entityData.set(DATA_FLINCH, (flinchBone & 0xffff) | (flinchT << 16));
            if (flinchT == 0) flinchBone = -1;
        }
        if (hurtByT > 0 && (hurtBy == null || !hurtBy.isAlive())) hurtByT = 0;
        if (hurtByT > 0) hurtByT--;
        float want = downTicks > 0 ? 1f : 0f;
        downAmt += Mth.clamp(want - downAmt, -0.035f, 0.055f);
        entityData.set(DATA_DOWN, downAmt);
        if (crippled()) {
            comeDownForGood();
            downTicks = Math.max(downTicks, 100);                    // he never heaves himself up off this one
            angerTicks = Math.max(angerTicks, 100);
        } else if (wasCrippled) {
            wasCrippled = false;                                     // enough knitted back that he can stand again
        }
        if (downTicks > 0) {
            downTicks--;
            if (downTicks == 0) {
                // he gets one leg back under him as he heaves himself up
                for (MountainRig.LegDef L : rig.legs) if (legBroken(L.k) && !legRuined(L.k)) { mendLeg(L.k); break; }
                sound(position(), net.jj.mountain.ModSounds.ROAR, 4f, 0.7f);
            }
        }
        limbCalm++;
        if (limbCalm > 400 && limbCalm % 200 == 0) {                 // left alone: he knits himself back together
            for (MountainRig.LegDef L : rig.legs) if (legBroken(L.k) && !legRuined(L.k)) { mendLeg(L.k); return; }
            for (MountainRig.ArmDef A : rig.arms) if (armBroken(A.k) && !armRuined(A.k)) { mendArm(A.k); return; }
            for (int k = 0; k < legHp.length; k++) legHp[k] = Math.min(legHpMax(k), legHp[k] + legHpMax(k) * 0.25f);
            for (int k = 0; k < armHp.length; k++) armHp[k] = Math.min(armHpMax(k), armHp[k] + armHpMax(k) * 0.25f);
        }
    }

    private void mendLeg(int k) {
        if (legRuined(k)) return;                                   // that one is not coming back
        legsBroken &= ~(1L << k);
        if (MountainConfig.V.scars) legsScarred |= 1L << k;         // it knits, but never the way it was
        legHp[k] = legHpMax(k) * 0.6f; syncLimbs();
        sound(legKneeWorld(k), SoundEvents.SLIME_SQUISH, 2f, 0.6f);
    }

    private void mendArm(int k) {
        if (armRuined(k)) return;
        if (k < 64) armsBroken0 &= ~(1L << k); else armsBroken1 &= ~(1L << (k - 64));
        if (MountainConfig.V.scars) { if (k < 64) armsScarred0 |= 1L << k; else armsScarred1 |= 1L << (k - 64); }
        armHp[k] = armHpMax(k) * 0.6f; syncLimbs();
    }

    /** /mountain breakleg and the tests: break this one leg outright */
    public void breakLegAt(int k) {
        if (k < 0 || k >= legHp.length || legBroken(k)) return;
        legHp[k] = 0f;
        breakLeg(k, null);
    }

    /** knits one broken leg back the way he does when he is left alone. Which one, or -1 if there is none to knit. */
    public int mendOneLeg() {
        for (MountainRig.LegDef L : rig.legs) if (legBroken(L.k) && !legRuined(L.k)) { mendLeg(L.k); return L.k; }
        return -1;
    }

    /** what that leg can take before it goes again, as a share of a whole one */
    public float legStrength(int k) { return legHpMax() <= 0 ? 0f : legHpMax(k) / legHpMax(); }

    /** /mountain scars clear: every mark of every fight taken off him */
    public void clearScars() {
        legsScarred = legsRuined = armsScarred0 = armsScarred1 = armsRuined0 = armsRuined1 = 0L;
        for (int k = 0; k < legHp.length; k++) if (!legBroken(k)) legHp[k] = legHpMax(k);
        for (int k = 0; k < armHp.length; k++) if (!armBroken(k)) armHp[k] = armHpMax(k);
        syncLimbs();
    }

    /** which arm a hit landed on: the one holding the attacker, or the nearest hand to them */
    private int armHitBy(DamageSource src) {
        Entity att = src.getDirectEntity() != null ? src.getDirectEntity() : src.getEntity();
        if (att == null) return -1;
        if (held != null && att == held && holdArm >= 0) return holdArm;
        float s = mountainScale();
        double best = sq(7 * s + 3); int bk = -1;
        Vec3 p = att.getEyePosition();
        for (MountainRig.ArmDef A : rig.arms) {
            if (armBroken(A.k)) continue;
            double d = handWorld(A.k).distanceToSqr(p);
            if (d < best) { best = d; bk = A.k; }
        }
        return bk;
    }

    /** eye >= 0: a hit on that eye; null: a hit on his flesh (which barely feels it). */
    public boolean applyDamage(DamageSource src, float amount, @Nullable Integer eye) {
        if (level().isClientSide || isDeadOrDying()) return false;
        Entity att = src.getEntity();
        float dealt;
        if (eye != null) {
            int e = eye;
            eyeHp[e] -= amount;
            dealt = Math.min(amount * 1.2f, healthMax() * 0.004f);   // a swing through an eye still only wounds him
            Vec3 c = eyeWorld(e);
            sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.WHITE_CONCRETE.defaultBlockState()), c, 12, rig.eyes[e].radius * mountainScale() * 0.4, 0.5, 0.2);
            if (eyeHp[e] <= 0 && !state.isPopped(e)) {
                state.setPopped(e, true);
                dealt += eyeWorth();                        // one eye out of his two hundred and ten
                sound(c, SoundEvents.SLIME_DEATH, 3.0f, 0.4f);
                sound(c, SoundEvents.TURTLE_EGG_CRACK, 2.0f, 0.5f);
                sendParticles(ParticleTypes.SQUID_INK, c, 60, rig.eyes[e].radius * mountainScale() * 0.6, rig.eyes[e].radius * mountainScale() * 0.6, 0.3);
                sendParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR, c, 20, rig.eyes[e].radius * mountainScale() * 0.4, 0.5, 0.0);
                if (att instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("message.mountain_breathes.eye_popped", eyesOpenNow()), true);
            }
        } else {
            dealt = amount * 0.1f;
        }
        return takeDamage(src, dealt);
    }

    /** the part of a hit that reaches him: his health, his temper, and waking him up */
    private boolean takeDamage(DamageSource src, float dealt) {
        Entity att = src.getEntity();
        if (downTicks > 0) dealt *= 1.6f;                   // flat on his belly he can really be hurt
        hp -= dealt;
        this.level().broadcastDamageEvent(this, src);
        if (att instanceof LivingEntity le2 && !(le2 instanceof HeartEntity)) { hurtBy = le2; hurtByT = 60; }     // his head snaps round at whoever it was
        if (sleeping() && (asleep || waking >= 0)) startWaking(att instanceof Player pl ? pl : null, true);     // a hit wakes him at once
        if (tongueHold && att != null && att == held) letGoOfTongue();                 // fight the tongue and it lets go
        if (att instanceof Player p) {
            setLastHurtByPlayer(p);
            remember(p);
            boolean fair = !p.isCreative() && !p.isSpectator() && !Innards.isInside(p);
            if (fair && spares(p)) {
                // the book is keeping him off you and you hit him anyway. He wears it, up to a point.
                if (mood.struck(p.getUUID())) {
                    setTarget(p); angerTicks = 2400; playerAnger = 2400;
                    sound(toWorld(rig.headCenter, pose[rig.head]), net.jj.mountain.ModSounds.RAGE, 4f, 0.75f);
                    p.displayClientMessage(Component.translatable("message.mountain_breathes.had_enough"), false);
                } else {
                    int left = mood.strikesLeft(p.getUUID());
                    if (left < Integer.MAX_VALUE)
                        p.displayClientMessage(Component.translatable("message.mountain_breathes.lets_it_go", left), true);
                }
            } else if (fair) {
                if (isGuardian() && playerAnger <= 0) p.displayClientMessage(Component.translatable("message.mountain_breathes.guardian_provoked"), true);
                setTarget(p); angerTicks = 1200; playerAnger = 1200;
            }
        } else if (att instanceof LivingEntity le && !(att instanceof HeartEntity)) {
            setLastHurtByMob(le);
            if (getTarget() == null) { setTarget(le); angerTicks = Math.max(angerTicks, 600); }
        }
        syncHealth();
        if (hp > 0 && phase() > phaseSeen) enterPhase(phase());
        if (hp <= 0) { this.setHealth(0f); this.die(src); }
        return true;
    }

    private int eyesOpenNow() { int n = 0; for (int e = 0; e < rig.eyes.length; e++) if (!state.isPopped(e)) n++; return n; }

    /** The first unpopped eye along a ray, or -1. */
    public int eyeOnRay(Vec3 from, Vec3 dir, double maxDist) {
        float s = mountainScale();
        int best = -1; double bt = maxDist;
        for (MountainRig.EyeDef E : rig.eyes) {
            if (state.isPopped(E.k)) continue;
            Vec3 c = eyeWorld(E.k);
            double r = (E.radius + 0.5) * s + 0.3;
            Vec3 oc = from.subtract(c);
            double b = oc.dot(dir), cc = oc.lengthSqr() - r * r;
            double disc = b * b - cc;
            if (disc < 0) continue;
            double t = -b - Math.sqrt(disc);
            if (t < 0) t = -b + Math.sqrt(disc);
            if (t >= 0 && t < bt) { bt = t; best = E.k; }
        }
        return best;
    }

    public int eyeNear(Vec3 p, double slack) {
        float s = mountainScale();
        int best = -1; double bd = Double.MAX_VALUE;
        for (MountainRig.EyeDef E : rig.eyes) {
            if (state.isPopped(E.k)) continue;
            double d = eyeWorld(E.k).distanceTo(p) - E.radius * s;
            if (d < slack && d < bd) { bd = d; best = E.k; }
        }
        return best;
    }

    public Vec3 eyeWorld(int e) { return toWorld(rig.eyes[e].center, pose[rig.eyes[e].bone]); }

    /** /mountain popeye: pop the nearest eyes (tests and cinematics). */
    /** what one eye is worth: take all two hundred and ten and you have taken all of him */
    public float eyeWorth() { return healthMax() / Math.max(1, rig.eyes.length); }

    public void popEyes(int n, @Nullable Entity by) {
        for (int q = 0; q < n; q++) {
            int e = -1;
            for (int k = 0; k < rig.eyes.length; k++) if (!state.isPopped(k)) { e = k; break; }
            if (e < 0) return;
            DamageSource src = by instanceof Player pl ? damageSources().playerAttack(pl) : damageSources().generic();
            eyeHp[e] = 0f;
            applyDamage(src, 0.01f, e);          // bursts it, and costs him exactly what one eye is worth
            if (isDeadOrDying()) return;
        }
    }

    public boolean isEyePopped(int e) { return state.isPopped(e); }

    /** /mountain attack */
    public boolean forceAttack(int which) { return !sleeping() && downTicks <= 0 && attacks.force(which, getTarget()); }
    public int attackNow() { return level().isClientSide ? curC.attack : attacks.id; }
    /** how many things his gaze is burning through this time */
    public int gazeTargets() { return attacks.gazeTargets(); }
    /** how many things the eye storm has a line on */
    public int stormTargets() { return attacks.stormTargets(); }
    /** how many things his eyes are split between right now */
    public int watchCount() { return level().isClientSide ? curC.lookCount : lookCount; }
    /** what his eyes are split between right now (server side, for the tests) */
    public List<LivingEntity> watching() { return List.copyOf(watchList); }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide) return false;
        if (burrowed()) return false;                     // there is nothing of him above the ground to hit
        // a swing at the body can land on him rather than on one of his boxes: that has to cut him up too
        if (isDeadOrDying()) return false;
        if (src.is(DamageTypes.GENERIC_KILL)) {
            hp = 0; syncHealth(); this.setHealth(0f); this.die(src);
            return true;
        }
        if (isInvulnerableTo(src)) return false;
        return applyDamage(src, amount, null);
    }

    public float dmg(float base) {
        float d = base * MountainConfig.V.damageMultiplier * (MountainConfig.V.attackDamage / 10f);
        return crippled() ? d * 1.35f : d;               // down for good and nothing left to save it for
    }

    /** his hits land harder on anything that isn't a player */
    public float dmg(float base, Entity victim) {
        float d = dmg(base);
        return victim instanceof Player ? d : d * Math.max(0f, MountainConfig.V.mobDamage);
    }

    /** /mountain health: put his new full-size health on a mountain that already exists, keeping how hurt he is */
    /** after a load or a health change, don't roar for phases he is already past */
    public void resetPhaseMark() { phaseSeen = phase(); }

    public void refreshHealthFromConfig() {
        float frac = healthMax() > 0 ? Mth.clamp(hp / healthMax(), 0f, 1f) : 1f;
        float max = Math.max(60f, Math.round(MountainConfig.V.health * sizeFactor(mountainScale())));
        this.entityData.set(DATA_HP_MAX, max);
        hp = max * frac;
        syncHealth();
    }

    boolean canGrief() {
        return MountainConfig.V.griefing && level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    // ------------------------------------------------------------------ dying: one last breath, then he folds and sinks
    @Override
    public void die(DamageSource src) {
        super.die(src);
        // the next one is set going whether this was the world's own or one somebody put down themselves, as long
        // as he was the only one standing: otherwise killing a spawn-egg one would quietly end them for good
        if (level() instanceof ServerLevel sl && !isAPiece()) {
            boolean last = worldOne || (MountainConfig.V.oneInTheWorld
                    && net.jj.mountain.world.MountainWorld.anyOther(sl.getServer(), this) == null);
            if (last) net.jj.mountain.world.MountainWorld.get(sl.getServer().overworld()).died(sl, this);
        }
        if (!level().isClientSide) {
            stopFetch();
            dropRider();
            releaseHeld(true);
            Innards.ejectAll(this);
            swallowedPlayers.clear();
        }
    }

    private @Nullable DamageSource deathLoot;
    private boolean lootDropped;

    /**
     * The loot waits until he has sunk out of sight. A lump he tore off himself carries none of it: the flesh, the
     * eyes, the horn and the heart all come off the real one, or killing the pieces would be the easy way round
     * the whole fight.
     */
    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource src) { if (!isAPiece()) deathLoot = src; }

    /** how long his body lies there before it goes */
    public int carcassTicks() { return Math.max(0, MountainConfig.V.carcassSeconds) * 20; }
    /** the whole thing: last breath, the legs going, the fall, lying there, then nothing */
    public int deathLength() { return FALL_AT + carcassTicks() + 140; }
    /** he is down and still: you can walk on him and cut him up */
    public boolean isCarcass() { return isDeadOrDying() && deathTime >= FALL_AT && !isRemoved(); }

    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (level().isClientSide) return;
        float s = mountainScale();
        int t = deathTime;
        if (t < 60) { breath += (1.25f - breath) * 0.06f; mouth += (1f - mouth) * 0.08f; suck(0.7f, false); }
        else if (t < 80) { grind = 1.2f * Mth.sin(t * 1.3f); }
        else if (t < 110) { breath += (0f - breath) * 0.2f; mouth = 1f; if (t == 80) { blast(true); lastBreathRing(); } }
        else mouth += (0.15f - mouth) * 0.05f;
        if (t == 60) sound(mouthWorld(), net.jj.mountain.ModSounds.BREATH_IN, 3f, 0.6f);

        // the legs give way one at a time and he sags further with each one
        if (t >= 62 && t < FALL_AT && t % 2 == 0) {
            for (MountainRig.LegDef L : rig.legs) {
                if (legBroken(L.k)) continue;
                legsBroken |= 1L << L.k; legHp[L.k] = 0f; syncLimbs();
                if (t % 6 == 0) {
                    Vec3 c = legKneeWorld(L.k);
                    sound(c, SoundEvents.BONE_BLOCK_BREAK, 2.2f, 0.55f + random.nextFloat() * 0.25f);
                    sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PINK_TERRACOTTA.defaultBlockState()), c, 24, 2.5 * s + 0.6, 1.0, 0.15);
                }
                break;
            }
        }
        if (t == FALL_AT) comeDown();

        // (the dying pose itself lays him out; downAmt is left alone or he would be pushed through the ground)

        // and at the very end he goes into the ground
        int rotFrom = deathLength() - 140;
        if (t > rotFrom && t % 6 == 0) {
            Vec3 c = toWorld(rig.segCenter[2], pose[rig.segments[2]]);
            sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PINK_TERRACOTTA.defaultBlockState()), new Vec3(c.x, getY() + 1, c.z), 60, 60 * s, 2, 0.2);
            sendParticles(ParticleTypes.SQUID_INK, new Vec3(c.x, getY() + 1, c.z), 30, 50 * s, 2, 0.1);
            if (t % 24 == 0) sound(c, SoundEvents.MUD_BREAK, 2.5f, 0.3f);
        }
        if (t >= deathLength() && !isRemoved()) {
            Vec3 c = new Vec3(getX(), getY() + 1, getZ());
            sendParticles(ParticleTypes.POOF, c, 120, 50 * s, 3, 0.05);
            sound(c, SoundEvents.WARDEN_DIG, 3f, 0.5f);
            if (!lootDropped && !isAPiece() && level() instanceof ServerLevel sl)
                super.dropAllDeathLoot(sl, deathLoot != null ? deathLoot : damageSources().generic());
            deathLoot = null;
            lootDropped = true;
            level().broadcastEntityEvent(this, (byte) 60);
            this.remove(RemovalReason.KILLED);
        }
    }

    /** the moment the last leg folds: he drops, and everything near him feels it */
    private void comeDown() {
        float s = mountainScale();
        Vec3 c = toWorld(rig.segCenter[2], pose[rig.segments[2]]);
        Vec3 at = new Vec3(c.x, getY(), c.z);
        sound(at, net.jj.mountain.ModSounds.DEATH, 5f, 0.85f);
        sound(at, SoundEvents.GENERIC_EXPLODE.value(), 5f, 0.35f);
        sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()), at, 400, 70 * s + 8, 3, 0.4);
        sendParticles(ParticleTypes.EXPLOSION_EMITTER, at, 8, 40 * s + 4, 2, 0.0);
        double r = 100 * s + 20;
        for (Entity e : level().getEntities(this, getBoundingBox().inflate(r))) {
            if (e instanceof MountainPart || e instanceof MountainEntity) continue;
            if (e instanceof Player p && (p.isSpectator() || p.isCreative())) continue;
            double d = Math.hypot(e.getX() - at.x, e.getZ() - at.z);
            if (d > r) continue;
            double push = 1.0 - d / r;
            if (e instanceof LivingEntity le && !spares(le)) le.hurt(damageSources().mobAttack(this), dmg((float) (6 + 14 * s) * (float) push, le));
            Vec3 away = new Vec3(e.getX() - at.x, 0, e.getZ() - at.z).normalize().scale(1.2 * push * (0.6 + s)).add(0, 0.7 * push, 0);
            e.setDeltaMovement(e.getDeltaMovement().add(away));
            e.hurtMarked = true;
        }
        if (canGrief()) trample(at, 8 + 30 * s, (int) (10 + 24 * s));
        // everything he was carrying comes off him now, not when the body has rotted away: you killed him, take it
        // reloaded half way through dying, nobody remembers who did it; the loot still falls
        if (level() instanceof ServerLevel sl) {
            if (!isAPiece()) super.dropAllDeathLoot(sl, deathLoot != null ? deathLoot : damageSources().generic());
            deathLoot = null;
            lootDropped = true;
        }
    }

    private void lastBreathRing() {
        float s = mountainScale();
        double r = 90 * s + 16;
        Vec3 c = position();
        for (Entity e : level().getEntities(this, getBoundingBox().inflate(r), this::canBeSucked)) {
            double d = Math.hypot(e.getX() - c.x, e.getZ() - c.z);
            if (d > r) continue;
            Vec3 away = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z).normalize().scale(1.5 + 1.5 * s * (1 - d / r)).add(0, 0.6, 0);
            e.setDeltaMovement(e.getDeltaMovement().add(away));
            e.hurtMarked = true;
        }
    }

    /**
     * Put away with the piece of world he was standing in. Where he was going is written down before he goes, so
     * he is never simply lost: the finder still points at the spot and he carries on walking there while nobody
     * is looking. The game does not always call remove() when a chunk goes, so the unload callback calls this too.
     */
    public void parkForNow() {
        // a lump he tore off himself is never written down and never walks anywhere as a sum: it is a minute of
        // his own flesh and when it is gone it is gone
        if (isAPiece()) { parked = true; return; }
        if (parked || level().isClientSide || !(level() instanceof ServerLevel sl) || sl.getServer() == null) return;
        parked = true;
        var w = net.jj.mountain.world.MountainWorld.get(sl.getServer().overworld());
        w.parked(sl, this);
        if (worldOne) w.seen(this);
        boolean under = digState != 0 && digTo != null;
        Vec3 dest = under ? digTo : goal != null ? goal : wanderGoal;
        if (dest != null && !isDeadOrDying()) w.startTrip(sl, this, dest, travelSpeed(under), under);
    }

    private boolean parked;
    /**
     * When he first turned up in the world, kept through every save and reload. It is what decides which of them
     * is the old one when somebody summons another and the world is already full.
     */
    private long bornAt = -1;
    public long bornAt(net.minecraft.world.level.Level l) {
        if (bornAt < 0) bornAt = l.getGameTime();
        return bornAt;
    }

    /** somebody summoned another and he is the one who has to go: out of the world, and not as a sum either */
    public void takenPlaceOf() {
        parked = true;                                   // he is not written down and he is not coming back
        clearBars();
        if (level() instanceof ServerLevel sl) {
            Vec3 c = position();
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SQUID_INK,
                    c.x, c.y + 12 * mountainScale() + 2, c.z, 200, 14 * mountainScale() + 4, 8 * mountainScale() + 2, 14 * mountainScale() + 4, 0.3);
            sound(c, net.jj.mountain.ModSounds.DEATH, 4f, 0.6f);
        }
        discard();
    }
    /** ticks he has spent with nobody anywhere near him */
    private int aloneOut;
    /** he has just been put back from being a sum: don't step straight back out again */
    private int justBack;
    public void backFromAway() { justBack = 200; }

    /**
     * Nobody for a long way in any direction. He is written down in full — health, broken legs, burst eyes,
     * where he was told to go — handed to the world as a sum, and taken out of the game. From then on the world
     * works out where he ought to be, and puts him back there the moment anybody comes near that spot.
     */
    private int lastSeenTick = -1, stalled;

    public void stepAsideIfAlone(int every) {
        if (tickCount < 40) return;            // he has only just turned up: let him find his feet first
        if (justBack > 0) { justBack = Math.max(0, justBack - every); return; }
        if (!MountainConfig.V.offscreenTravel || level().isClientSide) return;
        if (!(level() instanceof ServerLevel sl) || isRemoved() || isDeadOrDying()) return;
        // never while anybody has a stake in him being here
        if (ridden() || carryingSomebody() || comingForSomebody() || settingSomebodyDown()) return;
        if (held != null || unmaking() || !swallowedPlayers.isEmpty()) return;
        if (Innards.anyoneIn(this)) return;
        // He has stopped running. Underground he covers three blocks a tick and can outrun the piece of world
        // he holds open; the moment that happens he is a statue that never moves again, and the book has
        // nothing to say about him but "loaded". A sum is better than a statue, whoever is standing nearby:
        // he is written down and put straight back where the sum says he should be.
        if (tickCount == lastSeenTick) {
            stalled += every;
            if (stalled >= 60) {
                net.jj.mountain.MountainMod.LOG.info("The Mountain has stopped running at {}, {}; keeping him as a sum instead",
                        getBlockX(), getBlockZ());
                stepAside();
                return;
            }
        } else { stalled = 0; lastSeenTick = tickCount; }
        double away = net.jj.mountain.world.MountainWorld.awayRange(sl.getServer());
        for (Player p : sl.players())
            if (p.distanceToSqr(this) < away * away) { aloneOut = 0; return; }
        // a couple of seconds of being properly alone, so somebody crossing the line doesn't flicker him in and out
        aloneOut += every;
        if (aloneOut < 60) return;
        stepAside();
    }

    /** hands him to the world as a sum and takes him out of the game */
    public boolean stepAside() {
        if (!(level() instanceof ServerLevel sl) || isRemoved() || isDeadOrDying()) return false;
        var w = net.jj.mountain.world.MountainWorld.get(sl.getServer().overworld());
        stopFetch();
        dropRider();
        releaseHeld(true);
        boolean under = digState != 0 && digTo != null;
        Vec3 dest = under ? digTo : goal != null ? goal : wanderGoal;
        CompoundTag full = new CompoundTag();
        saveWithoutId(full);
        w.takeAway(sl, this, full, dest, travelSpeed(under), under);
        parked = true;                                  // the world already has him: remove() must not park him twice
        Innards.ejectAll(this);
        discard();
        return true;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            stopFetch();
            dropRider();
            if (!isRemoved() && (reason == RemovalReason.UNLOADED_TO_CHUNK || reason == RemovalReason.UNLOADED_WITH_PLAYER))
                parkForNow();
            releaseHeld(true);
            Innards.ejectAll(this);
        }
        super.remove(reason);
        for (int k = 0; k < parts.length; k++) { if (parts[k] != null) parts[k].discard(); parts[k] = null; }
        clearBars();
        MountainCollision.untrack(this);
    }

    /** Takes his bars off everyone's screen. Also called when his chunk unloads (that skips remove()). */
    public void clearBars() {
        if (bar != null) { bar.removeAllPlayers(); eyeBar.removeAllPlayers(); }
    }

    @Override
    public void onClientRemoval() { super.onClientRemoval(); MountainCollision.untrack(this); }

    // ------------------------------------------------------------------ pose, parts, sync
    private void updateServerState() {
        RigState s = state;
        boolean dying = isDeadOrDying();
        s.walkPhase = walkPhase; s.walkAmount = dying ? Math.max(0f, walkAmount -= 0.05f) : walkAmount; s.turn = turnAmt;
        s.bodyPitch = bodyPitch; s.bodyRoll = bodyRoll;
        System.arraycopy(segBend, 0, s.segBend, 0, 5);
        float dt = dying ? deathTime : 0f;
        s.death = dying ? Mth.clamp((dt - 100f) / 50f, 0f, 1f) : 0f;
        s.sleep = sleepAmt; s.eyesOpen = lids;
        s.legsBroken = legsBroken; s.armsBroken0 = armsBroken0; s.armsBroken1 = armsBroken1; s.down = downAmt;
        s.flinchBone = flinchBone; s.flinchAmt = Mth.clamp(flinchT / (float) FLINCH_TICKS, 0f, 1f);
        // he folds onto his belly as the legs go, and only goes into the ground once the body has lain there a while
        float rotFrom = FALL_AT + carcassTicks();
        s.bodyLift = dying ? -54f * smooth((dt - 100f) / 60f) - 175f * smooth((dt - rotFrom) / 100f) : -230f * digAmt;
        s.burrow = digAmt;
        s.time = tickCount;
        s.breath = breath; s.mouthOpen = mouth; s.grind = grind;
        s.headYaw = headYaw; s.headPitch = headPitch;
        s.lookX = look.x; s.lookY = look.y; s.lookZ = look.z; s.lookAmt = dying ? 0f : lookAmt;
        for (int i = 0; i < looks.length; i++) s.looks[i].set(looks[i]);
        s.lookCount = dying ? 1 : lookCount;
        s.reachX = reach.x; s.reachY = reach.y; s.reachZ = reach.z; s.reachAmt = reachAmt;
        s.holdArm = holdArm; s.holdNext = holdNext; s.holdT = holdT;
        s.attack = attacks.id; s.attackT = attacks.t; s.attackArg = attacks.arg;
        s.atkX = (float) attacks.aim.x; s.atkY = (float) attacks.aim.y; s.atkZ = (float) attacks.aim.z;
        if (gait.ready()) {
            gait.toModel(1f, getX(), getY(), getZ(), getYRot(), mountainScale(), feetS, tipsS);
            s.feet = feetS; s.tips = tipsS;
        } else { s.feet = null; s.tips = null; }
    }

    private void writeSyncedState() {
        RigState s = state;
        entityData.set(DATA_WALK, s.walkPhase); entityData.set(DATA_WALK_AMT, s.walkAmount); entityData.set(DATA_TURN, s.turn);
        entityData.set(DATA_PITCH, s.bodyPitch); entityData.set(DATA_ROLL, s.bodyRoll); entityData.set(DATA_LIFT, s.bodyLift);
        entityData.set(DATA_BEND, new org.joml.Quaternionf(s.segBend[0], s.segBend[1], s.segBend[3], s.segBend[4]));
        entityData.set(DATA_LOOK2, new org.joml.Vector3f(s.looks[0])); entityData.set(DATA_LOOK3, new org.joml.Vector3f(s.looks[1]));
        entityData.set(DATA_LOOK4, new org.joml.Vector3f(s.looks[2])); entityData.set(DATA_LOOK_N, s.lookCount);
        entityData.set(DATA_BREATH, s.breath); entityData.set(DATA_MOUTH, s.mouthOpen); entityData.set(DATA_GRIND, s.grind);
        entityData.set(DATA_HYAW, s.headYaw); entityData.set(DATA_HPITCH, s.headPitch);
        entityData.set(DATA_LX, s.lookX); entityData.set(DATA_LY, s.lookY); entityData.set(DATA_LZ, s.lookZ); entityData.set(DATA_LAMT, s.lookAmt);
        entityData.set(DATA_RX, s.reachX); entityData.set(DATA_RY, s.reachY); entityData.set(DATA_RZ, s.reachZ); entityData.set(DATA_RAMT, s.reachAmt);
        entityData.set(DATA_HOLD, (s.holdArm + 1) | ((s.holdNext + 1) << 12)); entityData.set(DATA_HOLD_T, s.holdT);
        for (int q = 0; q < 4; q++) entityData.set(DATA_POP[q], s.popped[q]);
        entityData.set(DATA_ATK, (s.attack << 16) | Math.min((int) s.attackT, 0xffff)); entityData.set(DATA_ATK_ARG, s.attackArg);
        entityData.set(DATA_AX, s.atkX); entityData.set(DATA_AY, s.atkY); entityData.set(DATA_AZ, s.atkZ);
        entityData.set(DATA_SLEEP, s.sleep); entityData.set(DATA_LIDS, s.eyesOpen);
        int st = isDeadOrDying() ? DYING : stage;
        entityData.set(DATA_ACT, ((strongBreath ? 1 : 0) << 24) | (st << 16) | Math.min(isDeadOrDying() ? deathTime : stageT, 0xffff));
    }

    private void readSyncedState(RigState s) {
        s.walkPhase = entityData.get(DATA_WALK); s.walkAmount = entityData.get(DATA_WALK_AMT); s.turn = entityData.get(DATA_TURN);
        s.bodyPitch = entityData.get(DATA_PITCH); s.bodyRoll = entityData.get(DATA_ROLL); s.bodyLift = entityData.get(DATA_LIFT);
        s.burrow = isDeadOrDying() ? 0f : Mth.clamp(-s.bodyLift / 230f, 0f, 1f);
        { org.joml.Quaternionf q = entityData.get(DATA_BEND); s.segBend[0] = q.x; s.segBend[1] = q.y; s.segBend[3] = q.z; s.segBend[4] = q.w; }
        s.looks[0].set(entityData.get(DATA_LOOK2)); s.looks[1].set(entityData.get(DATA_LOOK3));
        s.looks[2].set(entityData.get(DATA_LOOK4)); s.lookCount = entityData.get(DATA_LOOK_N);
        s.breath = entityData.get(DATA_BREATH); s.mouthOpen = entityData.get(DATA_MOUTH); s.grind = entityData.get(DATA_GRIND);
        s.headYaw = entityData.get(DATA_HYAW); s.headPitch = entityData.get(DATA_HPITCH);
        s.lookX = entityData.get(DATA_LX); s.lookY = entityData.get(DATA_LY); s.lookZ = entityData.get(DATA_LZ); s.lookAmt = entityData.get(DATA_LAMT);
        s.reachX = entityData.get(DATA_RX); s.reachY = entityData.get(DATA_RY); s.reachZ = entityData.get(DATA_RZ); s.reachAmt = entityData.get(DATA_RAMT);
        int h = entityData.get(DATA_HOLD);
        s.holdArm = (h & 0xfff) - 1; s.holdNext = (h >>> 12 & 0xfff) - 1; s.holdT = entityData.get(DATA_HOLD_T);
        for (int q = 0; q < 4; q++) s.popped[q] = entityData.get(DATA_POP[q]);
        int atk = entityData.get(DATA_ATK);
        s.attack = atk >>> 16; s.attackT = atk & 0xffff; s.attackArg = entityData.get(DATA_ATK_ARG);
        s.atkX = entityData.get(DATA_AX); s.atkY = entityData.get(DATA_AY); s.atkZ = entityData.get(DATA_AZ);
        s.sleep = entityData.get(DATA_SLEEP); s.eyesOpen = entityData.get(DATA_LIDS);
        int fl = entityData.get(DATA_FLINCH);
        s.flinchBone = (fl & 0xffff) == 0xffff ? -1 : (fl & 0xffff);
        s.flinchAmt = Mth.clamp((fl >>> 16) / (float) FLINCH_TICKS, 0f, 1f);
        s.legsScarred = entityData.get(DATA_LEGS_SCARRED);
        s.legsBroken = entityData.get(DATA_LEGS_BROKEN); s.armsBroken0 = entityData.get(DATA_ARMS_BROKEN0);
        s.armsBroken1 = entityData.get(DATA_ARMS_BROKEN1); s.down = entityData.get(DATA_DOWN);
        s.time = tickCount;
        float dt = isDeadOrDying() ? deathTime : 0f;
        s.death = isDeadOrDying() ? Mth.clamp((dt - 100f) / 50f, 0f, 1f) : 0f;
    }

    private static float smooth(float t) { t = Mth.clamp(t, 0f, 1f); return t * t * (3f - 2f * t); }

    private void ensureParts() {
        if (this.tickCount % 10 != 1 || isDeadOrDying()) return;
        for (int k = 0; k < parts.length; k++) {
            if (parts[k] != null && !parts[k].isRemoved()) continue;
            Vec3 at = partFeet(k);
            if (!level().hasChunkAt(BlockPos.containing(at))) { parts[k] = null; continue; }
            MountainPart p = new MountainPart(level(), this, k);
            p.moveWith(at.x, at.y, at.z);
            parts[k] = p;
            level().addFreshEntity(p);
        }
    }

    private void placeParts() {
        for (int k = 0; k < parts.length; k++) {
            MountainPart p = parts[k];
            if (p == null || p.isRemoved()) continue;
            Vec3 at = partFeet(k);
            p.moveWith(at.x, at.y, at.z);
        }
    }

    public boolean ownsPart(MountainPart p) { int k = p.index(); return k >= 0 && k < parts.length && parts[k] == p; }

    public List<MountainPart> parts() {
        List<MountainPart> out = new ArrayList<>();
        for (MountainPart p : parts) if (p != null && !p.isRemoved()) out.add(p);
        return out;
    }

    // ------------------------------------------------------------------ the top of his back, which you can walk on
    // The line of his spine (posed, in the world) and how thick he is along it; the walkable top of his back at any
    // spot is worked out from these, so it follows the real curve of his back and moves as he moves and breathes.
    private final List<Vec3> spine = new ArrayList<>();
    private final List<Double> spineR = new ArrayList<>();
    private final List<Integer> spineBone = new ArrayList<>();
    private @Nullable AABB backBounds;
    private double[][] topReach;
    /** only the top of his back, as far out to each side as you can stand */
    static final double BACK_WIDTH = 0.78;

    /** recomputed every tick from whichever pose this side has */
    private void updateBack() {
        spine.clear(); spineR.clear(); spineBone.clear();
        float s = mountainScale();
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        if (topReach == null) {                                                   // how high and how far out each slice of his back goes
            topReach = new double[rig.body.size()][2];
            for (int i = 0; i < rig.body.size(); i++) {
                float[] top = rig.body.get(i).top();
                double hi = rig.body.get(i).r(), out = rig.body.get(i).r();
                for (int k = 0; k < top.length; k++) if (!Float.isNaN(top[k])) { hi = Math.max(hi, top[k]); out = Math.max(out, Math.abs(k - MountainRig.TOP_HALF) + 1); }
                topReach[i][0] = hi; topReach[i][1] = out;
            }
        }
        for (int i = 0; i < rig.body.size(); i++) {
            MountainRig.BodySample b = rig.body.get(i);
            Vec3 w = toWorld(b.p(), poseForSide()[b.bone()]);
            double r = b.r() * s, hi = topReach[i][0] * s, out = topReach[i][1] * s;
            spine.add(w); spineR.add(r); spineBone.add(b.bone());
            x0 = Math.min(x0, w.x - out); x1 = Math.max(x1, w.x + out); y0 = Math.min(y0, w.y); y1 = Math.max(y1, w.y + hi);
            z0 = Math.min(z0, w.z - out); z1 = Math.max(z1, w.z + out);
        }
        backBounds = spine.isEmpty() || (isDeadOrDying() && !isCarcass()) ? null : new AABB(x0, y0, z0, x1, y1 + 1, z1);
        MountainCollision.track(this);
    }

    @Nullable AABB backBounds() { return isDeadOrDying() && !isCarcass() ? null : backBounds; }
    /** where leg k's foot is this tick (server pose), and the end it plants */
    public Vec3 legFootWorld(int k) { MountainRig.LegDef L = rig.legs[k]; return toWorld(L.foot(), pose[L.bones[2]]); }
    public Vec3 legKneeWorld(int k) { MountainRig.LegDef L = rig.legs[k]; return toWorld(L.joints[1], pose[L.bones[0]]); }

    /** the four points down leg k as it is drawn right now: hip, knee, mid, foot */
    public Vec3[] legLine(int k) {
        MountainRig.LegDef L = rig.legs[k];
        org.joml.Matrix4f[] ps = level().isClientSide ? clientPose : pose;
        return new Vec3[]{ toWorld(L.joints[0], ps[L.bones[0]]), toWorld(L.joints[1], ps[L.bones[1]]),
                toWorld(L.joints[2], ps[L.bones[2]]), footWorld(L) };
    }

    /** how fat leg k is where you would be holding on */
    public double legThickness(int k) { return rig.legs[k].thick * mountainScale(); }
    public int legCount() { return rig.legs.length; }
    public int legKind(int k) { return rig.legs[k].kind; }
    /** point i along his spine (0 = tail end), this tick */
    public Vec3 spinePoint(int i) { return spine.isEmpty() ? position() : spine.get(Mth.clamp(i, 0, spine.size() - 1)); }
    public int spineSize() { return spine.size(); }

    /** height of the top of his back above world spot x, z (NaN if that spot isn't over his back) */
    public double backTop(double x, double z) { int[] seg = new int[1]; return backTop(x, z, seg); }

    private double backTop(double x, double z, int[] segOut) {
        double best = Double.NaN;
        float s = Math.max(0.02f, mountainScale());
        for (int i = 0; i + 1 < spine.size(); i++) {
            Vec3 a = spine.get(i), b = spine.get(i + 1);
            double dx = b.x - a.x, dz = b.z - a.z, len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1e-6) continue;
            dx /= len; dz /= len;
            double along = (x - a.x) * dx + (z - a.z) * dz;
            if (along < -0.5 || along > len + 0.5) continue;
            double u = Mth.clamp(along / len, 0, 1);
            double lat = ((x - a.x) * -dz + (z - a.z) * dx) / s;            // to his side, in model blocks
            double ha = profile(rig.body.get(i), lat), hb = profile(rig.body.get(i + 1), lat);
            double h = Double.isNaN(ha) ? hb : Double.isNaN(hb) ? ha : ha + (hb - ha) * u;
            if (Double.isNaN(h)) continue;
            double top = a.y + (b.y - a.y) * u + h * s;
            if (Double.isNaN(best) || top > best) { best = top; segOut[0] = u < 0.5 ? i : i + 1; }
        }
        return best;
    }

    private static double profile(MountainRig.BodySample b, double lat) {
        double f = lat + MountainRig.TOP_HALF;
        int i = (int) Math.floor(f);
        if (i < 0 || i + 1 >= b.top().length) return Double.NaN;
        double v0 = b.top()[i], v1 = b.top()[i + 1], w = f - i;
        if (Double.isNaN(v0)) return v1;
        if (Double.isNaN(v1)) return v0;
        return v0 + (v1 - v0) * w;
    }

    /**
     * The bits of his back an entity moving through box could land on or bump into: a shell of 1x1 columns.
     * A column whose top is at most SOFT above the entity's feet (or that the entity is already sunk into) becomes a
     * floor right at its feet instead, so it walks straight over small bumps; MountainCollision.pushUp then lifts it
     * onto the real surface. Taller columns are walls, unless floorsOnly.
     */
    List<AABB> backShellIn(AABB box, double feet, boolean floorsOnly) {
        List<AABB> out = new ArrayList<>();
        int xa = Mth.floor(box.minX), xb = Mth.floor(box.maxX), za = Mth.floor(box.minZ), zb = Mth.floor(box.maxZ);
        if ((long) (xb - xa + 1) * (zb - za + 1) > 400) return out;             // something huge: skip
        double shell = 2.0 + 2.0 * Math.min(1f, mountainScale());
        for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) {
            double top = backTop(x + 0.5, z + 0.5);
            if (Double.isNaN(top)) continue;
            if (top <= feet + 1e-4) {                                             // under you: a floor
                if (top < box.minY - 0.01) continue;
                out.add(new AABB(x, top - shell, z, x + 1, top, z + 1));
            } else if (top - feet <= MountainCollision.SOFT || top - shell < feet) {    // a small bump, or you're in him
                out.add(new AABB(x, Math.min(top - shell, feet - 1), z, x + 1, feet, z + 1));
            } else if (!floorsOnly) {                                              // a real wall of him
                if (top - shell > box.maxY) continue;
                out.add(new AABB(x, top - shell, z, x + 1, top, z + 1));
            }
        }
        return out;
    }

    /** the highest top of his back under box that someone whose feet were at feet0 should be lifted onto (or NaN) */
    double surfaceUnder(AABB box, double feet0) {
        double shell = 2.0 + 2.0 * Math.min(1f, mountainScale());
        double best = Double.NaN;
        int xa = Mth.floor(box.minX), xb = Mth.floor(box.maxX - 1e-7), za = Mth.floor(box.minZ), zb = Mth.floor(box.maxZ - 1e-7);
        if ((long) (xb - xa + 1) * (zb - za + 1) > 64) return best;
        for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) {
            double top = backTop(x + 0.5, z + 0.5);
            if (Double.isNaN(top) || top <= box.minY) continue;
            if (top - feet0 <= MountainCollision.SOFT || top - shell < feet0) best = Double.isNaN(best) ? top : Math.max(best, top);
        }
        return best;
    }

    // ------------------------------------------------------------------ things standing on him ride along
    /** a point stuck to one of his bones */
    public record Anchor(int bone, Vector3f local, int part) {}
    private record Riding(Anchor at, Vec3 was, float yaw) {}
    private final Map<Integer, Riding> riding = new HashMap<>();

    /** The solid part of his back that e is standing on, as a point fixed to that part's bone (or null). */
    public @Nullable Anchor anchorUnder(Entity e) {
        int[] seg = new int[1];
        double top = backTop(e.getX(), e.getZ(), seg);
        if (Double.isNaN(top) || e.getY() > top + 0.9 || e.getY() < top - 2.5) return null;
        return anchorAt(e.position(), spineBone.get(seg[0]), 0);
    }

    public Anchor anchorAt(Vec3 world, int bone, int part) {
        Vec3 m = worldToModelPoint(world);
        Vector3f local = new Matrix4f(poseForSide()[bone]).invertAffine().transformPosition(new Vector3f((float) m.x, (float) m.y, (float) m.z));
        return new Anchor(bone, local, part);
    }

    /** Where something carried on a part should now stand: moved with his body, and never sunk into the part's top. */
    public Vec3 carriedTo(Anchor a, Vec3 now) {
        double top = backTop(now.x, now.z);
        if (!Double.isNaN(top) && now.y < top - 2.5 && now.y > top - 5) return new Vec3(now.x, top - 2.5, now.z);   // pushUp does the rest, gently
        return now;
    }

    public Vec3 anchorWorld(Anchor a) { return toWorld(a.local(), poseForSide()[a.bone()]); }

    /** Server: mobs and items on his back move and turn with him (players are carried by their own client). */
    private void carryRiders() {
        if (isDeadOrDying()) { riding.clear(); return; }
        for (Map.Entry<Integer, Riding> en : riding.entrySet()) {
            Entity r = level().getEntity(en.getKey());
            if (r == null || r.isRemoved() || r.isPassenger()) continue;
            Riding ri = en.getValue();
            Vec3 d = anchorWorld(ri.at()).subtract(ri.was());
            if (d.lengthSqr() > 36) continue;
            Vec3 to = carriedTo(ri.at(), new Vec3(r.getX() + d.x, r.getY() + d.y, r.getZ() + d.z));
            r.setPos(to.x, to.y, to.z);
            float dy = Mth.wrapDegrees(getYRot() - ri.yaw());
            r.setYRot(r.getYRot() + dy);
            if (r instanceof LivingEntity le) { le.setYBodyRot(le.yBodyRot + dy); le.setYHeadRot(le.getYHeadRot() + dy); }
        }
        riding.clear();
        AABB bb = backBounds();
        if (bb == null) return;
        for (Entity r : level().getEntities(this, bb, x -> !(x instanceof MountainPart) && !(x instanceof MountainEntity) && !(x instanceof Player)
                && !(x instanceof GripSeat) && !(x instanceof GooGlob) && !x.isPassenger())) {
            Anchor a = anchorUnder(r);
            if (a != null) riding.put(r.getId(), new Riding(a, r.position(), getYRot()));
        }
    }

    // ------------------------------------------------------------------ coordinates
    private Matrix4f entityMatrix() {
        return new Matrix4f().rotateY((180f - getYRot()) * Mth.DEG_TO_RAD).scale(mountainScale());
    }

    public Vec3 toWorld(Vector3f model, Matrix4f bone) {
        Vector3f tmp = new Vector3f();
        bone.transformPosition(model, tmp);
        entityMatrix().transformPosition(tmp);
        return new Vec3(getX() + tmp.x, getY() + tmp.y, getZ() + tmp.z);
    }

    private Vec3 worldFromModelDir(Vector3f d) {
        Vector3f v = new Vector3f(d);
        new Matrix4f().rotationY((180f - getYRot()) * Mth.DEG_TO_RAD).transformDirection(v);
        return new Vec3(v.x, v.y, v.z);
    }

    public Vec3 worldToModelPoint(Vec3 w) {
        Vec3 d = w.subtract(position());
        Vector3f v = new Vector3f((float) d.x, (float) d.y, (float) d.z);
        new Matrix4f().rotationY(-(180f - getYRot()) * Mth.DEG_TO_RAD).transformDirection(v);
        float s = Math.max(mountainScale(), 0.001f);
        return new Vec3(v.x / s, v.y / s, v.z / s);
    }

    private Matrix4f[] poseForSide() { return level().isClientSide ? clientPose : pose; }

    public Vec3 mouthWorld() { return toWorld(rig.mouth, poseForSide()[rig.head]); }
    public Vec3 mouthDir() {
        Vector3f f = poseForSide()[rig.head].transformDirection(new Vector3f(0, 0, -1));
        return worldFromModelDir(f).normalize();
    }
    public Vec3 handWorld(int arm) { MountainRig.ArmDef A = rig.arms[arm]; return toWorld(A.handPoint, poseForSide()[A.hand]); }

    // ------------------------------------------------------------------ helpers
    List<LivingEntity> victims(Vec3 c, double r) {
        return level().getEntitiesOfClass(LivingEntity.class, AABB.ofSize(c, r * 2, r * 2, r * 2),
                e -> e != this && !spares(e) && e.isAlive() && !(e instanceof HeartEntity) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()))
                        && e.getBoundingBox().getCenter().distanceTo(c) < r + e.getBbWidth());
    }

    void sendParticles(ParticleOptions p, Vec3 at, int count, double spreadH, double spreadV, double speed) {
        if (!(level() instanceof ServerLevel sl)) return;
        for (ServerPlayer sp : sl.players()) {
            if (sp.distanceToSqr(at) < 512 * 512) sl.sendParticles(sp, p, true, at.x, at.y, at.z, count, spreadH, spreadV, spreadH, speed);
        }
    }

    /** one particle with a velocity (count 0 = the spread numbers are the velocity) */
    void sendParticle(ParticleOptions p, Vec3 at, Vec3 vel) {
        if (!(level() instanceof ServerLevel sl)) return;
        for (ServerPlayer sp : sl.players()) {
            if (sp.distanceToSqr(at) < 400 * 400) sl.sendParticles(sp, p, true, at.x, at.y, at.z, 0, vel.x, vel.y, vel.z, 1.0);
        }
    }

    // ------------------------------------------------------------------ boss bars and chunk tickets
    private List<ServerPlayer> barPlayers() {
        List<ServerPlayer> out = new ArrayList<>();
        if (!(level() instanceof ServerLevel sl)) return out;
        double r = 120 + 300 * mountainScale();
        for (ServerPlayer p : sl.players()) if (p.distanceToSqr(this) < r * r) out.add(p);
        for (ServerPlayer p : Innards.playersInside(this)) if (!out.contains(p)) out.add(p);
        return out;
    }

    private void updateBars() {
        if (bar == null) {
            bar = new MountainBar(MountainBar.idFor(getUUID(), "health"), this.getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_20);
            eyeBar = new MountainBar(MountainBar.idFor(getUUID(), "eyes"), Component.translatable("bar.mountain_breathes.eyes", eyesOpenNow(), rig.eyes.length), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);
        }
        bar.setProgress(Mth.clamp(hp / Math.max(1f, healthMax()), 0f, 1f));
        bar.setColor(crippled() ? BossEvent.BossBarColor.PURPLE
                : isGuardian() && playerAnger <= 0 ? BossEvent.BossBarColor.GREEN : BossEvent.BossBarColor.RED);
        if (this.tickCount % 10 == 0)
            bar.setName(crippled() ? Component.translatable("bar.mountain_breathes.down_for_good", getDisplayName()) : getDisplayName());
        int open = eyesOpenNow();
        eyeBar.setProgress(open / (float) rig.eyes.length);
        if (this.tickCount % 10 == 0) {
            eyeBar.setName(Component.translatable("bar.mountain_breathes.eyes", open, rig.eyes.length));
            List<ServerPlayer> want = isDeadOrDying() || !MountainConfig.V.bossBar ? List.of() : barPlayers();
            for (MountainBar b : List.of(bar, eyeBar)) {
                for (ServerPlayer p : new ArrayList<>(b.getPlayers())) if (!want.contains(p)) b.removePlayer(p);
                for (ServerPlayer p : want) if (!b.getPlayers().contains(p)) b.addPlayer(p);
            }
        }
    }

    /** holds a chunk open and running, so an entity put down there starts ticking straight away */
    public static void holdChunkAt(ServerLevel sl, BlockPos p, int id) {
        sl.getChunkSource().addRegionTicket(TICKET, new ChunkPos(p), 3, id);
    }

    /**
     * The moment he turns up, hold his own chunk open. Big, he is put down hundreds of blocks away, well past the
     * chunks the game keeps running for a player, so he would never take his first tick: he sat there unloaded and
     * invisible until somebody walked out to him. One ticket is enough to get him going; from his first tick
     * keepChunksLoaded takes over.
     */
    public void forceChunks() {
        if (!(level() instanceof ServerLevel sl) || isRemoved()) return;
        holdChunkAt(sl, blockPosition(), getId());
    }

    private void keepChunksLoaded() {
        // underground he covers three blocks a tick, which is further than the piece of world he holds open is
        // wide: under there it has to be held open more often and wider, or he runs off the edge of it
        boolean fast = digState != 0;
        if (!MountainConfig.V.chunkLoading || this.tickCount % (fast ? 5 : 20) != 0 || isRemoved()) return;
        // barPlayers() includes anyone he is carrying. Somebody driving him counts, and so does a run under the
        // ground: sent to a far spot he would otherwise stop running the moment he left everybody behind, and
        // be left half way there with nothing to start him again.
        // and while he is breaking the world, whatever else is going on: he must not stop half way up
        if (!ridden() && digState == 0 && barPlayers().isEmpty() && goal == null && !unmaking()) return;
        var cs = ((ServerLevel) level()).getChunkSource();
        cs.addRegionTicket(TICKET, new ChunkPos(blockPosition()), fast ? 6 : 3, getId());
        Set<Long> seen = new HashSet<>();
        for (int k = 0; k < parts.length; k++) {
            ChunkPos cp = new ChunkPos(BlockPos.containing(partFeet(k)));
            if (seen.add(cp.toLong())) cs.addRegionTicket(TICKET, cp, 0, getId());
        }
    }

    // ------------------------------------------------------------------ client side
    private final Gait gaitC = new Gait(rig);
    private final float[] feetC = new float[rig.legs.length * 3], tipsC = new float[rig.legs.length * 3];
    private final float[] feetR = new float[rig.legs.length * 3], tipsR = new float[rig.legs.length * 3];

    private void clientTick() {
        prevC.set(curC);
        readSyncedState(curC);
        if (this.tickCount <= 1) prevC.set(curC);
        float dP = curC.walkPhase - prevC.walkPhase;
        if (dP < -500f) dP += 1000f; else if (dP > 500f) dP -= 1000f;
        float s = mountainScale();
        if (curC.attack == RigState.WHIP) gaitC.setSweep(curC.attackArg, curC.attackT / (float) MountainAttacks.length(RigState.WHIP), curC.atkX, curC.atkZ);
        else if (curC.attack == RigState.STOMP) gaitC.setStomp(curC.attackArg, curC.attackT / (float) MountainAttacks.length(RigState.STOMP), curC.atkX, curC.atkZ);
        else gaitC.setWhip(-1, 0f, 0, 0);
        gaitC.setBroken(curC.legsBroken);
        gaitC.tick(getX(), getY(), getZ(), getYRot(), s, curC.walkPhase, Math.max(0f, dP), tickCount, isDeadOrDying(), Math.max(curC.sleep, curC.down), this::groundOrNaN, clientFootHook);
        gaitC.toModel(1f, getX(), getY(), getZ(), getYRot(), s, feetC, tipsC);
        curC.feet = feetC; curC.tips = tipsC;
        rig.computePose(curC, clientPose, null);
        clientPoseReady = true;
        updateBack();
        if (CLIENT_LEG_DEBUG) debugClientLegs();
        clientTickHook.accept(this);
    }

    private static final boolean CLIENT_LEG_DEBUG = Boolean.getBoolean("mountain.legdebug");
    private double[] dbgGap, dbgMove; private Vec3[] dbgFirst, dbgKnee;
    private int dbgBad, dbgJumps, dbgUnder, dbgTicks, dbgWorstK, dbgTrace, dbgTrace2; private double dbgWorst; private final int[] dbgJumpLeg = new int[64];
    /** dev only: which legs, as drawn, never come down to the ground or never move */
    private void debugClientLegs() {
        int n = rig.legs.length;
        if (dbgGap == null) { dbgGap = new double[n]; dbgMove = new double[n]; dbgFirst = new Vec3[n]; java.util.Arrays.fill(dbgGap, 1e9); }
        boolean every5 = tickCount % 5 == 0;
        for (int k = 0; k < n && every5; k++) {
            MountainRig.LegDef L = rig.legs[k];
            Vec3 f = toWorld(L.foot(), clientPose[L.bones[2]]);
            double g = groundOrNaN(f.x, f.z);
            if (!Double.isNaN(g)) dbgGap[k] = Math.min(dbgGap[k], f.y - g);
            Vec3 rel = worldToModelPoint(f);
            if (dbgFirst[k] == null) dbgFirst[k] = rel; else dbgMove[k] = Math.max(dbgMove[k], rel.distanceTo(dbgFirst[k]));
        }
        // how far each drawn leg end is from where the gait wants it, and how far its knee jumped since last check
        if (gaitC.ready()) {
            float s = mountainScale();
            int bad = 0, jumps = 0, under = 0; double worst = 0; int worstK = -1;
            if (dbgKnee == null) dbgKnee = new Vec3[n];
            for (int k = 0; k < n; k++) {
                MountainRig.LegDef L = rig.legs[k];
                Vec3 e = toWorld(L.end(), clientPose[L.bones[2]]);
                double err = Math.sqrt(Math.pow(e.x - gaitC.x[k], 2) + Math.pow(e.y - gaitC.y[k], 2) + Math.pow(e.z - gaitC.z[k], 2));
                if (err > 3 * s + 1) bad++;
                if (err > worst) { worst = err; worstK = k; }
                if (err > 15 && dbgTrace2 < 40 && tickCount % 20 == 0) {
                    dbgTrace2++;
                    Vec3 hip = toWorld(L.joints[0], clientPose[L.bones[0]]);
                    net.jj.mountain.MountainMod.LOG.info(String.format("FAR clientGround=%.1f motionBlocking=%d loaded=%s ", groundOrNaN(gaitC.x[k], gaitC.z[k]),
                        level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(gaitC.x[k]), Mth.floor(gaitC.z[k])), level().hasChunkAt(BlockPos.containing(gaitC.x[k], getY(), gaitC.z[k]))) + String.format("t=%d leg %d kind %d seg %d hip %.1f,%.1f,%.1f end %.1f,%.1f,%.1f target %.1f,%.1f,%.1f hip->target %.1f (rest hip->end %.1f) pitch %.2f roll %.2f bends %.2f %.2f %.2f %.2f y %.1f",
                        tickCount, k, L.kind, rig.legSegment(L), hip.x, hip.y, hip.z, e.x, e.y, e.z, gaitC.x[k], gaitC.y[k], gaitC.z[k],
                        hip.distanceTo(new Vec3(gaitC.x[k], gaitC.y[k], gaitC.z[k])), L.joints[0].distance(L.end()) * s,
                        curC.bodyPitch, curC.bodyRoll, curC.segBend[0], curC.segBend[1], curC.segBend[3], curC.segBend[4], getY()));
                }
                Vec3 kn = toWorld(L.joints[1], clientPose[L.bones[1]]);
                if (dbgKnee[k] != null && kn.distanceTo(dbgKnee[k]) > 2.5 * s + 1) {
                    jumps++; dbgJumpLeg[k]++;
                    if (dbgTrace < 60) {
                        dbgTrace++;
                        Vec3 hip = toWorld(L.joints[0], clientPose[L.bones[0]]);
                        net.jj.mountain.MountainMod.LOG.info(String.format("KNEEJUMP t=%d leg %d kind %d jump %.1f swing %s hip->end %.1f (A %.1f B %.1f) knee %.1f,%.1f,%.1f was %.1f,%.1f,%.1f target %.1f,%.1f,%.1f",
                            tickCount, k, L.kind, kn.distanceTo(dbgKnee[k]), gaitC.swinging(k), hip.distanceTo(new Vec3(gaitC.x[k], gaitC.y[k], gaitC.z[k])),
                            L.joints[0].distance(L.joints[1]), L.joints[1].distance(L.end()), kn.x, kn.y, kn.z, dbgKnee[k].x, dbgKnee[k].y, dbgKnee[k].z, gaitC.x[k], gaitC.y[k], gaitC.z[k]));
                    }
                }
                dbgKnee[k] = kn;
                Vec3 k2 = toWorld(L.joints[2], clientPose[L.bones[2]]);
                double g1 = groundOrNaN(kn.x, kn.z), g2 = groundOrNaN(k2.x, k2.z);
                if ((!Double.isNaN(g1) && kn.y < g1 - 2) || (!Double.isNaN(g2) && k2.y < g2 - 2)) under++;
            }
            dbgBad += bad; dbgJumps += jumps; dbgUnder += under; dbgTicks++;
            if (worst > dbgWorst) { dbgWorst = worst; dbgWorstK = worstK; }
            if (tickCount % 40 == 0 && MountainRig.LEGDBG) {
                StringBuilder sb2 = new StringBuilder();
                for (int k = 0; k < n; k++) {
                    int a1 = MountainRig.dbgSwitch[k], a2 = MountainRig.dbgFar[k], a3 = MountainRig.dbgNear[k], a4 = MountainRig.dbgPsi[k];
                    if (a1 + a2 + a3 + a4 > 0) sb2.append(String.format(" %d%s:sw%d/far%d/near%d/turn%d", k, rig.legs[k].kind == 0 ? "c" : "s", a1, a2, a3, a4));
                    MountainRig.dbgSwitch[k] = 0; MountainRig.dbgFar[k] = 0; MountainRig.dbgNear[k] = 0; MountainRig.dbgPsi[k] = 0;
                }
                net.jj.mountain.MountainMod.LOG.info("LEGWHY" + sb2);
            }
            if (tickCount % 40 == 0) {
                net.jj.mountain.MountainMod.LOG.info(String.format("LEGSTAT t=%d pos=%.0f,%.0f,%.0f legs off target (avg per check) %.2f worst %.1f (leg %d) knee jumps %d knees in ground (avg) %.2f broken %d/%d down %.2f",
                        tickCount, getX(), getY(), getZ(), dbgBad / (double) dbgTicks, dbgWorst, dbgWorstK, dbgJumps, dbgUnder / (double) dbgTicks,
                        Long.bitCount(curC.legsBroken), Long.bitCount(curC.armsBroken0) + Long.bitCount(curC.armsBroken1), curC.down));
                dbgBad = 0; dbgJumps = 0; dbgUnder = 0; dbgTicks = 0; dbgWorst = 0; dbgWorstK = -1;
            }
        }
        if (tickCount % 200 == 0) {
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < n; k++) sb.append(String.format(" %d:%s/%.1f/%.0f", k, rig.legs[k].kind == 0 ? "c" : rig.legs[k].kind == 1 ? "s" : "t", dbgGap[k], dbgMove[k]));
            net.jj.mountain.MountainMod.LOG.info("client legs (gap/moved):{}", sb);
        }
    }

    /** Blend of the last two ticks for smooth drawing; the feet are placed from where they are in the world. */
    public void renderState(float partial, RigState out) {
        out.lerp(partial, prevC, curC);
        out.time = tickCount + partial;
        float dt = isDeadOrDying() ? deathTime + partial : 0f;
        out.death = isDeadOrDying() ? Mth.clamp((dt - 100f) / 50f, 0f, 1f) : 0f;
        if (gaitC.ready()) {
            double x = Mth.lerp(partial, xOld, getX()), y = Mth.lerp(partial, yOld, getY()), z = Mth.lerp(partial, zOld, getZ());
            gaitC.toModel(partial, x, y, z, Mth.rotLerp(partial, yRotO, getYRot()), mountainScale(), feetR, tipsR);
            out.feet = feetR; out.tips = tipsR;
        } else { out.feet = null; out.tips = null; }
        out.legLift = null;
    }

    public boolean clientFootLifted(int k) { return gaitC.swinging(k); }

    public Matrix4f[] clientPose() { return clientPose; }
    public boolean clientPoseReady() { return clientPoseReady; }
    public RigState clientState() { return curC; }

    /** Where hitbox part k sits (feet position), from whichever side's pose is current. */
    public Vec3 partFeet(int k) {
        MountainRig.PartDef d = rig.parts.get(k);
        Matrix4f[] ps = poseForSide();
        Vec3 w = toWorld(d.center(), ps[d.bone()]);
        return new Vec3(w.x, w.y - d.height() * mountainScale() / 2, w.z);
    }

    public Vec3 clientWorld(Vector3f model, int bone) { return toWorld(model, clientPose[bone]); }
}
