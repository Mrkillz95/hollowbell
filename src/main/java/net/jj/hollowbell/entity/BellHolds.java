package net.jj.hollowbell.entity;

import com.mojang.math.Transformation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.jj.hollowbell.Adv;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.ModSounds;
import net.jj.hollowbell.fx.BigFx;
import net.jj.hollowbell.rig.BellRig;
import net.jj.hollowbell.rig.CarryPath;
import net.jj.hollowbell.world.NoWait;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
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
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * What his strands and arms hold: grabbing, carrying up and setting down, the wrap, the inside of his dome, and trees.
 */
final class BellHolds {
    private final BellMoves mv;
    private final HollowbellEntity h;
    private final BellRig rig;

    BellHolds(BellMoves mv) {
        this.mv = mv;
        this.h = mv.h;
        this.rig = mv.rig;
    }

    void runGrab() {
        if (mv.gentle) {
            runCarry();
            return;
        }
        float s = mv.s();
        Vec3 tip = h.strandTipWorld(mv.arg);
        if (mv.t < Moves.REACH) {
            // it follows what it's reaching for
            if (mv.target != null && mv.target.isAlive()) h.setAim(h.toModel(mv.aimAt(mv.target)));
            if (mv.target == null && mv.treeAt == null) {
                mv.end();
                return;
            }
            return;
        }
        if (mv.t == Moves.REACH) {
            if (mv.target != null) {
                // got it if it's within reach of the strand's end (or right under it)
                double reach = 14 * s + 4;
                if (!mv.target.isAlive()
                        || !HollowbellEntity.canCarry(mv.target)
                        || (mv.target.position().distanceTo(tip) > reach
                                && h.horiz(mv.target.position()) > h.bellRadius())) {
                    mv.end();
                    return;
                }
                mv.grabbed = mv.target;
                mv.grabSeat = seat(mv.grabbed, tip);
                h.sound(tip, ModSounds.GRAB, 2.5f, 0.8f);
                if (mv.grabbed instanceof ServerPlayer sp)
                    sp.displayClientMessage(
                            Component.translatable(
                                    mv.gentle ? "message.hollowbell.lifting_you" : "message.hollowbell.grabbed"),
                            true);
            } else if (mv.treeAt != null) {
                BlockPos base = mv.treeAt;
                int wood = Block.getId(mv.level().getBlockState(base));
                mv.grabbedTree = pullUpTree(mv.treeAt, tip);
                mv.treeAt = null;
                if (mv.grabbedTree == null) {
                    mv.end();
                    return;
                }
                // the roots tear out
                Vec3 b = Vec3.atBottomCenterOf(base);
                mv.fx(BigFx.DEBRIS, b, mv.px(4, 2), new Vec3(0, 1, 0), wood);
                mv.fx(BigFx.DUST, b, mv.px(5, 2));
            }
            mv.intoFrom = tip;
        }
        int into0 = Moves.REACH + Moves.LIFT;
        if (mv.t < into0) {
            mv.lift = Mth.clamp((mv.t - Moves.REACH) / (float) Moves.LIFT, 0f, 1f);
            h.setLift(mv.lift);
            // everything is carried after the pose (afterPose); here only the sting of being held
            if (mv.grabbed != null
                    && !mv.gentle
                    && mv.t % 30 == 0
                    && !(mv.grabbed instanceof Player p && p.isCreative())) {
                if (mv.grabbed instanceof Player) {
                    mv.blow(mv.grabbed, 4f, tip, 0, 0);
                    mv.sting(mv.grabbed);
                } else {
                    // a creature is squeezed hard but kept alive until it's in the dome, so you see it taken in
                    float d = Math.min(h.dmg(7f, mv.grabbed), mv.grabbed.getHealth() - 1f);
                    mv.grabbed.invulnerableTime = 0;
                    if (d > 0) mv.grabbed.hurt(h.damageSources().mobAttack(h), d);
                    mv.grabbed.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                    h.sound(mv.grabbed.position(), ModSounds.STING, 1f, 1f);
                }
            }
            if (mv.t % 20 == 0) h.sound(tip, ModSounds.STRAND, 1.5f, 0.7f);
            return;
        }
        if (mv.t == into0) {
            if (mv.grabbed != null && mv.grabSeat != null) mv.intoFrom = mv.grabSeat.position();
            // everything goes in the dome, if it fits
            if (!domeFits() || !BellConfig.V.insideDome || mv.inside.size() >= mv.slotCount()) {
                // too small (or not allowed) to take it in: a squeeze, and it's let go
                if (mv.grabbed != null) mv.blow(mv.grabbed, 12f, h.position(), 0, 0);
                letGoOfGrab(true);
                mv.end();
                return;
            }
        }
        int slot = freeSlot();
        float k = Mth.clamp((mv.t - into0) / (float) Moves.INTO, 0f, 1f);
        Vec3 to = mv.slotWorld(slot, mv.grabbed);
        Vec3 at = mv.intoFrom.lerp(to, k * k * (3 - 2 * k));
        if (mv.grabSeat != null) {
            mv.grabSeat.follow(Seat.FREE, 0);
            mv.grabSeat.moveTo(at.x, at.y, at.z);
        }
        if (mv.grabbedTree != null) placeTree(mv.grabbedTree, at);
        if (k >= 1f && mv.grabbed == null && mv.grabbedTree == null) {
            // let go of on the way up (the book's "let go"): nothing to keep in there
            mv.end();
            return;
        }
        if (k >= 1f) {
            BellMoves.Inside in = new BellMoves.Inside();
            in.e = mv.grabbed;
            in.seat = mv.grabSeat;
            in.tree = mv.grabbedTree;
            in.slot = slot;
            mv.inside.add(in);
            if (mv.grabbed instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.hollowbell.inside"), false);
                Adv.grant(sp, "inside");
            }
            mv.grabbed = null;
            mv.grabSeat = null;
            mv.grabbedTree = null;
            h.sound(at, ModSounds.ECHO, 2.5f, 1f);
            mv.end();
        }
    }

