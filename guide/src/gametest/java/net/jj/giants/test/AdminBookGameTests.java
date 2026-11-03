package net.jj.giants.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminBook;
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminInsight;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.admin.AdminTransfer;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.net.AdminBookStatePayload;
import net.minecraft.commands.CommandSource;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;

/**
 * The admin book's server half (ADMIN_BRIDGE.md, "Book payloads"): only operators, every request; a change from the
 * book is exactly the command's change (kept, logged, told, sent to the giants); this world or all worlds; whoever has
 * the book open gets fresh pages when anyone changes anything; and the Home page's danger rating, clashes and preset.
 */
public class AdminBookGameTests implements FabricGameTest {
    private static final Giant FAKE = fake("fakeadmin"), OLD = fake("fakeold");

    private static Giant fake(String key) {
        return new Giant(key, key, key, "net.jj." + key + ".GiantsBridge", List.of(), Set.of());
    }

    /** what the book sent to whom */
    private record Sent(ServerPlayer to, AdminBookStatePayload page) {}

    private static final List<Sent> sent = new ArrayList<>();

    private static MinecraftServer fresh(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        AdminServer.useGiants(List.of(FAKE, OLD));
        AdminWorld w = AdminWorld.get(s);
        for (String g : w.giantsWithValues()) w.clear(g);
        net.jj.fakeadmin.GiantsBridge.reset();
        AdminTransfer.forget();
        AdminBook.forget();
        sent.clear();
        AdminBook.sender = (p, page) -> sent.add(new Sent(p, page));
        return s;
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        MinecraftServer s = h.getLevel().getServer();
        for (ServerPlayer p : players) {
            s.getPlayerList().getOps().remove(p.getGameProfile());
            s.getPlayerList().remove(p);
        }
        AdminBook.forget();
        AdminBook.sender = (p, page) -> {
            if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, AdminBookStatePayload.TYPE))
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, page);
        };
        AdminServer.useGiants(null);
        h.succeed();
    }

    /** a player in the world; an operator (level 4) or not */
    private static ServerPlayer player(GameTestHelper h, boolean op) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        if (op) h.getLevel().getServer().getPlayerList().getOps().add(new ServerOpListEntry(p.getGameProfile(), 4, false));
        return p;
    }

    private static List<AdminBookStatePayload> to(ServerPlayer p, String kind) {
        List<AdminBookStatePayload> out = new ArrayList<>();
        for (Sent x : sent) if (x.to() == p && x.page().kind().equals(kind)) out.add(x.page());
        return out;
    }

    private static JsonObject json(AdminBookStatePayload p) {
        return JsonParser.parseString(p.json()).getAsJsonObject();
    }

    /** the page of a giant (or "all") a player was last sent */
    private static JsonObject lastPage(ServerPlayer p, String giant) {
        JsonObject last = null;
        for (AdminBookStatePayload x : to(p, AdminBookStatePayload.PAGE)) {
            JsonObject o = json(x);
            if (o.get("giant").getAsString().equals(giant)) last = o;
        }
        return last;
    }

    private static JsonElement valueOn(JsonObject page, String id) {
        for (JsonElement e : page.getAsJsonArray("settings"))
            if (e.getAsJsonObject().get("id").getAsString().equals(id)) return e.getAsJsonObject().get("value");
        return null;
    }

    private static String set(String id, String value, boolean allWorlds) {
        return AdminBook.setJson("fakeadmin", id, null, value, allWorlds);
    }

    // who may

    @GameTest(template = EMPTY_STRUCTURE)
    public void theBookIsForOperatorsOnly(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        ServerPlayer steve = player(h, false);
        h.assertFalse(AdminBook.allowed(steve), "not an operator");
        h.assertFalse(AdminBook.open(steve), "the book doesn't open for him");
        AdminBook.ask(s, steve, "");
        h.assertFalse(AdminBook.watching(steve), "no pages for him");
        h.assertTrue(to(steve, AdminBookStatePayload.HOME).isEmpty(), "no Home page sent");
        h.assertTrue(to(steve, AdminBookStatePayload.DENIED).size() == 1, "told it is for admins");
        h.assertTrue(json(to(steve, AdminBookStatePayload.DENIED).get(0)).get("line").getAsString()
                .equals("This book is for admins."), "the words");
        // a change asked for anyway (a modified client): refused, nothing kept, nothing sent to the giant
        AdminServer.Result r = AdminBook.change(s, steve, set("power", "3", false));
        h.assertFalse(r.ok(), "refused");
        h.assertTrue(r.lines().equals(List.of("This book is for admins.")), "" + r.lines());
        h.assertTrue(AdminWorld.get(s).values("fakeadmin").isEmpty(), "nothing kept");
        h.assertFalse(net.jj.fakeadmin.GiantsBridge.values.has("power"), "nothing sent to the giant");
        AdminBook.change(s, steve, set("power", "3", true));
        h.assertFalse(net.jj.fakeadmin.GiantsBridge.defaults.has("power"), "nor the server default");
        int logged = AdminWorld.get(s).log().size();
        AdminBook.change(s, steve, "{\"do\":\"reset\",\"giant\":\"everything\",\"scope\":\"world\"}");
        h.assertTrue(AdminWorld.get(s).log().size() == logged, "nothing logged");
        // an operator may
        ServerPlayer alex = player(h, true);
        h.assertTrue(AdminBook.allowed(alex), "an operator");
        AdminBook.ask(s, alex, "");
        h.assertTrue(AdminBook.watching(alex), "she has it open");
        h.assertTrue(to(alex, AdminBookStatePayload.HOME).size() == 1, "Home sent");
        h.assertTrue(lastPage(alex, "fakeadmin") != null && lastPage(alex, AdminCatalog.ALL) != null,
                "her giant's page and the all-giants page");
        h.assertTrue(lastPage(alex, "fakeold") == null, "no page for a giant without admin settings");
        // made a non-operator while it's open: the next request is refused and the book stops
        s.getPlayerList().getOps().remove(alex.getGameProfile());
        h.assertFalse(AdminBook.change(s, alex, set("power", "2", false)).ok(), "no longer");
        h.assertFalse(AdminBook.watching(alex), "and no more pages");
        done(h, steve, alex);
    }

    // the book's change is the command's change

    @GameTest(template = EMPTY_STRUCTURE)
    public void aChangeFromTheBookIsTheCommandsChange(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        ServerPlayer alex = player(h, true);
        String name = alex.getGameProfile().getName();
        AdminServer.Result book = AdminBook.change(s, alex, set("power", "3", false));
        h.assertTrue(book.ok(), "done: " + book.lines());
        AdminWorld.Change byBook = AdminWorld.get(s).log().get(AdminWorld.get(s).log().size() - 1);
        Map<String, JsonElement> keptByBook = AdminWorld.get(s).values("fakeadmin");
        JsonElement giantGot = net.jj.fakeadmin.GiantsBridge.values.get("power");
        // the answer the book shows is the command's own
        List<AdminBookStatePayload> said = to(alex, AdminBookStatePayload.SAID);
        h.assertTrue(said.size() == 1, "one answer");
        JsonObject answer = json(said.get(0));
        h.assertTrue(answer.get("ok").getAsBoolean(), "ok");
        h.assertTrue(answer.getAsJsonArray("lines").get(0).getAsString()
                .equals("Fakeadmin: Power set to 3 in this world (was 5)."), answer.toString());
        h.assertTrue(book.announce().equals(List.of(name + " set Fakeadmin Power to 3.")), "" + book.announce());
        // the same by command, from the same player
        fresh(h);
        Heard heard = new Heard();
        s.getCommands().performPrefixedCommand(alex.createCommandSourceStack().withSource(heard).withPermission(2),
                "giants admin set fakeadmin power 3");
        AdminWorld.Change byCommand = AdminWorld.get(s).log().get(AdminWorld.get(s).log().size() - 1);
        h.assertTrue(heard.lines.equals(book.lines()), "the same words: " + heard.lines + " / " + book.lines());
        h.assertTrue(AdminWorld.get(s).values("fakeadmin").equals(keptByBook), "the same value kept");
        h.assertTrue(net.jj.fakeadmin.GiantsBridge.values.get("power").equals(giantGot), "the same sent");
        h.assertTrue(byBook.who().equals(name) && byCommand.who().equals(name), "who");
        h.assertTrue(byBook.giant().equals(byCommand.giant()) && byBook.id().equals(byCommand.id())
                && byBook.name().equals(byCommand.name()) && byBook.old().equals(byCommand.old())
                && byBook.now().equals(byCommand.now()) && byBook.undo().equals(byCommand.undo()),
                "the same log entry");
        // a bad value: the command's refusal, nothing changed
        AdminServer.Result bad = AdminBook.change(s, alex, set("power", "99", false));
        h.assertFalse(bad.ok(), "refused");
        h.assertTrue(bad.lines().get(0).equals("Power has to be a whole number from 0 to 10."), "" + bad.lines());
        // every type the book's controls send
        h.assertTrue(AdminBook.change(s, alex, set("angry", "on", false)).ok(), "a toggle");
        h.assertTrue(AdminBook.change(s, alex, set("mood", "hunting", false)).ok(), "a choice");
        h.assertTrue(AdminBook.change(s, alex, set("sizes", "0.5-1.5", false)).ok(), "a range");
        h.assertTrue(AdminBook.change(s, alex, set("speedy", "1.25", false)).ok(), "a slider");
        h.assertTrue(AdminBook.change(s, alex, set("move.bite.on", "off", false)).ok(), "a move");
        h.assertTrue(AdminBook.change(s, alex, AdminBook.setJson("all", "all.health", null, "2", false)).ok(),
                "an all-giants multiplier");
        h.assertTrue(net.jj.fakeadmin.GiantsBridge.all.get("all.health").getAsDouble() == 2.0, "sent to him");
        done(h, alex);
    }

    /** everything a command says */
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
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void thisWorldOrAllWorlds(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        ServerPlayer alex = player(h, true);
        // all worlds: the server default (his config file); the world keeps nothing
        AdminServer.Result r = AdminBook.change(s, alex, set("power", "7", true));
        h.assertTrue(r.ok(), "" + r.lines());
        h.assertTrue(r.lines().get(0).equals("Fakeadmin: Power set to 7 for all worlds (server default; was 5)."),
                r.lines().get(0));
        h.assertTrue(net.jj.fakeadmin.GiantsBridge.defaults.get("power").getAsInt() == 7, "his file");
        h.assertTrue(AdminWorld.get(s).values("fakeadmin").isEmpty(), "the world keeps nothing");
        AdminWorld.Change c = AdminWorld.get(s).log().get(AdminWorld.get(s).log().size() - 1);
        h.assertTrue(c.name().equals("Power (server default)") && c.undo().isEmpty(), "logged as the server default");
        // back to what his mod ships with (4), for all worlds
        r = AdminBook.change(s, alex, "{\"do\":\"reset\",\"giant\":\"fakeadmin\",\"id\":\"power\",\"scope\":\"allworlds\"}");
        h.assertTrue(r.ok() && net.jj.fakeadmin.GiantsBridge.defaults.get("power").getAsInt() == 4,
                "the jar's value: " + r.lines());
        // the world's own wins, and the answer says so
        AdminBook.change(s, alex, set("power", "2", false));
        r = AdminBook.change(s, alex, set("power", "6", true));
        h.assertTrue(r.lines().get(0).contains("This world keeps its own: 2"), r.lines().get(0));
        // only kept for each world: the all-giants multipliers and the moves
        r = AdminBook.change(s, alex, AdminBook.setJson("all", "all.health", null, "2", true));
        h.assertFalse(r.ok(), "all-giants for all worlds");
        h.assertTrue(r.lines().get(0).startsWith("The all-giants multipliers are only kept for each world"),
                r.lines().get(0));
        r = AdminBook.change(s, alex, set("move.bite.on", "off", true));
        h.assertFalse(r.ok(), "a move for all worlds");
        h.assertTrue(r.lines().get(0).contains("is only kept for each world"), r.lines().get(0));
        // no scope said: this world
        r = AdminBook.change(s, alex, "{\"do\":\"set\",\"giant\":\"fakeadmin\",\"id\":\"power\",\"value\":\"1\"}");
        h.assertTrue(r.ok() && AdminWorld.get(s).values("fakeadmin").get("power").getAsInt() == 1, "this world");
        done(h, alex);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void resetsFromTheBook(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        ServerPlayer alex = player(h, true);
        AdminBook.change(s, alex, set("power", "3", false));
        AdminBook.change(s, alex, set("angry", "on", false));
        AdminBook.change(s, alex, AdminBook.setJson("all", "all.damage", null, "2", false));
        AdminServer.Result r = AdminBook.change(s, alex,
                "{\"do\":\"reset\",\"giant\":\"fakeadmin\",\"id\":\"power\",\"scope\":\"world\"}");
        h.assertTrue(r.lines().get(0).equals("Fakeadmin: Power back to the server default, 5."), r.lines().get(0));
        r = AdminBook.change(s, alex, "{\"do\":\"reset\",\"giant\":\"fakeadmin\",\"scope\":\"world\"}");
        h.assertTrue(r.lines().get(0).equals("Fakeadmin: 1 setting back to the server defaults."), r.lines().get(0));
        h.assertTrue(AdminWorld.get(s).values("fakeadmin").isEmpty(), "his are gone");
        h.assertTrue(!AdminWorld.get(s).values(AdminCatalog.ALL).isEmpty(), "the all-giants one stays");
        r = AdminBook.change(s, alex, "{\"do\":\"reset\",\"giant\":\"everything\",\"scope\":\"world\"}");
        h.assertTrue(r.ok() && AdminWorld.get(s).giantsWithValues().isEmpty(), "everything: " + r.lines());
        // things the server can't make sense of
        h.assertFalse(AdminBook.change(s, alex, "not json").ok(), "not JSON");
        h.assertFalse(AdminBook.change(s, alex, "{\"do\":\"explode\",\"giant\":\"fakeadmin\"}").ok(), "unknown");
        h.assertFalse(AdminBook.change(s, alex, "{\"do\":\"set\",\"id\":\"power\",\"value\":\"1\"}").ok(), "no giant");
        h.assertFalse(AdminBook.change(s, alex, "{\"do\":\"set\",\"giant\":\"fakeadmin\",\"value\":\"1\"}").ok(),
                "no setting");
        h.assertTrue(AdminBook.change(s, alex, set("nope", "1", false)).lines().get(0)
                .startsWith("Fakeadmin has no setting called nope."), "an unknown setting");
        done(h, alex);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void tooManyChangesAtOnceAreRefused(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        ServerPlayer alex = player(h, true);
        int ok = 0;
        for (int i = 0; i < 30; i++) if (AdminBook.change(s, alex, set("power", String.valueOf(i % 10), false)).ok()) ok++;
        h.assertTrue(ok <= 20 && ok >= 15, "about a second's worth: " + ok);
        done(h, alex);
    }

    // fresh pages

    @GameTest(template = EMPTY_STRUCTURE)
    public void openBooksGetFreshPagesWhenAnyoneChangesAnything(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        ServerPlayer alex = player(h, true), bob = player(h, true);
        AdminBook.ask(s, alex, "");
        AdminBook.ask(s, bob, "");
        sent.clear();
        // Bob changes one in his book: both books get the new Home and that giant's page (and nothing else)
        AdminBook.change(s, bob, set("power", "2", false));
        AdminBook.flush(s);
        for (ServerPlayer p : List.of(alex, bob)) {
            h.assertTrue(to(p, AdminBookStatePayload.HOME).size() == 1, "Home again");
            JsonObject page = lastPage(p, "fakeadmin");
            h.assertTrue(page != null && valueOn(page, "power").getAsInt() == 2, "his page with the new value");
            h.assertTrue(lastPage(p, AdminCatalog.ALL) == null, "the all-giants page didn't change");
        }
        h.assertTrue(to(bob, AdminBookStatePayload.SAID).size() == 1 && to(alex, AdminBookStatePayload.SAID).isEmpty(),
                "only Bob gets the answer");
        // a command from the console reaches the open books too
        sent.clear();
        s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSource(new Heard()).withPermission(4),
                "giants admin set all speed 1.5");
        AdminBook.flush(s);
        JsonObject all = lastPage(alex, AdminCatalog.ALL);
        h.assertTrue(all != null && valueOn(all, "all.speed").getAsDouble() == 1.5, "the all-giants page");
        // a preset (one log entry for everything): every page
        sent.clear();
        AdminTransfer.apply(s, "T", AdminTransfer.plan(s, AdminTransfer.preset("classic")));
        AdminBook.flush(s);
        h.assertTrue(lastPage(alex, "fakeadmin") != null && lastPage(alex, AdminCatalog.ALL) != null, "every page");
        JsonObject home = json(to(alex, AdminBookStatePayload.HOME).get(0));
        h.assertTrue(home.get("preset").getAsString().equals("Classic"), "Classic again: " + home.get("preset"));
        // nothing changed: nothing sent; a closed book gets nothing
        sent.clear();
        AdminBook.flush(s);
        h.assertTrue(sent.isEmpty(), "nothing new");
        AdminBook.ask(s, alex, "close");
        AdminBook.change(s, bob, set("power", "4", false));
        AdminBook.flush(s);
        h.assertTrue(to(alex, AdminBookStatePayload.HOME).isEmpty(), "Alex closed hers");
        h.assertTrue(to(bob, AdminBookStatePayload.HOME).size() == 1, "Bob's is still open");
        // no longer an operator while it's open: the next fresh pages are a refusal instead
        s.getPlayerList().getOps().remove(bob.getGameProfile());
        sent.clear();
        AdminServer.set(s, "Server", "fakeadmin", "power", "5");
        AdminBook.flush(s);
        h.assertTrue(to(bob, AdminBookStatePayload.HOME).isEmpty() && to(bob, AdminBookStatePayload.DENIED).size() == 1,
                "Bob's book shuts");
        h.assertFalse(AdminBook.watching(bob), "and stays shut");
        done(h, alex, bob);
    }

    // the pages

    @GameTest(template = EMPTY_STRUCTURE)
    public void pagesCarryEverySettingAsTheGiantSaidIt(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        AdminServer.set(s, "T", "fakeadmin", "power", "3");
        AdminJson.Report r = AdminServer.report(s, FAKE);
        JsonObject page = json(AdminBook.page(s, "fakeadmin"));
        h.assertTrue(page.get("name").getAsString().equals("Fakeadmin"), "his name");
        JsonArray list = page.getAsJsonArray("settings");
        h.assertTrue(list.size() == r.settings().size(), "every setting");
        for (int i = 0; i < list.size(); i++) {
            AdminSetting back = AdminJson.setting("fakeadmin", list.get(i).getAsJsonObject());
            AdminSetting was = r.settings().get(i);
            h.assertTrue(back != null && back.equals(was), "read back the same: " + was.id());
        }
        h.assertTrue(page.getAsJsonArray("uses").size() == 2, "what he applies");
        JsonObject all = json(AdminBook.page(s, AdminCatalog.ALL));
        h.assertTrue(all.getAsJsonArray("settings").size() == AdminCatalog.shared().size(), "the all-giants page");
        for (JsonElement e : all.getAsJsonArray("settings"))
            h.assertTrue(AdminJson.setting(AdminCatalog.ALL, e.getAsJsonObject(), true) != null, "all-giants rows read");
        h.assertTrue(AdminJson.setting(AdminCatalog.ALL, all.getAsJsonArray("settings").get(0).getAsJsonObject())
                == null, "a giant's list still may not hold an all-giants id");
        h.assertTrue(AdminBook.page(s, "fakeold") == null, "no page for an old giant");
        // Home: the giants, what each has set, the rating, the clashes, the last change
        JsonObject home = json(AdminBook.home(s));
        JsonArray giants = home.getAsJsonArray("giants");
        h.assertTrue(giants.size() == 2, "both giants");
        JsonObject fa = giants.get(0).getAsJsonObject(), fo = giants.get(1).getAsJsonObject();
        h.assertTrue(fa.get("speaks").getAsBoolean() && fa.get("set").getAsInt() == 1, "his count");
        h.assertFalse(fo.get("speaks").getAsBoolean(), "the old one doesn't speak it");
        h.assertTrue(home.getAsJsonObject("danger").get("level").getAsInt() >= 1, "a rating");
        // (Custom, or the last preset another test applied, changed since)
        String preset = home.get("preset").getAsString();
        h.assertTrue(preset.equals("Custom") || preset.endsWith(", changed"), "changed since: " + preset);
        h.assertTrue(home.getAsJsonObject("last").get("text").getAsString().equals("Fakeadmin Power 5 → 3"),
                home.getAsJsonObject("last").toString());
        done(h);
    }

    // the danger rating, the clashes and the preset (pure)

    private static Map<String, Double> map(Object... kv) {
        Map<String, Double> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], ((Number) kv[i + 1]).doubleValue());
        return m;
    }

    private static final Set<String> USES = Set.of("all.health", "all.damage", "all.speed", "all.size",
            "all.cooldowns", "all.healing", "all.windCost");

    /** a giant as his jar ships him, then with some values changed */
    private static AdminInsight.View giant(String key, Object... changed) {
        Map<String, Double> jar = map("healthMultiplier", 1, "damageMultiplier", 1, "health", 6000, "passiveHeal", 1,
                "healHomePerMinute", 2, "healAwayPerMinute", 1, "griefing", 0, "grudgeRate", 1, "fleeInWildFights", 1,
                "yieldAt", 0.25, "oneInTheWorld", 1, "maxInWorld", 1, "chunkLoading", 1, "bookCosts", 1,
                "windSeconds", 600, "wardBlocks", 400, "wardSeconds", 600, "worldScale", 1);
        Map<String, Double> now = new HashMap<>(jar);
        now.putAll(map(changed));
        return AdminInsight.view(key, Character.toUpperCase(key.charAt(0)) + key.substring(1), now, jar, USES);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void dangerRatingFollowsTheSettings(GameTestHelper h) {
        List<AdminInsight.View> two = List.of(giant("wreckback"), giant("pitchgut"));
        AdminInsight.Danger d = AdminInsight.danger(two, Map.of());
        h.assertTrue(d.level() == 3 && d.label().equals("Normal"), "as shipped: 3, " + d);
        h.assertTrue(d.reason().equals("Every giant at his usual strength."), d.reason());
        // Titans: 2.5x health, 3x damage, faster, short waits
        d = AdminInsight.danger(two, map("all.health", 2.5, "all.damage", 3, "all.speed", 1.25, "all.cooldowns", 0.5));
        h.assertTrue(d.level() == 5 && d.label().equals("Brutal"), "Titans: " + d);
        h.assertTrue(d.reason().startsWith("Damage x3"), "the biggest reason first: " + d.reason());
        // the old /giants preset hard (health x1.5, damage x1.35): Hard
        d = AdminInsight.danger(two, map("all.health", 1.5, "all.damage", 1.35));
        h.assertTrue(d.level() == 4, "hard: " + d);
        // gentle: weak, no healing, none come on their own
        List<AdminInsight.View> gentle = List.of(giant("wreckback", "oneInTheWorld", 0, "passiveHeal", 0),
                giant("pitchgut", "oneInTheWorld", 0, "passiveHeal", 0));
        d = AdminInsight.danger(gentle, map("all.damage", 0.25, "all.health", 0.5));
        h.assertTrue(d.level() == 1 && d.label().equals("Gentle"), "gentle: " + d);
        h.assertTrue(d.reason().contains("none come on their own"), d.reason());
        // a giant's own multiplier counts, averaged over the giants
        d = AdminInsight.danger(List.of(giant("wreckback", "damageMultiplier", 4), giant("pitchgut")), Map.of());
        h.assertTrue(d.level() == 4 && d.reason().equals("Damage x2."), "one giant x4 of two: " + d);
        // griefing and grudges push it up a little; easy settings down
        double base = AdminInsight.danger(two, Map.of()).score();
        h.assertTrue(AdminInsight.danger(List.of(giant("a", "griefing", 1), giant("b", "griefing", 1)), Map.of())
                .score() > base, "griefing");
        h.assertTrue(AdminInsight.danger(List.of(giant("a", "grudgeRate", 4)), Map.of()).score() > base, "grudges");
        h.assertTrue(AdminInsight.danger(two, map("all.damage", 0.75, "all.health", 0.75)).level() == 2, "easy");
        // a multiplier a giant doesn't apply changes nothing for him
        AdminInsight.View deaf = AdminInsight.view("x", "X", map("damageMultiplier", 1), map("damageMultiplier", 1),
                Set.of());
        h.assertTrue(AdminInsight.danger(List.of(deaf), map("all.damage", 5)).level() == 3, "not his");
        // no giants at all: the all-giants multipliers alone
        h.assertTrue(AdminInsight.danger(List.of(), map("all.damage", 3)).level() >= 4, "no giants");
        h.succeed();
    }

    private static List<String> rules(List<AdminInsight.Warning> w) {
        return w.stream().map(AdminInsight.Warning::rule).toList();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void clashesAreFoundAndOnlyWhenTheyAreReal(GameTestHelper h) {
        h.assertTrue(AdminInsight.ruleIds().equals(List.of("grief_unloaded", "heal_rates_zero", "heal_rates_unused",
                "never_backs_down", "ward_no_time", "free_orders", "my_giant_one", "wind_unused", "size_capped",
                "count_capped", "count_alone", "seek_no_fight", "meet_fight_off", "seek_no_meet", "raid_peaceful",
                "stay_roams", "herd_alone", "tiny_war")),
                "" + AdminInsight.ruleIds());
        h.assertTrue(AdminInsight.warnings(List.of(giant("a"), giant("b")), Map.of()).isEmpty(), "as shipped: none");
        // each rule, alone
        Object[][] cases = {
            {"grief_unloaded", new Object[] {"griefing", 1, "chunkLoading", 0}, Map.of()},
            {"heal_rates_zero", new Object[] {"healHomePerMinute", 0, "healAwayPerMinute", 0}, Map.of()},
            {"heal_rates_unused", new Object[] {"passiveHeal", 0, "healHomePerMinute", 4}, Map.of()},
            {"never_backs_down", new Object[] {"yieldAt", 0}, Map.of()},
            {"ward_no_time", new Object[] {"wardSeconds", 0}, Map.of()},
            {"free_orders", new Object[] {}, map("all.windCost", 0)},
            {"wind_unused", new Object[] {"bookCosts", 0, "windSeconds", 60}, Map.of()},
            {"size_capped", new Object[] {"worldScale", 1.5}, map("all.size", 2)}
        };
        for (Object[] c : cases) {
            @SuppressWarnings("unchecked")
            Map<String, Double> all = (Map<String, Double>) c[2];
            List<AdminInsight.Warning> w = AdminInsight.warnings(List.of(giant("wreckback", (Object[]) c[1])), all);
            h.assertTrue(rules(w).equals(List.of(c[0])), c[0] + ": " + rules(w));
            h.assertTrue(w.get(0).text().startsWith("Wreckback: "), w.get(0).text());
        }
        // and not when the other half is fine
        h.assertTrue(AdminInsight.warnings(List.of(giant("a", "griefing", 1)), Map.of()).isEmpty(), "land loaded");
        h.assertTrue(AdminInsight.warnings(List.of(giant("a", "yieldAt", 0, "fleeInWildFights", 0)), Map.of())
                .isEmpty(), "never backs down on purpose");
        h.assertTrue(AdminInsight.warnings(List.of(giant("a", "worldScale", 0.5)), map("all.size", 2)).isEmpty(),
                "x2 of a half: still within his biggest");
        h.assertTrue(AdminInsight.warnings(List.of(giant("a", "bookCosts", 0)), map("all.windCost", 0)).isEmpty(),
                "no costs anyway");
        // the same clash for two giants: one line, both named, in the plural
        List<AdminInsight.Warning> w = AdminInsight.warnings(List.of(giant("wreckback", "yieldAt", 0),
                giant("pitchgut", "yieldAt", 0)), Map.of());
        h.assertTrue(w.size() == 1 && w.get(0).giants().equals(List.of("wreckback", "pitchgut")), "one line: " + w);
        h.assertTrue(w.get(0).text().startsWith("Wreckback and Pitchgut: ")
                && w.get(0).text().endsWith("so they never back down."), w.get(0).text());
        // the all-giants healing with nobody healing
        w = AdminInsight.warnings(List.of(giant("a", "passiveHeal", 0)), map("all.healing", 3));
        h.assertTrue(rules(w).equals(List.of("healing_unused")), "" + rules(w));
        h.assertTrue(AdminInsight.warnings(List.of(giant("a")), map("all.healing", 3)).isEmpty(), "he heals");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void presetNameComesFromTheLog(GameTestHelper h) {
        List<AdminWorld.Change> log = new ArrayList<>();
        h.assertTrue(AdminInsight.presetName(log, false).equals("Classic"), "nothing set");
        log.add(new AdminWorld.Change(1, "A", "wreckback", "power", "Power", "1", "2", "{}"));
        h.assertTrue(AdminInsight.presetName(log, true).equals("Custom"), "changed without a preset");
        log.add(new AdminWorld.Change(2, "A", "everything", "preset:titans", "preset Titans", "", "9 changes", "{}"));
        h.assertTrue(AdminInsight.presetName(log, true).equals("Titans"), "a preset");
        log.add(new AdminWorld.Change(3, "A", "wreckback", "power", "Power (server default)", "1", "3", ""));
        h.assertTrue(AdminInsight.presetName(log, true).equals("Titans"), "a server default change isn't the world's");
        log.add(new AdminWorld.Change(4, "A", "wreckback", "power", "Power", "1", "3", "{}"));
        h.assertTrue(AdminInsight.presetName(log, true).equals("Titans, changed"), "changed after");
        log.add(new AdminWorld.Change(5, "A", "everything", "import", "imported settings", "", "3 changes", "{}"));
        h.assertTrue(AdminInsight.presetName(log, true).equals("Imported settings"), "an import");
        h.assertTrue(AdminInsight.presetName(log, false).equals("Classic"), "all back: Classic");
        // Simple mode: about twenty, every all-giants multiplier among them
        h.assertTrue(AdminInsight.SIMPLE.size() >= 18 && AdminInsight.SIMPLE.size() <= 22, "about twenty");
        for (AdminSetting s : AdminCatalog.multipliers()) h.assertTrue(AdminInsight.simple(s.id()), s.id());
        h.succeed();
    }

    /** with the eight giants' own mods (-Pgiants): every page reads back, and the rating as shipped is 3 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void realGiantsPagesWhenTheyAreHere(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        AdminServer.useGiants(null);
        List<Giant> here = AdminServer.giants();
        if (here.isEmpty()) {
            h.succeed();
            return;
        }
        AdminWorld w = AdminWorld.get(s);
        for (String g : w.giantsWithValues()) w.clear(g);
        AdminServer.push(s, w);
        int pages = 0;
        for (Giant g : here) {
            AdminBookStatePayload p = AdminBook.page(s, g.key());
            if (p == null) continue;
            pages++;
            JsonObject o = json(p);
            int n = 0;
            for (JsonElement e : o.getAsJsonArray("settings"))
                if (AdminJson.setting(g.key(), e.getAsJsonObject()) != null) n++;
            h.assertTrue(n == AdminServer.report(s, g).settings().size(), g.key() + ": every setting reads back");
            h.assertTrue(p.json().length() < (1 << 19), g.key() + ": fits in one page");
        }
        h.assertTrue(pages == here.size(), "a page for every giant: " + pages + " of " + here.size());
        JsonObject home = json(AdminBook.home(s));
        h.assertTrue(home.getAsJsonObject("danger").get("level").getAsInt() == 3,
                "as the config files have them: Normal (" + home.getAsJsonObject("danger") + ")");
        h.assertTrue(home.get("preset").getAsString().equals("Classic"), "Classic");
        h.succeed();
    }
}
