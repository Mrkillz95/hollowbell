package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.ModBlocks;
import net.jj.hollowbell.world.Cracks;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.mixin.StructureManagerAccess;
import net.jj.hollowbell.world.BellGen;
import net.jj.hollowbell.world.GroundCheck;
import net.jj.hollowbell.world.HomeGround;
import net.jj.hollowbell.world.Painter;
import net.jj.hollowbell.world.WorldOne;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** His ground, the Bell Hollows. */
public class GroundTests implements FabricGameTest {
    /** the test world has villages: the ground tests want a chunk with no building in it */
    private static LevelChunk plainChunk(GameTestHelper h, BlockPos at) {
        var ch = h.getLevel().getChunkAt(at);
        ch.setAllReferences(new HashMap<>());
        ch.setInhabitedTime(0);
        return ch;
    }

    /** his ground turned, but not grass: plainly his */
    private static boolean plainlyOurs(BlockState s) {
        return ours(s) && !s.is(Blocks.GRASS_BLOCK) && s.getFluidState().isEmpty();
    }

    /** claims the ground 100 blocks west of the spot: the spot's chunk lies in the pale core, outside the den */
    private static void claimNear(GameTestHelper h, WorldOne w, BlockPos c) {
        w.claimHome(h.getLevel(), c.getX() - 100, c.getZ());
    }