    boolean domeFits() {
        return 60 * mv.s() >= 4f;
    }

    /**
     * "Ride him": a strand reaches out and takes the one who asked round the middle (its end right on them, so they
     * never jump to it), then carries them up his side, round the rim and over the dome, and lets them down onto
     * the crown, where they stay on the same seat. Then the strand goes back the way it came and hangs again.
     * The way (CarryPath) keeps them a set distance from every part of him. Both sides lay the strand out from the
     * same numbers (the grab's phase, 2 + how far up, and the carry's size in DATA_CARRY); the seat follows the end
     * of the strand on each side.
     */
    boolean startCarry(LivingEntity who) {
        if (h.sunk()
                || h.isDeadOrDying()
                || mv.grabbed != null
                || mv.grabbedTree != null
                || !HollowbellEntity.canCarry(who)
                || who.isPassenger() && !(who.getVehicle() instanceof Seat)) return false;
        int k = mv.fetchStrand >= 0 ? mv.fetchStrand : bestCarryStrand(who, -1);
        if (k < 0) return false;
        if (mv.move != Moves.NONE) mv.end();
        mv.target = who;
        mv.struckThisMove.clear();
        mv.hitAt.clear();
        mv.slapped.clear();
        mv.move = Moves.GRAB;
        mv.t = 0;
        mv.arg = k;
        mv.lastArg = k;
        mv.landed = false;
        mv.lastMove = Moves.GRAB;
        mv.grabbed = null;
        mv.lift = 0f;
        mv.strandHits = 0f;
        mv.gentle = true;
        mv.back = false;
        mv.carryT = 0;
        mv.carryLen = 0;
        mv.backT = 0;
        mv.backLen = 0;
        mv.carryU = 0f;
        mv.reachFrom = who.position();
        mv.reachHp = who.getHealth();
        mv.reachHurt = who.hurtTime;
        h.setMove(Moves.GRAB, k, h.toModel(BellMoves.middle(who)));
        h.setLift(Moves.CARRY_PHASE);
        h.setCarry(mv.carryD(who), mv.carryEnd(who));
        mv.warn(Moves.GRAB, k);
        return true;
    }

    /**
     * Lets go of one creature, however he has it (a player leaving the game): a strand holding it lets go (one
     * carrying it up onto his crown goes back down the way it came), an arm unwraps, and it's taken out of his dome.
     */
    public void letGo(LivingEntity e) {
        if (mv.grabbed == e) {
            letGoOfGrab(false);
            if (!mv.gentle) mv.end();
        }
        if (mv.wrapped == e) letGoOfWrap();
        for (Iterator<BellMoves.Inside> it = mv.inside.iterator(); it.hasNext(); ) {
            BellMoves.Inside in = it.next();
            if (in.e != e) continue;
            dropSeat(in.seat);
            it.remove();
        }
        if (mv.gentle && mv.move == Moves.GRAB && mv.target == e) {
            if (mv.t <= Moves.REACH) mv.end();
            else mv.target = null;
        }
    }

