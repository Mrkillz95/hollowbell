package net.jj.hollowbell.entity;

import com.mojang.math.Transformation;
import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.ModEntities;
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
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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

/** What each of his moves does to the world, the things he is holding, and the inside of his dome. Server only. */
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
    public boolean holdsStill() { return move == Moves.DROP || move == Moves.CURTAIN || move == Moves.WRAP || (move == Moves.SLAM && t > 20); }

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

    /** makes him do a move now. False if he can't (busy, down, nothing to do it to). */
    public boolean force(int which, @Nullable LivingEntity at) {
        if (h.isDeadOrDying() || which <= 0 || which >= Moves.NAMES.length) return false;
        if (move != Moves.NONE) end();
        return start(which, at);
    }

    private boolean start(int which, @Nullable LivingEntity at) {
        if (h.sunk() && (which == Moves.DROP || which == Moves.GRAB || which == Moves.HARVEST)) return false;
        target = at;
        struckThisMove.clear();
        Vector3f aim = at != null ? h.toModel(at.position()) : new Vector3f(0, 0, 40);
        int a = -1;
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
            case Moves.SHED -> { if (h.eggsLeft() == 0 || HollowbellConfig.V.shedCount <= 0) return false; }
            case Moves.SWEEP -> {
                if (at == null) aim = new Vector3f(1, 0, 0);
            }
            default -> {}
        }
        move = which; t = 0; arg = a;
        h.setMove(which, a, aim);
        switch (which) {
            case Moves.DROP -> h.sound(h.position().add(0, rig.rimY * s(), 0), SoundEvents.WARDEN_ROAR, 3f, 0.6f);
            case Moves.SLAM -> h.sound(h.armTipWorld(a), SoundEvents.RAVAGER_ROAR, 2.5f, 0.5f);
            case Moves.CURTAIN -> h.sound(h.position().add(0, 20 * s(), 0), SoundEvents.SCULK_CLICKING, 3f, 0.5f);
            case Moves.SHED -> h.sound(h.position().add(0, 60 * s(), 0), SoundEvents.FROGSPAWN_HATCH, 3f, 0.4f);
            default -> {}
        }
        return true;
    }

    private @Nullable BlockPos treeAt;

    private void end() {
        if (move == Moves.GRAB || move == Moves.HARVEST) letGoOfGrab(false);
        if (move == Moves.WRAP) letGoOfWrap();
        move = Moves.NONE; t = 0; arg = -1; target = null;
        h.setMove(Moves.NONE, -1, new Vector3f());
        cooldown = (h.angry() ? 50 : 90) + h.getRandom().nextInt(60);
        treeAt = null;
    }

    public void tick() {
        insideTick();
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
        if (h.carrying()) return;              // whoever is on his crown picks the moves
        choose();
    }

    private void choose() {
        LivingEntity tg = h.getTarget();
        if (tg == null) {
            if (HollowbellConfig.V.harvest && --harvestIn <= 0) {
                harvestIn = 500 + h.getRandom().nextInt(900);
                if (h.variant() != HollowbellEntity.CALM || h.getRandom().nextInt(3) == 0) start(Moves.HARVEST, null);
            }
            return;
        }
        double d = h.horiz(tg.position());
        double bell = h.bellRadius();
        boolean flying = tg instanceof Player p && (p.isFallFlying() || p.getAbilities().flying)
                || tg.getY() - h.groundAt(tg.getX(), tg.getZ()) > 8 + 20 * s();
        List<int[]> opts = new ArrayList<>();
        if (flying && d < bell * 2.5) opts.add(new int[]{Moves.PULSE, 6});
        if (d < bell * 0.85) {
            if (!h.sunk()) opts.add(new int[]{Moves.GRAB, 4});
            opts.add(new int[]{Moves.CURTAIN, 2});
            if (!h.sunk() && d < bell * 0.6) opts.add(new int[]{Moves.DROP, 2});
            opts.add(new int[]{Moves.PULSE, 1});
        } else if (d < bell * 1.7) {
            opts.add(new int[]{Moves.SLAM, 3});
            opts.add(new int[]{Moves.WRAP, 2});
            opts.add(new int[]{Moves.SWEEP, 2});
            opts.add(new int[]{Moves.PULSE, 1});
        }
        if (d < h.senseRange() && h.eggsLeft() > 0 && HollowbellConfig.V.shedCount > 0 && bellingsNear() < 8) opts.add(new int[]{Moves.SHED, 1});
        int total = 0; for (int[] o : opts) total += o[1];
        if (total == 0) return;
        int r = h.getRandom().nextInt(total);
        for (int[] o : opts) { r -= o[1]; if (r < 0) { if (!start(o[0], tg)) cooldown = 20; return; } }
    }

    private int bellingsNear() {
        return level().getEntitiesOfClass(Belling.class, h.bodyBox().inflate(40)).size();
    }

    // ------------------------------------------------------------------ each move, tick by tick

    private void run() {
        float s = s();
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
                        if (t % 20 == 0) { e.hurt(h.damageSources().mobAttack(h), h.dmg(4f, e)); sting(e); }
                    }
                    if (t % 15 == 0) h.sound(c, SoundEvents.SLIME_SQUISH, 2f, 0.5f);
                }
            }
            case Moves.SWEEP -> {
                if (t >= 20 && t <= 48 && t % 2 == 0) {
                    Vector3f a = h.aim();
                    Vec3 dir = h.toWorld(new Vector3f(a.x, 0, a.z).normalize().mul(10)).subtract(h.position()).normalize();
                    double r = h.bellRadius() * 1.25 + 4;
                    for (LivingEntity e : near(h.bodyBox().setMaxY(h.getY() + rig.rimY * s * 0.8))) {
                        if (struckThisMove.contains(e.getUUID()) || h.horiz(e.position()) > r) continue;
                        struckThisMove.add(e.getUUID());
                        e.hurt(h.damageSources().mobAttack(h), h.dmg(6f, e));
                        e.setDeltaMovement(e.getDeltaMovement().add(dir.x * 1.6, 0.5, dir.z * 1.6));
                        e.hurtMarked = true;
                        sting(e);
                    }
                    if (t == 30) h.sound(h.position().add(0, 30 * s, 0), SoundEvents.PLAYER_ATTACK_SWEEP, 3f, 0.4f);
                }
            }
            case Moves.SLAM -> {
                int hit = Math.round(BellRig.SLAM_HIT * Moves.length(Moves.SLAM));
                if (t == hit) {
                    Vec3 c = h.armTipWorld(arg);
                    c = new Vec3(c.x, Math.max(c.y, h.groundAt(c.x, c.z)), c.z);
                    double r = 16 * s + 3;
                    for (LivingEntity e : near(new AABB(c, c).inflate(r, 6 + 12 * s, r))) {
                        double d = e.position().distanceTo(c);
                        if (d > r) continue;
                        e.hurt(h.damageSources().mobAttack(h), h.dmg(14f, e) * (float) (1.0 - 0.5 * d / r));
                        Vec3 out = e.position().subtract(c).multiply(1, 0, 1);
                        out = out.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : out.normalize();
                        e.setDeltaMovement(e.getDeltaMovement().add(out.x * 1.2, 0.9, out.z * 1.2));
                        e.hurtMarked = true;
                    }
                    h.sound(c, SoundEvents.GENERIC_EXPLODE.value(), 2.5f, 0.5f);
                    h.sound(c, SoundEvents.COPPER_BREAK, 3f, 0.4f);
                    h.particles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.WEATHERED_COPPER.defaultBlockState()), c, 80, 6 * s + 1, 0.2);
                    h.particles(ParticleTypes.EXPLOSION, c, 6, 4 * s + 1, 0);
                    thump(c, 0.8f);
                    if (griefing()) crack(BlockPos.containing(c), (int) Math.ceil(8 * s + 1));
                }
            }
            case Moves.WRAP -> runWrap();
            case Moves.PULSE -> {
                if (t == Moves.PULSE_AT) {
                    h.pulse(1.6f);
                    h.sound(h.position().add(0, rig.rimY * s, 0), SoundEvents.WARDEN_SONIC_BOOM, 3f, 0.6f);
                    thump(h.position(), 0.6f);
                }
                if (t > Moves.PULSE_AT && t <= Moves.PULSE_AT + 24) {
                    // the shock goes out from the rim in a ring
                    double r0 = h.bellRadius();
                    double r = r0 + (t - Moves.PULSE_AT) / 24.0 * (60 * s + 26);
                    Vec3 c = h.position().add(0, rig.rimY * s, 0);
                    for (LivingEntity e : near(h.bodyBox().inflate(70 * s + 30, 80 * s + 40, 70 * s + 30))) {
                        if (struckThisMove.contains(e.getUUID())) continue;
                        double d = e.position().distanceTo(c);
                        if (d > r) continue;
                        struckThisMove.add(e.getUUID());
                        Vec3 out = e.position().subtract(c);
                        out = out.lengthSqr() < 0.01 ? new Vec3(0, 1, 0) : out.normalize();
                        e.hurt(h.damageSources().mobAttack(h), h.dmg(4f, e));
                        e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 140, 0));
                        e.setDeltaMovement(e.getDeltaMovement().add(out.x * 2.0, 0.4 + Math.max(0, out.y), out.z * 2.0));
                        // it knocks flying players out of the air
                        if (e instanceof ServerPlayer p && (p.isFallFlying() || p.getAbilities().flying)) {
                            p.stopFallFlying();
                            if (!p.isCreative() && p.getAbilities().flying) { p.getAbilities().flying = false; p.onUpdateAbilities(); }
                            p.setDeltaMovement(p.getDeltaMovement().x, -1.6, p.getDeltaMovement().z);
                            p.displayClientMessage(Component.translatable("message.hollowbell.knocked_down"), true);
                        }
                        e.hurtMarked = true;
                    }
                    if (t % 3 == 0) ring(c, r);
                }
            }
            case Moves.DROP -> {
                if (t == Moves.DROP_FALL) {
                    Vec3 c = h.position();
                    double r = h.bellRadius() + 4;
                    for (LivingEntity e : near(h.bodyBox().setMaxY(h.getY() + 40 * s + 6))) {
                        double d = h.horiz(e.position());
                        if (d > r) continue;
                        e.hurt(h.damageSources().mobAttack(h), h.dmg(18f, e));
                        Vec3 out = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
                        out = out.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : out.normalize();
                        e.setDeltaMovement(e.getDeltaMovement().add(out.x * 1.8, 0.8, out.z * 1.8));
                        e.hurtMarked = true;
                    }
                    h.sound(c, SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.35f);
                    h.sound(c, SoundEvents.ANVIL_LAND, 3f, 0.3f);
                    h.particles(ParticleTypes.CLOUD, c.add(0, 1, 0), 200, h.bellRadius(), 0.15);
                    thump(c, 1f);
                }
                if (t == Moves.DROP_FALL + Moves.DROP_DOWN) h.sound(h.position(), SoundEvents.CONDUIT_ACTIVATE, 3f, 0.5f);
            }
            case Moves.SHED -> {
                if (t == Moves.SHED_AT) shed();
            }
            default -> {}
        }
    }

    private boolean griefing() {
        return HollowbellConfig.V.griefing && level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    /** the ground cracks under the slam: grass and dirt go to coarse dirt, plants go */
    private void crack(BlockPos c, int r) {
        ServerLevel l = level();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (dx * dx + dz * dz > r * r || h.getRandom().nextInt(3) == 0) continue;
            BlockPos top = l.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.offset(dx, 0, dz));
            BlockPos below = top.below();
            BlockState st = l.getBlockState(below);
            if (st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.DIRT) || st.is(Blocks.PODZOL) || st.is(Blocks.MYCELIUM))
                l.setBlock(below, Blocks.COARSE_DIRT.defaultBlockState(), 3);
            BlockState up = l.getBlockState(top);
            if (!up.isAir() && up.canBeReplaced() && up.getFluidState().isEmpty()) l.destroyBlock(top, false);
        }
    }

    private void ring(Vec3 c, double r) {
        int n = (int) Math.min(90, 12 + r * 1.5);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            level().sendParticles(ParticleTypes.SONIC_BOOM, c.x + Math.cos(a) * r, c.y - 10 * s(), c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    private void thump(Vec3 c, float k) {
        if (!HollowbellConfig.V.screenShake) return;
        for (ServerPlayer p : level().players()) {
            if (p.distanceToSqr(c) > Mth.square(160 * s() + 60)) continue;
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
                h.sound(tip, SoundEvents.SLIME_SQUISH, 2.5f, 0.5f);
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
                grabbed.hurt(h.damageSources().mobAttack(h), h.dmg(2f, grabbed));
                sting(grabbed);
            }
            if (t % 20 == 0) h.sound(tip, SoundEvents.SLIME_JUMP, 1.5f, 0.4f);
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
                if (grabbed != null) grabbed.hurt(h.damageSources().mobAttack(h), h.dmg(8f, grabbed));
                letGoOfGrab(true);
                end();
                return;
            }
        }
        int slot = freeSlot();
        float k = Mth.clamp((t - into0) / (float) Moves.INTO, 0f, 1f);
        Vec3 to = slotWorld(slot, grabbed);
        Vec3 at = intoFrom.lerp(to, k * k * (3 - 2 * k));
        if (grabSeat != null) grabSeat.moveTo(at.x, at.y, at.z);
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
            h.sound(at, SoundEvents.AMETHYST_BLOCK_RESONATE, 2.5f, 0.5f);
            end();
        }
    }

    private boolean domeFits() { return 60 * s() >= 4f; }

    /** hits on the strand that has hold of something: enough and it lets go */
    public void strandHit(int strand, float amount, @Nullable Entity by) {
        if ((move != Moves.GRAB && move != Moves.HARVEST) || strand != arg || t < Moves.REACH) return;
        if (gentle) return;
        strandHits += 1f + amount / 10f;
        h.sound(h.strandTipWorld(strand), SoundEvents.BONE_BLOCK_HIT, 2f, 0.6f);
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
            h.sound(tip, SoundEvents.COPPER_PLACE, 2.5f, 0.5f);
        }
        if (t >= Moves.WRAP_REACH + Moves.WRAP_HOLD) { letGoOfWrap(); return; }
        if (wrapped != null && t % 20 == 0) {
            wrapped.hurt(h.damageSources().mobAttack(h), h.dmg(3f, wrapped));
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
                Vec3 tip = h.strandTipWorld(arg);
                if (grabSeat != null && grabbed != null) {
                    grabSeat.moveTo(tip.x, tip.y - grabbed.getBbHeight() * 0.6, tip.z);
                    if (grabbed.getVehicle() != grabSeat) grabbed.startRiding(grabSeat, true);
                    grabbed.fallDistance = 0;
                }
                if (grabbedTree != null) placeTree(grabbedTree, tip.add(0, -1, 0));
            }
        }
        if (wrapSeat != null && wrapped != null && arg >= 0) {
            Vec3 tip = h.armTipWorld(arg);
            wrapSeat.moveTo(tip.x, tip.y - wrapped.getBbHeight() * 0.5, tip.z);
            if (wrapped.getVehicle() != wrapSeat) wrapped.startRiding(wrapSeat, true);
            wrapped.fallDistance = 0;
        }
        for (Inside in : inside) {
            Vec3 at = slotWorld(in.slot, in.e);
            if (in.seat != null && in.e != null) {
                in.seat.moveTo(at.x, at.y, at.z);
                if (in.e.getVehicle() != in.seat) in.e.startRiding(in.seat, true);
                in.e.fallDistance = 0;
            }
            if (in.tree != null) placeTree(in.tree, at);
        }
    }

    // ------------------------------------------------------------------ inside the dome

    /** places inside the dome to hang things: the first four right under the glowing balls, then round the vase */
    private static final int SLOTS = 14;
    private int slotCount() { return SLOTS; }

    private Vector3f slotModel(int i) {
        if (i < 4) {
            var S = rig.spots[i];
            return new Vector3f(S.centre().x * 0.95f, 140f, S.centre().z * 0.95f);
        }
        if (i < 8) {
            // between the balls, close to the vase
            double a = Math.atan2(rig.spots[i - 4].centre().z, rig.spots[i - 4].centre().x) + Math.PI / 4;
            return new Vector3f((float) (Math.cos(a) * 26), 150f, (float) (Math.sin(a) * 26));
        }
        double a = (i - 8) * Math.PI * 2 / (SLOTS - 8) + 0.3;
        return new Vector3f((float) (Math.cos(a) * 58), 142f, (float) (Math.sin(a) * 58));
    }

    /** where the feet of whatever is in slot i go, in the world */
    private Vec3 slotWorld(int i, @Nullable LivingEntity who) {
        Vec3 w = h.boneWorld(rig.bellBone, slotModel(i));
        if (i < 4) {
            // right under the ball, so it is in reach over your head
            Vec3 ballBottom = h.boneWorld(rig.spots[i].bone(), new Vector3f(rig.spots[i].centre().x, rig.spots[i].centre().y - 15f, rig.spots[i].centre().z));
            w = new Vec3(ballBottom.x, ballBottom.y - 2.6, ballBottom.z);
        } else if (i < 8) {
            w = w.add(0, -1, 0);
        }
        return w;
    }

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
                // it hurts in here
                if (in.age % 40 == 0 && !(in.e instanceof Player p && p.isCreative())) {
                    in.e.hurt(h.damageSources().mobAttack(h), in.e instanceof Player ? h.dmg(2f, in.e) : 2f);
                    if (in.e instanceof Player) in.e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
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
            h.sound(h.position().add(0, rig.rimY * s(), 0), SoundEvents.WARDEN_HURT, 3f, 0.5f);
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
        e.hurt(h.damageSources().mobAttack(h), h.dmg(1f, e));
        h.sound(e.position(), SoundEvents.BEE_STING, 1f, 0.5f);
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
        if (n > 0) h.sound(h.position().add(0, 50 * s(), 0), SoundEvents.TURTLE_EGG_HATCH, 3f, 0.6f);
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
