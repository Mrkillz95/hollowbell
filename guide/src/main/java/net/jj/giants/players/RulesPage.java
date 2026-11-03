package net.jj.giants.players;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminInsight;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.admin.AdminTransfer;
import net.jj.giants.admin.AdminWorld;
import net.minecraft.server.MinecraftServer;

/**
 * The players' Rules page (ADMIN_BRIDGE.md 20.14): the preset, what it means, how dangerous it is, and (full) the
 * rules that differ from Classic, made from the world's own values. Admins choose full, basic or hidden.
 */
public final class RulesPage {
    private RulesPage() {}

    public static final int MOST_LINES = 24;

    public static JsonObject json(MinecraftServer server) {
        JsonObject o = new JsonObject();
        String mode = Players.option(server, "rulesPage");
        o.addProperty("mode", mode);
        if (mode.equals("hidden")) {
            o.addProperty("hidden", "The admins keep this world's rules to themselves.");
            return o;
        }
        AdminWorld w = AdminWorld.get(server);
        String name = AdminInsight.presetName(w.log(), !w.giantsWithValues().isEmpty());
        o.addProperty("preset", name);
        o.addProperty("about", about(name));
        if (mode.equals("basic")) return o;
        List<AdminInsight.View> views = new ArrayList<>();
        Map<Giant, AdminJson.Report> reports = new LinkedHashMap<>();
        for (Giant g : AdminServer.giants()) {
            AdminJson.Report r = AdminServer.report(server, g);
            if (r == null) continue;
            reports.put(g, r);
            views.add(AdminInsight.view(r));
        }
        AdminInsight.Danger d = AdminInsight.danger(views, Players.allValues(server));
        JsonObject dj = new JsonObject();
        dj.addProperty("level", d.level());
        dj.addProperty("label", d.label());
        dj.addProperty("reason", d.reason());
        o.add("danger", dj);
        List<String> lines = lines(w, reports);
        JsonArray a = new JsonArray();
        for (int i = 0; i < lines.size() && i < MOST_LINES; i++) a.add(lines.get(i));
        o.add("rules", a);
        if (lines.size() > MOST_LINES) o.addProperty("more", lines.size() - MOST_LINES);
        List<String> pl = playerLines(w);
        JsonArray pa = new JsonArray();
        for (String s : pl) pa.add(s);
        o.add("players", pa);
        return o;
    }

    /** what a preset (by its name on Home) is about */
    static String about(String name) {
        String base = name.endsWith(", changed") ? name.substring(0, name.length() - 9) : name;
        if (base.equals("Custom")) return "Custom settings: the admins chose them one by one.";
        if (base.equals("Imported settings")) return "Settings brought in from another world.";
        if (base.equals("Restored snapshot")) return "Settings put back from an earlier snapshot.";
        AdminTransfer.Bundle b = AdminTransfer.preset(base);
        String about = b == null ? "" : b.about();
        if (base.equals("Classic")) about = "Every giant as he comes: the usual game.";
        return name.endsWith(", changed") ? about + " The admins changed a few things since." : about;
    }

    /** the giant settings this world set, folded across giants where every one of them has the same */
    static List<String> lines(AdminWorld w, Map<Giant, AdminJson.Report> reports) {
        List<String> out = new ArrayList<>();
        // the all-giants multipliers
        for (AdminSetting s : AdminCatalog.multipliers()) {
            JsonElement v = w.values(AdminCatalog.ALL).get(s.id());
            if (v != null) out.add("Every giant's " + s.name().toLowerCase(java.util.Locale.ROOT) + " x"
                    + AdminSetting.text(v) + ".");
        }
        // each giant's own: id -> (value text -> giant names)
        Map<String, Map<String, List<String>>> by = new LinkedHashMap<>();
        Map<String, AdminSetting> rows = new LinkedHashMap<>();
        List<String> movesOff = new ArrayList<>();
        for (var e : reports.entrySet()) {
            String gname = AdminServer.nameOf(e.getKey());
            for (AdminSetting s : e.getValue().settings()) {
                if (!s.set() || s.cat().equals("worldgen")) continue;
                if (!s.group().isEmpty()) {
                    if (s.id().endsWith(".on") && !s.value().getAsBoolean())
                        movesOff.add(gname + ": " + s.name().replaceAll(": on$", ""));
                    continue;
                }
                rows.putIfAbsent(s.id(), s);
                by.computeIfAbsent(s.id(), k -> new LinkedHashMap<>())
                        .computeIfAbsent(AdminSetting.text(s.value()), k -> new ArrayList<>()).add(gname);
            }
        }
        int n = reports.size();
        for (var e : by.entrySet()) {
            AdminSetting s = rows.get(e.getKey());
            for (var v : e.getValue().entrySet()) {
                String who = v.getValue().size() == n && n > 1 ? "Every giant" : String.join(", ", v.getValue());
                out.add(who + ": " + s.name() + " " + v.getKey() + " (usually " + AdminSetting.text(s.def())
                        + ").");
            }
        }
        if (!movesOff.isEmpty()) out.add("Moves turned off: " + String.join(", ", movesOff) + ".");
        if (out.isEmpty()) out.add("Every giant as he comes, as in Classic.");
        return out;
    }

    /** the player settings not at their default ("Riding giants: off") */
    static List<String> playerLines(AdminWorld w) {
        List<String> out = new ArrayList<>();
        Map<String, JsonElement> v = w.values(AdminCatalog.ALL);
        for (AdminSetting s : AdminCatalog.players()) {
            JsonElement x = v.get(s.id());
            if (x == null || AdminSetting.same(x, s.def())) continue;
            out.add(s.name() + ": " + AdminSetting.text(x) + ".");
        }
        return out;
    }
}
