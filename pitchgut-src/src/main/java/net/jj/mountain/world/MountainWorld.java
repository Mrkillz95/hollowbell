package net.jj.mountain.world;

import net.jj.mountain.ModEntities;
import net.jj.mountain.MountainConfig;
import net.jj.mountain.MountainMod;
import net.jj.mountain.entity.MountainEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.Nullable;

/**
 * There is one of him in the world, always. This keeps track of where: while he is loaded he reports in, and when
 * he is not, the last place he was seen is what the compass points at. When he is killed the world picks a new
 * spot within ten thousand blocks of where he fell and another one gets up there a while later.
 */
public class MountainWorld extends SavedData {
    private static final String NAME = "mountain_breathes_world";

    /** where he is, or where the next one will come up */
    private int x, z;
    private boolean placed;          // a spot has been chosen at all
    private boolean alive;           // one of them is out there now
    private long dueAt = -1;         // game time the next one gets up, while none is alive
    private boolean bookMade;        // the one book that will ever be written has been
    /** which one it is. Every other copy in the world, however it got there, is not it. */
    private @Nullable java.util.UUID bookId;
    /** every player keeps their own list of who he is to leave alone; only the book holder's list counts */
    private final java.util.Map<java.util.UUID, java.util.Set<java.util.UUID>> friends = new java.util.HashMap<>();
    /** who has the book right now; kept through their logging off, and saved with the world */
    private @Nullable java.util.UUID holder;
    /** and the kinds of creature each player has told him to leave alone, by their registry name */
    private final java.util.Map<java.util.UUID, java.util.Set<String>> kinds = new java.util.HashMap<>();
    /** names for the book's own page, kept as they were last seen */
    private final java.util.Map<java.util.UUID, String> names = new java.util.HashMap<>();
    /** anyone knocked out of him: when they may go back in */
    private final java.util.Map<java.util.UUID, Long> shutOut = new java.util.HashMap<>();
    /** when each player last got an answer out of his heart, so breaking it and setting it down again is no use */
    private final java.util.Map<java.util.UUID, Long> heartUsed = new java.util.HashMap<>();
    /** whoever asked one of them for the last thing he does. Every Mountain there will ever be knows them. */
    private final java.util.Set<java.util.UUID> marked = new java.util.HashSet<>();

    // ------------------------------------------------------------------ the heart holding him off
    /**
     * His own heart, beating in somebody else's hands, is the one thing he will not walk towards. While it is
     * going he keeps right out of a circle around it. There is one of these going at a time — the heart came out
     * of one Mountain and it only knows how to hold off one place — and it costs a long rest afterwards, so it is
     * somewhere to run to rather than somewhere to live.
     */
    private int wardX, wardZ;
    private String wardDim = "minecraft:overworld";
    private long wardUntil = -1, wardRestUntil = -1;

    public boolean warding(ServerLevel l) { return wardUntil > 0 && l.getGameTime() < wardUntil; }
    public long wardLeft(ServerLevel l) { return Math.max(0, wardUntil - l.getGameTime()); }
    public long wardRestLeft(ServerLevel l) { return Math.max(0, wardRestUntil - l.getGameTime()); }
    public @Nullable BlockPos wardSpot() { return wardUntil > 0 ? new BlockPos(wardX, 0, wardZ) : null; }
    public String wardDim() { return wardDim; }
    public static double wardRange() { return Math.max(48, MountainConfig.V.wardBlocks); }

    /** the heart is knocked on and wakes: he will not come near this place until it is spent */
    public void startWard(ServerLevel l, BlockPos at, int ticks, int restTicks) {
        wardX = at.getX(); wardZ = at.getZ();
        wardDim = l.dimension().location().toString();
        wardUntil = l.getGameTime() + ticks;
        wardRestUntil = wardUntil + restTicks;
        setDirty();
    }

    public void endWard() { wardUntil = -1; setDirty(); }

    /** /mountain ward off, and a clean slate between tests: the rest is let off as well */
    public void forgetWard() { wardUntil = -1; wardRestUntil = -1; setDirty(); }

    /** is this spot inside the circle he will not enter? */
    public boolean warded(net.minecraft.world.level.Level l, double x, double z) {
        if (!(l instanceof ServerLevel sl) || !warding(sl)) return false;
        if (!sl.dimension().location().toString().equals(wardDim)) return false;
        double dx = x - (wardX + 0.5), dz = z - (wardZ + 0.5);
        double r = wardRange();
        return dx * dx + dz * dz < r * r;
    }

    // ------------------------------------------------------------------ still walking while nobody watches
    /**
     * Sent somewhere and then left behind by everybody, he would simply stop where the world stopped running
     * him. Instead the journey is written down here: where he set off from, where he is going, when he left and
     * how fast he covers ground. From then on the world can say where he ought to be at any moment, the finder
     * points at that spot, and the moment anybody gets near enough to see it he is brought there and carries on.
     */
    private boolean tripOn, tripUnder;
    /** everything he was, while he is out of the world: health, broken legs, popped eyes, his orders, the lot */
    private @Nullable CompoundTag away;
    private String awayDim = "minecraft:overworld";
    /** how fast he covers ground, above and below, kept so the book can still turn him around out there */
    private double awaySpeed = 0.05, awayUnderSpeed = 3.0;
    private double tripFromX, tripFromZ, tripToX, tripToZ, tripAtX, tripAtZ, tripSpeed;
    private long tripStart;
    private @Nullable java.util.UUID tripWho;

    public void startTrip(ServerLevel l, MountainEntity m, net.minecraft.world.phys.Vec3 to, double speed, boolean under) {
        tripOn = true;
        tripWho = m.getUUID();
        tripFromX = m.getX(); tripFromZ = m.getZ();
        tripAtX = m.getX(); tripAtZ = m.getZ();
        tripToX = to.x; tripToZ = to.z;
        tripStart = l.getGameTime();
        tripSpeed = Math.max(0.01, speed);
        tripUnder = under;
        setDirty();
        MountainMod.LOG.info("The Mountain is still on his way to {}, {} ({} blocks off, {} blocks a tick)",
                Mth.floor(tripToX), Mth.floor(tripToZ), Mth.floor(Math.hypot(tripToX - tripFromX, tripToZ - tripFromZ)), tripSpeed);
    }

