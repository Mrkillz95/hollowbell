package net.jj.giants.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminBook;
import net.jj.giants.admin.AdminCreation;
import net.jj.giants.admin.AdminDraft;
import net.jj.giants.admin.AdminInsight;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminPresets;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.admin.AdminTransfer;
import net.jj.giants.admin.AdminWorld;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;

/**
 * Part 3 (ADMIN_BRIDGE.md §18): the Create World screen's draft (the same checks and words as /giants admin), the
 * draft going into a brand-new world before its first tick, new_world.txt on a dedicated server, the presets Close
 * Quarters and Far Frontier, and with the eight giants' own mods (-Pgiants) the presets taking and the giants keeping
 * the band and the room between their grounds when each chooses his ground as a new world does.
 */
public class CreationGameTests implements FabricGameTest {
    private static final Giant FAKE = fake("fakeadmin"), OLD = fake("fakeold");

    private static Giant fake(String key) {
        return new Giant(key, key, key, "net.jj." + key + ".GiantsBridge", List.of(), Set.of());
    }

    private static MinecraftServer fresh(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        AdminServer.useGiants(List.of(FAKE, OLD));
        AdminWorld w = AdminWorld.get(s);
        for (String g : w.giantsWithValues()) w.clear(g);
        net.jj.fakeadmin.GiantsBridge.reset();
        AdminTransfer.forget();
        return s;
    }

    private static void done(GameTestHelper h) {
        AdminServer.useGiants(null);
        AdminCreation.testDedicated = null;
        AdminCreation.dirOverride = null;
        AdminPresets.dirOverride = null;
        AdminPresets.forget();
        h.succeed();
    }

    private static AdminSetting setting(AdminDraft d, Giant g, String id) {
        return d.report(g).setting(id);
    }

