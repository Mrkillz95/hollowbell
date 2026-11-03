package net.jj.fakeadmin2;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;

/**
 * (Part 2C tests) a second giant sharing some of fakeadmin's settings with narrower ranges and fewer options, so the
 * All giants page's every-giant values are pulled into each one's own range; one setting names its own section.
 */
public final class GiantsBridge {
    private GiantsBridge() {}

    public static final int API = 1;
    public static JsonObject values = new JsonObject();

    public static void reset() {
        values = new JsonObject();
    }

    private static final String[][] OWN = {
        {"power", "{\"cat\":\"stats\",\"type\":\"int\",\"min\":0,\"max\":6,\"step\":1,\"default\":3,"
                + "\"when\":\"live\",\"name\":\"Power\",\"what\":\"How hard he hits.\"}"},
        {"angry", "{\"cat\":\"behaviour\",\"section\":\"misc\",\"type\":\"bool\",\"default\":false,\"when\":\"live\","
                + "\"name\":\"Angry\",\"what\":\"Starts angry.\"}"},
        {"speedy", "{\"cat\":\"stats\",\"type\":\"float\",\"min\":0.5,\"max\":1.5,\"step\":0.05,\"default\":1,"
                + "\"when\":\"live\",\"name\":\"Speed\",\"what\":\"How fast.\"}"},
        {"mood", "{\"cat\":\"behaviour\",\"type\":\"choice\",\"options\":[\"calm\",\"hunting\"],"
                + "\"default\":\"calm\",\"when\":\"newSpawns\",\"name\":\"Starting mood\",\"what\":\"His mood.\"}"},
        {"sizes", "{\"cat\":\"spawning\",\"type\":\"range\",\"min\":0.5,\"max\":2,\"step\":0.05,\"default\":[1,1],"
                + "\"when\":\"newSpawns\",\"name\":\"Spawn size\",\"what\":\"Sizes he comes at.\"}"},
        {"shell", "{\"cat\":\"load\",\"type\":\"bool\",\"default\":true,\"when\":\"live\",\"name\":\"Shell\","
                + "\"what\":\"Only he has one.\"}"}
    };

    public static List<String> giants(MinecraftServer s, String action, String arg) {
        List<String> out = new ArrayList<>();
        switch (action) {
            case "admin" -> {
                out.add("{\"admin\":1,\"giant\":\"fakeadmin2\",\"name\":\"Fakeadmin2\",\"uses\":[],\"all\":{}}");
                for (String[] o : OWN) {
                    JsonObject j = JsonParser.parseString(o[1]).getAsJsonObject();
                    j.addProperty("id", o[0]);
                    JsonElement v = values.get(o[0]);
                    j.add("value", v != null ? v : j.get("default"));
                    j.addProperty("set", v != null);
                    out.add(j.toString());
                }
            }
            case "adminset" -> {
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                if (o.get("admin").getAsInt() != 1) return null;
                values = o.getAsJsonObject("values");
                out.add("ok");
            }
            default -> {
                return null;
            }
        }
        return out;
    }
}
