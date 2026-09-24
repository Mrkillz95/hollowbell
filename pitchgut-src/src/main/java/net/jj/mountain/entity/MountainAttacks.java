package net.jj.mountain.entity;

import net.jj.mountain.MountainConfig;
import net.jj.mountain.rig.MountainRig;
import net.jj.mountain.rig.RigState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

import static net.jj.mountain.rig.RigState.*;

/**
 * Everything he does to you besides breathing and grabbing: lobbing goo, whipping a tentacle, slamming hands on
 * whoever is on his back, the stare of all his eyes, rearing up and crashing down, throwing up a flood of goo,
 * and spitting darts from the holes in his skin. One at a time, picked by where you are.
 */
public final class MountainAttacks {
    private final MountainEntity m;
    private final MountainRig rig;
    int id = NONE, t, arg = -1, last = NONE;
    /** where it is aimed, in the world */
    Vec3 aim = Vec3.ZERO;

    /**
     * An aim he could actually reach. Anything further off is pulled back to the edge of that: a goo lump thrown
     * at something on the far side of the world would fly hundreds of blocks a tick and drag the whole world in
     * behind it.
     */
    private Vec3 near(Vec3 p) {
        double r = 300 * m.mountainScale() + 120;
        Vec3 c = m.position();
        double dx = p.x - c.x, dz = p.z - c.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len <= r || len <= 1.0E-6) return p;
        double f = r / len;
        return new Vec3(c.x + dx * f, p.y, c.z + dz * f);
    }
    private int cooldown = 100;
    private final Set<Integer> hit = new HashSet<>();
    private Vec3 impact = Vec3.ZERO;
    private double ring;

    MountainAttacks(MountainEntity m) { this.m = m; this.rig = m.rig; }

    /** what each one is called, in the book and in the commands */
    public static final String[] NAMES = {"", "goo_lob", "leg_sweep", "hand_slam", "gaze", "body_slam", "vomit",
            "darts", "tentacle_eruption", "loose_eyes", "scream", "tongue", "leg_stomp", "goo_storm", "eye_storm",
            "draw_in", "tear_off"};

    public static int length(int id) {
        return switch (id) {
            case ARTILLERY -> 92; case WHIP -> 44; case SLAM -> 44; case GAZE -> 64; case REAR -> 96; case VOMIT -> 90; case DARTS -> 40;
            case ERUPT -> 96; case SWARM -> 64; case SCREAM -> 72; case TONGUE -> 140; case STOMP -> 52;
            case GOO_STORM -> 130; case EYE_STORM -> 120; case DRAW -> 330; case SPLIT -> 120; case UNMAKE -> 470;
            default -> 0;
        };
    }

    boolean active() { return id != NONE; }
    boolean usesMouth() { return id == ARTILLERY || id == VOMIT || id == SCREAM || id == TONGUE || id == GOO_STORM || id == DRAW; }
    boolean holdsStill() { return id == UNMAKE || id == REAR || id == VOMIT || id == ARTILLERY || id == WHIP || id == SCREAM || id == TONGUE || id == STOMP
            || id == GOO_STORM || (id == EYE_STORM && t > 25) || (id == DRAW && t > 40) || (id == SPLIT && t > 30); }
    /** all his eyes burn for the stare, for the storm, and for the last thing he does */
    public static boolean eyesBurn(int id) { return id == GAZE || id == EYE_STORM || id == UNMAKE; }
    boolean controlsArms() { return id == SLAM; }

    void stop() {
        if (id == TONGUE) m.letGoOfTongue();
        id = NONE; t = 0; arg = -1; done = false; forced = null;
        boolean wasStorm = !stormOn.isEmpty();
        stormOn.clear(); gazeOn.clear();                  // nothing held on to between one attack and the next
        if (wasStorm) tellTheStorm();                     // and everybody watching is told the beams are out
    }
    private boolean done;
    private @Nullable LivingEntity forced;

    // ------------------------------------------------------------------ choosing
    void tick(@Nullable LivingEntity target) {
        if (m.isDeadOrDying()) { stop(); return; }
        if (id != NONE) { run(target); return; }
        if (cooldown > 0) { cooldown--; return; }
        if (target == null || !m.isAngry() || target.level() != m.level()) return;
        choose(target);
    }

    /** /mountain attack: start one now */
    public boolean force(int which, @Nullable LivingEntity target) {
        if (m.isDeadOrDying() || which <= NONE || which > LAST_ATTACK) return false;
        if (target == null) {
            // whatever is nearest, but only out to what he can actually reach: a target on the far side of the
            // world would send everything he throws across thousands of chunks
            double r = 300 * m.mountainScale() + 80;
            LivingEntity best = null; double bd = r * r;
            // never somebody he has been told to leave alone: aiming the move at them was what made him look
            // like he was attacking the one player he will not touch
            for (Player p : m.level().players()) { double d = p.distanceToSqr(m); if (!p.isSpectator() && !p.isCreative() && !m.spares(p) && d < bd) { bd = d; best = p; } }
            if (best == null) {
                for (LivingEntity e : m.level().getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(r),
                        e -> e.isAlive() && !(e instanceof MountainEntity) && !(e instanceof HeartEntity) && !(e instanceof Player)
                                && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand) && !m.spares(e))) {
                    double d = e.distanceToSqr(m);
                    if (d < bd) { bd = d; best = e; }
                }
            }
            target = best;
        }
        if (target != null && m.getTarget() == null && !(target instanceof Player)) m.setTarget(target);
        // tearing a piece off himself and breathing the world in need nobody standing in front of him
        if (target == null && which != SPLIT && which != DRAW) return false;
        stop();
        start(which, target);
        forced = target;                 // a calm one drops his target at once; the attack still goes where it was sent
        return true;
    }

    /**
     * The last thing he does. Twenty seconds of it, and he is not getting up afterwards. Like the sunder it is
     * never chosen, only asked for, and only of a Mountain who has almost nothing left.
     */
    boolean forceUnmake() {
        if (m.isDeadOrDying()) return false;
        stop();
        id = UNMAKE; t = 0; arg = -1; hit.clear(); ring = 0;
        aim = m.position();
        Vec3 head = m.toWorld(rig.headCenter, m.pose[rig.head]);
        m.sound(head, net.jj.mountain.ModSounds.RAGE, 5f, 0.4f);
        m.sound(head, net.jj.mountain.ModSounds.SCREAM, 5f, 0.35f);
        return true;
    }

    /** the burning: the storm's machinery, pointed at everything alive at once */
    void burnAll(java.util.List<LivingEntity> them) {
        stormOn.clear();
        stormOn.addAll(them);
        tellTheStorm();
        m.watchAll(stormOn);
        for (LivingEntity e : stormOn) {
            e.hurt(m.damageSources().indirectMagic(m, m), m.dmg(2.0f + 3.5f * Math.max(0.05f, m.mountainScale()), e));
            e.igniteForSeconds(5);
            if (e instanceof Player p) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0), m);
            m.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, e.getEyePosition(), 16, 0.8, 0.8, 0.06);
        }
    }

    /**
     * Twenty seconds in beats, not twenty seconds of noise. Nothing at all while the sky goes over, then he
     * comes up, then his eyes open a bank at a time, then the burning — and then everything stops. Forty ticks
     * of dead silence with him standing at his full height and nothing coming out of him at all, and then he
     * comes down. The stillness is the loudest part of it.
     */
    private void unmake(float s) {
        Vec3 head = m.toWorld(rig.headCenter, m.pose[rig.head]);
        Vec3 c = m.position();

        // ---- one low note, and the sky goes over. He has not moved.
        if (t == 8) m.sound(head, net.jj.mountain.ModSounds.WEAK, 4f, 0.25f);

        // ---- he comes up off his front legs, and takes his time about it
        if (t == 70) m.sound(head, net.jj.mountain.ModSounds.ROAR, 5f, 0.3f);
        if (t > 30 && t < 140 && t % 10 == 0)
            m.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.add(0, 2, 0), 24, 22 * s + 8, 2, 0.01);

        // ---- held at the top: his eyes come open a bank at a time, each with its own note
        if (t >= 140 && t < 200 && (t - 140) % 12 == 0) {
            float k = (t - 140) / 12f;
            m.sound(head, net.jj.mountain.ModSounds.WEAK, 3f, 0.55f + 0.14f * k);
            m.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.add(0, 12, 0), 30, 26 * s + 9, 15, 0.0);
        }

        // ---- and then everything he has left, all at once
        if (t == 200) m.sound(head, net.jj.mountain.ModSounds.SCREAM, 5f, 0.4f);
        if (t == 286) m.sound(head, net.jj.mountain.ModSounds.RAGE, 5f, 0.45f);
        if (t >= 202 && t < 350 && t % 6 == 0) m.unmakeBurn();
        if (t >= 200 && t < 350 && t % 4 == 0) {
            m.sendParticles(ParticleTypes.ASH, c.add(0, 7, 0), 70, 40 * s + 14, 11, 0.0);
            m.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.add(0, 10, 0), 22, 30 * s + 10, 13, 0.02);
        }

        // ---- and then nothing. The beams go out, the ash stops, and he just stands there.
        if (t == 350) {
            stormOn.clear();
            tellTheStorm();
            m.sound(head, net.jj.mountain.ModSounds.BREATH_IN, 5f, 0.22f);
            if (m.level() instanceof net.minecraft.server.level.ServerLevel sl)
                for (var sp : sl.getServer().getPlayerList().getPlayers())
                    sp.displayClientMessage(Component.translatable("message.mountain_breathes.unmake_still"), false);
        }

        if (t == 400) m.unmakeNow();
    }

    private void choose(LivingEntity target) {
        float s = Math.max(0.02f, m.mountainScale());
        Vec3 mp = m.worldToModelPoint(target.position());
        double side = Math.abs(mp.x), ahead = -mp.z;
        double far = Math.hypot(mp.x, mp.z - 40);
        boolean back = m.onHisBack(target);
        boolean restful = m.stage == MountainEntity.REST;
        int[] w = new int[LAST_ATTACK + 1];
        boolean big = s >= 0.15f;                     // a tiny one doesn't call up tentacles or throw eyes
        if (back) { w[SLAM] = 5; w[DARTS] = 3; }
        else if (ahead < 60 && mp.z < 200 && side < 150) { w[WHIP] = 5; w[STOMP] = 4; w[DARTS] = 2; w[REAR] = 1; }
        else if (ahead >= 60 && far < 230 && side < ahead + 40) {
            w[REAR] = 3; w[GAZE] = 1; if (big) w[ERUPT] = 1; if (restful) { w[VOMIT] = 3; w[ARTILLERY] = 1; w[SCREAM] = 2; w[TONGUE] = ahead < 170 ? 4 : 0; }
        }
        else { w[GAZE] = 1; w[REAR] = far < 260 ? 1 : 0; if (big) { w[ERUPT] = 3; w[SWARM] = 2; } if (restful) { w[ARTILLERY] = 3; w[SCREAM] = far < 300 ? 1 : 0; } }
        // the two big ones. He only reaches for either once he is properly hurt, and never often.
        int ph = m.phase();
        if (ph >= 1 && big && !m.isAPiece()) w[DRAW] = far < 320 ? 3 : 1;
        // the lump out of his own side is the book holder's line to call: he only decides it himself when
        // there is nobody carrying the book to decide it for him
        if (ph >= 1 && big && !m.isAPiece() && !m.someoneHasTheBook() && m.healthNow() > m.healthMax() * 0.12f) w[SPLIT] = 2;
        if (m.tickCount - lastUse[DRAW] < 900) w[DRAW] = 0;
        if (m.tickCount - lastUse[SPLIT] < 1800) w[SPLIT] = 0;
        // half gone he starts spraying the ground; a quarter gone every eye he has left opens at once
        if (ph >= 1 && big) w[GOO_STORM] = far < 300 ? 4 : 2;
        if (ph >= 2) w[EYE_STORM] = 5;
        // flat on his belly for good: the moves that need legs under him are out, and everything else is in
        if (m.crippled()) {
            w[STOMP] = 0; w[WHIP] = 0; w[REAR] = 0;
            w[GAZE] = Math.max(w[GAZE], 3); w[DARTS] = Math.max(w[DARTS], 3); w[SCREAM] = Math.max(w[SCREAM], 2);
            w[DRAW] = Math.max(w[DRAW], 3);                // flat on his belly, pulling you in is all he has left
            w[ARTILLERY] = Math.max(w[ARTILLERY], 3); w[VOMIT] = Math.max(w[VOMIT], 2);
            w[TONGUE] = Math.max(w[TONGUE], ahead < 200 ? 4 : 0);
            if (big) { w[ERUPT] = Math.max(w[ERUPT], 4); w[SWARM] = Math.max(w[SWARM], 3); w[GOO_STORM] = Math.max(w[GOO_STORM], 3); }
            w[EYE_STORM] = Math.max(w[EYE_STORM], 5);
            if (back) w[SLAM] = Math.max(w[SLAM], 5);
        }
        // the stare is his big one: not more than about once every half minute, less as he comes apart
        int gate = ph == 2 ? 360 : ph == 1 ? 520 : 700;
        if (m.crippled()) gate = 180;                     // he is not saving it for later any more
        if (m.tickCount - lastUse[GAZE] < gate) w[GAZE] = 0;
        if (m.tickCount - lastUse[SWARM] < gate - 200) w[SWARM] = 0;
        if (m.tickCount - lastUse[SCREAM] < gate - 200) w[SCREAM] = 0;
        if (m.tickCount - lastUse[GOO_STORM] < (m.crippled() ? 200 : 420)) w[GOO_STORM] = 0;
        if (m.tickCount - lastUse[EYE_STORM] < (m.crippled() ? 220 : 480)) w[EYE_STORM] = 0;
        // variety: the last few he used are pushed right down, so he works through what he has instead of
        // leaning on the same two moves
        for (int i = 0; i < recent.length; i++) {
            int a = recent[i];
            if (a <= 0 || a > LAST_ATTACK) continue;
            int cut = i == 0 ? 8 : i == 1 ? 4 : 2;                 // the last one hardest, the one before that less
            w[a] = w[a] / cut;
        }
        if (last >= 1 && last <= LAST_ATTACK && w[last] > 1) w[last] /= 2;
        int sum = 0;
        for (int v : w) sum += v;
        if (sum == 0) return;
        int r = m.getRandom().nextInt(sum);
        for (int a = 1; a <= LAST_ATTACK; a++) { r -= w[a]; if (r < 0) { remember(a); start(a, target); return; } }
    }

    private final int[] lastUse = new int[LAST_ATTACK + 1];
    { java.util.Arrays.fill(lastUse, -100000); }

    /** the last three he used, newest first, so he doesn't fall back into a rut */
    private final int[] recent = {-1, -1, -1};
    private void remember(int a) { recent[2] = recent[1]; recent[1] = recent[0]; recent[0] = a; }

    private void start(int which, @Nullable LivingEntity target) {
        id = which; t = 0; arg = -1; hit.clear(); ring = 0;
        lastUse[which] = m.tickCount;
        // the two he does to himself want nobody in particular: he aims them at the ground in front of his face
        aim = near(target != null ? target.position() : m.position().add(m.getLookAngle().scale(40 * m.mountainScale() + 10)));
        float s = m.mountainScale();
        Vec3 head = m.toWorld(rig.headCenter, m.pose[rig.head]);
        switch (which) {
            case DRAW -> m.sound(head, net.jj.mountain.ModSounds.BREATH_IN, 4f, 0.4f);
            case SPLIT -> m.sound(head, net.jj.mountain.ModSounds.RAGE, 3.5f, 0.55f);
            case ARTILLERY -> { arg = 4 + m.getRandom().nextInt(3); m.sound(head, net.jj.mountain.ModSounds.BREATH_IN, 2.5f, 0.75f); }
            case WHIP -> {
                // the walking leg whose foot is nearest swings across through where you stand
                double bd = Double.MAX_VALUE;
                for (MountainRig.LegDef L : rig.legs) {
                    if (L.kind == 2) continue;
                    double d = m.toWorld(L.foot(), m.pose[L.bones[2]]).distanceToSqr(target.position());
                    if (d < bd) { bd = d; arg = L.k; }
                }
                sweepPrev = null;
                m.sound(target.position(), SoundEvents.WARDEN_TENDRIL_CLICKS, 2.5f, 0.4f);
            }
            case SLAM -> m.sound(target.position(), net.jj.mountain.ModSounds.ROAR, 2.0f, 1.1f);
            case GAZE -> {
                m.sound(head, SoundEvents.WARDEN_SONIC_CHARGE, 3f, 0.45f);
                for (Player p : m.level().players())
                    if (p.distanceToSqr(m) < sq(300 * s + 60) && !m.spares(p)) p.displayClientMessage(Component.translatable("message.mountain_breathes.gaze"), true);
            }
            case REAR -> m.sound(head, net.jj.mountain.ModSounds.ROAR, 3f, 0.7f);
            case VOMIT -> m.sound(m.mouthWorld(), net.jj.mountain.ModSounds.GOO, 3f, 0.5f);
            case ERUPT -> { m.sound(head, SoundEvents.WARDEN_DIG, 3f, 0.4f); burrow = new Vec3(m.mouthWorld().x, 0, m.mouthWorld().z); }
            case SWARM -> m.sound(head, net.jj.mountain.ModSounds.ROAR, 2.5f, 1.2f);
            case SCREAM -> m.sound(head, net.jj.mountain.ModSounds.BREATH_IN, 3f, 0.7f);
            case TONGUE -> { tip = m.mouthWorld(); back = false; m.sound(m.mouthWorld(), SoundEvents.WARDEN_TENDRIL_CLICKS, 3f, 0.3f); }
            case STOMP -> {
                double bd = Double.MAX_VALUE;
                for (MountainRig.LegDef L : rig.legs) {
                    if (L.kind == 2) continue;
                    double d = m.toWorld(L.foot(), m.pose[L.bones[2]]).distanceToSqr(target.position());
                    if (d < bd) { bd = d; arg = L.k; }
                }
                m.sound(head, net.jj.mountain.ModSounds.ROAR, 2.5f, 0.95f);
            }
            case GOO_STORM -> { m.sound(head, net.jj.mountain.ModSounds.BREATH_IN, 3f, 0.6f); m.sound(head, net.jj.mountain.ModSounds.ROAR, 3f, 0.8f); }
            case EYE_STORM -> { m.sound(head, net.jj.mountain.ModSounds.RAGE, 3.5f, 1.1f); m.sound(head, net.jj.mountain.ModSounds.WEAK, 3f, 0.9f); }
            case DARTS -> {}
            default -> {}
        }
    }

    private void finish() {
        last = id;
        float rush = m.phase() == 2 ? 0.45f : m.phase() == 1 ? 0.7f : 1f;
        if (m.stormy()) rush *= 0.72f;                    // a storm winds him up
        if (m.crippled()) rush *= 0.3f;                   // flat out and furious: barely a gap between them
        cooldown = (int) ((60 + m.getRandom().nextInt(80)) * (m.isHunter() ? 0.75f : 1f) * rush);
        stop();
    }

    // ------------------------------------------------------------------ running
    private void run(@Nullable LivingEntity target) {
        t++;
        if (target == null && forced != null && forced.isAlive() && !forced.isRemoved()) target = forced;
        boolean live = target != null && target.isAlive() && !target.isRemoved() && target.level() == m.level();
        float s = Math.max(0.02f, m.mountainScale());
        switch (id) {
            case ARTILLERY -> artillery(live ? target : null, s);
            case WHIP -> whip(live ? target : null, s);
            case SLAM -> slam(live ? target : null, s);
            case GAZE -> gaze(live ? target : null, s);
            case REAR -> rear(s);
            case VOMIT -> vomit(s);
            case DARTS -> darts(live ? target : null, s);
            case ERUPT -> erupt(live ? target : null, s);
            case SWARM -> swarm(live ? target : null, s);
            case SCREAM -> scream(live ? target : null, s);
            case TONGUE -> tongue(live ? target : null, s);
            case STOMP -> stomp(live ? target : null, s);
            case GOO_STORM -> gooStorm(live ? target : null, s);
            case EYE_STORM -> eyeStorm(live ? target : null, s);
            case DRAW -> drawIn(s);
            case SPLIT -> tearOff(s);
            case UNMAKE -> unmake(s);
            default -> {}
        }
        // the tongue doesn't give up part way with somebody still on the end of it
        boolean reeling = id == TONGUE && m.tongueHolding() && t < length(id) + 200;
        if ((t >= length(id) && !reeling) || done) finish();
    }

    /** He tips his head back and lobs a string of goo lumps high over, to come down on and around you. */
    private void artillery(@Nullable LivingEntity target, float s) {
        if (target != null && t < 30) aim = near(target.position());
        m.headPitch += (0.2f - m.headPitch) * 0.08f;
        int k = t - 26;
        boolean shot = k >= 0 && k % 9 == 0 && k / 9 < arg;
        m.mouth += ((shot ? 0.75f : 0.28f) - m.mouth) * 0.35f;
        if (!shot) return;
        int i = k / 9;
        Vec3 from = m.mouthWorld().add(m.mouthDir().scale(4 * s));
        Vec3 to = aim;
        if (target != null) to = near(target.position().add(target.getDeltaMovement().scale(20)));
        if (i > 0) {
            double a = m.getRandom().nextDouble() * Math.PI * 2, r = (3 + 7 * s) * (0.4 + m.getRandom().nextDouble());
            to = to.add(Math.cos(a) * r, 0, Math.sin(a) * r);
        }
        to = new Vec3(to.x, m.groundAt(Mth.floor(to.x), Mth.floor(to.z)), to.z);
        float size = 1.4f + 3.2f * s;
        GooGlob g = new GooGlob(m.level(), m, from, size, false, m.dmg(4 + 8 * s), 2f + 4f * s);
        g.setDeltaMovement(lob(from, to, 0.045));
        if (DEBUG) net.jj.mountain.MountainMod.LOG.info("lob from {} to {} v {}", from, to, g.getDeltaMovement());
        m.level().addFreshEntity(g);
        m.sound(from, SoundEvents.LLAMA_SPIT, 3f, 0.3f);
        m.sound(from, net.jj.mountain.ModSounds.GOO, 2f, 0.7f);
        m.sendParticles(ParticleTypes.SQUID_INK, from, 30, 2 * s, 2 * s, 0.2);
    }

    /**
     * Launch velocity that lands a high lob on 'to': a first guess from the flight time, then corrected a few
     * times by flying it the way the game does (move, then 1% air drag, then gravity).
     */
    static Vec3 lob(Vec3 from, Vec3 to, double g) {
        Vec3 d = to.subtract(from);
        double h = Math.hypot(d.x, d.z);
        int T = (int) Mth.clamp(22 + h * 0.5, 26, 70);
        double f = (1 - Math.pow(0.99, T)) / (0.01 * T);
        Vec3 v = new Vec3(d.x / (T * f), (d.y + 0.5 * g * T * T) / (T * f), d.z / (T * f));
        for (int it = 0; it < 6; it++) {
            Vec3 p = from, w = v;
            for (int k = 0; k < T; k++) { p = p.add(w); w = w.scale(0.99).add(0, -g, 0); }
            Vec3 err = to.subtract(p);
            if (err.lengthSqr() < 0.01) break;
            v = v.add(err.scale(1.0 / (T * f)));
        }
        // a throw is a throw: nothing he spits leaves at more than about a block a tick per block of him
        double sp = v.length();
        return sp > 16 ? v.scale(16 / sp) : v;
    }

    private Vec3 sweepPrev;

    /** One of his legs swings out to the side and sweeps across the ground through where you stand. */
    private void whip(@Nullable LivingEntity target, float s) {
        if (target != null && t < 12) aim = near(target.position());
        if (arg < 0) { done = true; return; }
        MountainRig.LegDef L = rig.legs[arg];
        Vec3 c = m.toWorld(L.foot(), m.pose[L.bones[2]]);
        if (t == 13) m.sound(aim, SoundEvents.PLAYER_ATTACK_SWEEP, 3f, 0.4f);
        if (t >= 13 && t <= 28) {
            Vec3 a = m.toWorld(L.joints[1], m.pose[L.bones[0]]), b = m.toWorld(L.joints[2], m.pose[L.bones[1]]);
            Vec3 along = sweepPrev == null ? Vec3.ZERO : c.subtract(sweepPrev).multiply(1, 0, 1);
            double r = 3.5 * s + 1.5;
            for (LivingEntity e : m.victims(c, a.distanceTo(c) + r)) {
                if (hit.contains(e.getId())) continue;
                Vec3 p = e.getBoundingBox().getCenter();
                if (Math.min(segDist(p, a, b), segDist(p, b, c)) > r + e.getBbWidth() * 0.5) continue;
                hit.add(e.getId());
                if (e.hurt(m.damageSources().mobAttack(m), m.dmg(6 + 9 * s, e))) {
                    Vec3 push = along.lengthSqr() > 1e-6 ? along.normalize() : p.subtract(a).multiply(1, 0, 1).normalize();
                    e.setDeltaMovement(e.getDeltaMovement().add(push.scale(1.4 + 0.9 * s)).add(0, 0.5 + 0.25 * s, 0));
                    e.hurtMarked = true;
                }
            }
            if (t % 3 == 0) {
                Vec3 g = new Vec3(c.x, m.groundAt(Mth.floor(c.x), Mth.floor(c.z)), c.z);
                m.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundBlock(g)), g, (int) (10 + 25 * s), 2 * s + 1, 0.5, 0.2);
                if (m.canGrief()) m.trample(g, 2 + 3 * s, (int) (2 + 3 * s));
            }
        }
        sweepPrev = c;
    }

    private static double segDist(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double l = ab.lengthSqr();
        double u = l < 1e-6 ? 0 : Mth.clamp(p.subtract(a).dot(ab) / l, 0, 1);
        return p.distanceTo(a.add(ab.scale(u)));
    }

    /** The hands on his back rise together and slam down on whoever is up there. */
    private void slam(@Nullable LivingEntity target, float s) {
        if (target != null && t < 18) aim = near(target.position());
        Vec3 mp = m.worldToModelPoint(aim);
        Vector3f p = new Vector3f((float) mp.x, (float) mp.y, (float) mp.z);
        if (t < 22) m.reach.lerp(new Vector3f(p).add(0, 34, 0), 0.3f);
        else if (t < 30) m.reach.lerp(new Vector3f(p).add(0, -3, 0), 0.6f);
        m.reachAmt = t < 34 ? Math.min(1f, m.reachAmt + 0.1f) : Math.max(0f, m.reachAmt - 0.08f);
        if (t == 28) {
            double r = 4 * s + 2.5;
            m.sendParticles(ParticleTypes.EXPLOSION, aim.add(0, 1, 0), 3, r * 0.4, 0.5, 0);
            m.sendParticles(ParticleTypes.SQUID_INK, aim.add(0, 1, 0), 50, r * 0.5, 1, 0.3);
            m.sound(aim, SoundEvents.ANVIL_LAND, 2.5f, 0.45f);
            m.sound(aim, SoundEvents.RAVAGER_ATTACK, 3f, 0.4f);
            for (LivingEntity e : m.victims(aim.add(0, 1, 0), r)) {
                if (e.hurt(m.damageSources().mobAttack(m), m.dmg(7 + 10 * s, e))) {
                    // off his back: thrown out sideways
                    Vec3 em = m.worldToModelPoint(e.position());
                    Vec3 side = m.toWorld(new Vector3f((float) Math.signum(em.x == 0 ? 1 : em.x), 0, 0), new org.joml.Matrix4f()).subtract(m.position()).multiply(1, 0, 1);
                    Vec3 push = side.lengthSqr() > 1e-6 ? side.normalize() : new Vec3(1, 0, 0);
                    e.setDeltaMovement(e.getDeltaMovement().add(push.scale(1.4 + 0.6 * s)).add(0, 0.9, 0));
                    e.hurtMarked = true;
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), m);
                }
            }
        }
    }

    /** Every eye turns on you and burns. Anything solid between you and his head blocks it. */
    /**
     * Half of him gone, he throws his head up and hoses the whole place down with goo: a long, fast stream of
     * lumps fanned out across the ground around you, instead of the few careful lobs he starts the fight with.
     */
    private void gooStorm(@Nullable LivingEntity target, float s) {
        if (target != null && t < 100) aim = near(target.position());
        m.headPitch += (0.24f - m.headPitch) * 0.12f;
        boolean firing = t >= 22 && t < 118;
        m.mouth += ((firing ? 0.9f : 0.3f) - m.mouth) * 0.3f;
        if (!firing || t % 3 != 0) return;
        int i = (t - 22) / 3;
        Vec3 from = m.mouthWorld().add(m.mouthDir().scale(4 * s));
        // a fan that sweeps out and back across the ground round you
        double spread = (18 + 55 * s) * (0.35 + 0.65 * Math.abs(Mth.sin(i * 0.55f)));
        double a = i * 2.39996;                                  // never lands twice in the same place
        Vec3 to = aim.add(Math.cos(a) * spread, 0, Math.sin(a) * spread);
        if (target != null && i % 5 == 0) to = target.position().add(target.getDeltaMovement().scale(16));
        to = new Vec3(to.x, m.groundAt(Mth.floor(to.x), Mth.floor(to.z)), to.z);
        GooGlob g = new GooGlob(m.level(), m, from, 1.2f + 2.4f * s, false, m.dmg(3 + 5 * s), 1.6f + 3f * s);
        g.setDeltaMovement(lob(from, to, 0.045));
        m.level().addFreshEntity(g);
        if (i % 2 == 0) m.sound(from, net.jj.mountain.ModSounds.GOO, 2f, 0.8f + m.getRandom().nextFloat() * 0.3f);
        m.sendParticles(ParticleTypes.SQUID_INK, from, 14, 2 * s, 2 * s, 0.25);
    }

    /**
     * A quarter left and he stops picking one thing to stare at. Every eye still open finds something of its own
     * and they all burn at once, over and over, for as long as he can keep it up.
     */
    private void eyeStorm(@Nullable LivingEntity target, float s) {
        if (target != null) {
            Vec3 mp = m.worldToModelPoint(target.getEyePosition());
            m.look.lerp(new Vector3f((float) mp.x, (float) mp.y, (float) mp.z), 0.2f);
            m.lookAmt = Math.min(1f, m.lookAmt + 0.06f);
        }
        if (t == 26) {
            Vec3 head = m.toWorld(rig.headCenter, m.pose[rig.head]);
            m.sound(head, net.jj.mountain.ModSounds.SCREAM, 4f, 1.15f);
            for (Player p : m.level().players())
                if (p.distanceToSqr(m) < sq(300 * s + 60) && !m.spares(p)) p.displayClientMessage(Component.translatable("message.mountain_breathes.gaze"), true);
        }
        if (t < 26) return;
        // every one of them gets an eye of their own on them: the storm keeps the list so his eyes can hold a
        // line on each while it burns, instead of one look for the whole crowd
        if (t % 6 == 0) {
            double r = 60 + 150 * s;
            stormOn.clear();
            for (LivingEntity e : m.level().getEntitiesOfClass(LivingEntity.class, m.getBoundingBoxForCulling().inflate(r))) {
                if (!e.isAlive() || e == m || e instanceof MountainEntity || e instanceof HeartEntity || m.spares(e)) continue;
                if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
                if (!seenBy(e)) continue;
                stormOn.add(e);
                if (stormOn.size() >= net.jj.mountain.net.StormPayload.MOST) break;
            }
            tellTheStorm();
        }
        stormOn.removeIf(e -> !e.isAlive());
        m.watchAll(stormOn);                                       // his eyes split between all of them at once
        if (t < 30 || t % 6 != 0) return;
        for (LivingEntity e : stormOn) {
            e.hurt(m.damageSources().indirectMagic(m, m), m.dmg(1.2f + 2.6f * s, e));
            e.igniteForSeconds(3);
            if (e instanceof Player p) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0), m);
            m.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, e.getEyePosition(), 12, 0.6, 0.6, 0.05);
        }
        if (!stormOn.isEmpty()) m.sound(m.toWorld(rig.headCenter, m.pose[rig.head]), net.jj.mountain.ModSounds.WEAK, 2f, 1.5f);
    }

    /**
     * Hands the whole list over to everybody watching. The game only syncs three look points about him, which
     * was why the storm could never have more than four beams in it however many things were standing there.
     */
    private void tellTheStorm() {
        if (!(m.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        int[] ids = new int[stormOn.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = stormOn.get(i).getId();
        var pay = new net.jj.mountain.net.StormPayload(m.getId(), ids);
        double see = Math.max(256, net.jj.mountain.MountainConfig.V.renderDistance);
        for (net.minecraft.server.level.ServerPlayer p : sl.players())
            if (p.distanceToSqr(m) < see * see) net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, pay);
    }

    /** everything the storm is burning right now */
    private final java.util.List<LivingEntity> stormOn = new java.util.ArrayList<>();
    public int stormTargets() { return stormOn.size(); }

    /** everything he is burning this time round: his eyes swing from one to the next */
    private final java.util.List<LivingEntity> gazeOn = new java.util.ArrayList<>();
    /** how many things his gaze is working through right now */
    public int gazeTargets() { return gazeOn.size(); }

    /**
     * The stare. He does not burn you any more — he looks at you, and black goo comes up out of the ground behind
     * you in your own shape, with your health and whatever is in your hand, and comes at you.
     *
     * It stands for exactly as long as he can see you. Put a hill, a wall or his own leg between his eyes and your
     * back and it falls apart where it is. And holding the stare costs him nothing: he goes straight on with
     * everything else while your own shadow works on you.
     */
    private void gaze(@Nullable LivingEntity target, float s) {
        if (t == 40) {
            gazeOn.clear();
            Vec3 c = target != null ? near(target.position()) : aim;
            for (Player p : m.level().players()) {
                if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
                if (m.spares(p) || net.jj.mountain.innards.Innards.isInside(p)) continue;
                if (p.distanceToSqr(c) > (60 + 120 * s) * (60 + 120 * s)) continue;
                if (!seenBy(p)) continue;
                gazeOn.add(p);
                if (gazeOn.size() >= 6) break;
            }
            if (target instanceof Player tp && tp.isAlive() && !gazeOn.contains(tp)) gazeOn.add(0, tp);
        }
        gazeOn.removeIf(e -> !e.isAlive());
        LivingEntity on = gazeOn.isEmpty() ? target : gazeOn.get(0);
        if (on != null) aim = near(on.getEyePosition());
        Vec3 mp = m.worldToModelPoint(aim);
        m.look.lerp(new Vector3f((float) mp.x, (float) mp.y, (float) mp.z), t < 40 ? 0.25f : 0.14f);
        m.lookAmt = Math.min(1f, m.lookAmt + 0.06f);
        if (t == 42) {
            Vec3 head = m.toWorld(rig.headCenter, m.pose[rig.head]);
            m.sound(head, net.jj.mountain.ModSounds.WEAK, 3f, 1.4f);
            m.sound(head, SoundEvents.WARDEN_SONIC_BOOM, 2.5f, 0.5f);
        }
        if (t != 50 || !(m.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        for (LivingEntity e : gazeOn) {
            if (!(e instanceof Player p) || !seenBy(p)) continue;
            if (standsAlready(p)) continue;                     // one of you at a time is quite enough
            var sh = net.jj.mountain.ModEntities.SHADOW.create(sl);
            if (sh == null) continue;
            Vec3 at = net.jj.mountain.entity.ShadowOfYou.spotFor(p);
            sh.moveTo(at.x, at.y, at.z, p.getYRot() + 180f, 0f);
            sh.becomeShadowOf(p, m);
            sl.addFreshEntity(sh);
            sh.rise(sl);
            if (p instanceof net.minecraft.server.level.ServerPlayer sp)
                sp.displayClientMessage(Component.translatable("message.mountain_breathes.shadow_up"), false);
        }
    }

    // ------------------------------------------------------------------ the long breath in
    /**
     * He breathes in and does not stop. For about fifteen seconds everything loose within a few hundred blocks is
     * dragged toward his mouth — you, whatever else is alive, dropped items, the lot. Solid world between you and
     * his head is the only thing that cuts it: get a hill or one of his own legs in the way and the pull dies.
     * Reach the mouth and you go down his throat.
     */
    private void drawIn(float s) {
        Vec3 mouth = m.toWorld(rig.mouth, m.pose[rig.head]);
        // he winds up, then holds it open for the length of the move
        float open = t < 30 ? t / 30f * 0.5f : 0.95f;
        m.mouth += (open - m.mouth) * 0.25f;
        m.headPitch += (-0.12f - m.headPitch) * 0.05f;
        if (t == 12) m.sound(mouth, net.jj.mountain.ModSounds.WEAK, 4f, 0.5f);
        if (t == 34) {
            m.sound(mouth, net.jj.mountain.ModSounds.ROAR, 5f, 0.35f);
            for (Player p : m.level().players())
                if (p.distanceToSqr(mouth) < sq(360 * s + 140))
                    p.displayClientMessage(Component.translatable("message.mountain_breathes.draw_in"), false);
        }
        if (t < 34) return;

        double reach = 180 * s + 90;
        if (m.level() instanceof net.minecraft.server.level.ServerLevel sl && t % 2 == 0) {
            Vec3 back = mouth.subtract(m.position()).normalize().scale(6 * s + 2);
            sl.sendParticles(ParticleTypes.SQUID_INK, mouth.x - back.x, mouth.y - back.y, mouth.z - back.z,
                    30, 5 * s + 2, 5 * s + 2, 5 * s + 2, -0.35);
        }
        if (t % 3 != 0) return;

        for (net.minecraft.world.entity.Entity e : m.level().getEntities(m, new net.minecraft.world.phys.AABB(mouth, mouth).inflate(reach))) {
            if (e instanceof MountainEntity || e instanceof net.jj.mountain.entity.MountainPart) continue;
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            if (e instanceof LivingEntity le && (m.spares(le) || net.jj.mountain.innards.Innards.isInside(le))) continue;
            if (e == m.rider() || e == m.held()) continue;
            Vec3 d = mouth.subtract(e.position());
            double len = d.length();
            if (len < 1.0E-3 || len > reach) continue;
            if (!clearLine(mouth, e.getEyePosition())) continue;      // solid world in the way and the pull dies

            // strongest near his mouth, so the last few blocks are the ones you cannot fight
            double near = 1.0 - (len / reach);
            double pull = (0.10 + 0.55 * near * near) * (1.0 + 0.6 * s);
            Vec3 add = d.scale(pull / len);
            e.setDeltaMovement(e.getDeltaMovement().scale(0.86).add(add.x, add.y * 0.75 + 0.03, add.z));
            e.hurtMarked = true;
            if (e instanceof net.minecraft.server.level.ServerPlayer sp) sp.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));

            // right at the mouth: down he goes
            if (len < 14 * s + 4) m.swallow(e);
        }
    }

    // ------------------------------------------------------------------ the piece he tears off himself
    /**
     * He puts a hand into his own side and pulls a lump of himself out, and it stands up. It is him at a fifth
     * the size and it fights beside him for a minute before it comes apart into goo.
     *
     * Costs him a piece of himself, most of his wind, and it sours him badly. He will not do it off his own bat
     * while somebody is carrying his book — that is the holder's line to call.
     */
    private void tearOff(float s) {
        if (t == 20) {
            Vec3 side = m.toWorld(new Vector3f(rig.segCenter[2]).add(60, 0, 0), m.pose[rig.segments[2]]);
            m.sound(side, net.jj.mountain.ModSounds.HURT, 4f, 0.45f);
            m.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PINK_TERRACOTTA.defaultBlockState()),
                    side, 220, 14 * s + 4, 14 * s + 4, 0.45);
        }
        if (t != 46 || !(m.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;

        MountainEntity lump = net.jj.mountain.ModEntities.MOUNTAIN.create(sl);
        if (lump == null) { done = true; return; }
        float small = Mth.clamp(s * 0.22f, 0.03f, 0.35f);
        Vec3 out = m.position().add((m.getRandom().nextDouble() - 0.5) * 120 * s, 0, (m.getRandom().nextDouble() - 0.5) * 120 * s);
        lump.setMountainScale(small);
        lump.setVariant(MountainEntity.HUNTER);
        lump.becomeAPieceOf(m);                                   // short-lived, and no part of the world's count
        lump.moveTo(out.x, m.groundAt(Mth.floor(out.x), Mth.floor(out.z)), out.z, m.getRandom().nextFloat() * 360f, 0f);
        lump.setYBodyRot(lump.getYRot()); lump.setYHeadRot(lump.getYRot());
        sl.addFreshEntity(lump);
        if (m.getTarget() != null) lump.setTarget(m.getTarget());

        m.hurtSelf(m.healthMax() * 0.06f);                        // it comes out of him, not out of nowhere
        m.sound(out, net.jj.mountain.ModSounds.RAGE, 5f, 0.8f);
        m.sendParticles(ParticleTypes.SQUID_INK, out.add(0, 8 * small + 2, 0), 250, 12 * s + 4, 8 * s + 2, 0.4);
        for (Player p : m.level().players())
            if (p.distanceToSqr(out) < sq(400 * s + 140))
                p.displayClientMessage(Component.translatable("message.mountain_breathes.tear_off"), false);
    }

    /** is one of theirs already up? */
    private boolean standsAlready(Player p) {
        for (var sh : m.level().getEntitiesOfClass(net.jj.mountain.entity.ShadowOfYou.class,
                p.getBoundingBox().inflate(220, 90, 220)))
            if (!sh.isRemoved() && p.getUUID().equals(sh.ownerId())) return true;
        return false;
    }

    private boolean seenBy(LivingEntity e) {
        Vec3 to = e.getEyePosition();
        for (int k = 0; k < rig.eyes.length; k += 23) {
            if (m.isEyePopped(k)) continue;
            if (clearLine(m.eyeWorld(k), to)) return true;
        }
        return clearLine(m.toWorld(rig.headCenter, m.pose[rig.head]), to);
    }

    /** can he see from here to there? His own goo doesn't count: it lies all around him and would blind him. */
    private boolean clearLine(Vec3 from, Vec3 to) {
        Vec3 f = from;
        for (int i = 0; i < 6; i++) {
            HitResult h = m.level().clip(new ClipContext(f, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, m));
            if (h.getType() == HitResult.Type.MISS) return true;
            if (!(h instanceof net.minecraft.world.phys.BlockHitResult bh) || !m.level().getBlockState(bh.getBlockPos()).is(net.jj.mountain.ModBlocks.GOO)) return false;
            Vec3 d = to.subtract(f);
            double len = d.length();
            if (len < 1.5) return true;
            f = h.getLocation().add(d.scale(1.05 / len));
        }
        return false;
    }

    /** He rears his whole front end up into the sky, then drops it: a shock ring runs out across the ground. */
    private void rear(float s) {
        if (t == 45) m.sound(m.toWorld(rig.headCenter, m.pose[rig.head]), net.jj.mountain.ModSounds.ROAR, 3f, 0.7f);
        if (t == 63) {
            Vec3 h = m.toWorld(rig.mouth, m.pose[rig.head]);
            impact = new Vec3(h.x, m.groundAt(Mth.floor(h.x), Mth.floor(h.z)), h.z);
            ring = 4 * s + 2;
            m.sendParticles(ParticleTypes.EXPLOSION_EMITTER, impact.add(0, 1, 0), 2, 6 * s, 1, 0);
            m.sound(impact, SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.45f);
            m.sound(impact, SoundEvents.ANVIL_LAND, 3f, 0.3f);
            if (m.canGrief()) m.trample(impact, 6 + 10 * s, (int) (4 + 8 * s));
        }
        if (t < 63 || t > 92) return;
        double step = 3.2 * s + 1.2, band = 4 * s + 2;
        ring += step;
        for (LivingEntity e : m.victims(impact, ring + 2)) {
            if (hit.contains(e.getId())) continue;
            double d = Math.hypot(e.getX() - impact.x, e.getZ() - impact.z);
            if (d < ring - band - step || d > ring + 1) continue;
            if (e.getY() - m.groundAt(Mth.floor(e.getX()), Mth.floor(e.getZ())) > 3 + 2 * s) continue;       // jump it!
            hit.add(e.getId());
            if (e.hurt(m.damageSources().mobAttack(m), m.dmg(6 + 10 * s, e))) {
                Vec3 out = new Vec3(e.getX() - impact.x, 0, e.getZ() - impact.z);
                if (out.lengthSqr() < 1e-4) out = new Vec3(1, 0, 0);
                e.setDeltaMovement(e.getDeltaMovement().add(out.normalize().scale(1.0 + 0.8 * s)).add(0, 0.7 + 0.3 * s, 0));
                e.hurtMarked = true;
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2), m);
            }
        }
        if (t % 2 == 0) {
            int n = (int) Math.min(48, 10 + ring * 0.6);
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n + t;
                double x = impact.x + Math.cos(a) * ring, z = impact.z + Math.sin(a) * ring;
                int gy = m.groundAt(Mth.floor(x), Mth.floor(z));
                Vec3 p = new Vec3(x, gy + 0.2, z);
                m.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundBlock(p)), p, 6, 0.8, 0.3, 0.2);
                if (i % 4 == 0) m.sendParticles(ParticleTypes.POOF, p, 2, 0.6, 0.4, 0.05);
            }
        }
    }

    /** His face splits wide and a flood of goo pours out over the ground in front of him. */
    private void vomit(float s) {
        m.mouth += ((t < 78 ? 1.0f : 0.05f) - m.mouth) * 0.2f;
        m.headPitch += ((t < 78 ? -0.22f : 0f) - m.headPitch) * 0.06f;
        if (t == 15) { m.sound(m.mouthWorld(), net.jj.mountain.ModSounds.ROAR, 3f, 0.6f); m.sound(m.mouthWorld(), net.jj.mountain.ModSounds.GOO, 3f, 0.6f); }
        if (t < 15 || t > 76) return;
        float prog = (t - 15) / 60f;
        Vec3 mouth = m.mouthWorld(), dir = m.mouthDir();
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        double reachL = (14 + 58 * Math.min(1f, prog * 1.6f)) * s + 4;
        if (t % 10 == 0) m.sound(mouth, SoundEvents.GENERIC_SPLASH, 3f, 0.3f);
        // falling goo between his mouth and the ground
        for (int i = 0; i < 4; i++) {
            double d = (6 + 20 * m.getRandom().nextDouble()) * s;
            Vec3 p = mouth.add(flat.scale(d)).add(0, -m.getRandom().nextDouble() * 30 * s, 0);
            m.sendParticles(ParticleTypes.SQUID_INK, p, 6, 2 * s, 2 * s, 0.1);
        }
        if (t % 3 == 0 && MountainConfig.V.gooTrail) {
            for (int i = 0; i < 2 + (int) (3 * s); i++) {
                double d = reachL * Math.sqrt(m.getRandom().nextDouble());
                Vec3 side = new Vec3(-flat.z, 0, flat.x).scale((m.getRandom().nextDouble() - 0.5) * (0.5 * d + 4 * s));
                Vec3 p = mouth.add(flat.scale(d)).add(side);
                m.gooPatch(p.x, p.z, 1.2 + 2.2 * s * m.getRandom().nextDouble(), false);
            }
        }
        double cone = Math.cos(Math.toRadians(26));
        if (DEBUG && t % 10 == 0) net.jj.mountain.MountainMod.LOG.info("vomit t={} reach {} mouth {} flat {} victims {}", t, reachL, mouth, flat,
                m.victims(mouth.add(flat.scale(reachL * 0.5)).add(0, -20 * s, 0), reachL * 0.6 + 30 * s).stream().map(x -> x.position().toString()).toList());
        for (LivingEntity e : m.victims(mouth.add(flat.scale(reachL * 0.5)).add(0, -20 * s, 0), reachL * 0.6 + 30 * s)) {
            Vec3 v = e.position().subtract(mouth).multiply(1, 0, 1);
            double d = v.length();
            if (d > reachL + 3 || d < 1 || v.normalize().dot(flat) < cone) continue;
            e.setDeltaMovement(e.getDeltaMovement().add(flat.scale(0.12 + 0.05 * s)));
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3), m);
            e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0), m);
            if ((t + e.getId()) % 10 == 0) e.hurt(m.damageSources().indirectMagic(m, m), m.dmg(2 + 3 * s, e));
        }
    }

    /** The holes on his skin nearest you spit fast poison darts. */
    private void darts(@Nullable LivingEntity target, float s) {
        if (target == null || t < 8 || t % 8 != 0) return;
        Vec3 tgt = target.getBoundingBox().getCenter();
        // the three bits of his skin nearest you
        MountainRig.BodySample[] best = new MountainRig.BodySample[3];
        double[] bd = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        for (MountainRig.BodySample b : rig.body) {
            double d = m.toWorld(b.p(), m.pose[b.bone()]).distanceToSqr(tgt);
            for (int i = 0; i < 3; i++) if (d < bd[i]) {
                for (int j = 2; j > i; j--) { bd[j] = bd[j - 1]; best[j] = best[j - 1]; }
                bd[i] = d; best[i] = b; break;
            }
        }
        for (MountainRig.BodySample b : best) {
            if (b == null) continue;
            Vec3 c = m.toWorld(b.p(), m.pose[b.bone()]);
            Vec3 dir = tgt.subtract(c);
            if (dir.lengthSqr() < 1e-4) continue;
            dir = dir.normalize();
            Vec3 from = c.add(dir.scale(b.r() * s * 0.95 + 1));
            double speed = 1.5 + 0.8 * s;
            Vec3 lead = tgt.add(target.getDeltaMovement().scale(from.distanceTo(tgt) / speed));
            Vec3 v = lead.subtract(from).normalize().scale(speed)
                    .add((m.getRandom().nextDouble() - 0.5) * 0.08, (m.getRandom().nextDouble() - 0.5) * 0.08, (m.getRandom().nextDouble() - 0.5) * 0.08);
            GooGlob g = new GooGlob(m.level(), m, from, 0.45f + 0.5f * s, true, m.dmg(2 + 3 * s), 1f + 0.5f * s);
            g.setDeltaMovement(v);
            m.level().addFreshEntity(g);
            m.sendParticles(ParticleTypes.SQUID_INK, from, 10, 0.5 + s, 0.5 + s, 0.1);
            m.sound(from, SoundEvents.SHULKER_SHOOT, 1.6f, 0.5f);
        }
    }

    // ------------------------------------------------------------------ the scream, the tongue, the stomp
    /**
     * His face splits wide and he screams. A wall of sound rolls out in front of him: it throws you back, makes the
     * world swim and goes dark for a moment. It spreads out as it goes, so it's easier to dodge up close to his side.
     */
    private void scream(@Nullable LivingEntity target, float s) {
        if (target != null && t < 20) aim = near(target.position());
        m.mouth += ((t < 58 ? 1.0f : 0.05f) - m.mouth) * 0.25f;
        m.headPitch += ((t < 58 ? 0.14f : 0f) - m.headPitch) * 0.1f;
        m.grind = t > 14 && t < 58 ? 0.6f * Mth.sin(t * 1.7f) : m.grind * 0.7f;
        Vec3 mouth = m.mouthWorld();
        if (t == 22) {
            m.sound(mouth, net.jj.mountain.ModSounds.SCREAM, 4f, 1.0f);
            m.sound(mouth, net.jj.mountain.ModSounds.ROAR, 4f, 0.6f);
            m.sound(mouth, SoundEvents.WARDEN_ROAR, 4f, 0.25f);
        }
        if (t < 22 || t > 54) return;
        Vec3 dir = aim.add(0, 1, 0).subtract(mouth);
        dir = dir.lengthSqr() < 1e-4 ? m.mouthDir() : dir.normalize();
        double range = 85 * s + 26;
        double front = (t - 21) / 32.0 * range, cone = Math.toRadians(38);
        if (t % 2 == 0) {
            Vec3 side = dir.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
            Vec3 up = side.cross(dir).normalize();
            int n = (int) Math.min(24, 4 + front * 0.25);
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n, rr = front * Math.tan(cone) * (0.35 + 0.65 * m.getRandom().nextDouble());
                Vec3 p = mouth.add(dir.scale(front)).add(side.scale(Math.cos(a) * rr)).add(up.scale(Math.sin(a) * rr * 0.6));
                m.sendParticles(ParticleTypes.SONIC_BOOM, p, 1, 0, 0, 0);
            }
        }
        for (LivingEntity e : m.victims(mouth.add(dir.scale(Math.min(front, range) * 0.5)), Math.min(front, range) * 0.5 + front * Math.tan(cone) + 4)) {
            if (hit.contains(e.getId())) continue;
            Vec3 v = e.getBoundingBox().getCenter().subtract(mouth);
            double d = v.length();
            if (d > front + 2 || d < 1 || v.normalize().dot(dir) < Math.cos(cone)) continue;
            hit.add(e.getId());
            if (e.hurt(m.damageSources().mobAttack(m), m.dmg(4 + 7 * s, e))) {
                Vec3 push = v.multiply(1, 0, 1);
                push = push.lengthSqr() < 1e-4 ? new Vec3(dir.x, 0, dir.z) : push.normalize();
                e.setDeltaMovement(e.getDeltaMovement().add(push.scale(1.6 + 1.2 * s)).add(0, 0.55 + 0.25 * s, 0));
                e.hurtMarked = true;
                e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0), m);
                e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 70, 0), m);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), m);
            }
        }
    }

    /** where the tip of his tongue is (the attack's aim follows it, so the client can draw it) */
    private Vec3 tip = Vec3.ZERO;
    private boolean back;

    /**
     * A long tongue shoots out of his mouth at you. If it touches you it wraps round you and reels you back into
     * his mouth; if it misses (or hits a wall) it slaps down and slides back in.
     */
    private void tongue(@Nullable LivingEntity target, float s) {
        Vec3 mouth = m.mouthWorld();
        m.mouth += ((back && t > 30 && tip.distanceTo(mouth) < 6 * s + 3 ? 0.2f : 0.7f) - m.mouth) * 0.3f;
        if (t < 12) { tip = mouth; aim = tip; return; }
        double maxLen = 95 * s + 20, speed = 2.8 * s + 1.4;
        if (!back) {
            Vec3 want = target != null ? target.getBoundingBox().getCenter() : tip.add(m.mouthDir().scale(speed));
            Vec3 d = want.subtract(tip);
            Vec3 next = tip.add(d.lengthSqr() < 1e-4 ? Vec3.ZERO : d.normalize().scale(Math.min(speed, d.length() + 0.5)));
            HitResult h = m.level().clip(new ClipContext(tip, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, m));
            if (h.getType() != HitResult.Type.MISS) { next = h.getLocation(); back = true; m.sound(next, SoundEvents.SLIME_ATTACK, 2f, 0.4f); }
            tip = next;
            if (tip.distanceTo(mouth) > maxLen || t > 70) back = true;
            for (LivingEntity e : m.victims(tip, 1.8 + 2.2 * s)) {
                if (!m.canBeTongued(e)) continue;
                m.tongueGrab(e);
                e.hurt(m.damageSources().mobAttack(m), m.dmg(2 + 3 * s, e));
                m.sound(tip, SoundEvents.SLIME_SQUISH, 2.5f, 0.35f);
                back = true;
                break;
            }
        } else {
            Vec3 d = mouth.subtract(tip);
            // with somebody on the end of it he reels harder, not softer: what he has caught is going in
            double pull = m.tongueHolding() ? speed * 1.3 : speed * 0.9;
            tip = d.length() <= pull ? mouth : tip.add(d.normalize().scale(pull));
            m.moveTongueCatch(tip);
            if (tip.distanceTo(mouth) < 4 * s + 3) {
                m.swallowFromTongue();
                done = true;
            }
        }
        aim = tip;
    }

    /** One of his walking legs lifts high over you and stamps down. */
    private void stomp(@Nullable LivingEntity target, float s) {
        if (target != null && t < 18) aim = near(target.position());
        if (arg < 0) { done = true; return; }
        MountainRig.LegDef L = rig.legs[arg];
        int hitT = (int) (0.6f * length(STOMP));
        if (DEBUG && t % 4 == 0) net.jj.mountain.MountainMod.LOG.info("stomp t={} leg {} foot {} aim {}", t, arg, m.toWorld(L.foot(), m.pose[L.bones[2]]), aim);
        if (t == hitT - 6) m.sound(aim, SoundEvents.PLAYER_ATTACK_SWEEP, 3f, 0.3f);
        if (t != hitT) return;
        Vec3 f = m.toWorld(L.foot(), m.pose[L.bones[2]]);
        Vec3 g = new Vec3(f.x, m.groundAt(Mth.floor(f.x), Mth.floor(f.z)), f.z);
        double r = 5 * s + 2.5;
        m.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundBlock(g)), g, (int) (40 + 80 * s), r * 0.6, 0.8, 0.3);
        m.sendParticles(ParticleTypes.EXPLOSION, g.add(0, 1, 0), 3, r * 0.4, 0.5, 0);
        m.sound(g, SoundEvents.GENERIC_EXPLODE.value(), 2.5f, 0.5f);
        m.sound(g, SoundEvents.ANVIL_LAND, 2.5f, 0.35f);
        if (m.canGrief()) m.trample(g, 3 + 6 * s, (int) (3 + 6 * s));
        for (LivingEntity e : m.victims(g.add(0, 1, 0), r + 1.5)) {
            if (e.hurt(m.damageSources().mobAttack(m), m.dmg(8 + 12 * s, e))) {
                Vec3 out = new Vec3(e.getX() - g.x, 0, e.getZ() - g.z);
                out = out.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : out.normalize();
                e.setDeltaMovement(e.getDeltaMovement().add(out.scale(0.9 + 0.6 * s)).add(0, 0.8 + 0.3 * s, 0));
                e.hurtMarked = true;
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2), m);
            }
        }
    }

    // ------------------------------------------------------------------ far away: tentacles from below, eyes torn loose
    private Vec3 burrow = Vec3.ZERO;

    /**
     * He stamps and something burrows out from under his mouth, a furrow of bursting earth racing across the ground
     * to wherever you are. When it reaches you, a ring of tentacles erupts up around you.
     */
    private void erupt(@Nullable LivingEntity target, float s) {
        if (target != null && t < 60) aim = near(target.position());
        if (t < 16) return;
        if (t == 16) { Vec3 mw = m.mouthWorld(); burrow = new Vec3(mw.x, 0, mw.z); }
        Vec3 to = new Vec3(aim.x, 0, aim.z);
        double left = to.distanceTo(burrow);
        if (left > 3 && t < 80) {
            double speed = Math.max(1.2, left / Math.max(1, 70 - t));
            burrow = burrow.add(to.subtract(burrow).normalize().scale(Math.min(left, speed)));
            int gy = m.groundAt(Mth.floor(burrow.x), Mth.floor(burrow.z));
            Vec3 p = new Vec3(burrow.x, gy + 0.2, burrow.z);
            m.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundBlock(p)), p, 14, 0.9, 0.3, 0.25);
            if (t % 3 == 0) m.sendParticles(ParticleTypes.SQUID_INK, p, 4, 0.6, 0.2, 0.05);
            if (t % 5 == 0) m.sound(p, SoundEvents.ROOTED_DIRT_BREAK, 1.6f, 0.5f);
            return;
        }
        if (arg >= 0) return;                         // already came up
        arg = 1;
        int n = 4 + m.getRandom().nextInt(2);
        double a0 = m.getRandom().nextDouble() * Math.PI * 2, r = 3.5 + m.getRandom().nextDouble();
        int alive = m.level().getEntitiesOfClass(net.jj.mountain.entity.inside.GutTentacle.class, m.getBoundingBox().inflate(400)).size();
        for (int i = 0; i < n && alive < 10; i++, alive++) {
            double a = a0 + i * Math.PI * 2 / n;
            double x = aim.x + Math.cos(a) * r, z = aim.z + Math.sin(a) * r;
            int gy = m.groundAt(Mth.floor(x), Mth.floor(z));
            var tent = net.jj.mountain.ModEntities.GUT_TENTACLE.create(m.level());
            if (tent == null) continue;
            tent.moveTo(Mth.floor(x) + 0.5, gy, Mth.floor(z) + 0.5, m.getRandom().nextFloat() * 360f, 0f);
            tent.summonedBy(m, 200 + m.getRandom().nextInt(60));
            m.level().addFreshEntity(tent);
        }
        Vec3 p = new Vec3(aim.x, m.groundAt(Mth.floor(aim.x), Mth.floor(aim.z)) + 0.2, aim.z);
        m.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundBlock(p)), p, 60, 4, 0.5, 0.3);
        m.sound(p, SoundEvents.WARDEN_EMERGE, 3f, 0.7f);
    }

    /** He tears a few of his eyes loose and flings them at you; they hang in the air around you and spit goo. */
    private void swarm(@Nullable LivingEntity target, float s) {
        if (target != null) aim = near(target.getEyePosition());
        if (t < 12 || t > 48 || (t - 12) % 9 != 0) return;
        int alive = m.level().getEntitiesOfClass(net.jj.mountain.entity.inside.WatcherEye.class, m.getBoundingBox().inflate(400)).size();
        if (alive >= 8) return;
        // an open eye on the side facing you
        int best = -1; double bd = Double.MAX_VALUE;
        for (int k = 0; k < rig.eyes.length; k++) {
            if (m.isEyePopped(k)) continue;
            double d = m.eyeWorld(k).distanceToSqr(aim) + m.getRandom().nextDouble() * 400;
            if (d < bd) { bd = d; best = k; }
        }
        if (best < 0) return;
        Vec3 from = m.eyeWorld(best);
        var eye = net.jj.mountain.ModEntities.WATCHER.create(m.level());
        if (eye == null) return;
        eye.moveTo(from.x, from.y - 1, from.z, 0f, 0f);
        eye.summonedBy(m, 500 + m.getRandom().nextInt(200), target, aim);
        Vec3 v = aim.subtract(from);
        eye.setDeltaMovement(v.normalize().scale(Math.min(1.4, 0.4 + v.length() * 0.02)).add(0, 0.3, 0));
        m.level().addFreshEntity(eye);
        m.sendParticles(ParticleTypes.SQUID_INK, from, 30, 1.5, 1.5, 0.3);
        m.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.6f, 0.05f, 0.05f), 2f), from, 20, 1.2, 1.2, 0);
        m.sound(from, SoundEvents.SLIME_BLOCK_BREAK, 2.5f, 0.5f);
        m.sound(from, SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 2f, 0.4f);
    }

    private BlockState groundBlock(Vec3 p) {
        BlockState st = m.level().getBlockState(BlockPos.containing(p.x, p.y - 1, p.z));
        return st.isAir() ? Blocks.DIRT.defaultBlockState() : st;
    }

    private static double sq(double v) { return v * v; }
    static final boolean DEBUG = Boolean.getBoolean("mountain.debug") || System.getProperty("fabric-api.gametest") != null;
}
