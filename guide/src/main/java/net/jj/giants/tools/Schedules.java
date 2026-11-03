package net.jj.giants.tools;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminTransfer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 6, ADMIN_BRIDGE.md §22.7) Preset schedules: a preset that goes on by itself on certain days of the week (the
 * server's clock; "weekends" is Saturday and Sunday), every full-moon night (in the game), or on one date (every
 * year, or once), and back off when that time is over. Only one runs at a time (the first in the list that is due).
 * Going on is a preset like any other (a snapshot first, locked settings left alone); going off puts back what it set
 * that nobody changed since.
 */
public final class Schedules {
    private Schedules() {}

    public static final String DAYS = "days", FULL_MOON = "fullmoon", DATE = "date";
    public static final int MOST = 16;
    /** (the tests) the day it is, in place of the server's clock */
    public static @Nullable LocalDate today;

    static LocalDate today() {
        return today != null ? today : LocalDate.now();
    }

    /** a full-moon night in the overworld (moon phase 0, dusk to dawn) */
    static boolean fullMoonNight(MinecraftServer server) {
        ServerLevel over = server.overworld();
        long t = over.getDayTime() % 24000L;
        return over.getMoonPhase() == 0 && t >= 13000 && t < 23000;
    }

    /** is it this one's time now */
    static boolean due(MinecraftServer server, ToolsData.Schedule s) {
        if (!s.on) return false;
        LocalDate d = today();
        return switch (s.kind) {
            case DAYS -> s.days.contains(d.getDayOfWeek().getValue());
            case FULL_MOON -> fullMoonNight(server);
            case DATE -> s.date.length() == 5 ? s.date.equals(String.format("%02d-%02d", d.getMonthValue(),
                    d.getDayOfMonth())) : s.date.equals(d.toString());
            default -> false;
        };
    }

    /** "weekends", "Mondays and Fridays", "full-moon nights", "Dec 25 (every year)" */
    public static String when(ToolsData.Schedule s) {
        return switch (s.kind) {
            case DAYS -> {
                if (s.days.size() == 2 && s.days.contains(6) && s.days.contains(7)) yield "weekends";
                if (s.days.size() == 7) yield "every day";
                List<String> n = new ArrayList<>();
                for (int i = 1; i <= 7; i++)
                    if (s.days.contains(i)) n.add(DayOfWeek.of(i).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "s");
                yield n.isEmpty() ? "no days" : n.size() == 1 ? n.get(0)
                        : String.join(", ", n.subList(0, n.size() - 1)) + " and " + n.get(n.size() - 1);
            }
            case FULL_MOON -> "full-moon nights";
            case DATE -> s.date.length() == 5 ? dateWords(s.date) + " (every year)" : s.date;
            default -> s.kind;
        };
    }

    private static String dateWords(String mmdd) {
        try {
            int m = Integer.parseInt(mmdd.substring(0, 2)), d = Integer.parseInt(mmdd.substring(3));
            return java.time.Month.of(m).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + d;
        } catch (RuntimeException e) {
            return mmdd;
        }
    }

