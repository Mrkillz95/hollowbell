package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.run;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** He has no height limit: told to stay up at Y 1000 he stays there, and sent down again he comes down. */
public class HeightTests implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 8000, batch = "height")
    public void heStaysUpAtAThousandAndComesBackDown(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.1f, HollowbellEntity.CALM, 641);
        double[] ground = new double[1];
        h.runAfterDelay(20, () -> {
            ground[0] = e.groundAt(e.getX(), e.getZ());
            e.setStay(true);
            e.setPos(e.getX(), 1000, e.getZ());
        });
        h.runAfterDelay(140, () -> {
            h.assertTrue(e.isAlive(), "he died up there");
            h.assertTrue(e.getY() > 990, "told to stay at Y 1000 he sank to " + e.getY());
            h.assertTrue(
                    Math.abs(e.groundAt(e.getX(), e.getZ()) - ground[0]) < 1e-6,
                    "up high he lost the ground under him");
            e.setStay(false);
            run(h, e.position(), "hollowbell height 4");
        });
        boolean[] done = {false};
        h.onEachTick(() -> {
            if (done[0] || h.getTick() < 150) return;
            if (e.getY() > 900) return;
            done[0] = true;
            boolean alive = e.isAlive();
            release(h, e);
            h.assertTrue(alive, "he died coming down");
            h.succeed();
        });
    }
}
