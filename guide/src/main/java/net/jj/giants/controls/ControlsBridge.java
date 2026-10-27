package net.jj.giants.controls;

import com.google.gson.JsonObject;
import java.util.List;
import net.jj.giants.Giant;
import net.jj.giants.perf.PerfBridges;
import org.jetbrains.annotations.Nullable;

/**
 * Gestures' on/off, through each giant's GiantsBridge (CONTROLS_BRIDGE.md), on the player's own game (server null):
 * <ul>
 *   <li>"controlget", arg the gesture's config name: {@code ["true"]} or {@code ["false"]};
 *   <li>"controlset", arg {@code {"controls":1,"config":"tripleCrouchSit","value":false}}: any non-null list once
 *       it has taken it (and saved it in his own client settings).
 * </ul>
 * A mod that doesn't know them answers null, and its gesture is shown but can't be changed from here.
 */
public final class ControlsBridge {
    private ControlsBridge() {}

    public static final String GET = "controlget", SET = "controlset";

    /** whether the gesture is on now; null if his mod doesn't say */
    public static @Nullable Boolean get(Giant g, ControlsJson.Gesture x) {
        List<String> r = PerfBridges.call(null, g, GET, x.config());
        if (r == null || r.isEmpty()) return null;
        String s = r.get(0).strip();
        if (s.equalsIgnoreCase("true")) return true;
        if (s.equalsIgnoreCase("false")) return false;
        return null;
    }

    /** turns it on or off; false if his mod didn't take it */
    public static boolean set(Giant g, ControlsJson.Gesture x, boolean on) {
        return PerfBridges.call(null, g, SET, setArg(x, on)) != null;
    }

    public static String setArg(ControlsJson.Gesture x, boolean on) {
        JsonObject o = new JsonObject();
        o.addProperty("controls", ControlsJson.VERSION);
        o.addProperty("config", x.config());
        o.addProperty("value", on);
        return o.toString();
    }
}
