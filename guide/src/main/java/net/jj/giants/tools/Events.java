package net.jj.giants.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminTransfer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 6, ADMIN_BRIDGE.md §22.6) The events button: a giant war, a siege on spawn or a Boss Rush hour, for so many
 * minutes, then everything it changed goes back by itself. One at a time. The time counts while the server runs, and
 * a boss bar shows it to everyone (not in quiet mode).
 */
public final class Events {
    private Events() {}

    /** an event: its id, name, what it says when it starts and ends, and the minutes it lasts unless told */
    public record Kind(String id, String name, String about, String start, String end, int minutes) {}

    public static final Map<String, Kind> KINDS = new LinkedHashMap<>();

    static {
        KINDS.put("war", new Kind("war", "Giant war",
                "Every giant seeks out the others and fights every kind he meets; the sleeping ones wake.",
                "Giant war! The giants are seeking each other out to fight.", "The giant war is over.", 30));
        KINDS.put("siege", new Kind("siege", "Siege on spawn",
                "Every giant marches on the world's spawn, hunts every player he sees there and breaks blocks on the"
                        + " way (griefing on while it lasts).",
                "Siege! The giants are marching on spawn.", "The siege on spawn is over.", 20));
        KINDS.put("bossrush", new Kind("bossrush", "Boss Rush hour",
                "Fallen giants come back at once, with less health, harder hits, shorter waits and three times the"
                        + " loot.", "Boss Rush hour! Fallen giants come back at once, and drop three times the loot.",
                "Boss Rush hour is over.", 60));
    }

    public static final int[] MINUTES = {5, 10, 20, 30, 60, 120};

    /** every giant's values for an event (not a replace: only what it names changes) */
    static String bundle(String kind) {
        String every = switch (kind) {
            case "war" -> "\"seekGiants\":true,\"fightGiants\":true,\"meetings\":true,\"meetCooldown\":1,"
                    + "\"meetGiants\":\"fight\"";
            // (JJ, Nov 3) a siege breaks things: griefing on while it lasts, put back after like the rest
            case "siege" -> "\"temper\":\"hunter\",\"griefing\":true";
            default -> "\"worldRespawnDays\":0,\"ownedRespawnDays\":0,\"lootMultiplier\":3";
        };
        String all = kind.equals("bossrush") ? "\"all.health\":0.6,\"all.damage\":1.25,\"all.cooldowns\":0.75" : "";
        StringBuilder g = new StringBuilder();
        for (Giant k : AdminServer.giants())
            g.append(g.length() == 0 ? "" : ",").append('"').append(k.key()).append("\":{").append(every).append('}');
        return "{\"admin\":1,\"preset\":\"event\",\"name\":\"" + KINDS.get(kind).name() + "\",\"replace\":false,"
                + "\"all\":{" + all + "},\"giants\":{" + g + "}}";
    }

    private static @Nullable ServerBossEvent bar;
    private static int barTotal = 1;

    public static void forget() {
        if (bar != null) bar.removeAllPlayers();
        bar = null;
    }

    public static AdminServer.Result start(MinecraftServer server, String who, String kind, int minutes) {
        Kind k = KINDS.get(kind);
        if (k == null) return AdminServer.Result.fail("No event called " + kind + ". Try one of: war, siege, bossrush.");
        if (minutes < 1 || minutes > 240) return AdminServer.Result.fail("An event lasts from 1 to 240 minutes.");
        ToolsData d = ToolsData.get(server);
        if (d.s.event != null)
            return AdminServer.Result.fail(d.s.event.name + " is on already. Stop it first (/giants admin event stop).");
        AdminTransfer.Bundle b = AdminTransfer.read(bundle(kind));
        AdminTransfer.Plan plan = AdminTransfer.plan(server, b);
        ToolsData.Running r = new ToolsData.Running();
        r.id = kind;
        r.name = k.name();
        r.by = who;
        r.left = minutes * 1200L;
        r.started = System.currentTimeMillis();
        r.puts = Timed.apply(server, who, plan, "event", "Event started: " + k.name() + " for " + minutes + " minutes",
                false);
        d.s.event = r;
        d.setDirty();
        barTotal = (int) r.left;
        // what it does besides the settings, through each giant's own bridge
        kick(server, kind);
        Tools.tellEveryone(server, Tools.gold(k.start() + " (" + minutes + " minutes)"));
        ToolsAdmin.changed();
        List<String> lines = new ArrayList<>(List.of(k.name() + " for " + minutes + " minutes: " + r.puts.size()
                + " settings changed until it ends."));
        if (!plan.locked().isEmpty()) lines.add("Locked, so left as they are: " + String.join(", ", plan.locked())
                + ".");
        return new AdminServer.Result(true, lines, List.of(who + " started " + k.name() + " (" + minutes
                + " minutes)."));
    }