    /**
     * A new schedule: preset (built-in id or a saved one's name), kind and its day(s). Days: "weekends", "weekdays",
     * "every", or names/numbers ("mon,fri", "1,5"). Date: "MM-dd" every year, "yyyy-MM-dd" once.
     */
    public static AdminServer.Result add(MinecraftServer server, String who, String preset, String kind,
            String when) {
        String no = PackMode.refusal();
        if (no != null) return AdminServer.Result.fail(no);
        AdminTransfer.Bundle b = AdminTransfer.preset(preset);
        if (b == null) return AdminServer.Result.fail("No preset called " + preset + ".");
        ToolsData d = ToolsData.get(server);
        if (d.s.schedules.size() >= MOST) return AdminServer.Result.fail("This world has " + MOST + " schedules.");
        ToolsData.Schedule s = new ToolsData.Schedule();
        String k = kind.toLowerCase(Locale.ROOT);
        String w = when == null ? "" : when.strip().toLowerCase(Locale.ROOT);
        switch (k) {
            case "weekends", "weekend" -> {
                s.kind = DAYS;
                s.days = new ArrayList<>(List.of(6, 7));
            }
            case DAYS, "day" -> {
                s.kind = DAYS;
                List<Integer> days = days(w);
                if (days == null || days.isEmpty())
                    return AdminServer.Result.fail("Name the days: weekends, weekdays, every, or mon,tue,... (or 1-7,"
                            + " Monday 1).");
                s.days = days;
            }
            case FULL_MOON, "moon" -> s.kind = FULL_MOON;
            case DATE -> {
                s.kind = DATE;
                if (!w.matches("\\d{2}-\\d{2}") && !w.matches("\\d{4}-\\d{2}-\\d{2}"))
                    return AdminServer.Result.fail("A date is MM-DD (every year) or YYYY-MM-DD (once).");
                try {
                    if (w.length() == 5) java.time.MonthDay.parse("--" + w);
                    else LocalDate.parse(w);
                } catch (RuntimeException e) {
                    return AdminServer.Result.fail("There's no such date as " + w + ".");
                }
                s.date = w;
            }
            default -> {
                return AdminServer.Result.fail("A schedule is weekends, days <which>, fullmoon or date <MM-DD>.");
            }
        }
        String id = AdminTransfer.builtInId(preset);
        s.preset = id != null ? id : preset;
        s.name = AdminTransfer.title(b);
        s.by = who;
        s.id = d.newId("s");
        d.s.schedules.add(s);
        d.setDirty();
        String line = s.name + " on " + when(s) + ".";
        Areas.log(server, who, "schedule", "Schedule added: " + line);
        return new AdminServer.Result(true, List.of("Schedule: " + line + " It goes on by itself then, and back off"
                + " after."), List.of(who + " scheduled " + line));
    }