    private static AdminTransfer.Bundle bundle(String json) {
        return AdminTransfer.read(json);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "creation_draft")
    public void theDraftChecksEveryChangeLikeTheCommand(GameTestHelper h) {
        fresh(h);
        AdminDraft d = new AdminDraft();
        h.assertTrue(d.empty() && d.label().equals("Classic"), "a new draft isn't Classic: " + d.label());
        var r = d.set("fakeadmin", "power", "7");
        h.assertTrue(r.ok() && r.lines().get(0).equals("Fakeadmin: Power set to 7 for the new world."),
                "set said " + r.lines());
        AdminSetting p = setting(d, FAKE, "power");
        h.assertTrue(p.set() && p.value().getAsInt() == 7 && p.def().getAsInt() == 5, "the page doesn't show 7");
        // the same refusals as /giants admin set
        var bad = d.set("fakeadmin", "power", "99");
        h.assertTrue(!bad.ok() && bad.lines().get(0).contains("has to be"), "99 taken: " + bad.lines());
        h.assertTrue(!d.set("fakeadmin", "nothing", "1").ok(), "an unknown setting taken");
        h.assertTrue(!d.set("nobody", "power", "1").ok(), "an unknown giant taken");
        // a world-creation setting changes like any other (one line, no warning: there's no world yet)
        var land = d.set("fakeadmin", "landSize", "800");
        h.assertTrue(land.ok() && land.lines().size() == 1, "landSize: " + land.lines());
        h.assertTrue(d.set("all", "health", "2").ok(), "all.health");
        h.assertTrue(d.count() == 3 && d.label().equals("Custom"), "count/label " + d.count() + " " + d.label());
        // the book's pages read from it
        JsonObject page = AdminBook.pageJson(d, "fakeadmin");
        boolean seen = false;
        for (JsonElement e : page.getAsJsonArray("settings"))
            if (e.getAsJsonObject().get("id").getAsString().equals("landSize"))
                seen = e.getAsJsonObject().get("value").getAsInt() == 800 && e.getAsJsonObject().get("set")
                        .getAsBoolean();
        h.assertTrue(seen, "the page doesn't show landSize 800, set");
        JsonObject home = AdminBook.homeJson(d, d.label());
        h.assertTrue(home.get("allSet").getAsInt() == 1 && home.get("preset").getAsString().equals("Custom"),
                "home " + home);
        // back
        h.assertTrue(d.reset("fakeadmin", "power").ok() && !setting(d, FAKE, "power").set(), "reset one");
        h.assertTrue(d.reset("everything", null).ok() && d.empty() && d.label().equals("Classic"), "reset all");
        // nothing reached the giant: there is no world yet
        h.assertTrue(net.jj.fakeadmin.GiantsBridge.sets == 0, "the draft sent values to a giant");
        done(h);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "creation_draft_presets")
    public void theDraftTakesPresetsImportsAndSavedPresets(GameTestHelper h) throws Exception {
        fresh(h);
        Path dir = Files.createTempDirectory(Path.of("."), "p3presets");
        AdminPresets.dirOverride = dir;
        AdminPresets.forget();
        AdminDraft d = new AdminDraft();
        // an import, previewed then applied
        String text = AdminTransfer.encode(bundle(
                "{\"admin\":1,\"replace\":true,\"giants\":{\"fakeadmin\":{\"power\":3,\"landSize\":300}}}"));
        AdminTransfer.Plan pl = d.plan(AdminTransfer.decode(text), "import");
        h.assertTrue(pl.changes().size() == 2 && pl.creationOnly().size() == 1, "plan " + pl.lines());
        h.assertTrue(d.apply().ok() && setting(d, FAKE, "power").value().getAsInt() == 3, "import not applied");
        h.assertTrue(d.label().equals("Imported settings"), "label " + d.label());
        // saved as a preset, and picked in another draft
        h.assertTrue(AdminPresets.save(d.world(), "Alex", "", "Weekend Fun").ok(), "not saved");
        AdminDraft e = new AdminDraft();
        h.assertTrue(e.pick("weekend fun") != null && e.label().equals("Weekend Fun")
                && setting(e, FAKE, "landSize").value().getAsInt() == 300, "saved preset " + e.label());
        // changed after: ", changed"; Classic picked: empty again
        e.set("fakeadmin", "angry", "on");
        h.assertTrue(e.label().equals("Weekend Fun, changed"), "label " + e.label());
        e.pick("classic");
        h.assertTrue(e.empty() && e.label().equals("Classic"), "Classic left " + e.count());
        // the export is the draft exactly
        AdminTransfer.Bundle b = AdminTransfer.decode(AdminTransfer.encode(d.bundle()));
        h.assertTrue(b.replace() && b.values().get("fakeadmin").get("power").getAsInt() == 3, "export");
        h.assertTrue(e.pick("nothing like it") == null, "a preset that isn't there picked");
        done(h);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "creation_world")
    public void theDraftGoesIntoTheNewWorldBeforeItsFirstTick(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        AdminWorld w = AdminWorld.get(s);
        AdminDraft d = new AdminDraft();
        d.set("fakeadmin", "power", "8");
        d.set("fakeadmin", "landSize", "650");
        d.set("all", "health", "2");
        String folder = AdminCreation.folder(s);
        h.assertTrue(!folder.isEmpty(), "no folder name");
        // (the test world exists already: the real step leaves it alone)
        h.assertTrue(!AdminCreation.brandNew(s), "the test world reads as brand new");
        AdminCreation.arm(new AdminCreation.Pending(folder, d.bundle(), "Far Frontier, changed", "Alex"));
        h.assertTrue(AdminCreation.atLoad(s, s.overworld()) == 0 && AdminCreation.pending() == null
                && w.giantsWithValues().isEmpty(), "a world that isn't new was given the choice");
        // another world's choice is left out
        int n = AdminCreation.atLoad(s, s.overworld(),
                new AdminCreation.Pending("someOtherWorld", d.bundle(), "Custom", "Alex"), folder);
        h.assertTrue(n == 0 && w.giantsWithValues().isEmpty(), "another world's choice went in");
        // this world's choice, as the early load step does it for a brand-new world
        int logBefore = w.log().size();
        n = AdminCreation.atLoad(s, s.overworld(),
                new AdminCreation.Pending(folder, d.bundle(), "Far Frontier, changed", "Alex"), folder);
        h.assertTrue(n == 3, "3 settings in, not " + n);
        h.assertTrue(w.values("fakeadmin").get("power").getAsInt() == 8
                && w.values("fakeadmin").get("landSize").getAsInt() == 650
                && w.values("all").get("all.health").getAsDouble() == 2, "values " + w.values("fakeadmin"));
        h.assertTrue(w.log().size() == logBefore + 1, "not one log entry");
        AdminWorld.Change c = w.log().get(w.log().size() - 1);
        h.assertTrue(c.id().equals(AdminCreation.LOG_ID) && c.who().equals("Alex")
                && AdminServer.changeText(c).equals("Set at world creation: Far Frontier, changed (3 changes)"),
                "log says " + AdminServer.changeText(c));
        h.assertTrue(AdminInsight.presetName(w.log(), true).equals("Far Frontier, changed"),
                "Home's preset " + AdminInsight.presetName(w.log(), true));
        h.assertTrue(net.jj.giants.admin.AdminUndo.undoable(c), "it can't be undone");
        // then the usual push: the giant has them before his own load step
        AdminServer.push(s, w);
        h.assertTrue(net.jj.fakeadmin.GiantsBridge.values.get("power").getAsInt() == 8, "the giant didn't get 8");
        done(h);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "creation_newworld")
    public void newWorldTxtSetsUpABrandNewWorldOnADedicatedServer(GameTestHelper h) throws Exception {
        MinecraftServer s = fresh(h);
        AdminWorld w = AdminWorld.get(s);
        Path dir = Files.createTempDirectory(Path.of("."), "p3newworld");
        AdminCreation.dirOverride = dir;
        AdminPresets.dirOverride = dir;
        AdminPresets.forget();
        String folder = AdminCreation.folder(s);
        // no file: nothing
        AdminCreation.testDedicated = true;
        h.assertTrue(AdminCreation.atLoad(s, s.overworld(), null, folder) == 0, "no file, yet something");
        // a settings string after a comment
        String text = AdminTransfer.encode(bundle("{\"admin\":1,\"giants\":{\"fakeadmin\":{\"power\":2}}}"));
        Files.writeString(AdminCreation.file(), "# the new world's giants\n\n" + text + "\n");
        h.assertTrue(AdminCreation.atLoad(s, s.overworld(), null, folder) == 1
                && w.values("fakeadmin").get("power").getAsInt() == 2, "the string didn't go in");
        AdminWorld.Change c = w.log().get(w.log().size() - 1);
        h.assertTrue(c.who().equals("Server") && AdminServer.changeText(c).startsWith("Set at world creation: Imported"),
                "log " + AdminServer.changeText(c));
        h.assertTrue(Files.exists(AdminCreation.file()), "the file went (it stays for the next new world)");
        // a saved preset by name
        for (String g : w.giantsWithValues()) w.clear(g);
        AdminDraft d = new AdminDraft();
        d.set("fakeadmin", "angry", "on");
        AdminPresets.save(d.world(), "Alex", "", "Angry Ones");
        Files.writeString(AdminCreation.file(), "angry ones\n");
        h.assertTrue(AdminCreation.atLoad(s, s.overworld(), null, folder) == 1
                && w.values("fakeadmin").get("angry").getAsBoolean(), "the saved preset didn't go in");
        h.assertTrue(AdminInsight.presetName(w.log(), true).equals("Angry Ones"), "preset name");
        // a built-in by its name
        Files.writeString(AdminCreation.file(), "Far Frontier\n");
        AdminCreation.atLoad(s, s.overworld(), null, folder);
        h.assertTrue(AdminServer.changeText(w.log().get(w.log().size() - 1)).startsWith(
                "Set at world creation: Far Frontier"), "Far Frontier by name");
        // rubbish: nothing, no log entry
        for (String g : w.giantsWithValues()) w.clear(g);
        int logged = w.log().size();
        Files.writeString(AdminCreation.file(), "make them all purple\n");
        h.assertTrue(AdminCreation.atLoad(s, s.overworld(), null, folder) == 0 && w.giantsWithValues().isEmpty()
                && w.log().size() == logged, "rubbish changed something");
        // not a dedicated server (single player): the file isn't read
        Files.writeString(AdminCreation.file(), text + "\n");
        AdminCreation.testDedicated = false;
        h.assertTrue(AdminCreation.atLoad(s, s.overworld(), null, folder) == 0 && w.giantsWithValues().isEmpty(),
                "single player read new_world.txt");
        // the Create World choice wins over the file
        AdminCreation.testDedicated = true;
        AdminDraft mine = new AdminDraft();
        mine.set("fakeadmin", "power", "9");
        h.assertTrue(AdminCreation.atLoad(s, s.overworld(),
                new AdminCreation.Pending(folder, mine.bundle(), "Custom", "Alex"), folder) == 1
                && w.values("fakeadmin").get("power").getAsInt() == 9, "the screen's choice didn't win");
        done(h);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "creation_presets")
    public void closeQuartersAndFarFrontierAreRealPresets(GameTestHelper h) {
        fresh(h);
        String[] keys = {"pitchgut", "furrowmaw", "cerberus", "hollowbell", "lanternwillow", "wreckback",
            "swarmforge", "gloamwick"};
        AdminTransfer.Bundle cq = AdminTransfer.preset("close_quarters"), ff = AdminTransfer.preset("Far Frontier");
        h.assertTrue(cq != null && ff != null && !cq.replace() && !ff.replace(), "the two presets");
        for (String k : keys) {
            Map<String, JsonElement> a = cq.values().get(k), b = ff.values().get(k);
            h.assertTrue(a != null && a.get("groundNearest").getAsInt() == 500
                    && a.get("groundFarthest").getAsInt() == 1500 && a.get("groundGap").getAsInt() == 50, k + " CQ");
            h.assertTrue(b != null && b.get("groundNearest").getAsInt() == 5000
                    && b.get("groundFarthest").getAsInt() == 20000, k + " FF");
        }
        h.assertTrue(cq.about().contains("500 to 1,500") && ff.about().contains("5,000 to 20,000"), "about");
        // the Presets page and the picker: Classic first (Default), then the others under All presets
        JsonObject p = AdminBook.presetsJson(new AdminDraft(), "", "Classic");
        var built = p.getAsJsonArray("builtin");
        List<String> ids = new ArrayList<>();
        for (JsonElement e : built) ids.add(e.getAsJsonObject().get("id").getAsString());
        h.assertTrue(ids.equals(List.of("classic", "close_quarters", "far_frontier")), "order " + ids);
        JsonObject first = built.get(0).getAsJsonObject(), cqj = built.get(1).getAsJsonObject();
        h.assertTrue(first.get("default").getAsBoolean() && first.get("group").getAsString().equals("default"),
                "Classic isn't the default");
        h.assertTrue(cqj.get("group").getAsString().equals("all") && cqj.get("level").getAsInt() == 3,
                "Close Quarters " + cqj);
        // by command too
        h.assertTrue(AdminTransfer.presets().contains("far_frontier"), "/giants admin preset far_frontier");
        done(h);
    }

