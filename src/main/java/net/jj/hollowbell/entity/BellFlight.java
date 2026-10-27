package net.jj.hollowbell.entity;

import java.util.UUID;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.ModSounds;
import net.jj.hollowbell.solid.GiantHull;
import net.jj.hollowbell.world.Away;
import net.jj.hollowbell.world.NoWait;
import net.jj.hollowbell.world.WorldOne;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/** How he flies: drifting, cruising, coming when called, keeping off the ward and the other giants. */
final class BellFlight {
    private final HollowbellEntity hb;

    BellFlight(HollowbellEntity hb) {
        this.hb = hb;
    }

    /** a new one comes down out of the sky over where he was put, bell open, slowly */
    void arrive() {
        hb.settled = true;
        if (hb.level().isClientSide) return;
        float s = hb.bellScale();
        double g = hb.groundAt(hb.getX(), hb.getZ());
        double top = NoWait.ceiling(hb.level()) - (hb.rig.crownY + 10) * s;
        double y = Math.min(top, g + 110 * s + 40);
        if (y <= hb.getY() + 4) return;
        hb.setPos(hb.getX(), y, hb.getZ());
        hb.animAt = null;
        hb.arriving = 400;
        hb.vel = new Vec3(0, -0.05, 0);
        hb.sound(hb.position().add(0, hb.rig.rimY * s, 0), ModSounds.TOLL, 5f, 1.1f);
        hb.particles(ParticleTypes.CLOUD, hb.position().add(0, (hb.rig.rimY + 40) * s, 0), 120, 60 * s + 4, 0.02);
    }

    /**
     * Coming to somebody. Every ten seconds he aims at where they are now. If they've left the game (or gone to
     * another world) he goes on to the last place he saw them and waits there; when they're back he carries on to
     * them. After 20 real minutes of waiting he gives up and goes back to his own business.
     */
    void comeTick() {
        if (hb.tickCount % 20 != 0 || !(hb.level() instanceof ServerLevel sl)) return;
        ServerPlayer cp = sl.getServer().getPlayerList().getPlayer(hb.comeTo);
        if (cp != null && cp.level() == hb.level() && cp.isAlive()) {
            boolean waited = hb.comeLostAt != 0 || hb.goal == null;
            hb.comeLostAt = 0;
            if (waited || hb.tickCount % 200 == 0) {
                hb.goal = hb.keptIn(cp.position());
                if (waited) {
                    hb.setStay(false);
                    hb.wakeUp();
                }
            }
            return;
        }
        long t = System.currentTimeMillis();
        if (hb.comeLostAt == 0) hb.comeLostAt = t;
        else if (t - hb.comeLostAt > HollowbellEntity.comeGiveUpMs) {
            hb.comeTo = null;
            hb.holdThere = false;
            hb.comeLostAt = 0;
        }
    }

    /** who he follows (null: nobody), and whether he's on his way back to them just now */
    @Nullable
    UUID follow;

    private boolean closing;

    void follow(@Nullable Player p) {
        closing = false;
        if (p == null) {
            follow = null;
            hb.setFlag(HollowbellEntity.F_FOLLOW, false);
            return;
        }
        if (hb.huntOrdered) hb.targets.dropHunt();
        hb.goal = null;
        hb.comeTo = null;
        hb.holdThere = false;
        hb.setStay(false);
        hb.wakeUp();
        follow = p.getUUID();
        hb.setFlag(HollowbellEntity.F_FOLLOW, true);
    }

    /** the one he follows, while they're here in his world */
    private @Nullable Player followed() {
        if (follow == null || !(hb.level() instanceof ServerLevel sl)) return null;
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(follow);
        return p != null && p.level() == hb.level() && !p.isSpectator() && p.isAlive() ? p : null;
    }

    /** gone from his world, or into a woken crown's circle where he won't go: he stops following */
    void followTick() {
        if (follow == null || hb.tickCount % 20 != 0) return;
        Player f = followed();
        if (f == null || hb.warded(f.getX(), f.getZ())) follow(null);
    }

