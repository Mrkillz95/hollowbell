package net.jj.giants.test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.giants.Giants;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.minigames.MgData;
import net.jj.giants.minigames.MgGame;
import net.jj.giants.minigames.MgGiants;
import net.jj.giants.minigames.MgLevels;
import net.jj.giants.minigames.Minigames;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Part 10A with the real giants (-Pgiants): every installed giant comes into a Giant Royale round's own world by his
 * own summon, hunting, and is taken away with it; Boss Rush's giants get the round's health; Possession seats a
 * player at a real giant's controls.
 */
public class MinigameGiantsGameTests implements FabricGameTest {
    static boolean real(GameTestHelper h) {
        if (!Giants.installed().isEmpty()) return true;
        h.succeed();
        return false;
    }

    static MinecraftServer fresh(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        if (Minigames.current() != null) Minigames.stopForTests(s);
        AdminWorld.get(s).clear("all");
        MgData.get(s).s = new MgData.State();
        AdminServer.push(s, AdminWorld.get(s));
        MinigameGameTests.set(h, s, "mgSize", "small");
        MinigameGameTests.set(h, s, "mgLobby", "10");
        return s;
    }

    static void done(GameTestHelper h, ServerPlayer... ps) {
        MinecraftServer s = h.getLevel().getServer();
        if (Minigames.current() != null) Minigames.stopForTests(s);
        for (ServerPlayer p : ps) if (s.getPlayerList().getPlayer(p.getUUID()) != null) s.getPlayerList().remove(p);
        AdminWorld.get(s).clear("all");
        AdminServer.push(s, AdminWorld.get(s));
        h.succeed();
    }

    static MgGame game() {
        return MinigameGameTests.game();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20000, batch = "mgg_royale")
    public void everyRealGiantComesIntoARoyaleAndGoesWithIt(GameTestHelper h) {
        if (!real(h)) return;
        MinecraftServer s = fresh(h);
        ServerPlayer a = MinigameGameTests.join(h, "mgreal", false).p();
        ServerLevel[] w = {null};
        h.assertTrue(Minigames.start(s, "Tester", "royale", false, 1).ok(), "opened");
        Minigames.join(a, false);
        Minigames.now(s, "Tester");
        // (a giant who never fights isn't in the minigames)
        int kinds = (int) AdminServer.giants().stream().filter(g -> !Giants.NEVER_FIGHT.contains(g.key())).count();
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(game().phase() == MgGame.Phase.PLAY, "playing"))
                .thenWaitUntil(() -> h.assertTrue(game().giants.all.size() == kinds, "every giant came: "
                        + game().giants.all.size() + " of " + kinds))
                .thenExecute(() -> {
                    w[0] = game().level;
                    Set<String> seen = new HashSet<>();
                    for (MgGiants.G k : game().giants.all.values()) {
                        LivingEntity e = game().giants.entity(k);
                        h.assertTrue(e != null && e.level() == w[0], k.kind + " stands in the round's world");
                        double size = FunGiants.sizeOf(e);
                        h.assertTrue(Math.abs(size - game().giantSize) < 0.02, k.kind + " at the round's size: " + size);
                        seen.add(k.kind);
                    }
                    h.assertTrue(seen.size() == kinds, "one of each: " + seen);
                    Minigames.stop(s, "Tester");
                })
                .thenWaitUntil(() -> h.assertTrue(s.getLevel(w[0].dimension()) == null, "its world thrown away"))
                .thenExecute(() -> {
                    for (ServerLevel l : s.getAllLevels())
                        for (Entity e : l.getAllEntities())
                            h.assertTrue(!MgLevels.temporary(l) || FunGiants.kindOf(e) == null, "none left");
                    // no giant mod lists one in a minigame's world any more
                    h.assertTrue(Minigames.cleanGiantsForTests(s) == 0, "no giant noted in a minigame world");
                    done(h, a);
                });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20000, batch = "mgg_rush")
    public void bossRushGivesRealGiantsTheRoundsHealth(GameTestHelper h) {
        if (!real(h)) return;
        MinecraftServer s = fresh(h);
        ServerPlayer a = MinigameGameTests.join(h, "mgrealbr", false).p();
        h.assertTrue(Minigames.start(s, "Tester", "bossrush", false, 1).ok(), "opened");
        Minigames.join(a, false);
        Minigames.now(s, "Tester");
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(game().phase() == MgGame.Phase.PLAY, "playing"))
                .thenWaitUntil(() -> h.assertTrue(!game().giants.all.isEmpty(), "the first boss"))
                .thenIdle(20)
                .thenExecute(() -> {
                    MgGiants.G k = game().giants.all.values().iterator().next();
                    LivingEntity e = game().giants.entity(k);
                    double want = 135;
                    double max = e == null ? -1 : game().giants.maxOfForTests(e);
                    // (a giant's own health tag goes no lower than x0.1: the Hollowbell is that tough at any size)
                    boolean floor = k.health <= 0.1001;
                    String why = k.kind + "'s health for the round: " + max + " x" + k.health;
                    Minigames.stopForTests(s);
                    h.assertTrue(e != null && (Math.abs(max - want) < want * 0.3 || floor), why);
                    done(h, a);
                });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20000, batch = "mgg_possession")
    public void possessionSeatsAPlayerAtARealGiantsControls(GameTestHelper h) {
        if (!real(h)) return;
        MinecraftServer s = fresh(h);
        ServerPlayer a = MinigameGameTests.join(h, "mgrealpa", false).p(),
                b = MinigameGameTests.join(h, "mgrealpb", false).p();
        h.assertTrue(Minigames.start(s, "Tester", "possession", false, 1).ok(), "opened");
        Minigames.join(a, false);
        Minigames.join(b, false);
        Minigames.now(s, "Tester");
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(game().phase() == MgGame.Phase.PLAY, "playing"))
                .thenExecute(() -> MinigameGameTests.arrived(a, b))
                .thenWaitUntil(() -> h.assertTrue(a.isPassenger() || b.isPassenger(), "someone at his controls"))
                .thenExecute(() -> {
                    ServerPlayer at = a.isPassenger() ? a : b;
                    h.assertTrue(FunGiants.kindOf(at.getRootVehicle()) != null || at.getVehicle() != null,
                            "riding the giant");
                    Minigames.stop(s, "Tester");
                    h.assertTrue(!a.isPassenger() && !b.isPassenger(), "set down when it ends");
                    done(h, a, b);
                });
    }

    static List<String> unused() {
        return List.of();
    }
}
