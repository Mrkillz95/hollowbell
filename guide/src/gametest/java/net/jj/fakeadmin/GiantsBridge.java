package net.jj.fakeadmin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;

/**
 * A giant's bridge that speaks the admin protocol the way ADMIN_BRIDGE.md says (with a few bad lines in, which the
 * guide must leave out). Its "config file" has power 5; what the guide last sent is kept for the tests, and its list
 * reports it back as the value in use.
 */
public final class GiantsBridge {
    private GiantsBridge() {}

    public static final int API = 1;
    /** what the guide last sent: the world's values, and the all-giants ones */
    public static JsonObject values = new JsonObject(), all = new JsonObject();
    /** his "config file" as changed by /giants admin set ... allworlds (admindefault) */
    public static JsonObject defaults = new JsonObject();
    /** false: he speaks the admin protocol but not admindefault (a giant from Part 1b) */
    public static boolean knowsDefault = true;

    public static int sets;

    public static void reset() {
        values = new JsonObject();
        all = new JsonObject();
        defaults = new JsonObject();
        knowsDefault = true;
        sets = 0;
        standing.clear();
        acts.clear();
    }

    private static final String[][] OWN = {
        // id, json without value/set
        {"power", "{\"cat\":\"stats\",\"type\":\"int\",\"min\":0,\"max\":10,\"step\":1,\"default\":5,\"jar\":4,"
                + "\"when\":\"live\",\"name\":\"Power\",\"what\":\"How hard he hits.\"}"},
        {"angry", "{\"cat\":\"behaviour\",\"type\":\"bool\",\"default\":false,\"when\":\"live\",\"name\":\"Angry\","
                + "\"what\":\"Starts angry.\"}"},
        {"speedy", "{\"cat\":\"stats\",\"type\":\"float\",\"min\":0.5,\"max\":2,\"step\":0.05,\"default\":1,"
                + "\"when\":\"live\",\"name\":\"Speed\",\"what\":\"How fast.\"}"},
        {"mood", "{\"cat\":\"behaviour\",\"type\":\"choice\",\"options\":[\"calm\",\"hunting\",\"guardian\"],"
                + "\"default\":\"calm\",\"when\":\"newSpawns\",\"name\":\"Starting mood\",\"what\":\"His mood.\"}"},
        {"sizes", "{\"cat\":\"spawning\",\"type\":\"range\",\"min\":0.1,\"max\":4,\"step\":0.05,\"default\":[1,1],"
                + "\"when\":\"newSpawns\",\"name\":\"Spawn size\",\"what\":\"Sizes he comes at.\"}"},
        {"landSize", "{\"cat\":\"worldgen\",\"type\":\"int\",\"min\":100,\"max\":1000,\"step\":50,\"default\":500,"
                + "\"when\":\"worldCreation\",\"name\":\"Ground size\",\"what\":\"How big his ground is.\"}"},
        {"move.bite.on", "{\"cat\":\"moves\",\"type\":\"bool\",\"default\":true,\"when\":\"live\","
                + "\"name\":\"Bite: on\",\"group\":\"bite\"}"}
    };

    public static List<String> giants(MinecraftServer s, String action, String arg) {
        List<String> out = new ArrayList<>();
        switch (action) {
            case "admin" -> {
                JsonObject head = new JsonObject();
                head.addProperty("admin", 1);
                head.addProperty("giant", "fakeadmin");
                head.addProperty("name", "Fakeadmin");
                JsonArray uses = new JsonArray();
                uses.add("all.health");
                uses.add("all.damage");
                head.add("uses", uses);
                head.add("all", all);
                out.add(head.toString());
                for (String[] o : OWN) {
                    JsonObject j = JsonParser.parseString(o[1]).getAsJsonObject();
                    j.addProperty("id", o[0]);
                    if (defaults.has(o[0])) j.add("default", defaults.get(o[0]));
                    JsonElement v = values.get(o[0]);
                    j.add("value", v != null ? v : j.get("default"));
                    j.addProperty("set", v != null);
                    out.add(j.toString());
                }
                // bad ones: left out
                out.add("{\"id\":\"wobbly\",\"type\":\"wobble\",\"default\":1}");
                out.add("{\"id\":\"bad id!\",\"type\":\"int\",\"default\":1}");
                out.add("{\"id\":\"onechoice\",\"type\":\"choice\",\"options\":[\"only\"],\"default\":\"only\"}");
                out.add("{\"id\":\"all.health\",\"type\":\"float\",\"default\":1}");
                out.add("{\"id\":\"nodefault\",\"type\":\"int\"}");
                out.add("not json at all");
            }
            case "adminset" -> {
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                if (o.get("admin").getAsInt() != 1) return null;
                values = o.getAsJsonObject("values");
                all = o.getAsJsonObject("all");
                sets++;
                out.add("ok");
            }
            case "admindefault" -> {
                if (!knowsDefault) return null;
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                String id = o.get("id").getAsString();
                if (id.startsWith("move.")) {
                    out.add("no");
                    out.add("{\"rejected\":\"" + id + "\",\"why\":\"world only\"}");
                    return out;
                }
                defaults.add(id, o.get("value"));
                out.add("ok");
            }
            case "live" -> {
                out.add("{\"live\":1,\"giant\":\"fakeadmin\",\"name\":\"Fakeadmin\",\"moods\":[\"calm\",\"hunting\"],"
                        + "\"size\":[0.1,2],\"does\":[\"tp\",\"heal\",\"size\",\"sleep\",\"wake\",\"calm\",\"forgive\","
                        + "\"owner\",\"home\",\"remove\",\"kill\"]}");
                for (JsonObject g : standing.values()) out.add(g.toString());
            }
            case "liveact" -> {
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                if (o.get("live").getAsInt() != 1) return null;
                String id = o.get("id").getAsString(), what = o.get("do").getAsString();
                acts.add(what + " " + id + " " + o.get("value").getAsString() + " by " + o.get("by").getAsString());
                JsonObject g = standing.get(id);
                if (g == null) return List.of("no", "He isn't there any more.");
                switch (what) {
                    case "heal" -> g.addProperty("health", g.get("max").getAsDouble());
                    case "size" -> g.addProperty("size", Double.parseDouble(o.get("value").getAsString()));
                    case "remove", "kill" -> standing.remove(id);
                    case "calm" -> g.addProperty("mood", "calm");
                    default -> {}
                }
                return List.of("ok", "Fakeadmin: " + what + " done.");
            }
            default -> {
                return null;
            }
        }
        return out;
    }

    /** his standing ones for the live control (id to its line), and what liveact was asked */
    public static final java.util.Map<String, JsonObject> standing = new java.util.LinkedHashMap<>();
    public static final List<String> acts = new ArrayList<>();

    /** one standing for the live control */
    public static String stand(String mood, double health) {
        String id = java.util.UUID.randomUUID().toString();
        JsonObject g = new JsonObject();
        g.addProperty("id", id);
        g.addProperty("state", "here");
        g.addProperty("dim", "minecraft:overworld");
        g.addProperty("x", 10.5);
        g.addProperty("y", 64);
        g.addProperty("z", -3);
        g.addProperty("health", health);
        g.addProperty("max", 100);
        g.addProperty("size", 1);
        g.addProperty("mood", mood);
        g.addProperty("asleep", false);
        standing.put(id, g);
        return id;
    }
}
