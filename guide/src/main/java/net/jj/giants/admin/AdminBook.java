package net.jj.giants.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jj.giants.Giant;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.net.AdminBookAskPayload;
import net.jj.giants.net.AdminBookChangePayload;
import net.jj.giants.net.AdminBookOpenPayload;
import net.jj.giants.net.AdminBookStatePayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The admin book's server half (ADMIN_BRIDGE.md, "Book payloads" and Part 2B). A book screen asks for its pages and
 * sends requests; every request is checked here, every time (the client is never trusted): an Admin (operator,
 * permission level 2) may do everything, a Moderator (a player an Admin made one, kept with the world) only Live
 * control, anyone else nothing. A change does exactly what the matching /giants admin command does: kept for the
 * world (or the server default), logged, told to the other operators, sent to the giants. Whoever has the book open
 * gets fresh pages whenever anything changes, by the book, a command or a giant's own command; the Live control page
 * is sent again once a second to whoever is looking at it.
 */
public final class AdminBook {
    private AdminBook() {}

    public static final String DENIED = "This book is for admins.";
    /** most requests one player may make in a second (a slider sends one when it is let go) */
    static final int MOST_PER_SECOND = 20;

    /** who may use the book, and for what */
    public enum Role {
        NONE,
        MODERATOR,
        ADMIN;

        public String word() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** who has the book open */
    private static final Set<UUID> watching = new LinkedHashSet<>();
    /** who is looking at the Live control page */
    private static final Set<UUID> liveWatching = new LinkedHashSet<>();
    /** what changed since the pages were last sent ("*": every page) */
    private static final Set<String> dirty = new LinkedHashSet<>();
    private static final Map<UUID, int[]> asked = new HashMap<>();
    /** each giant's list as last seen, to notice changes made behind the Guide's back (his own allworlds command) */
    private static final Map<String, Integer> seenLists = new HashMap<>();

    /** how a page goes to a player: over the network, or (in the tests) to a catcher */
    public static BiConsumer<ServerPlayer, AdminBookStatePayload> sender = (p, s) -> {
        if (ServerPlayNetworking.canSend(p, AdminBookStatePayload.TYPE)) ServerPlayNetworking.send(p, s);
    };

    public static void register() {
        PayloadTypeRegistry.playS2C().register(AdminBookOpenPayload.TYPE, AdminBookOpenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AdminBookStatePayload.TYPE, AdminBookStatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AdminBookAskPayload.TYPE, AdminBookAskPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AdminBookChangePayload.TYPE, AdminBookChangePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(AdminBookAskPayload.TYPE,
                (p, ctx) -> ctx.server().execute(() -> ask(ctx.server(), ctx.player(), p.what())));
        ServerPlayNetworking.registerGlobalReceiver(AdminBookChangePayload.TYPE,
                (p, ctx) -> ctx.server().execute(() -> change(ctx.server(), ctx.player(), p.json())));
        ServerTickEvents.END_SERVER_TICK.register(AdminBook::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> {
            watching.remove(h.getPlayer().getUUID());
            liveWatching.remove(h.getPlayer().getUUID());
            asked.remove(h.getPlayer().getUUID());
            changed("players");
        });
        // (Home lists who is online, for making a moderator)
        ServerPlayConnectionEvents.JOIN.register((h, s, server) -> changed("players"));
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> forget());
    }

    /** nobody watching, nothing waiting (a server stopping, and the tests) */
    public static void forget() {
        watching.clear();
        liveWatching.clear();
        dirty.clear();
        asked.clear();
        seenLists.clear();
    }

    // who may

    /** what this player may do with the book, checked now */
    public static Role role(ServerPlayer p) {
        if (p.hasPermissions(AdminCommand.OPS)) return Role.ADMIN;
        MinecraftServer s = p.getServer();
        if (s != null && AdminWorld.get(s).isMod(p.getUUID())) return Role.MODERATOR;
        return Role.NONE;
    }

    /** may open the book at all (an Admin or a Moderator) */
    public static boolean allowed(ServerPlayer p) {
        return role(p) != Role.NONE;
    }

    public static boolean watching(ServerPlayer p) {
        return watching.contains(p.getUUID());
    }

    public static boolean watchingLive(ServerPlayer p) {
        return liveWatching.contains(p.getUUID());
    }

