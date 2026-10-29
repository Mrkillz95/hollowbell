package net.jj.hollowbell.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.HollowbellMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/**
 * His new ground after he dies is claimed in land the world hasn't made yet when it can be. When some of it was made
 * already (players have been there), that land would stay as it was. So the chunks of it that are made are painted as
 * his, a few a tick, with his own ground's plan: the biome always, and the land itself when griefing is allowed (his
 * setting and the mobGriefing gamerule) and nobody has lived in that chunk. Never inside another giant's ground, and
 * never near the world's spawn. Kept with the world, so a restart picks it up again. A chunk that isn't loaded is
 * asked for a few ahead and painted once it's there, so the server never stops and waits for it.
 */
public final class RespawnPaint extends SavedData {
    private static final String NAME = "hollowbell_respawn_paint";
    /** blocks round the world's spawn that are never painted */
    public static final int SPAWN_KEEP = 320;
    /** a chunk players have spent this long in (ticks: a whole day) is somebody's place: only its biome turns */
    public static final long LIVED_IN = 24000L;

    /** the claim being painted (a different claim drops the rest), and the chunks still to do */
    private long seed;

    private int cx, cz, radius;
    private final ArrayDeque<Long> todo = new ArrayDeque<>();
    /** how many chunks were done, and how many of those had their land made his too */
    private int done, landDone;

    /** the land waiting to be painted, held until it is */
    private static final TicketType<ChunkPos> PAINT =
            TicketType.create("hollowbell_respawn_paint", Comparator.comparingLong(ChunkPos::toLong));
    /** how many chunks ahead are asked for at once */
    private static final int AHEAD = 16;
    /** how long the next chunk is waited for (clock ms) before it's loaded the old way, waiting */
    private static final long GIVE_UP = 60_000L;
    /** the chunks asked for (not kept with the world: a restart asks again) */
    private final LongOpenHashSet asked = new LongOpenHashSet();
    /** since when (clock ms) the next chunk has been waited for */
    private long waiting;

    private RespawnPaint() {}

