package net.jj.hollowbell.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.jj.hollowbell.AdminScope;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.GiantsAdmin;
import net.jj.hollowbell.GiantsBridge;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.command.GiantsCommand;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.net.CodexOrders;
import net.jj.hollowbell.net.CodexPayload;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.item.ItemStack;

/**
 * The command scope (ADMIN_BRIDGE.md, "Command scope"): every command that changes one of his settings takes "world"
 * (this world, through the Giants Guide) or "allworlds" (the server default, his config file) on the end; nothing
 * said is this world, or without the Guide the server default, and the reply says which. The Guide isn't in this
 * test run, so a stand-in for it (FakeGuide) keeps the world's values the way the Guide does and sends them back to
 * him over his bridge. Each test sets and clears everything within one tick.
 */
public class ScopeTests implements FabricGameTest {

    /** the Giants Guide's side, as far as he can see it: the world's values, kept and sent back; a log of who */
    static final class FakeGuide {
        final Map<String, JsonElement> store = new LinkedHashMap<>();
        final List<String> log = new ArrayList<>();
        private final MinecraftServer server;

        FakeGuide(MinecraftServer server) {
            this.server = server;
            AdminScope.testGuide = this::answer;
            GiantsCommand.testGuide = true;
        }

        List<String> answer(String action, String arg) {
            if (!action.equals("adminworld")) return null;
            JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
            if (o.get("admin").getAsInt() != 1 || !o.get("giant").getAsString().equals("hollowbell")) return null;
            String id = o.get("id").getAsString();
            JsonElement v = o.get("value");
            GiantsAdmin.Row r = GiantsAdmin.row(id);
            if (r == null) return List.of("no", "Hollowbell has no setting called " + id + ".");
            if (!r.type().equals("bool") && (v.getAsDouble() < r.min() || v.getAsDouble() > r.max()))
                return List.of("no", r.name() + " has to be a number from " + r.min() + " to " + r.max() + ".");
            JsonElement was = store.put(id, v);
            log.add(o.get("by").getAsString() + " " + id + " " + was + " -> " + v);
            JsonObject set = new JsonObject();
            set.addProperty("admin", 1);
            JsonObject values = new JsonObject();
            for (var e : store.entrySet()) values.add(e.getKey(), e.getValue());
            set.add("values", values);
            GiantsBridge.giants(server, "adminset", set.toString());
            List<String> out = new ArrayList<>(List.of("ok", "Hollowbell: " + r.name() + " set to " + v + "."));
            if (r.when().equals(GiantsAdmin.NEW_SPAWNS)) out.add("Only affects new spawns.");
            return out;
        }

        void close() {
            AdminScope.testGuide = null;
            GiantsCommand.testGuide = false;
            GiantsAdmin.clear();
        }
    }

    /** everything a command says */
    static final class Heard implements CommandSource {
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

    static String run(GameTestHelper h, String command) {
        Heard heard = new Heard();
        MinecraftServer s = h.getLevel().getServer();
        CommandSourceStack src = s.createCommandSourceStack().withSource(heard).withPermission(4);
        s.getCommands().performPrefixedCommand(src, command);
        return heard.all();
    }

    static String file() {
        try {
            return Files.readString(FabricLoader.getInstance().getConfigDir().resolve("hollowbell.json"));
        } catch (Exception e) {
            return "";
        }
    }

    /** what the tests change in the file, to put back */
    record Saved(float health, float damage, int limit, boolean natural, float grudge, boolean grief, boolean harvest) {
        static Saved now() {
            var F = BellConfig.fileValues();
            return new Saved(F.healthMultiplier, F.damageMultiplier, F.maxInWorld, F.oneInTheWorld, F.grudgeRate,
                    F.griefing, F.harvest);
        }

