package net.jj.giants.admin;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.GiantsGuideMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The admin settings, the server's half (ADMIN_BRIDGE.md): keeps each world's values (AdminWorld), sends every giant
 * that speaks the protocol its own and the all-giants ones, and does what /giants admin (and later the admin book)
 * asks: get, set, reset, presets, export and import. Every change is logged with the world and told to the other
 * operators online.
 */
public final class AdminServer {
    private AdminServer() {}

    public static final String LIST = "admin", SET = "adminset", DEFAULT = "admindefault";

    /** the giants to ask: null = every installed one (the tests put their own in) */
    private static @Nullable List<Giant> override;

    public static void register() {
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> AdminCommand.register(d));
        // the world's values reach the giants as soon as the overworld is there, before any of its land is made
        // (a setting for new land must be in place for the first chunks): an early phase of the world's loading
        ResourceLocation early = ResourceLocation.fromNamespaceAndPath(GiantsGuideMod.ID, "admin_first");
        ServerWorldEvents.LOAD.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        ServerWorldEvents.LOAD.register(early, (server, level) -> {
            if (level.dimension() == Level.OVERWORLD) {
                // (a brand-new world: the Create World choice or new_world.txt goes in first, Part 3)
                AdminCreation.atLoad(server, level);
                push(server, AdminWorld.get(level));
                // (Part 5) and the per-player rules
                net.jj.giants.players.Players.atLoad(server, level);
            }
        });
        ServerLifecycleEvents.SERVER_STARTING.register(s -> AdminPresets.forget());
        // the settings sync file (config/jj_giants_admin/sync.txt) as the server defaults, when it changed (Part 2B)
        ServerLifecycleEvents.SERVER_STARTED.register(AdminSync::onStart);
        ServerLifecycleEvents.SERVER_STARTED.register(AdminServer::logStart);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> AdminTransfer.forget());
    }

    /** the tests' own giants (null: back to the installed ones) */
    public static void useGiants(@Nullable List<Giant> here) {
        override = here == null ? null : List.copyOf(here);
    }

    public static List<Giant> giants() {
        return override != null ? override : Giants.installed();
    }

    /** the names /giants admin takes for a giant: the guide's key, and the name /giants knows him by */
    public static @Nullable Giant giant(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        String key = switch (n) {
            case "willow", "lantern_willow" -> "lanternwillow";
            case "mountain", "mountain_breathes" -> "pitchgut";
            case "fire_ice_cerberus" -> "cerberus";
            default -> n;
        };
        for (Giant g : giants()) if (g.key().equals(key)) return g;
        // (a giant's own mod id: what his commands send over GuideBridge)
        for (Giant g : giants()) if (g.modId().equals(n)) return g;
        return null;
    }

    /** a giant's plain name ("Lantern Willow"), from his key */
    public static String nameOf(Giant g) {
        return switch (g.key()) {
            case "lanternwillow" -> "Lantern Willow";
            default -> Character.toUpperCase(g.key().charAt(0)) + g.key().substring(1);
        };
    }

    // the bridge

    /** one call to a giant's bridge; null if it isn't there, doesn't know the action, or fails */
    public static @Nullable List<String> call(@Nullable MinecraftServer server, Giant g, String action, String arg) {
        try {
            Class<?> c = Class.forName(g.bridge(), true, AdminServer.class.getClassLoader());
            Method m = c.getMethod("giants", MinecraftServer.class, String.class, String.class);
            if (!Modifier.isStatic(m.getModifiers())) return null;
            Object r = m.invoke(null, server, action, arg);
            if (!(r instanceof List<?> l)) return null;
            List<String> out = new ArrayList<>();
            for (Object o : l) if (o != null && out.size() < AdminJson.MOST_SETTINGS + 2) out.add(o.toString());
            return out;
        } catch (Throwable t) {
            // a call into another giant's mod: an old one, a missing one, or a crash in it
            GiantsGuideMod.LOG.debug("No {} from {}: {}", action, g.key(), t.toString());
            return null;
        }
    }

    /** what a giant's mod says it has now, with its values; null for one that doesn't speak the protocol */
    public static @Nullable AdminJson.Report report(@Nullable MinecraftServer server, Giant g) {
        return AdminJson.read(g.key(), nameOf(g), call(server, g, LIST, String.valueOf(AdminJson.VERSION)));
    }

    /** the all-giants settings, with this world's values in */
    public static List<AdminSetting> shared(AdminWorld w) {
        Map<String, JsonElement> v = w.values(AdminCatalog.ALL);
        List<AdminSetting> out = new ArrayList<>();
        for (AdminSetting s : AdminCatalog.shared()) {
            JsonElement x = v.get(s.id());
            out.add(x == null ? s : withValue(s, x, true));
        }
        return out;
    }

    private static AdminSetting withValue(AdminSetting s, JsonElement v, boolean set) {
        return new AdminSetting(s.giant(), s.id(), s.cat(), s.type(), s.min(), s.max(), s.step(), s.options(),
                s.def(), v, s.jar(), set, s.when(), s.name(), s.what(), s.key(), s.group());
    }

    /** sends every giant its world values; how many took them */
    public static int push(MinecraftServer server, AdminWorld w) {
        int n = 0;
        for (Giant g : giants()) if (push(server, w, g) != null) n++;
        return n;
    }

    /** sends one giant its values; what it answered (null: it doesn't speak the protocol) */
    public static @Nullable List<String> push(MinecraftServer server, AdminWorld w, Giant g) {
        // (Part 6) quiet mode turns the giants' chat switches off
        // (Part 8) the systems: their server defaults, and the scaling, moon and season factors on the multipliers
        // (Part 9) Random chaos: the day's resting moves on what he is sent (never on the world's own values)
        List<String> r = call(server, g, SET, AdminJson.setArg(
                net.jj.giants.systems.Systems.forGiant(server, g, w.values(g.key())),
                net.jj.giants.systems.Systems.forGiants(server,
                        net.jj.giants.tools.Tools.forGiants(w.values(AdminCatalog.ALL)))));
        if (r != null && r.size() > 1)
            GiantsGuideMod.LOG.warn("Admin settings: {} didn't take {}", g.key(), r.subList(1, r.size()));
        return r;
    }

    private static void logStart(MinecraftServer server) {
        AdminWorld w = AdminWorld.get(server);
        int n = 0;
        for (String g : w.giantsWithValues()) n += w.values(g).size();
        int speak = 0;
        for (Giant g : giants()) if (report(server, g) != null) speak++;
        GiantsGuideMod.LOG.info(
                "Giants admin settings: {} of {} giants speak it, {} set for this world", speak, giants().size(), n);
    }

    // what the commands (and later the book) do

    /**
     * What one request did.
     *
     * @param ok       it went through
     * @param lines    what to tell whoever asked
     * @param announce what to tell the other operators online (empty: nothing changed)
     */
    public record Result(boolean ok, List<String> lines, List<String> announce) {
        public static Result fail(String line) {
            return new Result(false, List.of(line), List.of());
        }

        public static Result said(List<String> lines) {
            return new Result(true, lines, List.of());
        }
    }

    /** a setting someone named, with the giant it belongs to: or why there's none */
    record Found(@Nullable Giant giant, String name, @Nullable AdminJson.Report report, @Nullable AdminSetting s,
            @Nullable String why) {}

    /** the giant named in a command ("all" for the all-giants settings), and his report */
    static Found target(MinecraftServer server, String which) {
        if (which.equalsIgnoreCase(AdminCatalog.ALL)) return new Found(null, "All giants", null, null, null);
        Giant g = giant(which);
        if (g == null) {
            Giant known = null;
            for (Giant x : Giants.ALL) if (x.key().equalsIgnoreCase(which)) known = x;
            if (known != null) return new Found(null, "", null, null, nameOf(known) + " isn't installed.");
            List<String> names = new ArrayList<>();
            for (Giant x : giants()) names.add(x.key());
            names.add("all");
            return new Found(null, "", null, null,
                    "No giant called " + which + ". Try one of: " + String.join(", ", names) + ".");
        }
        AdminJson.Report r = report(server, g);
        if (r == null) return new Found(g, nameOf(g), null, null, nameOf(g) + ": this version has no admin settings.");
        return new Found(g, r.name(), r, null, null);
    }

    /** the named setting of the giant found */
    static Found setting(MinecraftServer server, Found f, String id) {
        if (f.why() != null) return f;
        if (f.giant() == null) {
            AdminSetting s = AdminCatalog.shared(id);
            if (s == null) {
                List<String> ids = new ArrayList<>();
                for (AdminSetting x : AdminCatalog.shared()) ids.add(x.id().substring(4));
                return new Found(null, f.name(), null, null,
                        "There's no all-giants setting called " + id + ". Try one of: " + String.join(", ", ids)
                                + ".");
            }
            for (AdminSetting x : shared(AdminWorld.get(server))) if (x.id().equals(s.id())) s = x;
            return new Found(null, f.name(), null, s, null);
        }
        AdminSetting s = f.report().setting(id);
        if (s == null)
            for (AdminSetting x : f.report().settings()) if (x.id().equalsIgnoreCase(id)) s = x;
        if (s == null)
            return new Found(f.giant(), f.name(), f.report(), null,
                    f.name() + " has no setting called " + id + ". /giants admin list " + f.giant().key()
                            + " shows them.");
        return new Found(f.giant(), f.name(), f.report(), s, null);
    }

    private static String key(Found f) {
        return f.giant() == null ? AdminCatalog.ALL : f.giant().key();
    }

    /** a value the way a list shows it: x2 for the all-giants ones */
    static String shown(AdminSetting s, JsonElement v) {
        return (AdminCatalog.multiplier(s) ? "x" : "") + AdminSetting.text(v);
    }

    /** the id as a command takes it ("health" for all.health) */
    static String commandId(AdminSetting s) {
        return s.giant().equals(AdminCatalog.ALL) && s.id().startsWith("all.") ? s.id().substring(4) : s.id();
    }

    /**
     * Why whoever this is can't change a locked setting (Part 2B, locks): only the admin who locked it may, until it is
     * unlocked. Null when it isn't locked, or it is theirs.
     */
    static @Nullable String lockedFor(AdminWorld w, String giant, String name, AdminSetting s, String who) {
        AdminWorld.Lock l = w.lock(giant, s.id());
        if (l == null || l.who().equals(who)) return null;
        return name + ": " + s.name() + " is locked by " + l.who() + ". /giants admin unlock " + giant + " "
                + commandId(s) + " first.";
    }

    /** /giants admin set: one setting to a value, for this world */
    public static Result set(MinecraftServer server, String who, String which, String id, String text) {
        Found f = setting(server, target(server, which), id);
        if (f.why() != null) return Result.fail(f.why());
        AdminSetting s = f.s();
        JsonElement v;
        try {
            v = s.parse(text);
        } catch (AdminSetting.BadValue e) {
            return Result.fail(e.getMessage());
        }
        AdminWorld w = AdminWorld.get(server);
        String locked = lockedFor(w, key(f), f.name(), s, who);
        if (locked != null) return Result.fail(locked);
        JsonElement was = s.value();
        w.put(key(f), s.id(), v);
        w.log(new AdminWorld.Change(System.currentTimeMillis(), who, key(f), s.id(), s.name(), shown(s, was),
                shown(s, v), AdminWorld.Change.undo(key(f), s.id(), s.set() ? was : null)));
        List<String> lines = new ArrayList<>();
        if (f.giant() == null) push(server, w);
        else {
            List<String> r = push(server, w, f.giant());
            AdminJson.Report now = report(server, f.giant());
            AdminSetting after = now == null ? null : now.setting(s.id());
            if (r == null || after == null || !AdminSetting.same(after.value(), v))
                lines.add(f.name() + ": kept for this world, but his mod didn't take it yet.");
        }
        lines.add(0, f.name() + ": " + s.name() + " set to " + shown(s, v) + " in this world (was " + shown(s, was)
                + ")." + (f.giant() == null ? " On top of each giant's own." : ""));
        if (!s.whenNote().isEmpty()) lines.add(s.whenNote());
        return new Result(true, lines, List.of(who + " set " + f.name() + " " + s.name() + " to " + shown(s, v) + "."));
    }

    /**
     * /giants admin set ... allworlds: the server default (the giant's config file), as his own commands with allworlds
     * change it. The world's own values are left as they are (if it keeps its own for this one, that still wins, and
     * the reply says so). Logged and told like any change.
     */
    public static Result setDefault(MinecraftServer server, String who, String which, String id, String text) {
        Found f = setting(server, target(server, which), id);
        if (f.why() != null) return Result.fail(f.why());
        AdminSetting s = f.s();
        // (Part 8) the systems keep a server default of their own (config/jj_giants_admin/systems.json)
        if (f.giant() == null && AdminCatalog.systems(s)) return systemsDefault(server, who, f, s, text);
        if (f.giant() == null)
            return Result.fail(net.jj.giants.minigames.MgCatalog.ours(s.id())
                    ? "The minigame settings are only kept for each world: there's no server default to change."
                            + " Leave out allworlds."
                    : AdminCatalog.server(s)
                    ? "The server tools settings are only kept for each world: there's no server default to change."
                            + " Leave out allworlds."
                    : AdminCatalog.player(s)
                    ? "The player settings are only kept for each world: there's no server default to change. Leave"
                            + " out allworlds."
                    : AdminCatalog.multiplier(s)
                    ? "The all-giants multipliers are only kept for each world: there's no server default to"
                            + " change. Leave out allworlds."
                    : "The all-giants settings are only kept for each world: there's no server default to change."
                            + " Leave out allworlds.");
        JsonElement v;
        try {
            v = s.parse(text);
        } catch (AdminSetting.BadValue e) {
            return Result.fail(e.getMessage());
        }
        String locked = lockedFor(AdminWorld.get(server), f.giant().key(), f.name(), s, who);
        if (locked != null) return Result.fail(locked);
        String why = writeDefault(server, f.giant(), f.name(), s, v);
        if (why != null) return Result.fail(why);
        AdminWorld w = AdminWorld.get(server);
        w.log(new AdminWorld.Change(System.currentTimeMillis(), who, f.giant().key(), s.id(),
                s.name() + " (server default)", shown(s, s.def()), shown(s, v), ""));
        String line = f.name() + ": " + s.name() + " set to " + shown(s, v) + " for all worlds (server default; was "
                + shown(s, s.def()) + ").";
        if (s.set())
            line += " This world keeps its own: " + shown(s, s.value()) + " (/giants admin reset " + f.giant().key()
                    + " " + s.id() + ").";
        List<String> lines = new ArrayList<>(List.of(line));
        if (!s.whenNote().isEmpty()) lines.add(s.whenNote());
        return new Result(true, lines, List.of(who + " set " + f.name() + " " + s.name() + " to " + shown(s, v)
                + " for all worlds (server default)."));
    }

    /** (Part 8) a system's server default: the systems file, logged and told like any change */
    private static Result systemsDefault(MinecraftServer server, String who, Found f, AdminSetting s, String text) {
        JsonElement v;
        try {
            v = s.parse(text);
        } catch (AdminSetting.BadValue e) {
            return Result.fail(e.getMessage());
        }
        AdminWorld w = AdminWorld.get(server);
        String locked = lockedFor(w, AdminCatalog.ALL, f.name(), s, who);
        if (locked != null) return Result.fail(locked);
        String why = net.jj.giants.systems.SystemsCatalog.setDefault(s, v);
        if (why != null) return Result.fail(why);
        w.log(new AdminWorld.Change(System.currentTimeMillis(), who, AdminCatalog.ALL, s.id(),
                s.name() + " (server default)", shown(s, s.def()), shown(s, v), ""));
        String line = f.name() + ": " + s.name() + " set to " + shown(s, v) + " for all worlds (server default; was "
                + shown(s, s.def()) + ").";
        if (s.set())
            line += " This world keeps its own: " + shown(s, s.value()) + " (/giants admin reset all "
                    + commandId(s) + ").";
        else push(server, w);
        return new Result(true, List.of(line), List.of(who + " set " + f.name() + " " + s.name() + " to "
                + shown(s, v) + " for all worlds (server default)."));
    }

    /**
     * A giant's server default (his config file) to a checked value, through his bridge (admindefault); null when it
     * took it, else why not. Nothing is logged or told here (setDefault and the settings sync do that).
     */
    static @Nullable String writeDefault(MinecraftServer server, Giant g, String name, AdminSetting s, JsonElement v) {
        com.google.gson.JsonObject arg = new com.google.gson.JsonObject();
        arg.addProperty("admin", AdminJson.VERSION);
        arg.addProperty("id", s.id());
        arg.add("value", v);
        List<String> r = call(server, g, DEFAULT, arg.toString());
        if (r == null || r.isEmpty())
            return name + ": this version can't change its server default from here. Its own command with allworlds"
                    + " does.";
        if (!r.get(0).equals("ok"))
            return String.join(" ", r).contains("world only")
                    ? name + ": " + s.name() + " is only kept for each world; it has no server default."
                    : name + ": his mod didn't take " + shown(s, v) + " for " + s.name() + ".";
        return null;
    }

    /**
     * GuideBridge "adminworld": one of a giant's own commands (or a switch in his book) changing a setting for this
     * world. The same as /giants admin set by whoever ran it: checked, kept, logged, told to the other operators.
     */
    public static @Nullable List<String> fromGiant(MinecraftServer server, String arg) {
        String giant, id, by;
        JsonElement value;
        try {
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(arg).getAsJsonObject();
            if (o.get("admin").getAsInt() > AdminJson.VERSION) return null;
            giant = o.get("giant").getAsString();
            id = o.get("id").getAsString();
            value = o.get("value");
            by = o.has("by") ? o.get("by").getAsString() : "-";
            if (value == null || value.isJsonNull()) return null;
        } catch (RuntimeException e) {
            return null;
        }
        ServerPlayer player = null;
        try {
            player = server.getPlayerList().getPlayer(java.util.UUID.fromString(by));
        } catch (IllegalArgumentException e) {
            // "-": the server's console or a command block
        }
        String who = player != null ? player.getGameProfile().getName() : "Server";
        Giant g = giant(giant);
        Result r = set(server, who, g != null ? g.key() : giant, id, AdminSetting.text(value));
        if (!r.ok()) return List.of("no", r.lines().isEmpty() ? "" : r.lines().get(0));
        announce(server, player, r.announce());
        List<String> out = new ArrayList<>();
        out.add("ok");
        out.addAll(r.lines());
        return out;
    }

    /** /giants admin get: one setting, or what this world has set for a giant (or for all of them) */
    public static Result get(MinecraftServer server, String which, @Nullable String id) {
        Found t = target(server, which);
        if (t.why() != null) return Result.fail(t.why());
        if (id != null) {
            Found f = setting(server, t, id);
            if (f.why() != null) return Result.fail(f.why());
            AdminSetting s = f.s();
            String line = f.name() + ": " + s.name() + " is " + shown(s, s.value()) + " (server default "
                    + shown(s, s.def()) + (s.set() ? ", set for this world" : "") + ").";
            List<String> lines = new ArrayList<>(List.of(line));
            if (!s.whenNote().isEmpty()) lines.add(s.whenNote());
            return Result.said(lines);
        }
        List<AdminSetting> list = t.giant() == null ? shared(AdminWorld.get(server)) : t.report().settings();
        List<String> lines = new ArrayList<>();
        for (AdminSetting s : list)
            if (s.set())
                lines.add("  " + s.name() + " (" + s.id() + "): " + shown(s, s.value()) + ", server default "
                        + shown(s, s.def()) + ".");
        if (lines.isEmpty())
            return Result.said(
                    List.of(t.name() + ": nothing set for this world; every setting is the server default."));
        lines.add(0, t.name() + ": " + lines.size() + (lines.size() == 1 ? " setting is" : " settings are")
                + " set for this world:");
        return Result.said(lines);
    }

    /** /giants admin list: every setting a giant has (by category), or every one in one category */
    public static Result list(MinecraftServer server, String which, @Nullable String cat) {
        Found t = target(server, which);
        if (t.why() != null) return Result.fail(t.why());
        List<AdminSetting> list = t.giant() == null ? shared(AdminWorld.get(server)) : t.report().settings();
        if (cat != null && !AdminSetting.CATEGORIES.contains(cat.toLowerCase(Locale.ROOT)))
            return Result.fail("No category called " + cat + ". Try one of: "
                    + String.join(", ", AdminSetting.CATEGORIES) + ".");
        String only = cat == null ? null : cat.toLowerCase(Locale.ROOT);
        List<String> lines = new ArrayList<>();
        Map<String, Integer> grouped = new LinkedHashMap<>();
        for (String c : AdminSetting.CATEGORIES) {
            if (only != null && !only.equals(c)) continue;
            boolean head = false;
            for (AdminSetting s : list) {
                if (!s.cat().equals(c)) continue;
                // (one line per move's on/wait/wind would be a hundred lines: only when the moves are asked for)
                if (only == null && !s.group().isEmpty()) {
                    grouped.merge(c, 1, Integer::sum);
                    continue;
                }
                if (!head) {
                    lines.add(c + ":");
                    head = true;
                }
                lines.add("  " + s.id() + " = " + shown(s, s.value()) + (s.set() ? "*" : "") + " (" + s.rangeText()
                        + ") " + s.name() + (s.whenLabel().isEmpty() ? "" : " [" + s.whenLabel() + "]"));
            }
        }
        for (var e : grouped.entrySet())
            lines.add(e.getKey() + ": " + e.getValue() + " more (each move's on, wait and wind): /giants admin list "
                    + key(t) + " " + e.getKey());
        if (lines.isEmpty()) return Result.said(List.of(t.name() + ": no settings in " + only + "."));
        lines.add(0, t.name() + ": " + list.size() + " settings (* = set for this world):");
        return Result.said(lines);
    }

    /** /giants admin reset: one setting, one giant's, the all-giants ones, or "everything" */
    public static Result reset(MinecraftServer server, String who, String which, @Nullable String id) {
        AdminWorld w = AdminWorld.get(server);
        long now = System.currentTimeMillis();
        if (which.equalsIgnoreCase("everything")) {
            // (Part 6) a modpack that keeps its preset
            String pack = net.jj.giants.tools.PackMode.refusal();
            if (pack != null) return Result.fail(pack);
            Map<String, Map<String, JsonElement>> before = new LinkedHashMap<>();
            int n = 0, kept = 0;
            for (String g : w.giantsWithValues()) {
                Map<String, JsonElement> m = new LinkedHashMap<>();
                for (var e : w.values(g).entrySet())
                    if (w.lock(g, e.getKey()) == null) m.put(e.getKey(), e.getValue());
                    else kept++;
                if (!m.isEmpty()) before.put(g, m);
                n += m.size();
            }
            if (n == 0)
                return Result.said(List.of(kept > 0 ? "Only locked settings are set for this world; they stay."
                        : "Nothing was set for this world."));
            // (a snapshot first, so it can all be put back in one go)
            w.snapshot(who, "Before every setting went back to the server defaults");
            for (var e : before.entrySet()) for (String i : e.getValue().keySet()) w.remove(e.getKey(), i);
            w.log(new AdminWorld.Change(now, who, "everything", "*", "every setting", n + " set", "-",
                    AdminWorld.Change.undo(before)));
            push(server, w);
            List<String> lines = new ArrayList<>(
                    List.of("Every giant's settings for this world are back to the server defaults (" + n + ")."));
            if (kept > 0) lines.add(kept + (kept == 1 ? " locked setting stays" : " locked settings stay") + ".");
            return new Result(true, lines, List.of(who + " put every giant's settings back to the server defaults."));
        }
        Found t = target(server, which);
        if (t.why() != null && !(t.giant() != null && id == null)) return Result.fail(t.why());
        String k = t.giant() == null ? AdminCatalog.ALL : t.giant().key();
        if (id == null) {
            Map<String, JsonElement> before = new LinkedHashMap<>();
            int kept = 0;
            for (var e : w.values(k).entrySet())
                if (w.lock(k, e.getKey()) == null) before.put(e.getKey(), e.getValue());
                else kept++;
            int n = before.size();
            if (n == 0)
                return Result.said(List.of(t.name() + (kept > 0 ? ": only locked settings are set; they stay."
                        : ": nothing was set for this world.")));
            for (String i : before.keySet()) w.remove(k, i);
            w.log(new AdminWorld.Change(now, who, k, "*", "every setting", n + " set", "-",
                    AdminWorld.Change.undo(Map.of(k, before))));
            push(server, w);
            String line = t.giant() == null
                    ? "All giants: the shared multipliers are back to x1."
                    : t.name() + ": " + n + (n == 1 ? " setting" : " settings") + " back to the server defaults.";
            if (kept > 0) line += " " + kept + (kept == 1 ? " locked one stays." : " locked ones stay.");
            String told = t.giant() == null
                    ? who + " put the all-giants multipliers back to x1."
                    : who + " put " + t.name() + "'s settings back to the server defaults.";
            return new Result(true, List.of(line), List.of(told));
        }
        Found f = setting(server, t, id);
        if (f.why() != null) return Result.fail(f.why());
        AdminSetting s = f.s();
        String locked = lockedFor(w, k, f.name(), s, who);
        if (locked != null) return Result.fail(locked);
        if (!w.remove(k, s.id()))
            return Result.said(List.of(f.name() + ": " + s.name() + " is already the server default, "
                    + shown(s, s.def()) + "."));
        w.log(new AdminWorld.Change(now, who, k, s.id(), s.name(), shown(s, s.value()), shown(s, s.def()),
                AdminWorld.Change.undo(k, s.id(), s.value())));
        if (f.giant() == null) push(server, w);
        else push(server, w, f.giant());
        return new Result(true,
                List.of(f.name() + ": " + s.name() + " back to the server default, " + shown(s, s.def()) + "."),
                List.of(who + " put " + f.name() + " " + s.name() + " back to " + shown(s, s.def()) + "."));
    }

    // locks (Part 2B)

    /**
     * /giants admin lock: the setting keeps the value it has now for this world (made the world's own if it wasn't),
     * and nothing but its locker changes it: not presets, imports, snapshots, undo or other admins, until an admin
     * unlocks it.
     */
    public static Result lock(MinecraftServer server, String who, String whoId, String which, String id) {
        Found f = setting(server, target(server, which), id);
        if (f.why() != null) return Result.fail(f.why());
        AdminSetting s = f.s();
        String k = key(f);
        AdminWorld w = AdminWorld.get(server);
        AdminWorld.Lock l = w.lock(k, s.id());
        if (l != null) return Result.said(List.of(f.name() + ": " + s.name() + " is already locked by " + l.who() + "."));
        // (pinned: kept for the world, so a change to the server default can't move it either)
        if (!s.set()) w.put(k, s.id(), s.value());
        w.lock(k, s.id(), new AdminWorld.Lock(who, whoId, System.currentTimeMillis()));
        w.log(new AdminWorld.Change(System.currentTimeMillis(), who, k, "lock:" + s.id(), s.name(), "",
                "at " + shown(s, s.value()), ""));
        if (!s.set()) {
            if (f.giant() == null) push(server, w);
            else push(server, w, f.giant());
        }
        return new Result(true,
                List.of(f.name() + ": " + s.name() + " is locked at " + shown(s, s.value()) + " for this world."
                        + " Presets, imports and other admins leave it alone until it is unlocked."),
                List.of(who + " locked " + f.name() + " " + s.name() + " (at " + shown(s, s.value()) + ")."));
    }

    /** /giants admin unlock: any admin may; the value stays as the world's own */
    public static Result unlock(MinecraftServer server, String who, String which, String id) {
        Found f = setting(server, target(server, which), id);
        if (f.why() != null) return Result.fail(f.why());
        AdminSetting s = f.s();
        String k = key(f);
        AdminWorld w = AdminWorld.get(server);
        AdminWorld.Lock l = w.lock(k, s.id());
        if (l == null) return Result.said(List.of(f.name() + ": " + s.name() + " isn't locked."));
        w.unlock(k, s.id());
        w.log(new AdminWorld.Change(System.currentTimeMillis(), who, k, "unlock:" + s.id(), s.name(), "", "", ""));
        return new Result(true,
                List.of(f.name() + ": " + s.name() + " is unlocked (it was locked by " + l.who() + "). It keeps "
                        + shown(s, s.value()) + " for this world."),
                List.of(who + " unlocked " + f.name() + " " + s.name() + "."));
    }

    /** /giants admin locks: every locked setting */
    public static Result locks(MinecraftServer server) {
        AdminWorld w = AdminWorld.get(server);
        if (w.locks().isEmpty()) return Result.said(List.of("No settings are locked in this world."));
        List<String> lines = new ArrayList<>(List.of("Locked settings:"));
        java.text.SimpleDateFormat when = new java.text.SimpleDateFormat("MMM d HH:mm", Locale.ROOT);
        for (var e : w.locks().entrySet()) {
            String[] gi = e.getKey().split("/", 2);
            lines.add("  " + giantWord(gi[0]) + " " + settingName(server, gi[0], gi[1]) + ": by "
                    + e.getValue().who() + ", " + when.format(new java.util.Date(e.getValue().time())));
        }
        return Result.said(lines);
    }

    /** a setting's plain name from its giant's list (the id if he isn't here) */
    static String settingName(MinecraftServer server, String giant, String id) {
        if (giant.equals(AdminCatalog.ALL)) {
            AdminSetting s = AdminCatalog.shared(id);
            return s == null ? id : s.name();
        }
        Giant g = giant(giant);
        AdminJson.Report r = g == null ? null : report(server, g);
        AdminSetting s = r == null ? null : r.setting(id);
        return s == null ? id : s.name();
    }

    /** a giant's key as people say it: "Wreckback", "All giants" */
    static String giantWord(String key) {
        return key.equals(AdminCatalog.ALL) ? "All giants"
                : key.equals("everything") ? "Every giant"
                : giant(key) != null ? nameOf(giant(key))
                : key.isEmpty() ? key : Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }

    /** /giants admin log: the last changes, oldest first */
    public static Result log(MinecraftServer server, int most) {
        List<AdminWorld.Change> all = AdminWorld.get(server).log();
        if (all.isEmpty()) return Result.said(List.of("No changes yet in this world."));
        List<String> lines = new ArrayList<>();
        lines.add("Recent changes (newest last):");
        java.text.SimpleDateFormat when = new java.text.SimpleDateFormat("MMM d HH:mm", Locale.ROOT);
        for (AdminWorld.Change c : all.subList(Math.max(0, all.size() - most), all.size()))
            lines.add("  " + when.format(new java.util.Date(c.time())) + " " + c.who() + ": " + changeText(c));
        return Result.said(lines);
    }

    /** one change in plain words: "Wreckback Health multiplier 1 -> 2" */
    public static String changeText(AdminWorld.Change c) {
        String g = giantWord(c.giant());
        if (c.id().equals("*")) return g + ": " + c.name() + " back to the defaults (" + c.old() + ")";
        if (c.id().equals(AdminCreation.LOG_ID))
            return c.name() + ": " + (c.old().isEmpty() ? "" : c.old() + " ") + "(" + c.now() + ")";

        if (c.id().startsWith("preset:") || c.id().startsWith("import") || c.id().equals("snapshot")
                || c.id().equals("undo") || c.id().equals("sync"))
            return g + ": " + c.name() + " (" + c.now() + ")";
        if (c.id().startsWith("every:")) return "Every giant: " + c.name() + " → " + c.now();
        if (c.id().startsWith("lock:")) return g + " " + c.name() + " locked " + c.now();
        if (c.id().startsWith("unlock:")) return g + " " + c.name() + " unlocked";
        if (c.id().startsWith("mod:")) return c.name();
        // (Part 5) a player rule, a re-pick, a vote
        if (c.id().startsWith("players:")) return c.name();
        // (Part 6) the server tools: events, schedules, areas, respawn timers, profiles
        if (c.id().startsWith("tools:")) return c.name() + (c.now().isEmpty() ? "" : " (" + c.now() + ")");
        return g + " " + c.name() + " " + c.old() + " → " + c.now();
    }

    /** tells every other operator online (and the server log) */
    public static void announce(MinecraftServer server, @Nullable ServerPlayer by, List<String> lines) {
        for (String s : lines) GiantsGuideMod.LOG.info("Giants admin: {}", s);
        // (Part 6) quiet mode: the change log and the server log only
        if (net.jj.giants.tools.Tools.quiet(server)) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p == by || !p.hasPermissions(2)) continue;
            for (String s : lines)
                p.sendSystemMessage(Component.literal(s).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    /** the value a JSON number would be for a plain number (for the tests and the book) */
    public static JsonElement number(double d) {
        return new JsonPrimitive(d);
    }
}
