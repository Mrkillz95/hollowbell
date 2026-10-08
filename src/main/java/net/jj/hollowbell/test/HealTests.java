package net.jj.hollowbell.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import static net.jj.hollowbell.test.HollowbellGameTests.*;

/** The slow heal all of JJ's giants share: 1% of his most health a minute left alone, nothing while he's being hit. */
public class HealTests implements FabricGameTest {
    private static final float S = 0.1f;

    /** a real hit where his own hurt takes it, else his health set down (the slow heal sees both the same) */
    static void hit(HollowbellEntity e, float amount) {
        float was = e.healthNow();
        e.hurt(e.damageSources().generic(), amount);
        if (e.healthNow() >= was - 0.001f) e.setHealthTo(was - amount);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1800, batch = "heal_slow")
    public void leftAloneHeHealsAboutOnePercentAMinute(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 351);
        float[] start = new float[1];
        h.runAfterDelay(20, () -> e.setHealthTo(e.healthMax() * 0.5f));
        // (the heal waits 15 seconds after a hurt; measured over the next whole minute)
        h.runAfterDelay(20 + HollowbellEntity.HEAL_PAUSE + 30, () -> start[0] = e.healthNow());
        h.runAfterDelay(20 + HollowbellEntity.HEAL_PAUSE + 30 + 1200, () -> {
            float gained = e.healthNow() - start[0], want = e.healthMax() * 0.01f;
            h.assertTrue(Math.abs(gained - want) <= want * 0.12f + 0.05f, "in a minute he healed " + gained + ", not about 1% (" + want + ") of " + e.healthMax());
            h.assertTrue(e.healthNow() <= e.healthMax(), "he healed past his most");
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1600, batch = "heal_hit")
    public void hitEveryTenSecondsHeGainsNothing(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 352);
        float[] start = new float[1];
        h.runAfterDelay(20, () -> { e.setHealthTo(e.healthMax() * 0.5f); start[0] = e.healthNow(); });
        for (int k = 1; k <= 6; k++) h.runAfterDelay(20 + k * 200, () -> hit(e, Math.max(0.5f, e.healthMax() * 0.0005f)));
        for (int k = 1; k <= 12; k++) {
            int at = 20 + k * 100 + 5;
            h.runAfterDelay(at, () -> h.assertTrue(e.healthNow() <= start[0] + 0.001f, "he healed while being hit every 10 s: " + start[0] + " -> " + e.healthNow()));
        }
        h.runAfterDelay(20 + 1200 + 40, () -> {
            h.assertTrue(e.healthNow() <= start[0] + 0.001f, "he gained health over the minute: " + start[0] + " -> " + e.healthNow());
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "heal_full")
    public void theSlowHealNeverGoesPastHisMost(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 353);
        h.runAfterDelay(20, () -> e.setHealthTo(e.healthMax() - 0.01f));
        h.runAfterDelay(20 + 20, () -> {
            h.assertTrue(e.healthNow() <= e.healthMax() + 0.001f, "past his most: " + e.healthNow() + " of " + e.healthMax());
            release(h, e);
            h.succeed();
        });
    }
}
