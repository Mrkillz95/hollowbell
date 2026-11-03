package net.jj.fakeplayers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.jj.giants.GuideBridge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * (Part 5) a giant's bridge that answers the player actions the way ADMIN_BRIDGE.md 20.3-20.5 says, simply: his
 * book's holder, his standing ones (with the owner on each live line), the rules and switches he was sent, and a
 * pick that takes the player to his "ground" at once and tells the Guide what came of it.
 */
public final class GiantsBridge {
    private GiantsBridge() {}

    public static final int API = 1;
    public static JsonObject all = new JsonObject(), rules = new JsonObject();
    public static String holder, holderName;
    public static int most = 0;
    public static final Map<String, Boolean> standing = new LinkedHashMap<>();
    /** (1.11.0, My Giant's own books) the ones bound to a picker: id -> {uuid, name} */
    public static final Map<String, String[]> boundTo = new LinkedHashMap<>();
    public static final List<String> gave = new ArrayList<>();
    /** where his ground's middle is (the test sets it) */
    public static double gx, gy, gz;

    public static void reset() {
        all = new JsonObject();
        rules = new JsonObject();
        holder = null;
        holderName = null;
        most = 0;
        standing.clear();
        boundTo.clear();
        gave.clear();
    }

    public static List<String> giants(MinecraftServer s, String action, String arg) {
        List<String> out = new ArrayList<>();
        switch (action) {
            case "admin" -> {
                out.add("{\"admin\":1,\"giant\":\"fakeplayers\",\"name\":\"Fakeplayers\",\"uses\":[\"all.health\","
                        + "\"all.sit\",\"all.books\",\"all.ride\"],\"all\":{}}");
                out.add("{\"id\":\"maxInWorld\",\"cat\":\"spawning\",\"type\":\"int\",\"min\":0,\"max\":20,\"step\":1,"
                        + "\"default\":0,\"value\":" + most + ",\"set\":false,\"when\":\"newSpawns\","
                        + "\"name\":\"Most at once\",\"what\":\"\"}");
            }
            case "adminset" -> {
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                all = o.getAsJsonObject("all");
                out.add("ok");
            }
            case "players" -> {
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                if (o.get("players").getAsInt() != 1) return null;
                rules = o.getAsJsonObject("rules");
                out.add("ok");
            }
            case "live" -> {
                out.add("{\"live\":1,\"giant\":\"fakeplayers\",\"name\":\"Fakeplayers\",\"moods\":[\"calm\"],"
                        + "\"size\":[0.1,2],\"does\":[]}");
                for (String id : standing.keySet()) {
                    JsonObject g = new JsonObject();
                    g.addProperty("id", id);
                    g.addProperty("state", "here");
                    g.addProperty("dim", "minecraft:overworld");
                    g.addProperty("x", gx);
                    g.addProperty("z", gz);
                    String[] own = boundTo.get(id);
                    if (own != null) {
                        g.addProperty("owner", own[1]);
                        g.addProperty("ownerId", own[0]);
                    } else if (holder != null) {
                        g.addProperty("owner", holderName);
                        g.addProperty("ownerId", holder);
                    }
                    out.add(g.toString());
                }
            }
            case "give" -> {
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                String p = o.get("player").getAsString(), what = o.get("what").getAsString();
                if (what.equals("book") && holder != null && !holder.equals(p))
                    return List.of("no", holderName + " has the Fakeplayers book.");
                if (what.equals("book")) {
                    holder = p;
                    holderName = s.getPlayerList().getPlayer(UUID.fromString(p)).getGameProfile().getName();
                }
                gave.add(what + " " + p);
                out.add("ok");
            }
            case "mygiant" -> {
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                if (o.get("players").getAsInt() != 1) return null;
                ServerPlayer p = s.getPlayerList().getPlayer(UUID.fromString(o.get("player").getAsString()));
                if (p == null) return List.of("no", "That player isn't online.");
                p.teleportTo(s.overworld(), gx, gy, gz, 0f, 0f);
                gave.add("finder " + p.getStringUUID());
                boolean book = o.get("book").getAsBoolean();
                String outcome;
                boolean spawned = false;
                String line;
                if (!book) {
                    outcome = "placed";
                    line = "Welcome to the Fakeplayers's ground.";
                } else if (holder != null && !holder.equals(p.getStringUUID())) {
                    // (BOOK_RULE.md's My Giant exception) room: their own book and their own one; the cap: shared
                    if (most <= 0 || standing.size() < most) {
                        String id = UUID.randomUUID().toString();
                        standing.put(id, true);
                        boundTo.put(id, new String[] {p.getStringUUID(), p.getGameProfile().getName()});
                        spawned = true;
                        outcome = "own";
                        line = "The Fakeplayers is yours: your own book.";
                    } else {
                        outcome = "shared";
                        line = "This world already has as many of the Fakeplayers as it allows, so you share "
                                + holderName + "'s.";
                    }
                } else {
                    holder = p.getStringUUID();
                    holderName = p.getGameProfile().getName();
                    if (most <= 0 || standing.size() < most) {
                        standing.put(UUID.randomUUID().toString(), true);
                        spawned = true;
                        outcome = "own";
                        line = "The Fakeplayers is yours.";
                    } else {
                        outcome = "own_cap";
                        line = "You take charge of the ones out there.";
                    }
                }
                JsonObject done = new JsonObject();
                done.addProperty("players", 1);
                done.addProperty("player", p.getStringUUID());
                done.addProperty("giant", "fakeplayers");
                done.addProperty("outcome", outcome);
                done.addProperty("holder", outcome.equals("own") && spawned && !p.getStringUUID().equals(holder)
                        ? p.getGameProfile().getName() : holderName == null ? "" : holderName);
                done.addProperty("spawned", spawned);
                GuideBridge.guide(s, "mygiantdone", done.toString());
                out.add("ok");
                out.add(line);
            }
            default -> {
                return null;
            }
        }
        return out;
    }
}
