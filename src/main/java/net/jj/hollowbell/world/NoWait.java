package net.jj.hollowbell.world;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Looking at the world without ever making the server wait for it. The game's own getChunk, getHeight and
 * getBlockState stop the whole server until a chunk that isn't ready yet has been loaded or made (hasChunk also says
 * yes for land that is still being made): on a real server that froze it for up to 30 seconds. These only read chunks
 * that are loaded right now, and say so when one isn't; land that's needed is asked for and made on the world's own
 * threads, and whatever needed it goes on once it's there.
 */
public final class NoWait {
    private NoWait() {}

    /** no answer: the chunk isn't loaded yet */
    public static final int NOT_YET = Integer.MIN_VALUE;

    /** asks for land to be loaded (and made, if it has to be) on the world's own threads, for a few seconds */
    public static final TicketType<ChunkPos> SOON = TicketType.create("hollowbell_soon", Comparator.comparingLong(ChunkPos::toLong), 100);

    /** the chunk at this block, if it's loaded right now (never loads or makes one) */
    public static @Nullable LevelChunk chunk(Level l, int x, int z) {
        return l.getChunkSource().getChunkNow(x >> 4, z >> 4);
    }

    public static boolean loaded(Level l, int x, int z) { return chunk(l, x, z) != null; }
    public static boolean loaded(Level l, BlockPos p) { return chunk(l, p.getX(), p.getZ()) != null; }
    /** by chunk: loaded right now (hasChunk also says yes for one still being made) */
    public static boolean loadedChunk(Level l, int cx, int cz) { return l.getChunkSource().getChunkNow(cx, cz) != null; }

    /** the land within a few blocks of here is all loaded */
    public static boolean loadedAround(Level l, int x, int z) {
        for (int cx = (x - 3) >> 4; cx <= (x + 3) >> 4; cx++)
            for (int cz = (z - 3) >> 4; cz <= (z + 3) >> 4; cz++) if (l.getChunkSource().getChunkNow(cx, cz) == null) return false;
        return true;
    }

    /** the block, or air if its chunk isn't loaded */
    public static BlockState state(Level l, BlockPos p) {
        if (l.isOutsideBuildHeight(p)) return Blocks.VOID_AIR.defaultBlockState();
        LevelChunk c = chunk(l, p.getX(), p.getZ());
        return c == null ? Blocks.AIR.defaultBlockState() : c.getBlockState(p);
    }

    /** the same as the level's getHeight (the first free block over the top), or NOT_YET */
    public static int height(Level l, Heightmap.Types t, int x, int z) {
        LevelChunk c = chunk(l, x, z);
        return c == null ? NOT_YET : c.getHeight(t, x & 15, z & 15) + 1;
    }

    /** the height if it's loaded, else the generator's guess (never waits) */
    public static int heightOrGuess(ServerLevel l, Heightmap.Types t, int x, int z) {
        int y = height(l, t, x, z);
        return y != NOT_YET ? y : guessGround(l, x, z);
    }

    /** ask for the land round this block to be made ready (radius in chunks), without waiting for it */
    public static void ask(ServerLevel l, int x, int z, int radius) {
        ChunkPos cp = new ChunkPos(x >> 4, z >> 4);
        l.getChunkSource().addRegionTicket(SOON, cp, radius, cp);
    }