    /** he is not in the world at all right now: he is a sum */
    public boolean isAway() { return away != null; }

    /**
     * How far the nearest player has to be before he steps out of the world. Left at nothing, it is the edge of
     * what the game itself keeps running — past that the game would stop ticking him anyway, so that is exactly
     * where he is better off being a sum. Set a number and that number is used instead.
     */
    public static double awayRange(@Nullable MinecraftServer server) {
        int set = MountainConfig.V.awayBlocks;
        if (set > 0) return Math.max(96, set);
        int sim = server == null ? 10 : Math.max(2, server.getPlayerList().getSimulationDistance());
        return Math.max(96, sim * 16 + 64);
    }

    /** and how close somebody has to come to where the sum says he is before he is put back */
    public static double backRange(@Nullable MinecraftServer server) { return Math.max(64, awayRange(server) - 48); }

    /**
     * Take him out of the world. Everything about him goes into the world's own notes — health, broken legs,
     * burst eyes, what he was told to do — and the entity is thrown away. From here the world works out where
     * he ought to be from how long he has been walking, and puts him back the moment anybody gets near that
     * spot, whole and still carrying out the order he was given.
     */
    public void takeAway(ServerLevel l, MountainEntity m, CompoundTag full, @Nullable net.minecraft.world.phys.Vec3 dest,
                         double speed, boolean under) {
        away = full;
        awayDim = l.dimension().location().toString();
        awaySpeed = Math.max(0.005, m.travelSpeed(false));
        awayUnderSpeed = Math.max(0.01, m.travelSpeed(true));
        parked(l, m);
        if (m.isWorldOne()) { x = Mth.floor(m.getX()); z = Mth.floor(m.getZ()); placed = true; alive = true; dueAt = -1; }
        if (dest != null && Math.hypot(dest.x - m.getX(), dest.z - m.getZ()) > 8) startTrip(l, m, dest, speed, under);
        else { endTrip(); MountainMod.LOG.info("The Mountain has stepped out of the world at {}, {}", Mth.floor(m.getX()), Mth.floor(m.getZ())); }
        setDirty();
    }

    /**
     * The book reaching him while he is nothing but a sum. He turns where he stands — which is wherever the
     * sum says he is this second — and sets off for the new spot, and the order goes into what is written down
     * so he is still carrying it out when he is put back in the world.
     */
    public boolean sendAway(ServerLevel l, net.minecraft.world.phys.Vec3 to, boolean under) {
        if (away == null) return false;
        BlockPos at = where(l);
        if (at == null) return false;
        tripOn = true;
        tripWho = away.hasUUID("UUID") ? away.getUUID("UUID") : tripWho;
        tripFromX = at.getX(); tripFromZ = at.getZ();
        tripAtX = at.getX(); tripAtZ = at.getZ();
        tripToX = to.x; tripToZ = to.z;
        tripStart = l.getGameTime();
        tripSpeed = Math.max(0.01, under ? awayUnderSpeed : awaySpeed);
        tripUnder = under;
        x = at.getX(); z = at.getZ(); placed = true;
        // and what he was told, written into him, so being put back changes nothing about it
        away.putDouble("GoalX", to.x); away.putDouble("GoalY", to.y); away.putDouble("GoalZ", to.z);
        away.putBoolean("Stay", false);
        away.putBoolean("Asleep", false);
        away.putBoolean("SleepMode", false);
        if (under) {
            away.putInt("DigState", 1); away.putInt("DigT", 0); away.putFloat("DigAmt", 0f);
            away.putDouble("DigToX", to.x); away.putDouble("DigToY", to.y); away.putDouble("DigToZ", to.z);
        } else {
            away.remove("DigState"); away.remove("DigT"); away.remove("DigAmt");
            away.remove("DigToX"); away.remove("DigToY"); away.remove("DigToZ");
        }
        setDirty();
        return true;
    }

    /** ticks before he will be made to go under again, as it stands in what is written down */
    public long awayUnderLeft(ServerLevel l) {
        if (away == null) return 0;
        long gap = MountainEntity.forcedUnderGap();
        if (gap <= 0) return 0;
        long at = away.contains("ForcedUnder") ? away.getLong("ForcedUnder") : Long.MIN_VALUE / 4;
        long since = l.getGameTime() - at;
        return since >= gap ? 0 : gap - since;
    }

    public void awayWentUnder(ServerLevel l) { if (away != null) { away.putLong("ForcedUnder", l.getGameTime()); setDirty(); } }

    /** stop where you are: the sum stops running and he is left standing out there */
    public boolean haltAway(ServerLevel l) {
        if (away == null) return false;
        BlockPos at = where(l);
        if (at != null) { x = at.getX(); z = at.getZ(); placed = true; parkedX = at.getX(); parkedZ = at.getZ(); parkedSet = true; }
        endTrip();
        away.remove("GoalX"); away.remove("GoalY"); away.remove("GoalZ");
        away.remove("DigState"); away.remove("DigT"); away.remove("DigAmt");
        away.remove("DigToX"); away.remove("DigToY"); away.remove("DigToZ");
        away.putBoolean("Stay", true);
        setDirty();
        return true;
    }

    /** the console taking every Mountain away should take the written-down one too */
    public void forgetAway() { if (away != null) { away = null; setDirty(); } }