    /** where to go to keep near the one he follows, or null to stay where he is */
    private @Nullable Vec3 followSpot() {
        Player f = followed();
        if (f == null) return null;
        Vec3 fp = hb.keptIn(f.position());
        // how far off he keeps: just past his rim. He sets off once you're a little further than that, and stops once
        // he's back there (one line both ways would have him stopping and starting all the time)
        double keep = hb.bellRadius() + 12, d = hb.horiz(fp);
        if (!closing && d > keep * 1.25) closing = true;
        else if (closing && d < keep) closing = false;
        return closing ? fp : null;
    }

    /** his top speed across, blocks per tick */
    public double maxSpeed() {
        float s = hb.bellScale();
        double v = (0.10 + 0.16 * Math.sqrt(s)) * hb.speedMul;
        if (hb.rider != null) v *= 1.5;
        if (hb.angry()) v *= 1.15;
        if (hb.tired()) v *= 0.4;
        // backed down from a giant and up out of its reach: he gets away fast (tired or not)
        if (escaping() && escapeUp) v = (0.10 + 0.16 * Math.sqrt(s)) * hb.speedMul * ESCAPE_FAST;
        return v;
    }

    // getting away from a giant he backed down to

    /** how high over the ground he climbs when he backs down from a giant, at full size (plus 20) */
    public static final double ESCAPE_UP = 220;
    /** how much faster than his own top speed he gets away once he's up there */
    public static final double ESCAPE_FAST = 4;
    /** how much harder he climbs to get up there */
    static final double ESCAPE_CLIMB = 3;

    /** up at the height he gets away at (once there, he keeps going fast until it's over) */
    boolean escapeUp;
    /** the top of the giants near him, and when it was last looked at */
    private double escapeTop = Double.NEGATIVE_INFINITY;

    private long escapeLooked = Long.MIN_VALUE;

    /**
     * Backed down from another giant and getting away from it: he climbs up out of every giant's reach first, then
     * flies off fast. Not while somebody rides him (they steer) or he's down on the ground.
     */
    public boolean escaping() {
        boolean on = hb.yielding()
                && hb.fleeFrom != null
                && hb.level().getGameTime() < hb.fleeUntil
                && hb.rider == null
                // (gone after a player meanwhile: that fight is as ever)
                && hb.getTarget() == null
                && !hb.isDeadOrDying();
        if (!on) escapeUp = false;
        return on;
    }

    /**
     * The height he gets away at: well up over the ground ({@link #ESCAPE_UP} at full size), and all of him (his
     * strand ends too) well over the top of every giant near him, past what they can reach up to.
     */
    public double escapeHeight(double gAvg) {
        float s = hb.bellScale();
        long now = hb.level().getGameTime();
        if (now - escapeLooked >= 10 || now < escapeLooked) {
            escapeLooked = now;
            escapeTop = Double.NEGATIVE_INFINITY;
            if (hb.level() instanceof ServerLevel sl) {
                double r = Meeting.range(s) * 1.5;
                for (Entity x : sl.getEntities(hb, hb.getBoundingBox().inflate(r, 400, r), Giants::isGiant)) {
                    LivingEntity o = Giants.ownerOf(x);
                    if (o == null || o == hb || o instanceof HollowbellEntity || !o.isAlive()) continue;
                    double[] them = GiantHull.of(o, Giants.TAG);
                    double top = them.length > 0 ? GiantHull.box(them).maxY : o.getBoundingBox().maxY;
                    if (Double.isFinite(top)) escapeTop = Math.max(escapeTop, top);
                }
            }
        }
        double y = gAvg + ESCAPE_UP * s + 20;
        if (escapeTop > Double.NEGATIVE_INFINITY) {
            double[] me = hb.jjHull();
            double under = me.length > 0 ? GiantHull.box(me).minY - hb.getY() : 0;
            if (!Double.isFinite(under)) under = 0;
            // (what a giant reaches up with over its own top: strands, breath, a jump)
            y = Math.max(y, escapeTop + 60 * s + 20 - under);
        }
        return y;
    }

