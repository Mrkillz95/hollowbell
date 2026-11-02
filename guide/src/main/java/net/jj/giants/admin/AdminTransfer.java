package net.jj.giants.admin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import net.jj.giants.Giant;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Bundles of setting values (ADMIN_BRIDGE.md, "Bundles"): a preset, or a world's settings exported as text and
 * imported into another. Applying one first shows what it would change (a plan); the change only happens on
 * /giants admin confirm.
 */
public final class AdminTransfer {
    private AdminTransfer() {}

    /** the start of an exported string: the format's version */
    public static final String PREFIX = "GA1-";

    /**
     * A bundle: a name, whether it replaces everything (settings it doesn't name go back to the server default) or
     * only changes what it names, and the values: "all" and each giant's key to id to value.
     */
    public record Bundle(String id, String name, String about, boolean replace,
            Map<String, Map<String, JsonElement>> values) {}

    /**
     * What applying a bundle would do.
     *
     * @param changes     each setting that would change: giant key ("all" too), id, old value (null: the default),
     *                    new value (null: back to the default)
     * @param lines       the preview, in plain words
     * @param creationOnly the changed settings that only take full effect in a new world or on new land
     */
    public record Plan(Bundle bundle, List<Step> changes, List<String> lines, List<String> creationOnly,
            List<String> skipped, List<String> locked) {}

    public record Step(String giant, String id, String name, @Nullable JsonElement old, @Nullable JsonElement now,
            String oldText, String nowText) {}

    // the built-in presets (Part 3 adds the rest: see ADMIN_BRIDGE.md)
    private static final Map<String, String> BUILT_IN = new LinkedHashMap<>();

    static {
        BUILT_IN.put("classic", "{\"admin\":1,\"preset\":\"classic\",\"name\":\"Classic\",\"about\":\"Every giant as"
                + " the server's config files have him: every setting this world changed goes back.\","
                + "\"replace\":true}");
    }

    public static Set<String> presets() {
        return BUILT_IN.keySet();
    }

    /** a built-in preset by its id, or a preset saved on this server by its name; null if there's none */
    public static @Nullable Bundle preset(String id) {
        String json = BUILT_IN.get(id.toLowerCase(java.util.Locale.ROOT));
        if (json != null) return read(json);
        AdminPresets.Saved s = AdminPresets.find(id);
        return s == null ? null : s.bundle();
    }

    /** a bundle from its JSON; null if it isn't one */
    public static @Nullable Bundle read(String json) {
        JsonObject o = AdminJson.object(json);
        if (o == null || !o.has("admin")) return null;
        try {
            if (o.get("admin").getAsInt() > AdminJson.VERSION) return null;
        } catch (RuntimeException e) {
            return null;
        }
        Map<String, Map<String, JsonElement>> values = new LinkedHashMap<>();
        if (o.get("all") instanceof JsonObject a) values.put(AdminCatalog.ALL, map(a));
        if (o.get("giants") instanceof JsonObject g)
            for (var e : g.entrySet())
                if (e.getValue() instanceof JsonObject v && AdminJson.ID.matcher(e.getKey()).matches())
                    values.put(e.getKey(), map(v));
        boolean replace = !(o.get("replace") instanceof com.google.gson.JsonPrimitive p) || !p.isBoolean()
                || p.getAsBoolean();
        String id = AdminJson.str(o, "preset", 32);
        return new Bundle(id.isEmpty() ? "import" : id, AdminJson.str(o, "name", 48), AdminJson.str(o, "about", 240),
                replace, values);
    }

    private static Map<String, JsonElement> map(JsonObject o) {
        Map<String, JsonElement> m = new LinkedHashMap<>();
        for (var e : o.entrySet()) if (AdminJson.ID.matcher(e.getKey()).matches()) m.put(e.getKey(), e.getValue());
        return m;
    }

