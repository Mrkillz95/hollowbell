package net.jj.hollowbell.world;

import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * There is one of him in the world, always. A brand-new world quietly picks a spot far away — open, flattish
 * land the world hasn't made yet — and claims the ground there as his own before any of it is made: the Bell
 * Hollows (see {@link BellGen}), made by the world's own generation. When he is killed the
 * world picks a new spot a long way from where he fell, claims new ground there, and another comes down out of
 * the sky a while later. This also keeps the woken crown's ward, and holds the world to its limit of him.
 */
public class WorldOne extends SavedData {
    private static final String NAME = "hollowbell_world_one";
    private static final TicketType<Integer> TICKET = TicketType.create("hollowbell_world", Integer::compare, 100);
    /** the test world puts several of him down on purpose; the spawner and the cap stand aside there */
    public static final boolean IN_TESTS = System.getProperty("fabric-api.gametest") != null;

    /** where he is, or where the next one will come down */
    private int x, z;
    private boolean placed;          // a spot has been chosen at all
    private boolean alive;           // one of them is out there now
    private long dueAt = -1;         // game time the next one comes down, while none is alive
    /** which one of him is the world's own (null until one is known) */
    private @Nullable java.util.UUID oneId;

    // ------------------------------------------------------------------ his ground (the Bell Hollows)
    /** where his ground lies, how far it reaches, and the seed that shapes it */
    private int homeX, homeZ, homeRadius;
    private long homeSeed;
    private boolean homeClaimed;
    /** 1: the first, smaller Hollows (1.4); 2: painted over loaded land (1.5); 3: made by the world's generation (1.6 on) */
    private int groundVersion = GROUND_VERSION;
    public static final int GROUND_VERSION = 3;

    public boolean homeClaimed() { return homeClaimed; }
    public int homeX() { return homeX; }
    public int homeZ() { return homeZ; }
    public int homeRadius() { return homeRadius; }
    public long homeSeed() { return homeSeed; }
    public int groundVersion() { return groundVersion; }

    /** how far a new ground reaches, from the settings */
    public static int configRadius() { return Mth.clamp(HollowbellConfig.V.homeRadius, 200, 2000); }

    /**
     * A ground from before 1.6 keeps its centre and seed and grows to the new size if it was smaller. The land the
     * world had already made stays exactly as it is (painted bits and all); only land not made yet comes out as his
     * ground from now on. /hollowbell ground new claims a whole fresh one. Returns whether anything changed.
     */
    public boolean upgradeGround() {
        if (!homeClaimed) return false;
        boolean changed = false;
        if (groundVersion < GROUND_VERSION) {
            groundVersion = GROUND_VERSION;
            changed = true;
            HollowbellMod.LOG.info("The Bell Hollows at {}, {} are from an older version: the land already made stays as it is", homeX, homeZ);
        }
        if (homeRadius < configRadius()) { homeRadius = configRadius(); changed = true; }
        if (changed) { setDirty(); BellGen.publish(this); }
        return changed;
    }

    /** for the tests: a ground claimed with a seed of their choosing, so it can be made twice the same */
    public void claimHome(ServerLevel level, int atX, int atZ, long seed) {
        claimHome(level, atX, atZ);
        homeSeed = seed;
        setDirty();
        BellGen.publish(this);
    }

    /** for the tests: a ground as an older version left it */
    public void claimOldHome(int atX, int atZ, long seed, int version) {
        homeX = atX; homeZ = atZ; homeRadius = 320; homeSeed = seed; homeClaimed = true;
        groundVersion = version;
        setDirty();
        BellGen.publish(this);
    }

    /** the ground round this spot becomes his: from now on the world makes the land there as his */
    public void claimHome(ServerLevel level, int atX, int atZ) {
        homeX = atX; homeZ = atZ;
        homeRadius = configRadius();
        homeSeed = level.random.nextLong();
        homeClaimed = true;
        groundVersion = GROUND_VERSION;
        setDirty();
        BellGen.publish(this);
        HollowbellMod.LOG.info("The Bell Hollows lie at {}, {}", atX, atZ);
    }