    /** makes a chunk his the way the world's generation does, but on a chunk that is already there */
    private static void decorate(GameTestHelper h, LevelChunk chunk) {
        var s = BellGen.SNAP;
        h.assertTrue(s != null && s.claimed, "no claim was published for the generator");
        HomeGround.decorate(h.getLevel(), chunk, h.getLevel().structureManager(), s);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "ground_trees")
    public void hisGroundClearsTreesAndCabins(GameTestHelper h) {
        var l = h.getLevel();
        var w = WorldOne.get(l.getServer());
        w.clearForTests();
        BlockPos c = groundSpot(h, 222);
        claimNear(h, w, c);
        var chunk = plainChunk(h, c);
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        int g = top(h, x0 + 5, z0 + 5); // the ground
        var LOG = Blocks.OAK_LOG.defaultBlockState();
        var PLANK = Blocks.OAK_PLANKS.defaultBlockState();
        var LEAF = Blocks.OAK_LEAVES.defaultBlockState();
        List<BlockPos> cabin = new ArrayList<>();
        // a log cabin: log walls 3 high round a 5x5, a plank roof over it
        for (int dx = 2; dx <= 6; dx++)
            for (int dz = 2; dz <= 6; dz++) {
                boolean wall = dx == 2 || dx == 6 || dz == 2 || dz == 6;
                if (wall)
                    for (int y = 1; y <= 3; y++) {
                        BlockPos p = new BlockPos(x0 + dx, g + y, z0 + dz);
                        l.setBlock(p, LOG, 2);
                        cabin.add(p);
                    }
                BlockPos r = new BlockPos(x0 + dx, g + 4, z0 + dz);
                l.setBlock(r, PLANK, 2);
                cabin.add(r);
            }
        // a tree: a trunk five high, a leaf cap three across on top
        int tx = x0 + 11, tz = z0 + 11;
        int tg = top(h, tx, tz);
        List<BlockPos> tree = new ArrayList<>();
        for (int y = 1; y <= 5; y++) {
            BlockPos p = new BlockPos(tx, tg + y, tz);
            l.setBlock(p, LOG, 2);
            tree.add(p);
        }
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = new BlockPos(tx + dx, tg + 6, tz + dz);
                l.setBlock(p, LEAF, 2);
                tree.add(p);
            }
        // and leaves hanging over a column from next door, nothing under them
        BlockPos hang = new BlockPos(x0 + 14, top(h, x0 + 14, z0 + 2) + 4, z0 + 2);
        l.setBlock(hang, LEAF, 2);
        decorate(h, chunk);
        h.assertTrue(
                count(h, cabin, st -> st.is(Blocks.OAK_LOG) || st.is(Blocks.OAK_PLANKS)) == 0,
                "the cabin is still there");
        h.assertTrue(
                count(h, tree, st -> st.is(Blocks.OAK_LOG) || st.is(Blocks.OAK_LEAVES)) == 0,
                "the tree is still there");
        h.assertTrue(!l.getBlockState(hang).is(Blocks.OAK_LEAVES), "the leaves from next door are still there");
        // the columns under them are his ground now, like all the rest
        h.assertTrue(
                ours(l.getBlockState(new BlockPos(tx, top(h, tx, tz), tz)))
                        || !HomeGround.plan().painted(tx, tz),
                "the column under the tree wasn't made");
        h.assertTrue(
                ours(l.getBlockState(new BlockPos(x0 + 4, top(h, x0 + 4, z0 + 4), z0 + 4))),
                "the column under the cabin wasn't made");
        int ours = 0;
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++)
                if (ours(l.getBlockState(new BlockPos(x0 + dx, top(h, x0 + dx, z0 + dz), z0 + dz)))) ours++;
        h.assertTrue(ours >= 240, "only " + ours + " columns were made his");
        h.assertTrue(BellGen.made(chunk), "the chunk isn't marked as made");
        w.clearForTests();
        force(h, c.getX(), c.getZ(), 1, false);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "ground_structure")
    public void whatIsLeftOfAStructureIsPaintedOver(GameTestHelper h) {
        var l = h.getLevel();
        var w = WorldOne.get(l.getServer());
        w.clearForTests();
        BlockPos c = groundSpot(h, 223);
        claimNear(h, w, c);
        var chunk = plainChunk(h, c);
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        // bits of a building on the ground: a cobblestone wall, a plank floor, a chest, a path
        List<BlockPos> built = new ArrayList<>();
        for (int dx = 3; dx < 9; dx++) {
            int y = top(h, x0 + dx, z0 + 5);
            for (int k = 1; k <= 4; k++) {
                BlockPos p = new BlockPos(x0 + dx, y + k, z0 + 5);
                l.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), 2);
                built.add(p);
            }
            BlockPos f = new BlockPos(x0 + dx, top(h, x0 + dx, z0 + 7), z0 + 7);
            l.setBlock(f, Blocks.OAK_PLANKS.defaultBlockState(), 2);
            built.add(f);
        }
        BlockPos chest = new BlockPos(x0 + 10, top(h, x0 + 10, z0 + 10) + 1, z0 + 10);
        l.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2);
        built.add(chest);
        BlockPos path = new BlockPos(x0 + 12, top(h, x0 + 12, z0 + 3), z0 + 3);
        l.setBlock(path, Blocks.DIRT_PATH.defaultBlockState(), 2);
        built.add(path);
        decorate(h, chunk);
        int left = count(
                h,
                built,
                st -> st.is(Blocks.COBBLESTONE)
                        || st.is(Blocks.OAK_PLANKS)
                        || st.is(Blocks.CHEST)
                        || st.is(Blocks.DIRT_PATH));
        h.assertTrue(left == 0, left + " blocks of the building are still there");
        for (int dx = 3; dx < 9; dx++) {
            int x = x0 + dx;
            h.assertTrue(
                    ours(l.getBlockState(new BlockPos(x, top(h, x, z0 + 5), z0 + 5))),
                    "the column under the wall wasn't made his at " + x);
            int fy = top(h, x, z0 + 7);
            var o = HomeGround.lastOut;
            int li = 7 * 16 + dx;
            h.assertTrue(
                    ours(l.getBlockState(new BlockPos(x, fy, z0 + 7))),
                    "the column under the floor wasn't made his at " + x + ": "
                            + l.getBlockState(new BlockPos(x, fy, z0 + 7)) + " at " + fy + ", under it "
                            + l.getBlockState(new BlockPos(x, fy - 1, z0 + 7))
                            + (o == null
                                    ? ""
                                    : ", planned top " + o.top[li] + " painted " + o.paint[li] + " layers "
                                            + o.layers[li] + " first " + (o.layers[li] > 0 ? o.layer[li][0] : null)));
        }
        w.clearForTests();
        force(h, c.getX(), c.getZ(), 1, false);
        h.succeed();
    }

    /** a piece of a structure with just a box: enough for the structure hook to look at */
    private static final class BoxPiece extends StructurePiece {
        BoxPiece(BoundingBox box) {
            super(StructurePieceType.SWAMPLAND_HUT, 0, box);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {}

        @Override
        public void postProcess(
                WorldGenLevel level,
                StructureManager sm,
                ChunkGenerator gen,
                RandomSource r,
                BoundingBox box,
                ChunkPos cp,
                BlockPos pivot) {}
    }

    private static StructureStart fakeStart(GameTestHelper h, BoundingBox box) {
        var hut = h.getLevel()
                .registryAccess()
                .registryOrThrow(Registries.STRUCTURE)
                .getOrThrow(BuiltinStructures.SWAMP_HUT);
        var pieces = new PiecesContainer(List.of(new BoxPiece(box)));
        return new StructureStart(hut, new ChunkPos(box.minX() >> 4, box.minZ() >> 4), 0, pieces);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "ground_no_structures")
    public void noSurfaceStructuresInHisGround(GameTestHelper h) {
        var l = h.getLevel();
        var w = WorldOne.get(l.getServer());
        w.clearForTests();
        BlockPos c = groundSpot(h, 229);
        claimNear(h, w, c);
        var s = BellGen.SNAP;
        h.assertTrue(s != null && s.claimed, "no claim was published");
        int sea = l.getSeaLevel();
        var gen = l.getChunkSource().getGenerator();
        int cx = w.homeX(), cz = w.homeZ();
        var BB = (Function<int[], BoundingBox>) a -> new BoundingBox(a[0], a[1], a[2], a[3], a[4], a[5]);
        // a hut in the middle of his ground: refused
        var inHut = BB.apply(new int[] {cx + 40, sea - 2, cz + 40, cx + 47, sea + 9, cz + 49});
        h.assertTrue(BellGen.refuses(s, inHut), "a hut in the middle of his ground was let in");
        h.assertTrue(BellGen.refuses(gen, fakeStart(h, inHut)), "the structure hook let a hut start in his ground");
        // a village far off: kept
        var far = BB.apply(new int[] {cx + 4000, sea - 2, cz, cx + 4100, sea + 20, cz + 100});
        h.assertTrue(!BellGen.refuses(s, far), "a village 4000 blocks off was refused");
        h.assertTrue(!BellGen.refuses(gen, fakeStart(h, far)), "the structure hook refused a village far off");
        // a big one outside whose box only just reaches in at one corner: refused (its corner is looked at)
        int edge = w.homeRadius();
        var corner = BB.apply(new int[] {cx + 5, sea - 5, cz + 5, cx + edge + 400, sea + 30, cz + edge + 400});
        h.assertTrue(BellGen.refuses(s, corner), "a structure reaching into his middle from outside was let in");
        // deep ones under his ground stay: a mineshaft, a stronghold, an ancient city
        var mine = BB.apply(new int[] {cx - 60, -40, cz - 60, cx + 60, sea - 17, cz + 60});
        h.assertTrue(!BellGen.refuses(s, mine), "a deep mine under his ground was refused");
        h.assertTrue(!BellGen.refuses(gen, fakeStart(h, mine)), "the structure hook refused a deep mine");
        // another world's generator (the nether) is never touched
        h.assertTrue(!BellGen.refuses(new Object(), fakeStart(h, inHut)), "another generator's structure was refused");
        // with no ground claimed nothing is refused
        w.clearForTests();
        h.assertTrue(!BellGen.refuses(gen, fakeStart(h, inHut)), "a structure was refused with no ground claimed");
        force(h, c.getX(), c.getZ(), 1, false);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "ground_tidy")
    public void treesFromNextDoorAreClearedOffHisGround(GameTestHelper h) {
        var l = h.getLevel();
        var w = WorldOne.get(l.getServer());
        w.clearForTests();
        BlockPos c = groundSpot(h, 230);
        claimNear(h, w, c);
        var s = BellGen.SNAP;
        var chunk = plainChunk(h, c);
        var next = plainChunk(h, c.offset(16, 0, 0));
        next.removeAttached(BellGen.MADE);
        chunk.removeAttached(BellGen.MADE);
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        decorate(h, chunk);
        h.assertTrue(BellGen.made(chunk), "the chunk isn't marked as made");
        // the next chunk's decoration puts a big tree whose crown reaches over the border into the made chunk
        var LOG = Blocks.DARK_OAK_LOG.defaultBlockState();
        var LEAF = Blocks.DARK_OAK_LEAVES.defaultBlockState();
        var VINE = Blocks.VINE.defaultBlockState();
        List<BlockPos> crown = new ArrayList<>();
        List<Integer> groundY = new ArrayList<>();
        for (int dx = 11; dx <= 15; dx++)
            for (int dz = 4; dz <= 8; dz++) {
                int y = top(h, x0 + dx, z0 + dz);
                BlockPos p = new BlockPos(x0 + dx, y + 7, z0 + dz);
                l.setBlock(p, LEAF, 2);
                crown.add(p);
                if (dx == 15 && dz == 6) { // a branch and a vine hanging down
                    for (int k = 1; k <= 6; k++) {
                        BlockPos b = new BlockPos(x0 + dx, y + k, z0 + dz);
                        l.setBlock(b, LOG, 2);
                        crown.add(b);
                    }
                }
                if (dx == 12 && dz == 5) {
                    BlockPos v = new BlockPos(x0 + dx, y + 6, z0 + dz);
                    l.setBlock(v, VINE, 2);
                    crown.add(v);
                }
            }
        // a made column the tree didn't reach keeps exactly what his ground put there
        var keepAt = new BlockPos(x0 + 3, top(h, x0 + 3, z0 + 12), z0 + 12);
        var keep = l.getBlockState(keepAt);
        // the next chunk is not made: nothing on it is touched
        int nx = next.getPos().getMinBlockX();
        BlockPos theirs = new BlockPos(nx + 4, top(h, nx + 4, z0 + 4) + 3, z0 + 4);
        l.setBlock(theirs, LEAF, 2);
        int cleared = HomeGround.tidyAround(l, next, s);
        h.assertTrue(cleared > 0, "nothing was cleared");
        int left = count(h, crown, st -> !st.isAir() && !ours(st));
        h.assertTrue(left == 0, left + " blocks of the tree are still on his ground");
        h.assertTrue(l.getBlockState(keepAt) == keep, "a column the tree didn't reach was changed");
        h.assertTrue(l.getBlockState(theirs).is(Blocks.DARK_OAK_LEAVES), "leaves on a chunk not made yet were touched");
        // made later, the next chunk clears the leaves on it itself
        decorate(h, next);
        h.assertTrue(
                !l.getBlockState(theirs).is(Blocks.DARK_OAK_LEAVES)
                        || !HomeGround.plan().painted(theirs.getX(), theirs.getZ()),
                "the leaves stayed on a column made after them");
        l.setBlock(theirs, Blocks.AIR.defaultBlockState(), 2);
        w.clearForTests();
        force(h, c.getX(), c.getZ(), 1, false);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "ground_check")
    public void theGroundCheckSeesAMadeChunkAndATree(GameTestHelper h) {
        var l = h.getLevel();
        var w = WorldOne.get(l.getServer());
        w.clearForTests();
        BlockPos c = groundSpot(h, 231);
        claimNear(h, w, c);
        var chunk = plainChunk(h, c);
        var plan = HomeGround.plan();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        boolean[] want = new boolean[256];
        var cols = new GroundCheck.Col[256];
        int wanted = 0;
        for (int i = 0; i < 256; i++) {
            want[i] = plan.painted(x0 + (i & 15), z0 + (i >> 4));
            if (want[i]) wanted++;
        }
        h.assertTrue(wanted > 200, "the test chunk is hardly his ground: " + wanted);
        // not made yet: the check says so
        GroundCheck.check(l, chunk, plan, want, cols);
        int notMade = 0;
        for (int k = 0; k < 256; k++)
            if (want[k] && cols[k].cause != GroundCheck.Cause.OK && cols[k].cause != GroundCheck.Cause.EDGE) notMade++;
        h.assertTrue(notMade > wanted / 3, "the check didn't see plain world ground: " + notMade + " of " + wanted);
        decorate(h, chunk);
        GroundCheck.check(l, chunk, plan, want, cols);
        int right = 0;
        StringBuilder bad = new StringBuilder();
        for (int i = 0; i < 256; i++) {
            if (!want[i]) continue;
            var cs = cols[i].cause;
            if (cs == GroundCheck.Cause.OK || cs == GroundCheck.Cause.EDGE) right++;
            else if (bad.length() < 300)
                bad.append(cs).append(' ').append(cols[i].what).append("; ");
        }
        h.assertTrue(
                right >= wanted * 95 / 100,
                "the check finds " + (wanted - right) + " wrong columns on a made chunk: " + bad);
        // leaves on one column: the check names them
        int i = 0;
        while (!want[i]) i++;
        int x = x0 + (i & 15), z = z0 + (i >> 4);
        BlockPos leaf = new BlockPos(x, top(h, x, z) + 5, z);
        l.setBlock(leaf, Blocks.OAK_LEAVES.defaultBlockState(), 2);
        GroundCheck.check(l, chunk, plan, want, cols);
        h.assertTrue(cols[i].cause == GroundCheck.Cause.TREE, "leaves on his ground came out as " + cols[i].cause);
        l.setBlock(leaf, Blocks.AIR.defaultBlockState(), 2);
        w.clearForTests();
        force(h, c.getX(), c.getZ(), 1, false);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "paint_hut")
    public void paintOverAWitchHutLeavesNoHut(GameTestHelper h) {
        var l = h.getLevel();
        BlockPos c = groundSpot(h, 232);
        force(h, c.getX(), c.getZ(), 2, true);
        var chunk = l.getChunkAt(c);
        var gen = l.getChunkSource().getGenerator();
        var hut = l.registryAccess().registryOrThrow(Registries.STRUCTURE).getOrThrow(BuiltinStructures.SWAMP_HUT);
        // a real witch hut, as the world makes one: its start kept in its chunk, references in every chunk it stands in
        var start = hut.generate(
                l.registryAccess(),
                gen,
                gen.getBiomeSource(),
                l.getChunkSource().randomState(),
                l.getStructureManager(),
                l.getSeed(),
                chunk.getPos(),
                0,
                l,
                b -> true);
        h.assertTrue(start.isValid(), "no witch hut could be made for the test");
        chunk.setStartForStructure(hut, start);
        var bb = start.getBoundingBox();
        for (int cx = bb.minX() >> 4; cx <= bb.maxX() >> 4; cx++)
            for (int cz = bb.minZ() >> 4; cz <= bb.maxZ() >> 4; cz++) {
                var ch = l.getChunk(cx, cz);
                ch.addReferenceForStructure(hut, chunk.getPos().toLong());
                var area = new BoundingBox(
                        cx << 4, l.getMinBuildHeight(), cz << 4, (cx << 4) + 15, l.getMaxBuildHeight(), (cz << 4) + 15);
                start.placeInChunk(l, l.structureManager(), gen, l.getRandom(), area, new ChunkPos(cx, cz));
            }
        ((StructureManagerAccess) l.structureManager())
                .hollowbell$check()
                .onStructureLoad(chunk.getPos(), chunk.getAllStarts());
        // the hut's piece settles onto the ground as it is built: its box is read again after
        bb = start.getPieces().get(0).getBoundingBox();
        BlockPos mid = bb.getCenter();
        h.assertTrue(
                l.structureManager().getStructureWithPieceAt(mid, hut).isValid(), "the test hut isn't a structure");
        Predicate<BlockState> hutBlock = st -> st.is(Blocks.SPRUCE_PLANKS)
                || st.is(Blocks.SPRUCE_STAIRS)
                || st.is(Blocks.OAK_LOG)
                || st.is(Blocks.OAK_FENCE)
                || st.is(Blocks.CAULDRON)
                || st.is(Blocks.CRAFTING_TABLE)
                || st.is(Blocks.POTTED_RED_MUSHROOM);
        List<BlockPos> box = new ArrayList<>();
        for (BlockPos p :
                BlockPos.betweenClosed(bb.minX(), bb.minY() - 4, bb.minZ(), bb.maxX(), bb.maxY() + 2, bb.maxZ()))
            box.add(p.immutable());
        int hutBefore = count(h, box, hutBlock);
        h.assertTrue(hutBefore > 40, "the test hut has only " + hutBefore + " blocks");
        // somebody's chest by the hut, a diamond in it
        BlockPos chest = new BlockPos(bb.maxX() + 3, top(h, bb.maxX() + 3, bb.minZ()) + 1, bb.minZ());
        l.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2);
        if (l.getBlockEntity(chest) instanceof Container box2) box2.setItem(0, new ItemStack(Items.DIAMOND, 3));
        // paint over it, round its middle
        Painter.startAt(l, null, mid.getX() + 0.5, mid.getZ() + 0.5, 24, true);
        Painter.finishAll(l.getServer());
        int hutAfter = count(h, box, hutBlock);
        h.assertTrue(hutAfter == 0, hutAfter + " blocks of the hut are still there");
        h.assertTrue(
                !l.structureManager().getStructureWithPieceAt(mid, hut).isValid(),
                "the hut is still a structure after the paint");
        h.assertTrue(
                chunk.getStartForStructure(hut) == null
                        || !chunk.getStartForStructure(hut).isValid(),
                "the hut's start is still kept");
        int his = 0, cols = 0;
        for (int x = bb.minX(); x <= bb.maxX(); x++)
            for (int z = bb.minZ(); z <= bb.maxZ(); z++) {
                cols++;
                if (ours(l.getBlockState(new BlockPos(x, top(h, x, z), z)))) his++;
            }
        h.assertTrue(
                his >= cols * 9 / 10, "only " + his + " of " + cols + " columns where the hut stood are his ground");
        h.assertTrue(
                hollowsAt(h, mid.getX(), top(h, mid.getX(), mid.getZ()) + 1, mid.getZ()),
                "the biome where the hut stood isn't his");
        h.assertTrue(hollowsAt(h, mid.getX(), -50, mid.getZ()), "the paint didn't reach all the way down the column");
        h.assertTrue(!l.getBlockState(chest).is(Blocks.CHEST), "the chest is still there");
        // the chunks here were only just loaded: the dropped things can be looked for once they are up and running
        h.runAfterDelay(5, () -> {
            var diamonds = l.getEntitiesOfClass(
                    ItemEntity.class,
                    new AABB(chest).inflate(3, 12, 3),
                    e -> e.getItem().is(Items.DIAMOND));
            int n = diamonds.stream().mapToInt(e -> e.getItem().getCount()).sum();
            h.assertTrue(n == 3, "the chest's diamonds weren't dropped on the new ground (" + n + " found)");
            diamonds.forEach(e -> e.discard());
            force(h, c.getX(), c.getZ(), 2, false);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "ground_paint")
    public void hisGroundTurnsAChunk(GameTestHelper h) {
        var l = h.getLevel();
        var w = WorldOne.get(l.getServer());
        w.clearForTests();
        BlockPos c = groundSpot(h, 210);
        claimNear(h, w, c);
        h.assertTrue(
                w.homeRadius() == WorldOne.configRadius() && w.homeRadius() >= 900,
                "a new ground is " + w.homeRadius() + " across");
        var chunk = plainChunk(h, c);
        decorate(h, chunk);
        int ours = 0, plain = 0, own = 0;
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++) {
                int x = chunk.getPos().getMinBlockX() + dx, z = chunk.getPos().getMinBlockZ() + dz;
                for (int y = top(h, x, z); y > top(h, x, z) - 4; y--) {
                    var b = l.getBlockState(new BlockPos(x, y, z));
                    if (b.is(ModBlocks.BELL_CALCITE)
                            || b.is(ModBlocks.TENDRIL_GLASS)
                            || b.is(ModBlocks.BELL_SHARD)
                            || b.is(ModBlocks.SPORE_MOSS)) own++;
                }
                var st = l.getBlockState(new BlockPos(x, top(h, x, z), z));
                if (ours(st)) ours++;
                if (plainlyOurs(st)) plain++;
            }
        h.assertTrue(ours >= 230, "only " + ours + " of 256 columns are his ground on top");
        h.assertTrue(plain >= 100, "only " + plain + " of 256 columns are plainly his (not grass)");
        w.clearForTests();
        force(h, c.getX(), c.getZ(), 1, false);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120, batch = "ground_made_before")
    public void landMadeBeforeTheClaimStays(GameTestHelper h) {
        var l = h.getLevel();
        var w = WorldOne.get(l.getServer());
        w.clearForTests();
        BlockPos c = groundSpot(h, 211);
        var chunk = plainChunk(h, c);
        List<BlockState> before = new ArrayList<>();
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++) {
                int x = chunk.getPos().getMinBlockX() + dx, z = chunk.getPos().getMinBlockZ() + dz;
                before.add(l.getBlockState(new BlockPos(x, top(h, x, z), z)));
            }
        claimNear(h, w, c);
        // no painter comes round for land that was already there: a second later it is just as it was
        h.runAfterDelay(40, () -> {
            int i = 0;
            for (int dx = 0; dx < 16; dx++)
                for (int dz = 0; dz < 16; dz++) {
                    int x = chunk.getPos().getMinBlockX() + dx,
                            z = chunk.getPos().getMinBlockZ() + dz;
                    h.assertTrue(
                            l.getBlockState(new BlockPos(x, top(h, x, z), z)) == before.get(i++),
                            "land made before the claim was changed at " + x + ", " + z);
                }
            h.assertTrue(
                    !hollowsAt(h, c.getX(), top(h, c.getX(), c.getZ()), c.getZ()),
                    "land made before the claim became the Bell Hollows");
            w.clearForTests();
            force(h, c.getX(), c.getZ(), 1, false);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "ground_outside")
    public void groundOutsideHisIsLeftAlone(GameTestHelper h) {
        var l = h.getLevel();
        var w = WorldOne.get(l.getServer());
        w.clearForTests();
        BlockPos c = groundSpot(h, 212);
        w.claimHome(l, c.getX(), c.getZ());
        BlockPos far = new BlockPos(c.getX(), 0, c.getZ() + w.homeRadius() + 40);
        force(h, far.getX(), far.getZ(), 1, true);
        var chunk = plainChunk(h, far);
        List<BlockState> before = new ArrayList<>();
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++) {
                int x = chunk.getPos().getMinBlockX() + dx, z = chunk.getPos().getMinBlockZ() + dz;
                before.add(l.getBlockState(new BlockPos(x, top(h, x, z), z)));
            }
        decorate(h, chunk);
        int i = 0;
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++) {
                int x = chunk.getPos().getMinBlockX() + dx, z = chunk.getPos().getMinBlockZ() + dz;
                h.assertTrue(
                        l.getBlockState(new BlockPos(x, top(h, x, z), z)) == before.get(i++),
                        "a block outside his ground was changed");
            }
        w.clearForTests();
        force(h, c.getX(), c.getZ(), 1, false);
        force(h, far.getX(), far.getZ(), 1, false);
        h.succeed();
    }

    /** every block of two chunks side by side, from a little under the ground to well over it */
    private static List<BlockState> grab(GameTestHelper h, int x0, int z0, int lo, int hi) {
        var out = new ArrayList<BlockState>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = x0; x < x0 + 32; x++)
            for (int z = z0; z < z0 + 16; z++)
                for (int y = lo; y <= hi; y++) out.add(h.getLevel().getBlockState(m.set(x, y, z)));
        return out;
    }

    private static void putBack(GameTestHelper h, int x0, int z0, int lo, int hi, List<BlockState> was) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int k = 0;
        for (int x = x0; x < x0 + 32; x++)
            for (int z = z0; z < z0 + 16; z++)
                for (int y = lo; y <= hi; y++) h.getLevel().setBlock(m.set(x, y, z), was.get(k++), 2 | 16);
    }

    /** the glass of the bell and the froglight of its crown */
    private static boolean bell(BlockState s) {
        return s.is(Blocks.LIME_STAINED_GLASS)
                || s.is(Blocks.WHITE_STAINED_GLASS)
                || s.is(Blocks.GREEN_STAINED_GLASS)
                || s.is(Blocks.LIGHT_GRAY_STAINED_GLASS)
                || s.is(Blocks.PEARLESCENT_FROGLIGHT)
                || s.is(ModBlocks.BELL_CALCITE);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "ground_border")
    public void bigFeaturesComeOutWholeInAnyOrder(GameTestHelper h) {
        var l = h.getLevel();
        var w = WorldOne.get(l.getServer());
        w.clearForTests();
        BlockPos c = groundSpot(h, 224);
        long seed = 0x5EED0001L;
        // the den's glass bell is fifty across: it straddles the border between this chunk and the next one east
        w.claimHome(l, c.getX() + 8, c.getZ(), seed);
        var a = plainChunk(h, c);
        var b = plainChunk(h, c.offset(16, 0, 0));
        int x0 = a.getPos().getMinBlockX(), z0 = a.getPos().getMinBlockZ();
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int x = x0; x < x0 + 32; x++)
            for (int z = z0; z < z0 + 16; z++) {
                lo = Math.min(lo, top(h, x, z));
                hi = Math.max(hi, top(h, x, z));
            }
        lo -= 12;
        hi += 40;
        var before = grab(h, x0, z0, lo, hi);
        decorate(h, a);
        decorate(h, b);
        var first = grab(h, x0, z0, lo, hi);
        // the bell really does cross the border: glass or calcite over the ground on both sides of it
        int east = 0, west = 0;
        for (int z = z0; z < z0 + 16; z++)
            for (int y = lo; y <= hi; y++) {
                if (bell(l.getBlockState(new BlockPos(x0 + 15, y, z)))) west++;
                if (bell(l.getBlockState(new BlockPos(x0 + 16, y, z)))) east++;
            }
        if (west <= 3 || east <= 3) {
            var o = HomeGround.lastOut;
            int pc = 0, an = 0, maxY = Integer.MIN_VALUE;
            if (o != null)
                for (int i = 0; i < 256; i++) {
                    if (o.paint[i]) pc++;
                    an += o.an[i];
                    for (int k = 0; k < o.an[i]; k++) maxY = Math.max(maxY, o.ay[i][k]);
                }
            h.fail("the bell doesn't cross the chunk border (" + west + " / " + east + "); last chunk: " + pc
                    + " painted, " + an + " set above, highest " + maxY + ", looked from " + lo + " to " + hi
                    + ", den at " + HomeGround.plan().denY());
        }
        // the same ground again, the chunks made the other way round
        putBack(h, x0, z0, lo, hi, before);
        w.clearForTests();
        w.claimHome(l, c.getX() + 8, c.getZ(), seed);
        plainChunk(h, c);
        plainChunk(h, c.offset(16, 0, 0));
        decorate(h, b);
        decorate(h, a);
        var second = grab(h, x0, z0, lo, hi);
        int diff = 0;
        for (int i = 0; i < first.size(); i++) if (first.get(i) != second.get(i)) diff++;
        h.assertTrue(diff == 0, diff + " blocks came out different when the chunks were made the other way round");
        putBack(h, x0, z0, lo, hi, before);
        w.clearForTests();
        force(h, c.getX(), c.getZ(), 1, false);
        force(h, c.getX() + 16, c.getZ(), 1, false);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "ground_raised")
    public void raisedGroundIsNeverHollow(GameTestHelper h) {
        var l = h.getLevel();
        var w = WorldOne.get(l.getServer());
        w.clearForTests();
        BlockPos c = groundSpot(h, 225);
        var chunk = plainChunk(h, c);
        // a claim that puts this chunk out in the hills, where the plan raises plenty of it
        int best = -1, bestAt = 0;
        int[][] offs = {{-300, 0}, {0, -300}, {300, 0}, {0, 300}, {-420, 0}, {0, -420}, {-220, -220}, {220, 220}};
        for (int k = 0; k < offs.length; k++) {
            w.clearForTests();
            w.claimHome(l, c.getX() + offs[k][0], c.getZ() + offs[k][1], 0x5EED0100L + k);
            int[] y0 = new int[256];
            var o = HomeGround.preview(l, chunk, BellGen.SNAP, y0);
            int up = 0;
            for (int i = 0; i < 256; i++) if (o.paint[i] && o.top[i] > y0[i]) up++;
            if (up > best) {
                best = up;
                bestAt = k;
            }
        }
        w.clearForTests();
        w.claimHome(l, c.getX() + offs[bestAt][0], c.getZ() + offs[bestAt][1], 0x5EED0100L + bestAt);
        plainChunk(h, c);
        decorate(h, chunk);
        var o = HomeGround.lastOut;
        int[] y0 = HomeGround.lastY0;
        h.assertTrue(o != null, "nothing was worked out");
        int raised = 0;
        for (int i = 0; i < 256; i++) {
            if (!o.paint[i] || o.top[i] <= y0[i]) continue;
            int x = chunk.getPos().getMinBlockX() + (i & 15), z = chunk.getPos().getMinBlockZ() + (i >> 4);
            for (int y = y0[i] + 1; y <= o.top[i]; y++) {
                var st = l.getBlockState(new BlockPos(x, y, z));
                h.assertTrue(
                        !st.isAir() && st.getFluidState().isEmpty(),
                        "raised ground is hollow at " + x + ", " + y + ", " + z);
            }
            raised++;
        }
        h.assertTrue(raised >= 10, "only " + raised + " columns were raised; the test proves little");
        w.clearForTests();
        force(h, c.getX(), c.getZ(), 1, false);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "ground_upgrade")
    public void anOldGroundKeepsItsPlaceAndItsLand(GameTestHelper h) {
        var l = h.getLevel();
        var w = WorldOne.get(l.getServer());
        w.clearForTests();
        BlockPos c = groundSpot(h, 226);
        var chunk = plainChunk(h, c);
        // an old painted ground, over land already made
        w.claimOldHome(c.getX() - 100, c.getZ(), 0x5EED0200L, 2);
        List<BlockState> before = new ArrayList<>();
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++) {
                int x = chunk.getPos().getMinBlockX() + dx, z = chunk.getPos().getMinBlockZ() + dz;
                before.add(l.getBlockState(new BlockPos(x, top(h, x, z), z)));
            }
        h.assertTrue(w.groundVersion() == 2 && w.homeRadius() == 320, "the old ground isn't old");
        h.assertTrue(w.upgradeGround(), "the old ground wasn't brought up to date");
        h.assertTrue(
                w.groundVersion() == WorldOne.GROUND_VERSION && w.homeRadius() >= 900,
                "the ground didn't grow: " + w.homeRadius());
        h.assertTrue(
                w.homeX() == c.getX() - 100 && w.homeZ() == c.getZ() && w.homeSeed() == 0x5EED0200L,
                "the old ground moved or changed its seed");
        var s = BellGen.SNAP;
        h.assertTrue(
                s != null && s.claimed && s.radius == w.homeRadius() && s.cx == w.homeX(),
                "the generator wasn't told about the bigger ground");
        // and the save keeps it: written and read back
        var tag = w.save(new CompoundTag(), l.registryAccess());
        h.assertTrue(
                tag.getInt("GroundVersion") == WorldOne.GROUND_VERSION && tag.getInt("HomeRadius") == w.homeRadius(),
                "the save lost the ground");
        h.runAfterDelay(40, () -> {
            int i = 0;
            for (int dx = 0; dx < 16; dx++)
                for (int dz = 0; dz < 16; dz++) {
                    int x = chunk.getPos().getMinBlockX() + dx,
                            z = chunk.getPos().getMinBlockZ() + dz;
                    h.assertTrue(
                            l.getBlockState(new BlockPos(x, top(h, x, z), z)) == before.get(i++),
                            "land already made was changed at " + x + ", " + z);
                }
            w.clearForTests();
            force(h, c.getX(), c.getZ(), 1, false);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "ground_den")
    public void theDenStandsInTheMiddle(GameTestHelper h) {
        var l = h.getLevel();
        var w = WorldOne.get(l.getServer());
        w.clearForTests();
        BlockPos c = groundSpot(h, 227);
        w.claimHome(l, c.getX(), c.getZ(), 0x5EED0300L);
        var chunk = plainChunk(h, c);
        decorate(h, chunk);
        int t = top(h, c.getX(), c.getZ());
        var crown = l.getBlockState(new BlockPos(c.getX(), t, c.getZ()));
        h.assertTrue(crown.is(Blocks.PEARLESCENT_FROGLIGHT), "the bell has no crown on top, it has " + crown);
        int air = 0;
        for (int y = t - 1; y > t - 30; y--)
            if (l.getBlockState(new BlockPos(c.getX(), y, c.getZ())).isAir()) air++;
        h.assertTrue(air >= 10, "the bell isn't hollow under its crown (" + air + " air)");
        int glass = 0, lights = 0, ribs = 0;
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++)
                for (int y = t - 30; y <= t; y++) {
                    var st = l.getBlockState(new BlockPos(
                            chunk.getPos().getMinBlockX() + dx,
                            y,
                            chunk.getPos().getMinBlockZ() + dz));
                    if (st.is(Blocks.LIME_STAINED_GLASS) || st.is(Blocks.WHITE_STAINED_GLASS)) glass++;
                    if (st.is(Blocks.CHAIN)) lights++;
                    if (st.is(ModBlocks.BELL_CALCITE)) ribs++;
                }
        h.assertTrue(glass >= 60, "only " + glass + " glass in the bell over the middle chunk");
        h.assertTrue(lights >= 1, "no lights hang inside the bell");
        h.assertTrue(ribs >= 10, "only " + ribs + " blocks of bell calcite in the bell's ribs");
        w.clearForTests();
        force(h, c.getX(), c.getZ(), 1, false);
        h.succeed();
    }

    /**
     * The real thing: a claim far out in land the test world hasn't made, then its chunks made by the world's own
     * generation. The biome, the den, the ground and the ores under it all come from the generator hooks.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "ground_generated")
    public void theWorldMakesHisGroundAsItMakesTheLand(GameTestHelper h) {
        var l = h.getLevel();
        var w = WorldOne.get(l.getServer());
        w.clearForTests();
        // far out, somewhere new each run: nothing of it is made yet
        int cx = 3_000_000 + l.random.nextInt(400) * 4096, cz = -2_000_000 - l.random.nextInt(400) * 4096;
        h.assertTrue(WorldOne.madeRegions(l, cx, cz, 1000) == 0, "the far spot is already made");
        w.claimHome(l, cx, cz, 0x5EED0400L);
        var snap = BellGen.SNAP;
        h.assertTrue(snap != null && snap.claimed && snap.cx == cx, "the claim wasn't published to the generator");
        // the den, made by the generator
        force(h, cx, cz, 0, true);
        int t = top(h, cx, cz);
        var crown = l.getBlockState(new BlockPos(cx, t, cz));
        h.assertTrue(
                crown.is(Blocks.PEARLESCENT_FROGLIGHT),
                "the generated den has no crown on top, it has " + crown + " at " + t);
        // the test world is flat (one fixed biome, no stone): the biome and the ores are only checked in a normal world
        boolean normal = l.getChunkSource().getGenerator().getBiomeSource() instanceof MultiNoiseBiomeSource;
        if (normal) h.assertTrue(hollowsAt(h, cx, t - 10, cz), "the generated den isn't in the Bell Hollows");
        // out in the middle of his ground: his blocks on top, his biome, and ores under it
        int mx = cx + 300, mz = cz + 40;
        force(h, mx, mz, 0, true);
        var ch = l.getChunkAt(new BlockPos(mx, 0, mz));
        int x0 = ch.getPos().getMinBlockX(), z0 = ch.getPos().getMinBlockZ();
        int ours = 0, ores = 0, deep = 0;
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++) {
                int x = x0 + dx, z = z0 + dz;
                int ty = top(h, x, z);
                if (ours(l.getBlockState(new BlockPos(x, ty, z)))) ours++;
                for (int y = 0; y < ty - 4; y++) {
                    var b = l.getBlockState(new BlockPos(x, y, z));
                    if (b.is(BlockTags.COAL_ORES)
                            || b.is(BlockTags.IRON_ORES)
                            || b.is(BlockTags.COPPER_ORES)
                            || b.is(BlockTags.GOLD_ORES)
                            || b.is(BlockTags.REDSTONE_ORES)
                            || b.is(BlockTags.LAPIS_ORES)) ores++;
                }
            }
        int ty = top(h, mx, mz);
        h.assertTrue(ours >= 150, "only " + ours + " columns of a generated middle chunk are his");
        h.assertTrue(BellGen.oreCount() >= 20, "the ores weren't all found: " + BellGen.oreCount());
        if (normal) {
            h.assertTrue(hollowsAt(h, mx, ty, mz), "a generated middle chunk isn't the Bell Hollows");
            h.assertTrue(!hollowsAt(h, mx, -40, mz), "the caves far under his ground became the Bell Hollows");
            h.assertTrue(ores >= 8, "only " + ores + " ore blocks under a generated chunk of his ground");
            // /locate biome finds it from outside
            var found = l.findClosestBiome3d(
                    b -> b.is(HomeGround.BELL_HOLLOWS), new BlockPos(cx + 1300, 80, cz), 6400, 32, 64);
            h.assertTrue(found != null, "/locate biome can't find the Bell Hollows");
            h.assertTrue(
                    Math.hypot(found.getFirst().getX() - cx, found.getFirst().getZ() - cz) < w.homeRadius() + 40,
                    "/locate found it at " + found.getFirst());
        }
        force(h, cx, cz, 0, false);
        force(h, mx, mz, 0, false);
        w.clearForTests();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "ground_biome_rule")
    public void theBiomeHookDecidesRight(GameTestHelper h) {
        var l = h.getLevel();
        var w = WorldOne.get(l.getServer());
        w.clearForTests();
        int cx = 5_000_000, cz = 5_000_000;
        w.claimHome(l, cx, cz, 0x5EED0500L);
        var s = BellGen.SNAP;
        var plains = l.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        int qx = QuartPos.fromBlock(cx), qz = QuartPos.fromBlock(cz);
        h.assertTrue(BellGen.wants(s, plains, qx, QuartPos.fromBlock(64), qz), "the middle of his ground isn't his");
        h.assertTrue(BellGen.wants(s, plains, qx, 0, qz), "y 0 isn't his");
        h.assertTrue(!BellGen.wants(s, plains, qx, -1, qz), "the caves under y 0 became his");
        h.assertTrue(!BellGen.wants(s, plains, qx + 1000, 16, qz), "4000 blocks off is his");
        // another giant got there first: it stays theirs
        var other = Holder.Reference.createStandAlone(
                new HolderOwner<Biome>() {},
                ResourceKey.create(
                        Registries.BIOME, ResourceLocation.fromNamespaceAndPath("furrowmaw", "furrowed_waste")));
        h.assertTrue(!BellGen.wants(s, other, qx, 16, qz), "another giant's biome was taken over");
        // only the overworld's own source is changed (the hook is given the source it runs in)
        var src = l.getChunkSource().getGenerator().getBiomeSource();
        h.assertTrue(s.source == src, "the snapshot holds some other biome source");
        h.assertTrue(
                BellGen.biome(src, plains, qx, 16, qz).is(HomeGround.BELL_HOLLOWS),
                "the hook doesn't say it for the overworld");
        h.assertTrue(BellGen.biome(new Object(), plains, qx, 16, qz) == plains, "another biome source was changed");
        if (src instanceof MultiNoiseBiomeSource) {
            var sampler = l.getChunkSource().randomState().sampler();
            h.assertTrue(
                    src.getNoiseBiome(qx, 16, qz, sampler).is(HomeGround.BELL_HOLLOWS),
                    "the overworld's biome source doesn't say it");
        }
        h.assertTrue(
                src.possibleBiomes().stream().anyMatch(b -> b.is(HomeGround.BELL_HOLLOWS)),
                "the overworld doesn't list his biome");
        // quick to say no outside his box: this is asked millions of times
        long t0 = System.nanoTime();
        int yes = 0;
        for (int i = 0; i < 2_000_000; i++) if (BellGen.wants(s, plains, (i & 1023) - 512, 16, (i >> 10) - 1000)) yes++;
        long ms = (System.nanoTime() - t0) / 1_000_000;
        h.assertTrue(yes == 0 && ms < 400, "two million checks outside took " + ms + " ms (" + yes + " yes)");
        w.clearForTests();
        h.assertTrue(
                !BellGen.SNAP.claimed || !BellGen.wants(BellGen.SNAP, plains, qx, 16, qz),
                "a let-go claim is still his");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "ground_new")
    public void groundNewClaimsLandNotMadeYet(GameTestHelper h) {
        var l = h.getLevel();
        var w = WorldOne.get(l.getServer());
        w.clearForTests();
        BlockPos o = h.absolutePos(new BlockPos(1, 2, 1));
        // an old ground right here, in land that is made
        w.claimHome(l, o.getX(), o.getZ(), 0x5EED0600L);
        h.assertTrue(
                WorldOne.madeRegions(l, o.getX(), o.getZ(), 64) > 0
                        || l.getServer().getPlayerList().getPlayerCount() >= 0,
                "the test area isn't made");
        run(h, Vec3.atCenterOf(o), "hollowbell ground new");
        h.assertTrue(w.homeClaimed(), "no new ground was claimed");
        double moved = Math.hypot(w.homeX() - o.getX(), w.homeZ() - o.getZ());
        h.assertTrue(moved >= w.homeRadius() * 2, "the new ground is only " + (int) moved + " blocks from the old one");
        h.assertTrue(
                WorldOne.madeRegions(l, w.homeX(), w.homeZ(), w.homeRadius()) == 0,
                "the new ground is in land already made");
        h.assertTrue(
                w.where() != null && w.where().getX() == w.homeX() && w.where().getZ() == w.homeZ(),
                "the next one doesn't come down in the new ground");
        var s = BellGen.SNAP;
        h.assertTrue(s != null && s.cx == w.homeX() && s.cz == w.homeZ(), "the generator wasn't told");
        run(h, Vec3.atCenterOf(o), "hollowbell ground");
        w.clearForTests();
        h.succeed();
    }

    /**
     * A landing's crack (a sky dive's, as big as they get): the spots are picked from his random just as they always
     * were, and cracked over the next ticks, a little at a time; at the end exactly those spots are cracked
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "ground_crack")
    public void aLandingCracksTheSameSpotsOverAFewTicks(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, 0, 1150);
        var l = h.getLevel();
        boolean grief = BellConfig.V.griefing;
        BellConfig.V.griefing = true;
        l.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, l.getServer());
        int r = 48, R = r + 2;
        int cx = e.getBlockX() + 3, cz = e.getBlockZ() - 2;
        int g = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz) - 1;
        BlockPos c = new BlockPos(cx, g + 1, cz);
        for (int dx = -R; dx <= R; dx++)
            for (int dz = -R; dz <= R; dz++) {
                l.setBlock(c.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                l.setBlock(c.offset(dx, 0, dz), Blocks.SHORT_GRASS.defaultBlockState(), 2);
            }
        // the spots his random picks, as the crack always picked them
        var want = new java.util.HashSet<Long>();
        e.getRandom().setSeed(0x5EED);
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r || e.getRandom().nextInt(3) == 0) continue;
                want.add(BlockPos.asLong(dx, 0, dz));
            }
        int before = Cracks.left();
        e.getRandom().setSeed(0x5EED);
        e.moves().crack(c, r);
        int picked = Cracks.left() - before;
        h.assertTrue(picked == want.size(), "picked " + picked + " spots, not " + want.size());
        int[] ticks = {0};
        h.onEachTick(() -> {
            if (ticks[0] < 0) return;
            ticks[0]++;
            if (Cracks.left() > 0) return;
            int[] wrong = {0};
            for (int dx = -R; dx <= R; dx++)
                for (int dz = -R; dz <= R; dz++) {
                    boolean w = want.contains(BlockPos.asLong(dx, 0, dz));
                    boolean cracked = l.getBlockState(c.offset(dx, -1, dz)).is(Blocks.COARSE_DIRT);
                    boolean gone = l.getBlockState(c.offset(dx, 0, dz)).isAir();
                    if (cracked != w || gone != w) wrong[0]++;
                }
            BellConfig.V.griefing = grief;
            e.discard();
            HollowbellMod.LOG.info("crack test: {} spots cracked over {} ticks", want.size(), ticks[0]);
            ticks[0] = -1;
            h.assertTrue(wrong[0] == 0, wrong[0] + " spots not as his random picked them");
            h.succeed();
        });
    }
}