    public boolean tripping() { return tripOn; }
    public boolean tripUnder() { return tripUnder; }
    public @Nullable java.util.UUID tripWho() { return tripWho; }
    public net.minecraft.world.phys.Vec3 tripEnd() { return new net.minecraft.world.phys.Vec3(tripToX, 0, tripToZ); }
    public void endTrip() { if (tripOn) { tripOn = false; tripWho = null; setDirty(); } }

    /** where he ought to be by now: how long he has been walking, times how fast he walks */
    public net.minecraft.world.phys.Vec3 tripSpot(ServerLevel l) {
        double dx = tripToX - tripFromX, dz = tripToZ - tripFromZ;
        double total = Math.sqrt(dx * dx + dz * dz);
        if (total < 1) return new net.minecraft.world.phys.Vec3(tripToX, 0, tripToZ);
        double gone = Math.max(0, l.getGameTime() - tripStart) * tripSpeed;
        double f = Math.min(1.0, gone / total);
        return new net.minecraft.world.phys.Vec3(tripFromX + dx * f, 0, tripFromZ + dz * f);
    }

    /** whole minutes of walking he has left, for the book to tell you */
    public int tripLeftMinutes(ServerLevel l) {
        double dx = tripToX - tripFromX, dz = tripToZ - tripFromZ;
        double total = Math.sqrt(dx * dx + dz * dz);
        double gone = Math.max(0, l.getGameTime() - tripStart) * tripSpeed;
        double left = Math.max(0, total - gone);
        return (int) Math.ceil(left / tripSpeed / 1200.0);
    }

    public boolean tripDone(ServerLevel l) {
        double dx = tripToX - tripFromX, dz = tripToZ - tripFromZ;
        double total = Math.sqrt(dx * dx + dz * dz);
        return total < 1 || Math.max(0, l.getGameTime() - tripStart) * tripSpeed >= total;
    }

    private int tripTick;