    /** a ground from the "grounds" lines */
    private record Ground(String giant, int x, int z, int r) {}

    /** with the eight giants' own mods (-Pgiants): the presets take, and each new ground keeps the band and the room */
    @GameTest(template = EMPTY_STRUCTURE, batch = "creation_real", timeoutTicks = 2000)
    public void realGiantsTakeThePresetsAndKeepTheirRoom(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        AdminServer.useGiants(null);
        List<Giant> here = AdminServer.giants();
        if (here.isEmpty()) {
            h.succeed();
            return;
        }
        AdminWorld w = AdminWorld.get(s);
        try {
            AdminServer.reset(s, "Server", "everything", null);
            // Far Frontier, applied as a preset in this world: every giant takes it, with the creation marker
            AdminTransfer.Plan pl = AdminTransfer.plan(s, AdminTransfer.preset("far_frontier"));
            h.assertTrue(pl.creationOnly().size() == 2 * here.size(), "creation marks: " + pl.creationOnly());
            AdminTransfer.apply(s, "Server", pl);
            for (Giant g : here) {
                AdminJson.Report r = AdminServer.report(s, g);
                h.assertTrue(r != null && r.setting("groundNearest").value().getAsInt() == 5000
                        && r.setting("groundFarthest").value().getAsInt() == 20000, g.key() + " didn't take it");
            }
            // each giant chooses his ground as a new world does, one after another, keeping band and room
            check(h, s, here, 3000, 6000, 1500);
            AdminServer.reset(s, "Server", "everything", null);
            AdminTransfer.apply(s, "Server", AdminTransfer.plan(s, AdminTransfer.preset("close_quarters")));
            check(h, s, here, 500, 1500, 50);
        } finally {
            AdminServer.reset(s, "Server", "everything", null);
        }
        h.succeed();
    }

