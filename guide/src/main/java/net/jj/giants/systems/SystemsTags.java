package net.jj.giants.systems;

import java.util.List;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.fun.FunWorld;
import net.jj.giants.fun.Marks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;

/**
 * (Part 8, ADMIN_BRIDGE.md §24.1 rule 4) A giant's own stat tags, shared by the fun tools and the systems: the tag
 * the giant reads ({@code jj_giants.<stat>=}) is the fun tag's value (his giant tag's, Part 7) times the systems' own
 * factor, kept apart as {@code jj_giants.sys.<stat>=}. Both 1: no tag at all, as before.
 */
public final class SystemsTags {
    private SystemsTags() {}

    public static final String SYS = "jj_giants.sys.";
    public static final List<String> STATS = List.of("health", "damage", "speed");

    /** the systems' own factor on him for this stat (1 when there's none) */
    public static double sys(LivingEntity e, String what) {
        String head = SYS + what + "=";
        for (String t : e.getTags())
            if (t.startsWith(head))
                try {
                    double v = Double.parseDouble(t.substring(head.length()));
                    return Double.isFinite(v) && v > 0 ? v : 1;
                } catch (NumberFormatException bad) {
                    return 1;
                }
        return 1;
    }

    /** the fun tools' value for this stat: the giant tag he wears (1 when none) */
    public static double fun(MinecraftServer server, LivingEntity e, String what) {
        String wears = Marks.wears(e);
        if (wears == null) return 1;
        FunWorld.Tag t = FunWorld.get(server).tag(wears);
        if (t == null) return 1;
        return switch (what) {
            case "health" -> t.health;
            case "damage" -> t.damage;
            default -> t.speed;
        };
    }

    /** the systems' factor for a stat, and the tag the giant reads written again (fun x systems x fight) */
    public static void set(MinecraftServer server, LivingEntity e, String what, double v) {
        for (String t : List.copyOf(e.getTags())) if (t.startsWith(SYS + what + "=")) e.removeTag(t);
        double r = Math.round(v * 1000) / 1000.0;
        if (Math.abs(r - 1) > 1e-9) e.addTag(SYS + what + "=" + AdminSetting.plain(r));
        write(e, what, fun(server, e, what) * sys(e, what) * fightOf(e, what));
    }

    /** (Part 10B) a staged fight's own factor (a chase: the loser slower, the winner faster), kept apart */
    public static final String FIGHT = "jj_giants.fightx.";

    public static double fightOf(LivingEntity e, String what) {
        String head = FIGHT + what + "=";
        for (String t : e.getTags())
            if (t.startsWith(head))
                try {
                    double v = Double.parseDouble(t.substring(head.length()));
                    return Double.isFinite(v) && v > 0 ? v : 1;
                } catch (NumberFormatException bad) {
                    return 1;
                }
        return 1;
    }

    /** (Part 10B) the fight's factor for a stat (1: none), and the tag the giant reads written again */
    public static void fight(MinecraftServer server, LivingEntity e, String what, double v) {
        for (String t : List.copyOf(e.getTags())) if (t.startsWith(FIGHT + what + "=")) e.removeTag(t);
        double r = Math.round(v * 1000) / 1000.0;
        if (Math.abs(r - 1) > 1e-9) e.addTag(FIGHT + what + "=" + AdminSetting.plain(r));
        write(e, what, fun(server, e, what) * sys(e, what) * fightOf(e, what));
    }

    /** the tag the giant reads: this value (none at 1) */
    public static void write(LivingEntity e, String what, double v) {
        String head = Marks.TAG + what + "=";
        for (String t : List.copyOf(e.getTags())) if (t.startsWith(head)) e.removeTag(t);
        double r = Math.round(v * 1000) / 1000.0;
        if (Math.abs(r - 1) > 1e-9) e.addTag(head + AdminSetting.plain(r));
    }

    /** every stat's tag written again from the two parts (after the fun tools took his tag off) */
    public static void rewrite(MinecraftServer server, LivingEntity e) {
        for (String w : STATS) {
            double s = sys(e, w) * fightOf(e, w);
            if (Math.abs(s - 1) > 1e-9) write(e, w, fun(server, e, w) * s);
        }
    }
}
