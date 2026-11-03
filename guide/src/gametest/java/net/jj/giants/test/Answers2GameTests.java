package net.jj.giants.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.faketools.GiantsBridge;
import net.jj.giants.admin.AdminBook;
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.admin.AdminTransfer;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.fun.FunWorld;
import net.jj.giants.net.AdminBookStatePayload;
import net.jj.giants.players.Players;
import net.jj.giants.tools.Events;
import net.jj.giants.tools.Respawns;
import net.jj.giants.tools.Tools;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.GameType;

/**
 * JJ's answers 2 (Nov 3, ADMIN_BRIDGE.md 22.16 and 23.6), the Guide's side with stand-in giants: Home keeps the
 * preset's name while an event or a schedule runs (#11), the "no giants" and mixing words on the respawn timers (#13,
 * #17), "Giants in each other's grounds" as an ordinary all-giants setting that presets name (#17), moderators may
 * Watch while the admins allow it (#15), and the pick screen's danger the same for every giant (JJ: they're
 * balanced). Each test has its own batch and puts the world back. Left out with the real giants loaded.
 */
public class Answers2GameTests implements FabricGameTest {

    private static String preset(MinecraftServer s) {
        return Tools.presetName(s);
    }

    private static JsonObject home(MinecraftServer s) {
        AdminBookStatePayload p = AdminBook.home(s);
        return JsonParser.parseString(p.json()).getAsJsonObject();
    }

    private static void set(GameTestHelper h, MinecraftServer s, String id, String v) {
        AdminServer.Result r = AdminServer.set(s, "Tester", "all", id, v);
        h.assertTrue(r.ok(), "set " + id + ": " + r.lines());
    }

