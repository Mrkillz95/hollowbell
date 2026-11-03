package net.jj.giants.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminBook;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminLive;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminTransfer;
import net.jj.giants.net.AdminBookStatePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 6, ADMIN_BRIDGE.md §22.13) The admin book's Server tools page, the server's half: the page's data (sent with
 * the other pages when anything changes, and every two seconds to whoever is looking at it, for the map and the
 * stats) and its requests, each the same as its command. Admins only.
 */
public final class ToolsAdmin {
    private ToolsAdmin() {}

    public static final String KIND = "tools";
    /** the book's requests this class answers */
    public static final List<String> DOS = List.of("eventstart", "eventstop", "schedadd", "schedremove", "schedturn",
            "schedstop", "areaadd", "areaedit", "arearemove", "maptp", "mapground", "statsreset", "alertsclear",
            "respawn", "profilesave", "profileapply", "profiledelete");

    /** who is looking at the page */
    private static final Set<UUID> watching = new LinkedHashSet<>();
    private static boolean dirty;

    public static void forget() {
        watching.clear();
        grounds.clear();
        groundsAt = -100000;
        dirty = false;
    }

    public static boolean anyWatching() {
        return !watching.isEmpty();
    }

    /** something on the page changed: every open book gets it with the next fresh pages */
    public static void changed() {
        AdminBook.changed(KIND);
        dirty = true;
    }

    /** the page is being looked at (it then comes every two seconds), or not */
    public static void watch(MinecraftServer server, ServerPlayer p, boolean on) {
        if (!on) {
            watching.remove(p.getUUID());
            return;
        }
        if (AdminBook.role(p) != AdminBook.Role.ADMIN) return;
        watching.add(p.getUUID());
        AdminBook.sender.accept(p, page(server));
    }

