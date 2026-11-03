package net.jj.giants.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.jj.giants.Giant;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 2C, ADMIN_BRIDGE.md 21.4) One setting the giants share, set to the same VALUE for every giant that has it at
 * once ("Size of the world's own: 2 for every giant"), the admin book's All giants page and
 * {@code /giants admin setall}. Each giant keeps to his own range: a number past his smallest or biggest is pulled in
 * and the reply says so; an option he doesn't have, or a setting locked by another admin, leaves him as he is. In a
 * world the whole change is one entry in the change log (one undo).
 */
public final class AdminEvery {
    private AdminEvery() {}

    /** what one giant gets: his setting, the value he takes, and a note when it isn't quite what was asked */
    public record Pick(String giant, String name, AdminSetting s, JsonElement v, @Nullable String note) {}

    /** what setting it for every giant would do */
    public record Plan(String id, String name, List<Pick> picks, List<String> notes) {}

    /** ids shared by at least two giants are "every giant" settings; moves and their rows never are */
    public static boolean shareable(String id) {
        return !id.startsWith("move.") && !id.startsWith("all.");
    }

    /** the giants' settings with this id (or the same id in another case), in the view, each giant's own row */
    static Map<String, AdminSetting> rows(AdminView view, String id) {
        Map<String, AdminSetting> out = new LinkedHashMap<>();
        for (Giant g : AdminServer.giants()) {
            AdminJson.Report r = view.report(g);
            if (r == null) continue;
            AdminSetting s = r.setting(id);
            if (s == null) for (AdminSetting x : r.settings()) if (x.id().equalsIgnoreCase(id)) s = x;
            if (s != null && shareable(s.id())) out.put(g.key(), s);
        }
        return out;
    }

    /** the value typed, fitted to one giant's row (pulled into his range); throws when it isn't a value at all */
    static Pick fit(String giant, String gname, AdminSetting s, String text) throws AdminSetting.BadValue {
        String t = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        switch (s.type()) {
            case BOOL, CHOICE -> {
                return new Pick(giant, gname, s, s.parse(t), null);
            }
            case RANGE -> {
                String[] w = t.replace("..", "-").replace("to", "-").split("\\s*-\\s*|\\s+");
                double lo, hi;
                try {
                    lo = Double.parseDouble(w[0]);
                    hi = w.length > 1 ? Double.parseDouble(w[1]) : lo;
                } catch (RuntimeException e) {
                    throw new AdminSetting.BadValue(s.name() + " has to be two numbers like 0.5-1.5.");
                }
                if (!Double.isFinite(lo) || !Double.isFinite(hi))
                    throw new AdminSetting.BadValue(s.name() + " has to be two numbers like 0.5-1.5.");
                double a = Math.min(lo, hi), b = Math.max(lo, hi);
                double ca = clamp(a, s), cb = clamp(b, s);
                JsonArray arr = new JsonArray();
                arr.add(ca);
                arr.add(cb);
                String note = ca != a || cb != b
                        ? gname + ": " + AdminSetting.plain(a) + "-" + AdminSetting.plain(b) + " is past his range ("
                                + s.rangeText() + "), so " + AdminSetting.plain(ca) + "-" + AdminSetting.plain(cb) + "."
                        : null;
                return new Pick(giant, gname, s, arr, note);
            }
            default -> {
                double v;
                try {
                    v = Double.parseDouble(t.startsWith("x") ? t.substring(1) : t);
                } catch (RuntimeException e) {
                    throw new AdminSetting.BadValue(s.name() + " has to be a number.");
                }
                if (!Double.isFinite(v)) throw new AdminSetting.BadValue(s.name() + " has to be a number.");
                double c = clamp(v, s);
                if (s.type() == AdminSetting.Type.INT) c = Math.rint(c);
                JsonElement e = s.type() == AdminSetting.Type.INT ? new JsonPrimitive((long) c) : new JsonPrimitive(c);
                String note = null;
                if (c > v + 1e-9 || c < v - 1e-9)
                    note = gname + ": " + AdminSetting.plain(v) + " is past his "
                            + (v > s.max() ? "most (" + AdminSetting.plain(s.max()) + ")"
                            : v < s.min() ? "least (" + AdminSetting.plain(s.min()) + ")"
                            : "whole numbers") + ", so " + AdminSetting.plain(c) + ".";
                return new Pick(giant, gname, s, e, note);
            }
        }
    }