    public static RespawnPaint get(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(RespawnPaint::new, RespawnPaint::load, null), NAME);
    }

    private static RespawnPaint load(CompoundTag tag, HolderLookup.Provider p) {
        RespawnPaint r = new RespawnPaint();
        r.seed = tag.getLong("Seed");
        r.cx = tag.getInt("CX");
        r.cz = tag.getInt("CZ");
        r.radius = tag.getInt("Radius");
        for (long c : tag.getLongArray("Todo")) r.todo.add(c);
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) {
        tag.putLong("Seed", seed);
        tag.putInt("CX", cx);
        tag.putInt("CZ", cz);
        tag.putInt("Radius", radius);
        long[] t = new long[todo.size()];
        int i = 0;
        for (long c : todo) t[i++] = c;
        tag.put("Todo", new LongArrayTag(t));
        return tag;
    }

    public int left() {
        return todo.size();
    }

    public int done() {
        return done;
    }

    public int landDone() {
        return landDone;
    }

    public boolean waiting(ChunkPos cp) {
        return todo.contains(cp.toLong());
    }

    /**
     * His new ground has just been claimed (he died): every chunk of it the world has made already is put in line to
     * be painted. Returns how many.
     */
    public static int start(ServerLevel over) {
        RespawnPaint r = get(over.getServer());
        r.letGo(over);
        r.todo.clear();
        r.done = r.landDone = 0;
        BellGen.Snap s = BellGen.SNAP;
        if (s == null || !s.claimed || s.outline == null) {
            r.setDirty();
            return 0;
        }
        r.seed = s.seed;
        r.cx = s.cx;
        r.cz = s.cz;
        r.radius = s.radius;
        Path dir = over.getServer().getWorldPath(LevelResource.ROOT).resolve("region");
        Long2ObjectOpenHashMap<int[]> headers = new Long2ObjectOpenHashMap<>();
        double reach = s.radius + 24.0;
        for (int x = s.minX >> 4; x <= s.maxX >> 4; x++)
            for (int z = s.minZ >> 4; z <= s.maxZ >> 4; z++) {
                ChunkPos cp = new ChunkPos(x, z);
                double nx = Mth.clamp(s.cx, cp.getMinBlockX(), cp.getMaxBlockX());
                double nz = Mth.clamp(s.cz, cp.getMinBlockZ(), cp.getMaxBlockZ());
                if (Mth.square(nx - s.cx) + Mth.square(nz - s.cz) > reach * reach) continue;
                if (nearSpawn(over, cp)) continue;
                if (over.getChunkSource().hasChunk(x, z) || onDisk(dir, headers, x, z)) r.todo.add(cp.toLong());
            }
        r.setDirty();
        if (!r.todo.isEmpty())
            HollowbellMod.LOG.info(
                    "{} chunks of his new ground at {}, {} were made before: painting them as his",
                    r.todo.size(),
                    s.cx,
                    s.cz);
        return r.todo.size();
    }

    /** is any of this chunk within SPAWN_KEEP blocks of the world's spawn? */
    public static boolean nearSpawn(ServerLevel over, ChunkPos cp) {
        BlockPos sp = over.getSharedSpawnPos();
        double nx = Mth.clamp(sp.getX(), cp.getMinBlockX(), cp.getMaxBlockX());
        double nz = Mth.clamp(sp.getZ(), cp.getMinBlockZ(), cp.getMaxBlockZ());
        return Mth.square(nx - sp.getX()) + Mth.square(nz - sp.getZ()) <= (double) SPAWN_KEEP * SPAWN_KEEP;
    }

    /** is this chunk saved in its region file? (the file's first 4 KB say where each chunk is; 0 = not there) */
    private static boolean onDisk(Path dir, Long2ObjectOpenHashMap<int[]> headers, int x, int z) {
        int rx = x >> 5, rz = z >> 5;
        int[] head = headers.computeIfAbsent(ChunkPos.asLong(rx, rz), k -> readHeader(dir.resolve("r." + rx + "." + rz + ".mca")));
        return head.length > 0 && head[(x & 31) + (z & 31) * 32] != 0;
    }

    private static int[] readHeader(Path f) {
        if (!Files.isRegularFile(f)) return new int[0];
        try (InputStream in = Files.newInputStream(f)) {
            byte[] b = in.readNBytes(4096);
            if (b.length < 4096) return new int[0];
            int[] out = new int[1024];
            for (int i = 0; i < 1024; i++)
                out[i] = (b[i * 4] & 255) << 24 | (b[i * 4 + 1] & 255) << 16 | (b[i * 4 + 2] & 255) << 8 | (b[i * 4 + 3] & 255);
            return out;
        } catch (IOException e) {
            HollowbellMod.LOG.warn("Could not read {}: {}", f, e.toString());
            return new int[0];
        }
    }

    /** every tick: a chunk or two, about 4 ms at most */
    public static void tick(MinecraftServer server) {
        RespawnPaint r = get(server);
        if (r.todo.isEmpty()) return;
        long t0 = System.nanoTime();
        ServerLevel over = server.overworld();
        if (!r.stillHis()) {
            r.dropAll(over);
            return;
        }
        while (!r.todo.isEmpty() && System.nanoTime() - t0 < 4_000_000L) {
            Long c = r.nextLoaded(over);
            if (c == null) {
                // (land not loaded yet: waited for without the server stopping; after a minute, the old way)
                long now = System.currentTimeMillis();
                if (r.waiting == 0) r.waiting = now;
                if (now - r.waiting < GIVE_UP) break;
                c = r.todo.poll();
            }
            r.waiting = 0;
            r.step(over, c);
        }
        if (r.todo.isEmpty()) r.letGo(over);
    }

    /**
     * The first chunk near the front of the line that is loaded now, taken out of the line; the ones before it that
     * aren't are asked for (loaded on the world's own threads). Null if none of them is loaded yet. (Each chunk is
     * painted on its own, so the order they come in makes no difference.)
     */
    private Long nextLoaded(ServerLevel over) {
        var src = over.getChunkSource();
        int n = 0;
        for (var it = todo.iterator(); it.hasNext() && n++ < AHEAD; ) {
            long c = it.next();
            ChunkPos cp = new ChunkPos(c);
            if (src.getChunkNow(cp.x, cp.z) != null) {
                it.remove();
                return c;
            }
            if (asked.add(c)) src.addRegionTicket(PAINT, cp, 0, cp);
        }
        return null;
    }

    private void letGo(ServerLevel over) {
        var src = over.getChunkSource();
        for (long c : asked) {
            ChunkPos cp = new ChunkPos(c);
            src.removeRegionTicket(PAINT, cp, 0, cp);
        }
        asked.clear();
    }

    /** every chunk waiting is done now (the tests) */
    public static void finishAll(MinecraftServer server) {
        RespawnPaint r = get(server);
        while (!r.todo.isEmpty()) r.step(server.overworld(), r.todo.poll());
        r.letGo(server.overworld());
    }

    /** is the claim being painted still his ground? (let go or claimed somewhere else since: the rest isn't) */
    private boolean stillHis() {
        BellGen.Snap s = BellGen.SNAP;
        return s != null && s.claimed && s.outline != null && s.seed == seed && s.cx == cx && s.cz == cz;
    }

    private void dropAll(ServerLevel over) {
        todo.clear();
        letGo(over);
        setDirty();
    }

    private void step(ServerLevel over, long next) {
        BellGen.Snap s = BellGen.SNAP;
        if (!stillHis()) {
            dropAll(over);
            return;
        }
        ChunkPos cp = new ChunkPos(next);
        if (asked.remove(cp.toLong())) over.getChunkSource().removeRegionTicket(PAINT, cp, 0, cp);
        setDirty();
        try {
            if (paintChunk(over, s, cp)) landDone++;
        } catch (Throwable t) {
            HollowbellMod.LOG.error("Painting chunk {} of his new ground failed", cp, t);
        }
        done++;
    }

    /** this chunk made his: the biome, and the land when allowed. True if the land was made his too. */
    static boolean paintChunk(ServerLevel l, BellGen.Snap s, ChunkPos cp) {
        LevelChunk chunk = l.getChunk(cp.x, cp.z);
        if (BellGen.made(chunk)) return false;
        // another giant's ground stays theirs: a chunk with any of it is left as it is
        int minQ = QuartPos.fromBlock(Math.max(0, l.getMinBuildHeight()));
        int topQ = QuartPos.fromBlock(chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8));
        boolean any = false;
        for (int qx = 0; qx < 4; qx++)
            for (int qz = 0; qz < 4; qz++) {
                int ax = QuartPos.fromBlock(cp.getMinBlockX()) + qx, az = QuartPos.fromBlock(cp.getMinBlockZ()) + qz;
                for (int qy = minQ; qy <= Math.max(minQ, topQ); qy++) {
                    var b = chunk.getNoiseBiome(ax, qy, az);
                    if (BellGen.otherGiant(b) && !b.equals(s.biome)) return false;
                }
                if (s.outline.painted(QuartPos.toBlock(ax) + 2, QuartPos.toBlock(az) + 2)) any = true;
            }
        if (!any) return false;
        var sampler = l.getChunkSource().randomState().sampler();
        chunk.fillBiomesFromNoise(
                (qx, qy, qz, smp) -> {
                    var was = chunk.getNoiseBiome(qx, qy, qz);
                    return BellGen.wants(s, was, qx, qy, qz) ? s.biome : was;
                },
                sampler);
        chunk.setUnsaved(true);
        l.getChunkSource().chunkMap.resendBiomesForChunks(List.of(chunk));
        boolean grief = BellConfig.V.griefing && l.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        if (!grief || chunk.getInhabitedTime() >= LIVED_IN) return false;
        HomeGround.paintClipped(l, chunk, HomeGround.planFor(s), (x, z) -> s.inBox(x, z) && s.outline.painted(x, z));
        Painter.forgetStructures(l, chunk);
        BellGen.markMade(chunk);
        chunk.setUnsaved(true);
        return true;
    }
}