    /** the book was used: an Admin's or Moderator's game opens it; anyone else is told it is for admins */
    public static boolean open(ServerPlayer p) {
        if (!allowed(p)) {
            p.displayClientMessage(Component.literal(DENIED), true);
            return false;
        }
        if (!ServerPlayNetworking.canSend(p, AdminBookOpenPayload.TYPE)) {
            p.displayClientMessage(Component.literal(
                    "Your game doesn't have the Giants Guide, so the book can't open. /giants admin does the same."),
                    false);
            return false;
        }
        ServerPlayNetworking.send(p, new AdminBookOpenPayload());
        return true;
    }

    private static void refuse(ServerPlayer p, String what) {
        watching.remove(p.getUUID());
        liveWatching.remove(p.getUUID());
        sender.accept(p, denied());
        GiantsGuideMod.LOG.info("Giants admin book: {} {}, but may not", p.getGameProfile().getName(), what);
    }

    /** the screen asks for its pages ("" every page, "live" / "live_off" the Live control page, "close") */
    public static void ask(MinecraftServer server, ServerPlayer p, String what) {
        if (AdminBookAskPayload.CLOSE.equals(what)) {
            watching.remove(p.getUUID());
            liveWatching.remove(p.getUUID());
            net.jj.giants.tools.ToolsAdmin.watch(server, p, false);
            return;
        }
        if (AdminBookAskPayload.LIVE_OFF.equals(what)) {
            liveWatching.remove(p.getUUID());
            return;
        }
        // (Part 6) the Server tools page, looked at or not (admins only: checked there)
        if (what.equals("tools") || what.equals("tools_off")) {
            net.jj.giants.tools.ToolsAdmin.watch(server, p, what.equals("tools"));
            return;
        }
        Role r = role(p);
        if (r == Role.NONE) {
            refuse(p, "asked for its pages");
            return;
        }
        if (tooMany(server, p)) return;
        if (AdminBookAskPayload.LIVE.equals(what)) {
            liveWatching.add(p.getUUID());
            sender.accept(p, live(server));
            return;
        }
        watching.add(p.getUUID());
        sendAll(server, p);
    }

    /** a request from the screen: checked, done the command's way, answered, told to the other operators */
    public static AdminServer.Result change(MinecraftServer server, ServerPlayer p, String json) {
        Role role = role(p);
        if (role == Role.NONE) {
            refuse(p, "asked to change something");
            return AdminServer.Result.fail(DENIED);
        }
        AdminServer.Result r;
        if (tooMany(server, p)) r = AdminServer.Result.fail("Too many changes at once. Wait a moment.");
        else r = request(server, p, role, json);
        if (r != NO_ANSWER) sender.accept(p, said(r));
        if (!r.announce().isEmpty()) AdminServer.announce(server, p, r.announce());
        return r;
    }

    /** (a request answered with its own page instead of the usual words) */
    static final AdminServer.Result NO_ANSWER = new AdminServer.Result(true, List.of(), List.of());

    private static boolean tooMany(MinecraftServer server, ServerPlayer p) {
        int second = server.getTickCount() / 20;
        int[] a = asked.computeIfAbsent(p.getUUID(), k -> new int[] {second, 0});
        if (a[0] != second) {
            a[0] = second;
            a[1] = 0;
        }
        return ++a[1] > MOST_PER_SECOND;
    }

