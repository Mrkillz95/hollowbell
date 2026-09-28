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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * The Bell Hollows: his own ground, in exactly one place in the world - where he rises. What it looks like is
 * worked out in {@link BellPlan}; this puts it into the world as anybody comes near (a chunk or two a tick, so
 * nothing ever stutters): soft hills scooped with hollows, pale calcite and bone with mint grass, fallen glass
 * shards, spires, ribs, reefs, gardens, pools and lights, and his great glass bell in the middle. The biome under
 * it becomes hollowbell:bell_hollows, with its own pale fog and drifting motes.
 *
 * The safety rules: chunks people have lived in and chunks with a village or other building are left alone; only
 * wild trees are cleared; a column with anything but plain ground near its top is not touched; nothing is read or
 * written outside the chunk being turned; water is only ever put where it has a floor and walls.
 */
public final class HomeGround {
    private HomeGround() {}

    public static final ResourceKey<Biome> BELL_HOLLOWS =
            ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "bell_hollows"));

    /** what the first, smaller Hollows were made of: still counted as plain ground when it is turned again */
    private static final Block[] PALETTE = {Blocks.CALCITE, Blocks.END_STONE, Blocks.DIORITE, Blocks.BONE_BLOCK,
            Blocks.SMOOTH_STONE, Blocks.VERDANT_FROGLIGHT, Blocks.LIME_STAINED_GLASS, Blocks.MOSS_BLOCK};

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final boolean DEBUG = Boolean.getBoolean("hollowbell.debug");

    // ------------------------------------------------------------------ the shape of his ground

    /** the plan for the ground now claimed, kept while the claim stays the same */
    private static BellPlan plan;
    private static long planKey;
    private static ServerLevel planLevel;

    /** what the ground looks like, worked out from its seed; the land's height comes from the world's generator */
    public static BellPlan plan(ServerLevel level, WorldOne w) {
        long key = BellPlan.mix(BellPlan.mix(w.homeSeed(), w.homeX()), BellPlan.mix(w.homeZ(), w.homeRadius()));
        if (plan == null || planKey != key || planLevel != level) {
            var src = level.getChunkSource();
            BellPlan.Ground g = (x, z) -> src.getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, src.randomState()) - 1;
            plan = new BellPlan(w.homeSeed(), w.homeX(), w.homeZ(), w.homeRadius(), level.getSeaLevel(),
                    level.getMinBuildHeight(), level.getMaxBuildHeight(), g);
            planKey = key;
            planLevel = level;
        }
        return plan;
    }

    /** is this column his? The edge wobbles and bulges, and the outer fringe thins out in patches to nothing. */
    public static boolean inside(ServerLevel level, WorldOne w, int wx, int wz) {
        return w.homeClaimed() && plan(level, w).painted(wx, wz);
    }

    public static boolean isPalette(BlockState s) {
        for (Block b : PALETTE) if (s.is(b)) return true;
        return false;
    }

    /** any block his ground is made of, on it or in it */
    public static boolean isOurs(BlockState s) {
        for (BellPlan.Mat m : BellPlan.Mat.values()) {
            BlockState b = state(m);
            if (b != null && s.is(b.getBlock())) return true;
        }
        return false;
    }

    /** the real block for each thing the plan asks for */
    public static BlockState state(BellPlan.Mat m) {
        return switch (m) {
            case KEEP -> null;
            case CALCITE -> Blocks.CALCITE.defaultBlockState();
            case DIORITE -> Blocks.DIORITE.defaultBlockState();
            case POLISHED_DIORITE -> Blocks.POLISHED_DIORITE.defaultBlockState();
            case END_STONE -> Blocks.END_STONE.defaultBlockState();
            case BONE -> Blocks.BONE_BLOCK.defaultBlockState();
            case SMOOTH_STONE -> Blocks.SMOOTH_STONE.defaultBlockState();
            case MOSS -> Blocks.MOSS_BLOCK.defaultBlockState();
            case GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
            case VERDANT -> Blocks.VERDANT_FROGLIGHT.defaultBlockState();
            case PEARL -> Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
            case OCHRE -> Blocks.OCHRE_FROGLIGHT.defaultBlockState();
            case LIME_GLASS -> Blocks.LIME_STAINED_GLASS.defaultBlockState();
            case WHITE_GLASS -> Blocks.WHITE_STAINED_GLASS.defaultBlockState();
            case GRAY_GLASS -> Blocks.LIGHT_GRAY_STAINED_GLASS.defaultBlockState();
            case GREEN_GLASS -> Blocks.GREEN_STAINED_GLASS.defaultBlockState();
            case MOSS_CARPET -> Blocks.MOSS_CARPET.defaultBlockState();
            case GLOW_LICHEN -> Blocks.GLOW_LICHEN.defaultBlockState().setValue(net.minecraft.world.level.block.MultifaceBlock.getFaceProperty(net.minecraft.core.Direction.DOWN), true);
            case DRIPLEAF_LOW -> Blocks.SMALL_DRIPLEAF.defaultBlockState().setValue(net.minecraft.world.level.block.SmallDripleafBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER);
            case DRIPLEAF_HIGH -> Blocks.SMALL_DRIPLEAF.defaultBlockState().setValue(net.minecraft.world.level.block.SmallDripleafBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
            case END_ROD -> Blocks.END_ROD.defaultBlockState();
            case CHAIN -> Blocks.CHAIN.defaultBlockState();
            case WATER -> Blocks.WATER.defaultBlockState();
        };
    }

    // ------------------------------------------------------------------ turning a chunk

    /**
     * Turns one chunk of the world into his ground: the land's shape, its blocks, the things standing on it, and
     * the biome itself. A chunk people have spent time in, or one a village or other building on the surface
     * reaches into, is left as it is. Everything here reads and writes only inside the chunk being turned; the
     * bigger features come out whole because each chunk works out its own part of them from the seed.
     */
    public static void paint(ServerLevel level, WorldOne w, LevelChunk chunk) {
        long key = chunk.getPos().toLong();
        if (!w.homeClaimed() || w.paintedAlready(key)) return;
        w.notePainted(key);
        boolean wasOld = w.forgetOldPaint(key);          // turned by the first, smaller Hollows: turned again over it
        if (lived(chunk) || built(chunk)) return;
        BellPlan p = plan(level, w);
        if (!p.near(chunk.getPos().x, chunk.getPos().z)) return;
        long t0 = System.nanoTime();
        Survey sv = survey(level, p, chunk, true, wasOld ? w.homeSeed() : null);
        if (!sv.any) return;
        BellPlan.Out o = new BellPlan.Out();
        p.chunk(chunk.getPos().x, chunk.getPos().z, sv.y0, sv.ok, sv.wet, sv.lowest, o);
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        List<BlockPos> water = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            if (!o.paint[i]) continue;
            int wx = x0 + (i & 15), wz = z0 + (i >> 4);
            if (sv.wet[i]) { paintBed(level, p, wx, wz, sv.y0[i], m); continue; }
            shape(level, o, i, wx, wz, sv.y0[i], m, water);
        }
        keepWaterIn(level, water, x0, z0, m);
        fillBiome(level, p, chunk);
        if (WorldOne.IN_TESTS) { lastOut = o; lastY0 = sv.y0; }
        if (DEBUG) HollowbellMod.LOG.info("Turned chunk {}, {} into the Bell Hollows in {} ms", chunk.getPos().x, chunk.getPos().z,
                String.format("%.1f", (System.nanoTime() - t0) / 1e6));
    }

    /** for the tests: what the last chunk turned was asked to become, and where its ground was before */
    public static BellPlan.Out lastOut;
    public static int[] lastY0;

    /** a chunk's columns as they stand: where the ground is, whether it may be turned, water, how deep it may be cut */
    private static final class Survey {
        final int[] y0 = new int[256], lowest = new int[256];
        final boolean[] ok = new boolean[256], wet = new boolean[256];
        boolean any;
    }

    private static Survey survey(ServerLevel level, BellPlan p, LevelChunk chunk, boolean clear, Long oldSeed) {
        Survey sv = new Survey();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 256; i++) {
            int wx = x0 + (i & 15), wz = z0 + (i >> 4);
            if (!p.painted(wx, wz)) continue;
            sv.any = true;
            if (clear) {
                clearAbove(level, wx, wz);
                if (oldSeed != null) stripOldShard(level, oldSeed, wx, wz, m);
            }
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
            sv.y0[i] = top;
            sv.lowest[i] = top;
            if (top <= level.getMinBuildHeight() + 2) continue;
            BlockState s = level.getBlockState(m.set(wx, top, wz));
            sv.wet[i] = !s.getFluidState().isEmpty();
            sv.ok[i] = plainGround(level, wx, wz, top, m);
            if (!sv.ok[i] || sv.wet[i] || !(natural(s) || isPalette(s))) continue;
            // cut no deeper than the plain ground goes: the new top is itself plain ground, never air or a cave
            int low = top;
            for (int y = top - 1; y >= top - BellPlan.MAX_DOWN - 1 && y > level.getMinBuildHeight() + 1; y--) {
                BlockState b = level.getBlockState(m.set(wx, y, wz));
                if (!(natural(b) || isPalette(b))) break;
                low = y;
            }
            sv.lowest[i] = low;
        }
        return sv;
    }

    /** for the tests: what this chunk would become, worked out without changing anything */
    public static BellPlan.Out preview(ServerLevel level, WorldOne w, LevelChunk chunk, int[] y0Out) {
        BellPlan p = plan(level, w);
        Survey sv = survey(level, p, chunk, false, null);
        BellPlan.Out o = new BellPlan.Out();
        p.chunk(chunk.getPos().x, chunk.getPos().z, sv.y0, sv.ok, sv.wet, sv.lowest, o);
        if (y0Out != null) System.arraycopy(sv.y0, 0, y0Out, 0, 256);
        return o;
    }

    /** a column with only plain ground in its top few blocks (no planks, path, chest or crop): it can be turned */
    private static boolean plainGround(ServerLevel level, int wx, int wz, int top, BlockPos.MutableBlockPos m) {
        for (int k = 0; k < 4; k++) {
            int y = top - k;
            if (y <= level.getMinBuildHeight()) break;
            BlockState s = level.getBlockState(m.set(wx, y, wz));
            if (s.isAir() || !s.getFluidState().isEmpty() || natural(s) || isPalette(s) || leftAlone(s)) continue;
            return false;
        }
        return true;
    }

    /** the first Hollows' little glass shards stood on the ground: they go before the ground is turned again */
    private static void stripOldShard(ServerLevel level, long seed, int wx, int wz, BlockPos.MutableBlockPos m) {
        long h = BellPlan.hash(seed, wx, wz, 29);
        if (Math.floorMod(h, 400) == 0 || Math.floorMod(h, 200) != 1) return;
        int n = 1 + (int) Math.floorMod(h >> 8, 3);
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
        for (int k = 0; k < n; k++, y--) {
            BlockState s = level.getBlockState(m.set(wx, y, wz));
            if (!(s.is(Blocks.LIME_STAINED_GLASS) || s.is(Blocks.VERDANT_FROGLIGHT))) break;
            level.setBlock(m, Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    /** under water only the bed is turned: the water stays where it is */
    private static void paintBed(ServerLevel level, BellPlan p, int wx, int wz, int top, BlockPos.MutableBlockPos m) {
        int y = top;
        while (y > top - 12 && y > level.getMinBuildHeight() + 1 && !level.getBlockState(m.set(wx, y, wz)).getFluidState().isEmpty()) y--;
        for (int k = 0; k < 2; k++, y--) {
            BlockState s = level.getBlockState(m.set(wx, y, wz));
            if (!natural(s) || !s.getFluidState().isEmpty()) break;
            level.setBlock(m, state(p.strata(wx, y, wz)), FLAGS);
        }
    }

    /** one column: cut down or built up to its new height, its ground turned, and what stands on it set */
    private static void shape(ServerLevel level, BellPlan.Out o, int i, int wx, int wz, int y0, BlockPos.MutableBlockPos m, List<BlockPos> water) {
        int top = o.top[i];
        if (top < y0) {
            for (int y = y0; y > top; y--) level.setBlock(m.set(wx, y, wz), Blocks.AIR.defaultBlockState(), FLAGS);
        }
        for (int k = 0; k < o.layers[i]; k++) {
            int y = top - k;
            BellPlan.Mat mat = o.layer[i][k];
            BlockState s = level.getBlockState(m.set(wx, y, wz));
            if (y > y0) {
                // built up: every block from the old ground to the new top is filled, nothing hollow under it
                if (!(s.isAir() || loose(s))) continue;
                level.setBlock(m, state(mat == BellPlan.Mat.KEEP ? BellPlan.Mat.CALCITE : mat), FLAGS);
                continue;
            }
            if (mat == BellPlan.Mat.KEEP || !s.getFluidState().isEmpty()) continue;
            if (!(natural(s) || isPalette(s))) continue;
            level.setBlock(m, state(mat), FLAGS);
        }
        for (int k = 0; k < o.an[i]; k++) {
            int y = o.ay[i][k];
            if (y >= level.getMaxBuildHeight()) continue;
            BlockState s = level.getBlockState(m.set(wx, y, wz));
            if (!s.isAir()) continue;                         // never into anything already there
            BellPlan.Mat mat = o.am[i][k];
            level.setBlock(m, state(mat), FLAGS);
            if (mat == BellPlan.Mat.WATER) water.add(m.immutable());
        }
    }

    /**
     * A pool is only water where it has a floor and walls. The plan keeps each pool inside one chunk, so this can
     * be checked here; any water that would run off (air beside or under it) becomes calcite instead.
     */
    private static void keepWaterIn(ServerLevel level, List<BlockPos> water, int x0, int z0, BlockPos.MutableBlockPos m) {
        for (int pass = 0; pass < 4 && !water.isEmpty(); pass++) {
            boolean changed = false;
            for (var it = water.iterator(); it.hasNext(); ) {
                BlockPos w = it.next();
                boolean leaks = false;
                for (var d : new net.minecraft.core.Direction[]{net.minecraft.core.Direction.DOWN, net.minecraft.core.Direction.NORTH,
                        net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST}) {
                    BlockPos n = w.relative(d);
                    if (n.getX() < x0 || n.getX() > x0 + 15 || n.getZ() < z0 || n.getZ() > z0 + 15) { leaks = true; break; }
                    BlockState s = level.getBlockState(n);
                    if (s.getFluidState().isEmpty() && !s.isFaceSturdy(level, n, d.getOpposite())) { leaks = true; break; }
                }
                if (leaks) {
                    level.setBlock(w, Blocks.CALCITE.defaultBlockState(), FLAGS);
                    it.remove();
                    changed = true;
                }
            }
            if (!changed) break;
        }
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

    /** the ground under his ground says his name: every quart of every section whose column is his */
    private static void fillBiome(ServerLevel level, BellPlan p, LevelChunk chunk) {
        Holder<Biome> home;
        try {
            home = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(BELL_HOLLOWS);
        } catch (Exception e) {
            HollowbellMod.LOG.error("The bell_hollows biome is missing from the data pack: {}", e.toString());
            return;
        }
        var sampler = level.getChunkSource().randomState().sampler();
        chunk.fillBiomesFromNoise((qx, qy, qz, s) ->
                p.painted(QuartPos.toBlock(qx) + 2, QuartPos.toBlock(qz) + 2) ? home : chunk.getNoiseBiome(qx, qy, qz), sampler);
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
        plan = null;
        planLevel = null;
    }
}
