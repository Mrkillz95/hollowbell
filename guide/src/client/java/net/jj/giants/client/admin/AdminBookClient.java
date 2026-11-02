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
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminSetting;
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
    public record Page(String giant, String name, List<AdminSetting> settings, List<String> uses) {
        public @Nullable AdminSetting setting(String id) {
            for (AdminSetting s : settings) if (s.id().equals(id)) return s;
            return null;
        }
    }

    private static @Nullable JsonObject home;
    private static final Map<String, Page> pages = new LinkedHashMap<>();
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
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> forget());
    }

    private static void forget() {
        home = null;
        pages.clear();
        said = List.of();
        version++;
    }

    /** opens the book's screen and asks for every page */
    public static void open() {
        Minecraft.getInstance().setScreen(new AdminBookScreen());
        ask();
    }

    public static void ask() {
        if (ClientPlayNetworking.canSend(AdminBookAskPayload.TYPE))
            ClientPlayNetworking.send(new AdminBookAskPayload(AdminBookAskPayload.ALL));
    }

    /** the screen closed: the server stops sending fresh pages */
    static void closed() {
        if (Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(AdminBookAskPayload.TYPE))
            ClientPlayNetworking.send(new AdminBookAskPayload(AdminBookAskPayload.CLOSE));
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
                pages.put(giant, new Page(giant, AdminJsonBridge.str(o, "name"), List.copyOf(list), List.copyOf(uses)));
            }
            case AdminBookStatePayload.SAID -> {
                List<String> lines = new ArrayList<>();
                if (o.get("lines") instanceof JsonArray a) for (JsonElement e : a) lines.add(e.getAsString());
                said = lines;
                saidOk = o.has("ok") && o.get("ok").getAsBoolean();
                saidAt = Util.getMillis();
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
