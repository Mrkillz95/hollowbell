package net.jj.faketools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;

/**
 * (Part 6) a giant's bridge for the server tools' tests, as ADMIN_BRIDGE.md §13 and §22 say, simply: a few settings,
 * his standing ones as the test puts them (where, which dimension, fighting), what was done to them (liveact, freeze,
 * wake, goto, home), his grounds, and a respawn timer.
 */
public final class GiantsBridge {
    private GiantsBridge() {}

    public static final int API = 1;
    public static JsonObject all = new JsonObject(), values = new JsonObject(), rules = new JsonObject();
    /** id -> his live line's fields */
    public static final Map<String, JsonObject> rows = new LinkedHashMap<>();
    /** what was asked of him, in order: "freeze on", "liveact home <id>", "wake", "goto x z", "respawnact now" ... */
    public static final List<String> asked = new ArrayList<>();
    public static String respawnState = "due";
    public static long respawnTicks = 48000;

    public static void reset() {
        all = new JsonObject();
        values = new JsonObject();
        rules = new JsonObject();
        rows.clear();
        asked.clear();
        respawnState = "due";
        respawnTicks = 48000;
    }

    /** one of him standing (or out of the world) where the test says */
    public static JsonObject put(String id, String state, String dim, double x, double z) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("state", state);
        o.addProperty("dim", dim);
        o.addProperty("x", x);
        o.addProperty("z", z);
        o.addProperty("mood", "calm");
        rows.put(id, o);
        return o;
    }

    private static String setting(String id, String cat, String type, String extra, String def, String name) {
        String v = values.has(id) ? values.get(id).toString() : def;
        return "{\"id\":\"" + id + "\",\"cat\":\"" + cat + "\",\"type\":\"" + type + "\"," + extra + "\"default\":"
                + def + ",\"value\":" + v + ",\"set\":" + values.has(id) + ",\"when\":\"live\",\"name\":\"" + name
                + "\",\"what\":\"\"}";
    }

    public static List<String> giants(MinecraftServer s, String action, String arg) {
        List<String> out = new ArrayList<>();
        switch (action) {
            case "admin" -> {
                out.add("{\"admin\":1,\"giant\":\"faketools\",\"name\":\"Faketools\",\"uses\":[\"all.health\","
                        + "\"all.chatMeetings\",\"all.chatMoves\",\"all.chatBackDown\"],\"all\":{}}");
                out.add(setting("seekGiants", "behaviour", "bool", "", "false", "Seeks out other giants"));
                out.add(setting("fightGiants", "behaviour", "bool", "", "true", "Fights other giants"));
                out.add(setting("meetCooldown", "behaviour", "int", "\"min\":0,\"max\":1440,\"step\":1,", "20",
                        "Minutes between meetings"));
                out.add(setting("temper", "behaviour", "choice", "\"options\":[\"peaceful\",\"normal\",\"hunter\"],",
                        "\"normal\"", "Temper"));
                out.add(setting("lootMultiplier", "loot", "float", "\"min\":0,\"max\":10,\"step\":0.25,", "1",
                        "Loot"));
            }
            case "adminset" -> {
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                all = o.getAsJsonObject("all");
                values = o.getAsJsonObject("values");
                out.add("ok");
            }
            case "players" -> {
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                rules = o.getAsJsonObject("rules");
                out.add("ok");
            }
            case "live" -> {
                out.add("{\"live\":1,\"giant\":\"faketools\",\"name\":\"Faketools\",\"moods\":[\"calm\"],"
                        + "\"size\":[0.1,2],\"does\":[\"tp\",\"home\",\"remove\"]}");
                for (JsonObject o : rows.values()) out.add(o.toString());
            }
            case "liveact" -> {
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                String id = o.get("id").getAsString(), d = o.get("do").getAsString();
                asked.add("liveact " + d + " " + id);
                if (!rows.containsKey(id)) return List.of("no", "He isn't there any more.");
                if (d.equals("remove")) rows.remove(id);
                out.add("ok");
                out.add(d.equals("tp") ? "Took you to him." : "Faketools: done.");
            }
            case "freeze" -> {
                asked.add("freeze " + arg.trim());
                out.add("Faketools: froze " + rows.size() + ".");
            }
            case "wake", "goto" -> {
                asked.add((action + " " + arg).trim());
                out.add("Faketools: ok.");
            }
            case "home" -> {
                asked.add("home " + arg.trim());
                out.add("Faketools: Took you to the middle of the Fake Ground.");
            }
            case "grounds" -> out.add("{\"ground\":1,\"giant\":\"faketools\",\"x\":300,\"z\":-200,\"r\":400,\"gap\":200,"
                    + "\"copy\":0}");
            case "respawn" -> {
                if (Integer.parseInt(arg.trim()) > 1) return null;
                JsonObject o = new JsonObject();
                o.addProperty("respawn", 1);
                o.addProperty("giant", "faketools");
                o.addProperty("name", "Faketools");
                o.addProperty("comes", true);
                o.addProperty("again", true);
                o.addProperty("state", respawnState);
                if (respawnState.equals("due")) o.addProperty("ticks", respawnTicks);
                o.addProperty("days", 2);
                o.addProperty("ownedDays", 5);
                out.add(o.toString());
            }
            case "respawnact" -> {
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                String d = o.get("do").getAsString();
                long t = o.has("ticks") ? o.get("ticks").getAsLong() : 0;
                asked.add("respawnact " + d + (d.equals("in") ? " " + t : ""));
                if (respawnState.equals("standing")) return List.of("no", "Faketools: he's already out there.");
                respawnState = "due";
                respawnTicks = d.equals("now") ? 0 : t;
                out.add("ok");
                out.add("Faketools: he comes back " + (t == 0 ? "within a few seconds." : "later."));
            }
            default -> {
                return null;
            }
        }
        return out;
    }
}
