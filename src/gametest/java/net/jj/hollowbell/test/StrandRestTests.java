package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.rig.BellModel;
import net.jj.hollowbell.rig.BellPieces;
import net.jj.hollowbell.rig.BellRig;
import net.jj.hollowbell.world.NoWait;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * His strand ends and arm tips rest on the real top of the ground: on a slab, a path or snow as much as on a whole
 * block, never sunk into it and never hanging just over it when they lie on it.
 */
public class StrandRestTests implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "rest_tops")
    public void theGroundsTopIsWhereItReallyIs(GameTestHelper h) {
        BlockPos o = h.absolutePos(new BlockPos(1, 1, 1));
        var l = h.getLevel();
        Object[][] cases = {
            {Blocks.STONE.defaultBlockState(), 1.0},
            {Blocks.STONE_SLAB.defaultBlockState(), 0.5},
            {Blocks.DIRT_PATH.defaultBlockState(), 15 / 16.0},
            {Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 4), 3 / 8.0},
            {Blocks.SNOW.defaultBlockState(), 0.0},
            {Blocks.WATER.defaultBlockState(), 8 / 9.0}
        };
        for (int i = 0; i < cases.length; i++) {
            BlockPos p = o.offset(2 * i, 0, 0);
            l.setBlockAndUpdate(p.below(), Blocks.STONE.defaultBlockState());
            l.setBlockAndUpdate(p, (BlockState) cases[i][0]);
            double want = p.getY() + (double) cases[i][1];
            double got = NoWait.realTop(l, p.getX(), p.getY() + 1, p.getZ());
            h.assertTrue(Math.abs(got - want) < 0.01, cases[i][0] + ": top " + got + ", want " + want);
        }
        h.succeed();
    }

    /**
     * Asleep, every strand and arm end that lies on the ground has its lowest block on the ground's top (within a
     * little), and none goes into it.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "rest_soles")
    public void strandEndsLieOnTheGround(GameTestHelper h) {
        float size = 0.5f;
        HollowbellEntity e = spawnAway(h, size, HollowbellEntity.CALM, 760);
        // asleep, the bell settles down and what hangs from it lies out on the ground
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            e.goToSleep();
        });
        // (looked at twice: a strand that has only just touched down, or is lifting off, floats for a moment)
        Set<String> floating = new HashSet<>();
        h.runAfterDelay(340, () -> check(e, floating, null));
        h.runAfterDelay(360, () -> {
            StringBuilder bad = new StringBuilder();
            int resting = check(e, floating, bad);
            HollowbellMod.LOG.info("strand rest: {} resting on the ground;{}", resting, bad);
            h.assertTrue(resting > 5, "only " + resting + " ends lie on the ground");
            h.assertTrue(bad.isEmpty(), "ends off the ground (blocks of his):" + bad);
            release(h, e);
            h.succeed();
        });
    }

    /**
     * Every end near the ground: sunk into it is wrong at once; resting on it but floating over it is wrong if it
     * floated the time before too (kept in floating). Gives how many rest on the ground.
     */
    static int check(HollowbellEntity e, Set<String> floating, @Nullable StringBuilder bad) {
        e.ensurePose();
        BellRig rig = e.rig;
        BellModel m = BellModel.get();
        int resting = 0;
        Set<String> now = new HashSet<>();
        for (int c = 0; c < rig.chains.length; c++) {
            BellRig.Chain ch = rig.chains[c];
            if (!ch.arm && rig.strands[ch.index].hidden()) continue;
            // its end, and the lowest of the whole of it (a strand lying on the ground may lift its end; and the pieces
            // up by his rim are drawn bent along the strand, which this rigid pose doesn't show)
            float end = lowestGap(e, m, ch.bones[ch.bones.length - 1]);
            float gap = Float.NaN;
            for (int b : ch.bones) {
                float g = lowestGap(e, m, b);
                if (!Float.isNaN(g) && (Float.isNaN(gap) || g < gap)) gap = g;
            }
            if (Float.isNaN(gap)) continue;
            String name = (ch.arm ? "arm " : "strand ") + ch.index;
            if (end < -0.75f && bad != null) bad.append(String.format(" %s end sunk %.2f;", name, end));
            if (e.chainKnocked(c)) {
                resting++;
                if (gap > 1f) {
                    now.add(name);
                    if (floating.contains(name) && bad != null)
                        bad.append(String.format(" %s floats %.2f;", name, gap));
                }
            }
        }
        floating.clear();
        floating.addAll(now);
        return resting;
    }

    /**
     * How far the lowest block of bone b is over the real ground under it, in blocks of his (NaN if none of it is
     * near the ground).
     */
    static float lowestGap(HollowbellEntity e, BellModel m, int b) {
        float[] bb = m.bounds[b];
        if (bb == null) return Float.NaN;
        float s = e.bellScale();
        float best = Float.NaN;
        Matrix4f[] slices = e.slicesNow();
        BellPieces pieces = BellPieces.get();
        Vector3f v = new Vector3f();
        for (int x = (int) Math.floor(bb[0]); x <= (int) Math.floor(bb[3]); x++)
            for (int y = (int) Math.floor(bb[1]); y <= (int) Math.floor(bb[4]); y++)
                for (int z = (int) Math.floor(bb[2]); z <= (int) Math.floor(bb[5]); z++) {
                    if (!m.has(b, x, y, z)) continue;
                    // (where it's drawn: the piece bent along the strand, in slices)
                    slices[pieces.sliceOf(b, x, y, z)].transformPosition(v.set(x + 0.5f, y + 0.5f, z + 0.5f));
                    Vec3 w = e.toWorld(v);
                    int top = e.level()
                            .getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(w.x), (int) Math.floor(w.z));
                    double g = NoWait.realTop(e.level(), (int) Math.floor(w.x), top, (int) Math.floor(w.z));
                    float gap = (float) ((w.y - g) / s) - 0.5f;
                    if (gap > 4f) continue;
                    if (Float.isNaN(best) || gap < best) best = gap;
                }
        return best;
    }
}
