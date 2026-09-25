package net.jj.hollowbell.entity;

import com.mojang.math.Transformation;
import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.ModSounds;
import net.jj.hollowbell.rig.BellRig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What each of his moves does to the world, the things he is holding, and the inside of his dome. Server only.
 *
 * His moves come in three strengths (see {@link Moves#tier}). Light ones are quick, cheap and used often. Medium
 * ones give a clear warning, then really hurt. Heavy ones are rare: a long wind-up you can see and hear coming,
 * then something that clears a wide area; afterwards he's worn out and slow for a few seconds. How hard each one
 * hits is set at full size and scaled by his size and the damage setting in {@link HollowbellEntity#dmg}.
 */
public final class BellMoves {
    private final HollowbellEntity h;
    private final BellRig rig;

    private int move, t, arg = -1, cooldown = 60, harvestIn = 400;
    private @Nullable LivingEntity target;
    /** the strand grabbing: who, on which seat, how far up, how many hits it has taken */
    private @Nullable LivingEntity grabbed;
    private @Nullable Tree grabbedTree;
    private @Nullable Seat grabSeat;
    private float lift;
    private float strandHits;
    private boolean gentle;
    private Vec3 intoFrom = Vec3.ZERO;
    /** the arm wrap */
    private @Nullable LivingEntity wrapped;
    private @Nullable Seat wrapSeat;
    private float armHits;
    /** whatever is inside the dome */
    private final List<Inside> inside = new ArrayList<>();
    private float hurtInside;
    private final Map<UUID, Integer> stingAt = new HashMap<>();
    private final Set<UUID> struckThisMove = new HashSet<>();
    /** per creature, the tick it was last hurt by the move of the moment (for moves that hurt over and over) */
    private final Map<UUID, Integer> hitAt = new HashMap<>();
    private @Nullable BlockPos treeAt;
    /** the slam's arm has come down (it only hits once); which of the storm's arms have */
    private boolean landed;
    private int stormLanded;
    private final int[] landedAt = new int[8];
    /** worn out after a heavy move: ticks left */
    private int tired;
    /** gametime a heavy move is his to use again, and each move's last use */
    private long heavyReadyAt = 300;
    private int lastMove;
    /** the spore clouds drifting after what he's hunting */
    private final List<AreaEffectCloud> clouds = new ArrayList<>();
    /** the sky dive: how high he goes, how far over he has turned, where he'll come down */
    private double diveTop;
    private float diveOver;
    /** the sun lances: where each beam touches the ground */
    private final Vec3[] lance = new Vec3[5];

    private static final class Inside {
        @Nullable LivingEntity e;
        @Nullable Tree tree;
        @Nullable Seat seat;
        int slot, age;
    }

    /** a tree he pulled up, as block displays hanging together */
    static final class Tree {
        final List<Display.BlockDisplay> blocks = new ArrayList<>();
        final List<Vector3f> offsets = new ArrayList<>();
        final List<BlockState> states = new ArrayList<>();
        float size = 1f;
    }

    BellMoves(HollowbellEntity h) { this.h = h; this.rig = h.rig; }

    public int move() { return move; }
    public int t() { return t; }
    public void setCooldown(int c) { cooldown = c; }
    public float lift() { return lift; }
    public @Nullable LivingEntity grabbed() { return grabbed; }
    public @Nullable LivingEntity wrapped() { return wrapped; }
    public int insideCount() { return inside.size(); }
    public List<LivingEntity> insideNow() { List<LivingEntity> l = new ArrayList<>(); for (Inside i : inside) if (i.e != null) l.add(i.e); return l; }
    public int treesInside() { int n = 0; for (Inside i : inside) if (i.tree != null) n++; return n; }
    public boolean isTired() { return tired > 0; }
    /** for the tests and the book: the heavy moves are his to use now */
    public void restNow() { tired = 0; h.setTired(false); heavyReadyAt = 0; cooldown = 0; }

    /** he stays where he is for this (he still rises and sinks) */
    public boolean holdsStill() {
        return switch (move) {
            case Moves.GRAB, Moves.HARVEST -> t <= Moves.REACH;
            case Moves.DROP, Moves.CURTAIN, Moves.WRAP, Moves.WHIRLPOOL, Moves.DEEP_TOLL, Moves.ARM_STORM, Moves.UNDERTOW, Moves.SUN_LANCES,
                 Moves.STINGER_STORM, Moves.FLASH, Moves.POD_BURST -> true;
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
                // the strand ends down at what it's reaching for
                if (t <= Moves.REACH + 4) {
                    if (target != null) return tg - 1.5 * s;
                    if (treeAt != null) return (double) treeAt.getY();
                }
            }
            // the strands and arms hit on the ground: down he comes to it
            case Moves.CURTAIN, Moves.SLAM, Moves.WRAP, Moves.SWEEP, Moves.LASH -> { if (target != null) return tg - 2 * s; }
            case Moves.WHIRLPOOL, Moves.UNDERTOW, Moves.ARM_STORM -> { return (target != null ? Math.min(tg, groundUnder()) : groundUnder()) - 3 * s; }
            case Moves.POD_BURST -> { if (target != null) return tg - 20 * s; }
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
        if (move != Moves.SKY_DIVE || t < Moves.DIVE_CLIMB + Moves.DIVE_TURN || t >= Moves.DIVE_CLIMB + Moves.DIVE_TURN + Moves.DIVE_FALL) return null;
        float s = s();
        Vec3 across = Vec3.ZERO;
        Vector3f a = h.aim();
        Vec3 aimAt = h.toWorld(a);
        Vec3 d = new Vec3(aimAt.x - h.getX(), 0, aimAt.z - h.getZ());
        if (d.length() > 1) across = d.normalize().scale(Math.min(d.length() * 0.05, 0.4 + 0.6 * s));
        return new Vec3(across.x, -(0.9 + 1.6 * s), across.z);
    }

    private double groundUnder() { return h.groundAt(h.getX(), h.getZ()); }

    public boolean caught(@Nullable Entity e) {
        if (e == null) return false;
        if (e == grabbed || e == wrapped) return true;
        for (Inside i : inside) if (i.e == e) return true;
        return false;
    }

    public boolean isInside(Entity e) { for (Inside i : inside) if (i.e == e) return true; return false; }

    public boolean usesSeat(Seat s) {
        if (s == grabSeat || s == wrapSeat) return true;
        for (Inside i : inside) if (i.seat == s) return true;
        return false;
    }

    private ServerLevel level() { return (ServerLevel) h.level(); }
    private float s() { return h.bellScale(); }

    // ------------------------------------------------------------------ starting and ending

    /** makes him do a move now. False if he can't (down, nothing to do it to). Cuts off whatever he was doing. */
    public boolean force(int which, @Nullable LivingEntity at) {
        if (h.isDeadOrDying() || which <= 0 || which >= Moves.NAMES.length) return false;
        if (move != Moves.NONE) end();
        return start(which, at);
    }

    private boolean start(int which, @Nullable LivingEntity at) {
        if (h.sunk() && (which == Moves.DROP || which == Moves.GRAB || which == Moves.HARVEST || which == Moves.SKY_DIVE)) return false;
        target = at;
        struckThisMove.clear();
        hitAt.clear();
        slapped.clear();
        Vector3f aim = at != null ? h.toModel(at.position()) : new Vector3f(0, 0, 40);
        int a = -1;
        float s = s();
        switch (which) {
            case Moves.GRAB -> {
                if (at == null || grabbed != null || grabbedTree != null) return false;
                a = nearestStrand(at.position());
                if (a < 0) return false;
                grabbed = null; lift = 0f; strandHits = 0f; gentle = false;
            }
            case Moves.HARVEST -> {
                if (grabbed != null || grabbedTree != null) return false;
                LivingEntity mob = at != null && !(at instanceof Player) ? at : findHarvestMob();
                if (mob != null) { target = mob; aim = h.toModel(mob.position()); a = nearestStrand(mob.position()); }
                else {
                    BlockPos tree = findTree();
                    if (tree == null) return false;
                    aim = h.toModel(Vec3.atBottomCenterOf(tree));
                    a = nearestStrand(Vec3.atBottomCenterOf(tree));
                    treeAt = tree;
                    target = null;
                }
                if (a < 0) return false;
                lift = 0f; strandHits = 0f; gentle = false;
            }
            case Moves.SLAM, Moves.WRAP -> {
                if (which == Moves.WRAP && (at == null || wrapped != null)) return false;
                a = at != null ? nearestArm(at.position()) : h.getRandom().nextInt(rig.arms.length);
                armHits = 0f;
            }
            case Moves.ARM_STORM -> a = at != null ? nearestArm(at.position()) : h.getRandom().nextInt(rig.arms.length);
            case Moves.LASH -> {
                a = nearestStrand(at != null ? at.position() : h.position().add(20 * s, 0, 0));
                if (a < 0) return false;
            }
            case Moves.SHED -> { if (h.eggsLeft() == 0 || HollowbellConfig.V.shedCount <= 0) return false; }
            case Moves.EGG_RAIN -> { if (h.eggsLeft() == 0) return false; }
            case Moves.POD_BURST -> { if (h.podsLeft() == 0) return false; }
            case Moves.SWEEP -> { if (at == null) aim = new Vector3f(1, 0, 0); }
            case Moves.SKY_DIVE -> {
                double top = Math.max(h.getY(), at != null ? at.getY() : h.getY()) + 70 * s + 30;
                diveTop = Math.min(top, h.level().getMaxBuildHeight() - (rig.crownY + 20) * s);
                diveOver = 0f;
            }
            case Moves.SUN_LANCES -> { for (int i = 0; i < 5; i++) lance[i] = null; }
            default -> {}
        }
        move = which; t = 0; arg = a; landed = false;
        lastMove = which;
        h.setMove(which, a, aim);
        if (which == Moves.SKY_DIVE) h.setLift(0f);
        warn(which, a);
        return true;
    }

    /** what you see and hear as a move begins: the tell, so you can get out of the way */
    private void warn(int which, int a) {
        float s = s();
        Vec3 mid = h.position().add(0, (rig.rimY + 20) * s, 0);
        switch (which) {
            case Moves.GRAB, Moves.HARVEST -> h.sound(h.strandTipWorld(a), ModSounds.STRAND, 1.5f, 0.8f);
            case Moves.SLAM -> h.sound(h.armTipWorld(a), SoundEvents.RAVAGER_ROAR, 2.5f, 0.5f);
            case Moves.CURTAIN -> h.sound(h.position().add(0, 20 * s, 0), ModSounds.STRAND, 3f, 0.6f);
            case Moves.SWEEP -> h.sound(h.position().add(0, 30 * s, 0), ModSounds.STRAND, 3f, 0.5f);
            case Moves.WRAP -> h.sound(h.armTipWorld(a), SoundEvents.COPPER_PLACE, 2.5f, 0.5f);
            case Moves.PULSE -> h.sound(mid, ModSounds.WARN_DROP, 2f, 1.2f);
            case Moves.SHED, Moves.SPORES, Moves.EGG_RAIN -> h.sound(h.position().add(0, 60 * s, 0), ModSounds.CLICK, 3f, 0.6f);
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

    private void heavyWarning(net.minecraft.sounds.SoundEvent ev, String name) {
        float s = s();
        h.sound(h.position().add(0, (rig.rimY + 20) * s, 0), ev, 4f, 1f);
        for (ServerPlayer p : level().players())
            if (p.distanceToSqr(h) < Mth.square(200 * s + 80)) p.displayClientMessage(Component.translatable("warn.hollowbell." + name).withStyle(net.minecraft.ChatFormatting.RED), true);
    }

    /** ends whatever move he's in the middle of, straight away (he eases back to rest by himself) */
    public void stopNow() { if (move != Moves.NONE) end(); }

    private void end() {
        int was = move;
        if (move == Moves.GRAB || move == Moves.HARVEST) letGoOfGrab(false);
        if (move == Moves.WRAP) letGoOfWrap();
        move = Moves.NONE; t = 0; arg = -1; target = null;
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
        insideTick();
        cloudsTick();
        if (tired > 0 && --tired == 0) h.setTired(false);
        if (h.isDeadOrDying()) return;
        if (move != Moves.NONE) {
            t++;
            run();
            if (move != Moves.NONE && t >= Moves.length(move)) end();
            return;
        }
        Player fetch = h.fetchingNow();
        if (fetch != null && h.horiz(fetch.position()) < h.bellRadius() * 0.8 + 2 && !h.resting()) {
            if (start(Moves.GRAB, fetch)) gentle = true;
            return;
        }
        if (cooldown > 0) { cooldown--; return; }
        if (tired > 0) return;
        if (h.carrying()) return;              // whoever is on his crown picks the moves
        choose();
    }

    /** picks a move for what he's after: what's in reach, what's up high, how many there are, whether a heavy one is his */
    private void choose() {
        LivingEntity tg = h.getTarget();
        if (tg == null) {
            if (HollowbellConfig.V.harvest && --harvestIn <= 0) {
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
        boolean heavy = h.level().getGameTime() >= heavyReadyAt && !h.sunk();
        boolean night = h.level().getSkyDarken() > 6;
        List<int[]> opts = new ArrayList<>();
        // light
        if (d < bell * 0.85 && !h.sunk()) opts.add(new int[]{Moves.GRAB, 4});
        if (d < bell * 3) opts.add(new int[]{Moves.VOLLEY, flying ? 5 : 3});
        if (d < bell * 1.3) opts.add(new int[]{Moves.LASH, 4});
        if (d < 50 * s + 30) opts.add(new int[]{Moves.FLASH, night ? 3 : 1});
        // medium
        if (flying && d < bell * 2.5) opts.add(new int[]{Moves.PULSE, 6});
        if (d < bell * 0.85) opts.add(new int[]{Moves.CURTAIN, 2});
        if (d < bell * 1.4) opts.add(new int[]{Moves.SWEEP, 2});
        if (d < bell * 1.7) opts.add(new int[]{Moves.SLAM, 3});
        if (d < bell * 1.5) opts.add(new int[]{Moves.WRAP, 2});
        if (d < bell * 1.6) opts.add(new int[]{Moves.PULSE, 1});
        if (d < bell * 2) opts.add(new int[]{Moves.SPORES, 2});
        if (d < bell * 0.8 && h.podsLeft() > 0) opts.add(new int[]{Moves.POD_BURST, 2});
        if (d < bell * 2 && h.eggsLeft() > 0) opts.add(new int[]{Moves.EGG_RAIN, 2});
        if (d < h.senseRange() && h.eggsLeft() > 0 && HollowbellConfig.V.shedCount > 0 && bellingsNear() < 8) opts.add(new int[]{Moves.SHED, 1});
        // heavy: rare, and more likely the more there are to hit
        if (heavy) {
            int w = crowd >= 3 ? 4 : 2;
            if (d < bell * 0.8) opts.add(new int[]{Moves.DROP, w});
            if (d < bell * 1.8) opts.add(new int[]{Moves.WHIRLPOOL, w});
            if (d < bell * 4) opts.add(new int[]{Moves.SKY_DIVE, flying ? 1 : w});
            if (d < bell * 2.5) opts.add(new int[]{Moves.DEEP_TOLL, flying ? w + 2 : w});
            if (d < bell * 1.8) opts.add(new int[]{Moves.ARM_STORM, w});
            if (d < bell * 3) opts.add(new int[]{Moves.STINGER_STORM, flying ? w + 1 : w});
            if (d < bell * 3) opts.add(new int[]{Moves.SUN_LANCES, w});
            if (d < bell * 2) opts.add(new int[]{Moves.UNDERTOW, w});
        }
        int total = 0;
        for (int[] o : opts) { if (o[0] == lastMove) o[1] = Math.max(1, o[1] / 2); total += o[1]; }
        if (total == 0) return;
        int r = h.getRandom().nextInt(total);
        for (int[] o : opts) { r -= o[1]; if (r < 0) { if (!start(o[0], tg)) cooldown = 10; return; } }
    }

    private int bellingsNear() {
        return level().getEntitiesOfClass(Belling.class, h.bodyBox().inflate(40)).size();
    }

    // ------------------------------------------------------------------ hitting

    /**
     * A blow: it lands even straight after another (a creature's moment of safety after a hit is taken away first),
     * and throws what it hits away from from, out and up.
     */
    private void blow(LivingEntity e, float base, Vec3 from, double out, double up) {
        e.invulnerableTime = 0;
        e.hurt(h.damageSources().mobAttack(h), h.dmg(base, e));
        Vec3 d = new Vec3(e.getX() - from.x, 0, e.getZ() - from.z);
        d = d.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : d.normalize();
        double k = 1.0 - Mth.clamp(e.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE), 0, 1) * 0.6;
        e.setDeltaMovement(e.getDeltaMovement().add(d.x * out * k, up * k, d.z * out * k));
        e.hurtMarked = true;
    }

    /** hurts e at most once every gap ticks during a move */
    private boolean every(LivingEntity e, int gap) {
        Integer last = hitAt.get(e.getUUID());
        if (last != null && t - last < gap) return false;
        hitAt.put(e.getUUID(), t);
        return true;
    }

    /** it knocks flying players out of the air */
    private void knockDown(LivingEntity e) {
        if (e instanceof ServerPlayer p && (p.isFallFlying() || p.getAbilities().flying)) {
            p.stopFallFlying();
            if (!p.isCreative() && p.getAbilities().flying) { p.getAbilities().flying = false; p.onUpdateAbilities(); }
            p.setDeltaMovement(p.getDeltaMovement().x, -1.6, p.getDeltaMovement().z);
            p.displayClientMessage(Component.translatable("message.hollowbell.knocked_down"), true);
        }
    }

    // ------------------------------------------------------------------ each move, tick by tick

    private void run() {
        float s = s();
        double bell = h.bellRadius();
        switch (move) {
            case Moves.GRAB, Moves.HARVEST -> runGrab();
            case Moves.CURTAIN -> {
                // it follows you while it closes, then it has you
                if (target != null && t < Moves.CURTAIN_CLOSE && target.isAlive()) h.setAim(h.toModel(target.position()));
                if (t >= Moves.CURTAIN_CLOSE - 6 && t < Moves.CURTAIN_CLOSE + Moves.CURTAIN_HOLD && t % 5 == 0) {
                    Vec3 c = h.toWorld(h.aim());
                    double r = 16 * s + 4;
                    for (LivingEntity e : near(new AABB(c, c).inflate(r, 10 + 30 * s, r))) {
                        Vec3 in = new Vec3(c.x - e.getX(), 0, c.z - e.getZ());
                        double dist = in.length();
                        if (dist > r) continue;
                        if (dist > 1.5) e.setDeltaMovement(e.getDeltaMovement().scale(0.4).add(in.normalize().scale(0.35)));
                        e.hurtMarked = true;
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
                        if (every(e, 20)) { blow(e, 7f, c, 0, 0); sting(e); }
                    }
                    if (t % 15 == 0) h.sound(c, ModSounds.GRAB, 2f, 0.6f);
                }
            }
            case Moves.SWEEP -> {
                if (t >= 20 && t <= 48 && t % 2 == 0) {
                    Vector3f a = h.aim();
                    Vec3 dir = h.toWorld(new Vector3f(a.x, 0, a.z).normalize().mul(10)).subtract(h.position()).normalize();
                    double r = bell * 1.35 + 4;
                    for (LivingEntity e : near(h.bodyBox().setMaxY(h.getY() + rig.rimY * s * 0.8))) {
                        if (struckThisMove.contains(e.getUUID()) || h.horiz(e.position()) > r) continue;
                        struckThisMove.add(e.getUUID());
                        blow(e, 18f, h.position(), 0, 0.6);
                        e.setDeltaMovement(e.getDeltaMovement().add(dir.x * 2.0, 0, dir.z * 2.0));
                        sting(e);
                    }
                    if (t == 30) h.sound(h.position().add(0, 30 * s, 0), ModSounds.WHIP, 3f, 0.4f);
                }
            }
            case Moves.SLAM -> {
                int hit = Math.round(BellRig.SLAM_HIT * Moves.length(Moves.SLAM));
                // it follows you while the arm is up, then it's coming down where you were
                if (target != null && target.isAlive() && t < hit - 10) h.setAim(h.toModel(target.position()));
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
            case Moves.WRAP -> runWrap();
            case Moves.PULSE -> {
                if (t == Moves.PULSE_AT) {
                    h.pulse(1.6f);
                    h.sound(h.position().add(0, rig.rimY * s, 0), ModSounds.SHOCK, 3f, 0.8f);
                    thump(h.position(), 0.6f);
                }
                if (t > Moves.PULSE_AT && t <= Moves.PULSE_AT + 24) {
                    // the shock goes out from the rim in a ring
                    double r = bell + (t - Moves.PULSE_AT) / 24.0 * (70 * s + 30);
                    Vec3 c = h.position().add(0, rig.rimY * s, 0);
                    for (LivingEntity e : near(h.bodyBox().inflate(80 * s + 34, 90 * s + 40, 80 * s + 34))) {
                        if (struckThisMove.contains(e.getUUID())) continue;
                        double d = e.position().distanceTo(c);
                        if (d > r) continue;
                        struckThisMove.add(e.getUUID());
                        blow(e, 18f, c, 2.0, 0.6);
                        e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 140, 0));
                        knockDown(e);
                    }
                    if (t % 3 == 0) ring(c.add(0, -10 * s, 0), r);
                }
            }
            case Moves.DROP -> {
                int hit = Moves.DROP_WIND + Moves.DROP_FALL;
                if (t == hit) {
                    Vec3 c = h.position();
                    double r = bell * 1.2 + 6;
                    for (LivingEntity e : near(h.bodyBox().inflate(r - bell + 4, 0, r - bell + 4).setMaxY(h.getY() + 50 * s + 8))) {
                        double d = h.horiz(e.position());
                        if (d > r) continue;
                        blow(e, 52f * (float) (1 - 0.35 * d / r), c, 2.4, 0.9);
                        knockDown(e);
                    }
                    h.sound(c, ModSounds.SLAM, 4f, 0.5f);
                    h.sound(c, ModSounds.SPLASH, 3f, 0.6f);
                    h.particles(ParticleTypes.CLOUD, c.add(0, 1, 0), 200, bell, 0.15);
                    ring(new Vec3(c.x, groundUnder() + 1, c.z), bell * 1.1);
                    thump(c, 1f);
                    if (griefing()) crack(BlockPos.containing(c.x, groundUnder(), c.z), (int) Math.ceil(bell * 0.8));
                }
                if (t == hit + Moves.DROP_DOWN) h.sound(h.position(), ModSounds.PULSE, 3f, 0.7f);
            }
            case Moves.SHED -> { if (t == Moves.SHED_AT) shed(); }
            case Moves.VOLLEY -> {
                if (t == Moves.VOLLEY_AT || t == Moves.VOLLEY_AT + 8 || t == Moves.VOLLEY_AT + 16) volley();
            }
            case Moves.LASH -> {
                if (t == Moves.LASH_AT - 3) h.sound(h.strandTipWorld(arg), ModSounds.WHIP, 2.5f, 0.6f);
                if (t == Moves.LASH_AT || t == Moves.LASH_AT + 3) {
                    Vector3f a = h.aim();
                    double dirA = Math.atan2(a.z, a.x);
                    Vec3 dir = h.toWorld(new Vector3f((float) Math.cos(dirA), 0, (float) Math.sin(dirA)).mul(10)).subtract(h.position()).normalize();
                    double r = bell * 1.35 + 3;
                    for (LivingEntity e : near(h.bodyBox().setMaxY(h.getY() + rig.rimY * s * 0.9))) {
                        if (struckThisMove.contains(e.getUUID())) continue;
                        Vec3 to = e.position().subtract(h.position()).multiply(1, 0, 1);
                        double d = to.length();
                        if (d > r) continue;
                        // in the half of him the strand whips through
                        if (d > 2 * s + 1 && to.normalize().dot(dir) < 0.3) continue;
                        struckThisMove.add(e.getUUID());
                        blow(e, 14f, h.position(), 0.6, 0.5);
                        e.setDeltaMovement(e.getDeltaMovement().add(dir.x * 1.8, 0, dir.z * 1.8));
                        sting(e);
                    }
                }
            }
            case Moves.FLASH -> {
                if (t == Moves.FLASH_AT) {
                    boolean night = h.level().getSkyDarken() > 6 || h.level().isThundering();
                    double r = 50 * s + 30;
                    Vec3 c = h.position().add(0, (rig.rimY + 30) * s, 0);
                    for (LivingEntity e : near(new AABB(c, c).inflate(r))) {
                        if (e.position().distanceTo(c) > r) continue;
                        e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, night ? 200 : 100, 0));
                        e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, night ? 160 : 60, 0));
                        if (night) e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0));
                        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 0));
                        blow(e, night ? 8f : 5f, c, 0, 0);
                    }
                    h.sound(c, ModSounds.FLASH, 4f, 1.3f);
                    for (var S : rig.spots) h.particles(ParticleTypes.FLASH, h.spotWorld(S.k()), 1, 0, 0);
                    h.particles(ParticleTypes.END_ROD, c, 200, 40 * s + 4, 0.4);
                }
            }
            case Moves.SPORES -> { if (t == Moves.SPORES_AT) spores(); }
            case Moves.POD_BURST -> podBurst();
            case Moves.EGG_RAIN -> { if (t >= Moves.EGG_AT && t <= Moves.EGG_AT + 24 && (t - Moves.EGG_AT) % 4 == 0) dropEgg(); }
            case Moves.WHIRLPOOL -> whirlpool();
            case Moves.SKY_DIVE -> skyDive();
            case Moves.DEEP_TOLL -> deepToll();
            case Moves.ARM_STORM -> {
                if (t == Moves.STORM_UP - 8) h.sound(h.position().add(0, rig.rimY * s, 0), ModSounds.WARN_ARMS, 3f, 0.8f);
                // each arm hits when its end really comes down on the ground (or a moment after it should have)
                if (t == 1) stormLanded = 0;
                for (int k = 0; k < rig.arms.length; k++) {
                    int hit = Moves.stormHit(k, Math.max(0, arg));
                    if ((stormLanded & (1 << k)) != 0) { armSlap(k, 45f, landedAt[k]); continue; }
                    if (t < hit - 2) continue;
                    Vec3 tip = h.armTipWorld(k);
                    if (tip.y - h.groundAt(tip.x, tip.z) < 6 * s + 2 || t >= hit + 14) {
                        stormLanded |= 1 << k;
                        landedAt[k] = t;
                        armBlow(tip, 45f, 16 * s + 5);
                    }
                }
            }
            case Moves.STINGER_STORM -> {
                if (t == Moves.RAIN_FROM - 10) h.sound(h.position().add(0, 30 * s, 0), ModSounds.CLICK, 4f, 0.8f);
                if (t >= Moves.RAIN_FROM && t < Moves.RAIN_TO && t % 2 == 0) stingerRain();
            }
            case Moves.SUN_LANCES -> sunLances();
            case Moves.UNDERTOW -> undertow();
            default -> {}
        }
    }

    /** an arm comes down: everything under its end and round it is hit and thrown */
    private void armBlow(Vec3 tip, float dmg, double r) {
        float s = s();
        double gy = h.groundAt(tip.x, tip.z);
        Vec3 c = new Vec3(tip.x, gy, tip.z);
        double topY = Math.max(tip.y, c.y) + 3 + 4 * s;
        for (LivingEntity e : near(new AABB(c.x - r, c.y - 2, c.z - r, c.x + r, topY, c.z + r))) {
            double d = Math.hypot(e.getX() - c.x, e.getZ() - c.z);
            if (d > r) continue;
            blow(e, dmg * (float) (1.0 - 0.45 * d / r), c, 1.4, 0.9);
            for (int k = 0; k < 8; k++) if (rig.arms.length > k && h.armTipWorld(k).distanceToSqr(tip) < 1) slapped.add(e.getUUID() + "/" + k);
        }
        h.sound(c, ModSounds.SLAM, 3f, 0.6f);
        h.sound(c, SoundEvents.COPPER_BREAK, 3f, 0.4f);
        h.particles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.WEATHERED_COPPER.defaultBlockState()), c, 80, 6 * s + 1, 0.2);
        h.particles(ParticleTypes.EXPLOSION, c, 6, 4 * s + 1, 0);
        thump(c, 0.8f);
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
        for (Vec3 p : down) {
            for (LivingEntity e : near(new AABB(p.x - r, p.y - 10 * s - 4, p.z - r, p.x + r, p.y + 6 * s + 3, p.z + r))) {
                String key = e.getUUID() + "/" + arm;
                if (slapped.contains(key) || Math.hypot(e.getX() - p.x, e.getZ() - p.z) > r) continue;
                slapped.add(key);
                blow(e, dmg, p, 1.4, 0.9);
            }
        }
    }

    private final Set<String> slapped = new HashSet<>();

    /** the sting volley: the strand ends nearest you flick stingers at you */
    private void volley() {
        float s = s();
        Vec3 aimAt = target != null && target.isAlive() ? target.position().add(0, target.getBbHeight() * 0.5, 0) : h.toWorld(h.aim());
        List<Integer> ends = nearestStrands(aimAt, 5);
        for (int k : ends) {
            Vec3 from = h.strandTipWorld(k).add(0, 1 + 2 * s, 0);
            Vec3 lead = target != null ? target.getDeltaMovement().multiply(1, 0, 1).scale(from.distanceTo(aimAt) / 1.8) : Vec3.ZERO;
            shoot(from, aimAt.add(lead), Shot.STINGER, 7f, 1f, 1.6f + 0.6f * s, 3f);
        }
        h.sound(h.position().add(0, 20 * s, 0), ModSounds.VOLLEY, 2.5f, 1f);
    }

    private void shoot(Vec3 from, Vec3 to, int kind, float dmg, float radius, float speed, float spread) {
        Shot sh = new Shot(level(), h, kind, dmg, radius);
        sh.setPos(from.x, from.y, from.z);
        Vec3 d = to.subtract(from);
        sh.shoot(d.x, d.y, d.z, speed, spread);
        level().addFreshEntity(sh);
    }

    /** the spore cloud: the egg clumps burst into drifting clouds of poison that follow what he's after */
    private void spores() {
        float s = s();
        List<Vec3> at = new ArrayList<>();
        for (int i = 0; i < rig.eggs.length && at.size() < 4; i++) if (!h.state.eggGone[(i * 7) % rig.eggs.length]) at.add(h.eggWorld((i * 7) % rig.eggs.length));
        if (target != null) at.add(target.position());
        if (at.isEmpty()) at.add(h.position());
        for (Vec3 p : at) {
            double gy = h.groundAt(p.x, p.z);
            AreaEffectCloud c = new AreaEffectCloud(level(), p.x, Math.max(gy, Math.min(p.y, gy + 4 * s + 1)), p.z);
            c.setRadius(3f + 5f * s);
            c.setDuration(260);
            c.setWaitTime(0);
            c.setRadiusPerTick(0f);
            c.setPotionContents(new PotionContents(Potions.POISON));
            c.setOwner(h);
            level().addFreshEntity(c);
            clouds.add(c);
            h.particles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAY_CONCRETE.defaultBlockState()), p, 30, 2 * s + 0.5, 0.2);
        }
        h.sound(h.position().add(0, 50 * s, 0), ModSounds.SPORES, 3f, 0.7f);
        h.sound(h.position().add(0, 50 * s, 0), ModSounds.EGG_BURST, 3f, 0.6f);
    }

    /** the clouds drift after what he's hunting, and whatever is in one is hurt as well as poisoned */
    private void cloudsTick() {
        if (clouds.isEmpty()) return;
        float s = s();
        LivingEntity tg = h.getTarget();
        for (Iterator<AreaEffectCloud> it = clouds.iterator(); it.hasNext(); ) {
            AreaEffectCloud c = it.next();
            if (c.isRemoved()) { it.remove(); continue; }
            if (tg != null && tg.isAlive()) {
                Vec3 d = tg.position().subtract(c.position()).multiply(1, 0, 1);
                if (d.length() > 1) {
                    Vec3 step = d.normalize().scale(0.05 + 0.06 * s);
                    double gy = h.groundAt(c.getX() + step.x, c.getZ() + step.z);
                    c.setPos(c.getX() + step.x, Math.max(gy, c.getY() + (gy + 1 - c.getY()) * 0.1), c.getZ() + step.z);
                }
            }
            if (c.tickCount % 10 == 0) {
                double r = c.getRadius();
                for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(c.position(), c.position()).inflate(r, 3 + 3 * s, r), h::fairGame)) {
                    if (Math.hypot(e.getX() - c.getX(), e.getZ() - c.getZ()) > r) continue;
                    e.invulnerableTime = 0;
                    e.hurt(h.damageSources().indirectMagic(c, h), h.dmg(5f, e));
                    e.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
                }
            }
        }
    }

    /** the pod burst: every pod left swells and sprays poison goo straight down */
    private void podBurst() {
        float s = s();
        if (t < Moves.POD_AT || t > Moves.POD_AT + 16 || t % 2 != 0) return;
        if (t == Moves.POD_AT) h.sound(h.position().add(0, 40 * s, 0), ModSounds.GOO, 4f, 0.6f);
        double r = 5 * s + 3;
        for (int i = 0; i < rig.pods.length; i++) {
            if (h.isPodPopped(i)) continue;
            Vec3 c = h.podWorld(i);
            for (int k = 0; k < 6; k++) {
                double ox = (h.getRandom().nextDouble() - 0.5) * r, oz = (h.getRandom().nextDouble() - 0.5) * r;
                level().sendParticles(ParticleTypes.ITEM_SLIME, c.x + ox * 0.3, c.y, c.z + oz * 0.3, 0, ox * 0.06, -1.0, oz * 0.06, 1.0);
            }
            level().sendParticles(ParticleTypes.SNEEZE, c.x, c.y - 2 * s, c.z, 4, r * 0.3, 1, r * 0.3, 0.02);
            double gy = h.groundAt(c.x, c.z);
            for (LivingEntity e : near(new AABB(c.x - r, gy - 1, c.z - r, c.x + r, c.y, c.z + r))) {
                if (struckThisMove.contains(e.getUUID()) || Math.hypot(e.getX() - c.x, e.getZ() - c.z) > r) continue;
                struckThisMove.add(e.getUUID());
                blow(e, 18f, c, 0, 0);
                e.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 1));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
            }
        }
    }

    /** egg rain: an egg clump comes off and drops toward what he's after, bursting where it lands */
    private void dropEgg() {
        float s = s();
        List<Integer> left = new ArrayList<>();
        for (int i = 0; i < rig.eggs.length; i++) if (!h.state.eggGone[i]) left.add(i);
        if (left.isEmpty()) return;
        int i = left.get(h.getRandom().nextInt(left.size()));
        Vec3 from = h.eggWorld(i);
        h.eggGone(i);
        Vec3 to = target != null && target.isAlive() ? target.position() : h.position();
        double spread = 6 * s + 2;
        to = to.add((h.getRandom().nextDouble() - 0.5) * 2 * spread, 0, (h.getRandom().nextDouble() - 0.5) * 2 * spread);
        double dy = Math.max(1, from.y - to.y);
        double fall = Math.sqrt(2 * dy / 0.05);
        Shot sh = new Shot(level(), h, Shot.EGG, 14f, 2.5f + 3f * s);
        sh.setPos(from.x, from.y, from.z);
        sh.setDeltaMovement((to.x - from.x) / fall, 0, (to.z - from.z) / fall);
        level().addFreshEntity(sh);
        h.sound(from, ModSounds.EGG_BURST, 2f, 1.1f);
    }

    /** the whirlpool: he spins, and his strands drag everything in a wide ring in toward the middle under him */
    private void whirlpool() {
        float s = s();
        double bell = h.bellRadius();
        if (t == Moves.WHIRL_UP) h.sound(h.position(), ModSounds.CHURN, 4f, 0.6f);
        if (t > Moves.WHIRL_UP && t < Moves.WHIRL_CRUSH) {
            if (t % 20 == 0) h.sound(h.position(), ModSounds.CHURN, 3f, 0.7f);
            double R = bell * 1.9 + 6;
            Vec3 c = h.position();
            for (LivingEntity e : near(h.bodyBox().inflate(R - bell + 4, 0, R - bell + 4).setMaxY(h.getY() + rig.rimY * s))) {
                Vec3 in = new Vec3(c.x - e.getX(), 0, c.z - e.getZ());
                double d = in.length();
                if (d > R || d < 0.5) continue;
                Vec3 n = in.normalize();
                Vec3 round = new Vec3(-n.z, 0, n.x);
                double pull = (0.10 + 0.06 * s) * (0.5 + 0.5 * d / R);
                e.setDeltaMovement(e.getDeltaMovement().scale(0.85).add(n.scale(pull)).add(round.scale(0.12 + 0.05 * s)).add(0, 0.02, 0));
                e.hurtMarked = true;
                if (d < bell && every(e, 20)) { blow(e, 6f, c, 0, 0); sting(e); }
            }
            if (t % 3 == 0) {
                double a = t * 0.4;
                for (int k = 0; k < 16; k++) {
                    double aa = a + k * Math.PI / 8, rr = R * (0.3 + 0.7 * ((k * 37 + t) % 16) / 16.0);
                    level().sendParticles(ParticleTypes.BUBBLE_POP, c.x + Math.cos(aa) * rr, groundUnder() + 0.5, c.z + Math.sin(aa) * rr, 2, 0.5, 0.2, 0.5, 0.05);
                }
            }
        }
        if (t == Moves.WHIRL_CRUSH) {
            Vec3 c = h.position();
            double r = bell * 0.75 + 4;
            for (LivingEntity e : near(h.bodyBox().setMaxY(h.getY() + rig.rimY * s))) {
                if (h.horiz(e.position()) > r) continue;
                blow(e, 55f, c, 1.2, 1.6);
            }
            h.sound(c, ModSounds.SLAM, 4f, 0.5f);
            h.sound(c, ModSounds.SPLASH, 4f, 0.5f);
            ring(new Vec3(c.x, groundUnder() + 1, c.z), r);
            thump(c, 0.9f);
        }
    }

    /** the sky dive: up very high, over he turns, bell first down he comes, and a huge shock wave where he lands */
    private void skyDive() {
        float s = s();
        int turn = Moves.DIVE_CLIMB, fall = Moves.DIVE_CLIMB + Moves.DIVE_TURN, back = fall + Moves.DIVE_FALL;
        // follow it while he climbs
        if (t < fall && target != null && target.isAlive()) h.setAim(h.toModel(target.position()));
        if (t == turn - 10) h.sound(h.position().add(0, rig.rimY * s, 0), ModSounds.WARN_DIVE, 5f, 0.9f);
        if (t >= turn && t < fall) diveOver = Moves.smooth((t - turn) / (float) Moves.DIVE_TURN);
        if (t == fall) h.sound(h.position(), ModSounds.SWOOP, 5f, 0.6f);
        if (t >= fall && t < back && !landed) {
            // down, bell first: he's landed when the lowest of him reaches the ground
            Vec3 crown = h.crownWorld();
            double gy = h.groundAt(crown.x, crown.z);
            if (crown.y <= gy + 1.5 * s + 0.5 || t == back - 1) {
                landed = true;
                double R = 75 * s + 24;
                Vec3 c = new Vec3(crown.x, gy, crown.z);
                for (LivingEntity e : near(new AABB(c, c).inflate(R, 30 * s + 12, R))) {
                    double d = Math.hypot(e.getX() - c.x, e.getZ() - c.z);
                    if (d > R) continue;
                    blow(e, 70f * (float) (1 - 0.5 * d / R), c, 3.0, 1.4);
                    knockDown(e);
                }
                h.sound(c, ModSounds.SHOCK, 5f, 0.5f);
                h.sound(c, ModSounds.SLAM, 5f, 0.4f);
                h.sound(c, ModSounds.SPLASH, 4f, 0.5f);
                h.particles(ParticleTypes.EXPLOSION_EMITTER, c.add(0, 1, 0), 3, 6 * s, 0);
                h.particles(ParticleTypes.CLOUD, c.add(0, 1, 0), 250, R * 0.5, 0.25);
                for (int k = 0; k < 4; k++) ring(c.add(0, 1, 0), R * (0.4 + 0.2 * k));
                thump(c, 1.3f);
                if (griefing()) crack(BlockPos.containing(c), (int) Math.ceil(R * 0.6));
                // on to the turn back up
                h.rewindMove(back);
                t = back;
            }
        }
        if (t >= back) diveOver = 1f - Moves.smooth((t - back - 8) / (float) (Moves.DIVE_BACK - 8));
        h.setLift(diveOver);
    }

    /** the deep toll: three tolls, louder each time, then a massive shock ring that throws everything far */
    private void deepToll() {
        float s = s();
        Vec3 mid = h.position().add(0, (rig.rimY + 20) * s, 0);
        if (t == 1 || t == 37 || t == 73) {
            int k = t / 36;
            h.pulse(1.2f + 0.2f * k);
            h.sound(mid, ModSounds.TOLL, 3f + 1.5f * k, 0.8f);
            ring(h.position().add(0, (rig.rimY - 10) * s, 0), h.bellRadius() * (1.0 + 0.15 * k));
            for (LivingEntity e : near(h.bodyBox().inflate(40 * s + 20))) e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80 + 40 * k, 0));
            thump(h.position(), 0.2f + 0.15f * k);
        }
        int big = Moves.TOLL_BIG;
        if (t == big) {
            h.pulse(1.8f);
            h.sound(mid, ModSounds.TOLL_BIG, 6f, 1f);
            h.sound(mid, ModSounds.SHOCK, 5f, 0.4f);
            thump(h.position(), 1.2f);
        }
        if (t >= big && t <= big + 25) {
            double R = 120 * s + 40, r0 = h.bellRadius();
            double r = r0 + (t - big) / 25.0 * (R - r0), rIn = r0 + (t - big - 1) / 25.0 * (R - r0);
            Vec3 c = h.position();
            for (LivingEntity e : near(h.bodyBox().inflate(R - r0 + 4, 100 * s + 40, R - r0 + 4))) {
                if (struckThisMove.contains(e.getUUID())) continue;
                double d = h.horiz(e.position());
                if (d > r) continue;
                struckThisMove.add(e.getUUID());
                blow(e, 50f * (float) (1 - 0.4 * d / R), c, 3.4, 1.1);
                e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
                knockDown(e);
            }
            if (t % 2 == 0) ring(new Vec3(c.x, groundUnder() + 2, c.z), r);
            if (griefing()) shatter(c, Math.max(0, rIn), r, 40 * s + 12);
        }
    }

    /** the big toll's ring breaks glass and leaves as it passes (a limited number of blocks each tick) */
    private void shatter(Vec3 c, double r0, double r1, double height) {
        ServerLevel l = level();
        int broken = 0;
        double mid = (r0 + r1) * 0.5;
        int n = (int) Math.min(1200, Math.max(16, 2 * Math.PI * mid));
        for (int i = 0; i < n && broken < 300; i++) {
            double a = i * 2 * Math.PI / n;
            for (double r = Math.max(0, r0); r <= r1 && broken < 300; r += 1) {
                int x = Mth.floor(c.x + Math.cos(a) * r), z = Mth.floor(c.z + Math.sin(a) * r);
                if (!l.hasChunkAt(new BlockPos(x, 0, z))) continue;
                int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                int y0 = Math.max(l.getMinBuildHeight(), top - 12), y1 = Math.min(top + 2, (int) (c.y + height));
                for (int y = y1; y >= y0 && broken < 300; y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState st = l.getBlockState(p);
                    if (st.is(BlockTags.LEAVES) || st.is(BlockTags.IMPERMEABLE) || st.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock && st.getSoundType() == net.minecraft.world.level.block.SoundType.GLASS) {
                        l.destroyBlock(p, false);
                        broken++;
                    }
                }
            }
        }
    }

    /** the stinger storm: every strand flicks stingers up and out, and they rain down over a wide area */
    private void stingerRain() {
        float s = s();
        double R = 90 * s + 24;
        Vec3 c = h.position();
        List<LivingEntity> near = near(h.bodyBox().inflate(R - h.bellRadius() + 4, 40 * s + 16, R - h.bellRadius() + 4));
        int n = 3 + (int) (3 * s);
        for (int k = 0; k < n; k++) {
            int strand = h.getRandom().nextInt(rig.strands.length);
            Vec3 from = h.strandTipWorld(strand).add(0, 2 + 3 * s, 0);
            Vec3 to;
            if (!near.isEmpty() && h.getRandom().nextInt(3) > 0) {
                LivingEntity e = near.get(h.getRandom().nextInt(near.size()));
                double sp = 2 * s + 1.5;
                to = e.position().add((h.getRandom().nextDouble() - 0.5) * sp, e.getBbHeight() * 0.5, (h.getRandom().nextDouble() - 0.5) * sp);
            } else {
                double a = h.getRandom().nextDouble() * Math.PI * 2, d = Math.sqrt(h.getRandom().nextDouble()) * R;
                double x = c.x + Math.cos(a) * d, z = c.z + Math.sin(a) * d;
                to = new Vec3(x, h.groundAt(x, z), z);
            }
            // up first and over: a lob
            Vec3 d = to.subtract(from);
            double horiz = Math.hypot(d.x, d.z);
            Shot sh = new Shot(level(), h, Shot.STINGER, 10f, 1f);
            sh.setPos(from.x, from.y, from.z);
            double speed = 1.3 + 0.7 * s;
            double time = Math.max(8, horiz / speed);
            sh.setDeltaMovement(d.x / time, d.y / time + 0.5 * 0.02 * time, d.z / time);
            level().addFreshEntity(sh);
        }
        if (t % 6 == 0) h.sound(c.add(0, 20 * s, 0), ModSounds.VOLLEY, 3f, 0.8f + h.getRandom().nextFloat() * 0.3f);
    }

    /** the sun lances: a beam of light from each glowing spot to the ground, sweeping out round him and burning */
    private void sunLances() {
        float s = s();
        double bell = h.bellRadius();
        int from = Moves.LANCE_FROM, to = Moves.LANCE_TO;
        if (t < from || t > to) {
            if (t == from - 12) for (int i = 0; i < 5; i++) h.particles(ParticleTypes.END_ROD, h.spotWorld(i), 30, 3 * s + 0.5, 0.05);
            return;
        }
        float frac = (t - from) / (float) (to - from);
        if (t % 20 == 0) h.sound(h.position().add(0, (rig.rimY + 20) * s, 0), ModSounds.BEAM, 3f, 0.7f + 0.3f * frac);
        double beamR = 2.5 + 2.5 * s;
        for (int i = 0; i < 5; i++) {
            Vec3 top = h.spotWorld(i);
            Vec3 ground;
            if (i < 4) {
                // spiralling out from under him, each from its own side
                double a0 = Math.atan2(rig.spots[i].centre().z, rig.spots[i].centre().x) - h.getYRot() * Mth.DEG_TO_RAD;
                double a = a0 + frac * 2.4;
                double r = bell * (0.35 + 1.35 * frac);
                double x = h.getX() + Math.cos(a) * r, z = h.getZ() + Math.sin(a) * r;
                ground = new Vec3(x, h.groundAt(x, z), z);
            } else {
                // the vase's beam hunts what he's after
                Vec3 want = target != null && target.isAlive() ? target.position() : h.position();
                Vec3 was = lance[4] == null ? new Vec3(h.getX(), groundUnder(), h.getZ()) : lance[4];
                Vec3 d = want.subtract(was).multiply(1, 0, 1);
                double step = 0.25 + 0.35 * s;
                Vec3 nx = d.length() > step ? was.add(d.normalize().scale(step)) : was.add(d);
                ground = new Vec3(nx.x, h.groundAt(nx.x, nx.z), nx.z);
            }
            lance[i] = ground;
            if (t % 2 == 0) {
                Vec3 line = ground.subtract(top);
                for (int k = 0; k <= 12; k++) {
                    Vec3 p = top.add(line.scale(k / 12.0));
                    level().sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.3 * s, 0.3 * s, 0.3 * s, 0);
                }
                level().sendParticles(ParticleTypes.FLAME, ground.x, ground.y + 0.2, ground.z, 8, beamR * 0.4, 0.1, beamR * 0.4, 0.02);
                level().sendParticles(ParticleTypes.LAVA, ground.x, ground.y + 0.2, ground.z, 1, beamR * 0.3, 0.1, beamR * 0.3, 0);
            }
            for (LivingEntity e : near(new AABB(ground.x - beamR, ground.y - 2, ground.z - beamR, ground.x + beamR, top.y, ground.z + beamR))) {
                if (Math.hypot(e.getX() - ground.x, e.getZ() - ground.z) > beamR || !every(e, 10)) continue;
                blow(e, 16f, ground, 0.3, 0.2);
                e.igniteForSeconds(4);
            }
            if (griefing() && h.getRandom().nextInt(5) == 0) {
                BlockPos p = BlockPos.containing(ground.x + (h.getRandom().nextDouble() - 0.5) * beamR, ground.y, ground.z + (h.getRandom().nextDouble() - 0.5) * beamR);
                if (level().getBlockState(p).isAir() && level().getBlockState(p.below()).isFaceSturdy(level(), p.below(), net.minecraft.core.Direction.UP))
                    level().setBlockAndUpdate(p, Blocks.FIRE.defaultBlockState());
            }
        }
    }

    /** the undertow: every strand spreads wide and drags everything round him in, then slams it down */
    private void undertow() {
        float s = s();
        double bell = h.bellRadius();
        Vec3 c = h.position();
        if (t >= Moves.UNDERTOW_PULL && t < Moves.UNDERTOW_SLAM) {
            double R = bell * 2.2 + 8;
            if (t == Moves.UNDERTOW_PULL) h.sound(c, ModSounds.CHURN, 4f, 0.5f);
            for (LivingEntity e : near(h.bodyBox().inflate(R - bell + 4, 20 * s + 8, R - bell + 4))) {
                Vec3 in = new Vec3(c.x - e.getX(), 0, c.z - e.getZ());
                double d = in.length();
                if (d > R || d < 0.5) continue;
                e.setDeltaMovement(e.getDeltaMovement().scale(0.8).add(in.normalize().scale(0.16 + 0.1 * s)).add(0, 0.05, 0));
                e.hurtMarked = true;
                if (every(e, 20)) sting(e);
            }
            if (t % 2 == 0) {
                for (int k = 0; k < 12; k++) {
                    double a = h.getRandom().nextDouble() * Math.PI * 2, r = R * (0.5 + 0.5 * h.getRandom().nextDouble());
                    level().sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, c.x + Math.cos(a) * r, groundUnder() + 1, c.z + Math.sin(a) * r, 0, -Math.cos(a), 0.2, -Math.sin(a), 0.6);
                }
            }
        }
        if (t == Moves.UNDERTOW_SLAM) {
            double r = bell * 0.95 + 5;
            for (LivingEntity e : near(h.bodyBox().setMaxY(h.getY() + rig.rimY * s))) {
                if (h.horiz(e.position()) > r) continue;
                blow(e, 52f, c, 0.8, 0);
                e.setDeltaMovement(e.getDeltaMovement().x, -1.5, e.getDeltaMovement().z);
                knockDown(e);
            }
            h.sound(c, ModSounds.SLAM, 4f, 0.5f);
            h.sound(c, ModSounds.SPLASH, 4f, 0.6f);
            h.particles(ParticleTypes.CLOUD, new Vec3(c.x, groundUnder() + 1, c.z), 150, r * 0.5, 0.1);
            thump(c, 1f);
        }
    }

    private boolean griefing() {
        return HollowbellConfig.V.griefing && level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    /** the ground cracks under the slam: grass and dirt go to coarse dirt, plants go */
    private void crack(BlockPos c, int r) {
        ServerLevel l = level();
        r = Math.min(r, 48);
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (dx * dx + dz * dz > r * r || h.getRandom().nextInt(3) == 0) continue;
            BlockPos top = l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.offset(dx, 0, dz));
            BlockPos below = top.below();
            BlockState st = l.getBlockState(below);
            if (st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.DIRT) || st.is(Blocks.PODZOL) || st.is(Blocks.MYCELIUM))
                l.setBlock(below, Blocks.COARSE_DIRT.defaultBlockState(), 3);
            BlockState up = l.getBlockState(top);
            if (!up.isAir() && up.canBeReplaced() && up.getFluidState().isEmpty()) l.destroyBlock(top, false);
        }
    }

    private void ring(Vec3 c, double r) {
        int n = (int) Math.min(120, 12 + r * 1.5);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            level().sendParticles(ParticleTypes.SONIC_BOOM, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    private void thump(Vec3 c, float k) {
        if (!HollowbellConfig.V.screenShake) return;
        for (ServerPlayer p : level().players()) {
            if (p.distanceToSqr(c) > Mth.square(200 * s() + 80)) continue;
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new net.jj.hollowbell.net.ThumpPayload(c.x, c.y, c.z, k * Math.min(1.5f, 0.4f + s())));
        }
    }

    // ------------------------------------------------------------------ grab and harvest

    private void runGrab() {
        float s = s();
        Vec3 tip = h.strandTipWorld(arg);
        if (t < Moves.REACH) {
            // it follows what it's reaching for
            if (target != null && target.isAlive()) h.setAim(h.toModel(target.position()));
            if (target == null && treeAt == null) { end(); return; }
            return;
        }
        if (t == Moves.REACH) {
            if (target != null) {
                // got it if it's within reach of the strand's end (or right under it)
                double reach = 14 * s + 4;
                if (!target.isAlive() || (target.position().distanceTo(tip) > reach && h.horiz(target.position()) > h.bellRadius())) { end(); return; }
                grabbed = target;
                grabSeat = seat(grabbed, tip);
                h.sound(tip, ModSounds.GRAB, 2.5f, 0.8f);
                if (grabbed instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable(gentle ? "message.hollowbell.lifting_you" : "message.hollowbell.grabbed"), true);
            } else if (treeAt != null) {
                grabbedTree = pullUpTree(treeAt, tip);
                treeAt = null;
                if (grabbedTree == null) { end(); return; }
            }
            intoFrom = tip;
        }
        int into0 = Moves.REACH + Moves.LIFT;
        if (t < into0) {
            lift = Mth.clamp((t - Moves.REACH) / (float) Moves.LIFT, 0f, 1f);
            h.setLift(lift);
            // everything is carried after the pose (afterPose); here only the sting of being held
            if (grabbed != null && !gentle && t % 30 == 0 && !(grabbed instanceof Player p && p.isCreative())) {
                blow(grabbed, 4f, tip, 0, 0);
                sting(grabbed);
            }
            if (t % 20 == 0) h.sound(tip, ModSounds.STRAND, 1.5f, 0.7f);
            return;
        }
        if (t == into0) {
            if (grabbed != null && grabSeat != null) intoFrom = grabSeat.position();
            // the one who asked to ride is put on the crown; everything else goes in the dome, if it fits
            if (gentle && grabbed != null) {
                LivingEntity who = grabbed;
                dropSeat(grabSeat); grabSeat = null; grabbed = null;
                h.seatOnCrown(who);
                end();
                return;
            }
            if (!domeFits() || !HollowbellConfig.V.insideDome || inside.size() >= slotCount()) {
                // too small (or not allowed) to take it in: a squeeze, and it's let go
                if (grabbed != null) blow(grabbed, 12f, h.position(), 0, 0);
                letGoOfGrab(true);
                end();
                return;
            }
        }
        int slot = freeSlot();
        float k = Mth.clamp((t - into0) / (float) Moves.INTO, 0f, 1f);
        Vec3 to = slotWorld(slot, grabbed);
        Vec3 at = intoFrom.lerp(to, k * k * (3 - 2 * k));
        if (grabSeat != null) { grabSeat.follow(Seat.FREE, 0); grabSeat.moveTo(at.x, at.y, at.z); }
        if (grabbedTree != null) placeTree(grabbedTree, at);
        if (k >= 1f) {
            Inside in = new Inside();
            in.e = grabbed; in.seat = grabSeat; in.tree = grabbedTree; in.slot = slot;
            inside.add(in);
            if (grabbed instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.hollowbell.inside"), false);
                net.jj.hollowbell.HollowbellMod.award(sp, "inside");
            }
            grabbed = null; grabSeat = null; grabbedTree = null;
            h.sound(at, ModSounds.ECHO, 2.5f, 1f);
            end();
        }
    }

    private boolean domeFits() { return 60 * s() >= 4f; }

    /** hits on the strand that has hold of something: enough and it lets go */
    public void strandHit(int strand, float amount, @Nullable Entity by) {
        if ((move != Moves.GRAB && move != Moves.HARVEST) || strand != arg || t < Moves.REACH) return;
        if (gentle) return;
        strandHits += 1f + amount / 10f;
        h.sound(h.strandTipWorld(strand), ModSounds.STRAND, 2f, 1.1f);
        if (strandHits >= 4f) {
            if (by instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("message.hollowbell.strand_let_go"), true);
            letGoOfGrab(true);
            end();
        }
    }

    public void armHit(int arm, float amount, @Nullable Entity by) {
        if (move != Moves.WRAP || arm != arg || wrapped == null) return;
        armHits += 1f + amount / 10f;
        if (armHits >= 4f) {
            if (by instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("message.hollowbell.arm_let_go"), true);
            letGoOfWrap();
            end();
        }
    }

    private void letGoOfGrab(boolean fall) {
        if (grabbed != null) {
            LivingEntity e = grabbed;
            dropSeat(grabSeat);
            e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));
            if (e instanceof ServerPlayer sp && fall) sp.displayClientMessage(Component.translatable("message.hollowbell.dropped"), true);
        }
        if (grabbedTree != null) dropTree(grabbedTree);
        grabbed = null; grabSeat = null; grabbedTree = null; lift = 0f;
    }

    // ------------------------------------------------------------------ the arm wrap

    private void runWrap() {
        Vec3 tip = h.armTipWorld(arg);
        if (t < Moves.WRAP_REACH) {
            if (target != null && target.isAlive()) h.setAim(h.toModel(target.position()));
            return;
        }
        if (t == Moves.WRAP_REACH) {
            if (target == null || !target.isAlive() || target.position().distanceTo(tip) > 30 * s() + 6) { end(); return; }
            wrapped = target;
            wrapSeat = seat(wrapped, tip);
            h.sound(tip, ModSounds.GRAB, 2.5f, 0.6f);
        }
        if (t >= Moves.WRAP_REACH + Moves.WRAP_HOLD) { letGoOfWrap(); return; }
        if (wrapped != null && t % 20 == 0) {
            blow(wrapped, 8f, tip, 0, 0);
            wrapped.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3));
            h.sound(tip, SoundEvents.COPPER_STEP, 2f, 0.5f);
        }
    }

    private void letGoOfWrap() {
        if (wrapped != null) {
            dropSeat(wrapSeat);
            wrapped.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0));
        }
        wrapped = null; wrapSeat = null;
    }

    // ------------------------------------------------------------------ holding things: seats follow the pose

    private Seat seat(LivingEntity e, Vec3 at) {
        Seat st = new Seat(level(), h);
        st.setPos(at.x, at.y - e.getBbHeight() * 0.5, at.z);
        level().addFreshEntity(st);
        e.stopRiding();
        e.startRiding(st, true);
        return st;
    }

    private void dropSeat(@Nullable Seat st) {
        if (st == null) return;
        for (Entity e : st.getPassengers()) { e.stopRiding(); e.fallDistance = 0; }
        st.discard();
    }

    /** after the pose is worked out each tick: move the seats and trees to where the strands and arms are now */
    public void afterPose() {
        if (grabSeat != null || grabbedTree != null) {
            if ((move == Moves.GRAB || move == Moves.HARVEST) && t < Moves.REACH + Moves.LIFT && arg >= 0) {
                if (grabSeat != null && grabbed != null) {
                    grabSeat.follow(Seat.STRAND_TIP, arg);
                    Vec3 at = h.seatSpot(Seat.STRAND_TIP, arg, grabbed);
                    grabSeat.moveTo(at.x, at.y, at.z);
                    if (grabbed.getVehicle() != grabSeat) grabbed.startRiding(grabSeat, true);
                    grabbed.fallDistance = 0;
                }
                if (grabbedTree != null) placeTree(grabbedTree, h.strandTipWorld(arg).add(0, -1, 0));
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
            Vec3 at = h.seatSpot(Seat.INSIDE, in.slot, in.e);
            if (in.seat != null && in.e != null) {
                in.seat.follow(Seat.INSIDE, in.slot);
                in.seat.moveTo(at.x, at.y, at.z);
                if (in.e.getVehicle() != in.seat) in.e.startRiding(in.seat, true);
                in.e.fallDistance = 0;
            }
            if (in.tree != null) placeTree(in.tree, at);
        }
    }

    // ------------------------------------------------------------------ inside the dome

    private static final int SLOTS = HollowbellEntity.SLOTS;
    private int slotCount() { return SLOTS; }
    private Vec3 slotWorld(int i, @Nullable LivingEntity who) { return h.slotWorld(i); }

    private int freeSlot() {
        boolean[] used = new boolean[SLOTS];
        for (Inside in : inside) if (in.slot >= 0 && in.slot < SLOTS) used[in.slot] = true;
        // players get the places in reach of the glowing spots, the rest go further out
        boolean player = grabbed instanceof Player;
        if (player) for (int i = 0; i < 8; i++) if (!used[i]) return i;
        for (int i = 8; i < SLOTS; i++) if (!used[i]) return i;
        for (int i = 0; i < SLOTS; i++) if (!used[i]) return i;
        return 0;
    }

    private void insideTick() {
        for (Iterator<Inside> it = inside.iterator(); it.hasNext(); ) {
            Inside in = it.next();
            in.age++;
            if (in.e != null) {
                if (!in.e.isAlive() || in.e.isRemoved() || in.e.level() != h.level()) {
                    if (in.seat != null) in.seat.discard();
                    it.remove();
                    continue;
                }
                // it hurts in here: he's digesting whatever he takes in. A creature doesn't last long; a player has
                // time to fight back from inside
                boolean player = in.e instanceof Player;
                if (in.age % (player ? 40 : 20) == 0 && !(in.e instanceof Player p && p.isCreative())) {
                    in.e.invulnerableTime = 0;
                    in.e.hurt(h.damageSources().mobAttack(h), h.dmg(player ? 4f : 8f, in.e));
                    in.e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, player ? 0 : 1));
                }
            } else if (in.tree != null && in.age > 20 * 60 * 3) {
                dropTree(in.tree);
                it.remove();
            }
        }
    }

    /** a hit from inside: hurt him enough and he lets go of everyone in there */
    public void hurtFromInside(float dealt) {
        hurtInside += dealt;
        if (hurtInside >= h.healthMax() * 0.04f) {
            hurtInside = 0f;
            h.sound(h.position().add(0, rig.rimY * s(), 0), ModSounds.HURT_HEAVY, 3f, 1f);
            dropEveryoneInside();
        }
    }

    public void dropEveryoneInside() {
        for (Iterator<Inside> it = inside.iterator(); it.hasNext(); ) {
            Inside in = it.next();
            if (in.e instanceof Player p) {
                dropSeat(in.seat);
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 300, 0));
                if (p instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("message.hollowbell.spat_out"), true);
                it.remove();
            }
        }
    }

    /** everything he holds goes, in or out. Trees drop as their blocks when he dies */
    public void letGoOfEverything(boolean dying) {
        letGoOfGrab(false);
        letGoOfWrap();
        for (Inside in : inside) {
            if (in.e != null) { dropSeat(in.seat); in.e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 300, 0)); }
            if (in.tree != null) { if (dying) dropTree(in.tree); else removeTree(in.tree); }
        }
        inside.clear();
    }

    // ------------------------------------------------------------------ trees

    private @Nullable LivingEntity findHarvestMob() {
        double r = h.bellRadius() * 1.1;
        List<LivingEntity> l = level().getEntitiesOfClass(LivingEntity.class, h.bodyBox().setMaxY(h.getY() + 20 * s() + 4),
                e -> (e instanceof Animal || e instanceof AbstractVillager || (e instanceof Mob && !(e instanceof HollowbellEntity) && !(e instanceof Belling)))
                        && h.fairGame(e) && h.horiz(e.position()) < r && !e.isPassenger());
        if (l.isEmpty()) return null;
        return l.get(h.getRandom().nextInt(l.size()));
    }

    private @Nullable BlockPos findTree() {
        if (!griefing()) return null;
        ServerLevel l = level();
        double r = h.bellRadius();
        for (int tries = 0; tries < 40; tries++) {
            double a = h.getRandom().nextDouble() * Math.PI * 2, d = Math.sqrt(h.getRandom().nextDouble()) * r;
            BlockPos top = l.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(h.getX() + Math.cos(a) * d, h.getY(), h.getZ() + Math.sin(a) * d));
            // look down through the leaves for a trunk
            for (int dy = 0; dy < 12; dy++) {
                BlockPos p = top.below(dy);
                if (!l.hasChunkAt(p)) break;
                BlockState st = l.getBlockState(p);
                if (st.is(BlockTags.LOGS)) {
                    BlockPos base = p;
                    while (l.getBlockState(base.below()).is(BlockTags.LOGS) && base.getY() > l.getMinBuildHeight()) base = base.below();
                    return base;
                }
                if (!st.is(BlockTags.LEAVES) && !st.isAir()) break;
            }
        }
        return null;
    }

    /** takes the tree out of the ground as a set of block displays, at most 96 blocks of it */
    private @Nullable Tree pullUpTree(BlockPos base, Vec3 at) {
        ServerLevel l = level();
        if (!l.getBlockState(base).is(BlockTags.LOGS)) return null;
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        q.add(base); seen.add(base);
        while (!q.isEmpty() && found.size() < 96) {
            BlockPos p = q.poll();
            BlockState st = l.getBlockState(p);
            if (!(st.is(BlockTags.LOGS) || st.is(BlockTags.LEAVES))) continue;
            found.add(p);
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos n = p.offset(dx, dy, dz);
                if (n.getY() < base.getY() || Math.abs(n.getX() - base.getX()) > 5 || Math.abs(n.getZ() - base.getZ()) > 5 || n.getY() - base.getY() > 14) continue;
                if (seen.add(n)) q.add(n);
            }
        }
        if (found.isEmpty()) return null;
        Tree tr = new Tree();
        // a tree is a lot bigger than a small Hollowbell's dome: it shrinks to fit
        tr.size = Mth.clamp(s() * 2.5f, 0.12f, 1f);
        for (BlockPos p : found) {
            BlockState st = l.getBlockState(p);
            Display.BlockDisplay d = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, l);
            d.setBlockState(st);
            d.setPosRotInterpolationDuration(3);
            Vector3f off = new Vector3f(p.getX() - base.getX() - 0.5f, p.getY() - base.getY(), p.getZ() - base.getZ() - 0.5f).mul(tr.size);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(tr.size), new Quaternionf()));
            d.setPos(at.x + off.x, at.y + off.y, at.z + off.z);
            d.addTag("hollowbell_tree");
            l.addFreshEntity(d);
            tr.blocks.add(d); tr.offsets.add(off); tr.states.add(st);
            l.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        h.sound(Vec3.atCenterOf(base), SoundEvents.ROOTED_DIRT_BREAK, 3f, 0.5f);
        h.particles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()), Vec3.atCenterOf(base), 40, 2, 0.1);
        return tr;
    }

    private void placeTree(Tree tr, Vec3 at) {
        for (int i = 0; i < tr.blocks.size(); i++) {
            Display.BlockDisplay d = tr.blocks.get(i);
            if (d.isRemoved()) continue;
            Vector3f o = tr.offsets.get(i);
            // hung from the top: the base hangs lowest
            d.setPos(at.x + o.x, at.y + o.y - 14 * tr.size, at.z + o.z);
        }
    }

    /** the tree comes down as the blocks it was made of */
    private void dropTree(Tree tr) {
        ServerLevel l = level();
        Vec3 c = h.position();
        for (int i = 0; i < tr.blocks.size(); i++) {
            Display.BlockDisplay d = tr.blocks.get(i);
            BlockState st = tr.states.get(i);
            if (st.is(BlockTags.LOGS) || h.getRandom().nextInt(6) == 0) {
                double x = c.x + (h.getRandom().nextDouble() - 0.5) * 6, z = c.z + (h.getRandom().nextDouble() - 0.5) * 6;
                l.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(l, x, h.groundAt(x, z) + 0.5, z, new ItemStack(st.getBlock().asItem())));
            }
            d.discard();
        }
        tr.blocks.clear();
    }

    private void removeTree(Tree tr) { for (Display.BlockDisplay d : tr.blocks) d.discard(); tr.blocks.clear(); }

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
                if (!l.hasChunkAt(q)) continue;
                BlockState st = l.getBlockState(q);
                if (!st.isAir() && st.canBeReplaced() && st.getFluidState().isEmpty()) l.destroyBlock(q, false);
            }
        }
    }

    // ------------------------------------------------------------------ the sting and the shed

    public void sting(LivingEntity e) {
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        long now = h.level().getGameTime();
        Integer last = stingAt.get(e.getUUID());
        if (last != null && now - last < 20) return;
        stingAt.put(e.getUUID(), (int) now);
        if (stingAt.size() > 200) stingAt.clear();
        e.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        e.hurt(h.damageSources().mobAttack(h), h.dmg(3f, e));
        h.sound(e.position(), ModSounds.STING, 1f, 1f);
    }

    private void shed() {
        int want = Math.max(0, HollowbellConfig.V.shedCount);
        List<Integer> left = new ArrayList<>();
        for (int i = 0; i < rig.eggs.length; i++) if (!h.state.eggGone[i]) left.add(i);
        java.util.Collections.shuffle(left, new java.util.Random(h.getRandom().nextLong()));
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
            h.particles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAY_CONCRETE.defaultBlockState()), at, 20, 1.5 * s() + 0.3, 0.1);
            n++;
        }
        if (n > 0) h.sound(h.position().add(0, 50 * s(), 0), ModSounds.EGG_BURST, 3f, 0.8f);
    }

    // ------------------------------------------------------------------ helpers

    private List<LivingEntity> near(AABB box) {
        return level().getEntitiesOfClass(LivingEntity.class, box, e -> h.fairGame(e) || (e == grabbed && !gentle) || e == wrapped);
    }

    private int nearestStrand(Vec3 p) {
        h.ensurePose();
        int best = -1; double bd = Double.MAX_VALUE;
        for (var S : rig.strands) {
            if (S.k() == arg && move != Moves.NONE) continue;
            Vec3 tip = h.strandTipWorld(S.k());
            double d = Math.pow(tip.x - p.x, 2) + Math.pow(tip.z - p.z, 2);
            // the long ones, so it can reach down
            d += Math.max(0, (S.low() - 20)) * s() * 20;
            if (d < bd) { bd = d; best = S.k(); }
        }
        return best;
    }

    /** the n strands whose ends are nearest a point */
    private List<Integer> nearestStrands(Vec3 p, int n) {
        h.ensurePose();
        List<double[]> all = new ArrayList<>();
        for (var S : rig.strands) {
            Vec3 tip = h.strandTipWorld(S.k());
            all.add(new double[]{tip.distanceToSqr(p), S.k()});
        }
        all.sort((a, b) -> Double.compare(a[0], b[0]));
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < Math.min(n, all.size()); i++) out.add((int) all.get(i)[1]);
        return out;
    }

    private int nearestArm(Vec3 p) {
        int best = 0; double bd = Double.MAX_VALUE;
        for (var A : rig.arms) {
            Vec3 tip = h.toWorld(A.tip());
            Vec3 mid = h.toWorld(A.centre());
            double d = Math.min(tip.distanceToSqr(p.x, tip.y, p.z), mid.distanceToSqr(p.x, mid.y, p.z));
            if (d < bd) { bd = d; best = A.k(); }
        }
        return best;
    }
}
