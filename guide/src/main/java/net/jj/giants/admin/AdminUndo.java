package net.jj.giants.admin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Undo from the change log (Part 2B, the Changes page), using what each entry keeps to put it back (its Undo, Part
 * 1): one change, or every change back to (and with) one of them. An undo is itself a change in the log, with its
 * own undo, so it can be undone too. Locked settings are left alone unless it is their locker undoing.
 */
public final class AdminUndo {
    private AdminUndo() {}

    /** the log's entry i, if it is still the one made at that time (the log moves on); else null */
    static @Nullable AdminWorld.Change at(AdminWorld w, int i, long time) {
        List<AdminWorld.Change> log = w.log();
        if (i < 0 || i >= log.size() || log.get(i).time() != time) return null;
        return log.get(i);
    }

    /** an entry's undo: giant to id to value (null = back to the server default); empty when it has none */
    static Map<String, Map<String, JsonElement>> undoOf(AdminWorld.Change c) {
        Map<String, Map<String, JsonElement>> out = new LinkedHashMap<>();
        if (c.undo().isEmpty()) return out;
        try {
            JsonObject o = JsonParser.parseString(c.undo()).getAsJsonObject();
            for (var g : o.entrySet()) {
                Map<String, JsonElement> m = new LinkedHashMap<>();
                for (var e : g.getValue().getAsJsonObject().entrySet())
                    m.put(e.getKey(), e.getValue().isJsonNull() ? null : e.getValue());
                out.put(g.getKey(), m);
            }
        } catch (RuntimeException e) {
            out.clear();
        }
        return out;
    }

    public static boolean undoable(AdminWorld.Change c) {
        return !undoOf(c).isEmpty();
    }

    private static final String MOVED = "The change log has moved on. Look again and pick it once more.";
    private static final String CANT =
            "That one can't be undone here: it changed a server default (a config file), a lock or a role.";

    /** undoes one change */
    public static AdminServer.Result undo(MinecraftServer server, String who, int i, long time) {
        AdminWorld w = AdminWorld.get(server);
        AdminWorld.Change c = at(w, i, time);
        if (c == null) return AdminServer.Result.fail(MOVED);
        Map<String, Map<String, JsonElement>> back = undoOf(c);
        if (back.isEmpty()) return AdminServer.Result.fail(CANT);
        String text = AdminServer.changeText(c);
        return put(server, w, who, back, c.giant().equals("everything") ? "everything" : c.giant(),
                "undo of " + text, "Undone: " + text + ".", who + " undid " + text + ".");
    }

    /** undoes every change from the newest back to (and with) entry i, as one change */
    public static AdminServer.Result undoBackTo(MinecraftServer server, String who, int i, long time) {
        AdminWorld w = AdminWorld.get(server);
        AdminWorld.Change c = at(w, i, time);
        if (c == null) return AdminServer.Result.fail(MOVED);
        List<AdminWorld.Change> log = w.log();
        // newest first: each one's undo laid over the last, so what is left is how it was before entry i
        Map<String, Map<String, JsonElement>> back = new LinkedHashMap<>();
        int undone = 0, skipped = 0;
        for (int k = log.size() - 1; k >= i; k--) {
            Map<String, Map<String, JsonElement>> u = undoOf(log.get(k));
            if (u.isEmpty()) {
                skipped++;
                continue;
            }
            undone++;
            for (var g : u.entrySet())
                for (var e : g.getValue().entrySet())
                    back.computeIfAbsent(g.getKey(), x -> new HashMap<>()).put(e.getKey(), e.getValue());
        }
        if (back.isEmpty()) return AdminServer.Result.fail(CANT);
        String when = new java.text.SimpleDateFormat("MMM d HH:mm", Locale.ROOT).format(new java.util.Date(c.time()));
        String n = undone + (undone == 1 ? " change" : " changes");
        AdminServer.Result r = put(server, w, who, back, "everything", "undo back to " + when + " (" + n + ")",
                "Undone back to " + when + ": " + n + ".", who + " undid every change back to " + when + ".");
        if (r.ok() && skipped > 0) {
            List<String> lines = new ArrayList<>(r.lines());
            lines.add(skipped + (skipped == 1 ? " change" : " changes")
                    + " to server defaults, locks or roles can't be undone here and stay.");
            return new AdminServer.Result(true, lines, r.announce());
        }
        return r;
    }

    /** puts these values back (null: the server default), logged as one change with its own undo */
    private static AdminServer.Result put(MinecraftServer server, AdminWorld w, String who,
            Map<String, Map<String, JsonElement>> back, String giant, String name, String said, String told) {
        Map<String, Map<String, JsonElement>> before = new LinkedHashMap<>();
        List<String> locked = new ArrayList<>();
        int n = 0;
        for (var g : back.entrySet())
            for (var e : g.getValue().entrySet()) {
                AdminWorld.Lock l = w.lock(g.getKey(), e.getKey());
                if (l != null && !l.who().equals(who)) {
                    locked.add(AdminServer.giantWord(g.getKey()) + " "
                            + AdminServer.settingName(server, g.getKey(), e.getKey()) + " (" + l.who() + ")");
                    continue;
                }
                JsonElement now = w.values(g.getKey()).get(e.getKey()), want = e.getValue();
                if (now == null && want == null || now != null && want != null && AdminSetting.same(now, want))
                    continue;
                before.computeIfAbsent(g.getKey(), x -> new HashMap<>()).put(e.getKey(), now);
                if (want == null) w.remove(g.getKey(), e.getKey());
                else w.put(g.getKey(), e.getKey(), want);
                n++;
            }
        List<String> lines = new ArrayList<>();
        if (n == 0) {
            lines.add("Nothing to put back: it is already as it was.");
            if (!locked.isEmpty()) lines.add("Locked, so left as they are: " + String.join(", ", locked) + ".");
            return new AdminServer.Result(true, lines, List.of());
        }
        w.log(new AdminWorld.Change(System.currentTimeMillis(), who, giant, "undo", name, "",
                n + (n == 1 ? " setting" : " settings"), AdminWorld.Change.undo(before)));
        AdminServer.push(server, w);
        lines.add(said + " " + n + (n == 1 ? " setting" : " settings") + " put back.");
        if (!locked.isEmpty()) lines.add("Locked, so left as they are: " + String.join(", ", locked) + ".");
        return new AdminServer.Result(true, lines, List.of(told));
    }

    /** a snapshot's preview: what putting it back would change (applied with the preset/import confirm) */
    public static @Nullable AdminTransfer.Plan restorePlan(MinecraftServer server, int i, long time) {
        List<AdminWorld.Snapshot> all = AdminWorld.get(server).snapshots();
        if (i < 0 || i >= all.size() || all.get(i).time() != time) return null;
        return AdminTransfer.plan(server, AdminTransfer.snapshotBundle(all.get(i)));
    }
}
