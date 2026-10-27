package net.jj.hollowbell.entity;

import java.util.UUID;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.item.BookRule;
import net.jj.hollowbell.world.WorldOne;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Backing down from a giants' fight, and where to (JJ, Oct 27). He never backs down from a fight a player started
 * (the book sent him, /giants meet, somebody riding him), and from a wild one only while /giants flee is on. When he
 * does, he climbs up out of reach and flies to the middle of the Bell Hollows, or to whoever holds his book when
 * they're in this world, at most {@link #FLEE_CAP} blocks; there he settles, rests and heals a little.
 */
public final class BellFlee {
    private final HollowbellEntity hb;

    BellFlee(HollowbellEntity hb) {
        this.hb = hb;
    }

    /** the furthest he flies after backing down, in blocks: when the spot is further, he rests where he got to */
    public static final double FLEE_CAP = 1500.0;
    /** the most time he spends on the way there (three minutes) */
    public static final long FLEE_TICKS = 3600L;
    /** how long he rests once he's there (three minutes) */
    public static final long REST_TICKS = 3600L;
    /** he heals while he rests, but never past this share of his health */
    public static final float REST_HEAL_TO = 0.5f;
    /** the share of his health he gets back each second of rest (a quarter over the whole rest) */
    public static final float REST_HEAL_PER_SECOND = 0.25f / (REST_TICKS / 20f);

    /** his meeting fight (meetFoe) was started by a player: the book's order or /giants meet */
    boolean ordered;
    /** on his way after backing down */
    private boolean run;
    /** where he flies to (null: straight away from the winner, as before) and where he set off from */
    private @Nullable Vec3 fleeTo, fleeStart;
    /** resting until this game time (-1 = not); restFor: what was left of it when he was saved */
    private long restUntil = -1L, restFor;

    /** for the tests: an owner and a home to fly to, in place of the real ones */
    private @Nullable UUID ownerForTests;

    private @Nullable Vec3 homeForTests;

    /** low enough, and a fight he may back down from (by: the giant behind the blow) */
    public boolean mayBackDown(@Nullable Entity by) {
        return BellConfig.V.fleeInWildFights && !playerFight(by);
    }

    /**
     * a fight a player started or ordered: somebody riding him, the giant behind the blow is one the book sent him
     * after, or a meeting fight the book or /giants meet started that is still on
     */
    public boolean playerFight(@Nullable Entity by) {
        if (hb.rider != null) return true;
        if (by != null && hb.huntOrdered && hb.hunted != null) {
            LivingEntity o = Giants.ownerOf(by);
            if (hb.hunted.equals(by.getUUID()) || o != null && hb.hunted.equals(o.getUUID())) return true;
        }
        if (!ordered || hb.meetFoe == null) return false;
        Entity foe = hb.level() instanceof ServerLevel sl ? sl.getEntity(hb.meetFoe) : null;
        return foe != null && foe.isAlive();
    }

    public void fleeSpotsForTests(@Nullable UUID owner, @Nullable Vec3 home) {
        ownerForTests = owner;
        homeForTests = home;
    }

    /** as though a player had started his meeting fight with this one (null: none) */
    public void orderedForTests(@Nullable UUID other) {
        hb.meetFoe = other;
        ordered = other != null;
    }

    private @Nullable Vec3 destination(ServerLevel sl) {
        if (ownerForTests != null || homeForTests != null) {
            Vec3 o = ownerSpot(sl, ownerForTests);
            return o != null ? o : homeForTests;
        }
        return realDestination(sl);
    }

    /** whoever holds his book when they're in this world, else the middle of the Bell Hollows (where /giants home goes) */
    public @Nullable Vec3 realDestination(ServerLevel sl) {
        Vec3 owner = ownerSpot(sl, BookRule.get(sl.getServer()).holder());
        if (owner != null) return owner;
        WorldOne w = WorldOne.get(sl.getServer());
        if (w.homeClaimed() && sl == sl.getServer().overworld())
            return new Vec3(w.homeX() + 0.5, hb.getY(), w.homeZ() + 0.5);
        return null;
    }

    private static @Nullable Vec3 ownerSpot(ServerLevel sl, @Nullable UUID owner) {
        ServerPlayer p = owner == null ? null : sl.getServer().getPlayerList().getPlayer(owner);
        return p != null && p.isAlive() && !p.isSpectator() && p.level() == sl ? p.position() : null;
    }

