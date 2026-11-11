package net.jj.giants.players;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Max giants per player (ADMIN_BRIDGE.md 20.18): how many giants each player owns, counted fresh from every giant,
 * and whether one more is allowed. One claim is one giant owned: a giant's main book held (online or not), a My Giant
 * own book, or the Hearthstride's Keeper. The Guide is the one place the limit is counted, so every giant says the
 * same; the giants ask "maygain" (GuideBridge) only while a limit is in force for that player.
 */
public final class Owns {
    private Owns() {}

    public static final String OWNERS = "owners";
    public static final int VERSION = 1;
    /** the setting (giant "all") */
    public static final String SETTING = "all.maxGiants";

    /** one giant a player owns: the giant's key and how ("main", "own" for a My Giant book, "keeper") */
    public record Claim(String giant, String kind) {
        /** how the Players page and the command say it: "Wreckback", "Pitchgut (My Giant)" */
        public String words() {
            Giant g = AdminServer.giant(giant);
            String n = g == null ? giant : AdminServer.nameOf(g);
            return switch (kind) {
                case "own" -> n + " (My Giant)";
                case "keeper" -> n + " (Keeper)";
                default -> n;
            };
        }
    }

    /** every player's claims (by UUID text), and the names the giants know them by */
    public record All(Map<String, List<Claim>> claims, Map<String, String> names) {
        public List<Claim> of(String id) {
            return claims.getOrDefault(id, List.of());
        }

        public int count(String id) {
            return of(id).size();
        }
    }

    /** what may happen: ok, or why not (the player-facing line) */
    public record Verdict(boolean ok, int n, int cap, String line) {}

    /** one pass over every giant: "owners", or for an older giant his live list (each owner once for that kind) */
    public static All all(MinecraftServer server) {
        Map<String, List<Claim>> claims = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (Giant g : AdminServer.giants()) {
            String mainKind = AdminCatalog.EARNED.contains(g.key()) ? "keeper" : "main";
            JsonObject o = owners(server, g);
            if (o != null) {
                String main = id(AdminJson.str(o, "main", 40));
                if (main != null) {
                    // (a giant whose one book covers several of him says how many: each counts)
                    int count = (int) Math.max(1, Math.min(64, AdminJson.num(o, "mainCount", 1)));
                    for (int i = 0; i < count; i++) add(claims, main, new Claim(g.key(), mainKind));
                    String n = AdminJson.str(o, "mainName", 40);
                    if (!n.isEmpty()) names.putIfAbsent(main, n);
                }
                if (o.get("own") instanceof JsonArray own)
                    for (JsonElement e : own) {
                        if (!(e instanceof JsonObject j)) continue;
                        String id = id(AdminJson.str(j, "id", 40));
                        if (id == null) continue;
                        add(claims, id, new Claim(g.key(), "own"));
                        String n = AdminJson.str(j, "name", 40);
                        if (!n.isEmpty()) names.putIfAbsent(id, n);
                    }
                continue;
            }
            // (an older giant: his live list's owners, each counted once for him)
            List<String> l = AdminServer.call(server, g, "live", "1");
            if (l == null) continue;
            Set<String> seen = new LinkedHashSet<>();
            for (int i = 1; i < l.size(); i++) {
                JsonObject r = AdminJson.object(l.get(i));
                if (r == null) continue;
                String id = id(AdminJson.str(r, "ownerId", 40));
                if (id == null || !seen.add(id)) continue;
                add(claims, id, new Claim(g.key(), mainKind));
                String n = AdminJson.str(r, "owner", 40);
                if (!n.isEmpty()) names.putIfAbsent(id, n);
            }
        }
        return new All(claims, names);
    }

    /** a giant's "owners" answer, or null (an older giant, or a newer version he answers null to) */
    static @Nullable JsonObject owners(MinecraftServer server, Giant g) {
        List<String> r = AdminServer.call(server, g, OWNERS, "{\"owners\":" + VERSION + "}");
        if (r == null || r.size() < 2 || !r.get(0).equals("ok")) return null;
        JsonObject o = AdminJson.object(r.get(1));
        return o != null && o.has(OWNERS) ? o : null;
    }