    /** for the tests: the ground let go again, so nothing more is made as his */
    public void dropHome() {
        homeClaimed = false;
        groundVersion = GROUND_VERSION;
        setDirty();
        BellGen.publish(this);
    }

    /** for the tests: a world that has picked nothing yet, no ground, no ward */
    public void clearForTests() {
        placed = false; alive = false; dueAt = -1; x = 0; z = 0; oneId = null;
        cooldown = 0;
        forgetWard();
        dropHome();
    }

    // ------------------------------------------------------------------ claiming ground the world hasn't made yet

    /** how many of the region files a circle round this spot would lie in exist already (0: none of it is made) */
    public static int madeRegions(ServerLevel level, int cx, int cz, int r) {
        java.nio.file.Path dir = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("region");
        int n = 0;
        for (int rx = Math.floorDiv(cx - r, 512); rx <= Math.floorDiv(cx + r, 512); rx++)
            for (int rz = Math.floorDiv(cz - r, 512); rz <= Math.floorDiv(cz + r, 512); rz++)
                if (java.nio.file.Files.exists(dir.resolve("r." + rx + "." + rz + ".mca"))) n++;
        return n;
    }

    /** is anybody near enough that the land there is (or is about to be) made? */
    private static boolean playersNear(ServerLevel level, int cx, int cz, int r) {
        double reach = r + level.getServer().getPlayerList().getViewDistance() * 16 + 64;
        for (var p : level.players()) if (Math.hypot(p.getX() - cx, p.getZ() - cz) < reach) return true;
        return false;
    }

    /**
     * Moves the chosen spot (x, z) until the whole of a new ground round it is land the world hasn't made yet: no
     * region file for it on disk and nobody near it. Up to twenty hops, further out from the world's spawn each
     * time; if all of them are made somewhere, the least-made spot is taken.
     */
    private void intoUnmadeLand(ServerLevel level) {
        int r = configRadius();
        BlockPos spawn = level.getSharedSpawnPos();
        int bestX = x, bestZ = z, best = Integer.MAX_VALUE;
        for (int tries = 0; tries < 20; tries++) {
            int made = madeRegions(level, x, z, r) + (playersNear(level, x, z, r) ? 100 : 0);
            if (made == 0) return;
            if (made < best) { best = made; bestX = x; bestZ = z; }
            double dx = x - spawn.getX(), dz = z - spawn.getZ(), len = Math.max(1, Math.hypot(dx, dz));
            double a = Math.atan2(dz, dx) + (level.random.nextDouble() - 0.5) * 0.8;
            double step = r * 1.5 + 256;
            x = Mth.floor(spawn.getX() + Math.cos(a) * (len + step));
            z = Mth.floor(spawn.getZ() + Math.sin(a) * (len + step));
            findSpot(level);
        }
        x = bestX; z = bestZ;
        HollowbellMod.LOG.info("No wholly unmade land found for the Bell Hollows; taking {}, {}", x, z);
    }

    /**
     * A fresh ground in land the world hasn't made yet, claimed right away so the world makes it as his as it goes.
     * The spot (x, z) is where the next one comes down: the middle of it.
     */
    private void claimFresh(ServerLevel level) {
        intoUnmadeLand(level);
        claimHome(level, x, z);
    }

    /**
     * /hollowbell ground new: a whole new ground in land not made yet, far enough from the old one never to meet
     * it. If he isn't out in the world right now, the next one comes down there. Returns the new middle.
     */
    public BlockPos newGround(ServerLevel level) {
        int keepX = x, keepZ = z;
        BlockPos from = homeClaimed ? new BlockPos(homeX, 0, homeZ) : level.getSharedSpawnPos();
        double a = level.random.nextDouble() * Math.PI * 2;
        double d = clearOfGround() + 200;
        x = Mth.floor(from.getX() + Math.cos(a) * d);
        z = Mth.floor(from.getZ() + Math.sin(a) * d);
        findSpot(level);
        keepOffOldGround();
        claimFresh(level);
        BlockPos at = new BlockPos(homeX, 0, homeZ);
        if (alive) { x = keepX; z = keepZ; }
        else { placed = true; if (dueAt < 0) dueAt = level.getGameTime(); }
        setDirty();
        return at;
    }

