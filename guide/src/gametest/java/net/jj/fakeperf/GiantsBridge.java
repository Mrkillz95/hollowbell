package net.jj.fakeperf;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;

/**
 * A giant's bridge that speaks the performance protocol, the way PERFORMANCE_BRIDGE.md says (with a few bad lines
 * in, which the guide must leave out). It remembers what it was last sent, for the tests.
 */
public final class GiantsBridge {
    private GiantsBridge() {}

    public static final int API = 1;
    /** the last "values" object sent for each side, or null */
    public static JsonObject client, server;
    public static int sets;
    /** its one gesture's client setting */
    public static boolean tripleCrouchSit = true;

    public static List<String> giants(MinecraftServer s, String action, String arg) {
        List<String> out = new ArrayList<>();
        switch (action) {
            case "perf" -> {
                out.add("{\"perf\":1,\"giant\":\"fakeperf\",\"uses\":[\"fx.particles\",\"fx.bigImpacts\","
                        + "\"detail.farSight\",\"detail.renderDistance\",\"feature.meetings\",\"bad id!\"]}");
                // its own: an id without his name in front gets it; Highest's level is always the default
                out.add("{\"id\":\"sparks\",\"section\":\"giant\",\"kind\":\"slider\",\"min\":0,\"max\":1,"
                        + "\"step\":0.25,\"default\":1,\"levels\":[0.5,1,0.75,0.5,0],\"cost\":\"med\","
                        + "\"side\":\"client\",\"name\":\"Soul sparks\",\"what\":\"Sparks off his antlers.\","
                        + "\"loses\":\"Fewer sparks.\"}");
                out.add("{\"id\":\"fakeperf.hush\",\"section\":\"features\",\"kind\":\"toggle\",\"default\":true,"
                        + "\"levels\":[true,true,true,true,false],\"cost\":\"high\",\"side\":\"server\",\"ops\":true,"
                        + "\"name\":\"The Hush\",\"what\":\"His shadow hunts by sound in the eclipse.\","
                        + "\"loses\":\"No Hush: the eclipse is only dark.\"}");
                out.add("{\"id\":\"fakeperf.mode\",\"kind\":\"choice\",\"options\":[\"Full\",\"Half\",\"Off\"],"
                        + "\"default\":0,\"levels\":[0,0,1,1,2],\"side\":\"client\",\"name\":\"Eclipse sky\"}");
                // bad ones: left out
                out.add("{\"id\":\"bad id!\",\"kind\":\"slider\"}");
                out.add("{\"id\":\"fakeperf.weird\",\"kind\":\"wobble\"}");
                out.add("not json at all");
                out.add("{\"id\":\"fakeperf.backwards\",\"kind\":\"slider\",\"min\":5,\"max\":1}");
                out.add("{\"id\":\"fakeperf.sparks\",\"kind\":\"toggle\"}");
            }
            case "perfset" -> {
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                if (o.get("perf").getAsInt() != 1) return null;
                JsonObject v = o.getAsJsonObject("values");
                if (o.get("side").getAsString().equals("server")) server = v;
                else client = v;
                sets++;
                out.add("ok");
            }
            // the Controls tab's gestures (CONTROLS_BRIDGE.md)
            case "controlget" -> {
                if (!arg.equals("tripleCrouchSit")) return null;
                out.add(String.valueOf(tripleCrouchSit));
            }
            case "controlset" -> {
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                if (o.get("controls").getAsInt() != 1 || !o.get("config").getAsString().equals("tripleCrouchSit"))
                    return null;
                tripleCrouchSit = o.get("value").getAsBoolean();
                out.add("ok");
            }
            default -> {
                return null;
            }
        }
        return out;
    }
}
