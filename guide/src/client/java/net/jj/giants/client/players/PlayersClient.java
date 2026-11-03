package net.jj.giants.client.players;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.net.PlayersAskPayload;
import net.jj.giants.net.PlayersStatePayload;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 5) the player's own game: the This world pages the server sends (rules, vote, leaderboards), My Giant's pick
 * screen, and the server's fight music choice handed to every giant on this game (ADMIN_BRIDGE.md 20.6).
 */
public final class PlayersClient {
    private PlayersClient() {}

    private static @Nullable JsonObject world;
    private static int version;
    private static List<String> said = List.of();
    private static boolean saidOk = true;
    private static long saidAt;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(PlayersStatePayload.TYPE,
                (p, ctx) -> ctx.client().execute(() -> received(p.kind(), p.json())));
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> forget());
    }

    public static void received(String kind, String json) {
        JsonObject o;
        try {
            o = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        switch (kind) {
            case PlayersStatePayload.WORLD -> {
                world = o;
                version++;
            }
            case PlayersStatePayload.PICK -> Minecraft.getInstance().setScreen(
                    new PickScreen(Minecraft.getInstance().screen, o));
            case PlayersStatePayload.SYNC -> giants(o.toString());
            case PlayersStatePayload.SAID -> {
                List<String> l = new ArrayList<>();
                if (o.get("lines") instanceof JsonArray a) for (JsonElement e : a) l.add(e.getAsString());
                said = l;
                saidOk = !o.has("ok") || o.get("ok").getAsBoolean();
                saidAt = System.currentTimeMillis();
                version++;
            }
            default -> {}
        }
    }

    /** every giant on this game hears the server's choice (or {} when leaving) */
    static void giants(String json) {
        for (Giant g : Giants.installed()) AdminServer.call(null, g, "playersclient", json);
    }

    public static void forget() {
        world = null;
        said = List.of();
        version++;
        giants("{}");
    }

    public static @Nullable JsonObject world() {
        return world;
    }

    public static int version() {
        return version;
    }

    public static List<String> said() {
        return System.currentTimeMillis() - saidAt < 8000 ? said : List.of();
    }

    public static boolean saidOk() {
        return saidOk;
    }

    /** asks the server; false when there's no server that speaks it */
    public static boolean ask(String what, Object... kv) {
        JsonObject o = new JsonObject();
        o.addProperty("do", what);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object v = kv[i + 1];
            if (v instanceof Number n) o.addProperty((String) kv[i], n);
            else o.addProperty((String) kv[i], String.valueOf(v));
        }
        if (!ClientPlayNetworking.canSend(PlayersAskPayload.TYPE)) return false;
        ClientPlayNetworking.send(new PlayersAskPayload(o.toString()));
        return true;
    }

    /** (the picture-taking) the pages as the server would send them */
    public static void setForTests(JsonObject w) {
        world = w;
        version++;
    }
}