    /** what one request does (the role already checked once; each kind checks what it needs) */
    public static AdminServer.Result request(MinecraftServer server, ServerPlayer p, Role role, String json) {
        JsonObject o = AdminJson.object(json);
        if (o == null) return AdminServer.Result.fail("The book sent something the server can't read.");
        String what = AdminJson.str(o, "do", 16);
        String who = p.getGameProfile().getName();
        // Live control: Admins and Moderators
        if (what.equals("live")) {
            String giant = AdminJson.str(o, "giant", 32), id = AdminJson.str(o, "id", 40),
                    act = AdminJson.str(o, "act", 16), value = AdminJson.str(o, "value", 64);
            if (giant.isEmpty() || id.isEmpty() || act.isEmpty())
                return AdminServer.Result.fail("The book didn't say which giant or what to do.");
            AdminServer.Result r = AdminLive.act(server, p, giant, id, act, value);
            liveSoon = true;
            return r;
        }
        // (JJ answers 2, #15) Watch: moderators too, while the admins allow it (Moderators may watch, on to start with)
        if (role == Role.MODERATOR && (what.equals("spectate") || what.equals("spectatestop"))) {
            if (what.equals("spectate") && !modWatch(server))
                return AdminServer.Result.fail("The admins have turned off watching for moderators.");
            AdminServer.Result wr = net.jj.giants.fun.Fun.request(server, p, who, what, o);
            return wr == null ? AdminServer.Result.fail("The book asked for something the server doesn't know.") : wr;
        }
        // everything else: Admins only
        if (role != Role.ADMIN) {
            GiantsGuideMod.LOG.info("Giants admin book: {} (a moderator) asked for {}, which only admins may", who,
                    what);
            return AdminServer.Result.fail("Only admins can do that. Moderators have Live control.");
        }
        // (Part 6) the Server tools page's requests
        if (net.jj.giants.tools.ToolsAdmin.DOS.contains(what)) {
            AdminServer.Result tr = net.jj.giants.tools.ToolsAdmin.request(server, p, what, o);
            return tr == null ? AdminServer.Result.fail("The book asked for something the server doesn't know.") : tr;
        }
        // (Part 5) the Players page's requests
        if (net.jj.giants.players.PlayersAdmin.DOS.contains(what)) {
            AdminServer.Result pr = net.jj.giants.players.PlayersAdmin.request(server, who, what, o);
            return pr == null ? AdminServer.Result.fail("The book asked for something the server doesn't know.") : pr;
        }
        // (Part 7) the Fun and World tools pages' requests
        if (net.jj.giants.fun.Fun.DOS.contains(what)) {
            AdminServer.Result fr = net.jj.giants.fun.Fun.request(server, p, who, what, o);
            return fr == null ? AdminServer.Result.fail("The book asked for something the server doesn't know.") : fr;
        }
        return switch (what) {
            case "set", "reset" -> settingRequest(server, who, what, o);
            // (Part 2C) one shared setting for every giant at once
            case "setall" -> AdminJson.str(o, "scope", 16).equals("allworlds")
                    ? AdminEvery.setDefault(server, who, AdminJson.str(o, "id", 64), AdminJson.str(o, "value", 256))
                    : AdminEvery.set(server, who, AdminJson.str(o, "id", 64), AdminJson.str(o, "value", 256));
            case "resetall" -> AdminEvery.reset(server, who, AdminJson.str(o, "id", 64));
            case "summon" -> {
                AdminServer.Result r = AdminLive.summon(server, p, AdminJson.str(o, "giant", 32),
                        AdminJson.str(o, "size", 16), AdminJson.str(o, "mood", 16));
                liveSoon = true;
                yield r;
            }
            case "lock" -> AdminServer.lock(server, who, p.getStringUUID(), AdminJson.str(o, "giant", 32),
                    AdminJson.str(o, "id", 64));
            case "unlock" -> AdminServer.unlock(server, who, AdminJson.str(o, "giant", 32), AdminJson.str(o, "id", 64));
            case "undo" -> AdminUndo.undo(server, who, (int) AdminJson.num(o, "i", -1), time(o));
            case "undoto" -> AdminUndo.undoBackTo(server, who, (int) AdminJson.num(o, "i", -1), time(o));
            case "restore" -> {
                AdminTransfer.Plan pl = AdminUndo.restorePlan(server, (int) AdminJson.num(o, "i", -1), time(o));
                if (pl == null)
                    yield AdminServer.Result.fail("That snapshot isn't kept any more. Look again.");
                yield preview(server, p, pl, "snapshot");
            }
            case "preset" -> {
                String name = AdminJson.str(o, "name", 48);
                AdminTransfer.Bundle b = AdminTransfer.preset(name);
                if (b == null) yield AdminServer.Result.fail("No preset called " + name + ".");
                yield preview(server, p, AdminTransfer.plan(server, b), "preset");
            }
            case "import" -> {
                AdminTransfer.Bundle b = AdminTransfer.decode(AdminJson.str(o, "text", AdminBookChangePayload.MOST));
                if (b == null)
                    yield AdminServer.Result.fail("That isn't a settings string: it starts with "
                            + AdminTransfer.PREFIX + " (from Export, or /giants admin export).");
                yield preview(server, p, AdminTransfer.plan(server, b), "import");
            }
            case "apply" -> AdminCommand.confirm(server, p.getStringUUID(), who);
            case "savepreset" -> AdminPresets.save(server, who, p.getStringUUID(), AdminJson.str(o, "name", 48));
            case "deletepreset" -> AdminPresets.delete(who, p.getStringUUID(), AdminJson.str(o, "name", 48));
            case "export" -> {
                String text = AdminTransfer.encode(AdminTransfer.current(server));
                JsonObject e = new JsonObject();
                e.addProperty("text", text);
                sender.accept(p, new AdminBookStatePayload(AdminBookStatePayload.EXPORT, e.toString()));
                GiantsGuideMod.LOG.info("Giants admin export ({}): {}", who, text);
                yield new AdminServer.Result(true, List.of("Copied this world's settings (" + text.length()
                        + " characters). Paste them into Import in another world's book."), List.of());
            }
            case "mod" -> AdminRoles.set(server, who, AdminJson.str(o, "player", 40),
                    !AdminJson.str(o, "on", 8).equals("false"));
            default -> AdminServer.Result.fail("The book asked for something the server doesn't know.");
        };
    }

