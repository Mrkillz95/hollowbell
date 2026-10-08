package net.jj.hollowbell.world;

import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Out of the world, like the Mountain. When nobody is anywhere near him, he's written down in full (health, pods,
 * mood, the lot) and taken out of the game. From then on he's a line on a map: where he set off from, where he's
 * going, when he left and how fast he drifts. The book and /hollowbell where work out where he ought to be from
 * that, and the moment anybody comes near that spot he's put back there, whole, still going the same way.
 */
public final class Away extends SavedData {
    public static final class Rec {
        public UUID id;
        public CompoundTag body;
        public String dim = "minecraft:overworld";
        public double fromX, fromZ, toX, toZ, speed, lift;
        public long start;
        public boolean going, stay;
        public float scale, hp, hpMax;
        public int variant;
        /** coming to this player: the trip is aimed at them again every ten seconds */
        public @Nullable UUID follow;
        /** a movement order: once there he holds still */
        public boolean hold;
        long aimedAt;
        /** real time (ms) the one he's coming to went missing; 0 = they're about */
        public long followLostAt;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", id);
            t.put("Body", body);
            t.putString("Dim", dim);
            t.putDouble("FromX", fromX); t.putDouble("FromZ", fromZ);
            t.putDouble("ToX", toX); t.putDouble("ToZ", toZ);
            t.putDouble("Speed", speed); t.putDouble("Lift", lift);
            t.putLong("Start", start);
            t.putBoolean("Going", going); t.putBoolean("Stay", stay);
            t.putFloat("Scale", scale); t.putFloat("Hp", hp); t.putFloat("HpMax", hpMax);
            t.putInt("Variant", variant);
            if (follow != null) t.putUUID("Follow", follow);
            t.putBoolean("Hold", hold);
            if (followLostAt != 0) t.putLong("FollowLostAt", followLostAt);
            return t;
        }

        static Rec load(CompoundTag t) {
            Rec r = new Rec();
            r.id = t.getUUID("Id");
            r.body = t.getCompound("Body");
            r.dim = t.getString("Dim");
            r.fromX = t.getDouble("FromX"); r.fromZ = t.getDouble("FromZ");
            r.toX = t.getDouble("ToX"); r.toZ = t.getDouble("ToZ");
            r.speed = t.getDouble("Speed"); r.lift = t.getDouble("Lift");
            r.start = t.getLong("Start");
            r.going = t.getBoolean("Going"); r.stay = t.getBoolean("Stay");
            r.scale = t.getFloat("Scale"); r.hp = t.getFloat("Hp"); r.hpMax = t.getFloat("HpMax");
            r.variant = t.getInt("Variant");
            r.follow = t.hasUUID("Follow") ? t.getUUID("Follow") : null;
            r.hold = t.getBoolean("Hold");
            r.followLostAt = t.getLong("FollowLostAt");
            return r;
        }

        /** where he ought to be by now: how long he's been going, times how fast he goes */
        public Vec3 spot(long now) {
            if (!going) return new Vec3(fromX, 0, fromZ);
            double dx = toX - fromX, dz = toZ - fromZ, total = Math.sqrt(dx * dx + dz * dz);
            if (total < 1) return new Vec3(toX, 0, toZ);
            double f = Math.min(1.0, Math.max(0, now - start) * speed / total);
            return new Vec3(fromX + dx * f, 0, fromZ + dz * f);
        }

        /** whole minutes of going he has left */
        public int minutesLeft(long now) {
            if (!going || speed <= 0) return 0;
            double total = Math.hypot(toX - fromX, toZ - fromZ);
            double left = Math.max(0, total - Math.max(0, now - start) * speed);
            return (int) Math.ceil(left / speed / 1200.0);
        }

