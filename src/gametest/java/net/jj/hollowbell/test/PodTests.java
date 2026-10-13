package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.*;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.entity.Belling;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;

/** His pods and egg clumps: popping, growing back, sinking. */
public class PodTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 900, batch = "pod_regrow")
    public void podsGrowBack(GameTestHelper h) {
        int was = BellConfig.V.podRegrowSeconds;
        BellConfig.V.podRegrowSeconds = 1;
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 40);
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            e.popPods(1);
            h.assertTrue(e.isPodPopped(0) && e.podGrowth(0) == 0f, "pod 0 wasn't popped");
        });
        h.runAfterDelay(
                60,
                () -> h.assertTrue(
                        e.podGrowth(0) > 0f && e.podGrowth(0) < 1f, "pod 0 isn't growing back: " + e.podGrowth(0)));
        h.runAfterDelay(20 + 20 + 420, () -> {
            BellConfig.V.podRegrowSeconds = was;
            h.assertTrue(e.podGrowth(0) >= 1f && !e.isPodPopped(0), "pod 0 never grew back: " + e.podGrowth(0));
            h.assertTrue(e.podsLeft() == e.rig.pods.length, "not all pods back");
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120, batch = "sunk")
    public void popAThirdOfThePodsAndHeSinks(GameTestHelper h) {
        int was = BellConfig.V.sunkSeconds;
        BellConfig.V.sunkSeconds = 1;
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 41);
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            e.popPods(e.podsToSink() - 1);
            h.assertTrue(!e.sunk(), "he sank before a third of his pods were popped");
            e.popPods(1);
            h.assertTrue(
                    e.podsToSink() >= e.rig.pods.length / 4 && e.podsToSink() <= e.rig.pods.length / 2,
                    "pods to sink: " + e.podsToSink());
        });
        h.runAfterDelay(35, () -> {
            h.assertTrue(e.sunk(), "a third of his pods popped and he hasn't sunk");
            h.assertTrue(!e.forceMove(Moves.DROP, null), "he can drop while he's already sunk");
            e.mendPods();
        });
        h.runAfterDelay(80, () -> {
            BellConfig.V.sunkSeconds = was;
            h.assertTrue(!e.sunk(), "pods grown back and he's still sunk");
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "pods")
    public void podsPopAndStayPopped(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 42);
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            float before = e.healthNow();
            int pods = e.podsLeft();
            int bone = e.rig.pods[2].bone();
            for (int i = 0; i < 40 && !e.isPodPopped(2); i++)
                e.applyDamage(e.damageSources().generic(), 5f, bone, false);
            h.assertTrue(e.isPodPopped(2), "pod 2 never popped");
            h.assertTrue(e.podsLeft() == pods - 1, "pods left " + e.podsLeft());
            h.assertTrue(e.healthNow() < before - e.healthMax() * 0.012f, "popping a pod should cost him");
            var items = h.getLevel()
                    .getEntitiesOfClass(
                            ItemEntity.class, e.bodyBox(), it -> it.getItem().is(ModItems.POD));
            h.assertTrue(!items.isEmpty(), "a popped pod dropped nothing");
            for (var it : items) it.discard();
            // hitting it again does nothing more to the pod
            e.applyDamage(e.damageSources().generic(), 5f, bone, false);
            h.assertTrue(e.podsLeft() == pods - 1, "a popped pod came back");
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120, batch = "shed")
    public void sheddingHatchesBellings(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.HUNTER, 43);
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            h.assertTrue(e.forceMove(Moves.SHED, null), "no shed");
        });
        h.runAfterDelay(20 + Moves.SHED_AT + 3, () -> {
            int eggsGone = e.rig.eggs.length - e.eggsLeft();
            h.assertTrue(eggsGone == BellConfig.V.shedCount, "egg clumps gone: " + eggsGone);
            var bs = h.getLevel().getEntitiesOfClass(Belling.class, e.bodyBox().inflate(10));
            h.assertTrue(bs.size() == eggsGone, "bellings: " + bs.size());
            for (Belling b : bs) b.discard();
            release(h, e);
            h.succeed();
        });
    }
}
