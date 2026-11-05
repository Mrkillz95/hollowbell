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
}
