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
 * land — and the ground there becomes his own: the Bell Hollows (see {@link HomeGround}). When he is killed the
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

    // ------------------------------------------------------------------ his ground (the Bell Hollows)
    /** where his ground lies, how far it reaches, the seed that shapes its edge, and the chunks already made his */
    private int homeX, homeZ, homeRadius;
    private long homeSeed;
    private boolean homeClaimed;
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet painted = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

    public boolean homeClaimed() { return homeClaimed; }
    public int homeX() { return homeX; }
    public int homeZ() { return homeZ; }
    public int homeRadius() { return homeRadius; }
    public long homeSeed() { return homeSeed; }
    public boolean paintedAlready(long chunkPos) { return painted.contains(chunkPos); }
    public void notePainted(long chunkPos) { if (painted.add(chunkPos)) setDirty(); }

    /** the ground round this spot becomes his. An old ground stays as it is: the land remembers him. */
    public void claimHome(ServerLevel level, int atX, int atZ) {
        homeX = atX; homeZ = atZ;
        homeRadius = 320;
        homeSeed = level.random.nextLong();
        homeClaimed = true;
        painted.clear();
        setDirty();
        HomeGround.claimed(level, this);
        HollowbellMod.LOG.info("The Bell Hollows lie at {}, {}", atX, atZ);
    }

    /** for the tests: the ground let go again, so nothing keeps painting near the arenas */
    public void dropHome() { homeClaimed = false; painted.clear(); setDirty(); }

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
        if (wardUntil <= 0 || !(l instanceof ServerLevel sl) || !warding(sl)) return false;
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
        w.homeClaimed = tag.getBoolean("HomeClaimed");
        w.homeX = tag.getInt("HomeX"); w.homeZ = tag.getInt("HomeZ");
        w.homeRadius = tag.contains("HomeRadius") ? tag.getInt("HomeRadius") : 320;
        w.homeSeed = tag.getLong("HomeSeed");
        for (long k : tag.getLongArray("Painted")) w.painted.add(k);
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
        tag.putBoolean("HomeClaimed", homeClaimed);
        tag.putInt("HomeX", homeX); tag.putInt("HomeZ", homeZ);
        tag.putInt("HomeRadius", homeRadius);
        tag.putLong("HomeSeed", homeSeed);
        tag.putLongArray("Painted", painted.toLongArray());
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

    /** a loaded one reports in: the finder points right at him */
    public void seen(HollowbellEntity h) {
        int nx = Mth.floor(h.getX()), nz = Mth.floor(h.getZ());
        boolean was = alive && placed && nx == x && nz == z;
        x = nx; z = nz;
        placed = true; alive = true; dueAt = -1;
        if (!was) setDirty();
    }

    /** for the tests: the world told outright where things stand */
    public void noteSpot(int atX, int atZ, boolean isAlive, long due) {
        x = atX; z = atZ; placed = true; alive = isAlive; dueAt = due;
        setDirty();
    }

    /** he has been killed: the next one comes down a long way off, on fresh ground of his own */
    public void died(ServerLevel level, HollowbellEntity h) {
        alive = false;
        double far = Math.max(600, HollowbellConfig.V.respawnBlocks);
        double a = level.random.nextDouble() * Math.PI * 2;
        double d = far * 0.25 + level.random.nextDouble() * far * 0.75;
        x = Mth.floor(h.getX() + Math.cos(a) * d);
        z = Mth.floor(h.getZ() + Math.sin(a) * d);
        findSpot(level);
        placed = true;
        dueAt = level.getGameTime() + Math.max(1200L, HollowbellConfig.V.worldRespawnDays * 24000L);
        setDirty();
        HollowbellMod.LOG.info("The Hollowbell has fallen; the next comes down near {}, {}", x, z);
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

    /** is the world's own one written down as a sum right now? */
    private static boolean awayWorldOne(MinecraftServer server) {
        for (Away.Rec r : Away.get(server).all()) if (r.body.getBoolean("WorldOne")) return true;
        return false;
    }

    /** the cap, skipped under the tests (they put several of him down on purpose) */
    public static boolean keepToTheLimit(@Nullable HollowbellEntity joining, ServerLevel world) {
        return !IN_TESTS && limitNow(joining, world);
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
        int max = Math.max(0, HollowbellConfig.V.maxHollowbells);
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
            if (keep.e != null) {
                keep.e.markWorldOne();
                w.seen(keep.e);
            } else {
                keep.r.body.putBoolean("WorldOne", true);
                away.setDirty();
                var at = keep.r.spot(world.getGameTime());
                w.noteSpot(Mth.floor(at.x), Mth.floor(at.z), true, -1);
            }
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

        if (!placed) {                                    // a brand new world: he is somewhere out there already
            BlockPos spawn = level.getSharedSpawnPos();
            double a = level.random.nextDouble() * Math.PI * 2;
            double d = 3000 + level.random.nextDouble() * 12000;
            x = Mth.floor(spawn.getX() + Math.cos(a) * d);
            z = Mth.floor(spawn.getZ() + Math.sin(a) * d);
            findSpot(level);
            placed = true; alive = false; dueAt = level.getGameTime();
            setDirty();
            HollowbellMod.LOG.info("A Hollowbell drifts near {}, {}", x, z);
        }
        if (alive) {
            // taken out from under us (/hollowbell remove, or the cap): the world quietly starts the count again
            if (anyOther(server, null) == null && Away.get(server).count() == 0) {
                alive = false;
                dueAt = level.getGameTime() + Math.max(1200L, HollowbellConfig.V.worldRespawnDays * 24000L);
                setDirty();
            }
            return;
        }
        // somebody put one down themselves: that is the one, and the world stops counting down to another
        HollowbellEntity already = anyOther(server, null);
        if (already != null) {
            already.markWorldOne();
            seen(already);
            return;
        }
        if (awayWorldOne(server)) { alive = true; dueAt = -1; setDirty(); return; }
        if (dueAt < 0 || level.getGameTime() < dueAt) return;
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
     * Puts one down at the chosen spot; only now is the chunk under him made. The ground there becomes the Bell
     * Hollows, his own chunk is turned right away, and the rest follows as anybody comes near. He comes down out
     * of the sky.
     */
    private void put(ServerLevel level) {
        HollowbellEntity e = ModEntities.HOLLOWBELL.create(level);
        if (e == null) return;
        avoidWard();
        e.setBellScale(Mth.clamp(HollowbellConfig.V.worldScale, HollowbellEntity.MIN_SCALE, HollowbellEntity.MAX_SCALE));
        e.setVariant(HollowbellEntity.CALM);
        e.markWorldOne();
        level.getChunkSource().addRegionTicket(TICKET, new ChunkPos(new BlockPos(x, 64, z)), 3, e.getId());
        var chunk = level.getChunk(x >> 4, z >> 4);
        claimHome(level, x, z);
        if (chunk instanceof net.minecraft.world.level.chunk.LevelChunk lc) HomeGround.paint(level, this, lc);
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
        HomeGround.drain(server);
    }
}