    /** stop a carry up to the crown: whoever is on the way up is let go gently, and the strand goes back */
    public void cancelCarry() {
        if (!mv.gentle || mv.move != Moves.GRAB || mv.back) return;
        if (mv.t < Moves.REACH) {
            mv.end();
            return;
        }
        if (mv.grabbed != null) letGoOfGrab(true);
    }

    /** where he goes to pick somebody up: them just outside his arms, in line with the strand that will carry them */
    public Vec3 fetchSpot(LivingEntity who) {
        if (mv.fetchStrand < 0 || h.tickCount % 20 == 0) mv.fetchStrand = bestCarryStrand(who, mv.fetchStrand);
        return mv.fetchStrand < 0 ? who.position() : spotFor(mv.fetchStrand, who);
    }

    Vec3 spotFor(int k, LivingEntity who) {
        var cp = mv.carryPath(k, who);
        float s = mv.s();
        float r = cp.radiusAt((float) (who.getBbHeight() * 0.5 / s));
        Vec3 off = h.toWorld(new Vector3f(r * (float) Math.cos(cp.theta), 0, r * (float) Math.sin(cp.theta)))
                .subtract(h.position());
        return new Vec3(who.getX() - off.x, who.getY(), who.getZ() - off.z);
    }

    /**
     * of the strands that can carry somebody, the one he has least far to go to use (keeping the one he has unless
     * another is a lot nearer)
     */
    int bestCarryStrand(LivingEntity who, int now) {
        int best = -1;
        double bd = Double.MAX_VALUE, nowD = Double.MAX_VALUE;
        for (int k : CarryPath.candidates(rig)) {
            double d = h.horiz(spotFor(k, who));
            if (k == now) nowD = d;
            if (d < bd) {
                bd = d;
                best = k;
            }
        }
        if (now >= 0 && nowD < bd + 5 + 10 * mv.s()) return now;
        return best;
    }

    void runCarry() {
        if (mv.down) {
            runSetDown();
            return;
        }
        LivingEntity who = mv.target;
        float s = mv.s();
        if (mv.t <= Moves.REACH) {
            if (who == null
                    || !who.isAlive()
                    || who.isRemoved()
                    || who.level() != h.level()
                    || !HollowbellEntity.canCarry(who)) {
                mv.end();
                return;
            }
            // it misses if they walk off, and lets go if they're hurt while it reaches: he tries again in a moment
            boolean moved = who.position().distanceTo(mv.reachFrom) > 2.5 + 5 * s;
            boolean hurt = who.getHealth() < mv.reachHp - 0.01f || who.hurtTime > mv.reachHurt;
            mv.reachHurt = who.hurtTime;
            if (moved || hurt) {
                if (who instanceof ServerPlayer sp)
                    sp.displayClientMessage(
                            Component.translatable(
                                    hurt ? "message.hollowbell.carry_hurt" : "message.hollowbell.carry_moved"),
                            true);
                mv.fetchPause = 60;
                mv.end();
                return;
            }
            // the end of the strand goes to the middle of them (the pose this tick puts it right there at REACH)
            h.setAim(h.toModel(BellMoves.middle(who)));
            if (mv.t < Moves.REACH) return;
            // got them: they sit on a seat right where they are, so they don't move at all as it closes round them
            mv.grabbed = who;
            Seat st = new Seat(mv.level(), h);
            Vec3 at = who.position().add(0, who.getVehicleAttachmentPoint(st).y, 0);
            st.setPos(at.x, at.y, at.z);
            st.follow(Seat.CARRY, mv.arg);
            mv.level().addFreshEntity(st);
            who.stopRiding();
            who.startRiding(st, true);
            mv.grabSeat = st;
            // how long the way up takes: about a block a tick at his full size, slower when he's small
            var cp = mv.carryPath(mv.arg, who);
            h.ensurePose();
            float len =
                    cp.new Run(h.restOf(h.toModel(BellMoves.middle(who))), rig.crownY + 1 + mv.carryEnd(who)).length()
                            * s;
            mv.carryLen = Mth.clamp(Mth.ceil(len / (0.12f + 1.0f * s)), 60, 20 * 30);
            mv.carryT = 0;
            mv.carryU = 0f;
            h.sound(BellMoves.middle(who), ModSounds.GRAB, 2f, 1.1f);
            if (who instanceof ServerPlayer sp)
                sp.displayClientMessage(Component.translatable("message.hollowbell.lifting_you"), true);
            return;
        }
        if (!mv.back) {
            if (mv.grabbed == null
                    || !mv.grabbed.isAlive()
                    || mv.grabbed.isRemoved()
                    || mv.grabSeat == null
                    || mv.grabSeat.isRemoved()
                    || mv.grabbed.getVehicle() != mv.grabSeat) {
                // they're gone (or were let go): the strand goes back down from where it got to
                if (mv.grabbed != null) letGoOfGrab(true);
                goBack();
                return;
            }
            mv.carryT++;
            mv.carryU = ease(mv.carryT / (float) mv.carryLen);
            h.setLift(Moves.CARRY_PHASE + mv.carryU);
            if (mv.carryT % 20 == 0) h.sound(h.strandTipWorld(mv.arg), ModSounds.STRAND, 1.2f, 0.8f);
            if (mv.carryT >= mv.carryLen) {
                // on the crown: the seat they came up on becomes the crown seat
                LivingEntity rideOn = mv.grabbed;
                Seat keep = mv.grabSeat;
                mv.grabbed = null;
                mv.grabSeat = null;
                h.seatOnCrown(rideOn, keep);
                goBack();
            }
            return;
        }
        mv.backT++;
        mv.carryU = mv.backFrom * (1f - ease(mv.backT / (float) mv.backLen));
        h.setLift(Moves.CARRY_PHASE + mv.carryU);
        if (mv.backT >= mv.backLen) mv.end();
    }

