package net.jj.hollowbell.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.GiantsMinigame;
import net.jj.hollowbell.GiantsSystems;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

/**
 * The Guide's "go" beats his keeping away from another giant (an escorted one drifted off the road near a guard),
 * and in a minigame world, or scripted by an adventure, he never meets other giants at all.
 */
public class GoOrderTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void aGoOrderBeatsKeepingAway(GameTestHelper h) {
        HollowbellEntity e = TinySizeTests.spawn(h, h.absoluteVec(new Vec3(2, 2, 2)), 0.1f);
        h.runAfterDelay(20, () -> {
            h.assertTrue(!e.goOrdered(), "he starts with no order");
            String go = String.format(java.util.Locale.ROOT, "{\"sys\":1,\"id\":\"%s\",\"do\":\"go\",\"x\":%.1f,\"z\":%.1f}",
                    e.getUUID(), e.getX() + 200, e.getZ());
            var said = GiantsSystems.act(h.getLevel().getServer(), go);
            h.assertTrue(said != null && "ok".equals(said.get(0)), "go wasn't taken: " + said);
            h.assertTrue(e.goOrdered() && e.avoidingSpot() == null, "the go order doesn't beat keeping away");
            h.assertTrue(!e.noMeetings(), "a wild one in a plain world still meets giants");
            GiantsMinigame.testWorld = h.getLevel().dimension().location();
            try {
                h.assertTrue(e.noMeetings(), "a minigame world still has meetings");
            } finally {
                GiantsMinigame.testWorld = null;
            }
            e.addTag("jj_giants.scripted");
            h.assertTrue(e.noMeetings(), "a scripted one still meets giants");
            e.discard();
            h.succeed();
        });
    }
}
