package net.jj.giants.tools;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminLive;
import net.jj.giants.admin.AdminServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 6, ADMIN_BRIDGE.md §22.3) Every two seconds, while anyone is on: each giant's live list (his own bridge, the
 * Live control page's), and what the server tools do with it: the pause (his own freeze, every one standing), the
 * dimension and area rules (his own home and remove), fights near spawn (an alert), the safe areas (the players in
 * them, as ignored players), and the map page's numbers. Only giants already loaded are looked at; nothing is loaded.
 */
public final class Watch {
    private Watch() {}

    /** one of a giant's live rows */
    public record Row(String kind, String name, JsonObject r) {
        public String id() {
            return AdminJson.str(r, "id", 40);
        }

        public String state() {
            return AdminJson.str(r, "state", 16);
        }

        public String dim() {
            return AdminJson.str(r, "dim", 64);
        }

        public double x() {
            return AdminJson.num(r, "x", 0);
        }

        public double z() {
            return AdminJson.num(r, "z", 0);
        }

        public boolean fighting() {
            return r.has("fighting") && r.get("fighting").getAsBoolean();
        }
    }

    /** the last look: every giant kind's rows (null: his mod doesn't list them) */
    private static final Map<String, List<Row>> last = new LinkedHashMap<>();
    private static int lastAt = -1000;
    /** a giant sent out of an area: when (server tick), so he is taken away if he's still there a minute later */
    private static final Map<String, Integer> sentOut = new HashMap<>();
    /** alerts already given (kind/giant id) and when, so each is told at most once in five minutes */
    private static final Map<String, Integer> told = new HashMap<>();
    /** the players in a safe area at the last look */
    private static Set<String> safe = Set.of();
    private static boolean wasPaused;

    public static void forget() {
        last.clear();
        sentOut.clear();
        told.clear();
        safe = Set.of();
        wasPaused = false;
        lastAt = -1000;
    }

    /** the players standing in a safe area now (the giants ignore them while they're there) */
    public static Set<String> safeNow() {
        return safe;
    }

    /** every kind's rows now (a fresh look if the last is more than a second old) */
    public static Map<String, List<Row>> rows(MinecraftServer server) {
        if (server.getTickCount() - lastAt > 20 || server.getTickCount() < lastAt) read(server);
        return last;
    }

    private static void read(MinecraftServer server) {
        last.clear();
        lastAt = server.getTickCount();
        for (Giant g : AdminServer.giants()) {
            List<String> lines = AdminServer.call(server, g, AdminLive.LIST, String.valueOf(AdminLive.VERSION));
            if (lines == null || lines.isEmpty()) continue;
            JsonObject head = AdminJson.object(lines.get(0));
            if (head == null || !head.has("live")) continue;
            List<Row> rows = new ArrayList<>();
            String name = AdminServer.nameOf(g);
            for (String l : lines.subList(1, lines.size())) {
                JsonObject r = AdminJson.object(l);
                if (r != null && r.has("id") && r.has("state")) rows.add(new Row(g.key(), name, r));
            }
            last.put(g.key(), rows);
        }
    }

    /** the two-second look */
    public static void look(MinecraftServer server) {
        if (!forced && server.getPlayerList().getPlayers().isEmpty() && !ToolsAdmin.anyWatching()) return;
        ToolsData d = ToolsData.get(server);
        boolean paused = Tools.on(server, "paused");
        boolean areas = !d.s.areas.isEmpty() || Tools.num(server, "spawnClear") > 0 || anyDimShut(server);
        boolean fights = Tools.on(server, "alertFights");
        if (!paused && !wasPaused && d.s.frozen.isEmpty() && !areas && !fights && !ToolsAdmin.anyWatching()) return;
        read(server);
        pause(server, d, paused);
        if (areas) keepOut(server, d);
        if (fights) fights(server);
        safeAreas(server, d);
    }

    // pause all giants (his own freeze: every one of him standing in loaded land)

    private static void pause(MinecraftServer server, ToolsData d, boolean paused) {
        for (Giant g : AdminServer.giants()) {
            List<Row> rows = last.get(g.key());
            if (rows == null) continue;
            List<String> here = new ArrayList<>();
            for (Row r : rows) if (r.state().equals("here")) here.add(r.id());
            if (paused) {
                if (here.isEmpty()) continue;
                AdminServer.call(server, g, "freeze", "on");
                if (d.s.frozen.addAll(here)) d.setDirty();
            } else if (wasPaused || here.stream().anyMatch(d.s.frozen::contains)) {
                // (a giant the pause froze stays frozen in his save: unfrozen when he is next seen standing)
                AdminServer.call(server, g, "freeze", "off");
                if (d.s.frozen.removeAll(here)) d.setDirty();
            }
        }
        wasPaused = paused;
        if (d.s.pausedNow != paused) {
            d.s.pausedNow = paused;
            d.setDirty();
        }
    }

    // the dimension and area rules

    private static boolean anyDimShut(MinecraftServer server) {
        for (String dim : DIMS.values()) if (Tools.option(server, dim).equals("nogiants")) return true;
        return false;
    }

    /** each dimension's setting */
    static final Map<String, String> DIMS = Map.of("minecraft:overworld", "dimOverworld",
            "minecraft:the_nether", "dimNether", "minecraft:the_end", "dimEnd");

