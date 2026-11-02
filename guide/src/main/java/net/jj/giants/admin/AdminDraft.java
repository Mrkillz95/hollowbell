package net.jj.giants.admin;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.jj.giants.Giant;
import org.jetbrains.annotations.Nullable;

/**
 * The Create World screen's choice (ADMIN_BRIDGE.md §18.6): the settings a world will start with, before there is a
 * world or a server. It holds only what the creator chose (a preset, then any changes), in a world-less AdminWorld,
 * and reads each giant's own list the way the book does, asked with no server (in the same game his config file is the
 * server default the new world will have), with the draft's values put in. Every change is checked exactly as
 * /giants admin checks it. When the world is made, the draft goes in before its first tick (AdminCreation).
 */
public final class AdminDraft implements AdminView {
    private final AdminWorld w = new AdminWorld();
    private final Function<Giant, AdminJson.@Nullable Report> base;
    /** the preset chosen last ("Classic": nothing), and whether anything changed after it */
    private String preset = "Classic";
    private boolean edited;
    private @Nullable AdminTransfer.Plan held;
    private String heldKind = "";

    /** a draft over the giants' own lists as this game has them (no server) */
    public AdminDraft() {
        this(g -> AdminServer.report(null, g));
    }

    /** a draft over some other lists (the tests; a blank copy of a world for a preset's rating) */
    public AdminDraft(Function<Giant, AdminJson.@Nullable Report> base) {
        this.base = base;
    }

    /** a view with nothing set over the same giants as this one (a preset's rating is worked out from there) */
    static AdminView blankLike(AdminView view) {
        return new AdminDraft(view::report);
    }

    @Override
    public AdminWorld world() {
        return w;
    }

    @Override
    public @Nullable AdminJson.Report report(Giant g) {
        AdminJson.Report r = base.apply(g);
        if (r == null) return null;
        Map<String, JsonElement> mine = w.values(g.key());
        List<AdminSetting> list = new ArrayList<>();
        for (AdminSetting s : r.settings()) {
            JsonElement v = mine.get(s.id());
            list.add(new AdminSetting(s.giant(), s.id(), s.cat(), s.type(), s.min(), s.max(), s.step(), s.options(),
                    s.def(), v == null ? s.def() : v, s.jar(), v != null, s.when(), s.name(), s.what(), s.key(),
                    s.group()));
        }
        return new AdminJson.Report(r.giant(), r.name(), r.version(), r.uses(), r.all(), List.copyOf(list));
    }

    /** the preset as Home shows it: "Classic", "Far Frontier", "Far Frontier, changed", "Custom" */
    public String label() {
        if (w.giantsWithValues().isEmpty()) return edited || !preset.equals("Classic") ? preset : "Classic";
        if (preset.equals("Classic") && edited) return "Custom";
        return edited ? preset + ", changed" : preset;
    }

    /** the preset chosen, without ", changed" */
    public String preset() {
        return preset;
    }

    public boolean edited() {
        return edited;
    }

    /** how many settings the draft sets */
    public int count() {
        int n = 0;
        for (String g : w.giantsWithValues()) n += w.values(g).size();
        return n;
    }

    public boolean empty() {
        return w.giantsWithValues().isEmpty();
    }

    /** every value the draft has, as a bundle that puts exactly them into a world (nothing else) */
    public AdminTransfer.Bundle bundle() {
        Map<String, Map<String, JsonElement>> v = new LinkedHashMap<>();
        for (String g : w.giantsWithValues()) v.put(g, w.values(g));
        return new AdminTransfer.Bundle("export", "", "", true, v);
    }

    // what the book's screen asks of it (the same words as the commands)

    private record Found(String giant, String name, @Nullable AdminSetting s, @Nullable String why) {}

    private Found find(String which, String id) {
        if (which.equalsIgnoreCase(AdminCatalog.ALL)) {
            AdminSetting s = AdminCatalog.shared(id);
            if (s != null) for (AdminSetting x : AdminServer.shared(w)) if (x.id().equals(s.id())) s = x;
            return s == null
                    ? new Found(AdminCatalog.ALL, "All giants", null, "There's no all-giants setting called " + id + ".")
                    : new Found(AdminCatalog.ALL, "All giants", s, null);
        }
        Giant g = AdminServer.giant(which);
        AdminJson.Report r = g == null ? null : report(g);
        if (r == null) return new Found(which, which, null, "No giant called " + which + " here.");
        AdminSetting s = r.setting(id);
        if (s == null) for (AdminSetting x : r.settings()) if (x.id().equalsIgnoreCase(id)) s = x;
        if (s == null) return new Found(g.key(), r.name(), null, r.name() + " has no setting called " + id + ".");
        return new Found(g.key(), r.name(), s, null);
    }

