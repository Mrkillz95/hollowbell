package net.jj.hollowbell.world;

import net.jj.hollowbell.HollowbellMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * The Bell Hollows: his own ground, in exactly one place in the world - where he comes down. What it looks like is
 * worked out in {@link BellPlan}; this puts it into a chunk while the world makes that chunk (see {@link BellGen}),
 * right after the vanilla decoration: soft hills scooped with hollows, pale calcite and bone with mint grass, fallen
 * glass shards, spires, ribs, reefs, gardens, pools and lights, the ores under it, and his great glass bell in the
 * middle. The biome itself is the world's own answer there (hollowbell:bell_hollows).
 *
 * The rules: only this chunk's own columns are written; a column a structure reaches into (from outside) is left
 * alone, and so is one with a tree or anything but plain ground at its top; water is only put where it has a floor
 * and walls. Land made before the claim is never touched.
 */
public final class HomeGround {
    private HomeGround() {}

    public static final ResourceKey<Biome> BELL_HOLLOWS =
            ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "bell_hollows"));

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

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
            case BELL_CALCITE -> net.jj.hollowbell.ModBlocks.BELL_CALCITE.defaultBlockState();
            case TENDRIL_GLASS -> net.jj.hollowbell.ModBlocks.TENDRIL_GLASS.defaultBlockState();
            case BELL_SHARD -> net.jj.hollowbell.ModBlocks.BELL_SHARD.defaultBlockState();
            case SPORE_MOSS -> net.jj.hollowbell.ModBlocks.SPORE_MOSS.defaultBlockState();
        };
    }

    // ------------------------------------------------------------------ making a chunk his

    /** for the tests: what the last chunk was asked to become, and where its ground was before */
    public static volatile BellPlan.Out lastOut;
    public static volatile int[] lastY0;

    /** one plan per generator thread (a plan keeps caches), made again when the claim changes */
    private static final class PlanFor { BellGen.Snap snap; BellPlan plan; }
    private static final ThreadLocal<PlanFor> PLANS = ThreadLocal.withInitial(PlanFor::new);

    public static BellPlan planFor(BellGen.Snap s) {
        PlanFor p = PLANS.get();
        if (p.snap != s || p.plan == null) { p.snap = s; p.plan = s.newPlan(); }
        return p.plan;
    }

    /** the plan of the ground claimed now (for the tests and the commands), or null */
    public static BellPlan plan() {
        BellGen.Snap s = BellGen.SNAP;
        return s == null || !s.claimed ? null : planFor(s);
    }

    /**
     * Makes this chunk his: the ores under it, the land's shape, its blocks, and everything standing on it. Called
     * from the world's generation (a WorldGenRegion) and by the tests (a ServerLevel on a real chunk).
     */
    public static void decorate(WorldGenLevel level, ChunkAccess chunk, StructureManager structures, BellGen.Snap s) {
        decorate(level, chunk, s, structureBoxes(level, chunk, structures));
    }

    public static void decorate(WorldGenLevel level, ChunkAccess chunk, BellGen.Snap s, List<BoundingBox> boxes) {
        if (!s.claimed || !s.touches(chunk.getPos())) return;
        BellPlan p = planFor(s);
        ChunkPos cp = chunk.getPos();
        if (!p.near(cp.x, cp.z)) return;
        long t0 = System.nanoTime();
        Survey sv = survey(level, chunk, p, boxes);
        if (!sv.any) return;
        BellGen.ores(level, chunk, s);
        BellPlan.Out o = new BellPlan.Out();
        p.chunk(cp.x, cp.z, sv.y0, sv.ok, sv.wet, sv.lowest, o);
        int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        List<BlockPos> water = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            if (!o.paint[i]) continue;
            int wx = x0 + (i & 15), wz = z0 + (i >> 4);
            if (sv.wet[i]) { paintBed(level, p, wx, wz, sv.y0[i], m); continue; }
            clearLoose(level, wx, sv.y0[i], wz, sv.surface[i], m);
            shape(level, o, i, wx, wz, sv.y0[i], m, water);
        }
        keepWaterIn(level, water, x0, z0);
        if (WorldOne.IN_TESTS && !(level instanceof net.minecraft.server.level.WorldGenRegion)) { lastOut = o; lastY0 = sv.y0; }
        if (DEBUG) HollowbellMod.LOG.info("Made chunk {}, {} of the Bell Hollows in {} ms", cp.x, cp.z,
                String.format("%.1f", (System.nanoTime() - t0) / 1e6));
    }

    private static final boolean DEBUG = Boolean.getBoolean("hollowbell.debug");

    /** the boxes of every structure piece that reaches into this chunk (a village from outside, a buried ruin...) */
    public static List<BoundingBox> structureBoxes(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        List<BoundingBox> out = new ArrayList<>();
        ChunkPos cp = chunk.getPos();
        SectionPos sp = SectionPos.of(cp, level.getMinSection());
        for (var e : chunk.getAllReferences().entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            for (var start : structures.startsForStructure(sp, e.getKey()))
                for (var piece : start.getPieces()) {
                    BoundingBox b = piece.getBoundingBox();
                    if (b.maxX() >= cp.getMinBlockX() && b.minX() <= cp.getMaxBlockX() && b.maxZ() >= cp.getMinBlockZ() && b.minZ() <= cp.getMaxBlockZ())
                        out.add(b);
                }
        }
        return out;
    }

    /**
     * Does a structure reach this column anywhere near its ground (from a little under where it may be cut to well
     * over where things may stand)? A mine far under the ground doesn't count.
     */
    public static boolean inStructure(List<BoundingBox> boxes, int x, int z, int y0) {
        for (BoundingBox b : boxes) {
            if (x < b.minX() || x > b.maxX() || z < b.minZ() || z > b.maxZ()) continue;
            if (b.maxY() < y0 - BellPlan.MAX_DOWN - 4 || b.minY() > y0 + BellPlan.MAX_UP + BellPlan.MAXA) continue;
            return true;
        }
        return false;
    }

    /** a chunk's columns as they stand: where the ground is, whether it may be changed, water, how deep it may be cut */
    private static final class Survey {
        final int[] y0 = new int[256], lowest = new int[256], surface = new int[256];
        final boolean[] ok = new boolean[256], wet = new boolean[256];
        boolean any;
    }

    private static Survey survey(WorldGenLevel level, ChunkAccess chunk, BellPlan p, List<BoundingBox> boxes) {
        Survey sv = new Survey();
        ChunkPos cp = chunk.getPos();
        int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ();
        int minY = level.getMinBuildHeight();
        // a chunk still being made keeps the generation height map; a finished one (the tests) the live one
        Heightmap.Types hm = chunk instanceof LevelChunk ? Heightmap.Types.WORLD_SURFACE : Heightmap.Types.WORLD_SURFACE_WG;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 256; i++) {
            int lx = i & 15, lz = i >> 4;
            int wx = x0 + lx, wz = z0 + lz;
            if (!p.painted(wx, wz)) continue;
            sv.any = true;
            int surf = chunk.getHeight(hm, lx, lz);        // the top block
            sv.surface[i] = surf;
            // down through grass, flowers and snow to the ground; a tree standing here keeps its column
            int top = surf;
            boolean tree = false;
            while (top > minY + 2) {
                BlockState b = level.getBlockState(m.set(wx, top, wz));
                if (b.isAir() || loose(b)) { top--; continue; }
                if (treePart(b)) tree = true;
                break;
            }
            sv.y0[i] = top;
            sv.lowest[i] = top;
            if (tree || top <= minY + 2) continue;
            BlockState s = level.getBlockState(m.set(wx, top, wz));
            sv.wet[i] = !s.getFluidState().isEmpty();
            sv.ok[i] = plainGround(level, wx, wz, top, m) && !inStructure(boxes, wx, wz, top);
            if (!sv.ok[i] || sv.wet[i] || !natural(s)) continue;
            // cut no deeper than the plain ground goes: the new top is itself plain ground, never air or a cave
            int low = top;
            for (int y = top - 1; y >= top - BellPlan.MAX_DOWN - 1 && y > minY + 1; y--) {
                if (!natural(level.getBlockState(m.set(wx, y, wz)))) break;
                low = y;
            }
            sv.lowest[i] = low;
        }
        return sv;
    }

    /** for the tests: what this chunk would become, worked out without changing anything */
    public static BellPlan.Out preview(WorldGenLevel level, ChunkAccess chunk, BellGen.Snap s, int[] y0Out) {
        BellPlan p = planFor(s);
        Survey sv = survey(level, chunk, p, List.of());
        BellPlan.Out o = new BellPlan.Out();
        p.chunk(chunk.getPos().x, chunk.getPos().z, sv.y0, sv.ok, sv.wet, sv.lowest, o);
        if (y0Out != null) System.arraycopy(sv.y0, 0, y0Out, 0, 256);
        return o;
    }

    /** a column with only plain ground in its top few blocks (no planks, path, chest or crop): it can be changed */
    private static boolean plainGround(WorldGenLevel level, int wx, int wz, int top, BlockPos.MutableBlockPos m) {
        for (int k = 0; k < 4; k++) {
            int y = top - k;
            if (y <= level.getMinBuildHeight()) break;
            BlockState s = level.getBlockState(m.set(wx, y, wz));
            if (s.isAir() || !s.getFluidState().isEmpty() || natural(s) || leftAlone(s)) continue;
            return false;
        }
        return true;
    }

    /** under water only the bed is changed: the water stays where it is */
    private static void paintBed(WorldGenLevel level, BellPlan p, int wx, int wz, int top, BlockPos.MutableBlockPos m) {
        int y = top;
        while (y > top - 12 && y > level.getMinBuildHeight() + 1 && !level.getBlockState(m.set(wx, y, wz)).getFluidState().isEmpty()) y--;
        for (int k = 0; k < 2; k++, y--) {
            BlockState s = level.getBlockState(m.set(wx, y, wz));
            if (!natural(s) || !s.getFluidState().isEmpty()) break;
            level.setBlock(m, state(p.strata(wx, y, wz)), FLAGS);
        }
    }

    /** grass, flowers and snow cover over the ground go before the ground is changed */
    private static void clearLoose(WorldGenLevel level, int wx, int y0, int wz, int surface, BlockPos.MutableBlockPos m) {
        for (int y = surface; y > y0; y--) {
            BlockState s = level.getBlockState(m.set(wx, y, wz));
            if (loose(s)) level.setBlock(m, Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    /** one column: cut down or built up to its new height, its ground changed, and what stands on it set */
    private static void shape(WorldGenLevel level, BellPlan.Out o, int i, int wx, int wz, int y0, BlockPos.MutableBlockPos m, List<BlockPos> water) {
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
            if (!natural(s)) continue;
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

    private static final Direction[] AROUND = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    /**
     * A pool is only water where it has a floor and walls. The plan keeps each pool inside one chunk, so this can
     * be checked here; any water that would run off (air beside or under it) becomes calcite instead.
     */
    private static void keepWaterIn(WorldGenLevel level, List<BlockPos> water, int x0, int z0) {
        for (int pass = 0; pass < 4 && !water.isEmpty(); pass++) {
            boolean changed = false;
            for (var it = water.iterator(); it.hasNext(); ) {
                BlockPos w = it.next();
                boolean leaks = false;
                for (Direction d : AROUND) {
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

    /** grass, flowers, snow cover and the like: loose on top of the ground, never somebody's work */
    private static boolean loose(BlockState s) {
        if (s.isAir() || s.is(BlockTags.LEAVES) || !s.getFluidState().isEmpty()) return false;
        return s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.canBeReplaced();
    }

    /** a tree or a giant mushroom: its column is left whole */
    private static boolean treePart(BlockState s) {
        return s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(Blocks.MUSHROOM_STEM) || s.is(Blocks.RED_MUSHROOM_BLOCK)
                || s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.is(Blocks.BEE_NEST);
    }

    /** the ground blocks the world made: only these are changed into his */
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
}