    /** a bundle's JSON */
    public static String write(Bundle b) {
        JsonObject o = new JsonObject();
        o.addProperty("admin", AdminJson.VERSION);
        if (!b.id().isEmpty() && !b.id().equals("export") && !b.id().equals("import"))
            o.addProperty("preset", b.id());
        if (!b.name().isEmpty()) o.addProperty("name", b.name());
        if (!b.about().isEmpty()) o.addProperty("about", b.about());
        o.addProperty("replace", b.replace());
        JsonObject giants = new JsonObject();
        for (var e : b.values().entrySet()) {
            JsonObject v = new JsonObject();
            for (var s : e.getValue().entrySet()) v.add(s.getKey(), s.getValue());
            if (e.getKey().equals(AdminCatalog.ALL)) o.add("all", v);
            else giants.add(e.getKey(), v);
        }
        o.add("giants", giants);
        return o.toString();
    }

    /** this world's settings as one bundle (every giant's, installed or not, and the all-giants ones) */
    public static Bundle current(MinecraftServer server) {
        AdminWorld w = AdminWorld.get(server);
        Map<String, Map<String, JsonElement>> v = new LinkedHashMap<>();
        for (String g : w.giantsWithValues()) v.put(g, w.values(g));
        return new Bundle("export", "", "", true, v);
    }

    /** a bundle as one line of text to copy: GA1- and the JSON, squeezed and in URL-safe Base64 */
    public static String encode(Bundle b) {
        byte[] raw = write(b).getBytes(StandardCharsets.UTF_8);
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        d.setInput(raw);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (!d.finished()) out.write(buf, 0, d.deflate(buf));
        d.end();
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
    }