    private static long time(JsonObject o) {
        try {
            return o.has("time") ? o.get("time").getAsLong() : -1;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** a preview: kept for Apply (the same as /giants admin confirm), and sent to the book as its own page */
    private static AdminServer.Result preview(MinecraftServer server, ServerPlayer p, AdminTransfer.Plan pl,
            String kind) {
        if (!pl.changes().isEmpty()) AdminTransfer.hold(p.getStringUUID(), pl);
        sender.accept(p, new AdminBookStatePayload(AdminBookStatePayload.PREVIEW, previewJson(server, pl, kind)
                .toString()));
        return NO_ANSWER;
    }

    /** a plan as the Presets page shows it: each change, what only reaches new land, locks, the rating before/after */
    public static JsonObject previewJson(MinecraftServer server, AdminTransfer.Plan pl, String kind) {
        return previewJson(AdminView.of(server), pl, kind);
    }

    /** the same from any view (the Create World screen's draft too) */
    public static JsonObject previewJson(AdminView view, AdminTransfer.Plan pl, String kind) {
        JsonObject o = new JsonObject();
        o.addProperty("kind", kind);
        o.addProperty("title", AdminTransfer.title(pl.bundle()));
        o.addProperty("about", pl.bundle().about());
        o.addProperty("count", pl.changes().size());
        JsonArray ch = new JsonArray();
        for (AdminTransfer.Step s : pl.changes()) {
            JsonObject j = new JsonObject();
            j.addProperty("giant", s.giant());
            j.addProperty("gname", AdminServer.giantWord(s.giant()));
            j.addProperty("name", s.name());
            j.addProperty("old", s.oldText());
            j.addProperty("now", s.nowText());
            AdminSetting set = AdminServer.giant(s.giant()) == null ? null
                    : settingOf(view, s.giant(), s.id());
            j.addProperty("creation", set != null && set.creationOnly());
            ch.add(j);
        }
        o.add("changes", ch);
        o.add("creation", strings(pl.creationOnly()));
        o.add("locked", strings(pl.locked()));
        o.add("skipped", strings(pl.skipped()));
        AdminInsight.Danger[] d = AdminTransfer.dangerBeforeAfter(view, pl);
        for (int i = 0; i < 2; i++) {
            JsonObject dj = new JsonObject();
            dj.addProperty("level", d[i].level());
            dj.addProperty("label", d[i].label());
            dj.addProperty("reason", d[i].reason());
            o.add(i == 0 ? "before" : "after", dj);
        }
        return o;
    }

    private static @Nullable AdminSetting settingOf(AdminView view, String giant, String id) {
        Giant g = AdminServer.giant(giant);
        AdminJson.Report r = g == null ? null : view.report(g);
        return r == null ? null : r.setting(id);
    }

    private static JsonArray strings(List<String> l) {
        JsonArray a = new JsonArray();
        for (String s : l) a.add(s);
        return a;
    }

    /** set or reset (2A): the same calls as /giants admin set and reset */
    private static AdminServer.Result settingRequest(MinecraftServer server, String who, String what, JsonObject o) {
        String giant = AdminJson.str(o, "giant", 32), id = AdminJson.str(o, "id", 64),
                value = AdminJson.str(o, "value", 256);
        boolean allWorlds = AdminJson.str(o, "scope", 16).equals("allworlds");
        if (giant.isEmpty()) return AdminServer.Result.fail("The book didn't say which giant.");
        if (what.equals("set")) {
            if (id.isEmpty()) return AdminServer.Result.fail("The book didn't say which setting.");
            return allWorlds
                    ? AdminServer.setDefault(server, who, giant, id, value)
                    : AdminServer.set(server, who, giant, id, value);
        }
        if (!allWorlds) return AdminServer.reset(server, who, giant, id.isEmpty() ? null : id);
        // all worlds: the server default back to what his mod ships with
        if (id.isEmpty()) return AdminServer.Result.fail("Only one setting at a time goes back for all worlds.");
        AdminServer.Found f = AdminServer.setting(server, AdminServer.target(server, giant), id);
        if (f.why() != null) return AdminServer.Result.fail(f.why());
        return AdminServer.setDefault(server, who, giant, id, AdminSetting.text(f.s().jar()));
    }

    /** (2A's entry point, for the tests and the command path) a set/reset request by name, as an admin */
    public static AdminServer.Result request(MinecraftServer server, String who, String json) {
        JsonObject o = AdminJson.object(json);
        if (o == null) return AdminServer.Result.fail("The book sent something the server can't read.");
        String what = AdminJson.str(o, "do", 16);
        if (!what.equals("set") && !what.equals("reset"))
            return AdminServer.Result.fail("The book asked for something the server doesn't know.");
        return settingRequest(server, who, what, o);
    }

    // telling the screens

    /** something changed (AdminWorld logs every change): whoever has the book open gets fresh pages next tick */
    public static void changed(String giant) {
        dirty.add(giant);
    }

    /** the Live control page goes out again on the next tick (after an action), not only once a second */
    private static boolean liveSoon;

    /** each tick: changed pages to open books; once a second the Live control page; every two seconds a look for
     * giants' lists that changed behind the Guide's back */
    public static void tick(MinecraftServer server) {
        int t = server.getTickCount();
        if (!watching.isEmpty() && t % 40 == 0) lookForQuietChanges(server);
        flush(server);
        if (!liveWatching.isEmpty() && (liveSoon || t % 20 == 0)) {
            liveSoon = false;
            flushLive(server);
        }
    }

    /**
     * A giant's own command with allworlds writes his config file without the Guide: his list (the server defaults
     * in it) changes, and the open books are told to fetch his page again.
     */
    public static void lookForQuietChanges(MinecraftServer server) {
        for (Giant g : AdminServer.giants()) {
            List<String> l = AdminServer.call(server, g, AdminServer.LIST, String.valueOf(AdminJson.VERSION));
            int h = l == null ? 0 : l.hashCode();
            Integer was = seenLists.put(g.key(), h);
            if (was != null && was != h) changed(g.key());
        }
    }

    /** sends the pages that changed to everyone with the book open (each tick, and from the tests) */
    public static void flush(MinecraftServer server) {
        if (dirty.isEmpty()) return;
        Set<String> what = new LinkedHashSet<>(dirty);
        dirty.clear();
        if (watching.isEmpty()) return;
        boolean every = what.contains("everything") || what.contains("*");
        // (made once, sent to each admin)
        List<AdminBookStatePayload> pages = new ArrayList<>();
        pages.add(home(server));
        if (every || what.contains(AdminCatalog.ALL)) pages.add(page(server, AdminCatalog.ALL));
        for (Giant g : AdminServer.giants())
            if (every || what.contains(g.key())) {
                AdminBookStatePayload pg = page(server, g.key());
                if (pg != null) pages.add(pg);
            }
        pages.add(changes(server));
        // (Part 5) the Players page: rules, picks, the vote, the leaderboards
        if (every || what.contains("players")) pages.add(net.jj.giants.players.PlayersAdmin.page(server));
        // (Part 6) the Server tools page
        if (every || what.contains(net.jj.giants.tools.ToolsAdmin.KIND)) pages.add(net.jj.giants.tools.ToolsAdmin.page(server));
        boolean presets = every || what.contains("presets");
        for (UUID u : List.copyOf(watching)) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p == null) {
                watching.remove(u);
                liveWatching.remove(u);
                continue;
            }
            Role r = role(p);
            if (r == Role.NONE) {
                // (no longer allowed: the book stops showing anything)
                refuse(p, "had the book open");
                continue;
            }
            sender.accept(p, roleState(p, r));
            if (r != Role.ADMIN) continue;
            for (AdminBookStatePayload pg : pages) sender.accept(p, pg);
            if (presets) sender.accept(p, presets(server, p));
            // (Part 7) the Fun and World tools pages (each admin's own: what they watch, play, their brush)
            if (every || what.contains(net.jj.giants.fun.Fun.PAGE)) sender.accept(p, net.jj.giants.fun.Fun.page(server, p));
        }
    }