    private static String shown(AdminSetting s, JsonElement v) {
        return AdminServer.shown(s, v);
    }

    /** one setting to a value, for the new world */
    public AdminServer.Result set(String which, String id, String text) {
        Found f = find(which, id);
        if (f.why() != null) return AdminServer.Result.fail(f.why());
        AdminSetting s = f.s();
        JsonElement v;
        try {
            v = s.parse(text);
        } catch (AdminSetting.BadValue e) {
            return AdminServer.Result.fail(e.getMessage());
        }
        w.put(f.giant(), s.id(), v);
        edited = true;
        return AdminServer.Result.said(List.of(f.name() + ": " + s.name() + " set to " + shown(s, v)
                + " for the new world."));
    }

    /** a setting, a giant's or every one ("everything") back to the server default */
    public AdminServer.Result reset(String which, @Nullable String id) {
        if (which.equalsIgnoreCase("everything")) {
            int n = count();
            for (String g : w.giantsWithValues()) w.clear(g);
            preset = "Classic";
            edited = false;
            return AdminServer.Result.said(List.of(n == 0 ? "Nothing was set for the new world."
                    : "Every setting for the new world is back to the server default (" + n + ")."));
        }
        if (id == null) {
            String key = which.equalsIgnoreCase(AdminCatalog.ALL) ? AdminCatalog.ALL : keyOf(which);
            int n = w.clear(key);
            if (n > 0) edited = true;
            return AdminServer.Result.said(List.of(n == 0 ? "Nothing was set there."
                    : n + (n == 1 ? " setting" : " settings") + " back to the server default."));
        }
        Found f = find(which, id);
        if (f.why() != null) return AdminServer.Result.fail(f.why());
        if (!w.remove(f.giant(), f.s().id()))
            return AdminServer.Result.said(List.of(f.name() + ": " + f.s().name() + " is already the server default."));
        edited = true;
        return AdminServer.Result.said(List.of(f.name() + ": " + f.s().name() + " back to the server default, "
                + shown(f.s(), f.s().def()) + "."));
    }

    private static String keyOf(String which) {
        Giant g = AdminServer.giant(which);
        return g == null ? which : g.key();
    }

    /** what a preset (built-in or saved) or an import would do to the draft; null with why in the result */
    public AdminTransfer.Plan plan(AdminTransfer.Bundle b, String kind) {
        AdminTransfer.Plan p = AdminTransfer.plan(this, b);
        held = p;
        heldKind = kind;
        return p;
    }

    /** the plan last shown, applied to the draft (the picker and the book's Apply) */
    public AdminServer.Result apply() {
        AdminTransfer.Plan p = held;
        held = null;
        if (p == null) return AdminServer.Result.fail("There's nothing to apply. Pick a preset first.");
        for (AdminTransfer.Step s : p.changes()) {
            if (s.now() == null) w.remove(s.giant(), s.id());
            else w.put(s.giant(), s.id(), s.now());
        }
        if (heldKind.equals("import")) preset = "Imported settings";
        else preset = p.bundle().name().isEmpty() ? p.bundle().id() : p.bundle().name();
        edited = false;
        return AdminServer.Result.said(List.of(AdminTransfer.title(p.bundle()) + ": " + p.changes().size()
                + (p.changes().size() == 1 ? " setting" : " settings") + " changed for the new world."));
    }

    /**
     * Picks a preset outright: the draft becomes exactly that preset (whatever was there before goes), the way the
     * Create World picker does it. Null if there's no such preset.
     */
    public @Nullable AdminServer.Result pick(String name) {
        AdminTransfer.Bundle b = AdminTransfer.preset(name);
        if (b == null) return null;
        for (String g : w.giantsWithValues()) w.clear(g);
        edited = false;
        preset = "Classic";
        plan(b, "preset");
        return apply();
    }
}