    /**
     * Flying, like a jellyfish. He picks a height and swims to it: going up, the bell squeezes hard and shoots him
     * up, then he drifts on up slower until the next push (quicker pushes the further he has to go); going down,
     * the bell opens and he sinks slowly like a parachute; level, slow gentle pulses carry him along. His speed
     * only ever changes smoothly, he turns in wide heavy curves, and he never goes into the ground: he looks at
     * the ground under all of him and ahead of him, and keeps his rim above it.
     */
    void fly(long now) {
        float s = hb.bellScale();
        boolean dying = hb.isDeadOrDying();
        boolean down = hb.resting() || dying || hb.asleep;
        LivingEntity t = hb.getTarget();
        // going for another giant: his body stays out of its body (over it, or at its side)
        // (worked out every few ticks: it looks at every column of both bodies)
        if (t == null || dying) hb.giantPlan = null;
        else if (hb.tickCount % 4 == 0 || hb.giantPlanFor != t) {
            hb.giantPlan = hb.targets.giantTargetPlan(t);
            hb.giantPlanFor = t;
        }
        boolean still =
                hb.stay || hb.moves.holdsStill() || dying || hb.asleep || (hb.goal == null && hb.waitingForSomebody());
        Vec3 want = wanted(t, still, s);
        if (hb.cruise < 0) hb.cruise = 6 * s + 1;

        // ---- the ground under him and ahead of him
        Vec3 hv = new Vec3(hb.vel.x, 0, hb.vel.z);
        double r = hb.bellRadius() * 0.6;
        double gC = hb.groundAt(hb.getX(), hb.getZ());
        double gMax = gC, gSum = gC;
        int gN = 1;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            double gx = hb.getX() + Math.cos(a) * r, gz = hb.getZ() + Math.sin(a) * r;
            double g = hb.groundAt(gx, gz);
            gMax = Math.max(gMax, g);
            gSum += g;
            gN++;
            // and where he'll be in a couple of seconds
            double ax = gx + hv.x * 50, az = gz + hv.z * 50;
            double g2 = hb.groundAt(ax, az);
            gMax = Math.max(gMax, g2);
            gSum += g2;
            gN++;
        }
        double gAvg = gSum / gN;
        // his rim stays above the highest ground under him; his strand ends may drag a little
        double hardMin = Math.max(gC - 8 * s, gMax + 2 + 2 * s - (hb.rig.rimY - 10) * s);

        double wy = heightWanted(t, dying, down, s, gC, gAvg, hardMin);

        // ---- up and down
        double e = wy - hb.getY();
        double vy = hb.vel.y;
        double climbAt = 3 * s + 1;
        float pulseAge = (float) (now - hb.getEntityData().get(HollowbellEntity.DATA_PULSE_START));
        float push = pushCurve(pulseAge) * hb.getEntityData().get(HollowbellEntity.DATA_PULSE_POWER);
        double vAvg = (0.07 + 0.24 * Math.pow(s, 0.7));
        if (!down && e > climbAt) {
            double u = Mth.clamp(e / (30 * s + 10), 0, 1);
            int period = (int) Mth.lerp(u, 62, 24);
            if (now >= hb.nextPulse) {
                hb.pulse((float) (1.05 + 0.6 * u));
                hb.nextPulse = now + period;
            }
            // the push comes with the squeeze, then the water slows him till the next one
            double climbRate = vAvg * (0.45 + 0.55 * u) * (escaping() ? ESCAPE_CLIMB : 1);
            vy += push * climbRate * period / 22.0 * 0.11;
            vy *= 0.955;
        } else if (e < -climbAt || down) {
            // sinking, bell open: slow and steady
            // coming down to hit something (or to the ground in a drop), he lets himself fall faster
            boolean hurry = (down && !dying && !hb.asleep) || hb.moves.wantsHeight() != null || hb.arriving > 0;
            double sinkMax = (0.04 + 0.16 * Math.pow(s, 0.7)) * (hurry ? 2.6 : 1.0);
            double vt = -Math.min(sinkMax, Math.max(0, -e) * 0.035);
            vy += (vt - vy) * 0.045;
        } else {
            vy += (e * 0.03 - vy) * 0.05;
            vy *= 0.97;
        }
        // never into the ground: pushed up out of it, firmly but without a jump
        Vec3 steer = hb.moves.steer();
        if (steer == null && hb.getY() + vy < hardMin)
            vy = Math.max(vy, Math.min((hardMin - hb.getY()) * 0.2, 0.4 + 0.9 * s));

