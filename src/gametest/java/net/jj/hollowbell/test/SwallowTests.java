package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.rig.BellAnim;
import net.jj.hollowbell.rig.BellRig;
import net.jj.hollowbell.rig.BellState;
import net.jj.hollowbell.rig.IntoDome;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import org.joml.Vector3f;

/**
 * What a strand pulls up is swallowed the way a bell jelly takes its food in: it is drawn from the strand's end in
 * under the rim and up into the hollow to its place, never through the glass, along one smooth curve with no jolt;
 * the bell opens as it comes and shuts hard on it once it is in.
 */
public class SwallowTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void swallowedInUnderTheRimAlongOneSmoothCurve(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 980);
        h.runAfterDelay(20, () -> {
            BellRig rig = BellRig.get();
            List<String> bad = new ArrayList<>();
            float worstJolt = 0f, worstGap = Float.MAX_VALUE, longest = 0f;
            int paths = 0;
            for (int slot = 0; slot < HollowbellEntity.SLOTS; slot++) {
                Vector3f to = e.toModel(e.slotWorld(slot));
                for (int k = 0; k < rig.strands.length; k += 3) {
                    if (rig.strands[k].hidden()) continue;
                    Vector3f top = rig.strands[k].joints()[0], end = rig.strands[k].bottom();
                    // from the end of the strand drawn up (as the lift leaves it), and hanging right down
                    for (float reel : new float[] {0.2f, 0.6f, 1f}) {
                        Vector3f from = new Vector3f(end).sub(top).mul(reel).add(top);
                        paths++;
                        Vector3f p = new Vector3f(), was = new Vector3f(), step = new Vector3f(), stepWas = null;
                        for (int t = 0; t <= Moves.INTO; t++) {
                            IntoDome.at(rig, from, to, t / (float) Moves.INTO, p);
                            if (t == 0 && p.distance(from) > 0.01f) bad.add("starts off the strand");
                            if (t == Moves.INTO && p.distance(to) > 0.01f) bad.add("ends off its place");
                            // (inside the bell, between the rim and the crown: clear of the glass)
                            if (p.y >= rig.rimY && p.y <= rig.crownY) {
                                float gap = rig.domeInner(p.y) - (float) Math.hypot(p.x, p.z);
                                worstGap = Math.min(worstGap, gap);
                                if (gap < 2f)
                                    bad.add(String.format("slot %d strand %d: through the glass at y %.0f", slot, k,
                                            p.y));
                            }
                            if (t > 0) {
                                Vector3f st = new Vector3f(p).sub(was);
                                longest = Math.max(longest, st.length());
                                if (stepWas != null) worstJolt = Math.max(worstJolt, st.distance(stepWas));
                                stepWas = step.set(st);
                            }
                            was.set(p);
                        }
                    }
                }
            }
            HollowbellMod.LOG.info(
                    "swallow: {} paths, longest step {} model blocks a tick, worst jolt {}, closest to the glass {}",
                    paths, longest, worstJolt, worstGap);
            // (a jolt is the change in its step from one tick to the next: never more than a third of its fastest)
            if (worstJolt > longest / 3f) bad.add("jolts " + worstJolt + " (fastest step " + longest + ")");
            release(h, e);
            h.assertTrue(bad.isEmpty(), String.join("; ", bad.subList(0, Math.min(6, bad.size()))));
            h.succeed();
        });
    }

    /** the bell opens wide as it draws the thing in and shuts hard on it once it is in */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void theBellOpensThenShutsOnIt(GameTestHelper h) {
        BellAnim.In in = new BellAnim.In();
        BellState st = new BellState(BellRig.get());
        int into0 = Moves.REACH + Moves.LIFT, gulp = Math.round(Moves.GULP * Moves.INTO);
        float opened = 0f, shut = -1f;
        for (int t = 1; t <= Moves.INTO; t++) {
            Moves.pose(BellRig.get(), st, in, Moves.GRAB, into0 + t, 30, 55f, 4f, 25f, 1f);
            if (t < gulp) opened = Math.min(opened, in.squeezeAdd);
            else shut = Math.max(shut, in.squeezeAdd);
        }
        h.assertTrue(opened < -0.4f, "the bell never opened for it: " + opened);
        h.assertTrue(shut > 0.15f, "the bell never shut on it: " + shut);
        h.succeed();
    }
}