    private static double clamp(double v, AdminSetting s) {
        return Math.max(s.min(), Math.min(s.max(), v));
    }

    /**
     * What setting it for every giant would do. Null with the reason in notes when nobody has it or the value can't be
     * read at all. Locked ones (by someone other than who) are left out and named.
     */
    public static Plan plan(AdminView view, String id, String text, String who) {
        Map<String, AdminSetting> rows = rows(view, id);
        List<Pick> picks = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        if (rows.isEmpty()) {
            notes.add("No giant here has a setting called " + id + ".");
            return new Plan(id, id, picks, notes);
        }
        String name = rows.values().iterator().next().name();
        String realId = rows.values().iterator().next().id();
        String bad = null;
        for (var e : rows.entrySet()) {
            Giant g = AdminServer.giant(e.getKey());
            String gname = g == null ? e.getKey() : AdminServer.nameOf(g);
            AdminWorld.Lock l = view.world().lock(e.getKey(), e.getValue().id());
            if (l != null && !l.who().equals(who)) {
                notes.add(gname + ": locked by " + l.who() + ", so left as it is.");
                continue;
            }
            try {
                Pick p = fit(e.getKey(), gname, e.getValue(), text);
                picks.add(p);
                if (p.note() != null) notes.add(p.note());
            } catch (AdminSetting.BadValue x) {
                if (e.getValue().type() == AdminSetting.Type.CHOICE)
                    notes.add(gname + ": he has no " + text.trim() + " (" + e.getValue().rangeText() + "), so left as"
                            + " it is.");
                else bad = x.getMessage();
            }
        }
        if (picks.isEmpty() && bad != null) notes.add(0, bad);
        return new Plan(realId, name, picks, notes);
    }

    /** the words for a plan done: "Size of the world's own set to 2 for every giant (8) in this world." */
    static List<String> words(Plan p, String where, int changed) {
        List<String> lines = new ArrayList<>();
        String v = p.picks().isEmpty() ? "" : AdminSetting.text(p.picks().get(0).v());
        // (the value most of them got: the one asked for, unless every one was pulled in)
        Map<String, Integer> count = new HashMap<>();
        for (Pick k : p.picks()) count.merge(AdminSetting.text(k.v()), 1, Integer::sum);
        for (var e : count.entrySet()) if (e.getValue() > count.getOrDefault(v, 0)) v = e.getKey();
        int n = p.picks().size();
        lines.add(p.name() + " set to " + v + " for every giant (" + n + (n == 1 ? " giant" : " giants") + ") " + where
                + (changed < n ? " (" + (n - changed) + " already had it)" : "") + ".");
        lines.addAll(p.notes());
        return lines;
    }

    /** /giants admin setall (and the book's All giants page): this world, one change in the log */
    public static AdminServer.Result set(MinecraftServer server, String who, String id, String text) {
        AdminView view = AdminView.of(server);
        Plan p = plan(view, id, text, who);
        if (p.picks().isEmpty()) return AdminServer.Result.fail(String.join(" ", p.notes()));
        AdminWorld w = AdminWorld.get(server);
        Map<String, Map<String, JsonElement>> before = new LinkedHashMap<>();
        int changed = 0;
        for (Pick k : p.picks()) {
            AdminSetting s = k.s();
            if (s.set() && AdminSetting.same(s.value(), k.v())) continue;
            before.computeIfAbsent(k.giant(), x -> new HashMap<>()).put(s.id(), s.set() ? s.value() : null);
            w.put(k.giant(), s.id(), k.v());
            changed++;
        }
        List<String> lines = words(p, "in this world", changed);
        if (changed > 0) {
            w.log(new AdminWorld.Change(System.currentTimeMillis(), who, "everything", "every:" + p.id(),
                    p.name(), "", AdminSetting.text(p.picks().get(0).v()) + " (" + changed
                            + (changed == 1 ? " giant" : " giants") + ")",
                    AdminWorld.Change.undo(before)));
            AdminServer.push(server, w);
        }
        AdminSetting first = p.picks().get(0).s();
        if (!first.whenNote().isEmpty()) lines.add(first.whenNote());
        return new AdminServer.Result(true, lines, List.of(who + " set " + p.name() + " to "
                + AdminSetting.text(p.picks().get(0).v()) + " for every giant."));
    }

