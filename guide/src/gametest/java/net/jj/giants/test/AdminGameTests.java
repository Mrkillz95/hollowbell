package net.jj.giants.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.admin.AdminTransfer;
import net.jj.giants.admin.AdminWorld;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * The admin settings (ADMIN_BRIDGE.md): the bridge with a giant that speaks it (and one that doesn't), get, set and
 * reset through /giants admin, the world's values saved and read back, server defaults under the world's own, the
 * all-giants multipliers, operators only, the change log, presets, export and import, and no giants at all.
 */
public class AdminGameTests implements FabricGameTest {
    private static final Giant FAKE = fake("fakeadmin"), OLD = fake("fakeold");

    private static Giant fake(String key) {
        return new Giant(key, key, key, "net.jj." + key + ".GiantsBridge", List.of(), Set.of());
    }

    /** a clean start: the fake giants, nothing set for the world, the fake's memory wiped */
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
        h.succeed();
    }

    /** everything a command says, by whoever runs it */
    private static final class Heard implements CommandSource {
        final List<String> lines = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component m) {
            lines.add(m.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        String all() {
            return String.join("\n", lines);
        }
    }

    private static Heard run(MinecraftServer s, int permission, String command) {
        Heard heard = new Heard();
        CommandSourceStack src = s.createCommandSourceStack().withSource(heard).withPermission(permission);
        s.getCommands().performPrefixedCommand(src, command);
        return heard;
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void bridgeListIsCheckedAndOldGiantsAreLeftOut(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        AdminJson.Report r = AdminServer.report(s, FAKE);
        h.assertTrue(r != null, "the fake giant answers");
        h.assertTrue(r.name().equals("Fakeadmin"), "his name");
        h.assertTrue(r.settings().size() == 7, "7 good settings, the bad lines left out: " + r.settings().size());
        h.assertTrue(r.uses().contains("all.health"), "he applies the all-giants health");
        AdminSetting mood = r.setting("mood");
        h.assertTrue(mood != null && mood.type() == AdminSetting.Type.CHOICE && mood.options().size() == 3, "choice");
        AdminSetting land = r.setting("landSize");
        h.assertTrue(land.creationOnly() && land.whenNote().startsWith("World creation only"), "creation marker");
        h.assertTrue(r.setting("power").jar().getAsInt() == 4 && r.setting("power").def().getAsInt() == 5,
                "his jar's default and the server's default are both told");
        h.assertTrue(r.setting("all.health") == null, "a giant can't report an all-giants id as his own");
        h.assertTrue(AdminServer.report(s, OLD) == null, "an old giant (no admin) is left out");
        // set, get, reset: by the command, as an operator
        String said = run(s, 2, "giants admin set fakeadmin power 3").all();
        h.assertTrue(said.contains("Fakeadmin: Power set to 3 (was 5)."), "set says so: " + said);
        h.assertTrue(net.jj.fakeadmin.GiantsBridge.values.get("power").getAsInt() == 3, "the giant was sent it");
        said = run(s, 2, "giants admin get fakeadmin power").all();
        h.assertTrue(said.contains("Power is 3 (server default 5, set for this world)."), "get: " + said);
        said = run(s, 2, "giants admin get fakeadmin").all();
        h.assertTrue(said.contains("1 setting is set for this world") && said.contains("Power (power): 3"), said);
        said = run(s, 2, "giants admin reset fakeadmin power").all();
        h.assertTrue(said.contains("Power back to the server default, 5."), "reset: " + said);
        h.assertFalse(net.jj.fakeadmin.GiantsBridge.values.has("power"), "the giant's value is gone");
        h.assertTrue(AdminServer.report(s, FAKE).setting("power").value().getAsInt() == 5, "back to the default");
        h.assertFalse(AdminServer.report(s, FAKE).setting("power").set(), "not set for the world");
        said = run(s, 2, "giants admin list fakeadmin").all();
        h.assertTrue(said.contains("power = 5 (0 to 10) Power") && said.contains("[world creation]"), said);
        h.assertTrue(said.contains("each move's on, wait and wind"), "per-move rows folded: " + said);
        h.assertTrue(run(s, 2, "giants admin list fakeadmin moves").all().contains("move.bite.on = on"), "moves");
        done(h);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void everyTypeTakesItsValuesAndRefusesOthers(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        h.assertTrue(AdminServer.set(s, "T", "fakeadmin", "angry", "on").ok(), "bool on");
        h.assertTrue(AdminServer.set(s, "T", "fakeadmin", "speedy", "1.5").ok(), "float");
        h.assertTrue(AdminServer.set(s, "T", "fakeadmin", "mood", "Hunting").ok(), "choice, any case");
        h.assertTrue(AdminServer.set(s, "T", "fakeadmin", "sizes", "0.5-1.5").ok(), "range");
        var v = net.jj.fakeadmin.GiantsBridge.values;
        h.assertTrue(v.get("angry").getAsBoolean(), "angry sent");
        h.assertTrue(v.get("speedy").getAsDouble() == 1.5, "speed sent");
        h.assertTrue(v.get("mood").getAsString().equals("hunting"), "mood sent as its option");
        h.assertTrue(v.get("sizes").getAsJsonArray().get(1).getAsDouble() == 1.5, "range sent as [low, high]");
        // refused, plainly (WORDING.md: "the multiplier has to be a number from 0.1 to 10.")
        String why = AdminServer.set(s, "T", "fakeadmin", "power", "99").lines().get(0);
        h.assertTrue(why.equals("Power has to be a whole number from 0 to 10."), why);
        why = AdminServer.set(s, "T", "fakeadmin", "power", "2.5").lines().get(0);
        h.assertTrue(why.equals("Power has to be a whole number from 0 to 10."), why);
        why = AdminServer.set(s, "T", "fakeadmin", "angry", "maybe").lines().get(0);
        h.assertTrue(why.equals("Angry has to be on or off."), why);
        why = AdminServer.set(s, "T", "fakeadmin", "mood", "sleepy").lines().get(0);
        h.assertTrue(why.equals("Starting mood has to be one of: calm, hunting, guardian."), why);
        h.assertFalse(AdminServer.set(s, "T", "fakeadmin", "sizes", "2-1").ok(), "range the wrong way round");
        why = AdminServer.set(s, "T", "fakeadmin", "nope", "1").lines().get(0);
        h.assertTrue(why.equals("Fakeadmin has no setting called nope. /giants admin list fakeadmin shows them."), why);
        why = AdminServer.set(s, "T", "fakeold", "power", "1").lines().get(0);
        h.assertTrue(why.equals("Fakeold: this version has no admin settings."), why);
        why = AdminServer.set(s, "T", "nobody", "power", "1").lines().get(0);
        h.assertTrue(why.startsWith("No giant called nobody. Try one of: fakeadmin, fakeold, all"), why);
        why = AdminServer.set(s, "T", "all", "health", "20").lines().get(0);
        h.assertTrue(why.equals("Health has to be a number from 0.1 to 10."), why);
        h.assertTrue(AdminServer.set(s, "T", "all", "power", "2").lines().get(0).startsWith(
                "There's no all-giants setting called power."), "unknown all-giants setting");
        // a creation-only setting says so when it's changed in a world that's already there
        List<String> lines = AdminServer.set(s, "T", "fakeadmin", "landSize", "800").lines();
        h.assertTrue(lines.contains("World creation only: takes full effect in a new world."), "" + lines);
        done(h);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void worldValuesSurviveSaveAndReload(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        AdminServer.set(s, "Steve", "fakeadmin", "power", "7");
        AdminServer.set(s, "Steve", "all", "health", "2");
        AdminWorld w = AdminWorld.get(s);
        CompoundTag tag = w.save(new CompoundTag(), s.registryAccess());
        AdminWorld back = AdminWorld.read(tag, s.registryAccess());
        h.assertTrue(back.values("fakeadmin").get("power").getAsInt() == 7, "his value read back");
        h.assertTrue(back.values(AdminCatalog.ALL).get("all.health").getAsDouble() == 2.0, "all-giants read back");
        h.assertTrue(back.log().size() == w.log().size() && back.log().size() >= 2, "the log read back");
        AdminWorld.Change c = back.log().get(back.log().size() - 2);
        h.assertTrue(c.who().equals("Steve") && c.now().equals("7") && c.id().equals("power"), "who and what");
        // and a world that never set anything sends nothing: the giant stays on his own config
        fresh(h);
        AdminServer.push(s, AdminWorld.get(s));
        h.assertTrue(net.jj.fakeadmin.GiantsBridge.values.size() == 0, "an old world sends no values");
        h.assertTrue(net.jj.fakeadmin.GiantsBridge.all.size() == 0, "and no all-giants ones");
        done(h);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void allGiantsMultipliersReachEveryGiant(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        String said = run(s, 2, "giants admin set all health 2").all();
        h.assertTrue(said.contains("All giants: Health set to x2 (was x1). On top of each giant's own."), said);
        h.assertTrue(net.jj.fakeadmin.GiantsBridge.all.get("all.health").getAsDouble() == 2.0, "sent to him");
        said = run(s, 2, "giants admin get all health").all();
        h.assertTrue(said.contains("All giants: Health is x2 (server default x1, set for this world)."), said);
        said = run(s, 2, "giants admin reset all").all();
        h.assertTrue(said.contains("All giants: the shared multipliers are back to x1."), said);
        h.assertFalse(net.jj.fakeadmin.GiantsBridge.all.has("all.health"), "gone from him too");
        // everything: every giant and the all-giants ones
        AdminServer.set(s, "T", "fakeadmin", "power", "1");
        AdminServer.set(s, "T", "all", "damage", "3");
        said = run(s, 2, "giants admin reset everything").all();
        h.assertTrue(said.contains("back to the server defaults (2)"), said);
        h.assertTrue(AdminWorld.get(s).giantsWithValues().isEmpty(), "nothing left");
        done(h);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void onlyOperatorsMayUseIt(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        Heard heard = run(s, 0, "giants admin set fakeadmin power 3");
        h.assertFalse(net.jj.fakeadmin.GiantsBridge.values.has("power"), "a non-operator changed nothing");
        h.assertTrue(AdminWorld.get(s).values("fakeadmin").isEmpty(), "nothing kept");
        h.assertTrue(notThere(heard.all()), "it isn't there for them: " + heard.all());
        h.assertTrue(notThere(run(s, 0, "giants adminbook").all()), "nor the book");
        h.assertTrue(run(s, 2, "giants admin set fakeadmin power 3").all().contains("set to 3"), "an op may");
        done(h);
    }

    /** what the game says for a command that isn't there for you */
    private static boolean notThere(String said) {
        return said.contains("Unknown or incomplete command") || said.contains("Incorrect argument for command");
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void everyChangeIsLogged(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        int before = AdminWorld.get(s).log().size();
        AdminServer.set(s, "Alex", "fakeadmin", "power", "8");
        AdminServer.reset(s, "Alex", "fakeadmin", "power");
        List<AdminWorld.Change> log = AdminWorld.get(s).log();
        h.assertTrue(log.size() == Math.min(AdminWorld.MOST_LOG, before + 2), "two more");
        AdminWorld.Change set = log.get(log.size() - 2), reset = log.get(log.size() - 1);
        h.assertTrue(set.who().equals("Alex") && set.old().equals("5") && set.now().equals("8"), "set: old and new");
        h.assertTrue(reset.old().equals("8") && reset.now().equals("5"), "reset: back to the default");
        h.assertTrue(set.undo().equals("{\"fakeadmin\":{\"power\":null}}"), "undo: was the default " + set.undo());
        h.assertTrue(reset.undo().equals("{\"fakeadmin\":{\"power\":8}}"), "undo: was 8 " + reset.undo());
        h.assertTrue(AdminServer.changeText(set).equals("Fakeadmin Power 5 → 8"), AdminServer.changeText(set));
        String said = run(s, 2, "giants admin log 2").all();
        h.assertTrue(said.contains("Alex: Fakeadmin Power 5 → 8") && said.contains("Alex: Fakeadmin Power 8"), said);
        // a failed change isn't logged; only the last hundred are kept
        AdminServer.set(s, "Alex", "fakeadmin", "power", "99");
        h.assertTrue(AdminWorld.get(s).log().size() == log.size(), "a refused value isn't a change");
        for (int i = 0; i < 120; i++) AdminServer.set(s, "Alex", "fakeadmin", "power", String.valueOf(i % 10));
        h.assertTrue(AdminWorld.get(s).log().size() == AdminWorld.MOST_LOG, "a hundred kept");
        // the announcement for the other operators
        var r = AdminServer.set(s, "Alex", "all", "health", "2");
        h.assertTrue(r.announce().equals(List.of("Alex set All giants Health to x2.")), "" + r.announce());
        done(h);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void exportImportAndPresetsShowWhatChanges(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        AdminServer.set(s, "T", "fakeadmin", "power", "2");
        AdminServer.set(s, "T", "fakeadmin", "landSize", "900");
        AdminServer.set(s, "T", "all", "health", "1.5");
        String text = AdminTransfer.encode(AdminTransfer.current(s));
        h.assertTrue(text.startsWith(AdminTransfer.PREFIX), "an export string: " + text);
        AdminTransfer.Bundle b = AdminTransfer.decode(text);
        h.assertTrue(b != null && b.replace(), "read back");
        h.assertTrue(b.values().get("fakeadmin").get("power").getAsInt() == 2, "his value in it");
        // the classic preset: everything back, shown first, done only on confirm
        String said = run(s, 2, "giants admin preset classic").all();
        h.assertTrue(said.contains("Classic would change 3 settings:"), said);
        h.assertTrue(said.contains("Fakeadmin Power: 2 → 5 (default)"), said);
        h.assertTrue(said.contains("World creation: these only take full effect"), "creation-only warning: " + said);
        h.assertTrue(AdminWorld.get(s).values("fakeadmin").size() == 2, "nothing changed yet");
        said = run(s, 2, "giants admin confirm").all();
        h.assertTrue(said.contains("Classic: 3 settings changed for this world."), said);
        h.assertTrue(AdminWorld.get(s).giantsWithValues().isEmpty(), "all back");
        h.assertTrue(run(s, 2, "giants admin confirm").all().contains("Nothing to confirm."), "only once");
        // the export brings it all back
        said = run(s, 2, "giants admin import " + text).all();
        h.assertTrue(said.contains("The import would change 3 settings:"), said);
        run(s, 2, "giants admin confirm");
        h.assertTrue(net.jj.fakeadmin.GiantsBridge.values.get("power").getAsInt() == 2, "power back on the giant");
        h.assertTrue(AdminWorld.get(s).values(AdminCatalog.ALL).get("all.health").getAsDouble() == 1.5, "all back");
        // a bundle that only changes what it names, with one value the giant can't take and one unknown giant
        AdminTransfer.Bundle merge = AdminTransfer.read("{\"admin\":1,\"name\":\"Mine\",\"replace\":false,"
                + "\"giants\":{\"fakeadmin\":{\"speedy\":2,\"power\":99},\"pitchgut\":{\"health\":2}}}");
        AdminTransfer.Plan p = AdminTransfer.plan(s, merge);
        h.assertTrue(p.changes().size() == 1 && p.changes().get(0).id().equals("speedy"), "only speed: " + p.lines());
        h.assertTrue(String.join(" ", p.lines()).contains("pitchgut (not here)"), "the unknown one said: " + p.lines());
        h.assertTrue(run(s, 2, "giants admin import nonsense").all().contains("That isn't a settings string"), "bad");
        done(h);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void withNoGiantsItStillAnswers(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        AdminServer.useGiants(List.of());
        // (the guide's own test run has no giant mods: Wreckback's isn't here)
        if (Giants.loaded(Giants.byKey("wreckback"))) {
            AdminServer.useGiants(null);
            h.succeed();
            return;
        }
        String said = run(s, 2, "giants admin get wreckback").all();
        h.assertTrue(said.contains("Wreckback isn't installed."), said);
        said = run(s, 2, "giants admin set all speed 2").all();
        h.assertTrue(said.contains("All giants: Speed set to x2"), "the all-giants layer is kept anyway: " + said);
        h.assertTrue(AdminServer.push(s, AdminWorld.get(s)) == 0, "nobody to send it to");
        run(s, 2, "giants admin reset all");
        h.assertTrue(run(s, 2, "giants admin").all().contains("/giants admin set"), "bare: what it can do");
        done(h);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void valuesReadAndWriteTheSameWay(GameTestHelper h) throws AdminSetting.BadValue {
        AdminSetting all = AdminCatalog.shared("health");
        JsonElement v = all.parse("x1.25");
        h.assertTrue(v.getAsDouble() == 1.25, "x in front is fine");
        h.assertTrue(AdminSetting.text(new JsonPrimitive(2.0)).equals("2"), "2, not 2.0");
        h.assertTrue(AdminSetting.same(new JsonPrimitive(1), new JsonPrimitive(1.0)), "1 is 1.0");
        h.assertTrue(AdminCatalog.shared().stream().allMatch(x -> x.def().getAsDouble() == 1.0), "x1 changes nothing");
        done(h);
    }

    /** with the Hollowbell's own mod loaded (-Pgiants): his real bridge, end to end */
    @GameTest(template = EMPTY_STRUCTURE)
    public void hollowbellForRealWhenHeIsHere(GameTestHelper h) {
        Giant hb = Giants.byKey("hollowbell");
        if (!Giants.loaded(hb)) {
            h.succeed();
            return;
        }
        MinecraftServer s = h.getLevel().getServer();
        AdminServer.useGiants(null);
        AdminJson.Report r = AdminServer.report(s, hb);
        h.assertTrue(r != null && r.settings().size() > 100, "his list: " + (r == null ? 0 : r.settings().size()));
        h.assertTrue(AdminServer.set(s, "T", "hollowbell", "healthMultiplier", "2").ok(), "set");
        h.assertTrue(AdminServer.report(s, hb).setting("healthMultiplier").value().getAsDouble() == 2.0, "in use");
        AdminServer.Result ground = AdminServer.set(s, "T", "hollowbell", "homeRadius", "1200");
        h.assertTrue(ground.ok() && ground.lines().contains("Only affects land not made yet."),
                "his ground's size says it only reaches new land: " + ground.lines());
        h.assertTrue(AdminServer.set(s, "T", "hollowbell", "move.grab.on", "off").ok(), "a move off");
        h.assertTrue(!AdminServer.report(s, hb).setting("move.grab.on").value().getAsBoolean(), "it is off");
        h.assertTrue(AdminServer.set(s, "T", "all", "damage", "1.5").ok(), "all damage");
        AdminServer.reset(s, "T", "everything", null);
        h.assertTrue(AdminServer.report(s, hb).setting("healthMultiplier").value().getAsDouble() == 1.0, "back");
        h.assertTrue(AdminServer.report(s, hb).setting("move.grab.on").value().getAsBoolean(), "the move back on");
        h.succeed();
    }

    /** with Wreckback's own mod loaded (-Pgiants): his real bridge, end to end */
    @GameTest(template = EMPTY_STRUCTURE)
    public void wreckbackForRealWhenHeIsHere(GameTestHelper h) {
        Giant wb = Giants.byKey("wreckback");
        if (!Giants.loaded(wb)) {
            h.succeed();
            return;
        }
        MinecraftServer s = h.getLevel().getServer();
        AdminServer.useGiants(null);
        AdminJson.Report r = AdminServer.report(s, wb);
        h.assertTrue(r != null && r.settings().size() > 100, "his list: " + (r == null ? 0 : r.settings().size()));
        h.assertTrue(AdminServer.set(s, "T", "wreckback", "healthMultiplier", "2").ok(), "set");
        h.assertTrue(AdminServer.report(s, wb).setting("healthMultiplier").value().getAsDouble() == 2.0, "in use");
        h.assertTrue(AdminServer.set(s, "T", "wreckback", "move.claw_snip.on", "off").ok(), "a move off");
        h.assertTrue(AdminServer.set(s, "T", "all", "damage", "1.5").ok(), "all damage");
        AdminServer.reset(s, "T", "everything", null);
        h.assertTrue(AdminServer.report(s, wb).setting("healthMultiplier").value().getAsDouble() == 1.0, "back");
        h.succeed();
    }
}
