package net.jj.hollowbell;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import net.jj.hollowbell.world.WorldOne;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 6, ADMIN_BRIDGE.md §22) The admin book's respawn timers: when the world's own one comes back, and an admin
 * changing that or bringing him back now. "respawn" says where things stand; "respawnact" sets the time. Nothing else
 * changes: he still comes the way he always does (his own spawner, his own spot, never past Most at once), only sooner
 * or later.
 */
public final class GiantsRespawn {
    private GiantsRespawn() {}

    public static final int VERSION = 1;
    /** the longest wait an admin may set: a thousand days */
    public static final long MOST = 1000L * 24000L;
    private static final String WHO = GiantsAdmin.NAME;

    /** "respawn": one line of JSON (null for a newer protocol, unreadable, or no server) */
    public static @Nullable List<String> describe(@Nullable MinecraftServer server, String arg) {
        if (server == null || !readable(arg)) return null;
        ServerLevel over = server.overworld();
        WorldOne w = WorldOne.get(server);
        var V = BellConfig.V;
        JsonObject o = new JsonObject();
        o.addProperty("respawn", VERSION);
        o.addProperty("giant", GiantsAdmin.GIANT);
        o.addProperty("name", WHO);
        o.addProperty("comes", V.oneInTheWorld);
        o.addProperty("again", V.respawn);
        String state = state(w);
        o.addProperty("state", state);
        if (state.equals("due")) o.addProperty("ticks", Math.max(0, w.respawnDueAt() - over.getGameTime()));
        int[] at = w.respawnWhere();
        if (at != null) {
            o.addProperty("x", at[0]);
            o.addProperty("z", at[1]);
        }
        o.addProperty("days", V.worldRespawnDays);
        o.addProperty("ownedDays", V.ownedRespawnDays);
        return List.of(o.toString());
    }

    /** standing, due, gone (fallen with Comes back after he falls off), off (doesn't come on his own), waiting */
    private static String state(WorldOne w) {
        if (!BellConfig.V.oneInTheWorld) return "off";
        return w.respawnState();
    }

    /**
     * "respawnact": {"respawn":1,"do":"now"} brings him back as soon as his spawner next looks (a few seconds, once
     * someone is in the world); {"respawn":1,"do":"in","ticks":N} sets the wait to N ticks from now. Either lifts a
     * fall that had ended his kind here. ["ok", line] or ["no", why]; null for nonsense.
     */
    public static @Nullable List<String> act(@Nullable MinecraftServer server, String arg) {
        if (server == null) return null;
        String what;
        long ticks;
        try {
            JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
            if (o.get("respawn").getAsInt() > VERSION) return null;
            what = o.get("do").getAsString();
            ticks = o.has("ticks") ? o.get("ticks").getAsLong() : 0;
        } catch (RuntimeException e) {
            return null;
        }
        if (!what.equals("now") && !what.equals("in")) return null;
        WorldOne w = WorldOne.get(server);
        String state = state(w);
        if (state.equals("standing")) return List.of("no", WHO + ": he's already out there.");
        if (state.equals("off"))
            return List.of("no", WHO + ": he doesn't come on his own in this world (Comes on his own is off).");
        if (what.equals("in") && (ticks < 0 || ticks > MOST))
            return List.of("no", WHO + ": the wait has to be from 0 to 1000 days.");
        long now = server.overworld().getGameTime();
        long t = what.equals("now") ? 0 : ticks;
        w.respawnAt(now + t);
        return List.of("ok", WHO + ": " + (t <= 0 ? "he comes back within a few seconds (once someone is in the world)."
                : "he comes back in " + waitText(t) + "."));
    }

    /** a wait the way people say it: "about 3 days", "about 20 minutes" */
    public static String waitText(long ticks) {
        if (ticks >= 24000) {
            long d = Math.round(ticks / 24000.0);
            return "about " + d + (d == 1 ? " day" : " days");
        }
        long m = Math.max(1, Math.round(ticks / 1200.0));
        return "about " + m + (m == 1 ? " minute" : " minutes");
    }

    private static boolean readable(String arg) {
        try {
            return arg != null && Integer.parseInt(arg.trim()) <= VERSION;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
