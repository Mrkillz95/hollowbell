package net.jj.giants.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 6, ADMIN_BRIDGE.md §22.2 and §22.10) The respawn timers page: when each giant's own one (the one that comes
 * on his own) comes back, from his bridge's "respawn"; an admin brings him back now or sets the wait ("respawnact").
 * He still comes the way he always does: his own spawner, his own spot, never past Most at once.
 */
public final class Respawns {
    private Respawns() {}

    public static final int VERSION = 1;
    public static final String LIST = "respawn", ACT = "respawnact";

    /** one giant's line (null: his mod has no respawn timers to show) */
    public static @Nullable JsonObject state(MinecraftServer server, Giant g) {
        List<String> l = AdminServer.call(server, g, LIST, String.valueOf(VERSION));
        if (l == null || l.isEmpty()) return null;
        JsonObject o = AdminJson.object(l.get(0));
        return o == null || !o.has("respawn") ? null : o;
    }

    /** every giant's, for the page */
    public static JsonArray json(MinecraftServer server) {
        JsonArray a = new JsonArray();
        for (Giant g : AdminServer.giants()) {
            JsonObject o = state(server, g);
            JsonObject j = o == null ? new JsonObject() : o.deepCopy();
            j.addProperty("key", g.key());
            j.addProperty("name", AdminServer.nameOf(g));
            j.addProperty("speaks", o != null);
            a.add(j);
        }
        return a;
    }

    /** "comes back in about 3 days" and the like */
    static String words(JsonObject o) {
        String state = AdminJson.str(o, "state", 16);
        return switch (state) {
            case "standing" -> "out there now";
            case "due" -> {
                long t = (long) AdminJson.num(o, "ticks", 0);
                yield t <= 100 ? "comes back within a few seconds" : "comes back in " + waitWords(t);
            }
            case "gone" -> "fallen for good (Comes back after he falls is off)";
            case "off" -> "doesn't come on his own (Comes on his own is off)";
            default -> "not counting yet (nobody has been in the world)";
        };
    }

    public static String waitWords(long ticks) {
        if (ticks >= 24000) {
            long whole = Math.round(ticks / 24000.0);
            return "about " + whole + (whole == 1 ? " day" : " days");
        }
        long m = Math.max(1, Math.round(ticks / 1200.0));
        return "about " + m + (m == 1 ? " minute" : " minutes");
    }

    /** "now", or a wait like 30m, 2h, 3d (game days), or a plain number of minutes */
    public static long ticks(String text) throws IllegalArgumentException {
        String t = text.trim().toLowerCase(Locale.ROOT);
        if (t.equals("now")) return 0;
        long mult = 1200;
        if (t.endsWith("d")) {
            mult = 24000;
            t = t.substring(0, t.length() - 1);
        } else if (t.endsWith("h")) {
            mult = 72000;
            t = t.substring(0, t.length() - 1);
        } else if (t.endsWith("m")) t = t.substring(0, t.length() - 1);
        double v = Double.parseDouble(t.trim());
        if (!Double.isFinite(v) || v < 0) throw new IllegalArgumentException();
        return Math.round(v * mult);
    }

    /** brings him back now, or sets the wait (ticks from now) */
    public static AdminServer.Result act(MinecraftServer server, String who, String which, long ticks) {
        Giant g = AdminServer.giant(which);
        if (g == null) return AdminServer.Result.fail("No giant called " + which + ".");
        String name = AdminServer.nameOf(g);
        JsonObject arg = new JsonObject();
        arg.addProperty("respawn", VERSION);
        arg.addProperty("do", ticks <= 0 ? "now" : "in");
        arg.addProperty("ticks", Math.max(0, ticks));
        List<String> r = AdminServer.call(server, g, ACT, arg.toString());
        if (r == null || r.isEmpty())
            return AdminServer.Result.fail(name + ": this version has no respawn timer to change.");
        String line = r.size() > 1 ? r.get(1) : "";
        if (!r.get(0).equals("ok")) return AdminServer.Result.fail(line.isEmpty() ? name + ": he can't." : line);
        String what = ticks <= 0 ? name + " brought back now" : name + "'s return set to " + waitWords(ticks);
        Areas.log(server, who, "respawn", what);
        return new AdminServer.Result(true, List.of(line), List.of(who + ": " + what + "."));
    }

    /** (Boss Rush hour) brings him back at once if he's due or gone; nothing said */
    static void bringBack(MinecraftServer server, Giant g) {
        JsonObject o = state(server, g);
        if (o == null) return;
        String s = AdminJson.str(o, "state", 16);
        if (!s.equals("due") && !s.equals("gone")) return;
        AdminServer.call(server, g, ACT, "{\"respawn\":1,\"do\":\"now\"}");
    }

    public static AdminServer.Result lines(MinecraftServer server) {
        List<String> out = new ArrayList<>(List.of("When each giant's own one comes back:"));
        for (Giant g : AdminServer.giants()) {
            JsonObject o = state(server, g);
            out.add("  " + AdminServer.nameOf(g) + ": " + (o == null ? "this version doesn't say" : words(o)) + ".");
        }
        return AdminServer.Result.said(out);
    }
}