    private static Vec3 capped(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dz = to.z - from.z, d = Math.hypot(dx, dz);
        if (d <= FLEE_CAP) return to;
        return new Vec3(from.x + dx / d * FLEE_CAP, to.y, from.z + dz / d * FLEE_CAP);
    }

    private static double flat(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    private double arriveAt() {
        return Math.max(16.0, 12.0 + 50.0 * hb.bellScale());
    }

    public boolean running() {
        return run;
    }

    /** resting after the run: settled, calm, no new fights, healing a little */
    public boolean resting() {
        return restUntil >= 0;
    }

    public @Nullable Vec3 fleeTo() {
        return fleeTo;
    }

    /** he has just backed down: off to his spot (or away from the winner for a minute when there's none) */
    void startRun(ServerLevel sl, long now) {
        ordered = false;
        restUntil = -1L;
        run = true;
        fleeStart = hb.position();
        Vec3 d = destination(sl);
        fleeTo = d == null ? null : capped(fleeStart, d);
        hb.fleeUntil = now + (fleeTo != null ? FLEE_TICKS : 1200L);
        hb.yieldUntil = Math.max(hb.yieldUntil, hb.fleeUntil);
    }

    /** once a second: the run (kept up as his owner moves), then the rest, when he heals a little */
    public void fleeTick(ServerLevel sl) {
        long now = sl.getGameTime();
        if (restFor > 0 && restUntil < 0) {
            restUntil = now + restFor;
            restFor = 0;
        }
        if (run) {
            if (fleeTo != null && fleeStart != null) {
                Vec3 d = destination(sl);
                if (d != null) fleeTo = capped(fleeStart, d);
            }
            Vec3 here = hb.position();
            boolean there = fleeTo != null && flat(here, fleeTo) <= arriveAt();
            boolean far = fleeTo != null && fleeStart != null && flat(here, fleeStart) >= FLEE_CAP - 8.0;
            if (there || far || now >= hb.fleeUntil) restFrom(now);
        } else if (resting()) {
            if (now >= restUntil) restUntil = -1L;
            else {
                float most = hb.healthMax(), hp = hb.healthNow();
                if (most > 0f && hp > 0f && hp < most * REST_HEAL_TO)
                    hb.setHealthTo(Math.min(most * REST_HEAL_TO, hp + most * REST_HEAL_PER_SECOND));
            }
        }
    }

    /** the run is over (there, as far as he goes, or out of time): he comes down to rest, still backed down */
    private void restFrom(long now) {
        run = false;
        fleeTo = null;
        fleeStart = null;
        hb.fleeFrom = null;
        hb.fleeUntil = 0;
        restUntil = now + REST_TICKS;
        if (hb.yieldUntil > 0) hb.yieldUntil = Math.max(hb.yieldUntil, restUntil); // (through the rest too)
    }

    public void restForTests(long now) {
        restFrom(now);
    }

    /** the next stretch of the way to his spot while he flies there, or null */
    @Nullable
    Vec3 towardSpot(double step) {
        if (!run || fleeTo == null) return null;
        Vec3 me = hb.position();
        double dx = fleeTo.x - me.x, dz = fleeTo.z - me.z, d = Math.hypot(dx, dz);
        if (d <= step) return new Vec3(fleeTo.x, me.y, fleeTo.z);
        return new Vec3(me.x + dx / d * step, me.y, me.z + dz / d * step);
    }

    void save(CompoundTag tag, long gt) {
        CompoundTag t = new CompoundTag();
        t.putBoolean("Ordered", ordered);
        t.putBoolean("Run", run);
        if (fleeTo != null) {
            t.putDouble("ToX", fleeTo.x);
            t.putDouble("ToZ", fleeTo.z);
        }
        if (fleeStart != null) {
            t.putDouble("FromX", fleeStart.x);
            t.putDouble("FromZ", fleeStart.z);
        }
        if (restUntil > gt) t.putLong("RestFor", restUntil - gt);
        tag.put("Flee", t);
    }

    void load(CompoundTag tag) {
        CompoundTag t = tag.getCompound("Flee");
        ordered = t.getBoolean("Ordered");
        run = t.getBoolean("Run");
        double y = hb.getY();
        fleeTo = t.contains("ToX") ? new Vec3(t.getDouble("ToX"), y, t.getDouble("ToZ")) : null;
        fleeStart = t.contains("FromX") ? new Vec3(t.getDouble("FromX"), y, t.getDouble("FromZ")) : null;
        restFor = t.getLong("RestFor");
        restUntil = -1L;
    }
}