        // ---- across: steered smoothly toward where he's going, heavy to turn
        double max = maxSpeed();
        Vec3 wantV = Vec3.ZERO;
        if (want != null && !still) {
            Vec3 d = want.subtract(hb.position()).multiply(1, 0, 1);
            double len = d.length();
            if (len > 0.01) {
                // easing off as he gets there, so he doesn't overshoot
                double k = Mth.clamp((len - 2 * s) / (30 * s + 12), 0, 1);
                wantV = d.scale(max * k / len);
            }
        }
        // each pulse gives a surge
        double surge = 1 + 0.5 * push;
        wantV = wantV.scale(surge);
        double acc = max / 60.0 * (0.6 + 1.2 * push) * (down ? 0.5 : 1);
        Vec3 dv = wantV.subtract(hv);
        if (dv.length() > acc) dv = dv.normalize().scale(acc);
        hv = hv.add(dv).scale(0.995);
        if (still && want == null) hv = hv.scale(0.97);
        hb.vel = new Vec3(hv.x, vy, hv.z);
        // the dive: driven down hard, bell first (it eases into the speed)
        if (steer != null) hb.vel = hb.vel.add(steer.subtract(hb.vel).scale(0.12));

        // asleep: a slow, weak breath of the bell now and then
        if (hb.asleep && now >= hb.nextPulse) {
            hb.pulse(0.35f);
            hb.nextPulse = now + 150 + hb.getRandom().nextInt(40);
        }
        // level pulses, to carry him along (none while he climbs: those come above; none sinking)
        if (!down && Math.abs(e) <= climbAt && now >= hb.nextPulse) {
            int period = hb.angry() ? 46 : 70;
            if (hb.rider != null && want != null) period = 36;
            if (want == null) period += 30; // idling: a slow breathing pulse
            hb.pulse(want == null ? 0.7f : 1f);
            hb.nextPulse = now + period + hb.getRandom().nextInt(12);
        }