    /** starts setting the rider down: picks the strand and the spot on the ground for them. False if there's nowhere */
    public boolean startSetDown(LivingEntity who) {
        if (h.sunk() || h.isDeadOrDying()) return false;
        float s = mv.s();
        double best = Double.MAX_VALUE;
        mv.downStrand = -1;
        mv.downSpot = null;
        Vec3 look = who.getLookAngle();
        for (int k : CarryPath.candidates(rig)) {
            var cp = mv.carryPath(k, who);
            float r = cp.radiusAt((float) (who.getBbHeight() * 0.5 / s));
            Vec3 off = h.toWorld(new Vector3f(r * (float) Math.cos(cp.theta), 0, r * (float) Math.sin(cp.theta)))
                    .subtract(h.position());
            double x = h.getX() + off.x, z = h.getZ() + off.z;
            BlockPos col = BlockPos.containing(x, h.getY(), z);
            if (!NoWait.loaded(mv.level(), col)) continue;
            double y = NoWait.standY(mv.level(), col.getX(), col.getZ(), who.getBlockY());
            AABB box = who.getDimensions(Pose.STANDING).makeBoundingBox(x, y, z);
            if (!mv.level().noCollision(who, box)
                    || mv.level().containsAnyLiquid(box)
                    || mv.level().containsAnyLiquid(box.move(0, -1, 0))) continue;
            // the side they're looking at, on ground as level with where he is as can be
            double score = Math.abs(y - h.groundAt(h.getX(), h.getZ())) * 4
                    - off.normalize().dot(new Vec3(look.x, 0, look.z)) * 10;
            if (score < best) {
                best = score;
                mv.downStrand = k;
                mv.downSpot = new Vec3(x, y, z);
            }
        }
        if (mv.downStrand < 0) return false;
        mv.downWho = who;
        mv.downWait = 0;
        if (who instanceof ServerPlayer sp)
            sp.displayClientMessage(Component.translatable("message.hollowbell.setting_down"), true);
        return true;
    }

    void startCarryDown() {
        LivingEntity who = mv.downWho;
        mv.downWho = null;
        if (who == null || mv.downSpot == null || mv.downStrand < 0) return;
        mv.target = null;
        mv.struckThisMove.clear();
        mv.hitAt.clear();
        mv.slapped.clear();
        mv.move = Moves.GRAB;
        mv.t = 0;
        mv.arg = mv.downStrand;
        mv.lastArg = mv.downStrand;
        mv.landed = false;
        mv.lastMove = Moves.GRAB;
        mv.grabbed = null;
        mv.grabSeat = null;
        mv.lift = 0f;
        mv.strandHits = 0f;
        mv.gentle = true;
        mv.down = true;
        mv.back = false;
        mv.carryT = 0;
        mv.carryLen = 0;
        mv.backT = 0;
        mv.backLen = 0;
        mv.carryU = 0f;
        mv.carryPhase = 0;
        mv.downWho = who;
        // the strand's end goes to where the middle of them will be, standing on the spot; he stays at this height
        h.holdHeight();
        h.setMove(Moves.GRAB, mv.arg, h.toModel(mv.downSpot.add(0, who.getBbHeight() * 0.5, 0)));
        h.setLift(Moves.CARRY_PHASE);
        h.setCarry(mv.carryD(who), mv.carryEnd(who));
        mv.warn(Moves.GRAB, mv.arg);
        mv.downWho = null;
        mv.carryWho = who;
    }

