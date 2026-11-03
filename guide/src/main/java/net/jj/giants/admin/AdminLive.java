package net.jj.giants.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.jj.giants.Giant;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The admin book's Live control page (Part 2B, ADMIN_BRIDGE.md "Live control"): every giant the world knows, asked of
 * each giant's own bridge ("live"), and one action on one of them ("liveact", by his id: teleport there, heal, size,
 * sleep, wake, calm, forgive, owner, home, remove, kill). Summon runs the giant's own /<giant> summon as the operator
 * who pressed it. Who may do what is checked before anything here is called (AdminBook).
 */
public final class AdminLive {
    private AdminLive() {}

    public static final int VERSION = 1;
    public static final String LIST = "live", ACT = "liveact";
    /** most giants of one kind on the page */
    static final int MOST = 64;

    /** what every giant's bridge says now, as the page's JSON */
    public static JsonObject state(MinecraftServer server) {
        JsonObject o = new JsonObject();
        JsonArray giants = new JsonArray();
        for (Giant g : AdminServer.giants()) {
            JsonObject j = new JsonObject();
            j.addProperty("key", g.key());
            j.addProperty("name", AdminServer.nameOf(g));
            j.addProperty("command", g.command());
            List<String> lines = AdminServer.call(server, g, LIST, String.valueOf(VERSION));
            JsonObject head = lines == null || lines.isEmpty() ? null : AdminJson.object(lines.get(0));
            boolean speaks = head != null && head.has("live");
            try {
                speaks = speaks && head.get("live").getAsInt() <= VERSION;
            } catch (RuntimeException e) {
                speaks = false;
            }
            j.addProperty("speaks", speaks);
            if (speaks) {
                if (head.has("name")) j.addProperty("name", AdminJson.str(head, "name", 48));
                for (String k : new String[] {"moods", "size", "does"})
                    if (head.get(k) instanceof JsonArray a) j.add(k, a);
                JsonArray rows = new JsonArray();
                for (String l : lines.subList(1, lines.size())) {
                    JsonObject r = AdminJson.object(l);
                    if (r == null || !r.has("id") || !r.has("state")) continue;
                    if (rows.size() >= MOST) break;
                    rows.add(r);
                }
                j.add("rows", rows);
            }
            giants.add(j);
        }
        o.add("giants", giants);
        // (the online players, for Set owner)
        JsonArray players = new JsonArray();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            JsonObject pj = new JsonObject();
            pj.addProperty("id", p.getStringUUID());
            pj.addProperty("name", p.getGameProfile().getName());
            players.add(pj);
        }
        o.add("players", players);
        return o;
    }

    /** a giant's header "does", or empty */
    static List<String> does(MinecraftServer server, Giant g) {
        List<String> lines = AdminServer.call(server, g, LIST, String.valueOf(VERSION));
        JsonObject head = lines == null || lines.isEmpty() ? null : AdminJson.object(lines.get(0));
        List<String> out = new ArrayList<>();
        if (head != null && head.get("does") instanceof JsonArray a)
            for (JsonElement e : a)
                try {
                    out.add(e.getAsString());
                } catch (RuntimeException bad) {
                    // left out
                }
        return out;
    }

    /** the words for an action in the operators' announcement */
    private static String told(String act, String name) {
        return switch (act) {
            case "tp" -> "went to a " + name;
            case "heal" -> "healed a " + name;
            case "size" -> "resized a " + name;
            case "sleep" -> "put a " + name + " to sleep";
            case "wake" -> "woke a " + name;
            case "calm" -> "calmed a " + name;
            case "forgive" -> "cleared a " + name + "'s grudges";
            case "owner" -> "gave a " + name + "'s book away";
            case "home" -> "sent a " + name + " home";
            case "remove" -> "removed a " + name;
            case "kill" -> "killed a " + name;
            default -> act + " " + name;
        };
    }

    /** one action on one giant, for this player (already checked) */
    public static AdminServer.Result act(MinecraftServer server, ServerPlayer p, String which, String id, String act,
            String value) {
        Giant g = AdminServer.giant(which);
        if (g == null) return AdminServer.Result.fail("No giant called " + which + ".");
        String name = AdminServer.nameOf(g), a = act.toLowerCase(Locale.ROOT);
        if (!does(server, g).contains(a))
            return AdminServer.Result.fail(name + ": this version can't do that from the book.");
        JsonObject arg = new JsonObject();
        arg.addProperty("live", VERSION);
        arg.addProperty("id", id);
        arg.addProperty("do", a);
        arg.addProperty("value", value == null ? "" : value);
        arg.addProperty("by", p.getStringUUID());
        List<String> r = AdminServer.call(server, g, ACT, arg.toString());
        if (r == null || r.isEmpty()) return AdminServer.Result.fail(name + ": his mod didn't answer.");
        String line = r.size() > 1 ? r.get(1) : "";
        if (!r.get(0).equals("ok")) return AdminServer.Result.fail(line.isEmpty() ? name + ": he can't." : line);
        // (1.13.0) Growth keeps the size an admin gave him
        if (a.equals("size")) net.jj.giants.systems.Systems.resized(server, id, value == null ? "" : value);
        String who = p.getGameProfile().getName();
        return new AdminServer.Result(true, List.of(line),
                a.equals("tp") ? List.of() : List.of(who + " " + told(a, name) + " (Live control)."));
    }

    /**
     * Summon: the giant's own summon command, run as this operator (their own permission, at their position), so it
     * does exactly what typing /<giant> summon <size> does. What it says comes back as the answer.
     */
    public static AdminServer.Result summon(MinecraftServer server, ServerPlayer p, String which, String size,
            @Nullable String mood) {
        Giant g = AdminServer.giant(which);
        if (g == null) return AdminServer.Result.fail("No giant called " + which + ".");
        double s;
        try {
            s = Double.parseDouble(size.trim());
        } catch (NumberFormatException e) {
            return AdminServer.Result.fail("The size has to be a number.");
        }
        if (!Double.isFinite(s) || s <= 0) return AdminServer.Result.fail("The size has to be a number.");
        String m = mood == null ? "" : mood.trim().toLowerCase(Locale.ROOT);
        if (!m.isEmpty() && !m.matches("[a-z_]{1,16}")) return AdminServer.Result.fail("That isn't a mood.");
        List<String> heard = new ArrayList<>();
        boolean[] worked = {false};
        CommandSource sink = new CommandSource() {
            @Override
            public void sendSystemMessage(Component c) {
                heard.add(c.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        String cmd = g.command() + " summon " + (m.isEmpty() ? "" : m + " ") + AdminSetting.plain(s);
        var src = p.createCommandSourceStack().withSource(sink).withCallback((ok, n) -> worked[0] = ok);
        server.getCommands().performPrefixedCommand(src, cmd);
        if (heard.isEmpty()) heard.add(!worked[0] ? AdminServer.nameOf(g) + ": the summon didn't work here."
                : "Summoned a " + AdminServer.nameOf(g) + ".");
        String who = p.getGameProfile().getName();
        return new AdminServer.Result(worked[0], heard, !worked[0] ? List.of()
                : List.of(who + " summoned a " + AdminServer.nameOf(g) + " at size " + AdminSetting.plain(s)
                        + " (Live control)."));
    }
}
