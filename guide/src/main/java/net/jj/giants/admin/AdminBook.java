package net.jj.giants.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
 * The admin book's server half (ADMIN_BRIDGE.md, "Book payloads"). An operator's book screen asks for its pages and
 * sends changes; every request is checked here (an operator, permission level 2, every time: the client is never
 * trusted), and a change does exactly what /giants admin set or reset does: kept for the world (or the server
 * default), logged, told to the other operators, sent to the giants. Whoever has the book open gets fresh pages
 * whenever anything changes, by the book, a command or a giant's own switch.
 */
public final class AdminBook {
    private AdminBook() {}

    public static final String DENIED = "This book is for admins.";
    /** most changes one player may ask for in a second (a slider sends one when it is let go) */
    static final int MOST_PER_SECOND = 20;

    /** who has the book open */
    private static final Set<UUID> watching = new LinkedHashSet<>();
    /** what changed since the pages were last sent ("*": every page) */
    private static final Set<String> dirty = new LinkedHashSet<>();
    private static final Map<UUID, int[]> asked = new HashMap<>();

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
        ServerTickEvents.END_SERVER_TICK.register(AdminBook::flush);
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> {
            watching.remove(h.getPlayer().getUUID());
            asked.remove(h.getPlayer().getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> forget());
    }

    /** nobody watching, nothing waiting (a server stopping, and the tests) */
    public static void forget() {
        watching.clear();
        dirty.clear();
        asked.clear();
    }

    /** the rule: an operator (permission level 2), the same as /giants admin */
    public static boolean allowed(ServerPlayer p) {
        return p.hasPermissions(AdminCommand.OPS);
    }

    public static boolean watching(ServerPlayer p) {
        return watching.contains(p.getUUID());
    }

    /** the book was used: an operator's game opens it; anyone else is told it is for admins, and nothing happens */
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

    /** the screen asks for its pages (or says it closed) */
    public static void ask(MinecraftServer server, ServerPlayer p, String what) {
        if (AdminBookAskPayload.CLOSE.equals(what)) {
            watching.remove(p.getUUID());
            return;
        }
        if (!allowed(p)) {
            watching.remove(p.getUUID());
            sender.accept(p, denied());
            GiantsGuideMod.LOG.info("Giants admin book: {} asked for its pages, but isn't an operator",
                    p.getGameProfile().getName());
            return;
        }
        if (!tooMany(server, p)) {
            watching.add(p.getUUID());
            sendAll(server, p);
        }
    }

    /** the screen asks for a change: checked, done the command's way, answered, told to the other operators */
    public static AdminServer.Result change(MinecraftServer server, ServerPlayer p, String json) {
        AdminServer.Result r;
        if (!allowed(p)) {
            GiantsGuideMod.LOG.info("Giants admin book: {} asked to change a setting, but isn't an operator",
                    p.getGameProfile().getName());
            watching.remove(p.getUUID());
            sender.accept(p, denied());
            return AdminServer.Result.fail(DENIED);
        }
        if (tooMany(server, p)) r = AdminServer.Result.fail("Too many changes at once. Wait a moment.");
        else r = request(server, p.getGameProfile().getName(), json);
        sender.accept(p, said(r));
        if (!r.announce().isEmpty()) AdminServer.announce(server, p, r.announce());
        return r;
    }

    private static boolean tooMany(MinecraftServer server, ServerPlayer p) {
        int second = server.getTickCount() / 20;
        int[] a = asked.computeIfAbsent(p.getUUID(), k -> new int[] {second, 0});
        if (a[0] != second) {
            a[0] = second;
            a[1] = 0;
        }
        return ++a[1] > MOST_PER_SECOND;
    }