    /** the same for all worlds: each giant's server default, as /giants admin set ... allworlds does it */
    public static AdminServer.Result setDefault(MinecraftServer server, String who, String id, String text) {
        Plan p = plan(AdminView.of(server), id, text, who);
        if (p.picks().isEmpty()) return AdminServer.Result.fail(String.join(" ", p.notes()));
        List<String> lines = new ArrayList<>(), told = new ArrayList<>(), failed = new ArrayList<>();
        int ok = 0;
        for (Pick k : p.picks()) {
            AdminServer.Result r = AdminServer.setDefault(server, who, k.giant(), k.s().id(), AdminSetting.text(k.v()));
            if (r.ok()) ok++;
            else failed.addAll(r.lines());
        }
        lines.add(p.name() + " set for every giant (" + ok + ") for all worlds (server default).");
        lines.addAll(p.notes());
        lines.addAll(failed);
        told.add(who + " set " + p.name() + " to " + AdminSetting.text(p.picks().get(0).v())
                + " for every giant, for all worlds.");
        return new AdminServer.Result(ok > 0, lines, told);
    }

    /** every giant's value of this setting back to the server default in this world (one change, one undo) */
    public static AdminServer.Result reset(MinecraftServer server, String who, String id) {
        AdminView view = AdminView.of(server);
        Map<String, AdminSetting> rows = rows(view, id);
        if (rows.isEmpty()) return AdminServer.Result.fail("No giant here has a setting called " + id + ".");
        AdminWorld w = AdminWorld.get(server);
        Map<String, Map<String, JsonElement>> before = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();
        String name = rows.values().iterator().next().name(), realId = rows.values().iterator().next().id();
        for (var e : rows.entrySet()) {
            AdminSetting s = e.getValue();
            if (!s.set()) continue;
            AdminWorld.Lock l = w.lock(e.getKey(), s.id());
            if (l != null && !l.who().equals(who)) {
                notes.add(e.getKey() + ": locked by " + l.who() + ", so left as it is.");
                continue;
            }
            before.computeIfAbsent(e.getKey(), x -> new HashMap<>()).put(s.id(), s.value());
            w.remove(e.getKey(), s.id());
        }
        if (before.isEmpty())
            return AdminServer.Result.said(List.of(name + " is already the server default for every giant."));
        w.log(new AdminWorld.Change(System.currentTimeMillis(), who, "everything", "every:" + realId, name, "",
                "server default (" + before.size() + (before.size() == 1 ? " giant" : " giants") + ")",
                AdminWorld.Change.undo(before)));
        AdminServer.push(server, w);
        List<String> lines = new ArrayList<>(List.of(name + ": back to the server default for every giant ("
                + before.size() + ") in this world."));
        lines.addAll(notes);
        return new AdminServer.Result(true, lines, List.of(who + " put " + name + " back to the server default for"
                + " every giant."));
    }

    // the Create World screen's draft (the same words, no log)

    public static AdminServer.Result set(AdminDraft d, String id, String text) {
        Plan p = plan(d, id, text, "");
        if (p.picks().isEmpty()) return AdminServer.Result.fail(String.join(" ", p.notes()));
        int changed = 0;
        for (Pick k : p.picks()) {
            if (k.s().set() && AdminSetting.same(k.s().value(), k.v())) continue;
            d.world().put(k.giant(), k.s().id(), k.v());
            changed++;
        }
        d.markEdited();
        return AdminServer.Result.said(words(p, "for the new world", changed));
    }

    public static AdminServer.Result reset(AdminDraft d, String id) {
        Map<String, AdminSetting> rows = rows(d, id);
        int n = 0;
        String name = rows.isEmpty() ? id : rows.values().iterator().next().name();
        for (var e : rows.entrySet()) if (d.world().remove(e.getKey(), e.getValue().id())) n++;
        if (n > 0) d.markEdited();
        return AdminServer.Result.said(List.of(n == 0 ? name + " is already the server default for every giant."
                : name + ": back to the server default for every giant (" + n + ") for the new world."));
    }
}
