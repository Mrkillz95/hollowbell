package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.*;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;

/**
 * Healing on his own, the rule every giant shares (JJ, Nov 1): nothing until he has been out of any fight for five
 * minutes (hurt, hurting something, hunting something), then 2% of his most health a minute over his own ground (the
 * Bell Hollows) and 1% anywhere else. While he sleeps he heals at those rates without the wait. The five minutes are
 * skipped with setCalmTicks.
 */
public class HealTests implements FabricGameTest {
    private static final float S = 0.1f;

    /** a real hit where his own hurt takes it, else his health set down (the heal sees both the same) */
    static void hit(HollowbellEntity e, float amount) {
        float was = e.healthNow();
        e.hurt(e.damageSources().generic(), amount);
        if (e.healthNow() >= was - 0.001f) e.setHealthTo(was - amount);
    }

    /** percent of his most health gained since `from` */
    private static float pct(HollowbellEntity e, float from) {
        return (e.healthNow() - from) / e.healthMax() * 100f;
    }

    private static void defaults() {
        BellConfig.V.passiveHeal = true;
        BellConfig.V.healHomePerMinute = 2f;
        BellConfig.V.healAwayPerMinute = 1f;
        BellConfig.V.healPauseMinutes = 5f;
    }

    /** his ground's biome put round where he hangs, with the game's own /fillbiome */
    private static void bellHollowsRound(GameTestHelper h, HollowbellEntity e) {
        int x = e.getBlockX(), z = e.getBlockZ();
        // (high up, the biome he's in is the one at the top of the world: biomes stop at its top)
        int top = h.getLevel().getMaxBuildHeight() - 1;
        int y1 = Math.max(h.getLevel().getMinBuildHeight(), Math.min(e.getBlockY() - 16, top - 32));
        CommandSourceStack src = h.getLevel()
                .getServer()
                .createCommandSourceStack()
                .withLevel(h.getLevel())
                .withPermission(4)
                .withSuppressedOutput();
        String cmd = String.format(
                "fillbiome %d %d %d %d %d %d hollowbell:bell_hollows",
                x - 8, y1, z - 8, x + 8, y1 + 32, z + 8);
        try {
            h.getLevel().getServer().getCommands().getDispatcher().execute(cmd, src);
        } catch (Exception ex) {
            h.fail("fillbiome failed: " + ex);
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "heal_calm")
    public void nothingUntilFiveMinutesOutOfAFight(GameTestHelper h) {
        defaults();
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 351);
        e.setStay(true); // (held still: wandering off the loaded ground would stop his clock)
        float[] start = new float[1];
        Pig[] pig = new Pig[1];
        h.runAfterDelay(20, () -> {
            e.setHealthTo(e.healthMax() * 0.5f);
            e.setCalmTicks(100_000);
            hit(e, Math.max(0.5f, e.healthMax() * 0.0005f));
        });
        h.runAfterDelay(22, () -> {
            h.assertTrue(e.calmTicks() < 5, "a hurt didn't start his five minutes again: " + e.calmTicks());
            start[0] = e.healthNow();
        });
        // a minute and a half later: nothing
        h.runAfterDelay(222, () -> {
            h.assertTrue(e.healthNow() <= start[0] + 0.001f, "he healed right after a hurt: " + pct(e, start[0]));
            // hurting something counts as a fight too
            e.setCalmTicks(5000);
            pig[0] = EntityType.PIG.create(h.getLevel());
            pig[0].moveTo(e.getX() + 30, e.getY(), e.getZ(), 0f, 0f);
            pig[0].setNoAi(true);
            h.getLevel().addFreshEntity(pig[0]);
            pig[0].hurt(e.damageSources().mobAttack(e), 1f);
            h.assertTrue(e.calmTicks() == 0, "hurting a pig didn't start his five minutes again: " + e.calmTicks());
            // and so does having something to go after
            e.setCalmTicks(5000);
            e.setTarget(pig[0]);
        });
        h.runAfterDelay(224, () -> {
            h.assertTrue(e.calmTicks() < 3, "a target didn't start his five minutes again: " + e.calmTicks());
            e.setTarget(null);
            pig[0].discard();
            // just short of five minutes: still nothing
            e.setCalmTicks(HollowbellEntity.healPause() - 60);
            start[0] = e.healthNow();
        });
        h.runAfterDelay(264, () -> h.assertTrue(
                e.healthNow() <= start[0] + 0.001f, "he healed before five minutes: " + pct(e, start[0])));
        // past it: he heals
        h.runAfterDelay(344, () -> {
            h.assertTrue(e.healthNow() > start[0], "five minutes out of a fight and he didn't heal");
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800, batch = "heal_home")
    public void twoPercentAMinuteOverTheBellHollows(GameTestHelper h) {
        defaults();
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 352);
        e.setStay(true);
        float[] start = new float[1];
        h.runAfterDelay(20, () -> bellHollowsRound(h, e));
        h.runAfterDelay(30, () -> {
            h.assertTrue(e.healsAtHome(), "the Bell Hollows round him don't count as his ground");
            e.setHealthTo(e.healthMax() * 0.5f);
        });
        // (a drop in his health counts as a hurt: the five minutes are skipped after it)
        h.runAfterDelay(32, () -> {
            e.setCalmTicks(100_000);
            start[0] = e.healthNow();
        });
        // half a minute: 1%
        h.runAfterDelay(630, () -> {
            float got = pct(e, start[0]);
            h.assertTrue(got > 0.85f && got < 1.15f, "over his ground he healed " + got + "% in half a minute, not 1");
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800, batch = "heal_away")
    public void onePercentAMinuteAnywhereElse(GameTestHelper h) {
        defaults();
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 353);
        e.setStay(true);
        float[] start = new float[1];
        h.runAfterDelay(20, () -> {
            h.assertTrue(!e.healsAtHome(), "far out he's over his own ground?");
            e.setHealthTo(e.healthMax() * 0.5f);
        });
        h.runAfterDelay(22, () -> {
            e.setCalmTicks(100_000);
            start[0] = e.healthNow();
        });
        h.runAfterDelay(620, () -> {
            float got = pct(e, start[0]);
            h.assertTrue(got > 0.42f && got < 0.58f, "away he healed " + got + "% in half a minute, not 0.5");
            h.assertTrue(e.healthNow() <= e.healthMax(), "he healed past his most");
            // off: nothing
            BellConfig.V.passiveHeal = false;
            start[0] = e.healthNow();
        });
        h.runAfterDelay(700, () -> {
            try {
                h.assertTrue(e.healthNow() <= start[0] + 0.001f, "with passiveHeal off he healed");
            } finally {
                defaults();
            }
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 700, batch = "heal_sleep")
    public void asleepHeHealsAtOnceAwakeHeWaits(GameTestHelper h) {
        defaults();
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 354);
        e.setStay(true);
        float[] start = new float[1];
        h.runAfterDelay(20, () -> {
            e.setHealthTo(e.healthMax() * 0.5f);
            e.goToSleep();
            e.setCalmTicks(0);
            start[0] = e.healthNow();
        });
        // half a minute asleep, fresh from a fight: 0.5% (he's not over his ground)
        h.runAfterDelay(620, () -> {
            h.assertTrue(e.asleep(), "he woke up on his own");
            float got = pct(e, start[0]);
            h.assertTrue(got > 0.42f && got < 0.58f, "asleep he healed " + got + "% in half a minute, not 0.5");
            e.wakeUp();
            e.setCalmTicks(0);
            start[0] = e.healthNow();
        });
        h.runAfterDelay(680, () -> {
            h.assertTrue(e.healthNow() <= start[0] + 0.001f, "awake, he healed before five minutes");
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "heal_full")
    public void healingNeverGoesPastHisMost(GameTestHelper h) {
        defaults();
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 355);
        e.setStay(true);
        h.runAfterDelay(20, () -> e.setHealthTo(e.healthMax() - 0.01f));
        h.runAfterDelay(22, () -> e.setCalmTicks(100_000));
        h.runAfterDelay(20 + 40, () -> {
            h.assertTrue(
                    e.healthNow() <= e.healthMax() + 0.001f,
                    "past his most: " + e.healthNow() + " of " + e.healthMax());
            release(h, e);
            h.succeed();
        });
    }
}
