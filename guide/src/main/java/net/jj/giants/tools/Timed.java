package net.jj.giants.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.admin.AdminTransfer;
import net.jj.giants.admin.AdminWorld;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 6, ADMIN_BRIDGE.md §22.5) Settings changed for a while (an event, a schedule's preset) and put back after.
 * The change is planned like any preset (each giant's own range, locked settings left alone), kept for the world,
 * logged as one change with its own undo, and sent to the giants. Putting back only touches the settings that still
 * hold what it set: one an admin changed meanwhile stays as they left it.
 */
public final class Timed {
    private Timed() {}

    /** applies a plan; what it set, to put back later (empty: nothing changed) */
    public static List<ToolsData.Put> apply(MinecraftServer server, String who, AdminTransfer.Plan plan, String logId,
            String logName, boolean snapshot) {
        AdminWorld w = AdminWorld.get(server);
        List<AdminTransfer.Step> steps = plan.changes().stream().filter(s -> w.lock(s.giant(), s.id()) == null)
                .toList();
        List<ToolsData.Put> puts = new ArrayList<>();
        if (steps.isEmpty()) return puts;
        if (snapshot) w.snapshot(who, "Before " + logName);
        Map<String, Map<String, JsonElement>> before = new LinkedHashMap<>();
        for (AdminTransfer.Step s : steps) {
            before.computeIfAbsent(s.giant(), k -> new HashMap<>()).put(s.id(), s.old());
            if (s.now() == null) w.remove(s.giant(), s.id());
            else w.put(s.giant(), s.id(), s.now());
            ToolsData.Put p = new ToolsData.Put();
            p.giant = s.giant();
            p.id = s.id();
            p.old = s.old() == null ? null : s.old().toString();
            p.now = s.now() == null ? null : s.now().toString();
            puts.add(p);
        }
        w.log(new AdminWorld.Change(System.currentTimeMillis(), who, ToolsAdmin.KIND, "tools:" + logId, logName, "",
                steps.size() + (steps.size() == 1 ? " change" : " changes"), AdminWorld.Change.undo(before)));
        AdminServer.push(server, w);
        return puts;
    }

    /** puts back what it set, where it still holds that; how many went back */
    public static int restore(MinecraftServer server, String who, List<ToolsData.Put> puts, String logId,
            String logName) {
        AdminWorld w = AdminWorld.get(server);
        Map<String, Map<String, JsonElement>> before = new LinkedHashMap<>();
        int n = 0;
        for (ToolsData.Put p : puts) {
            if (w.lock(p.giant, p.id) != null) continue;
            JsonElement now = w.values(p.giant).get(p.id), set = parse(p.now), old = parse(p.old);
            boolean still = now == null ? set == null : set != null && AdminSetting.same(now, set);
            if (!still) continue;
            before.computeIfAbsent(p.giant, k -> new HashMap<>()).put(p.id, now);
            if (old == null) w.remove(p.giant, p.id);
            else w.put(p.giant, p.id, old);
            n++;
        }
        if (n > 0) {
            w.log(new AdminWorld.Change(System.currentTimeMillis(), who, ToolsAdmin.KIND, "tools:" + logId, logName, "",
                    n + (n == 1 ? " change" : " changes") + " put back", AdminWorld.Change.undo(before)));
            AdminServer.push(server, w);
        }
        return n;
    }

    private static @Nullable JsonElement parse(@Nullable String s) {
        if (s == null) return null;
        try {
            return JsonParser.parseString(s);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
