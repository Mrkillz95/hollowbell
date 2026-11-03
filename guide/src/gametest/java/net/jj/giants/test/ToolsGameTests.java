package net.jj.giants.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.faketools.GiantsBridge;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminBook;
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminInsight;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminTransfer;
import net.jj.giants.admin.AdminUndo;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.net.AdminBookStatePayload;
import net.jj.giants.players.PlayerData;
import net.jj.giants.players.Votes;
import net.jj.giants.tools.Areas;
import net.jj.giants.tools.Events;
import net.jj.giants.tools.PackMode;
import net.jj.giants.tools.Profiles;
import net.jj.giants.tools.Respawns;
import net.jj.giants.tools.Schedules;
import net.jj.giants.tools.Stats;
import net.jj.giants.tools.Tools;
import net.jj.giants.tools.ToolsAdmin;
import net.jj.giants.tools.ToolsData;
import net.jj.giants.tools.Watch;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Part 6, "Server tools" (ADMIN_BRIDGE.md §22), the Guide's side with a stand-in giant (net.jj.faketools): pause all,
 * quiet mode, events, schedules, areas and dimension rules, alerts, stats, respawn timers, profiles, pack mode, the
 * book's page and its requests, and the commands. Each test has its own batch and puts the world back.
 */
public class ToolsGameTests implements FabricGameTest {
    static final Giant FAKE = new Giant("faketools", "faketools", "faketools", "net.jj.faketools.GiantsBridge",
            List.of(), Set.of());

    static MinecraftServer fresh(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        AdminServer.useGiants(List.of(FAKE));
        AdminWorld w = AdminWorld.get(s);
        for (String g : List.of("all", "faketools")) w.clear(g);
        w.forgetExtras();
        GiantsBridge.reset();
        ToolsData.get(s).s = new ToolsData.State();
        Tools.forget();
        Schedules.today = null;
        PackMode.packForTests(null);
        AdminServer.push(s, w);
        AdminTransfer.forget();
        AdminBook.forget();
        return s;
    }

    static void done(GameTestHelper h, ServerPlayer... players) {
        MinecraftServer s = h.getLevel().getServer();
        for (ServerPlayer p : players) {
            s.getPlayerList().getOps().remove(p.getGameProfile());
            s.getPlayerList().remove(p);
        }
        AdminWorld w = AdminWorld.get(s);
        for (String g : List.of("all", "faketools")) w.clear(g);
        ToolsData.get(s).s = new ToolsData.State();
        Tools.forget();
        Schedules.today = null;
        PackMode.packForTests(null);
        PlayerData.get(s).s = new PlayerData.State();
        AdminServer.push(s, w);
        AdminServer.useGiants(null);
        h.succeed();
    }

    private static void set(GameTestHelper h, MinecraftServer s, String id, String v) {
        AdminServer.Result r = AdminServer.set(s, "Tester", "all", id, v);
        h.assertTrue(r.ok(), "set " + id + ": " + r.lines());
    }

    private static boolean askedAny(String start) {
        for (String a : GiantsBridge.asked) if (a.startsWith(start)) return true;
        return false;
    }

