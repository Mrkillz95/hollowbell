package net.jj.hollowbell.entity;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.net.BeingHimPayload;
import net.jj.hollowbell.solid.Solid;
import net.jj.hollowbell.world.NoWait;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/** Riding his crown, being carried, put down, and standing on him. */
final class BellRiding {
    private final HollowbellEntity hb;

    BellRiding(HollowbellEntity hb) {
        this.hb = hb;
    }

    Vector3f slotModel(int i) {
        if (i < 4) {
            var S = hb.rig.spots[i];
            return new Vector3f(S.centre().x * 0.95f, 140f, S.centre().z * 0.95f);
        }
        if (i < 8) {
            // between the balls, close to the vase
            double a = Math.atan2(hb.rig.spots[i - 4].centre().z, hb.rig.spots[i - 4].centre().x) + Math.PI / 4;
            return new Vector3f((float) (Math.cos(a) * 26), 150f, (float) (Math.sin(a) * 26));
        }
        double a = (i - 8) * Math.PI * 2 / (HollowbellEntity.SLOTS - 8) + 0.3;
        return new Vector3f((float) (Math.cos(a) * 58), 142f, (float) (Math.sin(a) * 58));
    }

    /** where the feet of whatever is in slot i of the dome go, in the world */
    public Vec3 slotWorld(int i) {
        if (i < 4) {
            // right under the ball, so it is in reach over your head
            var S = hb.rig.spots[i];
            Vec3 ballBottom = hb.boneWorld(S.bone(), new Vector3f(S.centre().x, S.centre().y - 15f, S.centre().z));
            return new Vec3(ballBottom.x, ballBottom.y - 2.6, ballBottom.z);
        }
        Vec3 w = hb.boneWorld(hb.rig.bellBone, slotModel(i));
        return i < 8 ? w.add(0, -1, 0) : w;
    }

    /** just past the end of arm a, where it holds what it has wrapped (inside it, it would be hidden) */
    private Vec3 clutched(int a) {
        var A = hb.rig.arms[a];
        int[] b = A.bones();
        Vec3 tip = hb.armTipWorld(a), last = hb.boneWorld(b[b.length - 1], A.joints()[b.length - 1]);
        Vec3 d = tip.subtract(last);
        return d.lengthSqr() < 1e-6 ? tip : tip.add(d.normalize().scale(4 * hb.bellScale() + 1));
    }

    /** held in the wrap: thrashing about in the coil, jolted hardest as it squeezes (both sides work it out) */
    private Vec3 writhe() {
        if (hb.moveNow() != Moves.WRAP) return Vec3.ZERO;
        float t = hb.moveT(0f) - Moves.WRAP_REACH;
        if (t < 0 || t >= Moves.WRAP_HOLD) return Vec3.ZERO;
        double a = 0.18 + 0.35 * Moves.hump(t % 20f, 0, 2, 4, 9);
        return new Vec3(Math.sin(t * 1.7) * a, Math.sin(t * 2.3 + 1) * a * 0.6, Math.cos(t * 1.3) * a);
    }

    /** where a seat of the given kind is right now (see Seat), for whatever rides it */
    public Vec3 seatSpot(int mode, int index, @Nullable Entity who) {
        double hgt = who == null ? 1 : who.getBbHeight();
        return switch (mode) {
            case Seat.STRAND_TIP ->
                index >= 0 && index < hb.rig.strands.length
                        ? hb.strandTipWorld(index).add(0, -hgt * 0.6, 0)
                        : hb.position();
            case Seat.ARM_TIP ->
                index >= 0 && index < hb.rig.arms.length
                        ? clutched(index).add(0, -hgt * 0.5, 0).add(writhe())
                        : hb.position();
            case Seat.INSIDE -> slotWorld(Mth.clamp(index, 0, HollowbellEntity.SLOTS - 1));
            case Seat.POD ->
                index >= 0 && index < hb.rig.pods.length ? hb.podWorld(index).add(0, -hgt * 0.5, 0) : hb.position();
            case Seat.CROWN -> hb.crownWorld();
            // carried up onto the crown: their middle is right at the strand's end, held round the waist
            case Seat.CARRY ->
                index >= 0 && index < hb.rig.strands.length
                        ? hb.strandTipWorld(index).add(0, carryDrop(who), 0)
                        : hb.position();
            default -> hb.position();
        };
    }