    /**
     * What one change from the book does, by whoever asked (already checked): the same calls as /giants admin set and
     * reset, so the book and the commands can never differ.
     */
    public static AdminServer.Result request(MinecraftServer server, String who, String json) {
        JsonObject o = AdminJson.object(json);
        if (o == null) return AdminServer.Result.fail("The book sent something the server can't read.");
        String what = AdminJson.str(o, "do", 16), giant = AdminJson.str(o, "giant", 32),
                id = AdminJson.str(o, "id", 64), value = AdminJson.str(o, "value", 256);
        boolean allWorlds = AdminJson.str(o, "scope", 16).equals("allworlds");
        if (giant.isEmpty()) return AdminServer.Result.fail("The book didn't say which giant.");
        return switch (what) {
            case "set" -> {
                if (id.isEmpty()) yield AdminServer.Result.fail("The book didn't say which setting.");
                yield allWorlds
                        ? AdminServer.setDefault(server, who, giant, id, value)
                        : AdminServer.set(server, who, giant, id, value);
            }
            case "reset" -> {
                if (!allWorlds) yield AdminServer.reset(server, who, giant, id.isEmpty() ? null : id);
                // all worlds: the server default back to what his mod ships with
                if (id.isEmpty())
                    yield AdminServer.Result.fail("Only one setting at a time goes back for all worlds.");
                AdminServer.Found f = AdminServer.setting(server, AdminServer.target(server, giant), id);
                if (f.why() != null) yield AdminServer.Result.fail(f.why());
                yield AdminServer.setDefault(server, who, giant, id, AdminSetting.text(f.s().jar()));
            }
            default -> AdminServer.Result.fail("The book asked for something the server doesn't know.");
        };
    }

    // telling the screens

    /** something changed (AdminWorld logs every change): whoever has the book open gets fresh pages next tick */
    public static void changed(String giant) {
        dirty.add(giant);
    }

    /** sends the pages that changed to everyone with the book open (each tick, and from the tests) */
    public static void flush(MinecraftServer server) {
        if (dirty.isEmpty()) return;
        Set<String> what = new LinkedHashSet<>(dirty);
        dirty.clear();
        if (watching.isEmpty()) return;
        boolean every = what.contains("everything") || what.contains("*");
        // (made once, sent to each)
        List<AdminBookStatePayload> pages = new ArrayList<>();
        pages.add(home(server));
        if (every || what.contains(AdminCatalog.ALL)) pages.add(page(server, AdminCatalog.ALL));
        for (Giant g : AdminServer.giants())
            if (every || what.contains(g.key())) {
                AdminBookStatePayload pg = page(server, g.key());
                if (pg != null) pages.add(pg);
            }
        for (UUID u : List.copyOf(watching)) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p == null) {
                watching.remove(u);
                continue;
            }
            if (!allowed(p)) {
                // (no longer an operator: the book stops showing anything)
                watching.remove(u);
                sender.accept(p, denied());
                continue;
            }
            for (AdminBookStatePayload pg : pages) sender.accept(p, pg);
        }
    }

    /** every page, to one player */
    public static void sendAll(MinecraftServer server, ServerPlayer p) {
        sender.accept(p, home(server));
        sender.accept(p, page(server, AdminCatalog.ALL));
        for (Giant g : AdminServer.giants()) {
            AdminBookStatePayload pg = page(server, g.key());
            if (pg != null) sender.accept(p, pg);
        }
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

    /** the all-giants multipliers in use, as numbers */
    static Map<String, Double> allValues(AdminWorld w) {
        Map<String, Double> all = new HashMap<>();
        for (AdminSetting s : AdminServer.shared(w)) all.put(s.id(), s.value().getAsDouble());
        return all;
    }

    /**
     * The Home page: the preset, the danger rating, the clashes, the giants (each with how many settings the world
     * changed), and the last change.
     */
    public static AdminBookStatePayload home(MinecraftServer server) {
        AdminWorld w = AdminWorld.get(server);
        JsonObject o = new JsonObject();
        o.addProperty("op", true);
        o.addProperty("preset", AdminInsight.presetName(w.log(), !w.giantsWithValues().isEmpty()));
        List<AdminInsight.View> views = new ArrayList<>();
        JsonArray giants = new JsonArray();
        for (Giant g : AdminServer.giants()) {
            AdminJson.Report r = AdminServer.report(server, g);
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
        return new AdminBookStatePayload(AdminBookStatePayload.HOME, o.toString());
    }

    /** one page: the all-giants settings, or one giant's (null for a giant that has none) */
    public static @Nullable AdminBookStatePayload page(MinecraftServer server, String key) {
        List<AdminSetting> list;
        String name;
        AdminJson.Report r = null;
        if (key.equals(AdminCatalog.ALL)) {
            list = AdminServer.shared(AdminWorld.get(server));
            name = "All giants";
        } else {
            Giant g = AdminServer.giant(key);
            r = g == null ? null : AdminServer.report(server, g);
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
        return new AdminBookStatePayload(AdminBookStatePayload.PAGE, o.toString());
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
