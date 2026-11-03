package net.jj.giants.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

/**
 * (Part 6, ADMIN_BRIDGE.md §22.9) Alerts to admins: a fight near spawn, heavy griefing, a player killed by a giant, a
 * giant kept out of an area or a dimension. Each is kept (the last 50, the book's Alerts tab) and told in chat to the
 * admins online, with a link to go to the giant, unless its switch is off or quiet mode is on.
 */
public final class Alerts {
    private Alerts() {}

    /** the setting that switches each kind of alert's chat line */
    static String setting(String kind) {
        return switch (kind) {
            case "fight" -> "alertFights";
            case "grief" -> "alertGrief";
            case "death" -> "alertDeaths";
            default -> "alertAreas";
        };
    }

    public static void add(MinecraftServer server, String kind, String text, String giant, String id, String dim,
            int x, int z, Set<UUID> notThem) {
        ToolsData.Alert a = new ToolsData.Alert();
        a.time = System.currentTimeMillis();
        a.kind = kind;
        a.text = text;
        a.giant = giant;
        a.id = id;
        a.dim = dim;
        a.x = x;
        a.z = z;
        ToolsData.get(server).alert(a);
        net.jj.giants.GiantsGuideMod.LOG.info("Giants alert: {}", text);
        ToolsAdmin.changed();
        if (!Tools.on(server, setting(kind))) return;
        MutableComponent line = Component.literal("[Giants] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.YELLOW));
        if (!giant.isEmpty() && !id.isEmpty())
            line.append(Component.literal(" [Go there]").withStyle(s -> s.withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            "/giants admin goto " + giant + " " + id))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Takes you to him")))));
        Tools.tellAdmins(server, line, notThem);
    }

    public static net.jj.giants.admin.AdminServer.Result lines(MinecraftServer server) {
        List<ToolsData.Alert> all = ToolsData.get(server).s.alerts;
        if (all.isEmpty()) return net.jj.giants.admin.AdminServer.Result.said(List.of("No alerts yet."));
        List<String> out = new ArrayList<>(List.of("Alerts (newest last):"));
        java.text.SimpleDateFormat when = new java.text.SimpleDateFormat("MMM d HH:mm", java.util.Locale.ROOT);
        for (ToolsData.Alert a : all.subList(Math.max(0, all.size() - 15), all.size()))
            out.add("  " + when.format(new java.util.Date(a.time)) + " " + a.text);
        return net.jj.giants.admin.AdminServer.Result.said(out);
    }

    public static net.jj.giants.admin.AdminServer.Result clear(MinecraftServer server, String who) {
        ToolsData d = ToolsData.get(server);
        d.s.alerts.clear();
        d.setDirty();
        ToolsAdmin.changed();
        return net.jj.giants.admin.AdminServer.Result.said(List.of("The alerts are cleared."));
    }
}