    /** the giants' own commands an event uses: wake them all (war), send them all to spawn (siege), bring the fallen
     * back at once (Boss Rush hour) */
    private static void kick(MinecraftServer server, String kind) {
        BlockPos sp = server.overworld().getSharedSpawnPos();
        for (Giant g : AdminServer.giants()) {
            switch (kind) {
                case "war" -> AdminServer.call(server, g, "wake", "");
                case "siege" -> AdminServer.call(server, g, "goto", sp.getX() + " " + sp.getZ());
                case "bossrush" -> Respawns.bringBack(server, g);
                default -> {}
            }
        }
    }

    public static AdminServer.Result stop(MinecraftServer server, String who) {
        ToolsData d = ToolsData.get(server);
        if (d.s.event == null) return AdminServer.Result.fail("No event is on.");
        String name = d.s.event.name;
        end(server, who);
        return new AdminServer.Result(true, List.of(name + " is over: everything it changed is back."),
                List.of(who + " ended " + name + "."));
    }

    private static void end(MinecraftServer server, String who) {
        ToolsData d = ToolsData.get(server);
        ToolsData.Running r = d.s.event;
        if (r == null) return;
        d.s.event = null;
        d.setDirty();
        Timed.restore(server, who, r.puts, "event-end", "Event ended: " + r.name);
        Kind k = KINDS.get(r.id);
        Tools.tellEveryone(server, Tools.gold(k != null ? k.end() : r.name + " is over."));
        forget();
        ToolsAdmin.changed();
    }

    /** once a second: the time goes down; at none, it ends; the siege keeps them coming every minute */
    public static void tick(MinecraftServer server) {
        ToolsData d = ToolsData.get(server);
        ToolsData.Running r = d.s.event;
        if (r == null) {
            if (bar != null) forget();
            return;
        }
        r.left -= 20;
        d.setDirty();
        if (r.left <= 0) {
            end(server, "Event");
            return;
        }
        if (r.id.equals("siege") && r.left % 1200 == 0) kick(server, "siege");
        // the boss bar: everyone sees how long is left (not in quiet mode)
        if (Tools.quiet(server)) {
            forget();
            return;
        }
        if (bar == null) {
            bar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
            barTotal = Math.max(barTotal, (int) r.left);
        }
        long s = r.left / 20;
        bar.setName(Component.literal(r.name + " · " + s / 60 + ":" + String.format("%02d", s % 60)));
        bar.setProgress(Math.max(0f, Math.min(1f, r.left / (float) Math.max(1, barTotal))));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) bar.addPlayer(p);
    }

    public static AdminServer.Result status(MinecraftServer server) {
        ToolsData.Running r = ToolsData.get(server).s.event;
        if (r == null)
            return AdminServer.Result.said(List.of("No event is on. /giants admin event war|siege|bossrush [minutes]"
                    + " starts one."));
        long s = r.left / 20;
        return AdminServer.Result.said(List.of(r.name + " is on (" + r.by + "): " + s / 60 + " minutes " + s % 60
                + " seconds left."));
    }
}