    void runSetDown() {
        float s = mv.s();
        if (mv.carryPhase == 0) {
            // reaching out to the spot on the ground first
            if (mv.carryWho == null || h.rider() != mv.carryWho) {
                mv.end();
                return;
            }
            if (mv.t < Moves.REACH) return;
            var cp = mv.carryPath(mv.arg, mv.carryWho);
            h.ensurePose();
            float len = cp.new Run(h.restOf(h.aim()), rig.crownY + 1 + mv.carryEnd(mv.carryWho)).length() * s;
            mv.carryLen = Mth.clamp(Mth.ceil(len / (0.12f + 1.0f * s)), 60, 20 * 30);
            mv.carryPhase = BellMoves.PHASE_UP;
            mv.carryT = 0;
            return;
        }
        if (mv.carryPhase == BellMoves.PHASE_UP && !mv.back) {
            // up his side, round the rim and over the dome to the crown, empty
            int upLen = mv.carryLen;
            mv.carryT++;
            mv.carryU = ease(mv.carryT / (float) upLen);
            h.setLift(Moves.CARRY_PHASE + mv.carryU);
            if (mv.carryT % 20 == 0) h.sound(h.strandTipWorld(mv.arg), ModSounds.STRAND, 1.2f, 0.8f);
            if (mv.carryT < upLen) return;
            // at the crown: the seat they ride on becomes the strand's, so they're never taken off it
            LivingEntity who = mv.carryWho;
            Seat st = who != null && h.rider() == who ? h.handRiderToStrand() : null;
            mv.carryWho = null;
            if (st == null) {
                goBack();
                return;
            }
            mv.grabbed = who;
            mv.grabSeat = st;
            st.follow(Seat.CARRY, mv.arg);
            mv.carryPhase = BellMoves.PHASE_DOWN;
            mv.carryT = 0;
            h.sound(h.crownWorld(), ModSounds.GRAB, 2f, 1.1f);
            return;
        }
        if (mv.carryPhase == BellMoves.PHASE_DOWN && !mv.back) {
            if (mv.grabbed == null
                    || !mv.grabbed.isAlive()
                    || mv.grabbed.isRemoved()
                    || mv.grabSeat == null
                    || mv.grabSeat.isRemoved()
                    || mv.grabbed.getVehicle() != mv.grabSeat) {
                if (mv.grabbed != null) letGoOfGrab(true);
                goBack();
                return;
            }
            // back down the way they came up, and on to their feet on the spot
            mv.carryT++;
            mv.carryU = 1f - ease(mv.carryT / (float) mv.carryLen);
            h.setLift(Moves.CARRY_PHASE + mv.carryU);
            if (mv.carryT % 20 == 0) h.sound(h.strandTipWorld(mv.arg), ModSounds.STRAND, 1.2f, 0.8f);
            if (mv.carryT < mv.carryLen) return;
            LivingEntity who = mv.grabbed;
            Seat st = mv.grabSeat;
            mv.grabbed = null;
            mv.grabSeat = null;
            dropSeat(st);
            who.fallDistance = 0;
            who.setDeltaMovement(Vec3.ZERO);
            who.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false));
            if (who instanceof ServerPlayer sp)
                sp.displayClientMessage(Component.translatable("message.hollowbell.set_down"), true);
            mv.end();
            return;
        }
        // the strand going back down empty (they got off some other way)
        mv.backT++;
        mv.carryU = mv.backFrom * (1f - ease(mv.backT / (float) mv.backLen));
        h.setLift(Moves.CARRY_PHASE + mv.carryU);
        if (mv.backT >= mv.backLen) mv.end();
    }

    void goBack() {
        mv.back = true;
        mv.backFrom = mv.carryU;
        mv.backT = 0;
        mv.backLen = Math.max(30, Math.round(mv.carryLen * 0.7f * mv.backFrom));
    }

    /** how far along a carry is at k (0-1) of its time: easing in and out, steady in the middle */
    static float ease(float k) {
        k = Mth.clamp(k, 0f, 1f);
        float a = 0.15f;
        float v = 1f / (1f - a);
        if (k < a) return 0.5f * v * k * k / a;
        if (k > 1f - a) {
            float r = 1f - k;
            return 1f - 0.5f * v * r * r / a;
        }
        return 0.5f * v * a + v * (k - a);
    }

    /** hits on the strand that has hold of something: enough and it lets go */
    public void strandHit(int strand, float amount, @Nullable Entity by) {
        if ((mv.move != Moves.GRAB && mv.move != Moves.HARVEST) || strand != mv.arg || mv.t < Moves.REACH) return;
        if (mv.gentle) return;
        mv.strandHits += 1f + amount / 10f;
        h.sound(h.strandTipWorld(strand), ModSounds.STRAND, 2f, 1.1f);
        if (mv.strandHits >= 4f) {
            if (by instanceof ServerPlayer sp)
                sp.displayClientMessage(Component.translatable("message.hollowbell.strand_let_go"), true);
            letGoOfGrab(true);
            mv.end();
        }
    }

    public void armHit(int arm, float amount, @Nullable Entity by) {
        if (mv.move != Moves.WRAP || arm != mv.arg || mv.wrapped == null) return;
        mv.armHits += 1f + amount / 10f;
        if (mv.armHits >= 4f) {
            if (by instanceof ServerPlayer sp)
                sp.displayClientMessage(Component.translatable("message.hollowbell.arm_let_go"), true);
            letGoOfWrap();
            mv.end();
        }
    }

    void letGoOfGrab(boolean fall) {
        if (mv.grabbed != null) {
            LivingEntity e = mv.grabbed;
            dropSeat(mv.grabSeat);
            h.floatDown(e);
            if (e instanceof ServerPlayer sp && fall)
                sp.displayClientMessage(Component.translatable("message.hollowbell.dropped"), true);
        }
        if (mv.grabbedTree != null) dropTree(mv.grabbedTree);
        mv.grabbed = null;
        mv.grabSeat = null;
        mv.grabbedTree = null;
        mv.lift = 0f;
    }

    void runWrap() {
        Vec3 tip = h.armTipWorld(mv.arg);
        if (mv.t < Moves.WRAP_REACH) {
            if (mv.target != null && mv.target.isAlive()) h.setAim(h.toModel(mv.aimAt(mv.target)));
            return;
        }
        if (mv.t == Moves.WRAP_REACH) {
            if (mv.target == null
                    || !mv.target.isAlive()
                    || !HollowbellEntity.canCarry(mv.target)
                    || mv.target.position().distanceTo(tip) > 30 * mv.s() + 6) {
                mv.end();
                return;
            }
            mv.wrapped = mv.target;
            mv.wrapSeat = seat(mv.wrapped, tip);
            h.sound(tip, ModSounds.GRAB, 2.5f, 0.6f);
        }
        if (mv.t >= Moves.WRAP_REACH + Moves.WRAP_HOLD) {
            letGoOfWrap();
            return;
        }
        if (mv.wrapped != null && mv.t % 20 == 0) {
            mv.blow(mv.wrapped, 8f, tip, 0, 0);
            mv.wrapped.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3));
            h.sound(tip, SoundEvents.COPPER_STEP, 2f, 0.5f);
            mv.fx(BigFx.SPARKS, tip, mv.px(2, 1), Vec3.ZERO, 0);
        }
    }

    void letGoOfWrap() {
        if (mv.wrapped != null) {
            dropSeat(mv.wrapSeat);
            h.floatDown(mv.wrapped);
        }
        mv.wrapped = null;
        mv.wrapSeat = null;
    }

    Seat seat(LivingEntity e, Vec3 at) {
        Seat st = new Seat(mv.level(), h);
        st.setPos(at.x, at.y - e.getBbHeight() * 0.5, at.z);
        mv.level().addFreshEntity(st);
        e.stopRiding();
        e.startRiding(st, true);
        return st;
    }

    void dropSeat(@Nullable Seat st) {
        if (st == null) return;
        for (Entity e : st.getPassengers()) {
            e.stopRiding();
            e.fallDistance = 0;
        }
        st.discard();
    }

    int freeSlot() {
        boolean[] used = new boolean[BellMoves.SLOTS];
        for (BellMoves.Inside in : mv.inside) if (in.slot >= 0 && in.slot < BellMoves.SLOTS) used[in.slot] = true;
        // players get the places in reach of the glowing spots, the rest go further out
        boolean player = mv.grabbed instanceof Player;
        if (player) for (int i = 0; i < 8; i++) if (!used[i]) return i;
        for (int i = 8; i < BellMoves.SLOTS; i++) if (!used[i]) return i;
        for (int i = 0; i < BellMoves.SLOTS; i++) if (!used[i]) return i;
        return 0;
    }

    void insideTick() {
        for (Iterator<BellMoves.Inside> it = mv.inside.iterator(); it.hasNext(); ) {
            BellMoves.Inside in = it.next();
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
            } else if (in.tree == null) {
                it.remove();
            }
        }
    }

    /** a hit from inside: hurt him enough and he lets go of everyone in there */
    public void hurtFromInside(float dealt) {
        mv.hurtInside += dealt;
        if (mv.hurtInside >= h.healthMax() * 0.04f) {
            mv.hurtInside = 0f;
            h.sound(h.position().add(0, rig.rimY * mv.s(), 0), ModSounds.HURT_HEAVY, 3f, 1f);
            dropEveryoneInside();
        }
    }

    public void dropEveryoneInside() {
        for (Iterator<BellMoves.Inside> it = mv.inside.iterator(); it.hasNext(); ) {
            BellMoves.Inside in = it.next();
            if (in.e instanceof Player p) {
                dropSeat(in.seat);
                h.floatDown(p);
                if (p instanceof ServerPlayer sp)
                    sp.displayClientMessage(Component.translatable("message.hollowbell.spat_out"), true);
                it.remove();
            }
        }
    }

    /** everything he holds goes, in or out. Trees drop as their blocks, or (put away with his chunk) just go */
    public void letGoOfEverything(boolean dropTrees) {
        letGoOfGrab(false);
        letGoOfWrap();
        for (BellMoves.Inside in : mv.inside) {
            if (in.e != null) {
                dropSeat(in.seat);
                h.floatDown(in.e);
            }
            if (in.tree != null) {
                if (dropTrees) dropTree(in.tree);
                else removeTree(in.tree);
            }
        }
        mv.inside.clear();
    }

    @Nullable
    LivingEntity findHarvestMob() {
        double r = h.bellRadius() * 1.1;
        List<LivingEntity> l = mv.level()
                .getEntitiesOfClass(
                        LivingEntity.class,
                        h.bodyBox().setMaxY(h.getY() + 20 * mv.s() + 4),
                        e -> (e instanceof Animal
                                        || e instanceof AbstractVillager
                                        || (e instanceof Mob
                                                && !(e instanceof HollowbellEntity)
                                                && !(e instanceof Belling)))
                                && h.fairGame(e)
                                && HollowbellEntity.canCarry(e)
                                && h.horiz(e.position()) < r
                                && !e.isPassenger());
        if (l.isEmpty()) return null;
        return l.get(h.getRandom().nextInt(l.size()));
    }

    @Nullable
    BlockPos findTree() {
        if (!mv.griefing()) return null;
        ServerLevel l = mv.level();
        double r = h.bellRadius();
        for (int tries = 0; tries < 40; tries++) {
            double a = h.getRandom().nextDouble() * Math.PI * 2,
                    d = Math.sqrt(h.getRandom().nextDouble()) * r;
            BlockPos col = BlockPos.containing(h.getX() + Math.cos(a) * d, h.getY(), h.getZ() + Math.sin(a) * d);
            if (!NoWait.loaded(l, col)) continue; // (never waits for land being made)
            BlockPos top = l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, col);
            // look down through the leaves for a trunk
            for (int dy = 0; dy < 12; dy++) {
                BlockPos p = top.below(dy);
                if (!NoWait.loaded(l, p)) break;
                BlockState st = l.getBlockState(p);
                if (st.is(BlockTags.LOGS)) {
                    BlockPos base = p;
                    while (l.getBlockState(base.below()).is(BlockTags.LOGS) && base.getY() > l.getMinBuildHeight())
                        base = base.below();
                    return base;
                }
                if (!st.is(BlockTags.LEAVES) && !st.isAir()) break;
            }
        }
        return null;
    }

    /** takes the tree out of the ground as a set of block displays, at most 96 blocks of it */
    @Nullable
    BellMoves.Tree pullUpTree(BlockPos base, Vec3 at) {
        ServerLevel l = mv.level();
        if (!l.getBlockState(base).is(BlockTags.LOGS)) return null;
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(base);
        seen.add(base);
        while (!q.isEmpty() && found.size() < 96) {
            BlockPos p = q.poll();
            BlockState st = l.getBlockState(p);
            if (!(st.is(BlockTags.LOGS) || st.is(BlockTags.LEAVES))) continue;
            found.add(p);
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = p.offset(dx, dy, dz);
                        if (n.getY() < base.getY()
                                || Math.abs(n.getX() - base.getX()) > 5
                                || Math.abs(n.getZ() - base.getZ()) > 5
                                || n.getY() - base.getY() > 14) continue;
                        if (seen.add(n)) q.add(n);
                    }
        }
        if (found.isEmpty()) return null;
        BellMoves.Tree tr = new BellMoves.Tree();
        // a tree is a lot bigger than a small Hollowbell's dome: it shrinks to fit
        tr.size = Mth.clamp(mv.s() * 2.5f, 0.12f, 1f);
        for (BlockPos p : found) {
            BlockState st = l.getBlockState(p);
            Display.BlockDisplay d = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, l);
            d.setBlockState(st);
            d.setPosRotInterpolationDuration(3);
            Vector3f off = new Vector3f(
                            p.getX() - base.getX() - 0.5f, p.getY() - base.getY(), p.getZ() - base.getZ() - 0.5f)
                    .mul(tr.size);
            d.setTransformation(
                    new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(tr.size), new Quaternionf()));
            d.setPos(at.x + off.x, at.y + off.y, at.z + off.z);
            d.addTag(BellMoves.TREE_TAG);
            BellMoves.LIVE_TREES.add(d);
            l.addFreshEntity(d);
            tr.blocks.add(d);
            tr.offsets.add(off);
            tr.states.add(st);
            l.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        h.sound(Vec3.atCenterOf(base), SoundEvents.ROOTED_DIRT_BREAK, 3f, 0.5f);
        h.particles(
                new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                Vec3.atCenterOf(base),
                40,
                2,
                0.1);
        return tr;
    }

    void placeTree(BellMoves.Tree tr, Vec3 at) {
        for (int i = 0; i < tr.blocks.size(); i++) {
            Display.BlockDisplay d = tr.blocks.get(i);
            if (d.isRemoved()) continue;
            Vector3f o = tr.offsets.get(i);
            // hung from the top: the base hangs lowest
            d.setPos(at.x + o.x, at.y + o.y - 14 * tr.size, at.z + o.z);
        }
    }

    /** the tree comes down as the blocks it was made of */
    void dropTree(BellMoves.Tree tr) {
        ServerLevel l = mv.level();
        Vec3 c = h.position();
        for (int i = 0; i < tr.blocks.size(); i++) {
            Display.BlockDisplay d = tr.blocks.get(i);
            BlockState st = tr.states.get(i);
            if (st.is(BlockTags.LOGS) || h.getRandom().nextInt(6) == 0) {
                double x = c.x + (h.getRandom().nextDouble() - 0.5) * 6,
                        z = c.z + (h.getRandom().nextDouble() - 0.5) * 6;
                l.addFreshEntity(new ItemEntity(
                        l,
                        x,
                        h.groundAt(x, z) + 0.5,
                        z,
                        new ItemStack(st.getBlock().asItem())));
            }
            d.discard();
        }
        tr.blocks.clear();
    }

    /** a block of a tree he held, loaded with its chunk after he was put away: it drops where it hangs */
    public static void loadedTree(Entity e, ServerLevel l) {
        if (!(e instanceof Display.BlockDisplay d)
                || !d.getTags().contains(BellMoves.TREE_TAG)
                || BellMoves.LIVE_TREES.contains(d)) return;
        BlockState st = d.getBlockState();
        // (not while the chunk is still being put in place)
        l.getServer().tell(new TickTask(l.getServer().getTickCount(), () -> {
            if (d.isRemoved()) return;
            if (st.is(BlockTags.LOGS))
                l.addFreshEntity(new ItemEntity(
                        l,
                        d.getX(),
                        d.getY(),
                        d.getZ(),
                        new ItemStack(st.getBlock().asItem())));
            d.discard();
        }));
    }

    void removeTree(BellMoves.Tree tr) {
        for (Display.BlockDisplay d : tr.blocks) d.discard();
        tr.blocks.clear();
    }
}
