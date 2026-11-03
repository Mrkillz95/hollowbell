package net.jj.giants.test;

import com.google.gson.JsonElement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminDraft;
import net.jj.giants.admin.AdminEvery;
import net.jj.giants.admin.AdminInsight;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.admin.AdminTransfer;
import net.jj.giants.admin.AdminUndo;
import net.jj.giants.admin.AdminWorld;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;

/**
 * Part 2C (ADMIN_BRIDGE.md 21): one shared setting set to the same value for every giant (each kept to his own range,
 * and said so), put back for every giant, one change in the log and one undo, locks, the Create World draft and the
 * command; the sections a setting shows in; the meetGiants clash rules.
 */
public class P2cGameTests implements FabricGameTest {
    private static final Giant A = fake("fakeadmin"), B = fake("fakeadmin2");

    private static Giant fake(String key) {
        return new Giant(key, key, key, "net.jj." + key + ".GiantsBridge", List.of(), Set.of());
    }

    private static MinecraftServer fresh(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        AdminServer.useGiants(List.of(A, B));
        AdminWorld w = AdminWorld.get(s);
        for (String g : w.giantsWithValues()) w.clear(g);
        for (String k : List.copyOf(w.locks().keySet())) {
            String[] p = k.split("/", 2);
            if (p.length == 2) w.unlock(p[0], p[1]);
        }
        net.jj.fakeadmin.GiantsBridge.reset();
        net.jj.fakeadmin2.GiantsBridge.reset();
        AdminTransfer.forget();
        return s;
    }

    private static void done(GameTestHelper h) {
        AdminServer.useGiants(null);
        h.succeed();
    }

