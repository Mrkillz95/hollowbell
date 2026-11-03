package net.jj.giants.tools;

import java.util.List;
import java.util.Locale;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminWorld;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 6, ADMIN_BRIDGE.md §22.4) Areas drawn with the book: a circle round where the admin stands. "keepout": giants
 * that come in are sent home (his own home order), and taken away if they're still there a minute later. "safe": the
 * giants ignore players standing in it (pushed to every giant as the per-player rule "ignored", Part 5).
 */
public final class Areas {
    private Areas() {}

    public static final String KEEP_OUT = "keepout", SAFE = "safe";
    public static final int MOST = 32, LEAST_R = 8, MOST_R = 2000;

    public static boolean inside(ToolsData.Area a, String dim, double x, double z) {
        return a.dim.equals(dim) && Watch.dist(x, z, a.x + 0.5, a.z + 0.5) <= a.r;
    }

    static String kindWord(String kind) {
        return kind.equals(SAFE) ? "safe area" : "no-giants area";
    }

    /** a new area round where this admin stands */
    public static AdminServer.Result add(MinecraftServer server, ServerPlayer p, String kind, int r, String name) {
        String k = kind.toLowerCase(Locale.ROOT);
        if (!k.equals(KEEP_OUT) && !k.equals(SAFE))
            return AdminServer.Result.fail("An area is keepout (no giants) or safe (giants leave players alone).");
        if (r < LEAST_R || r > MOST_R)
            return AdminServer.Result.fail("An area's radius has to be from " + LEAST_R + " to " + MOST_R + " blocks.");
        ToolsData d = ToolsData.get(server);
        if (d.s.areas.size() >= MOST) return AdminServer.Result.fail("This world has " + MOST + " areas already.");
        String n = name == null ? "" : name.strip();
        if (n.length() > 32) n = n.substring(0, 32);
        if (n.isEmpty()) n = (k.equals(SAFE) ? "Safe area " : "No giants ") + (d.s.areas.size() + 1);
        ToolsData.Area a = new ToolsData.Area();
        a.id = d.newId("a");
        a.name = n;
        a.kind = k;
        a.dim = p.level().dimension().location().toString();
        a.x = p.getBlockX();
        a.z = p.getBlockZ();
        a.r = r;
        a.by = p.getGameProfile().getName();
        a.time = System.currentTimeMillis();
        d.s.areas.add(a);
        d.setDirty();
        String who = p.getGameProfile().getName();
        String line = n + ": a " + kindWord(k) + " of " + r + " blocks round " + a.x + " " + a.z + " ("
                + Watch.dimWord(a.dim) + ").";
        log(server, who, "area", "Area added: " + line);
        Watch.lookNow(server);
        return new AdminServer.Result(true, List.of(line + (k.equals(SAFE)
                ? " The giants leave players standing in it alone."
                : " Giants that come in are sent home, and taken away if they stay.")),
                List.of(who + " added the " + kindWord(k) + " " + n + "."));
    }

    public static @Nullable ToolsData.Area find(ToolsData d, String idOrNumber) {
        for (ToolsData.Area a : d.s.areas) if (a.id.equals(idOrNumber) || a.name.equalsIgnoreCase(idOrNumber)) return a;
        try {
            int i = Integer.parseInt(idOrNumber.trim()) - 1;
            return i >= 0 && i < d.s.areas.size() ? d.s.areas.get(i) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static AdminServer.Result remove(MinecraftServer server, String who, String id) {
        ToolsData d = ToolsData.get(server);
        ToolsData.Area a = find(d, id);
        if (a == null) return AdminServer.Result.fail("There's no area " + id + ". /giants admin area lists them.");
        d.s.areas.remove(a);
        d.setDirty();
        log(server, who, "area", "Area removed: " + a.name);
        Watch.lookNow(server);
        return new AdminServer.Result(true, List.of(a.name + " is gone."), List.of(who + " removed the area " + a.name
                + "."));
    }

    /** a new size or kind for one */
    public static AdminServer.Result edit(MinecraftServer server, String who, String id, int r, String kind) {
        ToolsData d = ToolsData.get(server);
        ToolsData.Area a = find(d, id);
        if (a == null) return AdminServer.Result.fail("There's no area " + id + ".");
        if (r > 0) {
            if (r < LEAST_R || r > MOST_R)
                return AdminServer.Result.fail("An area's radius has to be from " + LEAST_R + " to " + MOST_R
                        + " blocks.");
            a.r = r;
        }
        if (!kind.isEmpty()) {
            if (!kind.equals(KEEP_OUT) && !kind.equals(SAFE)) return AdminServer.Result.fail("That isn't a kind of area.");
            a.kind = kind;
        }
        d.setDirty();
        log(server, who, "area", "Area changed: " + a.name + ", " + kindWord(a.kind) + " of " + a.r + " blocks");
        Watch.lookNow(server);
        return new AdminServer.Result(true, List.of(a.name + ": a " + kindWord(a.kind) + " of " + a.r + " blocks."),
                List.of(who + " changed the area " + a.name + "."));
    }

    public static AdminServer.Result list(MinecraftServer server) {
        ToolsData d = ToolsData.get(server);
        if (d.s.areas.isEmpty())
            return AdminServer.Result.said(List.of("No areas yet. /giants admin area add keepout|safe <radius> [name]"
                    + " makes one round where you stand."));
        List<String> lines = new java.util.ArrayList<>(List.of("Areas:"));
        int i = 1;
        for (ToolsData.Area a : d.s.areas)
            lines.add("  " + i++ + ". " + a.name + ": " + kindWord(a.kind) + ", " + a.r + " blocks round " + a.x + " "
                    + a.z + " (" + Watch.dimWord(a.dim) + ")");
        return AdminServer.Result.said(lines);
    }

    /** a server tools change in the change log (nothing to undo there; the page puts it back) */
    static void log(MinecraftServer server, String who, String what, String text) {
        AdminWorld.get(server).log(new AdminWorld.Change(System.currentTimeMillis(), who, ToolsAdmin.KIND, "tools:" + what,
                text, "", "", ""));
        ToolsAdmin.changed();
    }
}