    /** where the ground is likely to be here without the land being there yet (the generator's first height) */
    public static int guessGround(ServerLevel l, int x, int z) {
        var src = l.getChunkSource();
        int y = src.getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, l, src.randomState());
        return Mth.clamp(y, l.getMinBuildHeight() + 1, l.getMaxBuildHeight() - 1);
    }

    // ------------------------------------------------------------------ trips to land that isn't loaded yet

    /**
     * A tp to land that isn't loaded yet: going there at once would have the server stop and wait while it's made.
     * Instead the land is asked for and the player goes the moment it's there (or after a minute, onto a guess).
     * `arrive` sets the player down and gives what to tell them.
     */
    private record Trip(ResourceKey<Level> dim, int x, int z, Function<ServerPlayer, Component> arrive, long until /* clock ms */) {}
    private static final Map<UUID, Trip> TRIPS = new HashMap<>();
    /** how long a trip waits for its land at most (60 seconds, by the clock: land is made in real time, whatever the ticks do) */
    static final int TRIP_WAIT = 1200;

    public static void go(ServerPlayer p, ServerLevel l, int x, int z, Function<ServerPlayer, Component> arrive) {
        ask(l, x, z, 2);
        TRIPS.put(p.getUUID(), new Trip(l.dimension(), x, z, arrive, System.currentTimeMillis() + TRIP_WAIT * 50L));
    }

    /** is this player waiting on a trip? (the tests ask) */
    public static boolean onTheWay(ServerPlayer p) { return TRIPS.containsKey(p.getUUID()); }

    // ------------------------------------------------------------------ land held until it's there

    /** land asked for until it's loaded (a single ask runs out after five seconds, and new land can take longer) */
    private record Hold(ResourceKey<Level> dim, int x, int z, long until /* clock ms */) {}
    private static final java.util.List<Hold> HOLDS = new java.util.ArrayList<>();

    /** ask for the land here and keep asking until it's loaded (at most `ticks`): something was put there that has to run */
    public static void hold(ServerLevel l, int x, int z, int ticks) {
        ask(l, x, z, 1);
        HOLDS.add(new Hold(l.dimension(), x, z, System.currentTimeMillis() + ticks * 50L));
    }

    private static void holds(MinecraftServer server) {
        if (HOLDS.isEmpty()) return;
        var it = HOLDS.iterator();
        while (it.hasNext()) {
            Hold h = it.next();
            ServerLevel l = server.getLevel(h.dim());
            if (l == null || System.currentTimeMillis() > h.until()) { it.remove(); continue; }
            if (loaded(l, h.x(), h.z())) { ask(l, h.x(), h.z(), 1); it.remove(); continue; }   // (one more ask: it runs a while longer)
            if (l.getGameTime() % 40 == 0) ask(l, h.x(), h.z(), 1);
        }
    }

    // ------------------------------------------------------------------ one just put down, held running a while

    /** held round one just put down far from everybody, at the level where things there take their ticks */
    public static final TicketType<ChunkPos> SETTLE = TicketType.create("hollowbell_settle", Comparator.comparingLong(ChunkPos::toLong), 100);
    private record Settle(ResourceKey<Level> dim, int x, int z, long giveUp /* clock ms */, int ticks, long[] loadedAt) {}
    private static final java.util.List<Settle> SETTLES = new java.util.ArrayList<>();

    /**
     * One was put down here, maybe far from everybody (a summon): his land is asked for without waiting, and once it's
     * loaded it's kept running for `ticks` more, so he takes his first ticks there (he can step out of the world or
     * write down where he is). A ticket that only loads the land isn't enough: nothing in it ever takes a tick.
     */
    public static void settle(ServerLevel l, int x, int z, int ticks) {
        ChunkPos cp = new ChunkPos(x >> 4, z >> 4);
        l.getChunkSource().addRegionTicket(SETTLE, cp, 3, cp);
        SETTLES.add(new Settle(l.dimension(), x, z, System.currentTimeMillis() + 60_000L, ticks, new long[] {-1}));
    }

    /** is anything still being held running? (the tests ask) */
    public static boolean settling() { return !SETTLES.isEmpty(); }

    private static void settles(MinecraftServer server) {
        if (SETTLES.isEmpty()) return;
        var it = SETTLES.iterator();
        while (it.hasNext()) {
            Settle s = it.next();
            ServerLevel l = server.getLevel(s.dim());
            if (l == null) { it.remove(); continue; }
            long now = l.getGameTime();
            if (s.loadedAt()[0] < 0) {
                if (loaded(l, s.x(), s.z())) s.loadedAt()[0] = now;
                else if (System.currentTimeMillis() > s.giveUp()) { it.remove(); continue; }
            } else if (now - s.loadedAt()[0] > s.ticks()) { it.remove(); continue; }   // (the ticket runs out on its own)
            if (now % 40 == 0) {
                ChunkPos cp = new ChunkPos(s.x() >> 4, s.z() >> 4);
                l.getChunkSource().addRegionTicket(SETTLE, cp, 3, cp);
            }
        }
    }

    public static void tick(MinecraftServer server) {
        settles(server);
        holds(server);
        if (TRIPS.isEmpty()) return;
        var it = TRIPS.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Trip t = e.getValue();
            ServerLevel l = server.getLevel(t.dim());
            if (p == null || l == null || p.isRemoved()) { it.remove(); continue; }
            boolean ready = loadedAround(l, t.x(), t.z());
            if (!ready && System.currentTimeMillis() <= t.until()) {
                // (the ask runs out after a few seconds: asked again while the land is still being made)
                if (l.getGameTime() % 40 == 0) ask(l, t.x(), t.z(), 2);
                continue;
            }
            it.remove();
            Component said = t.arrive().apply(p);
            p.fallDistance = 0;
            if (said != null) p.displayClientMessage(said, false);
        }
    }

    public static void forget() { TRIPS.clear(); HOLDS.clear(); SETTLES.clear(); }
}