    static void tick(MinecraftServer server) {
        if (watching.isEmpty()) return;
        if (server.getTickCount() % 40 != 0 && !dirty) return;
        dirty = false;
        AdminBookStatePayload pg = null;
        for (UUID u : List.copyOf(watching)) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p == null || AdminBook.role(p) != AdminBook.Role.ADMIN) {
                watching.remove(u);
                continue;
            }
            if (pg == null) pg = page(server);
            AdminBook.sender.accept(p, pg);
        }
    }

    public static AdminBookStatePayload page(MinecraftServer server) {
        return new AdminBookStatePayload(KIND, json(server).toString());
    }

    /** each giant's grounds (cached a minute: they hardly ever move) */
    private static final Map<String, JsonArray> grounds = new HashMap<>();
    private static int groundsAt = -100000;

    private static JsonArray grounds(MinecraftServer server) {
        if (server.getTickCount() - groundsAt > 1200 || server.getTickCount() < groundsAt) {
            grounds.clear();
            groundsAt = server.getTickCount();
            for (Giant g : AdminServer.giants()) {
                List<String> l = AdminServer.call(server, g, "grounds", "");
                JsonArray a = new JsonArray();
                if (l != null)
                    for (String line : l) {
                        JsonObject o = AdminJson.object(line);
                        if (o != null && o.has("x") && o.has("z")) a.add(o);
                    }
                grounds.put(g.key(), a);
            }
        }
        JsonArray out = new JsonArray();
        for (var e : grounds.entrySet())
            for (var x : e.getValue()) {
                JsonObject o = x.getAsJsonObject().deepCopy();
                o.addProperty("key", e.getKey());
                out.add(o);
            }
        return out;
    }

    public static JsonObject json(MinecraftServer server) {
        ToolsData d = ToolsData.get(server);
        JsonObject o = new JsonObject();
        o.addProperty("paused", Tools.on(server, "paused"));
        o.addProperty("quiet", Tools.quiet(server));
        // events
        JsonArray kinds = new JsonArray();
        for (Events.Kind k : Events.KINDS.values()) {
            JsonObject j = new JsonObject();
            j.addProperty("id", k.id());
            j.addProperty("name", k.name());
            j.addProperty("about", k.about());
            j.addProperty("minutes", k.minutes());
            kinds.add(j);
        }
        o.add("events", kinds);
        if (d.s.event != null) o.add("event", running(d.s.event, true));
        // schedules
        JsonArray sch = new JsonArray();
        for (ToolsData.Schedule s : d.s.schedules) {
            JsonObject j = new JsonObject();
            j.addProperty("id", s.id);
            j.addProperty("name", s.name);
            j.addProperty("preset", s.preset);
            j.addProperty("when", Schedules.when(s));
            j.addProperty("on", s.on);
            j.addProperty("now", d.s.schedule != null && d.s.schedule.id.equals(s.id));
            sch.add(j);
        }
        o.add("schedules", sch);
        if (d.s.schedule != null) o.add("schedule", running(d.s.schedule, false));
        JsonArray presets = new JsonArray();
        for (String id : AdminTransfer.presets()) {
            AdminTransfer.Bundle b = AdminTransfer.preset(id);
            if (b == null) continue;
            JsonObject j = new JsonObject();
            j.addProperty("id", id);
            j.addProperty("name", AdminTransfer.title(b));
            presets.add(j);
        }
        for (net.jj.giants.admin.AdminPresets.Saved s : net.jj.giants.admin.AdminPresets.list()) {
            JsonObject j = new JsonObject();
            j.addProperty("id", s.name());
            j.addProperty("name", s.name());
            presets.add(j);
        }
        o.add("presets", presets);
        // areas
        JsonArray areas = new JsonArray();
        for (ToolsData.Area a : d.s.areas) {
            JsonObject j = new JsonObject();
            j.addProperty("id", a.id);
            j.addProperty("name", a.name);
            j.addProperty("kind", a.kind);
            j.addProperty("dim", a.dim);
            j.addProperty("x", a.x);
            j.addProperty("z", a.z);
            j.addProperty("r", a.r);
            j.addProperty("by", a.by);
            areas.add(j);
        }
        o.add("areas", areas);
        BlockPos sp = server.overworld().getSharedSpawnPos();
        JsonObject spawn = new JsonObject();
        spawn.addProperty("x", sp.getX());
        spawn.addProperty("z", sp.getZ());
        spawn.addProperty("clear", Tools.num(server, "spawnClear"));
        o.add("spawn", spawn);
        // the map: every giant, his grounds, the players
        Map<String, List<Watch.Row>> rows = Watch.rows(server);
        JsonArray giants = new JsonArray();
        for (var e : rows.entrySet())
            for (Watch.Row r : e.getValue()) {
                JsonObject j = new JsonObject();
                j.addProperty("key", r.kind());
                j.addProperty("name", r.name());
                for (String k : new String[] {"id", "state", "dim", "x", "z", "mood", "owner", "fighting", "asleep",
                    "own", "health", "max", "size"})
                    if (r.r().has(k)) j.add(k, r.r().get(k));
                giants.add(j);
            }
        o.add("giants", giants);
        o.add("grounds", grounds(server));
        JsonArray players = new JsonArray();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            JsonObject j = new JsonObject();
            j.addProperty("name", p.getGameProfile().getName());
            j.addProperty("dim", p.level().dimension().location().toString());
            j.addProperty("x", Math.round(p.getX() * 10) / 10.0);
            j.addProperty("z", Math.round(p.getZ() * 10) / 10.0);
            players.add(j);
        }
        o.add("players", players);
        // the stats
        JsonArray stats = new JsonArray();
        for (Giant g : AdminServer.giants()) {
            ToolsData.Stat s = d.s.stats.getOrDefault(g.key(), new ToolsData.Stat());
            List<Watch.Row> rs = rows.get(g.key());
            JsonObject j = new JsonObject();
            j.addProperty("key", g.key());
            j.addProperty("name", AdminServer.nameOf(g));
            j.addProperty("listed", rs != null);
            int here = 0, away = 0;
            Set<String> owners = new LinkedHashSet<>();
            if (rs != null)
                for (Watch.Row r : rs) {
                    if (r.state().equals("here")) here++;
                    else away++;
                    String ow = AdminJson.str(r.r(), "owner", 48);
                    if (!ow.isEmpty()) owners.add(ow);
                }
            j.addProperty("here", here);
            j.addProperty("away", away);
            j.addProperty("owners", String.join(", ", owners));
            j.addProperty("deaths", s.deaths);
            j.addProperty("slain", s.slain);
            j.addProperty("killed", s.killed);
            j.addProperty("damage", Math.round(s.damage));
            j.addProperty("blocks", s.blocks);
            j.addProperty("ms", Math.round(Stats.costMs(g.key()) * 1000) / 1000.0);
            stats.add(j);
        }
        o.add("stats", stats);
        o.addProperty("tickMs", Math.round(Stats.tickMs() * 10) / 10.0);
        o.addProperty("statsSince", d.s.statsSince);
        // the alerts, newest first
        JsonArray alerts = new JsonArray();
        for (int i = d.s.alerts.size() - 1; i >= 0; i--) {
            ToolsData.Alert a = d.s.alerts.get(i);
            JsonObject j = new JsonObject();
            j.addProperty("time", a.time);
            j.addProperty("kind", a.kind);
            j.addProperty("text", a.text);
            j.addProperty("giant", a.giant);
            j.addProperty("id", a.id);
            j.addProperty("dim", a.dim);
            j.addProperty("x", a.x);
            j.addProperty("z", a.z);
            alerts.add(j);
        }
        o.add("alerts", alerts);
        o.add("respawns", Respawns.json(server));
        o.add("profile", Profiles.json(server));
        o.add("pack", PackMode.json());
        return o;
    }

    /** Home's line: what is on now */
    public static JsonObject homeJson(MinecraftServer server) {
        ToolsData d = ToolsData.get(server);
        JsonObject o = new JsonObject();
        o.addProperty("paused", Tools.on(server, "paused"));
        o.addProperty("quiet", Tools.quiet(server));
        if (d.s.event != null) o.add("event", running(d.s.event, true));
        if (d.s.schedule != null) o.add("schedule", running(d.s.schedule, false));
        o.add("pack", PackMode.json());
        o.addProperty("alerts", d.s.alerts.size());
        return o;
    }

    private static JsonObject running(ToolsData.Running r, boolean event) {
        JsonObject j = new JsonObject();
        j.addProperty("id", r.id);
        j.addProperty("name", r.name);
        j.addProperty("by", r.by);
        if (event) j.addProperty("seconds", r.left / 20);
        j.addProperty("changes", r.puts.size());
        return j;
    }

    /** one of the page's requests (an admin, checked by the book already) */
    public static @Nullable AdminServer.Result request(MinecraftServer server, ServerPlayer p, String what,
            JsonObject o) {
        String who = p.getGameProfile().getName();
        return switch (what) {
            case "eventstart" -> Events.start(server, who, AdminJson.str(o, "kind", 16),
                    (int) AdminJson.num(o, "minutes", 30));
            case "eventstop" -> Events.stop(server, who);
            case "schedadd" -> Schedules.add(server, who, AdminJson.str(o, "preset", 48), AdminJson.str(o, "kind", 16),
                    AdminJson.str(o, "when", 64));
            case "schedremove" -> Schedules.remove(server, who, AdminJson.str(o, "id", 16));
            case "schedturn" -> Schedules.turn(server, who, AdminJson.str(o, "id", 16),
                    !AdminJson.str(o, "on", 8).equals("false"));
            case "schedstop" -> Schedules.stopNow(server, who);
            case "areaadd" -> Areas.add(server, p, AdminJson.str(o, "kind", 16), (int) AdminJson.num(o, "r", 64),
                    AdminJson.str(o, "name", 32));
            case "areaedit" -> Areas.edit(server, who, AdminJson.str(o, "id", 16), (int) AdminJson.num(o, "r", 0),
                    AdminJson.str(o, "kind", 16));
            case "arearemove" -> Areas.remove(server, who, AdminJson.str(o, "id", 16));
            case "maptp" -> AdminLive.act(server, p, AdminJson.str(o, "giant", 32), AdminJson.str(o, "id", 40), "tp",
                    "");
            case "mapground" -> ground(server, p, AdminJson.str(o, "giant", 32));
            case "statsreset" -> Stats.reset(server, who);
            case "alertsclear" -> Alerts.clear(server, who);
            case "respawn" -> Respawns.act(server, who, AdminJson.str(o, "giant", 32),
                    (long) AdminJson.num(o, "ticks", 0));
            case "profilesave" -> Profiles.save(server, who);
            case "profileapply" -> Profiles.applyNow(server, who);
            case "profiledelete" -> Profiles.delete(server, who);
            default -> null;
        };
    }

    /** the map's "take me to his ground": his own way there (as /giants home <name>) */
    static AdminServer.Result ground(MinecraftServer server, ServerPlayer p, String which) {
        Giant g = AdminServer.giant(which);
        if (g == null) return AdminServer.Result.fail("No giant called " + which + ".");
        List<String> r = AdminServer.call(server, g, "home", p.getStringUUID());
        if (r == null || r.isEmpty()) return AdminServer.Result.fail(AdminServer.nameOf(g) + ": he can't take you there.");
        return AdminServer.Result.said(r);
    }
}