    /** the Live control page to whoever is looking at it */
    public static void flushLive(MinecraftServer server) {
        AdminBookStatePayload page = null;
        for (UUID u : List.copyOf(liveWatching)) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p == null) {
                liveWatching.remove(u);
                continue;
            }
            if (role(p) == Role.NONE) {
                refuse(p, "was looking at Live control");
                continue;
            }
            if (page == null) page = live(server);
            sender.accept(p, page);
        }
    }

    /** every page this player may see */
    public static void sendAll(MinecraftServer server, ServerPlayer p) {
        Role r = role(p);
        sender.accept(p, roleState(p, r));
        if (r == Role.ADMIN) {
            sender.accept(p, home(server));
            sender.accept(p, page(server, AdminCatalog.ALL));
            for (Giant g : AdminServer.giants()) {
                AdminBookStatePayload pg = page(server, g.key());
                if (pg != null) sender.accept(p, pg);
            }
            sender.accept(p, changes(server));
            sender.accept(p, presets(server, p));
            sender.accept(p, net.jj.giants.players.PlayersAdmin.page(server));
            sender.accept(p, net.jj.giants.tools.ToolsAdmin.page(server));
            sender.accept(p, net.jj.giants.fun.Fun.page(server, p));
        }
        if (r != Role.NONE) sender.accept(p, live(server));
    }

    /** (JJ answers 2, #15) moderators may watch giants (an admin setting, on to start with) */
    public static boolean modWatch(MinecraftServer server) {
        return net.jj.giants.tools.Tools.on(server, "modWatch");
    }

    static AdminBookStatePayload roleState(ServerPlayer p, Role r) {
        JsonObject o = new JsonObject();
        o.addProperty("role", r.word());
        o.addProperty("name", p.getGameProfile().getName());
        o.addProperty("id", p.getStringUUID());
        // (JJ answers 2, #15) Watch on Live control: may they, and whom they watch now
        MinecraftServer server = p.getServer();
        if (server != null) {
            o.addProperty("watch", r == Role.ADMIN || r == Role.MODERATOR && modWatch(server));
            JsonObject now = net.jj.giants.fun.Fun.watchingJson(p);
            if (now != null) o.add("watching", now);
        }
        return new AdminBookStatePayload(AdminBookStatePayload.ROLE, o.toString());
    }

    static AdminBookStatePayload denied() {
        JsonObject o = new JsonObject();
        o.addProperty("line", DENIED);
        return new AdminBookStatePayload(AdminBookStatePayload.DENIED, o.toString());
    }

    static AdminBookStatePayload said(AdminServer.Result r) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", r.ok());
        JsonArray a = new JsonArray();
        for (String s : r.lines()) a.add(s);
        o.add("lines", a);
        return new AdminBookStatePayload(AdminBookStatePayload.SAID, o.toString());
    }

    public static AdminBookStatePayload live(MinecraftServer server) {
        return new AdminBookStatePayload(AdminBookStatePayload.LIVE, AdminLive.state(server).toString());
    }

    /** the all-giants multipliers in use, as numbers */
    /** (the tests) the all-giants values as numbers, the player switches as 1/0 */
    public static Map<String, Double> allValuesForTests(AdminWorld w) {
        return allValues(w);
    }

    static Map<String, Double> allValues(AdminWorld w) {
        Map<String, Double> all = new HashMap<>();
        for (AdminSetting s : AdminServer.shared(w))
            if (AdminCatalog.multiplier(s)) all.put(s.id(), s.value().getAsDouble());
            // (Part 5: the player switches as 1/0, for the clash rules)
            else if (s.type() == AdminSetting.Type.BOOL) all.put(s.id(), s.value().getAsBoolean() ? 1.0 : 0.0);
        return all;
    }

    /**
     * The Home page: the preset, the danger rating, the clashes, the giants (each with how many settings the world
     * changed), the last change and the moderators.
     */
    public static AdminBookStatePayload home(MinecraftServer server) {
        AdminWorld w = AdminWorld.get(server);
        JsonObject o = homeJson(AdminView.of(server),
                net.jj.giants.tools.Tools.presetName(server));
        List<AdminWorld.Change> log = w.log();
        if (!log.isEmpty()) {
            AdminWorld.Change c = log.get(log.size() - 1);
            JsonObject last = new JsonObject();
            last.addProperty("time", c.time());
            last.addProperty("who", c.who());
            last.addProperty("text", AdminServer.changeText(c));
            o.add("last", last);
        }
        o.addProperty("changes", log.size());
        o.add("mods", AdminRoles.json(server));
        // (the players online, for making one a moderator)
        JsonArray players = new JsonArray();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            JsonObject pj = new JsonObject();
            pj.addProperty("id", p.getStringUUID());
            pj.addProperty("name", p.getGameProfile().getName());
            pj.addProperty("admin", p.hasPermissions(AdminCommand.OPS));
            pj.addProperty("mod", w.isMod(p.getUUID()));
            players.add(pj);
        }
        o.add("players", players);
        o.addProperty("locks", w.locks().size());
        // (Part 6) what the server tools have on now: paused, quiet, an event, a schedule, a modpack
        o.add("tools", net.jj.giants.tools.ToolsAdmin.homeJson(server));
        return new AdminBookStatePayload(AdminBookStatePayload.HOME, o.toString());
    }

    /**
     * Home's part that needs no server (the Create World screen's draft has it too): the preset, the giants (each
     * with how many settings are set), the danger rating and the clashes.
     */
    public static JsonObject homeJson(AdminView view, String presetName) {
        AdminWorld w = view.world();
        JsonObject o = new JsonObject();
        o.addProperty("op", true);
        o.addProperty("preset", presetName);
        List<AdminInsight.View> views = new ArrayList<>();
        JsonArray giants = new JsonArray();
        for (Giant g : AdminServer.giants()) {
            AdminJson.Report r = view.report(g);
            JsonObject j = new JsonObject();
            j.addProperty("key", g.key());
            j.addProperty("name", r != null ? r.name() : AdminServer.nameOf(g));
            j.addProperty("speaks", r != null);
            j.addProperty("set", w.values(g.key()).size());
            if (r != null) {
                j.addProperty("settings", r.settings().size());
                views.add(AdminInsight.view(r));
            }
            giants.add(j);
        }
        o.add("giants", giants);
        o.addProperty("allSet", w.values(AdminCatalog.ALL).size());
        Map<String, Double> all = allValues(w);
        AdminInsight.Danger d = AdminInsight.danger(views, all);
        JsonObject dj = new JsonObject();
        dj.addProperty("level", d.level());
        dj.addProperty("label", d.label());
        dj.addProperty("reason", d.reason());
        o.add("danger", dj);
        JsonArray warn = new JsonArray();
        for (AdminInsight.Warning x : AdminInsight.warnings(views, all)) {
            JsonObject wj = new JsonObject();
            wj.addProperty("rule", x.rule());
            JsonArray who = new JsonArray();
            for (String k : x.giants()) who.add(k);
            wj.add("giants", who);
            wj.addProperty("text", x.text());
            warn.add(wj);
        }
        o.add("warnings", warn);
        return o;
    }

    /** one page: the all-giants settings, or one giant's (null for a giant that has none), with its locks */
    public static @Nullable AdminBookStatePayload page(MinecraftServer server, String key) {
        JsonObject o = pageJson(AdminView.of(server), key);
        if (o == null) return null;
        JsonObject locks = new JsonObject();
        for (var e : AdminWorld.get(server).locks().entrySet())
            if (e.getKey().startsWith(key + "/")) locks.addProperty(e.getKey().substring(key.length() + 1),
                    e.getValue().who());
        o.add("locks", locks);
        return new AdminBookStatePayload(AdminBookStatePayload.PAGE, o.toString());
    }

    /** a page's settings from any view (no locks: the draft has none) */
    public static @Nullable JsonObject pageJson(AdminView view, String key) {
        List<AdminSetting> list;
        String name;
        AdminJson.Report r = null;
        if (key.equals(AdminCatalog.ALL)) {
            list = AdminServer.shared(view.world());
            name = "All giants";
        } else {
            Giant g = AdminServer.giant(key);
            r = g == null ? null : view.report(g);
            if (r == null) return null;
            list = r.settings();
            name = r.name();
        }
        JsonObject o = new JsonObject();
        o.addProperty("giant", key);
        o.addProperty("name", name);
        JsonArray a = new JsonArray();
        for (AdminSetting s : list) a.add(AdminJson.write(s));
        o.add("settings", a);
        if (r != null) {
            JsonArray uses = new JsonArray();
            for (String u : r.uses()) uses.add(u);
            o.add("uses", uses);
        }
        o.add("locks", new JsonObject());
        return o;
    }

    /** the Changes page: the log (newest first, with what can be undone), the snapshots and the locks */
    public static AdminBookStatePayload changes(MinecraftServer server) {
        AdminWorld w = AdminWorld.get(server);
        JsonObject o = new JsonObject();
        JsonArray log = new JsonArray();
        List<AdminWorld.Change> all = w.log();
        for (int i = all.size() - 1; i >= 0; i--) {
            AdminWorld.Change c = all.get(i);
            JsonObject j = new JsonObject();
            j.addProperty("i", i);
            j.addProperty("time", c.time());
            j.addProperty("who", c.who());
            j.addProperty("giant", c.giant());
            j.addProperty("text", AdminServer.changeText(c));
            j.addProperty("undo", AdminUndo.undoable(c));
            log.add(j);
        }
        o.add("log", log);
        JsonArray snaps = new JsonArray();
        List<AdminWorld.Snapshot> ss = w.snapshots();
        for (int i = ss.size() - 1; i >= 0; i--) {
            AdminWorld.Snapshot s = ss.get(i);
            JsonObject j = new JsonObject();
            j.addProperty("i", i);
            j.addProperty("time", s.time());
            j.addProperty("who", s.who());
            j.addProperty("why", s.why());
            j.addProperty("count", s.count());
            snaps.add(j);
        }
        o.add("snapshots", snaps);
        JsonArray locks = new JsonArray();
        for (var e : w.locks().entrySet()) {
            String[] gi = e.getKey().split("/", 2);
            JsonObject j = new JsonObject();
            j.addProperty("giant", gi[0]);
            j.addProperty("gname", AdminServer.giantWord(gi[0]));
            j.addProperty("id", gi[1]);
            j.addProperty("name", AdminServer.settingName(server, gi[0], gi[1]));
            j.addProperty("by", e.getValue().who());
            j.addProperty("time", e.getValue().time());
            locks.add(j);
        }
        o.add("locks", locks);
        return new AdminBookStatePayload(AdminBookStatePayload.CHANGES, o.toString());
    }

    /** the Presets page for this player: the built-in ones (Classic first, the default) and the saved ones */
    public static AdminBookStatePayload presets(MinecraftServer server, ServerPlayer p) {
        AdminWorld w = AdminWorld.get(server);
        return new AdminBookStatePayload(AdminBookStatePayload.PRESETS, presetsJson(AdminView.of(server),
                p.getStringUUID(), net.jj.giants.tools.Tools.presetName(server)).toString());
    }

    /**
     * The Presets page (and the Create World picker): the built-in ones (Classic first, the default; then every other
     * built-in under "All presets", Part 3), each with its danger rating once applied and whether it is creation-only
     * (worked out now from the giants' own markers), and the saved ones.
     */
    public static JsonObject presetsJson(AdminView view, String myId, String current) {
        JsonObject o = new JsonObject();
        JsonArray built = new JsonArray();
        for (String id : AdminTransfer.presets()) {
            AdminTransfer.Bundle b = AdminTransfer.preset(id);
            if (b == null) continue;
            JsonObject j = new JsonObject();
            j.addProperty("id", id);
            j.addProperty("name", b.name());
            j.addProperty("about", b.about());
            j.addProperty("default", id.equals("classic"));
            j.addProperty("group", AdminTransfer.group(id));
            addRating(j, view, b);
            built.add(j);
        }
        o.add("builtin", built);
        JsonArray saved = new JsonArray();
        for (AdminPresets.Saved s : AdminPresets.list()) {
            JsonObject j = new JsonObject();
            j.addProperty("name", s.name());
            j.addProperty("by", s.by());
            j.addProperty("mine", s.byId().equals(myId));
            j.addProperty("time", s.time());
            int n = 0;
            for (Map<String, JsonElement> m : s.bundle().values().values()) n += m.size();
            j.addProperty("count", n);
            addRating(j, view, s.bundle());
            saved.add(j);
        }
        o.add("saved", saved);
        o.addProperty("current", current);
        return o;
    }

    /**
     * a preset's danger rating as a world with nothing set would have it once applied (so the same preset always reads
     * the same), and whether it changes anything that only takes full effect in a new world
     */
    private static void addRating(JsonObject j, AdminView view, AdminTransfer.Bundle b) {
        try {
            AdminView blank = AdminDraft.blankLike(view);
            AdminTransfer.Plan pl = AdminTransfer.plan(blank, b);
            AdminInsight.Danger d = AdminTransfer.dangerBeforeAfter(blank, pl)[1];
            j.addProperty("level", d.level());
            j.addProperty("label", d.label());
            j.addProperty("creation", !pl.creationOnly().isEmpty());
            j.addProperty("changes", pl.changes().size());
        } catch (RuntimeException e) {
            GiantsGuideMod.LOG.debug("no rating for preset {}: {}", b.name(), e.toString());
        }
    }


    /** (for the tests) a value as the book sends it */
    public static String setJson(String giant, String id, @Nullable JsonElement value, String text, boolean allWorlds) {
        JsonObject o = new JsonObject();
        o.addProperty("do", "set");
        o.addProperty("giant", giant);
        o.addProperty("id", id);
        o.addProperty("value", value != null ? AdminSetting.text(value) : text);
        o.addProperty("scope", allWorlds ? "allworlds" : "world");
        return o.toString();
    }
}
