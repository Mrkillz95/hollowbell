package net.jj.giants.admin;

import com.google.gson.JsonPrimitive;
import java.util.List;
import net.jj.giants.admin.AdminSetting.Type;
import org.jetbrains.annotations.Nullable;

/**
 * The guide's own admin settings: the all-giants multipliers (giant "all"). Each giant applies the ones it lists in
 * its "uses" on top of its own settings; 1 changes nothing.
 */
public final class AdminCatalog {
    private AdminCatalog() {}

    public static final String ALL = "all";

    private static AdminSetting mult(String id, String cat, double min, double max, String when, String name,
            String what) {
        JsonPrimitive one = new JsonPrimitive(1.0);
        return new AdminSetting(ALL, id, cat, Type.FLOAT, min, max, 0.05, List.of(), one, one, one, false, when, name,
                what, "admin.jj_giants." + id, "");
    }

    private static final List<AdminSetting> SHARED = List.of(
            mult("all.health", "stats", 0.1, 10, AdminSetting.LIVE, "Health",
                    "Every giant's health times this, on top of his own multiplier."),
            mult("all.damage", "stats", 0.1, 10, AdminSetting.LIVE, "Damage",
                    "Every hit a giant lands times this, on top of his own multiplier."),
            mult("all.speed", "stats", 0.25, 4, AdminSetting.LIVE, "Speed",
                    "How fast every giant walks, runs and swims, times this."),
            mult("all.size", "spawning", 0.1, 4, AdminSetting.NEW_SPAWNS, "Spawn size",
                    "The size each giant comes at (the world's own, eggs, a plain summon), times this. Each giant"
                            + " keeps to his own smallest and biggest."),
            mult("all.cooldowns", "moves", 0.25, 4, AdminSetting.LIVE, "Move waits",
                    "The wait after each move, and the book's wait for it, times this."),
            mult("all.healing", "healing", 0, 10, AdminSetting.LIVE, "Healing",
                    "How fast every giant heals on his own, times this. 0 = no healing on their own."),
            mult("all.windCost", "taming", 0, 4, AdminSetting.LIVE, "Wind cost",
                    "What every order from a giant's book costs his wind, times this."));

    /** every all-giants setting, at its default (values are filled in from the world by AdminServer) */
    public static List<AdminSetting> shared() {
        return SHARED;
    }

    public static @Nullable AdminSetting shared(String id) {
        String full = id.startsWith("all.") ? id : "all." + id;
        for (AdminSetting s : SHARED) if (s.id().equals(full)) return s;
        return null;
    }
}
