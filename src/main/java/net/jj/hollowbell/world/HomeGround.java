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
 * The rules: every column of his ground is made. Whatever stands on it (a tree, leaves from next door, a plant,
 * anything left of a building) is cleared first, from the very top of the column; no surface structure is ever
 * started in his ground (see BellGen.refuses). A chunk's own columns are made in its own pass; afterwards the made
 * chunks next to it are tidied of anything its decoration put on them. Water is only put where it has a floor and
 * walls. Land made before the claim is never touched.
 */
public final class HomeGround {
    private HomeGround() {}

    public static final ResourceKey<Biome> BELL_HOLLOWS =
            ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "bell_hollows"));

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** any block his ground is made of, on it or in it */
    public static boolean isOurs(BlockState s) {
        return oursBlocks().contains(s.getBlock());
    }

    private static volatile java.util.Set<Block> OURS;

    private static java.util.Set<Block> oursBlocks() {
        java.util.Set<Block> o = OURS;
        if (o != null) return o;
        java.util.Set<Block> n = new java.util.HashSet<>();
        for (BellPlan.Mat m : BellPlan.Mat.values()) {
            BlockState b = state(m);
            if (b != null) n.add(b.getBlock());
        }
        OURS = o = java.util.Set.copyOf(n);
        return o;
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
     * from the world's generation (a WorldGenRegion) and by the tests (a ServerLevel on a real chunk). Whatever stands
     * on a column (a tree from next door, leaves, a plant, anything left of a building) is cleared first: every
     * column of his ground is made, none is skipped.
     */
    public static void decorate(WorldGenLevel level, ChunkAccess chunk, StructureManager structures, BellGen.Snap s) {
        decorate(level, chunk, s);
    }

    public static void decorate(WorldGenLevel level, ChunkAccess chunk, BellGen.Snap s) {
        if (!s.claimed || !s.touches(chunk.getPos())) return;
        BellPlan p = planFor(s);
        if (!p.near(chunk.getPos().x, chunk.getPos().z)) return;
        make(level, chunk, p, false, s, null);
    }

    /** which columns a paint may touch */
    public interface Clip { boolean in(int x, int z); }

    /**
     * An admin's paint (/giants paint): this chunk made his ground by the given plan, everything in it included:
     * structures, places people live and anything built. Only bedrock, water and blocks holding things (chests,
     * furnaces...) are left; the ground is dressed round them.
     */
    public static void paint(WorldGenLevel level, ChunkAccess chunk, BellPlan p) {
        make(level, chunk, p, true, null, null);
    }

    /** an admin's paint, only inside the circle */
    public static void paintInCircle(WorldGenLevel level, ChunkAccess chunk, BellPlan p, double cx, double cz, int r) {
        double r2 = (double) r * r;
        make(level, chunk, p, true, null, (x, z) -> (x + 0.5 - cx) * (x + 0.5 - cx) + (z + 0.5 - cz) * (z + 0.5 - cz) <= r2);
    }

    private static void make(WorldGenLevel level, ChunkAccess chunk, BellPlan p, boolean admin,
                             @org.jetbrains.annotations.Nullable BellGen.Snap s, @org.jetbrains.annotations.Nullable Clip clip) {
        ChunkPos cp = chunk.getPos();
        long t0 = System.nanoTime();
        Survey sv = survey(level, chunk, p, admin, clip);
        if (!sv.any) return;
        if (s != null) BellGen.ores(level, chunk, s);
        BellPlan.Out o = new BellPlan.Out();
        p.chunk(cp.x, cp.z, sv.y0, sv.ok, sv.wet, sv.lowest, o);
        int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        List<BlockPos> water = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            if (!o.paint[i]) continue;
            int wx = x0 + (i & 15), wz = z0 + (i >> 4);
            if (sv.wet[i]) { paintBed(level, p, wx, wz, sv.y0[i], m); continue; }
            if (sv.real[i] < sv.y0[i]) fillUnder(p, o, i, wx, wz, sv.real[i]);
            shape(level, o, i, wx, wz, sv.real[i], m, water, admin);
            if (!admin) closeUnder(level, p, o.top[i], wx, wz, m);
        }
        keepWaterIn(level, water, x0, z0);
        // what was in a chest or the like that stood here comes out on top of the new ground: nothing is lost
        if (sv.drops != null && level instanceof net.minecraft.server.level.ServerLevel sl) {
            for (int i = 0; i < 256; i++) {
                if (sv.drops[i] == null) continue;
                int wx = x0 + (i & 15), wz = z0 + (i >> 4);
                int y = Math.max(o.paint[i] ? o.top[i] : sv.y0[i], sv.y0[i]) + 1;
                for (var st : sv.drops[i]) {
                    var e = new net.minecraft.world.entity.item.ItemEntity(sl, wx + 0.5, y + 0.2, wz + 0.5, st);
                    e.setDeltaMovement(0, 0.1, 0);
                    boolean in = sl.addFreshEntity(e);
                    if (DEBUG) HollowbellMod.LOG.info("Painted over a container at {}, {}: {} out at y {} ({})", wx, wz, st, y, in);
                }
            }
        }
        if (!admin) BellGen.markMade(chunk);
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
        /** y0: the ground the plan works from; real: where the ground really is (lower where a cave or ravine cut it) */
        final int[] y0 = new int[256], real = new int[256], lowest = new int[256], surface = new int[256];
        final boolean[] ok = new boolean[256], wet = new boolean[256], grass = new boolean[256];
        List<net.minecraft.world.item.ItemStack>[] drops;
        boolean any;
    }

    /** how far down from the top of a column its ground is looked for (the tallest trees are well under this) */
    public static final int DIG = 96;

    /**
     * The block a column's ground starts at: the world's own ground (or his). Everything over it is cleared: trees
     * and leaves (from this chunk or the next), plants, snow cover, and whatever else stands there. Water stops the
     * search: under water only the bed is changed.
     */
    public static boolean isGround(BlockState b) {
        return natural(b) || (leftAlone(b) && !b.is(Blocks.SNOW)) || (isOurs(b) && b.getFluidState().isEmpty() && b.isSolid());
    }

    /** a block holding water that is still cleared (leaves or roots grown into water); water, kelp and seagrass stop */
    private static boolean clearedInWater(BlockState b) {
        return treePart(b) || b.is(Blocks.MANGROVE_ROOTS);
    }

    /** what a cleared block leaves behind: water where it held a water source, else air */
    public static BlockState emptied(BlockState b) {
        return b.getFluidState().isSource() && b.getFluidState().is(net.minecraft.tags.FluidTags.WATER) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    @SuppressWarnings("unchecked")
    private static Survey survey(WorldGenLevel level, ChunkAccess chunk, BellPlan p, boolean admin, @org.jetbrains.annotations.Nullable Clip clip) {
        Survey sv = new Survey();
        ChunkPos cp = chunk.getPos();
        int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ();
        int minY = level.getMinBuildHeight();
        // the live height map: the generation one stops following the blocks once the caves are cut, so it misses
        // trees and leaves the chunks next door put here afterwards (a chunk being decorated has the live one ready)
        Heightmap.Types hm = surfaceMap(chunk);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 256; i++) {
            int lx = i & 15, lz = i >> 4;
            int wx = x0 + lx, wz = z0 + lz;
            if (!p.painted(wx, wz) || (clip != null && !clip.in(wx, wz))) continue;
            sv.any = true;
            int surf = chunk.getHeight(hm, lx, lz);        // the top block
            sv.surface[i] = surf;
            // down to the ground: everything standing on it goes, from the very top of the column
            int top = surf;
            int limit = admin ? minY : Math.max(minY, surf - DIG);
            while (top > limit) {
                BlockState b = level.getBlockState(m.set(wx, top, wz));
                if (b.isAir()) { top--; continue; }
                if (b.is(Blocks.BEDROCK) || isGround(b)) break;
                if (!b.getFluidState().isEmpty() && !clearedInWater(b)) break;
                if (admin && b.hasBlockEntity() && DEBUG) HollowbellMod.LOG.info("Paint found {} at {} holding {}", b, m, level.getBlockEntity(m));
                if (admin && b.hasBlockEntity() && level.getBlockEntity(m) instanceof net.minecraft.world.Container box) {
                    for (int k = 0; k < box.getContainerSize(); k++) {
                        var st = box.getItem(k);
                        if (st.isEmpty()) continue;
                        if (sv.drops == null) sv.drops = new List[256];
                        if (sv.drops[i] == null) sv.drops[i] = new ArrayList<>();
                        sv.drops[i].add(st.copy());
                    }
                    box.clearContent();
                }
                level.setBlock(m, emptied(b), FLAGS);
                top--;
            }
            sv.y0[i] = top;
            sv.real[i] = top;
            sv.lowest[i] = top;
            if (top <= limit) continue;                   // no ground in reach: the column stays as it is
            BlockState s = level.getBlockState(m.set(wx, top, wz));
            if (s.is(Blocks.BEDROCK)) continue;
            sv.wet[i] = !s.getFluidState().isEmpty();
            sv.ok[i] = true;
            if (sv.wet[i] || !changeable(s)) continue;
            sv.grass[i] = s.is(Blocks.GRASS_BLOCK);
            // cut no deeper than solid ground goes: the new top is itself ground, never air or a cave
            int low = top;
            for (int y = top - 1; y >= top - BellPlan.MAX_DOWN - 1 && y > minY + 1; y--) {
                if (!changeable(level.getBlockState(m.set(wx, y, wz)))) break;
                low = y;
            }
            sv.lowest[i] = low;
        }
        // a cave or a ravine the world cut into the land before it was made his: his ground goes over it at the height
        // the land had. Asked only where it may be so (a top that isn't the world's grass, or one well under the
        // columns beside it), as asking the world's generator costs time.
        if (!admin) for (int i = 0; i < 256; i++) {
            if (!sv.ok[i] || sv.wet[i]) continue;
            int lx = i & 15, lz = i >> 4;
            boolean ask = !sv.grass[i];
            for (int d = 0; d < 4 && !ask; d++) {
                int nx = lx + (d == 0 ? -1 : d == 1 ? 1 : 0), nz = lz + (d == 2 ? -1 : d == 3 ? 1 : 0);
                if (nx < 0 || nx > 15 || nz < 0 || nz > 15) continue;
                int j = nz * 16 + nx;
                if (sv.ok[j] && !sv.wet[j] && sv.real[j] - sv.real[i] >= 3) ask = true;
            }
            if (!ask) continue;
            int g = p.worldHeight(x0 + lx, z0 + lz);
            if (g > sv.real[i] + 2 && g > p.sea) sv.y0[i] = g;
        }
        return sv;
    }

    /** for the tests: what this chunk would become, worked out without changing anything but what stands on it */
    public static BellPlan.Out preview(WorldGenLevel level, ChunkAccess chunk, BellGen.Snap s, int[] y0Out) {
        BellPlan p = planFor(s);
        Survey sv = survey(level, chunk, p, false, null);
        BellPlan.Out o = new BellPlan.Out();
        p.chunk(chunk.getPos().x, chunk.getPos().z, sv.y0, sv.ok, sv.wet, sv.lowest, o);
        if (y0Out != null) System.arraycopy(sv.y0, 0, y0Out, 0, 256);
        return o;
    }

    // ------------------------------------------------------------------ tidying next door

    /**
     * After a chunk's decoration, the chunks round it that are already his may have had a tree (or leaves, vines, a
     * bee nest...) put on them by this chunk's own decoration. Those are cleared off his ground again. Only chunks
     * already made are looked at, and only what stands above his ground is touched.
     */
    public static int tidyAround(WorldGenLevel level, ChunkAccess center, BellGen.Snap s) {
        BellPlan p = planFor(s);
        ChunkPos c = center.getPos();
        int cleared = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            int nx = c.x + dx, nz = c.z + dz;
            if (!s.touches(new ChunkPos(nx, nz)) || !p.near(nx, nz) || !level.hasChunk(nx, nz)) continue;
            ChunkAccess n = level.getChunk(nx, nz);
            if (!BellGen.made(n)) continue;
            cleared += tidy(level, n, p);
        }
        return cleared;
    }

    /** the height map that follows every block of this chunk as it is now */
    public static Heightmap.Types surfaceMap(ChunkAccess c) {
        return c instanceof LevelChunk || c.hasPrimedHeightmap(Heightmap.Types.WORLD_SURFACE) ? Heightmap.Types.WORLD_SURFACE : Heightmap.Types.WORLD_SURFACE_WG;
    }

    /**
     * A block a neighbour's ore or stone blob put into the top of his ground: those blobs replace plain stone, and the
     * only plain stone in his ground is diorite, so the top block goes back to diorite.
     */
    private static boolean blobbed(BlockState b) {
        return (b.is(BlockTags.BASE_STONE_OVERWORLD) && !b.is(Blocks.DIORITE)) || b.is(Blocks.GRAVEL) || b.is(Blocks.DIRT)
                || b.is(BlockTags.COAL_ORES) || b.is(BlockTags.IRON_ORES) || b.is(BlockTags.COPPER_ORES) || b.is(BlockTags.GOLD_ORES)
                || b.is(BlockTags.REDSTONE_ORES) || b.is(BlockTags.LAPIS_ORES) || b.is(BlockTags.DIAMOND_ORES) || b.is(BlockTags.EMERALD_ORES);
    }

    /** clears what stands on the columns of his ground in one chunk; returns how many blocks went */
    public static int tidy(WorldGenLevel level, ChunkAccess n, BellPlan p) {
        ChunkPos cp = n.getPos();
        int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ();
        int minY = level.getMinBuildHeight();
        Heightmap.Types hm = surfaceMap(n);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int cleared = 0;
        for (int i = 0; i < 256; i++) {
            int lx = i & 15, lz = i >> 4, wx = x0 + lx, wz = z0 + lz;
            if (!p.painted(wx, wz)) continue;
            int top = n.getHeight(hm, lx, lz);
            int limit = Math.max(minY, top - DIG);
            for (int y = top; y > limit; y--) {
                BlockState b = n.getBlockState(m.set(wx, y, wz));
                if (b.isAir()) continue;
                if (blobbed(b)) { level.setBlock(m, Blocks.DIORITE.defaultBlockState(), FLAGS); cleared++; break; }
                if (!b.getFluidState().isEmpty() && !clearedInWater(b)) {
                    // under water: the bed may have had a blob put into it too
                    int by = y;
                    while (by > limit && !n.getBlockState(m.set(wx, by, wz)).getFluidState().isEmpty()) by--;
                    if (by > limit && blobbed(n.getBlockState(m))) { level.setBlock(m, state(p.strata(wx, by, wz)), FLAGS); cleared++; }
                    break;
                }
                if (b.is(Blocks.BEDROCK)) break;
                if (isGround(b) || isOurs(b)) {
                    // one of his things standing free (a rib, a shard) with air under it: what's under it is looked at too
                    if (isOurs(b) && n.getBlockState(m.set(wx, y - 1, wz)).isAir()) continue;
                    break;
                }
                level.setBlock(m, emptied(b), FLAGS);
                cleared++;
            }
        }
        return cleared;
    }

    /** under water only the bed is changed: the water stays where it is */
    private static void paintBed(WorldGenLevel level, BellPlan p, int wx, int wz, int top, BlockPos.MutableBlockPos m) {
        int y = top;
        while (y > top - DIG && y > level.getMinBuildHeight() + 1 && !level.getBlockState(m.set(wx, y, wz)).getFluidState().isEmpty()) y--;
        // two blocks of bed as the world makes it; a painting lays its own depth
        int bed = p.paintDepth > 0 ? p.paintDepth : 2;
        for (int k = 0; k < bed && y > level.getMinBuildHeight(); k++, y--) {
            BlockState s = level.getBlockState(m.set(wx, y, wz));
            if (!changeable(s)) break;
            level.setBlock(m, state(p.strata(wx, y, wz)), FLAGS);
        }
    }

    /**
     * A cave or an overhang right under his new ground (the world often leaves a thin roof of land over one) is
     * filled, up to 24 blocks down, so his ground is solid where you walk and no plain cave floor shows under it.
     */
    private static void closeUnder(WorldGenLevel level, BellPlan p, int top, int wx, int wz, BlockPos.MutableBlockPos m) {
        boolean inAir = false;
        for (int y = top - 1; y > top - BellPlan.MAXL && y > level.getMinBuildHeight() + 1; y--) {
            BlockState b = level.getBlockState(m.set(wx, y, wz));
            if (!b.getFluidState().isEmpty()) break;
            if (b.isAir() || loose(b)) {
                level.setBlock(m, state(p.strata(wx, y, wz)), FLAGS);
                inAir = true;
            } else if (inAir) break;
        }
    }

    /** over a cut (a cave or ravine): the layers of his ground reach down to where the ground really is, up to 24 deep */
    private static void fillUnder(BellPlan p, BellPlan.Out o, int i, int wx, int wz, int real) {
        int t = o.top[i];
        int n = Math.min(BellPlan.MAXL, t - real + 1);
        for (int k = o.layers[i]; k < n; k++) o.layer[i][k] = p.strata(wx, t - k, wz);
        if (n > o.layers[i]) o.layers[i] = n;
    }

    /** one column: cut down or built up to its new height, its ground changed, and what stands on it set */
    private static void shape(WorldGenLevel level, BellPlan.Out o, int i, int wx, int wz, int y0, BlockPos.MutableBlockPos m, List<BlockPos> water, boolean admin) {
        int top = o.top[i];
        if (top < y0) {
            for (int y = y0; y > top; y--) {
                if (!changeable(level.getBlockState(m.set(wx, y, wz)))) continue;
                level.setBlock(m.set(wx, y, wz), Blocks.AIR.defaultBlockState(), FLAGS);
            }
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
            if (s.is(Blocks.BEDROCK)) break;
            // deeper than the world lays it (a painting's depth): only solid ground is changed, so a cave under it
            // stays open with his ground for its roof, and nothing pours
            if (k >= BellPlan.MADE_DEPTH && k < o.laid[i] && s.isAir()) continue;
            if (!changeable(s)) continue;
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
    public static boolean loose(BlockState s) {
        if (s.isAir() || s.is(BlockTags.LEAVES) || !s.getFluidState().isEmpty()) return false;
        return s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.canBeReplaced();
    }

    /** a tree or a giant mushroom: its column is left whole */
    public static boolean treePart(BlockState s) {
        return s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(Blocks.MUSHROOM_STEM) || s.is(Blocks.RED_MUSHROOM_BLOCK)
                || s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.is(Blocks.BEE_NEST);
    }

    /** what may be changed into his ground: anything solid but bedrock and blocks holding things */
    private static boolean changeable(BlockState s) {
        return !s.is(Blocks.BEDROCK) && !s.hasBlockEntity() && s.getFluidState().isEmpty() && !s.isAir();
    }

    /** the ground blocks the world made: only these are changed into his */
    public static boolean natural(BlockState s) {
        return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL)
                || s.is(Blocks.ROOTED_DIRT) || s.is(Blocks.MYCELIUM)
                || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.SAND) || s.is(Blocks.RED_SAND)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK)
                || s.is(Blocks.ICE) || s.is(Blocks.CLAY) || s.is(Blocks.MUD) || s.is(BlockTags.TERRACOTTA)
                || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.CALCITE);
    }

    /** natural too, but left as it is: bedrock, sandstone under a desert, ores showing at the top */
    public static boolean leftAlone(BlockState s) {
        return s.is(Blocks.BEDROCK) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.PACKED_ICE)
                || s.is(Blocks.BLUE_ICE) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.SNOW)
                || s.is(BlockTags.COAL_ORES) || s.is(BlockTags.IRON_ORES) || s.is(BlockTags.COPPER_ORES)
                || s.is(BlockTags.GOLD_ORES) || s.is(BlockTags.REDSTONE_ORES) || s.is(BlockTags.LAPIS_ORES)
                || s.is(BlockTags.DIAMOND_ORES) || s.is(BlockTags.EMERALD_ORES);
    }
}