    /** "weekends" / "weekdays" / "every" / "mon,wed" / "1,3" to day numbers (1 Monday), null if not days */
    static @Nullable List<Integer> days(String w) {
        switch (w) {
            case "weekends", "weekend" -> {
                return new ArrayList<>(List.of(6, 7));
            }
            case "weekdays" -> {
                return new ArrayList<>(List.of(1, 2, 3, 4, 5));
            }
            case "every", "everyday", "all" -> {
                return new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7));
            }
            default -> {}
        }
        List<Integer> out = new ArrayList<>();
        for (String p : w.split("[,\\s]+")) {
            if (p.isEmpty()) continue;
            int n = -1;
            try {
                n = Integer.parseInt(p);
            } catch (NumberFormatException e) {
                for (DayOfWeek dw : DayOfWeek.values())
                    if (dw.name().toLowerCase(Locale.ROOT).startsWith(p) && p.length() >= 2) n = dw.getValue();
            }
            if (n < 1 || n > 7) return null;
            if (!out.contains(n)) out.add(n);
        }
        java.util.Collections.sort(out);
        return out;
    }

    public static @Nullable ToolsData.Schedule find(ToolsData d, String idOrNumber) {
        for (ToolsData.Schedule s : d.s.schedules) if (s.id.equals(idOrNumber)) return s;
        try {
            int i = Integer.parseInt(idOrNumber.trim()) - 1;
            return i >= 0 && i < d.s.schedules.size() ? d.s.schedules.get(i) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static AdminServer.Result remove(MinecraftServer server, String who, String id) {
        ToolsData d = ToolsData.get(server);
        ToolsData.Schedule s = find(d, id);
        if (s == null) return AdminServer.Result.fail("There's no schedule " + id + ". /giants admin schedule lists them.");
        d.s.schedules.remove(s);
        d.setDirty();
        Areas.log(server, who, "schedule", "Schedule removed: " + s.name + " on " + when(s));
        tick(server);
        return new AdminServer.Result(true, List.of("The schedule " + s.name + " on " + when(s) + " is gone."),
                List.of(who + " removed the schedule " + s.name + " on " + when(s) + "."));
    }

    public static AdminServer.Result turn(MinecraftServer server, String who, String id, boolean on) {
        ToolsData d = ToolsData.get(server);
        ToolsData.Schedule s = find(d, id);
        if (s == null) return AdminServer.Result.fail("There's no schedule " + id + ".");
        s.on = on;
        d.setDirty();
        Areas.log(server, who, "schedule", "Schedule " + (on ? "on" : "off") + ": " + s.name + " on " + when(s));
        tick(server);
        return new AdminServer.Result(true, List.of("The schedule " + s.name + " on " + when(s) + " is "
                + (on ? "on." : "off.")), List.of(who + " turned the schedule " + s.name + (on ? " on." : " off.")));
    }

    public static AdminServer.Result list(MinecraftServer server) {
        ToolsData d = ToolsData.get(server);
        List<String> lines = new ArrayList<>();
        if (d.s.schedules.isEmpty())
            lines.add("No schedules. /giants admin schedule add <preset> weekends|days <days>|fullmoon|date <MM-DD>.");
        else lines.add("Schedules (the first one due goes on):");
        int i = 1;
        for (ToolsData.Schedule s : d.s.schedules)
            lines.add("  " + i++ + ". " + s.name + " on " + when(s) + (s.on ? "" : " (off)")
                    + (d.s.schedule != null && d.s.schedule.id.equals(s.id) ? " (on now)" : ""));
        return AdminServer.Result.said(lines);
    }

    /** once a second: the one running ends when its time is over; else the first one due goes on */
    public static void tick(MinecraftServer server) {
        ToolsData d = ToolsData.get(server);
        if (d.s.schedules.isEmpty() && d.s.schedule == null) return;
        ToolsData.Running r = d.s.schedule;
        if (r != null) {
            ToolsData.Schedule s = find(d, r.id);
            if (s != null && due(server, s)) return;
            d.s.schedule = null;
            d.setDirty();
            int n = Timed.restore(server, "Schedule", r.puts, "schedule-end", "Schedule ended: " + r.name);
            Tools.tellEveryone(server, Tools.gold("The " + r.name + " schedule is over: the giants are back as they"
                    + " were."));
            net.jj.giants.GiantsGuideMod.LOG.info("Giants schedule {} ended ({} put back)", r.name, n);
            ToolsAdmin.changed();
        }
        if (PackMode.refusal() != null) return;
        for (ToolsData.Schedule s : d.s.schedules) {
            if (!due(server, s)) continue;
            AdminTransfer.Bundle b = AdminTransfer.preset(s.preset);
            if (b == null) continue;
            ToolsData.Running run = new ToolsData.Running();
            run.id = s.id;
            run.name = s.name;
            run.by = s.by;
            run.started = System.currentTimeMillis();
            run.puts = Timed.apply(server, "Schedule", AdminTransfer.plan(server, b), "schedule",
                    "Schedule started: " + s.name + " (" + when(s) + ")", true);
            d.s.schedule = run;
            d.setDirty();
            Tools.tellEveryone(server, Tools.gold("Schedule: " + s.name + " is on (" + when(s) + ")."));
            ToolsAdmin.changed();
            return;
        }
    }

    /** ends the one running now (it goes on again next time it is due) */
    public static AdminServer.Result stopNow(MinecraftServer server, String who) {
        ToolsData d = ToolsData.get(server);
        ToolsData.Running r = d.s.schedule;
        if (r == null) return AdminServer.Result.fail("No schedule is on now.");
        ToolsData.Schedule s = find(d, r.id);
        if (s != null) s.on = false;
        tick(server);
        return new AdminServer.Result(true, List.of("The " + r.name + " schedule is off: everything it changed is back"
                + " (turn it on again on the page)."), List.of(who + " stopped the schedule " + r.name + "."));
    }
}
