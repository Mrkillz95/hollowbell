package net.jj.giants.test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.GuideBridge;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminTransfer;
import net.jj.giants.admin.AdminWorld;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * The command scope (ADMIN_BRIDGE.md, "Command scope"): a giant's own command (or book switch) changes a setting for
 * this world through GuideBridge "adminworld", exactly like /giants admin set (kept, logged, sent back); /giants admin
 * set ... allworlds changes the server default through the giant's "admindefault" and leaves the world's values
 * alone. With -Pgiants, the real Wreckback and Hollowbell end to end: /giants and their own commands, with and
 * without a scope, and their config files untouched by a world change.
 */
public class ScopeGameTests implements FabricGameTest {
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
        h.succeed();
    }

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

    private static String run(MinecraftServer s, String command) {
        Heard heard = new Heard();
        CommandSourceStack src = s.createCommandSourceStack().withSource(heard).withPermission(4);
        s.getCommands().performPrefixedCommand(src, command);
        return heard.all();
    }

    private static AdminWorld.Change last(MinecraftServer s) {
        List<AdminWorld.Change> log = AdminWorld.get(s).log();
        return log.get(log.size() - 1);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aGiantsOwnCommandSetsThisWorldThroughTheGuide(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        int before = AdminWorld.get(s).log().size();
        List<String> r = GuideBridge.guide(s, "adminworld",
                "{\"admin\":1,\"giant\":\"fakeadmin\",\"id\":\"power\",\"value\":3,\"by\":\"-\"}");
        h.assertTrue(r != null && r.get(0).equals("ok"), "taken: " + r);
        h.assertTrue(r.get(1).equals("Fakeadmin: Power set to 3 in this world (was 5)."), r.get(1));
        h.assertTrue(AdminWorld.get(s).values("fakeadmin").get("power").getAsInt() == 3, "kept with the world");
        h.assertTrue(net.jj.fakeadmin.GiantsBridge.values.get("power").getAsInt() == 3, "sent back to the giant");
        h.assertTrue(AdminWorld.get(s).log().size() == Math.min(AdminWorld.MOST_LOG, before + 1), "logged");
        AdminWorld.Change c = last(s);
        h.assertTrue(c.who().equals("Server") && c.id().equals("power") && c.now().equals("3"), "who and what");
        h.assertTrue(c.undo().equals("{\"fakeadmin\":{\"power\":null}}"), "undo like any change: " + c.undo());
        // a switch: on/off as true/false
        r = GuideBridge.guide(s, "adminworld", "{\"admin\":1,\"giant\":\"fakeadmin\",\"id\":\"angry\",\"value\":true}");
        h.assertTrue(r.get(0).equals("ok") && net.jj.fakeadmin.GiantsBridge.values.get("angry").getAsBoolean(), "" + r);
        // refused, with the same words as /giants admin set, and nothing kept or logged
        int n = AdminWorld.get(s).log().size();
        r = GuideBridge.guide(s, "adminworld", "{\"admin\":1,\"giant\":\"fakeadmin\",\"id\":\"power\",\"value\":99}");
        h.assertTrue(r.equals(List.of("no", "Power has to be a whole number from 0 to 10.")), "" + r);
        r = GuideBridge.guide(s, "adminworld", "{\"admin\":1,\"giant\":\"fakeold\",\"id\":\"power\",\"value\":1}");
        h.assertTrue(r.equals(List.of("no", "Fakeold: this version has no admin settings.")), "" + r);
        h.assertTrue(AdminWorld.get(s).log().size() == n, "nothing logged");
        h.assertTrue(AdminWorld.get(s).values("fakeadmin").get("power").getAsInt() == 3, "still 3");
        // what it can't read, and what it doesn't know
        h.assertTrue(GuideBridge.guide(s, "adminworld", "{\"admin\":2,\"giant\":\"fakeadmin\",\"id\":\"power\","
                + "\"value\":1}") == null, "a newer protocol: null");
        h.assertTrue(GuideBridge.guide(s, "adminworld", "nonsense") == null, "nonsense: null");
        h.assertTrue(GuideBridge.guide(s, "nosuch", "{}") == null, "an action it doesn't know: null");
        h.assertTrue(GuideBridge.guide(null, "adminworld", "{}") == null, "no server: null");
        done(h);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void allworldsChangesTheServerDefaultAndNotTheWorld(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        String said = run(s, "giants admin set fakeadmin power 3 allworlds");
        h.assertTrue(said.equals("Fakeadmin: Power set to 3 for all worlds (server default; was 5)."), said);
        h.assertTrue(net.jj.fakeadmin.GiantsBridge.defaults.get("power").getAsInt() == 3, "his file changed");
        h.assertTrue(AdminWorld.get(s).values("fakeadmin").isEmpty(), "the world's own values are untouched");
        h.assertTrue(last(s).name().equals("Power (server default)") && last(s).now().equals("3")
                && last(s).undo().isEmpty(), "logged as the server default: " + AdminServer.changeText(last(s)));
        // the world keeps its own: it still wins, and the reply says so
        run(s, "giants admin set fakeadmin power 7 world");
        h.assertTrue(AdminWorld.get(s).values("fakeadmin").get("power").getAsInt() == 7, "world says world");
        said = run(s, "giants admin set fakeadmin power 4 allworlds");
        h.assertTrue(said.equals("Fakeadmin: Power set to 4 for all worlds (server default; was 3). This world keeps"
                + " its own: 7 (/giants admin reset fakeadmin power)."), said);
        h.assertTrue(AdminWorld.get(s).values("fakeadmin").get("power").getAsInt() == 7, "the world's still 7");
        // nothing said is this world
        said = run(s, "giants admin set fakeadmin speedy 1.5");
        h.assertTrue(said.equals("Fakeadmin: Speed set to 1.5 in this world (was 1)."), said);
        // what has no server default
        said = run(s, "giants admin set all health 2 allworlds");
        h.assertTrue(said.startsWith("The all-giants multipliers are only kept for each world"), said);
        said = run(s, "giants admin set fakeadmin move.bite.on off allworlds");
        h.assertTrue(said.equals("Fakeadmin: Bite: on is only kept for each world; it has no server default."), said);
        h.assertFalse(AdminWorld.get(s).values("fakeadmin").containsKey("move.bite.on"), "and nothing set");
        said = run(s, "giants admin set fakeadmin power 99 allworlds");
        h.assertTrue(said.equals("Power has to be a whole number from 0 to 10."), said);
        net.jj.fakeadmin.GiantsBridge.knowsDefault = false;
        said = run(s, "giants admin set fakeadmin power 2 allworlds");
        h.assertTrue(said.startsWith("Fakeadmin: this version can't change its server default from here."), said);
        h.assertTrue(run(s, "giants admin").contains("[world|allworlds]"), "the help says so");
        done(h);
    }

    // with the real giants (-Pgiants)

    private static String file(String name) {
        try {
            return Files.readString(FabricLoader.getInstance().getConfigDir().resolve(name));
        } catch (Exception e) {
            return "";
        }
    }

    /** /giants and his own commands, with and without a scope, for one real giant */
    private static void realGiant(GameTestHelper h, String key, String name, String file, String own) {
        MinecraftServer s = h.getLevel().getServer();
        AdminServer.useGiants(null);
        AdminWorld w = AdminWorld.get(s);
        w.clear(key);
        String before = file(file);
        double damage = AdminServer.report(s, Giants.byKey(key)).setting("damageMultiplier").def().getAsDouble();
        double health = AdminServer.report(s, Giants.byKey(key)).setting("healthMultiplier").def().getAsDouble();
        try {
            // nothing said: this world, through the Guide; the file stays as it was
            String said = run(s, "giants health 2 " + key);
            h.assertTrue(said.contains(name + ": health x2 in this world."), said);
            h.assertTrue(w.values(key).get("healthMultiplier").getAsDouble() == 2.0, "kept for the world");
            AdminWorld.Change c = last(s);
            h.assertTrue(c.giant().equals(key) && c.id().equals("healthMultiplier") && c.who().equals("Server"),
                    "logged: " + AdminServer.changeText(c));
            said = run(s, own + " heavydelay 40 world");
            h.assertTrue(said.endsWith("in this world."), said);
            h.assertTrue(w.values(key).get("heavyDelaySeconds").getAsInt() == 40, "his own command, this world");
            h.assertTrue(file(file).equals(before), "the config file isn't touched by a world change");
            // allworlds: the file, and the world's own values stay
            said = run(s, "giants health 1.5 " + key + " allworlds");
            h.assertTrue(said.contains(name + ": health x1.5 for all worlds (server default). This world keeps its own:"
                    + " 2"), said);
            h.assertTrue(w.values(key).get("healthMultiplier").getAsDouble() == 2.0, "the world's still 2");
            h.assertTrue(AdminServer.report(s, Giants.byKey(key)).setting("healthMultiplier").def().getAsDouble()
                    == 1.5, "the server default is 1.5");
            said = run(s, "giants admin set " + key + " damageMultiplier 1.25 allworlds");
            h.assertTrue(said.startsWith(name + ": Damage multiplier set to 1.25 for all worlds (server default"),
                    said);
            h.assertFalse(w.values(key).containsKey("damageMultiplier"), "not kept for the world");
            h.assertTrue(AdminServer.report(s, Giants.byKey(key)).setting("damageMultiplier").def().getAsDouble()
                    == 1.25, "his file has it");
        } finally {
            run(s, "giants admin set " + key + " healthMultiplier " + health + " allworlds");
            run(s, "giants admin set " + key + " damageMultiplier " + damage + " allworlds");
            AdminServer.reset(s, "T", key, null);
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
    public void wreckbackScopesForRealWhenHeIsHere(GameTestHelper h) {
        if (!Giants.loaded(Giants.byKey("wreckback"))) {
            h.succeed();
            return;
        }
        realGiant(h, "wreckback", "Wreckback", "wreckback.json", "wreckback");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
    public void hollowbellScopesForRealWhenHeIsHere(GameTestHelper h) {
        if (!Giants.loaded(Giants.byKey("hollowbell"))) {
            h.succeed();
            return;
        }
        realGiant(h, "hollowbell", "Hollowbell", "hollowbell.json", "hollowbell");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
    public void pitchgutScopesForRealWhenHeIsHere(GameTestHelper h) {
        if (!Giants.loaded(Giants.byKey("pitchgut"))) {
            h.succeed();
            return;
        }
        realGiant(h, "pitchgut", "Pitchgut", "mountain_breathes.json", "pitchgut");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
    public void furrowmawScopesForRealWhenHeIsHere(GameTestHelper h) {
        if (!Giants.loaded(Giants.byKey("furrowmaw"))) {
            h.succeed();
            return;
        }
        realGiant(h, "furrowmaw", "Furrowmaw", "furrowmaw.json", "furrowmaw");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
    public void cerberusScopesForRealWhenHeIsHere(GameTestHelper h) {
        if (!Giants.loaded(Giants.byKey("cerberus"))) {
            h.succeed();
            return;
        }
        realGiant(h, "cerberus", "Cerberus", "fire_ice_cerberus.json", "cerberus");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
    public void lanternwillowScopesForRealWhenHeIsHere(GameTestHelper h) {
        if (!Giants.loaded(Giants.byKey("lanternwillow"))) {
            h.succeed();
            return;
        }
        realGiant(h, "lanternwillow", "Lantern Willow", "lanternwillow.json", "lanternwillow");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
    public void swarmforgeScopesForRealWhenHeIsHere(GameTestHelper h) {
        if (!Giants.loaded(Giants.byKey("swarmforge"))) {
            h.succeed();
            return;
        }
        realGiant(h, "swarmforge", "Swarmforge", "swarmforge.json", "swarmforge");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
    public void gloamwickScopesForRealWhenHeIsHere(GameTestHelper h) {
        if (!Giants.loaded(Giants.byKey("gloamwick"))) {
            h.succeed();
            return;
        }
        realGiant(h, "gloamwick", "Gloamwick", "gloamwick.json", "gloamwick");
        h.succeed();
    }
}