    /**
     * How far below the strand's end the seat of somebody being carried up is, so that their middle is right at it:
     * half their height, less how far the game sits a rider below the seat (a player sits 0.6 lower).
     */
    public double carryDrop(@Nullable Entity who) {
        if (who == null) return -0.5;
        Entity v = who.getVehicle() != null ? who.getVehicle() : hb;
        return who.getVehicleAttachmentPoint(v).y - who.getBbHeight() * 0.5;
    }

    public @Nullable LivingEntity fetchingNow() {
        if (hb.fetching == null || !(hb.level() instanceof ServerLevel sl)) return null;
        Player p = sl.getServer().getPlayerList().getPlayer(hb.fetching);
        if (p != null) return p.level() == hb.level() ? p : null;
        // (anything else can be fetched too, with /execute as ... run hollowbell carry: handy for filming it)
        return sl.getEntity(hb.fetching) instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    /**
     * The book's "ride him": he comes alongside you, a strand reaches out and takes you round the middle, and carries
     * you up his side, round the rim and over the dome, and sets you down on his crown (see BellMoves.runCarry).
     */
    public boolean comeAndGetMe(LivingEntity who) {
        if (hb.rider != null || hb.isDeadOrDying() || hb.resting() || who == hb) return false;
        hb.fetching = who.getUUID();
        hb.setStay(false);
        return true;
    }

    /** stop coming (and put down anyone half way up, gently) */
    public void stopFetch() {
        hb.fetching = null;
        hb.moves.cancelCarry();
    }

    public boolean comingForSomebody() {
        return hb.fetching != null;
    }

    /**
     * Called by the moves once a strand has carried the one who asked up to the crown. keep is the seat they were
     * carried up on: it stays under them and becomes the crown seat, so they are never taken off it on the way.
     */
    void seatOnCrown(LivingEntity who, @Nullable Seat keep) {
        hb.fetching = null;
        if (hb.riderSeat != null && hb.riderSeat != keep) hb.riderSeat.discard();
        Vec3 c = hb.crownWorld();
        if (keep != null && !keep.isRemoved() && who.getVehicle() == keep) {
            hb.riderSeat = keep;
            hb.riderSeat.follow(Seat.CROWN, 0);
            hb.riderSeat.moveTo(c.x, c.y, c.z);
        } else {
            hb.riderSeat = new Seat(hb.level(), hb);
            hb.riderSeat.setPos(c.x, c.y, c.z);
            hb.riderSeat.follow(Seat.CROWN, 0);
            hb.level().addFreshEntity(hb.riderSeat);
            who.stopRiding();
            who.startRiding(hb.riderSeat, true);
        }
        hb.rider = who;
        hb.setFlag(HollowbellEntity.F_RIDDEN, true);
        hb.driveF = hb.driveS = 0f;
        hb.driveYaw = who.getYRot();
        hb.targets.clearHitList();
        if (who instanceof ServerPlayer sp) {
            ServerPlayNetworking.send(sp, new BeingHimPayload(hb.getId(), true));
            sp.displayClientMessage(Component.translatable("message.hollowbell.on_crown"), true);
        }
        hb.sound(c, SoundEvents.AMETHYST_BLOCK_CHIME, 2f, 0.5f);
    }

    /** straight on to the crown, no strand: the command and the tests */
    public boolean possess(LivingEntity who) {
        if (hb.rider != null || hb.isDeadOrDying()) return false;
        seatOnCrown(who, null);
        return true;
    }

    /**
     * A player is leaving the game (see LogOffMixin): if anything of his has them (on his crown, being carried up,
     * held by a strand or an arm, or inside his dome) he lets go, and they're put on the ground beside him first,
     * so they're saved standing there and not up in the air, where they'd fall when they came back.
     */
    public static void putDownOnLeaving(ServerPlayer p) {
        if (!(p.getVehicle() instanceof Seat st) || !(p.level().getEntity(st.ownerId()) instanceof HollowbellEntity h))
            return;
        h.riding.letGoOnLeaving(p);
    }

    void letGoOnLeaving(ServerPlayer p) {
        if (hb.rider == p) {
            ServerPlayNetworking.send(p, new BeingHimPayload(hb.getId(), false));
            hb.rider = null;
            hb.setFlag(HollowbellEntity.F_RIDDEN, false);
            hb.driveF = hb.driveS = 0f;
            if (hb.riderSeat != null) {
                hb.riderSeat.ejectPassengers();
                hb.riderSeat.discard();
                hb.riderSeat = null;
            }
        }
        if (hb.fetching != null && hb.fetching.equals(p.getUUID())) hb.fetching = null;
        hb.moves.letGo(p);
        p.stopRiding();
        Vec3 at = safeSpotBeside(p);
        p.moveTo(at.x, at.y, at.z, p.getYRot(), p.getXRot());
        p.fallDistance = 0f;
        p.setDeltaMovement(Vec3.ZERO);
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false));
    }

    /**
     * Somewhere on the ground beside him to stand: out past his arms and strands (the side they were on first, then
     * round him), not in blocks or water.
     */
    public Vec3 safeSpotBeside(Entity who) {
        float s = hb.bellScale();
        Vec3 from = who.position();
        Vec3 out = new Vec3(from.x - hb.getX(), 0, from.z - hb.getZ());
        if (out.lengthSqr() < 1e-4) out = new Vec3(1, 0, 0);
        out = out.normalize();
        for (double r = 100 * s + 3; r < 100 * s + 40; r += 4 * s + 3) {
            for (int turn = 0; turn < 16; turn++) {
                double a = Math.PI / 8 * ((turn + 1) / 2) * (turn % 2 == 0 ? 1 : -1);
                double dx = out.x * Math.cos(a) - out.z * Math.sin(a), dz = out.x * Math.sin(a) + out.z * Math.cos(a);
                double x = hb.getX() + dx * r, z = hb.getZ() + dz * r;
                BlockPos col = BlockPos.containing(x, hb.getY(), z);
                if (!NoWait.loaded(hb.level(), col)) continue;
                int top = NoWait.standY(hb.level(), col.getX(), col.getZ(), who.getBlockY());
                for (int up = 0; up < 6; up++) {
                    AABB box = who.getDimensions(Pose.STANDING).makeBoundingBox(x, top + up, z);
                    if (hb.level().noCollision(who, box)
                            && !hb.level().containsAnyLiquid(box)
                            && !hb.level().containsAnyLiquid(box.move(0, -1, 0))) return new Vec3(x, top + up, z);
                }
            }
        }
        // nowhere better: the ground right under where they are
        return new Vec3(from.x, hb.groundAt(from.x, from.z), from.z);
    }

    /**
     * Getting off (G, or "get off" in the book): he sinks to the ground, a strand comes up round the rim and over the
     * dome to his crown, takes you round the middle and carries you back down the way you came up, and sets you on
     * your feet beside him (see BellMoves.runCarry). If he can't (down on the ground, dying, nowhere to put you),
     * you're set down beside him straight away as before.
     */
    public void setMeDown(LivingEntity who) {
        if (hb.rider != who || hb.moves.settingDown()) return;
        if (hb.isDeadOrDying() || hb.resting() || !hb.moves.startSetDown(who)) dropRider();
        else {
            hb.driveF = hb.driveS = 0f;
            hb.driveUp = 0;
        }
    }

    /** he keeps the height he's at now (a strand is carrying somebody, and the way is fixed to him) */
    void holdHeight() {
        hb.wantY = hb.getY();
    }

    /** the moves take whoever rides the crown off it for the strand to carry down: the same seat, now the strand's */
    @Nullable
    Seat handRiderToStrand() {
        if (hb.rider == null
                || hb.riderSeat == null
                || hb.riderSeat.isRemoved()
                || hb.rider.getVehicle() != hb.riderSeat) return null;
        if (hb.rider instanceof ServerPlayer sp) ServerPlayNetworking.send(sp, new BeingHimPayload(hb.getId(), false));
        Seat st = hb.riderSeat;
        hb.riderSeat = null;
        hb.rider = null;
        hb.setFlag(HollowbellEntity.F_RIDDEN, false);
        hb.driveF = hb.driveS = 0f;
        return st;
    }

    public void dropRider() {
        if (hb.rider instanceof ServerPlayer sp) ServerPlayNetworking.send(sp, new BeingHimPayload(hb.getId(), false));
        LivingEntity who = hb.rider;
        hb.rider = null;
        hb.setFlag(HollowbellEntity.F_RIDDEN, false);
        if (hb.riderSeat != null) {
            hb.riderSeat.ejectPassengers();
            hb.riderSeat.discard();
            hb.riderSeat = null;
        }
        // (taken off to another world or by another giant, they stay where that put them)
        if (who != null
                && who.isAlive()
                && who.level() == hb.level()
                && !Giants.onGiantSeat(who, HollowbellMod.MOD_ID)) {
            // set down on the ground beside him, gently
            double a = hb.getRandom().nextDouble() * Math.PI * 2, r = hb.bellRadius() + 3;
            double x = hb.getX() + Math.cos(a) * r, z = hb.getZ() + Math.sin(a) * r;
            double y = hb.groundAt(x, z);
            who.teleportTo(x, y + 0.5, z);
            who.fallDistance = 0;
            who.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false));
        }
        hb.driveF = hb.driveS = 0f;
    }

    /** the being-him keys: which way, and up (1) or down (-1) */
    public void drive(LivingEntity who, float forward, float strafe, float yaw, int up) {
        if (hb.rider != who || hb.moves.settingDown()) return;
        hb.driveF = Mth.clamp(forward, -1f, 1f);
        hb.driveS = Mth.clamp(strafe, -1f, 1f);
        hb.driveUp = Mth.clamp(up, -1, 1);
        hb.driveYaw = yaw;
        hb.driveIdle = 0;
    }

    void riderTick() {
        if (hb.rider == null) return;
        if (!hb.rider.isAlive()
                || hb.rider.isRemoved()
                || hb.rider.level() != hb.level()
                || Giants.onGiantSeat(hb.rider, HollowbellMod.MOD_ID)) {
            dropRider();
            return;
        }
        if (hb.riderSeat == null || hb.riderSeat.isRemoved()) {
            hb.riderSeat = new Seat(hb.level(), hb);
            hb.riderSeat.follow(Seat.CROWN, 0);
            hb.level().addFreshEntity(hb.riderSeat);
        }
        hb.ensurePose();
        Vec3 c = hb.crownWorld();
        hb.riderSeat.moveTo(c.x, c.y, c.z);
        if (hb.rider.getVehicle() != hb.riderSeat) hb.rider.startRiding(hb.riderSeat, true);
        hb.rider.fallDistance = 0;
        if (++hb.driveIdle > 60) {
            hb.driveF = 0;
            hb.driveS = 0;
            hb.driveUp = 0;
        }
    }

    /** whoever stands on him now, anywhere on him (players too, or not) */
    public List<Entity> standingOnHim(boolean players) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : Solid.standingOn(hb, players)) if (!Giants.isGiant(e)) out.add(e);
        return out;
    }

    /** he is dying or going away: whoever stands on him floats down slowly instead of falling */
    void lowerWhoeverStandsOnHim() {
        // (dying, his health is already gone: he still holds them for this one look)
        hb.lowering = true;
        try {
            if (!hb.solidReady()) return;
            for (Entity e : standingOnHim(true)) if (e instanceof LivingEntity le) floatDown(le);
        } finally {
            hb.lowering = false;
        }
    }

    /** slow falling long enough to reach the ground from where they are, however high that is */
    void floatDown(LivingEntity le) {
        double over = le.getY() - hb.groundAt(le.getX(), le.getZ());
        // (slow falling comes down about 0.45 blocks a tick, after a second or so getting there)
        int ticks = Mth.clamp((int) Math.ceil(over / 0.45) + 60, 100, 2400);
        le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, ticks, 0, false, false));
        le.resetFallDistance();
    }
}