    /**
     * The overworld has just been made, before any of its land (ServerWorldEvents.LOAD). His ground is chosen now,
     * if the world keeps one of him and has none yet, so the world makes that land as his from the very start.
     */
    public static void worldLoaded(ServerLevel over) {
        BellGen.capture(over);
        WorldOne w = get(over.getServer());
        w.upgradeGround();
        if (!IN_TESTS && HollowbellConfig.V.oneInTheWorld && !w.homeClaimed) {
            if (!w.placed) w.pickFirstSpot(over);
            w.claimFresh(over);
            w.placed = true;
            if (!w.alive && w.dueAt < 0) w.dueAt = over.getGameTime();
            w.setDirty();
        }
        BellGen.publish(w);
    }

    /** a brand new world: he is somewhere out there already, a few thousand blocks from where people start */
    private void pickFirstSpot(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        double a = level.random.nextDouble() * Math.PI * 2;
        double d = 3000 + level.random.nextDouble() * 12000;
        x = Mth.floor(spawn.getX() + Math.cos(a) * d);
        z = Mth.floor(spawn.getZ() + Math.sin(a) * d);
        findSpot(level);
    }

    // ------------------------------------------------------------------ the crown holding him off
    /**
     * His own crown, set down and woken, is the one thing he will not drift towards. While it is going he keeps
     * right out of a circle round it, his moves find nothing in there, and the book cannot send him in. It costs
     * the crown a long rest afterwards.
     */
    private int wardX, wardY = Integer.MIN_VALUE, wardZ;
    private String wardDim = "minecraft:overworld";
    private long wardUntil = -1, wardRestUntil = -1;

    public boolean warding(ServerLevel l) { return wardUntil > 0 && l.getGameTime() < wardUntil; }
    public long wardLeft(ServerLevel l) { return Math.max(0, wardUntil - l.getGameTime()); }
    public long wardRestLeft(ServerLevel l) { return Math.max(0, wardRestUntil - l.getGameTime()); }
    public @Nullable BlockPos wardSpot() { return wardUntil > 0 ? new BlockPos(wardX, 0, wardZ) : null; }
    public static double wardRange() { return Math.max(48, HollowbellConfig.V.wardBlocks); }

    /** the crown is woken: he will not come near this place until it is spent */
    public void startWard(ServerLevel l, BlockPos at, int ticks, int restTicks) {
        wardX = at.getX(); wardY = at.getY(); wardZ = at.getZ();
        wardDim = l.dimension().location().toString();
        wardUntil = l.getGameTime() + ticks;
        wardRestUntil = wardUntil + restTicks;
        setDirty();
    }

    /** the crown the ward stands on was broken or picked up: the ward stops, and its rest starts now */
    public void crownTaken(ServerLevel l, BlockPos at) {
        if (!warding(l) || at.getX() != wardX || at.getZ() != wardZ || (wardY != Integer.MIN_VALUE && at.getY() != wardY)) return;
        if (!l.dimension().location().toString().equals(wardDim)) return;
        long now = l.getGameTime();
        wardRestUntil -= wardUntil - now;
        wardUntil = now;
        setDirty();
        for (net.minecraft.server.level.ServerPlayer p : l.players())
            if (p.distanceToSqr(at.getX(), at.getY(), at.getZ()) < 64 * 64)
                p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.hollowbell.ward_broken"), true);
    }

    /** /hollowbell ward off, and a clean slate between tests: the rest is let off as well */
    public void forgetWard() { wardUntil = -1; wardRestUntil = -1; setDirty(); }