        hb.setPos(hb.getX() + hb.vel.x, hb.getY() + hb.vel.y, hb.getZ() + hb.vel.z);
        hb.setDeltaMovement(Vec3.ZERO);
        hb.getEntityData()
                .set(HollowbellEntity.DATA_VEL, new Vector3f((float) hb.vel.x, (float) hb.vel.y, (float) hb.vel.z));
        if (hb.tickCount % 10 == 0 && BellConfig.V.griefing && hv.lengthSqr() > 0.001) hb.moves.flattenUnderStrands();
        // his strands slide over each other as he goes
        if (hb.tickCount % 50 == 0 && hv.length() > max * 0.3 && hb.getRandom().nextInt(2) == 0)
            hb.sound(
                    hb.strandTipWorld(hb.getRandom().nextInt(hb.rig.strands.length)),
                    ModSounds.STRAND,
                    0.8f,
                    0.9f + hb.getRandom().nextFloat() * 0.2f);
    }

    /** where he wants to go across the ground now, or null to stay where he is */
    private @Nullable Vec3 wanted(@Nullable LivingEntity t, boolean still, float s) {
        Vec3 want = null;
        // a woken crown's circle: a goal in there is dropped, and standing in there he makes for the way out
        Vec3 wardOut = hb.rider == null ? wardEscape() : null;
        if (wardOut == null && hb.goal != null && hb.warded(hb.goal.x, hb.goal.z)) hb.goal = null;
        if (hb.rider != null) {
            if (hb.driveF != 0f || hb.driveS != 0f) {
                float yr = hb.driveYaw * Mth.DEG_TO_RAD;
                Vec3 fw = new Vec3(-Mth.sin(yr), 0, Mth.cos(yr)), rt = new Vec3(Mth.cos(yr), 0, Mth.sin(yr));
                want = hb.position().add(fw.scale(hb.driveF * 60).add(rt.scale(hb.driveS * 60)));
            } else if (hb.huntOrdered && t != null && hb.horiz(t.position()) > 12 * s + 2)
                want = t.position(); // sent after something, and not steered
        } else if (!still) {
            LivingEntity f = hb.riding.fetchingNow();
            if (f != null) want = hb.moves.fetchSpot(f);
            else if (hb.goal != null) {
                want = hb.goal;
                if (hb.horiz(hb.goal) < 6 + 10 * s) {
                    hb.goal = null;
                    want = null;
                    // (coming to somebody who has gone: he waits here for them, still on the order)
                    if (hb.holdThere && !hb.waitingForSomebody()) {
                        hb.holdThere = false;
                        hb.comeTo = null;
                        hb.setStay(true);
                    }
                }
            } else if (follow != null) {
                want = followSpot();
            } else if (t != null) {
                // hunting: he hangs right over you, so the strands can reach (another giant: he stops where his body
                // meets its body across, and goes over it only once he's up over its top)
                if (hb.giantPlan != null && hb.giantPlan[1] < hb.contactGap()) want = null;
                else if (hb.horiz(t.position()) > 12 * s + 2) want = t.position();
            } else if (hb.huntOrdered && hb.huntSeen != null) {
                // sent after something gone from sight: where it was last seen
                if (hb.horiz(hb.huntSeen) > 8 * s + 3) want = hb.huntSeen;
            } else if (hb.flee.towardSpot(60 * s + 30) != null) {
                // backed down: off to his ground, or to whoever holds his book
                want = hb.flee.towardSpot(60 * s + 30);
                hb.wanderTo = null;
            } else if (hb.targets.keepAwayFrom() != null) {
                // backing down, or keeping out of another giant's way: straight away from it
                Vec3 o = hb.targets.keepAwayFrom();
                Vec3 d = new Vec3(hb.getX() - o.x, 0, hb.getZ() - o.z);
                if (d.lengthSqr() < 1e-4) d = new Vec3(1, 0, 0);
                want = hb.position().add(d.normalize().scale(60 * s + 30));
                hb.wanderTo = null;
            } else {
                if (--hb.wanderIn <= 0 || (hb.home != null && hb.horiz(hb.home) > wanderRange() * 1.3)) {
                    hb.wanderIn = 300 + hb.getRandom().nextInt(500);
                    Vec3 c = hb.isGuardian() && hb.home != null ? hb.home : hb.position();
                    double a = hb.getRandom().nextDouble() * Math.PI * 2,
                            d = hb.getRandom().nextDouble() * wanderRange();
                    hb.goal = null;
                    hb.wanderTo = c.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                    // and a new height to drift at: low over the ground, or up high
                    hb.cruise = hb.getRandom().nextInt(3) == 0
                            ? (20 + hb.getRandom().nextDouble() * 30) * s + 4
                            : (2 + hb.getRandom().nextDouble() * 14) * s + 1;
                }
                if (hb.wanderTo != null && hb.horiz(hb.wanderTo) > 8 * s + 3) want = hb.wanderTo;
            }
        }
        if (hb.rider == null) {
            // his own will keeps to his circle (a rider may drive him out), and never into a warded one
            if (want != null) {
                if (!(hb.huntOrdered && hb.hunted != null))
                    want = hb.keptIn(want); // (a player's "go after that" takes him out of it)
                if (hb.warded(want.x, want.z)) want = wardKeepOut(want);
            } else if (hb.bound() && !still && Math.hypot(hb.getX() - hb.boundX, hb.getZ() - hb.boundZ) > hb.boundR) {
                want = hb.keptIn(hb.position()); // drifted out somehow: he walks back in
            }
            if (wardOut != null && !still)
                want = wardOut; // out of the circle first (unless held still, dying, or mid-move)
        }
        return want;
    }

    /** the height he's making for */
    private double heightWanted(
            @Nullable LivingEntity t, boolean dying, boolean down, float s, double gC, double gAvg, double hardMin) {
        double wy;
        if (hb.arriving > 0) {
            hb.arriving--;
            wy = gAvg + hb.cruise;
            if (hb.getY() - wy < 3 * s + 1) hb.arriving = 0;
        } else if (dying || down) wy = gC;
        else if (hb.moves.setDownGround() != null) {
            wy = hb.moves.setDownGround();
            hb.wantY = wy;
        } else if (hb.rider != null) {
            if (Double.isNaN(hb.wantY)) hb.wantY = hb.getY();
            if (hb.driveUp != 0) hb.wantY = hb.getY() + hb.driveUp * (14 * s + 4);
            wy = hb.wantY;
        } else if (escaping()) {
            // backed down from a giant: up out of its reach first, then off fast
            wy = escapeHeight(gAvg);
            if (hb.getY() >= wy - (10 * s + 4)) escapeUp = true;
        } else if (hb.moves.carryingUp()) {
            // carrying somebody up: he keeps his height, so the way up stays put
            if (Double.isNaN(hb.wantY)) hb.wantY = hb.getY();
            wy = hb.wantY;
        } else if (hb.riding.fetchingNow() != null) wy = hb.riding.fetchingNow().getY();
        else if (hb.moves.wantsHeight() != null) wy = hb.moves.wantsHeight();
        else if (t != null) {
            double tg = hb.groundAt(t.getX(), t.getZ());
            boolean airborne = t.getY() - tg > 6 + 12 * s;
            double d = hb.horiz(t.position());
            if (airborne) wy = t.getY() - 55 * s; // up to it: it hangs among his strands, under the bell
            else if (d > hb.bellRadius() * 2.2) wy = Math.max(t.getY(), gAvg) + 18 * s + 2; // coming: a little higher
            else wy = t.getY() - 1.5 * s; // over it, the strand ends at its feet
        } else if (hb.stay) {
            if (Double.isNaN(hb.wantY)) hb.wantY = hb.getY();
            wy = Math.max(hb.wantY, gAvg);
        } else wy = gAvg + hb.cruise;
        if (hb.giantPlan != null && !down && !Double.isNaN(hb.giantPlan[0])) wy = Math.max(wy, hb.giantPlan[0]);
        wy = Math.max(wy, hardMin);
        // (no limit to how high he goes, but under the nether's roof he keeps all of him below it)
        if (hb.level().dimensionType().hasCeiling())
            wy = Math.min(wy, NoWait.ceiling(hb.level()) - (hb.rig.crownY + 4) * s);
        if (!(hb.rider != null) && !(hb.stay)) hb.wantY = wy;
        return wy;
    }

    /** how hard the bell is pushing, age ticks into a pulse: rises with the squeeze and fades, 0 to 1 */
    static float pushCurve(float age) {
        if (age < 0f || age > 20f) return 0f;
        return age < 5f ? Moves.smooth(age / 5f) : 1f - Moves.smooth((age - 5f) / 15f);
    }

    double wanderRange() {
        return (hb.isGuardian() ? 80 : 150) * hb.bellScale() + 30;
    }

    /** Once a second, from the server: nobody near for a few seconds and he steps out of the world. */
    public void stepAsideIfAlone(int every) {
        if (hb.tickCount < 40) return;
        if (hb.justBack > 0) {
            hb.justBack = Math.max(0, hb.justBack - every);
            return;
        }
        if (!(hb.level() instanceof ServerLevel sl) || hb.isRemoved() || hb.isDeadOrDying()) return;
        // not while riding, travelling, mid-move or meeting
        if (hb.rider != null || hb.riding.comingForSomebody() || hb.moveNow() != Moves.NONE || hb.meetFoe != null) {
            hb.aloneOut = 0;
            return;
        }
        double away = Away.awayRange(sl.getServer(), hb.bellScale());
        for (ServerPlayer p : sl.players())
            if (!p.isSpectator() && p.distanceToSqr(hb.getX(), p.getY(), hb.getZ()) < away * away) {
                hb.aloneOut = 0;
                return;
            }
        hb.aloneOut += every;
        if (hb.aloneOut < 40) return;
        stepAside();
    }

    /** steps him out of the world (see Away) */
    public boolean stepAside() {
        if (!(hb.level() instanceof ServerLevel sl) || hb.isRemoved() || hb.isDeadOrDying()) return false;
        hb.riding.stopFetch();
        hb.riding.dropRider();
        hb.moves.letGoOfEverything(true);
        Vec3 dest = hb.stay ? null : hb.goal != null ? hb.goal : hb.waitingForSomebody() ? null : hb.wanderTo;
        if (dest != null) dest = hb.keptIn(dest); // bound, the sum keeps to his circle too
        double lift = Math.max(0, hb.getY() - hb.groundAt(hb.getX(), hb.getZ()));
        CompoundTag body = new CompoundTag();
        hb.saveWithoutId(body);
        Away.get(sl.getServer()).takeAway(sl, hb, body, dest, hb.travelSpeed(), lift);
        if (hb.worldOne) WorldOne.get(sl.getServer()).seen(hb);
        hb.steppedOut = true;
        hb.clearBars();
        hb.discard();
        return true;
    }

    /** standing inside the circle: a spot outside its edge to make for; otherwise nothing */
    @Nullable
    Vec3 wardEscape() {
        if (!hb.warded(hb.getX(), hb.getZ())) return null;
        if (!(hb.level() instanceof ServerLevel sl)) return null;
        BlockPos at = WorldOne.get(sl.getServer()).wardSpot();
        if (at == null) return null;
        double dx = hb.getX() - (at.getX() + 0.5), dz = hb.getZ() - (at.getZ() + 0.5);
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4) {
            dx = 1;
            dz = 0;
            len = 1;
        }
        double r = WorldOne.wardRange() + 32 + hb.bellRadius();
        return new Vec3(at.getX() + 0.5 + dx / len * r, hb.getY(), at.getZ() + 0.5 + dz / len * r);
    }

    /** a spot he wants pushed back out of the warded circle, to just past its edge */
    Vec3 wardKeepOut(Vec3 want) {
        if (!(hb.level() instanceof ServerLevel sl)) return want;
        BlockPos at = WorldOne.get(sl.getServer()).wardSpot();
        if (at == null) return want;
        double dx = want.x - (at.getX() + 0.5), dz = want.z - (at.getZ() + 0.5);
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4) {
            dx = hb.getX() - (at.getX() + 0.5);
            dz = hb.getZ() - (at.getZ() + 0.5);
            len = Math.max(1.0E-4, Math.sqrt(dx * dx + dz * dz));
        }
        double r = WorldOne.wardRange() + 16 + hb.bellRadius();
        return new Vec3(at.getX() + 0.5 + dx / len * r, want.y, at.getZ() + 0.5 + dz / len * r);
    }

    /** the crown has woken under him: whatever he was doing here, he is not doing it any more */
    public void pushedBackByWard() {
        if (hb.level().isClientSide) return;
        hb.setTarget(null);
        hb.goal = null;
        hb.setStay(false);
        hb.targets.clearHitList();
        hb.riding.stopFetch();
        hb.angerTicks = 0;
        hb.sound(hb.position(), ModSounds.HURT, 4.5f, 0.45f);
    }

    /**
     * Each tick: out of any other giant he's got into, the shortest way across, his share by weight (all of it if the
     * other doesn't move itself out). Never through each other, whoever drifted into whom.
     */
    void giantsApart() {
        if (!(hb.level() instanceof ServerLevel sl)
                || hb.isDeadOrDying()
                || hb.frozen
                || !BellSolid.ready()
                || hb.solidVer == 0) return;
        if (hb.tickCount % 5 == 0) {
            hb.giantsNear.clear();
            for (Entity x : sl.getEntities(hb, hb.bodyBox().inflate(160), Giants::isGiant)) {
                LivingEntity o = Giants.ownerOf(x);
                if (o != null && o != hb && o.isAlive() && !hb.giantsNear.contains(o)) hb.giantsNear.add(o);
            }
        }
        if (hb.giantsNear.isEmpty()) return;
        hb.hullNow = null;
        double[] me = hb.jjHull();
        if (me.length == 0) return;
        double cap = 1 + 4 * hb.bellScale();
        for (LivingEntity o : hb.giantsNear) {
            if (o.isRemoved() || o.level() != hb.level()) continue;
            double[] them = GiantHull.of(o, Giants.TAG);
            if (them.length == 0) continue;
            double[] p = GiantHull.pushOut(me, them);
            if (p[2] <= 1e-3) continue;
            // never toward the other's middle, or both can be pushed the same way and never come apart
            Vec3 mc = GiantHull.box(me).getCenter();
            Vec3 tc = GiantHull.box(them).getCenter();
            double ax = mc.x - tc.x;
            double az = mc.z - tc.z;
            double al = Math.hypot(ax, az);
            if (al > 1e-3 && p[0] * ax + p[1] * az < 0) {
                p[0] = ax / al;
                p[1] = az / al;
            }
            double share = 1;
            if (GiantHull.shows(o)) {
                double a = GiantHull.mass(me), b = GiantHull.mass(them);
                share = a + b > 0 ? b / (a + b) : 0.5;
            }
            double step = Math.min(cap, p[2] * share + 0.02);
            hb.setPos(hb.getX() + p[0] * step, hb.getY(), hb.getZ() + p[1] * step);
            // (and his drift that way stops: no going straight back in)
            double into = -(hb.vel.x * p[0] + hb.vel.z * p[1]);
            if (into > 0) hb.vel = hb.vel.add(p[0] * into, 0, p[1] * into);
            hb.giantPushed += step;
            hb.hullNow = null;
            me = hb.jjHull(); // (moved: worked out again)
        }
    }
}