    // #11

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "ans2_preset")
    public void homeKeepsThePresetsNameDuringAnEvent(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = ToolsGameTests.fresh(h);
        h.assertTrue(preset(s).equals("Classic"), "nothing set: " + preset(s));
        // a player or server setting on Classic: still Classic
        set(h, s, "votes", "false");
        h.assertTrue(preset(s).equals("Classic"), "a player setting: " + preset(s));
        // an event on Classic: Classic, and Home says the event beside it
        AdminServer.Result r = Events.start(s, "Tester", "war", 30);
        h.assertTrue(r.ok(), "war: " + r.lines());
        h.assertTrue(!AdminWorld.get(s).values("faketools").isEmpty(), "the war set his values");
        h.assertTrue(preset(s).equals("Classic"), "an event: " + preset(s));
        JsonObject o = home(s);
        h.assertTrue(o.get("preset").getAsString().equals("Classic")
                && o.getAsJsonObject("tools").getAsJsonObject("event").get("name").getAsString().equals("Giant war"),
                "Home: " + o.get("preset") + " " + o.get("tools"));
        // an admin's own change during it: that is Custom
        set(h, s, "health", "2");
        h.assertTrue(preset(s).equals("Custom") || preset(s).endsWith(", changed"), "a real change: " + preset(s));
        Events.stop(s, "Tester");
        AdminServer.reset(s, "Tester", "all", "health");
        // a preset under the event: its name
        int n = AdminTransfer.apply(s, "Tester", AdminTransfer.plan(s, AdminTransfer.preset("titans")));
        h.assertTrue(n > 0 && preset(s).equals("Titans"), "Titans: " + preset(s));
        h.assertTrue(Events.start(s, "Tester", "siege", 20).ok(), "siege");
        h.assertTrue(preset(s).equals("Titans"), "Titans under a siege: " + preset(s));
        Events.stop(s, "Tester");
        h.assertTrue(preset(s).equals("Titans"), "after: " + preset(s));
        ToolsGameTests.done(h);
    }

    // #13 and #17 on the respawn timers

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "ans2_respawn")
    public void respawnTimersSayHeldAndWhere(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = ToolsGameTests.fresh(h);
        GiantsBridge.respawnHeld = "minecraft:overworld";
        String line = Respawns.lines(s).lines().get(1);
        h.assertTrue(line.contains("waits: giants may not be in the Overworld"), line);
        GiantsBridge.respawnHeld = "";
        GiantsBridge.respawnIn = "faketools";
        line = Respawns.lines(s).lines().get(1);
        h.assertTrue(line.contains("in the Faketools's ground, once someone is there"), line);
        GiantsBridge.respawnIn = "";
        line = Respawns.lines(s).lines().get(1);
        h.assertTrue(!line.contains("waits") && !line.contains("ground"), "as before: " + line);
        // the dimension setting's words say the world's own one waits now
        AdminSetting d = AdminCatalog.shared("all.dimOverworld");
        h.assertTrue(d != null && d.what().contains("don't come back there until it's allowed again"), "" + d);
        ToolsGameTests.done(h);
    }

    // #17

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "ans2_mix")
    public void giantsInEachOthersGroundsIsAnOrdinarySetting(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = ToolsGameTests.fresh(h);
        AdminSetting m = AdminCatalog.shared("all.spawnMix");
        h.assertTrue(m != null && m.type() == AdminSetting.Type.BOOL && m.cat().equals("spawning")
                && !m.def().getAsBoolean(), "off to start with, spawning: " + m);
        h.assertTrue(!AdminCatalog.multiplier(m) && !AdminCatalog.player(m), "a plain switch");
        h.assertTrue(AdminCatalog.multiplier(AdminCatalog.shared("all.health")), "health is a multiplier");
        // set: no "x", sent to the giants, Home and the danger still work, ", changed"-able like any setting
        set(h, s, "spawnMix", "true");
        h.assertTrue(GiantsBridge.all.has("all.spawnMix") && GiantsBridge.all.get("all.spawnMix").getAsBoolean(),
                "sent: " + GiantsBridge.all);
        String said = AdminServer.get(s, "all", "spawnMix").lines().get(0);
        h.assertTrue(said.contains(" is on") && !said.contains(" x"), "shown plainly: " + said);
        h.assertTrue(home(s).has("danger"), "Home");
        h.assertTrue(preset(s).equals("Custom") || preset(s).endsWith(", changed"), "its own: " + preset(s));
        // all worlds: refused, in its own words
        AdminServer.Result r = AdminServer.setDefault(s, "Tester", "all", "spawnMix", "true");
        h.assertTrue(!r.ok() && r.lines().get(0).contains("all-giants settings are only kept for each world"),
                "" + r.lines());
        // Nomads and Wild Sizes name it; Classic puts it back
        for (String p : List.of("nomads", "wild_sizes")) {
            var b = AdminTransfer.preset(p);
            h.assertTrue(b.values().get("all").get("all.spawnMix").getAsBoolean(), p + " names it");
        }
        AdminTransfer.apply(s, "Tester", AdminTransfer.plan(s, AdminTransfer.preset("classic")));
        h.assertTrue(!AdminWorld.get(s).values("all").containsKey("all.spawnMix"), "Classic: off again");
        AdminTransfer.apply(s, "Tester", AdminTransfer.plan(s, AdminTransfer.preset("nomads")));
        h.assertTrue(AdminWorld.get(s).values("all").get("all.spawnMix").getAsBoolean(), "Nomads: on");
        h.assertTrue(preset(s).equals("Nomads"), "Nomads: " + preset(s));
        ToolsGameTests.done(h);
    }

    // #15

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "ans2_modwatch")
    public void moderatorsMayWatchWhileAllowed(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = FunGameTests.fresh(h);
        AdminWorld.get(s).clear("all");
        AdminSetting w = AdminCatalog.shared("all.modWatch");
        h.assertTrue(w != null && w.cat().equals("server") && w.def().getAsBoolean(), "on to start with: " + w);
        BlockPos at = FunGameTests.spot(h, 30);
        ServerPlayer mod = PlayersGameTests.join(h, "moddy2", false);
        mod.teleportTo(h.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        mod.setGameMode(GameType.SURVIVAL);
        AdminWorld.get(s).addMod(mod.getUUID(), "moddy2");
        IronGolem g = net.jj.fakefun.GiantsBridge.spawn(h.getLevel(), at.getX() + 20.5, at.getY(), at.getZ() + 0.5);
        h.runAfterDelay(5, () -> {
            String ask = "{\"do\":\"spectate\",\"giant\":\"fakefun\",\"id\":\"" + g.getStringUUID()
                    + "\",\"mode\":\"free\"}";
            var r = AdminBook.request(s, mod, AdminBook.role(mod), ask);
            h.assertTrue(r.ok() && mod.isSpectator(), "a moderator watches: " + r.lines());
            JsonObject role = JsonParser.parseString(roleOf(mod)).getAsJsonObject();
            h.assertTrue(role.get("watch").getAsBoolean() && role.getAsJsonObject("watching").get("id")
                    .getAsString().equals(g.getStringUUID()), "the book knows: " + role);
            r = AdminBook.request(s, mod, AdminBook.role(mod), "{\"do\":\"spectatestop\"}");
            h.assertTrue(r.ok() && !mod.isSpectator(), "and stops: " + r.lines());
            // other fun is still for admins
            r = AdminBook.request(s, mod, AdminBook.role(mod), "{\"do\":\"playas\",\"giant\":\"fakefun\",\"id\":\""
                    + g.getStringUUID() + "\"}");
            h.assertTrue(!r.ok() && r.lines().get(0).contains("Only admins"), "play as: " + r.lines());
            // turned off: refused, and one watching is brought back
            r = AdminBook.request(s, mod, AdminBook.role(mod), ask);
            h.assertTrue(r.ok(), "watching again");
            AdminServer.Result off = AdminServer.set(s, "Tester", "all", "modWatch", "false");
            h.assertTrue(off.ok(), "" + off.lines());
            h.assertTrue(!JsonParser.parseString(roleOf(mod)).getAsJsonObject().get("watch").getAsBoolean(),
                    "the book knows it's off");
        });
        h.runAfterDelay(40, () -> {
            h.assertTrue(!mod.isSpectator() && !FunWorld.get(s).s.watching.containsKey(mod.getStringUUID()),
                    "brought back when it went off");
            var r = AdminBook.request(s, mod, AdminBook.role(mod), "{\"do\":\"spectate\",\"giant\":\"fakefun\","
                    + "\"id\":\"" + g.getStringUUID() + "\",\"mode\":\"free\"}");
            h.assertTrue(!r.ok() && r.lines().get(0).contains("turned off watching"), "refused: " + r.lines());
            AdminServer.reset(s, "Tester", "all", "modWatch");
            AdminWorld.get(s).removeMod(mod.getUUID());
            g.discard();
            FunGameTests.unspot(h, at);
            FunGameTests.done(h, mod);
        });
    }

    private static String roleOf(ServerPlayer p) {
        String[] out = new String[1];
        var was = AdminBook.sender;
        AdminBook.sender = (q, st) -> {
            if (st.kind().equals(AdminBookStatePayload.ROLE)) out[0] = st.json();
        };
        try {
            AdminBook.sendAll(p.getServer(), p);
        } finally {
            AdminBook.sender = was;
        }
        return out[0];
    }

    // the pick screen's danger (JJ, Nov 3)

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "ans2_danger")
    public void everyGiantStartsAsDangerousAsTheOthers(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        for (var e : Players.BASE_DANGER.entrySet())
            h.assertTrue(e.getValue() == 3, e.getKey() + " starts at Normal: " + e.getValue());
        h.assertTrue(Players.BASE_DANGER.keySet().equals(Set.copyOf(AdminCatalog.KEYS)), "all eight");
        h.succeed();
    }
}