        /** he's got there: the sum stops and he's left floating at the spot */
        void settle(long now) {
            if (!going) return;
            Vec3 at = spot(now);
            if (Math.hypot(toX - at.x, toZ - at.z) > 0.5) return;
            fromX = at.x; fromZ = at.z; going = false;
            body.remove("GoalX"); body.remove("GoalY"); body.remove("GoalZ");
            // coming to somebody who has left the game: he waits here, still on the order, until they're back
            if (hold && follow != null && followLostAt != 0) return;
            // an order got him there: he holds still until he's told something else
            if (hold) { stay = true; hold = false; follow = null; body.putBoolean("HoldThere", false); body.remove("ComeTo"); }
        }

        /** he gave up waiting for somebody who went: back to his own business */
        void giveUpWaiting() {
            follow = null; hold = false; followLostAt = 0;
            body.putBoolean("HoldThere", false); body.remove("ComeTo"); body.remove("ComeLostAt");
        }
    }

    private final Map<UUID, Rec> recs = new LinkedHashMap<>();
    private static final SavedData.Factory<Away> FACTORY = new SavedData.Factory<>(Away::new, Away::load, null);

    public static Away get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "hollowbell_away");
    }

    /** the test world has nobody in it, so every Hollowbell in it would step straight out. The away tests do it themselves. */
    private static final boolean IN_TESTS = System.getProperty("fabric-api.gametest") != null;

    /**
     * How far the nearest player has to be before he steps out of the world. At least the edge of what the game
     * keeps running, and never while you could still see his boss bars. A number in the settings wins.
     */
    public static double awayRange(@Nullable MinecraftServer server, float scale) {
        int set = HollowbellConfig.V.awayBlocks;
        if (set > 0) return Math.max(96, set);
        int sim = server == null ? 10 : Math.max(2, server.getPlayerList().getSimulationDistance());
        return Math.max(sim * 16 + 64, Math.max(300 * Math.max(0.15f, scale) + 140, HollowbellEntity.barRange(scale) + 16));
    }

    /** and how close somebody has to come to where the sum says he is before he's put back */
    public static double backRange(@Nullable MinecraftServer server, float scale) { return Math.max(64, awayRange(server, scale) - 48); }

    public List<Rec> all() { return new ArrayList<>(recs.values()); }
    public int count() { return recs.size(); }
    public @Nullable Rec get(UUID id) { return recs.get(id); }
    public void forget(UUID id) { if (recs.remove(id) != null) setDirty(); unpark(id); }
    public void forgetAll() { if (!recs.isEmpty() || !parked.isEmpty()) { recs.clear(); parked.clear(); setDirty(); } }

    /** the nearest one out there in this dimension, from this spot */
    public @Nullable Rec nearest(ServerLevel l, Vec3 from) {
        String dim = l.dimension().location().toString();
        long now = l.getGameTime();
        Rec best = null; double bd = Double.MAX_VALUE;
        for (Rec r : recs.values()) {
            if (!r.dim.equals(dim)) continue;
            Vec3 s = r.spot(now);
            double d = Mth.square(s.x - from.x) + Mth.square(s.z - from.z);
            if (d < bd) { bd = d; best = r; }
        }
        return best;
    }

    /** write him down and take him out of the game */
    public void takeAway(ServerLevel l, HollowbellEntity h, CompoundTag body, @Nullable Vec3 dest, double speed, double lift) {
        Rec r = new Rec();
        r.id = h.getUUID();
        r.body = body;
        r.dim = l.dimension().location().toString();
        r.fromX = h.getX(); r.fromZ = h.getZ();
        r.start = l.getGameTime();
        r.speed = Math.max(0.01, speed);
        r.lift = Math.max(0, lift);
        r.stay = h.staying();
        r.scale = h.bellScale(); r.hp = h.healthNow(); r.hpMax = h.healthMax(); r.variant = h.variant();
        r.follow = h.comingTo(); r.hold = h.holdsThere(); r.aimedAt = r.start;
        parked.remove(r.id);
        if (dest != null && Math.hypot(dest.x - h.getX(), dest.z - h.getZ()) > 8) { r.going = true; r.toX = dest.x; r.toZ = dest.z; }
        recs.put(r.id, r);
        setDirty();
        HollowbellMod.LOG.info("A Hollowbell has stepped out of the world at {}, {}{}", Mth.floor(h.getX()), Mth.floor(h.getZ()),
                r.going ? " going to " + Mth.floor(r.toX) + ", " + Mth.floor(r.toZ) : "");
    }

    /** the book reaching him while he's a sum: he turns where the sum says he is and sets off for the new spot */
    public boolean send(ServerLevel l, UUID id, Vec3 to) {
        Rec r = recs.get(id);
        if (r == null) return false;
        // bound to a circle, he keeps to it out of the world too
        if (r.body.getInt("BoundR") > 0) {
            double bx = r.body.getDouble("BoundX"), bz = r.body.getDouble("BoundZ");
            int br = r.body.getInt("BoundR");
            double dx = to.x - bx, dz = to.z - bz, len = Math.hypot(dx, dz);
            if (len > br) to = new Vec3(bx + dx / len * br, to.y, bz + dz / len * br);
        }
        long now = l.getGameTime();
        Vec3 at = r.spot(now);
        // a woken crown's circle: the trip stops at its edge rather than go through it
        to = WorldOne.get(l.getServer()).wardStop(l, at, to);
        r.fromX = at.x; r.fromZ = at.z; r.start = now;
        r.toX = to.x; r.toZ = to.z;
        r.going = Math.hypot(to.x - at.x, to.z - at.z) > 8;
        r.stay = false;
        r.aimedAt = now;
        setDirty();
        return true;
    }

    /** a movement order out of the world: the trip, who he is coming to, and that he holds still once there */
    public boolean order(ServerLevel l, UUID id, Vec3 to, @Nullable UUID follow) {
        Rec r = recs.get(id);
        if (r == null || !send(l, id, to)) return false;
        r.follow = follow;
        r.hold = true;
        r.followLostAt = 0;
        r.body.remove("ComeLostAt");
        r.body.putBoolean("HoldThere", true);
        if (follow != null) r.body.putUUID("ComeTo", follow); else r.body.remove("ComeTo");
        r.body.putBoolean("Asleep", false);
        setDirty();
        return true;
    }

    // ------------------------------------------------------------------ where the ones in unloaded land are

    /** one of him left in the world when his chunk was put away: where, so an order can reach him */
    public record Parked(String dim, double x, double z, double speed) {}
    private final Map<UUID, Parked> parked = new LinkedHashMap<>();

    public void noteParked(HollowbellEntity h) {
        parked.put(h.getUUID(), new Parked(h.level().dimension().location().toString(), h.getX(), h.getZ(), h.travelSpeed()));
        setDirty();
    }
    public void unpark(UUID id) { if (parked.remove(id) != null) setDirty(); }
    public Map<UUID, Parked> parked() { return java.util.Collections.unmodifiableMap(parked); }

    /** for the tests: one written down as lying at this spot */
    public void noteParked(UUID id, String dim, double x, double z, double speed) { parked.put(id, new Parked(dim, x, z, speed)); setDirty(); }

    public void forgetParked() { if (!parked.isEmpty()) { parked.clear(); setDirty(); } }

    /** every one written down as lying still where nobody was near, that isn't loaded now or out of the world */
    public Map<UUID, Parked> lying(MinecraftServer server) {
        Map<UUID, Parked> out = new LinkedHashMap<>();
        for (var e : parked.entrySet()) {
            var key = net.minecraft.resources.ResourceLocation.tryParse(e.getValue().dim());
            ServerLevel l = key == null ? null : server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, key));
            if (l == null || recs.containsKey(e.getKey()) || l.getEntity(e.getKey()) != null) continue;
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    /** the nearest one lying still in this dimension, from this spot */
    public @Nullable Map.Entry<UUID, Parked> nearestLying(ServerLevel l, Vec3 from) {
        String dim = l.dimension().location().toString();
        Map.Entry<UUID, Parked> best = null; double bd = Double.MAX_VALUE;
        for (var e : lying(l.getServer()).entrySet()) {
            if (!e.getValue().dim().equals(dim)) continue;
            double d = Mth.square(e.getValue().x() - from.x) + Mth.square(e.getValue().z() - from.z);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    /** as the server stops: every one loaded right now is written down where he is (he's saved with his chunk without
     * being put away, and after a restart nothing may load that chunk again) */
    public static void noteAllLoaded(MinecraftServer server) {
        Away a = get(server);
        for (ServerLevel l : server.getAllLevels())
            for (HollowbellEntity h : l.getEntities(net.jj.hollowbell.ModEntities.HOLLOWBELL, e -> !e.isRemoved() && !e.isDeadOrDying() && !e.steppedOut()))
                a.noteParked(h);
    }

    /** for the tests: the world's copy swapped for one read back from a save */
    public static void reloadForTests(MinecraftServer server) {
        server.overworld().getDataStorage().set("hollowbell_away", load(get(server).save(new CompoundTag(), server.registryAccess()), server.registryAccess()));
    }

    /** stop where you are: the sum stops and he's left out there (or, again, let him go on his way) */
    public boolean stay(ServerLevel l, UUID id, boolean on) {
        Rec r = recs.get(id);
        if (r == null) return false;
        long now = l.getGameTime();
        Vec3 at = r.spot(now);
        r.fromX = at.x; r.fromZ = at.z; r.start = now;
        if (on) r.going = false;
        r.stay = on;
        setDirty();
        return true;
    }

    // ------------------------------------------------------------------ once a second

    private static int sweepTick;

    /**
     * Once a second: every Hollowbell still in the world with nobody near him steps out, and every one out there
     * that somebody has come near is put back. This runs off the server's own clock rather than his, so one the
     * game has stopped ticking is still found.
     */
    public static void tick(MinecraftServer server) {
        if (--sweepTick > 0) return;
        sweepTick = 20;
        if (IN_TESTS) { get(server).bringBackNear(server); return; }
        if (!HollowbellConfig.V.offscreenTravel) { get(server).bringBackAll(server); return; }
        for (ServerLevel l : server.getAllLevels()) {
            List<HollowbellEntity> here = new ArrayList<>(l.getEntities(ModEntities.HOLLOWBELL, e -> !e.isRemoved() && !e.isDeadOrDying()));
            for (HollowbellEntity h : here) h.stepAsideIfAlone(20);
        }
        get(server).bringBackNear(server);
    }

    private void bringBackNear(MinecraftServer server) {
        if (recs.isEmpty()) return;
        for (Rec r : new ArrayList<>(recs.values())) {
            ServerLevel l = level(server, r.dim);
            if (l == null) continue;
            long now = l.getGameTime();
            // coming to somebody: aimed at where they are now, every ten seconds
            // (gone from the game: he goes on to where he last saw them and waits; 20 real minutes, then he gives up)
            if (r.follow != null && r.hold && (now - r.aimedAt >= 200 || r.followLostAt != 0)) {
                ServerPlayer who = server.getPlayerList().getPlayer(r.follow);
                if (who != null && who.level() == l) {
                    boolean h = r.hold; UUID f = r.follow;
                    r.followLostAt = 0; r.body.remove("ComeLostAt");
                    send(l, r.id, who.position()); r.hold = h; r.follow = f;
                } else {
                    r.aimedAt = now;
                    long t = System.currentTimeMillis();
                    if (r.followLostAt == 0) { r.followLostAt = t; setDirty(); }
                    else if (t - r.followLostAt > HollowbellEntity.comeGiveUpMs) { r.giveUpWaiting(); setDirty(); }
                }
            }
            r.settle(now);
            Vec3 s = r.spot(now);
            double back = backRange(server, r.scale);
            for (ServerPlayer p : l.players()) {
                if (p.isSpectator()) continue;
                if (Mth.square(p.getX() - s.x) + Mth.square(p.getZ() - s.z) < back * back) { bringBackWhenLoaded(l, r); break; }
            }
        }
    }

    /** the setting was turned off: every one out there comes back where the sum says he is */
    private void bringBackAll(MinecraftServer server) {
        for (Rec r : new ArrayList<>(recs.values())) {
            ServerLevel l = level(server, r.dim);
            if (l != null) bringBackWhenLoaded(l, r);
        }
    }

    /**
     * He comes back only once the land where he is has been loaded: it's asked for, made by the world on its own
     * threads, and he's put back on a later sweep. (Loading it there and then made the server wait until it was made.)
     * Null while waiting.
     */
    public @Nullable HollowbellEntity bringBackWhenLoaded(ServerLevel l, Rec r) {
        Vec3 s = r.spot(l.getGameTime());
        int bx = Mth.floor(s.x), bz = Mth.floor(s.z);
        if (!NoWait.loaded(l, bx, bz)) { NoWait.ask(l, bx, bz, 1); return null; }
        return bringBack(l, r);
    }

    private static @Nullable ServerLevel level(MinecraftServer server, String dim) {
        ResourceLocation key = ResourceLocation.tryParse(dim);
        if (key == null) return server.overworld();
        ServerLevel l = server.getLevel(ResourceKey.create(Registries.DIMENSION, key));
        return l != null ? l : server.overworld();
    }

    /** somebody has come to where the sum says he is: he's put back there, exactly as he was */
    public @Nullable HollowbellEntity bringBack(ServerLevel l, Rec r) {
        recs.remove(r.id);
        setDirty();
        HollowbellEntity h = ModEntities.HOLLOWBELL.create(l);
        if (h == null) return null;
        Vec3 s = r.spot(l.getGameTime());
        int bx = Mth.floor(s.x), bz = Mth.floor(s.z);
        // (never loads the land there and then: that made the server wait until it was made. Unloaded, he goes in
        // at the generator's guess of the ground and his land is asked for; he sets himself right once it's there)
        NoWait.hold(l, bx, bz, 1200);
        if (r.followLostAt != 0) r.body.putLong("ComeLostAt", r.followLostAt);
        h.load(r.body);
        if (l.getEntity(h.getUUID()) != null) {                             // never two of the same
            UUID was = h.getUUID();
            h.setUUID(UUID.randomUUID());
            WorldOne.get(l.getServer()).renamed(was, h.getUUID());
        }
        int ground = NoWait.heightOrGuess(l, Heightmap.Types.MOTION_BLOCKING, bx, bz);
        double y = Mth.clamp(ground + r.lift, l.getMinBuildHeight() + 1, l.getMaxBuildHeight() - 1);
        h.moveTo(s.x, y, s.z, h.getYRot(), 0f);
        h.backFromAway(r.going ? new Vec3(r.toX, ground, r.toZ) : null, r.stay);
        l.addFreshEntity(h);
        HollowbellMod.LOG.info("A Hollowbell is back in the world at {}, {}, {}", bx, Mth.floor(y), bz);
        return h;
    }

    // ------------------------------------------------------------------ saving

    public static Away load(CompoundTag tag, HolderLookup.Provider p) {
        Away a = new Away();
        ListTag l = tag.getList("Away", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            Rec r = Rec.load(l.getCompound(i));
            a.recs.put(r.id, r);
        }
        ListTag pk = tag.getList("Parked", Tag.TAG_COMPOUND);
        for (int i = 0; i < pk.size(); i++) {
            CompoundTag c = pk.getCompound(i);
            a.parked.put(c.getUUID("Id"), new Parked(c.getString("Dim"), c.getDouble("X"), c.getDouble("Z"), c.getDouble("Speed")));
        }
        // orders on their way to one in land nobody has loaded: carried on after a restart
        FarOrders.loadFetches(tag.getList("Fetches", Tag.TAG_COMPOUND));
        return a;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) {
        ListTag l = new ListTag();
        for (Rec r : recs.values()) l.add(r.save());
        tag.put("Away", l);
        ListTag pk = new ListTag();
        for (var e : parked.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("Id", e.getKey()); c.putString("Dim", e.getValue().dim());
            c.putDouble("X", e.getValue().x()); c.putDouble("Z", e.getValue().z()); c.putDouble("Speed", e.getValue().speed());
            pk.add(c);
        }
        tag.put("Parked", pk);
        tag.put("Fetches", FarOrders.saveFetches());
        return tag;
    }
}