    private static String allValue(MinecraftServer s, String id) {
        JsonElement v = AdminWorld.get(s).values("all").get(id);
        return v == null ? null : v.toString();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "tools_settings")
    public void theServerToolsSettingsAreOrdinarySettings(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        // every one is in the all-giants list, category server, live, kept for each world only
        List<String> ids = new ArrayList<>();
        for (var x : AdminCatalog.servers()) {
            ids.add(x.id());
            h.assertTrue(x.cat().equals("server") && x.when().equals("live"), x.id());
            h.assertTrue(AdminCatalog.shared(x.id()) != null, "listed: " + x.id());
        }
        h.assertTrue(ids.containsAll(List.of("all.paused", "all.quiet", "all.spawnClear", "all.dimOverworld",
                "all.dimNether", "all.dimEnd", "all.alertFights", "all.alertRadius", "all.alertGrief",
                "all.alertGriefBlocks", "all.alertDeaths", "all.alertAreas")), "" + ids);
        // set, logged, undone, locked, refused for all worlds, exported
        set(h, s, "paused", "on");
        h.assertTrue(Tools.on(s, "paused"), "paused");
        AdminServer.Result all = AdminServer.setDefault(s, "Tester", "all", "paused", "on");
        h.assertTrue(!all.ok() && all.lines().get(0).contains("server tools"), "all worlds: " + all.lines());
        List<AdminWorld.Change> log = AdminWorld.get(s).log();
        AdminWorld.Change c = log.get(log.size() - 1);
        h.assertTrue(c.id().equals("all.paused") && AdminUndo.undoable(c), "logged: " + c);
        AdminServer.Result u = AdminUndo.undo(s, "Tester", log.size() - 1, c.time());
        h.assertTrue(u.ok() && !Tools.on(s, "paused"), "undone: " + u.lines());
        AdminServer.lock(s, "Alice", "a", "all", "quiet");
        h.assertTrue(!AdminServer.set(s, "Bob", "all", "quiet", "on").ok(), "locked against others");
        AdminServer.unlock(s, "Alice", "all", "quiet");
        set(h, s, "dimNether", "nogiants");
        String export = AdminTransfer.encode(AdminTransfer.current(s));
        AdminTransfer.Bundle b = AdminTransfer.decode(export);
        h.assertTrue(b != null && b.values().get("all").containsKey("all.dimNether"), "exported");
        // a preset leaves them as they are; the preset name doesn't read "changed" for them
        AdminTransfer.apply(s, "Tester", AdminTransfer.plan(s, AdminTransfer.preset("titans")));
        set(h, s, "quiet", "on");
        h.assertTrue("\"nogiants\"".equals(allValue(s, "all.dimNether")), "kept by the preset");
        h.assertTrue(AdminInsight.presetName(AdminWorld.get(s).log(), true).equals("Titans"),
                "not changed: " + AdminInsight.presetName(AdminWorld.get(s).log(), true));
        // the multipliers' numbers leave them out (no crash on a switch)
        h.assertTrue(AdminBook.allValuesForTests(AdminWorld.get(s)).get("all.health") == 2.5, "the numbers");
        done(h);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "tools_pause")
    public void pauseFreezesEveryGiantAndLetsThemGo(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        GiantsBridge.put("g1", "here", "minecraft:overworld", 1000, 1000);
        GiantsBridge.put("g2", "away", "minecraft:overworld", 5000, 5000);
        set(h, s, "paused", "on");
        Watch.lookNow(s);
        h.assertTrue(GiantsBridge.asked.contains("freeze on"), "frozen: " + GiantsBridge.asked);
        h.assertTrue(ToolsData.get(s).s.frozen.contains("g1") && !ToolsData.get(s).s.frozen.contains("g2"),
                "who: " + ToolsData.get(s).s.frozen);
        // back from out of the world while paused: frozen too
        GiantsBridge.rows.get("g2").addProperty("state", "here");
        GiantsBridge.asked.clear();
        Watch.lookNow(s);
        h.assertTrue(GiantsBridge.asked.contains("freeze on") && ToolsData.get(s).s.frozen.contains("g2"), "g2");
        // let them go: unfrozen, and none left to unfreeze
        set(h, s, "paused", "off");
        GiantsBridge.asked.clear();
        Watch.lookNow(s);
        h.assertTrue(GiantsBridge.asked.contains("freeze off"), "unfrozen: " + GiantsBridge.asked);
        h.assertTrue(ToolsData.get(s).s.frozen.isEmpty(), "none left: " + ToolsData.get(s).s.frozen);
        // one frozen while unloaded is unfrozen when he's next seen standing
        ToolsData.get(s).s.frozen.add("g3");
        GiantsBridge.asked.clear();
        Watch.lookNow(s);
        h.assertTrue(!GiantsBridge.asked.contains("freeze off"), "not seen yet");
        GiantsBridge.put("g3", "here", "minecraft:overworld", 0, 0);
        Watch.lookNow(s);
        h.assertTrue(GiantsBridge.asked.contains("freeze off") && ToolsData.get(s).s.frozen.isEmpty(), "g3 let go");
        // nothing on: nothing asked
        GiantsBridge.asked.clear();
        set(h, s, "alertFights", "off");
        Watch.lookNow(s);
        h.assertTrue(GiantsBridge.asked.isEmpty(), "no freezing when nothing is on: " + GiantsBridge.asked);
        done(h);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "tools_quiet")
    public void quietModeTurnsTheGiantsChatOff(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        h.assertTrue(!GiantsBridge.all.has("all.chatMoves"), "nothing sent to start with");
        set(h, s, "quiet", "on");
        h.assertTrue(Tools.quiet(s), "quiet");
        for (String id : Tools.QUIETED)
            h.assertTrue(GiantsBridge.all.has(id) && !GiantsBridge.all.get(id).getAsBoolean(), "off: " + id);
        h.assertTrue(AdminWorld.get(s).values("all").get("all.chatMoves") == null, "the world's own switch untouched");
        set(h, s, "quiet", "off");
        h.assertTrue(!GiantsBridge.all.has("all.chatMoves"), "back as the world has them: " + GiantsBridge.all);
        done(h);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "tools_events")
    public void eventsChangeThingsForAWhileThenPutThemBack(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        AdminServer.Result r = Events.start(s, "Tester", "war", 10);
        h.assertTrue(r.ok(), "war: " + r.lines());
        h.assertTrue(GiantsBridge.values.get("seekGiants").getAsBoolean()
                && GiantsBridge.values.get("meetCooldown").getAsInt() == 1, "his values: " + GiantsBridge.values);
        h.assertTrue(askedAny("wake"), "woken");
        h.assertTrue(!Events.start(s, "Tester", "siege", 10).ok(), "one at a time");
        // an admin changes one it set meanwhile: that one stays
        AdminServer.set(s, "Tester", "faketools", "meetCooldown", "5");
        AdminServer.Result st = Events.stop(s, "Tester");
        h.assertTrue(st.ok() && ToolsData.get(s).s.event == null, "stopped: " + st.lines());
        h.assertTrue(!AdminWorld.get(s).values("faketools").containsKey("seekGiants"), "put back");
        h.assertTrue(AdminWorld.get(s).values("faketools").get("meetCooldown").getAsInt() == 5, "the admin's stays");
        // Boss Rush hour: the all-giants ones, and the fallen brought back; it ends by itself on time
        r = Events.start(s, "Tester", "bossrush", 1);
        h.assertTrue(r.ok() && "0.6".equals(allValue(s, "all.health")), "boss rush: " + r.lines());
        h.assertTrue(GiantsBridge.asked.contains("respawnact now"), "brought back: " + GiantsBridge.asked);
        ToolsData.get(s).s.event.left = 20;
        Events.tick(s);
        h.assertTrue(ToolsData.get(s).s.event == null && allValue(s, "all.health") == null, "ended on time");
        // siege: every giant sent to spawn
        r = Events.start(s, "Tester", "siege", 5);
        BlockPos sp = s.overworld().getSharedSpawnPos();
        h.assertTrue(GiantsBridge.asked.contains("goto " + sp.getX() + " " + sp.getZ()), "to spawn: "
                + GiantsBridge.asked);
        h.assertTrue("\"hunter\"".equals(GiantsBridge.values.get("temper").toString()), "hunting");
        Events.stop(s, "Tester");
        // logged with undo; Home's preset doesn't change for them
        boolean started = false;
        for (AdminWorld.Change c : AdminWorld.get(s).log())
            if (c.id().equals("tools:event") && AdminUndo.undoable(c)) started = true;
        h.assertTrue(started, "logged");
        h.assertTrue(AdminInsight.presetName(AdminWorld.get(s).log(), true).equals("Custom")
                || AdminInsight.presetName(AdminWorld.get(s).log(), true).equals("Classic"), "the preset name");
        h.assertTrue(!Events.start(s, "Tester", "party", 5).ok() && !Events.start(s, "Tester", "war", 0).ok(),
                "nonsense");
        done(h);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "tools_schedules")
    public void schedulesGoOnAndOffByThemselves(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        set(h, s, "damage", "2");
        Schedules.today = LocalDate.of(2026, 11, 7); // a Saturday
        AdminServer.Result r = Schedules.add(s, "Tester", "titans", "weekends", "");
        h.assertTrue(r.ok() && r.lines().get(0).contains("weekends"), "added: " + r.lines());
        int snaps = AdminWorld.get(s).snapshots().size();
        Schedules.tick(s);
        h.assertTrue(ToolsData.get(s).s.schedule != null && "2.5".equals(allValue(s, "all.health")), "on: "
                + allValue(s, "all.health"));
        h.assertTrue(AdminWorld.get(s).snapshots().size() == snaps + 1, "a snapshot first");
        // Monday: off again, and what it changed is back
        Schedules.today = LocalDate.of(2026, 11, 9);
        Schedules.tick(s);
        h.assertTrue(ToolsData.get(s).s.schedule == null, "off");
        h.assertTrue(allValue(s, "all.health") == null && "2".equals(allValue(s, "all.damage").replace(".0", "")),
                "back: " + allValue(s, "all.damage"));
        // the other kinds, and the words
        h.assertTrue(Schedules.add(s, "Tester", "gentle_giants", "days", "mon,wed").ok(), "days");
        h.assertTrue(Schedules.add(s, "Tester", "boss_rush", "date", "12-25").ok(), "every year");
        h.assertTrue(Schedules.add(s, "Tester", "lite", "fullmoon", "").ok(), "full moons");
        // (turned off at once: the test world's night may well be a full moon's)
        h.assertTrue(Schedules.turn(s, "Tester", "4", false).ok(), "full moons off");
        h.assertTrue(!Schedules.add(s, "Tester", "lite", "date", "13-40").ok(), "no such date");
        h.assertTrue(!Schedules.add(s, "Tester", "nonsense", "weekends", "").ok(), "no such preset");
        h.assertTrue(!Schedules.add(s, "Tester", "lite", "days", "funday").ok(), "no such day");
        List<ToolsData.Schedule> all = ToolsData.get(s).s.schedules;
        h.assertTrue(Schedules.when(all.get(1)).equals("Mondays and Wednesdays"), Schedules.when(all.get(1)));
        h.assertTrue(Schedules.when(all.get(2)).equals("Dec 25 (every year)"), Schedules.when(all.get(2)));
        // Monday: the first one due goes on (Gentle Giants); turned off it goes off
        Schedules.tick(s);
        h.assertTrue(ToolsData.get(s).s.schedule != null && ToolsData.get(s).s.schedule.name.equals("Gentle Giants"),
                "Mondays");
        h.assertTrue(Schedules.turn(s, "Tester", "2", false).ok() && ToolsData.get(s).s.schedule == null, "off");
        h.assertTrue(Schedules.remove(s, "Tester", "1").ok() && all.size() == 3, "removed");
        // a modpack that keeps its preset: no schedules
        PackMode.packForTests(new PackMode.Pack("Test Pack", "titans", true));
        h.assertTrue(!Schedules.add(s, "Tester", "lite", "weekends", "").ok(), "the pack keeps its preset");
        done(h);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "tools_areas")
    public void areasAndDimensionsKeepGiantsOut(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos sp = s.overworld().getSharedSpawnPos();
        GiantsBridge.put("near", "here", "minecraft:overworld", sp.getX() + 20, sp.getZ());
        GiantsBridge.put("far", "here", "minecraft:overworld", sp.getX() + 5000, sp.getZ());
        GiantsBridge.put("nether", "here", "minecraft:the_nether", 0, 0);
        set(h, s, "spawnClear", "64");
        Watch.lookNow(s);
        h.assertTrue(GiantsBridge.asked.contains("liveact home near"), "sent home: " + GiantsBridge.asked);
        h.assertTrue(!askedAny("liveact home far") && !askedAny("liveact remove"), "only him");
        // still there a minute later: taken away
        Watch.sentOutForTests("faketools/near", 1300, s);
        Watch.lookNow(s);
        h.assertTrue(GiantsBridge.asked.contains("liveact remove near") && !GiantsBridge.rows.containsKey("near"),
                "taken away: " + GiantsBridge.asked);
        // a dimension with no giants: taken away at once
        set(h, s, "dimNether", "nogiants");
        Watch.lookNow(s);
        h.assertTrue(GiantsBridge.asked.contains("liveact remove nether"), "out of the Nether");
        h.assertTrue(ToolsData.get(s).s.alerts.stream().anyMatch(a -> a.kind.equals("area")), "told");
        // an area drawn round an admin: a giant there is sent home
        ServerPlayer admin = PlayersGameTests.join(h, "Areas", true);
        admin.teleportTo(s.overworld(), sp.getX() + 3000.5, 100, sp.getZ() + 0.5, 0f, 0f);
        AdminServer.Result r = Areas.add(s, admin, "keepout", 100, "Town");
        h.assertTrue(r.ok(), "added: " + r.lines());
        GiantsBridge.put("town", "here", "minecraft:overworld", sp.getX() + 3050, sp.getZ());
        Watch.lookNow(s);
        h.assertTrue(GiantsBridge.asked.contains("liveact home town"), "town: " + GiantsBridge.asked);
        h.assertTrue(!Areas.add(s, admin, "moat", 100, "").ok() && !Areas.add(s, admin, "safe", 3, "").ok(),
                "nonsense");
        // a safe area: a player in it is an ignored player for the giants while there
        r = Areas.add(s, admin, "safe", 50, "Home");
        h.assertTrue(r.ok(), "safe: " + r.lines());
        Watch.lookNow(s);
        h.assertTrue(GiantsBridge.rules.has(admin.getStringUUID())
                && GiantsBridge.rules.getAsJsonObject(admin.getStringUUID()).get("ignored").getAsBoolean(),
                "ignored there: " + GiantsBridge.rules);
        admin.teleportTo(s.overworld(), sp.getX() + 9000.5, 100, sp.getZ() + 0.5, 0f, 0f);
        Watch.lookNow(s);
        h.assertTrue(!GiantsBridge.rules.has(admin.getStringUUID()), "not once out: " + GiantsBridge.rules);
        h.assertTrue(Areas.edit(s, "Tester", "Home", 80, "keepout").ok()
                && ToolsData.get(s).s.areas.get(1).r == 80, "edited");
        h.assertTrue(Areas.remove(s, "Tester", "1").ok() && ToolsData.get(s).s.areas.size() == 1, "removed");
        done(h, admin);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "tools_alerts")
    public void alertsAndStatsAreKept(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos sp = s.overworld().getSharedSpawnPos();
        GiantsBridge.put("f", "here", "minecraft:overworld", sp.getX() + 100, sp.getZ()).addProperty("fighting",
                true);
        Watch.lookNow(s);
        h.assertTrue(ToolsData.get(s).s.alerts.size() == 1 && ToolsData.get(s).s.alerts.get(0).text
                .contains("fighting 100 blocks from spawn"), "a fight near spawn: " + ToolsData.get(s).s.alerts.size());
        Watch.lookNow(s);
        h.assertTrue(ToolsData.get(s).s.alerts.size() == 1, "told once in five minutes");
        set(h, s, "alertRadius", "64");
        GiantsBridge.put("g", "here", "minecraft:overworld", sp.getX() + 200, sp.getZ()).addProperty("fighting", true);
        Watch.lookNow(s);
        h.assertTrue(ToolsData.get(s).s.alerts.size() == 1, "too far for the alert");
        // heavy griefing: blocks broken while his entities tick, a minute's worth
        Stats.current = "faketools";
        for (int i = 0; i < 250; i++) Stats.blockBroken();
        Stats.current = null;
        h.assertTrue(Stats.brokeForTests("faketools") == 250, "counted");
        Stats.minute(s);
        h.assertTrue(ToolsData.get(s).stat("faketools").blocks == 250, "kept");
        h.assertTrue(ToolsData.get(s).s.alerts.stream().anyMatch(a -> a.kind.equals("grief")), "griefing alert");
        // a fall
        Stats.fell(s, "faketools", true);
        Stats.fell(s, "faketools", false);
        h.assertTrue(ToolsData.get(s).stat("faketools").deaths == 2 && ToolsData.get(s).stat("faketools").slain == 1,
                "falls");
        Stats.costForTests("faketools", 1.25);
        JsonObject page = ToolsAdmin.json(s);
        JsonObject st = page.getAsJsonArray("stats").get(0).getAsJsonObject();
        h.assertTrue(st.get("blocks").getAsLong() == 250 && st.get("ms").getAsDouble() == 1.25
                && st.get("here").getAsInt() == 2, "on the page: " + st);
        h.assertTrue(Stats.reset(s, "Tester").ok() && ToolsData.get(s).s.stats.isEmpty(), "cleared");
        done(h);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "tools_respawn")
    public void respawnTimersAreReadAndSet(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        JsonArray rs = Respawns.json(s);
        JsonObject f = rs.get(0).getAsJsonObject();
        h.assertTrue(f.get("speaks").getAsBoolean() && f.get("state").getAsString().equals("due")
                && f.get("ticks").getAsLong() == 48000, "read: " + f);
        h.assertTrue(Respawns.ticks("now") == 0 && Respawns.ticks("30m") == 36000 && Respawns.ticks("2h") == 144000
                && Respawns.ticks("3d") == 72000 && Respawns.ticks("10") == 12000, "waits");
        AdminServer.Result r = Respawns.act(s, "Tester", "faketools", 0);
        h.assertTrue(r.ok() && GiantsBridge.asked.contains("respawnact now"), "now: " + r.lines());
        r = Respawns.act(s, "Tester", "faketools", 36000);
        h.assertTrue(r.ok() && GiantsBridge.asked.contains("respawnact in 36000"), "in: " + GiantsBridge.asked);
        GiantsBridge.respawnState = "standing";
        r = Respawns.act(s, "Tester", "faketools", 0);
        h.assertTrue(!r.ok() && r.lines().get(0).contains("already out there"), "refused: " + r.lines());
        boolean logged = false;
        for (AdminWorld.Change c : AdminWorld.get(s).log()) if (c.id().equals("tools:respawn")) logged = true;
        h.assertTrue(logged, "in the change log");
        h.assertTrue(!Respawns.act(s, "Tester", "nobody", 0).ok(), "no such giant");
        done(h);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "tools_profile")
    public void aWorldsProfileGoesInWhenItChanges(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        try {
            set(h, s, "health", "2");
            AdminServer.Result r = Profiles.save(s, "Tester");
            h.assertTrue(r.ok() && Files.isRegularFile(Profiles.file(s)), "saved: " + r.lines());
            // unchanged: not put in again
            AdminServer.reset(s, "Tester", "all", "health");
            Profiles.onStart(s);
            h.assertTrue(allValue(s, "all.health") == null, "the same file isn't put in again");
            // changed: put in at the start, as a preset ("Profile <world>")
            set(h, s, "health", "3");
            String text = AdminTransfer.encode(AdminTransfer.current(s));
            AdminServer.reset(s, "Tester", "all", "health");
            Files.writeString(Profiles.file(s), text);
            Profiles.onStart(s);
            h.assertTrue("3".equals(allValue(s, "all.health").replace(".0", "")), "put in: " + allValue(s, "all.health"));
            h.assertTrue(AdminInsight.presetName(AdminWorld.get(s).log(), true).startsWith("Profile "),
                    AdminInsight.presetName(AdminWorld.get(s).log(), true));
            h.assertTrue(ToolsAdmin.json(s).getAsJsonObject("profile").get("exists").getAsBoolean(), "on the page");
            Files.writeString(Profiles.file(s), "rubbish");
            h.assertTrue(!Profiles.applyNow(s, "Tester").ok(), "not a settings string");
            h.assertTrue(Profiles.delete(s, "Tester").ok() && !Files.exists(Profiles.file(s)), "deleted");
            h.assertTrue(!Profiles.applyNow(s, "Tester").ok(), "none to put in");
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                Files.deleteIfExists(Profiles.file(s));
            } catch (java.io.IOException e) {
                // left
            }
        }
        done(h);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "tools_pack")
    public void aModpackStartsWorldsOnItsPresetAndCanKeepIt(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        for (String g : AdminWorld.get(s).giantsWithValues()) AdminWorld.get(s).clear(g);
        ServerPlayer admin = PlayersGameTests.join(h, "PackAdmin", true);
        PackMode.packForTests(new PackMode.Pack("Test Pack", "titans", false));
        PackMode.onStart(s);
        h.assertTrue("2.5".equals(allValue(s, "all.health")), "a world with nothing set takes it");
        AdminServer.reset(s, "Tester", "everything", null);
        PackMode.onStart(s);
        h.assertTrue(allValue(s, "all.health") == null, "only once");
        // locked: it goes in again, and nothing else may be applied
        PackMode.packForTests(new PackMode.Pack("Test Pack", "titans", true));
        set(h, s, "damage", "0.5");
        PackMode.onStart(s);
        h.assertTrue("2.5".equals(allValue(s, "all.health")), "locked: put in");
        AdminBook.request(s, admin, AdminBook.Role.ADMIN, "{\"do\":\"preset\",\"name\":\"lite\"}");
        AdminServer.Result r = AdminBook.request(s, admin, AdminBook.Role.ADMIN, "{\"do\":\"apply\"}");
        h.assertTrue(!r.ok() && r.lines().get(0).contains("keeps the Titans preset"), "no other preset: " + r.lines());
        AdminBook.request(s, admin, AdminBook.Role.ADMIN, "{\"do\":\"preset\",\"name\":\"titans\"}");
        r = AdminBook.request(s, admin, AdminBook.Role.ADMIN, "{\"do\":\"apply\"}");
        h.assertTrue(!r.lines().get(0).contains("keeps the"), "its own may: " + r.lines());
        h.assertTrue(!AdminServer.reset(s, "Tester", "everything", null).ok(), "no everything back");
        h.assertTrue(!Votes.open(s, "Tester", List.of("lite", "titans"), 5, true).ok(), "no votes");
        h.assertTrue(AdminServer.set(s, "Tester", "all", "damage", "1.5").ok(), "single settings still may");
        h.assertTrue(ToolsAdmin.json(s).getAsJsonObject("pack").get("lock").getAsBoolean(), "on the page");
        done(h, admin);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "tools_book")
    public void theBooksServerToolsPageAndItsRequests(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        List<AdminBookStatePayload> got = new ArrayList<>();
        var was = AdminBook.sender;
        AdminBook.sender = (p, page) -> got.add(page);
        ServerPlayer admin = PlayersGameTests.join(h, "ToolsAdmin", true);
        ServerPlayer mod = PlayersGameTests.join(h, "ToolsMod", false);
        try {
            GiantsBridge.put("g1", "here", "minecraft:overworld", 10, 20);
            AdminBook.ask(s, admin, "tools");
            JsonObject page = null;
            for (AdminBookStatePayload p : got) if (p.kind().equals("tools")) page = JsonParser.parseString(p.json())
                    .getAsJsonObject();
            h.assertTrue(page != null, "the page came");
            for (String k : List.of("paused", "events", "schedules", "presets", "areas", "spawn", "giants", "grounds",
                    "players", "stats", "alerts", "respawns", "profile", "pack"))
                h.assertTrue(page.has(k), "has " + k);
            h.assertTrue(page.getAsJsonArray("giants").size() == 1 && page.getAsJsonArray("grounds").size() == 1,
                    "the map's giants and grounds");
            // its requests, each as its command
            AdminServer.Result r = AdminBook.request(s, admin, AdminBook.Role.ADMIN,
                    "{\"do\":\"eventstart\",\"kind\":\"war\",\"minutes\":5}");
            h.assertTrue(r.ok() && ToolsData.get(s).s.event != null, "event: " + r.lines());
            r = AdminBook.request(s, admin, AdminBook.Role.ADMIN, "{\"do\":\"eventstop\"}");
            h.assertTrue(r.ok() && ToolsData.get(s).s.event == null, "stopped");
            r = AdminBook.request(s, admin, AdminBook.Role.ADMIN, "{\"do\":\"areaadd\",\"kind\":\"safe\",\"r\":40}");
            h.assertTrue(r.ok() && ToolsData.get(s).s.areas.size() == 1, "area: " + r.lines());
            r = AdminBook.request(s, admin, AdminBook.Role.ADMIN, "{\"do\":\"respawn\",\"giant\":\"faketools\","
                    + "\"ticks\":0}");
            h.assertTrue(r.ok(), "respawn: " + r.lines());
            r = AdminBook.request(s, admin, AdminBook.Role.ADMIN, "{\"do\":\"maptp\",\"giant\":\"faketools\","
                    + "\"id\":\"g1\"}");
            h.assertTrue(r.ok() && GiantsBridge.asked.contains("liveact tp g1"), "to him: " + r.lines());
            r = AdminBook.request(s, admin, AdminBook.Role.ADMIN, "{\"do\":\"mapground\",\"giant\":\"faketools\"}");
            h.assertTrue(r.ok() && GiantsBridge.asked.contains("home " + admin.getStringUUID()), "his ground");
            r = AdminBook.request(s, admin, AdminBook.Role.ADMIN, "{\"do\":\"schedadd\",\"preset\":\"lite\","
                    + "\"kind\":\"days\",\"when\":\"1,3\"}");
            h.assertTrue(r.ok() && ToolsData.get(s).s.schedules.size() == 1, "schedule: " + r.lines());
            // a moderator: not these
            AdminWorld.get(s).addMod(mod.getUUID(), "ToolsMod");
            r = AdminBook.request(s, mod, AdminBook.Role.MODERATOR, "{\"do\":\"eventstart\",\"kind\":\"war\"}");
            h.assertTrue(!r.ok() && ToolsData.get(s).s.event == null, "moderators can't");
            got.clear();
            AdminBook.ask(s, mod, "tools");
            h.assertTrue(got.stream().noneMatch(p -> p.kind().equals("tools")), "nor see it");
            // Home says what's on
            set(h, s, "paused", "on");
            JsonObject home = JsonParser.parseString(AdminBook.home(s).json()).getAsJsonObject();
            h.assertTrue(home.getAsJsonObject("tools").get("paused").getAsBoolean(), "Home: paused");
            // the commands
            int ok = s.getCommands().getDispatcher().execute("giants admin pause off", s.createCommandSourceStack());
            h.assertTrue(ok == 1 && !Tools.on(s, "paused"), "/giants admin pause off");
            s.getCommands().getDispatcher().execute("giants admin dimension end nogiants", s.createCommandSourceStack());
            h.assertTrue("\"nogiants\"".equals(allValue(s, "all.dimEnd")), "/giants admin dimension");
            s.getCommands().getDispatcher().execute("giants admin event bossrush 3", s.createCommandSourceStack());
            h.assertTrue(ToolsData.get(s).s.event != null && ToolsData.get(s).s.event.left == 3 * 1200,
                    "/giants admin event");
            s.getCommands().getDispatcher().execute("giants admin event stop", s.createCommandSourceStack());
            s.getCommands().getDispatcher().execute("giants admin respawn faketools 2h", s.createCommandSourceStack());
            h.assertTrue(GiantsBridge.asked.contains("respawnact in 144000"), "/giants admin respawn");
            s.getCommands().getDispatcher().execute("giants admin schedule add titans weekends",
                    s.createCommandSourceStack());
            h.assertTrue(ToolsData.get(s).s.schedules.size() == 2, "/giants admin schedule add");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new RuntimeException(e);
        } finally {
            AdminBook.sender = was;
        }
        done(h, admin, mod);
    }
}
