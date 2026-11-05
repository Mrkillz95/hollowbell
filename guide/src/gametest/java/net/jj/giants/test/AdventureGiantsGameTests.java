package net.jj.giants.test;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.adventure.AdvData;
import net.jj.giants.adventure.Adventure;
import net.jj.giants.admin.AdminLive;
import net.jj.giants.admin.AdminServer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.jj.giants.adventure.AdvMaps;
import net.jj.giants.minigames.MgData;
import net.jj.giants.minigames.MgGame;
import net.jj.giants.minigames.Minigames;

/**
 * Part 11 with the real giants (-Pgiants): a real Wreckback (or the first giant installed) given a script walks its
 * path by his own wander spots (his `sysact go`) and gets his boss bar title from it.
 */
public class AdventureGiantsGameTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 4000, batch = "advg_walk")
    public void aRealGiantWalksHisScriptsPath(GameTestHelper h) {
        Giant g = Giants.byKey("wreckback");
        if (g == null || !Giants.loaded(g)) g = Giants.installed().isEmpty() ? null : Giants.installed().get(0);
        if (g == null) {
            h.succeed();
            return;
        }
        MinecraftServer s = h.getLevel().getServer();
        AdvData.get(s).s = new AdvData.State();
        Adventure.forget();
        BlockPos at = FunGiantsGameTests.pad(h, 61);
        LivingEntity e = FunGiantsGameTests.summon(h, g, at, 0.15);
        h.assertTrue(e != null, "summoned");
        String dim = h.getLevel().dimension().location().toString();
        double x0 = e.getX();
        Adventure.pathAdd(s, "Tester", "walk_east", dim, e.getX(), e.getY(), e.getZ());
        Adventure.pathAdd(s, "Tester", "walk_east", dim, e.getX() + 60, e.getY(), e.getZ());
        Adventure.scriptStep(s, "Tester", "walker", 0, "title The Walker");
        Adventure.scriptStep(s, "Tester", "walker", 0, "walk walk_east");
        h.assertTrue(Adventure.give(s, "Tester", 2, e, "walker").ok(), "given");
        Giant k = g;
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(e.getX() - x0 > 15 || AdvData.get(s).s.runs.get(e.getStringUUID())
                        .done, "walking east: " + Math.round(e.getX() - x0)))
                .thenExecute(() -> {
                    h.assertTrue(e.getCustomName() != null && e.getCustomName().getString().contains("The Walker"),
                            "his title: " + e.getCustomName());
                    JsonObject o = new JsonObject();
                    o.addProperty("live", AdminLive.VERSION);
                    o.addProperty("id", e.getStringUUID());
                    o.addProperty("do", "remove");
                    o.addProperty("value", "");
                    o.addProperty("by", "-");
                    AdminServer.call(s, k, AdminLive.ACT, o.toString());
                    AdvData.get(s).s = new AdvData.State();
                    Adventure.forget();
                    h.succeed();
                });
    }

    /** (lagfix) the lost giant's blows that landed on a player, and the guard's on him */
    static final java.util.concurrent.atomic.AtomicInteger lostHits = new java.util.concurrent.atomic.AtomicInteger(),
            guardOnLost = new java.util.concurrent.atomic.AtomicInteger();
    private static boolean listening;

    /**
     * (lagfix, JJ's playtest) The Wreck Cove Rescue played through with the real giants: a player reaches the lost
     * giant on the beach and keeps near him (a few blocks behind); he walks the shore road to the harbour by himself
     * (the escort step), the round is won by his hook, and on the way the guard and he never fight each other and he
     * never hurts the player.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 24000, batch = "advg_cove")
    public void theLostGiantWalksTheShoreRoadHomeInPeace(GameTestHelper h) {
        if (!MinigameGiantsGameTests.real(h)) return;
        if (!listening) {
            listening = true;
            net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register(
                    (e, src, base, taken, blocked) -> {
                        if (taken <= 0) return;
                        var by = src.getEntity();
                        boolean fromLost = by != null && by.getTags().contains("adv_lost");
                        if (e instanceof ServerPlayer && fromLost) lostHits.incrementAndGet();
                        if (e.getTags().contains("adv_lost") && by != null && by.getTags().contains("adv_guard"))
                            guardOnLost.incrementAndGet();
                    });
        }
        lostHits.set(0);
        guardOnLost.set(0);
        MinecraftServer s = MinigameGiantsGameTests.fresh(h);
        AdvData.get(s).s = new AdvData.State();
        Adventure.forget();
        ServerPlayer p = MinigameGameTests.join(h, "advcovereal", false).p();
        h.assertTrue(Adventure.play(s, "Tester", AdvMaps.BUILT_IN, false, 1).ok(), "opened");
        h.assertTrue(Minigames.join(p, false).ok(), "joined");
        Minigames.now(s, "Tester");
        LivingEntity[] lost = {null};
        double[] startX = {0}, minShare = {1};
        int[] ticks = {0};
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(MinigameGiantsGameTests.game().phase() == MgGame.Phase.PLAY,
                        "playing"))
                .thenExecute(() -> MinigameGameTests.arrived(p))
                .thenWaitUntil(() -> {
                    MgGame g = MinigameGiantsGameTests.game();
                    for (var k : g.giants.standing()) {
                        var e = g.giants.entity(k);
                        if (e != null && e.getTags().contains("adv_lost")) lost[0] = e;
                    }
                    var rs = Adventure.roundState();
                    h.assertTrue(lost[0] != null && rs != null && rs.runs.size() == 2, "both map giants stand");
                })
                .thenExecute(() -> startX[0] = lost[0].getX())
                // the player walks with him: a few blocks behind (east of) him, once a second, until he's home
                .thenWaitUntil(() -> {
                    MgGame g = Minigames.current();
                    if (g == null || g.phase() != MgGame.Phase.PLAY) return;
                    if (++ticks[0] % 20 == 0 && !lost[0].isRemoved() && g.level != null) {
                        ServerLevel l = g.level;
                        double x = lost[0].getX() + 6, z = lost[0].getZ();
                        p.teleportTo(l, x, l.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x),
                                (int) Math.floor(z)), z, 90, 0);
                        p.setHealth(p.getMaxHealth());
                        minShare[0] = Math.min(minShare[0], lost[0].getHealth() / Math.max(1, lost[0].getMaxHealth()));
                    }
                    h.assertTrue(false, "on the road: x " + Math.round(lost[0].getX()) + " (from "
                            + Math.round(startX[0]) + ")");
                })
                .thenExecute(() -> {
                    var res = MgData.get(s).s.results;
                    String line = res.isEmpty() ? "" : res.get(res.size() - 1).line;
                    h.assertTrue(line.contains("home safe"), "won by reaching the harbour: " + line);
                    h.assertTrue(lostHits.get() == 0, "the lost giant never hurt the player: " + lostHits.get());
                    h.assertTrue(guardOnLost.get() == 0, "the guard never hurt him: " + guardOnLost.get());
                    h.assertTrue(minShare[0] > 0.95, "he was never in a fight: health down to " + minShare[0]);
                    MinigameGiantsGameTests.done(h, p);
                });
    }
}
