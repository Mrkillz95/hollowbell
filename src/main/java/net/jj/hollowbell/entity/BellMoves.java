package net.jj.hollowbell.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.ModSounds;
import net.jj.hollowbell.fx.BigFx;
import net.jj.hollowbell.net.ThumpPayload;
import net.jj.hollowbell.rig.BellAnim;
import net.jj.hollowbell.rig.BellRig;
import net.jj.hollowbell.rig.CarryPath;
import net.jj.hollowbell.solid.GiantHull;
import net.jj.hollowbell.world.NoWait;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * What each of his moves does to the world, the things he is holding, and the inside of his dome. Server only.
 *
 * His moves come in three strengths (see {@link Moves#tier}). Light ones are quick, cheap and used often. Medium
 * ones give a clear warning, then really hurt. Heavy ones are rare: a long wind-up you can see and hear coming,
 * then something that clears a wide area; afterwards he's worn out and slow for a few seconds. How hard each one
 * hits is set at full size and scaled by his size and the damage setting in {@link HollowbellEntity#dmg}.
 */
public final class BellMoves {
    final HollowbellEntity h;
    final BellRig rig;
    final BellHolds holds;
    final BellHeavyMoves heavy;

    int move, t, arg = -1, lastArg = -1, cooldown = 60, harvestIn = 400;

    @Nullable
    LivingEntity target;
    /** what the move running now is aimed at (null between moves) */
    public @Nullable LivingEntity moveTarget() {
        return target;
    }
    /** the strand grabbing: who, on which seat, how far up, how many hits it has taken */
    @Nullable
    LivingEntity grabbed;

    @Nullable
    Tree grabbedTree;

    @Nullable
    Seat grabSeat;

    /** the pod a grabbed player is being taken up into (-1: the dome) */
    int grabPod = -1;

    float lift;
    float strandHits;
    boolean gentle;
    Vec3 intoFrom = Vec3.ZERO;
    /**
     * Carrying somebody up onto his crown (a gentle grab, see runCarry): back once the strand is on its way back down
     * (they're on the crown, or were let go); ticks into the
     * way up and how long it takes; ticks into the strand going back down, how long that takes and from how far up;
     * how far up it is now (0-1).
     */
    boolean back;
    /**
     * Setting whoever rides the crown down (G): who asked, the strand and the spot on the ground chosen, how long
     * he's been sinking to it; then, as a gentle grab with down set, the strand going up empty (phase UP) and coming
     * back down with them (phase DOWN).
     */
    @Nullable
    LivingEntity downWho;

    int downStrand = -1, downWait, carryPhase;

    @Nullable
    Vec3 downSpot;

    boolean down;
    static final int PHASE_UP = 1, PHASE_DOWN = 2;
    int reachHurt;
    int carryT, carryLen, backT, backLen;
    float backFrom, carryU;
    /** coming to pick somebody up: the strand that will do it, a pause after a miss, how long he's been at it */
    int fetchStrand = -1, fetchPause, fetchWait, fetchThere;
    /** where the one being reached for stood, and their health, when the strand set out */
    Vec3 reachFrom = Vec3.ZERO;

    float reachHp;
    /** the arm wrap */
    @Nullable
    LivingEntity wrapped;

    @Nullable
    Seat wrapSeat;

    float armHits;
    /** whatever is inside the dome */
    final List<Inside> inside = new ArrayList<>();

    float hurtInside;
    private final Map<UUID, Integer> stingAt = new HashMap<>();
    final Set<UUID> struckThisMove = new HashSet<>();
    /** per creature, the tick it was last hurt by the move of the moment (for moves that hurt over and over) */
    final Map<UUID, Integer> hitAt = new HashMap<>();

    @Nullable
    BlockPos treeAt;
    /** the slam's arm has come down (it only hits once); which of the storm's arms have */
    boolean landed;

    private int stormLanded;
    private final int[] landedAt = new int[8];
    /** worn out after a heavy move: ticks left */
    private int tired;
    /** gametime a heavy move is his to use again, and each move's last use */
    private long heavyReadyAt = 300;
    /** gametime this fight started (-1: not fighting), and how long he has had nobody to fight */
    private long fightSince = -1;

    private int calmFor;
    int lastMove;
    /** the spore clouds drifting after what he's hunting */
    final List<AreaEffectCloud> clouds = new ArrayList<>();
    /** the sky dive: how high he goes, how far over he has turned, where he'll come down */
    private double diveTop;

    float diveOver;
    /** the sun lances: where each beam touches the ground */
    final Vec3[] lance = new Vec3[5];

    static final class Inside {
        @Nullable
        LivingEntity e;

        @Nullable
        Tree tree;

        @Nullable
        Seat seat;

        int slot, age;
        /** the pod a player is shut in (-1: in the dome) */
        int pod = -1;
    }

    /** a tree he pulled up, as block displays hanging together */
    static final class Tree {
        final List<Display.BlockDisplay> blocks = new ArrayList<>();
        final List<Vector3f> offsets = new ArrayList<>();
        final List<BlockState> states = new ArrayList<>();
        float size = 1f;
    }

    BellMoves(HollowbellEntity h) {
        this.h = h;
        this.rig = h.rig;
        holds = new BellHolds(this);
        heavy = new BellHeavyMoves(this);
    }

    public int move() {
        return move;
    }
    /** the arm or strand the move of the moment (or the last one) used */
    public int lastStrand() {
        return lastArg;
    }

    public int t() {
        return t;
    }

    public void setCooldown(int c) {
        cooldown = c;
    }

    public float lift() {
        return lift;
    }

    public @Nullable LivingEntity grabbed() {
        return grabbed;
    }

    public @Nullable LivingEntity wrapped() {
        return wrapped;
    }

    public int insideCount() {
        return inside.size();
    }

    public List<LivingEntity> insideNow() {
        List<LivingEntity> l = new ArrayList<>();
        for (Inside i : inside) if (i.e != null) l.add(i.e);
        return l;
    }

    public int treesInside() {
        int n = 0;
        for (Inside i : inside) if (i.tree != null) n++;
        return n;
    }

    public boolean isTired() {
        return tired > 0;
    }
    /** the heavy moves are his to use now */
    public void restNow() {
        tired = 0;
        h.setTired(false);
        heavyReadyAt = 0;
        cooldown = 0;
        fightSince = 0;
    }

    /**
     * Over half health he keeps his heavy moves for later: none until heavyDelaySeconds into a fight. Under half,
     * only the usual wait after the last one.
     */
    public boolean heavyWaitOver() {
        if (h.healthNow() <= h.healthMax() * 0.5f) return true;
        long wait = Math.max(0, BellConfig.V.heavyDelaySeconds) * 20L;
        return fightSince >= 0 && h.level().getGameTime() - fightSince >= wait;
    }

    /** the fight clock: it starts when he goes for something and stops after half a minute with nobody */
    private void fightClock() {
        if (h.getTarget() != null) {
            if (fightSince < 0) fightSince = h.level().getGameTime();
            calmFor = 0;
        } else if (fightSince >= 0 && ++calmFor > 600) fightSince = -1;
    }

    /** what's left of his waits, to save: until a heavy move, and how far into a fight he is (-1 none) */
    long heavyIn() {
        return Math.max(0, heavyReadyAt - h.level().getGameTime());
    }

    long fightFor() {
        return fightSince < 0 ? -1 : h.level().getGameTime() - fightSince;
    }

    /** loaded: the waits picked up again from now */
    void resumeWaits(long heavyIn, long fightFor) {
        long now = h.level().getGameTime();
        heavyReadyAt = now + heavyIn;
        fightSince = fightFor < 0 ? -1 : now - fightFor;
    }

    /** he stays where he is for this (he still rises and sinks) */
    public boolean holdsStill() {
        return switch (move) {
            case Moves.GRAB -> gentle ? !back : t <= Moves.REACH;
            case Moves.HARVEST -> t <= Moves.REACH;
            case Moves.DROP,
                    Moves.CURTAIN,
                    Moves.WRAP,
                    Moves.WHIRLPOOL,
                    Moves.DEEP_TOLL,
                    Moves.ARM_STORM,
                    Moves.UNDERTOW,
                    Moves.SUN_LANCES,
                    Moves.STINGER_STORM,
                    Moves.FLASH,
                    Moves.POD_BURST -> true;
            case Moves.SLAM -> t > 20;
            case Moves.SKY_DIVE -> t >= Moves.DIVE_CLIMB;
            default -> false;
        };
    }

    /** the height (of his base) a move needs him at, or null to let him choose */
    public @Nullable Double wantsHeight() {
        float s = s();
        double tg = target != null ? target.getY() : Double.NaN;
        switch (move) {
            case Moves.GRAB, Moves.HARVEST -> {
                // (carrying somebody up, he keeps his height: see HollowbellEntity.fly)
                if (gentle) return null;
                // the strand ends down at what it's reaching for
                if (t <= Moves.REACH + 4) {
                    if (target != null) return tg - 1.5 * s;
                    if (treeAt != null) return (double) treeAt.getY();
                }
            }
            // the strands and arms hit on the ground: down he comes to it
            case Moves.CURTAIN, Moves.SLAM, Moves.WRAP, Moves.SWEEP, Moves.LASH -> {
                if (target != null) return tg - 2 * s;
            }
            case Moves.WHIRLPOOL, Moves.UNDERTOW, Moves.ARM_STORM -> {
                return (target != null ? Math.min(tg, groundUnder()) : groundUnder()) - 3 * s;
            }
            case Moves.POD_BURST -> {
                if (target != null) return tg - 20 * s;
            }
            case Moves.SKY_DIVE -> {
                if (t < Moves.DIVE_CLIMB + Moves.DIVE_TURN) return diveTop;
                if (t >= Moves.DIVE_CLIMB + Moves.DIVE_TURN + Moves.DIVE_FALL) return groundUnder() + 6 * s;
            }
            default -> {}
        }
        return null;
    }

    /** the sky dive's fall: he's driven down at this speed (blocks a tick), or null */
    public @Nullable Vec3 steer() {
        if (move != Moves.SKY_DIVE
                || t < Moves.DIVE_CLIMB + Moves.DIVE_TURN
                || t >= Moves.DIVE_CLIMB + Moves.DIVE_TURN + Moves.DIVE_FALL) return null;
        float s = s();
        Vec3 across = Vec3.ZERO;
        Vector3f a = h.aim();
        Vec3 aimAt = h.toWorld(a);
        Vec3 d = new Vec3(aimAt.x - h.getX(), 0, aimAt.z - h.getZ());
        if (d.length() > 1) across = d.normalize().scale(Math.min(d.length() * 0.05, 0.4 + 0.6 * s));
        return new Vec3(across.x, -(0.9 + 1.6 * s), across.z);
    }

    double groundUnder() {
        return h.groundAt(h.getX(), h.getZ());
    }

    public boolean caught(@Nullable Entity e) {
        if (e == null) return false;
        if (e == grabbed || e == wrapped) return true;
        // (whoever a strand is reaching out to carry up onto his crown is as good as held: it doesn't sting them)
        if (gentle && move == Moves.GRAB && e == target && t <= Moves.REACH) return true;
        for (Inside i : inside) if (i.e == e) return true;
        return false;
    }

    /**
     * a strand or arm is closing on it (the grab, the wrap, the harvest): his body doesn't shove it out of the way
     * first
     */
    public boolean closingOn(@Nullable Entity e) {
        return e != null && e == target && (move == Moves.GRAB || move == Moves.WRAP || move == Moves.HARVEST);
    }

    public boolean isInside(Entity e) {
        for (Inside i : inside) if (i.e == e) return true;
        return false;
    }

    /** the pod this one is shut in, or -1 */
    public int podOf(Entity e) {
        for (Inside i : inside) if (i.e == e) return i.pod;
        return -1;
    }

    public boolean usesSeat(Seat s) {
        if (s == grabSeat || s == wrapSeat) return true;
        for (Inside i : inside) if (i.seat == s) return true;
        return false;
    }

    ServerLevel level() {
        return (ServerLevel) h.level();
    }

    float s() {
        return h.bellScale();
    }

    // starting and ending

    /** makes him do a move now. False if he can't (down, nothing to do it to). Cuts off whatever he was doing. */
    public boolean force(int which, @Nullable LivingEntity at) {
        if (h.isDeadOrDying() || which <= 0 || which >= Moves.NAMES.length) return false;
        // a strand carrying somebody up onto his crown (or on its way back down from there) finishes first
        if (gentle && move == Moves.GRAB) return false;
        if (move != Moves.NONE) end();
        return start(which, at);
    }

    /** a move aimed at a spot rather than at somebody (an operator's /hollowbell do <move> <x y z>) */
    public boolean forceAt(int which, Vec3 spot) {
        forcedSpot = spot;
        try {
            return force(which, null);
        } finally {
            forcedSpot = null;
        }
    }

    @Nullable
    Vec3 forcedSpot;

    private boolean start(int which, @Nullable LivingEntity at) {
        if (h.sunk()
                && (which == Moves.DROP || which == Moves.GRAB || which == Moves.HARVEST || which == Moves.SKY_DIVE))
            return false;
        target = at;
        struckThisMove.clear();
        hitAt.clear();
        slapped.clear();
        Vector3f aim =
                at != null ? h.toModel(aimAt(at)) : forcedSpot != null ? h.toModel(forcedSpot) : new Vector3f(0, 0, 40);
        int a = -1;
        float s = s();
        switch (which) {
            case Moves.GRAB -> {
                if (at == null || grabbed != null || grabbedTree != null || !HollowbellEntity.canCarry(at))
                    return false;
                a = nearestStrand(at.position());
                if (a < 0) return false;
                grabbed = null;
                lift = 0f;
                strandHits = 0f;
                gentle = false;
            }
            case Moves.HARVEST -> {
                if (grabbed != null || grabbedTree != null) return false;
                LivingEntity mob = at != null && !(at instanceof Player) && HollowbellEntity.canCarry(at)
                        ? at
                        : holds.findHarvestMob();
                if (mob != null) {
                    target = mob;
                    aim = h.toModel(mob.position());
                    a = nearestStrand(mob.position());
                } else {
                    BlockPos tree = holds.findTree();
                    if (tree == null) return false;
                    aim = h.toModel(Vec3.atBottomCenterOf(tree));
                    a = nearestStrand(Vec3.atBottomCenterOf(tree));
                    treeAt = tree;
                    target = null;
                }
                if (a < 0) return false;
                lift = 0f;
                strandHits = 0f;
                gentle = false;
            }
            case Moves.SLAM, Moves.WRAP -> {
                if (which == Moves.WRAP && (at == null || wrapped != null || !HollowbellEntity.canCarry(at)))
                    return false;
                a = at != null ? nearestArm(aimAt(at)) : h.getRandom().nextInt(rig.arms.length);
                armHits = 0f;
            }
            case Moves.ARM_STORM ->
                a = at != null ? nearestArm(at.position()) : h.getRandom().nextInt(rig.arms.length);
            case Moves.LASH -> {
                a = lashStrand(at != null ? at.position() : h.position().add(20 * s, 0, 0));
                if (a < 0) return false;
            }
            case Moves.SHED -> {
                if (h.eggsLeft() == 0 || BellConfig.V.shedCount <= 0) return false;
            }
            case Moves.EGG_RAIN -> {
                if (h.eggsLeft() == 0) return false;
            }
            case Moves.POD_BURST -> {
                if (h.podsLeft() == 0) return false;
            }
            case Moves.SWEEP -> {
                if (at == null && forcedSpot == null) aim = new Vector3f(1, 0, 0);
            }
            case Moves.SKY_DIVE -> {
                double top = Math.max(h.getY(), at != null ? at.getY() : h.getY()) + 70 * s + 30;
                diveTop = Math.min(top, NoWait.ceiling(h.level()) - (rig.crownY + 20) * s);
                diveOver = 0f;
            }
            case Moves.SUN_LANCES -> {
                for (int i = 0; i < 5; i++) lance[i] = null;
            }
            default -> {}
        }
        if (which == Moves.EGG_RAIN) {
            lastEggs.clear();
            eggSpot = forcedSpot;
        }
        move = which;
        t = 0;
        arg = a;
        landed = false;
        lashWas = null;
        diveWarned = false;
        diveAt = null;
        lastMove = which;
        h.setMove(which, a, aim);
        if (which == Moves.SKY_DIVE) h.setLift(0f);
        warn(which, a);
        return true;
    }

    /** what you see and hear as a move begins: the tell, so you can get out of the way */
    void warn(int which, int a) {
        float s = s();
        Vec3 mid = h.position().add(0, (rig.rimY + 20) * s, 0);
        switch (which) {
            case Moves.GRAB, Moves.HARVEST -> h.sound(h.strandTipWorld(a), ModSounds.STRAND, 1.5f, 0.8f);
            case Moves.SLAM -> h.sound(h.armTipWorld(a), SoundEvents.RAVAGER_ROAR, 2.5f, 0.5f);
            case Moves.CURTAIN -> h.sound(h.position().add(0, 20 * s, 0), ModSounds.STRAND, 3f, 0.6f);
            case Moves.SWEEP -> h.sound(h.position().add(0, 30 * s, 0), ModSounds.STRAND, 3f, 0.5f);
            case Moves.WRAP -> h.sound(h.armTipWorld(a), SoundEvents.COPPER_PLACE, 2.5f, 0.5f);
            case Moves.PULSE -> h.sound(mid, ModSounds.WARN_DROP, 2f, 1.2f);
            case Moves.SHED, Moves.SPORES -> h.sound(h.position().add(0, 60 * s, 0), ModSounds.CLICK, 3f, 0.6f);
            // the egg rain's tell: a rising glassy shiver while he tips and his glow builds (see eggRain)
            case Moves.EGG_RAIN -> {
                h.sound(mid, ModSounds.EGG_WIND, 4f, 1f);
                h.sound(h.position().add(0, 60 * s, 0), ModSounds.CLICK, 3f, 0.6f);
            }
            case Moves.VOLLEY -> h.sound(h.position().add(0, 20 * s, 0), ModSounds.CLICK, 2.5f, 1f);
            case Moves.LASH -> h.sound(h.strandTipWorld(a), ModSounds.STRAND, 2f, 0.9f);
            case Moves.FLASH -> h.sound(mid, ModSounds.FLASH, 2.5f, 0.6f);
            case Moves.POD_BURST -> h.sound(h.position().add(0, 50 * s, 0), ModSounds.GOO, 3f, 0.5f);
            // the heavy ones: a loud warning, a message to everyone near, and his glow flares (see Moves.pose)
            case Moves.DROP -> heavyWarning(ModSounds.WARN_DROP, "drop");
            case Moves.WHIRLPOOL -> heavyWarning(ModSounds.WARN_WHIRLPOOL, "whirlpool");
            case Moves.SKY_DIVE -> heavyWarning(ModSounds.WARN_DIVE, "sky_dive");
            case Moves.DEEP_TOLL -> heavyWarning(ModSounds.WARN_TOLL, "deep_toll");
            case Moves.ARM_STORM -> heavyWarning(ModSounds.WARN_ARMS, "arm_storm");
            case Moves.STINGER_STORM -> heavyWarning(ModSounds.WARN_STINGERS, "stinger_storm");
            case Moves.SUN_LANCES -> heavyWarning(ModSounds.WARN_LANCES, "sun_lances");
            case Moves.UNDERTOW -> heavyWarning(ModSounds.WARN_UNDERTOW, "undertow");
            default -> {}
        }
    }

    private void heavyWarning(SoundEvent ev, String name) {
        float s = s();
        h.sound(h.position().add(0, (rig.rimY + 20) * s, 0), ev, 4f, 1f);
        for (ServerPlayer p : level().players())
            if (p.distanceToSqr(h) < Mth.square(200 * s + 80))
                p.displayClientMessage(
                        Component.translatable("warn.hollowbell." + name).withStyle(ChatFormatting.RED), true);
    }

    /** ends whatever move he's in the middle of, straight away (he eases back to rest by himself) */
    public void stopNow() {
        if (move != Moves.NONE) end();
    }

    /** for the operator's commands: every wait between moves let off */
    public void clearCooldowns() {
        cooldown = 0;
        heavyReadyAt = 0;
        tired = 0;
        h.setTired(false);
        fightSince = 0;
    }

    void end() {
        int was = move;
        if (move == Moves.GRAB || move == Moves.HARVEST) holds.letGoOfGrab(false);
        gentle = false;
        back = false;
        down = false;
        carryU = 0f;
        carryPhase = 0;
        carryWho = null;
        if (move == Moves.WRAP) holds.letGoOfWrap();
        move = Moves.NONE;
        t = 0;
        arg = -1;
        target = null;
        h.setMove(Moves.NONE, -1, new Vector3f());
        treeAt = null;
        int tier = Moves.tier(was);
        boolean angry = h.angry();
        var r = h.getRandom();
        cooldown = switch (tier) {
            case Moves.LIGHT -> (angry ? 16 : 24) + r.nextInt(20);
            case Moves.MEDIUM -> (angry ? 40 : 60) + r.nextInt(40);
            default -> 90 + r.nextInt(40);
        };
        if (tier == Moves.HEAVY && was != Moves.NONE) {
            // worn out: slower, drooping, nothing for a few seconds; and a while before the next heavy one
            tired = 100;
            h.setTired(true);
            h.sound(h.position().add(0, rig.rimY * s(), 0), ModSounds.TIRED, 2.5f, 0.8f);
            heavyReadyAt = h.level().getGameTime() + (angry ? 300 : 500) + r.nextInt(300);
        }
    }

    public void tick() {
        giantAt.clear();
        fightClock();
        holds.insideTick();
        heavy.cloudsTick();
        if (tired > 0 && --tired == 0) h.setTired(false);
        if (h.isDeadOrDying()) return;
        if (fetchPause > 0) fetchPause--;
        if (downWho != null) {
            // setting the rider down: whatever else he was doing stops (a strand already busy with a carry finishes
            // first)
            if (h.rider() != downWho || !downWho.isAlive()) downWho = null;
            else if (move != Moves.NONE && !gentle) end();
            else if (move == Moves.NONE) {
                downWait++;
                // (right down at the ground, so the strand reaches it without stretching; or he's tried a good while)
                boolean there = downSpot != null && Math.abs(h.getY() - downSpot.y) < 0.3 + 1.5 * s();
                if ((there && downWait > 10) || downWait > 20 * 30) holds.startCarryDown();
                return;
            }
        }
        if (move != Moves.NONE) {
            t++;
            run();
            // (a carry up onto his crown takes as long as the way up is long, and ends itself)
            if (move != Moves.NONE && (gentle ? t > 20 * 120 : t >= Moves.length(move))) end();
            return;
        }
        LivingEntity fetch = h.fetchingNow();
        if (fetch == null) {
            fetchStrand = -1;
            fetchWait = 0;
            fetchThere = 0;
        } else if (!h.resting() && fetchPause == 0) {
            // he comes alongside: the one who asked just outside his arms, in line with the strand that will carry them
            Vec3 spot = holds.fetchSpot(fetch);
            float s = s();
            fetchWait++;
            boolean near = h.horiz(spot) < 1.5 + 3 * s;
            fetchThere = near ? fetchThere + 1 : 0;
            boolean level = Math.abs(h.getY() - fetch.getY()) < 0.3 + 1.5 * s || fetchThere > 200;
            // (if he somehow can't get to the spot, he tries from where he is after a good while)
            boolean waited = fetchWait > 20 * 45 && h.horiz(fetch.position()) < h.bellRadius() * 1.4;
            if (((near && level) || waited) && holds.startCarry(fetch)) {
                fetchWait = 0;
                fetchThere = 0;
                return;
            }
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (tired > 0 || h.arriving()) return;
        if (h.carrying()) return; // whoever is on his crown picks the moves
        choose();
    }

    /**
     * picks a move for what he's after: what's in reach, what's up high, how many there are, whether a heavy one is his
     */
    private void choose() {
        if (h.asleep()) return; // asleep: nothing is started
        LivingEntity tg = h.getTarget();
        if (tg == null) {
            if (BellConfig.V.harvest && --harvestIn <= 0) {
                harvestIn = 500 + h.getRandom().nextInt(900);
                if (h.variant() != HollowbellEntity.CALM || h.getRandom().nextInt(3) == 0) start(Moves.HARVEST, null);
            }
            return;
        }
        float s = s();
        double d = h.horiz(tg.position());
        double bell = h.bellRadius();
        boolean flying = tg instanceof Player p && (p.isFallFlying() || p.getAbilities().flying)
                || tg.getY() - h.groundAt(tg.getX(), tg.getZ()) > 8 + 20 * s;
        int crowd = near(h.bodyBox().inflate(bell)).size();
        boolean heavy = h.level().getGameTime() >= heavyReadyAt && heavyWaitOver() && !h.sunk();
        boolean night = h.level().getSkyDarken() > 6;
        List<int[]> opts = new ArrayList<>();
        // light
        boolean carry = HollowbellEntity.canCarry(tg);
        if (d < bell * 0.85 && !h.sunk() && carry) opts.add(new int[] {Moves.GRAB, 4});
        if (d < bell * 3) opts.add(new int[] {Moves.VOLLEY, flying ? 5 : 3});
        if (d < bell * 1.3) opts.add(new int[] {Moves.LASH, 4});
        if (d < 50 * s + 30) opts.add(new int[] {Moves.FLASH, night ? 3 : 1});
        // medium
        if (flying && d < bell * 2.5) opts.add(new int[] {Moves.PULSE, 6});
        if (d < bell * 0.85) opts.add(new int[] {Moves.CURTAIN, 2});
        if (d < bell * 1.4) opts.add(new int[] {Moves.SWEEP, 2});
        if (d < bell * 1.7) opts.add(new int[] {Moves.SLAM, 3});
        if (d < bell * 1.5 && carry) opts.add(new int[] {Moves.WRAP, 2});
        if (d < bell * 1.6) opts.add(new int[] {Moves.PULSE, 1});
        if (d < bell * 2) opts.add(new int[] {Moves.SPORES, 2});
        if (d < bell * 0.8 && h.podsLeft() > 0) opts.add(new int[] {Moves.POD_BURST, 2});
        if (d < bell * 2 && h.eggsLeft() > 0) opts.add(new int[] {Moves.EGG_RAIN, 2});
        if (d < h.senseRange() && h.eggsLeft() > 0 && BellConfig.V.shedCount > 0 && bellingsNear() < 8)
            opts.add(new int[] {Moves.SHED, 1});
        // heavy: rare, and more likely the more there are to hit
        if (heavy) {
            int w = crowd >= 3 ? 4 : 2;
            if (d < bell * 0.8) opts.add(new int[] {Moves.DROP, w});
            if (d < bell * 1.8) opts.add(new int[] {Moves.WHIRLPOOL, w});
            if (d < bell * 4) opts.add(new int[] {Moves.SKY_DIVE, flying ? 1 : w});
            if (d < bell * 2.5) opts.add(new int[] {Moves.DEEP_TOLL, flying ? w + 2 : w});
            if (d < bell * 1.8) opts.add(new int[] {Moves.ARM_STORM, w});
            if (d < bell * 3) opts.add(new int[] {Moves.STINGER_STORM, flying ? w + 1 : w});
            if (d < bell * 3) opts.add(new int[] {Moves.SUN_LANCES, w});
            if (d < bell * 2) opts.add(new int[] {Moves.UNDERTOW, w});
        }
        int total = 0;
        for (int[] o : opts) {
            if (o[0] == lastMove) o[1] = Math.max(1, o[1] / 2);
            total += o[1];
        }
        if (total == 0) return;
        int r = h.getRandom().nextInt(total);
        for (int[] o : opts) {
            r -= o[1];
            if (r < 0) {
                if (!start(o[0], tg)) cooldown = 10;
                return;
            }
        }
    }

    private int bellingsNear() {
        return level().getEntitiesOfClass(Belling.class, h.bodyBox().inflate(40))
                .size();
    }

    // hitting

    /**
     * A blow: it lands even straight after another (a creature's moment of safety after a hit is taken away first),
     * and throws what it hits away from from, out and up.
     */
    void blow(LivingEntity e, float base, Vec3 from, double out, double up) {
        if (!h.fightingGiant(e)) return; // a giant he isn't fighting is left alone
        e.invulnerableTime = 0;
        e.hurt(h.damageSources().mobAttack(h), h.dmg(base, e));
        Vec3 d = new Vec3(e.getX() - from.x, 0, e.getZ() - from.z);
        d = d.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : d.normalize();
        double k = 1.0 - Mth.clamp(e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0, 1) * 0.6;
        e.setDeltaMovement(e.getDeltaMovement().add(d.x * out * k, up * k, d.z * out * k));
        e.hurtMarked = true;
    }

    /** hurts e at most once every gap ticks during a move */
    boolean every(LivingEntity e, int gap) {
        Integer last = hitAt.get(e.getUUID());
        if (last != null && t - last < gap) return false;
        hitAt.put(e.getUUID(), t);
        return true;
    }

    /** it knocks flying players out of the air */
    void knockDown(LivingEntity e) {
        if (e instanceof ServerPlayer p && (p.isFallFlying() || p.getAbilities().flying)) {
            p.stopFallFlying();
            if (!p.isCreative() && p.getAbilities().flying) {
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
            }
            p.setDeltaMovement(p.getDeltaMovement().x, -1.6, p.getDeltaMovement().z);
            p.displayClientMessage(Component.translatable("message.hollowbell.knocked_down"), true);
        }
    }

    // each move, tick by tick

    private void run() {
        float s = s();
        double bell = h.bellRadius();
        switch (move) {
            case Moves.GRAB, Moves.HARVEST -> holds.runGrab();
            case Moves.CURTAIN -> runCurtain(s);
            case Moves.SWEEP -> runSweep(s, bell);
            case Moves.SLAM -> runSlam(s);
            case Moves.WRAP -> holds.runWrap();
            case Moves.PULSE -> runPulse(s, bell);
            case Moves.DROP -> runDrop(s, bell);
            case Moves.SHED -> {
                if (t == Moves.SHED_AT) shed();
            }
            case Moves.VOLLEY -> {
                if (t == Moves.VOLLEY_AT || t == Moves.VOLLEY_AT + 8 || t == Moves.VOLLEY_AT + 16) volley();
            }
            case Moves.LASH -> runLash(s, bell);
            case Moves.FLASH -> runFlash(s);
            case Moves.SPORES -> {
                if (t == Moves.SPORES_AT) heavy.spores();
            }
            case Moves.POD_BURST -> heavy.podBurst();
            case Moves.EGG_RAIN -> heavy.eggRain();
            case Moves.WHIRLPOOL -> heavy.whirlpool();
            case Moves.SKY_DIVE -> heavy.skyDive();
            case Moves.DEEP_TOLL -> heavy.deepToll();
            case Moves.ARM_STORM -> runArmStorm(s);
            case Moves.STINGER_STORM -> {
                if (t == Moves.RAIN_FROM - 10) h.sound(h.position().add(0, 30 * s, 0), ModSounds.CLICK, 4f, 0.8f);
                if (t >= Moves.RAIN_FROM && t < Moves.RAIN_TO && t % 2 == 0) heavy.stingerRain();
            }
            case Moves.SUN_LANCES -> heavy.sunLances();
            case Moves.UNDERTOW -> heavy.undertow();
            default -> {}
        }
    }

    private void runCurtain(float s) {
        // it follows you while it closes, then it has you
        if (target != null && t < Moves.CURTAIN_CLOSE && target.isAlive()) h.setAim(h.toModel(aimAt(target)));
        if (t >= Moves.CURTAIN_CLOSE - 6 && t < Moves.CURTAIN_CLOSE + Moves.CURTAIN_HOLD && t % 5 == 0) {
            Vec3 c = h.toWorld(h.aim());
            double r = 16 * s + 4;
            for (LivingEntity e : near(new AABB(c, c).inflate(r, 10 + 30 * s, r))) {
                Vec3 in = new Vec3(c.x - at(e).x, 0, c.z - at(e).z);
                double dist = in.length();
                if (dist > r) continue;
                if (dist > 1.5)
                    e.setDeltaMovement(
                            e.getDeltaMovement().scale(0.4).add(in.normalize().scale(0.35)));
                e.hurtMarked = true;
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
                if (every(e, 20)) {
                    blow(e, 7f, c, 0, 0);
                    sting(e);
                }
            }
            if (t % 15 == 0) h.sound(c, ModSounds.GRAB, 2f, 0.6f);
        }
        Vec3 mid = h.toWorld(h.aim());
        if (t >= 6 && t <= 34 && t % 4 == 2) {
            for (int k = 0; k < 6; k++) {
                Vec3 tip = h.strandTipWorld(Math.floorMod(k * 13 + 5, rig.strands.length));
                Vec3 to = mid.subtract(tip).multiply(1, 0, 1);
                fx(
                        BigFx.TRAIL,
                        new Vec3(tip.x, h.groundAt(tip.x, tip.z), tip.z),
                        px(4, 1.5),
                        to.lengthSqr() > 1e-6 ? to.normalize() : Vec3.ZERO,
                        0);
            }
        }
        if (t == Moves.CURTAIN_CLOSE) fx(BigFx.DUST_RING, new Vec3(mid.x, h.groundAt(mid.x, mid.z), mid.z), px(16, 4));
    }

    private void runSweep(float s, double bell) {
        Vector3f a = h.aim();
        Vec3 dir = h.toWorld(new Vector3f(a.x, 0, a.z).normalize().mul(10))
                .subtract(h.position())
                .normalize();
        if (t >= 22 && t <= 40 && t % 4 == 2) {
            // dust dragged along by the strand ends at the front of the swing
            for (int k = 0; k < 6; k++) {
                Vec3 tip = h.strandTipWorld(Math.floorMod(arg + k * 13, rig.strands.length));
                fx(BigFx.TRAIL, new Vec3(tip.x, h.groundAt(tip.x, tip.z), tip.z), px(4, 1.5), dir, 0);
            }
        }
        if (t >= 21 && t <= 44) {
            double r = bell * 1.35 + 4;
            // (only what the swing has reached so far: the strands run across from its back to its front)
            double front = Moves.sweepFront(t);
            // (the box round him only reaches 112 out; the sweep reaches further)
            for (LivingEntity e :
                    near(h.bodyBox().inflate(r - bell + 4, 0, r - bell + 4).setMaxY(h.getY() + rig.rimY * s * 0.8))) {
                if (struckThisMove.contains(e.getUUID()) || h.horiz(at(e)) > r) continue;
                Vec3 off = at(e).subtract(h.position()).multiply(1, 0, 1);
                if (Mth.clamp(off.dot(dir) / r, -1, 1) > front) continue;
                struckThisMove.add(e.getUUID());
                blow(e, 18f, h.position(), 0, 0.6);
                e.setDeltaMovement(e.getDeltaMovement().add(dir.x * 2.0, 0, dir.z * 2.0));
                sting(e);
            }
            if (t == 30) h.sound(h.position().add(0, 30 * s, 0), ModSounds.WHIP, 3f, 0.4f);
        }
    }

    private void runSlam(float s) {
        int hit = Math.round(BellRig.SLAM_HIT * Moves.length(Moves.SLAM));
        // it follows you while the arm is up, then it's coming down where you were
        if (target != null && target.isAlive() && t < hit - 10) h.setAim(h.toModel(aimAt(target)));
        // the arm has weight: it lands when its tip really comes down, a moment after it's swung
        Vec3 tipNow = h.armTipWorld(arg);
        double gy = h.groundAt(tipNow.x, tipNow.z);
        boolean down = tipNow.y - gy < 6 * s + 2;
        if (!landed && t >= hit - 2 && (down || t >= hit + 14)) {
            landed = true;
            landedAt[arg] = t;
            armBlow(tipNow, 32f, 12 * s + 4);
        }
        if (landed) armSlap(arg, 32f, landedAt[arg]);
    }

    private void runPulse(float s, double bell) {
        // (the bell shuts a moment before the shock goes out)
        if (t == Moves.PULSE_AT - 5) h.pulse(1.6f);
        if (t == Moves.PULSE_AT) {
            h.sound(h.position().add(0, rig.rimY * s, 0), ModSounds.SHOCK, 3f, 0.8f);
            thump(h.position(), 0.6f);
            fx(BigFx.DUST_RING, under(), h.bellRadius() + px(70, 30));
            fx(BigFx.FLASH, h.position().add(0, rig.rimY * s, 0), px(30, 8), Vec3.ZERO, GLOW);
        }
        if (t > Moves.PULSE_AT && t <= Moves.PULSE_AT + 24) {
            // the shock goes out from the rim in a ring
            double r = bell + (t - Moves.PULSE_AT) / 24.0 * (70 * s + 30);
            Vec3 c = h.position().add(0, rig.rimY * s, 0);
            for (LivingEntity e : near(h.bodyBox().inflate(80 * s + 34, 90 * s + 40, 80 * s + 34))) {
                if (struckThisMove.contains(e.getUUID())) continue;
                double d = at(e).distanceTo(c);
                if (d > r) continue;
                struckThisMove.add(e.getUUID());
                blow(e, 18f, c, 2.0, 0.6);
                e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 140, 0));
                knockDown(e);
            }
        }
    }

    private void runDrop(float s, double bell) {
        int hit = Moves.DROP_WIND + Moves.DROP_FALL;
        if (t == hit) {
            Vec3 c = h.position();
            double r = bell * 1.2 + 6;
            for (LivingEntity e :
                    near(h.bodyBox().inflate(r - bell + 4, 0, r - bell + 4).setMaxY(h.getY() + 50 * s + 8))) {
                double d = h.horiz(at(e));
                if (d > r) continue;
                blow(e, 52f * (float) (1 - 0.35 * d / r), c, 2.4, 0.9);
                knockDown(e);
            }
            h.sound(c, ModSounds.SLAM, 4f, 0.5f);
            h.sound(c, ModSounds.SPLASH, 3f, 0.6f);
            fx(BigFx.DUST, under(), bell * 0.7 + px(0, 4));
            // (the whole bell down on the ground: chunks, dust and a ring; on water a splash and a wave)
            fx(heavy(bell * 1.2 + px(0, 6)), under(), bell * 1.2 + px(0, 6));
            fx(BigFx.SHOCKWAVE, under(), bell * 2.4 + px(0, 10));
            if (griefing()) crack(BlockPos.containing(c.x, groundUnder(), c.z), (int) Math.ceil(bell * 0.8));
        }
        heavy.bellTone(hit);
        // he swims back up: a beat, and the downdraft as he lifts off, then two more
        int up = hit + Moves.DROP_DOWN;
        if (t == up || t == up + 40 || t == up + 80) h.pulse(1.3f);
        if (t == up) fx(BigFx.DUST_RING, under(), bell * 1.1 + px(0, 6));
    }

    private void runLash(float s, double bell) {
        if (t == Moves.LASH_AT - 3) h.sound(h.strandTipWorld(arg), ModSounds.WHIP, 2.5f, 0.6f);
        // what the strand passes through between one tick and the next, while it whips past, is hit
        Vec3[] now = strandLine(arg);
        Vec3[] was = lashWas;
        lashWas = now;
        if (was == null || t < Moves.LASH_AT - 5 || t > Moves.LASH_AT + 8) return;
        Vec3 tip = now[now.length - 1], tipWas = was[was.length - 1];
        Vec3 way = tip.subtract(tipWas).multiply(1, 0, 1);
        way = way.lengthSqr() > 1e-6 ? way.normalize() : Vec3.ZERO;
        if (t == Moves.LASH_AT || t == Moves.LASH_AT + 3) {
            // the end cracks past, dragging dust; where it meets the ground, a thud
            Vec3 low = new Vec3(tip.x, h.groundAt(tip.x, tip.z), tip.z);
            fx(BigFx.TRAIL, low, px(4, 1.5), way, 0);
            if (t == Moves.LASH_AT) {
                if (tip.y - low.y < 6 * s + 2) fx(BigFx.STOMP, low, px(6, 2), way, 0);
                h.sound(tip, SoundEvents.TRIDENT_RIPTIDE_1.value(), 2f, 1.4f);
            }
        }
        double r = 3 * s + 1.5;
        AABB box = new AABB(tip, tipWas);
        for (int i = 0; i < now.length; i++) box = box.minmax(new AABB(now[i], was[i]));
        for (LivingEntity e : near(box.inflate(r + 2))) {
            if (struckThisMove.contains(e.getUUID()) || !swept(was, now, e, r)) continue;
            struckThisMove.add(e.getUUID());
            blow(e, 14f, h.position(), 0.6, 0.5);
            e.setDeltaMovement(e.getDeltaMovement().add(way.x * 1.8, 0, way.z * 1.8));
            sting(e);
        }
    }

    /** the strand lash's last line (null between lashes) */
    private Vec3[] lashWas;

    /** strand k from where it hangs to its end, joint by joint, as posed now */
    private Vec3[] strandLine(int k) {
        var S = rig.strands[k];
        int[] b = S.bones();
        Vec3[] out = new Vec3[b.length + 1];
        for (int i = 0; i < b.length; i++) out[i] = h.boneWorld(b[i], S.joints()[i]);
        out[b.length] = h.boneWorld(b[b.length - 1], S.bottom());
        return out;
    }

    /** did a line moving from was to now pass within r of e (its feet, middle or head) */
    private boolean swept(Vec3[] was, Vec3[] now, LivingEntity e, double r) {
        Vec3 c = at(e);
        double rr = r + e.getBbWidth() * 0.5;
        Vec3[] pts = {c, c.add(0, e.getBbHeight() * 0.5, 0), c.add(0, e.getBbHeight(), 0)};
        for (int k = 0; k <= 4; k++) {
            double f = k / 4.0;
            for (int i = 0; i + 1 < now.length; i++) {
                Vec3 a = was[i].lerp(now[i], f), b = was[i + 1].lerp(now[i + 1], f);
                for (Vec3 p : pts) if (toSegment(p, a, b) <= rr) return true;
            }
        }
        return false;
    }

    static double toSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double l2 = ab.lengthSqr();
        double u = l2 < 1e-9 ? 0 : Mth.clamp(p.subtract(a).dot(ab) / l2, 0, 1);
        return p.distanceTo(a.add(ab.scale(u)));
    }

    private void runFlash(float s) {
        if (t == 0) fx(BigFx.EMBERS, h.crownWorld(), px(10, 3), Vec3.ZERO, 18);
        if (t == Moves.FLASH_AT) {
            boolean night = h.level().getSkyDarken() > 6 || h.level().isThundering();
            double r = 50 * s + 30;
            Vec3 c = h.position().add(0, (rig.rimY + 30) * s, 0);
            for (LivingEntity e : near(new AABB(c, c).inflate(r))) {
                if (at(e).distanceTo(c) > r) continue;
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, night ? 200 : 100, 0));
                e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, night ? 160 : 60, 0));
                if (night) e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0));
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 0));
                blow(e, night ? 8f : 5f, c, 0, 0);
            }
            h.sound(c, ModSounds.FLASH, 4f, 1.3f);
            for (var S : rig.spots) fx(BigFx.FLASH, h.spotWorld(S.k()), px(12, 4), Vec3.ZERO, GLOW);
            fx(BigFx.FLASH, c, px(40, 10), Vec3.ZERO, GLOW);
        }
    }

    private void runArmStorm(float s) {
        if (t == Moves.STORM_UP - 8) h.sound(h.position().add(0, rig.rimY * s, 0), ModSounds.WARN_ARMS, 3f, 0.8f);
        // each arm hits when its end really comes down on the ground (or a moment after it should have)
        if (t == 1) stormLanded = 0;
        for (int k = 0; k < rig.arms.length; k++) {
            int hit = Moves.stormHit(k, Math.max(0, arg));
            if ((stormLanded & (1 << k)) != 0) {
                armSlap(k, 45f, landedAt[k]);
                continue;
            }
            if (t < hit - 2) continue;
            Vec3 tip = h.armTipWorld(k);
            if (tip.y - h.groundAt(tip.x, tip.z) < 6 * s + 2 || t >= hit + 14) {
                stormLanded |= 1 << k;
                landedAt[k] = t;
                armBlow(tip, 45f, 16 * s + 5);
            }
        }
    }

    /** an arm comes down: everything under its end and round it is hit and thrown */
    private void armBlow(Vec3 tip, float dmg, double r) {
        float s = s();
        double gy = h.groundAt(tip.x, tip.z);
        Vec3 c = new Vec3(tip.x, gy, tip.z);
        double topY = Math.max(tip.y, c.y) + 3 + 4 * s;
        for (LivingEntity e : near(new AABB(c.x - r, c.y - 2, c.z - r, c.x + r, topY, c.z + r))) {
            double d = Math.hypot(at(e).x - c.x, at(e).z - c.z);
            if (d > r) continue;
            blow(e, dmg * (float) (1.0 - 0.45 * d / r), c, 1.4, 0.9);
            for (int k = 0; k < 8; k++)
                if (rig.arms.length > k && h.armTipWorld(k).distanceToSqr(tip) < 1) slapped.add(e.getUUID() + "/" + k);
        }
        h.sound(c, ModSounds.SLAM, 3f, 0.6f);
        h.sound(c, SoundEvents.COPPER_BREAK, 3f, 0.4f);
        fx(heavy(px(14, 4)), c, px(14, 4));
        // copper on stone
        fx(BigFx.SPARKS, c, px(3, 1.5), Vec3.ZERO, 0);
        if (griefing()) crack(BlockPos.containing(c), (int) Math.ceil(8 * s + 1));
    }

    /**
     * A whole arm coming down: for a second after it meets the ground, everything under any part of it that's
     * down on the ground is hit (once), as it slaps down and slides back in.
     */
    private void armSlap(int arm, float dmg, int from) {
        if (t > from + 20 || arm < 0) return;
        float s = s();
        h.ensurePose();
        var A = rig.arms[arm];
        double r = 8 * s + 3;
        List<Vec3> down = new ArrayList<>();
        int[] bones = A.bones();
        for (int i = 0; i < bones.length; i++) {
            Vector3f j0 = A.joints()[i], j1 = i + 1 < bones.length ? A.joints()[i + 1] : A.tip();
            for (int k = 0; k <= 2; k++) {
                Vector3f p = new Vector3f(j0).lerp(j1, k / 2f);
                Vec3 w = h.boneWorld(bones[i], p);
                if (w.y - h.groundAt(w.x, w.z) < 10 * s + 3) down.add(w);
            }
        }
        if (down.isEmpty()) return;
        // the arm drags back in along the ground
        if ((t - from) % 4 == 0) {
            Vec3 tip = h.armTipWorld(arm);
            Vec3 in = h.position().subtract(tip).multiply(1, 0, 1);
            fx(
                    BigFx.TRAIL,
                    new Vec3(tip.x, h.groundAt(tip.x, tip.z), tip.z),
                    px(4, 1.5),
                    in.lengthSqr() > 1e-6 ? in.normalize() : Vec3.ZERO,
                    0);
        }
        for (Vec3 p : down) {
            for (LivingEntity e :
                    near(new AABB(p.x - r, p.y - 10 * s - 4, p.z - r, p.x + r, p.y + 6 * s + 3, p.z + r))) {
                String key = e.getUUID() + "/" + arm;
                if (slapped.contains(key) || Math.hypot(at(e).x - p.x, at(e).z - p.z) > r) continue;
                slapped.add(key);
                blow(e, dmg, p, 1.4, 0.9);
            }
        }
    }

    final Set<String> slapped = new HashSet<>();

    /** the sting volley: the strand ends nearest you flick stingers at you */
    private void volley() {
        float s = s();
        Vec3 aimAt = target != null && target.isAlive()
                ? (Giants.isGiant(target) ? aimAt(target) : target.position().add(0, target.getBbHeight() * 0.5, 0))
                : h.toWorld(h.aim());
        List<Integer> ends = nearestStrands(aimAt, 5);
        for (int k : ends) {
            Vec3 from = h.strandTipWorld(k).add(0, 1 + 2 * s, 0);
            Vec3 lead = target != null
                    ? target.getDeltaMovement().multiply(1, 0, 1).scale(from.distanceTo(aimAt) / 1.8)
                    : Vec3.ZERO;
            shoot(from, aimAt.add(lead), Shot.STINGER, 7f, 1f, 1.6f + 0.6f * s, 3f);
        }
        h.sound(h.position().add(0, 20 * s, 0), ModSounds.VOLLEY, 2.5f, 1f);
    }

    private void shoot(Vec3 from, Vec3 to, int kind, float dmg, float radius, float speed, float spread) {
        Shot sh = new Shot(level(), h, kind, dmg, radius);
        sh.setSize(s());
        sh.setPos(from.x, from.y, from.z);
        Vec3 d = to.subtract(from);
        sh.shoot(d.x, d.y, d.z, speed, spread);
        level().addFreshEntity(sh);
    }

    /** where the eggs go when there's nobody to aim at (set with the move) */
    @Nullable
    Vec3 eggSpot;
    /** the eggs of the last egg rain */
    public final List<Shot> lastEggs = new ArrayList<>();

    /** how many ticks the sky dive's blow takes to run out to its edge */
    static final int DIVE_RING = 12;

    static final int GLASS = 0xA8E6B0;
    boolean diveWarned;

    @Nullable
    Vec3 diveAt;

    boolean griefing() {
        return BellConfig.V.griefing && level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    /** the ground cracks under the slam: grass and dirt go to coarse dirt, plants go */
    void crack(BlockPos c, int r) {
        ServerLevel l = level();
        r = Math.min(r, 48);
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r || h.getRandom().nextInt(3) == 0) continue;
                if (!NoWait.loaded(l, c.offset(dx, 0, dz))) continue; // (never waits for land being made)
                BlockPos top = l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.offset(dx, 0, dz));
                BlockPos below = top.below();
                BlockState st = l.getBlockState(below);
                if (st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.DIRT) || st.is(Blocks.PODZOL) || st.is(Blocks.MYCELIUM))
                    l.setBlock(below, Blocks.COARSE_DIRT.defaultBlockState(), 3);
                BlockState up = l.getBlockState(top);
                if (!up.isAir() && up.canBeReplaced() && up.getFluidState().isEmpty()) l.destroyBlock(top, false);
            }
    }

    /** a big moment for everyone near to see (fx/BigFx): what kind, where, how big (blocks, for his size now) */
    /**
     * A size for the kit that shrinks with him: a * s + b at full size and over, but the b part shrinks too for a
     * small one (a 6-block jelly doesn't throw a 30-block ring).
     */
    double px(double a, double b) {
        float s = s();
        return a * s + b * Math.sqrt(Math.min(1f, s));
    }

    /** a heavy blow for a big one, a stomp for a small one */
    static int heavy(double size) {
        return size >= 12 ? BigFx.SLAM : BigFx.STOMP;
    }

    void fx(int kind, Vec3 at, double size) {
        BigFx.send(level(), kind, at, (float) size);
    }

    void fx(int kind, Vec3 at, double size, Vec3 dir, int extra) {
        BigFx.send(level(), kind, at, (float) size, dir, extra);
    }

    /** the ground (or water) right under him */
    Vec3 under() {
        return new Vec3(h.getX(), groundUnder(), h.getZ());
    }

    /** his glow, for the flashes */
    static final int GLOW = 0xBFFFD8;

    static final int POISON = 0x9CFF6A;

    /** an instant kick of the camera, for a blow the kit's own shake (SLAM, SHOCKWAVE, SHAKE) doesn't cover */
    private void thump(Vec3 c, float k) {
        if (!BellConfig.V.screenShake) return;
        for (ServerPlayer p : level().players()) {
            if (p.distanceToSqr(c) > Mth.square(200 * s() + 80)) continue;
            ServerPlayNetworking.send(p, new ThumpPayload(c.x, c.y, c.z, k * Math.min(1.5f, 1.2f * s())));
        }
    }

    // grab and harvest

    // carrying somebody up onto his crown

    public void letGo(LivingEntity e) {
        holds.letGo(e);
    }

    /** true while a strand is taking somebody up to his crown (not once they're on it) */
    public boolean carryingUp() {
        return gentle && move == Moves.GRAB && !back;
    }

    public void cancelCarry() {
        holds.cancelCarry();
    }

    static Vec3 middle(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    float carryD(LivingEntity who) {
        return CarryPath.clearance(who.getBbWidth(), who.getBbHeight(), s());
    }
    /** how high over the crown seat the middle of the one carried is once they sit on it (model blocks) */
    float carryEnd(LivingEntity who) {
        return (float) (-h.carryDrop(who) / s());
    }

    CarryPath carryPath(int k, LivingEntity who) {
        return CarryPath.get(rig, k, carryD(who));
    }

    public Vec3 fetchSpot(LivingEntity who) {
        return holds.fetchSpot(who);
    }

    // setting the rider down again

    /** true while he's setting whoever rode his crown down (sinking to the ground for it, or the strand at it) */
    public boolean settingDown() {
        return downWho != null || (gentle && down && move == Moves.GRAB);
    }

    /** the height he sinks to before the strand comes up for the rider (the ground at the spot), or null */
    public @Nullable Double setDownGround() {
        return downWho != null && move == Moves.NONE && downSpot != null ? downSpot.y : null;
    }

    public boolean startSetDown(LivingEntity who) {
        return holds.startSetDown(who);
    }

    /** the one the strand is going up to fetch down (still on his crown until it gets there) */
    @Nullable
    LivingEntity carryWho;

    public void strandHit(int strand, float amount, @Nullable Entity by) {
        holds.strandHit(strand, amount, by);
    }

    public void armHit(int arm, float amount, @Nullable Entity by) {
        holds.armHit(arm, amount, by);
    }

    // the arm wrap

    // holding things: seats follow the pose

    /** after the pose is worked out each tick: move the seats and trees to where the strands and arms are now */
    public void afterPose() {
        letGoOfTaken();
        if (gentle && move == Moves.GRAB && grabSeat != null && grabbed != null && arg >= 0) {
            // carried up onto his crown: held round the middle by the end of the strand, wherever it is now
            grabSeat.follow(Seat.CARRY, arg);
            Vec3 at = h.seatSpot(Seat.CARRY, arg, grabbed);
            grabSeat.moveTo(at.x, at.y, at.z);
            if (grabbed.getVehicle() != grabSeat) grabbed.startRiding(grabSeat, true);
            grabbed.fallDistance = 0;
        } else if (grabSeat != null || grabbedTree != null) {
            if ((move == Moves.GRAB || move == Moves.HARVEST) && t < Moves.REACH + Moves.LIFT && arg >= 0) {
                if (grabSeat != null && grabbed != null) {
                    grabSeat.follow(Seat.STRAND_TIP, arg);
                    Vec3 at = h.seatSpot(Seat.STRAND_TIP, arg, grabbed);
                    grabSeat.moveTo(at.x, at.y, at.z);
                    if (grabbed.getVehicle() != grabSeat) grabbed.startRiding(grabSeat, true);
                    grabbed.fallDistance = 0;
                }
                if (grabbedTree != null)
                    holds.placeTree(grabbedTree, h.strandTipWorld(arg).add(0, -1, 0));
            }
        }
        if (wrapSeat != null && wrapped != null && arg >= 0) {
            wrapSeat.follow(Seat.ARM_TIP, arg);
            Vec3 at = h.seatSpot(Seat.ARM_TIP, arg, wrapped);
            wrapSeat.moveTo(at.x, at.y, at.z);
            if (wrapped.getVehicle() != wrapSeat) wrapped.startRiding(wrapSeat, true);
            wrapped.fallDistance = 0;
        }
        for (Inside in : inside) {
            int mode = in.pod >= 0 ? Seat.POD : Seat.INSIDE, index = in.pod >= 0 ? in.pod : in.slot;
            Vec3 at = h.seatSpot(mode, index, in.e);
            if (in.seat != null && in.e != null) {
                in.seat.follow(mode, index);
                in.seat.moveTo(at.x, at.y, at.z);
                if (in.e.getVehicle() != in.seat) in.e.startRiding(in.seat, true);
                in.e.fallDistance = 0;
            }
            if (in.tree != null) holds.placeTree(in.tree, at);
        }
    }

    /**
     * Whatever another giant has taken off him, or that went to another world, is let go: pulling it back every tick
     * would fight the other giant for it for ever.
     */
    private void letGoOfTaken() {
        List<LivingEntity> taken = new ArrayList<>();
        if (grabbed != null && takenAway(grabbed, grabSeat)) taken.add(grabbed);
        if (wrapped != null && takenAway(wrapped, wrapSeat)) taken.add(wrapped);
        for (Inside in : inside) if (in.e != null && takenAway(in.e, in.seat)) taken.add(in.e);
        for (LivingEntity e : taken) holds.letGo(e);
    }

    private boolean takenAway(LivingEntity e, @Nullable Seat mine) {
        if (mine == null || e.getVehicle() == mine) return false;
        return e.level() != h.level() || Giants.onGiantSeat(e, HollowbellMod.MOD_ID);
    }

    // inside the dome

    static final int SLOTS = HollowbellEntity.SLOTS;

    int slotCount() {
        return SLOTS;
    }

    Vec3 slotWorld(int i, @Nullable LivingEntity who) {
        return h.slotWorld(i);
    }

    public void hurtFromInside(float dealt) {
        holds.hurtFromInside(dealt);
    }

    public void letGoOfEverything(boolean dropTrees) {
        holds.letGoOfEverything(dropTrees);
    }

    // trees

    static final String TREE_TAG = "hollowbell_tree";
    /** the blocks of the trees held right now; one loaded from a save has nobody holding it any more */
    static final Set<Display.BlockDisplay> LIVE_TREES = Collections.newSetFromMap(new WeakHashMap<>());

    public static void loadedTree(Entity e, ServerLevel l) {
        BellHolds.loadedTree(e, l);
    }

    /** his strand ends flatten plants as he drags them along */
    public void flattenUnderStrands() {
        if (!griefing() || h.isDeadOrDying()) return;
        ServerLevel l = level();
        for (int i = 0; i < 6; i++) {
            int k = h.getRandom().nextInt(rig.strands.length);
            Vec3 tip = h.strandTipWorld(k);
            BlockPos p = BlockPos.containing(tip);
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos q = p.above(dy);
                if (!NoWait.loaded(l, q)) continue;
                BlockState st = l.getBlockState(q);
                if (!st.isAir() && st.canBeReplaced() && st.getFluidState().isEmpty()) l.destroyBlock(q, false);
            }
        }
    }

    // the sting and the shed

    public void sting(LivingEntity e) {
        sting(e, true);
    }

    /** knock false: stung where it stands, not knocked off (it stands on him: a knock throws it off his pod) */
    public void sting(LivingEntity e, boolean knock) {
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        if (!h.fightingGiant(e)) return; // a giant he isn't fighting is left alone
        long now = h.level().getGameTime();
        Integer last = stingAt.get(e.getUUID());
        if (last != null && now - last < 20) return;
        stingAt.put(e.getUUID(), (int) now);
        if (stingAt.size() > 200) stingAt.clear();
        e.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        Vec3 v = e.getDeltaMovement();
        boolean marked = e.hurtMarked;
        e.hurt(h.damageSources().mobAttack(h), h.dmg(3f, e));
        if (!knock) {
            // (a hit also sends a player the speed the server has for them, which isn't how they really move)
            e.setDeltaMovement(v);
            e.hurtMarked = marked;
        }
        h.sound(e.position(), ModSounds.STING, 1f, 1f);
        // the poison going in
        fx(BigFx.SPARKS, e.position().add(0, e.getBbHeight() * 0.6, 0), 1.2, Vec3.ZERO, POISON);
    }

    private void shed() {
        int want = Math.max(0, BellConfig.V.shedCount);
        List<Integer> left = new ArrayList<>();
        for (int i = 0; i < rig.eggs.length; i++) if (!h.state.eggGone[i]) left.add(i);
        Collections.shuffle(left, new Random(h.getRandom().nextLong()));
        int n = 0;
        for (int i : left) {
            if (n >= want) break;
            Vec3 at = h.eggWorld(i);
            h.eggGone(i);
            Belling b = new Belling(ModEntities.BELLING, level());
            b.setPos(at.x, at.y, at.z);
            b.setOwner(h);
            if (target != null) b.setTarget(target);
            level().addFreshEntity(b);
            // the shell breaking off the strand
            fx(BigFx.DEBRIS, at, px(4, 1.2), new Vec3(0, -1, 0), Block.getId(Blocks.BONE_BLOCK.defaultBlockState()));
            fx(BigFx.SPARKS, at, px(3, 1), Vec3.ZERO, POISON);
            n++;
        }
        if (n > 0) h.sound(h.position().add(0, 50 * s(), 0), ModSounds.EGG_BURST, 3f, 0.8f);
    }

    // helpers

    /**
     * What's in the box. The other giants are mostly made of part boxes that aren't creatures themselves: any of those
     * in the box counts as that giant, and {@link #at} then gives where the nearest bit of it is (a giant is struck
     * where its body is, not at its middle). Nothing of his digs, so a part more than a block down under the ground
     * is out of his reach.
     */
    List<LivingEntity> near(AABB box) {
        List<LivingEntity> out = level().getEntitiesOfClass(
                        LivingEntity.class, box, e -> h.fairGame(e) || (e == grabbed && !gentle) || e == wrapped);
        Vec3 mid = box.getCenter();
        for (Entity p : level().getEntities(
                        (Entity) null,
                        box,
                        x -> !(x instanceof LivingEntity) && x.getTags().contains(Giants.TAG))) {
            LivingEntity o = Giants.ownerOf(p);
            if (o == null || o == h || !h.fairGame(o)) continue;
            AABB pb = p.getBoundingBox();
            if (pb.maxY < h.groundAt(p.getX(), p.getZ()) - 1) continue;
            Vec3 q = new Vec3(
                    Mth.clamp(mid.x, pb.minX, pb.maxX),
                    Mth.clamp(mid.y, pb.minY, pb.maxY),
                    Mth.clamp(mid.z, pb.minZ, pb.maxZ));
            if (q.distanceToSqr(mid) < at(o).distanceToSqr(mid)) giantAt.put(o, q);
            if (!out.contains(o)) out.add(o);
        }
        // a giant made of one creature with a small box (or any that shows its body, see GiantHull): hit where its body
        // is
        for (LivingEntity o : h.giantsNear()) {
            if (o.isRemoved() || o == h || out.contains(o) || !h.fairGame(o)) continue;
            double[] th = GiantHull.of(o, Giants.TAG);
            if (th.length == 0) continue;
            Vec3 q = GiantHull.nearest(th, mid);
            if (q == null || !box.inflate(0.5).contains(q)) continue;
            giantAt.put(o, q);
            out.add(o);
        }
        return out;
    }

    /**
     * Where a blow at it is aimed: its own spot, or for another giant the nearest point of its body (its hull, see
     * GiantHull) to the middle of his bell, so he strikes its side and not its middle.
     */
    Vec3 aimAt(LivingEntity e) {
        if (!Giants.isGiant(e)) return e.position();
        LivingEntity o = Giants.ownerOf(e);
        if (o == null) o = e;
        double[] th = GiantHull.of(o, Giants.TAG);
        Vec3 from = h.toWorld(new Vector3f(0, rig.rimY - 20, 0));
        Vec3 p = th.length == 0 ? null : GiantHull.nearest(th, from);
        return p != null ? p : e.position();
    }

    /**
     * where the nearest bit of it is: its own spot, or for another giant, the nearest of its part boxes found this tick
     */
    final Map<LivingEntity, Vec3> giantAt = new HashMap<>();

    Vec3 at(LivingEntity e) {
        Vec3 p = giantAt.get(e);
        return p != null ? p : e.position();
    }

    /** the strand to lash at p with: one hanging near it, long enough to reach down to it */
    private int lashStrand(Vec3 p) {
        Vector3f to = h.toModel(p);
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (var S : rig.strands) {
            if (S.hidden()) continue;
            Vector3f top = S.joints()[0];
            double across = Math.hypot(top.x - to.x, top.z - to.z);
            double need = Math.hypot(across, Math.max(0, top.y - BellAnim.LASH_DIP - to.y));
            double d = across + Math.max(0, need - rig.length(rig.strandChain[S.k()])) * 4;
            if (d < bd) {
                bd = d;
                best = S.k();
            }
        }
        return best;
    }

    private int nearestStrand(Vec3 p) {
        h.ensurePose();
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (var S : rig.strands) {
            if (S.hidden() || (S.k() == arg && move != Moves.NONE)) continue;
            Vec3 tip = h.strandTipWorld(S.k());
            double d = Math.pow(tip.x - p.x, 2) + Math.pow(tip.z - p.z, 2);
            // the long ones, so it can reach down
            d += Math.max(0, (S.low() - 20)) * s() * 20;
            if (d < bd) {
                bd = d;
                best = S.k();
            }
        }
        return best;
    }

    /** the n strands whose ends are nearest a point */
    private List<Integer> nearestStrands(Vec3 p, int n) {
        h.ensurePose();
        List<double[]> all = new ArrayList<>();
        for (var S : rig.strands) {
            if (S.hidden()) continue;
            Vec3 tip = h.strandTipWorld(S.k());
            all.add(new double[] {tip.distanceToSqr(p), S.k()});
        }
        all.sort((a, b) -> Double.compare(a[0], b[0]));
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < Math.min(n, all.size()); i++) out.add((int) all.get(i)[1]);
        return out;
    }

    private int nearestArm(Vec3 p) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (var A : rig.arms) {
            Vec3 tip = h.toWorld(A.tip());
            Vec3 mid = h.toWorld(A.centre());
            double d = Math.min(tip.distanceToSqr(p.x, tip.y, p.z), mid.distanceToSqr(p.x, mid.y, p.z));
            if (d < bd) {
                bd = d;
                best = A.k();
            }
        }
        return best;
    }
}
