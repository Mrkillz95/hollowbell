package net.jj.hollowbell.entity;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.UUID;
import net.jj.hollowbell.Adv;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.ModSounds;
import net.jj.hollowbell.item.CodexItem;
import net.jj.hollowbell.solid.GiantHull;
import net.jj.hollowbell.world.BellWorld;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Who he goes after, who he leaves alone, and meeting the other giants. */
final class BellTargets {
    private final HollowbellEntity hb;
    /** everyone and everything that has hurt him and is still about */
    final Grudges grudges = new Grudges();
    /** how long a player he's after has been out of his sight */
    final HuntSight.Memory sight = new HuntSight.Memory();

    BellTargets(HollowbellEntity hb) {
        this.hb = hb;
    }

    /** the player holding the book for him (the nearest one carrying it, within reach of the book) */
    public @Nullable Player bookHolder() {
        Player best = null;
        double bd = Double.MAX_VALUE;
        double r = BellConfig.V.bookRange;
        for (Player p : hb.level().players()) {
            if (!CodexItem.carriedBy(p)) continue;
            double d = p.distanceToSqr(hb);
            if (d < r * r && d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    /** he leaves this one alone: the book, the safe list, creative players */
    public boolean spares(Entity e) {
        if (e instanceof Player p) {
            // (on peaceful nobody is hunted, flung or poisoned)
            if (p.isCreative() || p.isSpectator() || hb.level().getDifficulty() == Difficulty.PEACEFUL) return true;
            if (hb.mood.turnedOn(p.getUUID()) || hb.mood.hunting(p.getUUID())) return false;
            Player holder = bookHolder();
            if (holder == null) return false;
            if (holder == p) return true;
            if (hb.level() instanceof ServerLevel sl)
                return BellWorld.get(sl.getServer()).onList(holder.getUUID(), p.getUUID());
            return false;
        }
        if (e instanceof HollowbellEntity || e instanceof Belling) return true;
        Player holder = bookHolder();
        if (holder != null && hb.level() instanceof ServerLevel sl && e instanceof LivingEntity le) {
            // the book holder's own tamed animals, always; then this one creature, or its whole kind, on the list
            if (le instanceof OwnableEntity o && holder.getUUID().equals(o.getOwnerUUID())) return true;
            BellWorld w = BellWorld.get(sl.getServer());
            return w.onList(holder.getUUID(), le.getUUID()) || w.kindOnList(holder.getUUID(), le.getType());
        }
        return false;
    }

    public boolean fairGame(@Nullable LivingEntity e) {
        return e != null
                && e.isAlive()
                && !e.isRemoved()
                && e.level() == hb.level()
                // (its place isn't a number, from a fault in another mod: nothing can go after it or aim at it)
                && BellMoves.finite(e.position())
                && !spares(e)
                && !(e instanceof ArmorStand)
                && e != hb.rider
                && !hb.moves.caught(e)
                // (what another Hollowbell holds is his: two of them would pull it back and forth every tick)
                && !(e.getVehicle() instanceof Seat st && st.ownerId() != hb.getId())
                // giants only while fightGiants is on
                && (BellConfig.V.fightGiants || !Giants.isGiant(e))
                // (a giant that has backed down is left alone, and backing down he leaves every giant alone)
                && !(Giants.isGiant(e) && (Meeting.yielding(e) || hb.yielding()))
                // anything standing where a woken crown holds him off is out of his reach
                && !hb.warded(e.getX(), e.getZ());
    }

    /** bound to a circle, anything too far outside it is past his reach and let go */
    boolean pastHisCircle(Vec3 at) {
        return hb.bound() && Math.hypot(at.x - hb.boundX, at.z - hb.boundZ) > hb.boundR + 110 * hb.bellScale() + 40;
    }

    /** where he looks out from: under his bell, round its rim toward them, low down, his middle and his crown */
    List<Vec3> lookouts(LivingEntity who) {
        float s = hb.bellScale();
        double rimY = hb.getY() + hb.rig.rimY * s;
        double dx = who.getX() - hb.getX(), dz = who.getZ() - hb.getZ(), d = Math.sqrt(dx * dx + dz * dz);
        double rr = d > 1e-3 ? hb.bellRadius() * 0.95 / d : 0;
        return List.of(
                new Vec3(hb.getX(), rimY - 2, hb.getZ()),
                new Vec3(hb.getX() + dx * rr, rimY, hb.getZ() + dz * rr),
                new Vec3(hb.getX(), hb.getY() + 1, hb.getZ()),
                new Vec3(hb.getX(), hb.getY() + (hb.rig.rimY + hb.rig.crownY) * 0.5 * s, hb.getZ()),
                new Vec3(hb.getX(), hb.getY() + hb.rig.crownY * s + 2, hb.getZ()));
    }

    /** a player he goes after on sight alone (not one he holds something against: those he hunts seen or not) */
    private boolean bySightOnly(Player p) {
        return !grudges.has(p.getUUID())
                && !hb.mood.turnedOn(p.getUUID())
                && !hb.mood.hunting(p.getUUID())
                && !p.getUUID().equals(hb.hunted);
    }

    void pickTarget() {
        LivingEntity t = hb.getTarget();
        boolean foe = t != null && hb.meetFoe != null && hb.meetFoe.equals(t.getUUID());
        // a player he picked on sight and hasn't seen for a while: let go
        if (t instanceof Player tp
                && !foe
                && bySightOnly(tp)
                && !sight.keep(hb.level(), lookouts(tp), tp, hb.level().getGameTime())) {
            hb.setTarget(null);
            t = null;
        }
        if (t != null
                && (!fairGame(t)
                        || hb.horiz(t.position())
                                > (foe ? Meeting.range(hb.bellScale()) * 2 + hb.senseRange() : hb.senseRange() * 1.6)
                        || pastHisCircle(t.position())
                        || (hb.isGuardian()
                                && hb.home != null
                                && t.position().distanceTo(hb.home) > hb.guardRange() * 1.3
                                && hb.angerTicks <= 0))) {
            hb.setTarget(null);
            t = null;
        }
        if (hb.hunted != null && hb.level() instanceof ServerLevel sl) {
            Entity h = sl.getEntity(hb.hunted);
            long now = sl.getGameTime();
            LivingEntity was = hb.huntRef.get();
            if (h == null && was != null && was.isDeadOrDying()) {
                dropHunt();
            } else if (h instanceof LivingEntity le && le.isAlive()) {
                hb.huntRef = new WeakReference<>(le);
                hb.huntSeen = le.position();
                hb.huntLostAt = -1;
                // put on the safe list since, or a giant that has backed down: let go for good
                if (spares(le) || (Giants.isGiant(le) && (Meeting.yielding(le) || hb.yielding()))) {
                    if (hb.getTarget() == le) hb.setTarget(null);
                    dropHunt();
                } else if (fairGame(le)) {
                    hb.setTarget(le);
                    return;
                }
            } else if (h != null) dropHunt(); // dead
            else if (!hb.huntOrdered) hb.hunted = null;
            else {
                // out of sight: looked for a while where it was
                if (hb.huntLostAt < 0) hb.huntLostAt = now;
                else if (now - hb.huntLostAt > HollowbellEntity.HUNT_LOST_TICKS) dropHunt();
            }
        }
        if (hb.level() instanceof ServerLevel sl) {
            if (hb.tickCount % 20 == 7) grudges.tick(sl, hb, hb.senseRange() * 1.6);
            // he doesn't cool off while what hurt him is still about
            if (t != null && grudges.has(t.getUUID())) hb.angerTicks = Math.max(hb.angerTicks, 200);
            // and when it's back in reach after hiding or walking off a way, he goes for it again
            if (t == null && hb.tickCount % 20 == 11 && !grudges.isEmpty() && freeForGrudges()) {
                LivingEntity g =
                        grudges.nearest(sl, hb, hb.senseRange(), e -> fairGame(e) && !pastHisCircle(e.position()));
                if (g != null) {
                    hb.setTarget(g);
                    hb.angerTicks = Math.max(hb.angerTicks, 1200);
                    return;
                }
            }
        }
        if (hb.tickCount % 20 != 0) return;
        // after a creature (not one that hurt him, nor a giant he's meeting): a player he sees comes first
        boolean onMob = t != null
                && !(t instanceof Player)
                && !foe
                && !grudges.has(t.getUUID())
                && !t.getUUID().equals(hb.hunted);
        if (t != null && !onMob) return;
        if (hb.variant() == HollowbellEntity.CALM && hb.angerTicks <= 0) return;
        double r = hb.senseRange();
        Player best = null;
        double bd = Double.MAX_VALUE;
        for (Player p : hb.level().players()) {
            if (!fairGame(p) || pastHisCircle(p.position())) continue;
            double d = hb.horiz(p.position());
            if (d > r) continue;
            if (hb.isGuardian() && hb.home != null && p.position().distanceTo(hb.home) > hb.guardRange()) continue;
            // (picked on sight: one he can't see from anywhere on him is left be)
            if (d < bd && (!bySightOnly(p) || HuntSight.sees(hb.level(), lookouts(p), p))) {
                bd = d;
                best = p;
            }
        }
        if (best != null) {
            hb.setTarget(best);
            sight.saw(best, hb.level().getGameTime());
            return;
        }
        if (t != null) return;
        // no player: hostile creatures (a guardian clears them off his ground; a hunter goes for them too)
        if (hb.tickCount % 40 != 0) return;
        // (getting away from a giant he backed down to, he doesn't stop for creatures on the way)
        if (hb.flight.escaping()) return;
        double mr = hb.isGuardian() ? hb.guardRange() : r * 0.8;
        Vec3 c = hb.isGuardian() && hb.home != null ? hb.home : hb.position();
        LivingEntity mob = null;
        double md = Double.MAX_VALUE;
        for (LivingEntity e : hb.level()
                .getEntitiesOfClass(
                        LivingEntity.class,
                        new AABB(c, c).inflate(mr, 60 * hb.bellScale() + 30, mr),
                        // (another giant is met by the meeting rules, not picked like any monster: see meetTick; nor is
                        // its
                        // helper, which he only fights when it goes for him)
                        e -> e instanceof Enemy
                                && !(e instanceof Player)
                                && !Giants.isGiant(e)
                                && !e.getTags().contains(Giants.KIN)
                                && fairGame(e)
                                && !pastHisCircle(e.position()))) {
            double d = hb.horiz(e.position());
            if (d < md) {
                md = d;
                mob = e;
            }
        }
        if (mob != null) hb.setTarget(mob);
    }

    /** not under an order, ridden or steered: a grudge may pick his target */
    private boolean freeForGrudges() {
        return hb.rider == null
                && hb.goal == null
                && hb.comeTo == null
                && hb.fetching == null
                && !hb.stay
                && !hb.following()
                && !hb.yielding()
                && !hb.isDeadOrDying();
    }

    /** whatever hurt him (a player, a creature, another giant) is held against it */
    void remember(@Nullable Entity e) {
        if (!(e instanceof LivingEntity) || e == hb || hb.level().isClientSide) return;
        LivingEntity g = giantOf(e);
        grudges.hold((g != null ? g : e).getUUID(), hb.level().getGameTime());
    }

    public boolean holdsGrudge(@Nullable Entity e) {
        return e != null && grudges.has(e.getUUID());
    }

    /** the book: go and get these */
    public void sendAfter(LivingEntity e) {
        hb.wakeUp();
        // it overrides what he was doing: his own target, wandering, holding still, an order to go somewhere
        hb.goal = null;
        hb.wanderTo = null;
        hb.comeTo = null;
        hb.holdThere = false;
        hb.comeLostAt = 0;
        hb.setStay(false);
        hb.flight.follow(null);
        hb.hunted = e.getUUID();
        hb.huntOrdered = true;
        hb.huntSeen = e.position();
        hb.huntLostAt = -1;
        hb.huntRef = new WeakReference<>(e);
        hb.setTarget(e);
        hb.angerTicks = 1200;
        // another giant: a giants' fight, by their rules (who backs down, who won, the wait before the next)
        if (Giants.isGiant(e) && hb.meetFoe == null) {
            LivingEntity g = giantOf(e);
            if (g != null && g != hb) startMeeting(g, true);
        }
    }

    /** a hunt the book could still give (the reason it can't, as a message key, or null) */
    public @Nullable String huntRefusal(LivingEntity e) {
        if (e == hb) return "look_him";
        if (e instanceof Belling) return "look_his_own";
        if (e instanceof HollowbellEntity) return "look_kin";
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return "look_creative";
        if (Giants.isGiant(e)) {
            if (!BellConfig.V.fightGiants) return "look_giants_off";
            if (hb.yielding()) return "look_he_backed_down";
            if (Meeting.yielding(e)) return "look_it_backed_down";
            if (hb.meetFoe == null || !hb.meetFoe.equals(e.getUUID())) {
                long left = meetCooldownLeft(e.getUUID());
                if (left > 0) return "look_cooldown:" + Math.max(1, (left + 1199) / 1200);
            }
        }
        if (hb.warded(e.getX(), e.getZ())) return "look_warded";
        return null;
    }

    /** the target a player sent him after */
    public @Nullable UUID huntedByOrder() {
        return hb.huntOrdered ? hb.hunted : null;
    }

    void dropHunt() {
        hb.hunted = null;
        hb.huntOrdered = false;
        hb.huntSeen = null;
        hb.huntLostAt = -1;
        hb.huntRef = new WeakReference<>(null);
    }

    public void clearHitList() {
        dropHunt();
        hb.setTarget(null);
        hb.angerTicks = 0;
    }

    /** a new order (stay, come, go there) ends the one to go after something */
    public void endOrderedHunt() {
        if (hb.huntOrdered) {
            if (hb.getTarget() != null
                    && hb.hunted != null
                    && hb.hunted.equals(hb.getTarget().getUUID())) hb.setTarget(null);
            dropHunt();
        }
    }

    public void forgiveAll() {
        hb.mood.settle(null);
        grudges.clear();
        clearHitList();
    }

    /** ticks before he can meet this one again (0 = he can) */
    public long meetCooldownLeft(UUID other) {
        Long t = hb.metLately.get(other);
        return t == null ? 0 : Math.max(0, t - hb.level().getGameTime());
    }

    @Nullable
    Vec3 keepAwayFrom() {
        long now = hb.level().getGameTime();
        if (hb.fleeFrom != null && now < hb.fleeUntil) return hb.fleeFrom;
        return hb.meetAvoid != null ? hb.avoidAt : null;
    }

    /**
     * free to meet another giant: awake, under no order or trip, after nobody, not ridden or staying, not backing down
     */
    public boolean freeToMeet() {
        return BellConfig.V.meetings
                && !hb.asleep
                && !hb.frozen
                && !hb.stay
                && hb.goal == null
                && hb.comeTo == null
                && hb.fetching == null
                && hb.hunted == null
                && hb.getTarget() == null
                && hb.rider == null
                && !hb.yielding()
                && !hb.bound()
                && !hb.isDeadOrDying()
                && hb.meetFoe == null
                && !hb.flee.resting()
                && hb.level().getGameTime() >= hb.fleeUntil;
    }

    /** the giant this body or part belongs to, as a creature */
    static @Nullable LivingEntity giantOf(Entity e) {
        return Giants.ownerOf(e);
    }

    /**
     * Whether his blows, stings and clouds may touch this: anything that isn't a giant, and of the giants only the one
     * he's going for (or the move he's doing now is aimed at), the one in his meeting fight, or any while a player
     * steers him. A giant just drifting past under his strands is left alone (else a pair meant to keep apart would
     * start a fight by accident).
     */
    public boolean fightingGiant(@Nullable Entity e) {
        if (e == null || !Giants.isGiant(e)) return true;
        if (hb.rider != null) return true;
        LivingEntity g = giantOf(e);
        if (g == null) return true;
        // a giant that has backed down (or him, backing down): nothing of his touches it, not even what's still flying
        if (Meeting.yielding(g) || hb.yielding()) return false;
        for (LivingEntity t : new LivingEntity[] {hb.getTarget(), hb.moves.moveTarget()}) {
            if (t != null && (t == g || giantOf(t) == g))
                return true; // the one he's going for, or his move is aimed at
        }
        return hb.meetFoe != null && hb.meetFoe.equals(g.getUUID());
    }

    void meetTick(long now) {
        if (!(hb.level() instanceof ServerLevel sl)) return;
        if (hb.tickCount % 20 == 0) hb.flee.fleeTick(sl);
        if (hb.yieldUntil > 0 && now >= hb.yieldUntil) {
            hb.removeTag(Meeting.YIELD);
            hb.yieldUntil = 0;
        }
        if (!hb.metLately.isEmpty() && hb.tickCount % 200 == 0)
            hb.metLately.values().removeIf(t -> t < now);
        // a meeting fight going on
        if (hb.meetFoe != null) {
            Entity f = sl.getEntity(hb.meetFoe);
            LivingEntity foe = f == null ? null : giantOf(f);
            if (f == null && hb.tickCount < 200) {
                // (just loaded: the other one's land may come in a little after his)
            } else if (foe == null || !foe.isAlive() || foe.isRemoved()) {
                // gone (dead, or out of reach): if it died, he won
                boolean won = foe != null && foe.isDeadOrDying();
                hb.meetFoe = null;
                if (hb.getTarget() == foe) hb.setTarget(null);
                if (won) victory();
            } else if (Meeting.yielding(foe)) {
                hb.meetFoe = null; // it backed down: he won
                if (hb.getTarget() == foe) hb.setTarget(null);
                if (hb.moves.moveTarget() == foe) hb.moves.stopNow();
                hb.moves.dropClouds(); // (his spores don't hang about on it)
                hb.angerTicks = 0;
                victory();
            } else if (!hb.yielding()
                    && BellConfig.V.yieldAt > 0f
                    && hb.healthNow() < hb.healthMax() * BellConfig.V.yieldAt
                    && hb.flee.mayBackDown(foe)) {
                backDown(foe);
            } else if (hb.getTarget() != foe && fairGame(foe)) {
                hb.setTarget(foe);
                hb.angerTicks = Math.max(hb.angerTicks, 600);
            }
        }
        // keeping out of another giant's way, until they're well apart
        if (hb.meetAvoid != null) {
            Entity o = sl.getEntity(hb.meetAvoid);
            if (o == null || !o.isAlive() || hb.horiz(o.position()) > Meeting.range(hb.bellScale()) * 1.5) {
                hb.meetAvoid = null;
                hb.avoidAt = null;
            } else hb.avoidAt = o.position();
        }
        if (HollowbellEntity.meetEvery <= 0
                || hb.tickCount % HollowbellEntity.meetEvery != 0
                || !freeToMeet()
                || hb.meetAvoid != null) return;
        double r = Meeting.range(hb.bellScale());
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : sl.getEntities(
                (Entity) null,
                new AABB(hb.getX() - r, hb.getY() - r, hb.getZ() - r, hb.getX() + r, hb.getY() + r, hb.getZ() + r),
                x -> x != hb && x.isAlive() && Giants.isGiant(x) && !(x instanceof Belling))) {
            LivingEntity g = giantOf(e);
            if (g == null || g == hb || Meeting.yielding(g) || meetCooldownLeft(g.getUUID()) > 0 || Giants.ridden(g))
                continue;
            double d = hb.horiz(g.position());
            if (d > r || d >= bd) continue;
            bd = d;
            best = g;
        }
        if (best == null) return;
        Meeting.Way way = Meeting.way(Meeting.ME, Meeting.kindOf(best));
        if (way == Meeting.Way.FIGHT && BellConfig.V.fightGiants) startMeeting(best);
        else if (way == Meeting.Way.AVOID) {
            hb.meetAvoid = best.getUUID();
            hb.avoidAt = best.position();
            // (no cooldown for keeping away: if they drift close again later, he keeps away again)
        }
    }

    /** a meeting fight with this giant starts now (the table said so) */
    public boolean startMeeting(LivingEntity other) {
        return startMeeting(other, false);
    }

    /** the same; byPlayer: an operator's /giants meet or the book's order, a fight he never backs down from */
    public boolean startMeeting(LivingEntity other, boolean byPlayer) {
        if (!(hb.level() instanceof ServerLevel sl) || other == hb || hb.isDeadOrDying()) return false;
        long now = hb.level().getGameTime();
        hb.wakeUp();
        hb.meetFoe = other.getUUID();
        hb.flee.ordered = byPlayer;
        hb.meetAvoid = null;
        hb.avoidAt = null;
        hb.metLately.put(other.getUUID(), now + BellConfig.V.meetCooldown * 1200L);
        hb.setStay(false);
        hb.setTarget(other);
        hb.angerTicks = Math.max(hb.angerTicks, 1200);
        String them = Meeting.kindOf(other);
        // the one that starts it tells everyone near; the shared board stops the other one saying it too
        boolean tell = Meeting.claimMeetingLine(hb.getUUID(), other.getUUID(), now);
        for (ServerPlayer p : sl.players()) {
            double dMe = p.distanceToSqr(hb), dThem = p.distanceToSqr(other);
            if (tell && (dMe < 256 * 256 || dThem < 256 * 256))
                p.sendSystemMessage(Component.translatable(
                        "message.hollowbell.giants_fight", Meeting.name(Meeting.ME, true), Meeting.name(them, false)));
            // (the advancement for seeing it)
            if (dMe < 128 * 128 || dThem < 128 * 128) Adv.grant(p, "watch_giants");
        }
        HollowbellMod.LOG.info("A Hollowbell meets {} and they fight", Meeting.name(them, false));
        return true;
    }

    /** he's had enough of this giant: he backs down, leaves every giant alone for two minutes and gets away from it */
    public void backDown(Entity from) {
        if (hb.yielding() || !(hb.level() instanceof ServerLevel sl)) return;
        long now = hb.level().getGameTime();
        hb.addTag(Meeting.YIELD);
        hb.yieldUntil = now + 2400;
        hb.fleeFrom = from.position();
        hb.fleeUntil = now + 1200;
        hb.meetFoe = null;
        hb.flee.startRun(sl, now); // up out of reach, then off to his ground or to whoever holds his book
        clearHitList();
        grudges.drop(from.getUUID());
        hb.stopFetch();
        hb.moves.stopNow();
        hb.moves.dropClouds();
        hb.metLately.put(from.getUUID(), now + BellConfig.V.meetCooldown * 1200L);
        String them = Meeting.kindOf(from);
        for (ServerPlayer p : sl.players())
            if (p.distanceToSqr(hb) < 256 * 256 || p.distanceToSqr(from) < 256 * 256)
                p.sendSystemMessage(Component.translatable(
                        "message.hollowbell.giant_yields", Meeting.name(Meeting.ME, true), Meeting.name(them, true)));
        HollowbellMod.LOG.info("A Hollowbell backs down from {}", Meeting.name(them, false));
    }

    /** he won: a big toll and a flare of his glow, then back to his own business */
    void victory() {
        hb.pulse(1.6f);
        hb.sound(hb.position().add(0, (hb.rig.rimY + 20) * hb.bellScale(), 0), ModSounds.TOLL_BIG, 4f, 1.1f);
        hb.particles(
                ParticleTypes.END_ROD,
                hb.position().add(0, (hb.rig.rimY + 40) * hb.bellScale(), 0),
                120,
                50 * hb.bellScale() + 3,
                0.2);
    }

    /**
     * A creature up in the air that he's after (not a player: their own game bumps into him): he rises up round it
     * and takes it in among his strands, so his bell doesn't knock it up ahead of him (it would ride up on him, and
     * he would climb after it for ever).
     */
    boolean huntedInTheAir(Entity e) {
        return e == hb.getTarget()
                && !(e instanceof Player)
                && !e.onGround()
                && e.getY() > hb.groundAt(e.getX(), e.getZ()) + 3;
    }

    @Nullable
    double[] giantTargetPlan(LivingEntity t) {
        if (!Giants.isGiant(t)) return null;
        LivingEntity o = Giants.ownerOf(t);
        if (o == null) o = t;
        double[] me = hb.jjHull(), them = GiantHull.of(o, Giants.TAG);
        if (me.length == 0 || them.length == 0) return null;
        float s = hb.bellScale();
        // the lowest of his body and arms, over his own spot
        double myBottom = Double.MAX_VALUE;
        for (int i = 0; i < me.length; i += GiantHull.STRIDE) myBottom = Math.min(myBottom, me[i + 3]);
        double under = myBottom - hb.getY();
        // the top of its body anywhere under all of him
        double reach = hb.bellRadius() + 4 * s;
        double top = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < them.length; i += GiantHull.STRIDE)
            if (Math.hypot(them[i] - hb.getX(), them[i + 1] - hb.getZ()) < reach + them[i + 2])
                top = Math.max(top, them[i + 4]);
        double keepY = top == Double.NEGATIVE_INFINITY ? Double.NaN : top + hb.contactGap() - under;
        double gap = GiantHull.gap(me, them);
        return new double[] {keepY, gap};
    }
}
