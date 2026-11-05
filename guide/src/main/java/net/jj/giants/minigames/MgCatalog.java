package net.jj.giants.minigames;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.List;
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.admin.AdminSetting.Type;
import net.jj.giants.admin.AdminWorld;
import net.minecraft.server.MinecraftServer;

/**
 * (Part 10A, ADMIN_BRIDGE.md §26.6) The minigames' settings: all-giants settings of category "server" (like the
 * server tools'), so the book, /giants admin set all, the change log, undo, locks, export/import and snapshots treat
 * them like any other, presets keep them, they are kept for each world only, and Home's preset never reads ", changed"
 * for them. Shown on the book's Minigames page.
 */
public final class MgCatalog {
    private MgCatalog() {}

    private static AdminSetting flag(String id, boolean on, String name, String what) {
        JsonPrimitive v = new JsonPrimitive(on);
        return new AdminSetting(AdminCatalog.ALL, id, AdminCatalog.SERVER, Type.BOOL, 0, 1, 1, List.of(), v, v, v,
                false, AdminSetting.LIVE, name, what, "admin.jj_giants." + id, "");
    }

    private static AdminSetting num(String id, int def, int min, int max, int step, String name, String what) {
        JsonPrimitive v = new JsonPrimitive(def);
        return new AdminSetting(AdminCatalog.ALL, id, AdminCatalog.SERVER, Type.INT, min, max, step, List.of(), v, v,
                v, false, AdminSetting.LIVE, name, what, "admin.jj_giants." + id, "");
    }

    private static AdminSetting real(String id, double def, double min, double max, double step, String name,
            String what) {
        JsonPrimitive v = new JsonPrimitive(def);
        return new AdminSetting(AdminCatalog.ALL, id, AdminCatalog.SERVER, Type.FLOAT, min, max, step, List.of(), v, v,
                v, false, AdminSetting.LIVE, name, what, "admin.jj_giants." + id, "");
    }

    private static AdminSetting choice(String id, List<String> options, String def, String name, String what) {
        JsonPrimitive v = new JsonPrimitive(def);
        return new AdminSetting(AdminCatalog.ALL, id, AdminCatalog.SERVER, Type.CHOICE, 0, options.size() - 1, 1,
                options, v, v, v, false, AdminSetting.LIVE, name, what, "admin.jj_giants." + id, "");
    }

    public static final List<AdminSetting> SETTINGS = List.of(
            flag("all.mgMods", true, "Moderators run minigames",
                    "Moderators may start and stop minigames (the book's Minigames page, /giants minigame). Off: only"
                            + " admins can."),
            num("all.mgLobby", 60, 10, 300, 5, "Lobby time",
                    "Seconds between a minigame opening and its start, while players join (in seconds)."),
            num("all.mgMin", 1, 1, 16, 1, "Fewest players",
                    "A minigame starts only with at least this many players (and each mode's own fewest)."),
            num("all.mgMax", 16, 2, 64, 1, "Most players",
                    "At most this many play in one minigame; the rest may watch."),
            num("all.mgRounds", 1, 1, 5, 1, "Rounds",
                    "Rounds in each minigame, each on a fresh map; the most round wins wins."),
            num("all.mgMinutes", 8, 2, 30, 1, "Round time",
                    "The longest a round lasts, in minutes; then the mode says who won."),
            choice("all.mgSize", List.of("small", "medium", "large"), "medium", "Map size",
                    "How big each round's map is: small, medium or large."),
            real("all.mgGiantSize", 0.15, 0.05, 0.5, 0.01, "Giant size",
                    "How big the giants are in minigames (1 = full size; 0.15 makes a Wreckback about 31 blocks tall)."),
            choice("all.mgPvp", List.of("mode", "off"), "mode", "Players hitting players",
                    "mode: as each mode says (Giant Royale and Possession allow it). off: players never hurt each"
                            + " other in a minigame."),
            flag("all.mgSpectate", true, "Watching",
                    "Players may watch a minigame (as spectators) instead of playing, and once it started."),
            flag("all.mgTell", true, "Tell everyone",
                    "Everyone online is told when a minigame opens (with Join and Watch to click) and who won. Quiet"
                            + " mode keeps it to the players in it."),
            flag("all.mgBoard", true, "Minigame board",
                    "Minigame wins on the leaderboards (the Guide's This world page and the signs at spawn)."));

    public static boolean ours(String id) {
        String full = id.startsWith("all.") ? id : "all." + id;
        for (AdminSetting s : SETTINGS) if (s.id().equals(full)) return true;
        return false;
    }

    // what the world has now

    static JsonElement value(MinecraftServer server, String id) {
        String full = id.startsWith("all.") ? id : "all." + id;
        JsonElement v = AdminWorld.get(server).values(AdminCatalog.ALL).get(full);
        if (v != null) return v;
        for (AdminSetting s : SETTINGS) if (s.id().equals(full)) return s.def();
        return new JsonPrimitive(0);
    }

    public static boolean on(MinecraftServer server, String id) {
        try {
            return value(server, id).getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static int num(MinecraftServer server, String id) {
        try {
            return value(server, id).getAsInt();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    public static double real(MinecraftServer server, String id) {
        try {
            return value(server, id).getAsDouble();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    public static String option(MinecraftServer server, String id) {
        JsonElement v = value(server, id);
        return v.isJsonPrimitive() ? v.getAsString() : "";
    }
}
