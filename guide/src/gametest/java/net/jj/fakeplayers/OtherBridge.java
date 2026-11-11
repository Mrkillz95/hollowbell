package net.jj.fakeplayers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * (Max giants per player, ADMIN_BRIDGE.md 20.18) a second stand-in giant of another kind: who holds his main book,
 * who has their own book of him, and the books he was asked to give (the starting kit). Nothing else.
 */
public final class OtherBridge {
    private OtherBridge() {}

    public static final int API = 1;
    public static String main, mainName;
    public static final Map<String, String> own = new LinkedHashMap<>();
    public static final List<String> gave = new ArrayList<>();
    public static int ownersCalls;

    public static void reset() {
        main = null;
        mainName = null;
        own.clear();
        gave.clear();
        ownersCalls = 0;
    }

    public static List<String> giants(net.minecraft.server.MinecraftServer s, String action, String arg) {
        switch (action) {
            case "owners" -> {
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                if (o.get("owners").getAsInt() != 1) return null;
                ownersCalls++;
                JsonObject a = new JsonObject();
                a.addProperty("owners", 1);
                if (main != null) {
                    a.addProperty("main", main);
                    a.addProperty("mainName", mainName);
                }
                JsonArray l = new JsonArray();
                for (var e : own.entrySet()) {
                    JsonObject j = new JsonObject();
                    j.addProperty("id", e.getKey());
                    j.addProperty("name", e.getValue());
                    l.add(j);
                }
                a.add("own", l);
                return List.of("ok", a.toString());
            }
            case "give" -> {
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                String p = o.get("player").getAsString(), what = o.get("what").getAsString();
                gave.add(what + " " + p);
                if (what.equals("book")) main = p;
                return List.of("ok");
            }
            default -> {
                return null;
            }
        }
    }
}