    private static void add(Map<String, List<Claim>> m, String id, Claim c) {
        m.computeIfAbsent(id, k -> new ArrayList<>()).add(c);
    }

    private static @Nullable String id(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return UUID.fromString(s).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static int count(MinecraftServer server, String id) {
        return all(server).count(id);
    }

    /** the world's limit: 0 for none */
    public static int worldCap(MinecraftServer server) {
        return number(Players.option(server, SETTING));
    }

    static int number(String v) {
        try {
            return Math.max(0, Math.min(9, Integer.parseInt(v)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** this player's limit: their own rule, else the world's; 0 = none */
    public static int cap(MinecraftServer server, String id) {
        PlayerData.Rule r = PlayerData.get(server).rule(id);
        return r.max >= 0 ? Math.min(9, r.max) : worldCap(server);
    }

    /** may this player gain one more giant now (counted fresh; no limit: no counting at all) */
    public static Verdict check(MinecraftServer server, String id) {
        int cap = cap(server, id);
        if (cap <= 0) return new Verdict(true, -1, 0, "");
        int n = count(server, id);
        return n < cap ? new Verdict(true, n, cap, "") : new Verdict(false, n, cap, line(n, cap));
    }

    /** the same, with a pass already made (a page build) */
    public static Verdict check(MinecraftServer server, All all, String id) {
        int cap = cap(server, id);
        if (cap <= 0) return new Verdict(true, all.count(id), 0, "");
        int n = all.count(id);
        return n < cap ? new Verdict(true, n, cap, "") : new Verdict(false, n, cap, line(n, cap));
    }

    /**
     * GuideBridge "maygain": {"players":1,"player":"<uuid>","giant":"<key>"} -> ["ok"] or ["no", line]; null for an
     * argument it can't read or a newer version (the giant then allows it)
     */
    public static @Nullable List<String> mayGain(MinecraftServer server, String arg) {
        JsonObject o = AdminJson.object(arg);
        if (o == null) return null;
        try {
            if (o.get("players").getAsInt() > 1) return null;
            String id = id(o.get("player").getAsString());
            if (id == null) return null;
            Verdict v = check(server, id);
            return v.ok() ? List.of("ok") : List.of("no", v.line());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** what a player at the limit is told (WORDING.md: the same words from every giant) */
    public static String line(int n, int cap) {
        if (n > cap) return "You already own " + n + " giants, more than this world allows (" + cap + ").";
        return "You already own " + n + (n == 1 ? " giant" : " giants") + ", the most this world allows.";
    }

    /** what an operator is told about someone else at the limit (a reason clause may follow) */
    public static String opLine(String name, int n, int cap) {
        if (n > cap)
            return name + " already owns " + n + " giants, more than this world allows for them (" + cap + ").";
        return name + " already owns " + n + (n == 1 ? " giant" : " giants") + ", the most this world allows for them.";
    }

    /** "Owns 1 of 2 giants", "Owns 2 giants (no limit)", "Owns 3 of 2 giants (keeps them)" */
    public static String pageLine(int n, int cap) {
        if (cap <= 0) return "Owns " + n + (n == 1 ? " giant" : " giants") + " (no limit)";
        if (n > cap) return "Owns " + n + " of " + cap + " giants (keeps them)";
        return "Owns " + n + " of " + cap + (cap == 1 ? " giant" : " giants");
    }

    /** "Alex owns 2 of 3 giants: Wreckback, Pitchgut (My Giant)." */
    public static String summary(String name, List<Claim> claims, int cap) {
        int n = claims.size();
        StringBuilder sb = new StringBuilder(name).append(" owns ").append(n);
        if (cap > 0) sb.append(" of ").append(cap).append(cap == 1 ? " giant" : " giants");
        else sb.append(n == 1 ? " giant" : " giants").append(" (no limit)");
        if (!claims.isEmpty()) {
            List<String> w = new ArrayList<>();
            for (Claim c : claims) w.add(c.words());
            sb.append(": ").append(String.join(", ", w));
        }
        return sb.append(".").toString();
    }
}
