package net.jj.hollowbell.world;

import net.jj.hollowbell.HollowbellMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;

/**
 * The Bell Hollows: his own ground, in exactly one place in the world - where he rises. The land there is
 * turned block by block into pale calcite, bone and green glass as anybody comes near it (a couple of chunks a
 * tick, so nothing ever stutters), and the biome under it becomes hollowbell:bell_hollows, with its own pale
 * fog and drifting motes. The region is an irregular blob round the spot, so the edge looks grown, not drawn.
 */
public final class HomeGround {
    private HomeGround() {}

    public static final ResourceKey<Biome> BELL_HOLLOWS =
            ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "bell_hollows"));

    /** what the ground is made of, by weight out of a hundred */
    private static final Block[] PALETTE = {Blocks.CALCITE, Blocks.END_STONE, Blocks.DIORITE, Blocks.BONE_BLOCK,
            Blocks.SMOOTH_STONE, Blocks.VERDANT_FROGLIGHT, Blocks.LIME_STAINED_GLASS, Blocks.MOSS_BLOCK};
    private static final int[] WEIGHT = {35, 15, 15, 10, 10, 5, 5, 5};

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    // ------------------------------------------------------------------ the shape of his ground

    private static long mix(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L ^ b;
        h ^= h >>> 32; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 29; h *= 0x94D049BB133111EBL; h ^= h >>> 32;
        return h;
    }

    private static long hash(long seed, int a, int b, int c) {
        return mix(mix(seed, a), ((long) b << 32) ^ (c & 0xffffffffL));
    }

    private static float hash01(long seed, int a, int b, int c) {
        return (hash(seed, a, b, c) >>> 40) / (float) (1 << 24);
    }

    /** 3-octave hash noise on the angle, 0..1, deterministic for a seed */
    private static float edgeNoise(long seed, double theta) {
        float sum = 0f, amp = 0.5f, total = 0f;
        int freq = 4;
        for (int o = 0; o < 3; o++) {
            double t = theta / (Math.PI * 2) * freq;
            int i0 = (int) Math.floor(t);
            double f = t - i0;
            float v0 = hash01(seed, o, Math.floorMod(i0, freq), 7);
            float v1 = hash01(seed, o, Math.floorMod(i0 + 1, freq), 7);
            double s = f * f * (3 - 2 * f);
            sum += amp * (float) (v0 + (v1 - v0) * s);
            total += amp;
            amp *= 0.5f; freq *= 2;
        }
        return sum / total;
    }

    /** is this column his? The edge wobbles with the angle, and the outer fringe thins out to nothing. */
    public static boolean inside(WorldOne w, int wx, int wz) {
        if (!w.homeClaimed()) return false;
        double dx = wx + 0.5 - w.homeX(), dz = wz + 0.5 - w.homeZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d >= w.homeRadius()) return false;
        double edge = w.homeRadius() * (0.72 + 0.28 * edgeNoise(w.homeSeed(), Math.atan2(dz, dx) + Math.PI));
        if (d >= edge) return false;
        double fringe = edge * 0.85;
        if (d <= fringe) return true;
        double t = (d - fringe) / Math.max(1, edge - fringe);
        return hash01(w.homeSeed(), wx, wz, 13) > t;
    }

    public static boolean isPalette(BlockState s) {
        for (Block b : PALETTE) if (s.is(b)) return true;
        return false;
    }

    // ------------------------------------------------------------------ turning a chunk

    /** turns one chunk of the world into his ground: blocks, little features, and the biome itself */
    public static void paint(ServerLevel level, WorldOne w, LevelChunk chunk) {
        long key = chunk.getPos().toLong();
        if (!w.homeClaimed() || w.paintedAlready(key)) return;
        w.notePainted(key);
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        boolean any = false;
        for (int cx = 0; cx < 16; cx++) for (int cz = 0; cz < 16; cz++) {
            int wx = x0 + cx, wz = z0 + cz;
            if (!inside(w, wx, wz)) continue;
            any = true;
            clearAbove(level, wx, wz);
            paintColumn(level, w, wx, wz);
        }
        if (!any) return;
        for (int cx = 2; cx < 14; cx++) for (int cz = 2; cz < 14; cz++) {
            int wx = x0 + cx, wz = z0 + cz;
            if (inside(w, wx, wz)) feature(level, w, wx, wz);
        }
        fillBiome(level, w, chunk);
    }

    /** trees and plants over the column go: any log, leaf, mushroom or loose growth down to the real ground */
    private static void clearAbove(ServerLevel level, int wx, int wz) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
        int floor = Math.max(level.getMinBuildHeight() + 1, top - 56);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = top; y >= floor; y--) {
            BlockState s = level.getBlockState(p.set(wx, y, wz));
            if (s.isAir()) continue;
            if (s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(Blocks.MUSHROOM_STEM)
                    || s.is(Blocks.RED_MUSHROOM_BLOCK) || s.is(Blocks.BROWN_MUSHROOM_BLOCK)
                    || s.is(Blocks.VINE) || s.is(Blocks.BEE_NEST) || s.is(Blocks.COCOA)
                    || (s.canBeReplaced() && s.getFluidState().isEmpty())) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
                continue;
            }
            break;                                       // real ground (or water): done here
        }
    }

    /** the ground blocks people have not touched: only these are turned into his palette */
    private static boolean natural(BlockState s) {
        return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL)
                || s.is(Blocks.ROOTED_DIRT) || s.is(Blocks.DIRT_PATH) || s.is(Blocks.MYCELIUM)
                || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.SAND) || s.is(Blocks.RED_SAND)
                || s.is(Blocks.SANDSTONE) || s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK)
                || s.is(Blocks.ICE) || s.is(Blocks.CLAY) || s.is(Blocks.MUD) || s.is(BlockTags.TERRACOTTA)
                || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.CALCITE);
    }

    private static Block pick(long seed, int wx, int y, int wz) {
        int roll = (int) Math.floorMod(hash(seed, wx, wz, y), 100);
        for (int i = 0; i < PALETTE.length; i++) {
            roll -= WEIGHT[i];
            if (roll < 0) return PALETTE[i];
        }
        return Blocks.CALCITE;
    }

    /** the top four blocks of the column become his: pale calcite, bone, old glass. Water stays water. */
    private static void paintColumn(ServerLevel level, WorldOne w, int wx, int wz) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 4; i++) {
            int y = top - i;
            if (y <= level.getMinBuildHeight()) break;
            BlockState s = level.getBlockState(p.set(wx, y, wz));
            if (!s.getFluidState().isEmpty()) continue;          // water below the sea stays water
            if (!natural(s)) continue;
            level.setBlock(p, pick(w.homeSeed(), wx, y, wz).defaultBlockState(), FLAGS);
        }
    }

    /** here and there: a shallow bowl with a froglight glowing at the bottom, or a little shard of glass */
    private static void feature(ServerLevel level, WorldOne w, int wx, int wz) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
        if (top <= level.getSeaLevel()) return;                  // not under water
        long h = hash(w.homeSeed(), wx, wz, 29);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        if (Math.floorMod(h, 400) == 0) {
            // a bowl five across, sunk one or two, with a froglight at the bottom
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                int t = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx + dx, wz + dz) - 1;
                int depth = Math.abs(dx) == 2 || Math.abs(dz) == 2 ? 1 : 2;
                for (int k = 0; k < depth; k++) {
                    BlockState s = level.getBlockState(p.set(wx + dx, t - k, wz + dz));
                    if (!s.getFluidState().isEmpty()) break;
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
            int floor = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
            level.setBlock(p.set(wx, floor, wz), Blocks.VERDANT_FROGLIGHT.defaultBlockState(), FLAGS);
        } else if (Math.floorMod(h, 200) == 1) {
            // a shard of old glass, one to three tall, sometimes lit
            int n = 1 + (int) Math.floorMod(h >> 8, 3);
            for (int k = 0; k < n; k++) {
                if (top + 1 + k >= level.getMaxBuildHeight()) break;
                boolean lit = k == n - 1 && n > 1 && (h & 64) != 0;
                level.setBlock(p.set(wx, top + 1 + k, wz),
                        (lit ? Blocks.VERDANT_FROGLIGHT : Blocks.LIME_STAINED_GLASS).defaultBlockState(), FLAGS);
            }
        }
    }

    /** the ground under his ground says his name: every quart of every section whose column is his */
    private static void fillBiome(ServerLevel level, WorldOne w, LevelChunk chunk) {
        Holder<Biome> home;
        try {
            home = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(BELL_HOLLOWS);
        } catch (Exception e) {
            HollowbellMod.LOG.error("The bell_hollows biome is missing from the data pack: {}", e.toString());
            return;
        }
        var sampler = level.getChunkSource().randomState().sampler();
        chunk.fillBiomesFromNoise((qx, qy, qz, s) ->
                inside(w, QuartPos.toBlock(qx) + 2, QuartPos.toBlock(qz) + 2) ? home : chunk.getNoiseBiome(qx, qy, qz), sampler);
        chunk.setUnsaved(true);
        level.getChunkSource().chunkMap.resendBiomesForChunks(List.of(chunk));
    }

    // ------------------------------------------------------------------ a couple of chunks a tick

    private static final ArrayDeque<Long> queue = new ArrayDeque<>();
    private static final HashSet<Long> queued = new HashSet<>();

    /** a chunk has come in: if it is his and not yet turned, it waits its turn */
    public static void chunkLoaded(ServerLevel level, LevelChunk chunk) {
        if (level.dimension() != Level.OVERWORLD) return;
        WorldOne w = WorldOne.get(level.getServer());
        maybeQueue(w, chunk.getPos());
    }

    /** fresh ground claimed: the chunks already sitting loaded near it get in the queue themselves */
    public static void claimed(ServerLevel level, WorldOne w) {
        if (!w.homeClaimed()) return;
        int r = ((w.homeRadius() + 24) >> 4) + 1;
        int ccx = w.homeX() >> 4, ccz = w.homeZ() >> 4;
        for (int cx = ccx - r; cx <= ccx + r; cx++)
            for (int cz = ccz - r; cz <= ccz + r; cz++) {
                if (level.getChunkSource().getChunkNow(cx, cz) == null) continue;
                maybeQueue(w, new ChunkPos(cx, cz));
            }
    }

    private static void maybeQueue(WorldOne w, ChunkPos pos) {
        if (!w.homeClaimed()) return;
        long key = pos.toLong();
        if (w.paintedAlready(key) || queued.contains(key)) return;
        double dx = pos.getMiddleBlockX() - w.homeX(), dz = pos.getMiddleBlockZ() - w.homeZ();
        if (dx * dx + dz * dz > (double) (w.homeRadius() + 24) * (w.homeRadius() + 24)) return;
        queued.add(key);
        queue.add(key);
    }

    /** at most two chunks a tick are turned, so his ground grows without the world ever stuttering */
    public static void drain(MinecraftServer server) {
        if (queue.isEmpty()) return;
        ServerLevel over = server.overworld();
        WorldOne w = WorldOne.get(server);
        for (int i = 0; i < 2 && !queue.isEmpty(); i++) {
            long key = queue.poll();
            queued.remove(key);
            if (!w.homeClaimed() || w.paintedAlready(key)) continue;
            LevelChunk c = over.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
            if (c == null) continue;                 // gone again; it queues itself when it comes back
            paint(over, w, c);
        }
    }

    /** the server is done: nothing waits around for the next world */
    public static void forgetQueue() {
        queue.clear();
        queued.clear();
    }
}