        void putBack() {
            try {
                Map<java.lang.reflect.Field, Object> m = new LinkedHashMap<>();
                Class<?> c = BellConfig.Values.class;
                m.put(c.getField("healthMultiplier"), health);
                m.put(c.getField("damageMultiplier"), damage);
                m.put(c.getField("maxInWorld"), limit);
                m.put(c.getField("oneInTheWorld"), natural);
                m.put(c.getField("grudgeRate"), grudge);
                m.put(c.getField("griefing"), grief);
                m.put(c.getField("harvest"), harvest);
                BellConfig.setDefaults(m);
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "scope_no_guide")
    public void withoutTheGuideNothingSaidIsTheServerDefault(GameTestHelper h) {
        Saved saved = Saved.now();
        try {
            h.assertFalse(AdminScope.guideHere(), "no Giants Guide in this run");
            String said = run(h, "hollowbell damage 2.5");
            h.assertTrue(said.equals("His hits: now 2.5 for all worlds (server default; per-world settings need the"
                    + " Giants Guide)."), "says where it went: " + said);
            h.assertTrue(BellConfig.fileValues().damageMultiplier == 2.5f, "the file changed");
            said = run(h, "hollowbell damage 3 world");
            h.assertTrue(said.equals(AdminScope.NEED_GUIDE), "world is refused without the Guide: " + said);
            h.assertTrue(BellConfig.fileValues().damageMultiplier == 2.5f, "nothing changed");
            said = run(h, "giants health 2 world");
            h.assertTrue(said.equals(GiantsCommand.NEED_GUIDE), "/giants too: " + said);
            h.assertTrue(BellConfig.fileValues().healthMultiplier == saved.health(), "nothing changed");
            said = run(h, "giants health 2");
            h.assertTrue(said.contains("Hollowbell: health x2 for all worlds (server default; per-world settings need"
                    + " the Giants Guide)."), said);
            h.assertTrue(BellConfig.fileValues().healthMultiplier == 2f, "the file");
            said = run(h, "giants health 1.5 allworlds");
            h.assertTrue(said.contains("Hollowbell: health x1.5 for all worlds (server default)."), said);
            said = run(h, "hollowbell griefing off allworlds");
            h.assertTrue(said.equals("Breaking blocks: now off for all worlds (server default)."), said);
            List<String> b = GiantsBridge.giants(h.getLevel().getServer(), "healthmult", "2 scope=world by=-");
            h.assertTrue(b.equals(List.of("Hollowbell: " + AdminScope.NEED_GUIDE)), "" + b);
        } finally {
            saved.putBack();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "scope_world")
    public void withTheGuideNothingSaidIsThisWorld(GameTestHelper h) {
        Saved saved = Saved.now();
        String before = file();
        var F = BellConfig.fileValues();
        float damage = F.damageMultiplier, health = F.healthMultiplier, grudge = F.grudgeRate;
        int limit = F.maxInWorld;
        FakeGuide guide = new FakeGuide(h.getLevel().getServer());
        try {
            String said = run(h, "hollowbell damage 2.5");
            h.assertTrue(said.equals("His hits: now 2.5 in this world."), said);
            h.assertTrue(guide.store.get("damageMultiplier").getAsDouble() == 2.5, "kept for the world");
            h.assertTrue(BellConfig.V.damageMultiplier == 2.5f, "in use");
            said = run(h, "giants health 3");
            h.assertTrue(said.contains("Hollowbell: health x3 in this world."), said);
            said = run(h, "giants limit 4 world");
            h.assertTrue(said.contains("Hollowbell: at most 4 in this world. Only affects new spawns."), said);
            h.assertTrue(BellConfig.V.maxInWorld == 4, "in use");
            said = run(h, "hollowbell config grudgeRate 2 world");
            h.assertTrue(said.equals("How fast he holds a grudge: now 2.0 in this world."), said);
            said = run(h, "hollowbell griefing on world");
            h.assertTrue(said.equals("Breaking blocks: now on in this world."), said);
            said = run(h, "giants preset easy world");
            h.assertTrue(said.startsWith("Preset easy: health x0.75, damage x0.75 in this world."), said);
            h.assertTrue(guide.store.get("healthMultiplier").getAsDouble() == 0.75, "the preset too");
            said = run(h, "hollowbell damage 50");
            h.assertTrue(said.startsWith("Damage multiplier has to be a number from"), said);
            h.assertTrue(guide.store.get("damageMultiplier").getAsDouble() == 0.75, "still the preset's");
            said = run(h, "hollowbell config soundVolume 1 world");
            h.assertTrue(said.startsWith("soundVolume isn't a world setting"), said);
            var G = BellConfig.fileValues();
            h.assertTrue(G.damageMultiplier == damage && G.healthMultiplier == health && G.grudgeRate == grudge
                    && G.maxInWorld == limit, "the server defaults stay");
            h.assertTrue(file().equals(before), "the config file isn't written");
            h.assertTrue(guide.log.size() == 7, "logged: " + guide.log);
            h.assertTrue(guide.log.get(0).equals("- damageMultiplier null -> 2.5"), guide.log.get(0));
        } finally {
            guide.close();
            saved.putBack();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "scope_all")
    public void allworldsChangesTheFileAndLeavesTheWorldAlone(GameTestHelper h) {
        Saved saved = Saved.now();
        FakeGuide guide = new FakeGuide(h.getLevel().getServer());
        try {
            AdminScope.Done d =
                    AdminScope.write(h.getLevel().getServer(), AdminScope.Scope.WORLD, null, "healthMultiplier", 3f);
            h.assertTrue(d.ok() && d.world(), "the world keeps 3");
            String said = run(h, "giants health 1.5 allworlds");
            h.assertTrue(said.contains("Hollowbell: health x1.5 for all worlds (server default). This world keeps its"
                    + " own: 3 (/giants admin reset hollowbell healthMultiplier)."), said);
            h.assertTrue(BellConfig.fileValues().healthMultiplier == 1.5f, "the file");
            h.assertTrue(BellConfig.V.healthMultiplier == 3f, "the world's still wins");
            h.assertTrue(guide.store.size() == 1 && guide.store.get("healthMultiplier").getAsDouble() == 3.0,
                    "the world's store is untouched");
            run(h, "giants health 3 allworlds");
            h.assertTrue(BellConfig.fileValues().healthMultiplier == 3f, "3 in the file too");
            said = run(h, "hollowbell natural off allworlds");
            h.assertTrue(said.endsWith("for all worlds (server default)."), said);
            h.assertFalse(BellConfig.fileValues().oneInTheWorld, "the file");
            said = run(h, "giants griefing off allworlds");
            h.assertTrue(said.contains("Hollowbell: he doesn't break blocks for all worlds (server default)."), said);
            h.assertTrue(guide.store.size() == 1 && guide.log.size() == 1, "still only the one world value");
        } finally {
            guide.close();
            saved.putBack();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "scope_default")
    public void theGuideCanChangeHisServerDefault(GameTestHelper h) {
        Saved saved = Saved.now();
        MinecraftServer s = h.getLevel().getServer();
        try {
            GiantsBridge.giants(s, "adminset", "{\"admin\":1,\"values\":{\"healthMultiplier\":3}}");
            List<String> r = GiantsBridge.giants(s, "admindefault",
                    "{\"admin\":1,\"id\":\"healthMultiplier\",\"value\":2.5}");
            h.assertTrue(r.equals(List.of("ok")), "" + r);
            h.assertTrue(BellConfig.fileValues().healthMultiplier == 2.5f, "his file");
            h.assertTrue(BellConfig.V.healthMultiplier == 3f && GiantsAdmin.overridden("healthMultiplier"),
                    "the world's own stays on top");
            r = GiantsBridge.giants(s, "admindefault", "{\"admin\":1,\"id\":\"move.grab.on\",\"value\":false}");
            h.assertTrue(r.get(0).equals("no") && r.get(1).contains("world only"), "" + r);
            r = GiantsBridge.giants(s, "admindefault", "{\"admin\":1,\"id\":\"nothing\",\"value\":1}");
            h.assertTrue(r.get(1).contains("unknown"), "" + r);
            r = GiantsBridge.giants(s, "admindefault", "{\"admin\":1,\"id\":\"griefing\",\"value\":3}");
            h.assertTrue(r.get(1).contains("bad value"), "" + r);
            h.assertTrue(GiantsBridge.giants(s, "admindefault", "{\"admin\":2}") == null, "newer: null");
        } finally {
            GiantsAdmin.clear();
            saved.putBack();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "scope_book")
    public void theBooksSwitchesActOnThisWorld(GameTestHelper h) {
        Saved saved = Saved.now();
        HollowbellEntity e = HollowbellGameTests.spawnAway(h, HollowbellGameTests.S, HollowbellEntity.CALM, 1151);
        h.runAfterDelay(10, () -> {
            ServerPlayer p = HollowbellGameTests.player(h, e.position().add(4, 0, 4));
            p.getInventory().add(new ItemStack(ModItems.CODEX));
            var ops = h.getLevel().getServer().getPlayerList().getOps();
            ops.add(new ServerOpListEntry(p.getGameProfile(), 4, false));
            boolean grief = BellConfig.fileValues().griefing, harvest = BellConfig.fileValues().harvest;
            FakeGuide guide = new FakeGuide(h.getLevel().getServer());
            try {
                CodexOrders.handle(p, new CodexPayload(CodexPayload.BREAK_BLOCKS, grief ? 0 : 1));
                CodexOrders.handle(p, new CodexPayload(CodexPayload.HARVEST, harvest ? 0 : 1));
                h.assertTrue(guide.store.containsKey("griefing") && guide.store.containsKey("harvest"),
                        "both kept for this world: " + guide.store + " " + guide.log);
                h.assertTrue(BellConfig.V.griefing == !grief && BellConfig.V.harvest == !harvest, "in use");
                h.assertTrue(BellConfig.fileValues().griefing == grief && BellConfig.fileValues().harvest == harvest,
                        "the server defaults stay");
                h.assertTrue(guide.log.get(0).startsWith(p.getUUID() + " griefing"), "who: " + guide.log);
                guide.close();
                // without the Guide the switches are the server default, as they always were
                CodexOrders.handle(p, new CodexPayload(CodexPayload.HARVEST, harvest ? 0 : 1));
                h.assertTrue(BellConfig.fileValues().harvest == !harvest, "the file now");
            } finally {
                guide.close();
                ops.remove(p.getGameProfile());
                saved.putBack();
            }
            HollowbellGameTests.drop(p);
            HollowbellGameTests.release(h, e);
            h.succeed();
        });
    }
}
