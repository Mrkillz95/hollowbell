package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.entity.BellMoves;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.entity.Shot;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;

/** He flies, so against the other giants he is a little weaker: their blows land harder, his reach them less. */
public class BalanceTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "balance_armour")
    public void giantsBlowsLandHarderAndOldSettingsMove(GameTestHelper h) {
        h.assertTrue(Math.abs(new BellConfig.Values().giantArmor - 0.78f) < 1e-6f, "the new default isn't 0.78");
        // an old file still holding the old default is brought up to the new one
        var old = new BellConfig.Values();
        old.configVersion = 8;
        old.giantArmor = 0.7f;
        BellConfig.migrate(old);
        h.assertTrue(Math.abs(old.giantArmor - 0.78f) < 1e-6f, "an old default stayed: " + old.giantArmor);
        // a number somebody chose is kept
        var mine = new BellConfig.Values();
        mine.configVersion = 8;
        mine.giantArmor = 0.5f;
        BellConfig.migrate(mine);
        h.assertTrue(Math.abs(mine.giantArmor - 0.5f) < 1e-6f, "a chosen number was changed: " + mine.giantArmor);
        // and a file already this new keeps 0.7 if that's what it says
        var now = new BellConfig.Values();
        now.configVersion = 9;
        now.giantArmor = 0.7f;
        BellConfig.migrate(now);
        h.assertTrue(Math.abs(now.giantArmor - 0.7f) < 1e-6f, "a new file's 0.7 was changed: " + now.giantArmor);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "balance_storm_cap")
    public void theStormCountsThreeArmsAGiant(GameTestHelper h) {
        Map<UUID, Integer> had = new HashMap<>();
        UUID g = UUID.randomUUID(), other = UUID.randomUUID();
        int yes = 0;
        for (int k = 0; k < 8; k++) if (BellMoves.stormCap(had, g, k)) yes++;
        h.assertTrue(yes == BellMoves.STORM_ARMS_PER_GIANT, yes + " arms landed on one giant");
        h.assertTrue(BellMoves.stormCap(had, g, 1), "an arm already counted was turned away");
        h.assertTrue(BellMoves.stormCap(had, other, 7), "another giant shares the first one's count");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "balance_storm")
    public void theArmStormLandsAtMostThreeArmsOnAGiant(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 801);
        int arms = e.rig.arms.length;
        Zombie[] g = new Zombie[1];
        FakePart[] part = new FakePart[arms];
        int[] most = {0};
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            // one giant with a part under every arm's end: every arm that comes down would land on it
            g[0] = fakeGiant(h, e.position().add(e.bellRadius() * 4 + 60, 0, 0));
            g[0].getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
            g[0].setHealth(1024f);
            for (int k = 0; k < arms; k++) {
                part[k] = new FakePart(h.getLevel(), g[0]);
                Vec3 tip = e.armTipWorld(k);
                part[k].moveTo(tip.x, e.groundAt(tip.x, tip.z), tip.z, 0f, 0f);
                h.getLevel().addFreshEntity(part[k]);
            }
            e.setTarget(g[0]);
            h.assertTrue(e.forceMove(Moves.ARM_STORM, g[0]), "no arm storm");
        });
        h.onEachTick(() -> {
            if (g[0] == null || part[0] == null || e.moveNow() != Moves.ARM_STORM) return;
            for (int k = 0; k < arms; k++) {
                Vec3 tip = e.armTipWorld(k);
                part[k].moveTo(tip.x, e.groundAt(tip.x, tip.z), tip.z, 0f, 0f);
            }
            most[0] = Math.max(most[0], e.moves().stormArmsOn(g[0]));
        });
        h.runAfterDelay(20 + Moves.length(Moves.ARM_STORM) + 20, () -> {
            h.assertTrue(e.moveNow() != Moves.ARM_STORM, "the arm storm never finished");
            h.assertTrue(most[0] <= BellMoves.STORM_ARMS_PER_GIANT, most[0] + " arms landed on the giant");
            h.assertTrue(most[0] > 0 && g[0].getHealth() < 1024f, "no arm landed on the giant at all");
            for (var p : part) p.discard();
            g[0].discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "balance_stray")
    public void strayStingersNeverTouchAGiantHeIsNotFighting(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 802);
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            var g = fakeGiant(h, under(e).add(30, 0, 0));
            Pig pig = pig(h, under(e).add(-30, 0, 0));
            Shot st = new Shot(h.getLevel(), e, Shot.STINGER, 7f, 1f);
            Shot egg = new Shot(h.getLevel(), e, Shot.EGG, 7f, 3f);
            e.setTarget(null);
            h.assertFalse(st.mayHit(g), "a stray stinger can hit a giant he isn't fighting");
            h.assertFalse(egg.mayHit(g), "a stray egg can hit a giant he isn't fighting");
            h.assertTrue(st.mayHit(pig), "a stinger can't hit a creature");
            e.setTarget(g);
            h.assertTrue(st.mayHit(g), "a stinger can't hit the giant he's fighting");
            h.assertTrue(egg.mayHit(g), "an egg can't hit the giant he's fighting");
            g.discard();
            pig.discard();
            release(h, e);
            h.succeed();
        });
    }
}