    private void tickTrip(ServerLevel l) {
        if (--tripTick > 0) return;
        tripTick = 20;
        if (tripOn) {
            var at = tripSpot(l);
            x = Mth.floor(at.x); z = Mth.floor(at.z); placed = true; setDirty();   // the finder walks along with him
        }
        if (away == null && !tripOn) return;

        BlockPos spot = where(l);
        if (spot == null) return;
        ServerLevel home = l;
        var key = net.minecraft.resources.ResourceLocation.tryParse(awayDim);
        if (key != null) {
            var other = l.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, key));
            if (other != null) home = other;
        }
        double back = backRange(l.getServer());
        boolean near = false;
        for (var p : home.players()) {
            double px = p.getX() - spot.getX(), pz = p.getZ() - spot.getZ();
            if (px * px + pz * pz < back * back) { near = true; break; }
        }
        if (!near) return;
        if (away != null) { bringBack(home, spot); return; }
        // the older way in: his own body is still saved in the piece of world he was put away with, so that
        // piece is held open for a moment and the first thing he does when he wakes is move to where he ought to be
        if (tripOn) MountainEntity.holdChunkAt(l, new BlockPos(Mth.floor(tripAtX), 64, Mth.floor(tripAtZ)), 990001);
    }

    /** somebody has come to where the sum says he is: he is put back there, exactly as he was */
    private void bringBack(ServerLevel l, BlockPos spot) {
        CompoundTag full = away;
        away = null;
        setDirty();
        if (full == null) return;
        MountainEntity e = ModEntities.MOUNTAIN.create(l);
        if (e == null) return;
        MountainEntity.holdChunkAt(l, spot, 990002);
        l.getChunk(spot.getX() >> 4, spot.getZ() >> 4);
        e.load(full);
        if (l.getEntity(e.getUUID()) != null) e.setUUID(java.util.UUID.randomUUID());   // never two of the same
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot.getX(), spot.getZ());
        e.moveTo(spot.getX() + 0.5, Math.max(y, l.getMinBuildHeight() + 1), spot.getZ() + 0.5, e.getYRot(), 0f);
        e.setYBodyRot(e.getYRot()); e.setYHeadRot(e.getYRot());
        e.backFromAway();
        l.addFreshEntity(e);
        endTrip();
        MountainMod.LOG.info("The Mountain is back in the world at {}, {}, {}", spot.getX(), y, spot.getZ());
    }

    public static MountainWorld get(ServerLevel level) {
        DimensionDataStorage store = level.getDataStorage();
        return store.computeIfAbsent(new SavedData.Factory<>(MountainWorld::new, MountainWorld::load, null), NAME);
    }

    private MountainWorld() {}

    private static MountainWorld load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider p) {
        MountainWorld w = new MountainWorld();
        w.x = tag.getInt("X"); w.z = tag.getInt("Z");
        w.placed = tag.getBoolean("Placed");
        w.alive = tag.getBoolean("Alive");
        w.dueAt = tag.contains("DueAt") ? tag.getLong("DueAt") : -1;
        w.bookMade = tag.getBoolean("BookMade");
        if (tag.contains("BookId")) w.bookId = net.minecraft.nbt.NbtUtils.loadUUID(tag.get("BookId"));
        if (tag.contains("Holder")) w.holder = net.minecraft.nbt.NbtUtils.loadUUID(tag.get("Holder"));
        if (tag.contains("ParkedX")) { w.parkedX = tag.getInt("ParkedX"); w.parkedZ = tag.getInt("ParkedZ"); w.parkedSet = true; w.parkedAt = tag.getLong("ParkedAt"); }
        if (tag.contains("Trip")) {
            CompoundTag t = tag.getCompound("Trip");
            w.tripOn = true;
            w.tripWho = net.minecraft.nbt.NbtUtils.loadUUID(t.get("Who"));
            w.tripFromX = t.getDouble("FromX"); w.tripFromZ = t.getDouble("FromZ");
            w.tripToX = t.getDouble("ToX"); w.tripToZ = t.getDouble("ToZ");
            w.tripAtX = t.getDouble("AtX"); w.tripAtZ = t.getDouble("AtZ");
            w.tripSpeed = Math.max(0.01, t.getDouble("Speed"));
            w.tripStart = t.getLong("Start");
            w.tripUnder = t.getBoolean("Under");
        }
        if (tag.contains("ShutOut", 9)) {
            net.minecraft.nbt.ListTag l = tag.getList("ShutOut", 10);
            for (int i = 0; i < l.size(); i++) {
                net.minecraft.nbt.CompoundTag c = l.getCompound(i);
                w.shutOut.put(net.minecraft.nbt.NbtUtils.loadUUID(c.get("Who")), c.getLong("Until"));
            }
        }
        if (tag.contains("WardUntil")) {
            w.wardX = tag.getInt("WardX"); w.wardZ = tag.getInt("WardZ");
            w.wardDim = tag.getString("WardDim");
            w.wardUntil = tag.getLong("WardUntil"); w.wardRestUntil = tag.getLong("WardRest");
            if (w.wardDim == null || w.wardDim.isEmpty()) w.wardDim = "minecraft:overworld";
        }
        if (tag.contains("Away", 10)) { w.away = tag.getCompound("Away"); w.awayDim = tag.getString("AwayDim"); }
        if (w.awayDim == null || w.awayDim.isEmpty()) w.awayDim = "minecraft:overworld";
        if (tag.contains("Marked", 9)) {
            net.minecraft.nbt.ListTag l = tag.getList("Marked", 11);
            for (int i = 0; i < l.size(); i++) w.marked.add(net.minecraft.nbt.NbtUtils.loadUUID(l.get(i)));
        }
        if (tag.contains("HeartUsed", 9)) {
            net.minecraft.nbt.ListTag l = tag.getList("HeartUsed", 10);
            for (int i = 0; i < l.size(); i++) {
                net.minecraft.nbt.CompoundTag c = l.getCompound(i);
                w.heartUsed.put(net.minecraft.nbt.NbtUtils.loadUUID(c.get("Who")), c.getLong("At"));
            }
        }
        if (tag.contains("SafeLists", 9)) {
            net.minecraft.nbt.ListTag l = tag.getList("SafeLists", 10);
            for (int i = 0; i < l.size(); i++) {
                net.minecraft.nbt.CompoundTag c = l.getCompound(i);
                java.util.UUID owner = net.minecraft.nbt.NbtUtils.loadUUID(c.get("Owner"));
                java.util.Set<java.util.UUID> set = new java.util.HashSet<>();
                net.minecraft.nbt.ListTag on = c.getList("On", 11);
                for (int k = 0; k < on.size(); k++) set.add(net.minecraft.nbt.NbtUtils.loadUUID(on.get(k)));
                if (!set.isEmpty()) w.friends.put(owner, set);
            }
        }
        if (tag.contains("SafeKinds", 9)) {
            net.minecraft.nbt.ListTag l = tag.getList("SafeKinds", 10);
            for (int i = 0; i < l.size(); i++) {
                net.minecraft.nbt.CompoundTag c = l.getCompound(i);
                java.util.Set<String> set = new java.util.HashSet<>();
                net.minecraft.nbt.ListTag on = c.getList("On", 8);
                for (int k = 0; k < on.size(); k++) set.add(on.getString(k));
                if (!set.isEmpty()) w.kinds.put(net.minecraft.nbt.NbtUtils.loadUUID(c.get("Owner")), set);
            }
        }
        if (tag.contains("Names", 9)) {
            net.minecraft.nbt.ListTag l = tag.getList("Names", 10);
            for (int i = 0; i < l.size(); i++) {
                net.minecraft.nbt.CompoundTag c = l.getCompound(i);
                w.names.put(net.minecraft.nbt.NbtUtils.loadUUID(c.get("Who")), c.getString("Name"));
            }
        }
        return w;
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider p) {
        tag.putInt("X", x); tag.putInt("Z", z);
        tag.putBoolean("Placed", placed);
        tag.putBoolean("Alive", alive);
        tag.putLong("DueAt", dueAt);
        tag.putBoolean("BookMade", bookMade);
        if (bookId != null) tag.put("BookId", net.minecraft.nbt.NbtUtils.createUUID(bookId));
        if (wardUntil > 0 || wardRestUntil > 0) {
            tag.putInt("WardX", wardX); tag.putInt("WardZ", wardZ); tag.putString("WardDim", wardDim);
            tag.putLong("WardUntil", wardUntil); tag.putLong("WardRest", wardRestUntil);
        }
        if (holder != null) tag.put("Holder", net.minecraft.nbt.NbtUtils.createUUID(holder));
        if (parkedSet) { tag.putInt("ParkedX", parkedX); tag.putInt("ParkedZ", parkedZ); tag.putLong("ParkedAt", parkedAt); }
        if (tripOn && tripWho != null) {
            CompoundTag t = new CompoundTag();
            t.put("Who", net.minecraft.nbt.NbtUtils.createUUID(tripWho));
            t.putDouble("FromX", tripFromX); t.putDouble("FromZ", tripFromZ);
            t.putDouble("ToX", tripToX); t.putDouble("ToZ", tripToZ);
            t.putDouble("AtX", tripAtX); t.putDouble("AtZ", tripAtZ);
            t.putDouble("Speed", tripSpeed);
            t.putLong("Start", tripStart);
            t.putBoolean("Under", tripUnder);
            tag.put("Trip", t);
        }
        if (!shutOut.isEmpty()) {
            net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
            for (var e : shutOut.entrySet()) {
                net.minecraft.nbt.CompoundTag c = new net.minecraft.nbt.CompoundTag();
                c.put("Who", net.minecraft.nbt.NbtUtils.createUUID(e.getKey()));
                c.putLong("Until", e.getValue());
                l.add(c);
            }
            tag.put("ShutOut", l);
        }
        if (away != null) { tag.put("Away", away); tag.putString("AwayDim", awayDim); }
        if (!marked.isEmpty()) {
            net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
            for (java.util.UUID u : marked) l.add(net.minecraft.nbt.NbtUtils.createUUID(u));
            tag.put("Marked", l);
        }
        if (!heartUsed.isEmpty()) {
            net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
            for (var e : heartUsed.entrySet()) {
                net.minecraft.nbt.CompoundTag c = new net.minecraft.nbt.CompoundTag();
                c.put("Who", net.minecraft.nbt.NbtUtils.createUUID(e.getKey()));
                c.putLong("At", e.getValue());
                l.add(c);
            }
            tag.put("HeartUsed", l);
        }
        if (!friends.isEmpty()) {
            net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
            for (var e : friends.entrySet()) {
                if (e.getValue().isEmpty()) continue;
                net.minecraft.nbt.CompoundTag c = new net.minecraft.nbt.CompoundTag();
                c.put("Owner", net.minecraft.nbt.NbtUtils.createUUID(e.getKey()));
                net.minecraft.nbt.ListTag on = new net.minecraft.nbt.ListTag();
                for (java.util.UUID u : e.getValue()) on.add(net.minecraft.nbt.NbtUtils.createUUID(u));
                c.put("On", on);
                l.add(c);
            }
            tag.put("SafeLists", l);
        }
        if (!kinds.isEmpty()) {
            net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
            for (var e : kinds.entrySet()) {
                if (e.getValue().isEmpty()) continue;
                net.minecraft.nbt.CompoundTag c = new net.minecraft.nbt.CompoundTag();
                c.put("Owner", net.minecraft.nbt.NbtUtils.createUUID(e.getKey()));
                net.minecraft.nbt.ListTag on = new net.minecraft.nbt.ListTag();
                for (String k : e.getValue()) on.add(net.minecraft.nbt.StringTag.valueOf(k));
                c.put("On", on);
                l.add(c);
            }
            tag.put("SafeKinds", l);
        }
        if (!names.isEmpty()) {
            net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
            for (var e : names.entrySet()) {
                net.minecraft.nbt.CompoundTag c = new net.minecraft.nbt.CompoundTag();
                c.put("Who", net.minecraft.nbt.NbtUtils.createUUID(e.getKey()));
                c.putString("Name", e.getValue());
                l.add(c);
            }
            tag.put("Names", l);
        }
        return tag;
    }

    /** knocked out of him: how long before they can step back in, in ticks */
    /** ticks before this player can get another answer out of the heart */
    public long heartLeft(ServerLevel l, java.util.UUID who, int rest) {
        Long at = heartUsed.get(who);
        if (at == null) return 0;
        long left = at + rest - l.getGameTime();
        if (left <= 0) { heartUsed.remove(who); setDirty(); return 0; }
        return left;
    }

    public void heartAnswered(ServerLevel l, java.util.UUID who) {
        heartUsed.put(who, l.getGameTime());
        setDirty();
    }

    public long shutOutFor(ServerLevel l, java.util.UUID who) {
        Long until = shutOut.get(who);
        return until == null ? 0 : Math.max(0, until - l.getGameTime());
    }

    public void shutOut(ServerLevel l, java.util.UUID who, long ticks) {
        shutOut.put(who, l.getGameTime() + ticks);
        setDirty();
    }

    /**
     * Whether he leaves this one alone. Only the safe list of whoever is carrying the book counts: put the book
     * down and he can turn on you and on everybody you had listed, until you have it in your hands again.
     */
    public boolean friendly(java.util.UUID who) {
        if (marked(who)) return false;                 // nothing covers them, not even their own book
        if (holder == null) return false;
        if (holder.equals(who)) return true;
        java.util.Set<java.util.UUID> on = friends.get(holder);
        return on != null && on.contains(who);
    }

    /** whether he leaves this kind of creature alone, by the book holder's list */
    public boolean friendlyKind(net.minecraft.world.entity.EntityType<?> type) {
        if (holder == null) return false;
        java.util.Set<String> on = kinds.get(holder);
        return on != null && on.contains(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
    }

    /** adds or removes a kind on one player's own list; true if it is on it now */
    public boolean toggleKind(java.util.UUID owner, net.minecraft.world.entity.EntityType<?> type) {
        String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        java.util.Set<String> on = kinds.computeIfAbsent(owner, k -> new java.util.HashSet<>());
        boolean added = on.add(id);
        if (!added) on.remove(id);
        setDirty();
        return added;
    }

    public java.util.List<String> kindsOf(java.util.UUID owner) {
        java.util.Set<String> on = kinds.get(owner);
        if (on == null || on.isEmpty()) return java.util.List.of();
        java.util.List<String> out = new java.util.ArrayList<>(on);
        java.util.Collections.sort(out);
        return out;
    }

    public void dropKind(java.util.UUID owner, String id) {
        java.util.Set<String> on = kinds.get(owner);
        if (on != null && on.remove(id)) setDirty();
    }

    /** the everyday name of a kind, for the book's page */
    public static String kindName(String id) {
        var rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        var t = rl == null ? null : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
        return t == null ? id : t.getDescription().getString();
    }

    /** whoever is carrying the book right now, or nobody */
    public @Nullable java.util.UUID bookHolder() { return holder; }
    public boolean listInForce(java.util.UUID owner) { return owner.equals(holder); }

    /** one player's own list, oldest name first once sorted */
    public java.util.List<java.util.UUID> listOf(java.util.UUID owner) {
        java.util.Set<java.util.UUID> on = friends.get(owner);
        if (on == null || on.isEmpty()) return java.util.List.of();
        java.util.List<java.util.UUID> out = new java.util.ArrayList<>(on);
        out.sort(java.util.Comparator.comparing(u -> nameOf(u).toLowerCase(java.util.Locale.ROOT)));
        return out;
    }

    public String nameOf(java.util.UUID who) {
        String n = names.get(who);
        return n == null ? who.toString().substring(0, 8) : n;
    }

    public void rememberName(java.util.UUID who, String name) {
        if (name != null && !name.isEmpty() && !name.equals(names.get(who))) { names.put(who, name); setDirty(); }
    }

    /** adds or removes on one player's own list; gives back true if they are on it now */
    public boolean toggleFriend(java.util.UUID owner, java.util.UUID who) {
        java.util.Set<java.util.UUID> on = friends.computeIfAbsent(owner, k -> new java.util.HashSet<>());
        boolean added = on.add(who);
        if (!added) on.remove(who);
        setDirty();
        return added;
    }

    public void dropFriend(java.util.UUID owner, java.util.UUID who) {
        java.util.Set<java.util.UUID> on = friends.get(owner);
        if (on != null && on.remove(who)) setDirty();
    }

    /** there is one book in the world and no more: true if this one is allowed to exist */
    public @Nullable java.util.UUID bookId() { return bookId; }

    /** the one book gets a mark of its own, and the world remembers which mark is the real one */
    public java.util.UUID claimBookAs() {
        bookId = java.util.UUID.randomUUID();
        bookMade = true;
        setDirty();
        return bookId;
    }

    public boolean claimBook() {
        if (bookMade) return false;
        bookMade = true; setDirty();
        return true;
    }
    public boolean bookExists() { return bookMade; }
    public void forgetBook() { bookMade = false; bookId = null; setDirty(); }

    /**
     * Where he is or was last seen, for the compass and the book. While he is walking somewhere with nobody
     * near him this is worked out from his speed on the spot, so it is right to the tick rather than right to
     * the last time anything was written down.
     */
    public @Nullable BlockPos where() { return placed ? new BlockPos(x, 0, z) : parkedSpot(); }

    public @Nullable BlockPos where(ServerLevel l) {
        if (tripOn) { var at = tripSpot(l); return new BlockPos(Mth.floor(at.x), 0, Mth.floor(at.z)); }
        return where();
    }
    public boolean aliveNow() { return alive; }

    // ------------------------------------------------------------------ the ones they all know
    /**
     * A player who asked a dying Mountain to finish it. No book will stay in their hands, no list will ever
     * cover them, and every Mountain that gets up from here on goes for them the moment it sees them. There is
     * no taking it back short of the console.
     */
    public boolean marked(@Nullable java.util.UUID who) { return who != null && !marked.isEmpty() && marked.contains(who); }

    public void mark(java.util.UUID who) { if (who != null && marked.add(who)) setDirty(); }

    /** /mountain forgive <player> on the worst of it */
    public boolean unmark(java.util.UUID who) {
        if (who == null || !marked.remove(who)) return false;
        setDirty();
        return true;
    }

    public int markedCount() { return marked.size(); }
    /** ticks until the next one gets up, or -1 */
    public long dueIn(ServerLevel l) { return alive || dueAt < 0 ? -1 : Math.max(0, dueAt - l.getGameTime()); }
    /** whole days until the next one gets up */
    public int daysLeft(ServerLevel l) { long t = dueIn(l); return t < 0 ? -1 : (int) Math.ceil(t / 24000.0); }

    /** the last place any of them was put away when its piece of world stopped being kept open */
    private int parkedX, parkedZ; private boolean parkedSet; private long parkedAt = -1;

    public void parked(ServerLevel level, MountainEntity m) {
        parkedX = Mth.floor(m.getX()); parkedZ = Mth.floor(m.getZ());
        parkedSet = true; parkedAt = level.getGameTime();
        setDirty();
        MountainMod.LOG.info("The Mountain has been put away at {}, {}, {}", parkedX, Mth.floor(m.getY()), parkedZ);
    }

    public @Nullable BlockPos parkedSpot() { return parkedSet ? new BlockPos(parkedX, 0, parkedZ) : null; }

    /** a Mountain that isn't the one the world keeps, seen right where he is this second */
    public void noted(MountainEntity m) {
        int nx = Mth.floor(m.getX()), nz = Mth.floor(m.getZ());
        if (nx == parkedX && nz == parkedZ && parkedSet) return;
        parkedX = nx; parkedZ = nz; parkedSet = true;
        setDirty();
    }

    public void seen(MountainEntity m) {
        x = Mth.floor(m.getX()); z = Mth.floor(m.getZ());
        placed = true; alive = true; dueAt = -1;
        setDirty();
    }

    /** he has been killed: the next one comes up somewhere within a few thousand blocks of here */
    public void died(ServerLevel level, MountainEntity m) {
        alive = false;
        double a = level.random.nextDouble() * Math.PI * 2;
        double far = Math.max(600, MountainConfig.V.respawnBlocks);
        double d = far * 0.25 + level.random.nextDouble() * far * 0.75;
        x = Mth.floor(m.getX() + Math.cos(a) * d);
        z = Mth.floor(m.getZ() + Math.sin(a) * d);
        placed = true;
        dueAt = level.getGameTime() + Math.max(1200L, MountainConfig.V.worldRespawnDays * 24000L);
        setDirty();
        MountainMod.LOG.info("The Mountain has fallen; the next comes up at {}, {}", x, z);
    }

    // ------------------------------------------------------------------ keeping one out there, and only one
    /** any Mountain in any level besides this one, still standing */
    public static @Nullable MountainEntity anyOther(MinecraftServer server, @Nullable MountainEntity except) {
        if (server == null) return null;
        MountainEntity found = null;
        for (ServerLevel l : server.getAllLevels()) {
            for (MountainEntity o : l.getEntities(ModEntities.MOUNTAIN, x -> !x.isRemoved() && !x.isDeadOrDying() && !x.isAPiece())) {
                if (o == except) continue;
                if (found == null || o.isWorldOne()) found = o;   // the world's own one always wins
            }
        }
        return found;
    }

    /**
     * How many of him the world will hold. A spawn egg, a /summon or a second world one could all leave you with
     * more of a thing the whole mod is written around there being one of.
     *
     * The newest wins. Summoning one is somebody deciding they want him here, now, so what they decide stands and
     * the oldest one goes — out of the world, and not written down as a sum either, so he does not come walking
     * back. If the one being pushed out was the world's own, the one left standing takes that over, so the count
     * down to the next one still means something.
     */
    public static boolean keepToTheLimit(@Nullable MountainEntity joining, ServerLevel world) {
        return !IN_TESTS && limitNow(joining, world);         // the tests put several down on purpose
    }

    /** the rule itself, with nothing turned off: the tests call this one straight */
    public static boolean limitNow(@Nullable MountainEntity joining, ServerLevel world) {
        if (joining != null && (joining.isRemoved() || joining.isAPiece())) return false;   // a lump he tore off is his own
        int max = Math.max(0, MountainConfig.V.maxMountains);
        if (max <= 0) return false;                              // as many as you like
        MinecraftServer server = world.getServer();
        if (server == null) return false;

        java.util.List<MountainEntity> all = new java.util.ArrayList<>();
        for (ServerLevel l : server.getAllLevels())
            for (MountainEntity o : l.getEntities(ModEntities.MOUNTAIN, x -> !x.isRemoved() && !x.isDeadOrDying() && !x.isAPiece())) all.add(o);
        if (joining != null && !all.contains(joining)) all.add(joining);

        MountainWorld w = get(server.overworld());
        // he is out there while he is nothing but a calculation too, and a new one takes that place as well
        if (w.isAway() && all.size() >= max) { w.forgetAway(); w.endTrip(); }
        if (all.size() <= max) return false;

        all.sort((a, b) -> Long.compare(b.bornAt(world), a.bornAt(world)));   // newest first
        java.util.List<MountainEntity> go = new java.util.ArrayList<>(all.subList(max, all.size()));
        boolean lostTheWorldOne = false;
        for (MountainEntity o : go) lostTheWorldOne |= o.isWorldOne();
        for (MountainEntity o : go) {
            if (o.level() instanceof ServerLevel ol)
                for (net.minecraft.server.level.ServerPlayer p : ol.players())
                    if (p.distanceToSqr(o) < 400 * 400)
                        p.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                                "message.mountain_breathes.made_way"), false);
            MountainMod.LOG.info("A Mountain at {}, {} made way for a newer one", Mth.floor(o.getX()), Mth.floor(o.getZ()));
            o.takenPlaceOf();
        }
        if (lostTheWorldOne && !all.isEmpty()) {
            MountainEntity keep = all.get(0);
            keep.markWorldOne();
            w.seen(keep);
        }
        return joining != null && go.contains(joining);
    }

    /** the limit was turned down, or something slipped past: put the world back inside it */
    private static void prune(MinecraftServer server) {
        if (IN_TESTS || MountainConfig.V.maxMountains <= 0) return;
        limitNow(null, server.overworld());
    }

    private int cooldown;

    public void tick(ServerLevel level) {
        if (!MountainConfig.V.oneInTheWorld) return;
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        if (--cooldown > 0) return;
        cooldown = 100;
        if (level.players().isEmpty()) return;

        if (!placed) {                                    // a brand new world: he is somewhere out there already
            BlockPos spawn = level.getSharedSpawnPos();
            double a = level.random.nextDouble() * Math.PI * 2;
            double d = 3000 + level.random.nextDouble() * 12000;
            x = Mth.floor(spawn.getX() + Math.cos(a) * d);
            z = Mth.floor(spawn.getZ() + Math.sin(a) * d);
            findLand(level);
            placed = true; alive = false; dueAt = level.getGameTime();
            setDirty();
            MountainMod.LOG.info("The Mountain That Breathes walks at {}, {}", x, z);
        }
        if (alive) return;
        // somebody put one down themselves: that is the one, and the world stops counting down to another
        MountainEntity already = anyOther(level.getServer(), null);
        if (already != null) {
            already.markWorldOne();
            x = Mth.floor(already.getX()); z = Mth.floor(already.getZ());
            placed = true; alive = true; dueAt = -1; setDirty();
            return;
        }
        if (isAway()) { alive = true; dueAt = -1; setDirty(); return; }
        if (dueAt < 0 || level.getGameTime() < dueAt) return;
        put(level);
    }

    /**
     * How high the ground is out there without making the world there first: asking the generator instead of
     * loading the chunk, so choosing a spot a few thousand blocks away costs nothing.
     */
    private int groundGuess(ServerLevel level, int x, int z) {
        var src = level.getChunkSource();
        return src.getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, src.randomState());
    }

    /** somewhere with ground above the sea, without building the world to find out */
    private void findLand(ServerLevel level) {
        for (int tries = 0; tries < 24; tries++) {
            if (groundGuess(level, x, z) > level.getSeaLevel() + 1) return;
            double a = level.random.nextDouble() * Math.PI * 2;
            double d = 400 + level.random.nextDouble() * 1600;
            x += (int) (Math.cos(a) * d);
            z += (int) (Math.sin(a) * d);
        }
    }

    /** puts one down at the chosen spot; only now is the one chunk under him made */
    private void put(ServerLevel level) {
        MountainEntity e = ModEntities.MOUNTAIN.create(level);
        if (e == null) return;
        findLand(level);
        e.setMountainScale(Math.max(0.05f, MountainConfig.V.worldScale));
        e.setVariant(MountainEntity.CALM);
        e.markWorldOne();
        MountainEntity.holdChunkAt(level, new BlockPos(x, 64, z), e.getId());
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        e.moveTo(x + 0.5, Math.max(y, level.getMinBuildHeight() + 1), z + 0.5, level.random.nextFloat() * 360f, 0f);
        e.setYBodyRot(e.getYRot()); e.setYHeadRot(e.getYRot());
        e.goToSleep();                                   // he is lying out there until something wakes him
        level.addFreshEntityWithPassengers(e);
        alive = true; dueAt = -1;
        setDirty();
        MountainMod.LOG.info("The Mountain That Breathes has got up at {}, {}, {}", x, y, z);
    }

    private int holderTick;

    /** who has the book in their hands this second */
    private void findHolder(MinecraftServer server) {
        if (--holderTick > 0) return;
        holderTick = 20;
        refreshHolder(server);
    }

    /**
     * Work out who is carrying the book right now. If the one who had it has logged off, the book went with
     * them: they keep it, and their safe list goes on doing its job until somebody else picks it up.
     */
    public void refreshHolder(MinecraftServer server) {
        java.util.UUID was = holder;
        java.util.UUID found = null;
        boolean wasHereToo = false;
        for (var p : server.getPlayerList().getPlayers()) {
            rememberName(p.getUUID(), p.getGameProfile().getName());
            if (found == null && net.jj.mountain.item.MountainCodexItem.heldBy(p)) found = p.getUUID();
            if (was != null && p.getUUID().equals(was)) wasHereToo = true;
        }
        if (found != null) {
            if (!found.equals(was)) { holder = found; setDirty(); }
            return;
        }
        if (was == null) return;
        if (!wasHereToo) return;                      // logged off with it: it is still theirs
        holder = null; setDirty();
        for (var p : server.getPlayerList().getPlayers())
            if (p.getUUID().equals(was)) p.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.mountain_breathes.list_off"), true);
    }

    private static int sweepTick;
    /** the test world has nobody standing in it, so every Mountain in it would step straight out. The two tests
     *  that are about stepping out call it themselves. Read from the game, never written to the settings file. */
    private static final boolean IN_TESTS = System.getProperty("fabric-api.gametest") != null;

    /**
     * Once a second, every Mountain in every world is asked whether anybody is near him. This runs off the
     * server's own tick rather than his, on purpose: he holds his own piece of world open from inside his own
     * tick, so if he ever misses a beat he cannot open it again and cannot tick because it is shut — he simply
     * stands there loaded and still, forever. Asked from out here, a Mountain who has stopped running is still
     * found, and still becomes a sum.
     */
    private static void sweep(MinecraftServer server) {
        if (--sweepTick > 0) return;
        sweepTick = 20;
        if (!MountainConfig.V.offscreenTravel || IN_TESTS) return;
        for (ServerLevel l : server.getAllLevels()) {
            if (l.dimension().equals(net.jj.mountain.innards.Innards.KEY)) continue;
            java.util.List<MountainEntity> here = new java.util.ArrayList<>();
            for (MountainEntity m : l.getEntities(ModEntities.MOUNTAIN, m -> !m.isRemoved() && !m.isDeadOrDying())) here.add(m);
            for (MountainEntity m : here) {
                m.stepAsideIfAlone(20);
                // and while he is still in the world, his piece of it is held open from out here as well, so he
                // can never end up shut out of his own chunk with no way to ask for it back
                if (!m.isRemoved() && MountainConfig.V.chunkLoading) m.forceChunks();
            }
        }
    }

    /**
     * One book. Not one that can be written, one that can EXIST. Crafting was the only thing ever checked, so a
     * second copy from the creative menu or a /give walked straight past it. Now the real one carries a mark of
     * its own and the world remembers which mark that is: every other copy, however it got there, crumbles the
     * moment it turns up in anybody's hands.
     */
    public void oneBookOnly(MinecraftServer server) {
        boolean seenReal = false;
        for (var p : server.getPlayerList().getPlayers()) {
            boolean cursed = marked(p.getUUID());
            net.minecraft.world.Container[] holds = { p.getInventory(), p.getEnderChestInventory() };
            for (net.minecraft.world.Container c : holds) {
                for (int i = 0; i < c.getContainerSize(); i++) {
                    net.minecraft.world.item.ItemStack st = c.getItem(i);
                    if (!st.is(net.jj.mountain.ModItems.CODEX)) continue;

                    if (cursed) {                                  // it will not stay in these hands at all
                        c.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                        if (net.jj.mountain.item.MountainCodexItem.theRealOne(this, st) || bookId == null) forgetBook();
                        gone(p, true);
                        continue;
                    }
                    if (bookId == null) {                          // nothing is the book yet: this one becomes it
                        net.jj.mountain.item.MountainCodexItem.markAs(st, claimBookAs());
                        seenReal = true;
                        continue;
                    }
                    if (!seenReal && net.jj.mountain.item.MountainCodexItem.theRealOne(this, st)) {
                        seenReal = true;                           // the one and only, left where it is
                        continue;
                    }
                    c.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                    gone(p, false);
                }
            }
        }
    }

    /** a copy coming apart in somebody's hands */
    private void gone(net.minecraft.server.level.ServerPlayer p, boolean burnt) {
        if (p.level() instanceof ServerLevel sl) {
            sl.sendParticles(burnt ? net.minecraft.core.particles.ParticleTypes.FLAME
                            : net.minecraft.core.particles.ParticleTypes.ASH,
                    p.getX(), p.getY() + 1.2, p.getZ(), 50, 0.4, 0.6, 0.4, 0.04);
            sl.playSound(null, p.blockPosition(),
                    burnt ? net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH : net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.2f, burnt ? 0.5f : 0.7f);
        }
        p.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                burnt ? "message.mountain_breathes.codex_will_not_stay" : "message.mountain_breathes.codex_crumbles"), true);
    }

    public static void serverTick(MinecraftServer server) {
        ServerLevel over = server.overworld();
        MountainWorld w = get(over);
        // half a second is soon enough for a copy to come apart, and it keeps the message from stuttering
        if (!IN_TESTS && over.getGameTime() % 10 == 0) w.oneBookOnly(server);
        if (over.getGameTime() % 100 == 0) prune(server);
        w.findHolder(server);
        w.tickTrip(over);
        w.tick(over);
        sweep(server);
    }
}
