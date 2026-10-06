package net.jj.hollowbell.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.command.GiantsCommand;
import net.jj.hollowbell.world.BellPlan;
import net.jj.hollowbell.world.HomeGround;
import net.jj.hollowbell.world.Painter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import static net.jj.hollowbell.test.HollowbellGameTests.drop;
import static net.jj.hollowbell.test.HollowbellGameTests.player;

/**
 * /giants paint [depth] (1.9.3): a painted column is his ground's own layers from its new top down to the depth asked
 * (5 unless told), and the block under that is left as it was. The test world is flat and only three blocks deep,
 * so these paint on a deep land of stone made for the test (the plan is told the land's top is there).
 */
public class PaintDepthTests implements FabricGameTest {
    /** the top of the test's deep land */
    private static final int LAND = -30;

    /** a spot far from the other tests, like HollowbellGameTests.spawnAway's */
    private static BlockPos farSpot(GameTestHelper h, int slot) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        return new BlockPos(o.getX() + 20000 + slot * 400, 0, o.getZ() + 5000);
    }

    private static CommandSourceStack op(GameTestHelper h, Vec3 at) {
        return h.getLevel().getServer().createCommandSourceStack().withPosition(at).withLevel(h.getLevel()).withPermission(4).withSuppressedOutput();
    }

    /**
     * Paints a circle of the deep land at this slot (depth null: the painter's own default) and checks every column on
     * the land as it was (not raised over it, not under water): from its new top down, `depth` blocks are exactly his
     * layers (the ones under the first four his own rock), and the next one is the land's stone as it was.
     */
    private static void paintsDeep(GameTestHelper h, int slot, Integer depth) {
        ServerLevel lv = h.getLevel();
        BlockPos at = farSpot(h, slot);
        int cx = at.getX(), cz = at.getZ(), R = 14;
        for (int x = (cx - R - 32) >> 4; x <= (cx + R + 32) >> 4; x++) for (int z = (cz - R - 32) >> 4; z <= (cz + R + 32) >> 4; z++) lv.getChunk(x, z);
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int x = cx - R - 2; x <= cx + R + 2; x++) for (int z = cz - R - 2; z <= cz + R + 2; z++)
            for (int y = lv.getMinBuildHeight() + 1; y <= LAND; y++) lv.setBlock(new BlockPos(x, y, z), stone, 2);
        int d = depth == null ? Painter.DEPTH : depth;
        BellPlan plan = new BellPlan(BellPlan.mix(lv.getSeed(), slot), cx, cz, (int) Math.ceil(R * 3.2), lv.getSeaLevel(),
                lv.getMinBuildHeight(), lv.getMaxBuildHeight(), (x, z) -> LAND);
        plan.withDen = false;
        plan.paintDepth = d;
        java.util.Set<ChunkPos> chunks = new java.util.LinkedHashSet<>();
        for (int x = cx - R; x <= cx + R; x++) for (int z = cz - R; z <= cz + R; z++) chunks.add(new ChunkPos(x >> 4, z >> 4));
        int checked = 0, bad = 0, badBelow = 0, deepOnes = 0;
        StringBuilder first = new StringBuilder();
        for (ChunkPos cp : chunks) {
            HomeGround.lastOut = null;
            HomeGround.paintInCircle(lv, lv.getChunk(cp.x, cp.z), plan, cx, cz, R);
            BellPlan.Out o = HomeGround.lastOut;
            int[] y0 = HomeGround.lastY0;
            if (o == null) continue;
            for (int i = 0; i < 256; i++) {
                int x = cp.getMinBlockX() + (i & 15), z = cp.getMinBlockZ() + (i >> 4);
                if ((x - cx) * (x - cx) + (z - cz) * (z - cz) > (R - 1) * (R - 1)) continue;
                if (!o.paint[i] || o.laid[i] == 0 || y0[i] != LAND) continue;
                int top = o.top[i];
                // (land raised over the old ground is filled right through with his ground; the depth counts down
                // from the old ground there)
                int bottom = Math.min(top, y0[i]) - (d - 1);
                checked++;
                boolean ok = true;
                for (int k = 0; top - k >= bottom; k++) {
                    int y = top - k;
                    BellPlan.Mat m = k == 0 ? o.layer[i][0] : plan.layer(x, y, z, k, o.topMat(i), y0[i]);
                    BlockState want = m == BellPlan.Mat.KEEP ? (y > y0[i] ? HomeGround.state(BellPlan.Mat.CALCITE) : stone) : HomeGround.state(m);
                    BlockState got = lv.getBlockState(new BlockPos(x, y, z));
                    if (!got.equals(want)) {
                        ok = false;
                        if (first.length() < 400) first.append(" (").append(x).append(",").append(y).append(",").append(z).append(" k").append(k)
                                .append(": ").append(got.getBlock().getName().getString()).append(" not ").append(want.getBlock().getName().getString()).append(")");
                    }
                    if (k >= BellPlan.MADE_DEPTH && y <= y0[i] && m == plan.strata(x, y, z)) deepOnes++;
                }
                if (!ok) bad++;
                BlockState below = lv.getBlockState(new BlockPos(x, bottom - 1, z));
                if (!below.is(Blocks.STONE)) {
                    badBelow++;
                    if (first.length() < 400) first.append(" [under ").append(x).append(",").append(bottom - 1).append(",").append(z).append(": ")
                            .append(below.getBlock().getName().getString()).append("]");
                }
            }
        }
        HollowbellMod.LOG.info("paint depth {}: {} columns checked, {} not his layers, {} with the block under touched, {} deep blocks{}", d, checked, bad, badBelow, deepOnes, first);
        h.assertTrue(checked >= 150, "too few columns to check: " + checked);
        h.assertTrue(bad <= checked * 0.03, bad + " of " + checked + " columns aren't his layers " + d + " deep:" + first);
        h.assertTrue(badBelow <= checked * 0.01, badBelow + " of " + checked + " columns had the block under the depth changed:" + first);
        if (d > BellPlan.MADE_DEPTH) h.assertTrue(deepOnes >= checked * (d - BellPlan.MADE_DEPTH) * 0.9, "the layers under the first four aren't his own rock: " + deepOnes);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "paint_depth_default")
    public void paintLaysHisLayersFiveDeepByDefault(GameTestHelper h) {
        h.assertTrue(Painter.DEPTH == 5 && GiantsCommand.PAINT_DEPTH == 5, "the default depth isn't 5");
        paintsDeep(h, 600, null);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "paint_depth_ten")
    public void paintLaysHisLayersTenDeepWhenAsked(GameTestHelper h) {
        paintsDeep(h, 605, 10);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "paint_depth_cmd")
    public void giantsPaintTakesADepthAndNeverGoesThroughBedrock(GameTestHelper h) throws Exception {
        ServerLevel lv = h.getLevel();
        var server = lv.getServer();
        var d = server.getCommands().getDispatcher();
        Vec3 here = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1)));
        for (String ok : new String[]{"giants paint hollowbell", "giants paint hollowbell 40", "giants paint hollowbell 40 full", "giants paint hollowbell 40 full 10",
                "giants paint hollowbell 40 full 1", "giants paint hollowbell 40 full 64", "giants paint hollowbell 40 biome"}) {
            var pr = d.parse(ok, op(h, here));
            h.assertTrue(!pr.getReader().canRead() && pr.getExceptions().isEmpty(), "/" + ok + " doesn't parse");
        }
        for (String no : new String[]{"giants paint hollowbell 40 full 0", "giants paint hollowbell 40 full 65"}) {
            var pr = d.parse(no, op(h, here));
            h.assertTrue(pr.getReader().canRead() || !pr.getExceptions().isEmpty(), "/" + no + " shouldn't be taken");
        }
        var tips = d.getCompletionSuggestions(d.parse("giants paint hollowbell 40 full ", op(h, here))).get();
        h.assertTrue(tips.getList().stream().anyMatch(s -> s.getText().equals("10")), "no depths are offered after full: " + tips.getList());

        BlockPos at = farSpot(h, 610);
        int cx = at.getX(), cz = at.getZ();
        for (int x = (cx - 48) >> 4; x <= (cx + 48) >> 4; x++) for (int z = (cz - 48) >> 4; z <= (cz + 48) >> 4; z++) lv.getChunk(x, z);
        ServerPlayer p = player(h, new Vec3(cx + 0.5, -40, cz + 0.5));
        String b = "net.jj.hollowbell.GiantsBridge";
        // the depth goes last; an older /giants sends none (5 then); a bare number after the player is taken too
        var said = GiantsCommand.ask(server, "paint", "16 full " + p.getUUID() + " depth=10", b);
        h.assertTrue(said.size() == 1 && said.get(0).contains("10 blocks deep"), "depth=10 wasn't heard: " + said);
        Painter.forget();
        said = GiantsCommand.ask(server, "paint", "16 full " + p.getUUID(), b);
        h.assertTrue(said.size() == 1 && said.get(0).contains("5 blocks deep"), "with no depth it should be 5: " + said);
        Painter.forget();
        said = GiantsCommand.ask(server, "paint", "16 full " + p.getUUID() + " 7", b);
        h.assertTrue(said.size() == 1 && said.get(0).contains("7 blocks deep"), "a bare depth wasn't heard: " + said);
        Painter.forget();
        said = GiantsCommand.ask(server, "paint", "16 full " + p.getUUID() + " depth=500", b);
        h.assertTrue(said.size() == 1 && said.get(0).contains("64 blocks deep"), "a depth over 64 should be 64: " + said);
        Painter.forget();
        said = GiantsCommand.ask(server, "paint", "16 biome " + p.getUUID() + " depth=10", b);
        h.assertTrue(said.size() == 1 && !said.get(0).contains("deep"), "biome only takes no depth: " + said);
        Painter.forget();

        // the test world is three blocks deep: 64 down stops at the bedrock, and never goes through it
        GiantsCommand.ask(server, "paint", "16 full " + p.getUUID() + " depth=64", b);
        for (int k = 0; k < 400 && Painter.busy(); k++) Painter.tick(server);
        h.assertTrue(!Painter.busy(), "the painting never finished");
        int floor = lv.getMinBuildHeight(), cols = 0, his = 0;
        for (int x = cx - 14; x <= cx + 14; x++) for (int z = cz - 14; z <= cz + 14; z++) {
            if ((x - cx) * (x - cx) + (z - cz) * (z - cz) > 14 * 14) continue;
            h.assertTrue(lv.getBlockState(new BlockPos(x, floor, z)).is(Blocks.BEDROCK), "the bedrock at " + x + ", " + z + " was painted over");
            cols++;
            BlockState over = lv.getBlockState(new BlockPos(x, floor + 1, z));
            // the flat world's dirt right on the bedrock is his ground now
            if (!over.is(Blocks.DIRT) && !over.is(Blocks.GRASS_BLOCK) && !over.isAir() && over.getFluidState().isEmpty()) his++;
        }
        HollowbellMod.LOG.info("paint depth 64 on the flat world: {} of {} columns are his right down to the bedrock", his, cols);
        h.assertTrue(his >= cols * 0.9, "only " + his + " of " + cols + " columns are his ground right down to the bedrock");
        drop(p);
        h.succeed();
    }
}
