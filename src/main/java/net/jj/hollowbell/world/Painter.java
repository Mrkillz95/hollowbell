package net.jj.hollowbell.world;

import net.jj.hollowbell.HollowbellMod;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /giants paint hollowbell [radius] [full|biome] (admins only): the land round the player becomes the Bell Hollows,
 * whatever is there. The biome of every column in the circle (every height) is set to his; in "full" the land is
 * also shaped and dressed like his own ground (no den): everything standing on it (buildings, trees, a witch hut)
 * is cleared first, only bedrock is left, and what was in chests comes out on the new ground. Structures standing
 * there stop being structures (no more witches from a painted-over hut, /locate stops pointing there). A chunk or
 * two a tick, so the server keeps up. It can't be undone.
 */
public final class Painter {
    private Painter() {}

    private static final class Job {
        UUID who; ServerLevel level; double cx, cz; int radius; boolean full;
        BellPlan plan;
        final ArrayDeque<ChunkPos> todo = new ArrayDeque<>();
        int total, done;
    }

    private static final List<Job> jobs = new ArrayList<>();

    /** starts painting round this player; returns how many chunks it will take */
    public static int start(ServerPlayer p, int radius, boolean full) {
        return startAt(p.serverLevel(), p.getUUID(), p.getX(), p.getZ(), radius, full);
    }