    /** is this spot inside the circle he will not enter? */
    public boolean warded(net.minecraft.world.level.Level l, double x, double z) {
        if (wardUntil <= 0 || HollowbellConfig.V.wardBlocks <= 0 || !(l instanceof ServerLevel sl) || !warding(sl)) return false;
        if (!sl.dimension().location().toString().equals(wardDim)) return false;
        double dx = x - (wardX + 0.5), dz = z - (wardZ + 0.5);
        double r = wardRange();
        return dx * dx + dz * dz < r * r;
    }

    // ------------------------------------------------------------------ getting at it

    public static WorldOne get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(WorldOne::new, WorldOne::load, null), NAME);
    }

    private WorldOne() {}

    private static WorldOne load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider p) {
        WorldOne w = new WorldOne();
        w.x = tag.getInt("X"); w.z = tag.getInt("Z");
        w.placed = tag.getBoolean("Placed");
        w.alive = tag.getBoolean("Alive");
        w.dueAt = tag.contains("DueAt") ? tag.getLong("DueAt") : -1;
        w.oneId = tag.hasUUID("OneId") ? tag.getUUID("OneId") : null;
        w.homeClaimed = tag.getBoolean("HomeClaimed");
        w.homeX = tag.getInt("HomeX"); w.homeZ = tag.getInt("HomeZ");
        w.homeRadius = tag.contains("HomeRadius") ? tag.getInt("HomeRadius") : 320;
        w.homeSeed = tag.getLong("HomeSeed");
        w.groundVersion = tag.contains("GroundVersion") ? tag.getInt("GroundVersion") : 1;
        w.upgradeGround();
        if (tag.contains("WardUntil")) {
            w.wardX = tag.getInt("WardX"); w.wardZ = tag.getInt("WardZ");
            w.wardY = tag.contains("WardY") ? tag.getInt("WardY") : Integer.MIN_VALUE;
            w.wardDim = tag.getString("WardDim");
            w.wardUntil = tag.getLong("WardUntil"); w.wardRestUntil = tag.getLong("WardRest");
            if (w.wardDim == null || w.wardDim.isEmpty()) w.wardDim = "minecraft:overworld";
        }
        return w;
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider p) {
        tag.putInt("X", x); tag.putInt("Z", z);
        tag.putBoolean("Placed", placed);
        tag.putBoolean("Alive", alive);
        tag.putLong("DueAt", dueAt);
        if (oneId != null) tag.putUUID("OneId", oneId);
        tag.putBoolean("HomeClaimed", homeClaimed);
        tag.putInt("HomeX", homeX); tag.putInt("HomeZ", homeZ);
        tag.putInt("HomeRadius", homeRadius);
        tag.putLong("HomeSeed", homeSeed);
        tag.putInt("GroundVersion", groundVersion);
        if (wardUntil > 0 || wardRestUntil > 0) {
            tag.putInt("WardX", wardX); tag.putInt("WardY", wardY); tag.putInt("WardZ", wardZ); tag.putString("WardDim", wardDim);
            tag.putLong("WardUntil", wardUntil); tag.putLong("WardRest", wardRestUntil);
        }
        return tag;
    }

    // ------------------------------------------------------------------ what the finder and the command read

    /** where he is or where the next one comes down, or null while no spot is known */
    public @Nullable BlockPos where() { return placed ? new BlockPos(x, 0, z) : null; }
    public boolean aliveNow() { return alive; }
    /** ticks until the next one comes down, or -1 */
    public long dueIn(ServerLevel l) { return alive || dueAt < 0 ? -1 : Math.max(0, dueAt - l.getGameTime()); }
    /** whole days until the next one comes down */
    public int daysLeft(ServerLevel l) { long t = dueIn(l); return t < 0 ? -1 : (int) Math.ceil(t / 24000.0); }

    public @Nullable java.util.UUID oneId() { return oneId; }
    /** is this the world's own one? */
    public boolean isTheOne(java.util.UUID id) { return oneId != null && oneId.equals(id); }

    /**
     * The world's own one reports in: the finder points right at him. Only the overworld is written down (the
     * spot is an overworld spot), and never while he is dying (that would undo his death).
     */
    public void seen(HollowbellEntity h) {
        if (h.isDeadOrDying()) return;
        if (oneId != null && !oneId.equals(h.getUUID())) return;
        boolean changed = oneId == null || !alive || !placed || dueAt >= 0;
        oneId = h.getUUID();
        placed = true; alive = true; dueAt = -1;
        if (h.level().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            int nx = Mth.floor(h.getX()), nz = Mth.floor(h.getZ());
            changed |= nx != x || nz != z;
            x = nx; z = nz;
        }
        if (changed) setDirty();
    }

    /** this one is the world's own from now on */
    public void adopt(HollowbellEntity h) {
        h.markWorldOne();
        oneId = h.getUUID();
        seen(h);
        setDirty();
    }

    /** this one, out of the world as a sum, is the world's own from now on */
    public void adoptAway(Away.Rec r, Away away, long now) {
        r.body.putBoolean("WorldOne", true);
        away.setDirty();
        oneId = r.id;
        placed = true; alive = true; dueAt = -1;
        if ("minecraft:overworld".equals(r.dim)) {
            var at = r.spot(now);
            x = Mth.floor(at.x); z = Mth.floor(at.z);
        }
        setDirty();
    }

    /** a sum came back under a new name (its old one was taken): the world keeps track */
    public void renamed(java.util.UUID was, java.util.UUID now) {
        if (isTheOne(was)) { oneId = now; setDirty(); }
    }

    /** for the tests: the world told outright where things stand */
    public void noteSpot(int atX, int atZ, boolean isAlive, long due) {
        x = atX; z = atZ; placed = true; alive = isAlive; dueAt = due;
        setDirty();
    }

    /** he has been killed: the next one comes down a long way off, on fresh ground of his own */
    public void died(ServerLevel level, HollowbellEntity h) {
        alive = false;
        oneId = null;
        double far = Math.max(600, HollowbellConfig.V.respawnBlocks);
        double a = level.random.nextDouble() * Math.PI * 2;
        double d = Math.max(clearOfGround(), far * 0.25 + level.random.nextDouble() * far * 0.75);
        // measured from where he fell if that was the overworld, otherwise from where he was last seen in it
        boolean over = h.level().dimension() == net.minecraft.world.level.Level.OVERWORLD;
        double fx = over ? h.getX() : x, fz = over ? h.getZ() : z;
        x = Mth.floor(fx + Math.cos(a) * d);
        z = Mth.floor(fz + Math.sin(a) * d);
        findSpot(level);
        keepOffOldGround();
        // his new ground is claimed now, in land not made yet, so it is ready when he comes down
        if (!IN_TESTS) claimFresh(level);
        placed = true;
        dueAt = level.getGameTime() + Math.max(1200L, HollowbellConfig.V.worldRespawnDays * 24000L);
        setDirty();
        HollowbellMod.LOG.info("The Hollowbell has fallen; the next comes down near {}, {}", x, z);
    }

    /**
     * One of him was taken away without dying (/hollowbell remove, /giants remove). Only if it was the world's
     * own does the count to the next one start; a remove that couldn't reach him (asleep in a far chunk) changes
     * nothing.
     */
    public void removed(ServerLevel level, java.util.UUID id) {
        if (isTheOne(id)) gone(level);
    }

    private void gone(ServerLevel level) {
        if (!alive) return;
        alive = false;
        oneId = null;
        dueAt = level.getGameTime() + Math.max(1200L, HollowbellConfig.V.worldRespawnDays * 24000L);
        setDirty();
    }

    // ------------------------------------------------------------------ keeping one out there, and only one

    /** any Hollowbell in any level besides this one, still standing */
    public static @Nullable HollowbellEntity anyOther(@Nullable MinecraftServer server, @Nullable HollowbellEntity except) {
        if (server == null) return null;
        HollowbellEntity found = null;
        for (ServerLevel l : server.getAllLevels()) {
            for (HollowbellEntity o : l.getEntities(ModEntities.HOLLOWBELL, e -> !e.isRemoved() && !e.isDeadOrDying())) {
                if (o == except) continue;
                if (found == null || o.isWorldOne()) found = o;   // the world's own one always wins
            }
        }
        return found;
    }

    /** the newest one out of the world as a sum (one already marked as the world's own first), or null */
    private static @Nullable Away.Rec newestAway(MinecraftServer server) {
        Away.Rec best = null;
        for (Away.Rec r : Away.get(server).all()) {
            if (best == null) { best = r; continue; }
            boolean bw = best.body.getBoolean("WorldOne"), rw = r.body.getBoolean("WorldOne");
            if (rw != bw) { if (rw) best = r; continue; }
            if (born(r) > born(best)) best = r;
        }
        return best;
    }

    private static long born(Away.Rec r) { return r.body.contains("BornAt") ? r.body.getLong("BornAt") : Long.MIN_VALUE / 4; }

    /**
     * One of him has come into the world (ENTITY_LOAD). Freshly made ones meet the cap. A saved body still marked
     * as the world's own that isn't the one the world knows (an old copy) loses the mark and meets the cap too.
     * The spawner's own placement never pushes anybody out.
     */
    public static void joined(HollowbellEntity h, ServerLevel world) {
        if (IN_TESTS) return;
        joinedNow(h, world);
    }

    /** as joined(), for the tests, which put several of him down on purpose */
    public static void joinedNow(HollowbellEntity h, ServerLevel world) {
        MinecraftServer server = world.getServer();
        if (server == null || h.isRemoved() || h.isDeadOrDying()) return;   // one saved mid-death is already counted as dead
        WorldOne w = get(server);
        if (w.isTheOne(h.getUUID())) return;                     // the world's own, or the spawner putting him down
        if (h.isWorldOne()) {
            if (w.oneId == null) { w.adopt(h); return; }         // a world from before names were kept
            h.clearWorldOne();                                  // an old copy: he's just one of them now
            return;                                             // (loading from a save never pushes anybody out)
        }
        if (h.freshSpawn()) limitNow(h, world);
    }

    /** one standing thing at the cap: a loaded one, or one written down as a sum */
    private record Standing(@Nullable HollowbellEntity e, @Nullable Away.Rec r, long born, boolean worldOne) {}

    /**
     * How many of him the world will hold. The newest wins: summoning one is somebody deciding they want him
     * here, now, so the oldest goes - out of the world, no loot, and not written down as a sum either. The ones
     * out of the world count against the cap and lose by age like everyone else. If the one pushed out was the
     * world's own, the newest one standing takes that over, so the count down to the next one still means something.
     */
    public static boolean limitNow(@Nullable HollowbellEntity joining, ServerLevel world) {
        if (joining != null && joining.isRemoved()) return false;
        int max = Math.max(0, HollowbellConfig.V.maxInWorld);
        if (max <= 0) return false;                              // as many as you like
        MinecraftServer server = world.getServer();
        if (server == null) return false;

        List<HollowbellEntity> all = new ArrayList<>();
        for (ServerLevel l : server.getAllLevels())
            for (HollowbellEntity o : l.getEntities(ModEntities.HOLLOWBELL, e -> !e.isRemoved() && !e.isDeadOrDying())) all.add(o);
        if (joining != null && !all.contains(joining)) all.add(joining);
        Away away = Away.get(server);
        List<Away.Rec> out = away.all();
        if (all.size() + out.size() <= max) return false;        // at the cap is fine; only past it someone goes

        List<Standing> list = new ArrayList<>();
        for (HollowbellEntity e : all) list.add(new Standing(e, null, e.bornAtOr(world), e.isWorldOne()));
        for (Away.Rec r : out)
            list.add(new Standing(null, r, r.body.contains("BornAt") ? r.body.getLong("BornAt") : Long.MIN_VALUE / 4, r.body.getBoolean("WorldOne")));
        // newest first; on a tie the one just joining wins (somebody wanted him here, now)
        list.sort((p, q) -> {
            int c = Long.compare(q.born, p.born);
            if (c != 0) return c;
            return Boolean.compare(q.e == joining, p.e == joining);
        });
        List<Standing> go = new ArrayList<>(list.subList(max, list.size()));
        boolean lostTheWorldOne = false, joiningGoes = false;
        for (Standing s : go) {
            lostTheWorldOne |= s.worldOne;
            if (s.e != null) {
                if (s.e == joining) joiningGoes = true;
                s.e.takenPlaceOf();
            } else {
                // a stored body giving up its place: nobody is anywhere near it, so it goes quietly
                away.forget(s.r.id);
                HollowbellMod.LOG.info("A Hollowbell out of the world made way for a newer one");
            }
        }
        if (lostTheWorldOne && !list.isEmpty()) {
            Standing keep = list.get(0);
            WorldOne w = get(server);
            if (keep.e != null) w.adopt(keep.e);
            else w.adoptAway(keep.r, away, world.getGameTime());
        }
        return joiningGoes;
    }

    // ------------------------------------------------------------------ the spawner

    private int cooldown;

    public void tick(ServerLevel level) {
        if (!HollowbellConfig.V.oneInTheWorld) return;
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        if (--cooldown > 0) return;
        cooldown = 100;
        if (level.players().isEmpty()) return;
        MinecraftServer server = level.getServer();

        if (!placed) {                                    // natural spawning just turned on: he is somewhere out there already
            pickFirstSpot(level);
            if (!homeClaimed) claimFresh(level);
            placed = true; alive = false; dueAt = level.getGameTime();
            setDirty();
            HollowbellMod.LOG.info("A Hollowbell drifts near {}, {}", x, z);
        }
        // only a real death or a remove that reached him ends the world's own one; not finding him means nothing
        // (he may be asleep in a chunk nobody is near)
        if (alive) return;
        // somebody already has one out, in the world or out of it: that is the one, and nothing new comes down
        HollowbellEntity already = anyOther(server, null);
        if (already != null) { adopt(already); return; }
        Away away = Away.get(server);
        Away.Rec rec = newestAway(server);
        if (rec != null) { adoptAway(rec, away, level.getGameTime()); return; }
        if (dueAt < 0 || level.getGameTime() < dueAt) return;
        // and the spawner never pushes anybody out to make room
        int max = HollowbellConfig.V.maxInWorld;
        if (max > 0 && away.count() >= max) return;
        put(level);
    }

    /**
     * How high the ground is out there without making the world there first: asking the generator instead of
     * loading the chunk, so choosing a spot a few thousand blocks away costs nothing.
     */
    private int groundGuess(ServerLevel level, int gx, int gz) {
        var src = level.getChunkSource();
        return src.getGenerator().getBaseHeight(gx, gz, Heightmap.Types.WORLD_SURFACE_WG, level, src.randomState());
    }

    private void hop(ServerLevel level, double near, double far) {
        double a = level.random.nextDouble() * Math.PI * 2;
        double d = near + level.random.nextDouble() * far;
        x += (int) (Math.cos(a) * d);
        z += (int) (Math.sin(a) * d);
    }

    /** somewhere with ground above the sea, without building the world to find out */
    private void findLand(ServerLevel level) {
        for (int tries = 0; tries < 24; tries++) {
            if (groundGuess(level, x, z) > level.getSeaLevel() + 1) return;
            hop(level, 400, 1600);
        }
    }

    /** dry, and not woodland that would swallow him: no ocean, river, jungle, dark forest or taiga */
    private boolean openGround(ServerLevel level, int gx, int gz) {
        int gy = groundGuess(level, gx, gz);
        if (gy <= level.getSeaLevel() + 1) return false;
        var b = level.getUncachedNoiseBiome(gx >> 2, gy >> 2, gz >> 2);
        return !b.is(BiomeTags.IS_OCEAN) && !b.is(BiomeTags.IS_DEEP_OCEAN) && !b.is(BiomeTags.IS_RIVER)
                && !b.is(BiomeTags.IS_JUNGLE) && !b.is(BiomeTags.IS_TAIGA) && !b.is(Biomes.DARK_FOREST);
    }

    /** flattish: five height guesses 40 blocks apart differ by less than 14 */
    private boolean flatEnough(ServerLevel level, int gx, int gz) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        int[][] at = {{0, 0}, {40, 0}, {-40, 0}, {0, 40}, {0, -40}};
        for (int[] o : at) {
            int hgt = groundGuess(level, gx + o[0], gz + o[1]);
            lo = Math.min(lo, hgt); hi = Math.max(hi, hgt);
        }
        return hi - lo < 14;
    }

    /** land above the sea, then a few extra hops for open ground, a few more for flat; then whatever there is */
    private void findSpot(ServerLevel level) {
        findLand(level);
        for (int tries = 0; tries < 8 && !openGround(level, x, z); tries++) hop(level, 400, 1600);
        for (int tries = 0; tries < 6 && !flatEnough(level, x, z); tries++) hop(level, 160, 480);
        avoidWard();
    }

    /** how far a new ground's centre must be from the old one so the two never overlap */
    private double clearOfGround() { return (homeClaimed ? homeRadius : 0) + configRadius() + 64; }

    /** the next one comes down far enough off that his new ground never lies over the old one */
    private void keepOffOldGround() {
        if (!homeClaimed) return;
        double dx = x - (homeX + 0.5), dz = z - (homeZ + 0.5);
        double len = Math.sqrt(dx * dx + dz * dz), keep = clearOfGround();
        if (len >= keep) return;
        if (len < 1.0E-4) { dx = 1; dz = 0; len = 1; }
        x = Mth.floor(homeX + 0.5 + dx / len * keep);
        z = Mth.floor(homeZ + 0.5 + dz / len * keep);
    }

    /** never come down inside a circle a woken crown is holding: pushed out past its edge */
    private void avoidWard() {
        if (wardUntil <= 0 || !"minecraft:overworld".equals(wardDim)) return;
        double dx = x - (wardX + 0.5), dz = z - (wardZ + 0.5);
        double len = Math.sqrt(dx * dx + dz * dz);
        double keep = wardRange() + 300;
        if (len >= keep) return;
        if (len < 1.0E-4) { dx = 1; dz = 0; len = 1; }
        x = Mth.floor(wardX + 0.5 + dx / len * keep);
        z = Mth.floor(wardZ + 0.5 + dz / len * keep);
    }

    /**
     * Puts one down at the chosen spot, the middle of his ground; only now is the chunk under him made (as his
     * ground, the claim came first). He comes down out of the sky.
     */
    private void put(ServerLevel level) {
        HollowbellEntity e = ModEntities.HOLLOWBELL.create(level);
        if (e == null) return;
        avoidWard();
        e.setBellScale(Mth.clamp(HollowbellConfig.V.worldScale, HollowbellEntity.MIN_SCALE, HollowbellEntity.MAX_SCALE));
        e.setVariant(HollowbellEntity.CALM);
        e.markWorldOne();
        oneId = e.getUUID();                              // named first, so his arrival never meets the cap
        // his ground was claimed long before, so the land here is made as his as this chunk is made
        if (!homeClaimed) claimFresh(level);
        level.getChunkSource().addRegionTicket(TICKET, new ChunkPos(new BlockPos(x, 64, z)), 3, e.getId());
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        e.moveTo(x + 0.5, Math.max(y, level.getMinBuildHeight() + 1), z + 0.5, level.random.nextFloat() * 360f, 0f);
        e.setHome(e.position());
        level.addFreshEntityWithPassengers(e);
        alive = true; dueAt = -1;
        setDirty();
        HollowbellMod.LOG.info("The Hollowbell has come down at {}, {}, {}", x, y, z);
    }

    public static void serverTick(MinecraftServer server) {
        if (IN_TESTS) return;
        ServerLevel over = server.overworld();
        WorldOne w = get(server);
        w.tick(over);
    }
}