    private static String val(MinecraftServer s, Giant g, String id) {
        AdminSetting x = AdminServer.report(s, g).setting(id);
        return AdminSetting.text(x.value()) + (x.set() ? "" : " (default)");
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p2c_every")
    public void oneValueForEveryGiantKeepsToEachOnesRange(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        List<AdminWorld.Change> was = AdminWorld.get(s).log();
        AdminWorld.Change lastBefore = was.isEmpty() ? null : was.get(was.size() - 1);
        AdminServer.Result r = AdminEvery.set(s, "Tester", "power", "8");
        h.assertTrue(r.ok(), "set: " + r.lines());
        h.assertTrue(val(s, A, "power").equals("8"), "fakeadmin takes 8: " + val(s, A, "power"));
        h.assertTrue(val(s, B, "power").equals("6"), "fakeadmin2 pulled to his most 6: " + val(s, B, "power"));
        String all = String.join(" | ", r.lines());
        h.assertTrue(all.contains("every giant") && all.contains("Fakeadmin2: 8 is past his most (6), so 6."), all);
        // the giants were sent it
        h.assertTrue(net.jj.fakeadmin2.GiantsBridge.values.get("power").getAsInt() == 6, "sent");
        // one change in the log, one undo puts both back
        List<AdminWorld.Change> log = AdminWorld.get(s).log();
        h.assertTrue(log.get(log.size() - 1).id().equals("every:power")
                && (log.size() < 2 || log.get(log.size() - 2) == lastBefore || lastBefore == null),
                "one entry: " + log.size() + " " + log.get(log.size() - 1).id());
        h.assertTrue(AdminServer.changeText(log.get(log.size() - 1)).startsWith("Every giant: Power"),
                AdminServer.changeText(log.get(log.size() - 1)));
        AdminWorld.Change c = log.get(log.size() - 1);
        AdminServer.Result u = AdminUndo.undo(s, "Tester", log.size() - 1, c.time());
        h.assertTrue(u.ok(), "undo: " + u.lines());
        h.assertTrue(val(s, A, "power").endsWith("(default)") && val(s, B, "power").endsWith("(default)"),
                "both back: " + val(s, A, "power") + " / " + val(s, B, "power"));
        // below the least, a float, a choice one of them hasn't got, a range
        AdminEvery.set(s, "Tester", "speedy", "0.1");
        h.assertTrue(val(s, A, "speedy").equals("0.5") && val(s, B, "speedy").equals("0.5"), "least 0.5");
        AdminServer.Result m = AdminEvery.set(s, "Tester", "mood", "guardian");
        h.assertTrue(m.ok() && val(s, A, "mood").equals("guardian") && val(s, B, "mood").endsWith("(default)"),
                "guardian only where there is one: " + m.lines());
        h.assertTrue(String.join(" ", m.lines()).contains("Fakeadmin2: he has no guardian"), "" + m.lines());
        AdminEvery.set(s, "Tester", "sizes", "0.2-3");
        h.assertTrue(val(s, A, "sizes").equals("0.2-3") && val(s, B, "sizes").equals("0.5-2"),
                val(s, A, "sizes") + " / " + val(s, B, "sizes"));
        // nonsense, and a setting nobody has
        h.assertFalse(AdminEvery.set(s, "Tester", "power", "lots").ok(), "not a number");
        h.assertFalse(AdminEvery.set(s, "Tester", "nothing", "1").ok(), "nobody has it");
        h.assertFalse(AdminEvery.set(s, "Tester", "move.bite.on", "off").ok(), "moves are each giant's own");
        done(h);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p2c_every")
    public void putBackForEveryGiantAndLocksAreLeftAlone(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        AdminEvery.set(s, "Tester", "power", "2");
        h.assertTrue(val(s, A, "power").equals("2") && val(s, B, "power").equals("2"), "both 2");
        AdminServer.Result r = AdminEvery.reset(s, "Tester", "power");
        h.assertTrue(r.ok() && val(s, A, "power").endsWith("(default)") && val(s, B, "power").endsWith("(default)"),
                "both back: " + r.lines());
        h.assertTrue(AdminEvery.reset(s, "Tester", "power").lines().get(0).contains("already"), "nothing to put back");
        // another admin's lock: that giant is left as he is, and named
        AdminWorld.get(s).lock("fakeadmin2", "power", new AdminWorld.Lock("Other", "", 1L));
        AdminServer.Result l = AdminEvery.set(s, "Tester", "power", "4");
        h.assertTrue(val(s, A, "power").equals("4") && val(s, B, "power").endsWith("(default)"), "locked one kept");
        h.assertTrue(String.join(" ", l.lines()).contains("locked by Other"), "" + l.lines());
        AdminWorld.get(s).unlock("fakeadmin2", "power");
        // the command
        int ok = exec(s, "giants admin setall speedy 1.25");
        h.assertTrue(ok == 1 && val(s, A, "speedy").equals("1.25") && val(s, B, "speedy").equals("1.25"),
                "command: " + val(s, A, "speedy"));
        h.assertTrue(exec(s, "giants admin resetall speedy") == 1 && val(s, A, "speedy").endsWith("(default)"),
                "resetall");
        done(h);
    }

    private static int exec(MinecraftServer s, String cmd) {
        try {
            return s.getCommands().getDispatcher().execute(cmd, s.createCommandSourceStack().withPermission(4));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return -1;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p2c_every")
    public void theCreateWorldDraftSetsEveryGiantToo(GameTestHelper h) {
        fresh(h);
        AdminDraft d = new AdminDraft();
        AdminServer.Result r = AdminEvery.set(d, "power", "9");
        h.assertTrue(r.ok(), "" + r.lines());
        Map<String, JsonElement> a = d.world().values("fakeadmin"), b = d.world().values("fakeadmin2");
        h.assertTrue(a.get("power").getAsInt() == 9 && b.get("power").getAsInt() == 6, a + " " + b);
        h.assertTrue(d.label().equals("Custom") && d.edited(), d.label());
        AdminEvery.reset(d, "power");
        h.assertTrue(d.world().values("fakeadmin").isEmpty() && d.world().values("fakeadmin2").isEmpty(), "reset");
        done(h);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p2c_every")
    public void eachSettingHasASection(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        AdminJson.Report b = AdminServer.report(s, B), a = AdminServer.report(s, A);
        h.assertTrue(AdminJson.section(b.setting("angry")).equals("misc"), "named by the giant");
        h.assertTrue(AdminJson.section(a.setting("angry")).equals("behaviour"), "from its category");
        h.assertTrue(AdminJson.section(b.setting("shell")).equals("misc"), "server load is under misc");
        h.assertTrue(AdminJson.section(b.setting("power")).equals("stats"), "stats");
        h.assertTrue(AdminJson.write(b.setting("angry")).get("section").getAsString().equals("misc"), "sent on");
        h.assertFalse(AdminJson.write(a.setting("angry")).has("section"), "not when not named");
        h.assertTrue(AdminJson.SECTIONS.size() == 11, "eleven sections");
        done(h);
    }

    // the meetGiants clash rules (ADMIN_BRIDGE.md 21.1)

    private static AdminInsight.View g(Map<String, Double> now) {
        Map<String, Double> jar = new HashMap<>(Map.of("meetGiants", 0.0, "seekGiants", 0.0, "fightGiants", 1.0,
                "meetings", 1.0));
        Map<String, Double> n = new HashMap<>(jar);
        n.putAll(now);
        return AdminInsight.view("g", "Giant", n, jar, Set.of());
    }

    private static List<String> rules(Map<String, Double> now) {
        return AdminInsight.warnings(List.of(g(now)), Map.of()).stream().map(AdminInsight.Warning::rule).toList();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p2c_clash")
    public void meetingClashesHoldEachWay(GameTestHelper h) {
        h.assertTrue(rules(Map.of()).isEmpty(), "as shipped: " + rules(Map.of()));
        h.assertTrue(rules(Map.of("meetGiants", 1.0, "fightGiants", 0.0)).contains("meet_fight_off"), "fight, off");
        h.assertFalse(rules(Map.of("meetGiants", 1.0)).contains("meet_fight_off"), "fight, on");
        h.assertFalse(rules(Map.of("meetGiants", 0.0, "fightGiants", 0.0)).contains("meet_fight_off"), "decide");
        h.assertTrue(rules(Map.of("meetGiants", 2.0, "seekGiants", 1.0)).contains("seek_no_meet"), "run away");
        h.assertTrue(rules(Map.of("meetGiants", 3.0, "seekGiants", 1.0)).contains("seek_no_meet"), "ignore");
        h.assertFalse(rules(Map.of("meetGiants", 1.0, "seekGiants", 1.0)).contains("seek_no_meet"), "fight seeks");
        h.assertFalse(rules(Map.of("meetGiants", 3.0)).contains("seek_no_meet"), "not seeking");
        String text = AdminInsight.warnings(List.of(g(Map.of("meetGiants", 2.0, "seekGiants", 1.0))), Map.of())
                .get(0).text();
        h.assertTrue(text.contains("never goes looking"), text);
        h.assertTrue(AdminInsight.simple("meetGiants"), "in Simple mode");
        done(h);
    }
}