    /** starts painting round a spot; who (may be null) is told how it goes */
    public static int startAt(ServerLevel level, @Nullable UUID who, double atX, double atZ, int radius, boolean full) {
        Job j = new Job();
        j.who = who;
        j.level = level;
        j.cx = atX; j.cz = atZ;
        j.radius = Mth.clamp(radius, 16, 512);
        j.full = full;
        ServerLevel l = j.level;
        var src = l.getChunkSource();
        // a plan big enough that the whole circle is well inside the middle of it, and no den
        long seed = BellPlan.mix(l.getSeed() ^ 0x7A1E7L, BellPlan.mix(Mth.floor(j.cx), Mth.floor(j.cz)));
        j.plan = new BellPlan(seed, Mth.floor(j.cx), Mth.floor(j.cz), (int) Math.ceil(j.radius * 3.2), l.getSeaLevel(),
                l.getMinBuildHeight(), l.getMaxBuildHeight(),
                (x, z) -> src.getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, l, src.randomState()) - 1);
        j.plan.withDen = false;
        int r = j.radius;
        for (int x = Math.floorDiv(Mth.floor(j.cx) - r, 16); x <= Math.floorDiv(Mth.floor(j.cx) + r, 16); x++)
            for (int z = Math.floorDiv(Mth.floor(j.cz) - r, 16); z <= Math.floorDiv(Mth.floor(j.cz) + r, 16); z++) {
                double nx = Mth.clamp(j.cx, x * 16, x * 16 + 15), nz = Mth.clamp(j.cz, z * 16, z * 16 + 15);
                if (Mth.square(nx - j.cx) + Mth.square(nz - j.cz) <= (double) r * r) j.todo.add(new ChunkPos(x, z));
            }
        j.total = j.todo.size();
        jobs.add(j);
        return j.total;
    }

    public static boolean busy() { return !jobs.isEmpty(); }

    /** every tick: a chunk or two, about 8 ms at most */
    public static void tick(MinecraftServer server) {
        if (jobs.isEmpty()) return;
        long t0 = System.nanoTime();
        Job j = jobs.get(0);
        while (!j.todo.isEmpty() && (System.nanoTime() - t0 < 8_000_000L)) {
            ChunkPos cp = j.todo.poll();
            try { paintChunk(j, cp); } catch (Throwable t) { HollowbellMod.LOG.error("Painting chunk {} failed", cp, t); }
            j.done++;
            if (j.done % 16 == 0 && !j.todo.isEmpty()) tell(server, j, Component.translatable("command.hollowbell.paint_progress", j.done, j.total));
        }
        if (j.todo.isEmpty()) {
            jobs.remove(0);
            tell(server, j, Component.translatable("command.hollowbell.paint_done", j.total));
        }
    }

    /** for the tests: every paint waiting is done now */
    public static void finishAll(MinecraftServer server) {
        while (!jobs.isEmpty()) {
            Job j = jobs.remove(0);
            while (!j.todo.isEmpty()) {
                ChunkPos cp = j.todo.poll();
                paintChunk(j, cp);
                j.done++;
            }
        }
    }

    private static void tell(MinecraftServer server, Job j, Component c) {
        if (j.who == null) return;
        ServerPlayer p = server.getPlayerList().getPlayer(j.who);
        if (p != null) p.displayClientMessage(c, false);
    }

    private static @Nullable Holder<Biome> biome(ServerLevel l) {
        try { return l.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(HomeGround.BELL_HOLLOWS); }
        catch (Exception e) { return null; }
    }

    private static void paintChunk(Job j, ChunkPos cp) {
        ServerLevel l = j.level;
        LevelChunk chunk = l.getChunk(cp.x, cp.z);
        Holder<Biome> home = biome(l);
        double r2 = (double) j.radius * j.radius;
        if (home != null) {
            var sampler = l.getChunkSource().randomState().sampler();
            // the whole column, every height: nothing under or over it keeps the old biome
            chunk.fillBiomesFromNoise((qx, qy, qz, s) -> {
                double bx = QuartPos.toBlock(qx) + 2, bz = QuartPos.toBlock(qz) + 2;
                boolean in = Mth.square(bx - j.cx) + Mth.square(bz - j.cz) <= r2;
                return in ? home : chunk.getNoiseBiome(qx, qy, qz);
            }, sampler);
            chunk.setUnsaved(true);
            l.getChunkSource().chunkMap.resendBiomesForChunks(List.of(chunk));
        }
        if (j.full) {
            // only the columns inside the circle: the plan says "his" for all of them; the rest of the chunk is kept
            BellPlan clipped = j.plan;
            HomeGround.paintInCircle(l, chunk, clipped, j.cx, j.cz, j.radius);
            forgetStructures(l, chunk);
        }
    }

    /** a structure reaching up to the surface: a hut, a village, a ruin (a mine or a stronghold deep down is not) */
    public static boolean surface(StructureStart st, int sea) {
        return st.isValid() && st.getBoundingBox().maxY() >= sea - 16;
    }

    /**
     * The structures standing in a painted chunk stop being structures: the starts in it and its references to them
     * are dropped (no more witches from a painted-over hut, no more village, /locate stops pointing there).
     */
    public static void forgetStructures(ServerLevel l, LevelChunk chunk) {
        int sea = l.getSeaLevel();
        boolean changed = false;
        Map<Structure, LongSet> keep = new HashMap<>();
        for (var e : chunk.getAllReferences().entrySet()) {
            LongSet kept = new LongOpenHashSet();
            for (long ref : e.getValue()) {
                ChunkPos at = new ChunkPos(ref);
                StructureStart st = l.getChunk(at.x, at.z, ChunkStatus.STRUCTURE_STARTS, true).getStartForStructure(e.getKey());
                if (st == null || !st.isValid() || surface(st, sea)) { changed = true; continue; }
                kept.add(ref);
            }
            if (!kept.isEmpty()) keep.put(e.getKey(), kept);
        }
        if (changed) chunk.setAllReferences(keep);
        Map<Structure, StructureStart> starts = new HashMap<>(chunk.getAllStarts());
        if (starts.values().removeIf(st -> surface(st, sea))) {
            chunk.setAllStarts(starts);
            changed = true;
        }
        if (changed) {
            chunk.setUnsaved(true);
            // what /locate and mob spawns remember about this chunk is told again
            ((net.jj.hollowbell.mixin.StructureManagerAccess) l.structureManager()).hollowbell$check().onStructureLoad(chunk.getPos(), chunk.getAllStarts());
        }
    }
}
