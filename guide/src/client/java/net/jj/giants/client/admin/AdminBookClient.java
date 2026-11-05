package net.jj.giants.client.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jj.giants.admin.AdminBook;
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminDraft;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminPresets;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.admin.AdminTransfer;
import net.jj.giants.net.AdminBookAskPayload;
import net.jj.giants.net.AdminBookChangePayload;
import net.jj.giants.net.AdminBookOpenPayload;
import net.jj.giants.net.AdminBookStatePayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The admin book's player half: opens the screen when the server says so (only for an operator), keeps the pages the
 * server last sent, and sends the screen's changes. The server checks every request; nothing here decides anything.
 */
public final class AdminBookClient {
    private AdminBookClient() {}

    /** one page of settings (the all-giants ones, or one giant's) */
    public record Page(String giant, String name, List<AdminSetting> settings, List<String> uses,
            Map<String, String> locks) {
        /** who locked this setting, or null */
        public @Nullable String lockedBy(String id) {
            return locks.get(id);
        }

        public @Nullable AdminSetting setting(String id) {
            for (AdminSetting s : settings) if (s.id().equals(id)) return s;
            return null;
        }
    }

    private static @Nullable JsonObject home;
    private static final Map<String, Page> pages = new LinkedHashMap<>();
    // Part 2B: who this player is to the book, and the Live control, Changes and Presets pages
    private static String role = "";
    private static String myName = "", myId = "";
    /** (JJ answers 2, #15) may this player watch giants from Live control; whom they watch now (null: nobody) */
    private static boolean mayWatch;
    private static @Nullable JsonObject watchingNow;
    private static @Nullable JsonObject live, changes, presets, preview;
    /** (Part 5) the Players page: rules, picks, the vote, the leaderboards */
    private static @Nullable JsonObject players;
    /** (Part 6) the Server tools page: pause, events, schedules, areas, map, stats, alerts, respawns, profile, pack */
    private static @Nullable JsonObject tools;
    /** (Part 8) the Systems page: what the new systems are doing now, and each giant they follow */
    private static @Nullable JsonObject systems;
    /** (Part 7) the Fun and World tools pages' own state */
    private static @Nullable JsonObject fun;
    /** (Part 10A) the Minigames page, and whether this player may run minigames (admins; moderators if allowed) */
    private static @Nullable JsonObject minigames;
    private static boolean mayMinigames;
    /** goes up with every Live control page (once a second while it is looked at) */
    private static int liveVersion, previewVersion;
    /** the server's last word on a change */
    private static List<String> said = List.of();
    private static boolean saidOk;
    private static long saidAt;
    /** goes up with every page the server sends (the screen notices) */
    private static int version;
    private static boolean openNextTick;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(AdminBookOpenPayload.TYPE, (p, ctx) -> openNextTick = true);
        ClientPlayNetworking.registerGlobalReceiver(AdminBookStatePayload.TYPE, (p, ctx) -> told(p));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (openNextTick) {
                openNextTick = false;
                open();
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> {
            if (local == null) forget();
        });

    }

    private static void forget() {
        home = null;
        pages.clear();
        said = List.of();
        role = "";
        live = changes = presets = preview = players = tools = systems = null;
        fun = minigames = adventure = null;
        mayMinigames = false;
        version++;
        liveVersion++;
    }

    /** "admin", "moderator", or "" before the server has said */
    public static String role() {
        return role;
    }

    /** (JJ answers 2, #15) a moderator (or admin) may watch giants from Live control */
    public static boolean mayWatch() {
        return mayWatch;
    }

    /** whom this player watches now, or null */
    public static @Nullable JsonObject watchingNow() {
        return watchingNow;
    }

    public static boolean admin() {
        return role.equals("admin");
    }

    static String myName() {
        return myName;
    }

    public static @Nullable JsonObject live() {
        return live;
    }

    public static @Nullable JsonObject changes() {
        return changes;
    }

    public static @Nullable JsonObject fun() {
        return fun;
    }

    /** (the picture-taking) the Fun pages as if the server had sent them */
    public static void funForTests(JsonObject o) {
        fun = o;
    }

    public static @Nullable JsonObject players() {
        return players;
    }

    /** (the picture-taking) the Players page as the server would send it */
    public static void playersForTests(JsonObject o) {
        players = o;
        version++;
    }

    public static @Nullable JsonObject presets() {
        return presets;
    }

    /** (Part 6) the Server tools page */
    public static @Nullable JsonObject tools() {
        return tools;
    }

    /** (the picture-taking) the Server tools page as the server would send it */
    public static void toolsForTests(JsonObject o) {
        tools = o;
        version++;
    }

    /** (Part 11) the Adventure page */
    private static @Nullable JsonObject adventure;

    public static @Nullable JsonObject adventure() {
        return adventure;
    }

    /** (the picture-taking) the Adventure page as the server would send it */
    public static void adventureForTests(JsonObject o) {
        adventure = o;
        version++;
    }

    /** (Part 10A) the Minigames page */
    public static @Nullable JsonObject minigames() {
        return minigames;
    }

    /** (Part 10A) may run minigames: admins, and moderators while the admins allow it */
    public static boolean mayMinigames() {
        return mayMinigames;
    }

    /** (the picture-taking) the Minigames page as the server would send it */
    public static void minigamesForTests(JsonObject o) {
        minigames = o;
        mayMinigames = true;
        version++;
    }

    /** (Part 10A) the Minigames page is being looked at (the server sends it every second while a game is on) */
    static void watchMinigames(boolean on) {
        if (local != null) return;
        if (Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(AdminBookAskPayload.TYPE))
            ClientPlayNetworking.send(new AdminBookAskPayload(on ? "minigames" : "minigames_off"));
    }

    /** (Part 8) the Systems page */
    public static @Nullable JsonObject systems() {
        return systems;
    }

    /** (the picture-taking) the Systems page as the server would send it */
    public static void systemsForTests(JsonObject o) {
        systems = o;
        version++;
    }

    /** (Part 8) the Systems page is being looked at (the server sends it every two seconds) or not */
    static void watchSystems(boolean on) {
        if (local != null) return;
        if (Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(AdminBookAskPayload.TYPE))
            ClientPlayNetworking.send(new AdminBookAskPayload(on ? "systems" : "systems_off"));
    }

    /** (Part 6) the Server tools page is being looked at (the server sends it every two seconds) or not */
    static void watchTools(boolean on) {
        if (local != null) return;
        if (Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(AdminBookAskPayload.TYPE))
            ClientPlayNetworking.send(new AdminBookAskPayload(on ? "tools" : "tools_off"));
    }

    static @Nullable JsonObject preview() {
        return preview;
    }

    static void clearPreview() {
        preview = null;
    }

    public static int liveVersion() {
        return liveVersion;
    }

    static int previewVersion() {
        return previewVersion;
    }

    /** the Live control page is being looked at (the server sends it once a second) or not */
    static void watchLive(boolean on) {
        if (Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(AdminBookAskPayload.TYPE))
            ClientPlayNetworking.send(new AdminBookAskPayload(on ? AdminBookAskPayload.LIVE
                    : AdminBookAskPayload.LIVE_OFF));
    }

    /** any other request (Part 2B): {"do":...} as ADMIN_BRIDGE.md §15 lists them */
    static boolean request(JsonObject o) {
        return send(o);
    }

    /** opens the book's screen and asks for every page */
    public static void open() {
        endLocal();
        Minecraft.getInstance().setScreen(new AdminBookScreen());
        ask();
    }

    public static void ask() {
        if (local != null) {
            refreshLocal();
            return;
        }
        if (ClientPlayNetworking.canSend(AdminBookAskPayload.TYPE))
            ClientPlayNetworking.send(new AdminBookAskPayload(AdminBookAskPayload.ALL));
    }

    /** the screen closed: the server stops sending fresh pages */
    static void closed() {
        if (local != null) return;
        if (Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(AdminBookAskPayload.TYPE))
            ClientPlayNetworking.send(new AdminBookAskPayload(AdminBookAskPayload.CLOSE));
    }

    // Part 3: the book over the Create World screen's draft (ADMIN_BRIDGE.md §18.6): no server, the pages are made
    // here from the draft and every request is answered here, with the same checks and words

    private static @Nullable AdminDraft local;

    /** the book is showing a new world's draft, not a world */
    public static boolean creating() {
        return local != null;
    }

    public static @Nullable AdminDraft draft() {
        return local;
    }

    /** opens the book over a draft; closing it goes back to the parent screen */
    public static void openLocal(AdminDraft d, net.minecraft.client.gui.screens.Screen parent) {
        startLocal(d);
        Minecraft.getInstance().setScreen(new AdminBookScreen(parent));
    }

    /** the draft's pages in place of a server's (no screen opened) */
    public static void startLocal(AdminDraft d) {
        forget();
        local = d;
        role = "admin";
        Minecraft mc = Minecraft.getInstance();
        myName = mc.getUser() != null ? mc.getUser().getName() : "";
        myId = "";
        refreshLocal();
    }

    /** back to the network (the book in a world) */
    public static void endLocal() {
        if (local == null) return;
        local = null;
        forget();
    }

    /** every page again from the draft */
    static void refreshLocal() {
        AdminDraft d = local;
        if (d == null) return;
        JsonObject h = AdminBook.homeJson(d, d.label());
        h.addProperty("creation", true);
        home = h;
        told(new AdminBookStatePayload(AdminBookStatePayload.ROLE, roleJson()));
        JsonObject all = AdminBook.pageJson(d, AdminCatalog.ALL);
        if (all != null) told(new AdminBookStatePayload(AdminBookStatePayload.PAGE, all.toString()));
        for (net.jj.giants.Giant g : AdminServer.giants()) {
            JsonObject pg = AdminBook.pageJson(d, g.key());
            if (pg != null) told(new AdminBookStatePayload(AdminBookStatePayload.PAGE, pg.toString()));
        }
        told(new AdminBookStatePayload(AdminBookStatePayload.PRESETS,
                AdminBook.presetsJson(d, "", d.label()).toString()));
        version++;
    }

    private static String roleJson() {
        JsonObject o = new JsonObject();
        o.addProperty("role", "admin");
        o.addProperty("name", myName);
        o.addProperty("id", myId);
        return o.toString();
    }

    /** a request answered from the draft: the same checks and words as the server's */
    private static void answerLocal(AdminDraft d, JsonObject o) {
        String what = AdminJsonBridge.str(o, "do");
        String giant = AdminJsonBridge.str(o, "giant"), id = AdminJsonBridge.str(o, "id");
        AdminServer.Result r = switch (what) {
            case "set" -> d.set(giant, id, AdminJsonBridge.str(o, "value"));
            case "reset" -> d.reset(giant, id.isEmpty() ? null : id);
            case "setall" -> net.jj.giants.admin.AdminEvery.set(d, id, AdminJsonBridge.str(o, "value"));
            case "resetall" -> net.jj.giants.admin.AdminEvery.reset(d, id);
            case "preset", "import" -> {
                AdminTransfer.Bundle b = what.equals("preset")
                        ? AdminTransfer.preset(AdminJsonBridge.str(o, "name"))
                        : AdminTransfer.decode(AdminJsonBridge.str(o, "text"));
                if (b == null)
                    yield AdminServer.Result.fail(what.equals("preset")
                            ? "No preset called " + AdminJsonBridge.str(o, "name") + "."
                            : "That isn't a settings string: it starts with " + AdminTransfer.PREFIX + ".");
                AdminTransfer.Plan pl = d.plan(b, what);
                preview = AdminBook.previewJson(d, pl, what);
                previewVersion++;
                yield null;
            }
            case "apply" -> d.apply();
            case "export" -> {
                String text = AdminTransfer.encode(d.bundle());
                try {
                    Minecraft.getInstance().keyboardHandler.setClipboard(text);
                } catch (RuntimeException e) {
                    // (no clipboard here)
                }
                lastExport = text;
                yield new AdminServer.Result(true, List.of("Copied the new world's settings (" + text.length()
                        + " characters)."), List.of());
            }
            case "savepreset" -> AdminPresets.save(d.world(), myName.isEmpty() ? "Player" : myName, myId,
                    AdminJsonBridge.str(o, "name"));
            case "deletepreset" -> AdminPresets.delete(myName, myId, AdminJsonBridge.str(o, "name"));
            default -> AdminServer.Result.fail("That can only be done in a world (there is none yet).");
        };
        refreshLocal();
        if (r != null) {
            said = r.lines();
            saidOk = r.ok();
            saidAt = Util.getMillis();
        }
    }


    /** asks the server to change a setting (it checks this player may, and answers) */
    static boolean set(String giant, String id, String value, boolean allWorlds) {
        JsonObject o = new JsonObject();
        o.addProperty("do", "set");
        o.addProperty("giant", giant);
        o.addProperty("id", id);
        o.addProperty("value", value);
        o.addProperty("scope", allWorlds ? "allworlds" : "world");
        return send(o);
    }

    /** (Part 2C) one shared setting to the same value for every giant that has it */
    static boolean setAll(String id, String value, boolean allWorlds) {
        JsonObject o = new JsonObject();
        o.addProperty("do", "setall");
        o.addProperty("id", id);
        o.addProperty("value", value);
        o.addProperty("scope", allWorlds ? "allworlds" : "world");
        return send(o);
    }

    static boolean resetAll(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("do", "resetall");
        o.addProperty("id", id);
        return send(o);
    }

    /** asks for a setting (or a whole giant's, id null; "everything" for every giant) back to the default */
    static boolean reset(String giant, @Nullable String id, boolean allWorlds) {
        JsonObject o = new JsonObject();
        o.addProperty("do", "reset");
        o.addProperty("giant", giant);
        if (id != null) o.addProperty("id", id);
        o.addProperty("scope", allWorlds ? "allworlds" : "world");
        return send(o);
    }

    private static boolean send(JsonObject o) {
        if (local != null) {
            answerLocal(local, o);
            return true;
        }
        if (!ClientPlayNetworking.canSend(AdminBookChangePayload.TYPE)) return false;
        ClientPlayNetworking.send(new AdminBookChangePayload(o.toString()));
        return true;
    }

    private static void told(AdminBookStatePayload p) {
        JsonObject o = AdminJsonBridge.object(p.json());
        if (o == null) return;
        switch (p.kind()) {
            case AdminBookStatePayload.HOME -> home = o;
            case AdminBookStatePayload.PAGE -> {
                String giant = AdminJsonBridge.str(o, "giant");
                boolean shared = giant.equals(AdminCatalog.ALL);
                List<AdminSetting> list = new ArrayList<>();
                if (o.get("settings") instanceof JsonArray a)
                    for (JsonElement e : a)
                        if (e.isJsonObject()) {
                            AdminSetting s = AdminJson.setting(giant, e.getAsJsonObject(), shared);
                            if (s != null) list.add(s);
                        }
                List<String> uses = new ArrayList<>();
                if (o.get("uses") instanceof JsonArray a) for (JsonElement e : a) uses.add(e.getAsString());
                Map<String, String> locks = new LinkedHashMap<>();
                if (o.get("locks") instanceof JsonObject lk)
                    for (var e : lk.entrySet()) locks.put(e.getKey(), e.getValue().getAsString());
                pages.put(giant, new Page(giant, AdminJsonBridge.str(o, "name"), List.copyOf(list), List.copyOf(uses),
                        Map.copyOf(locks)));
            }
            case AdminBookStatePayload.SAID -> {
                List<String> lines = new ArrayList<>();
                if (o.get("lines") instanceof JsonArray a) for (JsonElement e : a) lines.add(e.getAsString());
                said = lines;
                saidOk = o.has("ok") && o.get("ok").getAsBoolean();
                saidAt = Util.getMillis();
            }
            case AdminBookStatePayload.ROLE -> {
                role = AdminJsonBridge.str(o, "role");
                myName = AdminJsonBridge.str(o, "name");
                mayWatch = o.has("watch") && o.get("watch").getAsBoolean();
                mayMinigames = o.has("minigames") && o.get("minigames").getAsBoolean();
                watchingNow = o.get("watching") instanceof JsonObject wj ? wj : null;
                myId = AdminJsonBridge.str(o, "id");
            }
            case AdminBookStatePayload.LIVE -> {
                live = o;
                liveVersion++;
                return;
            }
            case AdminBookStatePayload.CHANGES -> changes = o;
            case "players" -> players = o;
            case "tools" -> tools = o;
            case "systems" -> systems = o;
            case "fun" -> fun = o;
            case "minigames" -> minigames = o;
            case "adventure" -> adventure = o;
            case AdminBookStatePayload.PRESETS -> presets = o;
            case AdminBookStatePayload.PREVIEW -> {
                preview = o;
                previewVersion++;
                return;
            }
            case AdminBookStatePayload.EXPORT -> {
                String text = AdminJsonBridge.str(o, "text");
                try {
                    Minecraft.getInstance().keyboardHandler.setClipboard(text);
                } catch (RuntimeException e) {
                    // (no clipboard here: the server log has it too)
                }
                lastExport = text;
                return;
            }
            case AdminBookStatePayload.DENIED -> {
                // (no longer an operator, or never was: the book shows nothing)
                forget();
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof AdminBookScreen) mc.setScreen(null);
                if (mc.player != null)
                    mc.player.displayClientMessage(Component.literal(AdminJsonBridge.str(o, "line")), true);
                return;
            }
            default -> {
                return;
            }
        }
        version++;
    }

    /** the last settings string the server sent to copy */
    static String lastExport = "";

    public static @Nullable JsonObject home() {
        return home;
    }

    public static @Nullable Page page(String giant) {
        return pages.get(giant);
    }

    public static List<Page> pages() {
        return List.copyOf(pages.values());
    }

    public static int version() {
        return version;
    }

    static List<String> said() {
        return said;
    }

    static boolean saidOk() {
        return saidOk;
    }

    static long saidAt() {
        return saidAt;
    }

    /** (the picture-taking) pages as if the server had sent them */
    public static void tell(AdminBookStatePayload p) {
        told(p);
    }

    /** reads the JSON the server sent; anything odd is left out */
    static final class AdminJsonBridge {
        private AdminJsonBridge() {}

        static @Nullable JsonObject object(String s) {
            try {
                JsonElement e = com.google.gson.JsonParser.parseString(s);
                return e.isJsonObject() ? e.getAsJsonObject() : null;
            } catch (RuntimeException e) {
                return null;
            }
        }

        static String str(JsonObject o, String k) {
            try {
                return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
            } catch (RuntimeException e) {
                return "";
            }
        }

        static int num(JsonObject o, String k, int or) {
            try {
                return o.has(k) ? o.get(k).getAsInt() : or;
            } catch (RuntimeException e) {
                return or;
            }
        }
    }
}
