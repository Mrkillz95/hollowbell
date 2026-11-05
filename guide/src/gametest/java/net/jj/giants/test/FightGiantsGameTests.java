package net.jj.giants.test;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.fights.FightData;
import net.jj.giants.fights.FightEdit;
import net.jj.giants.fights.FightGiants;
import net.jj.giants.fights.FightScript;
import net.jj.giants.fights.Fights;
import net.jj.giants.fun.FunGiants;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Part 10B with the real giants (-Pgiants, ADMIN_BRIDGE.md §27): every giant answers "fights" (his moves, his
 * finisher among them) and "fightact" (his health read and set, a fight started and stood down, the fight tags read
 * through his own hurt path); and a staged fight between two real kinds is fought out to the chosen winner with real,
 * paced blows. Without the giants each test passes at once.
 */
public class FightGiantsGameTests implements FabricGameTest {

    private static boolean here(String key) {
        Giant g = Giants.byKey(key);
        return g != null && Giants.loaded(g);
    }

    private void each(GameTestHelper h, String key, int slot) {
        if (!here(key)) {
            h.succeed();
            return;
        }
        MinecraftServer s = h.getLevel().getServer();
        Giant g = Giants.byKey(key);
        JsonObject head = FightGiants.head(s, g);
        h.assertTrue(head != null, key + ": no fights line");
        List<String> moves = FightGiants.moves(s, g);
        String fin = FightGiants.finisher(s, g);
        h.assertTrue(!moves.isEmpty() && moves.contains(fin), key + ": his finisher " + fin + " is one of " + moves);
        h.assertTrue(head.toString().contains("\"roar\""), key + ": " + head);
        BlockPos at = FunGiantsGameTests.pad(h, slot);
        LivingEntity e = FunGiantsGameTests.summon(h, g, at, 0.1);
        h.assertTrue(e != null, key + ": none summoned");
        h.runAfterDelay(20, () -> {
            FightGiants.Hp hp = FightGiants.hp(s, e);
            h.assertTrue(hp.most() > 0 && hp.now() > 0, key + ": hp " + hp);
            JsonObject o = new JsonObject();
            o.addProperty("share", 0.5);
            o.addProperty("set", true);
            List<String> r = FightGiants.act(s, e, "health", o);
            h.assertTrue(r != null && r.get(0).equals("ok"), key + ": health " + r);
            double share = FightGiants.hp(s, e).now() / FightGiants.hp(s, e).most();
            h.assertTrue(Math.abs(share - 0.5) < 0.05, key + ": health set to half: " + share);
            r = FightGiants.act(s, e, "stop", null);
            h.assertTrue(r != null && r.get(0).equals("ok"), key + ": stop " + r);
            r = FightGiants.act(s, e, "roar", null);
            h.assertTrue(r != null && r.get(0).equals("ok"), key + ": roar " + r);
            e.discard();
            FunGiantsGameTests.unpad(h, at);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "f10bg_pitchgut")
    public void pitchgut(GameTestHelper h) {
        each(h, "pitchgut", 40);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "f10bg_furrowmaw")
    public void furrowmaw(GameTestHelper h) {
        each(h, "furrowmaw", 41);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "f10bg_cerberus")
    public void cerberus(GameTestHelper h) {
        each(h, "cerberus", 42);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "f10bg_hollowbell")
    public void hollowbell(GameTestHelper h) {
        each(h, "hollowbell", 43);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "f10bg_lanternwillow")
    public void lanternwillow(GameTestHelper h) {
        each(h, "lanternwillow", 44);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "f10bg_wreckback")
    public void wreckback(GameTestHelper h) {
        each(h, "wreckback", 45);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "f10bg_swarmforge")
    public void swarmforge(GameTestHelper h) {
        each(h, "swarmforge", 46);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "f10bg_gloamwick")
    public void gloamwick(GameTestHelper h) {
        each(h, "gloamwick", 47);
    }

    /** a quick staged fight between two real kinds: the chosen winner wins with real blows, the loser bows out */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 9000, batch = "f10bg_staged")
    public void aStagedFightBetweenTwoKindsIsWonByTheChosenWinner(GameTestHelper h) {
        List<Giant> two = new ArrayList<>();
        for (String k : List.of("wreckback", "cerberus", "pitchgut", "gloamwick", "furrowmaw"))
            if (here(k) && two.size() < 2) two.add(Giants.byKey(k));
        if (two.size() < 2) {
            h.succeed();
            return;
        }
        MinecraftServer s = h.getLevel().getServer();
        BlockPos at = FunGiantsGameTests.pad(h, 48);
        FightData.Setup d = new FightData.Setup();
        FightEdit.giant(s, d, "a", 1, two.get(0).key(), 0.12, null);
        FightEdit.giant(s, d, "b", 1, two.get(1).key(), 0.12, null);
        FightEdit.set(d, "winner", "a");
        FightEdit.set(d, "ending", "bow");
        FightEdit.set(d, "length", "quick");
        FightEdit.set(d, "countdown", "off");
        FightEdit.set(d, "break", "0.4");
        AdminServer.Result r = FightScript.start(s, "Tester", java.util.UUID.randomUUID(), d, h.getLevel(), at,
                new Vec3(0, 0, 1));
        h.assertTrue(r.ok(), "start: " + r.lines());
        String[] won = {""};
        double[] lowA = {1};
        h.onEachTick(() -> {
            JsonObject now = Fights.state(s, null).getAsJsonObject("now");
            if (now == null) return;
            if (!AdminJson.str(now, "won", 2).isEmpty()) won[0] = AdminJson.str(now, "won", 2);
            for (var e : now.getAsJsonArray("giants")) {
                JsonObject j = e.getAsJsonObject();
                if (j.get("side").getAsString().equals("a")) lowA[0] = Math.min(lowA[0], j.get("share").getAsDouble());
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(!FightScript.busy(), "still fighting");
            h.assertTrue(won[0].equals("a"), "the chosen winner won: " + won[0]);
            h.assertTrue(lowA[0] > 0.4, "the winner never came near breaking: " + lowA[0]);
            for (LivingEntity e : FunGiants.standing(s, null))
                if (e.distanceToSqr(Vec3.atCenterOf(at)) < 300 * 300) e.discard();
            FunGiantsGameTests.unpad(h, at);
        });
    }

    /**
     * (1.16) the 1.15 server check: a bot standing between the two was killed 7 s after the fight was stopped. Now
     * Stop calms both down for a minute (every target and grudge dropped, players and each other) while they walk
     * home: a player standing at the spot between them is left alone.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 3000, batch = "f16g_stop")
    public void aStoppedFightLeavesAPlayerBetweenThemAlone(GameTestHelper h) {
        List<Giant> two = new ArrayList<>();
        for (String k : List.of("wreckback", "cerberus", "pitchgut", "gloamwick", "furrowmaw"))
            if (here(k) && two.size() < 2) two.add(Giants.byKey(k));
        if (two.size() < 2) {
            h.succeed();
            return;
        }
        MinecraftServer s = h.getLevel().getServer();
        BlockPos at = FunGiantsGameTests.pad(h, 49);
        FightData.Setup d = new FightData.Setup();
        FightEdit.giant(s, d, "a", 1, two.get(0).key(), 0.12, null);
        FightEdit.giant(s, d, "b", 1, two.get(1).key(), 0.12, null);
        FightEdit.set(d, "countdown", "off");
        FightEdit.set(d, "length", "epic");
        AdminServer.Result r = FightScript.start(s, "Tester", java.util.UUID.randomUUID(), d, h.getLevel(), at,
                new Vec3(0, 0, 1));
        h.assertTrue(r.ok(), "start: " + r.lines());
        long[] stopAt = {-1};
        MinigameGameTests.Joined[] j = {null};
        boolean[] hit = {false};
        h.onEachTick(() -> {
            long now = h.getLevel().getGameTime();
            if (stopAt[0] < 0) {
                JsonObject st = Fights.state(s, null).getAsJsonObject("now");
                if (st == null || !"fight".equals(AdminJson.str(st, "phase", 16))) return;
                // a few seconds in: a player walks in between them, and the fight is stopped
                j[0] = MinigameGameTests.join(h, "fstopper", false);
                var p = j[0].p();
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                p.teleportTo(h.getLevel(), at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 0, 0);
                h.assertTrue(FightScript.stop(s, "Tester").ok(), "stopped");
                stopAt[0] = now;
                return;
            }
            var p = j[0].p();
            // (kept at full health each tick, so any drop is a blow since the last tick)
            if (now - stopAt[0] > 30 && (p.getHealth() < 19.99f || p.isDeadOrDying())) {
                var src = p.getLastDamageSource();
                if (src != null && FunGiants.kindOf(src.getEntity()) != null) hit[0] = true;
            }
            if (!p.isDeadOrDying()) p.setHealth(20f);
        });
        h.succeedWhen(() -> {
            h.assertTrue(stopAt[0] > 0 && h.getLevel().getGameTime() - stopAt[0] > 240, "ten seconds after Stop");
            h.assertFalse(hit[0], "a giant went for the player after the fight was stopped");
            for (LivingEntity e : FunGiants.standing(s, null))
                if (e.distanceToSqr(Vec3.atCenterOf(at)) < 300 * 300) {
                    if (e instanceof net.minecraft.world.entity.Mob m)
                        h.assertTrue(!(m.getTarget() instanceof net.minecraft.world.entity.player.Player),
                                "no fighter has the player as his target");
                    e.discard();
                }
            s.getPlayerList().remove(j[0].p());
            FunGiantsGameTests.unpad(h, at);
        });
    }
}