    private static void check(GameTestHelper h, MinecraftServer s, List<Giant> here, int near, int far, int gap) {
        AdminWorld w = AdminWorld.get(s);
        for (Giant g : here) {
            AdminServer.set(s, "Server", g.key(), "groundNearest", String.valueOf(near));
            AdminServer.set(s, "Server", g.key(), "groundFarthest", String.valueOf(far));
            AdminServer.set(s, "Server", g.key(), "groundGap", String.valueOf(gap));
        }
        Map<String, Ground> grounds = new LinkedHashMap<>();
        for (Giant g : here) {
            List<String> lines = AdminServer.call(s, g, "groundnew", "");
            h.assertTrue(lines != null && !lines.isEmpty(), g.key() + ": no groundnew");
            JsonObject o = JsonParser.parseString(lines.get(0)).getAsJsonObject();
            grounds.put(g.key(), new Ground(g.key(), o.get("x").getAsInt(), o.get("z").getAsInt(),
                    o.get("r").getAsInt()));
        }
        var sp = s.overworld().getSharedSpawnPos();
        List<Ground> all = new ArrayList<>(grounds.values());
        for (int i = 0; i < all.size(); i++) {
            Ground a = all.get(i);
            double edge = Math.hypot(a.x() - sp.getX(), a.z() - sp.getZ()) - a.r();
            h.assertTrue(edge >= near - 2 && edge <= far + 2,
                    a.giant() + "'s ground begins " + Math.round(edge) + " from spawn, not " + near + "-" + far);
            for (int j = i + 1; j < all.size(); j++) {
                Ground b = all.get(j);
                double between = Math.hypot(a.x() - b.x(), a.z() - b.z()) - a.r() - b.r();
                h.assertTrue(between >= gap - 2, a.giant() + " and " + b.giant() + " are " + Math.round(between)
                        + " apart (room " + gap + ")");
            }
        }
    }
}
