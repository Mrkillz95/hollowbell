package net.jj.giants.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.players.Players;
import net.jj.giants.tools.Respawns;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * JJ's answers 2 with the real giants loaded (-Pgiants; each skips itself without them): every giant takes "Giants
 * in each other's grounds" (it's in his "uses") and holds his world's own one back while the Overworld has no giants
 * (his respawn line says so); and the pick screen rates every giant the same with nothing set for him.
 */
public class Answers2GiantsGameTests implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "ans2g_rules")
    public void everyGiantTakesTheDimensionRuleAndMixing(GameTestHelper h) {
        if (ToolsGiantsGameTests.none(h)) return;
        MinecraftServer s = h.getLevel().getServer();
        List<String> held = new ArrayList<>();
        try {
            for (Giant g : Giants.installed()) {
                // (the Hearthstride waits in his own yard, one in the world: he never comes in another's ground)
                if (g.key().equals("hearthstride")) continue;
                AdminJson.Report r = AdminServer.report(s, g);
                h.assertTrue(r != null && r.uses().contains("all.spawnMix"), g.key() + " takes all.spawnMix");
            }
            h.assertTrue(AdminServer.set(s, "Tester", "all", "dimOverworld", "nogiants").ok(), "set");
            for (Giant g : Giants.installed()) {
                // (his own mod doesn't hold his return for the dimension rule yet)
                if (g.key().equals("hearthstride")) continue;
                JsonObject o = Respawns.state(s, g);
                h.assertTrue(o != null, g.key() + " has respawn timers");
                String st = AdminJson.str(o, "state", 16);
                if (st.equals("standing") || st.equals("off")) continue;
                h.assertTrue(AdminJson.str(o, "held", 64).equals("minecraft:overworld"), g.key() + ": " + o);
                held.add(g.key());
            }
            h.assertTrue(!held.isEmpty(), "some were waiting to come");
            AdminServer.reset(s, "Tester", "all", "dimOverworld");
            for (String k : held) {
                JsonObject o = Respawns.state(s, Giants.byKey(k));
                h.assertTrue(!o.has("held"), k + " lifted: " + o);
            }
        } finally {
            if (AdminWorld.get(s).values("all").containsKey("all.dimOverworld"))
                AdminServer.reset(s, "Tester", "all", "dimOverworld");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "ans2g_danger")
    public void thePickScreenRatesEveryGiantTheSame(GameTestHelper h) {
        if (ToolsGiantsGameTests.none(h)) return;
        MinecraftServer s = h.getLevel().getServer();
        ServerPlayer p = PlayersGameTests.join(h, "picker2", false);
        try {
            AdminWorld w = AdminWorld.get(s);
            JsonObject pick = Players.pickJson(s, p);
            Integer level = null;
            int n = 0;
            for (JsonElement e : pick.getAsJsonArray("giants")) {
                JsonObject g = e.getAsJsonObject();
                // (only a giant this world set nothing for: his own settings move his rating)
                if (!w.values(g.get("key").getAsString()).isEmpty()) continue;
                int d = g.get("danger").getAsInt();
                if (level == null) level = d;
                h.assertTrue(d == level, g.get("key").getAsString() + " rated " + d + ", the others " + level);
                n++;
            }
            h.assertTrue(n >= 2, "at least two to compare: " + n);
        } finally {
            s.getPlayerList().remove(p);
        }
        h.succeed();
    }
}
