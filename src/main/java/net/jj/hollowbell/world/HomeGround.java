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

    /**
     * Turns one chunk of the world into his ground: blocks, little features, and the biome itself. A chunk people
     * have spent time in, or one a village or other building on the surface reaches into, is left as it is.
     * Everything here stays inside the chunk being turned.
     */
    public static void paint(ServerLevel level, WorldOne w, LevelChunk chunk) {
        long key = chunk.getPos().toLong();
        if (!w.homeClaimed() || w.paintedAlready(key)) return;
        w.notePainted(key);
        if (lived(chunk) || built(chunk)) return;
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

    /** players have spent a minute or more around here: it's somebody's place now */
    public static boolean lived(LevelChunk chunk) { return chunk.getInhabitedTime() > 1200; }

    /** a village, temple or anything else built on the land reaches into this chunk (mines and strongholds don't count) */
    public static boolean built(LevelChunk chunk) {
        for (var s : chunk.getAllReferences().entrySet()) {
            if (s.getValue() == null || s.getValue().isEmpty()) continue;
            var type = s.getKey().type();
            if (type == net.minecraft.world.level.levelgen.structure.StructureType.MINESHAFT
                    || type == net.minecraft.world.level.levelgen.structure.StructureType.STRONGHOLD) continue;
            return true;
        }
        return false;
    }

    /** grass, flowers, snow cover and the like: loose on top of the ground, never somebody's work */
    private static boolean loose(BlockState s) {
        if (s.isAir() || s.is(BlockTags.LEAVES) || !s.getFluidState().isEmpty()) return false;
        return s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.canBeReplaced();
    }

    /** a leaf that grew on a tree (placed ones are kept) */
    private static boolean wildLeaf(BlockState s) {
        return s.is(BlockTags.LEAVES) && !(s.hasProperty(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)
                && s.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT));
    }

    /** a wild tree or giant mushroom, down to its trunk */
    private static boolean treePart(BlockState s) {
        return wildLeaf(s) || s.is(BlockTags.LOGS) || s.is(Blocks.MUSHROOM_STEM) || s.is(Blocks.RED_MUSHROOM_BLOCK)
                || s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.is(Blocks.VINE) || s.is(Blocks.BEE_NEST) || s.is(Blocks.COCOA) || loose(s);
    }

    /** clears the loose plants off the top of the column; returns the height of what is left on top */
    private static int clearLoose(ServerLevel level, int wx, int wz, BlockPos.MutableBlockPos p) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
        int floor = level.getMinBuildHeight() + 1;
        while (y > floor) {
            BlockState s = level.getBlockState(p.set(wx, y, wz));
            if (!loose(s)) break;
            level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
            y--;
        }
        return y;
    }

    /**
     * Wild trees over the column go, and the grass and flowers on the ground. Only when the very top of the
     * column is a tree's own leaves (or a giant mushroom's cap) is anything but loose plants cleared, and the
     * clearing stops at the first gap of air or at anything that isn't tree: a log cabin, a roof, a post all stay.
     */
    private static void clearAbove(ServerLevel level, int wx, int wz) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int top = clearLoose(level, wx, wz, p);
        BlockState s = level.getBlockState(p.set(wx, top, wz));
        if (!(wildLeaf(s) || s.is(Blocks.RED_MUSHROOM_BLOCK) || s.is(Blocks.BROWN_MUSHROOM_BLOCK))) return;
        int floor = Math.max(level.getMinBuildHeight() + 1, top - 56);
        for (int y = top; y >= floor; y--) {
            BlockState b = level.getBlockState(p.set(wx, y, wz));
            if (!treePart(b)) break;                     // air, ground, or somebody's work: done here
            level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
        }
        clearLoose(level, wx, wz, p);                     // and whatever grew under the canopy
    }

    /** the ground blocks people have not touched: only these are turned into his palette */
    private static boolean natural(BlockState s) {
        return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL)
                || s.is(Blocks.ROOTED_DIRT) || s.is(Blocks.MYCELIUM)
                || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.SAND) || s.is(Blocks.RED_SAND)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK)
                || s.is(Blocks.ICE) || s.is(Blocks.CLAY) || s.is(Blocks.MUD) || s.is(BlockTags.TERRACOTTA)
                || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.CALCITE);
    }

    /** natural too, but left as it is: bedrock, sandstone under a desert, ores showing at the top */
    private static boolean leftAlone(BlockState s) {
        return s.is(Blocks.BEDROCK) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.PACKED_ICE)
                || s.is(Blocks.BLUE_ICE) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.SNOW)
                || s.is(BlockTags.COAL_ORES) || s.is(BlockTags.IRON_ORES) || s.is(BlockTags.COPPER_ORES)
                || s.is(BlockTags.GOLD_ORES) || s.is(BlockTags.REDSTONE_ORES) || s.is(BlockTags.LAPIS_ORES)
                || s.is(BlockTags.DIAMOND_ORES) || s.is(BlockTags.EMERALD_ORES);
    }

    private static Block pick(long seed, int wx, int y, int wz) {
        int roll = (int) Math.floorMod(hash(seed, wx, wz, y), 100);
        for (int i = 0; i < PALETTE.length; i++) {
            roll -= WEIGHT[i];
            if (roll < 0) return PALETTE[i];
        }
        return Blocks.CALCITE;
    }

    /**
     * The top four blocks of the column become his: pale calcite, bone, old glass. Water stays water. A column
     * with anything in its top few blocks that isn't plain ground (planks, a path, a chest, a crop) is left whole.
     */
    private static void paintColumn(ServerLevel level, WorldOne w, int wx, int wz) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 4; i++) {
            int y = top - i;
            if (y <= level.getMinBuildHeight()) break;
            BlockState s = level.getBlockState(p.set(wx, y, wz));
            if (s.isAir() || !s.getFluidState().isEmpty() || natural(s) || isPalette(s) || leftAlone(s)) continue;
            return;                                              // somebody's work: the whole column is left alone
        }
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
        BlockState ground = level.getBlockState(new BlockPos(wx, top, wz));
        if (!(natural(ground) || isPalette(ground))) return;      // never on a roof, a path, somebody's floor
        long h = hash(w.homeSeed(), wx, wz, 29);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        if (Math.floorMod(h, 400) == 0) {
            // a bowl five across, sunk one or two, with a froglight at the bottom
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                int t = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx + dx, wz + dz) - 1;
                int depth = Math.abs(dx) == 2 || Math.abs(dz) == 2 ? 1 : 2;
                for (int k = 0; k < depth; k++) {
                    BlockState s = level.getBlockState(p.set(wx + dx, t - k, wz + dz));
                    if (!s.getFluidState().isEmpty() || !(natural(s) || isPalette(s))) break;   // never into somebody's floor
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
            int floor = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
            BlockState under = level.getBlockState(p.set(wx, floor, wz));
            if (natural(under) || isPalette(under)) level.setBlock(p, Blocks.VERDANT_FROGLIGHT.defaultBlockState(), FLAGS);
        } else if (Math.floorMod(h, 200) == 1) {
            // a shard of old glass, one to three tall, sometimes lit
            int n = 1 + (int) Math.floorMod(h >> 8, 3);
            for (int k = 0; k < n; k++) {
                if (top + 1 + k >= level.getMaxBuildHeight() || !level.getBlockState(p.set(wx, top + 1 + k, wz)).isAir()) break;
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
        if (level.dimension() != Level.OVERWORLD || WorldOne.IN_TESTS) return;   // the tests paint by hand
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
        long started = System.nanoTime();
        // one chunk, and a second only if the first was quick (about 4 ms a tick at most)
        for (int i = 0; i < 2 && !queue.isEmpty() && (i == 0 || System.nanoTime() - started < 4_000_000L); i++) {
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
