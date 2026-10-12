package net.jj.hollowbell.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.animal.Pig;

import static net.jj.hollowbell.test.HollowbellGameTests.pig;
import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.run;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

/** Over half health he keeps his heavy moves out of the first part of a fight, as long as the setting says. */
public class HeavyDelayTests implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "heavy_delay")
    public void noHeavyMoveInTheFirstMinuteUnlessTheWaitIsOff(GameTestHelper h) {
        int was = HollowbellConfig.V.heavyDelaySeconds;
        HollowbellEntity e = spawnAway(h, 0.2f, HollowbellEntity.HUNTER, 615);
        Pig[] p = new Pig[1];
        boolean[] heavy = {false};
        h.runAfterDelay(20, () -> {
            run(h, e.position(), "hollowbell heavydelay 60");
            p[0] = pig(h, e.position().add(3, 0, 0));
            p[0].setInvulnerable(true);
            e.setTarget(p[0]);
            e.setMoveCooldown(0);
        });
        h.onEachTick(() -> { if (Moves.tier(e.moves().move()) == Moves.HEAVY) heavy[0] = true; });
        h.runAfterDelay(260, () -> {
            boolean waited = !e.moves().heavyWaitOver();
            run(h, e.position(), "hollowbell heavydelay 0");
            boolean free = e.moves().heavyWaitOver();
            HollowbellConfig.V.heavyDelaySeconds = was;
            HollowbellConfig.save();
            p[0].discard();
            release(h, e);
            h.assertTrue(HollowbellConfig.V.heavyDelaySeconds == was, "the setting wasn't put back");
            h.assertTrue(waited, "his heavy moves were his ten seconds into a fight");
            h.assertTrue(!heavy[0], "he used a heavy move in the first minute of a fight");
            h.assertTrue(free, "with the wait at 0 his heavy moves weren't his at once");
            h.succeed();
        });
    }
}
