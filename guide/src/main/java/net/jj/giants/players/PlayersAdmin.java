package net.jj.giants.players;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.net.AdminBookStatePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The admin book's Players page (ADMIN_BRIDGE.md 20.9), the server's half: the page's data, and the requests it
 * sends (each the same as its command).
 */
public final class PlayersAdmin {
    private PlayersAdmin() {}

    public static final String KIND = "players";
    /** the book's requests this class answers */
    public static final List<String> DOS = List.of("prule", "pmax", "repick", "voteopen", "voteclose", "votecancel",
            "boardremove");

    public static AdminBookStatePayload page(MinecraftServer server) {
        return new AdminBookStatePayload(KIND, json(server).toString());
    }

    public static JsonObject json(MinecraftServer server) {
        PlayerData d = PlayerData.get(server);
        JsonObject o = new JsonObject();
        // everyone online, and everyone with a rule or a pick
        Map<String, JsonObject> people = new LinkedHashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            JsonObject j = new JsonObject();
            j.addProperty("id", p.getStringUUID());
            j.addProperty("name", p.getGameProfile().getName());
            j.addProperty("online", true);
            people.put(p.getStringUUID(), j);
        }
        for (var e : d.s.rules.entrySet())
            people.computeIfAbsent(e.getKey(), k -> person(k, e.getValue().name));
        for (var e : d.s.picks.entrySet()) {
            PlayerData.Stats st = d.s.stats.get(e.getKey());
            people.computeIfAbsent(e.getKey(), k -> person(k, st == null ? k.substring(0, 8) : st.name));
        }
        // (Max giants per player, 20.18) one count of every giant's owners for the whole page; whoever owns one is
        // listed too, online or not
        Owns.All owns = Owns.all(server);
        for (String id : owns.claims().keySet()) {
            PlayerData.Stats st = d.s.stats.get(id);
            String n = owns.names().getOrDefault(id, st == null ? id.substring(0, 8) : st.name);
            people.computeIfAbsent(id, k -> person(k, n));
        }
        int world = Owns.worldCap(server);
        JsonArray rules = new JsonArray();
        for (JsonObject j : people.values()) {
            String id = j.get("id").getAsString();
            PlayerData.Rule r = d.rule(id);
            j.addProperty("own", r.own);
            j.addProperty("ignored", r.ignored);
            j.addProperty("nokit", r.noKit);
            j.addProperty("owns", owns.count(id));
            j.addProperty("max", r.max >= 0 ? Math.min(9, r.max) : world);
            j.addProperty("maxRule", Math.min(9, r.max));
            JsonArray claims = new JsonArray();
            for (Owns.Claim c : owns.of(id)) claims.add(c.words());
            j.add("claims", claims);
            PlayerData.Pick pk = d.s.picks.get(id);
            if (pk != null) {
                Giant g = AdminServer.giant(pk.giant);
                j.addProperty("pick", g == null ? pk.giant : AdminServer.nameOf(g));
                j.addProperty("outcome", pk.outcome);
            }
            rules.add(j);
        }
        o.add("rules", rules);
        o.add("vote", Votes.json(server, null));
        o.add("boards", Boards.json(server, null, 5));
        JsonArray signs = new JsonArray();
        for (PlayerData.Sign s : d.s.signs) {
            JsonObject j = new JsonObject();
            j.addProperty("dim", s.dim);
            j.addProperty("x", s.x);
            j.addProperty("y", s.y);
            j.addProperty("z", s.z);
            j.addProperty("kind", s.kind);
            j.addProperty("title", Boards.TITLES.get(s.kind));
            signs.add(j);
        }
        o.add("signs", signs);
        JsonArray presets = new JsonArray();
        for (String[] c : Votes.choices()) {
            JsonObject j = new JsonObject();
            j.addProperty("id", c[0]);
            j.addProperty("name", c[1]);
            presets.add(j);
        }
        o.add("presets", presets);
        return o;
    }

    private static JsonObject person(String id, String name) {
        JsonObject j = new JsonObject();
        j.addProperty("id", id);
        j.addProperty("name", name);
        j.addProperty("online", false);
        return j;
    }

    /** one of the page's requests (an admin, checked by the book already) */
    public static @Nullable AdminServer.Result request(MinecraftServer server, String who, String what, JsonObject o) {
        return switch (what) {
            case "prule" -> Players.setRule(server, who, AdminJson.str(o, "player", 40),
                    AdminJson.str(o, "rule", 16), Boolean.parseBoolean(AdminJson.str(o, "on", 8)));
            case "pmax" -> Players.setMax(server, who, AdminJson.str(o, "player", 40), AdminJson.str(o, "max", 8));
            case "repick" -> Players.repick(server, who, AdminJson.str(o, "player", 40));
            case "voteopen" -> Votes.open(server, who, Arrays.asList(AdminJson.str(o, "options", 2000).split(",")),
                    (int) AdminJson.num(o, "minutes", 5), Boolean.parseBoolean(AdminJson.str(o, "auto", 8)));
            case "voteclose" -> Votes.end(server, who, true);
            case "votecancel" -> Votes.end(server, who, false);
            case "boardremove" -> Boards.removeSign(server, (int) AdminJson.num(o, "i", -1));
            default -> null;
        };
    }
}