    private static void keepOut(MinecraftServer server, ToolsData d) {
        int now = server.getTickCount();
        int clear = Tools.num(server, "spawnClear");
        BlockPos spawn = server.overworld().getSharedSpawnPos();
        Set<String> inside = new HashSet<>();
        for (List<Row> rows : last.values())
            for (Row r : rows) {
                String setting = DIMS.get(r.dim());
                if (setting != null && Tools.option(server, setting).equals("nogiants")) {
                    act(server, r, "remove");
                    alert(server, "area", r, "A " + r.name() + " was in the " + dimWord(r.dim())
                            + ", where giants may not be: taken away.", false);
                    continue;
                }
                String area = null;
                if (clear > 0 && r.dim().equals("minecraft:overworld")
                        && dist(r.x(), r.z(), spawn.getX(), spawn.getZ()) < clear) area = "spawn";
                for (ToolsData.Area a : d.s.areas)
                    if (area == null && a.kind.equals(Areas.KEEP_OUT) && Areas.inside(a, r.dim(), r.x(), r.z()))
                        area = a.name.isEmpty() ? "an area" : a.name;
                if (area == null) continue;
                String key = r.kind() + "/" + r.id();
                inside.add(key);
                Integer since = sentOut.get(key);
                String where = area.equals("spawn") ? "near spawn" : "in " + area;
                if (since == null) {
                    sentOut.put(key, now);
                    act(server, r, "home");
                    alert(server, "area", r, "A " + r.name() + " came " + where + " (no giants): sent home.", false);
                } else if (now - since >= 1200) {
                    act(server, r, "remove");
                    sentOut.remove(key);
                    alert(server, "area", r, "A " + r.name() + " stayed " + where + " (no giants): taken away.", false);
                }
            }
        sentOut.keySet().retainAll(inside);
    }

    static String dimWord(String dim) {
        return switch (dim) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "End";
            default -> dim;
        };
    }

    private static void act(MinecraftServer server, Row r, String what) {
        Giant g = AdminServer.giant(r.kind());
        if (g == null) return;
        JsonObject arg = new JsonObject();
        arg.addProperty("live", AdminLive.VERSION);
        arg.addProperty("id", r.id());
        arg.addProperty("do", what);
        arg.addProperty("value", "");
        arg.addProperty("by", "-");
        AdminServer.call(server, g, AdminLive.ACT, arg.toString());
    }

    static double dist(double x, double z, double x2, double z2) {
        return Math.hypot(x - x2, z - z2);
    }

    // fights near spawn

    private static void fights(MinecraftServer server) {
        int radius = Tools.num(server, "alertRadius");
        BlockPos spawn = server.overworld().getSharedSpawnPos();
        for (List<Row> rows : last.values())
            for (Row r : rows) {
                if (!r.state().equals("here") || !r.fighting() || !r.dim().equals("minecraft:overworld")) continue;
                double dd = dist(r.x(), r.z(), spawn.getX(), spawn.getZ());
                if (dd > radius) continue;
                alert(server, "fight", r, "A " + r.name() + " is fighting " + Math.round(dd) + " blocks from spawn.",
                        true);
            }
    }

    /**
     * An alert about one giant, at most once in five minutes for him and this kind of alert; admins within 96 blocks
     * of him aren't told (they can see it)
     */
    private static void alert(MinecraftServer server, String kind, Row r, String text, boolean nearSkip) {
        int now = server.getTickCount();
        String key = kind + "/" + r.id();
        Integer was = told.get(key);
        if (was != null && now - was < 6000 && now >= was) return;
        told.put(key, now);
        Set<UUID> near = new HashSet<>();
        if (nearSkip)
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                if (p.level().dimension().location().toString().equals(r.dim())
                        && dist(p.getX(), p.getZ(), r.x(), r.z()) < 96) near.add(p.getUUID());
        Alerts.add(server, kind, text, r.kind(), r.id(), r.dim(), (int) Math.floor(r.x()), (int) Math.floor(r.z()),
                near);
    }

    // safe areas: the players standing in one are ignored players while they're there

    private static void safeAreas(MinecraftServer server, ToolsData d) {
        Set<String> now = new HashSet<>();
        boolean any = false;
        for (ToolsData.Area a : d.s.areas) any |= a.kind.equals(Areas.SAFE);
        if (any)
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                for (ToolsData.Area a : d.s.areas)
                    if (a.kind.equals(Areas.SAFE)
                            && Areas.inside(a, p.level().dimension().location().toString(), p.getX(), p.getZ()))
                        now.add(p.getStringUUID());
        if (now.equals(safe)) return;
        safe = Set.copyOf(now);
        net.jj.giants.players.Players.pushRules(server, net.jj.giants.players.PlayerData.get(server));
    }

    private static boolean forced;

    /** a look now, whatever the clock (after a change on the page, and the tests) */
    public static void lookNow(MinecraftServer server) {
        lastAt = -1000;
        forced = true;
        try {
            look(server);
        } finally {
            forced = false;
        }
    }

    /** (the tests) a giant sent out of an area this long ago */
    public static void sentOutForTests(String key, int ticksAgo, MinecraftServer server) {
        sentOut.put(key, server.getTickCount() - ticksAgo);
    }

    static @Nullable Row find(MinecraftServer server, String kind, String id) {
        List<Row> rows = rows(server).get(kind);
        if (rows != null) for (Row r : rows) if (r.id().equals(id)) return r;
        return null;
    }
}