    /** an exported string (or the bundle's JSON itself) back to a bundle; null if it isn't one */
    public static @Nullable Bundle decode(String text) {
        String t = text == null ? "" : text.trim();
        if (t.startsWith("{")) return read(t);
        if (!t.startsWith(PREFIX)) return null;
        try {
            byte[] packed = Base64.getUrlDecoder().decode(t.substring(PREFIX.length()));
            Inflater i = new Inflater();
            i.setInput(packed);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            while (!i.finished()) {
                int n = i.inflate(buf);
                if (n == 0 && (i.needsInput() || i.needsDictionary())) break;
                out.write(buf, 0, n);
                if (out.size() > 1 << 20) break; // (nothing real is this big)
            }
            i.end();
            return read(out.toString(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | DataFormatException e) {
            return null;
        }
    }

    /** what a bundle would change in this world, without changing anything */
    public static Plan plan(MinecraftServer server, Bundle b) {
        AdminWorld w = AdminWorld.get(server);
        List<Step> steps = new ArrayList<>();
        List<String> lines = new ArrayList<>(), skipped = new ArrayList<>(), creation = new ArrayList<>(),
                locked = new ArrayList<>();
        // the all-giants ones
        Map<String, JsonElement> wantAll = b.values().getOrDefault(AdminCatalog.ALL, Map.of());
        for (AdminSetting s : AdminServer.shared(w))
            consider(w, AdminCatalog.ALL, "All giants", s, wantAll, b.replace(), steps, creation, locked);
        for (String id : wantAll.keySet())
            if (AdminCatalog.shared(id) == null) skipped.add("all giants " + id);
        // each giant here that speaks the protocol
        Set<String> seen = new LinkedHashSet<>();
        for (Giant g : AdminServer.giants()) {
            AdminJson.Report r = AdminServer.report(server, g);
            if (r == null) continue;
            seen.add(g.key());
            Map<String, JsonElement> want = b.values().getOrDefault(g.key(), Map.of());
            for (AdminSetting s : r.settings())
                consider(w, g.key(), r.name(), s, want, b.replace(), steps, creation, locked);
            for (String id : want.keySet()) if (r.setting(id) == null) skipped.add(r.name() + " " + id);
        }
        for (String g : b.values().keySet())
            if (!g.equals(AdminCatalog.ALL) && !seen.contains(g)) skipped.add(g + " (not here)");
        for (Step s : steps)
            lines.add("  " + (s.giant().equals(AdminCatalog.ALL) ? "All giants" : nameFor(server, s.giant())) + " "
                    + s.name() + ": " + s.oldText() + " → " + s.nowText());
        String title = title(b);
        if (steps.isEmpty()) lines.add(0, title + " changes nothing in this world.");
        else lines.add(0, title + " would change " + steps.size() + (steps.size() == 1 ? " setting:" : " settings:"));
        if (!creation.isEmpty())
            lines.add("World creation: these only take full effect in a new world or on land not made yet: "
                    + String.join(", ", creation) + ".");
        if (!locked.isEmpty()) lines.add("Locked, so left as they are: " + String.join(", ", locked) + ".");
        if (!skipped.isEmpty()) lines.add("Left out (not here or not known): " + String.join(", ", skipped) + ".");
        if (!steps.isEmpty()) lines.add("/giants admin confirm to apply it (within five minutes).");
        return new Plan(b, steps, lines, creation, skipped, locked);
    }

    /** what a bundle is called in a preview */
    public static String title(Bundle b) {
        if (!b.name().isEmpty()) return b.name();
        return switch (b.id()) {
            case "import", "export" -> "The import";
            case "snapshot" -> "The snapshot";
            default -> b.id();
        };
    }

    private static String nameFor(MinecraftServer server, String key) {
        Giant g = AdminServer.giant(key);
        return g == null ? key : AdminServer.nameOf(g);
    }

    private static void consider(AdminWorld w, String giant, String giantName, AdminSetting s,
            Map<String, JsonElement> want, boolean replace, List<Step> steps, List<String> creation,
            List<String> locked) {
        JsonElement old = s.set() ? s.value() : null;
        AdminWorld.Lock lock = w.lock(giant, s.id());
        if (lock != null) {
            // (a locked one is never changed by a bundle; it is named only if the bundle would have changed it)
            boolean wants = want.containsKey(s.id()) ? !AdminSetting.same(want.get(s.id()), s.value())
                    : replace && old != null;
            if (wants) locked.add(giantName + " " + s.name() + " (" + lock.who() + ")");
            return;
        }
        JsonElement now;
        if (want.containsKey(s.id())) {
            JsonElement v = want.get(s.id());
            if (!s.accepts(v)) return; // (one it can't take is left as it is)
            try {
                now = s.parse(AdminSetting.text(v));
            } catch (AdminSetting.BadValue e) {
                return;
            }
        } else if (replace) now = null;
        else return;
        if (old == null && now == null) return;
        if (old != null && now != null && AdminSetting.same(old, now)) return;
        String oldText = AdminServer.shown(s, old == null ? s.def() : old) + (old == null ? " (default)" : "");
        String nowText = AdminServer.shown(s, now == null ? s.def() : now) + (now == null ? " (default)" : "");
        steps.add(new Step(giant, s.id(), s.name(), old, now, oldText, nowText));
        if (s.creationOnly()) creation.add(giantName + " " + s.name());
    }

    /** does a plan: every change kept for the world, logged as one, and sent to the giants */
    public static int apply(MinecraftServer server, String who, Plan p) {
        AdminWorld w = AdminWorld.get(server);
        // (locked since the preview: still left alone)
        List<Step> steps = p.changes().stream().filter(s -> w.lock(s.giant(), s.id()) == null).toList();
        if (steps.isEmpty()) return 0;
        String id = p.bundle().id();
        boolean imported = id.equals("import") || id.equals("export"), snap = id.equals("snapshot");
        // a snapshot first (not before putting a snapshot back: that one is undone from the change log)
        if (!snap) w.snapshot(who, imported ? "Before imported settings" : "Before the preset " + title(p.bundle()));
        Map<String, Map<String, JsonElement>> before = new LinkedHashMap<>();
        for (Step s : steps) before.computeIfAbsent(s.giant(), k -> new HashMap<>()).put(s.id(), s.old());
        for (Step s : steps) {
            if (s.now() == null) w.remove(s.giant(), s.id());
            else w.put(s.giant(), s.id(), s.now());
        }
        String what = imported ? "import" : snap ? "snapshot" : "preset:" + id;
        String name = snap ? p.bundle().name()
                : p.bundle().name().isEmpty() ? (imported ? "imported settings" : id)
                : "preset " + p.bundle().name();
        w.log(new AdminWorld.Change(System.currentTimeMillis(), who, "everything", what, name, "",
                steps.size() + (steps.size() == 1 ? " change" : " changes"), AdminWorld.Change.undo(before)));
        AdminServer.push(server, w);
        return steps.size();
    }

    /** a world's values as a bundle that puts exactly them back (a snapshot) */
    public static Bundle snapshotBundle(AdminWorld.Snapshot s) {
        String when = new java.text.SimpleDateFormat("MMM d HH:mm", java.util.Locale.ROOT)
                .format(new java.util.Date(s.time()));
        return new Bundle("snapshot", "snapshot from " + when, s.why(), true, s.values());
    }

    // the danger rating if a plan were applied (the Presets page's preview)

    /** the rating now, and as it would be with the plan applied */
    public static AdminInsight.Danger[] dangerBeforeAfter(MinecraftServer server, Plan p) {
        AdminWorld w = AdminWorld.get(server);
        Map<String, Map<String, JsonElement>> after = new HashMap<>();
        for (Step s : p.changes())
            after.computeIfAbsent(s.giant(), k -> new HashMap<>()).put(s.id(), s.now());
        List<AdminInsight.View> now = new ArrayList<>(), then = new ArrayList<>();
        for (Giant g : AdminServer.giants()) {
            AdminJson.Report r = AdminServer.report(server, g);
            if (r == null) continue;
            now.add(AdminInsight.view(r));
            Map<String, JsonElement> ch = after.getOrDefault(g.key(), Map.of());
            List<AdminSetting> list = new ArrayList<>();
            for (AdminSetting s : r.settings()) {
                if (!ch.containsKey(s.id())) {
                    list.add(s);
                    continue;
                }
                JsonElement v = ch.get(s.id());
                list.add(new AdminSetting(s.giant(), s.id(), s.cat(), s.type(), s.min(), s.max(), s.step(),
                        s.options(), s.def(), v == null ? s.def() : v, s.jar(), v != null, s.when(), s.name(),
                        s.what(), s.key(), s.group()));
            }
            then.add(AdminInsight.view(new AdminJson.Report(r.giant(), r.name(), r.version(), r.uses(), r.all(),
                    list)));
        }
        Map<String, Double> allNow = new HashMap<>(), allThen = new HashMap<>();
        Map<String, JsonElement> chAll = after.getOrDefault(AdminCatalog.ALL, Map.of());
        for (AdminSetting s : AdminServer.shared(w)) {
            allNow.put(s.id(), s.value().getAsDouble());
            if (chAll.containsKey(s.id())) {
                JsonElement v = chAll.get(s.id());
                allThen.put(s.id(), v == null ? s.def().getAsDouble() : v.getAsDouble());
            } else allThen.put(s.id(), s.value().getAsDouble());
        }
        return new AdminInsight.Danger[] {AdminInsight.danger(now, allNow), AdminInsight.danger(then, allThen)};
    }

    // waiting for /giants admin confirm: one plan per person, for five minutes

    private record Pending(Plan plan, long until) {}

    private static final Map<String, Pending> PENDING = new HashMap<>();

    public static void hold(String who, Plan p) {
        PENDING.put(who, new Pending(p, System.currentTimeMillis() + 5 * 60_000L));
    }

    public static @Nullable Plan take(String who) {
        Pending p = PENDING.remove(who);
        return p == null || p.until() < System.currentTimeMillis() ? null : p.plan();
    }

    public static void forget() {
        PENDING.clear();
    }
}
