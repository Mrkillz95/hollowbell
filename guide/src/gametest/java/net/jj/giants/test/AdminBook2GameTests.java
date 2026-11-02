package net.jj.giants.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.GuideBridge;
import net.jj.giants.admin.AdminBook;
import net.jj.giants.admin.AdminLive;
import net.jj.giants.admin.AdminPresets;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSync;
import net.jj.giants.admin.AdminTransfer;
import net.jj.giants.admin.AdminUndo;
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
 * The admin book's Part 2B, server side (ADMIN_BRIDGE.md §13–§17): Live control, roles (Admin / Moderator), undo,
 * snapshots, locks, the Presets page (preview, apply, save, delete), import through the book, the settings sync, and
 * fresh pages after a giant's own allworlds command. Each test sets and clears what it uses.
 */
public class AdminBook2GameTests implements FabricGameTest {
    private static final Giant FAKE = new Giant("fakeadmin", "fakeadmin", "fakeadmin", "net.jj.fakeadmin.GiantsBridge",
            List.of(), Set.of());

    private record Sent(ServerPlayer to, AdminBookStatePayload page) {}

    private static final List<Sent> sent = new ArrayList<>();

    private static MinecraftServer fresh(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        AdminServer.useGiants(List.of(FAKE));
        AdminWorld w = AdminWorld.get(s);
        for (String g : w.giantsWithValues()) w.clear(g);
        w.forgetExtras();
        net.jj.fakeadmin.GiantsBridge.reset();
        AdminTransfer.forget();
        AdminBook.forget();
        try {
            Path dir = s.getServerDirectory().resolve("book2-tests");
            Files.createDirectories(dir);
            for (String f : List.of("presets.json", "sync.txt", "sync.applied.json")) Files.deleteIfExists(dir.resolve(f));
            AdminPresets.dirOverride = dir;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        AdminPresets.forget();
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
        AdminWorld.get(s).forgetExtras();
        AdminBook.forget();
        AdminBook.sender = (p, page) -> {
            if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, AdminBookStatePayload.TYPE))
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, page);
        };
        AdminPresets.dirOverride = null;
        AdminPresets.forget();
        AdminServer.useGiants(null);
        h.succeed();
    }

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

    private static JsonObject last(ServerPlayer p, String kind) {
        List<AdminBookStatePayload> l = to(p, kind);
        return l.isEmpty() ? null : JsonParser.parseString(l.get(l.size() - 1).json()).getAsJsonObject();
    }

    private static String req(String what, Object... kv) {
        JsonObject o = new JsonObject();
        o.addProperty("do", what);
        for (int i = 0; i < kv.length; i += 2)
            if (kv[i + 1] instanceof Number n) o.addProperty((String) kv[i], n);
            else o.addProperty((String) kv[i], String.valueOf(kv[i + 1]));
        return o.toString();
    }

    private static String set(String id, String v) {
        return AdminBook.setJson("fakeadmin", id, null, v, false);
    }

    private static JsonElement value(MinecraftServer s, String id) {
        return AdminWorld.get(s).values("fakeadmin").get(id);
    }

    private static int lastIndex(MinecraftServer s) {
        return AdminWorld.get(s).log().size() - 1;
    }

    /** a few milliseconds, so the next change has its own time */
    private static void pause() {
        try {
            Thread.sleep(3);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** the log entry made at that time */
    private static int indexOf(MinecraftServer s, long time) {
        List<AdminWorld.Change> log = AdminWorld.get(s).log();
        for (int i = log.size() - 1; i >= 0; i--) if (log.get(i).time() == time) return i;
        return -1;
    }

    private static long timeAt(MinecraftServer s, int i) {
        return AdminWorld.get(s).log().get(i).time();
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
    }

    private static String console(MinecraftServer s, String cmd) {
        Heard h = new Heard();
        s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSource(h).withPermission(4), cmd);
        return String.join("\n", h.lines);
    }

    // roles

    @GameTest(template = EMPTY_STRUCTURE)
    public void aPlayerWhoIsNeitherIsRefusedEverything(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        ServerPlayer steve = player(h, false);
        String id = net.jj.fakeadmin.GiantsBridge.stand("calm", 50);
        h.assertTrue(AdminBook.role(steve) == AdminBook.Role.NONE, "no role");
        for (String r : List.of(set("power", "3"), req("live", "giant", "fakeadmin", "id", id, "act", "heal"),
                req("summon", "giant", "fakeadmin", "size", "1"), req("lock", "giant", "fakeadmin", "id", "power"),
                req("undo", "i", 0, "time", 0), req("preset", "name", "classic"), req("export"),
                req("savepreset", "name", "X"), req("mod", "player", steve.getStringUUID(), "on", "true"))) {
            AdminServer.Result res = AdminBook.change(s, steve, r);
            h.assertFalse(res.ok(), "refused: " + r);
        }
        AdminBook.ask(s, steve, "live");
        h.assertFalse(AdminBook.watchingLive(steve), "no live page");
        h.assertTrue(to(steve, AdminBookStatePayload.LIVE).isEmpty() && to(steve, AdminBookStatePayload.EXPORT).isEmpty()
                && to(steve, AdminBookStatePayload.PREVIEW).isEmpty(), "nothing sent but refusals");
        h.assertTrue(net.jj.fakeadmin.GiantsBridge.acts.isEmpty(), "no giant touched");
        h.assertTrue(AdminWorld.get(s).values("fakeadmin").isEmpty() && AdminWorld.get(s).mods().isEmpty()
                && AdminWorld.get(s).locks().isEmpty(), "nothing changed");
        done(h, steve);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aModeratorHasLiveControlOnly(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        ServerPlayer mo = player(h, false), alex = player(h, true);
        h.assertFalse(mo.hasPermissions(2), "Mo is no operator (" + mo.getUUID() + " / " + alex.getUUID() + ")");
        String id = net.jj.fakeadmin.GiantsBridge.stand("hunting", 40);
        // an admin makes Mo a moderator in the book (kept with the world; Mo gets a book)
        AdminServer.Result r = AdminBook.change(s, alex, req("mod", "player", mo.getStringUUID(), "on", "true"));
        h.assertTrue(r.ok() && AdminWorld.get(s).isMod(mo.getUUID()), "made a moderator: " + r.lines());
        h.assertTrue(AdminBook.role(mo) == AdminBook.Role.MODERATOR, "role");
        h.assertTrue(mo.getInventory().contains(new net.minecraft.world.item.ItemStack(
                net.jj.giants.admin.AdminBookItem.BOOK)), "he got the book");
        // his book: the role and Live control, nothing else
        sent.clear();
        AdminBook.ask(s, mo, "");
        h.assertTrue(last(mo, AdminBookStatePayload.ROLE).get("role").getAsString().equals("moderator"), "told");
        h.assertTrue(to(mo, AdminBookStatePayload.LIVE).size() == 1, "Live control sent");
        h.assertTrue(to(mo, AdminBookStatePayload.HOME).isEmpty() && to(mo, AdminBookStatePayload.PAGE).isEmpty()
                && to(mo, AdminBookStatePayload.CHANGES).isEmpty() && to(mo, AdminBookStatePayload.PRESETS).isEmpty(),
                "no settings pages");
        // live actions: yes
        r = AdminBook.change(s, mo, req("live", "giant", "fakeadmin", "id", id, "act", "heal"));
        h.assertTrue(r.ok(), "heal: " + r.lines());
        h.assertTrue(net.jj.fakeadmin.GiantsBridge.acts.get(0).equals("heal " + id + "  by " + mo.getStringUUID()),
                "the giant was asked, by him: " + net.jj.fakeadmin.GiantsBridge.acts);
        // anything else: no (each re-checked on the server)
        for (String q : List.of(set("power", "3"), req("summon", "giant", "fakeadmin", "size", "1"),
                req("lock", "giant", "fakeadmin", "id", "power"), req("preset", "name", "classic"),
                req("export"), req("undo", "i", 0, "time", 0),
                req("mod", "player", mo.getStringUUID(), "on", "false"))) {
            AdminServer.Result x = AdminBook.change(s, mo, q);
            h.assertFalse(x.ok(), "refused: " + q);
            h.assertTrue(x.lines().get(0).startsWith("Only admins"), x.lines().get(0));
        }
        h.assertTrue(AdminWorld.get(s).values("fakeadmin").isEmpty() && AdminWorld.get(s).locks().isEmpty(),
                "nothing changed");
        // a fresh-pages flush sends him his role only
        sent.clear();
        AdminServer.set(s, "Server", "fakeadmin", "power", "2");
        AdminBook.flush(s);
        h.assertTrue(to(mo, AdminBookStatePayload.PAGE).isEmpty() && to(mo, AdminBookStatePayload.HOME).isEmpty(),
                "no settings pages after a change");
        // taken away: his open book shuts, and he can't do live actions
        r = AdminBook.change(s, alex, req("mod", "player", mo.getStringUUID(), "on", "false"));
        h.assertTrue(r.ok() && !AdminWorld.get(s).isMod(mo.getUUID()), "no longer: " + r.lines());
        sent.clear();
        AdminBook.flush(s);
        h.assertTrue(to(mo, AdminBookStatePayload.DENIED).size() == 1 && !AdminBook.watching(mo), "his book shuts");
        h.assertFalse(AdminBook.change(s, mo, req("live", "giant", "fakeadmin", "id", id, "act", "heal")).ok(),
                "no more live control");
        // the command twins
        String said = console(s, "giants admin moderator add " + mo.getGameProfile().getName());
        h.assertTrue(AdminWorld.get(s).isMod(mo.getUUID()), said);
        h.assertTrue(console(s, "giants admin moderators").contains(mo.getGameProfile().getName()), "listed");
        console(s, "giants admin moderator remove " + mo.getGameProfile().getName());
        h.assertFalse(AdminWorld.get(s).isMod(mo.getUUID()), "removed by command");
        done(h, alex, mo);
    }

    // live control

    @GameTest(template = EMPTY_STRUCTURE)
    public void liveControlListsEveryGiantAndActsOnOne(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        ServerPlayer alex = player(h, true), bob = player(h, true);
        String a = net.jj.fakeadmin.GiantsBridge.stand("hunting", 40), b = net.jj.fakeadmin.GiantsBridge.stand("calm", 90);
        JsonObject st = AdminLive.state(s);
        JsonObject fa = st.getAsJsonArray("giants").get(0).getAsJsonObject();
        h.assertTrue(fa.get("speaks").getAsBoolean() && fa.getAsJsonArray("rows").size() == 2, "both listed: " + fa);
        h.assertTrue(fa.getAsJsonArray("does").size() == 11, "what he can do");
        h.assertTrue(st.getAsJsonArray("players").size() >= 2, "the players online, for the owner");
        // only the one asked for
        AdminServer.Result r = AdminBook.change(s, alex, req("live", "giant", "fakeadmin", "id", a, "act", "size",
                "value", "0.5"));
        h.assertTrue(r.ok() && r.lines().get(0).equals("Fakeadmin: size done."), "" + r.lines());
        h.assertTrue(net.jj.fakeadmin.GiantsBridge.standing.get(a).get("size").getAsDouble() == 0.5
                && net.jj.fakeadmin.GiantsBridge.standing.get(b).get("size").getAsDouble() == 1, "that one only");
        h.assertTrue(r.announce().get(0).contains("resized a Fakeadmin"), "the other operators are told");
        r = AdminBook.change(s, alex, req("live", "giant", "fakeadmin", "id", a, "act", "kill"));
        h.assertTrue(r.ok() && !net.jj.fakeadmin.GiantsBridge.standing.containsKey(a), "killed");
        h.assertFalse(AdminBook.change(s, alex, req("live", "giant", "fakeadmin", "id", a, "act", "heal")).ok(),
                "gone: his own words");
        h.assertFalse(AdminBook.change(s, alex, req("live", "giant", "fakeadmin", "id", b, "act", "explode")).ok(),
                "an action he doesn't list");
        h.assertFalse(AdminBook.change(s, alex, req("live", "giant", "nobody", "id", b, "act", "heal")).ok(),
                "no such giant");
        // summon: his own command run as Alex; there's no /fakeadmin, so it says it didn't work
        r = AdminBook.change(s, alex, req("summon", "giant", "fakeadmin", "size", "1"));
        h.assertFalse(r.ok(), "no summon command here: " + r.lines());
        // the page goes out once a second only to whoever is looking at it
        sent.clear();
        AdminBook.ask(s, alex, "live");
        h.assertTrue(AdminBook.watchingLive(alex) && to(alex, AdminBookStatePayload.LIVE).size() == 1, "sent at once");
        AdminBook.flushLive(s);
        h.assertTrue(to(alex, AdminBookStatePayload.LIVE).size() == 2 && to(bob, AdminBookStatePayload.LIVE).isEmpty(),
                "only to her");
        AdminBook.ask(s, alex, "live_off");
        AdminBook.flushLive(s);
        h.assertTrue(to(alex, AdminBookStatePayload.LIVE).size() == 2, "not after she left the page");
        done(h, alex, bob);
    }

    // undo and snapshots

    @GameTest(template = EMPTY_STRUCTURE)
    public void undoOneChangeOrBackToHere(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        ServerPlayer alex = player(h, true);
        // (the log keeps 100: indices move as it fills, so each entry is found by its time)
        AdminBook.change(s, alex, set("power", "3"));
        long firstT = timeAt(s, lastIndex(s));
        pause();
        AdminBook.change(s, alex, set("angry", "on"));
        long secondT = timeAt(s, lastIndex(s));
        pause();
        AdminBook.change(s, alex, set("power", "7"));
        int second = indexOf(s, secondT);
        // one: angry back to the server default; power stays 7
        AdminServer.Result r = AdminBook.change(s, alex, req("undo", "i", second, "time", secondT));
        h.assertTrue(r.ok() && value(s, "angry") == null && value(s, "power").getAsInt() == 7, "" + r.lines());
        h.assertTrue(net.jj.fakeadmin.GiantsBridge.values.get("angry") == null, "sent to the giant");
        AdminWorld.Change u = AdminWorld.get(s).log().get(lastIndex(s));
        h.assertTrue(u.id().equals("undo") && AdminUndo.undoable(u), "logged, and undoable itself");
        // an undo undone: angry on again
        r = AdminBook.change(s, alex, req("undo", "i", lastIndex(s), "time", timeAt(s, lastIndex(s))));
        h.assertTrue(value(s, "angry") != null && value(s, "angry").getAsBoolean(), "redone: " + r.lines());
        // back to here: before the first change, nothing set
        r = AdminBook.change(s, alex, req("undoto", "i", indexOf(s, firstT), "time", firstT));
        h.assertTrue(r.ok() && AdminWorld.get(s).values("fakeadmin").isEmpty(), "all back: " + r.lines());
        h.assertTrue(r.lines().get(0).startsWith("Undone back to"), r.lines().get(0));
        // a stale index (the log moved on) and a server-default change can't be undone
        h.assertFalse(AdminBook.change(s, alex, req("undo", "i", lastIndex(s), "time", 1)).ok(), "stale");
        AdminServer.setDefault(s, "T", "fakeadmin", "power", "6");
        r = AdminBook.change(s, alex, req("undo", "i", lastIndex(s), "time", timeAt(s, lastIndex(s))));
        h.assertFalse(r.ok(), "server default");
        // the command twin
        console(s, "giants admin set fakeadmin power 2");
        console(s, "giants admin undo");
        h.assertTrue(value(s, "power") == null, "/giants admin undo");
        done(h, alex);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void snapshotsBeforePresetsImportsAndResetsAndRestore(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        ServerPlayer alex = player(h, true);
        AdminWorld w = AdminWorld.get(s);
        AdminBook.change(s, alex, set("power", "3"));
        AdminBook.change(s, alex, set("speedy", "1.5"));
        // a preset (Classic: everything back): a snapshot of how it was first
        AdminBook.change(s, alex, req("preset", "name", "classic"));
        h.assertTrue(AdminBook.change(s, alex, req("apply")).ok(), "applied");
        h.assertTrue(w.values("fakeadmin").isEmpty(), "classic");
        h.assertTrue(w.snapshots().size() == 1 && w.snapshots().get(0).count() == 2
                && w.snapshots().get(0).why().equals("Before the preset Classic"), "snapshot: " + w.snapshots());
        // reset everything: another
        AdminBook.change(s, alex, set("angry", "on"));
        AdminServer.reset(s, "Alex", "everything", null);
        h.assertTrue(w.snapshots().size() == 2 && w.snapshots().get(1).values().get("fakeadmin").containsKey("angry"),
                "before the reset");
        // an import: another
        String text = AdminTransfer.encode(new AdminTransfer.Bundle("import", "", "", false,
                java.util.Map.of("fakeadmin", java.util.Map.of("power", AdminServer.number(9)))));
        AdminBook.change(s, alex, req("import", "text", text));
        AdminBook.change(s, alex, req("apply"));
        h.assertTrue(w.snapshots().size() == 3 && value(s, "power").getAsInt() == 9, "imported");
        // restore the first: power 3, speedy 1.5, nothing else
        sent.clear();
        AdminBook.change(s, alex, req("restore", "i", 0, "time", w.snapshots().get(0).time()));
        JsonObject pv = last(alex, AdminBookStatePayload.PREVIEW);
        h.assertTrue(pv != null && pv.get("kind").getAsString().equals("snapshot") && pv.get("count").getAsInt() == 2,
                "preview: " + pv);
        AdminServer.Result r = AdminBook.change(s, alex, req("apply"));
        h.assertTrue(r.ok() && value(s, "power").getAsInt() == 3 && value(s, "speedy").getAsDouble() == 1.5,
                "restored: " + r.lines());
        h.assertTrue(w.snapshots().size() == 3, "restoring doesn't take one (Undo has it)");
        h.assertTrue(w.log().get(lastIndex(s)).id().equals("snapshot"), "logged");
        // only the last ten are kept
        for (int i = 0; i < 12; i++) w.snapshot("T", "test " + i);
        h.assertTrue(w.snapshots().size() == AdminWorld.MOST_SNAPSHOTS
                && w.snapshots().get(9).why().equals("test 11"), "ten, newest last");
        h.assertTrue(console(s, "giants admin snapshot").contains("test 11"), "/giants admin snapshot");
        done(h, alex);
    }

    // locks

    @GameTest(template = EMPTY_STRUCTURE)
    public void locksKeepASettingFromPresetsImportsOthersAndCommands(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        ServerPlayer alex = player(h, true), bob = player(h, true);
        String an = alex.getGameProfile().getName();
        AdminWorld w = AdminWorld.get(s);
        // locking one the world hasn't set pins it at the value in use (5)
        AdminServer.Result r = AdminBook.change(s, alex, req("lock", "giant", "fakeadmin", "id", "power"));
        h.assertTrue(r.ok() && w.lock("fakeadmin", "power") != null && value(s, "power").getAsInt() == 5,
                "locked at 5: " + r.lines());
        JsonObject page = JsonParser.parseString(AdminBook.page(s, "fakeadmin").json()).getAsJsonObject();
        h.assertTrue(page.getAsJsonObject("locks").get("power").getAsString().equals(an), "the page says who");
        // another admin: refused, in words that name the locker
        // (every test player has the same name: Bob is told apart by name here, as the server does)
        r = AdminServer.set(s, "Bob", "fakeadmin", "power", "2");
        h.assertFalse(r.ok(), "Bob");
        h.assertTrue(r.lines().get(0).contains("is locked by " + an), r.lines().get(0));
        h.assertFalse(AdminServer.setDefault(s, "Bob", "fakeadmin", "power", "2").ok(),
                "nor its server default");
        h.assertFalse(AdminServer.reset(s, "Bob", "fakeadmin", "power").ok(),
                "nor reset");
        // a command (the console), and a giant's own command for this world (GuideBridge)
        h.assertTrue(console(s, "giants admin set fakeadmin power 2").contains("locked by"), "the console");
        List<String> g = GuideBridge.guide(s, "adminworld",
                "{\"admin\":1,\"giant\":\"fakeadmin\",\"id\":\"power\",\"value\":2,\"by\":\"-\"}");
        h.assertTrue(g.get(0).equals("no") && g.get(1).contains("locked by"), "his own command: " + g);
        // presets and imports leave it (and say so); reset everything keeps it
        AdminBook.change(s, alex, set("angry", "on"));
        AdminTransfer.Plan p = AdminTransfer.plan(s, AdminTransfer.preset("classic"));
        h.assertTrue(p.locked().size() == 1 && p.changes().stream().noneMatch(x -> x.id().equals("power")),
                "classic leaves it: " + p.lines());
        AdminTransfer.apply(s, "Alex", p);
        h.assertTrue(value(s, "power").getAsInt() == 5 && value(s, "angry") == null, "kept");
        String imp = AdminTransfer.encode(new AdminTransfer.Bundle("import", "", "", false,
                java.util.Map.of("fakeadmin", java.util.Map.of("power", AdminServer.number(1)))));
        h.assertTrue(AdminTransfer.plan(s, AdminTransfer.decode(imp)).changes().isEmpty(), "an import leaves it");
        AdminServer.reset(s, "Alex", "everything", null);
        h.assertTrue(value(s, "power").getAsInt() == 5, "reset everything keeps it");
        // the locker may change it
        h.assertTrue(AdminBook.change(s, alex, set("power", "4")).ok(), "Alex may");
        // undo by Bob leaves it
        r = AdminUndo.undo(s, "Bob", lastIndex(s), timeAt(s, lastIndex(s)));
        h.assertTrue(value(s, "power").getAsInt() == 4 && r.lines().stream().anyMatch(l -> l.contains("Locked")),
                "undo by Bob leaves it: " + r.lines());
        // any admin unlocks; then Bob can
        r = AdminServer.unlock(s, "Bob", "fakeadmin", "power");
        h.assertTrue(r.ok() && w.lock("fakeadmin", "power") == null, "unlocked: " + r.lines());
        h.assertTrue(AdminServer.set(s, "Bob", "fakeadmin", "power", "2").ok(), "Bob may now");
        // the all-giants ones too, and the command twins
        h.assertTrue(console(s, "giants admin lock all health").contains("locked at x1"), "lock all health");
        h.assertTrue(w.lock("all", "all.health") != null, "kept");
        h.assertTrue(console(s, "giants admin locks").contains("Health"), "/giants admin locks");
        h.assertTrue(console(s, "giants admin unlock all health").contains("unlocked"), "unlock");
        done(h, alex, bob);
    }

    // the Presets page

    @GameTest(template = EMPTY_STRUCTURE)
    public void presetsPreviewApplySaveAndDelete(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        ServerPlayer alex = player(h, true), bob = player(h, true);
        AdminBook.change(s, alex, set("power", "9"));
        AdminBook.change(s, alex, set("landSize", "800"));
        // preview: every change, what only reaches new land, and the rating before and after
        sent.clear();
        AdminBook.change(s, alex, req("preset", "name", "classic"));
        JsonObject pv = last(alex, AdminBookStatePayload.PREVIEW);
        h.assertTrue(pv != null && pv.get("title").getAsString().equals("Classic") && pv.get("count").getAsInt() == 2,
                "preview: " + pv);
        boolean creation = false;
        for (JsonElement e : pv.getAsJsonArray("changes"))
            if (e.getAsJsonObject().get("name").getAsString().equals("Ground size"))
                creation = e.getAsJsonObject().get("creation").getAsBoolean();
        h.assertTrue(creation, "Ground size is marked creation-only");
        h.assertTrue(pv.has("before") && pv.has("after"), "the danger rating before and after");
        h.assertTrue(value(s, "power").getAsInt() == 9, "nothing changed by a preview");
        // save the current settings as a preset (server-wide file)
        AdminServer.Result r = AdminBook.change(s, alex, req("savepreset", "name", "Weekend Fun"));
        h.assertTrue(r.ok() && AdminPresets.find("weekend fun") != null, "saved: " + r.lines());
        h.assertTrue(Files.exists(AdminPresets.dir().resolve("presets.json")), "in the config folder");
        AdminPresets.forget();
        h.assertTrue(AdminPresets.find("Weekend Fun").bundle().values().get("fakeadmin").get("power").getAsInt() == 9,
                "read back from the file");
        h.assertFalse(AdminBook.change(s, alex, req("savepreset", "name", "classic")).ok(), "a built-in name");
        h.assertFalse(AdminBook.change(s, alex, req("savepreset", "name", "bad/name")).ok(), "a bad name");
        h.assertFalse(AdminBook.change(s, bob, req("savepreset", "name", "Weekend Fun")).ok(), "Bob can't take it");
        // apply Classic, then the saved one: power 9 again
        AdminBook.change(s, alex, req("apply"));
        h.assertTrue(AdminWorld.get(s).values("fakeadmin").isEmpty(), "classic applied");
        AdminBook.change(s, alex, req("preset", "name", "Weekend Fun"));
        r = AdminBook.change(s, alex, req("apply"));
        h.assertTrue(r.ok() && value(s, "power").getAsInt() == 9, "the saved one: " + r.lines());
        h.assertTrue(net.jj.giants.admin.AdminInsight.presetName(AdminWorld.get(s).log(), true).equals("Weekend Fun"),
                "Home names it");
        // the Presets page: Classic first and the default; the saved one, Alex's
        sent.clear();
        AdminBook.sendAll(s, alex);
        JsonObject pp = last(alex, AdminBookStatePayload.PRESETS);
        JsonObject classic = pp.getAsJsonArray("builtin").get(0).getAsJsonObject();
        h.assertTrue(classic.get("id").getAsString().equals("classic") && classic.get("default").getAsBoolean(),
                "Classic first, the default");
        JsonArray saved = pp.getAsJsonArray("saved");
        h.assertTrue(saved.size() == 1 && saved.get(0).getAsJsonObject().get("mine").getAsBoolean(), "hers: " + pp);
        // only she deletes it
        h.assertFalse(AdminBook.change(s, bob, req("deletepreset", "name", "Weekend Fun")).ok(), "not Bob");
        h.assertTrue(AdminBook.change(s, alex, req("deletepreset", "name", "Weekend Fun")).ok()
                && AdminPresets.find("Weekend Fun") == null, "deleted");
        h.assertFalse(AdminBook.change(s, alex, req("apply")).ok(), "nothing left to apply");
        done(h, alex, bob);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void exportAndImportThroughTheBook(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        ServerPlayer alex = player(h, true);
        AdminBook.change(s, alex, set("power", "8"));
        AdminBook.change(s, alex, set("mood", "hunting"));
        AdminBook.change(s, alex, AdminBook.setJson("all", "all.damage", null, "2", false));
        sent.clear();
        AdminBook.change(s, alex, req("export"));
        String text = last(alex, AdminBookStatePayload.EXPORT).get("text").getAsString();
        h.assertTrue(text.startsWith(AdminTransfer.PREFIX), "a settings string");
        AdminServer.reset(s, "Alex", "everything", null);
        // a long string (no 256-character chat limit in the book)
        String longer = AdminTransfer.encode(AdminTransfer.decode(text));
        h.assertTrue(longer.equals(text) || longer.length() > 0, "round trip");
        sent.clear();
        AdminBook.change(s, alex, req("import", "text", text));
        JsonObject pv = last(alex, AdminBookStatePayload.PREVIEW);
        h.assertTrue(pv != null && pv.get("kind").getAsString().equals("import") && pv.get("count").getAsInt() == 3,
                "preview: " + pv);
        AdminServer.Result r = AdminBook.change(s, alex, req("apply"));
        h.assertTrue(r.ok() && value(s, "power").getAsInt() == 8 && value(s, "mood").getAsString().equals("hunting")
                && AdminWorld.get(s).values("all").get("all.damage").getAsDouble() == 2, "imported: " + r.lines());
        h.assertFalse(AdminBook.change(s, alex, req("import", "text", "hello")).ok(), "not a settings string");
        // longer than chat allows: a bundle with 300 characters of about
        StringBuilder noise = new StringBuilder();
        java.util.Random rnd = new java.util.Random(7);
        for (int i = 0; i < 300; i++) noise.append((char) ('a' + rnd.nextInt(26)));
        String big = AdminTransfer.encode(new AdminTransfer.Bundle("import", "Big", noise.toString(), true,
                java.util.Map.of("fakeadmin", java.util.Map.of("power", AdminServer.number(1)))));
        h.assertTrue(big.length() > 256, "longer than chat: " + big.length());
        sent.clear();
        AdminBook.change(s, alex, req("import", "text", big));
        h.assertTrue(last(alex, AdminBookStatePayload.PREVIEW) != null, "the book takes it");
        done(h, alex);
    }

    // settings sync

    @GameTest(template = EMPTY_STRUCTURE)
    public void settingsSyncAppliesTheFileAsServerDefaultsWhenItChanged(GameTestHelper h) throws Exception {
        MinecraftServer s = fresh(h);
        h.assertFalse(AdminSync.onStart(s).ran(), "no file: nothing");
        String text = AdminTransfer.encode(new AdminTransfer.Bundle("import", "", "", true, java.util.Map.of(
                "fakeadmin", java.util.Map.of("power", AdminServer.number(7), "move.bite.on",
                        new com.google.gson.JsonPrimitive(false)),
                "all", java.util.Map.of("all.health", AdminServer.number(2)))));
        Files.writeString(AdminSync.file(), text, StandardCharsets.UTF_8);
        AdminSync.Outcome o = AdminSync.onStart(s);
        h.assertTrue(o.ran() && o.applied() == 1, "applied: " + o.lines());
        h.assertTrue(net.jj.fakeadmin.GiantsBridge.defaults.get("power").getAsInt() == 7, "his server default");
        h.assertTrue(AdminWorld.get(s).values("fakeadmin").isEmpty(), "the world's values untouched");
        h.assertTrue(o.skipped().size() == 2, "the world-only ones left out: " + o.skipped());
        h.assertTrue(AdminWorld.get(s).log().get(lastIndex(s)).id().equals("sync"), "logged");
        // the next start: unchanged, nothing done
        net.jj.fakeadmin.GiantsBridge.defaults.remove("power");
        h.assertFalse(AdminSync.onStart(s).ran(), "unchanged");
        h.assertTrue(!net.jj.fakeadmin.GiantsBridge.defaults.has("power"), "not applied again");
        h.assertTrue(String.join(" ", AdminSync.status()).contains("hasn't changed"), "" + AdminSync.status());
        // changed (JSON this time): applied
        Files.writeString(AdminSync.file(), "{\"admin\":1,\"giants\":{\"fakeadmin\":{\"power\":3}}}");
        h.assertTrue(String.join(" ", AdminSync.status()).contains("has changed"), "status says so");
        h.assertTrue(AdminSync.onStart(s).ran() && net.jj.fakeadmin.GiantsBridge.defaults.get("power").getAsInt() == 3,
                "the new file");
        // sync now: again, whatever
        net.jj.fakeadmin.GiantsBridge.defaults.remove("power");
        h.assertTrue(console(s, "giants admin sync now").contains("Settings sync: 1 server default"), "sync now");
        h.assertTrue(net.jj.fakeadmin.GiantsBridge.defaults.get("power").getAsInt() == 3, "applied again");
        Files.writeString(AdminSync.file(), "rubbish");
        h.assertFalse(AdminSync.onStart(s).ran(), "not a settings string: nothing changes");
        done(h);
    }

    // 2A's gap: a giant's own allworlds command

    @GameTest(template = EMPTY_STRUCTURE)
    public void openBooksSeeAGiantsOwnServerDefaultChange(GameTestHelper h) {
        MinecraftServer s = fresh(h);
        ServerPlayer alex = player(h, true);
        AdminBook.ask(s, alex, "");
        AdminBook.lookForQuietChanges(s);
        AdminBook.flush(s);
        sent.clear();
        // his own command writes his config file without the Guide (the fake's defaults)
        net.jj.fakeadmin.GiantsBridge.defaults.add("power", AdminServer.number(6));
        AdminBook.lookForQuietChanges(s);
        AdminBook.flush(s);
        JsonObject page = null;
        for (AdminBookStatePayload p : to(alex, AdminBookStatePayload.PAGE)) {
            JsonObject o = JsonParser.parseString(p.json()).getAsJsonObject();
            if (o.get("giant").getAsString().equals("fakeadmin")) page = o;
        }
        h.assertTrue(page != null, "his page sent again");
        boolean six = false;
        for (JsonElement e : page.getAsJsonArray("settings"))
            if (e.getAsJsonObject().get("id").getAsString().equals("power"))
                six = e.getAsJsonObject().get("default").getAsInt() == 6;
        h.assertTrue(six, "with the new server default");
        sent.clear();
        AdminBook.lookForQuietChanges(s);
        AdminBook.flush(s);
        h.assertTrue(to(alex, AdminBookStatePayload.PAGE).isEmpty(), "nothing new, nothing sent");
        done(h, alex);
    }

    // with the real giants (-Pgiants)

    /** Wreckback and one more: listed for real, summoned at a size, each action on that one */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void realGiantsLiveControlWhenTheyAreHere(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        AdminServer.useGiants(null);
        List<String> keys = new ArrayList<>();
        for (String k : List.of("wreckback", "cerberus"))
            if (Giants.byKey(k) != null && Giants.loaded(Giants.byKey(k))) keys.add(k);
        if (keys.isEmpty()) {
            h.succeed();
            return;
        }
        ServerPlayer alex = player(h, true);
        alex.teleportTo(h.getLevel(), alex.getX() + 2000, 200, alex.getZ() + 2000, 0, 0);
        List<String> made = new ArrayList<>();
        for (String k : keys) {
            AdminServer.Result r = AdminLive.summon(s, alex, k, "0.1", null);
            h.assertTrue(r.ok(), k + " summon: " + r.lines());
            JsonObject row = newest(s, k, made);
            h.assertTrue(row != null, k + ": the summoned one is listed");
            h.assertTrue(Math.abs(row.get("size").getAsDouble() - 0.1) < 0.02, k + ": at size 0.1: " + row);
            h.assertTrue(row.get("state").getAsString().equals("here"), k + ": here");
            h.assertTrue(Math.abs(row.get("x").getAsDouble() - alex.getX()) < 40, k + ": at the op: " + row);
            String id = row.get("id").getAsString();
            made.add(id);
            for (String[] a : new String[][] {{"heal", ""}, {"size", "0.12"}, {"calm", ""}, {"forgive", ""},
                {"wake", ""}, {"tp", ""}}) {
                r = AdminLive.act(s, alex, k, id, a[0], a[1]);
                h.assertTrue(r.ok(), k + " " + a[0] + ": " + r.lines());
            }
            JsonObject after = rowOf(s, k, id);
            h.assertTrue(Math.abs(after.get("size").getAsDouble() - 0.12) < 0.02, k + ": resized: " + after);
            h.assertTrue(after.get("mood").getAsString().equals("calm"), k + ": calm");
            r = AdminLive.act(s, alex, k, id, k.equals("wreckback") ? "remove" : "kill", "");
            h.assertTrue(r.ok(), k + " gone: " + r.lines());
            JsonObject gone = rowOf(s, k, id);
            h.assertTrue(gone == null || !gone.get("state").getAsString().equals("here")
                    || k.equals("cerberus"), k + ": not standing any more");
        }
        s.getPlayerList().getOps().remove(alex.getGameProfile());
        s.getPlayerList().remove(alex);
        h.succeed();
    }

    private static JsonObject rowOf(MinecraftServer s, String key, String id) {
        for (JsonElement g : AdminLive.state(s).getAsJsonArray("giants")) {
            JsonObject k = g.getAsJsonObject();
            if (!k.get("key").getAsString().equals(key) || !k.has("rows")) continue;
            for (JsonElement r : k.getAsJsonArray("rows"))
                if (r.getAsJsonObject().get("id").getAsString().equals(id)) return r.getAsJsonObject();
        }
        return null;
    }

    /** the newest standing one of a kind not already seen */
    private static JsonObject newest(MinecraftServer s, String key, List<String> seen) {
        JsonObject best = null;
        for (JsonElement g : AdminLive.state(s).getAsJsonArray("giants")) {
            JsonObject k = g.getAsJsonObject();
            if (!k.get("key").getAsString().equals(key) || !k.has("rows")) continue;
            for (JsonElement r : k.getAsJsonArray("rows")) {
                JsonObject o = r.getAsJsonObject();
                if (o.get("state").getAsString().equals("here") && !seen.contains(o.get("id").getAsString())
                        && o.has("size") && o.get("size").getAsDouble() < 0.2)
                    best = o;
            }
        }
        return best;
    }
}
