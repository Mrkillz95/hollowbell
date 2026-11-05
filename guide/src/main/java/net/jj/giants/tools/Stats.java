package net.jj.giants.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * (Part 6, ADMIN_BRIDGE.md §22.8) The stats page's numbers, per kind of giant: how many fell (and how many by a
 * player's hand), the players he killed and the damage he did them, the blocks he broke (counted while his own
 * entities tick: him, his parts and his creatures), and what he costs the server: the time his entities' ticks take,
 * per tick, over the last ten seconds. The counts are kept with the world; the costs only while the server runs.
 */
public final class Stats {
    private Stats() {}

    // what the mixins call (the server thread only)

    /** the kind whose entity is ticking now, or null */
    public static String current;
    private static long started;
    private static final Map<String, long[]> nanos = new LinkedHashMap<>();
    /** blocks broken this minute, per kind */
    private static final Map<String, int[]> broke = new LinkedHashMap<>();
    /** ms per tick over the last ten seconds, per kind */
    private static final Map<String, Double> cost = new LinkedHashMap<>();
    /** the server's own ms per tick then */
    private static double tickMs;

    public static void entityStarts(Entity e) {
        // (Part 10A) a minigame's giants are left out (their cost, what they break, grief healing)
        current = net.jj.giants.minigames.MgLevels.temporary(e.level()) ? null : Tools.kindOf(e);
        // (Part 10B) the entity itself, for the blocks giants break while they fight
        currentEntity = current != null ? e : null;
        if (current != null) started = System.nanoTime();
    }

    /** (Part 10B) the giant mod's entity ticking now, or null */
    public static Entity currentEntity;

    public static void entityEnds() {
        if (current == null) return;
        nanos.computeIfAbsent(current, k -> new long[1])[0] += System.nanoTime() - started;
        current = null;
        currentEntity = null;
    }

    /** a block went to air while one of this kind's entities ticked */
    public static void blockBroken() {
        if (current != null) broke.computeIfAbsent(current, k -> new int[1])[0]++;
    }

    public static void forget() {
        nanos.clear();
        broke.clear();
        cost.clear();
        current = null;
    }

    /** each tick: every ten seconds the costs; every minute the blocks */
    static void tick(MinecraftServer server) {
        int t = server.getTickCount();
        if (t % 200 == 0) {
            cost.clear();
            for (var e : nanos.entrySet()) cost.put(e.getKey(), e.getValue()[0] / 200.0 / 1e6);
            nanos.clear();
            tickMs = server.getAverageTickTimeNanos() / 1e6;
        }
        if (t % 1200 == 0 && !broke.isEmpty()) minute(server);
    }

    /** the blocks broken this minute go into the counts; a kind past the setting is an alert */
    public static void minute(MinecraftServer server) {
        ToolsData d = ToolsData.get(server);
        int most = Tools.num(server, "alertGriefBlocks");
        boolean alert = Tools.on(server, "alertGrief");
        for (var e : broke.entrySet()) {
            int n = e.getValue()[0];
            d.stat(e.getKey()).blocks += n;
            if (alert && n >= most) {
                Giant g = AdminServer.giant(e.getKey());
                String name = g == null ? e.getKey() : AdminServer.nameOf(g);
                Alerts.add(server, "grief", name + " broke " + n + " blocks in the last minute.", e.getKey(), "", "",
                        0, 0, Set.of());
            }
        }
        d.setDirty();
        broke.clear();
    }

    /** a player died: killed by a giant (his blow, his creature's, his shot) */
    static void died(LivingEntity e, DamageSource src) {
        if (!(e instanceof ServerPlayer p) || net.jj.giants.minigames.MgLevels.temporary(p.level())) return;
        String kind = Tools.kindOf(src.getEntity());
        if (kind == null) kind = Tools.kindOf(src.getDirectEntity());
        if (kind == null) return;
        MinecraftServer server = p.getServer();
        if (server == null) return;
        ToolsData d = ToolsData.get(server);
        d.stat(kind).killed++;
        d.setDirty();
        if (Tools.on(server, "alertDeaths")) {
            Giant g = AdminServer.giant(kind);
            String name = g == null ? kind : AdminServer.nameOf(g);
            Entity by = src.getEntity() != null ? src.getEntity() : src.getDirectEntity();
            Alerts.add(server, "death", "A " + name + " killed " + p.getGameProfile().getName() + ".", kind,
                    by == null ? "" : by.getStringUUID(), p.level().dimension().location().toString(), p.getBlockX(),
                    p.getBlockZ(), Set.of(p.getUUID()));
        }
    }

    /** a player was hurt by a giant */
    static void damaged(LivingEntity e, DamageSource src, float base, float taken, boolean blocked) {
        if (!(e instanceof ServerPlayer p) || taken <= 0 || net.jj.giants.minigames.MgLevels.temporary(p.level()))
            return;
        String kind = Tools.kindOf(src.getEntity());
        if (kind == null) kind = Tools.kindOf(src.getDirectEntity());
        if (kind == null || p.getServer() == null) return;
        ToolsData d = ToolsData.get(p.getServer());
        d.stat(kind).damage += taken;
        d.setDirty();
    }

    /** a giant fell (the Guide's own count, Players.slain): by a player's hand or not */
    public static void fell(MinecraftServer server, String kind, boolean byPlayer) {
        ToolsData d = ToolsData.get(server);
        ToolsData.Stat s = d.stat(kind);
        s.deaths++;
        if (byPlayer) s.slain++;
        d.setDirty();
        ToolsAdmin.changed();
    }

    public static double costMs(String kind) {
        return cost.getOrDefault(kind, 0.0);
    }

    public static double tickMs() {
        return tickMs;
    }

    public static AdminServer.Result reset(MinecraftServer server, String who) {
        ToolsData d = ToolsData.get(server);
        d.s.stats.clear();
        d.s.statsSince = System.currentTimeMillis();
        d.setDirty();
        Areas.log(server, who, "stats", "Stats cleared");
        return new AdminServer.Result(true, List.of("The giants' stats start again from now."),
                List.of(who + " cleared the giants' stats."));
    }

    /** /giants admin stats: a line per kind */
    public static AdminServer.Result lines(MinecraftServer server) {
        ToolsData d = ToolsData.get(server);
        Map<String, List<Watch.Row>> rows = Watch.rows(server);
        List<String> out = new java.util.ArrayList<>(List.of("Giants (standing, fallen / by players, players killed,"
                + " damage to players, blocks broken, ms per tick):"));
        for (Giant g : AdminServer.giants()) {
            ToolsData.Stat s = d.s.stats.getOrDefault(g.key(), new ToolsData.Stat());
            List<Watch.Row> rs = rows.get(g.key());
            out.add("  " + AdminServer.nameOf(g) + ": " + (rs == null ? "?" : rs.size()) + ", " + s.deaths + " / "
                    + s.slain + ", " + s.killed + ", " + Math.round(s.damage) + ", " + s.blocks + ", "
                    + String.format(java.util.Locale.ROOT, "%.2f", costMs(g.key())));
        }
        out.add("The server: " + String.format(java.util.Locale.ROOT, "%.1f", tickMs) + " ms per tick.");
        return AdminServer.Result.said(out);
    }

    /** (the tests) a kind's cost now, as if measured */
    public static void costForTests(String kind, double ms) {
        cost.put(kind, ms);
    }

    /** (the tests) the blocks broken this minute */
    public static int brokeForTests(String kind) {
        int[] n = broke.get(kind);
        return n == null ? 0 : n[0];
    }
}
