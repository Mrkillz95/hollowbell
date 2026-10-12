package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.*;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.command.GiantsCommand;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Operator commands. */
public class OpsTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "ops_set")
    public void operatorsCanSetEverything(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 315);
        h.runAfterDelay(20, () -> {
            Vec3 at = e.position().add(0, 2, 0);
            run(h, at, "hollowbell set freeze on");
            h.assertTrue(e.frozen(), "set freeze on");
            run(h, at, "hollowbell set freeze off");
            h.assertTrue(!e.frozen(), "set freeze off");
            run(h, at, "hollowbell set speed 2.5");
            h.assertTrue(Math.abs(e.speedMul() - 2.5f) < 1e-3, "set speed: " + e.speedMul());
            run(h, at, "hollowbell set speed 1");
            run(h, at, "hollowbell set invulnerable on");
            h.assertTrue(e.isInvulnerable(), "set invulnerable on");
            run(h, at, "hollowbell set invulnerable off");
            h.assertTrue(!e.isInvulnerable(), "set invulnerable off");
            run(h, at, "hollowbell set glow on");
            h.assertTrue(e.hasGlowingTag(), "set glow on");
            run(h, at, "hollowbell set glow off");
            run(h, at, "hollowbell set name Big Bell");
            h.assertTrue(e.hasCustomName() && e.getCustomName().getString().equals("Big Bell"), "set name");
            run(h, at, "hollowbell set wind 0.25");
            h.assertTrue(
                    Math.abs(e.mood().wind() - 0.25f) < 0.02f,
                    "set wind: " + e.mood().wind());
            run(h, at, "hollowbell set home");
            h.assertTrue(e.home() != null && e.home().distanceTo(at) < 1.5, "set home: " + e.home());
            run(h, at, "hollowbell sethealth 50");
            h.assertTrue(Math.abs(e.healthNow() - 50f) < 1f, "sethealth: " + e.healthNow());
            run(h, at, "hollowbell pods pop 2");
            int left = e.podsLeft();
            h.assertTrue(left == e.rig.pods.length - 2, "pods pop 2 left " + left);
            run(h, at, "hollowbell pods mend");
            h.assertTrue(e.podsLeft() == e.rig.pods.length, "pods mend left " + e.podsLeft());
            run(h, at, "hollowbell size 0.15");
            h.assertTrue(Math.abs(e.bellScale() - 0.15f) < 1e-3, "size: " + e.bellScale());
            run(h, at, "hollowbell mood guardian");
            h.assertTrue(e.isGuardian(), "mood guardian");
            // a player to aim at: target and grudge
            ServerPlayer p = player(h, at.add(6, 0, 0));
            run(h, at, "hollowbell set target " + p.getGameProfile().getName());
            h.assertTrue(e.getTarget() == p, "set target: " + e.getTarget());
            run(h, at, "hollowbell set grudge " + p.getGameProfile().getName() + " 0.8");
            h.assertTrue(
                    e.mood().sourOf(p.getUUID()) > 0.7f,
                    "set grudge: " + e.mood().sourOf(p.getUUID()));
            run(h, at, "hollowbell set target none");
            h.assertTrue(e.getTarget() == null, "set target none");
            // a move at a spot, and at somebody
            e.setMoveCooldown(0);
            run(h, at, "hollowbell do sweep at " + (int) (e.getX() + 5) + " " + (int) e.getY() + " " + (int) e.getZ());
            h.assertTrue(
                    e.moves().move() == Moves.SWEEP,
                    "do sweep at a spot: " + e.moves().move());
            run(h, at, "hollowbell do pulse_wave " + p.getGameProfile().getName());
            h.assertTrue(
                    e.moves().move() == Moves.PULSE,
                    "do pulse_wave at a player: " + e.moves().move());
            run(h, at, "hollowbell set cooldowns clear");
            drop(p);
            e.setCustomName(null);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "ops_config")
    public void operatorsCanSetAnySetting(GameTestHelper h) {
        Vec3 at = onGround(h, 1, 1);
        float was = BellConfig.V.health;
        boolean bar = BellConfig.V.bossBar;
        try {
            var out = said(h, at, "hollowbell config health 1234");
            h.assertTrue(
                    Math.abs(BellConfig.V.health - 1234f) < 0.01f,
                    "config health didn't set: " + BellConfig.V.health + " " + out);
            said(h, at, "hollowbell config bossBar off");
            h.assertTrue(!BellConfig.V.bossBar, "config bossBar off didn't set");
            // the /giants bridge: list, look, set, a bad value, a key this mod doesn't have
            var server = h.getLevel().getServer();
            var keys = GiantsCommand.ask(server, "config", "", "net.jj.hollowbell.GiantsBridge");
            h.assertTrue(
                    keys.size() == 1
                            && keys.get(0).contains("health")
                            && keys.get(0).contains("bossBar"),
                    "config keys: " + keys);
            var look = GiantsCommand.ask(server, "config", "health", "net.jj.hollowbell.GiantsBridge");
            h.assertTrue(look.size() == 1 && look.get(0).contains("health = 1234"), "config look: " + look);
            var set = GiantsCommand.ask(server, "config", "health 2000", "net.jj.hollowbell.GiantsBridge");
            h.assertTrue(
                    Math.abs(BellConfig.V.health - 2000f) < 0.01f && set.get(0).contains("health = 2000"),
                    "config set: " + set);
            var bad = GiantsCommand.ask(server, "config", "health lots", "net.jj.hollowbell.GiantsBridge");
            h.assertTrue(bad.size() == 1 && bad.get(0).contains("can't be set"), "config bad value: " + bad);
            var none = GiantsCommand.ask(server, "config", "noSuchSetting 3", "net.jj.hollowbell.GiantsBridge");
            h.assertTrue(none.isEmpty(), "a key this mod doesn't have should say nothing: " + none);
        } finally {
            BellConfig.V.health = was;
            BellConfig.V.bossBar = bar;
            BellConfig.save();
        }
        h.succeed();
    }
}
